package com.securepay.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securepay.customer.data.MockFinancingRepository
import com.securepay.customer.domain.DeviceStatus
import com.securepay.customer.policy.DevicePolicyController
import com.securepay.customer.ui.CustomerDashboardScreen
import com.securepay.customer.ui.theme.SecurePayTheme
import com.securepay.customer.viewmodel.CustomerDashboardViewModel
import com.securepay.customer.viewmodel.CustomerDashboardViewModelFactory

class MainActivity : ComponentActivity() {
    private val policyController by lazy { DevicePolicyController(this) }
    private val viewModel by viewModels<CustomerDashboardViewModel> {
        CustomerDashboardViewModelFactory(MockFinancingRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

            LaunchedEffect(uiState.status) {
                if (uiState.status == DeviceStatus.LOCKED) {
                    policyController.enforceLockedMode(this@MainActivity)
                } else {
                    policyController.clearLockedMode(this@MainActivity)
                }
            }

            SecurePayTheme {
                CustomerDashboardScreen(
                    uiState = uiState,
                    onSimulatePayment = viewModel::simulatePayment,
                    onRequestGraceWindow = viewModel::requestGraceWindow,
                    onEmergencyCall = policyController::openEmergencyDialer
                )
            }
        }
    }
}
