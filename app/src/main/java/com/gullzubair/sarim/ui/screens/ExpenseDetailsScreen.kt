package com.gullzubair.sarim.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.ExpenseRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.EditDiffConfirmDialog
import com.gullzubair.sarim.ui.components.EditHistorySection
import com.gullzubair.sarim.ui.components.EditedBadge
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimDangerButton
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

private val EXPENSE_RANGES = listOf(
    "today" to "Today",
    "week" to "This Week",
    "month" to "This Month",
    "year" to "This Year",
    "all" to "All Time"
)

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
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val masked = session.restrictMoney || snapshot.settings.hideSensitiveMoney
    val isAdmin = session.mode == "admin"

    var range by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ExpenseRecord?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseRecord?>(null) }
    var expandedHistoryIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val allRecords = remember(snapshot.expenses, expenseName) {
        snapshot.expenses
            .filter {
                it.deletedAt == null &&
                    (it.name.equals(expenseName, true) || it.category.equals(expenseName, true))
            }
            .sortedWith(compareByDescending<ExpenseRecord> { it.date }.thenByDescending { it.timestamp })
    }
    val allTimeTotal = remember(allRecords) { InputGuards.round2(allRecords.sumOf { it.amount }) }

    val ranged = remember(allRecords, range, todayIso) {
        val cutoff = expenseRangeCutoffIso(range, todayIso)
        if (cutoff == null) allRecords else allRecords.filter { it.date.take(10) >= cutoff }
    }
    val visible = remember(ranged, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) ranged else ranged.filter {
            it.notes.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.date.contains(q) ||
                it.amount.toString().contains(q) ||
                it.paymentMethod.lowercase().contains(q)
        }
    }
    val filteredTotal = remember(ranged) { InputGuards.round2(ranged.sumOf { it.amount }) }
    val availCash = remember(snapshot) { FinanceEngine.computeAvailableCashInHand(snapshot) }
    val defaultCategory = remember(allRecords, snapshot.settings.expenseCategories) {
        allRecords.firstOrNull()?.category?.ifBlank { null }
            ?: snapshot.settings.expenseCategories.firstOrNull { it.equals(expenseName, true) }
            ?: "Other"
    }

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
                        val selected = range == key
                        Surface(
                            onClick = { range = key },
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) accents.softPillFill else accents.inputFill,
                            border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                                    color = if (selected) accents.emerald else accents.textMuted
                                )
                            }
                        }
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
                                    formError = "Insufficient cash in hand. Available: ${InputGuards.formatMoney(maxOf(0.0, availCash), currency, masked)}"
                                else -> {
                                    vm.submitPaymentOrExpense(
                                        date = todayIso,
                                        kind = "EXPENSE",
                                        entity = null,
                                        name = expenseName,
                                        category = defaultCategory,
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
                items(visible, key = { it.id }) { exp ->
                    val historyOpen = exp.id in expandedHistoryIds
                    val hasHistory = exp.editHistory.isNotEmpty() || exp.timestamp > 0L
                    SarimCard(modifier = Modifier.testTag("expense_row_${exp.id}")) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = hasHistory) {
                                    expandedHistoryIds = if (historyOpen) expandedHistoryIds - exp.id else expandedHistoryIds + exp.id
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        exp.notes.ifBlank { exp.description }.ifBlank { "No description" },
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    EditedBadge(exp.isEdited)
                                }
                                Text(
                                    "${DateUtils.formatPretty(exp.date)} • ${exp.category} • ${exp.paymentMethod}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                InputGuards.formatMoney(exp.amount, currency, masked),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = accents.gold
                            )
                        }
                        AnimatedVisibility(visible = historyOpen) {
                            Column {
                                Spacer(Modifier.height(10.dp))
                                EditHistorySection(
                                    editHistory = exp.editHistory,
                                    createdAtEpoch = exp.timestamp,
                                    addedLabel = "Recorded",
                                    canDelete = isAdmin,
                                    masked = masked,
                                    onDeleteEntry = { idx -> vm.deleteRecordEditHistory("expenses", exp.id, idx) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!exp.isMerged) {
                                Surface(
                                    onClick = { editingExpense = exp },
                                    shape = RoundedCornerShape(20.dp),
                                    color = accents.softPillFill,
                                    border = BorderStroke(1.dp, accents.emerald),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("expense_edit_${exp.id}")
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                                        Text("Edit", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                                    }
                                }
                            }
                            SarimDangerButton(
                                text = "Delete",
                                onClick = { pendingDelete = exp },
                                modifier = Modifier.weight(1f),
                                testTag = "expense_delete_${exp.id}"
                            )
                        }
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

    editingExpense?.let { orig ->
        EditExpenseDialog(
            original = orig,
            categories = snapshot.settings.expenseCategories.ifEmpty { listOf("Fuel", "Wages", "Rent", "Electricity", "Freight", "Maintenance", "Tea & Meals", "Office", "Other") },
            channels = snapshot.settings.paymentModes.ifEmpty { listOf("Cash", "Bank", "Easypaisa", "JazzCash") },
            currency = currency,
            masked = masked,
            onSave = { d, n, cat, ch, amt, nts ->
                vm.editExpenseRecord(orig, d, n, cat, ch, amt, nts) { editingExpense = null }
            },
            onDismiss = { editingExpense = null }
        )
    }

    pendingDelete?.let { exp ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete expense?") },
            text = {
                Text("${InputGuards.formatMoney(exp.amount, currency, masked)} on ${exp.date} will move to the Recycle Bin.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.softDeleteRecord(
                        collection = "expenses",
                        docId = exp.id,
                        displayName = exp.name,
                        displayDetail = "${exp.date} • ${exp.category}",
                        displayAmount = InputGuards.formatMoney(exp.amount, currency, masked),
                        snapshotMap = WorkspaceJsonCodec.expenseToMap(exp)
                    )
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
                        "to the Recycle Bin?\nTotal Amount: ${InputGuards.formatMoney(allTimeTotal, currency, masked)}"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    allRecords.forEach { exp ->
                        vm.softDeleteRecord(
                            collection = "expenses",
                            docId = exp.id,
                            displayName = exp.name,
                            displayDetail = "${exp.date} • ${exp.category}",
                            displayAmount = InputGuards.formatMoney(exp.amount, currency, masked),
                            snapshotMap = WorkspaceJsonCodec.expenseToMap(exp)
                        )
                    }
                    confirmDeleteAll = false
                    onBack()
                }) { Text("Delete All") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EditExpenseDialog(
    original: ExpenseRecord,
    categories: List<String>,
    channels: List<String>,
    currency: String,
    masked: Boolean,
    onSave: (String, String, String, String, Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var date by remember(original) { mutableStateOf(original.date) }
    var name by remember(original) { mutableStateOf(original.name) }
    var category by remember(original) { mutableStateOf(original.category.ifBlank { categories.first() }) }
    var channel by remember(original) { mutableStateOf(original.paymentMethod.ifBlank { channels.first() }) }
    var amountStr by remember(original) { mutableStateOf(InputGuards.fmtNum(original.amount).replace(",", "")) }
    var notes by remember(original) { mutableStateOf(original.notes.ifBlank { original.description }) }
    var confirmDiff by remember { mutableStateOf(false) }

    val amt = amountStr.toDoubleOrNull() ?: 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Expense") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DatePickerField("Date", date, { date = it }, "edit_exp_date")
                SarimPillInput(name, { name = it.take(80) }, label = "Title / Expense", testTag = "edit_exp_name")
                PickerField("Category", category, (categories + category).distinct(), { it }, { category = it }, "edit_exp_cat")
                PickerField("Channel", channel, (channels + channel).distinct(), { it }, { channel = it }, "edit_exp_ch")
                DecimalField("Amount ($currency)", amountStr, { amountStr = it }, "edit_exp_amount")
                SarimPillInput(notes, { notes = it.take(300) }, label = "Notes", testTag = "edit_exp_notes")
            }
        },
        confirmButton = {
            TextButton(
                onClick = { confirmDiff = true },
                enabled = name.isNotBlank() && amt > 0.0,
                modifier = Modifier.testTag("edit_exp_save_btn")
            ) { Text("Review Changes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    if (confirmDiff) {
        EditDiffConfirmDialog(
            title = "Confirm Expense Edit",
            rows = listOf(
                Triple("Date", original.date, date),
                Triple("Name", original.name, name.trim()),
                Triple("Category", original.category, category),
                Triple("Channel", original.paymentMethod, channel),
                Triple("Amount", InputGuards.formatMoney(original.amount, currency, masked), InputGuards.formatMoney(amt, currency, masked)),
                Triple("Notes", original.notes.ifBlank { original.description }, notes.trim())
            ),
            onConfirm = {
                confirmDiff = false
                onSave(date, name, category, channel, amt, notes)
            },
            onDismiss = { confirmDiff = false }
        )
    }
}
