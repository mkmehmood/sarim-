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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.LaunchedEffect
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
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.DiscardEditDialog
import com.gullzubair.sarim.ui.components.EditDiffConfirmDialog
import com.gullzubair.sarim.ui.components.EditedBadge
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.ProdPhotoSlot
import com.gullzubair.sarim.ui.components.ProductionDetailSheet
import com.gullzubair.sarim.ui.components.ProductionMultiShareBar
import com.gullzubair.sarim.ui.components.ProductionPhotoLightboxDialog
import com.gullzubair.sarim.ui.components.ProductionPhotoPickerStrip
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimYearMonthDayTree
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.CaptionedPhotoSharer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    var subTab by remember(isEntryOnlyProd) { mutableIntStateOf(0) }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var selectedStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var grossStr by remember { mutableStateOf("") }
    var containerStr by remember { mutableStateOf("0") }
    var unitsStr by remember { mutableStateOf("1") }
    var customCostStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var photoSlots by remember { mutableStateOf<List<ProdPhotoSlot>>(emptyList()) }

    // Edit state
    var editingRecord by remember { mutableStateOf<ProductionRecord?>(null) }
    var showEditDiffConfirm by remember { mutableStateOf(false) }
    var showDiscardEditDialog by remember { mutableStateOf(false) }

    // Return stock form
    var retDate by remember(todayIso) { mutableStateOf(todayIso) }
    var retStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var retQtyStr by remember { mutableStateOf("") }
    var retExpStr by remember { mutableStateOf("0") }
    var retBy by remember { mutableStateOf("") }
    var retNotes by remember { mutableStateOf("") }

    // Detail, Lightbox & Multi-photo Share
    var selectedRecord by remember { mutableStateOf<ProductionRecord?>(null) }
    var lightboxState by remember { mutableStateOf<Triple<List<String>, Int, ProductionRecord?>?>(null) }
    var selectedPhotoRecIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun resetEntryForm() {
        editingRecord = null
        date = todayIso
        grossStr = ""
        containerStr = "0"
        unitsStr = "1"
        customCostStr = ""
        notes = ""
        photoSlots = emptyList()
    }

    fun startEditing(rec: ProductionRecord) {
        editingRecord = rec
        subTab = 0
        date = rec.date.ifBlank { todayIso }
        selectedStore = activeStores.firstOrNull {
            it.key.equals(rec.store, true) || it.name.equals(rec.store, true)
        } ?: activeStores.firstOrNull()
        grossStr = if (rec.grossWt > 0.0) InputGuards.fmtNum(rec.grossWt).replace(",", "") else ""
        containerStr = InputGuards.fmtNum(rec.contWt).replace(",", "")
        unitsStr = InputGuards.fmtNum(rec.formulaUnits).replace(",", "")
        customCostStr = if (rec.cp > 0.0) rec.cp.toString() else ""
        notes = rec.returnNote
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

    val costIdx = remember(snapshot.production) { FormulaEngine.buildCostIndex(snapshot.production) }
    val resolvedFormula = remember(selectedStore, snapshot.formulas) {
        selectedStore?.formulaId?.let { fid -> snapshot.formulas.firstOrNull { it.id == fid } }
    }
    val formulaDesc = remember(resolvedFormula, snapshot.formulas, snapshot.inventory) {
        resolvedFormula?.let { FormulaEngine.describeFormula(it, snapshot.formulas, snapshot.inventory) }
    }
    val resolvedCost = remember(date, selectedStore, costIdx, snapshot.settings.storeCosts) {
        selectedStore?.let { st ->
            val lookup = FormulaEngine.costSourceFromIndex(costIdx, st.key.ifBlank { st.name }, date)
            if (lookup.cp > 0.0) lookup.cp to lookup.source
            else (snapshot.settings.storeCosts[st.key] ?: 0.0) to "default"
        } ?: (0.0 to "none")
    }
    LaunchedEffect(resolvedCost, editingRecord) {
        if (editingRecord == null && customCostStr.isBlank() && resolvedCost.first > 0.0) {
            customCostStr = resolvedCost.first.toString()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isEntryOnlyProd) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Log Entry", "Return Stock", "Stores", "History").forEachIndexed { i, title ->
                    val selected = subTab == i
                    Surface(
                        onClick = { subTab = i },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) accents.softPillFill else accents.inputFill,
                        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                        modifier = Modifier.testTag("prod_subtab_$i")
                    ) {
                        Box(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                            Text(title, style = MaterialTheme.typography.labelLarge, color = if (selected) accents.emerald else accents.textMuted, maxLines = 1)
                        }
                    }
                }
            }
        }

        val activeSubTab = if (isEntryOnlyProd) 0 else subTab
        when (activeSubTab) {
            0 -> {
                val gross = grossStr.toDoubleOrNull() ?: 0.0
                val container = containerStr.toDoubleOrNull() ?: 0.0
                val net = InputGuards.round2((gross - container).coerceAtLeast(0.0))
                val units = unitsStr.toDoubleOrNull() ?: 0.0
                val overrideCp = if (isEntryOnlyProd) 0.0 else (customCostStr.toDoubleOrNull() ?: 0.0)
                val formulaTotalCost = if (formulaDesc != null && units > 0.0) formulaDesc.perUnit * units else 0.0
                val effectiveTotalCost = when {
                    overrideCp > 0.0 -> net * overrideCp
                    formulaTotalCost > 0.0 -> formulaTotalCost
                    else -> net * resolvedCost.first
                }
                val salePrice = selectedStore?.let {
                    if (it.salePrice > 0.0) it.salePrice else (snapshot.settings.defaultStorePrices[it.key] ?: 0.0)
                } ?: 0.0
                val revenue = InputGuards.round2(net * salePrice)
                val profit = InputGuards.round2(revenue - effectiveTotalCost)

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isEntryOnlyProd) item { Spacer(Modifier.height(6.dp)) }
                    item {
                        SarimCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (editingRecord != null) "Edit Production Entry" else "Log Daily Production",
                                    style = MaterialTheme.typography.titleMedium
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
                            DatePickerField("Production Date", date, { date = it }, "prod_date")
                            Spacer(Modifier.height(10.dp))
                            PickerField(
                                label = "Store",
                                selectedLabel = selectedStore?.name ?: "Select Store",
                                items = activeStores,
                                itemLabel = { st ->
                                    if (isEntryOnlyProd) st.name
                                    else "${st.name} (${InputGuards.formatMoney(st.salePrice, currency, masked)}/kg)"
                                },
                                onSelect = {
                                    selectedStore = it
                                    if (editingRecord == null) customCostStr = ""
                                },
                                testTag = "prod_store_picker"
                            )
                            if (!isEntryOnlyProd && resolvedFormula != null && formulaDesc != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Linked formula: ${resolvedFormula.name} • ${InputGuards.formatMoney(formulaDesc.perUnit, currency, masked)}/unit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.emerald
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DecimalField("Gross Weight", grossStr, { grossStr = it }, "prod_gross_input", Modifier.weight(1f), "kg")
                                DecimalField("Container", containerStr, { containerStr = it }, "prod_container_input", Modifier.weight(1f), "kg")
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DecimalField("Formula Units", unitsStr, { unitsStr = it }, "prod_units_input", Modifier.weight(1f))
                                if (!isEntryOnlyProd) {
                                    DecimalField("Cost / kg (${resolvedCost.second})", customCostStr, { customCostStr = it }, "prod_cost_input", Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            SarimPillInput(
                                value = notes,
                                onValueChange = { notes = it.take(500) },
                                label = "NOTES (OPTIONAL)",
                                placeholder = "Optional production note",
                                testTag = "prod_notes_input"
                            )
                            Spacer(Modifier.height(10.dp))
                            ProductionPhotoPickerStrip(
                                slots = photoSlots,
                                onAddClick = { multiPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onRemoveAt = { idx -> photoSlots = photoSlots.filterIndexed { i, _ -> i != idx } },
                                onPreviewAt = { idx -> lightboxState = Triple(photoSlots.map { it.dataUrl }, idx, editingRecord) }
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("Net Produced", InputGuards.formatKg(net), accents.emerald, Modifier.weight(1f))
                                if (!isEntryOnlyProd) {
                                    SarimHalfRingKpiBox("Est. Profit", InputGuards.formatMoney(profit, currency, masked), accents.cyan, Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            SarimGradientButton(
                                text = if (editingRecord != null) "Update Production Entry" else "Save Production Entry",
                                onClick = {
                                    if (editingRecord != null) {
                                        showEditDiffConfirm = true
                                    } else {
                                        selectedStore?.let { store ->
                                            vm.submitProduction(
                                                date = date,
                                                store = store,
                                                gross = gross,
                                                container = container,
                                                units = units,
                                                costPerKg = overrideCp,
                                                notes = notes,
                                                photoDataUris = photoSlots.map { it.dataUrl }
                                            )
                                            resetEntryForm()
                                        }
                                    }
                                },
                                enabled = selectedStore != null && net > 0.0,
                                testTag = "prod_save_btn"
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            1 -> {
                val retQty = retQtyStr.toDoubleOrNull() ?: 0.0
                val expQty = retExpStr.toDoubleOrNull() ?: 0.0
                val returnsList = remember(snapshot.returns) {
                    snapshot.returns.filter { it.deletedAt == null }.sortedByDescending { it.date }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        SarimCard {
                            Text("Log Stock Return & Expired CHORA", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            DatePickerField("Return Date", retDate, { retDate = it }, "ret_date")
                            Spacer(Modifier.height(10.dp))
                            PickerField("Store", retStore?.name ?: "Select Store", activeStores, { it.name }, { retStore = it }, "ret_store_picker")
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                DecimalField("Good Return", retQtyStr, { retQtyStr = it }, "ret_qty_input", Modifier.weight(1f), "kg")
                                DecimalField("Expired (CHORA)", retExpStr, { retExpStr = it }, "ret_exp_input", Modifier.weight(1f), "kg")
                            }
                            Spacer(Modifier.height(10.dp))
                            SarimPillInput(retBy, { retBy = it.take(80) }, label = "RETURNED BY", placeholder = "Rep or party name", testTag = "ret_by_input")
                            Spacer(Modifier.height(10.dp))
                            SarimPillInput(retNotes, { retNotes = it.take(300) }, label = "NOTES", testTag = "ret_notes_input")
                            Spacer(Modifier.height(14.dp))
                            SarimGradientButton(
                                text = "Save Return Log",
                                onClick = {
                                    retStore?.let { st ->
                                        vm.submitStockReturn(retDate, st, retQty, expQty, retBy, retNotes)
                                        retQtyStr = ""; retExpStr = "0"; retBy = ""; retNotes = ""
                                    }
                                },
                                enabled = retStore != null && (retQty > 0.0 || expQty > 0.0),
                                testTag = "ret_save_btn"
                            )
                        }
                    }
                    items(returnsList, key = { it.id }) { ret ->
                        SarimCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${FormulaEngine.storeLabelFor(ret.store, snapshot.stores)} • ${DateUtils.formatPretty(ret.date)}", style = MaterialTheme.typography.titleSmall)
                                    Text("Good: ${InputGuards.formatKg(ret.quantity)} • Expired: ${InputGuards.formatKg(ret.expiredQty)}${if (ret.seller.isNotBlank()) " • ${ret.seller}" else ""}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                }
                                if (ret.recovered) {
                                    Text("Recovered", style = MaterialTheme.typography.labelMedium, color = accents.emerald)
                                } else {
                                    Surface(
                                        onClick = { vm.recoverReturnChora(ret) },
                                        shape = RoundedCornerShape(14.dp),
                                        color = accents.softPillFill,
                                        border = BorderStroke(1.dp, accents.emerald),
                                        modifier = Modifier.testTag("ret_recover_${ret.id}")
                                    ) {
                                        Text("Recover CHORA", style = MaterialTheme.typography.labelMedium, color = accents.emerald, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            2 -> {
                var period by remember { mutableStateOf("All") }
                val periodKey = when (period) { "Day" -> "today"; "Week" -> "week"; "Month" -> "month"; "Year" -> "fy"; else -> "all" }
                val filteredProd = remember(snapshot.production, periodKey, snapshot.settings.financialYearStartMonth) {
                    snapshot.production.filter {
                        it.deletedAt == null && !it.isTransfer && !it.isReturn &&
                            DateUtils.isInPeriod(it.date, periodKey, snapshot.settings.financialYearStartMonth)
                    }
                }
                val stockByKey = remember(snapshot) {
                    FinanceEngine.computeStoreStockSnapshots(snapshot).associateBy { it.storeKey.lowercase() }
                }
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Day", "Week", "Month", "Year", "All").forEach { p ->
                                val selected = period == p
                                Surface(
                                    onClick = { period = p },
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (selected) accents.softPillFill else accents.inputFill,
                                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                                    modifier = Modifier.testTag("prod_period_${p.lowercase()}")
                                ) {
                                    Text(p, style = MaterialTheme.typography.labelMedium, color = if (selected) accents.emerald else accents.textMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                                }
                            }
                        }
                    }
                    items(activeStores, key = { it.key }) { store ->
                        val itemsForStore = filteredProd.filter { it.store.equals(store.key, true) || it.store.equals(store.name, true) }
                        val kg = itemsForStore.sumOf { it.net }
                        val prof = itemsForStore.sumOf { it.profit }
                        val avail = stockByKey[store.key.lowercase()]?.availableKg ?: 0.0
                        SarimCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(store.name, style = MaterialTheme.typography.titleMedium)
                                Text(InputGuards.formatKg(kg), style = MaterialTheme.typography.titleMedium, color = accents.emerald)
                            }
                            Text(
                                "${itemsForStore.size} batches • Profit ${InputGuards.formatMoney(prof, currency, masked)} • Live Avail ${InputGuards.formatKg(avail)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = accents.textMuted
                            )
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }

            else -> {
                val activeList = remember(snapshot.production) {
                    snapshot.production.filter { it.deletedAt == null }.sortedByDescending { it.date }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (activeList.isEmpty()) {
                            item { EmptyStateCard("No production logs", "Log your first batch on the Entry tab.") }
                        } else {
                            item {
                                SarimYearMonthDayTree(
                                    items = activeList,
                                    dateOf = { it.date },
                                    summaryLabel = { list -> InputGuards.formatKg(list.sumOf { kotlin.math.abs(it.net) }) },
                                    itemRow = { rec ->
                                        val storeLabel = FormulaEngine.storeLabelFor(rec.store, snapshot.stores).ifBlank { rec.store }
                                        val recPhotos = rec.photoKeys.mapNotNull { k ->
                                            snapshot.photos[k]?.takeIf { !it.deleted && it.dataUri.isNotBlank() }?.dataUri
                                        }
                                        val hasPhotos = recPhotos.isNotEmpty() && !rec.isReturn && !rec.isTransfer
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable { selectedRecord = rec }
                                                .padding(vertical = 8.dp, horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (hasPhotos) {
                                                Checkbox(
                                                    checked = rec.id in selectedPhotoRecIds,
                                                    onCheckedChange = { on ->
                                                        selectedPhotoRecIds = if (on) selectedPhotoRecIds + rec.id else selectedPhotoRecIds - rec.id
                                                    },
                                                    modifier = Modifier.testTag("prod_photo_check_${rec.id}")
                                                )
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        when {
                                                            rec.isTransfer -> "TRANSFER ${rec.transferDirection.uppercase()} • $storeLabel"
                                                            rec.isReturn -> "RETURN • $storeLabel"
                                                            else -> storeLabel
                                                        },
                                                        style = MaterialTheme.typography.titleSmall
                                                    )
                                                    EditedBadge(rec.isEdited)
                                                }
                                                val sub = buildString {
                                                    if (rec.formulaUnits > 0.0) append("Units ${InputGuards.fmtNum(rec.formulaUnits)}")
                                                    if (rec.formulaName.isNotBlank()) {
                                                        if (isNotEmpty()) append(" • ")
                                                        append(rec.formulaName)
                                                    }
                                                    if (rec.isReturn && rec.returnedBy.isNotBlank()) {
                                                        if (isNotEmpty()) append(" • ")
                                                        append(rec.returnedBy)
                                                    }
                                                }
                                                if (sub.isNotBlank()) {
                                                    Text(sub, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                                }
                                            }
                                            if (hasPhotos) {
                                                Surface(
                                                    onClick = { lightboxState = Triple(recPhotos, 0, rec) },
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
                                            Text(
                                                InputGuards.formatKg(rec.net),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (rec.net >= 0) accents.emerald else accents.gold
                                            )
                                        }
                                    }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(72.dp)) }
                    }

                    ProductionMultiShareBar(
                        selectedCount = selectedPhotoRecIds.size,
                        onClear = { selectedPhotoRecIds = emptySet() },
                        onShare = {
                            val chosen = activeList.filter { it.id in selectedPhotoRecIds }
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
            }
        }
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

    if (showEditDiffConfirm && editingRecord != null && selectedStore != null) {
        val orig = editingRecord!!
        val st = selectedStore!!
        val gross = grossStr.toDoubleOrNull() ?: 0.0
        val container = containerStr.toDoubleOrNull() ?: 0.0
        val net = InputGuards.round2((gross - container).coerceAtLeast(0.0))
        val units = unitsStr.toDoubleOrNull() ?: 0.0
        val overrideCp = if (isEntryOnlyProd) 0.0 else (customCostStr.toDoubleOrNull() ?: 0.0)
        EditDiffConfirmDialog(
            title = "Confirm Production Edit",
            rows = listOf(
                Triple("Date", orig.date, date),
                Triple("Store", FormulaEngine.storeLabelFor(orig.store, snapshot.stores), st.name),
                Triple("Gross Wt", InputGuards.formatKg(orig.grossWt), InputGuards.formatKg(gross)),
                Triple("Container", InputGuards.formatKg(orig.contWt), InputGuards.formatKg(container)),
                Triple("Net Wt", InputGuards.formatKg(orig.net), InputGuards.formatKg(net)),
                Triple("Units", InputGuards.fmtNum(orig.formulaUnits), InputGuards.fmtNum(units)),
                Triple("Photos", "${orig.photoKeys.size}", "${photoSlots.size}"),
                Triple("Notes", orig.returnNote, notes.trim())
            ),
            onConfirm = {
                showEditDiffConfirm = false
                val retainedKeys = photoSlots.mapNotNull { it.existingKey }
                val newUris = photoSlots.filter { it.existingKey == null }.map { it.dataUrl }
                vm.editProduction(orig, date, st, gross, container, units, overrideCp, notes, retainedKeys, newUris) {
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
