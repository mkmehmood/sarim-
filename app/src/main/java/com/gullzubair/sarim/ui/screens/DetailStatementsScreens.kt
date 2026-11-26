package com.gullzubair.sarim.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.CustomerMoneyOutDialog
import com.gullzubair.sarim.ui.components.DecodedDataUrlImage
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.PdfExporter
import com.gullzubair.sarim.util.StatementRow
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@Composable
fun CustomerDetailScreen(
    snapshot: WorkspaceSnapshot,
    customerName: String,
    isRepClient: Boolean = false,
    vm: SarimViewModel,
    onBack: () -> Unit,
    onEditCustomer: () -> Unit
) {
    val context = LocalContext.current
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = FinanceEngine.deriveSession(snapshot.profile).restrictMoney || snapshot.settings.hideSensitiveMoney
    var showMoneyOut by remember { mutableStateOf(false) }

    val customer = remember(snapshot.customers, snapshot.clients, customerName, isRepClient) {
        val primary = if (isRepClient) snapshot.clients else snapshot.customers
        val fallback = if (isRepClient) snapshot.customers else snapshot.clients
        primary.firstOrNull { it.deletedAt == null && it.name.equals(customerName, true) }
            ?: fallback.firstOrNull { it.deletedAt == null && it.name.equals(customerName, true) }
    }
    val balance = remember(snapshot, customerName) {
        FinanceEngine.computeCustomerBalance(
            customerName = customerName,
            customers = snapshot.customers,
            sales = snapshot.sales,
            repRecords = snapshot.repRecords,
            calculator = snapshot.calculator,
            stores = snapshot.stores,
            clients = snapshot.clients,
            includeClients = true
        )
    }

    val rows = remember(snapshot, customerName) {
        val list = mutableListOf<StatementRow>()
        val reconciled = FinanceEngine.reconciledRepReturnIds(snapshot.calculator)
        val directSales = snapshot.sales.filter { it.deletedAt == null && it.customerName.equals(customerName, true) }
        val repSales = snapshot.repRecords.filter { it.deletedAt == null && it.customerName.equals(customerName, true) }
        val directHonor = FinanceEngine.allocHonor(directSales)
        val repHonor = FinanceEngine.allocHonor(repSales)

        val directHasOldDebt = directSales.any { it.transactionType == "OLD_DEBT" }
        val repHasOldDebt = repSales.any { it.transactionType == "OLD_DEBT" }
        val opening = (if (directHasOldDebt) 0.0 else snapshot.customers.filter { it.deletedAt == null && it.name.equals(customerName, true) }.sumOf { it.oldDebit }) +
            (if (repHasOldDebt) 0.0 else snapshot.clients.filter { it.deletedAt == null && it.name.equals(customerName, true) }.sumOf { it.oldDebit })

        var running = opening
        if (opening != 0.0) {
            list.add(
                StatementRow(
                    date = "Opening",
                    description = "Opening Balance",
                    debit = if (opening > 0 && !masked) InputGuards.fmtNum(opening) else "-",
                    credit = "-",
                    balance = if (masked) "••••" else InputGuards.fmtNum(running)
                )
            )
        }

        val combined = (directSales.map { it to false } + repSales.map { it to true })
            .sortedWith(compareBy({ it.first.date }, { it.first.timestamp }))

        combined.forEach { (s, isRep) ->
            val custPool = if (isRep) snapshot.clients.ifEmpty { snapshot.customers } else snapshot.customers
            val gross = FinanceEngine.effectiveSaleValue(s, custPool, snapshot.stores)
            val split = FinanceEngine.ledgerSplit(
                t = s,
                grossValue = gross,
                reconciledReturnIds = reconciled,
                honor = if (isRep) repHonor else directHonor
            )
            running = InputGuards.round2(running + split.delta)
            val qtyTxt = if (s.quantity > 0.0) " ${InputGuards.formatKg(s.quantity)}" else if (s.returnedQty > 0.0) " ${InputGuards.formatKg(s.returnedQty)}" else ""
            val desc = if (isRep) "Rep ${s.paymentType} (${s.salesRep})$qtyTxt" else "Direct ${s.paymentType}$qtyTxt"
            list.add(
                StatementRow(
                    date = s.date,
                    description = desc,
                    debit = if (split.debit > 0.005) (if (masked) "••••" else InputGuards.fmtNum(split.debit)) else "-",
                    credit = if (split.credit > 0.005) (if (masked) "••••" else InputGuards.fmtNum(split.credit)) else "-",
                    balance = if (masked) "••••" else InputGuards.fmtNum(running)
                )
            )
        }
        list
    }

    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val isSalesRestricted = remember(session) {
        FinanceEngine.isMoneyRestricted(session) && "sales" in session.tabs
    }
    CustomerLedgerBody(
        snapshot = snapshot,
        customerName = customerName,
        customer = customer,
        isRepClient = isRepClient,
        balance = balance,
        rows = rows,
        currency = currency,
        masked = masked,
        isSalesRestricted = isSalesRestricted,
        isAdmin = session.mode == "admin",
        vm = vm,
        onBack = onBack,
        onEditCustomer = onEditCustomer,
        onMoneyOut = { showMoneyOut = true }
    )

    if (showMoneyOut) {
        CustomerMoneyOutDialog(
            customerName = customerName,
            currentOutstanding = balance,
            currency = currency,
            masked = masked,
            defaultDate = DateUtils.todayIso(),
            onConfirm = { mDate, mAmt, mLedger, mNotes ->
                showMoneyOut = false
                vm.submitSale(
                    date = mDate,
                    supplyDate = mDate,
                    paymentType = "MONEY_OUT",
                    customer = customerName,
                    store = snapshot.stores.firstOrNull { it.deletedAt == null && it.active },
                    rep = "NONE",
                    qty = 0.0,
                    rate = 0.0,
                    collectedAmount = mAmt,
                    expiredKg = 0.0,
                    choraRate = 0.0,
                    addToLedger = mLedger,
                    notes = mNotes
                )
            },
            onDismiss = { showMoneyOut = false }
        )
    }
}

private data class EntityTimelineEvent(
    val date: String,
    val timestamp: Long,
    val title: String,
    val subtitle: String,
    val isIn: Boolean,
    val amount: Double,
    val runningBalance: Double
)

@Composable
fun EntityDetailScreen(
    snapshot: WorkspaceSnapshot,
    entityName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = FinanceEngine.deriveSession(snapshot.profile).restrictMoney || snapshot.settings.hideSensitiveMoney
    var menuOpen by remember { mutableStateOf(false) }

    val entity = remember(snapshot.entities, entityName) {
        snapshot.entities.firstOrNull { it.deletedAt == null && it.name.equals(entityName, true) }
    }
    val balance = remember(snapshot, entity) {
        entity?.let { FinanceEngine.computeEntityBalance(it, snapshot.transactions, snapshot.inventory) } ?: 0.0
    }
    val (rows, events, totalIn, totalOut) = remember(snapshot, entityName, entity, masked) {
        var running = entity?.openingBalance ?: 0.0
        var sumIn = 0.0
        var sumOut = 0.0
        val list = mutableListOf<StatementRow>()
        val timeline = mutableListOf<EntityTimelineEvent>()
        if (running != 0.0) {
            list.add(
                StatementRow(
                    date = "Opening",
                    description = "Opening Balance",
                    debit = "-",
                    credit = "-",
                    balance = if (masked) "••••" else InputGuards.fmtNum(running)
                )
            )
        }
        val txEvents = snapshot.transactions
            .filter {
                it.deletedAt == null && !it.isPayable &&
                    ((entity != null && it.entityId == entity.id) || it.entityName.equals(entityName, true))
            }
            .map { tx ->
                val isIn = tx.type.equals("IN", true)
                val delta = if (isIn) tx.amount else -tx.amount
                Triple(
                    tx.date,
                    tx.timestamp,
                    Triple(
                        (if (isIn) "Payment in" else "Payment out") to (tx.description.ifBlank { tx.paymentMethod }),
                        delta,
                        tx.amount to isIn
                    )
                )
            }
        val lotEvents = snapshot.inventory
            .filter {
                it.deletedAt == null && it.paymentStatus != "paid" && it.totalPayable > 0.0 &&
                    ((entity != null && it.supplierId == entity.id) || it.supplierName.equals(entityName, true))
            }
            .map { lot ->
                val dateStr = lot.purchaseDate.ifBlank { "Purchase" }
                Triple(
                    dateStr,
                    lot.timestamp,
                    Triple(
                        "Payment in" to "Material purchase: ${lot.materialName.ifBlank { lot.name }}",
                        -lot.totalPayable,
                        lot.totalPayable to false
                    )
                )
            }
        (txEvents + lotEvents)
            .sortedWith(compareBy({ it.first }, { it.second }))
            .forEach { (date, ts, details) ->
                val (titlePair, delta, amtPair) = details
                val (title, sub) = titlePair
                val (amt, isIn) = amtPair
                if (isIn) sumIn += amt else sumOut += amt
                running = InputGuards.round2(running + delta)
                list.add(
                    StatementRow(
                        date = date,
                        description = if (sub.isNotBlank()) "$title • $sub" else title,
                        debit = if (!isIn) (if (masked) "••••" else InputGuards.fmtNum(amt)) else "-",
                        credit = if (isIn) (if (masked) "••••" else InputGuards.fmtNum(amt)) else "-",
                        balance = if (masked) "••••" else InputGuards.fmtNum(running)
                    )
                )
                timeline.add(
                    EntityTimelineEvent(
                        date = date.take(10),
                        timestamp = ts,
                        title = title,
                        subtitle = sub,
                        isIn = isIn,
                        amount = amt,
                        runningBalance = running
                    )
                )
            }
        QuadResult(list, timeline, InputGuards.round2(sumIn), InputGuards.round2(sumOut))
    }

    val dayGroups = remember(events) {
        events.reversed().groupBy { it.date }
            .entries.sortedByDescending { it.key }
            .map { it.key to it.value }
    }

    val entPhotoUri = entity?.id?.let { eid -> snapshot.photos["entity:$eid"]?.takeIf { !it.deleted }?.dataUri }
    val phoneText = entity?.phone.orEmpty().ifBlank { entity?.wallet.orEmpty() }
    val isSettled = abs(balance) < 0.01

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = entityName.uppercase(),
            subtitle = phoneText.takeIf { it.isNotBlank() } ?: entity?.type?.uppercase(),
            onBack = onBack,
            leadingIcon = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accents.softPillFill)
                        .border(2.dp, accents.emerald, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!entPhotoUri.isNullOrBlank()) {
                        DecodedDataUrlImage(
                            dataUrl = entPhotoUri,
                            contentDescription = "$entityName photo",
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PersonOutline,
                            contentDescription = null,
                            tint = accents.emerald,
                            modifier = Modifier.size(22.dp)
                        )
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
                        modifier = Modifier.size(48.dp).testTag("entity_menu_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More actions",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export Statement PDF") },
                            onClick = {
                                menuOpen = false
                                PdfExporter.exportAndShareStatement(
                                    context = context,
                                    title = "Entity Statement: $entityName",
                                    subtitle = entity?.type?.uppercase() ?: "Account",
                                    rows = rows,
                                    summaryFooter = "Closing Balance: ${InputGuards.formatMoney(balance, currency, masked)}"
                                )
                            },
                            modifier = Modifier.testTag("entity_pdf_btn")
                        )
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Balance Hero Card (matches g03-entity-opened)
            item {
                SarimCard {
                    Text(
                        "Balance",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = when {
                            masked -> "••••"
                            isSettled -> "Settled"
                            else -> InputGuards.formatMoney(abs(balance), currency, false)
                        },
                        fontFamily = JakartaFont,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        color = if (balance >= -0.01) accents.emerald else accents.danger
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.width(3.dp).height(34.dp).clip(CircleShape).background(accents.emerald)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "TOTAL IN",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.4.sp),
                                    color = accents.textMuted
                                )
                                Text(
                                    InputGuards.formatMoney(totalIn, currency, masked),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = accents.emerald
                                )
                            }
                        }
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.width(3.dp).height(34.dp).clip(CircleShape).background(accents.danger)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "TOTAL OUT",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.4.sp),
                                    color = accents.textMuted
                                )
                                Text(
                                    InputGuards.formatMoney(totalOut, currency, masked),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = accents.danger
                                )
                            }
                        }
                    }
                }
            }

            if (dayGroups.isEmpty()) {
                item { EmptyStateCard("No ledger activity", "No recorded payments for $entityName.") }
            } else {
                items(dayGroups, key = { it.first }) { (dayIso, dayEvents) ->
                    val parsedDay = DateUtils.parseLocalDate(dayIso)
                    val dayNum = parsedDay?.dayOfMonth?.toString() ?: dayIso.takeLast(2)
                    val dayDow = parsedDay?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) ?: ""
                    SarimCard {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(
                                modifier = Modifier.width(34.dp).padding(top = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    dayNum,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    dayDow,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = accents.textMuted
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                dayEvents.forEachIndexed { idx, ev ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(
                                                    Modifier.size(8.dp).clip(CircleShape).background(
                                                        if (ev.isIn) accents.emerald else accents.danger
                                                    )
                                                )
                                                Text(
                                                    ev.title,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            if (ev.subtitle.isNotBlank()) {
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    ev.subtitle,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = accents.textMuted,
                                                    maxLines = 2
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                (if (ev.isIn) "+ " else "- ") + InputGuards.formatMoney(ev.amount, currency, masked),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (ev.isIn) accents.emerald else accents.danger
                                            )
                                            Text(
                                                InputGuards.formatMoney(ev.runningBalance, currency, masked),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accents.emerald
                                            )
                                        }
                                    }
                                    if (idx < dayEvents.lastIndex) {
                                        HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.5f))
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Surface(
                                        onClick = {
                                            val dayRows = dayEvents.reversed().map { ev ->
                                                StatementRow(
                                                    date = ev.date,
                                                    description = if (ev.subtitle.isNotBlank()) "${ev.title} — ${ev.subtitle}" else ev.title,
                                                    debit = if (!ev.isIn) InputGuards.formatMoney(ev.amount, currency, masked) else "-",
                                                    credit = if (ev.isIn) InputGuards.formatMoney(ev.amount, currency, masked) else "-",
                                                    balance = InputGuards.formatMoney(ev.runningBalance, currency, masked)
                                                )
                                            }
                                            PdfExporter.exportAndShareStatement(
                                                context = context,
                                                title = "Invoice: $entityName",
                                                subtitle = DateUtils.formatPretty(dayIso),
                                                rows = dayRows,
                                                summaryFooter = "Balance: ${InputGuards.formatMoney(dayEvents.first().runningBalance, currency, masked)}"
                                            )
                                        },
                                        shape = RoundedCornerShape(999.dp),
                                        color = accents.softPillFill,
                                        border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.6f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Share,
                                                contentDescription = null,
                                                tint = accents.emerald,
                                                modifier = Modifier.size(15.dp)
                                            )
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
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private data class QuadResult<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

