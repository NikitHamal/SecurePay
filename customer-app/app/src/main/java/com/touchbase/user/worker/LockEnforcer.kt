package com.touchbase.user.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.touchbase.user.admin.DevicePolicyController
import com.touchbase.user.data.model.DeviceStatus
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.ui.lock.LockTaskActivity
import com.touchbase.user.util.SecureLog

/**
 * Single source of truth for enforcing an overdue/dealer lock.
 *
 * Why this exists: previously the only lock trigger was HeartbeatWorker doing
 * `context.startActivity()`. Two failure modes came out of that:
 *
 *  1. HeartbeatWorker carries a NetworkType.CONNECTED constraint, so a phone
 *     that stays offline never even evaluates the locally cached deadline.
 *  2. Android 10+ silently blocks activity launches from the background
 *     (Background Activity Launch restrictions). The worker "locked" in the
 *     logs but nothing appeared on screen — the customer only got locked the
 *     next time they opened the app themselves.
 *
 * LockEnforcer fixes both: it is callable from any worker/receiver without a
 * network, it always applies DPM hardening (lockNow + restrictions — these
 * work from any process state), and it raises a high-priority full-screen
 * intent notification, which is the BAL-compliant way (same mechanism alarm
 * clocks use) to surface LockTaskActivity over the keyguard from background.
 */
object LockEnforcer {

    private const val TAG = "LockEnforcer"
    const val LOCK_CHANNEL_ID = "securepay_lock_alert"
    private const val LOCK_NOTIFICATION_ID = 41001

    /**
     * Evaluates the locally cached financing state and enforces a lock if the
     * deadline has passed or a dealer/stolen lock is cached. Never touches the
     * network. Returns the evaluated status, or null when there is nothing
     * cached to evaluate yet.
     */
    fun evaluateAndEnforce(context: Context, source: String): DeviceStatus? {
        val tokenManager = runCatching { DeviceTokenManager(context) }.getOrNull()
            ?: runCatching { DeviceTokenManager.fallback(context) }.getOrNull()
            ?: return null

        if (!tokenManager.isRegistered) return null

        if (tokenManager.cachedReleaseApproved) {
            cancelLockAlert(context)
            return DeviceStatus.ACTIVE
        }

        val due = tokenManager.cachedNextPaymentDue
        val lockedByDealer = tokenManager.cachedLockedByDealer
        val stolen = tokenManager.cachedIsStolen

        if (due <= 0L && !lockedByDealer && !stolen) {
            SecureLog.w(TAG, "[$source] No cached payment deadline yet; skipping evaluation")
            return null
        }

        val trustedNow = tokenManager.getTrustedTimeMillis()
        var status = DeviceStatus.evaluate(due, lockedByDealer, trustedNow)
        if (stolen) status = DeviceStatus.LOCKED

        if (status == DeviceStatus.LOCKED) {
            enforceNow(
                context = context,
                source = source,
                frpAccountIds = tokenManager.cachedFrpAccountIds,
                stolen = stolen,
                accountId = tokenManager.accountId
            )
        } else {
            // A payment caught up before the deadline: drop any stale alert.
            cancelLockAlert(context)
        }
        return status
    }

    /**
     * Applies every available lock primitive immediately. Safe to call from
     * any thread/process state; each step is individually failure-tolerant.
     */
    fun enforceNow(
        context: Context,
        source: String,
        frpAccountIds: List<String> = emptyList(),
        stolen: Boolean = false,
        accountId: String? = null
    ) {
        SecureLog.w(TAG, "[$source] Enforcing device lock NOW")

        // 1. DPM hardening + lockNow() — works from background, no activity needed.
        runCatching { DevicePolicyController(context).enforceLock(frpAccountIds) }
            .onFailure { SecureLog.e(TAG, "enforceLock failed", it) }

        if (stolen && !accountId.isNullOrBlank()) {
            runCatching { TrackingService.start(context, accountId) }
        }

        // 2. Direct activity start. Works when the app is foreground/recent;
        //    silently blocked by BAL otherwise — that is fine, step 3 covers it.
        runCatching {
            val direct = Intent(context, LockTaskActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                )
            }
            context.startActivity(direct)
        }.onFailure {
            SecureLog.w(TAG, "Direct lock activity start blocked (background): ${it.message}")
        }

        // 3. Full-screen intent notification — the Android-10+-compliant way to
        //    launch over the keyguard from a background process.
        showLockFullScreenIntent(context)
    }

    private fun showLockFullScreenIntent(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LOCK_CHANNEL_ID,
                "Payment lock alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shown when the device locks because a payment is overdue"
                setBypassDnd(true)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }

        val lockIntent = Intent(context, LockTaskActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val fullScreenPi = PendingIntent.getActivity(context, 0, lockIntent, piFlags)

        val notification = NotificationCompat.Builder(context, LOCK_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Device locked")
            .setContentText("A scheduled payment is overdue. Open Touch Base to see how to unlock.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .build()

        runCatching { nm.notify(LOCK_NOTIFICATION_ID, notification) }
            .onFailure { SecureLog.w(TAG, "Lock alert notify failed: ${it.message}") }
    }

    fun cancelLockAlert(context: Context) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(LOCK_NOTIFICATION_ID)
        }
    }
}
