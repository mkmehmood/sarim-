package com.gullzubair.sarim.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.RepSalesAnalyticsEngine
import com.gullzubair.sarim.domain.SalesAnalyticsEngine
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import kotlin.math.abs
import kotlin.math.max

enum class RepEntryMode {
    SALE,
    COLLECTION
}

data class RepSaveConfirmModel(
    val title: String,
    val lines: List<Pair<String, String>>,
    val warning: String? = null,
    val notes: List<String> = emptyList(),
    val confirmLabel: String = "Confirm & Save"
)

@Composable
fun RepEntryCard(
    isEditing: Boolean,
    editingSpecialType: String?, // e.g. "REP_RETURN" or "MONEY_OUT" when editing legacy rows
    isOwnRepOnly: Boolean,
    activeRepProfile: String,
    masked: Boolean,
    currency: String,
    entryMode: RepEntryMode,
    onSelectEntryMode: (RepEntryMode) -> Unit,
    dateIso: String,
    onDateChange: (String) -> Unit,
    customerName: String,
    onCustomerNameChange: (String) -> Unit,
    customerSuggestions: List<RepSalesAnalyticsEngine.RepClientDirectoryItem>,
    isRepNameCollision: Boolean,
    showNewCustomerPhoneField: Boolean,
    customerPhone: String,
    onCustomerPhoneChange: (String) -> Unit,
    existingDebt: Double,
    customerTotalQty: Double,
    onOpenCustomerLedger: (String) -> Unit,
    onClearCustomer: () -> Unit,
    activeStores: List<StoreConfig>,
    selectedStore: StoreConfig?,
    onSelectStore: (StoreConfig) -> Unit,
    paymentType: String, // "CREDIT" | "CASH"
    onSelectPaymentType: (String) -> Unit,
    qtyStr: String,
    onQtyChange: (String) -> Unit,
    rateStr: String,
    onRateChange: (String) -> Unit,
    previewTotalValue: Double,
    previewUnitCost: Double,
    previewProfit: Double,
    collectedStr: String,
    onCollectedChange: (String) -> Unit,
    expiredStr: String,
    onExpiredChange: (String) -> Unit,
    goodsCreditRate: Double,
    goodsCreditPlan: SalesAnalyticsEngine.GoodsCreditPlan,
    gpsStatus: String,
    gpsConfirmedCluster: Boolean,
    onCaptureGps: () -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onSaveClick: () -> Unit
) {
    val accents = LocalSarimAccents.current
    var showSuggestions by remember { mutableStateOf(false) }

    val cardTitle = when {
        isEditing && editingSpecialType == "REP_RETURN" -> "Edit Rep Return"
        isEditing && editingSpecialType == "MONEY_OUT" -> "Edit Money Out"
        isEditing && entryMode == RepEntryMode.COLLECTION -> "Edit Collection"
        isEditing -> "Edit Sale"
        entryMode == RepEntryMode.COLLECTION -> "Record Collection"
        else -> "New Transaction"
    }

    val collected = collectedStr.toDoubleOrNull() ?: 0.0
    val expired = expiredStr.toDoubleOrNull() ?: 0.0
    val qty = qtyStr.toDoubleOrNull() ?: 0.0
    val rate = rateStr.toDoubleOrNull() ?: 0.0
    val hasLoss = entryMode == RepEntryMode.SALE && editingSpecialType == null &&
        qty > 0.0 && rate > 0.0 && previewUnitCost > 0.0 && previewProfit < -0.01

    SarimCard(contentPadding = PaddingValues(16.dp)) {
        // Header + Mode Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = cardTitle,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accents.emerald,
                modifier = Modifier.weight(1f)
            )
            if (isEditing) {
                Surface(
                    onClick = onCancelEdit,
                    shape = RoundedCornerShape(14.dp),
                    color = accents.danger.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.45f)),
                    modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("rep_cancel_edit_btn")
                ) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text("Cancel Edit", style = MaterialTheme.typography.labelMedium, color = accents.danger)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        RepEntryMode.SALE to "Sale",
                        RepEntryMode.COLLECTION to "Collection"
                    ).forEach { (mode, label) ->
                        val selected = entryMode == mode
                        Surface(
                            onClick = { onSelectEntryMode(mode) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) accents.softPillFill else accents.inputFill,
                            border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 40.dp)
                                .testTag("rep_mode_${mode.name.lowercase()}")
                        ) {
                            Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (selected) accents.emerald else accents.textMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = accents.softPillFill,
            border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth().testTag("rep_active_profile_badge")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isOwnRepOnly) "REP PROFILE (LOCKED)" else "ACTIVE REP PROFILE",
                    style = MaterialTheme.typography.labelSmall,
                    color = accents.textMuted
                )
                Text(
                    text = activeRepProfile,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = accents.emerald
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        DatePickerField("Date", dateIso, onDateChange, "rep_date")

        Spacer(Modifier.height(10.dp))
        SarimPillInput(
            value = customerName,
            onValueChange = {
                onCustomerNameChange(it.take(80))
                showSuggestions = it.isNotBlank()
            },
            label = "CUSTOMER / SHOP",
            placeholder = "Type or select customer...",
            trailingIcon = if (customerName.isNotBlank()) {
                {
                    Surface(
                        onClick = {
                            onClearCustomer()
                            showSuggestions = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent,
                        modifier = Modifier.size(36.dp).testTag("rep_clear_cust_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = "Clear customer filter", tint = accents.textMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            } else null,
            testTag = "rep_customer_input"
        )

        // Autocomplete suggestions
        val topSuggestions = remember(customerSuggestions, customerName) {
            val clean = customerName.trim()
            if (clean.isEmpty()) emptyList()
            else customerSuggestions.filter {
                !it.name.equals(clean, ignoreCase = true)
            }.take(5)
        }
        AnimatedVisibility(visible = showSuggestions && topSuggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accents.inputFill,
                border = BorderStroke(1.dp, accents.borderSubtle),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    topSuggestions.forEachIndexed { idx, item ->
                        if (idx > 0) HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCustomerNameChange(item.name)
                                    showSuggestions = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val sub = listOfNotNull(
                                    item.phone.takeIf { it.isNotBlank() },
                                    item.ledgerPage.takeIf { it.isNotBlank() }?.let { "Page $it" }
                                ).joinToString(" • ")
                                if (sub.isNotBlank()) {
                                    Text(sub, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                }
                            }
                            Text(
                                text = if (abs(item.debt) < 0.01) "Settled" else InputGuards.formatMoney(item.debt, currency, masked),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (item.debt > 0.01) accents.gold else accents.emerald
                            )
                        }
                    }
                }
            }
        }

        if (isRepNameCollision) {
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accents.danger.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Customer name cannot match a rep profile name.",
                    style = MaterialTheme.typography.labelSmall,
                    color = accents.danger,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                )
            }
        }

        if (showNewCustomerPhoneField) {
            Spacer(Modifier.height(8.dp))
            SarimPillInput(
                value = customerPhone,
                onValueChange = { onCustomerPhoneChange(it.take(32)) },
                label = "NEW CUSTOMER MOBILE (OPTIONAL)",
                placeholder = "0300-0000000",
                leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                testTag = "rep_new_phone_input"
            )
        }

        // Current Debt & Customer Quick Card
        if (customerName.trim().isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accents.inputFill,
                border = BorderStroke(1.dp, if (existingDebt > 0.01) accents.gold.copy(alpha = 0.4f) else accents.emerald.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("CURRENT DEBT", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                        Text(
                            text = if (abs(existingDebt) < 0.01) "Settled (${InputGuards.formatMoney(0.0, currency, masked)})"
                            else InputGuards.formatMoney(existingDebt, currency, masked),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (existingDebt > 0.01) accents.gold else accents.emerald
                        )
                        if (customerTotalQty > 0.0) {
                            Text(
                                text = "Total supplied: ${InputGuards.formatKg(customerTotalQty)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = accents.textMuted
                            )
                        }
                    }
                    Surface(
                        onClick = { onOpenCustomerLedger(customerName.trim()) },
                        shape = RoundedCornerShape(12.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.4f)),
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("rep_open_cust_ledger_btn")
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("Ledger", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (entryMode == RepEntryMode.SALE && editingSpecialType == null) {
            // Payment Type: Credit (default in rep mode) vs Cash
            Text("PAYMENT TYPE", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
            Spacer(Modifier.height(5.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("CREDIT" to "Credit", "CASH" to "Cash").forEach { (key, label) ->
                    val selected = paymentType == key
                    val activeColor = if (key == "CREDIT") accents.gold else accents.emerald
                    Surface(
                        onClick = { onSelectPaymentType(key) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) activeColor.copy(alpha = 0.14f) else accents.inputFill,
                        border = BorderStroke(1.dp, if (selected) activeColor else accents.borderSubtle),
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp).testTag("rep_pay_$key")
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (selected) activeColor else accents.textMuted
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            PickerField(
                label = "Store",
                selectedLabel = selectedStore?.name ?: "Select Store",
                items = activeStores,
                itemLabel = { it.name },
                onSelect = onSelectStore,
                testTag = "rep_store_picker"
            )

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DecimalField(
                    label = "Quantity",
                    value = qtyStr,
                    onValueChange = onQtyChange,
                    testTag = "rep_qty_input",
                    modifier = Modifier.weight(1f),
                    suffix = "kg"
                )
                DecimalField(
                    label = "Rate / kg",
                    value = rateStr,
                    onValueChange = onRateChange,
                    testTag = "rep_rate_input",
                    modifier = Modifier.weight(1f),
                    suffix = currency
                )
            }
        } else if (editingSpecialType == "REP_RETURN") {
            PickerField(
                label = "Store",
                selectedLabel = selectedStore?.name ?: "Select Store",
                items = activeStores,
                itemLabel = { it.name },
                onSelect = onSelectStore,
                testTag = "rep_store_picker"
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DecimalField("Returned Qty", qtyStr, onQtyChange, "rep_qty_input", Modifier.weight(1f), suffix = "kg")
                DecimalField("Rate / kg", rateStr, onRateChange, "rep_rate_input", Modifier.weight(1f), suffix = currency)
            }
            Spacer(Modifier.height(10.dp))
            DecimalField("Expired -> CHORA", expiredStr, onExpiredChange, "rep_expired_input", suffix = "kg")
        } else {
            // Collection or Money Out
            DecimalField(
                label = if (editingSpecialType == "MONEY_OUT") "Payout Amount" else "Amount Received",
                value = collectedStr,
                onValueChange = onCollectedChange,
                testTag = "rep_cash_input",
                suffix = currency
            )
            if (editingSpecialType == null) {
                Spacer(Modifier.height(10.dp))
                DecimalField(
                    label = "Expired / Damage -> CHORA (optional)",
                    value = expiredStr,
                    onValueChange = onExpiredChange,
                    testTag = "rep_expired_input",
                    suffix = "kg"
                )
                if (expired > 0.0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (masked) {
                            "${InputGuards.formatKg(expired)} will be added to CHORA."
                        } else if (goodsCreditPlan.value > 0.0) {
                            "Expired credit: ${InputGuards.formatMoney(goodsCreditPlan.value, currency, false)} (${InputGuards.formatKg(expired)} × ${InputGuards.formatMoney(goodsCreditRate, currency, false)}${if (goodsCreditPlan.capped) ", capped at balance" else ""}) -> CHORA"
                        } else {
                            "${InputGuards.formatKg(expired)} logged -> CHORA (no open debt to credit)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = accents.cyan
                    )
                }
            }
        }

        // GPS Visit Tagging (new transactions)
        if (!isEditing) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accents.inputFill,
                border = BorderStroke(1.dp, accents.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(gpsStatus, style = MaterialTheme.typography.bodySmall, color = accents.cyan)
                        if (gpsConfirmedCluster) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(13.dp))
                                Text("Shop verified (3+ visits ≤ 150m)", style = MaterialTheme.typography.labelSmall, color = accents.emerald)
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = onCaptureGps,
                        shape = RoundedCornerShape(12.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.cyan.copy(alpha = 0.45f)),
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp).testTag("rep_gps_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = "Capture GPS", tint = accents.cyan, modifier = Modifier.size(16.dp))
                            Text("Tag GPS", style = MaterialTheme.typography.labelMedium, color = accents.cyan)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SarimPillInput(
            value = notes,
            onValueChange = { onNotesChange(it.take(500)) },
            label = "NOTES (OPTIONAL)",
            placeholder = "Delivery details, shop notes...",
            testTag = "rep_notes_input"
        )

        Spacer(Modifier.height(12.dp))

        // Result Summary Box
        if (entryMode == RepEntryMode.SALE && editingSpecialType == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SarimHalfRingKpiBox(
                    label = "Total Sale Value",
                    value = InputGuards.formatMoney(previewTotalValue, currency, masked),
                    ringColor = if (paymentType == "CASH") accents.emerald else accents.gold,
                    modifier = Modifier.weight(1f)
                )
                SarimHalfRingKpiBox(
                    label = if (paymentType == "CASH") "Cash Collected" else "Added Credit",
                    value = InputGuards.formatMoney(previewTotalValue, currency, masked),
                    ringColor = if (paymentType == "CASH") accents.emerald else accents.cyan,
                    modifier = Modifier.weight(1f)
                )
            }
            if (hasLoss) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accents.danger.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Negative margin warning: Sale price is below cost price.",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accents.danger,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        } else {
            val totalCreditApplied = InputGuards.round2(collected + goodsCreditPlan.value)
            val newBal = InputGuards.round2(existingDebt - totalCreditApplied)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SarimHalfRingKpiBox(
                    label = if (editingSpecialType == "REP_RETURN") "Return Value" else "Credit Applied",
                    value = InputGuards.formatMoney(if (editingSpecialType == "REP_RETURN") previewTotalValue else totalCreditApplied, currency, masked),
                    ringColor = accents.emerald,
                    modifier = Modifier.weight(1f)
                )
                SarimHalfRingKpiBox(
                    label = "New Balance",
                    value = when {
                        masked -> "••••"
                        abs(newBal) < 0.01 -> "SETTLED"
                        newBal < 0.0 -> "${InputGuards.formatMoney(-newBal, currency, false)} CR"
                        else -> InputGuards.formatMoney(newBal, currency, false)
                    },
                    ringColor = if (newBal > 0.01) accents.gold else accents.emerald,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        val canSubmit = customerName.trim().isNotBlank() && !isRepNameCollision && when {
            editingSpecialType == "REP_RETURN" -> qty > 0.0 || expired > 0.0
            editingSpecialType == "MONEY_OUT" -> collected > 0.0
            entryMode == RepEntryMode.COLLECTION -> collected > 0.0 || expired > 0.0
            else -> qty > 0.0 && rate > 0.0 && !hasLoss
        }
        SarimGradientButton(
            text = if (isEditing) "Update Transaction" else "Save Transaction",
            onClick = onSaveClick,
            enabled = canSubmit,
            testTag = "rep_save_btn"
        )
    }
}

@Composable
fun RepBlockingAlertDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = accents.gold)
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("rep_alert_ok_btn")) {
                Text("OK", color = accents.emerald, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun RepSaveConfirmDialog(
    model: RepSaveConfirmModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(model.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                model.lines.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(k, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        Text(v, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
                if (!model.warning.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accents.gold.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, accents.gold.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = model.warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = accents.gold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                model.notes.forEach { note ->
                    Text("• $note", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("rep_confirm_save_btn")) {
                Text(model.confirmLabel, color = accents.emerald, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
