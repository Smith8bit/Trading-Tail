package com.tradingtail.common

/**
 * Shared money rendering — one formatter for every P&L call site so ฿ display is consistent.
 * ponytail: expect/actual over java.text (both targets are JVM), same pattern as formatBangkok.
 * Color is applied by the caller via ui.theme.pnlColor — never baked into the string.
 */

/** Full form: "+฿1,234.50" / "−฿480.50" / "฿0.00". Sign-aware, 2dp, thousands-separated. */
expect fun formatMoney(v: BigDecimal): String

/** Abbreviated form for tight cells (calendar): "+฿2.3k" / "−฿1.4M" / "−฿90". */
expect fun formatMoneyShort(v: BigDecimal): String
