package com.gullzubair.sarim.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.MetricKpiCard
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun RawMaterialScreen(snapshot: WorkspaceSnapshot, todayIso: String, vm: SarimViewModel, onBack: () -> Unit) {
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var name by remember { mutableStateOf("") }
    var unitName by remember { mutableStateOf("kg") }
    var factorStr by remember { mutableStateOf("1") }
    var qtyStr by remember { mutableStateOf("") }
    var totalCostStr by remember { mutableStateOf("") }
    var paidStr by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var minStr by remember { mutableStateOf("10") }
    var notes by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SarimCard {
                Text("Raw Material Purchase", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                DatePickerField("Purchase Date", date, { date = it }, "rm_date")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Material Name") }, modifier = Modifier.fillMaxWidth().testTag("rm_name_input"))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = unitName, onValueChange = { unitName = it.take(20) }, label = { Text("Unit (e.g. Bag)") }, modifier = Modifier.weight(1f))
                    DecimalField("kg per Unit", factorStr, { factorStr = it }, "rm_factor_input", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField("Qty Units", qtyStr, { qtyStr = it }, "rm_qty_input", Modifier.weight(1f))
                    DecimalField("Total Cost", totalCostStr, { totalCostStr = it }, "rm_cost_input", Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField("Paid Now", paidStr, { paidStr = it }, "rm_paid_input", Modifier.weight(1f))
                    DecimalField("Low Stock Alert", minStr, { minStr = it }, "rm_min_input", Modifier.weight(1f), "kg")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = supplier, onValueChange = { supplier = it.take(80) }, label = { Text("Supplier Name") }, modifier = Modifier.fillMaxWidth().testTag("rm_supplier_input"))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it.take(500) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                val qty = qtyStr.toDoubleOrNull() ?: 0.0
                val factor = factorStr.toDoubleOrNull() ?: 1.0
                Button(
                    onClick = {
                        vm.submitRawPurchase(date, name, unitName, factor, qty, totalCostStr.toDoubleOrNull() ?: 0.0, paidStr.toDoubleOrNull() ?: 0.0, supplier, minStr.toDoubleOrNull() ?: 0.0, notes)
                        onBack()
                    },
                    enabled = name.isNotBlank() && qty > 0 && factor > 0,
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("rm_save_btn")
                ) { Text("Record Purchase (${InputGuards.formatKg(qty * factor)})") }
            }
        }
    }
}

@Composable
fun AddEntityScreen(vm: SarimViewModel, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("party") }
    var phone by remember { mutableStateOf("") }
    var openingStr by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SarimCard {
                Text("Add Party / Supplier / Staff", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("party", "supplier", "staff", "bank").forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.uppercase()) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth().testTag("ent_name_input"))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it.take(32) }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth().testTag("ent_phone_input"))
                Spacer(Modifier.height(8.dp))
                DecimalField("Opening Balance", openingStr, { openingStr = it }, "ent_opening_input")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = notes, onValueChange = { notes = it.take(500) }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        vm.addEntity(name, type, phone, openingStr.toDoubleOrNull() ?: 0.0, notes)
                        onBack()
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("ent_save_btn")
                ) { Text("Save Entity") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppAccountsScreen(snapshot: WorkspaceSnapshot, vm: SarimViewModel) {
    val accents = LocalSarimAccents.current
    val isOwner = snapshot.profile.uid == snapshot.profile.workspaceOwnerUid
    var inviteEmail by remember { mutableStateOf("") }
    var inviteName by remember { mutableStateOf("") }
    val allRoles = listOf("sales", "rep", "production", "factory", "payments", "calculator")
    val selectedRoles = remember { mutableStateListOf("sales", "rep") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (isOwner) {
            item {
                SarimCard {
                    Text("Invite Team Member (Google Sign-In)", style = MaterialTheme.typography.titleMedium)
                    Text("Invited members sign in securely with Google to join your workspace with scoped permissions.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = inviteName, onValueChange = { inviteName = it.take(80) }, label = { Text("Display Name") }, modifier = Modifier.fillMaxWidth().testTag("acc_name_input"))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = inviteEmail, onValueChange = { inviteEmail = it.take(160) }, label = { Text("Google Email") }, modifier = Modifier.fillMaxWidth().testTag("acc_email_input"))
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        allRoles.forEach { r ->
                            val has = r in selectedRoles
                            FilterChip(
                                selected = has,
                                onClick = { if (has) selectedRoles.remove(r) else selectedRoles.add(r) },
                                label = { Text(r.uppercase()) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            vm.inviteMember(inviteEmail, inviteName.ifBlank { inviteEmail.substringBefore("@") }, selectedRoles.toList())
                            inviteEmail = ""; inviteName = ""
                        },
                        enabled = inviteEmail.contains("@"),
                        modifier = Modifier.fillMaxWidth().testTag("acc_invite_btn")
                    ) { Text("Send Workspace Invite") }
                }
            }
        }
        items(snapshot.teamMembers, key = { it.uid }) { m ->
            SarimCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${m.displayName} (${m.role.uppercase()})", style = MaterialTheme.typography.titleMedium)
                        Text("${m.email} • Roles: ${m.roles.joinToString()}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    }
                    if (isOwner && m.uid != snapshot.profile.uid) {
                        Switch(
                            checked = m.approved,
                            onCheckedChange = { vm.updateMemberRoles(m.uid, m.roles, it) }
                        )
                    }
                }
            }
        }
        items(snapshot.invites, key = { it.id }) { inv ->
            SarimCard(accentBorder = accents.cyan) {
                Text("Invite: ${inv.displayName} (${inv.memberEmail})", style = MaterialTheme.typography.titleSmall)
                Text("Status: ${inv.status.uppercase()} • Roles: ${inv.roles.joinToString()}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            }
        }
    }
}

@Composable
fun RecycleBinScreen(snapshot: WorkspaceSnapshot, vm: SarimViewModel) {
    val accents = LocalSarimAccents.current
    val deletedItems = remember(snapshot) {
        buildList {
            snapshot.production.filter { it.recycled }.forEach { add(Triple("production", it.id, "${it.storeName} • ${it.date}")) }
            snapshot.sales.filter { it.recycled }.forEach { add(Triple("sales", it.id, "${it.customerName} • ${it.date}")) }
            snapshot.repRecords.filter { it.recycled }.forEach { add(Triple("rep_records", it.id, "${it.repName} / ${it.customerName} • ${it.date}")) }
            snapshot.calculator.filter { it.recycled }.forEach { add(Triple("calculator", it.id, "Settlement ${it.repName} • ${it.dateTo}")) }
            snapshot.factory.filter { it.recycled }.forEach { add(Triple("factory", it.id, "${it.recordType} • ${it.date}")) }
            snapshot.transactions.filter { it.recycled }.forEach { add(Triple("transactions", it.id, "${it.entityName} • ${it.date}")) }
            snapshot.ledgerEntries.filter { it.recycled }.forEach { add(Triple("ledger_entries", it.id, "Ledger ${it.entryType} • ${it.date}")) }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (deletedItems.isEmpty()) {
            item { EmptyStateCard("Recycle Bin is empty", "Recycled records are retained for 90 days before permanent purge.") }
        } else {
            items(deletedItems, key = { "${it.first}_${it.second}" }) { (col, id, label) ->
                SarimCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            Text(col.uppercase(), style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                        }
                        IconButton(onClick = { vm.restoreRecord(col, id) }, modifier = Modifier.testTag("bin_restore_$id")) {
                            Icon(Icons.Default.Restore, contentDescription = "Restore record", tint = accents.emerald)
                        }
                        IconButton(onClick = { vm.deleteForever(col, id) }, modifier = Modifier.testTag("bin_purge_$id")) {
                            Icon(Icons.Default.DeleteForever, contentDescription = "Delete forever", tint = accents.danger)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackupRestoreScreen(snapshot: WorkspaceSnapshot) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("Export a full workspace JSON snapshot to device storage.") }
    val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val root = JSONObject().apply {
                    put("exportedAt", DateUtils.todayIso())
                    put("workspaceOwnerUid", snapshot.profile.workspaceOwnerUid)
                    put("storesCount", snapshot.stores.size)
                    put("formulasCount", snapshot.formulas.size)
                    put("productionCount", snapshot.production.size)
                    put("salesCount", snapshot.sales.size)
                    put("factoryCount", snapshot.factory.size)
                    put("transactionsCount", snapshot.transactions.size)
                    put("ledgerCount", snapshot.ledgerEntries.size)
                    put("customers", JSONArray(snapshot.customers.map { mapOf("id" to it.id, "name" to it.name, "phone" to it.phone, "openingBalance" to it.openingBalance) }))
                    put("stores", JSONArray(snapshot.stores.map { mapOf("id" to it.id, "storeKey" to it.storeKey, "name" to it.name, "salePrice" to it.salePrice) }))
                }
                context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) }
            }.onSuccess {
                statusText = "Backup exported successfully."
            }.onFailure {
                statusText = "Export failed: ${it.localizedMessage}"
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SarimCard {
                Text("Workspace JSON Backup", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = LocalSarimAccents.current.textMuted)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { createDocLauncher.launch("sarim_backup_${DateUtils.todayIso()}.json") },
                    modifier = Modifier.fillMaxWidth().testTag("backup_export_btn")
                ) { Text("Export JSON Backup") }
            }
        }
    }
}

@Composable
fun DbStructureScreen(snapshot: WorkspaceSnapshot) {
    val rows = listOf(
        "Stores" to snapshot.stores.size,
        "Formulas" to snapshot.formulas.size,
        "Customers" to snapshot.customers.size,
        "Entities" to snapshot.entities.size,
        "Inventory" to snapshot.inventory.size,
        "Production" to snapshot.production.size,
        "Sales" to snapshot.sales.size,
        "Rep Records" to snapshot.repRecords.size,
        "Calculator Settlements" to snapshot.calculator.size,
        "Factory Records" to snapshot.factory.size,
        "Transactions" to snapshot.transactions.size,
        "Daily Ledger" to snapshot.ledgerEntries.size,
        "Pending Approvals" to snapshot.pendingApprovals.size
    )
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            MetricKpiCard("Active Workspace Owner", snapshot.profile.workspaceOwnerUid.take(16) + "...", "Real-time Firestore Sync")
        }
        items(rows, key = { it.first }) { (name, count) ->
            SarimCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text("$count docs", style = MaterialTheme.typography.labelLarge, color = LocalSarimAccents.current.emerald)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    snapshot: WorkspaceSnapshot,
    vm: SarimViewModel,
    onSignOut: () -> Unit
) {
    val accents = LocalSarimAccents.current
    var currency by remember(snapshot.settings) { mutableStateOf(snapshot.settings.currencySymbol) }
    var fyMonth by remember(snapshot.settings) { mutableStateOf(snapshot.settings.financialYearStartMonth.toString()) }
    var theme by remember(snapshot.settings) { mutableStateOf(snapshot.settings.themeMode) }
    var bio by remember(snapshot.settings) { mutableStateOf(snapshot.settings.biometricLockEnabled) }
    var hideMoney by remember(snapshot.settings) { mutableStateOf(snapshot.settings.hideSensitiveMoney) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SarimCard {
                Text("Account", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("${snapshot.profile.displayName} • ${snapshot.profile.email}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().testTag("settings_sign_out_btn")) {
                    Text("Sign Out", color = accents.danger)
                }
            }
        }
        item {
            SarimCard {
                Text("Workspace Preferences", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = currency, onValueChange = { currency = it.take(12) }, label = { Text("Currency Symbol") }, modifier = Modifier.fillMaxWidth().testTag("settings_currency_input"))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = fyMonth, onValueChange = { fyMonth = it.filter(Char::isDigit).take(2) }, label = { Text("Financial Year Start Month (1-12)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Text("Theme Mode", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("dark", "light", "system").forEach { m ->
                        FilterChip(selected = theme == m, onClick = { theme = m }, label = { Text(m.replaceFirstChar { it.uppercase() }) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Biometric App Lock", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = bio, onCheckedChange = { bio = it }, modifier = Modifier.testTag("settings_bio_switch"))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Privacy Mask Monetary Totals", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = hideMoney, onCheckedChange = { hideMoney = it }, modifier = Modifier.testTag("settings_privacy_switch"))
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        vm.updateSettings(
                            snapshot.settings.copy(
                                currencySymbol = currency.ifBlank { "Rs" },
                                financialYearStartMonth = (fyMonth.toLongOrNull() ?: 7L).coerceIn(1L, 12L),
                                themeMode = theme,
                                biometricLockEnabled = bio,
                                hideSensitiveMoney = hideMoney
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("settings_save_btn")
                ) { Text("Save Settings") }
            }
        }
    }
}
