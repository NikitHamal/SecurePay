package com.securepay.agent.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")
    data object Customers : Screen("customers")
    data object CustomerDetail : Screen("customers/{accountId}") {
        fun createRoute(accountId: String) = "customers/$accountId"
    }
    data object Enrollment : Screen("enrollment")
    data object Inventory : Screen("inventory")
    data object Ledger : Screen("ledger")
}