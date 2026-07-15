package com.tradingtail.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * The "Modern Terminal" material vocabulary, in two layers:
 *
 *  - **Glass** is the chrome and the sheet: edge panels ([glassChrome]) truly blur the aurora, and a
 *    report sheet tints it. Translucent — you're meant to see the ground through them.
 *  - **Tiles** ([GlassCard]) are the opposite: opaque, so a figure or a chart reads at full contrast
 *    no matter what the aurora is doing underneath. Data is never negotiable.
 *
 * Tiles used to be frosted too, which is why they can't be: haze samples the aurora (the shell's
 * hazeSource), not whatever is drawn between — so over a tinted sheet a frosted tile reads as a hole
 * punched through to the backdrop. Opaque tiles are what let the sheet be translucent at all.
 */

/** Every data tile: an opaque surface + a 1px sheen border — white on light, near-black on dark. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val tc = LocalTradeColors.current
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, tc.sheen, shape),
        content = content,
    )
}

/** Haze style for the chrome panels (sidebar, bars): heavy blur, glass tint, a whisper of frost noise. */
@Composable
fun glassChrome(): HazeStyle {
    val tc = LocalTradeColors.current
    return HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.surface,
        tint = HazeTint(tc.glass),
        blurRadius = 28.dp,
        noiseFactor = 0.06f,
    )
}
