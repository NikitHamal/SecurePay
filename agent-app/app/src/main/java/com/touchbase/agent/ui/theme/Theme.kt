package com.touchbase.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Context

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

object ThemeManager {
    var themeMode by mutableStateOf("system")
    private var sharedPrefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        sharedPrefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        themeMode = sharedPrefs?.getString("theme_mode", "system") ?: "system"
    }

    fun updateTheme(mode: String) {
        themeMode = mode
        sharedPrefs?.edit()?.putString("theme_mode", mode)?.apply()
    }
}

@Composable
fun SecurePayAgentTheme(
    darkTheme: Boolean = when (ThemeManager.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    },
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
