package com.securepay.agent.ui.enrollment

import com.securepay.agent.data.model.CustomerEnrollment
import com.securepay.agent.data.model.Plan

/** Wizard steps, in order. */
enum class EnrollmentStep {
    KYC,
    DEVICE,
    PLAN;

    companion object {
        val ordered: List<EnrollmentStep> = entries
        const val COUNT: Int = 3
    }
}

/** Outcome of submitting the assembled enrollment. */
sealed interface SubmissionState {
    data object Idle : SubmissionState
    data object Submitting : SubmissionState
    data class Success(val enrollmentId: String) : SubmissionState
    data class Error(val message: String) : SubmissionState
}

/**
 * Single source of truth for the enrollment wizard. Immutable; the view model
 * emits a fresh copy on every change.
 */
data class EnrollmentUiState(
    val stepIndex: Int = 0,
    val draft: CustomerEnrollment = CustomerEnrollment(),
    val availablePlans: List<Plan> = emptyList(),
    val selectedPlan: Plan? = null,
    val downPaymentInput: String = "",
    val submission: SubmissionState = SubmissionState.Idle
) {
    val currentStep: EnrollmentStep
        get() = EnrollmentStep.ordered[stepIndex.coerceIn(0, EnrollmentStep.COUNT - 1)]

    // ---- Per-field validation -------------------------------------------------

    val isNameValid: Boolean get() = draft.customerName.trim().length >= 3
    val isNationalIdValid: Boolean get() = draft.nationalId.trim().length in 6..20
    val isPhoneValid: Boolean get() = draft.phoneNumber.filter { it.isDigit() }.length in 9..15
    val isImeiValid: Boolean get() = draft.imei.length == IMEI_LENGTH && draft.imei.all { it.isDigit() }
    val isDeviceModelValid: Boolean get() = draft.deviceModel.isNotBlank()
    val isPlanValid: Boolean get() = selectedPlan != null

    private val downPaymentValue: Double? get() = downPaymentInput.toDoubleOrNull()
    val isDownPaymentValid: Boolean
        get() {
            val value = downPaymentValue ?: return false
            val plan = selectedPlan ?: return false
            return value in 0.0..plan.totalLoanAmount
        }

    // ---- Per-step gating ------------------------------------------------------

    val isKycStepValid: Boolean get() = isNameValid && isNationalIdValid && isPhoneValid
    val isDeviceStepValid: Boolean get() = isImeiValid && isDeviceModelValid
    val isPlanStepValid: Boolean get() = isPlanValid && isDownPaymentValid

    val isCurrentStepValid: Boolean
        get() = when (currentStep) {
            EnrollmentStep.KYC -> isKycStepValid
            EnrollmentStep.DEVICE -> isDeviceStepValid
            EnrollmentStep.PLAN -> isPlanStepValid
        }

    val isFirstStep: Boolean get() = stepIndex == 0
    val isLastStep: Boolean get() = stepIndex == EnrollmentStep.COUNT - 1
    val isSubmitting: Boolean get() = submission is SubmissionState.Submitting

    private companion object {
        const val IMEI_LENGTH = 15
    }
}
