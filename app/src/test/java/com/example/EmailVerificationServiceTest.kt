package com.example

import com.example.data.auth.AuthApiService
import com.example.data.auth.EmailOtpResult
import com.example.data.auth.EmailVerificationService
import com.example.data.auth.OtpEmailDispatcher
import com.example.data.auth.VerifyOtpResult
import com.example.data.dao.AccountingDao
import com.example.data.model.*
import com.example.data.repository.AccountingRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class FakeAccountingDao : AccountingDao {
    private val users = mutableMapOf<String, User>()

    fun addUser(user: User) {
        users[user.email.lowercase()] = user
    }

    override suspend fun getUserByEmail(email: String): User? = users[email.lowercase()]
    override suspend fun getUserById(id: Long): User? = users.values.firstOrNull { it.id == id }
    override fun getAllUsers(): Flow<List<User>> = emptyFlow()
    override suspend fun insertUser(user: User): Long {
        val id = (users.size + 1).toLong()
        users[user.email.lowercase()] = user.copy(id = id)
        return id
    }
    override suspend fun updateUser(user: User) {
        users[user.email.lowercase()] = user
    }
    override fun getAllAccounts(userId: Long): Flow<List<Account>> = emptyFlow()
    override fun getAccountsByCategory(userId: Long, category: AccountCategory): Flow<List<Account>> = emptyFlow()
    override suspend fun getAccountById(id: Long): Account? = null
    override suspend fun getAccountByCode(userId: Long, code: String): Account? = null
    override suspend fun getAccountByName(userId: Long, name: String): Account? = null
    override suspend fun insertAccount(account: Account): Long = 1L
    override suspend fun insertAccounts(accounts: List<Account>) {}
    override suspend fun updateAccount(account: Account) {}
    override suspend fun updateAccountBalance(id: Long, amountDelta: Double) {}
    override fun getPartiesByType(userId: Long, type: PartyType): Flow<List<Party>> = emptyFlow()
    override fun getAllParties(userId: Long): Flow<List<Party>> = emptyFlow()
    override suspend fun getPartyById(id: Long): Party? = null
    override suspend fun findPartyByName(userId: Long, query: String): Party? = null
    override suspend fun insertParty(party: Party): Long = 1L
    override suspend fun insertParties(parties: List<Party>) {}
    override suspend fun updateParty(party: Party) {}
    override suspend fun updatePartyOutstanding(id: Long, delta: Double) {}
    override fun getAllProducts(userId: Long): Flow<List<Product>> = emptyFlow()
    override suspend fun getProductById(id: Long): Product? = null
    override suspend fun findProductByName(userId: Long, query: String): Product? = null
    override suspend fun insertProduct(product: Product): Long = 1L
    override suspend fun insertProducts(products: List<Product>) {}
    override suspend fun updateProduct(product: Product) {}
    override suspend fun updateProductStock(id: Long, qtyDelta: Double) {}
    override fun getAllSalesInvoices(userId: Long): Flow<List<SalesInvoice>> = emptyFlow()
    override suspend fun getSalesInvoiceById(id: Long): SalesInvoice? = null
    override suspend fun getInvoiceItems(invoiceId: Long): List<SalesInvoiceItem> = emptyList()
    override fun getInvoiceItemsFlow(invoiceId: Long): Flow<List<SalesInvoiceItem>> = emptyFlow()
    override suspend fun insertSalesInvoice(invoice: SalesInvoice): Long = 1L
    override suspend fun insertSalesInvoiceItems(items: List<SalesInvoiceItem>) {}
    override suspend fun updateSalesInvoice(invoice: SalesInvoice) {}
    override fun getAllPurchaseBills(userId: Long): Flow<List<PurchaseBill>> = emptyFlow()
    override suspend fun getPurchaseBillById(id: Long): PurchaseBill? = null
    override suspend fun getPurchaseBillItems(billId: Long): List<PurchaseBillItem> = emptyList()
    override suspend fun insertPurchaseBill(bill: PurchaseBill): Long = 1L
    override suspend fun insertPurchaseBillItems(items: List<PurchaseBillItem>) {}
    override suspend fun updatePurchaseBill(bill: PurchaseBill) {}
    override fun getAllJournalEntries(userId: Long): Flow<List<JournalEntry>> = emptyFlow()
    override suspend fun getJournalEntryById(id: Long): JournalEntry? = null
    override suspend fun getJournalEntryLines(journalEntryId: Long): List<JournalEntryLine> = emptyList()
    override fun getLinesForAccount(accountId: Long): Flow<List<JournalEntryLine>> = emptyFlow()
    override fun getAccountLedgerLines(accountId: Long): Flow<List<JournalEntryLine>> = emptyFlow()
    override suspend fun insertJournalEntry(entry: JournalEntry): Long = 1L
    override suspend fun insertJournalEntryLines(lines: List<JournalEntryLine>) {}
    override fun getAllPaymentVouchers(userId: Long): Flow<List<PaymentVoucher>> = emptyFlow()
    override fun getPaymentVouchersByType(userId: Long, type: VoucherType): Flow<List<PaymentVoucher>> = emptyFlow()
    override suspend fun insertPaymentVoucher(voucher: PaymentVoucher): Long = 1L
    override fun getAllReminders(userId: Long): Flow<List<PaymentReminder>> = emptyFlow()
    override suspend fun insertReminder(reminder: PaymentReminder): Long = 1L
    override suspend fun updateReminder(reminder: PaymentReminder) {}
    override suspend fun deleteReminder(id: Long) {}
    override fun getCompanySettings(userId: Long): Flow<CompanySettings?> = emptyFlow()
    override suspend fun getCompanySettingsSync(userId: Long): CompanySettings? = null
    override suspend fun insertCompanySettings(settings: CompanySettings) {}
}

class EmailVerificationServiceTest {

    private lateinit var fakeDao: FakeAccountingDao
    private var currentTimeMs: Long = 1_700_000_000_000L
    private var lastDispatchedEmail: String? = null
    private var lastDispatchedOtp: String? = null
    private lateinit var service: EmailVerificationService

    @Before
    fun setUp() {
        fakeDao = FakeAccountingDao()
        currentTimeMs = 1_700_000_000_000L
        lastDispatchedEmail = null
        lastDispatchedOtp = null
        service = EmailVerificationService(
            accountingDao = fakeDao,
            emailDispatcher = OtpEmailDispatcher { email, otpCode ->
                lastDispatchedEmail = email
                lastDispatchedOtp = otpCode
                Result.success("6-digit verification code sent to $email. Valid for 10 minutes.")
            },
            clock = { currentTimeMs }
        )
    }

    private fun createMockBackendService(
        handler: (path: String, body: String) -> Pair<Int, String>
    ): AuthApiService {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val buffer = okio.Buffer()
            request.body?.writeTo(buffer)
            val bodyStr = buffer.readUtf8()
            val (code, json) = handler(request.url.encodedPath, bodyStr)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Mock")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
        }
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        return Retrofit.Builder()
            .baseUrl("http://localhost:8000/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AuthApiService::class.java)
    }

    @Test
    fun testValidEmailFormat() {
        assertTrue(service.isValidEmailFormat("mothish@example.com"))
        assertTrue(service.isValidEmailFormat("user.name+tag@domain.co.in"))
        assertFalse(service.isValidEmailFormat("invalid-email"))
        assertFalse(service.isValidEmailFormat("user@"))
        assertFalse(service.isValidEmailFormat("@domain.com"))
    }

    @Test
    fun testDuplicateEmailCheck() = runBlocking {
        fakeDao.addUser(User(id = 1, name = "Existing User", email = "existing@zenoai.in", passwordHash = "pwd"))

        val result = service.sendVerificationOtp("existing@zenoai.in")
        assertTrue(result is EmailOtpResult.Error)
        assertTrue((result as EmailOtpResult.Error).message.contains("already exists"))
        assertNull(lastDispatchedOtp)
    }

    @Test
    fun testOtpNeverStoredInPlainText() = runBlocking {
        val sendResult = service.sendVerificationOtp("hashcheck@zenoai.in")
        assertTrue(sendResult is EmailOtpResult.OtpSent)

        val code = lastDispatchedOtp
        assertNotNull(code)
        assertEquals(6, code?.length)
        assertTrue(code!!.all { it.isDigit() })

        val record = service.activeOtps["hashcheck@zenoai.in"]
        assertNotNull(record)
        assertNotEquals(code, record!!.otpHash)
        assertEquals(64, record.otpHash.length)
        assertEquals(32, record.salt.length)
        assertEquals(service.hashOtp(code, record.salt), record.otpHash)
    }

    @Test
    fun testOtpSendAndVerificationSuccess() = runBlocking {
        assertFalse(service.isEmailVerified("newuser@zenoai.in"))

        val sendResult = service.sendVerificationOtp("newuser@zenoai.in")
        assertTrue(sendResult is EmailOtpResult.OtpSent)
        assertFalse((sendResult as EmailOtpResult.OtpSent).message.contains(lastDispatchedOtp!!))
        assertFalse(service.isEmailVerified("newuser@zenoai.in"))

        val verifyResult = service.verifyOtp("newuser@zenoai.in", lastDispatchedOtp!!)
        assertTrue(verifyResult is VerifyOtpResult.Success)
        assertTrue(service.isEmailVerified("newuser@zenoai.in"))
        assertTrue(service.consumeEmailVerification("newuser@zenoai.in"))
        assertFalse(service.isEmailVerified("newuser@zenoai.in"))
    }

    @Test
    fun testInvalidOtpDecrementsAttemptsAndInvalidatesAfterMax() = runBlocking {
        val sendResult = service.sendVerificationOtp("testattempts@zenoai.in")
        assertTrue(sendResult is EmailOtpResult.OtpSent)

        val wrongOtp = if (lastDispatchedOtp == "000000") "111111" else "000000"

        for (expectedRemaining in listOf(4, 3, 2, 1)) {
            val verifyResult = service.verifyOtp("testattempts@zenoai.in", wrongOtp)
            assertTrue(verifyResult is VerifyOtpResult.Failure)
            assertEquals(expectedRemaining, (verifyResult as VerifyOtpResult.Failure).attemptsRemaining)
            assertFalse(verifyResult.isExpired)
            assertFalse(service.isEmailVerified("testattempts@zenoai.in"))
        }

        val finalFailure = service.verifyOtp("testattempts@zenoai.in", wrongOtp)
        assertTrue(finalFailure is VerifyOtpResult.Failure)
        assertEquals(0, (finalFailure as VerifyOtpResult.Failure).attemptsRemaining)
        assertNull(service.activeOtps["testattempts@zenoai.in"])
    }

    @Test
    fun testExpiredOtpRejected() = runBlocking {
        val sendResult = service.sendVerificationOtp("expired@zenoai.in")
        assertTrue(sendResult is EmailOtpResult.OtpSent)
        val otp = lastDispatchedOtp!!

        // Advance time past 10 minutes
        currentTimeMs += (10 * 60 * 1000L) + 1L

        val verifyResult = service.verifyOtp("expired@zenoai.in", otp)
        assertTrue(verifyResult is VerifyOtpResult.Failure)
        val failure = verifyResult as VerifyOtpResult.Failure
        assertTrue(failure.isExpired)
        assertTrue(failure.message.contains("expired", ignoreCase = true))
        assertFalse(service.isEmailVerified("expired@zenoai.in"))
    }

    @Test
    fun testResendOtpCooldownEnforcedAndAllowsAfter60Seconds() = runBlocking {
        val firstSend = service.sendVerificationOtp("resend@zenoai.in")
        assertTrue(firstSend is EmailOtpResult.OtpSent)
        val firstOtp = lastDispatchedOtp!!

        // 25 seconds later -> blocked by 60-second cooldown
        currentTimeMs += 25_000L
        val tooSoon = service.sendVerificationOtp("resend@zenoai.in")
        assertTrue(tooSoon is EmailOtpResult.Error)
        assertTrue((tooSoon as EmailOtpResult.Error).message.contains("wait", ignoreCase = true))

        // At +60 seconds -> allowed
        currentTimeMs += 35_000L
        val resendSuccess = service.sendVerificationOtp("resend@zenoai.in")
        assertTrue(resendSuccess is EmailOtpResult.OtpSent)
        val secondOtp = lastDispatchedOtp!!

        val verifyNew = service.verifyOtp("resend@zenoai.in", secondOtp)
        assertTrue(verifyNew is VerifyOtpResult.Success)
        assertNotNull(firstOtp)
    }

    @Test
    fun testStandaloneLocalModeFallbackGeneratesOtpSuccessfullyWhenUnconfigured() = runBlocking {
        val unconfiguredService = EmailVerificationService(accountingDao = fakeDao)
        val result = unconfiguredService.sendVerificationOtp("nobody@zenoai.in")
        assertTrue(result is EmailOtpResult.OtpSent)
        assertTrue((result as EmailOtpResult.OtpSent).message.contains("Standalone Preview OTP"))
    }

    @Test
    fun testBackendApiIntegrationHandlesSuccessAndErrorsCleanly() = runBlocking {
        val mockApi = createMockBackendService { path, body ->
            when {
                path.endsWith("/send-otp") && body.contains("smtpfail@zenoai.in") ->
                    503 to """{"detail":"Backend SMTP email service is not configured."}"""
                path.endsWith("/send-otp") ->
                    200 to """{"status":"otp_sent","email":"apiuser@zenoai.in","message":"6-digit verification code sent to apiuser@zenoai.in.","expires_in_seconds":600,"cooldown_seconds":60}"""
                path.endsWith("/verify-otp") && body.contains("654321") ->
                    200 to """{"status":"verified","email":"apiuser@zenoai.in","message":"Email verified successfully."}"""
                path.endsWith("/verify-otp") && body.contains("999999") ->
                    400 to """{"detail":"Verification code has expired. Please request a new OTP."}"""
                path.endsWith("/verify-otp") ->
                    400 to """{"detail":"Invalid OTP code. 4 attempts remaining."}"""
                path.endsWith("/register") && body.contains("apiuser@zenoai.in") ->
                    200 to """{"id":1,"name":"API User","email":"apiuser@zenoai.in","company_name":"Zeno Corp","is_verified":true}"""
                else ->
                    403 to """{"detail":"Account cannot be activated before successful email OTP verification."}"""
            }
        }

        val apiService = EmailVerificationService(accountingDao = fakeDao, authApiService = mockApi)

        val smtpErr = apiService.sendVerificationOtp("smtpfail@zenoai.in")
        assertTrue(smtpErr is EmailOtpResult.Error)
        assertTrue((smtpErr as EmailOtpResult.Error).message.contains("SMTP", ignoreCase = true))

        val sent = apiService.sendVerificationOtp("apiuser@zenoai.in")
        assertTrue(sent is EmailOtpResult.OtpSent)

        val wrong = apiService.verifyOtp("apiuser@zenoai.in", "111111")
        assertTrue(wrong is VerifyOtpResult.Failure)
        assertEquals(4, (wrong as VerifyOtpResult.Failure).attemptsRemaining)
        assertFalse(wrong.isExpired)

        val expired = apiService.verifyOtp("apiuser@zenoai.in", "999999")
        assertTrue(expired is VerifyOtpResult.Failure)
        assertTrue((expired as VerifyOtpResult.Failure).isExpired)

        val verified = apiService.verifyOtp("apiuser@zenoai.in", "654321")
        assertTrue(verified is VerifyOtpResult.Success)
        assertTrue(apiService.isEmailVerified("apiuser@zenoai.in"))

        val regResult = apiService.syncVerifiedRegistrationWithBackend(
            name = "API User",
            email = "apiuser@zenoai.in",
            password = "password123",
            companyName = "Zeno Corp",
            gstin = ""
        )
        assertTrue(regResult.isSuccess)
    }

    @Test
    fun testRepositorySignupHashesPasswordAndSupportsLoginAndLegacyUser() = runBlocking {
        val repo = AccountingRepository(fakeDao)

        // Legacy demo user (plain password in pre-seeded Room DB) remains compatible
        fakeDao.addUser(User(id = 1, name = "Demo", email = "demo@zenoai.in", passwordHash = "password123"))
        assertTrue(repo.login("demo@zenoai.in", "password123").isSuccess)
        assertTrue(repo.login("demo@zenoai.in", "wrongpass").isFailure)

        // Newly registered user gets PBKDF2-HMAC-SHA256 password hash
        val signupRes = repo.signup(
            name = "Mothish",
            email = "mothish@zenoai.in",
            password = "securePass!99",
            companyName = "Zeno Trading",
            gstin = "",
            stateCode = "27",
            stateName = "Maharashtra"
        )
        assertTrue(signupRes.isSuccess)
        val savedUser = signupRes.getOrNull()!!
        assertTrue(savedUser.passwordHash.startsWith("pbkdf2_sha256$"))
        assertNotEquals("securePass!99", savedUser.passwordHash)

        assertTrue(repo.login("mothish@zenoai.in", "securePass!99").isSuccess)
        assertTrue(repo.login("mothish@zenoai.in", "wrongPass").isFailure)
    }
}
