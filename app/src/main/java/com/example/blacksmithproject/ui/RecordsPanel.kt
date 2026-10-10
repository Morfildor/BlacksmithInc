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
    val page = s.records
    var bookTab by rememberSaveable { mutableStateOf(BookTab.METAL) }
    Column(modifier.fillMaxSize().padding(horizontal = Space.md).padding(top = Space.sm)) {
        SegmentRow(page, vm::selectRecords)
        LazyColumn(
            Modifier.fillMaxSize().testTag("records_list"),
            state = rememberSaveable(page, bookTab, saver = LazyListState.Saver) { LazyListState() },
            contentPadding = PaddingValues(bottom = Space.lg),
        ) {
            when (page) {
                // The edition archive is one item: its per-day rows keep the Gazette's own open and close state.
                RecordsPage.GAZETTE -> item(key = "news") { Column { GazettePanel(s) } }
                RecordsPage.JOURNAL -> notebookItems(s, vm, bookTab) { bookTab = it }
                RecordsPage.LEGACY -> legacyItems(s, vm)
            }
        }
    }
}
