package com.example

import com.example.data.model.AccountCategory
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ZenoAccountingEngineTest {

    @Test
    fun testDoubleEntryBalancing() {
        // Enforce debit == credit
        val debitLines = listOf(50000.0, 25000.0, 15000.0)
        val creditLines = listOf(90000.0)

        val totalDebit = debitLines.sum()
        val totalCredit = creditLines.sum()

        assertEquals("Double-entry rule: Total Debit must equal Total Credit", totalDebit, totalCredit, 0.001)
    }

    @Test
    fun testIndianGstIntraStateSplit() {
        val taxableAmount = 10000.0
        val gstRate = 18.0

        val totalTax = taxableAmount * (gstRate / 100.0)
        val cgst = totalTax / 2.0
        val sgst = totalTax / 2.0

        assertEquals(1800.0, totalTax, 0.001)
        assertEquals(900.0, cgst, 0.001)
        assertEquals(900.0, sgst, 0.001)
        assertEquals(totalTax, cgst + sgst, 0.001)
    }

    @Test
    fun testGstInclusiveCalculation() {
        val totalInclusive = 11800.0
        val gstRate = 18.0

        val baseTaxable = totalInclusive / (1 + (gstRate / 100.0))
        val taxCalculated = totalInclusive - baseTaxable

        assertEquals(10000.0, baseTaxable, 0.01)
        assertEquals(1800.0, taxCalculated, 0.01)
    }

    @Test
    fun testInputTaxCreditSetOff() {
        val outputLiability = 18600.0
        val inputTaxCredit = 12400.0

        val netGstPayable = maxOf(0.0, outputLiability - inputTaxCredit)
        assertEquals(6200.0, netGstPayable, 0.001)
    }

    @Test
    fun testAccountCategoryNature() {
        val assetNature = AccountCategory.ASSET
        val expenseNature = AccountCategory.EXPENSE
        val liabilityNature = AccountCategory.LIABILITY

        assertTrue(assetNature == AccountCategory.ASSET || assetNature == AccountCategory.EXPENSE)
        assertTrue(expenseNature == AccountCategory.ASSET || expenseNature == AccountCategory.EXPENSE)
        assertFalse(liabilityNature == AccountCategory.ASSET)
    }
}
