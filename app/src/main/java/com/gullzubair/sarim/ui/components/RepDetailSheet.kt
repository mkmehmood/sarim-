package com.gullzubair.sarim.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

@Composable
fun RepDetailSheet(
    r: SalesRecord,
    stores: List<StoreConfig>,
    currency: String,
    masked: Boolean,
    isAdmin: Boolean,
    vm: SarimViewModel,
    onEditEntry: (SalesRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val storeLabel = FormulaEngine.storeLabelFor(r.supplyStore, stores)
    val displayAmount = when {
        r.isRepReturn -> r.returnValue
        r.totalValue > 0.0 -> r.totalValue
        else -> r.collectedAmount
    }
    val canEdit = !r.isMerged && r.usedInCalcId.isBlank() && !r.isRepTransfer

    SarimDetailBottomSheet(
        title = r.customerName.uppercase(),
        rightTitle = InputGuards.formatMoney(displayAmount, currency, masked),
        subtitle = "${DateUtils.formatPretty(r.date).uppercase()} • ${r.salesRep}",
        onDismiss = onDismiss,
        content = {
            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Type", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                Text(r.paymentType, style = MaterialTheme.typography.titleSmall)
            }
            if (storeLabel.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Store", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(storeLabel, style = MaterialTheme.typography.titleSmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (r.isRepReturn) "Returned Qty" else "Quantity", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                Text(InputGuards.formatKg(if (r.isRepReturn) r.returnedQty else r.quantity), style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Collected", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                Text(InputGuards.formatMoney(r.collectedAmount, currency, masked), style = MaterialTheme.typography.titleSmall, color = accents.emerald)
            }
            if (r.expiredQty > 0.0) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Expired -> CHORA", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(InputGuards.formatKg(r.expiredQty), style = MaterialTheme.typography.titleSmall, color = accents.cyan)
                }
            }
            if (r.gpsLat != null && r.gpsLng != null) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GPS", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text("${"%.5f".format(r.gpsLat)}, ${"%.5f".format(r.gpsLng)}", style = MaterialTheme.typography.bodySmall, color = accents.cyan)
                }
            }
            if (r.usedInCalcId.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Reconciled in Calculator (undo calculation to edit)", style = MaterialTheme.typography.labelSmall, color = accents.gold)
            }
            if (r.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(r.notes, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            }
            if (r.editHistory.isNotEmpty() || r.timestamp > 0L) {
                Spacer(Modifier.height(14.dp))
                EditHistorySection(
                    editHistory = r.editHistory,
                    createdAtEpoch = r.timestamp,
                    addedLabel = "Recorded",
                    canDelete = isAdmin,
                    masked = masked,
                    onDeleteEntry = { idx -> vm.deleteRecordEditHistory("rep", r.id, idx) }
                )
            }
        },
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SarimDangerButton(
                    text = "Delete",
                    onClick = {
                        vm.softDeleteRecord(
                            collection = "rep",
                            docId = r.id,
                            displayName = r.customerName,
                            displayDetail = "${r.date} • ${r.salesRep}",
                            displayAmount = InputGuards.formatMoney(displayAmount, currency, masked),
                            snapshotMap = WorkspaceJsonCodec.saleToMap(r)
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(if (canEdit) 0.45f else 1f),
                    testTag = "rep_delete_${r.id}"
                )
                if (canEdit) {
                    Surface(
                        onClick = {
                            onDismiss()
                            onEditEntry(r)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.emerald),
                        modifier = Modifier.weight(0.55f).testTag("rep_edit_${r.id}")
                    ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center) {
                            Text("Edit Entry", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                        }
                    }
                }
            }
        }
    )
}
