package com.touchbase.user.util

import android.os.Build
import android.util.Log
import com.touchbase.user.BuildConfig
import com.touchbase.user.admin.SecurityChecker
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

object SecureLog {

    private val remoteEndpoint: String
        get() = BuildConfig.API_BASE_URL.trimEnd('/') + "/device/logs"

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        sendRemoteLog("INFO", tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        sendRemoteLog("WARN", tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable) {
        Log.w(tag, msg, tr)
        sendRemoteLog("WARN", tag, "$msg\n${Log.getStackTraceString(tr)}")
    }

    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        sendRemoteLog("ERROR", tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        Log.e(tag, msg, tr)
        sendRemoteLog("ERROR", tag, "$msg\n${Log.getStackTraceString(tr)}")
    }

    /**
     * Special method for provisioning failures.
     * Always sends full stack trace + device info.
     */
    fun provisioningError(tag: String, msg: String, tr: Throwable? = null) {
        val deviceInfo = buildString {
            append("Model=${Build.MODEL}, ")
            append("Manufacturer=${Build.MANUFACTURER}, ")
            append("Android=${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ")
            append("Device=${Build.DEVICE}")
        }
        val fullMessage = if (tr != null) {
            "$msg\n[Device: $deviceInfo]\n${Log.getStackTraceString(tr)}"
        } else {
            "$msg\n[Device: $deviceInfo]"
        }
        Log.e(tag, fullMessage)
        sendRemoteLog("PROVISIONING_ERROR", tag, fullMessage, force = true)
    }

    /**
     * Force send a critical log even during early provisioning.
     */
    fun forceError(tag: String, msg: String, tr: Throwable? = null) {
        val message = if (tr != null) "$msg\n${Log.getStackTraceString(tr)}" else msg
        Log.e(tag, message)
        sendRemoteLog("ERROR", tag, message, force = true)
    }

    private fun sendRemoteLog(level: String, tag: String, msg: String, force: Boolean = false) {
        // Use non-daemon thread for force/critical logs so the JVM (and Android system)
        // waits for the POST to complete before killing the process. isDaemon=true
        // risks losing the log if the activity finishes immediately after the call.
        val daemon = !force
        thread(start = true, isDaemon = daemon) {
            var attempt = 0
            val maxAttempts = if (force) 2 else 1
            while (attempt < maxAttempts) {
                attempt++
                val success = runCatching {
                    val secret = BuildConfig.HMAC_SECRET
                    require(secret.isNotBlank()) { "HMAC secret is not configured" }

                    val url = URL(remoteEndpoint)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.doOutput = true
                    conn.connectTimeout = if (force) 15000 else 5000
                    conn.readTimeout = if (force) 15000 else 5000

                    val escapedMsg = msg.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r")
                        .replace("\t", "\\t")
                    val escapedTag = tag.replace("\"", "\\\"")
                    val json = """{"tag":"$escapedTag","message":"$escapedMsg","level":"$level"}"""

                    val timestamp = System.currentTimeMillis().toString()
                    val nonce = UUID.randomUUID().toString()
                    val bodyHash = SecurityChecker.generateHmac(secret, json)
                    val path = url.path + (url.query?.let { "?$it" } ?: "")
                    val signature = SecurityChecker.generateHmac(
                        secret,
                        "POST\n$path\n$timestamp\n$nonce\n$bodyHash"
                    )
                    conn.setRequestProperty("X-Signature", signature)
                    conn.setRequestProperty("X-Timestamp", timestamp)
                    conn.setRequestProperty("X-Nonce", nonce)

                    OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                        writer.write(json)
                        writer.flush()
                    }

                    val code = conn.responseCode
                    conn.disconnect()
                    code in 200..299
                }.getOrDefault(false)

                if (success) break

                if (!success && attempt < maxAttempts) {
                    runCatching { Thread.sleep(1000L) }
                }
            }
        }
    }
}
