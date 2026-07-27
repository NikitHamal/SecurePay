package com.touchbase.agent.ui.enrollment

import com.touchbase.agent.data.model.AccountStatus
import com.touchbase.agent.data.model.Device
import com.touchbase.agent.data.model.Plan

enum class EnrollmentStep {
    KYC,
    REFERENCES,
    SIGNER,
    DEVICE,
    PLAN,
    CONSENT,
    REVIEW;

    companion object {
        val ordered: List<EnrollmentStep> = entries
        const val COUNT: Int = 7
    }
}

sealed interface SubmissionState {
    data object Idle : SubmissionState
    data object Submitting : SubmissionState
    data class Success(
        val enrollmentId: String,
        val accountNumber: String = "",
        val temporaryPin: String = ""
    ) : SubmissionState
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
    val idType: String = "",
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
    val nationalIdBackBase64: String? = null,
    val nextOfKinName: String = "",
    val nextOfKinRelation: String = "",
    val nextOfKinPhone: String = "",
    val refereeName: String = "",
    val refereePhone: String = "",
    val guarantorName: String = "",
    val guarantorRelation: String = "",
    val guarantorPhone: String = "",
    val guarantorIdNumber: String = "",
    val consentTerms: Boolean = false,
    val consentData: Boolean = false,
    val signatureBase64: String? = null
)

data class EnrollmentUiState(
    val stepIndex: Int = 0,
    val draft: EnrollmentDraft = EnrollmentDraft(),
    val availablePlans: List<Plan> = emptyList(),
    val availableDevices: List<Device> = emptyList(),
    val isLoadingDevices: Boolean = false,
    val deviceLookupStatus: DeviceLookupStatus = DeviceLookupStatus.Idle,
    val selectedPlan: Plan? = null,
    val dailyRateInput: String = "",
    val totalAmountInput: String = "",
    val termDaysInput: String = "",
    val downPaymentInput: String = "",
    val submission: SubmissionState = SubmissionState.Idle
) {
    /** Devices that can actually be enrolled right now (not already sold). */
    val unsoldDevices: List<Device>
        get() = availableDevices.filter { it.status != "sold" }

    val currentStep: EnrollmentStep
        get() = EnrollmentStep.ordered[stepIndex.coerceIn(0, EnrollmentStep.COUNT - 1)]

    val isNameValid: Boolean get() = draft.customerName.trim().length >= 3
    val isNationalIdValid: Boolean get() = draft.nationalId.trim().length in 6..20
    val isIdTypeValid: Boolean get() = draft.idType.isNotBlank()
    val isPhoneValid: Boolean get() = draft.phoneNumber.isValidPhone()
    val isImeiValid: Boolean get() = draft.imei.length == IMEI_LENGTH && draft.imei.all { it.isDigit() }
    val isDeviceModelValid: Boolean get() = draft.deviceModel.isNotBlank()

    private val customerPhoneDigits: String get() = draft.phoneNumber.filter { it.isDigit() }

    // References step
    val isNextOfKinNameValid: Boolean get() = draft.nextOfKinName.trim().length >= 3
    val isNextOfKinRelationValid: Boolean get() = draft.nextOfKinRelation.isNotBlank()
    val isNextOfKinPhoneValid: Boolean
        get() = draft.nextOfKinPhone.isValidPhone() &&
            draft.nextOfKinPhone.filter { it.isDigit() } != customerPhoneDigits
    val isRefereeNameValid: Boolean get() = draft.refereeName.trim().length >= 3
    val isRefereePhoneValid: Boolean
        get() = draft.refereePhone.isValidPhone() &&
            draft.refereePhone.filter { it.isDigit() } != customerPhoneDigits &&
            draft.refereePhone.filter { it.isDigit() } != draft.nextOfKinPhone.filter { it.isDigit() }

    // Guarantor / signer step
    val isGuarantorNameValid: Boolean get() = draft.guarantorName.trim().length >= 3
    val isGuarantorRelationValid: Boolean get() = draft.guarantorRelation.isNotBlank()
    val isGuarantorPhoneValid: Boolean
        get() = draft.guarantorPhone.isValidPhone() &&
            draft.guarantorPhone.filter { it.isDigit() } != customerPhoneDigits
    val isGuarantorIdValid: Boolean get() = draft.guarantorIdNumber.trim().length in 4..24

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
            val rangeEnd = maxOf(effectiveTotal, 1)
            if (effectiveMin > rangeEnd) return false
            return valueCents in effectiveMin..rangeEnd
        }

    val isKycStepValid: Boolean
        get() = isNameValid && isNationalIdValid && isIdTypeValid && isPhoneValid &&
            draft.customerPhotoBase64 != null && draft.nationalIdFrontBase64 != null && draft.nationalIdBackBase64 != null
    val isReferencesStepValid: Boolean
        get() = isNextOfKinNameValid && isNextOfKinRelationValid && isNextOfKinPhoneValid &&
            isRefereeNameValid && isRefereePhoneValid
    val isSignerStepValid: Boolean
        get() = isGuarantorNameValid && isGuarantorRelationValid && isGuarantorPhoneValid && isGuarantorIdValid
    val isConsentStepValid: Boolean
        get() = draft.consentTerms && draft.consentData && draft.signatureBase64 != null
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
            EnrollmentStep.REFERENCES -> isReferencesStepValid
            EnrollmentStep.SIGNER -> isSignerStepValid
            EnrollmentStep.DEVICE -> isDeviceStepValid
            EnrollmentStep.PLAN -> isPlanStepValid
            EnrollmentStep.CONSENT -> isConsentStepValid
            EnrollmentStep.REVIEW -> true
        }

    /** Everything an agent must complete before the application can be submitted. */
    val isSubmitReady: Boolean
        get() = isKycStepValid && isReferencesStepValid && isSignerStepValid &&
            isDeviceStepValid && isPlanStepValid && isConsentStepValid

    val isFirstStep: Boolean get() = stepIndex == 0
    val isLastStep: Boolean get() = stepIndex == EnrollmentStep.COUNT - 1
    val isSubmitting: Boolean get() = submission is SubmissionState.Submitting

    companion object {
        const val IMEI_LENGTH = 15

        val ID_TYPES = listOf("Ghana Card", "Voter ID", "Passport", "Driver's Licence", "NHIS Card")
        val RELATIONS = listOf("Spouse", "Parent", "Sibling", "Child", "Relative", "Friend", "Colleague")
    }
}

private fun String.isValidPhone(): Boolean = filter { it.isDigit() }.length in 9..15
