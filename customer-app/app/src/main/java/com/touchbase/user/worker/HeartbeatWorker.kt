package com.touchbase.user.worker

import android.content.Context
import android.content.Intent
import com.touchbase.user.util.SecureLog
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.touchbase.user.data.remote.ApiModule
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.remote.DeviceAuthRecovery
import com.touchbase.user.data.repository.DeviceRepository
import com.touchbase.user.BuildConfig
import java.util.concurrent.TimeUnit

class HeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val tokenManager = DeviceTokenManager(applicationContext)
        val accountId = tokenManager.accountId
        val imei = tokenManager.imei

        if (accountId == null || imei == null) {
            SecureLog.w(TAG, "Not registered, skipping heartbeat")
            return Result.success()
        }

        val recoveredSecret = tokenManager.apiSecret
            ?: DeviceAuthRecovery.ensureDeviceApiSecret(applicationContext, tokenManager)

        val repository = if (recoveredSecret.isNullOrBlank()) {
            SecureLog.w(TAG, "No per-device API secret available yet; heartbeat will use cached/offline state")
            null
        } else {
            // Use a fresh API instance after recovery so legacy app-level caches that
            // were created with the global HMAC secret cannot keep producing 401s.
            val api = com.touchbase.user.data.remote.ApiModule.provideApi(recoveredSecret, accountId)
            DeviceRepository(api, tokenManager)
        }

        // Step 1: Try network heartbeat — may fail when offline
        var heartbeatSucceeded = false
        if (repository != null) {
            runCatching {
                repository.heartbeat()
                heartbeatSucceeded = true
            }.onFailure {
                SecureLog.w(TAG, "Heartbeat network call failed, using cached data", it)
            }
        }

        // Step 2: Always evaluate lock status locally (works offline)
        val account = repository?.account?.value
        val cachedDue = if (account != null) account.nextPaymentDueEpochMillis else tokenManager.cachedNextPaymentDue
        val lockedByDealer = account?.lockedByDealer ?: tokenManager.cachedLockedByDealer
        val trustedNow = tokenManager.getTrustedTimeMillis()
        val status = com.touchbase.user.data.model.DeviceStatus.evaluate(cachedDue, lockedByDealer, trustedNow)
        if (status == com.touchbase.user.data.model.DeviceStatus.LOCKED) {
            SecureLog.w(TAG, "Local evaluation: LOCKED — enforcing lock")
            val intent = Intent(applicationContext, com.touchbase.user.ui.lock.LockTaskActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { applicationContext.startActivity(intent) }
        }

        val stolenNow = account?.isStolen ?: tokenManager.cachedIsStolen
        if (stolenNow) {
            SecureLog.w(TAG, "Stolen flag active — ensuring location tracking service is running")
            runCatching { TrackingService.start(applicationContext, accountId) }
        } else {
            runCatching { TrackingService.stop(applicationContext) }
        }

        // Step 3: Post-sync tasks (only when server reached)
        if (heartbeatSucceeded) {
            runCatching { syncFcmTokenIfNeeded(tokenManager) }

            val frpIds = account?.securityPolicy?.frpAccountIds
                ?: tokenManager.cachedFrpAccountIds
            runCatching { com.touchbase.user.admin.DevicePolicyController(applicationContext).applyBaseLoanSecurity(frpIds) }

            if ((account?.releaseApproved == true || tokenManager.cachedReleaseApproved) &&
                status != com.touchbase.user.data.model.DeviceStatus.LOCKED) {
                SecureLog.i(TAG, "Release approved — removing device management")
                runCatching { repository?.reportReleaseComplete() }
                runCatching { com.touchbase.user.admin.DevicePolicyController(applicationContext).releaseManagementForPaidLoan() }
            }
        }

        return Result.success()
    }

    private suspend fun syncFcmTokenIfNeeded(tokenManager: DeviceTokenManager) {
        val fcmToken = FcmService.getToken() ?: tokenManager.fcmToken ?: return
        if (fcmToken == tokenManager.fcmToken) return
        val accountId = tokenManager.accountId ?: return
        val imei = tokenManager.imei ?: return
        val signingSecret = tokenManager.apiSecret
            ?: DeviceAuthRecovery.ensureDeviceApiSecret(applicationContext, tokenManager)
            ?: run {
                SecureLog.w(TAG, "Skipping FCM token sync: no per-device API secret available")
                return
            }
        val api = ApiModule.provideApi(signingSecret, accountId)
        val response = api.uploadFcmToken(
            mapOf(
                "accountId" to accountId,
                "imei" to imei,
                "fcmToken" to fcmToken
            )
        )
        if (response.isSuccessful) {
            tokenManager.saveFcmToken(fcmToken)
            SecureLog.i(TAG, "FCM token synced to server")
        }
    }

    companion object {
        private const val TAG = "HeartbeatWorker"
        private const val WORK_NAME = "securepay_heartbeat"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(
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
}
