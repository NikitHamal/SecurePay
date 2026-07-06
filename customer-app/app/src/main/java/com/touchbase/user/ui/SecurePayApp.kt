package com.touchbase.user.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.touchbase.user.ui.update.UpdateScreen
import com.touchbase.user.ui.release.ReleaseApprovedScreen
import kotlinx.coroutines.launch
import com.touchbase.user.worker.TrackingService

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
    var releaseInProgress by remember { mutableStateOf(false) }
    var managementReleased by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun removeThisApp() {
        runCatching {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun runRelease() {
        if (releaseInProgress) return
        releaseInProgress = true
        scope.launch {
            runCatching { repository.reportReleaseComplete() }
            managementReleased = runCatching { policyController.releaseManagementForPaidLoan() }.getOrDefault(false)
            releaseInProgress = false
        }
    }

    var securityReport by remember { mutableStateOf<SecurityChecker.SecurityReport?>(null) }

    LaunchedEffect(Unit) {
        val report = SecurityChecker.runAllChecks(context)
        securityReport = report
        if (report.shouldLock) {
            policyController.enforceLock(state.account?.securityPolicy?.frpAccountIds.orEmpty())
        }
    }

    LaunchedEffect(state.account?.securityPolicy, state.releaseApproved, isRegistered) {
        if (isRegistered && !state.releaseApproved) {
            policyController.applyBaseLoanSecurity(state.account?.securityPolicy?.frpAccountIds.orEmpty())
        }
    }

    LaunchedEffect(state.releaseApproved) {
        if (state.releaseApproved && !managementReleased) {
            runRelease()
        }
    }

    LaunchedEffect(isRegistered, state.account?.id, state.account?.isStolen, state.releaseApproved) {
        val account = state.account ?: return@LaunchedEffect
        if (!isRegistered || state.releaseApproved) {
            TrackingService.stop(context)
            return@LaunchedEffect
        }
        if (account.isStolen) {
            TrackingService.start(context, account.id)
        } else {
            TrackingService.stop(context)
        }
    }

    LaunchedEffect(state.isLocked, state.releaseApproved) {
        if (state.releaseApproved) return@LaunchedEffect
        val nowLocked = state.isLocked
        if (nowLocked != lastEnforcedLocked) {
            if (nowLocked) {
                policyController.enforceLock(state.account?.securityPolicy?.frpAccountIds.orEmpty())
                onLocked()
            } else {
                policyController.releaseRestrictions()
            }
            lastEnforcedLocked = nowLocked
        }
    }

    if (state.releaseApproved) {
        ReleaseApprovedScreen(
            account = state.account,
            isReleasing = releaseInProgress,
            managementReleased = managementReleased,
            onRemoveApp = ::removeThisApp,
            onRefresh = { runRelease() }
        )
        return
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
                onCheckUpdates = { navController.navigate(Screen.Updates.route) },
                securityReport = securityReport
            )
        }

        composable(Screen.Payments.route) {
            PaymentsScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Updates.route) {
            UpdateScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
