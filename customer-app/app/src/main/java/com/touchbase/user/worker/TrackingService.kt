package com.touchbase.user.worker

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.touchbase.user.data.model.LocationReportRequest
import com.touchbase.user.data.model.LocationSample
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.remote.ApiModule
import com.touchbase.user.util.SecureLog
import kotlinx.coroutines.*

class TrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationStore: LocationReportStore
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var locationCallback: LocationCallback? = null

    companion object {
        const val CHANNEL_ID = "tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val TAG = "TrackingService"
        private const val MAX_UPLOAD_BATCH = 50
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationStore = LocationReportStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tokenManager = DeviceTokenManager(applicationContext)
        val accountId = intent?.getStringExtra("accountId") ?: tokenManager.accountId
        val imei = intent?.getStringExtra("imei") ?: tokenManager.imei

        if (accountId.isNullOrBlank() || imei.isNullOrBlank()) {
            SecureLog.w(TAG, "Missing accountId/IMEI; stopping tracking service")
            return START_NOT_STICKY
        }

        val notification = createNotification("Device Security Active", "High-precision tracking enabled.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationUpdates(accountId, imei)
        flushQueuedLocations(accountId, imei)
        return START_STICKY
    }

    private fun startLocationUpdates(accountId: String, imei: String) {
        if (locationCallback != null) return

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60_000L)
            .setMinUpdateIntervalMillis(30_000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    queueAndUploadLocation(accountId, imei, loc)
                }
            }
        }
        locationCallback = callback

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            SecureLog.e(TAG, "Location permission denied: ${e.message}")
            stopSelf()
        }
    }

    private fun queueAndUploadLocation(accountId: String, imei: String, location: Location) {
        val sample = LocationSample(
            lat = location.latitude,
            lng = location.longitude,
            accuracy = location.accuracy.toDouble(),
            battery = currentBatteryPercent(),
            timestamp = System.currentTimeMillis() / 1000L
        )
        locationStore.enqueue(sample)
        flushQueuedLocations(accountId, imei)
    }

    private fun flushQueuedLocations(accountId: String, imei: String) {
        serviceScope.launch {
            try {
                val tokenManager = DeviceTokenManager(applicationContext)
                val signingSecret = tokenManager.apiSecret ?: com.touchbase.user.BuildConfig.HMAC_SECRET
                val api = ApiModule.provideApi(signingSecret, accountId)
                val batch = locationStore.peek(MAX_UPLOAD_BATCH)
                if (batch.isEmpty()) return@launch

                val response = api.reportLocation(
                    LocationReportRequest(
                        accountId = accountId,
                        imei = imei,
                        logs = batch
                    )
                )
                if (response.isSuccessful) {
                    locationStore.removeFirst(batch.size)
                    SecureLog.i(TAG, "Uploaded ${batch.size} location ping(s)")
                } else {
                    SecureLog.w(TAG, "Failed to upload location: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                SecureLog.e(TAG, "Error uploading location: ${e.message}")
            }
        }
    }

    private fun currentBatteryPercent(): Int? {
        val manager = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
        val value = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return value.takeIf { it in 0..100 }
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
        locationCallback?.let { callback ->
            runCatching { fusedLocationClient.removeLocationUpdates(callback) }
        }
        locationCallback = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
