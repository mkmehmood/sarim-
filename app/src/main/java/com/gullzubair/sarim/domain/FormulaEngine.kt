package com.gullzubair.sarim.domain

import com.gullzubair.sarim.data.model.CalculatorRecord
import com.gullzubair.sarim.data.model.FactoryRecord
import com.gullzubair.sarim.data.model.FormulaConfig
import com.gullzubair.sarim.data.model.FormulaIngredient
import com.gullzubair.sarim.data.model.InventoryRecord
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.StoreConfig
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Exact 1:1 Kotlin port of web `store-keys.js`, `formula-links.js`, `batch-calc.js`,
 * and `profit-cost.js`.
 */
object FormulaEngine {

    private const val EPS = 1e-9
    const val MAX_BATCH_KG = 10_000_000.0
    const val LINK_KIND = "formula"

    val DEFAULT_STORES: List<StoreConfig> = listOf(
        StoreConfig(key = "zubair", name = "Zubair"),
        StoreConfig(key = "mahmood", name = "Mahmood"),
        StoreConfig(key = "asaan", name = "Asaan")
    )

    fun slugifyStoreName(name: String): String =
        name.trim().lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    fun makeStoreKey(name: String, existing: List<StoreConfig>): String {
        val base = slugifyStoreName(name).ifBlank { "store" }
        val taken = existing.map { it.key.lowercase(Locale.ROOT) }.toSet()
        if (base !in taken) return base
        var idx = 2
        while ("$base-$idx" in taken) idx++
        return "$base-$idx"
    }

    fun storeLabelFor(keyOrName: String, stores: List<StoreConfig>): String {
        val clean = keyOrName.trim()
        if (clean.isBlank()) return ""
        return stores.firstOrNull {
            it.key.equals(clean, ignoreCase = true) || it.name.equals(clean, ignoreCase = true)
        }?.name ?: clean
    }

    // --- Formula Linking & Flattening (formula-links.js) ---

    data class FormulaProblem(val type: String, val name: String)

    data class FlattenedFormula(
        val ingredients: List<FormulaIngredient>,
        val ownAdditionalCost: Double,
        val extraCost: Double,
        val additionalCost: Double,
        val problems: List<FormulaProblem>
    )

    data class FormulaCostBreakdown(
        val items: List<FormulaIngredient>,
        val raw: Double,
        val weight: Double,
        val ownAdditionalCost: Double,
        val extraCost: Double,
        val additionalCost: Double,
        val perUnit: Double,
        val perKg: Double,
        val problems: List<FormulaProblem>
    )

    fun isLink(ing: FormulaIngredient): Boolean =
        ing.kind == LINK_KIND && !ing.formulaId.isNullOrBlank()

    fun linksIn(formula: FormulaConfig?): List<String> =
        formula?.ingredients?.filter(::isLink)?.mapNotNull { it.formulaId } ?: emptyList()

    fun formulaUsers(list: List<FormulaConfig>, formulaId: String): List<FormulaConfig> =
        list.filter { it.id != formulaId && formulaId in linksIn(it) }

    fun dependsOn(
        list: List<FormulaConfig>,
        formulaId: String,
        targetId: String,
        seen: MutableSet<String> = mutableSetOf()
    ): Boolean {
        if (formulaId == targetId) return true
        if (!seen.add(formulaId)) return false
        val f = list.firstOrNull { it.id == formulaId } ?: return false
        return linksIn(f).any { dependsOn(list, it, targetId, seen) }
    }

    fun canLink(list: List<FormulaConfig>, parentId: String, childId: String): Boolean =
        parentId != childId && !dependsOn(list, childId, parentId)

    fun flattenFormula(
        formula: FormulaConfig,
        list: List<FormulaConfig>,
        path: List<String> = emptyList()
    ): FlattenedFormula {
        val out = mutableListOf<FormulaIngredient>()
        val problems = mutableListOf<FormulaProblem>()
        var extra = 0.0
        val here = path + formula.id

        fun addIngredient(m: FormulaIngredient) {
            val idx = out.indexOfFirst { it.id == m.id && it.kind != LINK_KIND }
            if (idx >= 0) {
                val cur = out[idx]
                out[idx] = cur.copy(quantity = InputGuards.round6(cur.quantity + m.quantity))
            } else {
                out.add(m)
            }
        }

        for (i in formula.ingredients) {
            if (!isLink(i)) {
                addIngredient(
                    FormulaIngredient(
                        id = i.id,
                        name = i.name,
                        cost = i.cost,
                        quantity = i.quantity,
                        custom = i.custom
                    )
                )
                continue
            }
            val q = i.quantity
            if (q <= 0.0) continue
            val sub = list.firstOrNull { it.id == i.formulaId }
            if (sub == null) {
                problems.add(FormulaProblem("missing", i.name.ifBlank { "Formula" }))
                continue
            }
            if (sub.id in here) {
                problems.add(FormulaProblem("cycle", sub.name.ifBlank { "Formula" }))
                continue
            }
            val flat = flattenFormula(sub, list, here)
            problems.addAll(flat.problems)
            val weight = flat.ingredients.sumOf { it.quantity }
            if (weight <= 0.0) continue
            val scale = q / weight
            flat.ingredients.forEach { m ->
                addIngredient(m.copy(quantity = InputGuards.round6(m.quantity * scale)))
            }
            extra += flat.additionalCost * scale
        }

        val own = formula.additionalCost
        return FlattenedFormula(
            ingredients = out,
            ownAdditionalCost = own,
            extraCost = extra,
            additionalCost = own + extra,
            problems = problems
        )
    }

    fun costWithInventory(items: List<FormulaIngredient>, inventory: List<InventoryRecord>): List<FormulaIngredient> {
        val materials = inventory.filter { !it.isPurchaseLot && it.deletedAt == null }
        return items.map { i ->
            val live = materials.firstOrNull { it.id == i.id }
                ?: materials.firstOrNull { it.name.trim().equals(i.name.trim(), ignoreCase = true) }
            val liveCost = live?.cost ?: 0.0
            i.copy(
                name = live?.name?.ifBlank { i.name } ?: i.name,
                cost = if (liveCost > 0.0) liveCost else i.cost
            )
        }
    }

    fun describeFormula(
        formula: FormulaConfig,
        list: List<FormulaConfig>,
        inventory: List<InventoryRecord>
    ): FormulaCostBreakdown {
        val flat = flattenFormula(formula, list)
        val items = costWithInventory(flat.ingredients, inventory)
        val raw = items.sumOf { it.cost * it.quantity }
        val weight = items.sumOf { it.quantity }
        val factor = if (formula.costAdjustmentFactor > 0.0) formula.costAdjustmentFactor else 1.0
        val perUnit = raw + flat.additionalCost
        val perKg = if (factor > 0.0) perUnit / factor else perUnit
        return FormulaCostBreakdown(
            items = items,
            raw = raw,
            weight = weight,
            ownAdditionalCost = flat.ownAdditionalCost,
            extraCost = flat.extraCost,
            additionalCost = flat.additionalCost,
            perUnit = perUnit,
            perKg = perKg,
            problems = flat.problems
        )
    }

    // --- Factory Unit Pools (store-keys.js) ---

    data class UnitPool(
        val key: String,
        val label: String,
        val formulaId: String,
        val producedUnits: Double,
        val usedUnits: Double,
        val availableUnits: Double,
        val avgUnitCost: Double
    )

    fun buildUnitPools(
        stores: List<StoreConfig>,
        formulas: List<FormulaConfig>,
        factoryRecords: List<FactoryRecord>,
        productionRecords: List<ProductionRecord>
    ): List<UnitPool> {
        val activeFactory = factoryRecords.filter { it.deletedAt == null }
        val activeProd = productionRecords.filter { it.deletedAt == null && !it.isReturn && !it.isTransfer }

        val producedByKey = LinkedHashMap<String, Pair<Double, Double>>() // units, totalCost
        val labels = LinkedHashMap<String, String>()
        val formulaByKey = LinkedHashMap<String, String>()

        activeFactory.forEach { f ->
            val fid = f.formulaId.ifBlank { f.store }
            if (fid.isBlank()) return@forEach
            val prev = producedByKey[fid] ?: (0.0 to 0.0)
            producedByKey[fid] = (prev.first + f.units) to (prev.second + f.totalCost)
            val label = f.formulaName.ifBlank {
                formulas.firstOrNull { it.id == fid }?.name
                    ?: storeLabelFor(f.store, stores).ifBlank { fid }
            }
            labels[fid] = label
            formulaByKey[fid] = f.formulaId
        }

        val usedByKey = LinkedHashMap<String, Double>()
        activeProd.forEach { p ->
            if (p.formulaUnits <= 0.0) return@forEach
            val key = p.formulaId.ifBlank { p.store }
            if (key.isBlank()) return@forEach
            usedByKey[key] = (usedByKey[key] ?: 0.0) + p.formulaUnits
        }

        val keys = (producedByKey.keys + usedByKey.keys).distinct()
        return keys.map { key ->
            val (prod, totalCost) = producedByKey[key] ?: (0.0 to 0.0)
            val used = usedByKey[key] ?: 0.0
            UnitPool(
                key = key,
                label = labels[key] ?: key,
                formulaId = formulaByKey[key] ?: key,
                producedUnits = InputGuards.round2(prod),
                usedUnits = InputGuards.round2(used),
                availableUnits = InputGuards.round2(prod - used),
                avgUnitCost = if (prod > 0.0) InputGuards.round2(totalCost / prod) else 0.0
            )
        }
    }

    // --- Batch Mix & Correction Calculator (batch-calc.js) ---

    data class BatchMixRow(
        val id: String,
        val name: String,
        val perUnit: Double,
        val total: Double
    )

    data class BatchMixPlan(
        val units: Double,
        val unitWeight: Double,
        val totalWeight: Double,
        val rows: List<BatchMixRow>
    )

    fun planBatch(
        formula: FormulaConfig,
        allFormulas: List<FormulaConfig>,
        mode: String, // "units" | "total" | "ingredient"
        value: Double,
        ingredientId: String? = null
    ): BatchMixPlan {
        val flat = flattenFormula(formula, allFormulas)
        val ings = flat.ingredients.filter { it.quantity > 0.0 && it.quantity <= MAX_BATCH_KG }
        val uw = ings.sumOf { it.quantity }
        val v = if (value > 0.0 && value <= MAX_BATCH_KG) value else 0.0
        var units = 0.0
        if (ings.isNotEmpty() && uw > 0.0 && v > 0.0) {
            units = when (mode) {
                "units" -> v
                "total" -> v / uw
                "ingredient" -> {
                    val pick = ings.firstOrNull { it.id == ingredientId } ?: ings.first()
                    if (pick.quantity > 0.0) v / pick.quantity else 0.0
                }
                else -> 0.0
            }
        }
        val rows = ings.map { i ->
            BatchMixRow(
                id = i.id,
                name = i.name,
                perUnit = i.quantity,
                total = i.quantity * units
            )
        }
        return BatchMixPlan(
            units = units,
            unitWeight = uw,
            totalWeight = units * uw,
            rows = rows
        )
    }

    data class BatchFixRow(
        val id: String,
        val name: String,
        val present: Double,
        val target: Double,
        val add: Double
    )

    data class BatchFixPlan(
        val ok: Boolean,
        val error: String = "",
        val remaining: Double = 0.0,
        val scale: Double = 0.0,
        val minScale: Double = 0.0,
        val manualId: String = "",
        val adjusted: Boolean = false,
        val rows: List<BatchFixRow> = emptyList(),
        val requiredTotal: Double = 0.0,
        val addTotal: Double = 0.0
    )

    fun planCorrection(
        formula: FormulaConfig,
        allFormulas: List<FormulaConfig>,
        original: Double,
        used: Double,
        actual: Map<String, Double> = emptyMap(),
        manualId: String? = null,
        manualAdd: Double? = null,
        manualTotal: Double? = null
    ): BatchFixPlan {
        val ings = flattenFormula(formula, allFormulas).ingredients.filter { it.quantity > 0.0 }
        val uw = ings.sumOf { it.quantity }
        if (ings.isEmpty() || original <= 0.0 || uw <= 0.0) {
            return BatchFixPlan(ok = false, error = "empty")
        }
        val safeUsed = max(0.0, used)
        val remaining = original - safeUsed
        if (remaining <= EPS) {
            return BatchFixPlan(ok = false, error = "nothing-left")
        }
        val actualTotal = ings.sumOf { max(0.0, actual[it.id] ?: 0.0) }
        val present = ings.map { i ->
            if (actualTotal > 0.0) (max(0.0, actual[i.id] ?: 0.0) / actualTotal) * remaining
            else (i.quantity / uw) * remaining
        }
        var minScale = 0.0
        ings.forEachIndexed { idx, i ->
            minScale = max(minScale, present[idx] / i.quantity)
        }
        var scale = minScale
        var resolvedManualId = ""
        val manualIdx = if (!manualId.isNullOrBlank()) ings.indexOfFirst { it.id == manualId } else -1
        if (manualTotal != null && manualTotal.isFinite()) {
            scale = max(minScale, (remaining + max(0.0, manualTotal)) / uw)
        } else if (manualIdx >= 0 && manualAdd != null && manualAdd.isFinite()) {
            scale = max(minScale, (present[manualIdx] + max(0.0, manualAdd)) / ings[manualIdx].quantity)
            resolvedManualId = ings[manualIdx].id
        }
        val rows = ings.mapIndexed { idx, i ->
            val target = i.quantity * scale
            val rawAdd = max(0.0, target - present[idx])
            BatchFixRow(
                id = i.id,
                name = i.name,
                present = present[idx],
                target = target,
                add = if (rawAdd < EPS) 0.0 else rawAdd
            )
        }
        return BatchFixPlan(
            ok = true,
            remaining = remaining,
            scale = scale,
            minScale = minScale,
            manualId = resolvedManualId,
            adjusted = scale > minScale + EPS,
            rows = rows,
            requiredTotal = rows.sumOf { it.target },
            addTotal = rows.sumOf { it.add }
        )
    }

    // --- Profit & Production Cost Indexing (profit-cost.js) ---

    data class CostIndex(
        val byStoreDate: Map<String, Double>,
        val byStore: Map<String, Double>
    )

    data class CostLookupResult(
        val cp: Double,
        val source: String // "production-day" | "store-average" | "none"
    )

    fun buildCostIndex(production: List<ProductionRecord>): CostIndex {
        val dayKg = LinkedHashMap<String, Double>()
        val dayCost = LinkedHashMap<String, Double>()
        val storeKg = LinkedHashMap<String, Double>()
        val storeCost = LinkedHashMap<String, Double>()

        production.forEach { p ->
            if (p.deletedAt != null || p.isReturn || p.isTransfer) return@forEach
            val store = p.store.trim().lowercase(Locale.ROOT)
            val kg = p.net
            val cost = p.totalCost
            if (store.isBlank() || kg <= 0.0 || cost <= 0.0) return@forEach
            storeKg[store] = (storeKg[store] ?: 0.0) + kg
            storeCost[store] = (storeCost[store] ?: 0.0) + cost
            if (p.date.length >= 10) {
                val key = "$store|${p.date.take(10)}"
                dayKg[key] = (dayKg[key] ?: 0.0) + kg
                dayCost[key] = (dayCost[key] ?: 0.0) + cost
            }
        }
        val byStoreDate = dayKg.mapValues { (k, kg) -> if (kg > 0.0) (dayCost[k] ?: 0.0) / kg else 0.0 }
        val byStore = storeKg.mapValues { (k, kg) -> if (kg > 0.0) (storeCost[k] ?: 0.0) / kg else 0.0 }
        return CostIndex(byStoreDate = byStoreDate, byStore = byStore)
    }

    fun costSourceFromIndex(index: CostIndex, storeName: String, supplyDate: String): CostLookupResult {
        val store = storeName.trim().lowercase(Locale.ROOT)
        if (store.isBlank()) return CostLookupResult(0.0, "none")
        if (supplyDate.length >= 10) {
            val dayCp = index.byStoreDate["$store|${supplyDate.take(10)}"]
            if (dayCp != null && dayCp > 0.0) {
                return CostLookupResult(InputGuards.round2(dayCp), "production-day")
            }
        }
        val avgCp = index.byStore[store]
        if (avgCp != null && avgCp > 0.0) {
            return CostLookupResult(InputGuards.round2(avgCp), "store-average")
        }
        return CostLookupResult(0.0, "none")
    }

    fun repriceSale(sale: SalesRecord, cp: Double, costSource: String): SalesRecord {
        if (cp <= 0.0) return sale
        if (sale.paymentType in setOf("COLLECTION", "PARTIAL_PAYMENT", "MONEY_OUT", "REP_RETURN") || sale.isCollection) {
            return sale
        }
        if (sale.quantity <= 0.0) return sale
        val totalCost = InputGuards.round2(sale.quantity * cp)
        val totalVal = if (sale.totalValue > 0.0) sale.totalValue else InputGuards.round2(sale.quantity * sale.unitPrice)
        val profit = InputGuards.round2(totalVal - totalCost)
        return sale.copy(totalCost = totalCost, profit = profit, costSource = costSource)
    }

    fun kgPerUnitFromProduction(
        production: List<ProductionRecord>,
        store: String,
        date: String
    ): Double {
        val matches = production.filter {
            it.deletedAt == null && !it.isReturn && !it.isTransfer &&
                it.store.equals(store, ignoreCase = true) &&
                it.date.take(10) == date.take(10) &&
                it.formulaUnits > 0.0 && it.net > 0.0
        }
        val totalUnits = matches.sumOf { it.formulaUnits }
        val totalKg = matches.sumOf { it.net }
        return if (totalUnits > 0.0) InputGuards.round2(totalKg / totalUnits) else 0.0
    }
}
