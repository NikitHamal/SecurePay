package com.securepay.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SecurePayDarkColorScheme = darkColorScheme(
    primary = Emerald,
    onPrimary = OnPrimaryDark,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = SecondaryGray,
    onSecondary = DeepCharcoal,
    background = DeepCharcoal,
    onBackground = OnBackgroundLight,
    surface = SurfaceDark,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = SecondaryGray,
    error = Crimson,
    onError = OnBackgroundLight,
    tertiary = Amber,
    onTertiary = DeepCharcoal,
    outline = SecondaryGray
)

@Composable
fun SecurePayAgentTheme(
    // The app is dark-only by design, but the parameter keeps the API conventional.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SecurePayDarkColorScheme,
        typography = Typography,
        content = content
    )
}
