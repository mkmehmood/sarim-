package com.gullzubair.sarim.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.AppRoute
import com.gullzubair.sarim.data.model.DeletionTombstone
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.domain.InputGuards
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.MetricKpiCard
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimDangerButton
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.FeedbackHelper
import com.gullzubair.sarim.util.HapticLevel
import com.gullzubair.sarim.util.SoundLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun SettingsScreen(
    snapshot: WorkspaceSnapshot,
    vm: SarimViewModel,
    onBack: () -> Unit,
    onNavigateRoute: (AppRoute) -> Unit,
    onSignOut: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val isAdminSession = session.mode == "admin"
    val canSeeRecycleBin = isAdminSession || (FinanceEngine.recycleCollections(session)?.isNotEmpty() == true)
    val allowedBinCount = remember(snapshot.deletions, session) {
        snapshot.deletions.count { FinanceEngine.isRecycleRecordAllowed(it.collection, session) }
    }
    var currency by remember(snapshot.settings) { mutableStateOf(snapshot.settings.currencySymbol) }
    var fyMonth by remember(snapshot.settings) { mutableStateOf(snapshot.settings.financialYearStartMonth.toString()) }
    var repProfile by remember(snapshot.settings) { mutableStateOf(snapshot.settings.repProfile) }
    var theme by remember(snapshot.settings) { mutableStateOf(snapshot.settings.themeMode) }
    var bio by remember(snapshot.settings) { mutableStateOf(snapshot.settings.biometricLockEnabled) }
    var hideMoney by remember(snapshot.settings) { mutableStateOf(snapshot.settings.hideSensitiveMoney) }

    val reps = remember(snapshot.settings.reps) { mutableStateListOf<String>().apply { addAll(snapshot.settings.reps) } }
    val paymentModes = remember(snapshot.settings.paymentModes) { mutableStateListOf<String>().apply { addAll(snapshot.settings.paymentModes) } }
    val expCats = remember(snapshot.settings.expenseCategories) { mutableStateListOf<String>().apply { addAll(snapshot.settings.expenseCategories) } }
    val fieldExpCats = remember(snapshot.settings.fieldExpenseCategories) { mutableStateListOf<String>().apply { addAll(snapshot.settings.fieldExpenseCategories) } }
    val commCats = remember(snapshot.settings.commissionCategories) { mutableStateListOf<String>().apply { addAll(snapshot.settings.commissionCategories) } }

    var listsExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "SETTINGS", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("WORKSPACE TOOLS", style = MaterialTheme.typography.labelLarge, color = accents.textMuted)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isAdminSession) {
                        SettingsNavTile("Store Manager", "Stores, linked formulas and sale prices", Icons.Outlined.Store, accents.emerald) {
                            onNavigateRoute(AppRoute.StoreManager)
                        }
                        SettingsNavTile("App Accounts", "Manage staff accounts and role access", Icons.Outlined.Group, accents.cyan) {
                            onNavigateRoute(AppRoute.AppAccounts)
                        }
                    }
                    SettingsNavTile("Sound & Haptics", "Customize tactile pulse & audio click intensity", Icons.Outlined.VolumeUp, accents.emerald) {
                        onNavigateRoute(AppRoute.SoundHaptics)
                    }
                    if (isAdminSession) {
                        SettingsNavTile("Backup & Restore", "Save or restore JSON & encrypted .gznd backups", Icons.Outlined.Backup, accents.gold) {
                            onNavigateRoute(AppRoute.BackupRestore)
                        }
                    }
                    if (canSeeRecycleBin) {
                        SettingsNavTile(
                            title = "Recycle Bin",
                            subtitle = if (allowedBinCount == 0) "Restore deleted records or empty permanently" else "$allowedBinCount deleted item(s)",
                            icon = Icons.Outlined.DeleteOutline,
                            tint = accents.danger
                        ) { onNavigateRoute(AppRoute.RecycleBin) }
                    }
                    if (isAdminSession) {
                        SettingsNavTile("DB Structure", "Inspect synced workspace & Room DB stats", Icons.Outlined.Storage, accents.purple) {
                            onNavigateRoute(AppRoute.DbStructure)
                        }
                        SettingsNavTile("Close Financial Year", "Compact historical entries into opening balances", Icons.Outlined.EventBusy, accents.danger) {
                            onNavigateRoute(AppRoute.CloseFinancialYear)
                        }
                    }
                }
            }

            if (isAdminSession) {
                item {
                    SarimCard {
                        Text("Workspace Preferences", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(10.dp))
                        SarimPillInput(
                            value = currency,
                            onValueChange = { currency = it.take(12) },
                            label = "Currency Symbol",
                            testTag = "settings_currency_input"
                        )
                        Spacer(Modifier.height(10.dp))
                        SarimPillInput(
                            value = fyMonth,
                            onValueChange = { fyMonth = it.filter(Char::isDigit).take(2) },
                            label = "Financial Year Start Month (1-12)"
                        )
                        Spacer(Modifier.height(10.dp))
                        SarimPillInput(
                            value = repProfile,
                            onValueChange = { repProfile = it.take(60) },
                            label = "Default Rep Profile",
                            placeholder = "Optional active rep name"
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("light", "dark", "system").forEach { m ->
                                FilterChip(
                                    selected = theme == m,
                                    onClick = { theme = m },
                                    label = { Text(m.replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Biometric App Lock", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = bio, onCheckedChange = { bio = it }, modifier = Modifier.testTag("settings_bio_switch"))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Privacy Mask Monetary Totals", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = hideMoney, onCheckedChange = { hideMoney = it }, modifier = Modifier.testTag("settings_privacy_switch"))
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable { listsExpanded = !listsExpanded }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Reps, Payment Modes & Categories",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = accents.emerald
                            )
                            Icon(
                                if (listsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = "Toggle category lists",
                                tint = accents.emerald
                            )
                        }

                        AnimatedVisibility(visible = listsExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                                EditableTagList("Sales Reps", reps, "Add rep name")
                                EditableTagList("Payment Modes", paymentModes, "Add payment mode")
                                EditableTagList("Expense Categories", expCats, "Add expense category")
                                EditableTagList("Field Expense Categories", fieldExpCats, "Add field expense category")
                                EditableTagList("Commission Categories", commCats, "Add commission category")
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.auditRepriceZeroCostSales() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Audit & Reprice Zero-Cost Sales")
                        }
                        Spacer(Modifier.height(10.dp))
                        SarimGradientButton(
                            text = "Save Settings",
                            onClick = {
                                vm.updateSettings(
                                    snapshot.settings.copy(
                                        currencySymbol = currency.ifBlank { "Rs" },
                                        financialYearStartMonth = (fyMonth.toIntOrNull() ?: 7).coerceIn(1, 12),
                                        repProfile = repProfile.trim(),
                                        themeMode = theme,
                                        biometricLockEnabled = bio,
                                        hideSensitiveMoney = hideMoney,
                                        reps = reps.toList(),
                                        paymentModes = paymentModes.toList().ifEmpty { listOf("Cash", "Bank") },
                                        expenseCategories = expCats.toList().ifEmpty { listOf("Other") },
                                        fieldExpenseCategories = fieldExpCats.toList().ifEmpty { listOf("Other") },
                                        commissionCategories = commCats.toList().ifEmpty { listOf("Sales Commission") }
                                    )
                                )
                            },
                            testTag = "settings_save_btn"
                        )
                    }
                }
            } else {
                item {
                    SarimCard {
                        Text("Device Preferences", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(10.dp))
                        Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("light", "dark", "system").forEach { m ->
                                FilterChip(
                                    selected = theme == m,
                                    onClick = {
                                        theme = m
                                        vm.updateSettings(snapshot.settings.copy(themeMode = m))
                                    },
                                    label = { Text(m.replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Biometric App Lock", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = bio,
                                onCheckedChange = {
                                    bio = it
                                    vm.updateSettings(snapshot.settings.copy(biometricLockEnabled = it))
                                },
                                modifier = Modifier.testTag("settings_bio_switch")
                            )
                        }
                    }
                }
            }

            item {
                SarimCard {
                    Text(
                        text = "${snapshot.profile.displayName} • ${snapshot.profile.email}",
                        style = MaterialTheme.typography.bodySmall,
                        color = accents.textMuted
                    )
                    Spacer(Modifier.height(10.dp))
                    SarimDangerButton(
                        text = "Sign Out",
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "settings_sign_out_btn"
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditableTagList(
    title: String,
    items: MutableList<String>,
    placeholder: String
) {
    val accents = LocalSarimAccents.current
    var draft by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = accents.textMuted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { items.remove(tag) },
                    label = { Text(tag) },
                    trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove $tag", modifier = Modifier.size(16.dp)) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SarimPillInput(
                value = draft,
                onValueChange = { draft = it.take(40) },
                placeholder = placeholder,
                modifier = Modifier.weight(1f)
            )
            Surface(
                onClick = {
                    val clean = draft.trim()
                    if (clean.isNotBlank() && items.none { it.equals(clean, ignoreCase = true) }) {
                        items.add(clean)
                        draft = ""
                    }
                },
                shape = CircleShape,
                color = accents.softPillFill,
                border = BorderStroke(1.dp, accents.emerald),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "Add $title", tint = accents.emerald)
                }
            }
        }
    }
}

@Composable
private fun SettingsNavTile(title: String, subtitle: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    val accents = LocalSarimAccents.current
    SarimCard(modifier = Modifier.clickable(onClick = onClick), contentPadding = PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = accents.textMuted)
        }
    }
}

@Composable
fun AppAccountsScreen(snapshot: WorkspaceSnapshot, vm: SarimViewModel, onBack: () -> Unit) {
    val accents = LocalSarimAccents.current
    val isOwner = snapshot.profile.isAdmin || snapshot.profile.uid == snapshot.profile.effectiveOwnerUid
    var inviteEmail by remember { mutableStateOf("") }
    var inviteName by remember { mutableStateOf("") }
    val allRoles = FinanceEngine.ROLE_KEYS
    val selectedRoles = remember { mutableStateListOf("rep", "sales") }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "APP ACCOUNTS", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isOwner) {
                item {
                    SarimCard {
                        Text("ADD ACCOUNT / INVITE", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(10.dp))
                        SarimPillInput(
                            value = inviteEmail,
                            onValueChange = { inviteEmail = it.take(160) },
                            label = "EMAIL",
                            placeholder = "user@example.com",
                            testTag = "acc_email_input"
                        )
                        Spacer(Modifier.height(10.dp))
                        SarimPillInput(
                            value = inviteName,
                            onValueChange = { inviteName = it.take(80) },
                            label = "NAME",
                            placeholder = "Full name",
                            testTag = "acc_name_input"
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("ROLES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = accents.textMuted)
                        Spacer(Modifier.height(6.dp))
                        RoleGridSelector(allRoles = allRoles, activeRoles = selectedRoles) { r ->
                            if (r in selectedRoles) selectedRoles.remove(r) else selectedRoles.add(r)
                        }
                        Spacer(Modifier.height(12.dp))
                        SarimGradientButton(
                            text = "Add Account",
                            onClick = {
                                vm.inviteMember(inviteEmail, inviteName.ifBlank { inviteEmail.substringBefore("@") }, selectedRoles.toList())
                                inviteEmail = ""; inviteName = ""
                            },
                            enabled = inviteEmail.contains("@"),
                            testTag = "acc_invite_btn"
                        )
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("REGISTERED ACCOUNTS", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = accents.textMuted)
                    Surface(
                        onClick = { vm.retrySync() },
                        shape = RoundedCornerShape(14.dp),
                        color = accents.headerFrosted,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text("↻ Refresh", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            items(snapshot.teamMembers, key = { it.uid }) { m ->
                val isSelf = m.uid == snapshot.profile.uid
                SarimCard(contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (m.approved) accents.emerald else accents.gold))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = m.displayName.ifBlank { m.email } + if (isSelf) " (you)" else "",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(m.email, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    RoleGridSelector(allRoles = allRoles, activeRoles = m.roles) { role ->
                        if (isOwner && !isSelf) {
                            val next = if (role in m.roles) m.roles - role else m.roles + role
                            vm.updateMemberRoles(m.uid, next.ifEmpty { listOf("sales") }, m.approved)
                        }
                    }
                    if (isOwner && !isSelf) {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                onClick = { vm.updateMemberRoles(m.uid, m.roles, !m.approved) },
                                shape = RoundedCornerShape(14.dp),
                                color = accents.gold.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, accents.gold.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    if (m.approved) "Suspend" else "Activate",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = accents.gold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            items(snapshot.invites, key = { it.id }) { inv ->
                SarimCard(accentBorder = accents.cyan.copy(alpha = 0.5f), contentPadding = PaddingValues(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${inv.displayName} (Invited)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("${inv.memberEmail} • ${inv.roles.joinToString()}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        if (isOwner) {
                            Surface(
                                onClick = { vm.revokeInvite(inv.id) },
                                shape = RoundedCornerShape(14.dp),
                                color = accents.danger.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.4f))
                            ) {
                                Text("Remove", style = MaterialTheme.typography.labelMedium, color = accents.danger, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                        }
                    }
                }
            }

            if (snapshot.accounts.isNotEmpty()) {
                item {
                    Text("SYNCED WORKSPACE DIRECTORY", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = accents.textMuted)
                }
                items(snapshot.accounts, key = { "${it.uid}_${it.email}" }) { acc ->
                    SarimCard(contentPadding = PaddingValues(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(acc.displayName.ifBlank { acc.email }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                Text("${acc.email} • ${acc.roles.joinToString()}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                            }
                            if (isOwner && acc.uid != snapshot.profile.uid) {
                                TextButton(onClick = {
                                    vm.saveAccountsDirectory(snapshot.accounts.filterNot { it.uid == acc.uid && it.email == acc.email })
                                }) {
                                    Text("Remove", color = accents.danger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleGridSelector(allRoles: List<String>, activeRoles: List<String>, onToggle: (String) -> Unit) {
    val accents = LocalSarimAccents.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        allRoles.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { role ->
                    val sel = role in activeRoles
                    Surface(
                        onClick = { onToggle(role) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (sel) accents.softPillFill else accents.inputFill,
                        border = BorderStroke(1.dp, if (sel) accents.emerald else accents.borderSubtle),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                role.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium),
                                color = if (sel) accents.emerald else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SoundAndHapticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val accents = LocalSarimAccents.current
    var selectedHaptic by remember { mutableStateOf(FeedbackHelper.getHapticLevel(context)) }
    var selectedSound by remember { mutableStateOf(FeedbackHelper.getSoundLevel(context)) }
    var hapticExpanded by remember { mutableStateOf(false) }
    var soundExpanded by remember { mutableStateOf(false) }
    val systemVibrationOn = remember { FeedbackHelper.isSystemTouchVibrationEnabled(context) }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "SOUND & HAPTICS", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Haptic Feedback Intensity", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Hardware vibrator pulse strength and crisp tactile clicks", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    }
                    Surface(
                        onClick = { FeedbackHelper.triggerHaptic(context, selectedHaptic) },
                        shape = RoundedCornerShape(18.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("sh_test_btn")
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Vibration, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Test", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SarimCard(modifier = Modifier.clickable { hapticExpanded = !hapticExpanded }, contentPadding = PaddingValues(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Vibration, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selectedHaptic.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(selectedHaptic.detail, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Icon(if (hapticExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = accents.emerald)
                    }
                    AnimatedVisibility(visible = hapticExpanded) {
                        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            HapticLevel.entries.forEach { level ->
                                val active = level == selectedHaptic
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (active) accents.softPillFill else Color.Transparent)
                                        .clickable {
                                            selectedHaptic = level
                                            FeedbackHelper.setHapticLevel(context, level)
                                            FeedbackHelper.triggerHaptic(context, level)
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(level.label, style = MaterialTheme.typography.titleSmall, color = if (active) accents.emerald else MaterialTheme.colorScheme.onSurface)
                                        Text(level.detail, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                    }
                                    if (active) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                if (!systemVibrationOn) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Touch vibration is turned off in phone settings (Sound & vibration -> Touch feedback). Turn it on and try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = accents.textMuted
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sound Feedback Intensity", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Audible acoustic key click volume", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    }
                    Surface(
                        onClick = { FeedbackHelper.triggerSound(context, selectedSound) },
                        shape = RoundedCornerShape(18.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("sh_play_btn")
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Play", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SarimCard(modifier = Modifier.clickable { soundExpanded = !soundExpanded }, contentPadding = PaddingValues(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.VolumeUp, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selectedSound.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(selectedSound.detail, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Icon(if (soundExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = accents.emerald)
                    }
                    AnimatedVisibility(visible = soundExpanded) {
                        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SoundLevel.entries.forEach { level ->
                                val active = level == selectedSound
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (active) accents.softPillFill else Color.Transparent)
                                        .clickable {
                                            selectedSound = level
                                            FeedbackHelper.setSoundLevel(context, level)
                                            FeedbackHelper.triggerSound(context, level)
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(level.label, style = MaterialTheme.typography.titleSmall, color = if (active) accents.emerald else MaterialTheme.colorScheme.onSurface)
                                        Text(level.detail, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                                    }
                                    if (active) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "These settings apply to this device only and cover dialogs, switches and toggle buttons.",
                    style = MaterialTheme.typography.bodySmall,
                    color = accents.textMuted
                )
            }
        }
    }
}

@Composable
fun BackupRestoreScreen(snapshot: WorkspaceSnapshot, vm: SarimViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val accents = LocalSarimAccents.current
    var statusText by remember { mutableStateOf("") }

    var showEncryptDialog by remember { mutableStateOf(false) }
    var encryptPassword by remember { mutableStateOf("") }
    var pendingEncryptedBytes by remember { mutableStateOf<ByteArray?>(null) }

    var showDecryptDialog by remember { mutableStateOf(false) }
    var decryptPassword by remember { mutableStateOf("") }
    var pendingRestoreBytes by remember { mutableStateOf<ByteArray?>(null) }

    val createJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val json = vm.buildPlainBackupJson() ?: error("Workspace not loaded")
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }.onSuccess { statusText = "Plain JSON backup exported to device." }
                .onFailure { statusText = "Export failed: ${it.localizedMessage}" }
        }
    }

    val createGzndLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val bytes = pendingEncryptedBytes
        pendingEncryptedBytes = null
        if (uri != null && bytes != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }.onSuccess { statusText = "Encrypted .gznd (GZND_ENC_V4) backup exported." }
                .onFailure { statusText = "Encrypted export failed: ${it.localizedMessage}" }
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not read file")
            }.onSuccess { bytes ->
                val isEnc = bytes.size >= 11 && String(bytes.copyOfRange(0, 11), Charsets.US_ASCII) == "GZND_ENC_V4"
                if (isEnc) {
                    pendingRestoreBytes = bytes
                    decryptPassword = ""
                    showDecryptDialog = true
                } else {
                    vm.restoreBackupBytesOrJson(rawText = null, rawBytes = bytes, password = "")
                    statusText = "Backup loaded for restore."
                }
            }.onFailure { statusText = "Read failed: ${it.localizedMessage}" }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "BACKUP & RESTORE", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("BACKUP", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = accents.textMuted)
                Spacer(Modifier.height(6.dp))
                SarimCard(
                    modifier = Modifier
                        .clickable { showEncryptDialog = true }
                        .testTag("backup_export_gznd_btn")
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(accents.softPillFill), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = accents.emerald)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Encrypted Backup (.gznd)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Password-protected GZND_ENC_V4 archive compatible with the web app", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = accents.textMuted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                SarimCard(
                    modifier = Modifier
                        .clickable { createJsonLauncher.launch("sarim_backup_${DateUtils.todayIso()}.json") }
                        .testTag("backup_export_btn")
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(accents.cyan.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = accents.cyan)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Plain JSON Backup (.json)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Save an unencrypted local snapshot of all collections", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = accents.textMuted)
                    }
                }
            }

            item {
                Text("RESTORE", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = accents.textMuted)
                Spacer(Modifier.height(6.dp))
                SarimCard(
                    modifier = Modifier
                        .clickable { openDocLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
                        .testTag("backup_restore_btn")
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(accents.gold.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Backup, contentDescription = null, tint = accents.gold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Restore from Phone (.json / .gznd)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Load and merge a previously saved JSON or encrypted .gznd backup", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = accents.textMuted)
                    }
                }
            }

            if (statusText.isNotBlank()) {
                item {
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = accents.emerald)
                }
            }
        }
    }

    if (showEncryptDialog) {
        AlertDialog(
            onDismissRequest = { showEncryptDialog = false },
            title = { Text("Encrypt Backup (.gznd)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a password to encrypt your GZND_ENC_V4 backup. You will need this password to restore it on Android or Web.", style = MaterialTheme.typography.bodySmall)
                    SarimPillInput(
                        value = encryptPassword,
                        onValueChange = { encryptPassword = it },
                        label = "BACKUP PASSWORD",
                        placeholder = "Min 4 characters",
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = encryptPassword.length >= 4,
                    onClick = {
                        val enc = vm.buildEncryptedGzndV4(encryptPassword)
                        showEncryptDialog = false
                        encryptPassword = ""
                        if (enc != null) {
                            pendingEncryptedBytes = enc
                            createGzndLauncher.launch("sarim_backup_${DateUtils.todayIso()}.gznd")
                        }
                    }
                ) { Text("Encrypt & Save") }
            },
            dismissButton = { TextButton(onClick = { showEncryptDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDecryptDialog && pendingRestoreBytes != null) {
        AlertDialog(
            onDismissRequest = {
                showDecryptDialog = false
                pendingRestoreBytes = null
            },
            title = { Text("Decrypt .gznd Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This file is encrypted (GZND_ENC_V4). Enter the backup password to decrypt and restore.", style = MaterialTheme.typography.bodySmall)
                    SarimPillInput(
                        value = decryptPassword,
                        onValueChange = { decryptPassword = it },
                        label = "BACKUP PASSWORD",
                        placeholder = "Password",
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = decryptPassword.isNotBlank(),
                    onClick = {
                        val bytes = pendingRestoreBytes
                        showDecryptDialog = false
                        pendingRestoreBytes = null
                        vm.restoreBackupBytesOrJson(rawText = null, rawBytes = bytes, password = decryptPassword)
                        decryptPassword = ""
                        statusText = "Decryption & restore initiated."
                    }
                ) { Text("Decrypt & Restore") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDecryptDialog = false
                    pendingRestoreBytes = null
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CloseFinancialYearScreen(snapshot: WorkspaceSnapshot, vm: SarimViewModel, onBack: () -> Unit) {
    val accents = LocalSarimAccents.current
    var confirmInput by remember { mutableStateOf("") }

    val prodActive = snapshot.production.filter { it.deletedAt == null }
    val salesActive = snapshot.sales.filter { it.deletedAt == null }
    val repActive = snapshot.repRecords.filter { it.deletedAt == null }
    val calcActive = snapshot.calculator.filter { it.deletedAt == null }
    val txActive = snapshot.transactions.filter { it.deletedAt == null }
    val expActive = snapshot.expenses.filter { it.deletedAt == null }
    val factoryActive = snapshot.factory.filter { it.deletedAt == null }
    val returnsCount = snapshot.returns.count { it.deletedAt == null } + calcActive.count { it.returned > 0.0 }
    val storesList = snapshot.stores.filter { it.deletedAt == null }.joinToString(", ") { it.name.uppercase(Locale.ROOT) }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = "CLOSE FINANCIAL YEAR",
            onBack = onBack,
            subtitle = "Compact all records into opening balances",
            badgeText = "FY #${snapshot.settings.fyCloseCount + 1}",
            leadingIcon = {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accents.danger.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.EventBusy, contentDescription = null, tint = accents.danger, modifier = Modifier.size(18.dp))
                }
            }
        )

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                CloseFyCard(
                    "Production",
                    Icons.Outlined.Inventory2,
                    accents.emerald,
                    "${prodActive.size} rec",
                    "${snapshot.stores.count { it.deletedAt == null }} store bal.",
                    "Stores: ${storesList.ifBlank { "None" }}"
                )
            }
            item {
                CloseFyCard(
                    "Sales",
                    Icons.Outlined.ReceiptLong,
                    accents.emerald,
                    "${salesActive.size} rec",
                    "${snapshot.customers.count { it.deletedAt == null }} balances",
                    "Customers carry forward into oldDebit"
                )
            }
            item {
                CloseFyCard(
                    "Rep Clients",
                    Icons.Outlined.Group,
                    accents.cyan,
                    "${repActive.size} rec",
                    "${snapshot.clients.count { it.deletedAt == null }} clients",
                    "Rep customer balances carry forward into oldDebit"
                )
            }
            item {
                CloseFyCard(
                    "Calculator",
                    Icons.Outlined.Calculate,
                    accents.cyan,
                    "${calcActive.size} rec",
                    "returns -> Prod",
                    "Reps settled across ${calcActive.map { it.seller }.filter { it.isNotBlank() }.distinct().size} sellers"
                )
            }
            item {
                CloseFyCard(
                    "Payments",
                    Icons.Outlined.Payments,
                    accents.gold,
                    "${txActive.size} rec",
                    "${snapshot.entities.count { it.deletedAt == null }} entities",
                    "Entities: ${snapshot.entities.count { it.deletedAt == null }}"
                )
            }
            item {
                CloseFyCard(
                    "Factory",
                    Icons.Outlined.Factory,
                    accents.purple,
                    "${factoryActive.size} rec",
                    "${snapshot.formulas.size} formulas",
                    "Active inventory lines: ${snapshot.inventory.count { it.deletedAt == null }}"
                )
            }
            item {
                CloseFySkippedCard("Expenses (${expActive.size} records preserved)")
            }
            item {
                CloseFyCard(
                    "Stock Returns",
                    Icons.Default.Restore,
                    accents.danger,
                    "$returnsCount rec",
                    "per store",
                    "Stores: ${storesList.ifBlank { "None" }}"
                )
            }
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = accents.danger.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(accents.danger.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = accents.danger, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Irreversible — production records compacted into Recycle Bin", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.danger)
                            Text("Type CLOSE below to compact year-end records into carry-forward opening balances.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SarimPillInput(
                    value = confirmInput,
                    onValueChange = { confirmInput = it.take(20) },
                    placeholder = "Type CLOSE to confirm",
                    testTag = "cfy_confirm_input"
                )
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
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(0.55f).height(50.dp), shape = RoundedCornerShape(26.dp)) {
                    Text("Cancel")
                }
                SarimDangerButton(
                    text = "Close Financial Year",
                    onClick = { vm.closeFinancialYear(onComplete = onBack) },
                    enabled = confirmInput.trim().equals("CLOSE", ignoreCase = true),
                    modifier = Modifier.weight(1f),
                    testTag = "cfy_execute_btn"
                )
            }
        }
    }
}

@Composable
private fun CloseFyCard(title: String, icon: ImageVector, accent: Color, countBadge: String, targetBadge: String, detail: String) {
    val accents = LocalSarimAccents.current
    SarimCard(contentPadding = PaddingValues(0.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(modifier = Modifier.weight(1f).padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        }
                        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(color = accents.gold.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)) {
                            Text(countBadge, style = MaterialTheme.typography.labelSmall, color = accents.gold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                        Surface(color = accent.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)) {
                            Text(targetBadge, style = MaterialTheme.typography.labelSmall, color = accent, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            }
        }
    }
}

@Composable
private fun CloseFySkippedCard(title: String) {
    val accents = LocalSarimAccents.current
    SarimCard(contentPadding = PaddingValues(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = accents.textMuted)
            Text("preserved", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        }
    }
}

@Composable
fun RecycleBinScreen(snapshot: WorkspaceSnapshot, vm: SarimViewModel, onBack: () -> Unit) {
    val accents = LocalSarimAccents.current
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val moneyRestricted = FinanceEngine.isMoneyRestricted(session)
    val hideAllMoney = snapshot.settings.hideSensitiveMoney

    var colFilter by remember { mutableStateOf("all") }
    var confirmPurgeItem by remember { mutableStateOf<DeletionTombstone?>(null) }

    val sortedDeletions = remember(snapshot.deletions, session) {
        snapshot.deletions
            .filter { FinanceEngine.isRecycleRecordAllowed(it.collection, session) }
            .sortedByDescending { it.deletedAt }
    }
    val collections = remember(sortedDeletions) {
        listOf("all") + sortedDeletions.map { it.collection.lowercase(Locale.ROOT) }.filter { it.isNotBlank() }.distinct()
    }
    val visible = remember(sortedDeletions, colFilter) {
        if (colFilter == "all") sortedDeletions
        else sortedDeletions.filter { it.collection.equals(colFilter, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = "RECYCLE BIN",
            subtitle = "${sortedDeletions.size} tombstone(s)",
            onBack = onBack
        )

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (collections.size > 1) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        collections.take(5).forEach { col ->
                            FilterChip(
                                selected = colFilter == col,
                                onClick = { colFilter = col },
                                label = { Text(col.uppercase(Locale.ROOT)) }
                            )
                        }
                    }
                }
            }

            if (visible.isEmpty()) {
                item {
                    EmptyStateCard("Recycle Bin is Empty", "Deleted items remain recoverable here for 30 days.")
                }
            } else {
                items(visible, key = { it.id.ifBlank { "${it.collection}_${it.recordId}" } }) { t ->
                    val deletedDate = remember(t.deletedAt) {
                        if (t.deletedAt > 0L) SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(t.deletedAt)) else ""
                    }
                    val daysLeft = remember(t.expiresAt) {
                        if (t.expiresAt > 0L) max(0L, (t.expiresAt - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L)) else 30L
                    }
                    val safeAmount = remember(t.displayAmount, session, moneyRestricted, hideAllMoney) {
                        if (hideAllMoney) null
                        else FinanceEngine.recycleAmountText(t.displayAmount, session)
                    }
                    SarimCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = t.displayName.ifBlank { t.recordId },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                val subtitleParts = listOfNotNull(
                                    t.collection.uppercase(Locale.ROOT),
                                    t.displayDetail.takeIf { it.isNotBlank() },
                                    safeAmount,
                                    deletedDate.takeIf { it.isNotBlank() },
                                    "${daysLeft}d left"
                                )
                                Text(
                                    text = subtitleParts.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accents.textMuted
                                )
                            }
                            IconButton(
                                onClick = { vm.restoreTombstone(t) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("bin_restore_${t.recordId}")
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore record", tint = accents.emerald)
                            }
                            if (session.mode == "admin") {
                                IconButton(
                                    onClick = { confirmPurgeItem = t },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("bin_purge_${t.recordId}")
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = "Delete forever", tint = accents.danger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmPurgeItem?.let { itemToPurge ->
        AlertDialog(
            onDismissRequest = { confirmPurgeItem = null },
            title = { Text("Delete Permanently") },
            text = { Text("Permanently erase \"${itemToPurge.displayName.ifBlank { itemToPurge.recordId }}\" from ${itemToPurge.collection}? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.purgeTombstone(itemToPurge)
                    confirmPurgeItem = null
                }) { Text("Erase Forever", color = accents.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmPurgeItem = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun DbStructureScreen(snapshot: WorkspaceSnapshot, onBack: () -> Unit) {
    val rows = listOf(
        "Stores" to "${snapshot.stores.count { it.deletedAt == null }} active (${snapshot.stores.size} total)",
        "Formulas" to "${snapshot.formulas.size} formulas",
        "Customers (Direct)" to "${snapshot.customers.count { it.deletedAt == null }} active (${snapshot.customers.size} total)",
        "Clients (Rep)" to "${snapshot.clients.count { it.deletedAt == null }} active (${snapshot.clients.size} total)",
        "Entities" to "${snapshot.entities.count { it.deletedAt == null }} active (${snapshot.entities.size} total)",
        "Inventory" to "${snapshot.inventory.count { it.deletedAt == null }} active (${snapshot.inventory.size} total)",
        "Production" to "${snapshot.production.count { it.deletedAt == null }} active (${snapshot.production.size} total)",
        "Sales (Direct)" to "${snapshot.sales.count { it.deletedAt == null }} active (${snapshot.sales.size} total)",
        "Rep Sales" to "${snapshot.repRecords.count { it.deletedAt == null }} active (${snapshot.repRecords.size} total)",
        "Calculator Settlements" to "${snapshot.calculator.count { it.deletedAt == null }} active (${snapshot.calculator.size} total)",
        "Factory Records" to "${snapshot.factory.count { it.deletedAt == null }} active (${snapshot.factory.size} total)",
        "Transactions" to "${snapshot.transactions.count { it.deletedAt == null }} active (${snapshot.transactions.size} total)",
        "Expenses" to "${snapshot.expenses.count { it.deletedAt == null }} active (${snapshot.expenses.size} total)",
        "Returns" to "${snapshot.returns.count { it.deletedAt == null }} active (${snapshot.returns.size} total)",
        "Daily Ledger" to "${snapshot.ledgerEntries.size} entries",
        "Recycle Bin Tombstones" to "${snapshot.deletions.size} tombstones",
        "Pending Approvals" to "${snapshot.pendingApprovals.count { it.status == "pending" }} pending"
    )
    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "DB STRUCTURE", onBack = onBack)
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                MetricKpiCard(
                    label = "Active Workspace Owner",
                    value = snapshot.profile.effectiveOwnerUid.take(16) + "...",
                    subtitle = if (snapshot.pendingSyncCount > 0) {
                        "Room kv_store + Outbox (${snapshot.pendingSyncCount} pending sync)"
                    } else {
                        "Room kv_store (WAL) + Cloud Firestore Sync"
                    }
                )
            }
            items(rows, key = { it.first }) { (name, countText) ->
                SarimCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text(countText, style = MaterialTheme.typography.labelLarge, color = LocalSarimAccents.current.emerald)
                    }
                }
            }
            if (snapshot.syncStats.isNotEmpty()) {
                item {
                    Text(
                        "SYNC TIMESTAMPS",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = LocalSarimAccents.current.textMuted
                    )
                }
                items(snapshot.syncStats.entries.toList(), key = { it.key }) { (col, ts) ->
                    val formatted = if (ts > 0L) SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date(ts)) else "—"
                    SarimCard(contentPadding = PaddingValues(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(col, style = MaterialTheme.typography.bodyMedium)
                            Text(formatted, style = MaterialTheme.typography.labelMedium, color = LocalSarimAccents.current.textMuted)
                        }
                    }
                }
            }
        }
    }
}
