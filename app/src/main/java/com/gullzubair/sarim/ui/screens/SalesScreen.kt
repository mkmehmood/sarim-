package com.gullzubair.sarim.ui.screens

import androidx.activity.compose.BackHandler
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
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.DecodedDataUrlImage
import com.gullzubair.sarim.ui.components.DiscardEditDialog
import com.gullzubair.sarim.ui.components.EditDiffConfirmDialog
import com.gullzubair.sarim.ui.components.EditedBadge
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SalesDetailSheet
import com.gullzubair.sarim.ui.components.SarimCard
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
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val isSalesRestricted = remember(session) {
        FinanceEngine.isMoneyRestricted(session) && "sales" in session.tabs
    }
    val masked = isSalesRestricted || session.restrictMoney || snapshot.settings.hideSensitiveMoney
    val activeStores = remember(snapshot.stores) { snapshot.stores.filter { it.deletedAt == null && it.active } }
    val stockSnapshots = remember(snapshot) { FinanceEngine.computeStoreStockSnapshots(snapshot) }

    val repNames = remember(snapshot) {
        (listOf("NONE") +
            snapshot.settings.reps +
            snapshot.teamMembers.map { it.displayName } +
            snapshot.repRecords.filter { it.deletedAt == null }.map { it.salesRep } +
            snapshot.entities.filter { it.deletedAt == null && (it.type == "staff" || it.type == "employee") }.map { it.name })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    var subTab by remember(isSalesRestricted) { mutableIntStateOf(0) }
    var paymentType by remember { mutableStateOf("CASH") }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var supplyDate by remember(todayIso) { mutableStateOf(todayIso) }
    var customerName by remember { mutableStateOf("") }
    var selectedRep by remember { mutableStateOf("NONE") }
    var selectedStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var qtyStr by remember { mutableStateOf("") }
    var rateStr by remember(selectedStore) { mutableStateOf(selectedStore?.salePrice?.takeIf { it > 0 }?.toString() ?: "") }
    var collectedStr by remember { mutableStateOf("") }
    var expiredStr by remember { mutableStateOf("0") }
    var choraRateStr by remember(selectedStore) { mutableStateOf(selectedStore?.salePrice?.takeIf { it > 0 }?.toString() ?: "") }
    var addToLedger by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var selectedSale by remember { mutableStateOf<SalesRecord?>(null) }

    // In-place edit state
    var editingSale by remember { mutableStateOf<SalesRecord?>(null) }
    var showEditDiff by remember { mutableStateOf(false) }
    var showDiscardEdit by remember { mutableStateOf(false) }

    fun resetForm() {
        editingSale = null
        date = todayIso
        supplyDate = todayIso
        qtyStr = ""
        collectedStr = ""
        expiredStr = "0"
        notes = ""
    }

    fun startEdit(s: SalesRecord) {
        editingSale = s
        subTab = 0
        paymentType = when (s.paymentType) {
            "COLLECTION", "PARTIAL_PAYMENT" -> "COLLECTION"
            "MONEY_OUT" -> "MONEY_OUT"
            "CREDIT" -> "CREDIT"
            else -> "CASH"
        }
        date = s.date.ifBlank { todayIso }
        supplyDate = s.supplyDate.ifBlank { date }
        customerName = s.customerName
        selectedRep = s.salesRep.ifBlank { "NONE" }
        val st = activeStores.firstOrNull { it.key.equals(s.supplyStore, true) || it.name.equals(s.supplyStore, true) }
        if (st != null) selectedStore = st
        qtyStr = if (s.quantity > 0.0) InputGuards.fmtNum(s.quantity).replace(",", "") else ""
        rateStr = if (s.unitPrice > 0.0) s.unitPrice.toString() else (st?.salePrice?.toString() ?: "")
        collectedStr = if (s.collectedAmount > 0.0) InputGuards.fmtNum(s.collectedAmount).replace(",", "")
        else if (s.totalValue > 0.0 && (paymentType == "COLLECTION" || paymentType == "MONEY_OUT")) InputGuards.fmtNum(s.totalValue).replace(",", "") else ""
        expiredStr = if (s.expiredQty > 0.0) InputGuards.fmtNum(s.expiredQty).replace(",", "") else "0"
        choraRateStr = if (s.expiredUnitPrice > 0.0) s.expiredUnitPrice.toString() else rateStr
        notes = s.notes
    }

    BackHandler(enabled = editingSale != null) {
        showDiscardEdit = true
    }

    val customerDirectory = remember(snapshot.customers, snapshot.sales) {
        (snapshot.customers.filter { it.deletedAt == null }.map { it.name } +
            snapshot.sales.filter { it.deletedAt == null }.map { it.customerName })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sorted()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isSalesRestricted) {
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
        }

        val activeTab = if (isSalesRestricted) 0 else subTab
        when (activeTab) {
            0 -> {
                val qty = qtyStr.toDoubleOrNull() ?: 0.0
                val rate = rateStr.toDoubleOrNull() ?: (selectedStore?.salePrice ?: 0.0)
                val collected = collectedStr.toDoubleOrNull() ?: 0.0
                val expired = expiredStr.toDoubleOrNull() ?: 0.0
                val choraRate = choraRateStr.toDoubleOrNull() ?: rate
                val isSaleMode = paymentType == "CASH" || paymentType == "CREDIT"
                val totalAmt = if (isSaleMode) InputGuards.round2(qty * rate) else 0.0
                val choraValue = if (paymentType == "COLLECTION") InputGuards.round2(expired * choraRate) else 0.0
                val availKg = stockSnapshots.firstOrNull { it.storeKey.equals(selectedStore?.key ?: "", ignoreCase = true) }?.availableKg ?: 0.0
                val existingBal = remember(customerName, snapshot) {
                    if (customerName.isBlank()) 0.0
                    else FinanceEngine.computeCustomerBalance(
                        customerName = customerName,
                        customers = snapshot.customers,
                        sales = snapshot.sales,
                        repRecords = snapshot.repRecords,
                        calculator = snapshot.calculator,
                        stores = snapshot.stores,
                        clients = snapshot.clients,
                        includeClients = true
                    )
                }
                val projectedBal = when (paymentType) {
                    "CREDIT" -> InputGuards.round2(existingBal + totalAmt)
                    "COLLECTION" -> InputGuards.round2(existingBal - collected - choraValue)
                    "MONEY_OUT" -> InputGuards.round2(existingBal + collected)
                    else -> existingBal
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isSalesRestricted) item { Spacer(Modifier.height(6.dp)) }
                    item {
                        SarimCard {
                            if (editingSale != null) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Editing ${editingSale?.paymentType}", style = MaterialTheme.typography.titleMedium, color = accents.cyan)
                                    Surface(
                                        onClick = { showDiscardEdit = true },
                                        shape = RoundedCornerShape(12.dp),
                                        color = accents.danger.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.4f)),
                                        modifier = Modifier.testTag("sales_cancel_edit_btn")
                                    ) {
                                        Text("Cancel Edit", style = MaterialTheme.typography.labelMedium, color = accents.danger, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("CASH" to "Cash", "CREDIT" to "Credit", "COLLECTION" to "Collect", "MONEY_OUT" to "Payout").forEach { (key, title) ->
                                    val selected = paymentType == key
                                    Surface(
                                        onClick = { paymentType = key },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) accents.softPillFill else accents.inputFill,
                                        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                                        modifier = Modifier.weight(1f).testTag("sales_mode_$key")
                                    ) {
                                        Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                            Text(title, style = MaterialTheme.typography.labelMedium, color = if (selected) accents.emerald else accents.textMuted, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            if (isSaleMode) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DatePickerField("Sale Date", date, { date = it }, "sales_date", Modifier.weight(1f))
                                    DatePickerField("Supply Date", supplyDate, { supplyDate = it }, "sales_supply_date", Modifier.weight(1f))
                                }
                            } else {
                                DatePickerField("Date", date, { date = it }, "sales_date")
                            }
                            Spacer(Modifier.height(10.dp))
                            SarimPillInput(
                                value = customerName,
                                onValueChange = { customerName = it.take(80) },
                                label = "CUSTOMER NAME",
                                placeholder = "Type or pick customer",
                                testTag = "sales_customer_input"
                            )
                            if (!isSalesRestricted && customerName.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Current Balance: ${InputGuards.formatMoney(existingBal, currency, masked)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (existingBal > 0.01) accents.gold else accents.emerald
                                )
                            }
                            if (customerDirectory.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                PickerField(
                                    label = "Or Pick Existing Customer",
                                    selectedLabel = customerName.ifBlank { "Browse directory (${customerDirectory.size})" },
                                    items = customerDirectory,
                                    itemLabel = { it },
                                    onSelect = { picked ->
                                        customerName = picked
                                        val custRate = snapshot.customers.firstOrNull {
                                            it.deletedAt == null && it.name.equals(picked, ignoreCase = true)
                                        }?.customSalePrice ?: 0.0
                                        if (custRate > 0.0) rateStr = custRate.toString()
                                    },
                                    testTag = "sales_customer_picker"
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            PickerField("Sales Rep", selectedRep, repNames, { it }, { selectedRep = it }, "sales_rep_picker")
                            if (isSaleMode) {
                                Spacer(Modifier.height(10.dp))
                                PickerField(
                                    label = "Store (Avail: ${InputGuards.formatKg(availKg)})",
                                    selectedLabel = selectedStore?.name ?: "Select Store",
                                    items = activeStores,
                                    itemLabel = { s ->
                                        val av = stockSnapshots.firstOrNull { it.storeKey.equals(s.key, true) }?.availableKg ?: 0.0
                                        "${s.name} • Avail ${InputGuards.formatKg(av)}"
                                    },
                                    onSelect = {
                                        selectedStore = it
                                        rateStr = it.salePrice.toString()
                                        choraRateStr = it.salePrice.toString()
                                    },
                                    testTag = "sales_store_picker"
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DecimalField("Qty Sold", qtyStr, { qtyStr = it }, "sales_qty_input", Modifier.weight(1f), "kg")
                                    DecimalField("Rate / kg", rateStr, { rateStr = it }, "sales_rate_input", Modifier.weight(1f))
                                }
                            } else if (paymentType == "COLLECTION") {
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DecimalField("Cash Collected", collectedStr, { collectedStr = it }, "sales_cash_input", Modifier.weight(1f))
                                    DecimalField("Expired -> CHORA", expiredStr, { expiredStr = it }, "sales_expired_input", Modifier.weight(1f), "kg")
                                }
                                if (expired > 0.0) {
                                    Spacer(Modifier.height(10.dp))
                                    DecimalField("CHORA Credit Rate / kg", choraRateStr, { choraRateStr = it }, "sales_chora_rate_input")
                                }
                            } else {
                                Spacer(Modifier.height(10.dp))
                                DecimalField("Payout / Refund Amount", collectedStr, { collectedStr = it }, "sales_cash_input")
                            }
                            if (editingSale == null && paymentType != "CREDIT") {
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Mirror in Daily Ledger", style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = addToLedger, onCheckedChange = { addToLedger = it }, modifier = Modifier.testTag("sales_ledger_switch"))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            SarimPillInput(notes, { notes = it.take(500) }, label = "NOTES", placeholder = "Optional note", testTag = "sales_notes_input")
                            if (!isSalesRestricted) {
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SarimHalfRingKpiBox(
                                        label = when (paymentType) {
                                            "COLLECTION" -> "Total Settled"
                                            "MONEY_OUT" -> "Payout Out"
                                            else -> "Bill Total"
                                        },
                                        value = InputGuards.formatMoney(
                                            when (paymentType) {
                                                "COLLECTION" -> InputGuards.round2(collected + choraValue)
                                                "MONEY_OUT" -> collected
                                                else -> totalAmt
                                            },
                                            currency,
                                            masked
                                        ),
                                        ringColor = accents.cyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                    SarimHalfRingKpiBox(
                                        label = "New Balance",
                                        value = InputGuards.formatMoney(projectedBal, currency, masked),
                                        ringColor = if (projectedBal > 0.01) accents.gold else accents.emerald,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            val canSave = customerName.isNotBlank() && when (paymentType) {
                                "CASH", "CREDIT" -> selectedStore != null && qty > 0.0 && rate > 0.0
                                "COLLECTION" -> collected > 0.0 || expired > 0.0
                                else -> collected > 0.0
                            }
                            SarimGradientButton(
                                text = if (editingSale != null) "Update Entry" else when (paymentType) {
                                    "CASH" -> "Save Cash Sale"
                                    "CREDIT" -> "Save Credit Sale"
                                    "COLLECTION" -> "Save Collection"
                                    else -> "Save Payout"
                                },
                                onClick = {
                                    if (editingSale != null) {
                                        showEditDiff = true
                                    } else {
                                        vm.submitSale(
                                            date = date,
                                            supplyDate = if (isSaleMode) supplyDate else date,
                                            paymentType = paymentType,
                                            customer = customerName,
                                            store = selectedStore,
                                            rep = selectedRep,
                                            qty = qty,
                                            rate = rate,
                                            collectedAmount = if (paymentType == "CASH") totalAmt else collected,
                                            expiredKg = if (paymentType == "COLLECTION") expired else 0.0,
                                            choraRate = choraRate,
                                            addToLedger = addToLedger && paymentType != "CREDIT",
                                            notes = notes
                                        )
                                        resetForm()
                                    }
                                },
                                enabled = canSave,
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
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        val bal = FinanceEngine.computeCustomerBalance(
                            customerName = name,
                            customers = snapshot.customers,
                            sales = snapshot.sales,
                            repRecords = snapshot.repRecords,
                            calculator = snapshot.calculator,
                            stores = snapshot.stores,
                            clients = snapshot.clients,
                            includeClients = true
                        )
                        val rec = snapshot.customers.firstOrNull { it.deletedAt == null && it.name.equals(name, true) }
                        val photoUri = rec?.id?.let { cid -> snapshot.photos["cust:$cid"]?.takeIf { !it.deleted }?.dataUri }
                        val initials = name.split(" ").filter { it.isNotBlank() }.take(2)
                            .joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "C" }
                        SarimCard(
                            modifier = Modifier.clickable { onOpenCustomerDetail(name) }.testTag("customer_card_$name"),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (!photoUri.isNullOrBlank()) {
                                    DecodedDataUrlImage(
                                        dataUrl = photoUri,
                                        contentDescription = "$name photo",
                                        modifier = Modifier.size(42.dp).clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.size(42.dp).clip(CircleShape).background(accents.softPillFill),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(initials, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                                    }
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
                val activeSales = remember(snapshot.sales) {
                    snapshot.sales.filter { it.deletedAt == null }
                        .sortedWith(compareByDescending<SalesRecord> { it.date }.thenByDescending { it.timestamp })
                }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (activeSales.isEmpty()) {
                        item { EmptyStateCard("No sales yet", "Create a sale or collection on the first tab.") }
                    } else {
                        item {
                            SarimYearMonthDayTree(
                                items = activeSales,
                                dateOf = { it.date },
                                summaryLabel = { list ->
                                    val kg = list.sumOf { FinanceEngine.soldQuantity(it) }
                                    val valSum = list.sumOf { if (FinanceEngine.isCollectionRow(it)) it.collectedAmount else it.totalValue }
                                    "${InputGuards.formatKg(kg)} • ${InputGuards.formatMoney(valSum, currency, masked)}"
                                },
                                itemRow = { s ->
                                    val storeLabel = FormulaEngine.storeLabelFor(s.supplyStore, snapshot.stores)
                                    val displayVal = if (FinanceEngine.isCollectionRow(s)) s.collectedAmount else s.totalValue
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
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(s.customerName.uppercase(), style = MaterialTheme.typography.titleSmall)
                                                EditedBadge(s.isEdited)
                                            }
                                            Text(
                                                listOfNotNull(
                                                    s.paymentType,
                                                    storeLabel.takeIf { it.isNotBlank() },
                                                    if (s.quantity > 0) InputGuards.formatKg(s.quantity) else null,
                                                    if (s.expiredQty > 0) "Exp ${InputGuards.formatKg(s.expiredQty)}" else null
                                                ).joinToString(" • "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accents.textMuted
                                            )
                                        }
                                        Text(
                                            InputGuards.formatMoney(displayVal, currency, masked),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (s.paymentType == "CREDIT" && !s.creditReceived) accents.gold else accents.emerald
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
        SalesDetailSheet(
            s = s,
            stores = snapshot.stores,
            currency = currency,
            masked = masked,
            isAdmin = session.mode == "admin",
            vm = vm,
            onEditEntry = { startEdit(it) },
            onEditCustomer = onEditCustomer,
            onDismiss = { selectedSale = null }
        )
    }

    if (showEditDiff && editingSale != null) {
        val orig = editingSale!!
        val qty = qtyStr.toDoubleOrNull() ?: 0.0
        val rate = rateStr.toDoubleOrNull() ?: (selectedStore?.salePrice ?: 0.0)
        val collected = collectedStr.toDoubleOrNull() ?: 0.0
        val expired = expiredStr.toDoubleOrNull() ?: 0.0
        val choraRate = choraRateStr.toDoubleOrNull() ?: rate
        val isSaleMode = paymentType == "CASH" || paymentType == "CREDIT"
        val totalAmt = if (isSaleMode) InputGuards.round2(qty * rate) else collected
        EditDiffConfirmDialog(
            title = "Confirm Sales Edit",
            rows = listOf(
                Triple("Date", orig.date, date),
                Triple("Customer", orig.customerName, customerName.trim()),
                Triple("Type", orig.paymentType, paymentType),
                Triple("Qty", InputGuards.formatKg(orig.quantity), InputGuards.formatKg(qty)),
                Triple("Amount", InputGuards.formatMoney(orig.totalValue, currency, masked), InputGuards.formatMoney(totalAmt, currency, masked)),
                Triple("Notes", orig.notes, notes.trim())
            ),
            onConfirm = {
                showEditDiff = false
                vm.editSaleOrCollection(
                    original = orig,
                    date = date,
                    supplyDate = if (isSaleMode) supplyDate else date,
                    paymentType = paymentType,
                    customerName = customerName,
                    store = selectedStore,
                    salesRep = selectedRep,
                    quantityKg = qty,
                    unitPrice = rate,
                    collectedAmount = if (paymentType == "CASH") totalAmt else collected,
                    expiredKg = if (paymentType == "COLLECTION") expired else 0.0,
                    expiredUnitPrice = choraRate,
                    notes = notes
                ) { resetForm() }
            },
            onDismiss = { showEditDiff = false }
        )
    }

    if (showDiscardEdit) {
        DiscardEditDialog(
            onDiscard = {
                showDiscardEdit = false
                resetForm()
            },
            onDismiss = { showDiscardEdit = false }
        )
    }
}
