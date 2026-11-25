package com.gullzubair.sarim.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.OverviewEngine
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SectionHeader
import com.gullzubair.sarim.ui.components.SimpleBarChart
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import kotlin.math.abs
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
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val masked = session.restrictMoney || snapshot.settings.hideSensitiveMoney
    var windowMonths by remember { mutableIntStateOf(6) }
    var activePanel by remember { mutableStateOf("Sales") }

    val summary = remember(snapshot, windowMonths) {
        OverviewEngine.computeOverview(snapshot, windowMonths)
    }
    val health = remember(snapshot) { FinanceEngine.calculateEconomicHealth(snapshot) }
    val storeSnapshots = remember(snapshot) { FinanceEngine.computeStoreStockSnapshots(snapshot) }
    val unitPools = remember(snapshot.stores, snapshot.formulas, snapshot.factory, snapshot.production) {
        FormulaEngine.buildUnitPools(snapshot.stores, snapshot.formulas, snapshot.factory, snapshot.production)
    }
    val pendingCount = remember(snapshot.pendingApprovals) {
        snapshot.pendingApprovals.count { it.status == "pending" }
    }

    val monthlyRevSeries = remember(summary.months) { summary.months.map { it.salesValue } }
    val monthlyExpSeries = remember(summary.months) { summary.months.map { it.expenses } }
    val monthlyProdSeries = remember(summary.months) { summary.months.map { it.prodKg } }

    val revDeltaPct = remember(monthlyRevSeries) {
        if (monthlyRevSeries.size >= 2 && monthlyRevSeries.first() > 0.01) {
            (((monthlyRevSeries.last() - monthlyRevSeries.first()) / monthlyRevSeries.first()) * 100.0).roundToInt()
        } else 0
    }
    val expDeltaPct = remember(monthlyExpSeries) {
        if (monthlyExpSeries.size >= 2 && monthlyExpSeries.first() > 0.01) {
            (((monthlyExpSeries.last() - monthlyExpSeries.first()) / monthlyExpSeries.first()) * 100.0).roundToInt()
        } else 0
    }
    val prodDeltaPct = remember(monthlyProdSeries) {
        if (monthlyProdSeries.size >= 2 && monthlyProdSeries.first() > 0.01) {
            (((monthlyProdSeries.last() - monthlyProdSeries.first()) / monthlyProdSeries.first()) * 100.0).roundToInt()
        } else 0
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today: ${InputGuards.formatKg(summary.todayProdKg)} • ${InputGuards.formatMoney(summary.todaySalesValue, currency, masked)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = accents.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
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

        // Mirrored Sparkline KPI Cards
        item {
            OverviewSparkCard(
                label = "Sales Revenue",
                value = InputGuards.formatMoney(summary.salesValue, currency, masked),
                subtitle = "Cash In ${InputGuards.formatMoney(summary.cashIn, currency, masked)}",
                deltaPct = revDeltaPct,
                series = monthlyRevSeries,
                lineColor = accents.emerald
            )
        }
        item {
            OverviewSparkCard(
                label = "Produced & Stock",
                value = InputGuards.formatKg(summary.prodKg),
                subtitle = "Profit ${InputGuards.formatMoney(summary.netOperatingProfit, currency, masked)}",
                deltaPct = prodDeltaPct,
                series = monthlyProdSeries,
                lineColor = accents.cyan
            )
        }
        item {
            OverviewSparkCard(
                label = "Operating Expenses",
                value = InputGuards.formatMoney(summary.expenses, currency, masked),
                subtitle = "Open Owed ${InputGuards.formatMoney(summary.owedAging.total, currency, masked)}",
                deltaPct = expDeltaPct,
                series = monthlyExpSeries,
                lineColor = accents.gold
            )
        }

        // Cash In / Out / Activity Multi-Curve Card
        item {
            SarimCard(contentPadding = PaddingValues(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OverviewMetricPill("Cash in", InputGuards.formatMoney(summary.cashIn, currency, masked), accents.emerald, true)
                    OverviewMetricPill("Out", InputGuards.formatMoney(summary.cashOut + summary.expenses, currency, masked), accents.danger, false)
                    OverviewMetricPill("Sales", "${summary.salesCount}", accents.purple, false)
                }
                Spacer(Modifier.height(14.dp))
                val revPts = monthlyRevSeries.ifEmpty { listOf(0.0, 0.0, 0.0) }
                val expPts = monthlyExpSeries.ifEmpty { listOf(0.0, 0.0, 0.0) }
                val actPts = monthlyProdSeries.ifEmpty { listOf(0.0, 0.0, 0.0) }
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
                    summary.months.forEach { b ->
                        Text(b.label, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
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
                if (summary.owedAging.count > 0) {
                    item {
                        SarimCard {
                            Text("Credit Aging (${summary.owedAging.count} open bills)", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("0–30d: ${InputGuards.formatMoney(summary.owedAging.d30, currency, masked)}", style = MaterialTheme.typography.bodySmall)
                                Text("31–60d: ${InputGuards.formatMoney(summary.owedAging.d60, currency, masked)}", style = MaterialTheme.typography.bodySmall, color = accents.gold)
                                Text("60d+: ${InputGuards.formatMoney(summary.owedAging.d90, currency, masked)}", style = MaterialTheme.typography.bodySmall, color = accents.danger)
                            }
                            if (summary.owedAging.topCustomers.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                summary.owedAging.topCustomers.take(5).forEach { (cust, amt, age) ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("$cust (${age}d)", style = MaterialTheme.typography.bodySmall)
                                        Text(InputGuards.formatMoney(amt, currency, masked), style = MaterialTheme.typography.labelMedium, color = accents.danger)
                                    }
                                }
                            }
                        }
                    }
                }
                if (summary.profitAnalysis.losingCount > 0) {
                    item {
                        SarimCard(accentBorder = accents.danger.copy(alpha = 0.6f)) {
                            Text(
                                "Loss Sales Alert (${summary.profitAnalysis.losingCount})",
                                style = MaterialTheme.typography.titleSmall,
                                color = accents.danger
                            )
                            Text(
                                "Net loss ${InputGuards.formatMoney(summary.profitAnalysis.losingSum, currency, masked)} • Avg sale ${InputGuards.formatMoney(summary.profitAnalysis.avgPrice, currency, masked)} vs cost ${InputGuards.formatMoney(summary.profitAnalysis.avgCost, currency, masked)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = accents.textMuted
                            )
                        }
                    }
                }
            }

            "Production" -> {
                item { SectionHeader("Store Inventory & Pricing", "Produced + returned - sold") }
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
                items(unitPools, key = { it.key }) { pool ->
                    SarimCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(pool.label, style = MaterialTheme.typography.titleMedium)
                            Text("${InputGuards.fmtNum(pool.availableUnits)} units free", color = accents.purple, style = MaterialTheme.typography.labelLarge)
                        }
                        Text(
                            "Produced ${InputGuards.fmtNum(pool.producedUnits)} • Used ${InputGuards.fmtNum(pool.usedUnits)} • Cost/unit ${InputGuards.formatMoney(pool.avgUnitCost, currency, masked)}",
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
                            if (snapshot.profile.isAdmin) {
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
                        }
                        Spacer(Modifier.height(10.dp))
                        val lines = listOf(
                            "Cash & Bank Channels" to health.cashInHand,
                            "Raw Material Stock" to health.rawMaterialValue,
                            "Formula Unit Pools" to health.formulaUnitsValue,
                            "Finished Store Stock" to health.finishedStockValue,
                            "Customer Receivables" to health.customerReceivables,
                            "Entity Receivables" to health.entityReceivables,
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
                val materials = FinanceEngine.materialRecords(snapshot.inventory).sortedBy { it.name.lowercase() }
                if (materials.isEmpty()) {
                    item { EmptyStateCard("No raw materials tracked", "Record a Raw Material Purchase from Factory or Drawer.") }
                } else {
                    items(materials, key = { it.id }) { item ->
                        val low = item.quantity <= 0.0
                        SarimCard(accentBorder = if (low) accents.gold else null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = if (low) accents.gold else accents.emerald)
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                                        val unitDesc = if (item.purchaseUnitName.isNotBlank()) " (${item.purchaseUnitName} = ${InputGuards.fmtNum(item.conversionFactor)} kg)" else ""
                                        Text("Avg ${InputGuards.formatMoney(item.cost, currency, masked)}/kg$unitDesc", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                    }
                                }
                                Text(
                                    InputGuards.formatKg(item.quantity),
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
                                    if (summary.audit.findings.isEmpty()) "Scanned ${summary.audit.scannedTotal} records — all checks passed"
                                    else "${summary.audit.errors} errors • ${summary.audit.warnings} warnings across ${summary.audit.scannedTotal} records",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted
                                )
                            }
                            if (snapshot.profile.isAdmin) {
                                Surface(
                                    onClick = { vm.auditRepriceZeroCostSales() },
                                    shape = RoundedCornerShape(14.dp),
                                    color = accents.softPillFill,
                                    border = BorderStroke(1.dp, accents.emerald),
                                    modifier = Modifier.testTag("overview_reprice_sales_btn")
                                ) {
                                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Build, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Reprice Zero-Cost", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                                    }
                                }
                            }
                        }
                    }
                }
                if (summary.audit.findings.isEmpty()) {
                    item {
                        SarimCard(accentBorder = accents.emerald) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accents.emerald)
                                Spacer(Modifier.width(10.dp))
                                Text("Zero discrepancies detected across stores, inventory, sales, and collections.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else {
                    items(summary.audit.findings, key = { it.id }) { finding ->
                        val color = if (finding.severity == "error") accents.danger else accents.gold
                        SarimCard(accentBorder = color) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = color)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${finding.title} (${finding.label}${if (finding.date.isNotBlank()) " • ${finding.date}" else ""})", style = MaterialTheme.typography.titleSmall, color = color)
                                    Text(finding.reasons.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                if (summary.recentActivity.isEmpty()) {
                    item { EmptyStateCard("No recent activity", "Log production, sales, payments, or expenses to see the feed.") }
                } else {
                    items(summary.recentActivity.size) { idx ->
                        val act = summary.recentActivity[idx]
                        val toneColor = when (act.tone) {
                            "good" -> accents.emerald
                            "bad" -> accents.danger
                            "warn" -> accents.gold
                            else -> accents.cyan
                        }
                        SarimCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${act.kind} • ${act.title}", style = MaterialTheme.typography.titleSmall)
                                    if (act.sub.isNotBlank()) {
                                        Text(act.sub, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                    }
                                }
                                Text(if (masked && act.kind != "Production") "$currency ••••" else act.amount, color = toneColor, style = MaterialTheme.typography.labelLarge)
                            }
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
                    text = if (deltaPct >= 0) "▲ $deltaPct%" else "▼ ${abs(deltaPct)}%",
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
