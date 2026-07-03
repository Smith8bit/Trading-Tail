package com.tradingtail.common

actual typealias BigDecimal = java.math.BigDecimal

actual fun bigDecimal(value: String): BigDecimal = java.math.BigDecimal(value)
actual fun bigDecimal(value: Int): BigDecimal = java.math.BigDecimal(value)
actual val ZERO: BigDecimal = java.math.BigDecimal.ZERO
