package com.tradingtail.ui.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tradingtail.common.reducedMotion
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.Radii
import com.tradingtail.ui.theme.Space
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Analytics controls: the responsive breakpoint, tab sheet, segmented control, and chip pills
// shared by the Reports and Dashboard screens. Split from AnalyticsWidgets.kt (2026-07-19).
// ---------------------------------------------------------------------------------------------

// ponytail: one responsive breakpoint drives every 2-up collapse, so a CompositionLocal beats
// prop-drilling a `compact` flag through a dozen view fns. 600dp splits phone (~360dp) from desktop.
internal val LocalCompact = staticCompositionLocalOf { false }

/** "1 trade" / "3 trades" — never "1 trades". (CalendarScreen keeps its own copy; one line each.) */
internal fun tradeCount(n: Int) = if (n == 1) "1 trade" else "$n trades"

/** Card title size — one step down when compact so half-width chart titles stop wrapping to 3 lines. */
@Composable
internal fun cardTitleStyle() =
    if (LocalCompact.current) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium

/** Two cards: side-by-side (each weight 1) on wide screens, stacked full-width when compact. */
@Composable
internal fun TwoUp(a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit) {
    if (LocalCompact.current) {
        a(Modifier.fillMaxWidth()); b(Modifier.fillMaxWidth())
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            a(Modifier.weight(1f)); b(Modifier.weight(1f))
        }
    }
}

/**
 * Two charts sharing a fixed-height band (wide) or stacked at natural height (compact). The lambda's
 * Boolean is the card's `fillHeight` — true only inside the wide fixed-height band.
 */
@Composable
internal fun ChartPair(a: @Composable (Modifier, Boolean) -> Unit, b: @Composable (Modifier, Boolean) -> Unit) {
    if (LocalCompact.current) {
        a(Modifier.fillMaxWidth(), false); b(Modifier.fillMaxWidth(), false)
    } else {
        Row(Modifier.fillMaxWidth().height(380.dp), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            a(Modifier.weight(1f).fillMaxHeight(), true); b(Modifier.weight(1f).fillMaxHeight(), true)
        }
    }
}

/**
 * Vertical padding for a tappable pill (segments, chips, the day-range fields): 8dp on desktop, where
 * a 20dp label makes a 36dp control that a mouse hits fine; 14dp on phones, which lands the same label
 * in a 48dp touch target. Compact-only, so the desktop density the app is tuned to doesn't move.
 *
 * ponytail: padding rather than `defaultMinSize(minHeight = 48.dp)`. That was the obvious tool and it
 * silently made the whole Recent/Year-Month-Day control DISAPPEAR inside OverviewControls'
 * horizontalScroll row — infinite width constraints there, and it measured to nothing (found by
 * bisect against HEAD at 400dp; the un-scrolled Dashboard toggle was unaffected, which is what hid it).
 * Padding measures the same everywhere. Don't "simplify" this back to defaultMinSize.
 */
@Composable
internal fun tapPadV(): Dp = if (LocalCompact.current) 14.dp else Space.sm

/** One pill in a chip row — the mock's selected-vs-idle chip styling (the year / month pickers). */
@Composable
internal fun ToggleChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(Radii.md)
    // The selected chip is edged in the accent, not just tinted with it. A 15%-alpha fill on a pale
    // canvas differs from an idle chip by ~1.2:1 — the month picker read as twelve near-identical
    // ghosts. The border is ~3.5:1 against both fills, so which month is on survives the aurora.
    Box(
        modifier = Modifier.clip(shape).background(bg)
            .then(if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = Space.md, vertical = tapPadV()),
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

/**
 * Chrome-style **tab sheet**: tab heads sitting on a translucent content panel. One continuous path
 * traces the active head *and* the panel with no line between them — filled with glass and stroked
 * with a sheen hairline, so the two read as a single sheet of paper with a tab at its head. The idle
 * heads are muted fills that tuck behind the sheet's top edge. Scrolls horizontally when compact.
 *
 * The sheet is translucent (the aurora glows through it) and the tiles inside it are opaque — that
 * ordering is deliberate, and it's why tiles can't be frosted: see the note in Glass.kt.
 */
@Composable
internal fun TabSheet(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tc = LocalTradeColors.current
    val radius = with(LocalDensity.current) { Radii.md.toPx() }
    val stroke = with(LocalDensity.current) { 1.dp.toPx() }
    // Plain holder, not State: it's only written and read inside layout callbacks, and a State read
    // there would re-invalidate layout every pass. `head` IS State — written in layout, read in draw.
    val root = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var head by remember { mutableStateOf(Rect.Zero) }

    // The sheet outline glides between heads: animate the head's horizontal edges toward the newly
    // selected head, so the tab reads as one moving marker instead of a hard cut. Snaps on first
    // appearance (nothing to glide from) and under reduced motion; only the x-edges move (rows align).
    val reduced = reducedMotion()
    val animL = remember { Animatable(0f) }
    val animR = remember { Animatable(0f) }
    LaunchedEffect(head, reduced) {
        if (head.width <= 1f) return@LaunchedEffect
        if (animR.value <= 1f || reduced) { animL.snapTo(head.left); animR.snapTo(head.right) }
        else {
            launch { animL.animateTo(head.left, tween(220, easing = EaseOutQuart)) }
            animR.animateTo(head.right, tween(220, easing = EaseOutQuart))
        }
    }

    // Four heads at desktop padding measured ~450dp against a 411dp phone, so "Drawdown" rendered as a
    // 13px sliver — a whole report the user had no way to know existed. Tightening the pad and dropping
    // the label a step fits all four at default scale; the strip still scrolls as a backstop for large
    // font scales, and the selected head scrolls itself into view.
    val compact = LocalCompact.current
    val headPad = if (compact) Space.sm else Space.lg
    val headStyle = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
    val headScroll = rememberScrollState()
    val headBounds = remember { mutableStateMapOf<Int, IntRange>() }
    LaunchedEffect(selected, headBounds[selected], headScroll.maxValue) {
        headBounds[selected]?.let { b ->
            val viewport = headScroll.viewportSize
            if (viewport > 0) {
                if (b.last > headScroll.value + viewport) headScroll.animateScrollTo(b.last - viewport)
                else if (b.first < headScroll.value) headScroll.animateScrollTo(b.first)
            }
        }
    }

    Box(
        modifier
            .onGloballyPositioned { root[0] = it }
            .drawWithContent {
                // Drawn from the animated edges once they're live; the raw head until then (first paint).
                val hd = if (head.width > 1f && animR.value > 1f) Rect(animL.value, head.top, animR.value, head.bottom) else head
                val sheet = if (hd.width > 1f) sheetOutline(hd, size, radius) else null
                // Fill under the content, stroke over it: the edge then crosses the idle heads'
                // bottoms, which is what tucks them behind the sheet.
                sheet?.let { drawPath(it, tc.glass) }
                drawContent()
                sheet?.let { drawPath(it, tc.sheen, style = Stroke(stroke)) }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(headScroll),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                items.forEachIndexed { i, label ->
                    val isSel = i == selected
                    Box(
                        modifier = Modifier
                            // Only the active head reports; the outline routes around wherever it lands
                            // (localBoundingBoxOf resolves the strip's scroll for free).
                            .then(
                                if (isSel) Modifier.onGloballyPositioned { c ->
                                    root[0]?.let { head = it.localBoundingBoxOf(c, clipBounds = false) }
                                } else Modifier,
                            )
                            // Every head reports its own span in strip coordinates, so the strip can
                            // scroll the selected one fully into view rather than leaving it a sliver.
                            .onPlaced { c -> headBounds[i] = c.positionInParent().x.toInt().let { it..(it + c.size.width) } }
                            .clip(RoundedCornerShape(topStart = Radii.md, topEnd = Radii.md))
                            .background(if (isSel) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSelect(i) }
                            .padding(horizontal = headPad, vertical = Space.md),
                    ) {
                        Text(
                            label,
                            // The active head has no fill of its own — the sheet's glass is drawn
                            // under it, so its label is on-surface like the sheet's contents.
                            color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = headStyle,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
    }
}

/** The tab-sheet silhouette: up and over the active head, then around the panel — open under the head. */
private fun sheetOutline(head: Rect, size: Size, r: Float): Path {
    val top = head.bottom // the panel's top edge — the seam the head opens into
    val w = size.width
    val h = size.height
    val l = head.left.coerceIn(0f, w) // a part-scrolled head still yields a sane path
    val rt = head.right.coerceIn(l, w)
    val hr = r.coerceAtMost(head.height / 2f)
    return Path().apply {
        moveTo(0f, h - r)
        lineTo(0f, top)                                   // panel's left side
        lineTo(l, top)                                    // top edge, left of the head
        lineTo(l, head.top + hr)                          // up the head's left side
        quadraticBezierTo(l, head.top, l + hr, head.top)
        lineTo(rt - hr, head.top)                         // across the head's top
        quadraticBezierTo(rt, head.top, rt, head.top + hr)
        lineTo(rt, top)                                   // down the head's right side
        lineTo(w, top)                                    // top edge, right of the head
        lineTo(w, h - r)                                  // panel's right side
        quadraticBezierTo(w, h, w - r, h)
        lineTo(r, h)                                      // panel's bottom
        quadraticBezierTo(0f, h, 0f, h - r)
    }
}

/**
 * A **segmented control** for the sub-level switchers (view mode, period, category): one recessed
 * surfaceVariant track, segments butted together, the selected one raised to a surface pill with a
 * sheen edge + primary label. Deliberately subordinate to the tab sheet above. [selected] may be out
 * of range so a nav-only segment (Calendar) shows no highlight.
 *
 * The track **wraps** rather than scrolling: a segment the user can't see is a segment they don't know
 * exists, and the category track (four labels up to "Win/Loss/Expectation") ran ~300dp past a 411dp
 * phone with nothing to suggest it continued. Wrapping fits by construction at any label set and any
 * font scale, so there's no width to keep re-tuning. Callers must give this a bounded width — inside a
 * horizontalScroll the constraint is infinite and it would lay out as one unwrapped row again.
 *
 * **The track is a tile (opaque surface + sheen), not a recessed grey well.** It used to fill with
 * surfaceVariant, which made it a hostage to whatever it happened to sit on: inside Reports' glass
 * sheet it read as a clean well (1.29:1 on that pale backdrop), but the Dashboard has no sheet, so the
 * same grey landed straight on the aurora's blue blob at **1.01:1 — the control dissolved into the
 * background**. Identical colour, opposite result, which is exactly why a fill can't be trusted here:
 * the aurora is a gradient, so no single value separates from it everywhere. An opaque tile plus its
 * own hairline carries its edge onto any backdrop — the same material (and the same reason) as every
 * data tile in Glass.kt. Selected then can't be a white pill on a white track, so it takes the chip's
 * accent tint + accent border; idle is a bare label. One selection language, two containers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SegmentedControl(items: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val tc = LocalTradeColors.current
    val track = RoundedCornerShape(Radii.md)
    FlowRow(
        modifier = Modifier
            .clip(track)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, tc.sheen, track)
            .padding(Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        items.forEachIndexed { i, label ->
            val isSel = i == selected
            val seg = RoundedCornerShape(Radii.sm)
            Box(
                modifier = Modifier.clip(seg)
                    // Accent tint + accent edge. The fill alone would be ~1.2:1 on either theme, so the
                    // border is what actually says "this one is on" (#005FFF is 5.15:1 on the tile).
                    .then(
                        if (isSel) {
                            Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .border(1.dp, MaterialTheme.colorScheme.primary, seg)
                        } else Modifier,
                    )
                    .clickable { onSelect(i) }
                    .padding(horizontal = Space.md, vertical = tapPadV()),
            ) {
                Text(
                    label,
                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

/** 30/60/90-day window selector — a segmented control (the mock's dashboard header period toggle). */
@Composable
internal fun PeriodToggle(period: Int, onSelect: (Int) -> Unit) {
    val opts = listOf(30, 60, 90)
    SegmentedControl(opts.map { "$it Days" }, opts.indexOf(period)) { onSelect(opts[it]) }
}
