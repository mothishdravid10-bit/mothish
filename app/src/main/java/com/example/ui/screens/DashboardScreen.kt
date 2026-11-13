package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AssistantQueryResult
import com.example.ai.ZenoIntent
import com.example.data.analytics.*
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun DashboardScreen(
    accounts: List<Account>,
    customers: List<Party>,
    suppliers: List<Party>,
    products: List<Product>,
    invoices: List<SalesInvoice>,
    bills: List<PurchaseBill>,
    vouchers: List<PaymentVoucher>,
    reminders: List<PaymentReminder>,
    onNavigate: (String) -> Unit,
    onVoiceClick: () -> Unit,
    latestAssistantResult: AssistantQueryResult? = null,
    onDismissAssistantResult: () -> Unit = {}
) {
    val analyticsEngine = remember { FinancialAnalyticsEngine() }

    // Filters: Time Filter & Party/Category filter
    var selectedTimeFilter by remember { mutableStateOf(AnalyticsTimeFilter.ALL_TIME) }
    var selectedPartyFilter by remember { mutableStateOf<String?>(null) } // null = All, otherwise party name

    // Filtered data based on Party selection
    val filteredInvoices = remember(invoices, selectedPartyFilter) {
        if (selectedPartyFilter == null) invoices else invoices.filter { it.customerName == selectedPartyFilter }
    }
    val filteredBills = remember(bills, selectedPartyFilter) {
        if (selectedPartyFilter == null) bills else bills.filter { it.supplierName == selectedPartyFilter }
    }

    // Backend Analytics calculations
    val summary = remember(accounts, customers, suppliers, products, filteredInvoices, filteredBills, selectedTimeFilter) {
        analyticsEngine.getDashboardSummary(accounts, customers, suppliers, products, filteredInvoices, filteredBills, selectedTimeFilter)
    }

    // 8 Database-Driven Analytics Datasets
    // 1. Income vs Expenses
    val incomeVsExpenseSlices = remember(accounts, filteredInvoices, filteredBills) {
        analyticsEngine.getIncomeExpenseAnalysis(accounts, filteredInvoices, filteredBills)
    }
    // 2. Expense Breakdown
    val expenseBreakdownSlices = remember(accounts, filteredBills) {
        analyticsEngine.getExpenseBreakdown(accounts, filteredBills)
    }
    // 3. Sales vs Purchases
    val salesVsPurchaseSlices = remember(filteredInvoices, filteredBills) {
        analyticsEngine.getSalesPurchaseAnalysis(filteredInvoices, filteredBills)
    }
    // 4. Transaction Distribution
    val transactionDistributions = remember(filteredInvoices, filteredBills, vouchers) {
        analyticsEngine.getTransactionDistribution(filteredInvoices, filteredBills, vouchers, emptyList())
    }
    // 5. Monthly Income vs Expenses (Bar Comparison)
    val monthlyAnalysisPoints = remember(filteredInvoices, filteredBills) {
        analyticsEngine.getMonthlyFinancialAnalysis(filteredInvoices, filteredBills)
    }
    // 6. Monthly Profit Trend (Line Chart)
    val profitTrendPoints = remember(monthlyAnalysisPoints) {
        monthlyAnalysisPoints
    }
    // 7. Cash Flow Analysis
    val cashFlowPoints = remember(vouchers, filteredInvoices, filteredBills) {
        analyticsEngine.getCashFlowAnalysis(vouchers, filteredInvoices, filteredBills)
    }
    // 8. Daily / Weekly Transaction Analysis
    val dailyTransactionPoints = remember(filteredInvoices, filteredBills) {
        analyticsEngine.getDailyFinancialAnalysis(filteredInvoices, filteredBills)
    }

    // Outstanding Details
    val customerOutstandingList = remember(customers) {
        analyticsEngine.getCustomerOutstanding(customers)
    }
    val supplierOutstandingList = remember(suppliers) {
        analyticsEngine.getSupplierOutstanding(suppliers)
    }

    // AI Financial Insights
    val insights = remember(summary, filteredInvoices, filteredBills, customers, suppliers) {
        analyticsEngine.getFinancialInsights(summary, filteredInvoices, filteredBills, customers, suppliers)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero AI Banner & ZENO Assistant CTA
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_hero_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Emerald600)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ZENO AI FINANCIAL INTELLIGENCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Emerald600,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enterprise Financial Analytics",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Indian Accounting • Double-Entry • Live Database",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B)
                            )
                        }

                        Button(
                            onClick = onVoiceClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("hero_voice_btn")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Voice AI", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFFE2E8F0))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Spotlight: Net Profit & Net Margin
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Net Operating Profit / (Loss)",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = formatCurrency(summary.netProfit),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (summary.netProfit >= 0) Emerald600 else ErrorRed
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            StatusBadge(
                                text = if (summary.netProfit >= 0) "PROFITABLE" else "DEFICIT",
                                isSuccess = summary.netProfit >= 0,
                                isWarning = summary.netProfit < 0
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Cash: ${formatCurrency(summary.cashBalance + summary.bankBalance)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        if (latestAssistantResult != null && latestAssistantResult.intent != ZenoIntent.CREATE_TRANSACTION) {
            item {
                AssistantQueryResultCard(
                    result = latestAssistantResult,
                    modifier = Modifier.testTag("dashboard_assistant_results_card"),
                    onDismissClick = onDismissAssistantResult
                )
            }
        }

        // Period & Entity Filters Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Time Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnalyticsTimeFilter.entries.forEach { f ->
                        val isSelected = selectedTimeFilter == f
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedTimeFilter = f },
                            label = {
                                Text(
                                    text = when (f) {
                                        AnalyticsTimeFilter.ALL_TIME -> "All Time"
                                        AnalyticsTimeFilter.TODAY -> "Today"
                                        AnalyticsTimeFilter.YESTERDAY -> "Yesterday"
                                        AnalyticsTimeFilter.THIS_WEEK -> "This Week"
                                        AnalyticsTimeFilter.THIS_MONTH -> "This Month"
                                        AnalyticsTimeFilter.THIS_QUARTER -> "This Quarter"
                                        AnalyticsTimeFilter.THIS_YEAR -> "FY 2024-25"
                                    },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFECFDF5),
                                selectedLabelColor = Emerald600,
                                containerColor = Color.White,
                                labelColor = Color(0xFF64748B)
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = if (isSelected) Emerald600 else Color(0xFFCBD5E1)
                            )
                        )
                    }
                }

                // Party Filter if customers or suppliers exist
                if (customers.isNotEmpty() || suppliers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Party:", style = MaterialTheme.typography.labelSmall, color = Emerald400)
                        FilterChip(
                            selected = selectedPartyFilter == null,
                            onClick = { selectedPartyFilter = null },
                            label = { Text("All Parties", fontSize = 11.sp) }
                        )
                        (customers + suppliers).take(8).forEach { p ->
                            FilterChip(
                                selected = selectedPartyFilter == p.name,
                                onClick = { selectedPartyFilter = if (selectedPartyFilter == p.name) null else p.name },
                                label = { Text(p.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        // Section: 8 Core Financial Metrics Cards
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "CORE FINANCIAL METRICS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Emerald400,
                    letterSpacing = 1.sp
                )

                // Row 1: Total Income & Total Expenses
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZenoMetricCard(
                        title = "Total Income",
                        value = formatCurrency(summary.totalIncome),
                        icon = Icons.Default.TrendingUp,
                        iconColor = Emerald500,
                        subtitle = "Recognized Revenue",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("sales") }
                    )
                    ZenoMetricCard(
                        title = "Total Expenses",
                        value = formatCurrency(summary.totalExpenses),
                        icon = Icons.Default.TrendingDown,
                        iconColor = Amber500,
                        subtitle = "Operating Costs",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("purchases") }
                    )
                }

                // Row 2: Total Sales & Total Purchases
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZenoMetricCard(
                        title = "Total Sales",
                        value = formatCurrency(summary.totalSales),
                        icon = Icons.Default.Receipt,
                        iconColor = Emerald400,
                        subtitle = "${filteredInvoices.size} Invoices",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("sales") }
                    )
                    ZenoMetricCard(
                        title = "Total Purchases",
                        value = formatCurrency(summary.totalPurchases),
                        icon = Icons.Default.ShoppingCart,
                        iconColor = InfoBlue,
                        subtitle = "${filteredBills.size} Vendor Bills",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("purchases") }
                    )
                }

                // Row 3: Cash Balance & Customer Outstanding
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZenoMetricCard(
                        title = "Cash & Bank Balance",
                        value = formatCurrency(summary.cashBalance + summary.bankBalance),
                        icon = Icons.Default.AccountBalance,
                        iconColor = Emerald500,
                        subtitle = "Cash: ${formatCurrency(summary.cashBalance)}",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("ledger") }
                    )
                    ZenoMetricCard(
                        title = "Customer Outstanding",
                        value = formatCurrency(summary.customerOutstanding),
                        icon = Icons.Default.People,
                        iconColor = Amber500,
                        subtitle = "Debtors Due",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("customers") }
                    )
                }

                // Row 4: Supplier Outstanding & GST Payable
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZenoMetricCard(
                        title = "Supplier Outstanding",
                        value = formatCurrency(summary.supplierOutstanding),
                        icon = Icons.Default.Business,
                        iconColor = ErrorRed,
                        subtitle = "Creditors Due",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("suppliers") }
                    )
                    ZenoMetricCard(
                        title = "Net GST Payable",
                        value = formatCurrency(summary.netGstPayable),
                        icon = Icons.Default.Gavel,
                        iconColor = PurpleAccent,
                        subtitle = "ITC Adjusted",
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate("gst") }
                    )
                }
            }
        }

        // Section: AI Financial Insights & Actionable Guidance
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Amber500, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI Financial Insights & Health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                        StatusBadge(text = "${insights.size} INSIGHTS", isSuccess = true)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    insights.forEach { ins ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when (ins.type) {
                                    InsightType.POSITIVE -> Color(0xFFECFDF5)
                                    InsightType.WARNING -> Color(0xFFFFFBEB)
                                    InsightType.NEUTRAL -> Color(0xFFEFF6FF)
                                }
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when (ins.type) {
                                    InsightType.POSITIVE -> Color(0xFFA7F3D0)
                                    InsightType.WARNING -> Color(0xFFFDE68A)
                                    InsightType.NEUTRAL -> Color(0xFFBFDBFE)
                                }
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = ins.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when (ins.type) {
                                        InsightType.POSITIVE -> Color(0xFF065F46)
                                        InsightType.WARNING -> Color(0xFF92400E)
                                        InsightType.NEUTRAL -> Color(0xFF1E40AF)
                                    }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(ins.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF334155))
                                if (ins.actionableStep != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Action: ${ins.actionableStep}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF047857), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: 8 Interactive Financial Charts
        item {
            Text(
                text = "INTERACTIVE FINANCIAL CHARTS & VISUALS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Emerald400,
                letterSpacing = 1.sp
            )
        }

        // Chart 1: Income vs Expenses Pie Chart
        item {
            InteractiveDonutChart(
                title = "1. Income vs Expenses Distribution",
                slices = incomeVsExpenseSlices,
                isDonut = false,
                onCategoryClick = { cat ->
                    if (cat == "Total Income") onNavigate("sales") else onNavigate("purchases")
                }
            )
        }

        // Chart 2: Expense Breakdown Donut Chart
        item {
            InteractiveDonutChart(
                title = "2. Expense Breakdown by Category",
                slices = expenseBreakdownSlices,
                isDonut = true,
                emptyMessage = "No expenses recorded yet. Post a purchase or voucher to view breakdown.",
                onCategoryClick = { onNavigate("purchases") }
            )
        }

        // Chart 3: Sales vs Purchases Comparison
        item {
            InteractiveDonutChart(
                title = "3. Sales vs Purchases Ratio",
                slices = salesVsPurchaseSlices,
                isDonut = true,
                onCategoryClick = { cat ->
                    if (cat == "Sales Revenue") onNavigate("sales") else onNavigate("purchases")
                }
            )
        }

        // Chart 4: Transaction Distribution by Module
        item {
            InteractiveDonutChart(
                title = "4. Transaction Distribution by Module",
                slices = transactionDistributions.map {
                    CategorySlice(it.type, it.totalAmount, it.percentage, it.count)
                },
                isDonut = true,
                onCategoryClick = { type ->
                    when {
                        "Sales" in type -> onNavigate("sales")
                        "Purchase" in type -> onNavigate("purchases")
                        "Receipt" in type -> onNavigate("receipts")
                        "Payment" in type -> onNavigate("payments")
                        else -> onNavigate("journal")
                    }
                }
            )
        }

        // Chart 5: Monthly Income vs Expenses Dual Bar Comparison
        item {
            InteractiveBarChart(
                title = "5. Monthly Income vs Expenses",
                points = monthlyAnalysisPoints,
                series1Label = "Income",
                series2Label = "Expenses",
                series1Color = Emerald500,
                series2Color = Amber500
            )
        }

        // Chart 6: Monthly Net Profit Trend Line
        item {
            InteractiveTrendLineChart(
                title = "6. Monthly Net Profit Trend",
                points = profitTrendPoints,
                valueExtractor = { it.netProfit },
                lineColor = Emerald400
            )
        }

        // Chart 7: Cash Flow Inflow vs Outflow
        item {
            InteractiveBarChart(
                title = "7. Cash Flow Inflow vs Outflow",
                points = cashFlowPoints,
                series1Label = "Inflow",
                series2Label = "Outflow",
                series1Color = InfoBlue,
                series2Color = Color(0xFFF97316)
            )
        }

        // Chart 8: Daily Transaction Trend
        item {
            InteractiveTrendLineChart(
                title = "8. Daily Transaction Volume",
                points = dailyTransactionPoints,
                valueExtractor = { it.sales + it.purchase },
                lineColor = PurpleAccent
            )
        }

        // Customer & Supplier Outstanding Summaries
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Customer & Supplier Outstanding Receivables",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (customerOutstandingList.isEmpty() && supplierOutstandingList.isEmpty()) {
                        Text(
                            text = "All customer accounts and vendor payables are fully balanced with zero outstanding dues.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    } else {
                        customerOutstandingList.take(3).forEach { d ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(d.partyName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                    Text("Customer Receivable", style = MaterialTheme.typography.labelSmall, color = Emerald600)
                                }
                                Text(formatCurrency(d.outstandingAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Emerald600)
                            }
                        }
                        supplierOutstandingList.take(3).forEach { s ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(s.partyName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                    Text("Supplier Payable", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                                }
                                Text(formatCurrency(s.outstandingAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = ErrorRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
