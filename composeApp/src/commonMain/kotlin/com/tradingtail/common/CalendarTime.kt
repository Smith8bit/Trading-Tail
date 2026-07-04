package com.tradingtail.common

/** A Bangkok-local calendar day (month is 1-12). Used to bucket trades for the P&L calendar. */
data class BkkDate(val year: Int, val month: Int, val day: Int)

/** Bangkok-local (year, month) — for the calendar's current-month default. */
data class YearMonth(val year: Int, val month: Int) {
    fun prev(): YearMonth = if (month == 1) YearMonth(year - 1, 12) else YearMonth(year, month - 1)
    fun next(): YearMonth = if (month == 12) YearMonth(year + 1, 1) else YearMonth(year, month + 1)
}

/** The Bangkok-local calendar day an epoch-millis instant falls on. */
expect fun bkkDate(epochMillis: Long): BkkDate

/** The Bangkok-local hour (0-23) an epoch-millis instant falls on. */
expect fun bkkHour(epochMillis: Long): Int

/** Current Bangkok-local month, for the calendar's initial view. */
expect fun currentYearMonth(): YearMonth

expect fun daysInMonth(ym: YearMonth): Int

/** ISO weekday of the 1st of the month: 1 = Monday .. 7 = Sunday. */
expect fun firstWeekday(ym: YearMonth): Int

/** Display name like "July 2026". */
expect fun monthLabel(ym: YearMonth): String
