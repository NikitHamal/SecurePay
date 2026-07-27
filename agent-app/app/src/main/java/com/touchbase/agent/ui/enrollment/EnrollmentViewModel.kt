package com.touchbase.agent.ui.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchbase.agent.data.model.CreateAccountRequest
import com.touchbase.agent.data.model.Device
import com.touchbase.agent.data.remote.SecurePayRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EnrollmentViewModel(
    private val repository: SecurePayRepository?
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnrollmentUiState())
    val uiState: StateFlow<EnrollmentUiState> = _uiState.asStateFlow()

    private var devicesLoaded = false

    init {
        loadDevices()
    }

    private fun loadDevices() {
        if (devicesLoaded) return
        if (repository == null) {
            devicesLoaded = true
            return
        }
        refreshDevices()
    }

    /** Re-pulls the dealer's inventory so the serial picker always shows the freshest "not yet sold" list. */
    fun refreshDevices() {
        if (repository == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDevices = true) }
            repository.listDevices().fold(
                onSuccess = { devices ->
                    devicesLoaded = true
                    _uiState.update { state ->
                        val refreshedLookup = if (state.draft.imei.length == IMEI_LENGTH) {
                            lookupDevice(state.draft.imei, devices)
                        } else {
                            state.deviceLookupStatus
                        }
                        val refreshedModel = (refreshedLookup as? DeviceLookupStatus.Found)?.model
                            ?: state.draft.deviceModel
                        state.copy(
                            availableDevices = devices,
                            isLoadingDevices = false,
                            deviceLookupStatus = refreshedLookup,
                            draft = state.draft.copy(deviceModel = refreshedModel)
                        )
                    }
                },
                onFailure = {
                    devicesLoaded = true
                    _uiState.update { it.copy(isLoadingDevices = false) }
                }
            )
        }
    }

    fun updateDraft(transform: (EnrollmentDraft) -> EnrollmentDraft) = _uiState.update {
        it.copy(draft = transform(it.draft))
    }

    fun updateFirstName(v: String) = updateDraft { it.copy(firstName = v) }
    fun updateSurname(v: String) = updateDraft { it.copy(surname = v) }
    fun updateIdType(v: String) = updateDraft { it.copy(idType = v) }
    fun updateNationalId(v: String) = updateDraft { it.copy(nationalId = v) }
    fun updatePhone(v: String) = updateDraft { it.copy(phoneNumber = v) }
    fun updateOtherPhone(v: String) = updateDraft { it.copy(otherPhone = v) }
    fun updateDateOfBirth(v: String) = updateDraft { it.copy(dateOfBirth = formatDobInput(v)) }
    fun updateMaritalStatus(v: String) = updateDraft { it.copy(maritalStatus = v) }
    fun updateEmploymentStatus(v: String) = updateDraft { it.copy(employmentStatus = v) }
    fun updateGender(v: String) = updateDraft { it.copy(gender = v) }
    fun updateIsCustomerUser(v: Boolean) = updateDraft { it.copy(isCustomerUser = v) }
    fun updateRegion(v: String) = updateDraft { if (it.region == v) it else it.copy(region = v, district = "") }
    fun updateDistrict(v: String) = updateDraft { it.copy(district = v) }
    fun updatePhysicalAddress(v: String) = updateDraft { it.copy(physicalAddress = v) }
    fun updatePreferredLanguage(v: String) = updateDraft { it.copy(preferredLanguage = v) }

    fun updateNextOfKinName(v: String) = updateDraft { it.copy(nextOfKinName = v) }
    fun updateNextOfKinRelation(v: String) = updateDraft { it.copy(nextOfKinRelation = v) }
    fun updateNextOfKinPhone(v: String) = updateDraft { it.copy(nextOfKinPhone = v) }
    fun updateRefereeName(v: String) = updateDraft { it.copy(refereeName = v) }
    fun updateRefereePhone(v: String) = updateDraft { it.copy(refereePhone = v) }
    fun updateGuarantorName(v: String) = updateDraft { it.copy(guarantorName = v) }
    fun updateGuarantorRelation(v: String) = updateDraft { it.copy(guarantorRelation = v) }
    fun updateGuarantorPhone(v: String) = updateDraft { it.copy(guarantorPhone = v) }
    fun updateGuarantorIdNumber(v: String) = updateDraft { it.copy(guarantorIdNumber = v) }

    fun updateCustomerPhoto(v: String?) = updateDraft { it.copy(customerPhotoBase64 = v) }
    fun updateIdFront(v: String?) = updateDraft { it.copy(nationalIdFrontBase64 = v) }
    fun updateIdBack(v: String?) = updateDraft { it.copy(nationalIdBackBase64 = v) }

    fun updateConsentTerms(v: Boolean) = updateDraft { it.copy(consentTerms = v, consentData = v) }
    fun updateSignature(v: String?) = updateDraft { it.copy(signatureBase64 = v) }

    fun updateImei(value: String) = _uiState.update {
        val sanitized = value.filter { ch -> ch.isDigit() }.take(IMEI_LENGTH)
        val status = if (sanitized.length == IMEI_LENGTH) lookupDevice(sanitized, it.availableDevices)
        else DeviceLookupStatus.Idle
        val newModel = if (status is DeviceLookupStatus.Found) status.model else it.draft.deviceModel
        it.copy(
            draft = it.draft.copy(imei = sanitized, deviceModel = newModel),
            deviceLookupStatus = status
        )
    }

    fun selectDevice(device: Device) = _uiState.update {
        it.copy(
            draft = it.draft.copy(imei = device.imei, deviceModel = device.model),
            deviceLookupStatus = lookupDevice(device.imei, it.availableDevices)
        )
    }

    private fun lookupDevice(imei: String, devices: List<Device>): DeviceLookupStatus {
        val device = devices.firstOrNull { it.imei == imei } ?: return DeviceLookupStatus.NotFound
        return when (device.status) {
            "sold" -> DeviceLookupStatus.AlreadySold
            else -> DeviceLookupStatus.Found(device.model)
        }
    }

    fun updateDeviceModel(value: String) = updateDraft { it.copy(deviceModel = value) }

    fun updateDailyRate(value: String) = _uiState.update { state ->
        val sanitized = value.filter { it.isDigit() || it == '.' }
        val parsed = sanitized.toDoubleOrNull() ?: 0.0
        state.copy(dailyRateInput = sanitized, draft = state.draft.copy(dailyRate = (parsed * 100).toInt()))
    }

    fun updateTotalAmount(value: String) = _uiState.update { state ->
        val sanitized = value.filter { it.isDigit() || it == '.' }
        val parsed = sanitized.toDoubleOrNull() ?: 0.0
        state.copy(totalAmountInput = sanitized, draft = state.draft.copy(totalLoanAmount = (parsed * 100).toInt()))
    }

    fun updateTermDays(value: String) = _uiState.update { state ->
        val sanitized = value.filter { it.isDigit() }
        state.copy(termDaysInput = sanitized, draft = state.draft.copy(termDays = sanitized.toIntOrNull() ?: 0))
    }

    fun updateDownPayment(value: String) = _uiState.update { state ->
        val sanitized = value.filter { it.isDigit() || it == '.' }
        val parsed = sanitized.toDoubleOrNull() ?: 0.0
        state.copy(downPaymentInput = sanitized, draft = state.draft.copy(downPayment = (parsed * 100).toInt()))
    }

    fun nextStep() = _uiState.update { state ->
        try {
            if (state.isCurrentStepValid && !state.isLastStep) state.copy(stepIndex = state.stepIndex + 1) else state
        } catch (_: Exception) {
            state
        }
    }

    fun prevStep() = _uiState.update { state ->
        if (!state.isFirstStep) state.copy(stepIndex = state.stepIndex - 1) else state
    }

    fun goToStep(index: Int) = _uiState.update { state ->
        state.copy(stepIndex = index.coerceIn(0, EnrollmentStep.COUNT - 1))
    }

    /** The exact agreement text built from the current draft (shown before signing + sent to the server). */
    fun buildAgreement(): String {
        val s = _uiState.value
        val d = s.draft
        return AgreementText.build(
            AgreementText.Parties(
                firstName = d.firstName,
                surname = d.surname,
                idType = d.idType,
                idNumber = d.nationalId,
                phone = d.phoneNumber,
                otherPhone = d.otherPhone,
                dateOfBirth = d.dateOfBirth,
                gender = d.gender,
                maritalStatus = d.maritalStatus,
                employmentStatus = d.employmentStatus,
                region = d.region,
                district = d.district,
                physicalAddress = d.physicalAddress,
                preferredLanguage = d.preferredLanguage,
                customerName = d.customerName,
                deviceModel = d.deviceModel,
                imei = d.imei,
                planName = d.planName,
                totalLoanAmountCents = d.totalLoanAmount,
                downPaymentCents = d.downPayment,
                dailyRateCents = d.dailyRate,
                termDays = d.termDays,
                kinName = d.nextOfKinName,
                kinRelation = d.nextOfKinRelation,
                kinPhone = d.nextOfKinPhone,
                refereeName = d.refereeName,
                refereePhone = d.refereePhone,
                guarantorName = d.guarantorName,
                guarantorRelation = d.guarantorRelation,
                guarantorPhone = d.guarantorPhone,
                guarantorId = d.guarantorIdNumber
            )
        )
    }

    fun submit() {
        val state = _uiState.value
        if (!state.isSubmitReady) return
        if (state.submission is SubmissionState.Submitting) return

        _uiState.update { it.copy(submission = SubmissionState.Submitting) }

        viewModelScope.launch {
            if (repository == null) {
                _uiState.update { it.copy(submission = SubmissionState.Success("LOCAL_PREVIEW_ENROLLMENT_ID", "0240000000", "12345678")) }
                return@launch
            }
            val d = state.draft
            val request = CreateAccountRequest(
                customerName = d.customerName,
                nationalId = d.nationalId,
                phoneNumber = d.phoneNumber,
                imei = d.imei,
                dailyRate = if (d.dailyRate > 0) d.dailyRate else null,
                totalAmount = if (d.totalLoanAmount > 0) d.totalLoanAmount else null,
                termDays = if (d.termDays > 0) d.termDays else null,
                downPayment = if (d.downPayment > 0) d.downPayment else null,
                customerPhoto = d.customerPhotoBase64,
                nationalIdFront = d.nationalIdFrontBase64,
                nationalIdBack = d.nationalIdBackBase64,
                idType = d.idType.ifBlank { null },
                nextOfKinName = d.nextOfKinName.ifBlank { null },
                nextOfKinPhone = d.nextOfKinPhone.ifBlank { null },
                nextOfKinRelation = d.nextOfKinRelation.ifBlank { null },
                refereeName = d.refereeName.ifBlank { null },
                refereePhone = d.refereePhone.ifBlank { null },
                guarantorName = d.guarantorName.ifBlank { null },
                guarantorPhone = d.guarantorPhone.ifBlank { null },
                guarantorIdNumber = d.guarantorIdNumber.ifBlank { null },
                guarantorRelation = d.guarantorRelation.ifBlank { null },
                consentTerms = d.consentTerms,
                consentData = d.consentData,
                customerSignature = d.signatureBase64,
                surname = d.surname.ifBlank { null },
                otherPhone = d.otherPhone.ifBlank { null },
                dateOfBirth = d.dateOfBirth.ifBlank { null },
                maritalStatus = d.maritalStatus.ifBlank { null },
                employmentStatus = d.employmentStatus.ifBlank { null },
                gender = d.gender.ifBlank { null },
                isCustomerUser = d.isCustomerUser,
                region = d.region.ifBlank { null },
                district = d.district.ifBlank { null },
                physicalAddress = d.physicalAddress.ifBlank { null },
                preferredLanguage = d.preferredLanguage.ifBlank { null },
                agreementText = buildAgreement()
            )

            val result = repository.createAccount(request)
            _uiState.update { current ->
                current.copy(
                    submission = result.fold(
                        onSuccess = { account ->
                            SubmissionState.Success(
                                enrollmentId = account.id,
                                accountNumber = account.initialCredentials?.accountNumber.orEmpty(),
                                temporaryPin = account.initialCredentials?.temporaryPin.orEmpty()
                            )
                        },
                        onFailure = { SubmissionState.Error(it.message ?: "Enrollment failed") }
                    )
                )
            }
        }
    }

    fun clearSubmissionError() = _uiState.update {
        if (it.submission is SubmissionState.Error) it.copy(submission = SubmissionState.Idle) else it
    }

    companion object {
        private const val IMEI_LENGTH = 15

        /** Auto-inserts slashes while the agent types a date: dd/MM/yyyy. */
        private fun formatDobInput(value: String): String {
            val digits = value.filter { it.isDigit() }.take(8)
            val sb = StringBuilder()
            digits.forEachIndexed { index, c ->
                if (index == 2 || index == 4) sb.append('/')
                sb.append(c)
            }
            return sb.toString()
        }
    }
}
