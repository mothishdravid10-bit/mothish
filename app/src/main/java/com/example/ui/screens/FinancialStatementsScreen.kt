package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlin.math.abs

@Composable
fun FinancialStatementsScreen(
    accounts: List<Account>,
    invoices: List<SalesInvoice>,
    bills: List<PurchaseBill>,
    settings: CompanySettings
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: P&L, 1: Balance Sheet, 2: Cash Flow

    // P&L Metrics
    val totalRevenue = remember(accounts) {
        accounts.filter { it.category == AccountCategory.INCOME }.sumOf { it.currentBalance }
    }
    val costOfGoods = remember(accounts) {
        accounts.filter { it.code == "5010" }.sumOf { it.currentBalance }
    }
    val grossProfit = totalRevenue - costOfGoods

    val operatingExpenses = remember(accounts) {
        accounts.filter { it.category == AccountCategory.EXPENSE && it.code != "5010" }.sumOf { it.currentBalance }
    }
    val netProfit = grossProfit - operatingExpenses

    // Balance Sheet Metrics
    val totalAssets = remember(accounts) {
        accounts.filter { it.category == AccountCategory.ASSET }.sumOf { it.currentBalance }
    }
    val totalLiabilities = remember(accounts) {
        accounts.filter { it.category == AccountCategory.LIABILITY }.sumOf { it.currentBalance }
    }
    val ownerEquity = remember(accounts) {
        accounts.filter { it.category == AccountCategory.EQUITY }.sumOf { it.currentBalance }
    }
    // In double entry, Net Profit is added to Equity / Retained Earnings
    val totalLiabilitiesAndEquity = totalLiabilities + ownerEquity + netProfit
    val balanceDifference = abs(totalAssets - totalLiabilitiesAndEquity)
    val isBsBalanced = balanceDifference < 1.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        // Tab Selector
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SurfaceWhite,
            contentColor = Emerald400,
            divider = {}
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Profit & Loss", fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Balance Sheet", fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Cash Flow", fontWeight = FontWeight.Bold) })
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (selectedTab) {
            0 -> {
                // Profit & Loss Statement
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight), shape = RoundedCornerShape(14.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("TRADING & PROFIT/LOSS ACCOUNT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Period: Financial Year ${settings.financialYear}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = BorderLight)
                                Spacer(modifier = Modifier.height(12.dp))

                                // Operating Revenue
                                Text("A. REVENUE FROM OPERATIONS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                                accounts.filter { it.category == AccountCategory.INCOME }.forEach { acc ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(acc.name, style = MaterialTheme.typography.bodySmall)
                                        Text(formatCurrency(acc.currentBalance), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Revenue (A):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(formatCurrency(totalRevenue), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = BorderLight)
                                Spacer(modifier = Modifier.height(10.dp))

                                // Cost of Goods Sold
                                Text("B. COST OF GOODS SOLD", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Amber500)
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Cost of Material Purchases:", style = MaterialTheme.typography.bodySmall)
                                    Text(formatCurrency(costOfGoods), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("GROSS PROFIT (A - B):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(formatCurrency(grossProfit), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = BorderLight)
                                Spacer(modifier = Modifier.height(10.dp))

                                // Operating Expenses
                                Text("C. OPERATING & ADMINISTRATIVE EXPENSES", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ErrorRed)
                                accounts.filter { it.category == AccountCategory.EXPENSE && it.code != "5010" }.forEach { acc ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(acc.name, style = MaterialTheme.typography.bodySmall)
                                        Text(formatCurrency(acc.currentBalance), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Operating Expenses (C):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(formatCurrency(operatingExpenses), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = ErrorRed)
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = BorderLight)
                                Spacer(modifier = Modifier.height(12.dp))

                                // Net Profit
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Navy900, RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("NET BUSINESS PROFIT:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(formatCurrency(netProfit), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = if (netProfit >= 0) Emerald400 else ErrorRed)
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                // Balance Sheet
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight), shape = RoundedCornerShape(14.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("BALANCE SHEET", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("As of ${formatDate(System.currentTimeMillis())}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    StatusBadge(text = if (isBsBalanced) "BALANCED (A = L + E)" else "UNBALANCED", isSuccess = isBsBalanced)
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = BorderLight)
                                Spacer(modifier = Modifier.height(12.dp))

                                // Assets Section
                                Text("I. ASSETS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                                accounts.filter { it.category == AccountCategory.ASSET }.forEach { acc ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${acc.name} (${acc.subcategory}):", style = MaterialTheme.typography.bodySmall)
                                        Text(formatCurrency(acc.currentBalance), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TOTAL ASSETS (A):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(formatCurrency(totalAssets), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = BorderLight)
                                Spacer(modifier = Modifier.height(12.dp))

                                // Liabilities Section
                                Text("II. LIABILITIES", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Amber500)
                                accounts.filter { it.category == AccountCategory.LIABILITY }.forEach { acc ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${acc.name} (${acc.subcategory}):", style = MaterialTheme.typography.bodySmall)
                                        Text(formatCurrency(acc.currentBalance), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Liabilities (L):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(formatCurrency(totalLiabilities), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = BorderLight)
                                Spacer(modifier = Modifier.height(12.dp))

                                // Equity Section
                                Text("III. PROPRIETOR EQUITY & RESERVES", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = InfoBlue)
                                accounts.filter { it.category == AccountCategory.EQUITY }.forEach { acc ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(acc.name, style = MaterialTheme.typography.bodySmall)
                                        Text(formatCurrency(acc.currentBalance), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Current Period Net Profit (Transferred):", style = MaterialTheme.typography.bodySmall)
                                    Text(formatCurrency(netProfit), style = MaterialTheme.typography.bodySmall, color = Emerald400)
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("TOTAL LIABILITIES & EQUITY (L + E):", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(formatCurrency(totalLiabilitiesAndEquity), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                // Cash Flow Statement
                val cashAccounts = accounts.filter { it.code in listOf("1010", "1020", "1030") }
                val closingCash = cashAccounts.sumOf { it.currentBalance }
                val openingCash = cashAccounts.sumOf { it.openingBalance }
                val netCashChange = closingCash - openingCash

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight), shape = RoundedCornerShape(14.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("CASH FLOW STATEMENT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Operating, Investing & Financing Cash Summary", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = BorderLight)
                                Spacer(modifier = Modifier.height(12.dp))

                                Text("1. Operating Activities:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Cash received from sales & trade collections:", style = MaterialTheme.typography.bodySmall)
                                    Text(formatCurrency(totalRevenue), style = MaterialTheme.typography.bodySmall)
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Cash disbursed for expenses & operations:", style = MaterialTheme.typography.bodySmall)
                                    Text("-${formatCurrency(operatingExpenses)}", style = MaterialTheme.typography.bodySmall, color = ErrorRed)
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = BorderLight)
                                Spacer(modifier = Modifier.height(10.dp))

                                Text("2. Summary Cash Positions:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Opening Liquid Cash & Bank Position:", style = MaterialTheme.typography.bodySmall)
                                    Text(formatCurrency(openingCash), style = MaterialTheme.typography.bodySmall)
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Net Change in Liquid Cash:", style = MaterialTheme.typography.bodySmall)
                                    Text(formatCurrency(netCashChange), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (netCashChange >= 0) Emerald400 else ErrorRed)
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("CLOSING CASH & BANK EQUIVALENTS:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text(formatCurrency(closingCash), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Emerald400)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
