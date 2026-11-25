package com.gullzubair.sarim.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.EntityRecord
import com.gullzubair.sarim.data.model.ExpenseRecord
import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
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
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

private sealed interface PaymentHistoryItem {
    val id: String
    val date: String
    val timestamp: Long
    data class TxItem(val tx: PaymentTransaction) : PaymentHistoryItem {
        override val id: String get() = "tx_${tx.id}"
        override val date: String get() = tx.date
        override val timestamp: Long get() = tx.timestamp
    }
    data class ExpItem(val exp: ExpenseRecord) : PaymentHistoryItem {
        override val id: String get() = "exp_${exp.id}"
        override val date: String get() = exp.date
        override val timestamp: Long get() = exp.timestamp
    }
}

@Composable
fun PaymentsScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onOpenAddEntity: () -> Unit,
    onOpenPaymentTransfer: () -> Unit,
    onOpenEntityDetail: (String) -> Unit,
    onOpenExpenseDetail: (String) -> Unit = {}
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = vm.sessionInfoFor(snapshot.profile).restrictMoney || snapshot.settings.hideSensitiveMoney
    val activeEntities = remember(snapshot.entities) { snapshot.entities.filter { it.deletedAt == null } }
    val activeTx = remember(snapshot.transactions) { snapshot.transactions.filter { it.deletedAt == null } }
    val activeExpenses = remember(snapshot.expenses) { snapshot.expenses.filter { it.deletedAt == null } }
    val channels = snapshot.settings.paymentModes.ifEmpty { listOf("Cash", "Bank", "Easypaisa", "JazzCash") }

    var subTab by remember { mutableIntStateOf(0) }
    var txKind by remember { mutableStateOf("EXPENSE") } // EXPENSE | IN | OUT
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var selectedEntity by remember { mutableStateOf<EntityRecord?>(null) }
    var customName by remember { mutableStateOf("") }
    var expCategory by remember(snapshot.settings.expenseCategories) {
        mutableStateOf(snapshot.settings.expenseCategories.firstOrNull() ?: "Other")
    }
    var channel by remember(channels) { mutableStateOf(channels.first()) }
    var amountStr by remember { mutableStateOf("") }
    var addToLedger by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var selectedTx by remember { mutableStateOf<PaymentTransaction?>(null) }
    var selectedExpense by remember { mutableStateOf<ExpenseRecord?>(null) }

    val health = remember(snapshot) { FinanceEngine.calculateEconomicHealth(snapshot) }
    val totalExpenses = remember(activeExpenses) { activeExpenses.sumOf { it.amount } }
    val netPayBalance = remember(health) {
        InputGuards.round2(health.entityReceivables - health.supplierPayables - health.entityPayables)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Pay / Exp", "Entities", "Health", "History").forEachIndexed { idx, label ->
                val selected = subTab == idx
                Surface(
                    onClick = { subTab = idx },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) accents.softPillFill else accents.inputFill,
                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                    modifier = Modifier.weight(1f).testTag("pay_subtab_$idx")
                ) {
                    Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) accents.emerald else accents.textMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        when (subTab) {
            0 -> {
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                val availCash = remember(snapshot) { FinanceEngine.computeAvailableCashInHand(snapshot) }
                val fifoPreview = remember(txKind, selectedEntity, customName, amount, snapshot.inventory) {
                    val supplierNeedle = selectedEntity?.id?.ifBlank { selectedEntity?.name } ?: customName
                    if (txKind == "OUT" && supplierNeedle.isNotBlank() && amount > 0.0) {
                        FinanceEngine.previewSupplierFifoAllocation(supplierNeedle, amount, snapshot.inventory)
                    } else emptyList()
                }
                val cashShort = (txKind == "OUT" || txKind == "EXPENSE") &&
                    FinanceEngine.isCashMode(channel) && amount > availCash + 0.01

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("EXPENSE" to "Expense", "IN" to "Payment IN", "OUT" to "Payment OUT").forEach { (key, title) ->
                                    val selected = txKind == key
                                    Surface(
                                        onClick = { txKind = key },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (selected) accents.softPillFill else accents.inputFill,
                                        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                                        modifier = Modifier.weight(1f).testTag("pay_type_${key.lowercase()}")
                                    ) {
                                        Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                            Text(title, style = MaterialTheme.typography.labelLarge, color = if (selected) accents.emerald else accents.textMuted)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            DatePickerField("Date", date, { date = it }, "pay_date")
                            Spacer(Modifier.height(10.dp))
                            if (txKind == "EXPENSE") {
                                PickerField(
                                    label = "Expense Category",
                                    selectedLabel = expCategory.ifBlank { "Pick Category" },
                                    items = snapshot.settings.expenseCategories,
                                    itemLabel = { it },
                                    onSelect = { picked ->
                                        expCategory = picked
                                        if (customName.isBlank()) customName = picked
                                    },
                                    testTag = "pay_exp_cat_picker"
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            if (activeEntities.isNotEmpty()) {
                                PickerField(
                                    label = if (txKind == "EXPENSE") "Optional Linked Entity" else "Select Entity (${activeEntities.size})",
                                    selectedLabel = selectedEntity?.let { "${it.name} (${it.type})" } ?: "Pick Party / Supplier / Staff",
                                    items = activeEntities,
                                    itemLabel = { "${it.name} • ${it.type.uppercase()}" },
                                    onSelect = { selectedEntity = it; if (txKind != "EXPENSE" || customName.isBlank()) customName = it.name },
                                    testTag = "pay_entity_picker"
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            SarimPillInput(
                                value = customName,
                                onValueChange = { customName = it.take(80); if (selectedEntity?.name != it) selectedEntity = null },
                                label = if (txKind == "EXPENSE") "EXPENSE TITLE" else "ENTITY / PAYEE NAME",
                                placeholder = if (txKind == "EXPENSE") "e.g. Fuel, Wages, Rent" else "Enter party or supplier",
                                testTag = "pay_name_input"
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PickerField("Channel", channel, channels, { it }, { channel = it }, "pay_channel_picker", Modifier.weight(1f))
                                DecimalField("Amount", amountStr, { amountStr = it }, "pay_amount_input", Modifier.weight(1f))
                            }
                            if (cashShort) {
                                Spacer(Modifier.height(6.dp))
                                Text("Warning: Cash in hand is ${InputGuards.formatMoney(availCash, currency, masked)}", color = accents.gold, style = MaterialTheme.typography.bodySmall)
                            }
                            if (fifoPreview.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Supplier FIFO Payable Settlement", style = MaterialTheme.typography.titleSmall, color = accents.cyan)
                                fifoPreview.forEach { lot ->
                                    Text(
                                        "${lot.date} • ${lot.materialName}: settles ${InputGuards.formatMoney(lot.appliedPayment, currency, masked)} (left ${InputGuards.formatMoney(lot.afterPayable, currency, masked)})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accents.textMuted
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Mirror in Daily Ledger", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = addToLedger, onCheckedChange = { addToLedger = it }, modifier = Modifier.testTag("pay_ledger_switch"))
                            }
                            SarimPillInput(
                                value = notes,
                                onValueChange = { notes = it.take(500) },
                                label = "NOTES",
                                placeholder = "Optional description",
                                testTag = "pay_notes_input"
                            )
                            Spacer(Modifier.height(14.dp))
                            SarimGradientButton(
                                text = "Save $txKind",
                                onClick = {
                                    vm.submitPaymentOrExpense(
                                        date = date,
                                        kind = txKind,
                                        entity = selectedEntity,
                                        name = customName,
                                        category = if (txKind == "EXPENSE") expCategory else (selectedEntity?.type ?: "party"),
                                        channel = channel,
                                        amount = amount,
                                        addToLedger = addToLedger,
                                        notes = notes
                                    )
                                    amountStr = ""
                                    notes = ""
                                },
                                enabled = (customName.isNotBlank() || selectedEntity != null) && amount > 0.0,
                                testTag = "pay_save_btn"
                            )
                        }
                    }

                    item {
                        SarimCard {
                            Text("Net Balance:", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                            Text(
                                InputGuards.formatMoney(netPayBalance, currency, masked),
                                fontFamily = JakartaFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("Total Receivable", InputGuards.formatMoney(health.entityReceivables, currency, masked), accents.emerald, Modifier.weight(1f))
                                SarimHalfRingKpiBox("Expenses", InputGuards.formatMoney(totalExpenses, currency, masked), accents.gold, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("Supplies Payable", InputGuards.formatMoney(health.supplierPayables, currency, masked), accents.danger, Modifier.weight(1f))
                                SarimHalfRingKpiBox("Entity Payable", InputGuards.formatMoney(health.entityPayables, currency, masked), accents.purple, Modifier.weight(1f))
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            1 -> {
                val avatarColors = listOf(Color(0xFFDCD3B2), Color(0xFFD6C8D3), Color(0xFFD1CFD8), Color(0xFFC5BDD0))
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                onClick = onOpenAddEntity,
                                shape = RoundedCornerShape(18.dp),
                                color = accents.softPillFill,
                                border = BorderStroke(1.dp, accents.emerald),
                                modifier = Modifier.weight(1f).testTag("pay_add_entity_btn")
                            ) {
                                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add Entity", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                                }
                            }
                            Surface(
                                onClick = onOpenPaymentTransfer,
                                shape = RoundedCornerShape(18.dp),
                                color = accents.inputFill,
                                border = BorderStroke(1.dp, accents.borderSubtle),
                                modifier = Modifier.weight(1f).testTag("pay_transfer_entity_btn")
                            ) {
                                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Transfer", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    if (activeEntities.isEmpty()) {
                        item { EmptyStateCard("No entities yet", "Add suppliers, parties, staff, or bank entities.", "Add Entity", onOpenAddEntity) }
                    } else {
                        items(activeEntities, key = { it.id }) { ent ->
                            val bal = FinanceEngine.computeEntityBalance(ent, snapshot.transactions, snapshot.inventory)
                            val initials = ent.name.split(" ").filter { it.isNotBlank() }.take(2)
                                .joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "E" }
                            val bgTint = avatarColors[kotlin.math.abs(ent.name.hashCode()) % avatarColors.size]
                            val contactStr = ent.phone.ifBlank { ent.contact }
                            SarimCard(
                                modifier = Modifier.clickable { onOpenEntityDetail(ent.name) }.testTag("entity_card_${ent.id}"),
                                contentPadding = PaddingValues(14.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(44.dp).clip(CircleShape).background(bgTint),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(initials, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFF1F2937))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(ent.name.uppercase(), style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = if (contactStr.isNotBlank()) contactStr else ent.type.uppercase(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (contactStr.isNotBlank()) accents.emerald else accents.textMuted,
                                            textDecoration = if (contactStr.isNotBlank()) TextDecoration.Underline else TextDecoration.None
                                        )
                                    }
                                    Text(
                                        InputGuards.formatMoney(bal, currency, masked),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (bal >= 0) accents.emerald else accents.danger
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            2 -> {
                val channelBals = remember(snapshot) { FinanceEngine.computeChannelBalances(snapshot) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            Text("Net Working Capital:", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                            Text(InputGuards.formatMoney(health.netWorkingCapital, currency, masked), fontFamily = JakartaFont, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("Total Receivable", InputGuards.formatMoney(health.entityReceivables + health.customerReceivables, currency, masked), accents.emerald, Modifier.weight(1f))
                                SarimHalfRingKpiBox("Expenses", InputGuards.formatMoney(totalExpenses, currency, masked), accents.gold, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("Supplies Payable", InputGuards.formatMoney(health.supplierPayables, currency, masked), accents.danger, Modifier.weight(1f))
                                SarimHalfRingKpiBox("Entity Payable", InputGuards.formatMoney(health.entityPayables, currency, masked), accents.purple, Modifier.weight(1f))
                            }
                        }
                    }
                    item {
                        SarimCard {
                            Text("Channel Cash Tracker", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            channelBals.forEach { (ch, bal) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(ch, style = MaterialTheme.typography.bodyMedium)
                                    Text(InputGuards.formatMoney(bal, currency, masked), style = MaterialTheme.typography.labelMedium, color = if (bal >= 0) accents.emerald else accents.danger)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            else -> {
                var historyFilter by remember { mutableStateOf("ALL") } // ALL | PAYMENTS | EXPENSES
                val historyItems = remember(activeTx, activeExpenses, historyFilter) {
                    val txList = if (historyFilter == "EXPENSES") emptyList() else activeTx.map { PaymentHistoryItem.TxItem(it) }
                    val expList = if (historyFilter == "PAYMENTS") emptyList() else activeExpenses.map { PaymentHistoryItem.ExpItem(it) }
                    (txList + expList).sortedWith(compareByDescending<PaymentHistoryItem> { it.date }.thenByDescending { it.timestamp })
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("ALL" to "All", "PAYMENTS" to "Payments", "EXPENSES" to "Expenses").forEach { (k, label) ->
                                val sel = historyFilter == k
                                Surface(
                                    onClick = { historyFilter = k },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (sel) accents.softPillFill else accents.inputFill,
                                    border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) accents.emerald else accents.textMuted)
                                    }
                                }
                            }
                        }
                    }
                    if (historyItems.isEmpty()) {
                        item { EmptyStateCard("No payment history", "Recorded expenses and entity payments appear here.") }
                    } else {
                        item {
                            SarimYearMonthDayTree(
                                items = historyItems,
                                dateOf = { it.date },
                                summaryLabel = { list ->
                                    val tIn = list.filterIsInstance<PaymentHistoryItem.TxItem>()
                                        .filter { it.tx.type.equals("IN", true) }
                                        .sumOf { it.tx.amount }
                                    val tOut = list.filterIsInstance<PaymentHistoryItem.TxItem>()
                                        .filter { !it.tx.type.equals("IN", true) }
                                        .sumOf { it.tx.amount } +
                                        list.filterIsInstance<PaymentHistoryItem.ExpItem>().sumOf { it.exp.amount }
                                    "In ${InputGuards.formatMoney(tIn, currency, masked)} • Out ${InputGuards.formatMoney(tOut, currency, masked)}"
                                },
                                itemRow = { item ->
                                    when (item) {
                                        is PaymentHistoryItem.TxItem -> {
                                            val tx = item.tx
                                            val isIn = tx.type.equals("IN", true)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { selectedTx = tx }
                                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                                    .testTag("pay_row_${tx.id}"),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (isIn) accents.emerald else accents.cyan))
                                                    Spacer(Modifier.width(8.dp))
                                                    Column {
                                                        Text(tx.entityName.uppercase(), style = MaterialTheme.typography.titleSmall)
                                                        Text(
                                                            "${tx.type} • ${tx.paymentMethod}${if (tx.isTransfer) " • Transfer" else ""}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = accents.textMuted
                                                        )
                                                    }
                                                }
                                                Text(
                                                    (if (isIn) "+ " else "- ") + InputGuards.formatMoney(tx.amount, currency, masked),
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = if (isIn) accents.emerald else accents.danger
                                                )
                                            }
                                        }
                                        is PaymentHistoryItem.ExpItem -> {
                                            val exp = item.exp
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { selectedExpense = exp }
                                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                                    .testTag("exp_row_${exp.id}"),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Box(Modifier.size(8.dp).clip(CircleShape).background(accents.gold))
                                                    Spacer(Modifier.width(8.dp))
                                                    Column {
                                                        Text(exp.name.uppercase(), style = MaterialTheme.typography.titleSmall)
                                                        Text("EXPENSE • ${exp.category} • ${exp.paymentMethod}", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                                    }
                                                }
                                                Text(
                                                    "- ${InputGuards.formatMoney(exp.amount, currency, masked)}",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = accents.danger
                                                )
                                            }
                                        }
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

    selectedTx?.let { tx ->
        SarimDetailBottomSheet(
            title = tx.entityName.uppercase(),
            rightTitle = InputGuards.formatMoney(tx.amount, currency, masked),
            subtitle = "${DateUtils.formatPretty(tx.date).uppercase()} • ${tx.type} • ${tx.entityType}",
            onDismiss = { selectedTx = null },
            content = {
                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Channel", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(tx.paymentMethod, style = MaterialTheme.typography.titleSmall)
                }
                if (tx.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Description", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                        Text(tx.description, style = MaterialTheme.typography.titleSmall)
                    }
                }
            },
            footer = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SarimDangerButton(
                        text = "Delete",
                        onClick = {
                            if (tx.isTransfer && tx.transferPairId.isNotBlank()) {
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
                            selectedTx = null
                        },
                        modifier = Modifier.weight(0.38f),
                        testTag = "pay_delete_${tx.id}"
                    )
                    SarimGradientButton(
                        text = "Copy to Form",
                        onClick = {
                            txKind = if (tx.type.equals("IN", true)) "IN" else "OUT"
                            date = tx.date
                            customName = tx.entityName
                            selectedEntity = activeEntities.firstOrNull { it.id == tx.entityId || it.name.equals(tx.entityName, true) }
                            channel = tx.paymentMethod
                            amountStr = tx.amount.toString()
                            notes = tx.description
                            selectedTx = null
                            subTab = 0
                        },
                        modifier = Modifier.weight(0.62f)
                    )
                }
            }
        )
    }

    selectedExpense?.let { exp ->
        SarimDetailBottomSheet(
            title = exp.name.uppercase(),
            rightTitle = InputGuards.formatMoney(exp.amount, currency, masked),
            subtitle = "${DateUtils.formatPretty(exp.date).uppercase()} • EXPENSE • ${exp.category}",
            onDismiss = { selectedExpense = null },
            content = {
                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Channel", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(exp.paymentMethod, style = MaterialTheme.typography.titleSmall)
                }
                if (exp.description.isNotBlank() || exp.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Notes", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                        Text(exp.notes.ifBlank { exp.description }, style = MaterialTheme.typography.titleSmall)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "View all \"${exp.name}\" records",
                    style = MaterialTheme.typography.labelLarge,
                    color = accents.emerald,
                    modifier = Modifier
                        .clickable {
                            val n = exp.name
                            selectedExpense = null
                            onOpenExpenseDetail(n)
                        }
                        .padding(vertical = 6.dp)
                        .testTag("pay_view_expense_${exp.id}")
                )
            },
            footer = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SarimDangerButton(
                        text = "Delete",
                        onClick = {
                            vm.softDeleteRecord(
                                collection = "expenses",
                                docId = exp.id,
                                displayName = exp.name,
                                displayDetail = "${exp.date} • ${exp.category}",
                                displayAmount = InputGuards.formatMoney(exp.amount, currency, masked),
                                snapshotMap = WorkspaceJsonCodec.expenseToMap(exp)
                            )
                            selectedExpense = null
                        },
                        modifier = Modifier.weight(0.38f),
                        testTag = "exp_delete_${exp.id}"
                    )
                    SarimGradientButton(
                        text = "Copy to Form",
                        onClick = {
                            txKind = "EXPENSE"
                            date = exp.date
                            expCategory = exp.category
                            customName = exp.name
                            selectedEntity = activeEntities.firstOrNull { it.id == exp.entityId }
                            channel = exp.paymentMethod
                            amountStr = exp.amount.toString()
                            notes = exp.notes.ifBlank { exp.description }
                            selectedExpense = null
                            subTab = 0
                        },
                        modifier = Modifier.weight(0.62f)
                    )
                }
            }
        )
    }
}
