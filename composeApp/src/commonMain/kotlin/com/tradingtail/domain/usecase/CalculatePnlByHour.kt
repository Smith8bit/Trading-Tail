package com.tradingtail.domain.usecase

import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO
import com.tradingtail.common.bkkHour
import com.tradingtail.data.local.entity.TradeEntity

/** Realized P&L and trade count for one Bangkok-local hour (0-23). */
data class HourPnl(val hour: Int, val trades: Int, val pnl: BigDecimal)

/**
 * Buckets closed trades by the Bangkok-local hour (0-23) of their exit, sums P&L, sorted by hour.
 * ponytail: aggregated in Kotlin with BigDecimal, not a SQL SUM — SUM on the TEXT-stored decimals
 * coerces to REAL (float), which the project forbids for money.
 */
class CalculatePnlByHour {
    operator fun invoke(trades: List<TradeEntity>): List<HourPnl> =
        trades.groupBy { bkkHour(it.exitTimestamp) }
            .map { (hour, group) ->
                HourPnl(
                    hour = hour,
                    trades = group.size,
                    pnl = group.fold(ZERO) { acc, t -> acc.add(t.realizedPnl) },
                )
            }
            .sortedBy { it.hour }
}
