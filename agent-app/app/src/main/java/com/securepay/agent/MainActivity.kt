package com.securepay.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securepay.agent.data.MockEnrollmentRepository
import com.securepay.agent.ui.AgentEnrollmentScreen
import com.securepay.agent.ui.theme.SecurePayAgentTheme
import com.securepay.agent.viewmodel.EnrollmentViewModel
import com.securepay.agent.viewmodel.EnrollmentViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<EnrollmentViewModel> {
        EnrollmentViewModelFactory(MockEnrollmentRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val state = viewModel.uiState.collectAsStateWithLifecycle().value
            SecurePayAgentTheme {
                AgentEnrollmentScreen(
                    state = state,
                    onFirstNameChange = viewModel::updateFirstName,
                    onLastNameChange = viewModel::updateLastName,
                    onNationalIdChange = viewModel::updateNationalId,
                    onPhoneChange = viewModel::updatePhone,
                    onAddressChange = viewModel::updateAddress,
                    onScanComplete = viewModel::captureHardware,
                    onDownPaymentChange = viewModel::updateDownPayment,
                    onPlanSelected = viewModel::selectPlan,
                    onBack = viewModel::back,
                    onNext = viewModel::next,
                    onSubmit = viewModel::submitEnrollment
                )
            }
        }
    }
}

