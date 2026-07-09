package com.touchbase.agent.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.touchbase.agent.ui.settings.SettingsScreen
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.touchbase.agent.data.remote.SecurePayRepository
import com.touchbase.agent.data.local.WifiSettingsStore
import com.touchbase.agent.data.remote.TokenManager
import com.touchbase.agent.ui.auth.LoginScreen
import com.touchbase.agent.ui.auth.RegisterScreen
import com.touchbase.agent.ui.dashboard.DashboardScreen
import com.touchbase.agent.ui.enrollment.EnrollmentWizardScreen
import com.touchbase.agent.ui.inventory.InventoryScreen
import com.touchbase.agent.ui.ledger.LedgerScreen
import com.touchbase.agent.ui.customers.CustomerDetailScreen
import com.touchbase.agent.ui.customers.CustomersScreen
import com.touchbase.agent.ui.provisioning.ProvisioningScreen
import com.touchbase.agent.ui.provisioning.ProvisioningViewModel
import com.touchbase.agent.ui.tracking.TrackingMapScreen
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun SecurePayNavHost(
    navController: NavHostController,
    repository: SecurePayRepository,
    tokenManager: TokenManager
) {
    val isLoggedIn by tokenManager.token.collectAsState()
    val appContext = LocalContext.current.applicationContext
    val wifiSettingsStore = remember(appContext) { WifiSettingsStore(appContext) }

    val startDestination = if (isLoggedIn != null) Screen.Dashboard.route else Screen.Login.route

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { fadeIn(animationSpec = tween(300)) },
        popExitTransition = { fadeOut(animationSpec = tween(300)) }
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                repository = repository,
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onRegisterClick = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                repository = repository,
                onBackToLogin = { navController.popBackStack() }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                repository = repository,
                onNavigateToCustomers = { navigateToTab(Screen.Customers.route) },
                onNavigateToEnrollment = { navController.navigate(Screen.Enrollment.route) },
                onNavigateToInventory = { navigateToTab(Screen.Inventory.route) },
                onNavigateToLedger = { navigateToTab(Screen.Ledger.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            val scope = rememberCoroutineScope()
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    scope.launch {
                        repository.logout()
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.Customers.route) {
            CustomersScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onNavigateToHome = { navigateToTab(Screen.Dashboard.route) },
                onNavigateToInventory = { navigateToTab(Screen.Inventory.route) },
                onNavigateToLedger = { navigateToTab(Screen.Ledger.route) },
                onCustomerClick = { accountId ->
                    navController.navigate(Screen.CustomerDetail.createRoute(accountId))
                }
            )
        }

        composable(
            route = Screen.CustomerDetail.route,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId") ?: return@composable
            CustomerDetailScreen(
                accountId = accountId,
                repository = repository,
                onBack = { navController.popBackStack() },
                onProvisionDevice = { imei ->
                    navController.navigate(Screen.Provisioning.createRoute(imei))
                },
                onViewLiveLocation = { accountId ->
                    navController.navigate(Screen.TrackingMap.createRoute(accountId))
                }
            )
        }

        composable(
            route = Screen.TrackingMap.route,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType })
        ) { backStackEntry ->
            val accountId = backStackEntry.arguments?.getString("accountId") ?: return@composable
            TrackingMapScreen(
                accountId = accountId,
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Enrollment.route) {
            EnrollmentWizardScreen(
                repository = repository,
                onComplete = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
                onProvisionDevice = { imei ->
                    navController.navigate(Screen.Provisioning.createRoute(imei))
                }
            )
        }

        composable(
            route = Screen.Provisioning.route,
            arguments = listOf(navArgument("imei") {
                type = NavType.StringType
                defaultValue = ""
                nullable = true
            })
        ) { backStackEntry ->
            val imei = backStackEntry.arguments?.getString("imei").orEmpty()
            val viewModel: ProvisioningViewModel = viewModel(
                factory = ProvisioningViewModel.Factory(repository, imei, wifiSettingsStore)
            )
            ProvisioningScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() }
            )
        }

        composable(Screen.Inventory.route) {
            InventoryScreen(
                repository = repository,
                onNavigateToHome = { navigateToTab(Screen.Dashboard.route) },
                onNavigateToCustomers = { navigateToTab(Screen.Customers.route) },
                onNavigateToLedger = { navigateToTab(Screen.Ledger.route) }
            )
        }

        composable(Screen.Ledger.route) {
            LedgerScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onNavigateToHome = { navigateToTab(Screen.Dashboard.route) },
                onNavigateToCustomers = { navigateToTab(Screen.Customers.route) },
                onNavigateToInventory = { navigateToTab(Screen.Inventory.route) }
            )
        }
    }
}
