package com.securepay.customer.policy

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.securepay.customer.receiver.SecurePayDeviceAdminReceiver

class DevicePolicyController(private val context: Context) {
    private val devicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, SecurePayDeviceAdminReceiver::class.java)

    fun enforceLockedMode(activity: Activity) {
        hideSystemBars(activity)
        disableUsbDebuggingIfPermitted()
        disableStatusBarIfPermitted(disabled = true)
        enterLockTaskIfPermitted(activity)
    }

    fun clearLockedMode(activity: Activity) {
        disableStatusBarIfPermitted(disabled = false)
        showSystemBars(activity)
        runCatching {
            if (devicePolicyManager.isLockTaskPermitted(context.packageName)) {
                activity.stopLockTask()
            }
        }
    }

    fun openEmergencyDialer() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun disableUsbDebuggingIfPermitted() {
        if (!isDeviceOwner()) return
        runCatching {
            devicePolicyManager.setGlobalSetting(
                adminComponent,
                Settings.Global.ADB_ENABLED,
                "0"
            )
        }
    }

    private fun enterLockTaskIfPermitted(activity: Activity) {
        if (isDeviceOwner()) {
            runCatching {
                devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(context.packageName))
            }
        }

        runCatching {
            if (devicePolicyManager.isLockTaskPermitted(context.packageName)) {
                activity.startLockTask()
            }
        }
    }

    private fun disableStatusBarIfPermitted(disabled: Boolean) {
        if (!isDeviceOwner() || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            devicePolicyManager.setStatusBarDisabled(adminComponent, disabled)
        }
    }

    private fun hideSystemBars(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun showSystemBars(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    private fun isDeviceOwner(): Boolean =
        devicePolicyManager.isDeviceOwnerApp(context.packageName)
}

