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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimDangerButton
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

/**
 * Port of the web app's `expense-details-screen`: every record under one operating
 * expense name, with a range filter, search, quick add, per-record delete and delete-all.
 * In this app an operating expense is a PaymentTransaction with txType == "expense".
 */
private val EXPENSE_RANGES = listOf(
    "today" to "Today",
    "week" to "This Week",
    "month" to "This Month",
    "year" to "This Year",
    "all" to "All Time"
)

/** Same cutoffs as the web `rangeCutoffDate`: today, 7 days, 1 month, 1 year back. */
private fun expenseRangeCutoffIso(range: String, todayIso: String): String? {
    val today = DateUtils.parseLocalDate(todayIso) ?: return null
    return when (range) {
        "today" -> today.toString()
        "week" -> today.minusDays(7).toString()
        "month" -> today.minusMonths(1).toString()
        "year" -> today.minusYears(1).toString()
        else -> null
    }
}

@Composable
fun ExpenseDetailsScreen(
    snapshot: WorkspaceSnapshot,
    expenseName: String,
    todayIso: String,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney

    var range by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<PaymentTransaction?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val allRecords = remember(snapshot.transactions, expenseName) {
        snapshot.transactions
            .filter { !it.recycled && it.txType == "expense" && it.entityName.equals(expenseName, true) }
            .sortedByDescending { it.date }
    }
    val allTimeTotal = remember(allRecords) { InputGuards.round2(allRecords.sumOf { it.amount }) }

    val ranged = remember(allRecords, range, todayIso) {
        val cutoff = expenseRangeCutoffIso(range, todayIso)
        if (cutoff == null) allRecords else allRecords.filter { it.date.take(10) >= cutoff }
    }
    val visible = remember(ranged, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) ranged else ranged.filter {
            (it.notes ?: "").lowercase().contains(q) ||
                it.date.contains(q) ||
                it.amount.toString().contains(q) ||
                it.paymentChannel.lowercase().contains(q)
        }
    }
    val filteredTotal = remember(ranged) { InputGuards.round2(ranged.sumOf { it.amount }) }
    val availCash = remember(snapshot) { FinanceEngine.computeAvailableCashInHand(snapshot) }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = expenseName.uppercase(),
            subtitle = "${ranged.size} record${if (ranged.size != 1) "s" else ""}",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SarimHalfRingKpiBox(
                        label = "TOTAL",
                        value = InputGuards.formatMoney(filteredTotal, currency, masked),
                        ringColor = accents.gold,
                        modifier = Modifier.weight(1f)
                    )
                    SarimHalfRingKpiBox(
                        label = "ALL-TIME",
                        value = InputGuards.formatMoney(allTimeTotal, currency, masked),
                        ringColor = accents.danger,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                SarimPillInput(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search history...",
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "expense_history_search"
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EXPENSE_RANGES) { (key, label) ->
                        FilterChip(
                            selected = range == key,
                            onClick = { range = key },
                            label = { Text(label) }
                        )
                    }
                }
            }

            item {
                SarimCard {
                    Text(
                        "Quick Add Expense",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(10.dp))
                    SarimPillInput(
                        value = amountStr,
                        onValueChange = { amountStr = it.filter { c -> c.isDigit() || c == '.' }.take(12); formError = null },
                        label = "Amount ($currency)",
                        placeholder = "0.00",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "expense_quick_amount"
                    )
                    Spacer(Modifier.height(8.dp))
                    SarimPillInput(
                        value = description,
                        onValueChange = { description = it.take(120) },
                        label = "Description (optional)",
                        placeholder = "e.g. Monthly rent",
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "expense_quick_description"
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Adds a new expense record under \"$expenseName\" for today's date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = accents.textMuted
                    )
                    formError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = accents.danger)
                    }
                    Spacer(Modifier.height(10.dp))
                    SarimGradientButton(
                        text = "Save Expense",
                        onClick = {
                            val amount = InputGuards.parsePositiveDouble(amountStr)
                            when {
                                amount == null || amount <= 0.0 -> formError = "Please enter a valid amount"
                                amount > availCash + 0.01 ->
                                    formError = "Insufficient cash in hand. Available: ${InputGuards.formatNum(maxOf(0.0, availCash))} — Required: ${InputGuards.formatNum(amount)}"
                                else -> {
                                    vm.submitPayment(
                                        date = todayIso,
                                        txType = "expense",
                                        entity = null,
                                        name = expenseName,
                                        category = "expense",
                                        channel = "Cash",
                                        amount = amount,
                                        addToLedger = true,
                                        notes = InputGuards.sanitizeNotes(description)
                                    )
                                    amountStr = ""
                                    description = ""
                                    formError = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "expense_quick_save"
                    )
                }
            }

            if (visible.isEmpty()) {
                item { EmptyStateCard("No expense records", "No expense records found for the selected period.") }
            } else {
                items(visible, key = { it.id }) { tx ->
                    SarimCard(modifier = Modifier.testTag("expense_row_${tx.id}")) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    tx.notes?.takeIf { it.isNotBlank() } ?: "No description",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "${DateUtils.formatPretty(tx.date)} • ${tx.paymentChannel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                InputGuards.formatMoney(tx.amount, currency, masked),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = accents.gold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        SarimDangerButton(
                            text = "Delete",
                            onClick = { pendingDelete = tx },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "expense_delete_${tx.id}"
                        )
                    }
                }
            }

            if (allRecords.isNotEmpty()) {
                item {
                    SarimDangerButton(
                        text = "Delete All Expenses",
                        onClick = { confirmDeleteAll = true },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "expense_delete_all"
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    pendingDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete expense?") },
            text = {
                Text("${InputGuards.formatNum(tx.amount)} on ${tx.date} will move to the Recycle Bin.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.recycleRecord("transactions", tx.id, "${tx.entityName} (${tx.date})")
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete All \"$expenseName\" Records") },
            text = {
                Text(
                    "Move ALL ${allRecords.size} expense record${if (allRecords.size != 1) "s" else ""} for \"$expenseName\" " +
                        "to the Recycle Bin?\nTotal Amount: ${InputGuards.formatNum(allTimeTotal)}"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    allRecords.forEach { vm.recycleRecord("transactions", it.id, "${it.entityName} (${it.date})") }
                    confirmDeleteAll = false
                    onBack()
                }) { Text("Delete All") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } }
        )
    }
}
