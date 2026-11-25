package com.gullzubair.sarim.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.EntityRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.MetricKpiCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

@Composable
fun PaymentsScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onOpenAddEntity: () -> Unit,
    onOpenPaymentTransfer: () -> Unit,
    onOpenEntityDetail: (String) -> Unit
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney
    val activeEntities = remember(snapshot.entities) { snapshot.entities.filter { !it.recycled } }
    val channels = snapshot.settings.paymentChannels.ifEmpty { listOf("Cash", "Bank", "Easypaisa", "JazzCash") }

    var subTab by remember { mutableIntStateOf(0) }
    var txType by remember { mutableStateOf("expense") } // expense | in | out
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var selectedEntity by remember { mutableStateOf<EntityRecord?>(null) }
    var customName by remember { mutableStateOf("") }
    var channel by remember(channels) { mutableStateOf(channels.first()) }
    var amountStr by remember { mutableStateOf("") }
    var addToLedger by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            listOf("Pay / Exp", "Health", "Entities", "History").forEachIndexed { idx, label ->
                Tab(selected = subTab == idx, onClick = { subTab = idx }, text = { Text(label) }, modifier = Modifier.testTag("pay_subtab_$idx"))
            }
        }

        when (subTab) {
            0 -> {
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                val availCash = remember(snapshot) { FinanceEngine.computeAvailableCashInHand(snapshot) }
                val fifoPreview = remember(txType, selectedEntity, amount, snapshot.factory) {
                    if (txType == "out" && selectedEntity?.entityType == "supplier" && amount > 0) {
                        FinanceEngine.previewSupplierFifoAllocation(selectedEntity!!.name, amount, snapshot.factory)
                    } else emptyList()
                }
                val cashShort = (txType == "out" || txType == "expense") && channel.equals("Cash", true) && amount > availCash + 0.01

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = txType == "expense", onClick = { txType = "expense" }, label = { Text("Expense") }, modifier = Modifier.testTag("pay_type_expense"))
                                FilterChip(selected = txType == "in", onClick = { txType = "in" }, label = { Text("Payment IN") }, modifier = Modifier.testTag("pay_type_in"))
                                FilterChip(selected = txType == "out", onClick = { txType = "out" }, label = { Text("Payment OUT") }, modifier = Modifier.testTag("pay_type_out"))
                            }
                            Spacer(Modifier.height(10.dp))
                            DatePickerField("Date", date, { date = it }, "pay_date")
                            Spacer(Modifier.height(10.dp))
                            if (txType == "expense") {
                                PickerField(
                                    label = "Expense Category",
                                    selectedLabel = customName.ifBlank { "Pick Category" },
                                    items = snapshot.settings.expenseCategories,
                                    itemLabel = { it },
                                    onSelect = { customName = it; selectedEntity = null },
                                    testTag = "pay_exp_cat_picker"
                                )
                                Spacer(Modifier.height(8.dp))
                            } else {
                                PickerField(
                                    label = "Select Entity (${activeEntities.size})",
                                    selectedLabel = selectedEntity?.let { "${it.name} (${it.entityType})" } ?: "Pick Party / Supplier / Staff",
                                    items = activeEntities,
                                    itemLabel = { "${it.name} • ${it.entityType.uppercase()}" },
                                    onSelect = { selectedEntity = it; customName = it.name },
                                    testTag = "pay_entity_picker"
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            OutlinedTextField(
                                value = customName,
                                onValueChange = { customName = it.take(80); if (selectedEntity?.name != it) selectedEntity = null },
                                label = { Text(if (txType == "expense") "Expense Title" else "Entity / Payee Name") },
                                modifier = Modifier.fillMaxWidth().testTag("pay_name_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PickerField("Channel", channel, channels, { it }, { channel = it }, "pay_channel_picker", Modifier.weight(1f))
                                DecimalField("Amount", amountStr, { amountStr = it }, "pay_amount_input", Modifier.weight(1f))
                            }
                            if (cashShort) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Warning: Cash in hand is ${InputGuards.formatMoney(availCash, currency, masked)}",
                                    color = accents.gold,
                                    style = MaterialTheme.typography.bodySmall
                                )
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
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it.take(500) },
                                label = { Text("Notes") },
                                modifier = Modifier.fillMaxWidth().testTag("pay_notes_input")
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    vm.submitPayment(
                                        date = date,
                                        txType = txType,
                                        entity = selectedEntity,
                                        name = customName,
                                        category = selectedEntity?.entityType ?: if (txType == "expense") "expense" else "party",
                                        channel = channel,
                                        amount = amount,
                                        addToLedger = addToLedger,
                                        notes = notes
                                    )
                                    amountStr = ""
                                    notes = ""
                                },
                                enabled = customName.isNotBlank() && amount > 0.0,
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pay_save_btn")
                            ) {
                                Text("Save ${txType.uppercase()}")
                            }
                        }
                    }
                }
            }

            1 -> {
                val health = remember(snapshot) { FinanceEngine.calculateEconomicHealth(snapshot) }
                val channelBals = remember(snapshot) { FinanceEngine.computeChannelBalances(snapshot) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricKpiCard("Net Working Capital", InputGuards.formatMoney(health.netWorkingCapital, currency, masked), accent = accents.emerald, modifier = Modifier.weight(1f))
                            MetricKpiCard("Total Liabilities", InputGuards.formatMoney(health.totalLiabilities, currency, masked), accent = accents.danger, modifier = Modifier.weight(1f))
                        }
                    }
                    item {
                        SarimCard {
                            Text("Channel Cash Tracker", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            channelBals.forEach { (ch, bal) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(ch, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        InputGuards.formatMoney(bal, currency, masked),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (bal >= 0) accents.emerald else accents.danger
                                    )
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onOpenAddEntity, modifier = Modifier.weight(1f).testTag("pay_add_entity_btn")) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text(" Add Entity")
                            }
                            OutlinedButton(onClick = onOpenPaymentTransfer, modifier = Modifier.weight(1f).testTag("pay_transfer_entity_btn")) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null)
                                Text(" Transfer")
                            }
                        }
                    }
                    if (activeEntities.isEmpty()) {
                        item { EmptyStateCard("No entities yet", "Add suppliers, parties, staff, or bank entities.", "Add Entity", onOpenAddEntity) }
                    } else {
                        items(activeEntities, key = { it.id }) { ent ->
                            val bal = FinanceEngine.computeEntityBalance(ent, snapshot.transactions, snapshot.factory)
                            SarimCard(modifier = Modifier.clickable { onOpenEntityDetail(ent.name) }.testTag("entity_card_${ent.id}")) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(ent.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${ent.entityType.uppercase()} ${if (ent.phone.isNotBlank()) "• ${ent.phone}" else ""}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                    }
                                    Text(
                                        InputGuards.formatMoney(bal, currency, masked),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (bal >= 0) accents.emerald else accents.gold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                val activeTx = remember(snapshot.transactions) { snapshot.transactions.filter { !it.recycled } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeTx.isEmpty()) {
                        item { EmptyStateCard("No payment history", "Recorded expenses and entity payments appear here.") }
                    } else {
                        items(activeTx, key = { it.id }) { tx ->
                            SarimCard {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${tx.entityName} • ${tx.txType.uppercase()}", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${tx.date} • ${tx.paymentChannel} • ${InputGuards.formatMoney(tx.amount, currency, masked)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accents.textMuted
                                        )
                                    }
                                    IconButton(
                                        onClick = { vm.recycleRecord("transactions", tx.id, "${tx.entityName} (${tx.date})") },
                                        modifier = Modifier.testTag("pay_delete_${tx.id}")
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Move to Recycle Bin", tint = accents.danger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
