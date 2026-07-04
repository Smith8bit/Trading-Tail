package com.tradingtail.domain.usecase

import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO
import com.tradingtail.data.local.entity.TradeEntity

/**
 * Overall record across all closed trades. [winRate] is a count ratio (0.0..1.0), safe as a
 * Double — it's not money. [totalPnl] stays BigDecimal.
 */
data class WinRateSummary(
    val wins: Int,
    val losses: Int,
    val breakeven: Int,
    val winRate: Double,
    val totalPnl: BigDecimal,
)

/**
 * ponytail: aggregated in Kotlin with BigDecimal, not a SQL SUM — SUM on the TEXT-stored decimals
 * coerces to REAL (float), which the project forbids for money. Win-rate % is a count ratio
 * (Double), the only division allowed here since it isn't money.
 */
class CalculateWinRate {
    operator fun invoke(trades: List<TradeEntity>): WinRateSummary {
        var wins = 0
        var losses = 0
        var breakeven = 0
        var total = ZERO
        for (t in trades) {
            when {
                t.realizedPnl > ZERO -> wins++
                t.realizedPnl < ZERO -> losses++
                else -> breakeven++
            }
            total = total.add(t.realizedPnl)
        }
        val decided = wins + losses + breakeven
        return WinRateSummary(
            wins = wins,
            losses = losses,
            breakeven = breakeven,
            winRate = if (decided == 0) 0.0 else wins.toDouble() / decided,
            totalPnl = total,
        )
    }
}
