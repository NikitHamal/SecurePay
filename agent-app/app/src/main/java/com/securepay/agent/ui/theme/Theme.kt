package com.securepay.agent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AgentColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    onPrimary = DeepCharcoal,
    secondary = SoftCyan,
    onSecondary = DeepCharcoal,
    error = VividCrimson,
    onError = OffWhite,
    background = DeepCharcoal,
    onBackground = OffWhite,
    surface = CharcoalSurface,
    onSurface = OffWhite,
    surfaceVariant = CharcoalSurfaceHigh,
    onSurfaceVariant = MutedText
)

@Composable
fun SecurePayAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AgentColorScheme,
        typography = Typography(),
        content = content
    )
}

