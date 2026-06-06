package com.securepay.customer.admin

import android.content.Context
import android.util.Log
import java.io.File

object SecurityChecker {

    data class SecurityReport(
        val isRooted: Boolean = false,
        val isDebuggable: Boolean = false,
        val isEmulator: Boolean = false,
        val isTampered: Boolean = false,
        val isScreenCaptureEnabled: Boolean = false
    ) {
        val isSecure: Boolean get() = !isRooted && !isTampered
        val hasWarnings: Boolean get() = isRooted || isDebuggable || isEmulator || isTampered
    }

    fun runAllChecks(context: Context): SecurityReport {
        return SecurityReport(
            isRooted = checkRooted(),
            isDebuggable = checkDebuggable(context),
            isEmulator = checkEmulator(),
            isTampered = checkTampering(context),
            isScreenCaptureEnabled = false
        )
    }

    fun checkRooted(): Boolean {
        val paths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/magisk/.core/bin/su",
            "/system/app/Magisk.apk",
            "/data/adb/magisk",
            "/sbin/.magisk"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }

        val which = try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            process.inputStream.bufferedReader().readLine()
        } catch (_: Exception) {
            null
        }
        if (!which.isNullOrBlank()) return true

        return false
    }

    fun checkDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun checkEmulator(): Boolean {
        val product = android.os.Build.PRODUCT.lowercase()
        val model = android.os.Build.MODEL.lowercase()
        val brand = android.os.Build.BRAND.lowercase()
        val device = android.os.Build.DEVICE.lowercase()
        val hardware = android.os.Build.HARDWARE.lowercase()
        val fingerprint = android.os.Build.FINGERPRINT.lowercase()

        val emulatorIndicators = listOf(
            "sdk", "google_sdk", "emulator", "android_sdk",
            "genymotion", "vbox", "nox", "bluestacks", "memu"
        )

        if (emulatorIndicators.any { product.contains(it) }) return true
        if (emulatorIndicators.any { model.contains(it) }) return true
        if (emulatorIndicators.any { brand.contains(it) }) return true
        if (emulatorIndicators.any { device.contains(it) }) return true
        if (hardware.contains("goldfish") || hardware.contains("ranchu")) return true
        if (fingerprint.contains("generic") || fingerprint.contains("emulator")) return true

        return false
    }

    fun checkTampering(context: Context): Boolean {
        val installer = try {
            context.packageManager.getInstallerPackageName(context.packageName)
        } catch (_: Exception) {
            null
        }

        val validInstallers = setOf(
            "com.android.vending",
            "com.google.android.feedback",
            "com.securepay.agent"
        )

        if (installer != null && installer !in validInstallers) {
            Log.w(TAG, "App installed from untrusted source: $installer")
        }

        return false
    }

    private const val TAG = "SecurityChecker"
}