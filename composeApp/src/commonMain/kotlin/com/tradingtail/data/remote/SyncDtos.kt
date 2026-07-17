package com.tradingtail.data.remote

import com.tradingtail.common.bigDecimal
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.ExecutionSource
import com.tradingtail.data.local.entity.InstrumentType
import com.tradingtail.data.local.entity.Side
import com.tradingtail.data.local.entity.TradeNoteEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shapes for the two synced tables. Money and quantity travel as the exact BigDecimal string —
 * never a JSON number — so a fill can't pick up float drift crossing the wire. Enums travel by name,
 * matching how Room stores them locally. Column names are snake_case (Postgres idiom); the local id
 * is deliberately absent — it's device-local and never leaves.
 */
@Serializable
data class ExecutionRow(
    @SerialName("sync_id") val syncId: String,
    val symbol: String,
    val side: String,
    val price: String,
    val quantity: String,
    val timestamp: Long,
    val fees: String,
    @SerialName("instrument_type") val instrumentType: String,
    val source: String,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean,
)

@Serializable
data class NoteRow(
    val symbol: String,
    @SerialName("entry_ts") val entryTs: Long,
    @SerialName("exit_ts") val exitTs: Long,
    val note: String,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean,
)

fun ExecutionEntity.toRow(): ExecutionRow = ExecutionRow(
    syncId = syncId,
    symbol = symbol,
    side = side.name,
    price = price.toString(),
    quantity = quantity.toString(),
    timestamp = timestamp,
    fees = fees.toString(),
    instrumentType = instrumentType.name,
    source = source.name,
    updatedAt = updatedAt,
    deleted = deleted,
)

fun ExecutionRow.toEntity(): ExecutionEntity = ExecutionEntity(
    id = 0, // device-local, reassigned by the DAO on insert
    symbol = symbol,
    side = Side.valueOf(side),
    price = bigDecimal(price),
    quantity = bigDecimal(quantity),
    timestamp = timestamp,
    fees = bigDecimal(fees),
    instrumentType = InstrumentType.valueOf(instrumentType),
    source = ExecutionSource.valueOf(source),
    syncId = syncId,
    updatedAt = updatedAt,
    deleted = deleted,
)

fun TradeNoteEntity.toRow(): NoteRow = NoteRow(symbol, entryTs, exitTs, note, updatedAt, deleted)

fun NoteRow.toEntity(): TradeNoteEntity = TradeNoteEntity(symbol, entryTs, exitTs, note, updatedAt, deleted)
