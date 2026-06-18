package com.touchbase.user.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.touchbase.user.admin.DevicePolicyController
import com.touchbase.user.admin.ProvisioningExtrasStore
import com.touchbase.user.admin.SecurityChecker
import com.touchbase.user.data.repository.DeviceRepository
import com.touchbase.user.ui.activation.ActivationScreen
import com.touchbase.user.ui.activation.ActivationViewModel
import com.touchbase.user.ui.dashboard.DashboardScreen
import com.touchbase.user.ui.navigation.Screen
import com.touchbase.user.ui.payments.PaymentsScreen

@Composable
fun SecurePayApp(
    repository: DeviceRepository,
    policyController: DevicePolicyController,
    onLocked: () -> Unit = {}
) {
    val context = LocalContext.current
    val isRegistered by repository.isRegistered.collectAsState()
    val startDestination = if (isRegistered) Screen.Dashboard.route else Screen.Activation.route

    val navController = rememberNavController()
    val deviceViewModel: DeviceViewModel = viewModel(
        factory = DeviceViewModel.Factory(repository)
    )
    val provisioningToken = remember(context) {
        ProvisioningExtrasStore.provisioningToken(context)
    }
    val activationViewModel: ActivationViewModel = viewModel(
        factory = ActivationViewModel.Factory(repository, provisioningToken)
    )
    val activationState by activationViewModel.uiState.collectAsState()
    val pendingActivationCode = remember(context) {
        ProvisioningExtrasStore.activationCode(context).orEmpty()
    }

    LaunchedEffect(pendingActivationCode, isRegistered) {
        if (!isRegistered && pendingActivationCode.length == 6) {
            activationViewModel.updateCode(pendingActivationCode)
            activationViewModel.checkAndActivate()
        }
    }

    LaunchedEffect(activationState.isActivated) {
        if (activationState.isActivated) {
            ProvisioningExtrasStore.clearActivationCode(context)
            ProvisioningExtrasStore.clearOneTimeToken(context)
        }
    }

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
            if (nowLocked) {
                policyController.enforceLock()
                onLocked()
            } else {
                policyController.releaseRestrictions()
            }
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
                onRefresh = deviceViewModel::refreshStatus,
                onMessageShown = deviceViewModel::consumeMessage,
                onViewPayments = { navController.navigate(Screen.Payments.route) },
                securityReport = securityReport
            )
        }

        composable(Screen.Payments.route) {
            PaymentsScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
