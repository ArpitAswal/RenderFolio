package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.constant.AppConstants

val LocalBorderColor = staticCompositionLocalOf { AppConstants.DarkBorder }

private val DarkColorScheme = darkColorScheme(
    primary = DarkThemePrimary,
    onPrimary = Color.White,
    primaryContainer = DarkThemePrimary.copy(alpha = 0.2f),
    onPrimaryContainer = DarkThemeTextPrimary,
    background = DarkThemeBackground,
    onBackground = DarkThemeTextPrimary,
    surface = DarkThemeSurface,
    onSurface = DarkThemeTextPrimary,
    surfaceVariant = DarkThemeSurface,
    onSurfaceVariant = DarkThemeTextMuted,
    outline = DarkThemeTextMuted,
    outlineVariant = DarkThemeBorder
)

private val LightColorScheme = lightColorScheme(
    primary = LightThemePrimary,
    onPrimary = Color.White,
    primaryContainer = LightThemePrimary.copy(alpha = 0.12f),
    onPrimaryContainer = LightThemePrimary,
    background = LightThemeBackground,
    onBackground = LightThemeTextPrimary,
    surface = LightThemeSurface,
    onSurface = LightThemeTextPrimary,
    surfaceVariant = LightThemeSurface,
    onSurfaceVariant = LightThemeTextMuted,
    outline = LightThemeTextMuted,
    outlineVariant = LightThemeBorder
)

@Composable
fun RenderFolioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val borderColor = if (darkTheme) DarkThemeBorder else LightThemeBorder

    CompositionLocalProvider(LocalBorderColor provides borderColor) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun ResumeShowcaseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) = RenderFolioTheme(darkTheme = darkTheme, content = content)
