package com.gullzubair.sarim.domain

import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Port of the web app's Manufacturing tab maths (updateAllStoresOverview,
 * updateStoreComparisonChart, updateMfgCharts, calculateDynamicCost,
 * updateUnitsAvailableIndicator, refreshUI history filtering).
 */
object ProductionEngine {

    enum class Period(val label: String) {
        DAY("Daily"), WEEK("Weekly"), MONTH("Monthly"), YEAR("Yearly"), ALL("All Times");

        val chip: String get() = if (this == ALL) "All time" else label
    }

    enum class Metric(val label: String) {
        WEIGHT("Weight (kg)"), VALUE("Total Value"), COST("Total Cost"), PROFIT("Net Profit")
    }

    /** Same rules as the web `_inPeriod`: week = selected day and the 6 days before it. */
    fun inPeriod(dateIso: String, period: Period, selectedIso: String): Boolean {
        if (period == Period.ALL) return true
        val d = DateUtils.parseIso(dateIso) ?: return false
        val sel = DateUtils.parseIso(selectedIso) ?: return false
        return when (period) {
            Period.DAY -> d == sel
            Period.WEEK -> !d.isBefore(sel.minusDays(6)) && !d.isAfter(sel)
            Period.MONTH -> d.year == sel.year && d.month == sel.month
            Period.YEAR -> d.year == sel.year
            Period.ALL -> true
        }
    }

    fun matchesStore(recStore: String, store: StoreConfig): Boolean =
        recStore.equals(store.key, true) || (store.name.isNotBlank() && recStore.equals(store.name, true))

    fun alive(production: List<ProductionRecord>): List<ProductionRecord> =
        production.filter { it.deletedAt == null }

    // ---------------------------------------------------------------- overview

    data class StoreOverview(
        val store: StoreConfig,
        val production: Double,
        val returns: Double,
        val sold: Double,
        val value: Double,
        val cost: Double,
        val profit: Double,
        val formulaUnits: Double,
        val formulaCost: Double,
        val soldByCustomer: List<Pair<String, Double>>
    ) {
        val totalIn: Double get() = production + returns
        val remaining: Double get() = totalIn - sold
        val marginPct: Int? get() = if (value > 0.0) Math.round(profit / value * 100.0).toInt() else null
        val leftPct: Int get() = if (totalIn > 0.0) Math.max(0, Math.round(remaining / totalIn * 100.0).toInt()) else 0
    }

    data class CombinedOverview(
        val production: Double,
        val returns: Double,
        val sold: Double,
        val qty: Double,
        val value: Double,
        val cost: Double,
        val profit: Double,
        val formulaUnits: Double,
        val formulaCost: Double,
        val soldByCustomer: List<Pair<String, Double>>
    ) {
        val remaining: Double get() = qty - sold
        val marginPct: Int? get() = if (value > 0.0) Math.round(profit / value * 100.0).toInt() else null
    }

    data class Overview(val stores: List<StoreOverview>, val total: CombinedOverview)

    fun overview(snapshot: WorkspaceSnapshot, stores: List<StoreConfig>, period: Period, selectedIso: String): Overview {
        val prod = alive(snapshot.production)
        val sales = snapshot.sales.filter { it.deletedAt == null }
        val allSold = LinkedHashMap<String, Double>()
        val rows = stores.map { st ->
            var production = 0.0; var returns = 0.0; var value = 0.0; var cost = 0.0
            var profit = 0.0; var fu = 0.0; var fc = 0.0
            prod.forEach { p ->
                if (matchesStore(p.store, st) && inPeriod(p.date, period, selectedIso)) {
                    if (p.isReturn) returns += p.net
                    else {
                        production += p.net; fu += p.formulaUnits; fc += p.formulaCost
                        value += p.totalSale; cost += p.totalCost; profit += p.profit
                    }
                }
            }
            var sold = 0.0
            val byCust = LinkedHashMap<String, Double>()
            sales.forEach { s ->
                val d = s.supplyDate.ifBlank { s.date }
                if (matchesStore(s.supplyStore, st) && inPeriod(d, period, selectedIso)) {
                    sold += s.quantity
                    val name = s.customerName.ifBlank { "Unknown" }
                    byCust[name] = (byCust[name] ?: 0.0) + s.quantity
                }
            }
            byCust.forEach { (k, v) -> allSold[k] = (allSold[k] ?: 0.0) + v }
            StoreOverview(
                store = st, production = production, returns = returns, sold = sold,
                value = value, cost = cost, profit = profit, formulaUnits = fu, formulaCost = fc,
                soldByCustomer = byCust.entries.sortedByDescending { it.value }.map { it.key to it.value }
            )
        }
        val total = CombinedOverview(
            production = rows.sumOf { it.production },
            returns = rows.sumOf { it.returns },
            sold = rows.sumOf { it.sold },
            qty = rows.sumOf { it.totalIn },
            value = rows.sumOf { it.value },
            cost = rows.sumOf { it.cost },
            profit = rows.sumOf { it.profit },
            formulaUnits = rows.sumOf { it.formulaUnits },
            formulaCost = rows.sumOf { it.formulaCost },
            soldByCustomer = allSold.entries.sortedByDescending { it.value }.map { it.key to it.value }
        )
        return Overview(rows, total)
    }

    // ------------------------------------------------------- store comparison

    fun storeComparison(
        snapshot: WorkspaceSnapshot,
        stores: List<StoreConfig>,
        metric: Metric,
        period: Period,
        selectedIso: String
    ): List<Pair<String, Double>> {
        val prod = alive(snapshot.production)
        return stores.map { st ->
            var w = 0.0; var v = 0.0; var c = 0.0; var p = 0.0
            prod.forEach { r ->
                if (matchesStore(r.store, st) && inPeriod(r.date, period, selectedIso)) {
                    w += r.net; v += r.totalSale; c += r.totalCost; p += r.profit
                }
            }
            st.name to when (metric) {
                Metric.WEIGHT -> w; Metric.VALUE -> v; Metric.COST -> c; Metric.PROFIT -> p
            }
        }
    }

    // ----------------------------------------------------------- analytics

    data class MfgAnalytics(
        val labels: List<String>,
        val qty: List<Double>,
        val totalCost: Double,
        val totalProfit: Double,
        val totalValue: Double
    )

    /** Weekly / Monthly / Yearly / All-time production bars plus cost-vs-profit totals. */
    fun analytics(snapshot: WorkspaceSnapshot, period: Period, selectedIso: String): MfgAnalytics {
        val data = alive(snapshot.production)
        val sel = DateUtils.parseIso(selectedIso) ?: LocalDate.now()
        val labels = ArrayList<String>()
        var qty = ArrayList<Double>()
        fun d(r: ProductionRecord): LocalDate? = DateUtils.parseIso(r.date)

        when (period) {
            Period.DAY, Period.WEEK -> {
                for (i in 6 downTo 0) {
                    val day = sel.minusDays(i.toLong())
                    labels.add(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                    qty.add(data.filter { it.date == day.toString() }.sumOf { it.net })
                }
            }
            Period.MONTH -> {
                val days = sel.lengthOfMonth()
                for (i in 1..days) { labels.add(i.toString()); qty.add(0.0) }
                data.forEach { r ->
                    val dd = d(r) ?: return@forEach
                    if (dd.year == sel.year && dd.month == sel.month) qty[dd.dayOfMonth - 1] = qty[dd.dayOfMonth - 1] + r.net
                }
            }
            Period.YEAR -> {
                Java12.forEach { labels.add(it); qty.add(0.0) }
                data.forEach { r ->
                    val dd = d(r) ?: return@forEach
                    if (dd.year == sel.year) qty[dd.monthValue - 1] = qty[dd.monthValue - 1] + r.net
                }
            }
            Period.ALL -> {
                val byMonth = java.util.TreeMap<String, Double>()
                data.forEach { r ->
                    val dd = d(r) ?: return@forEach
                    val key = "%04d-%02d".format(dd.year, dd.monthValue)
                    byMonth[key] = (byMonth[key] ?: 0.0) + r.net
                }
                byMonth.entries.toList().takeLast(12).forEach { (k, v) ->
                    val y = k.substring(0, 4); val m = k.substring(5).toInt()
                    labels.add("${Java12[m - 1]} ${y.takeLast(2)}"); qty.add(v)
                }
            }
        }
        // Web: the weekly chart (mode 'week') is the lowest granularity; totals follow the same window.
        val effective = if (period == Period.DAY) Period.WEEK else period
        var cost = 0.0; var profit = 0.0; var value = 0.0
        data.forEach { r ->
            if (inPeriod(r.date, effective, selectedIso)) {
                cost += r.totalCost; profit += r.profit; value += r.totalSale
            }
        }
        return MfgAnalytics(labels, qty, cost, profit, value)
    }

    private val Java12 = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    // ------------------------------------------------- entry form calculations

    data class DynamicCost(
        val hasFormula: Boolean,
        val formulaId: String,
        val formulaName: String,
        val costPerUnit: Double,
        val totalFormulaCost: Double,
        val costPerKg: Double
    )

    /**
     * Mirrors web calculateDynamicCost. When editing an entry that used the same formula the
     * unit cost it was saved with is kept (frozen), exactly like the web edit flow.
     */
    fun dynamicCost(
        snapshot: WorkspaceSnapshot,
        store: StoreConfig?,
        units: Double,
        net: Double,
        editing: ProductionRecord? = null
    ): DynamicCost {
        val formula = store?.formulaId?.let { fid -> snapshot.formulas.firstOrNull { it.id == fid } }
            ?: return DynamicCost(false, "", "", 0.0, 0.0, 0.0)
        val desc = FormulaEngine.describeFormula(formula, snapshot.formulas, snapshot.inventory)
        if (desc.items.isEmpty()) return DynamicCost(false, formula.id, formula.name, 0.0, 0.0, 0.0)
        var perUnit = desc.perUnit
        if (editing != null && editing.formulaId == formula.id && editing.formulaUnits > 0.0 &&
            editing.formulaCost > 0.0 && net > 0.0
        ) {
            perUnit = editing.formulaCost / editing.formulaUnits
        }
        val total = perUnit * units
        val perKg = if (units > 0.0 && net > 0.0) total / net else 0.0
        return DynamicCost(true, formula.id, formula.name, perUnit, total, perKg)
    }

    data class UnitsAvailability(val available: Double, val label: String)

    /** Mirrors web updateUnitsAvailableIndicator (pool of the store's formula, never below zero). */
    fun unitsAvailable(snapshot: WorkspaceSnapshot, store: StoreConfig?): UnitsAvailability {
        val fid = store?.formulaId ?: return UnitsAvailability(0.0, "")
        val pool = FormulaEngine.buildUnitPools(snapshot.stores, snapshot.formulas, snapshot.factory, snapshot.production)
            .firstOrNull { it.key == fid || it.formulaId == fid }
        val label = pool?.label?.takeIf { it.isNotBlank() }
            ?: snapshot.formulas.firstOrNull { it.id == fid }?.name.orEmpty()
        return UnitsAvailability(Math.max(0.0, pool?.availableUnits ?: 0.0), label)
    }

    fun salePriceFor(snapshot: WorkspaceSnapshot, store: StoreConfig?): Double {
        store ?: return 0.0
        return if (store.salePrice > 0.0) store.salePrice else (snapshot.settings.defaultStorePrices[store.key] ?: 0.0)
    }

    /**
     * Store Entry history: only the selected date and store (web `refreshUI` when the view is "store").
     * Combined history: everything, with the selected date pinned first, newest first after that.
     */
    fun history(
        snapshot: WorkspaceSnapshot,
        combined: Boolean,
        store: StoreConfig?,
        selectedIso: String
    ): List<ProductionRecord> {
        val base = alive(snapshot.production).filter { combined || (store != null && matchesStore(it.store, store)) }
        val sorted = base.sortedWith(
            compareByDescending<ProductionRecord> { it.date == selectedIso }.thenByDescending { it.timestamp }
        )
        return if (combined) sorted else sorted.filter { it.date.isBlank() || it.date == selectedIso }
    }
}
