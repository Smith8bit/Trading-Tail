package com.tradingtail.domain.usecase

import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO
import com.tradingtail.data.imports.ExecutionValidator
import com.tradingtail.data.local.entity.ExecutionSource
import com.tradingtail.data.local.entity.InstrumentType
import com.tradingtail.data.local.entity.Side
import com.tradingtail.data.repository.ExecutionRepository

/**
 * Advanced manual path: one fill at a time, for partial exits / scaling in and out. Same validator,
 * same insert path, same matcher as everything else.
 */
class RecordSingleExecution(
    private val executions: ExecutionRepository,
    private val rebuildTrades: RebuildTradesForSymbol,
) {
    suspend operator fun invoke(
        symbol: String,
        side: Side,
        price: BigDecimal,
        quantity: BigDecimal,
        timestamp: Long,
        fees: BigDecimal = ZERO,
        instrumentType: InstrumentType = InstrumentType.STOCK,
    ) {
        val execution = ExecutionValidator.validate(
            symbol, side, price, quantity, timestamp,
            ExecutionSource.MANUAL, fees, instrumentType,
        )
        executions.add(execution)
        rebuildTrades(execution.symbol)
    }
}
