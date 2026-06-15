package com.securepay.agent.data.remote

import com.securepay.agent.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private const val PRODUCTION_HOST = "securepay-dashboard.pages.dev"

    private val certificatePinner = CertificatePinner.Builder()
        .add(PRODUCTION_HOST, "sha256/DjLQVN7foWzyfDfw8rzJplr0P6Qt3ZARrePtjWGnmBo=") // leaf: CN=securepay-dashboard.pages.dev
        .add(PRODUCTION_HOST, "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=") // intermediate: CN=WE1, Google Trust Services
        .add(PRODUCTION_HOST, "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=") // root: CN=GTS Root R4
        .build()

    fun provideApi(tokenManager: TokenManager): SecurePayApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(AuthInterceptor(tokenManager))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    })
                }
                if (!BuildConfig.DEBUG) {
                    certificatePinner(certificatePinner)
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