package com.touchbase.user.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.touchbase.user.util.SecureLog
import com.touchbase.user.admin.DevicePolicyController
import com.touchbase.user.data.model.DeviceStatus
import com.touchbase.user.data.remote.DeviceTokenManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return

        SecureLog.i(TAG, "Boot received: ${intent.action}, scheduling heartbeat and enforcing lock state")

        HeartbeatWorker.schedule(context)
        AppUpdateWorker.schedule(context)

        val policyController = DevicePolicyController(context)
        val tokenManager = DeviceTokenManager(context)

        if (!tokenManager.isRegistered) {
            SecureLog.i(TAG, "Device not registered, skipping lock enforcement")
            return
        }

        if (!tokenManager.cachedReleaseApproved) {
            policyController.applyBaseLoanSecurity(tokenManager.cachedFrpAccountIds)
        }

        if (tokenManager.cachedReleaseApproved) {
            SecureLog.i(TAG, "Release approved, skipping lock enforcement")
            policyController.releaseManagementForPaidLoan()
            return
        }

        val cachedDue = tokenManager.cachedNextPaymentDue
        val cachedLockedByDealer = tokenManager.cachedLockedByDealer

        if (cachedDue <= 0L) {
            SecureLog.w(TAG, "No cached payment due data, cannot determine lock state")
            return
        }

        val trustedTime = tokenManager.getTrustedTimeMillis()
        val status = DeviceStatus.evaluate(cachedDue, cachedLockedByDealer, trustedTime)
        if (status == DeviceStatus.LOCKED) {
            SecureLog.w(TAG, "Device should be LOCKED based on cached data, enforcing lock + pinning")
            policyController.enforceLock(tokenManager.cachedFrpAccountIds)
            launchLockTask(context)
        } else {
            SecureLog.i(TAG, "Device status: $status, releasing restrictions")
            policyController.releaseRestrictions()
        }
    }

    private fun launchLockTask(context: Context) {
        runCatching {
            val intent = Intent(context, com.touchbase.user.ui.lock.LockTaskActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)
        }.onFailure { SecureLog.e(TAG, "Failed to launch LockTaskActivity", it) }
    }

    companion object {
        private const val TAG = "BootReceiver"
        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON"
        )
    }
}
