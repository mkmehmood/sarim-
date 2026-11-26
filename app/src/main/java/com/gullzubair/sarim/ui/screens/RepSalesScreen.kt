package com.gullzubair.sarim.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.RepSalesAnalyticsEngine
import com.gullzubair.sarim.domain.SalesAnalyticsEngine
import com.gullzubair.sarim.ui.components.AdminRepAnalyticsCard
import com.gullzubair.sarim.ui.components.AdminRepViewCard
import com.gullzubair.sarim.ui.components.DiscardEditDialog
import com.gullzubair.sarim.ui.components.EditDiffConfirmDialog
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.RepBlockingAlertDialog
import com.gullzubair.sarim.ui.components.RepCustomersDirectoryCard
import com.gullzubair.sarim.ui.components.RepDetailSheet
import com.gullzubair.sarim.ui.components.RepEntryCard
import com.gullzubair.sarim.ui.components.RepEntryMode
import com.gullzubair.sarim.ui.components.RepHistoryRowCard
import com.gullzubair.sarim.ui.components.RepSaveConfirmDialog
import com.gullzubair.sarim.ui.components.RepSaveConfirmModel
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.TabHeading
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimYearMonthDayTree
import com.gullzubair.sarim.ui.theme.CinzelFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.LocationHelper
import com.gullzubair.sarim.util.PdfExporter
import kotlin.math.max

@Composable
fun RepSalesScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onOpenCustomer: (String) -> Unit = {},
    onEditCustomer: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val isAdmin = session.mode == "admin"
    val isOwnRepOnly = remember(session, isAdmin) { FinanceEngine.hasOwnRepAccess(session) && !isAdmin }
    val masked = session.restrictMoney || snapshot.settings.hideSensitiveMoney
    val activeStores = remember(snapshot.stores) { snapshot.stores.filter { it.deletedAt == null && it.active } }

    val knownReps = remember(snapshot) { RepSalesAnalyticsEngine.knownRepProfiles(snapshot) }
    val lockedRepName = remember(snapshot.settings.repProfile, snapshot.profile.displayName) {
        snapshot.settings.repProfile.ifBlank { snapshot.profile.displayName.ifBlank { "Rep" } }
    }
    var selectedAdminRep by remember(snapshot.settings.repProfile, knownReps) {
        mutableStateOf(
            snapshot.settings.repProfile.ifBlank { knownReps.firstOrNull() ?: lockedRepName }
        )
    }
    val activeRepProfile = if (isOwnRepOnly) lockedRepName else selectedAdminRep

    // Entry Card state
    var entryMode by remember { mutableStateOf(RepEntryMode.SALE) }
    var dateIso by remember(todayIso) { mutableStateOf(todayIso) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var selectedStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var paymentType by remember { mutableStateOf("CREDIT") } // Default CREDIT in rep mode
    var qtyStr by remember { mutableStateOf("") }
    var rateStr by remember { mutableStateOf("") }
    var collectedStr by remember { mutableStateOf("") }
    var expiredStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    // GPS state
    var capturedLatLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var gpsStatus by remember { mutableStateOf("GPS not captured") }

    // Analytics / Directory / History state
    var analyticsPeriod by remember { mutableStateOf(RepSalesAnalyticsEngine.RepAnalyticsPeriod.DAY) }
    var customerSearch by remember { mutableStateOf("") }
    var historySearch by remember { mutableStateOf("") }

    // Sheets, Edit & Confirmation Modals
    var selectedRepRec by remember { mutableStateOf<SalesRecord?>(null) }
    var editingRepRec by remember { mutableStateOf<SalesRecord?>(null) }
    var showEditDiff by remember { mutableStateOf(false) }
    var showDiscardEdit by remember { mutableStateOf(false) }
    var pendingSaveConfirm by remember { mutableStateOf<RepSaveConfirmModel?>(null) }
    var blockingAlert by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Attempt silent GPS tag if permission already granted
    LaunchedEffect(Unit) {
        if (LocationHelper.hasLocationPermission(context)) {
            LocationHelper.getBestLastLocation(context)?.let { coords ->
                capturedLatLng = coords
                gpsStatus = "GPS: ${"%.5f".format(coords.first)}, ${"%.5f".format(coords.second)}"
            }
        }
    }

    // Auto-fill effective customer/store rate when not editing
    LaunchedEffect(customerName, selectedStore?.key, activeRepProfile, editingRepRec?.id) {
        if (editingRepRec == null) {
            val effRate = RepSalesAnalyticsEngine.effectiveRepRate(
                customerName = customerName,
                store = selectedStore,
                clients = snapshot.clients,
                repProfile = activeRepProfile,
                defaultPrices = snapshot.settings.defaultStorePrices
            )
            rateStr = if (effRate > 0.0) InputGuards.fmtNum(effRate).replace(",", "") else ""
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val coords = LocationHelper.getBestLastLocation(context)
            capturedLatLng = coords
            gpsStatus = if (coords != null) "GPS: ${"%.5f".format(coords.first)}, ${"%.5f".format(coords.second)}" else "Location unavailable; try outdoors"
        } else {
            gpsStatus = "Location permission denied"
        }
    }

    val editingSpecialType = remember(editingRepRec) {
        when {
            editingRepRec == null -> null
            editingRepRec!!.isRepReturn || editingRepRec!!.paymentType == "REP_RETURN" -> "REP_RETURN"
            editingRepRec!!.paymentType == "MONEY_OUT" -> "MONEY_OUT"
            else -> null
        }
    }

    fun resetForm() {
        editingRepRec = null
        entryMode = RepEntryMode.SALE
        dateIso = todayIso
        customerName = ""
        customerPhone = ""
        paymentType = "CREDIT"
        qtyStr = ""
        collectedStr = ""
        expiredStr = ""
        notes = ""
    }

    fun startEdit(r: SalesRecord) {
        val block = RepSalesAnalyticsEngine.getRepSaleBlockReason(snapshot, r, forEdit = true)
        if (block.isNotBlank()) {
            blockingAlert = "Cannot Edit" to block
            return
        }
        editingRepRec = r
        dateIso = r.date.ifBlank { todayIso }
        if (isAdmin && r.salesRep.isNotBlank()) {
            selectedAdminRep = r.salesRep
        }
        entryMode = when {
            r.paymentType == "COLLECTION" || r.paymentType == "PARTIAL_PAYMENT" || r.isCollection || r.paymentType == "MONEY_OUT" -> RepEntryMode.COLLECTION
            else -> RepEntryMode.SALE
        }
        customerName = r.customerName
        customerPhone = r.customerPhone
        val st = activeStores.firstOrNull { it.key.equals(r.supplyStore, true) || it.name.equals(r.supplyStore, true) }
        if (st != null) selectedStore = st
        paymentType = if (r.paymentType == "CASH") "CASH" else "CREDIT"
        val shownQty = if (r.isRepReturn || r.paymentType == "REP_RETURN") r.returnedQty else r.quantity
        qtyStr = if (shownQty > 0.0) InputGuards.fmtNum(shownQty).replace(",", "") else ""
        rateStr = if (r.unitPrice > 0.0) InputGuards.fmtNum(r.unitPrice).replace(",", "") else ""
        val shownCash = when {
            r.isCollection || r.paymentType == "COLLECTION" || r.paymentType == "PARTIAL_PAYMENT" -> r.collectedAmount
            r.paymentType == "MONEY_OUT" -> if (r.collectedAmount > 0.0) r.collectedAmount else r.totalValue
            else -> 0.0
        }
        collectedStr = if (shownCash > 0.0) InputGuards.fmtNum(shownCash).replace(",", "") else ""
        expiredStr = if (r.expiredQty > 0.0) InputGuards.fmtNum(r.expiredQty).replace(",", "") else ""
        notes = r.notes
    }

    BackHandler(enabled = editingRepRec != null) {
        showDiscardEdit = true
    }

    // Derived live data
    val qty = qtyStr.toDoubleOrNull() ?: 0.0
    val rate = rateStr.toDoubleOrNull() ?: RepSalesAnalyticsEngine.effectiveRepRate(
        customerName, selectedStore, snapshot.clients, activeRepProfile, snapshot.settings.defaultStorePrices
    )
    val collected = collectedStr.toDoubleOrNull() ?: 0.0
    val expired = expiredStr.toDoubleOrNull() ?: 0.0

    val previewTotalValue = InputGuards.round2(qty * rate)
    val costIdx = remember(snapshot.production) { FormulaEngine.buildCostIndex(snapshot.production) }
    val previewUnitCost = remember(costIdx, selectedStore, dateIso) {
        selectedStore?.let { FormulaEngine.costSourceFromIndex(costIdx, it.key.ifBlank { it.name }, dateIso).cp } ?: 0.0
    }
    val previewProfit = InputGuards.round2(previewTotalValue - (qty * previewUnitCost))

    val isRepNameCollision = remember(customerName, knownReps) {
        RepSalesAnalyticsEngine.isRepOwnNameCollision(customerName, knownReps)
    }
    val existingDebt = remember(snapshot, customerName, activeRepProfile, editingRepRec?.id) {
        RepSalesAnalyticsEngine.computeRepClientOutstanding(
            customerName = customerName,
            repProfile = activeRepProfile,
            snapshot = snapshot,
            excludeRecordId = editingRepRec?.id
        )
    }
    val customerTotalQty = remember(snapshot.repRecords, customerName, activeRepProfile) {
        val clean = customerName.trim()
        if (clean.isBlank()) 0.0
        else InputGuards.round2(
            RepSalesAnalyticsEngine.scopedRepRecords(snapshot.repRecords, activeRepProfile)
                .filter { it.customerName.equals(clean, ignoreCase = true) }
                .sumOf { FinanceEngine.soldQuantity(it) }
        )
    }
    val goodsCreditRate = remember(snapshot, activeRepProfile, customerName, selectedStore, editingRepRec?.id) {
        RepSalesAnalyticsEngine.computeRepGoodsCreditUnitPrice(
            repProfile = activeRepProfile,
            customerName = customerName,
            snapshot = snapshot,
            excludeRecordId = editingRepRec?.id,
            fallbackStore = selectedStore
        )
    }
    val goodsCreditPlan = remember(expired, goodsCreditRate, existingDebt, collected) {
        SalesAnalyticsEngine.planGoodsCredit(
            qtyKg = expired,
            unitPrice = goodsCreditRate,
            outstanding = max(0.0, existingDebt - collected)
        )
    }

    val matchingVisitsWithin150m = remember(capturedLatLng, customerName, snapshot.repRecords) {
        val loc = capturedLatLng ?: return@remember 0
        snapshot.repRecords.count { rec ->
            rec.deletedAt == null &&
                rec.customerName.equals(customerName.trim(), ignoreCase = true) &&
                rec.gpsLat != null && rec.gpsLng != null &&
                LocationHelper.distanceMeters(loc.first, loc.second, rec.gpsLat, rec.gpsLng) <= 150.0
        }
    }
    val gpsConfirmedCluster = matchingVisitsWithin150m >= 2

    val directory = remember(snapshot, activeRepProfile, customerSearch) {
        RepSalesAnalyticsEngine.computeRepClientDirectory(snapshot, activeRepProfile, customerSearch)
    }
    val suggestionPool = remember(snapshot, activeRepProfile, customerName) {
        RepSalesAnalyticsEngine.computeRepClientDirectory(snapshot, activeRepProfile, customerName).items
    }
    val showNewCustomerPhoneField = remember(customerName, snapshot, activeRepProfile, isRepNameCollision) {
        val clean = customerName.trim()
        !isRepNameCollision && clean.length > 2 &&
            snapshot.clients.none { it.deletedAt == null && it.name.equals(clean, ignoreCase = true) } &&
            RepSalesAnalyticsEngine.scopedRepRecords(snapshot.repRecords, activeRepProfile)
                .none { it.customerName.equals(clean, ignoreCase = true) }
    }

    val analytics = remember(snapshot, activeRepProfile, analyticsPeriod, dateIso) {
        RepSalesAnalyticsEngine.computeRepAnalytics(snapshot, activeRepProfile, analyticsPeriod, dateIso)
    }
    val routeStops = remember(snapshot, activeRepProfile, dateIso) {
        RepSalesAnalyticsEngine.computeDailyRouteStops(snapshot, activeRepProfile, dateIso)
    }
    val runningBalances = remember(snapshot, activeRepProfile) {
        RepSalesAnalyticsEngine.computeRepRunningBalances(snapshot, activeRepProfile)
    }
    val filteredHistory = remember(snapshot.repRecords, activeRepProfile, customerName, historySearch) {
        val cf = customerName.trim().lowercase()
        val hs = historySearch.trim().lowercase()
        RepSalesAnalyticsEngine.scopedRepRecords(snapshot.repRecords, activeRepProfile).filter { r ->
            if (cf.isNotBlank() && !r.customerName.lowercase().contains(cf)) return@filter false
            if (hs.isNotBlank()) {
                val hay = "${r.customerName} ${r.paymentType} ${r.supplyStore} ${r.notes} ${r.date}".lowercase()
                if (!hay.contains(hs)) return@filter false
            }
            true
        }.sortedWith(compareByDescending<SalesRecord> { it.date }.thenByDescending { it.timestamp })
    }

    fun executeSave() {
        vm.submitRepRecord(
            date = dateIso,
            repName = activeRepProfile,
            paymentType = if (entryMode == RepEntryMode.COLLECTION) "COLLECTION" else paymentType,
            customer = customerName,
            store = selectedStore,
            qty = if (entryMode == RepEntryMode.SALE) qty else 0.0,
            rate = if (entryMode == RepEntryMode.SALE) rate else 0.0,
            collectedAmount = when {
                entryMode == RepEntryMode.COLLECTION -> collected
                paymentType == "CASH" -> previewTotalValue
                else -> 0.0
            },
            expiredKg = if (entryMode == RepEntryMode.COLLECTION) expired else 0.0,
            lat = capturedLatLng?.first,
            lng = capturedLatLng?.second,
            accuracy = null,
            confirmed = gpsConfirmedCluster,
            notes = notes,
            customerPhone = if (showNewCustomerPhoneField) customerPhone else "",
            expiredUnitPrice = if (entryMode == RepEntryMode.COLLECTION) goodsCreditRate else 0.0
        )
        resetForm()
    }

    fun validateAndSave() {
        if (DateUtils.parseIso(dateIso) == null || dateIso > todayIso) {
            blockingAlert = "Invalid Date" to "Please pick a valid date up to today (${DateUtils.formatPretty(todayIso)})."
            return
        }
        val cleanCust = InputGuards.sanitizeName(customerName)
        if (cleanCust.isBlank()) {
            blockingAlert = "Customer Required" to "Please enter customer name."
            return
        }
        if (isRepNameCollision) {
            blockingAlert = "Invalid Customer" to "Customer name cannot match a rep profile name."
            return
        }
        if (editingRepRec != null) {
            if (editingSpecialType == null && entryMode == RepEntryMode.SALE && (qty <= 0.0 || rate <= 0.0)) {
                blockingAlert = "Missing Details" to "Enter valid quantity and rate."
                return
            }
            if (entryMode == RepEntryMode.COLLECTION && collected <= 0.0 && expired <= 0.0) {
                blockingAlert = "Missing Details" to "Enter amount received or expired kg."
                return
            }
            showEditDiff = true
            return
        }
        if (entryMode == RepEntryMode.COLLECTION) {
            if (collected < 0.0 || expired < 0.0 || (collected <= 0.0 && expired <= 0.0)) {
                blockingAlert = "Invalid Collection" to "Please enter amount collected and/or expired quantity."
                return
            }
            if (collected > 0.0 && existingDebt <= 0.005) {
                blockingAlert = "Credit Error" to "$cleanCust has no outstanding credit balance. Collections require existing unpaid credit."
                return
            }
            val overWarn = if (collected > 0.0 && collected > existingDebt + 0.01) {
                val over = collected - existingDebt
                "$cleanCust owes ${InputGuards.formatMoney(existingDebt, currency, masked)}. Collecting ${InputGuards.formatMoney(collected, currency, masked)} is an overpayment of ${InputGuards.formatMoney(over, currency, masked)}."
            } else null
            val notesList = buildList {
                if (expired > 0.0) {
                    add("Expired ${InputGuards.formatKg(expired)} will be added to CHORA.")
                    if (!masked && goodsCreditPlan.value > 0.0) {
                        add("Expired goods credit: ${InputGuards.formatMoney(goodsCreditPlan.value, currency, false)}${if (goodsCreditPlan.capped) " (capped at owed balance)" else ""}.")
                    }
                }
            }
            val lines = buildList {
                add("Customer" to cleanCust)
                add("Rep" to activeRepProfile)
                add("Date" to DateUtils.formatPretty(dateIso))
                if (collected > 0.0) add("Cash Collected" to InputGuards.formatMoney(collected, currency, masked))
                if (expired > 0.0) add("Expired -> CHORA" to InputGuards.formatKg(expired))
            }
            pendingSaveConfirm = RepSaveConfirmModel(
                title = "Confirm Collection",
                lines = lines,
                warning = overWarn,
                notes = notesList,
                confirmLabel = if (overWarn != null) "Collect Anyway" else "Confirm & Save"
            )
        } else {
            val st = selectedStore
            if (st == null) {
                blockingAlert = "Store Required" to "Please select a store."
                return
            }
            if (qty <= 0.0 || rate <= 0.0) {
                blockingAlert = "Invalid Sale" to "Please enter valid quantity and sale price."
                return
            }
            if (previewUnitCost > 0.0 && previewProfit < -0.01) {
                blockingAlert = "Negative Margin Blocked" to "Sale value is below cost price. Increase rate or check store price."
                return
            }
            val creditWarn = if (paymentType == "CREDIT" && existingDebt > 5000.0) {
                "$cleanCust already has unpaid balance of ${InputGuards.formatMoney(existingDebt, currency, masked)}. New balance will be ${InputGuards.formatMoney(existingDebt + previewTotalValue, currency, masked)}."
            } else null
            pendingSaveConfirm = RepSaveConfirmModel(
                title = "Confirm Rep $paymentType Sale",
                lines = listOf(
                    "Customer" to cleanCust,
                    "Rep" to activeRepProfile,
                    "Store" to st.name,
                    "Date" to DateUtils.formatPretty(dateIso),
                    "Quantity" to InputGuards.formatKg(qty),
                    "Rate / kg" to InputGuards.formatMoney(rate, currency, masked),
                    "Total Value" to InputGuards.formatMoney(previewTotalValue, currency, masked)
                ),
                warning = creditWarn,
                confirmLabel = if (creditWarn != null) "Add Credit Anyway" else "Confirm & Save"
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabHeading("Rep Sales")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accents.softPillFill,
                    border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = activeRepProfile,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = accents.emerald,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // 1. Unified Rep Entry Form
        item {
            RepEntryCard(
                isEditing = editingRepRec != null,
                editingSpecialType = editingSpecialType,
                isOwnRepOnly = isOwnRepOnly,
                activeRepProfile = activeRepProfile,
                masked = masked,
                currency = currency,
                entryMode = entryMode,
                onSelectEntryMode = { entryMode = it },
                dateIso = dateIso,
                onDateChange = { dateIso = it },
                customerName = customerName,
                onCustomerNameChange = { customerName = it },
                customerSuggestions = suggestionPool,
                isRepNameCollision = isRepNameCollision,
                showNewCustomerPhoneField = showNewCustomerPhoneField,
                customerPhone = customerPhone,
                onCustomerPhoneChange = { customerPhone = it },
                existingDebt = existingDebt,
                customerTotalQty = customerTotalQty,
                onOpenCustomerLedger = onOpenCustomer,
                onClearCustomer = { customerName = ""; customerPhone = "" },
                activeStores = activeStores,
                selectedStore = selectedStore,
                onSelectStore = { selectedStore = it },
                paymentType = paymentType,
                onSelectPaymentType = { paymentType = it },
                qtyStr = qtyStr,
                onQtyChange = { qtyStr = it },
                rateStr = rateStr,
                onRateChange = { rateStr = it },
                previewTotalValue = previewTotalValue,
                previewUnitCost = previewUnitCost,
                previewProfit = previewProfit,
                collectedStr = collectedStr,
                onCollectedChange = { collectedStr = it },
                expiredStr = expiredStr,
                onExpiredChange = { expiredStr = it },
                goodsCreditRate = goodsCreditRate,
                goodsCreditPlan = goodsCreditPlan,
                gpsStatus = gpsStatus,
                gpsConfirmedCluster = gpsConfirmedCluster,
                onCaptureGps = {
                    if (LocationHelper.hasLocationPermission(context)) {
                        val coords = LocationHelper.getBestLastLocation(context)
                        capturedLatLng = coords
                        gpsStatus = if (coords != null) "GPS: ${"%.5f".format(coords.first)}, ${"%.5f".format(coords.second)}" else "Waiting for GPS fix"
                    } else {
                        locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                notes = notes,
                onNotesChange = { notes = it },
                onCancelEdit = { showDiscardEdit = true },
                onSaveClick = { validateAndSave() }
            )
        }

        // 2 & 3. Admin View (Rep Switcher, GPS Route Map, Rep Analytics) - hidden for rep-restricted users
        if (isAdmin && !masked) {
            item {
                AdminRepViewCard(
                    availableReps = knownReps,
                    selectedRep = activeRepProfile,
                    onSelectRep = { selectedAdminRep = it },
                    dateIso = dateIso,
                    onDateChange = { dateIso = it },
                    routeStops = routeStops
                )
            }
            item {
                AdminRepAnalyticsCard(
                    activeRepProfile = activeRepProfile,
                    analytics = analytics,
                    selectedPeriod = analyticsPeriod,
                    onSelectPeriod = { analyticsPeriod = it },
                    currency = currency,
                    masked = masked
                )
            }
        }

        // 4. Rep Customer Directory
        item {
            RepCustomersDirectoryCard(
                directory = directory,
                searchQuery = customerSearch,
                onSearchChange = { customerSearch = it },
                currency = currency,
                masked = masked,
                onExportPdf = {
                    val (rows, footer) = RepSalesAnalyticsEngine.buildAllRepClientsPdfRows(snapshot, activeRepProfile, masked)
                    if (rows.isEmpty()) {
                        Toast.makeText(context, "No rep customers to export", Toast.LENGTH_SHORT).show()
                    } else {
                        PdfExporter.exportAndShareStatement(
                            context = context,
                            title = "$activeRepProfile — Clients Summary",
                            subtitle = "Gull & Zubair Naswar Dealers",
                            rows = rows,
                            summaryFooter = footer
                        )
                    }
                },
                onAddCustomer = { onEditCustomer("") },
                onOpenCustomer = onOpenCustomer,
                onEditCustomer = onEditCustomer
            )
        }

        // 5. Activity History Tree
        item {
            SarimCard(contentPadding = PaddingValues(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Activity", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("${filteredHistory.size} entries", style = MaterialTheme.typography.labelMedium, color = accents.emerald)
                }
                if (customerName.trim().isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        onClick = { customerName = "" },
                        shape = RoundedCornerShape(10.dp),
                        color = accents.cyan.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, accents.cyan.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("rep_history_clear_cust_filter")
                    ) {
                        Text(
                            text = "Filtered by \"${customerName.trim()}\" • Tap to show all",
                            style = MaterialTheme.typography.labelSmall,
                            color = accents.cyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                SarimPillInput(
                    value = historySearch,
                    onValueChange = { historySearch = it },
                    placeholder = "Search activity by customer, store, notes...",
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    testTag = "rep_history_search"
                )
                Spacer(Modifier.height(10.dp))
                if (filteredHistory.isEmpty()) {
                    EmptyStateCard("No rep activity", "Saved field sales and collections appear here.")
                } else {
                    SarimYearMonthDayTree(
                        items = filteredHistory,
                        dateOf = { it.date },
                        summaryLabel = { list ->
                            val kg = list.sumOf { FinanceEngine.soldQuantity(it) }
                            val cash = list.sumOf { it.collectedAmount }
                            "${InputGuards.formatKg(kg)} • ${InputGuards.formatMoney(cash, currency, masked)}"
                        },
                        itemRow = { r ->
                            RepHistoryRowCard(
                                r = r,
                                stores = snapshot.stores,
                                runningBalanceAfter = runningBalances[r.id],
                                isSelectedDate = r.date.take(10) == dateIso.take(10),
                                currency = currency,
                                masked = masked,
                                onClick = { selectedRepRec = r }
                            )
                        }
                    )
                }
            }
        }
    }

    selectedRepRec?.let { r ->
        RepDetailSheet(
            r = r,
            snapshot = snapshot,
            runningBalanceAfter = runningBalances[r.id],
            currency = currency,
            masked = masked,
            isAdmin = isAdmin,
            vm = vm,
            onEditEntry = { startEdit(it) },
            onOpenCustomerLedger = onOpenCustomer,
            onBlockedAction = { msg -> blockingAlert = "Action Blocked" to msg },
            onDismiss = { selectedRepRec = null }
        )
    }

    blockingAlert?.let { (title, msg) ->
        RepBlockingAlertDialog(title = title, message = msg, onDismiss = { blockingAlert = null })
    }

    pendingSaveConfirm?.let { model ->
        RepSaveConfirmDialog(
            model = model,
            onConfirm = {
                pendingSaveConfirm = null
                executeSave()
            },
            onDismiss = { pendingSaveConfirm = null }
        )
    }

    if (showEditDiff && editingRepRec != null) {
        val orig = editingRepRec!!
        val effType = when {
            editingSpecialType != null -> editingSpecialType
            entryMode == RepEntryMode.COLLECTION -> "COLLECTION"
            else -> paymentType
        }
        val origAmt = when {
            orig.isRepReturn || orig.paymentType == "REP_RETURN" -> orig.returnValue
            FinanceEngine.isCollectionRow(orig) -> orig.collectedAmount
            else -> orig.totalValue
        }
        val newAmt = when (effType) {
            "COLLECTION", "MONEY_OUT" -> collected
            else -> previewTotalValue
        }
        EditDiffConfirmDialog(
            title = "Confirm Rep Edit",
            rows = listOf(
                Triple("Date", orig.date, dateIso),
                Triple("Customer", orig.customerName, customerName.trim()),
                Triple("Type", orig.paymentType, effType),
                Triple("Qty", InputGuards.formatKg(if (orig.isRepReturn) orig.returnedQty else orig.quantity), InputGuards.formatKg(qty)),
                Triple("Amount", InputGuards.formatMoney(origAmt, currency, masked), InputGuards.formatMoney(newAmt, currency, masked)),
                Triple("Notes", orig.notes, notes.trim())
            ),
            onConfirm = {
                showEditDiff = false
                vm.editRepRecord(
                    original = orig,
                    date = dateIso,
                    paymentType = effType,
                    customerName = customerName,
                    store = selectedStore,
                    quantityKg = if (effType == "COLLECTION" || effType == "MONEY_OUT") 0.0 else qty,
                    unitPrice = if (effType == "COLLECTION" || effType == "MONEY_OUT") 0.0 else rate,
                    collectedAmount = when (effType) {
                        "COLLECTION", "MONEY_OUT" -> collected
                        "CASH" -> previewTotalValue
                        else -> 0.0
                    },
                    expiredKg = if (effType == "COLLECTION" || effType == "REP_RETURN") expired else 0.0,
                    notes = notes,
                    customerPhone = customerPhone,
                    expiredUnitPrice = if (effType == "COLLECTION") goodsCreditRate else 0.0
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
