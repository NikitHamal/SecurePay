package com.touchbase.user.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import com.touchbase.user.ui.payments.PayWithMoMoScreen
import com.touchbase.user.ui.recovery.RecoveryLoginScreen
import com.touchbase.user.ui.more.MoreScreen
import com.touchbase.user.ui.more.HelpScreen
import com.touchbase.user.ui.update.UpdateScreen
import com.touchbase.user.ui.release.ReleaseApprovedScreen
import com.touchbase.user.ui.account.AccountScreen
import com.touchbase.user.ui.provisioning.LockProScreen
import com.touchbase.user.ui.kiosk.KioskManager
import com.touchbase.user.util.DevicePower
import com.touchbase.user.util.SecureLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.touchbase.user.worker.TrackingService

private const val TAG = "SecurePayApp"

@Composable
fun SecurePayApp(
    repository: DeviceRepository,
    policyController: DevicePolicyController,
    onLocked: () -> Unit = {},
    skipLockPro: Boolean = false
) {
    val context = LocalContext.current
    val isRegistered by repository.isRegistered.collectAsState()

    val navController = rememberNavController()
    val deviceViewModel: DeviceViewModel = viewModel(
        factory = DeviceViewModel.Factory(repository)
    )
    val provisioningToken = remember(context) {
        ProvisioningExtrasStore.provisioningToken(context)
    }
    val expectedImei = remember(context) {
        ProvisioningExtrasStore.expectedImei(context)
    }
    val hasDealerActivation = !provisioningToken.isNullOrBlank() && !expectedImei.isNullOrBlank()
    val startDestination = when {
        isRegistered -> Screen.Dashboard.route
        skipLockPro -> if (hasDealerActivation) Screen.Activation.route else Screen.RecoveryLogin.route
        else -> Screen.LockPro.route
    }
    val activationViewModel: ActivationViewModel = viewModel(
        factory = ActivationViewModel.Factory(repository, provisioningToken, expectedImei)
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

    LaunchedEffect(isRegistered) {
        if (isRegistered) {
            runCatching {
                val act = context as? android.app.Activity
                if (act != null) act.stopLockTask()
            }
            KioskManager.restoreLauncher(context)
        }
    }

    fun removeThisApp() {
        scope.launch {
            SecureLog.i(TAG, "removeThisApp: start")
            // Release lock-task pinning so the system uninstaller can open.
            // Without this, ACTION_DELETE silently fails on a locked/kiosk device.
            runCatching { (context as? android.app.Activity)?.stopLockTask() }
            delay(300)
            // If the device is still under management, remove admin/owner first;
            // Android blocks uninstalling an active device-admin package.
            SecureLog.i(TAG, "removeThisApp: releasing management (adminActive=${policyController.isAdminActive}, owner=${policyController.isDeviceOwnerApp})")
            runCatching { policyController.releaseManagementForPaidLoan() }
            delay(300)

            val packageName = context.packageName
            SecureLog.i(TAG, "removeThisApp: firing uninstall for $packageName")

            // Preferred: explicit uninstall intent.
            val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launchedUninstall = runCatching {
                context.startActivity(uninstallIntent)
                true
            }.onFailure { SecureLog.w(TAG, "ACTION_UNINSTALL_PACKAGE failed", it) }
                .getOrDefault(false)
            if (launchedUninstall) {
                SecureLog.i(TAG, "removeThisApp: launched ACTION_UNINSTALL_PACKAGE")
                return@launch
            }

            // Fallback: legacy delete intent.
            val deleteIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launchedDelete = runCatching {
                context.startActivity(deleteIntent)
                true
            }.onFailure { SecureLog.w(TAG, "ACTION_DELETE failed", it) }
                .getOrDefault(false)
            if (launchedDelete) {
                SecureLog.i(TAG, "removeThisApp: launched ACTION_DELETE")
                return@launch
            }

            // Last resort: open this app's page in Settings so the user can
            // reach the Uninstall button manually.
            SecureLog.w(TAG, "removeThisApp: both uninstall intents failed, opening app-info")
            runCatching {
                val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                context.startActivity(details)
            }.onFailure { SecureLog.e(TAG, "removeThisApp: app-info failed", it) }
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
            onUninstall = ::removeThisApp,
            onRefresh = { runRelease() }
        )
        return
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.LockPro.route) {
            LockProScreen(
                onGetStarted = {
                    val target = if (hasDealerActivation) Screen.Activation.route else Screen.RecoveryLogin.route
                    navController.navigate(target) {
                        popUpTo(Screen.LockPro.route) { inclusive = true }
                    }
                },
                onPowerOff = { DevicePower.powerOff() }
            )
        }

        composable(Screen.Activation.route) {
            ActivationScreen(
                viewModel = activationViewModel,
                onActivated = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Activation.route) { inclusive = true }
                    }
                },
                onUseCustomerLogin = { navController.navigate(Screen.RecoveryLogin.route) }
            )
        }

        composable(Screen.RecoveryLogin.route) {
            RecoveryLoginScreen(
                repository = repository,
                expectedImei = expectedImei,
                onRecovered = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    }
                },
                onUseActivationCode = { navController.navigate(Screen.Activation.route) },
                onHelp = { navController.navigate(Screen.Help.route) }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                state = state,
                onRefresh = deviceViewModel::refreshStatus,
                onMessageShown = deviceViewModel::consumeMessage,
                onViewPayments = { navController.navigate(Screen.Payments.route) },
                onPayNow = { navController.navigate(Screen.PayWithMoMo.route) },
                onCheckUpdates = { navController.navigate(Screen.Updates.route) },
                onMore = { navController.navigate(Screen.More.route) },
                onAccount = { navController.navigate(Screen.Account.route) },
                securityReport = securityReport
            )
        }

        composable(Screen.PayWithMoMo.route) {
            PayWithMoMoScreen(
                repository = repository,
                account = state.account,
                onBack = { navController.popBackStack() },
                onPaid = {
                    deviceViewModel.refreshStatus()
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Payments.route) {
            PaymentsScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onPayNow = { navController.navigate(Screen.PayWithMoMo.route) }
            )
        }

        composable(Screen.More.route) {
            MoreScreen(
                account = state.account,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onPayments = { navController.navigate(Screen.Payments.route) },
                onHelp = { navController.navigate(Screen.Help.route) },
                onCheckUpdates = { navController.navigate(Screen.Updates.route) },
                onAccount = { navController.navigate(Screen.Account.route) }
            )
        }

        composable(Screen.Help.route) {
            HelpScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Updates.route) {
            UpdateScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Account.route) {
            AccountScreen(
                account = state.account,
                onBack = { navController.popBackStack() },
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onPayments = { navController.navigate(Screen.Payments.route) },
                onMore = { navController.navigate(Screen.More.route) }
            )
        }
    }
}
