package com.securepay.customer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * SecurePay always renders with its dark, charcoal-based scheme regardless of
 * the system setting — the financing brand identity is intentionally dark.
 */
private val SecurePayDarkColorScheme = darkColorScheme(
    primary = PrimaryEmerald,
    onPrimary = BackgroundCharcoal,
    primaryContainer = PrimaryContainerEmerald,
    onPrimaryContainer = OnBackgroundText,

    secondary = WarningAmber,
    onSecondary = BackgroundCharcoal,

    background = BackgroundCharcoal,
    onBackground = OnBackgroundText,

    surface = SurfaceDark,
    onSurface = OnBackgroundText,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = SecondaryText,

    error = ErrorCrimson,
    onError = OnBackgroundText,
    errorContainer = ErrorContainerCrimson,
    onErrorContainer = OnBackgroundText
)

@Composable
fun SecurePayTheme(
    // Parameter accepted for API symmetry; SecurePay forces the dark scheme.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SecurePayDarkColorScheme,
        typography = SecurePayTypography,
        content = content
    )
}
