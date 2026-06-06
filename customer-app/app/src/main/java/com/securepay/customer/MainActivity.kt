package com.securepay.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securepay.customer.data.model.DeviceStatus
import com.securepay.customer.ui.screens.DashboardScreen
import com.securepay.customer.ui.screens.LockOverlayScreen
import com.securepay.customer.ui.theme.BackgroundCharcoal
import com.securepay.customer.ui.theme.SecurePayTheme
import com.securepay.customer.ui.viewmodel.DashboardViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecurePayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundCharcoal
                ) {
                    SecurePayApp()
                }
            }
        }
    }
}

/**
 * Root UI. No NavHost: the dashboard is the home surface, and the lock overlay
 * is conditionally drawn on top whenever the live status becomes LOCKED.
 */
@Composable
private fun SecurePayApp(
    viewModel: DashboardViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardScreen(
        state = state,
        onSimulatePayment = viewModel::onSimulatePayment
    )

    // Overlay the non-dismissible lock screen when the device is locked.
    val loan = state.loan
    if (state.status == DeviceStatus.LOCKED && loan != null) {
        LockOverlayScreen(
            loan = loan,
            onRequestGrace = viewModel::onRequestGrace,
            onEmergencyCall = viewModel::onEmergencyCall
        )
    }
}
