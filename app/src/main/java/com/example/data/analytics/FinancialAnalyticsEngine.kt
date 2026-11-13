package com.example.data.analytics

import com.example.data.model.*
import java.text.SimpleDateFormat
import java.util.*

// Data Models for Analytics
data class DashboardSummary(
    val totalIncome: Double,
    val totalExpenses: Double,
    val totalSales: Double,
    val totalPurchases: Double,
    val netProfit: Double,
    val cashBalance: Double,
    val bankBalance: Double,
    val customerOutstanding: Double,
    val supplierOutstanding: Double,
    val netGstPayable: Double,
    val inventoryValue: Double
)

data class CategorySlice(
    val category: String,
    val amount: Double,
    val percentage: Double,
    val count: Int
)

data class TimeSeriesPoint(
    val label: String,
    val timestamp: Long,
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val sales: Double = 0.0,
    val purchase: Double = 0.0,
    val netProfit: Double = 0.0,
    val cashInflow: Double = 0.0,
    val cashOutflow: Double = 0.0,
    val netCashFlow: Double = 0.0
)

data class TransactionDistribution(
    val type: String,
    val count: Int,
    val totalAmount: Double,
    val percentage: Double
)

data class PartyOutstandingDetail(
    val partyId: Long,
    val partyName: String,
    val outstandingAmount: Double,
    val phone: String,
    val gstin: String,
    val isReceivable: Boolean
)

data class FinancialInsight(
    val title: String,
    val description: String,
    val type: InsightType, // POSITIVE, WARNING, NEUTRAL
    val actionableStep: String? = null
)

enum class InsightType {
    POSITIVE,
    WARNING,
    NEUTRAL
}

enum class AnalyticsTimeFilter {
    ALL_TIME,
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    THIS_QUARTER,
    THIS_YEAR
}

/**
 * Backend Financial Analytics Engine.
 * Implements strict Indian accounting standards:
 * - Proper revenue & expense recognition
 * - Never counts capital, transfers, or loans as business income
 * - Prevents double counting across sales invoices, vouchers, and journal entries
 * - Computes real-time dynamic statistics across all user-scoped records
 */
class FinancialAnalyticsEngine {

    fun getDashboardSummary(
        accounts: List<Account>,
        customers: List<Party>,
        suppliers: List<Party>,
        products: List<Product>,
        invoices: List<SalesInvoice>,
        bills: List<PurchaseBill>,
        filter: AnalyticsTimeFilter = AnalyticsTimeFilter.ALL_TIME
    ): DashboardSummary {
        val filteredInvoices = filterInvoices(invoices, filter)
        val filteredBills = filterBills(bills, filter)

        val totalSales = filteredInvoices.sumOf { it.totalAmount }
        val totalPurchases = filteredBills.sumOf { it.totalAmount }

        val customerOutstanding = customers.sumOf { it.outstandingAmount }
        val supplierOutstanding = suppliers.sumOf { it.outstandingAmount }

        val cashBalance = accounts.filter { it.code == "1010" }.sumOf { it.currentBalance }
        val bankBalance = accounts.filter { it.code in listOf("1020", "1030") }.sumOf { it.currentBalance }
        val inventoryValue = products.sumOf { it.currentStock * it.purchasePrice }

        val totalOutputTax = filteredInvoices.sumOf { it.cgst + it.sgst + it.igst }
        val totalInputTax = filteredBills.sumOf { it.cgst + it.sgst + it.igst }
        val netGstPayable = maxOf(0.0, totalOutputTax - totalInputTax)

        // Strict Accounting calculation:
        // For ALL_TIME, use cumulative balances or invoice/bill sums.
        // For time-filtered periods (TODAY, YESTERDAY, THIS_WEEK, etc.), strictly isolate the period's transactions!
        val allTimeIncome = accounts.filter { it.category == AccountCategory.INCOME }.sumOf { it.currentBalance }
        val allTimeExpenses = accounts.filter { it.category == AccountCategory.EXPENSE }.sumOf { it.currentBalance }

        val periodIncome = if (filter == AnalyticsTimeFilter.ALL_TIME) {
            if (allTimeIncome > 0) allTimeIncome else totalSales
        } else {
            totalSales
        }

        val periodExpenses = if (filter == AnalyticsTimeFilter.ALL_TIME) {
            if (allTimeExpenses > 0) allTimeExpenses else totalPurchases
        } else {
            totalPurchases
        }

        val netProfit = if (filter == AnalyticsTimeFilter.ALL_TIME) {
            if (allTimeIncome > 0 || allTimeExpenses > 0) (allTimeIncome - allTimeExpenses) else (totalSales - totalPurchases)
        } else {
            periodIncome - periodExpenses
        }

        return DashboardSummary(
            totalIncome = periodIncome,
            totalExpenses = periodExpenses,
            totalSales = totalSales,
            totalPurchases = totalPurchases,
            netProfit = netProfit,
            cashBalance = cashBalance,
            bankBalance = bankBalance,
            customerOutstanding = customerOutstanding,
            supplierOutstanding = supplierOutstanding,
            netGstPayable = netGstPayable,
            inventoryValue = inventoryValue
        )
    }

    fun getIncomeExpenseAnalysis(
        accounts: List<Account>,
        invoices: List<SalesInvoice>,
        bills: List<PurchaseBill>
    ): List<CategorySlice> {
        val totalInc = accounts.filter { it.category == AccountCategory.INCOME }.sumOf { it.currentBalance }.let {
            if (it > 0) it else invoices.sumOf { inv -> inv.totalAmount }
        }
        val totalExp = accounts.filter { it.category == AccountCategory.EXPENSE }.sumOf { it.currentBalance }.let {
            if (it > 0) it else bills.sumOf { bill -> bill.totalAmount }
        }
        val sum = totalInc + totalExp
        if (sum == 0.0) return emptyList()

        return listOf(
            CategorySlice(
                category = "Total Income",
                amount = totalInc,
                percentage = (totalInc / sum) * 100.0,
                count = invoices.size
            ),
            CategorySlice(
                category = "Total Expenses",
                amount = totalExp,
                percentage = (totalExp / sum) * 100.0,
                count = bills.size
            )
        )
    }

    fun getExpenseBreakdown(
        accounts: List<Account>,
        bills: List<PurchaseBill>
    ): List<CategorySlice> {
        val expenseAccounts = accounts.filter { it.category == AccountCategory.EXPENSE && it.currentBalance > 0 }
        if (expenseAccounts.isNotEmpty()) {
            val total = expenseAccounts.sumOf { it.currentBalance }
            return expenseAccounts.map { acc ->
                CategorySlice(
                    category = acc.name,
                    amount = acc.currentBalance,
                    percentage = if (total > 0) (acc.currentBalance / total) * 100.0 else 0.0,
                    count = 1
                )
            }.sortedByDescending { it.amount }
        }

        // Derived from purchase bills if manual expense accounts not yet recorded
        val totalBills = bills.sumOf { it.totalAmount }
        if (totalBills == 0.0) return emptyList()

        val directPurchases = bills.filter { !it.isInterstate }.sumOf { it.totalAmount }
        val interstatePurchases = bills.filter { it.isInterstate }.sumOf { it.totalAmount }

        val list = mutableListOf<CategorySlice>()
        if (directPurchases > 0) {
            list.add(CategorySlice("Local Inventory Purchases", directPurchases, (directPurchases / totalBills) * 100.0, bills.count { !it.isInterstate }))
        }
        if (interstatePurchases > 0) {
            list.add(CategorySlice("Inter-state Purchases (IGST)", interstatePurchases, (interstatePurchases / totalBills) * 100.0, bills.count { it.isInterstate }))
        }
        return list
    }

    fun getSalesPurchaseAnalysis(
        invoices: List<SalesInvoice>,
        bills: List<PurchaseBill>
    ): List<CategorySlice> {
        val sales = invoices.sumOf { it.totalAmount }
        val purchases = bills.sumOf { it.totalAmount }
        val total = sales + purchases
        if (total == 0.0) return emptyList()

        return listOf(
            CategorySlice("Sales Revenue", sales, (sales / total) * 100.0, invoices.size),
            CategorySlice("Purchases Cost", purchases, (purchases / total) * 100.0, bills.size)
        )
    }

    fun getTransactionDistribution(
        invoices: List<SalesInvoice>,
        bills: List<PurchaseBill>,
        vouchers: List<PaymentVoucher>,
        journalEntries: List<JournalEntry>
    ): List<TransactionDistribution> {
        val salesAmt = invoices.sumOf { it.totalAmount }
        val purchaseAmt = bills.sumOf { it.totalAmount }
        val receiptAmt = vouchers.filter { it.type == VoucherType.RECEIPT }.sumOf { it.amount }
        val paymentAmt = vouchers.filter { it.type == VoucherType.PAYMENT }.sumOf { it.amount }
        val journalAmt = journalEntries.filter { !it.isAutoGenerated }.sumOf { it.totalDebit }

        val grandTotal = salesAmt + purchaseAmt + receiptAmt + paymentAmt + journalAmt
        if (grandTotal == 0.0 && invoices.isEmpty() && bills.isEmpty() && vouchers.isEmpty()) {
            return emptyList()
        }

        val items = mutableListOf<TransactionDistribution>()
        if (invoices.isNotEmpty()) items.add(TransactionDistribution("Sales Invoices", invoices.size, salesAmt, if (grandTotal > 0) (salesAmt / grandTotal) * 100 else 0.0))
        if (bills.isNotEmpty()) items.add(TransactionDistribution("Purchase Bills", bills.size, purchaseAmt, if (grandTotal > 0) (purchaseAmt / grandTotal) * 100 else 0.0))
        val receipts = vouchers.filter { it.type == VoucherType.RECEIPT }
        if (receipts.isNotEmpty()) items.add(TransactionDistribution("Receipt Vouchers", receipts.size, receiptAmt, if (grandTotal > 0) (receiptAmt / grandTotal) * 100 else 0.0))
        val payments = vouchers.filter { it.type == VoucherType.PAYMENT }
        if (payments.isNotEmpty()) items.add(TransactionDistribution("Payment Vouchers", payments.size, paymentAmt, if (grandTotal > 0) (paymentAmt / grandTotal) * 100 else 0.0))
        val manualJournals = journalEntries.filter { !it.isAutoGenerated }
        if (manualJournals.isNotEmpty()) items.add(TransactionDistribution("Journal Entries", manualJournals.size, journalAmt, if (grandTotal > 0) (journalAmt / grandTotal) * 100 else 0.0))

        return items
    }

    fun getMonthlyFinancialAnalysis(
        invoices: List<SalesInvoice>,
        bills: List<PurchaseBill>
    ): List<TimeSeriesPoint> {
        val cal = Calendar.getInstance()
        val monthFormat = SimpleDateFormat("MMM", Locale.ENGLISH)
        val points = mutableListOf<TimeSeriesPoint>()

        for (i in 5 downTo 0) {
            val targetCal = Calendar.getInstance().apply {
                timeInMillis = cal.timeInMillis
                add(Calendar.MONTH, -i)
            }
            val m = targetCal.get(Calendar.MONTH)
            val y = targetCal.get(Calendar.YEAR)
            val label = monthFormat.format(targetCal.time)

            val mInvoices = invoices.filter {
                val c = Calendar.getInstance().apply { timeInMillis = it.date }
                c.get(Calendar.MONTH) == m && c.get(Calendar.YEAR) == y
            }
            val mBills = bills.filter {
                val c = Calendar.getInstance().apply { timeInMillis = it.date }
                c.get(Calendar.MONTH) == m && c.get(Calendar.YEAR) == y
            }

            val sales = mInvoices.sumOf { it.totalAmount }
            val purchases = mBills.sumOf { it.totalAmount }
            points.add(
                TimeSeriesPoint(
                    label = label,
                    timestamp = targetCal.timeInMillis,
                    sales = sales,
                    purchase = purchases,
                    income = sales,
                    expense = purchases,
                    netProfit = sales - purchases
                )
            )
        }
        return points
    }

    fun getDailyFinancialAnalysis(
        invoices: List<SalesInvoice>,
        bills: List<PurchaseBill>
    ): List<TimeSeriesPoint> {
        val dayFormat = SimpleDateFormat("EEE", Locale.ENGLISH)
        val points = mutableListOf<TimeSeriesPoint>()

        for (i in 6 downTo 0) {
            val targetCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val d = targetCal.get(Calendar.DAY_OF_YEAR)
            val y = targetCal.get(Calendar.YEAR)
            val label = dayFormat.format(targetCal.time)

            val dInvoices = invoices.filter {
                val c = Calendar.getInstance().apply { timeInMillis = it.date }
                c.get(Calendar.DAY_OF_YEAR) == d && c.get(Calendar.YEAR) == y
            }
            val dBills = bills.filter {
                val c = Calendar.getInstance().apply { timeInMillis = it.date }
                c.get(Calendar.DAY_OF_YEAR) == d && c.get(Calendar.YEAR) == y
            }

            val sales = dInvoices.sumOf { it.totalAmount }
            val purchases = dBills.sumOf { it.totalAmount }
            points.add(
                TimeSeriesPoint(
                    label = label,
                    timestamp = targetCal.timeInMillis,
                    sales = sales,
                    purchase = purchases,
                    netProfit = sales - purchases
                )
            )
        }
        return points
    }

    fun getWeeklyFinancialAnalysis(
        invoices: List<SalesInvoice>,
        bills: List<PurchaseBill>
    ): List<TimeSeriesPoint> {
        val points = mutableListOf<TimeSeriesPoint>()
        val cal = Calendar.getInstance()

        for (i in 3 downTo 0) {
            val endMillis = cal.timeInMillis - (i * 7L * 24 * 3600 * 1000)
            val startMillis = endMillis - (7L * 24 * 3600 * 1000)
            val label = "Wk ${4 - i}"

            val wInvoices = invoices.filter { it.date in startMillis..endMillis }
            val wBills = bills.filter { it.date in startMillis..endMillis }

            val sales = wInvoices.sumOf { it.totalAmount }
            val purchases = wBills.sumOf { it.totalAmount }

            points.add(
                TimeSeriesPoint(
                    label = label,
                    timestamp = endMillis,
                    sales = sales,
                    purchase = purchases,
                    income = sales,
                    expense = purchases,
                    netProfit = sales - purchases
                )
            )
        }
        return points
    }

    fun getCashFlowAnalysis(
        vouchers: List<PaymentVoucher>,
        invoices: List<SalesInvoice>,
        bills: List<PurchaseBill>
    ): List<TimeSeriesPoint> {
        val monthFormat = SimpleDateFormat("MMM", Locale.ENGLISH)
        val points = mutableListOf<TimeSeriesPoint>()

        for (i in 5 downTo 0) {
            val targetCal = Calendar.getInstance().apply {
                add(Calendar.MONTH, -i)
            }
            val m = targetCal.get(Calendar.MONTH)
            val y = targetCal.get(Calendar.YEAR)
            val label = monthFormat.format(targetCal.time)

            // Cash in: Receipts + Cash Sales
            val mReceipts = vouchers.filter {
                it.type == VoucherType.RECEIPT && isSameMonth(it.date, m, y)
            }.sumOf { it.amount }
            val mCashSales = invoices.filter {
                it.paymentMode in listOf("CASH", "BANK", "UPI") && isSameMonth(it.date, m, y)
            }.sumOf { it.totalAmount }
            val totalInflow = mReceipts + mCashSales

            // Cash out: Payments + Cash Purchases
            val mPayments = vouchers.filter {
                it.type == VoucherType.PAYMENT && isSameMonth(it.date, m, y)
            }.sumOf { it.amount }
            val mCashPurchases = bills.filter {
                it.paymentMode in listOf("CASH", "BANK", "UPI") && isSameMonth(it.date, m, y)
            }.sumOf { it.totalAmount }
            val totalOutflow = mPayments + mCashPurchases

            points.add(
                TimeSeriesPoint(
                    label = label,
                    timestamp = targetCal.timeInMillis,
                    cashInflow = totalInflow,
                    cashOutflow = totalOutflow,
                    netCashFlow = totalInflow - totalOutflow
                )
            )
        }
        return points
    }

    fun getTransactionStatistics(
        invoices: List<SalesInvoice>,
        bills: List<PurchaseBill>,
        vouchers: List<PaymentVoucher>,
        journalEntries: List<JournalEntry>
    ): Map<String, Any> {
        val totalTxCount = invoices.size + bills.size + vouchers.size + journalEntries.size
        val totalVolume = invoices.sumOf { it.totalAmount } + bills.sumOf { it.totalAmount } + vouchers.sumOf { it.amount }
        val avgTxSize = if (totalTxCount > 0) totalVolume / totalTxCount else 0.0

        return mapOf(
            "totalTransactions" to totalTxCount,
            "totalVolume" to totalVolume,
            "averageTransactionSize" to avgTxSize,
            "salesCount" to invoices.size,
            "purchaseCount" to bills.size,
            "voucherCount" to vouchers.size,
            "journalCount" to journalEntries.size
        )
    }

    fun getCustomerOutstanding(customers: List<Party>): List<PartyOutstandingDetail> {
        return customers
            .filter { it.outstandingAmount > 0 }
            .sortedByDescending { it.outstandingAmount }
            .map {
                PartyOutstandingDetail(
                    partyId = it.id,
                    partyName = it.name,
                    outstandingAmount = it.outstandingAmount,
                    phone = it.phone,
                    gstin = it.gstin,
                    isReceivable = true
                )
            }
    }

    fun getSupplierOutstanding(suppliers: List<Party>): List<PartyOutstandingDetail> {
        return suppliers
            .filter { it.outstandingAmount > 0 }
            .sortedByDescending { it.outstandingAmount }
            .map {
                PartyOutstandingDetail(
                    partyId = it.id,
                    partyName = it.name,
                    outstandingAmount = it.outstandingAmount,
                    phone = it.phone,
                    gstin = it.gstin,
                    isReceivable = false
                )
            }
    }

    fun getFinancialInsights(
        summary: DashboardSummary,
        invoices: List<SalesInvoice>,
        bills: List<PurchaseBill>,
        customers: List<Party>,
        suppliers: List<Party>
    ): List<FinancialInsight> {
        val insights = mutableListOf<FinancialInsight>()

        // 1. Profitability Insight
        if (summary.netProfit > 0) {
            val margin = if (summary.totalSales > 0) (summary.netProfit / summary.totalSales) * 100 else 0.0
            insights.add(
                FinancialInsight(
                    title = "Positive Operating Margin (${String.format(Locale.ENGLISH, "%.1f", margin)}%)",
                    description = "Your business generated ₹${String.format(Locale.ENGLISH, "%,.2f", summary.netProfit)} net profit over sales of ₹${String.format(Locale.ENGLISH, "%,.2f", summary.totalSales)}.",
                    type = InsightType.POSITIVE,
                    actionableStep = "Maintain expense controls to preserve healthy profit margins."
                )
            )
        } else if (summary.totalSales > 0 && summary.netProfit < 0) {
            insights.add(
                FinancialInsight(
                    title = "Operating Deficit Detected",
                    description = "Purchases and expenses exceed sales revenue by ₹${String.format(Locale.ENGLISH, "%,.2f", kotlin.math.abs(summary.netProfit))}.",
                    type = InsightType.WARNING,
                    actionableStep = "Review input costs or renegotiate supplier vendor purchase prices."
                )
            )
        }

        // 2. Working Capital & Receivables
        if (summary.customerOutstanding > summary.cashBalance + summary.bankBalance && summary.customerOutstanding > 0) {
            insights.add(
                FinancialInsight(
                    title = "High Outstanding Receivables",
                    description = "Customer dues (₹${String.format(Locale.ENGLISH, "%,.2f", summary.customerOutstanding)}) exceed current liquid cash/bank balances.",
                    type = InsightType.WARNING,
                    actionableStep = "Send WhatsApp/SMS reminders to customers with overdue credit terms."
                )
            )
        } else if (summary.customerOutstanding == 0.0 && summary.totalSales > 0) {
            insights.add(
                FinancialInsight(
                    title = "Excellent Liquidity Collection",
                    description = "All sales have been fully collected with zero customer debt outstanding.",
                    type = InsightType.POSITIVE
                )
            )
        }

        // 3. GST Tax Liability Set-off
        if (summary.netGstPayable > 0) {
            insights.add(
                FinancialInsight(
                    title = "GST Output Liability: ₹${String.format(Locale.ENGLISH, "%,.2f", summary.netGstPayable)}",
                    description = "Net tax payable after input tax credit set-off for current filing period.",
                    type = InsightType.NEUTRAL,
                    actionableStep = "Generate GSTR-3B summary before the 20th of the month."
                )
            )
        }

        if (insights.isEmpty()) {
            insights.add(
                FinancialInsight(
                    title = "Clean Slate & Ready for Bookkeeping",
                    description = "No transaction anomalies detected. Start logging sales or purchases to generate real-time AI insights.",
                    type = InsightType.NEUTRAL,
                    actionableStep = "Use ZENO voice assistant to record your first transaction."
                )
            )
        }

        return insights
    }

    // Exact lowercase Python / REST named aliases for full interoperability:
    fun get_dashboard_summary(accounts: List<Account>, customers: List<Party>, suppliers: List<Party>, products: List<Product>, invoices: List<SalesInvoice>, bills: List<PurchaseBill>, filter: AnalyticsTimeFilter = AnalyticsTimeFilter.ALL_TIME) = getDashboardSummary(accounts, customers, suppliers, products, invoices, bills, filter)
    fun get_income_expense_analysis(accounts: List<Account>, invoices: List<SalesInvoice>, bills: List<PurchaseBill>) = getIncomeExpenseAnalysis(accounts, invoices, bills)
    fun get_expense_breakdown(accounts: List<Account>, bills: List<PurchaseBill>) = getExpenseBreakdown(accounts, bills)
    fun get_sales_purchase_analysis(invoices: List<SalesInvoice>, bills: List<PurchaseBill>) = getSalesPurchaseAnalysis(invoices, bills)
    fun get_monthly_financial_analysis(invoices: List<SalesInvoice>, bills: List<PurchaseBill>) = getMonthlyFinancialAnalysis(invoices, bills)
    fun get_daily_financial_analysis(invoices: List<SalesInvoice>, bills: List<PurchaseBill>) = getDailyFinancialAnalysis(invoices, bills)
    fun get_weekly_financial_analysis(invoices: List<SalesInvoice>, bills: List<PurchaseBill>) = getWeeklyFinancialAnalysis(invoices, bills)
    fun get_cash_flow_analysis(vouchers: List<PaymentVoucher>, invoices: List<SalesInvoice>, bills: List<PurchaseBill>) = getCashFlowAnalysis(vouchers, invoices, bills)
    fun get_transaction_statistics(invoices: List<SalesInvoice>, bills: List<PurchaseBill>, vouchers: List<PaymentVoucher>, journalEntries: List<JournalEntry>) = getTransactionStatistics(invoices, bills, vouchers, journalEntries)
    fun get_customer_outstanding(customers: List<Party>) = getCustomerOutstanding(customers)
    fun get_supplier_outstanding(suppliers: List<Party>) = getSupplierOutstanding(suppliers)
    fun get_financial_insights(summary: DashboardSummary, invoices: List<SalesInvoice>, bills: List<PurchaseBill>, customers: List<Party>, suppliers: List<Party>) = getFinancialInsights(summary, invoices, bills, customers, suppliers)

    private fun isSameMonth(timestamp: Long, month: Int, year: Int): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        return c.get(Calendar.MONTH) == month && c.get(Calendar.YEAR) == year
    }

    private fun filterInvoices(invoices: List<SalesInvoice>, filter: AnalyticsTimeFilter): List<SalesInvoice> {
        return when (filter) {
            AnalyticsTimeFilter.ALL_TIME -> invoices
            AnalyticsTimeFilter.TODAY -> {
                val calStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val calEnd = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                invoices.filter { it.date in calStart..calEnd }
            }
            AnalyticsTimeFilter.YESTERDAY -> {
                val calStart = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val calEnd = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                invoices.filter { it.date in calStart..calEnd }
            }
            AnalyticsTimeFilter.THIS_WEEK -> {
                val calStart = Calendar.getInstance().apply {
                    val dow = get(Calendar.DAY_OF_WEEK)
                    val diffFromMonday = (dow - Calendar.MONDAY + 7) % 7
                    add(Calendar.DAY_OF_YEAR, -diffFromMonday)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                invoices.filter { it.date >= calStart }
            }
            AnalyticsTimeFilter.THIS_MONTH -> {
                val calStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                invoices.filter { it.date >= calStart }
            }
            AnalyticsTimeFilter.THIS_QUARTER -> {
                val qAgo = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
                invoices.filter { it.date >= qAgo }
            }
            AnalyticsTimeFilter.THIS_YEAR -> {
                val calStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                invoices.filter { it.date >= calStart }
            }
        }
    }

    private fun filterBills(bills: List<PurchaseBill>, filter: AnalyticsTimeFilter): List<PurchaseBill> {
        return when (filter) {
            AnalyticsTimeFilter.ALL_TIME -> bills
            AnalyticsTimeFilter.TODAY -> {
                val calStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val calEnd = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                bills.filter { it.date in calStart..calEnd }
            }
            AnalyticsTimeFilter.YESTERDAY -> {
                val calStart = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val calEnd = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                bills.filter { it.date in calStart..calEnd }
            }
            AnalyticsTimeFilter.THIS_WEEK -> {
                val calStart = Calendar.getInstance().apply {
                    val dow = get(Calendar.DAY_OF_WEEK)
                    val diffFromMonday = (dow - Calendar.MONDAY + 7) % 7
                    add(Calendar.DAY_OF_YEAR, -diffFromMonday)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                bills.filter { it.date >= calStart }
            }
            AnalyticsTimeFilter.THIS_MONTH -> {
                val calStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                bills.filter { it.date >= calStart }
            }
            AnalyticsTimeFilter.THIS_QUARTER -> {
                val qAgo = System.currentTimeMillis() - 90L * 24 * 3600 * 1000
                bills.filter { it.date >= qAgo }
            }
            AnalyticsTimeFilter.THIS_YEAR -> {
                val calStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                bills.filter { it.date >= calStart }
            }
        }
    }
}
