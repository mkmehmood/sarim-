package com.gullzubair.sarim.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun SarimCard(
    modifier: Modifier = Modifier,
    accentBorder: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val accents = LocalSarimAccents.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentBorder ?: accents.borderSubtle),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
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
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, accents.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
    OutlinedTextField(
        value = DateUtils.formatPretty(dateIso),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            IconButton(
                onClick = { open = true },
                modifier = Modifier.testTag("${testTag}_btn")
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = "Pick $label")
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable { open = true }
            .testTag(testTag),
        singleLine = true
    )
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
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedCard(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = LocalSarimAccents.current.textMuted)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        selectedLabel.ifBlank { "Select $label" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Open $label menu")
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
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d{0,3}$"))) {
                onValueChange(raw)
            }
        },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}

@Composable
fun EmptyStateCard(title: String, detail: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    SarimCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = LocalSarimAccents.current.textMuted)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = onAction, modifier = Modifier.testTag("empty_action_btn")) {
                    Text(actionLabel)
                }
            }
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
