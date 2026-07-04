package com.tradingtail.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val BANGKOK: ZoneId = ZoneId.of("Asia/Bangkok")

actual fun bkkDate(epochMillis: Long): BkkDate {
    val d = Instant.ofEpochMilli(epochMillis).atZone(BANGKOK).toLocalDate()
    return BkkDate(d.year, d.monthValue, d.dayOfMonth)
}

actual fun bkkHour(epochMillis: Long): Int =
    Instant.ofEpochMilli(epochMillis).atZone(BANGKOK).hour

actual fun currentYearMonth(): YearMonth {
    val d = LocalDate.now(BANGKOK)
    return YearMonth(d.year, d.monthValue)
}

actual fun daysInMonth(ym: YearMonth): Int =
    java.time.YearMonth.of(ym.year, ym.month).lengthOfMonth()

actual fun firstWeekday(ym: YearMonth): Int =
    LocalDate.of(ym.year, ym.month, 1).dayOfWeek.value

actual fun monthLabel(ym: YearMonth): String {
    val name = java.time.Month.of(ym.month).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    return "$name ${ym.year}"
}
