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

/**
 * App-specific semantic colours that sit alongside the Material colour scheme.
 * Surfaces and outlines are mapped onto the tonal container roles so custom components
 * (cards, headers, inputs, chips) stay in step with Material components.
 */
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
    val inputBorder: Color = LightOutlineVariant,
    val headerFrosted: Color = LightHeaderFrosted,
    val softPillFill: Color = EmeraldSoftPillLight,
    val cardBorderWidth: Dp = 0.dp
)

private val LightAccents = SarimAccents(
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
    softPillFill = EmeraldSoftPillLight,
    cardBorderWidth = 0.dp
)

private val DarkAccents = SarimAccents(
    emerald = EmeraldDark,
    cyan = CyanDark,
    gold = GoldDark,
    purple = PurpleDark,
    danger = DangerDark,
    cardElevated = DarkSurfaceElevated,
    borderSubtle = DarkOutlineVariant,
    textMuted = DarkTextSecondary,
    isDark = true,
    glassTop = DarkSurfaceContainerLow,
    glassBottom = DarkSurfaceContainerLow,
    inputFill = DarkSurfaceVariant,
    inputBorder = DarkOutlineVariant,
    headerFrosted = DarkSurfaceContainer,
    softPillFill = EmeraldSoftPillDark,
    cardBorderWidth = 0.dp
)

val LocalSarimAccents = staticCompositionLocalOf { LightAccents }

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldDark,
    onPrimary = OnEmeraldDark,
    primaryContainer = EmeraldDeep,
    onPrimaryContainer = OnEmeraldContainerDark,
    inversePrimary = EmeraldBright,
    secondary = CyanDark,
    onSecondary = Color(0xFF00363D),
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = GoldDark,
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkOutlineVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = EmeraldDark,
    inverseSurface = DarkTextPrimary,
    inverseOnSurface = Color(0xFF2C322F),
    error = DangerDark,
    onError = Color(0xFF690005),
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorContainerLight,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = Color(0xFF000000),
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurface,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest
)

private val LightColorScheme = lightColorScheme(
    primary = EmeraldBright,
    onPrimary = OnEmeraldLight,
    primaryContainer = EmeraldLightContainer,
    onPrimaryContainer = OnEmeraldContainerLight,
    inversePrimary = EmeraldDark,
    secondary = CyanLight,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = GoldLight,
    onTertiary = Color.White,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = EmeraldBright,
    inverseSurface = Color(0xFF2C322F),
    inverseOnSurface = Color(0xFFECF2ED),
    error = DangerLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF410002),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = Color(0xFF000000),
    surfaceBright = LightSurface,
    surfaceDim = LightSurfaceDim,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest
)

/**
 * Expressive shape scale: noticeably rounder corners across the board. Cards sit on `medium`,
 * sheets / dialogs / drawers on `large`, and hero surfaces on `extraLarge`.
 */
private val SarimShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp)
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
    val accents = if (isDark) DarkAccents else LightAccents

    CompositionLocalProvider(LocalSarimAccents provides accents) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = SarimShapes,
            typography = SarimTypography,
            content = content
        )
    }
}
