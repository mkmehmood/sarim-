package com.gullzubair.sarim.domain

import com.gullzubair.sarim.data.model.CustomerRecord
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.util.LocationHelper
import com.gullzubair.sarim.util.StatementRow
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure domain calculations and validation guards for the Rep Sales tab,
 * porting web `rep-sales.js`, `rep-return-link.js`, `factory-core.js` (`getRepGoodsCreditUnitPrice`),
 * `link-guards.js` (rep scopes), and `utilities-sales.js` (`exportCustomerData('rep')`).
 */
object RepSalesAnalyticsEngine {

    enum class RepAnalyticsPeriod(val key: String, val label: String) {
        DAY("day", "Daily"),
        WEEK("week", "Weekly"),
        MONTH("month", "Monthly"),
        YEAR("year", "Yearly"),
        ALL("all", "All Time")
    }

    data class RepClientDirectoryItem(
        val name: String,
        val debt: Double,
        val quantity: Double,
        val phone: String,
        val address: String,
        val ledgerPage: String,
        val visitCount: Long,
        val customSalePrice: Double,
        val lastDate: String,
        val photoUri: String?,
        val gpsLat: Double?,
        val gpsLng: Double?,
        val locationConfirmed: Boolean
    )

    data class RepClientDirectorySummary(
        val items: List<RepClientDirectoryItem>,
        val totalOutstandingCredit: Double,
        val totalCreditSales: Double,
        val totalCollections: Double,
        val totalCount: Int
    )

    data class RepPeriodAnalytics(
        val moneyIn: Double,
        val collections: Double,
        val cashSales: Double,
        val creditSales: Double
    )

    data class RepRouteStop(
        val record: SalesRecord,
        val lat: Double,
        val lng: Double,
        val label: String,
        val kind: String, // CASH | COLLECTION | CREDIT | OTHER
        val detail: String,
        val timeLabel: String,
        val distanceFromPrevMeters: Double?
    )

    fun isSameRep(candidate: String, repProfile: String): Boolean {
        val c = candidate.trim()
        val p = repProfile.trim()
        if (p.isBlank()) return c.isNotBlank() && !c.equals("NONE", ignoreCase = true)
        return c.equals(p, ignoreCase = true)
    }

    fun scopedRepRecords(
        repRecords: List<SalesRecord>,
        repProfile: String
    ): List<SalesRecord> {
        return repRecords.filter { r ->
            r.deletedAt == null && (
                isSameRep(r.salesRep, repProfile) ||
                    (r.salesRep.isBlank() && isSameRep(r.currentRepProfile, repProfile))
                )
        }
    }

    fun knownRepProfiles(snapshot: WorkspaceSnapshot): List<String> {
        return (snapshot.settings.reps +
            snapshot.teamMembers.map { it.displayName } +
            snapshot.repRecords.filter { it.deletedAt == null }.map { it.salesRep } +
            snapshot.clients.filter { it.deletedAt == null }.map { it.salesRep } +
            snapshot.profile.displayName)
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("NONE", ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedBy { it.lowercase(Locale.ROOT) }
    }

    fun isRepOwnNameCollision(customerName: String, knownReps: List<String>): Boolean {
        val clean = customerName.trim()
        if (clean.isBlank()) return false
        return knownReps.any { it.equals(clean, ignoreCase = true) }
    }

    fun effectiveRepRate(
        customerName: String,
        store: StoreConfig?,
        clients: List<CustomerRecord>,
        repProfile: String,
        defaultPrices: Map<String, Double> = emptyMap()
    ): Double {
        val clean = customerName.trim()
        if (clean.isNotBlank()) {
            val clientMatch = clients.firstOrNull {
                it.deletedAt == null &&
                    it.name.equals(clean, ignoreCase = true) &&
                    (it.salesRep.isBlank() || it.salesRep.equals(repProfile, ignoreCase = true))
            } ?: clients.firstOrNull {
                it.deletedAt == null && it.name.equals(clean, ignoreCase = true)
            }
            if ((clientMatch?.customSalePrice ?: 0.0) > 0.0) {
                return InputGuards.round2(clientMatch!!.customSalePrice)
            }
        }
        val storeRate = store?.salePrice ?: 0.0
        if (storeRate > 0.0) return InputGuards.round2(storeRate)
        val fallback = store?.let { defaultPrices[it.key] } ?: 0.0
        return InputGuards.round2(fallback)
    }

    fun computeRepClientOutstanding(
        customerName: String,
        repProfile: String,
        snapshot: WorkspaceSnapshot,
        excludeRecordId: String? = null
    ): Double {
        val clean = customerName.trim()
        if (clean.isBlank()) return 0.0
        val scoped = scopedRepRecords(snapshot.repRecords, repProfile)
            .filter { it.id != excludeRecordId && it.customerName.equals(clean, ignoreCase = true) }
        val honor = FinanceEngine.allocHonor(scoped)
        val reconciled = FinanceEngine.reconciledRepReturnIds(snapshot.calculator)
        val hasOldDebtTx = scoped.any { it.transactionType == "OLD_DEBT" }
        val opening = if (hasOldDebtTx) 0.0 else snapshot.clients
            .filter {
                it.deletedAt == null &&
                    it.name.equals(clean, ignoreCase = true) &&
                    (it.salesRep.isBlank() || it.salesRep.equals(repProfile, ignoreCase = true))
            }
            .sumOf { it.oldDebit }
        val net = scoped.sumOf { r ->
            val gross = FinanceEngine.effectiveSaleValue(r, snapshot.clients.ifEmpty { snapshot.customers }, snapshot.stores)
            FinanceEngine.ledgerDelta(r, gross, reconciled, honor)
        }
        return InputGuards.round2(max(0.0, opening + net))
    }

    fun computeRepGoodsCreditUnitPrice(
        repProfile: String,
        customerName: String,
        snapshot: WorkspaceSnapshot,
        excludeRecordId: String? = null,
        fallbackStore: StoreConfig? = null
    ): Double {
        val clean = customerName.trim()
        if (clean.isBlank()) return 0.0
        val rows = scopedRepRecords(snapshot.repRecords, repProfile).filter { r ->
            r.id != excludeRecordId &&
                !r.isMerged &&
                r.customerName.equals(clean, ignoreCase = true) &&
                r.paymentType == "CREDIT" &&
                r.transactionType != "OLD_DEBT" &&
                r.quantity > 0.0
        }

        fun weightedPrice(list: List<SalesRecord>): Double {
            var qSum = 0.0
            var vSum = 0.0
            list.forEach { r ->
                val v = FinanceEngine.effectiveSaleValue(r, snapshot.clients.ifEmpty { snapshot.customers }, snapshot.stores)
                if (r.quantity > 0.0 && v > 0.0) {
                    qSum += r.quantity
                    vSum += v
                }
            }
            return if (qSum > 0.0) InputGuards.round2(vSum / qSum) else 0.0
        }

        val unpaidPrice = weightedPrice(rows.filter { !it.creditReceived })
        if (unpaidPrice > 0.0) return unpaidPrice

        val newest = rows.maxWithOrNull(compareBy<SalesRecord> { it.date }.thenBy { it.timestamp })
        val lastPrice = newest?.let { weightedPrice(listOf(it)) } ?: 0.0
        if (lastPrice > 0.0) return lastPrice

        return effectiveRepRate(
            customerName = clean,
            store = fallbackStore ?: snapshot.stores.firstOrNull { it.deletedAt == null && it.active },
            clients = snapshot.clients,
            repProfile = repProfile,
            defaultPrices = snapshot.settings.defaultStorePrices
        )
    }

    fun computeRepClientDirectory(
        snapshot: WorkspaceSnapshot,
        repProfile: String,
        searchQuery: String
    ): RepClientDirectorySummary {
        val scoped = scopedRepRecords(snapshot.repRecords, repProfile)
        val honor = FinanceEngine.allocHonor(scoped)
        val reconciled = FinanceEngine.reconciledRepReturnIds(snapshot.calculator)
        val custPool = snapshot.clients.ifEmpty { snapshot.customers }

        val grouped = LinkedHashMap<String, MutableList<SalesRecord>>()
        scoped.forEach { r ->
            val key = r.customerName.trim().lowercase(Locale.ROOT)
            if (key.isNotBlank()) grouped.getOrPut(key) { mutableListOf() }.add(r)
        }

        val itemsMap = LinkedHashMap<String, RepClientDirectoryItem>()
        val salesNames = grouped.keys

        // Include registered rep clients for this rep (or un-assigned clients with sales under this rep)
        snapshot.clients.filter { it.deletedAt == null && it.name.isNotBlank() }.forEach { c ->
            val lc = c.name.trim().lowercase(Locale.ROOT)
            val belongs = when {
                c.salesRep.isNotBlank() -> c.salesRep.equals(repProfile, ignoreCase = true)
                else -> lc in salesNames
            }
            if (!belongs) return@forEach
            val rows = grouped[lc].orEmpty()
            val hasOldDebtTx = rows.any { it.transactionType == "OLD_DEBT" }
            val opening = if (hasOldDebtTx) 0.0 else c.oldDebit
            val deltaSum = rows.sumOf { r ->
                val gross = FinanceEngine.effectiveSaleValue(r, custPool, snapshot.stores)
                FinanceEngine.ledgerDelta(r, gross, reconciled, honor)
            }
            val debt = InputGuards.round2(opening + deltaSum)
            val qty = InputGuards.round2(rows.sumOf { FinanceEngine.soldQuantity(it) })
            val latest = rows.maxWithOrNull(compareBy<SalesRecord> { it.date }.thenBy { it.timestamp })
            val photoUri = snapshot.photos["client:${c.id}"]?.takeIf { !it.deleted }?.dataUri
                ?: snapshot.photos["rep-cust:${repProfile.lowercase(Locale.ROOT)}:$lc"]?.takeIf { !it.deleted }?.dataUri
            val ledgerPage = c.ledgerPage.ifBlank {
                Regex("page:([^;\\n]+)").find(c.notes)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            }
            itemsMap[lc] = RepClientDirectoryItem(
                name = c.name.trim(),
                debt = debt,
                quantity = qty,
                phone = c.phone.ifBlank { rows.firstOrNull { it.customerPhone.isNotBlank() }?.customerPhone.orEmpty() },
                address = c.address,
                ledgerPage = ledgerPage,
                visitCount = max(c.visitCount, rows.size.toLong()),
                customSalePrice = c.customSalePrice,
                lastDate = latest?.date.orEmpty(),
                photoUri = photoUri,
                gpsLat = c.gpsLat ?: latest?.gpsLat,
                gpsLng = c.gpsLng ?: latest?.gpsLng,
                locationConfirmed = c.address.startsWith("GPS:") || rows.count { it.gpsConfirmed } > 0
            )
        }

        // Also include any customers from rep records not yet in clients collection
        grouped.forEach { (lc, rows) ->
            if (lc in itemsMap) return@forEach
            val displayName = rows.lastOrNull()?.customerName?.trim().orEmpty()
            if (displayName.isBlank()) return@forEach
            val deltaSum = rows.sumOf { r ->
                val gross = FinanceEngine.effectiveSaleValue(r, custPool, snapshot.stores)
                FinanceEngine.ledgerDelta(r, gross, reconciled, honor)
            }
            val latest = rows.maxWithOrNull(compareBy<SalesRecord> { it.date }.thenBy { it.timestamp })
            itemsMap[lc] = RepClientDirectoryItem(
                name = displayName,
                debt = InputGuards.round2(deltaSum),
                quantity = InputGuards.round2(rows.sumOf { FinanceEngine.soldQuantity(it) }),
                phone = rows.firstOrNull { it.customerPhone.isNotBlank() }?.customerPhone.orEmpty(),
                address = "",
                ledgerPage = "",
                visitCount = rows.size.toLong(),
                customSalePrice = 0.0,
                lastDate = latest?.date.orEmpty(),
                photoUri = snapshot.photos["rep-cust:${repProfile.lowercase(Locale.ROOT)}:$lc"]?.takeIf { !it.deleted }?.dataUri,
                gpsLat = latest?.gpsLat,
                gpsLng = latest?.gpsLng,
                locationConfirmed = rows.any { it.gpsConfirmed }
            )
        }

        val allItems = itemsMap.values.sortedBy { it.name.lowercase(Locale.ROOT) }
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        val filtered = if (q.isBlank()) {
            allItems
        } else {
            allItems.filter {
                it.name.lowercase(Locale.ROOT).contains(q) ||
                    it.phone.lowercase(Locale.ROOT).contains(q) ||
                    it.address.lowercase(Locale.ROOT).contains(q) ||
                    it.ledgerPage.lowercase(Locale.ROOT).contains(q)
            }
        }

        var repCreditSales = 0.0
        var repCollections = 0.0
        scoped.forEach { s ->
            val valEff = FinanceEngine.effectiveSaleValue(s, custPool, snapshot.stores)
            when {
                s.paymentType == "CREDIT" -> repCreditSales += valEff
                FinanceEngine.isCollectionRow(s) -> {
                    repCollections += if (s.collectedAmount > 0.0) s.collectedAmount else s.totalValue
                }
            }
        }
        val totalOutstanding = InputGuards.round2(allItems.sumOf { max(0.0, it.debt) })

        return RepClientDirectorySummary(
            items = filtered,
            totalOutstandingCredit = totalOutstanding,
            totalCreditSales = InputGuards.round2(repCreditSales),
            totalCollections = InputGuards.round2(repCollections),
            totalCount = allItems.size
        )
    }

    fun computeRepAnalytics(
        snapshot: WorkspaceSnapshot,
        repProfile: String,
        period: RepAnalyticsPeriod,
        anchorDateIso: String
    ): RepPeriodAnalytics {
        val anchor = DateUtils.parseIso(anchorDateIso) ?: LocalDate.now()
        val scoped = scopedRepRecords(snapshot.repRecords, repProfile)
        val custPool = snapshot.clients.ifEmpty { snapshot.customers }

        var collections = 0.0
        var cashSales = 0.0
        var creditSales = 0.0

        scoped.forEach { sale ->
            val d = DateUtils.parseIso(sale.date)
            val inPeriod = when (period) {
                RepAnalyticsPeriod.ALL -> true
                RepAnalyticsPeriod.DAY -> d == anchor
                RepAnalyticsPeriod.WEEK -> d != null && !d.isBefore(anchor.minusDays(6)) && !d.isAfter(anchor)
                RepAnalyticsPeriod.MONTH -> d != null && d.year == anchor.year && d.monthValue == anchor.monthValue
                RepAnalyticsPeriod.YEAR -> d != null && d.year == anchor.year
            }
            if (!inPeriod) return@forEach

            val eff = FinanceEngine.effectiveSaleValue(sale, custPool, snapshot.stores)
            when {
                FinanceEngine.isCollectionRow(sale) -> {
                    collections += if (sale.collectedAmount > 0.0) sale.collectedAmount else sale.totalValue
                }
                sale.paymentType == "CASH" -> cashSales += eff
                sale.paymentType == "CREDIT" -> {
                    if (sale.creditReceived) {
                        cashSales += eff
                    } else {
                        creditSales += max(0.0, eff - sale.partialPaymentReceived)
                    }
                }
            }
        }

        val rCol = InputGuards.round2(collections)
        val rCash = InputGuards.round2(cashSales)
        val rCredit = InputGuards.round2(creditSales)
        return RepPeriodAnalytics(
            moneyIn = InputGuards.round2(rCol + rCash),
            collections = rCol,
            cashSales = rCash,
            creditSales = rCredit
        )
    }

    fun computeDailyRouteStops(
        snapshot: WorkspaceSnapshot,
        repProfile: String,
        dateIso: String
    ): List<RepRouteStop> {
        val day = dateIso.take(10)
        val custPool = snapshot.clients.ifEmpty { snapshot.customers }
        val route = scopedRepRecords(snapshot.repRecords, repProfile)
            .filter { it.date.take(10) == day && it.gpsLat != null && it.gpsLng != null }
            .sortedBy { it.timestamp }

        val out = mutableListOf<RepRouteStop>()
        var prevLat: Double? = null
        var prevLng: Double? = null

        route.forEach { txn ->
            val lat = txn.gpsLat ?: return@forEach
            val lng = txn.gpsLng ?: return@forEach
            val dist = if (prevLat != null && prevLng != null) {
                LocationHelper.distanceMeters(prevLat!!, prevLng!!, lat, lng)
            } else null
            prevLat = lat
            prevLng = lng

            val (kind, detail) = when {
                FinanceEngine.isCollectionRow(txn) -> {
                    val amt = if (txn.collectedAmount > 0.0) txn.collectedAmount else txn.totalValue
                    "COLLECTION" to "${snapshot.settings.currencySymbol}${InputGuards.fmtNum(amt)}"
                }
                txn.paymentType == "CREDIT" -> "CREDIT" to "${InputGuards.formatKg(txn.quantity)} (Credit)"
                txn.paymentType == "CASH" -> "CASH" to InputGuards.formatKg(txn.quantity)
                else -> {
                    val v = FinanceEngine.effectiveSaleValue(txn, custPool, snapshot.stores)
                    txn.paymentType to "${snapshot.settings.currencySymbol}${InputGuards.fmtNum(v)}"
                }
            }
            out.add(
                RepRouteStop(
                    record = txn,
                    lat = lat,
                    lng = lng,
                    label = txn.customerName,
                    kind = kind,
                    detail = detail,
                    timeLabel = txn.time.ifBlank { txn.date },
                    distanceFromPrevMeters = dist
                )
            )
        }
        return out
    }

    fun computeRepRunningBalances(
        snapshot: WorkspaceSnapshot,
        repProfile: String
    ): Map<String, Double> {
        val scoped = scopedRepRecords(snapshot.repRecords, repProfile)
        val reconciled = FinanceEngine.reconciledRepReturnIds(snapshot.calculator)
        val honor = FinanceEngine.allocHonor(scoped)
        val custPool = snapshot.clients.ifEmpty { snapshot.customers }
        val out = LinkedHashMap<String, Double>()

        scoped.groupBy { it.customerName.trim().lowercase(Locale.ROOT) }.forEach { (key, list) ->
            if (key.isBlank()) return@forEach
            val hasOldDebtTx = list.any { it.transactionType == "OLD_DEBT" }
            var run = if (hasOldDebtTx) 0.0 else snapshot.clients
                .filter {
                    it.deletedAt == null &&
                        it.name.trim().lowercase(Locale.ROOT) == key &&
                        (it.salesRep.isBlank() || it.salesRep.equals(repProfile, ignoreCase = true))
                }
                .sumOf { it.oldDebit }
            val asc = list.sortedWith(compareBy<SalesRecord> { it.date }.thenBy { it.timestamp }.thenBy { it.id })
            asc.forEach { s ->
                val gross = FinanceEngine.effectiveSaleValue(s, custPool, snapshot.stores)
                run = InputGuards.round2(run + FinanceEngine.ledgerDelta(s, gross, reconciled, honor))
                out[s.id] = run
            }
        }
        return out
    }

    fun getRepSaleBlockReason(
        snapshot: WorkspaceSnapshot,
        sale: SalesRecord,
        forEdit: Boolean = false
    ): String {
        if (sale.isMerged) {
            return "Merged opening balance records cannot be ${if (forEdit) "edited" else "deleted"}."
        }
        if (forEdit && sale.transactionType == "OLD_DEBT") {
            return "Opening balances are edited from customer details."
        }
        if (sale.usedInCalcId.isNotBlank()) {
            return "This record is already settled in Calculator. Undo that calculation first."
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
            return "This sale was paid through a bulk/partial collection. Delete or edit that collection first."
        }
        if (!forEdit && (sale.isCollection || sale.paymentType == "COLLECTION")) {
            val expApplied = if (sale.expiredQty > 0.0 && sale.expiredApplied > 0.0) sale.expiredApplied else 0.0
            if (expApplied > 0.0) {
                val chora = FinanceEngine.findMaterialByName(snapshot.inventory, "CHORA")
                if (chora != null && chora.quantity + 0.0001 < expApplied) {
                    return "Only ${InputGuards.formatKg(chora.quantity)} of CHORA remains; cannot reverse ${InputGuards.formatKg(expApplied)} expired goods."
                }
            }
        }
        if (!forEdit) {
            val children = snapshot.repRecords.filter {
                it.deletedAt == null && it.id != sale.id && it.relatedSaleId == sale.id
            }
            if (children.isNotEmpty()) {
                return "This sale has ${children.size} linked payment record${if (children.size != 1) "s" else ""}. Delete those first."
            }
        }
        return ""
    }

    fun buildAllRepClientsPdfRows(
        snapshot: WorkspaceSnapshot,
        repProfile: String,
        masked: Boolean
    ): Pair<List<StatementRow>, String> {
        val dir = computeRepClientDirectory(snapshot, repProfile, "")
        val scoped = scopedRepRecords(snapshot.repRecords, repProfile)
        val custPool = snapshot.clients.ifEmpty { snapshot.customers }
        val rows = dir.items.map { item ->
            val custRows = scoped
                .filter { it.customerName.equals(item.name, ignoreCase = true) }
                .map { s -> s to FinanceEngine.effectiveSaleValue(s, custPool, snapshot.stores) }
            val totals = FinanceEngine.periodTotals(custRows)
            val balLabel = when {
                masked -> "••••"
                abs(item.debt) < 0.01 -> "SETTLED"
                item.debt < 0.0 -> "${InputGuards.fmtNum(-item.debt)} CR"
                else -> InputGuards.fmtNum(item.debt)
            }
            StatementRow(
                date = item.lastDate.ifBlank { "-" },
                description = "${item.name} (${InputGuards.formatKg(item.quantity)})" +
                    (item.phone.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                debit = if (masked) "••••" else InputGuards.fmtNum(totals.totalSale),
                credit = if (masked) "••••" else InputGuards.fmtNum(totals.totalReceived),
                balance = balLabel
            )
        }
        val footer = "Rep: $repProfile • ${dir.totalCount} Clients • Credit Sales: " +
            "${InputGuards.formatMoney(dir.totalCreditSales, snapshot.settings.currencySymbol, masked)} • " +
            "Outstanding: ${InputGuards.formatMoney(dir.totalOutstandingCredit, snapshot.settings.currencySymbol, masked)}"
        return rows to footer
    }
}
