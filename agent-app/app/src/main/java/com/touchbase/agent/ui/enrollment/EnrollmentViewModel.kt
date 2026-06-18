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
        viewModelScope.launch {
            val result = repository.listDevices()
            result.fold(
                onSuccess = { devices ->
                    _uiState.update { it.copy(availableDevices = devices) }
                    devicesLoaded = true
                },
                onFailure = { devicesLoaded = true }
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

    fun selectPlan(plan: Plan) = _uiState.update { state ->
        state.copy(
            selectedPlan = plan,
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

    fun updateDownPayment(value: String) = _uiState.update { state ->
        val sanitized = value.filter { it.isDigit() || it == '.' }
        val parsed = sanitized.toDoubleOrNull() ?: 0.0
        state.copy(
            downPaymentInput = sanitized,
            draft = state.draft.copy(downPayment = (parsed * 100).toInt())
        )
    }

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

    fun submit() {
        val state = _uiState.value
        if (!state.isKycStepValid || !state.isDeviceStepValid || !state.isPlanStepValid) return
        if (state.submission is SubmissionState.Submitting) return
        val plan = state.selectedPlan ?: return

        _uiState.update { it.copy(submission = SubmissionState.Submitting) }

        viewModelScope.launch {
            if (repository == null) {
                _uiState.update { it.copy(submission = SubmissionState.Success("MOCK_ENROLL_ID")) }
                return@launch
            }
            val request = CreateAccountRequest(
                customerName = state.draft.customerName,
                nationalId = state.draft.nationalId,
                phoneNumber = state.draft.phoneNumber,
                imei = state.draft.imei,
                planId = plan.id,
                downPayment = if (state.draft.downPayment > 0) state.draft.downPayment else null
            )

            val result = repository.createAccount(request)
            _uiState.update { current ->
                current.copy(
                    submission = result.fold(
                        onSuccess = { SubmissionState.Success(it.id) },
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
