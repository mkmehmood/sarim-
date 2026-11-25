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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimHalfRingKpiBox
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.util.PdfExporter
import com.gullzubair.sarim.util.StatementRow

@Composable
fun CustomerDetailScreen(
    snapshot: WorkspaceSnapshot,
    customerName: String,
    onBack: () -> Unit,
    onEditCustomer: () -> Unit
) {
    val context = LocalContext.current
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val masked = snapshot.settings.hideSensitiveMoney

    val customer = remember(snapshot.customers, customerName) {
        snapshot.customers.firstOrNull { !it.recycled && it.name.equals(customerName, true) }
    }
    val balance = remember(snapshot, customerName) {
        FinanceEngine.computeCustomerBalance(customerName, snapshot.customers, snapshot.sales, snapshot.repRecords)
    }

    val rows = remember(snapshot, customerName) {
        val list = mutableListOf<StatementRow>()
        val sales = snapshot.sales.filter { !it.recycled && it.customerName.equals(customerName, true) }
        val rep = snapshot.repRecords.filter { !it.recycled && it.customerName.equals(customerName, true) }
        val opening = snapshot.customers.filter { !it.recycled && it.name.equals(customerName, true) }.sumOf { it.openingBalance }
        var running = opening
        if (opening != 0.0) {
            list.add(StatementRow("Opening", "Opening Balance", if (opening > 0) InputGuards.formatNum(opening) else "-", "-", InputGuards.formatNum(running)))
        }
        val combined = (sales.map { Triple(it.date, "Direct ${it.mode} ${InputGuards.formatKg(it.qtyKg)}", it.creditAmount to (it.cashReceived + it.choraValue)) } +
            rep.map { Triple(it.date, "Rep ${it.mode} (${it.repName})", it.creditAmount to if (it.mode == "return") it.totalAmount else it.cashReceived) })
            .sortedBy { it.first }
        combined.forEach { (date, desc, pair) ->
            val debit = pair.first
            val credit = pair.second
            running = InputGuards.round2(running + debit - credit)
            list.add(
                StatementRow(
                    date = date,
                    description = desc,
                    debit = if (debit > 0) InputGuards.formatNum(debit) else "-",
                    credit = if (credit > 0) InputGuards.formatNum(credit) else "-",
                    balance = InputGuards.formatNum(running)
                )
            )
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = customerName.uppercase(),
            subtitle = customer?.phone?.takeIf { it.isNotBlank() },
            onBack = onBack,
            trailingContent = {
                Surface(
                    onClick = onEditCustomer,
                    shape = RoundedCornerShape(14.dp),
                    color = accents.softPillFill,
                    border = BorderStroke(1.dp, accents.emerald),
                    modifier = Modifier.testTag("customer_edit_btn")
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Customer", tint = accents.emerald, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Edit", style = MaterialTheme.typography.labelLarge, color = accents.emerald)
                    }
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                SarimCard {
                    SarimHalfRingKpiBox(
                        label = "CUSTOMER BALANCE",
                        value = InputGuards.formatMoney(balance, currency, masked),
                        ringColor = if (balance > 0) accents.gold else accents.emerald,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!customer?.address.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Address: ${customer?.address}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    }
                    Spacer(Modifier.height(12.dp))
                    SarimGradientButton(
                        text = "Export Statement PDF",
                        onClick = {
                            PdfExporter.exportAndShareStatement(
                                context = context,
                                title = "Customer Statement: $customerName",
                                subtitle = "Gull & Zubair Naswar Dealers",
                                rows = rows,
                                summaryFooter = "Closing Balance: ${InputGuards.formatMoney(balance, currency, false)}"
                            )
                        },
                        testTag = "customer_pdf_btn"
                    )
                }
            }
            if (rows.isEmpty()) {
                item { EmptyStateCard("No transactions", "No recorded activity for $customerName yet.") }
            } else {
                items(rows.reversed()) { r ->
                    SarimCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.description, style = MaterialTheme.typography.titleSmall)
                                Text("${r.date} • Debit ${r.debit} • Credit ${r.credit}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                            }
                            Text("$currency ${r.balance}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.cyan)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
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
    val masked = snapshot.settings.hideSensitiveMoney
    val entity = remember(snapshot.entities, entityName) {
        snapshot.entities.firstOrNull { !it.recycled && it.name.equals(entityName, true) }
    }
    val balance = remember(snapshot, entity) {
        entity?.let { FinanceEngine.computeEntityBalance(it, snapshot.transactions, snapshot.factory) } ?: 0.0
    }
    val rows = remember(snapshot, entityName, entity) {
        var running = entity?.openingBalance ?: 0.0
        val list = mutableListOf<StatementRow>()
        if (running != 0.0) {
            list.add(StatementRow("Opening", "Opening Balance", "-", "-", InputGuards.formatNum(running)))
        }
        snapshot.transactions.filter { !it.recycled && it.entityName.equals(entityName, true) }
            .sortedBy { it.date }
            .forEach { tx ->
                val isOut = tx.txType in listOf("out", "transfer_out", "expense")
                running = InputGuards.round2(running + if (isOut) tx.amount else -tx.amount)
                list.add(
                    StatementRow(
                        date = tx.date,
                        description = "${tx.txType.uppercase()} • ${tx.paymentChannel}",
                        debit = if (isOut) InputGuards.formatNum(tx.amount) else "-",
                        credit = if (!isOut) InputGuards.formatNum(tx.amount) else "-",
                        balance = InputGuards.formatNum(running)
                    )
                )
            }
        list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = entityName.uppercase(),
            subtitle = entity?.entityType?.uppercase(),
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                SarimCard {
                    SarimHalfRingKpiBox(
                        label = "ENTITY BALANCE",
                        value = InputGuards.formatMoney(balance, currency, masked),
                        ringColor = if (balance >= 0) accents.emerald else accents.danger,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    SarimGradientButton(
                        text = "Export Statement PDF",
                        onClick = {
                            PdfExporter.exportAndShareStatement(
                                context = context,
                                title = "Entity Statement: $entityName",
                                subtitle = entity?.entityType?.uppercase() ?: "Account",
                                rows = rows,
                                summaryFooter = "Closing Balance: ${InputGuards.formatMoney(balance, currency, false)}"
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
                            Text("$currency ${r.balance}", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.emerald)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
