package com.tradingtail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.tradingtail.common.BigDecimal
import com.tradingtail.common.ZERO

// ponytail: colors only. M3 default Shapes (4/8/12/16) already match the spec, and money mono is
// applied at the call sites via FontFamily.Monospace — no custom Typography/Shapes object needed yet.
// Custom fonts (IBM Plex) are deferred polish; Monospace gives tabular digit alignment for free.

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BD9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEE4FB),
    onPrimaryContainer = Color(0xFF182B72),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF171A1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171A1F),
    surfaceVariant = Color(0xFFEEF0F4),
    onSurfaceVariant = Color(0xFF59616E),
    outline = Color(0xFFD2D7DF),
    outlineVariant = Color(0xFFE6E9EF),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9BCFF),
    onPrimary = Color(0xFF14275F),
    primaryContainer = Color(0xFF2C3E86),
    onPrimaryContainer = Color(0xFFDEE4FB),
    background = Color(0xFF0E1217),
    onBackground = Color(0xFFE5E9EF),
    surface = Color(0xFF151A20),
    onSurface = Color(0xFFE5E9EF),
    surfaceVariant = Color(0xFF1B2129),
    onSurfaceVariant = Color(0xFF98A2B0),
    outline = Color(0xFF333C48),
    outlineVariant = Color(0xFF232A33),
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

private val LightTradeColors = TradeColors(
    gain = Color(0xFF2E7D32),
    loss = Color(0xFFC62828),
    neutralPnl = Color(0xFF59616E),
    gainFill = Color(0xFF2E7D32).copy(alpha = 0.14f),
    lossFill = Color(0xFFC62828).copy(alpha = 0.12f),
)

private val DarkTradeColors = TradeColors(
    gain = Color(0xFF5FD48A),
    loss = Color(0xFFF0736F),
    neutralPnl = Color(0xFF98A2B0),
    gainFill = Color(0xFF5FD48A).copy(alpha = 0.16f),
    lossFill = Color(0xFFF0736F).copy(alpha = 0.16f),
)

val LocalTradeColors = staticCompositionLocalOf { LightTradeColors }

@Composable
fun TradingTailTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTradeColors provides if (dark) DarkTradeColors else LightTradeColors) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
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
