package com.gullzubair.sarim.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gullzubair.sarim.data.firebase.AppRepository
import com.gullzubair.sarim.data.firebase.WorkspaceOperations
import com.gullzubair.sarim.data.firebase.unwrapFirestoreError
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.CalculatorRecord
import com.gullzubair.sarim.data.model.CustomerRecord
import com.gullzubair.sarim.data.model.DeletionTombstone
import com.gullzubair.sarim.data.model.EntityRecord
import com.gullzubair.sarim.data.model.ExpenseRecord
import com.gullzubair.sarim.data.model.FactoryRecord
import com.gullzubair.sarim.data.model.FormulaConfig
import com.gullzubair.sarim.data.model.FormulaIngredient
import com.gullzubair.sarim.data.model.InventoryRecord
import com.gullzubair.sarim.data.model.MainTab
import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.data.model.PendingApproval
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.ReturnRecord
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.SavedCalcRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.data.model.UiState
import com.gullzubair.sarim.data.model.UserProfile
import com.gullzubair.sarim.data.model.WorkspaceAccountRecord
import com.gullzubair.sarim.data.model.WorkspaceSettings
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

enum class ToastKind { SUCCESS, WARNING, ERROR, INFO }

data class ToastEvent(
    val id: Long,
    val message: String,
    val kind: ToastKind = ToastKind.INFO,
    val durationMs: Long = 3000L
)

@OptIn(ExperimentalCoroutinesApi::class)
class SarimViewModel(
    private val repo: AppRepository,
    private val currentUserId: String
) : ViewModel() {

    private val ops = WorkspaceOperations(repo)
    private val dispatcher = SarimActionDispatcher(repo, ops)
    private val editActions = SarimEditActions(repo, ops, dispatcher)
    private val calcAndRepairActions = SarimCalcAndRepairActions(repo, ops, dispatcher)

    private val _selectedTab = MutableStateFlow(MainTab.PRODUCTION)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _todayIso = MutableStateFlow(DateUtils.todayIso())
    val todayIso: StateFlow<String> = _todayIso.asStateFlow()

    private val _biometricUnlocked = MutableStateFlow(false)
    val biometricUnlocked: StateFlow<Boolean> = _biometricUnlocked.asStateFlow()

    private var toastSeq = 0L
    private val _toastEvents = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 16)
    val toastEvents = _toastEvents.asSharedFlow()

    fun showToast(message: String, kind: ToastKind = ToastKind.INFO, durationMs: Long = 3000L) {
        if (message.isBlank()) return
        toastSeq += 1L
        _toastEvents.tryEmit(ToastEvent(id = toastSeq, message = message, kind = kind, durationMs = durationMs))
    }

    fun isOnline(): Boolean = repo.isOnline()

    private val profileTrigger = MutableStateFlow(0)

    val workspaceState: StateFlow<UiState<WorkspaceSnapshot>> = profileTrigger
        .flatMapLatest {
            channelFlow<UiState<WorkspaceSnapshot>> {
                val boot = repo.ensureUserProfileAndWorkspace()
                if (boot.isFailure) {
                    send(UiState.Error(boot.exceptionOrNull()?.localizedMessage ?: "Failed to initialize workspace"))
                    return@channelFlow
                }
                val bootProfile = boot.getOrThrow()
                val offline = repo.offlineRepo
                var hasEmittedCacheOrLive = false

                if (offline != null) {
                    val cachedSnap = offline.getWorkspaceSnapshotOnce(bootProfile)
                    if (cachedSnap != null) {
                        hasEmittedCacheOrLive = true
                        send(UiState.Success(cachedSnap))
                    }
                    launch {
                        offline.observeProfile(currentUserId)
                            .flatMapLatest { cachedProfile ->
                                offline.observeCachedWorkspaceSnapshot(cachedProfile ?: bootProfile)
                            }
                            .filterNotNull()
                            .collect { snap ->
                                hasEmittedCacheOrLive = true
                                send(UiState.Success(snap))
                            }
                    }
                }

                val remoteDb = repo.db
                if (remoteDb != null) {
                    var flushedOnConnect = false
                    launch {
                        repo.observeProfile(currentUserId)
                            .flatMapLatest { liveProfile ->
                                val active = liveProfile ?: bootProfile
                                repo.observeWorkspace(active)
                            }
                            .catch { error ->
                                val unwrapped = unwrapFirestoreError(error)
                                if (unwrapped == null && error is CancellationException) throw error
                                if (!hasEmittedCacheOrLive) {
                                    val root = unwrapped ?: error
                                    send(UiState.Error(root.localizedMessage ?: "Failed to sync workspace"))
                                }
                            }
                            .collect { liveSnapshot ->
                                if (offline != null) {
                                    val activeOwner = liveSnapshot.profile.effectiveOwnerUid
                                    if (!flushedOnConnect && repo.isOnline()) {
                                        flushedOnConnect = true
                                        launch {
                                            offline.flushOutbox(activeOwner, remoteDb)
                                        }
                                    }
                                    offline.cacheWorkspaceSnapshot(activeOwner, liveSnapshot)
                                    hasEmittedCacheOrLive = true
                                } else {
                                    hasEmittedCacheOrLive = true
                                    send(UiState.Success(liveSnapshot))
                                }
                            }
                    }
                }
            }
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

    // Customer ledger -> Sales tab "edit this sale" hand-off (web: startEdit('sale', id)).
    private val _pendingSaleEditId = MutableStateFlow<String?>(null)
    val pendingSaleEditId: StateFlow<String?> = _pendingSaleEditId.asStateFlow()

    fun requestSaleEdit(saleId: String) {
        _pendingSaleEditId.value = saleId
        _selectedTab.value = MainTab.SALES
    }

    fun consumePendingSaleEdit() {
        _pendingSaleEditId.value = null
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun setBiometricUnlocked(unlocked: Boolean) {
        _biometricUnlocked.value = unlocked
    }

    fun sessionInfoFor(profile: UserProfile): FinanceEngine.SessionInfo =
        FinanceEngine.deriveSession(profile)

    // Web parity (modules/roles.js TAB_ORDER): role-limited users see tabs in this order.
    private val roleTabOrder = listOf(MainTab.PRODUCTION, MainTab.FACTORY, MainTab.SALES, MainTab.PAYMENTS, MainTab.REP_SALES)

    fun visibleTabsFor(profile: UserProfile): List<MainTab> {
        val session = FinanceEngine.deriveSession(profile)
        if (session.mode == "admin") return MainTab.entries
        val allowed = session.tabs.map { MainTab.fromKey(it) }.distinct().sortedBy { roleTabOrder.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        return if (allowed.isEmpty()) listOf(MainTab.REP_SALES) else allowed
    }

    private fun currentSnapshot(): WorkspaceSnapshot? = (workspaceState.value as? UiState.Success)?.data

    fun syncNow() {
        viewModelScope.launch {
            val remoteDb = repo.db
            if (remoteDb == null) {
                showToast("Local mode: saved in Room database", ToastKind.INFO)
                return@launch
            }
            showToast("Syncing…", ToastKind.INFO)
            val snap = currentSnapshot()
            val ownerUid = snap?.profile?.effectiveOwnerUid ?: currentUserId
            if (!repo.isOnline()) {
                val pending = repo.offlineRepo?.getOutboxCount(ownerUid) ?: (snap?.pendingSyncCount ?: 0)
                showToast(
                    if (pending > 0) "Offline: $pending change(s) queued in local Room DB"
                    else "Offline: running on cached Room DB",
                    ToastKind.WARNING
                )
                return@launch
            }
            try {
                val flushRes = repo.offlineRepo?.flushOutbox(ownerUid, remoteDb)
                if (flushRes != null && flushRes.isFailure) {
                    showToast(flushRes.exceptionOrNull()?.localizedMessage ?: "Sync incomplete", ToastKind.WARNING)
                    return@launch
                }
                withTimeoutOrNull(5000L) {
                    remoteDb.waitForPendingWrites().await()
                }
                val syncedCount = flushRes?.getOrDefault(0) ?: 0
                showToast(
                    if (syncedCount > 0) "Synced $syncedCount offline change(s)" else "All changes synced",
                    ToastKind.SUCCESS
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Sync failed: ${e.localizedMessage ?: "check your connection"}", ToastKind.ERROR)
            }
        }
    }

    private fun handleResult(res: Result<*>, successMsg: String) {
        viewModelScope.launch {
            if (res.isSuccess) {
                val isPending = res.getOrNull()?.toString()?.startsWith("PENDING:") == true
                if (isPending) {
                    showToast("Submitted for Admin approval", ToastKind.INFO)
                } else {
                    showToast(successMsg, ToastKind.SUCCESS)
                }
            } else {
                showToast(res.exceptionOrNull()?.localizedMessage ?: "Operation failed", ToastKind.ERROR)
            }
        }
    }

    fun togglePrivacyMask() {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            dispatcher.saveSettings(snap, snap.settings.copy(hideSensitiveMoney = !snap.settings.hideSensitiveMoney))
        }
    }

    fun toggleThemeMode() {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val next = when (snap.settings.themeMode.lowercase()) {
                "system", "auto", "" -> "light"
                "light" -> "dark"
                else -> "system"
            }
            dispatcher.saveSettings(snap, snap.settings.copy(themeMode = next))
        }
    }

    fun updateSettings(settings: WorkspaceSettings) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveSettings(snap, settings), "Settings saved")
        }
    }

    fun submitProduction(
        date: String,
        store: StoreConfig,
        gross: Double,
        container: Double,
        units: Double,
        costPerKg: Double,
        notes: String,
        photoDataUri: String? = null,
        photoDataUris: List<String> = emptyList()
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(
                dispatcher.saveProduction(snap, date, store, gross, container, units, costPerKg, notes, photoDataUri, photoDataUris),
                "Production saved"
            )
        }
    }

    fun editProduction(
        original: ProductionRecord,
        date: String,
        store: StoreConfig,
        gross: Double,
        container: Double,
        units: Double,
        costPerKg: Double,
        notes: String,
        retainedPhotoKeys: List<String>,
        newPhotoDataUris: List<String>,
        onSuccess: () -> Unit = {}
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = editActions.editProduction(snap, original, date, store, gross, container, units, costPerKg, notes, retainedPhotoKeys, newPhotoDataUris)
            handleResult(res, "Production updated")
            if (res.isSuccess) onSuccess()
        }
    }

    fun submitStockReturn(date: String, store: StoreConfig, qtyKg: Double, expiredKg: Double, returnedBy: String, notes: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveStockReturn(snap, date, store, qtyKg, expiredKg, returnedBy, notes), "Return logged")
        }
    }

    fun recoverReturnChora(ret: ReturnRecord) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.recoverReturnChora(snap, ret), "CHORA recovered to inventory")
        }
    }

    fun submitSale(
        date: String,
        supplyDate: String,
        paymentType: String,
        customer: String,
        store: StoreConfig?,
        rep: String,
        qty: Double,
        rate: Double,
        collectedAmount: Double,
        expiredKg: Double,
        choraRate: Double,
        addToLedger: Boolean,
        notes: String,
        customerPhone: String = ""
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(
                dispatcher.saveSaleOrCollection(
                    snap, date, supplyDate, paymentType, customer, store, rep,
                    qty, rate, expiredKg, choraRate, collectedAmount, addToLedger, notes, customerPhone
                ),
                when (paymentType.uppercase()) {
                    "REP_RETURN" -> "Rep return saved"
                    "COLLECTION" -> "Collection saved"
                    "MONEY_OUT" -> "Money out saved"
                    else -> "Sales record saved"
                }
            )
        }
    }

    fun submitRepRecord(
        date: String,
        repName: String,
        paymentType: String,
        customer: String,
        store: StoreConfig?,
        qty: Double,
        rate: Double,
        collectedAmount: Double,
        expiredKg: Double,
        lat: Double?,
        lng: Double?,
        accuracy: Double?,
        confirmed: Boolean,
        notes: String,
        customerPhone: String = "",
        expiredUnitPrice: Double = 0.0
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(
                dispatcher.saveRepRecord(
                    snap, date, repName, paymentType, customer, store, qty, rate,
                    collectedAmount, expiredKg, lat, lng, accuracy, confirmed, notes, customerPhone, expiredUnitPrice
                ),
                "Rep entry saved"
            )
        }
    }

    fun submitFactoryBatch(date: String, formula: FormulaConfig, units: Double, notes: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveFactoryBatch(snap, date, formula, units, notes), "Factory batch recorded")
        }
    }

    fun submitRawPurchase(
        date: String,
        name: String,
        unitName: String,
        factor: Double,
        qtyUnits: Double,
        costPerUnit: Double,
        paidNow: Double,
        supplier: String,
        notes: String
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(
                dispatcher.saveRawMaterialPurchase(
                    snap, date, name, unitName, factor, qtyUnits, costPerUnit, paidNow, supplier, notes
                ),
                "Purchase recorded"
            )
        }
    }

    fun submitPaymentOrExpense(
        date: String,
        kind: String,
        entity: EntityRecord?,
        name: String,
        category: String,
        channel: String,
        amount: Double,
        addToLedger: Boolean,
        notes: String,
        photoDataUri: String? = null
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val normKind = kind.trim().uppercase()
            val successLabel = when {
                normKind == "EXPENSE" || normKind == "OPERATING" -> "Operating expense recorded: $name"
                normKind == "IN" -> "Payment IN recorded: $name"
                else -> "Payment OUT recorded: $name"
            }
            handleResult(
                dispatcher.savePaymentOrExpense(snap, date, kind, entity, name, category, channel, amount, addToLedger, notes, photoDataUri),
                successLabel
            )
        }
    }

    fun submitLedgerEntry(
        existingId: String? = null,
        date: String,
        type: String,
        scope: String,
        mode: String,
        toMode: String,
        category: String,
        party: String,
        amount: Double,
        ref: String = "",
        note: String
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(
                dispatcher.saveDailyLedgerEntry(snap, existingId, date, type, scope, mode, toMode, category, party, amount, ref, note),
                "Daily Ledger saved"
            )
        }
    }

    fun deleteLedgerEntry(entryId: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.removeDailyLedgerEntry(snap, entryId), "Ledger entry removed")
        }
    }

    fun submitCalculatorSettlement(
        date: String,
        dateFrom: String = date,
        seller: String,
        store: StoreConfig,
        totalSold: Double,
        returned: Double,
        returnType: String,
        returnTargetRep: String,
        expired: Double,
        shared: Double,
        creditQty: Double,
        unitPrice: Double,
        fieldExpenseItems: List<Map<String, Any>>,
        commissionPerUnit: Double = 0.0,
        commissionItems: List<Map<String, Any>>,
        prevColl: Double,
        received: Double,
        addToLedger: Boolean,
        notes: String
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(
                dispatcher.saveCalculatorSettlement(
                    snap, date, dateFrom, seller, store, totalSold, returned, returnType,
                    returnTargetRep, expired, shared, creditQty, unitPrice,
                    fieldExpenseItems, commissionPerUnit, commissionItems, prevColl, received, addToLedger, notes
                ),
                "Settlement recorded"
            )
        }
    }

    fun editCalculatorSettlement(
        original: CalculatorRecord,
        date: String,
        dateFrom: String,
        seller: String,
        store: StoreConfig,
        totalSold: Double,
        returned: Double,
        returnType: String,
        returnTargetRep: String,
        expired: Double,
        shared: Double,
        creditQty: Double,
        unitPrice: Double,
        fieldExpenseItems: List<Map<String, Any>>,
        commissionPerUnit: Double,
        commissionItems: List<Map<String, Any>>,
        prevColl: Double,
        received: Double,
        notes: String,
        onSuccess: () -> Unit = {}
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = calcAndRepairActions.editCalculatorSettlement(
                snap, original, date, dateFrom, seller, store, totalSold, returned, returnType,
                returnTargetRep, expired, shared, creditQty, unitPrice, fieldExpenseItems,
                commissionPerUnit, commissionItems, prevColl, received, notes
            )
            handleResult(res, "Calculator entry updated")
            if (res.isSuccess) onSuccess()
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

    fun saveStore(existingKey: String?, name: String, formulaId: String?, rate: Double, active: Boolean) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveStore(snap, existingKey, name, formulaId, rate, active), "Store saved")
        }
    }

    fun deleteStore(storeKey: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.deleteStore(snap, storeKey), "Store removed")
        }
    }

    fun saveFormula(existing: FormulaConfig?, name: String, additionalCost: Double, adjustment: Double, ingredients: List<FormulaIngredient>) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveFormula(snap, existing, name, additionalCost, adjustment, ingredients), "Formula saved")
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

    fun saveFormulaCalc(formula: FormulaConfig, calc: SavedCalcRecord) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveFormulaSavedCalc(snap, formula, calc), "Calculation saved")
        }
    }

    fun deleteFormulaCalc(formula: FormulaConfig, calcId: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.deleteFormulaSavedCalc(snap, formula, calcId), "Saved calculation deleted")
        }
    }

    fun addOrUpdateEntity(existingId: String?, name: String, type: String, phone: String, opening: Double, notes: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.addOrUpdateEntity(snap, existingId, name, type, phone, opening, notes), "Account saved")
        }
    }

    fun saveCustomerOrClient(
        existing: CustomerRecord?,
        isRepClient: Boolean,
        name: String,
        phone: String,
        address: String,
        oldDebit: Double,
        customSalePrice: Double,
        salesRep: String,
        notes: String,
        lat: Double? = existing?.gpsLat,
        lng: Double? = existing?.gpsLng
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(
                dispatcher.saveCustomerOrClient(
                    snap, existing, isRepClient, name, phone, address, oldDebit, customSalePrice, salesRep, notes, lat, lng
                ),
                "Customer saved"
            )
        }
    }

    fun editSaleOrCollection(
        original: SalesRecord,
        date: String,
        supplyDate: String,
        paymentType: String,
        customerName: String,
        store: StoreConfig?,
        salesRep: String,
        quantityKg: Double,
        unitPrice: Double,
        collectedAmount: Double,
        expiredKg: Double,
        expiredUnitPrice: Double,
        notes: String,
        customerPhone: String = "",
        onSuccess: () -> Unit = {}
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = editActions.editSaleOrCollection(
                snap, original, date, supplyDate, paymentType, customerName, store, salesRep,
                quantityKg, unitPrice, collectedAmount, expiredKg, expiredUnitPrice, notes, customerPhone
            )
            handleResult(res, "Sales record updated")
            if (res.isSuccess) onSuccess()
        }
    }

    fun editRepRecord(
        original: SalesRecord,
        date: String,
        paymentType: String,
        customerName: String,
        store: StoreConfig?,
        quantityKg: Double,
        unitPrice: Double,
        collectedAmount: Double,
        expiredKg: Double,
        notes: String,
        customerPhone: String = "",
        expiredUnitPrice: Double = 0.0,
        onSuccess: () -> Unit = {}
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = editActions.editRepRecord(
                snap, original, date, paymentType, customerName, store, quantityKg, unitPrice, collectedAmount, expiredKg, notes, customerPhone, expiredUnitPrice
            )
            handleResult(res, "Rep entry updated")
            if (res.isSuccess) onSuccess()
        }
    }

    fun deleteRepRecord(
        rec: SalesRecord,
        displayAmount: String,
        onBlocked: (String) -> Unit = {}
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = editActions.deleteRepRecordWithLinks(snap, rec, displayAmount)
            if (res.isFailure) {
                onBlocked(res.exceptionOrNull()?.localizedMessage ?: "Cannot delete rep entry")
            } else {
                handleResult(res, "Moved to Recycle Bin")
            }
        }
    }

    fun editFactoryBatch(
        original: FactoryRecord,
        date: String,
        formula: FormulaConfig,
        units: Double,
        notes: String,
        onSuccess: () -> Unit = {}
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = editActions.editFactoryBatch(snap, original, date, formula, units, notes)
            handleResult(res, "Factory batch updated")
            if (res.isSuccess) onSuccess()
        }
    }

    fun editPurchaseLot(
        lot: InventoryRecord,
        date: String,
        unitName: String,
        conversionFactor: Double,
        qtyUnits: Double,
        costPerUnit: Double,
        notes: String,
        onSuccess: () -> Unit = {}
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = editActions.editPurchaseLot(snap, lot, date, unitName, conversionFactor, qtyUnits, costPerUnit, notes)
            handleResult(res, "Purchase lot updated")
            if (res.isSuccess) onSuccess()
        }
    }

    fun deletePurchaseLot(lot: InventoryRecord, onSuccess: () -> Unit = {}) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = editActions.deletePurchaseLot(snap, lot)
            if (res.isSuccess) {
                dispatcher.syncLedgerMirrorToStorage(calcAndRepairActions.removeDocFromSnapshot(snap, "inventory", lot.id))
                onSuccess()
            }
            handleResult(res, "Purchase lot moved to Recycle Bin")
        }
    }

    fun editPaymentTransaction(
        original: PaymentTransaction,
        date: String,
        entity: EntityRecord?,
        customName: String,
        paymentMethod: String,
        amount: Double,
        description: String,
        photoDataUri: String? = null,
        clearPhoto: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = editActions.editPaymentTransaction(snap, original, date, entity, customName, paymentMethod, amount, description, photoDataUri, clearPhoto)
            handleResult(res, "Transaction updated")
            if (res.isSuccess) onSuccess()
        }
    }

    fun editExpenseRecord(
        original: ExpenseRecord,
        date: String,
        name: String,
        category: String,
        paymentMethod: String,
        amount: Double,
        notes: String,
        photoDataUri: String? = null,
        clearPhoto: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = editActions.editExpenseRecord(snap, original, date, name, category, paymentMethod, amount, notes, photoDataUri, clearPhoto)
            handleResult(res, "Transaction updated")
            if (res.isSuccess) onSuccess()
        }
    }

    fun saveEntityExtended(
        existingId: String?,
        name: String,
        type: String,
        phone: String,
        wallet: String,
        ledgerPage: String,
        openingBalance: Double,
        notes: String,
        photoDataUri: String?,
        clearPhoto: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = editActions.saveEntityExtended(snap, existingId, name, type, phone, wallet, ledgerPage, openingBalance, notes, photoDataUri, clearPhoto)
            handleResult(res, "Account saved")
            if (res.isSuccess) onSuccess()
        }
    }

    fun saveCustomerOrClientExtended(
        existing: CustomerRecord?,
        isRepClient: Boolean,
        name: String,
        phone: String,
        address: String,
        oldDebit: Double,
        customSalePrice: Double,
        salesRep: String,
        ledgerPage: String,
        notes: String,
        lat: Double? = existing?.gpsLat,
        lng: Double? = existing?.gpsLng,
        photoDataUri: String?,
        clearPhoto: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = editActions.saveCustomerOrClientExtended(
                snap, existing, isRepClient, name, phone, address, oldDebit, customSalePrice, salesRep, ledgerPage, notes, lat, lng, photoDataUri, clearPhoto
            )
            handleResult(res, "Customer saved")
            if (res.isSuccess) onSuccess()
        }
    }

    fun deleteRecordEditHistory(collection: String, docId: String, index: Int) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(editActions.deleteEditHistoryAt(snap, collection, docId, index), "History entry deleted")
        }
    }

    fun repairSalesLinks(kind: String = "sales", onDone: (String) -> Unit = {}) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = calcAndRepairActions.repairSalesLinks(snap, kind)
            if (res.isSuccess) {
                val msg = res.getOrThrow().text
                showToast(msg, ToastKind.SUCCESS)
                onDone(msg)
            } else {
                showToast(res.exceptionOrNull()?.localizedMessage ?: "Link repair failed", ToastKind.ERROR)
            }
        }
    }

    fun applyOverviewFix(col: String, docId: String, codes: List<String>, rawValues: Map<String, Any?>, onSuccess: () -> Unit = {}) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = calcAndRepairActions.applyOverviewFix(snap, col, docId, codes, rawValues)
            handleResult(res, res.getOrNull() ?: "Record fixed")
            if (res.isSuccess) onSuccess()
        }
    }

    fun deleteAuditFindingRecord(col: String, docId: String, onSuccess: () -> Unit = {}) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = calcAndRepairActions.deleteAuditFindingRecord(snap, col, docId)
            handleResult(res, "Moved to Recycle Bin")
            if (res.isSuccess) onSuccess()
        }
    }

    fun softDeleteRecord(
        collection: String,
        docId: String,
        displayName: String,
        displayDetail: String = "",
        displayAmount: String = "",
        snapshotMap: Map<String, Any> = emptyMap()
    ) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = ops.softDeleteToRecycleBin(snap.profile, collection, docId, displayName, displayDetail, displayAmount, snapshotMap)
            if (res.isSuccess && collection in setOf("sales", "transactions", "expenses", "calculator", "inventory")) {
                dispatcher.syncLedgerMirrorToStorage(calcAndRepairActions.removeDocFromSnapshot(snap, collection, docId))
            }
            handleResult(res, "Moved to Recycle Bin")
        }
    }

    fun recyclePair(collection: String, pairId: String, fallbackDocId: String, displayName: String) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            if (pairId.isBlank()) {
                val res = ops.softDeleteToRecycleBin(snap.profile, collection, fallbackDocId, displayName, "", "", emptyMap())
                if (res.isSuccess && collection == "transactions") {
                    dispatcher.syncLedgerMirrorToStorage(calcAndRepairActions.removeDocFromSnapshot(snap, collection, fallbackDocId))
                }
                handleResult(res, "Moved to Recycle Bin")
                return@launch
            }
            var last: Result<Unit> = Result.success(Unit)
            when (collection) {
                "production" -> snap.production.filter { it.deletedAt == null && it.transferPairId == pairId }.forEach {
                    last = ops.softDeleteToRecycleBin(snap.profile, "production", it.id, displayName, it.date, "${it.net} kg", WorkspaceJsonCodec.productionToMap(it))
                }
                "transactions" -> {
                    val goneIds = mutableSetOf<String>()
                    snap.transactions.filter { it.deletedAt == null && it.transferPairId == pairId }.forEach {
                        last = ops.softDeleteToRecycleBin(snap.profile, "transactions", it.id, displayName, it.date, "${it.amount}", WorkspaceJsonCodec.transactionToMap(it))
                        if (last.isSuccess) goneIds.add(it.id)
                    }
                    if (goneIds.isNotEmpty()) {
                        dispatcher.syncLedgerMirrorToStorage(snap.copy(transactions = snap.transactions.filterNot { it.id in goneIds }))
                    }
                }
            }
            handleResult(last, "Moved transfer pair to Recycle Bin")
        }
    }

    fun restoreTombstone(tombstone: DeletionTombstone) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(ops.restoreFromRecycleBin(snap.profile, tombstone), "Record restored")
        }
    }

    fun purgeTombstone(tombstone: DeletionTombstone) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = ops.purgeDeletedForever(snap.profile, tombstone)
            if (res.isSuccess) {
                dispatcher.syncLedgerMirrorToStorage(
                    snap.copy(erasedIds = snap.erasedIds + tombstone.recordId.ifBlank { tombstone.id })
                )
            }
            handleResult(res, "Deleted permanently")
        }
    }

    fun emptyRecycleBin(items: List<DeletionTombstone>) {
        if (items.isEmpty()) return
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val erased = mutableSetOf<String>()
            var lastRes: Result<Unit> = Result.success(Unit)
            items.forEach { t ->
                val res = ops.purgeDeletedForever(snap.profile, t)
                if (res.isSuccess) {
                    erased += t.recordId.ifBlank { t.id }
                }
                lastRes = res
            }
            if (erased.isNotEmpty()) {
                dispatcher.syncLedgerMirrorToStorage(
                    snap.copy(erasedIds = snap.erasedIds + erased)
                )
            }
            handleResult(lastRes, "Emptied ${erased.size} item(s) permanently")
        }
    }

    fun reviewPending(item: PendingApproval, approve: Boolean) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(ops.reviewPendingApproval(snap.profile, item, approve), if (approve) "Approved" else "Discarded")
        }
    }

    fun inviteMember(email: String, displayName: String, roles: List<String>) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(ops.inviteTeamMember(snap.profile, email, displayName, roles), "Team invite created")
        }
    }

    fun revokeInvite(inviteId: String) {
        viewModelScope.launch {
            handleResult(ops.revokeInvite(inviteId), "Invite revoked")
        }
    }

    fun updateMemberRoles(memberUid: String, roles: List<String>, approved: Boolean) {
        viewModelScope.launch {
            handleResult(ops.updateMemberRoles(memberUid, roles, approved), "Permissions updated")
        }
    }

    fun saveAccountsDirectory(accounts: List<WorkspaceAccountRecord>) {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            handleResult(dispatcher.saveAccountsDirectory(snap, accounts), "Accounts directory updated")
        }
    }

    fun auditRepriceZeroCostSales() {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            val res = dispatcher.auditRepriceZeroCostSales(snap)
            if (res.isSuccess) {
                val n = res.getOrDefault(0)
                showToast(
                    if (n > 0) "Repriced $n sale(s) from production cost" else "No zero-cost sales to reprice",
                    ToastKind.INFO
                )
            } else {
                showToast(res.exceptionOrNull()?.localizedMessage ?: "Reprice failed", ToastKind.ERROR)
            }
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

    fun buildPlainBackupJson(): String? = currentSnapshot()?.let { dispatcher.buildBackupJson(it) }

    fun buildEncryptedGzndV4(password: String): ByteArray? {
        val snap = currentSnapshot() ?: return null
        val json = dispatcher.buildBackupJson(snap)
        return WorkspaceJsonCodec.encryptGzndV4(
            jsonText = json,
            email = snap.profile.email,
            password = password,
            uid = snap.profile.uid
        )
    }

    fun restoreBackupBytesOrJson(rawText: String?, rawBytes: ByteArray?, password: String = "") {
        val snap = currentSnapshot() ?: return
        viewModelScope.launch {
            runCatching {
                val jsonText = when {
                    rawBytes != null && rawBytes.size >= 11 && String(rawBytes.copyOfRange(0, 11), Charsets.US_ASCII) == "GZND_ENC_V4" -> {
                        if (password.isBlank()) error("Enter backup password to decrypt .gznd file")
                        WorkspaceJsonCodec.decryptGzndV4(rawBytes, snap.profile.email, password, snap.profile.uid)
                    }
                    !rawText.isNullOrBlank() -> rawText
                    rawBytes != null -> String(rawBytes, Charsets.UTF_8)
                    else -> error("Empty backup payload")
                }
                val root = JSONObject(jsonText)
                dispatcher.restoreBackupPayload(snap, root).getOrThrow()
            }.onSuccess { n ->
                showToast(
                    if (n > 0) "Restored $n record(s) from backup" else "Backup verified — all records already present",
                    ToastKind.SUCCESS
                )
            }.onFailure { err ->
                showToast("Restore failed: ${err.localizedMessage}", ToastKind.ERROR)
            }
        }
    }

    companion object {
        fun factory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                SarimViewModel(AppRepository(app.applicationContext), currentUserId)
            }
        }
    }
}
