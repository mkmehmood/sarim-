package com.gullzubair.sarim.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.ui.components.ConfirmRequest
import com.gullzubair.sarim.ui.components.ConfirmRequestHost
import com.gullzubair.sarim.ui.components.deleteConfirmMessage
import com.gullzubair.sarim.ui.components.SarimDangerButton
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.SalesAnalyticsEngine
import com.gullzubair.sarim.ui.components.CustomerMoneyOutDialog
import com.gullzubair.sarim.ui.components.CustomersDirectoryCard
import com.gullzubair.sarim.ui.components.DiscardEditDialog
import com.gullzubair.sarim.ui.components.EditDiffConfirmDialog
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.SalesAnalyticsCard
import com.gullzubair.sarim.ui.components.SalesBlockingAlertDialog
import com.gullzubair.sarim.ui.components.SalesDetailSheet
import com.gullzubair.sarim.ui.components.SalesEntryCard
import com.gullzubair.sarim.ui.components.SalesEntryMode
import com.gullzubair.sarim.ui.components.SalesHistoryRowCard
import com.gullzubair.sarim.ui.components.SalesSaveConfirmDialog
import com.gullzubair.sarim.ui.components.SalesSaveConfirmModel
import com.gullzubair.sarim.ui.components.SalesSummaryCard
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimYearMonthDayTree
import com.gullzubair.sarim.ui.components.TabHeading
import com.gullzubair.sarim.ui.theme.CinzelFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.PdfExporter
import kotlin.math.abs
import kotlin.math.max

@Composable
fun SalesScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onOpenCustomerDetail: (String) -> Unit,
    onEditCustomer: (String) -> Unit
) {
    val accents = LocalSarimAccents.current
    val context = LocalContext.current
    val currency = snapshot.settings.currencySymbol
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val isSalesRestricted = remember(session) {
        FinanceEngine.isMoneyRestricted(session) && "sales" in session.tabs
    }
    val masked = isSalesRestricted || session.restrictMoney || snapshot.settings.hideSensitiveMoney
    val activeStores = remember(snapshot.stores) { snapshot.stores.filter { it.deletedAt == null && it.active } }

    val repNames = remember(snapshot) {
        (listOf("NONE") +
            snapshot.settings.reps +
            snapshot.teamMembers.map { it.displayName } +
            snapshot.repRecords.filter { it.deletedAt == null }.map { it.salesRep } +
            snapshot.entities.filter { it.deletedAt == null && (it.type == "staff" || it.type == "employee") }.map { it.name })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
    }

    // Entry form state (#tab-sales)
    var entryMode by remember { mutableStateOf(SalesEntryMode.SALE) }
    var dateIso by remember(todayIso) { mutableStateOf(todayIso) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var selectedRep by remember { mutableStateOf("NONE") }
    var selectedStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var paymentType by remember { mutableStateOf("CREDIT") }
    var qtyStr by remember { mutableStateOf("") }
    var rateStr by remember { mutableStateOf("") }
    var collectedStr by remember { mutableStateOf("") }
    var expiredStr by remember { mutableStateOf("") }
    var repReturnedStr by remember { mutableStateOf("") }
    var repExpiredStr by remember { mutableStateOf("") }
    var addToLedger by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    // Directory, Analytics, Summary & History controls
    var customerSearch by remember { mutableStateOf("") }
    var chartPeriod by remember { mutableStateOf(SalesAnalyticsEngine.SalesChartPeriod.WEEK) }
    var summaryPeriod by remember { mutableStateOf(SalesAnalyticsEngine.SalesSummaryPeriod.DAY) }

    // Modals & sheets
    var selectedSaleForSheet by remember { mutableStateOf<SalesRecord?>(null) }
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    var editingSale by remember { mutableStateOf<SalesRecord?>(null) }
    var showEditDiff by remember { mutableStateOf(false) }
    var showDiscardEdit by remember { mutableStateOf(false) }
    var pendingSaveConfirm by remember { mutableStateOf<SalesSaveConfirmModel?>(null) }
    var blockingAlert by remember { mutableStateOf<Pair<String, String>?>(null) }
    var moneyOutTarget by remember { mutableStateOf<String?>(null) }

    // Auto-fill customer/store rate when not editing
    LaunchedEffect(customerName, selectedStore?.key, editingSale?.id) {
        if (editingSale == null) {
            val effRate = SalesAnalyticsEngine.effectiveCustomerRate(
                customerName = customerName,
                store = selectedStore,
                customers = snapshot.customers,
                defaultPrices = snapshot.settings.defaultStorePrices
            )
            rateStr = if (effRate > 0.0) InputGuards.fmtNum(effRate).replace(",", "") else ""
        }
    }

    fun resetForm() {
        editingSale = null
        // Web resets to Direct / Credit / first store and leaves the date alone.
        entryMode = SalesEntryMode.SALE
        customerName = ""
        customerPhone = ""
        selectedRep = "NONE"
        selectedStore = activeStores.firstOrNull()
        paymentType = "CREDIT"
        addToLedger = false
        qtyStr = ""
        collectedStr = ""
        expiredStr = ""
        repReturnedStr = ""
        repExpiredStr = ""
        notes = ""
    }

    fun startEdit(s: SalesRecord) {
        val reason = SalesAnalyticsEngine.getSaleBlockReason(snapshot, s, forEdit = true)
        if (reason.isNotBlank()) {
            blockingAlert = "Cannot Edit" to reason
            return
        }
        editingSale = s
        val isRepRet = s.isRepReturn || s.paymentType == "REP_RETURN"
        val isColl = FinanceEngine.isCollectionRow(s)
        entryMode = if (isRepRet || isColl) SalesEntryMode.COLLECTION else SalesEntryMode.SALE
        dateIso = if (!isRepRet && !isColl && s.supplyDate.isNotBlank()) s.supplyDate else s.date.ifBlank { todayIso }
        customerName = s.customerName
        customerPhone = s.customerPhone
        selectedRep = s.salesRep.ifBlank { "NONE" }
        val st = activeStores.firstOrNull { it.key.equals(s.supplyStore, true) || it.name.equals(s.supplyStore, true) }
        if (st != null) selectedStore = st
        paymentType = if (s.paymentType == "CREDIT") "CREDIT" else "CASH"
        qtyStr = if (!isRepRet && s.quantity > 0.0) InputGuards.fmtNum(s.quantity).replace(",", "") else ""
        rateStr = if (s.unitPrice > 0.0) InputGuards.fmtNum(s.unitPrice).replace(",", "") else ""
        collectedStr = if (isColl && s.collectedAmount > 0.0) InputGuards.fmtNum(s.collectedAmount).replace(",", "") else ""
        expiredStr = if (isColl && s.expiredQty > 0.0) InputGuards.fmtNum(s.expiredQty).replace(",", "") else ""
        repReturnedStr = if (isRepRet && s.returnedQty > 0.0) InputGuards.fmtNum(s.returnedQty).replace(",", "") else ""
        repExpiredStr = if (isRepRet && s.expiredQty > 0.0) InputGuards.fmtNum(s.expiredQty).replace(",", "") else ""
        notes = s.notes
    }

    // Edit requested from the customer ledger screen.
    val pendingEditId by vm.pendingSaleEditId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingEditId, snapshot.sales) {
        val id = pendingEditId ?: return@LaunchedEffect
        val target = snapshot.sales.firstOrNull { it.id == id && it.deletedAt == null }
        if (target != null) startEdit(target)
        vm.consumePendingSaleEdit()
    }

    BackHandler(enabled = editingSale != null) {
        showDiscardEdit = true
    }

    val isRepSelected = selectedRep.isNotBlank() && !selectedRep.equals("NONE", true)
    val isRepReturnMode = entryMode == SalesEntryMode.COLLECTION && isRepSelected

    // Live computations for Entry Card
    val qty = qtyStr.toDoubleOrNull() ?: 0.0
    val rate = rateStr.toDoubleOrNull() ?: SalesAnalyticsEngine.effectiveCustomerRate(
        customerName, selectedStore, snapshot.customers, snapshot.settings.defaultStorePrices
    )
    val collected = collectedStr.toDoubleOrNull() ?: 0.0
    val expired = expiredStr.toDoubleOrNull() ?: 0.0
    val repReturned = repReturnedStr.toDoubleOrNull() ?: 0.0
    val repExpired = repExpiredStr.toDoubleOrNull() ?: 0.0

    val stockTile = remember(snapshot, selectedStore, dateIso, qty, editingSale?.id) {
        SalesAnalyticsEngine.computeDateStockTile(
            snapshot = snapshot,
            store = selectedStore,
            supplyDateIso = dateIso,
            requestedQtyKg = qty,
            excludeSaleId = editingSale?.id
        )
    }

    val costIdx = remember(snapshot.production) { FormulaEngine.buildCostIndex(snapshot.production) }
    val unitCost = remember(costIdx, selectedStore, dateIso, editingSale) {
        val orig = editingSale
        val st = selectedStore
        if (orig != null && st != null &&
            SalesAnalyticsEngine.matchesStore(orig.supplyStore, st) &&
            orig.supplyDate.ifBlank { orig.date } == dateIso &&
            orig.quantity > 0.0 && orig.totalCost > 0.0
        ) {
            InputGuards.round2(orig.totalCost / orig.quantity)
        } else {
            st?.let { FormulaEngine.costSourceFromIndex(costIdx, it.key.ifBlank { it.name }, dateIso).cp } ?: 0.0
        }
    }
    val previewTotalCost = InputGuards.round2(qty * unitCost)
    val previewTotalValue = InputGuards.round2(qty * rate)
    val previewProfit = InputGuards.round2(previewTotalValue - previewTotalCost)

    val existingCredit = remember(snapshot, customerName, editingSale?.id) {
        SalesAnalyticsEngine.computeCustomerOutstanding(customerName, snapshot, excludeSaleId = editingSale?.id)
    }
    val customerTotalQty = remember(snapshot, customerName) {
        SalesAnalyticsEngine.computeCustomerTotalQty(customerName, snapshot)
    }

    val goodsCreditRate = remember(snapshot, customerName, selectedStore, editingSale?.id) {
        SalesAnalyticsEngine.computeGoodsCreditUnitPrice(
            customerName = customerName,
            snapshot = snapshot,
            excludeSaleId = editingSale?.id,
            fallbackStore = selectedStore
        )
    }
    val goodsCreditPlan = remember(expired, goodsCreditRate, existingCredit, collected) {
        val cap = if (existingCredit >= 0.0) max(0.0, existingCredit - collected) else -1.0
        SalesAnalyticsEngine.planGoodsCredit(expired, goodsCreditRate, if (cap >= 0.0) cap else null)
    }

    val repPendingInfo = remember(snapshot, selectedRep, editingSale?.id) {
        SalesAnalyticsEngine.computeRepReturnPendingInfo(selectedRep, snapshot, excludeReturnId = editingSale?.id)
    }
    val repReturnUnitPrice = remember(snapshot, selectedRep, selectedStore, editingSale?.id) {
        if (isRepSelected) {
            SalesAnalyticsEngine.computeGoodsCreditUnitPrice(
                customerName = selectedRep,
                snapshot = snapshot,
                excludeSaleId = editingSale?.id,
                fallbackStore = selectedStore
            )
        } else 0.0
    }
    val repReturnPlan = remember(repExpired, repReturned, repReturnUnitPrice, existingCredit) {
        SalesAnalyticsEngine.planGoodsCredit(
            repExpired + repReturned,
            repReturnUnitPrice,
            if (existingCredit >= 0.0) existingCredit else null
        )
    }

    // Autocomplete suggestions & new-customer mobile check
    val fullDirectory = remember(snapshot, customerSearch) {
        SalesAnalyticsEngine.computeCustomerDirectory(snapshot, customerSearch)
    }
    val suggestionPool = remember(snapshot, customerName) {
        SalesAnalyticsEngine.computeCustomerDirectory(snapshot, customerName).items
    }
    val showNewCustomerPhoneField = remember(customerName, snapshot, isRepSelected) {
        val clean = customerName.trim()
        !isRepSelected && clean.length > 2 &&
            snapshot.customers.none { it.deletedAt == null && it.name.equals(clean, ignoreCase = true) } &&
            snapshot.sales.none { it.deletedAt == null && it.customerName.equals(clean, ignoreCase = true) }
    }

    // Analytics & Summary (respect customerName filter like web)
    val chartData = remember(snapshot, chartPeriod, dateIso, customerName) {
        SalesAnalyticsEngine.computeSalesAnalytics(
            snapshot = snapshot,
            period = chartPeriod,
            anchorDateIso = dateIso,
            customerFilter = customerName
        )
    }
    val summaryData = remember(snapshot, summaryPeriod, dateIso, customerName) {
        SalesAnalyticsEngine.computeSalesPeriodSummary(
            snapshot = snapshot,
            period = summaryPeriod,
            anchorDateIso = dateIso,
            customerFilter = customerName
        )
    }

    val runningBalances = remember(snapshot) {
        SalesAnalyticsEngine.computeCustomerRunningBalances(snapshot)
    }

    val filteredHistory = remember(snapshot, customerName) {
        val cf = customerName.trim().lowercase()
        snapshot.sales.filter { s ->
            if (s.deletedAt != null || !SalesAnalyticsEngine.isAdminSaleProfile(s.currentRepProfile)) return@filter false
            if (cf.isNotBlank() && !s.customerName.lowercase().contains(cf)) return@filter false
            true
        }.sortedWith(compareByDescending<SalesRecord> { it.date }.thenByDescending { it.timestamp })
    }

    fun executeSave() {
        val orig = editingSale
        if (orig != null) {
            showEditDiff = true
            return
        }
        when {
            isRepReturnMode -> {
                vm.submitSale(
                    date = dateIso,
                    supplyDate = dateIso,
                    paymentType = "REP_RETURN",
                    customer = selectedRep,
                    store = selectedStore,
                    rep = selectedRep,
                    qty = repReturned,
                    rate = repReturnUnitPrice,
                    collectedAmount = 0.0,
                    expiredKg = repExpired,
                    choraRate = repReturnUnitPrice,
                    addToLedger = false,
                    notes = notes
                )
            }
            entryMode == SalesEntryMode.COLLECTION -> {
                vm.submitSale(
                    date = dateIso,
                    supplyDate = dateIso,
                    paymentType = "COLLECTION",
                    customer = customerName,
                    store = selectedStore,
                    rep = selectedRep,
                    qty = 0.0,
                    rate = goodsCreditRate,
                    collectedAmount = collected,
                    expiredKg = expired,
                    choraRate = goodsCreditRate,
                    addToLedger = addToLedger && collected > 0.0,
                    notes = notes,
                    customerPhone = if (showNewCustomerPhoneField) customerPhone else ""
                )
            }
            else -> {
                val saleDate = todayIso
                vm.submitSale(
                    date = saleDate,
                    supplyDate = dateIso,
                    paymentType = paymentType,
                    customer = customerName,
                    store = selectedStore,
                    rep = selectedRep,
                    qty = qty,
                    rate = rate,
                    collectedAmount = if (paymentType == "CASH") previewTotalValue else 0.0,
                    expiredKg = 0.0,
                    choraRate = 0.0,
                    addToLedger = addToLedger && paymentType == "CASH",
                    notes = notes,
                    customerPhone = if (showNewCustomerPhoneField) customerPhone else ""
                )
            }
        }
        resetForm()
    }

    fun validateAndSave() {
        // Same checks, order and wording as web `_saveCustomerSaleImpl` / `saveCustomerCollection`.
        val webIssue = (if (dateIso.isBlank()) "Please select a date." else InputGuards.dateIssue(dateIso, todayIso))
            .ifEmpty { if (showNewCustomerPhoneField) InputGuards.phoneIssue(customerPhone) else "" }
            .ifEmpty { if (isRepReturnMode) "" else InputGuards.nameIssue(customerName, "Customer name") }
            .ifEmpty {
                if (!isRepReturnMode && entryMode == SalesEntryMode.SALE && qtyStr.isNotBlank()) {
                    InputGuards.quantityIssue(qtyStr, "Quantity")
                } else ""
            }
        if (webIssue.isNotEmpty()) {
            blockingAlert = "Warning" to webIssue
            return
        }
        when {
            isRepReturnMode -> {
                if (repExpired < 0.0 || repReturned < 0.0 || (repExpired <= 0.0 && repReturned <= 0.0)) {
                    blockingAlert = "Missing Quantity" to "Please enter expired and/or returned quantity."
                    return
                }
                val totalBack = InputGuards.round3(repExpired + repReturned)
                if (repPendingInfo.pendingDeliveredKg > 0.0 &&
                    repPendingInfo.alreadyReturnedKg + totalBack > repPendingInfo.pendingDeliveredKg + 0.001
                ) {
                    blockingAlert = "Return Exceeds Deliveries" to
                        "Cannot return ${InputGuards.formatKg(totalBack)}. Remaining pending deliveries: ${InputGuards.formatKg(repPendingInfo.remainingDeliverableKg)}."
                    return
                }
                if (editingSale != null) {
                    showEditDiff = true
                } else {
                    val lines = buildList {
                        add("Sales Rep" to selectedRep)
                        add("Date" to DateUtils.formatPretty(dateIso))
                        if (repExpired > 0.0) add("Expired / Damage" to InputGuards.formatKg(repExpired))
                        if (repReturned > 0.0) add("Returned Stock" to InputGuards.formatKg(repReturned))
                        add("Total Back" to InputGuards.formatKg(totalBack))
                        if (!isSalesRestricted && repReturnPlan.value > 0.0) {
                            add("Balance Deduction" to InputGuards.formatMoney(repReturnPlan.value, currency, masked))
                        }
                    }
                    pendingSaveConfirm = SalesSaveConfirmModel(
                        title = "Confirm Rep Stock Return",
                        lines = lines,
                        notes = listOf("Stock reaches CHORA / Production when the Calculator is reconciled.")
                    )
                }
            }
            entryMode == SalesEntryMode.COLLECTION -> {
                val cleanCust = InputGuards.sanitizeName(customerName)
                if (cleanCust.isBlank()) {
                    blockingAlert = "Customer Required" to "Please enter customer name."
                    return
                }
                if (collected < 0.0 || expired < 0.0 || (collected <= 0.0 && expired <= 0.0)) {
                    blockingAlert = "Invalid Collection" to "Please enter an amount collected and/or an expired quantity."
                    return
                }
                if (editingSale == null && collected > 0.0 && existingCredit <= 0.005) {
                    blockingAlert = "Credit Error" to
                        "$cleanCust has no outstanding credit balance. Collections can only be recorded against existing unpaid credit."
                    return
                }
                if (editingSale != null) {
                    showEditDiff = true
                } else {
                    val warning = if (collected > 0.0 && existingCredit >= 0.0 && collected > existingCredit + 0.01) {
                        val over = collected - existingCredit
                        "Customer owes ${InputGuards.formatMoney(existingCredit, currency, masked)}. Collecting ${InputGuards.formatMoney(collected, currency, masked)} is an overpayment of ${InputGuards.formatMoney(over, currency, masked)}. Proceed only if this is an advance payment."
                    } else null
                    val notesList = buildList {
                        if (expired > 0.0) {
                            add("Expired ${InputGuards.formatKg(expired)} will be added to CHORA raw material.")
                            if (!isSalesRestricted) {
                                add(
                                    if (goodsCreditPlan.value > 0.0) {
                                        "Expired goods credited: ${InputGuards.formatMoney(goodsCreditPlan.value, currency, masked)} (${InputGuards.formatKg(expired)} × ${InputGuards.formatMoney(goodsCreditRate, currency, masked)}${if (goodsCreditPlan.capped) ", limited to what is owed" else ""})."
                                    } else {
                                        "No balance is owed, so expired goods are recorded without a credit."
                                    }
                                )
                            }
                        }
                    }
                    val lines = buildList {
                        add("Customer" to cleanCust)
                        add("Date" to DateUtils.formatPretty(dateIso))
                        if (collected > 0.0) add("Cash Collected" to InputGuards.formatMoney(collected, currency, masked))
                        if (expired > 0.0) add("Expired -> CHORA" to InputGuards.formatKg(expired))
                    }
                    pendingSaveConfirm = SalesSaveConfirmModel(
                        title = "Confirm Collection",
                        lines = lines,
                        warning = warning,
                        notes = notesList,
                        confirmLabel = if (warning != null) "Collect Anyway" else "Confirm & Save"
                    )
                }
            }
            else -> {
                val cleanCust = InputGuards.sanitizeName(customerName)
                val st = selectedStore
                if (cleanCust.isBlank()) {
                    blockingAlert = "Customer Required" to "Please enter customer name."
                    return
                }
                if (st == null) {
                    blockingAlert = "Store Required" to "Please select a supply store."
                    return
                }
                if (qty <= 0.0) {
                    blockingAlert = "Quantity Required" to "Please enter a valid quantity greater than 0."
                    return
                }
                if (rate <= 0.0) {
                    blockingAlert = "Price Not Set" to "Sale price not configured for this store. Set prices in Factory Formulas."
                    return
                }
                if (stockTile.totalInKg <= 0.0) {
                    blockingAlert = "No Stock Available" to
                        "No production or returns for ${st.name} on ${DateUtils.formatPretty(dateIso)}."
                    return
                }
                if (stockTile.availableKg - qty < -1e-6) {
                    blockingAlert = "Insufficient Stock" to
                        "Available: ${InputGuards.formatKg(max(0.0, stockTile.availableKg))}, Requested: ${InputGuards.formatKg(qty)}."
                    return
                }
                if (editingSale != null) {
                    showEditDiff = true
                } else {
                    val creditWarn = if (paymentType == "CREDIT" && existingCredit > 5000.0) {
                        if (isSalesRestricted) {
                            "Check this credit sale before saving."
                        } else {
                            "$cleanCust already has unpaid balance of ${InputGuards.formatMoney(existingCredit, currency, masked)}. New total will be ${InputGuards.formatMoney(existingCredit + previewTotalValue, currency, masked)}."
                        }
                    } else null
                    val lines = buildList {
                        add("Customer" to cleanCust)
                        add("Supply Store" to st.name)
                        add("Supply Date" to DateUtils.formatPretty(dateIso))
                        add("Payment" to paymentType)
                        add("Quantity" to InputGuards.formatKg(qty))
                        if (!isSalesRestricted) {
                            add("Rate / kg" to InputGuards.formatMoney(rate, currency, masked))
                            add("Total Value" to InputGuards.formatMoney(previewTotalValue, currency, masked))
                        }
                    }
                    pendingSaveConfirm = SalesSaveConfirmModel(
                        title = "Confirm $paymentType Sale",
                        lines = lines,
                        warning = creditWarn,
                        confirmLabel = if (creditWarn != null) "Add Credit Anyway" else "Confirm & Save"
                    )
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Screen Heading (matches web #tab-sales header)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabHeading("Sales")
            }
        }

        // 1. Entry Card (Sale / Collection / Rep Return)
        item {
            SalesEntryCard(
                isEditing = editingSale != null,
                editingId = editingSale?.id,
                isSalesRestricted = isSalesRestricted,
                masked = masked,
                currency = currency,
                entryMode = entryMode,
                onSelectEntryMode = { mode -> entryMode = mode },
                dateIso = dateIso,
                onDateChange = { dateIso = it },
                todayIso = todayIso,
                customerName = customerName,
                onCustomerNameChange = { customerName = it },
                customerSuggestions = suggestionPool,
                showNewCustomerPhoneField = showNewCustomerPhoneField,
                customerPhone = customerPhone,
                onCustomerPhoneChange = { customerPhone = it },
                repNames = repNames,
                selectedRep = selectedRep,
                onSelectRep = { rep ->
                    val prevWasRep = selectedRep.isNotBlank() && !selectedRep.equals("NONE", true)
                    selectedRep = rep
                    if (!rep.equals("NONE", true)) {
                        customerName = rep
                        paymentType = "CREDIT"
                    } else if (prevWasRep && repNames.any { it.equals(customerName.trim(), true) }) {
                        customerName = ""
                    }
                },
                existingCredit = existingCredit,
                customerTotalQty = customerTotalQty,
                onOpenCustomerDetail = onOpenCustomerDetail,
                onOpenMoneyOut = { target -> moneyOutTarget = target },
                qtyStr = qtyStr,
                onQtyChange = { qtyStr = it },
                rateStr = rateStr,
                onRateChange = { rateStr = it },
                activeStores = activeStores,
                selectedStore = selectedStore,
                onSelectStore = { selectedStore = it },
                stockTile = stockTile,
                paymentType = paymentType,
                onSelectPaymentType = { paymentType = it },
                addToLedger = addToLedger,
                onAddToLedgerChange = { addToLedger = it },
                previewTotalCost = previewTotalCost,
                previewTotalValue = previewTotalValue,
                previewProfit = previewProfit,
                collectedStr = collectedStr,
                onCollectedChange = { collectedStr = it },
                expiredStr = expiredStr,
                onExpiredChange = { expiredStr = it },
                goodsCreditRate = goodsCreditRate,
                goodsCreditPlan = goodsCreditPlan,
                repReturnedStr = repReturnedStr,
                onRepReturnedChange = { repReturnedStr = it },
                repExpiredStr = repExpiredStr,
                onRepExpiredChange = { repExpiredStr = it },
                repPendingInfo = repPendingInfo,
                repReturnPlan = repReturnPlan,
                repReturnUnitPrice = repReturnUnitPrice,
                notes = notes,
                onNotesChange = { notes = it },
                onCancelEdit = { showDiscardEdit = true },
                onSaveClick = { validateAndSave() }
            )
        }

        // Data-money sections (completely hidden for restricted users)
        if (!isSalesRestricted) {
            // 2. Customers Directory Card
            item {
                CustomersDirectoryCard(
                    directory = fullDirectory,
                    searchQuery = customerSearch,
                    onSearchChange = { customerSearch = it },
                    currency = currency,
                    masked = masked,
                    onExportAllPdf = {
                        val (rows, footer) = SalesAnalyticsEngine.buildAllCustomersPdfRows(snapshot, masked)
                        if (rows.isEmpty()) {
                            vm.showToast("No customers to export", com.gullzubair.sarim.ui.viewmodel.ToastKind.WARNING)
                        } else {
                            PdfExporter.exportAndShareStatement(
                                context = context,
                                title = "All Customers Summary",
                                subtitle = "Gull & Zubair Naswar Dealers",
                                rows = rows,
                                summaryFooter = footer
                            )
                        }
                    },
                    onAddCustomer = { onEditCustomer("") },
                    onOpenCustomerDetail = onOpenCustomerDetail,
                    onEditCustomer = onEditCustomer
                )
            }

            // 3. Analytics (Stacked Bar Chart + Donut Summary)
            item {
                SalesAnalyticsCard(
                    chartData = chartData,
                    selectedPeriod = chartPeriod,
                    onSelectPeriod = { chartPeriod = it },
                    customerFilter = customerName,
                    currency = currency,
                    masked = masked
                )
            }

            // 4. Summary (Daily / Weekly / Monthly / Yearly / All Time)
            item {
                SalesSummaryCard(
                    summary = summaryData,
                    selectedPeriod = summaryPeriod,
                    onSelectPeriod = { summaryPeriod = it },
                    customerFilter = customerName,
                    currency = currency,
                    masked = masked
                )
            }

            // 5. Sales History Tree
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SALES HISTORY",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.7.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (customerName.isNotBlank()) {
                            Text(
                                text = "${filteredHistory.size} entries",
                                style = MaterialTheme.typography.labelMedium,
                                color = accents.emerald
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (filteredHistory.isEmpty()) {
                        SarimCard(contentPadding = PaddingValues(18.dp)) {
                            Text(
                                text = if (customerName.isNotBlank()) {
                                    "No matching sales history found."
                                } else {
                                    "Save a sale or collection above to see it here."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = accents.textMuted
                            )
                        }
                    } else {
                        val calcTotals: (List<com.gullzubair.sarim.data.model.SalesRecord>) -> Triple<Double, Double, Double> = { list ->
                            val sold = list.filter { !FinanceEngine.isCollectionRow(it) && !it.isRepReturn }.sumOf { it.totalValue }
                            val rec = list.sumOf {
                                when {
                                    FinanceEngine.isCollectionRow(it) -> if (it.collectedAmount > 0.0) it.collectedAmount else it.totalValue
                                    it.paymentType == "CASH" -> it.totalValue
                                    it.paymentType == "CREDIT" && it.creditReceived -> it.totalValue
                                    else -> it.partialPaymentReceived
                                }
                            }
                            val qtyVal = list.sumOf { it.quantity }
                            Triple(sold, rec, qtyVal)
                        }
                        SarimYearMonthDayTree(
                            items = filteredHistory,
                            dateOf = { it.date },
                            summaryLabel = { list ->
                                val (_, _, q) = calcTotals(list)
                                "${InputGuards.formatKg(q)} sold"
                            },
                            yearSubLabel = { list ->
                                val (sold, rec, _) = calcTotals(list)
                                "Sold ${InputGuards.formatMoney(sold, currency, masked)} · Received ${InputGuards.formatMoney(rec, currency, masked)}"
                            },
                            monthSubLabel = { list ->
                                val (sold, rec, _) = calcTotals(list)
                                "Sold ${InputGuards.formatMoney(sold, currency, masked)} · Received ${InputGuards.formatMoney(rec, currency, masked)}"
                            },
                            daySummaryLabel = { list ->
                                val (sold, rec, q) = calcTotals(list)
                                "${InputGuards.formatKg(q)} sold | Sold: ${InputGuards.formatMoney(sold, currency, masked)} | Received: ${InputGuards.formatMoney(rec, currency, masked)}"
                            },
                            initiallyExpandFirstDay = false
                        ) { s ->
                            SalesHistoryRowCard(
                                s = s,
                                stores = snapshot.stores,
                                runningBalanceAfter = runningBalances[s.id],
                                isSelectedDate = s.date.take(10) == dateIso.take(10),
                                currency = currency,
                                masked = masked,
                                onClick = { selectedSaleForSheet = s }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedSaleForSheet?.let { s ->
        SalesDetailSheet(
            s = s,
            snapshot = snapshot,
            runningBalanceAfter = runningBalances[s.id],
            currency = currency,
            masked = masked,
            isSalesRestricted = isSalesRestricted,
            isAdmin = session.mode == "admin",
            vm = vm,
            onEditEntry = { target -> startEdit(target) },
            onOpenCustomerDetail = { cust -> onOpenCustomerDetail(cust) },
            onBlockedAction = { msg -> blockingAlert = "Action Blocked" to msg },
            onDismiss = { selectedSaleForSheet = null }
        )
    }

    ConfirmRequestHost(confirmReq) { confirmReq = null }

    // Blocking Alert Dialog
    blockingAlert?.let { (title, msg) ->
        SalesBlockingAlertDialog(
            title = title,
            message = msg,
            onDismiss = { blockingAlert = null }
        )
    }

    // Pre-save Confirmation Dialog
    pendingSaveConfirm?.let { model ->
        SalesSaveConfirmDialog(
            model = model,
            onConfirm = {
                pendingSaveConfirm = null
                executeSave()
            },
            onDismiss = { pendingSaveConfirm = null }
        )
    }

    // Money Out Dialog
    moneyOutTarget?.let { targetName ->
        val outBal = SalesAnalyticsEngine.computeCustomerOutstanding(targetName, snapshot)
        CustomerMoneyOutDialog(
            customerName = targetName,
            currentOutstanding = outBal,
            currency = currency,
            masked = masked,
            defaultDate = dateIso,
            onConfirm = { mDate, mAmt, mLedger, mNotes ->
                moneyOutTarget = null
                vm.submitSale(
                    date = mDate,
                    supplyDate = mDate,
                    paymentType = "MONEY_OUT",
                    customer = targetName,
                    store = selectedStore,
                    rep = "NONE",
                    qty = 0.0,
                    rate = 0.0,
                    collectedAmount = mAmt,
                    expiredKg = 0.0,
                    choraRate = 0.0,
                    addToLedger = mLedger,
                    notes = mNotes
                )
            },
            onDismiss = { moneyOutTarget = null }
        )
    }

    // Edit Diff Confirmation Dialog
    if (showEditDiff && editingSale != null) {
        val orig = editingSale!!
        val isOrigRepRet = orig.isRepReturn || orig.paymentType == "REP_RETURN"
        val isOrigColl = FinanceEngine.isCollectionRow(orig)
        val effType = when {
            isOrigRepRet || isRepReturnMode -> "REP_RETURN"
            isOrigColl || entryMode == SalesEntryMode.COLLECTION -> "COLLECTION"
            else -> paymentType
        }
        val origAmt = when {
            isOrigRepRet -> orig.returnValue
            isOrigColl -> orig.collectedAmount
            else -> orig.totalValue
        }
        val newAmt = when (effType) {
            "REP_RETURN" -> repReturnPlan.value
            "COLLECTION" -> collected
            else -> previewTotalValue
        }
        val origQty = if (isOrigRepRet) orig.returnedQty + orig.expiredQty else if (isOrigColl) orig.expiredQty else orig.quantity
        val newQty = if (effType == "REP_RETURN") repReturned + repExpired else if (effType == "COLLECTION") expired else qty
        EditDiffConfirmDialog(
            title = "Confirm Sales Edit",
            rows = listOf(
                Triple("Date", orig.date, dateIso),
                Triple("Customer", orig.customerName, if (effType == "REP_RETURN") selectedRep else customerName.trim()),
                Triple("Type", orig.paymentType, effType),
                Triple("Qty", InputGuards.formatKg(origQty), InputGuards.formatKg(newQty)),
                Triple("Amount", InputGuards.formatMoney(origAmt, currency, masked), InputGuards.formatMoney(newAmt, currency, masked)),
                Triple("Notes", orig.notes, notes.trim())
            ),
            onConfirm = {
                showEditDiff = false
                vm.editSaleOrCollection(
                    original = orig,
                    date = if (effType == "CASH" || effType == "CREDIT") orig.date else dateIso,
                    supplyDate = dateIso,
                    paymentType = effType,
                    customerName = if (effType == "REP_RETURN") selectedRep else customerName,
                    store = selectedStore,
                    salesRep = selectedRep,
                    quantityKg = if (effType == "REP_RETURN") repReturned else qty,
                    unitPrice = if (effType == "REP_RETURN") repReturnUnitPrice else rate,
                    collectedAmount = when (effType) {
                        "CASH" -> previewTotalValue
                        "COLLECTION" -> collected
                        else -> 0.0
                    },
                    expiredKg = when (effType) {
                        "REP_RETURN" -> repExpired
                        "COLLECTION" -> expired
                        else -> 0.0
                    },
                    expiredUnitPrice = if (effType == "COLLECTION") goodsCreditRate else 0.0,
                    notes = notes,
                    customerPhone = if (showNewCustomerPhoneField) customerPhone else ""
                ) { resetForm() }
            },
            onDismiss = { showEditDiff = false }
        )
    }

    if (showDiscardEdit) {
        DiscardEditDialog(
            onDiscard = {
                showDiscardEdit = false
                resetForm()
            },
            onDismiss = { showDiscardEdit = false }
        )
    }
}
