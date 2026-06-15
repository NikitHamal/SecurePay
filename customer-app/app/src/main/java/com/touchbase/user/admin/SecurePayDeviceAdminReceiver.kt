package com.touchbase.user.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.touchbase.user.util.SecureLog

class SecurePayDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        SecureLog.i(TAG, "SecurePay device administration enabled.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        SecureLog.i(TAG, "SecurePay device administration disabled.")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Removing SecurePay management will violate your financing agreement " +
            "and may immediately restrict this device."
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        SecureLog.w(TAG, "Password failed attempt detected")
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        SecureLog.i(TAG, "Password succeeded")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        SecureLog.i(TAG, "Profile provisioning complete")
    }

    companion object {
        private const val TAG = "SecurePayDPC"
    }
}
