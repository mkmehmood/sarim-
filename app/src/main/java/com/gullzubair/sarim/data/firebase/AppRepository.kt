package com.gullzubair.sarim.data.firebase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import com.gullzubair.sarim.R
import com.gullzubair.sarim.data.local.OfflineSyncRepository
import com.gullzubair.sarim.data.local.SarimOfflineDatabase
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
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
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.InputGuards
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class AppRepository(
    val db: FirebaseFirestore,
    val offlineRepo: OfflineSyncRepository? = null,
    private val appContext: Context? = null
) {

    constructor(context: Context) : this(
        db = configureFirestore(
            FirebaseFirestore.getInstance(
                context.applicationContext.getString(R.string.firestore_database_id)
            )
        ),
        offlineRepo = OfflineSyncRepository(
            SarimOfflineDatabase.getInstance(context.applicationContext).offlineDao()
        ),
        appContext = context.applicationContext
    )

    private val auth = Firebase.auth

    fun isOnline(): Boolean {
        val ctx = appContext ?: return true
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun requireUserId(): String = auth.currentUser?.uid
        ?: throw IllegalStateException("User must be signed in before accessing Firestore.")

    private fun userRoot(ownerUid: String) = db.collection("users").document(ownerUid)

    private fun workspaceCol(ownerUid: String, collection: String) =
        userRoot(ownerUid).collection(collection)

    private fun <T> observeCollectionMapped(
        ownerUid: String,
        viewerUid: String,
        collection: String,
        mapper: (String, Map<String, Any>) -> T
    ): Flow<List<T>> = flow {
        val colRef = workspaceCol(ownerUid, collection)
        val path = "users/$ownerUid/$collection"
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.documents.mapNotNull { doc ->
                        val raw = WorkspaceJsonCodec.snapshotToMap(doc)
                        if (raw["_placeholder"] == true) null
                        else runCatching { mapper(doc.id, raw) }.getOrNull()
                    }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (unwrapped == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == viewerUid) {
                        handleFirestoreError(unwrapped ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    private fun observeSingletonMap(
        ownerUid: String,
        viewerUid: String,
        collection: String,
        docId: String
    ): Flow<Map<String, Any>> = flow {
        val docRef = workspaceCol(ownerUid, collection).document(docId)
        val path = "users/$ownerUid/$collection/$docId"
        emitAll(
            docRef.snapshots()
                .map { snap -> if (snap.exists()) WorkspaceJsonCodec.snapshotToMap(snap) else emptyMap() }
                .catch { error ->
                    val unwrapped = unwrapFirestoreError(error)
                    if (unwrapped == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == viewerUid) {
                        handleFirestoreError(unwrapped ?: error, OperationType.GET, path)
                    }
                    emit(emptyMap())
                }
        )
    }

    suspend fun ensureUserProfileAndWorkspace(): Result<UserProfile> {
        return try {
            val user = auth.currentUser ?: error("Not authenticated")
            val uid = user.uid
            val email = (user.email ?: "$uid@local.user").lowercase().take(160)
            val displayName = InputGuards.sanitizeName(user.displayName?.ifBlank { null } ?: email.substringBefore("@"))

            offlineRepo?.getCachedProfile(uid)?.let { cached ->
                if (!isOnline()) return Result.success(cached)
            }

            val profileRef = db.collection("users").document(uid)
            val cachedFirestoreSnap = runCatching { profileRef.get(Source.CACHE).await() }.getOrNull()
            if (cachedFirestoreSnap != null && cachedFirestoreSnap.exists() && !isOnline()) {
                val profile = WorkspaceJsonCodec.mapToUserProfile(uid, WorkspaceJsonCodec.snapshotToMap(cachedFirestoreSnap))
                offlineRepo?.cacheProfile(profile)
                return Result.success(profile)
            }

            if (!isOnline()) {
                offlineRepo?.getCachedProfile(uid)?.let { return Result.success(it) }
                return Result.failure(IllegalStateException("No internet connection. Connect once to download your workspace offline cache."))
            }

            val existingSnap = withTimeoutOrNull(8000L) { profileRef.get().await() }
                ?: return offlineRepo?.getCachedProfile(uid)?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException("Workspace connection timed out. Check your connection and retry."))

            if (existingSnap.exists()) {
                val profile = WorkspaceJsonCodec.mapToUserProfile(uid, WorkspaceJsonCodec.snapshotToMap(existingSnap))
                offlineRepo?.cacheProfile(profile)
                Result.success(profile)
            } else {
                var invitedProfile: UserProfile? = null
                if (user.isEmailVerified && !user.email.isNullOrBlank()) {
                    val invites = try {
                        db.collection("workspace_invites")
                            .whereEqualTo("memberEmail", email)
                            .get()
                            .await()
                            .documents
                            .mapNotNull { doc ->
                                runCatching {
                                    WorkspaceJsonCodec.mapToInvite(doc.id, WorkspaceJsonCodec.snapshotToMap(doc))
                                }.getOrNull()
                            }
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val match = invites.firstOrNull { it.status == "pending" && it.approved }
                    if (match != null) {
                        val payload = mapOf(
                            "uid" to uid,
                            "email" to email,
                            "displayName" to displayName,
                            "ownerUid" to match.ownerUid,
                            "role" to "user",
                            "roles" to match.roles.filter { it != "admin" }.take(8).ifEmpty { listOf("rep") },
                            "approved" to true,
                            "inviteId" to match.id,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        profileRef.set(payload).await()
                        db.collection("workspace_invites").document(match.id).update(
                            mapOf(
                                "status" to "accepted",
                                "acceptedByUid" to uid,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                        val loaded = profileRef.get().await()
                        invitedProfile = WorkspaceJsonCodec.mapToUserProfile(uid, WorkspaceJsonCodec.snapshotToMap(loaded))
                    }
                }

                val resolved = if (invitedProfile != null) {
                    invitedProfile
                } else {
                    val ownerPayload = mapOf(
                        "uid" to uid,
                        "email" to email,
                        "displayName" to displayName,
                        "role" to "admin",
                        "roles" to listOf("admin"),
                        "approved" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    profileRef.set(ownerPayload).await()
                    seedDefaultWorkspaceIfEmpty(uid)
                    val loaded = profileRef.get().await()
                    WorkspaceJsonCodec.mapToUserProfile(uid, WorkspaceJsonCodec.snapshotToMap(loaded))
                }
                offlineRepo?.cacheProfile(resolved)
                Result.success(resolved)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/${auth.currentUser?.uid}")
            Result.failure(e)
        }
    }

    private suspend fun seedDefaultWorkspaceIfEmpty(ownerUid: String) {
        val now = System.currentTimeMillis()
        val settingsConfigRef = workspaceCol(ownerUid, "settings").document("config")
        if (!settingsConfigRef.get().await().exists()) {
            settingsConfigRef.set(
                mapOf(
                    "settings" to mapOf(
                        "currencySymbol" to "Rs",
                        "financialYearStartMonth" to 7L,
                        "hideSensitiveMoney" to false,
                        "themeMode" to "light",
                        "paymentModes" to listOf("Cash", "Bank", "Easypaisa", "JazzCash")
                    ),
                    "settings_timestamp" to now,
                    "repProfile" to "",
                    "repProfile_timestamp" to now,
                    "reps" to emptyList<String>(),
                    "reps_timestamp" to now
                )
            ).await()
        }
        val storesRef = workspaceCol(ownerUid, "stores").document("list")
        if (!storesRef.get().await().exists()) {
            storesRef.set(
                mapOf(
                    "stores" to listOf(
                        mapOf("key" to "zubair", "name" to "Zubair", "salePrice" to 0.0, "active" to true, "updatedAt" to now),
                        mapOf("key" to "mahmood", "name" to "Mahmood", "salePrice" to 0.0, "active" to true, "updatedAt" to now),
                        mapOf("key" to "asaan", "name" to "Asaan", "salePrice" to 0.0, "active" to true, "updatedAt" to now)
                    ),
                    "stores_timestamp" to now
                )
            ).await()
        }
        val formulasRef = workspaceCol(ownerUid, "formulas").document("config")
        if (!formulasRef.get().await().exists()) {
            formulasRef.set(
                mapOf(
                    "formulas" to emptyList<Map<String, Any>>(),
                    "formulas_timestamp" to now,
                    "defaults" to emptyMap<String, Double>(),
                    "defaults_timestamp" to now,
                    "costs" to emptyMap<String, Double>(),
                    "costs_timestamp" to now,
                    "adjustment" to emptyMap<String, Double>(),
                    "adjustment_timestamp" to now,
                    "tracking" to emptyMap<String, Boolean>(),
                    "tracking_timestamp" to now
                )
            ).await()
        }
        val ledgerRef = workspaceCol(ownerUid, "daily_ledger").document("entries")
        if (!ledgerRef.get().await().exists()) {
            ledgerRef.set(
                mapOf(
                    "daily_ledger" to emptyList<Map<String, Any>>(),
                    "daily_ledger_timestamp" to now
                )
            ).await()
        }
    }

    fun observeProfile(uid: String): Flow<UserProfile?> =
        db.collection("users").document(uid)
            .snapshots()
            .map { snap ->
                if (snap.exists()) WorkspaceJsonCodec.mapToUserProfile(uid, WorkspaceJsonCodec.snapshotToMap(snap))
                else null
            }
            .onEach { profile ->
                if (profile != null) offlineRepo?.cacheProfile(profile)
            }
            .catch { error ->
                val unwrapped = unwrapFirestoreError(error)
                if (unwrapped == null && error is CancellationException) throw error
                val cached = offlineRepo?.getCachedProfile(uid)
                if (cached != null) {
                    emit(cached)
                } else {
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped ?: error, OperationType.GET, "users/$uid")
                    }
                    throw error
                }
            }

    @Suppress("UNCHECKED_CAST")
    fun observeWorkspace(profile: UserProfile): Flow<WorkspaceSnapshot> {
        val ownerUid = profile.effectiveOwnerUid
        val viewerUid = profile.uid

        // Group 1: Singleton configs
        val singletonsFlow = combine(
            listOf(
                observeSingletonMap(ownerUid, viewerUid, "stores", "list"),
                observeSingletonMap(ownerUid, viewerUid, "formulas", "config"),
                observeSingletonMap(ownerUid, viewerUid, "settings", "config"),
                observeSingletonMap(ownerUid, viewerUid, "settings", "team"),
                observeSingletonMap(ownerUid, viewerUid, "daily_ledger", "entries"),
                observeSingletonMap(ownerUid, viewerUid, "categories", "list"),
                observeSingletonMap(ownerUid, viewerUid, "field_expense_categories", "list"),
                observeSingletonMap(ownerUid, viewerUid, "commission_categories", "list"),
                observeSingletonMap(ownerUid, viewerUid, "settings", "yearCloseSignal")
            )
        ) { arr -> arr.toList() }

        // Group 2: Entity/Directory & Inventory collections
        val dirFlow = combine(
            observeCollectionMapped(ownerUid, viewerUid, "customers", WorkspaceJsonCodec::mapToCustomer),
            observeCollectionMapped(ownerUid, viewerUid, "clients", WorkspaceJsonCodec::mapToCustomer),
            observeCollectionMapped(ownerUid, viewerUid, "entities", WorkspaceJsonCodec::mapToEntity),
            observeCollectionMapped(ownerUid, viewerUid, "inventory", WorkspaceJsonCodec::mapToInventory)
        ) { customers, clients, entities, inventory ->
            listOf(customers, clients, entities, inventory)
        }

        // Group 3: Operations collections
        val opsFlow = combine(
            observeCollectionMapped(ownerUid, viewerUid, "production", WorkspaceJsonCodec::mapToProduction),
            observeCollectionMapped(ownerUid, viewerUid, "sales", WorkspaceJsonCodec::mapToSale),
            observeCollectionMapped(ownerUid, viewerUid, "rep", WorkspaceJsonCodec::mapToSale),
            observeCollectionMapped(ownerUid, viewerUid, "calculator", WorkspaceJsonCodec::mapToCalculator),
            observeCollectionMapped(ownerUid, viewerUid, "factory", WorkspaceJsonCodec::mapToFactory)
        ) { prod, sales, rep, calc, factory ->
            listOf(prod, sales, rep, calc, factory)
        }

        // Group 4: Finance & Deletions & Photos
        val finFlow = combine(
            observeCollectionMapped(ownerUid, viewerUid, "transactions", WorkspaceJsonCodec::mapToTransaction),
            observeCollectionMapped(ownerUid, viewerUid, "expenses", WorkspaceJsonCodec::mapToExpense),
            observeCollectionMapped(ownerUid, viewerUid, "returns", WorkspaceJsonCodec::mapToReturn),
            observeCollectionMapped(ownerUid, viewerUid, "deletions", WorkspaceJsonCodec::mapToDeletion),
            observeCollectionMapped(ownerUid, viewerUid, "photos", WorkspaceJsonCodec::mapToPhoto)
        ) { txns, exp, rets, dels, photos ->
            listOf(txns, exp, rets, dels, photos)
        }

        // Group 5: RBAC, Pending Approvals, Invites, Accounts
        val pendingFlow: Flow<List<PendingApproval>> = flow {
            val colRef = workspaceCol(ownerUid, "pending")
            val query: Query = if (profile.isAdmin) colRef else colRef.whereEqualTo("submittedBy.uid", viewerUid)
            emitAll(
                query.snapshots()
                    .map { snap ->
                        snap.documents.mapNotNull { d ->
                            runCatching { WorkspaceJsonCodec.mapToPending(d.id, WorkspaceJsonCodec.snapshotToMap(d)) }.getOrNull()
                        }
                    }
                    .catch { emit(emptyList()) }
            )
        }
        val accountsFlow: Flow<List<WorkspaceAccountRecord>> = flow {
            if (!profile.isAdmin) {
                emit(emptyList())
            } else {
                emitAll(
                    observeSingletonMap(ownerUid, viewerUid, "settings", "accounts")
                        .map { doc ->
                            val raw = doc["accounts"] as? Iterable<*> ?: emptyList<Any>()
                            raw.mapNotNull { item ->
                                val m = item as? Map<*, *> ?: return@mapNotNull null
                                val typed = m.entries.mapNotNull { (k, v) ->
                                    if (k != null && v != null) k.toString() to v else null
                                }.toMap()
                                WorkspaceJsonCodec.mapToAccountRecord(typed)
                            }
                        }
                )
            }
        }
        val invitesFlow: Flow<List<WorkspaceInvite>> = flow {
            if (!profile.isAdmin) {
                emit(emptyList())
            } else {
                emitAll(
                    db.collection("workspace_invites")
                        .whereEqualTo("ownerUid", ownerUid)
                        .snapshots()
                        .map { snap ->
                            snap.documents.mapNotNull { d ->
                                runCatching { WorkspaceJsonCodec.mapToInvite(d.id, WorkspaceJsonCodec.snapshotToMap(d)) }.getOrNull()
                            }
                        }
                        .catch { emit(emptyList()) }
                )
            }
        }
        val membersFlow: Flow<List<UserProfile>> = flow {
            if (!profile.isAdmin) {
                emit(listOf(profile))
            } else {
                emitAll(
                    db.collection("users")
                        .whereEqualTo("ownerUid", ownerUid)
                        .snapshots()
                        .map { snap ->
                            snap.documents.mapNotNull { d ->
                                runCatching { WorkspaceJsonCodec.mapToUserProfile(d.id, WorkspaceJsonCodec.snapshotToMap(d)) }.getOrNull()
                            }
                        }
                        .catch { emit(listOf(profile)) }
                )
            }
        }

        val adminFlow = combine(pendingFlow, accountsFlow, invitesFlow, membersFlow) { p, a, i, m ->
            listOf(p, a, i, m)
        }

        return combine(singletonsFlow, dirFlow, opsFlow, finFlow, adminFlow) { s, dir, ops, fin, adm ->
            val storesDoc = s[0]
            val formulasDoc = s[1]
            val settingsDoc = s[2]
            val teamDoc = s[3]
            val ledgerDoc = s[4]
            val categoriesDoc = s[5]
            val fieldCatDoc = s[6]
            val commCatDoc = s[7]
            val yearCloseDoc = s[8]

            val deletions = (fin[3] as List<DeletionTombstone>)
            val deletedIds = deletions.map { it.recordId.ifBlank { it.id } }.toSet()

            fun asListMaps(raw: Any?): List<Map<String, Any>> {
                val items = raw as? Iterable<*> ?: return emptyList()
                return items.mapNotNull { entry ->
                    val m = entry as? Map<*, *> ?: return@mapNotNull null
                    m.entries.mapNotNull { (k, v) -> if (k != null && v != null) k.toString() to v else null }.toMap()
                }
            }

            fun asStrList(raw: Any?): List<String> =
                (raw as? Iterable<*>)?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) } ?: emptyList()

            val stores = asListMaps(storesDoc["stores"]).map(WorkspaceJsonCodec::mapToStore).filter { it.deletedAt == null }
            val formulas = asListMaps(formulasDoc["formulas"]).map(WorkspaceJsonCodec::mapToFormula)
            val ledgerEntries = asListMaps(ledgerDoc["daily_ledger"])
                .map(WorkspaceJsonCodec::mapToDailyLedgerEntry)
                .sortedByDescending { it.date }
            val teamReps = asStrList(teamDoc["reps"])
            val expenseCats = asStrList(categoriesDoc["categories"])
            val fieldCats = asStrList(fieldCatDoc["categories"])
            val commCats = asStrList(commCatDoc["categories"])

            val mergedSettings = WorkspaceJsonCodec.mergeSettings(
                settingsConfigMap = settingsDoc,
                formulasConfigMap = formulasDoc,
                teamReps = teamReps,
                expenseCats = expenseCats,
                fieldExpenseCats = fieldCats,
                commissionCats = commCats,
                yearCloseMap = yearCloseDoc,
                localTheme = null,
                localAppSettings = emptyMap()
            )

            val photos = (fin[4] as List<PhotoBlob>).associateBy { it.key }
            val members = ((adm[3] as List<UserProfile>) + profile).distinctBy { it.uid }

            WorkspaceSnapshot(
                profile = profile,
                settings = mergedSettings,
                stores = stores,
                formulas = formulas,
                customers = (dir[0] as List<CustomerRecord>).filter { it.deletedAt == null && it.id !in deletedIds },
                clients = (dir[1] as List<CustomerRecord>).filter { it.deletedAt == null && it.id !in deletedIds },
                entities = (dir[2] as List<EntityRecord>).filter { it.deletedAt == null && it.id !in deletedIds },
                inventory = (dir[3] as List<InventoryRecord>).filter { it.deletedAt == null && it.id !in deletedIds },
                production = (ops[0] as List<ProductionRecord>).filter { it.deletedAt == null && it.id !in deletedIds }.sortedByDescending { it.date },
                sales = (ops[1] as List<SalesRecord>).filter { it.deletedAt == null && it.id !in deletedIds }.sortedByDescending { it.date },
                repRecords = (ops[2] as List<SalesRecord>).filter { it.deletedAt == null && it.id !in deletedIds }.sortedByDescending { it.date },
                calculator = (ops[3] as List<CalculatorRecord>).filter { it.deletedAt == null && it.id !in deletedIds }.sortedByDescending { it.date },
                factory = (ops[4] as List<FactoryRecord>).filter { it.deletedAt == null && it.id !in deletedIds }.sortedByDescending { it.date },
                transactions = (fin[0] as List<PaymentTransaction>).filter { it.deletedAt == null && it.id !in deletedIds }.sortedByDescending { it.date },
                expenses = (fin[1] as List<ExpenseRecord>).filter { it.deletedAt == null && it.id !in deletedIds }.sortedByDescending { it.date },
                returns = (fin[2] as List<ReturnRecord>).filter { it.deletedAt == null && it.id !in deletedIds }.sortedByDescending { it.date },
                ledgerEntries = ledgerEntries,
                deletions = deletions.sortedByDescending { it.deletedAt },
                photos = photos,
                pendingApprovals = (adm[0] as List<PendingApproval>).sortedByDescending { it.submittedAt },
                accounts = adm[1] as List<WorkspaceAccountRecord>,
                invites = adm[2] as List<WorkspaceInvite>,
                teamMembers = members
            )
        }
    }

    suspend fun saveSingleton(
        ownerUid: String,
        collection: String,
        docId: String,
        payload: Map<String, Any>,
        kvUpdates: Map<String, String> = emptyMap()
    ): Result<Unit> {
        val path = "users/$ownerUid/$collection/$docId"
        var outboxId = 0L
        return try {
            requireUserId()
            if (kvUpdates.isNotEmpty()) {
                offlineRepo?.setKvBatch(kvUpdates)
            }
            outboxId = offlineRepo?.enqueueOutbox(ownerUid, "SINGLETON", collection, docId, payload) ?: 0L
            if (!isOnline()) return Result.success(Unit)
            val synced = withTimeoutOrNull(2500L) {
                workspaceCol(ownerUid, collection).document(docId).set(payload, SetOptions.merge()).await()
                true
            }
            if (synced == true && outboxId > 0L) {
                offlineRepo?.markOutboxSynced(outboxId)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                e.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
            ) {
                Result.success(Unit)
            } else {
                offlineRepo?.discardOutboxOp(outboxId)
                handleFirestoreError(e, OperationType.WRITE, path)
                Result.failure(e)
            }
        } catch (e: Exception) {
            offlineRepo?.discardOutboxOp(outboxId)
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    companion object {
        private const val CACHE_SIZE_BYTES = 100L * 1024L * 1024L

        fun configureFirestore(firestore: FirebaseFirestore): FirebaseFirestore {
            runCatching {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(CACHE_SIZE_BYTES)
                            .build()
                    )
                    .build()
                firestore.firestoreSettings = settings
            }
            return firestore
        }
    }
}
