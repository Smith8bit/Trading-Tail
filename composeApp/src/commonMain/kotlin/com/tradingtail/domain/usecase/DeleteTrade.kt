package com.tradingtail.domain.usecase

import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.repository.ExecutionRepository

/**
 * A Trade is derived, so deleting it means deleting its underlying executions, then re-deriving the
 * symbol's remaining trades — otherwise the next rebuild resurrects it from the still-present fills.
 * ponytail: current entry paths (quick, long-only) give each trade its own 2 unshared executions, so
 * this is clean. If CSV import ever produces flip-through-zero trades (a fill shared as one trade's
 * exit and the next's entry), revisit — deleting a shared execution would corrupt the neighbour.
 */
class DeleteTrade(
    private val executions: ExecutionRepository,
    private val rebuildTrades: RebuildTradesForSymbol,
) {
    suspend operator fun invoke(trade: TradeEntity) {
        executions.deleteByIds(trade.entryExecutionIds + trade.exitExecutionIds)
        rebuildTrades(trade.symbol)
    }
}
