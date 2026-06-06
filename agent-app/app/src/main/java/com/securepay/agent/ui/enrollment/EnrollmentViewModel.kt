package com.securepay.agent.ui.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securepay.agent.data.model.CustomerEnrollment
import com.securepay.agent.data.model.Plan
import com.securepay.agent.data.model.PlanCatalog
import com.securepay.agent.data.repository.EnrollmentRepository
import com.securepay.agent.data.repository.MockEnrollmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EnrollmentViewModel(
    private val repository: EnrollmentRepository = MockEnrollmentRepository(),
    private val plans: List<Plan> = PlanCatalog.plans
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EnrollmentUiState(availablePlans = plans)
    )
    val uiState: StateFlow<EnrollmentUiState> = _uiState.asStateFlow()

    // ---- KYC (step 1) ---------------------------------------------------------

    fun updateKycName(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(customerName = value))
    }

    fun updateKycNationalId(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(nationalId = value))
    }

    fun updateKycPhone(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(phoneNumber = value))
    }

    // ---- Device (step 2) ------------------------------------------------------

    fun updateImei(value: String) = _uiState.update {
        // Keep only digits and cap at the 15-digit IMEI length.
        val sanitized = value.filter { ch -> ch.isDigit() }.take(IMEI_LENGTH)
        it.copy(draft = it.draft.copy(imei = sanitized))
    }

    fun updateDeviceModel(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(deviceModel = value))
    }

    /** Fills a deterministic mock 15-digit IMEI, as if scanned from a barcode. */
    fun simulateScan() = _uiState.update {
        it.copy(draft = it.draft.copy(imei = MOCK_IMEI))
    }

    // ---- Plan (step 3) --------------------------------------------------------

    fun selectPlan(plan: Plan) = _uiState.update { state ->
        state.copy(
            selectedPlan = plan,
            // Pre-fill the suggested down payment when none has been typed yet.
            downPaymentInput = state.downPaymentInput.ifBlank {
                plan.suggestedDownPayment.toBigDecimal().stripTrailingZeros().toPlainString()
            },
            draft = state.draft.copy(
                planName = plan.name,
                totalLoanAmount = plan.totalLoanAmount,
                dailyRate = plan.dailyRate,
                termDays = plan.termDays
            )
        )
    }

    fun updateDownPayment(value: String) = _uiState.update { state ->
        val sanitized = value.filter { it.isDigit() || it == '.' }
        val parsed = sanitized.toDoubleOrNull() ?: 0.0
        state.copy(
            downPaymentInput = sanitized,
            draft = state.draft.copy(downPayment = parsed)
        )
    }

    // ---- Navigation -----------------------------------------------------------

    fun nextStep() = _uiState.update { state ->
        if (state.isCurrentStepValid && !state.isLastStep) {
            state.copy(stepIndex = state.stepIndex + 1)
        } else {
            state
        }
    }

    fun prevStep() = _uiState.update { state ->
        if (!state.isFirstStep) state.copy(stepIndex = state.stepIndex - 1) else state
    }

    // ---- Submission -----------------------------------------------------------

    fun submit() {
        val state = _uiState.value
        if (!state.isKycStepValid || !state.isDeviceStepValid || !state.isPlanStepValid) {
            return
        }
        if (state.submission is SubmissionState.Submitting) return

        _uiState.update { it.copy(submission = SubmissionState.Submitting) }

        viewModelScope.launch {
            val enrollment: CustomerEnrollment = state.draft
            val result = repository.submitEnrollment(enrollment)
            _uiState.update { current ->
                current.copy(
                    submission = result.fold(
                        onSuccess = { id ->
                            SubmissionState.Success(id).also {
                                // Stamp the server id onto the immutable record.
                            }
                        },
                        onFailure = { error ->
                            SubmissionState.Error(error.message ?: "Submission failed")
                        }
                    ),
                    draft = result.fold(
                        onSuccess = { id -> current.draft.copy(id = id) },
                        onFailure = { current.draft }
                    )
                )
            }
        }
    }

    /** Clears a transient error so the agent can retry. */
    fun clearSubmissionError() = _uiState.update {
        if (it.submission is SubmissionState.Error) it.copy(submission = SubmissionState.Idle) else it
    }

    companion object {
        private const val IMEI_LENGTH = 15
        private const val MOCK_IMEI = "359881234567890"

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EnrollmentViewModel() as T
            }
        }
    }
}
