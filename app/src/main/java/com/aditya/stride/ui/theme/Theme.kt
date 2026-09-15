package com.aditya.stride.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = SeriesCalOut,
    onPrimary = Color(0xFF00150C),
    primaryContainer = Color(0xFF0F3A2A),
    onPrimaryContainer = Color(0xFFB8F1DA),
    secondary = SeriesWater,
    onSecondary = Color(0xFF001428),
    tertiary = SeriesWeight,
    onTertiary = Color(0xFF16112E),
    background = Ink900,
    onBackground = TextPrimary,
    surface = Ink900,
    onSurface = TextPrimary,
    surfaceVariant = Ink800,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Ink800,
    surfaceContainerHigh = Ink700,
    surfaceContainerHighest = Ink700,
    outline = Ink600,
    outlineVariant = Color(0xFF223047),
    error = StatusCritical,
    onError = Color(0xFF2A0606),
)

// The app is designed dark-first; the light scheme exists so that a phone set to
// light mode is not jarring, and follows the same validated hues.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF13704F),
    onPrimary = Color.White,
    secondary = Color(0xFF2A78D6),
    tertiary = Color(0xFF4A3AA7),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF0B1220),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF4A5A72),
    surfaceContainer = Color(0xFFFFFFFF),
    outline = Color(0xFFD3DCE8),
    error = Color(0xFFD03B3B),
)

@Composable
fun StrideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = StrideTypography,
        content = content,
    )
}

/** True when the app is showing its dark palette — charts use it to pick ink. */
@Composable
fun isDark(): Boolean = MaterialTheme.colorScheme.background == Ink900
