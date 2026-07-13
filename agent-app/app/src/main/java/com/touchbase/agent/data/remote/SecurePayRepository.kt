package com.touchbase.agent.data.remote

import com.touchbase.agent.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import android.graphics.Bitmap
import android.graphics.BitmapFactory

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
            tokenManager.saveSession(
                response.token,
                response.dealer.id,
                response.dealer.name,
                response.dealer.role
            )
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(Exception(e.friendlyMessage()))
        }
    }

    suspend fun registerAgent(
        fullName: String,
        email: String,
        phone: String,
        password: String
    ): Result<RegisterAgentResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.registerAgent(
                RegisterAgentRequest(fullName, email, phone, password)
            )
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

    suspend fun updateAccount(id: String, request: UpdateAccountRequest): Result<Account> = withContext(Dispatchers.IO) {
        try { Result.success(api.updateAccount(id, request)) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun deleteAccount(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteAccount(id)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Delete account failed"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.friendlyMessage()))
        }
    }

    suspend fun resetCustomerPin(id: String): Result<CustomerCredentials> = withContext(Dispatchers.IO) {
        try { Result.success(api.resetCustomerPin(id)) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun forceLock(id: String): Result<Account> = withContext(Dispatchers.IO) {
        try { Result.success(api.forceLock(id)) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun forceUnlock(id: String): Result<Account> = withContext(Dispatchers.IO) {
        try { Result.success(api.forceUnlock(id)) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun approveRelease(id: String, allowEarlyRelease: Boolean): Result<Account> = withContext(Dispatchers.IO) {
        try {
            val request = ReleaseAccountRequest(
                allowEarlyRelease = allowEarlyRelease,
                note = if (allowEarlyRelease) {
                    "Manual settlement release approved from TB Agent"
                } else {
                    "Paid-off release approved from TB Agent"
                }
            )
            Result.success(api.approveRelease(id, request))
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

    suspend fun deleteDevice(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteDevice(id)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Delete device failed"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.friendlyMessage()))
        }
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

    suspend fun getPhoto(id: String, type: String): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPhoto(id, type)
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    val bytes = body.bytes()
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        Result.success(bitmap)
                    } else {
                        Result.failure(Exception("Failed to decode bitmap"))
                    }
                } else {
                    Result.failure(Exception("Empty response body"))
                }
            } else {
                Result.failure(Exception("Failed to load photo: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLocation(accountId: String): Result<LocationResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getLocation(accountId)
            if (!response.hasCoordinates) {
                Result.failure(Exception(response.message ?: "No location ping has been received yet."))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.friendlyMessage()))
        }
    }

    suspend fun verifyIdentity(accountId: String): Result<VerifyResponse> = withContext(Dispatchers.IO) {
        try {
            Result.success(api.verifyIdentity(VerifyRequest(accountId)))
        } catch (e: Exception) {
            Result.failure(Exception(e.friendlyMessage()))
        }
    }

    suspend fun listAgencies(): Result<List<Agency>> = withContext(Dispatchers.IO) {
        try { Result.success(api.listAgencies()) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun listBranches(): Result<List<Branch>> = withContext(Dispatchers.IO) {
        try { Result.success(api.listBranches()) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun listNotifications(): Result<List<ApiNotification>> = withContext(Dispatchers.IO) {
        try { Result.success(api.listNotifications()) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }

    suspend fun markNotificationsRead(ids: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            api.markNotificationsRead(MarkReadRequest(ids))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(e.friendlyMessage()))
        }
    }

    suspend fun getMySales(): Result<List<SaleItem>> = withContext(Dispatchers.IO) {
        try { Result.success(api.getMySales()) } catch (e: Exception) { Result.failure(Exception(e.friendlyMessage())) }
    }
}
