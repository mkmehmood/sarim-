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
import androidx.compose.material.icons.filled.AddShoppingCart
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
import com.gullzubair.sarim.domain.FormulaEngine
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
fun FactoryScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onOpenRawPurchase: () -> Unit,
    onOpenFormulas: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney

    var subTab by remember { mutableIntStateOf(0) }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var selectedFormula by remember(snapshot.formulas) { mutableStateOf(snapshot.formulas.firstOrNull()) }
    var unitsStr by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }
    var sortByQty by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            listOf("Produce", "Inventory", "Pools", "History").forEachIndexed { idx, title ->
                Tab(selected = subTab == idx, onClick = { subTab = idx }, text = { Text(title) }, modifier = Modifier.testTag("factory_subtab_$idx"))
            }
        }

        when (subTab) {
            0 -> {
                val units = (unitsStr.toDoubleOrNull()?.toLong() ?: 0L).coerceAtLeast(0L)
                val reqLines = remember(selectedFormula, units, snapshot.formulas, snapshot.inventory) {
                    if (selectedFormula != null && units > 0) {
                        FormulaEngine.flattenFormula(selectedFormula!!, snapshot.formulas, snapshot.inventory, units.toDouble())
                    } else emptyList()
                }
                val estCost = remember(selectedFormula, units, snapshot.formulas, snapshot.inventory) {
                    if (selectedFormula != null && units > 0) {
                        FormulaEngine.calculateBatchCost(selectedFormula!!, snapshot.formulas, snapshot.inventory, units.toDouble())
                    } else 0.0
                }
                val invByName = remember(snapshot.inventory) {
                    snapshot.inventory.filter { !it.recycled }.associateBy { it.name.trim().lowercase() }
                }
                val allInStock = reqLines.isNotEmpty() && reqLines.all { line ->
                    (invByName[line.name.lowercase()]?.stockQty ?: 0.0) + 0.001 >= line.qtyKg
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Formula Batch Production", style = MaterialTheme.typography.titleMedium)
                                FilterChip(
                                    selected = false,
                                    onClick = onOpenFormulas,
                                    label = { Text("Manage Formulas") },
                                    modifier = Modifier.testTag("factory_manage_formulas_btn")
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            DatePickerField("Batch Date", date, { date = it }, "factory_date")
                            Spacer(Modifier.height(10.dp))
                            PickerField(
                                label = "Formula",
                                selectedLabel = selectedFormula?.name ?: "Create a formula first",
                                items = snapshot.formulas,
                                itemLabel = { "${it.name} (${FormulaEngine.describeFormula(it, snapshot.formulas)})" },
                                onSelect = { selectedFormula = it },
                                testTag = "factory_formula_picker"
                            )
                            Spacer(Modifier.height(10.dp))
                            DecimalField("Units to Produce", unitsStr, { unitsStr = it }, "factory_units_input")
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it.take(500) },
                                label = { Text("Batch Notes") },
                                modifier = Modifier.fillMaxWidth().testTag("factory_notes_input")
                            )
                            Spacer(Modifier.height(12.dp))
                            if (reqLines.isNotEmpty()) {
                                Text("Ingredient Deduction Preview", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(6.dp))
                                reqLines.forEach { line ->
                                    val have = invByName[line.name.lowercase()]?.stockQty ?: 0.0
                                    val ok = have + 0.001 >= line.qtyKg
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(line.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "Need ${InputGuards.formatKg(line.qtyKg)} / Have ${InputGuards.formatKg(have)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (ok) accents.emerald else accents.danger
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MetricKpiCard("Est. Batch Cost", InputGuards.formatMoney(estCost, currency, masked), accent = accents.gold, modifier = Modifier.weight(1f))
                                MetricKpiCard("Cost / Unit", InputGuards.formatMoney(if (units > 0) estCost / units else 0.0, currency, masked), accent = accents.cyan, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    selectedFormula?.let { f ->
                                        vm.submitFactoryBatch(date, f, units, notes)
                                        notes = ""
                                    }
                                },
                                enabled = selectedFormula != null && units > 0 && allInStock,
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("factory_produce_btn")
                            ) {
                                Text(if (allInStock || reqLines.isEmpty()) "Produce Units & Deduct Stock" else "Insufficient Raw Material")
                            }
                        }
                    }
                }
            }

            1 -> {
                val activeInv = remember(snapshot.inventory, sortByQty) {
                    val base = snapshot.inventory.filter { !it.recycled }
                    if (sortByQty) base.sortedByDescending { it.stockQty } else base.sortedBy { it.name.lowercase() }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !sortByQty, onClick = { sortByQty = false }, label = { Text("Sort Name") })
                                FilterChip(selected = sortByQty, onClick = { sortByQty = true }, label = { Text("Sort Qty") })
                            }
                            Button(onClick = onOpenRawPurchase, modifier = Modifier.testTag("factory_purchase_btn")) {
                                Icon(Icons.Default.AddShoppingCart, contentDescription = null)
                                Text(" Purchase")
                            }
                        }
                    }
                    if (activeInv.isEmpty()) {
                        item { EmptyStateCard("No raw material stock", "Tap Purchase to add raw materials and supplier lots.", "Add Purchase", onOpenRawPurchase) }
                    } else {
                        items(activeInv, key = { it.id }) { item ->
                            val low = item.minThreshold > 0 && item.stockQty <= item.minThreshold
                            SarimCard(accentBorder = if (low) accents.gold else null) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Unit: ${item.unitName} (1 = ${item.conversionFactor} kg) • Avg ${InputGuards.formatMoney(item.avgCostPerKg, currency, masked)}/kg",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accents.textMuted
                                        )
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
            }

            2 -> {
                val pools = remember(snapshot) { FinanceEngine.computeUnitPools(snapshot) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pools.isEmpty()) {
                        item { EmptyStateCard("No unit pools yet", "Create formulas and produce factory batches.") }
                    } else {
                        items(pools, key = { it.formulaId }) { pool ->
                            SarimCard {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(pool.formulaName, style = MaterialTheme.typography.titleMedium)
                                    Text("${InputGuards.formatNum(pool.availableUnits)} avail", color = accents.purple, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    "Produced ${InputGuards.formatNum(pool.producedUnits)} • Consumed ${InputGuards.formatNum(pool.usedUnits)} • Cost/Unit ${InputGuards.formatMoney(pool.avgCostPerUnit, currency, masked)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                val activeFactory = remember(snapshot.factory) { snapshot.factory.filter { !it.recycled } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeFactory.isEmpty()) {
                        item { EmptyStateCard("No factory history", "Batches and raw material purchases will appear here.") }
                    } else {
                        items(activeFactory, key = { it.id }) { rec ->
                            SarimCard {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        val title = if (rec.recordType == "batch") {
                                            "Batch: ${rec.formulaName} (${rec.unitsProduced} units)"
                                        } else {
                                            "Purchase: ${rec.rawItemName} (${InputGuards.formatKg(rec.qtyKg)})"
                                        }
                                        Text(title, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${rec.date} • Total ${InputGuards.formatMoney(rec.totalCost, currency, masked)}" +
                                                if (rec.recordType == "purchase" && rec.payableBalance > 0.01) " • Payable ${InputGuards.formatMoney(rec.payableBalance, currency, masked)}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accents.textMuted
                                        )
                                    }
                                    IconButton(
                                        onClick = { vm.recycleRecord("factory", rec.id, "${rec.recordType} (${rec.date})") },
                                        modifier = Modifier.testTag("factory_delete_${rec.id}")
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
