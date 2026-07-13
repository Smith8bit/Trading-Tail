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

// ponytail: colors + a Tailwind-sized Typography (see AppTypography). M3 default Shapes are overridden
// by the softer Radii scale in Spacing.kt; money mono is applied at call sites via FontFamily.Monospace.
// Custom fonts (IBM Plex) are deferred polish; Monospace gives tabular digit alignment for free.

// "Modern Terminal" — an immersive, bento-composed reskin. Webull blue #005FFF is the single chrome
// accent (actions/selection only); P&L green/red is untouched data. Light + dark are equals.

// ── Dark: immersive near-black ground, deep-blue tint. background (#080B12) sits under surface
// (#10151F) so bento tiles read as raised. onSurfaceVariant #8A97A9 clears 4.5:1 on surface.
// Webull blue #005FFF carries white text at ~5:1, so it fills buttons directly on both themes.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF005FFF),
    onPrimary = Color(0xFFFFFFFF),

    primaryContainer = Color(0xFF0A3FA8),
    onPrimaryContainer = Color(0xFFE8EEFB),

    secondary = Color(0xFF4D8BFF),
    onSecondary = Color(0xFF04121F),

    secondaryContainer = Color(0xFF1A2230),
    onSecondaryContainer = Color(0xFFE9EEF5),

    background = Color(0xFF080B12),
    onBackground = Color(0xFFE9EEF5),

    surface = Color(0xFF10151F),
    onSurface = Color(0xFFE9EEF5),

    surfaceVariant = Color(0xFF1A2230),
    onSurfaceVariant = Color(0xFF8A97A9),

    outline = Color(0xFF212E40),
    outlineVariant = Color(0xFF18222F),

    error = Color(0xFFF0736F),
    onError = Color(0xFF3A0D0C),
)

// ── Light: cool paper canvas (#EEF1F6) under white surfaces. Webull blue stays #005FFF (bright cyan
// couldn't carry text here, but blue can). Muted ink #586576 clears 4.5:1 on white.
private val LightColors = lightColorScheme(
    primary = Color(0xFF005FFF),
    onPrimary = Color(0xFFFFFFFF),

    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF00224D),

    secondary = Color(0xFF005FFF),
    onSecondary = Color(0xFFFFFFFF),

    secondaryContainer = Color(0xFFE3EBF6),
    onSecondaryContainer = Color(0xFF121822),

    background = Color(0xFFEEF1F6),
    onBackground = Color(0xFF121822),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121822),

    surfaceVariant = Color(0xFFE9EEF4),
    onSurfaceVariant = Color(0xFF586576),

    outline = Color(0xFFD4DCE7),
    outlineVariant = Color(0xFFE6EBF2),

    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
)


/**
 * Gain/loss semantics + a couple of chrome tokens Material's ColorScheme has no slot for.
 * [accent] is the on-surface chrome accent (brightened to #4D8BFF on the dark ground where the raw
 * #005FFF goes muddy on near-black; identical to primary on light). [glass] is the translucent panel
 * fill for edge chrome (sidebar, sheets) — the immersive canvas + glow bleed through it.
 */
data class TradeColors(
    val gain: Color,
    val loss: Color,
    val neutralPnl: Color,
    val gainFill: Color,
    val lossFill: Color,
    val accent: Color,
    val glass: Color,
)

private val DarkTradeColors = TradeColors(
    gain = Color(0xFF5FD48A),
    loss = Color(0xFFF0736F),
    neutralPnl = Color(0xFF98A2B0),
    gainFill = Color(0xFF5FD48A).copy(alpha = 0.16f),
    lossFill = Color(0xFFF0736F).copy(alpha = 0.16f),
    accent = Color(0xFF4D8BFF),
    glass = Color(0xFF10151F).copy(alpha = 0.55f),
)

private val LightTradeColors = TradeColors(
    gain = Color(0xFF2E7D32),
    loss = Color(0xFFC62828),
    neutralPnl = Color(0xFF59616E),
    gainFill = Color(0xFF2E7D32).copy(alpha = 0.12f),
    lossFill = Color(0xFFC62828).copy(alpha = 0.12f),
    accent = Color(0xFF005FFF),
    glass = Color(0xFFFFFFFF).copy(alpha = 0.72f),
)

val LocalTradeColors = staticCompositionLocalOf { DarkTradeColors }

@Composable
fun TradingTailTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalTradeColors provides if (dark) DarkTradeColors else LightTradeColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
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
