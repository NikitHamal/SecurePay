package com.touchbase.user.worker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.touchbase.user.data.model.LocationReportRequest
import com.touchbase.user.data.model.LocationSample
import com.touchbase.user.data.remote.ApiModule
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.remote.DeviceAuthRecovery
import com.touchbase.user.util.SecureLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class TrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationStore: LocationReportStore
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var locationCallback: LocationCallback? = null

    companion object {
        const val CHANNEL_ID = "tracking_channel_v2"
        const val NOTIFICATION_ID = 1001
        const val TAG = "TrackingService"
        private const val MAX_UPLOAD_BATCH = 50

        fun start(context: Context, accountId: String) {
            val appContext = context.applicationContext
            val tokenManager = DeviceTokenManager(appContext)
            val intent = Intent(appContext, TrackingService::class.java).apply {
                putExtra("accountId", accountId)
                putExtra("imei", tokenManager.imei)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            }.onFailure {
                SecureLog.e(TAG, "Unable to start foreground tracking service: ${it.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context.applicationContext, TrackingService::class.java)
            runCatching { context.applicationContext.stopService(intent) }
        }
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
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification("TB User recovery active", "Secure location reporting is enabled for this financed device.")
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
        return START_NOT_STICKY
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun startLocationUpdates(accountId: String, imei: String) {
        if (locationCallback != null) return

        if (!hasLocationPermission()) {
            SecureLog.e(TAG, "Location permission is missing; cannot start stolen-device GPS tracking")
            stopSelf()
            return
        }

        requestImmediateLocation(accountId, imei)

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60_000L)
            .setMinUpdateIntervalMillis(30_000L)
            .setMinUpdateDistanceMeters(0f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.lastOrNull()?.let { loc ->
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

    private fun requestImmediateLocation(accountId: String, imei: String) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let { queueAndUploadLocation(accountId, imei, it) }
            }
            val tokenSource = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, tokenSource.token)
                .addOnSuccessListener { loc -> loc?.let { queueAndUploadLocation(accountId, imei, it) } }
                .addOnFailureListener { SecureLog.w(TAG, "Immediate GPS fix failed: ${it.message}") }
        } catch (e: SecurityException) {
            SecureLog.e(TAG, "Immediate location permission denied: ${e.message}")
        } catch (e: Exception) {
            SecureLog.w(TAG, "Immediate location request failed: ${e.message}")
        }
    }

    private fun queueAndUploadLocation(accountId: String, imei: String, location: Location) {
        val sample = LocationSample(
            lat = location.latitude,
            lng = location.longitude,
            accuracy = location.accuracy.takeIf { location.hasAccuracy() }?.toDouble(),
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
                val signingSecret = tokenManager.apiSecret
                    ?: DeviceAuthRecovery.ensureDeviceApiSecret(applicationContext, tokenManager)
                    ?: run {
                        SecureLog.w(TAG, "Cannot upload location yet: no per-device API secret available")
                        return@launch
                    }
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SecureLog.w(TAG, "Location upload deferred: ${e.message}")
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
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TB User recovery tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Required while a device reported as stolen is sending its location."
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
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
