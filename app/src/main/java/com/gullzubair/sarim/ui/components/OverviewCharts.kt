package com.gullzubair.sarim.ui.components

import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.theme.JakartaFont
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
            Color(0xFF0EA5E9),
            accents.gold,
            accents.danger,
            accents.purple,
            Color(0xFF0D9488),
            Color(0xFF2563EB),
            Color(0xFFDB2777)
        )
    }
}

internal fun formatCompactMetric(v: Double, masked: Boolean = false): String {
    if (masked) return "••"
    val sign = if (v < 0) "-" else ""
    val a = abs(v)
    return when {
        a >= 1_000_000.0 -> {
            val m = a / 1_000_000.0
            if (abs(m - m.roundToInt()) < 0.05) "${sign}${m.roundToInt()}M"
            else "${sign}${String.format("%.1f", m)}M"
        }
        a >= 1_000.0 -> {
            val k = a / 1_000.0
            if (k >= 100 || abs(k - k.roundToInt()) < 0.05) "${sign}${k.roundToInt()}k"
            else "${sign}${String.format("%.1f", k)}k"
        }
        abs(a - a.roundToInt()) < 0.01 -> "${sign}${a.roundToInt()}"
        else -> "${sign}${String.format("%.1f", a)}"
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
            color = accents.textMuted
        )
        Spacer(Modifier.height(10.dp))

        if (labels.isEmpty() || series.isEmpty()) {
            Text("Nothing recorded in this period.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            return@SarimCard
        }

        // Toggleable series chips (matches 01b: wrapped pill chips on dark surface)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            series.forEachIndexed { idx, s ->
                val isOff = disabledIndices[idx] == true
                Surface(
                    onClick = { disabledIndices[idx] = !isOff },
                    shape = RoundedCornerShape(999.dp),
                    color = if (isOff) accents.inputFill.copy(alpha = 0.45f) else accents.inputFill,
                    border = BorderStroke(1.dp, if (isOff) accents.borderSubtle.copy(alpha = 0.45f) else accents.borderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (isOff) accents.textMuted.copy(alpha = 0.35f) else s.color)
                        )
                        Text(
                            s.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isOff) accents.textMuted else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (activeSeries.isEmpty()) {
            Text("Tap any legend pill above to display a series.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        } else {
            val allValues = activeSeries.flatMap { it.values }
            val maxV = (allValues.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
            val minV = (allValues.minOrNull() ?: 0.0).coerceAtMost(0.0)
            val span = (maxV - minV).coerceAtLeast(1.0)
            val mutedArgb = accents.textMuted.toArgb()
            val bgDotFill = accents.headerFrosted

            Box(modifier = Modifier.fillMaxWidth()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(172.dp)
                        .pointerInput(labels.size) {
                            detectTapGestures { offset ->
                                val count = labels.size
                                val padLeft = 36.dp.toPx()
                                val usableW = (size.width - padLeft - 10.dp.toPx()).coerceAtLeast(1f)
                                if (count > 0) {
                                    val idx = ((((offset.x - padLeft) / usableW) * (count - 1)).roundToInt())
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
                                val padLeft = 36.dp.toPx()
                                val usableW = (size.width - padLeft - 10.dp.toPx()).coerceAtLeast(1f)
                                if (count > 0) {
                                    val idx = ((((change.position.x - padLeft) / usableW) * (count - 1)).roundToInt())
                                        .coerceIn(0, count - 1)
                                    activeIndex = idx
                                }
                            }
                        }
                ) {
                    val padLeft = 36.dp.toPx()
                    val padRight = 10.dp.toPx()
                    val padTop = 10.dp.toPx()
                    val padBottom = 10.dp.toPx()
                    val usableW = (size.width - padLeft - padRight).coerceAtLeast(1f)
                    val usableH = (size.height - padTop - padBottom).coerceAtLeast(1f)
                    val count = labels.size
                    val stepX = if (count > 1) usableW / (count - 1) else 0f

                    val labelPaint = Paint().apply {
                        isAntiAlias = true
                        color = mutedArgb
                        textSize = 10.sp.toPx()
                        textAlign = Paint.Align.RIGHT
                    }

                    // 4 horizontal gridlines with Y-axis tick labels
                    val ticks = 3
                    for (t in 0..ticks) {
                        val frac = t.toFloat() / ticks.toFloat()
                        val y = padTop + usableH * (1f - frac)
                        val tickVal = minV + span * frac
                        drawLine(
                            color = accents.borderSubtle.copy(alpha = 0.55f),
                            start = Offset(padLeft, y),
                            end = Offset(size.width - padRight, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (t > 0) PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) else null
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            formatCompactMetric(tickVal, masked),
                            padLeft - 6.dp.toPx(),
                            y + 3.5.dp.toPx(),
                            labelPaint
                        )
                    }

                    val zeroY = padTop + usableH - (((0.0 - minV) / span).toFloat() * usableH)

                    activeSeries.forEach { s ->
                        val pts = if (s.values.size == 1) listOf(s.values.first(), s.values.first()) else s.values
                        val localStepX = if (pts.size > 1) usableW / (pts.size - 1) else 0f
                        val coords = pts.mapIndexed { idx, v ->
                            val x = padLeft + idx * localStepX
                            val y = padTop + usableH - (((v - minV) / span).toFloat() * usableH)
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
                                        listOf(s.color.copy(alpha = 0.20f), Color.Transparent)
                                    )
                                )
                            }
                            drawPath(
                                path = strokePath,
                                color = s.color,
                                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                            )
                            val focusIdx = activeIndex?.coerceIn(0, coords.lastIndex) ?: coords.lastIndex
                            val focusPt = coords[focusIdx]
                            drawCircle(color = s.color, radius = 4.2.dp.toPx(), center = focusPt)
                            drawCircle(color = bgDotFill, radius = 2.0.dp.toPx(), center = focusPt)
                        }
                    }

                    activeIndex?.let { idx ->
                        val x = padLeft + idx * stepX
                        drawLine(
                            color = accents.textMuted.copy(alpha = 0.5f),
                            start = Offset(x, padTop),
                            end = Offset(x, size.height - padBottom),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEachIndexed { idx, lbl ->
                    val isSel = activeIndex == idx
                    Text(
                        lbl,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        ),
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
    val mutedArgb = accents.textMuted.toArgb()
    val textArgb = MaterialTheme.colorScheme.onSurface.toArgb()

    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
            color = accents.textMuted
        )
        Spacer(Modifier.height(14.dp))
        if (labels.isEmpty() || values.all { it == 0.0 }) {
            Text("Nothing recorded in this period.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        } else {
            val hasNegative = values.any { it < 0.0 }
            val maxPos = (values.maxOfOrNull { it } ?: 1.0).coerceAtLeast(1.0)
            val minVal = if (hasNegative) (values.minOfOrNull { it } ?: 0.0).coerceAtMost(0.0) else 0.0
            val span = (maxPos - minVal).coerceAtLeast(1.0)
            val avgVal = values.average()

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(166.dp)
            ) {
                val padLeft = 36.dp.toPx()
                val padTop = 18.dp.toPx()
                val padBottom = 10.dp.toPx()
                val usableW = (size.width - padLeft - 8.dp.toPx()).coerceAtLeast(1f)
                val usableH = (size.height - padTop - padBottom).coerceAtLeast(1f)

                val axisPaint = Paint().apply {
                    isAntiAlias = true
                    color = mutedArgb
                    textSize = 10.sp.toPx()
                    textAlign = Paint.Align.RIGHT
                }
                val barTextPaint = Paint().apply {
                    isAntiAlias = true
                    color = textArgb
                    textSize = 9.5.sp.toPx()
                    textAlign = Paint.Align.CENTER
                    isFakeBoldText = true
                }

                // 4 horizontal gridlines
                for (t in 0..3) {
                    val frac = t.toFloat() / 3f
                    val y = padTop + usableH * (1f - frac)
                    val tickVal = minVal + span * frac
                    drawLine(
                        color = accents.borderSubtle.copy(alpha = 0.55f),
                        start = Offset(padLeft, y),
                        end = Offset(size.width - 6.dp.toPx(), y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = if (t > 0) PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) else null
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        formatCompactMetric(tickVal, masked),
                        padLeft - 6.dp.toPx(),
                        y + 3.5.dp.toPx(),
                        axisPaint
                    )
                }

                val zeroY = padTop + usableH - (((0.0 - minVal) / span).toFloat() * usableH)
                val slotW = usableW / values.size.coerceAtLeast(1)
                val barW = (slotW * 0.58f).coerceIn(14f, 44.dp.toPx())

                values.forEachIndexed { idx, v ->
                    val fracH = (abs(v) / span).toFloat() * usableH
                    val left = padLeft + idx * slotW + (slotW - barW) / 2f
                    val top = if (v >= 0.0) zeroY - fracH else zeroY
                    if (fracH > 1f) {
                        drawRoundRect(
                            color = if (v >= 0.0) accents.emerald else accents.danger,
                            topLeft = Offset(left, top),
                            size = Size(barW, fracH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                    val lblY = if (v >= 0.0) (top - 5.dp.toPx()).coerceAtLeast(10.dp.toPx()) else (top + fracH + 11.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(
                        formatCompactMetric(v, masked),
                        left + barW / 2f,
                        lblY,
                        barTextPaint
                    )
                }

                // Avg dashed reference line
                if (avgVal != 0.0) {
                    val avgY = padTop + usableH - (((avgVal - minVal) / span).toFloat() * usableH)
                    drawLine(
                        color = accents.textMuted.copy(alpha = 0.75f),
                        start = Offset(padLeft, avgY),
                        end = Offset(size.width - 6.dp.toPx(), avgY),
                        strokeWidth = 1.3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                    val avgPaint = Paint().apply {
                        isAntiAlias = true
                        color = textArgb
                        textSize = 9.5.sp.toPx()
                        textAlign = Paint.Align.RIGHT
                        isFakeBoldText = true
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "Avg ${formatCompactMetric(avgVal, masked)}",
                        size.width - 8.dp.toPx(),
                        avgY - 4.dp.toPx(),
                        avgPaint
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 36.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                labels.forEach { lbl ->
                    Text(lbl, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = accents.textMuted)
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
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
            color = accents.textMuted
        )
        Spacer(Modifier.height(12.dp))
        val sorted = remember(items) { items.sortedByDescending { it.second } }
        if (sorted.isEmpty()) {
            Text("Nothing recorded in this period.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        } else {
            val maxV = max(1.0, sorted.maxOf { max(0.0, it.second) })
            val totalV = sorted.sumOf { max(0.0, it.second) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sorted.forEach { (name, value) ->
                    val pct = if (showSharePct && totalV > 0.0) ((max(0.0, value) / totalV) * 100.0).roundToInt() else null
                    val valText = if (isMoneyMetric) {
                        InputGuards.formatMoney(value, currency, masked)
                    } else {
                        "${InputGuards.fmtNum(value, 0)}$unitSuffix"
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                name.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    text = valText,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (pct != null) {
                                    Text(
                                        text = "$pct%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accents.textMuted
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accents.inputFill)
                        ) {
                            val frac = if (value > 0.0) (max(0.0, value) / maxV).toFloat().coerceIn(0.025f, 1f) else 0f
                            if (frac > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(frac)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(barColor)
                                )
                            }
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
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(14.dp))
        if (total <= 0.0) {
            Text("Nothing recorded in this period.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        } else {
            val colors = listOf(accents.emerald, accents.gold, accents.cyan, accents.purple)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(104.dp)) {
                        var start = -90f
                        items.forEachIndexed { idx, (_, v) ->
                            val sweep = ((max(0.0, v) / total) * 360.0).toFloat()
                            drawArc(
                                color = colors[idx % colors.size],
                                startAngle = start,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Butt)
                            )
                            start += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatCompactMetric(total, masked),
                            fontFamily = JakartaFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "TOTAL",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                            color = accents.textMuted
                        )
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.forEachIndexed { idx, (label, v) ->
                        val share = (max(0.0, v) / total).toFloat().coerceIn(0f, 1f)
                        val pct = String.format("%.1f", share * 100f)
                        val col = colors[idx % colors.size]
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(col))
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "${formatCompactMetric(v, masked)}  $pct%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(accents.inputFill)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(share.coerceAtLeast(0.02f))
                                        .height(3.5.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(col)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
