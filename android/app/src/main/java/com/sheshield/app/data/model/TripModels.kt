package com.sheshield.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

// ── Enums ────────────────────────────────────────────────────────────────────

enum class TripState {
    IDLE,
    PLANNING,
    ACTIVE,
    CHECK_IN_PENDING,
    SOS_ACTIVE,
    COMPLETED,
    CANCELLED
}

enum class RiskLevel {
    LOW, MEDIUM, HIGH, UNKNOWN
}

// ── Location ──────────────────────────────────────────────────────────────────

data class LatLng(
    val latitude: Double,
    val longitude: Double
)

// ── Route models ──────────────────────────────────────────────────────────────

data class RouteOption(
    val routeId: String,
    val label: String,               // e.g. "Faster route"
    val durationSeconds: Int,
    val distanceMeters: Double,
    val riskLevel: RiskLevel,
    val riskScore: Double,           // 0.0 – 1.0
    val riskSummary: String,         // human-readable explanation
    val incidentCount: Int,          // # crime incidents near route
    val geometry: List<LatLng>,      // decoded polyline
    val isDemoData: Boolean = false  // true when using fixture data
)

data class PlanTripResponse(
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("routes") val routes: List<RouteOptionDto>
)

data class RouteOptionDto(
    @SerializedName("route_id") val routeId: String,
    @SerializedName("label") val label: String,
    @SerializedName("duration_seconds") val durationSeconds: Int,
    @SerializedName("distance_meters") val distanceMeters: Double,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("risk_score") val riskScore: Double,
    @SerializedName("risk_summary") val riskSummary: String,
    @SerializedName("incident_count") val incidentCount: Int,
    @SerializedName("geometry") val geometry: List<List<Double>>, // [[lng,lat],...]
    @SerializedName("is_demo_data") val isDemoData: Boolean = false
)

fun RouteOptionDto.toDomain() = RouteOption(
    routeId = routeId,
    label = label,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    riskLevel = runCatching { RiskLevel.valueOf(riskLevel) }.getOrDefault(RiskLevel.UNKNOWN),
    riskScore = riskScore,
    riskSummary = riskSummary,
    incidentCount = incidentCount,
    geometry = geometry.map { LatLng(it[1], it[0]) }, // ORS uses [lng, lat]
    isDemoData = isDemoData
)

// ── Trip session (persisted to Room) ─────────────────────────────────────────

@Entity(tableName = "active_trip")
data class ActiveTrip(
    @PrimaryKey val tripId: String,
    val sessionToken: String,       // short-lived token for backend authentication
    val originLat: Double,
    val originLng: Double,
    val destinationLat: Double,
    val destinationLng: Double,
    val destinationLabel: String,
    val selectedRouteId: String,
    val state: TripState = TripState.ACTIVE,
    val startedAtMs: Long = System.currentTimeMillis(),
    val lastLatitude: Double = originLat,
    val lastLongitude: Double = originLng,
    val lastUpdateMs: Long = System.currentTimeMillis(),
    val checkInDeadlineMs: Long = 0L,  // nonzero when CHECK_IN_PENDING
    val trustedContacts: String = "[]"  // JSON array of TrustedContact
)

// ── Trusted contacts ──────────────────────────────────────────────────────────

data class TrustedContact(
    val name: String,
    val phone: String  // E.164 format
)

// ── API request/response models ───────────────────────────────────────────────

data class PlanTripRequest(
    @SerializedName("origin_lat") val originLat: Double,
    @SerializedName("origin_lng") val originLng: Double,
    @SerializedName("dest_lat") val destLat: Double,
    @SerializedName("dest_lng") val destLng: Double,
    @SerializedName("dest_label") val destLabel: String,
    @SerializedName("trusted_contacts") val trustedContacts: List<TrustedContact>,
    @SerializedName("demo_mode") val demoMode: Boolean = false
)

data class StartTripRequest(
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("route_id") val routeId: String,
    @SerializedName("trusted_contacts") val trustedContacts: List<TrustedContact>
)

data class StartTripResponse(
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("session_token") val sessionToken: String,
    @SerializedName("status") val status: String
)

data class LocationUpdateRequest(
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("session_token") val sessionToken: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy_meters") val accuracyMeters: Float,
    @SerializedName("timestamp_ms") val timestampMs: Long
)

data class LocationUpdateResponse(
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("risk_event") val riskEvent: RiskEventDto?
)

data class RiskEventDto(
    @SerializedName("type") val type: String,  // "CHECK_IN_REQUIRED" | "SAFE_ZONE"
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("message") val message: String,
    @SerializedName("check_in_deadline_ms") val checkInDeadlineMs: Long
)

data class CheckInRequest(
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("session_token") val sessionToken: String,
    @SerializedName("status") val status: String  // "SAFE" | "SOS"
)

data class CheckInResponse(
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("new_state") val newState: String,
    @SerializedName("message") val message: String
)

data class SosRequest(
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("session_token") val sessionToken: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("trigger") val trigger: String  // "MANUAL" | "TIMEOUT"
)

data class SosResponse(
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String
)

data class EndTripRequest(
    @SerializedName("trip_id") val tripId: String,
    @SerializedName("session_token") val sessionToken: String
)

data class HealthResponse(
    @SerializedName("status") val status: String,
    @SerializedName("version") val version: String
)
