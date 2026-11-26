package com.gullzubair.sarim.ui.screens

import android.Manifest
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.DecimalField
import com.gullzubair.sarim.ui.components.DecodedDataUrlImage
import com.gullzubair.sarim.ui.components.EditHistorySection
import com.gullzubair.sarim.ui.components.PickerField
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimDangerButton
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.CaptionedPhotoSharer
import com.gullzubair.sarim.util.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.gullzubair.sarim.ui.components.ConfirmRequest
import com.gullzubair.sarim.ui.components.ConfirmRequestHost
import com.gullzubair.sarim.ui.components.deleteConfirmMessage
import com.gullzubair.sarim.ui.components.saveConfirmRequest
import com.gullzubair.sarim.ui.components.DialogTone

@Composable
fun AddEntityScreen(vm: SarimViewModel, onBack: () -> Unit) {
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }
    val accents = LocalSarimAccents.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var walletDetails by remember { mutableStateOf("") }
    var bookPage by remember { mutableStateOf("") }
    var isOpeningCredit by remember { mutableStateOf(true) }
    var openingStr by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUri = uri
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "Add Entity", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SarimCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AvatarPickerBadge(
                            photoUri = photoUri,
                            existingDataUrl = null,
                            cleared = false,
                            onPick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onClear = if (photoUri != null) { { photoUri = null } } else null
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            SarimPillInput(
                                value = name,
                                onValueChange = { name = it.take(80) },
                                label = "Entity Name",
                                placeholder = "Name",
                                testTag = "ent_name_input"
                            )
                        }
                    }
                }
            }

            item {
                SarimCard {
                    Text("Contact and payment details", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(10.dp))
                    SarimPillInput(
                        value = phone,
                        onValueChange = { phone = it.take(32) },
                        label = "Phone number",
                        placeholder = "03XX-XXXXXXX",
                        leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        testTag = "ent_phone_input"
                    )
                    Spacer(Modifier.height(10.dp))
                    SarimPillInput(
                        value = walletDetails,
                        onValueChange = { walletDetails = it.take(120) },
                        label = "Wallet / account details",
                        placeholder = "JazzCash, Easypaisa or bank account",
                        leadingIcon = { Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        testTag = "ent_wallet_input"
                    )
                    Spacer(Modifier.height(10.dp))
                    SarimPillInput(
                        value = bookPage,
                        onValueChange = { raw -> bookPage = raw.filter(Char::isDigit).take(12) },
                        label = "Manual ledger book page",
                        placeholder = "e.g. 45",
                        testTag = "ent_page_input"
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Shown as a small badge on the entity's photo.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                }
            }

            item {
                SarimCard {
                    Text("Account", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(10.dp))
                    Text("Old balance (already credit or debit)", style = MaterialTheme.typography.labelLarge, color = accents.textMuted)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            true to "Credit — we owe them (payable)",
                            false to "Debit — they owe us (receivable)"
                        ).forEach { (creditVal, label) ->
                            val sel = isOpeningCredit == creditVal
                            Surface(
                                onClick = { isOpeningCredit = creditVal },
                                shape = RoundedCornerShape(14.dp),
                                color = if (sel) accents.softPillFill else accents.inputFill,
                                border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (sel) accents.emerald else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    DecimalField("Old balance amount", openingStr, { openingStr = it }, "ent_opening_input")
                    Spacer(Modifier.height(4.dp))
                    Text("Balance carried over from before this app. Leave 0 for none.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
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
                    text = if (saving) "Saving..." else "Save Entity",
                    onClick = {
                        val absOpening = openingStr.toDoubleOrNull() ?: 0.0
                        val signedOpening = if (absOpening == 0.0) 0.0 else if (isOpeningCredit) absOpening else -absOpening
                        val entityDetails = buildList {
                            add("Name" to name.trim())
                            if (phone.isNotBlank()) add("Phone" to phone.trim())
                            if (absOpening != 0.0) {
                                add("Old Balance" to "${InputGuards.fmtNum(absOpening)} (${if (isOpeningCredit) "Credit" else "Debit"})")
                            }
                        }
                        confirmReq = saveConfirmRequest(
                            label = "Entity",
                            details = entityDetails,
                            gated = false,
                            verb = "Add",
                            tag = "entity_confirm_save_btn"
                        ) {
                            saving = true
                            scope.launch {
                                val dataUrl = photoUri?.let { uri ->
                                    withContext(Dispatchers.IO) {
                                        CaptionedPhotoSharer.compressUriToDataUrl(context, uri, maxDim = 900, quality = 80)
                                    }
                                }
                                vm.saveEntityExtended(
                                    existingId = null,
                                    name = name,
                                    type = "party",
                                    phone = phone,
                                    wallet = walletDetails,
                                    ledgerPage = bookPage,
                                    openingBalance = signedOpening,
                                    notes = "",
                                    photoDataUri = dataUrl,
                                    clearPhoto = false
                                ) {
                                    saving = false
                                    onBack()
                                }
                                saving = false
                            }
                        }
                    },
                    enabled = name.isNotBlank() && !saving,
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
    var confirmReq by remember { mutableStateOf<ConfirmRequest?>(null) }
    ConfirmRequestHost(confirmReq) { confirmReq = null }
    val accents = LocalSarimAccents.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currency = snapshot.settings.currencySymbol
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val masked = session.restrictMoney || snapshot.settings.hideSensitiveMoney
    val isAdmin = session.mode == "admin"
    val isOwnRepOnly = remember(session) { FinanceEngine.hasOwnRepAccess(session) && !isAdmin }
    val lockedRepName = remember(snapshot.settings.repProfile, snapshot.profile.displayName) {
        snapshot.settings.repProfile.ifBlank { snapshot.profile.displayName.ifBlank { "Rep" } }
    }

    val sourceList = if (isRepClient) snapshot.clients else snapshot.customers
    val existing = remember(sourceList, customerName) {
        sourceList.firstOrNull { it.deletedAt == null && it.name.equals(customerName, ignoreCase = true) }
            ?: CustomerRecord(name = customerName)
    }
    val photoKey = remember(existing.id, isRepClient) {
        if (existing.id.isNotBlank()) "${if (isRepClient) "client" else "cust"}:${existing.id}" else ""
    }
    val existingPhotoUrl = remember(snapshot.photos, photoKey) {
        if (photoKey.isBlank()) null else snapshot.photos[photoKey]?.takeIf { !it.deleted }?.dataUri
    }

    val initialCustomPrice = remember(existing) {
        when {
            existing.customSalePrice > 0.0 -> InputGuards.fmtNum(existing.customSalePrice).replace(",", "")
            else -> Regex("rate:([0-9.]+)").find(existing.notes)?.groupValues?.getOrNull(1) ?: ""
        }
    }
    val initialPage = remember(existing.ledgerPage, existing.notes) {
        existing.ledgerPage.ifBlank {
            Regex("page:([^;\\n]+)").find(existing.notes)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        }
    }
    val initialCleanNotes = remember(existing.notes) {
        existing.notes.replace(Regex("(page|rate):[^;\\n]+;?\\s*"), "").trim()
    }

    var name by remember(existing) { mutableStateOf(existing.name) }
    var phone by remember(existing) { mutableStateOf(existing.phone) }
    var address by remember(existing) { mutableStateOf(existing.address) }
    var salesRep by remember(existing, isRepClient, isOwnRepOnly, lockedRepName) {
        mutableStateOf(if (isRepClient && isOwnRepOnly) existing.salesRep.ifBlank { lockedRepName } else existing.salesRep)
    }
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
    var notesStr by remember(existing) { mutableStateOf(initialCleanNotes) }
    var gpsLat by remember(existing) { mutableStateOf(existing.gpsLat) }
    var gpsLng by remember(existing) { mutableStateOf(existing.gpsLng) }
    var photoUri by remember(existing.id) { mutableStateOf<Uri?>(null) }
    var clearPhoto by remember(existing.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            photoUri = uri
            clearPhoto = false
        }
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
            title = when {
                isRepClient && customerName.isBlank() -> "ADD REP CUSTOMER"
                isRepClient -> "EDIT REP CUSTOMER"
                customerName.isBlank() -> "ADD CUSTOMER"
                else -> "EDIT CUSTOMER"
            },
            onBack = onBack
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SarimCard {
                    val hasActivePhoto = photoUri != null || (!clearPhoto && !existingPhotoUrl.isNullOrBlank())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        AvatarPickerBadge(
                            photoUri = photoUri,
                            existingDataUrl = existingPhotoUrl,
                            cleared = clearPhoto,
                            onPick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onClear = if (hasActivePhoto) {
                                {
                                    photoUri = null
                                    clearPhoto = true
                                }
                            } else null
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            SarimPillInput(
                                value = name,
                                onValueChange = { name = it.take(80) },
                                label = "Customer Name",
                                placeholder = "Customer name",
                                testTag = "ec_name_input"
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = if (existing.id.isNotBlank()) "Editing the name will update all records for this customer" else "",
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
                        placeholder = "03XX-XXXXXXX",
                        leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        testTag = "ec_phone_input"
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Address / location", style = MaterialTheme.typography.labelSmall, color = accents.textMuted, modifier = Modifier.padding(bottom = 5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SarimPillInput(
                            value = address,
                            onValueChange = { address = it.take(200) },
                            placeholder = "Area, shop no., etc.",
                            leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp)) },
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
                            shape = RoundedCornerShape(18.dp),
                            color = accents.softPillFill,
                            border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.45f)),
                            modifier = Modifier.size(54.dp).testTag("ec_gps_btn")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.MyLocation, contentDescription = "Capture GPS", tint = accents.emerald)
                            }
                        }
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
                        onValueChange = { bookPageStr = it.take(40) },
                        label = "Manual ledger book page",
                        placeholder = "e.g. 45",
                        testTag = "ec_page_input"
                    )
                }
            }

            if (existing.id.isNotBlank() && (existing.editHistory.isNotEmpty() || existing.createdAt > 0L)) {
                item {
                    SarimCard {
                        EditHistorySection(
                            editHistory = existing.editHistory,
                            createdAtEpoch = existing.createdAt,
                            addedLabel = "Added",
                            canDelete = isAdmin,
                            masked = masked,
                            onDeleteEntry = { idx ->
                                vm.deleteRecordEditHistory(if (isRepClient) "clients" else "customers", existing.id, idx)
                            }
                        )
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
                if (existing.id.isNotBlank()) {
                    SarimDangerButton(
                        text = "Delete",
                        onClick = {
                            confirmReq = ConfirmRequest(
                                title = if (isRepClient) "Delete Rep Customer?" else "Delete Customer?",
                                message = deleteConfirmMessage(listOf(
                                    "Customer" to existing.name,
                                    "Phone" to existing.phone
                                )),
                                confirmText = "Delete",
                                danger = true,
                                tag = "ec_confirm_delete_btn"
                            ) {
                                vm.softDeleteRecord(
                                    collection = if (isRepClient) "clients" else "customers",
                                    docId = existing.id,
                                    displayName = existing.name,
                                    displayDetail = existing.phone,
                                    displayAmount = "",
                                    snapshotMap = WorkspaceJsonCodec.customerToMap(existing)
                                )
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(0.55f),
                        testTag = "ec_delete_btn"
                    )
                }
                SarimGradientButton(
                    text = if (saving) "Saving..." else if (existing.id.isBlank()) "Add Customer" else "Update Customer",
                    onClick = {
                        val isUpd = existing.id.isNotBlank()
                        val noun = if (isRepClient) "Rep Customer" else "Customer"
                        val custDetails = buildList {
                            add("Name" to name.trim())
                            if (phone.isNotBlank()) add("Phone" to phone.trim())
                            if (address.isNotBlank()) add("Address" to address.trim())
                            val opening = openingStr.toDoubleOrNull() ?: 0.0
                            if (opening != 0.0) add("Opening Balance" to InputGuards.formatMoney(opening, currency, masked))
                            val rate = customRateStr.toDoubleOrNull() ?: 0.0
                            if (rate > 0.0) add("Custom Rate" to InputGuards.formatMoney(rate, currency, masked))
                        }
                        confirmReq = saveConfirmRequest(
                            label = noun,
                            details = custDetails,
                            gated = !isAdmin,
                            isUpdate = isUpd,
                            verb = "Add",
                            tag = "ec_confirm_save_btn"
                        ) {
                        saving = true
                        scope.launch {
                            val dataUrl = photoUri?.let { uri ->
                                withContext(Dispatchers.IO) {
                                    CaptionedPhotoSharer.compressUriToDataUrl(context, uri, maxDim = 900, quality = 80)
                                }
                            }
                            vm.saveCustomerOrClientExtended(
                                existing = existing.takeIf { it.id.isNotBlank() },
                                isRepClient = isRepClient,
                                name = name,
                                phone = phone,
                                address = address,
                                oldDebit = openingStr.toDoubleOrNull() ?: 0.0,
                                customSalePrice = customRateStr.toDoubleOrNull() ?: 0.0,
                                salesRep = salesRep,
                                ledgerPage = bookPageStr,
                                notes = notesStr,
                                lat = gpsLat,
                                lng = gpsLng,
                                photoDataUri = dataUrl,
                                clearPhoto = clearPhoto
                            ) {
                                saving = false
                                onBack()
                            }
                            saving = false
                        }
                        }
                    },
                    enabled = name.isNotBlank() && !saving,
                    testTag = "ec_save_btn",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun AvatarPickerBadge(
    photoUri: Uri?,
    existingDataUrl: String?,
    cleared: Boolean,
    onPick: () -> Unit,
    onClear: (() -> Unit)?
) {
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
                when {
                    bitmap != null -> Image(
                        bitmap = bitmap!!,
                        contentDescription = "Selected photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    !cleared && !existingDataUrl.isNullOrBlank() -> DecodedDataUrlImage(
                        dataUrl = existingDataUrl,
                        contentDescription = "Saved photo",
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> Icon(Icons.Rounded.Person, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(38.dp))
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
                Icon(Icons.Rounded.Add, contentDescription = "Pick photo", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Surface(
            onClick = onPick,
            shape = RoundedCornerShape(999.dp),
            color = accents.softPillFill,
            border = BorderStroke(1.dp, accents.emerald.copy(alpha = 0.35f)),
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(15.dp))
                Text("Photo", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = accents.emerald)
            }
        }
        if (onClear != null) {
            Surface(
                onClick = onClear,
                shape = RoundedCornerShape(999.dp),
                color = accents.danger.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.35f)),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("avatar_remove_photo_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = accents.danger, modifier = Modifier.size(14.dp))
                    Text("Remove", style = MaterialTheme.typography.labelSmall, color = accents.danger)
                }
            }
        }
    }
}
