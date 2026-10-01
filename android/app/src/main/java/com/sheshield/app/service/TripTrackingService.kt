package com.sheshield.app.service

import android.app.*
import android.content.Intent
import android.location.Location
import android.os.*
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.sheshield.app.data.model.*
import com.sheshield.app.data.network.NetworkClient
import com.sheshield.app.data.repository.TripRepository
import com.sheshield.app.ui.MainActivity
import com.sheshield.app.util.NotificationHelper
import kotlinx.coroutines.launch

/**
 * Foreground service that runs while a trip is active.
 *
 * Responsibilities:
 * - Acquire GPS updates via FusedLocationProviderClient
 * - Send periodic heartbeats to the backend
 * - Handle risk events returned by the backend
 * - Trigger check-in notifications
 * - Manage the ongoing notification
 *
 * The service runs as a foreground service (foregroundServiceType=location)
 * so it can continue receiving location updates when the app is backgrounded.
 * This is the correct Android approach for active-trip tracking.
 */
class TripTrackingService : LifecycleService() {

    companion object {
        const val TAG = "TripTrackingService"
        const val ACTION_START = "com.sheshield.app.ACTION_START_TRACKING"
        const val ACTION_STOP  = "com.sheshield.app.ACTION_STOP_TRACKING"
        const val ACTION_CHECK_IN_SAFE = "com.sheshield.app.ACTION_CHECK_IN_SAFE"
        const val ACTION_SOS          = "com.sheshield.app.ACTION_SOS"
        const val EXTRA_TRIP_ID       = "trip_id"
        const val EXTRA_SESSION_TOKEN = "session_token"

        // Location update interval: 30s while active, 60s fallback
        private const val UPDATE_INTERVAL_MS   = 30_000L
        private const val FASTEST_INTERVAL_MS  = 15_000L
        private const val MAX_WAIT_TIME_MS     = 60_000L
    }

    private lateinit var fusedLocation: FusedLocationProviderClient
    private lateinit var repo: TripRepository

    private var tripId: String? = null
    private var sessionToken: String? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { handleLocationUpdate(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocation = LocationServices.getFusedLocationProviderClient(this)
        repo = TripRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> {
                tripId = intent.getStringExtra(EXTRA_TRIP_ID)
                sessionToken = intent.getStringExtra(EXTRA_SESSION_TOKEN)
                if (tripId != null && sessionToken != null) {
                    startForegroundWithNotification()
                    startLocationUpdates()
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> stopTracking()
            ACTION_CHECK_IN_SAFE -> handleCheckIn("SAFE")
            ACTION_SOS -> handleSosAction()
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationHelper.buildTripActiveNotification(this, tripId ?: "")
        startForeground(NotificationHelper.NOTIF_TRIP_ACTIVE_ID, notification)
    }

    private fun startLocationUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setMaxUpdateDelayMillis(MAX_WAIT_TIME_MS)
            .build()
        try {
            fusedLocation.requestLocationUpdates(req, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission not granted", e)
            stopSelf()
        }
    }

    private fun handleLocationUpdate(loc: Location) {
        val tid = tripId ?: return
        val token = sessionToken ?: return

        if (loc.accuracy > 50f) {
            Log.w(TAG, "Low GPS accuracy: ${loc.accuracy}m – skipping heartbeat")
            return
        }

        lifecycleScope.launch {
            val request = com.sheshield.app.data.model.LocationUpdateRequest(
                tripId = tid,
                sessionToken = token,
                latitude = loc.latitude,
                longitude = loc.longitude,
                accuracyMeters = loc.accuracy,
                timestampMs = loc.time
            )
            val result = repo.sendLocationUpdate(request)
            result.onSuccess { response ->
                response?.riskEvent?.let { handleRiskEvent(it) }
            }.onFailure { e ->
                Log.w(TAG, "Location update failed: ${e.message}")
                // Do NOT crash or stop – network loss is expected. Retry on next interval.
            }
        }
    }

    private fun handleRiskEvent(event: com.sheshield.app.data.model.RiskEventDto) {
        val tid = tripId ?: return
        when (event.type) {
            "CHECK_IN_REQUIRED" -> {
                lifecycleScope.launch {
                    repo.setCheckInPending(tid, event.checkInDeadlineMs)
                }
                NotificationHelper.showCheckInNotification(this, tid, event.message, event.checkInDeadlineMs)
            }
            "SAFE_ZONE" -> {
                NotificationHelper.cancelCheckInNotification(this)
                NotificationHelper.updateTripActiveNotification(this, tid, "Monitoring – ${event.message}")
            }
        }
    }

    private fun handleCheckIn(status: String) {
        val tid = tripId ?: return
        val token = sessionToken ?: return
        lifecycleScope.launch {
            repo.checkIn(tid, token, status)
            NotificationHelper.cancelCheckInNotification(this@TripTrackingService)
        }
    }

    private fun handleSosAction() {
        val tid = tripId ?: return
        val token = sessionToken ?: return
        lifecycleScope.launch {
            try {
                val loc = fusedLocation.lastLocation.await()
                repo.triggerSos(
                    com.sheshield.app.data.model.SosRequest(
                        tripId = tid,
                        sessionToken = token,
                        latitude = loc?.latitude ?: 0.0,
                        longitude = loc?.longitude ?: 0.0,
                        trigger = "MANUAL"
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "SOS send failed: ${e.message}")
            }
        }
    }

    private fun stopTracking() {
        fusedLocation.removeLocationUpdates(locationCallback)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        fusedLocation.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }
}

// Extension to await last known location in coroutines
private suspend fun com.google.android.gms.tasks.Task<Location>.await(): Location? {
    return kotlinx.coroutines.tasks.await()
}
