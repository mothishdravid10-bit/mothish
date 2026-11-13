package com.example.data.repository

import com.example.data.dao.AccountingDao
import com.example.data.db.initializeFreshUserAccounts
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class AccountingRepository(private val dao: AccountingDao) {

    // === AUTHENTICATION & MULTI-TENANCY ===
    private fun hashPassword(rawPassword: String): String {
        val saltBytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(saltBytes)
        val salt = saltBytes.joinToString("") { "%02x".format(it) }
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(rawPassword.toCharArray(), salt.toByteArray(Charsets.UTF_8), 120_000, 256)
        val hash = factory.generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
        return "pbkdf2_sha256\$$salt\$$hash"
    }

    private fun verifyPassword(rawPassword: String, storedHash: String): Boolean {
        if (!storedHash.startsWith("pbkdf2_sha256\$")) {
            return storedHash == rawPassword
        }
        val parts = storedHash.split("$", limit = 3)
        if (parts.size != 3) return false
        val salt = parts[1]
        val expectedHex = parts[2]
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(rawPassword.toCharArray(), salt.toByteArray(Charsets.UTF_8), 120_000, 256)
        val computedHex = factory.generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
        return java.security.MessageDigest.isEqual(
            computedHex.toByteArray(Charsets.UTF_8),
            expectedHex.toByteArray(Charsets.UTF_8)
        )
    }

    suspend fun login(email: String, password: String): Result<User> {
        val user = dao.getUserByEmail(email.trim().lowercase(Locale.ROOT))
            ?: return Result.failure(Exception("No account found with this email."))
        if (!verifyPassword(password.trim(), user.passwordHash)) {
            return Result.failure(Exception("Incorrect password. Please check your credentials."))
        }
        return Result.success(user)
    }

    suspend fun signup(
        name: String,
        email: String,
        password: String,
        companyName: String,
        gstin: String,
        stateCode: String,
        stateName: String
    ): Result<User> {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val existing = dao.getUserByEmail(cleanEmail)
        if (existing != null) {
            return Result.failure(Exception("An account with $cleanEmail already exists. Please log in."))
        }

        val hashedPassword = if (password == "GOOGLE_OAUTH_USER") {
            "GOOGLE_OAUTH_USER"
        } else {
            hashPassword(password.trim())
        }

        val newUser = User(
            name = name.trim(),
            email = cleanEmail,
            passwordHash = hashedPassword,
            companyName = companyName.trim().ifEmpty { "My Enterprise" },
            gstin = gstin.trim().uppercase(Locale.ROOT),
            stateCode = stateCode.trim().ifEmpty { "27" },
            stateName = stateName.trim().ifEmpty { "Maharashtra" }
        )
        val userId = dao.insertUser(newUser)
        val savedUser = newUser.copy(id = userId)

        // Initialize fresh zero-based accounts for this new user
        initializeFreshUserAccounts(
            dao = dao,
            userId = userId,
            companyName = savedUser.companyName,
            gstin = savedUser.gstin,
            stateCode = savedUser.stateCode,
            stateName = savedUser.stateName
        )

        return Result.success(savedUser)
    }

    suspend fun loginWithGoogle(email: String, name: String): Result<User> {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val existing = dao.getUserByEmail(cleanEmail)
        if (existing != null) {
            return Result.success(existing)
        }
        // Create new user for Google Sign-in with ₹0 fresh state
        return signup(
            name = name.ifEmpty { "Google User" },
            email = cleanEmail,
            password = "GOOGLE_OAUTH_USER",
            companyName = "$name Enterprise",
            gstin = "",
            stateCode = "27",
            stateName = "Maharashtra"
        )
    }

    suspend fun syncRemoteAuthenticatedUser(
        name: String,
        email: String,
        password: String,
        companyName: String,
        gstin: String = "",
        stateCode: String = "27",
        stateName: String = "Maharashtra"
    ): User {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val existing = dao.getUserByEmail(cleanEmail)
        if (existing != null) {
            val updated = existing.copy(
                name = name.trim().ifEmpty { existing.name },
                companyName = companyName.trim().ifEmpty { existing.companyName }
            )
            if (updated != existing) {
                dao.updateUser(updated)
            }
            return updated
        }

        val hashed = hashPassword(password.trim().ifEmpty { UUID.randomUUID().toString() })
        val newUser = User(
            name = name.trim().ifEmpty { "User" },
            email = cleanEmail,
            passwordHash = hashed,
            companyName = companyName.trim().ifEmpty { "My Enterprise" },
            gstin = gstin.trim().uppercase(Locale.ROOT),
            stateCode = stateCode.trim().ifEmpty { "27" },
            stateName = stateName.trim().ifEmpty { "Maharashtra" }
        )
        val userId = dao.insertUser(newUser)
        val savedUser = newUser.copy(id = userId)
        initializeFreshUserAccounts(
            dao = dao,
            userId = userId,
            companyName = savedUser.companyName,
            gstin = savedUser.gstin,
            stateCode = savedUser.stateCode,
            stateName = savedUser.stateName
        )
        return savedUser
    }

    suspend fun getUserById(userId: Long): User? = dao.getUserById(userId)

    // === USER-SCOPED DATA FLOWS ===
    fun getAccounts(userId: Long): Flow<List<Account>> = dao.getAllAccounts(userId)
    fun getCustomers(userId: Long): Flow<List<Party>> = dao.getPartiesByType(userId, PartyType.CUSTOMER)
    fun getSuppliers(userId: Long): Flow<List<Party>> = dao.getPartiesByType(userId, PartyType.SUPPLIER)
    fun getAllParties(userId: Long): Flow<List<Party>> = dao.getAllParties(userId)
    fun getProducts(userId: Long): Flow<List<Product>> = dao.getAllProducts(userId)
    fun getSalesInvoices(userId: Long): Flow<List<SalesInvoice>> = dao.getAllSalesInvoices(userId)
    fun getPurchaseBills(userId: Long): Flow<List<PurchaseBill>> = dao.getAllPurchaseBills(userId)
    fun getJournalEntries(userId: Long): Flow<List<JournalEntry>> = dao.getAllJournalEntries(userId)
    fun getPaymentVouchers(userId: Long): Flow<List<PaymentVoucher>> = dao.getAllPaymentVouchers(userId)
    fun getReminders(userId: Long): Flow<List<PaymentReminder>> = dao.getAllReminders(userId)
    fun getCompanySettings(userId: Long): Flow<CompanySettings?> = dao.getCompanySettings(userId)

    suspend fun getCompanySettingsSync(userId: Long): CompanySettings =
        dao.getCompanySettingsSync(userId) ?: CompanySettings(userId = userId)

    suspend fun addAccount(account: Account): Long = dao.insertAccount(account)
    suspend fun updateAccount(account: Account) = dao.updateAccount(account)

    suspend fun addParty(party: Party): Long = dao.insertParty(party)
    suspend fun updateParty(party: Party) = dao.updateParty(party)

    suspend fun addProduct(product: Product): Long = dao.insertProduct(product)
    suspend fun updateProduct(product: Product) = dao.updateProduct(product)

    suspend fun addReminder(reminder: PaymentReminder): Long = dao.insertReminder(reminder)
    suspend fun updateReminder(reminder: PaymentReminder) = dao.updateReminder(reminder)
    suspend fun deleteReminder(id: Long) = dao.deleteReminder(id)

    suspend fun getInvoiceItems(invoiceId: Long): List<SalesInvoiceItem> = dao.getInvoiceItems(invoiceId)
    suspend fun getPurchaseBillItems(billId: Long): List<PurchaseBillItem> = dao.getPurchaseBillItems(billId)
    suspend fun getJournalEntryLines(entryId: Long): List<JournalEntryLine> = dao.getJournalEntryLines(entryId)

    // === DOUBLE-ENTRY SALES INVOICE CREATION ===
    suspend fun createSalesInvoice(
        invoice: SalesInvoice,
        items: List<SalesInvoiceItem>
    ): Long {
        val invoiceId = dao.insertSalesInvoice(invoice)
        val itemsWithId = items.map { it.copy(invoiceId = invoiceId) }
        dao.insertSalesInvoiceItems(itemsWithId)

        // 1. Update Product Inventory Quantities
        for (item in items) {
            dao.updateProductStock(item.productId, -item.quantity)
        }

        // 2. Update Customer Outstanding if unpaid
        if (invoice.status == InvoiceStatus.UNPAID) {
            dao.updatePartyOutstanding(invoice.customerId, invoice.totalAmount)
        }

        // 3. Post Automatic Balanced Journal Entry
        val jrnNum = "JRN-INV-${invoice.invoiceNumber}"
        val jrnId = dao.insertJournalEntry(
            JournalEntry(
                userId = invoice.userId,
                entryNumber = jrnNum,
                date = invoice.date,
                reference = invoice.invoiceNumber,
                narration = "Sales Invoice to ${invoice.customerName} (Status: ${invoice.status})",
                totalDebit = invoice.totalAmount,
                totalCredit = invoice.totalAmount,
                isAutoGenerated = true,
                sourceModule = "SALES"
            )
        )

        val jrnLines = mutableListOf<JournalEntryLine>()
        // Debit Side: Bank/Cash/Receivables
        val userAccounts = dao.getAllAccounts(invoice.userId)
        val debitAcc = (if (invoice.paymentMode == "CASH") {
            dao.getAccountByCode(invoice.userId, "1010")
        } else if (invoice.paymentMode in listOf("BANK", "UPI")) {
            dao.getAccountByCode(invoice.userId, "1020")
        } else {
            dao.getAccountByCode(invoice.userId, "1040") // Debtors
        }) ?: Account(id = 1, userId = invoice.userId, code = "1040", name = "Sundry Debtors", category = AccountCategory.ASSET, subcategory = "Current Assets")

        jrnLines.add(JournalEntryLine(journalEntryId = jrnId, accountId = debitAcc.id, accountName = debitAcc.name, isDebit = true, amount = invoice.totalAmount, description = "Receivable from ${invoice.customerName}"))
        dao.updateAccountBalance(debitAcc.id, invoice.totalAmount)

        // Credit Side: Sales Account
        val salesAcc = if (invoice.isInterstate) {
            dao.getAccountByCode(invoice.userId, "4020")
        } else {
            dao.getAccountByCode(invoice.userId, "4010")
        } ?: Account(id = 18, userId = invoice.userId, code = "4010", name = "Sales Account", category = AccountCategory.INCOME, subcategory = "Direct Income")

        jrnLines.add(JournalEntryLine(journalEntryId = jrnId, accountId = salesAcc.id, accountName = salesAcc.name, isDebit = false, amount = invoice.subtotal, description = "Sales Revenue"))
        dao.updateAccountBalance(salesAcc.id, invoice.subtotal)

        // Credit Side: Taxes (GST)
        if (invoice.isInterstate && invoice.igst > 0) {
            val igstAcc = dao.getAccountByCode(invoice.userId, "2040") ?: Account(id = 13, userId = invoice.userId, code = "2040", name = "Output IGST", category = AccountCategory.LIABILITY, subcategory = "Duties & Taxes")
            jrnLines.add(JournalEntryLine(journalEntryId = jrnId, accountId = igstAcc.id, accountName = igstAcc.name, isDebit = false, amount = invoice.igst, description = "Output IGST Liability"))
            dao.updateAccountBalance(igstAcc.id, invoice.igst)
        } else {
            if (invoice.cgst > 0) {
                val cgstAcc = dao.getAccountByCode(invoice.userId, "2020") ?: Account(id = 11, userId = invoice.userId, code = "2020", name = "Output CGST", category = AccountCategory.LIABILITY, subcategory = "Duties & Taxes")
                jrnLines.add(JournalEntryLine(journalEntryId = jrnId, accountId = cgstAcc.id, accountName = cgstAcc.name, isDebit = false, amount = invoice.cgst, description = "Output CGST Liability"))
                dao.updateAccountBalance(cgstAcc.id, invoice.cgst)
            }
            if (invoice.sgst > 0) {
                val sgstAcc = dao.getAccountByCode(invoice.userId, "2030") ?: Account(id = 12, userId = invoice.userId, code = "2030", name = "Output SGST", category = AccountCategory.LIABILITY, subcategory = "Duties & Taxes")
                jrnLines.add(JournalEntryLine(journalEntryId = jrnId, accountId = sgstAcc.id, accountName = sgstAcc.name, isDebit = false, amount = invoice.sgst, description = "Output SGST Liability"))
                dao.updateAccountBalance(sgstAcc.id, invoice.sgst)
            }
        }
        dao.insertJournalEntryLines(jrnLines)

        return invoiceId
    }

    // === DOUBLE-ENTRY PURCHASE BILL CREATION ===
    suspend fun createPurchaseBill(
        bill: PurchaseBill,
        items: List<PurchaseBillItem>
    ): Long {
        val billId = dao.insertPurchaseBill(bill)
        val itemsWithId = items.map { it.copy(billId = billId) }
        dao.insertPurchaseBillItems(itemsWithId)

        for (item in items) {
            dao.updateProductStock(item.productId, item.quantity)
        }

        if (bill.status == InvoiceStatus.UNPAID) {
            dao.updatePartyOutstanding(bill.supplierId, bill.totalAmount)
        }

        val jrnNum = "JRN-BILL-${bill.billNumber}"
        val jrnId = dao.insertJournalEntry(
            JournalEntry(
                userId = bill.userId,
                entryNumber = jrnNum,
                date = bill.date,
                reference = bill.billNumber,
                narration = "Purchase Bill from ${bill.supplierName}",
                totalDebit = bill.totalAmount,
                totalCredit = bill.totalAmount,
                isAutoGenerated = true,
                sourceModule = "PURCHASE"
            )
        )

        val jrnLines = mutableListOf<JournalEntryLine>()
        val purchaseAcc = dao.getAccountByCode(bill.userId, "5010") ?: Account(id = 21, userId = bill.userId, code = "5010", name = "Purchases Account", category = AccountCategory.EXPENSE, subcategory = "Direct Expenses")
        jrnLines.add(JournalEntryLine(journalEntryId = jrnId, accountId = purchaseAcc.id, accountName = purchaseAcc.name, isDebit = true, amount = bill.subtotal, description = "Purchases Cost"))
        dao.updateAccountBalance(purchaseAcc.id, bill.subtotal)

        if (bill.isInterstate && bill.igst > 0) {
            val igstAcc = dao.getAccountByCode(bill.userId, "1070") ?: Account(id = 7, userId = bill.userId, code = "1070", name = "Input IGST", category = AccountCategory.ASSET, subcategory = "Duties & Taxes")
            jrnLines.add(JournalEntryLine(journalEntryId = jrnId, accountId = igstAcc.id, accountName = igstAcc.name, isDebit = true, amount = bill.igst, description = "Input IGST Credit"))
            dao.updateAccountBalance(igstAcc.id, bill.igst)
        } else {
            if (bill.cgst > 0) {
                val cgstAcc = dao.getAccountByCode(bill.userId, "1050") ?: Account(id = 5, userId = bill.userId, code = "1050", name = "Input CGST", category = AccountCategory.ASSET, subcategory = "Duties & Taxes")
                jrnLines.add(JournalEntryLine(journalEntryId = jrnId, accountId = cgstAcc.id, accountName = cgstAcc.name, isDebit = true, amount = bill.cgst, description = "Input CGST Credit"))
                dao.updateAccountBalance(cgstAcc.id, bill.cgst)
            }
            if (bill.sgst > 0) {
                val sgstAcc = dao.getAccountByCode(bill.userId, "1060") ?: Account(id = 6, userId = bill.userId, code = "1060", name = "Input SGST", category = AccountCategory.ASSET, subcategory = "Duties & Taxes")
                jrnLines.add(JournalEntryLine(journalEntryId = jrnId, accountId = sgstAcc.id, accountName = sgstAcc.name, isDebit = true, amount = bill.sgst, description = "Input SGST Credit"))
                dao.updateAccountBalance(sgstAcc.id, bill.sgst)
            }
        }

        val creditAcc = (if (bill.paymentMode == "CASH") {
            dao.getAccountByCode(bill.userId, "1010")
        } else if (bill.paymentMode in listOf("BANK", "UPI")) {
            dao.getAccountByCode(bill.userId, "1020")
        } else {
            dao.getAccountByCode(bill.userId, "2010") // Creditors
        }) ?: Account(id = 10, userId = bill.userId, code = "2010", name = "Sundry Creditors", category = AccountCategory.LIABILITY, subcategory = "Current Liabilities")

        jrnLines.add(JournalEntryLine(journalEntryId = jrnId, accountId = creditAcc.id, accountName = creditAcc.name, isDebit = false, amount = bill.totalAmount, description = "Payable to ${bill.supplierName}"))
        dao.updateAccountBalance(creditAcc.id, bill.totalAmount)

        dao.insertJournalEntryLines(jrnLines)
        return billId
    }

    // === VOUCHERS (Receipt / Payment) ===
    suspend fun recordVoucher(voucher: PaymentVoucher): Long {
        val voucherId = dao.insertPaymentVoucher(voucher)

        val jrnNum = "JRN-${voucher.type}-${voucher.voucherNumber}"
        val jrnId = dao.insertJournalEntry(
            JournalEntry(
                userId = voucher.userId,
                entryNumber = jrnNum,
                date = voucher.date,
                reference = voucher.reference.ifEmpty { voucher.voucherNumber },
                narration = "${voucher.type}: ${if (voucher.type == VoucherType.RECEIPT) "Received from" else "Paid to"} ${voucher.partyName} via ${voucher.paymentMode}",
                totalDebit = voucher.amount,
                totalCredit = voucher.amount,
                isAutoGenerated = true,
                sourceModule = if (voucher.type == VoucherType.RECEIPT) "RECEIPT" else "PAYMENT"
            )
        )

        val lines = mutableListOf<JournalEntryLine>()
        if (voucher.type == VoucherType.RECEIPT) {
            lines.add(JournalEntryLine(journalEntryId = jrnId, accountId = voucher.accountId, accountName = voucher.accountName, isDebit = true, amount = voucher.amount, description = "Cash/Bank Receipt"))
            val debtorsAcc = dao.getAccountByCode(voucher.userId, "1040") ?: Account(id = 4, userId = voucher.userId, code = "1040", name = "Sundry Debtors", category = AccountCategory.ASSET, subcategory = "Current Assets")
            lines.add(JournalEntryLine(journalEntryId = jrnId, accountId = debtorsAcc.id, accountName = debtorsAcc.name, isDebit = false, amount = voucher.amount, description = "Debtor settled - ${voucher.partyName}"))

            dao.updateAccountBalance(voucher.accountId, voucher.amount)
            dao.updateAccountBalance(debtorsAcc.id, -voucher.amount)
            dao.updatePartyOutstanding(voucher.partyId, -voucher.amount)
        } else {
            val creditorsAcc = dao.getAccountByCode(voucher.userId, "2010") ?: Account(id = 10, userId = voucher.userId, code = "2010", name = "Sundry Creditors", category = AccountCategory.LIABILITY, subcategory = "Current Liabilities")
            lines.add(JournalEntryLine(journalEntryId = jrnId, accountId = creditorsAcc.id, accountName = creditorsAcc.name, isDebit = true, amount = voucher.amount, description = "Creditor settled - ${voucher.partyName}"))
            lines.add(JournalEntryLine(journalEntryId = jrnId, accountId = voucher.accountId, accountName = voucher.accountName, isDebit = false, amount = voucher.amount, description = "Fund disbursed via ${voucher.accountName}"))

            dao.updateAccountBalance(creditorsAcc.id, -voucher.amount)
            dao.updateAccountBalance(voucher.accountId, -voucher.amount)
            dao.updatePartyOutstanding(voucher.partyId, -voucher.amount)
        }

        dao.insertJournalEntryLines(lines)
        return voucherId
    }

    // === MANUAL JOURNAL ENTRY ===
    suspend fun createManualJournalEntry(
        entry: JournalEntry,
        lines: List<JournalEntryLine>
    ): Result<Long> {
        val totalDebit = lines.filter { it.isDebit }.sumOf { it.amount }
        val totalCredit = lines.filter { !it.isDebit }.sumOf { it.amount }

        if (abs(totalDebit - totalCredit) > 0.01) {
            return Result.failure(IllegalArgumentException("Double-Entry Error: Total Debit (₹$totalDebit) must equal Total Credit (₹$totalCredit)"))
        }

        val entryId = dao.insertJournalEntry(entry.copy(totalDebit = totalDebit, totalCredit = totalCredit))
        val linesWithId = lines.map { it.copy(journalEntryId = entryId) }
        dao.insertJournalEntryLines(linesWithId)

        for (line in lines) {
            val delta = if (line.isDebit) line.amount else -line.amount
            dao.updateAccountBalance(line.accountId, delta)
        }

        return Result.success(entryId)
    }
}
