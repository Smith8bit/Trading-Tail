package com.tradingtail.domain.usecase

import com.tradingtail.common.BigDecimal
import com.tradingtail.common.BkkDate
import com.tradingtail.common.ZERO
import com.tradingtail.common.bkkDate
import com.tradingtail.data.local.entity.TradeEntity

/** Realized P&L and trade count for one calendar day. */
data class DayPnl(val trades: Int, val pnl: BigDecimal)

/**
 * Buckets closed trades by their Bangkok-local trade day (entry/open day) and sums P&L.
 * ponytail: aggregated in Kotlin with BigDecimal, not a SQL SUM — SUM on the TEXT-stored decimals
 * coerces to REAL (float), which the project forbids for money. Fine in-memory for a personal
 * journal's trade volume; revisit with an integer minor-units column only if it ever gets slow.
 */
class CalculateCalendarPnl {
    operator fun invoke(trades: List<TradeEntity>): Map<BkkDate, DayPnl> =
        trades.groupBy { bkkDate(it.entryTimestamp) }
            .mapValues { (_, dayTrades) ->
                DayPnl(
                    trades = dayTrades.size,
                    pnl = dayTrades.fold(ZERO) { acc, t -> acc.add(t.realizedPnl) },
                )
            }
}
