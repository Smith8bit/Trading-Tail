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
}

expect fun bigDecimal(value: String): BigDecimal
expect fun bigDecimal(value: Int): BigDecimal
expect val ZERO: BigDecimal
