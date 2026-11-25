package com.gullzubair.sarim.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel

@Composable
fun StoreManagerScreen(snapshot: WorkspaceSnapshot, vm: SarimViewModel) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney

    var editId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var selectedFormulaId by remember { mutableStateOf("") }
    var rateStr by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SarimCard {
                Text(if (editId == null) "Add New Store" else "Edit Store", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Store Name") }, modifier = Modifier.fillMaxWidth().testTag("sm_name_input"))
                Spacer(Modifier.height(8.dp))
                val formulaLabel = snapshot.formulas.firstOrNull { it.id == selectedFormulaId }?.name ?: "None (Direct)"
                PickerField(
                    label = "Linked Formula",
                    selectedLabel = formulaLabel,
                    items = listOf(null) + snapshot.formulas,
                    itemLabel = { it?.name ?: "None (Direct)" },
                    onSelect = { selectedFormulaId = it?.id ?: "" },
                    testTag = "sm_formula_picker"
                )
                Spacer(Modifier.height(8.dp))
                DecimalField("Default Sale Price / kg", rateStr, { rateStr = it }, "sm_rate_input")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Active Store", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        vm.saveStore(editId, name, selectedFormulaId, rateStr.toDoubleOrNull() ?: 0.0, active)
                        editId = null; name = ""; rateStr = ""; selectedFormulaId = ""; active = true
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("sm_save_btn")
                ) { Text("Save Store") }
            }
        }
        items(snapshot.stores, key = { it.id }) { s ->
            SarimCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${s.name} ${if (!s.active) "(Inactive)" else ""}", style = MaterialTheme.typography.titleMedium)
                        Text("Rate ${InputGuards.formatMoney(s.salePrice, currency, masked)}/kg", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    }
                    IconButton(onClick = {
                        editId = s.id; name = s.name; selectedFormulaId = s.formulaId; rateStr = s.salePrice.toString(); active = s.active
                    }) { Icon(Icons.Default.Edit, contentDescription = "Edit Store") }
                }
            }
        }
    }
}

@Composable
fun FormulaStoreScreen(
    snapshot: WorkspaceSnapshot,
    onEditFormula: (String) -> Unit,
    onOpenMix: (String) -> Unit,
    onOpenFix: (String) -> Unit
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = { onEditFormula("") },
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("formula_create_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Create Formula")
            }
        }
        if (snapshot.formulas.isEmpty()) {
            item { EmptyStateCard("No formulas defined", "Create your first recipe to enable factory batches and batch mixing.") }
        } else {
            items(snapshot.formulas, key = { it.id }) { f ->
                val batchCost = FormulaEngine.calculateBatchCost(f, snapshot.formulas, snapshot.inventory, 1.0)
                SarimCard {
                    Text(f.name, style = MaterialTheme.typography.titleMedium)
                    Text(FormulaEngine.describeFormula(f, snapshot.formulas), style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    Text("Est. Batch Cost: ${InputGuards.formatMoney(batchCost, currency, masked)}", style = MaterialTheme.typography.labelMedium, color = accents.emerald)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onOpenMix(f.id) }, modifier = Modifier.weight(1f).testTag("formula_mix_${f.id}")) { Text("Mix") }
                        OutlinedButton(onClick = { onOpenFix(f.id) }, modifier = Modifier.weight(1f).testTag("formula_fix_${f.id}")) { Text("Fix") }
                        Button(onClick = { onEditFormula(f.id) }, modifier = Modifier.weight(1f).testTag("formula_edit_${f.id}")) { Text("Edit") }
                    }
                }
            }
        }
    }
}

@Composable
fun FormulaEditScreen(snapshot: WorkspaceSnapshot, formulaId: String, vm: SarimViewModel, onBack: () -> Unit) {
    val existing = remember(snapshot.formulas, formulaId) { snapshot.formulas.firstOrNull { it.id == formulaId } }
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var unitsStr by remember(existing) { mutableStateOf((existing?.unitsPerBatch ?: 1.0).toString()) }
    var addCostStr by remember(existing) { mutableStateOf((existing?.additionalCost ?: 0.0).toString()) }
    var adjStr by remember(existing) { mutableStateOf((existing?.costAdjustment ?: 0.0).toString()) }

    val ingredients = remember(existing) {
        mutableStateListOf<Triple<String, String, String>>().apply {
            existing?.ingredients?.forEach { m ->
                add(Triple(m["name"] as? String ?: "", ((m["qtyKg"] as? Number)?.toDouble() ?: 0.0).toString(), m["linkFormulaId"] as? String ?: ""))
            }
            if (isEmpty()) add(Triple("", "", ""))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SarimCard {
                Text(if (existing == null) "New Formula" else "Edit ${existing.name}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Formula Name") }, modifier = Modifier.fillMaxWidth().testTag("fe_name_input"))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField("Units/Batch", unitsStr, { unitsStr = it }, "fe_units_input", Modifier.weight(1f))
                    DecimalField("Extra Cost", addCostStr, { addCostStr = it }, "fe_addcost_input", Modifier.weight(1f))
                    DecimalField("Cost Adj.", adjStr, { adjStr = it }, "fe_adj_input", Modifier.weight(1f))
                }
            }
        }
        items(ingredients.size) { idx ->
            val row = ingredients[idx]
            SarimCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = row.first,
                        onValueChange = { ingredients[idx] = row.copy(first = it) },
                        label = { Text("Ingredient") },
                        modifier = Modifier.weight(1.4f)
                    )
                    DecimalField("kg", row.second, { ingredients[idx] = row.copy(second = it) }, "fe_ing_qty_$idx", Modifier.weight(1f))
                    IconButton(onClick = { if (ingredients.size > 1) ingredients.removeAt(idx) }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove row")
                    }
                }
                val linkCandidates = snapshot.formulas.filter { cand ->
                    existing == null || FormulaEngine.canLink(snapshot.formulas, existing.id, cand.id)
                }
                if (linkCandidates.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    val linkName = snapshot.formulas.firstOrNull { it.id == row.third }?.name ?: "Raw Material (Direct)"
                    PickerField(
                        label = "Optional Sub-Formula Link",
                        selectedLabel = linkName,
                        items = listOf(null) + linkCandidates,
                        itemLabel = { it?.name ?: "Raw Material (Direct)" },
                        onSelect = { f ->
                            ingredients[idx] = row.copy(
                                first = f?.name ?: row.first,
                                third = f?.id ?: ""
                            )
                        },
                        testTag = "fe_link_$idx"
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { ingredients.add(Triple("", "", "")) }, modifier = Modifier.weight(1f).testTag("fe_add_ing_btn")) {
                    Text("+ Ingredient")
                }
                Button(
                    onClick = {
                        val list = ingredients.mapNotNull { (n, q, l) ->
                            val qty = q.toDoubleOrNull() ?: 0.0
                            if (n.isNotBlank() && qty > 0) mapOf("name" to n.trim(), "qtyKg" to qty, "linkFormulaId" to l) else null
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
                    modifier = Modifier.weight(1f).testTag("fe_save_btn")
                ) { Text("Save Formula") }
            }
        }
    }
}

@Composable
fun BatchMixScreen(snapshot: WorkspaceSnapshot, initialFormulaId: String) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney
    var selectedFormula by remember(snapshot.formulas, initialFormulaId) {
        mutableStateOf(snapshot.formulas.firstOrNull { it.id == initialFormulaId } ?: snapshot.formulas.firstOrNull())
    }
    var mode by remember { mutableStateOf("total") }
    var targetStr by remember { mutableStateOf("100") }
    var anchorName by remember { mutableStateOf("") }
    var useGrams by remember { mutableStateOf(false) }

    val lines = remember(selectedFormula, mode, targetStr, anchorName, useGrams, snapshot) {
        selectedFormula?.let {
            FormulaEngine.calculateBatchMix(it, snapshot.formulas, snapshot.inventory, mode, targetStr.toDoubleOrNull() ?: 0.0, anchorName, useGrams)
        } ?: emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SarimCard {
                Text("Batch Mix Calculator", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                PickerField("Formula", selectedFormula?.name ?: "Select Formula", snapshot.formulas, { it.name }, { selectedFormula = it }, "mix_formula_picker")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == "total", onClick = { mode = "total" }, label = { Text("Total Wt") })
                    FilterChip(selected = mode == "units", onClick = { mode = "units" }, label = { Text("Units") })
                    FilterChip(selected = mode == "ingredient", onClick = { mode = "ingredient" }, label = { Text("Anchor") })
                    FilterChip(selected = useGrams, onClick = { useGrams = !useGrams }, label = { Text(if (useGrams) "g" else "kg") })
                }
                if (mode == "ingredient" && selectedFormula != null) {
                    val flat = FormulaEngine.flattenFormula(selectedFormula!!, snapshot.formulas, snapshot.inventory)
                    PickerField("Anchor Ingredient", anchorName, flat.map { it.name }, { it }, { anchorName = it }, "mix_anchor_picker")
                }
                Spacer(Modifier.height(8.dp))
                DecimalField(if (mode == "units") "Target Units" else "Target Weight (${if (useGrams) "g" else "kg"})", targetStr, { targetStr = it }, "mix_target_input")
            }
        }
        items(lines, key = { it.ingredientName }) { line ->
            SarimCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(line.ingredientName, style = MaterialTheme.typography.titleSmall)
                    Text("${line.qtyDisplay} ${line.unitLabel}", color = accents.emerald, style = MaterialTheme.typography.titleSmall)
                }
                Text("Est. cost ${InputGuards.formatMoney(line.estCost, currency, masked)}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            }
        }
    }
}

@Composable
fun BatchFixScreen(snapshot: WorkspaceSnapshot, initialFormulaId: String) {
    val accents = LocalSarimAccents.current
    var selectedFormula by remember(snapshot.formulas, initialFormulaId) {
        mutableStateOf(snapshot.formulas.firstOrNull { it.id == initialFormulaId } ?: snapshot.formulas.firstOrNull())
    }
    var actualIngredient by remember { mutableStateOf("") }
    var actualAddedStr by remember { mutableStateOf("") }

    val flat = remember(selectedFormula, snapshot) {
        selectedFormula?.let { FormulaEngine.flattenFormula(it, snapshot.formulas, snapshot.inventory, 1.0) } ?: emptyList()
    }
    val anchor = flat.firstOrNull { it.name.equals(actualIngredient, true) } ?: flat.firstOrNull()
    val actualVal = actualAddedStr.toDoubleOrNull() ?: 0.0
    val scale = if (anchor != null && anchor.qtyKg > 0 && actualVal > 0) actualVal / anchor.qtyKg else 1.0

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SarimCard {
                Text("Batch Mistake Correction", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                PickerField("Formula", selectedFormula?.name ?: "Select Formula", snapshot.formulas, { it.name }, { selectedFormula = it }, "fix_formula_picker")
                Spacer(Modifier.height(8.dp))
                PickerField("Over-Added Ingredient", anchor?.name ?: "Select", flat.map { it.name }, { it }, { actualIngredient = it }, "fix_ing_picker")
                Spacer(Modifier.height(8.dp))
                DecimalField("Actual Added Qty (kg)", actualAddedStr, { actualAddedStr = it }, "fix_actual_input", suffix = "kg")
            }
        }
        items(flat, key = { it.name }) { line ->
            val targetQty = InputGuards.round3(line.qtyKg * scale)
            val addMore = InputGuards.round3((targetQty - line.qtyKg).coerceAtLeast(0.0))
            SarimCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(line.name, style = MaterialTheme.typography.titleSmall)
                    Text("Target ${InputGuards.formatKg(targetQty)}", style = MaterialTheme.typography.labelLarge, color = accents.cyan)
                }
                Text("Base ${InputGuards.formatKg(line.qtyKg)} -> Add ${InputGuards.formatKg(addMore)} more", style = MaterialTheme.typography.bodySmall, color = accents.emerald)
            }
        }
    }
}
