package com.touchbase.agent.data.remote

import com.touchbase.agent.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class SecurePayRepository(
    private val api: SecurePayApi,
    private val tokenManager: TokenManager
) {

    val token: StateFlow<String?> get() = tokenManager.token
    val dealerId: StateFlow<String?> get() = tokenManager.dealerId
    val dealerName: StateFlow<String?> get() = tokenManager.dealerName
    val isLoggedIn: Boolean get() = tokenManager.isLoggedIn

    private fun Throwable.friendlyMessage(): String {
        if (this is HttpException) {
            val body = response()?.errorBody()?.string()
            if (!body.isNullOrBlank()) {
                val match = """"error"\s*:\s*"([^"]+)"""".toRegex().find(body)
                if (match != null) return match.groupValues[1]
                val msgMatch = """"message"\s*:\s*"([^"]+)"""".toRegex().find(body)
                if (msgMatch != null) return msgMatch.groupValues[1]
            }
            return "Request failed (${code()})"
        }
        return message ?: "An unexpected error occurred"
    }

    suspend fun login(email: String, password: String): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.login(LoginRequest(email, password))
            tokenManager.saveSession(response.token, response.dealer.id, response.dealer.name)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(Exception(e.friendlyMessage()))
        }
    }

    suspend fun logout() {
        try { api.logout() } catch (_: Exception) {}
        tokenManager.clearSession()
    }

    suspend fun getKpis(): Result<KpiSummary> = withContext(Dispatchers.IO) {
        try { Result.success(api.getKpis()) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun listAccounts(status: String? = null): Result<List<Account>> = withContext(Dispatchers.IO) {
        try { Result.success(api.listAccounts(status)) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun getAccount(id: String): Result<Account> = withContext(Dispatchers.IO) {
        try { Result.success(api.getAccount(id)) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun createAccount(request: CreateAccountRequest): Result<Account> = withContext(Dispatchers.IO) {
        try { Result.success(api.createAccount(request)) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun updateAccount(id: String, updates: Map<String, @JvmSuppressWildcards Any>): Result<Account> = withContext(Dispatchers.IO) {
        try { Result.success(api.updateAccount(id, updates)) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun forceLock(id: String): Result<Account> = withContext(Dispatchers.IO) {
        try { Result.success(api.forceLock(id)) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun forceUnlock(id: String): Result<Account> = withContext(Dispatchers.IO) {
        try { Result.success(api.forceUnlock(id)) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun approveRelease(id: String, allowEarlyRelease: Boolean): Result<Account> = withContext(Dispatchers.IO) {
        try {
            val body = mapOf(
                "allowEarlyRelease" to allowEarlyRelease,
                "note" to if (allowEarlyRelease) "Manual settlement release approved from TB Agent" else "Paid-off release approved from TB Agent"
            )
            Result.success(api.approveRelease(id, body))
        } catch (e: Exception) {
            Result.failure(Exception(e.friendlyMessage()))
        }
    }

    suspend fun recordPayment(request: RecordPaymentRequest): Result<RecordPaymentResponse> = withContext(Dispatchers.IO) {
        try { Result.success(api.recordPayment(request)) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun listLedger(method: String? = null, accountId: String? = null): Result<List<LedgerEntry>> = withContext(Dispatchers.IO) {
        try { Result.success(api.listLedger(method, accountId)) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun listDevices(): Result<List<Device>> = withContext(Dispatchers.IO) {
        try { Result.success(api.listDevices()) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun addDevice(imei: String, model: String): Result<Device> = withContext(Dispatchers.IO) {
        try { Result.success(api.addDevice(AddDeviceRequest(imei, model))) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun listPlans(): Result<List<Plan>> = withContext(Dispatchers.IO) {
        try { Result.success(api.listPlans()) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun deviceCheck(imei: String): Result<DeviceCheckResponse> = withContext(Dispatchers.IO) {
        try { Result.success(api.deviceCheck(imei)) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun generateProvisioningQr(
        imei: String,
        wifiSsid: String?,
        wifiPassword: String?
    ): Result<ProvisioningQrResponse> = withContext(Dispatchers.IO) {
        try {
            Result.success(api.generateProvisioningQr(GenerateQrRequest(imei, wifiSsid, wifiPassword)))
        } catch (e: Exception) {
            Result.failure(Exception(e.friendlyMessage()))
        }
    }

    suspend fun getProvisioningStatus(token: String): Result<ProvisioningStatusResponse> = withContext(Dispatchers.IO) {
        try { Result.success(api.getProvisioningStatus(token)) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }
}
