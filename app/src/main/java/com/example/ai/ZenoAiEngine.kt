package com.example.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.example.data.model.*
import com.example.data.repository.AccountingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*

enum class AssistantState {
    IDLE,
    LISTENING,
    PROCESSING,
    CONFIRMATION
}

data class ParsedVoiceTransaction(
    val transactionType: String, // "SALES", "PURCHASE", "RECEIPT", "PAYMENT", "REMINDER"
    val dateText: String = "Today",
    val timestamp: Long = System.currentTimeMillis(),
    val partyName: String = "Counter Party",
    val isInterstate: Boolean = false,
    val taxableAmount: Double = 0.0,
    val gstRate: Double = 18.0,
    val cgst: Double = 0.0,
    val sgst: Double = 0.0,
    val igst: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paymentMode: String = "CREDIT",
    val narration: String = "",
    val detectedLanguage: SupportedLanguage = SupportedLanguage.ENGLISH,
    val canonicalEnglishSummary: String = "",
    val speakerVerified: Boolean = false,
    val scheduledDateTime: Long? = null
)

class ZenoAiEngine(
    private val context: Context,
    private val repository: AccountingRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val timeZoneProvider: () -> TimeZone = { TimeZone.getDefault() },
    private val enableAndroidSpeech: Boolean = true
) : RecognitionListener, TextToSpeech.OnInitListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var ttsReady = false

    private val _state = MutableStateFlow(AssistantState.IDLE)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _activeTransaction = MutableStateFlow<ParsedVoiceTransaction?>(null)
    val activeTransaction: StateFlow<ParsedVoiceTransaction?> = _activeTransaction.asStateFlow()

    private val _feedbackText = MutableStateFlow(
        "Tap the microphone or type a command — ask about transactions, balances, P&L, pending payments, or create entries"
    )
    val feedbackText: StateFlow<String> = _feedbackText.asStateFlow()

    private val _userLanguagePreference = MutableStateFlow(SupportedLanguage.ENGLISH)
    val userLanguagePreference: StateFlow<SupportedLanguage> = _userLanguagePreference.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()
    private val _saveSuccess = MutableStateFlow<String?>(null)
    val saveSuccess: StateFlow<String?> = _saveSuccess.asStateFlow()

    private val _latestQueryResult = MutableStateFlow<AssistantQueryResult?>(null)
    val latestQueryResult: StateFlow<AssistantQueryResult?> = _latestQueryResult.asStateFlow()

    private val _conversationHistory = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val conversationHistory: StateFlow<List<ConversationMessage>> = _conversationHistory.asStateFlow()

    private val _conversationStage = MutableStateFlow(ConversationStage.NONE)
    val conversationStage: StateFlow<ConversationStage> = _conversationStage.asStateFlow()

    private val _lastDetectedIntent = MutableStateFlow<ZenoIntent?>(null)
    val lastDetectedIntent: StateFlow<ZenoIntent?> = _lastDetectedIntent.asStateFlow()

    private var lastDateRange: DateRangeFilter? = null
    private var pendingCreationNature: String? = null // "INCOME" or "EXPENSE"
    private var activeUserId: Long = 1L

    private val _speakerRecognitionEnabled = MutableStateFlow(true)
    val speakerRecognitionEnabled: StateFlow<Boolean> = _speakerRecognitionEnabled.asStateFlow()

    init {
        if (enableAndroidSpeech) {
            try {
                textToSpeech = TextToSpeech(context.applicationContext, this)
            } catch (_: Exception) {
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            updateTtsLocale(_userLanguagePreference.value)
        }
    }

    fun setLanguagePreference(lang: SupportedLanguage) {
        _userLanguagePreference.value = lang
        updateTtsLocale(lang)
    }

    fun toggleSpeakerRecognition(enabled: Boolean) {
        _speakerRecognitionEnabled.value = enabled
    }

    fun clearQueryResults() {
        _latestQueryResult.value = null
    }

    fun resetConversation() {
        _conversationStage.value = ConversationStage.NONE
        pendingCreationNature = null
        lastDateRange = null
        _lastDetectedIntent.value = null
        _latestQueryResult.value = null
        _conversationHistory.value = emptyList()
        _activeTransaction.value = null
        _recognizedText.value = ""
        _state.value = AssistantState.IDLE
        _feedbackText.value = "Tap the microphone or ask ZENO about your accounts and transactions."
    }

    private fun updateTtsLocale(lang: SupportedLanguage) {
        if (!ttsReady || textToSpeech == null) return
        try {
            val locale = when (lang) {
                SupportedLanguage.TAMIL -> Locale("ta", "IN")
                SupportedLanguage.HINDI -> Locale("hi", "IN")
                SupportedLanguage.TELUGU -> Locale("te", "IN")
                SupportedLanguage.KANNADA -> Locale("kn", "IN")
                SupportedLanguage.MALAYALAM -> Locale("ml", "IN")
                SupportedLanguage.MARATHI -> Locale("mr", "IN")
                SupportedLanguage.BENGALI -> Locale("bn", "IN")
                else -> Locale("en", "IN")
            }
            val result = textToSpeech?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech?.language = Locale("en", "IN")
            }
            textToSpeech?.setSpeechRate(0.95f)
        } catch (_: Exception) {
        }
    }

    fun speak(text: String, lang: SupportedLanguage = _userLanguagePreference.value) {
        if (ttsReady && textToSpeech != null) {
            updateTtsLocale(lang)
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zeno_speech_${System.currentTimeMillis()}")
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
    }

    fun startListening(currentUserId: Long) {
        activeUserId = currentUserId
        _isSaving.value = false
        _saveError.value = null
        _saveSuccess.value = null
        stopSpeaking()
        _recognizedText.value = ""
        _activeTransaction.value = null

        if (enableAndroidSpeech && SpeechRecognizer.isRecognitionAvailable(context)) {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                    it.setRecognitionListener(this)
                }
            } else {
                try {
                    speechRecognizer?.cancel()
                } catch (_: Exception) {
                }
            }

            val prefLang = _userLanguagePreference.value
            val primaryLocale = prefLang.localeTag
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, primaryLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primaryLocale)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-IN", "ta-IN", "hi-IN", "te-IN", "kn-IN", "ml-IN"))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
            }
            _state.value = AssistantState.LISTENING
            _feedbackText.value = "Listening in ${prefLang.displayName}... ask a question or speak a transaction"
            speechRecognizer?.startListening(intent)
        } else {
            _state.value = AssistantState.IDLE
            _feedbackText.value = "Speech recognition is unavailable on this device. Type your question or command below."
        }
    }

    fun stopListening() {
        _state.value = AssistantState.IDLE
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
    }

    fun setRecognizedTextManually(text: String) {
        _recognizedText.value = text
    }

    fun processSpeechInput(input: String, currentUserId: Long) {
        val query = input.trim()
        if (query.isEmpty()) return

        activeUserId = currentUserId
        _isSaving.value = false
        _saveError.value = null
        _saveSuccess.value = null
        _recognizedText.value = query
        _state.value = AssistantState.PROCESSING
        _feedbackText.value = "Understanding your request & checking accounting records..."

        scope.launch(Dispatchers.IO) {
            processQuerySuspend(query, currentUserId)
        }
    }

    suspend fun processQuerySuspend(input: String, currentUserId: Long): AssistantQueryResult? {
        val rawQuery = input.trim()
        if (rawQuery.isEmpty()) return null
        val cleanQuery = ZenoIntentClassifier.stripWakeWords(rawQuery)
        val now = clock()
        val tz = timeZoneProvider()

        val parsedDate = DateRangeParser.parse(cleanQuery, nowMillis = now, timeZone = tz)
        val detectedIntent = ZenoIntentClassifier.classify(
            query = cleanQuery,
            stage = _conversationStage.value,
            lastIntent = _lastDetectedIntent.value,
            hasDateRange = parsedDate != null
        )

        if (detectedIntent == ZenoIntent.CREATE_TRANSACTION) {
            return handleCreateTransactionFlow(rawQuery, cleanQuery, currentUserId, now)
        }

        // Reset creation slot if user switched to a report/view command
        _conversationStage.value = ConversationStage.NONE
        pendingCreationNature = null
        _activeTransaction.value = null

        val queryResult = executeDatabaseQuery(
            rawQuery = rawQuery,
            cleanQuery = cleanQuery,
            detectedIntent = detectedIntent,
            parsedDate = parsedDate,
            currentUserId = currentUserId,
            nowMillis = now
        )

        _latestQueryResult.value = queryResult
        _lastDetectedIntent.value = queryResult.effectiveIntent
        if (queryResult.dateRange != null) {
            lastDateRange = queryResult.dateRange
        }
        _state.value = AssistantState.IDLE
        _feedbackText.value = queryResult.displayReply
        appendConversation(rawQuery, queryResult.displayReply, queryResult.effectiveIntent.name)
        speak(queryResult.spokenReply, _userLanguagePreference.value)
        return queryResult
    }

    private suspend fun handleCreateTransactionFlow(
        rawQuery: String,
        cleanQuery: String,
        currentUserId: Long,
        nowMillis: Long
    ): AssistantQueryResult {
        val lower = cleanQuery.lowercase(Locale.ROOT)
        val amount = ZenoIntentClassifier.extractAmount(cleanQuery)
        val mentionedIncome = listOf("income", "sale", "sales", "sold", "receipt", "received", "invoice", "revenue", "varavu").any { lower.contains(it) }
        val mentionedExpense = listOf("expense", "purchase", "purchased", "bought", "payment", "paid", "rent", "salary", "selavu").any { lower.contains(it) }

        val resolvedNature = when {
            mentionedIncome && !mentionedExpense -> "INCOME"
            mentionedExpense && !mentionedIncome -> "EXPENSE"
            else -> pendingCreationNature
        }

        // Step 1: User just said "Create a transaction"
        if (resolvedNature == null && amount == null) {
            _conversationStage.value = ConversationStage.AWAITING_TRANSACTION_TYPE
            _lastDetectedIntent.value = ZenoIntent.CREATE_TRANSACTION
            _state.value = AssistantState.IDLE
            val prompt = "What type of transaction? Income or Expense?"
            val res = AssistantQueryResult(
                query = rawQuery,
                intent = ZenoIntent.CREATE_TRANSACTION,
                effectiveIntent = ZenoIntent.CREATE_TRANSACTION,
                dateRange = null,
                spokenReply = prompt,
                displayReply = prompt,
                followUpPrompt = prompt,
                timestamp = nowMillis
            )
            _latestQueryResult.value = res
            _feedbackText.value = prompt
            appendConversation(rawQuery, prompt, ZenoIntent.CREATE_TRANSACTION.name)
            speak(prompt, _userLanguagePreference.value)
            return res
        }

        // Step 2: Type chosen ("Income" or "Expense"), awaiting amount & details
        if (resolvedNature != null && amount == null) {
            pendingCreationNature = resolvedNature
            _conversationStage.value = ConversationStage.AWAITING_TRANSACTION_DETAILS
            _lastDetectedIntent.value = ZenoIntent.CREATE_TRANSACTION
            _state.value = AssistantState.IDLE
            val typeLabel = if (resolvedNature == "INCOME") "Income" else "Expense"
            val example = if (resolvedNature == "INCOME") "₹5,000 from Rajesh" else "₹2,000 for office rent"
            val prompt = "Got it — $typeLabel transaction. Please provide the amount and party or description (for example: $example)."
            val res = AssistantQueryResult(
                query = rawQuery,
                intent = ZenoIntent.CREATE_TRANSACTION,
                effectiveIntent = ZenoIntent.CREATE_TRANSACTION,
                dateRange = null,
                spokenReply = prompt,
                displayReply = prompt,
                followUpPrompt = prompt,
                timestamp = nowMillis
            )
            _latestQueryResult.value = res
            _feedbackText.value = prompt
            appendConversation(rawQuery, prompt, ZenoIntent.CREATE_TRANSACTION.name)
            speak(prompt, _userLanguagePreference.value)
            return res
        }

        // Step 3: Build confirmation transaction from natural input (+ pending nature if any)
        val effectiveInput = when (resolvedNature) {
            "INCOME" -> if (!mentionedIncome) "Sold $cleanQuery" else cleanQuery
            "EXPENSE" -> if (!mentionedExpense) "Paid $cleanQuery" else cleanQuery
            else -> cleanQuery
        }

        val analysis = MultilingualNlpProcessor.analyze(effectiveInput, _userLanguagePreference.value)
        val finalTxType = when {
            resolvedNature == "INCOME" && analysis.transactionType in listOf("PURCHASE", "PAYMENT") ->
                if (lower.contains("from") || lower.contains("receipt") || lower.contains("received")) "RECEIPT" else "SALES"
            resolvedNature == "EXPENSE" && analysis.transactionType == "SALES" ->
                if (lower.contains("bill") || lower.contains("purchase") || lower.contains("bought")) "PURCHASE" else "PAYMENT"
            else -> analysis.transactionType
        }

        val settings = repository.getCompanySettingsSync(currentUserId)
        val customers = repository.getCustomers(currentUserId).first()
        val suppliers = repository.getSuppliers(currentUserId).first()
        val matchedParty = if (finalTxType in listOf("SALES", "RECEIPT")) {
            customers.firstOrNull { c ->
                rawQuery.contains(c.name, ignoreCase = true) ||
                    analysis.englishCanonicalTranslation.contains(c.name, ignoreCase = true)
            }
        } else {
            suppliers.firstOrNull { s ->
                rawQuery.contains(s.name, ignoreCase = true) ||
                    analysis.englishCanonicalTranslation.contains(s.name, ignoreCase = true)
            }
        }

        val partyName = matchedParty?.name ?: analysis.partyName
        val isInterstate = matchedParty?.stateCode != null && matchedParty.stateCode != settings.stateCode && matchedParty.stateCode.isNotBlank()
        val taxableAmount = analysis.amount
        val gstRate = analysis.gstRate
        val totalTax = if (gstRate > 0) taxableAmount * (gstRate / 100.0) else 0.0
        val cgst = if (!isInterstate) totalTax / 2.0 else 0.0
        val sgst = if (!isInterstate) totalTax / 2.0 else 0.0
        val igst = if (isInterstate) totalTax else 0.0
        val grandTotal = taxableAmount + totalTax

        val parsed = ParsedVoiceTransaction(
            transactionType = finalTxType,
            dateText = "Today",
            timestamp = nowMillis,
            partyName = partyName,
            isInterstate = isInterstate,
            taxableAmount = taxableAmount,
            gstRate = gstRate,
            cgst = cgst,
            sgst = sgst,
            igst = igst,
            totalAmount = grandTotal,
            paymentMode = analysis.paymentMode,
            narration = "Voice Entry (${analysis.detectedLanguage.displayName}): ${analysis.englishCanonicalTranslation} | Original: $rawQuery",
            detectedLanguage = analysis.detectedLanguage,
            canonicalEnglishSummary = analysis.englishCanonicalTranslation,
            speakerVerified = _speakerRecognitionEnabled.value,
            scheduledDateTime = analysis.scheduledDateTime
        )

        _conversationStage.value = ConversationStage.NONE
        pendingCreationNature = null
        _lastDetectedIntent.value = ZenoIntent.CREATE_TRANSACTION
        _activeTransaction.value = parsed
        _state.value = AssistantState.CONFIRMATION
        _feedbackText.value = analysis.responseFeedback
        appendConversation(rawQuery, analysis.responseFeedback, ZenoIntent.CREATE_TRANSACTION.name)
        speak(analysis.responseFeedback, analysis.detectedLanguage)

        return AssistantQueryResult(
            query = rawQuery,
            intent = ZenoIntent.CREATE_TRANSACTION,
            effectiveIntent = ZenoIntent.CREATE_TRANSACTION,
            dateRange = null,
            spokenReply = analysis.responseFeedback,
            displayReply = analysis.responseFeedback,
            totalIncome = if (finalTxType in listOf("SALES", "RECEIPT")) grandTotal else 0.0,
            totalExpenses = if (finalTxType in listOf("PURCHASE", "PAYMENT")) grandTotal else 0.0,
            netAmount = grandTotal,
            timestamp = nowMillis
        )
    }

    private suspend fun executeDatabaseQuery(
        rawQuery: String,
        cleanQuery: String,
        detectedIntent: ZenoIntent,
        parsedDate: DateRangeFilter?,
        currentUserId: Long,
        nowMillis: Long
    ): AssistantQueryResult {
        val lower = cleanQuery.lowercase(Locale.ROOT)
        val effectiveIntent = if (detectedIntent == ZenoIntent.DATE_FILTER) {
            val prev = _lastDetectedIntent.value
            if (prev != null && prev != ZenoIntent.CREATE_TRANSACTION && prev != ZenoIntent.DATE_FILTER) prev else ZenoIntent.VIEW_TRANSACTIONS
        } else {
            detectedIntent
        }

        val isFollowUp = listOf("what about", "how about", "same period", "that period", "those", "and ").any { lower.contains(it) }
        val effectiveDate = parsedDate ?: if (isFollowUp && effectiveIntent != ZenoIntent.VIEW_BALANCE && effectiveIntent != ZenoIntent.VIEW_PENDING_PAYMENTS) lastDateRange else null

        val invoices = repository.getSalesInvoices(currentUserId).first()
        val bills = repository.getPurchaseBills(currentUserId).first()
        val vouchers = repository.getPaymentVouchers(currentUserId).first()
        val journals = repository.getJournalEntries(currentUserId).first()
        val accounts = repository.getAccounts(currentUserId).first()
        val customers = repository.getCustomers(currentUserId).first()
        val suppliers = repository.getSuppliers(currentUserId).first()
        val reminders = repository.getReminders(currentUserId).first()

        fun inRange(ts: Long): Boolean = effectiveDate == null || (ts >= effectiveDate.startMillis && ts <= effectiveDate.endMillis)

        val matchedInvoices = invoices.filter { inRange(it.date) }
        val matchedBills = bills.filter { inRange(it.date) }
        val matchedVouchers = vouchers.filter { inRange(it.date) }
        val matchedManualJournals = journals.filter {
            inRange(it.date) && it.sourceModule.uppercase(Locale.ROOT) in listOf("MANUAL", "JOURNAL")
        }

        val items = mutableListOf<AssistantTransactionItem>()
        matchedInvoices.forEach { inv ->
            items.add(
                AssistantTransactionItem(
                    id = "INV-${inv.id}",
                    type = "SALES",
                    category = "INCOME",
                    reference = inv.invoiceNumber,
                    partyOrTitle = inv.customerName,
                    amount = inv.totalAmount,
                    dateMillis = inv.date,
                    paymentMode = inv.paymentMode,
                    status = inv.status.name
                )
            )
        }
        matchedBills.forEach { bill ->
            items.add(
                AssistantTransactionItem(
                    id = "BILL-${bill.id}",
                    type = "PURCHASE",
                    category = "EXPENSE",
                    reference = bill.billNumber,
                    partyOrTitle = bill.supplierName,
                    amount = bill.totalAmount,
                    dateMillis = bill.date,
                    paymentMode = bill.paymentMode,
                    status = bill.status.name
                )
            )
        }
        matchedVouchers.forEach { v ->
            val isRec = v.type == VoucherType.RECEIPT
            items.add(
                AssistantTransactionItem(
                    id = "VO-${v.id}",
                    type = v.type.name,
                    category = if (isRec) "INCOME" else "EXPENSE",
                    reference = v.voucherNumber,
                    partyOrTitle = v.partyName,
                    amount = v.amount,
                    dateMillis = v.date,
                    paymentMode = v.paymentMode,
                    status = "SETTLED"
                )
            )
        }
        matchedManualJournals.forEach { j ->
            items.add(
                AssistantTransactionItem(
                    id = "JRN-${j.id}",
                    type = "JOURNAL",
                    category = "JOURNAL",
                    reference = j.entryNumber,
                    partyOrTitle = j.narration,
                    amount = j.totalDebit,
                    dateMillis = j.date,
                    paymentMode = "LEDGER",
                    status = "POSTED"
                )
            )
        }
        val sortedItems = items.sortedByDescending { it.dateMillis }

        val salesIncome = matchedInvoices.sumOf { it.totalAmount }
        val receiptInflow = matchedVouchers.filter { it.type == VoucherType.RECEIPT }.sumOf { it.amount }
        val periodIncome = if (salesIncome > 0) salesIncome else receiptInflow
        val periodExpenses = matchedBills.sumOf { it.totalAmount } +
            matchedVouchers.filter { it.type == VoucherType.PAYMENT }.sumOf { it.amount }
        val periodLabel = effectiveDate?.label ?: "all time"

        return when (effectiveIntent) {
            ZenoIntent.VIEW_TRANSACTIONS, ZenoIntent.DATE_FILTER -> {
                val reply = if (sortedItems.isEmpty()) {
                    if (effectiveDate != null) "No transactions were found for $periodLabel." else "No transactions were found in your accounting books."
                } else {
                    "Found ${sortedItems.size} transaction(s) for $periodLabel: Income ${fmt(periodIncome)}, Expenses ${fmt(periodExpenses)}."
                }
                AssistantQueryResult(
                    query = rawQuery,
                    intent = detectedIntent,
                    effectiveIntent = ZenoIntent.VIEW_TRANSACTIONS,
                    dateRange = effectiveDate,
                    spokenReply = reply,
                    displayReply = reply,
                    totalIncome = periodIncome,
                    totalExpenses = periodExpenses,
                    netAmount = periodIncome - periodExpenses,
                    transactions = sortedItems,
                    timestamp = nowMillis
                )
            }

            ZenoIntent.VIEW_INCOME -> {
                val incomeItems = sortedItems.filter { it.category == "INCOME" }
                val reply = if (incomeItems.isEmpty()) {
                    if (effectiveDate != null) "No income records were found for $periodLabel." else "No income records were found."
                } else {
                    "Total income for $periodLabel is ${fmt(periodIncome)} across ${incomeItems.size} entry(ies)."
                }
                AssistantQueryResult(
                    query = rawQuery,
                    intent = detectedIntent,
                    effectiveIntent = ZenoIntent.VIEW_INCOME,
                    dateRange = effectiveDate,
                    spokenReply = reply,
                    displayReply = reply,
                    totalIncome = periodIncome,
                    totalExpenses = 0.0,
                    netAmount = periodIncome,
                    transactions = incomeItems,
                    timestamp = nowMillis
                )
            }

            ZenoIntent.VIEW_EXPENSES -> {
                val expenseItems = sortedItems.filter { it.category == "EXPENSE" }
                val reply = if (expenseItems.isEmpty()) {
                    if (effectiveDate != null) "No expenses were found for $periodLabel." else "No expense records were found."
                } else {
                    "Total expenses for $periodLabel were ${fmt(periodExpenses)} across ${expenseItems.size} entry(ies)."
                }
                AssistantQueryResult(
                    query = rawQuery,
                    intent = detectedIntent,
                    effectiveIntent = ZenoIntent.VIEW_EXPENSES,
                    dateRange = effectiveDate,
                    spokenReply = reply,
                    displayReply = reply,
                    totalIncome = 0.0,
                    totalExpenses = periodExpenses,
                    netAmount = -periodExpenses,
                    transactions = expenseItems,
                    timestamp = nowMillis
                )
            }

            ZenoIntent.VIEW_BALANCE -> {
                val cash = accounts.filter { it.code == "1010" }.sumOf { it.currentBalance }
                val bank = accounts.filter { it.code in listOf("1020", "1030") }.sumOf { it.currentBalance }
                val totalLiquid = cash + bank
                val reply = "Your current Cash & Bank balance is ${fmt(totalLiquid)} (Cash: ${fmt(cash)}, Bank: ${fmt(bank)})."
                AssistantQueryResult(
                    query = rawQuery,
                    intent = detectedIntent,
                    effectiveIntent = ZenoIntent.VIEW_BALANCE,
                    dateRange = null,
                    spokenReply = reply,
                    displayReply = reply,
                    netAmount = totalLiquid,
                    cashBalance = cash,
                    bankBalance = bank,
                    accountsSummary = accounts.filter { it.code in listOf("1010", "1020", "1030", "1040", "2010") },
                    timestamp = nowMillis
                )
            }

            ZenoIntent.PROFIT_LOSS -> {
                val totalInc = if (effectiveDate != null) {
                    periodIncome
                } else {
                    val accInc = accounts.filter { it.category == AccountCategory.INCOME }.sumOf { it.currentBalance }
                    if (accInc > 0) accInc else periodIncome
                }
                val totalExp = if (effectiveDate != null) {
                    periodExpenses
                } else {
                    val accExp = accounts.filter { it.category == AccountCategory.EXPENSE }.sumOf { it.currentBalance }
                    if (accExp > 0) accExp else periodExpenses
                }
                val net = totalInc - totalExp
                val label = if (net >= 0) "Net Profit" else "Net Loss"
                val reply = "Profit & Loss ($periodLabel): Income ${fmt(totalInc)}, Expenses ${fmt(totalExp)}, $label ${fmt(kotlin.math.abs(net))}."
                AssistantQueryResult(
                    query = rawQuery,
                    intent = detectedIntent,
                    effectiveIntent = ZenoIntent.PROFIT_LOSS,
                    dateRange = effectiveDate,
                    spokenReply = reply,
                    displayReply = reply,
                    totalIncome = totalInc,
                    totalExpenses = totalExp,
                    netAmount = net,
                    transactions = sortedItems,
                    timestamp = nowMillis
                )
            }

            ZenoIntent.VIEW_PENDING_PAYMENTS -> {
                val custDue = customers.filter { it.outstandingAmount > 0.0 }.sortedByDescending { it.outstandingAmount }
                val suppDue = suppliers.filter { it.outstandingAmount > 0.0 }.sortedByDescending { it.outstandingAmount }
                val activeReminders = reminders.filter { it.status != ReminderStatus.SETTLED }
                val totalRec = custDue.sumOf { it.outstandingAmount }
                val totalPay = suppDue.sumOf { it.outstandingAmount }
                val customersOnly = listOf("customer", "owe me", "owes me", "receivable").any { lower.contains(it) }

                val pendingTx = custDue.map {
                    AssistantTransactionItem(
                        id = "CUST-${it.id}",
                        type = "RECEIVABLE",
                        category = "INCOME",
                        reference = it.gstin.ifBlank { "DUE" },
                        partyOrTitle = it.name,
                        amount = it.outstandingAmount,
                        dateMillis = nowMillis,
                        paymentMode = "CREDIT",
                        status = "PENDING"
                    )
                } + if (customersOnly) emptyList() else suppDue.map {
                    AssistantTransactionItem(
                        id = "SUPP-${it.id}",
                        type = "PAYABLE",
                        category = "EXPENSE",
                        reference = it.gstin.ifBlank { "PAYABLE" },
                        partyOrTitle = it.name,
                        amount = it.outstandingAmount,
                        dateMillis = nowMillis,
                        paymentMode = "CREDIT",
                        status = "PENDING"
                    )
                }

                val reply = if (customersOnly) {
                    if (custDue.isEmpty()) "No customers currently owe you money."
                    else "${custDue.size} customer(s) owe you ${fmt(totalRec)}: ${custDue.take(3).joinToString(", ") { "${it.name} (${fmt(it.outstandingAmount)})" }}."
                } else {
                    if (custDue.isEmpty() && suppDue.isEmpty() && activeReminders.isEmpty()) {
                        "You have no pending payments or overdue reminders."
                    } else {
                        "Pending Payments: Customer receivables ${fmt(totalRec)} (${custDue.size} parties), Supplier payables ${fmt(totalPay)} (${suppDue.size} parties), Reminders: ${activeReminders.size}."
                    }
                }
                AssistantQueryResult(
                    query = rawQuery,
                    intent = detectedIntent,
                    effectiveIntent = ZenoIntent.VIEW_PENDING_PAYMENTS,
                    dateRange = null,
                    spokenReply = reply,
                    displayReply = reply,
                    totalReceivables = totalRec,
                    totalPayables = totalPay,
                    netAmount = totalRec - totalPay,
                    transactions = pendingTx,
                    pendingParties = if (customersOnly) custDue else (custDue + suppDue),
                    timestamp = nowMillis
                )
            }

            ZenoIntent.VIEW_LEDGER -> {
                val matchedParty = (customers + suppliers).firstOrNull { lower.contains(it.name.lowercase(Locale.ROOT)) }
                if (matchedParty != null) {
                    val partyItems = sortedItems.filter { it.partyOrTitle.equals(matchedParty.name, ignoreCase = true) }
                    val reply = "Ledger for ${matchedParty.name}: Outstanding ${fmt(matchedParty.outstandingAmount)} across ${partyItems.size} transaction(s)."
                    AssistantQueryResult(
                        query = rawQuery,
                        intent = detectedIntent,
                        effectiveIntent = ZenoIntent.VIEW_LEDGER,
                        dateRange = effectiveDate,
                        spokenReply = reply,
                        displayReply = reply,
                        transactions = partyItems,
                        pendingParties = listOf(matchedParty),
                        timestamp = nowMillis
                    )
                } else {
                    val matchedAccount = accounts.firstOrNull { lower.contains(it.name.lowercase(Locale.ROOT)) }
                    val activeAccounts = accounts.filter { kotlin.math.abs(it.currentBalance) > 0.0 }
                    val reply = if (matchedAccount != null) {
                        "Ledger for ${matchedAccount.name} (${matchedAccount.code}): Balance is ${fmt(matchedAccount.currentBalance)}."
                    } else {
                        "Ledger Summary: ${accounts.size} total accounts, ${activeAccounts.size} with active balances."
                    }
                    AssistantQueryResult(
                        query = rawQuery,
                        intent = detectedIntent,
                        effectiveIntent = ZenoIntent.VIEW_LEDGER,
                        dateRange = effectiveDate,
                        spokenReply = reply,
                        displayReply = reply,
                        accountsSummary = if (matchedAccount != null) listOf(matchedAccount) else activeAccounts.ifEmpty { accounts.take(6) },
                        transactions = sortedItems,
                        timestamp = nowMillis
                    )
                }
            }

            ZenoIntent.CREATE_TRANSACTION -> error("Handled prior to DB query")
        }
    }

    private fun fmt(amount: Double): String = "₹" + String.format(Locale.ENGLISH, "%,.2f", amount)

    private fun appendConversation(userText: String, assistantText: String, intentLabel: String) {
        val current = _conversationHistory.value.toMutableList()
        current.add(ConversationMessage(isUser = true, text = userText, timestamp = clock()))
        current.add(ConversationMessage(isUser = false, text = assistantText, intentLabel = intentLabel, timestamp = clock()))
        _conversationHistory.value = current.takeLast(12)
    }

    fun updateActiveTransaction(updated: ParsedVoiceTransaction) {
        val totalTax = if (updated.gstRate > 0) updated.taxableAmount * (updated.gstRate / 100.0) else 0.0
        val cgst = if (!updated.isInterstate) totalTax / 2.0 else 0.0
        val sgst = if (!updated.isInterstate) totalTax / 2.0 else 0.0
        val igst = if (updated.isInterstate) totalTax else 0.0
        val grandTotal = updated.taxableAmount + totalTax

        _activeTransaction.value = updated.copy(
            cgst = cgst,
            sgst = sgst,
            igst = igst,
            totalAmount = grandTotal
        )
    }

    fun confirmTransaction(tx: ParsedVoiceTransaction, currentUserId: Long) {
        scope.launch(Dispatchers.IO) {
            _isSaving.value = true
            _saveError.value = null
            _saveSuccess.value = null
            try {
                when (tx.transactionType) {
                    "SALES" -> {
                        var cust = repository.getCustomers(currentUserId).first().firstOrNull { it.name.equals(tx.partyName, ignoreCase = true) }
                        if (cust == null) {
                            val custId = repository.addParty(
                                Party(
                                    userId = currentUserId,
                                    name = tx.partyName,
                                    type = PartyType.CUSTOMER,
                                    stateCode = if (tx.isInterstate) "29" else "27"
                                )
                            )
                            cust = Party(id = custId, userId = currentUserId, name = tx.partyName, type = PartyType.CUSTOMER)
                        }
                        val invoice = SalesInvoice(
                            userId = currentUserId,
                            invoiceNumber = "INV-${System.currentTimeMillis() % 100000}",
                            customerId = cust.id,
                            customerName = cust.name,
                            date = tx.timestamp,
                            subtotal = tx.taxableAmount,
                            cgst = tx.cgst,
                            sgst = tx.sgst,
                            igst = tx.igst,
                            totalAmount = tx.totalAmount,
                            isInterstate = tx.isInterstate,
                            status = if (tx.paymentMode == "CREDIT") InvoiceStatus.UNPAID else InvoiceStatus.PAID,
                            paymentMode = tx.paymentMode,
                            notes = tx.narration
                        )
                        val item = SalesInvoiceItem(
                            invoiceId = 0,
                            productId = 1,
                            productName = "Goods / Professional Services",
                            quantity = 1.0,
                            unitRate = tx.taxableAmount,
                            gstRate = tx.gstRate,
                            taxableAmount = tx.taxableAmount,
                            taxAmount = tx.cgst + tx.sgst + tx.igst,
                            totalAmount = tx.totalAmount
                        )
                        repository.createSalesInvoice(invoice, listOf(item))
                    }

                    "PURCHASE" -> {
                        var supp = repository.getSuppliers(currentUserId).first().firstOrNull { it.name.equals(tx.partyName, ignoreCase = true) }
                        if (supp == null) {
                            val suppId = repository.addParty(
                                Party(
                                    userId = currentUserId,
                                    name = tx.partyName,
                                    type = PartyType.SUPPLIER,
                                    stateCode = if (tx.isInterstate) "29" else "27"
                                )
                            )
                            supp = Party(id = suppId, userId = currentUserId, name = tx.partyName, type = PartyType.SUPPLIER)
                        }
                        val bill = PurchaseBill(
                            userId = currentUserId,
                            billNumber = "BILL-${System.currentTimeMillis() % 100000}",
                            supplierId = supp.id,
                            supplierName = supp.name,
                            date = tx.timestamp,
                            subtotal = tx.taxableAmount,
                            cgst = tx.cgst,
                            sgst = tx.sgst,
                            igst = tx.igst,
                            totalAmount = tx.totalAmount,
                            isInterstate = tx.isInterstate,
                            status = if (tx.paymentMode == "CREDIT") InvoiceStatus.UNPAID else InvoiceStatus.PAID,
                            paymentMode = tx.paymentMode,
                            notes = tx.narration
                        )
                        val item = PurchaseBillItem(
                            billId = 0,
                            productId = 1,
                            productName = "Purchased Materials / Inward Goods",
                            quantity = 1.0,
                            unitRate = tx.taxableAmount,
                            gstRate = tx.gstRate,
                            taxableAmount = tx.taxableAmount,
                            taxAmount = tx.cgst + tx.sgst + tx.igst,
                            totalAmount = tx.totalAmount
                        )
                        repository.createPurchaseBill(bill, listOf(item))
                    }

                    "RECEIPT", "PAYMENT" -> {
                        val isRec = tx.transactionType == "RECEIPT"
                        val parties = if (isRec) repository.getCustomers(currentUserId).first() else repository.getSuppliers(currentUserId).first()
                        val party = parties.firstOrNull { it.name.equals(tx.partyName, ignoreCase = true) }
                            ?: parties.firstOrNull()
                            ?: Party(id = 1, userId = currentUserId, name = tx.partyName, type = if (isRec) PartyType.CUSTOMER else PartyType.SUPPLIER)

                        val bankAccounts = repository.getAccounts(currentUserId).first().filter { it.category == AccountCategory.ASSET && it.code in listOf("1010", "1020") }
                        val acc = bankAccounts.firstOrNull()
                            ?: Account(id = 1, userId = currentUserId, code = "1020", name = "Bank Account", category = AccountCategory.ASSET, subcategory = "Bank Accounts")

                        val voucher = PaymentVoucher(
                            userId = currentUserId,
                            voucherNumber = "VO-${System.currentTimeMillis() % 100000}",
                            type = if (isRec) VoucherType.RECEIPT else VoucherType.PAYMENT,
                            partyId = party.id,
                            partyName = tx.partyName,
                            accountId = acc.id,
                            accountName = acc.name,
                            amount = tx.totalAmount,
                            paymentMode = tx.paymentMode,
                            date = tx.timestamp,
                            reference = "VOICE-${tx.detectedLanguage.code}",
                            notes = tx.narration
                        )
                        repository.recordVoucher(voucher)
                    }

                    "REMINDER" -> {
                        val reminder = PaymentReminder(
                            userId = currentUserId,
                            title = tx.narration.take(50),
                            partyId = 1L,
                            partyName = tx.partyName,
                            amount = tx.totalAmount,
                            dueDate = tx.scheduledDateTime ?: (clock() + 24 * 60 * 60 * 1000),
                            type = PartyType.CUSTOMER,
                            status = ReminderStatus.PENDING,
                            notes = tx.narration
                        )
                        repository.addReminder(reminder)
                    }
                }

                _isSaving.value = false
                _activeTransaction.value = null
                _state.value = AssistantState.IDLE
                val ackText = tx.detectedLanguage.defaultAck
                _saveSuccess.value = ackText
                _feedbackText.value = ackText
                speak(ackText, tx.detectedLanguage)
            } catch (e: Exception) {
                _isSaving.value = false
                _saveError.value = "Error posting transaction: ${e.message}"
                _feedbackText.value = "Error posting transaction: ${e.message}"
            }
        }
    }

    fun cancelTransaction() {
        val lang = _activeTransaction.value?.detectedLanguage ?: _userLanguagePreference.value
        _activeTransaction.value = null
        _conversationStage.value = ConversationStage.NONE
        pendingCreationNature = null
        _state.value = AssistantState.IDLE
        val cancelMsg = when (lang) {
            SupportedLanguage.TAMIL -> "ரத்து செய்யப்பட்டது. மீண்டும் பேச மைக்ரோஃபோனைத் தொடவும்."
            SupportedLanguage.TANGLISH -> "Cancel aaiduchu. Marubadiyum pesa mic ah thodunga."
            SupportedLanguage.HINDI -> "रद्द कर दिया गया। फिर से बोलने के लिए माइक दबाएं।"
            else -> "Transaction cancelled. Tap microphone or ask another question."
        }
        _feedbackText.value = cancelMsg
        speak(cancelMsg, lang)
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { _state.value = AssistantState.PROCESSING }
    override fun onError(error: Int) {
        _state.value = AssistantState.IDLE
        val msg = if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            "Microphone permission is required for voice input. You can also type your query below."
        } else {
            when (_userLanguagePreference.value) {
                SupportedLanguage.TAMIL -> "குரல் கேட்கவில்லை. மைக்ரோஃபோனைத் தொட்டு மீண்டும் பேசவும்."
                SupportedLanguage.TANGLISH -> "Kural clear ah kekala. Mic ah thottu marubadiyum pesunga."
                SupportedLanguage.HINDI -> "आवाज स्पष्ट नहीं सुनाई दी। माइक दबाकर दोबारा बोलें।"
                else -> "Could not hear audio clearly. Tap microphone or type your question below."
            }
        }
        _feedbackText.value = msg
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val spoken = matches[0]
            _recognizedText.value = spoken
            processSpeechInput(spoken, activeUserId)
        } else {
            _state.value = AssistantState.IDLE
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            _recognizedText.value = matches[0]
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun onDestroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {
        }
    }
}
