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

/**
 * Series colours. This exact ordering was run through the data-viz palette
 * validator against the #131C2B chart surface and clears every gate: the
 * lightness band, the chroma floor, adjacent colour-vision-deficiency
 * separation, the normal-vision floor, and 3:1 contrast against the surface.
 * Do not re-order or substitute without re-validating.
 */
val SeriesWater = Color(0xFF3987E5)     // slot 1 — blue
val SeriesCalIn = Color(0xFFD95926)     // slot 2 — orange
val SeriesCalOut = Color(0xFF199E70)    // slot 3 — aqua
val SeriesWeight = Color(0xFF9085E9)    // slot 4 — violet
val SeriesDistance = SeriesWater

// Status — reserved, never reused as a series colour.
val StatusGood = Color(0xFF0CA30C)
val StatusWarning = Color(0xFFFAB219)
val StatusCritical = Color(0xFFD03B3B)

val GridLine = Color(0x14FFFFFF)
val AxisLine = Color(0x2EFFFFFF)
