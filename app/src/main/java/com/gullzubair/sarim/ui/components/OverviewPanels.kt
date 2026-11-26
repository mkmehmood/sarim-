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
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.unit.sp
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
            val o = summary.owedAging
            SarimCard {
                Text("Who owes you", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverviewOwedTile("Total owed", InputGuards.formatMoney(o.total, currency, masked), "${o.count} sale${if (o.count == 1) "" else "s"}, oldest ${o.oldestDays} days", accents.gold, Modifier.weight(1f))
                    OverviewOwedTile("Up to 30 days", InputGuards.formatMoney(o.d30, currency, masked), "", accents.emerald, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverviewOwedTile("31 to 60 days", InputGuards.formatMoney(o.d60, currency, masked), "", accents.gold, Modifier.weight(1f))
                    OverviewOwedTile("Over 60 days", InputGuards.formatMoney(o.d90, currency, masked), "", accents.danger, Modifier.weight(1f))
                }
            }
            OverviewRankedBarsCard(
                title = "By customer, with age of the oldest sale",
                items = o.topCustomers.map { (cust, amt, age) -> "$cust (${age}d)" to amt },
                barColor = accents.gold,
                currency = currency,
                masked = masked,
                showSharePct = false
            )
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OverviewRankedBarsCard("Stock by value", summary.stockByValue, accents.purple, currency, masked)

        if (summary.outOfStockNames.isNotEmpty()) {
            SarimCard(accentBorder = accents.danger.copy(alpha = 0.45f)) {
                Text("Out of stock", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = accents.danger)
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
                Text("Profit and data check", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                Text(
                    "${p.losingCount} of ${p.count} sales made a loss, losing ${InputGuards.formatMoney(-p.losingSum, currency, masked)} in total. The other ${p.gainingCount} earned ${InputGuards.formatMoney(p.gainingSum, currency, masked)}, so the net is ${InputGuards.formatMoney(p.net, currency, masked)}.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Average selling price was ${InputGuards.fmtNum(p.avgPrice)} per kg against an average cost of ${InputGuards.fmtNum(p.avgCost)} per kg${if (p.avgPrice < p.avgCost) ", so on average you sold below cost" else ""}.",
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
                    Text("Biggest losses", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = accents.danger)
                    Spacer(Modifier.height(8.dp))
                    p.worstLosses.forEach { w ->
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
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(12.dp), color = accents.softPillFill, border = BorderStroke(1.dp, accents.emerald)) {
                                Text("Open", style = MaterialTheme.typography.labelMedium, color = accents.emerald, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
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
                    Text("Data problems", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text(
                        if (a.findings.isEmpty()) "No problems found in ${a.scannedTotal} records."
                        else "${a.findings.size} of ${a.scannedTotal} records need attention: ${a.errors} with errors and ${a.warnings} with warnings. Errors break totals or links; warnings are unusual but may be intentional.",
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
                            Icon(Icons.Rounded.Build, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(15.dp))
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
                        "Fix one by one",
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
                                "${chk.label}  ${chk.count}",
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
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accents.emerald)
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
                        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = color)
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
                        Text("Produced kg", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(68.dp))
                        Text("Sales", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(78.dp))
                        Text("Net profit", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(78.dp))
                        Text("Sales profit", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(78.dp))
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

@Composable
private fun OverviewOwedTile(label: String, value: String, sub: String, color: Color, modifier: Modifier = Modifier) {
    val accents = LocalSarimAccents.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.40f))
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = accents.textMuted, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = accents.textMuted, maxLines = 2)
        }
    }
}
