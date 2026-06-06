package com.securepay.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SecurePayDarkColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    onPrimary = DeepCharcoal,
    primaryContainer = EmeraldGreen,
    onPrimaryContainer = DeepCharcoal,
    secondary = Amber,
    onSecondary = DeepCharcoal,
    error = VividCrimson,
    onError = OnDarkPrimary,
    background = DeepCharcoal,
    onBackground = OnDarkPrimary,
    surface = ElevatedSurface,
    onSurface = OnDarkPrimary,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = OnDarkSecondary,
    outline = OnDarkSecondary
)

@Composable
fun SecurePayAgentTheme(
    // The product ships dark-only to match the shared SecurePay design system.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SecurePayDarkColorScheme,
        typography = Typography,
        content = content
    )
}
