package com.gullzubair.sarim.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.FactoryRecord
import com.gullzubair.sarim.data.model.InventoryRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

@Composable
fun FactoryBatchDetailSheet(
    rec: FactoryRecord,
    stores: List<StoreConfig>,
    currency: String,
    masked: Boolean,
    isAdmin: Boolean,
    vm: SarimViewModel,
    onEdit: (FactoryRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val canEdit = !rec.isMerged
    SarimDetailBottomSheet(
        title = rec.formulaName.ifBlank { FormulaEngine.storeLabelFor(rec.store, stores).ifBlank { "Factory Batch" } },
        rightTitle = InputGuards.formatMoney(rec.totalCost, currency, masked),
        subtitle = "${DateUtils.formatPretty(rec.date).uppercase()} • ${InputGuards.fmtNum(rec.units)} UNITS",
        onDismiss = onDismiss,
        content = {
            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Materials Cost", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                Text(InputGuards.formatMoney(rec.materialsCost, currency, masked), style = MaterialTheme.typography.titleSmall)
            }
            if (rec.additionalCost > 0.0) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Additional Cost", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(InputGuards.formatMoney(rec.additionalCost, currency, masked), style = MaterialTheme.typography.titleSmall)
                }
            }
            val mats = rec.materialsUsed.ifEmpty { rec.rawMaterialsUsed }
            if (mats.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Materials Used", style = MaterialTheme.typography.labelMedium, color = accents.textMuted)
                mats.forEach { m ->
                    val qty = (m["qty"] as? Number)?.toDouble() ?: (m["quantity"] as? Number)?.toDouble() ?: 0.0
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(m["name"]?.toString().orEmpty(), style = MaterialTheme.typography.bodySmall)
                        Text(InputGuards.formatKg(qty), style = MaterialTheme.typography.bodySmall, color = accents.emerald)
                    }
                }
            }
            if (rec.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(rec.notes, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            }
            if (rec.editHistory.isNotEmpty() || rec.timestamp > 0L) {
                Spacer(Modifier.height(14.dp))
                EditHistorySection(
                    editHistory = rec.editHistory,
                    createdAtEpoch = rec.timestamp,
                    addedLabel = "Produced",
                    canDelete = isAdmin,
                    masked = masked,
                    onDeleteEntry = { idx -> vm.deleteRecordEditHistory("factory", rec.id, idx) }
                )
            }
        },
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SarimDangerButton(
                    text = "Delete",
                    onClick = {
                        vm.softDeleteRecord(
                            collection = "factory",
                            docId = rec.id,
                            displayName = rec.formulaName.ifBlank { "Factory Batch" },
                            displayDetail = "${rec.date} • ${InputGuards.fmtNum(rec.units)} units",
                            displayAmount = InputGuards.formatMoney(rec.totalCost, currency, masked),
                            snapshotMap = WorkspaceJsonCodec.factoryToMap(rec)
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(if (canEdit) 0.45f else 1f),
                    testTag = "factory_delete_${rec.id}"
                )
                if (canEdit) {
                    SarimGradientButton(
                        text = "Edit Batch",
                        onClick = {
                            onDismiss()
                            onEdit(rec)
                        },
                        modifier = Modifier.weight(0.55f),
                        testTag = "factory_edit_${rec.id}"
                    )
                }
            }
        }
    )
}

@Composable
fun MaterialPurchaseLotsSheet(
    material: InventoryRecord,
    lots: List<InventoryRecord>,
    currency: String,
    masked: Boolean,
    isAdmin: Boolean,
    vm: SarimViewModel,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    var editingLot by remember { mutableStateOf<InventoryRecord?>(null) }

    SarimDetailBottomSheet(
        title = material.name.uppercase(),
        rightTitle = InputGuards.formatKg(material.quantity),
        subtitle = "Avg ${InputGuards.formatMoney(material.cost, currency, masked)}/kg • ${lots.size} purchase lot(s)",
        onDismiss = onDismiss,
        content = {
            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))
            if (lots.isEmpty()) {
                Text("No purchase lots recorded for this material yet.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lots.forEach { lot ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = accents.inputFill,
                            border = BorderStroke(1.dp, accents.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(DateUtils.formatPretty(lot.purchaseDate), style = MaterialTheme.typography.titleSmall)
                                        EditedBadge(lot.isEdited)
                                    }
                                    Text(InputGuards.formatKg(lot.purchaseKg), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                                }
                                Text(
                                    "${InputGuards.fmtNum(lot.purchaseQuantity)} ${lot.purchaseUnitName.ifBlank { "unit" }} × ${InputGuards.fmtNum(lot.conversionFactor)} kg • ${InputGuards.formatMoney(lot.totalValue, currency, masked)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted
                                )
                                if (lot.supplierName.isNotBlank() || lot.totalPayable > 0.01) {
                                    Text(
                                        listOfNotNull(
                                            lot.supplierName.takeIf { it.isNotBlank() },
                                            if (lot.totalPayable > 0.01) "Payable ${InputGuards.formatMoney(lot.totalPayable, currency, masked)}" else "Paid"
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (lot.totalPayable > 0.01) accents.gold else accents.textMuted
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { editingLot = lot }, modifier = Modifier.testTag("lot_edit_${lot.id}")) {
                                        Text("Edit", color = accents.emerald)
                                    }
                                    TextButton(onClick = { vm.deletePurchaseLot(lot) }, modifier = Modifier.testTag("lot_del_${lot.id}")) {
                                        Text("Delete", color = accents.danger)
                                    }
                                }
                                if (lot.editHistory.isNotEmpty()) {
                                    EditHistorySection(
                                        editHistory = lot.editHistory,
                                        createdAtEpoch = lot.timestamp,
                                        addedLabel = "Purchased",
                                        canDelete = isAdmin,
                                        masked = masked,
                                        onDeleteEntry = { idx -> vm.deleteRecordEditHistory("inventory", lot.id, idx) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        footer = {
            SarimGradientButton(text = "Close", onClick = onDismiss)
        }
    )

    editingLot?.let { lot ->
        EditPurchaseLotDialog(
            lot = lot,
            onSave = { d, uName, factor, qtyUnits, costUnit, noteStr ->
                vm.editPurchaseLot(lot, d, uName, factor, qtyUnits, costUnit, noteStr) {
                    editingLot = null
                }
            },
            onDismiss = { editingLot = null }
        )
    }
}

@Composable
private fun EditPurchaseLotDialog(
    lot: InventoryRecord,
    onSave: (String, String, Double, Double, Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    var date by remember(lot) { mutableStateOf(lot.purchaseDate) }
    var unitName by remember(lot) { mutableStateOf(lot.purchaseUnitName.ifBlank { "kg" }) }
    var factorStr by remember(lot) { mutableStateOf(lot.conversionFactor.toString()) }
    var qtyStr by remember(lot) { mutableStateOf(lot.purchaseQuantity.toString()) }
    var costStr by remember(lot) { mutableStateOf(lot.purchaseCost.toString()) }
    var notes by remember(lot) { mutableStateOf(lot.notes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Purchase Lot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField("Purchase Date", date, { date = it }, "lot_edit_date")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SarimPillInput(unitName, { unitName = it.take(24) }, label = "UNIT", modifier = Modifier.weight(1f))
                    DecimalField("KG / UNIT", factorStr, { factorStr = it }, "lot_edit_factor", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField("UNITS", qtyStr, { qtyStr = it }, "lot_edit_qty", Modifier.weight(1f))
                    DecimalField("COST / UNIT", costStr, { costStr = it }, "lot_edit_cost", Modifier.weight(1f))
                }
                SarimPillInput(notes, { notes = it.take(300) }, label = "NOTES")
            }
        },
        confirmButton = {
            val f = factorStr.toDoubleOrNull() ?: 0.0
            val q = qtyStr.toDoubleOrNull() ?: 0.0
            val c = costStr.toDoubleOrNull() ?: 0.0
            TextButton(
                onClick = { onSave(date, unitName, f, q, c, notes) },
                enabled = f > 0.0 && q > 0.0 && c > 0.0,
                modifier = Modifier.testTag("lot_edit_save_btn")
            ) {
                Text("Save", color = accents.emerald, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
