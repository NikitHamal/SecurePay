package com.securepay.customer.data.repository

import com.securepay.customer.data.model.LoanAccount
import com.securepay.customer.data.remote.MockSecurePayApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the financed account. The repository exposes the
 * account as a [StateFlow] so the UI layer observes balance/deadline changes
 * reactively, and forwards mutating intents to the backend.
 */
interface DeviceRepository {
    val account: StateFlow<LoanAccount?>
    suspend fun refresh()
    suspend fun simulatePayment()
    suspend fun requestGraceWindow()
}

class MockDeviceRepository(
    private val api: MockSecurePayApi = MockSecurePayApi()
) : DeviceRepository {

    private val _account = MutableStateFlow<LoanAccount?>(null)
    override val account: StateFlow<LoanAccount?> = _account.asStateFlow()

    override suspend fun refresh() {
        _account.value = api.fetchAccount()
    }

    override suspend fun simulatePayment() {
        _account.value = api.simulatePayment()
    }

    override suspend fun requestGraceWindow() {
        _account.value = api.requestGraceWindow()
    }
}
