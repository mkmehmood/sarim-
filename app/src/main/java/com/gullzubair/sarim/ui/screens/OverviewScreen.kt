package com.gullzubair.sarim.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.OverviewEngine
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SectionHeader
import com.gullzubair.sarim.ui.components.SimpleBarChart
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import kotlin.math.roundToInt

@Composable
fun OverviewScreen(
    snapshot: WorkspaceSnapshot,
    vm: SarimViewModel,
    onOpenApprovals: () -> Unit,
    onOpenDailyLedger: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney
    var windowMonths by remember { mutableIntStateOf(6) }
    var activePanel by remember { mutableStateOf("Sales") }

    val summary = remember(snapshot, windowMonths) {
        OverviewEngine.computeOverview(snapshot, windowMonths)
    }
    val health = remember(snapshot) { FinanceEngine.calculateEconomicHealth(snapshot) }
    val storeSnapshots = remember(snapshot) { FinanceEngine.computeStoreStockSnapshots(snapshot) }
    val unitPools = remember(snapshot) { FinanceEngine.computeUnitPools(snapshot) }
    val pendingCount = remember(snapshot.pendingApprovals) { snapshot.pendingApprovals.count { it.status == "pending" } }

    val monthlyProdSeries = remember(snapshot.production, windowMonths) {
        snapshot.production.filter { !it.recycled && !it.transfer }
            .groupBy { it.date.take(7) }
            .entries.sortedBy { it.key }
            .takeLast(windowMonths)
            .map { it.key to it.value.sumOf { rec -> rec.netWeight } }
    }

    val revDeltaPct = remember(summary.monthlyRevenueSeries) {
        val s = summary.monthlyRevenueSeries
        if (s.size >= 2 && s.first().second > 0.01) (((s.last().second - s.first().second) / s.first().second) * 100.0).roundToInt() else 0
    }
    val expDeltaPct = remember(summary.monthlyExpenseSeries) {
        val s = summary.monthlyExpenseSeries
        if (s.size >= 2 && s.first().second > 0.01) (((s.last().second - s.first().second) / s.first().second) * 100.0).roundToInt() else 0
    }
    val prodDeltaPct = remember(monthlyProdSeries) {
        if (monthlyProdSeries.size >= 2 && monthlyProdSeries.first().second > 0.01) (((monthlyProdSeries.last().second - monthlyProdSeries.first().second) / monthlyProdSeries.first().second) * 100.0).roundToInt() else 0
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 6, 12).forEach { m ->
                        val selected = windowMonths == m
                        Surface(
                            onClick = { windowMonths = m },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) accents.softPillFill else accents.inputFill,
                            border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                            modifier = Modifier.testTag("overview_window_${m}m")
                        ) {
                            Text(
                                text = "${m}M",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) accents.emerald else accents.textMuted,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }

        if (pendingCount > 0) {
            item {
                SarimCard(accentBorder = accents.gold) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("$pendingCount Pending Approvals", style = MaterialTheme.typography.titleMedium, color = accents.gold)
                            Text("Team submissions waiting for review", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Surface(
                            onClick = onOpenApprovals,
                            shape = RoundedCornerShape(14.dp),
                            color = accents.gold.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, accents.gold),
                            modifier = Modifier.testTag("overview_open_approvals_btn")
                        ) {
                            Text("Review", style = MaterialTheme.typography.labelLarge, color = accents.gold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }

        // Mirrored Sparkline KPI Cards (matching screenshot 115730)
        item {
            OverviewSparkCard(
                label = "Sales Revenue",
                value = InputGuards.formatMoney(summary.totalSalesRevenue, currency, masked),
                subtitle = "Cash ${InputGuards.formatMoney(summary.totalCashCollected, currency, masked)}",
                deltaPct = revDeltaPct,
                series = summary.monthlyRevenueSeries.map { it.second },
                lineColor = accents.emerald
            )
        }
        item {
            OverviewSparkCard(
                label = "Produced & Sold",
                value = InputGuards.formatKg(summary.totalProducedKg),
                subtitle = "Profit ${InputGuards.formatMoney(summary.netOperatingProfit, currency, masked)}",
                deltaPct = prodDeltaPct,
                series = monthlyProdSeries.map { it.second },
                lineColor = accents.cyan
            )
        }
        item {
            OverviewSparkCard(
                label = "Operating Expenses",
                value = InputGuards.formatMoney(summary.totalOperatingExpenses, currency, masked),
                subtitle = "Open Credits ${InputGuards.formatMoney(summary.openCredits, currency, masked)}",
                deltaPct = expDeltaPct,
                series = summary.monthlyExpenseSeries.map { it.second },
                lineColor = accents.gold
            )
        }

        // Mirrored Cash in / Out / Activity Multi-Curve Card
        item {
            val cashInTotal = summary.totalCashCollected
            val cashOutTotal = summary.totalOperatingExpenses
            SarimCard(contentPadding = PaddingValues(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OverviewMetricPill("Cash in", InputGuards.formatMoney(cashInTotal, currency, masked), accents.emerald, true)
                    OverviewMetricPill("Out", InputGuards.formatMoney(cashOutTotal, currency, masked), accents.danger, false)
                    OverviewMetricPill("Activity", "${snapshot.sales.count { !it.recycled }}", accents.purple, false)
                }
                Spacer(Modifier.height(14.dp))
                val revPts = summary.monthlyRevenueSeries.map { it.second }.ifEmpty { listOf(0.0, 0.0, 0.0) }
                val expPts = summary.monthlyExpenseSeries.map { it.second }.ifEmpty { listOf(0.0, 0.0, 0.0) }
                val actPts = monthlyProdSeries.map { it.second }.ifEmpty { listOf(0.0, 0.0, 0.0) }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                ) {
                    val allMax = (revPts + expPts + actPts).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                    fun drawCurve(values: List<Double>, color: Color) {
                        val pts = if (values.size == 1) listOf(values.first(), values.first()) else values
                        val stepX = size.width / (pts.size - 1).coerceAtLeast(1)
                        val coords = pts.mapIndexed { idx, v ->
                            val x = idx * stepX
                            val y = size.height - 14f - ((v / allMax).toFloat() * (size.height - 28f))
                            Offset(x, y)
                        }
                        val path = Path().apply {
                            moveTo(coords.first().x, coords.first().y)
                            for (i in 0 until coords.lastIndex) {
                                val p0 = coords[i]
                                val p1 = coords[i + 1]
                                val cx = (p0.x + p1.x) / 2f
                                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                            }
                        }
                        drawPath(path = path, color = color, style = Stroke(width = 6f, cap = StrokeCap.Round))
                        val last = coords.last()
                        drawCircle(color = color, radius = 10f, center = last)
                        drawCircle(color = Color.White, radius = 5f, center = last)
                    }
                    drawCurve(revPts, accents.emerald)
                    drawCurve(actPts, accents.purple)
                    drawCurve(expPts, accents.gold)
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    summary.monthlyRevenueSeries.forEach { (ym, _) ->
                        Text(ym.takeLast(5), style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                    }
                }
            }
        }

        // Panel Switcher Pills
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Sales", "Production", "Money", "Stock", "Checks", "Activity").forEach { panel ->
                    val selected = activePanel == panel
                    Surface(
                        onClick = { activePanel = panel },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) accents.softPillFill else accents.inputFill,
                        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                        modifier = Modifier.testTag("overview_panel_${panel.lowercase()}")
                    ) {
                        Text(
                            panel,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) accents.emerald else accents.textMuted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }

        when (activePanel) {
            "Sales" -> {
                item {
                    SimpleBarChart(
                        title = "Sales by Store",
                        items = summary.storeSalesBreakdown,
                        barColor = accents.emerald,
                        currency = currency,
                        masked = masked
                    )
                }
            }

            "Production" -> {
                item { SectionHeader("Store Inventory & Pricing", "Net produced + rep returns - sold") }
                if (storeSnapshots.isEmpty()) {
                    item { EmptyStateCard("No stores yet", "Add stores from Store Manager in Settings.") }
                } else {
                    items(storeSnapshots, key = { it.storeKey }) { s ->
                        SarimCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(s.storeName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    InputGuards.formatKg(s.availableKg),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (s.availableKg >= 0) accents.emerald else accents.danger
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Produced: ${InputGuards.formatKg(s.producedKg)} • Sold: ${InputGuards.formatKg(s.soldKg)} • Avg Cost: ${InputGuards.formatMoney(s.avgCostPerKg, currency, masked)}/kg",
                                style = MaterialTheme.typography.bodySmall,
                                color = accents.textMuted
                            )
                        }
                    }
                }
                item { SectionHeader("Formula Unit Pools") }
                items(unitPools, key = { it.formulaId }) { pool ->
                    SarimCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(pool.formulaName, style = MaterialTheme.typography.titleMedium)
                            Text("${InputGuards.formatNum(pool.availableUnits)} units free", color = accents.purple, style = MaterialTheme.typography.labelLarge)
                        }
                        Text(
                            "Produced ${InputGuards.formatNum(pool.producedUnits)} • Used ${InputGuards.formatNum(pool.usedUnits)} • Cost/unit ${InputGuards.formatMoney(pool.avgCostPerUnit, currency, masked)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = accents.textMuted
                        )
                    }
                }
            }

            "Money" -> {
                item {
                    SarimCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Economic Health Balance Sheet", style = MaterialTheme.typography.titleMedium)
                            Surface(
                                onClick = onOpenDailyLedger,
                                shape = RoundedCornerShape(14.dp),
                                color = accents.softPillFill,
                                border = BorderStroke(1.dp, accents.emerald),
                                modifier = Modifier.testTag("overview_open_ledger_btn")
                            ) {
                                Text("Cashbook", style = MaterialTheme.typography.labelLarge, color = accents.emerald, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        val lines = listOf(
                            "Cash & Bank Channels" to health.cashInHand,
                            "Raw Material Stock" to health.rawMaterialValue,
                            "Formula Unit Pools" to health.formulaUnitsValue,
                            "Finished Store Stock" to health.finishedStockValue,
                            "Customer Receivables" to health.customerReceivables,
                            "Supplier & Entity Payables" to -health.totalLiabilities,
                            "Net Working Capital" to health.netWorkingCapital
                        )
                        lines.forEach { (label, amt) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    InputGuards.formatMoney(amt, currency, masked),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (amt >= 0) accents.emerald else accents.danger
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Current Liquidity Ratio: ${health.currentRatio}x",
                            style = MaterialTheme.typography.labelMedium,
                            color = accents.cyan
                        )
                    }
                }
            }

            "Stock" -> {
                val activeInv = snapshot.inventory.filter { !it.recycled }
                if (activeInv.isEmpty()) {
                    item { EmptyStateCard("No raw materials tracked", "Record a Raw Material Purchase from Factory or Drawer.") }
                } else {
                    items(activeInv, key = { it.id }) { item ->
                        val low = item.minThreshold > 0 && item.stockQty <= item.minThreshold
                        SarimCard(accentBorder = if (low) accents.gold else null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = if (low) accents.gold else accents.emerald)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                                        Text("Avg ${InputGuards.formatMoney(item.avgCostPerKg, currency, masked)}/kg", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                    }
                                }
                                Text(
                                    InputGuards.formatKg(item.stockQty),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (low) accents.gold else accents.emerald
                                )
                            }
                        }
                    }
                }
            }

            "Checks" -> {
                item {
                    SarimCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Workspace Integrity Audit", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (summary.findings.isEmpty()) "All ledger mirrors & stock balances passed" else "${summary.findings.size} findings detected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted
                                )
                            }
                            Surface(
                                onClick = { vm.repairLedgerMirrors() },
                                shape = RoundedCornerShape(14.dp),
                                color = accents.softPillFill,
                                border = BorderStroke(1.dp, accents.emerald),
                                modifier = Modifier.testTag("overview_repair_links_btn")
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Build, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Fix Links", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                                }
                            }
                        }
                    }
                }
                if (summary.findings.isEmpty()) {
                    item {
                        SarimCard(accentBorder = accents.emerald) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accents.emerald)
                                Spacer(Modifier.width(10.dp))
                                Text("Zero discrepancies detected across stores, inventory, and cashbook.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    items(summary.findings, key = { it.id }) { finding ->
                        val color = if (finding.severity == "critical") accents.danger else accents.gold
                        SarimCard(accentBorder = color) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = color)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(finding.title, style = MaterialTheme.typography.titleSmall, color = color)
                                    Text(finding.detail, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                val recentSales = snapshot.sales.filter { !it.recycled }.take(5)
                val recentProd = snapshot.production.filter { !it.recycled }.take(5)
                item { SectionHeader("Recent Production") }
                items(recentProd, key = { it.id }) { p ->
                    SarimCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${p.storeName} (${p.date})", style = MaterialTheme.typography.titleSmall)
                            Text(InputGuards.formatKg(p.netWeight), color = accents.emerald, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                item { SectionHeader("Recent Sales & Collections") }
                items(recentSales, key = { it.id }) { s ->
                    SarimCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${s.customerName} (${s.date})", style = MaterialTheme.typography.titleSmall)
                            Text(InputGuards.formatMoney(s.cashReceived, currency, masked), color = accents.cyan, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun OverviewSparkCard(
    label: String,
    value: String,
    subtitle: String,
    deltaPct: Int,
    series: List<Double>,
    lineColor: Color
) {
    val accents = LocalSarimAccents.current
    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = accents.textMuted)
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    fontFamily = JakartaFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = accents.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(0.9f)
            ) {
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                ) {
                    val pts = if (series.size >= 2) series else listOf(0.5, 0.8, 0.65)
                    val maxV = pts.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                    val minV = pts.minOrNull() ?: 0.0
                    val span = (maxV - minV).coerceAtLeast(1.0)
                    val stepX = size.width / (pts.size - 1).coerceAtLeast(1)
                    val coords = pts.mapIndexed { idx, v ->
                        Offset(idx * stepX, size.height - 6f - (((v - minV) / span).toFloat() * (size.height - 12f)))
                    }
                    val path = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        for (i in 0 until coords.lastIndex) {
                            val p0 = coords[i]
                            val p1 = coords[i + 1]
                            val cx = (p0.x + p1.x) / 2f
                            cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                        }
                    }
                    drawPath(path = path, color = lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
                }
                Text(
                    text = if (deltaPct >= 0) "▲ $deltaPct%" else "▼ ${kotlin.math.abs(deltaPct)}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (deltaPct >= 0) accents.emerald else accents.textMuted
                )
            }
        }
    }
}

@Composable
private fun OverviewMetricPill(
    label: String,
    value: String,
    valueColor: Color,
    highlighted: Boolean
) {
    val accents = LocalSarimAccents.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (highlighted) accents.softPillFill else accents.inputFill,
        border = BorderStroke(1.dp, if (highlighted) accents.emerald.copy(alpha = 0.5f) else accents.borderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(value, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = valueColor, maxLines = 1)
        }
    }
}
