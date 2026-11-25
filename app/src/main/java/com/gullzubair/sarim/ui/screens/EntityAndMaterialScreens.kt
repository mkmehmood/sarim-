package com.gullzubair.sarim.ui.screens

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.local.WorkspaceJsonCodec
import com.gullzubair.sarim.data.model.CustomerRecord
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DatePickerField
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimDangerButton
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                                placeholder = "Search saved or type new",
                                leadingIcon = { Icon(Icons.Outlined.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = if (materials.isNotEmpty()) {
                                    {
                                        Text(
                                            "▾",
                                            modifier = Modifier.clickable { matMenuOpen = true }.padding(8.dp),
                                            color = accents.emerald
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
                        DecimalField("Units bought", unitsBoughtStr, { unitsBoughtStr = it }, "rm_qty_input", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DecimalField("Weight per unit", weightPerUnitStr, { weightPerUnitStr = it }, "rm_factor_input", Modifier.weight(1f), suffix = "kg")
                        DecimalField("Cost per unit", costPerUnitStr, { costPerUnitStr = it }, "rm_cost_input", Modifier.weight(1f))
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
                                shape = RoundedCornerShape(18.dp),
                                color = if (sel) accents.softPillFill else accents.inputFill,
                                border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                                Icon(if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight, contentDescription = null)
                            }
                        }
                        AnimatedVisibility(visible = expanded) {
                            Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                list.forEach { lot ->
                                    SarimCard(contentPadding = PaddingValues(12.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(lot.materialName.ifBlank { lot.name }, style = MaterialTheme.typography.titleSmall)
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
                            vm.softDeleteRecord(
                                collection = "inventory",
                                docId = existingInv.id,
                                displayName = existingInv.name,
                                displayDetail = "Raw material",
                                displayAmount = InputGuards.formatKg(existingInv.quantity),
                                snapshotMap = WorkspaceJsonCodec.inventoryToMap(existingInv)
                            )
                            onBack()
                        },
                        modifier = Modifier.weight(0.85f)
                    )
                }
                SarimGradientButton(
                    text = "Save Purchase",
                    onClick = {
                        vm.submitRawPurchase(
                            date = date,
                            name = name,
                            unitName = unitName.ifBlank { "kg" },
                            factor = factor,
                            qtyUnits = unitsBought,
                            costPerUnit = costPerUnit,
                            paidNow = paidStr.toDoubleOrNull() ?: 0.0,
                            supplier = if (supplierMode == "none") "" else supplier,
                            notes = notes
                        )
                        onBack()
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
        shape = RoundedCornerShape(14.dp),
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

@Composable
fun AddEntityScreen(vm: SarimViewModel, onBack: () -> Unit) {
    val accents = LocalSarimAccents.current
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("party") }
    var phone by remember { mutableStateOf("") }
    var walletDetails by remember { mutableStateOf("") }
    var bookPage by remember { mutableStateOf("") }
    var openingStr by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUri = uri
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "ADD ENTITY", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SarimCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AvatarPickerBadge(photoUri = photoUri) {
                            photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            SarimPillInput(
                                value = name,
                                onValueChange = { name = it.take(80) },
                                label = "Entity Name",
                                placeholder = "Enter name...",
                                testTag = "ent_name_input"
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("party", "supplier", "staff", "expense", "bank").forEach { t ->
                                    val sel = type == t
                                    Surface(
                                        onClick = { type = t },
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (sel) accents.softPillFill else accents.inputFill,
                                        border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                        modifier = Modifier.defaultMinSize(minHeight = 36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                            Text(t.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = if (sel) accents.emerald else MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SarimCard {
                    Text("Contact", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(10.dp))
                    SarimPillInput(
                        value = phone,
                        onValueChange = { phone = it.take(32) },
                        label = "Phone number",
                        placeholder = "0300-0000000",
                        leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        testTag = "ent_phone_input"
                    )
                    Spacer(Modifier.height(10.dp))
                    SarimPillInput(
                        value = walletDetails,
                        onValueChange = { walletDetails = it.take(200) },
                        label = "Wallet / account details",
                        placeholder = "Bank IBAN, JazzCash, EasyPaisa...",
                        leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }

            item {
                SarimCard {
                    Text("Account", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(10.dp))
                    DecimalField("Old balance (payable / receivable)", openingStr, { openingStr = it }, "ent_opening_input")
                    Spacer(Modifier.height(10.dp))
                    SarimPillInput(
                        value = bookPage,
                        onValueChange = { bookPage = it.take(80) },
                        label = "Manual ledger book page",
                        placeholder = "Page # / reference"
                    )
                }
            }
        }

        Surface(color = accents.headerFrosted, border = BorderStroke(1.dp, accents.borderSubtle)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                SarimGradientButton(
                    text = "Save Entity",
                    onClick = {
                        val packedNotes = listOfNotNull(
                            walletDetails.trim().takeIf { it.isNotBlank() },
                            bookPage.trim().takeIf { it.isNotBlank() }?.let { "Book Page: $it" }
                        ).joinToString(" • ")
                        vm.addOrUpdateEntity(null, name, type, phone, openingStr.toDoubleOrNull() ?: 0.0, packedNotes)
                        onBack()
                    },
                    enabled = name.isNotBlank(),
                    testTag = "ent_save_btn"
                )
            }
        }
    }
}

@Composable
fun EditCustomerScreen(
    snapshot: WorkspaceSnapshot,
    customerName: String,
    isRepClient: Boolean = false,
    vm: SarimViewModel,
    onBack: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val context = LocalContext.current
    val currency = snapshot.settings.currencySymbol

    val sourceList = if (isRepClient) snapshot.clients else snapshot.customers
    val existing = remember(sourceList, customerName) {
        sourceList.firstOrNull { it.deletedAt == null && it.name.equals(customerName, ignoreCase = true) }
            ?: CustomerRecord(name = customerName)
    }
    val initialCustomPrice = remember(existing) {
        when {
            existing.customSalePrice > 0.0 -> InputGuards.fmtNum(existing.customSalePrice).replace(",", "")
            else -> Regex("rate:([0-9.]+)").find(existing.notes)?.groupValues?.getOrNull(1) ?: ""
        }
    }
    val initialPage = remember(existing.notes) {
        Regex("page:([^;\\n]+)").find(existing.notes)?.groupValues?.getOrNull(1)?.trim() ?: existing.notes
    }

    var name by remember(existing) { mutableStateOf(existing.name) }
    var phone by remember(existing) { mutableStateOf(existing.phone) }
    var address by remember(existing) { mutableStateOf(existing.address) }
    var salesRep by remember(existing) { mutableStateOf(existing.salesRep) }
    var openingStr by remember(existing) {
        val currentBal = if (existing.id.isNotBlank()) existing.oldDebit
        else FinanceEngine.computeCustomerBalance(
            customerName,
            snapshot.customers,
            snapshot.sales,
            snapshot.repRecords,
            snapshot.calculator,
            snapshot.stores,
            snapshot.clients,
            includeClients = isRepClient
        )
        mutableStateOf(InputGuards.fmtNum(currentBal).replace(",", ""))
    }
    var customRateStr by remember(existing) { mutableStateOf(initialCustomPrice) }
    var bookPageStr by remember(existing) { mutableStateOf(initialPage) }
    var gpsLat by remember(existing) { mutableStateOf(existing.gpsLat) }
    var gpsLng by remember(existing) { mutableStateOf(existing.gpsLng) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUri = uri
    }
    val gpsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            LocationHelper.getBestLastLocation(context)?.let { (la, ln) ->
                gpsLat = la; gpsLng = ln
                if (address.isBlank()) address = "${String.format("%.4f", la)}, ${String.format("%.4f", ln)}"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = if (isRepClient) "EDIT REP CUSTOMER" else "EDIT CUSTOMER",
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SarimCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AvatarPickerBadge(photoUri = photoUri) {
                            photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            SarimPillInput(
                                value = name,
                                onValueChange = { name = it.take(80) },
                                label = "Customer Name",
                                testTag = "ec_name_input"
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (isRepClient) "Rep customer profile in clients collection." else "Customer profile in customers collection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = accents.textMuted
                            )
                        }
                    }
                }
            }

            item {
                SarimCard {
                    Text("Contact", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(10.dp))
                    SarimPillInput(
                        value = phone,
                        onValueChange = { phone = it.take(32) },
                        label = "Phone number",
                        placeholder = "0300-0000000",
                        leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        testTag = "ec_phone_input"
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Address / location", style = MaterialTheme.typography.labelSmall, color = accents.textMuted, modifier = Modifier.padding(bottom = 5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SarimPillInput(
                            value = address,
                            onValueChange = { address = it.take(200) },
                            placeholder = "Area, shop no., etc.",
                            leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = {
                                if (LocationHelper.hasLocationPermission(context)) {
                                    LocationHelper.getBestLastLocation(context)?.let { (la, ln) ->
                                        gpsLat = la; gpsLng = ln
                                        if (address.isBlank()) address = "${String.format("%.4f", la)}, ${String.format("%.4f", ln)}"
                                    }
                                } else {
                                    gpsLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = accents.softPillFill,
                            border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.45f)),
                            modifier = Modifier.size(54.dp).testTag("ec_gps_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Capture GPS", tint = accents.emerald)
                            }
                        }
                    }
                    if (snapshot.settings.reps.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        PickerField(
                            label = "Assigned Sales Rep",
                            selectedLabel = salesRep.ifBlank { "None" },
                            items = listOf("") + snapshot.settings.reps,
                            itemLabel = { it.ifBlank { "None" } },
                            onSelect = { salesRep = it },
                            testTag = "ec_rep_picker"
                        )
                    }
                }
            }

            item {
                SarimCard {
                    Text("Account", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(10.dp))
                    DecimalField("Previous balance (old debt)", openingStr, { openingStr = it }, "ec_opening_input", suffix = currency)
                    Spacer(Modifier.height(10.dp))
                    DecimalField("Custom sale price", customRateStr, { customRateStr = it }, "ec_rate_input", suffix = "/kg")
                    Spacer(Modifier.height(10.dp))
                    SarimPillInput(
                        value = bookPageStr,
                        onValueChange = { bookPageStr = it.take(120) },
                        label = "Manual ledger book page / notes",
                        placeholder = "e.g. page:176"
                    )
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
                if (existing.id.isNotBlank()) {
                    SarimDangerButton(
                        text = "Delete",
                        onClick = {
                            vm.softDeleteRecord(
                                collection = if (isRepClient) "clients" else "customers",
                                docId = existing.id,
                                displayName = existing.name,
                                displayDetail = existing.phone,
                                displayAmount = "",
                                snapshotMap = WorkspaceJsonCodec.customerToMap(existing)
                            )
                            onBack()
                        },
                        modifier = Modifier.weight(0.55f),
                        testTag = "ec_delete_btn"
                    )
                }
                SarimGradientButton(
                    text = "Update Customer",
                    onClick = {
                        vm.saveCustomerOrClient(
                            existing = existing.takeIf { it.id.isNotBlank() },
                            isRepClient = isRepClient,
                            name = name,
                            phone = phone,
                            address = address,
                            oldDebit = openingStr.toDoubleOrNull() ?: 0.0,
                            customSalePrice = customRateStr.toDoubleOrNull() ?: 0.0,
                            salesRep = salesRep,
                            notes = bookPageStr,
                            lat = gpsLat,
                            lng = gpsLng
                        )
                        onBack()
                    },
                    enabled = name.isNotBlank(),
                    testTag = "ec_save_btn",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AvatarPickerBadge(photoUri: Uri?, onPick: () -> Unit) {
    val accents = LocalSarimAccents.current
    val context = LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photoUri) {
        bitmap = if (photoUri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(photoUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(86.dp).clickable(onClick = onPick)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(accents.softPillFill)
                    .border(2.dp, accents.emerald, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = "Selected photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(38.dp))
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accents.emerald)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = "Pick photo", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Surface(
            onClick = onPick,
            shape = RoundedCornerShape(14.dp),
            color = accents.softPillFill,
            border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.35f)),
            modifier = Modifier.defaultMinSize(minHeight = 34.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(14.dp))
                Text("Camera", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = accents.emerald)
            }
        }
    }
}
