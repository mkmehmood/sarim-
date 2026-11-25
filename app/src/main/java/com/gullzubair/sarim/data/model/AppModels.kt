package com.gullzubair.sarim.data.model

import androidx.annotation.Keep

@Keep
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val ownerUid: String? = null,
    val role: String = "admin",
    val roles: List<String> = listOf("admin"),
    val approved: Boolean = true,
    val createdBy: String = "",
    val inviteId: String? = null,
    val lastWrite: Long = 0L
) {
    val effectiveOwnerUid: String
        get() = ownerUid?.takeIf { it.isNotBlank() } ?: uid

    val isAdmin: Boolean
        get() = "admin" in roles || role == "admin"
}

@Keep
data class WorkspaceInvite(
    val id: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val memberEmail: String = "",
    val displayName: String = "",
    val roles: List<String> = emptyList(),
    val approved: Boolean = true,
    val status: String = "pending",
    val acceptedByUid: String? = null
)

@Keep
data class WorkspaceAccountRecord(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val roles: List<String> = listOf("rep"),
    val approved: Boolean = true,
    val ownerUid: String = "",
    val createdBy: String = ""
)

@Keep
data class StoreConfig(
    val key: String = "",
    val name: String = "",
    val salePrice: Double = 0.0,
    val formulaId: String? = null,
    val active: Boolean = true,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null
) {
    val storeKey: String get() = key
    val id: String get() = key
}

@Keep
data class FormulaIngredient(
    val id: String = "",
    val kind: String? = null,
    val formulaId: String? = null,
    val name: String = "",
    val cost: Double = 0.0,
    val quantity: Double = 0.0,
    val custom: Boolean = false
)

@Keep
data class SavedCalcRecord(
    val id: String = "",
    val kind: String = "mix", // mix | fix
    val name: String = "",
    val mode: String = "units", // units | total | ingredient
    val value: Double = 0.0,
    val ingredientId: String? = null,
    val original: Double = 0.0,
    val used: Double = 0.0,
    val actual: Map<String, Double> = emptyMap(),
    val manualId: String? = null,
    val manualAdd: Double? = null,
    val manualTotal: Double? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Keep
data class FormulaConfig(
    val id: String = "",
    val name: String = "",
    val ingredients: List<FormulaIngredient> = emptyList(),
    val additionalCost: Double = 0.0,
    val costAdjustmentFactor: Double = 1.0,
    val unitsPerBatch: Double = 1.0,
    val savedCalcs: List<SavedCalcRecord> = emptyList(),
    val updatedAt: Long = 0L
)

@Keep
data class ProductionRecord(
    val id: String = "",
    val date: String = "",
    val time: String = "",
    val store: String = "",
    val grossWt: Double = 0.0,
    val contWt: Double = 0.0,
    val net: Double = 0.0,
    val cp: Double = 0.0,
    val sp: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalSale: Double = 0.0,
    val profit: Double = 0.0,
    val formulaUnits: Double = 0.0,
    val formulaId: String = "",
    val formulaName: String = "",
    val formulaMaterials: List<Map<String, Any>> = emptyList(),
    val formulaCost: Double = 0.0,
    val paymentStatus: String = "",
    val photoKeys: List<String> = emptyList(),
    val isReturn: Boolean = false,
    val returnedBy: String = "",
    val returnNote: String = "",
    val isTransfer: Boolean = false,
    val transferDirection: String = "", // "" | "out" | "in"
    val transferPairId: String = "",
    val transferPeerStore: String = "",
    val transferNote: String = "",
    val isMerged: Boolean = false,
    val managedBy: String = "",
    val createdBy: String = "",
    val recordDate: String = "",
    val timestamp: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAtEpoch: Long = 0L,
    val deletedAt: Long? = null
)

@Keep
data class SalesRecord(
    val id: String = "",
    val date: String = "",
    val supplyDate: String = "",
    val time: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val quantity: Double = 0.0,
    val supplyStore: String = "",
    val paymentType: String = "CASH", // CASH | CREDIT | COLLECTION | PARTIAL_PAYMENT | MONEY_OUT | REP_RETURN
    val salesRep: String = "",
    val currentRepProfile: String = "",
    val unitPrice: Double = 0.0,
    val totalValue: Double = 0.0,
    val totalCost: Double = 0.0,
    val profit: Double = 0.0,
    val costSource: String = "", // production-day | store-average | none
    val creditReceived: Boolean = true,
    val partialPaymentReceived: Double = 0.0,
    val collectionAllocs: Map<String, Double> = emptyMap(),
    val isCollection: Boolean = false,
    val collectedAmount: Double = 0.0,
    val allocations: Map<String, Double> = emptyMap(),
    val expiredQty: Double = 0.0,
    val expiredValue: Double = 0.0,
    val expiredUnitPrice: Double = 0.0,
    val expiredApplied: Double = 0.0,
    val expiredAllocated: Double = 0.0,
    val transactionType: String = "", // OLD_DEBT | ""
    val isRepTransfer: Boolean = false,
    val repTransferFrom: String = "",
    val isTransfer: Boolean = false,
    val transferFrom: String = "",
    val transferPairId: String = "",
    val relatedSaleId: String = "",
    val affectsInventory: Boolean = true,
    val usedInCalcId: String = "",
    val isRepReturn: Boolean = false,
    val returnedQty: Double = 0.0,
    val returnValue: Double = 0.0,
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val gpsAccuracy: Double? = null,
    val gpsConfirmed: Boolean = false,
    val notes: String = "",
    val isMerged: Boolean = false,
    val timestamp: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAtEpoch: Long = 0L,
    val deletedAt: Long? = null
)

@Keep
data class CustomerRecord(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val oldDebit: Double = 0.0,
    val customSalePrice: Double = 0.0,
    val salesRep: String = "",
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val visitCount: Long = 0L,
    val notes: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val timestamp: Long = 0L,
    val deletedAt: Long? = null
)

@Keep
data class CalculatorRecord(
    val id: String = "",
    val seller: String = "",
    val date: String = "",
    val unitPrice: Double = 0.0,
    val costPrice: Double = 0.0,
    val revenue: Double = 0.0,
    val profit: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalSold: Double = 0.0,
    val returned: Double = 0.0,
    val returnStore: String = "",
    val returnEntryId: String = "",
    val returnLogId: String = "",
    val expiredApplied: Double = 0.0,
    val returnRep: String = "",
    val returnType: String = "", // store | rep | ""
    val transferSaleId: String = "",
    val expired: Double = 0.0,
    val shared: Double = 0.0,
    val creditQty: Double = 0.0,
    val cashQty: Double = 0.0,
    val creditValue: Double = 0.0,
    val prevColl: Double = 0.0,
    val fieldExpenses: Double = 0.0,
    val fieldExpenseItems: List<Map<String, Any>> = emptyList(),
    val commissionPaidItems: List<Map<String, Any>> = emptyList(),
    val totalExpected: Double = 0.0,
    val received: Double = 0.0,
    val commissionPerUnit: Double = 0.0,
    val commissionPaid: Double = 0.0,
    val grossCommission: Double = 0.0,
    val commissionPayable: Double = 0.0,
    val statusText: String = "",
    val statusClass: String = "",
    val linkedSalesIds: List<String> = emptyList(),
    val linkedRepSalesIds: List<String> = emptyList(),
    val linkedRepReturnIds: List<String> = emptyList(),
    val notes: String = "",
    val isMerged: Boolean = false,
    val timestamp: Long = 0L,
    val updatedAtEpoch: Long = 0L,
    val deletedAt: Long? = null
)

@Keep
data class InventoryRecord(
    val id: String = "",
    val name: String = "",
    val quantity: Double = 0.0,
    val unit: String = "kg",
    val cost: Double = 0.0,
    val totalValue: Double = 0.0,
    val collectedKg: Double = 0.0,
    val conversionFactor: Double = 1.0,
    val purchaseUnitName: String = "",
    val purchaseCost: Double = 0.0,
    val purchaseQuantity: Double = 0.0,
    val purchasesTracked: Boolean = false,
    val isPurchaseLot: Boolean = false,
    val materialRefId: String = "",
    val materialName: String = "",
    val purchaseKg: Double = 0.0,
    val purchaseDate: String = "",
    val paymentStatus: String = "", // "" | "pending" | "paid"
    val supplierId: String = "",
    val supplierName: String = "",
    val supplierContact: String = "",
    val supplierType: String = "",
    val totalPayable: Double = 0.0,
    val paidDate: String = "",
    val notes: String = "",
    val timestamp: Long = 0L,
    val updatedAtEpoch: Long = 0L,
    val deletedAt: Long? = null
)

@Keep
data class FactoryRecord(
    val id: String = "",
    val date: String = "",
    val time: String = "",
    val store: String = "",
    val formulaId: String = "",
    val formulaName: String = "",
    val units: Double = 0.0,
    val totalCost: Double = 0.0,
    val materialsCost: Double = 0.0,
    val additionalCost: Double = 0.0,
    val rawMaterialsUsed: List<Map<String, Any>> = emptyList(),
    val materialsUsed: List<Map<String, Any>> = emptyList(),
    val notes: String = "",
    val isMerged: Boolean = false,
    val timestamp: Long = 0L,
    val updatedAtEpoch: Long = 0L,
    val deletedAt: Long? = null
)

@Keep
data class EntityRecord(
    val id: String = "",
    val name: String = "",
    val type: String = "party", // party | supplier | staff | expense | bank | payee | employee
    val contact: String = "",
    val phone: String = "",
    val isSupplier: Boolean = false,
    val isExpenseEntity: Boolean = false,
    val openingBalance: Double = 0.0,
    val notes: String = "",
    val timestamp: Long = 0L,
    val updatedAtEpoch: Long = 0L,
    val deletedAt: Long? = null
)

@Keep
data class PaymentTransaction(
    val id: String = "",
    val date: String = "",
    val time: String = "",
    val entityId: String = "",
    val entityName: String = "",
    val entityType: String = "",
    val type: String = "OUT", // IN | OUT
    val amount: Double = 0.0,
    val description: String = "",
    val paymentMethod: String = "Cash",
    val isPayable: Boolean = false,
    val isTransfer: Boolean = false,
    val transferPairId: String = "",
    val transferDirection: String = "",
    val peerEntityName: String = "",
    val isOpeningBalance: Boolean = false,
    val isMerged: Boolean = false,
    val timestamp: Long = 0L,
    val updatedAtEpoch: Long = 0L,
    val deletedAt: Long? = null
)

@Keep
data class ExpenseRecord(
    val id: String = "",
    val date: String = "",
    val name: String = "",
    val category: String = "Other",
    val amount: Double = 0.0,
    val description: String = "",
    val notes: String = "",
    val entityId: String = "",
    val entityName: String = "",
    val paymentMethod: String = "Cash",
    val isMerged: Boolean = false,
    val timestamp: Long = 0L,
    val updatedAtEpoch: Long = 0L,
    val deletedAt: Long? = null
)

@Keep
data class ReturnRecord(
    val id: String = "",
    val date: String = "",
    val store: String = "",
    val returnStore: String = "",
    val seller: String = "",
    val customerName: String = "",
    val quantity: Double = 0.0,
    val expiredQty: Double = 0.0,
    val recoveredQty: Double = 0.0,
    val recovered: Boolean = false,
    val recoveredDate: String = "",
    val returnType: String = "",
    val notes: String = "",
    val isMerged: Boolean = false,
    val timestamp: Long = 0L,
    val updatedAtEpoch: Long = 0L,
    val deletedAt: Long? = null
)

@Keep
data class DailyLedgerEntry(
    val id: String = "",
    val type: String = "IN", // IN | OUT | TRANSFER
    val amount: Double = 0.0,
    val category: String = "General",
    val scope: String = "", // "" | BUSINESS | HOME
    val mode: String = "Cash",
    val toMode: String = "",
    val party: String = "",
    val note: String = "",
    val ref: String = "",
    val date: String = "",
    val timestamp: Long = 0L,
    val updatedAt: Long = 0L,
    val linkCollection: String? = null,
    val linkDocId: String? = null
) {
    val isTabMirror: Boolean get() = !linkCollection.isNullOrBlank() && !linkDocId.isNullOrBlank()
}

@Keep
data class DeletionTombstone(
    val id: String = "",
    val recordId: String = "",
    val collection: String = "",
    val recordType: String = "",
    val deletedAt: Long = 0L,
    val expiresAt: Long = 0L,
    val displayName: String = "",
    val displayDetail: String = "",
    val displayAmount: String = "",
    val snapshotJson: String = "",
    val deletedBy: String = "",
    val syncedToCloud: Boolean = false
)

@Keep
data class PendingApproval(
    val id: String = "",
    val collection: String = "",
    val docId: String = "",
    val action: String = "set", // set | delete
    val dataMap: Map<String, Any> = emptyMap(),
    val isEdit: Boolean = false,
    val status: String = "pending", // pending | approved | rejected
    val submittedByUid: String = "",
    val submittedByName: String = "",
    val submittedByEmail: String = "",
    val submittedAt: Long = 0L,
    val summaryTitle: String = "",
    val summaryBody: String = "",
    val decidedByUid: String? = null,
    val decidedByName: String? = null,
    val decidedAt: Long? = null
)

@Keep
data class PhotoBlob(
    val key: String = "",
    val dataUri: String = "",
    val deleted: Boolean = false,
    val updatedAt: Long = 0L
)

@Keep
data class WorkspaceSettings(
    val currencySymbol: String = "Rs",
    val financialYearStartMonth: Int = 7,
    val hideSensitiveMoney: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val themeMode: String = "light", // light | dark | system
    val repProfile: String = "",
    val reps: List<String> = emptyList(),
    val paymentModes: List<String> = listOf("Cash", "Bank", "Easypaisa", "JazzCash"),
    val expenseCategories: List<String> = listOf("Fuel", "Wages", "Rent", "Electricity", "Freight", "Maintenance", "Tea & Meals", "Office", "Other"),
    val fieldExpenseCategories: List<String> = listOf("Fuel", "Food", "Toll", "Repair", "Other"),
    val commissionCategories: List<String> = listOf("Sales Commission", "Recovery Bonus", "Target Bonus", "Other"),
    val defaultStorePrices: Map<String, Double> = emptyMap(),
    val storeCosts: Map<String, Double> = emptyMap(),
    val costAdjustments: Map<String, Double> = emptyMap(),
    val storeTracking: Map<String, Boolean> = emptyMap(),
    val fyCloseCount: Int = 0,
    val lastYearCloseAt: Long = 0L
)
