package com.gullzubair.sarim.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Factory
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.sp
import com.gullzubair.sarim.data.model.AppRoute
import com.gullzubair.sarim.data.model.DeletionTombstone
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.domain.DateUtils
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.MetricKpiCard
import com.gullzubair.sarim.ui.components.SarimCard
import com.gullzubair.sarim.ui.components.SarimConfirmDialog
import com.gullzubair.sarim.ui.components.SarimDangerButton
import com.gullzubair.sarim.ui.components.SarimGradientButton
import com.gullzubair.sarim.ui.components.SarimIconBadge
import com.gullzubair.sarim.ui.components.SarimNoteBanner
import com.gullzubair.sarim.ui.components.SarimPillInput
import com.gullzubair.sarim.ui.components.SarimSubpageHeader
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.ui.viewmodel.ToastKind
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
    @Suppress("UNUSED_PARAMETER") onSignOut: () -> Unit
) {
    val accents = LocalSarimAccents.current
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val isAdminSession = session.mode == "admin"
    val allowedBinCount = remember(snapshot.deletions, session) {
        snapshot.deletions.count { FinanceEngine.isRecycleRecordAllowed(it.collection, session) }
    }
    val themeMode = snapshot.settings.themeMode.lowercase(Locale.ROOT)
    val themeLabel = when (themeMode) {
        "light" -> "Light"
        "dark" -> "Dark"
        else -> "Auto"
    }
    val bioEnabled = snapshot.settings.biometricLockEnabled

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(title = "Settings", onBack = onBack)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Data & Sync (Sync Data & Recycle Bin always visible; Backup & DB Structure admin only)
            item {
                SettingsGroupCard(title = "Data & Sync") {
                    SettingsRowButton(
                        title = "Sync Data",
                        subtitle = if (snapshot.pendingSyncCount > 0) "${snapshot.pendingSyncCount} change(s) pending upload" else null,
                        icon = Icons.Rounded.Sync,
                        tint = accents.emerald,
                        testTag = "settings_sync_btn"
                    ) { vm.syncNow() }

                    if (isAdminSession) {
                        HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.45f))
                        SettingsRowButton(
                            title = "Backup & Restore",
                            icon = Icons.Rounded.Backup,
                            tint = accents.gold,
                            testTag = "settings_backup_btn"
                        ) { onNavigateRoute(AppRoute.BackupRestore) }
                    }

                    HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.45f))
                    SettingsRowButton(
                        title = "Recycle Bin",
                        subtitle = if (allowedBinCount > 0) "$allowedBinCount item(s)" else null,
                        icon = Icons.Rounded.DeleteOutline,
                        tint = accents.danger,
                        testTag = "settings_recycle_btn"
                    ) { onNavigateRoute(AppRoute.RecycleBin) }

                    if (isAdminSession) {
                        HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.45f))
                        SettingsRowButton(
                            title = "Database Structure",
                            icon = Icons.Rounded.Storage,
                            tint = accents.purple,
                            testTag = "settings_db_btn"
                        ) { onNavigateRoute(AppRoute.DbStructure) }
                    }
                }
            }

            // Card 2: Appearance & Device (Theme always visible; Sound & Haptics admin only matching web settings-screen.js)
            item {
                SettingsGroupCard(title = "Appearance & Device") {
                    SettingsRowButton(
                        title = "Theme: $themeLabel",
                        icon = Icons.Rounded.Brightness6,
                        tint = accents.cyan,
                        testTag = "settings_theme_btn"
                    ) {
                        val next = when (themeMode) {
                            "light" -> "dark"
                            "dark" -> "auto"
                            else -> "light"
                        }
                        val nextLbl = when (next) {
                            "light" -> "Light"
                            "dark" -> "Dark"
                            else -> "Auto"
                        }
                        vm.updateSettings(snapshot.settings.copy(themeMode = next))
                        vm.showToast("Theme: $nextLbl", ToastKind.INFO)
                    }

                    if (isAdminSession) {
                        HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.45f))
                        SettingsRowButton(
                            title = "Sound & Haptics",
                            icon = Icons.Rounded.VolumeUp,
                            tint = accents.emerald,
                            testTag = "settings_sound_haptics_btn"
                        ) { onNavigateRoute(AppRoute.SoundHaptics) }
                    }
                }
            }

            // Card 3: Security (Admin only)
            if (isAdminSession) {
                item {
                    SettingsGroupCard(title = "Security") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 52.dp)
                                .clickable {
                                    val next = !bioEnabled
                                    vm.updateSettings(snapshot.settings.copy(biometricLockEnabled = next))
                                    vm.showToast(if (next) "Fingerprint lock enabled" else "Fingerprint lock disabled", ToastKind.INFO)
                                }
                                .padding(horizontal = 4.dp, vertical = 10.dp)
                                .testTag("settings_bio_switch"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SarimIconBadge(
                                icon = Icons.Rounded.Fingerprint,
                                tint = accents.emerald,
                                size = 38.dp,
                                cornerRadius = 12.dp,
                                iconSize = 20.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Fingerprint Lock", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                            Switch(
                                checked = bioEnabled,
                                onCheckedChange = { next ->
                                    vm.updateSettings(snapshot.settings.copy(biometricLockEnabled = next))
                                    vm.showToast(if (next) "Fingerprint lock enabled" else "Fingerprint lock disabled", ToastKind.INFO)
                                }
                            )
                        }

                        HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.45f))
                        SettingsRowButton(
                            title = "App Accounts",
                            icon = Icons.Rounded.Group,
                            tint = accents.cyan,
                            testTag = "settings_accounts_btn"
                        ) { onNavigateRoute(AppRoute.AppAccounts) }
                    }
                }

                // Card 4: Business (Admin only)
                item {
                    SettingsGroupCard(title = "Business") {
                        SettingsRowButton(
                            title = "Store Manager",
                            icon = Icons.Rounded.Store,
                            tint = accents.emerald,
                            testTag = "settings_stores_btn"
                        ) { onNavigateRoute(AppRoute.StoreManager) }
                    }
                }

                // Card 5: Danger Zone (Admin only)
                item {
                    SettingsGroupCard(title = "Danger Zone") {
                        SettingsRowButton(
                            title = "Close Financial Year",
                            icon = Icons.Rounded.EventBusy,
                            tint = accents.danger,
                            titleColor = accents.danger,
                            testTag = "settings_close_fy_btn"
                        ) { onNavigateRoute(AppRoute.CloseFinancialYear) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    val accents = LocalSarimAccents.current
    SarimCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JakartaFont, fontWeight = FontWeight.Bold),
            color = accents.textMuted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun SettingsRowButton(
    title: String,
    icon: ImageVector,
    tint: Color,
    subtitle: String? = null,
    titleColor: Color? = null,
    testTag: String? = null,
    onClick: () -> Unit
) {
    val accents = LocalSarimAccents.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .let { if (testTag != null) it.testTag(testTag) else it }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SarimIconBadge(
            icon = icon,
            tint = tint,
            size = 38.dp,
            cornerRadius = 12.dp,
            iconSize = 20.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = titleColor ?: MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = accents.textMuted)
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
                        shape = RoundedCornerShape(999.dp),
                        color = accents.headerFrosted,
                        border = BorderStroke(1.dp, accents.borderSubtle),
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(15.dp))
                            Text("Refresh", style = MaterialTheme.typography.labelMedium)
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
                            Icon(Icons.Rounded.Vibration, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Test", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SarimCard(modifier = Modifier.clickable { hapticExpanded = !hapticExpanded }, contentPadding = PaddingValues(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SarimIconBadge(icon = Icons.Rounded.Vibration, tint = accents.emerald, size = 38.dp, cornerRadius = 12.dp, iconSize = 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selectedHaptic.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(selectedHaptic.detail, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Icon(if (hapticExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = accents.emerald)
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
                                    if (active) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(18.dp))
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
                            Icon(Icons.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Play", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SarimCard(modifier = Modifier.clickable { soundExpanded = !soundExpanded }, contentPadding = PaddingValues(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SarimIconBadge(icon = Icons.Rounded.VolumeUp, tint = accents.emerald, size = 38.dp, cornerRadius = 12.dp, iconSize = 20.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selectedSound.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(selectedSound.detail, style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Icon(if (soundExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = accents.emerald)
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
                                    if (active) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = accents.emerald, modifier = Modifier.size(18.dp))
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
                        SarimIconBadge(icon = Icons.Rounded.Lock, tint = accents.emerald, size = 44.dp, cornerRadius = 14.dp, iconSize = 21.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Encrypted Backup (.gznd)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Password-protected GZND_ENC_V4 archive compatible with the web app", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = accents.textMuted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                SarimCard(
                    modifier = Modifier
                        .clickable { createJsonLauncher.launch("sarim_backup_${DateUtils.todayIso()}.json") }
                        .testTag("backup_export_btn")
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SarimIconBadge(icon = Icons.Rounded.CloudUpload, tint = accents.cyan, size = 44.dp, cornerRadius = 14.dp, iconSize = 21.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Plain JSON Backup (.json)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Save an unencrypted local snapshot of all collections", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = accents.textMuted)
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
                        SarimIconBadge(icon = Icons.Rounded.Backup, tint = accents.gold, size = 44.dp, cornerRadius = 14.dp, iconSize = 21.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Restore from Phone (.json / .gznd)", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Load and merge a previously saved JSON or encrypted .gznd backup", style = MaterialTheme.typography.bodySmall, color = accents.textMuted)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = accents.textMuted)
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
                SarimIconBadge(
                    icon = Icons.Rounded.EventBusy,
                    tint = accents.danger,
                    size = 36.dp,
                    cornerRadius = 11.dp,
                    iconSize = 18.dp
                )
            }
        )

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                CloseFyCard(
                    "Production",
                    Icons.Rounded.Inventory2,
                    accents.emerald,
                    "${prodActive.size} rec",
                    "${snapshot.stores.count { it.deletedAt == null }} store bal.",
                    "Stores: ${storesList.ifBlank { "None" }}"
                )
            }
            item {
                CloseFyCard(
                    "Sales",
                    Icons.Rounded.ReceiptLong,
                    accents.emerald,
                    "${salesActive.size} rec",
                    "${snapshot.customers.count { it.deletedAt == null }} balances",
                    "Customers carry forward into oldDebit"
                )
            }
            item {
                CloseFyCard(
                    "Rep Clients",
                    Icons.Rounded.Group,
                    accents.cyan,
                    "${repActive.size} rec",
                    "${snapshot.clients.count { it.deletedAt == null }} clients",
                    "Rep customer balances carry forward into oldDebit"
                )
            }
            item {
                CloseFyCard(
                    "Calculator",
                    Icons.Rounded.Calculate,
                    accents.cyan,
                    "${calcActive.size} rec",
                    "returns -> Prod",
                    "Reps settled across ${calcActive.map { it.seller }.filter { it.isNotBlank() }.distinct().size} sellers"
                )
            }
            item {
                CloseFyCard(
                    "Payments",
                    Icons.Rounded.Payments,
                    accents.gold,
                    "${txActive.size} rec",
                    "${snapshot.entities.count { it.deletedAt == null }} entities",
                    "Entities: ${snapshot.entities.count { it.deletedAt == null }}"
                )
            }
            item {
                CloseFyCard(
                    "Factory",
                    Icons.Rounded.Factory,
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
                    Icons.Rounded.Restore,
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
                        SarimIconBadge(
                            icon = Icons.Rounded.WarningAmber,
                            tint = accents.danger,
                            size = 38.dp,
                            cornerRadius = 12.dp,
                            iconSize = 19.dp
                        )
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

private data class RecycleTabFilter(
    val key: String,
    val label: String,
    val collections: Set<String>
)

private val RECYCLE_TAB_FILTERS = listOf(
    RecycleTabFilter("all", "All Tabs", emptySet()),
    RecycleTabFilter("tab_sales", "Sales Tab", setOf("sales", "customers")),
    RecycleTabFilter("tab_rep", "Rep Tab", setOf("rep", "clients")),
    RecycleTabFilter("tab_production", "Manufacturing Tab", setOf("production", "returns")),
    RecycleTabFilter("tab_calculator", "Calculator Tab", setOf("calculator")),
    RecycleTabFilter("tab_factory", "Factory Tab", setOf("factory", "inventory")),
    RecycleTabFilter("tab_payments", "Payments Tab", setOf("transactions", "entities", "expenses")),
    RecycleTabFilter("tab_ledger", "Daily Ledger", setOf("daily_ledger")),
    RecycleTabFilter("tab_formulas", "Formula Store", setOf("formulas", "stores"))
)

@Composable
fun RecycleBinScreen(snapshot: WorkspaceSnapshot, vm: SarimViewModel, onBack: () -> Unit) {
    val accents = LocalSarimAccents.current
    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val moneyRestricted = FinanceEngine.isMoneyRestricted(session)
    val hideAllMoney = snapshot.settings.hideSensitiveMoney

    var colFilter by remember { mutableStateOf("all") }
    var confirmPurgeItem by remember { mutableStateOf<DeletionTombstone?>(null) }
    var confirmEmptyBin by remember { mutableStateOf(false) }

    val sortedDeletions = remember(snapshot.deletions, session) {
        snapshot.deletions
            .filter { FinanceEngine.isRecycleRecordAllowed(it.collection, session) }
            .sortedByDescending { it.deletedAt }
    }
    val availableFilters = remember(sortedDeletions, session) {
        RECYCLE_TAB_FILTERS.filter { tab ->
            tab.key == "all" ||
                tab.collections.any { c -> FinanceEngine.isRecycleRecordAllowed(c, session) } ||
                sortedDeletions.any { it.collection.lowercase(Locale.ROOT) in tab.collections }
        }
    }
    val activeFilterObj = availableFilters.firstOrNull { it.key == colFilter } ?: availableFilters.first()
    val visible = remember(sortedDeletions, activeFilterObj) {
        if (activeFilterObj.key == "all") sortedDeletions
        else sortedDeletions.filter { it.collection.lowercase(Locale.ROOT) in activeFilterObj.collections }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SarimSubpageHeader(
            title = "Recycle Bin",
            subtitle = "${sortedDeletions.size} item(s)",
            onBack = onBack,
            trailingContent = if (session.mode == "admin" && visible.isNotEmpty()) {
                {
                    Surface(
                        onClick = { confirmEmptyBin = true },
                        shape = RoundedCornerShape(14.dp),
                        color = accents.danger.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, accents.danger.copy(alpha = 0.45f)),
                        modifier = Modifier.defaultMinSize(minHeight = 40.dp).testTag("bin_empty_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Empty Bin", tint = accents.danger, modifier = Modifier.size(16.dp))
                            Text("Empty Bin", style = MaterialTheme.typography.labelMedium, color = accents.danger)
                        }
                    }
                }
            } else null
        )

        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (availableFilters.size > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableFilters.forEach { tab ->
                            FilterChip(
                                selected = colFilter == tab.key,
                                onClick = { colFilter = tab.key },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }

            item {
                SarimNoteBanner("Restore puts the record back to its original collection. Records expire after 90 days. Use Delete Forever to erase a record immediately and permanently.")
            }

            if (visible.isEmpty()) {
                item {
                    EmptyStateCard("Recycle Bin is Empty", "Deleted items remain recoverable here for 90 days.")
                }
            } else {
                items(visible, key = { it.id.ifBlank { "${it.collection}_${it.recordId}" } }) { t ->
                    val deletedDate = remember(t.deletedAt) {
                        if (t.deletedAt > 0L) SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(t.deletedAt)) else ""
                    }
                    val daysLeft = remember(t.expiresAt) {
                        if (t.expiresAt > 0L) max(0L, (t.expiresAt - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L)) else 90L
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
                                Icon(Icons.Rounded.Restore, contentDescription = "Restore record", tint = accents.emerald)
                            }
                            if (session.mode == "admin") {
                                IconButton(
                                    onClick = { confirmPurgeItem = t },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("bin_purge_${t.recordId}")
                                ) {
                                    Icon(Icons.Rounded.DeleteForever, contentDescription = "Delete forever", tint = accents.danger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmPurgeItem?.let { itemToPurge ->
        SarimConfirmDialog(
            title = "Delete Permanently?",
            message = "Item: ${itemToPurge.displayName.ifBlank { itemToPurge.recordId }}\nCollection: ${itemToPurge.collection}\n\nThis action cannot be undone.",
            confirmText = "Delete Forever",
            danger = true,
            onConfirm = {
                vm.purgeTombstone(itemToPurge)
                confirmPurgeItem = null
            },
            onDismiss = { confirmPurgeItem = null }
        )
    }

    if (confirmEmptyBin) {
        SarimConfirmDialog(
            title = "Empty Recycle Bin?",
            message = "Permanently erase ${visible.size} deleted item(s)? This action cannot be undone.",
            confirmText = "Empty Bin",
            danger = true,
            onConfirm = {
                val list = visible
                confirmEmptyBin = false
                vm.emptyRecycleBin(list)
            },
            onDismiss = { confirmEmptyBin = false }
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
