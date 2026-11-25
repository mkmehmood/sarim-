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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.SalesRecord
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
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

@Composable
fun SalesScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onOpenCustomerDetail: (String) -> Unit,
    onEditCustomer: (String) -> Unit
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
    var selectedSale by remember { mutableStateOf<SalesRecord?>(null) }

    val customerDirectory = remember(snapshot) {
        (snapshot.customers.filter { !it.recycled }.map { it.name } +
            snapshot.sales.filter { !it.recycled }.map { it.customerName } +
            snapshot.repRecords.filter { !it.recycled }.map { it.customerName })
            .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }.sorted()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Sale / Collect", "Customers", "History").forEachIndexed { idx, label ->
                val selected = subTab == idx
                Surface(
                    onClick = { subTab = idx },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) accents.softPillFill else accents.inputFill,
                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                    modifier = Modifier.weight(1f).testTag("sales_subtab_$idx")
                ) {
                    Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) accents.emerald else accents.textMuted, maxLines = 1)
                    }
                }
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
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("sale" to "New Sale", "collection" to "Collection / Return").forEach { (key, title) ->
                                    val selected = mode == key
                                    Surface(
                                        onClick = { mode = key },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (selected) accents.softPillFill else accents.inputFill,
                                        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                                        modifier = Modifier.weight(1f).testTag("sales_mode_$key")
                                    ) {
                                        Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                            Text(title, style = MaterialTheme.typography.labelLarge, color = if (selected) accents.emerald else accents.textMuted)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            DatePickerField("Date", date, { date = it }, "sales_date")
                            Spacer(Modifier.height(10.dp))
                            SarimPillInput(
                                value = customerName,
                                onValueChange = { customerName = it.take(80) },
                                label = "CUSTOMER NAME",
                                placeholder = "Type or pick customer",
                                testTag = "sales_customer_input"
                            )
                            if (customerName.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Current Balance: ${InputGuards.formatMoney(existingBal, currency, masked)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.emerald
                                )
                            }
                            if (customerDirectory.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
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
                                Switch(checked = addToLedger, onCheckedChange = { addToLedger = it }, modifier = Modifier.testTag("sales_ledger_switch"))
                            }
                            SarimPillInput(
                                value = notes,
                                onValueChange = { notes = it.take(500) },
                                label = "NOTES",
                                placeholder = "Optional note",
                                testTag = "sales_notes_input"
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("Bill / Chora", InputGuards.formatMoney(if (mode == "sale") totalAmt else choraValue, currency, masked), accents.cyan, Modifier.weight(1f))
                                SarimHalfRingKpiBox("New Balance", InputGuards.formatMoney(existingBal + creditAmt - (if (mode == "collection") cash + choraValue else choraValue), currency, masked), accents.gold, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(14.dp))
                            SarimGradientButton(
                                text = if (mode == "sale") "Save Sale" else "Save Collection",
                                onClick = {
                                    vm.submitSale(date, mode, customerName, selectedStore, selectedRep, qty, rate, cash, expired, rate, addToLedger, notes)
                                    qtyStr = ""
                                    cashStr = ""
                                    expiredStr = "0"
                                    notes = ""
                                },
                                enabled = customerName.isNotBlank() && ((mode == "sale" && qty > 0) || (mode == "collection" && (cash > 0 || expired > 0))),
                                testTag = "sales_save_btn"
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            1 -> {
                var query by remember { mutableStateOf("") }
                val filtered = remember(customerDirectory, query) {
                    customerDirectory.filter { it.contains(query, ignoreCase = true) }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        SarimPillInput(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Search customers...",
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            testTag = "sales_customer_search"
                        )
                    }
                    items(filtered, key = { it }) { name ->
                        val bal = FinanceEngine.computeCustomerBalance(name, snapshot.customers, snapshot.sales, snapshot.repRecords)
                        val rec = snapshot.customers.firstOrNull { !it.recycled && it.name.equals(name, true) }
                        val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "C" }
                        SarimCard(
                            modifier = Modifier.clickable { onOpenCustomerDetail(name) }.testTag("customer_card_$name"),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(42.dp).clip(CircleShape).background(accents.softPillFill),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(initials, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        rec?.phone?.takeIf { it.isNotBlank() } ?: "Tap for statement & PDF",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accents.textMuted
                                    )
                                }
                                Text(
                                    InputGuards.formatMoney(bal, currency, masked),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (bal > 0.01) accents.gold else accents.emerald
                                )
                                IconButton(
                                    onClick = { onEditCustomer(name) },
                                    modifier = Modifier.size(48.dp).testTag("edit_cust_$name")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit $name", tint = accents.textMuted)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            else -> {
                val activeSales = remember(snapshot.sales) { snapshot.sales.filter { !it.recycled }.sortedByDescending { it.date } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeSales.isEmpty()) {
                        item { EmptyStateCard("No sales yet", "Create a sale or collection on the first tab.") }
                    } else {
                        item {
                            SarimYearMonthDayTree(
                                items = activeSales,
                                dateOf = { it.date },
                                summaryLabel = { list ->
                                    "${InputGuards.formatKg(list.sumOf { it.qtyKg })} • ${InputGuards.formatMoney(list.sumOf { it.cashReceived }, currency, masked)}"
                                },
                                itemRow = { s ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedSale = s }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(s.customerName.uppercase(), style = MaterialTheme.typography.titleSmall)
                                            Text("${s.mode.uppercase()} • ${s.storeName ?: s.storeKey} • ${InputGuards.formatKg(s.qtyKg)}", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                        }
                                        Text(
                                            InputGuards.formatMoney(s.cashReceived, currency, masked),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = accents.emerald
                                        )
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

    selectedSale?.let { s ->
        SarimDetailBottomSheet(
            title = s.customerName.uppercase(),
            rightTitle = InputGuards.formatMoney(s.totalAmount.takeIf { it > 0 } ?: s.cashReceived, currency, masked),
            subtitle = "${DateUtils.formatPretty(s.date).uppercase()} • ${s.mode.uppercase()}",
            onDismiss = { selectedSale = null },
            content = {
                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Quantity", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(InputGuards.formatKg(s.qtyKg), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cash received", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(InputGuards.formatMoney(s.cashReceived, currency, masked), style = MaterialTheme.typography.titleSmall, color = accents.emerald)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Credit", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(InputGuards.formatMoney(s.creditAmount, currency, masked), style = MaterialTheme.typography.titleSmall, color = accents.gold)
                }
            },
            footer = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SarimDangerButton(
                        text = "Delete",
                        onClick = {
                            vm.recycleRecord("sales", s.id, "${s.customerName} (${s.date})")
                            selectedSale = null
                        },
                        modifier = Modifier.weight(0.45f),
                        testTag = "sales_delete_${s.id}"
                    )
                    SarimGradientButton(
                        text = "Edit Customer",
                        onClick = {
                            selectedSale = null
                            onEditCustomer(s.customerName)
                        },
                        modifier = Modifier.weight(0.55f)
                    )
                }
            }
        )
    }
}
