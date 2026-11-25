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
    private fun resolvedStoreKey(store: StoreConfig): String = store.key.ifBlank { store.name }

    private suspend fun appendDailyLedgerEntry(
        snapshot: WorkspaceSnapshot,
        entry: DailyLedgerEntry
    ): Result<Unit> {
        val ownerUid = snapshot.profile.effectiveOwnerUid
        val nextList = (listOf(entry) + snapshot.ledgerEntries).sortedByDescending { it.date }
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
        photoDataUri: String? = null
    ): Result<String> {
        val net = InputGuards.round2(grossWt - contWt)
        if (net <= 0.0) return Result.failure(IllegalArgumentException("Net weight must be greater than 0 kg"))

        val formula = snapshot.formulas.firstOrNull { it.id == store.formulaId }
        val desc = formula?.let { FormulaEngine.describeFormula(it, snapshot.formulas, snapshot.inventory) }
        val formulaCostTotal = if (desc != null && formulaUnits > 0.0) InputGuards.round2(desc.perUnit * formulaUnits) else 0.0
        val resolvedCp = when {
            costPerKgOverride > 0.0 -> InputGuards.round2(costPerKgOverride)
            formulaCostTotal > 0.0 -> InputGuards.round2(formulaCostTotal / net)
            else -> {
                val idx = FormulaEngine.buildCostIndex(snapshot.production)
                val lookup = FormulaEngine.costSourceFromIndex(idx, resolvedStoreKey(store), date)
                if (lookup.cp > 0.0) lookup.cp else (snapshot.settings.storeCosts[store.key] ?: 0.0)
            }
        }
        val sp = if (store.salePrice > 0.0) store.salePrice else (snapshot.settings.defaultStorePrices[store.key] ?: 0.0)
        val totalCost = if (formulaCostTotal > 0.0 && costPerKgOverride <= 0.0) formulaCostTotal else InputGuards.round2(net * resolvedCp)
        val totalSale = InputGuards.round2(net * sp)
        val profit = InputGuards.round2(totalSale - totalCost)
        val storeKey = resolvedStoreKey(store)

        val photoKeys = mutableListOf<String>()
        if (!photoDataUri.isNullOrBlank()) {
            val pk = ops.newId("photo_prod")
            ops.savePhoto(snapshot.profile, pk, photoDataUri)
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

    private suspend fun addChoraToInventory(snapshot: WorkspaceSnapshot, qtyKg: Double): Boolean {
        if (qtyKg <= 0.0) return false
        val chora = FinanceEngine.findMaterialByName(snapshot.inventory, "CHORA") ?: return false
        val updated = chora.copy(
            quantity = InputGuards.round6(chora.quantity + qtyKg),
            collectedKg = InputGuards.round6(FinanceEngine.collectedKg(chora) + qtyKg),
            totalValue = InputGuards.round2((chora.quantity + qtyKg) * chora.cost),
            timestamp = System.currentTimeMillis()
        )
        ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "inventory",
            docId = chora.id,
            summaryTitle = "CHORA +${InputGuards.formatKg(qtyKg)}",
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
        notes: String
    ): Result<String> {
        val cleanCust = InputGuards.sanitizeName(customerName)
        if (cleanCust.isBlank()) return Result.failure(IllegalArgumentException("Customer name is required"))
        val normType = paymentType.trim().uppercase(Locale.ROOT)
        val now = System.currentTimeMillis()
        val storeKey = store?.let(::resolvedStoreKey).orEmpty()
        val existingCust = snapshot.customers.firstOrNull {
            it.deletedAt == null && it.name.equals(cleanCust, ignoreCase = true)
        }

        if (normType == "COLLECTION" || normType == "PARTIAL_PAYMENT") {
            val expVal = InputGuards.round2(expiredKg * expiredUnitPrice)
            val totalPool = InputGuards.round2(collectedAmount + expVal)
            if (totalPool <= 0.0) {
                return Result.failure(IllegalArgumentException("Enter collected amount or expired kg"))
            }
            val colId = ops.newId("sale")
            val customerOpenSales = snapshot.sales.filter {
                it.deletedAt == null &&
                    it.customerName.equals(cleanCust, ignoreCase = true) &&
                    !it.creditReceived &&
                    (it.paymentType == "CREDIT" || it.transactionType == "OLD_DEBT")
            }
            val plan = if (snapshot.profile.isAdmin) {
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
                        collectionAllocs = nextAllocs
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
                customerPhone = existingCust?.phone ?: "",
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
                expiredQty = InputGuards.round2(expiredKg),
                expiredValue = expVal,
                expiredUnitPrice = InputGuards.round2(expiredUnitPrice),
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
                        linkDocId = colId
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
                        linkDocId = docId
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
        val costIdx = FormulaEngine.buildCostIndex(snapshot.production)
        val costLookup = FormulaEngine.costSourceFromIndex(costIdx, storeKey, effSupplyDate)
        val totalVal = InputGuards.round2(quantityKg * unitPrice)
        val totalCost = InputGuards.round2(quantityKg * costLookup.cp)
        val profit = InputGuards.round2(totalVal - totalCost)
        val saleId = ops.newId("sale")

        if (existingCust == null) {
            val newCust = CustomerRecord(
                id = ops.newId("cust"),
                name = cleanCust,
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
        }

        val sale = SalesRecord(
            id = saleId,
            date = date,
            supplyDate = effSupplyDate,
            time = DateUtils.nowTimeStr(),
            customerName = cleanCust,
            customerPhone = existingCust?.phone ?: "",
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
                    linkDocId = saleId
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
        notes: String
    ): Result<String> {
        val cleanCust = InputGuards.sanitizeName(customerName)
        val cleanRep = InputGuards.sanitizeName(repName).ifBlank { snapshot.profile.displayName.ifBlank { "Rep" } }
        if (cleanCust.isBlank()) return Result.failure(IllegalArgumentException("Customer name is required"))
        val normType = paymentType.trim().uppercase(Locale.ROOT)
        val now = System.currentTimeMillis()
        val storeKey = store?.let(::resolvedStoreKey).orEmpty()

        val existingClient = snapshot.clients.firstOrNull {
            it.deletedAt == null && it.name.equals(cleanCust, ignoreCase = true)
        }
        if (existingClient == null) {
            val newClient = CustomerRecord(
                id = ops.newId("client"),
                name = cleanCust,
                salesRep = cleanRep,
                gpsLat = gpsLat,
                gpsLng = gpsLng,
                visitCount = 1L,
                createdAt = now,
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
        }

        val totalVal = when (normType) {
            "COLLECTION", "PARTIAL_PAYMENT", "MONEY_OUT" -> InputGuards.round2(collectedAmount)
            "REP_RETURN" -> InputGuards.round2(quantityKg * unitPrice)
            else -> InputGuards.round2(quantityKg * unitPrice)
        }
        val costIdx = FormulaEngine.buildCostIndex(snapshot.production)
        val costLookup = FormulaEngine.costSourceFromIndex(costIdx, storeKey, date)
        val totalCost = if (normType in setOf("CASH", "CREDIT")) InputGuards.round2(quantityKg * costLookup.cp) else 0.0
        val profit = if (normType in setOf("CASH", "CREDIT")) InputGuards.round2(totalVal - totalCost) else 0.0

        val rec = SalesRecord(
            id = ops.newId("rep"),
            date = date,
            supplyDate = date,
            time = DateUtils.nowTimeStr(),
            customerName = cleanCust,
            customerPhone = existingClient?.phone ?: "",
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
            creditReceived = normType == "CASH" || normType == "COLLECTION",
            isCollection = normType == "COLLECTION",
            collectedAmount = if (normType == "COLLECTION") totalVal else if (normType == "CASH") totalVal else 0.0,
            expiredQty = InputGuards.round2(expiredKg),
            isRepReturn = normType == "REP_RETURN",
            returnedQty = if (normType == "REP_RETURN") InputGuards.round2(quantityKg) else 0.0,
            returnValue = if (normType == "REP_RETURN") totalVal else 0.0,
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
                    linkDocId = lotId
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
        notes: String
    ): Result<String> {
        val cleanName = InputGuards.sanitizeName(entity?.name ?: customName)
        if (cleanName.isBlank() || amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Name and positive amount are required"))
        }
        val now = System.currentTimeMillis()

        if (kind.equals("EXPENSE", ignoreCase = true)) {
            val expId = ops.newId("exp")
            val exp = ExpenseRecord(
                id = expId,
                date = date,
                name = cleanName,
                category = category.ifBlank { "Other" },
                amount = InputGuards.round2(amount),
                description = InputGuards.sanitizeNotes(notes),
                notes = InputGuards.sanitizeNotes(notes),
                entityId = entity?.id ?: "",
                entityName = entity?.name ?: "",
                paymentMethod = paymentMethod.ifBlank { "Cash" },
                timestamp = now
            )
            if (addToLedger && snapshot.profile.isAdmin) {
                appendDailyLedgerEntry(
                    snapshot,
                    DailyLedgerEntry(
                        id = ops.newId("dl"),
                        type = "OUT",
                        amount = exp.amount,
                        category = exp.category,
                        scope = "BUSINESS",
                        mode = exp.paymentMethod,
                        party = cleanName,
                        note = notes,
                        date = date,
                        timestamp = now,
                        updatedAt = now,
                        linkCollection = "expenses",
                        linkDocId = expId
                    )
                )
            }
            return ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "expenses",
                docId = expId,
                summaryTitle = "Expense: $cleanName",
                summaryDetail = "$date • ${exp.category} • ${InputGuards.fmtNum(amount)}",
                payload = WorkspaceJsonCodec.expenseToMap(exp)
            )
        }

        val normType = if (kind.equals("IN", ignoreCase = true)) "IN" else "OUT"
        val txId = ops.newId("tx")
        val tx = PaymentTransaction(
            id = txId,
            date = date,
            time = DateUtils.nowTimeStr(),
            entityId = entity?.id ?: "",
            entityName = cleanName,
            entityType = entity?.type ?: category,
            type = normType,
            amount = InputGuards.round2(amount),
            description = InputGuards.sanitizeNotes(notes),
            paymentMethod = paymentMethod.ifBlank { "Cash" },
            timestamp = now
        )

        if (normType == "OUT" && snapshot.profile.isAdmin) {
            val fifo = FinanceEngine.previewSupplierFifoAllocation(
                supplierIdOrName = entity?.id?.ifBlank { cleanName } ?: cleanName,
                paymentAmount = amount,
                inventory = snapshot.inventory
            )
            val invById = snapshot.inventory.associateBy { it.id }
            fifo.forEach { alloc ->
                val lot = invById[alloc.lotId] ?: return@forEach
                val nextLot = lot.copy(
                    totalPayable = alloc.afterPayable,
                    paymentStatus = if (alloc.afterPayable <= 0.01) "paid" else "pending",
                    paidDate = if (alloc.afterPayable <= 0.01) date else lot.paidDate
                )
                ops.submitOrExecuteWrite(
                    profile = snapshot.profile,
                    targetCollection = "inventory",
                    docId = lot.id,
                    summaryTitle = "Settle Lot: ${lot.materialName}",
                    summaryDetail = "Paid ${InputGuards.fmtNum(alloc.appliedPayment)}",
                    payload = WorkspaceJsonCodec.inventoryToMap(nextLot),
                    isEdit = true
                )
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
                    party = cleanName,
                    note = notes,
                    date = date,
                    timestamp = now,
                    updatedAt = now,
                    linkCollection = "transactions",
                    linkDocId = txId
                )
            )
        }

        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "transactions",
            docId = txId,
            summaryTitle = "$normType: $cleanName",
            summaryDetail = "$date • ${tx.paymentMethod} • ${InputGuards.fmtNum(amount)}",
            payload = WorkspaceJsonCodec.transactionToMap(tx)
        )
    }

    suspend fun saveCalculatorSettlement(
        snapshot: WorkspaceSnapshot,
        date: String,
        seller: String,
        store: StoreConfig,
        totalSold: Double,
        returned: Double,
        returnType: String, // "" | "store" | "rep"
        returnTargetRep: String,
        expired: Double,
        shared: Double,
        cashQty: Double,
        creditQty: Double,
        unitPrice: Double,
        fieldExpenseItems: List<Map<String, Any>>,
        commissionItems: List<Map<String, Any>>,
        prevColl: Double,
        received: Double,
        addToLedger: Boolean,
        notes: String
    ): Result<String> {
        val now = System.currentTimeMillis()
        val storeKey = resolvedStoreKey(store)
        val fieldExpensesTotal = InputGuards.round2(fieldExpenseItems.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 })
        val commissionTotal = InputGuards.round2(commissionItems.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 })
        val cashRevenue = InputGuards.round2(cashQty * unitPrice)
        val creditValue = InputGuards.round2(creditQty * unitPrice)
        val netRevenue = InputGuards.round2((totalSold - returned - expired).coerceAtLeast(0.0) * unitPrice)
        val expected = InputGuards.round2(cashRevenue + prevColl - shared - fieldExpensesTotal - commissionTotal)
        val diff = InputGuards.round2(received - expected)
        val statusText = when {
            abs(diff) <= 1.0 -> "Balanced"
            diff > 0.0 -> "Excess ${InputGuards.fmtNum(diff)}"
            else -> "Short ${InputGuards.fmtNum(-diff)}"
        }
        val statusClass = if (abs(diff) <= 1.0) "good" else if (diff > 0.0) "info" else "bad"
        val calcId = ops.newId("calc")

        if (returned > 0.0 && returnType == "store") {
            saveStockReturn(snapshot, date, store, returned, 0.0, seller, "Calculator settlement return")
        }
        if (expired > 0.0 && snapshot.profile.isAdmin) {
            addChoraToInventory(snapshot, expired)
        }

        val calc = CalculatorRecord(
            id = calcId,
            seller = seller.ifBlank { "Rep" },
            date = date,
            unitPrice = InputGuards.round2(unitPrice),
            costPrice = FormulaEngine.costSourceFromIndex(FormulaEngine.buildCostIndex(snapshot.production), storeKey, date).cp,
            revenue = netRevenue,
            totalSold = InputGuards.round2(totalSold),
            returned = InputGuards.round2(returned),
            returnStore = if (returnType == "store") storeKey else "",
            returnRep = if (returnType == "rep") returnTargetRep else "",
            returnType = returnType,
            expired = InputGuards.round2(expired),
            expiredApplied = expired,
            shared = InputGuards.round2(shared),
            creditQty = InputGuards.round2(creditQty),
            cashQty = InputGuards.round2(cashQty),
            creditValue = creditValue,
            prevColl = InputGuards.round2(prevColl),
            fieldExpenses = fieldExpensesTotal,
            fieldExpenseItems = fieldExpenseItems,
            commissionPaidItems = commissionItems,
            totalExpected = expected,
            received = InputGuards.round2(received),
            commissionPaid = commissionTotal,
            statusText = statusText,
            statusClass = statusClass,
            notes = InputGuards.sanitizeNotes(notes),
            timestamp = now
        )

        if (addToLedger && snapshot.profile.isAdmin) {
            if (received > 0.0) {
                appendDailyLedgerEntry(
                    snapshot,
                    DailyLedgerEntry(
                        id = ops.newId("dl"),
                        type = "IN",
                        amount = InputGuards.round2(received),
                        category = "Sales",
                        scope = "",
                        mode = "Cash",
                        party = seller,
                        note = "Calculator settlement (${store.name})",
                        date = date,
                        timestamp = now,
                        updatedAt = now,
                        linkCollection = "calculator",
                        linkDocId = calcId
                    )
                )
            }
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
