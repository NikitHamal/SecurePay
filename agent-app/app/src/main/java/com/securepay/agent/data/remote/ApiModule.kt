package com.securepay.agent.data.remote

import com.securepay.agent.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object ApiModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun provideApi(tokenManager: TokenManager): SecurePayApi {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenManager))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SecurePayApi::class.java)
    }
}