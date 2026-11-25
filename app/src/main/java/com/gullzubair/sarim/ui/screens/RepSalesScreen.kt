package com.gullzubair.sarim.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.MetricKpiCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SimpleBarChart
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.LocationHelper

@Composable
fun RepSalesScreen(snapshot: WorkspaceSnapshot, todayIso: String, vm: SarimViewModel) {
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
        TabRow(selectedTabIndex = subTab) {
            listOf("Field Entry", "Customers", "Analytics", "History").forEachIndexed { idx, label ->
                Tab(selected = subTab == idx, onClick = { subTab = idx }, text = { Text(label) }, modifier = Modifier.testTag("rep_subtab_$idx"))
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
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = mode == "sale", onClick = { mode = "sale" }, label = { Text("Sale") }, modifier = Modifier.testTag("rep_mode_sale"))
                                FilterChip(selected = mode == "collection", onClick = { mode = "collection" }, label = { Text("Collect") }, modifier = Modifier.testTag("rep_mode_collection"))
                                FilterChip(selected = mode == "return", onClick = { mode = "return" }, label = { Text("Return") }, modifier = Modifier.testTag("rep_mode_return"))
                            }
                            Spacer(Modifier.height(10.dp))
                            DatePickerField("Date", date, { date = it }, "rep_date")
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = repName,
                                onValueChange = { repName = it.take(80) },
                                label = { Text("Rep Name") },
                                modifier = Modifier.fillMaxWidth().testTag("rep_name_input")
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it.take(80) },
                                label = { Text("Customer / Shop") },
                                modifier = Modifier.fillMaxWidth().testTag("rep_customer_input")
                            )
                            if (mode != "collection") {
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
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it.take(500) },
                                label = { Text("Visit Notes") },
                                modifier = Modifier.fillMaxWidth().testTag("rep_notes_input")
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MetricKpiCard("Bill Total", InputGuards.formatMoney(total, currency, masked), accent = accents.emerald, modifier = Modifier.weight(1f))
                                MetricKpiCard("Credit", InputGuards.formatMoney(credit, currency, masked), accent = accents.gold, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(
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
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("rep_save_btn")
                            ) {
                                Text("Save Field Entry")
                            }
                        }
                    }
                }
            }

            1 -> {
                val activeCustomers = remember(snapshot.customers) { snapshot.customers.filter { !it.recycled } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeCustomers.isEmpty()) {
                        item { EmptyStateCard("No customers yet", "Customers are added automatically when you log sales.") }
                    } else {
                        items(activeCustomers, key = { it.id }) { cust ->
                            SarimCard {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(cust.name, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${cust.address.ifBlank { "No address" }} • ${cust.visitCount} visits",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accents.textMuted
                                        )
                                    }
                                    if (cust.phone.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${cust.phone}"))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.testTag("rep_call_${cust.id}")
                                        ) {
                                            Icon(Icons.Default.Call, contentDescription = "Call customer", tint = accents.emerald)
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SimpleBarChart("Field Sales by Rep", repTotals, accents.emerald, currency, masked)
                    }
                }
            }

            else -> {
                val activeRep = remember(snapshot.repRecords) { snapshot.repRecords.filter { !it.recycled } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeRep.isEmpty()) {
                        item { EmptyStateCard("No rep history", "Field sales, collections, and returns appear here.") }
                    } else {
                        items(activeRep, key = { it.id }) { r ->
                            SarimCard {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${r.repName} -> ${r.customerName} (${r.mode.uppercase()})", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${r.date} • Qty ${InputGuards.formatKg(r.qtyKg)} • Cash ${InputGuards.formatMoney(r.cashReceived, currency, masked)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accents.textMuted
                                        )
                                    }
                                    IconButton(
                                        onClick = { vm.recycleRecord("rep_records", r.id, "${r.customerName} (${r.date})") },
                                        modifier = Modifier.testTag("rep_delete_${r.id}")
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
