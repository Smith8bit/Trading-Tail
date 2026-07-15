package com.tradingtail.domain.usecase

import com.tradingtail.common.BigDecimal
import com.tradingtail.data.imports.ExecutionValidator
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.repository.ExecutionRepository

/**
 * Correct one fill, then re-derive the symbol's trades from the corrected set.
 *
 * A Trade is derived, so there is nothing to edit on it — you edit the fill underneath and let the
 * matcher rebuild. That also means a correction can legitimately change the round-trip's shape (a new
 * exit time can reorder FIFO matching), which is exactly why this routes through
 * [RebuildTradesForSymbol] rather than patching a trade row in place.
 *
 * Symbol and side are fixed: both are identity, not correction. Changing the symbol would have to
 * rebuild two symbols and would silently move the trade to a different instrument; re-enter it
 * instead. Validation reuses [ExecutionValidator], so a corrected fill can never be less valid than
 * a newly entered one.
 */
class UpdateExecution(
    private val executions: ExecutionRepository,
    private val rebuildTrades: RebuildTradesForSymbol,
) {
    suspend operator fun invoke(
        original: ExecutionEntity,
        price: BigDecimal,
        quantity: BigDecimal,
        timestamp: Long,
        fees: BigDecimal,
    ) {
        val validated = ExecutionValidator.validate(
            symbol = original.symbol,
            side = original.side,
            price = price,
            quantity = quantity,
            timestamp = timestamp,
            source = original.source,
            fees = fees,
            instrumentType = original.instrumentType,
        )
        executions.update(validated.copy(id = original.id))
        rebuildTrades(original.symbol)
    }
}
