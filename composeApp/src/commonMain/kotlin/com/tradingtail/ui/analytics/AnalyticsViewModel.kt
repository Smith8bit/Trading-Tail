package com.tradingtail.ui.analytics

import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.repository.ExecutionRepository
import com.tradingtail.data.repository.TradeRepository
import com.tradingtail.domain.usecase.CalculatePnlByHour
import com.tradingtail.domain.usecase.CalculatePnlBySymbol
import com.tradingtail.domain.usecase.CalculateWinRate
import com.tradingtail.domain.usecase.HourPnl
import com.tradingtail.domain.usecase.SymbolPnl
import com.tradingtail.domain.usecase.WinRateSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** UI talks to the repo only through here; the pure aggregation usecases do the work. */
class AnalyticsViewModel(
    repo: TradeRepository,
    execRepo: ExecutionRepository,
    winRate: CalculateWinRate,
    bySymbol: CalculatePnlBySymbol,
    byHour: CalculatePnlByHour,
) {
    val winRate: Flow<WinRateSummary> = repo.allFlow().map { winRate(it) }
    val bySymbol: Flow<List<SymbolPnl>> = repo.allFlow().map { bySymbol(it) }
    val byHour: Flow<List<HourPnl>> = repo.allFlow().map { byHour(it) }
    val trades: Flow<List<TradeEntity>> = repo.allFlow() // week strip, cumulative curve, recent list
    val executions: Flow<List<ExecutionEntity>> = execRepo.allFlow() // dashboard fees/volume/entry-price
}
