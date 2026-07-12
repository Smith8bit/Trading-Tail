package com.tradingtail.common

/**
 * Timestamps are stored as epoch millis (UTC); every display/entry uses Bangkok local time
 * (Asia/Bangkok, UTC+7) per project convention. Format is "yyyy-MM-dd HH:mm".
 * ponytail: expect/actual over java.time (both targets are JVM, Android minSdk 26 has it natively).
 */
expect fun formatBangkok(epochMillis: Long): String

/** Parse a "yyyy-MM-dd HH:mm" Bangkok-local string back to epoch millis. Throws on bad input. */
expect fun parseBangkok(text: String): Long

/**
 * Parse a "yyyy-MM-dd HH:mm:ss" Bangkok-local string to epoch millis. Seconds precision matters for
 * PDF imports: two fills can share a minute (see WebullStatementParser), and FIFO orders by timestamp.
 */
expect fun parseBangkokSeconds(text: String): Long
