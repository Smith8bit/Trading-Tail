package com.tradingtail.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The "Modern Terminal" glass vocabulary — frosted panels over the aurora backdrop.
 *
 * [LocalHazeState] carries the shell's haze source to every [GlassCard] without threading a parameter
 * through a dozen screen signatures. When present, tiles get true backdrop blur (the aurora frosts
 * through them); when absent (previews, tests), they fall back to plain translucency.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Every data tile: frosted glass — backdrop blur + glass tint + 1px sheen border. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val tc = LocalTradeColors.current
    val shape = MaterialTheme.shapes.medium
    val haze = LocalHazeState.current
    val glassed = modifier.clip(shape).let {
        if (haze != null) it.hazeEffect(haze, glassTile()) else it.background(tc.glass)
    }
    Column(modifier = glassed.border(1.dp, tc.sheen, shape), content = content)
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

/** Haze style for data tiles: lighter blur, same glass tint, near-zero noise (figures stay crisp). */
@Composable
fun glassTile(): HazeStyle {
    val tc = LocalTradeColors.current
    return HazeStyle(
        backgroundColor = MaterialTheme.colorScheme.surface,
        tint = HazeTint(tc.glass),
        blurRadius = 20.dp,
        noiseFactor = 0.03f,
    )
}
