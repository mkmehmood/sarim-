package com.gullzubair.sarim.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimDangerButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

/**
 * Port of the web app's `rep-customer-management-screen`: one rep customer's header
 * (name, phone, address, ledger page), balance and totals, search, a mode filter,
 * and the full rep history. Editing reuses [EditCustomerScreen] via [onEdit].
 */
private val REP_MODE_FILTERS = listOf(
    "all" to "All",
    "sale" to "Sales",
    "collection" to "Collections",
    "return" to "Returns"
)

@Composable
fun RepCustomerManagementScreen(
    snapshot: WorkspaceSnapshot,
    customerName: String,
    vm: SarimViewModel,
    onEdit: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney

    var query by remember { mutableStateOf("") }
    var modeFilter by remember { mutableStateOf("all") }
    var confirmDelete by remember { mutableStateOf(false) }

    val customer = remember(snapshot.customers, customerName) {
        snapshot.customers.firstOrNull { !it.recycled && it.name.equals(customerName, true) }
    }
    val ledgerPage = remember(customer) {
        Regex("page:([^;\\n]+)").find(customer?.notes ?: "")?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }
    val records = remember(snapshot.repRecords, customerName) {
        snapshot.repRecords
            .filter { !it.recycled && it.customerName.equals(customerName, true) }
            .sortedByDescending { it.date }
    }
    val balance = remember(snapshot, customerName) {
        FinanceEngine.computeCustomerBalance(customerName, snapshot.customers, snapshot.sales, snapshot.repRecords)
    }
    val soldKg = remember(records) { InputGuards.round3(records.filter { it.mode == "sale" }.sumOf { it.qtyKg }) }
    val soldValue = remember(records) { InputGuards.round2(records.filter { it.mode == "sale" }.sumOf { it.totalAmount }) }
    val collected = remember(records) { InputGuards.round2(records.sumOf { it.cashReceived }) }
    val returned = remember(records) { InputGuards.round2(records.filter { it.mode == "return" }.sumOf { it.totalAmount }) }

    val visible = remember(records, query, modeFilter) {
        val q = query.trim().lowercase()
        records.filter { r ->
            (modeFilter == "all" || r.mode == modeFilter) &&
                (q.isEmpty() ||
                    r.repName.lowercase().contains(q) ||
                    r.date.contains(q) ||
                    r.mode.contains(q) ||
                    (r.notes ?: "").lowercase().contains(q))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = customerName.uppercase(),
            subtitle = listOfNotNull(
                customer?.phone?.takeIf { it.isNotBlank() },
                customer?.address?.takeIf { it.isNotBlank() },
                ledgerPage.takeIf { it.isNotBlank() }?.let { "Page $it" }
            ).joinToString(" • ").ifBlank { "No contact details" },
            onBack = onBack,
            trailingContent = {
                Surface(
                    onClick = onEdit,
                    shape = RoundedCornerShape(14.dp),
                    color = accents.softPillFill,
                    border = BorderStroke(1.dp, accents.emerald),
                    modifier = Modifier.testTag("rep_cust_edit_btn")
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit customer", tint = accents.emerald, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Edit", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                SarimCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SarimHalfRingKpiBox(
                            label = "BALANCE DUE",
                            value = InputGuards.formatMoney(balance, currency, masked),
                            ringColor = if (balance > 0) accents.gold else accents.emerald,
                            modifier = Modifier.weight(1f)
                        )
                        SarimHalfRingKpiBox(
                            label = "SOLD",
                            value = InputGuards.formatKg(soldKg),
                            ringColor = accents.cyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SarimHalfRingKpiBox(
                            label = "SALE VALUE",
                            value = InputGuards.formatMoney(soldValue, currency, masked),
                            ringColor = accents.purple,
                            modifier = Modifier.weight(1f)
                        )
                        SarimHalfRingKpiBox(
                            label = "COLLECTED",
                            value = InputGuards.formatMoney(collected, currency, masked),
                            ringColor = accents.emerald,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (returned > 0.0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Returns credited: ${InputGuards.formatMoney(returned, currency, masked)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = accents.textMuted
                        )
                    }
                    if (!customer?.phone.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${customer?.phone}")))
                                },
                                modifier = Modifier.size(48.dp).testTag("rep_cust_call_btn")
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Call customer", tint = accents.emerald)
                            }
                            Text(customer?.phone.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = accents.emerald)
                        }
                    }
                }
            }

            item {
                SarimPillInput(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search history...",
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "rep_cust_search"
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(REP_MODE_FILTERS) { (key, label) ->
                        FilterChip(
                            selected = modeFilter == key,
                            onClick = { modeFilter = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            if (visible.isEmpty()) {
                item {
                    EmptyStateCard(
                        "No rep history",
                        if (records.isEmpty()) "No rep sales recorded for $customerName yet." else "Nothing matches this search or filter."
                    )
                }
            } else {
                items(visible, key = { it.id }) { r ->
                    val isReturn = r.mode == "return"
                    val amountText = when (r.mode) {
                        "collection" -> InputGuards.formatMoney(r.cashReceived, currency, masked)
                        else -> InputGuards.formatMoney(r.totalAmount, currency, masked)
                    }
                    SarimCard(modifier = Modifier.testTag("rep_cust_row_${r.id}")) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${r.mode.uppercase()} • ${r.repName}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    DateUtils.formatPretty(r.date) +
                                        (if (r.qtyKg > 0.0) " • ${InputGuards.formatKg(r.qtyKg)}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                amountText,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isReturn || r.mode == "collection") accents.emerald else accents.gold
                            )
                        }
                        if (r.mode == "sale" && r.creditAmount > 0.0) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Cash ${InputGuards.formatMoney(r.cashReceived, currency, masked)} • Credit ${InputGuards.formatMoney(r.creditAmount, currency, masked)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = accents.textMuted
                            )
                        }
                        if (!r.notes.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(r.notes.orEmpty(), style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                    }
                }
            }

            item {
                SarimDangerButton(
                    text = "Delete Customer & Rep History",
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "rep_cust_delete_btn"
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete Rep Customer") },
            text = {
                Text(
                    "Move \"$customerName\" and ${records.size} rep record${if (records.size != 1) "s" else ""} to the Recycle Bin?" +
                        (if (balance > 0.0) "\nOutstanding balance: ${InputGuards.formatNum(balance)}" else "")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    records.forEach { vm.recycleRecord("rep_records", it.id, "${it.customerName} (${it.date})") }
                    customer?.let { vm.recycleRecord("customers", it.id, it.name) }
                    confirmDelete = false
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
