package com.securepay.customer.data.remote

import com.securepay.customer.data.model.AccountResponse
import com.securepay.customer.data.model.DeviceCheckResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SecurePayApi {

    @GET("device/check")
    suspend fun deviceCheck(@Query("imei") imei: String): DeviceCheckResponse

    @POST("device/heartbeat")
    suspend fun deviceHeartbeat(@Body body: Map<String, @JvmSuppressWildcards String>): DeviceCheckResponse

    @GET("accounts/{id}")
    suspend fun getAccount(@retrofit2.http.Path("id") id: String): AccountResponse
}