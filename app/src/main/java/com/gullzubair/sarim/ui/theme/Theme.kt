package com.gullzubair.sarim.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Immutable
data class SarimAccents(
    val emerald: Color,
    val cyan: Color,
    val gold: Color,
    val purple: Color,
    val danger: Color,
    val cardElevated: Color,
    val borderSubtle: Color,
    val textMuted: Color
)

val LocalSarimAccents = staticCompositionLocalOf {
    SarimAccents(
        emerald = EmeraldDark,
        cyan = CyanDark,
        gold = GoldDark,
        purple = PurpleDark,
        danger = DangerDark,
        cardElevated = DarkSurfaceElevated,
        borderSubtle = DarkOutline,
        textMuted = DarkTextSecondary
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldDark,
    onPrimary = Color(0xFF03170B),
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = EmeraldDark,
    secondary = CyanDark,
    onSecondary = Color(0xFF031C22),
    secondaryContainer = Color(0xFF0B2E36),
    onSecondaryContainer = CyanDark,
    tertiary = GoldDark,
    onTertiary = Color(0xFF261900),
    tertiaryContainer = Color(0xFF332304),
    onTertiaryContainer = GoldDark,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    error = DangerDark,
    onError = Color(0xFF2B0606)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldLight,
    onPrimary = Color.White,
    primaryContainer = EmeraldLightContainer,
    onPrimaryContainer = Color(0xFF023D28),
    secondary = CyanLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF073B47),
    tertiary = GoldLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF452400),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    error = DangerLight,
    onError = Color.White
)

private val SarimShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun SarimTheme(
    themeMode: String = "dark",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val accents = if (isDark) {
        SarimAccents(
            emerald = EmeraldDark,
            cyan = CyanDark,
            gold = GoldDark,
            purple = PurpleDark,
            danger = DangerDark,
            cardElevated = DarkSurfaceElevated,
            borderSubtle = DarkOutline,
            textMuted = DarkTextSecondary
        )
    } else {
        SarimAccents(
            emerald = EmeraldLight,
            cyan = CyanLight,
            gold = GoldLight,
            purple = PurpleLight,
            danger = DangerLight,
            cardElevated = LightSurfaceElevated,
            borderSubtle = LightOutline,
            textMuted = LightTextSecondary
        )
    }

    CompositionLocalProvider(LocalSarimAccents provides accents) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SarimTypography,
            shapes = SarimShapes,
            content = content
        )
    }
}
