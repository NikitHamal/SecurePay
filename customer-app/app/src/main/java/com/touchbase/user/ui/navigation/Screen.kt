package com.touchbase.user.ui.navigation

sealed class Screen(val route: String) {
    data object Activation : Screen("activation")
    data object RecoveryLogin : Screen("recovery-login")
    data object Dashboard : Screen("dashboard")
    data object Payments : Screen("payments")
    data object More : Screen("more")
    data object Help : Screen("help")
    data object Updates : Screen("updates")
    data object Provisioning : Screen("provisioning")
    data object PayWithMoMo : Screen("pay-momo")
}
