package com.gullzubair.sarim.data.local

import android.util.Base64
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.gullzubair.sarim.push.ApprovalPush
import com.gullzubair.sarim.data.model.CalculatorRecord
import com.gullzubair.sarim.data.model.CustomerRecord
import com.gullzubair.sarim.data.model.DailyLedgerEntry
import com.gullzubair.sarim.data.model.DeletionTombstone
import com.gullzubair.sarim.data.model.EntityRecord
import com.gullzubair.sarim.data.model.ExpenseRecord
import com.gullzubair.sarim.data.model.FactoryRecord
import com.gullzubair.sarim.data.model.FormulaConfig
import com.gullzubair.sarim.data.model.InventoryRecord
import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.data.model.PendingApproval
import com.gullzubair.sarim.data.model.PhotoBlob
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.ReturnRecord
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.UserProfile
import com.gullzubair.sarim.data.model.WorkspaceAccountRecord
import com.gullzubair.sarim.data.model.WorkspaceInvite
import com.gullzubair.sarim.data.model.WorkspaceSettings
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineSyncRepository(private val dao: OfflineDao) {

    companion object {
        val SYNC_COLLECTIONS = listOf(
            "production", "sales", "rep", "clients", "customers", "calculator",
            "inventory", "factory", "entities", "transactions", "expenses", "returns"
        )
        const val KV_OWNER_UID = "_workspace_owner_uid"
        const val PHOTO_KEY_PREFIX = "photo:"
        const val LEGACY_PHOTOS_KEY = "photos"
    }

    fun observeProfile(uid: String): Flow<UserProfile?> =
        dao.observeProfile(uid).map { it?.let(WorkspaceJsonCodec::toProfile) }.flowOn(Dispatchers.Default)

    suspend fun getCachedProfile(uid: String): UserProfile? = withContext(Dispatchers.IO) {
        dao.getProfileOnce(uid)?.let(WorkspaceJsonCodec::toProfile)
    }

    suspend fun cacheProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        if (profile.uid.isBlank()) return@withContext
        dao.upsertProfile(WorkspaceJsonCodec.toEntity(profile))
    }

    suspend fun ensureWorkspaceScopedKv(ownerUid: String) = withContext(Dispatchers.IO) {
        val current = dao.getKv(KV_OWNER_UID)?.valueJson?.trim('"')
        if (!current.isNullOrBlank() && current != ownerUid) {
            dao.clearKv()
        }
        dao.putKv(KvStoreEntity(KV_OWNER_UID, "\"$ownerUid\"", System.currentTimeMillis()))
    }

    suspend fun getKvRaw(key: String): String? = withContext(Dispatchers.IO) {
        dao.getKv(key)?.valueJson
    }

    suspend fun setKvRaw(key: String, json: String) = withContext(Dispatchers.IO) {
        dao.putKv(KvStoreEntity(key, json, System.currentTimeMillis()))
    }

    suspend fun setKvBatch(entries: Map<String, String>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.putKvBatch(entries.entries.map { (k, v) -> KvStoreEntity(k, v, now) })
    }

    fun observeOutboxCount(ownerUid: String): Flow<Int> =
        dao.observeOutboxCount(ownerUid)

    suspend fun getOutboxCount(ownerUid: String): Int = withContext(Dispatchers.IO) {
        dao.getOutboxOnce(ownerUid).size
    }

    fun observeCachedWorkspaceSnapshot(profile: UserProfile): Flow<WorkspaceSnapshot?> {
        val ownerUid = profile.effectiveOwnerUid
        return combine(
            dao.observeKvMeta().mapLatest { dao.getAllKvOnce() },
            dao.observeOutboxCount(ownerUid)
        ) { kvList, pendingCount ->
            val map = kvList.associate { it.key to it.valueJson }
            val storedOwner = map[KV_OWNER_UID]?.trim('"')
            if (map.isEmpty() || (storedOwner != null && storedOwner != ownerUid)) {
                null
            } else {
                decodeSnapshot(profile, map, pendingCount)
            }
        }
            .catch { e ->
                if (e is CancellationException) throw e
                emit(null) // never let a local-cache read failure crash the app; live data still flows
            }
            .flowOn(Dispatchers.Default)
    }

    suspend fun getWorkspaceSnapshotOnce(profile: UserProfile): WorkspaceSnapshot? = withContext(Dispatchers.Default) {
        val ownerUid = profile.effectiveOwnerUid
        val kvList = try {
            dao.getAllKvOnce()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withContext null
        }
        if (kvList.isEmpty()) return@withContext null
        val map = kvList.associate { it.key to it.valueJson }
        val storedOwner = map[KV_OWNER_UID]?.trim('"')
        if (storedOwner != null && storedOwner != ownerUid) return@withContext null
        decodeSnapshot(profile, map, dao.getOutboxOnce(ownerUid).size)
    }

    private fun decodeSnapshot(
        profile: UserProfile,
        kv: Map<String, String>,
        pendingCount: Int
    ): WorkspaceSnapshot {
        val erasedIds = WorkspaceJsonCodec.jsonToStringList(kv["erased"]).toSet()
        val deletions = WorkspaceJsonCodec.jsonArrayToMapList(kv["deletions"])
            .map { WorkspaceJsonCodec.mapToDeletion(it["id"]?.toString().orEmpty(), it) }
            .filter { it.id !in erasedIds }
        val deletedIds = deletions.map { it.recordId.ifBlank { it.id } }.toSet()

        fun <T> decodeCol(
            key: String,
            mapper: (String, Map<String, Any>) -> T,
            idOf: (T) -> String,
            deletedAtOf: (T) -> Long?
        ): List<T> {
            return WorkspaceJsonCodec.jsonArrayToMapList(kv[key]).mapNotNull { raw ->
                val id = raw["id"]?.toString().orEmpty()
                if (id.isBlank() || id in erasedIds || id in deletedIds) return@mapNotNull null
                val item = runCatching { mapper(id, raw) }.getOrNull() ?: return@mapNotNull null
                if (deletedAtOf(item) != null) null else item
            }
        }

        val stores = WorkspaceJsonCodec.jsonArrayToMapList(kv["stores"])
            .mapNotNull { runCatching { WorkspaceJsonCodec.mapToStore(it) }.getOrNull() }
            .filter { it.deletedAt == null }
        val formulas = WorkspaceJsonCodec.jsonArrayToMapList(kv["formulas"])
            .mapNotNull { runCatching { WorkspaceJsonCodec.mapToFormula(it) }.getOrNull() }

        val customers = decodeCol("customers", WorkspaceJsonCodec::mapToCustomer, { it.id }, { it.deletedAt })
        val clients = decodeCol("clients", WorkspaceJsonCodec::mapToCustomer, { it.id }, { it.deletedAt })
        val entities = decodeCol("entities", WorkspaceJsonCodec::mapToEntity, { it.id }, { it.deletedAt })
        val inventory = decodeCol("inventory", WorkspaceJsonCodec::mapToInventory, { it.id }, { it.deletedAt })
        val production = decodeCol("production", WorkspaceJsonCodec::mapToProduction, { it.id }, { it.deletedAt })
            .sortedByDescending { it.date }
        val sales = decodeCol("sales", WorkspaceJsonCodec::mapToSale, { it.id }, { it.deletedAt })
            .sortedByDescending { it.date }
        val repRecords = decodeCol("rep", WorkspaceJsonCodec::mapToSale, { it.id }, { it.deletedAt })
            .sortedByDescending { it.date }
        val calculator = decodeCol("calculator", WorkspaceJsonCodec::mapToCalculator, { it.id }, { it.deletedAt })
            .sortedByDescending { it.date }
        val factory = decodeCol("factory", WorkspaceJsonCodec::mapToFactory, { it.id }, { it.deletedAt })
            .sortedByDescending { it.date }
        val transactions = decodeCol("transactions", WorkspaceJsonCodec::mapToTransaction, { it.id }, { it.deletedAt })
            .sortedByDescending { it.date }
        val expenses = decodeCol("expenses", WorkspaceJsonCodec::mapToExpense, { it.id }, { it.deletedAt })
            .sortedByDescending { it.date }
        val returns = decodeCol("returns", WorkspaceJsonCodec::mapToReturn, { it.id }, { it.deletedAt })
            .sortedByDescending { it.date }
        val ledgerEntries = WorkspaceJsonCodec.jsonArrayToMapList(kv["daily_ledger"])
            .mapNotNull { runCatching { WorkspaceJsonCodec.mapToDailyLedgerEntry(it) }.getOrNull() }
            .sortedByDescending { it.date }

        val pendingApprovals = WorkspaceJsonCodec.jsonArrayToMapList(kv["pending"])
            .mapNotNull { runCatching { WorkspaceJsonCodec.mapToPending(it["id"]?.toString().orEmpty(), it) }.getOrNull() }
            .sortedByDescending { it.submittedAt }

        val invites = WorkspaceJsonCodec.jsonArrayToMapList(kv["workspace_invites"])
            .mapNotNull { runCatching { WorkspaceJsonCodec.mapToInvite(it["id"]?.toString().orEmpty(), it) }.getOrNull() }
        val accounts = WorkspaceJsonCodec.jsonArrayToMapList(kv["accounts"])
            .mapNotNull { runCatching { WorkspaceJsonCodec.mapToAccountRecord(it) }.getOrNull() }
        val teamMembers = WorkspaceJsonCodec.jsonArrayToMapList(kv["team_members"])
            .mapNotNull { runCatching { WorkspaceJsonCodec.mapToUserProfile(it["uid"]?.toString().orEmpty(), it) }.getOrNull() }
            .ifEmpty { listOf(profile) }

        // Legacy format: one "photos" row holding every image (can exceed the CursorWindow limit).
        val photosMapRaw = WorkspaceJsonCodec.jsonToMap(kv["photos"])
        val legacyPhotos = photosMapRaw.entries.mapNotNull { (k, v) ->
            when (v) {
                is String -> k to PhotoBlob(key = k, dataUri = v, deleted = false)
                is Map<*, *> -> {
                    val dataUri = v["data"]?.toString().orEmpty()
                    val deleted = v["deleted"] as? Boolean ?: false
                    k to PhotoBlob(key = k, dataUri = dataUri, deleted = deleted)
                }
                else -> null
            }
        }.toMap()
        // Current format: one "photo:<key>" row per image (raw data URI). These win over legacy.
        val rowPhotos = kv.entries
            .filter { it.key.startsWith(PHOTO_KEY_PREFIX) && it.value.isNotBlank() }
            .associate { (rowKey, value) ->
                val k = rowKey.removePrefix(PHOTO_KEY_PREFIX)
                k to PhotoBlob(key = k, dataUri = value, deleted = false)
            }
        val photos = legacyPhotos + rowPhotos

        val settingsMap = WorkspaceJsonCodec.jsonToMap(kv["settings"])
        val formulasConfigMap = mapOf(
            "defaults" to WorkspaceJsonCodec.jsonToMap(kv["defaults"]),
            "costs" to WorkspaceJsonCodec.jsonToMap(kv["costs"]),
            "adjustment" to WorkspaceJsonCodec.jsonToMap(kv["adjustment"]),
            "tracking" to WorkspaceJsonCodec.jsonToMap(kv["tracking"])
        )
        val mergedSettings = WorkspaceJsonCodec.mergeSettings(
            settingsConfigMap = settingsMap,
            formulasConfigMap = formulasConfigMap,
            teamReps = WorkspaceJsonCodec.jsonToStringList(kv["reps"]),
            expenseCats = WorkspaceJsonCodec.jsonToStringList(kv["categories"]),
            fieldExpenseCats = WorkspaceJsonCodec.jsonToStringList(kv["field_expense_categories"]),
            commissionCats = WorkspaceJsonCodec.jsonToStringList(kv["commission_categories"]),
            yearCloseMap = WorkspaceJsonCodec.jsonToMap(kv["yearCloseSignal"]),
            localTheme = kv["theme"]?.trim('"'),
            localAppSettings = settingsMap
        )

        val syncStats = buildMap<String, Long> {
            (SYNC_COLLECTIONS + listOf("deletions", "photos", "daily_ledger", "stores", "formulas", "settings")).forEach { col ->
                val ts = kv["lastSync_$col"]?.toLongOrNull() ?: kv["${col}_timestamp"]?.toLongOrNull() ?: 0L
                if (ts > 0L) put(col, ts)
            }
        }

        return WorkspaceSnapshot(
            profile = profile,
            settings = mergedSettings,
            stores = stores,
            formulas = formulas,
            customers = customers,
            clients = clients,
            entities = entities,
            inventory = inventory,
            production = production,
            sales = sales,
            repRecords = repRecords,
            calculator = calculator,
            factory = factory,
            transactions = transactions,
            expenses = expenses,
            returns = returns,
            ledgerEntries = ledgerEntries,
            deletions = deletions,
            erasedIds = erasedIds,
            photos = photos,
            pendingApprovals = pendingApprovals,
            invites = invites,
            accounts = accounts,
            teamMembers = teamMembers,
            syncStats = syncStats,
            pendingSyncCount = pendingCount
        )
    }

    suspend fun cacheWorkspaceSnapshot(ownerUid: String, snapshot: WorkspaceSnapshot) = withContext(Dispatchers.Default) {
        ensureWorkspaceScopedKv(ownerUid)
        val pendingOps = dao.getOutboxOnce(ownerUid)
        val dirtyCollections = pendingOps.filter { it.action in listOf("SET", "DELETE") }.map { it.col }.toSet()
        val now = System.currentTimeMillis()
        val batch = LinkedHashMap<String, String>()

        fun putColIfClean(col: String, items: List<Map<String, Any>>) {
            if (col !in dirtyCollections) {
                batch[col] = WorkspaceJsonCodec.mapListToJson(items)
                batch["lastSync_$col"] = now.toString()
            }
        }

        putColIfClean("production", snapshot.production.map(WorkspaceJsonCodec::productionToMap))
        putColIfClean("sales", snapshot.sales.map(WorkspaceJsonCodec::saleToMap))
        putColIfClean("rep", snapshot.repRecords.map(WorkspaceJsonCodec::saleToMap))
        putColIfClean("clients", snapshot.clients.map(WorkspaceJsonCodec::customerToMap))
        putColIfClean("customers", snapshot.customers.map(WorkspaceJsonCodec::customerToMap))
        putColIfClean("calculator", snapshot.calculator.map(WorkspaceJsonCodec::calculatorToMap))
        putColIfClean("inventory", snapshot.inventory.map(WorkspaceJsonCodec::inventoryToMap))
        putColIfClean("factory", snapshot.factory.map(WorkspaceJsonCodec::factoryToMap))
        putColIfClean("entities", snapshot.entities.map(WorkspaceJsonCodec::entityToMap))
        putColIfClean("transactions", snapshot.transactions.map(WorkspaceJsonCodec::transactionToMap))
        putColIfClean("expenses", snapshot.expenses.map(WorkspaceJsonCodec::expenseToMap))
        putColIfClean("returns", snapshot.returns.map(WorkspaceJsonCodec::returnToMap))

        if ("deletions" !in dirtyCollections) {
            batch["deletions"] = WorkspaceJsonCodec.mapListToJson(snapshot.deletions.map(WorkspaceJsonCodec::deletionToLocalMap))
        }
        batch["erased"] = WorkspaceJsonCodec.stringListToJson(snapshot.erasedIds.toList())
        batch["stores"] = WorkspaceJsonCodec.mapListToJson(snapshot.stores.map(WorkspaceJsonCodec::storeToMap))
        batch["formulas"] = WorkspaceJsonCodec.mapListToJson(snapshot.formulas.map(WorkspaceJsonCodec::formulaToMap))
        batch["daily_ledger"] = WorkspaceJsonCodec.mapListToJson(snapshot.ledgerEntries.map(WorkspaceJsonCodec::dailyLedgerEntryToMap))
        batch["pending"] = WorkspaceJsonCodec.mapListToJson(snapshot.pendingApprovals.map(WorkspaceJsonCodec::pendingToMap))
        batch["accounts"] = WorkspaceJsonCodec.mapListToJson(snapshot.accounts.map(WorkspaceJsonCodec::accountRecordToMap))

        batch["categories"] = WorkspaceJsonCodec.stringListToJson(snapshot.settings.expenseCategories)
        batch["field_expense_categories"] = WorkspaceJsonCodec.stringListToJson(snapshot.settings.fieldExpenseCategories)
        batch["commission_categories"] = WorkspaceJsonCodec.stringListToJson(snapshot.settings.commissionCategories)
        batch["reps"] = WorkspaceJsonCodec.stringListToJson(snapshot.settings.reps)
        batch["repProfile"] = "\"${snapshot.settings.repProfile}\""
        batch["defaults"] = WorkspaceJsonCodec.mapToJson(snapshot.settings.defaultStorePrices)
        batch["costs"] = WorkspaceJsonCodec.mapToJson(snapshot.settings.storeCosts)
        batch["adjustment"] = WorkspaceJsonCodec.mapToJson(snapshot.settings.costAdjustments)
        batch["tracking"] = WorkspaceJsonCodec.mapToJson(snapshot.settings.storeTracking)
        batch["theme"] = "\"${snapshot.settings.themeMode}\""
        batch["settings"] = WorkspaceJsonCodec.mapToJson(
            mapOf(
                "currencySymbol" to snapshot.settings.currencySymbol,
                "financialYearStartMonth" to snapshot.settings.financialYearStartMonth,
                "hideSensitiveMoney" to snapshot.settings.hideSensitiveMoney,
                "biometricLockEnabled" to snapshot.settings.biometricLockEnabled,
                "soundEnabled" to snapshot.settings.soundEnabled,
                "hapticsEnabled" to snapshot.settings.hapticsEnabled,
                "themeMode" to snapshot.settings.themeMode,
                "paymentModes" to snapshot.settings.paymentModes,
                "repProfile" to snapshot.settings.repProfile,
                "reps" to snapshot.settings.reps,
                "fyCloseCount" to snapshot.settings.fyCloseCount,
                "lastYearCloseAt" to snapshot.settings.lastYearCloseAt
            )
        )
        batch["lastSync_photos"] = now.toString()
        batch["workspace_invites"] = WorkspaceJsonCodec.mapListToJson(
            snapshot.invites.map { inv ->
                buildMap {
                    put("id", inv.id)
                    put("ownerUid", inv.ownerUid)
                    put("ownerName", inv.ownerName)
                    put("memberEmail", inv.memberEmail)
                    put("displayName", inv.displayName)
                    put("roles", inv.roles)
                    put("approved", inv.approved)
                    put("status", inv.status)
                    inv.acceptedByUid?.let { put("acceptedByUid", it) }
                }
            }
        )
        batch["team_members"] = WorkspaceJsonCodec.mapListToJson(
            snapshot.teamMembers.map { m ->
                buildMap {
                    put("uid", m.uid)
                    put("email", m.email)
                    put("displayName", m.displayName)
                    m.ownerUid?.let { put("ownerUid", it) }
                    put("role", m.role)
                    put("roles", m.roles)
                    put("approved", m.approved)
                    if (m.createdBy.isNotBlank()) put("createdBy", m.createdBy)
                }
            }
        )

        dao.upsertProfile(WorkspaceJsonCodec.toEntity(snapshot.profile))
        setKvBatch(batch)
        syncPhotoRows(snapshot.photos)
    }

    /** Mirrors [photos] into per-photo kv rows, rewriting only new/changed ones and dropping stale ones. */
    private suspend fun syncPhotoRows(photos: Map<String, PhotoBlob>) {
        val wanted = photos.filterValues { !it.deleted && it.dataUri.isNotBlank() }
            .mapKeys { (k, _) -> PHOTO_KEY_PREFIX + k }
        val existing = dao.getKvMeta()
            .filter { it.key.startsWith(PHOTO_KEY_PREFIX) }
            .associate { it.key to it.valueLength }
        val now = System.currentTimeMillis()
        val upserts = wanted.filter { (rowKey, blob) -> existing[rowKey] != blob.dataUri.length.toLong() }
            .map { (rowKey, blob) -> KvStoreEntity(rowKey, blob.dataUri, now) }
        val stale = existing.keys.filter { it !in wanted } + LEGACY_PHOTOS_KEY
        dao.applyKvChanges(upserts, stale)
    }

    /** Splits the legacy single "photos" row into per-photo rows (no-op once migrated). */
    private suspend fun migrateLegacyPhotos() {
        val legacy = dao.getKv(LEGACY_PHOTOS_KEY)?.valueJson ?: return
        val now = System.currentTimeMillis()
        val rows = WorkspaceJsonCodec.jsonToMap(legacy).mapNotNull { (k, v) ->
            val data = when (v) {
                is String -> v
                is Map<*, *> -> if (v["deleted"] as? Boolean == true) "" else v["data"]?.toString().orEmpty()
                else -> ""
            }
            if (data.isBlank()) null else KvStoreEntity(PHOTO_KEY_PREFIX + k, data, now)
        }
        dao.applyKvChanges(rows, listOf(LEGACY_PHOTOS_KEY))
    }

    // --- Local Mutation Helpers (kv_store + ndapp_outbox) ---

    suspend fun upsertLocalCollectionDoc(
        ownerUid: String,
        col: String,
        docId: String,
        docMap: Map<String, Any>
    ) = withContext(Dispatchers.IO) {
        ensureWorkspaceScopedKv(ownerUid)
        val now = System.currentTimeMillis()
        val list = WorkspaceJsonCodec.jsonArrayToMapList(dao.getKv(col)?.valueJson).toMutableList()
        val idx = list.indexOfFirst { it["id"]?.toString() == docId }
        val normalized = docMap + ("id" to docId)
        if (idx >= 0) list[idx] = normalized else list.add(0, normalized)
        dao.putKvBatch(
            listOf(
                KvStoreEntity(col, WorkspaceJsonCodec.mapListToJson(list), now),
                KvStoreEntity("lastLocalMod_$col", now.toString(), now)
            )
        )
    }

    suspend fun removeLocalCollectionDoc(ownerUid: String, col: String, docId: String) = withContext(Dispatchers.IO) {
        ensureWorkspaceScopedKv(ownerUid)
        val now = System.currentTimeMillis()
        val list = WorkspaceJsonCodec.jsonArrayToMapList(dao.getKv(col)?.valueJson)
            .filterNot { it["id"]?.toString() == docId }
        dao.putKvBatch(
            listOf(
                KvStoreEntity(col, WorkspaceJsonCodec.mapListToJson(list), now),
                KvStoreEntity("lastLocalMod_$col", now.toString(), now)
            )
        )
    }

    suspend fun upsertLocalDeletion(ownerUid: String, tombstone: DeletionTombstone) = withContext(Dispatchers.IO) {
        ensureWorkspaceScopedKv(ownerUid)
        val now = System.currentTimeMillis()
        val list = WorkspaceJsonCodec.jsonArrayToMapList(dao.getKv("deletions")?.valueJson).toMutableList()
        val idx = list.indexOfFirst { it["id"]?.toString() == tombstone.id }
        val map = WorkspaceJsonCodec.deletionToLocalMap(tombstone)
        if (idx >= 0) list[idx] = map else list.add(0, map)
        dao.putKv(KvStoreEntity("deletions", WorkspaceJsonCodec.mapListToJson(list), now))
    }

    suspend fun removeLocalDeletionAndOptionallyErase(ownerUid: String, docId: String, erasePermanently: Boolean) =
        withContext(Dispatchers.IO) {
            ensureWorkspaceScopedKv(ownerUid)
            val now = System.currentTimeMillis()
            val dels = WorkspaceJsonCodec.jsonArrayToMapList(dao.getKv("deletions")?.valueJson)
                .filterNot { it["id"]?.toString() == docId || it["recordId"]?.toString() == docId }
            val updates = mutableListOf(KvStoreEntity("deletions", WorkspaceJsonCodec.mapListToJson(dels), now))
            if (erasePermanently) {
                val erased = (WorkspaceJsonCodec.jsonToStringList(dao.getKv("erased")?.valueJson) + docId).distinct()
                updates.add(KvStoreEntity("erased", WorkspaceJsonCodec.stringListToJson(erased), now))
            }
            dao.putKvBatch(updates)
        }

    suspend fun replaceLocalArray(ownerUid: String, kvKey: String, items: List<Map<String, Any>>) =
        withContext(Dispatchers.IO) {
            ensureWorkspaceScopedKv(ownerUid)
            val now = System.currentTimeMillis()
            dao.putKvBatch(
                listOf(
                    KvStoreEntity(kvKey, WorkspaceJsonCodec.mapListToJson(items), now),
                    KvStoreEntity("${kvKey}_timestamp", now.toString(), now)
                )
            )
        }

    suspend fun upsertLocalPhoto(ownerUid: String, photoKey: String, dataUri: String?, deleted: Boolean) =
        withContext(Dispatchers.IO) {
            ensureWorkspaceScopedKv(ownerUid)
            migrateLegacyPhotos()
            val rowKey = PHOTO_KEY_PREFIX + photoKey
            if (deleted || dataUri.isNullOrBlank()) {
                dao.deleteKv(rowKey)
            } else {
                dao.putKv(KvStoreEntity(rowKey, dataUri, System.currentTimeMillis()))
            }
        }

    suspend fun enqueueOutbox(
        ownerUid: String,
        action: String,
        col: String,
        docId: String,
        payload: Map<String, Any> = emptyMap()
    ): Long = withContext(Dispatchers.IO) {
        dao.enqueueOutbox(
            NdappOutboxEntity(
                ownerUid = ownerUid,
                action = action,
                col = col,
                docId = docId,
                payloadJson = WorkspaceJsonCodec.mapToJson(payload),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markOutboxSynced(opId: Long) = withContext(Dispatchers.IO) {
        if (opId > 0L) dao.deleteOutboxById(opId)
    }

    suspend fun discardOutboxOp(opId: Long) = withContext(Dispatchers.IO) {
        if (opId > 0L) dao.deleteOutboxById(opId)
    }

    suspend fun flushOutbox(
        ownerUid: String,
        db: FirebaseFirestore,
        timeoutPerOpMs: Long = 5000L
    ): Result<Int> = withContext(Dispatchers.IO) {
        val ops = dao.getOutboxOnce(ownerUid)
        if (ops.isEmpty()) return@withContext Result.success(0)

        var synced = 0
        for (op in ops) {
            try {
                val acked = withTimeoutOrNull(timeoutPerOpMs) {
                    executeOutboxOp(db, op)
                    true
                }
                if (acked == true) {
                    dao.deleteOutboxById(op.id)
                    synced++
                } else {
                    return@withContext if (synced > 0) Result.success(synced)
                    else Result.failure(IllegalStateException("Offline: ${ops.size} queued items in ndapp_outbox"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                when (e.code) {
                    FirebaseFirestoreException.Code.NOT_FOUND -> {
                        if (op.action == "DELETE") {
                            dao.deleteOutboxById(op.id)
                            synced++
                        } else {
                            dao.markOutboxFailed(op.id, e.localizedMessage)
                        }
                    }
                    FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    FirebaseFirestoreException.Code.INVALID_ARGUMENT,
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION -> {
                        dao.markOutboxFailed(op.id, e.localizedMessage)
                    }
                    else -> {
                        dao.markOutboxFailed(op.id, e.localizedMessage)
                        return@withContext Result.failure(e)
                    }
                }
            } catch (e: Exception) {
                dao.markOutboxFailed(op.id, e.localizedMessage)
                return@withContext Result.failure(e)
            }
        }
        Result.success(synced)
    }

    private suspend fun executeOutboxOp(db: FirebaseFirestore, op: NdappOutboxEntity) {
        val userRef = db.collection("users").document(op.ownerUid)
        val rawMap = WorkspaceJsonCodec.jsonToMap(op.payloadJson).toMutableMap()
        when (op.action) {
            "SET" -> {
                rawMap["id"] = op.docId
                rawMap["updatedAt"] = FieldValue.serverTimestamp()
                userRef.collection(op.col).document(op.docId).set(rawMap, SetOptions.merge()).await()
            }
            "DELETE" -> {
                val batch = db.batch()
                batch.delete(userRef.collection(op.col).document(op.docId))
                if (rawMap.isNotEmpty()) {
                    rawMap["deletedAt"] = FieldValue.serverTimestamp()
                    batch.set(userRef.collection("deletions").document(op.docId), rawMap, SetOptions.merge())
                }
                batch.commit().await()
            }
            "SINGLETON" -> {
                userRef.collection(op.col).document(op.docId).set(rawMap, SetOptions.merge()).await()
            }
            "PHOTO" -> {
                val encodedId = Base64.encodeToString(
                    op.docId.toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
                rawMap["updatedAt"] = FieldValue.serverTimestamp()
                userRef.collection("photos").document(encodedId).set(rawMap, SetOptions.merge()).await()
            }
            "PENDING" -> {
                userRef.collection("pending").document(op.docId).set(rawMap, SetOptions.merge()).await()
                // Submission that was queued while offline: alert the admins now it has landed.
                if (rawMap["status"] == "pending") ApprovalPush.notifyAdmins(op.docId)
            }
            "OWNER_PING" -> {
                userRef.update("lastWrite", System.currentTimeMillis()).await()
            }
        }
    }
}
