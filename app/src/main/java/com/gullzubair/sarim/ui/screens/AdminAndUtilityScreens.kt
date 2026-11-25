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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.gullzubair.sarim.data.model.AppRoute
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
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
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SettingsScreen(
    snapshot: WorkspaceSnapshot,
    vm: SarimViewModel,
    onBack: () -> Unit,
    onNavigateRoute: (AppRoute) -> Unit,
    onSignOut: () -> Unit
) {
    val accents = LocalSarimAccents.current
    var currency by remember(snapshot.settings) { mutableStateOf(snapshot.settings.currencySymbol) }
    var fyMonth by remember(snapshot.settings) { mutableStateOf(snapshot.settings.financialYearStartMonth.toString()) }
    var theme by remember(snapshot.settings) { mutableStateOf(snapshot.settings.themeMode) }
    var bio by remember(snapshot.settings) { mutableStateOf(snapshot.settings.biometricLockEnabled) }
    var hideMoney by remember(snapshot.settings) { mutableStateOf(snapshot.settings.hideSensitiveMoney) }

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
                    SettingsNavTile("Store Manager", "Stores, linked formulas and sale prices", Icons.Outlined.Store, accents.emerald) { onNavigateRoute(AppRoute.StoreManager) }
                    SettingsNavTile("App Accounts", "Manage staff accounts and role access", Icons.Outlined.Group, accents.cyan) { onNavigateRoute(AppRoute.AppAccounts) }
                    SettingsNavTile("Sound & Haptics", "Customize tactile pulse & audio click intensity", Icons.Outlined.VolumeUp, accents.emerald) { onNavigateRoute(AppRoute.SoundHaptics) }
                    SettingsNavTile("Backup & Restore", "Save or restore JSON workspace snapshot", Icons.Outlined.Backup, accents.gold) { onNavigateRoute(AppRoute.BackupRestore) }
                    SettingsNavTile("Recycle Bin", "Restore deleted records or empty permanently", Icons.Outlined.DeleteOutline, accents.danger) { onNavigateRoute(AppRoute.RecycleBin) }
                    SettingsNavTile("DB Structure", "Inspect synced workspace collections", Icons.Outlined.Storage, accents.purple) { onNavigateRoute(AppRoute.DbStructure) }
                    SettingsNavTile("Close Financial Year", "Compact historical entries into opening balances", Icons.Outlined.EventBusy, accents.danger) { onNavigateRoute(AppRoute.CloseFinancialYear) }
                }
            }

            item {
                SarimCard {
                    Text("Workspace Preferences", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(10.dp))
                    SarimPillInput(value = currency, onValueChange = { currency = it.take(12) }, label = "Currency Symbol", testTag = "settings_currency_input")
                    Spacer(Modifier.height(10.dp))
                    SarimPillInput(value = fyMonth, onValueChange = { fyMonth = it.filter(Char::isDigit).take(2) }, label = "Financial Year Start Month (1-12)")
                    Spacer(Modifier.height(10.dp))
                    Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("light", "dark", "system").forEach { m ->
                            FilterChip(selected = theme == m, onClick = { theme = m }, label = { Text(m.replaceFirstChar { it.uppercase() }) })
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
                    OutlinedButton(onClick = { vm.repairLedgerMirrors() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Repair Ledger Mirrors")
                    }
                    Spacer(Modifier.height(10.dp))
                    SarimGradientButton(
                        text = "Save Settings",
                        onClick = {
                            vm.updateSettings(
                                snapshot.settings.copy(
                                    currencySymbol = currency.ifBlank { "Rs" },
                                    financialYearStartMonth = (fyMonth.toLongOrNull() ?: 7L).coerceIn(1L, 12L),
                                    themeMode = theme,
                                    biometricLockEnabled = bio,
                                    hideSensitiveMoney = hideMoney
                                )
                            )
                        },
                        testTag = "settings_save_btn"
                    )
                }
            }

            item {
                SarimCard {
                    Text("${snapshot.profile.displayName} • ${snapshot.profile.email}", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    Spacer(Modifier.height(10.dp))
                    SarimDangerButton(text = "Sign Out", onClick = onSignOut, modifier = Modifier.fillMaxWidth(), testTag = "settings_sign_out_btn")
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
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)),
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
    val isOwner = snapshot.profile.uid == snapshot.profile.workspaceOwnerUid
    var inviteEmail by remember { mutableStateOf("") }
    var inviteName by remember { mutableStateOf("") }
    val allRoles = listOf("admin", "rep", "production", "factory", "payments", "sales")
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
                        Text("ADD ACCOUNT", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        Spacer(Modifier.height(10.dp))
                        SarimPillInput(value = inviteEmail, onValueChange = { inviteEmail = it.take(160) }, label = "EMAIL", placeholder = "user@example.com", testTag = "acc_email_input")
                        Spacer(Modifier.height(10.dp))
                        SarimPillInput(value = inviteName, onValueChange = { inviteName = it.take(80) }, label = "NAME", placeholder = "full name", testTag = "acc_name_input")
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
                        modifier = Modifier.defaultMinSize(minHeight = 36.dp)
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
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
                                Text("Remove", style = MaterialTheme.typography.labelMedium, color = accents.danger, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
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
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 36.dp)
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
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("sh_test_btn")
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
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
                        "Touch vibration is turned off in the phone settings (Sound & vibration, Touch feedback). Turn it on and try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = accents.textMuted
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sound Feedback Intensity", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Audible acoustic key click volume (Extreme Boost clears freely & loudly)", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                    }
                    Surface(
                        onClick = { FeedbackHelper.triggerSound(context, selectedSound) },
                        shape = RoundedCornerShape(18.dp),
                        color = accents.softPillFill,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("sh_play_btn")
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
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

    val createDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val root = JSONObject().apply {
                    put("exportedAt", DateUtils.todayIso())
                    put("workspaceOwnerUid", snapshot.profile.workspaceOwnerUid)
                    put("stores", JSONArray(snapshot.stores.map { mapOf("id" to it.id, "storeKey" to it.storeKey, "name" to it.name, "formulaId" to it.formulaId, "salePrice" to it.salePrice) }))
                    put("customers", JSONArray(snapshot.customers.map { mapOf("id" to it.id, "name" to it.name, "phone" to it.phone, "address" to it.address, "openingBalance" to it.openingBalance) }))
                }
                context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) }
            }.onSuccess { statusText = "Backup exported to device." }.onFailure { statusText = "Export failed: ${it.localizedMessage}" }
        }
    }
    val openDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            }.onSuccess { json ->
                vm.restoreBackupJson(json)
                statusText = "Backup loaded."
            }.onFailure { statusText = "Restore failed: ${it.localizedMessage}" }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "BACKUP & RESTORE", onBack = onBack)

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("BACKUP", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = accents.textMuted)
                Spacer(Modifier.height(6.dp))
                SarimCard(modifier = Modifier.clickable { createDocLauncher.launch("sarim_backup_${DateUtils.todayIso()}.json") }.testTag("backup_export_btn")) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(accents.softPillFill), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = accents.emerald)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Backup to Phone", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Save a local copy of all data to your device", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = accents.textMuted)
                    }
                }
            }
            item {
                Text("RESTORE", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = accents.textMuted)
                Spacer(Modifier.height(6.dp))
                SarimCard(modifier = Modifier.clickable { openDocLauncher.launch(arrayOf("application/json")) }.testTag("backup_restore_btn")) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(accents.gold.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = accents.gold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Restore from Phone", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Load a previously saved backup file", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = accents.textMuted)
                    }
                }
            }
            if (statusText.isNotBlank()) {
                item { Text(statusText, style = MaterialTheme.typography.bodySmall, color = accents.emerald) }
            }
        }
    }
}

@Composable
fun CloseFinancialYearScreen(snapshot: WorkspaceSnapshot, vm: SarimViewModel, onBack: () -> Unit) {
    val accents = LocalSarimAccents.current
    var confirmInput by remember { mutableStateOf("") }

    val prodActive = snapshot.production.filter { !it.recycled }
    val salesActive = snapshot.sales.filter { !it.recycled }
    val calcActive = snapshot.calculator.filter { !it.recycled }
    val txActive = snapshot.transactions.filter { !it.recycled }
    val factoryActive = snapshot.factory.filter { !it.recycled }
    val returnsCount = prodActive.count { it.transfer } + calcActive.count { it.returnedKg > 0 }
    val storesList = snapshot.stores.joinToString(", ") { it.name.uppercase() }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = "CLOSE FINANCIAL YEAR",
            onBack = onBack,
            subtitle = "Compact all records into opening balances",
            badgeText = "PREVIEW",
            leadingIcon = {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(accents.danger.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.EventBusy, contentDescription = null, tint = accents.danger, modifier = Modifier.size(18.dp))
                }
            }
        )

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                CloseFyCard("Production", Icons.Outlined.Inventory2, accents.emerald, "${prodActive.size} rec", "${snapshot.stores.size} store balances", "Stores: ${storesList.ifBlank { "None" }}")
            }
            item {
                CloseFyCard("Sales", Icons.Outlined.ReceiptLong, accents.emerald, "${salesActive.size} rec", "${snapshot.customers.count { !it.recycled }} balances", "Customers: ${snapshot.customers.count { !it.recycled }}")
            }
            item {
                CloseFyCard("Calculator", Icons.Outlined.Calculate, accents.cyan, "${calcActive.size} rec", "returns → Prod", "Reps settled across ${calcActive.map { it.repName }.distinct().size} reps")
            }
            item {
                CloseFyCard("Payments", Icons.Outlined.Payments, accents.gold, "${txActive.size} rec", "${snapshot.entities.count { !it.recycled }} opening bal.", "Entities: ${snapshot.entities.count { !it.recycled }}")
            }
            item {
                CloseFyCard("Factory", Icons.Outlined.Factory, accents.purple, "${factoryActive.size} rec", "${snapshot.formulas.size} formula rec.", "Active inventory lines: ${snapshot.inventory.count { !it.recycled }}")
            }
            item {
                CloseFySkippedCard("Rep Sales")
                Spacer(Modifier.height(8.dp))
                CloseFySkippedCard("Expenses")
            }
            item {
                CloseFyCard("Stock Returns", Icons.Default.Restore, accents.danger, "$returnsCount rec", "1 per store + date", "Stores: ${storesList.ifBlank { "None" }}")
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
                            Text("Irreversible — original records will be compacted", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = accents.danger)
                            Text("Type CLOSE below to compact year-end records into opening balances.", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SarimPillInput(value = confirmInput, onValueChange = { confirmInput = it.take(20) }, placeholder = "Type CLOSE to confirm", testTag = "cfy_confirm_input")
            }
        }

        Surface(color = accents.headerFrosted, border = BorderStroke(1.dp, accents.borderSubtle)) {
            Row(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 12.dp),
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
            Text("skipped", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
        }
    }
}

@Composable
fun RecycleBinScreen(snapshot: WorkspaceSnapshot, vm: SarimViewModel, onBack: () -> Unit) {
    val accents = LocalSarimAccents.current
    val deletedItems = remember(snapshot) {
        buildList {
            snapshot.production.filter { it.recycled }.forEach { add(Triple("production", it.id, "${it.storeName} • ${it.date}")) }
            snapshot.sales.filter { it.recycled }.forEach { add(Triple("sales", it.id, "${it.customerName} • ${it.date}")) }
            snapshot.repRecords.filter { it.recycled }.forEach { add(Triple("rep_records", it.id, "${it.repName} / ${it.customerName} • ${it.date}")) }
            snapshot.calculator.filter { it.recycled }.forEach { add(Triple("calculator", it.id, "Settlement ${it.repName} • ${it.dateTo}")) }
            snapshot.factory.filter { it.recycled }.forEach { add(Triple("factory", it.id, "${it.recordType} • ${it.date}")) }
            snapshot.transactions.filter { it.recycled }.forEach { add(Triple("transactions", it.id, "${it.entityName} • ${it.date}")) }
            snapshot.ledgerEntries.filter { it.recycled }.forEach { add(Triple("ledger_entries", it.id, "Ledger ${it.entryType} • ${it.date}")) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "RECYCLE BIN", onBack = onBack)
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (deletedItems.isEmpty()) {
                item { EmptyStateCard("Recycle Bin is empty", "Recycled records are retained for 90 days before permanent purge.") }
            } else {
                items(deletedItems, key = { "${it.first}_${it.second}" }) { (col, id, label) ->
                    SarimCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.titleSmall)
                                Text(col.uppercase(), style = MaterialTheme.typography.labelSmall, color = accents.textMuted)
                            }
                            IconButton(onClick = { vm.restoreRecord(col, id) }, modifier = Modifier.testTag("bin_restore_$id")) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore record", tint = accents.emerald)
                            }
                            IconButton(onClick = { vm.deleteForever(col, id) }, modifier = Modifier.testTag("bin_purge_$id")) {
                                Icon(Icons.Default.DeleteForever, contentDescription = "Delete forever", tint = accents.danger)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DbStructureScreen(snapshot: WorkspaceSnapshot, onBack: () -> Unit) {
    val rows = listOf(
        "Stores" to snapshot.stores.size,
        "Formulas" to snapshot.formulas.size,
        "Customers" to snapshot.customers.size,
        "Entities" to snapshot.entities.size,
        "Inventory" to snapshot.inventory.size,
        "Production" to snapshot.production.size,
        "Sales" to snapshot.sales.size,
        "Rep Records" to snapshot.repRecords.size,
        "Calculator Settlements" to snapshot.calculator.size,
        "Factory Records" to snapshot.factory.size,
        "Transactions" to snapshot.transactions.size,
        "Daily Ledger" to snapshot.ledgerEntries.size,
        "Pending Approvals" to snapshot.pendingApprovals.size
    )
    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "DB STRUCTURE", onBack = onBack)
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { MetricKpiCard("Active Workspace Owner", snapshot.profile.workspaceOwnerUid.take(16) + "...", "Real-time Firestore Sync") }
            items(rows, key = { it.first }) { (name, count) ->
                SarimCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text("$count docs", style = MaterialTheme.typography.labelLarge, color = LocalSarimAccents.current.emerald)
                    }
                }
            }
        }
    }
}
