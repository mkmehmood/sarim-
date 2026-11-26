package com.gullzubair.sarim.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.ProductionEngine
import com.gullzubair.sarim.domain.ProductionEngine.Metric
import com.gullzubair.sarim.domain.ProductionEngine.Period
import com.gullzubair.sarim.ui.components.CombinedOverviewCard
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.DiscardEditDialog
import com.gullzubair.sarim.ui.components.EditDiffConfirmDialog
import com.gullzubair.sarim.ui.components.EditedBadge
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.ProdPhotoSlot
import com.gullzubair.sarim.ui.components.ProductionBarChart
import com.gullzubair.sarim.ui.components.ProductionDetailSheet
import com.gullzubair.sarim.ui.components.ProductionMultiShareBar
import com.gullzubair.sarim.ui.components.ProductionPhotoLightboxDialog
import com.gullzubair.sarim.ui.components.ProductionPhotoPickerStrip
import com.gullzubair.sarim.ui.components.ProductionPieChart
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimYearMonthDayTree
import com.gullzubair.sarim.ui.components.StoreOverviewCard
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.CaptionedPhotoSharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val VIEW_COMBINED = 0
private const val VIEW_STORE = 1

/** Manufacturing tab — mirrors the web app: Combined / Store Entry views, entry form, overview, charts, history. */
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
    val masked = isEntryOnlyProd || session.restrictMoney || snapshot.settings.hideSensitiveMoney
    val activeStores = remember(snapshot.stores) { snapshot.stores.filter { it.deletedAt == null && it.active } }

    var view by remember(isEntryOnlyProd) { mutableIntStateOf(VIEW_STORE) }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var selectedStoreKey by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()?.key) }
    val selectedStore: StoreConfig? = activeStores.firstOrNull { it.key == selectedStoreKey } ?: activeStores.firstOrNull()
    var grossStr by remember { mutableStateOf("") }
    var containerStr by remember { mutableStateOf("") }
    var unitsStr by remember { mutableStateOf("1") }
    var photoSlots by remember { mutableStateOf<List<ProdPhotoSlot>>(emptyList()) }

    var ovPeriod by remember { mutableStateOf(Period.DAY) }
    var metric by remember { mutableStateOf(Metric.WEIGHT) }
    var mfgPeriod by remember { mutableStateOf(Period.WEEK) }

    var editingRecord by remember { mutableStateOf<ProductionRecord?>(null) }
    var showEditDiffConfirm by remember { mutableStateOf(false) }
    var showDiscardEditDialog by remember { mutableStateOf(false) }

    var selectedRecord by remember { mutableStateOf<ProductionRecord?>(null) }
    var lightboxState by remember { mutableStateOf<Triple<List<String>, Int, ProductionRecord?>?>(null) }
    var selectedPhotoRecIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun resetEntryForm() {
        editingRecord = null
        grossStr = ""
        containerStr = ""
        unitsStr = "1"
        photoSlots = emptyList()
    }

    fun startEditing(rec: ProductionRecord) {
        editingRecord = rec
        view = VIEW_STORE
        date = rec.date.ifBlank { todayIso }
        selectedStoreKey = activeStores.firstOrNull {
            it.key.equals(rec.store, true) || it.name.equals(rec.store, true)
        }?.key ?: activeStores.firstOrNull()?.key
        grossStr = if (rec.grossWt > 0.0) InputGuards.fmtNum(rec.grossWt).replace(",", "") else ""
        containerStr = if (rec.contWt > 0.0) InputGuards.fmtNum(rec.contWt).replace(",", "") else ""
        unitsStr = InputGuards.fmtNum(rec.formulaUnits).replace(",", "")
        photoSlots = rec.photoKeys.mapNotNull { key ->
            snapshot.photos[key]?.takeIf { !it.deleted && it.dataUri.isNotBlank() }?.let { blob ->
                ProdPhotoSlot(existingKey = key, dataUrl = blob.dataUri)
            }
        }
    }

    BackHandler(enabled = editingRecord != null) { showDiscardEditDialog = true }

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
                if (encoded.isNotEmpty()) photoSlots = (photoSlots + encoded).take(6)
            }
        }
    }

    val activeView = if (isEntryOnlyProd) VIEW_STORE else view
    val combined = activeView == VIEW_COMBINED

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Header: "Manufacturing" + Combined / Store Entry toggle (web prod-tab-header)
        item {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Manufacturing", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = accents.emerald)
                if (!isEntryOnlyProd) {
                    ToggleGroup(
                        options = listOf("Combined", "Store Entry"),
                        selectedIndex = view,
                        onSelect = { if (editingRecord == null) view = it },
                        tagPrefix = "prod_view"
                    )
                }
            }
        }

        if (!combined) {
            // ---- New Entry card
            item {
                val gross = grossStr.toDoubleOrNull() ?: 0.0
                val container = containerStr.toDoubleOrNull() ?: 0.0
                val net = InputGuards.round2((gross - container).coerceAtLeast(0.0))
                val units = unitsStr.toDoubleOrNull() ?: 0.0
                val dyn = ProductionEngine.dynamicCost(snapshot, selectedStore, units, net, editingRecord)
                val salePrice = ProductionEngine.salePriceFor(snapshot, selectedStore)
                val avail = ProductionEngine.unitsAvailable(snapshot, selectedStore)
                val credit = editingRecord?.takeIf { it.formulaId.isNotBlank() && it.formulaId == selectedStore?.formulaId }?.formulaUnits ?: 0.0
                val insufficient = units > avail.available + credit + 1e-9
                val costValue = net * dyn.costPerKg
                val profitPerKg = if (net > 0.0) salePrice - dyn.costPerKg else 0.0
                val perKg = { v: Double -> "${InputGuards.formatMoney(v, currency, masked)}/kg" }

                SarimCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (editingRecord != null) "Edit Production Entry" else "New Entry",
                            style = MaterialTheme.typography.titleMedium, color = accents.emerald
                        )
                        if (editingRecord != null) {
                            Surface(
                                onClick = { showDiscardEditDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                color = accents.danger.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.4f)),
                                modifier = Modifier.testTag("prod_cancel_edit_btn")
                            ) {
                                Text("Cancel Edit", style = MaterialTheme.typography.labelMedium, color = accents.danger, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        } else if (!isEntryOnlyProd) {
                            Surface(
                                onClick = onOpenStockTransfer,
                                shape = RoundedCornerShape(14.dp),
                                color = accents.softPillFill,
                                border = BorderStroke(1.dp, accents.emerald),
                                modifier = Modifier.testTag("prod_open_transfer_btn")
                            ) {
                                Text("Transfer Stock", style = MaterialTheme.typography.labelMedium, color = accents.emerald, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // Formula units available indicator
                    val availColor = when {
                        avail.available <= 0.0 -> accents.danger
                        avail.available < 10.0 -> accents.gold
                        else -> accents.emerald
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = availColor.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, availColor.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().testTag("prod_units_available")
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Formula Units Available:", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                            Text(
                                "${InputGuards.fmtNum(avail.available)} units available" + if (avail.label.isNotBlank()) " · ${avail.label}" else "",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = availColor
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    DatePickerField("Select Date", date, { date = it }, "prod_date")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DecimalField("Gross Weight (kg)", grossStr, { grossStr = it }, "prod_gross_input", Modifier.weight(1f), "kg")
                        DecimalField("Container Weight (kg)", containerStr, { containerStr = it }, "prod_container_input", Modifier.weight(1f), "kg")
                    }
                    Spacer(Modifier.height(10.dp))
                    SarimHalfRingKpiBox("Net Production (kg)", InputGuards.formatKg(net), accents.emerald, Modifier.fillMaxWidth())
                    if (!isEntryOnlyProd) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Dynamic Cost Value: ${InputGuards.formatMoney(costValue, currency, masked)}",
                            style = MaterialTheme.typography.bodySmall, color = accents.danger,
                            modifier = Modifier.testTag("prod_dynamic_cost_value")
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    // Store selector toggle group
                    Text("SELECT STORE:", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                    Spacer(Modifier.height(4.dp))
                    if (activeStores.isEmpty()) {
                        Text("No active stores. Add one in Factory Formulas.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    } else {
                        ToggleGroup(
                            options = activeStores.map { it.name },
                            selectedIndex = activeStores.indexOfFirst { it.key == selectedStore?.key }.coerceAtLeast(0),
                            onSelect = { selectedStoreKey = activeStores[it].key },
                            tagPrefix = "prod_store",
                            enabled = editingRecord == null
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            DecimalField("Formula Units Used", unitsStr, { unitsStr = it }, "prod_units_input", Modifier.fillMaxWidth())
                            Text("Units of factory mixture consumed", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                        }
                        if (!isEntryOnlyProd) {
                            Column(Modifier.weight(1f)) {
                                Text("SALE PRICE (PER KG)", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                Spacer(Modifier.height(10.dp))
                                Text(perKg(salePrice), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = accents.emerald)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ProductionPhotoPickerStrip(
                        slots = photoSlots,
                        onAddClick = { multiPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        onRemoveAt = { idx -> photoSlots = photoSlots.filterIndexed { i, _ -> i != idx } },
                        onPreviewAt = { idx -> lightboxState = Triple(photoSlots.map { it.dataUrl }, idx, editingRecord) }
                    )

                    if (!isEntryOnlyProd) {
                        Spacer(Modifier.height(12.dp))
                        // Formula cost block (web prod-formula-cost-block)
                        Surface(shape = RoundedCornerShape(16.dp), color = accents.inputFill.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                KeyValueRow("Formula Unit Cost:", "${InputGuards.formatMoney(dyn.costPerUnit, currency, masked)}/unit", accents.danger)
                                KeyValueRow("Total Formula Cost:", InputGuards.formatMoney(dyn.totalFormulaCost, currency, masked), accents.danger)
                                KeyValueRow("Cost per kg:", perKg(dyn.costPerKg), accents.danger)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // Profit block (web prod-profit-block)
                        SarimCard {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                KeyValueRow("Dynamic Cost Price:", perKg(dyn.costPerKg), accents.danger)
                                KeyValueRow("Sale Price:", perKg(salePrice), accents.emerald)
                                KeyValueRow("Profit (per kg):", InputGuards.formatMoney(profitPerKg, currency, masked), accents.emerald, bold = true)
                            }
                        }
                    }

                    if (insufficient) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = accents.gold.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, accents.gold.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().testTag("prod_insufficient_units")
                        ) {
                            Text(
                                "Insufficient formula units available! Please reduce formula units used.",
                                style = MaterialTheme.typography.bodySmall, color = accents.gold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    SarimGradientButton(
                        text = if (editingRecord != null) "Update Production Entry" else "Save Production",
                        onClick = {
                            val st = selectedStore ?: return@SarimGradientButton
                            if (editingRecord != null) {
                                showEditDiffConfirm = true
                            } else {
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
                            }
                        },
                        enabled = selectedStore != null && net > 0.0,
                        testTag = "prod_save_btn"
                    )
                }
            }
        } else {
            // ---- All Stores Overview
            item {
                val overview = remember(snapshot, activeStores, ovPeriod, date) {
                    ProductionEngine.overview(snapshot, activeStores, ovPeriod, date)
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("All Stores Overview", style = MaterialTheme.typography.titleMedium, color = accents.emerald)
                    ToggleGroup(
                        options = Period.values().map { it.label },
                        selectedIndex = ovPeriod.ordinal,
                        onSelect = { ovPeriod = Period.values()[it] },
                        tagPrefix = "prod_overview"
                    )
                    overview.stores.forEach { StoreOverviewCard(it, ovPeriod, currency, masked) }
                    CombinedOverviewCard(overview.total, ovPeriod, currency, masked)
                }
            }

            // ---- Store comparison chart
            item {
                val cmp = remember(snapshot, activeStores, metric, ovPeriod, date) {
                    ProductionEngine.storeComparison(snapshot, activeStores, metric, ovPeriod, date)
                }
                val storeColors = listOf(accents.cyan, accents.purple, accents.emerald, accents.gold, accents.danger)
                SarimCard {
                    Text("COMPARE STORES BY:", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                    Spacer(Modifier.height(6.dp))
                    ToggleGroup(
                        options = Metric.values().map { it.label },
                        selectedIndex = metric.ordinal,
                        onSelect = { metric = Metric.values()[it] },
                        tagPrefix = "prod_metric"
                    )
                    Spacer(Modifier.height(12.dp))
                    ProductionBarChart(
                        title = "Store Comparison by ${metric.label} (${ovPeriod.label})",
                        labels = cmp.map { it.first },
                        values = cmp.map { it.second },
                        barColors = storeColors,
                        valueLabel = { v ->
                            if (metric == Metric.WEIGHT) InputGuards.fmtNum(v, 1) else InputGuards.formatMoney(v, currency, masked)
                        }
                    )
                }
            }

            // ---- Analytics
            item {
                val an = remember(snapshot, mfgPeriod, date) { ProductionEngine.analytics(snapshot, mfgPeriod, date) }
                SarimCard {
                    Text("Analytics", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    ToggleGroup(
                        options = listOf(Period.WEEK, Period.MONTH, Period.YEAR, Period.ALL).map {
                            when (it) { Period.WEEK -> "Weekly"; Period.MONTH -> "Monthly"; Period.YEAR -> "Yearly"; else -> "All Times" }
                        },
                        selectedIndex = listOf(Period.WEEK, Period.MONTH, Period.YEAR, Period.ALL).indexOf(mfgPeriod).coerceAtLeast(0),
                        onSelect = { mfgPeriod = listOf(Period.WEEK, Period.MONTH, Period.YEAR, Period.ALL)[it] },
                        tagPrefix = "prod_mfg"
                    )
                    Spacer(Modifier.height(12.dp))
                    ProductionBarChart(
                        title = "Production Quantity (${if (mfgPeriod == Period.ALL) "All Times" else mfgPeriod.label})",
                        labels = an.labels,
                        values = an.qty,
                        barColors = listOf(accents.cyan),
                        valueLabel = { InputGuards.fmtNum(it, 1) }
                    )
                    Spacer(Modifier.height(16.dp))
                    ProductionPieChart(
                        title = "Financials: ${InputGuards.formatMoney(an.totalValue, currency, masked)} Total - ${if (mfgPeriod == Period.ALL) "All Times" else mfgPeriod.label}",
                        slices = listOf("Total Cost" to an.totalCost, "Net Profit" to an.totalProfit),
                        colors = listOf(accents.danger, accents.cyan),
                        valueLabel = { InputGuards.formatMoney(it, currency, masked) }
                    )
                }
            }
        }

        // ---- Production History (hidden for money-restricted entry-only roles, like web data-money)
        if (!isEntryOnlyProd) {
            item {
                Text("Production History", style = MaterialTheme.typography.titleMedium, color = accents.emerald)
            }
            val history = ProductionEngine.history(snapshot, combined, selectedStore, date)
            if (history.isEmpty()) {
                item { EmptyStateCard("No records found for this selection.", "Production entries will appear here.") }
            } else if (combined) {
                item {
                    SarimYearMonthDayTree(
                        items = history,
                        dateOf = { it.date },
                        summaryLabel = { list -> InputGuards.formatKg(list.filter { !it.isTransfer }.sumOf { it.net }) },
                        itemRow = { rec ->
                            HistoryCard(
                                rec, snapshot, date, combined, currency, masked,
                                selected = rec.id in selectedPhotoRecIds,
                                onToggleSelect = { on -> selectedPhotoRecIds = if (on) selectedPhotoRecIds + rec.id else selectedPhotoRecIds - rec.id },
                                onOpen = { selectedRecord = rec },
                                onPhotos = { photos -> lightboxState = Triple(photos, 0, rec) }
                            )
                        }
                    )
                }
            } else {
                history.forEach { rec ->
                    item(key = rec.id) {
                        HistoryCard(
                            rec, snapshot, date, combined, currency, masked,
                            selected = rec.id in selectedPhotoRecIds,
                            onToggleSelect = { on -> selectedPhotoRecIds = if (on) selectedPhotoRecIds + rec.id else selectedPhotoRecIds - rec.id },
                            onOpen = { selectedRecord = rec },
                            onPhotos = { photos -> lightboxState = Triple(photos, 0, rec) }
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(if (selectedPhotoRecIds.isNotEmpty()) 96.dp else 24.dp)) }
    }

    if (selectedPhotoRecIds.isNotEmpty() && !isEntryOnlyProd) {
        ProductionMultiShareBar(
            selectedCount = selectedPhotoRecIds.size,
            onClear = { selectedPhotoRecIds = emptySet() },
            onShare = {
                val chosen = snapshot.production.filter { it.id in selectedPhotoRecIds }
                val res = CaptionedPhotoSharer.shareProductionPhotos(context, chosen, snapshot.photos, currency, masked)
                if (res.isSuccess) selectedPhotoRecIds = emptySet()
                else Toast.makeText(context, res.exceptionOrNull()?.localizedMessage ?: "Share failed", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    } // Box

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

    if (showEditDiffConfirm && editingRecord != null && selectedStore != null) {
        val orig = editingRecord!!
        val st = selectedStore
        val gross = grossStr.toDoubleOrNull() ?: 0.0
        val container = containerStr.toDoubleOrNull() ?: 0.0
        val net = InputGuards.round2((gross - container).coerceAtLeast(0.0))
        val units = unitsStr.toDoubleOrNull() ?: 0.0
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
                // Web has no notes/cost override: keep whatever note the entry already had.
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

/** Pill toggle group (web `.toggle-group` / `.toggle-opt`). */
@Composable
private fun ToggleGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    tagPrefix: String,
    enabled: Boolean = true
) {
    val accents = LocalSarimAccents.current
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEachIndexed { i, title ->
            val selected = i == selectedIndex
            Surface(
                onClick = { if (enabled) onSelect(i) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) accents.softPillFill else accents.inputFill,
                border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                modifier = Modifier.testTag("${tagPrefix}_$i")
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) accents.emerald else accents.textMuted,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color, bold: Boolean = false) {
    val accents = LocalSarimAccents.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Bold), color = valueColor)
    }
}

/** Production history card (web hcCard): title, date/time, kg amount, profit tag and the metric grid. */
@Composable
private fun HistoryCard(
    rec: ProductionRecord,
    snapshot: WorkspaceSnapshot,
    selectedDate: String,
    combined: Boolean,
    currency: String,
    masked: Boolean,
    selected: Boolean,
    onToggleSelect: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onPhotos: (List<String>) -> Unit
) {
    val accents = LocalSarimAccents.current
    val storeLabel = FormulaEngine.storeLabelFor(rec.store, snapshot.stores).ifBlank { rec.store.ifBlank { "Unknown store" } }
    val peer = FormulaEngine.storeLabelFor(rec.transferPeerStore, snapshot.stores).ifBlank { rec.transferPeerStore }
    val recPhotos = rec.photoKeys.mapNotNull { k ->
        snapshot.photos[k]?.takeIf { !it.deleted && it.dataUri.isNotBlank() }?.dataUri
    }
    val hasPhotos = recPhotos.isNotEmpty() && !rec.isReturn && !rec.isTransfer
    val isSelectedDay = rec.date == selectedDate
    val money = { v: Double -> InputGuards.formatMoney(v, currency, masked) }

    val title = when {
        rec.isTransfer -> if (rec.transferDirection == "out") "Transfer out to $peer" else "Transfer in from $peer"
        rec.isReturn -> "${if (rec.isMerged) "Merged returns by" else "Returned by"} ${rec.returnedBy.ifBlank { "Representative" }}"
        else -> "$storeLabel production"
    }
    val tag = when {
        rec.isTransfer -> if (rec.transferDirection == "out") "Out" else "In"
        rec.isReturn -> "Return"
        else -> "Profit ${money(rec.profit)}"
    }
    val tagColor = when {
        rec.isTransfer -> if (rec.transferDirection == "out") accents.danger else accents.emerald
        rec.isReturn -> accents.emerald
        else -> if (rec.profit >= 0) accents.emerald else accents.danger
    }
    val sub = buildString {
        append(DateUtils.formatPretty(rec.date))
        if (rec.time.isNotBlank()) append(" · ${rec.time}")
        if (isSelectedDay) append(" (Selected)")
    }

    val metrics = buildList<Pair<String, String>> {
        if (!rec.isReturn && !rec.isTransfer && !masked) {
            if (rec.grossWt > 0.0) add("Gross weight" to InputGuards.formatKg(rec.grossWt))
            if (rec.contWt > 0.0) add("Container" to InputGuards.formatKg(rec.contWt))
            add("Cost price" to "${InputGuards.fmtNum(rec.cp)}/kg")
            add("Sale price" to "${InputGuards.fmtNum(rec.sp)}/kg")
            add("Total cost" to money(rec.totalCost))
            add("Total value" to money(rec.totalSale))
            add("Net profit" to money(rec.profit))
            if (rec.formulaUnits > 0.0) add("Formula units" to InputGuards.fmtNum(rec.formulaUnits))
            if (rec.formulaCost > 0.0) add("Formula cost" to money(rec.formulaCost))
        } else if (!rec.isReturn && !rec.isTransfer) {
            if (rec.grossWt > 0.0) add("Gross weight" to InputGuards.formatKg(rec.grossWt))
            if (rec.contWt > 0.0) add("Container" to InputGuards.formatKg(rec.contWt))
            if (rec.formulaUnits > 0.0) add("Formula units" to InputGuards.fmtNum(rec.formulaUnits))
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelectedDay) accents.softPillFill.copy(alpha = 0.55f) else accents.inputFill.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, if (isSelectedDay) accents.emerald.copy(alpha = 0.6f) else accents.borderSubtle),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onOpen() }.testTag("prod_hist_${rec.id}")
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (hasPhotos) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = onToggleSelect,
                        modifier = Modifier.testTag("prod_photo_check_${rec.id}")
                    )
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        EditedBadge(rec.isEdited)
                    }
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (combined) Text(storeLabel, style = MaterialTheme.typography.labelSmall, color = accents.cyan)
                        if (rec.managedBy.isNotBlank()) Text(rec.managedBy, style = MaterialTheme.typography.labelSmall, color = accents.gold)
                    }
                }
                if (hasPhotos) {
                    Surface(
                        onClick = { onPhotos(recPhotos) },
                        shape = RoundedCornerShape(10.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(end = 8.dp).testTag("prod_row_photo_${rec.id}")
                    ) {
                        Row(Modifier.padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "View photos", tint = accents.emerald, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("${recPhotos.size}", style = MaterialTheme.typography.labelSmall, color = accents.emerald)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        InputGuards.formatKg(if (rec.isTransfer) kotlin.math.abs(rec.net) else rec.net),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = tagColor
                    )
                    if (!(masked && !rec.isReturn && !rec.isTransfer)) {
                        Text(tag, style = MaterialTheme.typography.labelSmall, color = tagColor)
                    }
                }
            }
            if (rec.isTransfer && rec.transferNote.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Note: ${rec.transferNote}", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
            }
            if (metrics.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                metrics.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { (k, v) ->
                            Column(Modifier.weight(1f)) {
                                Text(k.uppercase(), style = MaterialTheme.typography.labelSmall, color = accents.textMuted, maxLines = 1)
                                Text(v, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
