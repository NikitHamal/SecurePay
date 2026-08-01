package com.touchbase.agent.data.local

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Best-effort GPS capture for the fraud-prevention trail the client asked for:
 * when a phone is registered into inventory or a customer application is
 * submitted, we attach where the agent physically was.
 *
 * Everything here is best-effort: any failure (no permission, GPS off,
 * timeout) yields null and the action proceeds without coordinates — the
 * server still records the who & when.
 */
object LocationCapture {

    data class GeoFix(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float?
    )

    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun hasPermission(context: Context): Boolean = REQUIRED_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Last known fix first (instant), then a single high-accuracy refresh if
     * the cached value is missing or obviously stale. Total wait is capped.
     */
    suspend fun capture(context: Context, timeoutMillis: Long = 6_000L): GeoFix? = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext null
        runCatching {
            withTimeoutOrNull(timeoutMillis) {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val cached = runCatching { client.lastLocation.await() }.getOrNull()
                if (cached != null && System.currentTimeMillis() - cached.time < 10 * 60 * 1000L) {
                    return@withTimeoutOrNull cached.toGeoFix()
                }
                val fresh = runCatching {
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
                }.getOrNull()
                (fresh ?: cached)?.toGeoFix()
            }
        }.getOrNull()
    }

    private fun Location.toGeoFix(): GeoFix = GeoFix(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null
    )
}
