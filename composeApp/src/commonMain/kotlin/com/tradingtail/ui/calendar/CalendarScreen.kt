package com.tradingtail.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.BkkDate
import com.tradingtail.common.YearMonth
import com.tradingtail.common.ZERO
import com.tradingtail.common.currentYearMonth
import com.tradingtail.common.daysInMonth
import com.tradingtail.common.firstWeekday
import com.tradingtail.common.monthLabel
import com.tradingtail.data.repository.TradeRepository
import com.tradingtail.domain.usecase.CalculateCalendarPnl
import com.tradingtail.domain.usecase.DayPnl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** UI talks to the repo only through here; the pure aggregation usecase does the work. */
class CalendarViewModel(repo: TradeRepository, calc: CalculateCalendarPnl) {
    val calendar: Flow<Map<BkkDate, DayPnl>> = repo.allFlow().map { calc(it) }
}

private val GAIN = Color(0xFF2E7D32)
private val LOSS = Color(0xFFC62828)

@Composable
fun CalendarScreen(vm: CalendarViewModel, modifier: Modifier = Modifier) {
    val byDay by vm.calendar.collectAsState(initial = emptyMap())
    var ym by remember { mutableStateOf(currentYearMonth()) }

    val monthDays = byDay.filterKeys { it.year == ym.year && it.month == ym.month }
    val monthTotal = monthDays.values.fold(ZERO) { acc, d -> acc.add(d.pnl) }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { ym = ym.prev() }) { Text("‹") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(monthLabel(ym), style = MaterialTheme.typography.titleMedium)
                Text(
                    "Month P&L " + monthTotal.toString(),
                    color = pnlColor(monthTotal),
                    style = MaterialTheme.typography.bodySmall,
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
                    DayCell(
                        day = day,
                        stat = day?.let { monthDays[BkkDate(ym.year, ym.month, it)] },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int?, stat: DayPnl?, modifier: Modifier) {
    Box(modifier = modifier.aspectRatio(1f).padding(2.dp)) {
        if (day == null) return@Box
        Column(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            Text(day.toString(), style = MaterialTheme.typography.labelSmall)
            if (stat != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stat.pnl.toString(),
                            color = pnlColor(stat.pnl),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text("${stat.trades}t", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun pnlColor(pnl: BigDecimal): Color = when {
    pnl > ZERO -> GAIN
    pnl < ZERO -> LOSS
    else -> Color.Unspecified
}
