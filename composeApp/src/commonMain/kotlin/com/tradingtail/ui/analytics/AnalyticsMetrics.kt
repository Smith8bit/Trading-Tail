package com.tradingtail.ui.analytics

import com.tradingtail.common.BigDecimal
import com.tradingtail.common.BkkDate
import com.tradingtail.common.YearMonth
import com.tradingtail.common.CURRENCY
import com.tradingtail.common.ZERO
import com.tradingtail.common.averageMoney
import com.tradingtail.common.bkkDate
import com.tradingtail.common.daysInMonth
import com.tradingtail.common.firstWeekday
import com.tradingtail.data.local.entity.ExecutionEntity
import com.tradingtail.data.local.entity.TradeEntity
import com.tradingtail.domain.usecase.HourPnl
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

// ---------------------------------------------------------------------------------------------
// Pure view-state derivations (testable; see AnalyticsViewStateTest). No Compose here.
// Helpers marked `internal` are also used by the widget/screen files in this package.
// ---------------------------------------------------------------------------------------------

data class WeekDay(val day: Int, val dow: String, val pnl: BigDecimal, val count: Int, val isToday: Boolean)
data class BestWorst(val largestGain: BigDecimal, val largestLoss: BigDecimal)

/** One bar row in a "Performance by …" section: a label, its trade count, and summed P&L. */
data class BucketPnl(val label: String, val trades: Int, val pnl: BigDecimal)
data class Streaks(val maxWins: Int, val maxLosses: Int)
data class DayTally(val winningDays: Int, val losingDays: Int, val breakevenDays: Int, val bestDay: BigDecimal, val worstDay: BigDecimal)

private val DOW_FULL = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
internal val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
internal const val DAY_MS = 86_400_000L

/**
 * The day a trade is attributed to — its entry (trade-open) day in Bangkok local time. "Trade day"
 * matches a broker statement's Trade Date; equals the exit day for intraday round-trips.
 */
internal fun TradeEntity.tradeDay(): BkkDate = bkkDate(entryTimestamp)

// Quick-pick presets for the date filter, computed against "today" (Bangkok).
enum class RangePreset(val label: String) {
    Today("Today"), Yesterday("Yesterday"), Last7("Last 7 days"), Last30("Last 30 days"),
    ThisMonth("This month"), LastMonth("Last month"), Last12Months("Last 12 months"),
    LastYear("Last year"), Ytd("YTD"),
}

/** Concrete [from, to] day bounds for a preset relative to [now]'s Bangkok day. */
fun presetRange(p: RangePreset, now: Long): Pair<BkkDate, BkkDate> {
    val today = bkkDate(now)
    fun daysAgo(n: Int) = bkkDate(now - n * DAY_MS)
    fun monthFull(ym: YearMonth) = BkkDate(ym.year, ym.month, 1) to BkkDate(ym.year, ym.month, daysInMonth(ym))
    return when (p) {
        RangePreset.Today -> today to today
        RangePreset.Yesterday -> daysAgo(1) to daysAgo(1)
        RangePreset.Last7 -> daysAgo(6) to today
        RangePreset.Last30 -> daysAgo(29) to today
        RangePreset.ThisMonth -> monthFull(YearMonth(today.year, today.month))
        RangePreset.LastMonth -> monthFull(YearMonth(today.year, today.month).prev())
        RangePreset.Last12Months -> {
            val ym = YearMonth(today.year - 1, today.month) // trailing 12 months, ending today
            BkkDate(ym.year, ym.month, minOf(today.day, daysInMonth(ym))) to today
        }
        RangePreset.LastYear -> BkkDate(today.year - 1, 1, 1) to BkkDate(today.year - 1, 12, 31)
        RangePreset.Ytd -> BkkDate(today.year, 1, 1) to today
    }
}

/** True if [d] falls within [from]..[to] inclusive; a null bound is open-ended on that side. */
fun dayInRange(d: BkkDate, from: BkkDate?, to: BkkDate?): Boolean {
    fun ord(x: BkkDate) = x.year * 10000 + x.month * 100 + x.day
    val o = ord(d)
    return (from == null || o >= ord(from)) && (to == null || o <= ord(to))
}

/** Weekday label for a date, reusing the calendar's month-first-weekday helper (Bangkok has no DST). */
private fun dowLabel(date: BkkDate): Int =
    ((firstWeekday(YearMonth(date.year, date.month)) - 1 + (date.day - 1)) % 7) + 1

/** The last 7 Bangkok-local days ending today, each with its realized P&L and trade count. */
fun weekStrip(trades: List<TradeEntity>, now: Long): List<WeekDay> {
    val today = bkkDate(now)
    return (6 downTo 0).map { i ->
        val date = bkkDate(now - i * DAY_MS)
        val dayTrades = trades.filter { it.tradeDay() == date }
        WeekDay(
            day = date.day,
            dow = DOW_FULL[dowLabel(date) - 1],
            pnl = dayTrades.fold(ZERO) { acc, t -> acc.add(t.realizedPnl) },
            count = dayTrades.size,
            isToday = date == today,
        )
    }
}

/** "Jun - Jul 2026" range spanning the 7-day strip (or "Jul 2026" within one month), like the mock. */
fun weekRangeLabel(now: Long): String {
    val start = bkkDate(now - 6 * DAY_MS)
    val end = bkkDate(now)
    val s = MONTHS[start.month - 1]
    val e = MONTHS[end.month - 1]
    return if (start.month == end.month) "$s ${end.year}" else "$s - $e ${end.year}"
}

/** Running total of realized P&L, trades in exit order — the cumulative curve's y-values. */
fun cumulativeSeries(trades: List<TradeEntity>): List<Float> =
    trades.sortedBy { it.exitTimestamp }
        .runningFold(ZERO) { acc, t -> acc.add(t.realizedPnl) }
        .drop(1)
        .map { it.toFloat() }

fun bestWorst(trades: List<TradeEntity>): BestWorst = BestWorst(
    largestGain = trades.map { it.realizedPnl }.filter { it > ZERO }.maxOrNull() ?: ZERO,
    largestLoss = trades.map { it.realizedPnl }.filter { it < ZERO }.minOrNull() ?: ZERO,
)

/** Sum realized P&L over a group of trades. */
internal fun sumPnl(trades: List<TradeEntity>): BigDecimal = trades.fold(ZERO) { a, t -> a.add(t.realizedPnl) }

private fun bucket(label: String, trades: List<TradeEntity>) = BucketPnl(label, trades.size, sumPnl(trades))

// Sunday-first weekday order (ISO dow 7,1..6), matching the mock and the calendar's Sun-first grid.
private val DOW_ORDER = listOf(7, 1, 2, 3, 4, 5, 6)

/** P&L grouped by Bangkok weekday of exit — all 7 days, Sun..Sat, empty days shown as $0. */
fun pnlByDayOfWeek(trades: List<TradeEntity>): List<BucketPnl> =
    DOW_ORDER.map { dow -> bucket(DOW_FULL[dow - 1], trades.filter { dowLabel(it.tradeDay()) == dow }) }

/** Intraday (entry and exit share a Bangkok day) vs multiday holds. */
fun pnlByDuration(trades: List<TradeEntity>): List<BucketPnl> {
    val (intraday, multiday) = trades.partition { bkkDate(it.entryTimestamp) == bkkDate(it.exitTimestamp) }
    return listOf(bucket("Intraday", intraday), bucket("Multiday", multiday)).filter { it.trades > 0 }
}

// Hold-time buckets (upper bound in seconds, exclusive) for intraday trades — the mock's mm:ss → h:mm:ss ladder.
private val INTRADAY_DUR_BUCKETS = listOf(
    "< 1:00" to 60L, "1:00 - 1:59" to 120L, "2:00 - 4:59" to 300L, "5:00 - 9:59" to 600L,
    "10:00 - 19:59" to 1200L, "20:00 - 39:59" to 2400L, "40:00 - 59:59" to 3600L,
    "1:00:00 - 1:59:59" to 7200L, "2:00:00 - 3:59:59" to 14400L,
)
private const val INTRADAY_DUR_TOP = "4:00:00 >"

/** P&L bucketed by hold time for intraday trades only; fixed ladder, empty buckets shown as $0. */
fun pnlByIntradayDuration(trades: List<TradeEntity>): List<BucketPnl> {
    val intraday = trades.filter { bkkDate(it.entryTimestamp) == bkkDate(it.exitTimestamp) }
    fun label(sec: Long): String = INTRADAY_DUR_BUCKETS.firstOrNull { sec < it.second }?.first ?: INTRADAY_DUR_TOP
    val groups = intraday.groupBy { label((it.exitTimestamp - it.entryTimestamp) / 1000) }
    val order = INTRADAY_DUR_BUCKETS.map { it.first } + INTRADAY_DUR_TOP
    return order.map { lab -> bucket(lab, groups[lab] ?: emptyList()) }
}

/** Realized P&L (and trade count) per symbol, for the Instrument tab's per-symbol ranking charts. */
fun pnlBySymbol(trades: List<TradeEntity>): List<BucketPnl> =
    trades.groupBy { it.symbol }.map { (s, g) -> bucket(s, g) }

/** The [n] best-performing symbols, P&L descending (mock's "Performance by Symbol – Top 20"). */
fun pnlBySymbolTop(trades: List<TradeEntity>, n: Int = 20): List<BucketPnl> =
    pnlBySymbol(trades).sortedByDescending { it.pnl }.take(n)

/** The [n] worst-performing symbols, P&L ascending (mock's "Performance by Symbol – Bottom 20"). */
fun pnlBySymbolBottom(trades: List<TradeEntity>, n: Int = 20): List<BucketPnl> =
    pnlBySymbol(trades).sortedBy { it.pnl }.take(n)

/** P&L grouped by Bangkok calendar month of exit — all 12 months, Jan..Dec, empty months as $0. */
fun pnlByMonth(trades: List<TradeEntity>): List<BucketPnl> {
    val byMonth = trades.groupBy { it.tradeDay().month }
    return (1..12).map { m -> bucket(MONTHS[m - 1], byMonth[m] ?: emptyList()) }
}

/**
 * [byHour] laid onto a continuous hour axis spanning only the hours actually traded (earliest..latest),
 * empty hours in between shown as $0. ponytail: earlier a fixed 06:00–20:00 window silently dropped
 * every trade outside it — wrong for US stocks traded from Bangkok (evening/overnight). Span the data
 * instead so no hour is lost; empty input → empty (the card shows its "no trades" state).
 */
fun hourWindow(byHour: List<HourPnl>): List<BucketPnl> {
    if (byHour.isEmpty()) return emptyList()
    val m = byHour.associateBy { it.hour }
    return (byHour.minOf { it.hour }..byHour.maxOf { it.hour }).map { h ->
        val hp = m[h]
        BucketPnl(h.toString().padStart(2, '0') + ":00", hp?.trades ?: 0, hp?.pnl ?: ZERO)
    }
}

/** Total fees across executions in scope. */
fun totalFees(executions: List<ExecutionEntity>): BigDecimal = executions.fold(ZERO) { a, e -> a.add(e.fees) }

/** Total shares/contracts traded (summed execution quantity). */
fun totalVolume(executions: List<ExecutionEntity>): BigDecimal = executions.fold(ZERO) { a, e -> a.add(e.quantity) }

/** Distinct Bangkok days that had at least one fill — the denominator for average daily volume. */
fun tradingDays(executions: List<ExecutionEntity>): Int = executions.map { bkkDate(it.timestamp) }.distinct().size

// Entry-price buckets (USD). Upper bound exclusive; anything ≥ last falls in the catch-all.
private val PRICE_BUCKETS = listOf(
    "< $2" to 2f, "$2–4.99" to 5f, "$5–9.99" to 10f, "$10–19.99" to 20f,
    "$20–49.99" to 50f, "$50–99.99" to 100f, "$100–199.99" to 200f, "$200–499.99" to 500f,
)
private const val PRICE_TOP = "$500+"

/**
 * P&L bucketed by a trade's entry price (first entry fill), via a lookup from execution id → price.
 * All price brackets are shown (empty ones as $0), matching the mock's fixed price ladder.
 */
fun pnlByPrice(trades: List<TradeEntity>, priceById: (Long) -> BigDecimal?): List<BucketPnl> {
    fun label(p: Float): String = PRICE_BUCKETS.firstOrNull { p < it.second }?.first ?: PRICE_TOP
    val groups = trades.groupBy { t ->
        t.entryExecutionIds.firstOrNull()?.let { priceById(it) }?.let { label(it.toFloat()) }
    }
    val order = PRICE_BUCKETS.map { it.first } + PRICE_TOP
    return order.map { lab -> bucket(lab, groups[lab] ?: emptyList()) }
}

// Share-size buckets from the mock. Upper bound exclusive; anything ≥ last falls in the catch-all.
private val VOLUME_BUCKETS = listOf(
    "10 - 19" to 20f, "20 - 49" to 50f, "50 - 99" to 100f, "100 - 500" to 501f,
    "500 - 999" to 1000f, "1,000 - 1,999" to 2000f, "2,000 - 2,999" to 3000f, "3,000 - 4,999" to 5000f,
)
private const val VOLUME_TOP = "5,000+"

/** Shares in a trade = summed quantity of its entry fills. */
private fun tradeShares(t: TradeEntity, qtyById: (Long) -> BigDecimal?): Float =
    t.entryExecutionIds.mapNotNull { qtyById(it) }.fold(ZERO) { a, q -> a.add(q) }.toFloat()

/** P&L bucketed by share size (summed entry-fill quantity); fixed ladder, empties shown as $0. */
fun pnlByVolumeTraded(trades: List<TradeEntity>, qtyById: (Long) -> BigDecimal?): List<BucketPnl> {
    fun label(s: Float): String = VOLUME_BUCKETS.firstOrNull { s < it.second }?.first ?: VOLUME_TOP
    val groups = trades.groupBy { label(tradeShares(it, qtyById)) }
    val order = VOLUME_BUCKETS.map { it.first } + VOLUME_TOP
    return order.map { lab -> bucket(lab, groups[lab] ?: emptyList()) }
}

/** Average of per-Bangkok-day summed P&L (money average; empty → $0). */
fun avgDailyPnl(trades: List<TradeEntity>): BigDecimal =
    averageMoney(trades.groupBy { it.tradeDay() }.values.map { sumPnl(it) })

/** Average hold time (ms) of break-even (scratch) trades, or null when there are none. */
fun avgHoldScratch(trades: List<TradeEntity>): Long? {
    val sel = trades.filter { it.realizedPnl == ZERO }
    return if (sel.isEmpty()) null else sel.map { it.exitTimestamp - it.entryTimestamp }.average().toLong()
}

/**
 * Population standard deviation of trade P&L (Double), or null with < 2 trades.
 * ponytail: a dimensionless spread statistic, so Double is fine — same last-step float call the app
 * already makes for profit factor / win rate; the money itself stays BigDecimal up to here.
 */
fun pnlStdDev(trades: List<TradeEntity>): Double? {
    if (trades.size < 2) return null
    val xs = trades.map { it.realizedPnl.toFloat().toDouble() }
    val mean = xs.average()
    return kotlin.math.sqrt(xs.sumOf { (it - mean) * (it - mean) } / xs.size)
}

/** System Quality Number: √N · mean / stdev. Null when stdev is 0 or < 2 trades. */
fun sqn(trades: List<TradeEntity>): Double? {
    val sd = pnlStdDev(trades) ?: return null
    if (sd == 0.0) return null
    val mean = trades.map { it.realizedPnl.toFloat().toDouble() }.average()
    return kotlin.math.sqrt(trades.size.toDouble()) * mean / sd
}

/** Kelly fraction: W − (1−W)/R, R = avgWin/|avgLoss|. Null when there are no losers (R undefined). */
fun kelly(trades: List<TradeEntity>): Double? {
    val avg = averages(trades)
    val avgLossAbs = -avg.perLoser.toFloat().toDouble()
    val decided = trades.count { it.realizedPnl > ZERO } + trades.count { it.realizedPnl < ZERO }
    if (avgLossAbs <= 0.0 || decided == 0) return null
    val w = trades.count { it.realizedPnl > ZERO }.toDouble() / decided
    val avgWin = avg.perWinner.toFloat().toDouble()
    return if (avgWin <= 0.0) -1.0 else w - (1 - w) / (avgWin / avgLossAbs) // no winners → deeply negative edge
}

data class DrawdownStats(
    val avgDrawdown: BigDecimal, val biggest: BigDecimal,
    val avgDays: Double, val totalDays: Int, val avgTrades: Double,
)

/**
 * Drawdown episodes over the equity curve (trades in exit order): an episode runs from the first
 * trade that drops cumulative P&L below its running peak until cum recovers to a new peak. Reports
 * the mean & worst trough depth (positive magnitudes) plus mean/total days and mean trades per episode.
 */
fun drawdownStats(trades: List<TradeEntity>): DrawdownStats {
    var cum = ZERO; var peak = ZERO; var trough = ZERO; var count = 0; var active = false
    var days = mutableSetOf<BkkDate>()
    val depths = mutableListOf<BigDecimal>(); val dayCounts = mutableListOf<Int>(); val tradeCounts = mutableListOf<Int>()
    fun close() { depths.add(peak.subtract(trough)); dayCounts.add(days.size); tradeCounts.add(count); active = false }
    for (t in trades.sortedBy { it.exitTimestamp }) {
        cum = cum.add(t.realizedPnl)
        if (cum >= peak) {
            if (active) close()
            peak = cum
        } else {
            if (!active) { active = true; trough = cum; days = mutableSetOf(); count = 0 }
            if (cum < trough) trough = cum
            days.add(bkkDate(t.exitTimestamp)); count++
        }
    }
    if (active) close() // episode still open at the end still counts
    return DrawdownStats(
        avgDrawdown = averageMoney(depths),
        biggest = depths.maxOrNull() ?: ZERO,
        avgDays = if (dayCounts.isEmpty()) 0.0 else dayCounts.average(),
        totalDays = dayCounts.sum(),
        avgTrades = if (tradeCounts.isEmpty()) 0.0 else tradeCounts.average(),
    )
}

/** Count, per Bangkok weekday (Sun..Sat), of trades that deepened the running drawdown. */
fun drawdownIncreaseByDayOfWeek(trades: List<TradeEntity>): List<BucketPnl> {
    var cum = ZERO; var peak = ZERO; var prevDd = ZERO
    val counts = IntArray(8) // index by ISO dow 1..7
    for (t in trades.sortedBy { it.exitTimestamp }) {
        cum = cum.add(t.realizedPnl)
        if (cum > peak) peak = cum
        val dd = peak.subtract(cum)
        if (dd > prevDd) counts[dowLabel(bkkDate(t.exitTimestamp))]++
        prevDd = dd
    }
    return DOW_ORDER.map { dow -> BucketPnl(DOW_FULL[dow - 1], counts[dow], ZERO) }
}

// Rolling window (in daily points) for the P&L moving-average / volatility overlays.
internal const val MA_WINDOW = 5

/** Trailing simple moving average; null until [window] points are available. */
fun movingAverage(xs: List<Float>, window: Int): List<Float?> =
    xs.indices.map { i -> if (i + 1 < window) null else xs.subList(i + 1 - window, i + 1).average().toFloat() }

/** Trailing population standard deviation over [window] points; null until the window fills. */
fun rollingStd(xs: List<Float>, window: Int): List<Float?> =
    xs.indices.map { i ->
        if (i + 1 < window) null else {
            val w = xs.subList(i + 1 - window, i + 1).map { it.toDouble() }
            val m = w.average()
            kotlin.math.sqrt(w.sumOf { (it - m) * (it - m) } / w.size).toFloat()
        }
    }

/** A single vertical bar in a per-day chart: its axis label and height value. */
data class DayPoint(val label: String, val value: Float)

internal fun two(n: Int) = n.toString().padStart(2, '0')
private fun md(d: BkkDate) = "${two(d.month)}-${two(d.day)}"

private fun <T> byDaySorted(items: List<T>, day: (T) -> BkkDate): List<Pair<BkkDate, List<T>>> =
    items.groupBy(day).entries.sortedWith(compareBy({ it.key.year }, { it.key.month }, { it.key.day })).map { it.toPair() }

/** Per-day win rate (0..100) over the period — the mock's Win % bar chart. */
fun winRateByDay(trades: List<TradeEntity>): List<DayPoint> =
    byDaySorted(trades) { it.tradeDay() }.map { (d, g) ->
        val decided = g.count { it.realizedPnl != ZERO }
        DayPoint(md(d), if (decided == 0) 0f else 100f * g.count { it.realizedPnl > ZERO } / decided)
    }

/** Per-day traded volume (shares) — the mock's Daily Volume bar chart. */
fun volumeByDay(executions: List<ExecutionEntity>): List<DayPoint> =
    byDaySorted(executions) { bkkDate(it.timestamp) }.map { (d, g) -> DayPoint(md(d), g.fold(ZERO) { a, e -> a.add(e.quantity) }.toFloat()) }

/** Per-day mean trade P&L — the mock's Average Trade P&L diverging bar chart. */
fun avgPnlByDay(trades: List<TradeEntity>): List<DayPoint> =
    byDaySorted(trades) { it.tradeDay() }.map { (d, g) -> DayPoint(md(d), averageMoney(g.map { it.realizedPnl }).toFloat()) }

/** Per-day summed realized P&L — the mock's Gross Daily P&L diverging bar chart. */
fun dailyPnlPoints(trades: List<TradeEntity>): List<DayPoint> =
    byDaySorted(trades) { it.tradeDay() }.map { (d, g) -> DayPoint(md(d), sumPnl(g).toFloat()) }

/** Trade count per calendar year of exit, ascending (mock's Trade Distribution by Year). */
fun tradesByYear(trades: List<TradeEntity>): List<DayPoint> =
    trades.groupBy { it.tradeDay().year }.entries.sortedBy { it.key }
        .map { (y, g) -> DayPoint(y.toString(), g.size.toFloat()) }

/** Summed realized P&L per calendar year of exit, ascending (mock's Performance by Year). */
fun pnlByYear(trades: List<TradeEntity>): List<DayPoint> =
    trades.groupBy { it.tradeDay().year }.entries.sortedBy { it.key }
        .map { (y, g) -> DayPoint(y.toString(), sumPnl(g).toFloat()) }

/** Trade count for each month Jan..Dec of [year]; empty months show as 0 (full ladder, like the mock). */
fun tradesByMonthOfYear(trades: List<TradeEntity>, year: Int): List<DayPoint> {
    val inYear = trades.filter { it.tradeDay().year == year }
    return (1..12).map { m -> DayPoint(MONTHS[m - 1], inYear.count { it.tradeDay().month == m }.toFloat()) }
}

/** Summed P&L for each month Jan..Dec of [year]. */
fun pnlByMonthOfYear(trades: List<TradeEntity>, year: Int): List<DayPoint> {
    val inYear = trades.filter { it.tradeDay().year == year }
    return (1..12).map { m -> DayPoint(MONTHS[m - 1], sumPnl(inYear.filter { it.tradeDay().month == m }).toFloat()) }
}

/** Trade count per day 1..daysInMonth of [year]/[month]; empty days show as 0. */
fun tradesByDayOfMonth(trades: List<TradeEntity>, year: Int, month: Int): List<DayPoint> {
    val inMonth = trades.filter { val d = it.tradeDay(); d.year == year && d.month == month }
    return (1..daysInMonth(YearMonth(year, month))).map { day -> DayPoint(day.toString(), inMonth.count { it.tradeDay().day == day }.toFloat()) }
}

/** Summed P&L per day 1..daysInMonth of [year]/[month]. */
fun pnlByDayOfMonth(trades: List<TradeEntity>, year: Int, month: Int): List<DayPoint> {
    val inMonth = trades.filter { val d = it.tradeDay(); d.year == year && d.month == month }
    return (1..daysInMonth(YearMonth(year, month))).map { day -> DayPoint(day.toString(), sumPnl(inMonth.filter { it.tradeDay().day == day }).toFloat()) }
}

/** Exit-date labels parallel to [cumulativeSeries]/[drawdownSeries] (both sort by exit). */
fun exitDateLabels(trades: List<TradeEntity>): List<String> =
    trades.sortedBy { it.exitTimestamp }.map { md(bkkDate(it.exitTimestamp)) }

/** Sum of magnitudes — denominator for a bar's % contribution. */
internal fun sumAbs(pnls: List<BigDecimal>): BigDecimal = pnls.fold(ZERO) { a, p -> a.add(abs(p)) }

internal fun percentOf(pnl: BigDecimal, totalAbs: BigDecimal): Float =
    if (totalAbs <= ZERO) 0f else abs(pnl).toFloat() / totalAbs.toFloat() * 100f

/**
 * Gross profit ÷ gross loss. Null when there are no losing trades (an "infinite" factor).
 * ponytail: a dimensionless ratio, not a money value — float division is fine here (same call the
 * project already makes for win-rate), money itself stays BigDecimal right up to this last step.
 */
fun profitFactor(trades: List<TradeEntity>): Double? {
    val gross = sumPnl(trades.filter { it.realizedPnl > ZERO })
    val lossAbs = abs(sumPnl(trades.filter { it.realizedPnl < ZERO }))
    return if (lossAbs <= ZERO) null else gross.toFloat().toDouble() / lossAbs.toFloat()
}

data class Averages(val perTrade: BigDecimal, val perWinner: BigDecimal, val perLoser: BigDecimal)

/** Mean P&L across all trades, and separately across winners / losers (money division; see averageMoney). */
fun averages(trades: List<TradeEntity>): Averages = Averages(
    perTrade = averageMoney(trades.map { it.realizedPnl }),
    perWinner = averageMoney(trades.map { it.realizedPnl }.filter { it > ZERO }),
    perLoser = averageMoney(trades.map { it.realizedPnl }.filter { it < ZERO }),
)

/** Average hold time (exit − entry, millis) for winners or losers; null when that bucket is empty. */
fun avgHoldMillis(trades: List<TradeEntity>, winners: Boolean): Long? {
    val sel = trades.filter { if (winners) it.realizedPnl > ZERO else it.realizedPnl < ZERO }
    return if (sel.isEmpty()) null else sel.map { it.exitTimestamp - it.entryTimestamp }.average().toLong()
}

/** Compact human hold-time: "45m" / "2h 15m" / "3d 4h". */
fun formatDuration(ms: Long): String {
    val m = ms / 60_000
    return when {
        m < 60 -> "${m}m"
        m < 1_440 -> "${m / 60}h ${m % 60}m"
        else -> "${m / 1_440}d ${(m % 1_440) / 60}h"
    }
}

/** Largest peak-to-trough decline of cumulative realized P&L, returned as a positive magnitude. */
fun maxDrawdown(trades: List<TradeEntity>): BigDecimal {
    var cum = ZERO; var peak = ZERO; var maxDd = ZERO
    for (t in trades.sortedBy { it.exitTimestamp }) {
        cum = cum.add(t.realizedPnl)
        if (cum > peak) peak = cum
        val dd = peak.subtract(cum)
        if (dd > maxDd) maxDd = dd
    }
    return maxDd
}

/** Underwater curve: how far cumulative P&L sits below its running peak at each close (values ≤ 0). */
fun drawdownSeries(trades: List<TradeEntity>): List<Float> {
    var cum = ZERO; var peak = ZERO
    return trades.sortedBy { it.exitTimestamp }.map { t ->
        cum = cum.add(t.realizedPnl)
        if (cum > peak) peak = cum
        cum.subtract(peak).toFloat()
    }
}

/** Win/loss/break-even tally at the day level (a day is won if its net realized P&L is positive). */
fun dayTally(trades: List<TradeEntity>): DayTally {
    val byDay = trades.groupBy { it.tradeDay() }
        .mapValues { (_, g) -> sumPnl(g) }.values
    var w = 0; var l = 0; var b = 0
    for (p in byDay) when { p > ZERO -> w++; p < ZERO -> l++; else -> b++ }
    return DayTally(w, l, b, byDay.filter { it > ZERO }.maxOrNull() ?: ZERO, byDay.filter { it < ZERO }.minOrNull() ?: ZERO)
}

/** Longest run of consecutive winners and losers in exit order; a break-even trade resets both. */
fun maxStreaks(trades: List<TradeEntity>): Streaks {
    var maxW = 0; var maxL = 0; var w = 0; var l = 0
    for (t in trades.sortedBy { it.exitTimestamp }) when {
        t.realizedPnl > ZERO -> { w++; l = 0; if (w > maxW) maxW = w }
        t.realizedPnl < ZERO -> { l++; w = 0; if (l > maxL) maxL = l }
        else -> { w = 0; l = 0 }
    }
    return Streaks(maxW, maxL)
}

private fun abs(x: BigDecimal): BigDecimal = if (x < ZERO) ZERO.subtract(x) else x

internal fun maxAbs(pnls: List<BigDecimal>): BigDecimal =
    pnls.fold(ZERO) { m, p -> abs(p).let { if (it > m) it else m } }

internal fun fraction(pnl: BigDecimal, max: BigDecimal): Float =
    if (max <= ZERO) 0f else (abs(pnl).toFloat() / max.toFloat()).coerceIn(0f, 1f)

// ---------------------------------------------------------------------------------------------
// Chart axis helpers — "nice" round tick values and compact tick labels for the horizontal bar charts.
// ---------------------------------------------------------------------------------------------

/**
 * Clean axis ticks spanning [min]..[max] (a "nice" round step, ~[maxTicks] ticks). When min < 0 < max
 * the ticks straddle 0 (0 is always one of them), so bars diverge from a real zero line like the mock.
 */
internal fun niceTicks(min: Float, max: Float, maxTicks: Int = 5): List<Float> {
    if (min >= max) return listOf(0f, maxOf(max, 1f)) // degenerate (all-zero / single value) → 0..max
    val range = niceNum((max - min).toDouble(), round = false)
    val step = niceNum(range / (maxTicks - 1), round = true)
    val niceMin = floor(min / step) * step
    val niceMax = ceil(max / step) * step
    val ticks = ArrayList<Float>()
    var v = niceMin
    while (v <= niceMax + step * 0.5) { ticks.add(v.toFloat()); v += step }
    return ticks
}

/** The 1/2/5·10ⁿ "nice" number bracketing [range] (rounded to nearest when [round], else ceil). */
private fun niceNum(range: Double, round: Boolean): Double {
    if (range <= 0.0) return 1.0
    val exp = floor(log10(range))
    val base = 10.0.pow(exp)
    val f = range / base
    val nf = if (round) when { f < 1.5 -> 1.0; f < 3.0 -> 2.0; f < 7.0 -> 5.0; else -> 10.0 }
             else when { f <= 1.0 -> 1.0; f <= 2.0 -> 2.0; f <= 5.0 -> 5.0; else -> 10.0 }
    return nf * base
}

/** Exactly two decimal places for a non-negative value: 1234.5 → "1234.50". */
internal fun twoDpAbs(x: Double): String {
    val c = (x * 100).roundToLong()
    return "${c / 100}.${(c % 100).toString().padStart(2, '0')}"
}

/** [twoDpAbs] with trailing zeros trimmed: 300.00 → "300", 1.50 → "1.5" — axes read as integers
 *  wherever the tick is whole, decimals only when it genuinely isn't. */
private fun trimDpAbs(x: Double): String = twoDpAbs(x).trimEnd('0').trimEnd('.')

/** Chart tooltip value: money keeps exact two decimals ("−$45.30"); counts are plain ("72"). */
internal fun tickLabel(v: Float, money: Boolean): String {
    val sign = if (v < 0f) "−" else "" // U+2212 minus, matching formatMoney
    val a = kotlin.math.abs(v).toDouble()
    return if (money) "$sign$CURRENCY${twoDpAbs(a)}" else "$sign${trimDpAbs(a)}"
}

/** Axis tick text, no currency symbol: whole ticks print as integers ("300", "−5", "2k"),
 *  abbreviated (k/M) for large money axes. Pair with [niceTicks] so ticks land on round values. */
internal fun axisLabel(v: Float, money: Boolean): String {
    val a = kotlin.math.abs(v).toDouble()
    val s = if (v < 0f) "−" else ""
    return when {
        !money -> "$s${trimDpAbs(a)}"
        a >= 1_000_000 -> "$s${trimDpAbs(a / 1_000_000)}M"
        a >= 1_000 -> "$s${trimDpAbs(a / 1_000)}k"
        else -> "$s${trimDpAbs(a)}"
    }
}
