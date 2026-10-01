package com.sheshield.app.util

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sheshield.app.R
import com.sheshield.app.service.TripTrackingService
import com.sheshield.app.ui.MainActivity

/**
 * Creates and manages all app notification channels and notifications.
 * Android 8+ requires channels to be created before notifications can be shown.
 */
object NotificationHelper {

    const val CHANNEL_TRIP_ACTIVE  = "sheshield_trip_active"
    const val CHANNEL_SAFETY       = "sheshield_safety"
    const val CHANNEL_SOS          = "sheshield_sos"

    const val NOTIF_TRIP_ACTIVE_ID = 1001
    const val NOTIF_CHECK_IN_ID    = 1002
    const val NOTIF_SOS_ID         = 1003

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TRIP_ACTIVE, "Trip Monitoring",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Active when a trip is in progress"
                setShowBadge(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SAFETY, "Safety Alerts",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Safety check-in requests"
                enableVibration(true)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SOS, "SOS / Emergency",
                NotificationManager.IMPORTANCE_MAX).apply {
                description = "SOS and escalation alerts"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun buildTripActiveNotification(ctx: Context, tripId: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            ctx, 1,
            Intent(ctx, TripTrackingService::class.java).apply {
                action = TripTrackingService.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CHANNEL_TRIP_ACTIVE)
            .setContentTitle("SheShield – Trip Active")
            .setContentText("Monitoring your journey")
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentIntent(tapIntent)
            .addAction(0, "End Trip", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun updateTripActiveNotification(ctx: Context, tripId: String, status: String) {
        val notification = buildTripActiveNotification(ctx, tripId) // rebuild with same tap intent
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_TRIP_ACTIVE_ID, notification)
    }

    fun showCheckInNotification(ctx: Context, tripId: String, message: String, deadlineMs: Long) {
        val safeIntent = PendingIntent.getService(
            ctx, 10,
            Intent(ctx, TripTrackingService::class.java).apply {
                action = TripTrackingService.ACTION_CHECK_IN_SAFE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val sosIntent = PendingIntent.getService(
            ctx, 11,
            Intent(ctx, TripTrackingService::class.java).apply {
                action = TripTrackingService.ACTION_SOS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val remaining = ((deadlineMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        val notif = NotificationCompat.Builder(ctx, CHANNEL_SAFETY)
            .setContentTitle("⚠️ Safety Check-In Required")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$message\n\nRespond within ${remaining}s or SOS will be triggered."
            ))
            .setSmallIcon(R.drawable.ic_shield_notification)
            .addAction(0, "✅ I'm Safe", safeIntent)
            .addAction(0, "🆘 SOS", sosIntent)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_CHECK_IN_ID, notif)
    }

    fun cancelCheckInNotification(ctx: Context) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIF_CHECK_IN_ID)
    }
}
