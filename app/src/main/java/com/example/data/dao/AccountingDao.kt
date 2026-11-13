package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountingDao {

    // === USERS ===
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Long): User?

    @Query("SELECT * FROM users ORDER BY id ASC")
    fun getAllUsers(): Flow<List<User>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    // === ACCOUNTS (Scoped by userId) ===
    @Query("SELECT * FROM accounts WHERE userId = :userId ORDER BY code ASC")
    fun getAllAccounts(userId: Long): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE userId = :userId AND category = :category ORDER BY code ASC")
    fun getAccountsByCategory(userId: Long, category: AccountCategory): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: Long): Account?

    @Query("SELECT * FROM accounts WHERE userId = :userId AND code = :code LIMIT 1")
    suspend fun getAccountByCode(userId: Long, code: String): Account?

    @Query("SELECT * FROM accounts WHERE userId = :userId AND name = :name LIMIT 1")
    suspend fun getAccountByName(userId: Long, name: String): Account?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<Account>)

    @Update
    suspend fun updateAccount(account: Account)

    @Query("UPDATE accounts SET currentBalance = currentBalance + :amountDelta WHERE id = :id")
    suspend fun updateAccountBalance(id: Long, amountDelta: Double)

    // === PARTIES (Scoped by userId) ===
    @Query("SELECT * FROM parties WHERE userId = :userId AND type = :type AND isActive = 1 ORDER BY name ASC")
    fun getPartiesByType(userId: Long, type: PartyType): Flow<List<Party>>

    @Query("SELECT * FROM parties WHERE userId = :userId ORDER BY name ASC")
    fun getAllParties(userId: Long): Flow<List<Party>>

    @Query("SELECT * FROM parties WHERE id = :id")
    suspend fun getPartyById(id: Long): Party?

    @Query("SELECT * FROM parties WHERE userId = :userId AND name LIKE '%' || :query || '%' LIMIT 1")
    suspend fun findPartyByName(userId: Long, query: String): Party?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParty(party: Party): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParties(parties: List<Party>)

    @Update
    suspend fun updateParty(party: Party)

    @Query("UPDATE parties SET outstandingAmount = outstandingAmount + :delta WHERE id = :id")
    suspend fun updatePartyOutstanding(id: Long, delta: Double)

    // === PRODUCTS & INVENTORY (Scoped by userId) ===
    @Query("SELECT * FROM products WHERE userId = :userId ORDER BY name ASC")
    fun getAllProducts(userId: Long): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Long): Product?

    @Query("SELECT * FROM products WHERE userId = :userId AND (name LIKE '%' || :query || '%' OR codeSku LIKE '%' || :query || '%') LIMIT 1")
    suspend fun findProductByName(userId: Long, query: String): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Update
    suspend fun updateProduct(product: Product)

    @Query("UPDATE products SET currentStock = currentStock + :qtyDelta WHERE id = :id")
    suspend fun updateProductStock(id: Long, qtyDelta: Double)

    // === SALES INVOICES (Scoped by userId) ===
    @Query("SELECT * FROM sales_invoices WHERE userId = :userId ORDER BY date DESC, id DESC")
    fun getAllSalesInvoices(userId: Long): Flow<List<SalesInvoice>>

    @Query("SELECT * FROM sales_invoices WHERE id = :id")
    suspend fun getSalesInvoiceById(id: Long): SalesInvoice?

    @Query("SELECT * FROM sales_invoice_items WHERE invoiceId = :invoiceId")
    suspend fun getInvoiceItems(invoiceId: Long): List<SalesInvoiceItem>

    @Query("SELECT * FROM sales_invoice_items WHERE invoiceId = :invoiceId")
    fun getInvoiceItemsFlow(invoiceId: Long): Flow<List<SalesInvoiceItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalesInvoice(invoice: SalesInvoice): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalesInvoiceItems(items: List<SalesInvoiceItem>)

    @Update
    suspend fun updateSalesInvoice(invoice: SalesInvoice)

    // === PURCHASE BILLS (Scoped by userId) ===
    @Query("SELECT * FROM purchase_bills WHERE userId = :userId ORDER BY date DESC, id DESC")
    fun getAllPurchaseBills(userId: Long): Flow<List<PurchaseBill>>

    @Query("SELECT * FROM purchase_bills WHERE id = :id")
    suspend fun getPurchaseBillById(id: Long): PurchaseBill?

    @Query("SELECT * FROM purchase_bill_items WHERE billId = :billId")
    suspend fun getPurchaseBillItems(billId: Long): List<PurchaseBillItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseBill(bill: PurchaseBill): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseBillItems(items: List<PurchaseBillItem>)

    @Update
    suspend fun updatePurchaseBill(bill: PurchaseBill)

    // === JOURNAL ENTRIES (Scoped by userId) ===
    @Query("SELECT * FROM journal_entries WHERE userId = :userId ORDER BY date DESC, id DESC")
    fun getAllJournalEntries(userId: Long): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getJournalEntryById(id: Long): JournalEntry?

    @Query("SELECT * FROM journal_entry_lines WHERE journalEntryId = :journalEntryId")
    suspend fun getJournalEntryLines(journalEntryId: Long): List<JournalEntryLine>

    @Query("SELECT * FROM journal_entry_lines WHERE accountId = :accountId ORDER BY id ASC")
    fun getLinesForAccount(accountId: Long): Flow<List<JournalEntryLine>>

    @Query("SELECT jel.*, je.date as entryDate, je.entryNumber as entryNum, je.narration as entryNarration FROM journal_entry_lines jel INNER JOIN journal_entries je ON jel.journalEntryId = je.id WHERE jel.accountId = :accountId ORDER BY je.date ASC, jel.id ASC")
    fun getAccountLedgerLines(accountId: Long): Flow<List<JournalEntryLine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntry(entry: JournalEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntryLines(lines: List<JournalEntryLine>)

    // === PAYMENT VOUCHERS (Scoped by userId) ===
    @Query("SELECT * FROM payment_vouchers WHERE userId = :userId ORDER BY date DESC, id DESC")
    fun getAllPaymentVouchers(userId: Long): Flow<List<PaymentVoucher>>

    @Query("SELECT * FROM payment_vouchers WHERE userId = :userId AND type = :type ORDER BY date DESC, id DESC")
    fun getPaymentVouchersByType(userId: Long, type: VoucherType): Flow<List<PaymentVoucher>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentVoucher(voucher: PaymentVoucher): Long

    // === REMINDERS (Scoped by userId) ===
    @Query("SELECT * FROM reminders WHERE userId = :userId ORDER BY dueDate ASC")
    fun getAllReminders(userId: Long): Flow<List<PaymentReminder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: PaymentReminder): Long

    @Update
    suspend fun updateReminder(reminder: PaymentReminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    // === SETTINGS (Scoped by userId) ===
    @Query("SELECT * FROM company_settings WHERE userId = :userId LIMIT 1")
    fun getCompanySettings(userId: Long): Flow<CompanySettings?>

    @Query("SELECT * FROM company_settings WHERE userId = :userId LIMIT 1")
    suspend fun getCompanySettingsSync(userId: Long): CompanySettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompanySettings(settings: CompanySettings)
}
