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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.MetricKpiCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SimpleBarChart
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import kotlin.math.abs

@Composable
fun CalculatorScreen(snapshot: WorkspaceSnapshot, todayIso: String, vm: SarimViewModel) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney
    val activeStores = remember(snapshot.stores) { snapshot.stores.filter { it.active } }

    val repList = remember(snapshot) {
        (snapshot.teamMembers.map { it.displayName } +
            snapshot.sales.filter { !it.recycled && it.repName != "NONE" }.map { it.repName } +
            snapshot.repRecords.filter { !it.recycled }.map { it.repName } +
            snapshot.entities.filter { !it.recycled && it.entityType == "staff" }.map { it.name })
            .map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }

    var subTab by remember { mutableIntStateOf(0) }
    var dateFrom by remember(todayIso) { mutableStateOf(todayIso) }
    var dateTo by remember(todayIso) { mutableStateOf(todayIso) }
    var selectedRep by remember(repList) { mutableStateOf(repList.firstOrNull() ?: "") }
    var selectedStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var soldStr by remember { mutableStateOf("") }
    var returnStr by remember { mutableStateOf("0") }
    var returnTarget by remember { mutableStateOf("store") }
    var returnTargetRep by remember { mutableStateOf("") }
    var expiredStr by remember { mutableStateOf("0") }
    var sharedDeductionStr by remember { mutableStateOf("0") }
    var creditQtyStr by remember { mutableStateOf("0") }
    var rateStr by remember(selectedStore) { mutableStateOf(selectedStore?.salePrice?.toString() ?: "") }
    var physicalCashStr by remember { mutableStateOf("") }
    var addToLedger by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var confirmVarianceDialog by remember { mutableStateOf(false) }

    val commissions = remember { mutableStateListOf<Pair<String, String>>() }
    val expenses = remember { mutableStateListOf<Pair<String, String>>() }

    LaunchedEffect(selectedRep, selectedStore, dateFrom, dateTo, snapshot.sales) {
        if (selectedRep.isNotBlank() && selectedStore != null) {
            val autoKg = FinanceEngine.getUnreconciledRepDeliveriesKg(
                selectedRep, selectedStore!!.storeKey, dateFrom, dateTo, snapshot.sales
            )
            if (autoKg > 0.0) soldStr = autoKg.toString()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            listOf("Settlement", "Market Debt", "History").forEachIndexed { idx, title ->
                Tab(selected = subTab == idx, onClick = { subTab = idx }, text = { Text(title) }, modifier = Modifier.testTag("calc_subtab_$idx"))
            }
        }

        when (subTab) {
            0 -> {
                val sold = soldStr.toDoubleOrNull() ?: 0.0
                val returned = returnStr.toDoubleOrNull() ?: 0.0
                val expired = expiredStr.toDoubleOrNull() ?: 0.0
                val netSold = InputGuards.round3((sold - returned - expired).coerceAtLeast(0.0))
                val creditQty = (creditQtyStr.toDoubleOrNull() ?: 0.0).coerceIn(0.0, netSold)
                val cashQty = InputGuards.round3((netSold - creditQty).coerceAtLeast(0.0))
                val rate = rateStr.toDoubleOrNull() ?: (selectedStore?.salePrice ?: 0.0)
                val sharedDed = sharedDeductionStr.toDoubleOrNull() ?: 0.0
                val commTotal = InputGuards.round2(commissions.sumOf { it.second.toDoubleOrNull() ?: 0.0 })
                val expTotal = InputGuards.round2(expenses.sumOf { it.second.toDoubleOrNull() ?: 0.0 })
                val expectedCash = InputGuards.round2((cashQty * rate) - sharedDed - commTotal - expTotal)
                val physicalCash = physicalCashStr.toDoubleOrNull() ?: expectedCash
                val variance = InputGuards.round2(physicalCash - expectedCash)

                fun commitSettlement() {
                    val store = selectedStore ?: return
                    vm.submitCalculatorSettlement(
                        dateFrom = dateFrom,
                        dateTo = dateTo,
                        repName = selectedRep,
                        store = store,
                        totalSold = sold,
                        returnedKg = returned,
                        returnTarget = returnTarget,
                        returnTargetName = if (returnTarget == "rep") returnTargetRep else store.name,
                        expiredKg = expired,
                        sharedDeduction = sharedDed,
                        cashQty = cashQty,
                        creditQty = creditQty,
                        rate = rate,
                        commissions = commissions.mapNotNull { (l, a) ->
                            val v = a.toDoubleOrNull() ?: 0.0
                            if (v > 0) mapOf("label" to l.ifBlank { "Commission" }, "amount" to v) else null
                        },
                        expenses = expenses.mapNotNull { (l, a) ->
                            val v = a.toDoubleOrNull() ?: 0.0
                            if (v > 0) mapOf("label" to l.ifBlank { "Field Expense" }, "amount" to v) else null
                        },
                        physicalCash = physicalCash,
                        addToLedger = addToLedger,
                        notes = notes
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            Text("Rep Settlement Calculator", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DatePickerField("From", dateFrom, { dateFrom = it }, "calc_date_from", Modifier.weight(1f))
                                DatePickerField("To", dateTo, { dateTo = it }, "calc_date_to", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = selectedRep,
                                onValueChange = { selectedRep = it },
                                label = { Text("Sales Rep Name") },
                                modifier = Modifier.fillMaxWidth().testTag("calc_rep_input")
                            )
                            if (repList.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                PickerField("Or Pick Rep", selectedRep, repList, { it }, { selectedRep = it }, "calc_rep_picker")
                            }
                            Spacer(Modifier.height(10.dp))
                            PickerField(
                                label = "Store",
                                selectedLabel = selectedStore?.name ?: "Select Store",
                                items = activeStores,
                                itemLabel = { it.name },
                                onSelect = { selectedStore = it; rateStr = it.salePrice.toString() },
                                testTag = "calc_store_picker"
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DecimalField("Total Issued/Sold", soldStr, { soldStr = it }, "calc_sold_input", Modifier.weight(1f), "kg")
                                DecimalField("Returned", returnStr, { returnStr = it }, "calc_return_input", Modifier.weight(1f), "kg")
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = returnTarget == "store", onClick = { returnTarget = "store" }, label = { Text("Return to Store") })
                                FilterChip(selected = returnTarget == "rep", onClick = { returnTarget = "rep" }, label = { Text("Transfer to Rep") })
                            }
                            if (returnTarget == "rep") {
                                OutlinedTextField(
                                    value = returnTargetRep,
                                    onValueChange = { returnTargetRep = it },
                                    label = { Text("Receiving Rep Name") },
                                    modifier = Modifier.fillMaxWidth().testTag("calc_target_rep_input")
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DecimalField("Expired", expiredStr, { expiredStr = it }, "calc_expired_input", Modifier.weight(1f), "kg")
                                DecimalField("Credit Qty", creditQtyStr, { creditQtyStr = it }, "calc_credit_qty_input", Modifier.weight(1f), "kg")
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DecimalField("Rate / kg", rateStr, { rateStr = it }, "calc_rate_input", Modifier.weight(1f))
                                DecimalField("Shared Deduction", sharedDeductionStr, { sharedDeductionStr = it }, "calc_shared_ded_input", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Commissions (${InputGuards.formatMoney(commTotal, currency, masked)})", style = MaterialTheme.typography.titleSmall)
                                OutlinedButton(onClick = { commissions.add("" to "") }, modifier = Modifier.testTag("calc_add_comm_btn")) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Commission")
                                }
                            }
                            commissions.forEachIndexed { idx, pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = pair.first,
                                        onValueChange = { commissions[idx] = it to pair.second },
                                        label = { Text("Label") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    DecimalField("Amount", pair.second, { commissions[idx] = pair.first to it }, "calc_comm_amt_$idx", Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Field Expenses (${InputGuards.formatMoney(expTotal, currency, masked)})", style = MaterialTheme.typography.titleSmall)
                                OutlinedButton(onClick = { expenses.add("" to "") }, modifier = Modifier.testTag("calc_add_exp_btn")) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Expense")
                                }
                            }
                            expenses.forEachIndexed { idx, pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = pair.first,
                                        onValueChange = { expenses[idx] = it to pair.second },
                                        label = { Text("Item") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    DecimalField("Amount", pair.second, { expenses[idx] = pair.first to it }, "calc_exp_amt_$idx", Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            DecimalField("Physical Cash Handed Over", physicalCashStr, { physicalCashStr = it }, "calc_physical_cash_input")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Add physical cash to Daily Ledger", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = addToLedger, onCheckedChange = { addToLedger = it }, modifier = Modifier.testTag("calc_ledger_switch"))
                            }
                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it.take(500) },
                                label = { Text("Settlement Notes") },
                                modifier = Modifier.fillMaxWidth().testTag("calc_notes_input")
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MetricKpiCard("Expected Cash", InputGuards.formatMoney(expectedCash, currency, masked), "Net ${InputGuards.formatKg(netSold)}", accents.cyan, modifier = Modifier.weight(1f))
                                MetricKpiCard(
                                    label = "Short / Over",
                                    value = InputGuards.formatMoney(variance, currency, masked),
                                    accent = if (abs(variance) < 1.0) accents.emerald else accents.gold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    if (abs(variance) >= 1.0) confirmVarianceDialog = true else commitSettlement()
                                },
                                enabled = selectedRep.isNotBlank() && selectedStore != null && sold > 0,
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("calc_save_btn")
                            ) {
                                Text("Finalize Settlement")
                            }
                        }
                    }
                }

                if (confirmVarianceDialog) {
                    AlertDialog(
                        onDismissRequest = { confirmVarianceDialog = false },
                        title = { Text("Cash Variance Detected") },
                        text = {
                            Text("Expected ${InputGuards.formatMoney(expectedCash, currency, masked)}, Physical ${InputGuards.formatMoney(physicalCash, currency, masked)} (Diff ${InputGuards.formatMoney(variance, currency, masked)}). Record anyway?")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmVarianceDialog = false
                                commitSettlement()
                            }, modifier = Modifier.testTag("calc_confirm_variance_btn")) { Text("Confirm & Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmVarianceDialog = false }) { Text("Cancel") }
                        }
                    )
                }
            }

            1 -> {
                val byRep = remember(snapshot.repRecords, snapshot.calculator) {
                    val names = (snapshot.repRecords.filter { !it.recycled }.map { it.repName } +
                        snapshot.calculator.filter { !it.recycled }.map { it.repName }).distinct()
                    names.map { rep ->
                        val credit = snapshot.repRecords.filter { !it.recycled && it.repName == rep && it.mode == "sale" }.sumOf { it.creditAmount } +
                            snapshot.calculator.filter { !it.recycled && it.repName == rep }.sumOf { it.creditQtyKg * it.ratePerKg }
                        val collected = snapshot.repRecords.filter { !it.recycled && it.repName == rep && it.mode == "collection" }.sumOf { it.cashReceived }
                        Triple(rep, InputGuards.round2(credit), InputGuards.round2(credit - collected))
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        SimpleBarChart(
                            title = "Open Market Debt by Rep",
                            items = byRep.map { it.first to it.third.coerceAtLeast(0.0) },
                            barColor = accents.gold,
                            currency = currency,
                            masked = masked
                        )
                    }
                    items(byRep, key = { it.first }) { (rep, totalCr, balance) ->
                        SarimCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(rep, style = MaterialTheme.typography.titleMedium)
                                Text(InputGuards.formatMoney(balance, currency, masked), color = accents.gold, style = MaterialTheme.typography.titleMedium)
                            }
                            Text("Lifetime Credit ${InputGuards.formatMoney(totalCr, currency, masked)}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                    }
                }
            }

            else -> {
                val activeCalc = remember(snapshot.calculator) { snapshot.calculator.filter { !it.recycled } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeCalc.isEmpty()) {
                        item { EmptyStateCard("No settlements saved", "Complete a rep settlement on the first tab.") }
                    } else {
                        items(activeCalc, key = { it.id }) { c ->
                            SarimCard {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${c.repName} • ${c.storeKey}", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "${c.dateFrom}..${c.dateTo} • Net ${InputGuards.formatKg(c.netSoldKg)} • Cash ${InputGuards.formatMoney(c.physicalCash, currency, masked)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accents.textMuted
                                        )
                                    }
                                    IconButton(
                                        onClick = { vm.recycleRecord("calculator", c.id, "${c.repName} (${c.dateTo})") },
                                        modifier = Modifier.testTag("calc_delete_${c.id}")
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Move to Recycle Bin", tint = accents.danger)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
