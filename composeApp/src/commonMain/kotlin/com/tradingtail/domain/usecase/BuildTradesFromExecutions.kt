package com.tradingtail.domain.usecase

import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO
import com.tradingtail.data.local.entity.Direction
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.Side
import com.tradingtail.data.local.entity.TradeEntity

/**
 * ★ The single source of truth for turning executions into closed trades. Pure function — no I/O.
 *
 * Model: a Trade is one contiguous round-trip, flat -> position -> flat. Per symbol, executions are
 * sorted by (timestamp, id) and matched FIFO:
 *   - an execution on the same side as the open position adds a lot (scale-in),
 *   - an opposite-side execution consumes open lots oldest-first (scale-out), realizing P&L,
 *   - when the position returns to flat, the accumulated entries+exits emit as one Trade.
 * A closing execution larger than the open position flips through zero: it flattens (emits a trade)
 * and its remainder opens a new position in the other direction.
 * A position still open at the end of the stream emits no Trade (it isn't a closed round-trip yet).
 */
class BuildTradesFromExecutions {
    operator fun invoke(executions: List<ExecutionEntity>): List<TradeEntity> {
        val out = mutableListOf<TradeEntity>()
        for ((symbol, execs) in executions.groupBy { it.symbol }) {
            matchSymbol(symbol, execs.sortedWith(compareBy({ it.timestamp }, { it.id })), out)
        }
        return out
    }

    private fun matchSymbol(symbol: String, sorted: List<ExecutionEntity>, out: MutableList<TradeEntity>) {
        val open = ArrayDeque<Lot>()
        var openSide: Side? = null
        var trade = OpenTrade()

        for (e in sorted) {
            if (open.isEmpty() || e.side == openSide) {
                // Open a new position or add to the current one (scale-in).
                openSide = e.side
                open.addLast(Lot(e.price, e.quantity, e.side))
                trade.addEntry(e)
                continue
            }

            // Opposite side — close open lots FIFO (scale-out / full close).
            trade.addExit(e)
            var remaining = e.quantity
            while (remaining > ZERO && open.isNotEmpty()) {
                val lot = open.first()
                val matchQty = minBd(lot.remaining, remaining)
                // Long earns exit-entry; short earns entry-exit.
                val gross = if (lot.side == Side.BUY) {
                    e.price.subtract(lot.price).multiply(matchQty)
                } else {
                    lot.price.subtract(e.price).multiply(matchQty)
                }
                trade.pnl = trade.pnl.add(gross)
                trade.exitTs = e.timestamp
                lot.remaining = lot.remaining.subtract(matchQty)
                remaining = remaining.subtract(matchQty)
                if (lot.remaining <= ZERO) open.removeFirst()
            }

            if (open.isEmpty()) {
                out += trade.toTrade(symbol)
                trade = OpenTrade()
                openSide = null
                if (remaining > ZERO) {
                    // Flip through zero: leftover opens a fresh position on this execution's side.
                    openSide = e.side
                    open.addLast(Lot(e.price, remaining, e.side))
                    trade.addEntry(e, chargeFees = false) // fees already booked to the closed trade
                }
            }
        }
        // Anything left in `open` is a still-open position — no closed Trade emitted.
    }
}

private class Lot(val price: BigDecimal, var remaining: BigDecimal, val side: Side)

private class OpenTrade {
    private val entryIds = LinkedHashSet<Long>()
    private val exitIds = LinkedHashSet<Long>()
    var pnl: BigDecimal = ZERO
    private var direction: Direction? = null
    private var entryTs: Long = 0
    var exitTs: Long = 0

    fun addEntry(e: ExecutionEntity, chargeFees: Boolean = true) {
        if (direction == null) {
            direction = if (e.side == Side.BUY) Direction.LONG else Direction.SHORT
            entryTs = e.timestamp
        }
        entryIds += e.id
        if (chargeFees) pnl = pnl.subtract(e.fees)
    }

    fun addExit(e: ExecutionEntity) {
        exitIds += e.id
        pnl = pnl.subtract(e.fees)
    }

    fun toTrade(symbol: String) = TradeEntity(
        symbol = symbol,
        direction = direction!!,
        entryExecutionIds = entryIds.toList(),
        exitExecutionIds = exitIds.toList(),
        realizedPnl = pnl,
        entryTimestamp = entryTs,
        exitTimestamp = exitTs,
    )
}

private fun minBd(a: BigDecimal, b: BigDecimal): BigDecimal = if (a <= b) a else b
