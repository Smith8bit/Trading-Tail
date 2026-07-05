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
    fun yearMonthDayGroupingSumsIntoTheRightBucketsAndKeepsFullLadders() {
        fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
        // 2025: one trade. 2026: Jul 1 (+10, +5), Jul 3 (-4). All exits in Bangkok local (UTC+7).
        val trades = listOf(
            trade("3.00", ms("2025-02-10T02:00:00Z")),
            trade("10.00", ms("2026-07-01T02:00:00Z")), trade("5.00", ms("2026-07-01T03:00:00Z")),
            trade("-4.00", ms("2026-07-03T02:00:00Z")),
        )
        // By year: ascending, summed.
        assertEquals(listOf("2025", "2026"), pnlByYear(trades).map { it.label })
        assertEquals(listOf(3f, 11f), pnlByYear(trades).map { it.value }) // 2026: 10+5-4
        assertEquals(listOf(1f, 3f), tradesByYear(trades).map { it.value })

        // By month of 2026: full Jan..Dec ladder, only Jul populated.
        val byMonth = pnlByMonthOfYear(trades, 2026)
        assertEquals(12, byMonth.size)
        assertEquals(11f, byMonth[6].value) // Jul = index 6
        assertEquals(0f, byMonth[0].value)  // Jan untouched

        // By day of Jul 2026: 31-day ladder, Jul 1 = +15, Jul 3 = -4, others 0.
        val byDay = pnlByDayOfMonth(trades, 2026, 7)
        assertEquals(31, byDay.size)
        assertEquals(15f, byDay[0].value)  // day 1
        assertEquals(-4f, byDay[2].value)  // day 3
        assertEquals(0f, byDay[1].value)   // day 2
        assertEquals(2, tradesByDayOfMonth(trades, 2026, 7)[0].value.toInt()) // two fills on Jul 1
    }

    @Test
    fun stdDevAndSqnFromTradePnl() {
        // pnls 10,20,30 → mean 20, population var (100+0+100)/3 → sd ≈ 8.165; sqn = √3·20/sd ≈ 4.243.
        val t = listOf(trade("10.00", 1), trade("20.00", 2), trade("30.00", 3))
        assertEquals(8.165, pnlStdDev(t)!!, 1e-2)
        assertEquals(4.243, sqn(t)!!, 1e-2)
        assertEquals(null, pnlStdDev(listOf(trade("5.00", 1)))) // < 2 trades → undefined
    }

    @Test
    fun kellyIsNullWithoutLosersElseUsesWinLossRatio() {
        assertEquals(null, kelly(listOf(trade("5.00", 1), trade("3.00", 2)))) // no losers
        // W=0.5, avgWin=10, avgLoss=-5 → R=2 → 0.5 − 0.5/2 = 0.25
        assertEquals(0.25, kelly(listOf(trade("10.00", 1), trade("-5.00", 2)))!!, 1e-6)
    }

    @Test
    fun avgDailyPnlAveragesPerDayNetNotPerTrade() {
        fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
        // Jul 1 net +3 (+5,-2) · Jul 2 net -4 → average of {3, -4} = -0.50 (not per-trade).
        val v = avgDailyPnl(listOf(
            trade("5.00", ms("2026-07-01T02:00:00Z")), trade("-2.00", ms("2026-07-01T03:00:00Z")),
            trade("-4.00", ms("2026-07-02T02:00:00Z")),
        ))
        assertEquals(bigDecimal("-0.50"), v)
    }

    @Test
    fun pnlByVolumeTradedBucketsOnEntryShareSize() {
        val qty = mapOf(1L to bigDecimal("15"), 2L to bigDecimal("250"), 3L to bigDecimal("6000"))
        val out = pnlByVolumeTraded(listOf(tradeAt("10.00", 1), tradeAt("-5.00", 2), tradeAt("2.00", 3))) { qty[it] }
        assertEquals(9, out.size) // 8 fixed buckets + catch-all
        val byLabel = out.associate { it.label to it.pnl }
        assertEquals(bigDecimal("10.00"), byLabel["10 - 19"])   // 15 shares
        assertEquals(bigDecimal("-5.00"), byLabel["100 - 500"]) // 250 shares
        assertEquals(bigDecimal("2.00"), byLabel["5,000+"])     // 6000 shares → catch-all
    }

    @Test
    fun drawdownStatsMeasuresEpisodeDepthDaysAndTrades() {
        fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
        // cum: +10 (peak) · −6 (dd starts, 07-02) · −3 (07-03) · +9 (recovers to 10).
        // One episode: trough 1 → depth 9, spanning 2 days / 2 trades.
        val s = drawdownStats(listOf(
            trade("10.00", ms("2026-07-01T02:00:00Z")),
            trade("-6.00", ms("2026-07-02T02:00:00Z")),
            trade("-3.00", ms("2026-07-03T02:00:00Z")),
            trade("9.00", ms("2026-07-04T02:00:00Z")),
        ))
        assertEquals(bigDecimal("9.00"), s.biggest)
        assertEquals(bigDecimal("9.00"), s.avgDrawdown)
        assertEquals(2, s.totalDays)
        assertEquals(2.0, s.avgDays, 1e-9)
        assertEquals(2.0, s.avgTrades, 1e-9)
    }

    @Test
    fun movingAverageIsTrailingAndNullUntilWindowFills() {
        assertEquals(listOf(null, 3f, 5f, 7f), movingAverage(listOf(2f, 4f, 6f, 8f), 2))
    }

    @Test
    fun dayInRangeIsInclusiveAndTreatsNullBoundsAsOpen() {
        val d = com.tradingtail.common.BkkDate(2026, 6, 15)
        val from = com.tradingtail.common.BkkDate(2026, 6, 1)
        val to = com.tradingtail.common.BkkDate(2026, 6, 30)
        assertEquals(true, dayInRange(d, from, to))
        assertEquals(true, dayInRange(from, from, to))              // inclusive lower bound
        assertEquals(true, dayInRange(to, from, to))               // inclusive upper bound
        assertEquals(false, dayInRange(com.tradingtail.common.BkkDate(2026, 7, 1), from, to))
        assertEquals(false, dayInRange(com.tradingtail.common.BkkDate(2026, 5, 31), from, to))
        assertEquals(true, dayInRange(d, null, null))              // no bounds → everything passes
        assertEquals(true, dayInRange(d, from, null))              // open upper
        assertEquals(false, dayInRange(com.tradingtail.common.BkkDate(2025, 1, 1), from, null))
    }

    @Test
    fun presetRangeComputesDayBoundsAgainstToday() {
        // "now" = 2026-07-05 12:00 Bangkok (2026-07-05T05:00Z).
        val now = Instant.parse("2026-07-05T05:00:00Z").toEpochMilli()
        fun d(y: Int, m: Int, day: Int) = com.tradingtail.common.BkkDate(y, m, day)
        assertEquals(d(2026, 7, 5) to d(2026, 7, 5), presetRange(RangePreset.Today, now))
        assertEquals(d(2026, 7, 4) to d(2026, 7, 4), presetRange(RangePreset.Yesterday, now))
        assertEquals(d(2026, 6, 29) to d(2026, 7, 5), presetRange(RangePreset.Last7, now))   // 7 days incl today
        assertEquals(d(2026, 7, 1) to d(2026, 7, 31), presetRange(RangePreset.ThisMonth, now))
        assertEquals(d(2026, 6, 1) to d(2026, 6, 30), presetRange(RangePreset.LastMonth, now))
        assertEquals(d(2025, 7, 5) to d(2026, 7, 5), presetRange(RangePreset.Last12Months, now))
        assertEquals(d(2025, 1, 1) to d(2025, 12, 31), presetRange(RangePreset.LastYear, now))
        assertEquals(d(2026, 1, 1) to d(2026, 7, 5), presetRange(RangePreset.Ytd, now))
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
