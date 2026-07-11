package com.tradingtail.data.imports

import com.tradingtail.common.bigDecimal
import com.tradingtail.data.local.entity.Side
import kotlin.test.Test
import kotlin.test.assertEquals

class WebullStatementParserTest {
    // Verbatim page-3 TRADE RECORDS text (ticker line + name/data line per fill), as extracted.
    private val page3 = """
Symbol & Name Trade Date Time Settlement Date Buy/Sell Quantity Traded Price Gross Amount Net Amount Comm/Fee/Tax VAT Exchange Remarks Status
LESL
LESLIES INC 15/05/2026 20:17:19,GMT+07 18/05/2026 SELL 200 4.02 804.00 803.08 -0.86 -0.06 NASDAQ
LESL
LESLIES INC 15/05/2026 20:14:53,GMT+07 18/05/2026 BUY 200 4.00 800.00 800.86 -0.80 -0.06 NASDAQ
SPAI
SAFE PRO GROUP INC 15/05/2026 19:11:04,GMT+07 18/05/2026 SELL 300 5.04 1,512.00 1,510.28 -1.61 -0.11 NASDAQ
SPAI
SAFE PRO GROUP INC 15/05/2026 19:10:15,GMT+07 18/05/2026 BUY 300 5.28 1,584.00 1,585.69 -1.58 -0.11 NASDAQ
AIIO
ROBO.AI INC. 14/05/2026 17:50:55,GMT+07 15/05/2026 SELL 200 4.56 912.00 910.97 -0.97 -0.06 NASDAQ
AIIO
ROBO.AI INC. 14/05/2026 17:50:26,GMT+07 15/05/2026 BUY 200 4.33 866.00 866.93 -0.87 -0.06 NASDAQ
AIIO
ROBO.AI INC. 14/05/2026 17:46:59,GMT+07 15/05/2026 SELL 50 4.13 206.50 206.26 -0.23 -0.01 NASDAQ
AIIO
ROBO.AI INC. 14/05/2026 17:44:24,GMT+07 15/05/2026 BUY 50 4.09 204.50 204.71 -0.20 -0.01 NASDAQ
OCG
ORIENTAL CULTURE HOLDING LTD 13/05/2026 18:42:44,GMT+07 14/05/2026 SELL 500 2.65 1,325.00 1,323.45 -1.46 -0.09 NASDAQ
OCG
ORIENTAL CULTURE HOLDING LTD 13/05/2026 18:41:05,GMT+07 14/05/2026 BUY 500 2.63 1,315.00 1,316.41 -1.32 -0.09 NASDAQ
QQQ
Invesco QQQ Trust 12/05/2026 19:10:29,GMT+07 13/05/2026 SELL 2 706.47 1,412.94 1,411.39 -1.45 -0.10 NASDAQ
CNCK
COINCHECK GROUP NV 12/05/2026 18:51:23,GMT+07 13/05/2026 SELL 100 2.62 262.00 261.69 -0.29 -0.02 NASDAQ
CNCK
COINCHECK GROUP NV 12/05/2026 18:51:06,GMT+07 13/05/2026 BUY 100 2.71 271.00 271.29 -0.27 -0.02 NASDAQ
BCDA
BIOCARDIA INC 08/05/2026 19:17:15,GMT+07 11/05/2026 SELL 200 1.32 264.00 263.67 -0.31 -0.02 NASDAQ
BCDA
BIOCARDIA INC 08/05/2026 19:15:50,GMT+07 11/05/2026 BUY 200 1.29 258.00 258.28 -0.26 -0.02 NASDAQ
VEEE
TWIN VEE POWERCATS CO 07/05/2026 19:07:07,GMT+07 08/05/2026 SELL 40 7.83 313.20 312.85 -0.33 -0.02 NASDAQ
VEEE
TWIN VEE POWERCATS CO 07/05/2026 19:06:38,GMT+07 08/05/2026 BUY 40 7.16 286.40 286.71 -0.29 -0.02 NASDAQ
RENX
RENX ENTERPRISES CORP 05/05/2026 20:05:45,GMT+07 06/05/2026 SELL 100 3.12 312.00 311.64 -0.34 -0.02 NASDAQ
RENX
RENX ENTERPRISES CORP 05/05/2026 20:05:00,GMT+07 06/05/2026 BUY 100 3.05 305.00 305.33 -0.31 -0.02 NASDAQ
CLNN
CLENE INC 04/05/2026 19:21:28,GMT+07 05/05/2026 SELL 30 8.99 269.70 269.39 -0.29 -0.02 NASDAQ
CLNN
CLENE INC 04/05/2026 19:20:30,GMT+07 05/05/2026 BUY 30 9.08 272.40 272.69 -0.27 -0.02 NASDAQ
""".trimIndent()

    @Test
    fun parsesEveryTradeRowWithSymbolFromThePrecedingTickerLine() {
        val fills = WebullStatementParser.parse(page3)
        assertEquals(21, fills.size)                       // 20 round-trip legs + 1 orphan QQQ sell
        assertEquals(10, fills.count { it.side == Side.BUY })
        assertEquals(11, fills.count { it.side == Side.SELL }) // extra sell = QQQ, bought a prior month
        // Ticker comes from the line above the name/data line, not the company name.
        assertEquals(setOf("LESL", "SPAI", "AIIO", "OCG", "QQQ", "CNCK", "BCDA", "VEEE", "RENX", "CLNN"),
            fills.map { it.symbol }.toSet())
    }

    @Test
    fun firstRowFieldsAndFeesAreExtractedExactly() {
        val f = WebullStatementParser.parse(page3).first()
        assertEquals("LESL", f.symbol)
        assertEquals(Side.SELL, f.side)
        assertEquals(bigDecimal("200"), f.quantity)
        assertEquals(bigDecimal("4.02"), f.price)
        assertEquals("2026-05-15 20:17:19", f.bangkokDateTime)   // dd/MM/yyyy → yyyy-MM-dd, seconds kept
        assertEquals(bigDecimal("0.92"), f.fees)                 // 0.86 comm + 0.06 vat, sign dropped
    }

    @Test
    fun secondsArePreservedSoSameMinuteFillsStayOrdered() {
        val aiio = WebullStatementParser.parse(page3).filter { it.symbol == "AIIO" }
        // 17:50:55 SELL and 17:50:26 BUY share a minute — seconds keep them distinct for FIFO.
        assertEquals("2026-05-14 17:50:55", aiio[0].bangkokDateTime)
        assertEquals("2026-05-14 17:50:26", aiio[1].bangkokDateTime)
    }

    @Test
    fun commaGroupedNumbersParse() {
        val qqq = WebullStatementParser.parse(page3).single { it.symbol == "QQQ" }
        assertEquals(bigDecimal("2"), qqq.quantity)
        assertEquals(bigDecimal("706.47"), qqq.price)
        assertEquals(bigDecimal("1.55"), qqq.fees)               // 1.45 + 0.10
    }
}
