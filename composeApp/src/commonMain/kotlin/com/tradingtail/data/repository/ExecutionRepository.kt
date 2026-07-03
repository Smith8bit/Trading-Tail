package com.tradingtail.data.repository

import com.tradingtail.data.local.dao.ExecutionDao
import com.tradingtail.data.local.entity.ExecutionEntity

/** The only seam UI/usecases touch for executions — never the DAO directly. */
class ExecutionRepository(private val dao: ExecutionDao) {
    suspend fun add(execution: ExecutionEntity): Long = dao.insert(execution)
    suspend fun addAll(executions: List<ExecutionEntity>): List<Long> = dao.insertAll(executions)
    suspend fun bySymbol(symbol: String): List<ExecutionEntity> = dao.bySymbol(symbol)
    suspend fun all(): List<ExecutionEntity> = dao.all()
}
