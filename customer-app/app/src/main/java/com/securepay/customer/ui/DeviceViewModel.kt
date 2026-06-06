package com.securepay.customer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securepay.customer.admin.SecurityChecker
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

class DeviceViewModel(
    private val repository: DeviceRepository
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
    private val securityReport = MutableStateFlow<SecurityChecker.SecurityReport?>(null)

    val securityWarnings: StateFlow<SecurityChecker.SecurityReport?> = securityReport.asStateFlow()

    private val derived: StateFlow<DeviceUiState> = combine(
        repository.account,
        ticker,
        processingPayment,
        requestingGrace,
        transientMessage
    ) { account, now, paying, grace, message ->
        if (account == null) {
            val cachedDue = repository.cachedNextPaymentDue
            if (cachedDue > 0L && repository.isRegistered.value) {
                val status = DeviceStatus.evaluate(cachedDue, repository.cachedLockedByDealer, now)
                DeviceUiState(
                    isLoading = false,
                    status = status,
                    remaining = RemainingTime.until(cachedDue, now),
                    isProcessingPayment = paying,
                    isRequestingGrace = grace,
                    message = message,
                    isOffline = true
                )
            } else {
                DeviceUiState(isLoading = true)
            }
        } else {
            val status = DeviceStatus.evaluate(
                account.nextPaymentDueEpochMillis,
                account.lockedByDealer,
                now
            )
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
        startHeartbeat()
    }

    fun runSecurityCheck(context: android.content.Context) {
        viewModelScope.launch {
            val report = SecurityChecker.runAllChecks(context)
            securityReport.value = report
        }
    }

    private fun startHeartbeat() {
        viewModelScope.launch {
            while (true) {
                delay(4 * 60 * 60 * 1000L)
                if (repository.isRegistered.value) {
                    repository.heartbeat()
                }
            }
        }
    }

    fun simulatePayment() {
        if (processingPayment.value) return
        viewModelScope.launch {
            processingPayment.value = true
            transientMessage.value = null
            runCatching { repository.heartbeat() }
                .onSuccess { transientMessage.value = "Updated. Device status refreshed." }
                .onFailure { transientMessage.value = "Update failed. Please retry." }
            processingPayment.value = false
        }
    }

    fun requestGraceWindow() {
        if (requestingGrace.value) return
        viewModelScope.launch {
            requestingGrace.value = true
            runCatching { repository.heartbeat() }
                .onSuccess { transientMessage.value = "5-minute grace window granted." }
                .onFailure { transientMessage.value = "Grace request unavailable." }
            requestingGrace.value = false
        }
    }

    fun consumeMessage() {
        transientMessage.value = null
    }

    class Factory(
        private val repository: DeviceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DeviceViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return DeviceViewModel(repository) as T
        }
    }
}