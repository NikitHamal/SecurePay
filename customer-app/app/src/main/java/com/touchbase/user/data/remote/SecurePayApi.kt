package com.touchbase.user.data.remote

import com.touchbase.user.data.model.AccountResponse
import com.touchbase.user.data.model.DeviceCheckResponse
import com.touchbase.user.data.model.PaymentsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SecurePayApi {

    @GET("device/check")
    suspend fun deviceCheck(@Query("imei") imei: String): DeviceCheckResponse

    @POST("device/heartbeat")
    suspend fun deviceHeartbeat(@Body body: Map<String, @JvmSuppressWildcards String>): DeviceCheckResponse

    @GET("accounts/{id}")
    suspend fun getAccount(@Path("id") id: String): AccountResponse

    @GET("device/payments")
    suspend fun getPayments(@Query("accountId") accountId: String): PaymentsResponse
}
