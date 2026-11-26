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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Warning
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.RepSalesAnalyticsEngine
import com.gullzubair.sarim.domain.SalesAnalyticsEngine
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.theme.PlayfairFont
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
    editingSpecialType: String?,
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
    onOpenCustomerLedger: (String) -> Unit,
    onClearCustomer: () -> Unit,
    paymentType: String, // "CREDIT" | "CASH"
    onSelectPaymentType: (String) -> Unit,
    qtyStr: String,
    onQtyChange: (String) -> Unit,
    rateVal: Double,
    previewTotalValue: Double,
    previewUnitCost: Double,
    previewProfit: Double,
    collectedStr: String,
    onCollectedChange: (String) -> Unit,
    expiredStr: String,
    onExpiredChange: (String) -> Unit,
    goodsCreditRate: Double,
    goodsCreditPlan: SalesAnalyticsEngine.GoodsCreditPlan,
    onCancelEdit: () -> Unit,
    onSaveClick: () -> Unit
) {
    val accents = LocalSarimAccents.current
    var showSuggestions by remember { mutableStateOf(false) }

    val cardTitle = when {
        isEditing && editingSpecialType == "REP_RETURN" -> "Edit Rep Return"
        isEditing && editingSpecialType == "MONEY_OUT" -> "Edit Money Out"
        isEditing && entryMode == RepEntryMode.COLLECTION -> "Edit Collection"
        isEditing -> "Edit Transaction"
        else -> "New Transaction"
    }

    val collected = collectedStr.toDoubleOrNull() ?: 0.0
    val expired = expiredStr.toDoubleOrNull() ?: 0.0
    val qty = qtyStr.toDoubleOrNull() ?: 0.0
    val hasLoss = entryMode == RepEntryMode.SALE && editingSpecialType == null &&
        qty > 0.0 && rateVal > 0.0 && previewUnitCost > 0.0 && previewProfit < -0.01

    SarimCard(contentPadding = PaddingValues(16.dp)) {
        // Header + Sale / Collection Toggle (#rep-new-transaction-card)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = cardTitle,
                fontFamily = PlayfairFont,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = accents.emerald,
                modifier = Modifier.weight(1f)
            )
            if (isEditing) {
                Surface(
                    onClick = onCancelEdit,
                    shape = RoundedCornerShape(999.dp),
                    color = accents.danger.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.45f)),
                    modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("rep_cancel_edit_btn")
                ) {
                    Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
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
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) accents.softPillFill else accents.inputFill,
                            border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 38.dp)
                                .testTag("rep_mode_${mode.name.lowercase()}")
                        ) {
                            Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                                    ),
                                    color = if (selected) accents.emerald else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            DatePickerField("DATE", dateIso, onDateChange, "rep_date", Modifier.weight(0.92f))
            SarimPillInput(
                value = customerName,
                onValueChange = {
                    onCustomerNameChange(it.take(80))
                    showSuggestions = it.isNotBlank()
                },
                label = "CUSTOMER NAME",
                placeholder = "Search customer...",
                trailingIcon = if (customerName.isNotBlank()) {
                    {
                        Surface(
                            onClick = {
                                onClearCustomer()
                                showSuggestions = false
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Transparent,
                            modifier = Modifier.size(48.dp).testTag("rep_clear_cust_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear customer filter", tint = accents.textMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                } else null,
                testTag = "rep_customer_input",
                modifier = Modifier.weight(1.08f)
            )
        }

        // Autocomplete suggestions (#rep-customer-search-results)
        val topSuggestions = remember(customerSuggestions, customerName) {
            val clean = customerName.trim()
            if (clean.isEmpty()) emptyList()
            else customerSuggestions.filter {
                !it.name.equals(clean, ignoreCase = true)
            }.take(6)
        }
        AnimatedVisibility(visible = showSuggestions && topSuggestions.isNotEmpty()) {
            val query = customerName.trim()
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = accents.cardElevated,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, accents.borderSubtle),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    topSuggestions.forEachIndexed { idx, item ->
                        if (idx > 0) HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.5f))
                        val highlighted = remember(item.name, query, accents.emerald) {
                            buildAnnotatedString {
                                val start = item.name.indexOf(query, ignoreCase = true)
                                if (start >= 0 && query.isNotEmpty()) {
                                    append(item.name.substring(0, start))
                                    withStyle(SpanStyle(color = accents.emerald, fontWeight = FontWeight.ExtraBold)) {
                                        append(item.name.substring(start, start + query.length))
                                    }
                                    append(item.name.substring(start + query.length))
                                } else append(item.name)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCustomerNameChange(item.name)
                                    showSuggestions = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(highlighted, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                label = "Mobile number",
                placeholder = "03XX-XXXXXXX",
                leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                testTag = "rep_new_phone_input"
            )
        }

        Spacer(Modifier.height(10.dp))

        if (entryMode == RepEntryMode.SALE && editingSpecialType == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                DecimalField(
                    label = "QUANTITY (KG)",
                    value = qtyStr,
                    onValueChange = onQtyChange,
                    testTag = "rep_qty_input",
                    modifier = Modifier.weight(0.5f)
                )
                Column(modifier = Modifier.weight(0.5f)) {
                    Text(
                        text = "PAYMENT TYPE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accents.textMuted,
                        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("CREDIT" to "Credit", "CASH" to "Cash").forEach { (key, label) ->
                            val selected = paymentType == key
                            Surface(
                                onClick = { onSelectPaymentType(key) },
                                shape = RoundedCornerShape(999.dp),
                                color = if (selected) accents.softPillFill else accents.inputFill,
                                border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 46.dp).testTag("rep_pay_$key")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                                        ),
                                        color = if (selected) accents.emerald else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (editingSpecialType == "REP_RETURN") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DecimalField("Returned Qty (kg)", qtyStr, onQtyChange, "rep_qty_input", Modifier.weight(1f), suffix = "kg")
                DecimalField("Expired / Damage Qty (kg)", expiredStr, onExpiredChange, "rep_expired_input", Modifier.weight(1f), suffix = "kg")
            }
        } else {
            // Collection (#rep-coll-inputs) or legacy Money Out
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SarimPillInput(
                    value = collectedStr,
                    onValueChange = { raw ->
                        if (raw.isEmpty() || raw.matches(Regex("^\\d*\\.?\\d{0,3}$"))) {
                            onCollectedChange(raw)
                        }
                    },
                    label = if (editingSpecialType == "MONEY_OUT") "PAYOUT AMOUNT" else "AMOUNT RECEIVED (${if (currency.isBlank()) "Rp" else currency})",
                    placeholder = "Cash received from customer",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    testTag = "rep_cash_input",
                    modifier = Modifier.weight(1f)
                )
                if (editingSpecialType == null) {
                    DecimalField(
                        label = "EXPIRED / DAMAGE QTY (KG)",
                        value = expiredStr,
                        onValueChange = onExpiredChange,
                        testTag = "rep_expired_input",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (editingSpecialType == null && expired > 0.0) {
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

        // Current Debt (#rep-customer-info-display)
        if (!masked && customerName.trim().isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accents.inputFill,
                border = BorderStroke(1.dp, if (existingDebt > 0.01) accents.gold.copy(alpha = 0.4f) else accents.emerald.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenCustomerLedger(customerName.trim()) }
                    .testTag("rep_customer_info_banner")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Current Debt:", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    Text(
                        text = if (abs(existingDebt) < 0.01) InputGuards.formatMoney(0.0, currency, false)
                        else InputGuards.formatMoney(existingDebt, currency, false),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (existingDebt > 0.01) accents.gold else accents.emerald
                    )
                }
            }
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

        Spacer(Modifier.height(14.dp))

        // Result Summary Box (#rep-result-label + #rep-total-value)
        val resultLabel = when {
            editingSpecialType == "REP_RETURN" -> "Return Value:"
            entryMode == RepEntryMode.COLLECTION -> "New Balance After Collection:"
            else -> "Transaction Value:"
        }
        val resultVal = when {
            editingSpecialType == "REP_RETURN" -> InputGuards.formatMoney(previewTotalValue, currency, masked)
            entryMode == RepEntryMode.COLLECTION -> {
                val remaining = InputGuards.round2(max(0.0, existingDebt - collected - goodsCreditPlan.value))
                InputGuards.formatMoney(remaining, currency, masked)
            }
            else -> InputGuards.formatMoney(previewTotalValue, currency, masked)
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = accents.inputFill,
            border = BorderStroke(1.dp, accents.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(resultLabel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                Text(
                    text = resultVal,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = accents.emerald
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SarimGradientButton(
            text = if (isEditing) "Update Transaction" else "Save Transaction",
            onClick = onSaveClick,
            enabled = true,
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
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = accents.gold)
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
