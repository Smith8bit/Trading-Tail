package com.tradingtail.ui.calendar

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.tradingtail.ui.theme.GlassCard
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.tradingtail.common.formatMoneyShort
import com.tradingtail.common.monthLabel
import com.tradingtail.common.nowMillis
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.data.repository.TradeNoteRepository
import com.tradingtail.data.repository.TradeRepository
import com.tradingtail.domain.usecase.CalculateCalendarPnl
import com.tradingtail.domain.usecase.DayPnl
import com.tradingtail.ui.theme.FAB_CLEARANCE
import com.tradingtail.ui.theme.FontSize
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Radii
import com.tradingtail.ui.theme.Space
import com.tradingtail.ui.theme.pnlColor
import com.tradingtail.ui.theme.pnlFill
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** UI talks to the repo only through here; the pure aggregation usecase does the work. */
class CalendarViewModel(repo: TradeRepository, notes: TradeNoteRepository, calc: CalculateCalendarPnl) {
    val calendar: Flow<Map<BkkDate, DayPnl>> = repo.allFlow().map { calc(it) }
    val trades: Flow<List<TradeEntity>> = repo.allFlow() // for the day-detail sheet

    /**
     * Days carrying a journal note → the note glyph lights up. Keyed by the note's ENTRY day, the
     * same day CalculateCalendarPnl buckets by, or an overnight trade's glyph lands on a cell whose
     * figures don't include it.
     *
     * Sourced from `trade_notes`, not from the trades table: notes are keyed by natural identity and
     * survive the matcher's rebuilds. This glyph was previously fed by `TradeEntity.notes`, a column
     * no screen could ever write — so it was always dark.
     */
    val notedDays: Flow<Set<BkkDate>> = notes.allFlow().map { list ->
        list.map { bkkDate(it.entryTs) }.toSet()
    }
}

private val WEEKDAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: CalendarViewModel, modifier: Modifier = Modifier) {
    // initial = null, not empty: "no data yet" and "haven't asked the database yet" are different
    // facts. Conflating them made cold start render $0.00 across the board before Room's first
    // emission — a figure the user could read and believe. Nothing renders during the gap.
    val byDayOrNull by vm.calendar.collectAsState(initial = null)
    val tradesOrNull by vm.trades.collectAsState(initial = null)
    val byDay = byDayOrNull ?: return
    val trades = tradesOrNull ?: return
    var ym by remember { mutableStateOf(currentYearMonth()) }
    var selectedDay by remember { mutableStateOf<BkkDate?>(null) }
    val today = remember { bkkDate(nowMillis()) }
    val notedDays by vm.notedDays.collectAsState(initial = emptySet())

    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // ponytail: one breakpoint — <600dp (phones) drops the Total column, shrinks day cells, and
        // halves the year grid to 2 mini-months per row. Matches the Analytics/Dashboard convention.
        val compact = maxWidth < 600.dp
        val pad = if (compact) Space.sm else Space.lg
        // Fluid column count off the real grid width: 2 (phone) → 3 (medium) → 4 (wide). Computed here
        // rather than in a nested BoxWithConstraints — a lazy list needs to know its rows up front.
        val gridWidth = maxWidth.coerceAtMost(1500.dp) - pad * 2
        val cols = when {
            gridWidth < 780.dp -> 2
            gridWidth < 1120.dp -> 3
            else -> 4
        }
        val tileNarrow = gridWidth / cols < 220.dp // short titles once a tile gets tight
        val monthRows = remember(cols) { (1..12).chunked(cols) }
        // ponytail: Lazy, not Column(verticalScroll) — a scrolling Column composes EVERY child up front,
        // so all 12 mini-months (12 × 42 = ~500 Text nodes) were built on open even though a phone shows
        // about one. That was the freeze: 124ms worst frame here at density 1.0, and Android's CPU makes
        // that ~3-5x worse. LazyColumn builds only the rows on screen.
        LazyColumn(
            modifier = Modifier.widthIn(max = 1500.dp).fillMaxWidth().padding(pad),
            // Bottom clearance for the FAB + Import pill. Calendar was the one screen that never
            // reserved room for the floating chrome at all.
            contentPadding = PaddingValues(bottom = if (compact) FAB_CLEARANCE else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "month") {
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
            }
            item(key = "year-nav") {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = if (compact) Space.md else Space.xl, bottom = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NavArrow("‹", "Previous year", compact) { ym = ym.copy(year = ym.year - 1) }
                    Text("${ym.year}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    NavArrow("›", "Next year", compact) { ym = ym.copy(year = ym.year + 1) }
                }
            }
            items(monthRows, key = { it.first() }) { rowMonths ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = Space.md),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                ) {
                    for (m in rowMonths) {
                        val cellYm = YearMonth(ym.year, m)
                        MiniMonth(cellYm, ym == cellYm, byDay, compact = tileNarrow, onOpen = { ym = cellYm }, modifier = Modifier.weight(1f))
                    }
                    repeat(cols - rowMonths.size) { Spacer(Modifier.weight(1f)) } // keep a short last row aligned
                }
            }
        }
    }

    selectedDay?.let { date ->
        // Entry day, matching the cell's bucketing — tapping a cell must list exactly the trades
        // whose P&L that cell shows.
        val dayTrades = trades.filter { bkkDate(it.entryTimestamp) == date }
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
            NavArrow("‹", "Previous month", compact, onPrev)
            Text(monthLabel(ym), style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            NavArrow("›", "Next month", compact, onNext)
        }
    }
    val monthPnl: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Monthly P&L: ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            Text(formatMoney(monthTotal), color = pnlColor(monthTotal), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }

    GlassCard(modifier = modifier.fillMaxWidth()) {
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
            val weeks = remember(ym) { monthCells(ym, minWeeks = 6).chunked(7) }
            weeks.forEachIndexed { i, week ->
                val monthStats = week.filter { it.inMonth }.mapNotNull { byDay[it.date] }
                val weekTotal = monthStats.fold(ZERO) { acc, s -> acc.add(s.pnl) }
                val weekTrades = monthStats.sumOf { it.trades }
                // ponytail: 2dp is off the 4px grid, compact only — seven columns on a 360dp phone
                // leave ~43dp a cell, and every dp of gap comes straight out of the P&L figure's
                // width. Same precedent as the day-cell pads, which are calibrated not tokenized.
                // IntrinsicSize.Min + fillMaxHeight so the row is as tall as its tallest cell and every
                // cell matches it. The cells used to be a hard height(56.dp), which clipped a day's P&L
                // to "+$8…" once the user's font scale grew — the figure is the entire point of a P&L
                // calendar. Text scales with the system setting; the box has to scale with the text.
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = Space.xs),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 2.dp else Space.xs),
                ) {
                    for (cell in week) {
                        DayCell(
                            cell = cell,
                            stat = if (cell.inMonth) byDay[cell.date] else null,
                            isToday = cell.date == today,
                            hasNote = cell.date in notedDays,
                            onClick = { if (cell.inMonth) onDayClick(cell.date) },
                            compact = compact,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    if (!compact) WeekCell("Week ${i + 1}", weekTrades, weekTotal, Modifier.weight(1.3f))
                }
            }
        }
    }
}

/**
 * One day tile. Traded days carry the figures (exact P&L + count); no-trade days are just a day
 * number — a month of `$0.00 · 0 trades` was wallpaper that made traded days invisible at a squint.
 */
@Composable
private fun DayCell(cell: CalCell, stat: DayPnl?, isToday: Boolean, hasNote: Boolean, onClick: () -> Unit, compact: Boolean, modifier: Modifier) {
    // Full-strength hairline (not the faint variant) — the day grid is the screen's structure,
    // and the faint tier washed out against the glass tint.
    val border = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val dayColor = if (cell.inMonth) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(Radii.sm)
    // heightIn(min=), not height(): the floor keeps empty months on a uniform grid, and the cell grows
    // instead of clipping the figure when the system font scale does. The enclosing week Row measures
    // at IntrinsicSize.Min, so this minimum is what sets the row's height.
    var box = modifier.heightIn(min = if (compact) 56.dp else 88.dp).clip(shape).border(1.dp, border, shape)
    // Only traded days open the detail sheet — an empty day has nothing to show, so it offers no click.
    if (stat != null) box = box.clickable(onClick = onClick)

    Column(modifier = box.padding(if (compact) 2.dp else Space.sm)) { // see the week Row's note on 2dp
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Text(cell.date.day.toString(), color = dayColor, style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            // Only a real note earns the glyph (dropped on narrow cells — no room next to the number).
            if (hasNote && !compact) {
                Icon(
                    NoteIcon,
                    contentDescription = "Has note",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        if (cell.inMonth && stat != null) {
            Spacer(Modifier.weight(1f))
            Text(
                // Wide cells carry the exact figure. A compact cell has ~39dp — five monospace
                // characters at 12sp — so the full form ellipsized on EVERY traded day; the
                // design system's short form ("+$450", "−$2.3k") is what actually fits.
                // ponytail: a 4-figure day is still 6 chars and stays a hair tight — Ellipsis
                // below remains the guard. Fixing that needs fewer than 7 columns, i.e. a
                // different calendar, so it's deliberately left.
                if (compact) formatMoneyShort(stat.pnl) else formatMoney(stat.pnl),
                color = pnlColor(stat.pnl),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis, // never let a wide figure silently clip into a shorter-looking number
            )
            // Trade count dropped on narrow cells to leave the exact P&L room; tap the day for detail.
            if (!compact) Text(
                tradeCount(stat.trades),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}

private fun tradeCount(n: Int) = if (n == 1) "1 trade" else "$n trades"

/** Right-rail weekly summary ("Total" column). A week with no trades is just its muted label. */
@Composable
private fun WeekCell(label: String, trades: Int, total: BigDecimal, modifier: Modifier) {
    Column(
        modifier = modifier.height(88.dp).clip(RoundedCornerShape(Radii.sm))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(Space.sm),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (trades == 0) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        )
        if (trades > 0) {
            Spacer(Modifier.weight(1f))
            Text(
                formatMoney(total), // exact figure, no abbreviation/round-up
                color = pnlColor(total),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
            Text(tradeCount(trades), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Year overview: 12 mini months
// ---------------------------------------------------------------------------------------------

@Composable
private fun MiniMonth(ym: YearMonth, isActive: Boolean, byDay: Map<BkkDate, DayPnl>, compact: Boolean, onOpen: () -> Unit, modifier: Modifier) {
    // The whole card is the tap target (was a small "Open" pill per card — twelve buttons all saying
    // the same thing). The opened month is marked by the Signal-Blue selection border instead.
    val active = if (isActive) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium) else Modifier
    GlassCard(modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onOpen).then(active)) {
        Column(modifier = Modifier.padding(if (compact) Space.sm else Space.md)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // Same format as the big card's monthLabel ("April 2026"); abbreviated when narrow.
                    if (compact) "${MONTH_NAMES[ym.month - 1].take(3)} ${ym.year}" else monthLabel(ym),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                for (d in WEEKDAYS) {
                    Text(
                        // Always single-letter in the minis (the standard year-view idiom): "Wed" at
                        // 14sp clips in any tile under ~260dp, and full names add nothing to a heatmap.
                        d.take(1),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
            MiniGrid(ym, byDay, compact)
        }
    }
}

/**
 * A mini-month's 6×7 day grid, drawn as ONE Canvas instead of 42 Box+Text composables.
 *
 * ponytail: the grid is pure display — only the enclosing card is clickable, no cell has its own
 * semantics — so there is nothing to gain from 42 layout nodes per month. Twelve months of them was
 * ~500 Text nodes on the Calendar, and text measurement is the expensive part of a Compose frame:
 * that was the residual scroll lag after the list went lazy. A Canvas measures each digit against a
 * cached TextMeasurer and draws; there is one node per month instead of 84.
 *
 * minWeeks = 6 pads every month to 6 rows so all mini-cards are the same height — hence the 7:6 ratio.
 */
@Composable
private fun MiniGrid(ym: YearMonth, byDay: Map<BkkDate, DayPnl>, compact: Boolean) {
    val cells = remember(ym) { monthCells(ym, minWeeks = 6) }
    // 31 day numbers x a few color/weight variants — the cache covers a whole month after one pass.
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val tc = LocalTradeColors.current
    val inMonthColor = MaterialTheme.colorScheme.onBackground
    val outColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    // 12sp on a phone, not the old 14sp: two digits at 14sp left ~3dp of air inside a ~21dp cell, which
    // is what made the numbers read as cramped. 12sp is the design system's floor (FontSize.xs).
    val fontSize = if (compact) FontSize.xs else FontSize.sm
    Canvas(Modifier.fillMaxWidth().aspectRatio(7f / 6f)) {
        val cw = size.width / 7f
        val ch = size.height / 6f
        val inset = 1.dp.toPx()
        val radius = CornerRadius(4.dp.toPx())
        cells.forEachIndexed { i, cell ->
            val stat = if (cell.inMonth) byDay[cell.date] else null
            val x = (i % 7) * cw
            val y = (i / 7) * ch
            // Traded days become filled color tiles (the gain/loss heatmap — same tokens as pnlFill).
            if (stat != null) {
                val fill = if (stat.pnl > ZERO) tc.gainFill else if (stat.pnl < ZERO) tc.lossFill else Color.Transparent
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(x + inset, y + inset),
                    size = Size(cw - inset * 2, ch - inset * 2),
                    cornerRadius = radius,
                )
            }
            val color = when {
                stat != null -> if (stat.pnl > ZERO) tc.gain else if (stat.pnl < ZERO) tc.loss else tc.neutralPnl
                cell.inMonth -> inMonthColor
                else -> outColor
            }
            val lay = measurer.measure(
                cell.date.day.toString(),
                TextStyle(fontSize = fontSize, color = color, fontWeight = if (stat != null) FontWeight.Bold else FontWeight.Normal),
            )
            drawText(lay, topLeft = Offset(x + (cw - lay.size.width) / 2f, y + (ch - lay.size.height) / 2f))
        }
    }
}

/** ‹ › nav button: the glyph is decoration; [label] is what a screen reader announces.
 *  M3 gives a TextButton a 40dp minimum — 8dp short of a thumb, so phones get the 48dp floor. */
@Composable
private fun NavArrow(glyph: String, label: String, compact: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = label }
            .then(if (compact) Modifier.defaultMinSize(minHeight = 48.dp) else Modifier),
    ) { Text(glyph) }
}

// ---------------------------------------------------------------------------------------------
// Day-detail sheet (unchanged behaviour)
// ---------------------------------------------------------------------------------------------

@Composable
private fun DaySheet(date: BkkDate, dayTrades: List<TradeEntity>) {
    val total = dayTrades.fold(ZERO) { acc, t -> acc.add(t.realizedPnl) }
    Column(
        // Scrollable: a heavy day can hold more rows than the sheet's max height.
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = Space.lg, end = Space.lg, bottom = Space.xl),
    ) {
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
                modifier = Modifier.fillMaxWidth().padding(vertical = Space.xs),
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
