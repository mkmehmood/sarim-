package com.gullzubair.sarim.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.OverviewEngine
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.MetricKpiCard
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SectionHeader
import com.gullzubair.sarim.ui.components.SimpleBarChart
import com.gullzubair.sarim.ui.components.SparklineChart
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

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
    var windowMonths by remember { mutableIntStateOf(3) }
    var activePanel by remember { mutableStateOf("Sales") }

    val summary = remember(snapshot, windowMonths) {
        OverviewEngine.computeOverview(snapshot, windowMonths)
    }
    val health = remember(snapshot) { FinanceEngine.calculateEconomicHealth(snapshot) }
    val storeSnapshots = remember(snapshot) { FinanceEngine.computeStoreStockSnapshots(snapshot) }
    val unitPools = remember(snapshot) { FinanceEngine.computeUnitPools(snapshot) }
    val pendingCount = remember(snapshot.pendingApprovals) { snapshot.pendingApprovals.count { it.status == "pending" } }

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
                Text("Executive Overview", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 6, 12).forEach { m ->
                        FilterChip(
                            selected = windowMonths == m,
                            onClick = { windowMonths = m },
                            label = { Text("${m}M") },
                            modifier = Modifier.testTag("overview_window_${m}m")
                        )
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
                        Button(onClick = onOpenApprovals, modifier = Modifier.testTag("overview_open_approvals_btn")) {
                            Text("Review")
                        }
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricKpiCard(
                    label = "Net Operating Profit",
                    value = InputGuards.formatMoney(summary.netOperatingProfit, currency, masked),
                    subtitle = "${windowMonths}M window",
                    accent = if (summary.netOperatingProfit >= 0) accents.emerald else accents.danger,
                    icon = Icons.Default.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
                MetricKpiCard(
                    label = "Sales Revenue",
                    value = InputGuards.formatMoney(summary.totalSalesRevenue, currency, masked),
                    subtitle = "Cash ${InputGuards.formatMoney(summary.totalCashCollected, currency, masked)}",
                    accent = accents.cyan,
                    icon = Icons.Default.PointOfSale,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricKpiCard(
                    label = "Cash in Hand",
                    value = InputGuards.formatMoney(summary.cashInHand, currency, masked),
                    subtitle = "All channels ${InputGuards.formatMoney(health.cashInHand, currency, masked)}",
                    accent = accents.emerald,
                    icon = Icons.Default.AccountBalanceWallet,
                    modifier = Modifier.weight(1f)
                )
                MetricKpiCard(
                    label = "Open Credits",
                    value = InputGuards.formatMoney(summary.openCredits, currency, masked),
                    subtitle = "Produced ${InputGuards.formatKg(summary.totalProducedKg)}",
                    accent = accents.gold,
                    icon = Icons.Default.Factory,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Sales", "Production", "Money", "Stock", "Checks", "Activity").forEach { panel ->
                    FilterChip(
                        selected = activePanel == panel,
                        onClick = { activePanel = panel },
                        label = { Text(panel) },
                        modifier = Modifier.testTag("overview_panel_${panel.lowercase()}")
                    )
                }
            }
        }

        when (activePanel) {
            "Sales" -> {
                item {
                    SparklineChart(
                        title = "Monthly Sales Trend",
                        series = summary.monthlyRevenueSeries,
                        lineColor = accents.emerald,
                        currency = currency,
                        masked = masked
                    )
                }
                item {
                    SimpleBarChart(
                        title = "Sales by Store",
                        items = summary.storeSalesBreakdown,
                        barColor = accents.cyan,
                        currency = currency,
                        masked = masked
                    )
                }
            }

            "Production" -> {
                item { SectionHeader("Store Inventory & Pricing", "Net produced + rep returns - sold") }
                if (storeSnapshots.isEmpty()) {
                    item { EmptyStateCard("No stores yet", "Add stores from Store Manager in the navigation drawer.") }
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
                            Button(onClick = onOpenDailyLedger, modifier = Modifier.testTag("overview_open_ledger_btn")) {
                                Text("Cashbook")
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
                item {
                    SparklineChart(
                        title = "Monthly Business Expenses",
                        series = summary.monthlyExpenseSeries,
                        lineColor = accents.gold,
                        currency = currency,
                        masked = masked
                    )
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
                            Button(onClick = { vm.repairLedgerMirrors() }, modifier = Modifier.testTag("overview_repair_links_btn")) {
                                Icon(Icons.Default.Build, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Fix Links")
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

            else -> { // Activity
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
