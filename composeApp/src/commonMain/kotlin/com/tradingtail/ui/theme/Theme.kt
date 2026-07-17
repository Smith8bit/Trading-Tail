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
//
// EVERY slot is set on purpose: an unset one silently falls back to M3's baseline lavender, and the
// components that pick their own container (DropdownMenu → surfaceContainer, DatePickerDialog /
// AlertDialog → surfaceContainerHigh, ModalBottomSheet → surfaceContainerLow, Snackbar →
// inverseSurface) would drag that purple into the app. Add a slot here before using a new component.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF005FFF),
    onPrimary = Color(0xFFFFFFFF),

    primaryContainer = Color(0xFF0A3FA8),
    onPrimaryContainer = Color(0xFFE8EEFB),
    inversePrimary = Color(0xFFB8CEFF),

    secondary = Color(0xFF4D8BFF),
    onSecondary = Color(0xFF04121F),

    secondaryContainer = Color(0xFF1A2230),
    onSecondaryContainer = Color(0xFFE9EEF5),

    // No third brand hue — tertiary mirrors the sky-blue accent so nothing can drift off-palette.
    tertiary = Color(0xFF4D8BFF),
    onTertiary = Color(0xFF04121F),
    tertiaryContainer = Color(0xFF1A2230),
    onTertiaryContainer = Color(0xFFE9EEF5),

    background = Color(0xFF080B12),
    onBackground = Color(0xFFE9EEF5),

    surface = Color(0xFF10151F),
    onSurface = Color(0xFFE9EEF5),

    // The recessed-fill role: segmented-control tracks, idle tab heads, idle chips, gauge/meter
    // tracks. Was #1A2230, only 1.23:1 against the canvas — an idle option you had to hunt for.
    // #212B3C reads at 1.38:1 and still carries onSurfaceVariant at 4.80:1. See the note on the
    // light scheme's twin: fill alone can't carry *selected*, the accent border does that.
    surfaceVariant = Color(0xFF212B3C),
    onSurfaceVariant = Color(0xFF8A97A9),
    surfaceTint = Color(0xFF005FFF),

    // The container ramp M3 reaches for on its own: menus/sheets land on the tile tone, dialogs a
    // step above it.
    surfaceDim = Color(0xFF080B12),
    surfaceBright = Color(0xFF232E3D),
    surfaceContainerLowest = Color(0xFF05070C),
    surfaceContainerLow = Color(0xFF0B0F17),
    surfaceContainer = Color(0xFF10151F),
    surfaceContainerHigh = Color(0xFF1A2230),
    surfaceContainerHighest = Color(0xFF232E3D),

    inverseSurface = Color(0xFFE9EEF5),
    inverseOnSurface = Color(0xFF10151F),

    outline = Color(0xFF212E40),
    outlineVariant = Color(0xFF18222F),

    error = Color(0xFFF0736F),
    onError = Color(0xFF3A0D0C),
    errorContainer = Color(0xFF3A0D0C),
    onErrorContainer = Color(0xFFFFD9D6),

    scrim = Color(0xFF000000),
)

// ── Light: cool paper canvas (#EEF1F6) under white surfaces. Webull blue stays #005FFF (bright cyan
// couldn't carry text here, but blue can). Muted ink #586576 clears 4.5:1 on white.
// Same rule as the dark scheme: every slot set, no baseline lavender left to leak.
private val LightColors = lightColorScheme(
    primary = Color(0xFF005FFF),
    onPrimary = Color(0xFFFFFFFF),

    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF00224D),
    inversePrimary = Color(0xFFA8C7FF),

    secondary = Color(0xFF005FFF),
    onSecondary = Color(0xFFFFFFFF),

    secondaryContainer = Color(0xFFE3EBF6),
    onSecondaryContainer = Color(0xFF121822),

    tertiary = Color(0xFF005FFF),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD6E4FF),
    onTertiaryContainer = Color(0xFF00224D),

    background = Color(0xFFEEF1F6),
    onBackground = Color(0xFF121822),

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121822),

    // The recessed-fill role: segmented-control tracks, idle tab heads, idle chips, gauge/meter
    // tracks. Was #E9EEF4 — 1.03:1 against the #EEF1F6 canvas, i.e. the same colour. Every idle
    // option in the app (30/60/90, the month picker, the unselected report tabs) was a ghost.
    //
    // #C9D5E5 is as deep as this can go while onSurfaceVariant still clears 4.5:1 on it (4.95:1),
    // and it only buys 1.31:1 — because on a light ground **fill lightness cannot carry state**:
    // white-on-track tops out near 1.5:1 no matter what. So the fill's job is only to make an idle
    // option *visible*; **the primary border on the selected one carries which is on** (#005FFF is
    // 3.5:1 vs this fill, clearing WCAG 1.4.11's 3:1 for state). Don't chase state via fill here.
    surfaceVariant = Color(0xFFC9D5E5),
    // Darkened with the fill above (was #586576, which fell to 3.99:1 on it — below the 4.5 floor).
    // #4A5768 holds 4.95:1 there and improves every other muted-text site too (5.94 → 7.20:1 on white).
    onSurfaceVariant = Color(0xFF4A5768),
    surfaceTint = Color(0xFF005FFF),

    // Menus and dialogs stay white/near-white here — they're paper on the pale canvas.
    surfaceDim = Color(0xFFD8DEE8),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFD),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF6F8FB),
    surfaceContainerHighest = Color(0xFFE9EEF4),

    inverseSurface = Color(0xFF121822),
    inverseOnSurface = Color(0xFFEEF1F6),

    outline = Color(0xFFD4DCE7),
    outlineVariant = Color(0xFFE6EBF2),

    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF5C0F0F),

    scrim = Color(0xFF000000),
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
    val sheen: Color, // 1px glass border highlight — the light edge that sells the material
)

private val DarkTradeColors = TradeColors(
    gain = Color(0xFF5FD48A),
    loss = Color(0xFFF0736F),
    neutralPnl = Color(0xFF98A2B0),
    gainFill = Color(0xFF5FD48A).copy(alpha = 0.16f),
    lossFill = Color(0xFFF0736F).copy(alpha = 0.16f),
    accent = Color(0xFF4D8BFF),
    // Thinner tint than a solid surface — backdrop blur (Glass.kt) carries the legibility.
    glass = Color(0xFF10161F).copy(alpha = 0.50f),
    sheen = Color(0xFFFFFFFF).copy(alpha = 0.12f),
)

private val LightTradeColors = TradeColors(
    gain = Color(0xFF2E7D32),
    loss = Color(0xFFC62828),
    neutralPnl = Color(0xFF59616E),
    gainFill = Color(0xFF2E7D32).copy(alpha = 0.12f),
    lossFill = Color(0xFFC62828).copy(alpha = 0.12f),
    accent = Color(0xFF005FFF),
    glass = Color(0xFFFFFFFF).copy(alpha = 0.55f),
    sheen = Color(0xFFD4DCE7), // on light, the hairline defines the glass edge (white sheen is invisible)
)

val LocalTradeColors = staticCompositionLocalOf { DarkTradeColors }

@Composable
fun TradingTailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // The system bars follow the app's theme, not the OS's — see SystemBarsEffect. It belongs here
    // because this is the one place that knows which scheme is actually on screen.
    SystemBarsEffect(darkTheme)
    CompositionLocalProvider(LocalTradeColors provides if (darkTheme) DarkTradeColors else LightTradeColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
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
