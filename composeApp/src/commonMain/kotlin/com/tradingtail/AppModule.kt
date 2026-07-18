package com.tradingtail

import com.tradingtail.common.AppSettings
import com.tradingtail.common.LocalSettings
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
class AppModule(db: TradeDatabase, localSettings: LocalSettings) {
    val executionRepo = ExecutionRepository(db.executionDao())
    val tradeRepo = TradeRepository(db.tradeDao())
    val tradeNoteRepo = TradeNoteRepository(db.tradeNoteDao())

    /** The device-local profile + sync credentials (never synced). Drives onboarding and the account chip. */
    val settings = AppSettings(localSettings)

    private val rebuild = RebuildTradesForSymbol(executionRepo, tradeRepo, BuildTradesFromExecutions())

    /**
     * Sync client, built from the user's own credentials in [settings]. Null until they enter a Supabase
     * URL + key in Settings — so the released app ships with sync OFF and is fully functional offline.
     * Read once at startup; new creds apply on next launch.
     */
    val syncManager: SyncManager? = settings.current.let { d ->
        if (d.supabaseUrl.isNotBlank() && d.supabaseKey.isNotBlank()) {
            SyncManager(createSupabase(SyncConfig(d.supabaseUrl, d.supabaseKey)), executionRepo, tradeNoteRepo, rebuild)
        } else {
            null
        }
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
