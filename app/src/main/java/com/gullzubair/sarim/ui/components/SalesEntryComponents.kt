package com.gullzubair.sarim.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.SalesAnalyticsEngine
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import kotlin.math.max

enum class SalesEntryMode {
    SALE,
    COLLECTION
}

data class SalesSaveConfirmModel(
    val title: String,
    val lines: List<Pair<String, String>>,
    val warning: String? = null,
    val notes: List<String> = emptyList(),
    val confirmLabel: String = "Confirm & Save"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SalesEntryCard(
    isEditing: Boolean,
    isSalesRestricted: Boolean,
    masked: Boolean,
    currency: String,
    entryMode: SalesEntryMode,
    onSelectEntryMode: (SalesEntryMode) -> Unit,
    dateIso: String,
    onDateChange: (String) -> Unit,
    todayIso: String,
    customerName: String,
    onCustomerNameChange: (String) -> Unit,
    customerSuggestions: List<SalesAnalyticsEngine.CustomerDirectoryItem>,
    showNewCustomerPhoneField: Boolean,
    customerPhone: String,
    onCustomerPhoneChange: (String) -> Unit,
    repNames: List<String>,
    selectedRep: String,
    onSelectRep: (String) -> Unit,
    existingCredit: Double,
    customerTotalQty: Double,
    onOpenCustomerDetail: (String) -> Unit,
    onOpenMoneyOut: (String) -> Unit,
    qtyStr: String,
    onQtyChange: (String) -> Unit,
    rateStr: String,
    onRateChange: (String) -> Unit,
    activeStores: List<StoreConfig>,
    selectedStore: StoreConfig?,
    onSelectStore: (StoreConfig) -> Unit,
    stockTile: SalesAnalyticsEngine.DateStockTileModel,
    paymentType: String,
    onSelectPaymentType: (String) -> Unit,
    addToLedger: Boolean,
    onAddToLedgerChange: (Boolean) -> Unit,
    previewTotalCost: Double,
    previewTotalValue: Double,
    previewProfit: Double,
    collectedStr: String,
    onCollectedChange: (String) -> Unit,
    expiredStr: String,
    onExpiredChange: (String) -> Unit,
    goodsCreditRate: Double,
    goodsCreditPlan: SalesAnalyticsEngine.GoodsCreditPlan,
    repReturnedStr: String,
    onRepReturnedChange: (String) -> Unit,
    repExpiredStr: String,
    onRepExpiredChange: (String) -> Unit,
    repPendingInfo: SalesAnalyticsEngine.RepReturnPendingInfo,
    repReturnPlan: SalesAnalyticsEngine.GoodsCreditPlan,
    repReturnUnitPrice: Double,
    notes: String,
    onNotesChange: (String) -> Unit,
    onCancelEdit: () -> Unit,
    onSaveClick: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val isRepSelected = selectedRep.isNotBlank() && !selectedRep.equals("NONE", true)
    val isRepReturnMode = entryMode == SalesEntryMode.COLLECTION && isRepSelected
    var showCustomerSuggestions by remember { mutableStateOf(false) }

    val cardTitle = when {
        isEditing && isRepReturnMode -> "Edit Rep Return"
        isEditing && entryMode == SalesEntryMode.COLLECTION -> "Edit Collection"
        isEditing -> "Edit Sale"
        isRepReturnMode -> "Rep Stock Return"
        entryMode == SalesEntryMode.COLLECTION -> "Record Collection"
        else -> "New Sale"
    }

    SarimCard(contentPadding = PaddingValues(16.dp)) {
        // Header + Sale / Collection Mode Toggle
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
                    border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("sales_cancel_edit_btn")
                ) {
                    Text(
                        text = "Cancel Edit",
                        style = MaterialTheme.typography.labelMedium,
                        color = accents.danger,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        SalesEntryMode.SALE to "Sale",
                        SalesEntryMode.COLLECTION to if (isRepSelected) "Rep Return" else "Collection"
                    ).forEach { (mode, label) ->
                        val selected = entryMode == mode
                        Surface(
                            onClick = { onSelectEntryMode(mode) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) accents.softPillFill else accents.inputFill,
                            border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 38.dp)
                                .testTag("sales_entry_mode_${mode.name.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) accents.emerald else accents.textMuted,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Date (#cust-date)
        DatePickerField(
            label = if (entryMode == SalesEntryMode.SALE && !isEditing && dateIso != todayIso) {
                "Date (Supply Stock Date)"
            } else {
                "Date"
            },
            dateIso = dateIso,
            onDateSelected = onDateChange,
            testTag = "sales_date"
        )
        if (entryMode == SalesEntryMode.SALE && !isEditing && dateIso != todayIso) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Supply stock uses ${DateUtils.formatPretty(dateIso)}; sale is recorded on ${DateUtils.formatPretty(todayIso)}.",
                style = MaterialTheme.typography.labelSmall,
                color = accents.cyan
            )
        }

        Spacer(Modifier.height(10.dp))

        // Customer Name + Autocomplete Search (#cust-name)
        SarimPillInput(
            value = customerName,
            onValueChange = { raw ->
                onCustomerNameChange(raw.take(80))
                showCustomerSuggestions = raw.isNotBlank()
            },
            label = if (isRepSelected) "CUSTOMER (LOCKED TO REP)" else "CUSTOMER NAME",
            placeholder = "Type or search customer...",
            readOnly = isRepSelected,
            trailingIcon = if (!isRepSelected && customerName.isNotBlank()) {
                {
                    TextButton(onClick = {
                        onCustomerNameChange("")
                        showCustomerSuggestions = false
                    }) {
                        Text("Clear", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                    }
                }
            } else null,
            testTag = "sales_customer_input"
        )

        if (!isRepSelected && showCustomerSuggestions && customerName.isNotBlank()) {
            val matches = customerSuggestions.take(6)
            val exactExists = customerSuggestions.any { it.name.equals(customerName.trim(), ignoreCase = true) }
            if (matches.isNotEmpty() && !exactExists) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accents.inputFill,
                    border = BorderStroke(1.dp, accents.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        matches.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp)
                                    .clickable {
                                        onCustomerNameChange(item.name)
                                        showCustomerSuggestions = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("sales_cust_suggest_$idx"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.phone.isNotBlank()) {
                                        Text(item.phone, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                    }
                                }
                                if (!isSalesRestricted) {
                                    Text(
                                        text = InputGuards.formatMoney(item.credit, currency, masked),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (item.credit > 0.01) accents.gold else accents.emerald
                                    )
                                }
                            }
                            if (idx < matches.lastIndex) {
                                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            } else if (!exactExists && customerName.trim().length > 2) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "No match found. \"${customerName.trim()}\" will be created as new customer.",
                    style = MaterialTheme.typography.labelSmall,
                    color = accents.cyan
                )
            }
        }

        if (showNewCustomerPhoneField && !isRepSelected) {
            Spacer(Modifier.height(10.dp))
            SarimPillInput(
                value = customerPhone,
                onValueChange = { onCustomerPhoneChange(it.take(32)) },
                label = "MOBILE NUMBER (NEW CUSTOMER)",
                placeholder = "0300-0000000 (optional)",
                testTag = "sales_new_cust_phone"
            )
        }

        Spacer(Modifier.height(12.dp))

        // Sales Rep Toggle Group (#sales-rep-toggle-group)
        Text(
            text = "SALES REP:",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = accents.textMuted,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repNames.forEachIndexed { idx, rep ->
                val selected = selectedRep.equals(rep, ignoreCase = true)
                val label = if (rep.equals("NONE", true)) "Direct" else rep
                Surface(
                    onClick = {
                        onSelectRep(rep)
                        showCustomerSuggestions = false
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) accents.softPillFill else accents.inputFill,
                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 40.dp)
                        .testTag("sales_rep_pill_$idx")
                ) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (selected) accents.emerald else accents.textMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Customer Info Display (#customer-info-display, hidden for restricted users & rep return mode)
        if (!isSalesRestricted && customerName.isNotBlank() && !isRepReturnMode) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accents.inputFill,
                border = BorderStroke(1.dp, accents.borderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sales_customer_info_banner")
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Current Credit", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Text(
                                text = InputGuards.formatMoney(existingCredit, currency, masked),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (existingCredit > 0.01) accents.gold else accents.emerald
                            )
                        }
                        if (entryMode == SalesEntryMode.SALE) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total Quantity", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                Text(
                                    text = InputGuards.formatKg(customerTotalQty),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = accents.cyan
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = { onOpenCustomerDetail(customerName.trim()) },
                            shape = RoundedCornerShape(12.dp),
                            color = accents.softPillFill,
                            border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("sales_cust_info_statement_btn")
                        ) {
                            Text(
                                text = "Statement",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accents.emerald,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            onClick = { onOpenMoneyOut(customerName.trim()) },
                            shape = RoundedCornerShape(12.dp),
                            color = accents.gold.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, accents.gold.copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("sales_cust_info_moneyout_btn")
                        ) {
                            Text(
                                text = "Money Out",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accents.gold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Entry Mode Fields
        when {
            isRepReturnMode -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DecimalField(
                        label = "Expired / Damage (kg)",
                        value = repExpiredStr,
                        onValueChange = onRepExpiredChange,
                        testTag = "sales_rep_expired_input",
                        modifier = Modifier.weight(1f),
                        suffix = "kg"
                    )
                    DecimalField(
                        label = "Returned Qty (kg)",
                        value = repReturnedStr,
                        onValueChange = onRepReturnedChange,
                        testTag = "sales_rep_returned_input",
                        modifier = Modifier.weight(1f),
                        suffix = "kg"
                    )
                }
                Spacer(Modifier.height(10.dp))
                val repExpVal = repExpiredStr.toDoubleOrNull() ?: 0.0
                val repRetVal = repReturnedStr.toDoubleOrNull() ?: 0.0
                val totalBack = InputGuards.round3(repExpVal + repRetVal)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accents.inputFill,
                    border = BorderStroke(1.dp, accents.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Back From Rep", style = MaterialTheme.typography.labelMedium, color = accents.textMuted)
                            Text(
                                InputGuards.formatKg(totalBack),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = accents.emerald
                            )
                        }
                        Text(
                            text = "Pending deliveries: ${InputGuards.formatKg(repPendingInfo.pendingDeliveredKg)} • Already returned: ${InputGuards.formatKg(repPendingInfo.alreadyReturnedKg)} • Remaining: ${InputGuards.formatKg(repPendingInfo.remainingDeliverableKg)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = accents.textMuted
                        )
                        if (!isSalesRestricted && totalBack > 0.0 && repReturnPlan.value > 0.0) {
                            Text(
                                text = "Balance deduction: ${InputGuards.formatMoney(repReturnPlan.value, currency, masked)} (${InputGuards.formatKg(totalBack)} × ${InputGuards.formatMoney(repReturnUnitPrice, currency, masked)}${if (repReturnPlan.capped) ", capped" else ""})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = accents.cyan
                            )
                        }
                        Text(
                            text = "Stock reaches CHORA / Production when Calculator settles.",
                            style = MaterialTheme.typography.labelSmall,
                            color = accents.gold
                        )
                    }
                }
            }

            entryMode == SalesEntryMode.COLLECTION -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DecimalField(
                        label = "Amount Received",
                        value = collectedStr,
                        onValueChange = onCollectedChange,
                        testTag = "sales_cash_input",
                        modifier = Modifier.weight(1f),
                        suffix = currency
                    )
                    DecimalField(
                        label = "Expired / Damage (kg)",
                        value = expiredStr,
                        onValueChange = onExpiredChange,
                        testTag = "sales_expired_input",
                        modifier = Modifier.weight(1f),
                        suffix = "kg"
                    )
                }
                val colVal = collectedStr.toDoubleOrNull() ?: 0.0
                val expVal = expiredStr.toDoubleOrNull() ?: 0.0
                if (!isEditing && colVal > 0.0) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Add to Daily Ledger (Cash In +)", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = addToLedger,
                            onCheckedChange = onAddToLedgerChange,
                            modifier = Modifier.testTag("sales_coll_ledger_switch")
                        )
                    }
                }
                if (!isSalesRestricted && customerName.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    val afterBal = InputGuards.round2(max(0.0, existingCredit - colVal - goodsCreditPlan.value))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = accents.inputFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "BALANCE AFTER COLLECTION",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accents.textMuted
                            )
                            Text(
                                InputGuards.formatMoney(afterBal, currency, masked),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (afterBal > 0.01) accents.gold else accents.emerald
                            )
                            if (expVal > 0.0) {
                                Text(
                                    text = if (goodsCreditPlan.value > 0.0) {
                                        "Expired credit: ${InputGuards.formatMoney(goodsCreditPlan.value, currency, masked)} (${InputGuards.formatKg(expVal)} × ${InputGuards.formatMoney(goodsCreditRate, currency, masked)}${if (goodsCreditPlan.capped) ", limited to owed" else ""}) -> CHORA"
                                    } else {
                                        "${InputGuards.formatKg(expVal)} expired will be added to CHORA (no credit owed)."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accents.cyan
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                // SALE mode
                if (isSalesRestricted) {
                    DecimalField(
                        label = "Quantity (kg)",
                        value = qtyStr,
                        onValueChange = onQtyChange,
                        testTag = "sales_qty_input",
                        suffix = "kg"
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DecimalField(
                            label = "Quantity (kg)",
                            value = qtyStr,
                            onValueChange = onQtyChange,
                            testTag = "sales_qty_input",
                            modifier = Modifier.weight(1f),
                            suffix = "kg"
                        )
                        DecimalField(
                            label = "Rate / kg",
                            value = rateStr,
                            onValueChange = onRateChange,
                            testTag = "sales_rate_input",
                            modifier = Modifier.weight(1f),
                            suffix = currency
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Supply Store Toggle Pills (#supply-store-toggle-group)
                Text(
                    text = "SUPPLY FROM STORE:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accents.textMuted,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                )
                if (activeStores.isEmpty()) {
                    Text(
                        "No active stores configured in Factory Formulas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = accents.gold
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        activeStores.forEach { st ->
                            val selected = selectedStore?.key == st.key
                            Surface(
                                onClick = { onSelectStore(st) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) accents.softPillFill else accents.inputFill,
                                border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 40.dp)
                                    .testTag("sales_store_pill_${st.key}")
                            ) {
                                Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = st.name,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (selected) accents.emerald else accents.textMuted,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Expandable Stock Tile (#inventory-warning)
                SalesStockTile(stockTile = stockTile)

                Spacer(Modifier.height(12.dp))

                // Payment Type Toggle (#payment-type-toggle-group)
                Text(
                    text = "PAYMENT:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accents.textMuted,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CASH" to "Cash", "CREDIT" to "Credit").forEach { (key, title) ->
                        val enabled = !(isRepSelected && key == "CASH")
                        val selected = paymentType == key
                        Surface(
                            onClick = { if (enabled) onSelectPaymentType(key) },
                            shape = RoundedCornerShape(14.dp),
                            color = when {
                                !enabled -> accents.inputFill.copy(alpha = 0.4f)
                                selected -> accents.softPillFill
                                else -> accents.inputFill
                            },
                            border = BorderStroke(
                                1.dp,
                                when {
                                    !enabled -> accents.borderSubtle.copy(alpha = 0.4f)
                                    selected -> if (key == "CREDIT") accents.gold else accents.emerald
                                    else -> accents.borderSubtle
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 42.dp)
                                .testTag("sales_pay_type_$key")
                        ) {
                            Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = when {
                                        !enabled -> accents.textMuted.copy(alpha = 0.45f)
                                        selected -> if (key == "CREDIT") accents.gold else accents.emerald
                                        else -> accents.textMuted
                                    }
                                )
                            }
                        }
                    }
                }
                if (isRepSelected) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Rep deliveries are recorded on Credit and settled later in Calculator.",
                        style = MaterialTheme.typography.labelSmall,
                        color = accents.textMuted
                    )
                }

                if (!isEditing && paymentType == "CASH") {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Add cash sale to Daily Ledger (Cash In +)", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = addToLedger,
                            onCheckedChange = onAddToLedgerChange,
                            modifier = Modifier.testTag("sales_ledger_switch")
                        )
                    }
                }

                // Result Preview Box (Total Cost / Total Value / Profit; Cost & Profit hidden for restricted users)
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = accents.inputFill,
                    border = BorderStroke(1.dp, accents.borderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (!isSalesRestricted) {
                            Column(Modifier.weight(1f)) {
                                Text("Total Cost", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                Text(
                                    InputGuards.formatMoney(previewTotalCost, currency, masked),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = accents.danger,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = if (isSalesRestricted) Alignment.Start else Alignment.CenterHorizontally
                        ) {
                            Text("Total Value", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Text(
                                InputGuards.formatMoney(previewTotalValue, currency, isSalesRestricted && false || snapshotMaskedPreview(masked, isSalesRestricted)),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = accents.cyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!isSalesRestricted) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("Profit", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                                Text(
                                    InputGuards.formatMoney(previewProfit, currency, masked),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (previewProfit >= 0.0) accents.emerald else accents.danger,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SarimPillInput(
            value = notes,
            onValueChange = { onNotesChange(it.take(500)) },
            label = "NOTES",
            placeholder = "Optional note",
            testTag = "sales_notes_input"
        )

        Spacer(Modifier.height(14.dp))
        val qtyVal = qtyStr.toDoubleOrNull() ?: 0.0
        val colVal = collectedStr.toDoubleOrNull() ?: 0.0
        val expVal = expiredStr.toDoubleOrNull() ?: 0.0
        val repRetVal = repReturnedStr.toDoubleOrNull() ?: 0.0
        val repExpVal = repExpiredStr.toDoubleOrNull() ?: 0.0
        val canSave = when {
            isRepReturnMode -> isRepSelected && (repRetVal > 0.0 || repExpVal > 0.0)
            entryMode == SalesEntryMode.COLLECTION -> customerName.isNotBlank() && (colVal > 0.0 || expVal > 0.0)
            else -> customerName.isNotBlank() && selectedStore != null && qtyVal > 0.0
        }
        val btnText = when {
            isEditing && isRepReturnMode -> "Update Rep Return"
            isEditing && entryMode == SalesEntryMode.COLLECTION -> "Update Collection"
            isEditing -> "Update Sale"
            isRepReturnMode -> "Save Rep Return"
            entryMode == SalesEntryMode.COLLECTION -> "Save Collection"
            paymentType == "CREDIT" -> "Save Credit Sale"
            else -> "Save Cash Sale"
        }
        SarimGradientButton(
            text = btnText,
            onClick = onSaveClick,
            enabled = canSave,
            testTag = "sales_save_btn"
        )
    }
}

private fun snapshotMaskedPreview(masked: Boolean, isSalesRestricted: Boolean): Boolean =
    if (isSalesRestricted) false else masked

@Composable
fun SalesStockTile(stockTile: SalesAnalyticsEngine.DateStockTileModel) {
    val accents = LocalSarimAccents.current
    var expanded by remember { mutableStateOf(false) }
    val toneColor = when (stockTile.tone) {
        "short" -> accents.danger
        "low" -> accents.gold
        else -> accents.emerald
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = toneColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, toneColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("sales_stock_tile")
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                        contentDescription = "Toggle stock breakdown",
                        tint = toneColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (stockTile.storeName.isNotBlank()) "${stockTile.storeName} Stock" else "Store Stock",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = toneColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${InputGuards.formatKg(max(0.0, stockTile.availableKg))} avail",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = toneColor
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stockTile.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accents.inputFill)
            ) {
                val frac = (stockTile.usedPercent / 100.0).toFloat().coerceIn(0f, 1f)
                if (frac > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(toneColor)
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider(color = toneColor.copy(alpha = 0.25f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Produced", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                        Text(InputGuards.formatKg(stockTile.producedKg), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Returned", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                        Text(InputGuards.formatKg(stockTile.returnsKg), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Already Sold", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                        Text(InputGuards.formatKg(stockTile.soldKg), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

@Composable
fun SalesBlockingAlertDialog(
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
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("sales_alert_ok_btn")) {
                Text("OK", color = accents.emerald, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun SalesSaveConfirmDialog(
    model: SalesSaveConfirmModel,
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
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("sales_confirm_save_btn")) {
                Text(model.confirmLabel, color = accents.emerald, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CustomerMoneyOutDialog(
    customerName: String,
    currentOutstanding: Double,
    currency: String,
    masked: Boolean,
    defaultDate: String,
    onConfirm: (date: String, amount: Double, addToLedger: Boolean, notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    var date by remember(defaultDate) { mutableStateOf(defaultDate) }
    var amountStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var addToLedger by remember { mutableStateOf(true) }
    val amt = amountStr.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Money Out / Payout", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(customerName, style = MaterialTheme.typography.bodySmall, color = accents.emerald)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Current Balance: ${InputGuards.formatMoney(currentOutstanding, currency, masked)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = accents.textMuted
                )
                DatePickerField("Date", date, { date = it }, "money_out_date")
                DecimalField("Payout Amount", amountStr, { amountStr = it }, "money_out_amount", suffix = currency)
                SarimPillInput(
                    value = notes,
                    onValueChange = { notes = it.take(200) },
                    label = "NOTES",
                    placeholder = "Refund / advance payout",
                    testTag = "money_out_notes"
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mirror in Daily Ledger", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = addToLedger, onCheckedChange = { addToLedger = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(date, amt, addToLedger, notes) },
                enabled = amt > 0.0,
                modifier = Modifier.testTag("money_out_confirm_btn")
            ) {
                Text("Record Payout", color = accents.gold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
