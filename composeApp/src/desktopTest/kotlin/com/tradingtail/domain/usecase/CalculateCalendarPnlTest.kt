package com.tradingtail.domain.usecase

import com.tradingtail.common.BkkDate
import com.tradingtail.common.bigDecimal
import com.tradingtail.data.local.entity.Direction
import com.tradingtail.data.local.entity.TradeEntity
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CalculateCalendarPnlTest {
    private val calc = CalculateCalendarPnl()

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun trade(pnl: String, exit: Long) = TradeEntity(
        symbol = "X", direction = Direction.LONG,
        entryExecutionIds = emptyList(), exitExecutionIds = emptyList(),
        realizedPnl = bigDecimal(pnl), entryTimestamp = exit, exitTimestamp = exit,
    )

    @Test
    fun bucketsByBangkokDayAndSumsWithoutFloatDrift() {
        val result = calc(
            listOf(
                trade("10.05", ms("2026-07-03T10:00:00Z")), // Bangkok Jul 3, 17:00
                trade("-4.02", ms("2026-07-03T11:00:00Z")), // Bangkok Jul 3, 18:00
                trade("7.00", ms("2026-07-03T18:30:00Z")),  // Bangkok Jul 4, 01:30 — crosses +7
            ),
        )

        assertEquals(2, result.size)
        val jul3 = result.getValue(BkkDate(2026, 7, 3))
        assertEquals(2, jul3.trades)
        assertEquals(bigDecimal("6.03"), jul3.pnl) // exact, not 6.0299999…
        val jul4 = result.getValue(BkkDate(2026, 7, 4))
        assertEquals(1, jul4.trades)
        assertEquals(bigDecimal("7.00"), jul4.pnl)
    }
}
