package com.gullzubair.sarim.domain

import com.gullzubair.sarim.data.model.CalculatorSettlement
import com.gullzubair.sarim.data.model.CustomerRecord
import com.gullzubair.sarim.data.model.DailyLedgerEntry
import com.gullzubair.sarim.data.model.EntityRecord
import com.gullzubair.sarim.data.model.FactoryRecord
import com.gullzubair.sarim.data.model.FormulaConfig
import com.gullzubair.sarim.data.model.InventoryItem
import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.RepRecord
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import kotlin.math.abs

data class StoreStockSnapshot(
    val storeKey: String,
    val storeName: String,
    val producedKg: Double,
    val soldKg: Double,
    val repReturnedKg: Double,
    val availableKg: Double,
    val avgCostPerKg: Double,
    val salePrice: Double
)

data class UnitPoolStatus(
    val formulaId: String,
    val formulaName: String,
    val producedUnits: Double,
    val usedUnits: Double,
    val availableUnits: Double,
    val avgCostPerUnit: Double
)

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

data class SupplierAllocationPreview(
    val purchaseId: String,
    val date: String,
    val materialName: String,
    val beforePayable: Double,
    val appliedPayment: Double,
    val afterPayable: Double
)

object FinanceEngine {

    fun buildStoreCostIndex(
        production: List<ProductionRecord>,
        stores: List<StoreConfig>
    ): Map<String, Double> {
        val activeProd = production.filter { !it.recycled && !it.transfer && it.netWeight > 0 && it.costPerKg > 0 }
        val result = mutableMapOf<String, Double>()
        stores.forEach { store ->
            val key = store.storeKey
            val matching = activeProd.filter { it.storeKey == key }
            if (matching.isNotEmpty()) {
                val totalCost = matching.sumOf { it.totalCost }
                val totalKg = matching.sumOf { it.netWeight }
                result[key] = if (totalKg > 0) InputGuards.round2(totalCost / totalKg) else 0.0
            } else {
                result[key] = 0.0
            }
        }
        return result
    }

    fun resolveProductionDayCostPerKg(
        date: String,
        storeKey: String,
        production: List<ProductionRecord>,
        stores: List<StoreConfig>,
        formulas: List<FormulaConfig>,
        inventory: List<InventoryItem>
    ): Pair<Double, String> {
        val sameDay = production.filter {
            !it.recycled && !it.transfer && it.storeKey == storeKey && it.date == date && it.costPerKg > 0
        }
        if (sameDay.isNotEmpty()) {
            val kg = sameDay.sumOf { it.netWeight }
            val cost = sameDay.sumOf { it.totalCost }
            if (kg > 0) return InputGuards.round2(cost / kg) to "production-day"
        }
        val avgIndex = buildStoreCostIndex(production, stores)
        val avg = avgIndex[storeKey] ?: 0.0
        if (avg > 0.0) return avg to "store-average"
        val store = stores.firstOrNull { it.storeKey == storeKey }
        val formula = formulas.firstOrNull { it.id == store?.formulaId }
        if (formula != null) {
            val batchCost = FormulaEngine.calculateBatchCost(formula, formulas, inventory, 1.0)
            val baseKg = formula.ingredients.sumOf { (it["qtyKg"] as? Number)?.toDouble() ?: 0.0 }.coerceAtLeast(1.0)
            return InputGuards.round2(batchCost / baseKg) to "formula-est"
        }
        return 0.0 to "none"
    }

    fun computeStoreStockSnapshots(snapshot: WorkspaceSnapshot): List<StoreStockSnapshot> {
        val prod = snapshot.production.filter { !it.recycled }
        val sales = snapshot.sales.filter { !it.recycled && it.mode == "sale" }
        val calcs = snapshot.calculator.filter { !it.recycled && it.returnTarget == "store" }
        val costIndex = buildStoreCostIndex(snapshot.production, snapshot.stores)

        return snapshot.stores.filter { it.active }.map { store ->
            val produced = prod.filter { it.storeKey == store.storeKey }.sumOf { it.netWeight }
            val sold = sales.filter { it.storeKey == store.storeKey }.sumOf { it.qtyKg }
            val returned = calcs.filter { it.storeKey == store.storeKey }.sumOf { it.returnedKg }
            val available = InputGuards.round3(produced + returned - sold)
            StoreStockSnapshot(
                storeKey = store.storeKey,
                storeName = store.name,
                producedKg = InputGuards.round3(produced),
                soldKg = InputGuards.round3(sold),
                repReturnedKg = InputGuards.round3(returned),
                availableKg = available,
                avgCostPerKg = costIndex[store.storeKey] ?: 0.0,
                salePrice = store.salePrice
            )
        }
    }

    fun computeUnitPools(snapshot: WorkspaceSnapshot): List<UnitPoolStatus> {
        val batches = snapshot.factory.filter { !it.recycled && it.recordType == "batch" }
        val usedByProd = snapshot.production.filter { !it.recycled && !it.transfer }

        return snapshot.formulas.map { formula ->
            val fBatches = batches.filter { it.formulaId == formula.id }
            val produced = fBatches.sumOf { it.unitsProduced.toDouble() }
            val totalBatchCost = fBatches.sumOf { it.totalCost }
            val used = usedByProd.filter {
                it.formulaId == formula.id ||
                    snapshot.stores.firstOrNull { s -> s.storeKey == it.storeKey }?.formulaId == formula.id
            }.sumOf { it.formulaUnits }
            UnitPoolStatus(
                formulaId = formula.id,
                formulaName = formula.name,
                producedUnits = InputGuards.round2(produced),
                usedUnits = InputGuards.round2(used),
                availableUnits = InputGuards.round2(produced - used),
                avgCostPerUnit = if (produced > 0.0) InputGuards.round2(totalBatchCost / produced) else 0.0
            )
        }
    }

    fun computeAvailableCashInHand(snapshot: WorkspaceSnapshot): Double {
        val entries = snapshot.ledgerEntries.filter { !it.recycled }
        val cashIn = entries.filter { it.entryType == "IN" && it.channel.equals("Cash", true) }.sumOf { it.amount }
        val cashOut = entries.filter { it.entryType == "OUT" && it.channel.equals("Cash", true) }.sumOf { it.amount }
        val trOut = entries.filter { it.entryType == "TRANSFER" && it.channel.equals("Cash", true) }.sumOf { it.amount }
        val trIn = entries.filter { it.entryType == "TRANSFER" && (it.toChannel ?: "").equals("Cash", true) }.sumOf { it.amount }
        return InputGuards.round2(cashIn + trIn - cashOut - trOut)
    }

    fun computeChannelBalances(snapshot: WorkspaceSnapshot): Map<String, Double> {
        val channels = snapshot.settings.paymentChannels.ifEmpty { listOf("Cash", "Bank", "Easypaisa", "JazzCash") }
        val entries = snapshot.ledgerEntries.filter { !it.recycled }
        return channels.associateWith { ch ->
            val inflow = entries.filter { it.entryType == "IN" && it.channel.equals(ch, true) }.sumOf { it.amount }
            val outflow = entries.filter { it.entryType == "OUT" && it.channel.equals(ch, true) }.sumOf { it.amount }
            val trOut = entries.filter { it.entryType == "TRANSFER" && it.channel.equals(ch, true) }.sumOf { it.amount }
            val trIn = entries.filter { it.entryType == "TRANSFER" && (it.toChannel ?: "").equals(ch, true) }.sumOf { it.amount }
            InputGuards.round2(inflow + trIn - outflow - trOut)
        }
    }

    fun computeCustomerBalance(
        customerName: String,
        customers: List<CustomerRecord>,
        sales: List<SalesRecord>,
        repRecords: List<RepRecord>
    ): Double {
        val key = customerName.trim().lowercase()
        val opening = customers.filter { !it.recycled && it.name.trim().lowercase() == key }
            .sumOf { it.openingBalance }
        val directSales = sales.filter { !it.recycled && it.customerName.trim().lowercase() == key }
        val rep = repRecords.filter { !it.recycled && it.customerName.trim().lowercase() == key }

        val salesDelta = directSales.sumOf {
            when (it.mode) {
                "sale" -> it.creditAmount
                "collection" -> -(it.cashReceived + it.choraValue)
                "money_out" -> it.totalAmount
                else -> 0.0
            }
        }
        val repDelta = rep.sumOf {
            when (it.mode) {
                "sale" -> it.creditAmount
                "collection" -> -it.cashReceived
                "return" -> -it.totalAmount
                else -> 0.0
            }
        }
        return InputGuards.round2(opening + salesDelta + repDelta)
    }

    fun computeEntityBalance(
        entity: EntityRecord,
        transactions: List<PaymentTransaction>,
        purchases: List<FactoryRecord>
    ): Double {
        val key = entity.name.trim().lowercase()
        val txs = transactions.filter { !it.recycled && it.entityName.trim().lowercase() == key }
        val txNet = txs.sumOf {
            when (it.txType) {
                "in", "transfer_in" -> -it.amount
                "out", "transfer_out", "expense" -> it.amount
                else -> 0.0
            }
        }
        val supplierPayable = if (entity.entityType == "supplier") {
            purchases.filter { !it.recycled && it.recordType == "purchase" && (it.supplierName ?: "").trim().lowercase() == key }
                .sumOf { it.payableBalance }
        } else 0.0
        return InputGuards.round2(entity.openingBalance + txNet - supplierPayable)
    }

    fun previewSupplierFifoAllocation(
        supplierName: String,
        paymentAmount: Double,
        factoryRecords: List<FactoryRecord>
    ): List<SupplierAllocationPreview> {
        var remaining = paymentAmount.coerceAtLeast(0.0)
        val openLots = factoryRecords
            .filter {
                !it.recycled &&
                    it.recordType == "purchase" &&
                    (it.supplierName ?: "").equals(supplierName.trim(), ignoreCase = true) &&
                    it.payableBalance > 0.005
            }
            .sortedBy { it.date }

        val previews = mutableListOf<SupplierAllocationPreview>()
        for (lot in openLots) {
            if (remaining <= 0.005) break
            val applied = minOf(remaining, lot.payableBalance)
            remaining = InputGuards.round2(remaining - applied)
            previews.add(
                SupplierAllocationPreview(
                    purchaseId = lot.id,
                    date = lot.date,
                    materialName = lot.rawItemName ?: "Raw Material",
                    beforePayable = InputGuards.round2(lot.payableBalance),
                    appliedPayment = InputGuards.round2(applied),
                    afterPayable = InputGuards.round2(lot.payableBalance - applied)
                )
            )
        }
        return previews
    }

    fun calculateEconomicHealth(snapshot: WorkspaceSnapshot): EconomicHealthSummary {
        val cash = computeChannelBalances(snapshot).values.sum()
        val rawValue = snapshot.inventory.filter { !it.recycled && it.stockQty > 0 }
            .sumOf { it.stockQty * it.avgCostPerKg }
        val unitValue = computeUnitPools(snapshot).filter { it.availableUnits > 0 }
            .sumOf { it.availableUnits * it.avgCostPerUnit }
        val finishedValue = computeStoreStockSnapshots(snapshot).filter { it.availableKg > 0 }
            .sumOf { it.availableKg * it.avgCostPerKg }

        val allCustomerNames = (snapshot.customers.filter { !it.recycled }.map { it.name } +
            snapshot.sales.filter { !it.recycled }.map { it.customerName } +
            snapshot.repRecords.filter { !it.recycled }.map { it.customerName })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        val receivables = allCustomerNames.sumOf { name ->
            computeCustomerBalance(name, snapshot.customers, snapshot.sales, snapshot.repRecords).coerceAtLeast(0.0)
        }

        val supplierPayables = snapshot.factory
            .filter { !it.recycled && it.recordType == "purchase" }
            .sumOf { it.payableBalance.coerceAtLeast(0.0) }

        var entityReceivables = 0.0
        var entityPayables = 0.0
        snapshot.entities.filter { !it.recycled && it.entityType != "expense" }.forEach { entity ->
            val bal = computeEntityBalance(entity, snapshot.transactions, emptyList())
            if (bal > 0) entityReceivables += bal else if (bal < 0) entityPayables += abs(bal)
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

    fun getUnreconciledRepDeliveriesKg(
        repName: String,
        storeKey: String,
        dateFrom: String,
        dateTo: String,
        sales: List<SalesRecord>
    ): Double {
        return InputGuards.round3(
            sales.filter {
                !it.recycled &&
                    it.mode == "sale" &&
                    it.repName.equals(repName, ignoreCase = true) &&
                    (storeKey.isBlank() || it.storeKey == storeKey) &&
                    DateUtils.isInRange(it.date, dateFrom, dateTo)
            }.sumOf { it.qtyKg }
        )
    }
}
