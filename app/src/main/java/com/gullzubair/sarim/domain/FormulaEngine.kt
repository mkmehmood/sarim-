package com.gullzubair.sarim.domain

import com.gullzubair.sarim.data.model.FormulaConfig
import com.gullzubair.sarim.data.model.InventoryItem

data class FlattenedIngredient(
    val name: String,
    val qtyKg: Double,
    val inventoryItemId: String = "",
    val unitCostPerKg: Double = 0.0
)

data class BatchMixLine(
    val ingredientName: String,
    val qtyDisplay: Double,
    val qtyKg: Double,
    val unitLabel: String,
    val estCost: Double
)

object FormulaEngine {

    fun canLink(formulas: List<FormulaConfig>, parentId: String, candidateChildId: String): Boolean {
        if (parentId == candidateChildId || candidateChildId.isBlank()) return false
        val byId = formulas.associateBy { it.id }
        val visited = mutableSetOf(parentId)
        val stack = ArrayDeque<String>()
        stack.add(candidateChildId)
        while (stack.isNotEmpty()) {
            val curr = stack.removeFirst()
            if (curr in visited) return false
            visited.add(curr)
            val formula = byId[curr] ?: continue
            formula.ingredients.forEach { ing ->
                val linkId = ing["linkFormulaId"] as? String ?: ""
                if (linkId.isNotBlank()) stack.add(linkId)
            }
        }
        return true
    }

    fun flattenFormula(
        formula: FormulaConfig,
        allFormulas: List<FormulaConfig>,
        inventory: List<InventoryItem>,
        multiplier: Double = 1.0,
        visited: Set<String> = emptySet()
    ): List<FlattenedIngredient> {
        if (formula.id in visited) return emptyList()
        val nextVisited = visited + formula.id
        val byId = allFormulas.associateBy { it.id }
        val invByName = inventory.filter { !it.recycled }.associateBy { it.name.trim().lowercase() }
        val aggregated = linkedMapOf<String, FlattenedIngredient>()

        formula.ingredients.forEach { ing ->
            val name = (ing["name"] as? String ?: "").trim()
            val qtyKg = ((ing["qtyKg"] as? Number)?.toDouble() ?: 0.0) * multiplier
            val linkId = (ing["linkFormulaId"] as? String ?: "").trim()
            if (linkId.isNotBlank() && byId[linkId] != null) {
                val child = byId[linkId]!!
                val childBaseWeight = child.ingredients.sumOf { (it["qtyKg"] as? Number)?.toDouble() ?: 0.0 }
                    .coerceAtLeast(1.0)
                val subMultiplier = qtyKg / childBaseWeight
                flattenFormula(child, allFormulas, inventory, subMultiplier, nextVisited).forEach { sub ->
                    val key = sub.name.lowercase()
                    val existing = aggregated[key]
                    aggregated[key] = if (existing == null) sub else existing.copy(qtyKg = InputGuards.round3(existing.qtyKg + sub.qtyKg))
                }
            } else if (name.isNotBlank() && qtyKg > 0.0) {
                val inv = invByName[name.lowercase()]
                val key = name.lowercase()
                val existing = aggregated[key]
                val line = FlattenedIngredient(
                    name = inv?.name ?: name,
                    qtyKg = InputGuards.round3((existing?.qtyKg ?: 0.0) + qtyKg),
                    inventoryItemId = inv?.id ?: "",
                    unitCostPerKg = inv?.avgCostPerKg ?: 0.0
                )
                aggregated[key] = line
            }
        }
        return aggregated.values.toList()
    }

    fun describeFormula(formula: FormulaConfig, allFormulas: List<FormulaConfig>): String {
        val baseKg = formula.ingredients.sumOf { (it["qtyKg"] as? Number)?.toDouble() ?: 0.0 }
        val linkedCount = formula.ingredients.count { ((it["linkFormulaId"] as? String) ?: "").isNotBlank() }
        val linkSuffix = if (linkedCount > 0) " • $linkedCount sub-formula" else ""
        return "${formula.ingredients.size} items (${InputGuards.formatKg(baseKg)}/batch)$linkSuffix"
    }

    fun calculateBatchCost(
        formula: FormulaConfig,
        allFormulas: List<FormulaConfig>,
        inventory: List<InventoryItem>,
        units: Double = 1.0
    ): Double {
        val flat = flattenFormula(formula, allFormulas, inventory, units)
        val materialCost = flat.sumOf { it.qtyKg * it.unitCostPerKg }
        val extra = (formula.additionalCost + formula.costAdjustment) * units
        return InputGuards.round2((materialCost + extra).coerceAtLeast(0.0))
    }

    fun calculateBatchMix(
        formula: FormulaConfig,
        allFormulas: List<FormulaConfig>,
        inventory: List<InventoryItem>,
        mode: String, // "total" | "units" | "ingredient"
        targetValue: Double,
        anchorIngredientName: String,
        useGrams: Boolean
    ): List<BatchMixLine> {
        val baseLines = flattenFormula(formula, allFormulas, inventory, 1.0)
        val totalBaseKg = baseLines.sumOf { it.qtyKg }
        if (totalBaseKg <= 0.0 || targetValue <= 0.0) return emptyList()

        val targetKgInput = if (useGrams && mode != "units") targetValue / 1000.0 else targetValue
        val scale = when (mode) {
            "units" -> targetValue
            "ingredient" -> {
                val anchor = baseLines.firstOrNull { it.name.equals(anchorIngredientName, ignoreCase = true) }
                if (anchor != null && anchor.qtyKg > 0.0) targetKgInput / anchor.qtyKg else 1.0
            }
            else -> targetKgInput / totalBaseKg
        }

        return baseLines.map { line ->
            val scaledKg = line.qtyKg * scale
            val displayVal = if (useGrams) InputGuards.round2(scaledKg * 1000.0) else InputGuards.round3(scaledKg)
            BatchMixLine(
                ingredientName = line.name,
                qtyDisplay = displayVal,
                qtyKg = InputGuards.round3(scaledKg),
                unitLabel = if (useGrams) "g" else "kg",
                estCost = InputGuards.round2(scaledKg * line.unitCostPerKg)
            )
        }
    }
}
