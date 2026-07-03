package com.tradingtail.data.repository

import com.tradingtail.data.local.dao.TradeDao
import com.tradingtail.data.local.entity.TradeEntity

/** The only seam UI/usecases touch for trades — never the DAO directly. */
class TradeRepository(private val dao: TradeDao) {
    suspend fun saveAll(trades: List<TradeEntity>): List<Long> = dao.insertAll(trades)
    suspend fun deleteForSymbol(symbol: String) = dao.deleteBySymbol(symbol)
    suspend fun all(): List<TradeEntity> = dao.all()
}
