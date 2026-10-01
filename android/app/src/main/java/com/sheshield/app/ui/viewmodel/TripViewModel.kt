package com.sheshield.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sheshield.app.data.model.*
import com.sheshield.app.data.repository.TripRepository
import com.sheshield.app.service.TripTrackingService
import kotlinx.coroutines.launch
import java.util.UUID

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class RoutesReady(val tripId: String, val routes: List<RouteOption>) : UiState()
    data class TripStarted(val trip: ActiveTrip) : UiState()
    data class CheckInPending(val trip: ActiveTrip, val deadlineMs: Long) : UiState()
    data class SosActive(val trip: ActiveTrip) : UiState()
    data class Error(val message: String, val isNetworkError: Boolean = false) : UiState()
    object TripEnded : UiState()
}

/**
 * Shared ViewModel for the main activity and all trip-related fragments.
 * Bridges the UI layer and TripRepository.
 */
class TripViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = TripRepository(application)

    val activeTrip: LiveData<ActiveTrip?> = repo.activeTrip

    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    // Parsed route options from planning
    var plannedTripId: String? = null
        private set
    var plannedRoutes: List<RouteOption> = emptyList()
        private set

    // Trusted contacts (loaded from prefs)
    private val prefs by lazy {
        application.getSharedPreferences("sheshield_prefs", android.content.Context.MODE_PRIVATE)
    }

    fun getTrustedContacts(): List<TrustedContact> {
        val json = prefs.getString("trusted_contacts", "[]") ?: "[]"
        return runCatching {
            Gson().fromJson<List<TrustedContact>>(json,
                object : TypeToken<List<TrustedContact>>() {}.type)
        }.getOrDefault(emptyList())
    }

    fun saveTrustedContacts(contacts: List<TrustedContact>) {
        prefs.edit().putString("trusted_contacts", Gson().toJson(contacts)).apply()
    }

    fun isDemoMode(): Boolean = prefs.getBoolean("demo_mode", false)
    fun setDemoMode(enabled: Boolean) = prefs.edit().putBoolean("demo_mode", enabled).apply()

    fun planTrip(
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double, destLabel: String
    ) {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val request = PlanTripRequest(
                originLat = originLat,
                originLng = originLng,
                destLat = destLat,
                destLng = destLng,
                destLabel = destLabel,
                trustedContacts = getTrustedContacts(),
                demoMode = isDemoMode()
            )
            val result = repo.planTrip(request)
            result.onSuccess { response ->
                plannedTripId = response.tripId
                plannedRoutes = response.routes.map { it.toDomain() }
                _uiState.value = UiState.RoutesReady(response.tripId, plannedRoutes)
            }.onFailure { e ->
                _uiState.value = UiState.Error(
                    "Could not plan trip: ${e.message}",
                    isNetworkError = true
                )
            }
        }
    }

    fun startTrip(
        selectedRoute: RouteOption,
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double, destLabel: String
    ) {
        val tripId = plannedTripId ?: "trip-${UUID.randomUUID()}"
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val result = repo.startTrip(
                tripId = tripId,
                selectedRoute = selectedRoute,
                origin = LatLng(originLat, originLng),
                destination = LatLng(destLat, destLng),
                destLabel = destLabel,
                contacts = getTrustedContacts()
            )
            result.onSuccess { response ->
                val trip = repo.getActiveTrip()
                if (trip != null) {
                    startTrackingService(trip)
                    _uiState.value = UiState.TripStarted(trip)
                } else {
                    _uiState.value = UiState.Error("Trip state error after start")
                }
            }.onFailure { e ->
                _uiState.value = UiState.Error("Failed to start trip: ${e.message}")
            }
        }
    }

    fun confirmSafe() {
        viewModelScope.launch {
            val trip = repo.getActiveTrip() ?: return@launch
            repo.checkIn(trip.tripId, trip.sessionToken, "SAFE")
            _uiState.value = UiState.TripStarted(trip.copy(state = TripState.ACTIVE))
        }
    }

    fun triggerSos() {
        viewModelScope.launch {
            val trip = repo.getActiveTrip() ?: return@launch
            // The service handles the actual SOS call - we update state here
            sendServiceAction(TripTrackingService.ACTION_SOS)
            _uiState.value = UiState.SosActive(trip)
        }
    }

    fun endTrip() {
        viewModelScope.launch {
            val trip = repo.getActiveTrip() ?: run {
                _uiState.value = UiState.TripEnded
                return@launch
            }
            stopTrackingService()
            repo.endTrip(trip.tripId, trip.sessionToken)
            _uiState.value = UiState.TripEnded
        }
    }

    private fun startTrackingService(trip: ActiveTrip) {
        val ctx = getApplication<android.app.Application>()
        val intent = Intent(ctx, TripTrackingService::class.java).apply {
            action = TripTrackingService.ACTION_START
            putExtra(TripTrackingService.EXTRA_TRIP_ID, trip.tripId)
            putExtra(TripTrackingService.EXTRA_SESSION_TOKEN, trip.sessionToken)
        }
        ctx.startForegroundService(intent)
    }

    private fun stopTrackingService() {
        val ctx = getApplication<android.app.Application>()
        ctx.startService(Intent(ctx, TripTrackingService::class.java).apply {
            action = TripTrackingService.ACTION_STOP
        })
    }

    private fun sendServiceAction(action: String) {
        val ctx = getApplication<android.app.Application>()
        ctx.startService(Intent(ctx, TripTrackingService::class.java).apply {
            this.action = action
        })
    }

    fun resetToIdle() {
        _uiState.value = UiState.Idle
    }
}
