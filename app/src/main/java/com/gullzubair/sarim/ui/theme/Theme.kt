package com.gullzubair.sarim.ui.theme

import android.app.Activity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/** Material 3 Expressive spring physics presets for tactile spatial & effect transitions. */
object SarimMotion {
    val SpatialSpring: SpringSpec<Float> = spring(
        dampingRatio = 0.72f,
        stiffness = 380f
    )
    val SpatialDpSpring: SpringSpec<Dp> = spring(
        dampingRatio = 0.76f,
        stiffness = 380f
    )
    val EffectsSpring: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 500f
    )
    val ColorSpring: SpringSpec<Color> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 460f
    )
}

@Immutable
data class SarimAccents(
    val emerald: Color,
    val cyan: Color,
    val gold: Color,
    val purple: Color,
    val danger: Color,
    val cardElevated: Color,
    val borderSubtle: Color,
    val textMuted: Color,
    val isDark: Boolean = false,
    val glassTop: Color = LightGlassTop,
    val glassBottom: Color = LightGlassBottom,
    val inputFill: Color = LightSurfaceVariant,
    val inputBorder: Color = LightOutline,
    val headerFrosted: Color = LightHeaderFrosted,
    val softPillFill: Color = EmeraldLightContainer,
    val cardBorderWidth: Dp = 1.dp
)

val LocalSarimAccents = staticCompositionLocalOf {
    SarimAccents(
        emerald = EmeraldBright,
        cyan = CyanLight,
        gold = GoldLight,
        purple = PurpleLight,
        danger = DangerLight,
        cardElevated = LightSurfaceElevated,
        borderSubtle = LightOutlineVariant,
        textMuted = LightTextSecondary,
        isDark = false,
        glassTop = LightGlassTop,
        glassBottom = LightGlassBottom,
        inputFill = LightSurfaceVariant,
        inputBorder = LightOutlineVariant,
        headerFrosted = LightHeaderFrosted,
        softPillFill = EmeraldLightContainer,
        cardBorderWidth = 1.dp
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldDark,
    onPrimary = Color(0xFF022011),
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = EmeraldDark,
    secondary = CyanDark,
    onSecondary = Color(0xFF03201E),
    secondaryContainer = Color(0xFF0B3532),
    onSecondaryContainer = CyanDark,
    tertiary = GoldDark,
    onTertiary = Color(0xFF281900),
    tertiaryContainer = Color(0xFF3A2705),
    onTertiaryContainer = GoldDark,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkSurfaceLowest,
    surfaceContainerLow = DarkSurfaceLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceHigh,
    surfaceContainerHighest = DarkSurfaceHighest,
    surfaceBright = DarkSurfaceHigh,
    surfaceDim = DarkBackground,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DangerDark,
    onError = Color(0xFF310707),
    errorContainer = Color(0xFF4C1111),
    onErrorContainer = Color(0xFFFEE2E2)
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldBright,
    onPrimary = CtaTextDark,
    primaryContainer = EmeraldLightContainer,
    onPrimaryContainer = EmeraldOnLightContainer,
    secondary = CyanLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F7FA),
    onSecondaryContainer = Color(0xFF053944),
    tertiary = GoldLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF0C7),
    onTertiaryContainer = Color(0xFF452400),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurfaceLowest,
    surfaceContainerLow = LightSurfaceLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerHighest = LightSurfaceHighest,
    surfaceBright = LightSurfaceLowest,
    surfaceDim = LightSurfaceHigh,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = DangerLight,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF5C0C0C)
)

private val SarimShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SarimTheme(
    themeMode: String = "light",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
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
            borderSubtle = DarkOutlineVariant,
            textMuted = DarkTextSecondary,
            isDark = true,
            glassTop = DarkSurfaceContainer,
            glassBottom = DarkSurfaceLow,
            inputFill = DarkSurfaceHigh,
            inputBorder = DarkOutline,
            headerFrosted = DarkSurfaceLow,
            softPillFill = Color(0x2E4ADE80),
            cardBorderWidth = 1.dp
        )
    } else {
        SarimAccents(
            emerald = EmeraldBright,
            cyan = CyanLight,
            gold = GoldLight,
            purple = PurpleLight,
            danger = DangerLight,
            cardElevated = LightSurfaceElevated,
            borderSubtle = LightOutlineVariant,
            textMuted = LightTextSecondary,
            isDark = false,
            glassTop = LightGlassTop,
            glassBottom = LightGlassBottom,
            inputFill = LightSurfaceVariant,
            inputBorder = LightOutlineVariant,
            headerFrosted = LightHeaderFrosted,
            softPillFill = EmeraldLightContainer,
            cardBorderWidth = 1.dp
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
