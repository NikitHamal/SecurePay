package com.securepay.customer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.securepay.customer.admin.DevicePolicyController
import com.securepay.customer.admin.SecurityChecker
import com.securepay.customer.data.repository.DeviceRepository
import com.securepay.customer.ui.activation.ActivationScreen
import com.securepay.customer.ui.activation.ActivationViewModel
import com.securepay.customer.ui.dashboard.DashboardScreen
import com.securepay.customer.ui.lock.LockOverlayScreen
import com.securepay.customer.ui.navigation.Screen
import com.securepay.customer.ui.payments.PaymentsScreen

@Composable
fun SecurePayApp(
    repository: DeviceRepository,
    policyController: DevicePolicyController
) {
    val context = LocalContext.current
    val isRegistered by repository.isRegistered.collectAsState()
    val startDestination = if (isRegistered) Screen.Dashboard.route else Screen.Activation.route

    val navController = rememberNavController()
    val deviceViewModel: DeviceViewModel = viewModel(
        factory = DeviceViewModel.Factory(repository)
    )
    val activationViewModel: ActivationViewModel = viewModel(
        factory = ActivationViewModel.Factory(repository)
    )

    val state by deviceViewModel.uiState.collectAsState()
    var lastEnforcedLocked by remember { mutableStateOf(false) }

    var securityReport by remember { mutableStateOf<SecurityChecker.SecurityReport?>(null) }

    LaunchedEffect(Unit) {
        val report = SecurityChecker.runAllChecks(context)
        securityReport = report
        if (report.shouldLock) {
            policyController.enforceLock()
        }
    }

    LaunchedEffect(state.isLocked) {
        val nowLocked = state.isLocked
        if (nowLocked != lastEnforcedLocked) {
            if (nowLocked) policyController.enforceLock() else policyController.releaseRestrictions()
            lastEnforcedLocked = nowLocked
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Activation.route) {
            ActivationScreen(
                viewModel = activationViewModel,
                onActivated = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Activation.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                state = state,
                onRefresh = deviceViewModel::simulatePayment,
                onMessageShown = deviceViewModel::consumeMessage,
                onViewPayments = { navController.navigate(Screen.Payments.route) },
                securityReport = securityReport
            )

            AnimatedVisibility(
                visible = state.isLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                LockOverlayScreen(
                    state = state,
                    onRequestGrace = deviceViewModel::requestGraceWindow
                )
            }
        }

        composable(Screen.Payments.route) {
            PaymentsScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}