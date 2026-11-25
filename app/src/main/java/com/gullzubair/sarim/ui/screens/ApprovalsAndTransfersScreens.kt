package com.gullzubair.sarim.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

@Composable
fun ApprovalsScreen(snapshot: WorkspaceSnapshot, vm: SarimViewModel) {
    val accents = LocalSarimAccents.current
    val isAdmin = snapshot.profile.role == "admin" || snapshot.profile.uid == snapshot.profile.workspaceOwnerUid
    val pendingList = remember(snapshot.pendingApprovals) { snapshot.pendingApprovals.filter { it.status == "pending" } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(if (isAdmin) "Approval Queue" else "My Pending Submissions", style = MaterialTheme.typography.headlineSmall)
                    Text("${pendingList.size} awaiting review", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                }
                if (isAdmin && pendingList.isNotEmpty()) {
                    Button(
                        onClick = { pendingList.forEach { vm.reviewPending(it, true) } },
                        modifier = Modifier.testTag("approvals_approve_all_btn")
                    ) { Text("Approve All") }
                }
            }
        }
        if (pendingList.isEmpty()) {
            item { EmptyStateCard("Queue is clear", "No pending member changes right now.") }
        } else {
            items(pendingList, key = { it.id }) { item ->
                SarimCard(accentBorder = accents.gold) {
                    Text(item.summaryTitle, style = MaterialTheme.typography.titleMedium)
                    Text(item.summaryDetail, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    Text(
                        "Submitted by ${item.submittedByName} • ${item.targetCollection.uppercase()} • ${item.action.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accents.cyan
                    )
                    if (isAdmin) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { vm.reviewPending(item, true) },
                                modifier = Modifier.weight(1f).testTag("approve_${item.id}")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Approve")
                            }
                            OutlinedButton(
                                onClick = { vm.reviewPending(item, false) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = accents.danger),
                                modifier = Modifier.weight(1f).testTag("reject_${item.id}")
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

@Composable
fun StockTransferScreen(snapshot: WorkspaceSnapshot, todayIso: String, vm: SarimViewModel, onBack: () -> Unit) {
    val activeStores = remember(snapshot.stores) { snapshot.stores.filter { it.active } }
    val stocks = remember(snapshot) { FinanceEngine.computeStoreStockSnapshots(snapshot) }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var fromStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var toStore by remember(activeStores) { mutableStateOf(activeStores.getOrElse(1) { activeStores.firstOrNull() }) }
    var qtyStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SarimCard {
                Text("Transfer Stock Between Stores", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                DatePickerField("Transfer Date", date, { date = it }, "st_date")
                Spacer(Modifier.height(10.dp))
                PickerField(
                    label = "From Store",
                    selectedLabel = fromStore?.let { s ->
                        val av = stocks.firstOrNull { it.storeKey == s.storeKey }?.availableKg ?: 0.0
                        "${s.name} (Avail ${InputGuards.formatKg(av)})"
                    } ?: "Select Source",
                    items = activeStores,
                    itemLabel = { it.name },
                    onSelect = { fromStore = it },
                    testTag = "st_from_picker"
                )
                Spacer(Modifier.height(10.dp))
                PickerField(
                    label = "To Store",
                    selectedLabel = toStore?.name ?: "Select Destination",
                    items = activeStores,
                    itemLabel = { it.name },
                    onSelect = { toStore = it },
                    testTag = "st_to_picker"
                )
                Spacer(Modifier.height(10.dp))
                DecimalField("Quantity to Transfer", qtyStr, { qtyStr = it }, "st_qty_input", suffix = "kg")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it.take(500) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                val qty = qtyStr.toDoubleOrNull() ?: 0.0
                Button(
                    onClick = {
                        if (fromStore != null && toStore != null) {
                            vm.submitStockTransfer(date, fromStore!!, toStore!!, qty, notes)
                            onBack()
                        }
                    },
                    enabled = fromStore != null && toStore != null && fromStore!!.storeKey != toStore!!.storeKey && qty > 0.0,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("st_save_btn")
                ) {
                    Text("Execute Stock Transfer")
                }
            }
        }
    }
}

@Composable
fun PaymentTransferScreen(snapshot: WorkspaceSnapshot, todayIso: String, vm: SarimViewModel, onBack: () -> Unit) {
    val activeEntities = remember(snapshot.entities) { snapshot.entities.filter { !it.recycled && it.entityType != "expense" } }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var fromEntity by remember(activeEntities) { mutableStateOf(activeEntities.firstOrNull()) }
    var toEntity by remember(activeEntities) { mutableStateOf(activeEntities.getOrElse(1) { activeEntities.firstOrNull() }) }
    var amountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SarimCard {
                Text("Transfer Balance Between Entities", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                DatePickerField("Transfer Date", date, { date = it }, "pt_date")
                Spacer(Modifier.height(10.dp))
                PickerField("From Entity", fromEntity?.name ?: "Select", activeEntities, { it.name }, { fromEntity = it }, "pt_from_picker")
                Spacer(Modifier.height(10.dp))
                PickerField("To Entity", toEntity?.name ?: "Select", activeEntities, { it.name }, { toEntity = it }, "pt_to_picker")
                Spacer(Modifier.height(10.dp))
                DecimalField("Amount", amountStr, { amountStr = it }, "pt_amount_input")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it.take(500) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                val amt = amountStr.toDoubleOrNull() ?: 0.0
                Button(
                    onClick = {
                        if (fromEntity != null && toEntity != null) {
                            vm.submitPaymentTransfer(date, fromEntity!!, toEntity!!, amt, notes)
                            onBack()
                        }
                    },
                    enabled = fromEntity != null && toEntity != null && fromEntity!!.id != toEntity!!.id && amt > 0.0,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("pt_save_btn")
                ) {
                    Text("Transfer Entity Balance")
                }
            }
        }
    }
}
