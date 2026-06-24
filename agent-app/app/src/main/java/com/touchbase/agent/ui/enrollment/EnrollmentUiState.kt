package com.touchbase.agent.ui.enrollment

import com.touchbase.agent.data.model.AccountStatus
import com.touchbase.agent.data.model.Device
import com.touchbase.agent.data.model.Plan

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

sealed interface DeviceLookupStatus {
    data object Idle : DeviceLookupStatus
    data class Found(val model: String) : DeviceLookupStatus
    data object NotFound : DeviceLookupStatus
    data object AlreadySold : DeviceLookupStatus
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
    val status: AccountStatus = AccountStatus.ACTIVE,
    val customerPhotoBase64: String? = null,
    val nationalIdFrontBase64: String? = null,
    val nationalIdBackBase64: String? = null
)

data class EnrollmentUiState(
    val stepIndex: Int = 0,
    val draft: EnrollmentDraft = EnrollmentDraft(),
    val availablePlans: List<Plan> = emptyList(),
    val availableDevices: List<Device> = emptyList(),
    val deviceLookupStatus: DeviceLookupStatus = DeviceLookupStatus.Idle,
    val selectedPlan: Plan? = null,
    val dailyRateInput: String = "",
    val totalAmountInput: String = "",
    val termDaysInput: String = "",
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

    private val dailyRateCents: Int get() = (dailyRateInput.toDoubleOrNull() ?: 0.0).let { (it * 100).toInt() }
    private val totalAmountCents: Int get() = (totalAmountInput.toDoubleOrNull() ?: 0.0).let { (it * 100).toInt() }
    private val termDaysValue: Int get() = termDaysInput.toIntOrNull() ?: 0
    private val downPaymentValue: Double? get() = downPaymentInput.toDoubleOrNull()
    private val downPaymentCents: Int get() = (downPaymentValue ?: 0.0).let { (it * 100).toInt() }

    val isPlanSelected: Boolean get() = selectedPlan != null
    val isCustomPlan: Boolean get() = !isPlanSelected

    val isDailyRateValid: Boolean
        get() = if (isPlanSelected) true else dailyRateCents > 0
    val isTotalAmountValid: Boolean
        get() = if (isPlanSelected) true else totalAmountCents > 0
    val isTermDaysValid: Boolean
        get() = if (isPlanSelected) true else termDaysValue > 0
    val isDownPaymentValid: Boolean
        get() {
            val value = downPaymentValue ?: return false
            val valueCents = (value * 100).toInt()
            val effectiveTotal = if (totalAmountCents > 0) totalAmountCents
                else selectedPlan?.totalAmount ?: 0
            val effectiveMin = selectedPlan?.minDownPayment ?: 0
            return valueCents in effectiveMin..maxOf(effectiveTotal, 1)
        }

    val isKycStepValid: Boolean get() = isNameValid && isNationalIdValid && isPhoneValid &&
            draft.customerPhotoBase64 != null && draft.nationalIdFrontBase64 != null && draft.nationalIdBackBase64 != null
    val isDeviceStepValid: Boolean
        get() = isImeiValid &&
            isDeviceModelValid &&
            deviceLookupStatus !is DeviceLookupStatus.AlreadySold
    val isPlanStepValid: Boolean
        get() = when {
            isPlanSelected -> downPaymentInput.isNotEmpty() && isDownPaymentValid
            else -> isDailyRateValid && isTotalAmountValid && isTermDaysValid && downPaymentInput.isNotEmpty() && isDownPaymentValid
        }

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
