package com.gullzubair.sarim.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.CalculatorSettlement
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
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimYearMonthDayTree
import com.gullzubair.sarim.ui.components.SimpleBarChart
import com.gullzubair.sarim.ui.theme.JakartaFont
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
    var selectedCalc by remember { mutableStateOf<CalculatorSettlement?>(null) }

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

    val activeCalc = remember(snapshot.calculator) { snapshot.calculator.filter { !it.recycled }.sortedByDescending { it.dateTo } }
    val todayCalcs = remember(activeCalc, todayIso) { activeCalc.filter { it.dateTo == todayIso } }
    val dayRevenue = remember(todayCalcs) { todayCalcs.sumOf { it.netSoldKg * it.ratePerKg } }
    val dayExpected = remember(todayCalcs) { todayCalcs.sumOf { it.expectedCash } }
    val dayPhysical = remember(todayCalcs) { todayCalcs.sumOf { it.physicalCash } }
    val dayCashKg = remember(todayCalcs) { todayCalcs.sumOf { it.cashQtyKg } }
    val dayCreditKg = remember(todayCalcs) { todayCalcs.sumOf { it.creditQtyKg } }
    val dayReturnedKg = remember(todayCalcs) { todayCalcs.sumOf { it.returnedKg } }
    val dayTotalKg = remember(todayCalcs) { todayCalcs.sumOf { it.totalSoldKg } }
    val dayVariance = remember(dayPhysical, dayExpected) { InputGuards.round2(dayPhysical - dayExpected) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Daily View", "Settlement", "Market Debt").forEachIndexed { idx, title ->
                val selected = subTab == idx
                Surface(
                    onClick = { subTab = idx },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) accents.softPillFill else accents.inputFill,
                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                    modifier = Modifier.weight(1f).testTag("calc_subtab_$idx")
                ) {
                    Box(Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(title, style = MaterialTheme.typography.labelLarge, color = if (selected) accents.emerald else accents.textMuted, maxLines = 1)
                    }
                }
            }
        }

        when (subTab) {
            0 -> {
                val activeRepName = selectedRep.ifBlank { byRep.firstOrNull()?.first ?: "ALL REPS" }
                val activeRepDebt = byRep.firstOrNull { it.first.equals(activeRepName, true) }?.third
                    ?: byRep.sumOf { it.third }
                val totKgSum = (dayCashKg + dayCreditKg).coerceAtLeast(1.0)
                val cashPct = if (dayCashKg + dayCreditKg > 0) ((dayCashKg / totKgSum) * 100).toInt() else 0
                val creditPct = if (dayCashKg + dayCreditKg > 0) 100 - cashPct else 0

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Mirrored DAILY VIEW card (matching screenshot 115955 top)
                    item {
                        SarimCard {
                            Text(
                                "DAILY VIEW",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("REVENUE", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Text(
                                InputGuards.formatNum(dayRevenue),
                                fontFamily = JakartaFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp
                            )
                            Text(
                                "Profit ${InputGuards.formatNum(todayCalcs.sumOf { (it.netSoldKg * it.ratePerKg) - it.commissionTotal - it.expenseTotal })}",
                                style = MaterialTheme.typography.bodySmall,
                                color = accents.textMuted
                            )
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(accents.borderSubtle.copy(alpha = 0.5f))
                            ) {
                                if (cashPct > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(cashPct / 100f)
                                            .height(6.dp)
                                            .background(accents.emerald)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(accents.emerald))
                                    Text("Cash  ${InputGuards.formatKg(dayCashKg)}  $cashPct%", style = MaterialTheme.typography.labelSmall)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(accents.gold))
                                    Text("Credit  ${InputGuards.formatKg(dayCreditKg)}  $creditPct%", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("TOTAL SOLD", InputGuards.formatKg(dayTotalKg), accents.cyan, Modifier.weight(1f))
                                SarimHalfRingKpiBox("RETURNED", InputGuards.formatKg(dayReturnedKg), accents.gold, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("CREDIT", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(accents.borderSubtle.copy(alpha = 0.5f))
                            )
                            Spacer(Modifier.height(8.dp))
                            SarimHalfRingKpiBox(
                                label = "NET DEBT",
                                value = InputGuards.formatNum(dayCreditKg * (todayCalcs.firstOrNull()?.ratePerKg ?: 0.0)),
                                ringColor = accents.emerald,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("CASH HANDOVER", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (abs(dayVariance) < 1.0) accents.softPillFill else accents.gold.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = if (abs(dayVariance) < 1.0) "Perfect match" else "Variance ${InputGuards.formatNum(dayVariance)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (abs(dayVariance) < 1.0) accents.emerald else accents.gold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("EXPECTED", InputGuards.formatNum(dayExpected), accents.cyan, Modifier.weight(1f))
                                SarimHalfRingKpiBox("RECEIVED", InputGuards.formatNum(dayPhysical), accents.emerald, Modifier.weight(1f))
                            }
                        }
                    }

                    // Mirrored MARKET DEBT TRACKER card
                    item {
                        SarimCard {
                            Text(
                                "${activeRepName.uppercase()}  •  MARKET DEBT TRACKER",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("CURRENT MARKET DEBT", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Text(
                                InputGuards.formatNum(activeRepDebt),
                                fontFamily = JakartaFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = if (activeRepDebt > 0) accents.danger else accents.emerald
                            )
                        }
                    }

                    // Year -> Month -> Day Settlement History
                    item {
                        Text(
                            "SETTLEMENT HISTORY",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp),
                            color = accents.textMuted
                        )
                    }
                    if (activeCalc.isEmpty()) {
                        item { EmptyStateCard("No settlements saved", "Switch to Settlement tab to record a rep settlement.") }
                    } else {
                        item {
                            SarimYearMonthDayTree(
                                items = activeCalc,
                                dateOf = { it.dateTo },
                                summaryLabel = { list -> "${InputGuards.formatKg(list.sumOf { it.totalSoldKg })} • ${InputGuards.formatNum(list.sumOf { it.physicalCash })}" },
                                itemRow = { c ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedCalc = c }
                                            .padding(vertical = 8.dp, horizontal = 4.dp)
                                            .testTag("calc_row_${c.id}"),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(c.repName.uppercase(), style = MaterialTheme.typography.titleSmall)
                                            Text("${c.storeKey} • Sold ${InputGuards.formatKg(c.totalSoldKg)}", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                        }
                                        Text(
                                            InputGuards.formatNum(c.totalSoldKg * c.ratePerKg),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = accents.emerald
                                        )
                                    }
                                }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            1 -> {
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
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
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
                            SarimPillInput(selectedRep, { selectedRep = it }, label = "SALES REP NAME", testTag = "calc_rep_input")
                            if (repList.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                PickerField("Or Pick Rep", selectedRep, repList, { it }, { selectedRep = it }, "calc_rep_picker")
                            }
                            Spacer(Modifier.height(10.dp))
                            PickerField("Store", selectedStore?.name ?: "Select Store", activeStores, { it.name }, { selectedStore = it; rateStr = it.salePrice.toString() }, "calc_store_picker")
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DecimalField("Total Issued/Sold", soldStr, { soldStr = it }, "calc_sold_input", Modifier.weight(1f), "kg")
                                DecimalField("Returned", returnStr, { returnStr = it }, "calc_return_input", Modifier.weight(1f), "kg")
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("store" to "Return to Store", "rep" to "Transfer to Rep").forEach { (key, title) ->
                                    val sel = returnTarget == key
                                    Surface(
                                        onClick = { returnTarget = key },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (sel) accents.softPillFill else accents.inputFill,
                                        border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                            Text(title, style = MaterialTheme.typography.labelMedium, color = if (sel) accents.emerald else accents.textMuted)
                                        }
                                    }
                                }
                            }
                            if (returnTarget == "rep") {
                                Spacer(Modifier.height(8.dp))
                                SarimPillInput(returnTargetRep, { returnTargetRep = it }, label = "RECEIVING REP NAME", testTag = "calc_target_rep_input")
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
                                    SarimPillInput(pair.first, { commissions[idx] = it to pair.second }, label = "LABEL", modifier = Modifier.weight(1f))
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
                                    SarimPillInput(pair.first, { expenses[idx] = it to pair.second }, label = "ITEM", modifier = Modifier.weight(1f))
                                    DecimalField("Amount", pair.second, { expenses[idx] = pair.first to it }, "calc_exp_amt_$idx", Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            DecimalField("Physical Cash Handed Over", physicalCashStr, { physicalCashStr = it }, "calc_physical_cash_input")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Add physical cash to Daily Ledger", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = addToLedger, onCheckedChange = { addToLedger = it }, modifier = Modifier.testTag("calc_ledger_switch"))
                            }
                            SarimPillInput(notes, { notes = it.take(500) }, label = "SETTLEMENT NOTES", testTag = "calc_notes_input")
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("Expected Cash", InputGuards.formatMoney(expectedCash, currency, masked), accents.cyan, Modifier.weight(1f))
                                SarimHalfRingKpiBox("Short / Over", InputGuards.formatMoney(variance, currency, masked), if (abs(variance) < 1.0) accents.emerald else accents.gold, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(14.dp))
                            SarimGradientButton(
                                text = "Finalize Settlement",
                                onClick = { if (abs(variance) >= 1.0) confirmVarianceDialog = true else commitSettlement() },
                                enabled = selectedRep.isNotBlank() && selectedStore != null && sold > 0,
                                testTag = "calc_save_btn"
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
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

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        SimpleBarChart("Open Market Debt by Rep", byRep.map { it.first to it.third.coerceAtLeast(0.0) }, accents.gold, currency, masked)
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
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    // Mirrored Settlement Bottom Sheet with QUANTITIES, CREDIT AND CASH, COMMISSION BREAKDOWN accordions (115955 & 120001)
    selectedCalc?.let { c ->
        var showQuantities by remember(c.id) { mutableStateOf(false) }
        var showCreditCash by remember(c.id) { mutableStateOf(false) }
        var showCommissions by remember(c.id) { mutableStateOf(false) }

        SarimDetailBottomSheet(
            title = DateUtils.formatPretty(c.dateTo).uppercase(),
            rightTitle = InputGuards.formatNum(c.netSoldKg * c.ratePerKg),
            subtitle = c.repName.uppercase(),
            onDismiss = { selectedCalc = null },
            content = {
                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                SheetKvRow("Total sold", InputGuards.formatNum(c.totalSoldKg))
                SheetKvRow("Profit", InputGuards.formatNum((c.netSoldKg * c.ratePerKg) - c.commissionTotal - c.expenseTotal))
                SheetKvRow("Expected cash", InputGuards.formatNum(c.expectedCash))
                SheetKvRow("Received cash", InputGuards.formatNum(c.physicalCash))
                SheetKvRow("Discrepancy", if (abs(c.variance) < 1.0) "Perfect match" else InputGuards.formatNum(c.variance))
                Spacer(Modifier.height(8.dp))
                AccordionSection(
                    title = "QUANTITIES",
                    expanded = showQuantities,
                    onToggle = { showQuantities = !showQuantities }
                ) {
                    SheetKvRow("Returned", InputGuards.formatNum(c.returnedKg), accents.cyan)
                    SheetKvRow("Expired (to CHORA)", InputGuards.formatNum(c.expiredKg), accents.danger)
                    SheetKvRow("Shared (deduction)", InputGuards.formatNum(c.sharedDeduction), accents.danger)
                    SheetKvRow("Cash qty", InputGuards.formatNum(c.cashQtyKg), accents.cyan)
                    SheetKvRow("Credit qty", InputGuards.formatNum(c.creditQtyKg), accents.cyan)
                }
                Spacer(Modifier.height(6.dp))
                AccordionSection(
                    title = "CREDIT AND CASH",
                    expanded = showCreditCash,
                    onToggle = { showCreditCash = !showCreditCash }
                ) {
                    SheetKvRow("Credit out", InputGuards.formatNum(c.creditQtyKg * c.ratePerKg), accents.danger)
                    SheetKvRow("Credit in", "0", accents.emerald)
                    SheetKvRow("Net debt", InputGuards.formatNum(c.creditQtyKg * c.ratePerKg), accents.danger)
                    SheetKvRow("Commission paid", InputGuards.formatNum(c.commissionTotal), accents.emerald)
                }
                Spacer(Modifier.height(6.dp))
                AccordionSection(
                    title = "COMMISSION BREAKDOWN",
                    expanded = showCommissions,
                    onToggle = { showCommissions = !showCommissions }
                ) {
                    if (c.commissions.isEmpty()) {
                        SheetKvRow("Total Commission", InputGuards.formatNum(c.commissionTotal), accents.danger)
                    } else {
                        c.commissions.forEach { m ->
                            val lbl = m["label"]?.toString() ?: "Commission"
                            val amt = (m["amount"] as? Number)?.toDouble() ?: 0.0
                            SheetKvRow(lbl, InputGuards.formatNum(amt), accents.danger)
                        }
                    }
                }
            },
            footer = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SarimDangerButton(
                        text = "Delete",
                        onClick = {
                            vm.recycleRecord("calculator", c.id, "${c.repName} (${c.dateTo})")
                            selectedCalc = null
                        },
                        modifier = Modifier.weight(0.38f),
                        testTag = "calc_delete_${c.id}"
                    )
                    SarimGradientButton(
                        text = "Edit",
                        onClick = {
                            dateFrom = c.dateFrom
                            dateTo = c.dateTo
                            selectedRep = c.repName
                            selectedStore = activeStores.firstOrNull { it.storeKey == c.storeKey } ?: selectedStore
                            soldStr = c.totalSoldKg.toString()
                            returnStr = c.returnedKg.toString()
                            expiredStr = c.expiredKg.toString()
                            sharedDeductionStr = c.sharedDeduction.toString()
                            creditQtyStr = c.creditQtyKg.toString()
                            rateStr = c.ratePerKg.toString()
                            physicalCashStr = c.physicalCash.toString()
                            notes = c.notes ?: ""
                            selectedCalc = null
                            subTab = 1
                        },
                        modifier = Modifier.weight(0.62f)
                    )
                }
            }
        )
    }
}

@Composable
private fun SheetKvRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
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
private fun AccordionSection(
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
