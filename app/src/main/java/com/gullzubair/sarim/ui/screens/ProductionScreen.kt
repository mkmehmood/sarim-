package com.gullzubair.sarim.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimDangerButton
import com.gullzubair.sarim.ui.components.SarimDetailBottomSheet
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimYearMonthDayTree
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
    var selectedRecord by remember { mutableStateOf<ProductionRecord?>(null) }

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Log Entry", "Stores", "History").forEachIndexed { i, title ->
                val selected = subTab == i
                Surface(
                    onClick = { subTab = i },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) accents.softPillFill else accents.inputFill,
                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                    modifier = Modifier.weight(1f).testTag("prod_subtab_$i")
                ) {
                    Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(title, style = MaterialTheme.typography.labelLarge, color = if (selected) accents.emerald else accents.textMuted)
                    }
                }
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
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Log Daily Production", style = MaterialTheme.typography.titleMedium)
                                Surface(
                                    onClick = onOpenStockTransfer,
                                    shape = RoundedCornerShape(14.dp),
                                    color = accents.softPillFill,
                                    border = BorderStroke(1.dp, accents.emerald),
                                    modifier = Modifier.testTag("prod_open_transfer_btn")
                                ) {
                                    Text("Transfer Stock", style = MaterialTheme.typography.labelMedium, color = accents.emerald, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                }
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
                            SarimPillInput(
                                value = notes,
                                onValueChange = { notes = it.take(500) },
                                label = "NOTES (OPTIONAL)",
                                placeholder = "Optional production note",
                                testTag = "prod_notes_input"
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("Net Produced", InputGuards.formatKg(net), accents.emerald, Modifier.weight(1f))
                                SarimHalfRingKpiBox("Est. Profit", InputGuards.formatMoney(profit, currency, masked), accents.cyan, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(14.dp))
                            SarimGradientButton(
                                text = "Save Production Entry",
                                onClick = {
                                    selectedStore?.let { store ->
                                        vm.submitProduction(date, store, gross, container, units, costKg, notes)
                                        grossStr = ""
                                        notes = ""
                                    }
                                },
                                enabled = selectedStore != null && net > 0.0,
                                testTag = "prod_save_btn"
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
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
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Day", "Week", "Month", "Year", "All").forEach { p ->
                                val selected = period == p
                                Surface(
                                    onClick = { period = p },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) accents.softPillFill else accents.inputFill,
                                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                                    modifier = Modifier.testTag("prod_period_${p.lowercase()}")
                                ) {
                                    Text(p, style = MaterialTheme.typography.labelMedium, color = if (selected) accents.emerald else accents.textMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                                }
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
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            else -> {
                val activeList = remember(snapshot.production) { snapshot.production.filter { !it.recycled }.sortedByDescending { it.date } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeList.isEmpty()) {
                        item { EmptyStateCard("No production logs", "Log your first batch on the Entry tab.") }
                    } else {
                        item {
                            SarimYearMonthDayTree(
                                items = activeList,
                                dateOf = { it.date },
                                summaryLabel = { list -> InputGuards.formatKg(list.sumOf { kotlin.math.abs(it.netWeight) }) },
                                itemRow = { rec ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedRecord = rec }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                if (rec.transfer) "TRANSFER ${rec.transferType.uppercase()} • ${rec.storeName}" else rec.storeName,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text("Units ${rec.formulaUnits}", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                        }
                                        Text(
                                            InputGuards.formatKg(rec.netWeight),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (rec.netWeight >= 0) accents.emerald else accents.gold
                                        )
                                    }
                                }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    selectedRecord?.let { rec ->
        SarimDetailBottomSheet(
            title = rec.storeName,
            rightTitle = InputGuards.formatKg(rec.netWeight),
            subtitle = DateUtils.formatPretty(rec.date).uppercase(),
            onDismiss = { selectedRecord = null },
            content = {
                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Formula Units", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text("${rec.formulaUnits}", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Profit", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(InputGuards.formatMoney(rec.profit, currency, masked), style = MaterialTheme.typography.titleSmall, color = accents.emerald)
                }
                if (!rec.notes.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(rec.notes, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                }
            },
            footer = {
                SarimDangerButton(
                    text = "Move to Recycle Bin",
                    onClick = {
                        vm.recycleRecord("production", rec.id, "${rec.storeName} (${rec.date})")
                        selectedRecord = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "prod_delete_${rec.id}"
                )
            }
        )
    }
}
