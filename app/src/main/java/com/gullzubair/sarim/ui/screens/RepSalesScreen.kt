package com.gullzubair.sarim.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.HorizontalDivider
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
import com.gullzubair.sarim.data.model.RepRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
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
import com.gullzubair.sarim.ui.components.SimpleBarChart
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.LocationHelper

@Composable
fun RepSalesScreen(snapshot: WorkspaceSnapshot, todayIso: String, vm: SarimViewModel, onOpenCustomer: (String) -> Unit = {}) {
    val context = LocalContext.current
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney
    val activeStores = remember(snapshot.stores) { snapshot.stores.filter { it.active } }

    var subTab by remember { mutableIntStateOf(0) }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var repName by remember(snapshot.profile) { mutableStateOf(snapshot.profile.displayName.ifBlank { "Rep" }) }
    var mode by remember { mutableStateOf("sale") }
    var customerName by remember { mutableStateOf("") }
    var selectedStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var qtyStr by remember { mutableStateOf("") }
    var rateStr by remember(selectedStore) { mutableStateOf(selectedStore?.salePrice?.toString() ?: "") }
    var cashStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var capturedLatLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var gpsStatus by remember { mutableStateOf("GPS not captured") }
    var selectedRepRec by remember { mutableStateOf<RepRecord?>(null) }

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
            !rec.recycled &&
                rec.customerName.equals(customerName.trim(), true) &&
                rec.routeLat != null && rec.routeLng != null &&
                LocationHelper.distanceMeters(loc.first, loc.second, rec.routeLat, rec.routeLng) <= 150.0
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
                val total = if (mode != "collection") InputGuards.round2(qty * rate) else 0.0
                val credit = if (mode == "sale") InputGuards.round2((total - cash).coerceAtLeast(0.0)) else 0.0
                val confirmed = matchingVisitsWithin150m >= 2

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("sale" to "Sale", "collection" to "Collect", "return" to "Return").forEach { (key, title) ->
                                    val selected = mode == key
                                    Surface(
                                        onClick = { mode = key },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (selected) accents.softPillFill else accents.inputFill,
                                        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                                        modifier = Modifier.weight(1f).testTag("rep_mode_$key")
                                    ) {
                                        Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                            Text(title, style = MaterialTheme.typography.labelLarge, color = if (selected) accents.emerald else accents.textMuted)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            DatePickerField("Date", date, { date = it }, "rep_date")
                            Spacer(Modifier.height(10.dp))
                            SarimPillInput(repName, { repName = it.take(80) }, label = "REP NAME", testTag = "rep_name_input")
                            Spacer(Modifier.height(10.dp))
                            SarimPillInput(customerName, { customerName = it.take(80) }, label = "CUSTOMER / SHOP", testTag = "rep_customer_input")
                            if (mode != "collection") {
                                Spacer(Modifier.height(10.dp))
                                PickerField("Store", selectedStore?.name ?: "Select Store", activeStores, { it.name }, { selectedStore = it; rateStr = it.salePrice.toString() }, "rep_store_picker")
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DecimalField("Qty", qtyStr, { qtyStr = it }, "rep_qty_input", Modifier.weight(1f), "kg")
                                    DecimalField("Rate", rateStr, { rateStr = it }, "rep_rate_input", Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            if (mode != "return") {
                                DecimalField("Cash Collected", cashStr, { cashStr = it }, "rep_cash_input")
                                Spacer(Modifier.height(10.dp))
                            }
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
                            Spacer(Modifier.height(8.dp))
                            SarimPillInput(notes, { notes = it.take(500) }, label = "VISIT NOTES", testTag = "rep_notes_input")
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("Bill Total", InputGuards.formatMoney(total, currency, masked), accents.emerald, Modifier.weight(1f))
                                SarimHalfRingKpiBox("Credit", InputGuards.formatMoney(credit, currency, masked), accents.gold, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(14.dp))
                            SarimGradientButton(
                                text = "Save Field Entry",
                                onClick = {
                                    vm.submitRepRecord(
                                        date, repName, mode, customerName, selectedStore, qty, rate, cash,
                                        capturedLatLng?.first, capturedLatLng?.second, confirmed, notes
                                    )
                                    qtyStr = ""
                                    cashStr = ""
                                    notes = ""
                                },
                                enabled = customerName.isNotBlank() && ((mode == "collection" && cash > 0) || (mode != "collection" && qty > 0)),
                                testTag = "rep_save_btn"
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            1 -> {
                val activeCustomers = remember(snapshot.customers) { snapshot.customers.filter { !it.recycled } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeCustomers.isEmpty()) {
                        item { EmptyStateCard("No customers yet", "Customers are added automatically when you log sales.") }
                    } else {
                        items(activeCustomers, key = { it.id }) { cust ->
                            SarimCard(modifier = Modifier.clickable { onOpenCustomer(cust.name) }.testTag("rep_customer_card_${cust.id}")) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(cust.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${cust.address.ifBlank { "No address" }} • ${cust.visitCount} visits", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                    }
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
                val repTotals = remember(snapshot.repRecords) {
                    snapshot.repRecords.filter { !it.recycled && it.mode == "sale" }
                        .groupBy { it.repName }
                        .mapValues { (_, list) -> InputGuards.round2(list.sumOf { it.totalAmount }) }
                        .entries.sortedByDescending { it.value }.map { it.key to it.value }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { SimpleBarChart("Field Sales by Rep", repTotals, accents.emerald, currency, masked) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            else -> {
                val activeRep = remember(snapshot.repRecords) { snapshot.repRecords.filter { !it.recycled }.sortedByDescending { it.date } }
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
                                summaryLabel = { list -> "${InputGuards.formatKg(list.sumOf { it.qtyKg })} • ${InputGuards.formatMoney(list.sumOf { it.cashReceived }, currency, masked)}" },
                                itemRow = { r ->
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
                                            Text("${r.repName} -> ${r.customerName}", style = MaterialTheme.typography.titleSmall)
                                            Text("${r.mode.uppercase()} • ${InputGuards.formatKg(r.qtyKg)}", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                        }
                                        Text(
                                            InputGuards.formatMoney(r.cashReceived, currency, masked),
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
        SarimDetailBottomSheet(
            title = r.customerName.uppercase(),
            rightTitle = InputGuards.formatMoney(r.totalAmount.takeIf { it > 0 } ?: r.cashReceived, currency, masked),
            subtitle = "${DateUtils.formatPretty(r.date).uppercase()} • ${r.repName}",
            onDismiss = { selectedRepRec = null },
            content = {
                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mode", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(r.mode.uppercase(), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Quantity", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(InputGuards.formatKg(r.qtyKg), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cash collected", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(InputGuards.formatMoney(r.cashReceived, currency, masked), style = MaterialTheme.typography.titleSmall, color = accents.emerald)
                }
            },
            footer = {
                SarimDangerButton(
                    text = "Move to Recycle Bin",
                    onClick = {
                        vm.recycleRecord("rep_records", r.id, "${r.customerName} (${r.date})")
                        selectedRepRec = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "rep_delete_${r.id}"
                )
            }
        )
    }
}
