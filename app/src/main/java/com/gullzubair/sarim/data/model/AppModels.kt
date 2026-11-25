package com.gullzubair.sarim.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val workspaceOwnerUid: String = "",
    val role: String = "admin",
    val roles: List<String> = listOf("admin"),
    val approved: Boolean = true,
    val inviteId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WorkspaceInvite(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val memberEmail: String = "",
    val displayName: String = "",
    val roles: List<String> = emptyList(),
    val approved: Boolean = true,
    val status: String = "pending",
    val acceptedByUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StoreConfig(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val storeKey: String = "",
    val name: String = "",
    val formulaId: String = "",
    val salePrice: Double = 0.0,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FormulaConfig(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val unitsPerBatch: Double = 1.0,
    val additionalCost: Double = 0.0,
    val costAdjustment: Double = 0.0,
    val ingredients: List<Map<String, Any>> = emptyList(),
    val savedMixes: List<Map<String, Any>> = emptyList(),
    val savedCorrections: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CustomerRecord(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val routeLat: Double? = null,
    val routeLng: Double? = null,
    val visitCount: Long = 0L,
    val openingBalance: Double = 0.0,
    val notes: String? = null,
    val recycled: Boolean = false,
    val recycledAtEpoch: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EntityRecord(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val entityType: String = "party", // party | supplier | staff | expense | bank
    val phone: String = "",
    val notes: String? = null,
    val openingBalance: Double = 0.0,
    val recycled: Boolean = false,
    val recycledAtEpoch: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class InventoryItem(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val name: String = "",
    val unitName: String = "kg",
    val conversionFactor: Double = 1.0,
    val stockQty: Double = 0.0,
    val avgCostPerKg: Double = 0.0,
    val minThreshold: Double = 0.0,
    val supplierName: String? = null,
    val recycled: Boolean = false,
    val recycledAtEpoch: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ProductionRecord(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val date: String = "",
    val storeKey: String = "",
    val storeName: String = "",
    val formulaId: String? = null,
    val formulaUnits: Double = 0.0,
    val grossWeight: Double = 0.0,
    val containerWeight: Double = 0.0,
    val netWeight: Double = 0.0,
    val costPerKg: Double = 0.0,
    val salePrice: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalRevenue: Double = 0.0,
    val profit: Double = 0.0,
    val transfer: Boolean = false,
    val transferType: String = "none", // none | out | in
    val transferPairId: String? = null,
    val transferPeerStore: String? = null,
    val notes: String? = null,
    val recycled: Boolean = false,
    val recycledAtEpoch: Long = 0L,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SalesRecord(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val date: String = "",
    val mode: String = "sale", // sale | collection
    val customerId: String? = null,
    val customerName: String = "",
    val storeKey: String = "",
    val storeName: String? = null,
    val repName: String = "NONE",
    val qtyKg: Double = 0.0,
    val ratePerKg: Double = 0.0,
    val totalAmount: Double = 0.0,
    val cashReceived: Double = 0.0,
    val creditAmount: Double = 0.0,
    val expiredKg: Double = 0.0,
    val choraValue: Double = 0.0,
    val addToLedger: Boolean = true,
    val ledgerEntryId: String? = null,
    val notes: String? = null,
    val recycled: Boolean = false,
    val recycledAtEpoch: Long = 0L,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RepRecord(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val date: String = "",
    val repName: String = "",
    val mode: String = "sale", // sale | collection | return
    val customerId: String? = null,
    val customerName: String = "",
    val storeKey: String = "",
    val qtyKg: Double = 0.0,
    val ratePerKg: Double = 0.0,
    val totalAmount: Double = 0.0,
    val cashReceived: Double = 0.0,
    val creditAmount: Double = 0.0,
    val routeLat: Double? = null,
    val routeLng: Double? = null,
    val addressConfirmed: Boolean = false,
    val notes: String? = null,
    val recycled: Boolean = false,
    val recycledAtEpoch: Long = 0L,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CalculatorSettlement(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val dateFrom: String = "",
    val dateTo: String = "",
    val repName: String = "",
    val storeKey: String = "",
    val totalSoldKg: Double = 0.0,
    val returnedKg: Double = 0.0,
    val returnTarget: String = "store", // store | rep
    val returnTargetName: String? = null,
    val expiredKg: Double = 0.0,
    val sharedDeduction: Double = 0.0,
    val netSoldKg: Double = 0.0,
    val cashQtyKg: Double = 0.0,
    val creditQtyKg: Double = 0.0,
    val ratePerKg: Double = 0.0,
    val commissionTotal: Double = 0.0,
    val commissions: List<Map<String, Any>> = emptyList(),
    val expenseTotal: Double = 0.0,
    val expenses: List<Map<String, Any>> = emptyList(),
    val expectedCash: Double = 0.0,
    val physicalCash: Double = 0.0,
    val variance: Double = 0.0,
    val addToLedger: Boolean = true,
    val ledgerEntryId: String? = null,
    val notes: String? = null,
    val recycled: Boolean = false,
    val recycledAtEpoch: Long = 0L,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FactoryRecord(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val date: String = "",
    val recordType: String = "batch", // batch | purchase
    val formulaId: String? = null,
    val formulaName: String? = null,
    val storeKey: String? = null,
    val unitsProduced: Long = 0L,
    val rawItemId: String? = null,
    val rawItemName: String? = null,
    val supplierName: String? = null,
    val qtyRawUnit: Double = 0.0,
    val conversionFactor: Double = 1.0,
    val qtyKg: Double = 0.0,
    val unitCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val paidAmount: Double = 0.0,
    val payableBalance: Double = 0.0,
    val allocatedPaid: Double = 0.0,
    val settled: Boolean = false,
    val ingredientsUsed: List<Map<String, Any>> = emptyList(),
    val notes: String? = null,
    val recycled: Boolean = false,
    val recycledAtEpoch: Long = 0L,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PaymentTransaction(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val date: String = "",
    val txType: String = "out", // in | out | expense | transfer_out | transfer_in
    val entityId: String? = null,
    val entityName: String = "",
    val entityCategory: String = "party",
    val paymentChannel: String = "Cash",
    val amount: Double = 0.0,
    val transferPairId: String? = null,
    val peerEntityName: String? = null,
    val addToLedger: Boolean = true,
    val ledgerEntryId: String? = null,
    val notes: String? = null,
    val recycled: Boolean = false,
    val recycledAtEpoch: Long = 0L,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DailyLedgerEntry(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val date: String = "",
    val entryType: String = "IN", // IN | OUT | TRANSFER
    val expenseScope: String = "none", // business | home | none
    val channel: String = "Cash",
    val toChannel: String? = null,
    val category: String = "General",
    val partyName: String = "",
    val amount: Double = 0.0,
    val tabEntry: Boolean = false,
    val sourceCollection: String? = null,
    val sourceDocId: String? = null,
    val notes: String? = null,
    val recycled: Boolean = false,
    val recycledAtEpoch: Long = 0L,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PendingApproval(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val submittedByUid: String = "",
    val submittedByName: String = "",
    val submittedByEmail: String = "",
    val action: String = "set", // set | delete
    val targetCollection: String = "sales",
    val targetDocId: String = "",
    val summaryTitle: String = "",
    val summaryDetail: String = "",
    val recordDate: String = "",
    val payloadData: Map<String, Any> = emptyMap(),
    val status: String = "pending", // pending | approved | rejected
    val reviewedByUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WorkspaceSettings(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val currencySymbol: String = "Rs",
    val financialYearStartMonth: Long = 7L,
    val hideSensitiveMoney: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val themeMode: String = "dark", // dark | light | system
    val paymentChannels: List<String> = listOf("Cash", "Bank", "Easypaisa", "JazzCash"),
    val expenseCategories: List<String> = listOf("Fuel", "Wages", "Rent", "Electricity", "Freight", "Maintenance", "Tea & Meals", "Office", "Other"),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
