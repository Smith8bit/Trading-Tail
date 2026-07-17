package com.tradingtail

import com.tradingtail.data.local.TradeDatabase
import com.tradingtail.data.remote.SyncConfig
import com.tradingtail.data.remote.createSupabase
import com.tradingtail.data.repository.ExecutionRepository
import com.tradingtail.data.repository.TradeNoteRepository
import com.tradingtail.data.repository.TradeRepository
import com.tradingtail.data.sync.SyncManager
import com.tradingtail.domain.usecase.BuildTradesFromExecutions
import com.tradingtail.domain.usecase.CalculateCalendarPnl
import com.tradingtail.domain.usecase.CalculatePnlByHour
import com.tradingtail.domain.usecase.CalculatePnlBySymbol
import com.tradingtail.domain.usecase.CalculateWinRate
import com.tradingtail.domain.usecase.DeleteTrade
import com.tradingtail.domain.usecase.ImportWebullPdf
import com.tradingtail.domain.usecase.RebuildTradesForSymbol
import com.tradingtail.domain.usecase.RecordQuickTrade
import com.tradingtail.domain.usecase.UpdateExecution

/**
 * ponytail: hand-wired object graph — one class, no DI framework. Each platform builds the live
 * [TradeDatabase] and constructs this once, then hands it to [App]. UI reaches usecases/repos
 * only through here.
 */
class AppModule(db: TradeDatabase, syncConfig: SyncConfig? = null) {
    val executionRepo = ExecutionRepository(db.executionDao())
    val tradeRepo = TradeRepository(db.tradeDao())
    val tradeNoteRepo = TradeNoteRepository(db.tradeNoteDao())

    private val rebuild = RebuildTradesForSymbol(executionRepo, tradeRepo, BuildTradesFromExecutions())

    /** Null when no credentials are configured — the app is fully functional offline; sync just stays off. */
    val syncManager: SyncManager? = syncConfig?.let {
        SyncManager(createSupabase(it), executionRepo, tradeNoteRepo, rebuild)
    }
    val recordQuickTrade = RecordQuickTrade(executionRepo, rebuild)
    val importWebullPdf = ImportWebullPdf(executionRepo, rebuild)
    val deleteTrade = DeleteTrade(executionRepo, rebuild)
    val updateExecution = UpdateExecution(executionRepo, rebuild)
    val calculateCalendarPnl = CalculateCalendarPnl()
    val calculateWinRate = CalculateWinRate()
    val calculatePnlBySymbol = CalculatePnlBySymbol()
    val calculatePnlByHour = CalculatePnlByHour()
}
