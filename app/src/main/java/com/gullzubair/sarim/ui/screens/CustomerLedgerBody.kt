package com.gullzubair.sarim.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.CustomerRecord
import com.gullzubair.sarim.data.model.SalesRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DecodedDataUrlImage
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.SalesDetailSheet
import com.gullzubair.sarim.ui.components.SalesBlockingAlertDialog
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.CtaGradientEnd
import com.gullzubair.sarim.ui.theme.CtaGradientEndLight
import com.gullzubair.sarim.ui.theme.CtaGradientStart
import com.gullzubair.sarim.ui.theme.CtaGradientStartLight
import com.gullzubair.sarim.ui.theme.CtaTextDark
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.PdfExporter
import com.gullzubair.sarim.util.StatementRow
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One ledger line of the customer statement (web: cm-ledger.js `entries`). */
private data class LedgerEntry(
    val sale: SalesRecord,
    val isRep: Boolean,
    val dkey: String,
    val title: String,
    val sub: String,
    val amount: Double,
    val isIn: Boolean,
    val cat: String,
    val unpaid: Boolean,
    val debit: Double,
    val credit: Double,
    val balAfter: Double,
    val kg: Double,
    val sold: Double,
    val got: Double
)

private data class LedgerDay(
    val key: String,
    val items: List<LedgerEntry>, // newest first
    val kg: Double,
    val sold: Double,
    val got: Double,
    val opening: Double,
    val closing: Double
)

private data class LedgerMonth(
    val key: String,
    val days: List<LedgerDay>,
    val count: Int,
    val kg: Double,
    val sold: Double,
    val got: Double
)

private sealed interface LedgerListItem {
    data class MonthHead(val month: LedgerMonth, val collapsed: Boolean) : LedgerListItem
    data class DayCard(val day: LedgerDay, val visible: List<LedgerEntry>) : LedgerListItem
}

private fun monthName(key: String, short: Boolean = false): String = runCatching {
    val ym = YearMonth.parse(key)
    val style = if (short) TextStyle.SHORT else TextStyle.FULL
    "${ym.month.getDisplayName(style, Locale.ENGLISH)} ${if (short) ym.year % 100 else ym.year}"
}.getOrDefault(key)

@Composable
internal fun CustomerLedgerBody(
    snapshot: WorkspaceSnapshot,
    customerName: String,
    customer: CustomerRecord?,
    isRepClient: Boolean,
    balance: Double,
    rows: List<StatementRow>,
    currency: String,
    masked: Boolean,
    isSalesRestricted: Boolean,
    isAdmin: Boolean,
    vm: SarimViewModel,
    onBack: () -> Unit,
    onEditCustomer: () -> Unit,
    onMoneyOut: () -> Unit
) {
    val context = LocalContext.current
    val accents = LocalSarimAccents.current
    var menuOpen by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var showQty by remember { mutableStateOf(false) }
    var selectedSale by remember { mutableStateOf<SalesRecord?>(null) }
    var blockedMsg by remember { mutableStateOf<String?>(null) }
    var jumpTarget by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Entries oldest -> newest with a running balance (same maths as the statement rows).
    val entries = remember(snapshot, customerName) {
        val reconciled = FinanceEngine.reconciledRepReturnIds(snapshot.calculator)
        val directSales = snapshot.sales.filter { it.deletedAt == null && it.customerName.equals(customerName, true) }
        val repSales = snapshot.repRecords.filter { it.deletedAt == null && it.customerName.equals(customerName, true) }
        val directHonor = FinanceEngine.allocHonor(directSales)
        val repHonor = FinanceEngine.allocHonor(repSales)
        val directHasOldDebt = directSales.any { it.transactionType == "OLD_DEBT" }
        val repHasOldDebt = repSales.any { it.transactionType == "OLD_DEBT" }
        var running = (if (directHasOldDebt) 0.0 else snapshot.customers.filter { it.deletedAt == null && it.name.equals(customerName, true) }.sumOf { it.oldDebit }) +
            (if (repHasOldDebt) 0.0 else snapshot.clients.filter { it.deletedAt == null && it.name.equals(customerName, true) }.sumOf { it.oldDebit })
        val combined = (directSales.map { it to false } + repSales.map { it to true })
            .sortedWith(compareBy({ it.first.date }, { it.first.timestamp }))
        combined.map { (s, isRep) ->
            val pool = if (isRep) snapshot.clients.ifEmpty { snapshot.customers } else snapshot.customers
            val gross = FinanceEngine.effectiveSaleValue(s, pool, snapshot.stores)
            val split = FinanceEngine.ledgerSplit(
                t = s,
                grossValue = gross,
                reconciledReturnIds = reconciled,
                honor = if (isRep) repHonor else directHonor
            )
            running = InputGuards.round2(running + split.delta)
            val isRepRet = s.isRepReturn || s.paymentType == "REP_RETURN"
            val isColl = FinanceEngine.isCollectionRow(s)
            val isMoneyOut = s.paymentType == "MONEY_OUT"
            val isOldDebt = s.transactionType == "OLD_DEBT"
            val title = when {
                isOldDebt -> "Old debt"
                isRepRet -> "Rep return"
                s.paymentType == "PARTIAL_PAYMENT" -> "Partial payment"
                isColl -> "Collection"
                isMoneyOut -> "Money out"
                isRep -> "Rep ${s.paymentType.lowercase().replaceFirstChar { it.uppercase() }}"
                s.paymentType == "CREDIT" -> "Credit sale"
                else -> "Cash sale"
            }
            val qty = if (s.quantity > 0.0) s.quantity else s.returnedQty
            val sub = buildString {
                if (qty > 0.0) append(InputGuards.formatKg(qty))
                if (s.salesRep.isNotBlank() && !s.salesRep.equals("NONE", true)) {
                    if (isNotEmpty()) append(" • ")
                    append(s.salesRep)
                }
                if (s.notes.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(s.notes)
                }
            }
            val amount = when {
                isRepRet -> s.returnValue
                isColl -> if (s.collectedAmount > 0.0) s.collectedAmount else s.totalValue
                else -> s.totalValue
            }
            val totals = FinanceEngine.periodTotals(listOf(s to gross))
            LedgerEntry(
                sale = s,
                isRep = isRep,
                dkey = s.date.take(10),
                title = title,
                sub = sub,
                amount = amount,
                isIn = split.credit > 0.005,
                cat = when {
                    isColl || isMoneyOut || isRepRet -> "pay"
                    s.paymentType == "CREDIT" -> "credit"
                    else -> "cash"
                },
                unpaid = s.paymentType == "CREDIT" && !s.creditReceived && !s.isMerged,
                debit = split.debit,
                credit = split.credit,
                balAfter = running,
                kg = FinanceEngine.soldQuantity(s),
                sold = totals.totalSale,
                got = totals.totalReceived
            )
        }
    }
    val n = entries.size
    val comfortable = n <= 6
    val many = n > 40

    val months = remember(entries) {
        entries.reversed().groupBy { it.dkey.take(7) }.map { (mKey, mEntries) ->
            val days = mEntries.groupBy { it.dkey }.map { (dKey, dItems) ->
                val net = dItems.sumOf { it.debit - it.credit }
                LedgerDay(
                    key = dKey,
                    items = dItems,
                    kg = dItems.sumOf { it.kg },
                    sold = dItems.sumOf { it.sold },
                    got = dItems.sumOf { it.got },
                    opening = dItems.first().balAfter - net,
                    closing = dItems.first().balAfter
                )
            }
            LedgerMonth(
                key = mKey,
                days = days,
                count = mEntries.size,
                kg = mEntries.sumOf { it.kg },
                sold = mEntries.sumOf { it.sold },
                got = mEntries.sumOf { it.got }
            )
        }
    }
    var collapsedMonths by remember(months.map { it.key }) {
        mutableStateOf(if (many) months.drop(1).map { it.key }.toSet() else emptySet())
    }

    val term = search.trim().lowercase()
    val effFilter = if (comfortable) "all" else filter
    val filtering = !comfortable && (term.isNotEmpty() || effFilter != "all")

    fun matches(e: LedgerEntry): Boolean {
        val okF = effFilter == "all" || (if (effFilter == "open") e.unpaid else e.cat == effFilter)
        val okT = comfortable || term.isEmpty() ||
            "${e.title} ${e.sub} ${e.dkey} ${DateUtils.formatPretty(e.dkey)} ${InputGuards.fmtNum(e.amount)}".lowercase().contains(term)
        return okF && okT
    }

    val listItems = remember(months, collapsedMonths, term, effFilter, comfortable) {
        buildList<LedgerListItem> {
            months.forEach { m ->
                val dayCards = m.days.mapNotNull { d ->
                    val vis = d.items.filter { matches(it) }
                    if (vis.isEmpty()) null else LedgerListItem.DayCard(d, vis)
                }
                if (dayCards.isEmpty()) return@forEach
                if (comfortable) {
                    addAll(dayCards)
                } else {
                    val collapsed = !filtering && m.key in collapsedMonths
                    add(LedgerListItem.MonthHead(m, collapsed))
                    if (!collapsed) addAll(dayCards)
                }
            }
        }
    }

    val totalSale = entries.sumOf { it.sold }
    val totalGot = entries.sumOf { it.got }
    val totalKg = entries.sumOf { it.kg }
    val receivedPct = run {
        val got = maxOf(0.0, totalGot)
        val owed = maxOf(0.0, balance)
        val t = got + owed
        if (t > 0.005) (got / t * 100).roundToInt().coerceIn(0, 100) else 0
    }
    val showJump = many && months.size >= 3
    val prefixCount = 3 + (if (!comfortable) 1 else 0) + (if (showJump) 1 else 0)

    LaunchedEffect(jumpTarget) {
        val key = jumpTarget ?: return@LaunchedEffect
        val idx = listItems.indexOfFirst { it is LedgerListItem.MonthHead && it.month.key == key }
        if (idx >= 0) listState.animateScrollToItem(prefixCount + idx)
        jumpTarget = null
    }

    fun exportStatement() {
        PdfExporter.exportAndShareStatement(
            context = context,
            title = "Customer Statement: $customerName",
            subtitle = "Gull & Zubair Naswar Dealers",
            rows = rows,
            summaryFooter = "Closing Balance: ${InputGuards.formatMoney(balance, currency, masked)}"
        )
    }

    fun shareDay(day: LedgerDay) {
        val dayRows = buildList {
            if (abs(day.opening) >= 0.01) {
                add(StatementRow("Previous", "Previous balance", "-", "-", if (masked) "••••" else InputGuards.fmtNum(day.opening)))
            }
            day.items.reversed().forEach { e ->
                add(
                    StatementRow(
                        date = e.dkey,
                        description = e.title + if (e.sub.isNotBlank()) " ${e.sub}" else "",
                        debit = if (e.debit > 0.005) (if (masked) "••••" else InputGuards.fmtNum(e.debit)) else "-",
                        credit = if (e.credit > 0.005) (if (masked) "••••" else InputGuards.fmtNum(e.credit)) else "-",
                        balance = if (masked) "••••" else InputGuards.fmtNum(e.balAfter)
                    )
                )
            }
        }
        if (dayRows.isEmpty()) {
            vm.showToast("Nothing to invoice for this day.", com.gullzubair.sarim.ui.viewmodel.ToastKind.WARNING)
            return
        }
        PdfExporter.exportAndShareStatement(
            context = context,
            title = "Invoice: $customerName",
            subtitle = DateUtils.formatPretty(day.key),
            rows = dayRows,
            summaryFooter = "Balance: ${InputGuards.formatMoney(day.closing, currency, masked)}"
        )
    }

    val photoKey = customer?.let { "${if (isRepClient) "client" else "cust"}:${it.id}" }
    val photoUri = photoKey?.let { snapshot.photos[it]?.takeIf { b -> !b.deleted }?.dataUri }
    val page = customer?.ledgerPage.orEmpty()
    val phone = customer?.phone.orEmpty()
    val address = customer?.address.orEmpty()
    val headerSubtitle = listOfNotNull(
        phone.ifBlank { "No Phone" },
        address.takeIf { it.isNotBlank() }
    ).joinToString(" | ")

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = customerName,
            subtitle = headerSubtitle,
            onBack = onBack,
            leadingIcon = {
                Box(modifier = Modifier.size(44.dp)) {
                    if (!photoUri.isNullOrBlank()) {
                        DecodedDataUrlImage(
                            dataUrl = photoUri,
                            contentDescription = "$customerName photo",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(2.dp, accents.emerald, CircleShape)
                                .align(Alignment.Center)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accents.softPillFill)
                                .border(2.dp, accents.emerald, CircleShape)
                                .align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PersonOutline,
                                contentDescription = null,
                                tint = accents.emerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (page.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, accents.emerald),
                            modifier = Modifier.align(Alignment.BottomEnd)
                        ) {
                            Text(
                                text = page,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = accents.emerald,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            },
            trailingContent = {
                Box {
                    Surface(
                        onClick = { menuOpen = true },
                        shape = CircleShape,
                        color = accents.inputFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.size(40.dp).testTag("customer_menu_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More actions", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                        }
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (phone.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Call $phone") },
                                onClick = {
                                    menuOpen = false
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))
                                        )
                                    }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Edit customer") },
                            onClick = { menuOpen = false; onEditCustomer() },
                            modifier = Modifier.testTag("customer_edit_btn")
                        )
                        DropdownMenuItem(
                            text = { Text("Statement PDF") },
                            onClick = { menuOpen = false; exportStatement() },
                            modifier = Modifier.testTag("customer_pdf_btn")
                        )
                    }
                }
            }
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(2.dp)) }

            // 1. Summary panel
            item {
                val debtColor = if (balance > 0.005) accents.gold else accents.emerald
                SarimCard {
                    Text("Current debt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = InputGuards.formatMoney(balance, currency, masked),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = debtColor
                    )
                    if (n > 0) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { showQty = !showQty }
                                    .padding(vertical = 4.dp)
                                    .testTag("customer_sale_chip"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.width(3.dp).height(34.dp).clip(CircleShape).background(accents.emerald))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        if (showQty) "TOTAL QUANTITY" else "TOTAL SALE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = accents.textMuted
                                    )
                                    Text(
                                        text = if (showQty) InputGuards.formatKg(totalKg) else InputGuards.formatMoney(totalSale, currency, masked),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.width(3.dp).height(34.dp).clip(CircleShape).background(accents.emerald))
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text("RECEIVED", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = accents.textMuted)
                                    Text(
                                        text = InputGuards.formatMoney(totalGot, currency, masked),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = accents.emerald
                                    )
                                }
                            }
                        }
                    }
                    if (n >= 3) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(accents.inputFill)
                        ) {
                            if (receivedPct > 0) {
                                Box(
                                    Modifier.fillMaxWidth(receivedPct / 100f).fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp)).background(accents.emerald)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$receivedPct% of the amount owed has been received",
                            style = MaterialTheme.typography.labelSmall,
                            color = accents.textMuted
                        )
                    }
                }
            }

            // 2. Search + Money Out
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!comfortable) {
                        SarimPillInput(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = "Search history...",
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            modifier = Modifier.weight(1f),
                            testTag = "customer_history_search"
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (!isRepClient) {
                        val ctaBrush = remember(accents.isDark) {
                            Brush.horizontalGradient(
                                if (accents.isDark) listOf(CtaGradientStart, CtaGradientEnd)
                                else listOf(CtaGradientStartLight, CtaGradientEndLight)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(ctaBrush)
                                .clickable { onMoneyOut() }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("customer_money_out_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Money Out (Cash Out −)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = CtaTextDark,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // 4. Filter chips
            if (!comfortable) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("all" to "All", "credit" to "Credit", "cash" to "Cash", "pay" to "Payments", "open" to "Unpaid")) { (k, label) ->
                            val on = effFilter == k
                            Surface(
                                onClick = { filter = k },
                                shape = RoundedCornerShape(999.dp),
                                color = if (on) accents.softPillFill else accents.inputFill,
                                border = BorderStroke(1.dp, if (on) accents.emerald else accents.borderSubtle),
                                modifier = Modifier.testTag("customer_filter_$k")
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                                    color = if (on) accents.emerald else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Month jump bar
            if (showJump) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(months) { m ->
                            Surface(
                                onClick = {
                                    collapsedMonths = collapsedMonths - m.key
                                    jumpTarget = m.key
                                },
                                shape = RoundedCornerShape(20.dp),
                                color = accents.inputFill,
                                border = BorderStroke(1.dp, accents.borderSubtle)
                            ) {
                                Text(
                                    monthName(m.key, short = true),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accents.textMuted,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 6. Months / day cards
            if (n == 0) {
                item {
                    EmptyStateCard(
                        "No transactions yet",
                        "Sales, collections and old debt for $customerName will appear here."
                    )
                }
            } else if (listItems.isEmpty()) {
                item { EmptyStateCard("No matching history", "Clear the search or pick All.") }
            } else {
                items(listItems, key = { item ->
                    when (item) {
                        is LedgerListItem.MonthHead -> "m-${item.month.key}"
                        is LedgerListItem.DayCard -> "d-${item.day.key}"
                    }
                }) { item ->
                    when (item) {
                        is LedgerListItem.MonthHead -> {
                            val m = item.month
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        collapsedMonths = if (m.key in collapsedMonths) collapsedMonths - m.key else collapsedMonths + m.key
                                    }
                                    .padding(horizontal = 4.dp, vertical = 8.dp)
                                    .testTag("customer_month_${m.key}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(monthName(m.key).uppercase(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                    Text(
                                        "Sold ${InputGuards.formatMoney(m.sold, currency, masked)} · Received ${InputGuards.formatMoney(m.got, currency, masked)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accents.textMuted
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "${InputGuards.formatKg(m.kg)} sold",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = accents.textMuted
                                    )
                                    Icon(
                                        if (item.collapsed) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        is LedgerListItem.DayCard -> {
                            val d = item.day
                            val parsedDay = DateUtils.parseLocalDate(d.key)
                            val dayNum = parsedDay?.dayOfMonth?.toString() ?: d.key.takeLast(2)
                            val dayDow = parsedDay?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) ?: ""
                            SarimCard {
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(
                                        modifier = Modifier.width(34.dp).padding(top = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(dayNum, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onSurface)
                                        Text(dayDow, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = accents.textMuted)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        item.visible.forEachIndexed { i, e ->
                                            val canOpen = !e.isRep
                                            val dotColor = if (e.isIn || e.cat == "cash") accents.emerald else accents.gold
                                            val isPaidCredit = e.cat == "credit" && e.sale.creditReceived && !e.sale.isMerged
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(enabled = canOpen) { selectedSale = e.sale }
                                                    .padding(vertical = 6.dp)
                                                    .testTag("customer_row_${e.sale.id}"),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
                                                        Text(e.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                                        if (e.unpaid) {
                                                            Text(
                                                                "PENDING",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                                color = accents.gold
                                                            )
                                                        } else if (isPaidCredit) {
                                                            Text(
                                                                "PAID",
                                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                                                color = accents.emerald
                                                            )
                                                        }
                                                    }
                                                    if (e.sub.isNotBlank()) {
                                                        Text(e.sub, style = MaterialTheme.typography.labelSmall, color = accents.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = (if (e.isIn) "+ " else "") + InputGuards.formatMoney(e.amount, currency, masked),
                                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = if (e.isIn) accents.emerald else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = InputGuards.formatMoney(e.balAfter, currency, masked),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = accents.textMuted
                                                    )
                                                }
                                            }
                                            if (i < item.visible.lastIndex) {
                                                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.5f))
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (d.items.size > 1) {
                                                    "${InputGuards.formatKg(d.kg)} sold | Sold: ${InputGuards.formatMoney(d.sold, currency, masked)} | Received: ${InputGuards.formatMoney(d.got, currency, masked)}"
                                                } else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accents.textMuted,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                onClick = { shareDay(d) },
                                                shape = RoundedCornerShape(999.dp),
                                                color = accents.softPillFill,
                                                border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.6f)),
                                                modifier = Modifier.testTag("customer_share_${d.key}")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Share, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(15.dp))
                                                    Text(
                                                        "Invoice",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = accents.emerald
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    selectedSale?.let { s ->
        SalesDetailSheet(
            s = s,
            snapshot = snapshot,
            runningBalanceAfter = entries.firstOrNull { it.sale.id == s.id }?.balAfter,
            currency = currency,
            masked = masked,
            isSalesRestricted = isSalesRestricted,
            isAdmin = isAdmin,
            vm = vm,
            onEditEntry = { sale ->
                vm.requestSaleEdit(sale.id)
                onBack()
            },
            onOpenCustomerDetail = {},
            onBlockedAction = { msg -> blockedMsg = msg },
            onDismiss = { selectedSale = null },
            showStatement = false
        )
    }
    blockedMsg?.let { msg ->
        SalesBlockingAlertDialog(title = "Action Blocked", message = msg, onDismiss = { blockedMsg = null })
    }
}
