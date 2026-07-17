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

    // The app-facing reads all hide tombstones, so the matcher and UI never see a soft-deleted fill.
    @Query("SELECT * FROM executions WHERE symbol = :symbol AND deleted = 0 ORDER BY timestamp, id")
    suspend fun bySymbol(symbol: String): List<ExecutionEntity>

    @Query("SELECT * FROM executions WHERE deleted = 0 ORDER BY timestamp, id")
    suspend fun all(): List<ExecutionEntity>

    // Reactive feed for the dashboard's fee/volume/entry-price widgets.
    @Query("SELECT * FROM executions WHERE deleted = 0 ORDER BY timestamp, id")
    fun allFlow(): Flow<List<ExecutionEntity>>

    // Soft delete: keep the row as a tombstone so the deletion syncs, and bump the clock so it wins.
    @Query("UPDATE executions SET deleted = 1, updatedAt = :now WHERE id IN (:ids)")
    suspend fun softDeleteByIds(ids: List<Long>, now: Long)

    // --- sync ---
    @Query("SELECT * FROM executions WHERE syncId = :syncId LIMIT 1")
    suspend fun bySyncId(syncId: String): ExecutionEntity?

    // Every row including tombstones — the sync layer needs to push deletions, not hide them.
    @Query("SELECT * FROM executions")
    suspend fun allForSync(): List<ExecutionEntity>
}

/**
 * Notes keyed by a trade's natural identity, never by `trades.id` — the matcher truncates and
 * re-derives that table, so a note hung off a row id would not survive the next fill.
 */
@Dao
interface TradeNoteDao {
    // Also the sync apply path: the composite PK is the natural key, so @Upsert resolves a remote note
    // (including a tombstone) against the local one for free.
    @Upsert
    suspend fun upsert(note: TradeNoteEntity)

    // Tombstones are hidden from the app but kept in the table so a cleared note stays cleared after sync.
    @Query("SELECT * FROM trade_notes WHERE symbol = :symbol AND entryTs = :entryTs AND exitTs = :exitTs AND deleted = 0")
    fun byKeyFlow(symbol: String, entryTs: Long, exitTs: Long): Flow<TradeNoteEntity?>

    // Powers the calendar's note glyph.
    @Query("SELECT * FROM trade_notes WHERE deleted = 0")
    fun allFlow(): Flow<List<TradeNoteEntity>>

    // Every note including tombstones, for the sync layer.
    @Query("SELECT * FROM trade_notes")
    suspend fun allForSync(): List<TradeNoteEntity>
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
