package com.aditya.stride.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val sans = FontFamily.SansSerif

val StrideTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Light,
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp, lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = sans, fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp, lineHeight = 13.sp, letterSpacing = 0.4.sp,
    ),
)
