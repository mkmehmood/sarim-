package com.gullzubair.sarim.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.CalculatorRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import java.time.YearMonth
import kotlin.math.abs

@Composable
fun CalculatorPerformanceChartCard(
    history: List<CalculatorRecord>,
    seller: String,
    referenceDateIso: String,
    currency: String,
    masked: Boolean
) {
    val accents = LocalSarimAccents.current
    var mode by remember { mutableStateOf("week") } // week | month | year | all
    var metric by remember { mutableStateOf("weight") } // weight | value | cost | profit | cash | credit

    val filtered = remember(history, seller) {
        if (seller.equals("COMBINED", true) || seller.isBlank()) history
        else history.filter { it.seller.equals(seller, ignoreCase = true) }
    }

    fun metricOf(c: CalculatorRecord): Double = when (metric) {
        "weight" -> (c.totalSold - c.returned).coerceAtLeast(0.0)
        "value" -> c.revenue
        "cost" -> c.totalCost
        "profit" -> c.profit
        "cash" -> c.cashQty * c.unitPrice
        "credit" -> c.creditValue
        else -> c.revenue
    }

    fun fmtMonthLabel(ym: String): String =
        runCatching {
            val y = YearMonth.parse(ym.take(7))
            y.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " " + (y.year % 100).toString().padStart(2, '0')
        }.getOrDefault(ym)

    val series: List<Pair<String, Double>> = remember(filtered, mode, metric, referenceDateIso) {
        val ref = DateUtils.parseLocalDate(referenceDateIso) ?: java.time.LocalDate.now()
        when (mode) {
            "week" -> (6 downTo 0).map { offset ->
                val d = ref.minusDays(offset.toLong())
                val iso = d.toString()
                val label = d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                label to filtered.filter { it.date == iso }.sumOf(::metricOf)
            }
            "month" -> {
                val ym = YearMonth.from(ref)
                val chunks = listOf(1..7, 8..14, 15..21, 22..ym.lengthOfMonth())
                chunks.mapIndexed { idx, range ->
                    val prefix = ym.toString()
                    val v = filtered.filter { it.date.startsWith(prefix) && (it.date.takeLast(2).toIntOrNull() ?: 0) in range }.sumOf(::metricOf)
                    "W${idx + 1}" to v
                }
            }
            "year" -> (1..12).map { m ->
                val prefix = "${ref.year}-${m.toString().padStart(2, '0')}"
                fmtMonthLabel(prefix) to filtered.filter { it.date.startsWith(prefix) }.sumOf(::metricOf)
            }
            else -> filtered.groupBy { it.date.take(7) }
                .entries.sortedBy { it.key }.takeLast(12)
                .map { (ym, rows) -> fmtMonthLabel(ym) to rows.sumOf(::metricOf) }
        }
    }

    val metricOptions = listOf(
        "weight" to "Weight (kg)",
        "value" to "Total Value",
        "cost" to "Total Cost",
        "profit" to "Net Profit",
        "cash" to "Cash Sales",
        "credit" to "Credit Sales"
    )
    val selectedMetricLabel = metricOptions.firstOrNull { it.first == metric }?.second ?: "Weight (kg)"

    SarimCard {
        Text(
            "Performance Analytics for ${if (seller.equals("COMBINED", true) || seller.isBlank()) "Combined" else seller}",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("week" to "Weekly", "month" to "Monthly", "year" to "Yearly", "all" to "All Times").forEach { (k, lbl) ->
                val sel = mode == k
                Surface(
                    onClick = { mode = k },
                    shape = RoundedCornerShape(12.dp),
                    color = if (sel) accents.softPillFill else accents.inputFill,
                    border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                    modifier = Modifier.weight(1f).testTag("calc_ind_mode_$k")
                ) {
                    Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                        Text(lbl, style = MaterialTheme.typography.labelSmall, color = if (sel) accents.emerald else accents.textMuted, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PickerField(
            label = "Select Metric:",
            selectedLabel = selectedMetricLabel,
            options = metricOptions,
            onSelect = { metric = it },
            testTag = "calc_ind_metric_picker"
        )
        Spacer(Modifier.height(10.dp))
        SparklineChart(
            title = selectedMetricLabel,
            series = series,
            lineColor = when (metric) {
                "cost" -> accents.danger
                "credit" -> accents.gold
                else -> accents.emerald
            },
            currency = if (metric == "weight") "kg" else currency,
            masked = masked && metric != "weight"
        )
    }
}

@Composable
fun CalculatorItemListDialog(
    title: String,
    eyebrow: String,
    caption: String,
    placeholder: String,
    totalLabel: String,
    categories: List<String>,
    items: List<Pair<String, String>>,
    currency: String,
    masked: Boolean,
    onAddItem: (String, String) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    var nameInput by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val total = items.sumOf { it.second.toDoubleOrNull() ?: 0.0 }
    val q = nameInput.trim().lowercase()
    val suggestions = remember(categories, items, q) {
        (categories + items.map { it.first })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .filter { q.isEmpty() || it.lowercase().contains(q) }
            .take(8)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = accents.emerald)
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                SarimPillInput(
                    value = nameInput,
                    onValueChange = { nameInput = it.take(60); errorMsg = null },
                    label = "CATEGORY",
                    placeholder = placeholder,
                    testTag = "calc_popup_cat_input"
                )
                if (suggestions.isNotEmpty() && !suggestions.any { it.equals(nameInput.trim(), true) }) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accents.inputFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            suggestions.take(4).forEach { sug ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { nameInput = sug }.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(sug, style = MaterialTheme.typography.bodySmall)
                                    Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                }
                            }
                        }
                    }
                }
                DecimalField("Amount", amountInput, { amountInput = it; errorMsg = null }, "calc_popup_amt_input")
                errorMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = accents.danger) }
                if (items.isEmpty()) {
                    Text("Nothing added yet.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(items) { idx, pair ->
                            val amt = pair.second.toDoubleOrNull() ?: 0.0
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pair.first, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Text(InputGuards.formatMoney(amt, currency, masked), style = MaterialTheme.typography.titleSmall, color = accents.danger)
                                IconButton(onClick = { onRemoveAt(idx) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove ${pair.first}", tint = accents.textMuted, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = accents.borderSubtle)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(totalLabel, style = MaterialTheme.typography.titleSmall)
                        Text(InputGuards.formatMoney(total, currency, masked), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val nm = nameInput.trim()
                val v = amountInput.toDoubleOrNull() ?: 0.0
                when {
                    nm.isEmpty() -> errorMsg = "Search and select a category, or type a new one."
                    v <= 0.0 -> errorMsg = "Enter an amount greater than zero."
                    else -> {
                        onAddItem(nm, amountInput.trim())
                        nameInput = ""
                        amountInput = ""
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun CalculatorDetailBottomSheet(
    record: CalculatorRecord,
    snapshot: WorkspaceSnapshot,
    currency: String,
    masked: Boolean,
    vm: SarimViewModel,
    onDismiss: () -> Unit,
    onEditClick: (CalculatorRecord) -> Unit
) {
    val accents = LocalSarimAccents.current
    var showQuantities by remember(record.id) { mutableStateOf(false) }
    var showCreditCash by remember(record.id) { mutableStateOf(false) }
    var showCommissions by remember(record.id) { mutableStateOf(false) }
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }
    val variance = InputGuards.round2(record.received - record.totalExpected)
    val profit = InputGuards.round2(record.revenue - record.commissionPaid - record.fieldExpenses)
    val storeLabel = FormulaEngine.storeLabelFor(record.returnStore, snapshot.stores)

    SarimDetailBottomSheet(
        title = DateUtils.formatPretty(record.date).uppercase(),
        rightTitle = InputGuards.formatMoney(record.revenue, currency, masked),
        subtitle = buildString {
            append(record.seller.uppercase())
            if (record.dateFrom.isNotBlank() && record.dateFrom != record.date) {
                append(" • ${DateUtils.formatPretty(record.dateFrom)} → ${DateUtils.formatPretty(record.date)}")
            }
        },
        onDismiss = onDismiss,
        content = {
            if (record.isEdited) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    EditedBadge(true)
                }
                Spacer(Modifier.height(4.dp))
            }
            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
            Spacer(Modifier.height(8.dp))
            CalcKvRow("Total sold", InputGuards.formatKg(record.totalSold))
            CalcKvRow("Profit", InputGuards.formatMoney(profit, currency, masked))
            CalcKvRow("Expected cash", InputGuards.formatMoney(record.totalExpected, currency, masked))
            CalcKvRow("Received cash", InputGuards.formatMoney(record.received, currency, masked))
            CalcKvRow("Status", record.statusText.ifBlank { if (abs(variance) <= 1.0) "Balanced" else InputGuards.formatMoney(variance, currency, masked) })
            if (storeLabel.isNotBlank()) CalcKvRow("Return store", storeLabel)
            if (record.notes.isNotBlank()) CalcKvRow("Notes", record.notes)
            Spacer(Modifier.height(8.dp))
            CalcAccordion("QUANTITIES", showQuantities, { showQuantities = !showQuantities }) {
                CalcKvRow("Returned", InputGuards.formatKg(record.returned), accents.cyan)
                CalcKvRow("Expired (to CHORA)", InputGuards.formatKg(record.expired), accents.danger)
                CalcKvRow("Shared deduction", InputGuards.formatKg(record.shared), accents.danger)
                CalcKvRow("Cash qty", InputGuards.formatKg(record.cashQty), accents.cyan)
                CalcKvRow("Credit qty", InputGuards.formatKg(record.creditQty), accents.cyan)
            }
            Spacer(Modifier.height(6.dp))
            CalcAccordion("CREDIT AND CASH", showCreditCash, { showCreditCash = !showCreditCash }) {
                CalcKvRow("Credit out", InputGuards.formatMoney(record.creditValue, currency, masked), accents.danger)
                CalcKvRow("Prev collection", InputGuards.formatMoney(record.prevColl, currency, masked), accents.emerald)
                CalcKvRow("Field expenses", InputGuards.formatMoney(record.fieldExpenses, currency, masked), accents.danger)
                CalcKvRow("Commission paid", InputGuards.formatMoney(record.commissionPaid, currency, masked), accents.emerald)
            }
            Spacer(Modifier.height(6.dp))
            CalcAccordion("COMMISSION BREAKDOWN", showCommissions, { showCommissions = !showCommissions }) {
                if (record.commissionPerUnit > 0.0) {
                    CalcKvRow("Commission / unit", InputGuards.formatMoney(record.commissionPerUnit, currency, masked))
                    CalcKvRow("Gross commission", InputGuards.formatMoney(record.grossCommission, currency, masked), accents.cyan)
                    CalcKvRow("Commission payable", InputGuards.formatMoney(record.commissionPayable, currency, masked), accents.gold)
                }
                if (record.commissionPaidItems.isEmpty()) {
                    CalcKvRow("Total Commission Paid", InputGuards.formatMoney(record.commissionPaid, currency, masked), accents.danger)
                } else {
                    record.commissionPaidItems.forEach { m ->
                        val lbl = m["label"]?.toString() ?: "Commission"
                        val amt = (m["amount"] as? Number)?.toDouble() ?: 0.0
                        CalcKvRow(lbl, InputGuards.formatMoney(amt, currency, masked), accents.danger)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            EditHistorySection(
                editHistory = record.editHistory,
                createdAtEpoch = record.timestamp,
                addedLabel = "Added in Calculator",
                canDelete = snapshot.profile.isAdmin,
                masked = masked,
                onDeleteEntry = { idx -> vm.deleteRecordEditHistory("calculator", record.id, idx) }
            )
        },
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SarimDangerButton(
                    text = "Delete",
                    onClick = {
                        confirmReq = ConfirmRequest(
                            title = "Delete ${record.seller.ifBlank { "Sales" }} Record?",
                            message = deleteConfirmMessage(listOf(
                                "Seller" to record.seller,
                                "Date" to DateUtils.formatPretty(record.date),
                                "Cash Received" to InputGuards.formatMoney(record.received, currency, masked)
                            )),
                            confirmText = "Delete",
                            danger = true,
                            tag = "calc_confirm_delete_btn"
                        ) {
                            vm.softDeleteRecord(
                                collection = "calculator",
                                docId = record.id,
                                displayName = record.seller,
                                displayDetail = record.date,
                                displayAmount = InputGuards.formatMoney(record.received, currency, masked),
                                snapshotMap = WorkspaceJsonCodec.calculatorToMap(record)
                            )
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(0.38f),
                    testTag = "calc_delete_${record.id}"
                )
                SarimGradientButton(
                    text = "Edit",
                    onClick = { onEditClick(record) },
                    modifier = Modifier.weight(0.62f),
                    testTag = "calc_edit_${record.id}"
                )
            }
        }
    )
}

@Composable
private fun CalcKvRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    val accents = LocalSarimAccents.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = valueColor)
        }
        HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.4f))
    }
}

@Composable
private fun CalcAccordion(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val accents = LocalSarimAccents.current
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(16.dp),
        color = accents.softPillFill.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, accents.borderSubtle)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = title,
                    tint = accents.emerald,
                    modifier = Modifier.size(18.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    content()
                }
            }
        }
    }
}
