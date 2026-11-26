package com.gullzubair.sarim.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimDangerButton
import com.gullzubair.sarim.ui.components.SarimDetailBottomSheet
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimNoteBanner
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.components.SarimYearMonthDayTree
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.ui.components.ConfirmRequest
import com.gullzubair.sarim.ui.components.ConfirmRequestHost
import com.gullzubair.sarim.ui.components.deleteConfirmMessage
import com.gullzubair.sarim.ui.components.saveConfirmRequest
import com.gullzubair.sarim.ui.components.DialogTone

private fun approvalSubmitterName(item: com.gullzubair.sarim.data.model.PendingApproval): String =
    item.submittedByName.ifBlank { item.submittedByEmail }.ifBlank { "A team member" }

private fun approvalTypeLabel(collection: String): String = when (collection.lowercase()) {
    "sales" -> "Sale"
    "rep" -> "Rep sale"
    "calculator" -> "Calculator"
    "production" -> "Production"
    "factory" -> "Factory"
    "transactions" -> "Payment"
    "customers" -> "Customer"
    "clients" -> "Rep customer"
    "entities" -> "Entity"
    "expenses" -> "Expense"
    "inventory" -> "Material"
    "returns" -> "Return"
    "stores" -> "Store"
    "formulas" -> "Formula"
    else -> collection.replaceFirstChar { it.uppercase() }
}

private fun approvalDateKey(item: com.gullzubair.sarim.data.model.PendingApproval): String {
    val rawDate = item.dataMap["date"]?.toString()?.take(10).orEmpty()
    if (rawDate.length == 10 && rawDate[4] == '-') return rawDate
    if (item.submittedAt > 0L) {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(item.submittedAt))
    }
    return DateUtils.todayIso()
}

@Composable
fun ApprovalsScreen(
    snapshot: WorkspaceSnapshot,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }
    val accents = LocalSarimAccents.current
    val isAdmin = snapshot.profile.isAdmin
    val pendingList = remember(snapshot.pendingApprovals) {
        snapshot.pendingApprovals.filter { it.status == "pending" }
            .sortedByDescending { it.submittedAt }
    }

    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("all") }
    var personFilter by remember { mutableStateOf("all") }

    val typeCounts = remember(pendingList) {
        pendingList.groupBy { it.collection.lowercase() }.mapValues { it.value.size }
    }
    val personCounts = remember(pendingList) {
        pendingList.groupBy { approvalSubmitterName(it) }.mapValues { it.value.size }
    }

    val filtered = remember(pendingList, searchQuery, typeFilter, personFilter) {
        val q = searchQuery.trim().lowercase()
        pendingList.filter { p ->
            val matchType = typeFilter == "all" || p.collection.equals(typeFilter, ignoreCase = true)
            val matchPerson = personFilter == "all" || approvalSubmitterName(p) == personFilter
            val matchQ = q.isBlank() ||
                p.summaryTitle.lowercase().contains(q) ||
                p.summaryBody.lowercase().contains(q) ||
                approvalSubmitterName(p).lowercase().contains(q) ||
                p.collection.lowercase().contains(q) ||
                p.dataMap.values.joinToString(" ").lowercase().contains(q)
            matchType && matchPerson && matchQ
        }
    }

    val dayGroups = remember(filtered) {
        filtered.groupBy { approvalDateKey(it) }
            .entries
            .sortedByDescending { it.key }
            .map { it.key to it.value }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = "Approvals",
            onBack = onBack,
            trailingContent = if (isAdmin && pendingList.isNotEmpty()) {
                {
                    Surface(
                        onClick = {
                            confirmReq = ConfirmRequest(
                                title = "Approve All?",
                                message = "Requests: ${pendingList.size}\n\nEvery pending request will be approved and become part of the shared records.",
                                confirmText = "Approve All",
                                tone = DialogTone.Warning,
                                tag = "approvals_confirm_all_btn"
                            ) { pendingList.forEach { vm.reviewPending(it, true) } }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.emerald),
                        modifier = Modifier.testTag("approvals_approve_all_btn")
                    ) {
                        Text("Approve All", style = MaterialTheme.typography.labelLarge, color = accents.emerald, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
            } else null
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Web Hero Card (apv-hero)
            item {
                val people = remember(pendingList) { pendingList.map { approvalSubmitterName(it) }.distinct() }
                val heroSubtitle = if (pendingList.isEmpty()) {
                    "Nothing needs your approval right now."
                } else {
                    val who = if (people.size <= 2) people.joinToString(" and ") else "${people.take(2).joinToString(", ")} and ${people.size - 2} more"
                    "From $who"
                }
                SarimCard(accentBorder = if (pendingList.isEmpty()) accents.emerald else accents.gold) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (pendingList.isEmpty()) accents.softPillFill else accents.gold.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (pendingList.isEmpty()) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(26.dp))
                            } else {
                                Text(
                                    text = "${pendingList.size}",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = accents.gold
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (pendingList.isEmpty()) "All caught up" else "waiting for approval",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(heroSubtitle, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                    }
                }
            }

            // Search bar when > 1 pending item (matches web)
            if (pendingList.size > 1) {
                item {
                    SarimPillInput(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search by name, item or amount",
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = accents.textMuted) },
                        testTag = "approvals_search_input"
                    )
                }
            }

            // Type Filter Pills
            if (typeCounts.size > 1) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val allSelected = typeFilter == "all"
                        Surface(
                            onClick = { typeFilter = "all" },
                            shape = RoundedCornerShape(999.dp),
                            color = if (allSelected) accents.softPillFill else accents.inputFill,
                            border = BorderStroke(1.dp, if (allSelected) accents.emerald else accents.borderSubtle),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text(
                                    "All ${pendingList.size}",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium),
                                    color = if (allSelected) accents.emerald else accents.textMuted
                                )
                            }
                        }
                        typeCounts.forEach { (col, cnt) ->
                            val selected = typeFilter == col
                            Surface(
                                onClick = { typeFilter = col },
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) accents.softPillFill else accents.inputFill,
                                border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                    Text(
                                        "${approvalTypeLabel(col)} $cnt",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                                        color = if (selected) accents.emerald else accents.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Person Filter Pills
            if (personCounts.size > 1) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        personCounts.forEach { (person, cnt) ->
                            val selected = personFilter == person
                            Surface(
                                onClick = { personFilter = if (selected) "all" else person },
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) accents.softPillFill else accents.inputFill,
                                border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                    Text(
                                        "$person $cnt",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                                        color = if (selected) accents.emerald else accents.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (dayGroups.isEmpty()) {
                item {
                    if (pendingList.isNotEmpty()) {
                        EmptyStateCard(
                            title = "No matching approvals",
                            detail = "Try clearing the search or filter chips."
                        )
                    }
                }
            } else {
                items(dayGroups, key = { it.first }) { (dayKey, dayItems) ->
                    val peopleNames = dayItems.map { approvalSubmitterName(it) }.distinct().joinToString(", ")
                    SarimCard(accentBorder = accents.gold.copy(alpha = 0.65f)) {
                        Text(
                            text = DateUtils.formatPretty(dayKey).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                            color = accents.textMuted
                        )
                        Spacer(Modifier.height(8.dp))
                        dayItems.forEachIndexed { idx, item ->
                            if (idx > 0) {
                                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.45f), modifier = Modifier.padding(vertical = 8.dp))
                            }
                            Column {
                                Text(item.summaryTitle.ifBlank { "${item.collection.uppercase()} ${item.action.uppercase()}" }, style = MaterialTheme.typography.titleMedium)
                                if (item.summaryBody.isNotBlank()) {
                                    Text(item.summaryBody, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Submitted by ${approvalSubmitterName(item)} • ${approvalTypeLabel(item.collection)} • ${item.action.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accents.cyan
                                )
                                if (isAdmin) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        SarimGradientButton(
                                            text = "Approve",
                                            onClick = {
                                                confirmReq = ConfirmRequest(
                                                    title = "Approve Request?",
                                                    message = "Request: ${item.summaryTitle.ifBlank { item.collection.uppercase() }}\nSubmitted by: ${approvalSubmitterName(item)}\n\nIt will become part of the shared records.",
                                                    confirmText = "Approve",
                                                    tone = DialogTone.Primary,
                                                    tag = "approvals_confirm_approve_btn"
                                                ) { vm.reviewPending(item, true) }
                                            },
                                            modifier = Modifier.weight(1f),
                                            testTag = "approve_${item.id}"
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                confirmReq = ConfirmRequest(
                                                    title = "Discard Request?",
                                                    message = "Request: ${item.summaryTitle.ifBlank { item.collection.uppercase() }}\nSubmitted by: ${approvalSubmitterName(item)}\n\nThe submitter will see it as discarded.",
                                                    confirmText = "Discard",
                                                    danger = true,
                                                    tag = "approvals_confirm_reject_btn"
                                                ) { vm.reviewPending(item, false) }
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accents.danger),
                                            modifier = Modifier.weight(1f).height(50.dp).testTag("reject_${item.id}")
                                        ) {
                                            Icon(Icons.Rounded.Close, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Discard")
                                        }
                                    }
                                }
                            }
                        }

                        if (isAdmin && dayItems.size > 1) {
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.45f))
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${dayItems.size} waiting • $peopleNames",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accents.textMuted,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        onClick = {
                                            confirmReq = ConfirmRequest(
                                                title = "Discard ${dayItems.size} requests?",
                                                message = "Discard all requests for ${DateUtils.formatPretty(dayKey)}?",
                                                confirmText = "Discard",
                                                danger = true
                                            ) { dayItems.forEach { vm.reviewPending(it, false) } }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = accents.danger.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.4f)),
                                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                            Text("Discard", style = MaterialTheme.typography.labelMedium, color = accents.danger)
                                        }
                                    }
                                    Surface(
                                        onClick = {
                                            confirmReq = ConfirmRequest(
                                                title = "Approve ${dayItems.size} requests?",
                                                message = "Approve all requests for ${DateUtils.formatPretty(dayKey)}?",
                                                confirmText = "Approve",
                                                tone = DialogTone.Primary
                                            ) { dayItems.forEach { vm.reviewPending(it, true) } }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = accents.softPillFill,
                                        border = BorderStroke(1.dp, accents.emerald),
                                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                            Text("Approve", style = MaterialTheme.typography.labelMedium, color = accents.emerald)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun StockTransferScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }
    val accents = LocalSarimAccents.current
    val activeStores = remember(snapshot.stores) { snapshot.stores.filter { it.deletedAt == null && it.active } }
    val stocks = remember(snapshot) { FinanceEngine.computeStoreStockSnapshots(snapshot) }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var fromStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var toStore by remember(activeStores) { mutableStateOf(activeStores.getOrElse(1) { activeStores.firstOrNull() }) }
    var qtyStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedTransfer by remember { mutableStateOf<ProductionRecord?>(null) }

    val availableAtSource = remember(fromStore, stocks) {
        stocks.firstOrNull { it.storeKey.equals(fromStore?.key ?: "", true) }?.availableKg ?: 0.0
    }
    val transferOutRecords = remember(snapshot.production) {
        snapshot.production.filter { it.deletedAt == null && it.isTransfer && it.transferDirection == "out" }
            .sortedWith(compareByDescending<ProductionRecord> { it.date }.thenByDescending { it.timestamp })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "STOCK TRANSFER", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                SarimCard {
                    Text(
                        "MOVE STOCK BETWEEN STORES",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PickerField(
                            label = "From Store",
                            selectedLabel = fromStore?.name ?: "Select",
                            items = activeStores,
                            itemLabel = { it.name },
                            onSelect = { fromStore = it },
                            testTag = "st_from_picker",
                            modifier = Modifier.weight(1f)
                        )
                        PickerField(
                            label = "To Store",
                            selectedLabel = toStore?.name ?: "Select",
                            items = activeStores,
                            itemLabel = { it.name },
                            onSelect = { toStore = it },
                            testTag = "st_to_picker",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DatePickerField("Date", date, { date = it }, "st_date", Modifier.weight(1f))
                        DecimalField("Quantity (kg)", qtyStr, { qtyStr = it }, "st_qty_input", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    SarimPillInput(
                        value = notes,
                        onValueChange = { notes = it.take(500) },
                        label = "NOTE (OPTIONAL)",
                        placeholder = "e.g. Balancing stock ahead of weekend rush",
                        testTag = "st_notes_input"
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${InputGuards.formatKg(availableAtSource)} available at ${fromStore?.name ?: "store"} on $date",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (availableAtSource <= 0.0) accents.danger else accents.emerald
                    )
                    Spacer(Modifier.height(12.dp))
                    val qty = qtyStr.toDoubleOrNull() ?: 0.0
                    SarimGradientButton(
                        text = "Transfer Stock",
                        onClick = {
                            val src = fromStore
                            val dst = toStore
                            if (src != null && dst != null) {
                                confirmReq = saveConfirmRequest(
                                    label = "Stock Transfer",
                                    details = listOf(
                                        "From" to src.name,
                                        "To" to dst.name,
                                        "Quantity" to InputGuards.formatKg(qty),
                                        "Date" to DateUtils.formatPretty(date)
                                    ) + (if (notes.isNotBlank()) listOf("Note" to notes.trim()) else emptyList()),
                                    gated = !snapshot.profile.isAdmin,
                                    verb = "Transfer",
                                    tag = "st_confirm_save_btn"
                                ) {
                                    vm.submitStockTransfer(date, src, dst, qty, notes)
                                    qtyStr = ""
                                    notes = ""
                                }
                            }
                        },
                        enabled = fromStore != null && toStore != null && !fromStore!!.key.equals(toStore!!.key, true) && qty > 0.0,
                        testTag = "st_save_btn"
                    )
                }
            }

            item {
                Text(
                    "TRANSFER HISTORY",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = accents.textMuted
                )
            }

            if (transferOutRecords.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("No stock transfers recorded yet.", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    }
                }
            } else {
                item {
                    SarimYearMonthDayTree(
                        items = transferOutRecords,
                        dateOf = { it.date },
                        summaryLabel = { list -> InputGuards.formatKg(list.sumOf { kotlin.math.abs(it.net) }) },
                        daySummaryLabel = { list -> InputGuards.formatKg(list.sumOf { kotlin.math.abs(it.net) }) },
                        itemRow = { rec ->
                            val fromLbl = FormulaEngine.storeLabelFor(rec.store, snapshot.stores)
                            val toLbl = FormulaEngine.storeLabelFor(rec.transferPeerStore, snapshot.stores)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedTransfer = rec }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("$fromLbl -> $toLbl", style = MaterialTheme.typography.titleSmall)
                                    Text("TRANSFER", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                }
                                Text(
                                    InputGuards.formatKg(kotlin.math.abs(rec.net)),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    )
                }
            }

            item {
                SarimNoteBanner("Note: A transfer is only allowed up to the stock currently available at the source store. Transfers are recorded against both stores' stock history and sync across devices.")
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    selectedTransfer?.let { tr ->
        val absQty = kotlin.math.abs(tr.net)
        val fromLbl = FormulaEngine.storeLabelFor(tr.store, snapshot.stores)
        val toLbl = FormulaEngine.storeLabelFor(tr.transferPeerStore, snapshot.stores)
        val noteStr = tr.transferNote.ifBlank { tr.returnNote }
        SarimDetailBottomSheet(
            title = "$fromLbl -> $toLbl",
            rightTitle = InputGuards.formatKg(absQty),
            subtitle = DateUtils.formatPretty(tr.date).uppercase(),
            onDismiss = { selectedTransfer = null },
            content = {
                if (noteStr.isNotBlank()) {
                    Text(noteStr, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                }
            },
            footer = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SarimDangerButton(
                        text = "Delete",
                        onClick = {
                            confirmReq = ConfirmRequest(
                                title = "Delete Transfer?",
                                message = deleteConfirmMessage(listOf(
                                    "From" to fromLbl,
                                    "To" to toLbl,
                                    "Date" to DateUtils.formatPretty(tr.date),
                                    "Note" to "Both sides of the transfer are removed"
                                )),
                                confirmText = "Delete",
                                danger = true,
                                tag = "st_confirm_delete_btn"
                            ) {
                                vm.recyclePair("production", tr.transferPairId, tr.id, "Transfer $fromLbl -> $toLbl")
                                selectedTransfer = null
                            }
                        },
                        modifier = Modifier.weight(0.38f)
                    )
                    SarimGradientButton(
                        text = "Edit",
                        onClick = {
                            fromStore = activeStores.firstOrNull { it.key.equals(tr.store, true) || it.name.equals(tr.store, true) } ?: fromStore
                            toStore = activeStores.firstOrNull { it.key.equals(tr.transferPeerStore, true) || it.name.equals(tr.transferPeerStore, true) } ?: toStore
                            date = tr.date
                            qtyStr = absQty.toString()
                            notes = noteStr
                            selectedTransfer = null
                        },
                        modifier = Modifier.weight(0.62f)
                    )
                }
            }
        )
    }
}

@Composable
fun PaymentTransferScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = vm.sessionInfoFor(snapshot.profile).restrictMoney || snapshot.settings.hideSensitiveMoney
    val activeEntities = remember(snapshot.entities) {
        snapshot.entities.filter { it.deletedAt == null && !it.isExpenseEntity && it.type != "expense" }
    }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var fromEntity by remember(activeEntities) { mutableStateOf(activeEntities.firstOrNull()) }
    var toEntity by remember(activeEntities) { mutableStateOf(activeEntities.getOrElse(1) { activeEntities.firstOrNull() }) }
    var amountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var attachedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedTransfer by remember { mutableStateOf<PaymentTransaction?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            attachedUri = uri
            if (notes.isBlank()) notes = "Voucher photo attached"
        }
    }

    val transferHistory = remember(snapshot.transactions) {
        snapshot.transactions.filter { it.deletedAt == null && it.isTransfer && it.transferDirection == "out" }
            .sortedWith(compareByDescending<PaymentTransaction> { it.date }.thenByDescending { it.timestamp })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "PAYMENT TRANSFER", onBack = onBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                SarimCard {
                    Text(
                        "MOVE BALANCE BETWEEN ENTITIES",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PickerField("From Entity", fromEntity?.name ?: "Search entity...", activeEntities, { it.name }, { fromEntity = it }, "pt_from_picker", Modifier.weight(1f))
                        PickerField("To Entity", toEntity?.name ?: "Search entity...", activeEntities, { it.name }, { toEntity = it }, "pt_to_picker", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DatePickerField("Date", date, { date = it }, "pt_date", Modifier.weight(1f))
                        DecimalField("Amount", amountStr, { amountStr = it }, "pt_amount_input", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "NOTE (OPTIONAL)",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                        color = accents.textMuted,
                        modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SarimPillInput(
                            value = notes,
                            onValueChange = { notes = it.take(500) },
                            placeholder = "e.g. Settling shared supplier bill",
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            shape = CircleShape,
                            color = if (attachedUri != null) accents.softPillFill else accents.inputFill,
                            border = BorderStroke(1.dp, if (attachedUri != null) accents.emerald else accents.emerald.copy(alpha = 0.45f)),
                            modifier = Modifier.size(48.dp).testTag("pt_photo_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PhotoCamera, contentDescription = "Attach voucher photo", tint = accents.emerald, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    SarimGradientButton(
                        text = "Transfer Payment",
                        onClick = {
                            val src = fromEntity
                            val dst = toEntity
                            if (src != null && dst != null) {
                                confirmReq = saveConfirmRequest(
                                    label = "Payment Transfer",
                                    details = listOf(
                                        "From" to src.name,
                                        "To" to dst.name,
                                        "Amount" to InputGuards.formatMoney(amt, currency, masked),
                                        "Date" to DateUtils.formatPretty(date)
                                    ) + (if (notes.isNotBlank()) listOf("Note" to notes.trim()) else emptyList()),
                                    gated = !snapshot.profile.isAdmin,
                                    verb = "Transfer",
                                    tag = "pt_confirm_save_btn"
                                ) {
                                    vm.submitPaymentTransfer(date, src, dst, amt, notes)
                                    amountStr = ""
                                    notes = ""
                                    attachedUri = null
                                }
                            }
                        },
                        enabled = fromEntity != null && toEntity != null && fromEntity!!.id != toEntity!!.id && amt > 0.0,
                        testTag = "pt_save_btn"
                    )
                }
            }

            item {
                Text(
                    "TRANSFER HISTORY",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = accents.textMuted
                )
            }

            if (transferHistory.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("No payment transfers recorded yet.", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    }
                }
            } else {
                item {
                    SarimYearMonthDayTree(
                        items = transferHistory,
                        dateOf = { it.date },
                        summaryLabel = { list -> InputGuards.formatMoney(list.sumOf { it.amount }, currency, masked) },
                        daySummaryLabel = { list -> InputGuards.formatMoney(list.sumOf { it.amount }, currency, masked) },
                        itemRow = { tx ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedTransfer = tx }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${tx.entityName} -> ${tx.peerEntityName}", style = MaterialTheme.typography.titleSmall)
                                    Text("TRANSFER", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                }
                                Text(InputGuards.formatMoney(tx.amount, currency, masked), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    )
                }
            }

            item {
                SarimNoteBanner("Note: A payment transfer records an OUT transaction against the source entity and an IN transaction against the destination entity. Both entities' balances update immediately and sync across devices.")
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    selectedTransfer?.let { tx ->
        SarimDetailBottomSheet(
            title = "${tx.entityName} -> ${tx.peerEntityName}",
            rightTitle = InputGuards.formatMoney(tx.amount, currency, masked),
            subtitle = DateUtils.formatPretty(tx.date).uppercase(),
            onDismiss = { selectedTransfer = null },
            content = {
                if (tx.description.isNotBlank()) {
                    Text(tx.description, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                }
            },
            footer = {
                SarimDangerButton(
                    text = "Delete Transfer Pair",
                    onClick = {
                        confirmReq = ConfirmRequest(
                            title = "Delete Transfer Pair?",
                            message = deleteConfirmMessage(listOf(
                                "From" to tx.entityName,
                                "To" to tx.peerEntityName,
                                "Date" to DateUtils.formatPretty(tx.date),
                                "Amount" to InputGuards.formatMoney(tx.amount, currency, masked),
                                "Note" to "Both sides of the transfer are removed"
                            )),
                            confirmText = "Delete",
                            danger = true,
                            tag = "pt_confirm_delete_btn"
                        ) {
                            vm.recyclePair("transactions", tx.transferPairId, tx.id, "Transfer ${tx.entityName} -> ${tx.peerEntityName}")
                            selectedTransfer = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}
