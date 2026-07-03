package com.tradingtail.common

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BANGKOK: ZoneId = ZoneId.of("Asia/Bangkok")
private val FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

actual fun formatBangkok(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(BANGKOK).format(FMT)

actual fun parseBangkok(text: String): Long =
    LocalDateTime.parse(text.trim(), FMT).atZone(BANGKOK).toInstant().toEpochMilli()
