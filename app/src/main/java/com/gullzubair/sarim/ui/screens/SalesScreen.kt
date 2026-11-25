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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
fun SalesScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onOpenCustomerDetail: (String) -> Unit
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney
    val activeStores = remember(snapshot.stores) { snapshot.stores.filter { it.active } }
    val stockSnapshots = remember(snapshot) { FinanceEngine.computeStoreStockSnapshots(snapshot) }

    val repNames = remember(snapshot) {
        (listOf("NONE") + snapshot.teamMembers.map { it.displayName } +
            snapshot.repRecords.filter { !it.recycled }.map { it.repName } +
            snapshot.entities.filter { !it.recycled && it.entityType == "staff" }.map { it.name })
            .map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    var subTab by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf("sale") }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var customerName by remember { mutableStateOf("") }
    var selectedRep by remember { mutableStateOf("NONE") }
    var selectedStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var qtyStr by remember { mutableStateOf("") }
    var rateStr by remember(selectedStore) { mutableStateOf(selectedStore?.salePrice?.toString() ?: "") }
    var cashStr by remember { mutableStateOf("") }
    var expiredStr by remember { mutableStateOf("0") }
    var addToLedger by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }

    val customerDirectory = remember(snapshot) {
        (snapshot.customers.filter { !it.recycled }.map { it.name } +
            snapshot.sales.filter { !it.recycled }.map { it.customerName } +
            snapshot.repRecords.filter { !it.recycled }.map { it.customerName })
            .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sorted()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            listOf("Sale / Collect", "Customers", "History").forEachIndexed { idx, label ->
                Tab(selected = subTab == idx, onClick = { subTab = idx }, text = { Text(label) }, modifier = Modifier.testTag("sales_subtab_$idx"))
            }
        }

        when (subTab) {
            0 -> {
                val qty = qtyStr.toDoubleOrNull() ?: 0.0
                val rate = rateStr.toDoubleOrNull() ?: (selectedStore?.salePrice ?: 0.0)
                val cash = cashStr.toDoubleOrNull() ?: 0.0
                val expired = expiredStr.toDoubleOrNull() ?: 0.0
                val totalAmt = if (mode == "sale") InputGuards.round2(qty * rate) else 0.0
                val creditAmt = if (mode == "sale") InputGuards.round2((totalAmt - cash).coerceAtLeast(0.0)) else 0.0
                val choraValue = InputGuards.round2(expired * rate)
                val availKg = stockSnapshots.firstOrNull { it.storeKey == selectedStore?.storeKey }?.availableKg ?: 0.0
                val existingBal = remember(customerName, snapshot) {
                    if (customerName.isBlank()) 0.0
                    else FinanceEngine.computeCustomerBalance(customerName, snapshot.customers, snapshot.sales, snapshot.repRecords)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = mode == "sale",
                                    onClick = { mode = "sale" },
                                    label = { Text("New Sale") },
                                    modifier = Modifier.testTag("sales_mode_sale")
                                )
                                FilterChip(
                                    selected = mode == "collection",
                                    onClick = { mode = "collection" },
                                    label = { Text("Collection / Return") },
                                    modifier = Modifier.testTag("sales_mode_collection")
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            DatePickerField("Date", date, { date = it }, "sales_date")
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it.take(80) },
                                label = { Text("Customer Name") },
                                supportingText = {
                                    if (customerName.isNotBlank()) {
                                        Text("Current Balance: ${InputGuards.formatMoney(existingBal, currency, masked)}")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("sales_customer_input")
                            )
                            if (customerDirectory.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                PickerField(
                                    label = "Or Pick Existing Customer",
                                    selectedLabel = customerName.ifBlank { "Browse directory (${customerDirectory.size})" },
                                    items = customerDirectory,
                                    itemLabel = { it },
                                    onSelect = { customerName = it },
                                    testTag = "sales_customer_picker"
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            PickerField(
                                label = "Sales Rep",
                                selectedLabel = selectedRep,
                                items = repNames,
                                itemLabel = { it },
                                onSelect = { selectedRep = it },
                                testTag = "sales_rep_picker"
                            )
                            if (mode == "sale") {
                                Spacer(Modifier.height(10.dp))
                                PickerField(
                                    label = "Store (Avail: ${InputGuards.formatKg(availKg)})",
                                    selectedLabel = selectedStore?.name ?: "Select Store",
                                    items = activeStores,
                                    itemLabel = { s ->
                                        val av = stockSnapshots.firstOrNull { it.storeKey == s.storeKey }?.availableKg ?: 0.0
                                        "${s.name} • Avail ${InputGuards.formatKg(av)}"
                                    },
                                    onSelect = {
                                        selectedStore = it
                                        rateStr = it.salePrice.toString()
                                    },
                                    testTag = "sales_store_picker"
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DecimalField("Qty Sold", qtyStr, { qtyStr = it }, "sales_qty_input", Modifier.weight(1f), "kg")
                                    DecimalField("Rate / kg", rateStr, { rateStr = it }, "sales_rate_input", Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DecimalField("Cash Received", cashStr, { cashStr = it }, "sales_cash_input", Modifier.weight(1f))
                                DecimalField("Expired -> CHORA", expiredStr, { expiredStr = it }, "sales_expired_input", Modifier.weight(1f), "kg")
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Mirror cash in Daily Ledger", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = addToLedger,
                                    onCheckedChange = { addToLedger = it },
                                    modifier = Modifier.testTag("sales_ledger_switch")
                                )
                            }
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it.take(500) },
                                label = { Text("Notes") },
                                modifier = Modifier.fillMaxWidth().testTag("sales_notes_input")
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MetricKpiCard("Bill / Chora", InputGuards.formatMoney(if (mode == "sale") totalAmt else choraValue, currency, masked), accent = accents.cyan, modifier = Modifier.weight(1f))
                                MetricKpiCard("New Balance", InputGuards.formatMoney(existingBal + creditAmt - (if (mode == "collection") cash + choraValue else choraValue), currency, masked), accent = accents.gold, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    vm.submitSale(date, mode, customerName, selectedStore, selectedRep, qty, rate, cash, expired, rate, addToLedger, notes)
                                    qtyStr = ""
                                    cashStr = ""
                                    expiredStr = "0"
                                    notes = ""
                                },
                                enabled = customerName.isNotBlank() && ((mode == "sale" && qty > 0) || (mode == "collection" && (cash > 0 || expired > 0))),
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sales_save_btn")
                            ) {
                                Text(if (mode == "sale") "Save Sale" else "Save Collection")
                            }
                        }
                    }
                }
            }

            1 -> {
                var query by remember { mutableStateOf("") }
                val filtered = remember(customerDirectory, query) {
                    customerDirectory.filter { it.contains(query, ignoreCase = true) }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search customers") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth().testTag("sales_customer_search")
                        )
                    }
                    items(filtered, key = { it }) { name ->
                        val bal = FinanceEngine.computeCustomerBalance(name, snapshot.customers, snapshot.sales, snapshot.repRecords)
                        SarimCard(modifier = Modifier.clickable { onOpenCustomerDetail(name) }.testTag("customer_card_$name")) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.titleMedium)
                                    Text("Tap to view ledger & export PDF statement", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                }
                                Text(
                                    InputGuards.formatMoney(bal, currency, masked),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (bal > 0.01) accents.gold else accents.emerald
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                val activeSales = remember(snapshot.sales) { snapshot.sales.filter { !it.recycled } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeSales.isEmpty()) {
                        item { EmptyStateCard("No sales yet", "Create a sale or collection on the first tab.") }
                    } else {
                        items(activeSales, key = { it.id }) { s ->
                            SarimCard {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${s.customerName} • ${s.mode.uppercase()}", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${s.date} • ${s.storeName ?: s.storeKey} • Qty ${InputGuards.formatKg(s.qtyKg)} • Cash ${InputGuards.formatMoney(s.cashReceived, currency, masked)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accents.textMuted
                                        )
                                    }
                                    IconButton(
                                        onClick = { vm.recycleRecord("sales", s.id, "${s.customerName} (${s.date})") },
                                        modifier = Modifier.testTag("sales_delete_${s.id}")
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
