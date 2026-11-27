package com.gullzubair.sarim.domain

import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import kotlin.math.max

/**
 * Delete guards for Payments records. Port of the web app's `link-guards.js` /
 * `link-graph.js` (getPaymentTxDeleteBlockReason, getEntityDeleteBlockReason,
 * getExpenseLinkedTxBlockReason, getCashShortIssue). Every function returns "" when the
 * delete is allowed, otherwise a user-facing reason.
 */
object LinkGuards {

    // ---- Pure helpers (mirror link-graph.js) -------------------------------------------

    /** A payment that moves real cash: not a transfer, not a merged opening balance, and not a payable "IN". */
    private fun isCashRelevant(tx: PaymentTransaction): Boolean =
        !tx.isTransfer && !tx.isMerged && !(tx.isPayable && tx.type == "IN")

    /** Cash that leaves the till if this payment is removed (only money received counts). */
    fun deleteCashDrop(tx: PaymentTransaction): Double =
        if (isCashRelevant(tx) && tx.type == "IN") tx.amount else 0.0

    /** Cash that must be available to put this payment back (only cash payments OUT count). */
    fun restoreCashNeed(tx: PaymentTransaction): Double =
        if (isCashRelevant(tx) && tx.type == "OUT" && !tx.isPayable) tx.amount else 0.0

    fun batchCashDrop(txs: List<PaymentTransaction>): Double = txs.sumOf { deleteCashDrop(it) }

    fun cashShortIssue(need: Double, available: Double, what: String): String {
        if (need <= 0.0 || available - need >= -0.0001) return ""
        return "$what would take ${InputGuards.fmtNum(InputGuards.round2(need))} out of cash in hand, " +
            "but only ${InputGuards.fmtNum(InputGuards.round2(max(0.0, available)))} is available. " +
            "Record the money in first."
    }

    /** Same figure the Payments dashboard shows as "Net cash in hand" (all time). */
    internal fun availableCash(snapshot: WorkspaceSnapshot): Double =
        FinanceEngine.calculateEconomicHealth(snapshot, "all").cashInHand

    private fun plural(n: Int, word: String) = "$n $word${if (n != 1) "s" else ""}"

    // ---- Guards ------------------------------------------------------------------------

    fun paymentTxDeleteBlockReason(snapshot: WorkspaceSnapshot, tx: PaymentTransaction): String {
        if (tx.isMerged) return "Merged opening balance records cannot be deleted."
        if (tx.isTransfer) return "" // transfers are removed as a pair; no cash impact
        val drop = deleteCashDrop(tx)
        if (drop <= 0.0) return ""
        return cashShortIssue(drop, availableCash(snapshot), "Deleting this payment")
    }

    fun entityDeleteBlockReason(snapshot: WorkspaceSnapshot, entityId: String, name: String = ""): String {
        if (entityId.isBlank()) return ""
        val txs = snapshot.transactions.filter { it.deletedAt == null && it.entityId == entityId }
        val merged = txs.filter { it.isMerged }
        if (merged.isNotEmpty()) {
            return "This entity has ${plural(merged.size, "merged opening balance record")} which cannot be deleted. " +
                "Unmerge or settle the opening balance first."
        }
        val normal = txs.filter { !it.isTransfer }
        val drop = batchCashDrop(normal)
        if (drop <= 0.0) return ""
        val label = name.ifBlank { "this entity" }
        return cashShortIssue(drop, availableCash(snapshot), "Deleting $label and its payment history")
    }

    fun expenseDeleteBlockReason(snapshot: WorkspaceSnapshot, expenseId: String): String {
        if (expenseId.isBlank()) return ""
        val linked = snapshot.transactions.filter { it.deletedAt == null && it.expenseId == expenseId }
        if (linked.isEmpty()) return ""
        val merged = linked.filter { it.isMerged }
        if (merged.isNotEmpty()) {
            val many = merged.size != 1
            return "${plural(merged.size, "payment record")} linked to this expense " +
                "${if (many) "are" else "is a"} merged opening balance${if (many) "s" else ""} and cannot be deleted."
        }
        val drop = batchCashDrop(linked.filter { !it.isTransfer })
        if (drop <= 0.0) return ""
        return cashShortIssue(drop, availableCash(snapshot), "Deleting this expense and reversing its linked payments")
    }

    /** Single entry point used by the view model for every soft-delete. */
    fun deleteBlockReason(snapshot: WorkspaceSnapshot, collection: String, docId: String): String =
        when (collection) {
            "transactions" ->
                snapshot.transactions.firstOrNull { it.id == docId && it.deletedAt == null }
                    ?.let { paymentTxDeleteBlockReason(snapshot, it) }.orEmpty()
            "expenses" -> expenseDeleteBlockReason(snapshot, docId)
            "entities" -> snapshot.entities.firstOrNull { it.id == docId }
                ?.let { entityDeleteBlockReason(snapshot, it.id, it.name) }.orEmpty()
            else -> ""
        }
}
