package com.touchbase.agent.ui.provisioning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.touchbase.agent.data.remote.SecurePayRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ProvisioningStage { IDLE, GENERATING, READY, POLLING, ACTIVATED, ERROR }

data class ProvisioningUiState(
    val imei: String = "",
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val rememberWifi: Boolean = true,
    val stage: ProvisioningStage = ProvisioningStage.IDLE,
    val qrPayload: String = "",
    val activationCode: String = "",
    val token: String = "",
    val accountName: String = "",
    val deviceModel: String = "",
    val statusText: String = "Awaiting device activation",
    val error: String? = null,
    val isGenerating: Boolean = false
)

class ProvisioningViewModel(
    private val repository: SecurePayRepository,
    initialImei: String = ""
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProvisioningUiState(imei = initialImei))
    val uiState: StateFlow<ProvisioningUiState> = _uiState.asStateFlow()

    fun updateImei(value: String) {
        _uiState.value = _uiState.value.copy(imei = value.trim(), error = null)
    }

    fun updateWifiSsid(value: String) {
        _uiState.value = _uiState.value.copy(wifiSsid = value)
    }

    fun updateWifiPassword(value: String) {
        _uiState.value = _uiState.value.copy(wifiPassword = value)
    }

    fun updateRememberWifi(value: Boolean) {
        _uiState.value = _uiState.value.copy(rememberWifi = value)
    }

    fun generateQr() {
        val state = _uiState.value
        if (state.imei.length != 15) {
            _uiState.value = state.copy(error = "Enter a valid 15-digit IMEI")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGenerating = true, stage = ProvisioningStage.GENERATING, error = null
            )
            val ssid = state.wifiSsid.ifBlank { null }
            val pass = state.wifiPassword.ifBlank { null }
            repository.generateProvisioningQr(state.imei, ssid, pass)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        stage = ProvisioningStage.READY,
                        qrPayload = response.qrPayload,
                        activationCode = response.activationCode,
                        token = response.token,
                        accountName = response.account.customerName,
                        deviceModel = response.device.model,
                        statusText = "Awaiting device activation"
                    )
                    startPolling(response.token)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        stage = ProvisioningStage.ERROR,
                        error = e.message ?: "Failed to generate QR code"
                    )
                }
        }
    }

    private fun startPolling(token: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(stage = ProvisioningStage.POLLING)
            while (_uiState.value.token == token && _uiState.value.stage != ProvisioningStage.ACTIVATED) {
                delay(5000)
                if (_uiState.value.token != token) break
                repository.getProvisioningStatus(token)
                    .onSuccess { status ->
                        if (status.status == "activated") {
                            _uiState.value = _uiState.value.copy(
                                stage = ProvisioningStage.ACTIVATED,
                                statusText = "Device activated successfully"
                            )
                            return@launch
                        } else if (status.status == "expired" || status.status == "revoked") {
                            _uiState.value = _uiState.value.copy(
                                statusText = "This code has ${status.status}. Generate a new QR."
                            )
                            return@launch
                        }
                    }
            }
        }
    }

    class Factory(
        private val repository: SecurePayRepository,
        private val initialImei: String = ""
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProvisioningViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return ProvisioningViewModel(repository, initialImei) as T
        }
    }
}
