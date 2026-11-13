package com.example.ai

import com.example.data.model.Account
import com.example.data.model.Party
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ZenoIntent {
    VIEW_TRANSACTIONS,
    CREATE_TRANSACTION,
    VIEW_INCOME,
    VIEW_EXPENSES,
    VIEW_BALANCE,
    PROFIT_LOSS,
    VIEW_LEDGER,
    VIEW_PENDING_PAYMENTS,
    DATE_FILTER
}

enum class ConversationStage {
    NONE,
    AWAITING_TRANSACTION_TYPE,
    AWAITING_TRANSACTION_DETAILS
}

data class DateRangeFilter(
    val label: String,
    val startMillis: Long,
    val endMillis: Long
)

data class AssistantTransactionItem(
    val id: String,
    val type: String, // "SALES", "PURCHASE", "RECEIPT", "PAYMENT", "JOURNAL", "RECEIVABLE", "PAYABLE"
    val category: String, // "INCOME", "EXPENSE", "JOURNAL"
    val reference: String,
    val partyOrTitle: String,
    val amount: Double,
    val dateMillis: Long,
    val paymentMode: String,
    val status: String
)

data class AssistantQueryResult(
    val query: String,
    val intent: ZenoIntent,
    val effectiveIntent: ZenoIntent,
    val dateRange: DateRangeFilter?,
    val spokenReply: String,
    val displayReply: String,
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netAmount: Double = 0.0,
    val cashBalance: Double? = null,
    val bankBalance: Double? = null,
    val totalReceivables: Double? = null,
    val totalPayables: Double? = null,
    val transactions: List<AssistantTransactionItem> = emptyList(),
    val pendingParties: List<Party> = emptyList(),
    val accountsSummary: List<Account> = emptyList(),
    val followUpPrompt: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ConversationMessage(
    val isUser: Boolean,
    val text: String,
    val intentLabel: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object DateRangeParser {
    private val monthMap = mapOf(
        "jan" to Calendar.JANUARY, "january" to Calendar.JANUARY,
        "feb" to Calendar.FEBRUARY, "february" to Calendar.FEBRUARY,
        "mar" to Calendar.MARCH, "march" to Calendar.MARCH,
        "apr" to Calendar.APRIL, "april" to Calendar.APRIL,
        "may" to Calendar.MAY,
        "jun" to Calendar.JUNE, "june" to Calendar.JUNE,
        "jul" to Calendar.JULY, "july" to Calendar.JULY,
        "aug" to Calendar.AUGUST, "august" to Calendar.AUGUST,
        "sep" to Calendar.SEPTEMBER, "sept" to Calendar.SEPTEMBER, "september" to Calendar.SEPTEMBER,
        "oct" to Calendar.OCTOBER, "october" to Calendar.OCTOBER,
        "nov" to Calendar.NOVEMBER, "november" to Calendar.NOVEMBER,
        "dec" to Calendar.DECEMBER, "december" to Calendar.DECEMBER
    )

    private const val MONTH_PATTERN =
        "(?:jan|january|feb|february|mar|march|apr|april|may|jun|june|jul|july|aug|august|sep|sept|september|oct|october|nov|november|dec|december)"
    private const val DATE_TOKEN =
        "(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|$MONTH_PATTERN\\s+\\d{1,2}(?:[,\\s]+\\d{4})?|\\d{1,2}\\s+$MONTH_PATTERN(?:[,\\s]+\\d{4})?)"

    private val customRangeRegex1 = Regex(
        "(?:from\\s+)?($DATE_TOKEN)\\s+(?:to|till|until|-)\\s+($DATE_TOKEN)",
        RegexOption.IGNORE_CASE
    )
    private val customRangeRegex2 = Regex(
        "between\\s+($DATE_TOKEN)\\s+and\\s+($DATE_TOKEN)",
        RegexOption.IGNORE_CASE
    )
    private val singleDateRegex = Regex(
        "(?:\\bon\\b|\\bfor\\b)\\s+($DATE_TOKEN)",
        RegexOption.IGNORE_CASE
    )

    fun parse(
        query: String,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): DateRangeFilter? {
        val lower = query.lowercase(Locale.ROOT).trim()
        val nowCal = Calendar.getInstance(timeZone, Locale.ENGLISH).apply {
            timeInMillis = nowMillis
        }
        val currentYear = nowCal.get(Calendar.YEAR)

        // 1. Custom date ranges
        listOf(customRangeRegex1, customRangeRegex2).forEach { regex ->
            val match = regex.find(query)
            if (match != null) {
                val left = parseSingleToken(match.groupValues[1], currentYear, timeZone)
                val right = parseSingleToken(match.groupValues[2], currentYear, timeZone)
                if (left != null && right != null) {
                    val (startCal, endCal) = if (left.timeInMillis <= right.timeInMillis) left to right else right to left
                    val fmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).apply { this.timeZone = timeZone }
                    val label = "${fmt.format(Date(startCal.timeInMillis))} to ${fmt.format(Date(endCal.timeInMillis))}"
                    return buildRange(label, startCal, endCal)
                }
            }
        }

        // 2. Relative dates in local timezone
        val hasToday = listOf("today", "innaiku", "இன்று", "aaj").any { lower.contains(it) }
        val hasYesterday = listOf("yesterday", "nethu", "நேற்று").any { lower.contains(it) }

        if (hasToday && !hasYesterday) {
            val today = cloneStartOfDay(nowCal)
            return buildRange("today", today, today)
        }

        if (hasYesterday && !hasToday) {
            val yday = cloneStartOfDay(nowCal).apply { add(Calendar.DAY_OF_YEAR, -1) }
            return buildRange("yesterday", yday, yday)
        }

        if (hasToday) {
            val today = cloneStartOfDay(nowCal)
            return buildRange("today", today, today)
        }

        if (listOf("last 7 days", "past 7 days", "last seven days", "7 days").any { lower.contains(it) }) {
            val start = cloneStartOfDay(nowCal).apply { add(Calendar.DAY_OF_YEAR, -6) }
            val end = cloneStartOfDay(nowCal)
            return buildRange("last 7 days", start, end)
        }

        if (lower.contains("this week")) {
            val start = cloneStartOfDay(nowCal).apply {
                val dow = get(Calendar.DAY_OF_WEEK)
                val diffFromMonday = (dow - Calendar.MONDAY + 7) % 7
                add(Calendar.DAY_OF_YEAR, -diffFromMonday)
            }
            val end = cloneStartOfDay(nowCal)
            return buildRange("this week", start, end)
        }

        if (lower.contains("last week") || lower.contains("previous week")) {
            val thisMonday = cloneStartOfDay(nowCal).apply {
                val dow = get(Calendar.DAY_OF_WEEK)
                val diffFromMonday = (dow - Calendar.MONDAY + 7) % 7
                add(Calendar.DAY_OF_YEAR, -diffFromMonday)
            }
            val lastMonday = (thisMonday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }
            val lastSunday = (thisMonday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            return buildRange("last week", lastMonday, lastSunday)
        }

        if (listOf("last month", "previous month", "pona maasam").any { lower.contains(it) }) {
            val firstPrev = cloneStartOfDay(nowCal).apply {
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -1)
            }
            val lastPrev = (firstPrev.clone() as Calendar).apply {
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            return buildRange("last month", firstPrev, lastPrev)
        }

        if (listOf("this month", "current month", "indha maasam").any { lower.contains(it) }) {
            val firstThis = cloneStartOfDay(nowCal).apply { set(Calendar.DAY_OF_MONTH, 1) }
            val today = cloneStartOfDay(nowCal)
            return buildRange("this month", firstThis, today)
        }

        // 3. Single specific date ("on Nov 10", "for 2026-11-12")
        val singleMatch = singleDateRegex.find(query)
        if (singleMatch != null) {
            val parsed = parseSingleToken(singleMatch.groupValues[1], currentYear, timeZone)
            if (parsed != null) {
                val fmt = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).apply { this.timeZone = timeZone }
                return buildRange(fmt.format(Date(parsed.timeInMillis)), parsed, parsed)
            }
        }

        return null
    }

    private fun cloneStartOfDay(source: Calendar): Calendar {
        return (source.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun buildRange(label: String, startDay: Calendar, endDay: Calendar): DateRangeFilter {
        val start = cloneStartOfDay(startDay)
        val end = (endDay.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return DateRangeFilter(label = label, startMillis = start.timeInMillis, endMillis = end.timeInMillis)
    }

    private fun parseSingleToken(token: String, defaultYear: Int, timeZone: TimeZone): Calendar? {
        val cleaned = token.trim().trimEnd(',', '.')

        // Numeric formats
        val numericPatterns = listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "dd/MM/yy", "dd-MM-yy")
        for (pat in numericPatterns) {
            try {
                val sdf = SimpleDateFormat(pat, Locale.ENGLISH).apply {
                    isLenient = false
                    this.timeZone = timeZone
                }
                val date = sdf.parse(cleaned)
                if (date != null) {
                    return Calendar.getInstance(timeZone, Locale.ENGLISH).apply {
                        time = date
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                }
            } catch (_: Exception) {
            }
        }

        // "Nov 10" or "Nov 10 2026"
        val m1 = Regex("^($MONTH_PATTERN)\\s+(\\d{1,2})(?:[,\\s]+(\\d{4}))?$", RegexOption.IGNORE_CASE).find(cleaned)
        if (m1 != null) {
            val month = monthMap[m1.groupValues[1].lowercase(Locale.ROOT)] ?: return null
            val day = m1.groupValues[2].toIntOrNull() ?: return null
            val year = m1.groupValues[3].toIntOrNull() ?: defaultYear
            return safeCalendar(year, month, day, timeZone)
        }

        // "10 Nov" or "10 Nov 2026"
        val m2 = Regex("^(\\d{1,2})\\s+($MONTH_PATTERN)(?:[,\\s]+(\\d{4}))?$", RegexOption.IGNORE_CASE).find(cleaned)
        if (m2 != null) {
            val day = m2.groupValues[1].toIntOrNull() ?: return null
            val month = monthMap[m2.groupValues[2].lowercase(Locale.ROOT)] ?: return null
            val year = m2.groupValues[3].toIntOrNull() ?: defaultYear
            return safeCalendar(year, month, day, timeZone)
        }

        return null
    }

    private fun safeCalendar(year: Int, month: Int, day: Int, timeZone: TimeZone): Calendar? {
        return try {
            Calendar.getInstance(timeZone, Locale.ENGLISH).apply {
                isLenient = false
                set(year, month, day, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
                timeInMillis // validates day of month
            }
        } catch (_: Exception) {
            null
        }
    }
}

object ZenoIntentClassifier {
    private val wakeWordRegex = Regex("^(?:hey|ok|hi|hello)?\\s*zeno[\\s,:-]*", RegexOption.IGNORE_CASE)

    fun stripWakeWords(query: String): String =
        wakeWordRegex.replace(query.trim(), "").trim()

    fun extractAmount(text: String): Double? {
        val lower = text.lowercase(Locale.ROOT)
        val tamilWords = mapOf(
            "ஆயிரம்" to 1000.0,
            "ரெண்டாயிரம்" to 2000.0,
            "இரண்டாயிரம்" to 2000.0,
            "ஐயாயிரம்" to 5000.0,
            "பத்தாயிரம்" to 10000.0
        )
        for ((word, value) in tamilWords) {
            if (lower.contains(word)) return value
        }

        val withoutGst = lower.replace(Regex("\\d+(?:\\.\\d+)?\\s*(?:%|percent|gst|சதவீதம்)"), "")
        // Remove 4-digit years / ISO dates so dates don't get mistaken for rupee amounts
        val withoutDates = withoutGst
            .replace(Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b"), "")
            .replace(Regex("\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b"), "")
        val matches = Regex("(\\d[\\d,]*(?:\\.\\d+)?)").findAll(withoutDates)
        return matches.mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }
            .filter { it > 0.0 }
            .maxOrNull()
    }

    fun isExplicitViewOrReport(lowerQuery: String): Boolean {
        val prefixes = listOf(
            "show", "view", "list", "get", "give me", "tell me", "what is", "what are",
            "what were", "how much", "which", "check", "display", "fetch", "find",
            "filter", "report", "summary", "statement", "what about", "how about", "and "
        )
        if (prefixes.any { lowerQuery.startsWith(it) }) return true

        val keywords = listOf(
            "transactions", "transaction history", "profit and loss", "profit & loss", "p&l",
            "pnl", "current balance", "bank balance", "cash balance", "pending payments",
            "owe me", "owes me", "receivables", "payables", "ledger", "total expenses",
            "total income", "what were my", "how much income", "did i receive", "did i spend"
        )
        return keywords.any { lowerQuery.contains(it) }
    }

    fun classify(
        query: String,
        stage: ConversationStage = ConversationStage.NONE,
        lastIntent: ZenoIntent? = null,
        hasDateRange: Boolean = false
    ): ZenoIntent {
        val clean = stripWakeWords(query)
        val q = clean.lowercase(Locale.ROOT)

        // Multi-turn pending creation conversation takes precedence for type/amount replies
        if (stage == ConversationStage.AWAITING_TRANSACTION_TYPE) {
            val isIncome = listOf("income", "sale", "sales", "receipt", "received", "revenue", "varavu").any { q.contains(it) }
            val isExpense = listOf("expense", "purchase", "bought", "payment", "paid", "spend", "selavu").any { q.contains(it) }
            if (isIncome || isExpense) return ZenoIntent.CREATE_TRANSACTION
        }

        if (stage == ConversationStage.AWAITING_TRANSACTION_DETAILS) {
            if (extractAmount(q) != null && !isExplicitViewOrReport(q)) {
                return ZenoIntent.CREATE_TRANSACTION
            }
        }

        // CRITICAL GUARD: Never route VIEW or REPORT queries into CREATE_TRANSACTION
        if (isExplicitViewOrReport(q)) {
            return classifyViewOrReport(q, lastIntent, hasDateRange)
        }

        // Explicit creation commands (with or without amount) or multilingual transactional entry
        val createMarkers = listOf(
            "create a transaction", "create transaction", "add transaction", "new transaction",
            "record transaction", "log transaction", "post transaction", "enter transaction",
            "sold", "purchased", "bought", "paid", "received", "remind", "reminder",
            "vithom", "vitrom", "vithaen", "vaangunom", "vaanginom", "kuduthen", "kuduthom",
            "poten", "potom", "becha", "beche", "kharida", "kharidi", "diya", "mila",
            "விற்பனை", "விற்றோம்", "கொள்முதல்", "வாங்கினோம்", "செலுத்தினோம்", "பெற்றோம்",
            "बेचा", "खरीदा", "भुगतान"
        )
        if (createMarkers.any { q.contains(it) }) {
            return ZenoIntent.CREATE_TRANSACTION
        }

        if (extractAmount(q) != null && listOf("sale", "sales", "bill", "invoice", "purchase", "expense", "rent", "salary", "payment", "receipt").any { q.contains(it) }) {
            return ZenoIntent.CREATE_TRANSACTION
        }

        return classifyViewOrReport(q, lastIntent, hasDateRange)
    }

    private fun classifyViewOrReport(
        q: String,
        lastIntent: ZenoIntent?,
        hasDateRange: Boolean
    ): ZenoIntent {
        return when {
            listOf("profit and loss", "profit & loss", "profit or loss", "p&l", "pnl", "net profit", "profitability", "loss report").any { q.contains(it) } ->
                ZenoIntent.PROFIT_LOSS

            listOf("pending payment", "owe me", "owes me", "receivable", "payable", "overdue", "due from", "unpaid", "who owes").any { q.contains(it) } ->
                ZenoIntent.VIEW_PENDING_PAYMENTS

            listOf("ledger", "account statement", "party statement").any { q.contains(it) } ->
                ZenoIntent.VIEW_LEDGER

            listOf("current balance", "cash balance", "bank balance", "my balance", "how much balance", "available balance", "cash and bank").any { q.contains(it) } ->
                ZenoIntent.VIEW_BALANCE

            listOf("expense", "expenses", "spent", "spend", "spending", "purchases", "purchase bill", "cost", "costs", "outflow").any { q.contains(it) } ->
                ZenoIntent.VIEW_EXPENSES

            listOf("income", "revenue", "earned", "earn", "earnings", "sales", "receipts", "inflow").any { q.contains(it) } ->
                ZenoIntent.VIEW_INCOME

            listOf("transaction", "transactions", "entries", "history", "activity", "records").any { q.contains(it) } ->
                ZenoIntent.VIEW_TRANSACTIONS

            hasDateRange ->
                ZenoIntent.DATE_FILTER

            lastIntent != null && lastIntent != ZenoIntent.CREATE_TRANSACTION ->
                lastIntent

            else -> ZenoIntent.VIEW_TRANSACTIONS
        }
    }
}
