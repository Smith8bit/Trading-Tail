package com.tradingtail.ui.calendar

import com.tradingtail.common.BkkDate
import com.tradingtail.common.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarViewTest {
    @Test
    fun januaryGridIsSundayFirstWithAdjacentMonthPadding() {
        // Jan 1 2026 is a Thursday → 4 leading cells from Dec 2025, 31 days, fills exactly 5 weeks.
        val cells = monthCells(YearMonth(2026, 1))
        assertEquals(35, cells.size)
        assertEquals(CalCell(BkkDate(2025, 12, 28), false), cells.first()) // Sunday slot
        assertEquals(CalCell(BkkDate(2026, 1, 1), true), cells[4])         // Thursday slot
        assertEquals(CalCell(BkkDate(2026, 1, 31), true), cells.last())    // no trailing padding
        assertTrue(cells.size % 7 == 0)
    }
}
