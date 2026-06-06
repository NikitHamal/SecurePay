package com.securepay.customer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securepay.customer.data.MockFinancingRepository
import com.securepay.customer.domain.DeviceLease
import com.securepay.customer.domain.DeviceStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CustomerDashboardViewModel(
    private val repository: MockFinancingRepository
) : ViewModel() {
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val uiState = combine(repository.observeLease(), ticker) { lease, now ->
        lease.toUiState(now)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = repository.observeLease().value.toUiState(System.currentTimeMillis())
    )

    fun simulatePayment() {
        viewModelScope.launch {
            repository.simulatePayment()
        }
    }

    fun requestGraceWindow() {
        viewModelScope.launch {
            repository.requestGraceWindow()
        }
    }
}

data class CustomerDashboardUiState(
    val contractId: String,
    val customerName: String,
    val imei: String,
    val status: DeviceStatus,
    val remainingMillis: Long,
    val progress: Float,
    val outstandingBalanceCents: Long,
    val financedAmountCents: Long
) {
    val isOverdue: Boolean = remainingMillis <= 0
}

class CustomerDashboardViewModelFactory(
    private val repository: MockFinancingRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CustomerDashboardViewModel(repository) as T
    }
}

private fun DeviceLease.toUiState(now: Long): CustomerDashboardUiState {
    val remainingMillis = dueEpochMillis - now
    val totalWindow = (dueEpochMillis - activatedEpochMillis).coerceAtLeast(1)
    val elapsed = (now - activatedEpochMillis).coerceAtLeast(0)
    val progress = (1f - (elapsed.toFloat() / totalWindow.toFloat())).coerceIn(0f, 1f)
    val status = when {
        remainingMillis <= 0 -> DeviceStatus.LOCKED
        remainingMillis <= WARNING_THRESHOLD_MILLIS -> DeviceStatus.WARNING
        else -> DeviceStatus.ACTIVE
    }

    return CustomerDashboardUiState(
        contractId = contractId,
        customerName = customerName,
        imei = imei,
        status = status,
        remainingMillis = remainingMillis.coerceAtLeast(0),
        progress = progress,
        outstandingBalanceCents = outstandingBalanceCents,
        financedAmountCents = financedAmountCents
    )
}

private const val WARNING_THRESHOLD_MILLIS = 24L * 60L * 60L * 1_000L

