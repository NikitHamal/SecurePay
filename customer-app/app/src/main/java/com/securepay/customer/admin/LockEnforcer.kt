package com.securepay.customer.admin

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.ComponentName
import android.os.UserManager
import android.util.Log

/**
 * Thin abstraction over [DevicePolicyManager] that encapsulates the DPC
 * enforcement actions used by the financing policy. Every call is guarded with
 * active-admin / device-owner checks and try/catch around [SecurityException]
 * so the app degrades gracefully on devices where SecurePay has not been
 * provisioned as device owner (e.g. a developer machine or emulator).
 *
 * Result: on an unprovisioned device these methods log and no-op rather than
 * crashing, while on a properly provisioned financed device they apply the
 * real platform policy.
 */
class LockEnforcer(context: Context) {

    private val appContext: Context = context.applicationContext
    private val dpm: DevicePolicyManager =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin: ComponentName = SecurePayDeviceAdminReceiver.componentName(appContext)

    /** True if SecurePay's admin component is currently active. */
    fun isAdminActive(): Boolean = try {
        dpm.isAdminActive(admin)
    } catch (e: SecurityException) {
        Log.w(TAG, "isAdminActive check failed", e)
        false
    }

    /** True if SecurePay is the provisioned device owner (full DPC powers). */
    fun isDeviceOwner(): Boolean = try {
        dpm.isDeviceOwnerApp(appContext.packageName)
    } catch (e: SecurityException) {
        Log.w(TAG, "isDeviceOwnerApp check failed", e)
        false
    }

    /**
     * Lock the device immediately. On a provisioned device this calls
     * DevicePolicyManager.lockNow(), which is the force-lock policy declared in
     * device_admin_policies.xml. Requires an active admin.
     *
     * @return true if the lock command was issued.
     */
    fun lockDevice(): Boolean {
        if (!isAdminActive()) {
            Log.i(TAG, "lockDevice skipped — admin not active (not provisioned).")
            return false
        }
        return try {
            // Real enforcement: immediately locks the screen.
            dpm.lockNow()
            Log.i(TAG, "lockNow() issued — device locked by financing policy.")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "lockNow() denied — degrading gracefully.", e)
            false
        }
    }

    /**
     * Restrict USB debugging / developer features so the controller cannot be
     * trivially bypassed while the loan is in default. This requires device
     * owner privileges; it is a no-op otherwise.
     *
     * @return true if the restriction was applied.
     */
    fun restrictDebugging(): Boolean {
        if (!isDeviceOwner()) {
            Log.i(TAG, "restrictDebugging skipped — not device owner.")
            return false
        }
        return try {
            // Block debugging features (USB debugging / ADB) while in default.
            dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            // Belt-and-braces: also clear the global ADB_ENABLED setting.
            dpm.setGlobalSetting(admin, android.provider.Settings.Global.ADB_ENABLED, "0")
            Log.i(TAG, "DISALLOW_DEBUGGING_FEATURES applied.")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "restrictDebugging denied — degrading gracefully.", e)
            false
        }
    }

    /**
     * Release the lock-time restrictions once the loan is brought current.
     * Clears the debugging restriction (device-owner only). The screen lock
     * itself is released by the user authenticating normally.
     *
     * @return true if restrictions were cleared.
     */
    fun releaseLock(): Boolean {
        if (!isDeviceOwner()) {
            Log.i(TAG, "releaseLock skipped — not device owner.")
            return false
        }
        return try {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            dpm.setGlobalSetting(admin, android.provider.Settings.Global.ADB_ENABLED, "1")
            Log.i(TAG, "Financing restrictions released — loan current.")
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "releaseLock denied — degrading gracefully.", e)
            false
        }
    }

    companion object {
        private const val TAG = "SecurePayLockEnforcer"
    }
}
