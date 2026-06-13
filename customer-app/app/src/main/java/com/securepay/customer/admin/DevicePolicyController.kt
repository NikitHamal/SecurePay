package com.securepay.customer.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import com.securepay.customer.util.SecureLog

class DevicePolicyController(context: Context) {

    private val appContext = context.applicationContext
    private val dpm = appContext
        .getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(appContext, SecurePayDeviceAdminReceiver::class.java)

    val isAdminActive: Boolean
        get() = dpm.isAdminActive(admin)

    private val isDeviceOwner: Boolean
        get() = dpm.isDeviceOwnerApp(appContext.packageName)

    fun enableAdminIntent() = android.content.Intent(
        DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
    ).apply {
        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
        putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "SecurePay requires device management to enforce your financing terms."
        )
    }

    fun enforceLock() {
        if (!isAdminActive) {
            SecureLog.w(TAG, "enforceLock requested but device admin is not active.")
            return
        }
        disableUsbDebugging()
        blockScreenCapture()
        hardenKeyguard()
        setPermittedInputMethods()
        runCatching { dpm.lockNow() }
            .onFailure { SecureLog.w(TAG, "lockNow() denied: ${it.message}") }
    }

    fun releaseRestrictions() {
        if (!isAdminActive) return
        restoreUsbDebugging()
        allowScreenCapture()
        runCatching {
            dpm.setKeyguardDisabledFeatures(
                admin,
                DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE
            )
        }.onFailure { SecureLog.w(TAG, "Restoring keyguard features denied: ${it.message}") }
        clearPermittedInputMethods()
    }

    fun startLockTask(activity: android.app.Activity) {
        if (!isDeviceOwner) {
            SecureLog.w(TAG, "startLockTask requires device owner privilege")
            return
        }
        runCatching {
            dpm.setLockTaskPackages(admin, arrayOf(appContext.packageName))
            activity.startLockTask()
        }.onFailure { SecureLog.w(TAG, "startLockTask failed: ${it.message}") }
    }

    fun stopLockTask(activity: android.app.Activity) {
        if (!isDeviceOwner) return
        runCatching { activity.stopLockTask() }
            .onFailure { SecureLog.w(TAG, "stopLockTask failed: ${it.message}") }
    }

    fun hideApp(packageName: String) {
        if (!isDeviceOwner) {
            SecureLog.i(TAG, "hideApp requires device owner — skipping")
            return
        }
        runCatching { dpm.setApplicationHidden(admin, packageName, true) }
            .onFailure { SecureLog.w(TAG, "hideApp($packageName) denied: ${it.message}") }
    }

    fun unhideApp(packageName: String) {
        if (!isDeviceOwner) return
        runCatching { dpm.setApplicationHidden(admin, packageName, false) }
            .onFailure { SecureLog.w(TAG, "unhideApp($packageName) denied: ${it.message}") }
    }

    fun enableSystemApp(packageName: String) {
        if (!isDeviceOwner) return
        runCatching { dpm.enableSystemApp(admin, packageName) }
            .onFailure { SecureLog.w(TAG, "enableSystemApp($packageName) denied: ${it.message}") }
    }

    fun setStayOnWhilePluggedIn() {
        if (!isDeviceOwner) return
        runCatching {
            dpm.setGlobalSetting(
                admin,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                "3"
            )
        }.onFailure { SecureLog.w(TAG, "setStayOnWhilePluggedIn denied: ${it.message}") }
    }

    fun disableCamera() {
        if (!isDeviceOwner) {
            runCatching {
                dpm.setCameraDisabled(admin, true)
            }.onFailure { SecureLog.w(TAG, "disableCamera denied: ${it.message}") }
            return
        }
        runCatching { dpm.setCameraDisabled(admin, true) }
            .onFailure { SecureLog.w(TAG, "disableCamera (DO) denied: ${it.message}") }
    }

    fun enableCamera() {
        runCatching { dpm.setCameraDisabled(admin, false) }
            .onFailure { SecureLog.w(TAG, "enableCamera denied: ${it.message}") }
    }

    fun setMaximumTimeToLock(timeoutMs: Long) {
        if (!isAdminActive) return
        runCatching { dpm.setMaximumTimeToLock(admin, timeoutMs) }
            .onFailure { SecureLog.w(TAG, "setMaximumTimeToLock denied: ${it.message}") }
    }

    fun setPasswordQuality() {
        if (!isAdminActive) return
        runCatching {
            dpm.setPasswordQuality(admin, DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED)
        }.onFailure { SecureLog.w(TAG, "setPasswordQuality denied: ${it.message}") }
    }

    fun wipeDevice(reason: String) {
        if (!isDeviceOwner) {
            SecureLog.w(TAG, "wipeDevice requires device owner — skipping")
            return
        }
        runCatching { dpm.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE) }
            .onFailure { SecureLog.w(TAG, "wipeDevice denied: ${it.message}") }
    }

    private fun disableUsbDebugging() {
        if (!isDeviceOwner) {
            SecureLog.i(TAG, "Not device owner — skipping ADB lockdown (admin-only mode).")
            return
        }
        runCatching {
            dpm.setGlobalSetting(admin, Settings.Global.ADB_ENABLED, "0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            }
        }.onFailure { SecureLog.w(TAG, "Disabling USB debugging denied: ${it.message}") }
    }

    private fun restoreUsbDebugging() {
        if (!isDeviceOwner) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            }
        }.onFailure { SecureLog.w(TAG, "Restoring USB debugging denied: ${it.message}") }
    }

    private fun blockScreenCapture() {
        if (!isAdminActive) return
        runCatching { dpm.setScreenCaptureDisabled(admin, true) }
            .onFailure { SecureLog.w(TAG, "blockScreenCapture denied: ${it.message}") }
    }

    private fun allowScreenCapture() {
        if (!isAdminActive) return
        runCatching { dpm.setScreenCaptureDisabled(admin, false) }
            .onFailure { SecureLog.w(TAG, "allowScreenCapture denied: ${it.message}") }
    }

    private fun hardenKeyguard() {
        runCatching {
            dpm.setKeyguardDisabledFeatures(
                admin,
                DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS or
                    DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT
            )
        }.onFailure { SecureLog.w(TAG, "Hardening keyguard denied: ${it.message}") }
    }

    private fun setPermittedInputMethods() {
        if (!isDeviceOwner) return
        runCatching {
            val safeInputMethods = setOf(
                "com.google.android.inputmethod.latin",
                "com.android.inputmethod.latin",
                "com.samsung.android.honeyboard",
                "com.sec.android.inputmethod",
                "com.touchtype.swiftkey",
                "com.ghisler.android.quickkeyboard"
            )
            dpm.setPermittedInputMethods(admin, safeInputMethods.toList())
        }.onFailure { SecureLog.w(TAG, "setPermittedInputMethods denied: ${it.message}") }
    }

    private fun clearPermittedInputMethods() {
        if (!isDeviceOwner) return
        runCatching { dpm.setPermittedInputMethods(admin, null as List<String>?) }
            .onFailure { SecureLog.w(TAG, "clearPermittedInputMethods denied: ${it.message}") }
    }

    companion object {
        private const val TAG = "SecurePayDPC"
    }
}