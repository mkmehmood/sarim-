package com.gullzubair.sarim.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.theme.CinzelFont
import com.gullzubair.sarim.ui.theme.CtaGradientEnd
import com.gullzubair.sarim.ui.theme.CtaGradientEndLight
import com.gullzubair.sarim.ui.theme.CtaGradientStart
import com.gullzubair.sarim.ui.theme.CtaGradientStartLight
import com.gullzubair.sarim.ui.theme.CtaTextDark
import com.gullzubair.sarim.ui.theme.DangerGradientEnd
import com.gullzubair.sarim.ui.theme.DangerGradientEndLight
import com.gullzubair.sarim.ui.theme.DangerGradientStart
import com.gullzubair.sarim.ui.theme.DangerGradientStartLight
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.theme.PlayfairFont
import com.gullzubair.sarim.ui.theme.SarimMotion
import com.gullzubair.sarim.ui.viewmodel.ToastEvent
import com.gullzubair.sarim.ui.viewmodel.ToastKind
import com.gullzubair.sarim.util.FeedbackHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

enum class SheetSurfaceTone {
    DEFAULT,
    GOOD,
    WARN_STRIPES,
    INFO_DOTS
}

fun Modifier.dashedBorder(
    width: Dp = 1.2.dp,
    color: Color,
    cornerRadius: Dp = 18.dp,
    dashLength: Dp = 5.dp,
    gapLength: Dp = 4.dp
): Modifier = this.drawBehind {
    val strokePx = width.toPx()
    val dashPx = dashLength.toPx()
    val gapPx = gapLength.toPx()
    val radiusPx = cornerRadius.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(strokePx / 2f, strokePx / 2f),
        size = Size(size.width - strokePx, size.height - strokePx),
        cornerRadius = CornerRadius(radiusPx, radiusPx),
        style = Stroke(
            width = strokePx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, gapPx), 0f)
        )
    )
}

fun Modifier.diagonalWarningStripes(
    stripeColor: Color,
    glowColor: Color
): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = 0.20f), Color.Transparent),
            center = Offset(size.width * 0.92f, size.height * 0.12f),
            radius = maxOf(size.width, size.height) * 0.72f
        )
    )
    clipRect {
        val step = 14.dp.toPx()
        val stroke = 6.dp.toPx()
        val diagonalSpan = size.height
        var x = -diagonalSpan
        while (x < size.width + diagonalSpan) {
            drawLine(
                color = stripeColor.copy(alpha = 0.085f),
                start = Offset(x, size.height),
                end = Offset(x + diagonalSpan, 0f),
                strokeWidth = stroke
            )
            x += step
        }
    }
}

fun Modifier.cyanDotMatrixPattern(
    dotColor: Color,
    glowColor: Color
): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = 0.20f), Color.Transparent),
            center = Offset(size.width * 0.92f, size.height * 0.14f),
            radius = maxOf(size.width, size.height) * 0.75f
        )
    )
    clipRect {
        val spacing = 12.dp.toPx()
        val dotRadius = 1.1.dp.toPx()
        val tint = dotColor.copy(alpha = 0.22f)
        var y = spacing * 0.5f
        while (y < size.height) {
            var x = spacing * 0.5f
            while (x < size.width) {
                drawCircle(color = tint, radius = dotRadius, center = Offset(x, y))
                x += spacing
            }
            y += spacing
        }
    }
}

fun Modifier.emeraldGoodGlow(glowColor: Color): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = 0.22f), Color.Transparent),
            center = Offset(size.width * 0.92f, size.height * 0.12f),
            radius = maxOf(size.width, size.height) * 0.72f
        )
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width * 0.08f, size.height * 0.92f),
            radius = maxOf(size.width, size.height) * 0.65f
        )
    )
}

/** Expressive squircle tonal icon badge used across headers, KPI cards, and action rows. */
@Composable
fun SarimIconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = tint.copy(alpha = 0.14f),
    size: Dp = 36.dp,
    cornerRadius: Dp = 12.dp,
    iconSize: Dp = 18.dp,
    contentDescription: String? = null
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, tint.copy(alpha = 0.22f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/** Expressive spring press feedback modifier for tactile Material 3 interaction. */
fun Modifier.expressivePressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.968f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = SarimMotion.SpatialSpring,
        label = "expressive_press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Pure editorial serif heading matching Sarim web `.tab-heading`. */
@Composable
fun TabHeading(title: String, modifier: Modifier = Modifier) {
    val accents = LocalSarimAccents.current
    Text(
        text = title,
        fontFamily = PlayfairFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 25.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.5).sp,
        color = accents.emerald,
        modifier = modifier
    )
}

@Composable
fun SarimCard(
    modifier: Modifier = Modifier,
    accentBorder: Color? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val accents = LocalSarimAccents.current
    val shape = RoundedCornerShape(22.dp)
    val bgBrush = remember(accents.glassTop, accents.glassBottom) {
        Brush.verticalGradient(listOf(accents.glassTop, accents.glassBottom))
    }
    val cyanWash = accents.cyan.copy(alpha = if (accents.isDark) 0.16f else 0.15f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (accents.isDark) 8.dp else 7.dp,
                shape = shape,
                ambientColor = accents.cyan.copy(alpha = if (accents.isDark) 0.24f else 0.18f),
                spotColor = accents.cyan.copy(alpha = if (accents.isDark) 0.28f else 0.16f)
            )
            .clip(shape)
            .background(bgBrush)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(cyanWash, Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = maxOf(size.width, size.height) * 0.72f
                    )
                )
            }
            .border(accents.cardBorderWidth, accentBorder ?: accents.borderSubtle, shape)
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun SarimSubpageHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    badgeText: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val accents = LocalSarimAccents.current
    val shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (accents.isDark) 10.dp else 8.dp,
                shape = shape,
                ambientColor = accents.cyan.copy(alpha = if (accents.isDark) 0.22f else 0.16f),
                spotColor = accents.cyan.copy(alpha = if (accents.isDark) 0.24f else 0.16f)
            ),
        color = accents.headerFrosted,
        shape = shape,
        border = BorderStroke(1.dp, accents.borderSubtle.copy(alpha = 0.85f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("nav_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = title.uppercase(),
                        fontFamily = CinzelFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        letterSpacing = 0.7.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!badgeText.isNullOrBlank()) {
                        Surface(
                            color = accents.softPillFill,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = badgeText.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = accents.emerald,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = accents.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailingContent?.invoke()
        }
    }
}

@Composable
fun SarimGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
    leadingIcon: ImageVector? = null
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(999.dp)
    val ctaAccents = LocalSarimAccents.current
    val interactionSource = remember { MutableInteractionSource() }
    val brush = if (enabled) {
        if (ctaAccents.isDark) {
            Brush.horizontalGradient(listOf(CtaGradientStart, CtaGradientEnd))
        } else {
            Brush.horizontalGradient(listOf(CtaGradientStartLight, CtaGradientEndLight))
        }
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )
    }
    val textColor = if (!enabled) ctaAccents.textMuted.copy(alpha = 0.65f) else CtaTextDark
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .height(48.dp)
            .expressivePressScale(interactionSource)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .shadow(
                elevation = if (enabled) 10.dp else 0.dp,
                shape = shape,
                ambientColor = ctaAccents.cyan.copy(alpha = 0.36f),
                spotColor = ctaAccents.emerald.copy(alpha = 0.36f)
            )
            .clip(shape)
            .background(brush)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = textColor),
                enabled = enabled
            ) {
                FeedbackHelper.triggerHaptic(context)
                FeedbackHelper.triggerSound(context)
                onClick()
            }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = text,
                fontFamily = JakartaFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                letterSpacing = 0.1.sp,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SarimDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
    leadingIcon: ImageVector? = null
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(999.dp)
    val dangerDark = LocalSarimAccents.current.isDark
    val interactionSource = remember { MutableInteractionSource() }
    val brush = if (enabled) {
        Brush.horizontalGradient(
            listOf(
                if (dangerDark) DangerGradientStart else DangerGradientStartLight,
                if (dangerDark) DangerGradientEnd else DangerGradientEndLight
            )
        )
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFE7B8B5), Color(0xFFEBC1B4)))
    }
    val textColor = if (!enabled) Color.White.copy(alpha = 0.8f) else CtaTextDark
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .height(48.dp)
            .expressivePressScale(interactionSource)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .shadow(if (enabled) 10.dp else 0.dp, shape, ambientColor = Color(0x55F97316), spotColor = Color(0x55EF4444))
            .clip(shape)
            .background(brush)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = textColor),
                enabled = enabled
            ) {
                FeedbackHelper.triggerHaptic(context)
                FeedbackHelper.triggerSound(context)
                onClick()
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = text,
                fontFamily = JakartaFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SarimPillInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    label: String? = null,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val accents = LocalSarimAccents.current
    Column(modifier = modifier) {
        if (!label.isNullOrBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                color = accents.textMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            placeholder = if (placeholder.isNotBlank()) {
                { Text(placeholder, color = accents.textMuted.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else null,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            singleLine = singleLine,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = accents.inputFill,
                unfocusedContainerColor = accents.inputFill,
                disabledContainerColor = accents.inputFill.copy(alpha = 0.6f),
                focusedBorderColor = accents.emerald,
                unfocusedBorderColor = accents.inputBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        )
    }
}

@Composable
fun SarimHalfRingKpiBox(
    label: String,
    value: String,
    ringColor: Color,
    modifier: Modifier = Modifier,
    labelColor: Color = LocalSarimAccents.current.textMuted
) {
    val accents = LocalSarimAccents.current
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (accents.isDark) Color(0xFF020403) else accents.inputFill)
            .border(1.dp, accents.borderSubtle, shape)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = 2.5.dp.toPx()
            val arcDiameter = size.height
            drawArc(
                color = ringColor,
                startAngle = 90f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(strokePx / 2f, strokePx / 2f),
                size = Size(arcDiameter - strokePx, size.height - strokePx),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Column(modifier = Modifier.padding(start = 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, letterSpacing = 0.6.sp),
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ringColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SarimNoteBanner(text: String, modifier: Modifier = Modifier) {
    val accents = LocalSarimAccents.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (accents.isDark) Color(0xFF231B0E) else Color(0xFFFFFBF2),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accents.gold.copy(alpha = 0.7f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
fun MetricKpiCard(
    label: String,
    value: String,
    subtitle: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    val accents = LocalSarimAccents.current
    SarimCard(
        modifier = modifier,
        contentPadding = PaddingValues(15.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = accents.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (icon != null) {
                SarimIconBadge(
                    icon = icon,
                    tint = accent,
                    size = 28.dp,
                    cornerRadius = 9.dp,
                    iconSize = 15.dp,
                    contentDescription = label
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = accents.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, action: @Composable (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalSarimAccents.current.textMuted)
            }
        }
        action?.invoke()
    }
}

@Composable
fun DatePickerField(
    label: String,
    dateIso: String,
    onDateSelected: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val accents = LocalSarimAccents.current
    val borderColor by animateColorAsState(
        targetValue = if (open) accents.emerald else accents.inputBorder,
        animationSpec = SarimMotion.ColorSpring,
        label = "date_picker_border"
    )
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                color = accents.textMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(18.dp),
            color = accents.inputFill,
            border = BorderStroke(if (open) 1.5.dp else 1.dp, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .testTag(testTag)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateUtils.formatPretty(dateIso).uppercase(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Rounded.CalendarToday,
                    contentDescription = "Pick $label",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    if (open) {
        SarimCalendarDialog(
            selectedDateIso = dateIso,
            onDismiss = { open = false },
            onSelectDate = { picked ->
                onDateSelected(picked)
                open = false
            },
            confirmTestTag = "${testTag}_confirm"
        )
    }
}

@Composable
fun DateRangePickerField(
    label: String,
    fromIso: String,
    toIso: String,
    onRangeSelected: (from: String, to: String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    val accents = LocalSarimAccents.current
    val borderColor by animateColorAsState(
        targetValue = if (open) accents.emerald else accents.inputBorder,
        animationSpec = SarimMotion.ColorSpring,
        label = "date_range_border"
    )
    val summaryText = remember(fromIso, toIso) {
        val f = DateUtils.parseLocalDate(fromIso)
        val t = DateUtils.parseLocalDate(toIso)
        if (f != null && t != null) {
            val fMon = f.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            val tMon = t.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            if (f.year == t.year) {
                "${f.dayOfMonth} $fMon to ${t.dayOfMonth} $tMon ${t.year}"
            } else {
                "${f.dayOfMonth} $fMon ${f.year} to ${t.dayOfMonth} $tMon ${t.year}"
            }
        } else {
            "$fromIso to $toIso"
        }
    }
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                color = accents.textMuted,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(18.dp),
            color = accents.inputFill,
            border = BorderStroke(if (open) 1.5.dp else 1.dp, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .testTag(testTag)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Rounded.CalendarToday,
                    contentDescription = "Pick $label",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    if (open) {
        SarimCalendarRangeDialog(
            initialFromIso = fromIso,
            initialToIso = toIso,
            onDismiss = { open = false },
            onApply = { f, t ->
                onRangeSelected(f, t)
                open = false
            },
            confirmTestTag = "${testTag}_confirm"
        )
    }
}

@Composable
private fun SarimCalendarDialog(
    selectedDateIso: String,
    onDismiss: () -> Unit,
    onSelectDate: (String) -> Unit,
    confirmTestTag: String
) {
    val accents = LocalSarimAccents.current
    val today = remember { LocalDate.now() }
    val initialDate = remember(selectedDateIso) { DateUtils.parseLocalDate(selectedDateIso) ?: today }
    var displayedYm by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var showMonthYearPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        SarimCard(contentPadding = PaddingValues(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { displayedYm = displayedYm.minusMonths(1) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    text = "${displayedYm.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${displayedYm.year}",
                    fontFamily = JakartaFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showMonthYearPicker = !showMonthYearPicker }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                IconButton(
                    onClick = { displayedYm = displayedYm.plusMonths(1) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            if (showMonthYearPicker) {
                Spacer(Modifier.height(8.dp))
                var pickerYear by remember(displayedYm) { mutableIntStateOf(displayedYm.year) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { pickerYear -= 1 }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous year")
                    }
                    Text(
                        pickerYear.toString(),
                        fontFamily = JakartaFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    IconButton(onClick = { pickerYear += 1 }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next year")
                    }
                }
                val months = remember { java.time.Month.entries }
                months.chunked(4).forEach { rowMonths ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowMonths.forEach { m ->
                            val active = m == displayedYm.month && pickerYear == displayedYm.year
                            Surface(
                                onClick = {
                                    displayedYm = YearMonth.of(pickerYear, m)
                                    showMonthYearPicker = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (active) accents.emerald else accents.inputFill,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 3.dp)
                            ) {
                                Text(
                                    text = m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                                    textAlign = TextAlign.Center,
                                    fontFamily = JakartaFont,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = if (active) CtaTextDark else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { wd ->
                        Text(
                            text = wd,
                            textAlign = TextAlign.Center,
                            fontFamily = JakartaFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = accents.textMuted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                val firstOfMonth = displayedYm.atDay(1)
                val leadingBlanks = firstOfMonth.dayOfWeek.value % 7 // Sun=0, Mon=1 ... Sat=6
                val daysInMonth = displayedYm.lengthOfMonth()
                val cells = buildList<LocalDate?> {
                    repeat(leadingBlanks) { add(null) }
                    for (d in 1..daysInMonth) add(displayedYm.atDay(d))
                    while (size % 7 != 0) add(null)
                }
                cells.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        week.forEach { day ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (day != null) {
                                    val isSelected = day == initialDate
                                    val isToday = day == today
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) accents.emerald else Color.Transparent)
                                            .clickable { onSelectDate(day.toString()) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = day.dayOfMonth.toString(),
                                                fontFamily = JakartaFont,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = if (isSelected) CtaTextDark else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isToday) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) CtaTextDark else accents.emerald)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onSelectDate(today.toString()) }) {
                    Text("Today", color = accents.emerald, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = { onSelectDate(initialDate.toString()) },
                    modifier = Modifier.testTag(confirmTestTag)
                ) {
                    Text("Done", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun SarimCalendarRangeDialog(
    initialFromIso: String,
    initialToIso: String,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit,
    confirmTestTag: String
) {
    val accents = LocalSarimAccents.current
    val today = remember { LocalDate.now() }
    var startDate by remember(initialFromIso) { mutableStateOf(DateUtils.parseLocalDate(initialFromIso) ?: today) }
    var endDate by remember(initialToIso) { mutableStateOf<LocalDate?>(DateUtils.parseLocalDate(initialToIso) ?: today) }
    var displayedYm by remember { mutableStateOf(YearMonth.from(endDate ?: startDate)) }

    Dialog(onDismissRequest = onDismiss) {
        SarimCard(contentPadding = PaddingValues(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { displayedYm = displayedYm.minusMonths(1) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    text = "${displayedYm.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${displayedYm.year}",
                    fontFamily = JakartaFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = { displayedYm = displayedYm.plusMonths(1) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Text(
                text = if (endDate == null) "Tap end date (${DateUtils.formatPretty(startDate.toString())} -> ...)"
                else "${DateUtils.formatPretty(startDate.toString())} to ${DateUtils.formatPretty(endDate.toString())}",
                style = MaterialTheme.typography.labelSmall,
                color = accents.emerald,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { wd ->
                    Text(
                        text = wd,
                        textAlign = TextAlign.Center,
                        fontFamily = JakartaFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = accents.textMuted,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val firstOfMonth = displayedYm.atDay(1)
            val leadingBlanks = firstOfMonth.dayOfWeek.value % 7
            val daysInMonth = displayedYm.lengthOfMonth()
            val cells = buildList<LocalDate?> {
                repeat(leadingBlanks) { add(null) }
                for (d in 1..daysInMonth) add(displayedYm.atDay(d))
                while (size % 7 != 0) add(null)
            }
            cells.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (day != null) {
                                val e = endDate
                                val isEndpoint = day == startDate || day == e
                                val inRange = e != null && !day.isBefore(startDate) && !day.isAfter(e)
                                val isToday = day == today
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isEndpoint -> accents.emerald
                                                inRange -> accents.emerald.copy(alpha = 0.22f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable {
                                            if (endDate != null) {
                                                startDate = day
                                                endDate = null
                                            } else {
                                                val (s, end) = if (day.isBefore(startDate)) day to startDate else startDate to day
                                                onApply(s.toString(), end.toString())
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = day.dayOfMonth.toString(),
                                            fontFamily = JakartaFont,
                                            fontWeight = if (isEndpoint || isToday) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = if (isEndpoint) CtaTextDark else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isToday) {
                                            Box(
                                                modifier = Modifier
                                                    .size(4.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isEndpoint) CtaTextDark else accents.emerald)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { onApply(today.toString(), today.toString()) }) {
                    Text("Today", color = accents.emerald, fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = {
                        val end = endDate ?: startDate
                        val (s, e) = if (end.isBefore(startDate)) end to startDate else startDate to end
                        onApply(s.toString(), e.toString())
                    },
                    modifier = Modifier.testTag(confirmTestTag)
                ) {
                    Text("Apply", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun <T> PickerField(
    label: String,
    selectedLabel: String,
    items: List<T>,
    itemLabel: (T) -> String,
    onSelect: (T) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val accents = LocalSarimAccents.current
    val borderColor by animateColorAsState(
        targetValue = if (expanded) accents.emerald else accents.inputBorder,
        animationSpec = SarimMotion.ColorSpring,
        label = "picker_border"
    )
    val chevronRot by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = SarimMotion.SpatialSpring,
        label = "picker_chevron"
    )
    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (label.isNotBlank()) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                    color = accents.textMuted,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )
            }
            Surface(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .testTag(testTag),
                shape = RoundedCornerShape(18.dp),
                color = accents.inputFill,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        selectedLabel.ifBlank { "Select $label" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Open $label menu",
                        tint = if (expanded) accents.emerald else accents.textMuted,
                        modifier = Modifier.rotate(chevronRot)
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            if (items.isEmpty()) {
                DropdownMenuItem(text = { Text("No options available") }, onClick = { expanded = false })
            } else {
                items.forEachIndexed { idx, item ->
                    DropdownMenuItem(
                        text = { Text(itemLabel(item)) },
                        onClick = {
                            onSelect(item)
                            expanded = false
                        },
                        modifier = Modifier.testTag("${testTag}_opt_$idx")
                    )
                }
            }
        }
    }
}

@Composable
fun DecimalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    prefix: String? = null,
    placeholder: String = "0.00"
) {
    SarimPillInput(
        value = value,
        onValueChange = { raw ->
            if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d{0,3}$"))) {
                onValueChange(raw)
            }
        },
        label = label.uppercase(),
        placeholder = placeholder,
        prefix = prefix?.let { { Text("$it ", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface) } },
        suffix = suffix?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        testTag = testTag,
        modifier = modifier
    )
}

@Composable
fun EmptyStateCard(
    title: String,
    detail: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector? = null
) {
    val accents = LocalSarimAccents.current
    SarimCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accents.softPillFill),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            }
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            SarimGradientButton(
                text = actionLabel,
                onClick = onAction,
                testTag = "empty_action_btn"
            )
        }
    }
}

@Composable
fun <T> SarimYearMonthDayTree(
    items: List<T>,
    dateOf: (T) -> String,
    summaryLabel: (List<T>) -> String,
    yearSubLabel: ((List<T>) -> String?)? = null,
    monthSubLabel: ((List<T>) -> String?)? = null,
    daySummaryLabel: ((List<T>) -> String)? = null,
    initiallyExpandFirstDay: Boolean = false,
    itemRow: @Composable (T) -> Unit
) {
    val accents = LocalSarimAccents.current
    val groupedByYear = remember(items) {
        items.groupBy { dateOf(it).take(4).ifBlank { "Unknown" } }
            .entries.sortedByDescending { it.key }
    }
    if (groupedByYear.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        groupedByYear.forEachIndexed { yIdx, (year, yearItems) ->
            var yearExpanded by remember(year) { mutableStateOf(yIdx == 0) }
            val ySub = yearSubLabel?.invoke(yearItems)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable { yearExpanded = !yearExpanded }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            year,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!ySub.isNullOrBlank()) {
                            Text(
                                ySub,
                                style = MaterialTheme.typography.labelSmall,
                                color = accents.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(summaryLabel(yearItems), style = MaterialTheme.typography.labelMedium, color = accents.textMuted)
                        Icon(
                            if (yearExpanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Toggle $year",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                AnimatedVisibility(
                    visible = yearExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val byMonth = remember(yearItems) {
                        yearItems.groupBy { dateOf(it).take(7) }.entries.sortedByDescending { it.key }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        byMonth.forEachIndexed { mIdx, (ym, monthItems) ->
                            var monthExpanded by remember(ym) { mutableStateOf(mIdx == 0) }
                            val monthTitle = DateUtils.formatPretty("$ym-01").drop(3).uppercase()
                            val mSub = monthSubLabel?.invoke(monthItems)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .clickable { monthExpanded = !monthExpanded }
                                    .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        monthTitle.ifBlank { ym },
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (!mSub.isNullOrBlank()) {
                                        Text(
                                            mSub,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accents.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(summaryLabel(monthItems), style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                    Icon(
                                        if (monthExpanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "Toggle $ym"
                                    )
                                }
                            }
                            AnimatedVisibility(
                                visible = monthExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                val byDay = remember(monthItems) {
                                    monthItems.groupBy { dateOf(it) }.entries.sortedByDescending { it.key }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    byDay.forEachIndexed { dIdx, (dayIso, dayItems) ->
                                        var dayExpanded by remember(dayIso) { mutableStateOf(initiallyExpandFirstDay && mIdx == 0 && dIdx == 0) }
                                        val localDate = DateUtils.parseLocalDate(dayIso)
                                        val dayNum = localDate?.dayOfMonth?.toString() ?: dayIso.takeLast(2)
                                        val dayName = localDate?.dayOfWeek?.name?.take(3)?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
                                        SarimCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .clickable { dayExpanded = !dayExpanded },
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.width(34.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        dayNum,
                                                        fontFamily = JakartaFont,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontSize = 15.5.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        dayName,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                        color = accents.textMuted
                                                    )
                                                }
                                                Spacer(Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        "${dayItems.size} ${if (dayItems.size == 1) "entry" else "entries"}",
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                    Text(
                                                        (daySummaryLabel ?: summaryLabel)(dayItems),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = accents.textMuted,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Icon(
                                                    if (dayExpanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                    contentDescription = "Toggle $dayIso",
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            AnimatedVisibility(
                                                visible = dayExpanded,
                                                enter = expandVertically() + fadeIn(),
                                                exit = shrinkVertically() + fadeOut()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(top = 8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                                                    dayItems.forEach { itemRow(it) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SarimDetailBottomSheet(
    title: String,
    rightTitle: String,
    subtitle: String? = null,
    surfaceTone: SheetSurfaceTone = SheetSurfaceTone.DEFAULT,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    footer: @Composable () -> Unit
) {
    val accents = LocalSarimAccents.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val patternModifier = when (surfaceTone) {
        SheetSurfaceTone.WARN_STRIPES -> Modifier.diagonalWarningStripes(accents.gold, accents.gold)
        SheetSurfaceTone.INFO_DOTS -> Modifier.cyanDotMatrixPattern(accents.cyan, accents.cyan)
        SheetSurfaceTone.GOOD -> Modifier.emeraldGoodGlow(accents.emerald)
        SheetSurfaceTone.DEFAULT -> Modifier
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = accents.headerFrosted,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .width(44.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(accents.borderSubtle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(accents.glassTop.copy(alpha = 0.85f), accents.headerFrosted, accents.headerFrosted)
                    )
                )
                .then(patternModifier)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = accents.emerald)
                    }
                }
                if (rightTitle.isNotBlank()) {
                    Text(rightTitle, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                content()
            }
            Spacer(Modifier.height(16.dp))
            footer()
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun SimpleBarChart(
    title: String,
    items: List<Pair<String, Double>>,
    barColor: Color,
    currency: String = "Rs",
    masked: Boolean = false
) {
    SarimCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) {
            Text("No data in selected period", style = MaterialTheme.typography.bodySmall, color = LocalSarimAccents.current.textMuted)
        } else {
            val maxVal = (items.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
            items.forEach { (label, amount) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(80.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))
                    ) {
                        val frac = (amount / maxVal).toFloat().coerceIn(0.02f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(frac)
                                .height(14.dp)
                                .background(barColor, RoundedCornerShape(7.dp))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        InputGuards.formatMoney(amount, currency, masked),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun SparklineChart(
    title: String,
    series: List<Pair<String, Double>>,
    lineColor: Color,
    currency: String = "Rs",
    masked: Boolean = false
) {
    SarimCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            series.lastOrNull()?.let { (_, v) ->
                Text(
                    InputGuards.formatMoney(v, currency, masked),
                    style = MaterialTheme.typography.labelMedium,
                    color = lineColor
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (series.size < 2) {
            Text("Add records across multiple months to visualize trend.", style = MaterialTheme.typography.bodySmall, color = LocalSarimAccents.current.textMuted)
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            ) {
                val maxV = series.maxOf { it.second }.coerceAtLeast(1.0)
                val minV = series.minOf { it.second }.coerceAtMost(0.0)
                val span = (maxV - minV).coerceAtLeast(1.0)
                val stepX = size.width / (series.size - 1).coerceAtLeast(1)
                val path = Path()
                series.forEachIndexed { idx, (_, value) ->
                    val x = idx * stepX
                    val y = size.height - (((value - minV) / span).toFloat() * (size.height - 16f)) - 8f
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                series.forEachIndexed { idx, (_, value) ->
                    val x = idx * stepX
                    val y = size.height - (((value - minV) / span).toFloat() * (size.height - 16f)) - 8f
                    drawCircle(color = lineColor, radius = 7f, center = Offset(x, y))
                    drawCircle(color = Color.White, radius = 3.5f, center = Offset(x, y))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                series.forEach { (label, _) ->
                    Text(label.takeLast(5), style = MaterialTheme.typography.labelSmall, color = LocalSarimAccents.current.textMuted)
                }
            }
        }
    }
}

/**
 * Web parity: `.toast-container` + `.liquid-toast` pill at top-center below status bar
 * with circular tinted glyph badge, text, and animated bottom progress bar.
 */
@Composable
fun LiquidToastOverlay(
    toastEvents: SharedFlow<ToastEvent>,
    modifier: Modifier = Modifier
) {
    val accents = LocalSarimAccents.current
    var currentToast by remember { mutableStateOf<ToastEvent?>(null) }
    val progress = remember { Animatable(1f) }

    androidx.compose.runtime.LaunchedEffect(toastEvents) {
        toastEvents.collect { evt ->
            currentToast = evt
        }
    }

    androidx.compose.runtime.LaunchedEffect(currentToast?.id) {
        val evt = currentToast ?: return@LaunchedEffect
        progress.snapTo(1f)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = evt.durationMs.toInt().coerceAtLeast(800), easing = LinearEasing)
        )
        delay(80)
        if (currentToast?.id == evt.id) {
            currentToast = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = currentToast != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(180)),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(180))
        ) {
            val evt = currentToast ?: return@AnimatedVisibility
            val tint = when (evt.kind) {
                ToastKind.SUCCESS -> accents.emerald
                ToastKind.WARNING -> accents.gold
                ToastKind.ERROR -> accents.danger
                ToastKind.INFO -> accents.cyan
            }
            val icon = when (evt.kind) {
                ToastKind.SUCCESS -> Icons.Rounded.Check
                ToastKind.WARNING -> Icons.Rounded.PriorityHigh
                ToastKind.ERROR -> Icons.Rounded.Close
                ToastKind.INFO -> Icons.Rounded.Info
            }
            Surface(
                onClick = { currentToast = null },
                shape = RoundedCornerShape(999.dp),
                color = accents.headerFrosted.copy(alpha = 0.97f),
                border = BorderStroke(1.dp, tint.copy(alpha = 0.38f)),
                shadowElevation = 10.dp,
                modifier = Modifier.testTag("liquid_toast_pill")
            ) {
                Box(modifier = Modifier.clip(RoundedCornerShape(999.dp))) {
                    Row(
                        modifier = Modifier
                            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SarimIconBadge(
                            icon = icon,
                            tint = tint,
                            size = 28.dp,
                            cornerRadius = 999.dp,
                            iconSize = 15.dp,
                            contentDescription = null
                        )
                        Text(
                            text = evt.message,
                            fontFamily = JakartaFont,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progress.value.coerceIn(0f, 1f))
                            .height(2.5.dp)
                            .background(tint.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}

/** Web parity: `#offline-banner` compact floating centered pill below top bar. */
@Composable
fun OfflineBanner(
    isOnline: Boolean,
    pendingSyncCount: Int,
    modifier: Modifier = Modifier
) {
    val accents = LocalSarimAccents.current
    if (isOnline && pendingSyncCount <= 0) return
    val dotColor = if (!isOnline) accents.gold else accents.cyan
    val statusIcon = if (!isOnline) Icons.Rounded.CloudOff else Icons.Rounded.Sync
    val message = if (!isOnline) {
        if (pendingSyncCount > 0) "Offline ($pendingSyncCount queued)" else "Offline — changes saved locally"
    } else {
        "Syncing $pendingSyncCount queued..."
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = accents.headerFrosted.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, dotColor.copy(alpha = 0.42f)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .wrapContentWidth()
                .testTag("offline_banner")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = dotColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = message,
                    fontFamily = JakartaFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

