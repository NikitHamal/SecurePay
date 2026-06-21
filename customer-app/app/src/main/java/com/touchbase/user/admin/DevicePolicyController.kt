package com.touchbase.user.admin

import android.app.admin.DevicePolicyManager
import android.app.admin.FactoryResetProtectionPolicy
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import com.touchbase.user.util.SecureLog

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
            "TB User requires device management to enforce your financing terms."
        )
    }

    /**
     * Always-on production baseline for financed devices.
     *
     * This is intentionally separate from the overdue lock screen. A customer who
     * is current on payments should still not be able to remove management with a
     * Settings factory reset, ADB/developer-options path, unknown-source APK, or
     * app-control/uninstall flow. These restrictions are only cleared by the final
     * server-approved release path.
     */
    fun applyBaseLoanSecurity(frpAccountIds: List<String> = emptyList()) {
        val owner = runCatching { isDeviceOwner }.getOrDefault(false)
        SecureLog.i(TAG, "applyBaseLoanSecurity: isDeviceOwner=$owner, isAdminActive=${isAdminActive}, frpIds=${frpAccountIds.size}")
        if (!owner) {
            SecureLog.w(TAG, "Base loan security REQUIRES Device Owner. Restrictions will NOT apply in admin-only mode.")
            return
        }

        disableUsbDebugging()
        setInstallAndAppControlRestrictions()
        setTimeTrustPolicy()
        setStayOnWhilePluggedIn()
        setPasswordQuality()
        setMaximumTimeToLock(30_000L)
        applyFactoryResetProtection(frpAccountIds)
        SecureLog.i(TAG, "applyBaseLoanSecurity: all restrictions applied")
    }

    fun enforceLock(frpAccountIds: List<String> = emptyList()) {
        if (!isAdminActive) {
            SecureLog.w(TAG, "enforceLock requested but device admin is not active.")
            return
        }
        applyBaseLoanSecurity(frpAccountIds)
        blockScreenCapture()
        hardenKeyguard()
        setPermittedInputMethods()
        runCatching { dpm.lockNow() }
            .onFailure { SecureLog.w(TAG, "lockNow() denied: ${it.message}") }
    }

    /**
     * Clears only lock-screen/kiosk hardening after a customer comes current.
     * Do NOT clear base loan restrictions here; the phone is still financed until
     * the server sends releaseApproved=true.
     */
    fun releaseRestrictions() {
        if (!isAdminActive) return
        allowScreenCapture()
        runCatching {
            dpm.setKeyguardDisabledFeatures(
                admin,
                DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE
            )
        }.onFailure { SecureLog.w(TAG, "Restoring keyguard features denied: ${it.message}") }
        clearPermittedInputMethods()
    }

    /**
     * Final loan-settlement path. The DPC is intentionally allowed to remove its
     * own management state after the server approves release, so the customer can
     * uninstall TB User without a factory reset.
     */
    fun releaseManagementForPaidLoan(): Boolean {
        if (!isAdminActive) return true
        releaseRestrictions()
        if (isDeviceOwner) {
            clearBaseLoanSecurityForRelease()
            runCatching { dpm.setLockTaskPackages(admin, emptyArray()) }
                .onFailure { SecureLog.w(TAG, "Clearing lock-task packages denied: ${it.message}") }
            @Suppress("DEPRECATION")
            runCatching { dpm.clearDeviceOwnerApp(appContext.packageName) }
                .onFailure { SecureLog.e(TAG, "clearDeviceOwnerApp failed", it) }
        }
        if (runCatching { dpm.isAdminActive(admin) }.getOrDefault(false)) {
            runCatching { dpm.removeActiveAdmin(admin) }
                .onFailure { SecureLog.e(TAG, "removeActiveAdmin failed", it) }
        }
        return !runCatching { dpm.isDeviceOwnerApp(appContext.packageName) || dpm.isAdminActive(admin) }.getOrDefault(true)
    }

    fun startLockTask(activity: android.app.Activity) {
        if (!isDeviceOwner) {
            SecureLog.w(TAG, "startLockTask requires device owner privilege")
            return
        }
        runCatching {
            dpm.setLockTaskPackages(admin, arrayOf(appContext.packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                )
            }
            activity.startLockTask()
        }.onFailure { SecureLog.w(TAG, "startLockTask failed: ${it.message}") }
    }

    fun stopLockTask(activity: android.app.Activity) {
        if (!isDeviceOwner) {
            activity.finish()
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                )
            }
            activity.stopLockTask()
        }.onFailure { SecureLog.w(TAG, "stopLockTask failed: ${it.message}") }
        activity.finish()
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
        runCatching { dpm.setCameraDisabled(admin, true) }
            .onFailure { SecureLog.w(TAG, "disableCamera denied: ${it.message}") }
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

    private fun disableUsbDebugging() {
        if (!isDeviceOwner) {
            SecureLog.i(TAG, "Not device owner — skipping ADB lockdown (admin-only mode).")
            return
        }
        runCatching {
            dpm.setGlobalSetting(admin, Settings.Global.ADB_ENABLED, "0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
                dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
                dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
                dpm.addUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
            }
        }.onFailure { SecureLog.w(TAG, "Disabling USB debugging denied: ${it.message}") }
    }

    private fun setInstallAndAppControlRestrictions() {
        if (!isDeviceOwner) return
        runCatching {
            dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_MODIFY_ACCOUNTS)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_CREDENTIALS)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
            }
            runCatching { dpm.setSecureSetting(admin, Settings.Secure.INSTALL_NON_MARKET_APPS, "0") }
        }.onFailure { SecureLog.w(TAG, "App/install restrictions denied: ${it.message}") }
    }

    @Suppress("DEPRECATION")
    private fun setTimeTrustPolicy() {
        if (!isDeviceOwner) return
        runCatching { dpm.setAutoTimeRequired(admin, true) }
            .onFailure { SecureLog.w(TAG, "setAutoTimeRequired denied: ${it.message}") }
        runCatching { dpm.setGlobalSetting(admin, Settings.Global.AUTO_TIME, "1") }
        runCatching { dpm.setGlobalSetting(admin, Settings.Global.AUTO_TIME_ZONE, "1") }
    }

    private fun applyFactoryResetProtection(frpAccountIds: List<String>) {
        if (!isDeviceOwner || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val cleanIds = frpAccountIds.map { it.trim() }
            .filter { it.matches(Regex("^[0-9]{6,32}$")) }
            .distinct()
        runCatching {
            val policy = FactoryResetProtectionPolicy.Builder()
                .setFactoryResetProtectionAccounts(cleanIds)
                .setFactoryResetProtectionEnabled(cleanIds.isNotEmpty())
                .build()
            dpm.setFactoryResetProtectionPolicy(admin, policy)
            SecureLog.i(TAG, "EFRP policy applied: ${cleanIds.size} account(s)")
        }.onFailure { SecureLog.w(TAG, "Setting EFRP policy denied: ${it.message}") }
    }

    private fun clearBaseLoanSecurityForRelease() {
        if (!isDeviceOwner) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dpm.setFactoryResetProtectionPolicy(admin, null)
            }
        }.onFailure { SecureLog.w(TAG, "Clearing EFRP policy denied: ${it.message}") }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_USB_FILE_TRANSFER)
            }
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_UNINSTALL_APPS)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_APPS_CONTROL)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_MODIFY_ACCOUNTS)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_CREDENTIALS)
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY)
            }
        }.onFailure { SecureLog.w(TAG, "Clearing base restrictions denied: ${it.message}") }
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
