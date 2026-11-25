package com.gullzubair.sarim.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.data.model.ProductionRecord
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
import com.gullzubair.sarim.ui.components.SarimNoteBanner
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.components.SarimYearMonthDayTree
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

@Composable
fun ApprovalsScreen(
    snapshot: WorkspaceSnapshot,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val isAdmin = snapshot.profile.isAdmin
    val pendingList = remember(snapshot.pendingApprovals) { snapshot.pendingApprovals.filter { it.status == "pending" } }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = "APPROVALS",
            onBack = onBack,
            trailingContent = if (isAdmin && pendingList.isNotEmpty()) {
                {
                    Surface(
                        onClick = { pendingList.forEach { vm.reviewPending(it, true) } },
                        shape = RoundedCornerShape(14.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.emerald),
                        modifier = Modifier.testTag("approvals_approve_all_btn")
                    ) {
                        Text("Approve All", style = MaterialTheme.typography.labelLarge, color = accents.emerald, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            } else null
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pendingList.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "All caught up",
                        detail = "Nothing needs your approval right now.",
                        icon = Icons.Default.CheckCircleOutline
                    )
                }
            } else {
                items(pendingList, key = { it.id }) { item ->
                    SarimCard(accentBorder = accents.gold) {
                        Text(item.summaryTitle.ifBlank { "${item.collection.uppercase()} ${item.action.uppercase()}" }, style = MaterialTheme.typography.titleMedium)
                        if (item.summaryBody.isNotBlank()) {
                            Text(item.summaryBody, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Submitted by ${item.submittedByName.ifBlank { item.submittedByEmail }} • ${item.collection.uppercase()} • ${item.action.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = accents.cyan
                        )
                        if (isAdmin) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimGradientButton(
                                    text = "Approve",
                                    onClick = { vm.reviewPending(item, true) },
                                    modifier = Modifier.weight(1f),
                                    testTag = "approve_${item.id}"
                                )
                                OutlinedButton(
                                    onClick = { vm.reviewPending(item, false) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accents.danger),
                                    modifier = Modifier.weight(1f).height(50.dp).testTag("reject_${item.id}")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reject")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StockTransferScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val activeStores = remember(snapshot.stores) { snapshot.stores.filter { it.deletedAt == null && it.active } }
    val stocks = remember(snapshot) { FinanceEngine.computeStoreStockSnapshots(snapshot) }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var fromStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var toStore by remember(activeStores) { mutableStateOf(activeStores.getOrElse(1) { activeStores.firstOrNull() }) }
    var qtyStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedTransfer by remember { mutableStateOf<ProductionRecord?>(null) }

    val availableAtSource = remember(fromStore, stocks) {
        stocks.firstOrNull { it.storeKey.equals(fromStore?.key ?: "", true) }?.availableKg ?: 0.0
    }
    val transferOutRecords = remember(snapshot.production) {
        snapshot.production.filter { it.deletedAt == null && it.isTransfer && it.transferDirection == "out" }
            .sortedWith(compareByDescending<ProductionRecord> { it.date }.thenByDescending { it.timestamp })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "STOCK TRANSFER", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                SarimCard {
                    Text(
                        "MOVE STOCK BETWEEN STORES",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PickerField(
                            label = "From Store",
                            selectedLabel = fromStore?.name ?: "Select",
                            items = activeStores,
                            itemLabel = { it.name },
                            onSelect = { fromStore = it },
                            testTag = "st_from_picker",
                            modifier = Modifier.weight(1f)
                        )
                        PickerField(
                            label = "To Store",
                            selectedLabel = toStore?.name ?: "Select",
                            items = activeStores,
                            itemLabel = { it.name },
                            onSelect = { toStore = it },
                            testTag = "st_to_picker",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DatePickerField("Date", date, { date = it }, "st_date", Modifier.weight(1f))
                        DecimalField("Quantity (kg)", qtyStr, { qtyStr = it }, "st_qty_input", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    SarimPillInput(
                        value = notes,
                        onValueChange = { notes = it.take(500) },
                        label = "NOTE (OPTIONAL)",
                        placeholder = "e.g. Balancing stock ahead of weekend rush",
                        testTag = "st_notes_input"
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${InputGuards.formatKg(availableAtSource)} available at ${fromStore?.name ?: "store"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (availableAtSource <= 0.0) accents.danger else accents.emerald
                    )
                    Spacer(Modifier.height(12.dp))
                    val qty = qtyStr.toDoubleOrNull() ?: 0.0
                    SarimGradientButton(
                        text = "Transfer Stock",
                        onClick = {
                            if (fromStore != null && toStore != null) {
                                vm.submitStockTransfer(date, fromStore!!, toStore!!, qty, notes)
                                qtyStr = ""
                                notes = ""
                            }
                        },
                        enabled = fromStore != null && toStore != null && !fromStore!!.key.equals(toStore!!.key, true) && qty > 0.0,
                        testTag = "st_save_btn"
                    )
                }
            }

            item {
                Text(
                    "TRANSFER HISTORY",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = accents.textMuted
                )
            }

            if (transferOutRecords.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("No stock transfers recorded yet.", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    }
                }
            } else {
                item {
                    SarimYearMonthDayTree(
                        items = transferOutRecords,
                        dateOf = { it.date },
                        summaryLabel = { list -> InputGuards.formatKg(list.sumOf { kotlin.math.abs(it.net) }) },
                        itemRow = { rec ->
                            val fromLbl = FormulaEngine.storeLabelFor(rec.store, snapshot.stores)
                            val toLbl = FormulaEngine.storeLabelFor(rec.transferPeerStore, snapshot.stores)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedTransfer = rec }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("$fromLbl -> $toLbl", style = MaterialTheme.typography.titleSmall)
                                    Text("TRANSFER", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                }
                                Text(
                                    InputGuards.formatKg(kotlin.math.abs(rec.net)),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    )
                }
            }

            item {
                SarimNoteBanner("Note: A transfer is only allowed up to the stock currently available at the source store. Transfers are recorded against both stores' stock history and sync across devices.")
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    selectedTransfer?.let { tr ->
        val absQty = kotlin.math.abs(tr.net)
        val fromLbl = FormulaEngine.storeLabelFor(tr.store, snapshot.stores)
        val toLbl = FormulaEngine.storeLabelFor(tr.transferPeerStore, snapshot.stores)
        val noteStr = tr.transferNote.ifBlank { tr.returnNote }
        SarimDetailBottomSheet(
            title = "$fromLbl -> $toLbl",
            rightTitle = InputGuards.formatKg(absQty),
            subtitle = DateUtils.formatPretty(tr.date).uppercase(),
            onDismiss = { selectedTransfer = null },
            content = {
                if (noteStr.isNotBlank()) {
                    Text(noteStr, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                }
            },
            footer = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SarimDangerButton(
                        text = "Delete",
                        onClick = {
                            vm.recyclePair("production", tr.transferPairId, tr.id, "Transfer $fromLbl -> $toLbl")
                            selectedTransfer = null
                        },
                        modifier = Modifier.weight(0.38f)
                    )
                    SarimGradientButton(
                        text = "Edit",
                        onClick = {
                            fromStore = activeStores.firstOrNull { it.key.equals(tr.store, true) || it.name.equals(tr.store, true) } ?: fromStore
                            toStore = activeStores.firstOrNull { it.key.equals(tr.transferPeerStore, true) || it.name.equals(tr.transferPeerStore, true) } ?: toStore
                            date = tr.date
                            qtyStr = absQty.toString()
                            notes = noteStr
                            selectedTransfer = null
                        },
                        modifier = Modifier.weight(0.62f)
                    )
                }
            }
        )
    }
}

@Composable
fun PaymentTransferScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = vm.sessionInfoFor(snapshot.profile).restrictMoney || snapshot.settings.hideSensitiveMoney
    val activeEntities = remember(snapshot.entities) {
        snapshot.entities.filter { it.deletedAt == null && !it.isExpenseEntity && it.type != "expense" }
    }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var fromEntity by remember(activeEntities) { mutableStateOf(activeEntities.firstOrNull()) }
    var toEntity by remember(activeEntities) { mutableStateOf(activeEntities.getOrElse(1) { activeEntities.firstOrNull() }) }
    var amountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var attachedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedTransfer by remember { mutableStateOf<PaymentTransaction?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            attachedUri = uri
            if (notes.isBlank()) notes = "Voucher photo attached"
        }
    }

    val transferHistory = remember(snapshot.transactions) {
        snapshot.transactions.filter { it.deletedAt == null && it.isTransfer && it.transferDirection == "out" }
            .sortedWith(compareByDescending<PaymentTransaction> { it.date }.thenByDescending { it.timestamp })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "PAYMENT TRANSFER", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                SarimCard {
                    Text(
                        "MOVE BALANCE BETWEEN ENTITIES",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PickerField("From Entity", fromEntity?.name ?: "Select entity...", activeEntities, { it.name }, { fromEntity = it }, "pt_from_picker", Modifier.weight(1f))
                        PickerField("To Entity", toEntity?.name ?: "Select entity...", activeEntities, { it.name }, { toEntity = it }, "pt_to_picker", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DatePickerField("Date", date, { date = it }, "pt_date", Modifier.weight(1f))
                        DecimalField("Amount", amountStr, { amountStr = it }, "pt_amount_input", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "NOTE (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                        color = accents.textMuted,
                        modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SarimPillInput(
                            value = notes,
                            onValueChange = { notes = it.take(500) },
                            placeholder = "e.g. Settling shared supplier bill",
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            shape = RoundedCornerShape(24.dp),
                            color = if (attachedUri != null) accents.softPillFill else accents.inputFill,
                            border = BorderStroke(1.dp, if (attachedUri != null) accents.emerald else accents.borderSubtle),
                            modifier = Modifier.size(50.dp).testTag("pt_photo_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.PhotoCamera, contentDescription = "Attach voucher photo", tint = accents.emerald)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    SarimGradientButton(
                        text = "Transfer Payment",
                        onClick = {
                            if (fromEntity != null && toEntity != null) {
                                vm.submitPaymentTransfer(date, fromEntity!!, toEntity!!, amt, notes)
                                amountStr = ""
                                notes = ""
                                attachedUri = null
                            }
                        },
                        enabled = fromEntity != null && toEntity != null && fromEntity!!.id != toEntity!!.id && amt > 0.0,
                        testTag = "pt_save_btn"
                    )
                }
            }

            item {
                Text(
                    "TRANSFER HISTORY",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = accents.textMuted
                )
            }

            if (transferHistory.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("No payment transfers recorded yet.", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    }
                }
            } else {
                item {
                    SarimYearMonthDayTree(
                        items = transferHistory,
                        dateOf = { it.date },
                        summaryLabel = { list -> InputGuards.formatMoney(list.sumOf { it.amount }, currency, masked) },
                        itemRow = { tx ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedTransfer = tx }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${tx.entityName} -> ${tx.peerEntityName}", style = MaterialTheme.typography.titleSmall)
                                    Text("TRANSFER", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                }
                                Text(InputGuards.formatMoney(tx.amount, currency, masked), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    )
                }
            }

            item {
                SarimNoteBanner("Note: A payment transfer records an OUT transaction against the source entity and an IN transaction against the destination entity. Both entities' balances update immediately and sync across devices.")
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    selectedTransfer?.let { tx ->
        SarimDetailBottomSheet(
            title = "${tx.entityName} -> ${tx.peerEntityName}",
            rightTitle = InputGuards.formatMoney(tx.amount, currency, masked),
            subtitle = DateUtils.formatPretty(tx.date).uppercase(),
            onDismiss = { selectedTransfer = null },
            content = {
                if (tx.description.isNotBlank()) {
                    Text(tx.description, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                }
            },
            footer = {
                SarimDangerButton(
                    text = "Delete Transfer Pair",
                    onClick = {
                        vm.recyclePair("transactions", tx.transferPairId, tx.id, "Transfer ${tx.entityName} -> ${tx.peerEntityName}")
                        selectedTransfer = null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}
