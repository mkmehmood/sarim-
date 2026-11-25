package com.gullzubair.sarim.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.StoreConfig
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.RepSalesAnalyticsEngine
import com.gullzubair.sarim.ui.theme.CinzelFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun AdminRepViewCard(
    availableReps: List<String>,
    selectedRep: String,
    onSelectRep: (String) -> Unit,
    dateIso: String,
    onDateChange: (String) -> Unit,
    routeStops: List<RepSalesAnalyticsEngine.RepRouteStop>
) {
    val accents = LocalSarimAccents.current
    val context = LocalContext.current

    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Text(
            text = "ADMIN VIEW & DAILY ROUTE",
            fontFamily = CinzelFont,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = accents.cyan
        )
        Spacer(Modifier.height(10.dp))
        PickerField(
            label = "View For Rep",
            selectedLabel = selectedRep,
            items = availableReps.ifEmpty { listOf(selectedRep) },
            itemLabel = { it },
            onSelect = onSelectRep,
            testTag = "admin_rep_picker"
        )
        Spacer(Modifier.height(10.dp))
        DatePickerField("Select Date", dateIso, onDateChange, "admin_rep_date")

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GPS Route (${routeStops.size} stop${if (routeStops.size == 1) "" else "s"})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RouteLegendDot("Cash", accents.cyan)
                RouteLegendDot("Collect", accents.emerald)
                RouteLegendDot("Credit", accents.gold)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (routeStops.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accents.inputFill,
                border = BorderStroke(1.dp, accents.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No GPS coordinates recorded for $selectedRep on ${DateUtils.formatPretty(dateIso)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = accents.textMuted,
                    modifier = Modifier.padding(14.dp)
                )
            }
        } else {
            val gridColor = accents.borderSubtle
            val pathColor = accents.cyan.copy(alpha = 0.7f)
            val cashColor = accents.cyan
            val collColor = accents.emerald
            val creditColor = accents.gold
            val otherColor = accents.purple

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accents.inputFill,
                border = BorderStroke(1.dp, accents.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(16.dp)
                ) {
                    val lats = routeStops.map { it.lat }
                    val lngs = routeStops.map { it.lng }
                    val minLat = lats.minOrNull() ?: 0.0
                    val maxLat = lats.maxOrNull() ?: 0.0
                    val minLng = lngs.minOrNull() ?: 0.0
                    val maxLng = lngs.maxOrNull() ?: 0.0
                    val spanLat = max(0.001, maxLat - minLat)
                    val spanLng = max(0.001, maxLng - minLng)

                    // Subtle grid lines
                    for (i in 1..3) {
                        val y = size.height * i / 4f
                        drawLine(gridColor.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), 1f)
                        val x = size.width * i / 4f
                        drawLine(gridColor.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, size.height), 1f)
                    }

                    val pts = routeStops.mapIndexed { idx, s ->
                        val nx = if (maxLng - minLng < 1e-6) {
                            if (routeStops.size == 1) 0.5f else (idx.toFloat() / (routeStops.size - 1).coerceAtLeast(1))
                        } else {
                            ((s.lng - minLng) / spanLng).toFloat().coerceIn(0.05f, 0.95f)
                        }
                        val ny = if (maxLat - minLat < 1e-6) {
                            0.5f
                        } else {
                            (1f - ((s.lat - minLat) / spanLat).toFloat()).coerceIn(0.08f, 0.92f)
                        }
                        Offset(nx * size.width, ny * size.height)
                    }

                    if (pts.size > 1) {
                        val p = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            pts.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = p,
                            color = pathColor,
                            style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                        )
                    }

                    pts.forEachIndexed { idx, pt ->
                        val stop = routeStops[idx]
                        val pinColor = when (stop.kind) {
                            "CASH" -> cashColor
                            "COLLECTION" -> collColor
                            "CREDIT" -> creditColor
                            else -> otherColor
                        }
                        drawCircle(color = pinColor.copy(alpha = 0.24f), radius = 18f, center = pt)
                        drawCircle(color = pinColor, radius = 10f, center = pt)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                routeStops.forEachIndexed { idx, stop ->
                    val kindColor = when (stop.kind) {
                        "CASH" -> accents.cyan
                        "COLLECTION" -> accents.emerald
                        "CREDIT" -> accents.gold
                        else -> accents.purple
                    }
                    Surface(
                        onClick = {
                            val uri = Uri.parse("geo:${stop.lat},${stop.lng}?q=${stop.lat},${stop.lng}(${Uri.encode(stop.label)})")
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = accents.inputFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.fillMaxWidth().testTag("rep_route_stop_$idx")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier.size(24.dp).clip(CircleShape).background(kindColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${idx + 1}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = kindColor)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(stop.label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val distStr = stop.distanceFromPrevMeters?.let { m ->
                                        if (m >= 1000.0) " • +${InputGuards.fmtNum(m / 1000.0)} km" else " • +${m.roundToInt()} m"
                                    } ?: ""
                                    Text(
                                        text = "${stop.detail} • ${stop.timeLabel}$distStr",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accents.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(Icons.Default.LocationOn, contentDescription = "Open in Maps", tint = kindColor, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteLegendDot(label: String, color: Color) {
    val accents = LocalSarimAccents.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
    }
}

@Composable
fun AdminRepAnalyticsCard(
    activeRepProfile: String,
    analytics: RepSalesAnalyticsEngine.RepPeriodAnalytics,
    selectedPeriod: RepSalesAnalyticsEngine.RepAnalyticsPeriod,
    onSelectPeriod: (RepSalesAnalyticsEngine.RepAnalyticsPeriod) -> Unit,
    currency: String,
    masked: Boolean
) {
    val accents = LocalSarimAccents.current
    val totalVolume = max(1.0, analytics.collections + analytics.cashSales + analytics.creditSales)

    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REP ANALYTICS",
                fontFamily = CinzelFont,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = activeRepProfile,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = accents.emerald
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RepSalesAnalyticsEngine.RepAnalyticsPeriod.entries.forEach { p ->
                val selected = selectedPeriod == p
                Surface(
                    onClick = { onSelectPeriod(p) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) accents.softPillFill else accents.inputFill,
                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                    modifier = Modifier.defaultMinSize(minHeight = 36.dp).testTag("rep_analytics_period_${p.key}")
                ) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                        Text(p.label, style = MaterialTheme.typography.labelMedium, color = if (selected) accents.emerald else accents.textMuted)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = accents.softPillFill,
            border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MONEY IN (${selectedPeriod.label.uppercase()})", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                    Text("Collections + Cash sales", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                }
                Text(
                    text = InputGuards.formatMoney(analytics.moneyIn, currency, masked),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = accents.emerald
                )
            }
        }

        // Proportional breakdown bar
        if (!masked && (analytics.collections + analytics.cashSales + analytics.creditSales) > 0.0) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)).background(accents.inputFill)
            ) {
                val cFrac = (analytics.collections / totalVolume).toFloat().coerceAtLeast(0f)
                val cashFrac = (analytics.cashSales / totalVolume).toFloat().coerceAtLeast(0f)
                val crFrac = (analytics.creditSales / totalVolume).toFloat().coerceAtLeast(0f)
                if (cFrac > 0.001f) Box(Modifier.weight(cFrac).fillMaxHeight().background(accents.emerald))
                if (cashFrac > 0.001f) Box(Modifier.weight(cashFrac).fillMaxHeight().background(accents.cyan))
                if (crFrac > 0.001f) Box(Modifier.weight(crFrac).fillMaxHeight().background(accents.gold))
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RepStatBox("Collections", InputGuards.formatMoney(analytics.collections, currency, masked), accents.emerald, Modifier.weight(1f))
            RepStatBox("Cash Sales", InputGuards.formatMoney(analytics.cashSales, currency, masked), accents.cyan, Modifier.weight(1f))
            RepStatBox("Credit Sales", InputGuards.formatMoney(analytics.creditSales, currency, masked), accents.gold, Modifier.weight(1f))
        }
    }
}

@Composable
fun RepCustomersDirectoryCard(
    directory: RepSalesAnalyticsEngine.RepClientDirectorySummary,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    currency: String,
    masked: Boolean,
    onExportPdf: () -> Unit,
    onAddCustomer: () -> Unit,
    onOpenCustomer: (String) -> Unit,
    onEditCustomer: (String) -> Unit
) {
    val accents = LocalSarimAccents.current
    val context = LocalContext.current

    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CUSTOMER LIST",
                    fontFamily = CinzelFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(shape = RoundedCornerShape(10.dp), color = accents.softPillFill) {
                    Text(
                        text = "${directory.totalCount}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accents.emerald,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onExportPdf,
                    modifier = Modifier.size(48.dp).testTag("rep_export_clients_pdf")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Export rep clients PDF", tint = accents.cyan)
                }
                Surface(
                    onClick = onAddCustomer,
                    shape = RoundedCornerShape(12.dp),
                    color = accents.softPillFill,
                    border = BorderStroke(1.dp, accents.emerald),
                    modifier = Modifier.size(44.dp).testTag("rep_add_client_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = "Add Rep Customer", tint = accents.emerald, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SarimPillInput(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = "Search rep customers, phone, page...",
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            testTag = "rep_customer_search"
        )

        Spacer(Modifier.height(10.dp))
        if (directory.items.isEmpty()) {
            Text(
                text = if (searchQuery.isBlank()) "No customers for this rep yet." else "No matching customers.",
                style = MaterialTheme.typography.bodySmall,
                color = accents.textMuted,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                directory.items.forEachIndexed { idx, item ->
                    val initials = item.name.split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("") { it.first().uppercaseChar().toString() }
                        .ifBlank { "C" }
                    val isSettled = abs(item.debt) < 0.01
                    val debtColor = when {
                        isSettled -> accents.emerald
                        item.debt < 0.0 -> accents.cyan
                        else -> accents.gold
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = accents.inputFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCustomer(item.name) }
                            .testTag("rep_client_card_$idx")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!item.photoUri.isNullOrBlank()) {
                                DecodedDataUrlImage(
                                    dataUrl = item.photoUri,
                                    contentDescription = "${item.name} photo",
                                    modifier = Modifier.size(40.dp).clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(accents.softPillFill),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(initials, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (item.ledgerPage.isNotBlank()) {
                                        Surface(shape = RoundedCornerShape(6.dp), color = accents.cyan.copy(alpha = 0.14f)) {
                                            Text(
                                                text = "P.${item.ledgerPage}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = accents.cyan,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    if (item.locationConfirmed) {
                                        Icon(Icons.Default.LocationOn, contentDescription = "Verified location", tint = accents.emerald, modifier = Modifier.size(13.dp))
                                    }
                                }
                                Text(
                                    text = listOfNotNull(
                                        item.phone.takeIf { it.isNotBlank() } ?: "No phone",
                                        "${item.visitCount} visits",
                                        item.quantity.takeIf { it > 0.0 }?.let { InputGuards.formatKg(it) }
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accents.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when {
                                    masked -> "••••"
                                    isSettled -> "Settled"
                                    item.debt < 0.0 -> "${InputGuards.formatMoney(-item.debt, currency, false)} CR"
                                    else -> InputGuards.formatMoney(item.debt, currency, false)
                                },
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = debtColor
                            )
                            if (item.phone.isNotBlank()) {
                                IconButton(
                                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.phone}"))) } },
                                    modifier = Modifier.size(48.dp).testTag("rep_call_btn_$idx")
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Call ${item.name}", tint = accents.emerald, modifier = Modifier.size(18.dp))
                                }
                            }
                            IconButton(
                                onClick = { onEditCustomer(item.name) },
                                modifier = Modifier.size(48.dp).testTag("rep_edit_client_btn_$idx")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit ${item.name}", tint = accents.textMuted, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RepStatBox("Outstanding", InputGuards.formatMoney(directory.totalOutstandingCredit, currency, masked), accents.gold, Modifier.weight(1f))
            RepStatBox("Credit Sales", InputGuards.formatMoney(directory.totalCreditSales, currency, masked), accents.cyan, Modifier.weight(1f))
            RepStatBox("Collections", InputGuards.formatMoney(directory.totalCollections, currency, masked), accents.emerald, Modifier.weight(1f))
        }
    }
}

@Composable
fun RepHistoryRowCard(
    r: SalesRecord,
    stores: List<StoreConfig>,
    runningBalanceAfter: Double?,
    isSelectedDate: Boolean,
    currency: String,
    masked: Boolean,
    onClick: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val storeLabel = FormulaEngine.storeLabelFor(r.supplyStore, stores)
    val isOldDebt = r.transactionType == "OLD_DEBT"
    val isRepRet = r.isRepReturn || r.paymentType == "REP_RETURN"
    val isColl = FinanceEngine.isCollectionRow(r)
    val isMoneyOut = r.paymentType == "MONEY_OUT"

    val badgeLabel = when {
        isOldDebt -> "OLD DEBT"
        isRepRet -> "REP RETURN"
        isColl -> "COLLECTION"
        isMoneyOut -> "MONEY OUT"
        r.paymentType == "CREDIT" && r.creditReceived -> "SETTLED"
        r.paymentType == "CREDIT" -> "CREDIT"
        else -> "CASH"
    }
    val badgeColor = when {
        isOldDebt || (r.paymentType == "CREDIT" && !r.creditReceived) || isMoneyOut -> accents.gold
        isRepRet || r.paymentType == "CASH" -> accents.cyan
        else -> accents.emerald
    }
    val displayAmount = when {
        isRepRet -> r.returnValue
        isColl -> if (r.collectedAmount > 0.0) r.collectedAmount else r.totalValue
        else -> r.totalValue
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelectedDate) accents.cyan.copy(alpha = 0.08f) else accents.inputFill,
        border = BorderStroke(1.dp, if (isSelectedDate) accents.cyan.copy(alpha = 0.6f) else accents.borderSubtle),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("rep_hist_row_${r.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = r.customerName.uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(shape = RoundedCornerShape(6.dp), color = badgeColor.copy(alpha = 0.15f)) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    EditedBadge(r.isEdited)
                    if (r.gpsLat != null && r.gpsLng != null) {
                        Icon(Icons.Default.LocationOn, contentDescription = "GPS tagged", tint = accents.cyan, modifier = Modifier.size(13.dp))
                    }
                }
                val meta = buildList {
                    if (storeLabel.isNotBlank()) add(storeLabel)
                    if (r.time.isNotBlank()) add(r.time)
                    if (r.usedInCalcId.isNotBlank()) add("Reconciled")
                    when {
                        isRepRet -> {
                            if (r.returnedQty > 0.0) add("Ret ${InputGuards.formatKg(r.returnedQty)}")
                            if (r.expiredQty > 0.0) add("Exp ${InputGuards.formatKg(r.expiredQty)}")
                        }
                        isColl -> {
                            if (r.expiredQty > 0.0) add("Exp ${InputGuards.formatKg(r.expiredQty)} -> CHORA")
                        }
                        r.quantity > 0.0 -> {
                            add("${InputGuards.formatKg(r.quantity)} @ ${InputGuards.formatMoney(r.unitPrice, currency, masked)}")
                            if (r.partialPaymentReceived > 0.0 && !r.creditReceived) {
                                add("Paid ${InputGuards.formatMoney(r.partialPaymentReceived, currency, masked)}")
                            }
                        }
                    }
                }
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = accents.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = InputGuards.formatMoney(displayAmount, currency, masked),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = badgeColor
                )
                if (runningBalanceAfter != null) {
                    Text(
                        text = "Bal: ${InputGuards.formatMoney(runningBalanceAfter, currency, masked)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = accents.textMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun RepStatBox(
    label: String,
    value: String,
    toneColor: Color,
    modifier: Modifier = Modifier
) {
    val accents = LocalSarimAccents.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accents.inputFill,
        border = BorderStroke(1.dp, accents.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = accents.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = toneColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
