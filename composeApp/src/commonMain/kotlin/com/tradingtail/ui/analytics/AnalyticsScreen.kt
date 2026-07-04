package com.tradingtail.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO
import com.tradingtail.common.formatMoney
import com.tradingtail.data.repository.TradeRepository
import com.tradingtail.domain.usecase.CalculatePnlByHour
import com.tradingtail.domain.usecase.CalculatePnlBySymbol
import com.tradingtail.domain.usecase.CalculateWinRate
import com.tradingtail.domain.usecase.HourPnl
import com.tradingtail.domain.usecase.SymbolPnl
import com.tradingtail.domain.usecase.WinRateSummary
import com.tradingtail.ui.theme.pnlColor
import com.tradingtail.ui.theme.pnlFill
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** UI talks to the repo only through here; the pure aggregation usecases do the work. */
class AnalyticsViewModel(
    repo: TradeRepository,
    winRate: CalculateWinRate,
    bySymbol: CalculatePnlBySymbol,
    byHour: CalculatePnlByHour,
) {
    val winRate: Flow<WinRateSummary> = repo.allFlow().map { winRate(it) }
    val bySymbol: Flow<List<SymbolPnl>> = repo.allFlow().map { bySymbol(it) }
    val byHour: Flow<List<HourPnl>> = repo.allFlow().map { byHour(it) }
}

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel, modifier: Modifier = Modifier) {
    val win by vm.winRate.collectAsState(initial = WinRateSummary(0, 0, 0, 0.0, ZERO))
    val bySymbol by vm.bySymbol.collectAsState(initial = emptyList())
    val byHour by vm.byHour.collectAsState(initial = emptyList())

    Column(
        modifier = modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WinRateHero(win)

        Section("P&L by Symbol") {
            if (bySymbol.isEmpty()) Text("No trades yet.")
            val max = maxAbs(bySymbol.map { it.pnl })
            for (s in bySymbol) BarRow("${s.symbol} (${s.trades}t)", s.pnl, max)
        }

        Section("P&L by Hour (Bangkok)") {
            if (byHour.isEmpty()) Text("No trades yet.")
            val max = maxAbs(byHour.map { it.pnl })
            for (h in byHour) {
                BarRow(h.hour.toString().padStart(2, '0') + ":00 (${h.trades}t)", h.pnl, max)
            }
        }
    }
}

@Composable
private fun WinRateHero(win: WinRateSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${(win.winRate * 100).toInt()}%",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                "Win rate — ${win.wins}W / ${win.losses}L / ${win.breakeven}BE",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total P&L", style = MaterialTheme.typography.titleMedium)
                Text(
                    formatMoney(win.totalPnl),
                    color = pnlColor(win.totalPnl),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    }
}

/** A row whose background fill length is proportional to |P&L| over the section max. */
@Composable
private fun BarRow(label: String, pnl: BigDecimal, max: BigDecimal) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth(fraction(pnl, max)).height(30.dp)
                .background(pnlFill(pnl), RoundedCornerShape(6.dp)),
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                formatMoney(pnl),
                color = pnlColor(pnl),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

private fun abs(x: BigDecimal): BigDecimal = if (x < ZERO) ZERO.subtract(x) else x

private fun maxAbs(pnls: List<BigDecimal>): BigDecimal =
    pnls.fold(ZERO) { m, p -> abs(p).let { if (it > m) it else m } }

private fun fraction(pnl: BigDecimal, max: BigDecimal): Float =
    if (max <= ZERO) 0f else (abs(pnl).toFloat() / max.toFloat()).coerceIn(0f, 1f)
