package com.sheshield.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sheshield.app.data.model.AppDatabase
import com.sheshield.app.data.model.TripState
import kotlinx.coroutines.*

/**
 * Restores foreground tracking service after device restart,
 * provided a trip was active at the time of the reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d("BootReceiver", "Boot completed - checking for active trip")

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val dao = AppDatabase.get(context).tripDao()
            val trip = dao.getActiveTrip() ?: return@launch
            if (trip.state == TripState.ACTIVE || trip.state == TripState.CHECK_IN_PENDING) {
                Log.d("BootReceiver", "Restoring trip tracking for trip ${trip.tripId}")
                val serviceIntent = Intent(context, TripTrackingService::class.java).apply {
                    action = TripTrackingService.ACTION_START
                    putExtra(TripTrackingService.EXTRA_TRIP_ID, trip.tripId)
                    putExtra(TripTrackingService.EXTRA_SESSION_TOKEN, trip.sessionToken)
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
