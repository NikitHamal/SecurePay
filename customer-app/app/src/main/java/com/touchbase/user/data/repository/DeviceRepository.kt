package com.touchbase.user.data.repository

import com.touchbase.user.util.SecureLog
import com.touchbase.user.data.model.AccountResponse
import com.touchbase.user.data.model.DeviceCheckResponse
import com.touchbase.user.data.model.LoanAccount
import com.touchbase.user.data.model.PaymentEntry
import com.touchbase.user.data.remote.DeviceTokenManager
import com.touchbase.user.data.remote.SecurePayApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class DeviceRepository(
    private val api: SecurePayApi,
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

    val trustedTime: Long
        get() = tokenManager.getTrustedTimeMillis()

    suspend fun checkAndRegister(imei: String): Result<DeviceCheckResponse> = withContext(Dispatchers.IO) {
        try {
            val requestTime = System.currentTimeMillis()
            val response = api.deviceCheck(imei)
            updateServerTimeOffset(requestTime)
            if (response.enrolled && response.account != null) {
                tokenManager.saveDevice(response.account.id, imei)
                _isRegistered.value = true
                refresh()
            }
            Result.success(response)
        } catch (e: Exception) {
            SecureLog.e(TAG, "deviceCheck failed", e)
            Result.failure(e)
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val accountId = tokenManager.accountId ?: return@withContext
        _isLoading.value = true
        try {
            val requestTime = System.currentTimeMillis()
            val response = api.getAccount(accountId)
            updateServerTimeOffset(requestTime)
            val account = response.toLoanAccount()
            _account.value = account
            tokenManager.saveCachedStatus(account.nextPaymentDueEpochMillis, account.lockedByDealer)
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
        try {
            val response = api.getPayments(accountId)
            _payments.value = response.payments
        } catch (e: Exception) {
            SecureLog.e(TAG, "refreshPayments failed", e)
        }
    }

    suspend fun heartbeat(): Result<LoanAccount?> = withContext(Dispatchers.IO) {
        val accountId = tokenManager.accountId ?: return@withContext Result.failure(IllegalStateException("Not registered"))
        val imei = tokenManager.imei ?: return@withContext Result.failure(IllegalStateException("No IMEI"))
        try {
            val requestTime = System.currentTimeMillis()
            val response = api.deviceHeartbeat(mapOf("imei" to imei, "accountId" to accountId))
            updateServerTimeOffset(requestTime)
            if (response.enrolled && response.account != null) {
                refresh()
            }
            Result.success(_account.value)
        } catch (e: Exception) {
            SecureLog.e(TAG, "heartbeat failed", e)
            Result.failure(e)
        }
    }

    private fun updateServerTimeOffset(requestSentAt: Long) {
        val localNow = System.currentTimeMillis()
        val estimatedOneWayLatency = (localNow - requestSentAt) / 2
        val offset = -estimatedOneWayLatency
        tokenManager.saveServerTimeOffset(offset)
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
    lockedByDealer = lockedByDealer == 1,
    currencyCode = currencyCode.ifEmpty { "KES" }
)
