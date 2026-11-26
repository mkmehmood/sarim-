package com.gullzubair.sarim.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.ExpenseRecord
import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

@Composable
fun PaymentTxDetailSheet(
    tx: PaymentTransaction,
    attachedPhotoDataUri: String? = null,
    currency: String,
    masked: Boolean,
    isAdmin: Boolean,
    vm: SarimViewModel,
    onOpenExpenseDetail: ((String) -> Unit)? = null,
    onEditEntry: (PaymentTransaction) -> Unit,
    onCopyToForm: (PaymentTransaction) -> Unit,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    var showPhotoLightbox by remember { mutableStateOf(false) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }
    val canEdit = !tx.isMerged && !tx.isTransfer && !tx.isSettled && !tx.isPayable &&
        tx.materialId.isBlank() && tx.supplierCreditAmount <= 0.0

    SarimDetailBottomSheet(
        title = tx.entityName.uppercase(),
        rightTitle = InputGuards.formatMoney(tx.amount, currency, masked),
        subtitle = buildString {
            append(DateUtils.formatPretty(tx.date).uppercase())
            append(" • ")
            append(if (tx.isExpense) "OPERATING EXPENSE" else tx.type)
            if (tx.entityType.isNotBlank() && !tx.isExpense) {
                append(" • ${tx.entityType}")
            }
            if (tx.isSettled) append(" • Settled")
        },
        onDismiss = onDismiss,
        content = {
            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))
            if (tx.description.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Description", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(tx.description, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
            }
            if (!attachedPhotoDataUri.isNullOrBlank()) {
                Text("Attached Photo", style = MaterialTheme.typography.labelMedium, color = accents.textMuted)
                Spacer(Modifier.height(6.dp))
                DecodedDataUrlImage(
                    dataUrl = attachedPhotoDataUri,
                    contentDescription = "Transaction photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showPhotoLightbox = true }
                        .testTag("pay_detail_photo")
                )
                Spacer(Modifier.height(8.dp))
            }
            if (tx.isExpense && onOpenExpenseDetail != null) {
                Text(
                    "View all \"${tx.entityName}\" records",
                    style = MaterialTheme.typography.labelLarge,
                    color = accents.emerald,
                    modifier = Modifier
                        .clickable {
                            val nm = tx.entityName
                            onDismiss()
                            onOpenExpenseDetail(nm)
                        }
                        .padding(vertical = 6.dp)
                        .testTag("pay_view_expense_${tx.id}")
                )
            }
            if (tx.editHistory.isNotEmpty() || tx.timestamp > 0L) {
                Spacer(Modifier.height(10.dp))
                EditHistorySection(
                    editHistory = tx.editHistory,
                    createdAtEpoch = tx.timestamp,
                    addedLabel = "Recorded",
                    canDelete = isAdmin,
                    masked = masked,
                    onDeleteEntry = { idx -> vm.deleteRecordEditHistory("transactions", tx.id, idx) }
                )
            }
        },
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SarimDangerButton(
                    text = "Delete",
                    onClick = {
                        val isPair = tx.isTransfer && tx.transferPairId.isNotBlank()
                        confirmReq = ConfirmRequest(
                            title = if (isPair) "Delete Transfer Pair?" else "Delete Payment ${tx.type}?",
                            message = deleteConfirmMessage(listOf(
                                "Account" to tx.entityName,
                                "Date" to DateUtils.formatPretty(tx.date),
                                "Amount" to InputGuards.formatMoney(tx.amount, currency, masked)
                            ) + (if (isPair) listOf("Note" to "Both sides of the transfer are removed") else emptyList())),
                            confirmText = "Delete",
                            danger = true,
                            tag = "pay_confirm_delete_btn"
                        ) {
                            if (isPair) {
                                vm.recyclePair("transactions", tx.transferPairId, tx.id, tx.entityName)
                            } else {
                                vm.softDeleteRecord(
                                    collection = "transactions",
                                    docId = tx.id,
                                    displayName = tx.entityName,
                                    displayDetail = "${tx.date} • ${tx.type}",
                                    displayAmount = InputGuards.formatMoney(tx.amount, currency, masked),
                                    snapshotMap = WorkspaceJsonCodec.transactionToMap(tx)
                                )
                            }
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(0.32f),
                    testTag = "pay_delete_${tx.id}"
                )
                if (canEdit) {
                    Surface(
                        onClick = {
                            onDismiss()
                            onEditEntry(tx)
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.emerald),
                        modifier = Modifier.weight(0.36f).testTag("pay_edit_${tx.id}")
                    ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center) {
                            Text("Edit Entry", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                        }
                    }
                }
                SarimGradientButton(
                    text = "Copy",
                    onClick = {
                        onDismiss()
                        onCopyToForm(tx)
                    },
                    modifier = Modifier.weight(if (canEdit) 0.32f else 0.68f)
                )
            }
        }
    )

    if (showPhotoLightbox && !attachedPhotoDataUri.isNullOrBlank()) {
        ProductionPhotoLightboxDialog(
            photos = listOf(attachedPhotoDataUri),
            onDismiss = { showPhotoLightbox = false }
        )
    }
}

@Composable
fun ExpenseTxDetailSheet(
    exp: ExpenseRecord,
    attachedPhotoDataUri: String? = null,
    currency: String,
    masked: Boolean,
    isAdmin: Boolean,
    vm: SarimViewModel,
    onOpenExpenseDetail: (String) -> Unit,
    onEditEntry: (ExpenseRecord) -> Unit,
    onCopyToForm: (ExpenseRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    var showPhotoLightbox by remember { mutableStateOf(false) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }
    val canEdit = !exp.isMerged
    SarimDetailBottomSheet(
        title = exp.name.uppercase(),
        rightTitle = InputGuards.formatMoney(exp.amount, currency, masked),
        subtitle = "${DateUtils.formatPretty(exp.date).uppercase()} • EXPENSE • ${exp.category}",
        onDismiss = onDismiss,
        content = {
            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
            Spacer(Modifier.height(10.dp))
            if (exp.description.isNotBlank() || exp.notes.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Notes", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(exp.notes.ifBlank { exp.description }, style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
            }
            if (!attachedPhotoDataUri.isNullOrBlank()) {
                Text("Attached Photo", style = MaterialTheme.typography.labelMedium, color = accents.textMuted)
                Spacer(Modifier.height(6.dp))
                DecodedDataUrlImage(
                    dataUrl = attachedPhotoDataUri,
                    contentDescription = "Expense photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showPhotoLightbox = true }
                        .testTag("exp_detail_photo")
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "View all \"${exp.name}\" records",
                style = MaterialTheme.typography.labelLarge,
                color = accents.emerald,
                modifier = Modifier
                    .clickable {
                        val n = exp.name
                        onDismiss()
                        onOpenExpenseDetail(n)
                    }
                    .padding(vertical = 6.dp)
                    .testTag("pay_view_expense_${exp.id}")
            )
            if (exp.editHistory.isNotEmpty() || exp.timestamp > 0L) {
                Spacer(Modifier.height(10.dp))
                EditHistorySection(
                    editHistory = exp.editHistory,
                    createdAtEpoch = exp.timestamp,
                    addedLabel = "Recorded",
                    canDelete = isAdmin,
                    masked = masked,
                    onDeleteEntry = { idx -> vm.deleteRecordEditHistory("expenses", exp.id, idx) }
                )
            }
        },
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SarimDangerButton(
                    text = "Delete",
                    onClick = {
                        confirmReq = ConfirmRequest(
                            title = "Delete Expense?",
                            message = deleteConfirmMessage(listOf(
                                "Expense" to exp.name,
                                "Category" to exp.category,
                                "Date" to DateUtils.formatPretty(exp.date),
                                "Amount" to InputGuards.formatMoney(exp.amount, currency, masked)
                            )),
                            confirmText = "Delete",
                            danger = true,
                            tag = "exp_confirm_delete_btn"
                        ) {
                            vm.softDeleteRecord(
                                collection = "expenses",
                                docId = exp.id,
                                displayName = exp.name,
                                displayDetail = "${exp.date} • ${exp.category}",
                                displayAmount = InputGuards.formatMoney(exp.amount, currency, masked),
                                snapshotMap = WorkspaceJsonCodec.expenseToMap(exp)
                            )
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(0.32f),
                    testTag = "exp_delete_${exp.id}"
                )
                if (canEdit) {
                    Surface(
                        onClick = {
                            onDismiss()
                            onEditEntry(exp)
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.emerald),
                        modifier = Modifier.weight(0.36f).testTag("exp_edit_${exp.id}")
                    ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center) {
                            Text("Edit Entry", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                        }
                    }
                }
                SarimGradientButton(
                    text = "Copy",
                    onClick = {
                        onDismiss()
                        onCopyToForm(exp)
                    },
                    modifier = Modifier.weight(if (canEdit) 0.32f else 0.68f)
                )
            }
        }
    )

    if (showPhotoLightbox && !attachedPhotoDataUri.isNullOrBlank()) {
        ProductionPhotoLightboxDialog(
            photos = listOf(attachedPhotoDataUri),
            onDismiss = { showPhotoLightbox = false }
        )
    }
}
