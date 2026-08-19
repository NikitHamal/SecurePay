package com.touchbase.user.ui.kiosk

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.touchbase.user.util.SecureLog

object KioskManager {
    private const val TAG = "KioskManager"
    private const val PREFS_NAME = "kiosk_prefs"
    private const val KEY_ORIGINAL_LAUNCHER = "original_launcher"

    /**
     * Disables the device's default home launcher so KioskLauncherActivity
     * becomes the sole HOME activity. Saves the original launcher package
     * for later restoration.
     */
    fun enterKioskMode(context: Context) {
        val pm = context.packageManager
        val ourPackage = context.packageName

        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfos = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)

        val originalLauncher = resolveInfos.firstOrNull {
            it.activityInfo.packageName != ourPackage
        }?.activityInfo?.packageName

        if (originalLauncher == null) {
            SecureLog.i(TAG, "No external launcher found to disable")
            return
        }

        SecureLog.i(TAG, "Disabling original launcher: $originalLauncher")

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ORIGINAL_LAUNCHER, originalLauncher)
            .apply()

        val homeActivities = resolveInfos.filter {
            it.activityInfo.packageName == originalLauncher
        }
        for (info in homeActivities) {
            val cn = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
            runCatching {
                pm.setComponentEnabledSetting(
                    cn,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }.onFailure { SecureLog.e(TAG, "Failed to disable ${cn.flattenToString()}", it) }
        }
    }

    /**
     * Re-enables the original launcher and disables KioskLauncherActivity,
     * restoring normal phone behaviour after the customer has been onboarded.
     */
    fun restoreLauncher(context: Context) {
        if (!isKioskActive(context)) return

        val pm = context.packageManager
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val originalLauncher = prefs.getString(KEY_ORIGINAL_LAUNCHER, null)

        if (originalLauncher != null) {
            SecureLog.i(TAG, "Restoring original launcher: $originalLauncher")

            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfos = pm.queryIntentActivities(
                homeIntent,
                PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DEFAULT_ONLY
            )
            val launcherActivities = resolveInfos.filter {
                it.activityInfo.packageName == originalLauncher
            }
            for (info in launcherActivities) {
                val cn = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                runCatching {
                    pm.setComponentEnabledSetting(
                        cn,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }.onFailure { SecureLog.e(TAG, "Failed to enable ${cn.flattenToString()}", it) }
            }
        }

        SecureLog.i(TAG, "Disabling KioskLauncherActivity")
        val kioskComponent = ComponentName(context, KioskLauncherActivity::class.java)
        runCatching {
            pm.setComponentEnabledSetting(
                kioskComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }.onFailure { SecureLog.e(TAG, "Failed to disable KioskLauncherActivity", it) }

        prefs.edit().remove(KEY_ORIGINAL_LAUNCHER).apply()
    }

    /**
     * Returns true if KioskLauncherActivity is currently enabled (kiosk mode is active).
     */
    fun isKioskActive(context: Context): Boolean {
        return runCatching {
            val kioskComponent = ComponentName(context, KioskLauncherActivity::class.java)
            val state = context.packageManager.getComponentEnabledSetting(kioskComponent)
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }.getOrDefault(false)
    }
}
