package com.touchbase.user.worker

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.touchbase.user.admin.SecurePayDeviceAdminReceiver
import com.touchbase.user.util.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

object AppUpdateInstaller {
    private const val MAX_APK_BYTES = 250L * 1024L * 1024L

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(2, TimeUnit.MINUTES)
        .build()

    suspend fun downloadVerifyAndInstall(
        context: Context,
        apkUrl: String,
        expectedSha256Base64: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            require(apkUrl.startsWith("https://")) { "Update APK URL must be HTTPS" }
            require(expectedSha256Base64.matches(Regex("^[A-Za-z0-9_-]{43}$"))) { "Invalid update checksum" }

            val request = Request.Builder().url(apkUrl).get().build()
            val apkBytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("APK download failed: HTTP ${response.code}")
                val declaredLength = response.body?.contentLength() ?: -1L
                if (declaredLength > MAX_APK_BYTES) error("APK exceeds the maximum allowed size")
                val bytes = response.body?.bytes() ?: error("Empty APK response")
                if (bytes.size.toLong() > MAX_APK_BYTES) error("APK exceeds the maximum allowed size")
                bytes
            }

            val digest = MessageDigest.getInstance("SHA-256").digest(apkBytes)
            val actual = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
            if (!MessageDigest.isEqual(actual.toByteArray(), expectedSha256Base64.toByteArray())) {
                error("APK checksum mismatch")
            }

            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apkBytes.size.toLong())
                setInstallReason(PackageManager.INSTALL_REASON_POLICY)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDeviceOwner(context)) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apkBytes.size.toLong()).use { output ->
                    output.write(apkBytes)
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
                session.commit(pending.intentSender)
            }
            true
        } catch (e: Exception) {
            SecureLog.e(TAG, "Self-update failed", e)
            false
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
