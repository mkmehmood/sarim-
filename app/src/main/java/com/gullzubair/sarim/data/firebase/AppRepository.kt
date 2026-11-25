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
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import com.gullzubair.sarim.R
import com.gullzubair.sarim.data.local.OfflineSyncRepository
import com.gullzubair.sarim.data.local.SarimOfflineDatabase
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
        ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun workspaceCol(ownerUid: String, collection: String) =
        db.collection("users").document(ownerUid).collection(collection)

    private inline fun <reified T> observeSubcollection(
        ownerUid: String,
        viewerUid: String,
        collection: String
    ): Flow<List<T>> = flow {
        val colRef = workspaceCol(ownerUid, collection)
        val query: Query = if (ownerUid == viewerUid) {
            colRef
        } else {
            colRef.whereArrayContains("memberUids", viewerUid)
        }
        val path = "users/$ownerUid/$collection"
        emitAll(
            query.snapshots()
                .map { snap -> snap.toObjects(T::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
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

    suspend fun ensureUserProfileAndWorkspace(): Result<UserProfile> {
        return try {
        val user = auth.currentUser ?: error("Not authenticated")
        val uid = user.uid
        val email = (user.email ?: "$uid@local.user").lowercase().take(160)
        val displayName = InputGuards.sanitizeName(user.displayName?.ifBlank { null } ?: email.substringBefore("@"))

        // Instant offline startup from Room SQLite cache when already initialized
        offlineRepo?.getCachedProfile(uid)?.let { cached ->
            return Result.success(cached)
        }

        val profileRef = db.collection("users").document(uid)
        val cachedFirestoreSnap = runCatching { profileRef.get(Source.CACHE).await() }.getOrNull()
        if (cachedFirestoreSnap != null && cachedFirestoreSnap.exists()) {
            val profile = cachedFirestoreSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(uid = uid, email = email, displayName = displayName, workspaceOwnerUid = uid)
            offlineRepo?.cacheProfile(profile)
            return Result.success(profile)
        }

        if (!isOnline()) {
            return Result.failure(IllegalStateException("No internet connection. Connect once to download your workspace offline cache."))
        }

        val existingSnap = withTimeoutOrNull(8000L) { profileRef.get().await() }
            ?: return Result.failure(IllegalStateException("Workspace connection timed out. Check your connection and retry."))

        if (existingSnap.exists()) {
            val profile = existingSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(uid = uid, email = email, displayName = displayName, workspaceOwnerUid = uid)
            offlineRepo?.cacheProfile(profile)
            Result.success(profile)
        } else {
            // Check verified pending workspace invite for this user's email
            var invitedProfile: UserProfile? = null
            if (user.isEmailVerified && !user.email.isNullOrBlank()) {
                val invites = try {
                    db.collection("workspace_invites")
                        .whereEqualTo("memberEmail", email)
                        .get()
                        .await()
                        .toObjects(WorkspaceInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } catch (_: Exception) {
                    emptyList()
                }
                val match = invites.firstOrNull { it.status == "pending" && it.approved }
                if (match != null) {
                    val payload = mapOf(
                        "uid" to uid,
                        "email" to email,
                        "displayName" to displayName,
                        "workspaceOwnerUid" to match.ownerUid,
                        "role" to "member",
                        "roles" to match.roles.take(8),
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
                    invitedProfile = profileRef.get().await()
                        .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }

            val resolved = if (invitedProfile != null) {
                invitedProfile
            } else {
                val ownerPayload = mapOf(
                    "uid" to uid,
                    "email" to email,
                    "displayName" to displayName,
                    "workspaceOwnerUid" to uid,
                    "role" to "admin",
                    "roles" to listOf("admin"),
                    "approved" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                profileRef.set(ownerPayload).await()
                seedDefaultWorkspaceIfEmpty(uid)
                profileRef.get().await()
                    .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(uid = uid, email = email, displayName = displayName, workspaceOwnerUid = uid)
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
        val settingsRef = workspaceCol(ownerUid, "settings").document("preferences")
        if (!settingsRef.get().await().exists()) {
            settingsRef.set(
                mapOf(
                    "ownerUid" to ownerUid,
                    "memberUids" to listOf(ownerUid),
                    "currencySymbol" to "Rs",
                    "financialYearStartMonth" to 7L,
                    "hideSensitiveMoney" to false,
                    "biometricLockEnabled" to false,
                    "themeMode" to "light",
                    "paymentChannels" to listOf("Cash", "Bank", "Easypaisa", "JazzCash"),
                    "expenseCategories" to listOf("Fuel", "Wages", "Rent", "Electricity", "Freight", "Maintenance", "Tea & Meals", "Office", "Other"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        val storesSnap = workspaceCol(ownerUid, "stores").limit(1).get().await()
        if (storesSnap.isEmpty) {
            workspaceCol(ownerUid, "stores").document("store_gull_special").set(
                mapOf(
                    "ownerUid" to ownerUid,
                    "memberUids" to listOf(ownerUid),
                    "storeKey" to "GULL_SPECIAL",
                    "name" to "GULL SPECIAL",
                    "formulaId" to "",
                    "salePrice" to 1600.0,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun observeProfile(uid: String): Flow<UserProfile?> =
        db.collection("users").document(uid)
            .snapshots()
            .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .onEach { profile ->
                if (profile != null) {
                    offlineRepo?.cacheProfile(profile)
                }
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
        val ownerUid = profile.workspaceOwnerUid.ifBlank { profile.uid }
        val viewerUid = profile.uid

        val coreFlows = listOf(
            observeSubcollection<WorkspaceSettings>(ownerUid, viewerUid, "settings"),
            observeSubcollection<StoreConfig>(ownerUid, viewerUid, "stores"),
            observeSubcollection<FormulaConfig>(ownerUid, viewerUid, "formulas"),
            observeSubcollection<CustomerRecord>(ownerUid, viewerUid, "customers"),
            observeSubcollection<EntityRecord>(ownerUid, viewerUid, "entities"),
            observeSubcollection<InventoryItem>(ownerUid, viewerUid, "inventory")
        )
        val txFlows = listOf(
            observeSubcollection<ProductionRecord>(ownerUid, viewerUid, "production"),
            observeSubcollection<SalesRecord>(ownerUid, viewerUid, "sales"),
            observeSubcollection<RepRecord>(ownerUid, viewerUid, "rep_records"),
            observeSubcollection<CalculatorSettlement>(ownerUid, viewerUid, "calculator"),
            observeSubcollection<FactoryRecord>(ownerUid, viewerUid, "factory"),
            observeSubcollection<PaymentTransaction>(ownerUid, viewerUid, "transactions"),
            observeSubcollection<DailyLedgerEntry>(ownerUid, viewerUid, "ledger_entries")
        )
        val pendingFlow: Flow<List<PendingApproval>> = flow {
            val colRef = workspaceCol(ownerUid, "pending")
            val query = if (profile.role == "admin" || ownerUid == viewerUid) {
                colRef
            } else {
                colRef.whereEqualTo("submittedByUid", viewerUid)
            }
            emitAll(
                query.snapshots()
                    .map { it.toObjects(PendingApproval::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                    .catch { error ->
                        val unwrapped = unwrapFirestoreError(error)
                        if (unwrapped == null && error is CancellationException) throw error
                        if (auth.currentUser?.uid == viewerUid) {
                            handleFirestoreError(unwrapped ?: error, OperationType.LIST, "users/$ownerUid/pending")
                        }
                        throw error
                    }
            )
        }
        val invitesFlow: Flow<List<WorkspaceInvite>> = flow {
            if (ownerUid != viewerUid) {
                emit(emptyList())
            } else {
                emitAll(
                    db.collection("workspace_invites")
                        .whereEqualTo("ownerUid", ownerUid)
                        .snapshots()
                        .map { it.toObjects(WorkspaceInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                        .catch { error ->
                            val unwrapped = unwrapFirestoreError(error)
                            if (unwrapped == null && error is CancellationException) throw error
                            emit(emptyList())
                        }
                )
            }
        }
        val membersFlow: Flow<List<UserProfile>> = flow {
            if (ownerUid != viewerUid) {
                emit(listOf(profile))
            } else {
                emitAll(
                    db.collection("users")
                        .whereEqualTo("workspaceOwnerUid", ownerUid)
                        .snapshots()
                        .map { it.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                        .catch { error ->
                            val unwrapped = unwrapFirestoreError(error)
                            if (unwrapped == null && error is CancellationException) throw error
                            emit(listOf(profile))
                        }
                )
            }
        }

        val coreCombined = combine(coreFlows) { arr -> arr }
        val txCombined = combine(txFlows) { arr -> arr }
        val adminCombined = combine(pendingFlow, invitesFlow, membersFlow) { p, i, m -> Triple(p, i, m) }

        return combine(coreCombined, txCombined, adminCombined) { core, tx, admin ->
            val settingsList = core[0] as List<WorkspaceSettings>
            WorkspaceSnapshot(
                profile = profile,
                settings = settingsList.firstOrNull { it.id == "preferences" } ?: settingsList.firstOrNull() ?: WorkspaceSettings(ownerUid = ownerUid),
                stores = core[1] as List<StoreConfig>,
                formulas = core[2] as List<FormulaConfig>,
                customers = core[3] as List<CustomerRecord>,
                entities = core[4] as List<EntityRecord>,
                inventory = core[5] as List<InventoryItem>,
                production = (tx[0] as List<ProductionRecord>).sortedByDescending { it.date },
                sales = (tx[1] as List<SalesRecord>).sortedByDescending { it.date },
                repRecords = (tx[2] as List<RepRecord>).sortedByDescending { it.date },
                calculator = (tx[3] as List<CalculatorSettlement>).sortedByDescending { it.dateTo },
                factory = (tx[4] as List<FactoryRecord>).sortedByDescending { it.date },
                transactions = (tx[5] as List<PaymentTransaction>).sortedByDescending { it.date },
                ledgerEntries = (tx[6] as List<DailyLedgerEntry>).sortedByDescending { it.date },
                pendingApprovals = admin.first.sortedByDescending { it.createdAt?.seconds ?: 0L },
                invites = admin.second,
                teamMembers = admin.third
            )
        }
    }

    suspend fun saveSettings(ownerUid: String, memberUids: List<String>, settings: WorkspaceSettings): Result<Unit> {
        val path = "users/$ownerUid/settings/preferences"
        var opId: String? = null
        return try {
            requireUserId()
            val members = (memberUids + ownerUid).distinct().take(20)
            val currency = settings.currencySymbol.take(12).ifBlank { "Rs" }
            val fyMonth = settings.financialYearStartMonth.coerceIn(1L, 12L)
            val channels = settings.paymentChannels.take(15)
            val categories = settings.expenseCategories.take(30)

            val localPayload = mapOf<String, Any?>(
                "id" to "preferences",
                "ownerUid" to ownerUid,
                "memberUids" to members,
                "currencySymbol" to currency,
                "financialYearStartMonth" to fyMonth,
                "hideSensitiveMoney" to settings.hideSensitiveMoney,
                "biometricLockEnabled" to settings.biometricLockEnabled,
                "themeMode" to settings.themeMode,
                "paymentChannels" to channels,
                "expenseCategories" to categories,
                "updatedAt" to System.currentTimeMillis()
            )
            offlineRepo?.applyLocalMutation(ownerUid, "settings", "preferences", localPayload, isUpdate = true)
            opId = offlineRepo?.enqueueSyncOp(ownerUid, "settings", "preferences", "SETTINGS", localPayload)

            if (!isOnline()) {
                return Result.success(Unit)
            }

            val docRef = workspaceCol(ownerUid, "settings").document("preferences")
            val synced = withTimeoutOrNull(1800L) {
                val exists = docRef.get().await().exists()
                if (exists) {
                    docRef.update(
                        mapOf(
                            "memberUids" to members,
                            "currencySymbol" to currency,
                            "financialYearStartMonth" to fyMonth,
                            "hideSensitiveMoney" to settings.hideSensitiveMoney,
                            "biometricLockEnabled" to settings.biometricLockEnabled,
                            "themeMode" to settings.themeMode,
                            "paymentChannels" to channels,
                            "expenseCategories" to categories,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    docRef.set(
                        mapOf(
                            "ownerUid" to ownerUid,
                            "memberUids" to members,
                            "currencySymbol" to currency,
                            "financialYearStartMonth" to fyMonth,
                            "hideSensitiveMoney" to settings.hideSensitiveMoney,
                            "biometricLockEnabled" to settings.biometricLockEnabled,
                            "themeMode" to settings.themeMode,
                            "paymentChannels" to channels,
                            "expenseCategories" to categories,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                true
            }
            if (synced == true && opId != null) {
                offlineRepo?.markOpSynced(opId)
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
                opId?.let { offlineRepo?.discardSyncOp(it) }
                handleFirestoreError(e, OperationType.WRITE, path)
                Result.failure(e)
            }
        } catch (e: Exception) {
            opId?.let { offlineRepo?.discardSyncOp(it) }
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
