package com.gullzubair.sarim.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.EntityRecord
import com.gullzubair.sarim.data.model.ExpenseRecord
import com.gullzubair.sarim.data.model.PaymentTransaction
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.ConfirmRequest
import com.gullzubair.sarim.ui.components.ConfirmRequestHost
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.DecodedDataUrlImage
import com.gullzubair.sarim.ui.components.DialogTone
import com.gullzubair.sarim.ui.components.DiscardEditDialog
import com.gullzubair.sarim.ui.components.EditDiffConfirmDialog
import com.gullzubair.sarim.ui.components.EditedBadge
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.PaymentTxDetailSheet
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimYearMonthDayTree
import com.gullzubair.sarim.ui.components.TabHeading
import com.gullzubair.sarim.ui.components.blockingAlert
import com.gullzubair.sarim.ui.components.saveConfirmRequest
import com.gullzubair.sarim.ui.theme.CinzelFont
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.theme.PlayfairFont
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.ui.viewmodel.ToastKind
import com.gullzubair.sarim.util.CaptionedPhotoSharer
import com.gullzubair.sarim.util.PdfExporter
import com.gullzubair.sarim.util.StatementRow
import java.util.Locale
import kotlin.math.abs

private data class UnifiedEntityRowData(
    val entity: EntityRecord,
    val balance: Double,
    val statusLabel: String,
    val contactInfo: String,
    val photoDataUri: String?
)

private data class UnifiedExpenseGroupData(
    val name: String,
    val totalAmount: Double,
    val count: Int,
    val latestDate: String
)

private fun unifiedPeriodStartIso(period: String, todayIso: String): String {
    val today = DateUtils.parseLocalDate(todayIso) ?: return ""
    return when (period) {
        "today" -> today.toString()
        "week" -> today.minusDays(7).toString()
        "month" -> today.minusDays(30).toString()
        else -> ""
    }
}

@Composable
fun PaymentsScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onOpenAddEntity: () -> Unit,
    onOpenPaymentTransfer: () -> Unit,
    onOpenEntityDetail: (String) -> Unit,
    onOpenExpenseDetail: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val hidesHistory = remember(session) { FinanceEngine.hidesPaymentHistory(session) }
    val masked = hidesHistory || session.restrictMoney || snapshot.settings.hideSensitiveMoney

    val activeEntities = remember(snapshot.entities) { snapshot.entities.filter { it.deletedAt == null } }
    val activeTx = remember(snapshot.transactions) { snapshot.transactions.filter { it.deletedAt == null } }
    val activeExpenses = remember(snapshot.expenses) { snapshot.expenses.filter { it.deletedAt == null } }
    val activeInventory = remember(snapshot.inventory) { snapshot.inventory.filter { it.deletedAt == null } }
    val defaultChannel = snapshot.settings.paymentModes.firstOrNull() ?: "Cash"

    // Transaction Manager state
    var txKind by remember { mutableStateOf("OUT") }
    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var selectedEntity by remember { mutableStateOf<EntityRecord?>(null) }
    var customName by remember { mutableStateOf("") }
    var expCategory by remember(snapshot.settings.expenseCategories) {
        mutableStateOf(snapshot.settings.expenseCategories.firstOrNull() ?: "Other")
    }
    var amountStr by remember { mutableStateOf("") }
    var addToLedger by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var pendingPhotoUri by remember { mutableStateOf<String?>(null) }
    var clearExistingPhoto by remember { mutableStateOf(false) }

    // Tracker / Unified / History state
    var cashTrackerMode by remember { mutableStateOf("day") }
    var unifiedViewMode by remember { mutableStateOf("entities") }
    var unifiedPeriod by remember { mutableStateOf("month") }
    var unifiedSearch by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf("ALL") }

    // Detail & edit modals
    var selectedTx by remember { mutableStateOf<PaymentTransaction?>(null) }
    var editingTx by remember { mutableStateOf<PaymentTransaction?>(null) }
    var editingExp by remember { mutableStateOf<ExpenseRecord?>(null) }
    var showEditDiff by remember { mutableStateOf(false) }
    var showDiscardEdit by remember { mutableStateOf(false) }
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val encoded = CaptionedPhotoSharer.compressUriToDataUrl(context, uri)
            if (!encoded.isNullOrBlank()) {
                pendingPhotoUri = encoded
                clearExistingPhoto = false
            } else {
                vm.showToast("Could not read selected photo", ToastKind.ERROR)
            }
        }
    }

    fun resetForm(clearName: Boolean = false) {
        editingTx = null
        editingExp = null
        date = todayIso
        amountStr = ""
        notes = ""
        pendingPhotoUri = null
        clearExistingPhoto = false
        addToLedger = false
        if (clearName) {
            customName = ""
            selectedEntity = null
            txKind = "OUT"
        }
    }

    fun startEditTx(tx: PaymentTransaction) {
        if (tx.isMerged) {
            vm.showToast("This transaction cannot be edited.", ToastKind.WARNING); return
        }
        if (tx.isTransfer) {
            vm.showToast("Transfers are edited from the Transfer card.", ToastKind.WARNING); return
        }
        if (tx.isSettled) {
            vm.showToast("Settled records cannot be edited.", ToastKind.WARNING); return
        }
        if (tx.isPayable || tx.materialId.isNotBlank() || tx.supplierCreditAmount > 0.0) {
            vm.showToast("This payment settled a supplier payable or credit. Delete and re-enter it to change it.", ToastKind.WARNING, 5000L)
            return
        }
        editingExp = null
        editingTx = tx
        txKind = if (tx.isExpense) "EXPENSE" else if (tx.type.equals("IN", true)) "IN" else "OUT"
        date = tx.date.ifBlank { todayIso }
        customName = tx.entityName
        selectedEntity = activeEntities.firstOrNull { it.id == tx.entityId || it.name.equals(tx.entityName, true) }
        amountStr = InputGuards.fmtNum(tx.amount).replace(",", "")
        notes = tx.description
        val photoKey = "expense:${tx.expenseId.ifBlank { tx.id }}"
        pendingPhotoUri = snapshot.photos[photoKey]?.takeIf { !it.deleted }?.dataUri
        clearExistingPhoto = false
    }

    BackHandler(enabled = editingTx != null || editingExp != null) {
        showDiscardEdit = true
    }

    val health = remember(snapshot, cashTrackerMode, date) {
        FinanceEngine.calculateEconomicHealth(snapshot, cashTrackerMode, date)
    }
    val tracker = remember(snapshot, cashTrackerMode, date) {
        FinanceEngine.calculateCashTracker(snapshot, cashTrackerMode, date)
    }

    // Unified Records computation matching web `renderUnifiedTable()`
    val unifiedStartIso = remember(unifiedPeriod, todayIso) { unifiedPeriodStartIso(unifiedPeriod, todayIso) }
    val unifiedSummary = remember(activeEntities, activeTx, activeExpenses, activeInventory, unifiedStartIso) {
        var totalExp = 0.0
        activeExpenses.forEach { exp ->
            val d = exp.date.take(10)
            if ((unifiedStartIso.isEmpty() || d >= unifiedStartIso) && exp.category.equals("operating", true)) {
                totalExp += exp.amount
            }
        }

        val supplierIdSet = mutableSetOf<String>()
        activeInventory.forEach { if (it.supplierId.isNotBlank()) supplierIdSet.add(it.supplierId) }
        activeTx.forEach { if (it.isPayable && it.type.equals("IN", true) && it.supplierCreditAmount > 0.0) supplierIdSet.add(it.entityId) }

        val supplierBalances = mutableMapOf<String, Double>()
        activeInventory.forEach { m ->
            if (m.supplierId.isNotBlank() && m.paymentStatus == "pending" && m.totalPayable > 0.0 &&
                FinanceEngine.hasLiveSupplierInvoice(m, activeTx)
            ) {
                supplierBalances[m.supplierId] = (supplierBalances[m.supplierId] ?: 0.0) + m.totalPayable
            }
        }
        val entityBalances = mutableMapOf<String, Double>()
        activeEntities.forEach { e ->
            if (!e.isExpenseEntity && !supplierIdSet.contains(e.id)) {
                entityBalances[e.id] = 0.0
            }
        }
        activeTx.forEach { tx ->
            if (tx.isExpense || supplierIdSet.contains(tx.entityId)) return@forEach
            if (FinanceEngine.isOrphanSupplierTx(tx, activeInventory)) return@forEach
            if (entityBalances.containsKey(tx.entityId)) {
                val cur = entityBalances[tx.entityId] ?: 0.0
                entityBalances[tx.entityId] = if (tx.type.equals("OUT", true)) cur - tx.amount else cur + tx.amount
            }
        }

        var supPay = 0.0
        supplierBalances.values.forEach { if (it > 0.0) supPay += it }
        activeTx.forEach { tx ->
            if (!tx.isExpense && tx.isPayable && tx.type.equals("IN", true) && tx.supplierCreditAmount > 0.0) {
                supPay += tx.supplierCreditAmount
            }
        }
        var entPay = 0.0
        var entRec = 0.0
        entityBalances.values.forEach { bal ->
            if (bal > 0.0) entPay += bal else if (bal < 0.0) entRec += abs(bal)
        }
        QuadSummary(
            totalReceivables = InputGuards.round2(entRec),
            totalExpenses = InputGuards.round2(totalExp),
            supplierPayables = InputGuards.round2(supPay),
            entityPayables = InputGuards.round2(entPay)
        )
    }

    val unifiedEntityRows = remember(activeEntities, activeTx, activeInventory, snapshot.photos, unifiedSearch) {
        val supplierIdSet = mutableSetOf<String>()
        activeInventory.forEach { if (it.supplierId.isNotBlank()) supplierIdSet.add(it.supplierId) }
        activeTx.forEach { if (it.isPayable && it.type.equals("IN", true) && it.supplierCreditAmount > 0.0) supplierIdSet.add(it.entityId) }

        val supplierBalances = mutableMapOf<String, Double>()
        activeInventory.forEach { m ->
            if (m.supplierId.isNotBlank() && m.paymentStatus == "pending" && m.totalPayable > 0.0 &&
                FinanceEngine.hasLiveSupplierInvoice(m, activeTx)
            ) {
                supplierBalances[m.supplierId] = (supplierBalances[m.supplierId] ?: 0.0) + m.totalPayable
            }
        }
        activeTx.forEach { tx ->
            if (tx.isPayable && tx.type.equals("IN", true) && tx.supplierCreditAmount > 0.0) {
                supplierBalances[tx.entityId] = (supplierBalances[tx.entityId] ?: 0.0) + tx.supplierCreditAmount
            }
        }
        val entityBalances = mutableMapOf<String, Double>()
        activeEntities.forEach { e ->
            if (!e.isExpenseEntity && !supplierIdSet.contains(e.id)) {
                entityBalances[e.id] = e.openingBalance
            }
        }
        activeTx.forEach { tx ->
            if (tx.isExpense || supplierIdSet.contains(tx.entityId)) return@forEach
            if (FinanceEngine.isOrphanSupplierTx(tx, activeInventory)) return@forEach
            if (entityBalances.containsKey(tx.entityId)) {
                val cur = entityBalances[tx.entityId] ?: 0.0
                entityBalances[tx.entityId] = if (tx.type.equals("OUT", true)) cur - tx.amount else cur + tx.amount
            }
        }

        val q = unifiedSearch.trim().lowercase()
        activeEntities.filter { !it.isExpenseEntity }
            .filter { e ->
                q.isEmpty() ||
                    e.name.lowercase().contains(q) ||
                    e.phone.contains(q) ||
                    e.wallet.lowercase().contains(q)
            }
            .map { e ->
                val bal = if (supplierIdSet.contains(e.id)) {
                    (supplierBalances[e.id] ?: 0.0) + e.openingBalance
                } else {
                    entityBalances[e.id] ?: 0.0
                }
                val status = when {
                    bal > 0.01 -> "PAYABLE"
                    bal < -0.01 -> "RECEIVABLE"
                    else -> "SETTLED"
                }
                val contact = e.phone.ifBlank { e.wallet }.ifBlank { "No contact" }
                val photo = snapshot.photos["entity:${e.id}"]?.takeIf { !it.deleted }?.dataUri
                UnifiedEntityRowData(e, bal, status, contact, photo)
            }
            .sortedByDescending { abs(it.balance) }
    }

    val unifiedExpenseGroups = remember(activeExpenses, unifiedStartIso, unifiedSearch) {
        val q = unifiedSearch.trim().lowercase()
        val groups = linkedMapOf<String, UnifiedExpenseGroupData>()
        activeExpenses.forEach { exp ->
            if (!exp.category.equals("operating", true)) return@forEach
            val d = exp.date.take(10)
            if (unifiedStartIso.isNotEmpty() && d < unifiedStartIso) return@forEach
            if (q.isNotEmpty() && !exp.name.lowercase().contains(q)) return@forEach
            val key = "${exp.category}||${exp.name}"
            val prev = groups[key]
            groups[key] = if (prev == null) {
                UnifiedExpenseGroupData(exp.name, exp.amount, 1, exp.date)
            } else {
                prev.copy(
                    totalAmount = prev.totalAmount + exp.amount,
                    count = prev.count + 1,
                    latestDate = if (exp.date > prev.latestDate) exp.date else prev.latestDate
                )
            }
        }
        groups.values.sortedByDescending { it.latestDate }
    }

    val isEditing = editingTx != null || editingExp != null
    val amount = amountStr.toDoubleOrNull() ?: 0.0
    val availCash = remember(snapshot) { FinanceEngine.computeAvailableCashInHand(snapshot) }
    val fifoPreview = remember(txKind, selectedEntity, customName, amount, activeInventory) {
        val supplierNeedle = selectedEntity?.id?.ifBlank { selectedEntity?.name } ?: customName
        if (txKind == "OUT" && supplierNeedle.isNotBlank() && amount > 0.0) {
            FinanceEngine.previewSupplierFifoAllocation(supplierNeedle, amount, activeInventory)
        } else emptyList()
    }
    val expenseNames = remember(snapshot.settings.expenseCategories) {
        snapshot.settings.expenseCategories.filter { it.isNotBlank() }
    }

    val filteredHistoryTx = remember(activeTx, historyFilter, customName) {
        val nameNeedle = customName.trim().lowercase()
        activeTx.filter { tx ->
            val typeMatch = when (historyFilter) {
                "IN" -> tx.type.equals("IN", true)
                "OUT" -> tx.type.equals("OUT", true)
                else -> true
            }
            val nameMatch = nameNeedle.isEmpty() || tx.entityName.lowercase().contains(nameNeedle)
            typeMatch && nameMatch
        }.sortedWith(compareByDescending<PaymentTransaction> { it.date }.thenByDescending { it.timestamp })
    }

    val darkAvatarBgs = remember(accents.isDark) {
        if (accents.isDark) {
            listOf(Color(0xFF3E352B), Color(0xFF2B2848), Color(0xFF3D3824), Color(0xFF422739), Color(0xFF2C2747))
        } else {
            listOf(Color(0xFFE7DFC6), Color(0xFFDCD8EE), Color(0xFFEAE5CA), Color(0xFFECDAE4), Color(0xFFDCD6EC))
        }
    }
    val darkAvatarTexts = remember(accents.isDark) {
        if (accents.isDark) {
            listOf(Color(0xFFE8C07D), Color(0xFFA79DF2), Color(0xFFD4C97A), Color(0xFFE89DC5), Color(0xFFB39EF5))
        } else {
            listOf(Color(0xFF5A4319), Color(0xFF3A3077), Color(0xFF544B16), Color(0xFF6B284D), Color(0xFF44317D))
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TabHeading("Payments") }

        // 1. TRANSACTION MANAGER
        item {
            val q = customName.trim().lowercase()
            val entityMatchExact = selectedEntity != null
            val expenseMatchExact = !entityMatchExact && q.isNotEmpty() && expenseNames.any { it.equals(customName.trim(), true) }
            val operatingLocked = entityMatchExact
            val paymentLocked = expenseMatchExact
            val hasAttachedPhoto = !pendingPhotoUri.isNullOrBlank() && !clearExistingPhoto

            SarimCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (isEditing) "Editing Transaction" else "Transaction Manager",
                            fontFamily = PlayfairFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (isEditing) accents.cyan else MaterialTheme.colorScheme.onSurface
                        )
                        if (isEditing) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = accents.inputFill,
                                border = BorderStroke(1.dp, accents.borderSubtle)
                            ) {
                                Text(
                                    "ID: ${editingTx?.id ?: editingExp?.id ?: "—"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accents.textMuted,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Surface(
                        onClick = {
                            if (isEditing) showDiscardEdit = true else resetForm(clearName = true)
                        },
                        shape = CircleShape,
                        color = accents.inputFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.testTag(if (isEditing) "pay_cancel_edit_btn" else "pay_clear_btn")
                    ) {
                        Text(
                            if (isEditing) "Cancel Edit" else "Clear",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    DatePickerField("DATE", date, { date = it }, "pay_date", Modifier.weight(0.45f))
                    SarimPillInput(
                        value = customName,
                        onValueChange = { v ->
                            customName = v.take(80)
                            val hit = activeEntities.firstOrNull { !it.isExpenseEntity && it.name.equals(v.trim(), true) }
                            selectedEntity = hit
                            if (!isEditing) {
                                if (hit != null && txKind == "EXPENSE") txKind = "OUT"
                                else if (hit == null && expenseNames.any { it.equals(v.trim(), true) } && txKind != "EXPENSE") {
                                    txKind = "EXPENSE"
                                }
                            }
                        },
                        label = "NAME/ENTITY",
                        placeholder = "Search expenses or entities...",
                        testTag = "pay_name_input",
                        modifier = Modifier.weight(0.55f)
                    )
                }

                if (q.isNotEmpty() && !isEditing && !entityMatchExact && !expenseMatchExact) {
                    val expMatches = expenseNames.filter { it.lowercase().contains(q) }
                    val entMatches = activeEntities.filter { !it.isExpenseEntity && it.name.lowercase().contains(q) }
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = accents.cardElevated,
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.fillMaxWidth().testTag("pay_search_results")
                    ) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(
                                "EXPENSES",
                                style = MaterialTheme.typography.labelSmall,
                                color = accents.textMuted,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            if (expMatches.isEmpty()) {
                                Text(
                                    "No matching expenses — will create new",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                            expMatches.take(6).forEach { nm ->
                                val n = activeExpenses.count { it.name.equals(nm, true) }
                                val highlighted = remember(nm, q, accents.emerald) {
                                    buildAnnotatedString {
                                        val start = nm.indexOf(q, ignoreCase = true)
                                        if (start >= 0 && q.isNotEmpty()) {
                                            append(nm.substring(0, start))
                                            withStyle(SpanStyle(color = accents.emerald, fontWeight = FontWeight.ExtraBold)) {
                                                append(nm.substring(start, start + q.length))
                                            }
                                            append(nm.substring(start + q.length))
                                        } else append(nm)
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        customName = nm
                                        selectedEntity = null
                                        txKind = "EXPENSE"
                                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(highlighted, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text("$n expense records", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                }
                            }
                            Text(
                                "ENTITIES",
                                style = MaterialTheme.typography.labelSmall,
                                color = accents.textMuted,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            if (entMatches.isEmpty()) {
                                Text(
                                    "No matching entities — will create new",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accents.textMuted,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                            entMatches.take(6).forEach { ent ->
                                val n = activeTx.count { it.entityId == ent.id }
                                val highlighted = remember(ent.name, q, accents.emerald) {
                                    buildAnnotatedString {
                                        val start = ent.name.indexOf(q, ignoreCase = true)
                                        if (start >= 0 && q.isNotEmpty()) {
                                            append(ent.name.substring(0, start))
                                            withStyle(SpanStyle(color = accents.emerald, fontWeight = FontWeight.ExtraBold)) {
                                                append(ent.name.substring(start, start + q.length))
                                            }
                                            append(ent.name.substring(start + q.length))
                                        } else append(ent.name)
                                    }
                                }
                                Row(
                                    Modifier.fillMaxWidth().clickable {
                                        customName = ent.name
                                        selectedEntity = ent
                                        if (txKind == "EXPENSE") txKind = "OUT"
                                    }.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(highlighted, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text(if (n > 0) "$n transactions" else "", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    DecimalField("AMOUNT", amountStr, { amountStr = it }, "pay_amount_input", Modifier.weight(0.34f))
                    Box(modifier = Modifier.weight(0.66f)) {
                        SarimPillInput(
                            value = notes,
                            onValueChange = { notes = it.take(500) },
                            label = "DESCRIPTION (OPTIONAL)",
                            placeholder = "Add details...",
                            testTag = "pay_notes_input"
                        )
                    }
                    Surface(
                        onClick = {
                            photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        shape = CircleShape,
                        color = accents.inputFill,
                        border = BorderStroke(
                            1.dp,
                            if (hasAttachedPhoto) accents.emerald else accents.borderSubtle
                        ),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("pay_photo_btn")
                    ) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.CameraAlt,
                                contentDescription = if (hasAttachedPhoto) "Photo attached — tap to replace" else "Attach photo",
                                tint = accents.emerald,
                                modifier = Modifier.size(20.dp)
                            )
                            if (hasAttachedPhoto) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(accents.emerald)
                                )
                            }
                        }
                    }
                }

                if (hasAttachedPhoto) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DecodedDataUrlImage(
                                dataUrl = pendingPhotoUri!!,
                                contentDescription = "Attached transaction photo",
                                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Text("Photo attached", style = MaterialTheme.typography.labelMedium, color = accents.emerald)
                        }
                        IconButton(
                            onClick = {
                                pendingPhotoUri = null
                                clearExistingPhoto = true
                            },
                            modifier = Modifier.size(48.dp).testTag("pay_photo_clear_btn")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove attached photo", tint = accents.danger)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "TRANSACTION TYPE",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
                    color = accents.textMuted
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple("EXPENSE", "Operating Expense", operatingLocked),
                        Triple("IN", "Payment IN (Cash In +)", paymentLocked),
                        Triple("OUT", "Payment OUT (Cash Out \u2212)", paymentLocked)
                    ).forEach { (key, title, locked) ->
                        val selected = txKind == key
                        val canTap = !isEditing && !locked
                        Surface(
                            onClick = { if (canTap) txKind = key },
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) accents.softPillFill else accents.inputFill,
                            border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 40.dp)
                                .alpha(if (locked || (isEditing && !selected)) 0.4f else 1f)
                                .testTag("pay_type_${key.lowercase()}")
                        ) {
                            Box(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    title,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    lineHeight = 12.sp,
                                    color = if (selected) accents.emerald else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (!hidesHistory && fifoPreview.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Supplier FIFO Payable Settlement", style = MaterialTheme.typography.titleSmall, color = accents.cyan)
                    fifoPreview.forEach { lot ->
                        Text(
                            "${lot.date} • ${lot.materialName}: settles ${InputGuards.formatMoney(lot.appliedPayment, currency, masked)} (left ${InputGuards.formatMoney(lot.afterPayable, currency, masked)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = accents.textMuted
                        )
                    }
                }

                if (!isEditing && txKind != "EXPENSE") {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { addToLedger = !addToLedger },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = addToLedger,
                            onCheckedChange = { addToLedger = it },
                            modifier = Modifier.testTag("pay_ledger_switch")
                        )
                        Text(
                            "ADD TO DAILY LEDGER",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                SarimGradientButton(
                    text = if (isEditing) "Update Transaction" else "Save Transaction",
                    onClick = {
                        val nm = customName.trim()
                        val isExp = txKind == "EXPENSE"
                        val issue = when {
                            nm.isEmpty() -> "Please enter name/entity"
                            else -> InputGuards.dateIssue(date, todayIso).ifEmpty { InputGuards.nameIssue(nm, "Name") }
                        }
                        if (issue.isNotEmpty()) {
                            confirmReq = blockingAlert("Warning", issue); return@SarimGradientButton
                        }
                        if (amount <= 0.0) {
                            confirmReq = blockingAlert("Warning", "Please enter valid amount"); return@SarimGradientButton
                        }
                        if (isEditing) {
                            showEditDiff = true; return@SarimGradientButton
                        }
                        if (!isExp && selectedEntity == null && expenseNames.any { it.equals(nm, true) }) {
                            confirmReq = blockingAlert(
                                "Something went wrong",
                                "\"$nm\" is an operating expense category, not an entity. Switch to Operating Expense mode or use a different name.",
                                DialogTone.Error
                            ); return@SarimGradientButton
                        }
                        if (txKind == "OUT" || isExp) {
                            val offset = if (txKind == "OUT") fifoPreview.sumOf { it.appliedPayment } else 0.0
                            val required = amount - offset
                            if (availCash + 0.005 < required) {
                                confirmReq = blockingAlert(
                                    "Low cash in hand",
                                    "Insufficient cash in hand. Available: ${InputGuards.formatMoney(maxOf(0.0, availCash), currency, masked)} — Required: ${InputGuards.formatMoney(required, currency, masked)}",
                                    DialogTone.Error
                                ); return@SarimGradientButton
                            }
                        }
                        val details = buildList {
                            add("Expense" to nm)
                            add("Amount" to InputGuards.formatMoney(amount, currency, masked))
                            add("Date" to DateUtils.formatPretty(date))
                            if (notes.isNotBlank()) add("Note" to notes.trim())
                        }
                        confirmReq = saveConfirmRequest(
                            label = "Expense",
                            details = details,
                            gated = session.mode != "admin",
                            tag = "pay_confirm_save_btn"
                        ) {
                            vm.submitPaymentOrExpense(
                                date = date,
                                kind = txKind,
                                entity = selectedEntity,
                                name = nm,
                                category = if (isExp) nm else (selectedEntity?.type ?: "party"),
                                channel = defaultChannel,
                                amount = amount,
                                addToLedger = addToLedger && !isExp,
                                notes = notes,
                                photoDataUri = pendingPhotoUri
                            )
                            resetForm(clearName = true)
                        }
                    },
                    testTag = "pay_save_btn"
                )
            }
        }

        if (!hidesHistory) {
            // 2. ECONOMIC HEALTH DASHBOARD
            item {
                val cashColor = when {
                    health.cashInHand < 0 -> accents.danger
                    health.cashInHand < 10000 -> accents.gold
                    else -> accents.emerald
                }
                val wcColor = when {
                    health.netWorkingCapital < 0 -> accents.danger
                    health.netWorkingCapital < 50000 -> accents.gold
                    else -> accents.emerald
                }
                val crColor = when {
                    health.currentRatio == null -> accents.textMuted
                    health.currentRatio < 1.0 -> accents.danger
                    health.currentRatio < 2.0 -> accents.gold
                    else -> accents.emerald
                }
                val navyRowBg = if (accents.isDark) Color(0xFF07111E) else accents.cyan.copy(alpha = 0.08f)
                val dangerRowBg = if (accents.isDark) Color(0xFF1A0809) else accents.danger.copy(alpha = 0.08f)
                val dangerStrongBg = if (accents.isDark) Color(0xFF210B0C) else accents.danger.copy(alpha = 0.13f)
                val emeraldRowBg = if (accents.isDark) Color(0xFF071A11) else accents.softPillFill

                SarimCard {
                    Text(
                        "ECONOMIC HEALTH DASHBOARD",
                        fontFamily = CinzelFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        letterSpacing = 0.6.sp,
                        color = accents.emerald,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        InputGuards.formatMoney(health.cashInHand, currency, masked),
                        fontFamily = JakartaFont,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        color = cashColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().testTag("econ_net_cash_value")
                    )
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = navyRowBg,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Operating Cash Flow: ",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                                color = accents.emerald
                            )
                            Text(
                                InputGuards.formatMoney(health.operatingCashFlow, currency, masked),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = accents.emerald
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    SectionSubHeader("CASH IN HAND DETAILS", accentColor = accents.emerald)
                    BreakdownRow("Net Sales Cash (Direct Customers):", health.directSalesCash, currency, masked)
                    BreakdownRow("Net Production Cash:", health.productionCash, currency, masked)
                    BreakdownRow("Representative Collections:", health.repCollectionsCash, currency, masked)
                    BreakdownRow("External Payments Received:", health.paymentsIn, currency, masked)
                    BreakdownRow("External Payments Made:", health.paymentsOut, currency, masked, accents.danger, rowBg = dangerRowBg)
                    BreakdownRow("Operating Expenses:", health.operatingExpenses, currency, masked, accents.danger, rowBg = dangerRowBg)
                    Spacer(Modifier.height(4.dp))
                    HalfRingTotalRow("NET CASH IN HAND:", health.cashInHand, currency, masked, accents.emerald)

                    Spacer(Modifier.height(16.dp))
                    SectionSubHeader("CURRENT ASSETS", accentColor = accents.emerald)
                    BreakdownRow("Cash in Hand (Above):", health.cashInHand, currency, masked)
                    BreakdownRow("Raw Materials Inventory:", health.rawMaterialValue, currency, masked, accents.emerald, boldValue = true)
                    BreakdownRow("Formula Units Inventory:", health.formulaUnitsValue, currency, masked, accents.purple, boldValue = true)

                    Spacer(Modifier.height(14.dp))
                    SectionSubHeader("ACCOUNTS RECEIVABLE", accentColor = accents.emerald)
                    BreakdownRow("Direct Customer Credits:", health.salesReceivables, currency, masked)
                    BreakdownRow("Representative Credits:", health.calculatorReceivables, currency, masked)
                    BreakdownRow("TOTAL RECEIVABLES:", health.totalReceivables, currency, masked, accents.emerald, bold = true)
                    Spacer(Modifier.height(6.dp))
                    HalfRingTotalRow("TOTAL CURRENT ASSETS:", health.totalAssets, currency, masked, accents.emerald)

                    Spacer(Modifier.height(16.dp))
                    SectionSubHeader("CURRENT LIABILITIES", accentColor = accents.danger)
                    Text(
                        "ACCOUNTS PAYABLE:",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accents.danger
                    )
                    Spacer(Modifier.height(2.dp))
                    BreakdownRow("  Supplier Payables (Raw Material Invoices):", health.supplierPayables, currency, masked, accents.danger, boldValue = true)
                    BreakdownRow("  Entity Payables (Payment Entities):", health.entityPayables, currency, masked, accents.danger, boldValue = true)
                    BreakdownRow("TOTAL ACCOUNTS PAYABLE:", health.supplierPayables + health.entityPayables, currency, masked, accents.danger, bold = true, rowBg = navyRowBg)
                    Spacer(Modifier.height(4.dp))
                    BreakdownRow("TOTAL CURRENT LIABILITIES:", health.totalLiabilities, currency, masked, accents.danger, bold = true, rowBg = dangerStrongBg)

                    Spacer(Modifier.height(6.dp))
                    BreakdownRow("Net Working Capital:", health.netWorkingCapital, currency, masked, wcColor, labelColor = accents.emerald, bold = true, rowBg = emeraldRowBg)
                    Spacer(Modifier.height(4.dp))
                    BreakdownRow("Total Enterprise Value:", health.enterpriseValue, currency, masked, accents.emerald, labelColor = accents.cyan, bold = true, rowBg = navyRowBg)

                    Spacer(Modifier.height(14.dp))
                    SectionSubHeader("FINANCIAL RATIOS", accentColor = null)
                    RatioRow("Current Ratio:", health.currentRatio, crColor)
                    RatioRow("Quick Ratio:", health.quickRatio, MaterialTheme.colorScheme.onSurface)
                    RatioRow("Cash Ratio:", health.cashRatio, MaterialTheme.colorScheme.onSurface)
                }
            }

            // 3. CASH IN HAND TRACKER
            item {
                val creditsBoxBg = if (accents.isDark) Color(0xFF151005) else accents.gold.copy(alpha = 0.08f)
                SarimCard {
                    Text(
                        "CASH IN HAND TRACKER",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JakartaFont,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "day" to "Daily",
                            "week" to "Weekly",
                            "month" to "Monthly",
                            "year" to "Yearly",
                            "all" to "All Times"
                        ).forEach { (key, label) ->
                            val sel = cashTrackerMode == key
                            Surface(
                                onClick = { cashTrackerMode = key },
                                shape = RoundedCornerShape(999.dp),
                                color = if (sel) accents.softPillFill else accents.inputFill,
                                border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 36.dp).testTag("cash_tracker_mode_$key")
                            ) {
                                Box(Modifier.padding(vertical = 8.dp, horizontal = 2.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (sel) accents.emerald else accents.textMuted,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = accents.inputFill,
                        border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "NET CASH FLOW",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = accents.emerald
                            )
                            Spacer(Modifier.height(8.dp))
                            BreakdownRow("Production Value:", tracker.productionValue, currency, masked, accents.emerald, boldValue = true)
                            BreakdownRow("Sales Tab Cash:", tracker.salesTabCash, currency, masked, accents.emerald, boldValue = true)
                            BreakdownRow("Calculator Cash:", tracker.calculatorCash, currency, masked, accents.emerald, boldValue = true)
                            BreakdownRow("Payments In:", tracker.paymentsIn, currency, masked, accents.emerald, boldValue = true)
                            BreakdownRow("Payments Out:", tracker.paymentsOut, currency, masked, accents.danger, boldValue = true)
                            BreakdownRow("Expenses:", tracker.expenses, currency, masked, accents.danger, boldValue = true)
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = accents.borderSubtle)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "NET CASH:",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val netCashTone = if (tracker.netCash >= 0) accents.emerald else accents.danger
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (tracker.netCash >= 0) accents.softPillFill else accents.danger.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        InputGuards.formatMoney(tracker.netCash, currency, masked),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = netCashTone,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = creditsBoxBg,
                        border = BorderStroke(1.dp, accents.gold.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "TOTAL OUTSTANDING CREDITS",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = accents.gold
                            )
                            Spacer(Modifier.height(8.dp))
                            BreakdownRow("Sales Tab Credits:", tracker.salesTabCredits, currency, masked, accents.gold, boldValue = true)
                            BreakdownRow("Calculator Credits:", tracker.calculatorCredits, currency, masked, accents.gold, boldValue = true)
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = accents.gold.copy(alpha = 0.25f))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "TOTAL CREDITS:",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = accents.gold.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        InputGuards.formatMoney(tracker.totalCredits, currency, masked),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = accents.gold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(accents.emerald))
                                Text("Credits awaiting collection", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            }
                        }
                    }
                }
            }

            // 4. UNIFIED RECORDS
            item {
                val periodOptions = listOf(
                    "today" to "Today",
                    "week" to "This Week",
                    "month" to "This Month",
                    "all" to "All Time"
                )
                val viewOptions = listOf(
                    "entities" to "Entities & Balances",
                    "transactions" to "Expenses"
                )
                val periodLabel = periodOptions.firstOrNull { it.first == unifiedPeriod }?.second ?: "This Month"
                val viewLabel = viewOptions.firstOrNull { it.first == unifiedViewMode }?.second ?: "Entities & Balances"

                SarimCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "UNIFIED RECORDS",
                            fontFamily = CinzelFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accents.inputFill,
                            border = BorderStroke(1.dp, accents.borderSubtle),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (unifiedSearch.isEmpty()) {
                                    Text(
                                        "Search...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accents.textMuted.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = unifiedSearch,
                                    onValueChange = { unifiedSearch = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontFamily = JakartaFont,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(accents.emerald),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("unified_search_input")
                                )
                            }
                        }
                        Surface(
                            onClick = {
                                val rows = if (unifiedViewMode == "entities") {
                                    unifiedEntityRows.map { row ->
                                        StatementRow(
                                            date = row.entity.type.uppercase(),
                                            description = "${row.entity.name} (${row.contactInfo})",
                                            debit = if (row.balance > 0.01) InputGuards.formatMoney(row.balance, currency, masked) else "—",
                                            credit = if (row.balance < -0.01) InputGuards.formatMoney(abs(row.balance), currency, masked) else "—",
                                            balance = row.statusLabel
                                        )
                                    }
                                } else {
                                    unifiedExpenseGroups.map { grp ->
                                        StatementRow(
                                            date = DateUtils.formatPretty(grp.latestDate),
                                            description = "${grp.name} (${grp.count} tx)",
                                            debit = InputGuards.formatMoney(grp.totalAmount, currency, masked),
                                            credit = "—",
                                            balance = "EXPENSE"
                                        )
                                    }
                                }
                                if (rows.isEmpty()) {
                                    vm.showToast("No records to export", ToastKind.WARNING)
                                } else {
                                    val netPos = unifiedSummary.totalReceivables - (unifiedSummary.supplierPayables + unifiedSummary.entityPayables)
                                    val footer = if (unifiedViewMode == "entities") {
                                        "Net Balance: ${InputGuards.formatMoney(abs(netPos), currency, masked)}"
                                    } else {
                                        val totExp = unifiedExpenseGroups.sumOf { it.totalAmount }
                                        "Total Operating Expenses: ${InputGuards.formatMoney(totExp, currency, masked)}"
                                    }
                                    PdfExporter.exportAndShareStatement(
                                        context = context,
                                        title = if (unifiedViewMode == "entities") "Payment Entities — Balances" else "Operating Expenses",
                                        subtitle = "Period: $periodLabel",
                                        rows = rows,
                                        summaryFooter = footer
                                    ).onFailure {
                                        vm.showToast(it.message ?: "PDF export failed", ToastKind.ERROR)
                                    }
                                }
                            },
                            shape = CircleShape,
                            color = accents.softPillFill,
                            border = BorderStroke(1.dp, accents.emerald),
                            modifier = Modifier.size(40.dp).testTag("unified_export_pdf_btn")
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Download PDF", tint = accents.emerald, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            PickerField(
                                label = "",
                                selectedLabel = viewLabel,
                                items = viewOptions,
                                itemLabel = { it.second },
                                onSelect = { unifiedViewMode = it.first },
                                testTag = "unified_view_picker"
                            )
                        }
                        Box(Modifier.weight(1f)) {
                            PickerField(
                                label = "",
                                selectedLabel = periodLabel,
                                items = periodOptions,
                                itemLabel = { it.second },
                                onSelect = { unifiedPeriod = it.first },
                                testTag = "unified_period_picker"
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = onOpenAddEntity,
                            shape = RoundedCornerShape(999.dp),
                            color = accents.softPillFill,
                            border = BorderStroke(1.dp, accents.emerald),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp).testTag("pay_add_entity_btn")
                        ) {
                            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Entity", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                            }
                        }
                        Surface(
                            onClick = onOpenPaymentTransfer,
                            shape = RoundedCornerShape(999.dp),
                            color = accents.inputFill,
                            border = BorderStroke(1.dp, accents.borderSubtle),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp).testTag("pay_transfer_entity_btn")
                        ) {
                            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Transfer", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = accents.inputFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                            if (unifiedViewMode == "entities") {
                                if (unifiedEntityRows.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "No records match your filters",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "Change the period or search to see more.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accents.textMuted
                                        )
                                    }
                                } else {
                                    unifiedEntityRows.forEachIndexed { idx, row ->
                                        val balColor = when (row.statusLabel) {
                                            "PAYABLE" -> accents.danger
                                            "RECEIVABLE" -> accents.emerald
                                            else -> accents.emerald
                                        }
                                        val initials = row.entity.name.split(" ").filter { it.isNotBlank() }.take(2)
                                            .joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "E" }
                                        val colorIdx = abs(row.entity.name.hashCode()) % darkAvatarBgs.size
                                        val bgTint = darkAvatarBgs[colorIdx]
                                        val fgTint = darkAvatarTexts[colorIdx]
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { onOpenEntityDetail(row.entity.name) }
                                                .padding(vertical = 9.dp, horizontal = 4.dp)
                                                .testTag("entity_card_${row.entity.id}"),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (!row.photoDataUri.isNullOrBlank()) {
                                                DecodedDataUrlImage(
                                                    dataUrl = row.photoDataUri,
                                                    contentDescription = "${row.entity.name} photo",
                                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(bgTint),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(initials, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = fgTint)
                                                }
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(row.entity.name.uppercase(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    row.contactInfo,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (row.entity.phone.isNotBlank()) accents.emerald else accents.textMuted,
                                                    textDecoration = if (row.entity.phone.isNotBlank()) TextDecoration.Underline else TextDecoration.None,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                InputGuards.formatMoney(abs(row.balance), currency, masked),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = balColor
                                            )
                                        }
                                        if (idx < unifiedEntityRows.lastIndex) {
                                            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.55f))
                                        }
                                    }
                                }
                            } else {
                                if (unifiedExpenseGroups.isEmpty()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "No records match your filters",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "Change the period or search to see more.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accents.textMuted
                                        )
                                    }
                                } else {
                                    unifiedExpenseGroups.forEachIndexed { idx, grp ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { onOpenExpenseDetail(grp.name) }
                                                .padding(vertical = 9.dp, horizontal = 4.dp)
                                                .testTag("unified_exp_${grp.name}"),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(grp.name.uppercase(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                                Text(
                                                    buildString {
                                                        append("Operating")
                                                        if (grp.count > 1) append(" • ${grp.count} transactions")
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = accents.textMuted
                                                )
                                            }
                                            Text(
                                                InputGuards.formatMoney(grp.totalAmount, currency, masked),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = accents.gold
                                            )
                                        }
                                        if (idx < unifiedExpenseGroups.lastIndex) {
                                            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.55f))
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = accents.borderSubtle)
                            val totalPay = unifiedSummary.supplierPayables + unifiedSummary.entityPayables
                            val netBalance = unifiedSummary.totalReceivables - totalPay
                            val footerLabel = if (unifiedViewMode == "entities") "Net Balance:" else "Net Total:"
                            val footerAmount = if (unifiedViewMode == "entities") abs(netBalance) else -unifiedExpenseGroups.sumOf { it.totalAmount }
                            val footerColor = if (unifiedViewMode == "entities") {
                                if (netBalance >= 0) MaterialTheme.colorScheme.onSurface else accents.danger
                            } else {
                                if (footerAmount >= 0) accents.emerald else accents.danger
                            }
                            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text(footerLabel, style = MaterialTheme.typography.labelMedium, color = accents.textMuted)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    InputGuards.formatMoney(footerAmount, currency, masked),
                                    fontFamily = JakartaFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = footerColor
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SarimHalfRingKpiBox("Total Receivable", InputGuards.formatMoney(unifiedSummary.totalReceivables, currency, masked), accents.emerald, Modifier.weight(1f))
                        SarimHalfRingKpiBox("Expenses", InputGuards.formatMoney(unifiedSummary.totalExpenses, currency, masked), accents.gold, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SarimHalfRingKpiBox("Supplies Payable", InputGuards.formatMoney(unifiedSummary.supplierPayables, currency, masked), accents.danger, Modifier.weight(1f))
                        SarimHalfRingKpiBox("Entity Payable", InputGuards.formatMoney(unifiedSummary.entityPayables, currency, masked), accents.purple, Modifier.weight(1f))
                    }
                }
            }

            // 5. PAYMENT HISTORY
            item {
                Text(
                    "PAYMENT HISTORY",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JakartaFont,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL" to "All", "IN" to "In", "OUT" to "Out").forEach { (k, label) ->
                        val sel = historyFilter == k
                        Surface(
                            onClick = { historyFilter = k },
                            shape = RoundedCornerShape(999.dp),
                            color = if (sel) accents.softPillFill else accents.inputFill,
                            border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                            modifier = Modifier.weight(1f).testTag("pay_hist_filter_$k")
                        ) {
                            Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(label, style = MaterialTheme.typography.labelMedium, color = if (sel) accents.emerald else accents.textMuted)
                            }
                        }
                    }
                }
            }
            if (filteredHistoryTx.isEmpty()) {
                item {
                    EmptyStateCard(
                        if (customName.isNotBlank()) "No matching payments" else "No payment transactions found.",
                        if (customName.isNotBlank()) "Clear the name to see all." else "Recorded payments and operating expenses appear here."
                    )
                }
            } else {
                item {
                    SarimYearMonthDayTree(
                        items = filteredHistoryTx,
                        dateOf = { it.date },
                        yearSubLabel = { list ->
                            val tIn = list.filter { it.type.equals("IN", true) }.sumOf { it.amount }
                            val tOut = list.filter { !it.type.equals("IN", true) }.sumOf { it.amount }
                            "In ${InputGuards.formatMoney(tIn, currency, masked)} · Out ${InputGuards.formatMoney(tOut, currency, masked)}"
                        },
                        monthSubLabel = { list ->
                            val tIn = list.filter { it.type.equals("IN", true) }.sumOf { it.amount }
                            val tOut = list.filter { !it.type.equals("IN", true) }.sumOf { it.amount }
                            "In ${InputGuards.formatMoney(tIn, currency, masked)} · Out ${InputGuards.formatMoney(tOut, currency, masked)}"
                        },
                        summaryLabel = { list ->
                            "${list.size} ${if (list.size == 1) "entry" else "entries"}"
                        },
                        daySummaryLabel = { list ->
                            val tIn = list.filter { it.type.equals("IN", true) }.sumOf { it.amount }
                            val tOut = list.filter { !it.type.equals("IN", true) }.sumOf { it.amount }
                            "In: ${InputGuards.formatMoney(tIn, currency, masked)} | Out: ${InputGuards.formatMoney(tOut, currency, masked)}"
                        },
                        itemRow = { tx ->
                            val isIn = tx.type.equals("IN", true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedTx = tx }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .testTag("pay_row_${tx.id}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (isIn) accents.emerald else accents.danger))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(tx.entityName.uppercase(), style = MaterialTheme.typography.titleSmall)
                                            EditedBadge(tx.isEdited)
                                            if (tx.isSettled) {
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = accents.softPillFill
                                                ) {
                                                    Text(
                                                        "Settled",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = accents.emerald,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        val sub = buildString {
                                            append(if (tx.isExpense) "Expense" else tx.type)
                                            if (tx.description.isNotBlank()) append(" • ${tx.description}")
                                        }
                                        Text(sub, style = MaterialTheme.typography.labelSmall, color = accents.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Text(
                                    (if (isIn) "+ " else "- ") + InputGuards.formatMoney(tx.amount, currency, masked),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isIn) accents.emerald else accents.danger
                                )
                            }
                        }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    selectedTx?.let { tx ->
        val photoKey = "expense:${tx.expenseId.ifBlank { tx.id }}"
        val attached = snapshot.photos[photoKey]?.takeIf { !it.deleted }?.dataUri
        PaymentTxDetailSheet(
            tx = tx,
            attachedPhotoDataUri = attached,
            currency = currency,
            masked = masked,
            isAdmin = session.mode == "admin",
            vm = vm,
            onOpenExpenseDetail = if (tx.isExpense) onOpenExpenseDetail else null,
            onEditEntry = { startEditTx(it) },
            onCopyToForm = {
                txKind = if (it.isExpense) "EXPENSE" else if (it.type.equals("IN", true)) "IN" else "OUT"
                date = it.date
                customName = it.entityName
                selectedEntity = activeEntities.firstOrNull { e -> e.id == it.entityId || e.name.equals(it.entityName, true) }
                amountStr = it.amount.toString()
                notes = it.description
            },
            onDismiss = { selectedTx = null }
        )
    }

    if (showEditDiff && (editingTx != null || editingExp != null)) {
        val amt = amountStr.toDoubleOrNull() ?: 0.0
        val rows = editingTx?.let { orig ->
            listOf(
                Triple("Date", orig.date, date),
                Triple("Account", orig.entityName, selectedEntity?.name ?: customName.trim()),
                Triple("Amount", InputGuards.formatMoney(orig.amount, currency, masked), InputGuards.formatMoney(amt, currency, masked)),
                Triple("Notes", orig.description, notes.trim())
            )
        } ?: editingExp!!.let { orig ->
            listOf(
                Triple("Date", orig.date, date),
                Triple("Title", orig.name, customName.trim()),
                Triple("Category", orig.category, expCategory),
                Triple("Amount", InputGuards.formatMoney(orig.amount, currency, masked), InputGuards.formatMoney(amt, currency, masked)),
                Triple("Notes", orig.notes.ifBlank { orig.description }, notes.trim())
            )
        }
        EditDiffConfirmDialog(
            title = if (editingExp != null) "Confirm Expense Edit" else "Confirm Payment Edit",
            rows = rows,
            onConfirm = {
                showEditDiff = false
                editingTx?.let { orig ->
                    vm.editPaymentTransaction(
                        original = orig,
                        date = date,
                        entity = selectedEntity,
                        customName = customName,
                        paymentMethod = orig.paymentMethod.ifBlank { defaultChannel },
                        amount = amt,
                        description = notes,
                        photoDataUri = if (!clearExistingPhoto) pendingPhotoUri else null,
                        clearPhoto = clearExistingPhoto
                    ) { resetForm(clearName = true) }
                }
                editingExp?.let { orig ->
                    vm.editExpenseRecord(
                        original = orig,
                        date = date,
                        name = customName,
                        category = expCategory,
                        paymentMethod = orig.paymentMethod.ifBlank { defaultChannel },
                        amount = amt,
                        notes = notes,
                        photoDataUri = if (!clearExistingPhoto) pendingPhotoUri else null,
                        clearPhoto = clearExistingPhoto
                    ) { resetForm(clearName = true) }
                }
            },
            onDismiss = { showEditDiff = false }
        )
    }

    if (showDiscardEdit) {
        DiscardEditDialog(
            onDiscard = {
                showDiscardEdit = false
                resetForm(clearName = true)
            },
            onDismiss = { showDiscardEdit = false }
        )
    }
}

private data class QuadSummary(
    val totalReceivables: Double,
    val totalExpenses: Double,
    val supplierPayables: Double,
    val entityPayables: Double
)

@Composable
private fun SectionSubHeader(title: String, accentColor: Color? = null) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
            color = accentColor ?: MaterialTheme.colorScheme.onSurface
        )
        if (accentColor != null) {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(thickness = 1.5.dp, color = accentColor)
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    amount: Double,
    currency: String,
    masked: Boolean,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    labelColor: Color? = null,
    bold: Boolean = false,
    boldValue: Boolean = false,
    rowBg: Color? = null
) {
    val accents = LocalSarimAccents.current
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (rowBg != null) 10.dp else 0.dp, vertical = if (rowBg != null) 8.dp else 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = if (bold) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall,
                color = labelColor ?: if (bold) MaterialTheme.colorScheme.onSurface else accents.textMuted,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                InputGuards.formatMoney(amount, currency, masked),
                style = if (bold || boldValue) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.labelMedium,
                color = valueColor
            )
        }
    }
    if (rowBg != null) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = rowBg,
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
        ) { content() }
    } else {
        content()
    }
}

@Composable
private fun HalfRingTotalRow(
    label: String,
    amount: Double,
    currency: String,
    masked: Boolean,
    ringColor: Color
) {
    val accents = LocalSarimAccents.current
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = if (accents.isDark) Color(0xFF020403) else accents.inputFill,
        border = BorderStroke(1.dp, accents.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val strokePx = 2.5.dp.toPx()
                val arcDiameter = size.height
                drawArc(
                    color = ringColor,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(strokePx / 2f, strokePx / 2f),
                    size = Size(arcDiameter - strokePx, size.height - strokePx),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    InputGuards.formatMoney(amount, currency, masked),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = ringColor
                )
            }
        }
    }
}

@Composable
private fun RatioRow(label: String, value: Double?, color: Color) {
    val accents = LocalSarimAccents.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        Text(
            value?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}
