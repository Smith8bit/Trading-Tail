package com.tradingtail.data.imports

import com.tradingtail.common.BigDecimal
import com.tradingtail.common.bigDecimal
import com.tradingtail.data.local.entity.Side

/**
 * One fill lifted from a Webull (Thailand) monthly statement's TRADE RECORDS table.
 * Currency-agnostic on purpose: prices/fees are exactly as printed (USD for US stocks) — how to
 * store them (native vs THB-converted) is decided downstream at import time, not here.
 */
data class ParsedFill(
    val symbol: String,
    val side: Side,
    val quantity: BigDecimal,
    val price: BigDecimal,
    /** Trade timestamp as "yyyy-MM-dd HH:mm:ss" in Bangkok local time (the statement prints GMT+07). */
    val bangkokDateTime: String,
    /** Comm/Fee/Tax + VAT, as a positive magnitude (statement prints them negative). */
    val fees: BigDecimal,
)

/**
 * Pure parser for the TRADE RECORDS table. Kept free of any PDF dependency so it's unit-tested against
 * captured text; the platform PDF extractor just feeds it a String. Validated against real PDFBox output.
 *
 * PDFBox emits each fill as three lines — ticker, company name, then the data row (which begins with the
 * trade date). ponytail: match the data row by a regex anchored on that date, and carry the ticker from
 * the most recent single-token all-caps line. Company names are multi-word so they never look like a
 * ticker. Ceiling: a single-word all-caps ≤6-char company name would shadow its ticker — unseen in
 * practice (real names carry INC/CORP/LTD); revisit if a broker prints bare one-word names.
 */
object WebullStatementParser {
    // …name… 15/05/2026 20:17:19,GMT+07 18/05/2026 SELL 200 4.02 804.00 803.08 -0.86 -0.06 NASDAQ
    private val ROW = Regex(
        """(\d{2})/(\d{2})/(\d{4})\s+(\d{2}:\d{2}:\d{2}),GMT[+\-]\d{2}\s+""" + // trade date + time
            """\d{2}/\d{2}/\d{4}\s+""" +                                        // settlement date (unused)
            """(BUY|SELL)\s+([\d,]+)\s+([\d,.]+)\s+""" +                        // side, qty, price
            """[\d,.]+\s+[\d,.]+\s+""" +                                        // gross, net (unused)
            """(-?[\d,.]+)\s+(-?[\d,.]+)""",                                    // comm/fee/tax, vat
    )

    // A "Symbol" cell rendered on its own line: a single all-caps ticker token (LESL, QQQ, OCG…).
    private val TICKER = Regex("""^[A-Z][A-Z0-9.]{0,6}$""")

    fun parse(text: String): List<ParsedFill> {
        val fills = mutableListOf<ParsedFill>()
        var lastTicker: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val m = ROW.find(line)
            if (m != null) {
                val (dd, mm, yyyy, time, side, qty, price, comm, vat) = m.destructured
                val ticker = lastTicker ?: continue // no symbol seen yet → skip stray row
                fills += ParsedFill(
                    symbol = ticker,
                    side = if (side == "BUY") Side.BUY else Side.SELL,
                    quantity = num(qty),
                    price = num(price),
                    bangkokDateTime = "$yyyy-$mm-$dd $time",
                    fees = magnitude(comm).add(magnitude(vat)),
                )
            } else if (TICKER.matches(line)) {
                lastTicker = line
            }
        }
        return fills
    }

    private fun num(s: String): BigDecimal = bigDecimal(s.replace(",", ""))
    private fun magnitude(s: String): BigDecimal = num(s.removePrefix("-"))
}
