package com.gullzubair.sarim.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
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
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
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
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

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
    val masked = session.restrictMoney || snapshot.settings.hideSensitiveMoney
    val activeStores = remember(snapshot.stores) { snapshot.stores.filter { it.deletedAt == null && it.active } }

    var subTab by remember { mutableIntStateOf(0) }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var selectedStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var grossStr by remember { mutableStateOf("") }
    var containerStr by remember { mutableStateOf("0") }
    var unitsStr by remember { mutableStateOf("1") }
    var customCostStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var photoDataUri by remember { mutableStateOf<String?>(null) }

    // Return stock form
    var retDate by remember(todayIso) { mutableStateOf(todayIso) }
    var retStore by remember(activeStores) { mutableStateOf(activeStores.firstOrNull()) }
    var retQtyStr by remember { mutableStateOf("") }
    var retExpStr by remember { mutableStateOf("0") }
    var retBy by remember { mutableStateOf("") }
    var retNotes by remember { mutableStateOf("") }

    var selectedRecord by remember { mutableStateOf<ProductionRecord?>(null) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bmp = BitmapFactory.decodeStream(stream) ?: return@use null
                            val maxDim = 900
                            val scale = minOf(1f, maxDim.toFloat() / maxOf(bmp.width, bmp.height).coerceAtLeast(1))
                            val resized = if (scale < 1f) {
                                Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                            } else bmp
                            val out = ByteArrayOutputStream()
                            resized.compress(Bitmap.CompressFormat.JPEG, 76, out)
                            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                        }
                    }.getOrNull()
                }
                if (!encoded.isNullOrBlank()) photoDataUri = encoded
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
    LaunchedEffect(resolvedCost) {
        if (customCostStr.isBlank() && resolvedCost.first > 0.0) {
            customCostStr = resolvedCost.first.toString()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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

        when (subTab) {
            0 -> {
                val gross = grossStr.toDoubleOrNull() ?: 0.0
                val container = containerStr.toDoubleOrNull() ?: 0.0
                val net = InputGuards.round2((gross - container).coerceAtLeast(0.0))
                val units = unitsStr.toDoubleOrNull() ?: 0.0
                val overrideCp = customCostStr.toDoubleOrNull() ?: 0.0
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
                    item {
                        SarimCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Log Daily Production", style = MaterialTheme.typography.titleMedium)
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
                            Spacer(Modifier.height(10.dp))
                            DatePickerField("Production Date", date, { date = it }, "prod_date")
                            Spacer(Modifier.height(10.dp))
                            PickerField(
                                label = "Store",
                                selectedLabel = selectedStore?.name ?: "Select Store",
                                items = activeStores,
                                itemLabel = { "${it.name} (${InputGuards.formatMoney(it.salePrice, currency, masked)}/kg)" },
                                onSelect = {
                                    selectedStore = it
                                    customCostStr = ""
                                },
                                testTag = "prod_store_picker"
                            )
                            if (resolvedFormula != null && formulaDesc != null) {
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
                                DecimalField("Cost / kg (${resolvedCost.second})", customCostStr, { customCostStr = it }, "prod_cost_input", Modifier.weight(1f))
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
                            Surface(
                                onClick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                shape = RoundedCornerShape(14.dp),
                                color = accents.softPillFill,
                                border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.45f)),
                                modifier = Modifier.testTag("prod_attach_photo_btn")
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (photoDataUri != null) Icons.Default.Check else Icons.Default.CameraAlt, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (photoDataUri != null) "Scale Photo Attached (Tap to change)" else "Attach Scale Photo (Optional)", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SarimHalfRingKpiBox("Net Produced", InputGuards.formatKg(net), accents.emerald, Modifier.weight(1f))
                                SarimHalfRingKpiBox("Est. Profit", InputGuards.formatMoney(profit, currency, masked), accents.cyan, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(14.dp))
                            SarimGradientButton(
                                text = "Save Production Entry",
                                onClick = {
                                    selectedStore?.let { store ->
                                        vm.submitProduction(date, store, gross, container, units, overrideCp, notes, photoDataUri)
                                        grossStr = ""
                                        notes = ""
                                        photoDataUri = null
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
                            PickerField(
                                label = "Store",
                                selectedLabel = retStore?.name ?: "Select Store",
                                items = activeStores,
                                itemLabel = { it.name },
                                onSelect = { retStore = it },
                                testTag = "ret_store_picker"
                            )
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
                                        retQtyStr = ""
                                        retExpStr = "0"
                                        retBy = ""
                                        retNotes = ""
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
                val periodKey = when (period) {
                    "Day" -> "today"
                    "Week" -> "week"
                    "Month" -> "month"
                    "Year" -> "fy"
                    else -> "all"
                }
                val filteredProd = remember(snapshot.production, periodKey, snapshot.settings.financialYearStartMonth) {
                    snapshot.production.filter {
                        it.deletedAt == null && !it.isTransfer && !it.isReturn &&
                            DateUtils.isInPeriod(it.date, periodKey, snapshot.settings.financialYearStartMonth)
                    }
                }
                val stockByKey = remember(snapshot) {
                    FinanceEngine.computeStoreStockSnapshots(snapshot).associateBy { it.storeKey.lowercase() }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                        val itemsForStore = filteredProd.filter {
                            it.store.equals(store.key, ignoreCase = true) || it.store.equals(store.name, ignoreCase = true)
                        }
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
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedRecord = rec }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                when {
                                                    rec.isTransfer -> "TRANSFER ${rec.transferDirection.uppercase()} • $storeLabel"
                                                    rec.isReturn -> "RETURN • $storeLabel"
                                                    else -> storeLabel
                                                },
                                                style = MaterialTheme.typography.titleSmall
                                            )
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
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    selectedRecord?.let { rec ->
        val storeLabel = FormulaEngine.storeLabelFor(rec.store, snapshot.stores).ifBlank { rec.store }
        SarimDetailBottomSheet(
            title = storeLabel,
            rightTitle = InputGuards.formatKg(rec.net),
            subtitle = DateUtils.formatPretty(rec.date).uppercase(),
            onDismiss = { selectedRecord = null },
            content = {
                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Formula Units", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(InputGuards.fmtNum(rec.formulaUnits), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cost / Sale", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text("${InputGuards.formatMoney(rec.totalCost, currency, masked)} / ${InputGuards.formatMoney(rec.totalSale, currency, masked)}", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Profit", style = MaterialTheme.typography.bodyMedium, color = accents.textMuted)
                    Text(InputGuards.formatMoney(rec.profit, currency, masked), style = MaterialTheme.typography.titleSmall, color = accents.emerald)
                }
                val note = rec.returnNote.ifBlank { rec.transferNote }
                if (note.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(note, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                }
            },
            footer = {
                SarimDangerButton(
                    text = if (rec.isTransfer) "Recycle Transfer Pair" else "Move to Recycle Bin",
                    onClick = {
                        if (rec.isTransfer) {
                            vm.recyclePair("production", rec.transferPairId, rec.id, "$storeLabel (${rec.date})")
                        } else {
                            vm.softDeleteRecord(
                                collection = "production",
                                docId = rec.id,
                                displayName = storeLabel,
                                displayDetail = rec.date,
                                displayAmount = InputGuards.formatKg(rec.net),
                                snapshotMap = WorkspaceJsonCodec.productionToMap(rec)
                            )
                        }
                        selectedRecord = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "prod_delete_${rec.id}"
                )
            }
        )
    }
}
