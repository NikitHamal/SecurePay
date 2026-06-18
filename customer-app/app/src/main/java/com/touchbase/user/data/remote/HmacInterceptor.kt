package com.touchbase.user.data.remote

import com.touchbase.user.BuildConfig
import com.touchbase.user.admin.SecurityChecker
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.util.Locale

class HmacInterceptor(
    private val deviceSecret: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val timestamp = System.currentTimeMillis().toString()
        val nonce = java.util.UUID.randomUUID().toString()

        val method = original.method.uppercase(Locale.US)
        val path = original.url.encodedPath + original.url.query.let { if (it.isNullOrEmpty()) "" else "?$it" }
        val bodyHash = original.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            SecurityChecker.generateHmac(BuildConfig.HMAC_SECRET, buffer.readUtf8())
        } ?: ""

        val stringToSign = "$method\n$path\n$timestamp\n$nonce\n$bodyHash"
        val signature = SecurityChecker.generateHmac(BuildConfig.HMAC_SECRET, stringToSign)

        val authenticated = original.newBuilder()
            .header("X-Signature", signature)
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Device-Id", deviceSecret.takeLast(16))
            .build()

        return chain.proceed(authenticated)
    }
}
