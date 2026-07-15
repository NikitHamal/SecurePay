package com.touchbase.user.worker

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import com.touchbase.user.admin.SecurePayDeviceAdminReceiver
import com.touchbase.user.util.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

object AppUpdateInstaller {
    private const val MAX_APK_BYTES = 250L * 1024L * 1024L
    private const val MIN_BATTERY_PERCENT = 30
    private const val MIN_FREE_BYTES = 512L * 1024L * 1024L

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .build()

    suspend fun downloadVerifyAndInstall(
        context: Context,
        apkUrl: String,
        expectedSha256Base64: String,
        expectedSignatureChecksumBase64: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            require(apkUrl.startsWith("https://")) { "Update APK URL must be HTTPS" }
            require(expectedSha256Base64.matches(Regex("^[A-Za-z0-9_-]{43}$"))) { "Invalid update checksum" }

            val batteryLevel = getBatteryLevel(context)
            val charging = isCharging(context)
            if (batteryLevel in 0..MIN_BATTERY_PERCENT && !charging) {
                SecureLog.w(TAG, "Battery too low ($batteryLevel%) and not charging — deferring update")
                return@withContext false
            }

            val freeBytes = getFreeBytes(context)
            if (freeBytes < MIN_FREE_BYTES) {
                SecureLog.w(TAG, "Insufficient storage (${freeBytes / 1024 / 1024}MB free) — deferring update")
                return@withContext false
            }

            val request = Request.Builder().url(apkUrl).get().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) error("APK download failed: HTTP ${response.code}")

            val body = response.body ?: error("Empty APK response")
            val declaredLength = body.contentLength()
            if (declaredLength > MAX_APK_BYTES) error("APK exceeds the maximum allowed size")

            val tempFile = File(context.cacheDir, "update_${System.nanoTime()}.apk")
            try {
                val digest = MessageDigest.getInstance("SHA-256")

                body.byteStream().use { input ->
                    DigestInputStream(input, digest).use { dis ->
                        tempFile.outputStream().use { output ->
                            dis.copyTo(output)
                        }
                    }
                }

                if (tempFile.length() > MAX_APK_BYTES) error("APK exceeds the maximum allowed size")

                val actualSha = Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest())
                if (!MessageDigest.isEqual(actualSha.toByteArray(), expectedSha256Base64.toByteArray())) {
                    error("APK checksum mismatch")
                }

                if (!expectedSignatureChecksumBase64.isNullOrBlank()) {
                    val actualCertHash = getSigningCertHash(context)
                    if (actualCertHash != null &&
                        !MessageDigest.isEqual(actualCertHash.toByteArray(), expectedSignatureChecksumBase64.toByteArray())
                    ) {
                        error("APK signing certificate mismatch")
                    }
                }

                val installer = context.packageManager.packageInstaller
                abandonStaleSessions(installer)

                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    setSize(tempFile.length())
                    setInstallReason(PackageManager.INSTALL_REASON_POLICY)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDeviceOwner(context)) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }
                val sessionId = installer.createSession(params)
                var error: Throwable? = null
                installer.openSession(sessionId).use { session ->
                    session.openWrite("base.apk", 0, tempFile.length()).use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                        session.fsync(output)
                    }
                    val intent = Intent(context, AppUpdateReceiver::class.java).apply {
                        action = ACTION_INSTALL_COMMIT
                        putExtra("sessionId", sessionId)
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE
                        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE
                        else 0
                    val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                    try {
                        session.commit(pending.intentSender)
                    } catch (e: Exception) {
                        error = e
                    }
                }
                if (error != null) {
                    installer.abandonSession(sessionId)
                    throw error!!
                }
                true
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            SecureLog.e(TAG, "Self-update failed", e)
            false
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        return runCatching {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra("level", -1) ?: -1
            val scale = intent?.getIntExtra("scale", 100) ?: 100
            if (level < 0 || scale <= 0) -1
            else (level * 100 / scale)
        }.getOrDefault(-1)
    }

    private fun isCharging(context: Context): Boolean {
        return runCatching {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = intent?.getIntExtra("status", -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }.getOrDefault(false)
    }

    private fun getFreeBytes(context: Context): Long {
        return try {
            StatFs(context.filesDir.absolutePath).availableBytes
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    private fun getSigningCertHash(context: Context): String? {
        return runCatching {
            val pm = context.packageManager
            val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                info.signingInfo.apkContentsSigners[0].toByteArray()
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                info.signatures[0].toByteArray()
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
            Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }.getOrNull()
    }

    private fun abandonStaleSessions(installer: PackageInstaller) {
        for (sessionInfo in installer.allSessions) {
            if (sessionInfo.appPackageName == "com.touchbase.securepay.client") {
                runCatching { installer.abandonSession(sessionInfo.sessionId) }
            }
        }
    }

    private fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        val admin = ComponentName(context, SecurePayDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(admin) && dpm.isDeviceOwnerApp(context.packageName)
    }

    private const val TAG = "AppUpdateInstaller"
    private const val ACTION_INSTALL_COMMIT = "com.touchbase.user.action.APP_UPDATE_COMMIT"
}
