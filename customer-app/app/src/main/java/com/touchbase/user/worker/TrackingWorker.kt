package com.touchbase.user.worker

import android.content.Context
import android.content.Intent
import androidx.work.*
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.remote.ApiModule
import com.touchbase.user.data.remote.DeviceAuthRecovery
import com.touchbase.user.data.remote.DeviceRegistrationRecovery
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
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
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
            if (!tokenManager.isRegistered || tokenManager.imei.isNullOrBlank()) {
                val repaired = DeviceRegistrationRecovery.repair(applicationContext, tokenManager)
                if (!repaired) {
                    SecureLog.w(TAG, "Account/IMEI still unavailable; tracking check will retry")
                    return Result.retry()
                }
            }
            val imei = tokenManager.imei ?: return Result.retry()
            val accountId = tokenManager.accountId.orEmpty()
            if (accountId.isBlank()) return Result.retry()

            val signingSecret = tokenManager.apiSecret
                ?: DeviceAuthRecovery.ensureDeviceApiSecret(applicationContext, tokenManager)

            if (signingSecret.isNullOrBlank()) {
                SecureLog.w(TAG, "No per-device API secret available yet; tracking check will retry later")
                return Result.retry()
            }

            val resolvedAccountId = tokenManager.accountId ?: accountId
            val api = ApiModule.provideApi(signingSecret, resolvedAccountId)
            val response = api.deviceCheck(imei, resolvedAccountId)

            if (response.account != null) {
                if (response.apiSecret.isNotBlank() && response.apiSecret != signingSecret) {
                    tokenManager.saveDevice(response.account.id, imei, response.apiSecret)
                }
                tokenManager.saveCachedStatus(
                    nextPaymentDue = response.account.nextPaymentDue,
                    lockedByDealer = response.account.status.equals("LOCKED", ignoreCase = true) || response.account.status.equals("STOLEN", ignoreCase = true),
                    releaseApproved = response.account.releaseApproved,
                    isStolen = response.account.isStolen
                )

                val trackedAccountId = response.account.id.ifBlank { resolvedAccountId }
                val isStolen = response.account.isStolen
                if (isStolen) {
                    SecureLog.i(TAG, "Device is flagged as STOLEN. Starting tracking service.")
                    TrackingService.start(applicationContext, trackedAccountId)
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
