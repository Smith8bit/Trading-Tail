package com.tradingtail

import com.tradingtail.data.local.TradeDatabase
import com.tradingtail.data.repository.ExecutionRepository
import com.tradingtail.data.repository.TradeRepository
import com.tradingtail.domain.usecase.BuildTradesFromExecutions
import com.tradingtail.domain.usecase.CalculateCalendarPnl
import com.tradingtail.domain.usecase.CalculatePnlByHour
import com.tradingtail.domain.usecase.CalculatePnlBySymbol
import com.tradingtail.domain.usecase.CalculateWinRate
import com.tradingtail.domain.usecase.DeleteTrade
import com.tradingtail.domain.usecase.RebuildTradesForSymbol
import com.tradingtail.domain.usecase.RecordQuickTrade

/**
 * ponytail: hand-wired object graph — one class, no DI framework. Each platform builds the live
 * [TradeDatabase] and constructs this once, then hands it to [App]. UI reaches usecases/repos
 * only through here.
 */
class AppModule(db: TradeDatabase) {
    private val executionRepo = ExecutionRepository(db.executionDao())
    val tradeRepo = TradeRepository(db.tradeDao())

    private val rebuild = RebuildTradesForSymbol(executionRepo, tradeRepo, BuildTradesFromExecutions())
    val recordQuickTrade = RecordQuickTrade(executionRepo, rebuild)
    val deleteTrade = DeleteTrade(executionRepo, rebuild)
    val calculateCalendarPnl = CalculateCalendarPnl()
    val calculateWinRate = CalculateWinRate()
    val calculatePnlBySymbol = CalculatePnlBySymbol()
    val calculatePnlByHour = CalculatePnlByHour()
}
