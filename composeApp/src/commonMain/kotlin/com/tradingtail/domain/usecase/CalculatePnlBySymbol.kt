package com.tradingtail.domain.usecase

import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO
import com.tradingtail.data.local.entity.TradeEntity

/** Realized P&L and trade count for one symbol. */
data class SymbolPnl(val symbol: String, val trades: Int, val pnl: BigDecimal)

/**
 * Groups closed trades by symbol, sums P&L, sorted by P&L descending.
 * ponytail: aggregated in Kotlin with BigDecimal, not a SQL SUM — SUM on the TEXT-stored decimals
 * coerces to REAL (float), which the project forbids for money.
 */
class CalculatePnlBySymbol {
    operator fun invoke(trades: List<TradeEntity>): List<SymbolPnl> =
        trades.groupBy { it.symbol }
            .map { (symbol, group) ->
                SymbolPnl(
                    symbol = symbol,
                    trades = group.size,
                    pnl = group.fold(ZERO) { acc, t -> acc.add(t.realizedPnl) },
                )
            }
            .sortedByDescending { it.pnl }
}
