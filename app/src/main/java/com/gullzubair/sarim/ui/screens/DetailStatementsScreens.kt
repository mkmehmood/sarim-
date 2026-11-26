package com.gullzubair.sarim.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
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
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.PdfExporter
import com.gullzubair.sarim.util.StatementRow

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
    val entity = remember(snapshot.entities, entityName) {
        snapshot.entities.firstOrNull { it.deletedAt == null && it.name.equals(entityName, true) }
    }
    val balance = remember(snapshot, entity) {
        entity?.let { FinanceEngine.computeEntityBalance(it, snapshot.transactions, snapshot.inventory) } ?: 0.0
    }
    val rows = remember(snapshot, entityName, entity, masked) {
        var running = entity?.openingBalance ?: 0.0
        val list = mutableListOf<StatementRow>()
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
                val delta = if (tx.type.equals("IN", true)) tx.amount else -tx.amount
                Triple(tx.date, tx.timestamp, Triple("${tx.type.uppercase()} • ${tx.paymentMethod}", delta, tx.amount to tx.type.equals("IN", true)))
            }
        val lotEvents = snapshot.inventory
            .filter {
                it.deletedAt == null && it.paymentStatus != "paid" && it.totalPayable > 0.0 &&
                    ((entity != null && it.supplierId == entity.id) || it.supplierName.equals(entityName, true))
            }
            .map { lot ->
                val dateStr = lot.purchaseDate.ifBlank { "Purchase" }
                Triple(dateStr, lot.timestamp, Triple("PURCHASE • ${lot.materialName.ifBlank { lot.name }}", -lot.totalPayable, lot.totalPayable to false))
            }
        (txEvents + lotEvents)
            .sortedWith(compareBy({ it.first }, { it.second }))
            .forEach { (date, _, details) ->
                val (desc, delta, amtPair) = details
                val (amt, isDebitSide) = amtPair
                running = InputGuards.round2(running + delta)
                list.add(
                    StatementRow(
                        date = date,
                        description = desc,
                        debit = if (!isDebitSide) (if (masked) "••••" else InputGuards.fmtNum(amt)) else "-",
                        credit = if (isDebitSide) (if (masked) "••••" else InputGuards.fmtNum(amt)) else "-",
                        balance = if (masked) "••••" else InputGuards.fmtNum(running)
                    )
                )
            }
        list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = entityName.uppercase(),
            subtitle = entity?.type?.uppercase(),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                val entPhotoUri = entity?.id?.let { eid -> snapshot.photos["entity:$eid"]?.takeIf { !it.deleted }?.dataUri }
                SarimCard {
                    if (!entPhotoUri.isNullOrBlank()) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DecodedDataUrlImage(
                                dataUrl = entPhotoUri,
                                contentDescription = "$entityName photo",
                                modifier = Modifier.size(52.dp).clip(CircleShape)
                            )
                            Column {
                                Text(entityName, style = MaterialTheme.typography.titleMedium)
                                if (entity.wallet.isNotBlank() || entity.ledgerPage.isNotBlank()) {
                                    Text(
                                        listOfNotNull(
                                            entity.wallet.takeIf { it.isNotBlank() },
                                            entity.ledgerPage.takeIf { it.isNotBlank() }?.let { "Page $it" }
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = accents.textMuted
                                    )
                                }
                            }
                        }
                    }
                    SarimHalfRingKpiBox(
                        label = "ENTITY BALANCE",
                        value = InputGuards.formatMoney(balance, currency, masked),
                        ringColor = if (balance >= 0) accents.emerald else accents.danger,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (entPhotoUri.isNullOrBlank() && entity != null && (entity.wallet.isNotBlank() || entity.ledgerPage.isNotBlank())) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            listOfNotNull(
                                entity.wallet.takeIf { it.isNotBlank() },
                                entity.ledgerPage.takeIf { it.isNotBlank() }?.let { "Ledger Page: $it" }
                            ).joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = accents.textMuted
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SarimGradientButton(
                        text = "Export Statement PDF",
                        onClick = {
                            PdfExporter.exportAndShareStatement(
                                context = context,
                                title = "Entity Statement: $entityName",
                                subtitle = entity?.type?.uppercase() ?: "Account",
                                rows = rows,
                                summaryFooter = "Closing Balance: ${InputGuards.formatMoney(balance, currency, masked)}"
                            )
                        },
                        testTag = "entity_pdf_btn"
                    )
                }
            }
            if (rows.isEmpty()) {
                item { EmptyStateCard("No ledger activity", "No recorded payments for $entityName.") }
            } else {
                items(rows.reversed()) { r ->
                    SarimCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.description, style = MaterialTheme.typography.titleSmall)
                                Text("${r.date} • Debit ${r.debit} • Credit ${r.credit}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                            }
                            Text(if (masked) "$currency ••••" else "$currency ${r.balance}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
