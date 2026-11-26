package com.gullzubair.sarim.data.local

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.gullzubair.sarim.data.model.CalculatorRecord
import com.gullzubair.sarim.data.model.CustomerRecord
import com.gullzubair.sarim.data.model.DailyLedgerEntry
import com.gullzubair.sarim.data.model.DeletionTombstone
import com.gullzubair.sarim.data.model.EntityRecord
import com.gullzubair.sarim.data.model.ExpenseRecord
import com.gullzubair.sarim.data.model.FactoryRecord
import com.gullzubair.sarim.data.model.FormulaConfig
import com.gullzubair.sarim.data.model.FormulaIngredient
import com.gullzubair.sarim.data.model.InventoryRecord
import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.data.model.PendingApproval
import com.gullzubair.sarim.data.model.PhotoBlob
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.ReturnRecord
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.SavedCalcRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.UserProfile
import com.gullzubair.sarim.data.model.WorkspaceAccountRecord
import com.gullzubair.sarim.data.model.WorkspaceInvite
import com.gullzubair.sarim.data.model.WorkspaceSettings
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object WorkspaceJsonCodec {

    // --- Primitive & Map/JSON Helpers ---

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

    fun jsonToMap(json: String?): Map<String, Any> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            val out = LinkedHashMap<String, Any>()
            obj.keys().forEach { key ->
                jsonValToKotlin(obj.get(key))?.let { out[key] = it }
            }
            out
        }.getOrDefault(emptyMap())
    }

    fun jsonValToKotlin(raw: Any?): Any? = when (raw) {
        null, JSONObject.NULL -> null
        is JSONObject -> {
            val map = LinkedHashMap<String, Any>()
            raw.keys().forEach { k -> jsonValToKotlin(raw.get(k))?.let { map[k] = it } }
            map
        }
        is JSONArray -> List(raw.length()) { idx -> jsonValToKotlin(raw.get(idx)) }.filterNotNull()
        is Int -> raw.toLong()
        else -> raw
    }

    fun jsonToMapList(arr: JSONArray?): List<Map<String, Any>> {
        if (arr == null) return emptyList()
        return buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val m = LinkedHashMap<String, Any>()
                obj.keys().forEach { k -> jsonValToKotlin(obj.get(k))?.let { m[k] = it } }
                add(m)
            }
        }
    }

    fun jsonArrayToMapList(json: String?): List<Map<String, Any>> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { jsonToMapList(JSONArray(json)) }.getOrDefault(emptyList())
    }

    fun stringListToJson(list: List<String>): String = JSONArray(list).toString()

    fun jsonToStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) { i -> arr.optString(i) }.filter { it.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    fun mapListToJson(list: List<Map<String, Any?>>): String = JSONArray().apply {
        list.forEach { put(anyToJsonValue(it)) }
    }.toString()

    private fun asStr(v: Any?, default: String = ""): String = when (v) {
        null, JSONObject.NULL -> default
        is String -> v
        else -> v.toString()
    }

    private fun asNullableStr(v: Any?): String? = when (v) {
        null, JSONObject.NULL -> null
        is String -> v.takeIf { it != "null" && it.isNotBlank() }
        else -> v.toString().takeIf { it != "null" && it.isNotBlank() }
    }

    private fun asDouble(v: Any?, default: Double = 0.0): Double = when (v) {
        is Number -> v.toDouble().let { if (it.isFinite()) it else default }
        is String -> v.trim().replace(",", "").toDoubleOrNull()?.let { if (it.isFinite()) it else default } ?: default
        else -> default
    }

    private fun asNullableDouble(v: Any?): Double? = when (v) {
        null, JSONObject.NULL -> null
        is Number -> v.toDouble().takeIf { it.isFinite() }
        is String -> v.trim().replace(",", "").toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }

    private fun asLong(v: Any?, default: Long = 0L): Long = when (v) {
        is Timestamp -> v.seconds * 1000L + (v.nanoseconds / 1_000_000L)
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull() ?: v.trim().toDoubleOrNull()?.toLong() ?: default
        else -> default
    }

    private fun asNullableLong(v: Any?): Long? = when (v) {
        null, JSONObject.NULL -> null
        is Timestamp -> v.seconds * 1000L + (v.nanoseconds / 1_000_000L)
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull() ?: v.trim().toDoubleOrNull()?.toLong()
        else -> null
    }

    private fun asBool(v: Any?, default: Boolean = false): Boolean = when (v) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> when (v.trim().lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no" -> false
            else -> default
        }
        else -> default
    }

    private fun asStrList(v: Any?): List<String> = when (v) {
        is Iterable<*> -> v.mapNotNull { asNullableStr(it) }
        else -> emptyList()
    }

    private fun asDoubleMap(v: Any?): Map<String, Double> {
        val raw = v as? Map<*, *> ?: return emptyMap()
        val out = LinkedHashMap<String, Double>()
        raw.forEach { (k, value) ->
            val key = asNullableStr(k) ?: return@forEach
            val d = asNullableDouble(value) ?: return@forEach
            out[key] = d
        }
        return out
    }

    private fun asMapList(v: Any?): List<Map<String, Any>> {
        val raw = v as? Iterable<*> ?: return emptyList()
        return raw.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val out = LinkedHashMap<String, Any>()
            m.forEach { (k, valItem) ->
                if (k != null && valItem != null && valItem != JSONObject.NULL) {
                    out[k.toString()] = valItem
                }
            }
            out
        }
    }

    private fun parseAllocPairs(v: Any?, idKey: String): Pair<Map<String, Double>, Map<String, Boolean>> {
        val amounts = LinkedHashMap<String, Double>()
        val fullFlags = LinkedHashMap<String, Boolean>()
        when (v) {
            is Iterable<*> -> {
                v.forEach { item ->
                    val m = item as? Map<*, *> ?: return@forEach
                    val id = asNullableStr(m[idKey])
                        ?: asNullableStr(m["id"])
                        ?: return@forEach
                    val amt = asNullableDouble(m["amount"]) ?: 0.0
                    amounts[id] = amt
                    fullFlags[id] = asBool(m["full"], false)
                }
            }
            is Map<*, *> -> {
                v.forEach { (k, rawVal) ->
                    val id = asNullableStr(k) ?: return@forEach
                    when (rawVal) {
                        is Map<*, *> -> {
                            amounts[id] = asNullableDouble(rawVal["amount"]) ?: 0.0
                            fullFlags[id] = asBool(rawVal["full"], false)
                        }
                        else -> {
                            amounts[id] = asNullableDouble(rawVal) ?: 0.0
                        }
                    }
                }
            }
        }
        return amounts to fullFlags
    }

    private fun allocsToWebList(
        amounts: Map<String, Double>,
        fullFlags: Map<String, Boolean>,
        idKey: String
    ): List<Map<String, Any>> = amounts.entries.map { (id, amt) ->
        mapOf(
            idKey to id,
            "amount" to amt,
            "full" to (fullFlags[id] == true)
        )
    }

    private fun asEditHistory(v: Any?): List<Map<String, Any>> = asMapList(v).takeLast(100)

    fun snapshotToMap(snap: DocumentSnapshot): Map<String, Any> {
        val data = snap.data ?: return emptyMap()
        val out = LinkedHashMap<String, Any>(data.size + 1)
        data.forEach { (k, v) -> if (v != null) out[k] = v }
        if (!out.containsKey("id")) out["id"] = snap.id
        return out
    }

    // --- Profile ---

    fun toEntity(p: UserProfile): CachedProfileEntity = CachedProfileEntity(
        uid = p.uid,
        email = p.email,
        displayName = p.displayName,
        ownerUid = p.ownerUid?.takeIf { it.isNotBlank() },
        role = p.role,
        rolesJson = stringListToJson(p.roles),
        approved = p.approved,
        createdBy = p.createdBy,
        inviteId = p.inviteId,
        updatedAtEpochMs = System.currentTimeMillis()
    )

    fun toProfile(e: CachedProfileEntity): UserProfile = UserProfile(
        uid = e.uid,
        email = e.email,
        displayName = e.displayName,
        ownerUid = e.ownerUid?.takeIf { it.isNotBlank() },
        role = e.role,
        roles = jsonToStringList(e.rolesJson).ifEmpty { listOf(e.role) },
        approved = e.approved,
        createdBy = e.createdBy,
        inviteId = e.inviteId
    )

    fun mapToUserProfile(uid: String, data: Map<String, Any>): UserProfile {
        val rawOwner = asNullableStr(data["ownerUid"])
            ?: asNullableStr(data["workspaceOwnerUid"])?.takeIf { it != uid }
        val rolesRaw = asStrList(data["roles"])
        val roleRaw = asStr(data["role"], if ("admin" in rolesRaw) "admin" else "user")
        val roles = if (rolesRaw.isNotEmpty()) rolesRaw else listOf(if (roleRaw == "admin") "admin" else "rep")
        return UserProfile(
            uid = asStr(data["uid"], uid).ifBlank { uid },
            email = asStr(data["email"]).lowercase(),
            displayName = asStr(data["displayName"], asStr(data["name"])),
            ownerUid = rawOwner,
            role = if ("admin" in roles || roleRaw == "admin") "admin" else "user",
            roles = roles,
            approved = data["approved"] as? Boolean ?: (asStr(data["approved"], "true") != "false"),
            createdBy = asStr(data["createdBy"]),
            inviteId = asNullableStr(data["inviteId"]),
            lastWrite = asLong(data["lastWrite"], 0L)
        )
    }

    fun mapToInvite(docId: String, data: Map<String, Any>): WorkspaceInvite = WorkspaceInvite(
        id = docId,
        ownerUid = asStr(data["ownerUid"]),
        ownerName = asStr(data["ownerName"]),
        memberEmail = asStr(data["memberEmail"]).lowercase(),
        displayName = asStr(data["displayName"]),
        roles = asStrList(data["roles"]),
        approved = asBool(data["approved"], true),
        status = asStr(data["status"], "pending"),
        acceptedByUid = asNullableStr(data["acceptedByUid"])
    )

    fun mapToAccountRecord(data: Map<String, Any>): WorkspaceAccountRecord = WorkspaceAccountRecord(
        uid = asStr(data["uid"]),
        email = asStr(data["email"]).lowercase(),
        displayName = asStr(data["displayName"]),
        roles = asStrList(data["roles"]).ifEmpty { listOf("rep") },
        approved = asBool(data["approved"], true),
        ownerUid = asStr(data["ownerUid"]),
        createdBy = asStr(data["createdBy"])
    )

    fun accountRecordToMap(a: WorkspaceAccountRecord): Map<String, Any> = buildMap {
        put("uid", a.uid)
        put("email", a.email.lowercase())
        put("displayName", a.displayName)
        put("roles", a.roles)
        put("approved", a.approved)
        if (a.ownerUid.isNotBlank()) put("ownerUid", a.ownerUid)
        if (a.createdBy.isNotBlank()) put("createdBy", a.createdBy)
    }

    // --- Stores ---

    fun mapToStore(data: Map<String, Any>): StoreConfig {
        val key = asStr(data["key"], asStr(data["storeKey"], asStr(data["id"])))
        return StoreConfig(
            key = key,
            name = asStr(data["name"], key),
            salePrice = asDouble(data["salePrice"], 0.0),
            formulaId = asNullableStr(data["formulaId"]),
            active = asBool(data["active"], true),
            updatedAt = asLong(data["updatedAt"], 0L),
            deletedAt = asNullableLong(data["deletedAt"])
        )
    }

    fun storeToMap(s: StoreConfig): Map<String, Any> = buildMap {
        put("key", s.key)
        put("name", s.name)
        put("salePrice", s.salePrice)
        s.formulaId?.takeIf { it.isNotBlank() }?.let { put("formulaId", it) }
        put("active", s.active)
        put("updatedAt", if (s.updatedAt > 0L) s.updatedAt else System.currentTimeMillis())
        s.deletedAt?.let { put("deletedAt", it) }
    }

    // --- Formulas ---

    fun mapToIngredient(data: Map<String, Any>): FormulaIngredient = FormulaIngredient(
        id = asStr(data["id"]),
        kind = asNullableStr(data["kind"]),
        formulaId = asNullableStr(data["formulaId"]),
        name = asStr(data["name"], "Untitled"),
        cost = asDouble(data["cost"], 0.0),
        quantity = asDouble(data["quantity"], 0.0),
        custom = asBool(data["custom"], false)
    )

    fun ingredientToMap(i: FormulaIngredient): Map<String, Any> = buildMap {
        put("id", i.id)
        i.kind?.let { put("kind", it) }
        i.formulaId?.let { put("formulaId", it) }
        put("name", i.name)
        put("cost", i.cost)
        put("quantity", i.quantity)
        if (i.custom) put("custom", true)
    }

    fun mapToSavedCalc(data: Map<String, Any>): SavedCalcRecord {
        val manual = data["manual"] as? Map<*, *>
        return SavedCalcRecord(
            id = asStr(data["id"]),
            kind = asStr(data["kind"], "mix"),
            name = asStr(data["name"]),
            mode = asStr(data["mode"], "units"),
            value = asDouble(data["value"], 0.0),
            ingredientId = asNullableStr(data["ingredientId"]),
            original = asDouble(data["original"], 0.0),
            used = asDouble(data["used"], 0.0),
            actual = asDoubleMap(data["actual"]),
            manualId = asNullableStr(manual?.get("id")),
            manualAdd = asNullableDouble(manual?.get("add")),
            manualTotal = asNullableDouble(manual?.get("total")),
            createdAt = asLong(data["createdAt"], 0L),
            updatedAt = asLong(data["updatedAt"], 0L)
        )
    }

    fun savedCalcToMap(c: SavedCalcRecord): Map<String, Any> = buildMap {
        put("id", c.id)
        put("kind", c.kind)
        put("name", c.name)
        put("createdAt", if (c.createdAt > 0L) c.createdAt else System.currentTimeMillis())
        put("updatedAt", if (c.updatedAt > 0L) c.updatedAt else System.currentTimeMillis())
        if (c.kind == "mix") {
            put("mode", c.mode)
            put("value", c.value)
            c.ingredientId?.let { put("ingredientId", it) }
        } else {
            put("original", c.original)
            put("used", c.used)
            put("actual", c.actual)
            if (c.manualId != null || c.manualTotal != null) {
                put("manual", buildMap<String, Any> {
                    c.manualId?.let { put("id", it) }
                    c.manualAdd?.let { put("add", it) }
                    c.manualTotal?.let { put("total", it) }
                })
            }
        }
    }

    fun mapToFormula(data: Map<String, Any>): FormulaConfig {
        val factor = asDouble(data["costAdjustmentFactor"], 0.0)
            .let { if (it > 0.0) it else asDouble(data["unitsPerBatch"], 1.0) }
            .let { if (it > 0.0) it else 1.0 }
        val rawCalcs = asMapList(data["savedCalcs"])
        val legacyMixes = asMapList(data["savedMixes"]).map { it + ("kind" to "mix") }
        val legacyFix = asMapList(data["savedCorrections"]).map { it + ("kind" to "fix") }
        val calcs = (if (rawCalcs.isNotEmpty()) rawCalcs else legacyMixes + legacyFix).map(::mapToSavedCalc)
        return FormulaConfig(
            id = asStr(data["id"]),
            name = asStr(data["name"]),
            ingredients = asMapList(data["ingredients"]).map(::mapToIngredient),
            additionalCost = asDouble(data["additionalCost"], 0.0),
            costAdjustmentFactor = factor,
            unitsPerBatch = asDouble(data["unitsPerBatch"], factor),
            savedCalcs = calcs,
            updatedAt = asLong(data["updatedAt"], 0L)
        )
    }

    fun formulaToMap(f: FormulaConfig): Map<String, Any> = buildMap {
        put("id", f.id)
        put("name", f.name)
        put("ingredients", f.ingredients.map(::ingredientToMap))
        put("additionalCost", f.additionalCost)
        put("costAdjustmentFactor", f.costAdjustmentFactor)
        put("unitsPerBatch", f.unitsPerBatch)
        put("savedCalcs", f.savedCalcs.map(::savedCalcToMap))
        put("updatedAt", if (f.updatedAt > 0L) f.updatedAt else System.currentTimeMillis())
    }

    // --- 12 Sync Collections ---

    fun mapToProduction(docId: String, data: Map<String, Any>): ProductionRecord = ProductionRecord(
        id = asStr(data["id"], docId).ifBlank { docId },
        date = asStr(data["date"]),
        time = asStr(data["time"]),
        store = asStr(data["store"], asStr(data["storeName"], asStr(data["storeKey"]))),
        grossWt = asDouble(data["grossWt"], asDouble(data["grossWeight"], 0.0)),
        contWt = asDouble(data["contWt"], asDouble(data["containerWeight"], 0.0)),
        net = asDouble(data["net"], asDouble(data["netWeight"], 0.0)),
        cp = asDouble(data["cp"], asDouble(data["costPerKg"], 0.0)),
        sp = asDouble(data["sp"], asDouble(data["salePrice"], 0.0)),
        totalCost = asDouble(data["totalCost"], 0.0),
        totalSale = asDouble(data["totalSale"], asDouble(data["totalRevenue"], 0.0)),
        profit = asDouble(data["profit"], 0.0),
        formulaUnits = asDouble(data["formulaUnits"], 0.0),
        formulaId = asStr(data["formulaId"]),
        formulaName = asStr(data["formulaName"]),
        formulaMaterials = asMapList(data["formulaMaterials"]),
        formulaCost = asDouble(data["formulaCost"], 0.0),
        paymentStatus = asStr(data["paymentStatus"]),
        photoKeys = asStrList(data["photoKeys"]),
        isReturn = asBool(data["isReturn"], false),
        returnedBy = asStr(data["returnedBy"]),
        returnNote = asStr(data["returnNote"], asStr(data["notes"])),
        isTransfer = asBool(data["isTransfer"], asBool(data["transfer"], false)),
        transferDirection = asStr(data["transferDirection"], asStr(data["transferType"]).let { if (it == "none") "" else it }),
        transferPairId = asStr(data["transferPairId"]),
        transferPeerStore = asStr(data["transferPeerStore"]),
        transferNote = asStr(data["transferNote"]),
        isMerged = asBool(data["isMerged"], false),
        managedBy = asStr(data["managedBy"]),
        createdBy = asStr(data["createdBy"], asStr(data["createdByUid"])),
        recordDate = asStr(data["recordDate"], asStr(data["date"])),
        timestamp = asLong(data["timestamp"], asLong(data["createdAt"], 0L)),
        createdAt = asLong(data["createdAt"], 0L),
        updatedAtEpoch = asLong(data["updatedAt"], 0L),
        deletedAt = asNullableLong(data["deletedAt"]),
        isEdited = asBool(data["isEdited"], false),
        editedAt = asStr(data["editedAt"]),
        editHistory = asEditHistory(data["editHistory"])
    )

    fun productionToMap(p: ProductionRecord): Map<String, Any> = buildMap {
        put("id", p.id)
        put("date", p.date)
        if (p.time.isNotBlank()) put("time", p.time)
        put("store", p.store)
        put("grossWt", p.grossWt)
        put("contWt", p.contWt)
        put("net", p.net)
        put("cp", p.cp)
        put("sp", p.sp)
        put("totalCost", p.totalCost)
        put("totalSale", p.totalSale)
        put("profit", p.profit)
        if (p.formulaUnits != 0.0) put("formulaUnits", p.formulaUnits)
        if (p.formulaId.isNotBlank()) put("formulaId", p.formulaId)
        if (p.formulaName.isNotBlank()) put("formulaName", p.formulaName)
        if (p.formulaMaterials.isNotEmpty()) put("formulaMaterials", p.formulaMaterials)
        if (p.formulaCost != 0.0) put("formulaCost", p.formulaCost)
        if (p.paymentStatus.isNotBlank()) put("paymentStatus", p.paymentStatus)
        if (p.photoKeys.isNotEmpty()) put("photoKeys", p.photoKeys)
        if (p.isReturn) {
            put("isReturn", true)
            if (p.returnedBy.isNotBlank()) put("returnedBy", p.returnedBy)
            if (p.returnNote.isNotBlank()) put("returnNote", p.returnNote)
        }
        if (p.isTransfer) {
            put("isTransfer", true)
            put("transferDirection", p.transferDirection)
            if (p.transferPairId.isNotBlank()) put("transferPairId", p.transferPairId)
            if (p.transferPeerStore.isNotBlank()) put("transferPeerStore", p.transferPeerStore)
            if (p.transferNote.isNotBlank()) put("transferNote", p.transferNote)
        }
        if (p.isMerged) put("isMerged", true)
        if (p.managedBy.isNotBlank()) put("managedBy", p.managedBy)
        if (p.createdBy.isNotBlank()) put("createdBy", p.createdBy)
        if (p.recordDate.isNotBlank()) put("recordDate", p.recordDate)
        put("timestamp", if (p.timestamp > 0L) p.timestamp else System.currentTimeMillis())
        put("createdAt", if (p.createdAt > 0L) p.createdAt else System.currentTimeMillis())
        p.deletedAt?.let { put("deletedAt", it) }
        if (p.isEdited) put("isEdited", true)
        if (p.editedAt.isNotBlank()) put("editedAt", p.editedAt)
        if (p.editHistory.isNotEmpty()) put("editHistory", p.editHistory)
    }

    fun mapToSale(docId: String, data: Map<String, Any>): SalesRecord {
        val gps = data["gps"] as? Map<*, *>
        val isColl = asBool(data["isCollection"], false) || asStr(data["mode"]) == "collection" ||
            asStr(data["paymentType"]).equals("COLLECTION", ignoreCase = true)
        val rawPayType = asStr(data["paymentType"]).uppercase().ifBlank {
            when (asStr(data["mode"]).lowercase()) {
                "collection" -> "COLLECTION"
                "return" -> "REP_RETURN"
                else -> if (asDouble(data["creditAmount"], 0.0) > 0.0) "CREDIT" else "CASH"
            }
        }
        val (collAllocs, collAllocFull) = parseAllocPairs(data["collectionAllocs"], "cid")
        val (allocs, allocFull) = parseAllocPairs(data["allocations"], "saleId")
        val legacyCollFull = data["collectionAllocFull"] as? Map<*, *>
        val resolvedCollFull = if (legacyCollFull != null) {
            legacyCollFull.entries.mapNotNull { (k, v) -> k?.toString()?.let { it to asBool(v, false) } }.toMap() + collAllocFull
        } else collAllocFull
        val legacyAllocFull = data["allocationFull"] as? Map<*, *>
        val resolvedAllocFull = if (legacyAllocFull != null) {
            legacyAllocFull.entries.mapNotNull { (k, v) -> k?.toString()?.let { it to asBool(v, false) } }.toMap() + allocFull
        } else allocFull

        return SalesRecord(
            id = asStr(data["id"], docId).ifBlank { docId },
            date = asStr(data["date"]),
            supplyDate = asStr(data["supplyDate"], asStr(data["date"])),
            time = asStr(data["time"]),
            customerName = asStr(data["customerName"]),
            customerPhone = asStr(data["customerPhone"]),
            quantity = asDouble(data["quantity"], asDouble(data["qtyKg"], 0.0)),
            supplyStore = asStr(data["supplyStore"], asStr(data["storeName"], asStr(data["storeKey"]))),
            paymentType = rawPayType,
            salesRep = asStr(data["salesRep"], asStr(data["repName"])),
            currentRepProfile = asStr(data["currentRepProfile"]),
            unitPrice = asDouble(data["unitPrice"], asDouble(data["ratePerKg"], 0.0)),
            totalValue = asDouble(data["totalValue"], asDouble(data["totalAmount"], 0.0)),
            totalCost = asDouble(data["totalCost"], 0.0),
            profit = asDouble(data["profit"], 0.0),
            costSource = asStr(data["costSource"]),
            creditReceived = asBool(data["creditReceived"], rawPayType == "CASH"),
            creditReceivedDate = asStr(data["creditReceivedDate"]),
            creditReceivedTime = asStr(data["creditReceivedTime"]),
            creditReceivedManually = asBool(data["creditReceivedManually"], false),
            partialPaymentReceived = asDouble(data["partialPaymentReceived"], 0.0),
            collectionAllocs = collAllocs,
            collectionAllocFull = resolvedCollFull,
            isCollection = isColl,
            collectedAmount = asDouble(data["collectedAmount"], asDouble(data["cashReceived"], 0.0)),
            allocations = allocs,
            allocationFull = resolvedAllocFull,
            expiredQty = asDouble(data["expiredQty"], asDouble(data["expiredKg"], 0.0)),
            expiredValue = asDouble(data["expiredValue"], asDouble(data["choraValue"], 0.0)),
            expiredUnitPrice = asDouble(data["expiredUnitPrice"], 0.0),
            expiredApplied = asDouble(data["expiredApplied"], 0.0),
            expiredAllocated = asDouble(data["expiredAllocated"], 0.0),
            transactionType = asStr(data["transactionType"]),
            isRepTransfer = asBool(data["isRepTransfer"], false),
            repTransferFrom = asStr(data["repTransferFrom"]),
            isTransfer = asBool(data["isTransfer"], false),
            transferFrom = asStr(data["transferFrom"]),
            transferPairId = asStr(data["transferPairId"]),
            relatedSaleId = asStr(data["relatedSaleId"]),
            affectsInventory = asBool(data["affectsInventory"], true),
            usedInCalcId = asStr(data["usedInCalcId"]),
            isRepReturn = asBool(data["isRepReturn"], rawPayType == "REP_RETURN"),
            returnedQty = asDouble(data["returnedQty"], 0.0),
            returnValue = asDouble(data["returnValue"], 0.0),
            gpsLat = asNullableDouble(gps?.get("lat")) ?: asNullableDouble(data["routeLat"]),
            gpsLng = asNullableDouble(gps?.get("lng")) ?: asNullableDouble(data["routeLng"]),
            gpsAccuracy = asNullableDouble(gps?.get("accuracy")),
            gpsConfirmed = asBool(gps?.get("confirmed"), asBool(data["addressConfirmed"], false)),
            notes = asStr(data["notes"]),
            isMerged = asBool(data["isMerged"], false),
            mergedSummary = asDoubleMap(data["mergedSummary"]),
            timestamp = asLong(data["timestamp"], asLong(data["createdAt"], 0L)),
            createdAt = asLong(data["createdAt"], 0L),
            updatedAtEpoch = asLong(data["updatedAt"], 0L),
            deletedAt = asNullableLong(data["deletedAt"]),
            isEdited = asBool(data["isEdited"], false),
            editedAt = asStr(data["editedAt"]),
            editHistory = asEditHistory(data["editHistory"])
        )
    }

    fun saleToMap(s: SalesRecord): Map<String, Any> = buildMap {
        put("id", s.id)
        put("date", s.date)
        if (s.supplyDate.isNotBlank()) put("supplyDate", s.supplyDate)
        if (s.time.isNotBlank()) put("time", s.time)
        put("customerName", s.customerName)
        if (s.customerPhone.isNotBlank()) put("customerPhone", s.customerPhone)
        put("quantity", s.quantity)
        put("supplyStore", s.supplyStore)
        put("paymentType", s.paymentType)
        if (s.salesRep.isNotBlank()) put("salesRep", s.salesRep)
        if (s.currentRepProfile.isNotBlank()) put("currentRepProfile", s.currentRepProfile)
        put("unitPrice", s.unitPrice)
        put("totalValue", s.totalValue)
        put("totalCost", s.totalCost)
        put("profit", s.profit)
        if (s.costSource.isNotBlank()) put("costSource", s.costSource)
        put("creditReceived", s.creditReceived)
        if (s.creditReceivedDate.isNotBlank()) put("creditReceivedDate", s.creditReceivedDate)
        if (s.creditReceivedTime.isNotBlank()) put("creditReceivedTime", s.creditReceivedTime)
        if (s.creditReceivedManually) put("creditReceivedManually", true)
        if (s.partialPaymentReceived != 0.0) put("partialPaymentReceived", s.partialPaymentReceived)
        if (s.collectionAllocs.isNotEmpty()) put("collectionAllocs", allocsToWebList(s.collectionAllocs, s.collectionAllocFull, "cid"))
        if (s.isCollection) put("isCollection", true)
        if (s.collectedAmount != 0.0) put("collectedAmount", s.collectedAmount)
        if (s.allocations.isNotEmpty()) put("allocations", allocsToWebList(s.allocations, s.allocationFull, "saleId"))
        if (s.expiredQty != 0.0) put("expiredQty", s.expiredQty)
        if (s.expiredValue != 0.0) put("expiredValue", s.expiredValue)
        if (s.expiredUnitPrice != 0.0) put("expiredUnitPrice", s.expiredUnitPrice)
        if (s.expiredApplied != 0.0) put("expiredApplied", s.expiredApplied)
        if (s.expiredAllocated != 0.0) put("expiredAllocated", s.expiredAllocated)
        if (s.transactionType.isNotBlank()) put("transactionType", s.transactionType)
        if (s.isRepTransfer) put("isRepTransfer", true)
        if (s.repTransferFrom.isNotBlank()) put("repTransferFrom", s.repTransferFrom)
        if (s.isTransfer) put("isTransfer", true)
        if (s.transferFrom.isNotBlank()) put("transferFrom", s.transferFrom)
        if (s.transferPairId.isNotBlank()) put("transferPairId", s.transferPairId)
        if (s.relatedSaleId.isNotBlank()) put("relatedSaleId", s.relatedSaleId)
        if (!s.affectsInventory) put("affectsInventory", false)
        if (s.usedInCalcId.isNotBlank()) put("usedInCalcId", s.usedInCalcId)
        if (s.isRepReturn) put("isRepReturn", true)
        if (s.returnedQty != 0.0) put("returnedQty", s.returnedQty)
        if (s.returnValue != 0.0) put("returnValue", s.returnValue)
        if (s.gpsLat != null && s.gpsLng != null) {
            put("gps", buildMap<String, Any> {
                put("lat", s.gpsLat)
                put("lng", s.gpsLng)
                s.gpsAccuracy?.let { put("accuracy", it) }
                if (s.gpsConfirmed) put("confirmed", true)
            })
        }
        if (s.notes.isNotBlank()) put("notes", s.notes)
        if (s.isMerged) put("isMerged", true)
        if (s.mergedSummary.isNotEmpty()) put("mergedSummary", s.mergedSummary)
        put("timestamp", if (s.timestamp > 0L) s.timestamp else System.currentTimeMillis())
        put("createdAt", if (s.createdAt > 0L) s.createdAt else System.currentTimeMillis())
        s.deletedAt?.let { put("deletedAt", it) }
        if (s.isEdited) put("isEdited", true)
        if (s.editedAt.isNotBlank()) put("editedAt", s.editedAt)
        if (s.editHistory.isNotEmpty()) put("editHistory", s.editHistory)
    }

    fun mapToCustomer(docId: String, data: Map<String, Any>): CustomerRecord {
        val gps = data["gps"] as? Map<*, *>
        return CustomerRecord(
            id = asStr(data["id"], docId).ifBlank { docId },
            name = asStr(data["name"]),
            phone = asStr(data["phone"]),
            address = asStr(data["address"]),
            oldDebit = asDouble(data["oldDebit"], asDouble(data["openingBalance"], 0.0)),
            customSalePrice = asDouble(data["customSalePrice"], 0.0),
            salesRep = asStr(data["salesRep"]),
            gpsLat = asNullableDouble(gps?.get("lat")) ?: asNullableDouble(data["routeLat"]),
            gpsLng = asNullableDouble(gps?.get("lng")) ?: asNullableDouble(data["routeLng"]),
            visitCount = asLong(data["visitCount"], 0L),
            ledgerPage = asStr(data["ledgerPage"]),
            notes = asStr(data["notes"]),
            createdAt = asLong(data["createdAt"], 0L),
            updatedAt = asLong(data["updatedAt"], 0L),
            timestamp = asLong(data["timestamp"], 0L),
            deletedAt = asNullableLong(data["deletedAt"]),
            isEdited = asBool(data["isEdited"], false),
            editedAt = asStr(data["editedAt"]),
            editHistory = asEditHistory(data["editHistory"])
        )
    }

    fun customerToMap(c: CustomerRecord): Map<String, Any> = buildMap {
        put("id", c.id)
        put("name", c.name)
        if (c.phone.isNotBlank()) put("phone", c.phone)
        if (c.address.isNotBlank()) put("address", c.address)
        if (c.oldDebit != 0.0) put("oldDebit", c.oldDebit)
        if (c.customSalePrice > 0.0) put("customSalePrice", c.customSalePrice)
        if (c.salesRep.isNotBlank()) put("salesRep", c.salesRep)
        if (c.gpsLat != null && c.gpsLng != null) {
            put("gps", mapOf("lat" to c.gpsLat, "lng" to c.gpsLng))
        }
        if (c.visitCount > 0L) put("visitCount", c.visitCount)
        if (c.ledgerPage.isNotBlank()) put("ledgerPage", c.ledgerPage)
        if (c.notes.isNotBlank()) put("notes", c.notes)
        put("createdAt", if (c.createdAt > 0L) c.createdAt else System.currentTimeMillis())
        put("timestamp", if (c.timestamp > 0L) c.timestamp else System.currentTimeMillis())
        c.deletedAt?.let { put("deletedAt", it) }
        if (c.isEdited) put("isEdited", true)
        if (c.editedAt.isNotBlank()) put("editedAt", c.editedAt)
        if (c.editHistory.isNotEmpty()) put("editHistory", c.editHistory)
    }

    fun mapToCalculator(docId: String, data: Map<String, Any>): CalculatorRecord = CalculatorRecord(
        id = asStr(data["id"], docId).ifBlank { docId },
        seller = asStr(data["seller"], asStr(data["repName"])),
        date = asStr(data["date"], asStr(data["dateTo"])),
        dateFrom = asStr(data["dateFrom"]),
        unitPrice = asDouble(data["unitPrice"], asDouble(data["ratePerKg"], 0.0)),
        costPrice = asDouble(data["costPrice"], 0.0),
        revenue = asDouble(data["revenue"], 0.0),
        profit = asDouble(data["profit"], 0.0),
        totalCost = asDouble(data["totalCost"], 0.0),
        totalSold = asDouble(data["totalSold"], asDouble(data["totalSoldKg"], 0.0)),
        returned = asDouble(data["returned"], asDouble(data["returnedKg"], 0.0)),
        returnStore = asStr(data["returnStore"]),
        returnEntryId = asStr(data["returnEntryId"]),
        returnLogId = asStr(data["returnLogId"]),
        expiredApplied = asDouble(data["expiredApplied"], 0.0),
        returnRep = asStr(data["returnRep"]),
        returnType = asStr(data["returnType"], asStr(data["returnTarget"])),
        transferSaleId = asStr(data["transferSaleId"]),
        expired = asDouble(data["expired"], asDouble(data["expiredKg"], 0.0)),
        shared = asDouble(data["shared"], asDouble(data["sharedDeduction"], 0.0)),
        creditQty = asDouble(data["creditQty"], asDouble(data["creditQtyKg"], 0.0)),
        cashQty = asDouble(data["cashQty"], asDouble(data["cashQtyKg"], 0.0)),
        creditValue = asDouble(data["creditValue"], 0.0),
        prevColl = asDouble(data["prevColl"], 0.0),
        fieldExpenses = asDouble(data["fieldExpenses"], asDouble(data["expenseTotal"], 0.0)),
        fieldExpenseItems = asMapList(data["fieldExpenseItems"]).ifEmpty { asMapList(data["expenses"]) },
        commissionPaidItems = asMapList(data["commissionPaidItems"]).ifEmpty { asMapList(data["commissions"]) },
        totalExpected = asDouble(data["totalExpected"], asDouble(data["expectedCash"], 0.0)),
        received = asDouble(data["received"], asDouble(data["physicalCash"], 0.0)),
        commissionPerUnit = asDouble(data["commissionPerUnit"], 0.0),
        commissionPaid = asDouble(data["commissionPaid"], asDouble(data["commissionTotal"], 0.0)),
        grossCommission = asDouble(data["grossCommission"], 0.0),
        commissionPayable = asDouble(data["commissionPayable"], 0.0),
        statusText = asStr(data["statusText"]),
        statusClass = asStr(data["statusClass"]),
        linkedSalesIds = asStrList(data["linkedSalesIds"]),
        linkedRepSalesIds = asStrList(data["linkedRepSalesIds"]),
        linkedRepReturnIds = asStrList(data["linkedRepReturnIds"]),
        notes = asStr(data["notes"]),
        isMerged = asBool(data["isMerged"], false),
        timestamp = asLong(data["timestamp"], asLong(data["createdAt"], 0L)),
        updatedAtEpoch = asLong(data["updatedAt"], 0L),
        deletedAt = asNullableLong(data["deletedAt"]),
        isEdited = asBool(data["isEdited"], false),
        editedAt = asStr(data["editedAt"]),
        editHistory = asEditHistory(data["editHistory"])
    )

    fun calculatorToMap(c: CalculatorRecord): Map<String, Any> = buildMap {
        put("id", c.id)
        put("seller", c.seller)
        put("date", c.date)
        if (c.dateFrom.isNotBlank()) put("dateFrom", c.dateFrom)
        put("unitPrice", c.unitPrice)
        put("costPrice", c.costPrice)
        put("revenue", c.revenue)
        put("profit", c.profit)
        put("totalCost", c.totalCost)
        put("totalSold", c.totalSold)
        put("returned", c.returned)
        if (c.returnStore.isNotBlank()) put("returnStore", c.returnStore)
        if (c.returnEntryId.isNotBlank()) put("returnEntryId", c.returnEntryId)
        if (c.returnLogId.isNotBlank()) put("returnLogId", c.returnLogId)
        if (c.expiredApplied != 0.0) put("expiredApplied", c.expiredApplied)
        if (c.returnRep.isNotBlank()) put("returnRep", c.returnRep)
        if (c.returnType.isNotBlank()) put("returnType", c.returnType)
        if (c.transferSaleId.isNotBlank()) put("transferSaleId", c.transferSaleId)
        put("expired", c.expired)
        put("shared", c.shared)
        put("creditQty", c.creditQty)
        put("cashQty", c.cashQty)
        put("creditValue", c.creditValue)
        put("prevColl", c.prevColl)
        put("fieldExpenses", c.fieldExpenses)
        if (c.fieldExpenseItems.isNotEmpty()) put("fieldExpenseItems", c.fieldExpenseItems)
        if (c.commissionPaidItems.isNotEmpty()) put("commissionPaidItems", c.commissionPaidItems)
        put("totalExpected", c.totalExpected)
        put("received", c.received)
        put("commissionPerUnit", c.commissionPerUnit)
        put("commissionPaid", c.commissionPaid)
        put("grossCommission", c.grossCommission)
        put("commissionPayable", c.commissionPayable)
        if (c.statusText.isNotBlank()) put("statusText", c.statusText)
        if (c.statusClass.isNotBlank()) put("statusClass", c.statusClass)
        if (c.linkedSalesIds.isNotEmpty()) put("linkedSalesIds", c.linkedSalesIds)
        if (c.linkedRepSalesIds.isNotEmpty()) put("linkedRepSalesIds", c.linkedRepSalesIds)
        if (c.linkedRepReturnIds.isNotEmpty()) put("linkedRepReturnIds", c.linkedRepReturnIds)
        if (c.notes.isNotBlank()) put("notes", c.notes)
        if (c.isMerged) put("isMerged", true)
        put("timestamp", if (c.timestamp > 0L) c.timestamp else System.currentTimeMillis())
        c.deletedAt?.let { put("deletedAt", it) }
        if (c.isEdited) put("isEdited", true)
        if (c.editedAt.isNotBlank()) put("editedAt", c.editedAt)
        if (c.editHistory.isNotEmpty()) put("editHistory", c.editHistory)
    }

    fun mapToInventory(docId: String, data: Map<String, Any>): InventoryRecord = InventoryRecord(
        id = asStr(data["id"], docId).ifBlank { docId },
        name = asStr(data["name"]),
        quantity = asDouble(data["quantity"], asDouble(data["stockQty"], 0.0)),
        unit = asStr(data["unit"], asStr(data["unitName"], "kg")).ifBlank { "kg" },
        cost = asDouble(data["cost"], asDouble(data["avgCostPerKg"], 0.0)),
        totalValue = asDouble(data["totalValue"], 0.0),
        collectedKg = asDouble(data["collectedKg"], 0.0),
        conversionFactor = asDouble(data["conversionFactor"], 1.0).let { if (it <= 0.0) 1.0 else it },
        purchaseUnitName = asStr(data["purchaseUnitName"]),
        purchaseCost = asDouble(data["purchaseCost"], 0.0),
        purchaseQuantity = asDouble(data["purchaseQuantity"], 0.0),
        purchasesTracked = asBool(data["purchasesTracked"], false),
        isPurchaseLot = asBool(data["isPurchaseLot"], false),
        materialRefId = asStr(data["materialRefId"]),
        materialName = asStr(data["materialName"]),
        purchaseKg = asDouble(data["purchaseKg"], 0.0),
        purchaseDate = asStr(data["purchaseDate"]),
        paymentStatus = asStr(data["paymentStatus"]),
        supplierId = asStr(data["supplierId"]),
        supplierName = asStr(data["supplierName"]),
        supplierContact = asStr(data["supplierContact"]),
        supplierType = asStr(data["supplierType"]),
        totalPayable = asDouble(data["totalPayable"], 0.0),
        paidDate = asStr(data["paidDate"]),
        notes = asStr(data["notes"]),
        timestamp = asLong(data["timestamp"], asLong(data["createdAt"], 0L)),
        updatedAtEpoch = asLong(data["updatedAt"], 0L),
        deletedAt = asNullableLong(data["deletedAt"]),
        isEdited = asBool(data["isEdited"], false),
        editedAt = asStr(data["editedAt"]),
        editHistory = asEditHistory(data["editHistory"])
    )

    fun inventoryToMap(i: InventoryRecord): Map<String, Any> = buildMap {
        put("id", i.id)
        put("name", i.name)
        put("quantity", i.quantity)
        put("unit", i.unit)
        put("cost", i.cost)
        put("totalValue", i.totalValue)
        if (i.collectedKg != 0.0) put("collectedKg", i.collectedKg)
        if (i.conversionFactor != 1.0) put("conversionFactor", i.conversionFactor)
        if (i.purchaseUnitName.isNotBlank()) put("purchaseUnitName", i.purchaseUnitName)
        if (i.purchaseCost != 0.0) put("purchaseCost", i.purchaseCost)
        if (i.purchaseQuantity != 0.0) put("purchaseQuantity", i.purchaseQuantity)
        if (i.purchasesTracked) put("purchasesTracked", true)
        if (i.isPurchaseLot) put("isPurchaseLot", true)
        if (i.materialRefId.isNotBlank()) put("materialRefId", i.materialRefId)
        if (i.materialName.isNotBlank()) put("materialName", i.materialName)
        if (i.purchaseKg != 0.0) put("purchaseKg", i.purchaseKg)
        if (i.purchaseDate.isNotBlank()) put("purchaseDate", i.purchaseDate)
        if (i.paymentStatus.isNotBlank()) put("paymentStatus", i.paymentStatus)
        if (i.supplierId.isNotBlank()) put("supplierId", i.supplierId)
        if (i.supplierName.isNotBlank()) put("supplierName", i.supplierName)
        if (i.supplierContact.isNotBlank()) put("supplierContact", i.supplierContact)
        if (i.supplierType.isNotBlank()) put("supplierType", i.supplierType)
        if (i.totalPayable != 0.0) put("totalPayable", i.totalPayable)
        if (i.paidDate.isNotBlank()) put("paidDate", i.paidDate)
        if (i.notes.isNotBlank()) put("notes", i.notes)
        put("timestamp", if (i.timestamp > 0L) i.timestamp else System.currentTimeMillis())
        i.deletedAt?.let { put("deletedAt", it) }
        if (i.isEdited) put("isEdited", true)
        if (i.editedAt.isNotBlank()) put("editedAt", i.editedAt)
        if (i.editHistory.isNotEmpty()) put("editHistory", i.editHistory)
    }

    fun mapToFactory(docId: String, data: Map<String, Any>): FactoryRecord = FactoryRecord(
        id = asStr(data["id"], docId).ifBlank { docId },
        date = asStr(data["date"]),
        time = asStr(data["time"]),
        store = asStr(data["store"], asStr(data["storeKey"])),
        formulaId = asStr(data["formulaId"]),
        formulaName = asStr(data["formulaName"]),
        units = asDouble(data["units"], asDouble(data["unitsProduced"], 0.0)),
        totalCost = asDouble(data["totalCost"], 0.0),
        materialsCost = asDouble(data["materialsCost"], 0.0),
        additionalCost = asDouble(data["additionalCost"], 0.0),
        rawMaterialsUsed = asMapList(data["rawMaterialsUsed"]),
        materialsUsed = asMapList(data["materialsUsed"]).ifEmpty { asMapList(data["ingredientsUsed"]) },
        notes = asStr(data["notes"]),
        isMerged = asBool(data["isMerged"], false),
        timestamp = asLong(data["timestamp"], asLong(data["createdAt"], 0L)),
        updatedAtEpoch = asLong(data["updatedAt"], 0L),
        deletedAt = asNullableLong(data["deletedAt"]),
        isEdited = asBool(data["isEdited"], false),
        editedAt = asStr(data["editedAt"]),
        editHistory = asEditHistory(data["editHistory"])
    )

    fun factoryToMap(f: FactoryRecord): Map<String, Any> = buildMap {
        put("id", f.id)
        put("date", f.date)
        if (f.time.isNotBlank()) put("time", f.time)
        put("store", f.store)
        put("formulaId", f.formulaId)
        put("formulaName", f.formulaName)
        put("units", f.units)
        put("totalCost", f.totalCost)
        put("materialsCost", f.materialsCost)
        put("additionalCost", f.additionalCost)
        if (f.rawMaterialsUsed.isNotEmpty()) put("rawMaterialsUsed", f.rawMaterialsUsed)
        if (f.materialsUsed.isNotEmpty()) put("materialsUsed", f.materialsUsed)
        if (f.notes.isNotBlank()) put("notes", f.notes)
        if (f.isMerged) put("isMerged", true)
        put("timestamp", if (f.timestamp > 0L) f.timestamp else System.currentTimeMillis())
        f.deletedAt?.let { put("deletedAt", it) }
        if (f.isEdited) put("isEdited", true)
        if (f.editedAt.isNotBlank()) put("editedAt", f.editedAt)
        if (f.editHistory.isNotEmpty()) put("editHistory", f.editHistory)
    }

    fun mapToEntity(docId: String, data: Map<String, Any>): EntityRecord = EntityRecord(
        id = asStr(data["id"], docId).ifBlank { docId },
        name = asStr(data["name"]),
        type = asStr(data["type"], asStr(data["entityType"], "party")),
        contact = asStr(data["contact"], asStr(data["phone"])),
        phone = asStr(data["phone"], asStr(data["contact"])),
        wallet = asStr(data["wallet"]),
        ledgerPage = asStr(data["ledgerPage"]),
        isSupplier = asBool(data["isSupplier"], false),
        isExpenseEntity = asBool(data["isExpenseEntity"], false),
        openingBalance = asDouble(data["openingBalance"], 0.0),
        notes = asStr(data["notes"]),
        timestamp = asLong(data["timestamp"], asLong(data["createdAt"], 0L)),
        updatedAtEpoch = asLong(data["updatedAt"], 0L),
        deletedAt = asNullableLong(data["deletedAt"]),
        isEdited = asBool(data["isEdited"], false),
        editedAt = asStr(data["editedAt"]),
        editHistory = asEditHistory(data["editHistory"])
    )

    fun entityToMap(e: EntityRecord): Map<String, Any> = buildMap {
        put("id", e.id)
        put("name", e.name)
        put("type", e.type)
        if (e.contact.isNotBlank()) put("contact", e.contact)
        if (e.phone.isNotBlank()) put("phone", e.phone)
        if (e.wallet.isNotBlank()) put("wallet", e.wallet)
        if (e.ledgerPage.isNotBlank()) put("ledgerPage", e.ledgerPage)
        if (e.isSupplier) put("isSupplier", true)
        if (e.isExpenseEntity) put("isExpenseEntity", true)
        if (e.openingBalance != 0.0) put("openingBalance", e.openingBalance)
        if (e.notes.isNotBlank()) put("notes", e.notes)
        put("timestamp", if (e.timestamp > 0L) e.timestamp else System.currentTimeMillis())
        e.deletedAt?.let { put("deletedAt", it) }
        if (e.isEdited) put("isEdited", true)
        if (e.editedAt.isNotBlank()) put("editedAt", e.editedAt)
        if (e.editHistory.isNotEmpty()) put("editHistory", e.editHistory)
    }

    fun mapToTransaction(docId: String, data: Map<String, Any>): PaymentTransaction {
        val rawType = asStr(data["type"], asStr(data["txType"], "OUT")).uppercase()
        val normType = if (rawType == "IN" || rawType == "TRANSFER_IN") "IN" else "OUT"
        return PaymentTransaction(
            id = asStr(data["id"], docId).ifBlank { docId },
            date = asStr(data["date"]),
            time = asStr(data["time"]),
            entityId = asStr(data["entityId"]),
            entityName = asStr(data["entityName"]),
            entityType = asStr(data["entityType"], asStr(data["entityCategory"])),
            type = normType,
            amount = asDouble(data["amount"], 0.0),
            description = asStr(data["description"], asStr(data["notes"])),
            paymentMethod = asStr(data["paymentMethod"], asStr(data["paymentChannel"], "Cash")),
            category = asStr(data["category"]),
            isPayable = asBool(data["isPayable"], false),
            isExpense = asBool(data["isExpense"], false),
            expenseId = asStr(data["expenseId"]),
            supplierCreditAmount = asDouble(data["supplierCreditAmount"], 0.0),
            materialId = asStr(data["materialId"]),
            materialIds = asStrList(data["materialIds"]),
            consumedCredits = asMapList(data["consumedCredits"]),
            isTransfer = asBool(data["isTransfer"], rawType.startsWith("TRANSFER")),
            transferPairId = asStr(data["transferPairId"]),
            transferDirection = asStr(data["transferDirection"]),
            peerEntityName = asStr(data["peerEntityName"]),
            isOpeningBalance = asBool(data["isOpeningBalance"], false),
            isMerged = asBool(data["isMerged"], false),
            isSettled = asBool(data["isSettled"], false),
            timestamp = asLong(data["timestamp"], asLong(data["createdAt"], 0L)),
            updatedAtEpoch = asLong(data["updatedAt"], 0L),
            deletedAt = asNullableLong(data["deletedAt"]),
            isEdited = asBool(data["isEdited"], false),
            editedAt = asStr(data["editedAt"]),
            editHistory = asEditHistory(data["editHistory"])
        )
    }

    fun transactionToMap(t: PaymentTransaction): Map<String, Any> = buildMap {
        put("id", t.id)
        put("date", t.date)
        if (t.time.isNotBlank()) put("time", t.time)
        if (t.entityId.isNotBlank()) put("entityId", t.entityId)
        put("entityName", t.entityName)
        if (t.entityType.isNotBlank()) put("entityType", t.entityType)
        put("type", t.type)
        put("amount", t.amount)
        if (t.description.isNotBlank()) put("description", t.description)
        put("paymentMethod", t.paymentMethod)
        if (t.category.isNotBlank()) put("category", t.category)
        if (t.isPayable) put("isPayable", true)
        if (t.isExpense) put("isExpense", true)
        if (t.expenseId.isNotBlank()) put("expenseId", t.expenseId)
        if (t.supplierCreditAmount != 0.0) put("supplierCreditAmount", t.supplierCreditAmount)
        if (t.materialId.isNotBlank()) put("materialId", t.materialId)
        if (t.materialIds.isNotEmpty()) put("materialIds", t.materialIds)
        if (t.consumedCredits.isNotEmpty()) put("consumedCredits", t.consumedCredits)
        if (t.isTransfer) {
            put("isTransfer", true)
            if (t.transferPairId.isNotBlank()) put("transferPairId", t.transferPairId)
            if (t.transferDirection.isNotBlank()) put("transferDirection", t.transferDirection)
            if (t.peerEntityName.isNotBlank()) put("peerEntityName", t.peerEntityName)
        }
        if (t.isOpeningBalance) put("isOpeningBalance", true)
        if (t.isMerged) put("isMerged", true)
        if (t.isSettled) put("isSettled", true)
        put("timestamp", if (t.timestamp > 0L) t.timestamp else System.currentTimeMillis())
        t.deletedAt?.let { put("deletedAt", it) }
        if (t.isEdited) put("isEdited", true)
        if (t.editedAt.isNotBlank()) put("editedAt", t.editedAt)
        if (t.editHistory.isNotEmpty()) put("editHistory", t.editHistory)
    }

    fun mapToExpense(docId: String, data: Map<String, Any>): ExpenseRecord = ExpenseRecord(
        id = asStr(data["id"], docId).ifBlank { docId },
        date = asStr(data["date"]),
        name = asStr(data["name"], asStr(data["category"], "Expense")),
        category = asStr(data["category"], "Other"),
        amount = asDouble(data["amount"], 0.0),
        description = asStr(data["description"]),
        notes = asStr(data["notes"]),
        entityId = asStr(data["entityId"]),
        entityName = asStr(data["entityName"]),
        paymentMethod = asStr(data["paymentMethod"], "Cash"),
        linkedTxId = asStr(data["linkedTxId"]),
        isMerged = asBool(data["isMerged"], false),
        timestamp = asLong(data["timestamp"], asLong(data["createdAt"], 0L)),
        updatedAtEpoch = asLong(data["updatedAt"], 0L),
        deletedAt = asNullableLong(data["deletedAt"]),
        isEdited = asBool(data["isEdited"], false),
        editedAt = asStr(data["editedAt"]),
        editHistory = asEditHistory(data["editHistory"])
    )

    fun expenseToMap(e: ExpenseRecord): Map<String, Any> = buildMap {
        put("id", e.id)
        put("date", e.date)
        put("name", e.name)
        put("category", e.category)
        put("amount", e.amount)
        if (e.description.isNotBlank()) put("description", e.description)
        if (e.notes.isNotBlank()) put("notes", e.notes)
        if (e.entityId.isNotBlank()) put("entityId", e.entityId)
        if (e.entityName.isNotBlank()) put("entityName", e.entityName)
        if (e.paymentMethod.isNotBlank()) put("paymentMethod", e.paymentMethod)
        if (e.linkedTxId.isNotBlank()) put("linkedTxId", e.linkedTxId)
        if (e.isMerged) put("isMerged", true)
        put("timestamp", if (e.timestamp > 0L) e.timestamp else System.currentTimeMillis())
        e.deletedAt?.let { put("deletedAt", it) }
        if (e.isEdited) put("isEdited", true)
        if (e.editedAt.isNotBlank()) put("editedAt", e.editedAt)
        if (e.editHistory.isNotEmpty()) put("editHistory", e.editHistory)
    }

    fun mapToReturn(docId: String, data: Map<String, Any>): ReturnRecord = ReturnRecord(
        id = asStr(data["id"], docId).ifBlank { docId },
        date = asStr(data["date"]),
        store = asStr(data["store"], asStr(data["returnStore"])),
        returnStore = asStr(data["returnStore"], asStr(data["store"])),
        seller = asStr(data["seller"]),
        customerName = asStr(data["customerName"]),
        quantity = asDouble(data["quantity"], 0.0),
        expiredQty = asDouble(data["expiredQty"], 0.0),
        recoveredQty = asDouble(data["recoveredQty"], 0.0),
        recovered = asBool(data["recovered"], false),
        recoveredDate = asStr(data["recoveredDate"]),
        returnType = asStr(data["returnType"]),
        notes = asStr(data["notes"]),
        isMerged = asBool(data["isMerged"], false),
        timestamp = asLong(data["timestamp"], asLong(data["createdAt"], 0L)),
        updatedAtEpoch = asLong(data["updatedAt"], 0L),
        deletedAt = asNullableLong(data["deletedAt"])
    )

    fun returnToMap(r: ReturnRecord): Map<String, Any> = buildMap {
        put("id", r.id)
        put("date", r.date)
        if (r.store.isNotBlank()) put("store", r.store)
        if (r.returnStore.isNotBlank()) put("returnStore", r.returnStore)
        if (r.seller.isNotBlank()) put("seller", r.seller)
        if (r.customerName.isNotBlank()) put("customerName", r.customerName)
        put("quantity", r.quantity)
        if (r.expiredQty != 0.0) put("expiredQty", r.expiredQty)
        if (r.recoveredQty != 0.0) put("recoveredQty", r.recoveredQty)
        if (r.recovered) put("recovered", true)
        if (r.recoveredDate.isNotBlank()) put("recoveredDate", r.recoveredDate)
        if (r.returnType.isNotBlank()) put("returnType", r.returnType)
        if (r.notes.isNotBlank()) put("notes", r.notes)
        if (r.isMerged) put("isMerged", true)
        put("timestamp", if (r.timestamp > 0L) r.timestamp else System.currentTimeMillis())
        r.deletedAt?.let { put("deletedAt", it) }
    }

    fun mapToDailyLedgerEntry(data: Map<String, Any>): DailyLedgerEntry {
        val link = data["link"] as? Map<*, *>
        val kind = asNullableStr(link?.get("kind"))
        val origin = asNullableStr(link?.get("origin"))
        val part = asNullableStr(link?.get("part"))
        val linkName = asNullableStr(link?.get("name"))
        val linkEntityId = asNullableStr(link?.get("entityId"))
        val inferredCol = asNullableStr(link?.get("col"))
            ?: asNullableStr(data["sourceCollection"])
            ?: when (kind) {
                "customer" -> "sales"
                "calc" -> "calculator"
                "expense" -> "expenses"
                "entity" -> if (asNullableStr(link?.get("expenseId")) != null) "expenses" else "transactions"
                else -> null
            }
        val inferredDocId = asNullableStr(link?.get("id"))
            ?: asNullableStr(data["sourceDocId"])
            ?: asNullableStr(link?.get("saleId"))
            ?: asNullableStr(link?.get("calcId"))
            ?: asNullableStr(link?.get("txId"))
            ?: asNullableStr(link?.get("expenseId"))

        return DailyLedgerEntry(
            id = asStr(data["id"]),
            type = asStr(data["type"], asStr(data["entryType"], "IN")).uppercase(),
            amount = asDouble(data["amount"], 0.0),
            category = asStr(data["category"], "General"),
            scope = asStr(data["scope"], asStr(data["expenseScope"])).uppercase().let {
                if (it == "NONE") "" else it
            },
            mode = asStr(data["mode"], asStr(data["channel"], "Cash")),
            toMode = asStr(data["toMode"], asStr(data["toChannel"])),
            party = asStr(data["party"], asStr(data["partyName"])),
            note = asStr(data["note"], asStr(data["notes"])),
            ref = asStr(data["ref"]),
            date = asStr(data["date"]),
            timestamp = asLong(data["timestamp"], 0L),
            updatedAt = asLong(data["updatedAt"], 0L),
            linkCollection = inferredCol,
            linkDocId = inferredDocId,
            linkKind = kind,
            linkOrigin = origin,
            linkPart = part,
            linkName = linkName,
            linkEntityId = linkEntityId
        )
    }

    fun dailyLedgerEntryToMap(e: DailyLedgerEntry): Map<String, Any> = buildMap {
        put("id", e.id)
        put("type", e.type)
        put("amount", e.amount)
        put("category", e.category)
        put("scope", e.scope)
        put("mode", e.mode)
        put("toMode", e.toMode)
        put("party", e.party)
        put("note", e.note)
        put("ref", e.ref)
        put("date", e.date)
        put("timestamp", if (e.timestamp > 0L) e.timestamp else System.currentTimeMillis())
        put("updatedAt", if (e.updatedAt > 0L) e.updatedAt else System.currentTimeMillis())
        if (!e.linkCollection.isNullOrBlank() || !e.linkKind.isNullOrBlank()) {
            val linkMap = buildMap<String, Any> {
                val resolvedKind = e.linkKind ?: when (e.linkCollection) {
                    "sales" -> "customer"
                    "calculator" -> "calc"
                    "expenses" -> "entity"
                    "transactions" -> "entity"
                    else -> "entity"
                }
                put("kind", resolvedKind)
                put("origin", e.linkOrigin ?: "tab")
                e.linkPart?.takeIf { it.isNotBlank() }?.let { put("part", it) }
                val nm = e.linkName?.takeIf { it.isNotBlank() } ?: e.party.takeIf { it.isNotBlank() }
                nm?.let { put("name", it) }
                e.linkEntityId?.takeIf { it.isNotBlank() }?.let { put("entityId", it) }
                if (!e.linkDocId.isNullOrBlank()) {
                    when (resolvedKind) {
                        "customer" -> put("saleId", e.linkDocId)
                        "calc" -> put("calcId", e.linkDocId)
                        else -> if (e.linkCollection == "expenses") put("expenseId", e.linkDocId) else put("txId", e.linkDocId)
                    }
                    put("id", e.linkDocId)
                }
                if (!e.linkCollection.isNullOrBlank()) put("col", e.linkCollection)
            }
            put("link", linkMap)
        }
    }

    fun mapToDeletion(docId: String, data: Map<String, Any>): DeletionTombstone {
        val snapMap = data["snapshot"] as? Map<*, *>
        val snapJson = if (snapMap != null) {
            mapToJson(snapMap.entries.associate { (k, v) -> k.toString() to v })
        } else {
            asStr(data["snapshotJson"])
        }
        return DeletionTombstone(
            id = asStr(data["id"], docId).ifBlank { docId },
            recordId = asStr(data["recordId"], asStr(data["id"], docId)),
            collection = asStr(data["collection"], asStr(data["recordType"])),
            recordType = asStr(data["recordType"], asStr(data["collection"])),
            deletedAt = asLong(data["deletedAt"], 0L),
            expiresAt = asLong(data["expiresAt"], 0L),
            displayName = asStr(data["displayName"]),
            displayDetail = asStr(data["displayDetail"]),
            displayAmount = asStr(data["displayAmount"]),
            snapshotJson = snapJson,
            deletedBy = asStr(data["deleted_by"], asStr(data["deletedBy"])),
            syncedToCloud = asBool(data["syncedToCloud"], true)
        )
    }

    fun deletionToLocalMap(d: DeletionTombstone): Map<String, Any> = buildMap {
        put("id", d.id)
        put("recordId", d.recordId)
        put("collection", d.collection)
        put("recordType", d.recordType)
        put("deletedAt", d.deletedAt)
        put("expiresAt", d.expiresAt)
        put("displayName", d.displayName)
        put("displayDetail", d.displayDetail)
        put("displayAmount", d.displayAmount)
        if (d.snapshotJson.isNotBlank()) put("snapshot", jsonToMap(d.snapshotJson))
        if (d.deletedBy.isNotBlank()) put("deleted_by", d.deletedBy)
        put("syncedToCloud", d.syncedToCloud)
    }

    fun mapToPending(docId: String, data: Map<String, Any>): PendingApproval {
        val sub = data["submittedBy"] as? Map<*, *>
        val sum = data["summary"] as? Map<*, *>
        val dec = data["decidedBy"] as? Map<*, *>
        val rawDataMap = (data["data"] as? Map<*, *> ?: data["payloadData"] as? Map<*, *>)
            ?.entries?.mapNotNull { (k, v) -> if (k != null && v != null) k.toString() to v else null }
            ?.toMap() ?: emptyMap()
        return PendingApproval(
            id = asStr(data["id"], docId).ifBlank { docId },
            collection = asStr(data["collection"], asStr(data["targetCollection"])),
            docId = asStr(data["docId"], asStr(data["targetDocId"])),
            action = asStr(data["action"], "set"),
            dataMap = rawDataMap,
            isEdit = asBool(data["isEdit"], false),
            status = asStr(data["status"], "pending"),
            submittedByUid = asStr(sub?.get("uid"), asStr(data["submittedByUid"])),
            submittedByName = asStr(sub?.get("name"), asStr(data["submittedByName"])),
            submittedByEmail = asStr(sub?.get("email"), asStr(data["submittedByEmail"])),
            submittedAt = asLong(data["submittedAt"], asLong(data["createdAt"], 0L)),
            summaryTitle = asStr(sum?.get("title"), asStr(data["summaryTitle"])),
            summaryBody = asStr(sum?.get("body"), asStr(data["summaryDetail"])),
            decidedByUid = asNullableStr(dec?.get("uid")) ?: asNullableStr(data["reviewedByUid"]),
            decidedByName = asNullableStr(dec?.get("name")),
            decidedAt = asNullableLong(data["decidedAt"])
        )
    }

    fun pendingToMap(p: PendingApproval): Map<String, Any> = buildMap {
        put("id", p.id)
        put("collection", p.collection)
        put("docId", p.docId)
        put("action", p.action)
        if (p.action != "delete") put("data", p.dataMap) else put("data", emptyMap<String, Any>())
        put("isEdit", p.isEdit)
        put("status", p.status)
        put("submittedBy", mapOf(
            "uid" to p.submittedByUid,
            "name" to p.submittedByName,
            "email" to p.submittedByEmail
        ))
        put("submittedAt", p.submittedAt)
        put("summary", mapOf("title" to p.summaryTitle, "body" to p.summaryBody))
        if (p.decidedByUid != null) {
            put("decidedBy", mapOf("uid" to p.decidedByUid, "name" to (p.decidedByName ?: "")))
        }
        p.decidedAt?.let { put("decidedAt", it) }
    }

    fun mapToPhoto(docId: String, data: Map<String, Any>): PhotoBlob = PhotoBlob(
        key = asStr(data["key"], docId),
        dataUri = asStr(data["data"]),
        deleted = asBool(data["deleted"], false),
        updatedAt = asLong(data["updatedAt"], 0L)
    )

    // --- Settings Merger ---

    fun mergeSettings(
        settingsConfigMap: Map<String, Any>,
        formulasConfigMap: Map<String, Any>,
        teamReps: List<String>,
        expenseCats: List<String>,
        fieldExpenseCats: List<String>,
        commissionCats: List<String>,
        yearCloseMap: Map<String, Any>,
        localTheme: String?,
        localAppSettings: Map<String, Any>
    ): WorkspaceSettings {
        val innerSettings = (settingsConfigMap["settings"] as? Map<*, *>)?.entries
            ?.associate { (k, v) -> k.toString() to v } ?: settingsConfigMap
        val repsFromConfig = asStrList(settingsConfigMap["reps"])
        val mergedReps = (teamReps + repsFromConfig).distinct().filter { it.isNotBlank() }
        val modes = asStrList(innerSettings["paymentModes"]).ifEmpty {
            asStrList(localAppSettings["paymentModes"]).ifEmpty {
                listOf("Cash", "Bank", "Easypaisa", "JazzCash")
            }
        }
        val mergedExpenseCats = expenseCats.ifEmpty {
            listOf("Fuel", "Wages", "Rent", "Electricity", "Freight", "Maintenance", "Tea & Meals", "Office", "Other")
        }
        val mergedFieldCats = fieldExpenseCats.ifEmpty {
            listOf("Fuel", "Food", "Toll", "Repair", "Other")
        }
        val mergedCommCats = commissionCats.ifEmpty {
            listOf("Sales Commission", "Recovery Bonus", "Target Bonus", "Other")
        }
        val trackingRaw = formulasConfigMap["tracking"] as? Map<*, *> ?: emptyMap<String, Any>()
        val trackingMap = trackingRaw.entries.mapNotNull { (k, v) ->
            k?.let { it.toString() to asBool(v, true) }
        }.toMap()

        return WorkspaceSettings(
            currencySymbol = asStr(innerSettings["currencySymbol"], asStr(localAppSettings["currencySymbol"], "Rs")).ifBlank { "Rs" },
            financialYearStartMonth = asLong(innerSettings["financialYearStartMonth"], asLong(localAppSettings["financialYearStartMonth"], 7L)).toInt().coerceIn(1, 12),
            hideSensitiveMoney = asBool(innerSettings["hideSensitiveMoney"], asBool(localAppSettings["hideSensitiveMoney"], false)),
            biometricLockEnabled = asBool(localAppSettings["biometricLockEnabled"], false),
            soundEnabled = asBool(localAppSettings["soundEnabled"], true),
            hapticsEnabled = asBool(localAppSettings["hapticsEnabled"], true),
            themeMode = localTheme?.takeIf { it.isNotBlank() } ?: asStr(innerSettings["themeMode"], "light"),
            repProfile = asStr(settingsConfigMap["repProfile"], asStr(localAppSettings["repProfile"])),
            reps = mergedReps,
            paymentModes = modes,
            expenseCategories = mergedExpenseCats,
            fieldExpenseCategories = mergedFieldCats,
            commissionCategories = mergedCommCats,
            defaultStorePrices = asDoubleMap(formulasConfigMap["defaults"]),
            storeCosts = asDoubleMap(formulasConfigMap["costs"]),
            costAdjustments = asDoubleMap(formulasConfigMap["adjustment"]),
            storeTracking = trackingMap,
            fyCloseCount = asLong(yearCloseMap["fyCloseCount"], asLong(localAppSettings["fyCloseCount"], 0L)).toInt(),
            lastYearCloseAt = asLong(yearCloseMap["triggeredAt"], asLong(localAppSettings["lastYearCloseAt"], 0L))
        )
    }

    // --- Web-Exact .gznd Encrypted Backup V4 (CryptoEngine 1:1 Parity) ---
    // Format: "GZND_ENC_V4" (11 bytes) + SHA-256(uid) (32 bytes) + salt (32 bytes) + iv (12 bytes) + AES-GCM ciphertext

    private val MAGIC_V4 = "GZND_ENC_V4".toByteArray(Charsets.US_ASCII)
    private const val SALT_LEN = 32
    private const val IV_LEN = 12
    private const val UID_HASH_LEN = 32
    private const val PBKDF2_ITERS_V4 = 210_000

    private fun sha256(input: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))

    private fun deriveKeyV4(email: String, password: String, uid: String, salt: ByteArray): SecretKeySpec {
        val ikm = "${email.lowercase().trim()}:$password:$uid".toCharArray()
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        val spec = PBEKeySpec(ikm, salt, PBKDF2_ITERS_V4, 256)
        val raw = factory.generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    fun encryptGzndV4(jsonText: String, email: String, password: String, uid: String): ByteArray {
        val rng = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rng.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rng.nextBytes(it) }
        val uidHash = sha256(uid)
        val key = deriveKeyV4(email, password, uid, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(jsonText.toByteArray(Charsets.UTF_8))
        return ByteArray(MAGIC_V4.size + UID_HASH_LEN + SALT_LEN + IV_LEN + ct.size).apply {
            var pos = 0
            System.arraycopy(MAGIC_V4, 0, this, pos, MAGIC_V4.size); pos += MAGIC_V4.size
            System.arraycopy(uidHash, 0, this, pos, UID_HASH_LEN); pos += UID_HASH_LEN
            System.arraycopy(salt, 0, this, pos, SALT_LEN); pos += SALT_LEN
            System.arraycopy(iv, 0, this, pos, IV_LEN); pos += IV_LEN
            System.arraycopy(ct, 0, this, pos, ct.size)
        }
    }

    fun decryptGzndV4(bytes: ByteArray, email: String, password: String, uid: String): String {
        val minLen = MAGIC_V4.size + UID_HASH_LEN + SALT_LEN + IV_LEN + 16
        require(bytes.size >= minLen) { "Invalid .gznd backup file" }
        for (i in MAGIC_V4.indices) {
            require(bytes[i] == MAGIC_V4[i]) { "Unsupported backup format (expected GZND_ENC_V4)" }
        }
        var pos = MAGIC_V4.size
        val storedUidHash = bytes.copyOfRange(pos, pos + UID_HASH_LEN); pos += UID_HASH_LEN
        val actualUidHash = sha256(uid)
        require(storedUidHash.contentEquals(actualUidHash)) {
            "This backup belongs to a different workspace account"
        }
        val salt = bytes.copyOfRange(pos, pos + SALT_LEN); pos += SALT_LEN
        val iv = bytes.copyOfRange(pos, pos + IV_LEN); pos += IV_LEN
        val ct = bytes.copyOfRange(pos, bytes.size)
        val key = deriveKeyV4(email, password, uid, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
