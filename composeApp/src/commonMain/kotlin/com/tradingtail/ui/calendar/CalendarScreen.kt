package com.tradingtail.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.tradingtail.common.monthLabel
import com.tradingtail.common.nowMillis
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.repository.TradeRepository
import com.tradingtail.domain.usecase.CalculateCalendarPnl
import com.tradingtail.domain.usecase.DayPnl
import com.tradingtail.ui.theme.Space
import com.tradingtail.ui.theme.pnlColor
import com.tradingtail.ui.theme.pnlFill
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
    // Days with a journal note → the note glyph on that day cell lights up (else it's a muted affordance).
    val notedDays = remember(trades) {
        trades.filter { !it.notes.isNullOrBlank() }.map { bkkDate(it.exitTimestamp) }.toSet()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // ponytail: one breakpoint — <600dp (phones) drops the Total column, shrinks day cells, and
        // halves the year grid to 2 mini-months per row. Matches the Analytics/Dashboard convention.
        val compact = maxWidth < 600.dp
        Column(
            modifier = Modifier.widthIn(max = 1500.dp).fillMaxWidth().padding(if (compact) Space.sm else Space.lg)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) Space.md else Space.xl),
        ) {
            MonthCard(
                ym = ym,
                byDay = byDay,
                today = today,
                notedDays = notedDays,
                onPrev = { ym = ym.prev() },
                onNext = { ym = ym.next() },
                onDayClick = { selectedDay = it },
                compact = compact,
                modifier = Modifier.widthIn(max = 1100.dp), // big card stays 1100; wider stretches its day cells
            )
            YearOverview(
                year = ym.year,
                active = ym,
                byDay = byDay,
                onPrevYear = { ym = ym.copy(year = ym.year - 1) },
                onNextYear = { ym = ym.copy(year = ym.year + 1) },
                onOpen = { ym = it },
                modifier = Modifier.fillMaxWidth(), // mini grid fills the wider 1500 band
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
    notedDays: Set<BkkDate>,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDayClick: (BkkDate) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val monthTotal = byDay.filterKeys { it.year == ym.year && it.month == ym.month }
        .values.fold(ZERO) { acc, d -> acc.add(d.pnl) }

    val monthNav: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrev) { Text("‹") }
            Text(monthLabel(ym), style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            TextButton(onClick = onNext) { Text("›") }
        }
    }
    val monthPnl: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Monthly P&L: ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            Text(formatMoney(monthTotal), color = pnlColor(monthTotal), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(if (compact) Space.sm else Space.lg)) {
            // Header: month name (with nav) + running monthly P&L — stacks on narrow so it can't overflow.
            if (compact) {
                monthNav(); monthPnl()
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    monthNav(); monthPnl()
                }
            }

            Spacer(Modifier.height(Space.md))

            // Column headers: Sun..Sat (+ a Total column for the weekly summary on wide screens).
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                for (d in WEEKDAYS) {
                    Text(
                        d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                if (!compact) Text(
                    "Total",
                    modifier = Modifier.weight(1.3f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            // minWeeks = 6 → the card is always 6 week-rows tall, so every active month is the same size.
            monthCells(ym, minWeeks = 6).chunked(7).forEachIndexed { i, week ->
                val monthStats = week.filter { it.inMonth }.mapNotNull { byDay[it.date] }
                val weekTotal = monthStats.fold(ZERO) { acc, s -> acc.add(s.pnl) }
                val weekTrades = monthStats.sumOf { it.trades }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    for (cell in week) {
                        DayCell(
                            cell = cell,
                            stat = if (cell.inMonth) byDay[cell.date] else null,
                            isToday = cell.date == today,
                            hasNote = cell.date in notedDays,
                            onClick = { if (cell.inMonth) onDayClick(cell.date) },
                            compact = compact,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (!compact) WeekCell("Week ${i + 1}", weekTrades, weekTotal, Modifier.weight(1.3f))
                }
            }
        }
    }
}

/** One day tile: day number + note glyph top row, then net P&L + trade count. Adjacent-month days are greyed. */
@Composable
private fun DayCell(cell: CalCell, stat: DayPnl?, isToday: Boolean, hasNote: Boolean, onClick: () -> Unit, compact: Boolean, modifier: Modifier) {
    val border = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val dayColor = if (cell.inMonth) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
    var box = modifier.height(if (compact) 56.dp else 88.dp).clip(RoundedCornerShape(8.dp))
        .border(1.dp, border, RoundedCornerShape(8.dp))
    if (cell.inMonth) box = box.clickable(onClick = onClick)

    Column(modifier = box.padding(if (compact) Space.xs else Space.sm)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(cell.date.day.toString(), color = dayColor, style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            // Note glyph dropped on narrow cells — no room next to the day number.
            if (cell.inMonth && !compact) {
                Icon(
                    NoteIcon,
                    contentDescription = if (hasNote) "Has note" else null,
                    tint = if (hasNote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        if (cell.inMonth) {
            Spacer(Modifier.weight(1f))
            Text(
                formatMoney(stat?.pnl ?: ZERO), // exact figure, no abbreviation/round-up
                color = if (stat == null) MaterialTheme.colorScheme.onSurfaceVariant else pnlColor(stat.pnl),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis, // never let a wide figure silently clip into a shorter-looking number
            )
            // Trade count dropped on narrow cells to leave the exact P&L room; tap the day for detail.
            if (!compact) Text(
                "${stat?.trades ?: 0} trades",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

/** Right-rail weekly summary matching the mock's "Total" column: Week N, net P&L, trade count. */
@Composable
private fun WeekCell(label: String, trades: Int, total: BigDecimal, modifier: Modifier) {
    Column(
        modifier = modifier.height(88.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(Space.sm),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(
            formatMoney(total), // exact figure, no abbreviation/round-up
            color = if (trades == 0) MaterialTheme.colorScheme.onSurfaceVariant else pnlColor(total),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        Text("$trades trades", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
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
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrevYear) { Text("‹") }
            Text("$year", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onNextYear) { Text("›") }
        }
        // Fluid column count off the actual grid width: 2 (phone) → 3 (medium) → 4 (wide).
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cols = when {
                maxWidth < 560.dp -> 2
                maxWidth < 880.dp -> 3
                else -> 4
            }
            val tileNarrow = maxWidth / cols < 220.dp // short titles once a tile gets tight
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                (1..12).chunked(cols).forEach { rowMonths ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        for (m in rowMonths) {
                            val ym = YearMonth(year, m)
                            MiniMonth(ym, active == ym, byDay, compact = tileNarrow, onOpen = { onOpen(ym) }, modifier = Modifier.weight(1f))
                        }
                        repeat(cols - rowMonths.size) { Spacer(Modifier.weight(1f)) } // keep a short last row aligned
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMonth(ym: YearMonth, isActive: Boolean, byDay: Map<BkkDate, DayPnl>, compact: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(if (compact) Space.sm else Space.md)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Short month name on narrow cards so the title + Pill fit; full name on wide.
                Text(
                    if (compact) "${MONTH_NAMES[ym.month - 1].take(3)} ${ym.year}" else "${MONTH_NAMES[ym.month - 1]}, ${ym.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Pill(if (isActive) "Active" else "Open", isActive, onOpen)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                for (d in WEEKDAYS) {
                    Text(
                        d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            // minWeeks = 6 pads every month to 6 rows so all mini-cards are the same height.
            monthCells(ym, minWeeks = 6).chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (cell in week) {
                        val stat = if (cell.inMonth) byDay[cell.date] else null
                        // Traded days become filled color tiles (gain/loss heatmap via the shared pnlFill token).
                        Box(
                            modifier = Modifier.weight(1f).aspectRatio(1f).padding(1.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (stat != null) pnlFill(stat.pnl) else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                cell.date.day.toString(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (stat != null) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    stat != null -> pnlColor(stat.pnl)
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
}

/** Small "Open"/"Active" button — Active is the currently opened month (green outline). */
@Composable
private fun Pill(text: String, active: Boolean, onClick: () -> Unit) {
    val c = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .border(1.dp, if (active) c else c.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.sm),
    ) {
        Text(text, color = c, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------------------------
// Day-detail sheet (unchanged behaviour)
// ---------------------------------------------------------------------------------------------

@Composable
private fun DaySheet(date: BkkDate, dayTrades: List<TradeEntity>) {
    val total = dayTrades.fold(ZERO) { acc, t -> acc.add(t.realizedPnl) }
    Column(modifier = Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.lg, bottom = Space.xl)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm),
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

// ponytail: a lined-page glyph stroked by hand — a "document" isn't in material-icons-core and it's
// not worth pulling material-icons-extended for one 13dp mark. Tinted by the caller (Icon).
private val NoteIcon: ImageVector = ImageVector.Builder(
    name = "Note",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
        moveTo(7f, 3f); lineTo(14f, 3f); lineTo(18f, 7f); lineTo(18f, 21f); lineTo(7f, 21f); close()
    }
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) { moveTo(10f, 11f); lineTo(15f, 11f) }
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) { moveTo(10f, 15f); lineTo(15f, 15f) }
}.build()

// ---------------------------------------------------------------------------------------------
// Pure grid layout (testable)
// ---------------------------------------------------------------------------------------------

/** One slot in the month grid: a real Bangkok date, flagged whether it belongs to the shown month. */
data class CalCell(val date: BkkDate, val inMonth: Boolean)

/**
 * Sunday-first grid of [ym], padded with the trailing days of the previous month and the leading
 * days of the next so every week row is a full 7 cells (matches the TraderVue calendar layout).
 * [minWeeks] pads out to at least that many rows so a set of grids can be forced to equal height.
 */
fun monthCells(ym: YearMonth, minWeeks: Int = 0): List<CalCell> = buildList {
    val lead = firstWeekday(ym) % 7 // ISO Mon=1..Sun=7 → Sunday-first offset (Sun=0)
    val prev = ym.prev()
    val prevTotal = daysInMonth(prev)
    for (i in 0 until lead) add(CalCell(BkkDate(prev.year, prev.month, prevTotal - lead + 1 + i), false))
    for (day in 1..daysInMonth(ym)) add(CalCell(BkkDate(ym.year, ym.month, day), true))
    val next = ym.next()
    var nd = 1
    while (size % 7 != 0 || size < minWeeks * 7) add(CalCell(BkkDate(next.year, next.month, nd++), false))
}
