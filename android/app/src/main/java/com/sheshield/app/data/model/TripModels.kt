package com.sheshield.app.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

enum class TripState { IDLE, PLANNING, ACTIVE, CHECK_IN_PENDING, SOS_ACTIVE, COMPLETED, CANCELLED }
enum class RiskLevel { LOW, MEDIUM, HIGH, UNKNOWN }
data class LatLng(val latitude: Double, val longitude: Double)
data class Place(val label: String, val latitude: Double, val longitude: Double, val id: String = "")
data class TrustedContact(val name: String, val phone: String)
data class RiskSegment(@SerializedName("segment_id") val id: String, @SerializedName("start_index") val startIndex: Int,
    @SerializedName("end_index") val endIndex: Int, val score: Double, @SerializedName("risk_level") val level: String,
    @SerializedName("distance_meters") val distanceMeters: Double)
data class Evidence(val id: String, val category: String, @SerializedName("days_old") val daysOld: Int,
    @SerializedName("distance_km") val distanceKm: Double, val source: String)
data class RouteStep(val instruction: String = "Continue along the route", val distance: Double = 0.0,
    @SerializedName("way_points") val wayPoints: List<Int> = emptyList())
data class RouteOption(@SerializedName("route_id") val routeId: String, val label: String,
    @SerializedName("duration_seconds") val durationSeconds: Int, @SerializedName("distance_meters") val distanceMeters: Double,
    @SerializedName("risk_level") val riskLevel: String, @SerializedName("risk_score") val riskScore: Double,
    @SerializedName("risk_summary") val riskSummary: String, @SerializedName("incident_count") val incidentCount: Int,
    val geometry: List<List<Double>>, @SerializedName("is_demo_data") val isDemoData: Boolean = false,
    val segments: List<RiskSegment> = emptyList(), val evidence: List<Evidence> = emptyList(),
    val coverage: String = "UNAVAILABLE", @SerializedName("extra_minutes") val extraMinutes: Int = 0,
    @SerializedName("evaluated_at") val evaluatedAt: String = "", @SerializedName("route_revision") val revision: Int = 1,
    val steps: List<RouteStep> = emptyList(),
    @SerializedName("origin_snap_meters") val originSnapMeters: Int = 0,
    @SerializedName("destination_snap_meters") val destinationSnapMeters: Int = 0) {
    val points get() = geometry.map { LatLng(it[1], it[0]) }
}
data class TripPlan(val id: String, val mode: String, val origin: Place, val destination: Place,
    val routes: List<RouteOption>, val attribution: String = "OpenStreetMap contributors",
    @SerializedName("geometry_source") val geometrySource: String = "",
    @SerializedName("expires_at_ms") val expiresAtMs: Long = 0)
data class LocationFix(val latitude: Double, val longitude: Double,
    @SerializedName("accuracy_meters") val accuracy: Float = 0f, @SerializedName("timestamp_ms") val timestampMs: Long = System.currentTimeMillis(),
    val sequence: Long = 0)
data class CheckInEvent(val id: String, @SerializedName("segment_id") val segmentId: String,
    val status: String, @SerializedName("deadline_ms") val deadlineMs: Long)
data class RemoteTrip(val id: String, val mode: String, val state: String, val origin: Place, val destination: Place,
    val route: RouteOption, val contacts: List<TrustedContact> = emptyList(),
    @SerializedName("last_location") val lastLocation: LocationFix? = null,
    @SerializedName("check_in") val checkIn: CheckInEvent? = null, @SerializedName("sos_id") val sosId: String? = null,
    @SerializedName("started_at_ms") val startedAtMs: Long, @SerializedName("ended_at_ms") val endedAtMs: Long? = null,
    val version: Int = 0)
@Entity(tableName = "active_trip")
data class ActiveTrip(@PrimaryKey val tripId: String, val sessionToken: String = "", val originLat: Double,
    val originLng: Double, val destinationLat: Double, val destinationLng: Double, val destinationLabel: String,
    val selectedRouteId: String, val state: TripState = TripState.ACTIVE,
    val startedAtMs: Long = System.currentTimeMillis(), val lastLatitude: Double = originLat,
    val lastLongitude: Double = originLng, val lastUpdateMs: Long = 0, val checkInDeadlineMs: Long = 0,
    val trustedContacts: String = "[]", @ColumnInfo(defaultValue="''") val routeJson: String = "",
    @ColumnInfo(defaultValue="'LIVE'") val mode: String = "LIVE",
    @ColumnInfo(defaultValue="''") val checkInId: String = "", @ColumnInfo(defaultValue="'SYNCED'") val syncStatus: String = "SYNCED",
    @ColumnInfo(defaultValue="0") val accuracyMeters: Float = 0f,
    @ColumnInfo(defaultValue="''") val sosId: String = "", @ColumnInfo(defaultValue="0") val endedAtMs: Long = 0,
    @ColumnInfo(defaultValue="0") val version: Int = 0, @ColumnInfo(defaultValue="0") val progressIndex: Int = 0,
    @ColumnInfo(defaultValue="0") val lastCheckInAtMs: Long = 0) {
    fun route(): RouteOption? = runCatching { Gson().fromJson(routeJson, RouteOption::class.java) }.getOrNull()
    val isRehearsal get() = mode == "REHEARSAL"
    val isEnded get() = state == TripState.COMPLETED || state == TripState.CANCELLED
}
@Entity(tableName = "trip_plans")
data class SavedPlan(@PrimaryKey val id: String = "current", val json: String)
@Entity(tableName = "outbox")
data class OutboxEvent(@PrimaryKey val id: String, val tripId: String, val path: String, val payload: String,
    val createdAtMs: Long = System.currentTimeMillis(), val expiresAtMs: Long = 0)
@Entity(tableName = "sos_incidents")
data class SavedIncident(@PrimaryKey val id: String, val json: String)
data class TimelineEvent(val id: String, val status: String, val message: String, val contact: String? = null,
    @SerializedName("at_ms") val atMs: Long)
data class DeliveryAttempt(val id: String, val contact: TrustedContact, val status: String)
data class SosIncident(val id: String, @SerializedName("trip_id") val tripId: String? = null,
    val mode: String, val status: String, val cancelled: Boolean = false, val location: LocationFix? = null,
    val contacts: List<TrustedContact> = emptyList(), val timeline: List<TimelineEvent> = emptyList(),
    val attempts: List<DeliveryAttempt> = emptyList(), @SerializedName("created_at_ms") val createdAtMs: Long)
data class SessionResponse(@SerializedName("session_token") val token: String)
data class PlacesResponse(val places: List<Place>)
data class TripsResponse(val trips: List<RemoteTrip>)
data class ReadyResponse(val status: String, val routing: Boolean, @SerializedName("risk_data") val riskData: Boolean,
    val n8n: Boolean, val callbacks: Boolean, @SerializedName("live_alerts") val liveAlerts: Boolean)
data class ShareResponse(val url: String)
