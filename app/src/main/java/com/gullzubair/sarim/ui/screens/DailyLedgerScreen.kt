package com.gullzubair.sarim.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
    val channels = snapshot.settings.paymentModes.ifEmpty { listOf("Cash", "Bank", "Easypaisa", "JazzCash") }

    var showComposer by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var entryType by remember { mutableStateOf("IN") } // IN | OUT | TRANSFER
    var expenseScope by remember { mutableStateOf("BUSINESS") } // BUSINESS | HOME | ""
    var channel by remember(channels) { mutableStateOf(channels.first()) }
    var toChannel by remember(channels) { mutableStateOf(channels.getOrElse(1) { channels.first() }) }
    var category by remember { mutableStateOf("General") }
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

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = "DAILY LEDGER",
            onBack = onBack,
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        onClick = {
                            if (showComposer) editingId = null
                            showComposer = !showComposer
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.emerald),
                        modifier = Modifier.testTag("ledger_toggle_composer")
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = "New entry", tint = accents.emerald, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Entry", style = MaterialTheme.typography.labelMedium, color = accents.emerald)
                        }
                    }
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
                        shape = RoundedCornerShape(14.dp),
                        color = accents.inputFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.testTag("ledger_export_pdf_btn")
                    ) {
                        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = accents.emerald, modifier = Modifier.size(18.dp))
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
                AnimatedVisibility(visible = showComposer) {
                    SarimCard {
                        Text(if (editingId == null) "New Cashbook Entry" else "Edit Cashbook Entry", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("IN", "OUT", "TRANSFER").forEach { type ->
                                val sel = entryType == type
                                Surface(
                                    onClick = { entryType = type },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (sel) accents.softPillFill else accents.inputFill,
                                    border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                    modifier = Modifier.weight(1f).testTag("ledger_type_${type.lowercase()}")
                                ) {
                                    Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(type, style = MaterialTheme.typography.labelLarge, color = if (sel) accents.emerald else accents.textMuted)
                                    }
                                }
                            }
                        }
                        if (entryType == "OUT") {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("BUSINESS" to "Business", "HOME" to "Home", "" to "General").forEach { (k, lbl) ->
                                    val sel = expenseScope == k
                                    Surface(
                                        onClick = { expenseScope = k },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (sel) accents.softPillFill else accents.inputFill,
                                        border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                            Text(lbl, style = MaterialTheme.typography.labelMedium, color = if (sel) accents.emerald else accents.textMuted)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        DatePickerField("Entry Date", date, { date = it }, "ledger_date")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PickerField(if (entryType == "TRANSFER") "From" else "Mode", channel, channels, { it }, { channel = it }, "ledger_channel_picker", Modifier.weight(1f))
                            if (entryType == "TRANSFER") {
                                PickerField("To Mode", toChannel, channels, { it }, { toChannel = it }, "ledger_to_channel_picker", Modifier.weight(1f))
                            } else {
                                DecimalField("Amount", amountStr, { amountStr = it }, "ledger_amount_input", Modifier.weight(1f))
                            }
                        }
                        if (entryType == "TRANSFER") {
                            Spacer(Modifier.height(8.dp))
                            DecimalField("Transfer Amount", amountStr, { amountStr = it }, "ledger_amount_input")
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SarimPillInput(category, { category = it.take(60) }, label = "CATEGORY", modifier = Modifier.weight(1f), testTag = "ledger_category_input")
                            SarimPillInput(partyName, { partyName = it.take(80) }, label = "PARTY / LABEL", modifier = Modifier.weight(1f), testTag = "ledger_party_input")
                        }
                        Spacer(Modifier.height(8.dp))
                        SarimPillInput(notes, { notes = it.take(500) }, label = "NOTES", testTag = "ledger_notes_input")
                        Spacer(Modifier.height(12.dp))
                        val amt = amountStr.toDoubleOrNull() ?: 0.0
                        SarimGradientButton(
                            text = if (editingId == null) "Record Cashbook Entry" else "Update Cashbook Entry",
                            onClick = {
                                vm.submitLedgerEntry(
                                    existingId = editingId,
                                    date = date,
                                    type = entryType,
                                    scope = if (entryType == "OUT") expenseScope else "",
                                    mode = channel,
                                    toMode = if (entryType == "TRANSFER") toChannel else "",
                                    category = category,
                                    party = partyName,
                                    amount = amt,
                                    ref = refStr,
                                    note = notes
                                )
                                editingId = null
                                amountStr = ""
                                notes = ""
                                refStr = ""
                                showComposer = false
                            },
                            enabled = amt > 0.0 && (entryType != "TRANSFER" || !channel.equals(toChannel, true)),
                            testTag = "ledger_save_btn"
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
                item { EmptyStateCard("No ledger entries", "Tap + Entry at the top right to record a transaction.") }
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
                                    Text(item.category.ifBlank { item.type }, style = MaterialTheme.typography.titleSmall)
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
                                vm.deleteLedgerEntry(v.id)
                                selectedVoucher = null
                            },
                            modifier = Modifier.weight(0.32f),
                            testTag = "ledger_delete_${v.id}"
                        )
                        Surface(
                            onClick = {
                                editingId = v.id
                                date = v.date
                                entryType = v.type.uppercase()
                                expenseScope = v.scope
                                channel = v.mode.ifBlank { channels.first() }
                                toChannel = v.toMode.ifBlank { channels.getOrElse(1) { channels.first() } }
                                category = v.category
                                partyName = v.party
                                refStr = v.ref
                                amountStr = v.amount.toString()
                                notes = v.note
                                selectedVoucher = null
                                showComposer = true
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
