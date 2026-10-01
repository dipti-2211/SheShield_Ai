package com.sheshield.app.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.sheshield.app.data.model.*
import com.sheshield.app.data.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single source of truth for trip lifecycle data.
 * Coordinates between the local Room database and the remote n8n backend.
 */
class TripRepository(context: Context) {

    private val db = AppDatabase.get(context)
    private val dao = db.tripDao()
    private val api = NetworkClient.api

    val activeTrip: LiveData<ActiveTrip?> = dao.observeActiveTrip()

    suspend fun getActiveTrip(): ActiveTrip? = withContext(Dispatchers.IO) {
        dao.getActiveTrip()
    }

    /**
     * Plan a trip. Returns route options with risk scores from backend.
     * Falls back to a demo-mode stub if the backend is unreachable.
     */
    suspend fun planTrip(request: PlanTripRequest): Result<PlanTripResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = api.planTrip(request)
                if (resp.isSuccessful) {
                    resp.body()!!
                } else {
                    throw Exception("Backend error ${resp.code()}: ${resp.errorBody()?.string()}")
                }
            }.recoverCatching { e ->
                // Return demo-mode data so the app remains demonstrable
                buildDemoTripResponse(request)
            }
        }

    /** Confirm trip start with the selected route. Persists the active trip locally. */
    suspend fun startTrip(
        tripId: String,
        selectedRoute: RouteOption,
        origin: LatLng,
        destination: LatLng,
        destLabel: String,
        contacts: List<TrustedContact>
    ): Result<StartTripResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val request = StartTripRequest(
                tripId = tripId,
                routeId = selectedRoute.routeId,
                trustedContacts = contacts
            )
            val resp = api.startTrip(request)
            if (!resp.isSuccessful)
                throw Exception("Start trip failed: ${resp.code()}")
            val body = resp.body()!!

            // Persist locally
            dao.upsert(
                ActiveTrip(
                    tripId = tripId,
                    sessionToken = body.sessionToken,
                    originLat = origin.latitude,
                    originLng = origin.longitude,
                    destinationLat = destination.latitude,
                    destinationLng = destination.longitude,
                    destinationLabel = destLabel,
                    selectedRouteId = selectedRoute.routeId,
                    state = TripState.ACTIVE,
                    trustedContacts = com.google.gson.Gson().toJson(contacts)
                )
            )
            body
        }
    }

    suspend fun sendLocationUpdate(request: LocationUpdateRequest): Result<LocationUpdateResponse?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trip = dao.getTripById(request.tripId) ?: return@runCatching null
                dao.updateLocation(request.tripId, request.latitude, request.longitude, request.timestampMs)

                val resp = api.locationUpdate(request)
                if (resp.isSuccessful) resp.body() else null
            }
        }

    suspend fun checkIn(tripId: String, sessionToken: String, status: String): Result<CheckInResponse?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = api.checkIn(CheckInRequest(tripId, sessionToken, status))
                if (resp.isSuccessful) {
                    val body = resp.body()!!
                    val newState = runCatching { TripState.valueOf(body.newState) }
                        .getOrDefault(TripState.ACTIVE)
                    dao.updateState(tripId, newState)
                    body
                } else null
            }
        }

    suspend fun triggerSos(request: SosRequest): Result<SosResponse?> =
        withContext(Dispatchers.IO) {
            runCatching {
                dao.updateState(request.tripId, TripState.SOS_ACTIVE)
                val resp = api.triggerSos(request)
                if (resp.isSuccessful) resp.body() else null
            }
        }

    suspend fun endTrip(tripId: String, sessionToken: String) = withContext(Dispatchers.IO) {
        runCatching {
            api.endTrip(EndTripRequest(tripId, sessionToken))
            dao.updateState(tripId, TripState.COMPLETED)
        }
    }

    suspend fun setCheckInPending(tripId: String, deadlineMs: Long) = withContext(Dispatchers.IO) {
        dao.setCheckInState(tripId, TripState.CHECK_IN_PENDING, deadlineMs)
    }

    // ── Demo mode ─────────────────────────────────────────────────────────────

    private fun buildDemoTripResponse(req: PlanTripRequest): PlanTripResponse {
        // Hardcoded demo routes for Kolkata (near the sample coords in original workflow)
        val demoTripId = "DEMO-" + System.currentTimeMillis()
        return PlanTripResponse(
            tripId = demoTripId,
            routes = listOf(
                RouteOptionDto(
                    routeId = "$demoTripId-R1",
                    label = "Recommended (Lower reported-risk)",
                    durationSeconds = 1440,
                    distanceMeters = 3200.0,
                    riskLevel = "MEDIUM",
                    riskScore = 0.42,
                    riskSummary = "[DEMO] 3 reported incidents within 500m in last 90 days. Moderate foot traffic.",
                    incidentCount = 3,
                    geometry = listOf(
                        listOf(req.originLng, req.originLat),
                        listOf(req.originLng + 0.005, req.originLat + 0.003),
                        listOf(req.destLng, req.destLat)
                    ),
                    isDemoData = true
                ),
                RouteOptionDto(
                    routeId = "$demoTripId-R2",
                    label = "Faster route (+2 min shorter) — Higher reported-risk",
                    durationSeconds = 960,
                    distanceMeters = 2100.0,
                    riskLevel = "HIGH",
                    riskScore = 0.78,
                    riskSummary = "[DEMO] 7 reported incidents within 300m. Includes isolated stretch after 21:00.",
                    incidentCount = 7,
                    geometry = listOf(
                        listOf(req.originLng, req.originLat),
                        listOf(req.originLng + 0.008, req.originLat - 0.001),
                        listOf(req.destLng, req.destLat)
                    ),
                    isDemoData = true
                )
            )
        )
    }
}
