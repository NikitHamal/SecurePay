package com.touchbase.agent.ui.theme

import androidx.compose.ui.graphics.Color

// Touch Base design-system palette.
// Brand = warm gold (taken from the TB logo). Status/semantic colors kept for clarity.
val Gold = Color(0xFFD4AF37)          // primary brand gold (logo)
val GoldBright = Color(0xFFE9C969)    // pressed/highlight
val GoldDim = Color(0xFF8C701E)       // dark container for gold

val DeepCharcoal = Color(0xFF0F0F10)
val ElevatedSurface = Color(0xFF1A1A1C)
val SurfaceVariant = Color(0xFF262629)
val Line = Color(0xFF2C2C2F)

val GoldGreen = Color(0xFF10B981)     // kept only for positive/success (paid, in-stock)
val VividCrimson = Color(0xFFDC2626)
val Amber = Color(0xFFF59E0B)
val OnDarkPrimary = Color(0xFFF2F2F3)
val OnDarkSecondary = Color(0xFFA1A1AA)

// Light mode — surfaces warm white, text ink, gold accent.
val Ink = Color(0xFF0B0B0C)
val InkSoft = Color(0xFF27272A)
val Muted = Color(0xFF6B7280)
val LightBg = Color(0xFFF8F6F0)       // warm cream paper to complement gold
val LightSurface = Color(0xFFFFFFFF)
val SoftGrayInput = Color(0xFFF1EFE8)
val DividerLight = Color(0xFFE7E4DA)
val GoldLight = Color(0xFFB8941F)

@Deprecated("Use Gold", ReplaceWith("Gold"))
val EmeraldGreen = Color(0xFF10B981)

@Deprecated("Use Gold", ReplaceWith("Gold"))
val ForestGreen = Color(0xFF004B30)

@Deprecated("Use LightBg", ReplaceWith("LightBg"))
val SoftMint = Color(0xFFEAF5EE)

fun Color.isLight(): Boolean =
    0.2126f * red + 0.7152f * green + 0.0722f * blue > 0.5f
