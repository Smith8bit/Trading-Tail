package com.tradingtail.ui.analytics

import com.tradingtail.common.bigDecimal
import com.tradingtail.data.local.entity.Direction
import com.tradingtail.data.local.entity.TradeEntity
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
}
