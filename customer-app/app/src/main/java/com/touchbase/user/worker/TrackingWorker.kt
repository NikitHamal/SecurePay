package com.touchbase.user.worker

import android.content.Context
import android.content.Intent
import androidx.work.*
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.remote.ApiModule
import com.touchbase.user.util.SecureLog
import java.util.concurrent.TimeUnit

class TrackingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "TrackingWorker"
        private const val WORK_NAME = "securepay_tracking"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TrackingWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val tokenManager = DeviceTokenManager(applicationContext)
            val imei = tokenManager.imei
            val accountId = tokenManager.accountId ?: ""
            if (imei == null || accountId.isBlank()) {
                SecureLog.w(TAG, "No account/IMEI found, skipping tracking check")
                return Result.success()
            }

            val signingSecret = tokenManager.apiSecret ?: com.touchbase.user.BuildConfig.HMAC_SECRET
            val api = ApiModule.provideApi(signingSecret, accountId)
            val response = api.deviceCheck(imei, accountId)

            if (response.account != null) {
                val isStolen = response.account.isStolen
                if (isStolen) {
                    SecureLog.i(TAG, "Device is flagged as STOLEN. Starting tracking service.")
                    TrackingService.start(applicationContext, accountId)
                } else {
                    TrackingService.stop(applicationContext)
                }
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            SecureLog.e(TAG, "Tracking worker failed: ${e.message}")
            Result.retry()
        }
    }
}
