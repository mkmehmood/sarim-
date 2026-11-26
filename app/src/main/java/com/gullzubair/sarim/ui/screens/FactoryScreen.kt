package com.gullzubair.sarim.ui.screens

import androidx.activity.compose.BackHandler
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
import com.gullzubair.sarim.data.model.InventoryRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.DiscardEditDialog
import com.gullzubair.sarim.ui.components.EditDiffConfirmDialog
import com.gullzubair.sarim.ui.components.EditedBadge
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.FactoryBatchDetailSheet
import com.gullzubair.sarim.ui.components.MaterialPurchaseLotsSheet
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.TabHeading
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
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val isEntryOnlyFactory = remember(session) {
        session.mode == "factory" || (FinanceEngine.isMoneyRestricted(session) && "factory" in session.tabs)
    }
    val masked = isEntryOnlyFactory || session.restrictMoney || snapshot.settings.hideSensitiveMoney

    var subTab by remember(isEntryOnlyFactory) { mutableIntStateOf(0) }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var selectedFormula by remember(snapshot.formulas) { mutableStateOf(snapshot.formulas.firstOrNull()) }
    var unitsStr by remember { mutableStateOf("1") }
    var notes by remember { mutableStateOf("") }
    var sortByQty by remember { mutableStateOf(false) }

    var editingBatch by remember { mutableStateOf<FactoryRecord?>(null) }
    var showEditDiff by remember { mutableStateOf(false) }
    var showDiscardEdit by remember { mutableStateOf(false) }
    var selectedFactoryRec by remember { mutableStateOf<FactoryRecord?>(null) }
    var selectedMaterialLots by remember { mutableStateOf<InventoryRecord?>(null) }

    fun resetBatchForm() {
        editingBatch = null
        date = todayIso
        unitsStr = "1"
        notes = ""
    }

    fun startEditBatch(rec: FactoryRecord) {
        editingBatch = rec
        subTab = 0
        date = rec.date.ifBlank { todayIso }
        selectedFormula = snapshot.formulas.firstOrNull { it.id == rec.formulaId } ?: selectedFormula
        unitsStr = InputGuards.fmtNum(rec.units).replace(",", "")
        notes = rec.notes
    }

    BackHandler(enabled = editingBatch != null) {
        showDiscardEdit = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isEntryOnlyFactory) {
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
        }

        val activeTab = if (isEntryOnlyFactory) 0 else subTab
        when (activeTab) {
            0 -> {
                val units = (unitsStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                val desc = remember(selectedFormula, snapshot.formulas, snapshot.inventory) {
                    selectedFormula?.let { FormulaEngine.describeFormula(it, snapshot.formulas, snapshot.inventory) }
                }
                val reqLines = desc?.items.orEmpty()
                val estCost = if (desc != null && units > 0.0) InputGuards.round2(desc.perUnit * units) else 0.0
                val materials = remember(snapshot.inventory) { FinanceEngine.materialRecords(snapshot.inventory) }
                val restoredById = remember(editingBatch) {
                    val map = mutableMapOf<String, Double>()
                    editingBatch?.let { b ->
                        b.materialsUsed.ifEmpty { b.rawMaterialsUsed }.forEach { m ->
                            val mid = m["id"]?.toString().orEmpty()
                            val qty = (m["qty"] as? Number)?.toDouble() ?: (m["quantity"] as? Number)?.toDouble() ?: 0.0
                            if (mid.isNotBlank()) map[mid] = (map[mid] ?: 0.0) + qty
                        }
                    }
                    map
                }
                val allInStock = reqLines.isNotEmpty() && (desc?.problems.isNullOrEmpty()) && reqLines.all { line ->
                    val mat = materials.firstOrNull { it.id == line.id }
                        ?: materials.firstOrNull { it.name.trim().equals(line.name.trim(), ignoreCase = true) }
                    val have = (mat?.quantity ?: 0.0) + (mat?.id?.let { restoredById[it] } ?: 0.0)
                    have + 0.001 >= (line.quantity * units)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { TabHeading("Factory") }
                    if (isEntryOnlyFactory) item { Spacer(Modifier.height(6.dp)) }
                    item {
                        SarimCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (editingBatch != null) "Edit Factory Batch" else "Formula Batch Production",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (editingBatch != null) {
                                    Surface(
                                        onClick = { showDiscardEdit = true },
                                        shape = RoundedCornerShape(14.dp),
                                        color = accents.danger.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.4f)),
                                        modifier = Modifier.testTag("factory_cancel_edit_btn")
                                    ) {
                                        Text("Cancel Edit", style = MaterialTheme.typography.labelMedium, color = accents.danger, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                    }
                                } else if (!isEntryOnlyFactory) {
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
                            }
                            Spacer(Modifier.height(10.dp))
                            DatePickerField("Batch Date", date, { date = it }, "factory_date")
                            Spacer(Modifier.height(10.dp))
                            PickerField(
                                label = "Formula",
                                selectedLabel = selectedFormula?.name ?: "Create a formula first",
                                items = snapshot.formulas,
                                itemLabel = { f ->
                                    val d = FormulaEngine.describeFormula(f, snapshot.formulas, snapshot.inventory)
                                    "${f.name} (${InputGuards.fmtNum(d.weight)} kg/unit)"
                                },
                                onSelect = { selectedFormula = it },
                                testTag = "factory_formula_picker"
                            )
                            Spacer(Modifier.height(10.dp))
                            DecimalField("Units to Produce", unitsStr, { unitsStr = it }, "factory_units_input")
                            Spacer(Modifier.height(10.dp))
                            SarimPillInput(notes, { notes = it.take(500) }, label = "BATCH NOTES", testTag = "factory_notes_input")
                            Spacer(Modifier.height(12.dp))
                            if (!desc?.problems.isNullOrEmpty()) {
                                Text(
                                    "Formula error: ${desc!!.problems.joinToString { "${it.type}:${it.name}" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.danger
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            if (reqLines.isNotEmpty()) {
                                Text("Ingredient Deduction Preview", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(6.dp))
                                reqLines.forEach { line ->
                                    val need = InputGuards.round4(line.quantity * units)
                                    val mat = materials.firstOrNull { it.id == line.id }
                                        ?: materials.firstOrNull { it.name.trim().equals(line.name.trim(), ignoreCase = true) }
                                    val have = (mat?.quantity ?: 0.0) + (mat?.id?.let { restoredById[it] } ?: 0.0)
                                    val ok = have + 0.001 >= need
                                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(line.name, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "Need ${InputGuards.formatKg(need)} / Have ${InputGuards.formatKg(have)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (ok) accents.emerald else accents.danger
                                        )
                                    }
                                }
                            }
                            if (!isEntryOnlyFactory) {
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SarimHalfRingKpiBox("Est. Batch Cost", InputGuards.formatMoney(estCost, currency, masked), accents.gold, Modifier.weight(1f))
                                    SarimHalfRingKpiBox("Cost / Unit", InputGuards.formatMoney(desc?.perUnit ?: 0.0, currency, masked), accents.cyan, Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            SarimGradientButton(
                                text = when {
                                    !allInStock && reqLines.isNotEmpty() -> "Insufficient Raw Material"
                                    editingBatch != null -> "Update Batch"
                                    else -> "Produce Units & Deduct Stock"
                                },
                                onClick = {
                                    if (editingBatch != null) {
                                        showEditDiff = true
                                    } else {
                                        selectedFormula?.let { f ->
                                            vm.submitFactoryBatch(date, f, units, notes)
                                            notes = ""
                                        }
                                    }
                                },
                                enabled = selectedFormula != null && units > 0.0 && allInStock,
                                testTag = "factory_produce_btn"
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            1 -> {
                val materials = remember(snapshot.inventory, sortByQty) {
                    val base = FinanceEngine.materialRecords(snapshot.inventory)
                    if (sortByQty) base.sortedByDescending { it.quantity } else base.sortedBy { it.name.lowercase() }
                }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    if (materials.isEmpty()) {
                        item { EmptyStateCard("No raw material stock", "Tap Purchase to add raw materials and supplier lots.", "Add Purchase", onOpenRawPurchase) }
                    } else {
                        items(materials, key = { it.id }) { item ->
                            val lots = remember(snapshot.inventory, item.id) { FinanceEngine.purchasesOf(snapshot.inventory, item.id) }
                            val pending = remember(item, lots) { FinanceEngine.materialPending(item, lots) }
                            val low = item.quantity <= 0.0
                            SarimCard(
                                accentBorder = if (low) accents.gold else null,
                                modifier = Modifier.clickable { selectedMaterialLots = item }.testTag("factory_mat_${item.id}")
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                                        val unitDesc = if (item.purchaseUnitName.isNotBlank()) "Unit: ${item.purchaseUnitName} (1 = ${InputGuards.fmtNum(item.conversionFactor)} kg) • " else ""
                                        Text(
                                            "${unitDesc}Avg ${InputGuards.formatMoney(item.cost, currency, masked)}/kg • ${lots.size} lots" +
                                                if (pending > 0.01) " • Pending ${InputGuards.formatMoney(pending, currency, masked)}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accents.textMuted
                                        )
                                    }
                                    Text(
                                        InputGuards.formatKg(item.quantity),
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
                val pools = remember(snapshot.stores, snapshot.formulas, snapshot.factory, snapshot.production) {
                    FormulaEngine.buildUnitPools(snapshot.stores, snapshot.formulas, snapshot.factory, snapshot.production)
                }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (pools.isEmpty()) {
                        item { EmptyStateCard("No unit pools yet", "Create formulas and produce factory batches.") }
                    } else {
                        items(pools, key = { it.key }) { pool ->
                            SarimCard {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(pool.label, style = MaterialTheme.typography.titleMedium)
                                    Text("${InputGuards.fmtNum(pool.availableUnits)} avail", color = accents.purple, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    "Produced ${InputGuards.fmtNum(pool.producedUnits)} • Consumed ${InputGuards.fmtNum(pool.usedUnits)} • Cost/Unit ${InputGuards.formatMoney(pool.avgUnitCost, currency, masked)}",
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
                val activeFactory = remember(snapshot.factory) {
                    snapshot.factory.filter { it.deletedAt == null }.sortedByDescending { it.date }
                }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (activeFactory.isEmpty()) {
                        item { EmptyStateCard("No factory batches", "Batches recorded on the Produce tab will appear here.") }
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
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(rec.formulaName.ifBlank { FormulaEngine.storeLabelFor(rec.store, snapshot.stores).ifBlank { "Factory Batch" } }, style = MaterialTheme.typography.titleSmall)
                                                EditedBadge(rec.isEdited)
                                            }
                                            Text("${InputGuards.fmtNum(rec.units)} units", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
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
        FactoryBatchDetailSheet(
            rec = rec,
            stores = snapshot.stores,
            currency = currency,
            masked = masked,
            isAdmin = session.mode == "admin",
            vm = vm,
            onEdit = { startEditBatch(it) },
            onDismiss = { selectedFactoryRec = null }
        )
    }

    selectedMaterialLots?.let { mat ->
        val lots = remember(snapshot.inventory, mat.id) {
            FinanceEngine.purchasesOf(snapshot.inventory, mat.id).sortedByDescending { it.purchaseDate }
        }
        MaterialPurchaseLotsSheet(
            material = mat,
            lots = lots,
            currency = currency,
            masked = masked,
            isAdmin = session.mode == "admin",
            vm = vm,
            onDismiss = { selectedMaterialLots = null }
        )
    }

    if (showEditDiff && editingBatch != null && selectedFormula != null) {
        val orig = editingBatch!!
        val f = selectedFormula!!
        val units = (unitsStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
        EditDiffConfirmDialog(
            title = "Confirm Factory Batch Edit",
            rows = listOf(
                Triple("Date", orig.date, date),
                Triple("Formula", orig.formulaName, f.name),
                Triple("Units", InputGuards.fmtNum(orig.units), InputGuards.fmtNum(units)),
                Triple("Notes", orig.notes, notes.trim())
            ),
            onConfirm = {
                showEditDiff = false
                vm.editFactoryBatch(orig, date, f, units, notes) { resetBatchForm() }
            },
            onDismiss = { showEditDiff = false }
        )
    }

    if (showDiscardEdit) {
        DiscardEditDialog(
            onDiscard = {
                showDiscardEdit = false
                resetBatchForm()
            },
            onDismiss = { showDiscardEdit = false }
        )
    }
}
