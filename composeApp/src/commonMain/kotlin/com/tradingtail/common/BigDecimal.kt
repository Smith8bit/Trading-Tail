package com.tradingtail.common

/**
 * ponytail: `java.math.BigDecimal` isn't visible in commonMain even though both targets are
 * JVM, so we `expect` a minimal surface here and `actual typealias` it to the real thing in
 * each JVM source set. Only the members actually used downstream (money arithmetic + ordering)
 * are declared — extend this if a caller needs more, don't pull in a third-party bignum.
 */
expect class BigDecimal : Comparable<BigDecimal> {
    fun add(other: BigDecimal): BigDecimal
    fun subtract(other: BigDecimal): BigDecimal
    fun multiply(other: BigDecimal): BigDecimal
    fun toFloat(): Float // display-only (bar-length ratios) — never for money arithmetic
}

expect fun bigDecimal(value: String): BigDecimal
expect fun bigDecimal(value: Int): BigDecimal
expect val ZERO: BigDecimal

/**
 * Mean of money values, HALF_UP to 2 decimals; empty list → ZERO. The single sanctioned money
 * division in the app (dashboard averages) — kept behind one helper so the rounding rule lives in
 * exactly one place instead of leaking RoundingMode into every call site.
 */
expect fun averageMoney(values: List<BigDecimal>): BigDecimal

/** ponytail: epoch-millis clock — commonMain can't see System.currentTimeMillis(). Swap for
 * kotlinx-datetime Clock only if calendar math is needed. Used to prefill entry-form timestamps. */
expect fun nowMillis(): Long
