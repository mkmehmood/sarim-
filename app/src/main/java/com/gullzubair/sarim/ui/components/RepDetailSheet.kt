package com.gullzubair.sarim.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.RepSalesAnalyticsEngine
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import java.util.Locale
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@Composable
fun RepDetailSheet(
    r: SalesRecord,
    snapshot: WorkspaceSnapshot,
    runningBalanceAfter: Double?,
    currency: String,
    masked: Boolean,
    isAdmin: Boolean,
    vm: SarimViewModel,
    onEditEntry: (SalesRecord) -> Unit,
    onOpenCustomerLedger: (String) -> Unit = {},
    onBlockedAction: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val context = LocalContext.current
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }
    val storeLabel = FormulaEngine.storeLabelFor(r.supplyStore, snapshot.stores)
    val isOldDebt = r.transactionType == "OLD_DEBT"
    val isRepRet = r.isRepReturn || r.paymentType == "REP_RETURN"
    val isColl = FinanceEngine.isCollectionRow(r)
    val isMoneyOut = r.paymentType == "MONEY_OUT"
    val custPool = snapshot.clients.ifEmpty { snapshot.customers }
    val effTotal = FinanceEngine.effectiveSaleValue(r, custPool, snapshot.stores)

    val displayAmount = when {
        isRepRet -> r.returnValue
        isColl -> if (r.collectedAmount > 0.0) r.collectedAmount else r.totalValue
        else -> effTotal
    }
    val typeLabel = when {
        isOldDebt -> "OLD DEBT"
        isRepRet -> "REP RETURN"
        isColl -> "COLLECTION"
        isMoneyOut -> "MONEY OUT"
        r.paymentType == "CREDIT" && r.creditReceived -> "CREDIT (SETTLED)"
        else -> r.paymentType
    }
    val canEdit = !r.isMerged && r.usedInCalcId.isBlank() && !r.isRepTransfer && !isOldDebt

    val isUnpaidCredit = r.paymentType == "CREDIT" && !r.creditReceived && !r.isMerged
    val sheetTone = when {
        isUnpaidCredit || isOldDebt || isMoneyOut -> SheetSurfaceTone.WARN_STRIPES
        else -> SheetSurfaceTone.GOOD
    }
    SarimDetailBottomSheet(
        title = r.customerName.uppercase(),
        rightTitle = InputGuards.formatMoney(displayAmount, currency, masked),
        subtitle = "${DateUtils.formatPretty(r.date).uppercase()} • $typeLabel • ${r.salesRep.ifBlank { "Rep" }}",
        surfaceTone = sheetTone,
        onDismiss = onDismiss,
        content = {
            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))
            if (storeLabel.isNotBlank()) {
                RepSheetRow("Store", storeLabel)
            }
            if (r.time.isNotBlank()) {
                RepSheetRow("Time", r.time)
            }
            when {
                isRepRet -> {
                    RepSheetRow("Returned Qty", InputGuards.formatKg(r.returnedQty))
                    if (r.expiredQty > 0.0) RepSheetRow("Expired -> CHORA", InputGuards.formatKg(r.expiredQty), accents.cyan)
                    if (r.returnValue > 0.0) RepSheetRow("Return Credit", InputGuards.formatMoney(r.returnValue, currency, masked), accents.emerald)
                }
                isColl -> {
                    RepSheetRow("Cash Collected", InputGuards.formatMoney(r.collectedAmount, currency, masked), accents.emerald)
                    if (r.expiredQty > 0.0) {
                        val expStr = if (masked || r.expiredValue <= 0.0) InputGuards.formatKg(r.expiredQty)
                        else "${InputGuards.formatKg(r.expiredQty)} (${InputGuards.formatMoney(r.expiredValue, currency, false)})"
                        RepSheetRow("Expired -> CHORA", expStr, accents.cyan)
                    }
                    if (r.allocations.isNotEmpty()) {
                        RepSheetRow("Linked Invoices", "${r.allocations.size} settled via FIFO", accents.emerald)
                    }
                }
                isMoneyOut -> {
                    RepSheetRow("Payout Amount", InputGuards.formatMoney(r.totalValue, currency, masked), accents.gold)
                }
                else -> {
                    if (r.quantity > 0.0) {
                        RepSheetRow(
                            "Quantity",
                            "${InputGuards.formatKg(r.quantity)} @ ${InputGuards.formatMoney(r.unitPrice, currency, masked)}"
                        )
                    }
                    if (isAdmin && !masked && r.totalCost > 0.0) {
                        RepSheetRow("Cost", InputGuards.formatMoney(r.totalCost, currency, false), accents.danger)
                        RepSheetRow(
                            "Profit",
                            InputGuards.formatMoney(r.profit, currency, false),
                            if (r.profit >= 0.0) accents.emerald else accents.danger
                        )
                    }
                    if (r.paymentType == "CREDIT") {
                        if (r.partialPaymentReceived > 0.0 && !r.creditReceived) {
                            RepSheetRow("Paid so far", InputGuards.formatMoney(r.partialPaymentReceived, currency, masked), accents.emerald)
                        }
                        val dueLeft = (effTotal - r.partialPaymentReceived).coerceAtLeast(0.0)
                        RepSheetRow(
                            if (r.creditReceived) "Status" else "Credit Due",
                            if (r.creditReceived) "Settled" else InputGuards.formatMoney(dueLeft, currency, masked),
                            if (r.creditReceived) accents.emerald else accents.gold
                        )
                    } else if (r.collectedAmount > 0.0) {
                        RepSheetRow("Collected", InputGuards.formatMoney(r.collectedAmount, currency, masked), accents.emerald)
                    }
                }
            }
            if (runningBalanceAfter != null) {
                RepSheetRow(
                    "Balance after",
                    InputGuards.formatMoney(runningBalanceAfter, currency, masked),
                    if (runningBalanceAfter > 0.01) accents.gold else accents.emerald
                )
            }
            if (r.gpsLat != null && r.gpsLng != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val uri = Uri.parse("geo:${r.gpsLat},${r.gpsLng}?q=${r.gpsLat},${r.gpsLng}(${Uri.encode(r.customerName)})")
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("GPS (Tap for Maps)", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(
                        text = String.format(Locale.US, "%.5f, %.5f", r.gpsLat, r.gpsLng) +
                            if (r.gpsConfirmed) " (Verified)" else "",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accents.cyan
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            if (r.usedInCalcId.isNotBlank()) {
                Text("Reconciled in Calculator (undo calculation to edit or delete)", style = MaterialTheme.typography.labelSmall, color = accents.gold)
                Spacer(Modifier.height(6.dp))
            }
            if (r.notes.isNotBlank()) {
                Text(r.notes, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                Spacer(Modifier.height(6.dp))
            }
            if (r.editHistory.isNotEmpty() || r.timestamp > 0L) {
                Spacer(Modifier.height(10.dp))
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
                        val reason = RepSalesAnalyticsEngine.getRepSaleBlockReason(snapshot, r, forEdit = false)
                        if (reason.isNotBlank()) {
                            onDismiss()
                            onBlockedAction(reason)
                        } else {
                            confirmReq = ConfirmRequest(
                                title = "Delete ${r.salesRep.ifBlank { "Rep" }} Record?",
                                message = deleteConfirmMessage(listOf(
                                    "Customer" to r.customerName,
                                    "Rep" to r.salesRep,
                                    "Date" to DateUtils.formatPretty(r.date),
                                    "Amount" to InputGuards.formatMoney(displayAmount, currency, masked)
                                )),
                                confirmText = "Delete",
                                danger = true,
                                tag = "rep_confirm_delete_btn"
                            ) {
                                onDismiss()
                                vm.deleteRepRecord(
                                    rec = r,
                                    displayAmount = InputGuards.formatMoney(displayAmount, currency, masked),
                                    onBlocked = onBlockedAction
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(0.32f),
                    testTag = "rep_delete_${r.id}"
                )
                if (canEdit) {
                    Surface(
                        onClick = {
                            val reason = RepSalesAnalyticsEngine.getRepSaleBlockReason(snapshot, r, forEdit = true)
                            if (reason.isNotBlank()) {
                                onDismiss()
                                onBlockedAction(reason)
                            } else {
                                onDismiss()
                                onEditEntry(r)
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.emerald),
                        modifier = Modifier.weight(0.34f).testTag("rep_edit_${r.id}")
                    ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center) {
                            Text("Edit Entry", style = MaterialTheme.typography.labelLarge, color = accents.emerald, maxLines = 1)
                        }
                    }
                }
                SarimGradientButton(
                    text = "Ledger",
                    onClick = {
                        onDismiss()
                        onOpenCustomerLedger(r.customerName)
                    },
                    modifier = Modifier.weight(if (canEdit) 0.34f else 0.68f),
                    testTag = "rep_detail_ledger_btn"
                )
            }
        }
    )
}

@Composable
private fun RepSheetRow(
    label: String,
    value: String,
    valueColor: Color? = null
) {
    val accents = LocalSarimAccents.current
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = valueColor ?: MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}
