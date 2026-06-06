package com.securepay.customer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securepay.customer.admin.DevicePolicyController
import com.securepay.customer.data.model.DeviceStatus
import com.securepay.customer.data.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.securepay.customer.domain.RemainingTime

/**
 * Owns the reactive financing state machine for the customer DPC.
 *
 * A 1-second ticker is combined with the account [StateFlow] from the
 * repository; every emission re-derives status + countdown and, as a side
 * effect, asks the [DevicePolicyController] to enforce or release device
 * restrictions when crossing the LOCKED boundary.
 */
class DeviceViewModel(
    private val repository: DeviceRepository,
    private val policyController: DevicePolicyController
) : ViewModel() {

    private val ticker: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = System.currentTimeMillis()
    )

    private val processingPayment = MutableStateFlow(false)
    private val requestingGrace = MutableStateFlow(false)
    private val transientMessage = MutableStateFlow<String?>(null)

    private var lastEnforcedLocked: Boolean? = null

    private val derived: StateFlow<DeviceUiState> = combine(
        repository.account,
        ticker,
        processingPayment,
        requestingGrace,
        transientMessage
    ) { account, now, paying, grace, message ->
        if (account == null) {
            DeviceUiState(isLoading = true)
        } else {
            val status = DeviceStatus.evaluate(account.nextPaymentDueEpochMillis, now)
            reconcilePolicy(status)
            DeviceUiState(
                isLoading = false,
                account = account,
                status = status,
                remaining = RemainingTime.until(account.nextPaymentDueEpochMillis, now),
                isProcessingPayment = paying,
                isRequestingGrace = grace,
                message = message
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DeviceUiState(isLoading = true)
    )

    val uiState: StateFlow<DeviceUiState> = derived

    init {
        viewModelScope.launch { repository.refresh() }
    }

    /** Apply DPC restrictions exactly once per LOCKED <-> unlocked transition. */
    private fun reconcilePolicy(status: DeviceStatus) {
        val nowLocked = status == DeviceStatus.LOCKED
        if (lastEnforcedLocked == nowLocked) return
        lastEnforcedLocked = nowLocked
        if (nowLocked) policyController.enforceLock() else policyController.releaseRestrictions()
    }

    fun simulatePayment() {
        if (processingPayment.value) return
        viewModelScope.launch {
            processingPayment.value = true
            transientMessage.value = null
            runCatching { repository.simulatePayment() }
                .onSuccess { transientMessage.value = "Payment received. Device unlocked." }
                .onFailure { transientMessage.value = "Payment failed. Please retry." }
            processingPayment.value = false
        }
    }

    fun requestGraceWindow() {
        if (requestingGrace.value) return
        viewModelScope.launch {
            requestingGrace.value = true
            runCatching { repository.requestGraceWindow() }
                .onSuccess { transientMessage.value = "5-minute grace window granted." }
                .onFailure { transientMessage.value = "Grace request unavailable." }
            requestingGrace.value = false
        }
    }

    fun consumeMessage() {
        transientMessage.value = null
    }

    class Factory(
        private val repository: DeviceRepository,
        private val policyController: DevicePolicyController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DeviceViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return DeviceViewModel(repository, policyController) as T
        }
    }
}
