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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.FactoryRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
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
    var selectedFactoryRec by remember { mutableStateOf<FactoryRecord?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Produce", "Inventory", "Pools", "History").forEachIndexed { idx, title ->
                val selected = subTab == idx
                Surface(
                    onClick = { subTab = idx },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) accents.softPillFill else accents.inputFill,
                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                    modifier = Modifier.weight(1f).testTag("factory_subtab_$idx")
                ) {
                    Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(title, style = MaterialTheme.typography.labelLarge, color = if (selected) accents.emerald else accents.textMuted, maxLines = 1)
                    }
                }
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
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Formula Batch Production", style = MaterialTheme.typography.titleMedium)
                                Surface(
                                    onClick = onOpenFormulas,
                                    shape = RoundedCornerShape(14.dp),
                                    color = accents.softPillFill,
                                    border = BorderStroke(1.dp, accents.emerald),
                                    modifier = Modifier.testTag("factory_manage_formulas_btn")
                                ) {
                                    Text("Formulas", style = MaterialTheme.typography.labelMedium, color = accents.emerald, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                }
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
                            Spacer(Modifier.height(10.dp))
                            SarimPillInput(notes, { notes = it.take(500) }, label = "BATCH NOTES", testTag = "factory_notes_input")
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
                                SarimHalfRingKpiBox("Est. Batch Cost", InputGuards.formatMoney(estCost, currency, masked), accents.gold, Modifier.weight(1f))
                                SarimHalfRingKpiBox("Cost / Unit", InputGuards.formatMoney(if (units > 0) estCost / units else 0.0, currency, masked), accents.cyan, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(14.dp))
                            SarimGradientButton(
                                text = if (allInStock || reqLines.isEmpty()) "Produce Units & Deduct Stock" else "Insufficient Raw Material",
                                onClick = {
                                    selectedFormula?.let { f ->
                                        vm.submitFactoryBatch(date, f, units, notes)
                                        notes = ""
                                    }
                                },
                                enabled = selectedFormula != null && units > 0 && allInStock,
                                testTag = "factory_produce_btn"
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            1 -> {
                val activeInv = remember(snapshot.inventory, sortByQty) {
                    val base = snapshot.inventory.filter { !it.recycled }
                    if (sortByQty) base.sortedByDescending { it.stockQty } else base.sortedBy { it.name.lowercase() }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(false to "Sort Name", true to "Sort Qty").forEach { (isQty, label) ->
                                    val sel = sortByQty == isQty
                                    Surface(
                                        onClick = { sortByQty = isQty },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (sel) accents.softPillFill else accents.inputFill,
                                        border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle)
                                    ) {
                                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) accents.emerald else accents.textMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                                    }
                                }
                            }
                            Surface(
                                onClick = onOpenRawPurchase,
                                shape = RoundedCornerShape(16.dp),
                                color = accents.softPillFill,
                                border = BorderStroke(1.dp, accents.emerald),
                                modifier = Modifier.testTag("factory_purchase_btn")
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Purchase", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                                }
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
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (low) accents.gold else accents.emerald
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            2 -> {
                val pools = remember(snapshot) { FinanceEngine.computeUnitPools(snapshot) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
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
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            else -> {
                val activeFactory = remember(snapshot.factory) { snapshot.factory.filter { !it.recycled }.sortedByDescending { it.date } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeFactory.isEmpty()) {
                        item { EmptyStateCard("No factory history", "Batches and raw material purchases will appear here.") }
                    } else {
                        item {
                            SarimYearMonthDayTree(
                                items = activeFactory,
                                dateOf = { it.date },
                                summaryLabel = { list -> InputGuards.formatMoney(list.sumOf { it.totalCost }, currency, masked) },
                                itemRow = { rec ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedFactoryRec = rec }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                if (rec.recordType == "batch") "BATCH • ${rec.formulaName}" else "PURCHASE • ${rec.rawItemName}",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                if (rec.recordType == "batch") "${rec.unitsProduced} units" else InputGuards.formatKg(rec.qtyKg),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accents.textMuted
                                            )
                                        }
                                        Text(
                                            InputGuards.formatMoney(rec.totalCost, currency, masked),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = accents.emerald
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

    selectedFactoryRec?.let { rec ->
        SarimDetailBottomSheet(
            title = if (rec.recordType == "batch") rec.formulaName ?: "Batch" else rec.rawItemName ?: "Purchase",
            rightTitle = InputGuards.formatMoney(rec.totalCost, currency, masked),
            subtitle = "${DateUtils.formatPretty(rec.date).uppercase()} • ${rec.recordType.uppercase()}",
            onDismiss = { selectedFactoryRec = null },
            content = {
                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (rec.recordType == "batch") "Units Produced" else "Quantity", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(if (rec.recordType == "batch") "${rec.unitsProduced}" else InputGuards.formatKg(rec.qtyKg), style = MaterialTheme.typography.titleSmall)
                }
                if (rec.recordType == "purchase" && rec.payableBalance > 0.01) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Payable Remaining", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                        Text(InputGuards.formatMoney(rec.payableBalance, currency, masked), style = MaterialTheme.typography.titleSmall, color = accents.danger)
                    }
                }
            },
            footer = {
                SarimDangerButton(
                    text = "Move to Recycle Bin",
                    onClick = {
                        vm.recycleRecord("factory", rec.id, "${rec.recordType} (${rec.date})")
                        selectedFactoryRec = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "factory_delete_${rec.id}"
                )
            }
        )
    }
}
