package com.tradingtail.domain.usecase

import com.tradingtail.data.repository.ExecutionRepository
import com.tradingtail.data.repository.TradeRepository

/**
 * Re-derives all closed trades for one symbol from its executions. Shared by both manual entry
 * paths so matching is invoked in exactly one place. Deletes + reinserts (idempotent) rather than
 * appending, so re-running after a new fill never duplicates trades.
 */
class RebuildTradesForSymbol(
    private val executions: ExecutionRepository,
    private val trades: TradeRepository,
    private val buildTrades: BuildTradesFromExecutions,
) {
    suspend operator fun invoke(symbol: String) {
        val symbolExecutions = executions.bySymbol(symbol)
        trades.deleteForSymbol(symbol)
        trades.saveAll(buildTrades(symbolExecutions))
    }
}
