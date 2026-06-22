package com.touchbase.agent.ui.navigation

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
    data object Provisioning : Screen("provisioning?imei={imei}") {
        fun createRoute(imei: String = "") = "provisioning?imei=$imei"
    }
    data object Settings : Screen("settings")
}
