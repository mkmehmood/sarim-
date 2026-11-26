package com.gullzubair.sarim.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.R

val CinzelFont = FontFamily(Font(R.font.cinzel, FontWeight.SemiBold))
val PlayfairFont = FontFamily(Font(R.font.playfair_display, FontWeight.SemiBold))
val JakartaFont = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
val ManropeFont = FontFamily(Font(R.font.manrope, FontWeight.Medium))
val JetBrainsMonoFont = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Medium))

val SarimTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = CinzelFont,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = PlayfairFont,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp
    ),
    displaySmall = TextStyle(
        fontFamily = CinzelFont,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.2.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = PlayfairFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlayfairFont,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = JakartaFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp
    ),
    titleLarge = TextStyle(
        fontFamily = JakartaFont,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.15).sp
    ),
    titleMedium = TextStyle(
        fontFamily = JakartaFont,
        fontWeight = FontWeight.Bold,
        fontSize = 15.5.sp,
        lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontFamily = ManropeFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = ManropeFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = ManropeFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = ManropeFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = JakartaFont,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum"
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFont,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        fontFeatureSettings = "tnum"
    )
)
