package com.gullzubair.sarim.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.DailyLedgerEntry
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
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.components.SarimYearMonthDayTree
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.PdfExporter
import com.gullzubair.sarim.util.StatementRow
import com.gullzubair.sarim.ui.components.ConfirmRequest
import com.gullzubair.sarim.ui.components.ConfirmRequestHost
import com.gullzubair.sarim.ui.components.deleteConfirmMessage
import com.gullzubair.sarim.ui.components.saveConfirmRequest
import com.gullzubair.sarim.ui.components.DialogTone

@Composable
fun DailyLedgerScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = vm.sessionInfoFor(snapshot.profile).restrictMoney || snapshot.settings.hideSensitiveMoney
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }
    val channels = snapshot.settings.paymentModes.ifEmpty { listOf("Cash", "Bank", "Easypaisa", "JazzCash") }

    var editingId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var entryType by remember { mutableStateOf("IN") } // IN | OUT | TRANSFER
    var channel by remember(channels) { mutableStateOf(channels.first()) }
    var toChannel by remember(channels) { mutableStateOf(channels.getOrElse(1) { channels.first() }) }
    var category by remember { mutableStateOf("") }
    var partyName by remember { mutableStateOf("") }
    var refStr by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedVoucher by remember { mutableStateOf<DailyLedgerEntry?>(null) }

    val allEntries = remember(snapshot.ledgerEntries) {
        snapshot.ledgerEntries.sortedWith(compareByDescending<DailyLedgerEntry> { it.date }.thenByDescending { it.timestamp })
    }
    val summary = remember(snapshot.ledgerEntries, channels) {
        FinanceEngine.summarizeDailyLedger(snapshot.ledgerEntries, channels)
    }
    val channelBalances = summary.byMode
    val categoryTotals = remember(summary.byCategory) {
        summary.byCategory.entries.sortedByDescending { it.value }.take(8)
    }
    val filteredEntries = remember(allEntries, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) allEntries
        else allEntries.filter {
            it.party.contains(q, true) ||
                it.category.contains(q, true) ||
                it.mode.contains(q, true) ||
                it.toMode.contains(q, true) ||
                it.note.contains(q, true) ||
                it.ref.contains(q, true) ||
                it.amount.toString().contains(q)
        }
    }
    val totalIn = remember(filteredEntries) { filteredEntries.filter { it.type.equals("IN", true) }.sumOf { it.amount } }
    val totalOut = remember(filteredEntries) { filteredEntries.filter { it.type.equals("OUT", true) }.sumOf { it.amount } }

    fun resetForm() {
        editingId = null
        category = ""
        partyName = ""
        refStr = ""
        amountStr = ""
        notes = ""
    }

    BackHandler(enabled = editingId != null) {
        resetForm()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = "Daily Ledger",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Hero Card: Cash In Hand + PDF Export + Cash / Bank / Others strip
            item {
                SarimCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "CASH IN HAND",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                            color = accents.textMuted
                        )
                        Surface(
                            onClick = {
                                val rows = filteredEntries.reversed().map { e ->
                                    val afterBal = summary.statement[e.id]?.second ?: 0.0
                                    val isOut = e.type.equals("OUT", true)
                                    val isIn = e.type.equals("IN", true)
                                    StatementRow(
                                        date = e.date,
                                        description = "${e.type} ${e.category} ${e.party}".trim(),
                                        debit = if (isOut) InputGuards.fmtNum(e.amount) else "-",
                                        credit = if (isIn) InputGuards.fmtNum(e.amount) else "-",
                                        balance = InputGuards.fmtNum(afterBal)
                                    )
                                }
                                PdfExporter.exportAndShareStatement(
                                    context = context,
                                    title = "Daily Cashbook Statement",
                                    subtitle = "Gull & Zubair Naswar Dealers",
                                    rows = rows,
                                    summaryFooter = "IN: ${InputGuards.formatMoney(totalIn, currency, masked)}  |  OUT: ${InputGuards.formatMoney(totalOut, currency, masked)}"
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = accents.softPillFill,
                            border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.45f)),
                            modifier = Modifier.size(40.dp).testTag("ledger_export_pdf_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Export Daily Ledger to PDF", tint = accents.emerald, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = InputGuards.formatMoney(summary.inHand, currency, masked),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (summary.inHand >= 0) accents.emerald else accents.danger
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(accents.inputFill)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Cash", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Text(InputGuards.formatMoney(summary.cash, currency, masked), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Bank", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Text(InputGuards.formatMoney(summary.bank, currency, masked), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Others", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Text(InputGuards.formatMoney(summary.others, currency, masked), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // 3-col Summary Strip: Total Cash In | Total Cash Out | Net Balance
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SarimCard(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
                        Text("Total Cash In", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                        Text(InputGuards.formatMoney(summary.totalIn, currency, masked), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                        Text("${summary.inCount} ${if (summary.inCount == 1) "entry" else "entries"}", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                    }
                    SarimCard(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
                        Text("Total Cash Out", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                        Text(InputGuards.formatMoney(summary.totalOut, currency, masked), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.danger)
                        Text("${summary.outCount} ${if (summary.outCount == 1) "entry" else "entries"}", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                    }
                    SarimCard(modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
                        Text("Net Balance", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                        Text(
                            InputGuards.formatMoney(summary.inHand, currency, masked),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (summary.inHand >= 0) accents.emerald else accents.danger
                        )
                        Text(if (summary.inHand >= 0) "Surplus" else "Deficit", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                    }
                }
            }

            // Inline Form Card (always visible, matches web #dl-form-card)
            item {
                SarimCard {
                    val formTitle = when {
                        editingId != null -> "Edit Record"
                        entryType == "TRANSFER" -> "Transfer Between Channels"
                        else -> "Add Entry"
                    }
                    Text(
                        formTitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                        color = accents.textMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("IN" to "+ In", "OUT" to "- Out", "TRANSFER" to "Transfer").forEach { (type, lbl) ->
                            val sel = entryType == type
                            Surface(
                                onClick = { entryType = type },
                                shape = RoundedCornerShape(14.dp),
                                color = if (sel) accents.softPillFill else accents.inputFill,
                                border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp).testTag("ledger_type_${type.lowercase()}")
                            ) {
                                Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(lbl, style = MaterialTheme.typography.labelLarge, color = if (sel) accents.emerald else accents.textMuted)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DatePickerField("Date", date, { date = it }, "ledger_date", Modifier.weight(1f))
                        DecimalField("Amount", amountStr, { amountStr = it }, "ledger_amount_input", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    PickerField(
                        label = if (entryType == "TRANSFER") "From Channel (Source)" else "Payment Channel / Mode",
                        selectedLabel = channel,
                        items = channels,
                        itemLabel = { it },
                        onSelect = { channel = it },
                        testTag = "ledger_channel_picker"
                    )
                    if (entryType == "TRANSFER") {
                        Spacer(Modifier.height(8.dp))
                        PickerField(
                            label = "To Channel (Destination)",
                            selectedLabel = toChannel,
                            items = channels,
                            itemLabel = { it },
                            onSelect = { toChannel = it },
                            testTag = "ledger_to_channel_picker"
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                        SarimPillInput(
                            value = partyName,
                            onValueChange = { partyName = it.take(80) },
                            label = "Party / Customer / Vendor (optional)",
                            placeholder = "e.g. Ali Store",
                            testTag = "ledger_party_input"
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SarimPillInput(
                            value = refStr,
                            onValueChange = { refStr = it.take(40) },
                            label = "Bill / Ref #",
                            placeholder = "Optional",
                            modifier = Modifier.weight(1f),
                            testTag = "ledger_ref_input"
                        )
                        SarimPillInput(
                            value = notes,
                            onValueChange = { notes = it.take(120) },
                            label = "Remarks / Note",
                            placeholder = "Optional",
                            modifier = Modifier.weight(1f),
                            testTag = "ledger_notes_input"
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    val amt = amountStr.toDoubleOrNull() ?: 0.0
                    val resolvedCategory = category.ifBlank {
                        when (entryType) {
                            "OUT" -> "Expense"
                            "TRANSFER" -> "Transfer"
                            else -> "Sales"
                        }
                    }
                    val saveBtnLabel = when {
                        editingId != null -> "Update Entry"
                        entryType == "OUT" -> "Add Cash Out"
                        entryType == "TRANSFER" -> "Record Transfer"
                        else -> "Add Cash In"
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (editingId != null) {
                            Surface(
                                onClick = { resetForm() },
                                shape = RoundedCornerShape(24.dp),
                                color = accents.inputFill,
                                border = BorderStroke(1.dp, accents.borderSubtle),
                                modifier = Modifier.weight(0.55f).height(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("Cancel Edit", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        SarimGradientButton(
                            text = saveBtnLabel,
                            onClick = {
                                val isUpd = editingId != null
                                val details = buildList {
                                    add("Type" to when (entryType) { "IN" -> "Money In"; "OUT" -> "Money Out"; else -> "Transfer" })
                                    add("Date" to DateUtils.formatPretty(date))
                                    add("Amount" to InputGuards.formatMoney(amt, currency, masked))
                                    add("Channel" to if (entryType == "TRANSFER") "$channel \u2192 $toChannel" else channel)
                                    if (partyName.isNotBlank() && entryType != "TRANSFER") add("Party" to partyName.trim())
                                }
                                confirmReq = saveConfirmRequest(
                                    label = "Cashbook Entry",
                                    details = details,
                                    gated = false,
                                    isUpdate = isUpd,
                                    verb = "Record",
                                    tag = "ledger_confirm_save_btn"
                                ) {
                                    vm.submitLedgerEntry(
                                        existingId = editingId,
                                        date = date,
                                        type = entryType,
                                        scope = "",
                                        mode = channel,
                                        toMode = if (entryType == "TRANSFER") toChannel else "",
                                        category = resolvedCategory,
                                        party = if (entryType == "TRANSFER") "" else partyName,
                                        amount = amt,
                                        ref = refStr,
                                        note = notes
                                    )
                                    resetForm()
                                }
                            },
                            enabled = amt > 0.0 && (entryType != "TRANSFER" || !channel.equals(toChannel, true)),
                            testTag = "ledger_save_btn",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Text(
                    "PAYMENT CHANNELS",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = accents.textMuted
                )
                Spacer(Modifier.height(6.dp))
                SarimCard {
                    channelBalances.entries.forEachIndexed { idx, (ch, bal) ->
                        if (idx > 0) Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(ch, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = (if (bal >= 0) "+ " else "- ") + InputGuards.formatMoney(kotlin.math.abs(bal), currency, masked).removePrefix("$currency "),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (bal >= 0) accents.emerald else accents.danger
                            )
                        }
                    }
                }
            }

            if (categoryTotals.isNotEmpty()) {
                item {
                    Text(
                        "CATEGORY SUMMARY",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                        color = accents.textMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    SarimCard {
                        categoryTotals.forEachIndexed { idx, (cat, tot) ->
                            if (idx > 0) Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(cat, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    InputGuards.formatMoney(tot, currency, masked),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "CASHBOOK LOG & HISTORY",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = accents.textMuted
                )
                Spacer(Modifier.height(6.dp))
                SarimCard {
                    SarimPillInput(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search by party, category, mode, amount...",
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = accents.textMuted) },
                        testTag = "ledger_search_input"
                    )
                }
            }

            if (filteredEntries.isEmpty()) {
                item { EmptyStateCard("No ledger entries", "Add your first transaction above.") }
            } else {
                item {
                    SarimYearMonthDayTree(
                        items = filteredEntries,
                        dateOf = { it.date },
                        summaryLabel = { list ->
                            val tIn = list.filter { it.type.equals("IN", true) }.sumOf { it.amount }
                            val tOut = list.filter { it.type.equals("OUT", true) }.sumOf { it.amount }
                            "+${InputGuards.formatMoney(tIn, currency, masked)} / -${InputGuards.formatMoney(tOut, currency, masked)}"
                        },
                        itemRow = { item ->
                            val color = when (item.type.uppercase()) {
                                "IN" -> accents.emerald
                                "OUT" -> accents.danger
                                else -> accents.cyan
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedVoucher = item }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(item.category.ifBlank { item.type }, style = MaterialTheme.typography.titleSmall)
                                        if (item.isTabMirror) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = accents.cyan.copy(alpha = 0.14f),
                                                border = BorderStroke(1.dp, accents.cyan.copy(alpha = 0.35f))
                                            ) {
                                                Text(
                                                    "SYNCED",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                    color = accents.cyan,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        listOfNotNull(
                                            item.party.ifBlank { item.mode },
                                            item.mode,
                                            if (item.scope.isNotBlank()) item.scope else null
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accents.textMuted
                                    )
                                }
                                Text(
                                    (if (item.type.equals("IN", true)) "+ " else if (item.type.equals("OUT", true)) "- " else "") +
                                        InputGuards.formatMoney(item.amount, currency, masked),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = color
                                )
                            }
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    selectedVoucher?.let { v ->
        val stmtPair = summary.statement[v.id]
        val beforeBal = stmtPair?.first ?: 0.0
        val afterBal = stmtPair?.second ?: beforeBal
        val signPrefix = if (v.type.equals("IN", true)) "+ " else if (v.type.equals("OUT", true)) "- " else ""

        SarimDetailBottomSheet(
            title = v.category.ifBlank { "Cashbook Entry" },
            rightTitle = "$signPrefix${InputGuards.formatMoney(v.amount, currency, masked)}",
            subtitle = "${DateUtils.formatPretty(v.date).uppercase()} • ${v.party.ifBlank { "General" }} • ${v.mode}",
            onDismiss = { selectedVoucher = null },
            content = {
                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                VoucherDetailRow("Type", when (v.type.uppercase()) { "IN" -> "Cash In"; "OUT" -> "Cash Out"; else -> "Transfer" })
                VoucherDetailRow("Category", v.category.ifBlank { "General" })
                VoucherDetailRow("Mode", v.mode + if (v.toMode.isNotBlank()) " -> ${v.toMode}" else "")
                VoucherDetailRow("Party / Name", v.party.ifBlank { "-" })
                VoucherDetailRow("Posted to", v.linkCollection ?: "Daily Ledger")
                VoucherDetailRow("Balance", "${InputGuards.formatMoney(beforeBal, currency, masked)} -> ${InputGuards.formatMoney(afterBal, currency, masked)}")
                VoucherDetailRow("Statement effect", "$signPrefix${InputGuards.formatMoney(v.amount, currency, masked)}")
                if (v.note.isNotBlank()) {
                    VoucherDetailRow("Notes", v.note)
                }
            },
            footer = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (v.isTabMirror) {
                        Text(
                            "Managed in ${v.linkCollection ?: "module"} tab",
                            style = MaterialTheme.typography.bodySmall,
                            color = accents.textMuted,
                            modifier = Modifier.weight(0.4f)
                        )
                    } else {
                        SarimDangerButton(
                            text = "Delete",
                            onClick = {
                                confirmReq = ConfirmRequest(
                                    title = "Delete Cashbook Entry?",
                                    message = deleteConfirmMessage(listOf(
                                        "Date" to DateUtils.formatPretty(v.date),
                                        "Amount" to InputGuards.formatMoney(v.amount, currency, masked),
                                        "Party" to v.party
                                    ), recycleBin = false),
                                    confirmText = "Delete",
                                    danger = true,
                                    tag = "ledger_confirm_delete_btn"
                                ) {
                                    vm.deleteLedgerEntry(v.id)
                                    selectedVoucher = null
                                }
                            },
                            modifier = Modifier.weight(0.32f),
                            testTag = "ledger_delete_${v.id}"
                        )
                        Surface(
                            onClick = {
                                editingId = v.id
                                date = v.date
                                entryType = v.type.uppercase()
                                channel = v.mode.ifBlank { channels.first() }
                                toChannel = v.toMode.ifBlank { channels.getOrElse(1) { channels.first() } }
                                category = v.category
                                partyName = v.party
                                refStr = v.ref
                                amountStr = v.amount.toString()
                                notes = v.note
                                selectedVoucher = null
                            },
                            shape = RoundedCornerShape(22.dp),
                            color = accents.softPillFill,
                            border = BorderStroke(1.dp, accents.emerald),
                            modifier = Modifier.height(50.dp).weight(0.28f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Edit", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                            }
                        }
                    }
                    SarimGradientButton(
                        text = "Copy",
                        onClick = {
                            clipboard.setText(AnnotatedString("${v.date} | ${v.type} | ${v.category} | ${v.party} | ${v.mode} | ${InputGuards.formatMoney(v.amount, currency, masked)}"))
                            selectedVoucher = null
                        },
                        modifier = Modifier.weight(if (v.isTabMirror) 0.6f else 0.4f)
                    )
                }
            }
        )
    }
}

@Composable
private fun VoucherDetailRow(label: String, value: String) {
    val accents = LocalSarimAccents.current
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
        HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.45f))
    }
}
