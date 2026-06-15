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
        // Full Google Trust Services chain for securepay-dashboard.pages.dev.
        // All three (leaf + intermediate + root) are pinned so a leaf rotation
        // by Cloudflare does not break the app.
        .add(PRODUCTION_HOST, "sha256/jBqfNvskKyGlqbyIS8u9xDE1GpTNnO88vExuhOj+RgA=") // leaf: CN=securepay-dashboard.pages.dev
        .add(PRODUCTION_HOST, "sha256/H7AMYAvicN2+UcFPBz3kJXCDmGrTItZh4ujUBK8hoWg=") // intermediate: CN=WE1, Google Trust Services
        .add(PRODUCTION_HOST, "sha256/YSoUL4CBzo5aJ/ES9gSZTsavsgtHsiLLnTG+BKUdork=") // root: CN=GTS Root R4
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