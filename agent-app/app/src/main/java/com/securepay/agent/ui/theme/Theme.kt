package com.securepay.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SecurePayDark = darkColorScheme(
    primary = EmeraldActive,
    onPrimary = DeepCharcoal,
    primaryContainer = EmeraldContainer,
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

private val SecurePayLight = lightColorScheme(
    primary = EmeraldActive,
    onPrimary = DeepCharcoal,
    primaryContainer = EmeraldContainer,
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
fun SecurePayAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) SecurePayDark else SecurePayLight,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
