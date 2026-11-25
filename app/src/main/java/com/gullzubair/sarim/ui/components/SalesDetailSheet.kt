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
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

@Composable
fun SalesDetailSheet(
    s: SalesRecord,
    stores: List<StoreConfig>,
    currency: String,
    masked: Boolean,
    isAdmin: Boolean,
    vm: SarimViewModel,
    onEditEntry: (SalesRecord) -> Unit,
    onEditCustomer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val storeLabel = FormulaEngine.storeLabelFor(s.supplyStore, stores)
    val displayVal = if (FinanceEngine.isCollectionRow(s)) s.collectedAmount else s.totalValue
    val canEdit = !s.isMerged && !s.isTransfer && !s.isRepTransfer

    SarimDetailBottomSheet(
        title = s.customerName.uppercase(),
        rightTitle = InputGuards.formatMoney(displayVal, currency, masked),
        subtitle = "${DateUtils.formatPretty(s.date).uppercase()} • ${s.paymentType}",
        onDismiss = onDismiss,
        content = {
            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))
            if (storeLabel.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Store", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(storeLabel, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
            }
            if (s.supplyDate.isNotBlank() && s.supplyDate != s.date) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Supply date", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(DateUtils.formatPretty(s.supplyDate), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
            }
            if (s.salesRep.isNotBlank() && !s.salesRep.equals("NONE", true)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sales Rep", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(s.salesRep, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
            }
            if (s.quantity > 0.0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Quantity", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text("${InputGuards.formatKg(s.quantity)} @ ${InputGuards.formatMoney(s.unitPrice, currency, masked)}", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Collected", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                Text(InputGuards.formatMoney(s.collectedAmount, currency, masked), style = MaterialTheme.typography.titleSmall, color = accents.emerald)
            }
            val dueLeft = (s.totalValue - s.partialPaymentReceived).coerceAtLeast(0.0)
            if (s.paymentType == "CREDIT" && !s.creditReceived) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Credit due", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(InputGuards.formatMoney(dueLeft, currency, masked), style = MaterialTheme.typography.titleSmall, color = accents.gold)
                }
            }
            if (s.expiredQty > 0.0) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Expired -> CHORA", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text("${InputGuards.formatKg(s.expiredQty)} (${InputGuards.formatMoney(s.expiredValue, currency, masked)})", style = MaterialTheme.typography.titleSmall, color = accents.cyan)
                }
            }
            if (s.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(s.notes, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            }
            if (s.editHistory.isNotEmpty() || s.createdAt > 0L) {
                Spacer(Modifier.height(14.dp))
                EditHistorySection(
                    editHistory = s.editHistory,
                    createdAtEpoch = s.createdAt,
                    addedLabel = "Recorded",
                    canDelete = isAdmin,
                    masked = masked,
                    onDeleteEntry = { idx -> vm.deleteRecordEditHistory("sales", s.id, idx) }
                )
            }
        },
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SarimDangerButton(
                    text = "Delete",
                    onClick = {
                        vm.softDeleteRecord(
                            collection = "sales",
                            docId = s.id,
                            displayName = s.customerName,
                            displayDetail = "${s.date} • ${s.paymentType}",
                            displayAmount = InputGuards.formatMoney(displayVal, currency, masked),
                            snapshotMap = WorkspaceJsonCodec.saleToMap(s)
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(0.32f),
                    testTag = "sales_delete_${s.id}"
                )
                if (canEdit) {
                    Surface(
                        onClick = {
                            onDismiss()
                            onEditEntry(s)
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.emerald),
                        modifier = Modifier.weight(0.34f).testTag("sales_edit_${s.id}")
                    ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center) {
                            Text("Edit Entry", style = MaterialTheme.typography.labelLarge, color = accents.emerald, maxLines = 1)
                        }
                    }
                }
                SarimGradientButton(
                    text = "Customer",
                    onClick = {
                        onDismiss()
                        onEditCustomer(s.customerName)
                    },
                    modifier = Modifier.weight(0.34f)
                )
            }
        }
    )
}
