package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.Panel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Space

/**
 * Records: the Gazette ("News"), the experiment journal and the legacy page as three segments of one lazy list. Only
 * the selected segment is composed and its rows are keyed, so a long journal costs what is on screen. The list owns
 * its own scroll (it must not sit inside the workshop's scrolling column) and restarts at the top for each segment.
 */
@Composable
fun RecordsPanel(s: UiState.Playing, vm: GameViewModel, modifier: Modifier = Modifier) {
    val pages = listOf(Panel.GAZETTE, Panel.JOURNAL, Panel.LEGACY)
    val page = if (s.panel in pages) s.panel else Panel.GAZETTE
    Column(modifier.fillMaxSize().padding(horizontal = Space.md).padding(top = Space.sm)) {
        SegmentRow(pages, page, vm::selectPanel)
        LazyColumn(
            Modifier.fillMaxSize().testTag("records_list"),
            state = remember(page) { LazyListState() },
            contentPadding = PaddingValues(bottom = Space.lg),
        ) {
            when (page) {
                // The edition archive is one item: its per-day rows keep the Gazette's own open and close state.
                Panel.GAZETTE -> item(key = "news") { Column { GazettePanel(s) } }
                Panel.JOURNAL -> journalItems(s, vm)
                else -> legacyItems(s, vm)
            }
        }
    }
}
