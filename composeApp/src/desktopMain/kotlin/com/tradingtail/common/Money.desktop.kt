package com.tradingtail.common

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val US = DecimalFormatSymbols(Locale.US)
private val FULL = DecimalFormat("#,##0.00", US)

actual fun formatMoney(v: BigDecimal): String {
    val scaled = v.setScale(2, RoundingMode.HALF_UP)
    val abs = FULL.format(scaled.abs())
    return when (scaled.signum()) {
        1 -> "+$CURRENCY$abs"
        -1 -> "−$CURRENCY$abs" // U+2212 minus, not hyphen
        else -> "$CURRENCY$abs"
    }
}
