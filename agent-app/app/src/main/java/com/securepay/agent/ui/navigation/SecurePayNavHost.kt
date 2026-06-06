package com.securepay.agent.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(initialOffsetX = { it / 3 }) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 3 }) }
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
                onNavigateToCustomers = { navController.navigate(Screen.Customers.route) },
                onNavigateToEnrollment = { navController.navigate(Screen.Enrollment.route) },
                onNavigateToInventory = { navController.navigate(Screen.Inventory.route) },
                onNavigateToLedger = { navController.navigate(Screen.Ledger.route) },
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
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Ledger.route) {
            LedgerScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}