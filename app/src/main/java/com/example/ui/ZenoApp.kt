package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.*
import com.example.ui.components.ZenoHeaderBar
import com.example.ui.screens.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class ScreenDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val group: String
) {
    DASHBOARD("dashboard", "Dashboard", Icons.Default.Dashboard, "Main"),
    ACCOUNTS("accounts", "Accounts", Icons.Default.AccountTree, "Master"),
    CUSTOMERS("customers", "Customers", Icons.Default.People, "Master"),
    SUPPLIERS("suppliers", "Suppliers", Icons.Default.Business, "Master"),
    SALES("sales", "Sales", Icons.Default.Receipt, "Transactions"),
    PURCHASES("purchases", "Purchases", Icons.Default.ShoppingCart, "Transactions"),
    RECEIPTS("receipts", "Receipts", Icons.Default.CallReceived, "Transactions"),
    PAYMENTS("payments", "Payments", Icons.Default.CallMade, "Transactions"),
    JOURNAL("journal", "Journal Entries", Icons.Default.MenuBook, "Transactions"),
    LEDGER("ledger", "Ledger", Icons.Default.Book, "Financial"),
    TRIAL_BALANCE("trial_balance", "Trial Balance", Icons.Default.Scale, "Financial"),
    GST_MANAGEMENT("gst", "GST Management", Icons.Default.Gavel, "Taxation"),
    INVENTORY("inventory", "Inventory", Icons.Default.Inventory2, "Master"),
    PROFIT_LOSS("pnl", "Profit & Loss", Icons.Default.TrendingUp, "Statements"),
    BALANCE_SHEET("balance_sheet", "Balance Sheet", Icons.Default.Assessment, "Statements"),
    CASH_FLOW("cash_flow", "Cash Flow", Icons.Default.AccountBalanceWallet, "Statements"),
    REMINDERS("reminders", "Reminders", Icons.Default.NotificationsActive, "Management"),
    AI_ASSISTANT("ai_assistant", "ZENO AI", Icons.Default.SmartToy, "Intelligence"),
    PROFILE("profile", "My Profile", Icons.Default.AccountCircle, "Configuration"),
    SETTINGS("settings", "Settings", Icons.Default.Settings, "Configuration")
}

@Composable
fun ZenoApp(
    viewModel: ZenoViewModel,
    modifier: Modifier = Modifier
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    // Gate screen with Login / Signup if user is not authenticated
    if (!isLoggedIn || currentUser == null) {
        AuthScreen(
            emailVerificationService = viewModel.emailVerificationService,
            onLogin = { email, pwd, onError ->
                viewModel.login(email, pwd, onError)
            },
            onSignup = { name, email, pwd, cName, gstin, sCode, sName, onError ->
                viewModel.signup(name, email, pwd, cName, gstin, sCode, sName, onError)
            },
            onGoogleSignIn = { onError ->
                viewModel.loginWithGoogle(onError)
            }
        )
        return
    }

    val activeUser = currentUser!!
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val salesInvoices by viewModel.salesInvoices.collectAsStateWithLifecycle()
    val purchaseBills by viewModel.purchaseBills.collectAsStateWithLifecycle()
    val journalEntries by viewModel.journalEntries.collectAsStateWithLifecycle()
    val paymentVouchers by viewModel.paymentVouchers.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val settings by viewModel.companySettings.collectAsStateWithLifecycle()
    val latestAssistantResult by viewModel.zenoAiEngine.latestQueryResult.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf(ScreenDestination.DASHBOARD) }
    var voucherPartyToPreload by remember { mutableStateOf<Party?>(null) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Handle back button: if not on Dashboard, return to Dashboard
    BackHandler(enabled = currentScreen != ScreenDestination.DASHBOARD) {
        currentScreen = ScreenDestination.DASHBOARD
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.White,
                drawerContentColor = Color(0xFF0F172A),
                modifier = Modifier.width(310.dp)
            ) {
                // Drawer Brand Header with active User & Company details
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9))
                        .padding(20.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Emerald600, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Z", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 24.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("ZENO AI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                Text("Your Personal AI Accountant", style = MaterialTheme.typography.labelSmall, color = Emerald600)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = activeUser.companyName.ifBlank { settings.companyName },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1
                                )
                                Text(
                                    text = "${activeUser.name} • ${activeUser.email}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Emerald600,
                                    maxLines = 1
                                )
                                if (settings.gstin.isNotBlank()) {
                                    Text(
                                        text = "GSTIN: ${settings.gstin}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Menu Navigation Items
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val grouped = ScreenDestination.entries.groupBy { it.group }
                    grouped.forEach { (groupName, items) ->
                        item {
                            Text(
                                text = groupName.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Emerald600,
                                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                                letterSpacing = 1.sp
                            )
                        }
                        items(items, key = { it.route }) { dest ->
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        dest.icon,
                                        contentDescription = null,
                                        tint = if (currentScreen == dest) Emerald600 else Color(0xFF64748B)
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.title,
                                        fontWeight = if (currentScreen == dest) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                selected = currentScreen == dest,
                                onClick = {
                                    currentScreen = dest
                                    scope.launch { drawerState.close() }
                                },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Emerald500.copy(alpha = 0.12f),
                                    selectedTextColor = Emerald600,
                                    unselectedTextColor = Color(0xFF1E293B),
                                    unselectedIconColor = Color(0xFF64748B)
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0))

                // Drawer Logout Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                viewModel.logout()
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed),
                        modifier = Modifier.testTag("drawer_logout_btn")
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Out (${activeUser.email.take(15)}...)")
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                ZenoHeaderBar(
                    title = currentScreen.title,
                    subtitle = if (currentScreen == ScreenDestination.DASHBOARD) activeUser.companyName else null,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onVoiceClick = { currentScreen = ScreenDestination.AI_ASSISTANT },
                    onProfileClick = { currentScreen = ScreenDestination.PROFILE },
                    onLogoutClick = { viewModel.logout() }
                )
            },
            bottomBar = {
                // Bottom bar for primary destinations
                NavigationBar(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0F172A),
                    tonalElevation = 6.dp
                ) {
                    val bottomItems = listOf(
                        ScreenDestination.DASHBOARD,
                        ScreenDestination.SALES,
                        ScreenDestination.AI_ASSISTANT,
                        ScreenDestination.PURCHASES,
                        ScreenDestination.GST_MANAGEMENT
                    )
                    bottomItems.forEach { dest ->
                        NavigationBarItem(
                            selected = currentScreen == dest,
                            onClick = { currentScreen = dest },
                            icon = {
                                if (dest == ScreenDestination.AI_ASSISTANT) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Emerald600, RoundedCornerShape(18.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(dest.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                } else {
                                    Icon(dest.icon, contentDescription = null)
                                }
                            },
                            label = { Text(dest.title, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Emerald600,
                                selectedTextColor = Emerald600,
                                unselectedIconColor = Color(0xFF64748B),
                                unselectedTextColor = Color(0xFF64748B),
                                indicatorColor = Color(0xFFECFDF5)
                            )
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = modifier
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentScreen) {
                    ScreenDestination.DASHBOARD -> DashboardScreen(
                        accounts = accounts,
                        customers = customers,
                        suppliers = suppliers,
                        products = products,
                        invoices = salesInvoices,
                        bills = purchaseBills,
                        vouchers = paymentVouchers,
                        reminders = reminders,
                        onNavigate = { route ->
                            val match = ScreenDestination.entries.firstOrNull { it.route == route }
                            if (match != null) currentScreen = match
                        },
                        onVoiceClick = { currentScreen = ScreenDestination.AI_ASSISTANT },
                        latestAssistantResult = latestAssistantResult,
                        onDismissAssistantResult = { viewModel.zenoAiEngine.clearQueryResults() }
                    )

                    ScreenDestination.ACCOUNTS -> AccountsScreen(
                        accounts = accounts,
                        onAddAccount = { viewModel.addAccount(it) }
                    )

                    ScreenDestination.CUSTOMERS -> PartiesScreen(
                        parties = customers,
                        initialType = PartyType.CUSTOMER,
                        onAddParty = { viewModel.addParty(it) },
                        onNavigateToVoucher = { p ->
                            voucherPartyToPreload = p
                            currentScreen = ScreenDestination.RECEIPTS
                        }
                    )

                    ScreenDestination.SUPPLIERS -> PartiesScreen(
                        parties = suppliers,
                        initialType = PartyType.SUPPLIER,
                        onAddParty = { viewModel.addParty(it) },
                        onNavigateToVoucher = { p ->
                            voucherPartyToPreload = p
                            currentScreen = ScreenDestination.PAYMENTS
                        }
                    )

                    ScreenDestination.SALES -> SalesScreen(
                        invoices = salesInvoices,
                        customers = customers,
                        products = products,
                        settings = settings,
                        onCreateInvoice = { inv, items -> viewModel.createSalesInvoice(inv, items) }
                    )

                    ScreenDestination.PURCHASES -> PurchasesScreen(
                        bills = purchaseBills,
                        suppliers = suppliers,
                        products = products,
                        settings = settings,
                        onCreateBill = { bill, items -> viewModel.createPurchaseBill(bill, items) }
                    )

                    ScreenDestination.RECEIPTS -> VouchersScreen(
                        vouchers = paymentVouchers,
                        customers = customers,
                        suppliers = suppliers,
                        accounts = accounts,
                        settings = settings,
                        initialParty = voucherPartyToPreload,
                        onRecordVoucher = {
                            viewModel.recordPaymentVoucher(it)
                            voucherPartyToPreload = null
                        }
                    )

                    ScreenDestination.PAYMENTS -> VouchersScreen(
                        vouchers = paymentVouchers,
                        customers = customers,
                        suppliers = suppliers,
                        accounts = accounts,
                        settings = settings,
                        initialParty = voucherPartyToPreload,
                        onRecordVoucher = {
                            viewModel.recordPaymentVoucher(it)
                            voucherPartyToPreload = null
                        }
                    )

                    ScreenDestination.JOURNAL -> JournalScreen(
                        journalEntries = journalEntries,
                        accounts = accounts,
                        onCreateJournalEntry = { entry, lines -> viewModel.createManualJournalEntry(entry, lines) }
                    )

                    ScreenDestination.LEDGER -> LedgerScreen(
                        accounts = accounts,
                        journalEntries = journalEntries
                    )

                    ScreenDestination.TRIAL_BALANCE -> TrialBalanceScreen(
                        accounts = accounts
                    )

                    ScreenDestination.GST_MANAGEMENT -> GstScreen(
                        invoices = salesInvoices,
                        bills = purchaseBills,
                        settings = settings
                    )

                    ScreenDestination.INVENTORY -> InventoryScreen(
                        products = products,
                        onAddProduct = { viewModel.addProduct(it) }
                    )

                    ScreenDestination.PROFIT_LOSS -> FinancialStatementsScreen(
                        accounts = accounts,
                        invoices = salesInvoices,
                        bills = purchaseBills,
                        settings = settings
                    )

                    ScreenDestination.BALANCE_SHEET -> FinancialStatementsScreen(
                        accounts = accounts,
                        invoices = salesInvoices,
                        bills = purchaseBills,
                        settings = settings
                    )

                    ScreenDestination.CASH_FLOW -> FinancialStatementsScreen(
                        accounts = accounts,
                        invoices = salesInvoices,
                        bills = purchaseBills,
                        settings = settings
                    )

                    ScreenDestination.REMINDERS -> RemindersScreen(
                        reminders = reminders,
                        customers = customers,
                        suppliers = suppliers,
                        onAddReminder = { viewModel.addReminder(it) },
                        onSettleReminder = { viewModel.updateReminder(it) }
                    )

                    ScreenDestination.AI_ASSISTANT -> ZenoAiScreen(
                        engine = viewModel.zenoAiEngine,
                        currentUserId = activeUser.id,
                        onNavigate = { route ->
                            val match = ScreenDestination.entries.firstOrNull { it.route == route }
                            if (match != null) currentScreen = match
                        }
                    )

                    ScreenDestination.PROFILE -> ProfileScreen(
                        user = activeUser,
                        settings = settings,
                        onBackClick = { currentScreen = ScreenDestination.DASHBOARD },
                        onSaveProfile = { cName, gstin, sCode, sName ->
                            viewModel.updateUserProfile(cName, gstin, sCode, sName)
                        },
                        onLogoutClick = { viewModel.logout() }
                    )

                    ScreenDestination.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onSaveSettings = { viewModel.saveSettings(it) },
                        onResetDemoData = { viewModel.reloadInitialData() }
                    )
                }
            }
        }
    }
}
