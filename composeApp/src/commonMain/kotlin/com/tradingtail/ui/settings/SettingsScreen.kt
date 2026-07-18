package com.tradingtail.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tradingtail.common.SettingsData
import com.tradingtail.ui.theme.Radii
import com.tradingtail.ui.theme.Space

/** "K. Siwatt" → "KS", "trader" → "TR". Falls back to "?" for a blank name. */
fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/**
 * First-run: ask the trader's name before the app opens. Stored on-device only — the copy says so,
 * because "account" can imply a cloud sign-up and this deliberately isn't one.
 */
@Composable
fun OnboardingScreen(onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = modifier.padding(Space.xl).imePadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Welcome to Trading Tail", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Space.sm))
        Text(
            "What should we call you? Your profile stays on this device — nothing here is sent anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.lg))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your name") },
            singleLine = true,
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.lg))
        Button(
            onClick = { onDone(name.trim()) },
            enabled = name.isNotBlank(),
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
        ) { Text("Get started") }
    }
}

/**
 * Profile + optional cloud sync. Sync is off until the trader pastes their OWN Supabase project's URL
 * and publishable key — the app never ships shared credentials, so each person's data stays theirs.
 */
@Composable
fun SettingsDialog(current: SettingsData, onSave: (SettingsData) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current.displayName) }
    var url by rememberSaveable { mutableStateOf(current.supabaseUrl) }
    var key by rememberSaveable { mutableStateOf(current.supabaseKey) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(Radii.lg),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp).padding(Space.lg).verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(Space.md),
            ) {
                Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()

                Text("Cloud sync (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Sync across your devices using your OWN Supabase project. Create one (free), run " +
                        "supabase/schema.sql in it, then paste its URL and publishable key. Takes effect after a restart.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Supabase URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Publishable key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(Space.sm))
                    Button(onClick = {
                        onSave(current.copy(displayName = name.trim(), supabaseUrl = url.trim(), supabaseKey = key.trim()))
                    }) { Text("Save") }
                }
            }
        }
    }
}
