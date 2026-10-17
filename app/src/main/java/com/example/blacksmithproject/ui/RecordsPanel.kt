package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.R
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.RecordsPage
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Space

/**
 * Records: the Gazette ("News"), the experiment journal and the legacy page as three segments of one lazy list. Only
 * the selected segment is composed and its rows are keyed, so a long journal costs what is on screen. The list owns
 * its own scroll (it must not sit inside the workshop's scrolling column), and each segment keeps its own place.
 */
@Composable
fun RecordsPanel(s: UiState.Playing, vm: GameViewModel, modifier: Modifier = Modifier) {
    // A classic run has Town for its people; a page kept from a guild run falls back to the Gazette.
    val pages = if (s.guild != null) RecordsPage.entries else RecordsPage.entries - RecordsPage.TOWNSFOLK
    val page = s.records.takeIf { it in pages } ?: RecordsPage.GAZETTE
    var bookTab by rememberSaveable { mutableStateOf(BookTab.METAL) }
    var showGone by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(horizontal = Space.md).padding(top = Space.sm)) {
        SegmentRow(page, vm::selectRecords, pages = pages)
        // The notebook is a bound book: a leather frame stays over the page while its rows scroll inside.
        val book = page == RecordsPage.JOURNAL
        val inset = if (book) frameInset() else 0.dp
        LazyColumn(
            Modifier.fillMaxSize().then(if (book) Modifier.padding(top = Space.sm).pixelFrame(R.drawable.panel_journal) else Modifier).testTag("records_list"),
            state = rememberSaveable(page, bookTab, saver = LazyListState.Saver) { LazyListState() },
            contentPadding = PaddingValues(start = inset + if (book) Space.sm else 0.dp, end = inset + if (book) Space.sm else 0.dp, top = inset, bottom = inset + Space.lg),
        ) {
            when (page) {
                // The edition archive is one item: its per-day rows keep the Gazette's own open and close state.
                RecordsPage.GAZETTE -> item(key = "news") { Column { GazettePanel(s, vm) } }
                RecordsPage.JOURNAL -> notebookItems(s, vm, bookTab) { bookTab = it }
                RecordsPage.LEGACY -> legacyItems(s, vm)
                RecordsPage.TOWNSFOLK -> townsfolkItems(s, vm, showGone) { showGone = !showGone }
            }
        }
    }
}
