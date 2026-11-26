package com.gullzubair.sarim.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import kotlin.math.max
import androidx.compose.runtime.Composable
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.ProductionAnalyticsEngine
import com.gullzubair.sarim.ui.components.AllStoresOverviewSection
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DiscardEditDialog
import com.gullzubair.sarim.ui.components.EditDiffConfirmDialog
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.MfgAnalyticsSection
import com.gullzubair.sarim.ui.components.ProdPhotoSlot
import com.gullzubair.sarim.ui.components.ProductionDetailSheet
import com.gullzubair.sarim.ui.components.ProductionEntryCard
import com.gullzubair.sarim.ui.components.ProductionHistoryRowCard
import com.gullzubair.sarim.ui.components.ProductionMultiShareBar
import com.gullzubair.sarim.ui.components.ProductionPhotoLightboxDialog
import com.gullzubair.sarim.ui.components.ProductionSaveConfirmDialog
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.TabHeading
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimYearMonthDayTree
import com.gullzubair.sarim.ui.components.StoreComparisonChartCard
import com.gullzubair.sarim.ui.theme.CinzelFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.CaptionedPhotoSharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun ProductionScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onOpenStockTransfer: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currency = snapshot.settings.currencySymbol
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val isEntryOnlyProd = remember(session) {
        session.mode == "production" || (FinanceEngine.isMoneyRestricted(session) && "prod" in session.tabs)
    }
    val isMoneyRestrictedMode = remember(session, isEntryOnlyProd) {
        isEntryOnlyProd || FinanceEngine.isMoneyRestricted(session)
    }
    val masked = isMoneyRestrictedMode || session.restrictMoney || snapshot.settings.hideSensitiveMoney
    val activeStores = remember(snapshot.stores) {
        snapshot.stores.filter { it.deletedAt == null && it.active }
    }

    // View mode: "store" (default, matches web) vs "combined"
    var prodView by remember(isMoneyRestrictedMode) { mutableStateOf("store") }
    val effectiveView = if (isMoneyRestrictedMode) "store" else prodView

    // Entry form state (matches web #sys-date, #gross-wt, #cont-wt, #storeSelector, #formula-units)
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var selectedStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var grossStr by remember { mutableStateOf("") }
    var containerStr by remember { mutableStateOf("") }
    var unitsStr by remember { mutableStateOf("1") }
    var photoSlots by remember { mutableStateOf<List<ProdPhotoSlot>>(emptyList()) }

    // Overview & Analytics controls
    var overviewPeriod by remember { mutableStateOf(ProductionAnalyticsEngine.OverviewPeriod.DAY) }
    var comparisonMetric by remember { mutableStateOf(ProductionAnalyticsEngine.ComparisonMetric.WEIGHT) }
    var mfgPeriod by remember { mutableStateOf(ProductionAnalyticsEngine.MfgChartPeriod.WEEK) }
    var historySearch by remember { mutableStateOf("") }

    // Edit & Confirmation Dialogs
    var editingRecord by remember { mutableStateOf<ProductionRecord?>(null) }
    var showSaveConfirmDialog by remember { mutableStateOf(false) }
    var showEditDiffConfirm by remember { mutableStateOf(false) }
    var showDiscardEditDialog by remember { mutableStateOf(false) }

    // Detail, Lightbox & Multi-photo Share
    var selectedRecord by remember { mutableStateOf<ProductionRecord?>(null) }
    var expandedHistoryId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ProductionRecord?>(null) }
    var lightboxState by remember { mutableStateOf<Triple<List<String>, Int, ProductionRecord?>?>(null) }
    var selectedPhotoRecIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun resetEntryForm() {
        editingRecord = null
        date = todayIso
        grossStr = ""
        containerStr = ""
        unitsStr = "1"
        photoSlots = emptyList()
    }

    fun startEditing(rec: ProductionRecord) {
        editingRecord = rec
        prodView = "store"
        date = rec.date.ifBlank { todayIso }
        selectedStore = activeStores.firstOrNull {
            ProductionAnalyticsEngine.matchesStore(rec.store, it)
        } ?: activeStores.firstOrNull()
        grossStr = if (rec.grossWt > 0.0) InputGuards.fmtNum(rec.grossWt).replace(",", "") else ""
        containerStr = if (rec.contWt > 0.0) InputGuards.fmtNum(rec.contWt).replace(",", "") else ""
        unitsStr = if (rec.formulaUnits > 0.0) InputGuards.fmtNum(rec.formulaUnits).replace(",", "") else "1"
        photoSlots = rec.photoKeys.mapNotNull { key ->
            snapshot.photos[key]?.takeIf { !it.deleted && it.dataUri.isNotBlank() }?.let { blob ->
                ProdPhotoSlot(existingKey = key, dataUrl = blob.dataUri)
            }
        }
    }

    BackHandler(enabled = editingRecord != null) {
        showDiscardEditDialog = true
    }

    val multiPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val room = (6 - photoSlots.size).coerceAtLeast(0)
                val encoded = withContext(Dispatchers.IO) {
                    uris.take(room).mapNotNull { uri ->
                        CaptionedPhotoSharer.compressUriToDataUrl(context, uri)?.let { ProdPhotoSlot(null, it) }
                    }
                }
                if (encoded.isNotEmpty()) {
                    photoSlots = (photoSlots + encoded).take(6)
                }
            }
        }
    }

    val gross = grossStr.toDoubleOrNull() ?: 0.0
    val container = containerStr.toDoubleOrNull() ?: 0.0
    val net = InputGuards.round2((gross - container).coerceAtLeast(0.0))
    val units = unitsStr.toDoubleOrNull() ?: 0.0

    val dynamicCost = remember(snapshot, selectedStore, units, net, editingRecord) {
        ProductionAnalyticsEngine.computeDynamicProdCost(
            snapshot = snapshot,
            store = selectedStore,
            formulaUnits = units,
            netWeight = net,
            editingRecord = editingRecord
        )
    }

    // Mirrors web `_recordEntryImpl`: same checks, same order, same wording.
    fun validateBeforeSubmit(): Boolean {
        fun warn(message: String, long: Boolean = false): Boolean {
            Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
            return false
        }
        val dateProblem = InputGuards.dateIssue(date, todayIso)
        if (dateProblem.isNotEmpty()) return warn(dateProblem)
        val weightProblem = InputGuards.numberIssue(
            grossStr, label = "Gross weight", min = InputGuards.QTY_MIN, max = InputGuards.QTY_MAX
        ).ifEmpty {
            InputGuards.numberIssue(
                containerStr, label = "Container weight", min = 0.0, max = InputGuards.QTY_MAX, allowBlank = true
            )
        }.ifEmpty {
            InputGuards.numberIssue(
                unitsStr, label = "Formula units", min = 0.0, max = InputGuards.QTY_MAX,
                minExclusive = true, allowBlank = true
            )
        }
        if (weightProblem.isNotEmpty()) return warn(weightProblem)
        val st = selectedStore ?: return warn("Select an active store first.")
        if (units > dynamicCost.effectiveAvailable + 1e-9) {
            return warn(
                "Not enough formula units at ${st.name}\n" +
                    "Available: ${InputGuards.fmtNum(dynamicCost.effectiveAvailable)} units\n" +
                    "Requested: ${InputGuards.fmtNum(units)} units\n" +
                    "Shortage: ${InputGuards.fmtNum(max(0.0, units - dynamicCost.effectiveAvailable))} units",
                long = true
            )
        }
        if (net <= 0.0) return warn("Net production must be greater than zero. Please check weights.")
        if (date.isBlank()) return warn("Please select a date.")
        if (dynamicCost.salePrice <= 0.0) return warn("Please set a sale price in Factory Formulas first.", long = true)
        if (units <= 0.0) return warn("Please enter formula units used.")
        if (!(dynamicCost.dynamicCostPerKg > 0.0)) {
            return warn(
                "This formula has no cost (no ingredients or ingredient costs are zero). " +
                    "Set the formula and ingredient costs first, otherwise sales from this production get a wrong cost.",
                long = true
            )
        }
        return true
    }

    val allStoresOverview = remember(snapshot, overviewPeriod, date, effectiveView) {
        if (effectiveView == "combined" && !isMoneyRestrictedMode) {
            ProductionAnalyticsEngine.computeAllStoresOverview(snapshot, overviewPeriod, date)
        } else null
    }
    val storeComparisonPoints = remember(snapshot, overviewPeriod, comparisonMetric, date, effectiveView) {
        if (effectiveView == "combined" && !isMoneyRestrictedMode) {
            ProductionAnalyticsEngine.computeStoreComparison(snapshot, overviewPeriod, comparisonMetric, date)
        } else emptyList()
    }
    val mfgAnalytics = remember(snapshot, mfgPeriod, date, effectiveView, selectedStore) {
        if (effectiveView == "combined" && !isMoneyRestrictedMode) {
            ProductionAnalyticsEngine.computeMfgAnalytics(snapshot, mfgPeriod, date, true, selectedStore)
        } else null
    }

    val filteredHistory = remember(snapshot.production, snapshot.stores, effectiveView, selectedStore, date, historySearch, isMoneyRestrictedMode) {
        if (isMoneyRestrictedMode) return@remember emptyList()
        val q = historySearch.trim().lowercase()
        val base = snapshot.production.filter { rec ->
            if (rec.deletedAt != null) return@filter false
            if (effectiveView == "store") {
                val st = selectedStore ?: return@filter false
                if (!ProductionAnalyticsEngine.matchesStore(rec.store, st)) return@filter false
                if (rec.date.isNotBlank() && rec.date.take(10) != date.take(10)) return@filter false
            }
            if (q.isNotBlank()) {
                val storeName = FormulaEngine.storeLabelFor(rec.store, snapshot.stores)
                val hay = listOf(storeName, rec.store, rec.date, rec.returnNote, rec.transferNote, rec.returnedBy, rec.formulaName, rec.managedBy)
                    .joinToString(" ")
                    .lowercase()
                if (q !in hay) return@filter false
            }
            true
        }
        base.sortedWith(
            compareByDescending<ProductionRecord> { it.date.take(10) == date.take(10) }
                .thenByDescending { it.date }
                .thenByDescending { it.timestamp }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Header (#prod-tab-header)
            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TabHeading("Manufacturing")
                    if (!isMoneyRestrictedMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                onClick = onOpenStockTransfer,
                                shape = RoundedCornerShape(14.dp),
                                color = accents.inputFill,
                                border = BorderStroke(1.dp, accents.borderSubtle),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 40.dp)
                                    .testTag("prod_open_transfer_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = "Transfer Stock", tint = accents.emerald, modifier = Modifier.size(16.dp))
                                    Text("Transfer", style = MaterialTheme.typography.labelMedium, color = accents.emerald)
                                }
                            }
                        }
                    }
                }
                if (!isMoneyRestrictedMode) {
                    Spacer(Modifier.height(10.dp))
                    // #prod-toggle-container (Combined vs Store Entry)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = accents.inputFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("combined" to "Combined", "store" to "Store Entry").forEach { (key, title) ->
                                val selected = effectiveView == key
                                Surface(
                                    onClick = { prodView = key },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) accents.softPillFill else accents.inputFill,
                                    border = if (selected) BorderStroke(1.dp, accents.emerald) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 44.dp)
                                        .testTag("prod_view_$key")
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (selected) accents.emerald else accents.textMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (effectiveView == "store") {
                // Store Entry Card (#production-entry-section)
                item {
                    ProductionEntryCard(
                        isEditing = editingRecord != null,
                        isMoneyRestrictedMode = isMoneyRestrictedMode,
                        masked = masked,
                        currency = currency,
                        date = date,
                        onDateChange = { date = it },
                        grossStr = grossStr,
                        onGrossChange = { grossStr = it },
                        containerStr = containerStr,
                        onContainerChange = { containerStr = it },
                        net = net,
                        activeStores = activeStores,
                        selectedStore = selectedStore,
                        onSelectStore = { selectedStore = it },
                        unitsStr = unitsStr,
                        onUnitsChange = { unitsStr = it },
                        unitsVal = units,
                        dynamicCost = dynamicCost,
                        photoSlots = photoSlots,
                        onAttachPhotoClick = {
                            multiPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onClearPhotosClick = { photoSlots = emptyList() },
                        onRemovePhotoAt = { idx -> photoSlots = photoSlots.filterIndexed { i, _ -> i != idx } },
                        onPreviewPhotoAt = { idx -> lightboxState = Triple(photoSlots.map { it.dataUrl }, idx, editingRecord) },
                        onCancelEdit = { showDiscardEditDialog = true },
                        onSaveClick = {
                            if (validateBeforeSubmit()) {
                                if (editingRecord != null) showEditDiffConfirm = true
                                else showSaveConfirmDialog = true
                            }
                        }
                    )
                }
            } else {
                // Combined View: Date anchor + All Stores Overview + Store Comparison + Mfg Analytics
                item {
                    SarimCard(contentPadding = PaddingValues(14.dp)) {
                        DatePickerField("Overview Date", date, { date = it }, "prod_combined_date")
                    }
                }
                allStoresOverview?.let { ov ->
                    item {
                        AllStoresOverviewSection(
                            overview = ov,
                            selectedPeriod = overviewPeriod,
                            onSelectPeriod = { overviewPeriod = it },
                            currency = currency,
                            masked = masked
                        )
                    }
                }
                item {
                    StoreComparisonChartCard(
                        points = storeComparisonPoints,
                        selectedMetric = comparisonMetric,
                        onSelectMetric = { comparisonMetric = it },
                        periodLabel = if (overviewPeriod == ProductionAnalyticsEngine.OverviewPeriod.ALL) "All Times" else overviewPeriod.label,
                        currency = currency,
                        masked = masked
                    )
                }
                mfgAnalytics?.let { mfg ->
                    item {
                        MfgAnalyticsSection(
                            analytics = mfg,
                            selectedPeriod = mfgPeriod,
                            onSelectPeriod = { mfgPeriod = it },
                            currency = currency,
                            masked = masked
                        )
                    }
                }
            }

            // Production History Section (#prod-history-header + #prodHistoryList)
            if (!isMoneyRestrictedMode) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Production History",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = accents.softPillFill
                        ) {
                            Text(
                                text = "${filteredHistory.size} ${if (filteredHistory.size == 1) "entry" else "entries"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accents.emerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SarimPillInput(
                        value = historySearch,
                        onValueChange = { historySearch = it },
                        placeholder = "Search production history...",
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search history") },
                        testTag = "prod_history_search"
                    )
                }

                if (filteredHistory.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No records found for this selection",
                            detail = if (effectiveView == "store") {
                                "No entries for ${selectedStore?.name ?: "selected store"} on ${DateUtils.formatPretty(date)}."
                            } else {
                                "Log a production batch to see manufacturing history."
                            }
                        )
                    }
                } else if (effectiveView == "store") {
                    items(filteredHistory, key = { it.id }) { rec ->
                        ProductionHistoryRowCard(
                            rec = rec,
                            stores = snapshot.stores,
                            photoStore = snapshot.photos,
                            selectedDate = date,
                            showStoreChip = false,
                            currency = currency,
                            masked = masked,
                            isPhotoSelected = rec.id in selectedPhotoRecIds,
                            expanded = expandedHistoryId == rec.id,
                            onToggleExpand = { expandedHistoryId = if (expandedHistoryId == rec.id) null else rec.id },
                            onTogglePhotoSelect = { on ->
                                selectedPhotoRecIds = if (on) selectedPhotoRecIds + rec.id else selectedPhotoRecIds - rec.id
                            },
                            onOpenLightbox = { photos, idx -> lightboxState = Triple(photos, idx, rec) },
                            onOpenDetails = { selectedRecord = rec },
                            onEdit = { startEditing(rec) },
                            onDelete = { pendingDelete = rec }
                        )
                    }
                } else {
                    item {
                        SarimYearMonthDayTree(
                            items = filteredHistory,
                            dateOf = { it.date },
                            summaryLabel = { list -> InputGuards.formatKg(list.sumOf { abs(it.net) }) },
                            itemRow = { rec ->
                                ProductionHistoryRowCard(
                                    rec = rec,
                                    stores = snapshot.stores,
                                    photoStore = snapshot.photos,
                                    selectedDate = date,
                                    showStoreChip = true,
                                    currency = currency,
                                    masked = masked,
                                    isPhotoSelected = rec.id in selectedPhotoRecIds,
                                    expanded = expandedHistoryId == rec.id,
                                    onToggleExpand = { expandedHistoryId = if (expandedHistoryId == rec.id) null else rec.id },
                                    onTogglePhotoSelect = { on ->
                                        selectedPhotoRecIds = if (on) selectedPhotoRecIds + rec.id else selectedPhotoRecIds - rec.id
                                    },
                                    onOpenLightbox = { photos, idx -> lightboxState = Triple(photos, idx, rec) },
                                    onOpenDetails = { selectedRecord = rec },
                                    onEdit = { startEditing(rec) },
                                    onDelete = { pendingDelete = rec }
                                )
                            }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(if (selectedPhotoRecIds.isNotEmpty()) 84.dp else 28.dp)) }
        }

        ProductionMultiShareBar(
            selectedCount = selectedPhotoRecIds.size,
            onClear = { selectedPhotoRecIds = emptySet() },
            onShare = {
                val chosen = filteredHistory.filter { it.id in selectedPhotoRecIds }
                val res = CaptionedPhotoSharer.shareProductionPhotos(context, chosen, snapshot.photos, currency, masked)
                if (res.isSuccess) {
                    selectedPhotoRecIds = emptySet()
                } else {
                    Toast.makeText(context, res.exceptionOrNull()?.localizedMessage ?: "Share failed", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    pendingDelete?.let { rec ->
        val label = FormulaEngine.storeLabelFor(rec.store, snapshot.stores).ifBlank { rec.store }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (rec.isTransfer) "Recycle transfer?" else "Delete production entry?") },
            text = {
                Text(
                    "$label · ${DateUtils.formatPretty(rec.date)} · ${InputGuards.formatKg(rec.net)}\n" +
                        if (rec.isTransfer) "Both sides of this transfer move to the Recycle Bin." else "It moves to the Recycle Bin and can be restored."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (rec.isTransfer) {
                        vm.recyclePair("production", rec.transferPairId, rec.id, "$label (${rec.date})")
                    } else {
                        vm.softDeleteRecord(
                            collection = "production",
                            docId = rec.id,
                            displayName = label,
                            displayDetail = rec.date,
                            displayAmount = InputGuards.formatKg(rec.net),
                            snapshotMap = WorkspaceJsonCodec.productionToMap(rec)
                        )
                    }
                    if (expandedHistoryId == rec.id) expandedHistoryId = null
                    pendingDelete = null
                }) { Text("Delete", color = accents.danger) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    selectedRecord?.let { rec ->
        ProductionDetailSheet(
            rec = rec,
            stores = snapshot.stores,
            photoStore = snapshot.photos,
            currency = currency,
            masked = masked,
            isAdmin = session.mode == "admin",
            vm = vm,
            onOpenLightbox = { photos, idx, r -> lightboxState = Triple(photos, idx, r) },
            onStartEdit = { startEditing(it) },
            onDismiss = { selectedRecord = null }
        )
    }

    lightboxState?.let { (photos, idx, rec) ->
        ProductionPhotoLightboxDialog(
            photos = photos,
            initialIndex = idx,
            record = rec,
            currencySymbol = currency,
            hideMoney = masked,
            onShareRecord = rec?.let { r ->
                {
                    val res = CaptionedPhotoSharer.shareProductionPhotos(context, listOf(r), snapshot.photos, currency, masked)
                    if (res.isFailure) {
                        Toast.makeText(context, res.exceptionOrNull()?.localizedMessage ?: "Share failed", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { lightboxState = null }
        )
    }

    if (showSaveConfirmDialog && selectedStore != null) {
        val st = selectedStore!!
        val totalCost = InputGuards.round2(net * dynamicCost.dynamicCostPerKg)
        val totalSale = InputGuards.round2(net * dynamicCost.salePrice)
        ProductionSaveConfirmDialog(
            storeName = st.name,
            dateIso = date,
            netKg = net,
            totalCost = totalCost,
            totalSale = totalSale,
            hideMoneyPanels = isMoneyRestrictedMode,
            masked = masked,
            currency = currency,
            grossKg = gross,
            containerKg = container,
            units = units,
            gated = session.mode != "admin",
            onConfirm = {
                showSaveConfirmDialog = false
                vm.submitProduction(
                    date = date,
                    store = st,
                    gross = gross,
                    container = container,
                    units = units,
                    costPerKg = 0.0,
                    notes = "",
                    photoDataUris = photoSlots.map { it.dataUrl }
                )
                resetEntryForm()
            },
            onDismiss = { showSaveConfirmDialog = false }
        )
    }

    if (showEditDiffConfirm && editingRecord != null && selectedStore != null) {
        val orig = editingRecord!!
        val st = selectedStore!!
        EditDiffConfirmDialog(
            title = "Confirm Production Edit",
            rows = listOf(
                Triple("Date", orig.date, date),
                Triple("Store", FormulaEngine.storeLabelFor(orig.store, snapshot.stores), st.name),
                Triple("Gross Wt", InputGuards.formatKg(orig.grossWt), InputGuards.formatKg(gross)),
                Triple("Container", InputGuards.formatKg(orig.contWt), InputGuards.formatKg(container)),
                Triple("Net Wt", InputGuards.formatKg(orig.net), InputGuards.formatKg(net)),
                Triple("Units", InputGuards.fmtNum(orig.formulaUnits), InputGuards.fmtNum(units)),
                Triple("Photos", "${orig.photoKeys.size}", "${photoSlots.size}")
            ),
            onConfirm = {
                showEditDiffConfirm = false
                val retainedKeys = photoSlots.mapNotNull { it.existingKey }
                val newUris = photoSlots.filter { it.existingKey == null }.map { it.dataUrl }
                vm.editProduction(orig, date, st, gross, container, units, 0.0, orig.returnNote, retainedKeys, newUris) {
                    resetEntryForm()
                }
            },
            onDismiss = { showEditDiffConfirm = false }
        )
    }

    if (showDiscardEditDialog) {
        DiscardEditDialog(
            onDiscard = {
                showDiscardEditDialog = false
                resetEntryForm()
            },
            onDismiss = { showDiscardEditDialog = false }
        )
    }
}
