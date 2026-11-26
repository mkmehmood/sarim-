package com.gullzubair.sarim.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.ui.theme.LocalSarimAccents

/** Vertical bar chart (web: Store Comparison + Production Quantity charts). */
@Composable
fun ProductionBarChart(
    title: String,
    labels: List<String>,
    values: List<Double>,
    barColors: List<Color>,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier
) {
    val accents = LocalSarimAccents.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = accents.textMuted)
        Spacer(Modifier.height(10.dp))
        val maxVal = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.0)
        if (labels.isEmpty() || maxVal <= 0.0) {
            Text("No data in selected period", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            return@Column
        }
        val slot = if (labels.size > 12) 30.dp else 46.dp
        val scroll = rememberScrollState()
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            labels.forEachIndexed { i, label ->
                val v = values.getOrElse(i) { 0.0 }
                val color = barColors[i % barColors.size]
                Column(modifier = Modifier.width(slot), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (v > 0.0) valueLabel(v) else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = accents.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.height(120.dp).width(slot - 10.dp), contentAlignment = Alignment.BottomCenter) {
                        val frac = (v / maxVal).toFloat().coerceIn(0f, 1f)
                        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                            val h = size.height * frac
                            if (h > 0f) {
                                drawRoundRect(
                                    color = color,
                                    topLeft = Offset(0f, size.height - h),
                                    size = Size(size.width, h),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = accents.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Pie chart with legend (web: Financials pie — Total Cost vs Net Profit). */
@Composable
fun ProductionPieChart(
    title: String,
    slices: List<Pair<String, Double>>,
    colors: List<Color>,
    valueLabel: (Double) -> String,
    modifier: Modifier = Modifier
) {
    val accents = LocalSarimAccents.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = accents.textMuted)
        Spacer(Modifier.height(10.dp))
        val positive = slices.map { it.second.coerceAtLeast(0.0) }
        val total = positive.sum()
        if (total <= 0.0) {
            Text("No data in selected period", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Canvas(Modifier.size(120.dp)) {
                var start = -90f
                positive.forEachIndexed { i, v ->
                    val sweep = (v / total * 360.0).toFloat()
                    drawArc(
                        color = colors[i % colors.size],
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height)
                    )
                    start += sweep
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                slices.forEachIndexed { i, (label, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i % colors.size]))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Text(valueLabel(v), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}
