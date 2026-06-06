package com.securepay.customer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securepay.customer.data.MockLeaseRepository
import com.securepay.customer.domain.DeviceLeaseUiState
import com.securepay.customer.domain.PolicyActionResult
import com.securepay.customer.domain.evaluate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CustomerViewModel(
    private val repository: MockLeaseRepository
) : ViewModel() {
    private val ticker = MutableStateFlow(System.currentTimeMillis())
    private val policyResult = MutableStateFlow<PolicyActionResult?>(null)

    val uiState = combine(repository.lease, ticker, policyResult) { lease, now, policy ->
        lease.evaluate(now).copy(lastPolicyResult = policy)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.lease.value.evaluate(System.currentTimeMillis())
    )

    init {
        viewModelScope.launchTicker()
    }

    fun simulatePayment() {
        repository.simulatePayment()
    }

    fun requestGraceWindow() {
        repository.requestGraceWindow()
        policyResult.update { null }
    }

    fun forceOverdueForTesting() {
        repository.forceOverdueForTesting()
    }

    fun recordPolicyResult(result: PolicyActionResult) {
        policyResult.update { result }
    }

    private fun kotlinx.coroutines.CoroutineScope.launchTicker() {
        launch {
            while (true) {
                ticker.value = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }
}
