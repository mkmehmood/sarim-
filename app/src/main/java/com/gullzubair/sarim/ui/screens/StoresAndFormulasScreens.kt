package com.gullzubair.sarim.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.FormulaConfig
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimDangerButton
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimNoteBanner
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

@Composable
fun StoreManagerScreen(
    snapshot: WorkspaceSnapshot,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney

    var editId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var selectedFormulaId by remember { mutableStateOf("") }
    var rateStr by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "STORE MANAGER", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SarimCard {
                    Text(
                        text = if (editId == null) "NEW STORE" else "EDIT STORE",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    SarimPillInput(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = "STORE NAME",
                        placeholder = "e.g. Chashma",
                        testTag = "sm_name_input"
                    )
                    Spacer(Modifier.height(10.dp))
                    val formulaLabel = snapshot.formulas.firstOrNull { it.id == selectedFormulaId }?.name ?: "None (No Formula)"
                    PickerField(
                        label = "FORMULA",
                        selectedLabel = formulaLabel,
                        items = listOf(null) + snapshot.formulas,
                        itemLabel = { it?.name ?: "None (No Formula)" },
                        onSelect = { selectedFormulaId = it?.id ?: "" },
                        testTag = "sm_formula_picker"
                    )
                    Spacer(Modifier.height(10.dp))
                    DecimalField(
                        label = "SALE PRICE (PER KG)",
                        value = rateStr,
                        onValueChange = { rateStr = it },
                        testTag = "sm_rate_input",
                        suffix = "$currency /kg"
                    )
                    if (editId != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Active Store", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = active, onCheckedChange = { active = it })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (editId != null) {
                            Surface(
                                onClick = {
                                    editId = null; name = ""; selectedFormulaId = ""; rateStr = ""; active = true
                                },
                                shape = RoundedCornerShape(24.dp),
                                color = accents.inputFill,
                                border = BorderStroke(1.dp, accents.borderSubtle),
                                modifier = Modifier.weight(0.55f).height(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        SarimGradientButton(
                            text = if (editId == null) "Add Store" else "Update Store",
                            onClick = {
                                vm.saveStore(editId, name, selectedFormulaId, rateStr.toDoubleOrNull() ?: 0.0, active)
                                editId = null; name = ""; rateStr = ""; selectedFormulaId = ""; active = true
                            },
                            enabled = name.isNotBlank(),
                            testTag = "sm_save_btn",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Text(
                    text = "EXISTING STORES",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
                    color = accents.textMuted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            if (snapshot.stores.isEmpty()) {
                item { EmptyStateCard("No stores configured", "Add your first store above to start tracking production and sales.") }
            } else {
                items(snapshot.stores, key = { it.id }) { s ->
                    val linkedFormulaName = snapshot.formulas.firstOrNull { it.id == s.formulaId }?.name ?: "DIRECT"
                    val hasRecords = remember(snapshot, s.storeKey) {
                        snapshot.production.any { !it.recycled && it.storeKey.equals(s.storeKey, true) } ||
                            snapshot.sales.any { !it.recycled && it.storeKey.equals(s.storeKey, true) }
                    }
                    val badge = s.name.trim().take(2).uppercase().ifBlank { "ST" }
                    SarimCard(contentPadding = PaddingValues(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(accents.softPillFill)
                                    .border(1.dp, accents.emerald.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(badge, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = s.name.uppercase(),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val recordStatus = if (hasRecords) "Has records" else if (s.active) "Active" else "Inactive"
                                Text(
                                    text = "Key: ${s.storeKey.lowercase()} • ${linkedFormulaName.uppercase()} • $recordStatus",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Sale Price: ${InputGuards.formatMoney(s.salePrice, currency, masked)}/kg",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accents.emerald
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                onClick = {
                                    editId = s.id; name = s.name; selectedFormulaId = s.formulaId
                                    rateStr = InputGuards.formatNum(s.salePrice).replace(",", "")
                                    active = s.active
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = accents.softPillFill,
                                border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.45f)),
                                modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("sm_edit_${s.id}")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                    Text("Edit", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = accents.emerald)
                                }
                            }
                        }
                    }
                }
            }

            item {
                SarimNoteBanner("Note: Changing a store's sale price updates future defaults and keeps historical entries intact.")
            }
        }
    }
}

@Composable
fun FormulaStoreScreen(
    snapshot: WorkspaceSnapshot,
    onBack: () -> Unit,
    onEditFormula: (String) -> Unit,
    onOpenMix: (String) -> Unit,
    onOpenFix: (String) -> Unit
) {
    val accents = LocalSarimAccents.current
    val masked = snapshot.settings.hideSensitiveMoney
    var speedDialExpanded by remember { mutableStateOf(false) }
    val invByName = remember(snapshot.inventory) {
        snapshot.inventory.filter { !it.recycled }.associateBy { it.name.trim().lowercase() }
    }
    val formulaById = remember(snapshot.formulas) { snapshot.formulas.associateBy { it.id } }
    val firstFormulaId = snapshot.formulas.firstOrNull()?.id ?: ""

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SarimSubpageHeader(title = "FORMULA STORE", onBack = onBack)

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (snapshot.formulas.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No formulas defined",
                            detail = "Tap + below to create a recipe formula for production and factory batches.",
                            actionLabel = "Create Formula",
                            onAction = { onEditFormula("") }
                        )
                    }
                } else {
                    items(snapshot.formulas, key = { it.id }) { f ->
                        val baseWeightKg = f.ingredients.sumOf { (it["qtyKg"] as? Number)?.toDouble() ?: 0.0 }
                        val flat = remember(f, snapshot.formulas, snapshot.inventory) {
                            FormulaEngine.flattenFormula(f, snapshot.formulas, snapshot.inventory, 1.0)
                        }
                        val rawMatCostPerUnit = remember(flat) { flat.sumOf { it.qtyKg * it.unitCostPerKg } }
                        val totalCostPerUnit = (rawMatCostPerUnit + f.additionalCost).coerceAtLeast(0.0)
                        val adjWeight = (baseWeightKg + f.costAdjustment).coerceAtLeast(0.01)
                        val costPerKg = if (baseWeightKg > 0.0) totalCostPerUnit / adjWeight else 0.0

                        val linkedIn = snapshot.formulas.filter { other ->
                            other.id != f.id && other.ingredients.any { (it["linkFormulaId"] as? String) == f.id }
                        }.map { it.name.uppercase() }
                        val usedByStores = snapshot.stores.filter { it.formulaId == f.id }.map { it.name.uppercase() }
                        val footerPrefix = buildList {
                            if (linkedIn.isNotEmpty()) add("Linked in ${linkedIn.joinToString(", ")}")
                            if (usedByStores.isNotEmpty()) add("Used by ${usedByStores.joinToString(", ")}")
                        }.joinToString(" • ")

                        SarimCard(
                            modifier = Modifier
                                .clickable { onEditFormula(f.id) }
                                .testTag("formula_card_${f.id}")
                        ) {
                            Text(
                                text = "${f.name.uppercase()} (${InputGuards.formatNum(f.unitsPerBatch)} UNIT)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(Modifier.height(8.dp))

                            f.ingredients.forEach { ing ->
                                val ingName = (ing["name"] as? String ?: "").trim()
                                val ingQty = (ing["qtyKg"] as? Number)?.toDouble() ?: 0.0
                                val linkId = (ing["linkFormulaId"] as? String ?: "").trim()
                                val child = formulaById[linkId]
                                if (child != null) {
                                    val childFlat = FormulaEngine.flattenFormula(child, snapshot.formulas, snapshot.inventory, 1.0)
                                    val childBase = child.ingredients.sumOf { (it["qtyKg"] as? Number)?.toDouble() ?: 0.0 }.coerceAtLeast(1.0)
                                    val mult = ingQty / childBase
                                    val childCost = childFlat.sumOf { it.qtyKg * it.unitCostPerKg } * mult
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            text = "${ingName.uppercase()} • sub formula (${InputGuards.formatNum(ingQty)} kg)",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (masked) "•••" else InputGuards.formatNum(childCost),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    childFlat.forEach { sub ->
                                        val scaledQty = sub.qtyKg * mult
                                        val subCost = scaledQty * sub.unitCostPerKg
                                        Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 1.dp, bottom = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                text = "↳ ${sub.name.uppercase()} (${InputGuards.formatNum(scaledQty)} kg)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = accents.textMuted,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = if (masked) "•••" else InputGuards.formatNum(subCost),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = accents.textMuted
                                            )
                                        }
                                    }
                                } else {
                                    val overrideCost = (ing["overrideUnitCost"] as? Number)?.toDouble()
                                    val unitCost = overrideCost ?: invByName[ingName.lowercase()]?.avgCostPerKg ?: 0.0
                                    val rowCost = ingQty * unitCost
                                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            text = "${ingName.uppercase()} (${InputGuards.formatNum(ingQty)} kg)",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (masked) "•••" else InputGuards.formatNum(rowCost),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            if (f.additionalCost > 0.0) {
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = "Additional Cost (${InputGuards.formatNum(f.additionalCost)} per unit)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accents.danger
                                    )
                                    Text(
                                        text = if (masked) "•••" else InputGuards.formatNum(f.additionalCost),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accents.danger
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                            Spacer(Modifier.height(8.dp))

                            FormulaMetricRow("Unit Weight", InputGuards.formatKg(baseWeightKg))
                            FormulaMetricRow("Raw Material Cost per Unit", if (masked) "•••" else InputGuards.formatNum(rawMatCostPerUnit))
                            FormulaMetricRow("Total Cost per Unit", if (masked) "•••" else InputGuards.formatNum(totalCostPerUnit), bold = true)
                            FormulaMetricRow("Cost per kg (Sales/Calc)", if (masked) "•••" else InputGuards.formatNum(costPerKg))

                            Spacer(Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Text(
                                    text = if (footerPrefix.isNotBlank()) "$footerPrefix • Tap to edit" else "Tap to edit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accents.textMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // Emerald Speed-Dial FAB
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 18.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(visible = speedDialExpanded) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpeedDialRow(label = "Correction", icon = Icons.Outlined.Build, testTag = "formula_fab_fix") {
                        speedDialExpanded = false
                        onOpenFix(firstFormulaId)
                    }
                    SpeedDialRow(label = "Batching", icon = Icons.Outlined.Balance, testTag = "formula_fab_mix") {
                        speedDialExpanded = false
                        onOpenMix(firstFormulaId)
                    }
                    SpeedDialRow(label = "Formula", icon = Icons.Outlined.Science, testTag = "formula_fab_new") {
                        speedDialExpanded = false
                        onEditFormula("")
                    }
                }
            }
            FloatingActionButton(
                onClick = { speedDialExpanded = !speedDialExpanded },
                shape = CircleShape,
                containerColor = accents.emerald,
                contentColor = Color.White,
                modifier = Modifier.size(58.dp).testTag("formula_speed_dial_fab")
            ) {
                Icon(
                    imageVector = if (speedDialExpanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "Formula actions"
                )
            }
        }
    }
}

@Composable
private fun FormulaMetricRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
            color = if (bold) MaterialTheme.colorScheme.onSurface else LocalSarimAccents.current.textMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
        )
    }
}

@Composable
private fun SpeedDialRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    val accents = LocalSarimAccents.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable(onClick = onClick).testTag(testTag)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = accents.headerFrosted,
            border = BorderStroke(1.dp, accents.borderSubtle),
            shadowElevation = 4.dp
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        Surface(
            shape = CircleShape,
            color = accents.emerald,
            contentColor = Color.White,
            shadowElevation = 4.dp,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private data class FormulaIngRowState(
    val name: String,
    val qtyStr: String,
    val linkFormulaId: String,
    val overrideCostStr: String
)

@Composable
fun FormulaEditScreen(
    snapshot: WorkspaceSnapshot,
    formulaId: String,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val existing = remember(snapshot.formulas, formulaId) { snapshot.formulas.firstOrNull { it.id == formulaId } }
    val invByName = remember(snapshot.inventory) { snapshot.inventory.filter { !it.recycled }.associateBy { it.name.trim().lowercase() } }
    val formulaById = remember(snapshot.formulas) { snapshot.formulas.associateBy { it.id } }

    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var unitsStr by remember(existing) { mutableStateOf((existing?.unitsPerBatch ?: 1.0).toString()) }
    var addCostStr by remember(existing) { mutableStateOf((existing?.additionalCost ?: 0.0).toString()) }
    var adjStr by remember(existing) { mutableStateOf((existing?.costAdjustment ?: 0.0).toString()) }

    val ingredients = remember(existing) {
        mutableStateListOf<FormulaIngRowState>().apply {
            existing?.ingredients?.forEach { m ->
                val n = m["name"] as? String ?: ""
                val q = ((m["qtyKg"] as? Number)?.toDouble() ?: 0.0).toString()
                val l = m["linkFormulaId"] as? String ?: ""
                val ov = (m["overrideUnitCost"] as? Number)?.toDouble()?.toString() ?: ""
                add(FormulaIngRowState(n, q, l, ov))
            }
            if (isEmpty()) add(FormulaIngRowState("", "", "", ""))
        }
    }

    val linkCandidates = remember(snapshot.formulas, existing) {
        snapshot.formulas.filter { cand ->
            existing == null || FormulaEngine.canLink(snapshot.formulas, existing.id, cand.id)
        }
    }
    val materialChoices = remember(snapshot.inventory) {
        snapshot.inventory.filter { !it.recycled }.map { it.name }.distinct().sorted()
    }

    val unitWeight = ingredients.sumOf { it.qtyStr.toDoubleOrNull() ?: 0.0 }
    val rawCostPerUnit = ingredients.sumOf { row ->
        val qty = row.qtyStr.toDoubleOrNull() ?: 0.0
        val unitPrice = row.overrideCostStr.toDoubleOrNull()
            ?: formulaById[row.linkFormulaId]?.let { child ->
                val baseW = child.ingredients.sumOf { (it["qtyKg"] as? Number)?.toDouble() ?: 0.0 }.coerceAtLeast(1.0)
                FormulaEngine.calculateBatchCost(child, snapshot.formulas, snapshot.inventory, 1.0) / baseW
            }
            ?: invByName[row.name.trim().lowercase()]?.avgCostPerKg
            ?: 0.0
        qty * unitPrice
    }
    val addCost = addCostStr.toDoubleOrNull() ?: 0.0
    val adjVal = adjStr.toDoubleOrNull() ?: 0.0
    val totalCostPerUnit = (rawCostPerUnit + addCost).coerceAtLeast(0.0)
    val costPerKgCalc = if (unitWeight > 0.0) totalCostPerUnit / (unitWeight + adjVal).coerceAtLeast(0.01) else 0.0

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = if (existing == null) "NEW FORMULA" else "EDIT FORMULA", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SarimCard {
                    SarimPillInput(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = "FORMULA NAME",
                        placeholder = "e.g. Standard Blend",
                        testTag = "fe_name_input"
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DecimalField("ADDITIONAL COST (PER UNIT)", addCostStr, { addCostStr = it }, "fe_addcost_input", Modifier.weight(1f))
                        DecimalField("COST ADJUSTMENT (X)", adjStr, { adjStr = it }, "fe_adj_input", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    DecimalField("UNITS PER BATCH", unitsStr, { unitsStr = it }, "fe_units_input")
                }
            }

            item {
                Text(
                    text = "INGREDIENTS",
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                    color = accents.textMuted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            items(ingredients.size) { idx ->
                val row = ingredients[idx]
                var matMenuOpen by remember { mutableStateOf(false) }
                val autoUnitCost = formulaById[row.linkFormulaId]?.let { child ->
                    val baseW = child.ingredients.sumOf { (it["qtyKg"] as? Number)?.toDouble() ?: 0.0 }.coerceAtLeast(1.0)
                    InputGuards.round2(FormulaEngine.calculateBatchCost(child, snapshot.formulas, snapshot.inventory, 1.0) / baseW)
                } ?: invByName[row.name.trim().lowercase()]?.avgCostPerKg ?: 0.0

                SarimCard(contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1.3f)) {
                            SarimPillInput(
                                value = row.name,
                                onValueChange = { ingredients[idx] = row.copy(name = it, linkFormulaId = "") },
                                label = "MATERIAL",
                                placeholder = "Tap or type",
                                trailingIcon = {
                                    IconButton(onClick = { matMenuOpen = true }, modifier = Modifier.size(32.dp)) {
                                        Text("▾", style = MaterialTheme.typography.titleMedium, color = accents.emerald)
                                    }
                                }
                            )
                            DropdownMenu(expanded = matMenuOpen, onDismissRequest = { matMenuOpen = false }) {
                                materialChoices.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m.uppercase()) },
                                        onClick = {
                                            ingredients[idx] = row.copy(name = m, linkFormulaId = "")
                                            matMenuOpen = false
                                        }
                                    )
                                }
                                linkCandidates.forEach { sub ->
                                    DropdownMenuItem(
                                        text = { Text("${sub.name.uppercase()} (Sub-Formula)", color = accents.emerald) },
                                        onClick = {
                                            ingredients[idx] = row.copy(name = sub.name, linkFormulaId = sub.id)
                                            matMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(0.85f)) {
                            Text(
                                text = "COST / UNIT",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                                color = accents.textMuted,
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                            SarimPillInput(
                                value = row.overrideCostStr,
                                onValueChange = { raw ->
                                    if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                        ingredients[idx] = row.copy(overrideCostStr = raw)
                                    }
                                },
                                placeholder = InputGuards.formatNum(autoUnitCost),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }

                        DecimalField(
                            label = "QTY (KG)",
                            value = row.qtyStr,
                            onValueChange = { ingredients[idx] = row.copy(qtyStr = it) },
                            testTag = "fe_ing_qty_$idx",
                            modifier = Modifier.weight(0.8f)
                        )

                        Surface(
                            onClick = { if (ingredients.size > 1) ingredients.removeAt(idx) },
                            shape = CircleShape,
                            color = accents.danger.copy(alpha = 0.14f),
                            modifier = Modifier.padding(top = 18.dp).size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("×", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = accents.danger)
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    onClick = { ingredients.add(FormulaIngRowState("", "", "", "")) },
                    shape = RoundedCornerShape(16.dp),
                    color = accents.inputFill,
                    border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("fe_add_ing_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+ Add Ingredient Row", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tip: Leave Cost / Unit blank to use live weighted raw material or sub-formula cost automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = accents.textMuted
                )
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = accents.softPillFill,
                    border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FormulaMetricRow("Unit Weight", InputGuards.formatKg(unitWeight))
                        FormulaMetricRow("Raw Material Cost per Unit", "$currency ${InputGuards.formatNum(rawCostPerUnit)}")
                        FormulaMetricRow("Total Cost per Unit", "$currency ${InputGuards.formatNum(totalCostPerUnit)}", bold = true)
                        FormulaMetricRow("Cost per kg (Sales/Calc)", "$currency ${InputGuards.formatNum(costPerKgCalc)}")
                    }
                }
            }
        }

        Surface(
            color = accents.headerFrosted,
            border = BorderStroke(1.dp, accents.borderSubtle)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (existing != null) {
                    SarimDangerButton(
                        text = "Delete Formula",
                        onClick = { vm.deleteFormula(existing.id, onDeleted = onBack) },
                        modifier = Modifier.weight(0.85f),
                        testTag = "fe_delete_btn"
                    )
                }
                SarimGradientButton(
                    text = "Save Formula",
                    onClick = {
                        val list = ingredients.mapNotNull { row ->
                            val qty = row.qtyStr.toDoubleOrNull() ?: 0.0
                            if (row.name.isNotBlank() && qty > 0.0) {
                                buildMap<String, Any> {
                                    put("name", row.name.trim())
                                    put("qtyKg", qty)
                                    put("linkFormulaId", row.linkFormulaId)
                                    row.overrideCostStr.toDoubleOrNull()?.let { put("overrideUnitCost", it) }
                                }
                            } else null
                        }
                        vm.saveFormula(
                            existing = existing,
                            name = name,
                            unitsPerBatch = unitsStr.toDoubleOrNull() ?: 1.0,
                            additionalCost = addCostStr.toDoubleOrNull() ?: 0.0,
                            adjustment = adjStr.toDoubleOrNull() ?: 0.0,
                            ingredients = list
                        )
                        onBack()
                    },
                    enabled = name.isNotBlank(),
                    testTag = "fe_save_btn",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
