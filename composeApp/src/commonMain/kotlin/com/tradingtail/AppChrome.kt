package com.tradingtail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradingtail.data.sync.SyncState
import com.tradingtail.ui.settings.initials
import com.tradingtail.ui.theme.BarChartIcon
import com.tradingtail.ui.theme.LocalTradeColors
import com.tradingtail.ui.theme.MoonIcon
import com.tradingtail.ui.theme.Radii
import com.tradingtail.ui.theme.Space
import com.tradingtail.ui.theme.SunIcon
import com.tradingtail.ui.theme.glassChrome
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

// The app shell's chrome — sidebar (desktop), floating pill (mobile), and their shared controls.
// Split from App.kt (2026-07-19); App keeps the state, this file keeps the panels.

/**
 * Everything the desktop sidebar and the mobile floating pill both render — one bundle, so the two
 * chrome surfaces can't drift apart as controls are added.
 */
internal class ShellChrome(
    val dark: Boolean,
    val onToggleTheme: () -> Unit,
    val onImport: () -> Unit,
    val syncEnabled: Boolean,
    val syncState: SyncState,
    val onSync: () -> Unit,
    val displayName: String,
    val onOpenSettings: () -> Unit,
)

@Composable
internal fun CaptureFab(onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) { Icon(Icons.Filled.Add, contentDescription = "Record trade") }
}

// Mobile has no app bar or sidebar — these controls float over the content instead. A glass pill
// keeps them legible against whatever scrolls underneath.
@Composable
internal fun FloatingActions(hazeState: HazeState, chrome: ShellChrome, modifier: Modifier) {
    val tc = LocalTradeColors.current
    val shape = RoundedCornerShape(Radii.pill)
    Row(
        modifier = modifier
            .clip(shape)
            // Real backdrop blur, like the sidebar and nav bar. This was a bare 50%-alpha fill with no
            // hazeEffect — tier-2 chrome glass per DESIGN.md, implemented as a translucent panel that
            // didn't blur, so trade rows scrolled visibly through it. (Measured impact was mild —
            // 1.05:1 to 1.28:1 ghosting — but the material is the system's, not this panel's to opt out
            // of.) The tint comes from the style, so no separate background().
            .hazeEffect(hazeState, glassChrome())
            .border(1.dp, tc.sheen, shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(chrome.displayName, chrome.onOpenSettings)
        ThemeToggleButton(chrome.dark, chrome.onToggleTheme)
        TextButton(onClick = chrome.onImport) { Text("Import") }
        if (chrome.syncEnabled) SyncButton(chrome.syncState, chrome.onSync)
    }
}

// The account avatar — a tappable initials disc that opens Settings. On mobile it's the only profile
// entry point (the sidebar chip is desktop-only); the 48dp box gives it a full thumb target.
@Composable
private fun AccountAvatar(name: String, onClick: () -> Unit) {
    Box(modifier = Modifier.size(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials(name), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Show the mode you'll switch *to*: a sun while dark, a moon while light.
@Composable
private fun ThemeToggleButton(dark: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        // M3's IconButton is nominally 48dp, but measured 47.6dp wide on a Pixel 9 (125px at density
        // 2.625) once the pill's horizontal padding took its cut. 0.4dp is nothing to a thumb — it is
        // also the entire margin, and a floor costs one modifier.
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
    ) {
        Icon(
            if (dark) SunIcon else MoonIcon,
            contentDescription = if (dark) "Switch to light theme" else "Switch to dark theme",
            tint = LocalTradeColors.current.accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Manual sync trigger — the 20s poll already runs, this is the "don't make me wait" button. Reflects
 * the shared [SyncState]: a spinner while a pass (manual or the poll's) is in flight, the loss colour
 * if the last pass errored, plain otherwise. Disabled mid-sync so a double-tap can't stack passes.
 */
@Composable
private fun SyncButton(state: SyncState, onSync: () -> Unit, modifier: Modifier = Modifier) {
    val label = when {
        state.syncing -> "Syncing…"
        state.error != null -> "Sync failed"
        else -> "Sync"
    }
    TextButton(onClick = onSync, enabled = !state.syncing, modifier = modifier) {
        if (state.syncing) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalTradeColors.current.accent)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = if (state.error != null && !state.syncing) MaterialTheme.colorScheme.error else Color.Unspecified)
    }
}

/** Desktop sidebar: a floating glass panel — inset from the window edge, real backdrop blur + frost. */
@Composable
internal fun Sidebar(hazeState: HazeState, current: Screen, onSelect: (Screen) -> Unit, onNewTrade: () -> Unit, chrome: ShellChrome) {
    val tc = LocalTradeColors.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier.fillMaxHeight().width(256.dp)
            .padding(Space.md) // inset margin — the panel floats over the immersive ground
            .clip(shape)
            .hazeEffect(hazeState, glassChrome()) // real blur + noise; tint comes from the style
            .border(1.dp, tc.sheen, shape)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Brand
        Row(
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(Radii.md)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(BarChartIcon, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(17.dp))
            }
            Row {
                Text("Trading", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tail",
                    color = tc.accent,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Screen.entries.forEach { s -> NavItem(s, s == current) { onSelect(s) } }

        Spacer(Modifier.weight(1f))

        // "New Trade" CTA — solid Signal Blue (the one chrome accent), matching "Record trade"
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onNewTrade).padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("New Trade", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = chrome.onImport, modifier = Modifier.fillMaxWidth()) { Text("Import statement") }

        if (chrome.syncEnabled) SyncButton(chrome.syncState, chrome.onSync, Modifier.fillMaxWidth())

        // Account chip — name + initials from the local profile; tapping it (not the theme toggle) opens Settings.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(Radii.md)).clickable(onClick = chrome.onOpenSettings).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initials(chrome.displayName), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(chrome.displayName.ifBlank { "Set your name" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("Bangkok · USD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ThemeToggleButton(chrome.dark, chrome.onToggleTheme)
        }
    }
}

@Composable
private fun NavItem(screen: Screen, selected: Boolean, onClick: () -> Unit) {
    val tc = LocalTradeColors.current
    val bg = if (selected) tc.accent.copy(alpha = 0.15f) else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val iconTint = if (selected) tc.accent else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(screen.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        Text(screen.label, color = fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
