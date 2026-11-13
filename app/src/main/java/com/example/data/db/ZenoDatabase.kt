package com.example.data.db

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AccountingDao
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Converters {
    @TypeConverter
    fun fromAccountCategory(value: AccountCategory): String = value.name

    @TypeConverter
    fun toAccountCategory(value: String): AccountCategory = AccountCategory.valueOf(value)

    @TypeConverter
    fun fromPartyType(value: PartyType): String = value.name

    @TypeConverter
    fun toPartyType(value: String): PartyType = PartyType.valueOf(value)

    @TypeConverter
    fun fromInvoiceStatus(value: InvoiceStatus): String = value.name

    @TypeConverter
    fun toInvoiceStatus(value: String): InvoiceStatus = InvoiceStatus.valueOf(value)

    @TypeConverter
    fun fromVoucherType(value: VoucherType): String = value.name

    @TypeConverter
    fun toVoucherType(value: String): VoucherType = VoucherType.valueOf(value)

    @TypeConverter
    fun fromReminderStatus(value: ReminderStatus): String = value.name

    @TypeConverter
    fun toReminderStatus(value: String): ReminderStatus = ReminderStatus.valueOf(value)
}

@Database(
    entities = [
        User::class,
        Account::class,
        Party::class,
        Product::class,
        SalesInvoice::class,
        SalesInvoiceItem::class,
        PurchaseBill::class,
        PurchaseBillItem::class,
        JournalEntry::class,
        JournalEntryLine::class,
        PaymentVoucher::class,
        PaymentReminder::class,
        CompanySettings::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ZenoDatabase : RoomDatabase() {
    abstract fun accountingDao(): AccountingDao

    companion object {
        @Volatile
        private var INSTANCE: ZenoDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): ZenoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZenoDatabase::class.java,
                    "zeno_accounting_db"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    // Seed standard default admin user if database is first created
                    val defaultUser = User(
                        id = 1,
                        name = "Business Owner",
                        email = "demo@zenoai.in",
                        passwordHash = "password123",
                        companyName = "My Enterprise",
                        gstin = "",
                        stateCode = "27",
                        stateName = "Maharashtra"
                    )
                    database.accountingDao().insertUser(defaultUser)
                    initializeFreshUserAccounts(database.accountingDao(), 1, defaultUser.companyName, defaultUser.gstin, defaultUser.stateCode, defaultUser.stateName)
                }
            }
        }
    }
}

/**
 * Initializes a clean, zero-balance Chart of Accounts and Company Settings for a user.
 * ZERO demo transactions, ZERO hardcoded balances - starts cleanly at ₹0.00!
 */
suspend fun initializeFreshUserAccounts(
    dao: AccountingDao,
    userId: Long,
    companyName: String = "My Enterprise",
    gstin: String = "",
    stateCode: String = "27",
    stateName: String = "Maharashtra"
) {
    // 1. Initial Company Settings with ₹0 balances
    dao.insertCompanySettings(
        CompanySettings(
            userId = userId,
            companyName = companyName,
            tradeName = "ZENO AI",
            gstin = gstin,
            stateCode = stateCode,
            stateName = stateName,
            financialYear = "2026-2027",
            baseCurrency = "INR",
            currencySymbol = "₹"
        )
    )

    // 2. Standard Indian Double-Entry Chart of Accounts with strictly 0.0 initial balances!
    val freshZeroAccounts = listOf(
        // ASSETS (1000 - 1999) - Initial balances 0.0
        Account(userId = userId, code = "1010", name = "Cash on Hand", category = AccountCategory.ASSET, subcategory = "Current Assets", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "1020", name = "Bank Account (Current A/c)", category = AccountCategory.ASSET, subcategory = "Bank Accounts", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "1040", name = "Sundry Debtors (Receivables)", category = AccountCategory.ASSET, subcategory = "Current Assets", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "1050", name = "Input CGST", category = AccountCategory.ASSET, subcategory = "Duties & Taxes", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "1060", name = "Input SGST", category = AccountCategory.ASSET, subcategory = "Duties & Taxes", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "1070", name = "Input IGST", category = AccountCategory.ASSET, subcategory = "Duties & Taxes", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "1080", name = "Stock-in-Hand (Inventory)", category = AccountCategory.ASSET, subcategory = "Current Assets", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "1090", name = "Office Equipment", category = AccountCategory.ASSET, subcategory = "Fixed Assets", openingBalance = 0.0, currentBalance = 0.0, isSystem = false),

        // LIABILITIES (2000 - 2999) - Initial balances 0.0
        Account(userId = userId, code = "2010", name = "Sundry Creditors (Payables)", category = AccountCategory.LIABILITY, subcategory = "Current Liabilities", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "2020", name = "Output CGST", category = AccountCategory.LIABILITY, subcategory = "Duties & Taxes", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "2030", name = "Output SGST", category = AccountCategory.LIABILITY, subcategory = "Duties & Taxes", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "2040", name = "Output IGST", category = AccountCategory.LIABILITY, subcategory = "Duties & Taxes", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "2050", name = "GST Payable Account", category = AccountCategory.LIABILITY, subcategory = "Duties & Taxes", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),

        // EQUITY (3000 - 3999) - Initial balances 0.0
        Account(userId = userId, code = "3010", name = "Proprietor Capital Account", category = AccountCategory.EQUITY, subcategory = "Capital", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "3020", name = "Retained Earnings", category = AccountCategory.EQUITY, subcategory = "Reserves & Surplus", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),

        // INCOME (4000 - 4999) - Initial balances 0.0
        Account(userId = userId, code = "4010", name = "Sales Account (GST)", category = AccountCategory.INCOME, subcategory = "Direct Income", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "4020", name = "Interstate Sales Account", category = AccountCategory.INCOME, subcategory = "Direct Income", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "4030", name = "Service & Other Income", category = AccountCategory.INCOME, subcategory = "Indirect Income", openingBalance = 0.0, currentBalance = 0.0, isSystem = false),

        // EXPENSES (5000 - 5999) - Initial balances 0.0
        Account(userId = userId, code = "5010", name = "Purchases Account", category = AccountCategory.EXPENSE, subcategory = "Direct Expenses", openingBalance = 0.0, currentBalance = 0.0, isSystem = true),
        Account(userId = userId, code = "5020", name = "Rent Expense", category = AccountCategory.EXPENSE, subcategory = "Indirect Expenses", openingBalance = 0.0, currentBalance = 0.0, isSystem = false),
        Account(userId = userId, code = "5030", name = "Salaries & Wages", category = AccountCategory.EXPENSE, subcategory = "Indirect Expenses", openingBalance = 0.0, currentBalance = 0.0, isSystem = false),
        Account(userId = userId, code = "5040", name = "Electricity & Utilities", category = AccountCategory.EXPENSE, subcategory = "Indirect Expenses", openingBalance = 0.0, currentBalance = 0.0, isSystem = false),
        Account(userId = userId, code = "5050", name = "Office & General Expenses", category = AccountCategory.EXPENSE, subcategory = "Indirect Expenses", openingBalance = 0.0, currentBalance = 0.0, isSystem = false)
    )
    dao.insertAccounts(freshZeroAccounts)
}
