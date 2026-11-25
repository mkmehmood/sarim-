package com.gullzubair.sarim.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.theme.SarimTheme
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.util.BiometricLockHelper
import com.gullzubair.sarim.util.FeedbackHelper
import kotlinx.coroutines.launch

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

    val themeMode = (workspaceState as? UiState.Success)?.data?.settings?.themeMode ?: "light"

    SarimTheme(themeMode = themeMode) {
        when (val state = workspaceState) {
            UiState.Loading -> {
                Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun WorkspaceNavContainer(
    snapshot: WorkspaceSnapshot,
    selectedTab: MainTab,
    todayIso: String,
    vm: SarimViewModel,
    snackbarHostState: SnackbarHostState,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val accents = LocalSarimAccents.current
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRouteName = backStackEntry?.destination?.route ?: ""
    val isHome = currentRouteName.contains("WorkspaceHome") || currentRouteName.isEmpty()

    val visibleTabs = remember(snapshot.profile) { vm.visibleTabsFor(snapshot.profile) }
    val pendingCount = remember(snapshot.pendingApprovals) { snapshot.pendingApprovals.count { it.status == "pending" } }

    BackHandler(enabled = isHome && selectedTab != MainTab.OVERVIEW) {
        vm.selectTab(MainTab.OVERVIEW)
    }

    fun navigateTo(route: AppRoute) {
        FeedbackHelper.triggerHaptic(context)
        scope.launch { drawerState.close() }
        navController.navigate(route)
    }

    fun selectMainTab(tab: MainTab) {
        FeedbackHelper.triggerHaptic(context)
        scope.launch { drawerState.close() }
        vm.selectTab(tab)
        if (!isHome) {
            navController.popBackStack(AppRoute.WorkspaceHome, inclusive = false)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = accents.headerFrosted,
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                modifier = Modifier.width(292.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(accents.glassTop.copy(alpha = 0.85f), accents.headerFrosted, accents.headerFrosted)
                            )
                        )
                ) {
                    // Drawer Brand Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = "GULL AND ZUBAIR",
                            fontFamily = JakartaFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.6.sp,
                            color = accents.emerald
                        )
                    }
                    HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))

                    // Scrollable Menu Sections
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        DrawerSectionLabel("NAVIGATION")
                        Spacer(Modifier.height(4.dp))
                        visibleTabs.forEach { tab ->
                            val selected = isHome && selectedTab == tab
                            DrawerRowItem(
                                label = tab.label.uppercase(),
                                icon = tabIcon(tab),
                                iconTint = tabIconColor(tab),
                                selected = selected,
                                testTag = "drawer_tab_${tab.key}",
                                onClick = { selectMainTab(tab) }
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        DrawerSectionLabel("DAILY WORK")
                        Spacer(Modifier.height(4.dp))
                        DrawerRowItem(
                            label = "APPROVALS",
                            icon = Icons.Outlined.CheckCircle,
                            iconTint = accents.emerald,
                            selected = currentRouteName.contains("Approvals"),
                            badgeCount = pendingCount,
                            testTag = "drawer_approvals",
                            onClick = { navigateTo(AppRoute.Approvals) }
                        )
                        DrawerRowItem(
                            label = "DAILY LEDGER",
                            icon = Icons.Outlined.MenuBook,
                            iconTint = Color(0xFF0D9488),
                            selected = currentRouteName.contains("DailyLedger"),
                            testTag = "drawer_daily_ledger",
                            onClick = { navigateTo(AppRoute.DailyLedger) }
                        )
                        DrawerRowItem(
                            label = "STOCK TRANSFER",
                            icon = Icons.Outlined.SwapHoriz,
                            iconTint = accents.gold,
                            selected = currentRouteName.contains("StockTransfer"),
                            testTag = "drawer_stock_transfer",
                            onClick = { navigateTo(AppRoute.StockTransfer) }
                        )
                        DrawerRowItem(
                            label = "PAYMENT TRANSFER",
                            icon = Icons.Outlined.CurrencyExchange,
                            iconTint = Color(0xFF059669),
                            selected = currentRouteName.contains("PaymentTransfer"),
                            testTag = "drawer_payment_transfer",
                            onClick = { navigateTo(AppRoute.PaymentTransfer) }
                        )

                        Spacer(Modifier.height(14.dp))
                        DrawerSectionLabel("RECORDS")
                        Spacer(Modifier.height(4.dp))
                        DrawerRowItem(
                            label = "ADD ENTITY",
                            icon = Icons.Outlined.PersonAdd,
                            iconTint = accents.cyan,
                            selected = currentRouteName.contains("AddEntity"),
                            testTag = "drawer_add_entity",
                            onClick = { navigateTo(AppRoute.AddEntity) }
                        )
                        DrawerRowItem(
                            label = "ADD RAW MATERIALS",
                            icon = Icons.Outlined.Eco,
                            iconTint = Color(0xFF16A34A),
                            selected = currentRouteName.contains("RawMaterialPurchase"),
                            testTag = "drawer_add_raw_materials",
                            onClick = { navigateTo(AppRoute.RawMaterialPurchase) }
                        )
                        DrawerRowItem(
                            label = "FORMULA STORE",
                            icon = Icons.Outlined.Science,
                            iconTint = accents.gold,
                            selected = currentRouteName.contains("FormulaStore"),
                            testTag = "drawer_formula_store",
                            onClick = { navigateTo(AppRoute.FormulaStore) }
                        )
                    }

                    // Sticky Footer (SETTINGS & LOGOUT)
                    HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        DrawerRowItem(
                            label = "SYNC NOW",
                            icon = Icons.Outlined.Sync,
                            iconTint = accents.emerald,
                            selected = false,
                            badgeCount = snapshot.pendingSyncCount,
                            testTag = "drawer_sync",
                            onClick = {
                                scope.launch { drawerState.close() }
                                vm.syncNow()
                            }
                        )
                        DrawerRowItem(
                            label = "SETTINGS",
                            icon = Icons.Outlined.Settings,
                            iconTint = accents.emerald,
                            selected = currentRouteName.contains("Settings"),
                            testTag = "drawer_settings",
                            onClick = { navigateTo(AppRoute.Settings) }
                        )
                        DrawerRowItem(
                            label = "LOGOUT",
                            icon = Icons.AutoMirrored.Outlined.Logout,
                            iconTint = accents.danger,
                            textColor = accents.danger,
                            selected = false,
                            testTag = "drawer_logout",
                            onClick = {
                                scope.launch { drawerState.close() }
                                onSignOut()
                            }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = if (isHome) WindowInsets.safeDrawing else WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (isHome) {
                    val headerShape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = if (accents.isDark) 0.dp else 5.dp,
                                shape = headerShape,
                                ambientColor = Color(0x14059669),
                                spotColor = Color(0x14059669)
                            ),
                        color = accents.headerFrosted,
                        shape = headerShape,
                        border = BorderStroke(1.dp, accents.borderSubtle.copy(alpha = 0.65f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    FeedbackHelper.triggerHaptic(context)
                                    scope.launch { drawerState.open() }
                                },
                                modifier = Modifier.size(48.dp).testTag("drawer_menu_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open navigation menu",
                                    tint = accents.emerald
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = snapshot.profile.displayName.ifBlank { "GULL AND ZUBAIR" }.uppercase(),
                                    fontFamily = JakartaFont,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    letterSpacing = 1.2.sp,
                                    color = accents.emerald,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (selectedTab != MainTab.OVERVIEW) {
                                    Text(
                                        text = selectedTab.label.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.9.sp),
                                        color = accents.textMuted,
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (pendingCount > 0) {
                                    IconButton(
                                        onClick = { navigateTo(AppRoute.Approvals) },
                                        modifier = Modifier.size(48.dp).testTag("topbar_approvals_btn")
                                    ) {
                                        BadgedBox(badge = { Badge { Text("$pendingCount") } }) {
                                            Icon(Icons.Default.Approval, contentDescription = "Approvals queue", tint = accents.gold)
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { vm.togglePrivacyMask() },
                                    modifier = Modifier.size(48.dp).testTag("topbar_privacy_btn")
                                ) {
                                    Icon(
                                        imageVector = if (snapshot.settings.hideSensitiveMoney) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle money privacy",
                                        tint = accents.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
                val containerWidth = this.maxWidth
                val useRail = isHome && containerWidth >= 600.dp
                Row(Modifier.fillMaxSize()) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = accents.headerFrosted,
                            modifier = Modifier.fillMaxHeight()
                        ) {
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

                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.WorkspaceHome,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) {
                        composable<AppRoute.WorkspaceHome> {
                            when (selectedTab) {
                                MainTab.OVERVIEW -> OverviewScreen(snapshot, vm, { navigateTo(AppRoute.Approvals) }, { navigateTo(AppRoute.DailyLedger) })
                                MainTab.PRODUCTION -> ProductionScreen(snapshot, todayIso, vm) { navigateTo(AppRoute.StockTransfer) }
                                MainTab.SALES -> SalesScreen(snapshot, todayIso, vm, { cust -> navigateTo(AppRoute.CustomerDetail(cust)) }, { cust -> navigateTo(AppRoute.EditCustomer(cust)) })
                                MainTab.CALCULATOR -> CalculatorScreen(snapshot, todayIso, vm)
                                MainTab.FACTORY -> FactoryScreen(snapshot, todayIso, vm, { navigateTo(AppRoute.RawMaterialPurchase) }, { navigateTo(AppRoute.FormulaStore) })
                                MainTab.PAYMENTS -> PaymentsScreen(snapshot, todayIso, vm, { navigateTo(AppRoute.AddEntity) }, { navigateTo(AppRoute.PaymentTransfer) }, { ent -> navigateTo(AppRoute.EntityDetail(ent)) }, { name -> navigateTo(AppRoute.ExpenseDetail(name)) })
                                MainTab.REP_SALES -> RepSalesScreen(snapshot, todayIso, vm) { cust -> navigateTo(AppRoute.RepCustomerManagement(cust)) }
                            }
                        }
                        composable<AppRoute.DailyLedger> {
                            DailyLedgerScreen(snapshot, todayIso, vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.Approvals> {
                            ApprovalsScreen(snapshot, vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.StockTransfer> {
                            StockTransferScreen(snapshot, todayIso, vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.PaymentTransfer> {
                            PaymentTransferScreen(snapshot, todayIso, vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.StoreManager> {
                            StoreManagerScreen(snapshot, vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.FormulaStore> {
                            FormulaStoreScreen(
                                snapshot = snapshot,
                                onBack = { navController.popBackStack() },
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
                            BatchMixScreen(snapshot, arg.formulaId, vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.BatchFix> { entry ->
                            val arg = entry.toRoute<AppRoute.BatchFix>()
                            BatchFixScreen(snapshot, arg.formulaId) { navController.popBackStack() }
                        }
                        composable<AppRoute.RawMaterialPurchase> {
                            RawMaterialScreen(snapshot, todayIso, vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.AddEntity> {
                            AddEntityScreen(vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.EditCustomer> { entry ->
                            val arg = entry.toRoute<AppRoute.EditCustomer>()
                            EditCustomerScreen(snapshot, arg.customerName, vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.AppAccounts> {
                            AppAccountsScreen(snapshot, vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.RecycleBin> {
                            RecycleBinScreen(snapshot, vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.BackupRestore> {
                            BackupRestoreScreen(snapshot, vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.DbStructure> {
                            DbStructureScreen(snapshot) { navController.popBackStack() }
                        }
                        composable<AppRoute.SoundHaptics> {
                            SoundAndHapticsScreen { navController.popBackStack() }
                        }
                        composable<AppRoute.CloseFinancialYear> {
                            CloseFinancialYearScreen(snapshot, vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.Settings> {
                            SettingsScreen(
                                snapshot = snapshot,
                                vm = vm,
                                onBack = { navController.popBackStack() },
                                onNavigateRoute = { route -> navigateTo(route) },
                                onSignOut = onSignOut
                            )
                        }
                        composable<AppRoute.CustomerDetail> { entry ->
                            val arg = entry.toRoute<AppRoute.CustomerDetail>()
                            CustomerDetailScreen(
                                snapshot = snapshot,
                                customerName = arg.customerName,
                                onBack = { navController.popBackStack() },
                                onEditCustomer = { navigateTo(AppRoute.EditCustomer(arg.customerName)) }
                            )
                        }
                        composable<AppRoute.EntityDetail> { entry ->
                            val arg = entry.toRoute<AppRoute.EntityDetail>()
                            EntityDetailScreen(snapshot, arg.entityName) { navController.popBackStack() }
                        }
                        composable<AppRoute.RepCustomerManagement> { entry ->
                            val arg = entry.toRoute<AppRoute.RepCustomerManagement>()
                            RepCustomerManagementScreen(
                                snapshot = snapshot,
                                customerName = arg.customerName,
                                vm = vm,
                                onEdit = { navigateTo(AppRoute.EditCustomer(arg.customerName)) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable<AppRoute.ExpenseDetail> { entry ->
                            val arg = entry.toRoute<AppRoute.ExpenseDetail>()
                            ExpenseDetailsScreen(snapshot, arg.expenseName, todayIso, vm) { navController.popBackStack() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = JakartaFont,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
        color = LocalSarimAccents.current.textMuted,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

@Composable
private fun DrawerRowItem(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    badgeCount: Int = 0,
    textColor: Color? = null
) {
    val accents = LocalSarimAccents.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(if (selected) accents.softPillFill else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accents.emerald else iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            fontFamily = JakartaFont,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 12.5.sp,
            letterSpacing = 0.8.sp,
            color = textColor ?: if (selected) accents.emerald else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accents.gold.copy(alpha = 0.18f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text("$badgeCount", style = MaterialTheme.typography.labelSmall, color = accents.gold)
            }
        }
    }
}

private fun tabIcon(tab: MainTab): ImageVector = when (tab) {
    MainTab.OVERVIEW -> Icons.Outlined.GridView
    MainTab.PRODUCTION -> Icons.Outlined.Inventory2
    MainTab.SALES -> Icons.Outlined.ReceiptLong
    MainTab.CALCULATOR -> Icons.Outlined.Calculate
    MainTab.FACTORY -> Icons.Outlined.Factory
    MainTab.PAYMENTS -> Icons.Outlined.CreditCard
    MainTab.REP_SALES -> Icons.Outlined.TrendingUp
}

private fun tabIconColor(tab: MainTab): Color = when (tab) {
    MainTab.OVERVIEW -> Color(0xFF059669)
    MainTab.PRODUCTION -> Color(0xFF10B981)
    MainTab.SALES -> Color(0xFF0891B2)
    MainTab.CALCULATOR -> Color(0xFF16A34A)
    MainTab.FACTORY -> Color(0xFF0D9488)
    MainTab.PAYMENTS -> Color(0xFFD97706)
    MainTab.REP_SALES -> Color(0xFF059669)
}
