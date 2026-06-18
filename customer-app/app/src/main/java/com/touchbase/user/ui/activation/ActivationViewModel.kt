package com.touchbase.user.ui.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.touchbase.user.data.model.ActivateResponse
import com.touchbase.user.data.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActivationUiState(
    val activationCode: String = "",
    val isChecking: Boolean = false,
    val isActivated: Boolean = false,
    val error: String? = null,
    val deviceModel: String = "",
    val customerName: String = "",
    val accountId: String = ""
)

class ActivationViewModel(
    private val repository: DeviceRepository,
    private val provisioningToken: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivationUiState())
    val uiState: StateFlow<ActivationUiState> = _uiState.asStateFlow()

    fun updateCode(value: String) {
        val digits = value.filter { it.isDigit() }.take(6)
        _uiState.value = _uiState.value.copy(activationCode = digits, error = null)
    }

    fun checkAndActivate() {
        if (_uiState.value.isChecking || _uiState.value.isActivated) return
        val code = _uiState.value.activationCode
        if (code.length != 6) {
            _uiState.value = _uiState.value.copy(error = "Please enter your 6-digit activation code")
            return
        }
        val token = provisioningToken
        if (token.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "Provisioning token missing. Reset the phone and scan a newly generated dealer QR code."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true, error = null)
            val result = repository.activate(code, token)
            _uiState.value = _uiState.value.copy(isChecking = false)
            result
                .onSuccess { response -> handleActivateResponse(response) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message ?: "Activation failed. Please check your network."
                    )
                }
        }
    }

    private fun handleActivateResponse(response: ActivateResponse) {
        if (response.activated && response.account != null) {
            _uiState.value = _uiState.value.copy(
                isActivated = true,
                deviceModel = response.device?.model ?: "",
                customerName = response.account.customerName,
                accountId = response.account.id
            )
        } else {
            _uiState.value = _uiState.value.copy(
                error = "Activation failed. Please verify the code with your dealer."
            )
        }
    }

    class Factory(
        private val repository: DeviceRepository,
        private val provisioningToken: String?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ActivationViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return ActivationViewModel(repository, provisioningToken) as T
        }
    }
}
