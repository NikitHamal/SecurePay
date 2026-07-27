package com.touchbase.agent.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {

    private val nonAuthPaths = listOf("/auth/login", "/auth/register")

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val isAuthEndpoint = nonAuthPaths.any { originalRequest.url.encodedPath.contains(it) }
        val token = tokenManager.token.value
        val request = if (!token.isNullOrEmpty() && !isAuthEndpoint) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(request)

        // The server rejected our token (expired, revoked, agent deactivated…).
        // Nothing in the app can succeed from here, so drop the dead session and
        // let the navigation host send the agent to the sign-in screen right away.
        // Login/register responses are left alone — a wrong password is not a
        // session expiry and must keep showing its own inline error.
        if (response.code == 401 && !isAuthEndpoint && !token.isNullOrEmpty()) {
            tokenManager.clearSession()
            SessionEvents.notifySessionExpired()
        }

        return response
    }
}
