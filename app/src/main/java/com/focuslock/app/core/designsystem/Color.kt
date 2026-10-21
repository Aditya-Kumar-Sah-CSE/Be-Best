package com.focuslock.app.core.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Deep Midnight Navy Palette (Primary Background & Surfaces matching reference)
val DarkBackground = Color(0xFF0B0F1D)
val DarkSurface = Color(0xFF13192B)
val DarkSurfaceElevated = Color(0xFF1A2238)
val DarkBorder = Color(0xFF222D4B)
val DarkBorderSubtle = Color(0xFF1B243D)

// Neutral Palette - Dark Mode
val Neutral950 = DarkBackground
val Neutral900 = DarkSurface
val Neutral850 = DarkSurfaceElevated
val Neutral800 = DarkBorder
val Neutral700 = Color(0xFF2E3A5A)
val Neutral600 = Color(0xFF475569)
val Neutral400 = Color(0xFF94A3B8) // Secondary text
val Neutral200 = Color(0xFFCBD5E1) // Secondary high
val Neutral50  = Color(0xFFF8FAFC) // Primary text

// Neutral Palette - Light Mode
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFF1F5F9)
val LightBorder = Color(0xFFE2E8F0)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)
val LightTextMuted = Color(0xFF64748B)

// Brand Vibrant Accents (Cyan, Blue, Indigo, Violet)
val BrandCyan = Color(0xFF38BDF8)
val BrandBlue = Color(0xFF3B82F6)
val BrandIndigo = Color(0xFF6366F1)
val BrandIndigoLight = Color(0xFF818CF8)
val BrandIndigoDark = Color(0xFF4F46E5)
val BrandViolet = Color(0xFF8B5CF6)
val BrandPurple = Color(0xFFA855F7)
val BrandTeal = Color(0xFF10B981)
val BrandTealLight = Color(0xFF34D399)

// Semantic State Colors
val SuccessGreen = Color(0xFF10B981)
val SuccessGreenLight = Color(0xFF34D399)
val WarningAmber = Color(0xFFF59E0B)
val WarningAmberLight = Color(0xFFFBBF24)
val DangerRed = Color(0xFFF43F5E)
val DangerRedLight = Color(0xFFFB7185)
val InfoBlue = Color(0xFF38BDF8)
val InfoBlueLight = Color(0xFF60A5FA)

// Signature Gradients
val PrimaryGradient = Brush.horizontalGradient(
    colors = listOf(BrandCyan, BrandIndigo, BrandViolet)
)

val PrimaryGradientVertical = Brush.verticalGradient(
    colors = listOf(BrandCyan, BrandIndigo, BrandViolet)
)

val AccentGlowGradient = Brush.radialGradient(
    colors = listOf(BrandIndigo.copy(alpha = 0.45f), Color.Transparent)
)

val DangerGlowGradient = Brush.radialGradient(
    colors = listOf(DangerRed.copy(alpha = 0.35f), Color.Transparent)
)

val CardGlowBorderBrush = Brush.linearGradient(
    colors = listOf(BrandCyan.copy(alpha = 0.6f), BrandIndigo.copy(alpha = 0.3f), Color.Transparent)
)

// Legacy aliases for backward compatibility
val AmoledBackground = DarkBackground
val SurfaceDark = DarkSurface
val SurfaceCard = DarkSurfaceElevated
val SurfaceCardBorder = DarkBorder
val CyanAccent = BrandCyan
val PurpleAccent = BrandViolet
val TextPrimary = Neutral50
val TextSecondary = Neutral400
val TextMuted = Neutral600
val SuccessEmerald = SuccessGreen
