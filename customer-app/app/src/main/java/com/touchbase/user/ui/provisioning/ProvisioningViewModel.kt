package com.touchbase.user.ui.provisioning

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.touchbase.user.admin.DevicePolicyController
import com.touchbase.user.admin.ProvisioningManager
import com.touchbase.user.admin.ProvisioningState
import com.touchbase.user.admin.SecurityChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProvisioningUiState(
    val provisioningState: ProvisioningState = ProvisioningState.NOT_PROVISIONED,
    val securityReport: SecurityChecker.SecurityReport = SecurityChecker.SecurityReport(),
    val isChecking: Boolean = false,
    val errorMessage: String? = null
)

class ProvisioningViewModel(
    private val provisioningManager: ProvisioningManager,
    private val policyController: DevicePolicyController,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProvisioningUiState())
    val uiState: StateFlow<ProvisioningUiState> = _uiState.asStateFlow()

    init {
        refreshState()
    }

    private fun refreshState() {
        val state = provisioningManager.state
        _uiState.value = _uiState.value.copy(provisioningState = state)
    }

    fun runSecurityCheck() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true)
            val report = withContext(Dispatchers.Default) {
                SecurityChecker.runAllChecks(appContext)
            }
            _uiState.value = _uiState.value.copy(
                securityReport = report,
                isChecking = false
            )
        }
    }

    fun getEnableAdminIntent() = policyController.enableAdminIntent()

    fun getDeviceOwnerIntent() = provisioningManager.createDeviceOwnerProvisioningIntent()

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    class Factory(
        private val provisioningManager: ProvisioningManager,
        private val policyController: DevicePolicyController,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProvisioningViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return ProvisioningViewModel(provisioningManager, policyController, appContext) as T
        }
    }
}
