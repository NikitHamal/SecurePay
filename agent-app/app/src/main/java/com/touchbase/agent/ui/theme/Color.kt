package com.touchbase.agent.ui.theme

import androidx.compose.ui.graphics.Color

// Shared SecurePay design-system palette (M3, dark).
val DeepCharcoal = Color(0xFF121212)
val ElevatedSurface = Color(0xFF1E1E1E)
val EmeraldGreen = Color(0xFF10B981)
val VividCrimson = Color(0xFFDC2626)
val Amber = Color(0xFFF59E0B)
val OnDarkPrimary = Color(0xFFE5E7EB)
val OnDarkSecondary = Color(0xFF9CA3AF)

// Light Mode palette (from reference image styling)
val ForestGreen = Color(0xFF004B30)
val SoftMint = Color(0xFFEAF5EE)
val LightBg = Color(0xFFF6FAF7)
val LightSurface = Color(0xFFFFFFFF)
val SoftGrayInput = Color(0xFFF3F4F6)
val TextDark = Color(0xFF111827)
val TextGray = Color(0xFF6B7280)

fun Color.isLight(): Boolean =
    0.2126f * red + 0.7152f * green + 0.0722f * blue > 0.5f
