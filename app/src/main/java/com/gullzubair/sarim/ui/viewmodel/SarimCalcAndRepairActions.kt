package com.gullzubair.sarim.ui.viewmodel

import com.gullzubair.sarim.data.firebase.AppRepository
import com.gullzubair.sarim.data.firebase.WorkspaceOperations
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.CalculatorRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.OverviewFixEngine
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Calculator Quick/Full edit with full side-effect reversal,
 * Overview interactive fix & link repair, and audit record deletion.
 */
class SarimCalcAndRepairActions(
    private val repo: AppRepository,
    private val ops: WorkspaceOperations,
    private val dispatcher: SarimActionDispatcher
) {
    private fun actorName(snapshot: WorkspaceSnapshot): String =
        snapshot.profile.displayName.ifBlank { snapshot.profile.email.ifBlank { "Admin" } }

    fun getCalcEditBlockReason(snapshot: WorkspaceSnapshot, entry: CalculatorRecord): String {
        if (entry.isMerged) return "Merged opening balance records cannot be edited."
        if (entry.transferSaleId.isNotBlank()) {
            val tr = snapshot.sales.firstOrNull { it.deletedAt == null && it.id == entry.transferSaleId }
            if (tr != null) {
                val settledByCalc = snapshot.calculator.any {
                    it.deletedAt == null && it.id != entry.id && entry.transferSaleId in it.linkedSalesIds
                }
                val hasPayments = tr.creditReceived || tr.partialPaymentReceived > 0.005 || tr.collectionAllocs.isNotEmpty()
                if (settledByCalc || hasPayments) {
                    return "The transferred stock from this entry has already been collected or settled. Undo those payments first."
                }
            }
        }
        if (entry.expired > 0.0 && entry.expiredApplied > 0.0) {
            val chora = FinanceEngine.findMaterialByName(snapshot.inventory, "CHORA")
            if (chora != null && chora.quantity + 0.001 < entry.expiredApplied) {
                return "Only ${InputGuards.formatKg(chora.quantity)} of CHORA remains; cannot reverse ${InputGuards.formatKg(entry.expiredApplied)} expired stock."
            }
        }
        if (entry.returned > 0.0 && entry.returnStore.isNotBlank()) {
            val stock = FinanceEngine.computeStoreStockSnapshots(snapshot)
                .firstOrNull { it.storeKey.equals(entry.returnStore, ignoreCase = true) }
            if (stock != null && stock.availableKg - entry.returned < -0.001) {
                return "${stock.storeName} stock was already sold. Reversing the ${InputGuards.formatKg(entry.returned)} return would make stock negative."
            }
        }
        return ""
    }

    suspend fun editCalculatorSettlement(
        snapshot: WorkspaceSnapshot,
        original: CalculatorRecord,
        date: String,
        dateFrom: String,
        seller: String,
        store: StoreConfig,
        totalSold: Double,
        returned: Double,
        returnType: String,
        returnTargetRep: String,
        expired: Double,
        shared: Double,
        creditQty: Double,
        unitPriceOverride: Double,
        fieldExpenseItems: List<Map<String, Any>>,
        commissionPerUnit: Double,
        commissionItems: List<Map<String, Any>>,
        prevColl: Double,
        received: Double,
        notes: String
    ): Result<String> {
        val effDateFrom = dateFrom.ifBlank { date }
        if (totalSold <= 0.0) return Result.failure(IllegalArgumentException("Enter valid units sold (> 0)"))
        if (returned < 0.0 || expired < 0.0 || shared < 0.0 || creditQty < 0.0 || prevColl < 0.0 || received < 0.0) {
            return Result.failure(IllegalArgumentException("Quantities and cash amounts cannot be negative"))
        }
        if (returned + expired + shared > totalSold + 0.0001) {
            return Result.failure(IllegalArgumentException("Combined returned + expired + shared cannot exceed total sold"))
        }
        if (commissionPerUnit < 0.0 || commissionPerUnit > 100_000.0) {
            return Result.failure(IllegalArgumentException("Commission per unit must be between 0 and 100,000"))
        }

        val storeKey = dispatcher.resolvedStoreKey(store)
        val normRetType = returnType.trim().lowercase(Locale.ROOT)
        val isRepTransferReturn = returned > 0.0 && (normRetType == "rep" || normRetType == "transfer") &&
            returnTargetRep.isNotBlank() && !returnTargetRep.equals(seller, ignoreCase = true)
        val isStoreReturn = returned > 0.0 && !isRepTransferReturn
        val desiredRetStore = if (isStoreReturn) storeKey else ""
        val desiredRetRep = if (isRepTransferReturn) returnTargetRep else ""

        val isFullEdit = !seller.equals(original.seller, ignoreCase = false) ||
            date != original.date ||
            InputGuards.round2(totalSold) != InputGuards.round2(original.totalSold) ||
            InputGuards.round2(returned) != InputGuards.round2(original.returned) ||
            InputGuards.round2(expired) != InputGuards.round2(original.expired) ||
            (returned > 0.0 && (desiredRetStore != original.returnStore || desiredRetRep != original.returnRep))

        val isQuickEdit = !isFullEdit
        val unitPrice = if (isQuickEdit && original.unitPrice > 0.0) original.unitPrice else unitPriceOverride
        if (unitPrice <= 0.0) return Result.failure(IllegalArgumentException("Set a sale price for this store first"))

        val netSold = InputGuards.round2(max(0.0, totalSold - returned - expired - shared))
        if (creditQty > netSold + 0.0001) {
            return Result.failure(IllegalArgumentException("Credit sales cannot exceed net sold quantity"))
        }

        val costPerKg = if (isQuickEdit) {
            original.costPrice
        } else {
            FormulaEngine.costSourceFromIndex(FormulaEngine.buildCostIndex(snapshot.production), storeKey, date).cp
        }
        val fieldExpensesTotal = InputGuards.round2(fieldExpenseItems.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 })
        val commissionPaid = InputGuards.round2(commissionItems.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 })
        val cashQty = InputGuards.round2(max(0.0, netSold - creditQty))
        val creditValue = InputGuards.round2(creditQty * unitPrice)
        val revenue = InputGuards.round2(netSold * unitPrice)
        val totalCost = InputGuards.round2(netSold * costPerKg)
        val profit = InputGuards.round2(revenue - totalCost)
        val expected = InputGuards.round2((cashQty * unitPrice) + prevColl - fieldExpensesTotal - commissionPaid)
        val diffCash = InputGuards.round2(received - expected)
        val grossCommission = InputGuards.round2(commissionPerUnit * netSold)
        val commissionPayable = InputGuards.round2(max(0.0, grossCommission - commissionPaid))
        val statusText = when {
            abs(diffCash) <= 0.01 -> "PERFECT MATCH"
            diffCash < 0.0 -> "SHORT: ${InputGuards.fmtNum(abs(diffCash))}"
            else -> "OVER: ${InputGuards.fmtNum(diffCash)}"
        }
        val statusClass = if (diffCash < -0.01) "bad" else "good"
        val cleanNotes = InputGuards.sanitizeNotes(notes)

        val diff = FinanceEngine.buildEditDiff(
            listOf(
                "date" to "Date",
                "seller" to "Seller",
                "totalSold" to "Total Sold",
                "returned" to "Returned",
                "expired" to "Expired",
                "shared" to "Shared",
                "creditQty" to "Credit Qty",
                "prevColl" to "Prev Credit Rec",
                "fieldExpenses" to "Field Expenses",
                "commissionPerUnit" to "Comm/kg",
                "commissionPaid" to "Comm Paid",
                "received" to "Cash Received",
                "notes" to "Notes"
            ),
            mapOf(
                "date" to original.date,
                "seller" to original.seller,
                "totalSold" to original.totalSold,
                "returned" to original.returned,
                "expired" to original.expired,
                "shared" to original.shared,
                "creditQty" to original.creditQty,
                "prevColl" to original.prevColl,
                "fieldExpenses" to original.fieldExpenses,
                "commissionPerUnit" to original.commissionPerUnit,
                "commissionPaid" to original.commissionPaid,
                "received" to original.received,
                "notes" to original.notes
            ),
            mapOf(
                "date" to date,
                "seller" to seller,
                "totalSold" to InputGuards.round2(totalSold),
                "returned" to InputGuards.round2(returned),
                "expired" to InputGuards.round2(expired),
                "shared" to InputGuards.round2(shared),
                "creditQty" to InputGuards.round2(creditQty),
                "prevColl" to InputGuards.round2(prevColl),
                "fieldExpenses" to fieldExpensesTotal,
                "commissionPerUnit" to InputGuards.round2(commissionPerUnit),
                "commissionPaid" to commissionPaid,
                "received" to InputGuards.round2(received),
                "notes" to cleanNotes
            )
        )
        val iso = Instant.now().toString()
        val now = System.currentTimeMillis()
        val nextHistory = FinanceEngine.pushEditHistory(original.editHistory, actorName(snapshot), diff, iso)

        if (isQuickEdit) {
            val updated = original.copy(
                dateFrom = effDateFrom,
                totalSold = InputGuards.round2(totalSold),
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
                revenue = revenue,
                profit = profit,
                totalCost = totalCost,
                totalExpected = expected,
                received = InputGuards.round2(received),
                statusText = statusText,
                statusClass = statusClass,
                notes = cleanNotes,
                updatedAtEpoch = now,
                isEdited = true,
                editedAt = iso,
                editHistory = nextHistory
            )
            val res = ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "calculator",
                docId = original.id,
                summaryTitle = "Quick Edit Calc: $seller",
                summaryDetail = "$date • Expected ${InputGuards.fmtNum(expected)} • Rec ${InputGuards.fmtNum(received)}",
                payload = WorkspaceJsonCodec.calculatorToMap(updated),
                isEdit = true
            )
            if (res.isSuccess) {
                val nextCalcs = snapshot.calculator.map { if (it.id == original.id) updated else it }
                dispatcher.syncLedgerMirrorToStorage(snapshot.copy(calculator = nextCalcs))
            }
            return res
        }

        // Full Edit path: verify no blocker and reverse side effects before applying new ones
        val blk = getCalcEditBlockReason(snapshot, original)
        if (blk.isNotBlank()) return Result.failure(IllegalArgumentException(blk))

        if (isRepTransferReturn) {
            val repContact = snapshot.customers.firstOrNull {
                it.deletedAt == null && it.name.equals(returnTargetRep, ignoreCase = true)
            }
            if ((repContact?.customSalePrice ?: 0.0) <= 0.0) {
                return Result.failure(IllegalArgumentException("Set a custom sale price for $returnTargetRep before transferring stock"))
            }
        }

        // 1. Revert linked sales
        original.linkedSalesIds.forEach { sid ->
            val s = snapshot.sales.firstOrNull { it.id == sid && it.deletedAt == null } ?: return@forEach
            val reverted = s.copy(
                creditReceived = false,
                paymentType = "CREDIT",
                creditReceivedDate = "",
                creditReceivedTime = "",
                creditReceivedManually = false,
                updatedAtEpoch = now
            )
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "sales",
                docId = s.id,
                summaryTitle = "Revert Calc Link: ${s.customerName}",
                summaryDetail = original.id,
                payload = WorkspaceJsonCodec.saleToMap(reverted),
                isEdit = true
            )
        }
        // 2. Revert linked rep records
        original.linkedRepSalesIds.forEach { rid ->
            val r = snapshot.repRecords.firstOrNull { it.id == rid && it.deletedAt == null } ?: return@forEach
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = "rep",
                docId = r.id,
                summaryTitle = "Unlink Rep Sale: ${r.customerName}",
                summaryDetail = original.id,
                payload = WorkspaceJsonCodec.saleToMap(r.copy(usedInCalcId = "", updatedAtEpoch = now)),
                isEdit = true
            )
        }
        // 3. Revert store return production & log
        if (original.returnEntryId.isNotBlank()) {
            snapshot.production.firstOrNull { it.id == original.returnEntryId && it.deletedAt == null }?.let { prod ->
                ops.softDeleteToRecycleBin(
                    profile = snapshot.profile,
                    collection = "production",
                    docId = prod.id,
                    displayName = "Calc Return Reversed (${prod.store})",
                    displayDetail = prod.date,
                    displayAmount = InputGuards.formatKg(prod.net),
                    snapshotMap = WorkspaceJsonCodec.productionToMap(prod)
                )
            }
        }
        if (original.returnLogId.isNotBlank()) {
            snapshot.returns.firstOrNull { it.id == original.returnLogId && it.deletedAt == null }?.let { retLog ->
                ops.softDeleteToRecycleBin(
                    profile = snapshot.profile,
                    collection = "returns",
                    docId = retLog.id,
                    displayName = "Calc Return Log Reversed",
                    displayDetail = retLog.date,
                    displayAmount = InputGuards.formatKg(retLog.quantity),
                    snapshotMap = WorkspaceJsonCodec.returnToMap(retLog)
                )
            }
        }
        // 4. Revert rep transfer sale
        if (original.transferSaleId.isNotBlank()) {
            snapshot.sales.firstOrNull { it.id == original.transferSaleId && it.deletedAt == null }?.let { tr ->
                ops.softDeleteToRecycleBin(
                    profile = snapshot.profile,
                    collection = "sales",
                    docId = tr.id,
                    displayName = "Calc Transfer Reversed (${tr.customerName})",
                    displayDetail = tr.date,
                    displayAmount = InputGuards.fmtNum(tr.totalValue),
                    snapshotMap = WorkspaceJsonCodec.saleToMap(tr)
                )
            }
        }
        // 5. Reverse CHORA expired delta
        val expDelta = InputGuards.round2(expired - original.expiredApplied)
        if (expDelta != 0.0 && snapshot.profile.isAdmin) {
            dispatcher.addChoraToInventory(snapshot, expDelta)
        }

        // Apply new return/transfer side effects
        var newRetEntryId = ""
        var newRetLogId = ""
        var newTransferSaleId = ""
        if (isStoreReturn) {
            val pair = dispatcher.createCalcStoreReturn(snapshot, date, store, returned, seller)
            newRetEntryId = pair.first
            newRetLogId = pair.second
        } else if (isRepTransferReturn) {
            newTransferSaleId = dispatcher.createCalcRepTransfer(snapshot, returnTargetRep, returned, date, seller)
        }

        // Link new sales/rep/returns for updated seller + date range
        val newLinkedSalesIds = mutableListOf<String>()
        val newLinkedRepSalesIds = mutableListOf<String>()
        val newLinkedRepReturnIds = mutableListOf<String>()
        if (!seller.equals("COMBINED", ignoreCase = true)) {
            val claimedByOthers = snapshot.calculator
                .filter { it.deletedAt == null && it.id != original.id }
                .flatMap { it.linkedSalesIds }
                .toSet()
            val timeNow = DateUtils.nowTimeStr()
            snapshot.sales.filter { s ->
                s.deletedAt == null &&
                    s.id != original.transferSaleId &&
                    (s.currentRepProfile == "admin" || s.currentRepProfile.isBlank()) &&
                    s.customerName.equals(seller, ignoreCase = true) &&
                    ((s.paymentType == "CREDIT" && !s.creditReceived) || s.id in original.linkedSalesIds) &&
                    s.transactionType != "OLD_DEBT" &&
                    s.id !in claimedByOthers &&
                    DateUtils.isInRange(s.supplyDate.ifBlank { s.date }.take(10), effDateFrom, date)
            }.forEach { s ->
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
                    summaryDetail = "$date • Linked to ${original.id}",
                    payload = WorkspaceJsonCodec.saleToMap(patched),
                    isEdit = true
                )
                newLinkedSalesIds.add(s.id)
            }

            snapshot.repRecords.filter { r ->
                r.deletedAt == null &&
                    r.salesRep.equals(seller, ignoreCase = true) &&
                    (r.usedInCalcId.isBlank() || r.usedInCalcId == original.id) &&
                    (r.paymentType == "CREDIT" || r.paymentType == "COLLECTION") &&
                    DateUtils.isInRange(r.date.take(10), effDateFrom, date)
            }.forEach { r ->
                ops.submitOrExecuteWrite(
                    profile = snapshot.profile,
                    targetCollection = "rep",
                    docId = r.id,
                    summaryTitle = "Calc Use Rep Sale: ${r.customerName}",
                    summaryDetail = "$date • ${original.id}",
                    payload = WorkspaceJsonCodec.saleToMap(r.copy(usedInCalcId = original.id)),
                    isEdit = true
                )
                newLinkedRepSalesIds.add(r.id)
            }

            val coveredByOthers = snapshot.calculator
                .filter { it.deletedAt == null && it.id != original.id }
                .flatMap { it.linkedRepReturnIds }
                .toSet()
            var remRet = returned
            var remExp = expired
            snapshot.sales.filter { s ->
                s.deletedAt == null && s.isRepReturn &&
                    s.salesRep.equals(seller, ignoreCase = true) &&
                    s.id !in coveredByOthers &&
                    s.date.take(10) <= date.take(10)
            }.sortedBy { it.timestamp }.forEach { r ->
                if (r.returnedQty <= remRet + 0.001 && r.expiredQty <= remExp + 0.001) {
                    remRet = max(0.0, remRet - r.returnedQty)
                    remExp = max(0.0, remExp - r.expiredQty)
                    newLinkedRepReturnIds.add(r.id)
                }
            }
        }

        val updated = original.copy(
            seller = seller,
            date = date,
            dateFrom = effDateFrom,
            unitPrice = InputGuards.round2(unitPrice),
            costPrice = costPerKg,
            revenue = revenue,
            profit = profit,
            totalCost = totalCost,
            totalSold = InputGuards.round2(totalSold),
            returned = InputGuards.round2(returned),
            returnStore = desiredRetStore,
            returnEntryId = newRetEntryId,
            returnLogId = newRetLogId,
            expiredApplied = if (expired > 0.0) InputGuards.round2(expired) else 0.0,
            returnRep = desiredRetRep,
            returnType = if (isRepTransferReturn) "TRANSFER" else if (isStoreReturn) "RETURN" else "",
            transferSaleId = newTransferSaleId,
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
            linkedSalesIds = newLinkedSalesIds,
            linkedRepSalesIds = newLinkedRepSalesIds,
            linkedRepReturnIds = newLinkedRepReturnIds,
            notes = cleanNotes,
            updatedAtEpoch = now,
            isEdited = true,
            editedAt = iso,
            editHistory = nextHistory
        )
        val res = ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = "calculator",
            docId = original.id,
            summaryTitle = "Edit Calc: $seller",
            summaryDetail = "$date • Expected ${InputGuards.fmtNum(expected)} • Rec ${InputGuards.fmtNum(received)}",
            payload = WorkspaceJsonCodec.calculatorToMap(updated),
            isEdit = true
        )
        if (res.isSuccess) {
            val nextCalcs = snapshot.calculator.map { if (it.id == original.id) updated else it }
            dispatcher.syncLedgerMirrorToStorage(snapshot.copy(calculator = nextCalcs))
        }
        return res
    }

    // --- Link Repair (link-repair.js) ---

    suspend fun repairSalesLinks(
        snapshot: WorkspaceSnapshot,
        kind: String = "sales",
        onlyIds: Set<String>? = null,
        orphanIds: Set<String>? = null
    ): Result<OverviewFixEngine.RepairSummary> {
        if (!snapshot.profile.isAdmin) {
            return Result.failure(IllegalArgumentException("Admin permission required"))
        }
        val records = if (kind == "rep") snapshot.repRecords else snapshot.sales
        val custs = if (kind == "rep") snapshot.clients else snapshot.customers
        val summary = OverviewFixEngine.repairStore(
            records = records,
            kind = kind,
            onlyIds = onlyIds,
            orphanIds = orphanIds,
            customers = custs,
            stores = snapshot.stores
        )
        summary.updatedRecords.values.forEach { rec ->
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                targetCollection = kind,
                docId = rec.id,
                summaryTitle = "Repair Link: ${rec.customerName}",
                summaryDetail = rec.date,
                payload = WorkspaceJsonCodec.saleToMap(rec),
                isEdit = true
            )
        }
        if (kind == "sales" && summary.updatedRecords.isNotEmpty()) {
            val nextSales = snapshot.sales.map { summary.updatedRecords[it.id] ?: it }
            dispatcher.syncLedgerMirrorToStorage(snapshot.copy(sales = nextSales))
        }
        return Result.success(summary)
    }

    // --- Interactive Overview Fix (overview-fix.js) ---

    private fun lookupRecordMap(snapshot: WorkspaceSnapshot, col: String, docId: String): Map<String, Any>? {
        return when (col) {
            "sales" -> snapshot.sales.firstOrNull { it.id == docId }?.let(WorkspaceJsonCodec::saleToMap)
            "rep" -> snapshot.repRecords.firstOrNull { it.id == docId }?.let(WorkspaceJsonCodec::saleToMap)
            "production" -> snapshot.production.firstOrNull { it.id == docId }?.let(WorkspaceJsonCodec::productionToMap)
            "transactions" -> snapshot.transactions.firstOrNull { it.id == docId }?.let(WorkspaceJsonCodec::transactionToMap)
            "expenses" -> snapshot.expenses.firstOrNull { it.id == docId }?.let(WorkspaceJsonCodec::expenseToMap)
            "inventory" -> snapshot.inventory.firstOrNull { it.id == docId }?.let(WorkspaceJsonCodec::inventoryToMap)
            else -> null
        }
    }

    suspend fun applyOverviewFix(
        snapshot: WorkspaceSnapshot,
        col: String,
        docId: String,
        codes: List<String>,
        rawValues: Map<String, Any?>
    ): Result<String> {
        if (!snapshot.profile.isAdmin) {
            return Result.failure(IllegalArgumentException("Admin permission required"))
        }
        if (codes.any { it in OverviewFixEngine.LINK_CODES }) {
            val kind = if (col == "rep") "rep" else "sales"
            val summary = repairSalesLinks(
                snapshot = snapshot,
                kind = kind,
                onlyIds = if ("col_unapplied" in codes) setOf(docId) else null,
                orphanIds = if ("link_orphan" in codes && "col_unapplied" !in codes) setOf(docId) else null
            ).getOrElse { return Result.failure(it) }
            return Result.success(summary.text)
        }

        val origMap = lookupRecordMap(snapshot, col, docId)
            ?: return Result.failure(IllegalArgumentException("Record not found"))
        val today = DateUtils.todayIso()
        val plan = OverviewFixEngine.planFields(col, origMap, codes, today, snapshot)
        val validation = OverviewFixEngine.validateValues(plan.fields, rawValues, today)
        if (!validation.ok) return Result.failure(IllegalArgumentException(validation.message))

        val nextMap = OverviewFixEngine.applyValues(col, origMap, validation.values, snapshot).toMutableMap()
        val iso = Instant.now().toString()
        val now = System.currentTimeMillis()
        nextMap["isEdited"] = true
        nextMap["editedAt"] = iso
        nextMap["updatedAtEpoch"] = now

        val targetId = if (plan.newId) ops.newId(col.take(4)) else docId
        nextMap["id"] = targetId

        val res = ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            targetCollection = col,
            docId = targetId,
            summaryTitle = "Overview Fix ($col)",
            summaryDetail = targetId,
            payload = nextMap,
            isEdit = !plan.newId
        )
        if (res.isSuccess && col in setOf("sales", "transactions", "expenses", "inventory")) {
            val refreshed = when (col) {
                "sales" -> snapshot.copy(sales = snapshot.sales.map { if (it.id == targetId) WorkspaceJsonCodec.mapToSale(targetId, nextMap) else it })
                "transactions" -> snapshot.copy(transactions = snapshot.transactions.map { if (it.id == targetId) WorkspaceJsonCodec.mapToTransaction(targetId, nextMap) else it })
                "expenses" -> snapshot.copy(expenses = snapshot.expenses.map { if (it.id == targetId) WorkspaceJsonCodec.mapToExpense(targetId, nextMap) else it })
                else -> snapshot
            }
            dispatcher.syncLedgerMirrorToStorage(refreshed)
        }
        return res.map { "Record fixed" }
    }

    suspend fun deleteAuditFindingRecord(
        snapshot: WorkspaceSnapshot,
        col: String,
        docId: String
    ): Result<Unit> {
        val rawMap = lookupRecordMap(snapshot, col, docId)
            ?: return Result.failure(IllegalArgumentException("Record not found"))
        val label = rawMap["customerName"]?.toString()?.takeIf { it.isNotBlank() }
            ?: rawMap["entityName"]?.toString()?.takeIf { it.isNotBlank() }
            ?: rawMap["name"]?.toString()?.takeIf { it.isNotBlank() }
            ?: rawMap["store"]?.toString()
            ?: docId
        val date = rawMap["date"]?.toString().orEmpty()
        val res = ops.softDeleteToRecycleBin(
            profile = snapshot.profile,
            collection = col,
            docId = docId,
            displayName = label,
            displayDetail = date,
            displayAmount = "",
            snapshotMap = rawMap
        )
        if (res.isSuccess && col in setOf("sales", "transactions", "expenses", "calculator", "inventory")) {
            val nextSnap = removeDocFromSnapshot(snapshot, col, docId)
            dispatcher.syncLedgerMirrorToStorage(nextSnap)
        }
        return res
    }

    internal fun removeDocFromSnapshot(snapshot: WorkspaceSnapshot, col: String, docId: String): WorkspaceSnapshot =
        when (col) {
            "sales" -> snapshot.copy(sales = snapshot.sales.filterNot { it.id == docId })
            "transactions" -> snapshot.copy(transactions = snapshot.transactions.filterNot { it.id == docId })
            "expenses" -> snapshot.copy(expenses = snapshot.expenses.filterNot { it.id == docId })
            "calculator" -> snapshot.copy(calculator = snapshot.calculator.filterNot { it.id == docId })
            "inventory" -> snapshot.copy(inventory = snapshot.inventory.filterNot { it.id == docId })
            else -> snapshot
        }
}
