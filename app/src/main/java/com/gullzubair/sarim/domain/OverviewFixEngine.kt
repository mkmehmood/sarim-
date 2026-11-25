package com.gullzubair.sarim.domain

import com.gullzubair.sarim.data.model.CustomerRecord
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * 1:1 Kotlin port of web `link-repair.js` and `overview-fix.js`.
 */
object OverviewFixEngine {

    val LINK_CODES = setOf("col_unapplied", "link_orphan")

    // --- Link Repair (link-repair.js) ---

    data class RepairSummary(
        val updatedRecords: Map<String, SalesRecord> = emptyMap(),
        val changedIds: List<String> = emptyList(),
        val restored: Int = 0,
        val dropped: Int = 0,
        val applied: Int = 0,
        val cleared: Int = 0
    ) {
        val text: String
            get() {
                val parts = mutableListOf<String>()
                if (cleared > 0) parts.add("$cleared stale payment link${if (cleared == 1) "" else "s"} removed")
                if (restored > 0) parts.add("$restored lost link${if (restored == 1) "" else "s"} restored")
                if (applied > 0) parts.add("cash applied to $applied sale${if (applied == 1) "" else "s"}")
                return if (parts.isNotEmpty()) parts.joinToString(", ") else "Nothing needed changing"
            }
    }

    private fun defaultGross(
        s: SalesRecord,
        customers: List<CustomerRecord>,
        stores: List<StoreConfig>
    ): Double {
        val locked = FinanceEngine.lockedSaleValue(s)
        return locked ?: FinanceEngine.effectiveSaleValue(s, customers, stores)
    }

    private fun customerSales(
        records: List<SalesRecord>,
        kind: String,
        col: SalesRecord
    ): List<SalesRecord> {
        val custKey = col.customerName.trim().lowercase(Locale.ROOT)
        return records.filter { s ->
            s.deletedAt == null &&
                !FinanceEngine.isCollectionRow(s) &&
                !s.isMerged &&
                s.customerName.trim().lowercase(Locale.ROOT) == custKey &&
                if (kind == "rep") {
                    s.salesRep == col.salesRep
                } else {
                    (s.currentRepProfile == "admin" || s.currentRepProfile.isBlank()) &&
                        (s.transactionType == "OLD_DEBT" || s.salesRep.isBlank() || s.salesRep == "NONE")
                }
        }
    }

    private fun sortForCollection(list: List<SalesRecord>): List<SalesRecord> =
        list.sortedWith(
            compareBy<SalesRecord> { if (it.transactionType == "OLD_DEBT") 1 else 0 }
                .thenByDescending { it.date }
                .thenByDescending { it.timestamp }
        )

    private fun collectionEligible(list: List<SalesRecord>, colDate: String): List<SalesRecord> {
        val cut = colDate.take(10)
        return list.filter { s ->
            s.transactionType == "OLD_DEBT" || cut.isBlank() || s.date.isBlank() || s.date.take(10) <= cut
        }
    }

    private data class PlannedAlloc(val saleId: String, val amount: Double, val full: Boolean)

    private fun planCollectionAllocation(amount: Double, dues: List<Pair<String, Double>>): List<PlannedAlloc> {
        var left = InputGuards.round2(max(0.0, amount))
        val out = mutableListOf<PlannedAlloc>()
        for ((id, rawDue) in dues) {
            if (left <= 0.004) break
            val due = InputGuards.round2(rawDue)
            if (due <= 0.004) continue
            if (left >= due - 0.004) {
                out.add(PlannedAlloc(id, due, true))
                left = InputGuards.round2(max(0.0, left - due))
            } else {
                out.add(PlannedAlloc(id, left, false))
                left = 0.0
            }
        }
        return out
    }

    private fun applyCollectionAllocToSale(sale: SalesRecord, alloc: PlannedAlloc, cid: String): SalesRecord {
        val nextAllocs = sale.collectionAllocs + (cid to InputGuards.round2(alloc.amount))
        val nextFull = sale.collectionAllocFull + (cid to alloc.full)
        return if (alloc.full) {
            sale.copy(
                collectionAllocs = nextAllocs,
                collectionAllocFull = nextFull,
                creditReceived = true,
                creditReceivedManually = true,
                partialPaymentReceived = if (sale.transactionType == "OLD_DEBT") {
                    InputGuards.round2(sale.partialPaymentReceived + alloc.amount)
                } else sale.partialPaymentReceived
            )
        } else {
            sale.copy(
                collectionAllocs = nextAllocs,
                collectionAllocFull = nextFull,
                partialPaymentReceived = InputGuards.round2(sale.partialPaymentReceived + alloc.amount)
            )
        }
    }

    private fun revertCollectionAllocFromSale(sale: SalesRecord, cid: String): Pair<SalesRecord, Boolean> {
        val amt = sale.collectionAllocs[cid] ?: return sale to false
        val wasFull = sale.collectionAllocFull[cid] == true
        val nextAllocs = sale.collectionAllocs - cid
        val nextFull = sale.collectionAllocFull - cid
        val updated = if (wasFull) {
            sale.copy(
                collectionAllocs = nextAllocs,
                collectionAllocFull = nextFull,
                creditReceived = false,
                creditReceivedManually = false,
                partialPaymentReceived = if (sale.transactionType == "OLD_DEBT") {
                    InputGuards.round2(max(0.0, sale.partialPaymentReceived - amt))
                } else sale.partialPaymentReceived
            )
        } else {
            sale.copy(
                collectionAllocs = nextAllocs,
                collectionAllocFull = nextFull,
                partialPaymentReceived = InputGuards.round2(max(0.0, sale.partialPaymentReceived - amt))
            )
        }
        return updated to true
    }

    private fun splitCollection(col: SalesRecord, allocs: List<PlannedAlloc>): SalesRecord {
        val amount = if (col.collectedAmount > 0.0 || col.isCollection) col.collectedAmount else col.totalValue
        if (allocs.isEmpty()) {
            return col.copy(
                collectedAmount = amount,
                allocations = emptyMap(),
                allocationFull = emptyMap(),
                expiredAllocated = 0.0,
                totalValue = amount,
                profit = amount
            )
        }
        val allocated = allocs.sumOf { it.amount }
        val cashPaid = min(amount, allocated)
        val cashLeft = InputGuards.round2(amount - cashPaid)
        val creditPaid = InputGuards.round2(max(0.0, allocated - cashPaid))
        return col.copy(
            collectedAmount = amount,
            allocations = allocs.associate { it.saleId to it.amount },
            allocationFull = allocs.associate { it.saleId to it.full },
            totalValue = cashLeft,
            profit = cashLeft,
            expiredAllocated = creditPaid
        )
    }

    fun repairOrphans(records: List<SalesRecord>, targetSaleIds: Set<String>? = null): RepairSummary {
        val live = records.filter { it.deletedAt == null }
        val liveCols = live.filter { FinanceEngine.isCollectionRow(it) }.map { it.id }.toSet()
        val updated = LinkedHashMap<String, SalesRecord>()
        var cleared = 0

        for (r in live) {
            if (FinanceEngine.isCollectionRow(r) || r.collectionAllocs.isEmpty()) continue
            if (targetSaleIds != null && r.id !in targetSaleIds) continue
            val orphanCids = r.collectionAllocs.keys.filter { it !in liveCols }
            if (orphanCids.isEmpty()) continue
            var cur = r
            for (cid in orphanCids) {
                val (next, ok) = revertCollectionAllocFromSale(cur, cid)
                cur = next
                if (ok) cleared++
            }
            if (cur != r) updated[cur.id] = cur
        }
        return RepairSummary(
            updatedRecords = updated,
            changedIds = updated.keys.toList(),
            cleared = cleared
        )
    }

    fun repairCollection(
        records: List<SalesRecord>,
        colId: String,
        kind: String = "sales",
        customers: List<CustomerRecord> = emptyList(),
        stores: List<StoreConfig> = emptyList()
    ): RepairSummary {
        val mutable = records.filter { it.deletedAt == null }.associateBy { it.id }.toMutableMap()
        val colOrig = mutable[colId]?.takeIf { FinanceEngine.isCollectionRow(it) } ?: return RepairSummary()
        var col = colOrig
        val hasClaims = col.allocations.isNotEmpty()
        val honor = FinanceEngine.allocHonor(mutable.values.toList())
        if (!hasClaims && FinanceEngine.collectionUnapplied(col, honor) <= 0.01) {
            return RepairSummary()
        }
        if (!hasClaims) {
            col = splitCollection(col, emptyList())
        }
        val mine = customerSales(mutable.values.toList(), kind, col)
        val liveIds = mutable.keys
        fun confirmedSum(s: SalesRecord): Double =
            s.collectionAllocs.entries.sumOf { (cid, amt) -> if (cid in liveIds) amt else 0.0 }

        val kept = mutableListOf<PlannedAlloc>()
        val changed = LinkedHashMap<String, SalesRecord>()
        var restored = 0
        var dropped = 0
        var applied = 0
        var droppedAmount = 0.0

        val initialClaims = if (hasClaims) {
            col.allocations.entries.map { (sid, amt) ->
                PlannedAlloc(sid, amt, col.allocationFull[sid] == true)
            }
        } else emptyList()

        for (claim in initialClaims) {
            val sale = mine.firstOrNull { it.id == claim.saleId }?.let { mutable[it.id] ?: it }
            val hasMirror = sale != null && col.id in sale.collectionAllocs
            if (hasMirror) {
                kept.add(claim)
                continue
            }
            if (sale != null) {
                val paid = if (sale.creditReceived) defaultGross(sale, customers, stores) else sale.partialPaymentReceived
                if (paid + 0.01 >= confirmedSum(sale) + claim.amount) {
                    val patchedSale = sale.copy(
                        collectionAllocs = sale.collectionAllocs + (col.id to InputGuards.round2(claim.amount)),
                        collectionAllocFull = sale.collectionAllocFull + (col.id to claim.full)
                    )
                    mutable[patchedSale.id] = patchedSale
                    changed[patchedSale.id] = patchedSale
                    restored++
                    kept.add(claim)
                    continue
                }
            }
            droppedAmount = InputGuards.round2(droppedAmount + claim.amount)
            dropped++
        }

        if (dropped > 0 || !hasClaims) {
            col = splitCollection(col, kept)
            val amount = if (col.collectedAmount > 0.0 || col.isCollection) col.collectedAmount else col.totalValue
            val held = InputGuards.round2(col.totalValue + max(0.0, col.expiredValue - col.expiredAllocated))
            val budget = if (hasClaims) min(droppedAmount, held) else held
            if (budget > 0.004) {
                val open = sortForCollection(
                    collectionEligible(
                        mine.map { mutable[it.id] ?: it }.filter { s ->
                            (s.paymentType == "CREDIT" || s.transactionType == "OLD_DEBT") &&
                                !s.creditReceived &&
                                s.usedInCalcId.isBlank() &&
                                !(s.isRepTransfer || (s.isTransfer && s.transferFrom.isNotBlank()))
                        },
                        col.date
                    )
                )
                val dues = open.map { s ->
                    s.id to FinanceEngine.debtDelta(s, defaultGross(s, customers, stores))
                }
                val plan = planCollectionAllocation(budget, dues)
                val next = kept.toMutableList()
                for (a in plan) {
                    val sale = mutable[a.saleId] ?: continue
                    val updatedSale = applyCollectionAllocToSale(sale, a, col.id)
                    mutable[updatedSale.id] = updatedSale
                    changed[updatedSale.id] = updatedSale
                    next.add(a)
                    applied++
                }
                if (plan.isNotEmpty()) {
                    val fresh = next.sumOf { it.amount }
                    val candidate = splitCollection(col, next)
                    col = if (fresh > amount + col.expiredValue + 0.01) splitCollection(col, kept) else candidate
                }
            }
            changed[col.id] = col
        } else if (restored > 0) {
            changed[col.id] = col
        }

        return RepairSummary(
            updatedRecords = changed,
            changedIds = changed.keys.toList(),
            restored = restored,
            dropped = dropped,
            applied = applied
        )
    }

    fun repairStore(
        records: List<SalesRecord>,
        kind: String = "sales",
        onlyIds: Set<String>? = null,
        orphanIds: Set<String>? = null,
        customers: List<CustomerRecord> = emptyList(),
        stores: List<StoreConfig> = emptyList()
    ): RepairSummary {
        val working = records.associateBy { it.id }.toMutableMap()
        val allChanged = LinkedHashMap<String, SalesRecord>()
        var restored = 0
        var dropped = 0
        var applied = 0
        var cleared = 0

        if (onlyIds == null || orphanIds != null) {
            val orphans = repairOrphans(working.values.toList(), orphanIds)
            orphans.updatedRecords.forEach { (id, rec) ->
                working[id] = rec
                allChanged[id] = rec
            }
            cleared = orphans.cleared
        }

        if (orphanIds == null) {
            val liveList = working.values.filter { it.deletedAt == null }
            val honor = FinanceEngine.allocHonor(liveList)
            val flaggedCols = liveList
                .filter { r ->
                    FinanceEngine.isCollectionRow(r) &&
                        FinanceEngine.collectionUnapplied(r, honor) > 0.01 &&
                        (onlyIds == null || r.id in onlyIds)
                }
                .sortedWith(compareBy<SalesRecord> { it.date }.thenBy { it.timestamp })

            for (col in flaggedCols) {
                val res = repairCollection(working.values.toList(), col.id, kind, customers, stores)
                res.updatedRecords.forEach { (id, rec) ->
                    working[id] = rec
                    allChanged[id] = rec
                }
                restored += res.restored
                dropped += res.dropped
                applied += res.applied
            }
        }

        return RepairSummary(
            updatedRecords = allChanged,
            changedIds = allChanged.keys.toList(),
            restored = restored,
            dropped = dropped,
            applied = applied,
            cleared = cleared
        )
    }

    // --- Interactive Fix Modal (overview-fix.js) ---

    data class FixSelectOption(val value: String, val label: String)

    data class FixField(
        val key: String,
        val label: String,
        val type: String, // "number" | "date" | "select" | "check"
        val defaultValue: String,
        val defaultChecked: Boolean = false,
        val min: Double = 0.0,
        val max: Double = InputGuards.MAX_AMOUNT,
        val options: List<FixSelectOption> = emptyList()
    )

    data class FixPlan(
        val fields: List<FixField> = emptyList(),
        val notes: List<String> = emptyList(),
        val newId: Boolean = false,
        val deletable: Boolean = false,
        val repair: Boolean = false
    )

    data class FixValidationResult(
        val ok: Boolean,
        val message: String = "",
        val values: Map<String, Any> = emptyMap()
    )

    private fun asNum(v: Any?): Double = when (v) {
        is Number -> v.toDouble().let { if (it.isFinite()) it else 0.0 }
        is String -> v.trim().replace(",", "").toDoubleOrNull()?.let { if (it.isFinite()) it else 0.0 } ?: 0.0
        else -> 0.0
    }

    private fun isNotNumber(v: Any?): Boolean {
        if (v == null || v.toString().isBlank()) return false
        return asNum(v).let { false } || (v !is Number && v.toString().trim().replace(",", "").toDoubleOrNull()?.isFinite() != true)
    }

    private val NUMERIC_KEYS = mapOf(
        "sales" to listOf("quantity", "totalValue", "totalCost", "profit", "unitPrice"),
        "rep" to listOf("quantity", "totalValue", "totalCost", "profit", "unitPrice"),
        "production" to listOf("net", "totalSale", "totalCost", "profit"),
        "transactions" to listOf("amount"),
        "expenses" to listOf("amount"),
        "inventory" to listOf("quantity", "cost")
    )

    private val FIELD_LABELS = mapOf(
        "quantity" to "Quantity",
        "totalValue" to "Total value",
        "totalCost" to "Total cost",
        "profit" to "Profit",
        "unitPrice" to "Unit price",
        "net" to "Net weight (kg)",
        "totalSale" to "Total sale",
        "amount" to "Amount",
        "cost" to "Cost per unit",
        "date" to "Date"
    )

    fun planFields(
        col: String,
        rec: Map<String, Any>,
        codes: List<String>,
        today: String,
        snapshot: WorkspaceSnapshot
    ): FixPlan {
        val fields = mutableListOf<FixField>()
        val notes = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        fun add(f: FixField) {
            if (seen.add(f.key)) fields.add(f)
        }
        fun numVal(k: String): String {
            val raw = rec[k] ?: return ""
            val n = asNum(raw)
            return if (raw.toString().isBlank()) "" else InputGuards.fmtNum(n, 2).replace(",", "")
        }

        if (codes.any { it in LINK_CODES }) {
            if ("col_unapplied" in codes) {
                notes.add("This collection recorded more cash than the sales confirm. Repair keeps the part the sales confirm, then applies the rest to this customer’s unpaid sales, newest first. Any cash left over stays as customer credit.")
            }
            if ("link_orphan" in codes) {
                notes.add("This sale is marked as paid by a collection that was deleted. Repair takes that payment off the sale so it is owed again.")
            }
            return FixPlan(fields = fields, notes = notes, repair = true)
        }

        val v = asNum(rec["totalValue"])
        val c = asNum(rec["totalCost"])
        val q = asNum(rec["quantity"])
        var newId = false

        if ("sale_profit_mismatch" in codes) {
            add(FixField("profit", FIELD_LABELS["profit"]!!, "number", InputGuards.round2(v - c).toString(), min = -InputGuards.MAX_AMOUNT))
            notes.add("Profit should equal total value minus total cost.")
        }
        if ("sale_total_mismatch" in codes) {
            add(FixField("unitPrice", FIELD_LABELS["unitPrice"]!!, "number", numVal("unitPrice"), min = 0.01))
            add(FixField("totalValue", FIELD_LABELS["totalValue"]!!, "number", numVal("totalValue"), min = 0.0))
            val expected = InputGuards.round2(asNum(rec["unitPrice"]) * q)
            notes.add("Quantity times unit price is ${InputGuards.fmtNum(expected)}, but the total value is ${InputGuards.fmtNum(v)}.")
        }
        if ("sale_no_cost" in codes) {
            add(FixField("totalCost", FIELD_LABELS["totalCost"]!!, "number", "", min = 0.01))
        }
        if ("sale_loss" in codes) {
            add(FixField("totalValue", FIELD_LABELS["totalValue"]!!, "number", numVal("totalValue"), min = 0.0))
            add(FixField("totalCost", FIELD_LABELS["totalCost"]!!, "number", numVal("totalCost"), min = 0.01))
        }
        if ("sale_bad_qty" in codes) {
            add(FixField("quantity", FIELD_LABELS["quantity"]!!, "number", "", min = 0.001, max = InputGuards.MAX_QUANTITY))
        }
        if ("sale_negative_value" in codes) {
            add(FixField("totalValue", FIELD_LABELS["totalValue"]!!, "number", "", min = 0.0))
        }
        if ("sale_cash_unreceived" in codes) {
            add(FixField("creditReceived", "Cash has been received", "check", "true", defaultChecked = true))
        }
        if ("bad_date" in codes || "future_date" in codes) {
            add(FixField("date", FIELD_LABELS["date"]!!, "date", today))
        }
        if ("not_a_number" in codes) {
            NUMERIC_KEYS[col].orEmpty().filter { isNotNumber(rec[it]) }.forEach { k ->
                add(FixField(k, FIELD_LABELS[k] ?: k, "number", "", min = if (k == "profit") -InputGuards.MAX_AMOUNT else 0.0))
            }
        }
        if ("prod_profit_mismatch" in codes) {
            val ts = asNum(rec["totalSale"])
            val tc = asNum(rec["totalCost"])
            add(FixField("profit", FIELD_LABELS["profit"]!!, "number", InputGuards.round2(ts - tc).toString(), min = -InputGuards.MAX_AMOUNT))
            notes.add("Profit should equal total sale minus total cost.")
        }
        if ("prod_bad_net" in codes) {
            add(FixField("net", FIELD_LABELS["net"]!!, "number", "", min = 0.001, max = InputGuards.MAX_QUANTITY))
        }
        if ("prod_no_formula" in codes && snapshot.formulas.isNotEmpty()) {
            add(
                FixField(
                    key = "formulaId",
                    label = "Formula",
                    type = "select",
                    defaultValue = snapshot.formulas.firstOrNull()?.id ?: "",
                    options = snapshot.formulas.map { FixSelectOption(it.id, it.name.ifBlank { "Untitled" }) }
                )
            )
            notes.add(
                if (asNum(rec["formulaUnits"]) > 0.0) "The cost and profit are recalculated from the formula."
                else "Only the formula is set; the saved cost stays."
            )
        }
        if ("txn_bad_type" in codes) {
            add(
                FixField(
                    key = "type",
                    label = "Type",
                    type = "select",
                    defaultValue = "IN",
                    options = listOf(FixSelectOption("IN", "Cash in"), FixSelectOption("OUT", "Cash out"))
                )
            )
        }
        if ("txn_bad_amount" in codes) {
            add(FixField("amount", FIELD_LABELS["amount"]!!, "number", "", min = 0.0))
        }
        if ("exp_bad_amount" in codes) {
            add(FixField("amount", FIELD_LABELS["amount"]!!, "number", "", min = 0.01))
        }
        val liveEntities = snapshot.entities.filter { it.deletedAt == null }
        if ("txn_missing_entity" in codes && liveEntities.isNotEmpty()) {
            add(
                FixField(
                    key = "entityId",
                    label = "Account",
                    type = "select",
                    defaultValue = liveEntities.first().id,
                    options = liveEntities.map { FixSelectOption(it.id, it.name.ifBlank { "Unnamed" }) }
                )
            )
        }
        if ("inv_negative_qty" in codes) {
            add(FixField("quantity", FIELD_LABELS["quantity"]!!, "number", "0", min = 0.0))
        }
        if ("inv_bad_cost" in codes) {
            add(FixField("cost", FIELD_LABELS["cost"]!!, "number", "", min = 0.01))
        }
        if ("duplicate_id" in codes) {
            newId = true
            notes.add("This record shares its id with another one. Saving gives it a new id.")
        }
        if ("duplicate_record" in codes) {
            notes.add("This looks like the same record entered twice. Delete the extra copy.")
        }

        val deletable = col != "inventory" && ("duplicate_record" in codes || (fields.isEmpty() && !newId))
        return FixPlan(fields = fields, notes = notes, newId = newId, deletable = deletable)
    }

    fun validateValues(
        fields: List<FixField>,
        raw: Map<String, Any?>,
        today: String
    ): FixValidationResult {
        val values = LinkedHashMap<String, Any>()
        for (f in fields) {
            if (f.type == "check") {
                val b = raw[f.key] == true || raw[f.key]?.toString() == "true"
                values[f.key] = b
                continue
            }
            val text = raw[f.key]?.toString()?.trim().orEmpty()
            if (f.type == "select") {
                if (f.options.none { it.value == text }) {
                    return FixValidationResult(false, "Choose a ${f.label.lowercase(Locale.ROOT)}.")
                }
                values[f.key] = text
                continue
            }
            if (f.type == "date") {
                if (text.isBlank()) return FixValidationResult(false, "Pick a date.")
                if (!DateUtils.isValidPastOrTodayDate(text, today)) {
                    return FixValidationResult(false, "Enter a valid past or today date (YYYY-MM-DD).")
                }
                values[f.key] = text
                continue
            }
            val cleaned = text.replace(",", "")
            val parsed = cleaned.toDoubleOrNull()
                ?: return FixValidationResult(false, "Enter a valid number for ${f.label}.")
            if (!parsed.isFinite() || parsed < f.min || parsed > f.max) {
                return FixValidationResult(false, "${f.label} must be between ${InputGuards.fmtNum(f.min)} and ${InputGuards.fmtNum(f.max)}.")
            }
            values[f.key] = InputGuards.round2(parsed)
        }
        return FixValidationResult(true, values = values)
    }

    fun applyValues(
        col: String,
        rec: Map<String, Any>,
        values: Map<String, Any>,
        snapshot: WorkspaceSnapshot
    ): Map<String, Any> {
        val next = rec.toMutableMap()
        values.forEach { (k, v) ->
            when (k) {
                "formulaId" -> {
                    val f = snapshot.formulas.firstOrNull { it.id == v.toString() } ?: return@forEach
                    next["formulaId"] = f.id
                    next["formulaName"] = f.name.ifBlank { "Untitled" }
                    val units = asNum(rec["formulaUnits"])
                    if (units > 0.0) {
                        val desc = FormulaEngine.describeFormula(f, snapshot.formulas, snapshot.inventory)
                        val total = InputGuards.round2(desc.perUnit * units)
                        val net = asNum(rec["net"])
                        next["formulaCost"] = total
                        next["totalCost"] = total
                        next["cp"] = if (net > 0.0) InputGuards.round2(total / net) else 0.0
                        next["profit"] = InputGuards.round2(asNum(rec["totalSale"]) - total)
                    }
                }
                "entityId" -> {
                    val e = snapshot.entities.firstOrNull { it.id == v.toString() } ?: return@forEach
                    next["entityId"] = e.id
                    next["entityName"] = e.name.ifBlank { next["entityName"]?.toString().orEmpty() }
                }
                else -> next[k] = v
            }
        }
        if ((col == "sales" || col == "rep") &&
            ("totalValue" in values || "totalCost" in values) &&
            "profit" !in values
        ) {
            next["profit"] = InputGuards.round2(asNum(next["totalValue"]) - asNum(next["totalCost"]))
        }
        return next
    }
}
