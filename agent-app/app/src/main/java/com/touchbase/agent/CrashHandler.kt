package com.touchbase.agent

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.PrintWriter
import java.io.StringWriter

class CrashHandler(private val app: Application) : Thread.UncaughtExceptionHandler {

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val prefs = app.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)

        if (prefs.getBoolean(KEY_SHOWING_CRASH, false)) {
            defaultHandler?.uncaughtException(thread, throwable)
            return
        }

        val sw = StringWriter()
        PrintWriter(sw).use { throwable.printStackTrace(it) }
        val trace = sw.toString()
        val builder = StringBuilder()

        builder.appendLine("=== CRASH ===")
        builder.appendLine("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        builder.appendLine("Thread: ${thread.name}")
        builder.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (API ${Build.VERSION.SDK_INT})")
        builder.appendLine()
        builder.append(trace)

        prefs.edit()
            .putString(KEY_TRACE, builder.toString())
            .putString(KEY_THREAD, thread.name)
            .putLong(KEY_TIME, System.currentTimeMillis())
            .putString(KEY_DEVICE, "${Build.MANUFACTURER} ${Build.MODEL}")
            .putInt(KEY_API, Build.VERSION.SDK_INT)
            .putBoolean(KEY_SHOWING_CRASH, false)
            .commit()

        try {
            val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            if (intent != null) {
                app.startActivity(intent)
            }
        } catch (_: Exception) {}

        Process.killProcess(Process.myPid())
        System.exit(1)
    }

    companion object {
        const val CRASH_PREFS = "securepay_crash"
        const val KEY_TRACE = "crash_trace"
        const val KEY_THREAD = "crash_thread"
        const val KEY_TIME = "crash_time"
        const val KEY_DEVICE = "crash_device"
        const val KEY_API = "crash_api"
        const val KEY_SHOWING_CRASH = "crash_showing"
    }
}
