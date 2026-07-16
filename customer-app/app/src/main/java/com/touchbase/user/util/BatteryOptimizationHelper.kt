package com.touchbase.user.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import com.touchbase.user.data.remote.DeviceTokenManager

object BatteryOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) return

        try {
            val intent = Intent(
                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(
                android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Opens the system battery exemption flow only after an explicit user action.
     * The app deliberately does not call this on startup, which avoids a recurring
     * system dialog every time the customer opens TB User.
     */
    fun requestIfRegistered(context: Context) {
        val tokenManager = DeviceTokenManager(context)
        if (!tokenManager.isRegistered) return
        requestIgnoreBatteryOptimizations(context)
    }
}
