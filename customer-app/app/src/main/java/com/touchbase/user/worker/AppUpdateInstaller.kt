package com.touchbase.user.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.touchbase.user.util.SecureLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

object AppUpdateInstaller {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun downloadVerifyAndInstall(
        context: Context,
        apkUrl: String,
        expectedSha256Base64: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!apkUrl.startsWith("https://")) error("Update APK URL must be HTTPS")
            val request = Request.Builder().url(apkUrl).get().build()
            val apkBytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("APK download failed: HTTP ${response.code}")
                response.body?.bytes() ?: error("Empty APK response")
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(apkBytes)
            val actual = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
            if (actual != expectedSha256Base64) {
                error("APK checksum mismatch")
            }

            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apkBytes.size.toLong())
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apkBytes.size.toLong()).use { out ->
                    out.write(apkBytes)
                    session.fsync(out)
                }
                val intent = Intent(context, AppUpdateReceiver::class.java).apply {
                    action = ACTION_INSTALL_COMMIT
                    putExtra("sessionId", sessionId)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
            true
        } catch (e: Exception) {
            SecureLog.e(TAG, "Self-update failed", e)
            false
        }
    }

    private const val TAG = "AppUpdateInstaller"
    private const val ACTION_INSTALL_COMMIT = "com.touchbase.user.action.APP_UPDATE_COMMIT"
}
