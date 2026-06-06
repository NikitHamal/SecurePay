package com.securepay.customer.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Device Policy Controller (DPC) receiver for SecurePay. The platform
 * instantiates this when the financing admin is enabled/disabled. It is the
 * anchor the [LockEnforcer] targets when issuing policy commands.
 *
 * This is the standard, documented Android device-admin pattern used by
 * legitimate device-financing products to enforce payment status.
 */
class SecurePayDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "SecurePay device admin enabled — financing policy active.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "SecurePay device admin disabled — financing policy released.")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Warning shown to the user if they attempt to remove the controller.
        return "Removing SecurePay will violate your financing agreement. " +
            "The device may be locked until the outstanding balance is settled."
    }

    companion object {
        private const val TAG = "SecurePayDPC"

        /** ComponentName the DevicePolicyManager calls reference. */
        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, SecurePayDeviceAdminReceiver::class.java)
    }
}
