package com.touchbase.user

import android.app.Application
import android.os.Looper
import android.util.Log
import com.touchbase.user.data.remote.ApiModule
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.remote.SecurePayApi
import com.touchbase.user.data.repository.DeviceRepository
import com.touchbase.user.util.SecureLog
import com.touchbase.user.worker.HeartbeatWorker
import com.touchbase.user.worker.AppUpdateWorker

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

        // Global safety net: background-thread failures during early DPC launch must not
        // kill provisioning. Main-thread crashes still go to Android's normal handler so
        // real UI faults are visible during development and crash reporting.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            SecureLog.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
            if (thread == Looper.getMainLooper().thread) {
                previous?.uncaughtException(thread, throwable)
            }
        }

        runCatching {
            if (tokenManager.isRegistered) {
                HeartbeatWorker.schedule(this)
                AppUpdateWorker.schedule(this)
            }
        }.onFailure { SecureLog.e(TAG, "HeartbeatWorker.schedule failed", it) }
    }

    companion object {
        private const val TAG = "SecurePayApp"
    }
}
