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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.SalesAnalyticsEngine
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

@Composable
fun SalesDetailSheet(
    s: SalesRecord,
    snapshot: WorkspaceSnapshot,
    runningBalanceAfter: Double?,
    currency: String,
    masked: Boolean,
    isSalesRestricted: Boolean,
    isAdmin: Boolean,
    vm: SarimViewModel,
    onEditEntry: (SalesRecord) -> Unit,
    onOpenCustomerDetail: (String) -> Unit,
    onBlockedAction: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val storeLabel = FormulaEngine.storeLabelFor(s.supplyStore, snapshot.stores)
    val isOldDebt = s.transactionType == "OLD_DEBT"
    val isRepRet = s.isRepReturn || s.paymentType == "REP_RETURN"
    val isColl = FinanceEngine.isCollectionRow(s)
    val isMoneyOut = s.paymentType == "MONEY_OUT"
    val displayVal = when {
        isRepRet -> s.returnValue
        isColl -> if (s.collectedAmount > 0.0) s.collectedAmount else s.totalValue
        else -> s.totalValue
    }
    val typeLabel = when {
        isOldDebt -> "OLD DEBT"
        isRepRet -> "REP RETURN"
        isColl -> "COLLECTION"
        isMoneyOut -> "MONEY OUT"
        s.paymentType == "CREDIT" && s.creditReceived -> "CREDIT (SETTLED)"
        else -> s.paymentType
    }
    val canEdit = !s.isMerged && !s.isTransfer && !s.isRepTransfer && !isOldDebt && !isMoneyOut

    SarimDetailBottomSheet(
        title = s.customerName.uppercase(),
        rightTitle = if (isSalesRestricted) "" else InputGuards.formatMoney(displayVal, currency, masked),
        subtitle = "${DateUtils.formatPretty(s.date).uppercase()} • $typeLabel",
        onDismiss = onDismiss,
        content = {
            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))
            if (storeLabel.isNotBlank()) {
                SheetDetailRow("Store", storeLabel)
            }
            if (s.supplyDate.isNotBlank() && s.supplyDate != s.date) {
                SheetDetailRow("Supply date", DateUtils.formatPretty(s.supplyDate))
            }
            if (s.salesRep.isNotBlank() && !s.salesRep.equals("NONE", true)) {
                SheetDetailRow("Sales Rep", s.salesRep)
            }
            if (s.isTransfer || s.isRepTransfer) {
                SheetDetailRow("Source", "Calculator Stock Transfer")
            }
            if (isRepRet) {
                SheetDetailRow("Returned Qty", InputGuards.formatKg(s.returnedQty))
                SheetDetailRow("Expired Qty", InputGuards.formatKg(s.expiredQty))
                if (!isSalesRestricted && s.returnValue > 0.0) {
                    SheetDetailRow("Balance Credit", InputGuards.formatMoney(s.returnValue, currency, masked), accents.emerald)
                }
            } else if (isColl) {
                if (!isSalesRestricted) {
                    SheetDetailRow("Cash Collected", InputGuards.formatMoney(s.collectedAmount, currency, masked), accents.emerald)
                }
                if (s.expiredQty > 0.0) {
                    val expStr = if (isSalesRestricted) {
                        InputGuards.formatKg(s.expiredQty)
                    } else {
                        "${InputGuards.formatKg(s.expiredQty)} (${InputGuards.formatMoney(s.expiredValue, currency, masked)})"
                    }
                    SheetDetailRow("Expired -> CHORA", expStr, accents.cyan)
                }
            } else if (isMoneyOut) {
                if (!isSalesRestricted) {
                    SheetDetailRow("Payout Amount", InputGuards.formatMoney(s.totalValue, currency, masked), accents.gold)
                }
            } else {
                if (s.quantity > 0.0) {
                    val qtyText = if (isSalesRestricted) {
                        InputGuards.formatKg(s.quantity)
                    } else {
                        "${InputGuards.formatKg(s.quantity)} @ ${InputGuards.formatMoney(s.unitPrice, currency, masked)}"
                    }
                    SheetDetailRow("Quantity", qtyText)
                }
                if (!isSalesRestricted) {
                    if (s.totalCost > 0.0) {
                        SheetDetailRow("Total Cost", InputGuards.formatMoney(s.totalCost, currency, masked), accents.danger)
                    }
                    if (!isOldDebt) {
                        SheetDetailRow(
                            "Net Profit",
                            InputGuards.formatMoney(s.profit, currency, masked),
                            if (s.profit >= 0.0) accents.emerald else accents.danger
                        )
                    }
                    if (s.partialPaymentReceived > 0.0 && !s.creditReceived) {
                        SheetDetailRow("Paid so far", InputGuards.formatMoney(s.partialPaymentReceived, currency, masked), accents.emerald)
                    }
                    val effTotal = FinanceEngine.effectiveSaleValue(s, snapshot.customers, snapshot.stores)
                    val dueLeft = (effTotal - s.partialPaymentReceived).coerceAtLeast(0.0)
                    if (s.paymentType == "CREDIT" && !s.creditReceived) {
                        SheetDetailRow("Credit due", InputGuards.formatMoney(dueLeft, currency, masked), accents.gold)
                    } else if (s.paymentType == "CREDIT" && s.creditReceived) {
                        SheetDetailRow("Status", "Settled", accents.emerald)
                    }
                }
            }
            if (!isSalesRestricted && runningBalanceAfter != null) {
                SheetDetailRow(
                    "Balance after",
                    InputGuards.formatMoney(runningBalanceAfter, currency, masked),
                    if (runningBalanceAfter > 0.01) accents.gold else accents.emerald
                )
            }
            if (s.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(s.notes, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            }
            if (!isSalesRestricted && (s.editHistory.isNotEmpty() || s.createdAt > 0L)) {
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
                        val reason = SalesAnalyticsEngine.getSaleBlockReason(snapshot, s, forEdit = false)
                        if (reason.isNotBlank()) {
                            onDismiss()
                            onBlockedAction(reason)
                        } else {
                            vm.softDeleteRecord(
                                collection = "sales",
                                docId = s.id,
                                displayName = s.customerName,
                                displayDetail = "${s.date} • $typeLabel",
                                displayAmount = InputGuards.formatMoney(displayVal, currency, masked),
                                snapshotMap = WorkspaceJsonCodec.saleToMap(s)
                            )
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(0.32f),
                    testTag = "sales_delete_${s.id}"
                )
                if (canEdit) {
                    Surface(
                        onClick = {
                            val reason = SalesAnalyticsEngine.getSaleBlockReason(snapshot, s, forEdit = true)
                            if (reason.isNotBlank()) {
                                onDismiss()
                                onBlockedAction(reason)
                            } else {
                                onDismiss()
                                onEditEntry(s)
                            }
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
                if (!isSalesRestricted) {
                    SarimGradientButton(
                        text = "Statement",
                        onClick = {
                            onDismiss()
                            onOpenCustomerDetail(s.customerName)
                        },
                        modifier = Modifier.weight(0.34f),
                        testTag = "sales_detail_statement_btn"
                    )
                }
            }
        }
    )
}

@Composable
private fun SheetDetailRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color? = null
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
