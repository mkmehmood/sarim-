package com.gullzubair.sarim.ui.viewmodel

import com.gullzubair.sarim.data.firebase.AppRepository
import com.gullzubair.sarim.data.firebase.WorkspaceOperations
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.CustomerRecord
import com.gullzubair.sarim.data.model.EntityRecord
import com.gullzubair.sarim.data.model.ExpenseRecord
import com.gullzubair.sarim.data.model.FactoryRecord
import com.gullzubair.sarim.data.model.FormulaConfig
import com.gullzubair.sarim.data.model.InventoryRecord
import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.ProductionEngine
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * In-place edit, edit-history diffing, reversal, and entity/customer persistence
 * matching web `edit-mode.js`, `edit-history.js`, `utilities-sales.js`, `factory.js`,
 * `payments-tab.js`, and `rep-sales.js`.
 */
class SarimEditActions(
    private val repo: AppRepository,
    private val ops: WorkspaceOperations,
    private val dispatcher: SarimActionDispatcher
) {
    private fun actorName(snapshot: WorkspaceSnapshot): String =
        snapshot.profile.displayName.ifBlank { snapshot.profile.email.ifBlank { "Admin" } }

    private fun nowIso(): String = Instant.now().toString()

    // --- Production Edit ---

    suspend fun editProduction(
        snapshot: WorkspaceSnapshot,
        original: ProductionRecord,
        date: String,
        store: StoreConfig,
        grossWt: Double,
        contWt: Double,
        formulaUnits: Double,
        costPerKgOverride: Double,
        notes: String,
        retainedPhotoKeys: List<String>,
        newPhotoDataUris: List<String>
    ): Result<String> {
        if (original.isMerged || original.isReturn || original.isTransfer) {
            return Result.failure(IllegalArgumentException("This production entry cannot be edited"))
        }
        val net = InputGuards.round2(grossWt - contWt)
        if (net <= 0.0) return Result.failure(IllegalArgumentException("Net weight must be greater than 0 kg"))

        dispatcher.validateProductionEntry(snapshot, store, net, formulaUnits, original)?.let {
            return Result.failure(IllegalArgumentException(it))
        }
        // Web guard: an edit may not shrink a day's stock below what was already sold from it.
        run {
            val sameBucket = ProductionEngine.matchesStore(original.store, store) && original.date == date
            val prodOld = snapshot.production.filter {
                it.deletedAt == null && !it.isReturn && it.id != original.id && it.date == original.date &&
                    it.store.equals(original.store, true)
            }.sumOf { it.net } + (if (sameBucket) net else 0.0)
            val retOld = snapshot.returns.filter {
                it.deletedAt == null && it.date == original.date && it.store.equals(original.store, true)
            }.sumOf { it.quantity }
            val soldOld = snapshot.sales.filter {
                it.deletedAt == null && it.supplyDate.ifBlank { it.date } == original.date &&
                    it.supplyStore.equals(original.store, true)
            }.sumOf { it.quantity }
            if (prodOld + retOld - soldOld < -1e-6) {
                return Result.failure(
                    IllegalArgumentException(
                        "Cannot reduce this entry: ${InputGuards.fmtNum(soldOld - prodOld - retOld)} kg already sold from ${original.date}. Reduce or delete those sales first."
                    )
                )
            }
        }

        val storeKey = dispatcher.resolvedStoreKey(store)
        val formula = snapshot.formulas.firstOrNull { it.id == store.formulaId }
        val desc = formula?.let { FormulaEngine.describeFormula(it, snapshot.formulas, snapshot.inventory) }
        val dyn = ProductionEngine.dynamicCost(snapshot, store, formulaUnits, net, original)
        val formulaCostTotal = if (dyn.hasFormula) InputGuards.round2(dyn.totalFormulaCost) else 0.0
        val resolvedCp = when {
            costPerKgOverride > 0.0 -> InputGuards.round2(costPerKgOverride)
            formulaCostTotal > 0.0 -> InputGuards.round2(formulaCostTotal / net)
            else -> {
                val idx = FormulaEngine.buildCostIndex(snapshot.production)
                val lookup = FormulaEngine.costSourceFromIndex(idx, storeKey, date)
                if (lookup.cp > 0.0) lookup.cp else (snapshot.settings.storeCosts[store.key] ?: original.cp)
            }
        }
        val sp = if (store.salePrice > 0.0) store.salePrice else (snapshot.settings.defaultStorePrices[store.key] ?: original.sp)
        val totalCost = if (formulaCostTotal > 0.0 && costPerKgOverride <= 0.0) formulaCostTotal else InputGuards.round2(net * resolvedCp)
        val totalSale = InputGuards.round2(net * sp)
        val profit = InputGuards.round2(totalSale - totalCost)

        // Manage photos (up to 6 total)
        val removedKeys = original.photoKeys.filter { it !in retainedPhotoKeys }
        removedKeys.forEach { pk -> ops.savePhoto(snapshot.profile, pk, null) }
        val finalKeys = retainedPhotoKeys.take(6).toMutableList()
        for (uri in newPhotoDataUris) {
            if (finalKeys.size >= 6) break
            if (uri.isBlank()) continue
            val pk = ops.newId("photo_prod")
            ops.savePhoto(snapshot.profile, pk, uri)
            finalKeys.add(pk)
        }

        val cleanNotes = InputGuards.sanitizeNotes(notes)
        val diff = FinanceEngine.buildEditDiff(
            fields = listOf(
                "date" to "Date",
                "store" to "Store",
                "grossWt" to "Gross Wt",
                "contWt" to "Container Wt",
                "net" to "Net Wt",
                "formulaUnits" to "Formula Units",
                "totalCost" to "Total Cost",
                "returnNote" to "Notes"
            ),
            before = mapOf(
                "date" to original.date,
                "store" to original.store,
                "grossWt" to original.grossWt,
                "contWt" to original.contWt,
                "net" to original.net,
                "formulaUnits" to original.formulaUnits,
                "totalCost" to original.totalCost,
                "returnNote" to original.returnNote
            ),
            after = mapOf(
                "date" to date,
                "store" to storeKey,
                "grossWt" to InputGuards.round2(grossWt),
                "contWt" to InputGuards.round2(contWt),
                "net" to net,
                "formulaUnits" to InputGuards.round2(formulaUnits),
                "totalCost" to totalCost,
                "returnNote" to cleanNotes
            )
        )
        val iso = nowIso()
        val updated = original.copy(
            date = date,
            recordDate = date,
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
            formulaId = formula?.id ?: original.formulaId,
            formulaName = formula?.name ?: original.formulaName,
            formulaMaterials = desc?.items?.map {
                mapOf("id" to it.id, "name" to it.name, "qty" to InputGuards.round4(it.quantity * formulaUnits))
            } ?: original.formulaMaterials,
            formulaCost = formulaCostTotal,
            photoKeys = finalKeys,
            returnNote = cleanNotes,
            updatedAtEpoch = System.currentTimeMillis(),
            isEdited = true,
            editedAt = iso,
            editHistory = FinanceEngine.pushEditHistory(original.editHistory, actorName(snapshot), diff, iso)
        )
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "production",
            docId = original.id,
            summaryTitle = "Edit Production: ${store.name} (${InputGuards.formatKg(net)})",
            summaryDetail = "$date • Updated",
            payload = WorkspaceJsonCodec.productionToMap(updated),
            isEdit = true
        )
    }

    // --- Sales / Collection / Rep Return Edit ---

    suspend fun editSaleOrCollection(
        snapshot: WorkspaceSnapshot,
        original: SalesRecord,
        date: String,
        supplyDate: String,
        paymentType: String,
        customerName: String,
        store: StoreConfig?,
        salesRep: String,
        quantityKg: Double,
        unitPrice: Double,
        collectedAmount: Double,
        expiredKg: Double,
        expiredUnitPrice: Double,
        notes: String
    ): Result<String> {
        if (original.isMerged) return Result.failure(IllegalArgumentException("Merged records cannot be edited"))
        if (original.isTransfer || original.isRepTransfer) {
            return Result.failure(IllegalArgumentException("Transferred stock entries cannot be edited directly"))
        }
        val cleanCust = InputGuards.sanitizeName(customerName)
        if (cleanCust.isBlank()) return Result.failure(IllegalArgumentException("Customer name is required"))
        val normType = paymentType.trim().uppercase(Locale.ROOT)
        val storeKey = store?.let(dispatcher::resolvedStoreKey).orEmpty()
        val iso = nowIso()

        if (original.paymentType == "PARTIAL_PAYMENT" && original.relatedSaleId.isNotBlank() &&
            abs(original.totalValue - collectedAmount) > 0.005
        ) {
            return Result.failure(IllegalArgumentException("This payment is linked to a credit sale. Delete and re-enter it instead of changing the amount."))
        }

        if (original.paymentType == "COLLECTION" || normType == "COLLECTION") {
            val expVal = InputGuards.round2(expiredKg * expiredUnitPrice)
            val totalPool = InputGuards.round2(collectedAmount + expVal)
            if (totalPool <= 0.0) {
                return Result.failure(IllegalArgumentException("Enter collected amount or expired kg"))
            }
            val origExpApplied = if (original.expiredQty > 0.0) original.expiredApplied else 0.0
            val expDelta = InputGuards.round2(expiredKg - origExpApplied)
            if (expDelta < 0.0) {
                val chora = FinanceEngine.findMaterialByName(snapshot.inventory, "CHORA")
                if (chora != null && chora.quantity + 0.0001 < -expDelta) {
                    return Result.failure(IllegalArgumentException("Only ${InputGuards.formatKg(chora.quantity)} of CHORA remains; cannot reduce expired qty by ${InputGuards.formatKg(-expDelta)}"))
                }
            }

            // Revert prior allocations of this collection from parent sales
            val workingSales = snapshot.sales.filter { it.deletedAt == null }.associateBy { it.id }.toMutableMap()
            original.allocations.forEach { (saleId, allocAmt) ->
                val parent = workingSales[saleId] ?: return@forEach
                val wasFull = parent.collectionAllocFull[original.id] == true || original.allocationFull[saleId] == true
                val nextPartial = InputGuards.round2(max(0.0, parent.partialPaymentReceived - allocAmt))
                val updatedParent = parent.copy(
                    collectionAllocs = parent.collectionAllocs - original.id,
                    collectionAllocFull = parent.collectionAllocFull - original.id,
                    partialPaymentReceived = if (parent.transactionType == "OLD_DEBT" || !wasFull) nextPartial else nextPartial,
                    creditReceived = if (wasFull) false else parent.creditReceived,
                    creditReceivedManually = if (wasFull) false else parent.creditReceivedManually
                )
                workingSales[saleId] = updatedParent
            }

            val openSales = workingSales.values.filter {
                it.id != original.id &&
                    it.customerName.equals(cleanCust, ignoreCase = true) &&
                    !it.creditReceived &&
                    (it.paymentType == "CREDIT" || it.transactionType == "OLD_DEBT")
            }
            val newPlan = if (snapshot.profile.isAdmin) {
                FinanceEngine.computeCollectionFifoPlan(totalPool, openSales, snapshot.customers, snapshot.stores)
            } else emptyMap()
            val appliedCash = InputGuards.round2(min(collectedAmount, newPlan.values.sumOf { it.first }))
            val unappliedCash = InputGuards.round2(max(0.0, collectedAmount - appliedCash))
            val expAppliedAmt = InputGuards.round2(max(0.0, newPlan.values.sumOf { it.first } - appliedCash))

            if (snapshot.profile.isAdmin) {
                newPlan.forEach { (saleId, pair) ->
                    val s = workingSales[saleId] ?: return@forEach
                    val (amt, full) = pair
                    workingSales[saleId] = s.copy(
                        partialPaymentReceived = if (full) s.totalValue else InputGuards.round2(s.partialPaymentReceived + amt),
                        creditReceived = full,
                        collectionAllocs = s.collectionAllocs + (original.id to amt),
                        collectionAllocFull = s.collectionAllocFull + (original.id to full)
                    )
                }
                val touchedIds = (original.allocations.keys + newPlan.keys).toSet()
                touchedIds.forEach { sid ->
                    val s = workingSales[sid] ?: return@forEach
                    ops.submitOrExecuteWrite(
                        profile = snapshot.profile,
                        targetCollection = "sales",
                        docId = s.id,
                        summaryTitle = "Collection Re-alloc: ${s.customerName}",
                        summaryDetail = date,
                        payload = WorkspaceJsonCodec.saleToMap(s),
                        isEdit = true
                    )
                }
                if (expDelta != 0.0) {
                    dispatcher.addChoraToInventory(snapshot, expDelta)
                }
            }

            val cleanNotes = InputGuards.sanitizeNotes(notes)
            val diff = FinanceEngine.buildEditDiff(
                fields = listOf(
                    "date" to "Date",
                    "customerName" to "Customer",
                    "collectedAmount" to "Collected",
                    "expiredQty" to "Expired kg",
                    "notes" to "Notes"
                ),
                before = mapOf(
                    "date" to original.date,
                    "customerName" to original.customerName,
                    "collectedAmount" to original.collectedAmount,
                    "expiredQty" to original.expiredQty,
                    "notes" to original.notes
                ),
                after = mapOf(
                    "date" to date,
                    "customerName" to cleanCust,
                    "collectedAmount" to InputGuards.round2(collectedAmount),
                    "expiredQty" to InputGuards.round2(expiredKg),
                    "notes" to cleanNotes
                )
            )
            val updated = original.copy(
                date = date,
                supplyDate = date,
                customerName = cleanCust,
                supplyStore = storeKey,
                salesRep = salesRep.ifBlank { "NONE" },
                collectedAmount = InputGuards.round2(collectedAmount),
                totalValue = if (newPlan.isEmpty()) InputGuards.round2(collectedAmount) else unappliedCash,
                profit = if (newPlan.isEmpty()) InputGuards.round2(collectedAmount) else unappliedCash,
                allocations = newPlan.mapValues { it.value.first },
                allocationFull = newPlan.mapValues { it.value.second },
                expiredQty = InputGuards.round2(expiredKg),
                expiredValue = expVal,
                expiredUnitPrice = InputGuards.round2(expiredUnitPrice),
                expiredApplied = if (expiredKg > 0.0) expiredKg else 0.0,
                expiredAllocated = expAppliedAmt,
                notes = cleanNotes,
                updatedAtEpoch = System.currentTimeMillis(),
                isEdited = true,
                editedAt = iso,
                editHistory = FinanceEngine.pushEditHistory(original.editHistory, actorName(snapshot), diff, iso)
            )
            val res = ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "sales",
                docId = original.id,
                summaryTitle = "Edit Collection: $cleanCust",
                summaryDetail = "$date • ${InputGuards.fmtNum(collectedAmount)}",
                payload = WorkspaceJsonCodec.saleToMap(updated),
                isEdit = true
            )
            if (res.isSuccess) {
                val nextSales = snapshot.sales.map { if (it.id == original.id) updated else (workingSales[it.id] ?: it) }
                dispatcher.syncLedgerMirrorToStorage(snapshot.copy(sales = nextSales))
            }
            return res
        }

        // Guard child payments on edited CASH/CREDIT sale
        val children = snapshot.sales.filter {
            it.deletedAt == null && it.id != original.id &&
                (it.relatedSaleId == original.id || original.id in it.allocations.keys)
        }
        val childTotal = InputGuards.round2(children.sumOf { it.totalValue } + original.partialPaymentReceived)
        if (childTotal > 0.005) {
            if (normType == "CASH") {
                return Result.failure(IllegalArgumentException("${InputGuards.fmtNum(childTotal)} was already collected against this credit sale. Remove linked collections first."))
            }
            val newVal = InputGuards.round2(quantityKg * unitPrice)
            if (newVal + 0.01 < childTotal) {
                return Result.failure(IllegalArgumentException("Sale total cannot be below already collected ${InputGuards.fmtNum(childTotal)}"))
            }
        }

        if (original.isRepReturn || normType == "REP_RETURN") {
            if (quantityKg <= 0.0 && expiredKg <= 0.0) {
                return Result.failure(IllegalArgumentException("Enter returned or expired kg"))
            }
            val cleanNotes = InputGuards.sanitizeNotes(notes)
            val retVal = InputGuards.round2((quantityKg + expiredKg) * unitPrice)
            val diff = FinanceEngine.buildEditDiff(
                listOf("date" to "Date", "returnedQty" to "Returned", "expiredQty" to "Expired", "returnValue" to "Value", "notes" to "Notes"),
                mapOf("date" to original.date, "returnedQty" to original.returnedQty, "expiredQty" to original.expiredQty, "returnValue" to original.returnValue, "notes" to original.notes),
                mapOf("date" to date, "returnedQty" to InputGuards.round2(quantityKg), "expiredQty" to InputGuards.round2(expiredKg), "returnValue" to retVal, "notes" to cleanNotes)
            )
            val updated = original.copy(
                date = date,
                customerName = cleanCust,
                salesRep = salesRep.ifBlank { original.salesRep },
                returnedQty = InputGuards.round2(quantityKg),
                expiredQty = InputGuards.round2(expiredKg),
                unitPrice = InputGuards.round2(unitPrice),
                returnValue = retVal,
                notes = cleanNotes,
                updatedAtEpoch = System.currentTimeMillis(),
                isEdited = true,
                editedAt = iso,
                editHistory = FinanceEngine.pushEditHistory(original.editHistory, actorName(snapshot), diff, iso)
            )
            return ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "sales",
                docId = original.id,
                summaryTitle = "Edit Rep Return: $cleanCust",
                summaryDetail = "$date • ${InputGuards.formatKg(quantityKg)}",
                payload = WorkspaceJsonCodec.saleToMap(updated),
                isEdit = true
            )
        }

        // Standard CASH or CREDIT sale edit
        if (quantityKg <= 0.0 || unitPrice <= 0.0) {
            return Result.failure(IllegalArgumentException("Quantity and unit price must be greater than 0"))
        }
        val effSupplyDate = supplyDate.ifBlank { date }
        val sameCostBasis = storeKey.equals(original.supplyStore, ignoreCase = true) && effSupplyDate == original.supplyDate.ifBlank { original.date }
        val costLookup = if (sameCostBasis && original.quantity > 0.0 && original.totalCost > 0.0) {
            FormulaEngine.CostLookupResult(InputGuards.round2(original.totalCost / original.quantity), original.costSource.ifBlank { "store-average" })
        } else {
            FormulaEngine.costSourceFromIndex(FormulaEngine.buildCostIndex(snapshot.production), storeKey, effSupplyDate)
        }
        val totalVal = InputGuards.round2(quantityKg * unitPrice)
        val totalCost = InputGuards.round2(quantityKg * costLookup.cp)
        val profit = InputGuards.round2(totalVal - totalCost)
        val cleanNotes = InputGuards.sanitizeNotes(notes)
        val creditReceived = when {
            normType == "CASH" -> true
            original.paymentType == "CREDIT" -> original.creditReceived
            else -> false
        }
        val diff = FinanceEngine.buildEditDiff(
            listOf(
                "date" to "Date",
                "supplyDate" to "Supply Date",
                "customerName" to "Customer",
                "supplyStore" to "Store",
                "paymentType" to "Payment Type",
                "quantity" to "Quantity",
                "unitPrice" to "Rate",
                "totalValue" to "Total Value",
                "notes" to "Notes"
            ),
            mapOf(
                "date" to original.date,
                "supplyDate" to original.supplyDate,
                "customerName" to original.customerName,
                "supplyStore" to original.supplyStore,
                "paymentType" to original.paymentType,
                "quantity" to original.quantity,
                "unitPrice" to original.unitPrice,
                "totalValue" to original.totalValue,
                "notes" to original.notes
            ),
            mapOf(
                "date" to date,
                "supplyDate" to effSupplyDate,
                "customerName" to cleanCust,
                "supplyStore" to storeKey,
                "paymentType" to normType,
                "quantity" to InputGuards.round2(quantityKg),
                "unitPrice" to InputGuards.round2(unitPrice),
                "totalValue" to totalVal,
                "notes" to cleanNotes
            )
        )
        val updated = original.copy(
            date = date,
            supplyDate = effSupplyDate,
            customerName = cleanCust,
            supplyStore = storeKey,
            paymentType = normType,
            salesRep = salesRep.ifBlank { "NONE" },
            quantity = InputGuards.round2(quantityKg),
            unitPrice = InputGuards.round2(unitPrice),
            totalValue = totalVal,
            totalCost = totalCost,
            profit = profit,
            costSource = costLookup.source,
            creditReceived = creditReceived,
            collectedAmount = if (normType == "CASH") totalVal else 0.0,
            notes = cleanNotes,
            updatedAtEpoch = System.currentTimeMillis(),
            isEdited = true,
            editedAt = iso,
            editHistory = FinanceEngine.pushEditHistory(original.editHistory, actorName(snapshot), diff, iso)
        )
        val res = ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "sales",
            docId = original.id,
            summaryTitle = "Edit $normType Sale: $cleanCust",
            summaryDetail = "$date • ${InputGuards.formatKg(quantityKg)} • ${InputGuards.fmtNum(totalVal)}",
            payload = WorkspaceJsonCodec.saleToMap(updated),
            isEdit = true
        )
        if (res.isSuccess) {
            val nextSales = snapshot.sales.map { if (it.id == original.id) updated else it }
            dispatcher.syncLedgerMirrorToStorage(snapshot.copy(sales = nextSales))
        }
        return res
    }

    // --- Rep Sale / Collection Edit ---

    suspend fun editRepRecord(
        snapshot: WorkspaceSnapshot,
        original: SalesRecord,
        date: String,
        paymentType: String,
        customerName: String,
        store: StoreConfig?,
        quantityKg: Double,
        unitPrice: Double,
        collectedAmount: Double,
        expiredKg: Double,
        notes: String
    ): Result<String> {
        if (original.usedInCalcId.isNotBlank()) {
            return Result.failure(IllegalArgumentException("Already reconciled in Calculator ($date). Undo that calculation first."))
        }
        if (original.paymentType == "PARTIAL_PAYMENT" && original.relatedSaleId.isNotBlank() &&
            abs(original.totalValue - collectedAmount) > 0.005
        ) {
            return Result.failure(IllegalArgumentException("Partial payment is linked to a credit sale. Delete and re-enter it instead."))
        }
        val cleanCust = InputGuards.sanitizeName(customerName)
        if (cleanCust.isBlank()) return Result.failure(IllegalArgumentException("Customer name is required"))
        val normType = paymentType.trim().uppercase(Locale.ROOT)
        val storeKey = store?.let(dispatcher::resolvedStoreKey).orEmpty()
        val totalVal = when (normType) {
            "COLLECTION", "PARTIAL_PAYMENT", "MONEY_OUT" -> InputGuards.round2(collectedAmount)
            else -> InputGuards.round2(quantityKg * unitPrice)
        }
        if (totalVal <= 0.0 && expiredKg <= 0.0) {
            return Result.failure(IllegalArgumentException("Enter valid quantity/rate or collected amount"))
        }
        val costLookup = FormulaEngine.costSourceFromIndex(FormulaEngine.buildCostIndex(snapshot.production), storeKey, date)
        val totalCost = if (normType in setOf("CASH", "CREDIT")) InputGuards.round2(quantityKg * costLookup.cp) else 0.0
        val profit = if (normType in setOf("CASH", "CREDIT")) InputGuards.round2(totalVal - totalCost) else 0.0
        val cleanNotes = InputGuards.sanitizeNotes(notes)
        val iso = nowIso()

        val diff = FinanceEngine.buildEditDiff(
            listOf("date" to "Date", "customerName" to "Customer", "paymentType" to "Type", "quantity" to "Quantity", "unitPrice" to "Rate", "totalValue" to "Total Value", "notes" to "Notes"),
            mapOf("date" to original.date, "customerName" to original.customerName, "paymentType" to original.paymentType, "quantity" to original.quantity, "unitPrice" to original.unitPrice, "totalValue" to original.totalValue, "notes" to original.notes),
            mapOf("date" to date, "customerName" to cleanCust, "paymentType" to normType, "quantity" to InputGuards.round2(quantityKg), "unitPrice" to InputGuards.round2(unitPrice), "totalValue" to totalVal, "notes" to cleanNotes)
        )
        val updated = original.copy(
            date = date,
            supplyDate = date,
            customerName = cleanCust,
            supplyStore = storeKey,
            paymentType = normType,
            quantity = if (normType in setOf("CASH", "CREDIT")) InputGuards.round2(quantityKg) else 0.0,
            unitPrice = InputGuards.round2(unitPrice),
            totalValue = totalVal,
            totalCost = totalCost,
            profit = profit,
            costSource = if (normType in setOf("CASH", "CREDIT")) costLookup.source else "",
            creditReceived = normType == "CASH" || normType == "COLLECTION",
            isCollection = normType == "COLLECTION",
            collectedAmount = if (normType == "COLLECTION" || normType == "CASH") totalVal else 0.0,
            expiredQty = InputGuards.round2(expiredKg),
            isRepReturn = normType == "REP_RETURN",
            returnedQty = if (normType == "REP_RETURN") InputGuards.round2(quantityKg) else 0.0,
            returnValue = if (normType == "REP_RETURN") totalVal else 0.0,
            notes = cleanNotes,
            updatedAtEpoch = System.currentTimeMillis(),
            isEdited = true,
            editedAt = iso,
            editHistory = FinanceEngine.pushEditHistory(original.editHistory, actorName(snapshot), diff, iso)
        )
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "rep",
            docId = original.id,
            summaryTitle = "Edit Rep $normType: $cleanCust",
            summaryDetail = "$date • ${InputGuards.fmtNum(totalVal)}",
            payload = WorkspaceJsonCodec.saleToMap(updated),
            isEdit = true
        )
    }

    // --- Factory Batch & Raw Material / Purchase Lot Edits ---

    suspend fun editFactoryBatch(
        snapshot: WorkspaceSnapshot,
        original: FactoryRecord,
        date: String,
        formula: FormulaConfig,
        unitsToProduce: Double,
        notes: String
    ): Result<String> {
        if (original.isMerged) return Result.failure(IllegalArgumentException("Merged batches cannot be edited"))
        if (unitsToProduce <= 0.0) return Result.failure(IllegalArgumentException("Units must be greater than 0"))

        val pools = FormulaEngine.buildUnitPools(snapshot.stores, snapshot.formulas, snapshot.factory, snapshot.production)
        val origPool = pools.firstOrNull { it.key == original.formulaId || it.formulaId == original.formulaId }
        val deltaUnits = if (formula.id == original.formulaId) unitsToProduce - original.units else -original.units
        if (origPool != null && origPool.availableUnits + deltaUnits < -0.001) {
            return Result.failure(IllegalArgumentException("Part of this batch's units are already used in production entries. Reduce or delete those first."))
        }

        val workingInv = FinanceEngine.materialRecords(snapshot.inventory).associateBy { it.id }.toMutableMap()
        // Restore old materials first
        val toRestore = original.materialsUsed.ifEmpty { original.rawMaterialsUsed }
        for (m in toRestore) {
            val mid = m["id"]?.toString().orEmpty()
            val mname = m["name"]?.toString().orEmpty()
            val qty = (m["qty"] as? Number)?.toDouble() ?: (m["quantity"] as? Number)?.toDouble() ?: 0.0
            if (qty <= 0.0) continue
            val inv = workingInv[mid] ?: workingInv.values.firstOrNull { it.name.equals(mname, ignoreCase = true) } ?: continue
            val nextQty = InputGuards.round6(inv.quantity + qty)
            workingInv[inv.id] = inv.copy(
                quantity = nextQty,
                totalValue = InputGuards.round2(nextQty * inv.cost)
            )
        }

        // Deduct new materials
        val desc = FormulaEngine.describeFormula(formula, snapshot.formulas, workingInv.values.toList())
        if (desc.problems.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Formula has linking issues: ${desc.problems.first().name}"))
        }
        val usedList = mutableListOf<Map<String, Any>>()
        val touchedIds = mutableSetOf<String>()
        toRestore.forEach { m ->
            val mid = m["id"]?.toString().orEmpty()
            workingInv[mid]?.let { touchedIds.add(it.id) }
        }
        for (ing in desc.items) {
            val need = InputGuards.round4(ing.quantity * unitsToProduce)
            if (need <= 0.0) continue
            val inv = workingInv[ing.id]
                ?: workingInv.values.firstOrNull { it.name.equals(ing.name.trim(), ignoreCase = true) }
                ?: return Result.failure(IllegalArgumentException("Missing raw material: ${ing.name}"))
            if (inv.quantity + 0.001 < need) {
                return Result.failure(IllegalArgumentException("Insufficient ${inv.name}: need ${InputGuards.fmtNum(need)} kg, have ${InputGuards.fmtNum(inv.quantity)} kg"))
            }
            val nextQty = InputGuards.round6(max(0.0, inv.quantity - need))
            workingInv[inv.id] = inv.copy(
                quantity = nextQty,
                totalValue = InputGuards.round2(nextQty * inv.cost)
            )
            touchedIds.add(inv.id)
            usedList.add(mapOf("id" to inv.id, "name" to inv.name, "qty" to need, "cost" to InputGuards.round2(need * inv.cost)))
        }

        val now = System.currentTimeMillis()
        if (snapshot.profile.isAdmin) {
            touchedIds.forEach { invId ->
                val inv = workingInv[invId] ?: return@forEach
                ops.submitOrExecuteWrite(
                    profile = snapshot.profile,
                    targetCollection = "inventory",
                    docId = inv.id,
                    summaryTitle = "Adjust ${inv.name}",
                    summaryDetail = "Batch edit (${formula.name})",
                    payload = WorkspaceJsonCodec.inventoryToMap(inv.copy(timestamp = now)),
                    isEdit = true
                )
            }
        }

        val materialsCost = InputGuards.round2(desc.raw * unitsToProduce)
        val extraCost = InputGuards.round2(desc.additionalCost * unitsToProduce)
        val totalCost = InputGuards.round2(materialsCost + extraCost)
        val storeKey = snapshot.stores.firstOrNull { it.formulaId == formula.id }?.let(dispatcher::resolvedStoreKey) ?: original.store
        val cleanNotes = InputGuards.sanitizeNotes(notes)
        val diff = FinanceEngine.buildEditDiff(
            listOf("date" to "Date", "formulaName" to "Formula", "units" to "Units", "totalCost" to "Total Cost", "notes" to "Notes"),
            mapOf("date" to original.date, "formulaName" to original.formulaName, "units" to original.units, "totalCost" to original.totalCost, "notes" to original.notes),
            mapOf("date" to date, "formulaName" to formula.name, "units" to InputGuards.round2(unitsToProduce), "totalCost" to totalCost, "notes" to cleanNotes)
        )
        val iso = nowIso()
        val updated = original.copy(
            date = date,
            store = storeKey,
            formulaId = formula.id,
            formulaName = formula.name,
            units = InputGuards.round2(unitsToProduce),
            totalCost = totalCost,
            materialsCost = materialsCost,
            additionalCost = extraCost,
            rawMaterialsUsed = usedList,
            materialsUsed = usedList,
            notes = cleanNotes,
            updatedAtEpoch = now,
            isEdited = true,
            editedAt = iso,
            editHistory = FinanceEngine.pushEditHistory(original.editHistory, actorName(snapshot), diff, iso)
        )
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "factory",
            docId = original.id,
            summaryTitle = "Edit Batch: ${formula.name}",
            summaryDetail = "$date • ${InputGuards.fmtNum(unitsToProduce)} units",
            payload = WorkspaceJsonCodec.factoryToMap(updated),
            isEdit = true
        )
    }

    suspend fun editPurchaseLot(
        snapshot: WorkspaceSnapshot,
        lot: InventoryRecord,
        date: String,
        unitName: String,
        conversionFactor: Double,
        qtyUnits: Double,
        costPerUnit: Double,
        notes: String
    ): Result<String> {
        if (!lot.isPurchaseLot) return Result.failure(IllegalArgumentException("Record is not a purchase lot"))
        if (qtyUnits <= 0.0 || conversionFactor <= 0.0 || costPerUnit <= 0.0) {
            return Result.failure(IllegalArgumentException("Quantity, factor, and cost must be greater than 0"))
        }
        val parentMat = snapshot.inventory.firstOrNull {
            it.deletedAt == null && !it.isPurchaseLot && it.id == lot.materialRefId
        } ?: return Result.failure(IllegalArgumentException("Parent raw material not found"))

        val newKg = InputGuards.round6(qtyUnits * conversionFactor)
        val oldKg = InputGuards.round6(lot.purchaseKg)
        val deltaKg = InputGuards.round6(newKg - oldKg)
        val newAmount = InputGuards.round2(qtyUnits * costPerUnit)
        val oldAmount = InputGuards.round2(lot.totalValue)
        val deltaAmount = InputGuards.round2(newAmount - oldAmount)
        val nextMatQty = InputGuards.round6(parentMat.quantity + deltaKg)
        if (nextMatQty < -1e-6) {
            return Result.failure(IllegalArgumentException("${InputGuards.formatKg(-nextMatQty)} more of this purchase was already used in production than the new weight allows."))
        }
        val safeQty = max(0.0, nextMatQty)
        val stockVal = (parentMat.quantity * parentMat.cost) + deltaAmount
        val costPerKg = InputGuards.round4(costPerUnit / conversionFactor)
        val nextAvgCost = if (safeQty > 1e-9) InputGuards.round4(max(0.0, stockVal / safeQty)) else costPerKg
        val now = System.currentTimeMillis()
        val iso = nowIso()

        val updatedMat = parentMat.copy(
            quantity = safeQty,
            cost = nextAvgCost,
            totalValue = InputGuards.round2(safeQty * nextAvgCost),
            collectedKg = max(0.0, InputGuards.round6(FinanceEngine.collectedKg(parentMat) + deltaKg)),
            conversionFactor = conversionFactor,
            purchaseUnitName = unitName.trim(),
            purchaseCost = costPerUnit,
            purchaseQuantity = if (conversionFactor != 1.0) InputGuards.round4(safeQty / conversionFactor) else safeQty,
            updatedAtEpoch = now
        )
        ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "inventory",
            docId = parentMat.id,
            summaryTitle = "Adjust ${parentMat.name}",
            summaryDetail = "Purchase lot edit",
            payload = WorkspaceJsonCodec.inventoryToMap(updatedMat),
            isEdit = true
        )

        val cleanNotes = InputGuards.sanitizeNotes(notes)
        val paidAlready = max(0.0, oldAmount - lot.totalPayable)
        val nextPayable = if (lot.supplierId.isNotBlank()) InputGuards.round2(max(0.0, newAmount - paidAlready)) else 0.0
        val diff = FinanceEngine.buildEditDiff(
            listOf("purchaseDate" to "Date", "purchaseQuantity" to "Units", "purchaseCost" to "Cost/Unit", "purchaseKg" to "Weight kg", "totalValue" to "Total", "notes" to "Notes"),
            mapOf("purchaseDate" to lot.purchaseDate, "purchaseQuantity" to lot.purchaseQuantity, "purchaseCost" to lot.purchaseCost, "purchaseKg" to lot.purchaseKg, "totalValue" to lot.totalValue, "notes" to lot.notes),
            mapOf("purchaseDate" to date, "purchaseQuantity" to qtyUnits, "purchaseCost" to costPerUnit, "purchaseKg" to newKg, "totalValue" to newAmount, "notes" to cleanNotes)
        )
        val updatedLot = lot.copy(
            name = "${parentMat.name} · $date",
            cost = costPerKg,
            totalValue = newAmount,
            conversionFactor = conversionFactor,
            purchaseUnitName = unitName.trim(),
            purchaseCost = costPerUnit,
            purchaseQuantity = qtyUnits,
            purchaseKg = newKg,
            purchaseDate = date,
            totalPayable = nextPayable,
            paymentStatus = if (lot.supplierId.isBlank() || nextPayable <= 0.01) "paid" else "pending",
            paidDate = if (nextPayable <= 0.01) date else "",
            notes = cleanNotes,
            updatedAtEpoch = now,
            isEdited = true,
            editedAt = iso,
            editHistory = FinanceEngine.pushEditHistory(lot.editHistory, actorName(snapshot), diff, iso)
        )
        val res = ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "inventory",
            docId = lot.id,
            summaryTitle = "Edit Purchase: ${parentMat.name}",
            summaryDetail = "$date • ${InputGuards.formatKg(newKg)}",
            payload = WorkspaceJsonCodec.inventoryToMap(updatedLot),
            isEdit = true
        )
        if (res.isSuccess) {
            val nextInv = snapshot.inventory.map {
                when (it.id) {
                    parentMat.id -> updatedMat
                    lot.id -> updatedLot
                    else -> it
                }
            }
            dispatcher.syncLedgerMirrorToStorage(snapshot.copy(inventory = nextInv))
        }
        return res
    }

    suspend fun deletePurchaseLot(snapshot: WorkspaceSnapshot, lot: InventoryRecord): Result<Unit> {
        if (!lot.isPurchaseLot) return Result.failure(IllegalArgumentException("Not a purchase lot"))
        val parentMat = snapshot.inventory.firstOrNull {
            it.deletedAt == null && !it.isPurchaseLot && it.id == lot.materialRefId
        }
        if (parentMat != null) {
            val nextQty = InputGuards.round6(parentMat.quantity - lot.purchaseKg)
            if (nextQty < -1e-6) {
                return Result.failure(IllegalArgumentException("${InputGuards.formatKg(-nextQty)} of this lot was already used in production; cannot delete it."))
            }
            val safeQty = max(0.0, nextQty)
            val stockVal = (parentMat.quantity * parentMat.cost) - lot.totalValue
            val nextCost = if (safeQty > 1e-9) InputGuards.round4(max(0.0, stockVal / safeQty)) else parentMat.cost
            val updatedMat = parentMat.copy(
                quantity = safeQty,
                cost = nextCost,
                totalValue = InputGuards.round2(safeQty * nextCost),
                collectedKg = max(0.0, InputGuards.round6(FinanceEngine.collectedKg(parentMat) - lot.purchaseKg)),
                updatedAtEpoch = System.currentTimeMillis()
            )
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "inventory",
                docId = parentMat.id,
                summaryTitle = "Remove Lot Stock: ${parentMat.name}",
                summaryDetail = "-${InputGuards.formatKg(lot.purchaseKg)}",
                payload = WorkspaceJsonCodec.inventoryToMap(updatedMat),
                isEdit = true
            )
        }
        return ops.softDeleteToRecycleBin(
            profile = snapshot.profile,
            collection = "inventory",
            docId = lot.id,
            displayName = lot.name,
            displayDetail = "${lot.purchaseDate} • ${InputGuards.formatKg(lot.purchaseKg)}",
            displayAmount = InputGuards.fmtNum(lot.totalValue),
            snapshotMap = WorkspaceJsonCodec.inventoryToMap(lot)
        )
    }

    // --- Payments & Expenses Edit ---

    suspend fun editPaymentTransaction(
        snapshot: WorkspaceSnapshot,
        original: PaymentTransaction,
        date: String,
        entity: EntityRecord?,
        customName: String,
        paymentMethod: String,
        amount: Double,
        description: String
    ): Result<String> {
        if (original.isMerged || original.isTransfer || original.isPayable) {
            return Result.failure(IllegalArgumentException("Transfers and supplier payable invoices cannot be edited here"))
        }
        val cleanName = InputGuards.sanitizeName(entity?.name ?: customName)
        if (cleanName.isBlank() || amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Name and positive amount are required"))
        }
        val cleanDesc = InputGuards.sanitizeNotes(description)
        val cleanMethod = paymentMethod.ifBlank { original.paymentMethod.ifBlank { "Cash" } }
        val now = System.currentTimeMillis()
        val iso = nowIso()

        val diff = FinanceEngine.buildEditDiff(
            listOf("date" to "Date", "entityName" to "Account/Name", "amount" to "Amount", "paymentMethod" to "Channel", "description" to "Note"),
            mapOf("date" to original.date, "entityName" to original.entityName, "amount" to original.amount, "paymentMethod" to original.paymentMethod, "description" to original.description),
            mapOf("date" to date, "entityName" to cleanName, "amount" to InputGuards.round2(amount), "paymentMethod" to cleanMethod, "description" to cleanDesc)
        )
        val updatedTx = original.copy(
            date = date,
            entityId = entity?.id ?: original.entityId,
            entityName = cleanName,
            amount = InputGuards.round2(amount),
            paymentMethod = cleanMethod,
            description = cleanDesc,
            updatedAtEpoch = now,
            isEdited = true,
            editedAt = iso,
            editHistory = FinanceEngine.pushEditHistory(original.editHistory, actorName(snapshot), diff, iso)
        )

        // Sync linked expense if any
        val linkedExp = if (original.isExpense || original.expenseId.isNotBlank()) {
            snapshot.expenses.firstOrNull { it.deletedAt == null && (it.id == original.expenseId || it.linkedTxId == original.id) }
        } else null
        if (linkedExp != null) {
            val updatedExp = linkedExp.copy(
                date = date,
                name = cleanName,
                amount = InputGuards.round2(amount),
                description = cleanDesc,
                notes = cleanDesc,
                entityId = updatedTx.entityId,
                entityName = cleanName,
                paymentMethod = cleanMethod,
                updatedAtEpoch = now,
                isEdited = true,
                editedAt = iso,
                editHistory = FinanceEngine.pushEditHistory(linkedExp.editHistory, actorName(snapshot), diff, iso)
            )
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "expenses",
                docId = linkedExp.id,
                summaryTitle = "Edit Expense: $cleanName",
                summaryDetail = "$date • ${InputGuards.fmtNum(amount)}",
                payload = WorkspaceJsonCodec.expenseToMap(updatedExp),
                isEdit = true
            )
        }

        val res = ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "transactions",
            docId = original.id,
            summaryTitle = "Edit ${original.type}: $cleanName",
            summaryDetail = "$date • ${InputGuards.fmtNum(amount)}",
            payload = WorkspaceJsonCodec.transactionToMap(updatedTx),
            isEdit = true
        )
        if (res.isSuccess) {
            val nextTxs = snapshot.transactions.map { if (it.id == original.id) updatedTx else it }
            dispatcher.syncLedgerMirrorToStorage(snapshot.copy(transactions = nextTxs))
        }
        return res
    }

    suspend fun editExpenseRecord(
        snapshot: WorkspaceSnapshot,
        original: ExpenseRecord,
        date: String,
        name: String,
        category: String,
        paymentMethod: String,
        amount: Double,
        notes: String
    ): Result<String> {
        if (original.isMerged) return Result.failure(IllegalArgumentException("Merged records cannot be edited"))
        val linkedTx = snapshot.transactions.firstOrNull {
            it.deletedAt == null && (it.expenseId == original.id || it.id == original.linkedTxId)
        }
        if (linkedTx != null) {
            return editPaymentTransaction(snapshot, linkedTx, date, null, name, paymentMethod, amount, notes)
        }
        val cleanName = InputGuards.sanitizeName(name)
        if (cleanName.isBlank() || amount <= 0.0) {
            return Result.failure(IllegalArgumentException("Expense name and positive amount are required"))
        }
        val cleanCat = category.ifBlank { original.category.ifBlank { "Other" } }
        val cleanNotes = InputGuards.sanitizeNotes(notes)
        val cleanMethod = paymentMethod.ifBlank { original.paymentMethod.ifBlank { "Cash" } }
        val now = System.currentTimeMillis()
        val iso = nowIso()

        val diff = FinanceEngine.buildEditDiff(
            listOf("date" to "Date", "name" to "Name", "category" to "Category", "amount" to "Amount", "paymentMethod" to "Channel", "notes" to "Notes"),
            mapOf("date" to original.date, "name" to original.name, "category" to original.category, "amount" to original.amount, "paymentMethod" to original.paymentMethod, "notes" to original.notes),
            mapOf("date" to date, "name" to cleanName, "category" to cleanCat, "amount" to InputGuards.round2(amount), "paymentMethod" to cleanMethod, "notes" to cleanNotes)
        )
        val updated = original.copy(
            date = date,
            name = cleanName,
            category = cleanCat,
            amount = InputGuards.round2(amount),
            description = cleanNotes,
            notes = cleanNotes,
            paymentMethod = cleanMethod,
            updatedAtEpoch = now,
            isEdited = true,
            editedAt = iso,
            editHistory = FinanceEngine.pushEditHistory(original.editHistory, actorName(snapshot), diff, iso)
        )
        val res = ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "expenses",
            docId = original.id,
            summaryTitle = "Edit Expense: $cleanName",
            summaryDetail = "$date • ${InputGuards.fmtNum(amount)}",
            payload = WorkspaceJsonCodec.expenseToMap(updated),
            isEdit = true
        )
        if (res.isSuccess) {
            val nextExps = snapshot.expenses.map { if (it.id == original.id) updated else it }
            dispatcher.syncLedgerMirrorToStorage(snapshot.copy(expenses = nextExps))
        }
        return res
    }

    // --- Entity & Customer/Client Extended Edits (Rename Cascade + Photos + Ledger Page) ---

    suspend fun saveEntityExtended(
        snapshot: WorkspaceSnapshot,
        existingId: String?,
        name: String,
        type: String,
        phone: String,
        wallet: String,
        ledgerPage: String,
        openingBalance: Double,
        notes: String,
        photoDataUri: String?,
        clearPhoto: Boolean = false
    ): Result<String> {
        val clean = InputGuards.sanitizeName(name)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Account name required"))
        val existing = existingId?.let { id -> snapshot.entities.firstOrNull { it.id == id } }
        val id = existing?.id ?: ops.newId("ent")
        val now = System.currentTimeMillis()
        val iso = nowIso()

        val diff = if (existing != null) {
            FinanceEngine.buildEditDiff(
                listOf("name" to "Name", "type" to "Type", "phone" to "Phone", "wallet" to "Wallet", "ledgerPage" to "Ledger Page", "openingBalance" to "Opening Bal", "notes" to "Notes"),
                mapOf("name" to existing.name, "type" to existing.type, "phone" to existing.phone, "wallet" to existing.wallet, "ledgerPage" to existing.ledgerPage, "openingBalance" to existing.openingBalance, "notes" to existing.notes),
                mapOf("name" to clean, "type" to type, "phone" to phone.take(32), "wallet" to wallet.take(60), "ledgerPage" to ledgerPage.take(40), "openingBalance" to InputGuards.round2(openingBalance), "notes" to InputGuards.sanitizeNotes(notes))
            )
        } else emptyList()

        val ent = EntityRecord(
            id = id,
            name = clean,
            type = type,
            contact = phone.take(32),
            phone = phone.take(32),
            wallet = wallet.trim().take(60),
            ledgerPage = ledgerPage.trim().take(40),
            isSupplier = type == "supplier",
            isExpenseEntity = type == "expense",
            openingBalance = InputGuards.round2(openingBalance),
            notes = InputGuards.sanitizeNotes(notes),
            timestamp = existing?.timestamp?.takeIf { it > 0L } ?: now,
            updatedAtEpoch = now,
            isEdited = existing != null && (existing.isEdited || diff.isNotEmpty()),
            editedAt = if (existing != null && diff.isNotEmpty()) iso else (existing?.editedAt ?: ""),
            editHistory = if (existing != null) FinanceEngine.pushEditHistory(existing.editHistory, actorName(snapshot), diff, iso) else emptyList()
        )
        val res = ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "entities",
            docId = id,
            summaryTitle = "${if (existing != null) "Edit" else "New"} Account: $clean",
            summaryDetail = type,
            payload = WorkspaceJsonCodec.entityToMap(ent),
            isEdit = existing != null
        )

        if (res.isSuccess) {
            if (clearPhoto) {
                ops.savePhoto(snapshot.profile, "entity:$id", null)
            } else if (!photoDataUri.isNullOrBlank()) {
                ops.savePhoto(snapshot.profile, "entity:$id", photoDataUri)
            }
            // Cascade rename across transactions & supplier inventory lots
            if (existing != null && !existing.name.equals(clean, ignoreCase = false) && snapshot.profile.isAdmin) {
                snapshot.transactions.filter { it.deletedAt == null && it.entityId == id }.forEach { t ->
                    ops.submitOrExecuteWrite(
                        profile = snapshot.profile,
                        targetCollection = "transactions",
                        docId = t.id,
                        summaryTitle = "Rename Cascade: $clean",
                        summaryDetail = t.date,
                        payload = WorkspaceJsonCodec.transactionToMap(t.copy(entityName = clean)),
                        isEdit = true
                    )
                }
                snapshot.inventory.filter { it.deletedAt == null && it.supplierId == id }.forEach { inv ->
                    ops.submitOrExecuteWrite(
                        profile = snapshot.profile,
                        targetCollection = "inventory",
                        docId = inv.id,
                        summaryTitle = "Supplier Rename: $clean",
                        summaryDetail = inv.name,
                        payload = WorkspaceJsonCodec.inventoryToMap(inv.copy(supplierName = clean)),
                        isEdit = true
                    )
                }
                val nextEntities = snapshot.entities.map { if (it.id == id) ent else it }
                dispatcher.syncLedgerMirrorToStorage(snapshot.copy(entities = nextEntities))
            }
        }
        return res
    }

    suspend fun saveCustomerOrClientExtended(
        snapshot: WorkspaceSnapshot,
        existing: CustomerRecord?,
        isRepClient: Boolean,
        name: String,
        phone: String,
        address: String,
        oldDebit: Double,
        customSalePrice: Double,
        salesRep: String,
        ledgerPage: String,
        notes: String,
        gpsLat: Double?,
        gpsLng: Double?,
        photoDataUri: String?,
        clearPhoto: Boolean = false
    ): Result<String> {
        val clean = InputGuards.sanitizeName(name)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Customer name required"))
        val col = if (isRepClient) "clients" else "customers"
        val now = System.currentTimeMillis()
        val iso = nowIso()
        val id = existing?.id?.takeIf { it.isNotBlank() } ?: ops.newId(if (isRepClient) "client" else "cust")

        val diff = if (existing != null) {
            FinanceEngine.buildEditDiff(
                listOf("name" to "Name", "phone" to "Phone", "address" to "Address", "oldDebit" to "Opening Debit", "customSalePrice" to "Custom Price", "ledgerPage" to "Ledger Page", "notes" to "Notes"),
                mapOf("name" to existing.name, "phone" to existing.phone, "address" to existing.address, "oldDebit" to existing.oldDebit, "customSalePrice" to existing.customSalePrice, "ledgerPage" to existing.ledgerPage, "notes" to existing.notes),
                mapOf("name" to clean, "phone" to InputGuards.sanitizePhone(phone), "address" to address.trim().take(200), "oldDebit" to InputGuards.round2(oldDebit.coerceAtLeast(0.0)), "customSalePrice" to InputGuards.round2(customSalePrice.coerceAtLeast(0.0)), "ledgerPage" to ledgerPage.trim().take(40), "notes" to InputGuards.sanitizeNotes(notes))
            )
        } else emptyList()

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
            ledgerPage = ledgerPage.trim().take(40),
            notes = InputGuards.sanitizeNotes(notes),
            createdAt = existing?.createdAt?.takeIf { it > 0L } ?: now,
            updatedAt = now,
            timestamp = existing?.timestamp?.takeIf { it > 0L } ?: now,
            isEdited = existing != null && (existing.isEdited || diff.isNotEmpty()),
            editedAt = if (existing != null && diff.isNotEmpty()) iso else (existing?.editedAt ?: ""),
            editHistory = if (existing != null) FinanceEngine.pushEditHistory(existing.editHistory, actorName(snapshot), diff, iso) else emptyList()
        )
        val res = ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = col,
            docId = id,
            summaryTitle = "${if (existing != null) "Edit" else "New"} ${if (isRepClient) "Rep Customer" else "Customer"}: $clean",
            summaryDetail = phone.ifBlank { address },
            payload = WorkspaceJsonCodec.customerToMap(rec),
            isEdit = existing != null
        )
        if (res.isSuccess) {
            val photoKey = "${if (isRepClient) "client" else "cust"}:$id"
            if (clearPhoto) {
                ops.savePhoto(snapshot.profile, photoKey, null)
            } else if (!photoDataUri.isNullOrBlank()) {
                ops.savePhoto(snapshot.profile, photoKey, photoDataUri)
            }
        }
        return res
    }

    // --- Delete Single Edit History Entry ---

    suspend fun deleteEditHistoryAt(
        snapshot: WorkspaceSnapshot,
        collection: String,
        docId: String,
        index: Int
    ): Result<String> {
        val nextMap: Map<String, Any>? = when (collection) {
            "production" -> snapshot.production.firstOrNull { it.id == docId }?.let {
                WorkspaceJsonCodec.productionToMap(it.copy(editHistory = FinanceEngine.deleteEditHistoryAt(it.editHistory, index)))
            }
            "sales" -> snapshot.sales.firstOrNull { it.id == docId }?.let {
                WorkspaceJsonCodec.saleToMap(it.copy(editHistory = FinanceEngine.deleteEditHistoryAt(it.editHistory, index)))
            }
            "rep" -> snapshot.repRecords.firstOrNull { it.id == docId }?.let {
                WorkspaceJsonCodec.saleToMap(it.copy(editHistory = FinanceEngine.deleteEditHistoryAt(it.editHistory, index)))
            }
            "calculator" -> snapshot.calculator.firstOrNull { it.id == docId }?.let {
                WorkspaceJsonCodec.calculatorToMap(it.copy(editHistory = FinanceEngine.deleteEditHistoryAt(it.editHistory, index)))
            }
            "factory" -> snapshot.factory.firstOrNull { it.id == docId }?.let {
                WorkspaceJsonCodec.factoryToMap(it.copy(editHistory = FinanceEngine.deleteEditHistoryAt(it.editHistory, index)))
            }
            "inventory" -> snapshot.inventory.firstOrNull { it.id == docId }?.let {
                WorkspaceJsonCodec.inventoryToMap(it.copy(editHistory = FinanceEngine.deleteEditHistoryAt(it.editHistory, index)))
            }
            "transactions" -> snapshot.transactions.firstOrNull { it.id == docId }?.let {
                WorkspaceJsonCodec.transactionToMap(it.copy(editHistory = FinanceEngine.deleteEditHistoryAt(it.editHistory, index)))
            }
            "expenses" -> snapshot.expenses.firstOrNull { it.id == docId }?.let {
                WorkspaceJsonCodec.expenseToMap(it.copy(editHistory = FinanceEngine.deleteEditHistoryAt(it.editHistory, index)))
            }
            "entities" -> snapshot.entities.firstOrNull { it.id == docId }?.let {
                WorkspaceJsonCodec.entityToMap(it.copy(editHistory = FinanceEngine.deleteEditHistoryAt(it.editHistory, index)))
            }
            "customers" -> snapshot.customers.firstOrNull { it.id == docId }?.let {
                WorkspaceJsonCodec.customerToMap(it.copy(editHistory = FinanceEngine.deleteEditHistoryAt(it.editHistory, index)))
            }
            "clients" -> snapshot.clients.firstOrNull { it.id == docId }?.let {
                WorkspaceJsonCodec.customerToMap(it.copy(editHistory = FinanceEngine.deleteEditHistoryAt(it.editHistory, index)))
            }
            else -> null
        }
        val payload = nextMap ?: return Result.failure(IllegalArgumentException("Record not found"))
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = collection,
            docId = docId,
            summaryTitle = "Trim Edit History ($collection)",
            summaryDetail = docId,
            payload = payload,
            isEdit = true
        )
    }
}
