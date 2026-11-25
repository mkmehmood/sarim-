package com.gullzubair.sarim.data.local

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.gullzubair.sarim.data.model.CalculatorSettlement
import com.gullzubair.sarim.data.model.CustomerRecord
import com.gullzubair.sarim.data.model.DailyLedgerEntry
import com.gullzubair.sarim.data.model.EntityRecord
import com.gullzubair.sarim.data.model.FactoryRecord
import com.gullzubair.sarim.data.model.FormulaConfig
import com.gullzubair.sarim.data.model.InventoryItem
import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.data.model.PendingApproval
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.RepRecord
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.UserProfile
import com.gullzubair.sarim.data.model.WorkspaceInvite
import com.gullzubair.sarim.data.model.WorkspaceSettings
import org.json.JSONArray
import org.json.JSONObject

object WorkspaceJsonCodec {

    fun stringListToJson(list: List<String>): String = JSONArray(list).toString()

    fun jsonToStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { i -> arr.optString(i) }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    fun anyToJsonValue(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is FieldValue -> System.currentTimeMillis()
        is Timestamp -> value.seconds * 1000L + (value.nanoseconds / 1_000_000L)
        is Map<*, *> -> JSONObject().apply {
            value.forEach { (k, v) -> if (k != null) put(k.toString(), anyToJsonValue(v) ?: JSONObject.NULL) }
        }
        is Iterable<*> -> JSONArray().apply {
            value.forEach { put(anyToJsonValue(it) ?: JSONObject.NULL) }
        }
        is Boolean, is Int, is Long, is Double, is String -> value
        is Number -> value.toDouble()
        else -> value.toString()
    }

    fun mapToJson(map: Map<String, Any?>): String = JSONObject().apply {
        map.forEach { (k, v) -> put(k, anyToJsonValue(v) ?: JSONObject.NULL) }
    }.toString()

    fun jsonToFirestoreMap(json: String): Map<String, Any> {
        val obj = JSONObject(json)
        val out = LinkedHashMap<String, Any>()
        obj.keys().forEach { key ->
            val raw = obj.get(key)
            if (raw != JSONObject.NULL) {
                jsonValToKotlin(raw)?.let { out[key] = it }
            }
        }
        return out
    }

    private fun jsonValToKotlin(raw: Any?): Any? = when (raw) {
        null, JSONObject.NULL -> null
        is JSONObject -> {
            val map = LinkedHashMap<String, Any>()
            raw.keys().forEach { k ->
                jsonValToKotlin(raw.get(k))?.let { map[k] = it }
            }
            map
        }
        is JSONArray -> List(raw.length()) { idx -> jsonValToKotlin(raw.get(idx)) }.filterNotNull()
        is Int -> raw.toLong()
        else -> raw
    }

    private fun jsonToMapList(arr: JSONArray?): List<Map<String, Any>> {
        if (arr == null) return emptyList()
        val out = ArrayList<Map<String, Any>>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val m = LinkedHashMap<String, Any>()
            obj.keys().forEach { k ->
                jsonValToKotlin(obj.get(k))?.let { m[k] = it }
            }
            out.add(m)
        }
        return out
    }

    private fun tsToMs(ts: Timestamp?): Long =
        if (ts == null) 0L else ts.seconds * 1000L + (ts.nanoseconds / 1_000_000L)

    private fun msToTs(ms: Long): Timestamp? =
        if (ms <= 0L) null else Timestamp(ms / 1000L, ((ms % 1000L) * 1_000_000L).toInt())

    private fun JSONObject.putTs(key: String, ts: Timestamp?) {
        val ms = tsToMs(ts)
        if (ms > 0L) put(key, ms)
    }

    private fun JSONObject.optTs(key: String): Timestamp? = msToTs(optLong(key, 0L))

    private fun JSONObject.optNullableStr(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it != "null" }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key)

    private fun JSONObject.optStrList(key: String): List<String> {
        val arr = optJSONArray(key) ?: return emptyList()
        return List(arr.length()) { i -> arr.optString(i) }.filter { it.isNotEmpty() }
    }

    // --- Profile ---

    fun toEntity(p: UserProfile): CachedProfileEntity = CachedProfileEntity(
        uid = p.uid,
        email = p.email,
        displayName = p.displayName,
        workspaceOwnerUid = p.workspaceOwnerUid,
        role = p.role,
        rolesJson = stringListToJson(p.roles),
        approved = p.approved,
        inviteId = p.inviteId,
        updatedAtEpochMs = tsToMs(p.updatedAt).takeIf { it > 0L } ?: System.currentTimeMillis()
    )

    fun toProfile(e: CachedProfileEntity): UserProfile = UserProfile(
        id = e.uid,
        uid = e.uid,
        email = e.email,
        displayName = e.displayName,
        workspaceOwnerUid = e.workspaceOwnerUid,
        role = e.role,
        roles = jsonToStringList(e.rolesJson).ifEmpty { listOf(e.role) },
        approved = e.approved,
        inviteId = e.inviteId,
        createdAt = msToTs(e.updatedAtEpochMs),
        updatedAt = msToTs(e.updatedAtEpochMs)
    )

    // --- Models <-> JSON ---

    fun encodeSettings(s: WorkspaceSettings): String = JSONObject().apply {
        put("id", s.id.ifBlank { "preferences" })
        put("ownerUid", s.ownerUid)
        put("memberUids", JSONArray(s.memberUids))
        put("currencySymbol", s.currencySymbol)
        put("financialYearStartMonth", s.financialYearStartMonth)
        put("hideSensitiveMoney", s.hideSensitiveMoney)
        put("biometricLockEnabled", s.biometricLockEnabled)
        put("themeMode", s.themeMode)
        put("paymentChannels", JSONArray(s.paymentChannels))
        put("expenseCategories", JSONArray(s.expenseCategories))
        putTs("createdAt", s.createdAt)
        putTs("updatedAt", s.updatedAt)
    }.toString()

    fun decodeSettings(docId: String, json: String): WorkspaceSettings {
        val o = JSONObject(json)
        return WorkspaceSettings(
            id = o.optString("id", docId).ifBlank { docId },
            ownerUid = o.optString("ownerUid"),
            memberUids = o.optStrList("memberUids"),
            currencySymbol = o.optString("currencySymbol", "Rs").ifBlank { "Rs" },
            financialYearStartMonth = o.optLong("financialYearStartMonth", 7L),
            hideSensitiveMoney = o.optBoolean("hideSensitiveMoney", false),
            biometricLockEnabled = o.optBoolean("biometricLockEnabled", false),
            themeMode = o.optString("themeMode", "light").ifBlank { "light" },
            paymentChannels = o.optStrList("paymentChannels").ifEmpty { listOf("Cash", "Bank", "Easypaisa", "JazzCash") },
            expenseCategories = o.optStrList("expenseCategories").ifEmpty {
                listOf("Fuel", "Wages", "Rent", "Electricity", "Freight", "Maintenance", "Tea & Meals", "Office", "Other")
            },
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodeStore(s: StoreConfig): String = JSONObject().apply {
        put("id", s.id)
        put("ownerUid", s.ownerUid)
        put("memberUids", JSONArray(s.memberUids))
        put("storeKey", s.storeKey)
        put("name", s.name)
        put("formulaId", s.formulaId)
        put("salePrice", s.salePrice)
        put("active", s.active)
        putTs("createdAt", s.createdAt)
        putTs("updatedAt", s.updatedAt)
    }.toString()

    fun decodeStore(docId: String, json: String): StoreConfig {
        val o = JSONObject(json)
        return StoreConfig(
            id = docId,
            ownerUid = o.optString("ownerUid"),
            memberUids = o.optStrList("memberUids"),
            storeKey = o.optString("storeKey"),
            name = o.optString("name"),
            formulaId = o.optString("formulaId"),
            salePrice = o.optDouble("salePrice", 0.0),
            active = o.optBoolean("active", true),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodeFormula(f: FormulaConfig): String = JSONObject().apply {
        put("id", f.id)
        put("ownerUid", f.ownerUid)
        put("memberUids", JSONArray(f.memberUids))
        put("name", f.name)
        put("unitsPerBatch", f.unitsPerBatch)
        put("additionalCost", f.additionalCost)
        put("costAdjustment", f.costAdjustment)
        put("ingredients", anyToJsonValue(f.ingredients))
        put("savedMixes", anyToJsonValue(f.savedMixes))
        put("savedCorrections", anyToJsonValue(f.savedCorrections))
        putTs("createdAt", f.createdAt)
        putTs("updatedAt", f.updatedAt)
    }.toString()

    fun decodeFormula(docId: String, json: String): FormulaConfig {
        val o = JSONObject(json)
        return FormulaConfig(
            id = docId,
            ownerUid = o.optString("ownerUid"),
            memberUids = o.optStrList("memberUids"),
            name = o.optString("name"),
            unitsPerBatch = o.optDouble("unitsPerBatch", 1.0),
            additionalCost = o.optDouble("additionalCost", 0.0),
            costAdjustment = o.optDouble("costAdjustment", 0.0),
            ingredients = jsonToMapList(o.optJSONArray("ingredients")),
            savedMixes = jsonToMapList(o.optJSONArray("savedMixes")),
            savedCorrections = jsonToMapList(o.optJSONArray("savedCorrections")),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodeCustomer(c: CustomerRecord): String = JSONObject().apply {
        put("id", c.id)
        put("ownerUid", c.ownerUid)
        put("memberUids", JSONArray(c.memberUids))
        put("name", c.name)
        put("phone", c.phone)
        put("address", c.address)
        c.routeLat?.let { put("routeLat", it) }
        c.routeLng?.let { put("routeLng", it) }
        put("visitCount", c.visitCount)
        put("openingBalance", c.openingBalance)
        c.notes?.let { put("notes", it) }
        put("recycled", c.recycled)
        put("recycledAtEpoch", c.recycledAtEpoch)
        putTs("createdAt", c.createdAt)
        putTs("updatedAt", c.updatedAt)
    }.toString()

    fun decodeCustomer(docId: String, json: String): CustomerRecord {
        val o = JSONObject(json)
        return CustomerRecord(
            id = docId,
            ownerUid = o.optString("ownerUid"),
            memberUids = o.optStrList("memberUids"),
            name = o.optString("name"),
            phone = o.optString("phone"),
            address = o.optString("address"),
            routeLat = o.optNullableDouble("routeLat"),
            routeLng = o.optNullableDouble("routeLng"),
            visitCount = o.optLong("visitCount", 0L),
            openingBalance = o.optDouble("openingBalance", 0.0),
            notes = o.optNullableStr("notes"),
            recycled = o.optBoolean("recycled", false),
            recycledAtEpoch = o.optLong("recycledAtEpoch", 0L),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodeEntity(e: EntityRecord): String = JSONObject().apply {
        put("id", e.id)
        put("ownerUid", e.ownerUid)
        put("memberUids", JSONArray(e.memberUids))
        put("name", e.name)
        put("entityType", e.entityType)
        put("phone", e.phone)
        e.notes?.let { put("notes", it) }
        put("openingBalance", e.openingBalance)
        put("recycled", e.recycled)
        put("recycledAtEpoch", e.recycledAtEpoch)
        putTs("createdAt", e.createdAt)
        putTs("updatedAt", e.updatedAt)
    }.toString()

    fun decodeEntity(docId: String, json: String): EntityRecord {
        val o = JSONObject(json)
        return EntityRecord(
            id = docId,
            ownerUid = o.optString("ownerUid"),
            memberUids = o.optStrList("memberUids"),
            name = o.optString("name"),
            entityType = o.optString("entityType", "party"),
            phone = o.optString("phone"),
            notes = o.optNullableStr("notes"),
            openingBalance = o.optDouble("openingBalance", 0.0),
            recycled = o.optBoolean("recycled", false),
            recycledAtEpoch = o.optLong("recycledAtEpoch", 0L),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodeInventory(i: InventoryItem): String = JSONObject().apply {
        put("id", i.id)
        put("ownerUid", i.ownerUid)
        put("memberUids", JSONArray(i.memberUids))
        put("name", i.name)
        put("unitName", i.unitName)
        put("conversionFactor", i.conversionFactor)
        put("stockQty", i.stockQty)
        put("avgCostPerKg", i.avgCostPerKg)
        put("minThreshold", i.minThreshold)
        i.supplierName?.let { put("supplierName", it) }
        put("recycled", i.recycled)
        put("recycledAtEpoch", i.recycledAtEpoch)
        putTs("createdAt", i.createdAt)
        putTs("updatedAt", i.updatedAt)
    }.toString()

    fun decodeInventory(docId: String, json: String): InventoryItem {
        val o = JSONObject(json)
        return InventoryItem(
            id = docId,
            ownerUid = o.optString("ownerUid"),
            memberUids = o.optStrList("memberUids"),
            name = o.optString("name"),
            unitName = o.optString("unitName", "kg"),
            conversionFactor = o.optDouble("conversionFactor", 1.0),
            stockQty = o.optDouble("stockQty", 0.0),
            avgCostPerKg = o.optDouble("avgCostPerKg", 0.0),
            minThreshold = o.optDouble("minThreshold", 0.0),
            supplierName = o.optNullableStr("supplierName"),
            recycled = o.optBoolean("recycled", false),
            recycledAtEpoch = o.optLong("recycledAtEpoch", 0L),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodeProduction(p: ProductionRecord): String = JSONObject().apply {
        put("id", p.id)
        put("ownerUid", p.ownerUid)
        put("memberUids", JSONArray(p.memberUids))
        put("date", p.date)
        put("storeKey", p.storeKey)
        put("storeName", p.storeName)
        p.formulaId?.let { put("formulaId", it) }
        put("formulaUnits", p.formulaUnits)
        put("grossWeight", p.grossWeight)
        put("containerWeight", p.containerWeight)
        put("netWeight", p.netWeight)
        put("costPerKg", p.costPerKg)
        put("salePrice", p.salePrice)
        put("totalCost", p.totalCost)
        put("totalRevenue", p.totalRevenue)
        put("profit", p.profit)
        put("transfer", p.transfer)
        put("transferType", p.transferType)
        p.transferPairId?.let { put("transferPairId", it) }
        p.transferPeerStore?.let { put("transferPeerStore", it) }
        p.notes?.let { put("notes", it) }
        put("recycled", p.recycled)
        put("recycledAtEpoch", p.recycledAtEpoch)
        put("createdByUid", p.createdByUid)
        putTs("createdAt", p.createdAt)
        putTs("updatedAt", p.updatedAt)
    }.toString()

    fun decodeProduction(docId: String, json: String): ProductionRecord {
        val o = JSONObject(json)
        return ProductionRecord(
            id = docId,
            ownerUid = o.optString("ownerUid"),
            memberUids = o.optStrList("memberUids"),
            date = o.optString("date"),
            storeKey = o.optString("storeKey"),
            storeName = o.optString("storeName"),
            formulaId = o.optNullableStr("formulaId"),
            formulaUnits = o.optDouble("formulaUnits", 0.0),
            grossWeight = o.optDouble("grossWeight", 0.0),
            containerWeight = o.optDouble("containerWeight", 0.0),
            netWeight = o.optDouble("netWeight", 0.0),
            costPerKg = o.optDouble("costPerKg", 0.0),
            salePrice = o.optDouble("salePrice", 0.0),
            totalCost = o.optDouble("totalCost", 0.0),
            totalRevenue = o.optDouble("totalRevenue", 0.0),
            profit = o.optDouble("profit", 0.0),
            transfer = o.optBoolean("transfer", false),
            transferType = o.optString("transferType", "none"),
            transferPairId = o.optNullableStr("transferPairId"),
            transferPeerStore = o.optNullableStr("transferPeerStore"),
            notes = o.optNullableStr("notes"),
            recycled = o.optBoolean("recycled", false),
            recycledAtEpoch = o.optLong("recycledAtEpoch", 0L),
            createdByUid = o.optString("createdByUid"),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodeSale(s: SalesRecord): String = JSONObject().apply {
        put("id", s.id)
        put("ownerUid", s.ownerUid)
        put("memberUids", JSONArray(s.memberUids))
        put("date", s.date)
        put("mode", s.mode)
        s.customerId?.let { put("customerId", it) }
        put("customerName", s.customerName)
        put("storeKey", s.storeKey)
        s.storeName?.let { put("storeName", it) }
        put("repName", s.repName)
        put("qtyKg", s.qtyKg)
        put("ratePerKg", s.ratePerKg)
        put("totalAmount", s.totalAmount)
        put("cashReceived", s.cashReceived)
        put("creditAmount", s.creditAmount)
        put("expiredKg", s.expiredKg)
        put("choraValue", s.choraValue)
        put("addToLedger", s.addToLedger)
        s.ledgerEntryId?.let { put("ledgerEntryId", it) }
        s.notes?.let { put("notes", it) }
        put("recycled", s.recycled)
        put("recycledAtEpoch", s.recycledAtEpoch)
        put("createdByUid", s.createdByUid)
        putTs("createdAt", s.createdAt)
        putTs("updatedAt", s.updatedAt)
    }.toString()

    fun decodeSale(docId: String, json: String): SalesRecord {
        val o = JSONObject(json)
        return SalesRecord(
            id = docId,
            ownerUid = o.optString("ownerUid"),
            memberUids = o.optStrList("memberUids"),
            date = o.optString("date"),
            mode = o.optString("mode", "sale"),
            customerId = o.optNullableStr("customerId"),
            customerName = o.optString("customerName"),
            storeKey = o.optString("storeKey"),
            storeName = o.optNullableStr("storeName"),
            repName = o.optString("repName", "NONE"),
            qtyKg = o.optDouble("qtyKg", 0.0),
            ratePerKg = o.optDouble("ratePerKg", 0.0),
            totalAmount = o.optDouble("totalAmount", 0.0),
            cashReceived = o.optDouble("cashReceived", 0.0),
            creditAmount = o.optDouble("creditAmount", 0.0),
            expiredKg = o.optDouble("expiredKg", 0.0),
            choraValue = o.optDouble("choraValue", 0.0),
            addToLedger = o.optBoolean("addToLedger", true),
            ledgerEntryId = o.optNullableStr("ledgerEntryId"),
            notes = o.optNullableStr("notes"),
            recycled = o.optBoolean("recycled", false),
            recycledAtEpoch = o.optLong("recycledAtEpoch", 0L),
            createdByUid = o.optString("createdByUid"),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodeRep(r: RepRecord): String = JSONObject().apply {
        put("id", r.id)
        put("ownerUid", r.ownerUid)
        put("memberUids", JSONArray(r.memberUids))
        put("date", r.date)
        put("repName", r.repName)
        put("mode", r.mode)
        r.customerId?.let { put("customerId", it) }
        put("customerName", r.customerName)
        put("storeKey", r.storeKey)
        put("qtyKg", r.qtyKg)
        put("ratePerKg", r.ratePerKg)
        put("totalAmount", r.totalAmount)
        put("cashReceived", r.cashReceived)
        put("creditAmount", r.creditAmount)
        r.routeLat?.let { put("routeLat", it) }
        r.routeLng?.let { put("routeLng", it) }
        put("addressConfirmed", r.addressConfirmed)
        r.notes?.let { put("notes", it) }
        put("recycled", r.recycled)
        put("recycledAtEpoch", r.recycledAtEpoch)
        put("createdByUid", r.createdByUid)
        putTs("createdAt", r.createdAt)
        putTs("updatedAt", r.updatedAt)
    }.toString()

    fun decodeRep(docId: String, json: String): RepRecord {
        val o = JSONObject(json)
        return RepRecord(
            id = docId,
            ownerUid = o.optString("ownerUid"),
            memberUids = o.optStrList("memberUids"),
            date = o.optString("date"),
            repName = o.optString("repName"),
            mode = o.optString("mode", "sale"),
            customerId = o.optNullableStr("customerId"),
            customerName = o.optString("customerName"),
            storeKey = o.optString("storeKey"),
            qtyKg = o.optDouble("qtyKg", 0.0),
            ratePerKg = o.optDouble("ratePerKg", 0.0),
            totalAmount = o.optDouble("totalAmount", 0.0),
            cashReceived = o.optDouble("cashReceived", 0.0),
            creditAmount = o.optDouble("creditAmount", 0.0),
            routeLat = o.optNullableDouble("routeLat"),
            routeLng = o.optNullableDouble("routeLng"),
            addressConfirmed = o.optBoolean("addressConfirmed", false),
            notes = o.optNullableStr("notes"),
            recycled = o.optBoolean("recycled", false),
            recycledAtEpoch = o.optLong("recycledAtEpoch", 0L),
            createdByUid = o.optString("createdByUid"),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodeCalculator(c: CalculatorSettlement): String = JSONObject().apply {
        put("id", c.id)
        put("ownerUid", c.ownerUid)
        put("memberUids", JSONArray(c.memberUids))
        put("dateFrom", c.dateFrom)
        put("dateTo", c.dateTo)
        put("repName", c.repName)
        put("storeKey", c.storeKey)
        put("totalSoldKg", c.totalSoldKg)
        put("returnedKg", c.returnedKg)
        put("returnTarget", c.returnTarget)
        c.returnTargetName?.let { put("returnTargetName", it) }
        put("expiredKg", c.expiredKg)
        put("sharedDeduction", c.sharedDeduction)
        put("netSoldKg", c.netSoldKg)
        put("cashQtyKg", c.cashQtyKg)
        put("creditQtyKg", c.creditQtyKg)
        put("ratePerKg", c.ratePerKg)
        put("commissionTotal", c.commissionTotal)
        put("commissions", anyToJsonValue(c.commissions))
        put("expenseTotal", c.expenseTotal)
        put("expenses", anyToJsonValue(c.expenses))
        put("expectedCash", c.expectedCash)
        put("physicalCash", c.physicalCash)
        put("variance", c.variance)
        put("addToLedger", c.addToLedger)
        c.ledgerEntryId?.let { put("ledgerEntryId", it) }
        c.notes?.let { put("notes", it) }
        put("recycled", c.recycled)
        put("recycledAtEpoch", c.recycledAtEpoch)
        put("createdByUid", c.createdByUid)
        putTs("createdAt", c.createdAt)
        putTs("updatedAt", c.updatedAt)
    }.toString()

    fun decodeCalculator(docId: String, json: String): CalculatorSettlement {
        val o = JSONObject(json)
        return CalculatorSettlement(
            id = docId,
            ownerUid = o.optString("ownerUid"),
            memberUids = o.optStrList("memberUids"),
            dateFrom = o.optString("dateFrom"),
            dateTo = o.optString("dateTo"),
            repName = o.optString("repName"),
            storeKey = o.optString("storeKey"),
            totalSoldKg = o.optDouble("totalSoldKg", 0.0),
            returnedKg = o.optDouble("returnedKg", 0.0),
            returnTarget = o.optString("returnTarget", "store"),
            returnTargetName = o.optNullableStr("returnTargetName"),
            expiredKg = o.optDouble("expiredKg", 0.0),
            sharedDeduction = o.optDouble("sharedDeduction", 0.0),
            netSoldKg = o.optDouble("netSoldKg", 0.0),
            cashQtyKg = o.optDouble("cashQtyKg", 0.0),
            creditQtyKg = o.optDouble("creditQtyKg", 0.0),
            ratePerKg = o.optDouble("ratePerKg", 0.0),
            commissionTotal = o.optDouble("commissionTotal", 0.0),
            commissions = jsonToMapList(o.optJSONArray("commissions")),
            expenseTotal = o.optDouble("expenseTotal", 0.0),
            expenses = jsonToMapList(o.optJSONArray("expenses")),
            expectedCash = o.optDouble("expectedCash", 0.0),
            physicalCash = o.optDouble("physicalCash", 0.0),
            variance = o.optDouble("variance", 0.0),
            addToLedger = o.optBoolean("addToLedger", true),
            ledgerEntryId = o.optNullableStr("ledgerEntryId"),
            notes = o.optNullableStr("notes"),
            recycled = o.optBoolean("recycled", false),
            recycledAtEpoch = o.optLong("recycledAtEpoch", 0L),
            createdByUid = o.optString("createdByUid"),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodeFactory(f: FactoryRecord): String = JSONObject().apply {
        put("id", f.id)
        put("ownerUid", f.ownerUid)
        put("memberUids", JSONArray(f.memberUids))
        put("date", f.date)
        put("recordType", f.recordType)
        f.formulaId?.let { put("formulaId", it) }
        f.formulaName?.let { put("formulaName", it) }
        f.storeKey?.let { put("storeKey", it) }
        put("unitsProduced", f.unitsProduced)
        f.rawItemId?.let { put("rawItemId", it) }
        f.rawItemName?.let { put("rawItemName", it) }
        f.supplierName?.let { put("supplierName", it) }
        put("qtyRawUnit", f.qtyRawUnit)
        put("conversionFactor", f.conversionFactor)
        put("qtyKg", f.qtyKg)
        put("unitCost", f.unitCost)
        put("totalCost", f.totalCost)
        put("paidAmount", f.paidAmount)
        put("payableBalance", f.payableBalance)
        put("allocatedPaid", f.allocatedPaid)
        put("settled", f.settled)
        put("ingredientsUsed", anyToJsonValue(f.ingredientsUsed))
        f.notes?.let { put("notes", it) }
        put("recycled", f.recycled)
        put("recycledAtEpoch", f.recycledAtEpoch)
        put("createdByUid", f.createdByUid)
        putTs("createdAt", f.createdAt)
        putTs("updatedAt", f.updatedAt)
    }.toString()

    fun decodeFactory(docId: String, json: String): FactoryRecord {
        val o = JSONObject(json)
        return FactoryRecord(
            id = docId,
            ownerUid = o.optString("ownerUid"),
            memberUids = o.optStrList("memberUids"),
            date = o.optString("date"),
            recordType = o.optString("recordType", "batch"),
            formulaId = o.optNullableStr("formulaId"),
            formulaName = o.optNullableStr("formulaName"),
            storeKey = o.optNullableStr("storeKey"),
            unitsProduced = o.optLong("unitsProduced", 0L),
            rawItemId = o.optNullableStr("rawItemId"),
            rawItemName = o.optNullableStr("rawItemName"),
            supplierName = o.optNullableStr("supplierName"),
            qtyRawUnit = o.optDouble("qtyRawUnit", 0.0),
            conversionFactor = o.optDouble("conversionFactor", 1.0),
            qtyKg = o.optDouble("qtyKg", 0.0),
            unitCost = o.optDouble("unitCost", 0.0),
            totalCost = o.optDouble("totalCost", 0.0),
            paidAmount = o.optDouble("paidAmount", 0.0),
            payableBalance = o.optDouble("payableBalance", 0.0),
            allocatedPaid = o.optDouble("allocatedPaid", 0.0),
            settled = o.optBoolean("settled", false),
            ingredientsUsed = jsonToMapList(o.optJSONArray("ingredientsUsed")),
            notes = o.optNullableStr("notes"),
            recycled = o.optBoolean("recycled", false),
            recycledAtEpoch = o.optLong("recycledAtEpoch", 0L),
            createdByUid = o.optString("createdByUid"),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodeTransaction(t: PaymentTransaction): String = JSONObject().apply {
        put("id", t.id)
        put("ownerUid", t.ownerUid)
        put("memberUids", JSONArray(t.memberUids))
        put("date", t.date)
        put("txType", t.txType)
        t.entityId?.let { put("entityId", it) }
        put("entityName", t.entityName)
        put("entityCategory", t.entityCategory)
        put("paymentChannel", t.paymentChannel)
        put("amount", t.amount)
        t.transferPairId?.let { put("transferPairId", it) }
        t.peerEntityName?.let { put("peerEntityName", it) }
        put("addToLedger", t.addToLedger)
        t.ledgerEntryId?.let { put("ledgerEntryId", it) }
        t.notes?.let { put("notes", it) }
        put("recycled", t.recycled)
        put("recycledAtEpoch", t.recycledAtEpoch)
        put("createdByUid", t.createdByUid)
        putTs("createdAt", t.createdAt)
        putTs("updatedAt", t.updatedAt)
    }.toString()

    fun decodeTransaction(docId: String, json: String): PaymentTransaction {
        val o = JSONObject(json)
        return PaymentTransaction(
            id = docId,
            ownerUid = o.optString("ownerUid"),
            memberUids = o.optStrList("memberUids"),
            date = o.optString("date"),
            txType = o.optString("txType", "out"),
            entityId = o.optNullableStr("entityId"),
            entityName = o.optString("entityName"),
            entityCategory = o.optString("entityCategory", "party"),
            paymentChannel = o.optString("paymentChannel", "Cash"),
            amount = o.optDouble("amount", 0.0),
            transferPairId = o.optNullableStr("transferPairId"),
            peerEntityName = o.optNullableStr("peerEntityName"),
            addToLedger = o.optBoolean("addToLedger", true),
            ledgerEntryId = o.optNullableStr("ledgerEntryId"),
            notes = o.optNullableStr("notes"),
            recycled = o.optBoolean("recycled", false),
            recycledAtEpoch = o.optLong("recycledAtEpoch", 0L),
            createdByUid = o.optString("createdByUid"),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodeLedger(l: DailyLedgerEntry): String = JSONObject().apply {
        put("id", l.id)
        put("ownerUid", l.ownerUid)
        put("memberUids", JSONArray(l.memberUids))
        put("date", l.date)
        put("entryType", l.entryType)
        put("expenseScope", l.expenseScope)
        put("channel", l.channel)
        l.toChannel?.let { put("toChannel", it) }
        put("category", l.category)
        put("partyName", l.partyName)
        put("amount", l.amount)
        put("tabEntry", l.tabEntry)
        l.sourceCollection?.let { put("sourceCollection", it) }
        l.sourceDocId?.let { put("sourceDocId", it) }
        l.notes?.let { put("notes", it) }
        put("recycled", l.recycled)
        put("recycledAtEpoch", l.recycledAtEpoch)
        put("createdByUid", l.createdByUid)
        putTs("createdAt", l.createdAt)
        putTs("updatedAt", l.updatedAt)
    }.toString()

    fun decodeLedger(docId: String, json: String): DailyLedgerEntry {
        val o = JSONObject(json)
        return DailyLedgerEntry(
            id = docId,
            ownerUid = o.optString("ownerUid"),
            memberUids = o.optStrList("memberUids"),
            date = o.optString("date"),
            entryType = o.optString("entryType", "IN"),
            expenseScope = o.optString("expenseScope", "none"),
            channel = o.optString("channel", "Cash"),
            toChannel = o.optNullableStr("toChannel"),
            category = o.optString("category", "General"),
            partyName = o.optString("partyName"),
            amount = o.optDouble("amount", 0.0),
            tabEntry = o.optBoolean("tabEntry", false),
            sourceCollection = o.optNullableStr("sourceCollection"),
            sourceDocId = o.optNullableStr("sourceDocId"),
            notes = o.optNullableStr("notes"),
            recycled = o.optBoolean("recycled", false),
            recycledAtEpoch = o.optLong("recycledAtEpoch", 0L),
            createdByUid = o.optString("createdByUid"),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodePending(p: PendingApproval): String = JSONObject().apply {
        put("id", p.id)
        put("ownerUid", p.ownerUid)
        put("memberUids", JSONArray(p.memberUids))
        put("submittedByUid", p.submittedByUid)
        put("submittedByName", p.submittedByName)
        put("submittedByEmail", p.submittedByEmail)
        put("action", p.action)
        put("targetCollection", p.targetCollection)
        put("targetDocId", p.targetDocId)
        put("summaryTitle", p.summaryTitle)
        put("summaryDetail", p.summaryDetail)
        put("recordDate", p.recordDate)
        put("payloadData", anyToJsonValue(p.payloadData))
        put("status", p.status)
        p.reviewedByUid?.let { put("reviewedByUid", it) }
        putTs("createdAt", p.createdAt)
        putTs("updatedAt", p.updatedAt)
    }.toString()

    fun decodePending(docId: String, json: String): PendingApproval {
        val o = JSONObject(json)
        val payloadObj = o.optJSONObject("payloadData")
        val payloadMap = LinkedHashMap<String, Any>()
        payloadObj?.keys()?.forEach { k ->
            jsonValToKotlin(payloadObj.get(k))?.let { payloadMap[k] = it }
        }
        return PendingApproval(
            id = docId,
            ownerUid = o.optString("ownerUid"),
            memberUids = o.optStrList("memberUids"),
            submittedByUid = o.optString("submittedByUid"),
            submittedByName = o.optString("submittedByName"),
            submittedByEmail = o.optString("submittedByEmail"),
            action = o.optString("action", "set"),
            targetCollection = o.optString("targetCollection", "sales"),
            targetDocId = o.optString("targetDocId"),
            summaryTitle = o.optString("summaryTitle"),
            summaryDetail = o.optString("summaryDetail"),
            recordDate = o.optString("recordDate"),
            payloadData = payloadMap,
            status = o.optString("status", "pending"),
            reviewedByUid = o.optNullableStr("reviewedByUid"),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodeInvite(i: WorkspaceInvite): String = JSONObject().apply {
        put("id", i.id)
        put("ownerUid", i.ownerUid)
        put("ownerName", i.ownerName)
        put("memberEmail", i.memberEmail)
        put("displayName", i.displayName)
        put("roles", JSONArray(i.roles))
        put("approved", i.approved)
        put("status", i.status)
        i.acceptedByUid?.let { put("acceptedByUid", it) }
        putTs("createdAt", i.createdAt)
        putTs("updatedAt", i.updatedAt)
    }.toString()

    fun decodeInvite(docId: String, json: String): WorkspaceInvite {
        val o = JSONObject(json)
        return WorkspaceInvite(
            id = docId,
            ownerUid = o.optString("ownerUid"),
            ownerName = o.optString("ownerName"),
            memberEmail = o.optString("memberEmail"),
            displayName = o.optString("displayName"),
            roles = o.optStrList("roles"),
            approved = o.optBoolean("approved", true),
            status = o.optString("status", "pending"),
            acceptedByUid = o.optNullableStr("acceptedByUid"),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }

    fun encodeUserProfile(p: UserProfile): String = JSONObject().apply {
        put("id", p.id.ifBlank { p.uid })
        put("uid", p.uid)
        put("email", p.email)
        put("displayName", p.displayName)
        put("workspaceOwnerUid", p.workspaceOwnerUid)
        put("role", p.role)
        put("roles", JSONArray(p.roles))
        put("approved", p.approved)
        p.inviteId?.let { put("inviteId", it) }
        putTs("createdAt", p.createdAt)
        putTs("updatedAt", p.updatedAt)
    }.toString()

    fun decodeUserProfile(docId: String, json: String): UserProfile {
        val o = JSONObject(json)
        val uid = o.optString("uid", docId).ifBlank { docId }
        val role = o.optString("role", "admin")
        return UserProfile(
            id = uid,
            uid = uid,
            email = o.optString("email"),
            displayName = o.optString("displayName"),
            workspaceOwnerUid = o.optString("workspaceOwnerUid", uid),
            role = role,
            roles = o.optStrList("roles").ifEmpty { listOf(role) },
            approved = o.optBoolean("approved", true),
            inviteId = o.optNullableStr("inviteId"),
            createdAt = o.optTs("createdAt"),
            updatedAt = o.optTs("updatedAt")
        )
    }
}
