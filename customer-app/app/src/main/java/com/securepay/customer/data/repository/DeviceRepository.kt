package com.securepay.customer.data.repository

import android.util.Log
import com.securepay.customer.data.model.AccountResponse
import com.securepay.customer.data.model.DeviceCheckResponse
import com.securepay.customer.data.model.LoanAccount
import com.securepay.customer.data.remote.DeviceTokenManager
import com.securepay.customer.data.remote.SecurePayApi
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

    suspend fun checkAndRegister(imei: String): Result<DeviceCheckResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.deviceCheck(imei)
            if (response.enrolled && response.account != null) {
                tokenManager.saveDevice(response.account.id, imei)
                _isRegistered.value = true
                refresh()
            }
            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "deviceCheck failed", e)
            Result.failure(e)
        }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val accountId = tokenManager.accountId ?: return@withContext
        try {
            val response = api.getAccount(accountId)
            _account.value = response.toLoanAccount()
        } catch (e: Exception) {
            Log.e(TAG, "refresh failed", e)
        }
    }

    suspend fun heartbeat(): Result<LoanAccount?> = withContext(Dispatchers.IO) {
        val accountId = tokenManager.accountId ?: return@withContext Result.failure(IllegalStateException("Not registered"))
        val imei = tokenManager.imei ?: return@withContext Result.failure(IllegalStateException("No IMEI"))
        try {
            val response = api.deviceHeartbeat(mapOf("imei" to imei, "accountId" to accountId))
            if (response.enrolled && response.account != null) {
                refresh()
            }
            Result.success(_account.value)
        } catch (e: Exception) {
            Log.e(TAG, "heartbeat failed", e)
            Result.failure(e)
        }
    }

    fun clearRegistration() {
        tokenManager.clear()
        _account.value = null
        _isRegistered.value = false
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