package com.tradingtail.data.repository

import com.tradingtail.common.nowMillis
import com.tradingtail.common.randomSyncId
import com.tradingtail.data.local.dao.ExecutionDao
import com.tradingtail.data.local.entity.ExecutionEntity
import kotlinx.coroutines.flow.Flow

/**
 * The only seam UI/usecases touch for executions — never the DAO directly.
 *
 * This is also the single place sync bookkeeping is stamped: every local write gets a fresh
 * [ExecutionEntity.updatedAt] and, on first insert, a [ExecutionEntity.syncId]. Usecases construct
 * executions with the field defaults and stay unaware of sync entirely.
 */
class ExecutionRepository(private val dao: ExecutionDao) {
    suspend fun add(execution: ExecutionEntity): Long = dao.insert(execution.stamped())
    suspend fun addAll(executions: List<ExecutionEntity>): List<Long> = dao.insertAll(executions.map { it.stamped() })
    suspend fun update(execution: ExecutionEntity) = dao.update(execution.stamped())
    suspend fun bySymbol(symbol: String): List<ExecutionEntity> = dao.bySymbol(symbol)
    suspend fun all(): List<ExecutionEntity> = dao.all()
    fun allFlow(): Flow<List<ExecutionEntity>> = dao.allFlow()
    suspend fun deleteByIds(ids: List<Long>) = dao.softDeleteByIds(ids, nowMillis())

    // --- sync ---
    /** Every row including tombstones — the sync layer diffs this against the remote snapshot. */
    suspend fun allForSync(): List<ExecutionEntity> = dao.allForSync()

    /**
     * Apply a row pulled from remote verbatim — its [syncId] and clock are the winning values, so it
     * bypasses [stamped]. Matches an existing local row by syncId; the local Room id is preserved on
     * update (it's device-local) and freshly assigned on insert.
     */
    suspend fun applyRemote(remote: ExecutionEntity) {
        val local = dao.bySyncId(remote.syncId)
        if (local == null) dao.insert(remote.copy(id = 0)) else dao.update(remote.copy(id = local.id))
    }
}

/** Assign a stable id on first insert (keep any existing), and always advance the last-write clock. */
private fun ExecutionEntity.stamped(): ExecutionEntity =
    copy(syncId = syncId.ifEmpty { randomSyncId() }, updatedAt = nowMillis())
