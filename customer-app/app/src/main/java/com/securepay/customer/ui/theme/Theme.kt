package com.securepay.customer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SecurePayColorScheme = darkColorScheme(
    primary = EmeraldGreen,
    onPrimary = DeepCharcoal,
    secondary = SignalAmber,
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
fun SecurePayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SecurePayColorScheme,
        typography = SecurePayTypography,
        content = content
    )
}

