package com.gullzubair.sarim.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.rounded.Logout
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
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Factory
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TrendingUp
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
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
import com.gullzubair.sarim.domain.FinanceEngine
import com.gullzubair.sarim.ui.components.EmptyStateCard
import com.gullzubair.sarim.ui.components.LiquidToastOverlay
import com.gullzubair.sarim.ui.components.OfflineBanner
import com.gullzubair.sarim.ui.components.SarimConfirmDialog
import com.gullzubair.sarim.ui.components.SarimIconBadge
import com.gullzubair.sarim.ui.theme.JakartaFont
import com.gullzubair.sarim.ui.theme.LocalSarimAccents
import com.gullzubair.sarim.ui.theme.SarimMotion
import com.gullzubair.sarim.ui.theme.SarimTheme
import com.gullzubair.sarim.ui.viewmodel.SarimViewModel
import com.gullzubair.sarim.ui.viewmodel.ToastKind
import com.gullzubair.sarim.util.BiometricLockHelper
import com.gullzubair.sarim.util.FeedbackHelper
import kotlinx.coroutines.launch

private val ScreenEnterEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private val ScreenLeaveEasing = CubicBezierEasing(0.4f, 0f, 0.8f, 0.6f)

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

    LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        vm.refreshTodayDate()
    }

    val themeMode = (workspaceState as? UiState.Success)?.data?.settings?.themeMode ?: "auto"

    SarimTheme(themeMode = themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                                    SarimIconBadge(
                                        icon = Icons.Rounded.Lock,
                                        tint = MaterialTheme.colorScheme.primary,
                                        size = 56.dp,
                                        cornerRadius = 18.dp,
                                        iconSize = 28.dp
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    Text("Workspace Locked", style = MaterialTheme.typography.headlineSmall)
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val act = context as? FragmentActivity
                                            if (act != null && BiometricLockHelper.isBiometricAvailable(act)) {
                                                BiometricLockHelper.authenticate(
                                                    activity = act,
                                                    onSuccess = { vm.setBiometricUnlocked(true) },
                                                    onError = { msg -> vm.showToast(msg, ToastKind.ERROR) }
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
                            onSignOut = {
                                signOutUser(credentialManager = credentialManager, scope = scope)
                            }
                        )
                    }
                }
            }
            LiquidToastOverlay(
                toastEvents = vm.toastEvents,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun WorkspaceNavContainer(
    snapshot: WorkspaceSnapshot,
    selectedTab: MainTab,
    todayIso: String,
    vm: SarimViewModel,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val accents = LocalSarimAccents.current
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSignOutConfirm by remember { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRouteName = backStackEntry?.destination?.route ?: ""
    val isHome = currentRouteName.contains("WorkspaceHome") || currentRouteName.isEmpty()

    val session = remember(snapshot.profile) { vm.sessionInfoFor(snapshot.profile) }
    val isAdminSession = session.mode == "admin"
    val moneyRestricted = FinanceEngine.isMoneyRestricted(session)
    val visibleTabs = remember(snapshot.profile) { vm.visibleTabsFor(snapshot.profile) }
    val effectiveTab = if (selectedTab in visibleTabs) selectedTab else visibleTabs.firstOrNull() ?: MainTab.OVERVIEW
    val pendingCount = remember(snapshot.pendingApprovals) { snapshot.pendingApprovals.count { it.status == "pending" } }

    LaunchedEffect(visibleTabs, selectedTab) {
        if (visibleTabs.isNotEmpty() && selectedTab !in visibleTabs) {
            vm.selectTab(visibleTabs.first())
        }
    }

    BackHandler(enabled = isHome && visibleTabs.isNotEmpty() && effectiveTab != visibleTabs.first()) {
        vm.selectTab(visibleTabs.first())
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
                drawerShape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp),
                modifier = Modifier.width(296.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(accents.glassTop.copy(alpha = 0.9f), accents.headerFrosted, accents.headerFrosted)
                            )
                        )
                ) {
                    // Drawer Brand Header (matches 08-sidebar)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GULL AND ZUBAIR",
                            fontFamily = JakartaFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp,
                            letterSpacing = 1.4.sp,
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
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (isAdminSession || visibleTabs.size > 1) {
                            DrawerSectionLabel("NAVIGATION")
                            Spacer(Modifier.height(4.dp))
                            visibleTabs.forEach { tab ->
                                val selected = isHome && effectiveTab == tab
                                DrawerRowItem(
                                    label = tab.label,
                                    icon = tabIcon(tab, selected),
                                    iconTint = tabIconColor(tab),
                                    selected = selected,
                                    testTag = "drawer_tab_${tab.key}",
                                    onClick = { selectMainTab(tab) }
                                )
                            }
                        }

                        if (isAdminSession) {
                            Spacer(Modifier.height(14.dp))
                            DrawerSectionLabel("DAILY WORK")
                            Spacer(Modifier.height(4.dp))
                            val approvalsSelected = currentRouteName.contains("Approvals")
                            DrawerRowItem(
                                label = "APPROVALS",
                                icon = if (approvalsSelected) Icons.Rounded.CheckCircle else Icons.Outlined.CheckCircle,
                                iconTint = accents.emerald,
                                selected = approvalsSelected,
                                badgeCount = pendingCount,
                                testTag = "drawer_approvals",
                                onClick = { navigateTo(AppRoute.Approvals) }
                            )
                            val ledgerSelected = currentRouteName.contains("DailyLedger")
                            DrawerRowItem(
                                label = "DAILY LEDGER",
                                icon = if (ledgerSelected) Icons.Rounded.MenuBook else Icons.Outlined.MenuBook,
                                iconTint = Color(0xFF0D9488),
                                selected = ledgerSelected,
                                testTag = "drawer_daily_ledger",
                                onClick = { navigateTo(AppRoute.DailyLedger) }
                            )
                            val stockTrSelected = currentRouteName.contains("StockTransfer")
                            DrawerRowItem(
                                label = "STOCK TRANSFER",
                                icon = if (stockTrSelected) Icons.Rounded.SwapHoriz else Icons.Outlined.SwapHoriz,
                                iconTint = accents.gold,
                                selected = stockTrSelected,
                                testTag = "drawer_stock_transfer",
                                onClick = { navigateTo(AppRoute.StockTransfer) }
                            )
                            val payTrSelected = currentRouteName.contains("PaymentTransfer")
                            DrawerRowItem(
                                label = "PAYMENT TRANSFER",
                                icon = if (payTrSelected) Icons.Rounded.CurrencyExchange else Icons.Outlined.CurrencyExchange,
                                iconTint = Color(0xFF059669),
                                selected = payTrSelected,
                                testTag = "drawer_payment_transfer",
                                onClick = { navigateTo(AppRoute.PaymentTransfer) }
                            )

                            Spacer(Modifier.height(14.dp))
                            DrawerSectionLabel("RECORDS")
                            Spacer(Modifier.height(4.dp))
                            val addEntitySelected = currentRouteName.contains("AddEntity")
                            DrawerRowItem(
                                label = "ADD ENTITY",
                                icon = if (addEntitySelected) Icons.Rounded.PersonAdd else Icons.Outlined.PersonAdd,
                                iconTint = accents.cyan,
                                selected = addEntitySelected,
                                testTag = "drawer_add_entity",
                                onClick = { navigateTo(AppRoute.AddEntity) }
                            )
                            val rawMatSelected = currentRouteName.contains("RawMaterialPurchase")
                            DrawerRowItem(
                                label = "ADD RAW MATERIALS",
                                icon = if (rawMatSelected) Icons.Rounded.Eco else Icons.Outlined.Eco,
                                iconTint = Color(0xFF16A34A),
                                selected = rawMatSelected,
                                testTag = "drawer_add_raw_materials",
                                onClick = { navigateTo(AppRoute.RawMaterialPurchase) }
                            )
                            val formulaSelected = currentRouteName.contains("FormulaStore")
                            DrawerRowItem(
                                label = "FORMULA STORE",
                                icon = if (formulaSelected) Icons.Rounded.Science else Icons.Outlined.Science,
                                iconTint = accents.gold,
                                selected = formulaSelected,
                                testTag = "drawer_formula_store",
                                onClick = { navigateTo(AppRoute.FormulaStore) }
                            )
                        }
                    }

                    // Sticky Footer (SETTINGS & LOGOUT — matches web sidebar-footer)
                    HorizontalDivider(color = accents.borderSubtle.copy(alpha = 0.6f))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        val settingsSelected = currentRouteName.contains("Settings")
                        DrawerRowItem(
                            label = "SETTINGS",
                            icon = if (settingsSelected) Icons.Rounded.Settings else Icons.Outlined.Settings,
                            iconTint = accents.emerald,
                            selected = settingsSelected,
                            testTag = "drawer_settings",
                            onClick = { navigateTo(AppRoute.Settings) }
                        )
                        DrawerRowItem(
                            label = "LOGOUT",
                            icon = Icons.AutoMirrored.Rounded.Logout,
                            iconTint = accents.danger,
                            textColor = accents.danger,
                            selected = false,
                            testTag = "drawer_logout",
                            onClick = {
                                scope.launch { drawerState.close() }
                                showSignOutConfirm = true
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
            topBar = {
                if (isHome) {
                    val headerShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = if (accents.isDark) 10.dp else 7.dp,
                                shape = headerShape,
                                ambientColor = accents.cyan.copy(alpha = if (accents.isDark) 0.22f else 0.16f),
                                spotColor = accents.cyan.copy(alpha = if (accents.isDark) 0.24f else 0.16f)
                            ),
                        color = accents.headerFrosted,
                        shape = headerShape,
                        border = BorderStroke(1.dp, accents.borderSubtle.copy(alpha = 0.85f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        FeedbackHelper.triggerHaptic(context)
                                        scope.launch { drawerState.open() }
                                    }
                                    .testTag("drawer_menu_btn"),
                                contentAlignment = Alignment.Center
                            ) {
                                val menuBrush = Brush.horizontalGradient(
                                    listOf(accents.emerald, accents.cyan)
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.5.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(22.dp)
                                            .height(2.4.dp)
                                            .clip(CircleShape)
                                            .background(menuBrush)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(14.dp)
                                            .height(2.4.dp)
                                            .clip(CircleShape)
                                            .background(menuBrush)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .height(2.4.dp)
                                            .clip(CircleShape)
                                            .background(menuBrush)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                val headerTitle = when (session.mode) {
                                    "admin" -> snapshot.profile.displayName.ifBlank { "MAHMOOD KHAN" }
                                    "rep" -> snapshot.profile.displayName.ifBlank { "Sales Rep" }
                                    "production" -> snapshot.profile.displayName.ifBlank { "Production Manager" }
                                    "factory" -> snapshot.profile.displayName.ifBlank { "Factory Manager" }
                                    else -> snapshot.profile.displayName.ifBlank { "User" }
                                }.uppercase()
                                Text(
                                    text = headerTitle,
                                    fontFamily = JakartaFont,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    letterSpacing = 1.0.sp,
                                    color = accents.emerald,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.size(48.dp))
                        }
                    }
                }
            }
        ) { padding ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
                val containerWidth = this.maxWidth
                val useRail = isHome && containerWidth >= 600.dp && (isAdminSession || visibleTabs.size > 1)
                Column(Modifier.fillMaxSize()) {
                    OfflineBanner(
                        isOnline = vm.isOnline(),
                        pendingSyncCount = snapshot.pendingSyncCount
                    )
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = accents.headerFrosted,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            visibleTabs.forEach { tab ->
                                val selected = effectiveTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { vm.selectTab(tab) },
                                    icon = { Icon(tabIcon(tab, selected), contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("rail_tab_${tab.key}")
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = AppRoute.WorkspaceHome,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        // Web parity: standalone screens slide up from the bottom (340ms in, 220ms out);
                        // the screen underneath stays put and tab switches use expressive transition.
                        enterTransition = {
                            slideInVertically(tween(340, easing = ScreenEnterEasing), initialOffsetY = { it }) +
                                fadeIn(tween(340, easing = ScreenEnterEasing), initialAlpha = 0.6f)
                        },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = {
                            slideOutVertically(tween(220, easing = ScreenLeaveEasing), targetOffsetY = { it }) +
                                fadeOut(tween(220, easing = ScreenLeaveEasing))
                        }
                    ) {
                        composable<AppRoute.WorkspaceHome> {
                            AnimatedContent(
                                targetState = effectiveTab,
                                transitionSpec = {
                                    (fadeIn(tween(220)) + slideInVertically(tween(240, easing = ScreenEnterEasing)) { it / 28 })
                                        .togetherWith(fadeOut(tween(140)))
                                },
                                label = "main_tab_transition"
                            ) { tab ->
                                when (tab) {
                                    MainTab.OVERVIEW -> OverviewScreen(snapshot, vm, { navigateTo(AppRoute.Approvals) }, { navigateTo(AppRoute.DailyLedger) })
                                    MainTab.PRODUCTION -> ProductionScreen(snapshot, todayIso, vm) { navigateTo(AppRoute.StockTransfer) }
                                    MainTab.SALES -> SalesScreen(
                                        snapshot = snapshot,
                                        todayIso = todayIso,
                                        vm = vm,
                                        onOpenCustomerDetail = { cust -> navigateTo(AppRoute.CustomerDetail(cust, isRepClient = false)) },
                                        onEditCustomer = { cust -> navigateTo(AppRoute.EditCustomer(cust, isRepClient = false)) }
                                    )
                                    MainTab.CALCULATOR -> CalculatorScreen(snapshot, todayIso, vm)
                                    MainTab.FACTORY -> FactoryScreen(snapshot, todayIso, vm, { navigateTo(AppRoute.RawMaterialPurchase) }, { navigateTo(AppRoute.FormulaStore) })
                                    MainTab.PAYMENTS -> PaymentsScreen(snapshot, todayIso, vm, { navigateTo(AppRoute.AddEntity) }, { navigateTo(AppRoute.PaymentTransfer) }, { ent -> navigateTo(AppRoute.EntityDetail(ent)) }, { name -> navigateTo(AppRoute.ExpenseDetail(name)) })
                                    MainTab.REP_SALES -> RepSalesScreen(
                                        snapshot = snapshot,
                                        todayIso = todayIso,
                                        vm = vm,
                                        onOpenCustomer = { cust -> navigateTo(AppRoute.RepCustomerManagement(cust)) },
                                        onEditCustomer = { cust -> navigateTo(AppRoute.EditCustomer(cust, isRepClient = true)) }
                                    )
                                }
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
                            BatchFixScreen(snapshot, arg.formulaId, vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.RawMaterialPurchase> {
                            RawMaterialScreen(snapshot, todayIso, vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.AddEntity> {
                            AddEntityScreen(vm) { navController.popBackStack() }
                        }
                        composable<AppRoute.EditCustomer> { entry ->
                            val arg = entry.toRoute<AppRoute.EditCustomer>()
                            EditCustomerScreen(snapshot, arg.customerName, arg.isRepClient, vm) { navController.popBackStack() }
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
                                isRepClient = arg.isRepClient,
                                vm = vm,
                                onBack = { navController.popBackStack() },
                                onEditCustomer = { navigateTo(AppRoute.EditCustomer(arg.customerName, isRepClient = arg.isRepClient)) }
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
                                onEdit = { navigateTo(AppRoute.EditCustomer(arg.customerName, isRepClient = true)) },
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

    if (showSignOutConfirm) {
        SarimConfirmDialog(
            title = "Sign Out?",
            message = "You will be signed out and the local data on this device will be erased.\nYour data stays safe in the cloud and returns when you sign in again.",
            confirmText = "Sign Out",
            cancelText = "Cancel",
            danger = true,
            onConfirm = {
                showSignOutConfirm = false
                onSignOut()
            },
            onDismiss = { showSignOutConfirm = false }
        )
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
    val shape = RoundedCornerShape(999.dp)
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            if (accents.isDark) Color(0xFF0A2218) else accents.softPillFill
        } else {
            Color.Transparent
        },
        animationSpec = SarimMotion.ColorSpring,
        label = "drawer_row_bg"
    )
    val resolvedTint = if (selected) accents.emerald else iconTint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = resolvedTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            fontFamily = JakartaFont,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.5.sp,
            letterSpacing = 0.6.sp,
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
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("$badgeCount", style = MaterialTheme.typography.labelSmall, color = accents.gold)
            }
        }
    }
}

private fun tabIcon(tab: MainTab, selected: Boolean): ImageVector = when (tab) {
    MainTab.OVERVIEW -> if (selected) Icons.Rounded.GridView else Icons.Outlined.GridView
    MainTab.PRODUCTION -> if (selected) Icons.Rounded.Inventory2 else Icons.Outlined.Inventory2
    MainTab.SALES -> if (selected) Icons.Rounded.ReceiptLong else Icons.Outlined.ReceiptLong
    MainTab.CALCULATOR -> if (selected) Icons.Rounded.Calculate else Icons.Outlined.Calculate
    MainTab.FACTORY -> if (selected) Icons.Rounded.Factory else Icons.Outlined.Factory
    MainTab.PAYMENTS -> if (selected) Icons.Rounded.CreditCard else Icons.Outlined.CreditCard
    MainTab.REP_SALES -> if (selected) Icons.Rounded.TrendingUp else Icons.Outlined.TrendingUp
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
