package com.securepay.customer.data.remote

import com.securepay.customer.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.CertificatePinner
import java.util.concurrent.TimeUnit

object ApiModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private const val PRODUCTION_HOST = "securepay-dashboard.pages.dev"

    private val certificatePinner = CertificatePinner.Builder()
        .add(PRODUCTION_HOST, "sha256/47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=")
        .add(PRODUCTION_HOST, "sha256/YLh1dUR9y6Kja30RrAn7JKnbQG/uEtJIkOgcoS5jC7M=")
        .build()

    private var cachedApi: SecurePayApi? = null
    private var cachedDeviceSecret: String? = null

    @Synchronized
    fun provideApi(deviceSecret: String = ""): SecurePayApi {
        if (cachedApi != null && cachedDeviceSecret == deviceSecret) {
            return cachedApi!!
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (deviceSecret.isNotEmpty()) {
                    addInterceptor(HmacInterceptor(deviceSecret))
                }
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

        cachedApi = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SecurePayApi::class.java)

        cachedDeviceSecret = deviceSecret
        return cachedApi!!
    }
}