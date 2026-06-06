package com.securepay.agent.data.remote

import com.securepay.agent.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class SecurePayRepository(
    private val api: SecurePayApi,
    private val tokenManager: TokenManager
) {

    val token: StateFlow<String?> get() = tokenManager.token
    val dealerId: StateFlow<String?> get() = tokenManager.dealerId
    val dealerName: StateFlow<String?> get() = tokenManager.dealerName
    val isLoggedIn: Boolean get() = tokenManager.isLoggedIn

    suspend fun login(email: String, password: String): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.login(LoginRequest(email, password))
            tokenManager.saveSession(response.token, response.dealer.id, response.dealer.name)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        try { api.logout() } catch (_: Exception) {}
        tokenManager.clearSession()
    }

    suspend fun getKpis(): Result<KpiSummary> = withContext(Dispatchers.IO) {
        try { Result.success(api.getKpis()) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun listAccounts(status: String? = null): Result<List<Account>> = withContext(Dispatchers.IO) {
        try { Result.success(api.listAccounts(status)) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getAccount(id: String): Result<Account> = withContext(Dispatchers.IO) {
        try { Result.success(api.getAccount(id)) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun createAccount(request: CreateAccountRequest): Result<Account> = withContext(Dispatchers.IO) {
        try { Result.success(api.createAccount(request)) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun forceLock(id: String): Result<Account> = withContext(Dispatchers.IO) {
        try { Result.success(api.forceLock(id)) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun forceUnlock(id: String): Result<Account> = withContext(Dispatchers.IO) {
        try { Result.success(api.forceUnlock(id)) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun recordPayment(request: RecordPaymentRequest): Result<RecordPaymentResponse> = withContext(Dispatchers.IO) {
        try { Result.success(api.recordPayment(request)) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun listLedger(method: String? = null, accountId: String? = null): Result<List<LedgerEntry>> = withContext(Dispatchers.IO) {
        try { Result.success(api.listLedger(method, accountId)) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun listDevices(): Result<List<Device>> = withContext(Dispatchers.IO) {
        try { Result.success(api.listDevices()) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun addDevice(imei: String, model: String): Result<Device> = withContext(Dispatchers.IO) {
        try { Result.success(api.addDevice(AddDeviceRequest(imei, model))) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun listPlans(): Result<List<Plan>> = withContext(Dispatchers.IO) {
        try { Result.success(api.listPlans()) } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun deviceCheck(imei: String): Result<DeviceCheckResponse> = withContext(Dispatchers.IO) {
        try { Result.success(api.deviceCheck(imei)) } catch (e: Exception) { Result.failure(e) }
    }
}