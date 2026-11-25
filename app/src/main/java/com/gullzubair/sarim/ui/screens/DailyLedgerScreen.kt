package com.gullzubair.sarim.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.MetricKpiCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.PdfExporter
import com.gullzubair.sarim.util.StatementRow

@Composable
fun DailyLedgerScreen(snapshot: WorkspaceSnapshot, todayIso: String, vm: SarimViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney
    val channels = snapshot.settings.paymentChannels.ifEmpty { listOf("Cash", "Bank", "Easypaisa", "JazzCash") }

    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var entryType by remember { mutableStateOf("IN") } // IN | OUT | TRANSFER
    var expenseScope by remember { mutableStateOf("business") }
    var channel by remember(channels) { mutableStateOf(channels.first()) }
    var toChannel by remember(channels) { mutableStateOf(channels.getOrElse(1) { channels.first() }) }
    var category by remember { mutableStateOf("General") }
    var partyName by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    var fromFilter by remember { mutableStateOf(DateUtils.startOfMonthIso()) }
    var toFilter by remember(todayIso) { mutableStateOf(todayIso) }

    val activeEntries = remember(snapshot.ledgerEntries, fromFilter, toFilter) {
        snapshot.ledgerEntries.filter { !it.recycled && DateUtils.isInRange(it.date, fromFilter, toFilter) }
    }
    val totalIn = remember(activeEntries) { activeEntries.filter { it.entryType == "IN" }.sumOf { it.amount } }
    val totalOut = remember(activeEntries) { activeEntries.filter { it.entryType == "OUT" }.sumOf { it.amount } }
    val cashInHand = remember(snapshot) { FinanceEngine.computeAvailableCashInHand(snapshot) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricKpiCard("Cash in Hand", InputGuards.formatMoney(cashInHand, currency, masked), accent = accents.emerald, modifier = Modifier.weight(1f))
                MetricKpiCard("Period IN / OUT", "+${InputGuards.formatNum(totalIn)} / -${InputGuards.formatNum(totalOut)}", accent = accents.cyan, modifier = Modifier.weight(1f))
            }
        }

        item {
            SarimCard {
                Text("New Cashbook Entry", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("IN", "OUT", "TRANSFER").forEach { type ->
                        FilterChip(
                            selected = entryType == type,
                            onClick = { entryType = type },
                            label = { Text(type) },
                            modifier = Modifier.testTag("ledger_type_${type.lowercase()}")
                        )
                    }
                }
                if (entryType == "OUT") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = expenseScope == "business", onClick = { expenseScope = "business" }, label = { Text("Business") })
                        FilterChip(selected = expenseScope == "home", onClick = { expenseScope = "home" }, label = { Text("Home") })
                        FilterChip(selected = expenseScope == "none", onClick = { expenseScope = "none" }, label = { Text("Party/Other") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                DatePickerField("Entry Date", date, { date = it }, "ledger_date")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(if (entryType == "TRANSFER") "From" else "Channel", channel, channels, { it }, { channel = it }, "ledger_channel_picker", Modifier.weight(1f))
                    if (entryType == "TRANSFER") {
                        PickerField("To Channel", toChannel, channels, { it }, { toChannel = it }, "ledger_to_channel_picker", Modifier.weight(1f))
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
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it.take(60) },
                        label = { Text("Category") },
                        modifier = Modifier.weight(1f).testTag("ledger_category_input")
                    )
                    OutlinedTextField(
                        value = partyName,
                        onValueChange = { partyName = it.take(80) },
                        label = { Text("Party / Label") },
                        modifier = Modifier.weight(1f).testTag("ledger_party_input")
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(500) },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth().testTag("ledger_notes_input")
                )
                Spacer(Modifier.height(12.dp))
                val amt = amountStr.toDoubleOrNull() ?: 0.0
                Button(
                    onClick = {
                        vm.submitLedgerEntry(
                            date = date,
                            entryType = entryType,
                            scope = if (entryType == "OUT") expenseScope else "none",
                            channel = channel,
                            toChannel = if (entryType == "TRANSFER") toChannel else "",
                            category = category,
                            party = partyName,
                            amount = amt,
                            notes = notes
                        )
                        amountStr = ""
                        notes = ""
                    },
                    enabled = amt > 0.0 && (entryType != "TRANSFER" || !channel.equals(toChannel, true)),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("ledger_save_btn")
                ) {
                    Text("Record Cashbook Entry")
                }
            }
        }

        item {
            SarimCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Statement (${activeEntries.size})", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        onClick = {
                            var running = 0.0
                            val rows = activeEntries.reversed().map { e ->
                                val delta = when (e.entryType) {
                                    "IN" -> e.amount
                                    "OUT" -> -e.amount
                                    else -> 0.0
                                }
                                running += delta
                                StatementRow(
                                    date = e.date,
                                    description = "${e.entryType} ${e.category} ${e.partyName}".trim(),
                                    debit = if (e.entryType == "OUT") InputGuards.formatNum(e.amount) else "-",
                                    credit = if (e.entryType == "IN") InputGuards.formatNum(e.amount) else "-",
                                    balance = InputGuards.formatNum(running)
                                )
                            }
                            PdfExporter.exportAndShareStatement(
                                context = context,
                                title = "Daily Cashbook Statement",
                                subtitle = "$fromFilter to $toFilter",
                                rows = rows,
                                summaryFooter = "Period IN: ${InputGuards.formatMoney(totalIn, currency, false)}  |  OUT: ${InputGuards.formatMoney(totalOut, currency, false)}"
                            )
                        },
                        modifier = Modifier.testTag("ledger_export_pdf_btn")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Export PDF")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField("From", fromFilter, { fromFilter = it }, "ledger_filter_from", Modifier.weight(1f))
                    DatePickerField("To", toFilter, { toFilter = it }, "ledger_filter_to", Modifier.weight(1f))
                }
            }
        }

        if (activeEntries.isEmpty()) {
            item { EmptyStateCard("No ledger entries in range", "Add cashbook entries above or adjust filter dates.") }
        } else {
            items(activeEntries, key = { it.id }) { item ->
                val color = when (item.entryType) {
                    "IN" -> accents.emerald
                    "OUT" -> accents.danger
                    else -> accents.cyan
                }
                SarimCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${item.entryType} • ${item.partyName.ifBlank { item.category }}", style = MaterialTheme.typography.titleSmall)
                                if (item.tabEntry) {
                                    Surface(color = accents.cyan.copy(alpha = 0.16f), shape = MaterialTheme.shapes.small) {
                                        Text("AUTO", style = MaterialTheme.typography.labelSmall, color = accents.cyan, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            Text(
                                "${item.date} • ${item.channel}${if (!item.toChannel.isNullOrBlank()) " -> ${item.toChannel}" else ""} • ${item.category}",
                                style = MaterialTheme.typography.bodySmall,
                                color = accents.textMuted
                            )
                        }
                        Text(
                            InputGuards.formatMoney(item.amount, currency, masked),
                            style = MaterialTheme.typography.titleSmall,
                            color = color
                        )
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString("${item.date} | ${item.entryType} | ${item.partyName} | ${item.channel} | ${InputGuards.formatMoney(item.amount, currency, false)}"))
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy voucher")
                        }
                        if (!item.tabEntry) {
                            IconButton(onClick = { vm.recycleRecord("ledger_entries", item.id, "${item.entryType} (${item.date})") }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Move to Recycle Bin", tint = accents.danger)
                            }
                        }
                    }
                }
            }
        }
    }
}
