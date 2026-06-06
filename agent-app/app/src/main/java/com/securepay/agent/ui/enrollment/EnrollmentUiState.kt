package com.securepay.agent.ui.enrollment

import com.securepay.agent.data.model.AccountStatus
import com.securepay.agent.data.model.Plan

enum class EnrollmentStep {
    KYC,
    DEVICE,
    PLAN;

    companion object {
        val ordered: List<EnrollmentStep> = entries
        const val COUNT: Int = 3
    }
}

sealed interface SubmissionState {
    data object Idle : SubmissionState
    data object Submitting : SubmissionState
    data class Success(val enrollmentId: String) : SubmissionState
    data class Error(val message: String) : SubmissionState
}

data class EnrollmentDraft(
    val customerName: String = "",
    val nationalId: String = "",
    val phoneNumber: String = "",
    val imei: String = "",
    val deviceModel: String = "",
    val planName: String = "",
    val totalLoanAmount: Int = 0,
    val downPayment: Int = 0,
    val dailyRate: Int = 0,
    val termDays: Int = 0,
    val status: AccountStatus = AccountStatus.ACTIVE
)

data class EnrollmentUiState(
    val stepIndex: Int = 0,
    val draft: EnrollmentDraft = EnrollmentDraft(),
    val availablePlans: List<Plan> = emptyList(),
    val selectedPlan: Plan? = null,
    val downPaymentInput: String = "",
    val submission: SubmissionState = SubmissionState.Idle
) {
    val currentStep: EnrollmentStep
        get() = EnrollmentStep.ordered[stepIndex.coerceIn(0, EnrollmentStep.COUNT - 1)]

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
            val minCents = plan.minDownPayment
            val maxCents = plan.totalAmount
            val valueCents = (value * 100).toInt()
            return valueCents in minCents..maxCents
        }

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

    companion object {
        const val IMEI_LENGTH = 15
    }
}