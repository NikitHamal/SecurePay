package com.securepay.customer.ui.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.securepay.customer.data.model.DeviceCheckResponse
import com.securepay.customer.data.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActivationUiState(
    val imei: String = "",
    val isChecking: Boolean = false,
    val isEnrolled: Boolean = false,
    val notFound: Boolean = false,
    val error: String? = null,
    val deviceModel: String = "",
    val customerName: String = "",
    val accountId: String = ""
)

class ActivationViewModel(
    private val repository: DeviceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivationUiState())
    val uiState: StateFlow<ActivationUiState> = _uiState.asStateFlow()

    fun updateImei(value: String) {
        _uiState.value = _uiState.value.copy(imei = value.trim(), notFound = false, error = null)
    }

    fun checkAndActivate() {
        val imei = _uiState.value.imei
        if (imei.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Please enter your IMEI number")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true, error = null, notFound = false)
            val result = repository.checkAndRegister(imei)
            _uiState.value = _uiState.value.copy(isChecking = false)
            result
                .onSuccess { response -> handleCheckResponse(response) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Connection failed. Please check your network."
                    )
                }
        }
    }

    private fun handleCheckResponse(response: DeviceCheckResponse) {
        if (response.enrolled && response.account != null && response.device != null) {
            _uiState.value = _uiState.value.copy(
                isEnrolled = true,
                deviceModel = response.device.model,
                customerName = response.account.customerName,
                accountId = response.account.id
            )
        } else if (response.device != null) {
            _uiState.value = _uiState.value.copy(
                notFound = true,
                error = "This device is in our inventory but has not been enrolled yet. Please visit your dealer to complete enrollment."
            )
        } else {
            _uiState.value = _uiState.value.copy(
                notFound = true,
                error = "IMEI not found. Please verify the number and try again."
            )
        }
    }

    class Factory(
        private val repository: DeviceRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ActivationViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return ActivationViewModel(repository) as T
        }
    }
}