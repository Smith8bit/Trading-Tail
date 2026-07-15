package com.tradingtail.domain.usecase

import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO
import com.tradingtail.data.imports.ExecutionValidator
import com.tradingtail.data.local.entity.Direction
import com.tradingtail.data.local.entity.ExecutionSource
import com.tradingtail.data.local.entity.InstrumentType
import com.tradingtail.data.local.entity.Side
import com.tradingtail.data.repository.ExecutionRepository

/**
 * Primary manual path: entry + exit in one form -> two Executions inserted atomically, then the
 * same matcher every other path runs.
 */
class RecordQuickTrade(
    private val executions: ExecutionRepository,
    private val rebuildTrades: RebuildTradesForSymbol,
) {
    suspend operator fun invoke(
        symbol: String,
        direction: Direction,
        quantity: BigDecimal,
        entryPrice: BigDecimal,
        exitPrice: BigDecimal,
        entryTimestamp: Long,
        exitTimestamp: Long,
        entryFees: BigDecimal = ZERO,
        exitFees: BigDecimal = ZERO,
        instrumentType: InstrumentType = InstrumentType.STOCK,
    ) {
        // The one check no per-execution validator can make: each leg is individually valid and the
        // pair is still impossible. Enforced here rather than in the form because this is the gate
        // every manual path routes through — a UI-only check would leave the usecase accepting an
        // exit-before-entry round-trip and handing it to the FIFO matcher, which sorts by timestamp
        // and would silently match the "exit" as the opening leg.
        require(exitTimestamp >= entryTimestamp) {
            "exit time must be at or after entry time"
        }
        val (entrySide, exitSide) = when (direction) {
            Direction.LONG -> Side.BUY to Side.SELL
            Direction.SHORT -> Side.SELL to Side.BUY
        }
        val entry = ExecutionValidator.validate(
            symbol, entrySide, entryPrice, quantity, entryTimestamp,
            ExecutionSource.MANUAL, entryFees, instrumentType,
        )
        val exit = ExecutionValidator.validate(
            entry.symbol, exitSide, exitPrice, quantity, exitTimestamp,
            ExecutionSource.MANUAL, exitFees, instrumentType,
        )
        executions.addAll(listOf(entry, exit))
        rebuildTrades(entry.symbol)
    }
}
