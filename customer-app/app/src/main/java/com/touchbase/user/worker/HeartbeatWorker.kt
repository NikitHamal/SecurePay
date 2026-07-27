package com.touchbase.user.worker

import android.content.Context
import com.touchbase.user.util.SecureLog
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.touchbase.user.data.remote.ApiModule
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.remote.DeviceAuthRecovery
import com.touchbase.user.data.remote.DeviceRegistrationRecovery
import com.touchbase.user.data.repository.DeviceRepository
import com.touchbase.user.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class HeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val tokenManager = DeviceTokenManager(applicationContext)
        if (!tokenManager.isRegistered || tokenManager.imei.isNullOrBlank()) {
            val repaired = DeviceRegistrationRecovery.repair(applicationContext, tokenManager)
            if (!repaired) {
                SecureLog.w(TAG, "Not registered; heartbeat will retry after deployment/enrollment is available")
                return Result.retry()
            }
        }
        val accountId = tokenManager.accountId ?: return Result.retry()
        val imei = tokenManager.imei ?: return Result.retry()

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
            try {
                val heartbeatResult = repository.heartbeat()
                heartbeatSucceeded = heartbeatResult.isSuccess
                heartbeatResult.exceptionOrNull()?.let { error ->
                    SecureLog.w(TAG, "Heartbeat network call failed, using cached data: ${error.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SecureLog.w(TAG, "Heartbeat network call failed, using cached data: ${e.message}")
            }
        }

        // Step 2: Evaluate + enforce lock state from the freshest cached data.
        // LockEnforcer is offline-safe and BAL-compliant (it raises a
        // full-screen intent instead of relying on background activity starts),
        // so the phone locks even when this worker runs with the app closed.
        val account = repository?.account?.value
        val status = LockEnforcer.evaluateAndEnforce(applicationContext, "heartbeat")
            ?: com.touchbase.user.data.model.DeviceStatus.ACTIVE

        // Step 2b: Keep the doze-proof deadline alarm aligned with fresh state.
        runCatching { LockDeadlineScheduler.sync(applicationContext) }

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
            runCatching { AppUpdateWorker.runNow(applicationContext) }

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
        private const val WORK_NAME_NOW = "securepay_heartbeat_now"

        /** Fires one immediate best-effort sync (used after the deadline alarm). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<HeartbeatWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_NOW,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(
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
}
