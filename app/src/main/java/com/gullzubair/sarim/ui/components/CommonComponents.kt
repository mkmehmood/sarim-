package com.gullzubair.sarim.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import com.gullzubair.sarim.ui.theme.CtaTextDark
import com.gullzubair.sarim.ui.theme.DangerGradientEnd
import com.gullzubair.sarim.ui.theme.DangerGradientStart
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.util.FeedbackHelper
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun SarimCard(
    modifier: Modifier = Modifier,
    accentBorder: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val accents = LocalSarimAccents.current
    val shape = RoundedCornerShape(20.dp)
    val bgBrush = remember(accents.glassTop, accents.glassBottom) {
        Brush.verticalGradient(listOf(accents.glassTop, accents.glassBottom, accents.glassBottom))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = if (accents.isDark) 0.dp else 5.dp, shape = shape, ambientColor = Color(0x14059669), spotColor = Color(0x14059669))
            .clip(shape)
            .background(bgBrush)
            .border(1.dp, accentBorder ?: accents.borderSubtle, shape)
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
    val shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = if (accents.isDark) 0.dp else 6.dp, shape = shape, ambientColor = Color(0x16059669), spotColor = Color(0x16059669)),
        color = accents.headerFrosted,
        shape = shape,
        border = BorderStroke(1.dp, accents.borderSubtle.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp).testTag("nav_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
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
                        fontSize = 16.sp,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!badgeText.isNullOrBlank()) {
                        Surface(
                            color = accents.softPillFill,
                            shape = RoundedCornerShape(10.dp)
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

@Composable
fun SarimGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(26.dp)
    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(CtaGradientStart, CtaGradientMid, CtaGradientEnd))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFCFE0D9), Color(0xFFD8E6DF)))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 50.dp)
            .height(50.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .shadow(if (enabled) 4.dp else 0.dp, shape, ambientColor = Color(0x3310B981), spotColor = Color(0x3310B981))
            .clip(shape)
            .background(brush)
            .clickable(enabled = enabled) {
                FeedbackHelper.triggerHaptic(context)
                FeedbackHelper.triggerSound(context)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = JakartaFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (enabled) CtaTextDark else Color(0xFF6B8077),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SarimDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(26.dp)
    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(DangerGradientStart, DangerGradientEnd))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFE7B8B5), Color(0xFFEBC1B4)))
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 50.dp)
            .height(50.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .shadow(if (enabled) 3.dp else 0.dp, shape, ambientColor = Color(0x33EF4444), spotColor = Color(0x33EF4444))
            .clip(shape)
            .background(brush)
            .clickable(enabled = enabled) {
                FeedbackHelper.triggerHaptic(context)
                FeedbackHelper.triggerSound(context)
                onClick()
            }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontFamily = JakartaFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (enabled) Color(0xFF1F0905) else Color.White.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            shape = RoundedCornerShape(16.dp),
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
            .background(accents.inputFill.copy(alpha = 0.72f))
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
            shape = RoundedCornerShape(16.dp),
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
                    Icons.Default.CalendarToday,
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
                shape = RoundedCornerShape(16.dp),
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
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Open $label menu")
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
                            if (yearExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
                                        if (monthExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
                                                    if (dayExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
