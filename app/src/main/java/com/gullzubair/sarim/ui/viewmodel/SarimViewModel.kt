package com.gullzubair.sarim.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gullzubair.sarim.R
import com.gullzubair.sarim.data.firebase.AppRepository
import com.gullzubair.sarim.data.firebase.WorkspaceOperations
import com.gullzubair.sarim.data.firebase.unwrapFirestoreError
import com.gullzubair.sarim.data.model.EntityRecord
import com.gullzubair.sarim.data.model.FormulaConfig
import com.gullzubair.sarim.data.model.MainTab
import com.gullzubair.sarim.data.model.PendingApproval
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.UiState
import com.gullzubair.sarim.data.model.UserProfile
import com.gullzubair.sarim.data.model.WorkspaceSettings
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
class SarimViewModel(
    private val repo: AppRepository,
    private val currentUserId: String
) : ViewModel() {

    private val ops = WorkspaceOperations(repo)
    private val dispatcher = SarimActionDispatcher(repo, ops)

    private val _selectedTab = MutableStateFlow(MainTab.OVERVIEW)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _todayIso = MutableStateFlow(DateUtils.todayIso())
    val todayIso: StateFlow<String> = _todayIso.asStateFlow()

    private val _biometricUnlocked = MutableStateFlow(false)
    val biometricUnlocked: StateFlow<Boolean> = _biometricUnlocked.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private val profileTrigger = MutableStateFlow(0)

    val workspaceState: StateFlow<UiState<WorkspaceSnapshot>> = profileTrigger
        .flatMapLatest {
            flow {
                val boot = repo.ensureUserProfileAndWorkspace()
                if (boot.isFailure) {
                    emit(UiState.Error(boot.exceptionOrNull()?.localizedMessage ?: "Failed to initialize workspace"))
                    return@flow
                }
                repo.observeProfile(currentUserId)
                    .flatMapLatest { profile ->
                        val active = profile ?: boot.getOrThrow()
                        repo.observeWorkspace(active)
                    }
                    .map<WorkspaceSnapshot, UiState<WorkspaceSnapshot>> { UiState.Success(it) }
                    .collect { emit(it) }
            }
        }
        .catch { error ->
            val unwrapped = unwrapFirestoreError(error)
            if (unwrapped == null && error is CancellationException) throw error
            val root = unwrapped ?: error
            emit(UiState.Error(root.localizedMessage ?: "Failed to sync workspace"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun refreshTodayDate() {
        val now = DateUtils.todayIso()
        if (_todayIso.value != now) _todayIso.value = now
    }

    fun retrySync() {
        profileTrigger.value += 1
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun setBiometricUnlocked(unlocked: Boolean) {
        _biometricUnlocked.value = unlocked
    }

    fun visibleTabsFor(profile: UserProfile): List<MainTab> {
        if (profile.role == "admin" || "admin" in profile.roles || profile.uid == profile.workspaceOwnerUid) {
            return MainTab.entries
        }
        val mapped = profile.roles.mapNotNull { r -> MainTab.entries.firstOrNull { it.key == r } }
        return if (mapped.isEmpty()) listOf(MainTab.OVERVIEW, MainTab.SALES) else (listOf(MainTab.OVERVIEW) + mapped).distinct()
    }

    private fun currentSnapshot(): WorkspaceSnapshot? = (workspaceState.value as? UiState.Success)?.data

    /** Sidebar "Sync": waits until every queued local write has reached the cloud. */
    fun syncNow() {
        viewModelScope.launch {
            _snackbarMessage.emit("Syncing…")
            try {
                repo.db.waitForPendingWrites().await()
                _snackbarMessage.emit("All changes synced")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.emit("Sync failed: ${e.localizedMessage ?: "check your connection"}")
            }
        }
    }

    private fun handleResult(res: Result<*>, successMsg: String) {
        viewModelScope.launch {
            if (res.isSuccess) {
                val msg = if (res.getOrNull()?.toString()?.startsWith("PENDING:") == true) {
                    "Submitted for Admin approval"
                } else successMsg
                _snackbarMessage.emit(msg)
            } else {
                _snackbarMessage.emit(res.exceptionOrNull()?.localizedMessage ?: "Operation failed")
            }
        }
    }

    fun togglePrivacyMask() {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val updated = snap.settings.copy(hideSensitiveMoney = !snap.settings.hideSensitiveMoney)
            val members = snap.teamMembers.map { it.uid }
            repo.saveSettings(snap.profile.workspaceOwnerUid.ifBlank { snap.profile.uid }, members, updated)
        }
    }

    fun toggleThemeMode() {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val next = when (snap.settings.themeMode) {
                "dark" -> "light"
                "light" -> "system"
                else -> "dark"
            }
            val members = snap.teamMembers.map { it.uid }
            repo.saveSettings(snap.profile.workspaceOwnerUid.ifBlank { snap.profile.uid }, members, snap.settings.copy(themeMode = next))
        }
    }

    fun updateSettings(settings: WorkspaceSettings) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val members = snap.teamMembers.map { it.uid }
            val res = repo.saveSettings(snap.profile.workspaceOwnerUid.ifBlank { snap.profile.uid }, members, settings)
            handleResult(res, "Settings saved")
        }
    }

    fun submitProduction(date: String, store: StoreConfig, gross: Double, container: Double, units: Double, costPerKg: Double, notes: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveProduction(snap, date, store, gross, container, units, costPerKg, notes), "Production saved")
        }
    }

    fun submitSale(date: String, mode: String, customer: String, store: StoreConfig?, rep: String, qty: Double, rate: Double, cash: Double, expiredKg: Double, choraRate: Double, addToLedger: Boolean, notes: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveSaleOrCollection(snap, date, mode, customer, store, rep, qty, rate, cash, expiredKg, choraRate, addToLedger, notes), "Sales record saved")
        }
    }

    fun submitRepRecord(date: String, repName: String, mode: String, customer: String, store: StoreConfig?, qty: Double, rate: Double, cash: Double, lat: Double?, lng: Double?, confirmed: Boolean, notes: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveRepRecord(snap, date, repName, mode, customer, store, qty, rate, cash, lat, lng, confirmed, notes), "Rep entry saved")
        }
    }

    fun submitFactoryBatch(date: String, formula: FormulaConfig, units: Long, notes: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveFactoryBatch(snap, date, formula, units, notes), "Batch units produced")
        }
    }

    fun submitRawPurchase(date: String, name: String, unitName: String, factor: Double, qtyUnits: Double, totalCost: Double, paidNow: Double, supplier: String, minThreshold: Double, notes: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveRawMaterialPurchase(snap, date, name, unitName, factor, qtyUnits, totalCost, paidNow, supplier, minThreshold, notes), "Purchase recorded")
        }
    }

    fun submitPayment(date: String, txType: String, entity: EntityRecord?, name: String, category: String, channel: String, amount: Double, addToLedger: Boolean, notes: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.savePaymentTransaction(snap, date, txType, entity, name, category, channel, amount, addToLedger, notes), "Payment saved")
        }
    }

    fun submitLedgerEntry(date: String, entryType: String, scope: String, channel: String, toChannel: String, category: String, party: String, amount: Double, notes: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveLedgerEntry(snap, date, entryType, scope, channel, toChannel, category, party, amount, notes), "Ledger entry saved")
        }
    }

    fun submitCalculatorSettlement(dateFrom: String, dateTo: String, repName: String, store: StoreConfig, totalSold: Double, returnedKg: Double, returnTarget: String, returnTargetName: String, expiredKg: Double, sharedDeduction: Double, cashQty: Double, creditQty: Double, rate: Double, commissions: List<Map<String, Any>>, expenses: List<Map<String, Any>>, physicalCash: Double, addToLedger: Boolean, notes: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveCalculatorSettlement(snap, dateFrom, dateTo, repName, store, totalSold, returnedKg, returnTarget, returnTargetName, expiredKg, sharedDeduction, cashQty, creditQty, rate, commissions, expenses, physicalCash, addToLedger, notes), "Settlement recorded")
        }
    }

    fun submitStockTransfer(date: String, from: StoreConfig, to: StoreConfig, qty: Double, notes: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveStockTransfer(snap, date, from, to, qty, notes), "Stock transferred")
        }
    }

    fun submitPaymentTransfer(date: String, from: EntityRecord, to: EntityRecord, amount: Double, notes: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.savePaymentTransfer(snap, date, from, to, amount, notes), "Balance transferred")
        }
    }

    fun saveStore(existingId: String?, name: String, formulaId: String, rate: Double, active: Boolean) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveStore(snap, existingId, name, formulaId, rate, active), "Store saved")
        }
    }

    fun saveFormula(existing: FormulaConfig?, name: String, unitsPerBatch: Double, additionalCost: Double, adjustment: Double, ingredients: List<Map<String, Any>>) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveFormula(snap, existing, name, unitsPerBatch, additionalCost, adjustment, ingredients), "Formula saved")
        }
    }

    fun addEntity(name: String, type: String, phone: String, opening: Double, notes: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.addEntity(snap, name, type, phone, opening, notes), "Entity saved")
        }
    }

    fun recycleRecord(collection: String, docId: String, title: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val members = snap.teamMembers.map { it.uid }
            handleResult(ops.softRecycleRecord(snap.profile, members, collection, docId, title), "Moved to Recycle Bin")
        }
    }

    fun restoreRecord(collection: String, docId: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val ownerUid = snap.profile.workspaceOwnerUid.ifBlank { snap.profile.uid }
            handleResult(ops.restoreFromRecycleBin(ownerUid, collection, docId), "Restored")
        }
    }

    fun deleteForever(collection: String, docId: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val ownerUid = snap.profile.workspaceOwnerUid.ifBlank { snap.profile.uid }
            handleResult(ops.permanentDelete(ownerUid, collection, docId), "Deleted permanently")
        }
    }

    fun reviewPending(item: PendingApproval, approve: Boolean) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val members = snap.teamMembers.map { it.uid }
            handleResult(ops.reviewPendingApproval(snap.profile, members, item, approve), if (approve) "Approved" else "Rejected")
        }
    }

    fun inviteMember(email: String, displayName: String, roles: List<String>) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(ops.inviteTeamMember(snap.profile, email, displayName, roles), "Team invite created")
        }
    }

    fun updateMemberRoles(memberUid: String, roles: List<String>, approved: Boolean) {
        viewModelScope.launch {
            handleResult(ops.updateMemberRoles(memberUid, roles, approved), "Member permissions updated")
        }
    }

    fun updateCustomer(
        existing: com.gullzubair.sarim.data.model.CustomerRecord,
        name: String,
        phone: String,
        address: String,
        openingBalance: Double,
        notes: String,
        lat: Double? = existing.routeLat,
        lng: Double? = existing.routeLng
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.updateCustomer(snap, existing, name, phone, address, openingBalance, notes, lat, lng), "Customer updated")
        }
    }

    fun deleteFormula(formulaId: String, onDeleted: () -> Unit = {}) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = dispatcher.deleteFormula(snap, formulaId)
            handleResult(res, "Formula deleted")
            if (res.isSuccess) onDeleted()
        }
    }

    fun saveFormulaSavedMix(formula: FormulaConfig, title: String, mode: String, target: Double, useGrams: Boolean, totalKg: Double) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val mixMap = mapOf<String, Any>(
                "id" to "mix_${System.currentTimeMillis()}",
                "title" to title.ifBlank { "Batch • ${formula.name} • ${DateUtils.todayIso()}" },
                "date" to DateUtils.todayIso(),
                "mode" to mode,
                "target" to target,
                "useGrams" to useGrams,
                "totalKg" to totalKg
            )
            handleResult(dispatcher.saveFormulaSavedMix(snap, formula, mixMap), "Calculation saved")
        }
    }

    fun recyclePair(collection: String, pairId: String?, fallbackDocId: String, title: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val members = snap.teamMembers.map { it.uid }
            if (pairId.isNullOrBlank()) {
                handleResult(ops.softRecycleRecord(snap.profile, members, collection, fallbackDocId, title), "Moved to Recycle Bin")
            } else {
                val ids = when (collection) {
                    "production" -> snap.production.filter { !it.recycled && it.transferPairId == pairId }.map { it.id }
                    "transactions" -> snap.transactions.filter { !it.recycled && it.transferPairId == pairId }.map { it.id }
                    else -> listOf(fallbackDocId)
                }.ifEmpty { listOf(fallbackDocId) }
                var lastRes: Result<Unit> = Result.success(Unit)
                ids.forEach { id -> lastRes = ops.softRecycleRecord(snap.profile, members, collection, id, title) }
                handleResult(lastRes, "Moved to Recycle Bin")
            }
        }
    }

    fun revokeInvite(inviteId: String) {
        viewModelScope.launch {
            val res = try {
                repo.db.collection("workspace_invites").document(inviteId).delete().await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
            handleResult(res, "Invite removed")
        }
    }

    fun closeFinancialYear(onComplete: () -> Unit = {}) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = dispatcher.closeFinancialYear(snap)
            handleResult(res, res.getOrNull() ?: "Financial year closed")
            if (res.isSuccess) onComplete()
        }
    }

    fun restoreBackupJson(jsonString: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            runCatching {
                val root = org.json.JSONObject(jsonString)
                var count = 0
                val existingStoreKeys = snap.stores.map { it.storeKey.lowercase() }.toSet()
                root.optJSONArray("stores")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val name = obj.optString("name")
                        if (name.isNotBlank() && obj.optString("storeKey").lowercase() !in existingStoreKeys) {
                            dispatcher.saveStore(snap, null, name, obj.optString("formulaId"), obj.optDouble("salePrice", 0.0), true)
                            count++
                        }
                    }
                }
                val existingCustNames = snap.customers.filter { !it.recycled }.map { it.name.lowercase() }.toSet()
                root.optJSONArray("customers")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val name = obj.optString("name")
                        if (name.isNotBlank() && name.lowercase() !in existingCustNames) {
                            ops.submitOrExecuteWrite(
                                profile = snap.profile,
                                memberUids = snap.teamMembers.map { it.uid },
                                targetCollection = "customers",
                                docId = null,
                                summaryTitle = "Restored Customer: $name",
                                summaryDetail = "From backup",
                                recordDate = DateUtils.todayIso(),
                                payload = mapOf(
                                    "name" to name,
                                    "phone" to obj.optString("phone"),
                                    "address" to obj.optString("address"),
                                    "visitCount" to 0L,
                                    "openingBalance" to obj.optDouble("openingBalance", 0.0),
                                    "recycled" to false,
                                    "recycledAtEpoch" to 0L
                                )
                            )
                            count++
                        }
                    }
                }
                count
            }.onSuccess { restored ->
                _snackbarMessage.emit(if (restored > 0) "Restored $restored items from backup" else "Backup validated — all records already present")
            }.onFailure { err ->
                _snackbarMessage.emit("Restore failed: ${err.localizedMessage}")
            }
        }
    }

    fun repairLedgerMirrors() {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val members = snap.teamMembers.map { it.uid }
            val ledgerIds = snap.ledgerEntries.filter { !it.recycled }.map { it.id }.toSet()
            var repaired = 0
            snap.sales.filter { !it.recycled && it.addToLedger && it.cashReceived > 0 && (it.ledgerEntryId.isNullOrBlank() || it.ledgerEntryId !in ledgerIds) }
                .forEach { sale ->
                    ops.saveLedgerMirror(snap.profile, members, sale.date, "IN", "none", "Cash", "Sales Receipt", sale.customerName, sale.cashReceived, "sales", sale.id, "Auto-repaired")
                    repaired++
                }
            snap.transactions.filter { !it.recycled && it.addToLedger && it.amount > 0 && it.txType in listOf("in", "out", "expense") && (it.ledgerEntryId.isNullOrBlank() || it.ledgerEntryId !in ledgerIds) }
                .forEach { tx ->
                    val lType = if (tx.txType == "in") "IN" else "OUT"
                    val scope = if (tx.txType == "expense") "business" else "none"
                    ops.saveLedgerMirror(snap.profile, members, tx.date, lType, scope, tx.paymentChannel, tx.entityCategory, tx.entityName, tx.amount, "transactions", tx.id, "Auto-repaired")
                    repaired++
                }
            _snackbarMessage.emit(if (repaired > 0) "Repaired $repaired missing ledger links" else "All ledger links healthy")
        }
    }

    companion object {
        fun factory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val dbId = app.getString(R.string.firestore_database_id)
                SarimViewModel(AppRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance(dbId)), currentUserId)
            }
        }
    }
}
