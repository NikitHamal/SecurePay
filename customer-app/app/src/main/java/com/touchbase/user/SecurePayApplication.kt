package com.touchbase.user

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Looper
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.touchbase.user.data.remote.ApiModule
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.remote.SecurePayApi
import com.touchbase.user.data.repository.DeviceRepository
import com.touchbase.user.util.SecureLog
import com.touchbase.user.worker.HeartbeatWorker
import com.touchbase.user.worker.AppUpdateWorker
import com.touchbase.user.worker.TrackingWorker
import com.touchbase.user.worker.TrackingService
import kotlinx.coroutines.launch

class SecurePayApplication : Application() {

    @Volatile private var tokenManagerCache: DeviceTokenManager? = null
    @Volatile private var apiCache: SecurePayApi? = null
    @Volatile private var repositoryCache: DeviceRepository? = null

    val tokenManager: DeviceTokenManager
        get() = tokenManagerCache ?: synchronized(this) {
            tokenManagerCache ?: run {
                val tm = runCatching { DeviceTokenManager(this) }.getOrElse {
                    Log.e(TAG, "DeviceTokenManager init failed", it)
                    DeviceTokenManager.fallback(this)
                }
                tokenManagerCache = tm
                tm
            }
        }

    val api: SecurePayApi
        get() = apiCache ?: synchronized(this) {
            apiCache ?: run {
                val signingSecret = runCatching { tokenManager.apiSecret }.getOrNull()
                    ?: BuildConfig.HMAC_SECRET
                val deviceId = runCatching { tokenManager.accountId ?: tokenManager.imei.orEmpty() }.getOrDefault("")
                val a = runCatching { ApiModule.provideApi(signingSecret, deviceId) }.getOrElse {
                    Log.e(TAG, "ApiModule.provideApi failed", it)
                    ApiModule.provideApiSafe(signingSecret, deviceId)
                }
                apiCache = a
                a
            }
        }

    val deviceRepository: DeviceRepository
        get() = repositoryCache ?: synchronized(this) {
            repositoryCache ?: run {
                val r = DeviceRepository(api, tokenManager)
                repositoryCache = r
                r
            }
        }

    override fun onCreate() {
        super.onCreate()

        // Ultra-early log — this runs before almost everything
        SecureLog.forceError(TAG, "Application.onCreate() called — APK started successfully")

        // Global safety net: background-thread failures during early DPC launch must not
        // kill provisioning. Main-thread crashes still go to Android's normal handler so
        // real UI faults are visible during development and crash reporting.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            SecureLog.provisioningError(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            if (thread == Looper.getMainLooper().thread) {
                previous?.uncaughtException(thread, throwable)
            }
        }

        // Initialize Firebase for FCM push notifications. This runs without
        // google-services.json — config values come from BuildConfig.
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val projectId = BuildConfig.FCM_PROJECT_ID
                val appId = BuildConfig.FCM_APPLICATION_ID
                if (projectId.isNotBlank() && appId.isNotBlank()) {
                    val options = FirebaseOptions.Builder()
                        .setProjectId(projectId)
                        .setApiKey(BuildConfig.FCM_API_KEY)
                        .setApplicationId(appId)
                        .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
                        .build()
                    FirebaseApp.initializeApp(this, options)
                    SecureLog.i(TAG, "Firebase initialized for project $projectId")
                } else {
                    SecureLog.w(TAG, "Firebase skipped: FCM_PROJECT_ID or FCM_APPLICATION_ID not configured")
                }
            }
        }.onFailure { SecureLog.w(TAG, "Firebase initialization skipped", it) }

        // Create notification channel for FCM data-message fallback
        runCatching {
            val channel = NotificationChannel(
                FCM_CHANNEL_ID,
                "SecurePay notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Device lock and payment notifications"
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }.onFailure { SecureLog.w(TAG, "Notification channel creation failed", it) }

        runCatching {
            if (tokenManager.isRegistered) {
                HeartbeatWorker.schedule(this)
                AppUpdateWorker.schedule(this)
                TrackingWorker.schedule(this)
            }
        }.onFailure { SecureLog.e(TAG, "Worker scheduling failed", it) }

        runCatching {
            val tm = DeviceTokenManager.fallback(this)
            if (tm.isRegistered && tm.cachedIsStolen) {
                val accountId = tm.accountId ?: ""
                SecureLog.i(TAG, "Startup check: Device is flagged as stolen. Starting tracking service.")
                TrackingService.start(this, accountId)
            }
        }.onFailure { SecureLog.w(TAG, "Failed to start tracking service from cache", it) }

        runCatching {
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
            scope.launch {
                deviceRepository.account.collect { account ->
                    if (account != null) {
                        if (account.isStolen) {
                            SecureLog.i(TAG, "DeviceRepository account updated: isStolen=true. Starting tracking service.")
                            TrackingService.start(this@SecurePayApplication, account.id)
                        } else {
                            SecureLog.i(TAG, "DeviceRepository account updated: isStolen=false. Stopping tracking service.")
                            TrackingService.stop(this@SecurePayApplication)
                        }
                    }
                }
            }
        }.onFailure { SecureLog.w(TAG, "Failed to collect account flow for tracking", it) }
    }

    companion object {
        private const val TAG = "SecurePayApp"
        const val FCM_CHANNEL_ID = "securepay_fcm"
    }
}
