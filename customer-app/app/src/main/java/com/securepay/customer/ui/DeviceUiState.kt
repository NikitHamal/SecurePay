package com.securepay.customer.ui

import com.securepay.customer.data.model.DeviceStatus
import com.securepay.customer.data.model.LoanAccount
import com.securepay.customer.domain.RemainingTime

/**
 * Fully-derived, immutable snapshot the UI renders. Recomputed on every clock
 * tick so the countdown, status and progress stay in lockstep.
 */
data class DeviceUiState(
    val isLoading: Boolean = true,
    val account: LoanAccount? = null,
    val status: DeviceStatus = DeviceStatus.ACTIVE,
    val remaining: RemainingTime = RemainingTime(0L),
    val isProcessingPayment: Boolean = false,
    val isRequestingGrace: Boolean = false,
    val message: String? = null
) {
    val isLocked: Boolean get() = status == DeviceStatus.LOCKED
}
