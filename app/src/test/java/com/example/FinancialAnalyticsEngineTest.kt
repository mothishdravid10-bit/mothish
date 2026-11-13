package com.example

import com.example.data.analytics.AnalyticsTimeFilter
import com.example.data.analytics.FinancialAnalyticsEngine
import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class FinancialAnalyticsEngineTest {

    private lateinit var engine: FinancialAnalyticsEngine

    @Before
    fun setUp() {
        engine = FinancialAnalyticsEngine()
    }

    @Test
    fun testEmptyDataReturnsZeroMetrics() {
        val summary = engine.getDashboardSummary(
            accounts = emptyList(),
            customers = emptyList(),
            suppliers = emptyList(),
            products = emptyList(),
            invoices = emptyList(),
            bills = emptyList(),
            filter = AnalyticsTimeFilter.ALL_TIME
        )

        assertEquals(0.0, summary.totalSales, 0.001)
        assertEquals(0.0, summary.totalPurchases, 0.001)
        assertEquals(0.0, summary.netProfit, 0.001)
        assertEquals(0.0, summary.customerOutstanding, 0.001)
        assertEquals(0.0, summary.supplierOutstanding, 0.001)
        assertEquals(0.0, summary.netGstPayable, 0.001)
    }

    @Test
    fun testGstPayableComputation() {
        val invoices = listOf(
            SalesInvoice(
                userId = 1,
                invoiceNumber = "INV-001",
                customerId = 1,
                customerName = "Apex Ltd",
                subtotal = 10000.0,
                cgst = 900.0,
                sgst = 900.0,
                totalAmount = 11800.0
            )
        )
        val bills = listOf(
            PurchaseBill(
                userId = 1,
                billNumber = "BILL-001",
                supplierId = 1,
                supplierName = "Zenith Corp",
                subtotal = 5000.0,
                cgst = 450.0,
                sgst = 450.0,
                totalAmount = 5900.0
            )
        )

        val summary = engine.getDashboardSummary(
            accounts = emptyList(),
            customers = emptyList(),
            suppliers = emptyList(),
            products = emptyList(),
            invoices = invoices,
            bills = bills
        )

        // Output tax = 1800, Input tax = 900 -> Net GST Payable = 900
        assertEquals(900.0, summary.netGstPayable, 0.001)
        assertEquals(11800.0, summary.totalSales, 0.001)
        assertEquals(5900.0, summary.totalPurchases, 0.001)
        assertEquals(5900.0, summary.netProfit, 0.001)
    }

    @Test
    fun testIncomeExpenseDistributionCalculation() {
        val accounts = listOf(
            Account(id = 1, code = "4010", name = "Sales Account", category = AccountCategory.INCOME, subcategory = "Direct", currentBalance = 50000.0),
            Account(id = 2, code = "5010", name = "Purchases Account", category = AccountCategory.EXPENSE, subcategory = "Direct", currentBalance = 30000.0)
        )

        val slices = engine.getIncomeExpenseAnalysis(accounts, emptyList(), emptyList())
        assertEquals(2, slices.size)
        val incSlice = slices.first { it.category == "Total Income" }
        val expSlice = slices.first { it.category == "Total Expenses" }

        assertEquals(50000.0, incSlice.amount, 0.001)
        assertEquals(62.5, incSlice.percentage, 0.001)
        assertEquals(30000.0, expSlice.amount, 0.001)
        assertEquals(37.5, expSlice.percentage, 0.001)
    }

    @Test
    fun testCustomerAndSupplierOutstandingSorting() {
        val customers = listOf(
            Party(id = 1, name = "Customer A", type = PartyType.CUSTOMER, outstandingAmount = 15000.0),
            Party(id = 2, name = "Customer B", type = PartyType.CUSTOMER, outstandingAmount = 45000.0),
            Party(id = 3, name = "Customer C", type = PartyType.CUSTOMER, outstandingAmount = 0.0)
        )

        val debtors = engine.getCustomerOutstanding(customers)
        assertEquals(2, debtors.size)
        assertEquals("Customer B", debtors[0].partyName)
        assertEquals(45000.0, debtors[0].outstandingAmount, 0.001)
        assertEquals("Customer A", debtors[1].partyName)
    }

    @Test
    fun testTodayVsYesterdayExpensesAreIsolated() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val todayMillis = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayMillis = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -7)
        val lastWeekMillis = cal.timeInMillis

        val bills = listOf(
            PurchaseBill(
                userId = 1,
                billNumber = "BILL-TODAY",
                supplierId = 1,
                supplierName = "Supplier A",
                subtotal = 2000.0,
                totalAmount = 2360.0,
                date = todayMillis
            ),
            PurchaseBill(
                userId = 1,
                billNumber = "BILL-YDAY",
                supplierId = 2,
                supplierName = "Supplier B",
                subtotal = 5000.0,
                totalAmount = 5900.0,
                date = yesterdayMillis
            ),
            PurchaseBill(
                userId = 1,
                billNumber = "BILL-PAST",
                supplierId = 3,
                supplierName = "Supplier C",
                subtotal = 10000.0,
                totalAmount = 11800.0,
                date = lastWeekMillis
            )
        )

        // Expense account has cumulative lifetime balance of 50000.0
        val expenseAccount = Account(
            id = 10,
            userId = 1,
            code = "5010",
            name = "Purchases Account",
            category = AccountCategory.EXPENSE,
            subcategory = "Direct Expenses",
            currentBalance = 50000.0
        )

        // TODAY filter: strictly today's bill (2360.0), NOT yesterday's (5900.0) and NOT cumulative (50000.0)
        val todaySummary = engine.getDashboardSummary(
            accounts = listOf(expenseAccount),
            customers = emptyList(),
            suppliers = emptyList(),
            products = emptyList(),
            invoices = emptyList(),
            bills = bills,
            filter = AnalyticsTimeFilter.TODAY
        )
        assertEquals(2360.0, todaySummary.totalExpenses, 0.001)
        assertEquals(2360.0, todaySummary.totalPurchases, 0.001)

        // YESTERDAY filter: strictly yesterday's bill (5900.0), NOT today's (2360.0)
        val yesterdaySummary = engine.getDashboardSummary(
            accounts = listOf(expenseAccount),
            customers = emptyList(),
            suppliers = emptyList(),
            products = emptyList(),
            invoices = emptyList(),
            bills = bills,
            filter = AnalyticsTimeFilter.YESTERDAY
        )
        assertEquals(5900.0, yesterdaySummary.totalExpenses, 0.001)
        assertEquals(5900.0, yesterdaySummary.totalPurchases, 0.001)

        // ALL_TIME filter: uses cumulative expense account balance (50000.0)
        val allTimeSummary = engine.getDashboardSummary(
            accounts = listOf(expenseAccount),
            customers = emptyList(),
            suppliers = emptyList(),
            products = emptyList(),
            invoices = emptyList(),
            bills = bills,
            filter = AnalyticsTimeFilter.ALL_TIME
        )
        assertEquals(50000.0, allTimeSummary.totalExpenses, 0.001)
    }
}
