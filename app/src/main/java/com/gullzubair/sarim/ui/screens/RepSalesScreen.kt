package com.gullzubair.sarim.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.DecodedDataUrlImage
import com.gullzubair.sarim.ui.components.DiscardEditDialog
import com.gullzubair.sarim.ui.components.EditDiffConfirmDialog
import com.gullzubair.sarim.ui.components.EditedBadge
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.RepDetailSheet
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimYearMonthDayTree
import com.gullzubair.sarim.ui.components.SimpleBarChart
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.LocationHelper

@Composable
fun RepSalesScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onOpenCustomer: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val isOwnRepOnly = remember(session) {
        FinanceEngine.hasOwnRepAccess(session) && session.mode != "admin"
    }
    val lockedRepName = remember(snapshot.settings.repProfile, snapshot.profile.displayName) {
        snapshot.settings.repProfile.ifBlank { snapshot.profile.displayName.ifBlank { "Rep" } }
    }
    val masked = session.restrictMoney || snapshot.settings.hideSensitiveMoney
    val activeStores = remember(snapshot.stores) { snapshot.stores.filter { it.deletedAt == null && it.active } }

    val knownReps = remember(snapshot) {
        (snapshot.settings.reps +
            snapshot.teamMembers.map { it.displayName } +
            snapshot.repRecords.filter { it.deletedAt == null }.map { it.salesRep } +
            snapshot.profile.displayName)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }
    val clientNames = remember(snapshot.clients, snapshot.repRecords, isOwnRepOnly, lockedRepName) {
        (snapshot.clients.filter { it.deletedAt == null }.map { it.name } +
            snapshot.repRecords.filter {
                it.deletedAt == null && (!isOwnRepOnly || it.salesRep.equals(lockedRepName, true))
            }.map { it.customerName })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sorted()
    }

    var subTab by remember { mutableIntStateOf(0) }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var repName by remember(lockedRepName) { mutableStateOf(lockedRepName) }
    var mode by remember { mutableStateOf("CASH") } // CASH | CREDIT | COLLECTION | REP_RETURN | MONEY_OUT
    var customerName by remember { mutableStateOf("") }
    var selectedStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var qtyStr by remember { mutableStateOf("") }
    var rateStr by remember(selectedStore) { mutableStateOf(selectedStore?.salePrice?.takeIf { it > 0 }?.toString() ?: "") }
    var cashStr by remember { mutableStateOf("") }
    var expiredStr by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var capturedLatLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var gpsStatus by remember { mutableStateOf("GPS not captured") }
    var selectedRepRec by remember { mutableStateOf<SalesRecord?>(null) }

    // In-place edit state
    var editingRepRec by remember { mutableStateOf<SalesRecord?>(null) }
    var showEditDiff by remember { mutableStateOf(false) }
    var showDiscardEdit by remember { mutableStateOf(false) }

    fun resetForm() {
        editingRepRec = null
        date = todayIso
        qtyStr = ""
        cashStr = ""
        expiredStr = "0"
        notes = ""
        if (isOwnRepOnly) repName = lockedRepName
    }

    fun startEdit(r: SalesRecord) {
        editingRepRec = r
        subTab = 0
        date = r.date.ifBlank { todayIso }
        repName = if (isOwnRepOnly) lockedRepName else r.salesRep.ifBlank { lockedRepName }
        mode = when {
            r.isRepReturn || r.paymentType == "REP_RETURN" -> "REP_RETURN"
            r.paymentType == "COLLECTION" || r.paymentType == "PARTIAL_PAYMENT" -> "COLLECTION"
            r.paymentType == "MONEY_OUT" -> "MONEY_OUT"
            r.paymentType == "CREDIT" -> "CREDIT"
            else -> "CASH"
        }
        customerName = r.customerName
        val st = activeStores.firstOrNull { it.key.equals(r.supplyStore, true) || it.name.equals(r.supplyStore, true) }
        if (st != null) selectedStore = st
        val shownQty = if (mode == "REP_RETURN") r.returnedQty else r.quantity
        qtyStr = if (shownQty > 0.0) InputGuards.fmtNum(shownQty).replace(",", "") else ""
        rateStr = if (r.unitPrice > 0.0) r.unitPrice.toString() else (st?.salePrice?.toString() ?: "")
        val shownCash = when (mode) {
            "COLLECTION", "MONEY_OUT" -> if (r.collectedAmount > 0.0) r.collectedAmount else r.totalValue
            else -> r.collectedAmount
        }
        cashStr = if (shownCash > 0.0) InputGuards.fmtNum(shownCash).replace(",", "") else ""
        expiredStr = if (r.expiredQty > 0.0) InputGuards.fmtNum(r.expiredQty).replace(",", "") else "0"
        notes = r.notes
    }

    BackHandler(enabled = editingRepRec != null) {
        showDiscardEdit = true
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val coords = LocationHelper.getBestLastLocation(context)
            capturedLatLng = coords
            gpsStatus = if (coords != null) "GPS: ${"%.5f".format(coords.first)}, ${"%.5f".format(coords.second)}" else "Location unavailable; try outdoors"
        } else {
            gpsStatus = "Location permission denied"
        }
    }

    val matchingVisitsWithin150m = remember(capturedLatLng, customerName, snapshot.repRecords) {
        val loc = capturedLatLng ?: return@remember 0
        snapshot.repRecords.count { rec ->
            rec.deletedAt == null &&
                rec.customerName.equals(customerName.trim(), true) &&
                rec.gpsLat != null && rec.gpsLng != null &&
                LocationHelper.distanceMeters(loc.first, loc.second, rec.gpsLat, rec.gpsLng) <= 150.0
        }
    }

    val scopedRepRecords = remember(snapshot.repRecords, isOwnRepOnly, lockedRepName) {
        snapshot.repRecords.filter { rec ->
            rec.deletedAt == null && (
                !isOwnRepOnly ||
                    rec.salesRep.equals(lockedRepName, ignoreCase = true) ||
                    rec.currentRepProfile.equals(lockedRepName, ignoreCase = true)
                )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Field Entry", "Customers", "Analytics", "History").forEachIndexed { idx, label ->
                val selected = subTab == idx
                Surface(
                    onClick = { subTab = idx },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) accents.softPillFill else accents.inputFill,
                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                    modifier = Modifier.weight(1f).testTag("rep_subtab_$idx")
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
                val isSaleOrReturn = mode in setOf("CASH", "CREDIT", "REP_RETURN")
                val total = when (mode) {
                    "COLLECTION", "MONEY_OUT" -> InputGuards.round2(cash)
                    else -> InputGuards.round2(qty * rate)
                }
                val credit = if (mode == "CREDIT") total else 0.0
                val confirmed = matchingVisitsWithin150m >= 2

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            if (editingRepRec != null) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Editing ${editingRepRec?.paymentType}", style = MaterialTheme.typography.titleMedium, color = accents.cyan)
                                    Surface(
                                        onClick = { showDiscardEdit = true },
                                        shape = RoundedCornerShape(12.dp),
                                        color = accents.danger.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.4f)),
                                        modifier = Modifier.testTag("rep_cancel_edit_btn")
                                    ) {
                                        Text("Cancel Edit", style = MaterialTheme.typography.labelMedium, color = accents.danger, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "CASH" to "Cash",
                                    "CREDIT" to "Credit",
                                    "COLLECTION" to "Collect",
                                    "REP_RETURN" to "Return",
                                    "MONEY_OUT" to "Payout"
                                ).forEach { (key, title) ->
                                    val selected = mode == key
                                    Surface(
                                        onClick = { mode = key },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) accents.softPillFill else accents.inputFill,
                                        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                                        modifier = Modifier.weight(1f).testTag("rep_mode_$key")
                                    ) {
                                        Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                            Text(title, style = MaterialTheme.typography.labelSmall, color = if (selected) accents.emerald else accents.textMuted, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            DatePickerField("Date", date, { date = it }, "rep_date")
                            Spacer(Modifier.height(10.dp))
                            if (isOwnRepOnly) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = accents.softPillFill,
                                    border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth().testTag("rep_locked_name")
                                ) {
                                    Text(
                                        "REP: $lockedRepName (locked)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = accents.emerald,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                    )
                                }
                            } else {
                                SarimPillInput(repName, { repName = it.take(80) }, label = "REP NAME", testTag = "rep_name_input")
                                if (knownReps.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    PickerField("Or Pick Rep", repName, knownReps, { it }, { repName = it }, "rep_picker")
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            SarimPillInput(customerName, { customerName = it.take(80) }, label = "CUSTOMER / SHOP", testTag = "rep_customer_input")
                            if (clientNames.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                PickerField(
                                    label = "Or Pick Client (${clientNames.size})",
                                    selectedLabel = customerName.ifBlank { "Browse rep clients" },
                                    items = clientNames,
                                    itemLabel = { it },
                                    onSelect = { picked ->
                                        customerName = picked
                                        val custRate = snapshot.clients.firstOrNull {
                                            it.deletedAt == null && it.name.equals(picked, true)
                                        }?.customSalePrice ?: 0.0
                                        if (custRate > 0.0) rateStr = custRate.toString()
                                    },
                                    testTag = "rep_client_picker"
                                )
                            }
                            if (isSaleOrReturn) {
                                Spacer(Modifier.height(10.dp))
                                PickerField(
                                    label = "Store",
                                    selectedLabel = selectedStore?.name ?: "Select Store",
                                    items = activeStores,
                                    itemLabel = { it.name },
                                    onSelect = { selectedStore = it; rateStr = it.salePrice.toString() },
                                    testTag = "rep_store_picker"
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DecimalField(
                                        label = if (mode == "REP_RETURN") "Returned Qty" else "Qty",
                                        value = qtyStr,
                                        onValueChange = { qtyStr = it },
                                        testTag = "rep_qty_input",
                                        modifier = Modifier.weight(1f),
                                        suffix = "kg"
                                    )
                                    DecimalField("Rate / kg", rateStr, { rateStr = it }, "rep_rate_input", Modifier.weight(1f))
                                }
                            }
                            if (mode == "COLLECTION" || mode == "MONEY_OUT") {
                                Spacer(Modifier.height(10.dp))
                                DecimalField(
                                    label = if (mode == "MONEY_OUT") "Payout Amount" else "Cash Collected",
                                    value = cashStr,
                                    onValueChange = { cashStr = it },
                                    testTag = "rep_cash_input"
                                )
                            }
                            if (mode == "COLLECTION" || mode == "REP_RETURN") {
                                Spacer(Modifier.height(10.dp))
                                DecimalField("Expired -> CHORA (optional)", expiredStr, { expiredStr = it }, "rep_expired_input", suffix = "kg")
                            }
                            if (editingRepRec == null) {
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(gpsStatus, style = MaterialTheme.typography.bodySmall, color = accents.cyan)
                                        if (confirmed) {
                                            Text("Shop location verified (3+ visits <= 150m)", style = MaterialTheme.typography.labelSmall, color = accents.emerald)
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            if (LocationHelper.hasLocationPermission(context)) {
                                                val coords = LocationHelper.getBestLastLocation(context)
                                                capturedLatLng = coords
                                                gpsStatus = if (coords != null) "GPS: ${"%.5f".format(coords.first)}, ${"%.5f".format(coords.second)}" else "Waiting for GPS fix"
                                            } else {
                                                locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                            }
                                        },
                                        modifier = Modifier.testTag("rep_gps_btn")
                                    ) {
                                        Icon(Icons.Default.MyLocation, contentDescription = "Capture GPS")
                                        Text(" Tag GPS")
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            SarimPillInput(notes, { notes = it.take(500) }, label = "VISIT NOTES", testTag = "rep_notes_input")
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox(
                                    label = if (mode == "REP_RETURN") "Return Value" else "Bill / Total",
                                    value = InputGuards.formatMoney(total, currency, masked),
                                    ringColor = accents.emerald,
                                    modifier = Modifier.weight(1f)
                                )
                                SarimHalfRingKpiBox(
                                    label = "Credit",
                                    value = InputGuards.formatMoney(credit, currency, masked),
                                    ringColor = accents.gold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            val canSave = customerName.isNotBlank() && when (mode) {
                                "COLLECTION", "MONEY_OUT" -> cash > 0.0 || expired > 0.0
                                "REP_RETURN" -> qty > 0.0 || expired > 0.0
                                else -> qty > 0.0 && rate > 0.0
                            }
                            SarimGradientButton(
                                text = if (editingRepRec != null) "Update Entry" else "Save Field Entry",
                                onClick = {
                                    if (editingRepRec != null) {
                                        showEditDiff = true
                                    } else {
                                        vm.submitRepRecord(
                                            date = date,
                                            repName = if (isOwnRepOnly) lockedRepName else repName,
                                            paymentType = mode,
                                            customer = customerName,
                                            store = selectedStore,
                                            qty = qty,
                                            rate = rate,
                                            collectedAmount = if (mode == "CASH") total else cash,
                                            expiredKg = expired,
                                            lat = capturedLatLng?.first,
                                            lng = capturedLatLng?.second,
                                            accuracy = null,
                                            confirmed = confirmed,
                                            notes = notes
                                        )
                                        resetForm()
                                    }
                                },
                                enabled = canSave,
                                testTag = "rep_save_btn"
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            1 -> {
                val activeClients = remember(snapshot.clients, isOwnRepOnly, lockedRepName) {
                    snapshot.clients.filter {
                        it.deletedAt == null && (!isOwnRepOnly || it.salesRep.isBlank() || it.salesRep.equals(lockedRepName, true))
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeClients.isEmpty()) {
                        item { EmptyStateCard("No rep clients yet", "Rep customers are added automatically when you log field sales.") }
                    } else {
                        items(activeClients, key = { it.id }) { cust ->
                            val owed = FinanceEngine.computeCustomerBalance(
                                customerName = cust.name,
                                customers = emptyList(),
                                sales = emptyList(),
                                repRecords = snapshot.repRecords,
                                calculator = snapshot.calculator,
                                stores = snapshot.stores,
                                clients = snapshot.clients,
                                includeClients = true
                            )
                            val photoUri = snapshot.photos["client:${cust.id}"]?.takeIf { !it.deleted }?.dataUri
                            val initials = cust.name.split(" ").filter { it.isNotBlank() }.take(2)
                                .joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "C" }
                            SarimCard(modifier = Modifier.clickable { onOpenCustomer(cust.name) }.testTag("rep_customer_card_${cust.id}")) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    if (!photoUri.isNullOrBlank()) {
                                        DecodedDataUrlImage(
                                            dataUrl = photoUri,
                                            contentDescription = "${cust.name} photo",
                                            modifier = Modifier.size(40.dp).clip(CircleShape)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(40.dp).clip(CircleShape).background(accents.softPillFill),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(initials, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(cust.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            listOfNotNull(
                                                cust.address.ifBlank { "No address" },
                                                "${cust.visitCount} visits",
                                                cust.ledgerPage.takeIf { it.isNotBlank() }?.let { "Page $it" }
                                            ).joinToString(" • "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accents.textMuted
                                        )
                                    }
                                    Text(
                                        InputGuards.formatMoney(owed, currency, masked),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (owed > 0.01) accents.gold else accents.emerald
                                    )
                                    if (cust.phone.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${cust.phone}"))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.size(48.dp).testTag("rep_call_${cust.id}")
                                        ) {
                                            Icon(Icons.Default.Call, contentDescription = "Call customer", tint = accents.emerald)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            2 -> {
                val repTotals = remember(scopedRepRecords) {
                    scopedRepRecords.filter { !FinanceEngine.isLedgerRow(it) }
                        .groupBy { it.salesRep.ifBlank { "Rep" } }
                        .mapValues { (_, list) -> InputGuards.round2(list.sumOf { it.totalValue }) }
                        .entries.sortedByDescending { it.value }.map { it.key to it.value }
                }
                val totalCollected = remember(scopedRepRecords) {
                    scopedRepRecords.sumOf { it.collectedAmount }
                }
                val totalReturns = remember(scopedRepRecords) {
                    scopedRepRecords.filter { it.isRepReturn || it.paymentType == "REP_RETURN" }.sumOf { it.returnValue }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("Field Sales", InputGuards.formatMoney(repTotals.sumOf { it.second }, currency, masked), accents.emerald, Modifier.weight(1f))
                                SarimHalfRingKpiBox("Collected", InputGuards.formatMoney(totalCollected, currency, masked), accents.cyan, Modifier.weight(1f))
                            }
                            if (totalReturns > 0.0) {
                                Spacer(Modifier.height(10.dp))
                                SarimHalfRingKpiBox("Rep Returns", InputGuards.formatMoney(totalReturns, currency, masked), accents.gold, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    item { SimpleBarChart("Field Sales by Rep", repTotals, accents.emerald, currency, masked) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            else -> {
                val activeRep = remember(scopedRepRecords) {
                    scopedRepRecords.sortedWith(compareByDescending<SalesRecord> { it.date }.thenByDescending { it.timestamp })
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeRep.isEmpty()) {
                        item { EmptyStateCard("No rep history", "Field sales, collections, and returns appear here.") }
                    } else {
                        item {
                            SarimYearMonthDayTree(
                                items = activeRep,
                                dateOf = { it.date },
                                summaryLabel = { list ->
                                    val kg = list.sumOf { FinanceEngine.soldQuantity(it) }
                                    val cash = list.sumOf { it.collectedAmount }
                                    "${InputGuards.formatKg(kg)} • ${InputGuards.formatMoney(cash, currency, masked)}"
                                },
                                itemRow = { r ->
                                    val displayVal = if (r.collectedAmount > 0.0) r.collectedAmount else r.totalValue
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedRepRec = r }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("${r.salesRep.ifBlank { "Rep" }} -> ${r.customerName}", style = MaterialTheme.typography.titleSmall)
                                                EditedBadge(r.isEdited)
                                            }
                                            Text(
                                                "${r.paymentType} • ${if (r.isRepReturn) InputGuards.formatKg(r.returnedQty) else InputGuards.formatKg(r.quantity)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accents.textMuted
                                            )
                                        }
                                        Text(
                                            InputGuards.formatMoney(displayVal, currency, masked),
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

    selectedRepRec?.let { r ->
        RepDetailSheet(
            r = r,
            stores = snapshot.stores,
            currency = currency,
            masked = masked,
            isAdmin = session.mode == "admin",
            vm = vm,
            onEditEntry = { startEdit(it) },
            onDismiss = { selectedRepRec = null }
        )
    }

    if (showEditDiff && editingRepRec != null) {
        val orig = editingRepRec!!
        val qty = qtyStr.toDoubleOrNull() ?: 0.0
        val rate = rateStr.toDoubleOrNull() ?: (selectedStore?.salePrice ?: 0.0)
        val cash = cashStr.toDoubleOrNull() ?: 0.0
        val expired = expiredStr.toDoubleOrNull() ?: 0.0
        val total = when (mode) {
            "COLLECTION", "MONEY_OUT" -> InputGuards.round2(cash)
            else -> InputGuards.round2(qty * rate)
        }
        EditDiffConfirmDialog(
            title = "Confirm Rep Edit",
            rows = listOf(
                Triple("Date", orig.date, date),
                Triple("Customer", orig.customerName, customerName.trim()),
                Triple("Type", orig.paymentType, mode),
                Triple("Qty", InputGuards.formatKg(if (orig.isRepReturn) orig.returnedQty else orig.quantity), InputGuards.formatKg(qty)),
                Triple("Amount", InputGuards.formatMoney(orig.totalValue, currency, masked), InputGuards.formatMoney(total, currency, masked)),
                Triple("Notes", orig.notes, notes.trim())
            ),
            onConfirm = {
                showEditDiff = false
                vm.editRepRecord(
                    original = orig,
                    date = date,
                    paymentType = mode,
                    customerName = customerName,
                    store = selectedStore,
                    quantityKg = qty,
                    unitPrice = rate,
                    collectedAmount = if (mode == "CASH") total else cash,
                    expiredKg = expired,
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
