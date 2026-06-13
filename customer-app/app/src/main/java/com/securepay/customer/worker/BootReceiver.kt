package com.securepay.customer.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.securepay.customer.util.SecureSecureLog
import com.securepay.customer.admin.DevicePolicyController
import com.securepay.customer.data.model.DeviceStatus
import com.securepay.customer.data.remote.DeviceTokenManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BOOT_ACTIONS) return

        SecureLog.i(TAG, "Boot received: ${intent.action}, scheduling heartbeat and enforcing lock state")

        HeartbeatWorker.schedule(context)

        val policyController = DevicePolicyController(context)
        val tokenManager = DeviceTokenManager(context)

        if (!tokenManager.isRegistered) {
            SecureLog.i(TAG, "Device not registered, skipping lock enforcement")
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
            SecureLog.w(TAG, "Device should be LOCKED based on cached data, enforcing lock")
            policyController.enforceLock()
        } else {
            SecureLog.i(TAG, "Device status: $status, releasing restrictions")
            policyController.releaseRestrictions()
        }
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