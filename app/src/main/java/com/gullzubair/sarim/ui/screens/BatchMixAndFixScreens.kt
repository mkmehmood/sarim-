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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.SavedCalcRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.gullzubair.sarim.ui.components.ConfirmRequest
import com.gullzubair.sarim.ui.components.ConfirmRequestHost
import com.gullzubair.sarim.ui.components.deleteConfirmMessage
import com.gullzubair.sarim.ui.components.saveConfirmRequest
import com.gullzubair.sarim.ui.components.DialogTone

@Composable
fun BatchMixScreen(
    snapshot: WorkspaceSnapshot,
    initialFormulaId: String,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }
    val accents = LocalSarimAccents.current
    var selectedFormulaId by remember(snapshot.formulas, initialFormulaId) {
        mutableStateOf(
            snapshot.formulas.firstOrNull { it.id == initialFormulaId }?.id
                ?: snapshot.formulas.firstOrNull()?.id.orEmpty()
        )
    }
    val selectedFormula = remember(snapshot.formulas, selectedFormulaId) {
        snapshot.formulas.firstOrNull { it.id == selectedFormulaId } ?: snapshot.formulas.firstOrNull()
    }

    var useGrams by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("total") } // "total" | "units" | "ingredient"
    var targetStr by remember { mutableStateOf("") }
    var anchorId by remember { mutableStateOf("") }
    var mixName by remember { mutableStateOf("") }

    val flat = remember(selectedFormula, snapshot.formulas) {
        selectedFormula?.let { FormulaEngine.flattenFormula(it, snapshot.formulas) }
    }
    val flatIngredients = remember(flat) {
        flat?.ingredients?.filter { it.quantity > 0.0 }.orEmpty()
    }
    val resolvedAnchor = remember(flatIngredients, anchorId) {
        flatIngredients.firstOrNull { it.id == anchorId } ?: flatIngredients.firstOrNull()
    }

    val rawInputVal = targetStr.toDoubleOrNull() ?: 0.0
    val engineValue = if (mode != "units" && useGrams) rawInputVal / 1000.0 else rawInputVal
    val plan = remember(selectedFormula, snapshot.formulas, mode, engineValue, resolvedAnchor) {
        selectedFormula?.let {
            FormulaEngine.planBatch(
                formula = it,
                allFormulas = snapshot.formulas,
                mode = mode,
                value = engineValue,
                ingredientId = resolvedAnchor?.id
            )
        } ?: FormulaEngine.BatchMixPlan(0.0, 0.0, 0.0, emptyList())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "BATCHING", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (snapshot.formulas.isEmpty()) {
                item {
                    EmptyStateCard("No formulas", "Create a formula in Formula Store first before calculating batches.")
                }
            } else {
                item {
                    PickerField(
                        label = "FORMULA",
                        selectedLabel = selectedFormula?.name?.uppercase(Locale.ROOT) ?: "Select Formula",
                        items = snapshot.formulas,
                        itemLabel = { it.name.uppercase(Locale.ROOT) },
                        onSelect = {
                            selectedFormulaId = it.id
                            anchorId = ""
                        },
                        testTag = "mix_formula_picker"
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "1 unit = ${InputGuards.formatKg(plan.unitWeight)} • ${flatIngredients.size} ingredients",
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
                    if (mode == "ingredient" && flatIngredients.isNotEmpty()) {
                        PickerField(
                            label = "ANCHOR INGREDIENT",
                            selectedLabel = (resolvedAnchor?.name ?: "").uppercase(Locale.ROOT),
                            items = flatIngredients,
                            itemLabel = { it.name.uppercase(Locale.ROOT) },
                            onSelect = { anchorId = it.id },
                            testTag = "mix_anchor_picker"
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    val targetLabel = when (mode) {
                        "units" -> "Number of Units"
                        "ingredient" -> "Ingredient on Hand (${if (useGrams) "g" else "kg"})"
                        else -> "Total Batch Weight (${if (useGrams) "g" else "kg"})"
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
                            MixSummaryRow("Units", String.format(Locale.US, "%.3f", plan.units), accents.gold)
                            MixSummaryRow("Weight per Unit", InputGuards.formatKg(plan.unitWeight), accents.gold)
                            MixSummaryRow(
                                "Total Batch Weight",
                                if (useGrams) "${InputGuards.fmtNum(plan.totalWeight * 1000.0)} g" else InputGuards.formatKg(plan.totalWeight),
                                accents.emerald
                            )
                        }
                    }
                }

                if (plan.rows.isNotEmpty()) {
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
                                plan.rows.forEach { row ->
                                    val displayQty = if (useGrams) row.total * 1000.0 else row.total
                                    val unitLbl = if (useGrams) "g" else "kg"
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(row.name.uppercase(Locale.ROOT), style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = "${InputGuards.fmtNum(displayQty)} $unitLbl",
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
                                val now = System.currentTimeMillis()
                                val calc = SavedCalcRecord(
                                    id = "calc_$now",
                                    kind = "mix",
                                    name = mixName.trim().ifBlank { "${f.name} (${InputGuards.formatKg(plan.totalWeight)})" },
                                    mode = mode,
                                    value = engineValue,
                                    ingredientId = if (mode == "ingredient") resolvedAnchor?.id else null,
                                    createdAt = now,
                                    updatedAt = now
                                )
                                confirmReq = saveConfirmRequest(
                                    label = "Mix Calculation",
                                    details = listOf(
                                        "Formula" to f.name,
                                        "Name" to calc.name,
                                        "Total Weight" to InputGuards.formatKg(plan.totalWeight)
                                    ),
                                    gated = false,
                                    tag = "mix_confirm_save_btn"
                                ) {
                                    vm.saveFormulaCalc(f, calc)
                                    mixName = ""
                                }
                            }
                        },
                        enabled = selectedFormula != null && plan.totalWeight > 0.0,
                        testTag = "mix_save_btn"
                    )
                }

                val savedMixes = selectedFormula?.savedCalcs?.filter { it.kind == "mix" }.orEmpty()
                if (savedMixes.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "SAVED CALCULATIONS (${savedMixes.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                            color = accents.textMuted
                        )
                    }
                    items(savedMixes, key = { it.id }) { m ->
                        val dateLabel = remember(m.updatedAt, m.createdAt) {
                            val ts = if (m.updatedAt > 0L) m.updatedAt else m.createdAt
                            if (ts > 0L) SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(ts)) else ""
                        }
                        val loadedPlan = remember(selectedFormula, m, snapshot.formulas) {
                            selectedFormula?.let {
                                FormulaEngine.planBatch(it, snapshot.formulas, m.mode, m.value, m.ingredientId)
                            }
                        }
                        SarimCard(
                            modifier = Modifier.clickable {
                                mode = m.mode
                                anchorId = m.ingredientId ?: ""
                                val restoredVal = if (m.mode != "units" && useGrams) m.value * 1000.0 else m.value
                                targetStr = InputGuards.fmtNum(restoredVal).replace(",", "")
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
                                    Text(m.name.ifBlank { "Saved Mix" }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        listOfNotNull(
                                            m.mode.uppercase(Locale.ROOT),
                                            dateLabel.takeIf { it.isNotBlank() }
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accents.textMuted
                                    )
                                }
                                Text(
                                    InputGuards.formatKg(loadedPlan?.totalWeight ?: 0.0),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = accents.cyan
                                )
                                IconButton(
                                    onClick = {
                                        selectedFormula?.let { f ->
                                            confirmReq = ConfirmRequest(
                                                title = "Delete Saved Mix?",
                                                message = deleteConfirmMessage(listOf(
                                                    "Mix" to m.name.ifBlank { "Saved Mix" },
                                                    "Formula" to f.name
                                                ), recycleBin = false),
                                                confirmText = "Delete",
                                                danger = true,
                                                tag = "mix_confirm_delete_btn"
                                            ) { vm.deleteFormulaCalc(f, m.id) }
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete saved mix", tint = accents.danger, modifier = Modifier.size(18.dp))
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
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }
    val accents = LocalSarimAccents.current
    var selectedFormulaId by remember(snapshot.formulas, initialFormulaId) {
        mutableStateOf(
            snapshot.formulas.firstOrNull { it.id == initialFormulaId }?.id
                ?: snapshot.formulas.firstOrNull()?.id.orEmpty()
        )
    }
    val selectedFormula = remember(snapshot.formulas, selectedFormulaId) {
        snapshot.formulas.firstOrNull { it.id == selectedFormulaId } ?: snapshot.formulas.firstOrNull()
    }

    var useGrams by remember { mutableStateOf(true) }
    var originalStr by remember { mutableStateOf("50") }
    var usedLostStr by remember { mutableStateOf("25") }
    var customMixedExpanded by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }

    val actualInputs = remember(selectedFormulaId) { mutableStateMapOf<String, String>() }
    var manualId by remember(selectedFormulaId) { mutableStateOf<String?>(null) }
    var manualAddStr by remember(selectedFormulaId) { mutableStateOf<String?>(null) }
    var manualTotalStr by remember(selectedFormulaId) { mutableStateOf<String?>(null) }

    val unitTag = if (useGrams) "g" else "kg"
    val factor = if (useGrams) 1000.0 else 1.0

    val originalKg = (originalStr.toDoubleOrNull() ?: 0.0) / factor
    val usedKg = (usedLostStr.toDoubleOrNull() ?: 0.0) / factor
    val actualKgMap = actualInputs.mapValues { (_, raw) -> (raw.toDoubleOrNull() ?: 0.0) / factor }
    val manualAddKg = manualAddStr?.toDoubleOrNull()?.let { it / factor }
    val manualTotalKg = manualTotalStr?.toDoubleOrNull()?.let { it / factor }

    val fixPlan = remember(
        selectedFormula,
        snapshot.formulas,
        originalKg,
        usedKg,
        actualKgMap.toMap(),
        manualId,
        manualAddKg,
        manualTotalKg
    ) {
        selectedFormula?.let {
            FormulaEngine.planCorrection(
                formula = it,
                allFormulas = snapshot.formulas,
                original = originalKg,
                used = usedKg,
                actual = actualKgMap,
                manualId = manualId,
                manualAdd = manualAddKg,
                manualTotal = manualTotalKg
            )
        } ?: FormulaEngine.BatchFixPlan(ok = false, error = "empty")
    }

    val remainingDisplay = fixPlan.remaining * factor
    val newTotalDisplay = fixPlan.requiredTotal * factor
    val addTotalDisplay = fixPlan.addTotal * factor

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "BATCH CORRECTION", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (snapshot.formulas.isEmpty()) {
                item {
                    EmptyStateCard("No formulas", "Create a formula in Formula Store first before running batch correction.")
                }
            } else {
                item {
                    PickerField(
                        label = "FORMULA",
                        selectedLabel = selectedFormula?.name?.uppercase(Locale.ROOT) ?: "Select Formula",
                        items = snapshot.formulas,
                        itemLabel = { it.name.uppercase(Locale.ROOT) },
                        onSelect = {
                            selectedFormulaId = it.id
                            manualId = null
                            manualAddStr = null
                            manualTotalStr = null
                        },
                        testTag = "fix_formula_picker"
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MixPillToggle("g", selected = useGrams) {
                            if (!useGrams) {
                                useGrams = true
                                originalStr = InputGuards.fmtNum((originalStr.toDoubleOrNull() ?: 0.0) * 1000.0).replace(",", "")
                                usedLostStr = InputGuards.fmtNum((usedLostStr.toDoubleOrNull() ?: 0.0) * 1000.0).replace(",", "")
                                manualAddStr = null
                                manualTotalStr = null
                            }
                        }
                        MixPillToggle("kg", selected = !useGrams) {
                            if (useGrams) {
                                useGrams = false
                                originalStr = InputGuards.fmtNum((originalStr.toDoubleOrNull() ?: 0.0) / 1000.0).replace(",", "")
                                usedLostStr = InputGuards.fmtNum((usedLostStr.toDoubleOrNull() ?: 0.0) / 1000.0).replace(",", "")
                                manualAddStr = null
                                manualTotalStr = null
                            }
                        }
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
                                label = "ORIGINAL BATCH (${unitTag.uppercase(Locale.ROOT)})",
                                value = originalStr,
                                onValueChange = { originalStr = it },
                                testTag = "fix_orig_input",
                                modifier = Modifier.weight(1f)
                            )
                            DecimalField(
                                label = "USED / LOST (${unitTag.uppercase(Locale.ROOT)})",
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
                                text = "${InputGuards.fmtNum(remainingDisplay)} $unitTag",
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
                            Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Enter actual weights present in the batch if proportions drifted; leave blank for standard formula ratio.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted
                                )
                                fixPlan.rows.forEach { row ->
                                    SarimPillInput(
                                        value = actualInputs[row.id] ?: "",
                                        onValueChange = { raw ->
                                            if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d{0,3}$"))) {
                                                actualInputs[row.id] = raw
                                            }
                                        },
                                        label = "${row.name.uppercase(Locale.ROOT)} ACTUAL ($unitTag)",
                                        placeholder = InputGuards.fmtNum(row.present * factor)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    SarimCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Required Additions",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = accents.emerald
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text("New total", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                Text(
                                    text = "${InputGuards.fmtNum(newTotalDisplay)} $unitTag",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = accents.danger
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (!fixPlan.ok) {
                                if (fixPlan.error == "nothing-left") "Nothing remaining to correct (Used >= Original)."
                                else "Enter valid batch quantities above."
                            } else {
                                "Type an Add amount for any ingredient — all other additions scale to restore exact formula proportions."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (fixPlan.ok) accents.textMuted else accents.danger
                        )
                        Spacer(Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            fixPlan.rows.forEach { row ->
                                val addDisplay = if (manualId == row.id && manualAddStr != null) {
                                    manualAddStr!!
                                } else {
                                    InputGuards.fmtNum(row.add * factor).replace(",", "")
                                }
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
                                                text = row.name.uppercase(Locale.ROOT),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text("ADD", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                                Box(modifier = Modifier.width(96.dp)) {
                                                    SarimPillInput(
                                                        value = addDisplay,
                                                        onValueChange = { raw ->
                                                            if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                                                manualId = row.id
                                                                manualAddStr = raw
                                                                manualTotalStr = null
                                                            }
                                                        },
                                                        placeholder = "0"
                                                    )
                                                }
                                                Text(unitTag.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                text = "In remaining ${InputGuards.fmtNum(row.present * factor)} $unitTag",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = accents.textMuted
                                            )
                                            Text(
                                                text = "Target ${InputGuards.fmtNum(row.target * factor)} $unitTag",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (fixPlan.ok) {
                            Spacer(Modifier.height(12.dp))
                            MixSummaryRow("Total in Remaining", "${InputGuards.fmtNum(remainingDisplay)} $unitTag", MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(6.dp))
                            MixSummaryRow("Total Required", "${InputGuards.fmtNum(newTotalDisplay)} $unitTag", MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(10.dp))
                            val totalAddValue = manualTotalStr ?: InputGuards.fmtNum(addTotalDisplay).replace(",", "")
                            DecimalField(
                                label = "Total to Add ($unitTag)",
                                value = totalAddValue,
                                onValueChange = { raw ->
                                    manualId = null
                                    manualAddStr = null
                                    manualTotalStr = raw
                                },
                                testTag = "fix_total_add_input"
                            )
                        }
                    }
                }

                item {
                    SarimPillInput(
                        value = saveName,
                        onValueChange = { saveName = it.take(80) },
                        label = "CORRECTION LABEL (OPTIONAL)",
                        placeholder = "e.g. Afternoon Fix",
                        testTag = "fix_save_name_input"
                    )
                    Spacer(Modifier.height(10.dp))
                    SarimGradientButton(
                        text = "Save Correction",
                        onClick = {
                            selectedFormula?.let { f ->
                                val now = System.currentTimeMillis()
                                val calc = SavedCalcRecord(
                                    id = "fix_$now",
                                    kind = "fix",
                                    name = saveName.trim().ifBlank { "${f.name} Fix" },
                                    original = originalKg,
                                    used = usedKg,
                                    actual = actualKgMap.filterValues { it > 0.0 },
                                    manualId = manualId,
                                    manualAdd = manualAddKg,
                                    manualTotal = manualTotalKg,
                                    createdAt = now,
                                    updatedAt = now
                                )
                                confirmReq = saveConfirmRequest(
                                    label = "Correction",
                                    details = listOf(
                                        "Formula" to f.name,
                                        "Name" to calc.name
                                    ),
                                    gated = false,
                                    tag = "fix_confirm_save_btn"
                                ) {
                                    vm.saveFormulaCalc(f, calc)
                                    saveName = ""
                                }
                            }
                        },
                        enabled = selectedFormula != null && fixPlan.ok,
                        testTag = "fix_save_btn"
                    )
                }

                val savedFixes = selectedFormula?.savedCalcs?.filter { it.kind == "fix" }.orEmpty()
                if (savedFixes.isNotEmpty()) {
                    item {
                        Text(
                            text = "SAVED CORRECTIONS (${savedFixes.size})",
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                            color = accents.textMuted
                        )
                    }
                    items(savedFixes, key = { it.id }) { fx ->
                        SarimCard(
                            modifier = Modifier.clickable {
                                originalStr = InputGuards.fmtNum(fx.original * factor).replace(",", "")
                                usedLostStr = InputGuards.fmtNum(fx.used * factor).replace(",", "")
                                actualInputs.clear()
                                fx.actual.forEach { (k, v) ->
                                    actualInputs[k] = InputGuards.fmtNum(v * factor).replace(",", "")
                                }
                                manualId = fx.manualId
                                manualAddStr = fx.manualAdd?.let { InputGuards.fmtNum(it * factor).replace(",", "") }
                                manualTotalStr = fx.manualTotal?.let { InputGuards.fmtNum(it * factor).replace(",", "") }
                            },
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(fx.name.ifBlank { "Saved Fix" }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        "Orig ${InputGuards.fmtNum(fx.original * factor)} $unitTag • Used ${InputGuards.fmtNum(fx.used * factor)} $unitTag",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accents.textMuted
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        selectedFormula?.let { f ->
                                            confirmReq = ConfirmRequest(
                                                title = "Delete Saved Correction?",
                                                message = deleteConfirmMessage(listOf(
                                                    "Correction" to fx.name.ifBlank { "Saved Fix" },
                                                    "Formula" to f.name
                                                ), recycleBin = false),
                                                confirmText = "Delete",
                                                danger = true,
                                                tag = "fix_confirm_delete_btn"
                                            ) { vm.deleteFormulaCalc(f, fx.id) }
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete saved correction", tint = accents.danger, modifier = Modifier.size(18.dp))
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
private fun MixSummaryRow(label: String, value: String, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = valueColor)
    }
}
