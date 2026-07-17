package com.tradingtail.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tradingtail.common.BigDecimal

enum class Side { BUY, SELL }
enum class Direction { LONG, SHORT }
enum class InstrumentType { STOCK, OPTION, FUTURES, FOREX }
enum class ExecutionSource { MANUAL, CSV, PDF }

/**
 * A single fill — one imported statement row or one leg of a manual entry.
 *
 * `id` is the device-local Room row id; it is NOT stable across devices, so sync keys on [syncId]
 * (a UUID assigned once at creation). The three sync columns are stamped by [ExecutionRepository],
 * never by usecases — they carry their construction defaults through and the repo overwrites them.
 */
@Entity(tableName = "executions", indices = [Index(value = ["syncId"], unique = true)])
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
    // --- sync bookkeeping (see data/sync/SyncManager) ---
    val syncId: String = "",      // stable cross-device identity
    val updatedAt: Long = 0L,     // last local mutation, epoch millis — the last-write-wins clock
    val deleted: Boolean = false, // tombstone: soft-deleted so the delete propagates, then filtered from reads
)

/**
 * A derived, closed round-trip (flat -> position -> flat). Produced by BuildTradesFromExecutions.
 *
 * Rows here are disposable: RebuildTradesForSymbol truncates the symbol and re-derives from fills, so
 * `id` is NOT stable across rebuilds and nothing durable may hang off it. User-authored data keys off
 * [naturalKey] and lives in its own table (see [TradeNoteEntity]).
 */
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
)

/**
 * The stable identity of a round-trip across rebuilds. A trade's row id churns every time the matcher
 * re-derives the symbol, but the same fills always produce the same symbol + open/close instants.
 */
data class TradeKey(val symbol: String, val entryTs: Long, val exitTs: Long)

val TradeEntity.naturalKey: TradeKey get() = TradeKey(symbol, entryTimestamp, exitTimestamp)

/**
 * A note the trader wrote about a round-trip. Keyed by [TradeKey], not by `trades.id` — notes used to
 * live on TradeEntity, where every rebuild silently destroyed them (the table is truncated per symbol
 * on each new fill and each import). This table is never touched by the matcher, so a note outlives
 * every re-derivation. A note whose trade no longer exists is a harmless orphan, not a crash.
 */
@Entity(tableName = "trade_notes", primaryKeys = ["symbol", "entryTs", "exitTs"])
data class TradeNoteEntity(
    val symbol: String,
    val entryTs: Long,
    val exitTs: Long,
    val note: String,
    // The composite PK is already a stable cross-device key, so notes need no syncId — only the
    // last-write-wins clock and a tombstone so a cleared note propagates instead of silently reappearing.
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
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
