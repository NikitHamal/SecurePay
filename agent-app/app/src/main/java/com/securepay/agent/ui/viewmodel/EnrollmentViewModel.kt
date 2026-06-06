package com.securepay.agent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securepay.agent.data.model.EnrollmentData
import com.securepay.agent.data.model.FinancePlan
import com.securepay.agent.data.model.WizardStep
import com.securepay.agent.data.repository.EnrollmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Immutable UI state for the enrollment wizard. Every update produces a new copy
 * so Compose can diff cleanly.
 */
data class EnrollmentUiState(
    val currentStep: WizardStep = WizardStep.KYC,
    val draft: EnrollmentData = EnrollmentData(),
    val selectedPlan: FinancePlan = FinancePlan.DEFAULT,
    val totalAmountInput: String = "",
    val downPaymentInput: String = "",
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val enrollmentId: String? = null,
    val submitMessage: String? = null
) {
    val isKycValid: Boolean
        get() = draft.customerName.trim().length >= 2 &&
            draft.nationalId.trim().length >= 6 &&
            draft.phoneNumber.filter { it.isDigit() }.length >= 9

    val isScannerValid: Boolean
        get() = draft.imei.length == 15 &&
            draft.imei.all { it.isDigit() } &&
            draft.deviceModel.isNotBlank()

    val isPaymentValid: Boolean
        get() = draft.totalLoanAmount > 0.0 &&
            draft.downPayment >= 0.0 &&
            draft.downPayment < draft.totalLoanAmount

    /** Whether the Next button should be enabled for the current step. */
    val canAdvance: Boolean
        get() = when (currentStep) {
            WizardStep.KYC -> isKycValid
            WizardStep.SCANNER -> isScannerValid
            WizardStep.PAYMENT -> isPaymentValid
        }

    val isFirstStep: Boolean get() = currentStep == WizardStep.KYC
    val isLastStep: Boolean get() = currentStep == WizardStep.last
}

class EnrollmentViewModel(
    private val repository: EnrollmentRepository = EnrollmentRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnrollmentUiState())
    val uiState: StateFlow<EnrollmentUiState> = _uiState.asStateFlow()

    fun updateKyc(
        customerName: String = _uiState.value.draft.customerName,
        nationalId: String = _uiState.value.draft.nationalId,
        phoneNumber: String = _uiState.value.draft.phoneNumber
    ) {
        _uiState.update { state ->
            state.copy(
                draft = state.draft.copy(
                    customerName = customerName,
                    nationalId = nationalId,
                    phoneNumber = phoneNumber
                )
            )
        }
    }

    fun onImeiScanned(imei: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(imei = imei))
        }
    }

    fun updateDeviceModel(model: String) {
        _uiState.update { state ->
            state.copy(draft = state.draft.copy(deviceModel = model))
        }
    }

    fun updateAmounts(totalInput: String, downInput: String) {
        val total = totalInput.toDoubleOrNull() ?: 0.0
        val down = downInput.toDoubleOrNull() ?: 0.0
        _uiState.update { state ->
            state.copy(
                totalAmountInput = totalInput,
                downPaymentInput = downInput,
                draft = state.draft.copy(
                    totalLoanAmount = total,
                    downPayment = down
                )
            )
        }
    }

    fun selectPlan(plan: FinancePlan) {
        _uiState.update { state ->
            state.copy(
                selectedPlan = plan,
                draft = state.draft.copy(planMonths = plan.months)
            )
        }
    }

    fun next() {
        val state = _uiState.value
        if (!state.canAdvance || state.isLastStep) return
        val nextStep = WizardStep.fromIndex(state.currentStep.index + 1)
        _uiState.update { it.copy(currentStep = nextStep) }
    }

    fun back() {
        val state = _uiState.value
        if (state.isFirstStep) return
        val prevStep = WizardStep.fromIndex(state.currentStep.index - 1)
        _uiState.update { it.copy(currentStep = prevStep) }
    }

    fun submit() {
        val state = _uiState.value
        if (!state.isPaymentValid || state.isSubmitting || state.isSubmitted) return

        _uiState.update { it.copy(isSubmitting = true, submitMessage = null) }
        viewModelScope.launch {
            val result = repository.submitEnrollment(state.draft)
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    isSubmitted = result.success,
                    enrollmentId = result.enrollmentId,
                    submitMessage = result.message
                )
            }
        }
    }

    /** Reset the wizard to begin a fresh enrollment. */
    fun reset() {
        _uiState.value = EnrollmentUiState()
    }
}
