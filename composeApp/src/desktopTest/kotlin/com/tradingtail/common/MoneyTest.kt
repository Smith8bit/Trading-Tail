package com.tradingtail.common

import kotlin.test.Test
import kotlin.test.assertEquals

private const val BAHT = "฿"   // ฿
private const val MINUS = "−"  // − (real minus, not hyphen)

class MoneyTest {
    @Test
    fun fullFormIsSignAwareTwoDpAndSeparated() {
        assertEquals("+${BAHT}1,234.50", formatMoney(bigDecimal("1234.5")))
        assertEquals("${MINUS}${BAHT}480.50", formatMoney(bigDecimal("-480.5")))
        assertEquals("${BAHT}0.00", formatMoney(bigDecimal("0")))
        assertEquals("+${BAHT}1,234,567.89", formatMoney(bigDecimal("1234567.891"))) // rounds HALF_UP
    }

    @Test
    fun tinyNegativeRoundingToZeroHasNoMinus() {
        assertEquals("${BAHT}0.00", formatMoney(bigDecimal("-0.001")))
    }

    @Test
    fun shortFormAbbreviatesWithSign() {
        assertEquals("+${BAHT}2.3k", formatMoneyShort(bigDecimal("2345")))
        assertEquals("${MINUS}${BAHT}1.4M", formatMoneyShort(bigDecimal("-1400000")))
        assertEquals("${MINUS}${BAHT}90", formatMoneyShort(bigDecimal("-90")))
        assertEquals("${BAHT}0", formatMoneyShort(bigDecimal("0")))
    }
}
