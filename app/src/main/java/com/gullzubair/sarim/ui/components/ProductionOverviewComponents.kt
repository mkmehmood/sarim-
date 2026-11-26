package com.gullzubair.sarim.ui.components

import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.ProductionAnalyticsEngine
import com.gullzubair.sarim.ui.theme.CinzelFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.theme.PlayfairFont
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun AllStoresOverviewSection(
    overview: ProductionAnalyticsEngine.AllStoresOverviewResult,
    selectedPeriod: ProductionAnalyticsEngine.OverviewPeriod,
    onSelectPeriod: (ProductionAnalyticsEngine.OverviewPeriod) -> Unit,
    currency: String,
    masked: Boolean
) {
    val accents = LocalSarimAccents.current
    val periodLabel = if (selectedPeriod == ProductionAnalyticsEngine.OverviewPeriod.ALL) "All time" else selectedPeriod.label.uppercase()
    val comb = overview.combined

    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Text(
            text = "ALL STORES OVERVIEW",
            fontFamily = CinzelFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.5.sp,
            letterSpacing = 0.8.sp,
            color = accents.emerald,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProductionAnalyticsEngine.OverviewPeriod.entries.forEach { period ->
                val selected = period == selectedPeriod
                Surface(
                    onClick = { onSelectPeriod(period) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) accents.softPillFill else accents.inputFill,
                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 38.dp)
                        .testTag("prod_overview_period_${period.key}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = period.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) accents.emerald else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // TOTAL COMBINED FIRST (exact match to e-prod-combined screenshot)
        SarimCard(
            accentBorder = accents.emerald.copy(alpha = 0.45f),
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TOTAL COMBINED",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                SxPeriodBadge(periodLabel)
            }

            Spacer(Modifier.height(12.dp))
            SxHeroBlock(
                label = "NET PROFIT",
                value = InputGuards.formatMoney(comb.netProfit, currency, masked),
                sub = "on ${InputGuards.formatMoney(comb.totalValue, currency, masked)} total value",
                chip = comb.marginPct?.let { "${InputGuards.fmtNum(it, 1)}% margin" },
                chipColor = if ((comb.marginPct ?: 0.0) >= 0.0) accents.emerald else accents.danger
            )
            Spacer(Modifier.height(10.dp))
            SxSplitBar(
                segments = listOf(
                    SxSplitSegment("Cost", comb.totalCost, accents.danger),
                    SxSplitSegment("Profit", max(0.0, comb.netProfit), accents.emerald)
                ),
                formatValue = { InputGuards.formatMoney(it, currency, masked) }
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SarimHalfRingKpiBox("TOTAL VALUE", InputGuards.formatMoney(comb.totalValue, currency, masked), accents.emerald, Modifier.weight(1f))
                SarimHalfRingKpiBox("TOTAL COST", InputGuards.formatMoney(comb.totalCost, currency, masked), accents.danger, Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
            Spacer(Modifier.height(12.dp))

            SxHeroBlock(
                label = "TOTAL REMAINING",
                value = InputGuards.formatKg(comb.remainingKg),
                sub = "${InputGuards.formatKg(comb.totalInKg)} in · ${InputGuards.formatKg(comb.soldKg)} sold",
                chip = null,
                chipColor = accents.emerald
            )
            Spacer(Modifier.height(10.dp))
            SxSplitBar(
                segments = listOf(
                    SxSplitSegment("Sold", comb.soldKg, accents.gold),
                    SxSplitSegment("Remaining", max(0.0, comb.remainingKg), accents.emerald)
                ),
                formatValue = { InputGuards.formatKg(it) }
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SarimHalfRingKpiBox("FRESH PRODUCTION", InputGuards.formatKg(comb.producedKg), accents.emerald, Modifier.weight(1f))
                SarimHalfRingKpiBox("TOTAL SOLD", InputGuards.formatKg(comb.soldKg), accents.danger, Modifier.weight(1f))
            }
            if (comb.returnsKg > 0.0) {
                Spacer(Modifier.height(8.dp))
                SarimHalfRingKpiBox("TOTAL RETURNS", InputGuards.formatKg(comb.returnsKg), accents.gold, Modifier.fillMaxWidth())
            }

            if (comb.soldByCustomer.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SxCustomerBreakdown(
                    title = "SOLD BY CUSTOMER",
                    rows = comb.soldByCustomer,
                    testTag = "combined_cust_breakdown"
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SarimHalfRingKpiBox("FORMULA UNITS", InputGuards.fmtNum(comb.formulaUnits), accents.gold, Modifier.weight(1f))
                SarimHalfRingKpiBox("FORMULA COST", InputGuards.formatMoney(comb.formulaCost, currency, masked), accents.danger, Modifier.weight(1f))
            }
        }

        // Per-store overview cards below Total Combined
        overview.stores.forEach { st ->
            Spacer(Modifier.height(12.dp))
            val storeAccent = when {
                st.storeKey.contains("zubair", true) -> accents.emerald
                st.storeKey.contains("mahmood", true) -> accents.purple
                st.storeKey.contains("asaan", true) -> accents.cyan
                else -> accents.emerald
            }
            val pctLeft = if (st.totalInKg > 0.0) {
                max(0, ((st.remainingKg / st.totalInKg) * 100.0).roundToInt())
            } else null

            SarimCard(contentPadding = PaddingValues(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = storeAccent.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, storeAccent.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = st.storeName.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp),
                            color = storeAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    SxPeriodBadge(periodLabel)
                }

                Spacer(Modifier.height(12.dp))
                SxHeroBlock(
                    label = "REMAINING STOCK",
                    value = InputGuards.formatKg(st.remainingKg),
                    sub = "${InputGuards.formatKg(st.totalInKg)} in · ${InputGuards.formatKg(st.soldKg)} sold",
                    chip = pctLeft?.let { "$it% left" },
                    chipColor = if (st.remainingKg > 0.0) accents.emerald else accents.gold
                )
                Spacer(Modifier.height(10.dp))
                SxSplitBar(
                    segments = listOf(
                        SxSplitSegment("Sold", st.soldKg, accents.gold),
                        SxSplitSegment("Remaining", max(0.0, st.remainingKg), accents.emerald)
                    ),
                    formatValue = { InputGuards.formatKg(it) }
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SarimHalfRingKpiBox("PRODUCED", InputGuards.formatKg(st.producedKg), accents.emerald, Modifier.weight(1f))
                    if (st.returnsKg > 0.0) {
                        SarimHalfRingKpiBox("RETURNS RECEIVED", InputGuards.formatKg(st.returnsKg), accents.gold, Modifier.weight(1f))
                    } else {
                        SarimHalfRingKpiBox("SOLD (SALES TAB)", InputGuards.formatKg(st.soldKg), accents.danger, Modifier.weight(1f))
                    }
                }
                if (st.returnsKg > 0.0) {
                    Spacer(Modifier.height(8.dp))
                    SarimHalfRingKpiBox("SOLD (SALES TAB)", InputGuards.formatKg(st.soldKg), accents.danger, Modifier.fillMaxWidth())
                }

                if (st.soldByCustomer.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SxCustomerBreakdown(
                        title = "SOLD BY CUSTOMER",
                        rows = st.soldByCustomer,
                        testTag = "store_cust_breakdown_${st.storeKey}"
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SarimHalfRingKpiBox("FORMULA UNITS", InputGuards.fmtNum(st.formulaUnits), accents.gold, Modifier.weight(1f))
                    SarimHalfRingKpiBox("FORMULA COST", InputGuards.formatMoney(st.formulaCost, currency, masked), accents.danger, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SarimHalfRingKpiBox("TOTAL VALUE", InputGuards.formatMoney(st.totalValue, currency, masked), accents.emerald, Modifier.weight(1f))
                    SarimHalfRingKpiBox(
                        label = "NET PROFIT",
                        value = InputGuards.formatMoney(st.netProfit, currency, masked),
                        ringColor = if (st.netProfit >= 0.0) accents.emerald else accents.danger,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun StoreComparisonChartCard(
    points: List<ProductionAnalyticsEngine.StoreComparisonPoint>,
    selectedMetric: ProductionAnalyticsEngine.ComparisonMetric,
    onSelectMetric: (ProductionAnalyticsEngine.ComparisonMetric) -> Unit,
    periodLabel: String,
    currency: String,
    masked: Boolean
) {
    val accents = LocalSarimAccents.current
    var activeIdx by remember(points, selectedMetric) { mutableStateOf<Int?>(null) }
    val mutedArgb = accents.textMuted.toArgb()
    val textArgb = MaterialTheme.colorScheme.onSurface.toArgb()

    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Text(
            text = "COMPARE STORES BY:",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
            color = accents.textMuted
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProductionAnalyticsEngine.ComparisonMetric.entries.forEach { metric ->
                val selected = metric == selectedMetric
                Surface(
                    onClick = { onSelectMetric(metric) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) accents.softPillFill else accents.inputFill,
                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 38.dp)
                        .testTag("prod_cmp_metric_${metric.key}")
                ) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            metric.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) accents.emerald else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = accents.inputFill,
            border = BorderStroke(1.dp, accents.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "Store Comparison by ${selectedMetric.label} ($periodLabel)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))

                if (points.isEmpty() || points.all { it.value == 0.0 }) {
                    Text("No production data for this period.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                } else {
                    val maxAbs = points.maxOfOrNull { abs(it.value) }?.coerceAtLeast(1.0) ?: 1.0
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .pointerInput(points.size) {
                                detectTapGestures { offset ->
                                    val padLeft = 34.dp.toPx()
                                    val usableW = (size.width - padLeft).coerceAtLeast(1f)
                                    if (points.isNotEmpty()) {
                                        val idx = (((offset.x - padLeft) / usableW) * points.size).toInt().coerceIn(0, points.lastIndex)
                                        activeIdx = if (activeIdx == idx) null else idx
                                    }
                                }
                            }
                    ) {
                        val padLeft = 34.dp.toPx()
                        val padTop = 18.dp.toPx()
                        val padBottom = 8.dp.toPx()
                        val usableW = (size.width - padLeft - 8.dp.toPx()).coerceAtLeast(1f)
                        val usableH = (size.height - padTop - padBottom).coerceAtLeast(1f)
                        val baselineY = padTop + usableH

                        val axisPaint = Paint().apply {
                            isAntiAlias = true
                            color = mutedArgb
                            textSize = 10.sp.toPx()
                            textAlign = Paint.Align.RIGHT
                        }
                        val barValPaint = Paint().apply {
                            isAntiAlias = true
                            color = textArgb
                            textSize = 9.5.sp.toPx()
                            textAlign = Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                        for (t in 0..4) {
                            val frac = t / 4f
                            val y = padTop + usableH * (1f - frac)
                            val tickVal = maxAbs * frac
                            drawLine(
                                color = accents.borderSubtle.copy(alpha = 0.5f),
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

                        val count = points.size.coerceAtLeast(1)
                        val slotW = usableW / count
                        val barW = (slotW * 0.48f).coerceIn(18f, 48.dp.toPx())
                        points.forEachIndexed { idx, pt ->
                            val frac = (max(0.0, pt.value) / maxAbs).toFloat().coerceIn(0f, 1f)
                            val barH = if (pt.value > 0.0) (frac * usableH).coerceAtLeast(4.dp.toPx()) else 0f
                            val left = padLeft + idx * slotW + (slotW - barW) / 2f
                            if (barH > 0f) {
                                drawRoundRect(
                                    brush = Brush.verticalGradient(listOf(accents.emerald, Color(0xFF065F46))),
                                    topLeft = Offset(left, baselineY - barH),
                                    size = Size(barW, barH),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                )
                                drawContext.canvas.nativeCanvas.drawText(
                                    formatCompactMetric(pt.value, masked),
                                    left + barW / 2f,
                                    (baselineY - barH - 5.dp.toPx()).coerceAtLeast(10.dp.toPx()),
                                    barValPaint
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 34.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        points.forEachIndexed { idx, pt ->
                            Text(
                                text = pt.storeName.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = accents.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { activeIdx = if (activeIdx == idx) null else idx }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MfgAnalyticsSection(
    analytics: ProductionAnalyticsEngine.MfgAnalyticsResult,
    selectedPeriod: ProductionAnalyticsEngine.MfgChartPeriod,
    onSelectPeriod: (ProductionAnalyticsEngine.MfgChartPeriod) -> Unit,
    currency: String,
    masked: Boolean
) {
    val accents = LocalSarimAccents.current
    val modeLabel = if (selectedPeriod == ProductionAnalyticsEngine.MfgChartPeriod.ALL) "All Times" else selectedPeriod.label
    var activeBarIdx by remember(analytics.labels, selectedPeriod) { mutableStateOf<Int?>(null) }
    val mutedArgb = accents.textMuted.toArgb()
    val textArgb = MaterialTheme.colorScheme.onSurface.toArgb()

    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Text(
            text = "Analytics",
            fontFamily = PlayfairFont,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = accents.emerald
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProductionAnalyticsEngine.MfgChartPeriod.entries.forEach { period ->
                val selected = period == selectedPeriod
                Surface(
                    onClick = { onSelectPeriod(period) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) accents.softPillFill else accents.inputFill,
                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 38.dp)
                        .testTag("prod_mfg_period_${period.key}")
                ) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = period.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) accents.emerald else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = accents.inputFill,
            border = BorderStroke(1.dp, accents.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "Production Quantity ($modeLabel)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(10.dp))

                if (analytics.labels.isEmpty() || analytics.quantities.all { it == 0.0 }) {
                    Text("Nothing produced in this period.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                } else {
                    val maxQ = analytics.quantities.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                    val nonZero = analytics.quantities.filter { it > 0.0 }
                    val avgQ = if (nonZero.isNotEmpty()) nonZero.average() else 0.0
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(166.dp)
                            .pointerInput(analytics.quantities.size) {
                                detectTapGestures { offset ->
                                    val padLeft = 32.dp.toPx()
                                    val usableW = (size.width - padLeft).coerceAtLeast(1f)
                                    if (analytics.quantities.isNotEmpty()) {
                                        val idx = (((offset.x - padLeft) / usableW) * analytics.quantities.size)
                                            .toInt().coerceIn(0, analytics.quantities.lastIndex)
                                        activeBarIdx = if (activeBarIdx == idx) null else idx
                                    }
                                }
                            }
                    ) {
                        val padLeft = 32.dp.toPx()
                        val padTop = 18.dp.toPx()
                        val padBottom = 8.dp.toPx()
                        val usableW = (size.width - padLeft - 8.dp.toPx()).coerceAtLeast(1f)
                        val usableH = (size.height - padTop - padBottom).coerceAtLeast(1f)
                        val baselineY = padTop + usableH

                        val axisPaint = Paint().apply {
                            isAntiAlias = true
                            color = mutedArgb
                            textSize = 10.sp.toPx()
                            textAlign = Paint.Align.RIGHT
                        }
                        val barValPaint = Paint().apply {
                            isAntiAlias = true
                            color = textArgb
                            textSize = 9.sp.toPx()
                            textAlign = Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                        for (t in 0..4) {
                            val frac = t / 4f
                            val y = padTop + usableH * (1f - frac)
                            drawLine(
                                color = accents.borderSubtle.copy(alpha = 0.5f),
                                start = Offset(padLeft, y),
                                end = Offset(size.width - 6.dp.toPx(), y),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = if (t > 0) PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f) else null
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                formatCompactMetric(maxQ * frac),
                                padLeft - 5.dp.toPx(),
                                y + 3.5.dp.toPx(),
                                axisPaint
                            )
                        }

                        val count = analytics.quantities.size.coerceAtLeast(1)
                        val slotW = usableW / count
                        val barW = (slotW * 0.60f).coerceIn(5f, 32.dp.toPx())
                        analytics.quantities.forEachIndexed { idx, q ->
                            val frac = if (q > 0.0) (q / maxQ).toFloat().coerceIn(0.04f, 1f) else 0f
                            if (frac > 0f) {
                                val h = frac * usableH
                                val left = padLeft + idx * slotW + (slotW - barW) / 2f
                                val barColors = if (activeBarIdx == idx) {
                                    listOf(accents.emerald, Color(0xFF065F46))
                                } else {
                                    listOf(Color(0xFF38BDF8), Color(0xFF1D4ED8))
                                }
                                drawRoundRect(
                                    brush = Brush.verticalGradient(barColors),
                                    topLeft = Offset(left, baselineY - h),
                                    size = Size(barW, h),
                                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                                )
                                if (count <= 10) {
                                    drawContext.canvas.nativeCanvas.drawText(
                                        InputGuards.fmtNum(q),
                                        left + barW / 2f,
                                        (baselineY - h - 5.dp.toPx()).coerceAtLeast(10.dp.toPx()),
                                        barValPaint
                                    )
                                }
                            }
                        }

                        if (avgQ > 0.0) {
                            val avgY = baselineY - ((avgQ / maxQ).toFloat() * usableH)
                            drawLine(
                                color = accents.gold.copy(alpha = 0.85f),
                                start = Offset(padLeft, avgY),
                                end = Offset(size.width - 6.dp.toPx(), avgY),
                                strokeWidth = 1.3.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )
                            val avgPaint = Paint().apply {
                                isAntiAlias = true
                                color = accents.gold.toArgb()
                                textSize = 9.5.sp.toPx()
                                textAlign = Paint.Align.RIGHT
                                isFakeBoldText = true
                            }
                            drawContext.canvas.nativeCanvas.drawText(
                                "Avg ${InputGuards.fmtNum(avgQ, 0)}",
                                size.width - 8.dp.toPx(),
                                avgY - 4.dp.toPx(),
                                avgPaint
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    val stride = when {
                        analytics.labels.size > 20 -> 5
                        analytics.labels.size > 12 -> 3
                        else -> 1
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        analytics.labels.forEachIndexed { idx, lbl ->
                            if (idx % stride == 0 || idx == analytics.labels.lastIndex) {
                                Text(lbl, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = accents.textMuted)
                            }
                        }
                    }

                    activeBarIdx?.let { idx ->
                        if (idx in analytics.labels.indices) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, accents.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(analytics.labels[idx], style = MaterialTheme.typography.labelMedium, color = accents.cyan)
                                    Text(
                                        InputGuards.formatKg(analytics.quantities[idx]),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = accents.emerald
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OverviewDonutSummaryCard(
            title = "Financials: ${InputGuards.formatMoney(analytics.totalValue, currency, masked)} Total - $modeLabel",
            items = listOf(
                "Total Cost" to analytics.totalCost,
                "Net Profit" to max(0.0, analytics.totalProfit)
            ),
            currency = currency,
            masked = masked
        )
    }
}

@Composable
private fun SxPeriodBadge(label: String) {
    val accents = LocalSarimAccents.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accents.softPillFill,
        border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.28f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = accents.emerald,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun SxHeroBlock(
    label: String,
    value: String,
    sub: String,
    chip: String?,
    chipColor: Color
) {
    val accents = LocalSarimAccents.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                color = accents.textMuted
            )
            if (!chip.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = chipColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, chipColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = chip,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = chipColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (sub.isNotBlank()) {
            Text(text = sub, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        }
    }
}

private data class SxSplitSegment(
    val label: String,
    val value: Double,
    val color: Color
)

@Composable
private fun SxSplitBar(
    segments: List<SxSplitSegment>,
    formatValue: (Double) -> String
) {
    val accents = LocalSarimAccents.current
    val total = segments.sumOf { max(0.0, it.value) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accents.inputFill)
        ) {
            if (total > 0.0) {
                segments.filter { it.value > 0.0 }.forEach { seg ->
                    val weight = max(0.02f, (seg.value / total).toFloat())
                    Box(
                        modifier = Modifier
                            .weight(weight)
                            .fillMaxHeight()
                            .background(seg.color)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            segments.forEach { seg ->
                val pct = if (total > 0.0) ((max(0.0, seg.value) / total) * 100.0).roundToInt() else 0
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(seg.color))
                    Text(
                        text = "${seg.label} ${formatValue(seg.value)} $pct%",
                        style = MaterialTheme.typography.labelSmall,
                        color = accents.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SxCustomerBreakdown(
    title: String,
    rows: List<Pair<String, Double>>,
    testTag: String
) {
    val accents = LocalSarimAccents.current
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = accents.softPillFill.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 42.dp)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    color = accents.emerald
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${rows.size}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Toggle $title",
                        tint = accents.emerald,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                    Spacer(Modifier.height(2.dp))
                    rows.forEach { (cust, qty) ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                cust,
                                style = MaterialTheme.typography.bodySmall,
                                color = accents.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                InputGuards.formatKg(qty),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
