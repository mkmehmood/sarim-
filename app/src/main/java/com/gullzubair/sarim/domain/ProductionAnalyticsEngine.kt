package com.gullzubair.sarim.domain

import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure domain calculations for the Manufacturing / Production tab, matching web:
 * - `factory-core.js` (`calculateDynamicCost`, `validateFormulaAvailability`)
 * - `utilities-core.js` (`_recordEntryImpl` frozen edit cost & stock-sold guard)
 * - `utilities-sales.js` (`_inPeriod`, `updateAllStoresOverview`, `updateStoreComparisonChart`, `updateMfgCharts`)
 */
object ProductionAnalyticsEngine {

    enum class OverviewPeriod(val key: String, val label: String) {
        DAY("day", "Daily"),
        WEEK("week", "Weekly"),
        MONTH("month", "Monthly"),
        YEAR("year", "Yearly"),
        ALL("all", "All Times")
    }

    enum class ComparisonMetric(val key: String, val label: String) {
        WEIGHT("weight", "Weight (kg)"),
        VALUE("value", "Total Value"),
        COST("cost", "Total Cost"),
        PROFIT("profit", "Net Profit")
    }

    enum class MfgChartPeriod(val key: String, val label: String) {
        WEEK("week", "Weekly"),
        MONTH("month", "Monthly"),
        YEAR("year", "Yearly"),
        ALL("all", "All Times")
    }

    data class DynamicProdCost(
        val costPerUnit: Double,
        val totalFormulaCost: Double,
        val dynamicCostPerKg: Double,
        val formulaId: String,
        val formulaName: String,
        val salePrice: Double,
        val availableUnits: Double,
        val unitCredit: Double,
        val effectiveAvailable: Double
    )

    data class StoreOverviewCardData(
        val storeKey: String,
        val storeName: String,
        val producedKg: Double,
        val returnsKg: Double,
        val totalInKg: Double,
        val soldKg: Double,
        val remainingKg: Double,
        val formulaUnits: Double,
        val formulaCost: Double,
        val totalValue: Double,
        val totalCost: Double,
        val netProfit: Double,
        val marginPct: Double?,
        val soldByCustomer: List<Pair<String, Double>>
    )

    data class TotalCombinedOverviewData(
        val producedKg: Double,
        val returnsKg: Double,
        val totalInKg: Double,
        val soldKg: Double,
        val remainingKg: Double,
        val formulaUnits: Double,
        val formulaCost: Double,
        val totalValue: Double,
        val totalCost: Double,
        val netProfit: Double,
        val marginPct: Double?,
        val soldByCustomer: List<Pair<String, Double>>
    )

    data class AllStoresOverviewResult(
        val stores: List<StoreOverviewCardData>,
        val combined: TotalCombinedOverviewData
    )

    data class StoreComparisonPoint(
        val storeKey: String,
        val storeName: String,
        val value: Double
    )

    data class MfgAnalyticsResult(
        val labels: List<String>,
        val quantities: List<Double>,
        val totalCost: Double,
        val totalProfit: Double,
        val totalValue: Double
    )

    fun isInAnchoredPeriod(dateStr: String, periodKey: String, anchorDateIso: String): Boolean {
        if (periodKey.equals("all", ignoreCase = true)) return true
        val d = DateUtils.parseIso(dateStr) ?: return false
        val anchor = DateUtils.parseIso(anchorDateIso) ?: LocalDate.now()
        return when (periodKey.lowercase(Locale.ROOT)) {
            "day" -> d == anchor
            "week" -> !d.isBefore(anchor.minusDays(6)) && !d.isAfter(anchor)
            "month" -> d.year == anchor.year && d.month == anchor.month
            "year" -> d.year == anchor.year
            else -> true
        }
    }

    fun matchesStore(candidate: String, store: StoreConfig): Boolean {
        val c = candidate.trim()
        if (c.isBlank()) return false
        return c.equals(store.key, ignoreCase = true) || c.equals(store.name, ignoreCase = true)
    }

    fun resolvePoolForStore(
        store: StoreConfig,
        pools: List<FormulaEngine.UnitPool>
    ): FormulaEngine.UnitPool? {
        val fid = store.formulaId?.takeIf { it.isNotBlank() }
        if (fid != null) {
            pools.firstOrNull { it.formulaId == fid || it.key == fid }?.let { return it }
        }
        return pools.firstOrNull {
            it.key.equals(store.key, ignoreCase = true) ||
                it.label.equals(store.name, ignoreCase = true)
        }
    }

    fun computeDynamicProdCost(
        snapshot: WorkspaceSnapshot,
        store: StoreConfig?,
        formulaUnits: Double,
        netWeight: Double,
        editingRecord: ProductionRecord? = null
    ): DynamicProdCost {
        if (store == null) {
            return DynamicProdCost(0.0, 0.0, 0.0, "", "", 0.0, 0.0, 0.0, 0.0)
        }
        val pools = FormulaEngine.buildUnitPools(
            snapshot.stores,
            snapshot.formulas,
            snapshot.factory,
            snapshot.production
        )
        val pool = resolvePoolForStore(store, pools)
        val available = pool?.availableUnits ?: 0.0
        val formula = snapshot.formulas.firstOrNull { it.id == store.formulaId }
        val resolvedFormulaId = formula?.id ?: store.formulaId.orEmpty()
        val desc = formula?.let { FormulaEngine.describeFormula(it, snapshot.formulas, snapshot.inventory) }

        var costPerUnit = desc?.perUnit ?: 0.0
        var totalFormulaCost = if (formulaUnits > 0.0) costPerUnit * formulaUnits else 0.0
        var dynamicCostPerKg = if (formulaUnits > 0.0 && netWeight > 0.0) totalFormulaCost / netWeight else 0.0
        var formulaName = formula?.name ?: pool?.label.orEmpty()

        // Web utilities-core.js frozen edit formula snapshot
        if (editingRecord != null &&
            editingRecord.formulaId.isNotBlank() &&
            editingRecord.formulaId == resolvedFormulaId &&
            editingRecord.formulaUnits > 0.0 &&
            editingRecord.formulaCost > 0.0 &&
            netWeight > 0.0
        ) {
            val frozenUnit = editingRecord.formulaCost / editingRecord.formulaUnits
            costPerUnit = frozenUnit
            totalFormulaCost = frozenUnit * formulaUnits
            dynamicCostPerKg = if (formulaUnits > 0.0) totalFormulaCost / netWeight else 0.0
            if (editingRecord.formulaName.isNotBlank()) {
                formulaName = editingRecord.formulaName
            }
        }

        val salePrice = if (store.salePrice > 0.0) {
            store.salePrice
        } else {
            snapshot.settings.defaultStorePrices[store.key] ?: 0.0
        }

        val unitCredit = if (editingRecord != null &&
            (editingRecord.formulaId == resolvedFormulaId || matchesStore(editingRecord.store, store))
        ) {
            editingRecord.formulaUnits
        } else {
            0.0
        }

        return DynamicProdCost(
            costPerUnit = InputGuards.round2(costPerUnit),
            totalFormulaCost = InputGuards.round2(totalFormulaCost),
            dynamicCostPerKg = InputGuards.round2(dynamicCostPerKg),
            formulaId = resolvedFormulaId,
            formulaName = formulaName,
            salePrice = InputGuards.round2(salePrice),
            availableUnits = InputGuards.round2(available),
            unitCredit = InputGuards.round2(unitCredit),
            effectiveAvailable = InputGuards.round2(available + unitCredit)
        )
    }

    fun computeAllStoresOverview(
        snapshot: WorkspaceSnapshot,
        period: OverviewPeriod,
        anchorDateIso: String
    ): AllStoresOverviewResult {
        val activeStores = snapshot.stores.filter { it.deletedAt == null && it.active }
        val activeProd = snapshot.production.filter { it.deletedAt == null && !it.isTransfer }
        val activeSales = snapshot.sales.filter { it.deletedAt == null && !FinanceEngine.isLedgerRow(it) }
        val activeCalc = snapshot.calculator.filter { it.deletedAt == null }

        var combinedProd = 0.0
        var combinedReturns = 0.0
        var combinedSold = 0.0
        var combinedQty = 0.0
        var combinedValue = 0.0
        var combinedCost = 0.0
        var combinedProfit = 0.0
        var combinedFormulaUnits = 0.0
        var combinedFormulaCost = 0.0
        val combinedCustMap = LinkedHashMap<String, Double>()

        val storeCards = activeStores.map { store ->
            var produced = 0.0
            var returns = 0.0
            var fu = 0.0
            var fc = 0.0
            var totalVal = 0.0
            var totalCost = 0.0
            var profit = 0.0

            activeProd.forEach { item ->
                if (isInAnchoredPeriod(item.date, period.key, anchorDateIso) && matchesStore(item.store, store)) {
                    if (item.isReturn) {
                        returns += item.net
                    } else {
                        produced += item.net
                        fu += item.formulaUnits
                        fc += item.formulaCost
                        totalVal += item.totalSale
                        totalCost += item.totalCost
                        profit += item.profit
                    }
                }
            }

            var sold = 0.0
            val soldByCust = LinkedHashMap<String, Double>()
            activeSales.forEach { sale ->
                val effDate = sale.supplyDate.ifBlank { sale.date }
                if (isInAnchoredPeriod(effDate, period.key, anchorDateIso) && matchesStore(sale.supplyStore, store)) {
                    val qty = sale.quantity
                    sold += qty
                    val cust = sale.customerName.ifBlank { "Unknown" }
                    soldByCust[cust] = (soldByCust[cust] ?: 0.0) + qty
                }
            }

            // Reconcile calculator returns as in web updateAllStoresOverview
            var calcStoreReturns = 0.0
            activeCalc.forEach { h ->
                if (isInAnchoredPeriod(h.date, period.key, anchorDateIso) && h.returned > 0.0) {
                    val matchingReturns = activeProd.filter { item ->
                        item.isReturn &&
                            item.returnedBy.equals(h.seller, ignoreCase = true) &&
                            matchesStore(item.store, store) &&
                            item.date.take(10) == h.date.take(10)
                    }
                    calcStoreReturns += matchingReturns.sumOf { it.net }
                }
            }
            if (calcStoreReturns > 0.0 && abs(returns - calcStoreReturns) > 0.01) {
                returns = calcStoreReturns
            }

            val totalIn = produced + returns
            val remaining = totalIn - sold

            combinedProd += produced
            combinedReturns += returns
            combinedSold += sold
            combinedQty += totalIn
            combinedValue += totalVal
            combinedCost += totalCost
            combinedProfit += profit
            combinedFormulaUnits += fu
            combinedFormulaCost += fc
            soldByCust.forEach { (cust, qty) ->
                combinedCustMap[cust] = (combinedCustMap[cust] ?: 0.0) + qty
            }

            val margin = if (totalVal > 0.0) ((profit / totalVal) * 1000.0).roundToInt() / 10.0 else null

            StoreOverviewCardData(
                storeKey = store.key,
                storeName = store.name,
                producedKg = InputGuards.round2(produced),
                returnsKg = InputGuards.round2(returns),
                totalInKg = InputGuards.round2(totalIn),
                soldKg = InputGuards.round2(sold),
                remainingKg = InputGuards.round2(remaining),
                formulaUnits = InputGuards.round2(fu),
                formulaCost = InputGuards.round2(fc),
                totalValue = InputGuards.round2(totalVal),
                totalCost = InputGuards.round2(totalCost),
                netProfit = InputGuards.round2(profit),
                marginPct = margin,
                soldByCustomer = soldByCust.entries
                    .filter { it.value > 0.0 }
                    .sortedByDescending { it.value }
                    .map { it.key to InputGuards.round2(it.value) }
            )
        }

        val calcTotalReturns = activeCalc
            .filter { isInAnchoredPeriod(it.date, period.key, anchorDateIso) }
            .sumOf { it.returned }
        if (calcTotalReturns > 0.0 && abs(combinedReturns - calcTotalReturns) > 0.01) {
            combinedReturns = calcTotalReturns
        }

        val combinedRemaining = combinedQty - combinedSold
        val combinedMargin = if (combinedValue > 0.0) ((combinedProfit / combinedValue) * 1000.0).roundToInt() / 10.0 else null

        return AllStoresOverviewResult(
            stores = storeCards,
            combined = TotalCombinedOverviewData(
                producedKg = InputGuards.round2(combinedProd),
                returnsKg = InputGuards.round2(combinedReturns),
                totalInKg = InputGuards.round2(combinedQty),
                soldKg = InputGuards.round2(combinedSold),
                remainingKg = InputGuards.round2(combinedRemaining),
                formulaUnits = InputGuards.round2(combinedFormulaUnits),
                formulaCost = InputGuards.round2(combinedFormulaCost),
                totalValue = InputGuards.round2(combinedValue),
                totalCost = InputGuards.round2(combinedCost),
                netProfit = InputGuards.round2(combinedProfit),
                marginPct = combinedMargin,
                soldByCustomer = combinedCustMap.entries
                    .filter { it.value > 0.0 }
                    .sortedByDescending { it.value }
                    .map { it.key to InputGuards.round2(it.value) }
            )
        )
    }

    fun computeStoreComparison(
        snapshot: WorkspaceSnapshot,
        period: OverviewPeriod,
        metric: ComparisonMetric,
        anchorDateIso: String
    ): List<StoreComparisonPoint> {
        val activeStores = snapshot.stores.filter { it.deletedAt == null && it.active }
        val activeProd = snapshot.production.filter { it.deletedAt == null && !it.isTransfer && !it.isReturn }
        return activeStores.map { store ->
            var w = 0.0
            var v = 0.0
            var c = 0.0
            var p = 0.0
            activeProd.forEach { item ->
                if (isInAnchoredPeriod(item.date, period.key, anchorDateIso) && matchesStore(item.store, store)) {
                    w += item.net
                    v += item.totalSale
                    c += item.totalCost
                    p += item.profit
                }
            }
            val raw = when (metric) {
                ComparisonMetric.WEIGHT -> w
                ComparisonMetric.VALUE -> v
                ComparisonMetric.COST -> c
                ComparisonMetric.PROFIT -> p
            }
            StoreComparisonPoint(
                storeKey = store.key,
                storeName = store.name,
                value = InputGuards.round2(raw)
            )
        }
    }

    fun computeMfgAnalytics(
        snapshot: WorkspaceSnapshot,
        period: MfgChartPeriod,
        anchorDateIso: String,
        isCombinedView: Boolean,
        selectedStore: StoreConfig?
    ): MfgAnalyticsResult {
        val anchor = DateUtils.parseIso(anchorDateIso) ?: LocalDate.now()
        val filtered = snapshot.production.filter { item ->
            item.deletedAt == null && !item.isTransfer && !item.isReturn &&
                (isCombinedView || (selectedStore != null && matchesStore(item.store, selectedStore)))
        }

        val labels = mutableListOf<String>()
        val quantities = mutableListOf<Double>()

        when (period) {
            MfgChartPeriod.WEEK -> {
                for (offset in 6 downTo 0) {
                    val d = anchor.minusDays(offset.toLong())
                    val iso = d.toString()
                    labels.add(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US))
                    quantities.add(InputGuards.round2(filtered.filter { it.date.take(10) == iso }.sumOf { it.net }))
                }
            }
            MfgChartPeriod.MONTH -> {
                val ym = YearMonth.from(anchor)
                val daysInMonth = ym.lengthOfMonth()
                val buckets = DoubleArray(daysInMonth)
                filtered.forEach { item ->
                    val d = DateUtils.parseIso(item.date) ?: return@forEach
                    if (d.year == anchor.year && d.month == anchor.month) {
                        buckets[d.dayOfMonth - 1] += item.net
                    }
                }
                for (day in 1..daysInMonth) {
                    labels.add(day.toString())
                    quantities.add(InputGuards.round2(buckets[day - 1]))
                }
            }
            MfgChartPeriod.YEAR -> {
                val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val buckets = DoubleArray(12)
                filtered.forEach { item ->
                    val d = DateUtils.parseIso(item.date) ?: return@forEach
                    if (d.year == anchor.year) {
                        buckets[d.monthValue - 1] += item.net
                    }
                }
                months.forEachIndexed { idx, name ->
                    labels.add(name)
                    quantities.add(InputGuards.round2(buckets[idx]))
                }
            }
            MfgChartPeriod.ALL -> {
                val monthMap = sortedMapOf<String, Pair<String, Double>>()
                filtered.forEach { item ->
                    val d = DateUtils.parseIso(item.date) ?: return@forEach
                    val ymKey = String.format(Locale.US, "%04d-%02d", d.year, d.monthValue)
                    val label = "${d.month.getDisplayName(TextStyle.SHORT, Locale.US)} ${d.year % 100}"
                    val prev = monthMap[ymKey]
                    monthMap[ymKey] = label to ((prev?.second ?: 0.0) + item.net)
                }
                val entries = monthMap.entries.toList().let { if (it.size > 12) it.takeLast(12) else it }
                entries.forEach { (_, pair) ->
                    labels.add(pair.first)
                    quantities.add(InputGuards.round2(pair.second))
                }
            }
        }

        var totalCost = 0.0
        var totalProfit = 0.0
        var totalValue = 0.0
        filtered.forEach { item ->
            if (isInAnchoredPeriod(item.date, period.key, anchorDateIso)) {
                totalCost += item.totalCost
                totalProfit += item.profit
                totalValue += item.totalSale
            }
        }

        return MfgAnalyticsResult(
            labels = labels,
            quantities = quantities,
            totalCost = InputGuards.round2(max(0.0, totalCost)),
            totalProfit = InputGuards.round2(totalProfit),
            totalValue = InputGuards.round2(totalValue)
        )
    }
}
