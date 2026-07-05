package com.tradingtail.ui.analytics

import com.tradingtail.common.ZERO
import com.tradingtail.common.bigDecimal
import com.tradingtail.data.local.entity.Direction
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.ExecutionSource
import com.tradingtail.data.local.entity.InstrumentType
import com.tradingtail.data.local.entity.Side
import com.tradingtail.data.local.entity.TradeEntity
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsViewStateTest {
    private fun trade(pnl: String, exit: Long) = TradeEntity(
        symbol = "X", direction = Direction.LONG,
        entryExecutionIds = emptyList(), exitExecutionIds = emptyList(),
        realizedPnl = bigDecimal(pnl), entryTimestamp = exit, exitTimestamp = exit,
    )

    @Test
    fun cumulativeRunsInExitOrderAndSumsExactly() {
        // Fed out of order; must sort by exit and produce a running total, no float drift.
        val series = cumulativeSeries(listOf(trade("7.00", 30), trade("10.05", 10), trade("-4.05", 20)))
        assertEquals(listOf(10.05f, 6.0f, 13.0f), series) // 10.05 → 10.05-4.05 → +7.00
    }

    @Test
    fun bestWorstPicksExtremesIgnoringSign() {
        val bw = bestWorst(listOf(trade("5.00", 1), trade("-8.00", 2), trade("3.00", 3)))
        assertEquals(bigDecimal("5.00"), bw.largestGain)
        assertEquals(bigDecimal("-8.00"), bw.largestLoss)
    }

    @Test
    fun profitFactorDividesGrossProfitByGrossLoss() {
        // +10 +5 gross profit, -6 gross loss → 15/6 = 2.5
        assertEquals(2.5, profitFactor(listOf(trade("10.00", 1), trade("5.00", 2), trade("-6.00", 3)))!!, 1e-4)
        // No losers → infinite factor, represented as null.
        assertEquals(null, profitFactor(listOf(trade("4.00", 1))))
    }

    @Test
    fun maxStreaksCountsRunsInExitOrderAndBreakEvenResets() {
        // Fed out of order; by exit ts: W L BE W W W → longest win run 3, loss run 1.
        val s = maxStreaks(listOf(
            trade("1.00", 50), trade("1.00", 40), trade("1.00", 30),
            trade("0.00", 25), trade("-1.00", 20), trade("1.00", 10),
        ))
        assertEquals(3, s.maxWins)
        assertEquals(1, s.maxLosses)
    }

    @Test
    fun maxDrawdownIsLargestPeakToTroughDecline() {
        // cum: 10, -5, 0, -3; peak stays 10 → deepest trough -5 → drawdown 15.
        val dd = maxDrawdown(listOf(trade("10.00", 1), trade("-15.00", 2), trade("5.00", 3), trade("-3.00", 4)))
        assertEquals(bigDecimal("15.00"), dd)
    }

    @Test
    fun dayTallyGroupsByBangkokDayThenClassifies() {
        fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
        // Jul 1: +5 -2 = +3 (win) · Jul 2: -4 (loss) · Jul 3: +1 -1 = 0 (break-even).
        val t = dayTally(listOf(
            trade("5.00", ms("2026-07-01T02:00:00Z")), trade("-2.00", ms("2026-07-01T03:00:00Z")),
            trade("-4.00", ms("2026-07-02T02:00:00Z")),
            trade("1.00", ms("2026-07-03T02:00:00Z")), trade("-1.00", ms("2026-07-03T03:00:00Z")),
        ))
        assertEquals(DayTally(1, 1, 1, bigDecimal("3.00"), bigDecimal("-4.00")), t)
    }

    @Test
    fun averagesRoundHalfUpToTwoDecimalsAndSplitByOutcome() {
        // all: (10 + 5 - 6)/3 = 3.00; winners (10+5)/2 = 7.50; losers -6/1 = -6.00
        val a = averages(listOf(trade("10.00", 1), trade("5.00", 2), trade("-6.00", 3)))
        assertEquals(bigDecimal("3.00"), a.perTrade)
        assertEquals(bigDecimal("7.50"), a.perWinner)
        assertEquals(bigDecimal("-6.00"), a.perLoser)
        assertEquals(ZERO, averages(emptyList()).perWinner) // empty bucket → ZERO, no divide-by-zero
    }

    private fun tradeAt(pnl: String, entryExecId: Long) = TradeEntity(
        symbol = "X", direction = Direction.LONG,
        entryExecutionIds = listOf(entryExecId), exitExecutionIds = emptyList(),
        realizedPnl = bigDecimal(pnl), entryTimestamp = 0, exitTimestamp = 0,
    )

    @Test
    fun pnlByPriceShowsEveryBracketBucketingOnEntryFillPrice() {
        val price = mapOf(1L to bigDecimal("1.50"), 2L to bigDecimal("3.00"), 3L to bigDecimal("50.00"))
        val out = pnlByPrice(listOf(tradeAt("10.00", 2), tradeAt("-5.00", 3), tradeAt("2.00", 1))) { price[it] }
        assertEquals(9, out.size) // full fixed ladder, empties included
        val byLabel = out.associate { it.label to it.pnl }
        assertEquals(bigDecimal("2.00"), byLabel["< ฿2"])
        assertEquals(bigDecimal("10.00"), byLabel["฿2–4.99"])
        assertEquals(bigDecimal("-5.00"), byLabel["฿50–99.99"])
        assertEquals(ZERO, byLabel["฿500+"]) // untouched bracket → ฿0
    }

    @Test
    fun totalFeesSumsExecutionFees() {
        fun exec(fee: String) = ExecutionEntity(
            symbol = "X", side = Side.BUY, price = bigDecimal("1.00"), quantity = bigDecimal("1"),
            timestamp = 0, fees = bigDecimal(fee), instrumentType = InstrumentType.STOCK, source = ExecutionSource.MANUAL,
        )
        assertEquals(bigDecimal("2.75"), totalFees(listOf(exec("1.25"), exec("1.50"))))
    }

    @Test
    fun winRateByDayIsChronologicalWithPerDayRate() {
        fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
        // Jul 1: +1 -1 → 50% · Jul 2: +1 +1 → 100% (fed out of order, must sort ascending).
        val out = winRateByDay(listOf(
            trade("1.00", ms("2026-07-02T02:00:00Z")), trade("1.00", ms("2026-07-02T03:00:00Z")),
            trade("1.00", ms("2026-07-01T02:00:00Z")), trade("-1.00", ms("2026-07-01T03:00:00Z")),
        ))
        assertEquals(listOf("07-01", "07-02"), out.map { it.label })
        assertEquals(listOf(50f, 100f), out.map { it.value })
    }

    @Test
    fun hourWindowSpansPreToAfterMarketFillingEmptyHours() {
        val out = hourWindow(listOf(com.tradingtail.domain.usecase.HourPnl(9, 3, bigDecimal("50.00"))))
        assertEquals(HOUR_END - HOUR_START + 1, out.size)          // continuous 06:00..20:00
        assertEquals("06:00", out.first().label)
        assertEquals("20:00", out.last().label)
        assertEquals(bigDecimal("50.00"), out.single { it.label == "09:00" }.pnl)
        assertEquals(ZERO, out.single { it.label == "07:00" }.pnl)  // untraded hour → ฿0
    }
}
