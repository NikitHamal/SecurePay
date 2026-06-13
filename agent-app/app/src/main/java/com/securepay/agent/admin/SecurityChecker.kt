package com.securepay.agent.admin

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SecurityChecker {

    data class SecurityReport(
        val isRooted: Boolean = false,
        val isDebuggable: Boolean = false,
        val isEmulator: Boolean = false,
        val isTampered: Boolean = false
    ) {
        val isSecure: Boolean get() = !isRooted && !isTampered
        val hasWarnings: Boolean get() = isRooted || isDebuggable || isEmulator || isTampered
        val shouldBlock: Boolean get() = isRooted || isTampered
    }

    fun runAllChecks(context: Context): SecurityReport {
        return SecurityReport(
            isRooted = checkRooted(),
            isDebuggable = checkDebuggable(context),
            isEmulator = checkEmulator(),
            isTampered = checkTampering(context)
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
            "/sbin/.magisk",
            "/system/app/SuperSU.apk",
            "/system/etc/init.d/99SuperSUDaemon",
            "/system/xbin/daemonsu",
            "/system/bin/.ext/.su",
            "/system/usr/we-need-root/su-backup",
            "/system/xbin/.su"
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

        try {
            val process = Runtime.getRuntime().exec(arrayOf("su"))
            val exitCode = process.waitFor()
            if (exitCode == 0) return true
        } catch (_: Exception) {
        }

        try {
            if (File("/system/bin/su").canExecute()) return true
        } catch (_: Exception) {
        }

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
        val board = android.os.Build.BOARD.lowercase()
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()

        val emulatorIndicators = listOf(
            "sdk", "google_sdk", "emulator", "android_sdk",
            "genymotion", "vbox", "nox", "bluestacks", "memu",
            "andy", "x86", "arm64-v8a_test"
        )

        if (emulatorIndicators.any { product.contains(it) }) return true
        if (emulatorIndicators.any { model.contains(it) }) return true
        if (emulatorIndicators.any { brand.contains(it) }) return true
        if (emulatorIndicators.any { device.contains(it) }) return true
        if (hardware.contains("goldfish") || hardware.contains("ranchu")) return true
        if (fingerprint.contains("generic") || fingerprint.contains("emulator")) return true
        if (board.contains("unknown") && brand.contains("generic") && device.contains("generic")) return true
        if (manufacturer.contains("genymotion") || manufacturer.contains("nox")) return true

        try {
            val qemu = File("/dev/qemu_pipe").exists() || File("/dev/goldfish_pipe").exists()
            if (qemu) return true
        } catch (_: Exception) {
        }

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
            "com.securepay.customer"
        )

        if (installer == null) {
            if (!checkDebuggable(context)) {
                Log.w(TAG, "App sideloaded with no installer package — possible tampering")
                return true
            }
        } else if (installer !in validInstallers) {
            Log.w(TAG, "App installed from untrusted source: $installer")
            return true
        }

        try {
            val pm = context.packageManager
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            }

            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signersAreValid(signatures)) {
            } else {
                if (checkDebuggable(context)) {
                    Log.w(TAG, "APK signature mismatch — allowing in debug build")
                } else {
                    Log.w(TAG, "APK signature mismatch — possible tampering")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification failed", e)
            if (!checkDebuggable(context)) return true
        }

        return false
    }

    private fun signersAreValid(signatures: Array<android.content.pm.Signature>?): Boolean {
        if (signatures == null || signatures.isEmpty()) return false
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        for (sig in signatures) {
            val hash = digest.digest(sig.toByteArray())
            val hex = hash.joinToString("") { "%02x".format(it) }
            if (hex in EXPECTED_SIGNING_HASHES) return true
        }
        return false
    }

    fun generateHmac(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private const val TAG = "SecurityChecker"

    private val EXPECTED_SIGNING_HASHES = setOf<String>(
    )
}