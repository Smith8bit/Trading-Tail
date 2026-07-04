package com.tradingtail.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO

// ponytail: colors only. M3 default Shapes (4/8/12/16) already match the spec, and money mono is
// applied at the call sites via FontFamily.Monospace — no custom Typography/Shapes object needed yet.
// Custom fonts (IBM Plex) are deferred polish; Monospace gives tabular digit alignment for free.

// Dark-only app. Blue-black ground + slate surfaces, TraderVue green accent (matches dashboard mock).
// background #0D131C is darker than surface #1B2431 so Cards read as raised. onSurfaceVariant #8B98A9
// on surface #1B2431 clears 4.5:1 for muted text. onPrimary is dark for contrast on the bright green.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF22C55E),
    onPrimary = Color(0xFF06170D),

    primaryContainer = Color(0xFF16A34A),
    onPrimaryContainer = Color(0xFFFFFFFF),

    secondary = Color(0xFF22C55E),
    onSecondary = Color(0xFF06170D),

    secondaryContainer = Color(0xFF232E3D),
    onSecondaryContainer = Color(0xFFE6EDF3),

    background = Color(0xFF0D131C),
    onBackground = Color(0xFFE6EDF3),

    surface = Color(0xFF1B2431),
    onSurface = Color(0xFFE6EDF3),

    surfaceVariant = Color(0xFF232E3D),
    onSurfaceVariant = Color(0xFF8B98A9),

    outline = Color(0xFF2C3A4C),
    outlineVariant = Color(0xFF232E3D),

    error = Color(0xFFF0736F),
    onError = Color(0xFF14275F),
)


/** Gain/loss semantics that Material's ColorScheme has no slot for. One source, not per-screen hex. */
data class TradeColors(
    val gain: Color,
    val loss: Color,
    val neutralPnl: Color,
    val gainFill: Color,
    val lossFill: Color,
)

private val DarkTradeColors = TradeColors(
    gain = Color(0xFF5FD48A),
    loss = Color(0xFFF0736F),
    neutralPnl = Color(0xFF98A2B0),
    gainFill = Color(0xFF5FD48A).copy(alpha = 0.16f),
    lossFill = Color(0xFFF0736F).copy(alpha = 0.16f),
)

val LocalTradeColors = staticCompositionLocalOf { DarkTradeColors }

@Composable
fun TradingTailTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTradeColors provides DarkTradeColors) {
        MaterialTheme(colorScheme = DarkColors, content = content)
    }
}

/** The single P&L color rule — replaces the GAIN/LOSS/pnlColor duplicated across screens. */
@Composable
fun pnlColor(pnl: BigDecimal): Color = LocalTradeColors.current.let {
    when {
        pnl > ZERO -> it.gain
        pnl < ZERO -> it.loss
        else -> it.neutralPnl
    }
}

/** Tint for magnitude bars / calendar cells — the faint gain/loss fill behind content. */
@Composable
fun pnlFill(pnl: BigDecimal): Color = LocalTradeColors.current.let {
    when {
        pnl > ZERO -> it.gainFill
        pnl < ZERO -> it.lossFill
        else -> Color.Transparent
    }
}
