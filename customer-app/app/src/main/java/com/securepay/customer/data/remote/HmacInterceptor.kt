package com.securepay.customer.data.remote

import com.securepay.customer.admin.SecurityChecker
import okhttp3.Interceptor
import okhttp3.Response
import java.util.Locale

class HmacInterceptor(
    private val deviceSecret: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val timestamp = System.currentTimeMillis().toString()
        val nonce = java.util.UUID.randomUUID().toString()

        val method = original.method.uppercase(Locale.US)
        val path = original.url.encodedPath
        val bodyHash = original.body?.let { body ->
            val bytes = body.bytes()
            SecurityChecker.generateHmac(deviceSecret, bytes.toString(Charsets.UTF_8))
        } ?: ""

        val stringToSign = "$method\n$path\n$timestamp\n$nonce\n$bodyHash"
        val signature = SecurityChecker.generateHmac(deviceSecret, stringToSign)

        val authenticated = original.newBuilder()
            .header("X-Signature", signature)
            .header("X-Timestamp", timestamp)
            .header("X-Nonce", nonce)
            .header("X-Device-Id", deviceSecret.takeLast(16))
            .build()

        return chain.proceed(authenticated)
    }
}