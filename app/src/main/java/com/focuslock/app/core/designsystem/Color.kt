package com.focuslock.app.core.designsystem

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// 3D Deep AMOLED Palette
val AmoledBackground = Color(0xFF07070A)
val SurfaceDark = Color(0xFF10121A)
val SurfaceCard = Color(0xFF181B26)
val SurfaceCardElevated = Color(0xFF222634)
val SurfaceCardBorder = Color(0xFF2E3347)

// Iridescent Cyberpunk & Fluid Accent Colors
val CyanAccent = Color(0xFF38BDF8)
val NeonCyan = Color(0xFF00F5FF)
val PurpleAccent = Color(0xFF7C3AED)
val VioletGlow = Color(0xFFA78BFA)
val FluidTeal = Color(0xFF2DD4BF)

// Functional Colors
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

val DangerRed = Color(0xFFF43F5E)
val WarningAmber = Color(0xFFF59E0B)
val SuccessEmerald = Color(0xFF10B981)

// Gradients
val IridescentPebbleBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF38BDF8),
        Color(0xFF6366F1),
        Color(0xFF7C3AED)
    )
)

val GlowPebbleBrush = Brush.radialGradient(
    colors = listOf(
        Color(0x6638BDF8),
        Color(0x337C3AED),
        Color(0x00000000)
    )
)

val CardGlowBorder = Brush.linearGradient(
    colors = listOf(
        Color(0x4D38BDF8),
        Color(0x267C3AED),
        Color(0x1AFFFFFF)
    )
)
