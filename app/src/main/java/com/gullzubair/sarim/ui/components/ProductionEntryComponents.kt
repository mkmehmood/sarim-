package com.gullzubair.sarim.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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

/**
 * Production history card — mirrors the web `hcCard`: tone accent bar, lead icon, title/date,
 * kg amount + tag, and an expandable body with the metric grid, photo strip and actions.
 */
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
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onTogglePhotoSelect: (Boolean) -> Unit,
    onOpenLightbox: (List<String>, Int) -> Unit,
    onOpenDetails: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val storeLabel = FormulaEngine.storeLabelFor(rec.store, stores).ifBlank { rec.store }
    val peerLabel = FormulaEngine.storeLabelFor(rec.transferPeerStore, stores).ifBlank { rec.transferPeerStore }
    val recPhotos = rec.photoKeys.mapNotNull { k ->
        photoStore[k]?.takeIf { !it.deleted && it.dataUri.isNotBlank() }?.dataUri
    }
    val hasPhotos = recPhotos.isNotEmpty() && !rec.isReturn && !rec.isTransfer
    val isSelectedDate = rec.date.take(10) == selectedDate.take(10)
    val isOut = rec.isTransfer && rec.transferDirection == "out"
    val money = { v: Double -> InputGuards.formatMoney(v, currency, masked) }

    val title = when {
        isOut -> "Transfer out to $peerLabel"
        rec.isTransfer -> "Transfer in from $peerLabel"
        rec.isReturn -> "${if (rec.isMerged) "Merged returns by" else "Returned by"} ${rec.returnedBy.ifBlank { "Representative" }}"
        else -> "$storeLabel production"
    }
    val tagText = when {
        isOut -> "Out"
        rec.isTransfer -> "In"
        rec.isReturn -> "Return"
        else -> "Profit ${money(rec.profit)}"
    }
    val tone = when {
        isOut -> accents.danger
        rec.isTransfer || rec.isReturn -> accents.emerald
        rec.profit >= 0.0 -> accents.cyan
        else -> accents.danger
    }
    val tagColor = when {
        isOut -> accents.danger
        rec.isTransfer || rec.isReturn -> accents.emerald
        rec.profit >= 0.0 -> accents.emerald
        else -> accents.danger
    }
    val leadIcon = when {
        rec.isTransfer -> Icons.Default.SwapHoriz
        rec.isReturn -> Icons.Default.Replay
        else -> Icons.Default.Inventory2
    }
    val canEdit = onEdit != null && !rec.isMerged && !rec.isReturn && !rec.isTransfer
    val canDelete = !rec.isMerged

    // (label, value, colour, wide) — same order and wording as the web production card
    val metrics = buildList<Triple<String, String, Color>> {
        if (!rec.isReturn && !rec.isTransfer) {
            if (rec.grossWt > 0.0) add(Triple("Gross weight", InputGuards.formatKg(rec.grossWt), accents.emerald))
            if (rec.contWt > 0.0) add(Triple("Container", InputGuards.formatKg(rec.contWt), accents.textMuted))
            if (!masked) {
                add(Triple("Cost price", "${InputGuards.fmtNum(rec.cp)}/kg", accents.danger))
                add(Triple("Sale price", "${InputGuards.fmtNum(rec.sp)}/kg", accents.emerald))
                add(Triple("Total cost", money(rec.totalCost), accents.danger))
                add(Triple("Total value", money(rec.totalSale), accents.emerald))
            }
            if (rec.formulaUnits > 0.0) add(Triple("Formula units", InputGuards.fmtNum(rec.formulaUnits), accents.emerald))
            if (!masked && rec.formulaCost > 0.0) add(Triple("Formula cost", money(rec.formulaCost), accents.danger))
        }
    }
    val note = rec.returnNote.ifBlank { rec.transferNote }

    SarimCard(
        accentBorder = if (isSelectedDate) accents.emerald.copy(alpha = 0.45f) else null,
        contentPadding = PaddingValues(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        modifier = Modifier.testTag("prod_hist_card_${rec.id}")
    ) {
        Column(
            modifier = Modifier.drawBehind {
                // web `.hc-card::before` — 3dp tone bar on the left edge
                drawRoundRect(
                    color = tone.copy(alpha = 0.85f),
                    topLeft = Offset(-15.dp.toPx(), 2.dp.toPx()),
                    size = Size(3.dp.toPx(), (size.height - 4.dp.toPx()).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
            }
        ) {
            // ---- head (tap to expand/collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleExpand)
                    .testTag("prod_hist_head_${rec.id}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasPhotos) {
                    Checkbox(
                        checked = isPhotoSelected,
                        onCheckedChange = onTogglePhotoSelect,
                        modifier = Modifier.testTag("prod_photo_check_${rec.id}")
                    )
                }
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(tone.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(leadIcon, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        EditedBadge(rec.isEdited)
                    }
                    val dateSub = buildString {
                        append(DateUtils.formatPretty(rec.date))
                        if (rec.time.isNotBlank()) append(" ${rec.time}")
                        if (isSelectedDate) append(" (Selected)")
                    }
                    Text(dateSub, style = MaterialTheme.typography.labelSmall, color = accents.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = InputGuards.formatKg(abs(rec.net)),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (isOut) accents.danger else accents.emerald,
                        maxLines = 1
                    )
                    if (!(masked && !rec.isReturn && !rec.isTransfer)) {
                        Text(
                            text = tagText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = tagColor,
                            maxLines = 1
                        )
                    }
                }
            }

            // ---- body (web `.hc-body`)
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))

                    // chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        if (showStoreChip && storeLabel.isNotBlank()) HistoryChip(storeLabel, accents.cyan)
                        if (rec.managedBy.isNotBlank()) HistoryChip(rec.managedBy, accents.gold)
                        if (rec.createdBy.isNotBlank() && !rec.createdBy.contains("-") && rec.createdBy.length < 32) HistoryChip(rec.createdBy, accents.purple)
                        if (rec.isMerged) HistoryChip("Merged", accents.gold)
                    }

                    // metrics grid: two columns, Net profit spans the full width
                    if (metrics.isNotEmpty() || (!rec.isReturn && !rec.isTransfer && !masked)) {
                        Spacer(Modifier.height(8.dp))
                        metrics.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { (label, value, color) -> HistoryMetricTile(label, value, color, Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        if (!rec.isReturn && !rec.isTransfer && !masked) {
                            HistoryMetricTile(
                                "Net profit", money(rec.profit),
                                if (rec.profit >= 0.0) accents.emerald else accents.danger,
                                Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (note.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Note: $note", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    }

                    // photo strip (web `prodPhotoStripHtml`)
                    if (hasPhotos) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            recPhotos.forEachIndexed { idx, uri ->
                                DecodedDataUrlImage(
                                    dataUrl = uri,
                                    contentDescription = "Product photo ${idx + 1}",
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onOpenLightbox(recPhotos, idx) }
                                        .testTag("prod_hist_photo_${rec.id}_$idx")
                                )
                            }
                        }
                    }

                    // actions row (web `.tbl-action-row`: Edit + Delete)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HistoryActionButton("Details", Icons.Default.Info, accents.textMuted, Modifier.weight(1f), "prod_hist_details_${rec.id}", onOpenDetails)
                        if (canEdit) {
                            HistoryActionButton("Edit", Icons.Default.Edit, accents.emerald, Modifier.weight(1f), "prod_hist_edit_${rec.id}") { onEdit?.invoke() }
                        }
                        if (canDelete) {
                            HistoryActionButton(
                                if (rec.isTransfer) "Recycle" else "Delete", Icons.Default.Delete, accents.danger,
                                Modifier.weight(1f), "prod_hist_delete_${rec.id}", onDelete
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun HistoryMetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val accents = LocalSarimAccents.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accents.inputFill.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = accents.textMuted, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1)
    }
}

@Composable
private fun HistoryActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier.testTag(tag)
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = color, maxLines = 1)
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
    grossKg: Double = 0.0,
    containerKg: Double = 0.0,
    units: Double = 0.0,
    gated: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Same structure as web confirmGuard: "Save Production Entry?" + detail lines + tail sentence.
    val details = buildList {
        add("Store" to storeName)
        add("Date" to DateUtils.formatPretty(dateIso))
        if (grossKg > 0.0) add("Gross Weight" to InputGuards.fmtNum(grossKg))
        if (grossKg > 0.0) add("Container" to InputGuards.fmtNum(containerKg))
        add("Net Weight" to InputGuards.fmtNum(netKg))
        if (units > 0.0) add("Units" to InputGuards.fmtNum(units))
        if (!hideMoneyPanels) {
            add("Total Cost" to InputGuards.formatMoney(totalCost, currency, masked))
            add("Net Profit" to InputGuards.formatMoney(totalSale - totalCost, currency, masked))
        }
    }
    SarimConfirmDialog(
        title = confirmGuardTitle("Production Entry", isUpdate = false, gated = gated),
        message = confirmGuardMessage(details, isUpdate = false, gated = gated),
        confirmText = confirmGuardConfirmWord(isUpdate = false, gated = gated),
        cancelText = "Cancel",
        tone = DialogTone.Primary,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmTag = "prod_confirm_save_btn"
    )
}
