package com.tradingtail.common

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val US = DecimalFormatSymbols(Locale.US)
private val FULL = DecimalFormat("#,##0.00", US)
private val SHORT = DecimalFormat("0.0", US)
private val THOUSAND = BigDecimal(1000)
private val MILLION = BigDecimal(1_000_000)

actual fun formatMoney(v: BigDecimal): String {
    val scaled = v.setScale(2, RoundingMode.HALF_UP)
    val abs = FULL.format(scaled.abs())
    return when (scaled.signum()) {
        1 -> "+$CURRENCY$abs"
        -1 -> "−$CURRENCY$abs" // U+2212 minus, not hyphen
        else -> "$CURRENCY$abs"
    }
}

actual fun formatMoneyShort(v: BigDecimal): String {
    val sign = when (v.signum()) { 1 -> "+"; -1 -> "−"; else -> "" }
    val abs = v.abs()
    // ponytail: abbreviation uses Double — display-only glance value for a tight cell, not accounting.
    val mag = when {
        abs >= MILLION -> SHORT.format(abs.toDouble() / 1_000_000) + "M"
        abs >= THOUSAND -> SHORT.format(abs.toDouble() / 1_000) + "k"
        else -> abs.setScale(0, RoundingMode.HALF_UP).toPlainString()
    }
    return "$sign$CURRENCY$mag"
}
