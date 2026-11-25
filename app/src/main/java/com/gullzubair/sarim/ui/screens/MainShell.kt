package com.gullzubair.sarim.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.gullzubair.sarim.data.firebase.signOutUser
import com.gullzubair.sarim.data.model.AppRoute
import com.gullzubair.sarim.data.model.MainTab
import com.gullzubair.sarim.data.model.UiState
import com.gullzubair.sarim.data.model.WorkspaceSnapshot
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.theme.SarimTheme
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.BiometricLockHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainFeatureScreen(currentUserId: String) {
    val vm: SarimViewModel = viewModel(
        key = currentUserId,
        factory = SarimViewModel.factory(currentUserId)
    )
    val workspaceState by vm.workspaceState.collectAsStateWithLifecycle()
    val selectedTab by vm.selectedTab.collectAsStateWithLifecycle()
    val todayIso by vm.todayIso.collectAsStateWithLifecycle()
    val biometricUnlocked by vm.biometricUnlocked.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        vm.refreshTodayDate()
    }

    LaunchedEffect(Unit) {
        vm.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val themeMode = (workspaceState as? UiState.Success)?.data?.settings?.themeMode ?: "dark"

    SarimTheme(themeMode = themeMode) {
        when (val state = workspaceState) {
            UiState.Loading -> {
                Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text("Syncing Sarim Workspace...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            is UiState.Error -> {
                Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                        EmptyStateCard(
                            title = "Workspace Sync Unavailable",
                            detail = state.message,
                            actionLabel = "Retry Sync",
                            onAction = { vm.retrySync() }
                        )
                    }
                }
            }

            is UiState.Success -> {
                val snapshot = state.data
                if (snapshot.settings.biometricLockEnabled && !biometricUnlocked) {
                    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(12.dp))
                                Text("Workspace Locked", style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val act = context as? FragmentActivity
                                        if (act != null && BiometricLockHelper.isBiometricAvailable(act)) {
                                            BiometricLockHelper.authenticate(
                                                activity = act,
                                                onSuccess = { vm.setBiometricUnlocked(true) },
                                                onError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                            )
                                        } else {
                                            vm.setBiometricUnlocked(true)
                                        }
                                    },
                                    modifier = Modifier.testTag("biometric_unlock_btn")
                                ) {
                                    Text("Unlock")
                                }
                            }
                        }
                    }
                } else {
                    WorkspaceNavContainer(
                        snapshot = snapshot,
                        selectedTab = selectedTab,
                        todayIso = todayIso,
                        vm = vm,
                        snackbarHostState = snackbarHostState,
                        onSignOut = {
                            signOutUser(credentialManager = credentialManager, scope = scope)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceNavContainer(
    snapshot: WorkspaceSnapshot,
    selectedTab: MainTab,
    todayIso: String,
    vm: SarimViewModel,
    snackbarHostState: SnackbarHostState,
    onSignOut: () -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRouteName = backStackEntry?.destination?.route ?: ""
    val isHome = currentRouteName.contains("WorkspaceHome") || currentRouteName.isEmpty()

    val visibleTabs = remember(snapshot.profile) { vm.visibleTabsFor(snapshot.profile) }
    val pendingCount = remember(snapshot.pendingApprovals) { snapshot.pendingApprovals.count { it.status == "pending" } }

    fun navigateTo(route: AppRoute) {
        scope.launch { drawerState.close() }
        navController.navigate(route)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                    Text("GULL & ZUBAIR", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(snapshot.profile.displayName, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    val drawerItems: List<Triple<String, ImageVector, AppRoute>> = listOf(
                        Triple("Daily Ledger", Icons.Default.MenuBook, AppRoute.DailyLedger),
                        Triple("Approvals ($pendingCount)", Icons.Default.Approval, AppRoute.Approvals),
                        Triple("Stock Transfer", Icons.Default.SwapHoriz, AppRoute.StockTransfer),
                        Triple("Payment Transfer", Icons.Default.AccountBalance, AppRoute.PaymentTransfer),
                        Triple("Store Manager", Icons.Default.Store, AppRoute.StoreManager),
                        Triple("Formula Store", Icons.Default.Science, AppRoute.FormulaStore),
                        Triple("Batch Mix", Icons.Default.Calculate, AppRoute.BatchMix()),
                        Triple("Batch Correction", Icons.Default.PrecisionManufacturing, AppRoute.BatchFix()),
                        Triple("Raw Material Purchase", Icons.Default.Inventory, AppRoute.RawMaterialPurchase),
                        Triple("Add Entity", Icons.Default.AddBusiness, AppRoute.AddEntity),
                        Triple("Team & Roles", Icons.Default.Group, AppRoute.AppAccounts),
                        Triple("Recycle Bin", Icons.Default.DeleteSweep, AppRoute.RecycleBin),
                        Triple("Backup & Export", Icons.Default.Backup, AppRoute.BackupRestore),
                        Triple("Database Stats", Icons.Default.Storage, AppRoute.DbStructure),
                        Triple("Settings", Icons.Default.Settings, AppRoute.Settings)
                    )
                    drawerItems.forEach { (label, icon, route) ->
                        NavigationDrawerItem(
                            label = { Text(label) },
                            icon = { Icon(icon, contentDescription = null) },
                            selected = false,
                            onClick = { navigateTo(route) }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (isHome) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.testTag("drawer_menu_btn")) {
                                Icon(Icons.Default.Menu, contentDescription = "Open navigation menu")
                            }
                        } else {
                            IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.testTag("nav_back_btn")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
                            }
                        }
                    },
                    title = {
                        Text(
                            if (isHome) selectedTab.label else "Gull & Zubair",
                            maxLines = 1
                        )
                    },
                    actions = {
                        IconButton(onClick = { vm.togglePrivacyMask() }, modifier = Modifier.testTag("topbar_privacy_btn")) {
                            Icon(
                                if (snapshot.settings.hideSensitiveMoney) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle money privacy"
                            )
                        }
                        IconButton(onClick = { navigateTo(AppRoute.Approvals) }, modifier = Modifier.testTag("topbar_approvals_btn")) {
                            BadgedBox(badge = { if (pendingCount > 0) Badge { Text("$pendingCount") } }) {
                                Icon(Icons.Default.Approval, contentDescription = "Approvals queue")
                            }
                        }
                        IconButton(onClick = { vm.toggleThemeMode() }, modifier = Modifier.testTag("topbar_theme_btn")) {
                            Icon(Icons.Default.DarkMode, contentDescription = "Switch theme")
                        }
                    }
                )
            },
            bottomBar = {
                if (isHome) {
                    NavigationBar {
                        visibleTabs.take(5).forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { vm.selectTab(tab) },
                                icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("bottom_tab_${tab.key}")
                            )
                        }
                    }
                }
            }
        ) { padding ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
                val containerWidth = this.maxWidth
                val useRail = isHome && (containerWidth >= 600.dp || visibleTabs.size > 5)
                Row(Modifier.fillMaxSize()) {
                    if (useRail && containerWidth >= 600.dp) {
                        NavigationRail(modifier = Modifier.fillMaxHeight()) {
                            visibleTabs.forEach { tab ->
                                NavigationRailItem(
                                    selected = selectedTab == tab,
                                    onClick = { vm.selectTab(tab) },
                                    icon = { Icon(tabIcon(tab), contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("rail_tab_${tab.key}")
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        if (isHome && containerWidth < 600.dp && visibleTabs.size > 5) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                visibleTabs.drop(5).forEach { extra ->
                                    androidx.compose.material3.FilterChip(
                                        selected = selectedTab == extra,
                                        onClick = { vm.selectTab(extra) },
                                        leadingIcon = { Icon(tabIcon(extra), contentDescription = null) },
                                        label = { Text(extra.label) },
                                        modifier = Modifier.testTag("extra_tab_${extra.key}")
                                    )
                                }
                            }
                        }

                        NavHost(
                            navController = navController,
                            startDestination = AppRoute.WorkspaceHome,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            composable<AppRoute.WorkspaceHome> {
                                when (selectedTab) {
                                    MainTab.OVERVIEW -> OverviewScreen(snapshot, vm, { navigateTo(AppRoute.Approvals) }, { navigateTo(AppRoute.DailyLedger) })
                                    MainTab.PRODUCTION -> ProductionScreen(snapshot, todayIso, vm) { navigateTo(AppRoute.StockTransfer) }
                                    MainTab.SALES -> SalesScreen(snapshot, todayIso, vm) { cust -> navigateTo(AppRoute.CustomerDetail(cust)) }
                                    MainTab.CALCULATOR -> CalculatorScreen(snapshot, todayIso, vm)
                                    MainTab.FACTORY -> FactoryScreen(snapshot, todayIso, vm, { navigateTo(AppRoute.RawMaterialPurchase) }, { navigateTo(AppRoute.FormulaStore) })
                                    MainTab.PAYMENTS -> PaymentsScreen(snapshot, todayIso, vm, { navigateTo(AppRoute.AddEntity) }, { navigateTo(AppRoute.PaymentTransfer) }, { ent -> navigateTo(AppRoute.EntityDetail(ent)) })
                                    MainTab.REP_SALES -> RepSalesScreen(snapshot, todayIso, vm)
                                }
                            }
                            composable<AppRoute.DailyLedger> { DailyLedgerScreen(snapshot, todayIso, vm) }
                            composable<AppRoute.Approvals> { ApprovalsScreen(snapshot, vm) }
                            composable<AppRoute.StockTransfer> { StockTransferScreen(snapshot, todayIso, vm) { navController.popBackStack() } }
                            composable<AppRoute.PaymentTransfer> { PaymentTransferScreen(snapshot, todayIso, vm) { navController.popBackStack() } }
                            composable<AppRoute.StoreManager> { StoreManagerScreen(snapshot, vm) }
                            composable<AppRoute.FormulaStore> {
                                FormulaStoreScreen(
                                    snapshot = snapshot,
                                    onEditFormula = { id -> navigateTo(AppRoute.FormulaEdit(id)) },
                                    onOpenMix = { id -> navigateTo(AppRoute.BatchMix(id)) },
                                    onOpenFix = { id -> navigateTo(AppRoute.BatchFix(id)) }
                                )
                            }
                            composable<AppRoute.FormulaEdit> { entry ->
                                val arg = entry.toRoute<AppRoute.FormulaEdit>()
                                FormulaEditScreen(snapshot, arg.formulaId, vm) { navController.popBackStack() }
                            }
                            composable<AppRoute.BatchMix> { entry ->
                                val arg = entry.toRoute<AppRoute.BatchMix>()
                                BatchMixScreen(snapshot, arg.formulaId)
                            }
                            composable<AppRoute.BatchFix> { entry ->
                                val arg = entry.toRoute<AppRoute.BatchFix>()
                                BatchFixScreen(snapshot, arg.formulaId)
                            }
                            composable<AppRoute.RawMaterialPurchase> { RawMaterialScreen(snapshot, todayIso, vm) { navController.popBackStack() } }
                            composable<AppRoute.AddEntity> { AddEntityScreen(vm) { navController.popBackStack() } }
                            composable<AppRoute.AppAccounts> { AppAccountsScreen(snapshot, vm) }
                            composable<AppRoute.RecycleBin> { RecycleBinScreen(snapshot, vm) }
                            composable<AppRoute.BackupRestore> { BackupRestoreScreen(snapshot) }
                            composable<AppRoute.DbStructure> { DbStructureScreen(snapshot) }
                            composable<AppRoute.Settings> { SettingsScreen(snapshot, vm, onSignOut) }
                            composable<AppRoute.CustomerDetail> { entry ->
                                val arg = entry.toRoute<AppRoute.CustomerDetail>()
                                CustomerDetailScreen(snapshot, arg.customerName)
                            }
                            composable<AppRoute.EntityDetail> { entry ->
                                val arg = entry.toRoute<AppRoute.EntityDetail>()
                                EntityDetailScreen(snapshot, arg.entityName)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: MainTab): ImageVector = when (tab) {
    MainTab.OVERVIEW -> Icons.Default.Dashboard
    MainTab.PRODUCTION -> Icons.Default.PrecisionManufacturing
    MainTab.SALES -> Icons.Default.PointOfSale
    MainTab.CALCULATOR -> Icons.Default.Calculate
    MainTab.FACTORY -> Icons.Default.Factory
    MainTab.PAYMENTS -> Icons.Default.Payments
    MainTab.REP_SALES -> Icons.Default.DirectionsRun
}
