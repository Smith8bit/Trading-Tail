package com.tradingtail.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tradingtail.common.BigDecimal

enum class Side { BUY, SELL }
enum class Direction { LONG, SHORT }
enum class InstrumentType { STOCK, OPTION, FUTURES, FOREX }
enum class ExecutionSource { MANUAL, CSV, PDF }

/** A single fill — one CSV row or one leg of a manual entry. */
@Entity(tableName = "executions")
data class ExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val side: Side,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val timestamp: Long, // ponytail: epoch millis, not a DateTime type — sorts + diffs fine; swap for kotlinx-datetime only if a caller needs calendar math
    val fees: BigDecimal,
    val instrumentType: InstrumentType,
    val source: ExecutionSource,
)

/** A derived, closed round-trip (flat -> position -> flat). Produced by BuildTradesFromExecutions. */
@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val direction: Direction,
    val entryExecutionIds: List<Long>,
    val exitExecutionIds: List<Long>,
    val realizedPnl: BigDecimal,
    val entryTimestamp: Long,
    val exitTimestamp: Long,
    val notes: String? = null,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/** Many-to-many: tags attach at Trade level, never Execution. */
@Entity(tableName = "trade_tag_cross_ref", primaryKeys = ["tradeId", "tagId"])
data class TradeTagCrossRef(
    val tradeId: Long,
    val tagId: Long,
)
