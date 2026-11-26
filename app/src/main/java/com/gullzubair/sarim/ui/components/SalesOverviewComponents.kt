package com.gullzubair.sarim.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.gullzubair.sarim.domain.SalesAnalyticsEngine
import com.gullzubair.sarim.ui.theme.CinzelFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import android.content.Intent
import android.net.Uri
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun CustomersDirectoryCard(
    directory: SalesAnalyticsEngine.CustomerDirectorySummary,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    currency: String,
    masked: Boolean,
    onExportAllPdf: () -> Unit,
    onAddCustomer: () -> Unit,
    onOpenCustomerDetail: (String) -> Unit,
    onEditCustomer: (String) -> Unit
) {
    val accents = LocalSarimAccents.current
    val context = androidx.compose.ui.platform.LocalContext.current
    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "CUSTOMERS",
                    fontFamily = CinzelFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accents.softPillFill
                ) {
                    Text(
                        text = "${directory.totalCount} customers",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accents.emerald,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onExportAllPdf,
                    modifier = Modifier.size(48.dp).testTag("sales_export_all_customers_pdf")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = "Download All Customers PDF",
                        tint = accents.cyan
                    )
                }
                Surface(
                    onClick = onAddCustomer,
                    shape = RoundedCornerShape(16.dp),
                    color = accents.softPillFill,
                    border = BorderStroke(1.dp, accents.emerald),
                    modifier = Modifier.size(48.dp).testTag("sales_add_customer_btn")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add New Customer",
                            tint = accents.emerald,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        SarimPillInput(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = "Search customers...",
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            testTag = "sales_customer_search"
        )

        Spacer(Modifier.height(10.dp))
        if (directory.items.isEmpty()) {
            Text(
                text = if (searchQuery.isBlank()) "No customers yet." else "No matching customers.",
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
                    val isSettled = abs(item.credit) < 0.01
                    val creditColor = when {
                        isSettled -> accents.emerald
                        item.credit < 0.0 -> accents.cyan
                        else -> accents.gold
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = accents.inputFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCustomerDetail(item.name) }
                            .testTag("customer_card_${idx}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(accents.softPillFill),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = accents.emerald
                                    )
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
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = accents.cyan.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = "P.${item.ledgerPage}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = accents.cyan,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                if (item.phone.isNotBlank()) {
                                    Text(
                                        text = item.phone,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accents.cyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + item.phone.filter { it.isDigit() || it == '+' }))
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = InputGuards.formatMoney(if (isSettled) 0.0 else item.credit, currency, masked),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = creditColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SalesKpiTile(
                label = "Outstanding credit",
                value = InputGuards.formatMoney(directory.totalOutstandingCredit, currency, masked),
                toneColor = if (directory.totalOutstandingCredit > 0.01) accents.gold else accents.emerald,
                modifier = Modifier.weight(1f)
            )
            SalesKpiTile(
                label = "Quantity sold",
                value = InputGuards.formatKg(directory.totalSoldKg),
                toneColor = accents.cyan,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SalesAnalyticsCard(
    chartData: SalesAnalyticsEngine.SalesChartData,
    selectedPeriod: SalesAnalyticsEngine.SalesChartPeriod,
    onSelectPeriod: (SalesAnalyticsEngine.SalesChartPeriod) -> Unit,
    customerFilter: String,
    currency: String,
    masked: Boolean
) {
    val accents = LocalSarimAccents.current
    var activeBarIdx by remember(chartData.labels, selectedPeriod) { mutableStateOf<Int?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SarimCard(contentPadding = PaddingValues(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Analytics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                if (customerFilter.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(10.dp), color = accents.softPillFill) {
                        Text(
                            text = customerFilter.trim(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accents.emerald,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SalesAnalyticsEngine.SalesChartPeriod.entries.forEach { p ->
                    val selected = p == selectedPeriod
                    Surface(
                        onClick = { onSelectPeriod(p) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) accents.softPillFill else accents.inputFill,
                        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 38.dp)
                            .testTag("sales_chart_period_${p.key}")
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = p.label,
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

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accents.emerald))
                    Text("Cash (incl. received)", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accents.gold))
                    Text("Pending Credit", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                }
            }

            Spacer(Modifier.height(10.dp))
            val totals = remember(chartData) {
                chartData.labels.indices.map { i ->
                    chartData.cashValues.getOrElse(i) { 0.0 } + chartData.creditValues.getOrElse(i) { 0.0 }
                }
            }
            if (chartData.labels.isEmpty() || totals.all { it <= 0.0 }) {
                Text("No sales recorded in this period.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            } else {
                val maxTotal = totals.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .pointerInput(chartData.labels.size) {
                            detectTapGestures { offset ->
                                if (size.width > 0 && chartData.labels.isNotEmpty()) {
                                    val idx = ((offset.x / size.width.toFloat()) * chartData.labels.size)
                                        .toInt().coerceIn(0, chartData.labels.lastIndex)
                                    activeBarIdx = if (activeBarIdx == idx) null else idx
                                }
                            }
                        }
                ) {
                    val count = chartData.labels.size.coerceAtLeast(1)
                    val slotW = size.width / count
                    val barW = (slotW * 0.6f).coerceIn(5f, 44f)
                    val baselineY = size.height - 6f
                    val drawH = baselineY - 8f
                    drawLine(
                        color = accents.borderSubtle,
                        start = Offset(0f, baselineY),
                        end = Offset(size.width, baselineY),
                        strokeWidth = 2f
                    )
                    chartData.labels.indices.forEach { idx ->
                        val cashV = max(0.0, chartData.cashValues.getOrElse(idx) { 0.0 })
                        val credV = max(0.0, chartData.creditValues.getOrElse(idx) { 0.0 })
                        val cashH = ((cashV / maxTotal).toFloat() * drawH).coerceAtLeast(if (cashV > 0.0) 4f else 0f)
                        val credH = ((credV / maxTotal).toFloat() * drawH).coerceAtLeast(if (credV > 0.0) 4f else 0f)
                        val left = idx * slotW + (slotW - barW) / 2f
                        val alpha = if (activeBarIdx == null || activeBarIdx == idx) 0.92f else 0.45f
                        if (cashH > 0f) {
                            drawRoundRect(
                                color = accents.emerald.copy(alpha = alpha),
                                topLeft = Offset(left, baselineY - cashH),
                                size = Size(barW, cashH),
                                cornerRadius = CornerRadius(5f, 5f)
                            )
                        }
                        if (credH > 0f) {
                            drawRoundRect(
                                color = accents.gold.copy(alpha = alpha),
                                topLeft = Offset(left, baselineY - cashH - credH),
                                size = Size(barW, credH),
                                cornerRadius = CornerRadius(5f, 5f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                val stride = when {
                    chartData.labels.size > 20 -> 5
                    chartData.labels.size > 12 -> 3
                    else -> 1
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    chartData.labels.forEachIndexed { idx, lbl ->
                        if (idx % stride == 0 || idx == chartData.labels.lastIndex) {
                            Text(lbl, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = accents.textMuted)
                        }
                    }
                }

                activeBarIdx?.let { idx ->
                    if (idx in chartData.labels.indices) {
                        val c = chartData.cashValues.getOrElse(idx) { 0.0 }
                        val cr = chartData.creditValues.getOrElse(idx) { 0.0 }
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = accents.inputFill,
                            border = BorderStroke(1.dp, accents.borderSubtle),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(chartData.labels[idx], style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = accents.cyan)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Cash", style = MaterialTheme.typography.bodySmall, color = accents.emerald)
                                    Text(InputGuards.formatMoney(c, currency, masked), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Pending Credit", style = MaterialTheme.typography.bodySmall, color = accents.gold)
                                    Text(InputGuards.formatMoney(cr, currency, masked), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }
        }

        val pieTotal = InputGuards.round2(chartData.totalCash + chartData.totalCredit)
        OverviewDonutSummaryCard(
            title = "Total: ${InputGuards.formatMoney(pieTotal, currency, masked)} - ${selectedPeriod.label}",
            items = listOf(
                "Cash" to chartData.totalCash,
                "Pending Credit" to chartData.totalCredit
            ),
            currency = currency,
            masked = masked
        )
    }
}

@Composable
fun SalesSummaryCard(
    summary: SalesAnalyticsEngine.SalesPeriodSummary,
    selectedPeriod: SalesAnalyticsEngine.SalesSummaryPeriod,
    onSelectPeriod: (SalesAnalyticsEngine.SalesSummaryPeriod) -> Unit,
    customerFilter: String,
    currency: String,
    masked: Boolean
) {
    val accents = LocalSarimAccents.current
    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            if (customerFilter.isNotBlank()) {
                Surface(shape = RoundedCornerShape(10.dp), color = accents.softPillFill) {
                    Text(
                        text = customerFilter.trim(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accents.emerald,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SalesAnalyticsEngine.SalesSummaryPeriod.entries.forEach { p ->
                val selected = p == selectedPeriod
                Surface(
                    onClick = { onSelectPeriod(p) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) accents.softPillFill else accents.inputFill,
                    border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 38.dp)
                        .testTag("sales_summary_period_${p.key}")
                ) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = p.label,
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

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedPeriod.title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = accents.textMuted
            )
            summary.marginPercent?.let { m ->
                val chipColor = if (m >= 0) accents.emerald else accents.danger
                Surface(shape = RoundedCornerShape(10.dp), color = chipColor.copy(alpha = 0.14f)) {
                    Text(
                        text = "$m% margin",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = chipColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = InputGuards.formatMoney(summary.totalValue, currency, masked),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Total value (${InputGuards.formatKg(summary.quantityKg)})",
            style = MaterialTheme.typography.bodySmall,
            color = accents.textMuted
        )

        Spacer(Modifier.height(10.dp))
        val cashV = max(0.0, summary.cashAmount)
        val credV = max(0.0, summary.pendingCreditAmount)
        val totalSplit = cashV + credV
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accents.inputFill)
        ) {
            if (totalSplit > 0.0) {
                if (cashV > 0.0) {
                    Box(Modifier.weight(max(0.02f, (cashV / totalSplit).toFloat())).fillMaxHeight().background(accents.emerald))
                }
                if (credV > 0.0) {
                    Box(Modifier.weight(max(0.02f, (credV / totalSplit).toFloat())).fillMaxHeight().background(accents.gold))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val cashPct = if (totalSplit > 0.0) ((cashV / totalSplit) * 100.0).roundToInt() else 0
        val credPct = if (totalSplit > 0.0) 100 - cashPct else 0
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Cash $cashPct%", style = MaterialTheme.typography.labelSmall, color = accents.emerald)
            Text("Credit $credPct%", style = MaterialTheme.typography.labelSmall, color = accents.gold)
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SalesKpiTile("Quantity", InputGuards.formatKg(summary.quantityKg), accents.cyan, Modifier.weight(1f))
            SalesKpiTile(
                "Net profit",
                InputGuards.formatMoney(summary.netProfit, currency, masked),
                if (summary.netProfit >= 0.0) accents.emerald else accents.danger,
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SalesKpiTile(
                "Cash (incl. received)",
                InputGuards.formatMoney(summary.cashAmount, currency, masked),
                accents.emerald,
                Modifier.weight(1f)
            )
            SalesKpiTile(
                "Pending credits",
                InputGuards.formatMoney(summary.pendingCreditAmount, currency, masked),
                accents.gold,
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SalesHistoryRowCard(
    s: SalesRecord,
    stores: List<StoreConfig>,
    runningBalanceAfter: Double?,
    isSelectedDate: Boolean,
    currency: String,
    masked: Boolean,
    onClick: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val storeLabel = FormulaEngine.storeLabelFor(s.supplyStore, stores)
    val isOldDebt = s.transactionType == "OLD_DEBT"
    val isRepRet = s.isRepReturn || s.paymentType == "REP_RETURN"
    val isColl = FinanceEngine.isCollectionRow(s)
    val isMoneyOut = s.paymentType == "MONEY_OUT"

    val typeBadgeLabel = when {
        isOldDebt -> "OLD DEBT"
        isRepRet -> "REP RETURN"
        isColl -> "COLLECTION"
        isMoneyOut -> "PAYOUT"
        s.paymentType == "CREDIT" && s.creditReceived -> "RECEIVED"
        s.paymentType == "CREDIT" -> "CREDIT"
        else -> "CASH"
    }
    val typeBadgeColor = when {
        isOldDebt || (s.paymentType == "CREDIT" && !s.creditReceived) || isMoneyOut -> accents.gold
        isRepRet -> accents.cyan
        else -> accents.emerald
    }
    val displayAmount = when {
        isRepRet -> s.returnValue
        isColl -> if (s.collectedAmount > 0.0) s.collectedAmount else s.totalValue
        else -> s.totalValue
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelectedDate) accents.cyan.copy(alpha = 0.08f) else accents.inputFill,
        border = BorderStroke(1.dp, if (isSelectedDate) accents.cyan.copy(alpha = 0.65f) else accents.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("sales_hist_row_${s.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = s.customerName.uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(shape = RoundedCornerShape(6.dp), color = typeBadgeColor.copy(alpha = 0.14f)) {
                        Text(
                            text = typeBadgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = typeBadgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    EditedBadge(s.isEdited)
                    if (isSelectedDate) {
                        Surface(shape = RoundedCornerShape(6.dp), color = accents.cyan.copy(alpha = 0.16f)) {
                            Text(
                                text = "Selected",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = accents.cyan,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                val metaParts = buildList {
                    if (storeLabel.isNotBlank()) add(storeLabel)
                    if (s.salesRep.isNotBlank() && !s.salesRep.equals("NONE", true)) add("Rep: ${s.salesRep}")
                    if (s.isTransfer || s.isRepTransfer) add("Transfer")
                    if (s.supplyDate.isNotBlank() && s.supplyDate != s.date) add("Supply: ${DateUtils.formatPretty(s.supplyDate)}")
                    when {
                        isRepRet -> {
                            if (s.returnedQty > 0.0) add("Ret ${InputGuards.formatKg(s.returnedQty)}")
                            if (s.expiredQty > 0.0) add("Exp ${InputGuards.formatKg(s.expiredQty)}")
                        }
                        isColl -> {
                            if (s.expiredQty > 0.0) add("Exp ${InputGuards.formatKg(s.expiredQty)} -> CHORA")
                        }
                        s.quantity > 0.0 -> {
                            add("${InputGuards.formatKg(s.quantity)} @ ${InputGuards.formatMoney(s.unitPrice, currency, masked)}")
                            if (s.partialPaymentReceived > 0.0 && !s.creditReceived) {
                                add("Paid ${InputGuards.formatMoney(s.partialPaymentReceived, currency, masked)}")
                            }
                        }
                    }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = accents.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = InputGuards.formatMoney(displayAmount, currency, masked),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = typeBadgeColor
                )
                if (s.paymentType == "CREDIT" && s.creditReceived) {
                    Text("Settled", style = MaterialTheme.typography.labelSmall, color = accents.emerald)
                }
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
private fun SalesKpiTile(
    label: String,
    value: String,
    toneColor: Color,
    modifier: Modifier = Modifier
) {
    val accents = LocalSarimAccents.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = accents.inputFill,
        border = BorderStroke(1.dp, accents.borderSubtle)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accents.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = toneColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
