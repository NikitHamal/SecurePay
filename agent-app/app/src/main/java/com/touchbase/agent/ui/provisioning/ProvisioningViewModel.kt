package com.touchbase.agent.ui.provisioning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.data.local.WifiSettingsStore
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
    val securityText: String = "EFRP policy will be checked when QR is generated",
    val error: String? = null,
    val isGenerating: Boolean = false
)

class ProvisioningViewModel(
    private val repository: SecurePayRepository,
    initialImei: String = "",
    private val wifiSettingsStore: WifiSettingsStore? = null
) : ViewModel() {

    private val savedWifi = wifiSettingsStore?.load()
    private val _uiState = MutableStateFlow(
        ProvisioningUiState(
            imei = initialImei,
            wifiSsid = savedWifi?.ssid.orEmpty(),
            wifiPassword = savedWifi?.password.orEmpty(),
            rememberWifi = true
        )
    )
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
        if (!value) {
            wifiSettingsStore?.clear()
        }
        _uiState.value = _uiState.value.copy(rememberWifi = value)
    }

    fun generateQr() {
        val state = _uiState.value
        if (state.imei.length != 15) {
            _uiState.value = state.copy(error = "Enter a valid 15-digit IMEI")
            return
        }
        val ssid = state.wifiSsid.trim().ifBlank { null }
        val pass = state.wifiPassword.ifBlank { null }
        if (pass != null && ssid == null) {
            _uiState.value = state.copy(error = "Enter the Wi-Fi name or clear the password")
            return
        }
        if (pass != null && pass.length !in 8..63 && !pass.matches(Regex("^[0-9A-Fa-f]{64}$"))) {
            _uiState.value = state.copy(error = "WPA/WPA2 password must be 8–63 characters or 64 hex characters")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGenerating = true, stage = ProvisioningStage.GENERATING, error = null
            )
            repository.generateProvisioningQr(state.imei, ssid, pass)
                .onSuccess { response ->
                    if (state.rememberWifi && ssid != null) {
                        wifiSettingsStore?.save(ssid, pass.orEmpty())
                    } else if (!state.rememberWifi) {
                        wifiSettingsStore?.clear()
                    }
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        stage = ProvisioningStage.READY,
                        qrPayload = response.qrPayload,
                        activationCode = response.activationCode,
                        token = response.token,
                        accountName = response.account.customerName,
                        deviceModel = response.device.model,
                        statusText = "Awaiting device activation",
                        securityText = if (response.securityPolicy.frpEnabled) {
                            "EFRP enabled (${response.securityPolicy.frpAccountCount} admin account ID(s))"
                        } else {
                            "EFRP not configured. Add Google admin user IDs before production rollout."
                        }
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
                        } else if (status.status == "provisioned") {
                            _uiState.value = _uiState.value.copy(
                                stage = ProvisioningStage.POLLING,
                                statusText = "Management installed; finishing activation"
                            )
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
        private val initialImei: String = "",
        private val wifiSettingsStore: WifiSettingsStore? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProvisioningViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return ProvisioningViewModel(repository, initialImei, wifiSettingsStore) as T
        }
    }
}
