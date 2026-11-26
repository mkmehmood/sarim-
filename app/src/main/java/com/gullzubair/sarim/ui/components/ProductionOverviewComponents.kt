package com.gullzubair.sarim.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.ProductionAnalyticsEngine
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
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
    val palette = OverviewPalette()
    val periodLabel = if (selectedPeriod == ProductionAnalyticsEngine.OverviewPeriod.ALL) "All time" else selectedPeriod.label

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SarimCard(contentPadding = PaddingValues(16.dp)) {
            Text(
                text = "All Stores Overview",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accents.emerald
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
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) accents.softPillFill else accents.inputFill,
                        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("prod_overview_period_${period.key}")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = period.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) accents.emerald else accents.textMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Per-store overview cards (matches web order: stores + total combined)
        overview.stores.forEachIndexed { idx, st ->
            val storeAccent = palette[idx % palette.size]
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
                        shape = RoundedCornerShape(16.dp),
                        color = storeAccent.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, storeAccent.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = st.storeName,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
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
                    SxKpiTile("Produced", InputGuards.formatKg(st.producedKg), accents.emerald, Modifier.weight(1f))
                    if (st.returnsKg > 0.0) {
                        SxKpiTile("Returns received", InputGuards.formatKg(st.returnsKg), accents.gold, Modifier.weight(1f))
                    } else {
                        SxKpiTile("Sold (Sales tab)", InputGuards.formatKg(st.soldKg), accents.danger, Modifier.weight(1f))
                    }
                }
                if (st.returnsKg > 0.0) {
                    Spacer(Modifier.height(8.dp))
                    SxKpiTile("Sold (Sales tab)", InputGuards.formatKg(st.soldKg), accents.danger, Modifier.fillMaxWidth())
                }

                if (st.soldByCustomer.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SxCustomerBreakdown(
                        title = "Sold by customer",
                        rows = st.soldByCustomer,
                        testTag = "store_cust_breakdown_${st.storeKey}"
                    )
                }

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                Spacer(Modifier.height(10.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SxKpiTile("Formula units", InputGuards.fmtNum(st.formulaUnits), accents.gold, Modifier.weight(1f))
                    SxKpiTile("Formula cost", InputGuards.formatMoney(st.formulaCost, currency, masked), accents.danger, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SxKpiTile("Total value", InputGuards.formatMoney(st.totalValue, currency, masked), accents.emerald, Modifier.weight(1f))
                    SxKpiTile(
                        label = "Net profit",
                        value = InputGuards.formatMoney(st.netProfit, currency, masked),
                        toneColor = if (st.netProfit >= 0.0) accents.emerald else accents.danger,
                        modifier = Modifier.weight(1f),
                        sub = st.marginPct?.let { "${InputGuards.fmtNum(it, 1)}% margin" }
                    )
                }
            }
        }

        // Total Combined Card
        val comb = overview.combined
        SarimCard(
            accentBorder = accents.emerald.copy(alpha = 0.55f),
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total combined",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = accents.emerald
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
                SxKpiTile("Total value", InputGuards.formatMoney(comb.totalValue, currency, masked), accents.emerald, Modifier.weight(1f))
                SxKpiTile("Total cost", InputGuards.formatMoney(comb.totalCost, currency, masked), accents.danger, Modifier.weight(1f))
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
                SxKpiTile("Fresh production", InputGuards.formatKg(comb.producedKg), accents.emerald, Modifier.weight(1f))
                SxKpiTile("Total sold", InputGuards.formatKg(comb.soldKg), accents.danger, Modifier.weight(1f))
            }
            if (comb.returnsKg > 0.0) {
                Spacer(Modifier.height(8.dp))
                SxKpiTile("Total returns", InputGuards.formatKg(comb.returnsKg), accents.gold, Modifier.fillMaxWidth())
            }

            if (comb.soldByCustomer.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SxCustomerBreakdown(
                    title = "Sold by customer",
                    rows = comb.soldByCustomer,
                    testTag = "combined_cust_breakdown"
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SxKpiTile("Formula units", InputGuards.fmtNum(comb.formulaUnits), accents.gold, Modifier.weight(1f))
                SxKpiTile("Formula cost", InputGuards.formatMoney(comb.formulaCost, currency, masked), accents.danger, Modifier.weight(1f))
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
    val palette = OverviewPalette()
    var activeIdx by remember(points, selectedMetric) { mutableStateOf<Int?>(null) }

    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Text(
            text = "Compare Stores By:",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
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
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) accents.softPillFill else accents.inputFill,
                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .testTag("prod_cmp_metric_${metric.key}")
                ) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            metric.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) accents.emerald else accents.textMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
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
                    .height(150.dp)
                    .pointerInput(points.size) {
                        detectTapGestures { offset ->
                            if (size.width > 0 && points.isNotEmpty()) {
                                val idx = ((offset.x / size.width.toFloat()) * points.size).toInt().coerceIn(0, points.lastIndex)
                                activeIdx = if (activeIdx == idx) null else idx
                            }
                        }
                    }
            ) {
                val count = points.size.coerceAtLeast(1)
                val slotW = size.width / count
                val barW = (slotW * 0.55f).coerceIn(18f, 76f)
                val baselineY = size.height - 8f
                drawLine(
                    color = accents.borderSubtle,
                    start = Offset(0f, baselineY),
                    end = Offset(size.width, baselineY),
                    strokeWidth = 2f
                )
                points.forEachIndexed { idx, pt ->
                    val frac = (max(0.0, pt.value) / maxAbs).toFloat().coerceIn(0.03f, 1f)
                    val barH = frac * (baselineY - 12f)
                    val left = idx * slotW + (slotW - barW) / 2f
                    val color = palette[idx % palette.size]
                    drawRoundRect(
                        color = if (activeIdx == null || activeIdx == idx) color else color.copy(alpha = 0.45f),
                        topLeft = Offset(left, baselineY - barH),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                points.forEachIndexed { idx, pt ->
                    val color = palette[idx % palette.size]
                    val formatted = if (selectedMetric == ProductionAnalyticsEngine.ComparisonMetric.WEIGHT) {
                        InputGuards.formatKg(pt.value)
                    } else {
                        InputGuards.formatMoney(pt.value, currency, masked)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeIdx = if (activeIdx == idx) null else idx },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = pt.storeName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = color,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = formatted,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SarimCard(contentPadding = PaddingValues(16.dp)) {
            Text(
                text = "Analytics",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) accents.softPillFill else accents.inputFill,
                        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 40.dp)
                            .testTag("prod_mfg_period_${period.key}")
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = period.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) accents.emerald else accents.textMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Production Quantity ($modeLabel)",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(10.dp))

            if (analytics.labels.isEmpty() || analytics.quantities.all { it == 0.0 }) {
                Text("Nothing produced in this period.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            } else {
                val maxQ = analytics.quantities.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .pointerInput(analytics.quantities.size) {
                            detectTapGestures { offset ->
                                if (size.width > 0 && analytics.quantities.isNotEmpty()) {
                                    val idx = ((offset.x / size.width.toFloat()) * analytics.quantities.size)
                                        .toInt().coerceIn(0, analytics.quantities.lastIndex)
                                    activeBarIdx = if (activeBarIdx == idx) null else idx
                                }
                            }
                        }
                ) {
                    val count = analytics.quantities.size.coerceAtLeast(1)
                    val slotW = size.width / count
                    val barW = (slotW * 0.62f).coerceIn(5f, 44f)
                    val baselineY = size.height - 6f
                    drawLine(
                        color = accents.borderSubtle,
                        start = Offset(0f, baselineY),
                        end = Offset(size.width, baselineY),
                        strokeWidth = 2f
                    )
                    analytics.quantities.forEachIndexed { idx, q ->
                        val frac = if (q > 0.0) (q / maxQ).toFloat().coerceIn(0.04f, 1f) else 0f
                        if (frac > 0f) {
                            val h = frac * (baselineY - 8f)
                            val left = idx * slotW + (slotW - barW) / 2f
                            drawRoundRect(
                                color = if (activeBarIdx == idx) accents.emerald else accents.cyan.copy(alpha = 0.85f),
                                topLeft = Offset(left, baselineY - h),
                                size = Size(barW, h),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                val stride = when {
                    analytics.labels.size > 20 -> 5
                    analytics.labels.size > 12 -> 3
                    else -> 1
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                            color = accents.inputFill,
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
        shape = RoundedCornerShape(14.dp),
        color = accents.softPillFill
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = accents.emerald,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
                    shape = RoundedCornerShape(14.dp),
                    color = chipColor.copy(alpha = 0.14f)
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
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
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
                .height(8.dp)
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
                        text = "${seg.label} ${formatValue(seg.value)} ($pct%)",
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
private fun SxKpiTile(
    label: String,
    value: String,
    toneColor: Color,
    modifier: Modifier = Modifier,
    sub: String? = null
) {
    val accents = LocalSarimAccents.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = accents.inputFill,
        border = BorderStroke(1.dp, accents.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accents.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = toneColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!sub.isNullOrBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = accents.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
        color = accents.inputFill,
        border = BorderStroke(1.dp, accents.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                        contentDescription = "Toggle $title",
                        tint = accents.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                Text("${rows.size}", style = MaterialTheme.typography.labelSmall, color = accents.emerald)
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
