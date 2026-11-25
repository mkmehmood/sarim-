package com.gullzubair.sarim.domain

import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * 1:1 Kotlin port of web `overview-data.js`, `overview-extra.js`, and `overview-audit.js`.
 */
object OverviewEngine {

    data class AuditFinding(
        val id: String,
        val collection: String,
        val recordId: String,
        val code: String,
        val codes: List<String> = listOf(code),
        val severity: String, // "error" | "warn"
        val title: String,
        val label: String,
        val date: String,
        val reasons: List<String>,
        val impact: Double = 0.0
    )

    data class AuditCheckSummary(
        val code: String,
        val label: String,
        val severity: String,
        val count: Int
    )

    data class AuditReport(
        val findings: List<AuditFinding>,
        val byCheck: List<AuditCheckSummary>,
        val scannedTotal: Int,
        val errors: Int,
        val warnings: Int
    )

    data class MonthBucket(
        val key: String, // YYYY-MM
        val label: String,
        val prodKg: Double = 0.0,
        val prodProfit: Double = 0.0,
        val prodValue: Double = 0.0,
        val prodCost: Double = 0.0,
        val soldKg: Double = 0.0,
        val returnsKg: Double = 0.0,
        val salesValue: Double = 0.0,
        val salesProfit: Double = 0.0,
        val salesCount: Int = 0,
        val cashIn: Double = 0.0,
        val cashOut: Double = 0.0,
        val expenses: Double = 0.0,
        val factoryUnits: Double = 0.0
    )

    data class ProfitAnalysis(
        val count: Int,
        val losingCount: Int,
        val losingSum: Double,
        val gainingCount: Int,
        val gainingSum: Double,
        val net: Double,
        val avgPrice: Double,
        val avgCost: Double,
        val worstLosses: List<WorstSaleLoss>,
        val byCustomer: List<Pair<String, Double>> = emptyList(),
        val byStore: List<Pair<String, Double>> = emptyList(),
        val byMonth: List<Pair<String, Double>> = emptyList(),
        val noCostCount: Int = 0,
        val noCostValue: Double = 0.0,
        val mismatchCount: Int = 0,
        val mismatchSum: Double = 0.0,
        val zeroQtyCount: Int = 0
    )

    data class WorstSaleLoss(
        val id: String,
        val date: String,
        val customer: String,
        val store: String,
        val qty: Double,
        val price: Double,
        val cost: Double,
        val profit: Double
    )

    data class OwedAgingSummary(
        val total: Double,
        val count: Int,
        val d30: Double,
        val d60: Double,
        val d90: Double,
        val oldestDays: Int,
        val topCustomers: List<Triple<String, Double, Int>> // name, amount, oldestDays
    )

    data class OverviewKpiSummary(
        val windowMonths: Int,
        val months: List<MonthBucket>,
        val prodKg: Double,
        val prodProfit: Double,
        val prodValue: Double,
        val prodCost: Double,
        val marginPct: Double,
        val soldKg: Double,
        val returnsKg: Double,
        val remainingKg: Double,
        val salesValue: Double,
        val salesProfit: Double,
        val salesCount: Int,
        val cashIn: Double,
        val cashOut: Double,
        val netCash: Double,
        val expenses: Double,
        val openCreditValue: Double,
        val openCreditCount: Int,
        val stockValue: Double,
        val stockItemsCount: Int,
        val outOfStockNames: List<String>,
        val todayProdKg: Double,
        val todaySalesValue: Double,
        val todayIn: Double,
        val todayOut: Double,
        val storeSalesBreakdown: List<Pair<String, Double>>,
        val topCustomersValue: List<Pair<String, Double>>,
        val topCustomersKg: List<Pair<String, Double>>,
        val formulaProductionKg: List<Pair<String, Double>>,
        val storeProductionKg: List<Pair<String, Double>>,
        val expenseCategories: List<Pair<String, Double>>,
        val stockByValue: List<Pair<String, Double>>,
        val cashVsCredit: List<Pair<String, Double>>,
        val factoryByFormula: List<Pair<String, Double>>,
        val formulaCostPerKg: List<Pair<String, Double>>,
        val profitAnalysis: ProfitAnalysis,
        val owedAging: OwedAgingSummary,
        val audit: AuditReport,
        val recentActivity: List<RecentActivityItem>,
        val salesByCustomerTrend: List<Pair<String, List<Double>>> = emptyList(),
        val salesByStoreTrend: List<Pair<String, List<Double>>> = emptyList(),
        val prodByFormulaTrend: List<Pair<String, List<Double>>> = emptyList(),
        val prodByStoreTrend: List<Pair<String, List<Double>>> = emptyList(),
        val moveFormulas: List<Pair<String, Double>> = emptyList(),
        val moveStores: List<Pair<String, Double>> = emptyList()
    ) {
        val totalSalesRevenue: Double get() = salesValue
        val totalProducedKg: Double get() = prodKg
        val totalProductionProfit: Double get() = prodProfit
        val totalOperatingExpenses: Double get() = expenses
        val netOperatingProfit: Double get() = InputGuards.round2(prodProfit - expenses)
        val openCredits: Double get() = owedAging.total
        val findings: List<AuditFinding> get() = audit.findings
    }

    data class RecentActivityItem(
        val kind: String,
        val title: String,
        val sub: String,
        val amount: String,
        val tone: String, // info | good | warn | bad
        val goTab: String,
        val timestamp: Long
    )

    private val CHECK_META: Map<String, Pair<String, String>> = mapOf(
        "duplicate_id" to ("Same record id used twice" to "error"),
        "duplicate_record" to ("Identical records entered twice" to "warn"),
        "not_a_number" to ("Amount or quantity is not a number" to "error"),
        "bad_date" to ("Missing or invalid date" to "error"),
        "future_date" to ("Dated in the future" to "warn"),
        "sale_bad_qty" to ("Sale with zero or negative quantity" to "error"),
        "sale_negative_value" to ("Sale with negative value" to "error"),
        "sale_total_mismatch" to ("Sale total differs from quantity x price" to "error"),
        "sale_profit_mismatch" to ("Sale profit differs from value minus cost" to "warn"),
        "sale_no_cost" to ("Sale with no cost recorded" to "warn"),
        "sale_loss" to ("Sale recorded at a loss" to "warn"),
        "sale_cash_unreceived" to ("Cash sale not marked as received" to "warn"),
        "prod_no_formula" to ("Production with no formula (cost and profit are unreliable)" to "error"),
        "prod_bad_net" to ("Production with zero or negative weight" to "error"),
        "prod_profit_mismatch" to ("Production profit differs from sale minus cost" to "warn"),
        "txn_bad_type" to ("Payment is neither in nor out" to "error"),
        "txn_bad_amount" to ("Payment with a negative amount" to "error"),
        "txn_missing_entity" to ("Payment for an account that no longer exists" to "warn"),
        "exp_bad_amount" to ("Expense with zero or negative amount" to "error"),
        "inv_negative_qty" to ("Material with negative stock" to "error"),
        "inv_bad_cost" to ("Material with no cost" to "warn"),
        "col_unapplied" to ("Collection cash not applied to any sale" to "error"),
        "link_orphan" to ("Sale marked paid by a collection that no longer exists" to "error")
    )

    private fun monthLabel(ym: String): String {
        val parts = ym.split("-")
        if (parts.size != 2) return ym
        val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val m = (parts[1].toIntOrNull() ?: 1).coerceIn(1, 12)
        return "${names[m - 1]} ${parts[0].takeLast(2)}"
    }

    fun computeOverview(snapshot: WorkspaceSnapshot, windowMonths: Int = 6): OverviewKpiSummary {
        val span = if (windowMonths in listOf(3, 6, 12)) windowMonths else 6
        val monthKeys = DateUtils.recentMonthKeys(span).map { it.first }
        val keySet = monthKeys.toSet()
        val today = DateUtils.todayIso()

        val monthAcc = LinkedHashMap<String, MonthBucket>()
        monthKeys.forEach { k -> monthAcc[k] = MonthBucket(key = k, label = monthLabel(k)) }

        var todayProdKg = 0.0
        var todaySalesValue = 0.0
        var todayIn = 0.0
        var todayOut = 0.0
        var openCreditValue = 0.0
        var openCreditCount = 0

        val formulasMap = LinkedHashMap<String, Double>()
        val storesMap = LinkedHashMap<String, Double>()
        val moveFormulasMap = LinkedHashMap<String, Double>()
        val moveStoresMap = LinkedHashMap<String, Double>()
        val custValMap = LinkedHashMap<String, Double>()
        val custKgMap = LinkedHashMap<String, Double>()
        val expCatMap = LinkedHashMap<String, Double>()
        val storeSalesMap = LinkedHashMap<String, Double>()
        val factoryByFormula = LinkedHashMap<String, Double>()
        val recent = mutableListOf<RecentActivityItem>()

        val idxOf = monthKeys.withIndex().associate { (i, k) -> k to i }
        val fTrend = LinkedHashMap<String, DoubleArray>()
        val sTrend = LinkedHashMap<String, DoubleArray>()
        val cTrend = LinkedHashMap<String, DoubleArray>()
        val ssTrend = LinkedHashMap<String, DoubleArray>()

        fun bump(map: MutableMap<String, Double>, k: String, v: Double) {
            map[k] = (map[k] ?: 0.0) + v
        }

        fun tadd(map: MutableMap<String, DoubleArray>, name: String, ym: String, v: Double) {
            val idx = idxOf[ym] ?: return
            val arr = map.getOrPut(name) { DoubleArray(monthKeys.size) }
            arr[idx] += v
        }

        // Production
        snapshot.production.filter { it.deletedAt == null }.forEach { p ->
            val d = p.date.take(10)
            val ym = d.take(7)
            val m = monthAcc[ym]
            if (p.isReturn || p.isTransfer) {
                if (m != null) {
                    val label = if (p.isTransfer) "Transfers" else "Returns"
                    val stLabel = FormulaEngine.storeLabelFor(p.store, snapshot.stores).ifBlank { "Unassigned" }
                    bump(moveFormulasMap, label, abs(p.net))
                    bump(moveStoresMap, "$stLabel – $label", abs(p.net))
                    if (p.isReturn) monthAcc[ym] = m.copy(returnsKg = m.returnsKg + p.net)
                }
                return@forEach
            }
            if (m != null) {
                monthAcc[ym] = m.copy(
                    prodKg = m.prodKg + p.net,
                    prodProfit = m.prodProfit + p.profit,
                    prodValue = m.prodValue + p.totalSale,
                    prodCost = m.prodCost + p.totalCost
                )
                val fName = p.formulaName.ifBlank { "No formula" }
                val sName = FormulaEngine.storeLabelFor(p.store, snapshot.stores).ifBlank { "Unassigned" }
                bump(formulasMap, fName, p.net)
                bump(storesMap, sName, p.net)
                tadd(fTrend, fName, ym, p.net)
                tadd(sTrend, sName, ym, p.net)
            }
            if (d == today) todayProdKg += p.net
            recent.add(
                RecentActivityItem(
                    kind = "Production",
                    title = FormulaEngine.storeLabelFor(p.store, snapshot.stores).ifBlank { "Production" },
                    sub = p.formulaName,
                    amount = "${InputGuards.fmtNum(p.net, 1)} kg",
                    tone = "info",
                    goTab = "prod",
                    timestamp = p.timestamp
                )
            )
        }

        // Sales
        val owedByCust = LinkedHashMap<String, Double>()
        val oldestByCust = LinkedHashMap<String, Int>()
        var owedTotal = 0.0
        var owedCount = 0
        var d30 = 0.0
        var d60 = 0.0
        var d90 = 0.0
        var oldestDays = 0
        var cashPayTotal = 0.0
        var creditPayTotal = 0.0

        val activeSales = snapshot.sales.filter { it.deletedAt == null }
        activeSales.forEach { s ->
            val d = s.date.take(10)
            val ym = d.take(7)
            val m = monthAcc[ym]
            val valAmt = s.totalValue
            if (m != null) {
                monthAcc[ym] = m.copy(
                    salesValue = m.salesValue + valAmt,
                    salesProfit = m.salesProfit + s.profit,
                    salesCount = m.salesCount + 1
                )
                val cLabel = s.customerName.ifBlank { "Walk-in" }
                val stLabel = FormulaEngine.storeLabelFor(s.supplyStore, snapshot.stores).ifBlank { "Unassigned" }
                bump(custValMap, cLabel, valAmt)
                tadd(cTrend, cLabel, ym, valAmt)
                tadd(ssTrend, stLabel, ym, valAmt)
            }
            if (!FinanceEngine.isLedgerRow(s)) {
                val supplyYmd = s.supplyDate.ifBlank { s.date }.take(10)
                val sym = supplyYmd.take(7)
                monthAcc[sym]?.let { sm ->
                    monthAcc[sym] = sm.copy(soldKg = sm.soldKg + s.quantity)
                    bump(custKgMap, s.customerName.ifBlank { "Unknown" }, s.quantity)
                }
                if (ym in keySet) {
                    val stLabel = FormulaEngine.storeLabelFor(s.supplyStore, snapshot.stores).ifBlank { "Unassigned" }
                    bump(storeSalesMap, stLabel, valAmt)
                    if (s.paymentType.equals("CASH", ignoreCase = true)) cashPayTotal += valAmt
                    else creditPayTotal += valAmt
                }
                if (!s.paymentType.equals("CASH", ignoreCase = true) && !s.creditReceived && valAmt > 0.0) {
                    val age = if (d.length == 10) DateUtils.daysBetween(d, today).toInt().coerceAtLeast(0) else 0
                    val rem = max(0.0, valAmt - s.partialPaymentReceived)
                    owedTotal += rem
                    owedCount++
                    when {
                        age <= 30 -> d30 += rem
                        age <= 60 -> d60 += rem
                        else -> d90 += rem
                    }
                    oldestDays = max(oldestDays, age)
                    val cName = s.customerName.ifBlank { "Walk-in" }
                    bump(owedByCust, cName, rem)
                    oldestByCust[cName] = max(oldestByCust[cName] ?: 0, age)
                }
            }
            if (d == today) todaySalesValue += valAmt
            if (!s.paymentType.equals("CASH", ignoreCase = true) && !s.creditReceived) {
                openCreditValue += valAmt
                openCreditCount++
            }
            recent.add(
                RecentActivityItem(
                    kind = "Sale",
                    title = s.customerName.ifBlank { "Sale" },
                    sub = FormulaEngine.storeLabelFor(s.supplyStore, snapshot.stores),
                    amount = InputGuards.fmtNum(valAmt, 0),
                    tone = "good",
                    goTab = "sales",
                    timestamp = s.timestamp
                )
            )
        }

        // Transactions
        snapshot.transactions.filter { it.deletedAt == null }.forEach { t ->
            val d = t.date.take(10)
            val ym = d.take(7)
            val isIn = t.type.equals("IN", ignoreCase = true)
            val isCreditPurchase = isIn && t.isPayable
            if (!isCreditPurchase) {
                monthAcc[ym]?.let { m ->
                    monthAcc[ym] = if (isIn) m.copy(cashIn = m.cashIn + t.amount) else m.copy(cashOut = m.cashOut + t.amount)
                }
                if (d == today) {
                    if (isIn) todayIn += t.amount else todayOut += t.amount
                }
            }
            recent.add(
                RecentActivityItem(
                    kind = if (isCreditPurchase) "Credit purchase" else if (isIn) "Cash in" else "Cash out",
                    title = t.entityName.ifBlank { "Payment" },
                    sub = t.description,
                    amount = InputGuards.fmtNum(t.amount, 0),
                    tone = if (isIn) "good" else "bad",
                    goTab = "payments",
                    timestamp = t.timestamp
                )
            )
        }

        // Expenses
        snapshot.expenses.filter { it.deletedAt == null }.forEach { e ->
            val d = e.date.take(10)
            val ym = d.take(7)
            monthAcc[ym]?.let { m ->
                monthAcc[ym] = m.copy(expenses = m.expenses + e.amount)
                bump(expCatMap, e.category.ifBlank { "Other" }, e.amount)
            }
            recent.add(
                RecentActivityItem(
                    kind = "Expense",
                    title = e.name.ifBlank { e.category.ifBlank { "Expense" } },
                    sub = e.category,
                    amount = InputGuards.fmtNum(e.amount, 0),
                    tone = "warn",
                    goTab = "payments",
                    timestamp = e.timestamp
                )
            )
        }

        // Factory
        snapshot.factory.filter { it.deletedAt == null }.forEach { f ->
            val ym = f.date.take(7)
            monthAcc[ym]?.let { m ->
                monthAcc[ym] = m.copy(factoryUnits = m.factoryUnits + f.units)
                bump(factoryByFormula, f.formulaName.ifBlank { "No formula" }, f.units)
            }
        }

        // Inventory
        val liveMaterials = FinanceEngine.materialRecords(snapshot.inventory)
        var stockValue = 0.0
        var stockItems = 0
        val stockRows = mutableListOf<Pair<String, Double>>()
        val outOfStock = mutableListOf<String>()
        liveMaterials.forEach { m ->
            if (m.quantity > 0.0) {
                val v = m.quantity * m.cost
                stockValue += v
                stockItems++
                stockRows.add(m.name.ifBlank { "Material" } to InputGuards.round2(v))
            } else {
                outOfStock.add(m.name.ifBlank { "Material" })
            }
        }

        val formulaCosts = snapshot.formulas.mapNotNull { f ->
            val desc = FormulaEngine.describeFormula(f, snapshot.formulas, snapshot.inventory)
            if (desc.weight > 0.0 && desc.perUnit > 0.0) {
                val lbl = if (f.ingredients.any { FormulaEngine.isLink(it) }) "${f.name} (linked)" else f.name
                lbl to InputGuards.round2(desc.perUnit / desc.weight)
            } else null
        }.sortedByDescending { it.second }

        val buckets = monthAcc.values.toList()
        val prodKg = buckets.sumOf { it.prodKg }
        val prodProfit = buckets.sumOf { it.prodProfit }
        val prodValue = buckets.sumOf { it.prodValue }
        val prodCost = buckets.sumOf { it.prodCost }
        val soldKg = buckets.sumOf { it.soldKg }
        val returnsKg = buckets.sumOf { it.returnsKg }
        val salesValue = buckets.sumOf { it.salesValue }
        val salesProfit = buckets.sumOf { it.salesProfit }
        val salesCount = buckets.sumOf { it.salesCount }
        val cashIn = buckets.sumOf { it.cashIn }
        val cashOut = buckets.sumOf { it.cashOut }
        val expenses = buckets.sumOf { it.expenses }

        val profitAnalysis = analyseProfit(activeSales, keySet, snapshot)
        val audit = auditWorkspace(snapshot)

        val topOwedCust = owedByCust.entries
            .sortedByDescending { it.value }
            .take(8)
            .map { Triple(it.key, InputGuards.round2(it.value), oldestByCust[it.key] ?: 0) }

        return OverviewKpiSummary(
            windowMonths = span,
            months = buckets,
            prodKg = InputGuards.round2(prodKg),
            prodProfit = InputGuards.round2(prodProfit),
            prodValue = InputGuards.round2(prodValue),
            prodCost = InputGuards.round2(prodCost),
            marginPct = if (prodValue > 0.0) (prodProfit / prodValue) * 100.0 else 0.0,
            soldKg = InputGuards.round2(soldKg),
            returnsKg = InputGuards.round2(returnsKg),
            remainingKg = InputGuards.round2(prodKg + returnsKg - soldKg),
            salesValue = InputGuards.round2(salesValue),
            salesProfit = InputGuards.round2(salesProfit),
            salesCount = salesCount,
            cashIn = InputGuards.round2(cashIn),
            cashOut = InputGuards.round2(cashOut),
            netCash = InputGuards.round2(cashIn - cashOut),
            expenses = InputGuards.round2(expenses),
            openCreditValue = InputGuards.round2(openCreditValue),
            openCreditCount = openCreditCount,
            stockValue = InputGuards.round2(stockValue),
            stockItemsCount = stockItems,
            outOfStockNames = outOfStock,
            todayProdKg = InputGuards.round2(todayProdKg),
            todaySalesValue = InputGuards.round2(todaySalesValue),
            todayIn = InputGuards.round2(todayIn),
            todayOut = InputGuards.round2(todayOut),
            storeSalesBreakdown = storeSalesMap.entries.sortedByDescending { it.value }.map { it.key to InputGuards.round2(it.value) },
            topCustomersValue = custValMap.entries.sortedByDescending { it.value }.take(6).map { it.key to InputGuards.round2(it.value) },
            topCustomersKg = custKgMap.entries.sortedByDescending { it.value }.take(6).map { it.key to InputGuards.round2(it.value) },
            formulaProductionKg = formulasMap.entries.sortedByDescending { it.value }.take(6).map { it.key to InputGuards.round2(it.value) },
            storeProductionKg = storesMap.entries.sortedByDescending { it.value }.take(5).map { it.key to InputGuards.round2(it.value) },
            expenseCategories = expCatMap.entries.sortedByDescending { it.value }.take(5).map { it.key to InputGuards.round2(it.value) },
            stockByValue = stockRows.sortedByDescending { it.second }.take(6),
            cashVsCredit = listOfNotNull(
                if (cashPayTotal > 0.0) "Cash" to InputGuards.round2(cashPayTotal) else null,
                if (creditPayTotal > 0.0) "Credit" to InputGuards.round2(creditPayTotal) else null
            ),
            factoryByFormula = factoryByFormula.entries.sortedByDescending { it.value }.map { it.key to InputGuards.round2(it.value) },
            formulaCostPerKg = formulaCosts,
            profitAnalysis = profitAnalysis,
            owedAging = OwedAgingSummary(
                total = InputGuards.round2(owedTotal),
                count = owedCount,
                d30 = InputGuards.round2(d30),
                d60 = InputGuards.round2(d60),
                d90 = InputGuards.round2(d90),
                oldestDays = oldestDays,
                topCustomers = topOwedCust
            ),
            audit = audit,
            recentActivity = recent.sortedByDescending { it.timestamp }.take(10),
            salesByCustomerTrend = cTrend.entries
                .sortedByDescending { it.value.sum() }
                .take(5)
                .map { it.key to it.value.map { v -> InputGuards.round2(v) } },
            salesByStoreTrend = ssTrend.entries
                .sortedByDescending { it.value.sum() }
                .take(5)
                .map { it.key to it.value.map { v -> InputGuards.round2(v) } },
            prodByFormulaTrend = fTrend.entries
                .sortedByDescending { it.value.sum() }
                .take(5)
                .map { it.key to it.value.map { v -> InputGuards.round2(v) } },
            prodByStoreTrend = sTrend.entries
                .sortedByDescending { it.value.sum() }
                .take(5)
                .map { it.key to it.value.map { v -> InputGuards.round2(v) } },
            moveFormulas = moveFormulasMap.entries.sortedByDescending { it.value }.map { it.key to InputGuards.round2(it.value) },
            moveStores = moveStoresMap.entries.sortedByDescending { it.value }.map { it.key to InputGuards.round2(it.value) }
        )
    }

    private fun analyseProfit(
        activeSales: List<SalesRecord>,
        monthKeys: Set<String>,
        snapshot: WorkspaceSnapshot
    ): ProfitAnalysis {
        val inRange = activeSales.filter { it.date.take(7) in monthKeys }
        var losingCount = 0
        var losingSum = 0.0
        var gainingCount = 0
        var gainingSum = 0.0
        var net = 0.0
        var qty = 0.0
        var value = 0.0
        var cost = 0.0
        var noCostCount = 0
        var noCostValue = 0.0
        var mismatchCount = 0
        var mismatchSum = 0.0
        var zeroQtyCount = 0
        val byCust = LinkedHashMap<String, Double>()
        val byStore = LinkedHashMap<String, Double>()
        val byMonth = LinkedHashMap<String, Double>()

        inRange.forEach { x ->
            net += x.profit
            qty += x.quantity
            value += x.totalValue
            cost += x.totalCost
            if (x.profit < 0.0) {
                losingCount++
                losingSum += x.profit
            } else {
                gainingCount++
                gainingSum += x.profit
            }
            if (!FinanceEngine.isLedgerRow(x)) {
                if (x.totalCost <= 0.0 && x.totalValue > 0.0) {
                    noCostCount++
                    noCostValue += x.totalValue
                }
                if (abs(x.profit - (x.totalValue - x.totalCost)) > 1.0) {
                    mismatchCount++
                    mismatchSum += x.profit - (x.totalValue - x.totalCost)
                }
                if (x.quantity <= 0.0) zeroQtyCount++
            }
            val custName = x.customerName.ifBlank { "Walk-in" }
            val storeName = FormulaEngine.storeLabelFor(x.supplyStore, snapshot.stores).ifBlank { "Unassigned" }
            val ym = x.date.take(7)
            byCust[custName] = (byCust[custName] ?: 0.0) + x.profit
            byStore[storeName] = (byStore[storeName] ?: 0.0) + x.profit
            if (ym.length == 7) byMonth[ym] = (byMonth[ym] ?: 0.0) + x.profit
        }
        val worst = inRange.filter { it.profit < 0.0 }.sortedBy { it.profit }.take(8).map { x ->
            WorstSaleLoss(
                id = x.id,
                date = x.date.take(10),
                customer = x.customerName.ifBlank { "Walk-in" },
                store = FormulaEngine.storeLabelFor(x.supplyStore, snapshot.stores),
                qty = x.quantity,
                price = if (x.quantity > 0.0) x.totalValue / x.quantity else 0.0,
                cost = if (x.quantity > 0.0) x.totalCost / x.quantity else 0.0,
                profit = InputGuards.round2(x.profit)
            )
        }
        return ProfitAnalysis(
            count = inRange.size,
            losingCount = losingCount,
            losingSum = InputGuards.round2(losingSum),
            gainingCount = gainingCount,
            gainingSum = InputGuards.round2(gainingSum),
            net = InputGuards.round2(net),
            avgPrice = if (qty > 0.0) value / qty else 0.0,
            avgCost = if (qty > 0.0) cost / qty else 0.0,
            worstLosses = worst,
            byCustomer = byCust.entries.filter { it.value < 0.0 }.sortedBy { it.value }.take(6).map { it.key to InputGuards.round2(it.value) },
            byStore = byStore.entries.filter { it.value < 0.0 }.sortedBy { it.value }.take(5).map { it.key to InputGuards.round2(it.value) },
            byMonth = byMonth.entries.sortedBy { it.key }.map { monthLabel(it.key) to InputGuards.round2(it.value) },
            noCostCount = noCostCount,
            noCostValue = InputGuards.round2(noCostValue),
            mismatchCount = mismatchCount,
            mismatchSum = InputGuards.round2(mismatchSum),
            zeroQtyCount = zeroQtyCount
        )
    }

    fun auditWorkspace(snapshot: WorkspaceSnapshot): AuditReport {
        val today = DateUtils.todayIso()
        val findings = mutableListOf<AuditFinding>()
        val counts = LinkedHashMap<String, Int>()
        val entityIds = snapshot.entities.filter { it.deletedAt == null }.map { it.id }.toSet()
        val hasEntities = entityIds.isNotEmpty()
        var scannedTotal = 0

        fun flag(
            col: String,
            id: String,
            title: String,
            label: String,
            date: String,
            code: String,
            detail: String = "",
            impact: Double = 0.0
        ) {
            counts[code] = (counts[code] ?: 0) + 1
            val (checkLabel, severity) = CHECK_META[code] ?: (code to "warn")
            val reason = if (detail.isNotBlank()) "$checkLabel ($detail)" else checkLabel
            val existing = findings.indexOfFirst { it.collection == col && it.recordId == id }
            if (existing >= 0) {
                val cur = findings[existing]
                findings[existing] = cur.copy(
                    codes = if (code in cur.codes) cur.codes else cur.codes + code,
                    severity = if (severity == "error") "error" else cur.severity,
                    reasons = cur.reasons + reason,
                    impact = if (abs(impact) > abs(cur.impact)) impact else cur.impact
                )
            } else {
                findings.add(
                    AuditFinding(
                        id = "${col}_${code}_$id",
                        collection = col,
                        recordId = id,
                        code = code,
                        codes = listOf(code),
                        severity = severity,
                        title = title,
                        label = label,
                        date = date,
                        reasons = listOf(reason),
                        impact = impact
                    )
                )
            }
        }

        // Sales
        val liveSales = snapshot.sales.filter { it.deletedAt == null }
        scannedTotal += liveSales.size
        val saleIdCounts = liveSales.groupingBy { it.id }.eachCount()
        val saleSig = liveSales.filter { !FinanceEngine.isLedgerRow(it) }
            .groupBy { listOf(it.customerName, it.date, it.time, it.quantity.toString(), it.totalValue.toString()).joinToString("|") }

        liveSales.forEach { s ->
            val d = s.date.take(10)
            val title = s.customerName.ifBlank { "Sale" }
            if (d.length != 10) flag("sales", s.id, title, "Sale", "", "bad_date")
            else if (d > today) flag("sales", s.id, title, "Sale", d, "future_date", d)
            if ((saleIdCounts[s.id] ?: 0) > 1) flag("sales", s.id, title, "Sale", d, "duplicate_id", s.id)
            if (!FinanceEngine.isLedgerRow(s)) {
                if (s.quantity <= 0.0) flag("sales", s.id, title, "Sale", d, "sale_bad_qty", s.quantity.toString())
                if (s.totalValue < 0.0) flag("sales", s.id, title, "Sale", d, "sale_negative_value", InputGuards.fmtNum(s.totalValue), s.totalValue)
                if (s.unitPrice > 0.0 && s.quantity > 0.0 && abs(s.unitPrice * s.quantity - s.totalValue) > max(1.0, s.totalValue * 0.005)) {
                    flag("sales", s.id, title, "Sale", d, "sale_total_mismatch", "${InputGuards.fmtNum(s.quantity)} x ${InputGuards.fmtNum(s.unitPrice)} vs ${InputGuards.fmtNum(s.totalValue)}", s.unitPrice * s.quantity - s.totalValue)
                }
                if (abs(s.profit - (s.totalValue - s.totalCost)) > 1.0) {
                    flag("sales", s.id, title, "Sale", d, "sale_profit_mismatch", "saved ${InputGuards.fmtNum(s.profit)} vs ${InputGuards.fmtNum(s.totalValue - s.totalCost)}", s.profit - (s.totalValue - s.totalCost))
                }
                if (s.totalCost <= 0.0 && s.totalValue > 0.0) {
                    flag("sales", s.id, title, "Sale", d, "sale_no_cost", "value ${InputGuards.fmtNum(s.totalValue)}", s.totalValue)
                }
                if (s.paymentType.equals("CASH", ignoreCase = true) && !s.creditReceived) {
                    flag("sales", s.id, title, "Sale", d, "sale_cash_unreceived")
                }
            }
        }
        saleSig.values.filter { it.size > 1 }.forEach { grp ->
            grp.forEach { s ->
                flag("sales", s.id, s.customerName.ifBlank { "Sale" }, "Sale", s.date.take(10), "duplicate_record", "${grp.size} copies", s.totalValue)
            }
        }

        // Collection links in sales & rep
        listOf("sales" to liveSales, "rep" to snapshot.repRecords.filter { it.deletedAt == null }).forEach { (col, list) ->
            val honor = FinanceEngine.allocHonor(list)
            val liveCols = list.filter { FinanceEngine.isCollectionRow(it) }.map { it.id }.toSet()
            list.forEach { r ->
                val title = r.customerName.ifBlank { "Record" }
                val lbl = if (col == "rep") "Rep record" else "Sale"
                if (FinanceEngine.isCollectionRow(r)) {
                    val left = FinanceEngine.collectionUnapplied(r, honor)
                    if (left > 0.01) flag(col, r.id, title, lbl, r.date.take(10), "col_unapplied", "${InputGuards.fmtNum(left)} unapplied", left)
                } else if (r.collectionAllocs.isNotEmpty()) {
                    val orphanAmt = r.collectionAllocs.filterKeys { it !in liveCols }.values.sumOf { max(0.0, it) }
                    if (orphanAmt > 0.01) flag(col, r.id, title, lbl, r.date.take(10), "link_orphan", "${InputGuards.fmtNum(orphanAmt)} linked to missing collection", orphanAmt)
                }
            }
        }

        // Production
        val liveProd = snapshot.production.filter { it.deletedAt == null }
        scannedTotal += liveProd.size
        liveProd.forEach { p ->
            val d = p.date.take(10)
            val title = FormulaEngine.storeLabelFor(p.store, snapshot.stores).ifBlank { p.formulaName.ifBlank { "Production" } }
            if (d.length != 10) flag("production", p.id, title, "Production", "", "bad_date")
            else if (d > today) flag("production", p.id, title, "Production", d, "future_date", d)
            if (!p.isReturn && !p.isTransfer) {
                if (p.net <= 0.0) flag("production", p.id, title, "Production", d, "prod_bad_net", p.net.toString())
                if (p.formulaName.isBlank()) flag("production", p.id, title, "Production", d, "prod_no_formula", "${InputGuards.fmtNum(p.net)} kg", p.profit)
                if (p.totalSale != 0.0 && abs(p.profit - (p.totalSale - p.totalCost)) > 1.0) {
                    flag("production", p.id, title, "Production", d, "prod_profit_mismatch", "saved ${InputGuards.fmtNum(p.profit)}", p.profit - (p.totalSale - p.totalCost))
                }
            }
        }

        // Transactions
        val liveTx = snapshot.transactions.filter { it.deletedAt == null }
        scannedTotal += liveTx.size
        liveTx.forEach { t ->
            val d = t.date.take(10)
            val title = t.entityName.ifBlank { "Payment" }
            if (d.length != 10) flag("transactions", t.id, title, "Payment", "", "bad_date")
            else if (d > today) flag("transactions", t.id, title, "Payment", d, "future_date", d)
            if (t.type != "IN" && t.type != "OUT") flag("transactions", t.id, title, "Payment", d, "txn_bad_type", t.type)
            if (t.amount < 0.0) flag("transactions", t.id, title, "Payment", d, "txn_bad_amount", t.amount.toString())
            if (hasEntities && t.entityId.isNotBlank() && t.entityId !in entityIds) {
                flag("transactions", t.id, title, "Payment", d, "txn_missing_entity", t.entityName.ifBlank { t.entityId }, t.amount)
            }
        }

        // Expenses
        val liveExp = snapshot.expenses.filter { it.deletedAt == null }
        scannedTotal += liveExp.size
        liveExp.forEach { e ->
            val d = e.date.take(10)
            val title = e.name.ifBlank { e.category.ifBlank { "Expense" } }
            if (d.length != 10) flag("expenses", e.id, title, "Expense", "", "bad_date")
            if (e.amount <= 0.0) flag("expenses", e.id, title, "Expense", d, "exp_bad_amount", e.amount.toString())
        }

        // Inventory materials
        val liveInv = FinanceEngine.materialRecords(snapshot.inventory)
        scannedTotal += liveInv.size
        liveInv.forEach { m ->
            val title = m.name.ifBlank { "Material" }
            if (m.quantity < 0.0) flag("inventory", m.id, title, "Material", "", "inv_negative_qty", m.quantity.toString(), m.quantity * m.cost)
            if (m.cost <= 0.0 && m.quantity > 0.0) flag("inventory", m.id, title, "Material", "", "inv_bad_cost")
        }

        findings.sortWith(
            compareBy<AuditFinding> { if (it.severity == "error") 0 else 1 }
                .thenByDescending { abs(it.impact) }
                .thenByDescending { it.date }
        )

        val byCheck = counts.map { (code, count) ->
            val (label, sev) = CHECK_META[code] ?: (code to "warn")
            AuditCheckSummary(code = code, label = label, severity = sev, count = count)
        }.sortedWith(
            compareBy<AuditCheckSummary> { if (it.severity == "error") 0 else 1 }
                .thenByDescending { it.count }
        )

        return AuditReport(
            findings = findings,
            byCheck = byCheck,
            scannedTotal = scannedTotal,
            errors = findings.count { it.severity == "error" },
            warnings = findings.count { it.severity != "error" }
        )
    }
}
