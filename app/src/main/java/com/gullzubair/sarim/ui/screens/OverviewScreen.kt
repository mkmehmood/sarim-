package com.gullzubair.sarim.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.FormulaEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.domain.OverviewEngine
import com.gullzubair.sarim.domain.OverviewFixEngine
import com.gullzubair.sarim.ui.components.OverviewActivityPanel
import com.gullzubair.sarim.ui.components.OverviewChecksPanel
import com.gullzubair.sarim.ui.components.OverviewFixDialog
import com.gullzubair.sarim.ui.components.OverviewMoneyPanel
import com.gullzubair.sarim.ui.components.OverviewProdPanel
import com.gullzubair.sarim.ui.components.OverviewSalesPanel
import com.gullzubair.sarim.ui.components.OverviewStockPanel
import com.gullzubair.sarim.ui.components.RepairLinksConfirmDialog
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.TabHeading
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.theme.SarimMotion
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

private data class SparkTileModel(
    val label: String,
    val value: String,
    val subtitle: String,
    val series: List<Double>?,
    val lineColor: Color,
    val isAlert: Boolean = false,
    val isHero: Boolean = false
)

@Composable
fun OverviewScreen(
    snapshot: WorkspaceSnapshot,
    vm: SarimViewModel,
    onOpenApprovals: () -> Unit,
    onOpenDailyLedger: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val isAdmin = session.mode == "admin"
    val masked = session.restrictMoney || snapshot.settings.hideSensitiveMoney

    var windowMonths by remember { mutableIntStateOf(6) }
    var activePanel by remember { mutableStateOf("Sales") }

    var activeFinding by remember { mutableStateOf<OverviewEngine.AuditFinding?>(null) }
    var sequenceQueue by remember { mutableStateOf<List<String>>(emptyList()) }
    var sequenceTotal by remember { mutableIntStateOf(0) }
    var showBulkRepairConfirm by remember { mutableStateOf(false) }

    val summary = remember(snapshot, windowMonths) {
        OverviewEngine.computeOverview(snapshot, windowMonths)
    }
    val health = remember(snapshot) { FinanceEngine.calculateEconomicHealth(snapshot) }
    val storeSnapshots = remember(snapshot) { FinanceEngine.computeStoreStockSnapshots(snapshot) }
    val unitPools = remember(snapshot.stores, snapshot.formulas, snapshot.factory, snapshot.production) {
        FormulaEngine.buildUnitPools(snapshot.stores, snapshot.formulas, snapshot.factory, snapshot.production)
    }
    val pendingCount = remember(snapshot.pendingApprovals) {
        snapshot.pendingApprovals.count { it.status == "pending" }
    }

    val kpiTiles = remember(summary, health, currency, masked, accents) {
        val inKg = summary.prodKg + summary.returnsKg
        listOf(
            SparkTileModel(
                label = "Net profit",
                value = InputGuards.formatMoney(summary.prodProfit, currency, masked),
                subtitle = if (summary.prodValue > 0) "${String.format("%.1f", summary.marginPct)}% margin on ${InputGuards.formatMoney(summary.prodValue, currency, masked)}" else "",
                series = summary.months.map { it.prodProfit },
                lineColor = if (summary.prodProfit < 0) accents.danger else accents.emerald,
                isAlert = summary.prodProfit < 0,
                isHero = true
            ),
            SparkTileModel(
                label = "Sales",
                value = InputGuards.formatMoney(summary.salesValue, currency, masked),
                subtitle = "${summary.salesCount} sale${if (summary.salesCount == 1) "" else "s"}",
                series = summary.months.map { it.salesValue },
                lineColor = accents.emerald
            ),
            SparkTileModel(
                label = "Produced",
                value = InputGuards.formatKg(summary.prodKg),
                subtitle = if (summary.returnsKg > 0) "+${InputGuards.formatKg(summary.returnsKg)} returns" else "Cost ${InputGuards.formatMoney(summary.prodCost, currency, masked)}",
                series = summary.months.map { it.prodKg },
                lineColor = accents.cyan
            ),
            SparkTileModel(
                label = "Remaining",
                value = InputGuards.formatKg(summary.remainingKg),
                subtitle = "${InputGuards.fmtNum(inKg, 0)} in · ${InputGuards.fmtNum(summary.soldKg, 0)} sold",
                series = null,
                lineColor = if (summary.remainingKg < 0) accents.danger else accents.gold,
                isAlert = summary.remainingKg < 0
            ),
            SparkTileModel(
                label = "Net cash",
                value = InputGuards.formatMoney(summary.netCash, currency, masked),
                subtitle = "In ${InputGuards.formatMoney(summary.cashIn, currency, masked)} · Out ${InputGuards.formatMoney(summary.cashOut, currency, masked)}",
                series = summary.months.map { it.cashIn - it.cashOut },
                lineColor = if (summary.netCash < 0) accents.danger else accents.emerald
            ),
            SparkTileModel(
                label = "Expenses",
                value = InputGuards.formatMoney(summary.expenses, currency, masked),
                subtitle = "",
                series = summary.months.map { it.expenses },
                lineColor = accents.gold
            ),
            SparkTileModel(
                label = "Owed to you",
                value = InputGuards.formatMoney(if (summary.owedAging.count > 0) summary.owedAging.total else summary.openCreditValue, currency, masked),
                subtitle = "${if (summary.owedAging.count > 0) summary.owedAging.count else summary.openCreditCount} unreceived",
                series = null,
                lineColor = accents.gold
            ),
            SparkTileModel(
                label = "Stock",
                value = InputGuards.formatMoney(summary.stockValue, currency, masked),
                subtitle = "${summary.stockItemsCount} material${if (summary.stockItemsCount == 1) "" else "s"}${if (summary.outOfStockNames.isNotEmpty()) " · ${summary.outOfStockNames.size} out" else ""}",
                series = null,
                lineColor = accents.purple
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    TabHeading("Overview")
                    val stamp = remember(snapshot, windowMonths) {
                        java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
                    }
                    Text("Updated $stamp", style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 6, 12).forEach { m ->
                        val selected = windowMonths == m
                        val bg by animateColorAsState(if (selected) accents.softPillFill else accents.inputFill, SarimMotion.ColorSpring, label = "win_bg")
                        val border by animateColorAsState(if (selected) accents.emerald else accents.borderSubtle, SarimMotion.ColorSpring, label = "win_bd")
                        Surface(
                            onClick = { windowMonths = m },
                            shape = RoundedCornerShape(999.dp),
                            color = bg,
                            border = BorderStroke(1.dp, border),
                            modifier = Modifier.testTag("overview_window_${m}m")
                        ) {
                            Text(
                                text = "${m}M",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) accents.emerald else accents.textMuted,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isAdmin && pendingCount > 0) {
            item {
                SarimCard(accentBorder = accents.gold) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("$pendingCount Pending Approvals", style = MaterialTheme.typography.titleMedium, color = accents.gold)
                            Text("Team submissions waiting for review", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Surface(
                            onClick = onOpenApprovals,
                            shape = RoundedCornerShape(14.dp),
                            color = accents.gold.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, accents.gold),
                            modifier = Modifier.testTag("overview_open_approvals_btn")
                        ) {
                            Text("Review", style = MaterialTheme.typography.labelLarge, color = accents.gold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }

        // 8 Spark KPI Cards Grid (2 cols on compact/medium, 4 cols on expanded)
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cols = if (maxWidth >= 720.dp) 4 else 2
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    kpiTiles.chunked(cols).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowItems.forEach { tile ->
                                OverviewSparkTile(tile = tile, modifier = Modifier.weight(1f))
                            }
                            repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        // Right-Now 4 Pills Strip (matches 01-tab-overview)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "RIGHT NOW",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                    color = accents.textMuted
                )
                OverviewRightNowPill("Production", InputGuards.formatKg(summary.todayProdKg), accents.cyan)
                OverviewRightNowPill("Sales", InputGuards.formatMoney(summary.todaySalesValue, currency, masked), accents.emerald)
                OverviewRightNowPill("In", InputGuards.formatMoney(summary.todayIn, currency, masked), accents.emerald)
                OverviewRightNowPill("Out", InputGuards.formatMoney(summary.todayOut, currency, masked), accents.danger)
            }
        }

        // 6 Panel Switcher Pills (matches 01b-tab-overview-full: clean text pills, red circle badge on Checks)
        item {
            val issueCount = summary.audit.errors + summary.audit.warnings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Sales", "Prod", "Money", "Stock", "Checks", "Activity").forEach { panel ->
                    val display = if (panel == "Prod") "Production" else panel
                    val selected = activePanel == panel
                    val bg by animateColorAsState(if (selected) accents.softPillFill else accents.inputFill, SarimMotion.ColorSpring, label = "panel_bg")
                    val border by animateColorAsState(
                        when {
                            selected -> accents.emerald
                            else -> accents.borderSubtle
                        },
                        SarimMotion.ColorSpring,
                        label = "panel_bd"
                    )
                    val fg = if (selected) accents.emerald else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
                    Surface(
                        onClick = { activePanel = panel },
                        shape = RoundedCornerShape(999.dp),
                        color = bg,
                        border = BorderStroke(1.dp, border),
                        modifier = Modifier.testTag("overview_panel_${panel.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                display,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = fg
                            )
                            if (panel == "Checks" && issueCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(accents.danger)
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$issueCount",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            when (activePanel) {
                "Sales" -> OverviewSalesPanel(summary, currency, masked)
                "Prod" -> OverviewProdPanel(summary, storeSnapshots, unitPools, currency, masked)
                "Money" -> OverviewMoneyPanel(summary, health, isAdmin, currency, masked, onOpenDailyLedger)
                "Stock" -> OverviewStockPanel(snapshot, summary, currency, masked)
                "Checks" -> OverviewChecksPanel(
                    summary = summary,
                    isAdmin = isAdmin,
                    currency = currency,
                    masked = masked,
                    onRepriceZeroCost = { vm.auditRepriceZeroCostSales() },
                    onStartFixOneByOne = {
                        val ids = summary.audit.findings.map { it.id }
                        if (ids.isNotEmpty()) {
                            sequenceTotal = ids.size
                            sequenceQueue = ids.drop(1)
                            activeFinding = summary.audit.findings.first()
                        }
                    },
                    onSelectByCode = { code ->
                        if (code in OverviewFixEngine.LINK_CODES) {
                            showBulkRepairConfirm = true
                        } else {
                            sequenceQueue = emptyList()
                            sequenceTotal = 0
                            activeFinding = summary.audit.findings.firstOrNull { code in it.codes }
                        }
                    },
                    onSelectFinding = { f ->
                        sequenceQueue = emptyList()
                        sequenceTotal = 0
                        activeFinding = f
                    },
                    onInspectLossSale = { w ->
                        sequenceQueue = emptyList()
                        sequenceTotal = 0
                        activeFinding = OverviewEngine.AuditFinding(
                            id = "sale_loss:${w.id}",
                            collection = "sales",
                            recordId = w.id,
                            code = "sale_loss",
                            codes = listOf("sale_loss"),
                            severity = "warn",
                            title = w.customer,
                            label = "Sale",
                            date = w.date,
                            reasons = listOf("Sold at ${InputGuards.fmtNum(w.price)}/kg against cost ${InputGuards.fmtNum(w.cost)}/kg (loss ${InputGuards.formatMoney(abs(w.profit), currency, masked)})."),
                            impact = w.profit
                        )
                    }
                )
                else -> OverviewActivityPanel(summary, currency, masked)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    activeFinding?.let { finding ->
        val stepLabel = if (sequenceTotal > 0) {
            val doneIdx = sequenceTotal - sequenceQueue.size
            "Step $doneIdx of $sequenceTotal"
        } else null

        OverviewFixDialog(
            finding = finding,
            snapshot = snapshot,
            vm = vm,
            sequenceStepLabel = stepLabel,
            onDismiss = {
                activeFinding = null
                sequenceQueue = emptyList()
                sequenceTotal = 0
            },
            onAdvanceSequence = if (sequenceTotal > 0) {
                {
                    val nextId = sequenceQueue.firstOrNull()
                    val nextFinding = if (nextId != null) summary.audit.findings.firstOrNull { it.id == nextId } else null
                    if (nextFinding != null) {
                        sequenceQueue = sequenceQueue.drop(1)
                        activeFinding = nextFinding
                    } else {
                        activeFinding = null
                        sequenceQueue = emptyList()
                        sequenceTotal = 0
                    }
                }
            } else null
        )
    }

    if (showBulkRepairConfirm) {
        val count = summary.audit.findings.count { f -> f.codes.any { it in OverviewFixEngine.LINK_CODES } }
        RepairLinksConfirmDialog(
            affectedCount = count,
            onDismiss = { showBulkRepairConfirm = false },
            onConfirm = {
                showBulkRepairConfirm = false
                val hasSales = summary.audit.findings.any { it.collection == "sales" && it.codes.any { c -> c in OverviewFixEngine.LINK_CODES } }
                val hasRep = summary.audit.findings.any { it.collection == "rep" && it.codes.any { c -> c in OverviewFixEngine.LINK_CODES } }
                if (hasSales) vm.repairSalesLinks("sales")
                if (hasRep) vm.repairSalesLinks("rep")
            }
        )
    }
}

@Composable
private fun OverviewSparkTile(tile: SparkTileModel, modifier: Modifier = Modifier) {
    val accents = LocalSarimAccents.current
    val deltaPct = remember(tile.series) {
        val s = tile.series
        if (s != null && s.size >= 2 && abs(s[s.size - 2]) > 0.0) {
            (((s.last() - s[s.size - 2]) / abs(s[s.size - 2])) * 100.0).roundToInt()
        } else null
    }
    SarimCard(
        modifier = modifier,
        accentBorder = when {
            tile.isAlert -> accents.danger.copy(alpha = 0.55f)
            tile.isHero -> accents.emerald.copy(alpha = 0.55f)
            else -> null
        },
        contentPadding = PaddingValues(14.dp)
    ) {
        Text(
            tile.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold),
            color = accents.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            tile.value,
            fontFamily = JakartaFont,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (tile.isHero) 20.sp else 18.sp,
            color = when {
                tile.isAlert -> accents.danger
                tile.isHero -> accents.emerald
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val pts = tile.series
        if (pts != null && pts.size >= 2) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.weight(1f).height(26.dp)) {
                    val maxV = pts.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                    val minV = pts.minOrNull() ?: 0.0
                    val span = (maxV - minV).coerceAtLeast(1.0)
                    val allFlatZero = pts.all { abs(it) < 1e-6 }
                    val stepX = size.width / (pts.size - 1).coerceAtLeast(1)
                    val coords = if (allFlatZero) {
                        pts.mapIndexed { idx, _ -> Offset(idx * stepX, size.height - 4f) }
                    } else {
                        pts.mapIndexed { idx, v ->
                            Offset(idx * stepX, size.height - 4f - (((v - minV) / span).toFloat() * (size.height - 8f)))
                        }
                    }
                    val path = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        for (i in 0 until coords.lastIndex) {
                            val p0 = coords[i]
                            val p1 = coords[i + 1]
                            val cx = (p0.x + p1.x) / 2f
                            cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                        }
                    }
                    if (!allFlatZero) {
                        val areaPath = Path().apply {
                            addPath(path)
                            lineTo(coords.last().x, size.height)
                            lineTo(coords.first().x, size.height)
                            close()
                        }
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                listOf(tile.lineColor.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )
                    }
                    drawPath(path = path, color = tile.lineColor, style = Stroke(width = 4.2f, cap = StrokeCap.Round))
                }
                if (deltaPct != null) {
                    Spacer(Modifier.width(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (deltaPct >= 0) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "${abs(deltaPct)}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        if (tile.subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                tile.subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = accents.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OverviewRightNowPill(label: String, value: String, color: Color) {
    val accents = LocalSarimAccents.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accents.inputFill,
        border = BorderStroke(1.dp, accents.borderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
            Text(value, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = color)
        }
    }
}
