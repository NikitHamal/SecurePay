package com.touchbase.user.util

import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object SecureLog {

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

    private fun sendRemoteLog(level: String, tag: String, msg: String) {
        thread(start = true, isDaemon = true) {
            runCatching {
                val endpoint = "https://securepay-dashboard.pages.dev/api/device/logs"
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.doOutput = true
                conn.connectTimeout = 3000
                conn.readTimeout = 3000

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
                    Log.w("RemoteLog", "Failed to send log to remote server: $code")
                }
                conn.disconnect()
            }.onFailure {
                Log.w("RemoteLog", "Error sending remote log: ${it.message}")
            }
        }
    }
}
