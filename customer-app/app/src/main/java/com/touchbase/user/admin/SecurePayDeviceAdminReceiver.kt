package com.touchbase.user.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.touchbase.user.util.SecureLog

class SecurePayDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        SecureLog.forceError(TAG, "DeviceAdminReceiver.onEnabled() called — DPC is being enabled by system")
        ProvisioningExtrasStore.recordStage(context, "DEVICE_ADMIN_ENABLED")
        ProvisioningExtrasStore.persistFromIntent(context, intent)
        SecureLog.i(TAG, "Touch Base device administration enabled.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        SecureLog.i(TAG, "Touch Base device administration disabled.")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Removing Touch Base management will violate your financing agreement " +
            "and may immediately restrict this device."
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        SecureLog.w(TAG, "Password failed attempt detected")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        SecureLog.i(TAG, "Password succeeded")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        SecureLog.i(TAG, "Profile/device provisioning complete broadcast")
        runCatching {
            ProvisioningFinalizer.finalizeProvisioning(
                context = context,
                sourceIntent = intent,
                stage = "PROFILE_PROVISIONING_COMPLETE"
            )
        }.onFailure { SecureLog.e(TAG, "Provisioning broadcast finalization failed", it) }
    }

    companion object {
        private const val TAG = "SecurePayDPC"
    }
}
