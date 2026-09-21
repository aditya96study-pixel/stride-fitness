package com.aditya.stride.ui.theme

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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.aditya.stride.data.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = SeriesDark.calOut,
    onPrimary = Color(0xFF00150C),
    primaryContainer = Color(0xFF0F3A2A),
    onPrimaryContainer = Color(0xFFB8F1DA),
    secondary = SeriesDark.water,
    onSecondary = Color(0xFF001428),
    tertiary = SeriesDark.weight,
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

// The app is designed dark-first. Light mode is a supported choice rather than a
// fallback, so its chart colours were validated against the light card surface in their
// own right — see SeriesPalette.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF13704F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCEDDE),
    onPrimaryContainer = Color(0xFF00301F),
    secondary = SeriesLight.water,
    onSecondary = Color.White,
    tertiary = SeriesLight.weight,
    onTertiary = Color.White,
    background = Paper50,
    onBackground = TextPrimaryLight,
    surface = Paper50,
    onSurface = TextPrimaryLight,
    surfaceVariant = Paper00,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = Paper00,
    surfaceContainerHigh = Paper100,
    surfaceContainerHighest = Paper100,
    outline = Paper200,
    outlineVariant = Color(0xFFE2E8F0),
    error = StatusCritical,
    onError = Color.White,
)

/**
 * The chart palette for whichever scheme is in force. A composition local rather than a
 * pair of globals so no screen can draw dark-mode series onto a light card.
 */
val LocalSeriesPalette = staticCompositionLocalOf { DarkSeriesPalette }

/**
 * Series and chart-ink colours for the current theme. A composable property rather than a
 * function so a screen can write `seriesPalette.water` inline, the way it reads
 * `MaterialTheme.colorScheme`.
 */
val seriesPalette: SeriesPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalSeriesPalette.current

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
    CompositionLocalProvider(
        LocalSeriesPalette provides if (darkTheme) DarkSeriesPalette else LightSeriesPalette
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = StrideTypography,
            content = content,
        )
    }
}

/** True when the app is showing its dark palette — charts use it to pick ink. */
@Composable
@ReadOnlyComposable
fun isDark(): Boolean = MaterialTheme.colorScheme.background == Ink900

/** Resolves the user's choice against the system setting. */
@Composable
fun ThemeMode.isDarkTheme(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
