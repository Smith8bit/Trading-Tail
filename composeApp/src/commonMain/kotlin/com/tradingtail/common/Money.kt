package com.tradingtail.common

/**
 * Shared money rendering — one formatter for every P&L call site so currency display is consistent.
 * ponytail: expect/actual over java.text (both targets are JVM), same pattern as formatBangkok.
 * Color is applied by the caller via ui.theme.pnlColor — never baked into the string.
 */

/** The single currency symbol for the whole app. USD ($): Webull trades are USD-denominated. */
const val CURRENCY = "$"

/** Full form: "+$1,234.50" / "−$480.50" / "$0.00". Sign-aware, 2dp, thousands-separated. */
expect fun formatMoney(v: BigDecimal): String

/**
 * Abbreviated form for tight cells: "+$2.3k" / "−$1.4M" / "−$90". Whole dollars below 1k, one
 * decimal above. The compact calendar day cell is ~43dp wide on a 360dp phone, which is about five
 * monospace characters at 12sp — the full "+$1,234.50" needs three times that and ellipsized away.
 *
 * ponytail: commonMain, no expect/actual — unlike [formatMoney] there's no thousands separator to
 * need java.text. toFloat is display-only (see BigDecimal.kt); the sign comes off the ordering, and
 * the magnitude is being deliberately rounded away here, so no money arithmetic rides on it.
 */
fun formatMoneyShort(v: BigDecimal): String {
    // Round to whole dollars up front so 999.60 abbreviates to "1k" rather than a 6-char "1000".
    val a = (kotlin.math.abs(v.toFloat()) + 0.5f).toInt()
    val body = when {
        a >= 1_000_000 -> "${oneDp(a / 1_000_000f)}M"
        a >= 1_000 -> "${oneDp(a / 1_000f)}k"
        else -> "$a"
    }
    // Sign off the ROUNDED figure, matching formatMoney: −$0.40 abbreviates to "$0", never "−$0".
    val sign = if (a == 0) "" else if (v < ZERO) "−" else "+" // U+2212 minus, not a hyphen
    return "$sign$CURRENCY$body"
}

/** One decimal place, a trailing ".0" trimmed: 2.34 → "2.3", 9.96 → "10", 1.0 → "1". */
private fun oneDp(x: Float): String {
    val t = (x * 10f + 0.5f).toInt()
    return if (t % 10 == 0) "${t / 10}" else "${t / 10}.${t % 10}"
}
