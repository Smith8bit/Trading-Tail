package com.tradingtail.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.TagEntity
import com.tradingtail.data.local.entity.TradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionDao {
    @Insert
    suspend fun insert(execution: ExecutionEntity): Long

    @Insert
    suspend fun insertAll(executions: List<ExecutionEntity>): List<Long>

    @Query("SELECT * FROM executions WHERE symbol = :symbol ORDER BY timestamp, id")
    suspend fun bySymbol(symbol: String): List<ExecutionEntity>

    @Query("SELECT * FROM executions ORDER BY timestamp, id")
    suspend fun all(): List<ExecutionEntity>
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
