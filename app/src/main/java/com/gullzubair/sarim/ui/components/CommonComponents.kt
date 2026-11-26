package com.gullzubair.sarim.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.theme.CinzelFont
import com.gullzubair.sarim.ui.theme.CtaGradientEnd
import com.gullzubair.sarim.ui.theme.CtaGradientMid
import com.gullzubair.sarim.ui.theme.CtaGradientStart
import com.gullzubair.sarim.ui.theme.CtaGradientStartLight
import com.gullzubair.sarim.ui.theme.DangerGradientStartLight
import com.gullzubair.sarim.ui.theme.DangerGradientEndLight
import com.gullzubair.sarim.ui.theme.CtaTextDark
import com.gullzubair.sarim.ui.theme.DangerGradientEnd
import com.gullzubair.sarim.ui.theme.DangerGradientStart
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.ToastEvent
import com.gullzubair.sarim.ui.viewmodel.ToastKind
import com.gullzubair.sarim.util.FeedbackHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import java.time.Instant
import java.time.ZoneOffset

/** Expressive screen title: large, heavy Playfair headline with tight tracking. */
@Composable
fun TabHeading(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    )
}

/**
 * Expressive filled card: a tonal container with a large (28dp) corner radius and no drop shadow.
 * Depth comes from the surface-container tone rather than elevation; pass [accentBorder] to
 * outline a card that needs to draw attention (errors, selections).
 */
@Composable
fun SarimCard(
    modifier: Modifier = Modifier,
    accentBorder: Color? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val accents = LocalSarimAccents.current
    val shape = RoundedCornerShape(28.dp)
    val borderWidth = if (accentBorder != null) 2.dp else accents.cardBorderWidth
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accents.glassTop)
            .then(
                if (borderWidth > 0.dp) Modifier.border(borderWidth, accentBorder ?: accents.borderSubtle, shape)
                else Modifier
            )
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
    // Expressive top bar: a tonal container with a large bottom radius, no hairline and no shadow.
    val shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = accents.headerFrosted,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp).testTag("nav_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(Modifier.width(10.dp))
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
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!badgeText.isNullOrBlank()) {
                        Surface(
                            color = accents.softPillFill,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = badgeText.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = accents.emerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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

/**
 * Shared body for the primary / danger action buttons.
 *
 * Expressive behaviour: a solid tonal fill, a 52dp touch target, and a shape morph — the pill
 * squares off to a 16dp radius while pressed and springs back with a slight overshoot.
 */
@Composable
private fun ExpressiveActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    testTag: String?,
    fillWidth: Boolean,
    container: Color,
    content: Color
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 16.dp else 26.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "actionButtonCorner"
    )
    val shape = RoundedCornerShape(corner)
    val onSurface = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minHeight = 52.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(shape)
            .background(if (enabled) container else onSurface.copy(alpha = 0.12f))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled
            ) {
                FeedbackHelper.triggerHaptic(context)
                FeedbackHelper.triggerSound(context)
                onClick()
            }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 15.sp,
            color = if (enabled) content else onSurface.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SarimGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null
) {
    ExpressiveActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        testTag = testTag,
        fillWidth = true,
        container = MaterialTheme.colorScheme.primary,
        content = MaterialTheme.colorScheme.onPrimary
    )
}

@Composable
fun SarimDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null
) {
    ExpressiveActionButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        testTag = testTag,
        fillWidth = false,
        container = MaterialTheme.colorScheme.error,
        content = MaterialTheme.colorScheme.onError
    )
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
                modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
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
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = accents.inputFill,
                unfocusedContainerColor = accents.inputFill,
                disabledContainerColor = accents.inputFill.copy(alpha = 0.6f),
                focusedBorderColor = accents.emerald,
                unfocusedBorderColor = accents.inputBorder,
                cursorColor = accents.emerald
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
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(accents.inputFill)
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
    // Expressive notes sit on the tertiary (gold) container rather than an outlined cream box.
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(24.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(16.dp)
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
        contentPadding = PaddingValues(14.dp)
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
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
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
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                color = accents.textMuted,
                modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
            )
        }
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(20.dp),
            color = accents.inputFill,
            border = BorderStroke(1.dp, accents.inputBorder),
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
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Rounded.CalendarToday,
                    contentDescription = "Pick $label",
                    tint = accents.textMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    if (open) {
        val initialMillis = remember(dateIso) {
            DateUtils.parseLocalDate(dateIso)?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { ms ->
                            val local = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            onDateSelected(local.toString())
                        }
                        open = false
                    },
                    modifier = Modifier.testTag("${testTag}_confirm")
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
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
    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (label.isNotBlank()) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                    color = accents.textMuted,
                    modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
                )
            }
            Surface(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .testTag(testTag),
                shape = RoundedCornerShape(20.dp),
                color = accents.inputFill,
                border = BorderStroke(1.dp, accents.inputBorder)
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
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Open $label menu")
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
    suffix: String? = null
) {
    SarimPillInput(
        value = value,
        onValueChange = { raw ->
            if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d{0,3}$"))) {
                onValueChange(raw)
            }
        },
        label = label.uppercase(),
        placeholder = "0.00",
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
                        .clip(RoundedCornerShape(20.dp))
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
                    Text(year, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(summaryLabel(yearItems), style = MaterialTheme.typography.labelMedium, color = accents.textMuted)
                        Icon(
                            if (yearExpanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Toggle $year",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                AnimatedVisibility(visible = yearExpanded) {
                    val byMonth = remember(yearItems) {
                        yearItems.groupBy { dateOf(it).take(7) }.entries.sortedByDescending { it.key }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        byMonth.forEachIndexed { mIdx, (ym, monthItems) ->
                            var monthExpanded by remember(ym) { mutableStateOf(mIdx == 0) }
                            val monthTitle = DateUtils.formatPretty("$ym-01").drop(3).uppercase()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp)
                                    .clickable { monthExpanded = !monthExpanded }
                                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(monthTitle.ifBlank { ym }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(summaryLabel(monthItems), style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                    Icon(
                                        if (monthExpanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                        contentDescription = "Toggle $ym"
                                    )
                                }
                            }
                            AnimatedVisibility(visible = monthExpanded) {
                                val byDay = remember(monthItems) {
                                    monthItems.groupBy { dateOf(it) }.entries.sortedByDescending { it.key }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    byDay.forEachIndexed { dIdx, (dayIso, dayItems) ->
                                        var dayExpanded by remember(dayIso) { mutableStateOf(dIdx == 0) }
                                        val localDate = DateUtils.parseLocalDate(dayIso)
                                        val dayNum = localDate?.dayOfMonth?.toString() ?: dayIso.takeLast(2)
                                        val dayName = localDate?.dayOfWeek?.name?.take(3)?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
                                        SarimCard(contentPadding = PaddingValues(12.dp)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 44.dp)
                                                    .clickable { dayExpanded = !dayExpanded },
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.width(36.dp)
                                                ) {
                                                    Text(dayNum, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                                    Text(dayName, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                                }
                                                Spacer(Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("${dayItems.size} ${if (dayItems.size == 1) "entry" else "entries"}", style = MaterialTheme.typography.titleSmall)
                                                    Text(summaryLabel(dayItems), style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                                }
                                                Icon(
                                                    if (dayExpanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                    contentDescription = "Toggle $dayIso"
                                                )
                                            }
                                            AnimatedVisibility(visible = dayExpanded) {
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
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    footer: @Composable () -> Unit
) {
    val accents = LocalSarimAccents.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = accents.headerFrosted,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(38.dp)
                    .height(4.dp)
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
            val glyph = when (evt.kind) {
                ToastKind.SUCCESS -> "✓"
                ToastKind.WARNING -> "!"
                ToastKind.ERROR -> "✕"
                ToastKind.INFO -> "i"
            }
            Surface(
                onClick = { currentToast = null },
                shape = RoundedCornerShape(999.dp),
                color = accents.headerFrosted.copy(alpha = 0.96f),
                border = BorderStroke(1.dp, tint.copy(alpha = 0.38f)),
                shadowElevation = 8.dp,
                modifier = Modifier.testTag("liquid_toast_pill")
            ) {
                Box(modifier = Modifier.clip(RoundedCornerShape(999.dp))) {
                    Row(
                        modifier = Modifier
                            .padding(start = 8.dp, end = 16.dp, top = 7.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(tint.copy(alpha = 0.18f))
                                .border(1.dp, tint.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = glyph,
                                fontFamily = JakartaFont,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = tint
                            )
                        }
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
                            .height(2.dp)
                            .background(tint.copy(alpha = 0.65f))
                    )
                }
            }
        }
    }
}

/** Web parity: `#offline-banner` pill showing offline / pending Room outbox count. */
@Composable
fun OfflineBanner(
    isOnline: Boolean,
    pendingSyncCount: Int,
    modifier: Modifier = Modifier
) {
    val accents = LocalSarimAccents.current
    if (isOnline && pendingSyncCount <= 0) return
    val dotColor = if (!isOnline) accents.gold else accents.cyan
    val message = if (!isOnline) {
        "Offline — changes saved locally"
    } else {
        "Syncing $pendingSyncCount queued operation${if (pendingSyncCount == 1) "" else "s"}..."
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accents.headerFrosted.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.35f)),
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("offline_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = message,
                fontFamily = JakartaFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (pendingSyncCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(accents.inputFill)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$pendingSyncCount pending",
                        fontFamily = JakartaFont,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.5.sp,
                        color = accents.textMuted
                    )
                }
            }
        }
    }
}

