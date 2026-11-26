package com.gullzubair.sarim.ui.viewmodel

import com.gullzubair.sarim.data.firebase.AppRepository
import com.gullzubair.sarim.data.firebase.WorkspaceOperations
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.CalculatorRecord
import com.gullzubair.sarim.data.model.CustomerRecord
import com.gullzubair.sarim.data.model.DailyLedgerEntry
import com.gullzubair.sarim.data.model.EntityRecord
import com.gullzubair.sarim.data.model.ExpenseRecord
import com.gullzubair.sarim.data.model.FactoryRecord
import com.gullzubair.sarim.data.model.FormulaConfig
import com.gullzubair.sarim.data.model.FormulaIngredient
import com.gullzubair.sarim.data.model.InventoryRecord
import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.ReturnRecord
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.SavedCalcRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.WorkspaceAccountRecord
import com.gullzubair.sarim.data.model.WorkspaceSettings
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.RepSalesAnalyticsEngine
import com.gullzubair.sarim.domain.SalesAnalyticsEngine
import com.gullzubair.sarim.util.LocationHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class SarimActionDispatcher(
    private val repo: AppRepository,
    private val ops: WorkspaceOperations
) {
    internal fun resolvedStoreKey(store: StoreConfig): String = store.key.ifBlank { store.name }

    internal suspend fun appendDailyLedgerEntry(
        snapshot: WorkspaceSnapshot,
        entry: DailyLedgerEntry
    ): Result<Unit> = appendDailyLedgerEntries(snapshot, listOf(entry))

    internal suspend fun appendDailyLedgerEntries(
        snapshot: WorkspaceSnapshot,
        entries: List<DailyLedgerEntry>
    ): Result<Unit> {
        if (entries.isEmpty()) return Result.success(Unit)
        val ownerUid = snapshot.profile.effectiveOwnerUid
        val ids = entries.map { it.id }.toSet()
        val nextList = (entries + snapshot.ledgerEntries.filterNot { it.id in ids })
            .sortedByDescending { it.date }
        val now = System.currentTimeMillis()
        val rawList = nextList.map(WorkspaceJsonCodec::dailyLedgerEntryToMap)
        return repo.saveSingleton(
            ownerUid = ownerUid,
            collection = "daily_ledger",
            docId = "entries",
            payload = mapOf(
                "daily_ledger" to rawList,
                "daily_ledger_timestamp" to now
            ),
            kvUpdates = mapOf(
                "daily_ledger" to WorkspaceJsonCodec.mapListToJson(rawList),
                "daily_ledger_timestamp" to now.toString()
            )
        )
    }

    suspend fun syncLedgerMirrorToStorage(snapshot: WorkspaceSnapshot): FinanceEngine.LedgerSyncResult {
        val syncRes = FinanceEngine.syncLedgerEntriesWithSnapshot(snapshot)
        if (syncRes.removedEntries.isEmpty() && syncRes.updatedCount == 0) return syncRes
        val ownerUid = snapshot.profile.effectiveOwnerUid
        val now = System.currentTimeMillis()
        syncRes.removedEntries.forEach { gone ->
            ops.softDeleteToRecycleBin(
                profile = snapshot.profile,
                collection = "daily_ledger",
                docId = gone.id,
                displayName = gone.party.ifBlank { gone.category },
                displayDetail = gone.date,
                displayAmount = InputGuards.fmtNum(gone.amount),
                snapshotMap = WorkspaceJsonCodec.dailyLedgerEntryToMap(gone)
            )
        }
        val rawList = syncRes.nextEntries.sortedByDescending { it.date }.map(WorkspaceJsonCodec::dailyLedgerEntryToMap)
        repo.saveSingleton(
            ownerUid = ownerUid,
            collection = "daily_ledger",
            docId = "entries",
            payload = mapOf(
                "daily_ledger" to rawList,
                "daily_ledger_timestamp" to now
            ),
            kvUpdates = mapOf(
                "daily_ledger" to WorkspaceJsonCodec.mapListToJson(rawList),
                "daily_ledger_timestamp" to now.toString()
            )
        )
        return syncRes
    }

    suspend fun saveDailyLedgerEntry(
        snapshot: WorkspaceSnapshot,
        existingId: String?,
        date: String,
        type: String,
        scope: String,
        mode: String,
        toMode: String,
        category: String,
        party: String,
        amount: Double,
        ref: String,
        note: String
    ): Result<String> {
        if (amount <= 0.0 || amount > InputGuards.MAX_AMOUNT) {
            return Result.failure(IllegalArgumentException("Enter a valid amount greater than 0"))
        }
        val normType = type.trim().uppercase(Locale.ROOT).let {
            if (it in setOf("IN", "OUT", "TRANSFER")) it else "IN"
        }
        val cleanMode = mode.trim().ifBlank { "Cash" }
        val cleanToMode = if (normType == "TRANSFER") {
            toMode.trim().ifBlank { if (FinanceEngine.isCashMode(cleanMode)) "Bank" else "Cash" }
        } else ""
        if (normType == "TRANSFER" && cleanMode.equals(cleanToMode, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Source and destination modes must differ"))
        }
        val resolvedScope = if (normType == "OUT") {
            if (scope.equals("HOME", ignoreCase = true) || FinanceEngine.isHomeExpense(category, scope)) "HOME" else "BUSINESS"
        } else ""
        val now = System.currentTimeMillis()
        val entryId = existingId?.takeIf { it.isNotBlank() } ?: ops.newId("dl")
        val existing = snapshot.ledgerEntries.firstOrNull { it.id == entryId }
        val entry = DailyLedgerEntry(
            id = entryId,
            type = normType,
            amount = InputGuards.round2(amount),
            category = category.trim().ifBlank { if (normType == "OUT") "Expense" else if (normType == "TRANSFER") "Account Transfer" else "Sales" },
            scope = resolvedScope,
            mode = cleanMode,
            toMode = cleanToMode,
            party = InputGuards.sanitizeName(party),
            note = InputGuards.sanitizeNotes(note),
            ref = ref.trim().take(60),
            date = date,
            timestamp = existing?.timestamp?.takeIf { it > 0L } ?: now,
            updatedAt = now,
            linkCollection = existing?.linkCollection,
            linkDocId = existing?.linkDocId
        )
        val nextList = (listOf(entry) + snapshot.ledgerEntries.filterNot { it.id == entryId })
            .sortedByDescending { it.date }
        val rawList = nextList.map(WorkspaceJsonCodec::dailyLedgerEntryToMap)
        val ownerUid = snapshot.profile.effectiveOwnerUid
        val res = repo.saveSingleton(
            ownerUid = ownerUid,
            collection = "daily_ledger",
            docId = "entries",
            payload = mapOf(
                "daily_ledger" to rawList,
                "daily_ledger_timestamp" to now
            ),
            kvUpdates = mapOf(
                "daily_ledger" to WorkspaceJsonCodec.mapListToJson(rawList),
                "daily_ledger_timestamp" to now.toString()
            )
        )
        return res.map { entryId }
    }

    suspend fun removeDailyLedgerEntry(snapshot: WorkspaceSnapshot, entryId: String): Result<Unit> {
        val ownerUid = snapshot.profile.effectiveOwnerUid
        val now = System.currentTimeMillis()
        val nextList = snapshot.ledgerEntries.filterNot { it.id == entryId }
        val rawList = nextList.map(WorkspaceJsonCodec::dailyLedgerEntryToMap)
        return repo.saveSingleton(
            ownerUid = ownerUid,
            collection = "daily_ledger",
            docId = "entries",
            payload = mapOf(
                "daily_ledger" to rawList,
                "daily_ledger_timestamp" to now
            ),
            kvUpdates = mapOf(
                "daily_ledger" to WorkspaceJsonCodec.mapListToJson(rawList),
                "daily_ledger_timestamp" to now.toString()
            )
        )
    }

    suspend fun saveProduction(
        snapshot: WorkspaceSnapshot,
        date: String,
        store: StoreConfig,
        grossWt: Double,
        contWt: Double,
        formulaUnits: Double,
        costPerKgOverride: Double,
        notes: String,
        photoDataUri: String? = null,
        photoDataUris: List<String> = emptyList()
    ): Result<String> {
        val net = InputGuards.round2(grossWt - contWt)
        if (net <= 0.0) return Result.failure(IllegalArgumentException("Net production must be greater than zero. Please check weights."))
        if (formulaUnits <= 0.0) return Result.failure(IllegalArgumentException("Please enter formula units used."))

        val storeKey = resolvedStoreKey(store)
        val pools = FormulaEngine.buildUnitPools(snapshot.stores, snapshot.formulas, snapshot.factory, snapshot.production)
        val pool = com.gullzubair.sarim.domain.ProductionAnalyticsEngine.resolvePoolForStore(store, pools)
        val availableUnits = pool?.availableUnits ?: 0.0
        if (costPerKgOverride <= 0.0 && availableUnits + 1e-9 < formulaUnits) {
            return Result.failure(
                IllegalArgumentException(
                    "Not enough formula units at ${store.name}. Available: ${InputGuards.fmtNum(availableUnits)}, Requested: ${InputGuards.fmtNum(formulaUnits)}"
                )
            )
        }

        val formula = snapshot.formulas.firstOrNull { it.id == store.formulaId }
        val desc = formula?.let { FormulaEngine.describeFormula(it, snapshot.formulas, snapshot.inventory) }
        val formulaCostTotal = if (desc != null && formulaUnits > 0.0) InputGuards.round2(desc.perUnit * formulaUnits) else 0.0
        val resolvedCp = when {
            costPerKgOverride > 0.0 -> InputGuards.round2(costPerKgOverride)
            formulaCostTotal > 0.0 -> InputGuards.round2(formulaCostTotal / net)
            else -> {
                val idx = FormulaEngine.buildCostIndex(snapshot.production)
                val lookup = FormulaEngine.costSourceFromIndex(idx, storeKey, date)
                if (lookup.cp > 0.0) lookup.cp else (snapshot.settings.storeCosts[store.key] ?: 0.0)
            }
        }
        val sp = if (store.salePrice > 0.0) store.salePrice else (snapshot.settings.defaultStorePrices[store.key] ?: 0.0)
        if (sp <= 0.0) {
            return Result.failure(IllegalArgumentException("Please set a sale price in Factory Formulas first."))
        }
        if (resolvedCp <= 0.0) {
            return Result.failure(IllegalArgumentException("This formula has no cost. Set the formula and ingredient costs first."))
        }
        val totalCost = if (formulaCostTotal > 0.0 && costPerKgOverride <= 0.0) formulaCostTotal else InputGuards.round2(net * resolvedCp)
        val totalSale = InputGuards.round2(net * sp)
        val profit = InputGuards.round2(totalSale - totalCost)

        val allPhotos = (photoDataUris + listOfNotNull(photoDataUri?.takeIf { it.isNotBlank() }))
            .distinct()
            .take(6)
        val photoKeys = mutableListOf<String>()
        allPhotos.forEach { uri ->
            val pk = ops.newId("photo_prod")
            ops.savePhoto(snapshot.profile, pk, uri)
            photoKeys.add(pk)
        }

        val record = ProductionRecord(
            id = ops.newId("prod"),
            date = date,
            time = DateUtils.nowTimeStr(),
            store = storeKey,
            grossWt = InputGuards.round2(grossWt),
            contWt = InputGuards.round2(contWt),
            net = net,
            cp = resolvedCp,
            sp = sp,
            totalCost = totalCost,
            totalSale = totalSale,
            profit = profit,
            formulaUnits = InputGuards.round2(formulaUnits),
            formulaId = formula?.id ?: "",
            formulaName = formula?.name ?: "",
            formulaMaterials = desc?.items?.map {
                mapOf("id" to it.id, "name" to it.name, "qty" to InputGuards.round4(it.quantity * formulaUnits))
            } ?: emptyList(),
            formulaCost = formulaCostTotal,
            paymentStatus = "CASH",
            photoKeys = photoKeys,
            returnNote = InputGuards.sanitizeNotes(notes),
            createdBy = snapshot.profile.uid,
            recordDate = date,
            timestamp = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "production",
            docId = record.id,
            summaryTitle = "Production: ${store.name} (${InputGuards.formatKg(net)})",
            summaryDetail = "$date • Units ${InputGuards.fmtNum(formulaUnits)} • Profit ${InputGuards.fmtNum(profit)}",
            payload = WorkspaceJsonCodec.productionToMap(record)
        )
    }

    suspend fun saveStockReturn(
        snapshot: WorkspaceSnapshot,
        date: String,
        store: StoreConfig,
        quantityKg: Double,
        expiredKg: Double,
        returnedBy: String,
        notes: String
    ): Result<String> {
        if (quantityKg <= 0.0 && expiredKg <= 0.0) {
            return Result.failure(IllegalArgumentException("Return or expired quantity must be greater than 0"))
        }
        val storeKey = resolvedStoreKey(store)
        val idx = FormulaEngine.buildCostIndex(snapshot.production)
        val cp = FormulaEngine.costSourceFromIndex(idx, storeKey, date).cp
        val sp = if (store.salePrice > 0.0) store.salePrice else (snapshot.settings.defaultStorePrices[store.key] ?: 0.0)
        val now = System.currentTimeMillis()

        if (quantityKg > 0.0) {
            val prodEntry = ProductionRecord(
                id = ops.newId("ret"),
                date = date,
                time = DateUtils.nowTimeStr(),
                store = storeKey,
                net = InputGuards.round2(quantityKg),
                cp = cp,
                sp = sp,
                totalCost = InputGuards.round2(quantityKg * cp),
                totalSale = InputGuards.round2(quantityKg * sp),
                profit = InputGuards.round2(quantityKg * (sp - cp)),
                isReturn = true,
                returnedBy = InputGuards.sanitizeName(returnedBy),
                returnNote = InputGuards.sanitizeNotes(notes).ifBlank { "Returned by ${returnedBy.ifBlank { "Store" }}" },
                createdBy = snapshot.profile.uid,
                recordDate = date,
                timestamp = now,
                createdAt = now
            )
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "production",
                docId = prodEntry.id,
                summaryTitle = "Return: ${store.name} (${InputGuards.formatKg(quantityKg)})",
                summaryDetail = "$date • ${returnedBy.ifBlank { "Store return" }}",
                payload = WorkspaceJsonCodec.productionToMap(prodEntry)
            )
        }

        if (expiredKg > 0.0 && snapshot.profile.isAdmin) {
            addChoraToInventory(snapshot, expiredKg)
        }

        val retLog = ReturnRecord(
            id = ops.newId("retlog"),
            date = date,
            store = storeKey,
            returnStore = storeKey,
            seller = InputGuards.sanitizeName(returnedBy),
            quantity = InputGuards.round2(quantityKg),
            expiredQty = InputGuards.round2(expiredKg),
            returnType = "store",
            notes = InputGuards.sanitizeNotes(notes),
            timestamp = now
        )
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "returns",
            docId = retLog.id,
            summaryTitle = "Return Log: ${store.name}",
            summaryDetail = "$date • ${InputGuards.formatKg(quantityKg)}",
            payload = WorkspaceJsonCodec.returnToMap(retLog)
        )
    }

    suspend fun recoverReturnChora(snapshot: WorkspaceSnapshot, ret: ReturnRecord): Result<String> {
        if (ret.recovered) return Result.failure(IllegalArgumentException("CHORA already recovered for this return"))
        val qty = if (ret.expiredQty > 0.0) ret.expiredQty else ret.quantity
        if (qty <= 0.0) return Result.failure(IllegalArgumentException("No quantity available to recover"))
        if (snapshot.profile.isAdmin) {
            addChoraToInventory(snapshot, qty)
        }
        val updated = ret.copy(
            recovered = true,
            recoveredQty = qty,
            recoveredDate = DateUtils.todayIso()
        )
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "returns",
            docId = ret.id,
            summaryTitle = "Recover CHORA (${InputGuards.formatKg(qty)})",
            summaryDetail = "${ret.date} • ${ret.store}",
            payload = WorkspaceJsonCodec.returnToMap(updated),
            isEdit = true
        )
    }

    internal suspend fun addChoraToInventory(snapshot: WorkspaceSnapshot, qtyKg: Double): Boolean {
        if (qtyKg == 0.0) return false
        val chora = FinanceEngine.findMaterialByName(snapshot.inventory, "CHORA") ?: return false
        val nextQty = InputGuards.round6(max(0.0, chora.quantity + qtyKg))
        val nextCollected = InputGuards.round6(max(0.0, FinanceEngine.collectedKg(chora) + qtyKg))
        val updated = chora.copy(
            quantity = nextQty,
            collectedKg = nextCollected,
            totalValue = InputGuards.round2(nextQty * chora.cost),
            timestamp = System.currentTimeMillis()
        )
        ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "inventory",
            docId = chora.id,
            summaryTitle = "CHORA ${if (qtyKg >= 0) "+" else ""}${InputGuards.formatKg(qtyKg)}",
            summaryDetail = "Recovered/Expired return",
            payload = WorkspaceJsonCodec.inventoryToMap(updated),
            isEdit = true
        )
        return true
    }

    suspend fun saveSaleOrCollection(
        snapshot: WorkspaceSnapshot,
        date: String,
        supplyDate: String,
        paymentType: String, // CASH | CREDIT | COLLECTION | MONEY_OUT
        customerName: String,
        store: StoreConfig?,
        salesRep: String,
        quantityKg: Double,
        unitPrice: Double,
        expiredKg: Double,
        expiredUnitPrice: Double,
        collectedAmount: Double,
        addToLedger: Boolean,
        notes: String,
        customerPhone: String = ""
    ): Result<String> {
        // Web `canonicalName`: reuse the stored spelling so "ali" / "Ali " never split one debtor into two.
        val cleanCust = InputGuards.sanitizeName(
            InputGuards.canonicalName(customerName, snapshot.customers.filter { it.deletedAt == null }.map { it.name })
        )
        if (cleanCust.isBlank()) return Result.failure(IllegalArgumentException("Customer name is required"))
        val normType = paymentType.trim().uppercase(Locale.ROOT)
        // Web `dateIssue` / `phoneIssue` / `nameIssue` / `quantityIssue` (same wording as the web app).
        val guardIssue = InputGuards.dateIssue(date, DateUtils.todayIso()).ifEmpty {
            InputGuards.phoneIssue(customerPhone)
        }.ifEmpty {
            if (normType == "REP_RETURN") "" else InputGuards.nameIssue(cleanCust, "Customer name")
        }.ifEmpty {
            if ((normType == "CASH" || normType == "CREDIT") && quantityKg > 0.0) {
                InputGuards.rangeIssue(quantityKg, "Quantity", InputGuards.QTY_MIN, InputGuards.QTY_MAX)
            } else ""
        }
        if (guardIssue.isNotEmpty()) return Result.failure(IllegalArgumentException(guardIssue))
        val cleanPhone = InputGuards.sanitizePhone(customerPhone)
        val now = System.currentTimeMillis()
        val storeKey = store?.let(::resolvedStoreKey).orEmpty()
        val existingCust = snapshot.customers.firstOrNull {
            it.deletedAt == null && it.name.equals(cleanCust, ignoreCase = true)
        }
        val resolvedPhone = cleanPhone.ifBlank { existingCust?.phone ?: "" }

        if (normType == "REP_RETURN") {
            val cleanRep = salesRep.trim().takeIf { it.isNotBlank() && !it.equals("NONE", true) } ?: cleanCust
            if (quantityKg < 0.0 || expiredKg < 0.0) {
                return Result.failure(IllegalArgumentException("Quantities cannot be negative"))
            }
            if (quantityKg <= 0.0 && expiredKg <= 0.0) {
                return Result.failure(IllegalArgumentException("Enter expired and/or returned quantity"))
            }
            val pendingInfo = SalesAnalyticsEngine.computeRepReturnPendingInfo(cleanRep, snapshot)
            val totalBack = InputGuards.round3(expiredKg + quantityKg)
            if (pendingInfo.pendingDeliveredKg > 0.0 && pendingInfo.alreadyReturnedKg + totalBack > pendingInfo.pendingDeliveredKg + 0.001) {
                return Result.failure(
                    IllegalArgumentException(
                        "Cannot return ${InputGuards.formatKg(totalBack)}. Pending deliveries total ${InputGuards.formatKg(pendingInfo.pendingDeliveredKg)} and ${InputGuards.formatKg(pendingInfo.alreadyReturnedKg)} already returned (remaining ${InputGuards.formatKg(pendingInfo.remainingDeliverableKg)})."
                    )
                )
            }
            val retUnit = if (unitPrice > 0.0) InputGuards.round2(unitPrice)
            else SalesAnalyticsEngine.computeGoodsCreditUnitPrice(cleanRep, snapshot, fallbackStore = store)
            val custOut = SalesAnalyticsEngine.computeCustomerOutstanding(cleanRep, snapshot)
            val repPlan = SalesAnalyticsEngine.planGoodsCredit(
                totalBack,
                retUnit,
                if (custOut >= 0.0) custOut else null
            )
            val docId = ops.newId("sale")
            val repRet = SalesRecord(
                id = docId,
                date = date,
                supplyDate = date,
                time = DateUtils.nowTimeStr(),
                customerName = cleanRep,
                customerPhone = "",
                quantity = 0.0,
                supplyStore = "",
                paymentType = "REP_RETURN",
                salesRep = cleanRep,
                currentRepProfile = "admin",
                unitPrice = if (repPlan.value > 0.0) retUnit else 0.0,
                totalCost = 0.0,
                totalValue = 0.0,
                profit = 0.0,
                creditReceived = true,
                isRepReturn = true,
                expiredQty = InputGuards.round2(expiredKg),
                returnedQty = InputGuards.round2(quantityKg),
                returnValue = repPlan.value,
                notes = InputGuards.sanitizeNotes(notes),
                timestamp = now,
                createdAt = now
            )
            return ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "sales",
                docId = docId,
                summaryTitle = "Rep Return: $cleanRep",
                summaryDetail = "$date • Exp ${InputGuards.formatKg(expiredKg)} • Ret ${InputGuards.formatKg(quantityKg)}",
                payload = WorkspaceJsonCodec.saleToMap(repRet)
            )
        }

        if (normType == "COLLECTION" || normType == "PARTIAL_PAYMENT") {
            val effExpRate = when {
                expiredUnitPrice > 0.0 -> InputGuards.round2(expiredUnitPrice)
                expiredKg > 0.0 -> SalesAnalyticsEngine.computeGoodsCreditUnitPrice(cleanCust, snapshot, fallbackStore = store)
                else -> 0.0
            }
            val custOutstanding = SalesAnalyticsEngine.computeCustomerOutstanding(cleanCust, snapshot)
            val goodsPlan = if (expiredKg > 0.0) {
                SalesAnalyticsEngine.planGoodsCredit(expiredKg, effExpRate, max(0.0, custOutstanding - collectedAmount))
            } else {
                SalesAnalyticsEngine.GoodsCreditPlan(0.0, 0.0, false)
            }
            val expVal = goodsPlan.value
            val totalPool = InputGuards.round2(collectedAmount + expVal)
            if (collectedAmount <= 0.0 && expiredKg <= 0.0) {
                return Result.failure(IllegalArgumentException("Enter collected amount or expired kg"))
            }
            val colId = ops.newId("sale")
            val customerOpenSales = snapshot.sales.filter {
                it.deletedAt == null &&
                    it.customerName.equals(cleanCust, ignoreCase = true) &&
                    !it.creditReceived &&
                    (it.paymentType == "CREDIT" || it.transactionType == "OLD_DEBT")
            }
            val plan = if (snapshot.profile.isAdmin && totalPool > 0.0) {
                FinanceEngine.computeCollectionFifoPlan(totalPool, customerOpenSales, snapshot.customers, snapshot.stores)
            } else emptyMap()

            val appliedCash = InputGuards.round2(min(collectedAmount, plan.values.sumOf { it.first }))
            val unappliedCash = InputGuards.round2(max(0.0, collectedAmount - appliedCash))
            val expAppliedAmt = InputGuards.round2(max(0.0, plan.values.sumOf { it.first } - appliedCash))

            if (snapshot.profile.isAdmin && plan.isNotEmpty()) {
                val salesById = customerOpenSales.associateBy { it.id }
                plan.forEach { (saleId, pair) ->
                    val s = salesById[saleId] ?: return@forEach
                    val (amt, full) = pair
                    val nextPartial = InputGuards.round2(s.partialPaymentReceived + amt)
                    val nextAllocs = s.collectionAllocs + (colId to amt)
                    val updatedSale = s.copy(
                        partialPaymentReceived = if (full) s.totalValue else nextPartial,
                        creditReceived = full,
                        collectionAllocs = nextAllocs,
                        collectionAllocFull = s.collectionAllocFull + (colId to full)
                    )
                    ops.submitOrExecuteWrite(
                        profile = snapshot.profile,
                        targetCollection = "sales",
                        docId = s.id,
                        summaryTitle = "FIFO Settle: ${s.customerName}",
                        summaryDetail = "$date • Applied ${InputGuards.fmtNum(amt)}",
                        payload = WorkspaceJsonCodec.saleToMap(updatedSale),
                        isEdit = true
                    )
                }
            }

            var choraAdded = false
            if (expiredKg > 0.0 && snapshot.profile.isAdmin) {
                choraAdded = addChoraToInventory(snapshot, expiredKg)
            }

            val colRecord = SalesRecord(
                id = colId,
                date = date,
                supplyDate = date,
                time = DateUtils.nowTimeStr(),
                customerName = cleanCust,
                customerPhone = resolvedPhone,
                quantity = 0.0,
                supplyStore = storeKey,
                paymentType = "COLLECTION",
                salesRep = salesRep.ifBlank { "NONE" },
                currentRepProfile = "admin",
                unitPrice = 0.0,
                totalValue = if (plan.isEmpty()) InputGuards.round2(collectedAmount) else unappliedCash,
                profit = if (plan.isEmpty()) InputGuards.round2(collectedAmount) else unappliedCash,
                isCollection = true,
                collectedAmount = InputGuards.round2(collectedAmount),
                allocations = plan.mapValues { it.value.first },
                allocationFull = plan.mapValues { it.value.second },
                expiredQty = InputGuards.round2(expiredKg),
                expiredValue = expVal,
                expiredUnitPrice = effExpRate,
                expiredApplied = if (choraAdded) expiredKg else 0.0,
                expiredAllocated = expAppliedAmt,
                creditReceived = true,
                notes = InputGuards.sanitizeNotes(notes),
                timestamp = now,
                createdAt = now
            )

            if (addToLedger && collectedAmount > 0.0 && snapshot.profile.isAdmin) {
                appendDailyLedgerEntry(
                    snapshot,
                    DailyLedgerEntry(
                        id = ops.newId("dl"),
                        type = "IN",
                        amount = InputGuards.round2(collectedAmount),
                        category = "Customer Payment",
                        scope = "",
                        mode = "Cash",
                        party = cleanCust,
                        note = notes.ifBlank { "Collection" },
                        date = date,
                        timestamp = now,
                        updatedAt = now,
                        linkCollection = "sales",
                        linkDocId = colId,
                        linkKind = "customer",
                        linkOrigin = "tab",
                        linkPart = "collection"
                    )
                )
            }

            return ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "sales",
                docId = colId,
                summaryTitle = "Collection: $cleanCust",
                summaryDetail = "$date • ${InputGuards.fmtNum(collectedAmount)}",
                payload = WorkspaceJsonCodec.saleToMap(colRecord)
            )
        }

        if (normType == "MONEY_OUT") {
            if (collectedAmount <= 0.0) return Result.failure(IllegalArgumentException("Enter payout amount"))
            val docId = ops.newId("sale")
            val rec = SalesRecord(
                id = docId,
                date = date,
                supplyDate = date,
                time = DateUtils.nowTimeStr(),
                customerName = cleanCust,
                customerPhone = existingCust?.phone ?: "",
                paymentType = "MONEY_OUT",
                salesRep = salesRep.ifBlank { "NONE" },
                currentRepProfile = "admin",
                totalValue = InputGuards.round2(collectedAmount),
                creditReceived = false,
                notes = InputGuards.sanitizeNotes(notes),
                timestamp = now,
                createdAt = now
            )
            if (addToLedger && snapshot.profile.isAdmin) {
                appendDailyLedgerEntry(
                    snapshot,
                    DailyLedgerEntry(
                        id = ops.newId("dl"),
                        type = "OUT",
                        amount = InputGuards.round2(collectedAmount),
                        category = "Miscellaneous Business",
                        scope = "BUSINESS",
                        mode = "Cash",
                        party = cleanCust,
                        note = notes.ifBlank { "Customer refund / payout" },
                        date = date,
                        timestamp = now,
                        updatedAt = now,
                        linkCollection = "sales",
                        linkDocId = docId,
                        linkKind = "customer",
                        linkOrigin = "tab",
                        linkPart = "payout"
                    )
                )
            }
            return ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "sales",
                docId = docId,
                summaryTitle = "Money Out: $cleanCust",
                summaryDetail = "$date • ${InputGuards.fmtNum(collectedAmount)}",
                payload = WorkspaceJsonCodec.saleToMap(rec)
            )
        }

        // Standard CASH or CREDIT sale
        if (quantityKg <= 0.0 || unitPrice <= 0.0) {
            return Result.failure(IllegalArgumentException("Quantity and unit price must be greater than 0"))
        }
        val effSupplyDate = supplyDate.ifBlank { date }
        if (store != null) {
            val stockTile = SalesAnalyticsEngine.computeDateStockTile(snapshot, store, effSupplyDate, quantityKg)
            if (stockTile.totalInKg <= 0.0) {
                return Result.failure(IllegalArgumentException("No production or returns for ${store.name} on $effSupplyDate."))
            }
            if (stockTile.availableKg - quantityKg < -1e-6) {
                return Result.failure(
                    IllegalArgumentException(
                        "Insufficient stock! Available: ${InputGuards.formatKg(max(0.0, stockTile.availableKg))}, Requested: ${InputGuards.formatKg(quantityKg)}"
                    )
                )
            }
        }
        val costIdx = FormulaEngine.buildCostIndex(snapshot.production)
        val costLookup = FormulaEngine.costSourceFromIndex(costIdx, storeKey, effSupplyDate)
        val totalVal = InputGuards.round2(quantityKg * unitPrice)
        val totalCost = InputGuards.round2(quantityKg * costLookup.cp)
        val profit = InputGuards.round2(totalVal - totalCost)
        val saleId = ops.newId("sale")
        val isDirectCustomer = salesRep.isBlank() || salesRep.equals("NONE", true)

        if (existingCust == null && isDirectCustomer) {
            val newCust = CustomerRecord(
                id = ops.newId("cust"),
                name = cleanCust,
                phone = cleanPhone,
                visitCount = 1L,
                createdAt = now,
                timestamp = now
            )
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "customers",
                docId = newCust.id,
                summaryTitle = "New Customer: $cleanCust",
                summaryDetail = "Added on $date",
                payload = WorkspaceJsonCodec.customerToMap(newCust)
            )
        } else if (existingCust != null && existingCust.phone.isBlank() && cleanPhone.isNotBlank()) {
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "customers",
                docId = existingCust.id,
                summaryTitle = "Update Phone: $cleanCust",
                summaryDetail = cleanPhone,
                payload = WorkspaceJsonCodec.customerToMap(existingCust.copy(phone = cleanPhone, timestamp = now)),
                isEdit = true
            )
        }

        val sale = SalesRecord(
            id = saleId,
            date = date,
            supplyDate = effSupplyDate,
            time = DateUtils.nowTimeStr(),
            customerName = cleanCust,
            customerPhone = resolvedPhone,
            quantity = InputGuards.round2(quantityKg),
            supplyStore = storeKey,
            paymentType = normType,
            salesRep = salesRep.ifBlank { "NONE" },
            currentRepProfile = "admin",
            unitPrice = InputGuards.round2(unitPrice),
            totalValue = totalVal,
            totalCost = totalCost,
            profit = profit,
            costSource = costLookup.source,
            creditReceived = normType == "CASH",
            collectedAmount = if (normType == "CASH") totalVal else 0.0,
            notes = InputGuards.sanitizeNotes(notes),
            timestamp = now,
            createdAt = now
        )

        if (addToLedger && normType == "CASH" && snapshot.profile.isAdmin) {
            appendDailyLedgerEntry(
                snapshot,
                DailyLedgerEntry(
                    id = ops.newId("dl"),
                    type = "IN",
                    amount = totalVal,
                    category = "Sales",
                    scope = "",
                    mode = "Cash",
                    party = cleanCust,
                    note = "${InputGuards.formatKg(quantityKg)} @ ${InputGuards.fmtNum(unitPrice)}",
                    date = date,
                    timestamp = now,
                    updatedAt = now,
                    linkCollection = "sales",
                    linkDocId = saleId,
                    linkKind = "customer",
                    linkOrigin = "tab",
                    linkPart = "sale"
                )
            )
        }

        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "sales",
            docId = saleId,
            summaryTitle = "$normType Sale: $cleanCust",
            summaryDetail = "$date • ${InputGuards.formatKg(quantityKg)} • ${InputGuards.fmtNum(totalVal)}",
            payload = WorkspaceJsonCodec.saleToMap(sale)
        )
    }

    suspend fun saveRepRecord(
        snapshot: WorkspaceSnapshot,
        date: String,
        repName: String,
        paymentType: String, // CASH | CREDIT | COLLECTION | REP_RETURN | MONEY_OUT
        customerName: String,
        store: StoreConfig?,
        quantityKg: Double,
        unitPrice: Double,
        collectedAmount: Double,
        expiredKg: Double,
        gpsLat: Double?,
        gpsLng: Double?,
        gpsAccuracy: Double?,
        gpsConfirmed: Boolean,
        notes: String,
        customerPhone: String = "",
        expiredUnitPrice: Double = 0.0
    ): Result<String> {
        val cleanCust = InputGuards.sanitizeName(
            InputGuards.canonicalName(customerName, snapshot.customers.filter { it.deletedAt == null }.map { it.name })
        )
        val cleanRep = InputGuards.sanitizeName(repName).ifBlank { snapshot.profile.displayName.ifBlank { "Rep" } }
        if (cleanCust.isBlank()) return Result.failure(IllegalArgumentException("Customer name is required"))
        val repGuardIssue = InputGuards.dateIssue(date, DateUtils.todayIso()).ifEmpty {
            InputGuards.phoneIssue(customerPhone)
        }.ifEmpty { InputGuards.nameIssue(cleanCust, "Customer name") }
        if (repGuardIssue.isNotEmpty()) return Result.failure(IllegalArgumentException(repGuardIssue))
        val cleanPhone = InputGuards.sanitizePhone(customerPhone)
        val normType = paymentType.trim().uppercase(Locale.ROOT)
        val now = System.currentTimeMillis()
        val storeKey = store?.let(::resolvedStoreKey).orEmpty()

        val existingClient = snapshot.clients.firstOrNull {
            it.deletedAt == null &&
                it.name.equals(cleanCust, ignoreCase = true) &&
                (it.salesRep.isBlank() || it.salesRep.equals(cleanRep, ignoreCase = true))
        } ?: snapshot.clients.firstOrNull {
            it.deletedAt == null && it.name.equals(cleanCust, ignoreCase = true)
        }
        val resolvedPhone = cleanPhone.ifBlank { existingClient?.phone ?: "" }

        if (normType == "COLLECTION" || normType == "PARTIAL_PAYMENT") {
            if (collectedAmount < 0.0 || expiredKg < 0.0) {
                return Result.failure(IllegalArgumentException("Amount and expired quantity cannot be negative"))
            }
            if (collectedAmount <= 0.0 && expiredKg <= 0.0) {
                return Result.failure(IllegalArgumentException("Enter amount collected and/or expired quantity"))
            }
            val effExpRate = when {
                expiredUnitPrice > 0.0 -> InputGuards.round2(expiredUnitPrice)
                expiredKg > 0.0 -> RepSalesAnalyticsEngine.computeRepGoodsCreditUnitPrice(cleanRep, cleanCust, snapshot, fallbackStore = store)
                else -> 0.0
            }
            val repOutstanding = RepSalesAnalyticsEngine.computeRepClientOutstanding(cleanCust, cleanRep, snapshot)
            val goodsPlan = if (expiredKg > 0.0) {
                SalesAnalyticsEngine.planGoodsCredit(expiredKg, effExpRate, max(0.0, repOutstanding - collectedAmount))
            } else {
                SalesAnalyticsEngine.GoodsCreditPlan(0.0, 0.0, false)
            }
            val expVal = goodsPlan.value
            val totalPool = InputGuards.round2(collectedAmount + expVal)
            val colId = ops.newId("rep")

            val openSales = snapshot.repRecords.filter {
                it.deletedAt == null &&
                    !it.isMerged &&
                    it.usedInCalcId.isBlank() &&
                    !it.creditReceived &&
                    it.salesRep.equals(cleanRep, ignoreCase = true) &&
                    it.customerName.equals(cleanCust, ignoreCase = true) &&
                    (it.paymentType == "CREDIT" || it.transactionType == "OLD_DEBT")
            }
            val plan = if (snapshot.profile.isAdmin && totalPool > 0.0) {
                FinanceEngine.computeCollectionFifoPlan(
                    paymentAmount = totalPool,
                    openCreditRows = openSales,
                    customers = snapshot.clients.ifEmpty { snapshot.customers },
                    stores = snapshot.stores
                )
            } else emptyMap()

            val appliedCash = InputGuards.round2(min(collectedAmount, plan.values.sumOf { it.first }))
            val unappliedCash = InputGuards.round2(max(0.0, collectedAmount - appliedCash))
            val expAppliedAmt = InputGuards.round2(max(0.0, plan.values.sumOf { it.first } - appliedCash))

            if (snapshot.profile.isAdmin && plan.isNotEmpty()) {
                val byId = openSales.associateBy { it.id }
                plan.forEach { (saleId, pair) ->
                    val s = byId[saleId] ?: return@forEach
                    val (amt, full) = pair
                    val updatedSale = s.copy(
                        partialPaymentReceived = if (full) s.totalValue else InputGuards.round2(s.partialPaymentReceived + amt),
                        creditReceived = full,
                        collectionAllocs = s.collectionAllocs + (colId to amt),
                        collectionAllocFull = s.collectionAllocFull + (colId to full),
                        updatedAtEpoch = now
                    )
                    ops.submitOrExecuteWrite(
                        profile = snapshot.profile,
                        targetCollection = "rep",
                        docId = s.id,
                        summaryTitle = "Rep FIFO Settle: ${s.customerName}",
                        summaryDetail = "$date • Applied ${InputGuards.fmtNum(amt)}",
                        payload = WorkspaceJsonCodec.saleToMap(updatedSale),
                        isEdit = true
                    )
                }
            }

            var choraAdded = false
            if (expiredKg > 0.0 && snapshot.profile.isAdmin) {
                choraAdded = addChoraToInventory(snapshot, expiredKg)
            }

            val rec = SalesRecord(
                id = colId,
                date = date,
                supplyDate = date,
                time = DateUtils.nowTimeStr(),
                customerName = cleanCust,
                customerPhone = resolvedPhone,
                quantity = 0.0,
                supplyStore = storeKey,
                paymentType = "COLLECTION",
                salesRep = cleanRep,
                currentRepProfile = cleanRep,
                unitPrice = 0.0,
                totalValue = if (plan.isEmpty()) InputGuards.round2(collectedAmount) else unappliedCash,
                totalCost = 0.0,
                profit = if (plan.isEmpty()) InputGuards.round2(collectedAmount) else unappliedCash,
                creditReceived = true,
                isCollection = true,
                collectedAmount = InputGuards.round2(collectedAmount),
                allocations = plan.mapValues { it.value.first },
                allocationFull = plan.mapValues { it.value.second },
                expiredQty = InputGuards.round2(expiredKg),
                expiredValue = expVal,
                expiredUnitPrice = effExpRate,
                expiredApplied = if (choraAdded) InputGuards.round2(expiredKg) else 0.0,
                expiredAllocated = expAppliedAmt,
                affectsInventory = false,
                gpsLat = gpsLat,
                gpsLng = gpsLng,
                gpsAccuracy = gpsAccuracy,
                gpsConfirmed = gpsConfirmed,
                notes = InputGuards.sanitizeNotes(notes),
                timestamp = now,
                createdAt = now
            )

            syncRepClientOnVisit(snapshot, existingClient, cleanCust, cleanRep, resolvedPhone, gpsLat, gpsLng, now)

            return ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "rep",
                docId = rec.id,
                summaryTitle = "Rep COLLECTION: $cleanCust",
                summaryDetail = "$date • $cleanRep • ${InputGuards.fmtNum(collectedAmount)}",
                payload = WorkspaceJsonCodec.saleToMap(rec)
            )
        }

        if (normType in setOf("CASH", "CREDIT")) {
            if (quantityKg <= 0.0 || unitPrice <= 0.0) {
                return Result.failure(IllegalArgumentException("Enter valid quantity and sale rate"))
            }
        } else if (collectedAmount <= 0.0 && quantityKg <= 0.0 && expiredKg <= 0.0) {
            return Result.failure(IllegalArgumentException("Enter a valid amount or quantity"))
        }

        val totalVal = when (normType) {
            "MONEY_OUT" -> InputGuards.round2(collectedAmount)
            "REP_RETURN" -> InputGuards.round2(quantityKg * unitPrice)
            else -> InputGuards.round2(quantityKg * unitPrice)
        }
        val costIdx = FormulaEngine.buildCostIndex(snapshot.production)
        val costLookup = FormulaEngine.costSourceFromIndex(costIdx, storeKey, date)
        val totalCost = if (normType in setOf("CASH", "CREDIT")) InputGuards.round2(quantityKg * costLookup.cp) else 0.0
        val profit = if (normType in setOf("CASH", "CREDIT")) InputGuards.round2(totalVal - totalCost) else 0.0
        if (normType in setOf("CASH", "CREDIT") && costLookup.cp > 0.0 && profit < -0.01) {
            return Result.failure(IllegalArgumentException("This sale would result in a loss. Check sale price vs cost price."))
        }

        syncRepClientOnVisit(snapshot, existingClient, cleanCust, cleanRep, resolvedPhone, gpsLat, gpsLng, now)

        val rec = SalesRecord(
            id = ops.newId("rep"),
            date = date,
            supplyDate = date,
            time = DateUtils.nowTimeStr(),
            customerName = cleanCust,
            customerPhone = resolvedPhone,
            quantity = if (normType in setOf("CASH", "CREDIT")) InputGuards.round2(quantityKg) else 0.0,
            supplyStore = storeKey,
            paymentType = normType,
            salesRep = cleanRep,
            currentRepProfile = cleanRep,
            unitPrice = InputGuards.round2(unitPrice),
            totalValue = totalVal,
            totalCost = totalCost,
            profit = profit,
            costSource = if (normType in setOf("CASH", "CREDIT")) costLookup.source else "",
            creditReceived = normType == "CASH",
            isCollection = false,
            collectedAmount = if (normType == "CASH" || normType == "MONEY_OUT") totalVal else 0.0,
            expiredQty = InputGuards.round2(expiredKg),
            isRepReturn = normType == "REP_RETURN",
            returnedQty = if (normType == "REP_RETURN") InputGuards.round2(quantityKg) else 0.0,
            returnValue = if (normType == "REP_RETURN") totalVal else 0.0,
            affectsInventory = false,
            gpsLat = gpsLat,
            gpsLng = gpsLng,
            gpsAccuracy = gpsAccuracy,
            gpsConfirmed = gpsConfirmed,
            notes = InputGuards.sanitizeNotes(notes),
            timestamp = now,
            createdAt = now
        )
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "rep",
            docId = rec.id,
            summaryTitle = "Rep $normType: $cleanCust",
            summaryDetail = "$date • $cleanRep • ${InputGuards.fmtNum(totalVal)}",
            payload = WorkspaceJsonCodec.saleToMap(rec)
        )
    }

    private suspend fun syncRepClientOnVisit(
        snapshot: WorkspaceSnapshot,
        existingClient: CustomerRecord?,
        cleanCust: String,
        cleanRep: String,
        resolvedPhone: String,
        gpsLat: Double?,
        gpsLng: Double?,
        now: Long
    ) {
        if (existingClient == null) {
            val newClient = CustomerRecord(
                id = ops.newId("client"),
                name = cleanCust,
                phone = resolvedPhone,
                salesRep = cleanRep,
                gpsLat = gpsLat,
                gpsLng = gpsLng,
                visitCount = 1L,
                createdAt = now,
                updatedAt = now,
                timestamp = now
            )
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "clients",
                docId = newClient.id,
                summaryTitle = "New Rep Customer: $cleanCust",
                summaryDetail = "Rep: $cleanRep",
                payload = WorkspaceJsonCodec.customerToMap(newClient)
            )
            return
        }

        // Check 3+ consistent visits within 150m to confirm GPS address (port of autoUpdateCustomerLocation)
        var nextAddress = existingClient.address
        var nextLat = existingClient.gpsLat
        var nextLng = existingClient.gpsLng
        val isManualAddress = existingClient.address.length > 5 && !existingClient.address.startsWith("GPS:")
        if (!isManualAddress && gpsLat != null && gpsLng != null) {
            val cluster = snapshot.repRecords.filter { rec ->
                rec.deletedAt == null &&
                    rec.customerName.equals(cleanCust, ignoreCase = true) &&
                    rec.gpsLat != null && rec.gpsLng != null &&
                    LocationHelper.distanceMeters(gpsLat, gpsLng, rec.gpsLat, rec.gpsLng) <= 150.0
            }.take(3)
            if (cluster.size >= 3) {
                val avgLat = (cluster.sumOf { it.gpsLat ?: 0.0 } + gpsLat) / (cluster.size + 1)
                val avgLng = (cluster.sumOf { it.gpsLng ?: 0.0 } + gpsLng) / (cluster.size + 1)
                nextAddress = "GPS: ${String.format(Locale.US, "%.6f", avgLat)}, ${String.format(Locale.US, "%.6f", avgLng)}"
                nextLat = avgLat
                nextLng = avgLng
            } else if (nextLat == null || nextLng == null) {
                nextLat = gpsLat
                nextLng = gpsLng
            }
        }
        val phoneUpdateNeeded = existingClient.phone.isBlank() && resolvedPhone.isNotBlank()
        val addressChanged = nextAddress != existingClient.address || nextLat != existingClient.gpsLat || nextLng != existingClient.gpsLng
        val updatedClient = existingClient.copy(
            phone = if (phoneUpdateNeeded) resolvedPhone else existingClient.phone,
            address = nextAddress,
            gpsLat = nextLat,
            gpsLng = nextLng,
            visitCount = existingClient.visitCount + 1L,
            updatedAt = now
        )
        if (snapshot.profile.isAdmin || phoneUpdateNeeded || addressChanged) {
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "clients",
                docId = existingClient.id,
                summaryTitle = "Update Rep Client: $cleanCust",
                summaryDetail = "Visit #${updatedClient.visitCount}",
                payload = WorkspaceJsonCodec.customerToMap(updatedClient),
                isEdit = true
            )
        }
    }

    suspend fun saveFactoryBatch(
        snapshot: WorkspaceSnapshot,
        date: String,
        formula: FormulaConfig,
        unitsToProduce: Double,
        notes: String
    ): Result<String> {
        if (unitsToProduce <= 0.0) return Result.failure(IllegalArgumentException("Units must be greater than 0"))
        val desc = FormulaEngine.describeFormula(formula, snapshot.formulas, snapshot.inventory)
        if (desc.problems.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Formula has linking issues: ${desc.problems.first().name}"))
        }
        val materials = FinanceEngine.materialRecords(snapshot.inventory)
        val deductions = mutableListOf<Pair<InventoryRecord, Double>>()
        val usedList = mutableListOf<Map<String, Any>>()

        for (ing in desc.items) {
            val need = InputGuards.round4(ing.quantity * unitsToProduce)
            if (need <= 0.0) continue
            val inv = materials.firstOrNull { it.id == ing.id }
                ?: materials.firstOrNull { it.name.equals(ing.name.trim(), ignoreCase = true) }
                ?: return Result.failure(IllegalArgumentException("Missing raw material: ${ing.name}"))
            if (inv.quantity + 0.001 < need) {
                return Result.failure(IllegalArgumentException("Insufficient ${inv.name}: need ${InputGuards.fmtNum(need)} kg, have ${InputGuards.fmtNum(inv.quantity)} kg"))
            }
            deductions.add(inv to need)
            usedList.add(
                mapOf(
                    "id" to inv.id,
                    "name" to inv.name,
                    "qty" to need,
                    "cost" to InputGuards.round2(need * inv.cost)
                )
            )
        }

        val materialsCost = InputGuards.round2(desc.raw * unitsToProduce)
        val extraCost = InputGuards.round2(desc.additionalCost * unitsToProduce)
        val totalCost = InputGuards.round2(materialsCost + extraCost)
        val storeKey = snapshot.stores.firstOrNull { it.formulaId == formula.id }?.let(::resolvedStoreKey).orEmpty()
        val now = System.currentTimeMillis()
        val record = FactoryRecord(
            id = ops.newId("fact"),
            date = date,
            time = DateUtils.nowTimeStr(),
            store = storeKey,
            formulaId = formula.id,
            formulaName = formula.name,
            units = InputGuards.round2(unitsToProduce),
            totalCost = totalCost,
            materialsCost = materialsCost,
            additionalCost = extraCost,
            rawMaterialsUsed = usedList,
            materialsUsed = usedList,
            notes = InputGuards.sanitizeNotes(notes),
            timestamp = now
        )

        val res = ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "factory",
            docId = record.id,
            summaryTitle = "Factory: ${formula.name} (${InputGuards.fmtNum(unitsToProduce)} units)",
            summaryDetail = "$date • Cost ${InputGuards.fmtNum(totalCost)}",
            payload = WorkspaceJsonCodec.factoryToMap(record)
        )

        if (res.isSuccess && snapshot.profile.isAdmin) {
            deductions.forEach { (inv, need) ->
                val nextQty = InputGuards.round6(max(0.0, inv.quantity - need))
                val updated = inv.copy(
                    quantity = nextQty,
                    totalValue = InputGuards.round2(nextQty * inv.cost),
                    timestamp = now
                )
                ops.submitOrExecuteWrite(
                    profile = snapshot.profile,
                    targetCollection = "inventory",
                    docId = inv.id,
                    summaryTitle = "Consume ${inv.name}",
                    summaryDetail = "-${InputGuards.fmtNum(need)} kg",
                    payload = WorkspaceJsonCodec.inventoryToMap(updated),
                    isEdit = true
                )
            }
        }
        return res
    }

    suspend fun saveRawMaterialPurchase(
        snapshot: WorkspaceSnapshot,
        date: String,
        materialName: String,
        purchaseUnitName: String,
        conversionFactor: Double,
        purchaseUnitsQty: Double,
        costPerPurchaseUnit: Double,
        paidNow: Double,
        supplierName: String,
        notes: String
    ): Result<String> {
        val cleanName = InputGuards.sanitizeName(materialName)
        if (cleanName.isBlank() || purchaseUnitsQty <= 0.0 || conversionFactor <= 0.0) {
            return Result.failure(IllegalArgumentException("Material name, quantity, and conversion factor must be > 0"))
        }
        val kg = InputGuards.round6(purchaseUnitsQty * conversionFactor)
        val totalCost = InputGuards.round2(purchaseUnitsQty * costPerPurchaseUnit)
        val costPerKg = if (kg > 0.0) InputGuards.round4(totalCost / kg) else 0.0
        val cleanSupplier = InputGuards.sanitizeName(supplierName)
        val now = System.currentTimeMillis()

        // Ensure supplier entity if named
        var supplierEntity = snapshot.entities.firstOrNull {
            it.deletedAt == null && it.name.equals(cleanSupplier, ignoreCase = true)
        }
        if (cleanSupplier.isNotBlank() && supplierEntity == null) {
            val newSupp = EntityRecord(
                id = ops.newId("ent"),
                name = cleanSupplier,
                type = "supplier",
                isSupplier = true,
                timestamp = now
            )
            supplierEntity = newSupp
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "entities",
                docId = newSupp.id,
                summaryTitle = "New Supplier: $cleanSupplier",
                summaryDetail = "Created on purchase",
                payload = WorkspaceJsonCodec.entityToMap(newSupp)
            )
        }

        val existingMat = FinanceEngine.findMaterialByName(snapshot.inventory, cleanName)
        val matId = existingMat?.id ?: ops.newId("mat")
        val updatedMat = if (existingMat != null) {
            val nextQty = InputGuards.round6(existingMat.quantity + kg)
            val nextVal = (existingMat.quantity * existingMat.cost) + totalCost
            val avgCost = if (nextQty > 0.0) InputGuards.round4(nextVal / nextQty) else costPerKg
            existingMat.copy(
                quantity = nextQty,
                cost = avgCost,
                totalValue = InputGuards.round2(nextQty * avgCost),
                collectedKg = InputGuards.round6(FinanceEngine.collectedKg(existingMat) + kg),
                conversionFactor = conversionFactor,
                purchaseUnitName = purchaseUnitName.trim(),
                purchaseCost = costPerPurchaseUnit,
                purchaseQuantity = if (conversionFactor != 1.0) InputGuards.round4(nextQty / conversionFactor) else nextQty,
                purchasesTracked = true,
                timestamp = now
            )
        } else {
            InventoryRecord(
                id = matId,
                name = cleanName,
                quantity = kg,
                unit = "kg",
                cost = costPerKg,
                totalValue = totalCost,
                collectedKg = kg,
                conversionFactor = conversionFactor,
                purchaseUnitName = purchaseUnitName.trim(),
                purchaseCost = costPerPurchaseUnit,
                purchaseQuantity = purchaseUnitsQty,
                purchasesTracked = true,
                timestamp = now
            )
        }
        ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "inventory",
            docId = matId,
            summaryTitle = "${if (existingMat != null) "Restock" else "Add"} $cleanName",
            summaryDetail = "+${InputGuards.formatKg(kg)} • Avg ${InputGuards.fmtNum(updatedMat.cost)}/kg",
            payload = WorkspaceJsonCodec.inventoryToMap(updatedMat),
            isEdit = existingMat != null
        )

        val payable = InputGuards.round2(max(0.0, totalCost - paidNow))
        val lotId = ops.newId("lot")
        val lot = InventoryRecord(
            id = lotId,
            name = "$cleanName · $date",
            quantity = 0.0,
            unit = "kg",
            cost = costPerKg,
            totalValue = totalCost,
            conversionFactor = conversionFactor,
            purchaseUnitName = purchaseUnitName.trim(),
            purchaseCost = costPerPurchaseUnit,
            purchaseQuantity = purchaseUnitsQty,
            isPurchaseLot = true,
            materialRefId = matId,
            materialName = cleanName,
            purchaseKg = kg,
            purchaseDate = date,
            paymentStatus = if (supplierEntity != null && payable > 0.01) "pending" else "paid",
            supplierId = supplierEntity?.id ?: "",
            supplierName = cleanSupplier,
            supplierContact = supplierEntity?.contact ?: "",
            supplierType = supplierEntity?.type ?: "",
            totalPayable = if (supplierEntity != null) payable else 0.0,
            paidDate = if (payable <= 0.01) date else "",
            notes = InputGuards.sanitizeNotes(notes),
            timestamp = now
        )

        if (paidNow > 0.0 && snapshot.profile.isAdmin) {
            appendDailyLedgerEntry(
                snapshot,
                DailyLedgerEntry(
                    id = ops.newId("dl"),
                    type = "OUT",
                    amount = InputGuards.round2(paidNow),
                    category = "Raw Materials",
                    scope = "BUSINESS",
                    mode = "Cash",
                    party = cleanSupplier.ifBlank { cleanName },
                    note = "$cleanName (${InputGuards.formatKg(kg)})",
                    date = date,
                    timestamp = now,
                    updatedAt = now,
                    linkCollection = "inventory",
                    linkDocId = lotId,
                    linkOrigin = "tab"
                )
            )
        }

        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "inventory",
            docId = lotId,
            summaryTitle = "Purchase Lot: $cleanName",
            summaryDetail = "$date • ${InputGuards.formatKg(kg)} • ${InputGuards.fmtNum(totalCost)}",
            payload = WorkspaceJsonCodec.inventoryToMap(lot)
        )
    }

    suspend fun savePaymentOrExpense(
        snapshot: WorkspaceSnapshot,
        date: String,
        kind: String, // "IN" | "OUT" | "EXPENSE"
        entity: EntityRecord?,
        customName: String,
        category: String,
        paymentMethod: String,
        amount: Double,
        addToLedger: Boolean,
        notes: String,
        photoDataUri: String? = null
    ): Result<String> {
        val cleanName = InputGuards.sanitizeName(entity?.name ?: customName)
        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter name/entity"))
        }
        val cleanNotes = InputGuards.sanitizeNotes(notes)
        val issue = InputGuards.dateIssue(date, DateUtils.todayIso())
            .ifEmpty { InputGuards.nameIssue(cleanName, "Name") }
            .ifEmpty { InputGuards.nameIssue(cleanNotes, "Description", 300) }
        if (issue.isNotEmpty()) return Result.failure(IllegalArgumentException(issue))
        val roundedAmount = InputGuards.round2(amount)
        if (roundedAmount <= 0.0) {
            return Result.failure(IllegalArgumentException("Please enter valid amount"))
        }

        val normKind = kind.trim().uppercase(Locale.ROOT)
        val isOperating = normKind == "EXPENSE" || normKind == "OPERATING"
        val normType = if (normKind == "IN") "IN" else "OUT"
        val cleanMethod = paymentMethod.ifBlank { "Cash" }
        val now = System.currentTimeMillis()
        val session = FinanceEngine.deriveSession(snapshot.profile)

        if (!isOperating || normType == "OUT") {
            if (isOperating || normType == "OUT") {
                var materialOffset = 0.0
                if (!isOperating && normType == "OUT") {
                    val matchedEnt = entity ?: snapshot.entities.firstOrNull {
                        it.deletedAt == null && !it.isExpenseEntity && it.name.equals(cleanName, ignoreCase = true)
                    }
                    if (matchedEnt != null) {
                        var rem = roundedAmount
                        val openMats = snapshot.inventory.filter { m ->
                            m.deletedAt == null &&
                                m.supplierId == matchedEnt.id &&
                                (m.paymentStatus == "pending" || m.paymentStatus.isBlank()) &&
                                m.totalPayable > 0.0
                        }.sortedWith(compareBy<InventoryRecord> { it.purchaseDate.ifBlank { "9999-99-99" } }.thenBy { it.timestamp })
                        for (m in openMats) {
                            if (rem <= 0.0) break
                            materialOffset += min(rem, m.totalPayable)
                            rem -= m.totalPayable
                        }
                    }
                }
                val netOutflow = InputGuards.round2(roundedAmount - materialOffset)
                val availCash = FinanceEngine.computeAvailableCashInHand(snapshot)
                if (availCash < netOutflow) {
                    val msg = if (session.restrictMoney || snapshot.settings.hideSensitiveMoney) {
                        "Insufficient cash in hand."
                    } else {
                        "Insufficient cash in hand. Available: ${InputGuards.fmtNum(max(0.0, availCash))} — Required: ${InputGuards.fmtNum(netOutflow)}"
                    }
                    return Result.failure(IllegalArgumentException(msg))
                }
            }
        }

        if (!isOperating) {
            val knownEntity = entity ?: snapshot.entities.firstOrNull {
                it.deletedAt == null && !it.isExpenseEntity && it.name.equals(cleanName, ignoreCase = true)
            }
            if (knownEntity == null && snapshot.settings.expenseCategories.any { it.equals(cleanName, ignoreCase = true) }) {
                return Result.failure(
                    IllegalArgumentException("\"$cleanName\" is an operating expense category, not an entity. Switch to Operating Expense mode or use a different name.")
                )
            }
        }

        if (isOperating) {
            val expId = ops.newId("exp")
            var expenseEnt = snapshot.entities.firstOrNull {
                it.deletedAt == null && it.isExpenseEntity && it.name.equals(cleanName, ignoreCase = true)
            }
            if (expenseEnt == null) {
                val createdEnt = EntityRecord(
                    id = ops.newId("ent"),
                    name = cleanName,
                    type = "payee",
                    isSupplier = false,
                    isExpenseEntity = true,
                    notes = "Auto-created by Expense Manager",
                    timestamp = now,
                    updatedAtEpoch = now
                )
                expenseEnt = createdEnt
                ops.submitOrExecuteWrite(
                    profile = snapshot.profile,
                    targetCollection = "entities",
                    docId = createdEnt.id,
                    summaryTitle = "Expense Payee: $cleanName",
                    summaryDetail = "Auto-created by Expense Manager",
                    payload = WorkspaceJsonCodec.entityToMap(createdEnt)
                )
            }

            val exp = ExpenseRecord(
                id = expId,
                date = date,
                name = cleanName,
                category = "operating",
                amount = roundedAmount,
                description = cleanNotes,
                notes = cleanNotes,
                entityId = expenseEnt.id,
                entityName = expenseEnt.name,
                paymentMethod = cleanMethod,
                timestamp = now,
                updatedAtEpoch = now
            )
            if (!photoDataUri.isNullOrBlank()) {
                ops.savePhoto(snapshot.profile, "expense:$expId", photoDataUri)
            }
            if (snapshot.profile.isAdmin && snapshot.settings.expenseCategories.none { it.equals(cleanName, ignoreCase = true) }) {
                val updatedCats = (snapshot.settings.expenseCategories + cleanName).distinct()
                saveSettings(snapshot, snapshot.settings.copy(expenseCategories = updatedCats))
            }

            val expRes = ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "expenses",
                docId = expId,
                summaryTitle = "Operating Expense: $cleanName",
                summaryDetail = "$date • ${InputGuards.fmtNum(roundedAmount)}",
                payload = WorkspaceJsonCodec.expenseToMap(exp)
            )
            if (expRes.isSuccess && snapshot.profile.isAdmin) {
                val txId = ops.newId("pay")
                val tx = PaymentTransaction(
                    id = txId,
                    date = date,
                    time = DateUtils.nowTimeStr(),
                    entityId = expenseEnt.id,
                    entityName = expenseEnt.name,
                    entityType = expenseEnt.type,
                    type = "OUT",
                    amount = roundedAmount,
                    description = cleanNotes.ifBlank { "Expense: $cleanName" },
                    paymentMethod = cleanMethod,
                    category = "operating",
                    isPayable = false,
                    isExpense = true,
                    expenseId = expId,
                    timestamp = now,
                    updatedAtEpoch = now
                )
                ops.submitOrExecuteWrite(
                    profile = snapshot.profile,
                    targetCollection = "transactions",
                    docId = txId,
                    summaryTitle = "Expense Tx: $cleanName",
                    summaryDetail = "$date • ${InputGuards.fmtNum(roundedAmount)}",
                    payload = WorkspaceJsonCodec.transactionToMap(tx)
                )
            }
            return expRes
        }

        // Non-operating Payment (IN / OUT)
        var targetEntity = entity ?: snapshot.entities.firstOrNull {
            it.deletedAt == null && !it.isExpenseEntity && it.name.equals(cleanName, ignoreCase = true)
        }
        if (targetEntity == null) {
            val newEnt = EntityRecord(
                id = ops.newId("ent"),
                name = cleanName,
                type = if (normType == "OUT") "payee" else "payor",
                isSupplier = false,
                isExpenseEntity = false,
                notes = "Auto-created from Transaction Manager",
                timestamp = now,
                updatedAtEpoch = now
            )
            targetEntity = newEnt
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "entities",
                docId = newEnt.id,
                summaryTitle = "New Account: $cleanName",
                summaryDetail = "Created from Payments",
                payload = WorkspaceJsonCodec.entityToMap(newEnt)
            )
        }

        val payExpenseId = ops.newId("exp")
        val payDesc = cleanNotes.ifBlank { "Payment $normType: ${targetEntity.name}" }
        val payExpense = ExpenseRecord(
            id = payExpenseId,
            date = date,
            name = targetEntity.name,
            category = normType,
            amount = roundedAmount,
            description = payDesc,
            notes = cleanNotes,
            entityId = targetEntity.id,
            entityName = targetEntity.name,
            paymentMethod = cleanMethod,
            timestamp = now,
            updatedAtEpoch = now
        )
        if (!photoDataUri.isNullOrBlank()) {
            ops.savePhoto(snapshot.profile, "expense:$payExpenseId", photoDataUri)
        }
        if (snapshot.profile.isAdmin) {
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "expenses",
                docId = payExpenseId,
                summaryTitle = "Payment Mirror: ${targetEntity.name}",
                summaryDetail = "$date • $normType • ${InputGuards.fmtNum(roundedAmount)}",
                payload = WorkspaceJsonCodec.expenseToMap(payExpense)
            )
        }

        val txId = ops.newId("pay")
        var tx = PaymentTransaction(
            id = txId,
            date = date,
            time = DateUtils.nowTimeStr(),
            entityId = targetEntity.id,
            entityName = targetEntity.name,
            entityType = targetEntity.type,
            type = normType,
            amount = roundedAmount,
            description = payDesc,
            paymentMethod = cleanMethod,
            category = normType,
            isPayable = false,
            isExpense = false,
            expenseId = payExpenseId,
            timestamp = now,
            updatedAtEpoch = now
        )

        if (normType == "OUT" && snapshot.profile.isAdmin) {
            val pendingMaterials = snapshot.inventory.filter { m ->
                m.deletedAt == null &&
                    m.supplierId == targetEntity.id &&
                    m.paymentStatus == "pending" &&
                    m.totalPayable > 0.0
            }.sortedWith(compareBy<InventoryRecord> { it.purchaseDate.ifBlank { "9999-99-99" } }.thenBy { it.timestamp })

            var remaining = roundedAmount
            if (pendingMaterials.isNotEmpty()) {
                val alloc = FinanceEngine.allocatePaymentToMaterials(pendingMaterials, remaining, date, now)
                remaining = alloc.remaining
                if (alloc.touched.isNotEmpty()) {
                    tx = tx.copy(
                        isPayable = true,
                        materialId = alloc.touched.first().id,
                        materialIds = alloc.touched.map { it.id }
                    )
                    alloc.touched.forEach { mat ->
                        ops.submitOrExecuteWrite(
                            profile = snapshot.profile,
                            targetCollection = "inventory",
                            docId = mat.id,
                            summaryTitle = "Settle Lot: ${mat.materialName.ifBlank { mat.name }}",
                            summaryDetail = "$date • Balance ${InputGuards.fmtNum(mat.totalPayable)}",
                            payload = WorkspaceJsonCodec.inventoryToMap(mat),
                            isEdit = true
                        )
                    }
                }
            }
            if (remaining > 0.0) {
                val creditPlan = FinanceEngine.planSupplierCreditConsumption(snapshot.transactions, targetEntity.id, remaining)
                if (creditPlan.consumptions.isNotEmpty()) {
                    val consumedMap = creditPlan.consumptions.associate { it.id to it.amount }
                    snapshot.transactions.filter { it.deletedAt == null && it.id in consumedMap }.forEach { ct ->
                        val dec = consumedMap[ct.id] ?: 0.0
                        val nextCredit = InputGuards.round2(max(0.0, ct.supplierCreditAmount - dec))
                        val updatedCt = ct.copy(supplierCreditAmount = nextCredit, updatedAtEpoch = now)
                        ops.submitOrExecuteWrite(
                            profile = snapshot.profile,
                            targetCollection = "transactions",
                            docId = ct.id,
                            summaryTitle = "Consume Supplier Credit: ${ct.entityName}",
                            summaryDetail = "$date • Left ${InputGuards.fmtNum(nextCredit)}",
                            payload = WorkspaceJsonCodec.transactionToMap(updatedCt),
                            isEdit = true
                        )
                    }
                    tx = tx.copy(
                        isPayable = true,
                        consumedCredits = creditPlan.consumptions.map { mapOf("id" to it.id, "amount" to it.amount) }
                    )
                }
            }
        }

        if (normType == "IN") {
            val hasMaterials = snapshot.inventory.any { it.deletedAt == null && it.supplierId == targetEntity.id }
            val isSupplierEntity = targetEntity.isSupplier || hasMaterials
            if (isSupplierEntity) {
                tx = tx.copy(isPayable = true, supplierCreditAmount = roundedAmount)
            }
        }

        if (addToLedger && snapshot.profile.isAdmin) {
            appendDailyLedgerEntry(
                snapshot,
                DailyLedgerEntry(
                    id = ops.newId("dl"),
                    type = normType,
                    amount = tx.amount,
                    category = if (normType == "IN") "Other Income" else "Miscellaneous Business",
                    scope = if (normType == "OUT") "BUSINESS" else "",
                    mode = tx.paymentMethod,
                    party = targetEntity.name,
                    note = cleanNotes,
                    date = date,
                    timestamp = now,
                    updatedAt = now,
                    linkCollection = "transactions",
                    linkDocId = txId,
                    linkOrigin = "tab",
                    linkEntityId = targetEntity.id
                )
            )
        }

        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "transactions",
            docId = txId,
            summaryTitle = "Payment $normType recorded: ${targetEntity.name}",
            summaryDetail = "$date • ${tx.paymentMethod} • ${InputGuards.fmtNum(roundedAmount)}",
            payload = WorkspaceJsonCodec.transactionToMap(tx)
        )
    }

    suspend fun saveCalculatorSettlement(
        snapshot: WorkspaceSnapshot,
        date: String,
        dateFrom: String = date,
        seller: String,
        store: StoreConfig,
        totalSold: Double,
        returned: Double,
        returnType: String, // "" | "store" | "rep" | "RETURN" | "TRANSFER"
        returnTargetRep: String,
        expired: Double,
        shared: Double,
        creditQty: Double,
        unitPrice: Double,
        fieldExpenseItems: List<Map<String, Any>>,
        commissionPerUnit: Double = 0.0,
        commissionItems: List<Map<String, Any>>,
        prevColl: Double,
        received: Double,
        addToLedger: Boolean,
        notes: String
    ): Result<String> {
        val effDateFrom = dateFrom.ifBlank { date }
        if (totalSold <= 0.0) return Result.failure(IllegalArgumentException("Enter valid units sold (> 0)"))
        if (unitPrice <= 0.0) return Result.failure(IllegalArgumentException("Set a sale price for this store first"))
        if (returned < 0.0 || expired < 0.0 || shared < 0.0 || creditQty < 0.0 || prevColl < 0.0 || received < 0.0) {
            return Result.failure(IllegalArgumentException("Quantities and cash amounts cannot be negative"))
        }
        if (returned + expired + shared > totalSold + 0.0001) {
            return Result.failure(IllegalArgumentException("Combined returned + expired + shared cannot exceed total sold"))
        }
        if (commissionPerUnit < 0.0 || commissionPerUnit > 100_000.0) {
            return Result.failure(IllegalArgumentException("Commission per unit must be between 0 and 100,000"))
        }
        val netSold = InputGuards.round2(max(0.0, totalSold - returned - expired - shared))
        if (creditQty > netSold + 0.0001) {
            return Result.failure(IllegalArgumentException("Credit sales cannot exceed net sold quantity"))
        }

        val now = System.currentTimeMillis()
        val storeKey = resolvedStoreKey(store)
        val fieldExpensesTotal = InputGuards.round2(fieldExpenseItems.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 })
        val commissionPaid = InputGuards.round2(commissionItems.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 })
        val cashQty = InputGuards.round2(max(0.0, netSold - creditQty))
        val creditValue = InputGuards.round2(creditQty * unitPrice)
        val revenue = InputGuards.round2(netSold * unitPrice)
        val costPerKg = FormulaEngine.costSourceFromIndex(FormulaEngine.buildCostIndex(snapshot.production), storeKey, date).cp
        val totalCost = InputGuards.round2(netSold * costPerKg)
        val profit = InputGuards.round2(revenue - totalCost)
        val expected = InputGuards.round2((cashQty * unitPrice) + prevColl - fieldExpensesTotal - commissionPaid)
        val diff = InputGuards.round2(received - expected)
        val grossCommission = InputGuards.round2(commissionPerUnit * netSold)
        val commissionPayable = InputGuards.round2(max(0.0, grossCommission - commissionPaid))
        val statusText = when {
            abs(diff) <= 0.01 -> "PERFECT MATCH"
            diff < 0.0 -> "SHORT: ${InputGuards.fmtNum(abs(diff))}"
            else -> "OVER: ${InputGuards.fmtNum(diff)}"
        }
        val statusClass = if (diff < -0.01) "bad" else "good"

        val normRetType = returnType.trim().lowercase(Locale.ROOT)
        val isRepTransferReturn = returned > 0.0 && (normRetType == "rep" || normRetType == "transfer") &&
            returnTargetRep.isNotBlank() && !returnTargetRep.equals(seller, ignoreCase = true)
        val isStoreReturn = returned > 0.0 && !isRepTransferReturn
        if (returned > 0.0 && isRepTransferReturn) {
            val repContact = snapshot.customers.firstOrNull {
                it.deletedAt == null && it.name.equals(returnTargetRep, ignoreCase = true)
            }
            if ((repContact?.customSalePrice ?: 0.0) <= 0.0) {
                return Result.failure(IllegalArgumentException("Set a custom sale price for $returnTargetRep before transferring stock"))
            }
        }

        val calcId = ops.newId("calc")
        var returnEntryId = ""
        var returnLogId = ""
        var transferSaleId = ""

        if (isStoreReturn) {
            val retIds = createCalcStoreReturn(snapshot, date, store, returned, seller)
            returnEntryId = retIds.first
            returnLogId = retIds.second
        } else if (isRepTransferReturn) {
            transferSaleId = createCalcRepTransfer(snapshot, returnTargetRep, returned, date, seller)
        }

        var choraApplied = 0.0
        if (expired > 0.0 && snapshot.profile.isAdmin) {
            if (addChoraToInventory(snapshot, expired)) choraApplied = expired
        }

        // Link sales and rep records in active cycle
        val linkedSalesIds = mutableListOf<String>()
        val linkedRepSalesIds = mutableListOf<String>()
        val linkedRepReturnIds = mutableListOf<String>()

        if (!seller.equals("COMBINED", ignoreCase = true)) {
            val alreadyClaimedSales = snapshot.calculator
                .filter { it.deletedAt == null }
                .flatMap { it.linkedSalesIds }
                .toSet()
            val timeNow = DateUtils.nowTimeStr()
            val pendingSales = snapshot.sales.filter { s ->
                s.deletedAt == null &&
                    (s.currentRepProfile == "admin" || s.currentRepProfile.isBlank()) &&
                    s.customerName.equals(seller, ignoreCase = true) &&
                    s.paymentType == "CREDIT" &&
                    !s.creditReceived &&
                    s.transactionType != "OLD_DEBT" &&
                    s.id !in alreadyClaimedSales &&
                    DateUtils.isInRange(s.supplyDate.ifBlank { s.date }.take(10), effDateFrom, date)
            }
            pendingSales.forEach { s ->
                val patched = s.copy(
                    creditReceived = true,
                    creditReceivedManually = true,
                    creditReceivedDate = DateUtils.todayIso(),
                    creditReceivedTime = timeNow
                )
                ops.submitOrExecuteWrite(
                    profile = snapshot.profile,
                    targetCollection = "sales",
                    docId = s.id,
                    summaryTitle = "Calc Settle: ${s.customerName}",
                    summaryDetail = "$date • Linked to $calcId",
                    payload = WorkspaceJsonCodec.saleToMap(patched),
                    isEdit = true
                )
                linkedSalesIds.add(s.id)
            }

            val pendingRep = snapshot.repRecords.filter { r ->
                r.deletedAt == null &&
                    r.salesRep.equals(seller, ignoreCase = true) &&
                    r.usedInCalcId.isBlank() &&
                    (r.paymentType == "CREDIT" || r.paymentType == "COLLECTION") &&
                    DateUtils.isInRange(r.date.take(10), effDateFrom, date)
            }
            pendingRep.forEach { r ->
                val patched = r.copy(usedInCalcId = calcId)
                ops.submitOrExecuteWrite(
                    profile = snapshot.profile,
                    targetCollection = "rep",
                    docId = r.id,
                    summaryTitle = "Calc Use Rep Sale: ${r.customerName}",
                    summaryDetail = "$date • $calcId",
                    payload = WorkspaceJsonCodec.saleToMap(patched),
                    isEdit = true
                )
                linkedRepSalesIds.add(r.id)
            }

            val alreadyCoveredReturns = snapshot.calculator
                .filter { it.deletedAt == null }
                .flatMap { it.linkedRepReturnIds }
                .toSet()
            val pendingReturns = snapshot.sales.filter { s ->
                s.deletedAt == null &&
                    s.isRepReturn &&
                    s.salesRep.equals(seller, ignoreCase = true) &&
                    s.id !in alreadyCoveredReturns &&
                    s.date.take(10) <= date.take(10)
            }.sortedBy { it.timestamp }
            var remRet = returned
            var remExp = expired
            for (r in pendingReturns) {
                if (r.returnedQty <= remRet + 0.001 && r.expiredQty <= remExp + 0.001) {
                    remRet = max(0.0, remRet - r.returnedQty)
                    remExp = max(0.0, remExp - r.expiredQty)
                    linkedRepReturnIds.add(r.id)
                }
            }
        }

        val calc = CalculatorRecord(
            id = calcId,
            seller = seller.ifBlank { "Rep" },
            date = date,
            dateFrom = effDateFrom,
            unitPrice = InputGuards.round2(unitPrice),
            costPrice = costPerKg,
            revenue = revenue,
            profit = profit,
            totalCost = totalCost,
            totalSold = InputGuards.round2(totalSold),
            returned = InputGuards.round2(returned),
            returnStore = if (isStoreReturn) storeKey else "",
            returnEntryId = returnEntryId,
            returnLogId = returnLogId,
            expiredApplied = choraApplied,
            returnRep = if (isRepTransferReturn) returnTargetRep else "",
            returnType = if (isRepTransferReturn) "TRANSFER" else if (isStoreReturn) "RETURN" else "",
            transferSaleId = transferSaleId,
            expired = InputGuards.round2(expired),
            shared = InputGuards.round2(shared),
            creditQty = InputGuards.round2(creditQty),
            cashQty = cashQty,
            creditValue = creditValue,
            prevColl = InputGuards.round2(prevColl),
            fieldExpenses = fieldExpensesTotal,
            fieldExpenseItems = fieldExpenseItems,
            commissionPerUnit = InputGuards.round2(commissionPerUnit),
            commissionPaid = commissionPaid,
            commissionPaidItems = commissionItems,
            grossCommission = grossCommission,
            commissionPayable = commissionPayable,
            totalExpected = expected,
            received = InputGuards.round2(received),
            statusText = statusText,
            statusClass = statusClass,
            linkedSalesIds = linkedSalesIds,
            linkedRepSalesIds = linkedRepSalesIds,
            linkedRepReturnIds = linkedRepReturnIds,
            notes = InputGuards.sanitizeNotes(notes),
            timestamp = now
        )

        if (addToLedger && snapshot.profile.isAdmin) {
            val ledgerMirrors = mutableListOf<DailyLedgerEntry>()
            if (received > 0.0) {
                ledgerMirrors.add(
                    DailyLedgerEntry(
                        id = ops.newId("dl"),
                        type = "IN",
                        amount = InputGuards.round2(received),
                        category = "Sales",
                        mode = "Cash",
                        party = seller,
                        note = "Calculator: cash received (${store.name})",
                        date = date,
                        timestamp = now,
                        updatedAt = now,
                        linkCollection = "calculator",
                        linkDocId = calcId,
                        linkKind = "calc",
                        linkOrigin = "tab",
                        linkPart = "cash"
                    )
                )
            }
            if (fieldExpensesTotal > 0.0) {
                ledgerMirrors.add(
                    DailyLedgerEntry(
                        id = ops.newId("dl"),
                        type = "OUT",
                        amount = fieldExpensesTotal,
                        category = "Field Expense",
                        scope = "BUSINESS",
                        mode = "Cash",
                        party = seller,
                        note = "Calculator: field expenses",
                        date = date,
                        timestamp = now + 1L,
                        updatedAt = now,
                        linkCollection = "calculator",
                        linkDocId = calcId,
                        linkKind = "calc",
                        linkOrigin = "tab",
                        linkPart = "expense"
                    )
                )
            }
            if (commissionPaid > 0.0) {
                ledgerMirrors.add(
                    DailyLedgerEntry(
                        id = ops.newId("dl"),
                        type = "OUT",
                        amount = commissionPaid,
                        category = "Commission",
                        scope = "BUSINESS",
                        mode = "Cash",
                        party = seller,
                        note = "Calculator: commission paid",
                        date = date,
                        timestamp = now + 2L,
                        updatedAt = now,
                        linkCollection = "calculator",
                        linkDocId = calcId,
                        linkKind = "calc",
                        linkOrigin = "tab",
                        linkPart = "commission"
                    )
                )
            }
            appendDailyLedgerEntries(snapshot, ledgerMirrors)
        }

        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "calculator",
            docId = calcId,
            summaryTitle = "Calculator: $seller (${store.name})",
            summaryDetail = "$date • Expected ${InputGuards.fmtNum(expected)} • Rec ${InputGuards.fmtNum(received)}",
            payload = WorkspaceJsonCodec.calculatorToMap(calc)
        )
    }

    internal suspend fun createCalcStoreReturn(
        snapshot: WorkspaceSnapshot,
        date: String,
        store: StoreConfig,
        quantityKg: Double,
        seller: String
    ): Pair<String, String> {
        val storeKey = resolvedStoreKey(store)
        val idx = FormulaEngine.buildCostIndex(snapshot.production)
        val cp = FormulaEngine.costSourceFromIndex(idx, storeKey, date).cp
        val sp = if (store.salePrice > 0.0) store.salePrice else (snapshot.settings.defaultStorePrices[store.key] ?: 0.0)
        val now = System.currentTimeMillis()
        val prodId = ops.newId("ret")
        val logId = ops.newId("retlog")
        val prodEntry = ProductionRecord(
            id = prodId,
            date = date,
            time = DateUtils.nowTimeStr(),
            store = storeKey,
            net = InputGuards.round2(quantityKg),
            cp = cp,
            sp = sp,
            totalCost = InputGuards.round2(quantityKg * cp),
            totalSale = InputGuards.round2(quantityKg * sp),
            profit = InputGuards.round2(quantityKg * (sp - cp)),
            isReturn = true,
            returnedBy = InputGuards.sanitizeName(seller),
            returnNote = "Calculator settlement return",
            createdBy = snapshot.profile.uid,
            recordDate = date,
            timestamp = now,
            createdAt = now
        )
        ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "production",
            docId = prodId,
            summaryTitle = "Calc Return: ${store.name} (${InputGuards.formatKg(quantityKg)})",
            summaryDetail = "$date • $seller",
            payload = WorkspaceJsonCodec.productionToMap(prodEntry)
        )
        val retLog = ReturnRecord(
            id = logId,
            date = date,
            store = storeKey,
            returnStore = storeKey,
            seller = InputGuards.sanitizeName(seller),
            quantity = InputGuards.round2(quantityKg),
            returnType = "store",
            notes = "Calculator settlement return",
            timestamp = now
        )
        ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "returns",
            docId = logId,
            summaryTitle = "Return Log: ${store.name}",
            summaryDetail = "$date • ${InputGuards.formatKg(quantityKg)}",
            payload = WorkspaceJsonCodec.returnToMap(retLog)
        )
        return prodId to logId
    }

    internal suspend fun createCalcRepTransfer(
        snapshot: WorkspaceSnapshot,
        targetRep: String,
        quantityKg: Double,
        date: String,
        seller: String
    ): String {
        val repCust = snapshot.customers.firstOrNull {
            it.deletedAt == null && it.name.equals(targetRep, ignoreCase = true)
        }
        val unitPrice = repCust?.customSalePrice ?: 0.0
        val totalValue = InputGuards.round2(quantityKg * unitPrice)
        val now = System.currentTimeMillis()
        val saleId = ops.newId("sale")
        val rec = SalesRecord(
            id = saleId,
            date = DateUtils.todayIso(),
            supplyDate = date,
            time = DateUtils.nowTimeStr(),
            customerName = targetRep,
            quantity = InputGuards.round2(quantityKg),
            supplyStore = "N/A",
            paymentType = "CREDIT",
            salesRep = targetRep,
            currentRepProfile = "admin",
            totalCost = totalValue,
            totalValue = totalValue,
            profit = 0.0,
            unitPrice = InputGuards.round2(unitPrice),
            creditReceived = false,
            isRepTransfer = true,
            repTransferFrom = seller,
            timestamp = now,
            createdAt = now
        )
        ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "sales",
            docId = saleId,
            summaryTitle = "Rep Transfer: $seller → $targetRep",
            summaryDetail = "$date • ${InputGuards.formatKg(quantityKg)}",
            payload = WorkspaceJsonCodec.saleToMap(rec)
        )
        return saleId
    }

    suspend fun saveStockTransfer(
        snapshot: WorkspaceSnapshot,
        date: String,
        fromStore: StoreConfig,
        toStore: StoreConfig,
        qtyKg: Double,
        notes: String
    ): Result<String> {
        val fromKey = resolvedStoreKey(fromStore)
        val toKey = resolvedStoreKey(toStore)
        if (fromKey.equals(toKey, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Source and destination stores must differ"))
        }
        if (qtyKg <= 0.0) return Result.failure(IllegalArgumentException("Quantity must be greater than 0"))
        val avail = FinanceEngine.computeStoreStockSnapshots(snapshot)
            .firstOrNull { it.storeKey.equals(fromKey, ignoreCase = true) }?.availableKg ?: 0.0
        if (qtyKg > avail + 0.01) {
            return Result.failure(IllegalArgumentException("Only ${InputGuards.formatKg(avail)} available in ${fromStore.name}"))
        }
        val pairId = ops.newId("str")
        val now = System.currentTimeMillis()
        val outRec = ProductionRecord(
            id = "${pairId}_out",
            date = date,
            time = DateUtils.nowTimeStr(),
            store = fromKey,
            net = -InputGuards.round2(qtyKg),
            isTransfer = true,
            transferDirection = "out",
            transferPairId = pairId,
            transferPeerStore = toKey,
            transferNote = InputGuards.sanitizeNotes(notes),
            createdBy = snapshot.profile.uid,
            recordDate = date,
            timestamp = now,
            createdAt = now
        )
        val inRec = ProductionRecord(
            id = "${pairId}_in",
            date = date,
            time = DateUtils.nowTimeStr(),
            store = toKey,
            net = InputGuards.round2(qtyKg),
            isTransfer = true,
            transferDirection = "in",
            transferPairId = pairId,
            transferPeerStore = fromKey,
            transferNote = InputGuards.sanitizeNotes(notes),
            createdBy = snapshot.profile.uid,
            recordDate = date,
            timestamp = now,
            createdAt = now
        )
        ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "production",
            docId = outRec.id,
            summaryTitle = "Transfer OUT: ${fromStore.name} → ${toStore.name}",
            summaryDetail = InputGuards.formatKg(qtyKg),
            payload = WorkspaceJsonCodec.productionToMap(outRec)
        )
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "production",
            docId = inRec.id,
            summaryTitle = "Transfer IN: ${fromStore.name} → ${toStore.name}",
            summaryDetail = InputGuards.formatKg(qtyKg),
            payload = WorkspaceJsonCodec.productionToMap(inRec)
        )
    }

    suspend fun savePaymentTransfer(
        snapshot: WorkspaceSnapshot,
        date: String,
        fromEntity: EntityRecord,
        toEntity: EntityRecord,
        amount: Double,
        notes: String
    ): Result<String> {
        if (fromEntity.id == toEntity.id) return Result.failure(IllegalArgumentException("Source and destination accounts must differ"))
        if (amount <= 0.0) return Result.failure(IllegalArgumentException("Amount must be greater than 0"))
        val pairId = ops.newId("ptr")
        val now = System.currentTimeMillis()
        val outTx = PaymentTransaction(
            id = "${pairId}_out",
            date = date,
            time = DateUtils.nowTimeStr(),
            entityId = fromEntity.id,
            entityName = fromEntity.name,
            entityType = fromEntity.type,
            type = "OUT",
            amount = InputGuards.round2(amount),
            description = InputGuards.sanitizeNotes(notes).ifBlank { "Transfer to ${toEntity.name}" },
            paymentMethod = "Transfer",
            isTransfer = true,
            transferPairId = pairId,
            transferDirection = "out",
            peerEntityName = toEntity.name,
            timestamp = now
        )
        val inTx = PaymentTransaction(
            id = "${pairId}_in",
            date = date,
            time = DateUtils.nowTimeStr(),
            entityId = toEntity.id,
            entityName = toEntity.name,
            entityType = toEntity.type,
            type = "IN",
            amount = InputGuards.round2(amount),
            description = InputGuards.sanitizeNotes(notes).ifBlank { "Transfer from ${fromEntity.name}" },
            paymentMethod = "Transfer",
            isTransfer = true,
            transferPairId = pairId,
            transferDirection = "in",
            peerEntityName = fromEntity.name,
            timestamp = now
        )
        ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "transactions",
            docId = outTx.id,
            summaryTitle = "Transfer OUT: ${fromEntity.name} → ${toEntity.name}",
            summaryDetail = InputGuards.fmtNum(amount),
            payload = WorkspaceJsonCodec.transactionToMap(outTx)
        )
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "transactions",
            docId = inTx.id,
            summaryTitle = "Transfer IN: ${fromEntity.name} → ${toEntity.name}",
            summaryDetail = InputGuards.fmtNum(amount),
            payload = WorkspaceJsonCodec.transactionToMap(inTx)
        )
    }

    suspend fun saveStore(
        snapshot: WorkspaceSnapshot,
        existingKey: String?,
        name: String,
        formulaId: String?,
        salePrice: Double,
        active: Boolean
    ): Result<String> {
        val cleanName = InputGuards.sanitizeName(name).uppercase(Locale.ROOT)
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Store name required"))
        val now = System.currentTimeMillis()
        val key = existingKey?.takeIf { it.isNotBlank() } ?: FormulaEngine.makeStoreKey(cleanName, snapshot.stores)
        val store = StoreConfig(
            key = key,
            name = cleanName,
            salePrice = InputGuards.round2(salePrice),
            formulaId = formulaId?.takeIf { it.isNotBlank() },
            active = active,
            updatedAt = now
        )
        val nextList = snapshot.stores.filterNot { it.key == key } + store
        val rawList = nextList.map(WorkspaceJsonCodec::storeToMap)
        val ownerUid = snapshot.profile.effectiveOwnerUid
        val res = repo.saveSingleton(
            ownerUid = ownerUid,
            collection = "stores",
            docId = "list",
            payload = mapOf("stores" to rawList, "stores_timestamp" to now),
            kvUpdates = mapOf("stores" to WorkspaceJsonCodec.mapListToJson(rawList), "stores_timestamp" to now.toString())
        )
        // Sync defaultStorePrices in formulas/config
        val nextDefaults = snapshot.settings.defaultStorePrices + (key to store.salePrice)
        repo.saveSingleton(
            ownerUid = ownerUid,
            collection = "formulas",
            docId = "config",
            payload = mapOf("defaults" to nextDefaults, "defaults_timestamp" to now),
            kvUpdates = mapOf("defaults" to WorkspaceJsonCodec.mapToJson(nextDefaults))
        )
        return res.map { key }
    }

    suspend fun deleteStore(snapshot: WorkspaceSnapshot, storeKey: String): Result<Unit> {
        val now = System.currentTimeMillis()
        val nextList = snapshot.stores.filterNot { it.key == storeKey }
        val rawList = nextList.map(WorkspaceJsonCodec::storeToMap)
        val ownerUid = snapshot.profile.effectiveOwnerUid
        return repo.saveSingleton(
            ownerUid = ownerUid,
            collection = "stores",
            docId = "list",
            payload = mapOf("stores" to rawList, "stores_timestamp" to now),
            kvUpdates = mapOf("stores" to WorkspaceJsonCodec.mapListToJson(rawList), "stores_timestamp" to now.toString())
        )
    }

    suspend fun saveFormula(
        snapshot: WorkspaceSnapshot,
        existing: FormulaConfig?,
        name: String,
        additionalCost: Double,
        costAdjustmentFactor: Double,
        ingredients: List<FormulaIngredient>
    ): Result<String> {
        val clean = InputGuards.sanitizeName(name)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Formula name required"))
        val fId = existing?.id?.takeIf { it.isNotBlank() } ?: ops.newId("formula")
        for (ing in ingredients) {
            if (FormulaEngine.isLink(ing) && !FormulaEngine.canLink(snapshot.formulas, fId, ing.formulaId!!)) {
                return Result.failure(IllegalArgumentException("Circular sub-formula link detected for ${ing.name}"))
            }
        }
        val now = System.currentTimeMillis()
        val factor = if (costAdjustmentFactor > 0.0) costAdjustmentFactor else 1.0
        val updated = FormulaConfig(
            id = fId,
            name = clean,
            ingredients = ingredients,
            additionalCost = InputGuards.round2(additionalCost.coerceAtLeast(0.0)),
            costAdjustmentFactor = factor,
            unitsPerBatch = factor,
            savedCalcs = existing?.savedCalcs ?: emptyList(),
            updatedAt = now
        )
        val nextList = snapshot.formulas.filterNot { it.id == fId } + updated
        val rawList = nextList.map(WorkspaceJsonCodec::formulaToMap)
        val ownerUid = snapshot.profile.effectiveOwnerUid
        return repo.saveSingleton(
            ownerUid = ownerUid,
            collection = "formulas",
            docId = "config",
            payload = mapOf("formulas" to rawList, "formulas_timestamp" to now),
            kvUpdates = mapOf("formulas" to WorkspaceJsonCodec.mapListToJson(rawList), "formulas_timestamp" to now.toString())
        ).map { fId }
    }

    suspend fun deleteFormula(snapshot: WorkspaceSnapshot, formulaId: String): Result<Unit> {
        if (snapshot.stores.any { it.formulaId == formulaId }) {
            return Result.failure(IllegalArgumentException("Formula is linked to a store. Unlink it in Store Manager first."))
        }
        val usedBy = FormulaEngine.formulaUsers(snapshot.formulas, formulaId)
        if (usedBy.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Formula is used inside ${usedBy.first().name}"))
        }
        val now = System.currentTimeMillis()
        val nextList = snapshot.formulas.filterNot { it.id == formulaId }
        val rawList = nextList.map(WorkspaceJsonCodec::formulaToMap)
        val ownerUid = snapshot.profile.effectiveOwnerUid
        return repo.saveSingleton(
            ownerUid = ownerUid,
            collection = "formulas",
            docId = "config",
            payload = mapOf("formulas" to rawList, "formulas_timestamp" to now),
            kvUpdates = mapOf("formulas" to WorkspaceJsonCodec.mapListToJson(rawList), "formulas_timestamp" to now.toString())
        )
    }

    suspend fun saveFormulaSavedCalc(
        snapshot: WorkspaceSnapshot,
        formula: FormulaConfig,
        calc: SavedCalcRecord
    ): Result<Unit> {
        val now = System.currentTimeMillis()
        val nextCalcs = (listOf(calc.copy(updatedAt = now)) + formula.savedCalcs.filterNot { it.id == calc.id }).take(40)
        val updatedFormula = formula.copy(savedCalcs = nextCalcs, updatedAt = now)
        val nextList = snapshot.formulas.map { if (it.id == formula.id) updatedFormula else it }
        val rawList = nextList.map(WorkspaceJsonCodec::formulaToMap)
        val ownerUid = snapshot.profile.effectiveOwnerUid
        return repo.saveSingleton(
            ownerUid = ownerUid,
            collection = "formulas",
            docId = "config",
            payload = mapOf("formulas" to rawList, "formulas_timestamp" to now),
            kvUpdates = mapOf("formulas" to WorkspaceJsonCodec.mapListToJson(rawList), "formulas_timestamp" to now.toString())
        )
    }

    suspend fun deleteFormulaSavedCalc(
        snapshot: WorkspaceSnapshot,
        formula: FormulaConfig,
        calcId: String
    ): Result<Unit> {
        val now = System.currentTimeMillis()
        val updatedFormula = formula.copy(
            savedCalcs = formula.savedCalcs.filterNot { it.id == calcId },
            updatedAt = now
        )
        val nextList = snapshot.formulas.map { if (it.id == formula.id) updatedFormula else it }
        val rawList = nextList.map(WorkspaceJsonCodec::formulaToMap)
        val ownerUid = snapshot.profile.effectiveOwnerUid
        return repo.saveSingleton(
            ownerUid = ownerUid,
            collection = "formulas",
            docId = "config",
            payload = mapOf("formulas" to rawList, "formulas_timestamp" to now),
            kvUpdates = mapOf("formulas" to WorkspaceJsonCodec.mapListToJson(rawList), "formulas_timestamp" to now.toString())
        )
    }

    suspend fun addOrUpdateEntity(
        snapshot: WorkspaceSnapshot,
        existingId: String?,
        name: String,
        type: String,
        phone: String,
        openingBalance: Double,
        notes: String
    ): Result<String> {
        val clean = InputGuards.sanitizeName(name)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Entity name required"))
        val now = System.currentTimeMillis()
        val id = existingId?.takeIf { it.isNotBlank() } ?: ops.newId("ent")
        val ent = EntityRecord(
            id = id,
            name = clean,
            type = type,
            contact = phone.take(32),
            phone = phone.take(32),
            isSupplier = type == "supplier",
            isExpenseEntity = type == "expense",
            openingBalance = InputGuards.round2(openingBalance),
            notes = InputGuards.sanitizeNotes(notes),
            timestamp = now
        )
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "entities",
            docId = id,
            summaryTitle = "${if (existingId != null) "Edit" else "New"} Account: $clean",
            summaryDetail = type,
            payload = WorkspaceJsonCodec.entityToMap(ent),
            isEdit = existingId != null
        )
    }

    suspend fun saveCustomerOrClient(
        snapshot: WorkspaceSnapshot,
        existing: CustomerRecord?,
        isRepClient: Boolean,
        name: String,
        phone: String,
        address: String,
        oldDebit: Double,
        customSalePrice: Double,
        salesRep: String,
        notes: String,
        gpsLat: Double? = existing?.gpsLat,
        gpsLng: Double? = existing?.gpsLng
    ): Result<String> {
        val clean = InputGuards.sanitizeName(name)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Customer name required"))
        val col = if (isRepClient) "clients" else "customers"
        val now = System.currentTimeMillis()
        val id = existing?.id?.takeIf { it.isNotBlank() } ?: ops.newId(if (isRepClient) "client" else "cust")
        val rec = CustomerRecord(
            id = id,
            name = clean,
            phone = InputGuards.sanitizePhone(phone),
            address = address.trim().take(200),
            oldDebit = InputGuards.round2(oldDebit.coerceAtLeast(0.0)),
            customSalePrice = InputGuards.round2(customSalePrice.coerceAtLeast(0.0)),
            salesRep = salesRep,
            gpsLat = gpsLat,
            gpsLng = gpsLng,
            visitCount = existing?.visitCount ?: 0L,
            notes = InputGuards.sanitizeNotes(notes),
            createdAt = existing?.createdAt?.takeIf { it > 0L } ?: now,
            updatedAt = now,
            timestamp = now
        )
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = col,
            docId = id,
            summaryTitle = "${if (existing != null) "Edit" else "New"} ${if (isRepClient) "Rep Customer" else "Customer"}: $clean",
            summaryDetail = phone.ifBlank { address },
            payload = WorkspaceJsonCodec.customerToMap(rec),
            isEdit = existing != null
        )
    }

    suspend fun saveSettings(snapshot: WorkspaceSnapshot, next: WorkspaceSettings): Result<Unit> {
        val ownerUid = snapshot.profile.effectiveOwnerUid
        val now = System.currentTimeMillis()
        val settingsMap = mapOf(
            "currencySymbol" to next.currencySymbol,
            "financialYearStartMonth" to next.financialYearStartMonth,
            "hideSensitiveMoney" to next.hideSensitiveMoney,
            "biometricLockEnabled" to next.biometricLockEnabled,
            "soundEnabled" to next.soundEnabled,
            "hapticsEnabled" to next.hapticsEnabled,
            "themeMode" to next.themeMode,
            "paymentModes" to next.paymentModes,
            "fyCloseCount" to next.fyCloseCount,
            "lastYearCloseAt" to next.lastYearCloseAt
        )
        val res = repo.saveSingleton(
            ownerUid = ownerUid,
            collection = "settings",
            docId = "config",
            payload = mapOf(
                "settings" to settingsMap,
                "settings_timestamp" to now,
                "repProfile" to next.repProfile,
                "repProfile_timestamp" to now,
                "reps" to next.reps,
                "reps_timestamp" to now
            ),
            kvUpdates = mapOf(
                "settings" to WorkspaceJsonCodec.mapToJson(settingsMap + ("repProfile" to next.repProfile) + ("reps" to next.reps)),
                "theme" to "\"${next.themeMode}\"",
                "repProfile" to "\"${next.repProfile}\"",
                "reps" to WorkspaceJsonCodec.stringListToJson(next.reps),
                "categories" to WorkspaceJsonCodec.stringListToJson(next.expenseCategories),
                "field_expense_categories" to WorkspaceJsonCodec.stringListToJson(next.fieldExpenseCategories),
                "commission_categories" to WorkspaceJsonCodec.stringListToJson(next.commissionCategories)
            )
        )
        // Also persist team reps & category singletons when modified
        repo.saveSingleton(ownerUid, "settings", "team", mapOf("reps" to next.reps, "reps_timestamp" to now))
        repo.saveSingleton(ownerUid, "categories", "list", mapOf("categories" to next.expenseCategories, "categories_timestamp" to now))
        repo.saveSingleton(ownerUid, "field_expense_categories", "list", mapOf("categories" to next.fieldExpenseCategories, "field_expense_categories_timestamp" to now))
        repo.saveSingleton(ownerUid, "commission_categories", "list", mapOf("categories" to next.commissionCategories, "commission_categories_timestamp" to now))
        return res
    }

    suspend fun saveAccountsDirectory(snapshot: WorkspaceSnapshot, accounts: List<WorkspaceAccountRecord>): Result<Unit> {
        val ownerUid = snapshot.profile.effectiveOwnerUid
        val now = System.currentTimeMillis()
        val raw = accounts.map(WorkspaceJsonCodec::accountRecordToMap)
        return repo.saveSingleton(
            ownerUid = ownerUid,
            collection = "settings",
            docId = "accounts",
            payload = mapOf("accounts" to raw, "accounts_timestamp" to now),
            kvUpdates = mapOf("accounts" to WorkspaceJsonCodec.mapListToJson(raw))
        )
    }

    suspend fun auditRepriceZeroCostSales(snapshot: WorkspaceSnapshot): Result<Int> {
        if (!snapshot.profile.isAdmin) return Result.failure(IllegalArgumentException("Admin permission required"))
        val idx = FormulaEngine.buildCostIndex(snapshot.production)
        var fixed = 0
        snapshot.sales.filter { it.deletedAt == null && !FinanceEngine.isLedgerRow(it) && it.totalCost <= 0.0 && it.quantity > 0.0 }
            .forEach { s ->
                val res = FormulaEngine.costSourceFromIndex(idx, s.supplyStore, s.supplyDate.ifBlank { s.date })
                if (res.cp > 0.0) {
                    val repriced = FormulaEngine.repriceSale(s, res.cp, res.source)
                    ops.submitOrExecuteWrite(
                        profile = snapshot.profile,
                        targetCollection = "sales",
                        docId = s.id,
                        summaryTitle = "Reprice Sale: ${s.customerName}",
                        summaryDetail = "Cost ${InputGuards.fmtNum(res.cp)}/kg",
                        payload = WorkspaceJsonCodec.saleToMap(repriced),
                        isEdit = true
                    )
                    fixed++
                }
            }
        return Result.success(fixed)
    }

    suspend fun closeFinancialYear(snapshot: WorkspaceSnapshot): Result<String> {
        if (!snapshot.profile.isAdmin) {
            return Result.failure(IllegalArgumentException("Only workspace Admin can close the financial year"))
        }
        val today = DateUtils.todayIso()
        val now = System.currentTimeMillis()

        // 1. Update customers' oldDebit and clients' oldDebit with closing balances
        snapshot.customers.filter { it.deletedAt == null }.forEach { c ->
            val closing = FinanceEngine.computeCustomerBalance(
                c.name, snapshot.customers, snapshot.sales, emptyList(),
                snapshot.calculator, snapshot.stores, emptyList(), includeClients = false
            ).coerceAtLeast(0.0)
            if (abs(closing - c.oldDebit) > 0.01) {
                ops.submitOrExecuteWrite(
                    profile = snapshot.profile,
                    targetCollection = "customers",
                    docId = c.id,
                    summaryTitle = "FY Carry Forward: ${c.name}",
                    summaryDetail = InputGuards.fmtNum(closing),
                    payload = WorkspaceJsonCodec.customerToMap(c.copy(oldDebit = closing, updatedAt = now)),
                    isEdit = true
                )
            }
        }
        snapshot.clients.filter { it.deletedAt == null }.forEach { cl ->
            val closing = FinanceEngine.computeCustomerBalance(
                cl.name, emptyList(), emptyList(), snapshot.repRecords,
                snapshot.calculator, snapshot.stores, snapshot.clients, includeClients = true
            ).coerceAtLeast(0.0)
            if (abs(closing - cl.oldDebit) > 0.01) {
                ops.submitOrExecuteWrite(
                    profile = snapshot.profile,
                    targetCollection = "clients",
                    docId = cl.id,
                    summaryTitle = "FY Rep Carry Forward: ${cl.name}",
                    summaryDetail = InputGuards.fmtNum(closing),
                    payload = WorkspaceJsonCodec.customerToMap(cl.copy(oldDebit = closing, updatedAt = now)),
                    isEdit = true
                )
            }
        }

        // 2. Compact production per store (non-transfer, non-merged)
        val storeStocks = FinanceEngine.computeStoreStockSnapshots(snapshot)
        var compactedCount = 0
        snapshot.production.filter { it.deletedAt == null && !it.isMerged && !it.isTransfer }.forEach { p ->
            ops.softDeleteToRecycleBin(
                profile = snapshot.profile,
                collection = "production",
                docId = p.id,
                displayName = "FY Closed Production (${p.store})",
                displayDetail = "${p.date} • ${InputGuards.formatKg(p.net)}",
                displayAmount = InputGuards.fmtNum(p.totalSale),
                snapshotMap = WorkspaceJsonCodec.productionToMap(p)
            )
            compactedCount++
        }
        storeStocks.filter { it.availableKg > 0.0 }.forEach { st ->
            val carry = ProductionRecord(
                id = ops.newId("prod-merged"),
                date = today,
                time = DateUtils.nowTimeStr(),
                store = st.storeKey,
                grossWt = st.availableKg,
                contWt = 0.0,
                net = st.availableKg,
                cp = st.avgCostPerKg,
                sp = st.salePrice,
                totalCost = InputGuards.round2(st.availableKg * st.avgCostPerKg),
                totalSale = InputGuards.round2(st.availableKg * st.salePrice),
                profit = InputGuards.round2(st.availableKg * (st.salePrice - st.avgCostPerKg)),
                isMerged = true,
                returnNote = "FY carry-forward stock (${st.storeName})",
                createdBy = snapshot.profile.uid,
                recordDate = today,
                timestamp = now,
                createdAt = now
            )
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "production",
                docId = carry.id,
                summaryTitle = "FY Stock Carry: ${st.storeName}",
                summaryDetail = InputGuards.formatKg(st.availableKg),
                payload = WorkspaceJsonCodec.productionToMap(carry)
            )
        }

        // 3. Emit yearCloseSignal
        val ownerUid = snapshot.profile.effectiveOwnerUid
        val nextCount = snapshot.settings.fyCloseCount + 1
        repo.saveSingleton(
            ownerUid = ownerUid,
            collection = "settings",
            docId = "yearCloseSignal",
            payload = mapOf(
                "triggeredAt" to now,
                "fyCloseCount" to nextCount,
                "closedBy" to snapshot.profile.email
            ),
            kvUpdates = mapOf(
                "yearCloseSignal" to WorkspaceJsonCodec.mapToJson(
                    mapOf("triggeredAt" to now, "fyCloseCount" to nextCount)
                )
            )
        )

        return Result.success("Financial year closed ($compactedCount records compacted)")
    }

    fun buildBackupJson(snapshot: WorkspaceSnapshot): String {
        val root = JSONObject()
        root.put("version", 4)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("ownerUid", snapshot.profile.effectiveOwnerUid)
        root.put("production", JSONArray(snapshot.production.map { JSONObject(WorkspaceJsonCodec.productionToMap(it)) }))
        root.put("sales", JSONArray(snapshot.sales.map { JSONObject(WorkspaceJsonCodec.saleToMap(it)) }))
        root.put("rep", JSONArray(snapshot.repRecords.map { JSONObject(WorkspaceJsonCodec.saleToMap(it)) }))
        root.put("clients", JSONArray(snapshot.clients.map { JSONObject(WorkspaceJsonCodec.customerToMap(it)) }))
        root.put("customers", JSONArray(snapshot.customers.map { JSONObject(WorkspaceJsonCodec.customerToMap(it)) }))
        root.put("calculator", JSONArray(snapshot.calculator.map { JSONObject(WorkspaceJsonCodec.calculatorToMap(it)) }))
        root.put("inventory", JSONArray(snapshot.inventory.map { JSONObject(WorkspaceJsonCodec.inventoryToMap(it)) }))
        root.put("factory", JSONArray(snapshot.factory.map { JSONObject(WorkspaceJsonCodec.factoryToMap(it)) }))
        root.put("entities", JSONArray(snapshot.entities.map { JSONObject(WorkspaceJsonCodec.entityToMap(it)) }))
        root.put("transactions", JSONArray(snapshot.transactions.map { JSONObject(WorkspaceJsonCodec.transactionToMap(it)) }))
        root.put("expenses", JSONArray(snapshot.expenses.map { JSONObject(WorkspaceJsonCodec.expenseToMap(it)) }))
        root.put("returns", JSONArray(snapshot.returns.map { JSONObject(WorkspaceJsonCodec.returnToMap(it)) }))
        root.put("stores", JSONArray(snapshot.stores.map { JSONObject(WorkspaceJsonCodec.storeToMap(it)) }))
        root.put("formulas", JSONArray(snapshot.formulas.map { JSONObject(WorkspaceJsonCodec.formulaToMap(it)) }))
        root.put("daily_ledger", JSONArray(snapshot.ledgerEntries.map { JSONObject(WorkspaceJsonCodec.dailyLedgerEntryToMap(it)) }))
        root.put("categories", JSONArray(snapshot.settings.expenseCategories))
        root.put("field_expense_categories", JSONArray(snapshot.settings.fieldExpenseCategories))
        root.put("commission_categories", JSONArray(snapshot.settings.commissionCategories))
        return root.toString(2)
    }

    suspend fun restoreBackupPayload(snapshot: WorkspaceSnapshot, root: JSONObject): Result<Int> {
        if (!snapshot.profile.isAdmin) {
            return Result.failure(IllegalArgumentException("Only Admin can restore backups"))
        }
        var count = 0
        val ownerUid = snapshot.profile.effectiveOwnerUid
        val now = System.currentTimeMillis()

        // Restore stores & formulas & daily_ledger & categories if present
        root.optJSONArray("stores")?.let { arr ->
            val incoming = WorkspaceJsonCodec.jsonToMapList(arr).map(WorkspaceJsonCodec::mapToStore)
            if (incoming.isNotEmpty()) {
                val merged = (snapshot.stores + incoming).distinctBy { it.key }
                val raw = merged.map(WorkspaceJsonCodec::storeToMap)
                repo.saveSingleton(ownerUid, "stores", "list", mapOf("stores" to raw, "stores_timestamp" to now))
                count += incoming.size
            }
        }
        root.optJSONArray("formulas")?.let { arr ->
            val incoming = WorkspaceJsonCodec.jsonToMapList(arr).map(WorkspaceJsonCodec::mapToFormula)
            if (incoming.isNotEmpty()) {
                val merged = (snapshot.formulas + incoming).distinctBy { it.id }
                val raw = merged.map(WorkspaceJsonCodec::formulaToMap)
                repo.saveSingleton(ownerUid, "formulas", "config", mapOf("formulas" to raw, "formulas_timestamp" to now))
                count += incoming.size
            }
        }
        root.optJSONArray("daily_ledger")?.let { arr ->
            val incoming = WorkspaceJsonCodec.jsonToMapList(arr).map(WorkspaceJsonCodec::mapToDailyLedgerEntry)
            if (incoming.isNotEmpty()) {
                val merged = (snapshot.ledgerEntries + incoming).distinctBy { it.id }
                val raw = merged.map(WorkspaceJsonCodec::dailyLedgerEntryToMap)
                repo.saveSingleton(ownerUid, "daily_ledger", "entries", mapOf("daily_ledger" to raw, "daily_ledger_timestamp" to now))
                count += incoming.size
            }
        }

        suspend fun restoreCol(col: String, existingIds: Set<String>) {
            val arr = root.optJSONArray(col) ?: return
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val map = WorkspaceJsonCodec.jsonToMap(obj.toString())
                val id = map["id"]?.toString().orEmpty()
                if (id.isBlank() || id in existingIds) continue
                ops.submitOrExecuteWrite(
                    profile = snapshot.profile,
                    targetCollection = col,
                    docId = id,
                    summaryTitle = "Restored $col",
                    summaryDetail = id,
                    payload = map
                )
                count++
            }
        }

        restoreCol("customers", snapshot.customers.map { it.id }.toSet())
        restoreCol("clients", snapshot.clients.map { it.id }.toSet())
        restoreCol("entities", snapshot.entities.map { it.id }.toSet())
        restoreCol("inventory", snapshot.inventory.map { it.id }.toSet())
        restoreCol("production", snapshot.production.map { it.id }.toSet())
        restoreCol("sales", snapshot.sales.map { it.id }.toSet())
        restoreCol("rep", snapshot.repRecords.map { it.id }.toSet())
        restoreCol("calculator", snapshot.calculator.map { it.id }.toSet())
        restoreCol("factory", snapshot.factory.map { it.id }.toSet())
        restoreCol("transactions", snapshot.transactions.map { it.id }.toSet())
        restoreCol("expenses", snapshot.expenses.map { it.id }.toSet())
        restoreCol("returns", snapshot.returns.map { it.id }.toSet())

        return Result.success(count)
    }
}
