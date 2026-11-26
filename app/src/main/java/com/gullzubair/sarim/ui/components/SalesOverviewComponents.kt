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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import com.gullzubair.sarim.ui.theme.PlayfairFont
import android.content.Intent
import android.net.Uri
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val customerAvatarBgDark = listOf(
    Color(0xFF1A1F3C),
    Color(0xFF1D2246),
    Color(0xFF112A36),
    Color(0xFF2E162B),
    Color(0xFF122C38),
    Color(0xFF132D2E)
)
private val customerAvatarFgDark = listOf(
    Color(0xFF93A5FF),
    Color(0xFFA5B4FC),
    Color(0xFF67E8F9),
    Color(0xFFF472B6),
    Color(0xFF7DD3FC),
    Color(0xFF5EEAD4)
)

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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "CUSTOMERS",
                fontFamily = CinzelFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
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
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.5.sp
                        ),
                        cursorBrush = SolidColor(accents.emerald),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sales_customer_search"),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search customers...",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = accents.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    )
                }
            }
            Text(
                text = "${directory.totalCount} active",
                style = MaterialTheme.typography.labelSmall,
                color = accents.textMuted,
                maxLines = 1
            )
            Surface(
                onClick = onExportAllPdf,
                shape = CircleShape,
                color = accents.softPillFill,
                border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.55f)),
                modifier = Modifier
                    .size(40.dp)
                    .testTag("sales_export_all_customers_pdf")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = "Download All Customers PDF",
                        tint = accents.emerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Surface(
                onClick = onAddCustomer,
                shape = CircleShape,
                color = accents.softPillFill,
                border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.55f)),
                modifier = Modifier
                    .size(40.dp)
                    .testTag("sales_add_customer_btn")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PersonAdd,
                        contentDescription = "Add New Customer",
                        tint = accents.emerald,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (directory.items.isEmpty()) {
            Text(
                text = if (searchQuery.isBlank()) "No customers yet." else "No matching customers.",
                style = MaterialTheme.typography.bodySmall,
                color = accents.textMuted,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = accents.inputFill,
                border = BorderStroke(1.dp, accents.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp)
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
                                else -> accents.danger
                            }
                            val bgTint = customerAvatarBgDark[idx % customerAvatarBgDark.size]
                            val fgTint = customerAvatarFgDark[idx % customerAvatarFgDark.size]
                            if (idx > 0) {
                                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.45f))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 60.dp)
                                    .clickable { onOpenCustomerDetail(item.name) }
                                    .padding(vertical = 10.dp)
                                    .testTag("customer_card_${idx}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(44.dp)) {
                                    if (!item.photoUri.isNullOrBlank()) {
                                        DecodedDataUrlImage(
                                            dataUrl = item.photoUri,
                                            contentDescription = "${item.name} photo",
                                            modifier = Modifier.size(40.dp).clip(CircleShape).align(Alignment.Center)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(bgTint)
                                                .align(Alignment.Center),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = initials,
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = fgTint
                                            )
                                        }
                                    }
                                    if (item.ledgerPage.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, accents.emerald),
                                            modifier = Modifier.align(Alignment.BottomEnd)
                                        ) {
                                            Text(
                                                text = item.ledgerPage,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                color = accents.emerald,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = item.name.uppercase(),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.5.sp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.phone.isNotBlank()) {
                                        Text(
                                            text = item.phone,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Medium,
                                                textDecoration = TextDecoration.Underline
                                            ),
                                            color = accents.emerald,
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
                                    } else {
                                        Text(
                                            text = "No phone",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accents.textMuted,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = InputGuards.formatMoney(if (isSettled) 0.0 else item.credit, currency, masked),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                                    color = creditColor
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.65f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text("Outstanding credit", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = InputGuards.formatMoney(directory.totalOutstandingCredit, currency, masked),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                                color = if (directory.totalOutstandingCredit > 0.01) accents.danger else accents.emerald,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(34.dp)
                                .background(accents.borderSubtle)
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("Quantity sold", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = InputGuards.formatKg(directory.totalSoldKg),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
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

    SarimCard(contentPadding = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Analytics",
                fontFamily = PlayfairFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = accents.emerald
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SalesAnalyticsEngine.SalesChartPeriod.entries.forEach { p ->
                    val selected = p == selectedPeriod
                    Surface(
                        onClick = { onSelectPeriod(p) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) accents.softPillFill else accents.inputFill,
                        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 34.dp)
                            .testTag("sales_chart_period_${p.key}")
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = p.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.5.sp
                                ),
                                color = if (selected) accents.emerald else accents.textMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        if (customerFilter.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(999.dp), color = accents.softPillFill) {
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

        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = accents.inputFill,
            border = BorderStroke(1.dp, accents.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "Sales by Payment Type (${selectedPeriod.label})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(accents.emerald))
                        Text("Cash Sales (Inc. Received Credits)", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(accents.gold))
                        Text("Pending Credits", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
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
                    val yTicks = listOf(1.0, 0.75, 0.5, 0.25, 0.0)
                    Row(modifier = Modifier.fillMaxWidth().height(155.dp)) {
                        Column(
                            modifier = Modifier
                                .width(34.dp)
                                .fillMaxHeight()
                                .padding(bottom = 6.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            yTicks.forEach { frac ->
                                Text(
                                    text = if (frac == 0.0) "0" else formatCompactMetric(maxTotal * frac, masked),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                    color = accents.textMuted,
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
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
                            val barW = (slotW * 0.58f).coerceIn(5f, 44f)
                            val baselineY = size.height - 6f
                            val drawH = baselineY - 8f
                            yTicks.forEach { frac ->
                                val y = baselineY - (drawH * frac.toFloat())
                                drawLine(
                                    color = accents.borderSubtle.copy(alpha = if (frac == 0.0) 0.8f else 0.35f),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = if (frac == 0.0) 1.5f else 1f,
                                    pathEffect = if (frac == 0.0) null else PathEffect.dashPathEffect(floatArrayOf(5f, 5f))
                                )
                            }
                            chartData.labels.indices.forEach { idx ->
                                val cashV = max(0.0, chartData.cashValues.getOrElse(idx) { 0.0 })
                                val credV = max(0.0, chartData.creditValues.getOrElse(idx) { 0.0 })
                                val cashH = ((cashV / maxTotal).toFloat() * drawH).coerceAtLeast(if (cashV > 0.0) 4f else 0f)
                                val credH = ((credV / maxTotal).toFloat() * drawH).coerceAtLeast(if (credV > 0.0) 4f else 0f)
                                val left = idx * slotW + (slotW - barW) / 2f
                                val alpha = if (activeBarIdx == null || activeBarIdx == idx) 0.94f else 0.45f
                                if (cashH > 0f) {
                                    drawRoundRect(
                                        color = accents.emerald.copy(alpha = alpha),
                                        topLeft = Offset(left, baselineY - cashH),
                                        size = Size(barW, cashH),
                                        cornerRadius = CornerRadius(6f, 6f)
                                    )
                                }
                                if (credH > 0f) {
                                    drawRoundRect(
                                        color = accents.gold.copy(alpha = alpha),
                                        topLeft = Offset(left, baselineY - cashH - credH),
                                        size = Size(barW, credH),
                                        cornerRadius = CornerRadius(6f, 6f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    val stride = when {
                        chartData.labels.size > 20 -> 5
                        chartData.labels.size > 12 -> 3
                        else -> 1
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 42.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
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
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
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
        }

        Spacer(Modifier.height(12.dp))
        val pieTotal = InputGuards.round2(chartData.totalCash + chartData.totalCredit)
        OverviewDonutSummaryCard(
            title = "Total: ${InputGuards.formatMoney(pieTotal, currency, masked)} - ${selectedPeriod.label}",
            items = listOf(
                "Cash Sales (Inc. Received Credits)" to chartData.totalCash,
                "Pending Credits" to chartData.totalCredit
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
                fontFamily = PlayfairFont,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SalesAnalyticsEngine.SalesSummaryPeriod.entries.forEach { p ->
                    val selected = p == selectedPeriod
                    Surface(
                        onClick = { onSelectPeriod(p) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) accents.softPillFill else accents.inputFill,
                        border = BorderStroke(1.dp, if (selected) accents.emerald else accents.borderSubtle),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 34.dp)
                            .testTag("sales_summary_period_${p.key}")
                    ) {
                        Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = p.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.5.sp
                                ),
                                color = if (selected) accents.emerald else accents.textMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        if (customerFilter.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(999.dp), color = accents.softPillFill) {
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

        Spacer(Modifier.height(12.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = accents.inputFill,
            border = BorderStroke(1.dp, accents.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedPeriod.title,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    summary.marginPercent?.let { m ->
                        val chipColor = if (m >= 0) accents.emerald else accents.danger
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = chipColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, chipColor.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "$m% margin",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = chipColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "TOTAL VALUE",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = accents.textMuted
                )
                Text(
                    text = InputGuards.formatMoney(summary.totalValue, currency, masked),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = accents.emerald
                )

                Spacer(Modifier.height(10.dp))
                val cashV = max(0.0, summary.cashAmount)
                val credV = max(0.0, summary.pendingCreditAmount)
                val totalSplit = cashV + credV
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surface)
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
                Spacer(Modifier.height(8.dp))
                val cashPct = if (totalSplit > 0.0) ((cashV / totalSplit) * 100.0).roundToInt() else 0
                val credPct = if (totalSplit > 0.0) 100 - cashPct else 0
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(accents.emerald))
                        Text(
                            "Cash ${InputGuards.formatMoney(summary.cashAmount, currency, masked)} $cashPct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(accents.gold))
                        Text(
                            "Credit ${InputGuards.formatMoney(summary.pendingCreditAmount, currency, masked)} $credPct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SarimHalfRingKpiBox("QUANTITY", InputGuards.formatKg(summary.quantityKg), accents.cyan, Modifier.weight(1f))
                    SarimHalfRingKpiBox(
                        "NET PROFIT",
                        InputGuards.formatMoney(summary.netProfit, currency, masked),
                        if (summary.netProfit >= 0.0) accents.emerald else accents.danger,
                        Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SarimHalfRingKpiBox(
                        "CASH (INCL. RECEIVED)",
                        InputGuards.formatMoney(summary.cashAmount, currency, masked),
                        accents.emerald,
                        Modifier.weight(1f)
                    )
                    SarimHalfRingKpiBox(
                        "PENDING CREDITS",
                        InputGuards.formatMoney(summary.pendingCreditAmount, currency, masked),
                        accents.gold,
                        Modifier.weight(1f)
                    )
                }
            }
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

    val isUnpaidCredit = s.paymentType == "CREDIT" && !s.creditReceived && !s.isMerged
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelectedDate) accents.cyan.copy(alpha = 0.08f) else accents.inputFill,
        border = BorderStroke(
            1.dp,
            when {
                isSelectedDate -> accents.cyan.copy(alpha = 0.65f)
                isUnpaidCredit -> accents.gold.copy(alpha = 0.45f)
                else -> accents.borderSubtle
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("sales_hist_row_${s.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isUnpaidCredit) Modifier.diagonalWarningStripes(accents.gold, accents.gold) else Modifier)
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
        shape = RoundedCornerShape(20.dp),
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
