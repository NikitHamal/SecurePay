package com.touchbase.agent.data.remote

import com.touchbase.agent.data.model.*
import retrofit2.http.*

interface SecurePayApi {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("auth/register-agent")
    suspend fun registerAgent(@Body request: RegisterAgentRequest): RegisterAgentResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("accounts")
    suspend fun listAccounts(@Query("status") status: String? = null): List<Account>

    @GET("accounts/{id}")
    suspend fun getAccount(@Path("id") id: String): Account

    @POST("accounts")
    suspend fun createAccount(@Body request: CreateAccountRequest): Account

    @PATCH("accounts/{id}")
    suspend fun updateAccount(@Path("id") id: String, @Body updates: Map<String, @JvmSuppressWildcards Any>): Account

    @DELETE("accounts/{id}")
    suspend fun deleteAccount(@Path("id") id: String): retrofit2.Response<Unit>

    @POST("accounts/{id}/force-lock")
    suspend fun forceLock(@Path("id") id: String): Account

    @POST("accounts/{id}/force-unlock")
    suspend fun forceUnlock(@Path("id") id: String): Account

    @POST("accounts/{id}/release")
    suspend fun approveRelease(
        @Path("id") id: String,
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): Account

    @POST("payments")
    suspend fun recordPayment(@Body request: RecordPaymentRequest): RecordPaymentResponse

    @GET("ledger")
    suspend fun listLedger(
        @Query("method") method: String? = null,
        @Query("accountId") accountId: String? = null
    ): List<LedgerEntry>

    @GET("devices")
    suspend fun listDevices(): List<Device>

    @POST("devices")
    suspend fun addDevice(@Body request: AddDeviceRequest): Device

    @DELETE("devices/{id}")
    suspend fun deleteDevice(@Path("id") id: String): retrofit2.Response<Unit>

    @GET("plans")
    suspend fun listPlans(): List<Plan>

    @GET("kpis")
    suspend fun getKpis(): KpiSummary

    @GET("accounts/{id}/photos/{type}")
    suspend fun getPhoto(
        @Path("id") id: String,
        @Path("type") type: String
    ): retrofit2.Response<okhttp3.ResponseBody>

    @GET("accounts/{id}/location")
    suspend fun getLocation(@Path("id") id: String): LocationResponse

    @GET("device/check")
    suspend fun deviceCheck(@Query("imei") imei: String): DeviceCheckResponse

    @POST("device/heartbeat")
    suspend fun deviceHeartbeat(@Body body: Map<String, String>): DeviceCheckResponse

    @POST("provisioning/qr")
    suspend fun generateProvisioningQr(@Body request: GenerateQrRequest): ProvisioningQrResponse

    @GET("provisioning/qr/{token}")
    suspend fun getProvisioningStatus(@Path("token") token: String): ProvisioningStatusResponse

    @POST("verify")
    suspend fun verifyIdentity(@Body request: VerifyRequest): VerifyResponse
}
