package com.gullzubair.sarim.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
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
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.RepSalesAnalyticsEngine
import com.gullzubair.sarim.ui.components.DecodedDataUrlImage
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.RepBlockingAlertDialog
import com.gullzubair.sarim.ui.components.RepDetailSheet
import com.gullzubair.sarim.ui.components.RepHistoryRowCard
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimDangerButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.PdfExporter
import com.gullzubair.sarim.util.StatementRow
import java.util.Locale
import kotlin.math.abs

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
    val isOwnRepOnly = remember(session) { FinanceEngine.hasOwnRepAccess(session) && !isAdmin }
    val lockedRepName = remember(snapshot.settings.repProfile, snapshot.profile.displayName) {
        snapshot.settings.repProfile.ifBlank { snapshot.profile.displayName.ifBlank { "Rep" } }
    }

    var query by remember { mutableStateOf("") }
    var modeFilter by remember { mutableStateOf("all") }
    var confirmDelete by remember { mutableStateOf(false) }
    var selectedRecord by remember { mutableStateOf<SalesRecord?>(null) }
    var blockingAlert by remember { mutableStateOf<Pair<String, String>?>(null) }

    val client = remember(snapshot.clients, snapshot.customers, customerName, isOwnRepOnly, lockedRepName) {
        snapshot.clients.firstOrNull {
            it.deletedAt == null &&
                it.name.equals(customerName, ignoreCase = true) &&
                (!isOwnRepOnly || it.salesRep.isBlank() || it.salesRep.equals(lockedRepName, true))
        } ?: snapshot.clients.firstOrNull { it.deletedAt == null && it.name.equals(customerName, ignoreCase = true) }
            ?: snapshot.customers.firstOrNull { it.deletedAt == null && it.name.equals(customerName, ignoreCase = true) }
    }
    val effectiveRepScope = remember(isOwnRepOnly, lockedRepName, client) {
        when {
            isOwnRepOnly -> lockedRepName
            !client?.salesRep.isNullOrBlank() -> client!!.salesRep
            else -> ""
        }
    }
    val clientPhotoUri = remember(client, snapshot.photos, effectiveRepScope, customerName) {
        val cid = client?.id.orEmpty()
        val key1 = if (cid.isNotBlank()) "client:$cid" else ""
        val key2 = "rep-cust:${effectiveRepScope.lowercase(Locale.ROOT)}:${customerName.trim().lowercase(Locale.ROOT)}"
        val key3 = if (cid.isNotBlank()) "cust:$cid" else ""
        listOf(key1, key2, key3).firstNotNullOfOrNull { k ->
            if (k.isBlank()) null else snapshot.photos[k]?.takeIf { !it.deleted }?.dataUri
        }
    }
    val ledgerPage = remember(client) {
        client?.ledgerPage?.takeIf { it.isNotBlank() }
            ?: Regex("page:([^;\\n]+)").find(client?.notes ?: "")?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    val records = remember(snapshot.repRecords, customerName, isOwnRepOnly, lockedRepName) {
        val base = if (isOwnRepOnly) {
            RepSalesAnalyticsEngine.scopedRepRecords(snapshot.repRecords, lockedRepName)
        } else {
            snapshot.repRecords.filter { it.deletedAt == null }
        }
        base.filter { it.customerName.equals(customerName, ignoreCase = true) }
            .sortedWith(compareByDescending<SalesRecord> { it.date }.thenByDescending { it.timestamp })
    }

    val customerListForPricing = remember(snapshot.clients, snapshot.customers) {
        snapshot.clients.ifEmpty { snapshot.customers }
    }
    val honor = remember(records) { FinanceEngine.allocHonor(records) }
    val reconciledIds = remember(snapshot.calculator) { FinanceEngine.reconciledRepReturnIds(snapshot.calculator) }

    val hasOldDebtTx = remember(records) { records.any { it.transactionType == "OLD_DEBT" } }
    val openingDebit = remember(hasOldDebtTx, client) {
        if (hasOldDebtTx) 0.0 else (client?.oldDebit ?: 0.0)
    }

    // Chronological running balance map + PDF statement rows
    val (runningBalanceById, pdfRows) = remember(records, openingDebit, reconciledIds, honor, customerListForPricing, snapshot.stores, masked) {
        val balMap = LinkedHashMap<String, Double>()
        val rowsOut = mutableListOf<StatementRow>()
        var run = openingDebit
        if (abs(openingDebit) > 0.005) {
            rowsOut.add(
                StatementRow(
                    date = "Opening",
                    description = "Opening Balance",
                    debit = if (openingDebit > 0.0 && !masked) InputGuards.fmtNum(openingDebit) else "-",
                    credit = if (openingDebit < 0.0 && !masked) InputGuards.fmtNum(-openingDebit) else "-",
                    balance = if (masked) "••••" else InputGuards.fmtNum(run)
                )
            )
        }
        val asc = records.sortedWith(compareBy<SalesRecord> { it.date }.thenBy { it.timestamp }.thenBy { it.id })
        asc.forEach { s ->
            val gross = FinanceEngine.effectiveSaleValue(s, customerListForPricing, snapshot.stores)
            val split = FinanceEngine.ledgerSplit(s, gross, reconciledIds, honor)
            run = InputGuards.round2(run + split.delta)
            balMap[s.id] = run
            val qtyTxt = when {
                s.quantity > 0.0 -> " • ${InputGuards.formatKg(s.quantity)}"
                s.returnedQty > 0.0 -> " • Ret ${InputGuards.formatKg(s.returnedQty)}"
                s.expiredQty > 0.0 -> " • Exp ${InputGuards.formatKg(s.expiredQty)}"
                else -> ""
            }
            val desc = buildString {
                append(s.paymentType)
                if (s.salesRep.isNotBlank()) append(" (${s.salesRep})")
                append(qtyTxt)
                if (s.notes.isNotBlank()) append(" — ${s.notes.take(40)}")
            }
            rowsOut.add(
                StatementRow(
                    date = s.date,
                    description = desc,
                    debit = if (split.debit > 0.005) (if (masked) "••••" else InputGuards.fmtNum(split.debit)) else "-",
                    credit = if (split.credit > 0.005) (if (masked) "••••" else InputGuards.fmtNum(split.credit)) else "-",
                    balance = if (masked) "••••" else InputGuards.fmtNum(run)
                )
            )
        }
        balMap to rowsOut
    }

    val balance = remember(records, openingDebit, runningBalanceById) {
        val lastAsc = records.sortedWith(compareBy<SalesRecord> { it.date }.thenBy { it.timestamp }.thenBy { it.id }).lastOrNull()
        if (lastAsc != null) (runningBalanceById[lastAsc.id] ?: openingDebit) else InputGuards.round2(openingDebit)
    }

    val soldKg = remember(records) { InputGuards.round3(records.sumOf { FinanceEngine.soldQuantity(it) }) }
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
    val totalExpiredKg = remember(records) { InputGuards.round2(records.sumOf { it.expiredQty }) }
    val collPct = remember(soldValue, collected, openingDebit) {
        val denom = soldValue + openingDebit.coerceAtLeast(0.0)
        if (denom <= 0.0) 1f else (collected / denom).toFloat().coerceIn(0f, 1f)
    }

    val bestGps: Pair<Double, Double>? = remember(client, records) {
        if (client?.gpsLat != null && client.gpsLng != null) {
            client.gpsLat to client.gpsLng
        } else {
            records.firstOrNull { it.gpsLat != null && it.gpsLng != null }?.let { it.gpsLat!! to it.gpsLng!! }
        }
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
                effectiveRepScope.takeIf { it.isNotBlank() }?.let { "Rep: $it" },
                ledgerPage.takeIf { it.isNotBlank() }?.let { "Page $it" }
            ).joinToString(" • ").ifBlank { "Rep customer ledger" },
            onBack = onBack,
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (pdfRows.isEmpty()) {
                                vm.showToast("No ledger rows to export", com.gullzubair.sarim.ui.viewmodel.ToastKind.WARNING)
                            } else {
                                val footer = "Total Sold: ${InputGuards.formatKg(soldKg)} • Sale Value: " +
                                    "${InputGuards.formatMoney(soldValue, currency, masked)} • Collected: " +
                                    "${InputGuards.formatMoney(collected, currency, masked)} • Balance: " +
                                    InputGuards.formatMoney(balance, currency, masked)
                                PdfExporter.exportAndShareStatement(
                                    context = context,
                                    title = "$customerName — Rep Statement",
                                    subtitle = listOfNotNull(
                                        effectiveRepScope.takeIf { it.isNotBlank() }?.let { "Rep: $it" },
                                        client?.phone?.takeIf { it.isNotBlank() },
                                        ledgerPage.takeIf { it.isNotBlank() }?.let { "Page $it" }
                                    ).joinToString(" • "),
                                    rows = pdfRows,
                                    summaryFooter = footer
                                )
                            }
                        },
                        modifier = Modifier.size(48.dp).testTag("rep_cust_export_pdf")
                    ) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Export PDF statement", tint = accents.cyan)
                    }
                    Surface(
                        onClick = onEdit,
                        shape = RoundedCornerShape(999.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.emerald),
                        modifier = Modifier.testTag("rep_cust_edit_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit customer", tint = accents.emerald, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Edit", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                        }
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
                            ringColor = if (balance > 0.01) accents.gold else accents.emerald,
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

                    // Collection progress meter
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Collection progress", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                        Text(
                            text = if (masked) "••%" else "${(collPct * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accents.emerald
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(accents.inputFill)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(collPct).fillMaxHeight().background(accents.emerald)
                        )
                    }

                    if (totalExpiredKg > 0.0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Expired -> CHORA returned: ${InputGuards.formatKg(totalExpiredKg)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = accents.cyan
                        )
                    }
                    if (openingDebit > 0.0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Opening debit: ${InputGuards.formatMoney(openingDebit, currency, masked)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = accents.textMuted
                        )
                    }

                    if (!client?.phone.isNullOrBlank() || bestGps != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!client?.phone.isNullOrBlank()) {
                                Surface(
                                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${client?.phone}"))) } },
                                    shape = RoundedCornerShape(12.dp),
                                    color = accents.softPillFill,
                                    border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.4f)),
                                    modifier = Modifier.weight(1f).testTag("rep_cust_call_btn")
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Rounded.Call, contentDescription = "Call customer", tint = accents.emerald, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(client?.phone.orEmpty(), style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                                    }
                                }
                            }
                            if (bestGps != null) {
                                Surface(
                                    onClick = {
                                        val uri = Uri.parse("geo:${bestGps.first},${bestGps.second}?q=${bestGps.first},${bestGps.second}(${Uri.encode(customerName)})")
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = accents.inputFill,
                                    border = BorderStroke(1.dp, accents.cyan.copy(alpha = 0.45f)),
                                    modifier = Modifier.weight(1f).testTag("rep_cust_maps_btn")
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Rounded.LocationOn, contentDescription = "Open in Maps", tint = accents.cyan, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Open Maps", style = MaterialTheme.typography.labelLarge, color = accents.cyan)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SarimPillInput(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search rep history...",
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
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
                    RepHistoryRowCard(
                        r = r,
                        stores = snapshot.stores,
                        runningBalanceAfter = runningBalanceById[r.id],
                        isSelectedDate = false,
                        currency = currency,
                        masked = masked,
                        onClick = { selectedRecord = r }
                    )
                }
            }

            if (isAdmin) {
                item {
                    SarimDangerButton(
                        text = "Delete Customer & Rep History",
                        onClick = {
                            val blockedRec = records.firstOrNull { RepSalesAnalyticsEngine.getRepSaleBlockReason(snapshot, it, forEdit = false).isNotBlank() }
                            if (blockedRec != null) {
                                blockingAlert = "Cannot Delete Customer" to RepSalesAnalyticsEngine.getRepSaleBlockReason(snapshot, blockedRec, forEdit = false)
                            } else {
                                confirmDelete = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "rep_cust_delete_btn"
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    selectedRecord?.let { r ->
        RepDetailSheet(
            r = r,
            snapshot = snapshot,
            runningBalanceAfter = runningBalanceById[r.id],
            currency = currency,
            masked = masked,
            isAdmin = isAdmin,
            vm = vm,
            onEditEntry = {
                vm.showToast(
                    "Open Rep tab History to edit ${r.customerName} in-place.",
                    com.gullzubair.sarim.ui.viewmodel.ToastKind.INFO
                )
            },
            onOpenCustomerLedger = { /* already on customer ledger */ },
            onBlockedAction = { msg -> blockingAlert = "Action Blocked" to msg },
            onDismiss = { selectedRecord = null }
        )
    }

    blockingAlert?.let { (title, msg) ->
        RepBlockingAlertDialog(title = title, message = msg, onDismiss = { blockingAlert = null })
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
                        vm.deleteRepRecord(rec, InputGuards.formatMoney(rec.totalValue, currency, masked))
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
