package com.touchbase.user.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.touchbase.user.util.SecureLog
import java.util.concurrent.TimeUnit

/**
 * Offline-capable lock watchdog — the safety net under the deadline alarm.
 *
 * Unlike [HeartbeatWorker] this carries NO network constraint: even with zero
 * connectivity it re-evaluates the cached financing state every ~15 minutes
 * and enforces the lock if the deadline slipped past while alarms were
 * blocked (force-stopped app, revoked exact-alarm permission, OEM quirks).
 * Network sync remains HeartbeatWorker's job; this worker only guards
 * enforcement with locally cached truth.
 */
class LockWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val status = LockEnforcer.evaluateAndEnforce(applicationContext, "watchdog")
        SecureLog.i(TAG, "Watchdog tick — status=$status")
        // Keep the exact deadline alarm aligned with the newest cache.
        LockDeadlineScheduler.sync(applicationContext)
        return Result.success()
    }

    companion object {
        private const val TAG = "LockWatchdogWorker"
        private const val WORK_NAME = "securepay_lock_watchdog"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LockWatchdogWorker>(
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
