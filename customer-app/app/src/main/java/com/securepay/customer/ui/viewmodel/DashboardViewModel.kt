package com.securepay.customer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securepay.customer.data.model.DeviceStatus
import com.securepay.customer.data.model.LoanState
import com.securepay.customer.data.repository.DeviceRepository
import com.securepay.customer.domain.CountdownEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Immutable UI state for the dashboard. Recomputed every tick so the countdown
 * text and progress bar update once per second.
 */
data class DashboardUiState(
    val loan: LoanState? = null,
    val status: DeviceStatus = DeviceStatus.ACTIVE,
    val remainingFormatted: String = "--:--:--",
    val progress: Float = 1f,
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false
)

/**
 * Drives the customer dashboard. Maintains a local "now" clock seeded from the
 * server epoch and advanced by a 1s ticker; re-evaluates [DeviceStatus] via the
 * pure [CountdownEngine] on every tick.
 */
class DashboardViewModel(
    private val repository: DeviceRepository = DeviceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /** Local monotonic-ish clock; seeded from server epoch, advanced each tick. */
    private var localNowMillis: Long = System.currentTimeMillis()
    private var tickerJob: Job? = null

    init {
        loadAndStart()
    }

    private fun loadAndStart() {
        viewModelScope.launch {
            val loan = repository.getLoanState()
            localNowMillis = loan.serverEpochMillis
            _uiState.update { it.copy(loan = loan, isLoading = false) }
            recompute()
            startTicker()
        }
    }

    /** Launch the 1s ticker that advances the clock and recomputes state. */
    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                localNowMillis += 1000L
                recompute()
            }
        }
    }

    /** Recompute derived UI fields from the current loan and local clock. */
    private fun recompute() {
        val loan = _uiState.value.loan ?: return
        val remainingMillis = loan.nextDueEpochMillis - localNowMillis
        val status = CountdownEngine.evaluateStatus(
            nowMillis = localNowMillis,
            nextDueMillis = loan.nextDueEpochMillis
        )
        val formatted = CountdownEngine.formatRemaining(remainingMillis)
        val progress = CountdownEngine.progressFraction(
            nowMillis = localNowMillis,
            nextDueMillis = loan.nextDueEpochMillis
        )
        _uiState.update {
            it.copy(
                status = status,
                remainingFormatted = formatted,
                progress = progress
            )
        }
    }

    /** Simulate a payment-gateway integration; refreshes loan and clock. */
    fun onSimulatePayment() {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val updated = repository.simulatePayment()
            localNowMillis = updated.serverEpochMillis
            _uiState.update { it.copy(loan = updated, isProcessing = false) }
            recompute()
        }
    }

    /** Request a 5-minute grace window (extends the deadline). */
    fun onRequestGrace() {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val updated = repository.requestGrace()
            localNowMillis = updated.serverEpochMillis
            _uiState.update { it.copy(loan = updated, isProcessing = false) }
            recompute()
        }
    }

    /**
     * Placeholder hook for the emergency-call action. The actual ACTION_DIAL
     * intent is fired from the UI layer (which holds the Context); this exists
     * so analytics / audit logging can be wired in centrally later.
     */
    fun onEmergencyCall() {
        // Intentionally a no-op placeholder. The lock overlay launches the
        // dialer directly. Audit logging would be added here.
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
    }
}
