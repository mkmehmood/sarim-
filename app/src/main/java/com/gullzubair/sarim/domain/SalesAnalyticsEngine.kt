package com.gullzubair.sarim.domain

import com.gullzubair.sarim.data.model.CalculatorRecord
import com.gullzubair.sarim.data.model.CustomerRecord
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.ReturnRecord
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.util.StatementRow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure domain calculations for the Sales tab, porting web `utilities-sales.js`,
 * `utilities-payments.js`, `customers.js`, `factory-core.js`, `inventory-note.js`,
 * and `link-guards.js`.
 */
object SalesAnalyticsEngine {

    enum class SalesChartPeriod(val key: String, val label: String) {
        WEEK("week", "Weekly"),
        MONTH("month", "Monthly"),
        YEAR("year", "Yearly"),
        ALL("all", "All Times")
    }

    enum class SalesSummaryPeriod(val key: String, val label: String, val title: String) {
        DAY("day", "Daily", "Daily Sales"),
        WEEK("week", "Weekly", "Weekly Sales"),
        MONTH("month", "Monthly", "Monthly Sales"),
        YEAR("year", "Yearly", "Yearly Sales"),
        ALL("all", "All Time", "All Time Sales")
    }

    data class DateStockTileModel(
        val storeName: String,
        val producedKg: Double,
        val returnsKg: Double,
        val soldKg: Double,
        val totalInKg: Double,
        val availableKg: Double,
        val leftAfterSaleKg: Double,
        val usedPercent: Double,
        val tone: String, // good | low | short
        val note: String,
        val summary: String
    )

    data class GoodsCreditPlan(
        val value: Double,
        val raw: Double,
        val capped: Boolean
    )

    data class RepReturnPendingInfo(
        val pendingDeliveredKg: Double,
        val alreadyReturnedKg: Double,
        val remainingDeliverableKg: Double
    )

    data class CustomerDirectoryItem(
        val name: String,
        val credit: Double,
        val quantity: Double,
        val lastSaleDate: String,
        val phone: String,
        val address: String,
        val ledgerPage: String,
        val customSalePrice: Double,
        val photoUri: String?
    )

    data class CustomerDirectorySummary(
        val items: List<CustomerDirectoryItem>,
        val totalOutstandingCredit: Double,
        val totalSoldKg: Double,
        val totalCount: Int
    )

    data class SalesChartData(
        val labels: List<String>,
        val cashValues: List<Double>,
        val creditValues: List<Double>,
        val totalCash: Double,
        val totalCredit: Double
    )

    data class SalesPeriodSummary(
        val quantityKg: Double,
        val totalValue: Double,
        val cashAmount: Double,
        val pendingCreditAmount: Double,
        val netProfit: Double,
        val marginPercent: Int?
    )

    fun matchesStore(candidate: String, store: StoreConfig): Boolean {
        val c = candidate.trim()
        if (c.isBlank()) return false
        return c.equals(store.key, ignoreCase = true) || c.equals(store.name, ignoreCase = true)
    }

    fun isAdminSaleProfile(profile: String): Boolean {
        val p = profile.trim()
        return p.isBlank() || p.equals("admin", ignoreCase = true)
    }

    fun effectiveCustomerRate(
        customerName: String,
        store: StoreConfig?,
        customers: List<CustomerRecord>,
        defaultPrices: Map<String, Double> = emptyMap()
    ): Double {
        val clean = customerName.trim()
        if (clean.isNotBlank()) {
            val custRate = customers.firstOrNull {
                it.deletedAt == null && it.name.equals(clean, ignoreCase = true)
            }?.customSalePrice ?: 0.0
            if (custRate > 0.0) return InputGuards.round2(custRate)
        }
        val storeRate = store?.salePrice ?: 0.0
        if (storeRate > 0.0) return InputGuards.round2(storeRate)
        val fallback = store?.let { defaultPrices[it.key] } ?: 0.0
        return InputGuards.round2(fallback)
    }

    fun computeDateStockTile(
        snapshot: WorkspaceSnapshot,
        store: StoreConfig?,
        supplyDateIso: String,
        requestedQtyKg: Double,
        excludeSaleId: String? = null
    ): DateStockTileModel {
        if (store == null || supplyDateIso.length < 10) {
            return DateStockTileModel(
                storeName = store?.name ?: "",
                producedKg = 0.0,
                returnsKg = 0.0,
                soldKg = 0.0,
                totalInKg = 0.0,
                availableKg = 0.0,
                leftAfterSaleKg = 0.0,
                usedPercent = 0.0,
                tone = "short",
                note = "Select store and date",
                summary = "0.00 kg in stock"
            )
        }
        val day = supplyDateIso.take(10)
        val produced = snapshot.production.filter {
            it.deletedAt == null && !it.isReturn && it.date.take(10) == day && matchesStore(it.store, store)
        }.sumOf { it.net }
        val returned = snapshot.returns.filter {
            it.deletedAt == null && it.date.take(10) == day && matchesStore(it.store, store)
        }.sumOf { it.quantity }
        val sold = snapshot.sales.filter {
            it.deletedAt == null &&
                it.id != excludeSaleId &&
                !FinanceEngine.isLedgerRow(it) &&
                it.supplyDate.ifBlank { it.date }.take(10) == day &&
                matchesStore(it.supplyStore, store)
        }.sumOf { it.quantity }

        val totalIn = InputGuards.round3(produced + returned)
        val avail = InputGuards.round3(totalIn - sold)
        val have = max(0.0, avail)
        val want = max(0.0, requestedQtyKg)
        val left = InputGuards.round3(max(0.0, avail - want))
        val tone = when {
            avail - want < -1e-6 -> "short"
            avail <= 0.0 && want <= 0.0 -> "short"
            want > 0.0 && left <= max(5.0, avail * 0.15) -> "low"
            avail > 0.0 && avail < 10.0 && want == 0.0 -> "low"
            else -> "good"
        }
        val note = when (tone) {
            "short" -> if (want > avail) "Over by ${InputGuards.formatKg(want - max(0.0, avail))}" else "No stock on $day"
            "low" -> "Only ${InputGuards.formatKg(left)} left"
            else -> if (want > 0.0) "${InputGuards.formatKg(left)} left" else "Ready to sell"
        }
        val usedPct = if (want > 0.0) {
            if (have > 0.0) min(100.0, (want / have) * 100.0) else 100.0
        } else 0.0

        return DateStockTileModel(
            storeName = store.name,
            producedKg = InputGuards.round3(produced),
            returnsKg = InputGuards.round3(returned),
            soldKg = InputGuards.round3(sold),
            totalInKg = totalIn,
            availableKg = avail,
            leftAfterSaleKg = left,
            usedPercent = (usedPct * 10.0).roundToInt() / 10.0,
            tone = tone,
            note = note,
            summary = "${InputGuards.formatKg(have)} in stock. $note."
        )
    }

    fun computeGoodsCreditUnitPrice(
        customerName: String,
        snapshot: WorkspaceSnapshot,
        excludeSaleId: String? = null,
        fallbackStore: StoreConfig? = null
    ): Double {
        val lname = customerName.trim().lowercase(Locale.ROOT)
        if (lname.isBlank()) return 0.0
        val creditSales = snapshot.sales.filter {
            it.deletedAt == null &&
                !it.isMerged &&
                isAdminSaleProfile(it.currentRepProfile) &&
                it.id != excludeSaleId &&
                it.customerName.trim().lowercase(Locale.ROOT) == lname &&
                it.paymentType == "CREDIT" &&
                it.transactionType != "OLD_DEBT" &&
                !it.isRepTransfer &&
                it.quantity > 0.0
        }

        fun weightedAvg(items: List<SalesRecord>): Double {
            var qSum = 0.0
            var vSum = 0.0
            items.forEach { s ->
                val v = FinanceEngine.effectiveSaleValue(s, snapshot.customers, snapshot.stores)
                if (s.quantity > 0.0 && v > 0.0) {
                    qSum += s.quantity
                    vSum += v
                }
            }
            return if (qSum > 0.0) InputGuards.round2(vSum / qSum) else 0.0
        }

        val unpaidAvg = weightedAvg(creditSales.filter { !it.creditReceived })
        if (unpaidAvg > 0.0) return unpaidAvg

        val latest = creditSales.maxWithOrNull(compareBy<SalesRecord> { it.date }.thenBy { it.timestamp })
        if (latest != null) {
            val latestRate = weightedAvg(listOf(latest))
            if (latestRate > 0.0) return latestRate
        }
        val firstStore = fallbackStore ?: snapshot.stores.firstOrNull { it.deletedAt == null && it.active }
        return effectiveCustomerRate(customerName, firstStore, snapshot.customers, snapshot.settings.defaultStorePrices)
    }

    fun planGoodsCredit(qtyKg: Double, unitPrice: Double, outstanding: Double?): GoodsCreditPlan {
        val raw = InputGuards.round2(max(0.0, qtyKg) * max(0.0, unitPrice))
        if (outstanding == null || outstanding < 0.0) {
            return GoodsCreditPlan(value = raw, raw = raw, capped = false)
        }
        val cap = InputGuards.round2(max(0.0, outstanding))
        return if (raw > cap) GoodsCreditPlan(value = cap, raw = raw, capped = true)
        else GoodsCreditPlan(value = raw, raw = raw, capped = false)
    }

    fun computeRepReturnPendingInfo(
        repName: String,
        snapshot: WorkspaceSnapshot,
        excludeReturnId: String? = null
    ): RepReturnPendingInfo {
        val cleanRep = repName.trim()
        if (cleanRep.isBlank() || cleanRep.equals("NONE", true)) {
            return RepReturnPendingInfo(0.0, 0.0, 0.0)
        }
        val reconciledSalesIds = snapshot.calculator
            .filter { it.deletedAt == null }
            .flatMap { it.linkedSalesIds }
            .toSet()
        val reconciledReturnIds = FinanceEngine.reconciledRepReturnIds(snapshot.calculator)

        val pendingDelivered = snapshot.sales.filter { s ->
            s.deletedAt == null &&
                isAdminSaleProfile(s.currentRepProfile) &&
                s.customerName.equals(cleanRep, ignoreCase = true) &&
                s.transactionType != "OLD_DEBT" &&
                s.paymentType == "CREDIT" &&
                !s.creditReceived &&
                s.id !in reconciledSalesIds
        }.sumOf { it.quantity }

        val alreadyBack = snapshot.sales.filter { r ->
            r.deletedAt == null &&
                r.isRepReturn &&
                r.id != excludeReturnId &&
                isAdminSaleProfile(r.currentRepProfile) &&
                r.customerName.equals(cleanRep, ignoreCase = true) &&
                r.id !in reconciledReturnIds
        }.sumOf { it.expiredQty + it.returnedQty }

        return RepReturnPendingInfo(
            pendingDeliveredKg = InputGuards.round3(pendingDelivered),
            alreadyReturnedKg = InputGuards.round3(alreadyBack),
            remainingDeliverableKg = InputGuards.round3(max(0.0, pendingDelivered - alreadyBack))
        )
    }

    fun computeCustomerOutstanding(
        customerName: String,
        snapshot: WorkspaceSnapshot,
        excludeSaleId: String? = null
    ): Double {
        val key = customerName.trim().lowercase(Locale.ROOT)
        if (key.isBlank()) return 0.0
        val reconciled = FinanceEngine.reconciledRepReturnIds(snapshot.calculator)
        val rows = snapshot.sales.filter {
            it.deletedAt == null &&
                it.id != excludeSaleId &&
                isAdminSaleProfile(it.currentRepProfile) &&
                it.customerName.trim().lowercase(Locale.ROOT) == key
        }
        val honor = FinanceEngine.allocHonor(rows)
        val hasOldDebtTx = rows.any { it.transactionType == "OLD_DEBT" }
        val opening = if (hasOldDebtTx) 0.0 else snapshot.customers
            .filter { it.deletedAt == null && it.name.trim().lowercase(Locale.ROOT) == key }
            .sumOf { it.oldDebit }
        val net = rows.sumOf { s ->
            FinanceEngine.ledgerDelta(
                s,
                FinanceEngine.effectiveSaleValue(s, snapshot.customers, snapshot.stores),
                reconciled,
                honor
            )
        }
        return InputGuards.round2(opening + net)
    }

    fun computeCustomerTotalQty(customerName: String, snapshot: WorkspaceSnapshot): Double {
        val key = customerName.trim().lowercase(Locale.ROOT)
        if (key.isBlank()) return 0.0
        return InputGuards.round2(
            snapshot.sales.filter {
                it.deletedAt == null &&
                    isAdminSaleProfile(it.currentRepProfile) &&
                    it.customerName.trim().lowercase(Locale.ROOT) == key
            }.sumOf { it.quantity }
        )
    }

    fun computeCustomerDirectory(
        snapshot: WorkspaceSnapshot,
        searchQuery: String
    ): CustomerDirectorySummary {
        val reconciled = FinanceEngine.reconciledRepReturnIds(snapshot.calculator)
        val aliveSales = snapshot.sales.filter { it.deletedAt == null && isAdminSaleProfile(it.currentRepProfile) }
        val aliveCusts = snapshot.customers.filter { it.deletedAt == null }
        val honor = FinanceEngine.allocHonor(aliveSales)

        val statsMap = LinkedHashMap<String, Triple<Double, Double, String>>() // key -> (credit, qty, lastDate)
        val displayNames = LinkedHashMap<String, String>()
        val salePhones = LinkedHashMap<String, String>()

        aliveSales.forEach { s ->
            val rawName = s.customerName.trim()
            if (rawName.isBlank()) return@forEach
            val key = rawName.lowercase(Locale.ROOT)
            displayNames.putIfAbsent(key, rawName)
            if (s.customerPhone.isNotBlank()) salePhones.putIfAbsent(key, s.customerPhone)
            val prev = statsMap[key] ?: Triple(0.0, 0.0, "")
            val gross = FinanceEngine.effectiveSaleValue(s, aliveCusts, snapshot.stores)
            val delta = FinanceEngine.ledgerDelta(s, gross, reconciled, honor)
            val nextDate = if (s.date > prev.third) s.date else prev.third
            statsMap[key] = Triple(
                InputGuards.round2(prev.first + delta),
                InputGuards.round3(prev.second + s.quantity),
                nextDate
            )
        }

        val custByKey = LinkedHashMap<String, CustomerRecord>()
        aliveCusts.forEach { c ->
            val key = c.name.trim().lowercase(Locale.ROOT)
            if (key.isBlank()) return@forEach
            custByKey[key] = c
            displayNames.putIfAbsent(key, c.name.trim())
            if (!statsMap.containsKey(key)) {
                statsMap[key] = Triple(InputGuards.round2(c.oldDebit), 0.0, "")
            } else {
                val hasOldDebtTx = aliveSales.any {
                    it.customerName.trim().lowercase(Locale.ROOT) == key && it.transactionType == "OLD_DEBT"
                }
                if (!hasOldDebtTx && c.oldDebit != 0.0) {
                    val prev = statsMap[key]!!
                    statsMap[key] = prev.copy(first = InputGuards.round2(prev.first + c.oldDebit))
                }
            }
        }

        val q = searchQuery.trim().lowercase(Locale.ROOT)
        val items = displayNames.keys.map { key ->
            val c = custByKey[key]
            val st = statsMap[key] ?: Triple(0.0, 0.0, "")
            val photoUri = c?.id?.takeIf { it.isNotBlank() }?.let { cid ->
                snapshot.photos["cust:$cid"]?.takeIf { !it.deleted }?.dataUri
            }
            CustomerDirectoryItem(
                name = c?.name?.takeIf { it.isNotBlank() } ?: displayNames[key].orEmpty(),
                credit = InputGuards.round2(st.first),
                quantity = InputGuards.round2(st.second),
                lastSaleDate = st.third,
                phone = c?.phone?.ifBlank { salePhones[key].orEmpty() } ?: salePhones[key].orEmpty(),
                address = c?.address.orEmpty(),
                ledgerPage = c?.ledgerPage.orEmpty(),
                customSalePrice = c?.customSalePrice ?: 0.0,
                photoUri = photoUri
            )
        }.filter { item ->
            q.isBlank() ||
                item.name.lowercase(Locale.ROOT).contains(q) ||
                item.phone.lowercase(Locale.ROOT).contains(q) ||
                item.ledgerPage.lowercase(Locale.ROOT).contains(q)
        }.sortedWith(
            compareByDescending<CustomerDirectoryItem> { it.credit }
                .thenByDescending { it.lastSaleDate }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )

        val totalCredit = InputGuards.round2(items.sumOf { max(0.0, it.credit) })
        val totalQty = InputGuards.round2(items.sumOf { it.quantity })
        return CustomerDirectorySummary(
            items = items,
            totalOutstandingCredit = totalCredit,
            totalSoldKg = totalQty,
            totalCount = items.size
        )
    }

    private fun matchesCustomerFilter(candidate: String, filterName: String, exact: Boolean): Boolean {
        val f = filterName.trim().lowercase(Locale.ROOT)
        if (f.isBlank()) return true
        val c = candidate.trim().lowercase(Locale.ROOT)
        return if (exact) c == f else c.contains(f)
    }

    private fun pendingAmountOf(s: SalesRecord, customers: List<CustomerRecord>, stores: List<StoreConfig>): Double {
        val v = FinanceEngine.effectiveSaleValue(s, customers, stores)
        return InputGuards.round2(max(0.0, v - s.partialPaymentReceived))
    }

    fun computeSalesAnalytics(
        snapshot: WorkspaceSnapshot,
        period: SalesChartPeriod,
        anchorDateIso: String,
        customerFilter: String = "",
        exactCustomerMatch: Boolean = false
    ): SalesChartData {
        val anchor = DateUtils.parseIso(anchorDateIso) ?: LocalDate.now()
        val aliveSales = snapshot.sales.filter {
            it.deletedAt == null &&
                isAdminSaleProfile(it.currentRepProfile) &&
                matchesCustomerFilter(it.customerName, customerFilter, exactCustomerMatch)
        }

        val labels = mutableListOf<String>()
        val cashList = mutableListOf<Double>()
        val creditList = mutableListOf<Double>()

        fun addSaleToBucket(item: SalesRecord, addCash: (Double) -> Unit, addCredit: (Double) -> Unit) {
            val isRepLinked = item.salesRep.isNotBlank() && !item.salesRep.equals("NONE", true)
            when {
                item.paymentType == "CREDIT" && !item.creditReceived -> addCredit(pendingAmountOf(item, snapshot.customers, snapshot.stores))
                isRepLinked -> Unit
                item.paymentType == "CASH" || (item.paymentType == "CREDIT" && item.creditReceived) -> {
                    addCash(FinanceEngine.effectiveSaleValue(item, snapshot.customers, snapshot.stores))
                }
            }
        }

        when (period) {
            SalesChartPeriod.WEEK -> {
                for (i in 6 downTo 0) {
                    val d = anchor.minusDays(i.toLong())
                    val iso = d.toString()
                    labels.add(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US))
                    var c = 0.0
                    var cr = 0.0
                    aliveSales.filter { it.date.take(10) == iso }.forEach { s ->
                        addSaleToBucket(s, { c += it }, { cr += it })
                    }
                    cashList.add(InputGuards.round2(c))
                    creditList.add(InputGuards.round2(cr))
                }
            }
            SalesChartPeriod.MONTH -> {
                val ym = YearMonth.of(anchor.year, anchor.month)
                val days = ym.lengthOfMonth()
                val arrCash = DoubleArray(days)
                val arrCredit = DoubleArray(days)
                for (d in 1..days) labels.add(d.toString())
                aliveSales.forEach { s ->
                    val ld = DateUtils.parseIso(s.date) ?: return@forEach
                    if (ld.year == anchor.year && ld.month == anchor.month) {
                        val idx = (ld.dayOfMonth - 1).coerceIn(0, days - 1)
                        addSaleToBucket(s, { arrCash[idx] += it }, { arrCredit[idx] += it })
                    }
                }
                for (i in 0 until days) {
                    cashList.add(InputGuards.round2(arrCash[i]))
                    creditList.add(InputGuards.round2(arrCredit[i]))
                }
            }
            SalesChartPeriod.YEAR -> {
                val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val arrCash = DoubleArray(12)
                val arrCredit = DoubleArray(12)
                labels.addAll(months)
                aliveSales.forEach { s ->
                    val ld = DateUtils.parseIso(s.date) ?: return@forEach
                    if (ld.year == anchor.year) {
                        val idx = (ld.monthValue - 1).coerceIn(0, 11)
                        addSaleToBucket(s, { arrCash[idx] += it }, { arrCredit[idx] += it })
                    }
                }
                for (i in 0 until 12) {
                    cashList.add(InputGuards.round2(arrCash[i]))
                    creditList.add(InputGuards.round2(arrCredit[i]))
                }
            }
            SalesChartPeriod.ALL -> {
                val monthMap = sortedMapOf<String, Pair<Double, Double>>()
                aliveSales.forEach { s ->
                    val ld = DateUtils.parseIso(s.date) ?: return@forEach
                    val ymKey = String.format(Locale.US, "%04d-%02d", ld.year, ld.monthValue)
                    val prev = monthMap[ymKey] ?: (0.0 to 0.0)
                    var c = prev.first
                    var cr = prev.second
                    addSaleToBucket(s, { c += it }, { cr += it })
                    monthMap[ymKey] = c to cr
                }
                val keys = monthMap.keys.toList().takeLast(12)
                keys.forEach { k ->
                    val parts = k.split("-")
                    val y = parts.getOrNull(0)?.takeLast(2) ?: ""
                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 1
                    val mName = LocalDate.of(2000, m.coerceIn(1, 12), 1)
                        .month.getDisplayName(TextStyle.SHORT, Locale.US)
                    labels.add("$mName $y")
                    val pair = monthMap[k] ?: (0.0 to 0.0)
                    cashList.add(InputGuards.round2(pair.first))
                    creditList.add(InputGuards.round2(pair.second))
                }
            }
        }

        val totalCash = InputGuards.round2(cashList.sum())
        val totalCredit = InputGuards.round2(creditList.sum())
        return SalesChartData(
            labels = labels,
            cashValues = cashList,
            creditValues = creditList,
            totalCash = totalCash,
            totalCredit = totalCredit
        )
    }

    fun computeSalesPeriodSummary(
        snapshot: WorkspaceSnapshot,
        period: SalesSummaryPeriod,
        anchorDateIso: String,
        customerFilter: String = "",
        exactCustomerMatch: Boolean = false
    ): SalesPeriodSummary {
        var qty = 0.0
        var value = 0.0
        var cash = 0.0
        var credit = 0.0
        var profit = 0.0

        snapshot.sales.filter {
            it.deletedAt == null &&
                isAdminSaleProfile(it.currentRepProfile) &&
                matchesCustomerFilter(it.customerName, customerFilter, exactCustomerMatch) &&
                ProductionAnalyticsEngine.isInAnchoredPeriod(it.date, period.key, anchorDateIso)
        }.forEach { item ->
            val isRepLinked = item.salesRep.isNotBlank() && !item.salesRep.equals("NONE", true)
            val isAdminColl = !isRepLinked && item.paymentType == "COLLECTION"
            val isOldDebt = item.transactionType == "OLD_DEBT"
            if (item.paymentType == "MONEY_OUT" || item.isRepReturn || item.paymentType == "REP_RETURN") return@forEach
            if (!isRepLinked && !isAdminColl && !isOldDebt && (item.paymentType == "PARTIAL_PAYMENT" || item.paymentType == "COLLECTION")) return@forEach
            if (isRepLinked && item.paymentType == "PARTIAL_PAYMENT") return@forEach

            if (isAdminColl) {
                cash += if (item.collectedAmount > 0.0) item.collectedAmount else item.totalValue
                return@forEach
            }
            if (isOldDebt) {
                credit += item.totalValue
                return@forEach
            }
            if (isRepLinked) {
                if (item.paymentType == "CREDIT" && !item.creditReceived) {
                    credit += pendingAmountOf(item, snapshot.customers, snapshot.stores)
                }
                return@forEach
            }
            val effVal = FinanceEngine.effectiveSaleValue(item, snapshot.customers, snapshot.stores)
            if (item.paymentType == "CREDIT" && !item.creditReceived) {
                credit += max(0.0, effVal - item.partialPaymentReceived)
            } else if (item.paymentType == "CASH" || item.creditReceived) {
                cash += effVal
            }
            qty += item.quantity
            value += effVal
            profit += item.profit
        }

        val rVal = InputGuards.round2(value)
        val rProf = InputGuards.round2(profit)
        val margin = if (rVal > 0.005) ((rProf / rVal) * 100.0).roundToInt() else null
        return SalesPeriodSummary(
            quantityKg = InputGuards.round2(qty),
            totalValue = rVal,
            cashAmount = InputGuards.round2(cash),
            pendingCreditAmount = InputGuards.round2(credit),
            netProfit = rProf,
            marginPercent = margin
        )
    }

    fun computeCustomerRunningBalances(snapshot: WorkspaceSnapshot): Map<String, Double> {
        val aliveSales = snapshot.sales.filter { it.deletedAt == null && isAdminSaleProfile(it.currentRepProfile) }
        val reconciled = FinanceEngine.reconciledRepReturnIds(snapshot.calculator)
        val honor = FinanceEngine.allocHonor(aliveSales)
        val out = LinkedHashMap<String, Double>()

        aliveSales.groupBy { it.customerName.trim().lowercase(Locale.ROOT) }.forEach { (key, list) ->
            if (key.isBlank()) return@forEach
            val hasOldDebtTx = list.any { it.transactionType == "OLD_DEBT" }
            var run = if (hasOldDebtTx) 0.0 else snapshot.customers
                .filter { it.deletedAt == null && it.name.trim().lowercase(Locale.ROOT) == key }
                .sumOf { it.oldDebit }
            val asc = list.sortedWith(compareBy<SalesRecord> { it.date }.thenBy { it.timestamp }.thenBy { it.id })
            asc.forEach { s ->
                val gross = FinanceEngine.effectiveSaleValue(s, snapshot.customers, snapshot.stores)
                run = InputGuards.round2(run + FinanceEngine.ledgerDelta(s, gross, reconciled, honor))
                out[s.id] = run
            }
        }
        return out
    }

    fun getSaleBlockReason(
        snapshot: WorkspaceSnapshot,
        sale: SalesRecord,
        forEdit: Boolean = false
    ): String {
        if (sale.isMerged) {
            return "Merged opening balance records cannot be ${if (forEdit) "edited" else "deleted"}."
        }
        val calcLink = snapshot.calculator.firstOrNull { c ->
            c.deletedAt == null && (
                sale.id in c.linkedSalesIds ||
                    sale.id in c.linkedRepReturnIds ||
                    c.transferSaleId == sale.id
                )
        }
        if (calcLink != null) {
            return if (calcLink.transferSaleId == sale.id || sale.isTransfer || sale.isRepTransfer) {
                "This stock transfer from ${calcLink.seller} is linked to Calculator (${calcLink.date}). Delete that calculator record first."
            } else {
                "This record is already settled in Calculator (${calcLink.seller} • ${calcLink.date}). Delete that calculator entry first."
            }
        }
        if (sale.collectionAllocs.isNotEmpty()) {
            return "This sale was paid through a collection. Delete or edit that collection first."
        }
        if (!forEdit) {
            val children = snapshot.sales.count {
                it.deletedAt == null && it.id != sale.id && it.relatedSaleId == sale.id
            }
            if (children > 0) {
                return "This sale has $children linked payment record${if (children != 1) "s" else ""}. " +
                    "Delete the payment record${if (children != 1) "s" else ""} first."
            }
        }
        if (sale.isCollection || sale.paymentType == "COLLECTION") {
            val expApplied = if (sale.expiredQty > 0.0 && sale.expiredApplied > 0.0) sale.expiredApplied else 0.0
            if (expApplied > 0.0 && !forEdit) {
                val chora = FinanceEngine.findMaterialByName(snapshot.inventory, "CHORA")
                if (chora != null && chora.quantity + 0.0001 < expApplied) {
                    return "Only ${InputGuards.formatKg(chora.quantity)} of CHORA remains; cannot reverse ${InputGuards.formatKg(expApplied)} expired goods."
                }
            }
        }
        return ""
    }

    fun buildAllCustomersPdfRows(
        snapshot: WorkspaceSnapshot,
        masked: Boolean
    ): Pair<List<StatementRow>, String> {
        val dir = computeCustomerDirectory(snapshot, "")
        val aliveSales = snapshot.sales.filter { it.deletedAt == null && isAdminSaleProfile(it.currentRepProfile) }
        val honor = FinanceEngine.allocHonor(aliveSales)
        val rows = dir.items.map { item ->
            val custRows = aliveSales
                .filter { it.customerName.equals(item.name, ignoreCase = true) }
                .map { s -> s to FinanceEngine.effectiveSaleValue(s, snapshot.customers, snapshot.stores) }
            val totals = FinanceEngine.periodTotals(custRows)
            val balLabel = when {
                masked -> "••••"
                abs(item.credit) < 0.01 -> "SETTLED"
                item.credit < 0.0 -> "${InputGuards.fmtNum(-item.credit)} CR"
                else -> InputGuards.fmtNum(item.credit)
            }
            val colCredit = totals.totalReceived + honor.byCol.entries
                .filter { (cid, _) -> custRows.any { it.first.id == cid } }
                .sumOf { it.value }
            val _unused = colCredit
            StatementRow(
                date = item.lastSaleDate.ifBlank { "-" },
                description = "${item.name} (${InputGuards.formatKg(item.quantity)})" +
                    (item.phone.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                debit = if (masked) "••••" else InputGuards.fmtNum(totals.totalSale),
                credit = if (masked) "••••" else InputGuards.fmtNum(totals.totalReceived),
                balance = balLabel
            )
        }
        val footer = "${dir.totalCount} Customers • Total Sold: ${InputGuards.formatKg(dir.totalSoldKg)} • " +
            "Outstanding Credit: ${InputGuards.formatMoney(dir.totalOutstandingCredit, snapshot.settings.currencySymbol, masked)}"
        return rows to footer
    }
}
