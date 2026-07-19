package com.touchbase.agent.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

    private val nonAuthPaths = listOf("/auth/login", "/auth/register")

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val token = tokenManager.token.value
        val request = if (!token.isNullOrEmpty() && !nonAuthPaths.any { originalRequest.url.encodedPath.contains(it) }) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(request)

        // Do NOT auto-clear the session on 401.
        // The token is kept in encrypted storage so the user stays logged in
        // across app restarts. Only explicit logout clears it.
        return response
    }
}
