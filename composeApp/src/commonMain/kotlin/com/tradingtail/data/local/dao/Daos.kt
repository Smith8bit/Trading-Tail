package com.tradingtail.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.TagEntity
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.local.entity.TradeNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionDao {
    @Insert
    suspend fun insert(execution: ExecutionEntity): Long

    @Insert
    suspend fun insertAll(executions: List<ExecutionEntity>): List<Long>

    // Correcting a fill: the trade detail screen edits the execution, then the matcher re-derives.
    @Update
    suspend fun update(execution: ExecutionEntity)

    @Query("SELECT * FROM executions WHERE symbol = :symbol ORDER BY timestamp, id")
    suspend fun bySymbol(symbol: String): List<ExecutionEntity>

    @Query("SELECT * FROM executions ORDER BY timestamp, id")
    suspend fun all(): List<ExecutionEntity>

    // Reactive feed for the dashboard's fee/volume/entry-price widgets.
    @Query("SELECT * FROM executions ORDER BY timestamp, id")
    fun allFlow(): Flow<List<ExecutionEntity>>

    @Query("DELETE FROM executions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

/**
 * Notes keyed by a trade's natural identity, never by `trades.id` — the matcher truncates and
 * re-derives that table, so a note hung off a row id would not survive the next fill.
 */
@Dao
interface TradeNoteDao {
    @Upsert
    suspend fun upsert(note: TradeNoteEntity)

    @Query("DELETE FROM trade_notes WHERE symbol = :symbol AND entryTs = :entryTs AND exitTs = :exitTs")
    suspend fun delete(symbol: String, entryTs: Long, exitTs: Long)

    @Query("SELECT * FROM trade_notes WHERE symbol = :symbol AND entryTs = :entryTs AND exitTs = :exitTs")
    fun byKeyFlow(symbol: String, entryTs: Long, exitTs: Long): Flow<TradeNoteEntity?>

    // Powers the calendar's note glyph.
    @Query("SELECT * FROM trade_notes")
    fun allFlow(): Flow<List<TradeNoteEntity>>
}

@Dao
interface TradeDao {
    @Insert
    suspend fun insertAll(trades: List<TradeEntity>): List<Long>

    @Query("DELETE FROM trades WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)

    @Query("SELECT * FROM trades ORDER BY exitTimestamp DESC")
    suspend fun all(): List<TradeEntity>

    // Reactive journal feed — re-emits whenever the matcher rewrites the trades table.
    @Query("SELECT * FROM trades ORDER BY exitTimestamp DESC")
    fun allFlow(): Flow<List<TradeEntity>>
}

@Dao
interface TagDao {
    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tags ORDER BY name")
    suspend fun all(): List<TagEntity>
}
