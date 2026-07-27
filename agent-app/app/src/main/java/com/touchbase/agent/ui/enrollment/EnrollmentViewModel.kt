package com.touchbase.agent.ui.enrollment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.touchbase.agent.data.model.CreateAccountRequest
import com.touchbase.agent.data.model.Device
import com.touchbase.agent.data.model.Plan
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

    private var plansLoaded = false
    private var devicesLoaded = false

    init {
        loadPlans()
        loadDevices()
    }

    private fun loadPlans() {
        if (plansLoaded) return
        if (repository == null) {
            plansLoaded = true
            return
        }
        viewModelScope.launch {
            val result = repository.listPlans()
            result.fold(
                onSuccess = { plans ->
                    _uiState.update { it.copy(availablePlans = plans) }
                    plansLoaded = true
                },
                onFailure = { }
            )
        }
    }

    private fun loadDevices() {
        if (devicesLoaded) return
        if (repository == null) {
            devicesLoaded = true
            return
        }
        refreshDevices()
    }

    /**
     * Re-pulls the dealer's inventory so the device picker always shows the
     * freshest "not yet sold" list. Called when the Device step is entered.
     */
    fun refreshDevices() {
        if (repository == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDevices = true) }
            val result = repository.listDevices()
            result.fold(
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

    fun updateKycName(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(customerName = value))
    }

    fun updateKycNationalId(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(nationalId = value))
    }

    fun updateKycPhone(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(phoneNumber = value))
    }

    fun updateKycPhoto(value: String?) = _uiState.update {
        it.copy(draft = it.draft.copy(customerPhotoBase64 = value))
    }

    fun updateKycIdFront(value: String?) = _uiState.update {
        it.copy(draft = it.draft.copy(nationalIdFrontBase64 = value))
    }

    fun updateKycIdBack(value: String?) = _uiState.update {
        it.copy(draft = it.draft.copy(nationalIdBackBase64 = value))
    }

    fun updateKycIdType(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(idType = value))
    }

    fun updateNextOfKinName(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(nextOfKinName = value))
    }

    fun updateNextOfKinRelation(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(nextOfKinRelation = value))
    }

    fun updateNextOfKinPhone(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(nextOfKinPhone = value))
    }

    fun updateRefereeName(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(refereeName = value))
    }

    fun updateRefereePhone(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(refereePhone = value))
    }

    fun updateGuarantorName(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(guarantorName = value))
    }

    fun updateGuarantorRelation(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(guarantorRelation = value))
    }

    fun updateGuarantorPhone(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(guarantorPhone = value))
    }

    fun updateGuarantorIdNumber(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(guarantorIdNumber = value))
    }

    fun updateConsentTerms(value: Boolean) = _uiState.update {
        it.copy(draft = it.draft.copy(consentTerms = value))
    }

    fun updateConsentData(value: Boolean) = _uiState.update {
        it.copy(draft = it.draft.copy(consentData = value))
    }

    fun updateSignature(value: String?) = _uiState.update {
        it.copy(draft = it.draft.copy(signatureBase64 = value))
    }

    fun updateImei(value: String) = _uiState.update {
        val sanitized = value.filter { ch -> ch.isDigit() }.take(IMEI_LENGTH)
        val status = if (sanitized.length == IMEI_LENGTH) {
            lookupDevice(sanitized, it.availableDevices)
        } else {
            DeviceLookupStatus.Idle
        }
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
        val device = devices.firstOrNull { it.imei == imei }
            ?: return DeviceLookupStatus.NotFound
        return when (device.status) {
            "sold" -> DeviceLookupStatus.AlreadySold
            else -> DeviceLookupStatus.Found(device.model)
        }
    }

    fun updateDeviceModel(value: String) = _uiState.update {
        it.copy(draft = it.draft.copy(deviceModel = value))
    }

    fun selectPlan(plan: Plan?) = _uiState.update { state ->
        if (plan == null) {
            state.copy(
                selectedPlan = null,
                dailyRateInput = "",
                totalAmountInput = "",
                termDaysInput = "",
                downPaymentInput = "",
                draft = state.draft.copy(
                    planName = "",
                    totalLoanAmount = 0,
                    dailyRate = 0,
                    termDays = 0,
                    downPayment = 0
                )
            )
        } else {
            val planDaily = (plan.dailyRate / 100.0).toBigDecimal().stripTrailingZeros().toPlainString()
            val planTotal = (plan.totalAmount / 100.0).toBigDecimal().stripTrailingZeros().toPlainString()
            state.copy(
                selectedPlan = plan,
                dailyRateInput = state.dailyRateInput.ifBlank { planDaily },
                totalAmountInput = state.totalAmountInput.ifBlank { planTotal },
                termDaysInput = state.termDaysInput.ifBlank { plan.termDays.toString() },
                downPaymentInput = state.downPaymentInput.ifBlank {
                    val minCents = plan.minDownPayment
                    (minCents / 100.0).toBigDecimal().stripTrailingZeros().toPlainString()
                },
                draft = state.draft.copy(
                    planName = plan.name,
                    totalLoanAmount = plan.totalAmount,
                    dailyRate = plan.dailyRate,
                    termDays = plan.termDays
                )
            )
        }
    }

    fun updateDailyRate(value: String) = _uiState.update { state ->
        val sanitized = value.filter { it.isDigit() || it == '.' }
        val parsed = sanitized.toDoubleOrNull() ?: 0.0
        state.copy(
            dailyRateInput = sanitized,
            draft = state.draft.copy(dailyRate = (parsed * 100).toInt())
        )
    }

    fun updateTotalAmount(value: String) = _uiState.update { state ->
        val sanitized = value.filter { it.isDigit() || it == '.' }
        val parsed = sanitized.toDoubleOrNull() ?: 0.0
        state.copy(
            totalAmountInput = sanitized,
            draft = state.draft.copy(totalLoanAmount = (parsed * 100).toInt())
        )
    }

    fun updateTermDays(value: String) = _uiState.update { state ->
        val sanitized = value.filter { it.isDigit() }
        state.copy(
            termDaysInput = sanitized,
            draft = state.draft.copy(termDays = sanitized.toIntOrNull() ?: 0)
        )
    }

    fun updateDownPayment(value: String) = _uiState.update { state ->
        val sanitized = value.filter { it.isDigit() || it == '.' }
        val parsed = sanitized.toDoubleOrNull() ?: 0.0
        state.copy(
            downPaymentInput = sanitized,
            draft = state.draft.copy(downPayment = (parsed * 100).toInt())
        )
    }

    fun nextStep() = _uiState.update { state ->
        try {
            if (state.isCurrentStepValid && !state.isLastStep) {
                state.copy(stepIndex = state.stepIndex + 1)
            } else {
                state
            }
        } catch (_: Exception) {
            state
        }
    }

    fun prevStep() = _uiState.update { state ->
        if (!state.isFirstStep) state.copy(stepIndex = state.stepIndex - 1) else state
    }

    /** Jumps straight to a step — used by the Review screen's "tap to edit" rows. */
    fun goToStep(index: Int) = _uiState.update { state ->
        state.copy(stepIndex = index.coerceIn(0, EnrollmentStep.COUNT - 1))
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
            val plan = state.selectedPlan
            val request = CreateAccountRequest(
                customerName = state.draft.customerName,
                nationalId = state.draft.nationalId,
                phoneNumber = state.draft.phoneNumber,
                imei = state.draft.imei,
                planId = plan?.id,
                dailyRate = if (state.draft.dailyRate > 0) state.draft.dailyRate else null,
                totalAmount = if (state.draft.totalLoanAmount > 0) state.draft.totalLoanAmount else null,
                termDays = if (state.draft.termDays > 0) state.draft.termDays else null,
                downPayment = if (state.draft.downPayment > 0) state.draft.downPayment else null,
                customerPhoto = state.draft.customerPhotoBase64,
                nationalIdFront = state.draft.nationalIdFrontBase64,
                nationalIdBack = state.draft.nationalIdBackBase64,
                idType = state.draft.idType.ifBlank { null },
                nextOfKinName = state.draft.nextOfKinName.ifBlank { null },
                nextOfKinPhone = state.draft.nextOfKinPhone.ifBlank { null },
                nextOfKinRelation = state.draft.nextOfKinRelation.ifBlank { null },
                refereeName = state.draft.refereeName.ifBlank { null },
                refereePhone = state.draft.refereePhone.ifBlank { null },
                guarantorName = state.draft.guarantorName.ifBlank { null },
                guarantorPhone = state.draft.guarantorPhone.ifBlank { null },
                guarantorIdNumber = state.draft.guarantorIdNumber.ifBlank { null },
                guarantorRelation = state.draft.guarantorRelation.ifBlank { null },
                consentTerms = state.draft.consentTerms,
                consentData = state.draft.consentData,
                customerSignature = state.draft.signatureBase64
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
    }
}
