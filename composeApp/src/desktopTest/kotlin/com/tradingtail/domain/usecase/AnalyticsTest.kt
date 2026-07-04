package com.tradingtail.domain.usecase

import com.tradingtail.common.bigDecimal
import com.tradingtail.data.local.entity.Direction
import com.tradingtail.data.local.entity.TradeEntity
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsTest {
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun trade(pnl: String, symbol: String = "X", exit: Long = 0L) = TradeEntity(
        symbol = symbol, direction = Direction.LONG,
        entryExecutionIds = emptyList(), exitExecutionIds = emptyList(),
        realizedPnl = bigDecimal(pnl), entryTimestamp = exit, exitTimestamp = exit,
    )

    @Test
    fun winRateCountsAndTotalWithoutFloatDrift() {
        val result = CalculateWinRate()(
            listOf(trade("10.05"), trade("-4.02"), trade("7.00"), trade("0.00")),
        )
        assertEquals(2, result.wins)
        assertEquals(1, result.losses)
        assertEquals(1, result.breakeven)
        assertEquals(0.5, result.winRate) // 2 wins / 4 trades
        assertEquals(bigDecimal("13.03"), result.totalPnl) // exact, not 13.0299…
    }

    @Test
    fun bySymbolGroupsSumsAndSortsByPnlDescending() {
        val result = CalculatePnlBySymbol()(
            listOf(
                trade("5.00", symbol = "AAPL"),
                trade("-2.50", symbol = "AAPL"),
                trade("10.00", symbol = "TSLA"),
            ),
        )
        assertEquals(2, result.size)
        assertEquals("TSLA", result[0].symbol) // 10.00 > 2.50, sorted desc
        assertEquals(1, result[0].trades)
        assertEquals(bigDecimal("10.00"), result[0].pnl)
        assertEquals("AAPL", result[1].symbol)
        assertEquals(2, result[1].trades)
        assertEquals(bigDecimal("2.50"), result[1].pnl)
    }

    @Test
    fun byHourUsesBangkokOffsetAndSumsExactly() {
        val result = CalculatePnlByHour()(
            listOf(
                trade("3.00", exit = ms("2026-07-03T10:00:00Z")), // Bangkok 17:00
                trade("2.00", exit = ms("2026-07-03T10:30:00Z")), // Bangkok 17:00
                trade("9.00", exit = ms("2026-07-03T18:30:00Z")), // Bangkok next day 01:30 — crosses +7
            ),
        )
        assertEquals(2, result.size)
        val h1 = result.first { it.hour == 1 }
        assertEquals(1, h1.trades)
        assertEquals(bigDecimal("9.00"), h1.pnl)
        val h17 = result.first { it.hour == 17 }
        assertEquals(2, h17.trades)
        assertEquals(bigDecimal("5.00"), h17.pnl) // exact
    }
}
