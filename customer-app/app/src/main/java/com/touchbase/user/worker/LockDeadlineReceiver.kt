package com.touchbase.user.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.touchbase.user.util.SecureLog

/**
 * Fired by [LockDeadlineScheduler]'s exact alarm when the cached payment
 * deadline passes — with or without network, with or without the app running.
 * This is the fix for "the phone only locked after I opened the app".
 */
class LockDeadlineReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LockDeadlineScheduler.ACTION_LOCK_DEADLINE) return
        SecureLog.w(TAG, "Lock deadline alarm fired — enforcing")

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        Thread {
            try {
                // 1. Enforce from cache (offline-safe, instant).
                LockEnforcer.evaluateAndEnforce(appContext, "deadline_alarm")

                // 2. Re-arm for the next deadline (or cancel when released).
                LockDeadlineScheduler.sync(appContext)

                // 3. Best-effort server refresh so a payment made just before
                //    the deadline (or a dealer grace extension) can rescind
                //    the lock as soon as connectivity allows.
                runCatching { HeartbeatWorker.runNow(appContext) }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "LockDeadlineReceiver"
    }
}
