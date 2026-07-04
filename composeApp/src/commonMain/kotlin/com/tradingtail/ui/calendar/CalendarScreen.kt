package com.tradingtail.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BkkDate
import com.tradingtail.common.YearMonth
import com.tradingtail.common.ZERO
import com.tradingtail.common.bkkDate
import com.tradingtail.common.currentYearMonth
import com.tradingtail.common.daysInMonth
import com.tradingtail.common.firstWeekday
import com.tradingtail.common.formatBangkok
import com.tradingtail.common.formatMoney
import com.tradingtail.common.formatMoneyShort
import com.tradingtail.common.monthLabel
import com.tradingtail.common.nowMillis
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.repository.TradeRepository
import com.tradingtail.domain.usecase.CalculateCalendarPnl
import com.tradingtail.domain.usecase.DayPnl
import com.tradingtail.ui.theme.pnlColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** UI talks to the repo only through here; the pure aggregation usecase does the work. */
class CalendarViewModel(repo: TradeRepository, calc: CalculateCalendarPnl) {
    val calendar: Flow<Map<BkkDate, DayPnl>> = repo.allFlow().map { calc(it) }
    val trades: Flow<List<TradeEntity>> = repo.allFlow() // for the day-detail sheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: CalendarViewModel, modifier: Modifier = Modifier) {
    val byDay by vm.calendar.collectAsState(initial = emptyMap())
    val trades by vm.trades.collectAsState(initial = emptyList())
    var ym by remember { mutableStateOf(currentYearMonth()) }
    var selectedDay by remember { mutableStateOf<BkkDate?>(null) }
    val today = remember { bkkDate(nowMillis()) }

    val monthDays = byDay.filterKeys { it.year == ym.year && it.month == ym.month }
    val monthTotal = monthDays.values.fold(ZERO) { acc, d -> acc.add(d.pnl) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // ponytail: cap grid width so cells stay square-ish on a wide desktop window instead of ballooning.
        Column(modifier = Modifier.widthIn(max = 480.dp).padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { ym = ym.prev() }) { Text("‹") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(monthLabel(ym), style = MaterialTheme.typography.titleMedium)
                    Text(
                        formatMoney(monthTotal),
                        color = pnlColor(monthTotal),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextButton(onClick = { ym = ym.next() }) { Text("›") }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (d in listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")) {
                    Text(
                        d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            // Leading blanks (Monday=1..Sunday=7), then each day, padded to full weeks of 7.
            val lead = firstWeekday(ym) - 1
            val total = daysInMonth(ym)
            val cells = buildList {
                repeat(lead) { add(null) }
                for (day in 1..total) add(day)
                while (size % 7 != 0) add(null)
            }
            for (week in cells.chunked(7)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (day in week) {
                        val date = day?.let { BkkDate(ym.year, ym.month, it) }
                        DayCell(
                            day = day,
                            stat = date?.let { monthDays[it] },
                            isToday = date == today,
                            onClick = { if (date != null) selectedDay = date },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    selectedDay?.let { date ->
        val dayTrades = trades.filter { bkkDate(it.exitTimestamp) == date }
        ModalBottomSheet(
            onDismissRequest = { selectedDay = null },
            sheetState = rememberModalBottomSheetState(),
        ) {
            DaySheet(date, dayTrades)
        }
    }
}

@Composable
private fun DaySheet(date: BkkDate, dayTrades: List<TradeEntity>) {
    val total = dayTrades.fold(ZERO) { acc, t -> acc.add(t.realizedPnl) }
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${date.year}-${pad(date.month)}-${pad(date.day)}", fontWeight = FontWeight.Bold)
            Text(
                formatMoney(total),
                color = pnlColor(total),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        if (dayTrades.isEmpty()) Text("No trades.", style = MaterialTheme.typography.bodyMedium)
        for (t in dayTrades) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${t.symbol}   ${formatBangkok(t.exitTimestamp)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatMoney(t.realizedPnl),
                    color = pnlColor(t.realizedPnl),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DayCell(day: Int?, stat: DayPnl?, isToday: Boolean, onClick: () -> Unit, modifier: Modifier) {
    var cell = modifier.aspectRatio(1f).padding(2.dp)
    if (isToday) cell = cell.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
    Box(modifier = if (day != null) cell.clickable(onClick = onClick) else cell) {
        if (day == null) return@Box
        Column(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            Text(day.toString(), style = MaterialTheme.typography.labelSmall)
            if (stat != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatMoneyShort(stat.pnl),
                            color = pnlColor(stat.pnl),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text("${stat.trades}t", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun pad(n: Int): String = n.toString().padStart(2, '0')
