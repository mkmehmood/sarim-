package com.gullzubair.sarim.data.local

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.gullzubair.sarim.data.model.UserProfile
import com.gullzubair.sarim.data.model.WorkspaceSettings
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID

class OfflineSyncRepository(private val dao: OfflineDao) {

    fun observeProfile(uid: String): Flow<UserProfile?> =
        dao.observeProfile(uid)
            .combine(kotlinx.coroutines.flow.flowOf(Unit)) { entity, _ ->
                entity?.let { WorkspaceJsonCodec.toProfile(it) }
            }
            .flowOn(Dispatchers.Default)

    suspend fun getCachedProfile(uid: String): UserProfile? = withContext(Dispatchers.IO) {
        dao.getProfileOnce(uid)?.let { WorkspaceJsonCodec.toProfile(it) }
    }

    suspend fun cacheProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        if (profile.uid.isBlank()) return@withContext
        dao.upsertProfile(WorkspaceJsonCodec.toEntity(profile))
    }

    fun observePendingSyncCount(ownerUid: String): Flow<Int> =
        dao.observePendingSyncCount(ownerUid)

    suspend fun getPendingSyncCount(ownerUid: String): Int = withContext(Dispatchers.IO) {
        dao.getPendingSyncOps(ownerUid).size
    }

    fun observeCachedWorkspaceSnapshot(profile: UserProfile): Flow<WorkspaceSnapshot?> {
        val ownerUid = profile.workspaceOwnerUid.ifBlank { profile.uid }
        return combine(
            dao.observeWorkspaceRecords(ownerUid),
            dao.observePendingSyncCount(ownerUid)
        ) { records, pendingCount ->
            if (records.isEmpty()) null else decodeSnapshot(profile, ownerUid, records, pendingCount)
        }.flowOn(Dispatchers.Default)
    }

    suspend fun getWorkspaceSnapshotOnce(profile: UserProfile): WorkspaceSnapshot? = withContext(Dispatchers.Default) {
        val ownerUid = profile.workspaceOwnerUid.ifBlank { profile.uid }
        val records = dao.getWorkspaceRecordsOnce(ownerUid)
        if (records.isEmpty()) return@withContext null
        val pendingCount = dao.getPendingSyncOps(ownerUid).size
        decodeSnapshot(profile, ownerUid, records, pendingCount)
    }

    private fun decodeSnapshot(
        profile: UserProfile,
        ownerUid: String,
        records: List<WorkspaceRecordEntity>,
        pendingCount: Int
    ): WorkspaceSnapshot {
        val byCol = records.groupBy { it.collection }
        val settingsList = byCol["settings"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeSettings(it.docId, it.payloadJson) }.getOrNull()
        }
        val stores = byCol["stores"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeStore(it.docId, it.payloadJson) }.getOrNull()
        }
        val formulas = byCol["formulas"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeFormula(it.docId, it.payloadJson) }.getOrNull()
        }
        val customers = byCol["customers"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeCustomer(it.docId, it.payloadJson) }.getOrNull()
        }
        val entities = byCol["entities"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeEntity(it.docId, it.payloadJson) }.getOrNull()
        }
        val inventory = byCol["inventory"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeInventory(it.docId, it.payloadJson) }.getOrNull()
        }
        val production = byCol["production"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeProduction(it.docId, it.payloadJson) }.getOrNull()
        }.sortedByDescending { it.date }
        val sales = byCol["sales"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeSale(it.docId, it.payloadJson) }.getOrNull()
        }.sortedByDescending { it.date }
        val repRecords = byCol["rep_records"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeRep(it.docId, it.payloadJson) }.getOrNull()
        }.sortedByDescending { it.date }
        val calculator = byCol["calculator"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeCalculator(it.docId, it.payloadJson) }.getOrNull()
        }.sortedByDescending { it.dateTo }
        val factory = byCol["factory"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeFactory(it.docId, it.payloadJson) }.getOrNull()
        }.sortedByDescending { it.date }
        val transactions = byCol["transactions"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeTransaction(it.docId, it.payloadJson) }.getOrNull()
        }.sortedByDescending { it.date }
        val ledgerEntries = byCol["ledger_entries"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeLedger(it.docId, it.payloadJson) }.getOrNull()
        }.sortedByDescending { it.date }
        val pendingApprovals = byCol["pending"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodePending(it.docId, it.payloadJson) }.getOrNull()
        }.sortedByDescending { it.createdAt?.seconds ?: 0L }
        val invites = byCol["workspace_invites"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeInvite(it.docId, it.payloadJson) }.getOrNull()
        }
        val teamMembers = byCol["team_members"].orEmpty().mapNotNull {
            runCatching { WorkspaceJsonCodec.decodeUserProfile(it.docId, it.payloadJson) }.getOrNull()
        }.ifEmpty { listOf(profile) }

        return WorkspaceSnapshot(
            profile = profile,
            settings = settingsList.firstOrNull { it.id == "preferences" }
                ?: settingsList.firstOrNull()
                ?: WorkspaceSettings(ownerUid = ownerUid),
            stores = stores,
            formulas = formulas,
            customers = customers,
            entities = entities,
            inventory = inventory,
            production = production,
            sales = sales,
            repRecords = repRecords,
            calculator = calculator,
            factory = factory,
            transactions = transactions,
            ledgerEntries = ledgerEntries,
            pendingApprovals = pendingApprovals,
            invites = invites,
            teamMembers = teamMembers,
            pendingSyncCount = pendingCount
        )
    }

    suspend fun cacheWorkspaceSnapshot(ownerUid: String, snapshot: WorkspaceSnapshot) = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val entities = buildList {
            add(
                WorkspaceRecordEntity(
                    ownerUid = ownerUid,
                    collection = "settings",
                    docId = snapshot.settings.id.ifBlank { "preferences" },
                    payloadJson = WorkspaceJsonCodec.encodeSettings(snapshot.settings),
                    sortDate = "",
                    recycled = false,
                    updatedAtEpochMs = now
                )
            )
            snapshot.stores.forEach { s ->
                add(WorkspaceRecordEntity(ownerUid, "stores", s.id, WorkspaceJsonCodec.encodeStore(s), "", false, now))
            }
            snapshot.formulas.forEach { f ->
                add(WorkspaceRecordEntity(ownerUid, "formulas", f.id, WorkspaceJsonCodec.encodeFormula(f), "", false, now))
            }
            snapshot.customers.forEach { c ->
                add(WorkspaceRecordEntity(ownerUid, "customers", c.id, WorkspaceJsonCodec.encodeCustomer(c), "", c.recycled, now))
            }
            snapshot.entities.forEach { e ->
                add(WorkspaceRecordEntity(ownerUid, "entities", e.id, WorkspaceJsonCodec.encodeEntity(e), "", e.recycled, now))
            }
            snapshot.inventory.forEach { i ->
                add(WorkspaceRecordEntity(ownerUid, "inventory", i.id, WorkspaceJsonCodec.encodeInventory(i), "", i.recycled, now))
            }
            snapshot.production.forEach { p ->
                add(WorkspaceRecordEntity(ownerUid, "production", p.id, WorkspaceJsonCodec.encodeProduction(p), p.date, p.recycled, now))
            }
            snapshot.sales.forEach { s ->
                add(WorkspaceRecordEntity(ownerUid, "sales", s.id, WorkspaceJsonCodec.encodeSale(s), s.date, s.recycled, now))
            }
            snapshot.repRecords.forEach { r ->
                add(WorkspaceRecordEntity(ownerUid, "rep_records", r.id, WorkspaceJsonCodec.encodeRep(r), r.date, r.recycled, now))
            }
            snapshot.calculator.forEach { c ->
                add(WorkspaceRecordEntity(ownerUid, "calculator", c.id, WorkspaceJsonCodec.encodeCalculator(c), c.dateTo, c.recycled, now))
            }
            snapshot.factory.forEach { f ->
                add(WorkspaceRecordEntity(ownerUid, "factory", f.id, WorkspaceJsonCodec.encodeFactory(f), f.date, f.recycled, now))
            }
            snapshot.transactions.forEach { t ->
                add(WorkspaceRecordEntity(ownerUid, "transactions", t.id, WorkspaceJsonCodec.encodeTransaction(t), t.date, t.recycled, now))
            }
            snapshot.ledgerEntries.forEach { l ->
                add(WorkspaceRecordEntity(ownerUid, "ledger_entries", l.id, WorkspaceJsonCodec.encodeLedger(l), l.date, l.recycled, now))
            }
            snapshot.pendingApprovals.forEach { p ->
                add(WorkspaceRecordEntity(ownerUid, "pending", p.id, WorkspaceJsonCodec.encodePending(p), p.recordDate, false, now))
            }
            snapshot.invites.forEach { inv ->
                add(WorkspaceRecordEntity(ownerUid, "workspace_invites", inv.id, WorkspaceJsonCodec.encodeInvite(inv), "", false, now))
            }
            snapshot.teamMembers.forEach { m ->
                val mid = m.uid.ifBlank { m.id }
                if (mid.isNotBlank()) {
                    add(WorkspaceRecordEntity(ownerUid, "team_members", mid, WorkspaceJsonCodec.encodeUserProfile(m), "", false, now))
                }
            }
        }
        dao.upsertProfile(WorkspaceJsonCodec.toEntity(snapshot.profile))
        dao.replaceCleanWorkspaceRecords(ownerUid, entities)
    }

    suspend fun applyLocalMutation(
        ownerUid: String,
        collection: String,
        docId: String,
        payload: Map<String, Any?>,
        isUpdate: Boolean
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = if (isUpdate) dao.getRecordOnce(ownerUid, collection, docId) else null
        val jsonObj = if (existing != null) {
            runCatching { JSONObject(existing.payloadJson) }.getOrElse { JSONObject() }
        } else {
            JSONObject()
        }
        jsonObj.put("id", docId)
        if (!jsonObj.has("ownerUid")) jsonObj.put("ownerUid", ownerUid)
        payload.forEach { (k, v) ->
            if (v != null) {
                jsonObj.put(k, WorkspaceJsonCodec.anyToJsonValue(v) ?: JSONObject.NULL)
            }
        }
        val sortDate = jsonObj.optString("date").ifBlank { jsonObj.optString("dateTo") }
        val recycled = jsonObj.optBoolean("recycled", false)
        dao.upsertRecord(
            WorkspaceRecordEntity(
                ownerUid = ownerUid,
                collection = collection,
                docId = docId,
                payloadJson = jsonObj.toString(),
                sortDate = sortDate,
                recycled = recycled,
                updatedAtEpochMs = now
            )
        )
    }

    suspend fun applyLocalDelete(ownerUid: String, collection: String, docId: String) = withContext(Dispatchers.IO) {
        dao.deleteRecord(ownerUid, collection, docId)
    }

    suspend fun enqueueSyncOp(
        ownerUid: String,
        collection: String,
        docId: String,
        opType: String,
        payload: Map<String, Any?> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val opId = "op_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val op = PendingSyncOpEntity(
            opId = opId,
            ownerUid = ownerUid,
            collection = collection,
            docId = docId,
            opType = opType,
            payloadJson = WorkspaceJsonCodec.mapToJson(payload),
            createdAtEpochMs = System.currentTimeMillis()
        )
        dao.enqueueSyncOp(op)
        opId
    }

    suspend fun markOpSynced(opId: String) = withContext(Dispatchers.IO) {
        dao.deleteSyncOp(opId)
    }

    suspend fun discardSyncOp(opId: String) = withContext(Dispatchers.IO) {
        dao.deleteSyncOp(opId)
    }

    suspend fun flushPendingSyncOps(
        ownerUid: String,
        db: FirebaseFirestore,
        timeoutPerOpMs: Long = 4000L
    ): Result<Int> = withContext(Dispatchers.IO) {
        val ops = dao.getPendingSyncOps(ownerUid)
        if (ops.isEmpty()) return@withContext Result.success(0)

        var synced = 0
        for (op in ops) {
            try {
                val acked = withTimeoutOrNull(timeoutPerOpMs) {
                    executeSingleOp(db, op)
                    true
                }
                if (acked == true) {
                    dao.deleteSyncOp(op.opId)
                    synced++
                } else {
                    return@withContext if (synced > 0) {
                        Result.success(synced)
                    } else {
                        Result.failure(IllegalStateException("Offline: ${ops.size} changes queued in local Room DB"))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                when (e.code) {
                    FirebaseFirestoreException.Code.NOT_FOUND -> {
                        if (op.opType == "DELETE" || op.opType == "REVOKE_INVITE") {
                            dao.deleteSyncOp(op.opId)
                            synced++
                        } else {
                            dao.markSyncOpFailed(op.opId, e.localizedMessage)
                        }
                    }
                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    FirebaseFirestoreException.Code.INVALID_ARGUMENT,
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION -> {
                        dao.markSyncOpFailed(op.opId, e.localizedMessage)
                    }
                    else -> {
                        dao.markSyncOpFailed(op.opId, e.localizedMessage)
                        return@withContext Result.failure(e)
                    }
                }
            } catch (e: Exception) {
                dao.markSyncOpFailed(op.opId, e.localizedMessage)
                return@withContext Result.failure(e)
            }
        }
        Result.success(synced)
    }

    private suspend fun executeSingleOp(db: FirebaseFirestore, op: PendingSyncOpEntity) {
        val rawMap = if (op.payloadJson.isBlank()) emptyMap() else WorkspaceJsonCodec.jsonToFirestoreMap(op.payloadJson)
        val workspaceColRef = db.collection("users").document(op.ownerUid).collection(op.collection)
        when (op.opType) {
            "SET" -> {
                val payload = rawMap.toMutableMap().apply {
                    put("ownerUid", op.ownerUid)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                workspaceColRef.document(op.docId).set(payload).await()
            }
            "UPDATE" -> {
                val payload = rawMap.toMutableMap().apply {
                    remove("createdAt")
                    remove("createdByUid")
                    remove("ownerUid")
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                workspaceColRef.document(op.docId).update(payload).await()
            }
            "DELETE" -> {
                workspaceColRef.document(op.docId).delete().await()
            }
            "SETTINGS" -> {
                val settingsRef = db.collection("users").document(op.ownerUid)
                    .collection("settings").document(op.docId.ifBlank { "preferences" })
                val exists = settingsRef.get().await().exists()
                if (exists) {
                    val updatePayload = rawMap.toMutableMap().apply {
                        remove("ownerUid")
                        remove("createdAt")
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    settingsRef.update(updatePayload).await()
                } else {
                    val createPayload = rawMap.toMutableMap().apply {
                        put("ownerUid", op.ownerUid)
                        put("createdAt", FieldValue.serverTimestamp())
                        put("updatedAt", FieldValue.serverTimestamp())
                    }
                    settingsRef.set(createPayload).await()
                }
            }
            "INVITE" -> {
                val payload = rawMap.toMutableMap().apply {
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                db.collection("workspace_invites").document(op.docId).set(payload).await()
            }
            "REVOKE_INVITE" -> {
                db.collection("workspace_invites").document(op.docId).delete().await()
            }
            "MEMBER_ROLE" -> {
                val payload = rawMap.toMutableMap().apply {
                    remove("createdAt")
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                db.collection("users").document(op.docId).update(payload).await()
            }
        }
    }
}
