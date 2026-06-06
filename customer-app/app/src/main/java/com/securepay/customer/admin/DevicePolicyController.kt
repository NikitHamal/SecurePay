package com.securepay.customer.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Thin, defensive wrapper around [DevicePolicyManager] that translates the
 * reactive [com.securepay.customer.data.model.DeviceStatus] into concrete device
 * restrictions for a financed handset.
 *
 * Capabilities are tiered by privilege:
 *  - Plain *device-admin* (what a sideloaded demo build gets): can [lockNow] and
 *    toggle keyguard features.
 *  - *Device-owner* (what a properly provisioned financed unit gets): can also
 *    disable USB debugging (ADB) so the lock cannot be bypassed over a cable.
 *
 * Every privileged call is guarded so the app never crashes on devices where the
 * policy is unavailable — it simply applies the strongest restriction it is
 * permitted to.
 */
class DevicePolicyController(context: Context) {

    private val appContext = context.applicationContext
    private val dpm = appContext
        .getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(appContext, SecurePayDeviceAdminReceiver::class.java)

    val isAdminActive: Boolean
        get() = dpm.isAdminActive(admin)

    private val isDeviceOwner: Boolean
        get() = dpm.isDeviceOwnerApp(appContext.packageName)

    /** Intent that prompts the user to grant SecurePay device-admin rights. */
    fun enableAdminIntent() = android.content.Intent(
        DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
    ).apply {
        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
        putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "SecurePay requires device management to enforce your financing terms."
        )
    }

    /**
     * Enforce the LOCKED posture: harden the device, disable USB debugging where
     * permitted and drop to the secure lock screen.
     */
    fun enforceLock() {
        if (!isAdminActive) {
            Log.w(TAG, "enforceLock requested but device admin is not active.")
            return
        }
        disableUsbDebugging()
        hardenKeyguard()
        runCatching { dpm.lockNow() }
            .onFailure { Log.w(TAG, "lockNow() denied: ${it.message}") }
    }

    /** Release restrictions once the account returns to ACTIVE/WARNING. */
    fun releaseRestrictions() {
        if (!isAdminActive) return
        restoreUsbDebugging()
        runCatching {
            dpm.setKeyguardDisabledFeatures(
                admin,
                DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE
            )
        }.onFailure { Log.w(TAG, "Restoring keyguard features denied: ${it.message}") }
    }

    /**
     * Disable USB debugging (ADB) so the lock screen cannot be circumvented over
     * a wired connection. Requires device-owner privilege.
     */
    private fun disableUsbDebugging() {
        if (!isDeviceOwner) {
            Log.i(TAG, "Not device owner — skipping ADB lockdown (admin-only mode).")
            return
        }
        runCatching {
            dpm.setGlobalSetting(admin, Settings.Global.ADB_ENABLED, "0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.addUserRestriction(
                    admin,
                    android.os.UserManager.DISALLOW_DEBUGGING_FEATURES
                )
            }
        }.onFailure { Log.w(TAG, "Disabling USB debugging denied: ${it.message}") }
    }

    private fun restoreUsbDebugging() {
        if (!isDeviceOwner) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.clearUserRestriction(
                    admin,
                    android.os.UserManager.DISALLOW_DEBUGGING_FEATURES
                )
            }
        }.onFailure { Log.w(TAG, "Restoring USB debugging denied: ${it.message}") }
    }

    private fun hardenKeyguard() {
        runCatching {
            dpm.setKeyguardDisabledFeatures(
                admin,
                DevicePolicyManager.KEYGUARD_DISABLE_TRUST_AGENTS or
                    DevicePolicyManager.KEYGUARD_DISABLE_FINGERPRINT
            )
        }.onFailure { Log.w(TAG, "Hardening keyguard denied: ${it.message}") }
    }

    companion object {
        private const val TAG = "SecurePayDPC"
    }
}
