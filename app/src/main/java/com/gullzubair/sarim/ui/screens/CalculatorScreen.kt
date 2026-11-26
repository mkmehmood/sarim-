package com.gullzubair.sarim.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.CalculatorRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.CalculatorDetailBottomSheet
import com.gullzubair.sarim.ui.components.CalculatorItemListDialog
import com.gullzubair.sarim.ui.components.CalculatorPerformanceChartCard
import com.gullzubair.sarim.ui.components.ConfirmRequest
import com.gullzubair.sarim.ui.components.ConfirmRequestHost
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.DialogTone
import com.gullzubair.sarim.ui.components.DiscardEditDialog
import com.gullzubair.sarim.ui.components.EditDiffConfirmDialog
import com.gullzubair.sarim.ui.components.EditedBadge
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.OverviewDonutSummaryCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimConfirmDialog
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimNoteBanner
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimYearMonthDayTree
import com.gullzubair.sarim.ui.components.SimpleBarChart
import com.gullzubair.sarim.ui.components.TabHeading
import com.gullzubair.sarim.ui.components.blockingAlert
import com.gullzubair.sarim.ui.components.saveConfirmRequest
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import kotlin.math.abs

private data class CalcRangeTotals(
    val sold: Double = 0.0,
    val ret: Double = 0.0,
    val exp: Double = 0.0,
    val shared: Double = 0.0,
    val cost: Double = 0.0,
    val rev: Double = 0.0,
    val prof: Double = 0.0,
    val giv: Double = 0.0,
    val coll: Double = 0.0,
    val fieldExp: Double = 0.0,
    val commissionPaid: Double = 0.0,
    val cashQty: Double = 0.0,
    val credQty: Double = 0.0,
    val expected: Double = 0.0,
    val received: Double = 0.0
)

private fun List<CalculatorRecord>.aggregateCalcTotals(): CalcRangeTotals {
    var sold = 0.0
    var ret = 0.0
    var exp = 0.0
    var shared = 0.0
    var cost = 0.0
    var rev = 0.0
    var prof = 0.0
    var giv = 0.0
    var coll = 0.0
    var fieldExp = 0.0
    var commPaid = 0.0
    var cashQty = 0.0
    var credQty = 0.0
    var expected = 0.0
    var received = 0.0
    forEach { h ->
        sold += h.totalSold
        ret += h.returned
        exp += h.expired
        shared += h.shared
        cost += h.totalCost
        rev += h.revenue
        prof += h.profit
        giv += h.creditValue
        coll += h.prevColl
        fieldExp += h.fieldExpenses
        commPaid += h.commissionPaid
        cashQty += h.cashQty
        credQty += h.creditQty
        expected += h.totalExpected
        received += h.received
    }
    return CalcRangeTotals(sold, ret, exp, shared, cost, rev, prof, giv, coll, fieldExp, commPaid, cashQty, credQty, expected, received)
}

@Composable
fun CalculatorScreen(snapshot: WorkspaceSnapshot, todayIso: String, vm: SarimViewModel) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val masked = session.restrictMoney || snapshot.settings.hideSensitiveMoney
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

    var editingCalc by remember { mutableStateOf<CalculatorRecord?>(null) }
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }

    // Selector & date range state
    var selectedSeller by remember { mutableStateOf("COMBINED") } // "COMBINED" or rep name
    var dateFrom by remember(todayIso) { mutableStateOf(todayIso) }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var selectedStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }

    // Entry fields
    var soldStr by remember { mutableStateOf("") }
    var returnStr by remember { mutableStateOf("") }
    var returnTarget by remember { mutableStateOf("store") }
    var returnTargetRep by remember { mutableStateOf("") }
    var expiredStr by remember { mutableStateOf("") }
    var sharedKgStr by remember { mutableStateOf("") }
    var creditQtyStr by remember { mutableStateOf("") }
    var prevCollStr by remember { mutableStateOf("") }
    var commissionPerUnitStr by remember { mutableStateOf("") }
    var rateStr by remember(selectedStore) { mutableStateOf(selectedStore?.salePrice?.takeIf { it > 0 }?.toString() ?: "") }
    var physicalCashStr by remember { mutableStateOf("") }
    var addToLedger by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    val commissions = remember { mutableStateListOf<Pair<String, String>>() }
    val expenses = remember { mutableStateListOf<Pair<String, String>>() }

    // Popups & confirmation dialogs
    var showExpensePopup by remember { mutableStateOf(false) }
    var showCommissionPopup by remember { mutableStateOf(false) }
    var confirmVarianceDialog by remember { mutableStateOf(false) }
    var confirmEditDiffDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var selectedCalc by remember { mutableStateOf<CalculatorRecord?>(null) }

    // Analytics & Overview state
    var compMode by remember { mutableStateOf("all") } // week | month | year | all
    var compMetric by remember { mutableStateOf("prof") } // prof | rev | sold
    var perfMode by remember { mutableStateOf("day") } // day | week | month | year | all

    fun resetForm() {
        editingCalc = null
        date = todayIso
        dateFrom = todayIso
        soldStr = ""
        returnStr = ""
        returnTarget = "store"
        returnTargetRep = ""
        expiredStr = ""
        sharedKgStr = ""
        creditQtyStr = ""
        prevCollStr = ""
        commissionPerUnitStr = ""
        physicalCashStr = ""
        commissions.clear()
        expenses.clear()
        notes = ""
        addToLedger = false
    }

    BackHandler(enabled = editingCalc != null) {
        showDiscardDialog = true
    }

    val activeCalc = remember(snapshot.calculator) {
        snapshot.calculator.filter { it.deletedAt == null }
            .sortedWith(compareByDescending<CalculatorRecord> { it.date }.thenByDescending { it.timestamp })
    }

    val isCombined = selectedSeller == "COMBINED"

    // Auto-fill total sold from unreconciled Sales deliveries when a rep is chosen
    LaunchedEffect(selectedSeller, selectedStore, dateFrom, date, snapshot.sales, editingCalc) {
        if (editingCalc == null && !isCombined && selectedStore != null) {
            val fromEff = dateFrom.ifBlank { date }.let { if (it <= date) it else date }
            val autoKg = FinanceEngine.getUnreconciledRepDeliveriesKg(
                repName = selectedSeller,
                storeKey = selectedStore!!.key,
                dateFrom = fromEff,
                dateTo = date,
                sales = snapshot.sales
            )
            soldStr = if (autoKg > 0.0) InputGuards.fmtNum(autoKg).replace(",", "") else ""
        }
    }

    // Form calculations
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

    // Scoped history for selected seller or combined
    val scopedCalc = remember(activeCalc, selectedSeller) {
        if (isCombined) activeCalc else activeCalc.filter { it.seller.equals(selectedSeller, true) }
    }

    // Period filtering for Performance Overview & History
    val refLocalDate = remember(date) { DateUtils.parseLocalDate(date) ?: java.time.LocalDate.now() }
    val perfFiltered = remember(scopedCalc, perfMode, date, refLocalDate) {
        val weekStart = refLocalDate.minusDays(6).toString()
        val ymPrefix = date.take(7)
        val yPrefix = date.take(4)
        scopedCalc.filter { h ->
            val hd = h.date.take(10)
            when (perfMode) {
                "day" -> hd == date
                "week" -> hd in weekStart..date
                "month" -> hd.startsWith(ymPrefix)
                "year" -> hd.startsWith(yPrefix)
                else -> true
            }
        }
    }
    val perfTotals = remember(perfFiltered) { perfFiltered.aggregateCalcTotals() }

    // Market Debt Tracker for selected seller (or Combined)
    val debtCreditIssued = remember(scopedCalc) { scopedCalc.sumOf { it.creditValue } }
    val debtRecovered = remember(scopedCalc) { scopedCalc.sumOf { it.prevColl } }
    val debtBalance = InputGuards.round2(debtCreditIssued - debtRecovered)

    // Comparison analytics for COMBINED mode
    val compByRep = remember(activeCalc, repList, compMode, date, refLocalDate) {
        val weekStart = refLocalDate.minusDays(6).toString()
        val ymPrefix = date.take(7)
        val yPrefix = date.take(4)
        val inPeriod = activeCalc.filter { h ->
            val hd = h.date.take(10)
            when (compMode) {
                "week" -> hd in weekStart..date
                "month" -> hd.startsWith(ymPrefix)
                "year" -> hd.startsWith(yPrefix)
                else -> true
            }
        }
        repList.associateWith { rep ->
            inPeriod.filter { it.seller.equals(rep, true) }.aggregateCalcTotals()
        }
    }

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
                seller = selectedSeller,
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
                seller = selectedSeller,
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

    val sellerLabel = if (isCombined) "Combined" else selectedSeller
    val sellerOptions = listOf("COMBINED" to "COMPARISON") + repList.map { it to it }
    val selectedSellerOptionLabel = sellerOptions.firstOrNull { it.first.equals(selectedSeller, true) }?.second ?: "COMPARISON"
    val rangeDisplayLabel = remember(dateFrom, date) {
        when {
            dateFrom.isBlank() || date.isBlank() -> "Select dates"
            dateFrom == date -> DateUtils.formatPretty(date)
            else -> "${DateUtils.formatPretty(dateFrom)} → ${DateUtils.formatPretty(date)}"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TabHeading("Calculator") }

        // 1. TOP SELECTOR + ENTRY / COMPARISON CARD
        item {
            SarimCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (editingCalc != null) "Edit Entry (${editingCalc!!.seller})" else "Sales Representative",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (editingCalc != null) accents.cyan else accents.textMuted
                    )
                    if (editingCalc != null) {
                        IconButton(onClick = { showDiscardDialog = true }, modifier = Modifier.size(40.dp).testTag("calc_cancel_edit_btn")) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel edit")
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                PickerField(
                    label = "Sales Representative",
                    selectedLabel = selectedSellerOptionLabel,
                    options = sellerOptions,
                    onSelect = { selectedSeller = it },
                    testTag = "calc_seller_picker"
                )
                Spacer(Modifier.height(10.dp))
                Text("Date Range · $rangeDisplayLabel", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DatePickerField("From", dateFrom, { dateFrom = it }, "calc_date_from", Modifier.weight(1f))
                    DatePickerField("To", date, { date = it }, "calc_date", Modifier.weight(1f))
                }

                // Entry section shown when a rep is selected
                if (!isCombined) {
                    editingCalc?.let { orig ->
                        val isQuick = selectedSeller == orig.seller &&
                            date == orig.date &&
                            InputGuards.round2(sold) == InputGuards.round2(orig.totalSold) &&
                            InputGuards.round2(returned) == InputGuards.round2(orig.returned) &&
                            InputGuards.round2(expired) == InputGuards.round2(orig.expired)
                        Spacer(Modifier.height(10.dp))
                        SarimNoteBanner(
                            text = if (isQuick) {
                                "Editing ${orig.seller} (${orig.date}) — Quick Edit preserves existing sales/return links."
                            } else {
                                "Editing ${orig.seller} (${orig.date}) — Full Edit reverses and re-applies stock & sales links."
                            }
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Product Quantities", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                    Spacer(Modifier.height(8.dp))
                    PickerField(
                        label = "Store",
                        selectedLabel = selectedStore?.name ?: "Select Store",
                        items = activeStores,
                        itemLabel = { it.name },
                        onSelect = {
                            selectedStore = it
                            if (it.salePrice > 0.0) rateStr = it.salePrice.toString()
                        },
                        testTag = "calc_store_picker"
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DecimalField("Total Sold Qty", soldStr, { soldStr = it }, "calc_sold_input", Modifier.weight(1f), "kg")
                        DecimalField("Returned Qty", returnStr, { returnStr = it }, "calc_return_input", Modifier.weight(1f), "kg")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DecimalField("Expired / Damage Qty", expiredStr, { expiredStr = it }, "calc_expired_input", Modifier.weight(1f), "kg")
                        DecimalField("Shared (Deduction)", sharedKgStr, { sharedKgStr = it }, "calc_shared_kg_input", Modifier.weight(1f), "kg")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = accents.inputFill,
                            border = BorderStroke(1.dp, accents.borderSubtle),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text("Net Sold Qty", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                Text(
                                    InputGuards.formatKg(netSold),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = accents.emerald
                                )
                            }
                        }
                        DecimalField("Credit Sales Qty", creditQtyStr, { creditQtyStr = it }, "calc_credit_qty_input", Modifier.weight(1f), "kg")
                    }

                    if (returned > 0.0) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = accents.inputFill,
                            border = BorderStroke(1.dp, accents.cyan.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Return Stock To Physical Inventory:", style = MaterialTheme.typography.labelLarge, color = accents.cyan)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    activeStores.forEach { st ->
                                        val isChosen = returnTarget == "store" && selectedStore?.key.equals(st.key, true)
                                        Surface(
                                            onClick = {
                                                returnTarget = "store"
                                                selectedStore = st
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isChosen) accents.softPillFill else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, if (isChosen) accents.emerald else accents.borderSubtle),
                                            modifier = Modifier.weight(1f).testTag("ret_store_${st.key.lowercase()}")
                                        ) {
                                            Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                                Text(st.name.uppercase(), style = MaterialTheme.typography.labelMedium, color = if (isChosen) accents.emerald else accents.textMuted)
                                            }
                                        }
                                    }
                                    val isRepTransfer = returnTarget == "rep"
                                    Surface(
                                        onClick = { returnTarget = "rep" },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isRepTransfer) accents.softPillFill else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, if (isRepTransfer) accents.emerald else accents.borderSubtle),
                                        modifier = Modifier.weight(1f).testTag("ret_rep_btn")
                                    ) {
                                        Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                            Text("Rep Transfer", style = MaterialTheme.typography.labelMedium, color = if (isRepTransfer) accents.emerald else accents.textMuted)
                                        }
                                    }
                                }
                                if (returnTarget == "rep") {
                                    Spacer(Modifier.height(8.dp))
                                    SarimPillInput(returnTargetRep, { returnTargetRep = it }, label = "RECEIVING REP", testTag = "calc_target_rep_input")
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "* This will increase the store's stock level without creating a new production record.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted
                                )
                            }
                        }
                    }

                    if (expired > 0.0) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = accents.inputFill,
                            border = BorderStroke(1.dp, accents.gold.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Expired Stock — Added to CHORA Raw Material:", style = MaterialTheme.typography.labelLarge, color = accents.gold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "* Expired product will be added to the CHORA raw material inventory. It will NOT be added to production stock.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Commission", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DecimalField("Commission / Unit", commissionPerUnitStr, { commissionPerUnitStr = it }, "calc_comm_per_unit_input", Modifier.weight(1f))
                        Surface(
                            onClick = { showCommissionPopup = true },
                            shape = RoundedCornerShape(16.dp),
                            color = accents.inputFill,
                            border = BorderStroke(1.dp, accents.borderSubtle),
                            modifier = Modifier.weight(1f).testTag("calc_open_comm_popup")
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text("Commission Paid (Advance/Paid)", style = MaterialTheme.typography.labelSmall, color = accents.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (commTotal > 0.0) InputGuards.formatMoney(commTotal, currency, masked) else "Tap to add commission",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (commTotal > 0.0) accents.emerald else accents.textMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("Collections & Handover Cash", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DecimalField("Recovered Credit", prevCollStr, { prevCollStr = it }, "calc_prev_coll_input", Modifier.weight(1f))
                        Surface(
                            onClick = { showExpensePopup = true },
                            shape = RoundedCornerShape(16.dp),
                            color = accents.inputFill,
                            border = BorderStroke(1.dp, accents.borderSubtle),
                            modifier = Modifier.weight(1f).testTag("calc_open_exp_popup")
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text("Field Expenses", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                Text(
                                    if (expTotal > 0.0) InputGuards.formatMoney(expTotal, currency, masked) else "Tap to add expenses",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (expTotal > 0.0) accents.danger else accents.textMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    DecimalField("Physical Cash Received from Rep", physicalCashStr, { physicalCashStr = it }, "calc_physical_cash_input")

                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val discText = when {
                                abs(variance) < 0.01 -> "PERFECT MATCH"
                                variance < 0 -> "SHORT: ${InputGuards.formatMoney(abs(variance), currency, masked)}"
                                else -> "OVER: ${InputGuards.formatMoney(variance, currency, masked)}"
                            }
                            val discColor = if (variance < -0.01) accents.danger else accents.emerald
                            ResultKvRow("Expected Cash:", InputGuards.formatMoney(expectedCash, currency, masked), accents.emerald)
                            ResultKvRow("Status:", discText, discColor)
                            ResultKvRow("Gross Commission Earned:", InputGuards.formatMoney(grossCommission, currency, masked), accents.emerald)
                            ResultKvRow("Commission Paid:", InputGuards.formatMoney(commTotal, currency, masked), accents.danger)
                            ResultKvRow(
                                "Commission Payable:",
                                InputGuards.formatMoney(commissionPayable, currency, masked),
                                if (commissionPayable <= 0.01) accents.emerald else accents.danger
                            )
                        }
                    }

                    if (editingCalc == null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { addToLedger = !addToLedger },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = addToLedger,
                                onCheckedChange = { addToLedger = it },
                                modifier = Modifier.testTag("calc_ledger_switch")
                            )
                            Text("Add cash received, expenses and commission to Daily Ledger", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SarimGradientButton(
                        text = if (editingCalc != null) "Update Transaction" else "Save Transaction",
                        onClick = {
                            if (selectedStore == null) {
                                confirmReq = blockingAlert("Warning", "Please configure an active store first."); return@SarimGradientButton
                            }
                            if (sold <= 0.0) {
                                confirmReq = blockingAlert("Warning", "Please enter valid units sold (must be greater than 0)"); return@SarimGradientButton
                            }
                            if (rate <= 0.0) {
                                confirmReq = blockingAlert("Warning", "Please set a sale price in Factory Formulas first"); return@SarimGradientButton
                            }
                            if ((returned + expired + sharedKg) > sold) {
                                confirmReq = blockingAlert("Warning", "Combined returned + expired + shared quantity cannot exceed total sold"); return@SarimGradientButton
                            }
                            if (creditQty > netSold) {
                                confirmReq = blockingAlert("Credit warning", "Credit sales cannot exceed net sold quantity"); return@SarimGradientButton
                            }
                            when {
                                editingCalc != null -> confirmEditDiffDialog = true
                                abs(variance) > 0.01 -> confirmVarianceDialog = true
                                else -> {
                                    val details = buildList {
                                        add("Seller" to selectedSeller)
                                        add("Date" to DateUtils.formatPretty(date))
                                        add("Total Sold" to InputGuards.formatKg(sold))
                                        if (returned > 0.0) add("Returned" to InputGuards.formatKg(returned))
                                        if (expired > 0.0) add("Expired" to InputGuards.formatKg(expired))
                                        add("Credit Sales" to InputGuards.formatKg(creditQty))
                                        add("Cash Received" to InputGuards.formatMoney(physicalCash, currency, masked))
                                    }
                                    confirmReq = saveConfirmRequest(
                                        label = "Settlement",
                                        details = details,
                                        gated = session.mode != "admin",
                                        tag = "calc_confirm_save_btn"
                                    ) { commitSettlement() }
                                }
                            }
                        },
                        testTag = "calc_save_btn"
                    )
                }
            }
        }

        // 2. COMPARISON SECTION (when COMBINED and !masked)
        if (isCombined && !masked) {
            item {
                SarimCard {
                    Text("Comparison", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("week" to "Week", "month" to "Month", "year" to "Year", "all" to "All Time").forEach { (k, lbl) ->
                            val sel = compMode == k
                            Surface(
                                onClick = { compMode = k },
                                shape = RoundedCornerShape(12.dp),
                                color = if (sel) accents.softPillFill else accents.inputFill,
                                border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                modifier = Modifier.weight(1f).testTag("comp_mode_$k")
                            ) {
                                Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                    Text(lbl, style = MaterialTheme.typography.labelSmall, color = if (sel) accents.emerald else accents.textMuted)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val metricOpts = listOf(
                        "prof" to "Net Profit (Value)",
                        "rev" to "Gross Revenue (Value)",
                        "sold" to "Quantity Sold"
                    )
                    PickerField(
                        label = "View Chart For:",
                        selectedLabel = metricOpts.firstOrNull { it.first == compMetric }?.second ?: "Net Profit (Value)",
                        options = metricOpts,
                        onSelect = { compMetric = it },
                        testTag = "comp_metric_picker"
                    )
                    Spacer(Modifier.height(10.dp))
                    val barSeries = repList.map { r ->
                        val t = compByRep[r] ?: CalcRangeTotals()
                        val v = when (compMetric) {
                            "rev" -> t.rev
                            "sold" -> t.sold
                            else -> t.prof
                        }
                        r to v
                    }
                    SimpleBarChart(
                        title = "COMPETITION",
                        items = barSeries,
                        barColor = accents.emerald,
                        currency = if (compMetric == "sold") "kg" else currency,
                        masked = masked && compMetric != "sold"
                    )
                    Spacer(Modifier.height(10.dp))
                    val totalRevAll = compByRep.values.sumOf { it.rev }
                    val totalGivAll = compByRep.values.sumOf { it.giv }
                    val totalCashValAll = (totalRevAll - totalGivAll).coerceAtLeast(0.0)
                    val totalSoldAll = compByRep.values.sumOf { it.sold }
                    val totalRetAll = compByRep.values.sumOf { it.ret }
                    val avgPrice = if (totalSoldAll > 0.0) totalRevAll / totalSoldAll else 0.0
                    val totalRetValAll = totalRetAll * avgPrice
                    OverviewDonutSummaryCard(
                        title = "MARKET COMPOSITION",
                        items = listOf(
                            "Cash Sale Value" to totalCashValAll,
                            "Credit Value" to totalGivAll,
                            "Return Value" to totalRetValAll
                        ),
                        currency = currency,
                        masked = masked
                    )

                    if (repList.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        val tableMetrics: List<Triple<String, (CalcRangeTotals) -> Double, Color>> = listOf(
                            Triple("Qty Sold", { it.sold }, MaterialTheme.colorScheme.onSurface),
                            Triple("Returns", { it.ret }, MaterialTheme.colorScheme.onSurface),
                            Triple("Expired (→ CHORA)", { it.exp }, accents.danger),
                            Triple("Shared (Deduction)", { it.shared }, accents.danger),
                            Triple("Total Cost", { it.cost }, accents.danger),
                            Triple("Gross Revenue", { it.rev }, accents.cyan),
                            Triple("Net Profit", { it.prof }, accents.emerald),
                            Triple("Credit Issued", { it.giv }, MaterialTheme.colorScheme.onSurface),
                            Triple("Credit Recovered", { it.coll }, accents.purple),
                            Triple("Field Expenses", { it.fieldExp }, accents.danger),
                            Triple("Commission Paid", { it.commissionPaid }, accents.emerald)
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accents.inputFill)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Metric Comparison", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(136.dp))
                                repList.forEach { rep ->
                                    val short = rep.split(" ").first().lowercase().replaceFirstChar { it.uppercase() }
                                    Text(short, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.width(86.dp))
                                }
                            }
                            tableMetrics.forEach { (lbl, getter, color) ->
                                Row(
                                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(lbl, style = MaterialTheme.typography.bodySmall, color = accents.textMuted, modifier = Modifier.width(136.dp))
                                    repList.forEach { rep ->
                                        val v = getter(compByRep[rep] ?: CalcRangeTotals())
                                        val isQty = lbl == "Qty Sold" || lbl == "Returns" || lbl.startsWith("Expired") || lbl.startsWith("Shared")
                                        val txt = if (isQty) InputGuards.fmtNum(v) else InputGuards.formatMoney(v, currency, masked)
                                        Text(txt, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = color, modifier = Modifier.width(86.dp))
                                    }
                                }
                                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.35f))
                            }
                        }
                    }
                }
            }
        }

        // 3. INDIVIDUAL PERFORMANCE ANALYTICS (when specific rep selected & !masked)
        if (!isCombined && !masked) {
            item {
                CalculatorPerformanceChartCard(
                    history = activeCalc,
                    seller = selectedSeller,
                    referenceDateIso = date,
                    currency = currency,
                    masked = masked
                )
            }
        }

        // 4. PERFORMANCE OVERVIEW + MARKET DEBT TRACKER + CALCULATOR HISTORY
        if (!masked) {
            item {
                SarimCard {
                    Text(
                        "Performance Overview ($sellerLabel)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("day" to "Daily", "week" to "Weekly", "month" to "Monthly", "year" to "Yearly", "all" to "All Time").forEach { (k, lbl) ->
                            val sel = perfMode == k
                            Surface(
                                onClick = { perfMode = k },
                                shape = RoundedCornerShape(12.dp),
                                color = if (sel) accents.softPillFill else accents.inputFill,
                                border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                modifier = Modifier.weight(1f).testTag("calc_po_$k")
                            ) {
                                Box(Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                    Text(lbl, style = MaterialTheme.typography.labelSmall, color = if (sel) accents.emerald else accents.textMuted, maxLines = 1)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val pDisc = InputGuards.round2(perfTotals.received - perfTotals.expected)
                    val discLabel = when {
                        abs(pDisc) < 0.01 -> "Perfect Match"
                        pDisc < 0 -> "Short ${InputGuards.formatMoney(abs(pDisc), currency, masked)}"
                        else -> "Over ${InputGuards.formatMoney(pDisc, currency, masked)}"
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SarimHalfRingKpiBox("Total Sold", InputGuards.formatKg(perfTotals.sold), accents.cyan, Modifier.weight(1f))
                        SarimHalfRingKpiBox("Returned", InputGuards.formatKg(perfTotals.ret), accents.gold, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SarimHalfRingKpiBox("Cash Sales", InputGuards.formatKg(perfTotals.cashQty), accents.emerald, Modifier.weight(1f))
                        SarimHalfRingKpiBox("Credit Sales", InputGuards.formatKg(perfTotals.credQty), accents.gold, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    ResultKvRow("Total Value", InputGuards.formatMoney(perfTotals.rev, currency, masked), accents.cyan)
                    ResultKvRow("Total Cost", InputGuards.formatMoney(perfTotals.cost, currency, masked), accents.danger)
                    ResultKvRow("Net Profit", InputGuards.formatMoney(perfTotals.prof, currency, masked), accents.emerald)
                    ResultKvRow("Credit Issued", InputGuards.formatMoney(perfTotals.giv, currency, masked), accents.gold)
                    ResultKvRow("Cash Recovered", InputGuards.formatMoney(perfTotals.coll, currency, masked), accents.emerald)
                    if (perfTotals.exp > 0.0) ResultKvRow("Expired (→ CHORA)", InputGuards.formatKg(perfTotals.exp), accents.danger)
                    if (perfTotals.shared > 0.0) ResultKvRow("Shared (Deduction)", InputGuards.formatKg(perfTotals.shared), accents.danger)
                    ResultKvRow("Expected Cash", InputGuards.formatMoney(perfTotals.expected, currency, masked), accents.cyan)
                    ResultKvRow("Cash Received", InputGuards.formatMoney(perfTotals.received, currency, masked), accents.emerald)
                    ResultKvRow("Handover Status", discLabel, if (pDisc < -0.01) accents.danger else accents.emerald)
                    if (perfTotals.fieldExp > 0.0) ResultKvRow("Field Expenses", InputGuards.formatMoney(perfTotals.fieldExp, currency, masked), accents.danger)
                    if (perfTotals.commissionPaid > 0.0) ResultKvRow("Commission Paid", InputGuards.formatMoney(perfTotals.commissionPaid, currency, masked), accents.emerald)
                }
            }

            item {
                SarimCard {
                    Text(
                        "$sellerLabel · Market debt tracker",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Current market debt", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                    Text(
                        InputGuards.formatMoney(debtBalance, currency, masked),
                        fontFamily = JakartaFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = if (debtBalance > 0.01) accents.danger else accents.emerald
                    )
                    Spacer(Modifier.height(8.dp))
                    val totalActivity = (debtRecovered + debtBalance.coerceAtLeast(0.0)).coerceAtLeast(1.0)
                    val recPct = ((debtRecovered / totalActivity) * 100.0).toInt().coerceIn(0, 100)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accents.danger.copy(alpha = 0.28f))
                    ) {
                        if (recPct > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth(recPct / 100f)
                                    .height(6.dp)
                                    .background(accents.emerald)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SarimHalfRingKpiBox("Total credit issued", InputGuards.formatMoney(debtCreditIssued, currency, masked), accents.gold, Modifier.weight(1f))
                        SarimHalfRingKpiBox("Cash recovered", InputGuards.formatMoney(debtRecovered, currency, masked), accents.emerald, Modifier.weight(1f))
                    }
                }
            }

            item {
                Text(
                    "Calculator History",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (perfFiltered.isEmpty()) {
                item { EmptyStateCard("No calculator entries found.", "Select a representative above to record a settlement.") }
            } else {
                item {
                    SarimYearMonthDayTree(
                        items = perfFiltered,
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
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showExpensePopup) {
        CalculatorItemListDialog(
            title = "Field Expenses",
            eyebrow = "Expenses",
            caption = "Search or type a category, add the amount and save. The field shows the total paid.",
            placeholder = "Search or type a category (e.g. Fuel)",
            totalLabel = "Total paid",
            categories = snapshot.settings.fieldExpenseCategories,
            items = expenses,
            currency = currency,
            masked = masked,
            onAddItem = { nm, amt -> expenses.add(nm to amt) },
            onRemoveAt = { idx -> expenses.removeAt(idx) },
            onDismiss = { showExpensePopup = false }
        )
    }

    if (showCommissionPopup) {
        CalculatorItemListDialog(
            title = "Commission Paid",
            eyebrow = "Commission",
            caption = "Search or type a category, add the amount and save. The field shows the total paid.",
            placeholder = "Search or type a category (e.g. Advance)",
            totalLabel = "Total paid",
            categories = snapshot.settings.commissionCategories,
            items = commissions,
            currency = currency,
            masked = masked,
            onAddItem = { nm, amt -> commissions.add(nm to amt) },
            onRemoveAt = { idx -> commissions.removeAt(idx) },
            onDismiss = { showCommissionPopup = false }
        )
    }

    if (confirmVarianceDialog) {
        val short = variance < 0.0
        SarimConfirmDialog(
            title = if (short) "Cash is short" else "Cash is over",
            message = "Expected: ${InputGuards.formatMoney(expectedCash, currency, masked)}\n" +
                "Physical: ${InputGuards.formatMoney(physicalCash, currency, masked)}\n" +
                "${if (short) "Short" else "Over"} by ${InputGuards.formatMoney(abs(variance), currency, masked)}. Save anyway?",
            confirmText = "Save anyway",
            onConfirm = {
                confirmVarianceDialog = false
                commitSettlement()
            },
            onDismiss = { confirmVarianceDialog = false }
        )
    }

    if (confirmEditDiffDialog && editingCalc != null) {
        val o = editingCalc!!
        EditDiffConfirmDialog(
            title = "Update Calculator Entry?",
            rows = listOf(
                Triple("Date", o.date, date),
                Triple("Rep", o.seller, selectedSeller),
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
                selectedSeller = rec.seller
                selectedStore = activeStores.firstOrNull { it.key.equals(rec.returnStore, true) } ?: selectedStore
                soldStr = rec.totalSold.toString()
                returnStr = if (rec.returned > 0) rec.returned.toString() else ""
                returnTarget = when (rec.returnType.uppercase()) {
                    "TRANSFER" -> "rep"
                    "RETURN" -> "store"
                    else -> if (rec.returnRep.isNotBlank()) "rep" else "store"
                }
                returnTargetRep = rec.returnRep
                expiredStr = if (rec.expired > 0) rec.expired.toString() else ""
                sharedKgStr = if (rec.shared > 0) rec.shared.toString() else ""
                creditQtyStr = if (rec.creditQty > 0) rec.creditQty.toString() else ""
                prevCollStr = if (rec.prevColl > 0) rec.prevColl.toString() else ""
                commissionPerUnitStr = if (rec.commissionPerUnit > 0) rec.commissionPerUnit.toString() else ""
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

@Composable
private fun ResultKvRow(label: String, value: String, valueColor: Color) {
    val accents = LocalSarimAccents.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = valueColor)
    }
}
