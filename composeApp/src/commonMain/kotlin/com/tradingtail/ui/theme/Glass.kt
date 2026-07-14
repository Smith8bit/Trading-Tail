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
 * The "Modern Terminal" glass vocabulary.
 *
 * Two tiers, deliberately:
 *  - [GlassCard] — every data tile. Translucent fill + sheen border, NO backdrop blur: the ground
 *    behind a tile is the smooth immersive glow, so blur would be invisible but cost a capture per
 *    tile (~25 on the dashboard). Translucency + sheen is the whole visible effect, for free.
 *  - [glassChrome] — the few persistent chrome panels (sidebar, top bar, nav bar). These get real
 *    Haze backdrop blur + noise: one to three nodes, cheap, and the frosted-noise material is what
 *    reads as "glass" even over a smooth ground.
 */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val tc = LocalTradeColors.current
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .clip(shape)
            .background(tc.glass)
            .border(1.dp, tc.sheen, shape),
        content = content,
    )
}

/** Haze style for the chrome panels: heavy blur, glass tint, a whisper of frost noise. */
@Composable
fun glassChrome(): HazeStyle {
    val tc = LocalTradeColors.current
    return HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.surface,
        tint = HazeTint(tc.glass),
        blurRadius = 24.dp,
        noiseFactor = 0.06f,
    )
}
