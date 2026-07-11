package com.tradingtail.common

import kotlin.test.Test
import kotlin.test.assertEquals

private const val CUR = "$"   // USD
private const val MINUS = "−"  // − (real minus, not hyphen)

class MoneyTest {
    @Test
    fun fullFormIsSignAwareTwoDpAndSeparated() {
        assertEquals("+${CUR}1,234.50", formatMoney(bigDecimal("1234.5")))
        assertEquals("${MINUS}${CUR}480.50", formatMoney(bigDecimal("-480.5")))
        assertEquals("${CUR}0.00", formatMoney(bigDecimal("0")))
        assertEquals("+${CUR}1,234,567.89", formatMoney(bigDecimal("1234567.891"))) // rounds HALF_UP
    }

    @Test
    fun tinyNegativeRoundingToZeroHasNoMinus() {
        assertEquals("${CUR}0.00", formatMoney(bigDecimal("-0.001")))
    }

    @Test
    fun shortFormAbbreviatesWithSign() {
        assertEquals("+${CUR}2.3k", formatMoneyShort(bigDecimal("2345")))
        assertEquals("${MINUS}${CUR}1.4M", formatMoneyShort(bigDecimal("-1400000")))
        assertEquals("${MINUS}${CUR}90", formatMoneyShort(bigDecimal("-90")))
        assertEquals("${CUR}0", formatMoneyShort(bigDecimal("0")))
    }
}
