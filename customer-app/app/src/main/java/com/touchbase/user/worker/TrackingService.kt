package com.touchbase.user.worker

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.remote.ApiModule
import com.touchbase.user.util.SecureLog
import kotlinx.coroutines.*

class TrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        const val CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val TAG = "TrackingService"
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val accountId = intent?.getStringExtra("accountId") ?: return START_NOT_STICKY
        
        startForeground(
            NOTIFICATION_ID,
            createNotification("Device Security Active", "High-precision tracking enabled."),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )

        startLocationUpdates(accountId)
        return START_STICKY
    }

    private fun startLocationUpdates(accountId: String) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60_000L)
            .setMinUpdateIntervalMillis(30_000L)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.let { loc ->
                            uploadLocation(accountId, loc)
                        }
                    }
                },
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            SecureLog.e(TAG, "Location permission denied: ${e.message}")
            stopSelf()
        }
    }

    private fun uploadLocation(accountId: String, location: Location) {
        serviceScope.launch {
            try {
                // In a real production app, we would save to local DB first if offline.
                // For this implementation, we attempt an immediate upload.
                val tokenManager = DeviceTokenManager(applicationContext)
                val signingSecret = tokenManager.apiSecret ?: com.touchbase.user.BuildConfig.HMAC_SECRET
                val api = ApiModule.provideApi(signingSecret, accountId)
                val response = api.reportLocation(
                    mapOf(
                        "accountId" to accountId,
                        "lat" to location.latitude,
                        "lng" to location.longitude,
                        "accuracy" to location.accuracy.toDouble(),
                        "battery" to 100, // Simplified
                        "timestamp" to System.currentTimeMillis() / 1000
                    )
                )
                if (!response.isSuccessful) {
                    SecureLog.w(TAG, "Failed to upload location: ${response.code()}")
                }
            } catch (e: Exception) {
                SecureLog.e(TAG, "Error uploading location: ${e.message}")
            }
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Device Security Tracking",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
