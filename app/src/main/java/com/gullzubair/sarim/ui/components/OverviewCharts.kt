package com.gullzubair.sarim.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class OverviewSeries(
    val name: String,
    val values: List<Double>,
    val color: Color
)

@Composable
fun OverviewPalette(): List<Color> {
    val accents = LocalSarimAccents.current
    return remember(accents) {
        listOf(
            accents.emerald,
            accents.cyan,
            accents.gold,
            accents.danger,
            accents.purple,
            Color(0xFF0D9488),
            Color(0xFF2563EB),
            Color(0xFFDB2777)
        )
    }
}

@Composable
fun OverviewInteractiveLineChart(
    title: String,
    labels: List<String>,
    series: List<OverviewSeries>,
    currency: String = "Rs",
    masked: Boolean = false,
    unitSuffix: String = "",
    showArea: Boolean = false
) {
    val accents = LocalSarimAccents.current
    val disabledIndices = remember(series) { mutableStateMapOf<Int, Boolean>() }
    var activeIndex by remember(labels) { mutableStateOf<Int?>(null) }

    val activeSeries = remember(series, disabledIndices.toMap()) {
        series.mapIndexedNotNull { idx, s -> if (disabledIndices[idx] == true) null else s }
    }

    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(10.dp))

        if (labels.isEmpty() || series.isEmpty()) {
            Text("Nothing recorded in this period.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            return@SarimCard
        }

        // Toggleable series chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            series.forEachIndexed { idx, s ->
                val isOff = disabledIndices[idx] == true
                Surface(
                    onClick = { disabledIndices[idx] = !isOff },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isOff) accents.inputFill else s.color.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, if (isOff) accents.borderSubtle else s.color.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isOff) accents.textMuted.copy(alpha = 0.4f) else s.color)
                        )
                        Text(
                            s.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOff) accents.textMuted else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (activeSeries.isEmpty()) {
            Text("Tap any legend pill above to display a series.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        } else {
            val allValues = activeSeries.flatMap { it.values }
            val maxV = (allValues.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
            val minV = (allValues.minOrNull() ?: 0.0).coerceAtMost(0.0)
            val span = (maxV - minV).coerceAtLeast(1.0)

            Box(modifier = Modifier.fillMaxWidth()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .pointerInput(labels.size) {
                            detectTapGestures { offset ->
                                val count = labels.size
                                if (count > 0 && size.width > 0) {
                                    val idx = (((offset.x / size.width.toFloat()) * (count - 1)).roundToInt())
                                        .coerceIn(0, count - 1)
                                    activeIndex = if (activeIndex == idx) null else idx
                                }
                            }
                        }
                        .pointerInput(labels.size) {
                            detectDragGestures(
                                onDragEnd = { activeIndex = null },
                                onDragCancel = { activeIndex = null }
                            ) { change, _ ->
                                val count = labels.size
                                if (count > 0 && size.width > 0) {
                                    val idx = (((change.position.x / size.width.toFloat()) * (count - 1)).roundToInt())
                                        .coerceIn(0, count - 1)
                                    activeIndex = idx
                                }
                            }
                        }
                ) {
                    val padH = 12f
                    val padV = 16f
                    val usableW = (size.width - padH * 2f).coerceAtLeast(1f)
                    val usableH = (size.height - padV * 2f).coerceAtLeast(1f)
                    val count = labels.size
                    val stepX = if (count > 1) usableW / (count - 1) else 0f

                    // Baseline at zero
                    val zeroY = padV + usableH - (((0.0 - minV) / span).toFloat() * usableH)
                    drawLine(
                        color = accents.borderSubtle.copy(alpha = 0.7f),
                        start = Offset(padH, zeroY),
                        end = Offset(size.width - padH, zeroY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )

                    activeSeries.forEach { s ->
                        val pts = if (s.values.size == 1) listOf(s.values.first(), s.values.first()) else s.values
                        val localStepX = if (pts.size > 1) usableW / (pts.size - 1) else 0f
                        val coords = pts.mapIndexed { idx, v ->
                            val x = padH + idx * localStepX
                            val y = padV + usableH - (((v - minV) / span).toFloat() * usableH)
                            Offset(x, y)
                        }
                        if (coords.isNotEmpty()) {
                            val strokePath = Path().apply {
                                moveTo(coords.first().x, coords.first().y)
                                for (i in 0 until coords.lastIndex) {
                                    val p0 = coords[i]
                                    val p1 = coords[i + 1]
                                    val cx = (p0.x + p1.x) / 2f
                                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                                }
                            }
                            if (showArea && coords.size >= 2) {
                                val areaPath = Path().apply {
                                    addPath(strokePath)
                                    lineTo(coords.last().x, zeroY)
                                    lineTo(coords.first().x, zeroY)
                                    close()
                                }
                                drawPath(
                                    path = areaPath,
                                    brush = Brush.verticalGradient(
                                        listOf(s.color.copy(alpha = 0.22f), Color.Transparent)
                                    )
                                )
                            }
                            drawPath(
                                path = strokePath,
                                color = s.color,
                                style = Stroke(width = 5f, cap = StrokeCap.Round)
                            )
                            val focusIdx = activeIndex?.coerceIn(0, coords.lastIndex) ?: coords.lastIndex
                            val focusPt = coords[focusIdx]
                            drawCircle(color = s.color, radius = 8f, center = focusPt)
                            drawCircle(color = Color.White, radius = 4f, center = focusPt)
                        }
                    }

                    activeIndex?.let { idx ->
                        val x = padH + idx * stepX
                        drawLine(
                            color = accents.textMuted.copy(alpha = 0.6f),
                            start = Offset(x, padV),
                            end = Offset(x, size.height - padV),
                            strokeWidth = 2f
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEachIndexed { idx, lbl ->
                    val isSel = activeIndex == idx
                    Text(
                        lbl,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal),
                        color = if (isSel) accents.emerald else accents.textMuted
                    )
                }
            }

            // Active Scrub Tooltip
            activeIndex?.let { idx ->
                if (idx in labels.indices) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accents.inputFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(labels[idx], style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                            activeSeries.forEach { s ->
                                val rawVal = s.values.getOrElse(idx) { 0.0 }
                                val formatted = if (unitSuffix.isNotBlank()) {
                                    "${InputGuards.fmtNum(rawVal, 0)}$unitSuffix"
                                } else {
                                    InputGuards.formatMoney(rawVal, currency, masked)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(s.name, style = MaterialTheme.typography.bodySmall, color = s.color)
                                    Text(formatted, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverviewNetProfitBarChart(
    title: String,
    labels: List<String>,
    values: List<Double>,
    currency: String,
    masked: Boolean
) {
    val accents = LocalSarimAccents.current
    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(10.dp))
        if (labels.isEmpty() || values.all { it == 0.0 }) {
            Text("Nothing recorded in this period.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        } else {
            val maxAbs = values.maxOfOrNull { abs(it) }?.coerceAtLeast(1.0) ?: 1.0
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val midY = size.height / 2f
                drawLine(
                    color = accents.borderSubtle,
                    start = Offset(0f, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = 2f
                )
                val slotW = size.width / values.size.coerceAtLeast(1)
                val barW = (slotW * 0.56f).coerceIn(10f, 44f)
                values.forEachIndexed { idx, v ->
                    val frac = (abs(v) / maxAbs).toFloat().coerceIn(0.05f, 0.92f)
                    val h = frac * (size.height * 0.44f)
                    val left = idx * slotW + (slotW - barW) / 2f
                    val top = if (v >= 0.0) midY - h else midY
                    drawRoundRect(
                        color = if (v >= 0.0) accents.emerald else accents.danger,
                        topLeft = Offset(left, top),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEachIndexed { idx, lbl ->
                    val v = values.getOrElse(idx) { 0.0 }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(lbl, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                        Text(
                            InputGuards.formatMoney(v, currency, masked),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (v >= 0.0) accents.emerald else accents.danger
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OverviewRankedBarsCard(
    title: String,
    items: List<Pair<String, Double>>,
    barColor: Color,
    currency: String = "Rs",
    masked: Boolean = false,
    unitSuffix: String = "",
    showSharePct: Boolean = true,
    isMoneyMetric: Boolean = unitSuffix.isBlank()
) {
    val accents = LocalSarimAccents.current
    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(10.dp))
        val sorted = remember(items) { items.sortedByDescending { it.second } }
        if (sorted.isEmpty()) {
            Text("Nothing recorded in this period.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        } else {
            val maxV = max(1.0, sorted.maxOf { max(0.0, it.second) })
            val totalV = sorted.sumOf { max(0.0, it.second) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sorted.forEach { (name, value) ->
                    val pct = if (showSharePct && totalV > 0.0) ((max(0.0, value) / totalV) * 100.0).roundToInt() else null
                    val valText = if (isMoneyMetric) {
                        InputGuards.formatMoney(value, currency, masked)
                    } else {
                        "${InputGuards.fmtNum(value, 0)}$unitSuffix"
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (pct != null) "$valText ($pct%)" else valText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = barColor
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(accents.inputFill)
                        ) {
                            val frac = (max(0.0, value) / maxV).toFloat().coerceIn(0.03f, 1f)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(frac)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(barColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverviewDonutSummaryCard(
    title: String,
    items: List<Pair<String, Double>>,
    currency: String,
    masked: Boolean
) {
    val accents = LocalSarimAccents.current
    val total = items.sumOf { max(0.0, it.second) }
    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(10.dp))
        if (total <= 0.0) {
            Text("Nothing recorded in this period.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        } else {
            val colors = listOf(accents.emerald, accents.gold, accents.cyan, accents.purple)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(86.dp)) {
                    var start = -90f
                    items.forEachIndexed { idx, (_, v) ->
                        val sweep = ((max(0.0, v) / total) * 360.0).toFloat()
                        drawArc(
                            color = colors[idx % colors.size],
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = 18f, cap = StrokeCap.Butt)
                        )
                        start += sweep
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEachIndexed { idx, (label, v) ->
                        val pct = ((max(0.0, v) / total) * 100.0).roundToInt()
                        val col = colors[idx % colors.size]
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(9.dp).clip(CircleShape).background(col))
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "${InputGuards.formatMoney(v, currency, masked)} ($pct%)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = col
                            )
                        }
                    }
                }
            }
        }
    }
}
