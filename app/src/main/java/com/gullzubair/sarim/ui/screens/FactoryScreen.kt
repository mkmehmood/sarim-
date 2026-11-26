package com.gullzubair.sarim.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.FactoryRecord
import com.gullzubair.sarim.data.model.InventoryRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.ConfirmRequest
import com.gullzubair.sarim.ui.components.ConfirmRequestHost
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.DiscardEditDialog
import com.gullzubair.sarim.ui.components.EditDiffConfirmDialog
import com.gullzubair.sarim.ui.components.EditedBadge
import com.gullzubair.sarim.ui.components.FactoryBatchDetailSheet
import com.gullzubair.sarim.ui.components.MaterialPurchaseLotsSheet
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimYearMonthDayTree
import com.gullzubair.sarim.ui.components.TabHeading
import com.gullzubair.sarim.ui.components.cyanDotMatrixPattern
import com.gullzubair.sarim.ui.components.saveConfirmRequest
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.theme.ManropeFont
import com.gullzubair.sarim.ui.theme.PlayfairFont
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import java.text.SimpleDateFormat
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

@Composable
fun FactoryScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onOpenRawPurchase: () -> Unit = {},
    onOpenFormulas: () -> Unit = {}
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val isEntryOnlyFactory = remember(session) {
        session.mode == "factory" || (FinanceEngine.isMoneyRestricted(session) && "factory" in session.tabs)
    }
    val masked = isEntryOnlyFactory || session.restrictMoney || snapshot.settings.hideSensitiveMoney

    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var selectedFormula by remember(snapshot.formulas) { mutableStateOf(snapshot.formulas.firstOrNull()) }
    var unitsStr by remember { mutableStateOf("1") }
    var sortByValue by remember { mutableStateOf(false) }
    var summaryMode by remember { mutableStateOf("daily") }
    var availPoolKey by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    var editingBatch by remember { mutableStateOf<FactoryRecord?>(null) }
    var showEditDiff by remember { mutableStateOf(false) }
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }
    var showDiscardEdit by remember { mutableStateOf(false) }
    var selectedFactoryRec by remember { mutableStateOf<FactoryRecord?>(null) }
    var selectedMaterialLots by remember { mutableStateOf<InventoryRecord?>(null) }

    fun resetBatchForm() {
        editingBatch = null
        date = todayIso
        unitsStr = "1"
    }

    fun startEditBatch(rec: FactoryRecord) {
        editingBatch = rec
        date = rec.date.ifBlank { todayIso }
        selectedFormula = snapshot.formulas.firstOrNull { it.id == rec.formulaId } ?: selectedFormula
        unitsStr = InputGuards.fmtNum(rec.units).replace(",", "")
    }

    BackHandler(enabled = editingBatch != null) {
        showDiscardEdit = true
    }

    LaunchedEffect(editingBatch) {
        if (editingBatch != null) listState.animateScrollToItem(1)
    }

    val units = (unitsStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
    val desc = remember(selectedFormula, snapshot.formulas, snapshot.inventory) {
        selectedFormula?.let { FormulaEngine.describeFormula(it, snapshot.formulas, snapshot.inventory) }
    }
    val reqLines = desc?.items.orEmpty()
    val estCost = if (desc != null && units > 0.0) InputGuards.round2(desc.perUnit * units) else 0.0
    val materials = remember(snapshot.inventory) { FinanceEngine.materialRecords(snapshot.inventory) }
    val restoredById = remember(editingBatch) {
        val map = mutableMapOf<String, Double>()
        editingBatch?.let { b ->
            b.materialsUsed.ifEmpty { b.rawMaterialsUsed }.forEach { m ->
                val mid = m["id"]?.toString().orEmpty()
                val qty = (m["qty"] as? Number)?.toDouble() ?: (m["quantity"] as? Number)?.toDouble() ?: 0.0
                if (mid.isNotBlank()) map[mid] = (map[mid] ?: 0.0) + qty
            }
        }
        map
    }
    val allInStock = reqLines.isNotEmpty() && (desc?.problems.isNullOrEmpty()) && reqLines.all { line ->
        val mat = materials.firstOrNull { it.id == line.id }
            ?: materials.firstOrNull { it.name.trim().equals(line.name.trim(), ignoreCase = true) }
        val have = (mat?.quantity ?: 0.0) + (mat?.id?.let { restoredById[it] } ?: 0.0)
        have + 0.001 >= (line.quantity * units)
    }

    val activeFactory = remember(snapshot.factory) {
        snapshot.factory.filter { it.deletedAt == null }.sortedByDescending { it.date }
    }
    val activeProduction = remember(snapshot.production) {
        snapshot.production.filter { it.deletedAt == null && !it.isReturn }
    }
    val pools = remember(snapshot.stores, snapshot.formulas, snapshot.factory, snapshot.production) {
        FormulaEngine.buildUnitPools(snapshot.stores, snapshot.formulas, snapshot.factory, snapshot.production)
    }
    val selectedPool = pools.firstOrNull { it.key == availPoolKey } ?: pools.firstOrNull()

    val anchor = DateUtils.parseIso(date) ?: DateUtils.parseIso(todayIso)
    fun inSummaryPeriod(recDate: String): Boolean {
        if (summaryMode == "all") return true
        val d = DateUtils.parseIso(recDate) ?: return false
        val a = anchor ?: return true
        return when (summaryMode) {
            "daily" -> d == a
            "weekly" -> !d.isBefore(a.minusDays(6)) && !d.isAfter(a)
            "monthly" -> d.year == a.year && d.month == a.month
            "yearly" -> d.year == a.year
            else -> true
        }
    }
    val periodFactory = remember(activeFactory, summaryMode, date) { activeFactory.filter { inSummaryPeriod(it.date) } }
    val periodProduction = remember(activeProduction, summaryMode, date) { activeProduction.filter { inSummaryPeriod(it.date) } }

    val materialsSorted = remember(snapshot.inventory, sortByValue) {
        val base = FinanceEngine.materialRecords(snapshot.inventory)
        if (sortByValue) base.sortedByDescending { it.quantity * it.cost } else base.sortedBy { it.name.lowercase() }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TabHeading("Factory") }

        // 1. New Production Entry
        item {
            SarimCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        FactoryCardTitle(if (editingBatch != null) "Edit Factory Batch" else "New Production Entry", accent = true)
                        if (editingBatch != null) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                onClick = { showDiscardEdit = true },
                                shape = RoundedCornerShape(50),
                                color = accents.danger.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.4f)),
                                modifier = Modifier.testTag("factory_cancel_edit_btn")
                            ) {
                                Text("Cancel Edit", style = MaterialTheme.typography.labelMedium, color = accents.danger, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                    DatePickerField("Date", date, { date = it }, "factory_date", Modifier.width(164.dp))
                }
                Spacer(Modifier.height(12.dp))
                FactorySectionLabel("Formula")
                Spacer(Modifier.height(6.dp))
                if (snapshot.formulas.isEmpty()) {
                    Text("Select a store...", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                } else {
                    FactoryChoiceRow(
                        options = snapshot.formulas.map { it.name.uppercase() },
                        selectedIndex = snapshot.formulas.indexOfFirst { it.id == selectedFormula?.id },
                        onSelect = { selectedFormula = snapshot.formulas[it] },
                        tagPrefix = "factory_formula_chip_"
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f)) { DecimalField("Units to Produce", unitsStr, { unitsStr = it }, "factory_units_input") }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "TOTAL COST",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                            color = accents.textMuted,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                        )
                        val pinkBg = if (accents.isDark) accents.danger.copy(alpha = 0.14f) else Color(0xFFFFF1F2)
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = pinkBg,
                            border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)
                        ) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    InputGuards.formatMoney(estCost, currency, masked),
                                    fontFamily = ManropeFont,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.5.sp,
                                    color = accents.danger
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (!desc?.problems.isNullOrEmpty()) {
                    Text(
                        "Formula error: ${desc!!.problems.joinToString { "${it.type}:${it.name}" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = accents.danger
                    )
                    Spacer(Modifier.height(8.dp))
                }
                val mintBg = if (accents.isDark) accents.softPillFill else Color(0xFFECFDF7)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = mintBg,
                    border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        if (reqLines.isEmpty()) {
                            Text("Select a formula...", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        } else {
                            Text(
                                "${selectedFormula?.name?.uppercase() ?: "STANDARD"} FORMULA (${InputGuards.fmtNum(units)} UNITS)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            reqLines.forEach { line ->
                                val need = InputGuards.round4(line.quantity * units)
                                val mat = materials.firstOrNull { it.id == line.id }
                                    ?: materials.firstOrNull { it.name.trim().equals(line.name.trim(), ignoreCase = true) }
                                val have = (mat?.quantity ?: 0.0) + (mat?.id?.let { restoredById[it] } ?: 0.0)
                                val ok = have + 0.001 >= need
                                val lineCost = InputGuards.round2(need * (mat?.cost ?: line.cost))
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = if (ok) "${line.name.uppercase()} (${InputGuards.fmtNum(need)} kg)"
                                        else "${line.name.uppercase()} (${InputGuards.fmtNum(need)} kg · Have ${InputGuards.fmtNum(have)} kg)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (ok) MaterialTheme.colorScheme.onSurface else accents.danger,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = InputGuards.formatMoney(lineCost, currency, masked),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = if (ok) MaterialTheme.colorScheme.onSurface else accents.danger
                                    )
                                }
                            }
                            val addCostUnit = selectedFormula?.additionalCost ?: 0.0
                            val addCostTotal = InputGuards.round2(addCostUnit * units)
                            if (addCostUnit > 0.0) {
                                Spacer(Modifier.height(2.dp))
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        "Additional Cost (${InputGuards.fmtNum(addCostUnit)} per unit)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accents.danger
                                    )
                                    Text(
                                        InputGuards.formatMoney(addCostTotal, currency, masked),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = accents.danger
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                SarimGradientButton(
                    text = when {
                        !allInStock && reqLines.isNotEmpty() -> "Insufficient Raw Material"
                        editingBatch != null -> "Update Batch"
                        else -> "Save Factory Production"
                    },
                    onClick = {
                        if (editingBatch != null) {
                            showEditDiff = true
                        } else {
                            selectedFormula?.let { f ->
                                val details = buildList {
                                    add("Formula" to f.name)
                                    add("Date" to DateUtils.formatPretty(date))
                                    add("Units" to InputGuards.fmtNum(units))
                                }
                                confirmReq = saveConfirmRequest(
                                    label = "Production",
                                    details = details,
                                    gated = session.mode != "admin",
                                    tag = "factory_confirm_save_btn"
                                ) {
                                    vm.submitFactoryBatch(date, f, units, "")
                                }
                            }
                        }
                    },
                    enabled = selectedFormula != null && units > 0.0 && allInStock,
                    testTag = "factory_produce_btn"
                )
            }
        }

        if (!isEntryOnlyFactory) {
            // 2. Units Available
            item {
                SarimCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FactoryCardTitle("Units Available")
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(accents.emerald)
                            )
                        }
                        if (pools.isNotEmpty()) {
                            FactoryChoiceRow(
                                options = pools.map { it.label },
                                selectedIndex = pools.indexOfFirst { it.key == selectedPool?.key },
                                onSelect = { availPoolKey = pools[it].key },
                                tagPrefix = "factory_pool_chip_"
                            )
                        }
                    }
                    if (pools.isEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("0 kg", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    } else if (selectedPool != null) {
                        val prodForPool = activeProduction.filter { it.formulaId == selectedPool.formulaId }
                        val poolFormula = snapshot.formulas.firstOrNull { it.id == selectedPool.formulaId }
                        val weightPerUnit = poolFormula?.let {
                            FormulaEngine.describeFormula(it, snapshot.formulas, snapshot.inventory).weight
                        } ?: 0.0
                        val outputQuantity = prodForPool.sumOf { it.net }
                        val rawMaterialsUsed = prodForPool.sumOf { p ->
                            val matTotal = p.formulaMaterials.sumOf { fm ->
                                (fm["qty"] as? Number)?.toDouble()
                                    ?: ((fm["quantity"] as? Number)?.toDouble()?.let { it * p.formulaUnits })
                                    ?: 0.0
                            }
                            if (matTotal > 0.0) matTotal else weightPerUnit * p.formulaUnits
                        }
                        val costPerUnit = selectedPool.avgUnitCost
                        val totalCostValue = costPerUnit * selectedPool.availableUnits
                        val materialsValue = prodForPool.sumOf { if (it.formulaCost > 0.0) it.formulaCost else it.totalCost }
                        val totalProfit = prodForPool.sumOf { it.profit }
                        val profitPerKg = if (outputQuantity > 0.0) totalProfit / outputQuantity else 0.0

                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FactoryGroupBox(
                                    title = "UNITS",
                                    rows = listOf(
                                        Triple("Available", InputGuards.fmtNum(selectedPool.availableUnits), accents.gold),
                                        Triple("Used", InputGuards.fmtNum(selectedPool.usedUnits), accents.gold)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                FactoryGroupBox(
                                    title = "OUTPUT",
                                    rows = listOf(
                                        Triple("Quantity", "${InputGuards.fmtNum(outputQuantity)} kg", accents.gold),
                                        Triple("Raw Used", "${InputGuards.fmtNum(rawMaterialsUsed)} kg", accents.gold)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FactoryGroupBox(
                                    title = "COST",
                                    rows = listOf(
                                        Triple("Per Unit", InputGuards.formatMoney(costPerUnit, currency, masked), accents.danger),
                                        Triple("Total", InputGuards.formatMoney(totalCostValue, currency, masked), accents.emerald),
                                        Triple("Materials", InputGuards.formatMoney(materialsValue, currency, masked), accents.danger)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                FactoryGroupBox(
                                    title = "PROFIT",
                                    rows = listOf(
                                        Triple("Total", InputGuards.formatMoney(totalProfit, currency, masked), if (totalProfit >= 0.0) accents.emerald else accents.danger),
                                        Triple("Per kg", "${InputGuards.formatMoney(profitPerKg, currency, masked)}/kg", accents.emerald)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Raw Material Inventory
            item {
                SarimCard {
                    FactoryCardTitle("Raw Material Inventory")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sort by", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                        FactoryPill(if (sortByValue) "Material" else "Material ▲", selected = !sortByValue, onClick = { sortByValue = false })
                        FactoryPill("Total Value ⇅", selected = sortByValue, onClick = { sortByValue = true })
                    }
                    Spacer(Modifier.height(8.dp))
                    if (materialsSorted.isEmpty()) {
                        Text("Loading inventory", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    } else {
                        materialsSorted.forEach { item ->
                            val lots = remember(snapshot.inventory, item.id) { FinanceEngine.purchasesOf(snapshot.inventory, item.id) }
                            val pending = remember(item, lots) { FinanceEngine.materialPending(item, lots) }
                            val low = item.quantity <= 0.0
                            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMaterialLots = item }
                                    .padding(vertical = 10.dp)
                                    .testTag("factory_mat_${item.id}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    val unitDesc = if (item.purchaseUnitName.isNotBlank()) "Unit: ${item.purchaseUnitName} (1 = ${InputGuards.fmtNum(item.conversionFactor)} kg) • " else ""
                                    Text(
                                        "${unitDesc}Avg ${InputGuards.formatMoney(item.cost, currency, masked)}/kg • ${lots.size} lots" +
                                            if (pending > 0.01) " • Pending ${InputGuards.formatMoney(pending, currency, masked)}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accents.textMuted
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "${InputGuards.fmtNum(item.quantity)} kg",
                                        fontFamily = ManropeFont,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = if (low) accents.gold else accents.emerald
                                    )
                                    Text(
                                        InputGuards.formatMoney(item.quantity * item.cost, currency, masked),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accents.textMuted
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = accents.inputFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                Text("Total collected weight", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${InputGuards.fmtNum(materialsSorted.sumOf { it.quantity })} kg",
                                    fontFamily = ManropeFont,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Box(
                                Modifier
                                    .width(1.dp)
                                    .height(34.dp)
                                    .background(accents.borderSubtle)
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text("Total inventory value", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    InputGuards.formatMoney(materialsSorted.sumOf { it.quantity * it.cost }, currency, masked),
                                    fontFamily = ManropeFont,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 4. Performance Summary (ported from updateFactorySummaryCard)
            item {
                val sumAvailable = pools.sumOf { it.availableUnits }
                val sumConsumed = periodProduction.sumOf { it.formulaUnits }
                val sumOutput = periodProduction.sumOf { it.net }
                val sumTotalCost = periodProduction.sumOf { it.totalCost }
                val sumTotalProfit = periodProduction.sumOf { it.profit }
                val sumMatVal = periodProduction.sumOf { if (it.formulaCost > 0.0) it.formulaCost else it.totalCost }
                val avgProfitPerKg = if (sumOutput > 0.0) sumTotalProfit / sumOutput else 0.0

                val sumRawUsed = periodProduction.sumOf { entry ->
                    val matTotal = entry.formulaMaterials.sumOf { fm ->
                        (fm["qty"] as? Number)?.toDouble()
                            ?: ((fm["quantity"] as? Number)?.toDouble()?.let { it * entry.formulaUnits })
                            ?: 0.0
                    }
                    if (matTotal > 0.0) {
                        matTotal
                    } else {
                        val f = snapshot.formulas.firstOrNull { it.id == entry.formulaId }
                        val wpu = f?.let { FormulaEngine.describeFormula(it, snapshot.formulas, snapshot.inventory).weight } ?: 0.0
                        wpu * entry.formulaUnits
                    }
                }
                val frozenUnitCostTotal = periodProduction.sumOf { entry ->
                    val unitsUsed = entry.formulaUnits
                    val poolCost = pools.firstOrNull { it.formulaId == entry.formulaId }?.avgUnitCost ?: 0.0
                    if (entry.formulaCost > 0.0 && unitsUsed > 0.0) entry.formulaCost else unitsUsed * poolCost
                }
                val avgCostPerUnit = if (sumConsumed > 0.0) frozenUnitCostTotal / sumConsumed else 0.0

                val rawByMaterial = remember(periodProduction, snapshot.formulas, snapshot.inventory) {
                    val map = linkedMapOf<String, Pair<Double, Double>>()
                    periodProduction.forEach { entry ->
                        val u = entry.formulaUnits
                        if (u <= 0.0) return@forEach
                        val items = if (entry.formulaMaterials.isNotEmpty()) {
                            entry.formulaMaterials.map { fm ->
                                val nm = fm["name"]?.toString().orEmpty()
                                val q = (fm["quantity"] as? Number)?.toDouble() ?: (fm["qty"] as? Number)?.toDouble() ?: 0.0
                                val c = (fm["cost"] as? Number)?.toDouble() ?: 0.0
                                Triple(nm.ifBlank { "Unknown" }, q * u, c * q * u)
                            }
                        } else {
                            val f = snapshot.formulas.firstOrNull { it.id == entry.formulaId }
                            val descF = f?.let { FormulaEngine.describeFormula(it, snapshot.formulas, snapshot.inventory) }
                            descF?.items?.map { line ->
                                val inv = materials.firstOrNull { it.id == line.id }
                                    ?: materials.firstOrNull { it.name.equals(line.name, true) }
                                val c = inv?.cost ?: line.cost
                                Triple(inv?.name ?: line.name, line.quantity * u, c * line.quantity * u)
                            }.orEmpty()
                        }
                        items.forEach { (nm, q, c) ->
                            val cur = map[nm] ?: (0.0 to 0.0)
                            map[nm] = (cur.first + q) to (cur.second + c)
                        }
                    }
                    map.entries.sortedByDescending { it.value.first }
                }

                val totalAdditionalCost = periodFactory.filter { !it.isMerged }.sumOf { entry ->
                    if (entry.additionalCost > 0.0) entry.additionalCost
                    else (snapshot.formulas.firstOrNull { it.id == entry.formulaId }?.additionalCost ?: 0.0) * entry.units
                }
                val periodDays = remember(summaryMode, anchor, activeFactory) {
                    val a = anchor ?: java.time.LocalDate.now()
                    when (summaryMode) {
                        "daily" -> 1
                        "weekly" -> 7
                        "monthly" -> a.lengthOfMonth()
                        "yearly" -> a.lengthOfYear()
                        else -> {
                            val dates = activeFactory.mapNotNull { DateUtils.parseIso(it.date) }
                            if (dates.size > 1) {
                                (ChronoUnit.DAYS.between(dates.min(), dates.max()).toInt() + 1).coerceAtLeast(1)
                            } else 1
                        }
                    }
                }
                val avgAdditionalCostPerDay = if (totalAdditionalCost > 0.0) totalAdditionalCost / periodDays else 0.0

                var showMaterialsBreakdown by remember { mutableStateOf(false) }

                SarimCard {
                    FactoryCardTitle("Performance Summary")
                    Spacer(Modifier.height(10.dp))
                    FactoryChoiceRow(
                        options = listOf("Daily", "Weekly", "Monthly", "Yearly", "All Times"),
                        selectedIndex = listOf("daily", "weekly", "monthly", "yearly", "all").indexOf(summaryMode),
                        onSelect = { summaryMode = listOf("daily", "weekly", "monthly", "yearly", "all")[it] },
                        tagPrefix = "factory_summary_chip_",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("TOTAL PROFIT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = accents.textMuted)
                    Text(
                        InputGuards.formatMoney(sumTotalProfit, currency, masked),
                        fontFamily = ManropeFont,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = if (sumTotalProfit >= 0.0) accents.emerald else accents.danger
                    )
                    Text(
                        "${InputGuards.formatMoney(avgProfitPerKg, currency, masked)}/kg average",
                        style = MaterialTheme.typography.bodySmall,
                        color = accents.textMuted
                    )
                    Spacer(Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FactorySectionLabel("Units")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SarimHalfRingKpiBox("AVAILABLE", InputGuards.fmtNum(sumAvailable), accents.emerald, Modifier.weight(1f))
                            SarimHalfRingKpiBox("USED", InputGuards.fmtNum(sumConsumed), accents.gold, Modifier.weight(1f))
                            FactoryHalfRingSubKpiBox(
                                label = "COST / DAY",
                                value = InputGuards.formatMoney(avgAdditionalCostPerDay, currency, masked),
                                subLabel = "average extra",
                                ringColor = accents.danger,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        FactorySectionLabel("Output")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SarimHalfRingKpiBox("QUANTITY", "${InputGuards.fmtNum(sumOutput)} kg", accents.emerald, Modifier.weight(1f))
                            SarimHalfRingKpiBox("RAW USED", "${InputGuards.fmtNum(sumRawUsed)} kg", accents.cyan, Modifier.weight(1f))
                        }
                        if (rawByMaterial.isNotEmpty()) {
                            val breakdownMint = if (accents.isDark) accents.softPillFill else Color(0xFFDCFCE7).copy(alpha = 0.7f)
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = breakdownMint,
                                border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showMaterialsBreakdown = !showMaterialsBreakdown }
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                            .testTag("factory_materials_breakdown_toggle"),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "MATERIALS BREAKDOWN",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = accents.emerald
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                "${rawByMaterial.size}",
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Icon(
                                                if (showMaterialsBreakdown) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                                contentDescription = "Toggle materials breakdown",
                                                tint = accents.emerald,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (showMaterialsBreakdown) {
                                        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
                                            rawByMaterial.forEachIndexed { idx, (matName, pair) ->
                                                if (idx > 0) HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.55f))
                                                Row(
                                                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        matName.uppercase(),
                                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        buildAnnotatedString {
                                                            append("${InputGuards.fmtNum(pair.first)} kg · ")
                                                            withStyle(SpanStyle(color = accents.danger, fontWeight = FontWeight.Medium)) {
                                                                append(InputGuards.formatMoney(pair.second, currency, masked))
                                                            }
                                                        },
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        FactorySectionLabel("Cost")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SarimHalfRingKpiBox("PER UNIT", InputGuards.formatMoney(avgCostPerUnit, currency, masked), accents.danger, Modifier.weight(1f))
                            SarimHalfRingKpiBox("TOTAL", InputGuards.formatMoney(sumTotalCost, currency, masked), accents.gold, Modifier.weight(1f))
                            SarimHalfRingKpiBox("MATERIALS", InputGuards.formatMoney(sumMatVal, currency, masked), accents.danger, Modifier.weight(1f))
                        }
                    }
                }
            }

            // 5. Recent Production Activity
            item {
                SarimCard {
                    FactoryCardTitle("Recent Production Activity")
                    Spacer(Modifier.height(10.dp))
                    if (periodFactory.isEmpty()) {
                        Text("No recent activity", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    } else {
                        SarimYearMonthDayTree(
                            items = periodFactory,
                            dateOf = { it.date },
                            summaryLabel = { list -> "${list.size} ${if (list.size == 1) "entry" else "entries"}" },
                            yearSubLabel = { list -> "Cost ${InputGuards.formatMoney(list.sumOf { it.totalCost }, currency, masked)}" },
                            monthSubLabel = { list -> "Cost ${InputGuards.formatMoney(list.sumOf { it.totalCost }, currency, masked)}" },
                            daySummaryLabel = { list -> "Cost: ${InputGuards.formatMoney(list.sumOf { it.totalCost }, currency, masked)}" },
                            initiallyExpandFirstDay = false,
                            itemRow = { rec ->
                                val timeStr = remember(rec.timestamp) {
                                    if (rec.timestamp > 0L) SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(rec.timestamp)) else ""
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(accents.cyan.copy(alpha = 0.08f))
                                        .cyanDotMatrixPattern(accents.cyan, accents.cyan)
                                        .clickable { selectedFactoryRec = rec }
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(
                                                Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.onSurface)
                                            )
                                            Text(
                                                rec.formulaName.ifBlank { FormulaEngine.storeLabelFor(rec.store, snapshot.stores).ifBlank { "Factory Batch" } }.uppercase(),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                "${InputGuards.fmtNum(rec.units)} UNITS",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accents.textMuted
                                            )
                                            EditedBadge(rec.isEdited)
                                        }
                                        if (timeStr.isNotBlank()) {
                                            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                        }
                                    }
                                    Text(
                                        InputGuards.formatMoney(rec.totalCost, currency, masked),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    selectedFactoryRec?.let { rec ->
        FactoryBatchDetailSheet(
            rec = rec,
            stores = snapshot.stores,
            currency = currency,
            masked = masked,
            isAdmin = session.mode == "admin",
            vm = vm,
            onEdit = { startEditBatch(it) },
            onDismiss = { selectedFactoryRec = null }
        )
    }

    selectedMaterialLots?.let { mat ->
        val lots = remember(snapshot.inventory, mat.id) {
            FinanceEngine.purchasesOf(snapshot.inventory, mat.id).sortedByDescending { it.purchaseDate }
        }
        MaterialPurchaseLotsSheet(
            material = mat,
            lots = lots,
            currency = currency,
            masked = masked,
            isAdmin = session.mode == "admin",
            vm = vm,
            onDismiss = { selectedMaterialLots = null }
        )
    }

    if (showEditDiff && editingBatch != null && selectedFormula != null) {
        val orig = editingBatch!!
        val f = selectedFormula!!
        val u = (unitsStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
        EditDiffConfirmDialog(
            title = "Confirm Factory Batch Edit",
            rows = listOf(
                Triple("Date", orig.date, date),
                Triple("Formula", orig.formulaName, f.name),
                Triple("Units", InputGuards.fmtNum(orig.units), InputGuards.fmtNum(u))
            ),
            onConfirm = {
                showEditDiff = false
                vm.editFactoryBatch(orig, date, f, u, orig.notes) { resetBatchForm() }
            },
            onDismiss = { showEditDiff = false }
        )
    }

    if (showDiscardEdit) {
        DiscardEditDialog(
            onDiscard = {
                showDiscardEdit = false
                resetBatchForm()
            },
            onDismiss = { showDiscardEdit = false }
        )
    }
}

@Composable
private fun FactoryCardTitle(text: String, accent: Boolean = false) {
    val accents = LocalSarimAccents.current
    Text(
        text = text,
        fontFamily = PlayfairFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = (-0.14).sp,
        color = if (accent) accents.emerald else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun FactorySectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        fontFamily = JakartaFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.sp,
        letterSpacing = 0.6.sp,
        color = LocalSarimAccents.current.textMuted
    )
}

@Composable
private fun FactoryPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accents = LocalSarimAccents.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) accents.softPillFill else accents.inputFill,
        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
        modifier = modifier
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accents.emerald else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun FactoryChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { idx, label ->
            FactoryPill(label, selected = idx == selectedIndex, onClick = { onSelect(idx) }, modifier = Modifier.testTag("$tagPrefix$idx"))
        }
    }
}

@Composable
private fun FactoryHalfRingSubKpiBox(
    label: String,
    value: String,
    subLabel: String,
    ringColor: Color,
    modifier: Modifier = Modifier
) {
    val accents = LocalSarimAccents.current
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(accents.inputFill.copy(alpha = 0.72f))
            .border(1.dp, accents.borderSubtle, shape)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = 2.5.dp.toPx()
            val arcDiameter = size.height
            drawArc(
                color = ringColor,
                startAngle = 90f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(strokePx / 2f, strokePx / 2f),
                size = Size(arcDiameter - strokePx, size.height - strokePx),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Column(modifier = Modifier.padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, letterSpacing = 0.6.sp),
                color = accents.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ringColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subLabel,
                fontSize = 9.5.sp,
                color = accents.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FactoryGroupBox(
    title: String,
    rows: List<Triple<String, String, Color>>,
    modifier: Modifier = Modifier
) {
    val accents = LocalSarimAccents.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accents.inputFill)
            .border(1.dp, accents.borderSubtle, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            fontFamily = JakartaFont,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            color = accents.emerald
        )
        rows.forEach { (k, v, c) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(k, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = accents.textMuted, maxLines = 1)
                Text(
                    v,
                    fontFamily = ManropeFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = c,
                    maxLines = 1
                )
            }
        }
    }
}

