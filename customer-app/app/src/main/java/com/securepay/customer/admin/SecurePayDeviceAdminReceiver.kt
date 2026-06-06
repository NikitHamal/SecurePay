package com.securepay.customer.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device administrator receiver for the SecurePay DPC. Registering this receiver
 * is what unlocks the [android.app.admin.DevicePolicyManager] capabilities used
 * by [DevicePolicyController] to enforce the LOCKED state (e.g. disabling USB
 * debugging and forcing the lock screen) until the financed balance is settled.
 */
class SecurePayDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "SecurePay device administration enabled.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "SecurePay device administration disabled.")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Removing SecurePay management will violate your financing agreement " +
            "and may immediately restrict this device."
    }

    companion object {
        private const val TAG = "SecurePayDPC"
    }
}
