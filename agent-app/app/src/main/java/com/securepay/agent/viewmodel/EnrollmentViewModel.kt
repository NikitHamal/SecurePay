package com.securepay.agent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securepay.agent.data.MockEnrollmentRepository
import com.securepay.agent.domain.EnrollmentPayload
import com.securepay.agent.domain.HardwareScan
import com.securepay.agent.domain.KycData
import com.securepay.agent.domain.PaymentPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EnrollmentViewModel(
    private val repository: MockEnrollmentRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(EnrollmentFormState())
    val uiState: StateFlow<EnrollmentFormState> = _uiState.asStateFlow()

    fun updateFirstName(value: String) = updateKyc { it.copy(firstName = value) }
    fun updateLastName(value: String) = updateKyc { it.copy(lastName = value) }
    fun updateNationalId(value: String) = updateKyc { it.copy(nationalId = value) }
    fun updatePhone(value: String) = updateKyc { it.copy(phoneNumber = value) }
    fun updateAddress(value: String) = updateKyc { it.copy(residentialAddress = value) }

    fun captureHardware(imei: String, barcode: String) {
        _uiState.update {
            it.copy(
                hardwareScan = HardwareScan(
                    imei = imei.trim(),
                    barcode = barcode.trim(),
                    capturedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateDownPayment(value: String) {
        val digitsOnly = value.filter(Char::isDigit)
        _uiState.update { it.copy(downPaymentInput = digitsOnly) }
    }

    fun selectPlan(plan: PaymentPlan) {
        _uiState.update { it.copy(selectedPlan = plan) }
    }

    fun next() {
        _uiState.update { state ->
            state.copy(currentStep = (state.currentStep + 1).coerceAtMost(EnrollmentStep.entries.lastIndex))
        }
    }

    fun back() {
        _uiState.update { state ->
            state.copy(currentStep = (state.currentStep - 1).coerceAtLeast(0))
        }
    }

    fun submitEnrollment() {
        val payload = _uiState.value.toPayload() ?: return
        _uiState.update { it.copy(isSubmitting = true, submissionMessage = null) }
        viewModelScope.launch {
            val result = repository.submitEnrollment(payload)
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    submissionMessage = result.fold(
                        onSuccess = { id -> "Enrollment ready for sync: $id" },
                        onFailure = { error -> error.message ?: "Enrollment failed" }
                    )
                )
            }
        }
    }

    private fun updateKyc(transform: (KycData) -> KycData) {
        _uiState.update { it.copy(kycData = transform(it.kycData)) }
    }
}

enum class EnrollmentStep(val title: String) {
    KYC("Customer KYC"),
    HARDWARE("Device Scan"),
    PAYMENT("Plan & Downpayment")
}

data class EnrollmentFormState(
    val currentStep: Int = 0,
    val kycData: KycData = KycData(),
    val hardwareScan: HardwareScan = HardwareScan(),
    val selectedPlan: PaymentPlan = PaymentPlan.FLEX,
    val downPaymentInput: String = PaymentPlan.FLEX.requiredDownPaymentCents.toString(),
    val isSubmitting: Boolean = false,
    val submissionMessage: String? = null
) {
    val steps: List<EnrollmentStep> = EnrollmentStep.entries
    val activeStep: EnrollmentStep = steps[currentStep]
    val canGoBack: Boolean = currentStep > 0
    val canGoNext: Boolean = when (activeStep) {
        EnrollmentStep.KYC -> kycData.firstName.isNotBlank() &&
            kycData.lastName.isNotBlank() &&
            kycData.nationalId.isNotBlank() &&
            kycData.phoneNumber.isNotBlank()
        EnrollmentStep.HARDWARE -> hardwareScan.imei.isNotBlank()
        EnrollmentStep.PAYMENT -> downPaymentInput.toLongOrNull() != null
    }

    fun toPayload(): EnrollmentPayload? {
        val downPayment = downPaymentInput.toLongOrNull() ?: return null
        return EnrollmentPayload(
            kycData = kycData,
            hardwareScan = hardwareScan,
            selectedPlan = selectedPlan,
            downPaymentCents = downPayment,
            agentId = "AGENT-SECUREPAY-001"
        )
    }
}

class EnrollmentViewModelFactory(
    private val repository: MockEnrollmentRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EnrollmentViewModel(repository) as T
    }
}

