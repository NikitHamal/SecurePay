package com.touchbase.user.ui

import com.touchbase.user.data.model.DeviceStatus
import com.touchbase.user.data.model.LoanAccount
import com.touchbase.user.domain.RemainingTime

data class DeviceUiState(
    val isLoading: Boolean = true,
    val account: LoanAccount? = null,
    val status: DeviceStatus = DeviceStatus.ACTIVE,
    val remaining: RemainingTime = RemainingTime(0L),
    val isProcessingPayment: Boolean = false,
    val isRequestingGrace: Boolean = false,
    val message: String? = null,
    val isOffline: Boolean = false
) {
    val isLocked: Boolean get() = status == DeviceStatus.LOCKED
}
