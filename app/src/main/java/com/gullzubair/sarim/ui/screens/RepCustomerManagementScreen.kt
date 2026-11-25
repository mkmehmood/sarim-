package com.gullzubair.sarim.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DecodedDataUrlImage
import com.gullzubair.sarim.ui.components.EditHistorySection
import com.gullzubair.sarim.ui.components.EditedBadge
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimDangerButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import java.util.Locale

private val REP_MODE_FILTERS = listOf(
    "all" to "All",
    "sale" to "Sales",
    "collection" to "Collections",
    "return" to "Returns",
    "payout" to "Money Out"
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
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val masked = session.restrictMoney || snapshot.settings.hideSensitiveMoney
    val isAdmin = session.mode == "admin"

    var query by remember { mutableStateOf("") }
    var modeFilter by remember { mutableStateOf("all") }
    var confirmDelete by remember { mutableStateOf(false) }
    var expandedHistoryIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val client = remember(snapshot.clients, snapshot.customers, customerName) {
        snapshot.clients.firstOrNull { it.deletedAt == null && it.name.equals(customerName, ignoreCase = true) }
            ?: snapshot.customers.firstOrNull { it.deletedAt == null && it.name.equals(customerName, ignoreCase = true) }
    }
    val clientPhotoUri = remember(client, snapshot.photos) {
        client?.id?.let { cid ->
            (snapshot.photos["client:$cid"] ?: snapshot.photos["cust:$cid"])?.takeIf { !it.deleted }?.dataUri
        }
    }
    val ledgerPage = remember(client) {
        client?.ledgerPage?.takeIf { it.isNotBlank() }
            ?: Regex("page:([^;\\n]+)").find(client?.notes ?: "")?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }
    val records = remember(snapshot.repRecords, customerName) {
        snapshot.repRecords
            .filter { it.deletedAt == null && it.customerName.equals(customerName, ignoreCase = true) }
            .sortedWith(compareByDescending<SalesRecord> { it.date }.thenByDescending { it.timestamp })
    }
    val balance = remember(snapshot, customerName) {
        FinanceEngine.computeCustomerBalance(
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

    val customerListForPricing = remember(snapshot.clients, snapshot.customers) {
        snapshot.clients.ifEmpty { snapshot.customers }
    }
    val soldKg = remember(records) {
        InputGuards.round3(records.sumOf { FinanceEngine.soldQuantity(it) })
    }
    val soldValue = remember(records, customerListForPricing, snapshot.stores) {
        InputGuards.round2(
            records.filter { !FinanceEngine.isLedgerRow(it) }
                .sumOf { FinanceEngine.effectiveSaleValue(it, customerListForPricing, snapshot.stores) }
        )
    }
    val collected = remember(records, customerListForPricing, snapshot.stores) {
        InputGuards.round2(
            records.sumOf { r ->
                val v = FinanceEngine.effectiveSaleValue(r, customerListForPricing, snapshot.stores)
                FinanceEngine.rowReceived(r, v).received
            }
        )
    }
    val returned = remember(records) {
        InputGuards.round2(
            records.filter { it.isRepReturn || it.paymentType == "REP_RETURN" }
                .sumOf { it.returnValue }
        )
    }

    val visible = remember(records, query, modeFilter) {
        val q = query.trim().lowercase(Locale.ROOT)
        records.filter { r ->
            val modeMatch = when (modeFilter) {
                "sale" -> !FinanceEngine.isLedgerRow(r)
                "collection" -> FinanceEngine.isCollectionRow(r)
                "return" -> r.isRepReturn || r.paymentType == "REP_RETURN"
                "payout" -> r.paymentType == "MONEY_OUT"
                else -> true
            }
            val qMatch = q.isEmpty() ||
                r.salesRep.lowercase(Locale.ROOT).contains(q) ||
                r.date.contains(q) ||
                r.paymentType.lowercase(Locale.ROOT).contains(q) ||
                r.supplyStore.lowercase(Locale.ROOT).contains(q) ||
                r.notes.lowercase(Locale.ROOT).contains(q)
            modeMatch && qMatch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = customerName.uppercase(Locale.ROOT),
            subtitle = listOfNotNull(
                client?.phone?.takeIf { it.isNotBlank() },
                client?.address?.takeIf { it.isNotBlank() },
                client?.salesRep?.takeIf { it.isNotBlank() }?.let { "Rep: $it" },
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
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit customer",
                            tint = accents.emerald,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Edit", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                SarimCard {
                    if (!clientPhotoUri.isNullOrBlank()) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DecodedDataUrlImage(
                                dataUrl = clientPhotoUri,
                                contentDescription = "$customerName photo",
                                modifier = Modifier.size(52.dp).clip(CircleShape)
                            )
                            Column {
                                Text(customerName, style = MaterialTheme.typography.titleMedium)
                                if (ledgerPage.isNotBlank()) {
                                    Text("Ledger Page: $ledgerPage", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                }
                            }
                        }
                    }
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
                    if ((client?.oldDebit ?: 0.0) > 0.0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Opening debit: ${InputGuards.formatMoney(client?.oldDebit ?: 0.0, currency, masked)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = accents.textMuted
                        )
                    }
                    if (!client?.phone.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${client?.phone}")))
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("rep_cust_call_btn")
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Call customer", tint = accents.emerald)
                            }
                            Text(client?.phone.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = accents.emerald)
                        }
                    }
                }
            }

            item {
                SarimPillInput(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search rep history...",
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
                    val isReturn = r.isRepReturn || r.paymentType == "REP_RETURN"
                    val isColl = FinanceEngine.isCollectionRow(r)
                    val isOut = r.paymentType == "MONEY_OUT"
                    val effectiveVal = FinanceEngine.effectiveSaleValue(r, customerListForPricing, snapshot.stores)
                    val displayAmount = when {
                        isColl -> if (r.collectedAmount > 0.0) r.collectedAmount else r.totalValue
                        isReturn -> r.returnValue
                        else -> effectiveVal
                    }
                    val storeLabel = FormulaEngine.storeLabelFor(r.supplyStore, snapshot.stores)
                    val kgShown = if (isReturn) r.returnedQty else r.quantity
                    val historyOpen = r.id in expandedHistoryIds
                    val hasHistory = r.editHistory.isNotEmpty() || r.timestamp > 0L

                    SarimCard(
                        modifier = Modifier
                            .clickable(enabled = hasHistory) {
                                expandedHistoryIds = if (historyOpen) expandedHistoryIds - r.id else expandedHistoryIds + r.id
                            }
                            .testTag("rep_cust_row_${r.id}")
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = buildString {
                                            append(r.paymentType.uppercase(Locale.ROOT))
                                            if (r.salesRep.isNotBlank()) append(" • ${r.salesRep}")
                                        },
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    EditedBadge(r.isEdited)
                                }
                                Text(
                                    text = buildString {
                                        append(DateUtils.formatPretty(r.date))
                                        if (kgShown > 0.0) append(" • ${InputGuards.formatKg(kgShown)}")
                                        if (storeLabel.isNotBlank()) append(" • $storeLabel")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = InputGuards.formatMoney(displayAmount, currency, masked),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = when {
                                    isOut -> accents.danger
                                    isReturn || isColl || r.paymentType == "CASH" -> accents.emerald
                                    else -> accents.gold
                                }
                            )
                        }
                        if (!FinanceEngine.isLedgerRow(r) && r.paymentType == "CREDIT") {
                            Spacer(Modifier.height(4.dp))
                            val paidPart = if (r.creditReceived) effectiveVal else r.partialPaymentReceived
                            val unpaidPart = (effectiveVal - paidPart).coerceAtLeast(0.0)
                            Text(
                                "Paid ${InputGuards.formatMoney(paidPart, currency, masked)} • Due ${InputGuards.formatMoney(unpaidPart, currency, masked)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = accents.textMuted
                            )
                        }
                        if (r.expiredQty > 0.0) {
                            Spacer(Modifier.height(4.dp))
                            Text("Expired / Chora: ${InputGuards.formatKg(r.expiredQty)}", style = MaterialTheme.typography.bodySmall, color = accents.gold)
                        }
                        if (r.gpsLat != null && r.gpsLng != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "GPS: ${String.format(Locale.US, "%.5f, %.5f", r.gpsLat, r.gpsLng)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = accents.cyan
                            )
                        }
                        if (r.notes.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(r.notes, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        AnimatedVisibility(visible = historyOpen) {
                            Column {
                                Spacer(Modifier.height(10.dp))
                                EditHistorySection(
                                    editHistory = r.editHistory,
                                    createdAtEpoch = r.timestamp,
                                    addedLabel = "Recorded",
                                    canDelete = isAdmin,
                                    masked = masked,
                                    onDeleteEntry = { idx -> vm.deleteRecordEditHistory("rep", r.id, idx) }
                                )
                            }
                        }
                    }
                }
            }

            if (isAdmin) {
                item {
                    SarimDangerButton(
                        text = "Delete Customer & Rep History",
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "rep_cust_delete_btn"
                    )
                }
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
                        (if (balance > 0.0 && !masked) "\nOutstanding balance: ${InputGuards.formatMoney(balance, currency, false)}" else "")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    records.forEach { rec ->
                        vm.softDeleteRecord(
                            collection = "rep",
                            docId = rec.id,
                            displayName = "${rec.customerName} (${rec.salesRep})",
                            displayDetail = "${rec.date} • ${rec.paymentType}",
                            displayAmount = InputGuards.fmtNum(rec.totalValue),
                            snapshotMap = WorkspaceJsonCodec.saleToMap(rec)
                        )
                    }
                    client?.let { c ->
                        val col = if (snapshot.clients.any { it.id == c.id }) "clients" else "customers"
                        vm.softDeleteRecord(
                            collection = col,
                            docId = c.id,
                            displayName = c.name,
                            displayDetail = c.phone.ifBlank { c.address },
                            displayAmount = InputGuards.fmtNum(c.oldDebit),
                            snapshotMap = WorkspaceJsonCodec.customerToMap(c)
                        )
                    }
                    confirmDelete = false
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
