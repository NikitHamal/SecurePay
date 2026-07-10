package com.touchbase.user.data.repository

import com.touchbase.user.util.SecureLog
import com.touchbase.user.data.model.AccountResponse
import com.touchbase.user.data.model.ActivateResponse
import com.touchbase.user.data.model.DeviceCheckResponse
import com.touchbase.user.data.model.LoanAccount
import com.touchbase.user.data.model.PaymentEntry
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.remote.SecurePayApi
import com.touchbase.user.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DeviceRepository(
    private var api: SecurePayApi,
    private val tokenManager: DeviceTokenManager
) {

    private val _account = MutableStateFlow<LoanAccount?>(null)
    val account: StateFlow<LoanAccount?> = _account.asStateFlow()

    private val _isRegistered = MutableStateFlow(tokenManager.isRegistered)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    private val _payments = MutableStateFlow<List<PaymentEntry>>(emptyList())
    val payments: StateFlow<List<PaymentEntry>> = _payments.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val cachedNextPaymentDue: Long get() = tokenManager.cachedNextPaymentDue
    val cachedLockedByDealer: Boolean get() = tokenManager.cachedLockedByDealer
    val cachedReleaseApproved: Boolean get() = tokenManager.cachedReleaseApproved

    val trustedTime: Long
        get() = tokenManager.getTrustedTimeMillis()

    suspend fun checkAndRegister(imei: String): Result<DeviceCheckResponse> = withContext(Dispatchers.IO) {
        try {
            val requestTime = System.currentTimeMillis()
            val response = api.deviceCheck(imei, tokenManager.accountId)
            updateServerTimeOffset(requestTime, response.serverTime)
            tokenManager.saveSecurityPolicy(response.securityPolicy)
            if (response.enrolled && response.account != null) {
                val recoveredSecret = response.apiSecret.ifBlank { tokenManager.apiSecret.orEmpty() }.ifBlank { null }
                tokenManager.saveDevice(response.account.id, imei, recoveredSecret)
                tokenManager.saveSecurityPolicy(response.securityPolicy)
                if (!recoveredSecret.isNullOrBlank()) {
                    api = com.touchbase.user.data.remote.ApiModule.provideApi(recoveredSecret, response.account.id)
                }
                tokenManager.saveCachedStatus(
                    nextPaymentDue = response.account.nextPaymentDue,
                    lockedByDealer = response.account.status.equals("LOCKED", ignoreCase = true) || response.account.status.equals("STOLEN", ignoreCase = true),
                    releaseApproved = response.account.releaseApproved,
                    isStolen = response.account.isStolen
                )
                _isRegistered.value = true
                if (!recoveredSecret.isNullOrBlank()) {
                    refresh()
                }
            }
            Result.success(response)
        } catch (e: Exception) {
            SecureLog.e(TAG, "deviceCheck failed", e)
            Result.failure(e)
        }
    }

    suspend fun activate(
        activationCode: String,
        provisioningToken: String,
        expectedImei: String
    ): Result<ActivateResponse> = withContext(Dispatchers.IO) {
        try {
            val requestTime = System.currentTimeMillis()
            val response = api.activate(
                mapOf(
                    "activationCode" to activationCode,
                    "provisioningToken" to provisioningToken,
                    "imei" to expectedImei
                )
            )
            updateServerTimeOffset(requestTime, response.serverTime)
            if (response.activated && response.account != null) {
                val imei = response.imei.ifBlank { response.device?.imei.orEmpty() }
                tokenManager.saveDevice(response.account.id, imei, response.apiSecret.ifBlank { null })
                tokenManager.saveSecurityPolicy(response.securityPolicy)
                if (response.apiSecret.isNotBlank()) {
                    api = com.touchbase.user.data.remote.ApiModule.provideApi(response.apiSecret, response.account.id)
                }
                _isRegistered.value = true
                refresh()
            }
            Result.success(response)
        } catch (e: Exception) {
            SecureLog.e(TAG, "activate failed", e)
            Result.failure(e)
        }
    }


    suspend fun reportProvisioned(token: String, imei: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = linkedMapOf("token" to token)
            if (!imei.isNullOrBlank()) body["imei"] = imei
            val response = api.reportProvisioned(body)
            if (!response.isSuccessful) {
                throw IllegalStateException("Provisioning report failed with HTTP ${response.code()}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            SecureLog.e(TAG, "reportProvisioned failed", e)
            Result.failure(e)
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val accountId = tokenManager.accountId ?: return@withContext
        val imei = tokenManager.imei ?: return@withContext
        _isLoading.value = true
        try {
            ensurePerDeviceApi(accountId, imei)
            val requestTime = System.currentTimeMillis()
            val response = api.getAccount(accountId, imei)
            updateServerTimeOffset(requestTime, response.serverTime)
            val account = response.toLoanAccount()
            _account.value = account
            tokenManager.saveSecurityPolicy(response.securityPolicy)
            tokenManager.saveCachedStatus(
                account.nextPaymentDueEpochMillis,
                account.lockedByDealer,
                account.releaseApproved,
                account.isStolen
            )
            _error.value = null
        } catch (e: Exception) {
            SecureLog.e(TAG, "refresh failed", e)
            _error.value = e.message ?: "Failed to refresh account"
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun refreshPayments() = withContext(Dispatchers.IO) {
        val accountId = tokenManager.accountId ?: return@withContext
        val imei = tokenManager.imei ?: return@withContext
        try {
            ensurePerDeviceApi(accountId, imei)
            val requestTime = System.currentTimeMillis()
            val response = api.getPayments(accountId, imei)
            _payments.value = response.payments
            updateServerTimeOffset(requestTime, response.serverTime)
        } catch (e: Exception) {
            SecureLog.e(TAG, "refreshPayments failed", e)
        }
    }

    suspend fun heartbeat(): Result<LoanAccount?> = withContext(Dispatchers.IO) {
        val accountId = tokenManager.accountId ?: return@withContext Result.failure(IllegalStateException("Not registered"))
        val imei = tokenManager.imei ?: return@withContext Result.failure(IllegalStateException("No IMEI"))
        try {
            ensurePerDeviceApi(accountId, imei)
            val requestTime = System.currentTimeMillis()
            val response = api.deviceHeartbeat(mapOf("imei" to imei, "accountId" to accountId))
            updateServerTimeOffset(requestTime, response.serverTime)
            tokenManager.saveSecurityPolicy(response.securityPolicy)
            if (response.enrolled && response.account != null) {
                refresh()
            }
            Result.success(_account.value)
        } catch (e: Exception) {
            SecureLog.e(TAG, "heartbeat failed", e)
            Result.failure(e)
        }
    }


    suspend fun reportReleaseComplete(): Result<Unit> = withContext(Dispatchers.IO) {
        val accountId = tokenManager.accountId ?: return@withContext Result.failure(IllegalStateException("Not registered"))
        val imei = tokenManager.imei ?: return@withContext Result.failure(IllegalStateException("No IMEI"))
        try {
            ensurePerDeviceApi(accountId, imei)
            val requestTime = System.currentTimeMillis()
            val response = api.releaseComplete(mapOf("accountId" to accountId, "imei" to imei))
            updateServerTimeOffset(requestTime, response.serverTime)
            Result.success(Unit)
        } catch (e: Exception) {
            SecureLog.e(TAG, "releaseComplete failed", e)
            Result.failure(e)
        }
    }

    suspend fun checkForAppUpdate(): Result<com.touchbase.user.data.model.AppUpdateResponse> = withContext(Dispatchers.IO) {
        try {
            val accountId = tokenManager.accountId.orEmpty()
            val imei = tokenManager.imei.orEmpty()
            ensurePerDeviceApi(accountId, imei)
            val requestTime = System.currentTimeMillis()
            val response = api.appUpdate(
                BuildConfig.VERSION_CODE,
                accountId,
                imei
            )
            updateServerTimeOffset(requestTime, response.serverTime)
            Result.success(response)
        } catch (e: Exception) {
            SecureLog.e(TAG, "appUpdate check failed", e)
            Result.failure(e)
        }
    }

    private suspend fun ensurePerDeviceApi(accountId: String, imei: String): Boolean {
        val existingSecret = tokenManager.apiSecret
        if (!existingSecret.isNullOrBlank()) {
            api = com.touchbase.user.data.remote.ApiModule.provideApi(existingSecret, accountId)
            return true
        }
        SecureLog.w(TAG, "Per-device credential is missing for $imei; use activation recovery or reprovision the phone")
        return false
    }

    private fun updateServerTimeOffset(requestSentAt: Long, serverTime: Long) {
        if (serverTime <= 0L) return
        val receivedAt = System.currentTimeMillis()
        val halfRoundTrip = ((receivedAt - requestSentAt).coerceAtLeast(0L)) / 2L
        val estimatedServerAtReceipt = serverTime + halfRoundTrip
        tokenManager.saveServerTimeOffset(estimatedServerAtReceipt - receivedAt)
    }

    fun clearRegistration() {
        tokenManager.clear()
        _account.value = null
        _isRegistered.value = false
        _payments.value = emptyList()
    }

    companion object {
        private const val TAG = "DeviceRepository"
    }
}

fun AccountResponse.toLoanAccount(): LoanAccount = LoanAccount(
    id = id,
    customerName = customerName,
    imei = imei,
    deviceModel = deviceModel,
    planName = planName,
    totalLoanAmountCents = totalLoanAmount,
    amountPaidCents = amountPaid,
    dailyRateCents = dailyRate,
    termDays = termDays,
    nextPaymentDueEpochMillis = nextPaymentDueEpochMillis,
    lockedByDealer = lockedByDealer == 1 || isStolen,
    currencyCode = currencyCode.ifEmpty { "GHS" },
    releaseApproved = releaseApproved,
    isStolen = isStolen,
    releaseApprovedAt = releaseApprovedAt,
    releasedAt = releasedAt,
    securityPolicy = securityPolicy
)
