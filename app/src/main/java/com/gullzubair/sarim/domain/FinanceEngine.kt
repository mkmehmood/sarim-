package com.gullzubair.sarim.domain

import com.gullzubair.sarim.data.model.CalculatorRecord
import com.gullzubair.sarim.data.model.CustomerRecord
import com.gullzubair.sarim.data.model.DailyLedgerEntry
import com.gullzubair.sarim.data.model.EntityRecord
import com.gullzubair.sarim.data.model.ExpenseRecord
import com.gullzubair.sarim.data.model.FactoryRecord
import com.gullzubair.sarim.data.model.FormulaConfig
import com.gullzubair.sarim.data.model.InventoryRecord
import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.ReturnRecord
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.UserProfile
import com.gullzubair.sarim.data.model.WorkspaceSettings
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 1:1 Kotlin port of web `roles.js`, `finance.js`, `raw-purchases.js`,
 * `ledger-core.js`, `link-repair.js`, and store/entity/economic computations.
 */
object FinanceEngine {

    // --- RBAC & Multi-role Session (roles.js) ---

    val ROLE_KEYS: List<String> = listOf("admin", "rep", "production", "factory", "payments", "sales")

    val ROLE_LABELS: Map<String, String> = mapOf(
        "admin" to "Admin",
        "rep" to "Rep",
        "production" to "Production",
        "factory" to "Factory",
        "payments" to "Payments",
        "sales" to "Sales"
    )

    private val ROLE_TAB: Map<String, String> = mapOf(
        "rep" to "rep",
        "production" to "prod",
        "factory" to "factory",
        "payments" to "payments",
        "sales" to "sales"
    )

    private val TAB_ORDER: List<String> = listOf("prod", "factory", "sales", "payments", "rep")
    private val MONEY_TABS: Set<String> = setOf("payments", "sales")

    data class SessionInfo(
        val mode: String, // admin | rep | production | factory | userrole
        val tabs: List<String>,
        val roles: List<String>,
        val restrictMoney: Boolean
    )

    fun cleanRoles(list: List<String>?): List<String> {
        val seen = list.orEmpty().map { it.trim().lowercase(Locale.ROOT) }.filter { it in ROLE_KEYS }.toSet()
        return ROLE_KEYS.filter { it in seen }
    }

    fun normalizeRoles(profile: UserProfile): List<String> {
        val cleaned = cleanRoles(profile.roles)
        return if (cleaned.isNotEmpty()) cleaned else cleanRoles(listOf(profile.role))
    }

    fun tabsForRoles(roles: List<String>): List<String> {
        val tabs = cleanRoles(roles).mapNotNull { ROLE_TAB[it] }.toSet()
        return TAB_ORDER.filter { it in tabs }
    }

    fun rolesRestrictMoney(roles: List<String>): Boolean {
        val cur = cleanRoles(roles)
        if ("admin" in cur) return false
        return tabsForRoles(cur).any { it in MONEY_TABS }
    }

    fun deriveSession(profile: UserProfile): SessionInfo {
        val cur = normalizeRoles(profile)
        if ("admin" in cur || profile.effectiveOwnerUid == profile.uid && cur.isEmpty()) {
            return SessionInfo(mode = "admin", tabs = emptyList(), roles = if (cur.isEmpty()) listOf("admin") else cur, restrictMoney = false)
        }
        val tabs = tabsForRoles(cur)
        val mode = when {
            cur.size == 1 && cur[0] == "rep" -> "rep"
            cur.size == 1 && cur[0] == "production" -> "production"
            cur.size == 1 && cur[0] == "factory" -> "factory"
            else -> "userrole"
        }
        return SessionInfo(mode = mode, tabs = tabs, roles = cur, restrictMoney = rolesRestrictMoney(cur))
    }

    // --- Finance & FIFO Debt / Collection Math (finance.js) ---

    private val LEDGER_PAYMENT_TYPES = setOf("COLLECTION", "PARTIAL_PAYMENT", "MONEY_OUT", "REP_RETURN")

    fun isCollectionRow(t: SalesRecord?): Boolean =
        t != null && (t.paymentType == "COLLECTION" || t.paymentType == "PARTIAL_PAYMENT")

    fun isLedgerRow(t: SalesRecord?): Boolean =
        t != null && (t.isCollection || t.isRepTransfer || t.transactionType == "OLD_DEBT" || t.paymentType in LEDGER_PAYMENT_TYPES)

    fun lockedUnitPrice(t: SalesRecord?): Double {
        if (t == null) return 0.0
        if (t.unitPrice > 0.0) return t.unitPrice
        return if (t.quantity > 0.0 && t.totalValue > 0.0) t.totalValue / t.quantity else 0.0
    }

    fun lockedSaleValue(t: SalesRecord?): Double? {
        if (t == null) return null
        if (t.quantity <= 0.0) return InputGuards.round2(t.totalValue)
        if (t.unitPrice > 0.0) return InputGuards.round2(t.quantity * t.unitPrice)
        return if (t.totalValue > 0.0) InputGuards.round2(t.totalValue) else null
    }

    fun collectionExpiredLeft(t: SalesRecord?): Double {
        if (t == null) return 0.0
        return InputGuards.round2(max(0.0, t.expiredValue - max(0.0, t.expiredAllocated)))
    }

    fun debtDelta(
        t: SalesRecord?,
        grossValue: Double,
        reconciledReturnIds: Set<String> = emptySet()
    ): Double {
        if (t == null) return 0.0
        val partial = t.partialPaymentReceived
        if (t.isRepReturn || t.paymentType == "REP_RETURN") {
            val v = InputGuards.round2(max(0.0, t.returnValue))
            if (v <= 0.0) return 0.0
            if (t.id in reconciledReturnIds) return 0.0
            return -v
        }
        if (t.transactionType == "OLD_DEBT") {
            return if (t.creditReceived) 0.0 else InputGuards.round2(max(0.0, grossValue - partial))
        }
        if (t.paymentType == "CREDIT" && !t.creditReceived) {
            return InputGuards.round2(max(0.0, grossValue - partial))
        }
        if (isCollectionRow(t)) {
            return InputGuards.round2(-(max(0.0, t.totalValue) + collectionExpiredLeft(t)))
        }
        if (t.paymentType == "MONEY_OUT") {
            return InputGuards.round2(max(0.0, t.totalValue))
        }
        return 0.0
    }

    data class AllocHonorResult(
        val bySale: Map<String, Double> = emptyMap(),
        val byCol: Map<String, Double> = emptyMap()
    )

    fun allocHonor(records: List<SalesRecord>): AllocHonorResult {
        val list = records.filter { it.id.isNotBlank() && it.deletedAt == null }
        val byId = list.associateBy { it.id }
        val perSale = LinkedHashMap<String, MutableList<Pair<String, Double>>>()

        list.forEach { c ->
            if (!isCollectionRow(c) || c.allocations.isEmpty()) return@forEach
            c.allocations.forEach { (saleId, allocAmt) ->
                val s = byId[saleId] ?: return@forEach
                val mirrorAmt = s.collectionAllocs[c.id] ?: return@forEach
                val amt = min(max(0.0, allocAmt), max(0.0, mirrorAmt))
                perSale.getOrPut(s.id) { mutableListOf() }.add(c.id to amt)
            }
        }

        val bySale = LinkedHashMap<String, Double>()
        val byCol = LinkedHashMap<String, Double>()
        perSale.forEach { (sid, pairs) ->
            val s = byId[sid] ?: return@forEach
            val cap = if (s.creditReceived) {
                if (s.totalValue > 0.0) s.totalValue else Double.POSITIVE_INFINITY
            } else {
                max(0.0, s.partialPaymentReceived)
            }
            val sum = pairs.sumOf { it.second }
            val factor = if (sum > cap && sum > 0.0) cap / sum else 1.0
            pairs.forEach { (cid, amt) ->
                val h = InputGuards.round2(amt * factor)
                bySale[sid] = InputGuards.round2((bySale[sid] ?: 0.0) + h)
                byCol[cid] = InputGuards.round2((byCol[cid] ?: 0.0) + h)
            }
        }
        return AllocHonorResult(bySale = bySale, byCol = byCol)
    }

    fun ledgerDelta(
        t: SalesRecord?,
        grossValue: Double,
        reconciledReturnIds: Set<String> = emptySet(),
        honor: AllocHonorResult? = null
    ): Double {
        if (t == null) return 0.0
        val d = debtDelta(t, grossValue, reconciledReturnIds)
        if (honor == null) return d
        if (isCollectionRow(t)) return InputGuards.round2(d - (honor.byCol[t.id] ?: 0.0))
        if (t.isRepReturn || t.paymentType == "REP_RETURN" || t.isMerged) return d
        return InputGuards.round2(d + (honor.bySale[t.id] ?: 0.0))
    }

    fun collectionUnapplied(t: SalesRecord?, honor: AllocHonorResult?): Double {
        if (!isCollectionRow(t)) return 0.0
        val row = t!!
        val got = (if (row.collectedAmount > 0.0) row.collectedAmount else row.totalValue) + max(0.0, row.expiredValue)
        val dropped = -min(0.0, ledgerDelta(row, 0.0, honor = honor))
        return InputGuards.round2(max(0.0, got - dropped))
    }

    data class LedgerSplit(val debit: Double, val credit: Double, val delta: Double)

    fun ledgerSplit(
        t: SalesRecord?,
        grossValue: Double,
        reconciledReturnIds: Set<String> = emptySet(),
        honor: AllocHonorResult? = null
    ): LedgerSplit {
        val delta = ledgerDelta(t, grossValue, reconciledReturnIds, honor)
        if (t == null || t.isRepReturn || t.paymentType == "REP_RETURN" || t.paymentType == "MONEY_OUT" || isCollectionRow(t)) {
            return LedgerSplit(debit = if (delta > 0.0) delta else 0.0, credit = if (delta < 0.0) -delta else 0.0, delta = delta)
        }
        val gross = max(0.0, grossValue)
        val credit = InputGuards.round2(max(0.0, gross - delta))
        return LedgerSplit(debit = InputGuards.round2(delta + credit), credit = credit, delta = delta)
    }

    data class RowReceivedResult(
        val sale: Double = 0.0,
        val collection: Double = 0.0,
        val returns: Double = 0.0,
        val cash: Double = 0.0,
        val paid: Double = 0.0,
        val received: Double = 0.0,
        val moneyOut: Double = 0.0
    )

    fun rowReceived(t: SalesRecord?, value: Double): RowReceivedResult {
        if (t == null || t.isMerged || t.transactionType == "OLD_DEBT") return RowReceivedResult()
        if (t.paymentType == "MONEY_OUT") return RowReceivedResult(moneyOut = max(0.0, t.totalValue))
        var sale = 0.0
        var col = 0.0
        var ret = 0.0
        var cash = 0.0
        var paid = 0.0
        if (t.isRepReturn || t.paymentType == "REP_RETURN") {
            ret = max(0.0, t.returnValue)
        } else if (isCollectionRow(t)) {
            val collected = if (t.collectedAmount > 0.0) t.collectedAmount else t.totalValue
            col = max(0.0, collected) + max(0.0, t.expiredValue)
        } else {
            val v = max(0.0, value)
            sale = v
            if (t.paymentType == "CASH") cash = v
            else if (t.paymentType == "CREDIT" && t.creditReceived && t.collectionAllocs.isEmpty()) paid = v
        }
        return RowReceivedResult(sale = sale, collection = col, returns = ret, cash = cash, paid = paid, received = col + ret + cash + paid)
    }

    data class PeriodTotals(
        val totalSale: Double,
        val collection: Double,
        val returns: Double,
        val totalCollection: Double,
        val cashSales: Double,
        val paidSales: Double,
        val totalReceived: Double
    )

    fun periodTotals(rows: List<Pair<SalesRecord, Double>>): PeriodTotals {
        var sale = 0.0
        var col = 0.0
        var ret = 0.0
        var cash = 0.0
        var paid = 0.0
        rows.forEach { (t, value) ->
            val r = rowReceived(t, value)
            sale += r.sale
            col += r.collection
            ret += r.returns
            cash += r.cash
            paid += r.paid
        }
        return PeriodTotals(
            totalSale = InputGuards.round2(sale),
            collection = InputGuards.round2(col),
            returns = InputGuards.round2(ret),
            totalCollection = InputGuards.round2(col + ret),
            cashSales = InputGuards.round2(cash),
            paidSales = InputGuards.round2(paid),
            totalReceived = InputGuards.round2(col + ret + cash + paid)
        )
    }

    fun soldQuantity(t: SalesRecord?): Double {
        if (t == null || t.isMerged || t.transactionType == "OLD_DEBT") return 0.0
        if (t.isRepReturn || t.paymentType in LEDGER_PAYMENT_TYPES) return 0.0
        return max(0.0, t.quantity)
    }

    fun returnedQuantity(t: SalesRecord?): Double {
        if (t == null || t.isMerged || t.transactionType == "OLD_DEBT") return 0.0
        if (t.isRepReturn || t.paymentType == "REP_RETURN") return max(0.0, t.returnedQty) + max(0.0, t.expiredQty)
        if (isCollectionRow(t)) return max(0.0, t.expiredQty)
        return 0.0
    }

    fun netSoldQuantity(t: SalesRecord?): Double = InputGuards.round2(soldQuantity(t) - returnedQuantity(t))

    fun receivedPercent(received: Double, debt: Double): Int {
        val got = max(0.0, received)
        val owed = max(0.0, debt)
        val total = got + owed
        return if (total > 0.005) ((got / total) * 100.0).toInt().coerceIn(0, 100) else 0
    }

    fun reconciledRepReturnIds(calculator: List<CalculatorRecord>): Set<String> =
        calculator.filter { it.deletedAt == null }.flatMap { it.linkedRepReturnIds }.toSet()

    fun effectiveSaleValue(
        t: SalesRecord,
        customers: List<CustomerRecord>,
        stores: List<StoreConfig>
    ): Double {
        if (t.isMerged) return t.totalValue
        if (t.paymentType in LEDGER_PAYMENT_TYPES || t.transactionType == "OLD_DEBT") return t.totalValue
        lockedSaleValue(t)?.let { if (it > 0.0) return it }
        val custRate = customers.firstOrNull { it.deletedAt == null && it.name.equals(t.customerName.trim(), ignoreCase = true) }?.customSalePrice ?: 0.0
        val storeRate = stores.firstOrNull { it.key.equals(t.supplyStore, ignoreCase = true) || it.name.equals(t.supplyStore, ignoreCase = true) }?.salePrice ?: 0.0
        val rate = if (custRate > 0.0) custRate else storeRate
        return InputGuards.round2(t.quantity * rate)
    }

    fun computeCustomerBalance(
        customerName: String,
        customers: List<CustomerRecord>,
        sales: List<SalesRecord>,
        repRecords: List<SalesRecord>,
        calculator: List<CalculatorRecord> = emptyList(),
        stores: List<StoreConfig> = emptyList(),
        clients: List<CustomerRecord> = emptyList(),
        includeClients: Boolean = true
    ): Double {
        val key = customerName.trim().lowercase(Locale.ROOT)
        if (key.isBlank()) return 0.0
        val reconciled = reconciledRepReturnIds(calculator)

        val directRows = sales.filter { it.deletedAt == null && it.customerName.trim().lowercase(Locale.ROOT) == key }
        val directHonor = allocHonor(directRows)
        val directHasOldDebtTx = directRows.any { it.transactionType == "OLD_DEBT" }
        val directOpening = if (directHasOldDebtTx) 0.0 else customers
            .filter { it.deletedAt == null && it.name.trim().lowercase(Locale.ROOT) == key }
            .sumOf { it.oldDebit }

        val repRows = repRecords.filter { it.deletedAt == null && it.customerName.trim().lowercase(Locale.ROOT) == key }
        val repHonor = allocHonor(repRows)
        val repHasOldDebtTx = repRows.any { it.transactionType == "OLD_DEBT" }
        val repOpening = if (!includeClients || repHasOldDebtTx) 0.0 else clients
            .filter { it.deletedAt == null && it.name.trim().lowercase(Locale.ROOT) == key }
            .sumOf { it.oldDebit }

        val directNet = directRows.sumOf { s ->
            ledgerDelta(s, effectiveSaleValue(s, customers, stores), reconciled, directHonor)
        }
        val repNet = repRows.sumOf { r ->
            ledgerDelta(r, effectiveSaleValue(r, clients.ifEmpty { customers }, stores), reconciled, repHonor)
        }
        return InputGuards.round2(directOpening + repOpening + directNet + repNet)
    }

    fun computeCollectionFifoPlan(
        paymentAmount: Double,
        openCreditRows: List<SalesRecord>,
        customers: List<CustomerRecord>,
        stores: List<StoreConfig>
    ): Map<String, Pair<Double, Boolean>> {
        var remaining = max(0.0, paymentAmount)
        val plan = LinkedHashMap<String, Pair<Double, Boolean>>()
        val sorted = openCreditRows
            .filter { it.deletedAt == null && !it.creditReceived && (it.paymentType == "CREDIT" || it.transactionType == "OLD_DEBT") }
            .sortedWith(compareBy<SalesRecord> { it.date }.thenBy { it.timestamp })
        for (s in sorted) {
            if (remaining <= 0.005) break
            val gross = effectiveSaleValue(s, customers, stores)
            val due = max(0.0, gross - s.partialPaymentReceived)
            if (due <= 0.005) continue
            val applied = InputGuards.round2(min(remaining, due))
            val full = applied + 0.005 >= due
            plan[s.id] = applied to full
            remaining = InputGuards.round2(remaining - applied)
        }
        return plan
    }

    // --- Raw Material & Purchase Lots (raw-purchases.js) ---

    fun normMaterialName(s: String): String = s.trim().lowercase(Locale.ROOT)

    fun materialRecords(inventory: List<InventoryRecord>): List<InventoryRecord> =
        inventory.filter { it.deletedAt == null && !it.isPurchaseLot }

    fun findMaterialByName(inventory: List<InventoryRecord>, name: String): InventoryRecord? {
        val key = normMaterialName(name)
        if (key.isBlank()) return null
        return materialRecords(inventory).firstOrNull { normMaterialName(it.name) == key }
    }

    fun purchasesOf(inventory: List<InventoryRecord>, materialId: String): List<InventoryRecord> =
        inventory.filter { it.deletedAt == null && it.isPurchaseLot && it.materialRefId == materialId }
            .sortedWith(compareByDescending<InventoryRecord> { it.purchaseDate }.thenByDescending { it.timestamp })

    fun collectedKg(material: InventoryRecord?): Double {
        if (material == null) return 0.0
        return if (material.collectedKg >= 0.0 && material.collectedKg > 0.0) InputGuards.round6(material.collectedKg)
        else InputGuards.round6(material.quantity)
    }

    fun pendingOf(rec: InventoryRecord?): Double {
        if (rec == null || rec.supplierId.isBlank()) return 0.0
        if (rec.paymentStatus == "paid") return 0.0
        return max(0.0, InputGuards.round2(rec.totalPayable))
    }

    fun materialPending(material: InventoryRecord?, lots: List<InventoryRecord>): Double =
        InputGuards.round2(pendingOf(material) + lots.sumOf { pendingOf(it) })

    data class SupplierAllocationPreview(
        val lotId: String,
        val date: String,
        val materialName: String,
        val beforePayable: Double,
        val appliedPayment: Double,
        val afterPayable: Double
    )

    fun previewSupplierFifoAllocation(
        supplierIdOrName: String,
        paymentAmount: Double,
        inventory: List<InventoryRecord>
    ): List<SupplierAllocationPreview> {
        var remaining = max(0.0, paymentAmount)
        val needle = supplierIdOrName.trim()
        val openLots = inventory.filter { rec ->
            rec.deletedAt == null &&
                rec.paymentStatus != "paid" &&
                rec.totalPayable > 0.005 &&
                (rec.supplierId == needle || rec.supplierName.equals(needle, ignoreCase = true))
        }.sortedWith(compareBy<InventoryRecord> { it.purchaseDate.ifBlank { "9999-99-99" } }.thenBy { it.timestamp })

        val out = mutableListOf<SupplierAllocationPreview>()
        for (lot in openLots) {
            if (remaining <= 0.005) break
            val applied = InputGuards.round2(min(remaining, lot.totalPayable))
            remaining = InputGuards.round2(remaining - applied)
            out.add(
                SupplierAllocationPreview(
                    lotId = lot.id,
                    date = lot.purchaseDate,
                    materialName = lot.materialName.ifBlank { lot.name },
                    beforePayable = InputGuards.round2(lot.totalPayable),
                    appliedPayment = applied,
                    afterPayable = InputGuards.round2(max(0.0, lot.totalPayable - applied))
                )
            )
        }
        return out
    }

    // --- Store Stock & Cost Lookup ---

    data class StoreStockSnapshot(
        val storeKey: String,
        val storeName: String,
        val producedKg: Double,
        val soldKg: Double,
        val returnedKg: Double,
        val availableKg: Double,
        val avgCostPerKg: Double,
        val salePrice: Double
    )

    fun computeStoreStockSnapshots(snapshot: WorkspaceSnapshot): List<StoreStockSnapshot> {
        val prod = snapshot.production.filter { it.deletedAt == null && !it.isReturn }
        val ret = snapshot.returns.filter { it.deletedAt == null }
        val sales = snapshot.sales.filter { it.deletedAt == null && !isLedgerRow(it) }
        val costIdx = FormulaEngine.buildCostIndex(snapshot.production)

        val configuredKeys = snapshot.stores.map { it.key }.toSet()
        val activeStores = snapshot.stores.filter { it.deletedAt == null && it.active }
        val extraStoreKeys = (prod.map { it.store } + ret.map { it.store } + sales.map { it.supplyStore })
            .map { it.trim() }
            .filter { k -> k.isNotBlank() && configuredKeys.none { it.equals(k, ignoreCase = true) } }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .map { StoreConfig(key = it, name = it, active = true) }

        return (activeStores + extraStoreKeys).map { st ->
            fun matchesStore(candidate: String): Boolean {
                val c = candidate.trim()
                return c.equals(st.key, ignoreCase = true) || c.equals(st.name, ignoreCase = true)
            }
            val produced = prod.filter { matchesStore(it.store) }.sumOf { it.net }
            val returned = ret.filter { matchesStore(it.store) }.sumOf { it.quantity }
            val sold = sales.filter { matchesStore(it.supplyStore) }.sumOf { it.quantity }
            val available = InputGuards.round3(produced + returned - sold)
            val avgCost = costIdx.byStore[st.key.lowercase(Locale.ROOT)]
                ?: costIdx.byStore[st.name.lowercase(Locale.ROOT)]
                ?: snapshot.settings.storeCosts[st.key]
                ?: 0.0
            val price = if (st.salePrice > 0.0) st.salePrice else (snapshot.settings.defaultStorePrices[st.key] ?: 0.0)
            StoreStockSnapshot(
                storeKey = st.key,
                storeName = st.name,
                producedKg = InputGuards.round3(produced),
                soldKg = InputGuards.round3(sold),
                returnedKg = InputGuards.round3(returned),
                availableKg = available,
                avgCostPerKg = InputGuards.round2(avgCost),
                salePrice = InputGuards.round2(price)
            )
        }
    }

    fun computeStoreStockOnDate(
        storeKeyOrName: String,
        dateIso: String,
        snapshot: WorkspaceSnapshot,
        excludeProdIds: Set<String> = emptySet()
    ): Double {
        fun matchesStore(c: String) = c.trim().equals(storeKeyOrName.trim(), ignoreCase = true) ||
            FormulaEngine.storeLabelFor(c, snapshot.stores).equals(FormulaEngine.storeLabelFor(storeKeyOrName, snapshot.stores), ignoreCase = true)
        val produced = snapshot.production.filter {
            it.deletedAt == null && !it.isReturn && it.id !in excludeProdIds &&
                it.date.take(10) == dateIso.take(10) && matchesStore(it.store)
        }.sumOf { it.net }
        val returned = snapshot.returns.filter {
            it.deletedAt == null && it.date.take(10) == dateIso.take(10) && matchesStore(it.store)
        }.sumOf { it.quantity }
        val sold = snapshot.sales.filter {
            it.deletedAt == null && !isLedgerRow(it) &&
                (it.supplyDate.ifBlank { it.date }).take(10) == dateIso.take(10) &&
                matchesStore(it.supplyStore)
        }.sumOf { it.quantity }
        return InputGuards.round3(produced + returned - sold)
    }

    // --- Entity Balances ---

    fun computeEntityBalance(
        entity: EntityRecord,
        transactions: List<PaymentTransaction>,
        inventory: List<InventoryRecord> = emptyList()
    ): Double {
        val supplierPayable = inventory.filter {
            it.deletedAt == null &&
                it.paymentStatus != "paid" &&
                it.totalPayable > 0.0 &&
                (it.supplierId == entity.id || it.supplierName.equals(entity.name.trim(), ignoreCase = true))
        }.sumOf { it.totalPayable }

        val supplierIds = inventory.mapNotNull { it.supplierId.takeIf { s -> s.isNotBlank() } }.toSet()
        if (entity.isSupplier || entity.type == "supplier" || entity.id in supplierIds) {
            // For suppliers, positive owed is represented as negative or payable; expose raw payable for supplier view
            val netPayments = transactions.filter {
                it.deletedAt == null && !it.isPayable &&
                    (it.entityId == entity.id || it.entityName.equals(entity.name.trim(), ignoreCase = true))
            }.sumOf { if (it.type.equals("IN", ignoreCase = true)) it.amount else -it.amount }
            return InputGuards.round2(entity.openingBalance + netPayments - supplierPayable)
        }

        val txNet = transactions.filter {
            it.deletedAt == null && !it.isPayable &&
                (it.entityId == entity.id || it.entityName.equals(entity.name.trim(), ignoreCase = true))
        }.sumOf {
            when (it.type.uppercase(Locale.ROOT)) {
                "IN" -> it.amount
                "OUT" -> -it.amount
                else -> 0.0
            }
        }
        return InputGuards.round2(entity.openingBalance + txNet)
    }

    // --- Daily Ledger Core (ledger-core.js) ---

    private val HOME_WORDS = listOf(
        "home", "grocer", "school", "tuition", "maid", "doctor", "medical", "clothing",
        "personal", "family", "kitchen", "house", "gas", "water", "ration", "sabzi",
        "milk", "child", "domestic", "food", "shopping", "charity", "gift"
    )

    fun isCashMode(mode: String): Boolean =
        mode.trim().lowercase(Locale.ROOT) in setOf("cash", "cash in hand", "petty cash")

    fun isBankMode(mode: String): Boolean {
        val low = mode.trim().lowercase(Locale.ROOT)
        return low.contains("bank") || low in setOf("cheque", "online")
    }

    fun isHomeExpense(category: String, scope: String = ""): Boolean {
        val s = scope.trim().uppercase(Locale.ROOT)
        if (s.contains("HOME") || s.contains("PERSONAL")) return true
        val c = category.trim().lowercase(Locale.ROOT)
        if (c.isBlank()) return false
        return HOME_WORDS.any { c.contains(it) }
    }

    data class DailyLedgerSummary(
        val totalIn: Double,
        val totalOut: Double,
        val totalTransferred: Double,
        val inCount: Int,
        val outCount: Int,
        val transferCount: Int,
        val cash: Double,
        val bank: Double,
        val others: Double,
        val inHand: Double,
        val byMode: Map<String, Double>,
        val byCategory: Map<String, Double>,
        val statement: Map<String, Pair<Double, Double>> // id -> (before, after)
    )

    fun summarizeDailyLedger(
        entries: List<DailyLedgerEntry>,
        paymentModes: List<String> = WorkspaceSettings().paymentModes
    ): DailyLedgerSummary {
        val sorted = entries.sortedWith(
            compareBy<DailyLedgerEntry> { it.date }
                .thenBy { it.timestamp }
                .thenBy { it.id }
        )
        var running = 0.0
        val statement = LinkedHashMap<String, Pair<Double, Double>>()
        var totalIn = 0.0
        var totalOut = 0.0
        var totalTransferred = 0.0
        var inCount = 0
        var outCount = 0
        var transferCount = 0
        var cash = 0.0
        var bank = 0.0
        var others = 0.0
        val byMode = LinkedHashMap<String, Double>()
        paymentModes.forEach { byMode[it] = 0.0 }
        val byCategory = LinkedHashMap<String, Double>()

        fun bumpMode(mode: String, amt: Double) {
            val m = mode.ifBlank { "Cash" }
            if (isCashMode(m)) cash += amt
            else if (isBankMode(m)) bank += amt
            else others += amt
            byMode[m] = InputGuards.round2((byMode[m] ?: 0.0) + amt)
        }

        sorted.forEach { t ->
            val before = InputGuards.round2(running)
            val impact = when (t.type.uppercase(Locale.ROOT)) {
                "OUT" -> -t.amount
                "TRANSFER" -> 0.0
                else -> t.amount
            }
            running = InputGuards.round2(running + impact)
            statement[t.id] = before to running

            when (t.type.uppercase(Locale.ROOT)) {
                "OUT" -> {
                    totalOut += t.amount
                    outCount++
                    bumpMode(t.mode, -t.amount)
                    val cat = t.category.ifBlank { "Expense" }
                    byCategory[cat] = InputGuards.round2((byCategory[cat] ?: 0.0) + t.amount)
                }
                "TRANSFER" -> {
                    totalTransferred += t.amount
                    transferCount++
                    val from = t.mode.ifBlank { "Cash" }
                    val to = t.toMode.ifBlank { if (isCashMode(from)) "Bank Transfer" else "Cash" }
                    bumpMode(from, -t.amount)
                    bumpMode(to, t.amount)
                }
                else -> {
                    totalIn += t.amount
                    inCount++
                    bumpMode(t.mode, t.amount)
                    val cat = t.category.ifBlank { "Sales" }
                    byCategory[cat] = InputGuards.round2((byCategory[cat] ?: 0.0) + t.amount)
                }
            }
        }

        val inHand = InputGuards.round2(totalIn - totalOut)
        return DailyLedgerSummary(
            totalIn = InputGuards.round2(totalIn),
            totalOut = InputGuards.round2(totalOut),
            totalTransferred = InputGuards.round2(totalTransferred),
            inCount = inCount,
            outCount = outCount,
            transferCount = transferCount,
            cash = InputGuards.round2(cash),
            bank = InputGuards.round2(bank),
            others = InputGuards.round2(others),
            inHand = inHand,
            byMode = byMode,
            byCategory = byCategory,
            statement = statement
        )
    }

    fun computeChannelBalances(snapshot: WorkspaceSnapshot): Map<String, Double> =
        summarizeDailyLedger(snapshot.ledgerEntries, snapshot.settings.paymentModes).byMode

    fun computeAvailableCashInHand(snapshot: WorkspaceSnapshot): Double =
        summarizeDailyLedger(snapshot.ledgerEntries, snapshot.settings.paymentModes).cash

    // --- Economic Health Dashboard ---

    data class EconomicHealthSummary(
        val cashInHand: Double,
        val rawMaterialValue: Double,
        val formulaUnitsValue: Double,
        val finishedStockValue: Double,
        val customerReceivables: Double,
        val entityReceivables: Double,
        val totalAssets: Double,
        val supplierPayables: Double,
        val entityPayables: Double,
        val totalLiabilities: Double,
        val netWorkingCapital: Double,
        val enterpriseValue: Double,
        val currentRatio: Double
    )

    fun calculateEconomicHealth(snapshot: WorkspaceSnapshot): EconomicHealthSummary {
        val ledgerSum = summarizeDailyLedger(snapshot.ledgerEntries, snapshot.settings.paymentModes)
        val cash = ledgerSum.inHand
        val rawValue = materialRecords(snapshot.inventory)
            .filter { it.quantity > 0.0 }
            .sumOf { it.quantity * it.cost }
        val unitValue = FormulaEngine.buildUnitPools(
            snapshot.stores, snapshot.formulas, snapshot.factory, snapshot.production
        ).filter { it.availableUnits > 0.0 }.sumOf { it.availableUnits * it.avgUnitCost }
        val finishedValue = computeStoreStockSnapshots(snapshot)
            .filter { it.availableKg > 0.0 }
            .sumOf { it.availableKg * it.avgCostPerKg }

        val allCustomerNames = (snapshot.customers.filter { it.deletedAt == null }.map { it.name } +
            snapshot.sales.filter { it.deletedAt == null }.map { it.customerName } +
            snapshot.repRecords.filter { it.deletedAt == null }.map { it.customerName } +
            snapshot.clients.filter { it.deletedAt == null }.map { it.name })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }

        val receivables = allCustomerNames.sumOf { name ->
            computeCustomerBalance(
                name, snapshot.customers, snapshot.sales, snapshot.repRecords,
                snapshot.calculator, snapshot.stores, snapshot.clients
            ).coerceAtLeast(0.0)
        }

        val supplierPayables = snapshot.inventory
            .filter { it.deletedAt == null && it.paymentStatus != "paid" && it.totalPayable > 0.0 }
            .sumOf { it.totalPayable }

        var entityReceivables = 0.0
        var entityPayables = 0.0
        val supplierIds = snapshot.inventory.mapNotNull { it.supplierId.takeIf { s -> s.isNotBlank() } }.toSet()
        snapshot.entities.filter {
            it.deletedAt == null && !it.isExpenseEntity && !it.isSupplier && it.type != "supplier" && it.id !in supplierIds
        }.forEach { entity ->
            val bal = computeEntityBalance(entity, snapshot.transactions, snapshot.inventory)
            if (bal > 0.0) entityReceivables += bal else if (bal < 0.0) entityPayables += abs(bal)
        }

        val totalAssets = InputGuards.round2(cash + rawValue + unitValue + finishedValue + receivables + entityReceivables)
        val totalLiab = InputGuards.round2(supplierPayables + entityPayables)
        val nwc = InputGuards.round2(totalAssets - totalLiab)
        val ratio = if (totalLiab > 0.01) InputGuards.round2(totalAssets / totalLiab) else 99.9

        return EconomicHealthSummary(
            cashInHand = InputGuards.round2(cash),
            rawMaterialValue = InputGuards.round2(rawValue),
            formulaUnitsValue = InputGuards.round2(unitValue),
            finishedStockValue = InputGuards.round2(finishedValue),
            customerReceivables = InputGuards.round2(receivables),
            entityReceivables = InputGuards.round2(entityReceivables),
            totalAssets = totalAssets,
            supplierPayables = InputGuards.round2(supplierPayables),
            entityPayables = InputGuards.round2(entityPayables),
            totalLiabilities = totalLiab,
            netWorkingCapital = nwc,
            enterpriseValue = nwc,
            currentRatio = ratio
        )
    }

    // --- Unreconciled Rep Sales Helpers ---

    fun getUnreconciledRepDeliveriesKg(
        repName: String,
        storeKey: String,
        dateFrom: String,
        dateTo: String,
        sales: List<SalesRecord>
    ): Double {
        return InputGuards.round3(
            sales.filter {
                it.deletedAt == null &&
                    !isLedgerRow(it) &&
                    it.salesRep.equals(repName, ignoreCase = true) &&
                    (storeKey.isBlank() || it.supplyStore.equals(storeKey, ignoreCase = true)) &&
                    DateUtils.isInRange(it.date, dateFrom, dateTo)
            }.sumOf { it.quantity }
        )
    }
}
