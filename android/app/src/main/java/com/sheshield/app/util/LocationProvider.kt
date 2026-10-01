package com.sheshield.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import com.sheshield.app.data.model.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class LocationProvider(private val context:Context) {
    fun permitted()=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    suspend fun current():LocationFix {
        check(permitted()){ "Allow location access to use your current position." }
        val cancellation=CancellationTokenSource()
        try{
            val loc=withTimeout(15000){LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,cancellation.token).await()}
                ?:error("Your location is unavailable. Turn on GPS, move outdoors, or choose a starting point on the map.")
            return LocationFix(loc.latitude,loc.longitude,loc.accuracy,loc.time)
        }catch(e:SecurityException){error("Location access was revoked. Allow location access in Settings.")}
        finally{cancellation.cancel()}
    }
}
