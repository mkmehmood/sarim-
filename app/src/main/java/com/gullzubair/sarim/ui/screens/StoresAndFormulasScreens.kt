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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.FormulaConfig
import com.gullzubair.sarim.data.model.FormulaIngredient
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
import com.gullzubair.sarim.ui.components.SarimConfirmDialog
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import java.util.Locale

@Composable
fun StoreManagerScreen(
    snapshot: WorkspaceSnapshot,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val masked = session.restrictMoney || snapshot.settings.hideSensitiveMoney

    var editKey by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var selectedFormulaId by remember { mutableStateOf("") }
    var rateStr by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }
    var confirmDeleteKey by remember { mutableStateOf<String?>(null) }

    val activeStores = remember(snapshot.stores) {
        snapshot.stores.filter { it.deletedAt == null }
    }

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
                        text = if (editKey == null) "NEW STORE" else "EDIT STORE",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JakartaFont,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
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
                    if (editKey != null) {
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
                        if (editKey != null) {
                            Surface(
                                onClick = {
                                    editKey = null; name = ""; selectedFormulaId = ""; rateStr = ""; active = true
                                },
                                shape = RoundedCornerShape(24.dp),
                                color = accents.inputFill,
                                border = BorderStroke(1.dp, accents.borderSubtle),
                                modifier = Modifier
                                    .weight(0.55f)
                                    .height(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        SarimGradientButton(
                            text = if (editKey == null) "Add Store" else "Update Store",
                            onClick = {
                                vm.saveStore(
                                    existingKey = editKey,
                                    name = name,
                                    formulaId = selectedFormulaId.ifBlank { null },
                                    rate = rateStr.toDoubleOrNull() ?: 0.0,
                                    active = active
                                )
                                editKey = null; name = ""; rateStr = ""; selectedFormulaId = ""; active = true
                            },
                            enabled = name.isNotBlank(),
                            testTag = "sm_save_btn",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (editKey != null && snapshot.profile.isAdmin) {
                        Spacer(Modifier.height(10.dp))
                        SarimDangerButton(
                            text = "Remove Store",
                            onClick = { confirmDeleteKey = editKey },
                            testTag = "sm_delete_btn"
                        )
                    }
                }
            }

            item {
                Text(
                    text = "EXISTING STORES",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JakartaFont,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = accents.textMuted,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            if (activeStores.isEmpty()) {
                item { EmptyStateCard("No stores configured", "Add your first store above to start tracking production and sales.") }
            } else {
                items(activeStores, key = { it.key }) { s ->
                    val linkedFormulaName = snapshot.formulas.firstOrNull { it.id == s.formulaId }?.name ?: "DIRECT"
                    val hasRecords = remember(snapshot.production, snapshot.sales, s.key, s.name) {
                        snapshot.production.any {
                            it.deletedAt == null && (it.store.equals(s.key, true) || it.store.equals(s.name, true))
                        } || snapshot.sales.any {
                            it.deletedAt == null && (it.supplyStore.equals(s.key, true) || it.supplyStore.equals(s.name, true))
                        }
                    }
                    val badge = s.name.trim().take(2).uppercase(Locale.ROOT).ifBlank { "ST" }
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
                                    text = s.name.uppercase(Locale.ROOT),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val recordStatus = if (hasRecords) "Has records" else if (s.active) "Active" else "Inactive"
                                Text(
                                    text = "Key: ${s.key.lowercase(Locale.ROOT)} • ${linkedFormulaName.uppercase(Locale.ROOT)} • $recordStatus",
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
                                    editKey = s.key
                                    name = s.name
                                    selectedFormulaId = s.formulaId ?: ""
                                    rateStr = InputGuards.fmtNum(s.salePrice).replace(",", "")
                                    active = s.active
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = accents.softPillFill,
                                border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("sm_edit_${s.key}")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
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

    confirmDeleteKey?.let { keyToDel ->
        val storeLabel = activeStores.firstOrNull { it.key == keyToDel }?.name ?: keyToDel
        SarimConfirmDialog(
            title = "Remove Store?",
            message = "Store: $storeLabel\n\nRemove it from active stores? Existing historical records keep their store reference.",
            confirmText = "Remove",
            danger = true,
            onConfirm = {
                vm.deleteStore(keyToDel)
                confirmDeleteKey = null
                editKey = null; name = ""; selectedFormulaId = ""; rateStr = ""; active = true
            },
            onDismiss = { confirmDeleteKey = null }
        )
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
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney
    var speedDialExpanded by remember { mutableStateOf(false) }

    val activeMaterials = remember(snapshot.inventory) {
        snapshot.inventory.filter { it.deletedAt == null && !it.isPurchaseLot }
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
                        val breakdown = remember(f, snapshot.formulas, snapshot.inventory) {
                            FormulaEngine.describeFormula(f, snapshot.formulas, snapshot.inventory)
                        }
                        val linkedIn = remember(snapshot.formulas, f.id) {
                            FormulaEngine.formulaUsers(snapshot.formulas, f.id).map { it.name.uppercase(Locale.ROOT) }
                        }
                        val usedByStores = remember(snapshot.stores, f.id) {
                            snapshot.stores.filter { it.deletedAt == null && it.formulaId == f.id }.map { it.name.uppercase(Locale.ROOT) }
                        }
                        val footerPrefix = buildList {
                            if (linkedIn.isNotEmpty()) add("Linked in ${linkedIn.joinToString(", ")}")
                            if (usedByStores.isNotEmpty()) add("Used by ${usedByStores.joinToString(", ")}")
                        }.joinToString(" • ")

                        SarimCard(
                            modifier = Modifier
                                .clickable { onEditFormula(f.id) }
                                .testTag("formula_card_${f.id}")
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = f.name.uppercase(Locale.ROOT),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(color = accents.softPillFill, shape = RoundedCornerShape(10.dp)) {
                                    Text(
                                        text = "${InputGuards.fmtNum(f.costAdjustmentFactor)} UNIT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accents.emerald,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            f.ingredients.forEach { ing ->
                                if (FormulaEngine.isLink(ing)) {
                                    val child = formulaById[ing.formulaId]
                                    if (child != null) {
                                        val childDesc = FormulaEngine.describeFormula(child, snapshot.formulas, snapshot.inventory)
                                        val mult = if (childDesc.weight > 0.0) ing.quantity / childDesc.weight else 0.0
                                        val childCost = childDesc.raw * mult
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${child.name.uppercase(Locale.ROOT)} • sub-formula (${InputGuards.fmtNum(ing.quantity)} kg)",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = InputGuards.formatMoney(childCost, currency, masked),
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                        childDesc.items.forEach { sub ->
                                            val scaledQty = sub.quantity * mult
                                            val subCost = scaledQty * sub.cost
                                            Row(
                                                Modifier.fillMaxWidth().padding(start = 10.dp, top = 1.dp, bottom = 1.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "↳ ${sub.name.uppercase(Locale.ROOT)} (${InputGuards.fmtNum(scaledQty)} kg)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = accents.textMuted,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    text = InputGuards.formatMoney(subCost, currency, masked),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = accents.textMuted
                                                )
                                            }
                                        }
                                    } else {
                                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                            Text(
                                                text = "${ing.name.uppercase(Locale.ROOT)} (Missing sub-formula)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = accents.danger
                                            )
                                        }
                                    }
                                } else {
                                    val liveMat = activeMaterials.firstOrNull { it.id == ing.id }
                                        ?: activeMaterials.firstOrNull { it.name.trim().equals(ing.name.trim(), ignoreCase = true) }
                                    val unitCost = if ((liveMat?.cost ?: 0.0) > 0.0) liveMat!!.cost else ing.cost
                                    val rowCost = ing.quantity * unitCost
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${ing.name.uppercase(Locale.ROOT)} (${InputGuards.fmtNum(ing.quantity)} kg)",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = InputGuards.formatMoney(rowCost, currency, masked),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            if (breakdown.additionalCost > 0.0) {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Additional Cost" + if (breakdown.extraCost > 0.0) " (incl. sub-formula)" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accents.danger
                                    )
                                    Text(
                                        text = InputGuards.formatMoney(breakdown.additionalCost, currency, masked),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accents.danger
                                    )
                                }
                            }

                            if (breakdown.problems.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = breakdown.problems.joinToString(", ") { "${it.type}: ${it.name}" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.danger
                                )
                            }

                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                            Spacer(Modifier.height(8.dp))

                            FormulaMetricRow("Unit Weight", InputGuards.formatKg(breakdown.weight))
                            FormulaMetricRow("Raw Material Cost per Unit", InputGuards.formatMoney(breakdown.raw, currency, masked))
                            FormulaMetricRow("Total Cost per Unit", InputGuards.formatMoney(breakdown.perUnit, currency, masked), bold = true)
                            FormulaMetricRow("Cost per kg (Adjusted)", InputGuards.formatMoney(breakdown.perKg, currency, masked))

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
                modifier = Modifier
                    .size(58.dp)
                    .testTag("formula_speed_dial_fab")
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    val accents = LocalSarimAccents.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
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
    val id: String,
    val name: String,
    val qtyStr: String,
    val kind: String?,
    val linkFormulaId: String?,
    val costStr: String,
    val custom: Boolean
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
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val masked = session.restrictMoney || snapshot.settings.hideSensitiveMoney

    val existing = remember(snapshot.formulas, formulaId) {
        snapshot.formulas.firstOrNull { it.id == formulaId }
    }
    val activeMaterials = remember(snapshot.inventory) {
        snapshot.inventory.filter { it.deletedAt == null && !it.isPurchaseLot }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }
    val formulaById = remember(snapshot.formulas) { snapshot.formulas.associateBy { it.id } }

    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var addCostStr by remember(existing) { mutableStateOf((existing?.additionalCost ?: 0.0).toString()) }
    var adjFactorStr by remember(existing) { mutableStateOf((existing?.costAdjustmentFactor ?: 1.0).toString()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val ingredients = remember(existing) {
        mutableStateListOf<FormulaIngRowState>().apply {
            existing?.ingredients?.forEach { ing ->
                add(
                    FormulaIngRowState(
                        id = ing.id,
                        name = ing.name,
                        qtyStr = if (ing.quantity > 0.0) InputGuards.fmtNum(ing.quantity).replace(",", "") else "",
                        kind = ing.kind,
                        linkFormulaId = ing.formulaId,
                        costStr = if (ing.cost > 0.0) InputGuards.fmtNum(ing.cost).replace(",", "") else "",
                        custom = ing.custom
                    )
                )
            }
            if (isEmpty()) add(FormulaIngRowState("", "", "", null, null, "", false))
        }
    }

    val linkCandidates = remember(snapshot.formulas, existing) {
        snapshot.formulas.filter { cand ->
            existing == null || FormulaEngine.canLink(snapshot.formulas, existing.id, cand.id)
        }
    }

    val draftIngredients = ingredients.mapNotNull { row ->
        val qty = row.qtyStr.toDoubleOrNull() ?: 0.0
        if (row.name.isBlank() || qty <= 0.0) return@mapNotNull null
        FormulaIngredient(
            id = row.id.ifBlank { row.linkFormulaId ?: row.name.trim().lowercase(Locale.ROOT) },
            kind = row.kind,
            formulaId = row.linkFormulaId,
            name = row.name.trim(),
            cost = row.costStr.toDoubleOrNull() ?: 0.0,
            quantity = qty,
            custom = row.custom
        )
    }
    val previewFormula = FormulaConfig(
        id = existing?.id ?: "__draft__",
        name = name.ifBlank { "Draft" },
        ingredients = draftIngredients,
        additionalCost = addCostStr.toDoubleOrNull() ?: 0.0,
        costAdjustmentFactor = (adjFactorStr.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.01)
    )
    val previewBreakdown = FormulaEngine.describeFormula(previewFormula, snapshot.formulas, snapshot.inventory)

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
                        DecimalField("ADDITIONAL COST", addCostStr, { addCostStr = it }, "fe_addcost_input", Modifier.weight(1f))
                        DecimalField("COST ADJUST FACTOR", adjFactorStr, { adjFactorStr = it }, "fe_adj_input", Modifier.weight(1f))
                    }
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
                val autoUnitCost = if (row.kind == FormulaEngine.LINK_KIND && !row.linkFormulaId.isNullOrBlank()) {
                    val child = formulaById[row.linkFormulaId]
                    if (child != null) {
                        val cd = FormulaEngine.describeFormula(child, snapshot.formulas, snapshot.inventory)
                        if (cd.weight > 0.0) InputGuards.round2(cd.raw / cd.weight) else 0.0
                    } else 0.0
                } else {
                    activeMaterials.firstOrNull {
                        it.id == row.id || it.name.trim().equals(row.name.trim(), ignoreCase = true)
                    }?.cost ?: 0.0
                }

                SarimCard(contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1.35f)) {
                            SarimPillInput(
                                value = row.name,
                                onValueChange = {
                                    ingredients[idx] = row.copy(
                                        id = "",
                                        name = it,
                                        kind = null,
                                        linkFormulaId = null,
                                        custom = true
                                    )
                                },
                                label = if (row.kind == FormulaEngine.LINK_KIND) "SUB-FORMULA" else "MATERIAL",
                                placeholder = "Tap or type",
                                trailingIcon = {
                                    IconButton(onClick = { matMenuOpen = true }, modifier = Modifier.size(40.dp)) {
                                        Text("▾", style = MaterialTheme.typography.titleMedium, color = accents.emerald)
                                    }
                                }
                            )
                            DropdownMenu(expanded = matMenuOpen, onDismissRequest = { matMenuOpen = false }) {
                                activeMaterials.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text("${m.name.uppercase(Locale.ROOT)} (${InputGuards.fmtNum(m.cost)}/kg)") },
                                        onClick = {
                                            ingredients[idx] = row.copy(
                                                id = m.id,
                                                name = m.name,
                                                kind = null,
                                                linkFormulaId = null,
                                                costStr = "",
                                                custom = false
                                            )
                                            matMenuOpen = false
                                        }
                                    )
                                }
                                linkCandidates.forEach { sub ->
                                    DropdownMenuItem(
                                        text = { Text("${sub.name.uppercase(Locale.ROOT)} (Sub-Formula)", color = accents.emerald) },
                                        onClick = {
                                            ingredients[idx] = row.copy(
                                                id = sub.id,
                                                name = sub.name,
                                                kind = FormulaEngine.LINK_KIND,
                                                linkFormulaId = sub.id,
                                                costStr = "",
                                                custom = false
                                            )
                                            matMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(0.85f)) {
                            Text(
                                text = "COST / KG",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                                color = accents.textMuted,
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                            SarimPillInput(
                                value = row.costStr,
                                onValueChange = { raw ->
                                    if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                        ingredients[idx] = row.copy(costStr = raw)
                                    }
                                },
                                placeholder = InputGuards.fmtNum(autoUnitCost),
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
                            modifier = Modifier
                                .padding(top = 18.dp)
                                .size(48.dp)
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
                    onClick = { ingredients.add(FormulaIngRowState("", "", "", null, null, "", false)) },
                    shape = RoundedCornerShape(16.dp),
                    color = accents.inputFill,
                    border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("fe_add_ing_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+ Add Ingredient Row", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Leave Cost / kg blank to auto-price from live inventory or linked sub-formula cost.",
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
                        FormulaMetricRow("Unit Weight", InputGuards.formatKg(previewBreakdown.weight))
                        FormulaMetricRow("Raw Material Cost per Unit", InputGuards.formatMoney(previewBreakdown.raw, currency, masked))
                        FormulaMetricRow("Total Cost per Unit", InputGuards.formatMoney(previewBreakdown.perUnit, currency, masked), bold = true)
                        FormulaMetricRow("Cost per kg (Adjusted)", InputGuards.formatMoney(previewBreakdown.perKg, currency, masked))
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
                        onClick = { confirmDelete = true },
                        modifier = Modifier.weight(0.85f),
                        testTag = "fe_delete_btn"
                    )
                }
                SarimGradientButton(
                    text = "Save Formula",
                    onClick = {
                        vm.saveFormula(
                            existing = existing,
                            name = name,
                            additionalCost = addCostStr.toDoubleOrNull() ?: 0.0,
                            adjustment = adjFactorStr.toDoubleOrNull() ?: 1.0,
                            ingredients = draftIngredients
                        )
                        onBack()
                    },
                    enabled = name.isNotBlank() && draftIngredients.isNotEmpty(),
                    testTag = "fe_save_btn",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (confirmDelete && existing != null) {
        SarimConfirmDialog(
            title = "Delete Formula?",
            message = "Formula: ${existing.name}\n\nNote: Formulas linked to stores or used inside other formulas cannot be deleted until unlinked.",
            confirmText = "Delete",
            danger = true,
            onConfirm = {
                confirmDelete = false
                vm.deleteFormula(existing.id, onDeleted = onBack)
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
