package com.gullzubair.sarim.ui.viewmodel

import com.gullzubair.sarim.data.firebase.AppRepository
import com.gullzubair.sarim.data.firebase.WorkspaceOperations
import com.gullzubair.sarim.data.model.CustomerRecord
import com.gullzubair.sarim.data.model.EntityRecord
import com.gullzubair.sarim.data.model.FactoryRecord
import com.gullzubair.sarim.data.model.FormulaConfig
import com.gullzubair.sarim.data.model.InventoryItem
import com.gullzubair.sarim.data.model.PendingApproval
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.WorkspaceSettings
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import java.util.UUID

class SarimActionDispatcher(
    private val repo: AppRepository,
    private val ops: WorkspaceOperations
) {
    private fun memberUids(snapshot: WorkspaceSnapshot): List<String> =
        (snapshot.teamMembers.map { it.uid } + snapshot.profile.workspaceOwnerUid + snapshot.profile.uid)
            .filter { it.isNotBlank() }
            .distinct()
            .take(20)

    suspend fun saveProduction(
        snapshot: WorkspaceSnapshot,
        date: String,
        store: StoreConfig,
        grossWeight: Double,
        containerWeight: Double,
        formulaUnits: Double,
        costPerKg: Double,
        notes: String
    ): Result<String> {
        val net = InputGuards.round3((grossWeight - containerWeight).coerceAtLeast(0.0))
        if (net <= 0.0) return Result.failure(IllegalArgumentException("Net weight must be greater than 0 kg"))
        val resolvedCost = if (costPerKg > 0) costPerKg else FinanceEngine.resolveProductionDayCostPerKg(
            date, store.storeKey, snapshot.production, snapshot.stores, snapshot.formulas, snapshot.inventory
        ).first
        val totalCost = InputGuards.round2(net * resolvedCost)
        val totalRev = InputGuards.round2(net * store.salePrice)
        val profit = InputGuards.round2(totalRev - totalCost)
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = memberUids(snapshot),
            targetCollection = "production",
            docId = null,
            summaryTitle = "Production: ${store.name} (${InputGuards.formatKg(net)})",
            summaryDetail = "$date • Units $formulaUnits • Profit ${InputGuards.formatMoney(profit)}",
            recordDate = date,
            payload = mapOf(
                "date" to date,
                "storeKey" to store.storeKey,
                "storeName" to store.name,
                "formulaId" to store.formulaId,
                "formulaUnits" to InputGuards.round2(formulaUnits),
                "grossWeight" to InputGuards.round3(grossWeight),
                "containerWeight" to InputGuards.round3(containerWeight),
                "netWeight" to net,
                "costPerKg" to InputGuards.round2(resolvedCost),
                "salePrice" to InputGuards.round2(store.salePrice),
                "totalCost" to totalCost,
                "totalRevenue" to totalRev,
                "profit" to profit,
                "transfer" to false,
                "transferType" to "none",
                "notes" to InputGuards.sanitizeNotes(notes),
                "recycled" to false,
                "recycledAtEpoch" to 0L
            )
        )
    }

    suspend fun saveSaleOrCollection(
        snapshot: WorkspaceSnapshot,
        date: String,
        mode: String,
        customerName: String,
        store: StoreConfig?,
        repName: String,
        qtyKg: Double,
        ratePerKg: Double,
        cashReceived: Double,
        expiredKg: Double,
        choraRate: Double,
        addToLedger: Boolean,
        notes: String
    ): Result<String> {
        val cleanCustomer = InputGuards.sanitizeName(customerName)
        if (cleanCustomer.isBlank()) return Result.failure(IllegalArgumentException("Customer name is required"))
        val members = memberUids(snapshot)
        val totalAmount = if (mode == "sale") InputGuards.round2(qtyKg * ratePerKg) else 0.0
        val creditAmount = if (mode == "sale") InputGuards.round2((totalAmount - cashReceived).coerceAtLeast(0.0)) else 0.0
        val choraVal = InputGuards.round2(expiredKg * choraRate)

        val saleId = "sale_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}"
        var ledgerId = ""
        if (addToLedger && cashReceived > 0.0 && (snapshot.profile.role == "admin" || snapshot.profile.uid == snapshot.profile.workspaceOwnerUid)) {
            ledgerId = ops.saveLedgerMirror(
                profile = snapshot.profile,
                memberUids = members,
                date = date,
                entryType = "IN",
                expenseScope = "none",
                channel = "Cash",
                category = if (mode == "sale") "Sales Receipt" else "Credit Collection",
                partyName = cleanCustomer,
                amount = cashReceived,
                sourceCollection = "sales",
                sourceDocId = saleId,
                notes = if (mode == "sale") "Sale ${InputGuards.formatKg(qtyKg)} @ ${InputGuards.round2(ratePerKg)}" else "Customer collection"
            ).getOrDefault("")
        }

        // Ensure CHORA inventory receives expired stock if any
        if (expiredKg > 0.0 && (snapshot.profile.role == "admin" || snapshot.profile.uid == snapshot.profile.workspaceOwnerUid)) {
            val chora = snapshot.inventory.firstOrNull { !it.recycled && it.name.equals("CHORA", true) }
            if (chora != null) {
                val ownerUid = snapshot.profile.workspaceOwnerUid.ifBlank { snapshot.profile.uid }
                ops.updateFieldsOfflineSafe(
                    ownerUid = ownerUid,
                    collection = "inventory",
                    docId = chora.id,
                    fields = mapOf("stockQty" to InputGuards.round3(chora.stockQty + expiredKg))
                )
            }
        }

        // Upsert customer record if new
        val existingCustomer = snapshot.customers.firstOrNull { !it.recycled && it.name.equals(cleanCustomer, true) }
        if (existingCustomer == null) {
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                memberUids = members,
                targetCollection = "customers",
                docId = null,
                summaryTitle = "New Customer: $cleanCustomer",
                summaryDetail = "Auto-added on $date",
                recordDate = date,
                payload = mapOf(
                    "name" to cleanCustomer,
                    "phone" to "",
                    "address" to "",
                    "visitCount" to 1L,
                    "openingBalance" to 0.0,
                    "recycled" to false,
                    "recycledAtEpoch" to 0L
                )
            )
        }

        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = members,
            targetCollection = "sales",
            docId = saleId,
            summaryTitle = "${if (mode == "sale") "Sale" else "Collection"}: $cleanCustomer",
            summaryDetail = "$date • Qty ${InputGuards.formatKg(qtyKg)} • Cash ${InputGuards.formatMoney(cashReceived)}",
            recordDate = date,
            payload = mapOf(
                "date" to date,
                "mode" to mode,
                "customerId" to (existingCustomer?.id ?: ""),
                "customerName" to cleanCustomer,
                "storeKey" to (store?.storeKey ?: ""),
                "storeName" to (store?.name ?: ""),
                "repName" to repName.ifBlank { "NONE" },
                "qtyKg" to InputGuards.round3(qtyKg),
                "ratePerKg" to InputGuards.round2(ratePerKg),
                "totalAmount" to totalAmount,
                "cashReceived" to InputGuards.round2(cashReceived),
                "creditAmount" to creditAmount,
                "expiredKg" to InputGuards.round3(expiredKg),
                "choraValue" to choraVal,
                "addToLedger" to addToLedger,
                "ledgerEntryId" to ledgerId,
                "notes" to InputGuards.sanitizeNotes(notes),
                "recycled" to false,
                "recycledAtEpoch" to 0L
            )
        )
    }

    suspend fun saveRepRecord(
        snapshot: WorkspaceSnapshot,
        date: String,
        repName: String,
        mode: String,
        customerName: String,
        store: StoreConfig?,
        qtyKg: Double,
        ratePerKg: Double,
        cashReceived: Double,
        lat: Double?,
        lng: Double?,
        addressConfirmed: Boolean,
        notes: String
    ): Result<String> {
        val cleanCust = InputGuards.sanitizeName(customerName)
        if (cleanCust.isBlank()) return Result.failure(IllegalArgumentException("Customer name required"))
        val total = InputGuards.round2(qtyKg * ratePerKg)
        val credit = if (mode == "sale") InputGuards.round2((total - cashReceived).coerceAtLeast(0.0)) else 0.0
        val payload = mutableMapOf<String, Any?>(
            "date" to date,
            "repName" to InputGuards.sanitizeName(repName).ifBlank { snapshot.profile.displayName },
            "mode" to mode,
            "customerId" to (snapshot.customers.firstOrNull { it.name.equals(cleanCust, true) }?.id ?: ""),
            "customerName" to cleanCust,
            "storeKey" to (store?.storeKey ?: ""),
            "qtyKg" to InputGuards.round3(qtyKg),
            "ratePerKg" to InputGuards.round2(ratePerKg),
            "totalAmount" to total,
            "cashReceived" to InputGuards.round2(cashReceived),
            "creditAmount" to credit,
            "addressConfirmed" to addressConfirmed,
            "notes" to InputGuards.sanitizeNotes(notes),
            "recycled" to false,
            "recycledAtEpoch" to 0L
        )
        if (lat != null && lng != null) {
            payload["routeLat"] = lat
            payload["routeLng"] = lng
        }
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = memberUids(snapshot),
            targetCollection = "rep_records",
            docId = null,
            summaryTitle = "Rep ${mode.uppercase()}: $cleanCust",
            summaryDetail = "$date • $repName • ${InputGuards.formatMoney(if (mode == "collection") cashReceived else total)}",
            recordDate = date,
            payload = payload
        )
    }

    suspend fun saveFactoryBatch(
        snapshot: WorkspaceSnapshot,
        date: String,
        formula: FormulaConfig,
        unitsToProduce: Long,
        notes: String
    ): Result<String> {
        if (unitsToProduce <= 0) return Result.failure(IllegalArgumentException("Units to produce must be >= 1"))
        val flat = FormulaEngine.flattenFormula(formula, snapshot.formulas, snapshot.inventory, unitsToProduce.toDouble())
        val invByName = snapshot.inventory.filter { !it.recycled }.associateBy { it.name.trim().lowercase() }
        for (line in flat) {
            val inv = invByName[line.name.lowercase()]
                ?: return Result.failure(IllegalArgumentException("Missing raw material in inventory: ${line.name}"))
            if (inv.stockQty + 0.001 < line.qtyKg) {
                return Result.failure(IllegalArgumentException("Insufficient ${inv.name}: need ${InputGuards.formatKg(line.qtyKg)}, have ${InputGuards.formatKg(inv.stockQty)}"))
            }
        }
        val ownerUid = snapshot.profile.workspaceOwnerUid.ifBlank { snapshot.profile.uid }
        val totalCost = FormulaEngine.calculateBatchCost(formula, snapshot.formulas, snapshot.inventory, unitsToProduce.toDouble())
        val members = memberUids(snapshot)

        val res = ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = members,
            targetCollection = "factory",
            docId = null,
            summaryTitle = "Factory Batch: ${formula.name} ($unitsToProduce units)",
            summaryDetail = "$date • Cost ${InputGuards.formatMoney(totalCost)}",
            recordDate = date,
            payload = mapOf(
                "date" to date,
                "recordType" to "batch",
                "formulaId" to formula.id,
                "formulaName" to formula.name,
                "storeKey" to (snapshot.stores.firstOrNull { it.formulaId == formula.id }?.storeKey ?: ""),
                "unitsProduced" to unitsToProduce,
                "qtyRawUnit" to 0.0,
                "conversionFactor" to 1.0,
                "qtyKg" to InputGuards.round3(flat.sumOf { it.qtyKg }),
                "unitCost" to InputGuards.round2(if (unitsToProduce > 0) totalCost / unitsToProduce else 0.0),
                "totalCost" to totalCost,
                "paidAmount" to 0.0,
                "payableBalance" to 0.0,
                "allocatedPaid" to 0.0,
                "settled" to true,
                "ingredientsUsed" to flat.map { mapOf("name" to it.name, "qtyKg" to it.qtyKg, "cost" to InputGuards.round2(it.qtyKg * it.unitCostPerKg)) },
                "notes" to InputGuards.sanitizeNotes(notes),
                "recycled" to false,
                "recycledAtEpoch" to 0L
            )
        )

        if (res.isSuccess && (snapshot.profile.role == "admin" || snapshot.profile.uid == ownerUid)) {
            flat.forEach { line ->
                val inv = invByName[line.name.lowercase()] ?: return@forEach
                ops.updateFieldsOfflineSafe(
                    ownerUid = ownerUid,
                    collection = "inventory",
                    docId = inv.id,
                    fields = mapOf("stockQty" to InputGuards.round3((inv.stockQty - line.qtyKg).coerceAtLeast(0.0)))
                )
            }
        }
        return res
    }

    suspend fun saveRawMaterialPurchase(
        snapshot: WorkspaceSnapshot,
        date: String,
        materialName: String,
        unitName: String,
        conversionFactor: Double,
        qtyRawUnits: Double,
        totalCost: Double,
        paidNow: Double,
        supplierName: String,
        minThreshold: Double,
        notes: String
    ): Result<String> {
        val cleanName = InputGuards.sanitizeName(materialName)
        if (cleanName.isBlank() || qtyRawUnits <= 0 || conversionFactor <= 0) {
            return Result.failure(IllegalArgumentException("Valid material name, quantity, and conversion factor are required"))
        }
        val qtyKg = InputGuards.round3(qtyRawUnits * conversionFactor)
        val unitCostPerKg = InputGuards.round2(if (qtyKg > 0) totalCost / qtyKg else 0.0)
        val payable = InputGuards.round2((totalCost - paidNow).coerceAtLeast(0.0))
        val cleanSupplier = InputGuards.sanitizeName(supplierName)
        val members = memberUids(snapshot)
        val ownerUid = snapshot.profile.workspaceOwnerUid.ifBlank { snapshot.profile.uid }

        val existingInv = snapshot.inventory.firstOrNull { !it.recycled && it.name.equals(cleanName, true) }
        val invId = existingInv?.id ?: "inv_${InputGuards.sanitizeDocId(cleanName)}"
        if (existingInv != null) {
            val newQty = InputGuards.round3(existingInv.stockQty + qtyKg)
            val newVal = (existingInv.stockQty.coerceAtLeast(0.0) * existingInv.avgCostPerKg) + totalCost
            val newAvg = if (newQty > 0) InputGuards.round2(newVal / newQty) else unitCostPerKg
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                memberUids = members,
                targetCollection = "inventory",
                docId = invId,
                summaryTitle = "Restock $cleanName (+${InputGuards.formatKg(qtyKg)})",
                summaryDetail = "Avg cost ${InputGuards.formatMoney(newAvg)}/kg",
                recordDate = date,
                isUpdate = true,
                payload = mapOf(
                    "name" to cleanName,
                    "unitName" to unitName.take(32).ifBlank { "kg" },
                    "conversionFactor" to conversionFactor,
                    "stockQty" to newQty,
                    "avgCostPerKg" to newAvg,
                    "minThreshold" to minThreshold,
                    "supplierName" to cleanSupplier
                )
            )
        } else {
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                memberUids = members,
                targetCollection = "inventory",
                docId = invId,
                summaryTitle = "Add Material $cleanName",
                summaryDetail = "${InputGuards.formatKg(qtyKg)} @ ${InputGuards.formatMoney(unitCostPerKg)}/kg",
                recordDate = date,
                payload = mapOf(
                    "name" to cleanName,
                    "unitName" to unitName.take(32).ifBlank { "kg" },
                    "conversionFactor" to conversionFactor,
                    "stockQty" to qtyKg,
                    "avgCostPerKg" to unitCostPerKg,
                    "minThreshold" to minThreshold,
                    "supplierName" to cleanSupplier,
                    "recycled" to false,
                    "recycledAtEpoch" to 0L
                )
            )
        }

        // Ensure supplier entity exists if named
        if (cleanSupplier.isNotBlank() && snapshot.entities.none { !it.recycled && it.name.equals(cleanSupplier, true) }) {
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                memberUids = members,
                targetCollection = "entities",
                docId = null,
                summaryTitle = "New Supplier: $cleanSupplier",
                summaryDetail = "Created on raw purchase",
                recordDate = date,
                payload = mapOf(
                    "name" to cleanSupplier,
                    "entityType" to "supplier",
                    "phone" to "",
                    "openingBalance" to 0.0,
                    "recycled" to false,
                    "recycledAtEpoch" to 0L
                )
            )
        }

        if (paidNow > 0 && (snapshot.profile.role == "admin" || snapshot.profile.uid == ownerUid)) {
            ops.saveLedgerMirror(
                profile = snapshot.profile,
                memberUids = members,
                date = date,
                entryType = "OUT",
                expenseScope = "business",
                channel = "Cash",
                category = "Raw Material Purchase",
                partyName = cleanSupplier.ifBlank { cleanName },
                amount = paidNow,
                sourceCollection = "factory",
                sourceDocId = invId,
                notes = "$cleanName (${InputGuards.formatKg(qtyKg)})"
            )
        }

        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = members,
            targetCollection = "factory",
            docId = null,
            summaryTitle = "Purchase: $cleanName (${InputGuards.formatKg(qtyKg)})",
            summaryDetail = "Total ${InputGuards.formatMoney(totalCost)} • Payable ${InputGuards.formatMoney(payable)}",
            recordDate = date,
            payload = mapOf(
                "date" to date,
                "recordType" to "purchase",
                "rawItemId" to invId,
                "rawItemName" to cleanName,
                "supplierName" to cleanSupplier,
                "unitsProduced" to 0L,
                "qtyRawUnit" to qtyRawUnits,
                "conversionFactor" to conversionFactor,
                "qtyKg" to qtyKg,
                "unitCost" to unitCostPerKg,
                "totalCost" to InputGuards.round2(totalCost),
                "paidAmount" to InputGuards.round2(paidNow),
                "payableBalance" to payable,
                "allocatedPaid" to InputGuards.round2(paidNow),
                "settled" to (payable <= 0.01),
                "ingredientsUsed" to emptyList<Map<String, Any>>(),
                "notes" to InputGuards.sanitizeNotes(notes),
                "recycled" to false,
                "recycledAtEpoch" to 0L
            )
        )
    }

    suspend fun savePaymentTransaction(
        snapshot: WorkspaceSnapshot,
        date: String,
        txType: String, // "in" | "out" | "expense"
        entity: EntityRecord?,
        entityName: String,
        entityCategory: String,
        channel: String,
        amount: Double,
        addToLedger: Boolean,
        notes: String
    ): Result<String> {
        val cleanName = InputGuards.sanitizeName(entity?.name ?: entityName)
        if (cleanName.isBlank() || amount <= 0) {
            return Result.failure(IllegalArgumentException("Name and positive amount are required"))
        }
        val members = memberUids(snapshot)
        val ownerUid = snapshot.profile.workspaceOwnerUid.ifBlank { snapshot.profile.uid }
        val txId = "tx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(5)}"

        if (addToLedger && (snapshot.profile.role == "admin" || snapshot.profile.uid == ownerUid)) {
            val lType = if (txType == "in") "IN" else "OUT"
            val scope = if (txType == "expense") "business" else "none"
            ops.saveLedgerMirror(
                profile = snapshot.profile,
                memberUids = members,
                date = date,
                entryType = lType,
                expenseScope = scope,
                channel = channel,
                category = if (txType == "expense") cleanName else entityCategory.replaceFirstChar { it.uppercase() },
                partyName = cleanName,
                amount = amount,
                sourceCollection = "transactions",
                sourceDocId = txId,
                notes = notes
            )
        }

        // FIFO supplier allocation on payment OUT to supplier
        if (txType == "out" && entity?.entityType == "supplier" && (snapshot.profile.role == "admin" || snapshot.profile.uid == ownerUid)) {
            val allocations = FinanceEngine.previewSupplierFifoAllocation(cleanName, amount, snapshot.factory)
            val factoryById = snapshot.factory.associateBy { it.id }
            allocations.forEach { alloc ->
                val existingAllocated = factoryById[alloc.purchaseId]?.allocatedPaid ?: 0.0
                ops.updateFieldsOfflineSafe(
                    ownerUid = ownerUid,
                    collection = "factory",
                    docId = alloc.purchaseId,
                    fields = mapOf(
                        "payableBalance" to alloc.afterPayable,
                        "allocatedPaid" to InputGuards.round2(existingAllocated + alloc.appliedPayment),
                        "settled" to (alloc.afterPayable <= 0.01)
                    )
                )
            }
        }

        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = members,
            targetCollection = "transactions",
            docId = txId,
            summaryTitle = "${txType.uppercase()}: $cleanName",
            summaryDetail = "$date • $channel • ${InputGuards.formatMoney(amount)}",
            recordDate = date,
            payload = mapOf(
                "date" to date,
                "txType" to txType,
                "entityId" to (entity?.id ?: ""),
                "entityName" to cleanName,
                "entityCategory" to entityCategory,
                "paymentChannel" to channel,
                "amount" to InputGuards.round2(amount),
                "addToLedger" to addToLedger,
                "notes" to InputGuards.sanitizeNotes(notes),
                "recycled" to false,
                "recycledAtEpoch" to 0L
            )
        )
    }

    suspend fun saveLedgerEntry(
        snapshot: WorkspaceSnapshot,
        date: String,
        entryType: String, // "IN" | "OUT" | "TRANSFER"
        expenseScope: String,
        channel: String,
        toChannel: String,
        category: String,
        partyName: String,
        amount: Double,
        notes: String
    ): Result<String> {
        if (amount <= 0) return Result.failure(IllegalArgumentException("Amount must be positive"))
        if (entryType == "TRANSFER" && channel.equals(toChannel, true)) {
            return Result.failure(IllegalArgumentException("Source and destination channels must differ"))
        }
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = memberUids(snapshot),
            targetCollection = "ledger_entries",
            docId = null,
            summaryTitle = "Ledger $entryType: ${partyName.ifBlank { category }}",
            summaryDetail = "$date • $channel • ${InputGuards.formatMoney(amount)}",
            recordDate = date,
            payload = mapOf(
                "date" to date,
                "entryType" to entryType,
                "expenseScope" to expenseScope,
                "channel" to channel,
                "toChannel" to toChannel,
                "category" to category.ifBlank { "General" }.take(60),
                "partyName" to InputGuards.sanitizeName(partyName),
                "amount" to InputGuards.round2(amount),
                "tabEntry" to false,
                "notes" to InputGuards.sanitizeNotes(notes),
                "recycled" to false,
                "recycledAtEpoch" to 0L
            )
        )
    }

    suspend fun saveCalculatorSettlement(
        snapshot: WorkspaceSnapshot,
        dateFrom: String,
        dateTo: String,
        repName: String,
        store: StoreConfig,
        totalSoldKg: Double,
        returnedKg: Double,
        returnTarget: String,
        returnTargetName: String,
        expiredKg: Double,
        sharedDeduction: Double,
        cashQtyKg: Double,
        creditQtyKg: Double,
        ratePerKg: Double,
        commissions: List<Map<String, Any>>,
        expenses: List<Map<String, Any>>,
        physicalCash: Double,
        addToLedger: Boolean,
        notes: String
    ): Result<String> {
        val netSold = InputGuards.round3((totalSoldKg - returnedKg - expiredKg).coerceAtLeast(0.0))
        val commTotal = InputGuards.round2(commissions.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 })
        val expTotal = InputGuards.round2(expenses.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 })
        val expectedCash = InputGuards.round2((cashQtyKg * ratePerKg) - sharedDeduction - commTotal - expTotal)
        val variance = InputGuards.round2(physicalCash - expectedCash)
        val members = memberUids(snapshot)
        val calcId = "calc_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"

        if (addToLedger && physicalCash > 0 && (snapshot.profile.role == "admin" || snapshot.profile.uid == snapshot.profile.workspaceOwnerUid)) {
            ops.saveLedgerMirror(
                profile = snapshot.profile,
                memberUids = members,
                date = dateTo,
                entryType = "IN",
                expenseScope = "none",
                channel = "Cash",
                category = "Rep Settlement",
                partyName = repName,
                amount = physicalCash,
                sourceCollection = "calculator",
                sourceDocId = calcId,
                notes = "$dateFrom..$dateTo • Net ${InputGuards.formatKg(netSold)}"
            )
        }

        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = members,
            targetCollection = "calculator",
            docId = calcId,
            summaryTitle = "Settlement: $repName (${store.name})",
            summaryDetail = "$dateFrom..$dateTo • Expected ${InputGuards.formatMoney(expectedCash)} • Physical ${InputGuards.formatMoney(physicalCash)}",
            recordDate = dateTo,
            payload = mapOf(
                "dateFrom" to dateFrom,
                "dateTo" to dateTo,
                "repName" to repName,
                "storeKey" to store.storeKey,
                "totalSoldKg" to InputGuards.round3(totalSoldKg),
                "returnedKg" to InputGuards.round3(returnedKg),
                "returnTarget" to returnTarget,
                "returnTargetName" to InputGuards.sanitizeName(returnTargetName),
                "expiredKg" to InputGuards.round3(expiredKg),
                "sharedDeduction" to InputGuards.round2(sharedDeduction),
                "netSoldKg" to netSold,
                "cashQtyKg" to InputGuards.round3(cashQtyKg),
                "creditQtyKg" to InputGuards.round3(creditQtyKg),
                "ratePerKg" to InputGuards.round2(ratePerKg),
                "commissionTotal" to commTotal,
                "commissions" to commissions.take(25),
                "expenseTotal" to expTotal,
                "expenses" to expenses.take(40),
                "expectedCash" to expectedCash,
                "physicalCash" to InputGuards.round2(physicalCash),
                "variance" to variance,
                "addToLedger" to addToLedger,
                "notes" to InputGuards.sanitizeNotes(notes),
                "recycled" to false,
                "recycledAtEpoch" to 0L
            )
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
        if (fromStore.storeKey == toStore.storeKey) return Result.failure(IllegalArgumentException("Source and destination stores must differ"))
        if (qtyKg <= 0) return Result.failure(IllegalArgumentException("Quantity must be positive"))
        val available = FinanceEngine.computeStoreStockSnapshots(snapshot)
            .firstOrNull { it.storeKey == fromStore.storeKey }?.availableKg ?: 0.0
        if (qtyKg > available + 0.001) {
            return Result.failure(IllegalArgumentException("Only ${InputGuards.formatKg(available)} available in ${fromStore.name}"))
        }
        val pairId = "tr_${System.currentTimeMillis()}"
        val members = memberUids(snapshot)
        ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = members,
            targetCollection = "production",
            docId = "${pairId}_out",
            summaryTitle = "Transfer OUT: ${fromStore.name} -> ${toStore.name}",
            summaryDetail = InputGuards.formatKg(qtyKg),
            recordDate = date,
            payload = mapOf(
                "date" to date,
                "storeKey" to fromStore.storeKey,
                "storeName" to fromStore.name,
                "formulaUnits" to 0.0,
                "grossWeight" to qtyKg,
                "containerWeight" to 0.0,
                "netWeight" to -InputGuards.round3(qtyKg),
                "costPerKg" to 0.0,
                "salePrice" to fromStore.salePrice,
                "totalCost" to 0.0,
                "totalRevenue" to 0.0,
                "profit" to 0.0,
                "transfer" to true,
                "transferType" to "out",
                "transferPairId" to pairId,
                "transferPeerStore" to toStore.name,
                "notes" to InputGuards.sanitizeNotes(notes),
                "recycled" to false,
                "recycledAtEpoch" to 0L
            )
        )
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = members,
            targetCollection = "production",
            docId = "${pairId}_in",
            summaryTitle = "Transfer IN: ${fromStore.name} -> ${toStore.name}",
            summaryDetail = InputGuards.formatKg(qtyKg),
            recordDate = date,
            payload = mapOf(
                "date" to date,
                "storeKey" to toStore.storeKey,
                "storeName" to toStore.name,
                "formulaUnits" to 0.0,
                "grossWeight" to qtyKg,
                "containerWeight" to 0.0,
                "netWeight" to InputGuards.round3(qtyKg),
                "costPerKg" to 0.0,
                "salePrice" to toStore.salePrice,
                "totalCost" to 0.0,
                "totalRevenue" to 0.0,
                "profit" to 0.0,
                "transfer" to true,
                "transferType" to "in",
                "transferPairId" to pairId,
                "transferPeerStore" to fromStore.name,
                "notes" to InputGuards.sanitizeNotes(notes),
                "recycled" to false,
                "recycledAtEpoch" to 0L
            )
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
        if (fromEntity.id == toEntity.id) return Result.failure(IllegalArgumentException("Entities must differ"))
        if (amount <= 0) return Result.failure(IllegalArgumentException("Amount must be positive"))
        val pairId = "ptr_${System.currentTimeMillis()}"
        val members = memberUids(snapshot)
        ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = members,
            targetCollection = "transactions",
            docId = "${pairId}_out",
            summaryTitle = "Entity Transfer OUT: ${fromEntity.name} -> ${toEntity.name}",
            summaryDetail = InputGuards.formatMoney(amount),
            recordDate = date,
            payload = mapOf(
                "date" to date,
                "txType" to "transfer_out",
                "entityId" to fromEntity.id,
                "entityName" to fromEntity.name,
                "entityCategory" to fromEntity.entityType,
                "paymentChannel" to "Transfer",
                "amount" to InputGuards.round2(amount),
                "transferPairId" to pairId,
                "peerEntityName" to toEntity.name,
                "addToLedger" to false,
                "notes" to InputGuards.sanitizeNotes(notes),
                "recycled" to false,
                "recycledAtEpoch" to 0L
            )
        )
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = members,
            targetCollection = "transactions",
            docId = "${pairId}_in",
            summaryTitle = "Entity Transfer IN: ${fromEntity.name} -> ${toEntity.name}",
            summaryDetail = InputGuards.formatMoney(amount),
            recordDate = date,
            payload = mapOf(
                "date" to date,
                "txType" to "transfer_in",
                "entityId" to toEntity.id,
                "entityName" to toEntity.name,
                "entityCategory" to toEntity.entityType,
                "paymentChannel" to "Transfer",
                "amount" to InputGuards.round2(amount),
                "transferPairId" to pairId,
                "peerEntityName" to fromEntity.name,
                "addToLedger" to false,
                "notes" to InputGuards.sanitizeNotes(notes),
                "recycled" to false,
                "recycledAtEpoch" to 0L
            )
        )
    }

    suspend fun saveStore(snapshot: WorkspaceSnapshot, existingId: String?, name: String, formulaId: String, salePrice: Double, active: Boolean): Result<String> {
        val cleanName = InputGuards.sanitizeName(name).uppercase()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Store name required"))
        val storeKey = InputGuards.canonicalKey(cleanName).uppercase()
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = memberUids(snapshot),
            targetCollection = "stores",
            docId = existingId ?: "store_${InputGuards.sanitizeDocId(storeKey)}",
            summaryTitle = "Store: $cleanName",
            summaryDetail = "Rate ${InputGuards.formatMoney(salePrice)}",
            recordDate = DateUtils.todayIso(),
            isUpdate = !existingId.isNullOrBlank(),
            payload = mapOf(
                "storeKey" to storeKey,
                "name" to cleanName,
                "formulaId" to formulaId,
                "salePrice" to InputGuards.round2(salePrice),
                "active" to active
            )
        )
    }

    suspend fun saveFormula(
        snapshot: WorkspaceSnapshot,
        existing: FormulaConfig?,
        name: String,
        unitsPerBatch: Double,
        additionalCost: Double,
        costAdjustment: Double,
        ingredients: List<Map<String, Any>>
    ): Result<String> {
        val clean = InputGuards.sanitizeName(name)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Formula name required"))
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = memberUids(snapshot),
            targetCollection = "formulas",
            docId = existing?.id?.takeIf { it.isNotBlank() } ?: "formula_${InputGuards.sanitizeDocId(clean)}",
            summaryTitle = "Formula: $clean",
            summaryDetail = "${ingredients.size} ingredients",
            recordDate = DateUtils.todayIso(),
            isUpdate = existing != null && existing.id.isNotBlank(),
            payload = mapOf(
                "name" to clean,
                "unitsPerBatch" to InputGuards.round2(unitsPerBatch.coerceAtLeast(0.01)),
                "additionalCost" to InputGuards.round2(additionalCost.coerceAtLeast(0.0)),
                "costAdjustment" to InputGuards.round2(costAdjustment),
                "ingredients" to ingredients.take(40),
                "savedMixes" to (existing?.savedMixes ?: emptyList()),
                "savedCorrections" to (existing?.savedCorrections ?: emptyList())
            )
        )
    }

    suspend fun addEntity(snapshot: WorkspaceSnapshot, name: String, entityType: String, phone: String, openingBalance: Double, notes: String): Result<String> {
        val clean = InputGuards.sanitizeName(name)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Entity name required"))
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = memberUids(snapshot),
            targetCollection = "entities",
            docId = null,
            summaryTitle = "Entity: $clean ($entityType)",
            summaryDetail = phone,
            recordDate = DateUtils.todayIso(),
            payload = mapOf(
                "name" to clean,
                "entityType" to entityType,
                "phone" to phone.take(32),
                "notes" to InputGuards.sanitizeNotes(notes),
                "openingBalance" to InputGuards.round2(openingBalance),
                "recycled" to false,
                "recycledAtEpoch" to 0L
            )
        )
    }

    suspend fun updateCustomer(
        snapshot: WorkspaceSnapshot,
        existing: CustomerRecord,
        name: String,
        phone: String,
        address: String,
        openingBalance: Double,
        notes: String,
        lat: Double? = existing.routeLat,
        lng: Double? = existing.routeLng
    ): Result<String> {
        val clean = InputGuards.sanitizeName(name)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Customer name required"))
        val payload = mutableMapOf<String, Any?>(
            "name" to clean,
            "phone" to phone.take(32),
            "address" to address.take(200),
            "visitCount" to existing.visitCount,
            "openingBalance" to InputGuards.round2(openingBalance),
            "notes" to InputGuards.sanitizeNotes(notes),
            "recycled" to false,
            "recycledAtEpoch" to 0L
        )
        if (lat != null && lng != null) {
            payload["routeLat"] = lat
            payload["routeLng"] = lng
        }
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = memberUids(snapshot),
            targetCollection = "customers",
            docId = existing.id,
            summaryTitle = "Edit Customer: $clean",
            summaryDetail = phone.ifBlank { address },
            recordDate = DateUtils.todayIso(),
            isUpdate = true,
            payload = payload
        )
    }

    suspend fun deleteFormula(snapshot: WorkspaceSnapshot, formulaId: String): Result<Unit> {
        val ownerUid = snapshot.profile.workspaceOwnerUid.ifBlank { snapshot.profile.uid }
        val inUseByStore = snapshot.stores.any { it.formulaId == formulaId }
        val inUseByFormula = snapshot.formulas.any { f ->
            f.id != formulaId && f.ingredients.any { (it["linkFormulaId"] as? String) == formulaId }
        }
        if (inUseByStore || inUseByFormula) {
            return Result.failure(IllegalArgumentException("Formula is currently linked to a store or sub-formula"))
        }
        return ops.permanentDelete(ownerUid, "formulas", formulaId)
    }

    suspend fun saveFormulaSavedMix(
        snapshot: WorkspaceSnapshot,
        formula: FormulaConfig,
        mixMap: Map<String, Any>
    ): Result<String> {
        val nextMixes = (listOf(mixMap) + formula.savedMixes).take(30)
        return ops.submitOrExecuteWrite(
            profile = snapshot.profile,
            memberUids = memberUids(snapshot),
            targetCollection = "formulas",
            docId = formula.id,
            summaryTitle = "Saved Mix: ${formula.name}",
            summaryDetail = mixMap["title"]?.toString() ?: "Batch calculation",
            recordDate = DateUtils.todayIso(),
            isUpdate = true,
            payload = mapOf(
                "name" to formula.name,
                "unitsPerBatch" to formula.unitsPerBatch,
                "additionalCost" to formula.additionalCost,
                "costAdjustment" to formula.costAdjustment,
                "ingredients" to formula.ingredients,
                "savedMixes" to nextMixes,
                "savedCorrections" to formula.savedCorrections
            )
        )
    }

    suspend fun closeFinancialYear(snapshot: WorkspaceSnapshot): Result<String> {
        val ownerUid = snapshot.profile.workspaceOwnerUid.ifBlank { snapshot.profile.uid }
        if (snapshot.profile.uid != ownerUid && snapshot.profile.role != "admin") {
            return Result.failure(IllegalArgumentException("Only workspace Admin can close the financial year"))
        }
        val members = memberUids(snapshot)
        val today = DateUtils.todayIso()
        val storeStocks = FinanceEngine.computeStoreStockSnapshots(snapshot)

        // Persist closing customer balances into customer openingBalance before recycling historical sales
        val activeCustomers = snapshot.customers.filter { !it.recycled }
        activeCustomers.forEach { cust ->
            val closingBal = FinanceEngine.computeCustomerBalance(cust.name, snapshot.customers, snapshot.sales, snapshot.repRecords)
            updateCustomer(snapshot, cust, cust.name, cust.phone, cust.address, closingBal, cust.notes ?: "")
        }

        // Persist closing entity balances into entity openingBalance
        val activeEntities = snapshot.entities.filter { !it.recycled }
        activeEntities.forEach { ent ->
            val closingBal = FinanceEngine.computeEntityBalance(ent, snapshot.transactions, snapshot.factory)
            ops.submitOrExecuteWrite(
                profile = snapshot.profile,
                memberUids = members,
                targetCollection = "entities",
                docId = ent.id,
                summaryTitle = "Year-End Opening: ${ent.name}",
                summaryDetail = InputGuards.formatMoney(closingBal),
                recordDate = today,
                isUpdate = true,
                payload = mapOf(
                    "name" to ent.name,
                    "entityType" to ent.entityType,
                    "phone" to ent.phone,
                    "notes" to (ent.notes ?: ""),
                    "openingBalance" to closingBal,
                    "recycled" to false,
                    "recycledAtEpoch" to 0L
                )
            )
        }

        var compacted = 0
        snapshot.production.filter { !it.recycled && !it.transfer }.forEach {
            ops.softRecycleRecord(snapshot.profile, members, "production", it.id, "FY Close ${it.date}")
            compacted++
        }
        snapshot.sales.filter { !it.recycled }.forEach {
            ops.softRecycleRecord(snapshot.profile, members, "sales", it.id, "FY Close ${it.date}")
            compacted++
        }
        snapshot.calculator.filter { !it.recycled }.forEach {
            ops.softRecycleRecord(snapshot.profile, members, "calculator", it.id, "FY Close ${it.dateTo}")
            compacted++
        }
        snapshot.transactions.filter { !it.recycled }.forEach {
            ops.softRecycleRecord(snapshot.profile, members, "transactions", it.id, "FY Close ${it.date}")
            compacted++
        }

        // Re-create single opening stock record per store with non-zero stock
        storeStocks.filter { it.availableKg > 0.001 }.forEach { st ->
            val storeObj = snapshot.stores.firstOrNull { it.storeKey == st.storeKey }
            if (storeObj != null) {
                saveProduction(
                    snapshot = snapshot,
                    date = today,
                    store = storeObj,
                    grossWeight = st.availableKg,
                    containerWeight = 0.0,
                    formulaUnits = 0.0,
                    costPerKg = st.avgCostPerKg,
                    notes = "Financial Year Opening Stock"
                )
            }
        }
        return Result.success("Compacted $compacted records into opening balances")
    }
}

