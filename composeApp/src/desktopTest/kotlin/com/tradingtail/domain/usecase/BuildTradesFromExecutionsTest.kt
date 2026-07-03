package com.tradingtail.domain.usecase

import com.tradingtail.common.BigDecimal
import com.tradingtail.common.bigDecimal
import com.tradingtail.data.local.entity.Direction
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.ExecutionSource
import com.tradingtail.data.local.entity.InstrumentType
import com.tradingtail.data.local.entity.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// ponytail: desktopTest, not commonTest — the matcher is pure common code, and desktopTest gives a
// plain JVM junit runner without wiring test deps into every target.
class BuildTradesFromExecutionsTest {

    private val build = BuildTradesFromExecutions()

    private fun exec(
        id: Long,
        side: Side,
        price: String,
        qty: String,
        ts: Long,
        fees: String = "0",
        symbol: String = "AAPL",
    ) = ExecutionEntity(
        id = id,
        symbol = symbol,
        side = side,
        price = bigDecimal(price),
        quantity = bigDecimal(qty),
        timestamp = ts,
        fees = bigDecimal(fees),
        instrumentType = InstrumentType.STOCK,
        source = ExecutionSource.MANUAL,
    )

    private fun assertMoney(expected: String, actual: BigDecimal, msg: String = "") =
        assertEquals(0, bigDecimal(expected).compareTo(actual), "$msg expected $expected but was $actual")

    @Test
    fun simpleLongRoundTripWithFees() {
        val trades = build(
            listOf(
                exec(1, Side.BUY, "10", "100", ts = 1, fees = "1"),
                exec(2, Side.SELL, "12", "100", ts = 2, fees = "1"),
            )
        )
        assertEquals(1, trades.size)
        val t = trades.single()
        assertEquals(Direction.LONG, t.direction)
        assertMoney("198", t.realizedPnl) // (12-10)*100 - 1 - 1
        assertEquals(listOf(1L), t.entryExecutionIds)
        assertEquals(listOf(2L), t.exitExecutionIds)
        assertEquals(1L, t.entryTimestamp)
        assertEquals(2L, t.exitTimestamp)
    }

    @Test
    fun scaleOutOneOpenMultipleCloses() {
        val trades = build(
            listOf(
                exec(1, Side.BUY, "10", "100", ts = 1),
                exec(2, Side.SELL, "12", "50", ts = 2),
                exec(3, Side.SELL, "14", "50", ts = 3),
            )
        )
        val t = trades.single()
        assertEquals(Direction.LONG, t.direction)
        assertMoney("300", t.realizedPnl) // 50*(12-10) + 50*(14-10)
        assertEquals(listOf(1L), t.entryExecutionIds)
        assertEquals(listOf(2L, 3L), t.exitExecutionIds)
    }

    @Test
    fun scaleInMultipleOpensOneClose() {
        val trades = build(
            listOf(
                exec(1, Side.BUY, "10", "50", ts = 1),
                exec(2, Side.BUY, "12", "50", ts = 2),
                exec(3, Side.SELL, "15", "100", ts = 3),
            )
        )
        val t = trades.single()
        assertEquals(Direction.LONG, t.direction)
        assertMoney("400", t.realizedPnl) // 50*(15-10) + 50*(15-12)
        assertEquals(listOf(1L, 2L), t.entryExecutionIds)
        assertEquals(listOf(3L), t.exitExecutionIds)
    }

    @Test
    fun shortRoundTrip() {
        val trades = build(
            listOf(
                exec(1, Side.SELL, "20", "100", ts = 1),
                exec(2, Side.BUY, "15", "100", ts = 2),
            )
        )
        val t = trades.single()
        assertEquals(Direction.SHORT, t.direction)
        assertMoney("500", t.realizedPnl) // (20-15)*100
        assertEquals(listOf(1L), t.entryExecutionIds)
        assertEquals(listOf(2L), t.exitExecutionIds)
    }

    @Test
    fun partialLeftoverOpenPositionEmitsNoTrade() {
        val trades = build(
            listOf(
                exec(1, Side.BUY, "10", "100", ts = 1),
                exec(2, Side.SELL, "12", "50", ts = 2), // 50 still held
            )
        )
        assertTrue(trades.isEmpty(), "open position must not produce a closed trade")
    }

    @Test
    fun flipThroughZeroSplitsIntoTwoTrades() {
        val trades = build(
            listOf(
                exec(1, Side.BUY, "10", "100", ts = 1),
                exec(2, Side.SELL, "12", "150", ts = 2), // closes 100 long, opens 50 short
                exec(3, Side.BUY, "11", "50", ts = 3),   // closes the 50 short
            )
        )
        assertEquals(2, trades.size)
        val (first, second) = trades
        assertEquals(Direction.LONG, first.direction)
        assertMoney("200", first.realizedPnl) // (12-10)*100
        assertEquals(listOf(1L), first.entryExecutionIds)
        assertEquals(listOf(2L), first.exitExecutionIds)

        assertEquals(Direction.SHORT, second.direction)
        assertMoney("50", second.realizedPnl) // (12-11)*50
        assertEquals(listOf(2L), second.entryExecutionIds)
        assertEquals(listOf(3L), second.exitExecutionIds)
    }

    @Test
    fun separateSymbolsMatchIndependently() {
        val trades = build(
            listOf(
                exec(1, Side.BUY, "10", "100", ts = 1, symbol = "AAPL"),
                exec(2, Side.BUY, "5", "100", ts = 2, symbol = "MSFT"),
                exec(3, Side.SELL, "12", "100", ts = 3, symbol = "AAPL"),
                exec(4, Side.SELL, "6", "100", ts = 4, symbol = "MSFT"),
            )
        )
        assertEquals(2, trades.size)
        assertEquals(setOf("AAPL", "MSFT"), trades.map { it.symbol }.toSet())
    }
}
