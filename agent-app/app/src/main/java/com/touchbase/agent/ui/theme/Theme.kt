package com.touchbase.agent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context

private val TBDark = darkColorScheme(
    primary = Gold,
    onPrimary = DeepCharcoal,
    primaryContainer = GoldDim,
    onPrimaryContainer = OnDarkPrimary,
    secondary = GoldBright,
    onSecondary = DeepCharcoal,
    tertiary = Amber,
    onTertiary = DeepCharcoal,
    error = VividCrimson,
    onError = OnDarkPrimary,
    background = DeepCharcoal,
    onBackground = OnDarkPrimary,
    surface = ElevatedSurface,
    onSurface = OnDarkPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnDarkSecondary,
    outline = Line
)

private val TBLight = lightColorScheme(
    primary = GoldLight,
    onPrimary = LightSurface,
    primaryContainer = SoftGrayInput,
    onPrimaryContainer = Ink,
    secondary = Gold,
    onSecondary = Ink,
    tertiary = Amber,
    onTertiary = Ink,
    error = VividCrimson,
    onError = LightSurface,
    background = LightBg,
    onBackground = Ink,
    surface = LightSurface,
    onSurface = Ink,
    surfaceVariant = SoftGrayInput,
    onSurfaceVariant = Muted,
    outline = DividerLight
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
    val colorScheme = if (darkTheme) TBDark else TBLight
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
