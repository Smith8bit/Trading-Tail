package com.tradingtail.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tradingtail.common.BigDecimal
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

private val WEEKDAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: CalendarViewModel, modifier: Modifier = Modifier) {
    val byDay by vm.calendar.collectAsState(initial = emptyMap())
    val trades by vm.trades.collectAsState(initial = emptyList())
    var ym by remember { mutableStateOf(currentYearMonth()) }
    var selectedDay by remember { mutableStateOf<BkkDate?>(null) }
    val today = remember { bkkDate(nowMillis()) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth().padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MonthCard(
                ym = ym,
                byDay = byDay,
                today = today,
                onPrev = { ym = ym.prev() },
                onNext = { ym = ym.next() },
                onDayClick = { selectedDay = it },
            )
            YearOverview(
                year = ym.year,
                active = ym,
                byDay = byDay,
                onPrevYear = { ym = ym.copy(year = ym.year - 1) },
                onNextYear = { ym = ym.copy(year = ym.year + 1) },
                onOpen = { ym = it },
            )
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

// ---------------------------------------------------------------------------------------------
// Big month card
// ---------------------------------------------------------------------------------------------

@Composable
private fun MonthCard(
    ym: YearMonth,
    byDay: Map<BkkDate, DayPnl>,
    today: BkkDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDayClick: (BkkDate) -> Unit,
) {
    val monthTotal = byDay.filterKeys { it.year == ym.year && it.month == ym.month }
        .values.fold(ZERO) { acc, d -> acc.add(d.pnl) }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: month name (with nav) left, running monthly P&L right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPrev) { Text("‹") }
                    Text(monthLabel(ym), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onNext) { Text("›") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Monthly P&L: ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        formatMoney(monthTotal),
                        color = pnlColor(monthTotal),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Column headers: Sun..Sat + a Total column for the weekly summary.
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                for (d in WEEKDAYS) {
                    Text(
                        d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    "Total",
                    modifier = Modifier.weight(1.3f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            monthCells(ym).chunked(7).forEachIndexed { i, week ->
                val monthStats = week.filter { it.inMonth }.mapNotNull { byDay[it.date] }
                val weekTotal = monthStats.fold(ZERO) { acc, s -> acc.add(s.pnl) }
                val weekTrades = monthStats.sumOf { it.trades }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (cell in week) {
                        DayCell(
                            cell = cell,
                            stat = if (cell.inMonth) byDay[cell.date] else null,
                            isToday = cell.date == today,
                            onClick = { if (cell.inMonth) onDayClick(cell.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    WeekCell("Week ${i + 1}", weekTrades, weekTotal, Modifier.weight(1.3f))
                }
            }
        }
    }
}

/** One day tile: day number top-left, then net P&L + trade count. Adjacent-month days are greyed. */
@Composable
private fun DayCell(cell: CalCell, stat: DayPnl?, isToday: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val border = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val dayColor = if (cell.inMonth) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
    var box = modifier.height(60.dp).clip(RoundedCornerShape(8.dp))
        .border(1.dp, border, RoundedCornerShape(8.dp))
    if (cell.inMonth) box = box.clickable(onClick = onClick)

    Column(modifier = box.padding(6.dp)) {
        Text(cell.date.day.toString(), color = dayColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        if (cell.inMonth) {
            Spacer(Modifier.weight(1f))
            Text(
                formatMoneyShort(stat?.pnl ?: ZERO),
                color = if (stat == null) MaterialTheme.colorScheme.onSurfaceVariant else pnlColor(stat.pnl),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
            Text(
                "${stat?.trades ?: 0} trades",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/** Right-rail weekly summary matching the mock's "Total" column: Week N, net P&L, trade count. */
@Composable
private fun WeekCell(label: String, trades: Int, total: BigDecimal, modifier: Modifier) {
    Column(
        modifier = modifier.height(60.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(
            formatMoneyShort(total),
            color = if (trades == 0) MaterialTheme.colorScheme.onSurfaceVariant else pnlColor(total),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Text("$trades trades", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Year overview: 12 mini months
// ---------------------------------------------------------------------------------------------

@Composable
private fun YearOverview(
    year: Int,
    active: YearMonth,
    byDay: Map<BkkDate, DayPnl>,
    onPrevYear: () -> Unit,
    onNextYear: () -> Unit,
    onOpen: (YearMonth) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrevYear) { Text("‹") }
            Text("$year", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onNextYear) { Text("›") }
        }
        (1..12).chunked(3).forEach { rowMonths ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (m in rowMonths) {
                    val ym = YearMonth(year, m)
                    MiniMonth(ym, active == ym, byDay, onOpen = { onOpen(ym) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MiniMonth(ym: YearMonth, isActive: Boolean, byDay: Map<BkkDate, DayPnl>, onOpen: () -> Unit, modifier: Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${MONTH_NAMES[ym.month - 1]}, ${ym.year}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Pill(if (isActive) "Active" else "Open", isActive, onOpen)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                for (d in WEEKDAYS) {
                    Text(
                        d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            monthCells(ym).chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (cell in week) {
                        val stat = if (cell.inMonth) byDay[cell.date] else null
                        Text(
                            cell.date.day.toString(),
                            modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (stat != null) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                stat != null -> pnlColor(stat.pnl)          // traded days pop as a mini heatmap
                                cell.inMonth -> MaterialTheme.colorScheme.onBackground
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Small "Open"/"Active" button — Active is the currently opened month (green outline). */
@Composable
private fun Pill(text: String, active: Boolean, onClick: () -> Unit) {
    val c = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .border(1.dp, if (active) c else c.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = c, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------------------------
// Day-detail sheet (unchanged behaviour)
// ---------------------------------------------------------------------------------------------

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

private fun pad(n: Int): String = n.toString().padStart(2, '0')

// ---------------------------------------------------------------------------------------------
// Pure grid layout (testable)
// ---------------------------------------------------------------------------------------------

/** One slot in the month grid: a real Bangkok date, flagged whether it belongs to the shown month. */
data class CalCell(val date: BkkDate, val inMonth: Boolean)

/**
 * Sunday-first grid of [ym], padded with the trailing days of the previous month and the leading
 * days of the next so every week row is a full 7 cells (matches the TraderVue calendar layout).
 */
fun monthCells(ym: YearMonth): List<CalCell> = buildList {
    val lead = firstWeekday(ym) % 7 // ISO Mon=1..Sun=7 → Sunday-first offset (Sun=0)
    val prev = ym.prev()
    val prevTotal = daysInMonth(prev)
    for (i in 0 until lead) add(CalCell(BkkDate(prev.year, prev.month, prevTotal - lead + 1 + i), false))
    for (day in 1..daysInMonth(ym)) add(CalCell(BkkDate(ym.year, ym.month, day), true))
    val next = ym.next()
    var nd = 1
    while (size % 7 != 0) add(CalCell(BkkDate(next.year, next.month, nd++), false))
}
