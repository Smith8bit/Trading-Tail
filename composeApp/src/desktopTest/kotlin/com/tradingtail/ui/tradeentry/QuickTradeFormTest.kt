package com.tradingtail.ui.tradeentry

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickTradeFormTest {
    private fun filled() = QuickTradeForm(now = "2026-07-19 10:00").apply {
        symbol = "AAPL"; quantity = "10"; entryPrice = "1.50"; exitPrice = "2.00"
        entryTs = "2026-07-19 09:30"; exitTs = "2026-07-19 10:00"
    }

    @Test
    fun validFormPassesWithNoErrors() {
        val f = filled()
        assertTrue(f.validate())
        assertNull(f.symbolErr)
        assertNull(f.exitTsErr)
    }

    @Test
    fun emptyFormFailsOnRequiredAndNumericFields() {
        val f = QuickTradeForm(now = "2026-07-19 10:00")
        assertFalse(f.validate())
        assertEquals("Required", f.symbolErr)
        assertEquals("Enter a number", f.qtyErr)
        assertNull(f.entryTsErr) // the prefilled timestamps are already valid
        f.quantity = "0"
        f.validate()
        assertEquals("Must be > 0", f.qtyErr) // zero quantity is not a trade
    }

    @Test
    fun exitBeforeEntryFailsOnTheExitFieldOnly() {
        val f = filled().apply { exitTs = "2026-07-19 09:00" }
        assertFalse(f.validate())
        assertEquals("Exit can't be before entry", f.exitTsErr)
        assertNull(f.entryTsErr)
    }

    @Test
    fun feesAreOptionalButMustBeNonNegative() {
        assertTrue(filled().apply { entryFees = "0" }.validate()) // 0 is a legal fee, unlike price/qty
        val f = filled().apply { entryFees = "-1" }
        assertFalse(f.validate())
        assertEquals("Must be ≥ 0", f.entryFeesErr)
    }

    @Test
    fun dirtyIgnoresThePrefilledTimestamps() {
        val f = QuickTradeForm(now = "2026-07-19 10:00")
        assertFalse(f.dirty) // untouched form must close without a discard confirmation
        f.symbol = "A"
        assertTrue(f.dirty)
    }

    // The Saver replaces sixteen individually-proven rememberSaveable vars on the app's
    // highest-traffic path — this round-trip is what makes that swap safe.
    @Test
    fun saverRoundTripsEveryFieldAndError() {
        val f = filled().apply { entryFees = "0.10"; quantity = "" }
        f.validate() // populate a mix of set and null errors
        val saved = with(QuickTradeForm.Saver) { SaverScope { true }.save(f) }!!
        val r = QuickTradeForm.Saver.restore(saved)!!
        assertEquals(f.symbol, r.symbol)
        assertEquals(f.quantity, r.quantity)
        assertEquals(f.entryPrice, r.entryPrice)
        assertEquals(f.exitPrice, r.exitPrice)
        assertEquals(f.entryTs, r.entryTs)
        assertEquals(f.exitTs, r.exitTs)
        assertEquals(f.entryFees, r.entryFees)
        assertEquals(f.exitFees, r.exitFees)
        assertEquals(f.symbolErr, r.symbolErr)
        assertEquals(f.qtyErr, r.qtyErr) // "Enter a number" — a live error survives recreation
        assertEquals(f.entryErr, r.entryErr)
        assertEquals(f.exitErr, r.exitErr)
        assertEquals(f.entryTsErr, r.entryTsErr)
        assertEquals(f.exitTsErr, r.exitTsErr)
        assertEquals(f.entryFeesErr, r.entryFeesErr)
        assertEquals(f.exitFeesErr, r.exitFeesErr)
        assertEquals(f.dirty, r.dirty)
    }
}
