package com.gullzubair.sarim.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

@Composable
fun BatchMixScreen(
    snapshot: WorkspaceSnapshot,
    initialFormulaId: String,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    val accents = LocalSarimAccents.current
    var selectedFormula by remember(snapshot.formulas, initialFormulaId) {
        mutableStateOf(snapshot.formulas.firstOrNull { it.id == initialFormulaId } ?: snapshot.formulas.firstOrNull())
    }
    var useGrams by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("units") } // "total" | "units" | "ingredient"
    var targetStr by remember { mutableStateOf("5") }
    var anchorName by remember { mutableStateOf("") }
    var mixName by remember { mutableStateOf("") }

    val flatBase = remember(selectedFormula, snapshot) {
        selectedFormula?.let { FormulaEngine.flattenFormula(it, snapshot.formulas, snapshot.inventory, 1.0) } ?: emptyList()
    }
    val baseUnitKg = remember(flatBase) { flatBase.sumOf { it.qtyKg } }
    val lines = remember(selectedFormula, mode, targetStr, anchorName, useGrams, snapshot) {
        selectedFormula?.let {
            FormulaEngine.calculateBatchMix(
                formula = it,
                allFormulas = snapshot.formulas,
                inventory = snapshot.inventory,
                mode = mode,
                targetValue = targetStr.toDoubleOrNull() ?: 0.0,
                anchorIngredientName = anchorName.ifBlank { flatBase.firstOrNull()?.name ?: "" },
                useGrams = useGrams
            )
        } ?: emptyList()
    }
    val totalBatchKg = remember(lines) { InputGuards.round3(lines.sumOf { it.qtyKg }) }
    val computedUnits = remember(totalBatchKg, baseUnitKg) {
        if (baseUnitKg > 0.0) InputGuards.round3(totalBatchKg / baseUnitKg) else 0.0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "BATCHING", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PickerField(
                    label = "FORMULA",
                    selectedLabel = selectedFormula?.name?.uppercase() ?: "Select Formula",
                    items = snapshot.formulas,
                    itemLabel = { it.name.uppercase() },
                    onSelect = { selectedFormula = it },
                    testTag = "mix_formula_picker"
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "1 unit = ${InputGuards.formatKg(baseUnitKg)} • ${flatBase.size} ingredients",
                    style = MaterialTheme.typography.bodySmall,
                    color = accents.textMuted
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MixPillToggle("g", selected = useGrams) { useGrams = true }
                    MixPillToggle("kg", selected = !useGrams) { useGrams = false }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MixPillToggle("Weight", selected = mode == "total") { mode = "total" }
                    MixPillToggle("Units", selected = mode == "units") { mode = "units" }
                    MixPillToggle("Ingredient", selected = mode == "ingredient") { mode = "ingredient" }
                }
            }

            item {
                if (mode == "ingredient" && flatBase.isNotEmpty()) {
                    PickerField(
                        label = "ANCHOR INGREDIENT",
                        selectedLabel = anchorName.ifBlank { flatBase.first().name }.uppercase(),
                        items = flatBase.map { it.name },
                        itemLabel = { it.uppercase() },
                        onSelect = { anchorName = it },
                        testTag = "mix_anchor_picker"
                    )
                    Spacer(Modifier.height(10.dp))
                }
                val targetLabel = when (mode) {
                    "units" -> "NUMBER OF UNITS"
                    "ingredient" -> "ANCHOR QTY (${if (useGrams) "G" else "KG"})"
                    else -> "TARGET WEIGHT (${if (useGrams) "G" else "KG"})"
                }
                DecimalField(targetLabel, targetStr, { targetStr = it }, "mix_target_input")
            }

            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = accents.softPillFill,
                    border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MixSummaryRow("Units", String.format("%.3f", computedUnits), accents.gold)
                        MixSummaryRow("Weight per Unit", InputGuards.formatKg(baseUnitKg), accents.gold)
                        MixSummaryRow(
                            "Total Batch Weight",
                            if (useGrams) "${InputGuards.formatNum(totalBatchKg * 1000.0)} g" else InputGuards.formatKg(totalBatchKg),
                            accents.emerald
                        )
                    }
                }
            }

            if (lines.isNotEmpty()) {
                item {
                    Text(
                        text = "REQUIRED INGREDIENTS",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                        color = accents.textMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            lines.forEach { line ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(line.ingredientName.uppercase(), style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "${InputGuards.formatNum(line.qtyDisplay)} ${line.unitLabel}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = accents.gold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SarimPillInput(
                    value = mixName,
                    onValueChange = { mixName = it.take(80) },
                    label = "NAME (OPTIONAL)",
                    placeholder = "e.g. Morning batch",
                    testTag = "mix_save_name_input"
                )
                Spacer(Modifier.height(10.dp))
                SarimGradientButton(
                    text = "Save Calculation",
                    onClick = {
                        selectedFormula?.let { f ->
                            vm.saveFormulaSavedMix(
                                formula = f,
                                title = mixName,
                                mode = mode,
                                target = targetStr.toDoubleOrNull() ?: 0.0,
                                useGrams = useGrams,
                                totalKg = totalBatchKg
                            )
                            mixName = ""
                        }
                    },
                    enabled = selectedFormula != null && totalBatchKg > 0.0,
                    testTag = "mix_save_btn"
                )
            }

            val savedMixes = selectedFormula?.savedMixes.orEmpty()
            if (savedMixes.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "SAVED CALCULATIONS (${savedMixes.size})",
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                        color = accents.textMuted
                    )
                }
                items(savedMixes) { m ->
                    val title = m["title"]?.toString() ?: "Batch"
                    val dateStr = m["date"]?.toString() ?: ""
                    val totKg = (m["totalKg"] as? Number)?.toDouble() ?: 0.0
                    val savedMode = m["mode"]?.toString() ?: "units"
                    val savedTarget = (m["target"] as? Number)?.toDouble() ?: 1.0
                    val savedGrams = m["useGrams"] as? Boolean ?: false

                    SarimCard(
                        modifier = Modifier.clickable {
                            mode = savedMode
                            useGrams = savedGrams
                            targetStr = InputGuards.formatNum(savedTarget).replace(",", "")
                        },
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accents.softPillFill),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text(dateStr, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(InputGuards.formatKg(totKg), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.cyan)
                                Surface(color = accents.softPillFill, shape = RoundedCornerShape(8.dp)) {
                                    Text(
                                        "BATCH",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accents.cyan,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BatchFixScreen(
    snapshot: WorkspaceSnapshot,
    initialFormulaId: String,
    onBack: () -> Unit
) {
    val accents = LocalSarimAccents.current
    var selectedFormula by remember(snapshot.formulas, initialFormulaId) {
        mutableStateOf(snapshot.formulas.firstOrNull { it.id == initialFormulaId } ?: snapshot.formulas.firstOrNull())
    }
    var useGrams by remember { mutableStateOf(true) }
    var originalStr by remember { mutableStateOf("50") }
    var usedLostStr by remember { mutableStateOf("25") }
    var customMixedExpanded by remember { mutableStateOf(false) }

    val flat = remember(selectedFormula, snapshot) {
        selectedFormula?.let { FormulaEngine.flattenFormula(it, snapshot.formulas, snapshot.inventory, 1.0) } ?: emptyList()
    }
    val totalBaseKg = remember(flat) { flat.sumOf { it.qtyKg }.coerceAtLeast(0.001) }

    val originalDisplay = originalStr.toDoubleOrNull() ?: 0.0
    val usedLostDisplay = usedLostStr.toDoubleOrNull() ?: 0.0
    val remainingDisplay = (originalDisplay - usedLostDisplay).coerceAtLeast(0.0)
    val unitTag = if (useGrams) "g" else "kg"

    // Track per-ingredient anchor or custom remaining ratio; editing any "Add" field scales all other additions dynamically
    val addInputs = remember(selectedFormula, useGrams) { mutableStateMapOf<String, String>() }
    var scaleMultiplier by remember(selectedFormula, originalStr, usedLostStr, useGrams) { mutableStateOf(0.0) }

    val newTotalDisplay = InputGuards.round2(
        remainingDisplay + flat.sumOf { line ->
            val prop = line.qtyKg / totalBaseKg
            val inRemaining = remainingDisplay * prop
            val targetBase = (originalDisplay * prop) + (scaleMultiplier * prop)
            (targetBase - inRemaining).coerceAtLeast(0.0)
        }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "BATCH CORRECTION", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PickerField(
                    label = "FORMULA",
                    selectedLabel = selectedFormula?.name?.uppercase() ?: "Select Formula",
                    items = snapshot.formulas,
                    itemLabel = { it.name.uppercase() },
                    onSelect = { selectedFormula = it },
                    testTag = "fix_formula_picker"
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MixPillToggle("g", selected = useGrams) { useGrams = true }
                    MixPillToggle("kg", selected = !useGrams) { useGrams = false }
                }
            }

            item {
                SarimCard {
                    Text(
                        text = "Batch Correction",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = accents.emerald
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DecimalField(
                            label = "ORIGINAL BATCH (${unitTag.uppercase()})",
                            value = originalStr,
                            onValueChange = { originalStr = it },
                            testTag = "fix_orig_input",
                            modifier = Modifier.weight(1f)
                        )
                        DecimalField(
                            label = "USED / LOST (${unitTag.uppercase()})",
                            value = usedLostStr,
                            onValueChange = { usedLostStr = it },
                            testTag = "fix_used_input",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Remaining Batch", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${InputGuards.formatNum(remainingDisplay)} $unitTag",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = accents.danger
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { customMixedExpanded = !customMixedExpanded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (customMixedExpanded) "▾ Mixed differently than the formula?" else "▸ Mixed differently than the formula?",
                            style = MaterialTheme.typography.labelLarge,
                            color = accents.emerald
                        )
                    }
                    AnimatedVisibility(visible = customMixedExpanded) {
                        Column(modifier = Modifier.padding(top = 6.dp)) {
                            Text(
                                text = "Use the ADD fields below to boost any single ingredient — the app recalculates all other ingredients to restore the exact formula ratio.",
                                style = MaterialTheme.typography.bodySmall,
                                color = accents.textMuted
                            )
                        }
                    }
                }
            }

            item {
                SarimCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Required Additions",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = accents.emerald
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text("New total", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Text(
                                text = "${InputGuards.formatNum(newTotalDisplay)} $unitTag",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = accents.danger
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Type a new amount in any Add box — all other ingredients update automatically to keep the formula ratio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = accents.textMuted
                    )
                    Spacer(Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        flat.forEach { line ->
                            val prop = line.qtyKg / totalBaseKg
                            val inRemaining = InputGuards.round2(remainingDisplay * prop)
                            val requiredTarget = InputGuards.round2(
                                ((originalDisplay * prop) + (scaleMultiplier * prop) - inRemaining).coerceAtLeast(inRemaining)
                            )
                            val autoAddVal = InputGuards.round2((scaleMultiplier * prop).coerceAtLeast(0.0))
                            val displayAdd = addInputs[line.name] ?: if (autoAddVal > 0.0) InputGuards.formatNum(autoAddVal).replace(",", "") else "0"

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = accents.inputFill,
                                border = BorderStroke(1.dp, accents.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = line.name.uppercase(),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("ADD", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                            Box(modifier = Modifier.width(96.dp)) {
                                                SarimPillInput(
                                                    value = displayAdd,
                                                    onValueChange = { raw ->
                                                        if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                                            addInputs.clear()
                                                            addInputs[line.name] = raw
                                                            val typed = raw.toDoubleOrNull() ?: 0.0
                                                            scaleMultiplier = if (prop > 0.0) typed / prop else 0.0
                                                        }
                                                    },
                                                    placeholder = "0"
                                                )
                                            }
                                            Text(unitTag.uppercase(), style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            text = "In remaining ${InputGuards.formatNum(inRemaining)} $unitTag",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accents.textMuted
                                        )
                                        Text(
                                            text = "Required ${InputGuards.formatNum(requiredTarget)} $unitTag",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MixPillToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    val accents = LocalSarimAccents.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) accents.softPillFill else accents.inputFill,
        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium),
                color = if (selected) accents.emerald else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MixSummaryRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = valueColor)
    }
}
