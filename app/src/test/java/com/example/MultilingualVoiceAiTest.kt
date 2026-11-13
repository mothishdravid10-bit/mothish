package com.example

import com.example.ai.MultilingualNlpProcessor
import com.example.ai.SupportedLanguage
import org.junit.Assert.*
import org.junit.Test

class MultilingualVoiceAiTest {

    @Test
    fun testEnglishVoiceInputParsing() {
        val input = "Sold electronics to Rajesh for 25000 with 18% GST"
        val result = MultilingualNlpProcessor.analyze(input)

        assertEquals(SupportedLanguage.ENGLISH, result.detectedLanguage)
        assertEquals("SALES", result.transactionType)
        assertEquals(25000.0, result.amount, 0.01)
        assertEquals(18.0, result.gstRate, 0.01)
        assertTrue(result.partyName.contains("Rajesh", ignoreCase = true))
    }

    @Test
    fun testTamilVoiceInputParsing() {
        val input = "சுரேஷ் அவர்களுக்கு 20000 ரூபாய் விற்பனை செய்தோம் 18% ஜிஎஸ்டி"
        val result = MultilingualNlpProcessor.analyze(input)

        assertEquals(SupportedLanguage.TAMIL, result.detectedLanguage)
        assertEquals("SALES", result.transactionType)
        assertEquals(20000.0, result.amount, 0.01)
        assertEquals(18.0, result.gstRate, 0.01)
        // Check canonical English output
        assertTrue(result.englishCanonicalTranslation.contains("Sold goods") || result.englishCanonicalTranslation.contains("Sale"))
        // Check localized response
        assertTrue(result.responseFeedback.contains("விற்பனை"))
    }

    @Test
    fun testTanglishVoiceInputParsing() {
        val input = "Murugan ku 15000 sales bill poten 18% GST"
        val result = MultilingualNlpProcessor.analyze(input)

        assertEquals(SupportedLanguage.TANGLISH, result.detectedLanguage)
        assertEquals("SALES", result.transactionType)
        assertEquals(15000.0, result.amount, 0.01)
        assertEquals(18.0, result.gstRate, 0.01)
        assertTrue(result.partyName.contains("Murugan", ignoreCase = true))
        assertTrue(result.responseFeedback.contains("Confirm pannunga"))
    }

    @Test
    fun testHindiHinglishVoiceInputParsing() {
        val input = "Sharma ji ko 30000 ka maal becha 18% GST"
        val result = MultilingualNlpProcessor.analyze(input)

        assertEquals(SupportedLanguage.HINDI, result.detectedLanguage)
        assertEquals("SALES", result.transactionType)
        assertEquals(30000.0, result.amount, 0.01)
        assertEquals(18.0, result.gstRate, 0.01)
        assertTrue(result.partyName.contains("Sharma", ignoreCase = true))
        assertTrue(result.responseFeedback.contains("पुष्टि करें"))
    }

    @Test
    fun testPurchaseBillRecognition() {
        val input = "Purchased raw materials from ABC Traders for 45000 with 12% GST"
        val result = MultilingualNlpProcessor.analyze(input)

        assertEquals("PURCHASE", result.transactionType)
        assertEquals(45000.0, result.amount, 0.01)
        assertEquals(12.0, result.gstRate, 0.01)
    }

    @Test
    fun testPaymentVoucherRecognition() {
        val input = "Paid 8000 for office rent by Cash"
        val result = MultilingualNlpProcessor.analyze(input)

        assertEquals("PAYMENT", result.transactionType)
        assertEquals(8000.0, result.amount, 0.01)
        assertEquals("CASH", result.paymentMode)
    }
}
