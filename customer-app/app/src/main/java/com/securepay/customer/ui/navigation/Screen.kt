package com.securepay.customer.ui.navigation

sealed class Screen(val route: String) {
    data object Activation : Screen("activation")
    data object Dashboard : Screen("dashboard")
    data object Payments : Screen("payments")
}