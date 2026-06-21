package com.touchbase.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
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

private val SecurePayLightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = LightSurface,
    primaryContainer = SoftMint,
    onPrimaryContainer = ForestGreen,
    secondary = Amber,
    onSecondary = TextDark,
    error = VividCrimson,
    onError = LightSurface,
    background = LightBg,
    onBackground = TextDark,
    surface = LightSurface,
    onSurface = TextDark,
    surfaceVariant = SoftGrayInput,
    onSurfaceVariant = TextGray,
    outline = TextGray.copy(alpha = 0.5f)
)

@Composable
fun SecurePayAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        SecurePayDarkColorScheme
    } else {
        SecurePayLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
