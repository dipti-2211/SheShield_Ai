package com.sheshield.app.util

import android.content.Context
import android.telephony.SmsManager
import android.util.Log

/**
 * Helper for sending emergency SMS messages to trusted contacts.
 * Uses the device's native SMS stack — no internet required.
 */
object SmsHelper {

    private const val TAG = "SmsHelper"

    /**
     * Sends an SOS SMS to every trusted contact phone number.
     *
     * @param context   Application context
     * @param phones    List of phone numbers in E.164 format (e.g. +919876543210)
     * @param latitude  Current GPS latitude
     * @param longitude Current GPS longitude
     * @param appName   App name used in the message (default: SheShield)
     */
    @Suppress("DEPRECATION")
    fun sendSosMessages(
        context: Context,
        phones: List<String>,
        latitude: Double,
        longitude: Double,
        appName: String = "SheShield"
    ) {
        if (phones.isEmpty()) {
            Log.w(TAG, "No trusted contacts to notify")
            return
        }

        val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"
        val message = buildString {
            append("🆘 SOS ALERT from $appName!\n")
            append("I may be in danger. My last known location:\n")
            append(mapsLink)
            append("\n(Lat: ${String.format("%.5f", latitude)}, Lng: ${String.format("%.5f", longitude)})")
        }

        val smsManager: SmsManager = try {
            // Android 12+ API
            context.getSystemService(SmsManager::class.java)
                ?: SmsManager.getDefault()
        } catch (e: Exception) {
            SmsManager.getDefault()
        }

        phones.forEach { phone ->
            try {
                // divideMessage handles messages > 160 chars automatically
                val parts = smsManager.divideMessage(message)
                if (parts.size == 1) {
                    smsManager.sendTextMessage(phone, null, message, null, null)
                } else {
                    smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
                }
                Log.i(TAG, "SOS SMS sent to $phone")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SOS SMS to $phone: ${e.message}")
            }
        }
    }
}
