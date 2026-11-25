package com.gullzubair.sarim.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.OverviewEngine
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import kotlin.math.max

@Composable
fun OverviewSalesPanel(
    summary: OverviewEngine.OverviewKpiSummary,
    currency: String,
    masked: Boolean
) {
    val accents = LocalSarimAccents.current
    val palette = OverviewPalette()
    val monthLabels = remember(summary.months) { summary.months.map { it.label } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OverviewInteractiveLineChart(
            title = "Money trends",
            labels = monthLabels,
            series = listOf(
                OverviewSeries("Sales", summary.months.map { it.salesValue }, accents.emerald),
                OverviewSeries("Net profit", summary.months.map { it.prodProfit }, accents.purple),
                OverviewSeries("Sales profit", summary.months.map { it.salesProfit }, accents.cyan),
                OverviewSeries("Cash in", summary.months.map { it.cashIn }, Color(0xFF0D9488)),
                OverviewSeries("Cash out", summary.months.map { it.cashOut }, accents.danger),
                OverviewSeries("Expenses", summary.months.map { it.expenses }, accents.gold)
            ),
            currency = currency,
            masked = masked,
            showArea = true
        )

        if (summary.months.any { it.prodProfit != 0.0 }) {
            OverviewNetProfitBarChart(
                title = "Net profit by month",
                labels = monthLabels,
                values = summary.months.map { it.prodProfit },
                currency = currency,
                masked = masked
            )
        }

        if (summary.salesByCustomerTrend.any { (_, vals) -> vals.any { it != 0.0 } }) {
            OverviewInteractiveLineChart(
                title = "Sales by customer over time",
                labels = monthLabels,
                series = summary.salesByCustomerTrend.mapIndexed { i, (n, v) -> OverviewSeries(n, v, palette[i % palette.size]) },
                currency = currency,
                masked = masked
            )
        }

        if (summary.salesByStoreTrend.any { (_, vals) -> vals.any { it != 0.0 } }) {
            OverviewInteractiveLineChart(
                title = "Sales by store over time",
                labels = monthLabels,
                series = summary.salesByStoreTrend.mapIndexed { i, (n, v) -> OverviewSeries(n, v, palette[i % palette.size]) },
                currency = currency,
                masked = masked
            )
        }

        val inKg = summary.prodKg + summary.returnsKg
        val moveRows = listOf(
            "Sold" to summary.soldKg,
            "Remaining" to max(0.0, inKg - summary.soldKg),
            "Production + returns in" to inKg
        ).filter { it.second > 0.0 }
        OverviewRankedBarsCard(
            title = "Stock movement (kg)",
            items = moveRows,
            barColor = accents.gold,
            unitSuffix = " kg",
            showSharePct = false,
            isMoneyMetric = false
        )

        OverviewRankedBarsCard(
            title = "Sold by customer (kg)",
            items = summary.topCustomersKg,
            barColor = accents.gold,
            unitSuffix = " kg",
            isMoneyMetric = false
        )

        if (summary.owedAging.count > 0) {
            SarimCard {
                Text("Owed to you (${summary.owedAging.count} open • oldest ${summary.owedAging.oldestDays}d)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("≤30d: ${InputGuards.formatMoney(summary.owedAging.d30, currency, masked)}", style = MaterialTheme.typography.bodySmall, color = accents.emerald)
                    Text("31–60d: ${InputGuards.formatMoney(summary.owedAging.d60, currency, masked)}", style = MaterialTheme.typography.bodySmall, color = accents.gold)
                    Text("60d+: ${InputGuards.formatMoney(summary.owedAging.d90, currency, masked)}", style = MaterialTheme.typography.bodySmall, color = accents.danger)
                }
                if (summary.owedAging.topCustomers.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    summary.owedAging.topCustomers.forEach { (cust, amt, age) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("$cust (${age}d)", style = MaterialTheme.typography.bodySmall)
                            Text(InputGuards.formatMoney(amt, currency, masked), style = MaterialTheme.typography.labelMedium, color = accents.danger)
                        }
                    }
                }
            }
        }

        OverviewRankedBarsCard("Top customers", summary.topCustomersValue, accents.emerald, currency, masked)
        if (summary.storeSalesBreakdown.isNotEmpty()) {
            OverviewRankedBarsCard("Sales by store", summary.storeSalesBreakdown, accents.emerald, currency, masked)
        }
        if (summary.cashVsCredit.isNotEmpty()) {
            OverviewDonutSummaryCard("Cash and credit sales", summary.cashVsCredit, currency, masked)
        }
    }
}

@Composable
fun OverviewProdPanel(
    summary: OverviewEngine.OverviewKpiSummary,
    storeSnapshots: List<FinanceEngine.StoreStockSnapshot>,
    unitPools: List<FormulaEngine.UnitPool>,
    currency: String,
    masked: Boolean
) {
    val accents = LocalSarimAccents.current
    val palette = OverviewPalette()
    val monthLabels = remember(summary.months) { summary.months.map { it.label } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OverviewInteractiveLineChart(
            title = "Production (kg)",
            labels = monthLabels,
            series = listOf(OverviewSeries("Production", summary.months.map { it.prodKg }, accents.cyan)),
            unitSuffix = " kg",
            showArea = true
        )

        if (summary.prodByFormulaTrend.any { (_, v) -> v.any { it != 0.0 } }) {
            OverviewInteractiveLineChart(
                title = "Production by formula over time",
                labels = monthLabels,
                series = summary.prodByFormulaTrend.mapIndexed { i, (n, v) -> OverviewSeries(n, v, palette[i % palette.size]) },
                unitSuffix = " kg"
            )
        }

        if (summary.prodByStoreTrend.any { (_, v) -> v.any { it != 0.0 } }) {
            OverviewInteractiveLineChart(
                title = "Production by store over time",
                labels = monthLabels,
                series = summary.prodByStoreTrend.mapIndexed { i, (n, v) -> OverviewSeries(n, v, palette[i % palette.size]) },
                unitSuffix = " kg"
            )
        }

        if (summary.months.any { it.factoryUnits > 0.0 }) {
            OverviewInteractiveLineChart(
                title = "Factory output (units)",
                labels = monthLabels,
                series = listOf(OverviewSeries("Units", summary.months.map { it.factoryUnits }, accents.purple)),
                unitSuffix = " units",
                showArea = true
            )
        }

        OverviewRankedBarsCard(
            title = "Production by formula",
            items = summary.formulaProductionKg + summary.moveFormulas,
            barColor = accents.cyan,
            unitSuffix = " kg",
            isMoneyMetric = false
        )
        OverviewRankedBarsCard(
            title = "Production by store",
            items = summary.storeProductionKg + summary.moveStores,
            barColor = accents.cyan,
            unitSuffix = " kg",
            isMoneyMetric = false
        )

        if (summary.factoryByFormula.isNotEmpty()) {
            OverviewRankedBarsCard("Factory output by formula", summary.factoryByFormula, accents.purple, unitSuffix = " units", isMoneyMetric = false)
        }
        if (summary.formulaCostPerKg.isNotEmpty()) {
            OverviewRankedBarsCard("Formula cost per kg", summary.formulaCostPerKg, accents.purple, currency, masked)
        }

        SectionHeader("Store Inventory & Pricing", "Produced + returned - sold")
        if (storeSnapshots.isEmpty()) {
            EmptyStateCard("No stores yet", "Add stores from Store Manager in Settings.")
        } else {
            storeSnapshots.forEach { s ->
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

        if (unitPools.isNotEmpty()) {
            SectionHeader("Formula Unit Pools")
            unitPools.forEach { pool ->
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
    }
}

@Composable
fun OverviewMoneyPanel(
    summary: OverviewEngine.OverviewKpiSummary,
    health: FinanceEngine.EconomicHealthSummary,
    isAdmin: Boolean,
    currency: String,
    masked: Boolean,
    onOpenDailyLedger: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val monthLabels = remember(summary.months) { summary.months.map { it.label } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OverviewInteractiveLineChart(
            title = "Cash in and out",
            labels = monthLabels,
            series = listOf(
                OverviewSeries("Cash in", summary.months.map { it.cashIn }, accents.emerald),
                OverviewSeries("Cash out", summary.months.map { it.cashOut }, accents.danger)
            ),
            currency = currency,
            masked = masked,
            showArea = true
        )

        OverviewRankedBarsCard("Expenses by category", summary.expenseCategories, accents.gold, currency, masked)

        SarimCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Economic Health Balance Sheet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                if (isAdmin) {
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
            listOf(
                "Cash & Bank Channels" to health.cashInHand,
                "Raw Material Stock" to health.rawMaterialValue,
                "Formula Unit Pools" to health.formulaUnitsValue,
                "Finished Store Stock" to health.finishedStockValue,
                "Customer Receivables" to health.customerReceivables,
                "Entity Receivables" to health.entityReceivables,
                "Supplier & Entity Payables" to -health.totalLiabilities,
                "Net Working Capital" to health.netWorkingCapital
            ).forEach { (label, amt) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        InputGuards.formatMoney(amt, currency, masked),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (amt >= 0) accents.emerald else accents.danger
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Current Liquidity Ratio: ${health.currentRatio}x", style = MaterialTheme.typography.labelMedium, color = accents.cyan)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewStockPanel(
    snapshot: WorkspaceSnapshot,
    summary: OverviewEngine.OverviewKpiSummary,
    currency: String,
    masked: Boolean
) {
    val accents = LocalSarimAccents.current
    val materials = remember(snapshot.inventory) {
        FinanceEngine.materialRecords(snapshot.inventory).sortedBy { it.name.lowercase() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OverviewRankedBarsCard("Stock by value", summary.stockByValue, accents.purple, currency, masked)

        if (summary.outOfStockNames.isNotEmpty()) {
            SarimCard(accentBorder = accents.danger.copy(alpha = 0.45f)) {
                Text("Out of stock (${summary.outOfStockNames.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = accents.danger)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    summary.outOfStockNames.forEach { name ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = accents.danger.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.4f))
                        ) {
                            Text(name, style = MaterialTheme.typography.labelMedium, color = accents.danger, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }

        if (materials.isEmpty()) {
            EmptyStateCard("No raw materials tracked", "Record a Raw Material Purchase from Factory or Drawer.")
        } else {
            materials.forEach { item ->
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
                        Text(InputGuards.formatKg(item.quantity), style = MaterialTheme.typography.titleMedium, color = if (low) accents.gold else accents.emerald)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewChecksPanel(
    summary: OverviewEngine.OverviewKpiSummary,
    isAdmin: Boolean,
    currency: String,
    masked: Boolean,
    onRepriceZeroCost: () -> Unit,
    onStartFixOneByOne: () -> Unit,
    onSelectByCode: (String) -> Unit,
    onSelectFinding: (OverviewEngine.AuditFinding) -> Unit,
    onInspectLossSale: (OverviewEngine.WorstSaleLoss) -> Unit
) {
    val accents = LocalSarimAccents.current
    val p = summary.profitAnalysis
    val a = summary.audit

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (p.count > 0) {
            SarimCard {
                Text("Profit Analysis", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                Text(
                    "${p.losingCount} of ${p.count} sales made a loss (${InputGuards.formatMoney(-p.losingSum, currency, masked)}). The other ${p.gainingCount} earned ${InputGuards.formatMoney(p.gainingSum, currency, masked)}, net ${InputGuards.formatMoney(p.net, currency, masked)}.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Avg selling price ${InputGuards.formatMoney(p.avgPrice, currency, masked)}/kg vs avg cost ${InputGuards.formatMoney(p.avgCost, currency, masked)}/kg.",
                    style = MaterialTheme.typography.bodySmall,
                    color = accents.textMuted
                )
                if (p.noCostCount > 0 || p.mismatchCount > 0 || p.zeroQtyCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "No-cost sales: ${p.noCostCount} • Profit mismatches: ${p.mismatchCount} • Zero-qty rows: ${p.zeroQtyCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accents.gold
                    )
                }
            }

            if (p.byMonth.isNotEmpty()) {
                OverviewInteractiveLineChart(
                    title = "Profit by month",
                    labels = p.byMonth.map { it.first },
                    series = listOf(OverviewSeries("Profit", p.byMonth.map { it.second }, if (p.net < 0) accents.danger else accents.emerald)),
                    currency = currency,
                    masked = masked,
                    showArea = true
                )
            }
            if (p.byCustomer.isNotEmpty()) {
                OverviewRankedBarsCard("Loss by customer", p.byCustomer.map { it.first to -it.second }, accents.danger, currency, masked)
            }
            if (p.byStore.isNotEmpty()) {
                OverviewRankedBarsCard("Loss by store", p.byStore.map { it.first to -it.second }, accents.danger, currency, masked)
            }
            if (p.worstLosses.isNotEmpty()) {
                SarimCard(accentBorder = accents.danger.copy(alpha = 0.45f)) {
                    Text("Biggest losses (${p.worstLosses.size})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = accents.danger)
                    Spacer(Modifier.height(8.dp))
                    p.worstLosses.take(6).forEach { w ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable { onInspectLossSale(w) }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(w.customer, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${w.date}${if (w.store.isNotBlank()) " • ${w.store}" else ""} • ${InputGuards.fmtNum(w.qty)} kg @ ${InputGuards.fmtNum(w.price)} vs cost ${InputGuards.fmtNum(w.cost)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(InputGuards.formatMoney(w.profit, currency, masked), style = MaterialTheme.typography.labelLarge, color = accents.danger)
                        }
                        HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // Data Audit Card
        SarimCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Data Problems & Integrity", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        if (a.findings.isEmpty()) "Scanned ${a.scannedTotal} records — all checks passed"
                        else "${a.findings.size} of ${a.scannedTotal} records need attention (${a.errors} errors • ${a.warnings} warnings)",
                        style = MaterialTheme.typography.bodySmall,
                        color = accents.textMuted
                    )
                }
                if (isAdmin) {
                    Surface(
                        onClick = onRepriceZeroCost,
                        shape = RoundedCornerShape(14.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.emerald),
                        modifier = Modifier.testTag("overview_reprice_sales_btn")
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Reprice Zero-Cost", style = MaterialTheme.typography.labelMedium, color = accents.emerald)
                        }
                    }
                }
            }

            if (a.findings.isNotEmpty() && isAdmin) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = onStartFixOneByOne,
                    shape = RoundedCornerShape(14.dp),
                    color = accents.emerald.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accents.emerald),
                    modifier = Modifier.testTag("overview_fix_one_by_one_btn")
                ) {
                    Text(
                        "Fix one by one (${a.findings.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = accents.emerald,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            if (a.byCheck.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    a.byCheck.forEach { chk ->
                        val col = if (chk.severity == "error") accents.danger else accents.gold
                        Surface(
                            onClick = { if (isAdmin) onSelectByCode(chk.code) },
                            shape = RoundedCornerShape(14.dp),
                            color = col.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, col.copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("ov_check_${chk.code}")
                        ) {
                            Text(
                                "${chk.label} (${chk.count})",
                                style = MaterialTheme.typography.labelSmall,
                                color = col,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        if (a.findings.isEmpty()) {
            SarimCard(accentBorder = accents.emerald) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accents.emerald)
                    Spacer(Modifier.width(10.dp))
                    Text("Zero discrepancies detected across stores, inventory, sales, and collections.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            a.findings.forEach { finding ->
                val color = if (finding.severity == "error") accents.danger else accents.gold
                SarimCard(
                    accentBorder = color,
                    modifier = Modifier
                        .clickable(enabled = isAdmin) { onSelectFinding(finding) }
                        .testTag("ov_finding_${finding.id}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = color)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${finding.title} (${finding.label}${if (finding.date.isNotBlank()) " • ${finding.date}" else ""})", style = MaterialTheme.typography.titleSmall, color = color)
                            Text(finding.reasons.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        if (isAdmin) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = accents.softPillFill,
                                border = BorderStroke(1.dp, accents.emerald)
                            ) {
                                Text("Fix", style = MaterialTheme.typography.labelMedium, color = accents.emerald, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverviewActivityPanel(
    summary: OverviewEngine.OverviewKpiSummary,
    currency: String,
    masked: Boolean
) {
    val accents = LocalSarimAccents.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (summary.recentActivity.isEmpty()) {
            EmptyStateCard("No recent activity", "Log production, sales, payments, or expenses to see the feed.")
        } else {
            SarimCard {
                Text("Recent activity", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                summary.recentActivity.forEachIndexed { idx, act ->
                    val toneColor = when (act.tone) {
                        "good" -> accents.emerald
                        "bad" -> accents.danger
                        "warn" -> accents.gold
                        else -> accents.cyan
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(act.title, style = MaterialTheme.typography.titleSmall)
                            Text("${act.kind}${if (act.sub.isNotBlank()) " • ${act.sub}" else ""}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Text(if (masked && act.kind != "Production") "$currency ••••" else act.amount, color = toneColor, style = MaterialTheme.typography.labelLarge)
                    }
                    if (idx < summary.recentActivity.lastIndex) HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.45f))
                }
            }
        }

        // Monthly Summary Table
        SarimCard {
            Text("Monthly summary", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.background(accents.inputFill).padding(vertical = 6.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Month", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(64.dp))
                        Text("Prod kg", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(68.dp))
                        Text("Sales", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(78.dp))
                        Text("Net Profit", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(78.dp))
                        Text("Sales Profit", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(78.dp))
                        Text("In", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(74.dp))
                        Text("Out", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(74.dp))
                        Text("Expenses", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(74.dp))
                    }
                    summary.months.reversed().forEach { m ->
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(m.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(64.dp))
                            Text(InputGuards.fmtNum(m.prodKg, 0), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(68.dp))
                            Text(InputGuards.formatMoney(m.salesValue, currency, masked), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(78.dp))
                            Text(InputGuards.formatMoney(m.prodProfit, currency, masked), style = MaterialTheme.typography.bodySmall, color = if (m.prodProfit >= 0) accents.emerald else accents.danger, modifier = Modifier.width(78.dp))
                            Text(InputGuards.formatMoney(m.salesProfit, currency, masked), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(78.dp))
                            Text(InputGuards.formatMoney(m.cashIn, currency, masked), style = MaterialTheme.typography.bodySmall, color = accents.emerald, modifier = Modifier.width(74.dp))
                            Text(InputGuards.formatMoney(m.cashOut, currency, masked), style = MaterialTheme.typography.bodySmall, color = accents.danger, modifier = Modifier.width(74.dp))
                            Text(InputGuards.formatMoney(m.expenses, currency, masked), style = MaterialTheme.typography.bodySmall, color = accents.gold, modifier = Modifier.width(74.dp))
                        }
                        HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }
}
