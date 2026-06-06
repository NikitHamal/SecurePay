package com.securepay.customer.policy

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.securepay.customer.domain.PolicyActionResult

class DevicePolicyController(context: Context) {
    private val appContext = context.applicationContext
    private val devicePolicyManager =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(appContext, SecurePayDeviceAdminReceiver::class.java)

    fun enforceLockedMode(activity: Activity): PolicyActionResult {
        val packageName = appContext.packageName
        val isDeviceOwner = devicePolicyManager.isDeviceOwnerApp(packageName)
        val isProfileOwner = devicePolicyManager.isProfileOwnerApp(packageName)
        val canUseOwnerApis = isDeviceOwner || isProfileOwner

        var lockTaskAttempted = false
        var adbDisableAttempted = false
        var forceLockAttempted = false
        val messages = mutableListOf<String>()

        if (canUseOwnerApis) {
            runCatching {
                devicePolicyManager.setLockTaskPackages(adminComponent, arrayOf(packageName))
                activity.startLockTask()
                lockTaskAttempted = true
            }.onFailure { messages += "Lock task unavailable: ${it.message.orEmpty()}" }

            runCatching {
                devicePolicyManager.setGlobalSetting(
                    adminComponent,
                    Settings.Global.ADB_ENABLED,
                    "0"
                )
                adbDisableAttempted = true
            }.onFailure { messages += "ADB policy unavailable: ${it.message.orEmpty()}" }
        } else {
            messages += "Device owner enrollment required for lock task and USB debugging policy."
        }

        runCatching {
            if (devicePolicyManager.isAdminActive(adminComponent)) {
                devicePolicyManager.lockNow()
                forceLockAttempted = true
            }
        }.onFailure { messages += "Immediate lock unavailable: ${it.message.orEmpty()}" }

        return PolicyActionResult(
            lockTaskAttempted = lockTaskAttempted,
            adbDisableAttempted = adbDisableAttempted,
            forceLockAttempted = forceLockAttempted,
            deviceOwnerMode = canUseOwnerApis,
            message = messages.ifEmpty { listOf("Locked policy hooks executed.") }.joinToString(" ")
        )
    }
}
