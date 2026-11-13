package com.example.ai

import com.example.data.model.*
import com.example.ui.components.formatCurrency
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

data class FinancialInsight(
    val id: String,
    val title: String,
    val description: String,
    val category: InsightCategory,
    val impact: InsightImpact,
    val metricValue: String? = null
)

enum class InsightCategory {
    EXPENSE_TREND,
    SALES_GROWTH,
    PURCHASE_RATIO,
    PROFITABILITY,
    CASH_FLOW,
    RECEIVABLES_ALERT,
    PAYABLES_ALERT,
    ANOMALY
}

enum class InsightImpact {
    POSITIVE,
    WARNING,
    CRITICAL,
    NEUTRAL
}

object ZenoAiInsightGenerator {

    /**
     * Generates rule-based, deterministic AI Financial Insights strictly from actual database records.
     * Never invents financial trends or percentages.
     * Returns clear insufficient historical data notifications if data is minimal.
     */
    fun generateInsights(
        accounts: List<Account>,
        customers: List<Party>,
        suppliers: List<Party>,
        invoices: List<SalesInvoice>,
        bills: List<PurchaseBill>,
        vouchers: List<PaymentVoucher>,
        journalEntries: List<JournalEntry>
    ): List<FinancialInsight> {
        val insights = mutableListOf<FinancialInsight>()

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        // 1. Minimum Data Check
        val totalTxCount = invoices.size + bills.size + vouchers.size + journalEntries.size
        if (totalTxCount == 0) {
            insights.add(
                FinancialInsight(
                    id = "insufficient_data",
                    title = "Awaiting Initial Financial Data",
                    description = "ZENO AI needs at least one recorded invoice, bill, or voucher to generate intelligent spending and growth analytics. Tap Voice AI or Quick Actions to record your first transaction.",
                    category = InsightCategory.PROFITABILITY,
                    impact = InsightImpact.NEUTRAL
                )
            )
            return insights
        }

        // Current Month vs Previous Month Partitioning
        calendar.timeInMillis = now
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        val calPrev = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.MONTH, -1)
        }
        val prevMonth = calPrev.get(Calendar.MONTH)
        val prevYear = calPrev.get(Calendar.YEAR)

        fun isCurrentMonth(ts: Long): Boolean {
            val c = Calendar.getInstance().apply { timeInMillis = ts }
            return c.get(Calendar.MONTH) == currentMonth && c.get(Calendar.YEAR) == currentYear
        }

        fun isPrevMonth(ts: Long): Boolean {
            val c = Calendar.getInstance().apply { timeInMillis = ts }
            return c.get(Calendar.MONTH) == prevMonth && c.get(Calendar.YEAR) == prevYear
        }

        // 2. Sales Trend Analysis
        val curSales = invoices.filter { isCurrentMonth(it.date) }.sumOf { it.totalAmount }
        val prevSales = invoices.filter { isPrevMonth(it.date) }.sumOf { it.totalAmount }
        val totalSales = invoices.sumOf { it.totalAmount }

        if (prevSales > 0.0 && curSales > 0.0) {
            val pctChange = ((curSales - prevSales) / prevSales) * 100.0
            if (pctChange > 0) {
                insights.add(
                    FinancialInsight(
                        id = "sales_growth",
                        title = "Sales Expansion Detected",
                        description = "Your sales increased by ${String.format("%.1f", pctChange)}% this month (${formatCurrency(curSales)} vs ${formatCurrency(prevSales)} in previous month).",
                        category = InsightCategory.SALES_GROWTH,
                        impact = InsightImpact.POSITIVE,
                        metricValue = "+${String.format("%.1f", pctChange)}%"
                    )
                )
            } else if (pctChange < -5.0) {
                insights.add(
                    FinancialInsight(
                        id = "sales_contraction",
                        title = "Sales Volume Contraction",
                        description = "Sales contracted by ${String.format("%.1f", abs(pctChange))}% compared to last month. Review outstanding customer follow-ups.",
                        category = InsightCategory.SALES_GROWTH,
                        impact = InsightImpact.WARNING,
                        metricValue = "-${String.format("%.1f", abs(pctChange))}%"
                    )
                )
            }
        } else if (totalSales > 0.0 && invoices.size >= 2) {
            insights.add(
                FinancialInsight(
                    id = "sales_active",
                    title = "Active Revenue Trajectory",
                    description = "Total sales currently stand at ${formatCurrency(totalSales)} across ${invoices.size} generated invoices.",
                    category = InsightCategory.SALES_GROWTH,
                    impact = InsightImpact.POSITIVE,
                    metricValue = formatCurrency(totalSales)
                )
            )
        }

        // 3. Purchase vs Sales Comparison
        val curPurchases = bills.filter { isCurrentMonth(it.date) }.sumOf { it.totalAmount }
        val totalPurchases = bills.sumOf { it.totalAmount }

        if (totalPurchases > totalSales && totalSales > 0) {
            val excess = totalPurchases - totalSales
            insights.add(
                FinancialInsight(
                    id = "purchases_exceed_sales",
                    title = "Procurement Exceeds Sales",
                    description = "Total purchases (${formatCurrency(totalPurchases)}) exceed total sales (${formatCurrency(totalSales)}) by ${formatCurrency(excess)}. Monitor stock inventory turnover to avoid tying up working capital.",
                    category = InsightCategory.PURCHASE_RATIO,
                    impact = InsightImpact.WARNING,
                    metricValue = formatCurrency(excess)
                )
            )
        } else if (totalSales > totalPurchases && totalPurchases > 0) {
            val margin = totalSales - totalPurchases
            insights.add(
                FinancialInsight(
                    id = "healthy_trade_margin",
                    title = "Favorable Trade Margin",
                    description = "Sales revenue exceeds inward purchases by ${formatCurrency(margin)}, demonstrating positive gross trading spread.",
                    category = InsightCategory.PURCHASE_RATIO,
                    impact = InsightImpact.POSITIVE,
                    metricValue = formatCurrency(margin)
                )
            )
        }

        // 4. Highest Expense Category Breakdown
        val expenseAccounts = accounts.filter { it.category == AccountCategory.EXPENSE && it.currentBalance > 0.0 }
        val highestExpense = expenseAccounts.maxByOrNull { it.currentBalance }
        val totalExpenses = expenseAccounts.sumOf { it.currentBalance }

        if (highestExpense != null && totalExpenses > 0) {
            val pct = (highestExpense.currentBalance / totalExpenses) * 100.0
            insights.add(
                FinancialInsight(
                    id = "highest_expense",
                    title = "${highestExpense.name} is Your Top Expense",
                    description = "${highestExpense.name} accounts for ${String.format("%.1f", pct)}% of all business expenses at ${formatCurrency(highestExpense.currentBalance)}.",
                    category = InsightCategory.EXPENSE_TREND,
                    impact = if (pct > 50.0) InsightImpact.WARNING else InsightImpact.NEUTRAL,
                    metricValue = "${String.format("%.0f", pct)}% of OPEX"
                )
            )
        }

        // 5. Customer Receivables & Overdue Payment Risk
        val overdueReceivables = customers.filter { it.outstandingAmount > 0.0 }
        val totalReceivables = overdueReceivables.sumOf { it.outstandingAmount }

        if (totalReceivables > 0.0) {
            val topDebtor = overdueReceivables.maxByOrNull { it.outstandingAmount }
            val debtorDesc = if (topDebtor != null) " Highest outstanding balance is with ${topDebtor.name} (${formatCurrency(topDebtor.outstandingAmount)})." else ""
            insights.add(
                FinancialInsight(
                    id = "receivables_alert",
                    title = "Outstanding Customer Receivables",
                    description = "You have ${formatCurrency(totalReceivables)} uncollected receivables across ${overdueReceivables.size} customer accounts.$debtorDesc",
                    category = InsightCategory.RECEIVABLES_ALERT,
                    impact = if (totalReceivables > 50000.0) InsightImpact.CRITICAL else InsightImpact.WARNING,
                    metricValue = formatCurrency(totalReceivables)
                )
            )
        }

        // 6. Supplier Payables Obligations
        val pendingPayables = suppliers.filter { it.outstandingAmount > 0.0 }
        val totalPayables = pendingPayables.sumOf { it.outstandingAmount }

        if (totalPayables > 0.0) {
            insights.add(
                FinancialInsight(
                    id = "payables_alert",
                    title = "Upcoming Vendor Payables",
                    description = "Total vendor liability stands at ${formatCurrency(totalPayables)} across ${pendingPayables.size} supplier credit ledgers.",
                    category = InsightCategory.PAYABLES_ALERT,
                    impact = InsightImpact.NEUTRAL,
                    metricValue = formatCurrency(totalPayables)
                )
            )
        }

        // 7. Cash Flow & Liquidity Trends
        val cashAccounts = accounts.filter { it.code in listOf("1010", "1020", "1030") }
        val liquidCash = cashAccounts.sumOf { it.currentBalance }

        if (liquidCash > 0 && totalPayables > 0) {
            val coverage = liquidCash / totalPayables
            if (coverage >= 1.0) {
                insights.add(
                    FinancialInsight(
                        id = "strong_liquidity",
                        title = "Strong Liquid Working Capital",
                        description = "Current bank and cash balance (${formatCurrency(liquidCash)}) fully covers all supplier payables (${formatCurrency(totalPayables)}) with a ${(coverage * 100).toInt()}% coverage ratio.",
                        category = InsightCategory.CASH_FLOW,
                        impact = InsightImpact.POSITIVE,
                        metricValue = "${String.format("%.1f", coverage)}x Coverage"
                    )
                )
            } else {
                insights.add(
                    FinancialInsight(
                        id = "tight_liquidity",
                        title = "Tight Liquidity Notice",
                        description = "Immediate liquid funds (${formatCurrency(liquidCash)}) are below total vendor payables (${formatCurrency(totalPayables)}). Accelerate customer collections.",
                        category = InsightCategory.CASH_FLOW,
                        impact = InsightImpact.WARNING,
                        metricValue = "${String.format("%.0f", coverage * 100)}% Available"
                    )
                )
            }
        }

        // 8. Net Profitability Ratio
        val incomeAccounts = accounts.filter { it.category == AccountCategory.INCOME }
        val totalIncome = incomeAccounts.sumOf { it.currentBalance }
        val netProfit = totalIncome - totalExpenses

        if (totalIncome > 0.0) {
            val netMarginPct = (netProfit / totalIncome) * 100.0
            insights.add(
                FinancialInsight(
                    id = "profit_margin",
                    title = if (netProfit >= 0) "Net Profit Margin: ${String.format("%.1f", netMarginPct)}%" else "Operating Loss Alert",
                    description = if (netProfit >= 0)
                        "Net business profit stands at ${formatCurrency(netProfit)} representing a ${String.format("%.1f", netMarginPct)}% return on total earnings."
                    else
                        "Expenses currently exceed revenues by ${formatCurrency(abs(netProfit))}. Review direct procurement and utility costs.",
                    category = InsightCategory.PROFITABILITY,
                    impact = if (netProfit >= 0) InsightImpact.POSITIVE else InsightImpact.CRITICAL,
                    metricValue = formatCurrency(netProfit)
                )
            )
        }

        return insights
    }
}
