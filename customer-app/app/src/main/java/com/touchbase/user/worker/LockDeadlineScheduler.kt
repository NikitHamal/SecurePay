package com.touchbase.user.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.util.SecureLog

/**
 * Arms an exact, doze-proof AlarmManager alarm for the moment the cached
 * payment deadline passes. This is the primary "locks even when the app is
 * never opened" mechanism:
 *
 *  - `setExactAndAllowWhileIdle` fires even in deep Doze and after the process
 *    has been killed by OEM battery savers (alarms survive process death).
 *  - The alarm time is derived from the server-trusted deadline, converted to
 *    a wall-clock trigger so it survives clock-offset drift.
 *  - [sync] must be re-invoked after every state change (heartbeat success,
 *    boot, FCM message, app open) so the alarm always tracks the newest due
 *    date. Each sync replaces the previous alarm atomically.
 */
object LockDeadlineScheduler {

    private const val TAG = "LockDeadlineScheduler"
    const val ACTION_LOCK_DEADLINE = "com.touchbase.user.action.LOCK_DEADLINE"
    private const val REQUEST_CODE = 4242
    private const val FALLBACK_WINDOW_MS = 60_000L

    /** Reconciles the deadline alarm with the newest cached financing state. */
    fun sync(context: Context) {
        val tokenManager = runCatching { DeviceTokenManager(context) }.getOrNull()
            ?: runCatching { DeviceTokenManager.fallback(context) }.getOrNull()
            ?: return

        if (!tokenManager.isRegistered || tokenManager.cachedReleaseApproved) {
            cancel(context)
            return
        }

        // Dealer/stolen locks are immediate — no scheduling needed.
        if (tokenManager.cachedLockedByDealer || tokenManager.cachedIsStolen) {
            LockEnforcer.evaluateAndEnforce(context, "deadline_sync_immediate")
            return
        }

        val due = tokenManager.cachedNextPaymentDue
        if (due <= 0L) {
            cancel(context)
            return
        }

        val trustedNow = tokenManager.getTrustedTimeMillis()
        if (due <= trustedNow) {
            // Already overdue — enforce right now, no need for an alarm.
            cancel(context)
            LockEnforcer.evaluateAndEnforce(context, "deadline_sync_overdue")
            return
        }

        // Convert trusted-time deadline into a wall-clock trigger time.
        val delayMs = due - trustedNow
        val triggerAt = System.currentTimeMillis() + delayMs
        schedule(context, triggerAt)
    }

    fun schedule(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        val pi = pendingIntent(context)
        runCatching {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                SecureLog.i(TAG, "Lock deadline alarm armed (exact) for epoch=$triggerAtMillis")
            } else {
                // OEM/user denied exact alarms — fall back to a tight window.
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    FALLBACK_WINDOW_MS,
                    pi
                )
                SecureLog.w(TAG, "Exact alarms not permitted; armed 60s window instead")
            }
        }.onFailure { t ->
            SecureLog.e(TAG, "Failed to arm lock deadline alarm", t)
            // Absolute fallback: best-effort inexact alarm still beats nothing.
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent(context))
            }
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return
        runCatching {
            alarmManager.cancel(pendingIntent(context))
            SecureLog.i(TAG, "Lock deadline alarm cancelled")
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LockDeadlineReceiver::class.java)
            .setAction(ACTION_LOCK_DEADLINE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
