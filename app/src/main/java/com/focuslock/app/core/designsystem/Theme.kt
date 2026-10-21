package com.focuslock.app.core.designsystem

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

data class FocusLockColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color,
    val isDark: Boolean
)

val DarkThemeColors = FocusLockColors(
    background = DarkBackground,
    surface = DarkSurface,
    surfaceElevated = DarkSurfaceElevated,
    border = DarkBorder,
    borderSubtle = DarkBorderSubtle,
    textPrimary = Neutral50,
    textSecondary = Neutral400,
    textMuted = Neutral600,
    primary = BrandCyan,
    isDark = true
)

val LightThemeColors = FocusLockColors(
    background = LightBackground,
    surface = LightSurface,
    surfaceElevated = LightSurfaceElevated,
    border = LightBorder,
    borderSubtle = Color(0xFFCBD5E1),
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textMuted = LightTextMuted,
    primary = BrandIndigo,
    isDark = false
)

val LocalFocusLockColors = staticCompositionLocalOf { DarkThemeColors }

object AppTheme {
    val colors: FocusLockColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFocusLockColors.current
}

private val DarkColorScheme = darkColorScheme(
    primary = BrandIndigoLight,
    onPrimary = Neutral950,
    primaryContainer = Neutral850,
    onPrimaryContainer = Neutral50,
    secondary = BrandTealLight,
    onSecondary = Neutral950,
    background = Neutral950,
    onBackground = Neutral50,
    surface = Neutral900,
    onSurface = Neutral50,
    surfaceVariant = Neutral850,
    onSurfaceVariant = Neutral400,
    outline = Neutral800,
    outlineVariant = Neutral700,
    error = DangerRedLight,
    onError = Neutral950
)

private val LightColorScheme = lightColorScheme(
    primary = BrandIndigo,
    onPrimary = LightSurface,
    primaryContainer = LightSurfaceElevated,
    onPrimaryContainer = LightTextPrimary,
    secondary = BrandTeal,
    onSecondary = LightSurface,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = Neutral200,
    error = DangerRed,
    onError = LightSurface
)

@Composable
fun FocusLockTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val colors = if (darkTheme) DarkThemeColors else LightThemeColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                val insetsController = WindowCompat.getInsetsController(it, view)
                @Suppress("DEPRECATION")
                it.statusBarColor = colorScheme.background.toArgb()
                @Suppress("DEPRECATION")
                it.navigationBarColor = colorScheme.background.toArgb()
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalFocusLockColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FocusLockTypography,
            content = content
        )
    }
}
