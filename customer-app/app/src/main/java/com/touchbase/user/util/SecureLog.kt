package com.touchbase.user.util

import android.os.Build
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object SecureLog {

    private const val REMOTE_ENDPOINT = "https://securepay-dashboard.pages.dev/api/device/logs"

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
        thread(start = true, isDaemon = true) {
            runCatching {
                val url = URL(REMOTE_ENDPOINT)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.doOutput = true
                conn.connectTimeout = if (force) 8000 else 3000
                conn.readTimeout = if (force) 8000 else 3000

                val escapedMsg = msg.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                val escapedTag = tag.replace("\"", "\\\"")

                val json = """{"tag":"$escapedTag","message":"$escapedMsg","level":"$level"}"""

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(json)
                    writer.flush()
                }

                val code = conn.responseCode
                if (code >= 300) {
                    Log.w("RemoteLog", "Failed to send log: $code")
                }
                conn.disconnect()
            }.onFailure {
                if (force) {
                    Log.e("RemoteLog", "CRITICAL: Failed to send provisioning log: ${it.message}")
                } else {
                    Log.w("RemoteLog", "Error sending remote log: ${it.message}")
                }
            }
        }
    }
}
