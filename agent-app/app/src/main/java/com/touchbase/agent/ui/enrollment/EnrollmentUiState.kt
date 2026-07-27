package com.touchbase.agent.ui.enrollment

import com.touchbase.agent.data.model.Device
import com.touchbase.agent.data.model.Plan

/**
 * The M-KOPA "Start Application" flow — one small focus per screen, grouped
 * under 6 progress dots exactly like the reference app:
 *
 *  dot 1  Customer information  — INTRO, CUSTOMER, DETAILS, CONTACTS (refs & guarantor)
 *  dot 2  Identity verification — IDENTITY (photo capture)
 *  dot 3  Location              — LOCATION
 *  dot 4  Product information   — PRODUCT (serial), OFFERS, LOAN (loan details)
 *  dot 5  Verification result   — VERIFY
 *  dot 6  Consent & signature   — CONSENT
 */
enum class EnrollmentStep(val dot: Int) {
    INTRO(0),
    CUSTOMER(0),
    DETAILS(0),
    CONTACTS(0),
    IDENTITY(1),
    LOCATION(2),
    PRODUCT(3),
    OFFERS(3),
    LOAN(3),
    VERIFY(4),
    CONSENT(5);

    companion object {
        val ordered: List<EnrollmentStep> = entries
        const val COUNT: Int = 11
        const val DOT_COUNT: Int = 6
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
    // Customer information
    val firstName: String = "",
    val surname: String = "",
    val idType: String = "",
    val nationalId: String = "",
    val phoneNumber: String = "",
    val otherPhone: String = "",
    val dateOfBirth: String = "", // dd/MM/yyyy display format
    val maritalStatus: String = "",
    val employmentStatus: String = "",
    val gender: String = "",
    val isCustomerUser: Boolean? = null,
    // Location details
    val region: String = "",
    val district: String = "",
    val physicalAddress: String = "",
    val preferredLanguage: String = "",
    // Device / plan
    val imei: String = "",
    val deviceModel: String = "",
    val planName: String = "",
    val totalLoanAmount: Int = 0,
    val downPayment: Int = 0,
    val dailyRate: Int = 0,
    val termDays: Int = 0,
    // Identity captures
    val customerPhotoBase64: String? = null,
    val nationalIdFrontBase64: String? = null,
    val nationalIdBackBase64: String? = null,
    // Next of kin, referee, guarantor
    val nextOfKinName: String = "",
    val nextOfKinRelation: String = "",
    val nextOfKinPhone: String = "",
    val refereeName: String = "",
    val refereePhone: String = "",
    val guarantorName: String = "",
    val guarantorRelation: String = "",
    val guarantorPhone: String = "",
    val guarantorIdNumber: String = "",
    // Consent + signature
    val consentTerms: Boolean = false,
    val consentData: Boolean = false,
    val signatureBase64: String? = null
) {
    /** Full customer name as stored on the account. */
    val customerName: String
        get() = "$firstName $surname".replace(Regex("\\s+"), " ").trim()
}

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

    /** The M-KOPA section header (icon label) shown above the current screen. */
    val currentSection: String
        get() = when (currentStep) {
            EnrollmentStep.INTRO, EnrollmentStep.CUSTOMER, EnrollmentStep.DETAILS -> "Customer information"
            EnrollmentStep.CONTACTS -> "Personal references"
            EnrollmentStep.IDENTITY -> "Identity verification"
            EnrollmentStep.LOCATION -> "Customer location details"
            EnrollmentStep.PRODUCT, EnrollmentStep.OFFERS, EnrollmentStep.LOAN -> "Product information"
            EnrollmentStep.VERIFY -> "Identity verification result"
            EnrollmentStep.CONSENT -> "Customer consent for collection and processing of their personal data and product information."
        }

    // ---- Customer information ----
    val isFirstNameValid: Boolean get() = draft.firstName.trim().length >= 2
    val isSurnameValid: Boolean get() = draft.surname.trim().length >= 2
    val isNameValid: Boolean get() = isFirstNameValid && isSurnameValid
    val isNationalIdValid: Boolean get() = draft.nationalId.trim().length in 6..20
    val isIdTypeValid: Boolean get() = draft.idType.isNotBlank()
    val isPhoneValid: Boolean get() = draft.phoneNumber.isValidPhone()
    val isOtherPhoneValid: Boolean
        get() = draft.otherPhone.isBlank() ||
            (draft.otherPhone.isValidPhone() && draft.otherPhone.digits() != draft.phoneNumber.digits())

    val isCustomerStepValid: Boolean
        get() = isFirstNameValid && isSurnameValid && isIdTypeValid && isNationalIdValid &&
            isPhoneValid && isOtherPhoneValid

    // ---- Personal details ----
    val isDobValid: Boolean
        get() {
            val match = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").matchEntire(draft.dateOfBirth.trim()) ?: return false
            val (day, month, year) = match.destructured
            val d = day.toIntOrNull() ?: return false
            val m = month.toIntOrNull() ?: return false
            val y = year.toIntOrNull() ?: return false
            return d in 1..31 && m in 1..12 && y in 1930..2012
        }
    val isMaritalValid: Boolean get() = draft.maritalStatus.isNotBlank()
    val isEmploymentValid: Boolean get() = draft.employmentStatus.isNotBlank()
    val isGenderValid: Boolean get() = draft.gender.isNotBlank()
    val isDetailsStepValid: Boolean
        get() = isDobValid && isMaritalValid && isEmploymentValid && isGenderValid && draft.isCustomerUser != null

    // ---- References (next of kin + referee + guarantor) ----
    private val customerPhoneDigits: String get() = draft.phoneNumber.digits()
    val isNextOfKinNameValid: Boolean get() = draft.nextOfKinName.trim().length >= 3
    val isNextOfKinRelationValid: Boolean get() = draft.nextOfKinRelation.isNotBlank()
    val isNextOfKinPhoneValid: Boolean
        get() = draft.nextOfKinPhone.isValidPhone() && draft.nextOfKinPhone.digits() != customerPhoneDigits
    val isRefereeNameValid: Boolean get() = draft.refereeName.trim().length >= 3
    val isRefereePhoneValid: Boolean
        get() = draft.refereePhone.isValidPhone() &&
            draft.refereePhone.digits() != customerPhoneDigits &&
            draft.refereePhone.digits() != draft.nextOfKinPhone.digits()
    val isGuarantorNameValid: Boolean get() = draft.guarantorName.trim().length >= 3
    val isGuarantorRelationValid: Boolean get() = draft.guarantorRelation.isNotBlank()
    val isGuarantorPhoneValid: Boolean
        get() = draft.guarantorPhone.isValidPhone() && draft.guarantorPhone.digits() != customerPhoneDigits
    val isGuarantorIdValid: Boolean get() = draft.guarantorIdNumber.trim().length in 4..24

    val isContactsStepValid: Boolean
        get() = isNextOfKinNameValid && isNextOfKinRelationValid && isNextOfKinPhoneValid &&
            isRefereeNameValid && isRefereePhoneValid &&
            isGuarantorNameValid && isGuarantorRelationValid && isGuarantorPhoneValid && isGuarantorIdValid

    // ---- Identity verification ----
    val isIdentityStepValid: Boolean
        get() = draft.nationalIdFrontBase64 != null &&
            draft.nationalIdBackBase64 != null &&
            draft.customerPhotoBase64 != null

    // ---- Location ----
    val isRegionValid: Boolean get() = draft.region.isNotBlank()
    val isDistrictValid: Boolean get() = draft.district.isNotBlank()
    val isAddressValid: Boolean get() = draft.physicalAddress.trim().length >= 2
    val isLanguageValid: Boolean get() = draft.preferredLanguage.isNotBlank()
    val isLocationStepValid: Boolean
        get() = isRegionValid && isDistrictValid && isAddressValid && isLanguageValid

    // ---- Product ----
    val isImeiValid: Boolean get() = draft.imei.length == IMEI_LENGTH && draft.imei.all { it.isDigit() }
    val isDeviceModelValid: Boolean get() = draft.deviceModel.isNotBlank()
    val isProductStepValid: Boolean
        get() = isImeiValid && isDeviceModelValid && deviceLookupStatus !is DeviceLookupStatus.AlreadySold

    // ---- Offers & loan ----
    private val dailyRateCents: Int get() = (dailyRateInput.toDoubleOrNull() ?: 0.0).let { (it * 100).toInt() }
    private val totalAmountCents: Int get() = (totalAmountInput.toDoubleOrNull() ?: 0.0).let { (it * 100).toInt() }
    private val termDaysValue: Int get() = termDaysInput.toIntOrNull() ?: 0
    private val downPaymentValue: Double? get() = downPaymentInput.toDoubleOrNull()
    private val downPaymentCents: Int get() = (downPaymentValue ?: 0.0).let { (it * 100).toInt() }

    val isPlanSelected: Boolean get() = selectedPlan != null
    val isCustomPlan: Boolean get() = !isPlanSelected
    val isDailyRateValid: Boolean get() = if (isPlanSelected) true else dailyRateCents > 0
    val isTotalAmountValid: Boolean get() = if (isPlanSelected) true else totalAmountCents > 0
    val isTermDaysValid: Boolean get() = if (isPlanSelected) true else termDaysValue > 0
    /** OFFERS screen is complete once an offer card (or valid custom terms) is chosen. */
    val isOffersStepValid: Boolean
        get() = isPlanSelected || (isDailyRateValid && isTotalAmountValid && isTermDaysValid)

    val isReferencesStepValid: Boolean get() = isContactsStepValid
    val isSignerStepValid: Boolean get() = isContactsStepValid
    val isDeviceStepValid: Boolean get() = isProductStepValid
    val isPlanStepValid: Boolean get() = isOffersStepValid

    val isDownPaymentValid: Boolean
        get() {
            val value = downPaymentValue ?: return false
            val valueCents = (value * 100).toInt()
            val effectiveTotal = if (isPlanSelected) selectedPlan?.totalAmount ?: 0 else totalAmountCents
            val effectiveMin = selectedPlan?.minDownPayment ?: 0
            val rangeEnd = maxOf(effectiveTotal, 1)
            if (effectiveMin > rangeEnd) return false
            return valueCents in effectiveMin..rangeEnd
        }

    // ---- Consent ----
    val isConsentStepValid: Boolean
        get() = draft.consentTerms && draft.signatureBase64 != null

    val isCurrentStepValid: Boolean
        get() = when (currentStep) {
            EnrollmentStep.INTRO -> true
            EnrollmentStep.CUSTOMER -> isCustomerStepValid
            EnrollmentStep.DETAILS -> isDetailsStepValid
            EnrollmentStep.CONTACTS -> isContactsStepValid
            EnrollmentStep.IDENTITY -> isIdentityStepValid
            EnrollmentStep.LOCATION -> isLocationStepValid
            EnrollmentStep.PRODUCT -> isProductStepValid
            EnrollmentStep.OFFERS -> isOffersStepValid
            EnrollmentStep.LOAN -> downPaymentInput.isNotEmpty() && isDownPaymentValid
            EnrollmentStep.VERIFY -> isIdentityStepValid
            EnrollmentStep.CONSENT -> isConsentStepValid
        }

    /** Everything an agent must complete before the application can be submitted. */
    val isSubmitReady: Boolean
        get() = isCustomerStepValid && isDetailsStepValid && isContactsStepValid &&
            isIdentityStepValid && isLocationStepValid && isProductStepValid &&
            isOffersStepValid && downPaymentInput.isNotEmpty() && isDownPaymentValid &&
            isConsentStepValid

    val isFirstStep: Boolean get() = stepIndex == 0
    val isLastStep: Boolean get() = stepIndex == EnrollmentStep.COUNT - 1
    val isSubmitting: Boolean get() = submission is SubmissionState.Submitting

    companion object {
        const val IMEI_LENGTH = 15

        val ID_TYPES = listOf("Ghana Card", "National ID", "Voter ID", "Passport", "Driver's Licence")
        val RELATIONS = listOf("Spouse", "Parent", "Sibling", "Child", "Relative", "Friend", "Colleague")
        val GENDERS = listOf("Male", "Female")
    }
}

private fun String.isValidPhone(): Boolean = digits().length in 9..15
private fun String.digits(): String = filter { it.isDigit() }
