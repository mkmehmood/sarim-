package com.gullzubair.sarim.domain

import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.DeletionTombstone
import com.gullzubair.sarim.data.model.WorkspaceSnapshot

/**
 * Recycle-bin recovery guards. Port of the web app's `getRecoverBlockReason` and
 * `getRecoverLinkBlockReason` (link-guards.js) for the checks that don't need extra state.
 * Returns "" when the record can be recovered, otherwise a user-facing reason.
 *
 * NOT ported (see docs/LINK_GUARDS.md): re-linking parents on recovery, recovery ID remapping,
 * stock re-deduction, calculator restore planning and the by-date stock overdraw check.
 */
object RecoveryGuards {
    /** Stamped into the tombstone snapshot when a record is deleted from its own tab (not via the calculator). */
    const val DELETE_ORIGIN_FIELD = "_deleteOrigin"
    const val ORIGIN_PROD_TAB = "prod-tab"

    private const val CALC_MSG_SUFFIX = "Recover is not allowed; enter the calculation again."

    fun recoverBlockReason(snapshot: WorkspaceSnapshot, tomb: DeletionTombstone): String {
        val docId = tomb.recordId.ifBlank { tomb.id }
        val raw = runCatching { WorkspaceJsonCodec.jsonToMap(tomb.snapshotJson) }.getOrNull() ?: return ""
        val fromProdTab = raw[DELETE_ORIGIN_FIELD] == ORIGIN_PROD_TAB
        return runCatching {
            when (tomb.collection) {
                "sales", "rep" -> saleReason(snapshot, tomb.collection, docId, raw)
                "production" -> {
                    val p = WorkspaceJsonCodec.mapToProduction(docId, raw)
                    if (p.isReturn && p.returnedBy.isNotBlank() && !fromProdTab) {
                        "This stock return was created by a calculator record. $CALC_MSG_SUFFIX"
                    } else ""
                }
                "returns" -> {
                    val r = WorkspaceJsonCodec.mapToReturn(docId, raw)
                    if (r.seller.isNotBlank() && !fromProdTab) {
                        "This return log was created by a calculator record. $CALC_MSG_SUFFIX"
                    } else ""
                }
                "inventory" -> {
                    val m = WorkspaceJsonCodec.mapToInventory(docId, raw)
                    if (m.supplierId.isNotBlank() && snapshot.entities.none { it.id == m.supplierId && it.deletedAt == null }) {
                        "This material was linked to ${m.supplierName.ifBlank { "a supplier" }} who is no longer in your payments. " +
                            "Recover that supplier first, then recover the material."
                    } else ""
                }
                "transactions" -> txReason(snapshot, docId, raw)
                else -> ""
            }
        }.getOrDefault("")
    }

    private fun saleReason(snapshot: WorkspaceSnapshot, collection: String, docId: String, raw: Map<String, Any>): String {
        val s = WorkspaceJsonCodec.mapToSale(docId, raw)
        // Rep stock transfers are created by calculator records.
        if (collection == "sales" && (s.isRepTransfer || (s.isTransfer && s.transferFrom.isNotBlank()))) {
            return "This is a rep stock transfer created by a calculator record. $CALC_MSG_SUFFIX"
        }
        val live = if (collection == "rep") snapshot.repRecords else snapshot.sales
        if (s.paymentType == "PARTIAL_PAYMENT" && s.relatedSaleId.isNotBlank()) {
            val parent = live.firstOrNull { it.id == s.relatedSaleId && it.deletedAt == null }
                ?: return "The credit sale this payment belongs to is not in your records. " +
                    "Recover that sale first, then recover the payment."
            if (parent.paymentType != "CREDIT") return "The sale this payment belongs to is no longer a credit sale."
            val next = InputGuards.round2(parent.partialPaymentReceived + s.totalValue)
            if (parent.totalValue > 0.0 && next > parent.totalValue + 0.01) {
                return "Recovering this payment would put ${InputGuards.fmtNum(next)} against a sale worth " +
                    "${InputGuards.fmtNum(parent.totalValue)}. The sale was changed after the payment was deleted."
            }
        }
        if (s.paymentType == "COLLECTION" && s.allocations.isNotEmpty()) {
            for (saleId in s.allocations.keys) {
                val sale = live.firstOrNull { it.id == saleId && it.deletedAt == null }
                    ?: return "A sale this collection paid is no longer in your records. " +
                        "Recover that sale first, then recover the collection."
                if (sale.creditReceived) {
                    return "A sale this collection paid has since been settled another way, so the collection cannot be recovered."
                }
            }
        }
        return ""
    }

    private fun txReason(snapshot: WorkspaceSnapshot, docId: String, raw: Map<String, Any>): String {
        val tx = WorkspaceJsonCodec.mapToTransaction(docId, raw)

        if (tx.isTransfer && tx.transferPairId.isNotBlank()) {
            val otherLive = snapshot.transactions.any {
                it.deletedAt == null && it.transferPairId == tx.transferPairId && it.id != docId
            }
            val otherInBin = snapshot.deletions.any { t ->
                t.collection == "transactions" && t.recordId.ifBlank { t.id } != docId &&
                    runCatching { WorkspaceJsonCodec.jsonToMap(t.snapshotJson)["transferPairId"] == tx.transferPairId }
                        .getOrDefault(false)
            }
            if (!otherLive && !otherInBin) {
                return "The other side of this transfer was permanently deleted. Restoring only one side " +
                    "would unbalance your cash, so this record cannot be recovered."
            }
        }

        if (tx.entityId.isNotBlank() && snapshot.entities.none { it.id == tx.entityId && it.deletedAt == null }) {
            val who = if (tx.entityName.isNotBlank()) "\"${tx.entityName}\"" else "entity"
            return "The $who this payment belongs to no longer exists. Recover that entity first."
        }

        // Restoring a cash payment OUT takes money back out of the till.
        val need = LinkGuards.restoreCashNeed(tx)
        if (need > 0.0) {
            return LinkGuards.cashShortIssue(need, LinkGuards.availableCash(snapshot), "Recovering this payment")
        }
        return ""
    }
}
