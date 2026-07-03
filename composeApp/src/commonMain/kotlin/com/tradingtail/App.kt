package com.tradingtail

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.tradingtail.ui.journal.JournalScreen
import com.tradingtail.ui.tradeentry.QuickTradeEntryScreen
import com.tradingtail.ui.tradeentry.QuickTradeEntryViewModel

private enum class Screen(val label: String) {
    Journal("Journal"),
    Quick("Quick Entry"),
}

/**
 * ponytail: state-based navigation — a `when(screen)` switch behind a bottom bar. No nav library
 * for two flat destinations. Journal is home.
 */
@Composable
fun App(module: AppModule) {
    MaterialTheme {
        var screen by remember { mutableStateOf(Screen.Journal) }
        val quickVm = remember { QuickTradeEntryViewModel(module.recordQuickTrade) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    Screen.entries.forEach { s ->
                        NavigationBarItem(
                            selected = screen == s,
                            onClick = { screen = s },
                            icon = {},
                            label = { Text(s.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Surface(modifier = Modifier.padding(padding)) {
                when (screen) {
                    Screen.Journal -> JournalScreen(module.tradeRepo)
                    Screen.Quick -> QuickTradeEntryScreen(quickVm)
                }
            }
        }
    }
}
