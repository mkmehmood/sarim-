package com.gullzubair.sarim.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.MetricKpiCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

@Composable
fun ProductionScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onOpenStockTransfer: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney
    val activeStores = remember(snapshot.stores) { snapshot.stores.filter { it.active } }

    var subTab by remember { mutableIntStateOf(0) }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var selectedStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var grossStr by remember { mutableStateOf("") }
    var containerStr by remember { mutableStateOf("0") }
    var unitsStr by remember { mutableStateOf("1") }
    var customCostStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val resolvedCost = remember(date, selectedStore, snapshot) {
        selectedStore?.let {
            FinanceEngine.resolveProductionDayCostPerKg(
                date, it.storeKey, snapshot.production, snapshot.stores, snapshot.formulas, snapshot.inventory
            )
        } ?: (0.0 to "none")
    }
    LaunchedEffect(resolvedCost) {
        if (customCostStr.isBlank() && resolvedCost.first > 0.0) {
            customCostStr = resolvedCost.first.toString()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            listOf("Log Entry", "Stores Overview", "History").forEachIndexed { i, title ->
                Tab(
                    selected = subTab == i,
                    onClick = { subTab = i },
                    text = { Text(title) },
                    modifier = Modifier.testTag("prod_subtab_$i")
                )
            }
        }

        when (subTab) {
            0 -> {
                val gross = grossStr.toDoubleOrNull() ?: 0.0
                val container = containerStr.toDoubleOrNull() ?: 0.0
                val net = InputGuards.round3((gross - container).coerceAtLeast(0.0))
                val units = unitsStr.toDoubleOrNull() ?: 0.0
                val costKg = customCostStr.toDoubleOrNull() ?: resolvedCost.first
                val salePrice = selectedStore?.salePrice ?: 0.0
                val revenue = InputGuards.round2(net * salePrice)
                val profit = InputGuards.round2(revenue - (net * costKg))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Log Daily Production", style = MaterialTheme.typography.titleMedium)
                                FilterChip(
                                    selected = false,
                                    onClick = onOpenStockTransfer,
                                    label = { Text("Transfer Stock") },
                                    modifier = Modifier.testTag("prod_open_transfer_btn")
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            DatePickerField("Production Date", date, { date = it }, "prod_date")
                            Spacer(Modifier.height(10.dp))
                            PickerField(
                                label = "Store",
                                selectedLabel = selectedStore?.name ?: "Select Store",
                                items = activeStores,
                                itemLabel = { "${it.name} (${InputGuards.formatMoney(it.salePrice, currency, masked)}/kg)" },
                                onSelect = {
                                    selectedStore = it
                                    customCostStr = ""
                                },
                                testTag = "prod_store_picker"
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DecimalField("Gross Weight", grossStr, { grossStr = it }, "prod_gross_input", Modifier.weight(1f), "kg")
                                DecimalField("Container", containerStr, { containerStr = it }, "prod_container_input", Modifier.weight(1f), "kg")
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DecimalField("Formula Units", unitsStr, { unitsStr = it }, "prod_units_input", Modifier.weight(1f))
                                DecimalField("Cost / kg (${resolvedCost.second})", customCostStr, { customCostStr = it }, "prod_cost_input", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it.take(500) },
                                label = { Text("Notes (optional)") },
                                modifier = Modifier.fillMaxWidth().testTag("prod_notes_input")
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MetricKpiCard("Net Produced", InputGuards.formatKg(net), accent = accents.emerald, modifier = Modifier.weight(1f))
                                MetricKpiCard("Est. Profit", InputGuards.formatMoney(profit, currency, masked), accent = accents.cyan, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    selectedStore?.let { store ->
                                        vm.submitProduction(date, store, gross, container, units, costKg, notes)
                                        grossStr = ""
                                        notes = ""
                                    }
                                },
                                enabled = selectedStore != null && net > 0.0,
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("prod_save_btn")
                            ) {
                                Text("Save Production Entry")
                            }
                        }
                    }
                }
            }

            1 -> {
                var period by remember { mutableStateOf("All") }
                val minDate = remember(period) {
                    when (period) {
                        "Day" -> DateUtils.todayIso()
                        "Week" -> DateUtils.startOfWeekIso()
                        "Month" -> DateUtils.startOfMonthIso()
                        "Year" -> DateUtils.startOfFinancialYearIso(snapshot.settings.financialYearStartMonth.toInt())
                        else -> "1900-01-01"
                    }
                }
                val filteredProd = remember(snapshot.production, minDate) {
                    snapshot.production.filter { !it.recycled && !it.transfer && it.date >= minDate }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Day", "Week", "Month", "Year", "All").forEach { p ->
                                FilterChip(
                                    selected = period == p,
                                    onClick = { period = p },
                                    label = { Text(p) },
                                    modifier = Modifier.testTag("prod_period_${p.lowercase()}")
                                )
                            }
                        }
                    }
                    items(activeStores, key = { it.id }) { store ->
                        val itemsForStore = filteredProd.filter { it.storeKey == store.storeKey }
                        val kg = itemsForStore.sumOf { it.netWeight }
                        val prof = itemsForStore.sumOf { it.profit }
                        SarimCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(store.name, style = MaterialTheme.typography.titleMedium)
                                Text(InputGuards.formatKg(kg), style = MaterialTheme.typography.titleMedium, color = accents.emerald)
                            }
                            Text(
                                "${itemsForStore.size} batches • Profit ${InputGuards.formatMoney(prof, currency, masked)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = accents.textMuted
                            )
                        }
                    }
                }
            }

            else -> {
                val activeList = remember(snapshot.production) { snapshot.production.filter { !it.recycled } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeList.isEmpty()) {
                        item { EmptyStateCard("No production logs", "Log your first batch on the Entry tab.") }
                    } else {
                        items(activeList, key = { it.id }) { rec ->
                            SarimCard {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        val title = if (rec.transfer) {
                                            "Transfer ${rec.transferType.uppercase()}: ${rec.storeName}"
                                        } else {
                                            "${rec.storeName} • ${InputGuards.formatKg(rec.netWeight)}"
                                        }
                                        Text(title, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${rec.date} • Units ${rec.formulaUnits} • Profit ${InputGuards.formatMoney(rec.profit, currency, masked)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accents.textMuted
                                        )
                                        if (!rec.notes.isNullOrBlank()) {
                                            Text(rec.notes, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    IconButton(
                                        onClick = { vm.recycleRecord("production", rec.id, "${rec.storeName} (${rec.date})") },
                                        modifier = Modifier.testTag("prod_delete_${rec.id}")
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Move to Recycle Bin", tint = accents.danger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
