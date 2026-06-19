package com.touchbase.user.worker

import android.content.Context
import android.content.Intent
import com.touchbase.user.util.SecureLog
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.repository.DeviceRepository
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

        return try {
            val app = applicationContext as? com.touchbase.user.SecurePayApplication
            val repository = app?.deviceRepository ?: run {
                val api = com.touchbase.user.data.remote.ApiModule.provideApi(imei)
                DeviceRepository(api, tokenManager)
            }
            repository.heartbeat()
            SecureLog.i(TAG, "Heartbeat successful")

            val account = repository.account.value
            if (account?.releaseApproved == true || tokenManager.cachedReleaseApproved) {
                SecureLog.i(TAG, "Release approved — removing device management")
                runCatching { repository.reportReleaseComplete() }
                runCatching { com.touchbase.user.admin.DevicePolicyController(applicationContext).releaseManagementForPaidLoan() }
                return Result.success()
            }

            val cachedDue = if (account != null) account.nextPaymentDueEpochMillis else tokenManager.cachedNextPaymentDue
            val lockedByDealer = account?.lockedByDealer ?: tokenManager.cachedLockedByDealer
            val trustedNow = tokenManager.getTrustedTimeMillis()
            val status = com.touchbase.user.data.model.DeviceStatus.evaluate(cachedDue, lockedByDealer, trustedNow)
            if (status == com.touchbase.user.data.model.DeviceStatus.LOCKED) {
                SecureLog.w(TAG, "Post-heartbeat status LOCKED — ensuring lock enforced")
                val intent = Intent(applicationContext, com.touchbase.user.ui.lock.LockTaskActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { applicationContext.startActivity(intent) }
            }

            Result.success()
        } catch (e: Exception) {
            SecureLog.e(TAG, "Heartbeat failed, will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "HeartbeatWorker"
        private const val WORK_NAME = "securepay_heartbeat"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(
                4, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
