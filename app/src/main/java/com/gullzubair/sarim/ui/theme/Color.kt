package com.gullzubair.sarim.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Material 3 Expressive palette.
 *
 * Seeded from the brand emerald (hue ~160) with a teal secondary and a golden tertiary, laid out as
 * M3 tonal palettes (tone 10/20/30/40/80/90) so every role pairs with an accessible "on" colour.
 * Neutrals carry a faint green tint so surfaces feel part of the brand instead of flat grey.
 * The legacy token names are kept so existing screens keep compiling; their values now map onto
 * the tonal system.
 */

// ── Primary · Emerald ───────────────────────────────────────────────────────────────────────
val EmeraldDark = Color(0xFF72DBB0)              // primary (dark)  · tone 80
val EmeraldBright = Color(0xFF00704F)            // primary (light) · tone 40
val EmeraldDeep = Color(0xFF005138)              // primaryContainer (dark) · tone 30
val EmeraldLight = Color(0xFF00704F)             // success / accent text on light surfaces
val EmeraldLightContainer = Color(0xFF8FF8CB)    // primaryContainer (light) · tone 90
val OnEmeraldLight = Color(0xFFFFFFFF)
val OnEmeraldDark = Color(0xFF003826)
val OnEmeraldContainerLight = Color(0xFF002114)
val OnEmeraldContainerDark = Color(0xFF8FF8CB)
val EmeraldSoftPillLight = Color(0xFFC6EFDC)     // quiet badge / selected-chip fill
val EmeraldSoftPillDark = Color(0x3372DBB0)

// ── Secondary · Teal / Cyan ─────────────────────────────────────────────────────────────────
val CyanDark = Color(0xFF4FD8EB)
val CyanLight = Color(0xFF006874)
val SecondaryContainerLight = Color(0xFFCFE9EE)
val SecondaryContainerDark = Color(0xFF1F4D54)
val OnSecondaryContainerLight = Color(0xFF001F24)
val OnSecondaryContainerDark = Color(0xFFCFE9EE)

// ── Tertiary · Gold ─────────────────────────────────────────────────────────────────────────
val GoldDark = Color(0xFFF5BD3D)
val GoldLight = Color(0xFF7A5900)
val TertiaryContainerLight = Color(0xFFFFDF9E)
val TertiaryContainerDark = Color(0xFF5C4200)
val OnTertiaryContainerLight = Color(0xFF261A00)
val OnTertiaryContainerDark = Color(0xFFFFDF9E)

// ── Data accent · Violet (charts / badges only) ─────────────────────────────────────────────
val PurpleDark = Color(0xFFD0BCFF)
val PurpleLight = Color(0xFF6750A4)

// ── Error / danger ──────────────────────────────────────────────────────────────────────────
val DangerDark = Color(0xFFFFB4AB)
val DangerLight = Color(0xFFBA1A1A)
val ErrorContainerLight = Color(0xFFFFDAD6)
val ErrorContainerDark = Color(0xFF93000A)

// ── Call-to-action fills (kept for legacy call sites; buttons now use solid tonal colour) ───
val CtaGradientStart = Color(0xFF72DBB0)
val CtaGradientStartLight = Color(0xFF00704F)
val CtaGradientMid = Color(0xFF8FF8CB)
val CtaGradientEnd = Color(0xFFA6F2CF)
val CtaTextDark = Color(0xFF002114)

val DangerGradientStart = Color(0xFFFFB4AB)
val DangerGradientStartLight = Color(0xFFBA1A1A)
val DangerGradientEnd = Color(0xFFF5BD3D)
val DangerGradientEndLight = Color(0xFFD97706)

// ── Neutrals · Dark (tonal, green-tinted) ───────────────────────────────────────────────────
val DarkBackground = Color(0xFF0F1512)
val DarkSurface = Color(0xFF0F1512)
val DarkSurfaceVariant = Color(0xFF252B28)        // input fill / quiet container
val DarkSurfaceElevated = Color(0xFF1B211E)       // card container
val DarkSurfaceContainerLowest = Color(0xFF0A0F0D)
val DarkSurfaceContainerLow = Color(0xFF171D1A)
val DarkSurfaceContainer = Color(0xFF1B211E)
val DarkSurfaceContainerHigh = Color(0xFF252B28)
val DarkSurfaceContainerHighest = Color(0xFF303633)
val DarkSurfaceBright = Color(0xFF353B38)
val DarkOutline = Color(0xFF89938C)
val DarkOutlineVariant = Color(0xFF3F4943)
val DarkTextPrimary = Color(0xFFDEE4DE)
val DarkTextSecondary = Color(0xFFBFC9C2)

// ── Neutrals · Light (tonal, green-tinted) ──────────────────────────────────────────────────
val LightBackground = Color(0xFFF5FBF6)
val LightSurface = Color(0xFFF5FBF6)
val LightSurfaceVariant = Color(0xFFE3EAE4)       // input fill / quiet container
val LightSurfaceElevated = Color(0xFFEBF2EC)      // card container
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFEFF5F0)
val LightSurfaceContainer = Color(0xFFE9F0EA)
val LightSurfaceContainerHigh = Color(0xFFE3EAE4)
val LightSurfaceContainerHighest = Color(0xFFDEE4DE)
val LightSurfaceDim = Color(0xFFD5DBD6)
val LightOutline = Color(0xFF6F7973)
val LightOutlineVariant = Color(0xFFBFC9C2)
val LightTextPrimary = Color(0xFF171D1A)
val LightTextSecondary = Color(0xFF3F4943)
val LightGlassTop = Color(0xFFEFF5F0)
val LightGlassBottom = Color(0xFFEFF5F0)
val LightHeaderFrosted = Color(0xFFE9F0EA)
