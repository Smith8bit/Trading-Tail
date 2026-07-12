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
}
