package com.touchbase.user.data.remote

import com.touchbase.user.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit

object ApiModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

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
                // Use the platform trust store. The app is hosted on Cloudflare Pages,
                // whose serving certificate/key can rotate outside our control; hard-coded
                // leaf/intermediate pins can strand every managed device after rotation.
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

    /**
     * Non-throwing variant: skips cert pinning entirely. Used only as a last-resort
     * fallback if [provideApi] throws during DPC first launch, so the app never dies
     * before provisioning can complete.
     */
    @Synchronized
    fun provideApiSafe(deviceSecret: String = ""): SecurePayApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (deviceSecret.isNotEmpty()) {
                    addInterceptor(HmacInterceptor(deviceSecret))
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
