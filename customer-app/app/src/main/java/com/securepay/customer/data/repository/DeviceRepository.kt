package com.securepay.customer.data.repository

import com.securepay.customer.data.model.LoanState
import com.securepay.customer.data.remote.MockApi

/**
 * Repository abstraction over the financing backend. The ViewModel depends on
 * this rather than [MockApi] directly so the data source can later be swapped
 * for a real network client without touching presentation logic.
 */
class DeviceRepository(
    private val api: MockApi = MockApi()
) {
    /** Load the current loan state. */
    suspend fun getLoanState(): LoanState = api.fetchLoanState()

    /** Push a simulated installment payment and return the updated state. */
    suspend fun simulatePayment(): LoanState = api.simulatePayment()

    /** Request a short grace extension and return the updated state. */
    suspend fun requestGrace(): LoanState = api.requestGrace()
}
