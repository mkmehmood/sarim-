package com.gullzubair.sarim.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.EditedBadge
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimDangerButton
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.ui.components.ConfirmRequest
import com.gullzubair.sarim.ui.components.ConfirmRequestHost
import com.gullzubair.sarim.ui.components.deleteConfirmMessage
import com.gullzubair.sarim.ui.components.saveConfirmRequest
import com.gullzubair.sarim.ui.components.DialogTone

@Composable
fun RawMaterialScreen(
    snapshot: WorkspaceSnapshot,
    todayIso: String,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val currency = snapshot.settings.currencySymbol
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val masked = session.restrictMoney || snapshot.settings.hideSensitiveMoney
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }

    var date by remember(todayIso) { mutableStateOf(todayIso) }
    var name by remember { mutableStateOf("") }
    var matMenuOpen by remember { mutableStateOf(false) }
    var unitName by remember { mutableStateOf("") }
    var unitsBoughtStr by remember { mutableStateOf("") }
    var weightPerUnitStr by remember { mutableStateOf("1") }
    var costPerUnitStr by remember { mutableStateOf("") }
    var supplierMode by remember { mutableStateOf("none") } // none | existing | new
    var supplier by remember { mutableStateOf("") }
    var paidStr by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }

    val materials = remember(snapshot.inventory) { FinanceEngine.materialRecords(snapshot.inventory) }
    val existingInv = remember(materials, name) {
        materials.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
    }
    val allLots = remember(snapshot.inventory) {
        snapshot.inventory.filter { it.deletedAt == null && it.isPurchaseLot }
    }
    val collectedKg = remember(existingInv, materials) {
        existingInv?.let { FinanceEngine.collectedKg(it) } ?: materials.sumOf { FinanceEngine.collectedKg(it) }
    }
    val inStockKg = existingInv?.quantity ?: materials.sumOf { it.quantity }
    val pendingSuppliers = remember(existingInv, allLots) {
        if (existingInv != null) {
            FinanceEngine.materialPending(existingInv, FinanceEngine.purchasesOf(allLots, existingInv.id))
        } else {
            allLots.filter { it.paymentStatus != "paid" }.sumOf { it.totalPayable }
        }
    }

    val unitsBought = unitsBoughtStr.toDoubleOrNull() ?: 0.0
    val factor = weightPerUnitStr.toDoubleOrNull() ?: 1.0
    val costPerUnit = costPerUnitStr.toDoubleOrNull() ?: 0.0
    val addedKg = InputGuards.round3(unitsBought * factor)
    val totalAmount = InputGuards.round2(unitsBought * costPerUnit)
    val costPerKg = if (addedKg > 0.0) InputGuards.round2(totalAmount / addedKg) else 0.0

    val supplierList = remember(snapshot.entities) {
        snapshot.entities.filter { it.deletedAt == null && (it.isSupplier || it.type == "supplier") }
            .map { it.name }.distinct().sorted()
    }
    val purchasesHistory = remember(allLots, name) {
        allLots.filter { name.isBlank() || it.materialName.equals(name.trim(), ignoreCase = true) }
            .groupBy { it.purchaseDate.ifBlank { "No date" } }
            .entries.sortedByDescending { it.key }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "ADD RAW MATERIAL", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SarimCard {
                    Text("Material", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Box(modifier = Modifier.weight(1.25f)) {
                            SarimPillInput(
                                value = name,
                                onValueChange = { name = it.take(80) },
                                label = "Search or add material",
                                placeholder = "Search saved materials or type...",
                                leadingIcon = { Icon(Icons.Rounded.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = if (materials.isNotEmpty()) {
                                    {
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = "Choose saved material",
                                            tint = accents.emerald,
                                            modifier = Modifier.clickable { matMenuOpen = true }.padding(12.dp).size(20.dp)
                                        )
                                    }
                                } else null,
                                testTag = "rm_name_input"
                            )
                            DropdownMenu(expanded = matMenuOpen, onDismissRequest = { matMenuOpen = false }) {
                                materials.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text("${item.name.uppercase()} (${InputGuards.formatKg(item.quantity)})") },
                                        onClick = {
                                            name = item.name
                                            unitName = item.purchaseUnitName
                                            weightPerUnitStr = InputGuards.fmtNum(item.conversionFactor).replace(",", "")
                                            matMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        DatePickerField("Date", date, { date = it }, "rm_date", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    StatsStrip(
                        listOf(
                            Triple("COLLECTED", InputGuards.formatKg(collectedKg), MaterialTheme.colorScheme.onSurface),
                            Triple("IN STOCK", InputGuards.formatKg(inStockKg), MaterialTheme.colorScheme.onSurface),
                            Triple("PENDING TO SUPPLIERS", InputGuards.formatMoney(pendingSuppliers, currency, masked), accents.emerald)
                        )
                    )
                }
            }

            item {
                SarimCard {
                    Text("New purchase", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SarimPillInput(
                            value = unitName,
                            onValueChange = { unitName = it.take(24) },
                            label = "Unit name",
                            placeholder = "bag, box, carton",
                            modifier = Modifier.weight(1f)
                        )
                        DecimalField("Units bought", unitsBoughtStr, { unitsBoughtStr = it }, "rm_qty_input", Modifier.weight(1f), placeholder = "0")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DecimalField("Weight per unit", weightPerUnitStr, { weightPerUnitStr = it }, "rm_factor_input", Modifier.weight(1f), suffix = "kg")
                        DecimalField("Cost per unit", costPerUnitStr, { costPerUnitStr = it }, "rm_cost_input", Modifier.weight(1f), prefix = currency)
                    }
                    Spacer(Modifier.height(12.dp))
                    StatsStrip(
                        listOf(
                            Triple("STOCK", InputGuards.formatKg(addedKg), MaterialTheme.colorScheme.onSurface),
                            Triple("COST PER KG", InputGuards.formatMoney(costPerKg, currency, masked), MaterialTheme.colorScheme.onSurface),
                            Triple("TOTAL AMOUNT", InputGuards.formatMoney(totalAmount, currency, masked), accents.emerald)
                        )
                    )
                }
            }

            item {
                SarimCard {
                    Text("Supplier", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("none" to "None", "existing" to "Existing", "new" to "New").forEach { (k, lbl) ->
                            val sel = supplierMode == k
                            Surface(
                                onClick = {
                                    supplierMode = k
                                    if (k == "none") supplier = ""
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = if (sel) accents.softPillFill else accents.inputFill,
                                border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    Text(lbl, style = MaterialTheme.typography.labelLarge, color = if (sel) accents.emerald else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    if (supplierMode == "existing") {
                        Spacer(Modifier.height(10.dp))
                        PickerField("Select Supplier", supplier, supplierList, { it }, { supplier = it }, "rm_supplier_picker")
                    } else if (supplierMode == "new") {
                        Spacer(Modifier.height(10.dp))
                        SarimPillInput(value = supplier, onValueChange = { supplier = it.take(80) }, label = "Supplier Name", testTag = "rm_supplier_input")
                    }
                    if (supplierMode != "none") {
                        Spacer(Modifier.height(10.dp))
                        DecimalField("Paid Now", paidStr, { paidStr = it }, "rm_paid_input")
                    }
                    Spacer(Modifier.height(10.dp))
                    SarimPillInput(value = notes, onValueChange = { notes = it.take(300) }, label = "NOTES (OPTIONAL)", testTag = "rm_notes_input")
                }
            }

            if (purchasesHistory.isNotEmpty()) {
                items(purchasesHistory.take(8).size) { idx ->
                    val (day, list) = purchasesHistory[idx]
                    var expanded by remember(day) { mutableStateOf(false) }
                    val dayTotal = list.sumOf { it.totalValue }
                    Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 4.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(DateUtils.formatPretty(day), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text("Purchased ${InputGuards.formatMoney(dayTotal, currency, masked)}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${list.size} ${if (list.size == 1) "entry" else "entries"}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                Icon(
                                    if (expanded) Icons.Rounded.KeyboardArrowDown else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = accents.textMuted
                                )
                            }
                        }
                        AnimatedVisibility(visible = expanded) {
                            Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                list.forEach { lot ->
                                    SarimCard(contentPadding = PaddingValues(12.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(lot.materialName.ifBlank { lot.name }, style = MaterialTheme.typography.titleSmall)
                                                EditedBadge(lot.isEdited)
                                            }
                                            Text(InputGuards.formatKg(lot.purchaseKg), color = accents.emerald, style = MaterialTheme.typography.labelLarge)
                                        }
                                        Text(
                                            "Total ${InputGuards.formatMoney(lot.totalValue, currency, masked)} • Payable ${InputGuards.formatMoney(lot.totalPayable, currency, masked)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = accents.textMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(color = accents.headerFrosted, border = BorderStroke(1.dp, accents.borderSubtle)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (existingInv != null) {
                    SarimDangerButton(
                        text = "Delete Material",
                        onClick = {
                            confirmReq = ConfirmRequest(
                                title = "Delete Material?",
                                message = deleteConfirmMessage(listOf(
                                    "Material" to existingInv.name,
                                    "In Stock" to InputGuards.formatKg(existingInv.quantity)
                                )),
                                confirmText = "Delete",
                                danger = true,
                                tag = "rm_confirm_delete_btn"
                            ) {
                                vm.softDeleteRecord(
                                    collection = "inventory",
                                    docId = existingInv.id,
                                    displayName = existingInv.name,
                                    displayDetail = "Raw material",
                                    displayAmount = InputGuards.formatKg(existingInv.quantity),
                                    snapshotMap = WorkspaceJsonCodec.inventoryToMap(existingInv)
                                )
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(0.85f)
                    )
                }
                SarimGradientButton(
                    text = "Save Purchase",
                    onClick = {
                        val paidNow = paidStr.toDoubleOrNull() ?: 0.0
                        val supplierName = if (supplierMode == "none") "" else supplier
                        val details = buildList {
                            add("Material" to name.trim())
                            add("Date" to DateUtils.formatPretty(date))
                            add("Quantity" to "${InputGuards.fmtNum(unitsBought)} ${unitName.ifBlank { "kg" }}")
                            add("Added Stock" to InputGuards.formatKg(addedKg))
                            add("Total Cost" to InputGuards.formatMoney(totalAmount, currency, masked))
                            if (paidNow > 0.0) add("Paid Now" to InputGuards.formatMoney(paidNow, currency, masked))
                            if (supplierName.isNotBlank()) add("Supplier" to supplierName)
                        }
                        confirmReq = saveConfirmRequest(
                            label = "Purchase",
                            details = details,
                            gated = !snapshot.profile.isAdmin,
                            tag = "rm_confirm_save_btn"
                        ) {
                            vm.submitRawPurchase(
                                date = date,
                                name = name,
                                unitName = unitName.ifBlank { "kg" },
                                factor = factor,
                                qtyUnits = unitsBought,
                                costPerUnit = costPerUnit,
                                paidNow = paidNow,
                                supplier = supplierName,
                                notes = notes
                            )
                            onBack()
                        }
                    },
                    enabled = name.isNotBlank() && unitsBought > 0.0 && factor > 0.0,
                    testTag = "rm_save_btn",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatsStrip(cols: List<Triple<String, String, Color>>) {
    val accents = LocalSarimAccents.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = accents.softPillFill,
        border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            cols.forEach { (k, v, c) ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(k, style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                    Spacer(Modifier.height(2.dp))
                    Text(v, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = c)
                }
            }
        }
    }
}
