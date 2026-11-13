package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AssistantQueryResult
import com.example.ai.AssistantState
import com.example.ai.ConversationStage
import com.example.ai.DateRangeParser
import com.example.ai.ZenoAiEngine
import com.example.ai.ZenoIntent
import com.example.ai.ZenoIntentClassifier
import com.example.data.dao.AccountingDao
import com.example.data.model.*
import com.example.data.repository.AccountingRepository
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ZenoAiScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class InMemoryAccountingDao : AccountingDao {
    val users = MutableStateFlow<List<User>>(emptyList())
    val accounts = MutableStateFlow<List<Account>>(emptyList())
    val parties = MutableStateFlow<List<Party>>(emptyList())
    val products = MutableStateFlow<List<Product>>(emptyList())
    val invoices = MutableStateFlow<List<SalesInvoice>>(emptyList())
    val bills = MutableStateFlow<List<PurchaseBill>>(emptyList())
    val journals = MutableStateFlow<List<JournalEntry>>(emptyList())
    val journalLines = mutableStateFlowLines()
    val vouchers = MutableStateFlow<List<PaymentVoucher>>(emptyList())
    val reminders = MutableStateFlow<List<PaymentReminder>>(emptyList())
    val settings = MutableStateFlow<CompanySettings?>(CompanySettings(userId = 1L, companyName = "Zeno Traders"))

    private fun mutableStateFlowLines() = MutableStateFlow<List<JournalEntryLine>>(emptyList())

    override suspend fun getUserByEmail(email: String): User? =
        users.value.firstOrNull { it.email.equals(email, ignoreCase = true) }

    override suspend fun getUserById(id: Long): User? =
        users.value.firstOrNull { it.id == id }

    override fun getAllUsers(): Flow<List<User>> = users

    override suspend fun insertUser(user: User): Long {
        val nextId = (users.value.maxOfOrNull { it.id } ?: 0L) + 1L
        users.value = users.value + user.copy(id = nextId)
        return nextId
    }

    override suspend fun updateUser(user: User) {
        users.value = users.value.map { if (it.id == user.id) user else it }
    }

    override fun getAllAccounts(userId: Long): Flow<List<Account>> =
        accounts.map { list -> list.filter { it.userId == userId } }

    override fun getAccountsByCategory(userId: Long, category: AccountCategory): Flow<List<Account>> =
        accounts.map { list -> list.filter { it.userId == userId && it.category == category } }

    override suspend fun getAccountById(id: Long): Account? =
        accounts.value.firstOrNull { it.id == id }

    override suspend fun getAccountByCode(userId: Long, code: String): Account? =
        accounts.value.firstOrNull { it.userId == userId && it.code == code }

    override suspend fun getAccountByName(userId: Long, name: String): Account? =
        accounts.value.firstOrNull { it.userId == userId && it.name.equals(name, ignoreCase = true) }

    override suspend fun insertAccount(account: Account): Long {
        val nextId = if (account.id > 0) account.id else (accounts.value.maxOfOrNull { it.id } ?: 0L) + 1L
        accounts.value = accounts.value + account.copy(id = nextId)
        return nextId
    }

    override suspend fun insertAccounts(accounts: List<Account>) {
        accounts.forEach { insertAccount(it) }
    }

    override suspend fun updateAccount(account: Account) {
        accounts.value = accounts.value.map { if (it.id == account.id) account else it }
    }

    override suspend fun updateAccountBalance(id: Long, amountDelta: Double) {
        accounts.value = accounts.value.map {
            if (it.id == id) it.copy(currentBalance = it.currentBalance + amountDelta) else it
        }
    }

    override fun getPartiesByType(userId: Long, type: PartyType): Flow<List<Party>> =
        parties.map { list -> list.filter { it.userId == userId && it.type == type } }

    override fun getAllParties(userId: Long): Flow<List<Party>> =
        parties.map { list -> list.filter { it.userId == userId } }

    override suspend fun getPartyById(id: Long): Party? =
        parties.value.firstOrNull { it.id == id }

    override suspend fun findPartyByName(userId: Long, query: String): Party? =
        parties.value.firstOrNull { it.userId == userId && it.name.contains(query, ignoreCase = true) }

    override suspend fun insertParty(party: Party): Long {
        val nextId = if (party.id > 0) party.id else (parties.value.maxOfOrNull { it.id } ?: 0L) + 1L
        parties.value = parties.value + party.copy(id = nextId)
        return nextId
    }

    override suspend fun insertParties(parties: List<Party>) {
        parties.forEach { insertParty(it) }
    }

    override suspend fun updateParty(party: Party) {
        parties.value = parties.value.map { if (it.id == party.id) party else it }
    }

    override suspend fun updatePartyOutstanding(id: Long, delta: Double) {
        parties.value = parties.value.map {
            if (it.id == id) it.copy(outstandingAmount = it.outstandingAmount + delta) else it
        }
    }

    override fun getAllProducts(userId: Long): Flow<List<Product>> =
        products.map { list -> list.filter { it.userId == userId } }

    override suspend fun getProductById(id: Long): Product? =
        products.value.firstOrNull { it.id == id }

    override suspend fun findProductByName(userId: Long, query: String): Product? =
        products.value.firstOrNull { it.userId == userId && it.name.contains(query, ignoreCase = true) }

    override suspend fun insertProduct(product: Product): Long {
        val nextId = (products.value.maxOfOrNull { it.id } ?: 0L) + 1L
        products.value = products.value + product.copy(id = nextId)
        return nextId
    }

    override suspend fun insertProducts(products: List<Product>) {
        products.forEach { insertProduct(it) }
    }

    override suspend fun updateProduct(product: Product) {
        products.value = products.value.map { if (it.id == product.id) product else it }
    }

    override suspend fun updateProductStock(id: Long, qtyDelta: Double) {}

    override fun getAllSalesInvoices(userId: Long): Flow<List<SalesInvoice>> =
        invoices.map { list -> list.filter { it.userId == userId } }

    override suspend fun getSalesInvoiceById(id: Long): SalesInvoice? =
        invoices.value.firstOrNull { it.id == id }

    override suspend fun getInvoiceItems(invoiceId: Long): List<SalesInvoiceItem> = emptyList()

    override fun getInvoiceItemsFlow(invoiceId: Long): Flow<List<SalesInvoiceItem>> = MutableStateFlow(emptyList())

    override suspend fun insertSalesInvoice(invoice: SalesInvoice): Long {
        val nextId = if (invoice.id > 0) invoice.id else (invoices.value.maxOfOrNull { it.id } ?: 0L) + 1L
        invoices.value = invoices.value + invoice.copy(id = nextId)
        return nextId
    }

    override suspend fun insertSalesInvoiceItems(items: List<SalesInvoiceItem>) {}

    override suspend fun updateSalesInvoice(invoice: SalesInvoice) {
        invoices.value = invoices.value.map { if (it.id == invoice.id) invoice else it }
    }

    override fun getAllPurchaseBills(userId: Long): Flow<List<PurchaseBill>> =
        bills.map { list -> list.filter { it.userId == userId } }

    override suspend fun getPurchaseBillById(id: Long): PurchaseBill? =
        bills.value.firstOrNull { it.id == id }

    override suspend fun getPurchaseBillItems(billId: Long): List<PurchaseBillItem> = emptyList()

    override suspend fun insertPurchaseBill(bill: PurchaseBill): Long {
        val nextId = if (bill.id > 0) bill.id else (bills.value.maxOfOrNull { it.id } ?: 0L) + 1L
        bills.value = bills.value + bill.copy(id = nextId)
        return nextId
    }

    override suspend fun insertPurchaseBillItems(items: List<PurchaseBillItem>) {}

    override suspend fun updatePurchaseBill(bill: PurchaseBill) {
        bills.value = bills.value.map { if (it.id == bill.id) bill else it }
    }

    override fun getAllJournalEntries(userId: Long): Flow<List<JournalEntry>> =
        journals.map { list -> list.filter { it.userId == userId } }

    override suspend fun getJournalEntryById(id: Long): JournalEntry? =
        journals.value.firstOrNull { it.id == id }

    override suspend fun getJournalEntryLines(journalEntryId: Long): List<JournalEntryLine> =
        journalLines.value.filter { it.journalEntryId == journalEntryId }

    override fun getLinesForAccount(accountId: Long): Flow<List<JournalEntryLine>> =
        journalLines.map { list -> list.filter { it.accountId == accountId } }

    override fun getAccountLedgerLines(accountId: Long): Flow<List<JournalEntryLine>> =
        journalLines.map { list -> list.filter { it.accountId == accountId } }

    override suspend fun insertJournalEntry(entry: JournalEntry): Long {
        val nextId = (journals.value.maxOfOrNull { it.id } ?: 0L) + 1L
        journals.value = journals.value + entry.copy(id = nextId)
        return nextId
    }

    override suspend fun insertJournalEntryLines(lines: List<JournalEntryLine>) {
        journalLines.value = journalLines.value + lines
    }

    override fun getAllPaymentVouchers(userId: Long): Flow<List<PaymentVoucher>> =
        vouchers.map { list -> list.filter { it.userId == userId } }

    override fun getPaymentVouchersByType(userId: Long, type: VoucherType): Flow<List<PaymentVoucher>> =
        vouchers.map { list -> list.filter { it.userId == userId && it.type == type } }

    override suspend fun insertPaymentVoucher(voucher: PaymentVoucher): Long {
        val nextId = if (voucher.id > 0) voucher.id else (vouchers.value.maxOfOrNull { it.id } ?: 0L) + 1L
        vouchers.value = vouchers.value + voucher.copy(id = nextId)
        return nextId
    }

    override fun getAllReminders(userId: Long): Flow<List<PaymentReminder>> =
        reminders.map { list -> list.filter { it.userId == userId } }

    override suspend fun insertReminder(reminder: PaymentReminder): Long {
        val nextId = if (reminder.id > 0) reminder.id else (reminders.value.maxOfOrNull { it.id } ?: 0L) + 1L
        reminders.value = reminders.value + reminder.copy(id = nextId)
        return nextId
    }

    override suspend fun updateReminder(reminder: PaymentReminder) {
        reminders.value = reminders.value.map { if (it.id == reminder.id) reminder else it }
    }

    override suspend fun deleteReminder(id: Long) {
        reminders.value = reminders.value.filterNot { it.id == id }
    }

    override fun getCompanySettings(userId: Long): Flow<CompanySettings?> = settings

    override suspend fun getCompanySettingsSync(userId: Long): CompanySettings? = settings.value

    override suspend fun insertCompanySettings(settings: CompanySettings) {
        this.settings.value = settings
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ZenoVoiceAssistantTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val istTz = TimeZone.getTimeZone("Asia/Kolkata")
    private lateinit var dao: InMemoryAccountingDao
    private lateinit var repo: AccountingRepository
    private lateinit var engine: ZenoAiEngine
    private var fixedNowMillis: Long = 0L
    private var yesterdayNoonMillis: Long = 0L
    private var todayMorningMillis: Long = 0L

    private fun istMillis(year: Int, monthZeroBased: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(istTz, Locale.ENGLISH).apply {
            set(year, monthZeroBased, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Before
    fun setUp() = runBlocking {
        // 2026-11-12 10:30 IST (Thursday)
        fixedNowMillis = istMillis(2026, Calendar.NOVEMBER, 12, 10, 30)
        yesterdayNoonMillis = istMillis(2026, Calendar.NOVEMBER, 11, 14, 0)
        todayMorningMillis = istMillis(2026, Calendar.NOVEMBER, 12, 9, 15)

        dao = InMemoryAccountingDao()
        repo = AccountingRepository(dao)

        // Seed Chart of Accounts
        dao.insertAccounts(
            listOf(
                Account(id = 1, userId = 1, code = "1010", name = "Cash in Hand", category = AccountCategory.ASSET, subcategory = "Current Asset", currentBalance = 25000.0),
                Account(id = 2, userId = 1, code = "1020", name = "Primary Bank Account", category = AccountCategory.ASSET, subcategory = "Current Asset", currentBalance = 75000.0),
                Account(id = 3, userId = 1, code = "1040", name = "Accounts Receivable", category = AccountCategory.ASSET, subcategory = "Debtors", currentBalance = 12000.0),
                Account(id = 4, userId = 1, code = "2010", name = "Accounts Payable", category = AccountCategory.LIABILITY, subcategory = "Creditors", currentBalance = 4000.0),
                Account(id = 5, userId = 1, code = "4010", name = "Sales Revenue", category = AccountCategory.INCOME, subcategory = "Operating Revenue", currentBalance = 18000.0),
                Account(id = 6, userId = 1, code = "5010", name = "Cost of Goods Sold", category = AccountCategory.EXPENSE, subcategory = "Operating Expense", currentBalance = 7500.0)
            )
        )

        // Seed Customers & Suppliers
        dao.insertParties(
            listOf(
                Party(id = 10, userId = 1, name = "Rajesh Electronics", type = PartyType.CUSTOMER, outstandingAmount = 12000.0),
                Party(id = 11, userId = 1, name = "Alpha Retailers", type = PartyType.CUSTOMER, outstandingAmount = 0.0),
                Party(id = 20, userId = 1, name = "Sharma Wholesalers", type = PartyType.SUPPLIER, outstandingAmount = 4000.0)
            )
        )

        // Seed Yesterday + Today Transactions
        dao.insertSalesInvoice(
            SalesInvoice(
                id = 100, userId = 1, invoiceNumber = "INV-26/01", customerId = 10,
                customerName = "Rajesh Electronics", date = yesterdayNoonMillis,
                subtotal = 10000.0, cgst = 900.0, sgst = 900.0, totalAmount = 11800.0,
                status = InvoiceStatus.UNPAID, paymentMode = "CREDIT"
            )
        )
        dao.insertPurchaseBill(
            PurchaseBill(
                id = 200, userId = 1, billNumber = "BILL-26/01", supplierId = 20,
                supplierName = "Sharma Wholesalers", date = yesterdayNoonMillis,
                subtotal = 3000.0, cgst = 270.0, sgst = 270.0, totalAmount = 3540.0,
                status = InvoiceStatus.UNPAID, paymentMode = "CREDIT"
            )
        )
        dao.insertPaymentVoucher(
            PaymentVoucher(
                id = 300, userId = 1, voucherNumber = "PAY-26/01", type = VoucherType.PAYMENT,
                partyId = 20, partyName = "Office Rent", accountId = 1, accountName = "Cash in Hand",
                amount = 1500.0, paymentMode = "CASH", date = yesterdayNoonMillis
            )
        )
        dao.insertSalesInvoice(
            SalesInvoice(
                id = 101, userId = 1, invoiceNumber = "INV-26/02", customerId = 11,
                customerName = "Alpha Retailers", date = todayMorningMillis,
                subtotal = 5000.0, cgst = 450.0, sgst = 450.0, totalAmount = 5900.0,
                status = InvoiceStatus.PAID, paymentMode = "UPI"
            )
        )

        val app = ApplicationProvider.getApplicationContext<Application>()
        engine = ZenoAiEngine(
            context = app,
            repository = repo,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            clock = { fixedNowMillis },
            timeZoneProvider = { istTz },
            enableAndroidSpeech = false
        )
    }

    @Test
    fun intentClassifierCoversAll9IntentsAndNeverMisroutesViewOrReportQueries() {
        val cases = listOf(
            "Show me yesterday's transactions." to ZenoIntent.VIEW_TRANSACTIONS,
            "What were my total expenses yesterday?" to ZenoIntent.VIEW_EXPENSES,
            "How much income did I receive yesterday?" to ZenoIntent.VIEW_INCOME,
            "Show today's transactions." to ZenoIntent.VIEW_TRANSACTIONS,
            "Show transactions for this month." to ZenoIntent.VIEW_TRANSACTIONS,
            "What is my current balance?" to ZenoIntent.VIEW_BALANCE,
            "Give me my profit and loss report." to ZenoIntent.PROFIT_LOSS,
            "Show my pending payments." to ZenoIntent.VIEW_PENDING_PAYMENTS,
            "Which customers owe me money?" to ZenoIntent.VIEW_PENDING_PAYMENTS,
            "Show ledger for Rajesh Electronics" to ZenoIntent.VIEW_LEDGER
        )

        cases.forEach { (prompt, expected) ->
            val detected = ZenoIntentClassifier.classify(prompt)
            assertEquals("Failed on prompt: $prompt", expected, detected)
            assertNotEquals("Must NEVER classify view/report as CREATE_TRANSACTION: $prompt", ZenoIntent.CREATE_TRANSACTION, detected)
        }

        val dateRange = DateRangeParser.parse("Nov 1 to Nov 10", fixedNowMillis, istTz)
        assertNotNull(dateRange)
        assertEquals(ZenoIntent.DATE_FILTER, ZenoIntentClassifier.classify("Nov 1 to Nov 10", hasDateRange = true))
        assertEquals(ZenoIntent.CREATE_TRANSACTION, ZenoIntentClassifier.classify("Create a transaction"))
    }

    @Test
    fun dateRangeParserRecognizesRelativeAndCustomRangesInLocalTimezone() {
        val yesterday = DateRangeParser.parse("Show me yesterday's transactions", fixedNowMillis, istTz)
        assertEquals("yesterday", yesterday?.label)
        assertEquals(istMillis(2026, Calendar.NOVEMBER, 11, 0, 0), yesterday?.startMillis)

        val today = DateRangeParser.parse("Show today's transactions", fixedNowMillis, istTz)
        assertEquals("today", today?.label)
        assertEquals(istMillis(2026, Calendar.NOVEMBER, 12, 0, 0), today?.startMillis)

        val last7 = DateRangeParser.parse("Last 7 days", fixedNowMillis, istTz)
        assertEquals("last 7 days", last7?.label)
        assertEquals(istMillis(2026, Calendar.NOVEMBER, 6, 0, 0), last7?.startMillis)

        val thisWeek = DateRangeParser.parse("This week", fixedNowMillis, istTz)
        assertEquals("this week", thisWeek?.label)
        assertEquals(istMillis(2026, Calendar.NOVEMBER, 9, 0, 0), thisWeek?.startMillis) // Monday

        val lastWeek = DateRangeParser.parse("Last week", fixedNowMillis, istTz)
        assertEquals("last week", lastWeek?.label)
        assertEquals(istMillis(2026, Calendar.NOVEMBER, 2, 0, 0), lastWeek?.startMillis)

        val thisMonth = DateRangeParser.parse("Show transactions for this month", fixedNowMillis, istTz)
        assertEquals("this month", thisMonth?.label)
        assertEquals(istMillis(2026, Calendar.NOVEMBER, 1, 0, 0), thisMonth?.startMillis)

        val lastMonth = DateRangeParser.parse("Last month", fixedNowMillis, istTz)
        assertEquals("last month", lastMonth?.label)
        assertEquals(istMillis(2026, Calendar.OCTOBER, 1, 0, 0), lastMonth?.startMillis)

        val custom = DateRangeParser.parse("Show transactions from Nov 1 to Nov 10", fixedNowMillis, istTz)
        assertNotNull(custom)
        assertEquals(istMillis(2026, Calendar.NOVEMBER, 1, 0, 0), custom?.startMillis)
        assertTrue((custom?.endMillis ?: 0L) >= istMillis(2026, Calendar.NOVEMBER, 10, 23, 59))
    }

    @Test
    fun queriesReturnRealDatabaseRecordsTotalsAndEmptyStateMessages() = runBlocking {
        // 1. Yesterday's transactions (Sales 11,800 + Purchase 3,540 + Payment 1,500)
        val ydayRes = engine.processQuerySuspend("Show me yesterday's transactions.", 1L)!!
        assertEquals(ZenoIntent.VIEW_TRANSACTIONS, ydayRes.effectiveIntent)
        assertEquals(3, ydayRes.transactions.size)
        assertEquals(11800.0, ydayRes.totalIncome, 0.01)
        assertEquals(5040.0, ydayRes.totalExpenses, 0.01)
        assertNull(engine.activeTransaction.value)

        // 2. Yesterday's expenses
        val expRes = engine.processQuerySuspend("What were my total expenses yesterday?", 1L)!!
        assertEquals(ZenoIntent.VIEW_EXPENSES, expRes.effectiveIntent)
        assertEquals(5040.0, expRes.totalExpenses, 0.01)
        assertEquals(2, expRes.transactions.size)

        // 3. Yesterday's income
        val incRes = engine.processQuerySuspend("How much income did I receive yesterday?", 1L)!!
        assertEquals(ZenoIntent.VIEW_INCOME, incRes.effectiveIntent)
        assertEquals(11800.0, incRes.totalIncome, 0.01)

        // 4. Current balance (Cash 25,000 + Bank 75,000 = 100,000)
        val balRes = engine.processQuerySuspend("What is my current balance?", 1L)!!
        assertEquals(ZenoIntent.VIEW_BALANCE, balRes.effectiveIntent)
        assertEquals(25000.0, balRes.cashBalance ?: 0.0, 0.01)
        assertEquals(75000.0, balRes.bankBalance ?: 0.0, 0.01)
        assertEquals(100000.0, balRes.netAmount, 0.01)

        // 5. Profit and loss report
        val pnlRes = engine.processQuerySuspend("Give me my profit and loss report.", 1L)!!
        assertEquals(ZenoIntent.PROFIT_LOSS, pnlRes.effectiveIntent)
        assertTrue(pnlRes.totalIncome > 0.0)

        // 6. Which customers owe me money?
        val oweRes = engine.processQuerySuspend("Which customers owe me money?", 1L)!!
        assertEquals(ZenoIntent.VIEW_PENDING_PAYMENTS, oweRes.effectiveIntent)
        assertEquals(12000.0, oweRes.totalReceivables ?: 0.0, 0.01)
        assertTrue(oweRes.displayReply.contains("Rajesh Electronics"))

        // 7. Follow-up context: "Show me yesterday's transactions" -> "What about today?"
        engine.processQuerySuspend("Show me yesterday's transactions.", 1L)
        val followUpRes = engine.processQuerySuspend("What about today?", 1L)!!
        assertEquals(ZenoIntent.VIEW_TRANSACTIONS, followUpRes.effectiveIntent)
        assertEquals("today", followUpRes.dateRange?.label)
        assertEquals(1, followUpRes.transactions.size)
        assertEquals("Alpha Retailers", followUpRes.transactions.first().partyOrTitle)

        // 8. Empty yesterday returns clean natural message
        dao.invoices.value = emptyList()
        dao.bills.value = emptyList()
        dao.vouchers.value = emptyList()
        val emptyRes = engine.processQuerySuspend("Show me yesterday's transactions.", 1L)!!
        assertEquals("No transactions were found for yesterday.", emptyRes.displayReply)
        assertTrue(emptyRes.transactions.isEmpty())
    }

    @Test
    fun multiTurnCreateTransactionCapturesTypeAmountPartyAndPostsToDatabase() = runBlocking {
        val step1 = engine.processQuerySuspend("Create a transaction", 1L)!!
        assertEquals("What type of transaction? Income or Expense?", step1.displayReply)
        assertEquals(ConversationStage.AWAITING_TRANSACTION_TYPE, engine.conversationStage.value)
        assertNull(engine.activeTransaction.value)

        val step2 = engine.processQuerySuspend("Income", 1L)!!
        assertEquals(ConversationStage.AWAITING_TRANSACTION_DETAILS, engine.conversationStage.value)
        assertTrue(step2.displayReply.contains("Income", ignoreCase = true))

        val step3 = engine.processQuerySuspend("5000 from Rajesh Electronics", 1L)!!
        assertEquals(ZenoIntent.CREATE_TRANSACTION, step3.effectiveIntent)
        assertEquals(AssistantState.CONFIRMATION, engine.state.value)
        val pendingTx = engine.activeTransaction.value
        assertNotNull(pendingTx)
        assertTrue(pendingTx!!.transactionType in listOf("SALES", "RECEIPT"))
        assertEquals(5000.0, pendingTx.taxableAmount, 0.01)
        assertEquals("Rajesh Electronics", pendingTx.partyName)
    }

    @Test
    fun zenoAiScreenAndDashboardDisplayRealQueryResultsWithoutAmountPrompt() {
        composeRule.setContent {
            MyApplicationTheme {
                ZenoAiScreen(
                    engine = engine,
                    currentUserId = 1L,
                    onNavigate = {}
                )
            }
        }

        composeRule.onNodeWithTag("zeno_query_input").performTextInput("Show me yesterday's transactions")
        composeRule.onNodeWithTag("zeno_send_query_btn").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            engine.latestQueryResult.value != null
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("zeno_assistant_response_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Rajesh Electronics").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sharma Wholesalers").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("zeno_confirmation_card").assertDoesNotExist()
    }

    @Test
    fun dashboardScreenDisplaysAssistantQueryResultCardWhenResultPresent() {
        val result: AssistantQueryResult = runBlocking {
            engine.processQuerySuspend("Show me yesterday's transactions.", 1L)!!
        }

        composeRule.setContent {
            MyApplicationTheme {
                DashboardScreen(
                    accounts = dao.accounts.value,
                    customers = dao.parties.value.filter { it.type == PartyType.CUSTOMER },
                    suppliers = dao.parties.value.filter { it.type == PartyType.SUPPLIER },
                    products = emptyList(),
                    invoices = dao.invoices.value,
                    bills = dao.bills.value,
                    vouchers = dao.vouchers.value,
                    reminders = emptyList(),
                    onNavigate = {},
                    onVoiceClick = {},
                    latestAssistantResult = result,
                    onDismissAssistantResult = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dashboard_assistant_results_card").assertIsDisplayed()
        composeRule.onAllNodesWithText("Rajesh Electronics")[0].assertIsDisplayed()
    }

    @Test
    fun todayVsYesterdayExpensesAreIsolatedInAssistant() = runBlocking {
        // Initially, today has no expenses (only 1 sales invoice INV-26/02 of 5900)
        val todayEmpty = engine.processQuerySuspend("What were my total expenses today?", 1L)!!
        assertEquals(ZenoIntent.VIEW_EXPENSES, todayEmpty.effectiveIntent)
        assertEquals("today", todayEmpty.dateRange?.label)
        assertEquals(0.0, todayEmpty.totalExpenses, 0.01)
        assertEquals(0, todayEmpty.transactions.size)

        // Yesterday has 5040.0 in expenses (3540 bill + 1500 voucher)
        val ydayExp = engine.processQuerySuspend("What were my total expenses yesterday?", 1L)!!
        assertEquals(ZenoIntent.VIEW_EXPENSES, ydayExp.effectiveIntent)
        assertEquals("yesterday", ydayExp.dateRange?.label)
        assertEquals(5040.0, ydayExp.totalExpenses, 0.01)
        assertEquals(2, ydayExp.transactions.size)

        // Insert a purchase bill of 2500 for today
        dao.insertPurchaseBill(
            PurchaseBill(
                id = 205, userId = 1, billNumber = "BILL-TODAY", supplierId = 20,
                supplierName = "Sharma Wholesalers", date = todayMorningMillis,
                subtotal = 2118.64, cgst = 190.68, sgst = 190.68, totalAmount = 2500.0,
                status = InvoiceStatus.PAID, paymentMode = "CASH"
            )
        )

        // Now today's expenses must be 2500.0, completely isolated from yesterday's 5040.0
        val todayWithBill = engine.processQuerySuspend("What were my total expenses today?", 1L)!!
        assertEquals(ZenoIntent.VIEW_EXPENSES, todayWithBill.effectiveIntent)
        assertEquals("today", todayWithBill.dateRange?.label)
        assertEquals(2500.0, todayWithBill.totalExpenses, 0.01)
        assertEquals(1, todayWithBill.transactions.size)

        // Yesterday's expenses remain unchanged
        val ydayRecheck = engine.processQuerySuspend("What were my total expenses yesterday?", 1L)!!
        assertEquals(5040.0, ydayRecheck.totalExpenses, 0.01)
    }
}
