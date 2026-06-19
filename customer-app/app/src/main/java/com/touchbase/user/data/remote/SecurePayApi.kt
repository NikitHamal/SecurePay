package com.touchbase.user.data.remote

import com.touchbase.user.data.model.AccountResponse
import com.touchbase.user.data.model.ActivateResponse
import com.touchbase.user.data.model.DeviceCheckResponse
import com.touchbase.user.data.model.PaymentsResponse
import com.touchbase.user.data.model.ReleaseCompleteResponse
import com.touchbase.user.data.model.AppUpdateResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SecurePayApi {

    @GET("device/check")
    suspend fun deviceCheck(@Query("imei") imei: String): DeviceCheckResponse

    @POST("device/activate")
    suspend fun activate(@Body body: Map<String, @JvmSuppressWildcards String>): ActivateResponse

    @POST("device/provisioned")
    suspend fun reportProvisioned(
        @Body body: Map<String, @JvmSuppressWildcards String>
    ): retrofit2.Response<Unit>

    @POST("device/heartbeat")
    suspend fun deviceHeartbeat(@Body body: Map<String, @JvmSuppressWildcards String>): DeviceCheckResponse

    @GET("device/account")
    suspend fun getAccount(
        @Query("accountId") accountId: String,
        @Query("imei") imei: String
    ): AccountResponse

    @GET("device/payments")
    suspend fun getPayments(
        @Query("accountId") accountId: String,
        @Query("imei") imei: String
    ): PaymentsResponse

    @POST("device/release-complete")
    suspend fun releaseComplete(@Body body: Map<String, @JvmSuppressWildcards String>): ReleaseCompleteResponse

    @GET("device/app-update")
    suspend fun appUpdate(@Query("currentVersionCode") currentVersionCode: Int): AppUpdateResponse
}
