package com.sheshield.app

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the risk scoring logic.
 *
 * These tests verify deterministic behavior of the risk algorithm.
 * They do not require Android runtime.
 *
 * Note: The JavaScript risk_engine.js is the n8n implementation.
 * Here we test an equivalent Kotlin version of the algorithm to verify the math.
 */
class RiskEngineTest {

    // ── Haversine ──────────────────────────────────────────────────────────────

    @Test
    fun `haversine returns zero for identical points`() {
        val dist = haversine(22.5726, 88.3639, 22.5726, 88.3639)
        assertEquals(0.0, dist, 0.001)
    }

    @Test
    fun `haversine returns reasonable distance for known points`() {
        // Victoria Memorial to Howrah Bridge, Kolkata - approx 4.7 km
        val dist = haversine(22.5448, 88.3426, 22.5855, 88.3417)
        assertTrue("Expected ~4.7 km, got $dist", dist in 4.0..5.5)
    }

    // ── Recency decay ──────────────────────────────────────────────────────────

    @Test
    fun `recency weight for today is 1_0`() {
        assertEquals(1.0, recencyWeight(0), 0.001)
    }

    @Test
    fun `recency weight for 60 days is approximately 0_5`() {
        assertEquals(0.5, recencyWeight(60), 0.01)
    }

    @Test
    fun `recency weight for 365 days is near zero`() {
        assertTrue("Should be < 0.05", recencyWeight(365) < 0.05)
    }

    // ── Distance decay ─────────────────────────────────────────────────────────

    @Test
    fun `distance decay at zero is 1_0`() {
        assertEquals(1.0, distanceDecay(0.0), 0.001)
    }

    @Test
    fun `distance decay at 1km is substantially reduced`() {
        assertTrue("Should be < 0.1 at 1km", distanceDecay(1.0) < 0.1)
    }

    // ── Score tests ────────────────────────────────────────────────────────────

    @Test
    fun `empty incident list returns LOW risk score of zero`() {
        val result = scoreRoute(emptyList(), testRoutePoints())
        assertEquals("LOW", result.level)
        assertEquals(0.0, result.score, 0.001)
        assertEquals(0, result.incidentCount)
    }

    @Test
    fun `single nearby recent violent crime returns nonzero low score`() {
        val incidents = listOf(
            Incident(lat = 22.5730, lng = 88.3640, category = "violent_crime", daysOld = 5)
        )
        val result = scoreRoute(incidents, testRoutePoints())
        // Algorithm: single incident at ~50m scores ~0.09 (LOW). This is correct.
        // Aggregate incidents are required to reach MEDIUM or HIGH, preventing single-data-point panic.
        assertTrue("Score should be > 0.05 for nearby recent violent crime", result.score > 0.05)
        assertEquals("Exactly 1 incident should be counted", 1, result.incidentCount)
    }

    @Test
    fun `incident 2km away from route has negligible weight`() {
        // Place incident far from the test route
        val incidents = listOf(
            Incident(lat = 22.5900, lng = 88.3900, category = "robbery", daysOld = 10)
        )
        val result = scoreRoute(incidents, testRoutePoints())
        assertEquals("LOW", result.level)
    }

    @Test
    fun `seven recent incidents near route returns elevated MEDIUM risk`() {
        val incidents = (1..7).map {
            Incident(lat = 22.5726 + it * 0.0001, lng = 88.3640, category = "robbery", daysOld = 30)
        }
        val result = scoreRoute(incidents, testRoutePoints())
        // 7 robberies at 10m intervals, 30 days old = score ~0.41 (MEDIUM). Verified by Python test.
        assertTrue("7 incidents should produce elevated risk", result.score > 0.3)
        assertEquals(7, result.incidentCount)
        assertTrue("Level should be MEDIUM or HIGH", result.level == "MEDIUM" || result.level == "HIGH")
    }

    @Test
    fun `mix of old and new incidents weights new ones higher`() {
        val recentIncident = listOf(
            Incident(lat = 22.5730, lng = 88.3640, category = "theft", daysOld = 5)
        )
        val oldIncident = listOf(
            Incident(lat = 22.5730, lng = 88.3640, category = "theft", daysOld = 360)
        )
        val recentResult = scoreRoute(recentIncident, testRoutePoints())
        val oldResult = scoreRoute(oldIncident, testRoutePoints())
        assertTrue("Recent incident should score higher", recentResult.score > oldResult.score)
    }

    @Test
    fun `score is deterministic for same input`() {
        val incidents = listOf(
            Incident(lat = 22.5730, lng = 88.3640, category = "harassment", daysOld = 45)
        )
        val r1 = scoreRoute(incidents, testRoutePoints())
        val r2 = scoreRoute(incidents, testRoutePoints())
        assertEquals(r1.score, r2.score, 0.0001)
        assertEquals(r1.level, r2.level)
    }

    @Test
    fun `risk level thresholds are correct`() {
        // LOW below 0.3
        assertEquals("LOW",   levelForScore(0.0))
        assertEquals("LOW",   levelForScore(0.29))
        // MEDIUM 0.3-0.6
        assertEquals("MEDIUM", levelForScore(0.3))
        assertEquals("MEDIUM", levelForScore(0.59))
        // HIGH at or above 0.6
        assertEquals("HIGH",  levelForScore(0.6))
        assertEquals("HIGH",  levelForScore(1.0))
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun testRoutePoints() = listOf(
        Pair(88.3639, 22.5726),
        Pair(88.3700, 22.5760),
        Pair(88.3800, 22.5800)
    )
}

// ── Inline Kotlin risk engine (mirrors n8n risk_engine.js) ───────────────────

data class Incident(val lat: Double, val lng: Double, val category: String, val daysOld: Int)
data class RouteRisk(val score: Double, val level: String, val incidentCount: Int)

private val SEVERITY = mapOf(
    "violent_crime" to 1.0, "sexual_assault" to 1.0, "robbery" to 0.85,
    "kidnapping" to 0.95, "assault" to 0.75, "harassment" to 0.55,
    "theft" to 0.40, "vandalism" to 0.25, "traffic_incident" to 0.20,
    "other" to 0.30
)

fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat/2).pow(2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng/2).pow(2)
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

fun recencyWeight(daysOld: Int): Double = Math.pow(0.5, daysOld / 60.0)
fun distanceDecay(distKm: Double): Double = Math.exp(-(distKm * distKm) / (2 * 0.3 * 0.3))
fun levelForScore(s: Double) = when { s < 0.3 -> "LOW"; s < 0.6 -> "MEDIUM"; else -> "HIGH" }

fun scoreRoute(incidents: List<Incident>, routePoints: List<Pair<Double, Double>>, corridorKm: Double = 0.5): RouteRisk {
    if (incidents.isEmpty()) return RouteRisk(0.0, "LOW", 0)
    var total = 0.0; var count = 0
    for (inc in incidents) {
        val minDist = routePoints.minOf { haversine(inc.lat, inc.lng, it.second, it.first) }
        if (minDist > corridorKm) continue
        val sev = SEVERITY[inc.category] ?: 0.35
        val w = sev * recencyWeight(inc.daysOld) * distanceDecay(minDist)
        total += w; count++
    }
    val score = minOf(total / 10.0, 1.0)
    return RouteRisk(score, levelForScore(score), count)
}

private fun Double.pow(n: Int) = Math.pow(this, n.toDouble())
private fun Double.pow(n: Double) = Math.pow(this, n)
