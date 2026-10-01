/**
 * SheShield Risk Engine – JavaScript implementation for n8n Code nodes.
 *
 * This is NOT an LLM risk score.
 * Risk is computed deterministically from actual crime incident data.
 *
 * Algorithm:
 *   For each route geometry point, look up incidents in the Pinecone result set
 *   (passed in as structured JSON).  Compute a weighted score per incident:
 *
 *     incident_weight = severity_weight(category)
 *                     * recency_weight(days_old)
 *                     * distance_decay(distance_km)
 *
 *   Aggregate over all incidents visible from the route corridor.
 *   Normalise to [0, 1].  Map to LOW / MEDIUM / HIGH threshold.
 *
 * Thresholds (configurable at top):
 *   score < 0.3  → LOW
 *   score < 0.6  → MEDIUM
 *   score ≥ 0.6  → HIGH
 *
 * LLM usage: NONE.  The LLM is used separately (in a different node)
 * only to generate a human-readable explanation of this score.
 */

const THRESHOLDS = { LOW: 0.3, MEDIUM: 0.6 };

const SEVERITY_WEIGHTS = {
  "violent_crime": 1.0,
  "sexual_assault": 1.0,
  "robbery": 0.85,
  "kidnapping": 0.95,
  "assault": 0.75,
  "harassment": 0.55,
  "theft": 0.40,
  "vandalism": 0.25,
  "traffic_incident": 0.20,
  "other": 0.30,
  "default": 0.35
};

/**
 * Recency decay: incidents older than 365 days have near-zero weight.
 * Uses an exponential decay with half-life of 60 days.
 */
function recencyWeight(daysOld) {
  const HALF_LIFE = 60;
  return Math.pow(0.5, daysOld / HALF_LIFE);
}

/**
 * Distance decay: incidents closer to the route have higher weight.
 * Uses a Gaussian kernel with sigma = 0.3 km.
 */
function distanceDecay(distanceKm) {
  const SIGMA = 0.3;
  return Math.exp(-(distanceKm * distanceKm) / (2 * SIGMA * SIGMA));
}

/**
 * Haversine distance in km between two lat/lng pairs.
 */
function haversine(lat1, lng1, lat2, lng2) {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLng = (lng2 - lng1) * Math.PI / 180;
  const a = Math.sin(dLat/2)**2 +
            Math.cos(lat1*Math.PI/180) * Math.cos(lat2*Math.PI/180) * Math.sin(dLng/2)**2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
}

/**
 * Score a set of incidents against a route corridor.
 *
 * @param {Array} incidents - Array of {lat, lng, category, days_old}
 * @param {Array} routePoints - Array of [lng, lat] pairs (ORS geometry format)
 * @param {number} corridorKm - Search corridor radius in km
 * @returns {object} {score, level, incident_count, supporting_evidence}
 */
function scoreRoute(incidents, routePoints, corridorKm = 0.5) {
  if (!incidents || incidents.length === 0) {
    return { score: 0, level: "LOW", incident_count: 0, supporting_evidence: [] };
  }

  let totalWeight = 0;
  const supporting = [];

  for (const inc of incidents) {
    const incLat = parseFloat(inc.lat || inc.latitude || 0);
    const incLng = parseFloat(inc.lng || inc.longitude || 0);
    if (!incLat || !incLng) continue;

    // Find closest point on the route to this incident
    let minDist = Infinity;
    for (const pt of routePoints) {
      const ptLat = pt[1] ?? pt.latitude ?? 0;
      const ptLng = pt[0] ?? pt.longitude ?? 0;
      const d = haversine(incLat, incLng, ptLat, ptLng);
      if (d < minDist) minDist = d;
    }

    if (minDist > corridorKm) continue; // Outside corridor

    const catKey = (inc.category || "").toLowerCase().replace(/[^a-z_]/g, "_");
    const sevWeight = SEVERITY_WEIGHTS[catKey] || SEVERITY_WEIGHTS["default"];
    const recWeight = recencyWeight(inc.days_old || 180);
    const distWeight = distanceDecay(minDist);
    const w = sevWeight * recWeight * distWeight;

    totalWeight += w;
    supporting.push({
      category: inc.category || "unknown",
      days_old: inc.days_old,
      distance_km: minDist.toFixed(3),
      weight: w.toFixed(4)
    });
  }

  // Normalise: cap at a practical maximum (10 * max_single_weight ≈ 10)
  const MAX_PRACTICAL = 10;
  const score = Math.min(totalWeight / MAX_PRACTICAL, 1.0);

  let level;
  if (score < THRESHOLDS.LOW) level = "LOW";
  else if (score < THRESHOLDS.MEDIUM) level = "MEDIUM";
  else level = "HIGH";

  return {
    score: parseFloat(score.toFixed(4)),
    level,
    incident_count: supporting.length,
    supporting_evidence: supporting.slice(0, 10) // cap for readability
  };
}

module.exports = { scoreRoute, haversine, recencyWeight, distanceDecay };
