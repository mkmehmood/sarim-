package com.gullzubair.sarim.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import com.gullzubair.sarim.ui.components.DateRangePickerField
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
import com.gullzubair.sarim.ui.components.TabHeading
import com.gullzubair.sarim.ui.components.blockingAlert
import com.gullzubair.sarim.ui.components.dashedBorder
import com.gullzubair.sarim.ui.components.saveConfirmRequest
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.theme.PlayfairFont
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

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
                if (editingCalc != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Edit Entry (${editingCalc!!.seller})",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = accents.cyan
                        )
                        IconButton(onClick = { showDiscardDialog = true }, modifier = Modifier.size(48.dp).testTag("calc_cancel_edit_btn")) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel edit")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    PickerField(
                        label = "Sales Representative",
                        selectedLabel = selectedSellerOptionLabel,
                        items = sellerOptions,
                        itemLabel = { it.second },
                        onSelect = { selectedSeller = it.first },
                        testTag = "calc_seller_picker",
                        modifier = Modifier.weight(0.46f)
                    )
                    DateRangePickerField(
                        label = "Date Range",
                        fromIso = dateFrom,
                        toIso = date,
                        onRangeSelected = { f, t ->
                            dateFrom = f
                            date = t
                        },
                        testTag = "calc_date",
                        modifier = Modifier.weight(0.54f)
                    )
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
                    Text(
                        "Product Quantities",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = accents.emerald
                    )
                    if (activeStores.size > 1) {
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
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CalcDashedDecimalField("TOTAL SOLD QTY", soldStr, { soldStr = it }, "calc_sold_input", Modifier.weight(1f))
                        CalcDashedDecimalField("RETURNED QTY", returnStr, { returnStr = it }, "calc_return_input", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CalcDashedDecimalField("EXPIRED / DAMAGE QTY", expiredStr, { expiredStr = it }, "calc_expired_input", Modifier.weight(1f))
                        DecimalField("Shared (Deduction)", sharedKgStr, { sharedKgStr = it }, "calc_shared_kg_input", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth(0.5f).padding(end = 5.dp)) {
                        Text(
                            text = "NET SOLD QTY",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                            color = accents.textMuted,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(accents.inputFill.copy(alpha = 0.7f))
                                .dashedBorder(1.1.dp, accents.inputBorder, cornerRadius = 18.dp)
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = InputGuards.fmtNum(netSold),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (returned > 0.0) {
                        Spacer(Modifier.height(12.dp))
                        val amberBg = if (accents.isDark) accents.gold.copy(alpha = 0.09f) else Color(0xFFFFF7ED)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = amberBg,
                            border = BorderStroke(1.dp, accents.gold.copy(alpha = 0.65f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "RETURN STOCK TO PHYSICAL INVENTORY:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = accents.gold
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    activeStores.forEach { st ->
                                        val isChosen = returnTarget == "store" && selectedStore?.key.equals(st.key, true)
                                        Surface(
                                            onClick = {
                                                returnTarget = "store"
                                                selectedStore = st
                                            },
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (isChosen) accents.softPillFill else MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, if (isChosen) accents.emerald else accents.borderSubtle),
                                            modifier = Modifier.testTag("ret_store_${st.key.lowercase()}")
                                        ) {
                                            Text(
                                                st.name.uppercase(),
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isChosen) FontWeight.Bold else FontWeight.SemiBold),
                                                color = if (isChosen) accents.emerald else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                                            )
                                        }
                                    }
                                    val isRepTransfer = returnTarget == "rep"
                                    Surface(
                                        onClick = { returnTarget = "rep" },
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (isRepTransfer) accents.softPillFill else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, if (isRepTransfer) accents.emerald else accents.borderSubtle),
                                        modifier = Modifier.testTag("ret_rep_btn")
                                    ) {
                                        Text(
                                            "Rep Transfer",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isRepTransfer) FontWeight.Bold else FontWeight.SemiBold),
                                            color = if (isRepTransfer) accents.emerald else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                                if (returnTarget == "rep") {
                                    Spacer(Modifier.height(8.dp))
                                    SarimPillInput(returnTargetRep, { returnTargetRep = it }, label = "RECEIVING REP", testTag = "calc_target_rep_input")
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "* This will increase the store's stock level without creating a new production record.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth(0.5f).padding(end = 5.dp)) {
                        CalcTintedDecimalField(
                            label = "CREDIT SALES QTY",
                            value = creditQtyStr,
                            onValueChange = { creditQtyStr = it },
                            testTag = "calc_credit_qty_input"
                        )
                    }

                    if (expired > 0.0) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
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
                    Text(
                        "Commission",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = accents.emerald
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        DecimalField("Commission / Unit", commissionPerUnitStr, { commissionPerUnitStr = it }, "calc_comm_per_unit_input", Modifier.weight(1f))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "COMMISSION PAID (ADVANCE/PAID)",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                                color = accents.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(accents.inputFill.copy(alpha = 0.65f))
                                    .dashedBorder(1.1.dp, accents.inputBorder, cornerRadius = 18.dp)
                                    .clickable { showCommissionPopup = true }
                                    .padding(horizontal = 14.dp, vertical = 14.dp)
                                    .testTag("calc_open_comm_popup"),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = if (commTotal > 0.0) InputGuards.formatMoney(commTotal, currency, masked) else "Tap to add commission",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (commTotal > 0.0) accents.emerald else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Collections & Handover Cash",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                        color = accents.emerald
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        CalcTintedDecimalField(
                            label = "RECOVERED CREDIT",
                            value = prevCollStr,
                            onValueChange = { prevCollStr = it },
                            testTag = "calc_prev_coll_input",
                            modifier = Modifier.weight(1f)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "FIELD EXPENSES",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                                color = accents.textMuted,
                                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 52.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(accents.inputFill.copy(alpha = 0.65f))
                                    .dashedBorder(1.1.dp, accents.inputBorder, cornerRadius = 18.dp)
                                    .clickable { showExpensePopup = true }
                                    .padding(horizontal = 14.dp, vertical = 14.dp)
                                    .testTag("calc_open_exp_popup"),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = if (expTotal > 0.0) InputGuards.formatMoney(expTotal, currency, masked) else "Tap to add expenses",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (expTotal > 0.0) accents.danger else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.5f).padding(end = 5.dp)) {
                        DecimalField("Physical Cash Received from Rep", physicalCashStr, { physicalCashStr = it }, "calc_physical_cash_input")
                    }

                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Text(
                                "ADD CASH RECEIVED, EXPENSES AND COMMISSION TO DAILY LEDGER",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
                            )
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

                // Comparison inline inside the top card when COMBINED and !masked
                if (isCombined && !masked) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Comparison",
                            fontFamily = PlayfairFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            listOf("week" to "Week", "month" to "Month", "year" to "Year", "all" to "All Time").forEach { (k, lbl) ->
                                val sel = compMode == k
                                Surface(
                                    onClick = { compMode = k },
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (sel) accents.softPillFill else accents.inputFill,
                                    border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 32.dp)
                                        .testTag("comp_mode_$k")
                                ) {
                                    Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            lbl,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (sel) accents.emerald else accents.textMuted,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val metricOpts = listOf(
                        "prof" to "Net Profit (Value)",
                        "rev" to "Gross Revenue (Value)",
                        "sold" to "Quantity Sold"
                    )
                    PickerField(
                        label = "View Chart For:",
                        selectedLabel = metricOpts.firstOrNull { it.first == compMetric }?.second ?: "Net Profit (Value)",
                        items = metricOpts,
                        itemLabel = { it.second },
                        onSelect = { compMetric = it.first },
                        testTag = "comp_metric_picker"
                    )
                    Spacer(Modifier.height(12.dp))

                    val barSeries = repList.map { r ->
                        val t = compByRep[r] ?: CalcRangeTotals()
                        val v = when (compMetric) {
                            "rev" -> t.rev
                            "sold" -> t.sold
                            else -> t.prof
                        }
                        r to v
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = accents.inputFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "COMPETITION",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = accents.emerald
                            )
                            Spacer(Modifier.height(10.dp))
                            if (barSeries.isEmpty() || barSeries.all { abs(it.second) < 0.01 }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 22.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No data yet", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                }
                            } else {
                                val maxVal = (barSeries.maxOfOrNull { max(0.0, it.second) } ?: 1.0).coerceAtLeast(1.0)
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    barSeries.forEach { (repName, value) ->
                                        val frac = (max(0.0, value) / maxVal).toFloat().coerceIn(0.03f, 1f)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                repName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.width(86.dp)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(frac)
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(accents.emerald)
                                                )
                                            }
                                            Text(
                                                text = if (compMetric == "sold") InputGuards.formatKg(value) else InputGuards.formatMoney(value, currency, masked),
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = accents.emerald
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    val totalRevAll = compByRep.values.sumOf { it.rev }
                    val totalGivAll = compByRep.values.sumOf { it.giv }
                    val totalCashValAll = (totalRevAll - totalGivAll).coerceAtLeast(0.0)
                    val totalSoldAll = compByRep.values.sumOf { it.sold }
                    val totalRetAll = compByRep.values.sumOf { it.ret }
                    val avgPrice = if (totalSoldAll > 0.0) totalRevAll / totalSoldAll else 0.0
                    val totalRetValAll = totalRetAll * avgPrice
                    val marketItems = listOf(
                        "Cash Sale Value" to totalCashValAll,
                        "Credit Value" to totalGivAll,
                        "Return Value" to totalRetValAll
                    )
                    if (marketItems.sumOf { it.second } <= 0.0) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = accents.inputFill,
                            border = BorderStroke(1.dp, accents.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "MARKET COMPOSITION",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.6.sp
                                    ),
                                    color = accents.emerald
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Market Composition",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 22.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Nothing recorded in this period", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                }
                            }
                        }
                    } else {
                        OverviewDonutSummaryCard(
                            title = "MARKET COMPOSITION",
                            items = marketItems,
                            currency = currency,
                            masked = masked
                        )
                    }

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
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = accents.inputFill,
                            border = BorderStroke(1.dp, accents.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "METRIC",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.width(140.dp)
                                    )
                                    repList.forEach { rep ->
                                        val short = rep.split(" ").first().uppercase()
                                        Text(
                                            short,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = accents.textMuted,
                                            modifier = Modifier.width(88.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                                tableMetrics.forEach { (lbl, getter, color) ->
                                    val rowBg = when (lbl) {
                                        "Qty Sold" -> accents.emerald.copy(alpha = 0.08f)
                                        "Net Profit" -> accents.gold.copy(alpha = 0.10f)
                                        else -> Color.Transparent
                                    }
                                    Row(
                                        Modifier
                                            .background(rowBg)
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            lbl,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                            modifier = Modifier.width(140.dp)
                                        )
                                        repList.forEach { rep ->
                                            val v = getter(compByRep[rep] ?: CalcRangeTotals())
                                            val isQty = lbl == "Qty Sold" || lbl == "Returns" || lbl.startsWith("Expired") || lbl.startsWith("Shared")
                                            val txt = if (isQty) InputGuards.fmtNum(v) else InputGuards.formatMoney(v, currency, masked)
                                            Text(
                                                txt,
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = color,
                                                modifier = Modifier.width(88.dp)
                                            )
                                        }
                                    }
                                    HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.35f))
                                }
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Performance Overview ($sellerLabel)",
                        fontFamily = PlayfairFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("day" to "Daily", "week" to "Weekly", "month" to "Monthly", "year" to "Yearly", "all" to "All Time").forEach { (k, lbl) ->
                            val sel = perfMode == k
                            Surface(
                                onClick = { perfMode = k },
                                shape = RoundedCornerShape(999.dp),
                                color = if (sel) accents.softPillFill else accents.inputFill,
                                border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 36.dp)
                                    .testTag("calc_po_$k")
                            ) {
                                Box(Modifier.padding(horizontal = 13.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        lbl,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (sel) accents.emerald else accents.textMuted,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    SarimCard(contentPadding = PaddingValues(16.dp)) {
                        val perfViewLabel = when (perfMode) {
                            "week" -> "WEEKLY VIEW"
                            "month" -> "MONTHLY VIEW"
                            "year" -> "YEARLY VIEW"
                            "all" -> "ALL TIME VIEW"
                            else -> "DAILY VIEW"
                        }
                        Text(
                            perfViewLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("REVENUE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = accents.textMuted)
                        Text(
                            InputGuards.formatMoney(perfTotals.rev, currency, masked),
                            fontFamily = JakartaFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Profit ${InputGuards.formatMoney(perfTotals.prof, currency, masked)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (perfTotals.prof >= 0.0) accents.textMuted else accents.danger
                        )

                        Spacer(Modifier.height(8.dp))
                        val cashKg = max(0.0, perfTotals.cashQty)
                        val credKg = max(0.0, perfTotals.credQty)
                        val totKg = cashKg + credKg
                        val cashPct = if (totKg > 0.0) ((cashKg / totKg) * 100.0).roundToInt() else 0
                        val credPct = if (totKg > 0.0) 100 - cashPct else 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(accents.inputFill)
                        ) {
                            if (totKg > 0.0) {
                                if (cashKg > 0.0) Box(Modifier.weight(max(0.02f, (cashKg / totKg).toFloat())).fillMaxHeight().background(accents.emerald))
                                if (credKg > 0.0) Box(Modifier.weight(max(0.02f, (credKg / totKg).toFloat())).fillMaxHeight().background(accents.gold))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(accents.emerald))
                                Text("Cash ${InputGuards.formatKg(cashKg)} $cashPct%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(accents.gold))
                                Text("Credit ${InputGuards.formatKg(credKg)} $credPct%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SarimHalfRingKpiBox("TOTAL SOLD", InputGuards.formatKg(perfTotals.sold), accents.cyan, Modifier.weight(1f))
                            SarimHalfRingKpiBox("RETURNED", InputGuards.formatKg(perfTotals.ret), accents.gold, Modifier.weight(1f))
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.55f))
                        Spacer(Modifier.height(12.dp))

                        Text(
                            "CREDIT",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        val credOut = max(0.0, perfTotals.giv)
                        val credRec = max(0.0, perfTotals.coll)
                        val credTot = credOut + credRec
                        val outPct = if (credTot > 0.0) ((credOut / credTot) * 100.0).roundToInt() else 0
                        val recCredPct = if (credTot > 0.0) 100 - outPct else 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(accents.inputFill)
                        ) {
                            if (credTot > 0.0) {
                                if (credOut > 0.0) Box(Modifier.weight(max(0.02f, (credOut / credTot).toFloat())).fillMaxHeight().background(accents.danger))
                                if (credRec > 0.0) Box(Modifier.weight(max(0.02f, (credRec / credTot).toFloat())).fillMaxHeight().background(accents.emerald))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(accents.danger))
                                Text("Out ${InputGuards.formatMoney(credOut, currency, masked)} $outPct%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(accents.emerald))
                                Text("Recovered ${InputGuards.formatMoney(credRec, currency, masked)} $recCredPct%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val netPeriodDebt = InputGuards.round2(perfTotals.giv - perfTotals.coll)
                        SarimHalfRingKpiBox(
                            "NET DEBT",
                            InputGuards.formatMoney(netPeriodDebt, currency, masked),
                            if (netPeriodDebt > 0.01) accents.danger else accents.emerald,
                            Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.55f))
                        Spacer(Modifier.height(12.dp))

                        val pDisc = InputGuards.round2(perfTotals.received - perfTotals.expected)
                        val discLabel = when {
                            abs(pDisc) < 0.01 -> "Perfect match"
                            pDisc < 0 -> "Short ${InputGuards.formatMoney(abs(pDisc), currency, masked)}"
                            else -> "Over ${InputGuards.formatMoney(pDisc, currency, masked)}"
                        }
                        val discTone = if (pDisc < -0.01) accents.danger else accents.emerald
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "CASH HANDOVER",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = discTone.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    discLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = discTone,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SarimHalfRingKpiBox("EXPECTED", InputGuards.formatMoney(perfTotals.expected, currency, masked), accents.cyan, Modifier.weight(1f))
                            SarimHalfRingKpiBox("RECEIVED", InputGuards.formatMoney(perfTotals.received, currency, masked), accents.emerald, Modifier.weight(1f))
                        }

                        if (perfTotals.exp > 0.0 || perfTotals.shared > 0.0 || perfTotals.fieldExp > 0.0 || perfTotals.commissionPaid > 0.0) {
                            Spacer(Modifier.height(10.dp))
                            if (perfTotals.exp > 0.0) ResultKvRow("Expired (→ CHORA)", InputGuards.formatKg(perfTotals.exp), accents.danger)
                            if (perfTotals.shared > 0.0) ResultKvRow("Shared (Deduction)", InputGuards.formatKg(perfTotals.shared), accents.danger)
                            if (perfTotals.fieldExp > 0.0) ResultKvRow("Field Expenses", InputGuards.formatMoney(perfTotals.fieldExp, currency, masked), accents.danger)
                            if (perfTotals.commissionPaid > 0.0) ResultKvRow("Commission Paid", InputGuards.formatMoney(perfTotals.commissionPaid, currency, masked), accents.emerald)
                        }
                    }
                }
            }

            item {
                SarimCard(contentPadding = PaddingValues(16.dp)) {
                    Text(
                        "${sellerLabel.uppercase()} · MARKET DEBT TRACKER",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("CURRENT MARKET DEBT", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = accents.textMuted)
                    Text(
                        InputGuards.formatMoney(debtBalance, currency, masked),
                        fontFamily = JakartaFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = if (debtBalance > 0.01) accents.danger else accents.emerald
                    )
                    Spacer(Modifier.height(8.dp))
                    val totalActivity = (debtRecovered + debtBalance.coerceAtLeast(0.0)).coerceAtLeast(1.0)
                    val recPct = ((debtRecovered / totalActivity) * 100.0).toInt().coerceIn(0, 100)
                    val outPct = if (debtCreditIssued <= 0.01 && debtRecovered <= 0.01) 0 else 100 - recPct
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(accents.inputFill)
                    ) {
                        if (debtCreditIssued > 0.01 || debtRecovered > 0.01) {
                            Row(Modifier.fillMaxWidth().height(5.dp)) {
                                if (recPct > 0) Box(Modifier.weight(max(0.02f, recPct / 100f)).fillMaxHeight().background(accents.emerald))
                                if (outPct > 0) Box(Modifier.weight(max(0.02f, outPct / 100f)).fillMaxHeight().background(accents.danger))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(accents.emerald))
                            Text(
                                "Recovered ${InputGuards.formatMoney(debtRecovered, currency, masked)} $recPct%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(accents.danger))
                            Text(
                                "Outstanding ${InputGuards.formatMoney(debtBalance, currency, masked)} $outPct%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SarimHalfRingKpiBox("TOTAL CREDIT ISSUED", InputGuards.formatMoney(debtCreditIssued, currency, masked), accents.gold, Modifier.weight(1f))
                        SarimHalfRingKpiBox("CASH RECOVERED", InputGuards.formatMoney(debtRecovered, currency, masked), accents.emerald, Modifier.weight(1f))
                    }
                }
            }

            item {
                Text(
                    "CALCULATOR HISTORY",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
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
                            "${list.size} ${if (list.size == 1) "entry" else "entries"}"
                        },
                        yearSubLabel = { list ->
                            "Revenue ${InputGuards.formatMoney(list.sumOf { it.revenue }, currency, masked)}"
                        },
                        monthSubLabel = { list ->
                            "Revenue ${InputGuards.formatMoney(list.sumOf { it.revenue }, currency, masked)}"
                        },
                        daySummaryLabel = { list ->
                            "Revenue: ${InputGuards.formatMoney(list.sumOf { it.revenue }, currency, masked)}"
                        },
                        initiallyExpandFirstDay = false,
                        itemRow = { c ->
                            val storeLabel = FormulaEngine.storeLabelFor(c.returnStore, snapshot.stores)
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = accents.inputFill,
                                border = BorderStroke(1.dp, accents.borderSubtle),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCalc = c }
                                    .testTag("calc_row_${c.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(c.seller.uppercase(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
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
private fun CalcDashedDecimalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val accents = LocalSarimAccents.current
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
            color = accents.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 52.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(accents.inputFill.copy(alpha = 0.65f))
                .dashedBorder(1.1.dp, accents.inputBorder, cornerRadius = 18.dp)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = { raw ->
                    if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d{0,3}$"))) {
                        onValueChange(raw)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(
                    fontFamily = JakartaFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(accents.emerald),
                modifier = Modifier.fillMaxWidth().testTag(testTag),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(
                            "0.00",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accents.textMuted.copy(alpha = 0.65f)
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
private fun CalcTintedDecimalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val accents = LocalSarimAccents.current
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
            color = accents.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = accents.softPillFill,
            border = BorderStroke(1.2.dp, accents.emerald),
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { raw ->
                        if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d{0,3}$"))) {
                            onValueChange(raw)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = TextStyle(
                        fontFamily = JakartaFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = accents.emerald
                    ),
                    cursorBrush = SolidColor(accents.emerald),
                    modifier = Modifier.fillMaxWidth().testTag(testTag),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                "0.00",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = accents.emerald.copy(alpha = 0.85f)
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
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
