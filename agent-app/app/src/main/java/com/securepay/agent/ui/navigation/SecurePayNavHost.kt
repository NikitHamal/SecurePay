package com.securepay.agent.ui.navigation

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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.securepay.agent.data.remote.SecurePayRepository
import com.securepay.agent.data.remote.TokenManager
import com.securepay.agent.ui.auth.LoginScreen
import com.securepay.agent.ui.dashboard.DashboardScreen
import com.securepay.agent.ui.enrollment.EnrollmentWizardScreen
import com.securepay.agent.ui.inventory.InventoryScreen
import com.securepay.agent.ui.ledger.LedgerScreen
import com.securepay.agent.ui.customers.CustomerDetailScreen
import com.securepay.agent.ui.customers.CustomersScreen

@Composable
fun SecurePayNavHost(
    navController: NavHostController,
    repository: SecurePayRepository,
    tokenManager: TokenManager
) {
    val isLoggedIn by tokenManager.token.collectAsState()

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
                }
            )
        }

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                repository = repository,
                onNavigateToCustomers = { navigateToTab(Screen.Customers.route) },
                onNavigateToEnrollment = { navController.navigate(Screen.Enrollment.route) },
                onNavigateToInventory = { navigateToTab(Screen.Inventory.route) },
                onNavigateToLedger = { navigateToTab(Screen.Ledger.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
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
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Enrollment.route) {
            EnrollmentWizardScreen(
                repository = repository,
                onComplete = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
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