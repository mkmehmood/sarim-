package com.gullzubair.sarim.data.model

import kotlinx.serialization.Serializable

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class MainTab(val key: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    PRODUCTION("production", "Production"),
    SALES("sales", "Sales"),
    CALCULATOR("calculator", "Calculator"),
    FACTORY("factory", "Factory"),
    PAYMENTS("payments", "Payments"),
    REP_SALES("rep", "Rep Sales");

    companion object {
        fun fromKey(key: String): MainTab = entries.firstOrNull { it.key == key } ?: OVERVIEW
    }
}

sealed interface AppRoute {
    @Serializable data object WorkspaceHome : AppRoute
    @Serializable data object DailyLedger : AppRoute
    @Serializable data object Approvals : AppRoute
    @Serializable data object StockTransfer : AppRoute
    @Serializable data object PaymentTransfer : AppRoute
    @Serializable data object StoreManager : AppRoute
    @Serializable data object FormulaStore : AppRoute
    @Serializable data class FormulaEdit(val formulaId: String = "") : AppRoute
    @Serializable data class BatchMix(val formulaId: String = "") : AppRoute
    @Serializable data class BatchFix(val formulaId: String = "") : AppRoute
    @Serializable data object RawMaterialPurchase : AppRoute
    @Serializable data object AddEntity : AppRoute
    @Serializable data object AppAccounts : AppRoute
    @Serializable data object RecycleBin : AppRoute
    @Serializable data object BackupRestore : AppRoute
    @Serializable data object DbStructure : AppRoute
    @Serializable data object Settings : AppRoute
    @Serializable data object SoundHaptics : AppRoute
    @Serializable data object CloseFinancialYear : AppRoute
    @Serializable data class EditCustomer(val customerName: String) : AppRoute
    @Serializable data class CustomerDetail(val customerName: String) : AppRoute
    @Serializable data class EntityDetail(val entityName: String) : AppRoute
}

data class WorkspaceSnapshot(
    val profile: UserProfile = UserProfile(),
    val settings: WorkspaceSettings = WorkspaceSettings(),
    val stores: List<StoreConfig> = emptyList(),
    val formulas: List<FormulaConfig> = emptyList(),
    val customers: List<CustomerRecord> = emptyList(),
    val entities: List<EntityRecord> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val production: List<ProductionRecord> = emptyList(),
    val sales: List<SalesRecord> = emptyList(),
    val repRecords: List<RepRecord> = emptyList(),
    val calculator: List<CalculatorSettlement> = emptyList(),
    val factory: List<FactoryRecord> = emptyList(),
    val transactions: List<PaymentTransaction> = emptyList(),
    val ledgerEntries: List<DailyLedgerEntry> = emptyList(),
    val pendingApprovals: List<PendingApproval> = emptyList(),
    val invites: List<WorkspaceInvite> = emptyList(),
    val teamMembers: List<UserProfile> = emptyList()
)
