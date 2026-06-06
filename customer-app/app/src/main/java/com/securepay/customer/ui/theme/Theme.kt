package com.securepay.customer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = EmeraldActive,
    onPrimary = DeepCharcoal,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = SoftText,
    secondary = AmberWarning,
    error = VividCrimson,
    errorContainer = CrimsonContainer,
    background = DeepCharcoal,
    onBackground = SoftText,
    surface = CharcoalSurface,
    onSurface = SoftText,
    surfaceVariant = CharcoalVariant,
    onSurfaceVariant = SoftText
)

private val LightScheme = lightColorScheme(
    primary = EmeraldActive,
    onPrimary = DeepCharcoal,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = SoftText,
    secondary = AmberWarning,
    error = VividCrimson,
    errorContainer = CrimsonContainer,
    background = DeepCharcoal,
    onBackground = SoftText,
    surface = CharcoalSurface,
    onSurface = SoftText,
    surfaceVariant = CharcoalVariant,
    onSurfaceVariant = SoftText
)

@Composable
fun SecurePayCustomerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
