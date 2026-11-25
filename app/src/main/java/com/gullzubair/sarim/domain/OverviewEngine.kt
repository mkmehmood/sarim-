package com.gullzubair.sarim.domain

import com.gullzubair.sarim.data.model.WorkspaceSnapshot

data class AuditFinding(
    val id: String,
    val category: String, // "ledger_mirror" | "negative_stock" | "transfer_pair" | "cost_missing"
    val severity: String, // "warning" | "critical"
    val title: String,
    val detail: String,
    val targetId: String = ""
)

data class OverviewKpiSummary(
    val windowMonths: Int,
    val totalSalesRevenue: Double,
    val totalCashCollected: Double,
    val totalProducedKg: Double,
    val totalProductionProfit: Double,
    val totalOperatingExpenses: Double,
    val netOperatingProfit: Double,
    val openCredits: Double,
    val cashInHand: Double,
    val monthlyRevenueSeries: List<Pair<String, Double>>,
    val monthlyExpenseSeries: List<Pair<String, Double>>,
    val storeSalesBreakdown: List<Pair<String, Double>>,
    val findings: List<AuditFinding>
)

object OverviewEngine {

    fun computeOverview(snapshot: WorkspaceSnapshot, windowMonths: Int = 3): OverviewKpiSummary {
        val fromIso = DateUtils.monthsAgoIso(windowMonths.toLong())
        val todayIso = DateUtils.todayIso()

        val activeProd = snapshot.production.filter {
            !it.recycled && !it.transfer && DateUtils.isInRange(it.date, fromIso, todayIso)
        }
        val activeSales = snapshot.sales.filter {
            !it.recycled && DateUtils.isInRange(it.date, fromIso, todayIso)
        }
        val activeLedger = snapshot.ledgerEntries.filter {
            !it.recycled && DateUtils.isInRange(it.date, fromIso, todayIso)
        }
        val activeTx = snapshot.transactions.filter {
            !it.recycled && DateUtils.isInRange(it.date, fromIso, todayIso)
        }

        val salesRev = activeSales.filter { it.mode == "sale" }.sumOf { it.totalAmount }
        val cashCollected = activeSales.sumOf { it.cashReceived }
        val producedKg = activeProd.sumOf { it.netWeight }
        val prodProfit = activeProd.sumOf { it.profit }

        val opExpenses = activeTx.filter { it.txType == "expense" }.sumOf { it.amount } +
            activeLedger.filter { it.entryType == "OUT" && it.expenseScope == "business" && !it.tabEntry }.sumOf { it.amount }

        val health = FinanceEngine.calculateEconomicHealth(snapshot)

        val monthRevMap = sortedMapOf<String, Double>()
        activeSales.filter { it.mode == "sale" }.forEach { s ->
            val ym = s.date.take(7)
            if (ym.length == 7) monthRevMap[ym] = (monthRevMap[ym] ?: 0.0) + s.totalAmount
        }
        val monthExpMap = sortedMapOf<String, Double>()
        activeLedger.filter { it.entryType == "OUT" && it.expenseScope == "business" }.forEach { l ->
            val ym = l.date.take(7)
            if (ym.length == 7) monthExpMap[ym] = (monthExpMap[ym] ?: 0.0) + l.amount
        }

        val byStore = activeSales.filter { it.mode == "sale" }
            .groupBy { it.storeName ?: it.storeKey }
            .mapValues { (_, list) -> InputGuards.round2(list.sumOf { it.totalAmount }) }
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        val findings = auditWorkspace(snapshot)

        return OverviewKpiSummary(
            windowMonths = windowMonths,
            totalSalesRevenue = InputGuards.round2(salesRev),
            totalCashCollected = InputGuards.round2(cashCollected),
            totalProducedKg = InputGuards.round3(producedKg),
            totalProductionProfit = InputGuards.round2(prodProfit),
            totalOperatingExpenses = InputGuards.round2(opExpenses),
            netOperatingProfit = InputGuards.round2(prodProfit - opExpenses),
            openCredits = health.customerReceivables,
            cashInHand = health.cashInHand,
            monthlyRevenueSeries = monthRevMap.entries.toList().takeLast(6).map { it.key to InputGuards.round2(it.value) },
            monthlyExpenseSeries = monthExpMap.entries.toList().takeLast(6).map { it.key to InputGuards.round2(it.value) },
            storeSalesBreakdown = byStore,
            findings = findings
        )
    }

    fun auditWorkspace(snapshot: WorkspaceSnapshot): List<AuditFinding> {
        val findings = mutableListOf<AuditFinding>()
        val ledgerIds = snapshot.ledgerEntries.filter { !it.recycled }.map { it.id }.toSet()

        // 1. Check sales marked addToLedger without active ledger mirror
        snapshot.sales.filter { !it.recycled && it.addToLedger && it.cashReceived > 0 }.forEach { sale ->
            if (sale.ledgerEntryId.isNullOrBlank() || sale.ledgerEntryId !in ledgerIds) {
                findings.add(
                    AuditFinding(
                        id = "mirror_sale_${sale.id}",
                        category = "ledger_mirror",
                        severity = "warning",
                        title = "Missing Ledger Mirror for Sale",
                        detail = "${sale.date} • ${sale.customerName} (${InputGuards.formatMoney(sale.cashReceived)})",
                        targetId = sale.id
                    )
                )
            }
        }

        // 2. Check payments marked addToLedger without active ledger mirror
        snapshot.transactions.filter { !it.recycled && it.addToLedger && it.amount > 0 && it.txType in listOf("in", "out", "expense") }.forEach { tx ->
            if (tx.ledgerEntryId.isNullOrBlank() || tx.ledgerEntryId !in ledgerIds) {
                findings.add(
                    AuditFinding(
                        id = "mirror_tx_${tx.id}",
                        category = "ledger_mirror",
                        severity = "warning",
                        title = "Missing Ledger Mirror for Payment",
                        detail = "${tx.date} • ${tx.entityName} (${InputGuards.formatMoney(tx.amount)})",
                        targetId = tx.id
                    )
                )
            }
        }

        // 3. Check negative store stock
        FinanceEngine.computeStoreStockSnapshots(snapshot).filter { it.availableKg < -0.001 }.forEach { stock ->
            findings.add(
                AuditFinding(
                    id = "neg_stock_${stock.storeKey}",
                    category = "negative_stock",
                    severity = "critical",
                    title = "Negative Store Stock: ${stock.storeName}",
                    detail = "Available balance is ${InputGuards.formatKg(stock.availableKg)}",
                    targetId = stock.storeKey
                )
            )
        }

        // 4. Check low raw material inventory
        snapshot.inventory.filter { !it.recycled && it.minThreshold > 0 && it.stockQty <= it.minThreshold }.forEach { item ->
            findings.add(
                AuditFinding(
                    id = "low_inv_${item.id}",
                    category = "low_inventory",
                    severity = "warning",
                    title = "Low Raw Material: ${item.name}",
                    detail = "${InputGuards.formatKg(item.stockQty)} remaining (min ${InputGuards.formatKg(item.minThreshold)})",
                    targetId = item.id
                )
            )
        }

        return findings
    }
}
