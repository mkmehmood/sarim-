package com.gullzubair.sarim.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.PhotoBlob
import com.gullzubair.sarim.data.model.ProductionRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.ProductionAnalyticsEngine
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductionEntryCard(
    isEditing: Boolean,
    isMoneyRestrictedMode: Boolean,
    masked: Boolean,
    currency: String,
    date: String,
    onDateChange: (String) -> Unit,
    grossStr: String,
    onGrossChange: (String) -> Unit,
    containerStr: String,
    onContainerChange: (String) -> Unit,
    net: Double,
    activeStores: List<StoreConfig>,
    selectedStore: StoreConfig?,
    onSelectStore: (StoreConfig) -> Unit,
    unitsStr: String,
    onUnitsChange: (String) -> Unit,
    unitsVal: Double,
    dynamicCost: ProductionAnalyticsEngine.DynamicProdCost,
    photoSlots: List<ProdPhotoSlot>,
    onAttachPhotoClick: () -> Unit,
    onClearPhotosClick: () -> Unit,
    onRemovePhotoAt: (Int) -> Unit,
    onPreviewPhotoAt: (Int) -> Unit,
    onCancelEdit: () -> Unit,
    onSaveClick: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val hideMoneyPanels = isMoneyRestrictedMode
    val availableColor = when {
        dynamicCost.availableUnits <= 0.0 -> accents.danger
        dynamicCost.availableUnits < 10.0 -> accents.gold
        else -> accents.emerald
    }
    val hasInsufficientUnits = unitsVal > dynamicCost.effectiveAvailable + 1e-9

    SarimCard(contentPadding = PaddingValues(16.dp)) {
        // Card Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditing) "Edit Production Entry" else "New Entry",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = accents.emerald
            )
            if (isEditing) {
                Surface(
                    onClick = onCancelEdit,
                    shape = RoundedCornerShape(14.dp),
                    color = accents.danger.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.4f)),
                    modifier = Modifier.testTag("prod_cancel_edit_btn")
                ) {
                    Text(
                        "Cancel Edit",
                        style = MaterialTheme.typography.labelMedium,
                        color = accents.danger,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Formula Units Available Banner (#unitsAvailableIndicator)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = accents.inputFill,
            border = BorderStroke(1.dp, availableColor.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("prod_units_available_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Formula Units Available:",
                    style = MaterialTheme.typography.labelMedium,
                    color = accents.textMuted
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = availableColor.copy(alpha = 0.14f)
                ) {
                    val availText = buildString {
                        append("${InputGuards.fmtNum(dynamicCost.availableUnits)} units available")
                        if (dynamicCost.formulaName.isNotBlank()) {
                            append(" · ${dynamicCost.formulaName}")
                        }
                    }
                    Text(
                        text = availText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = availableColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Select Date
        DatePickerField("Select Date", date, onDateChange, "prod_date")

        Spacer(Modifier.height(10.dp))

        // Gross Weight & Container Weight
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DecimalField(
                label = "Gross Weight (kg)",
                value = grossStr,
                onValueChange = onGrossChange,
                testTag = "prod_gross_input",
                modifier = Modifier.weight(1f),
                suffix = "kg"
            )
            DecimalField(
                label = "Container Weight (kg)",
                value = containerStr,
                onValueChange = onContainerChange,
                testTag = "prod_container_input",
                modifier = Modifier.weight(1f),
                suffix = "kg"
            )
        }

        Spacer(Modifier.height(10.dp))

        // Net Production (kg) Read-only + Dynamic Cost Value underneath
        SarimPillInput(
            value = if (net > 0.0) InputGuards.fmtNum(net) else "0.00",
            onValueChange = {},
            label = "NET PRODUCTION (KG)",
            readOnly = true,
            suffix = { Text("kg", style = MaterialTheme.typography.bodySmall) },
            testTag = "prod_net_display"
        )
        if (!hideMoneyPanels) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Dynamic Cost Value: ",
                    style = MaterialTheme.typography.bodySmall,
                    color = accents.textMuted
                )
                Text(
                    text = InputGuards.formatMoney(net * dynamicCost.dynamicCostPerKg, currency, masked),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = accents.danger
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Select Store Toggle Group (#storeSelectorToggleGroup)
        Text(
            text = "SELECT STORE:",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = accents.textMuted,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )
        if (activeStores.isEmpty()) {
            Text(
                "No active stores configured. Add stores in Factory Formulas.",
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
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) accents.softPillFill else accents.inputFill,
                        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("prod_store_pill_${st.key}")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = st.name,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) accents.emerald else accents.textMuted
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Formula Units Used + Sale Price (per kg) + Camera Photo Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                DecimalField(
                    label = "Formula Units Used",
                    value = unitsStr,
                    onValueChange = onUnitsChange,
                    testTag = "prod_units_input"
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Units of factory mixture consumed",
                    style = MaterialTheme.typography.labelSmall,
                    color = accents.textMuted,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                if (!hideMoneyPanels) {
                    Text(
                        text = "SALE PRICE (PER KG)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accents.textMuted,
                        modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
                    )
                } else {
                    Text(
                        text = "SCALE PHOTOS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accents.textMuted,
                        modifier = Modifier.padding(start = 2.dp, bottom = 5.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (!hideMoneyPanels) {
                        Text(
                            text = if (masked) "••••/kg" else "${InputGuards.fmtNum(dynamicCost.salePrice)}/kg",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = accents.emerald,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = if (photoSlots.isEmpty()) "Optional" else "${photoSlots.size}/6",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accents.textMuted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Surface(
                            onClick = onAttachPhotoClick,
                            enabled = photoSlots.size < 6,
                            shape = RoundedCornerShape(14.dp),
                            color = accents.softPillFill,
                            border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("prod_attach_photo_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Attach photo",
                                    tint = accents.emerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (photoSlots.isNotEmpty()) {
                                    Surface(
                                        shape = CircleShape,
                                        color = accents.gold,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(16.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "${photoSlots.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (photoSlots.isNotEmpty()) {
                            IconButton(
                                onClick = onClearPhotosClick,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("prod_clear_photos_btn")
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove attached photos",
                                    tint = accents.danger
                                )
                            }
                        }
                    }
                }
            }
        }

        // Photo Thumbnails Strip
        if (photoSlots.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                photoSlots.forEachIndexed { idx, slot ->
                    Box(modifier = Modifier.size(76.dp)) {
                        DecodedDataUrlImage(
                            dataUrl = slot.dataUrl,
                            contentDescription = "Scale photo ${idx + 1}",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPreviewPhotoAt(idx) }
                                .testTag("prod_photo_thumb_$idx")
                        )
                        IconButton(
                            onClick = { onRemovePhotoAt(idx) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(36.dp)
                                .testTag("prod_photo_remove_$idx")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.72f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove photo ${idx + 1}",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Formula Cost Panel (#prod-formula-cost-block)
        if (!hideMoneyPanels) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accents.inputFill,
                border = BorderStroke(1.dp, accents.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProdMetricRow(
                        label = "Formula Unit Cost:",
                        value = if (masked) "••••/unit" else "${InputGuards.formatMoney(dynamicCost.costPerUnit, currency, false)}/unit",
                        valueColor = accents.danger
                    )
                    ProdMetricRow(
                        label = "Total Formula Cost:",
                        value = InputGuards.formatMoney(dynamicCost.totalFormulaCost, currency, masked),
                        valueColor = accents.danger
                    )
                    ProdMetricRow(
                        label = "Cost per kg:",
                        value = if (masked) "••••/kg" else "${InputGuards.fmtNum(dynamicCost.dynamicCostPerKg)}/kg",
                        valueColor = accents.danger
                    )
                }
            }

            // Profit Result Box (#prod-profit-block)
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accents.softPillFill.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ProdMetricRow(
                        label = "Dynamic Cost Price:",
                        value = if (masked) "••••/kg" else "${InputGuards.fmtNum(dynamicCost.dynamicCostPerKg)}/kg",
                        valueColor = accents.danger
                    )
                    ProdMetricRow(
                        label = "Sale Price:",
                        value = if (masked) "••••/kg" else "${InputGuards.fmtNum(dynamicCost.salePrice)}/kg",
                        valueColor = accents.emerald
                    )
                    HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.5f))
                    val profitPerKg = if (net > 0.0) dynamicCost.salePrice - dynamicCost.dynamicCostPerKg else 0.0
                    ProdMetricRow(
                        label = "Profit (per kg):",
                        value = InputGuards.formatMoney(profitPerKg, currency, masked),
                        valueColor = if (profitPerKg >= 0.0) accents.emerald else accents.danger
                    )
                }
            }
        }

        // Insufficient Formula Units Warning (#insufficientUnitsWarning)
        if (hasInsufficientUnits) {
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accents.danger.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("prod_insufficient_units_warn")
            ) {
                Text(
                    text = "Insufficient formula units available! Please reduce formula units used.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = accents.danger,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SarimGradientButton(
            text = if (isEditing) "Update Production" else "Save Production",
            onClick = onSaveClick,
            enabled = selectedStore != null && net > 0.0,
            testTag = "prod_save_btn"
        )
    }
}

@Composable
private fun ProdMetricRow(
    label: String,
    value: String,
    valueColor: Color
) {
    val accents = LocalSarimAccents.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = valueColor)
    }
}

@Composable
fun ProductionHistoryRowCard(
    rec: ProductionRecord,
    stores: List<StoreConfig>,
    photoStore: Map<String, PhotoBlob>,
    selectedDate: String,
    showStoreChip: Boolean,
    currency: String,
    masked: Boolean,
    isPhotoSelected: Boolean,
    onTogglePhotoSelect: (Boolean) -> Unit,
    onOpenLightbox: (List<String>) -> Unit,
    onClick: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val storeLabel = FormulaEngine.storeLabelFor(rec.store, stores).ifBlank { rec.store }
    val recPhotos = rec.photoKeys.mapNotNull { k ->
        photoStore[k]?.takeIf { !it.deleted && it.dataUri.isNotBlank() }?.dataUri
    }
    val hasPhotos = recPhotos.isNotEmpty() && !rec.isReturn && !rec.isTransfer
    val isSelectedDate = rec.date.take(10) == selectedDate.take(10)

    val title = when {
        rec.isTransfer && rec.transferDirection == "out" ->
            "Transfer out to ${FormulaEngine.storeLabelFor(rec.transferPeerStore, stores).ifBlank { rec.transferPeerStore }}"
        rec.isTransfer ->
            "Transfer in from ${FormulaEngine.storeLabelFor(rec.transferPeerStore, stores).ifBlank { rec.transferPeerStore }}"
        rec.isReturn ->
            "${if (rec.isMerged) "Merged returns by" else "Returned by"} ${rec.returnedBy.ifBlank { "Representative" }}"
        else -> "$storeLabel production"
    }

    val tagText = when {
        rec.isTransfer && rec.transferDirection == "out" -> "Out"
        rec.isTransfer -> "In"
        rec.isReturn -> "Return"
        else -> "Profit ${InputGuards.formatMoney(rec.profit, currency, masked)}"
    }
    val tagColor = when {
        rec.isTransfer && rec.transferDirection == "out" -> accents.danger
        rec.isTransfer || rec.isReturn -> accents.emerald
        rec.profit >= 0.0 -> accents.emerald
        else -> accents.danger
    }

    SarimCard(
        accentBorder = if (isSelectedDate) accents.emerald.copy(alpha = 0.45f) else null,
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("prod_hist_card_${rec.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasPhotos) {
                Checkbox(
                    checked = isPhotoSelected,
                    onCheckedChange = onTogglePhotoSelect,
                    modifier = Modifier.testTag("prod_photo_check_${rec.id}")
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (showStoreChip && storeLabel.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accents.softPillFill
                        ) {
                            Text(
                                storeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accents.emerald,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    EditedBadge(rec.isEdited)
                }
                Spacer(Modifier.height(2.dp))
                val dateSub = buildString {
                    append(DateUtils.formatPretty(rec.date))
                    if (rec.time.isNotBlank()) append(" ${rec.time}")
                    if (isSelectedDate) append(" (Selected)")
                    if (rec.managedBy.isNotBlank()) append(" · ${rec.managedBy}")
                }
                Text(dateSub, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
            }

            if (hasPhotos) {
                Surface(
                    onClick = { onOpenLightbox(recPhotos) },
                    shape = RoundedCornerShape(10.dp),
                    color = accents.softPillFill,
                    border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .testTag("prod_row_photo_${rec.id}")
                ) {
                    Row(Modifier.padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "View photos", tint = accents.emerald, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("${recPhotos.size}", style = MaterialTheme.typography.labelSmall, color = accents.emerald)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = InputGuards.formatKg(abs(rec.net)),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (rec.isTransfer && rec.transferDirection == "out") accents.danger else accents.emerald
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = tagColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = tagText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = tagColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (!rec.isTransfer && !rec.isReturn && !masked) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.5f))
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Cost ${InputGuards.fmtNum(rec.cp)}/kg · Sale ${InputGuards.fmtNum(rec.sp)}/kg",
                    style = MaterialTheme.typography.labelSmall,
                    color = accents.textMuted
                )
                if (rec.formulaUnits > 0.0) {
                    Text(
                        "${InputGuards.fmtNum(rec.formulaUnits)} units · ${InputGuards.formatMoney(rec.totalSale, currency, false)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accents.textMuted
                    )
                }
            }
        }
        val note = rec.returnNote.ifBlank { rec.transferNote }
        if (note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("Note: $note", style = MaterialTheme.typography.labelSmall, color = accents.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ProductionSaveConfirmDialog(
    storeName: String,
    dateIso: String,
    netKg: Double,
    totalCost: Double,
    totalSale: Double,
    hideMoneyPanels: Boolean,
    masked: Boolean,
    currency: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val netProfit = totalSale - totalCost
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this production entry?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$storeName · ${DateUtils.formatPretty(dateIso)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Net Production: ${InputGuards.formatKg(netKg)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = accents.emerald
                )
                if (!hideMoneyPanels) {
                    Text(
                        "Total Cost: ${InputGuards.formatMoney(totalCost, currency, masked)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = accents.danger
                    )
                    Text(
                        "Net Profit: ${InputGuards.formatMoney(netProfit, currency, masked)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (netProfit >= 0.0) accents.emerald else accents.danger
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("prod_confirm_save_btn")
            ) {
                Text("Save", color = accents.emerald, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
