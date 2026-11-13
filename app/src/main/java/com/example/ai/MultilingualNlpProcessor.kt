package com.example.ai

import java.util.Locale
import java.util.Calendar
import java.util.regex.Pattern

enum class SupportedLanguage(
    val code: String,
    val displayName: String,
    val localeTag: String,
    val defaultAck: String
) {
    ENGLISH("en", "English", "en-IN", "Transaction recorded in accounting ledger"),
    TAMIL("ta", "Tamil (தமிழ்)", "ta-IN", "கணக்குப் பதிவேட்டில் வெற்றிகரமாக சேர்க்கப்பட்டது"),
    TANGLISH("ta-Latn", "Tanglish (Tamil in English)", "en-IN", "Kanakkil vetrigaramaaga pathivu seyyappattathu"),
    HINDI("hi", "Hindi (हिन्दी)", "hi-IN", "बहीखाता में सफलतापूर्वक दर्ज किया गया"),
    TELUGU("te", "Telugu (తెలుగు)", "te-IN", "ఖాతా పుస్తకంలో విజయవంతంగా నమోదు చేయబడింది"),
    KANNADA("kn", "Kannada (ಕನ್ನಡ)", "kn-IN", "ಲೆಕ್ಕಪತ್ರದಲ್ಲಿ ಯಶಸ್ವಿಯಾಗಿ ದಾಖಲಿಸಲಾಗಿದೆ"),
    MALAYALAM("ml", "Malayalam (മലയാളം)", "ml-IN", "അക്കൗണ്ടിംഗ് ലെഡ്ജറിൽ വിജയകരമായി രേഖപ്പെടുത്തി"),
    MARATHI("mr", "Marathi (मराठी)", "mr-IN", "लेखापरीक्षणामध्ये यशस्वीरित्या नोंदवले गेले"),
    BENGALI("bn", "Bengali (বাংলা)", "bn-IN", "হিসাবরক্ষণ লেজারে সফলভাবে রেকর্ড করা হয়েছে")
}

data class MultilingualAnalysisResult(
    val detectedLanguage: SupportedLanguage,
    val rawInput: String,
    val englishCanonicalTranslation: String,
    val transactionType: String, // "SALES", "PURCHASE", "RECEIPT", "PAYMENT"
    val partyName: String,
    val amount: Double,
    val gstRate: Double,
    val paymentMode: String, // "CASH", "BANK", "CREDIT"
    val responseFeedback: String,
    val scheduledDateTime: Long? = null,
    val confidenceScore: Float = 0.95f
)

/**
 * Intelligent Multilingual Voice & NLP Engine for ZENO AI.
 * 
 * Supports:
 * - Pure Tamil (தமிழ்)
 * - Tanglish (Tamil spoken/written in Latin script, e.g. "Murugan ku 5000 sales bill poten")
 * - Hindi & Hinglish (हिन्दी, "Sharma ji ko 15000 ka maal becha 18% gst")
 * - English ("Sold electronic items to Rajesh for 25000 with 18% GST")
 * - Mixed language code-switching within sentences.
 * 
 * Always normalizes and stores the transaction canonically in clear, standard English
 * for accurate audit trails and double-entry accounting records, while generating response
 * speech in the user's preferred or detected language.
 */
object MultilingualNlpProcessor {

    fun analyze(input: String, userPreferredLanguage: SupportedLanguage? = null): MultilingualAnalysisResult {
        val trimmed = input.trim()
        val detected = detectLanguage(trimmed, userPreferredLanguage)
        val englishCanonical = translateToEnglishCanonical(trimmed, detected)

        val txType = extractTransactionType(trimmed, englishCanonical)
        val amount = extractAmount(trimmed) ?: extractAmount(englishCanonical) ?: 0.0
        val gstRate = extractGstRate(trimmed) ?: extractGstRate(englishCanonical) ?: 0.0
        val paymentMode = extractPaymentMode(trimmed, englishCanonical)
        val partyName = extractPartyName(trimmed, englishCanonical, txType)
        val scheduledDateTime = extractDate(trimmed) ?: extractDate(englishCanonical)

        val responseFeedback = generateResponse(detected, txType, partyName, amount, gstRate)

        return MultilingualAnalysisResult(
            detectedLanguage = detected,
            rawInput = trimmed,
            englishCanonicalTranslation = englishCanonical,
            transactionType = txType,
            partyName = partyName,
            amount = amount,
            gstRate = gstRate,
            paymentMode = paymentMode,
            responseFeedback = responseFeedback,
            scheduledDateTime = scheduledDateTime
        )
    }

    private fun detectLanguage(text: String, preferred: SupportedLanguage?): SupportedLanguage {
        val lower = text.lowercase(Locale.ROOT)

        // Tamil Unicode Range 0x0B80 - 0x0BFF
        val containsTamilScript = text.any { it in '\u0B80'..'\u0BFF' }
        if (containsTamilScript) return SupportedLanguage.TAMIL

        // Devanagari Unicode Range 0x0900 - 0x097F
        val containsDevanagari = text.any { it in '\u0900'..'\u097F' }
        if (containsDevanagari) return SupportedLanguage.HINDI

        // Telugu Range 0x0C00 - 0x0C7F
        if (text.any { it in '\u0C00'..'\u0C7F' }) return SupportedLanguage.TELUGU
        // Kannada Range 0x0C80 - 0x0CFF
        if (text.any { it in '\u0C80'..'\u0CFF' }) return SupportedLanguage.KANNADA
        // Malayalam Range 0x0D00 - 0x0D7F
        if (text.any { it in '\u0D00'..'\u0D7F' }) return SupportedLanguage.MALAYALAM
        // Bengali Range 0x0980 - 0x09FF
        if (text.any { it in '\u0980'..'\u09FF' }) return SupportedLanguage.BENGALI

        // Check for Tanglish keyword markers
        val tanglishKeywords = listOf(
            "vithom", "vitrom", "vithathu", "vithaen", "vaangunom", "vaanginom", "vaanguna",
            "kuduthen", "kuduthom", "koduthen", "vanthathu", "vandhuchu", "panam", "kaasu",
            "rooba", "roobai", "poten", "potom", "serunga", "edunga", "selavu", "varavu",
            "vilpanai", "kolmuthal", "billu", "karan", "ku", "kitta", "irunthu", "kitta irunthu"
        )
        if (tanglishKeywords.any { lower.contains(it) }) {
            return SupportedLanguage.TANGLISH
        }

        // Check for Hinglish keyword markers
        val hinglishKeywords = listOf(
            "becha", "beche", "bikri", "kharida", "kharid", "diya", "diye", "mila", "mile",
            "aaya", "jama", "kharcha", "bhara", "bheja", "rupaye", "rupiya", "ka bill", "se mila", "ko diya"
        )
        if (hinglishKeywords.any { lower.contains(it) }) {
            return SupportedLanguage.HINDI
        }

        return preferred ?: SupportedLanguage.ENGLISH
    }

    fun translateToEnglishCanonical(raw: String, lang: SupportedLanguage): String {
        var text = raw.trim()

        when (lang) {
            SupportedLanguage.TAMIL -> {
                // Common Tamil accounting phrase replacements
                text = text
                    .replace("விற்பனை செய்தோம்", "Sold goods")
                    .replace("விற்பனை", "Sale of goods")
                    .replace("விற்றோம்", "Sold goods")
                    .replace("விற்றேன்", "Sold goods")
                    .replace("கொள்முதல் செய்தோம்", "Purchased goods")
                    .replace("கொள்முதல்", "Purchase of inward goods")
                    .replace("வாங்கினோம்", "Purchased goods")
                    .replace("வாங்கினேன்", "Purchased goods")
                    .replace("பெற்றோம்", "Received money")
                    .replace("பணம் வந்தது", "Received payment")
                    .replace("செலுத்தினோம்", "Paid expenses")
                    .replace("கொடுத்தோம்", "Paid expenses")
                    .replace("ரூபாய்", "Rupees")
                    .replace("ரூ", "Rs.")
                    .replace("ரொக்கம்", "by Cash")
                    .replace("வங்கி", "via Bank")
            }

            SupportedLanguage.TANGLISH -> {
                text = text
                    .replace(Regex("(?i)\\b(vithom|vitrom|vithaen|vithathu)\\b"), "Sold goods to")
                    .replace(Regex("(?i)\\b(vilpanai|sales poten)\\b"), "Sales invoice for")
                    .replace(Regex("(?i)\\b(vaangunom|vaanginom|vaangina)\\b"), "Purchased inward goods from")
                    .replace(Regex("(?i)\\b(kolmuthal|purchase bill)\\b"), "Purchase bill from")
                    .replace(Regex("(?i)\\b(vanthathu|vandhuchu|vaanginom)\\b"), "Received payment from")
                    .replace(Regex("(?i)\\b(kuduthen|kuduthom|koduthen|pay panninom)\\b"), "Paid payment to")
                    .replace(Regex("(?i)\\b(rooba|roobai|rubai)\\b"), "Rupees")
                    .replace(Regex("(?i)\\b(kaasu|rokka)\\b"), "in Cash")
                    .replace(Regex("(?i)\\b(vangi|accountla)\\b"), "via Bank")
            }

            SupportedLanguage.HINDI -> {
                text = text
                    .replace("बेचा", "Sold goods to")
                    .replace("बिक्री", "Sale invoice for")
                    .replace("खरीदा", "Purchased goods from")
                    .replace("खरीद", "Purchase bill from")
                    .replace("भुगतान मिला", "Received payment from")
                    .replace("प्राप्त हुआ", "Received payment from")
                    .replace("भुगतान किया", "Paid payment to")
                    .replace("खर्च", "Expense payment for")
                    .replace("रुपये", "Rupees")
                    .replace("नकद", "in Cash")
                    .replace("बैंक", "via Bank")
                    // Hinglish Latin phrases
                    .replace(Regex("(?i)\\b(becha|beche|bikri ki)\\b"), "Sold goods to")
                    .replace(Regex("(?i)\\b(kharida|kharidi)\\b"), "Purchased inward goods from")
                    .replace(Regex("(?i)\\b(mila|aaya|jama hua)\\b"), "Received payment from")
                    .replace(Regex("(?i)\\b(diya|bhara|chukaaya)\\b"), "Paid payment to")
                    .replace(Regex("(?i)\\b(rupaye|rupiya)\\b"), "Rupees")
                    .replace(Regex("(?i)\\b(rokad|nakad)\\b"), "in Cash")
            }

            SupportedLanguage.TELUGU -> {
                text = text
                    .replace("అమ్మకం", "Sale of goods")
                    .replace("కొనుగోలు", "Purchase of goods")
                    .replace("రూపాయలు", "Rupees")
            }

            SupportedLanguage.KANNADA -> {
                text = text
                    .replace("ಮಾರಾಟ", "Sale of goods")
                    .replace("ಖರೀದಿ", "Purchase of goods")
                    .replace("ರೂಪಾಯಿ", "Rupees")
            }

            SupportedLanguage.MALAYALAM -> {
                text = text
                    .replace("വിൽപ്പന", "Sale of goods")
                    .replace("വാങ്ങി", "Purchase of goods")
                    .replace("രൂപ", "Rupees")
            }

            SupportedLanguage.MARATHI -> {
                text = text
                    .replace("विकले", "Sold goods")
                    .replace("खरेदी", "Purchased goods")
                    .replace("मिळाले", "Received money")
                    .replace("रुपये", "Rupees")
            }
            SupportedLanguage.BENGALI -> {
                text = text
                    .replace("বিক্রি", "Sold goods")
                    .replace("ক্রয়", "Purchased goods")
                    .replace("পাওয়া", "Received money")
                    .replace("টাকা", "Rupees")
            }
            SupportedLanguage.ENGLISH -> {
                // Already in English
            }
        }

        return text.trim()
    }

    private fun extractTransactionType(raw: String, english: String): String {
        val lower = (raw + " " + english).lowercase(Locale.ROOT)

        val salesMarkers = listOf(
            "sold", "sale", "sales", "invoice", "vith", "vilpanai", "becha", "bikri", "ammakam", "maarata", "vilppana"
        )
        val purchaseMarkers = listOf(
            "purchase", "purchased", "bought", "buy", "bill", "vaang", "kolmuthal", "kharid", "konugolu", "kharidi"
        )
        val receiptMarkers = listOf(
            "receipt", "received", "collection", "collected", "got", "vanth", "vandh", "mila", "aaya", "jama"
        )
        val paymentMarkers = listOf(
            "payment", "paid", "rent", "salary", "expense", "kuduth", "koduth", "diya", "bhara", "chukaaya"
        )
        val reminderMarkers = listOf(
            "reminder", "set", "remind", "ஞாபகப்படுத்து", "எச்சரிக்கை", "நினைவூட்டல்"
        )

        return when {
            salesMarkers.any { lower.contains(it) } -> "SALES"
            purchaseMarkers.any { lower.contains(it) } -> "PURCHASE"
            receiptMarkers.any { lower.contains(it) } -> "RECEIPT"
            paymentMarkers.any { lower.contains(it) } -> "PAYMENT"
            reminderMarkers.any { lower.contains(it) } -> "REMINDER"
            else -> "SALES"
        }
    }

    // Simple date parsing for reminders (e.g., "Nov 20 2026")
    private fun extractDate(text: String): Long? {
        val lower = text.lowercase(Locale.ROOT)
        // Basic match for "Date Month Year"
        val matcher = Pattern.compile("(\\d{1,2})\\s*(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\s*(\\d{2,4})?").matcher(lower)
        if (matcher.find()) {
            val day = matcher.group(1)?.toIntOrNull() ?: 1
            val monthStr = matcher.group(2) ?: "jan"
            val month = when (monthStr) {
                "jan" -> Calendar.JANUARY
                "feb" -> Calendar.FEBRUARY
                "mar" -> Calendar.MARCH
                "apr" -> Calendar.APRIL
                "may" -> Calendar.MAY
                "jun" -> Calendar.JUNE
                "jul" -> Calendar.JULY
                "aug" -> Calendar.AUGUST
                "sep" -> Calendar.SEPTEMBER
                "oct" -> Calendar.OCTOBER
                "nov" -> Calendar.NOVEMBER
                "dec" -> Calendar.DECEMBER
                else -> Calendar.JANUARY
            }
            val year = matcher.group(3)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            val cal = Calendar.getInstance()
            cal.set(year, month, day)
            return cal.timeInMillis
        }
        return null
    }

    private fun extractAmount(text: String): Double? {
        val lower = text.lowercase(Locale.ROOT)
        
        // Tamil number word mapping
        val tamilNumbers = mapOf(
            "ஆயிரம்" to 1000.0,
            "ரெண்டாயிரம்" to 2000.0,
            "இரண்டாயிரம்" to 2000.0,
            "ஐயாயிரம்" to 5000.0,
            "பத்தாயிரம்" to 10000.0
        )
        
        for ((word, value) in tamilNumbers) {
            if (lower.contains(word)) return value
        }

        // Match numbers, but discard ones followed by % or percent or gst
        val matcher = Pattern.compile("(\\d[\\d,]*(\\.\\d+)?)(\\s*(%|percent|gst|சதவீதம்))?").matcher(lower)
        val validAmounts = mutableListOf<Double>()
        while (matcher.find()) {
            val numStr = matcher.group(1)?.replace(",", "") ?: continue
            val hasGstSuffix = !matcher.group(3).isNullOrBlank()
            val num = numStr.toDoubleOrNull() ?: continue
            if (hasGstSuffix) {
                // This is a GST percentage, not amount
                continue
            }
            if (num > 0) {
                validAmounts.add(num)
            }
        }
        // If multiple numbers, return the largest or primary transaction amount
        return validAmounts.maxOrNull()
    }

    private fun extractGstRate(text: String): Double? {
        val lower = text.lowercase(Locale.ROOT)
        val matcher = Pattern.compile("(\\d+(\\.\\d+)?)\\s*(%|percent|gst|சதவீதம்)").matcher(lower)
        if (matcher.find()) {
            val rate = matcher.group(1)?.toDoubleOrNull()
            if (rate != null) return rate
        }
        return 0.0
    }

    private fun extractPaymentMode(raw: String, english: String): String {
        val combined = (raw + " " + english).lowercase(Locale.ROOT)
        return when {
            combined.contains("cash") || combined.contains("rokka") || combined.contains("kaasu") || combined.contains("nakad") || combined.contains("ரொக்கம்") || combined.contains("नकद") -> "CASH"
            combined.contains("bank") || combined.contains("upi") || combined.contains("gpay") || combined.contains("cheque") || combined.contains("வங்கி") || combined.contains("बैंक") -> "BANK"
            else -> "CREDIT"
        }
    }

    private fun extractPartyName(raw: String, english: String, txType: String): String {
        val combined = "$raw $english"

        // Pattern: [Party Name] (ji)? (ko|ku|kitta|se|kitta irunthu)
        // Allow Tamil characters [\u0B80-\u0BFF] in name
        val hindiTanglishMatch = Regex("(?i)\\b([A-Za-z\u0B80-\u0BFF]+)(\\s+ji)?\\s+(ko|ku|kitta|se|irunthu|வாங்க|கொடுக்க)\\b").find(raw)
        if (hindiTanglishMatch != null) {
            val name = hindiTanglishMatch.groupValues[1]
            // Allow slightly longer names for Tamil characters
            if (name.length in 2..35 && name.lowercase(Locale.ROOT) !in listOf("zeno", "bill", "gst", "cash", "bank", "maal", "amount")) {
                return name.replaceFirstChar { it.uppercase() }
            }
        }

        // Pattern: to/from [Party Name]
        val toFromMatch = Regex("(?i)\\b(to|from)\\s+([A-Za-z]+(\\s+[A-Za-z]+)?)\\b").find(combined)
        if (toFromMatch != null) {
            val candidate = toFromMatch.groupValues[2].trim()
            val stopWords = listOf("for", "worth", "rupees", "rs", "cash", "bank", "office", "goods", "materials", "stock", "customer", "vendor")
            val filtered = candidate.split(" ").filter { it.lowercase(Locale.ROOT) !in stopWords }.joinToString(" ")
            if (filtered.isNotBlank()) {
                return filtered.replaceFirstChar { it.uppercase() }
            }
        }

        return when (txType) {
            "SALES" -> "General Customer"
            "PURCHASE" -> "General Supplier"
            else -> "General Account"
        }
    }

    private fun generateResponse(
        lang: SupportedLanguage,
        txType: String,
        party: String,
        amount: Double,
        gstRate: Double
    ): String {
        val formattedAmt = String.format(Locale.ENGLISH, "%,.2f", amount)
        val gstStr = "${gstRate.toInt()}%"

        return when (lang) {
            SupportedLanguage.TAMIL -> {
                when (txType) {
                    "SALES" -> "$party அவர்களுக்கு ₹$formattedAmt மதிப்புள்ள விற்பனை ($gstStr GST) பதிவு செய்யப்படுகிறது. உறுதிப்படுத்துங்கள்."
                    "PURCHASE" -> "$party இடமிருந்து ₹$formattedAmt மதிப்புள்ள கொள்முதல் ($gstStr GST) கணக்கில் எடுக்கப்படுகிறது. உறுதிப்படுத்துங்கள்."
                    "RECEIPT" -> "$party இடமிருந்து ₹$formattedAmt வரவு வைக்கப்படுகிறது. உறுதிப்படுத்துங்கள்."
                    "PAYMENT" -> "$party அவர்களுக்கு ₹$formattedAmt பற்று வைக்கப்படுகிறது. உறுதிப்படுத்துங்கள்."
                    else -> "பரிவர்த்தனை விவரங்களை சரிபார்த்து உறுதிப்படுத்தவும்."
                }
            }

            SupportedLanguage.TANGLISH -> {
                when (txType) {
                    "SALES" -> "$party ku ₹$formattedAmt sales with $gstStr GST ready aachu. Confirm pannunga."
                    "PURCHASE" -> "$party kitta irunthu ₹$formattedAmt purchase bill with $gstStr GST record aaguthu. Confirm pannunga."
                    "RECEIPT" -> "$party kitta irunthu ₹$formattedAmt payment receipt record aaguthu. Confirm pannunga."
                    "PAYMENT" -> "$party ku ₹$formattedAmt payment voucher ready aachu. Confirm pannunga."
                    else -> "Transaction details check pannitu confirm pannunga."
                }
            }

            SupportedLanguage.HINDI -> {
                when (txType) {
                    "SALES" -> "$party को ₹$formattedAmt की बिक्री ($gstStr GST) दर्ज करने के लिए तैयार है। कृपया पुष्टि करें।"
                    "PURCHASE" -> "$party से ₹$formattedAmt की खरीद ($gstStr GST) प्रविष्टि तैयार है। कृपया पुष्टि करें।"
                    "RECEIPT" -> "$party से ₹$formattedAmt की रसीद तैयार है। कृपया पुष्टि करें।"
                    "PAYMENT" -> "$party को ₹$formattedAmt का भुगतान तैयार है। कृपया पुष्टि करें।"
                    else -> "लेन-देन के विवरण की पुष्टि करें।"
                }
            }

            SupportedLanguage.TELUGU -> {
                "$party కోసం ₹$formattedAmt $txType ($gstStr GST) నమోదు చేయడానికి సిద్ధంగా ఉంది. దయచేసి నిర్ధారించండి."
            }

            SupportedLanguage.KANNADA -> {
                "$party ರವರಿಗೆ ₹$formattedAmt $txType ($gstStr GST) ದಾಖಲಿಸಲು ಸಿದ್ಧವಾಗಿದೆ. ದಯವಿಟ್ಟು ದೃಢೀಕರಿಸಿ."
            }

            SupportedLanguage.MALAYALAM -> {
                "$party ക്കായി ₹$formattedAmt $txType ($gstStr GST) രേഖപ്പെടുത്താൻ തയ്യാറാണ്. സ്ഥിരീകരിക്കുക."
            }

            SupportedLanguage.MARATHI -> {
                "$party साठी ₹$formattedAmt $txType ($gstStr GST) नोंदवण्यासाठी तयार आहे. कृपया पुष्टी करा."
            }
            SupportedLanguage.BENGALI -> {
                "$party এর জন্য ₹$formattedAmt $txType ($gstStr GST) রেকর্ড করার জন্য প্রস্তুত. অনুগ্রহ করে নিশ্চিত করুন।"
            }

            SupportedLanguage.ENGLISH -> {
                "Identified $txType for $party amounting to ₹$formattedAmt with $gstStr GST. Please review and confirm to post."
            }
        }
    }
}
