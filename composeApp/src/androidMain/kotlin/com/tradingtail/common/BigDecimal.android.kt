package com.tradingtail.common

actual typealias BigDecimal = java.math.BigDecimal

actual fun bigDecimal(value: String): BigDecimal = java.math.BigDecimal(value)
actual fun bigDecimal(value: Int): BigDecimal = java.math.BigDecimal(value)
actual val ZERO: BigDecimal = java.math.BigDecimal.ZERO

actual fun averageMoney(values: List<BigDecimal>): BigDecimal =
    if (values.isEmpty()) java.math.BigDecimal.ZERO
    else values.reduce(BigDecimal::add).divide(java.math.BigDecimal(values.size), 2, java.math.RoundingMode.HALF_UP)

actual fun nowMillis(): Long = System.currentTimeMillis()
