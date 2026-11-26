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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.CalculatorRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.CalculatorDetailBottomSheet
import com.gullzubair.sarim.ui.components.CalculatorPerformanceChartCard
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.DiscardEditDialog
import com.gullzubair.sarim.ui.components.EditDiffConfirmDialog
import com.gullzubair.sarim.ui.components.EditedBadge
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.TabHeading
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimNoteBanner
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
    val masked = vm.sessionInfoFor(snapshot.profile).restrictMoney || snapshot.settings.hideSensitiveMoney
    val activeStores = remember(snapshot.stores) { snapshot.stores.filter { it.deletedAt == null && it.active } }

    val repList = remember(snapshot) {
        (snapshot.settings.reps +
            snapshot.teamMembers.map { it.displayName } +
            snapshot.sales.filter { it.deletedAt == null && it.salesRep != "NONE" }.map { it.salesRep } +
            snapshot.repRecords.filter { it.deletedAt == null }.map { it.salesRep } +
            snapshot.entities.filter { it.deletedAt == null && (it.type == "staff" || it.type == "employee") }.map { it.name })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    var subTab by remember { mutableIntStateOf(0) }
    var editingCalc by remember { mutableStateOf<CalculatorRecord?>(null) }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var dateFrom by remember(todayIso) { mutableStateOf(todayIso) }
    var selectedRep by remember(repList) { mutableStateOf(repList.firstOrNull() ?: "") }
    var selectedStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var soldStr by remember { mutableStateOf("") }
    var returnStr by remember { mutableStateOf("0") }
    var returnTarget by remember { mutableStateOf("store") }
    var returnTargetRep by remember { mutableStateOf("") }
    var expiredStr by remember { mutableStateOf("0") }
    var sharedKgStr by remember { mutableStateOf("0") }
    var creditQtyStr by remember { mutableStateOf("0") }
    var prevCollStr by remember { mutableStateOf("0") }
    var commissionPerUnitStr by remember { mutableStateOf("0") }
    var rateStr by remember(selectedStore) { mutableStateOf(selectedStore?.salePrice?.takeIf { it > 0 }?.toString() ?: "") }
    var physicalCashStr by remember { mutableStateOf("") }
    var addToLedger by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var confirmVarianceDialog by remember { mutableStateOf(false) }
    var confirmEditDiffDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var selectedCalc by remember { mutableStateOf<CalculatorRecord?>(null) }

    val commissions = remember { mutableStateListOf<Pair<String, String>>() }
    val expenses = remember { mutableStateListOf<Pair<String, String>>() }

    fun resetForm() {
        editingCalc = null
        date = todayIso
        dateFrom = todayIso
        soldStr = ""
        returnStr = "0"
        returnTarget = "store"
        returnTargetRep = ""
        expiredStr = "0"
        sharedKgStr = "0"
        creditQtyStr = "0"
        prevCollStr = "0"
        commissionPerUnitStr = "0"
        physicalCashStr = ""
        commissions.clear()
        expenses.clear()
        notes = ""
    }

    BackHandler(enabled = editingCalc != null) {
        showDiscardDialog = true
    }

    val activeCalc = remember(snapshot.calculator) {
        snapshot.calculator.filter { it.deletedAt == null }
            .sortedWith(compareByDescending<CalculatorRecord> { it.date }.thenByDescending { it.timestamp })
    }

    LaunchedEffect(selectedRep, selectedStore, dateFrom, date, snapshot.sales, editingCalc) {
        if (editingCalc == null && selectedRep.isNotBlank() && selectedStore != null) {
            val fromEff = dateFrom.ifBlank { date }.let { if (it <= date) it else date }
            val autoKg = FinanceEngine.getUnreconciledRepDeliveriesKg(
                repName = selectedRep,
                storeKey = selectedStore!!.key,
                dateFrom = fromEff,
                dateTo = date,
                sales = snapshot.sales
            )
            if (autoKg > 0.0) soldStr = autoKg.toString()
        }
    }

    val todayCalcs = remember(activeCalc, todayIso) { activeCalc.filter { it.date == todayIso } }
    val dayRevenue = remember(todayCalcs) { todayCalcs.sumOf { it.revenue } }
    val dayProfit = remember(todayCalcs) { todayCalcs.sumOf { it.revenue - it.commissionPaid - it.fieldExpenses } }
    val dayExpected = remember(todayCalcs) { todayCalcs.sumOf { it.totalExpected } }
    val dayPhysical = remember(todayCalcs) { todayCalcs.sumOf { it.received } }
    val dayCashKg = remember(todayCalcs) { todayCalcs.sumOf { it.cashQty } }
    val dayCreditKg = remember(todayCalcs) { todayCalcs.sumOf { it.creditQty } }
    val dayCreditValue = remember(todayCalcs) { todayCalcs.sumOf { it.creditValue } }
    val dayReturnedKg = remember(todayCalcs) { todayCalcs.sumOf { it.returned } }
    val dayTotalKg = remember(todayCalcs) { todayCalcs.sumOf { it.totalSold } }
    val dayVariance = remember(dayPhysical, dayExpected) { InputGuards.round2(dayPhysical - dayExpected) }

    val byRep = remember(snapshot.repRecords, activeCalc) {
        val names = (snapshot.repRecords.filter { it.deletedAt == null }.map { it.salesRep } +
            activeCalc.map { it.seller })
            .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        names.map { rep ->
            val repCredits = snapshot.repRecords
                .filter { it.deletedAt == null && it.salesRep.equals(rep, true) && it.paymentType == "CREDIT" && !it.creditReceived }
                .sumOf { (it.totalValue - it.partialPaymentReceived).coerceAtLeast(0.0) }
            val calcCredits = activeCalc.filter { it.seller.equals(rep, true) }.sumOf { it.creditValue }
            val collections = snapshot.repRecords
                .filter { it.deletedAt == null && it.salesRep.equals(rep, true) && FinanceEngine.isCollectionRow(it) }
                .sumOf { it.collectedAmount } +
                activeCalc.filter { it.seller.equals(rep, true) }.sumOf { it.prevColl }
            val totalCr = InputGuards.round2(repCredits + calcCredits)
            val balance = InputGuards.round2(totalCr - collections)
            Triple(rep, totalCr, balance)
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
                    item { TabHeading("Calculator") }
                    item {
                        SarimCard {
                            Text(
                                "DAILY VIEW",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("REVENUE", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Text(
                                text = InputGuards.formatMoney(dayRevenue, currency, masked),
                                fontFamily = JakartaFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp
                            )
                            Text(
                                "Profit ${InputGuards.formatMoney(dayProfit, currency, masked)}",
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
                            SarimHalfRingKpiBox(
                                label = "NET CREDIT",
                                value = InputGuards.formatMoney(dayCreditValue, currency, masked),
                                ringColor = accents.emerald,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("CASH HANDOVER", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (abs(dayVariance) <= 1.0) accents.softPillFill else accents.gold.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = if (abs(dayVariance) <= 1.0) "Perfect match" else "Variance ${InputGuards.formatMoney(dayVariance, currency, masked)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (abs(dayVariance) <= 1.0) accents.emerald else accents.gold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("EXPECTED", InputGuards.formatMoney(dayExpected, currency, masked), accents.cyan, Modifier.weight(1f))
                                SarimHalfRingKpiBox("RECEIVED", InputGuards.formatMoney(dayPhysical, currency, masked), accents.emerald, Modifier.weight(1f))
                            }
                        }
                    }

                    item {
                        SarimCard {
                            Text(
                                "${activeRepName.uppercase()}  •  MARKET DEBT TRACKER",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("CURRENT MARKET DEBT", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Text(
                                InputGuards.formatMoney(activeRepDebt, currency, masked),
                                fontFamily = JakartaFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = if (activeRepDebt > 0) accents.danger else accents.emerald
                            )
                        }
                    }

                    if (!masked) {
                        item {
                            CalculatorPerformanceChartCard(
                                history = activeCalc,
                                seller = activeRepName,
                                referenceDateIso = todayIso,
                                currency = currency,
                                masked = masked
                            )
                        }
                    }

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
                                dateOf = { it.date },
                                summaryLabel = { list ->
                                    "${InputGuards.formatKg(list.sumOf { it.totalSold })} • ${InputGuards.formatMoney(list.sumOf { it.received }, currency, masked)}"
                                },
                                itemRow = { c ->
                                    val storeLabel = FormulaEngine.storeLabelFor(c.returnStore, snapshot.stores)
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
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(c.seller.uppercase(), style = MaterialTheme.typography.titleSmall)
                                                EditedBadge(c.isEdited)
                                            }
                                            Text(
                                                "${storeLabel.ifBlank { c.returnType.ifBlank { "Settlement" } }} • Sold ${InputGuards.formatKg(c.totalSold)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accents.textMuted
                                            )
                                        }
                                        Text(
                                            InputGuards.formatMoney(c.revenue, currency, masked),
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
                val sharedKg = sharedKgStr.toDoubleOrNull() ?: 0.0
                val netSold = InputGuards.round2((sold - returned - expired - sharedKg).coerceAtLeast(0.0))
                val creditQty = (creditQtyStr.toDoubleOrNull() ?: 0.0).coerceIn(0.0, netSold)
                val cashQty = InputGuards.round2((netSold - creditQty).coerceAtLeast(0.0))
                val rate = rateStr.toDoubleOrNull() ?: (selectedStore?.salePrice ?: 0.0)
                val prevColl = prevCollStr.toDoubleOrNull() ?: 0.0
                val commPerUnit = commissionPerUnitStr.toDoubleOrNull() ?: 0.0
                val commTotal = InputGuards.round2(commissions.sumOf { it.second.toDoubleOrNull() ?: 0.0 })
                val expTotal = InputGuards.round2(expenses.sumOf { it.second.toDoubleOrNull() ?: 0.0 })
                val grossCommission = InputGuards.round2(commPerUnit * netSold)
                val commissionPayable = InputGuards.round2((grossCommission - commTotal).coerceAtLeast(0.0))
                val expectedCash = InputGuards.round2((cashQty * rate) + prevColl - expTotal - commTotal)
                val physicalCash = physicalCashStr.toDoubleOrNull() ?: expectedCash
                val variance = InputGuards.round2(physicalCash - expectedCash)

                fun commitSettlement() {
                    val store = selectedStore ?: return
                    val expItems = expenses.mapNotNull { (l, a) ->
                        val v = a.toDoubleOrNull() ?: 0.0
                        if (v > 0) mapOf("label" to l.ifBlank { "Field Expense" }, "amount" to v) else null
                    }
                    val commItems = commissions.mapNotNull { (l, a) ->
                        val v = a.toDoubleOrNull() ?: 0.0
                        if (v > 0) mapOf("label" to l.ifBlank { "Commission" }, "amount" to v) else null
                    }
                    val targetEdit = editingCalc
                    if (targetEdit != null) {
                        vm.editCalculatorSettlement(
                            original = targetEdit,
                            date = date,
                            dateFrom = dateFrom.ifBlank { date },
                            seller = selectedRep,
                            store = store,
                            totalSold = sold,
                            returned = returned,
                            returnType = returnTarget,
                            returnTargetRep = if (returnTarget == "rep") returnTargetRep else "",
                            expired = expired,
                            shared = sharedKg,
                            creditQty = creditQty,
                            unitPrice = rate,
                            fieldExpenseItems = expItems,
                            commissionPerUnit = commPerUnit,
                            commissionItems = commItems,
                            prevColl = prevColl,
                            received = physicalCash,
                            notes = notes,
                            onSuccess = { resetForm() }
                        )
                    } else {
                        vm.submitCalculatorSettlement(
                            date = date,
                            dateFrom = dateFrom.ifBlank { date },
                            seller = selectedRep,
                            store = store,
                            totalSold = sold,
                            returned = returned,
                            returnType = returnTarget,
                            returnTargetRep = if (returnTarget == "rep") returnTargetRep else "",
                            expired = expired,
                            shared = sharedKg,
                            creditQty = creditQty,
                            unitPrice = rate,
                            fieldExpenseItems = expItems,
                            commissionPerUnit = commPerUnit,
                            commissionItems = commItems,
                            prevColl = prevColl,
                            received = physicalCash,
                            addToLedger = addToLedger,
                            notes = notes
                        )
                        resetForm()
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    editingCalc?.let { orig ->
                        val isQuick = selectedRep == orig.seller &&
                            date == orig.date &&
                            InputGuards.round2(sold) == InputGuards.round2(orig.totalSold) &&
                            InputGuards.round2(returned) == InputGuards.round2(orig.returned) &&
                            InputGuards.round2(expired) == InputGuards.round2(orig.expired)
                        item {
                            SarimNoteBanner(
                                text = if (isQuick) {
                                    "Editing ${orig.seller} (${orig.date}) — Quick Edit preserves existing sales/return links."
                                } else {
                                    "Editing ${orig.seller} (${orig.date}) — Full Edit reverses and re-applies stock & sales links."
                                }
                            )
                        }
                    }

                    item {
                        SarimCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(if (editingCalc != null) "Edit Settlement" else "Rep Settlement Calculator", style = MaterialTheme.typography.titleMedium)
                                if (editingCalc != null) {
                                    IconButton(onClick = { showDiscardDialog = true }, modifier = Modifier.size(40.dp).testTag("calc_cancel_edit_btn")) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel editing")
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DatePickerField("Cycle Start", dateFrom, { dateFrom = it }, "calc_date_from", Modifier.weight(1f))
                                DatePickerField("Settlement Date", date, { date = it }, "calc_date", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            SarimPillInput(selectedRep, { selectedRep = it }, label = "SALES REP / SELLER", testTag = "calc_rep_input")
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
                            if (returned > 0.0) {
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("store" to "Return to Store", "rep" to "Transfer to Rep", "" to "None").forEach { (key, title) ->
                                        val sel = returnTarget == key
                                        Surface(
                                            onClick = { returnTarget = key },
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (sel) accents.softPillFill else accents.inputFill,
                                            border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                Text(title, style = MaterialTheme.typography.labelSmall, color = if (sel) accents.emerald else accents.textMuted, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                                if (returnTarget == "rep") {
                                    Spacer(Modifier.height(8.dp))
                                    SarimPillInput(returnTargetRep, { returnTargetRep = it }, label = "RECEIVING REP NAME", testTag = "calc_target_rep_input")
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DecimalField("Expired -> CHORA", expiredStr, { expiredStr = it }, "calc_expired_input", Modifier.weight(1f), "kg")
                                DecimalField("Shared / Damage", sharedKgStr, { sharedKgStr = it }, "calc_shared_kg_input", Modifier.weight(1f), "kg")
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DecimalField("Credit Qty", creditQtyStr, { creditQtyStr = it }, "calc_credit_qty_input", Modifier.weight(1f), "kg")
                                DecimalField("Rate / kg", rateStr, { rateStr = it }, "calc_rate_input", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DecimalField("Prev Collection", prevCollStr, { prevCollStr = it }, "calc_prev_coll_input", Modifier.weight(1f))
                                DecimalField("Commission / kg", commissionPerUnitStr, { commissionPerUnitStr = it }, "calc_comm_per_unit_input", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Commissions Paid (${InputGuards.formatMoney(commTotal, currency, masked)})", style = MaterialTheme.typography.titleSmall)
                                OutlinedButton(
                                    onClick = {
                                        val defCat = snapshot.settings.commissionCategories.firstOrNull() ?: "Sales Commission"
                                        commissions.add(defCat to "")
                                    },
                                    modifier = Modifier.testTag("calc_add_comm_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Commission")
                                }
                            }
                            commissions.forEachIndexed { idx, pair ->
                                Spacer(Modifier.height(6.dp))
                                if (snapshot.settings.commissionCategories.isNotEmpty()) {
                                    PickerField(
                                        label = "Commission Category",
                                        selectedLabel = pair.first.ifBlank { "Pick Category" },
                                        items = snapshot.settings.commissionCategories,
                                        itemLabel = { it },
                                        onSelect = { commissions[idx] = it to pair.second },
                                        testTag = "calc_comm_cat_$idx"
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SarimPillInput(pair.first, { commissions[idx] = it to pair.second }, label = "LABEL", modifier = Modifier.weight(1f))
                                    DecimalField("Amount", pair.second, { commissions[idx] = pair.first to it }, "calc_comm_amt_$idx", Modifier.weight(0.85f))
                                    IconButton(onClick = { commissions.removeAt(idx) }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove commission", tint = accents.danger)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Field Expenses (${InputGuards.formatMoney(expTotal, currency, masked)})", style = MaterialTheme.typography.titleSmall)
                                OutlinedButton(
                                    onClick = {
                                        val defCat = snapshot.settings.fieldExpenseCategories.firstOrNull() ?: "Fuel"
                                        expenses.add(defCat to "")
                                    },
                                    modifier = Modifier.testTag("calc_add_exp_btn")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Expense")
                                }
                            }
                            expenses.forEachIndexed { idx, pair ->
                                Spacer(Modifier.height(6.dp))
                                if (snapshot.settings.fieldExpenseCategories.isNotEmpty()) {
                                    PickerField(
                                        label = "Expense Category",
                                        selectedLabel = pair.first.ifBlank { "Pick Category" },
                                        items = snapshot.settings.fieldExpenseCategories,
                                        itemLabel = { it },
                                        onSelect = { expenses[idx] = it to pair.second },
                                        testTag = "calc_exp_cat_$idx"
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    SarimPillInput(pair.first, { expenses[idx] = it to pair.second }, label = "ITEM", modifier = Modifier.weight(1f))
                                    DecimalField("Amount", pair.second, { expenses[idx] = pair.first to it }, "calc_exp_amt_$idx", Modifier.weight(0.85f))
                                    IconButton(onClick = { expenses.removeAt(idx) }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove expense", tint = accents.danger)
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            DecimalField("Physical Cash Handed Over", physicalCashStr, { physicalCashStr = it }, "calc_physical_cash_input")
                            if (editingCalc == null) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Add cash, expenses & commission to Daily Ledger", style = MaterialTheme.typography.bodyMedium)
                                    Switch(checked = addToLedger, onCheckedChange = { addToLedger = it }, modifier = Modifier.testTag("calc_ledger_switch"))
                                }
                            }
                            SarimPillInput(notes, { notes = it.take(500) }, label = "SETTLEMENT NOTES", testTag = "calc_notes_input")
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("Net Sold", InputGuards.formatKg(netSold), accents.emerald, Modifier.weight(1f))
                                SarimHalfRingKpiBox("Cash Qty", InputGuards.formatKg(cashQty), accents.cyan, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            if (commPerUnit > 0.0 || commTotal > 0.0) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SarimHalfRingKpiBox("Gross Comm", InputGuards.formatMoney(grossCommission, currency, masked), accents.cyan, Modifier.weight(1f))
                                    SarimHalfRingKpiBox("Comm Payable", InputGuards.formatMoney(commissionPayable, currency, masked), accents.gold, Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("Expected Cash", InputGuards.formatMoney(expectedCash, currency, masked), accents.cyan, Modifier.weight(1f))
                                SarimHalfRingKpiBox("Short / Over", InputGuards.formatMoney(variance, currency, masked), if (abs(variance) <= 1.0) accents.emerald else accents.gold, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(14.dp))
                            SarimGradientButton(
                                text = if (editingCalc != null) "Update Settlement" else "Finalize Settlement",
                                onClick = {
                                    when {
                                        editingCalc != null -> confirmEditDiffDialog = true
                                        abs(variance) > 1.0 -> confirmVarianceDialog = true
                                        else -> commitSettlement()
                                    }
                                },
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

                if (confirmEditDiffDialog && editingCalc != null) {
                    val o = editingCalc!!
                    EditDiffConfirmDialog(
                        title = "Update Calculator Entry?",
                        rows = listOf(
                            Triple("Date", o.date, date),
                            Triple("Rep", o.seller, selectedRep),
                            Triple("Total Sold", InputGuards.formatKg(o.totalSold), InputGuards.formatKg(sold)),
                            Triple("Returned", InputGuards.formatKg(o.returned), InputGuards.formatKg(returned)),
                            Triple("Expired", InputGuards.formatKg(o.expired), InputGuards.formatKg(expired)),
                            Triple("Shared", InputGuards.formatKg(o.shared), InputGuards.formatKg(sharedKg)),
                            Triple("Credit Qty", InputGuards.formatKg(o.creditQty), InputGuards.formatKg(creditQty)),
                            Triple("Prev Collection", InputGuards.fmtNum(o.prevColl), InputGuards.fmtNum(prevColl)),
                            Triple("Field Expenses", InputGuards.fmtNum(o.fieldExpenses), InputGuards.fmtNum(expTotal)),
                            Triple("Comm / unit", InputGuards.fmtNum(o.commissionPerUnit), InputGuards.fmtNum(commPerUnit)),
                            Triple("Comm Paid", InputGuards.fmtNum(o.commissionPaid), InputGuards.fmtNum(commTotal)),
                            Triple("Cash Received", InputGuards.fmtNum(o.received), InputGuards.fmtNum(physicalCash))
                        ),
                        onConfirm = {
                            confirmEditDiffDialog = false
                            commitSettlement()
                        },
                        onDismiss = { confirmEditDiffDialog = false }
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
                                Text(InputGuards.formatMoney(balance, currency, masked), color = if (balance > 0.01) accents.gold else accents.emerald, style = MaterialTheme.typography.titleMedium)
                            }
                            Text("Gross Credit ${InputGuards.formatMoney(totalCr, currency, masked)}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    selectedCalc?.let { c ->
        CalculatorDetailBottomSheet(
            record = c,
            snapshot = snapshot,
            currency = currency,
            masked = masked,
            vm = vm,
            onDismiss = { selectedCalc = null },
            onEditClick = { rec ->
                editingCalc = rec
                date = rec.date
                dateFrom = rec.dateFrom.ifBlank { rec.date }
                selectedRep = rec.seller
                selectedStore = activeStores.firstOrNull { it.key.equals(rec.returnStore, true) } ?: selectedStore
                soldStr = rec.totalSold.toString()
                returnStr = rec.returned.toString()
                returnTarget = when (rec.returnType.uppercase()) {
                    "TRANSFER" -> "rep"
                    "RETURN" -> "store"
                    else -> if (rec.returnRep.isNotBlank()) "rep" else "store"
                }
                returnTargetRep = rec.returnRep
                expiredStr = rec.expired.toString()
                sharedKgStr = rec.shared.toString()
                creditQtyStr = rec.creditQty.toString()
                prevCollStr = rec.prevColl.toString()
                commissionPerUnitStr = rec.commissionPerUnit.toString()
                rateStr = rec.unitPrice.toString()
                physicalCashStr = rec.received.toString()
                commissions.clear()
                rec.commissionPaidItems.forEach { m ->
                    commissions.add((m["label"]?.toString() ?: "Commission") to ((m["amount"] as? Number)?.toDouble()?.toString() ?: ""))
                }
                expenses.clear()
                rec.fieldExpenseItems.forEach { m ->
                    expenses.add((m["label"]?.toString() ?: "Expense") to ((m["amount"] as? Number)?.toDouble()?.toString() ?: ""))
                }
                notes = rec.notes
                selectedCalc = null
                subTab = 1
            }
        )
    }

    if (showDiscardDialog) {
        DiscardEditDialog(
            onDiscard = {
                showDiscardDialog = false
                resetForm()
            },
            onDismiss = { showDiscardDialog = false }
        )
    }
}
