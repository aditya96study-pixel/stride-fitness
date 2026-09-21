package com.aditya.stride.ui.theme

import androidx.compose.ui.graphics.Color

// Surfaces — a cool, near-black navy so the accent colours stay calm.
val Ink900 = Color(0xFF0B1220)   // app background
val Ink800 = Color(0xFF131C2B)   // cards and the chart surface
val Ink700 = Color(0xFF1C2637)   // raised / pressed
val Ink600 = Color(0xFF2A364A)   // borders

val TextPrimary = Color(0xFFF1F5F9)
val TextSecondary = Color(0xFF9FB0C7)
val TextMuted = Color(0xFF6B7C93)

// Light surfaces, mirroring the dark ones.
val Paper50 = Color(0xFFF7F9FC)   // app background
val Paper00 = Color(0xFFFFFFFF)   // cards and the chart surface
val Paper100 = Color(0xFFEDF1F7)  // raised / pressed
val Paper200 = Color(0xFFD3DCE8)  // borders

val TextPrimaryLight = Color(0xFF0B1220)
val TextSecondaryLight = Color(0xFF4A5A72)

/**
 * Series colours, in two sets.
 *
 * Every colour that shares a chart with another was checked against the surface it is
 * drawn on — #131C2B in dark mode, #FFFFFF in light — for five things: an OKLCH
 * lightness band, a chroma floor, separation under normal vision, separation under
 * simulated deuteranopia, protanopia and tritanopia, and 3:1 contrast against the
 * surface. Do not substitute one without re-running that check: the light set is not a
 * mechanical lightening of the dark set, because the pairs that stay distinguishable
 * differ between the two backgrounds.
 *
 * Pick a set with [chartSeriesColors] rather than referring to either directly, so a
 * screen cannot end up drawing dark-mode colours on a light card.
 */
object SeriesDark {
    val water = Color(0xFF3987E5)
    val calIn = Color(0xFFD95926)
    val calOut = Color(0xFF199E70)
    val weight = Color(0xFF9085E9)

    // Run, walk and treadmill all appear in one chart, so all three pairs were checked.
    val run = Color(0xFF4A9CF1)
    val walk = Color(0xFF9FB83C)
    val treadmill = Color(0xFFCC5A82)
}

object SeriesLight {
    val water = Color(0xFF1C66C4)
    val calIn = Color(0xFF9C3A11)
    val calOut = Color(0xFF009362)
    val weight = Color(0xFF5B4BC4)

    val run = Color(0xFF0067D1)
    val walk = Color(0xFF556600)
    val treadmill = Color(0xFFC93F76)
}

/** Series colours for one theme, so screens never hardcode a set. */
data class SeriesPalette(
    val water: Color,
    val calIn: Color,
    val calOut: Color,
    val weight: Color,
    val run: Color,
    val walk: Color,
    val treadmill: Color,
    val grid: Color,
    val axis: Color,
) {
    val distance: Color get() = water
}

val DarkSeriesPalette = SeriesPalette(
    water = SeriesDark.water,
    calIn = SeriesDark.calIn,
    calOut = SeriesDark.calOut,
    weight = SeriesDark.weight,
    run = SeriesDark.run,
    walk = SeriesDark.walk,
    treadmill = SeriesDark.treadmill,
    grid = Color(0x14FFFFFF),
    axis = Color(0x2EFFFFFF),
)

val LightSeriesPalette = SeriesPalette(
    water = SeriesLight.water,
    calIn = SeriesLight.calIn,
    calOut = SeriesLight.calOut,
    weight = SeriesLight.weight,
    run = SeriesLight.run,
    walk = SeriesLight.walk,
    treadmill = SeriesLight.treadmill,
    grid = Color(0x14000000),
    axis = Color(0x2E000000),
)

// Status — reserved, never reused as a series colour.
val StatusGood = Color(0xFF0CA30C)
val StatusWarning = Color(0xFFFAB219)
val StatusCritical = Color(0xFFD03B3B)
