package com.example.blacksmithproject

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.data.GameRepository
import com.example.blacksmithproject.data.Settings
import com.example.blacksmithproject.data.ShopDaySpeed
import com.example.blacksmithproject.data.StoredRows
import com.example.blacksmithproject.ui.RecordsPanel
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

internal class MemoryRepository(private var run: String?, private var legacy: String?, private var cursor: String? = null) : GameRepository {
    override suspend fun load() = StoredRows(run, legacy, cursor)
    override suspend fun commit(run: String?, legacy: String) { this.run = run; this.legacy = legacy }
    override suspend fun saveCursor(cursor: String?) { this.cursor = cursor }
    override suspend fun quarantine(key: String) = Unit
}

internal class QuietSettings : Settings {
    override val reducedMotion = MutableStateFlow(false)
    override val haptics = MutableStateFlow(true)
    override val seenTips = MutableStateFlow(emptySet<String>())
    override suspend fun setReducedMotion(value: Boolean) { reducedMotion.value = value }
    override suspend fun setHaptics(value: Boolean) { haptics.value = value }
    override suspend fun markTipSeen(id: String) { seenTips.value += id }
    override suspend fun dismissedReport(): String? = null
    override val shopDaySpeed = MutableStateFlow(ShopDaySpeed.TAP)
    override suspend fun setShopDaySpeed(value: ShopDaySpeed) { shopDaySpeed.value = value }
}

/** Records is three segments over one lazy list; each keeps its own content and only the chosen one is composed. */
@RunWith(AndroidJUnit4::class)
class RecordsPanelTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun threeSegmentsKeepTheirContent() {
        val engine = GameEngine()
        val start = engine.newRun(LegacyProfile(), 42L)
        val repo = MemoryRepository(SaveCodec.encodeRun(start), SaveCodec.encodeLegacy(start.legacy))
        lateinit var vm: GameViewModel
        compose.runOnUiThread { vm = GameViewModel(engine, GameSession(engine, repo), QuietSettings(), SavedStateHandle()) }
        compose.setContent {
            BlacksmithProjectTheme {
                val ui by vm.ui.collectAsState()
                (ui as? UiState.Playing)?.let { Box(Modifier.size(360.dp, 560.dp)) { RecordsPanel(it, vm) } }
            }
        }
        compose.waitUntil(10_000) { vm.ui.value is UiState.Playing }
        compose.runOnUiThread { vm.selectDest(Dest.RECORDS) }

        // News is the first segment: the Gazette archive.
        compose.onNodeWithTag("page_gazette").assertIsSelected()
        compose.onNodeWithText("EMBERFALL GAZETTE", substring = true).assertIsDisplayed()

        compose.onNodeWithTag("page_journal").performClick()
        compose.onNodeWithTag("page_journal").assertIsSelected()
        compose.onNodeWithText("Notebook").assertIsDisplayed()
        compose.onNodeWithText("EMBERFALL GAZETTE", substring = true).assertDoesNotExist()

        compose.onNodeWithTag("page_legacy").performClick()
        compose.onNodeWithTag("page_legacy").assertIsSelected()
        compose.onNodeWithText("Permanent upgrades").assertIsDisplayed()
        val tracks = engine.content.upgrades
        assertEquals("the launch catalogue has eleven legacy tracks", 11, tracks.size)
        tracks.forEach { u ->
            compose.onNodeWithTag("records_list").performScrollToNode(hasText(u.name))
            compose.onNodeWithText(u.name).assertIsDisplayed()
        }
        compose.onNodeWithTag("records_list").performScrollToNode(hasText("Lineages"))
        compose.onNodeWithText("Lineages").assertIsDisplayed()
        compose.onNodeWithText("Experiment Journal").assertDoesNotExist()
        compose.onNodeWithText("Reduced motion").assertDoesNotExist()   // it lives behind the gear now

        // Going back to a segment shows its content again.
        compose.onNodeWithTag("page_gazette").performClick()
        compose.onNodeWithText("EMBERFALL GAZETTE", substring = true).assertIsDisplayed()
    }
}
