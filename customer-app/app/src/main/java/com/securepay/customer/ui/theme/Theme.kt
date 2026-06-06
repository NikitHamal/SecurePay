package com.securepay.customer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// SecurePay is a permanently dark, charcoal-based experience regardless of system setting.
private val SecurePayDarkColors = darkColorScheme(
    primary = Emerald,
    onPrimary = OnAccent,
    primaryContainer = EmeraldDim,
    onPrimaryContainer = TextPrimary,
    secondary = Amber,
    onSecondary = OnAccent,
    error = Crimson,
    onError = TextPrimary,
    errorContainer = CrimsonDim,
    onErrorContainer = TextPrimary,
    background = Charcoal,
    onBackground = TextPrimary,
    surface = CharcoalElevated,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = TextSecondary
)

@Composable
fun SecurePayTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = SecurePayDarkColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Charcoal.toArgb()
            window.navigationBarColor = Charcoal.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SecurePayTypography,
        content = content
    )
}
