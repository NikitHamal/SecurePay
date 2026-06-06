package com.securepay.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securepay.customer.admin.DevicePolicyController
import com.securepay.customer.ui.DeviceViewModel
import com.securepay.customer.ui.dashboard.DashboardScreen
import com.securepay.customer.ui.lock.LockOverlayScreen
import com.securepay.customer.ui.theme.SecurePayTheme

class MainActivity : ComponentActivity() {

    private lateinit var policyController: DevicePolicyController

    private val enableAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result intentionally ignored; controller re-checks live state */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        policyController = DevicePolicyController(this)
        if (!policyController.isAdminActive) {
            enableAdminLauncher.launch(policyController.enableAdminIntent())
        }

        val repository = (application as SecurePayApplication).deviceRepository

        setContent {
            SecurePayTheme {
                val vm: DeviceViewModel = viewModel(
                    factory = DeviceViewModel.Factory(repository, policyController)
                )
                val state by vm.uiState.collectAsStateWithLifecycle()

                // Keep the lock surface visible even over the keyguard on lock.
                ensureLockVisibility(state.isLocked)

                DashboardScreen(
                    state = state,
                    onSimulatePayment = vm::simulatePayment,
                    onMessageShown = vm::consumeMessage
                )

                // Full-screen overlay slides in instantly on LOCKED, covering all
                // dashboard navigation beneath it.
                AnimatedVisibility(
                    visible = state.isLocked,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    LockOverlayScreen(
                        state = state,
                        onRequestGrace = vm::requestGraceWindow
                    )
                }
            }
        }
    }

    /** Surfaces the activity above the keyguard while LOCKED on API 27+. */
    private fun ensureLockVisibility(locked: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(locked)
            setTurnScreenOn(locked)
        }
    }
}
