package com.securepay.customer.util

import android.util.Log
import com.securepay.customer.BuildConfig

object SecureLog {

    private const val MAX_TAG_LENGTH = 23

    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable) {
        Log.w(tag, msg, tr)
    }

    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
    }

    fun e(tag: String, msg: String, tr: Throwable) {
        Log.e(tag, msg, tr)
    }
}