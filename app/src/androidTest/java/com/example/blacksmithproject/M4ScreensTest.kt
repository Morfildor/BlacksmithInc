package com.example.blacksmithproject

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.ForgePanel
import com.example.blacksmithproject.ui.ShopPanel
import com.example.blacksmithproject.ui.threatUi
import com.example.blacksmithproject.ui.detail.HeroDetailContent
import com.example.blacksmithproject.ui.detail.heroDetail
import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.CommissionKind
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Want
import com.tinyblacksmith.core.model.WeaponFamilyId
import com.tinyblacksmith.core.persistence.DayCursor
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.shopday.Lines
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The M4 screens over engine states: what core says in `Lines` is what the screen shows, and the actions report what was tapped. */
@RunWith(AndroidJUnit4::class)
class M4ScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val engine get() = ShopDayFixtures.engine

    /** The morning after three played days on seed 42: blades on the shelf, heroes in town. */
    private val morning: GameState by lazy { ShopDayFixtures.run(42, 3).last().state }

    private var forgedFamily: WeaponFamilyId? = null

    /** The whole Shop in a box taller than any screen, so every row is composed. */
    private fun showShop(state: GameState) {
        val shop = engine.shopUi(state)
        compose.setContent {
            BlacksmithProjectTheme {
                Box(Modifier.width(360.dp).requiredHeight(6000.dp)) {
                    ShopPanel(shop, busy = false, reducedMotion = true, onLead = {}, onOpenBlade = {}, onOpenHero = {}, onAnswer = { _, _ -> }, onOpenStorage = {}, onOpenNews = {}, onForgeWant = { forgedFamily = it })
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aStandingWantIsOnTheShopAndOnTheHeroSheet() {
        val hero = morning.aliveHeroes().first()
        // A family nothing on the shelf belongs to, so no listed blade can answer it.
        val family = engine.content.families.first { f -> morning.listedWeapons().none { it.familyId == f.id } }.id
        val wanting = hero.copy(want = Want(family, minPower = 10, budget = 95, sinceDay = morning.day))
        val state = morning.copy(heroes = morning.heroes + (hero.id to wanting))
        val line = Lines.want(wanting, engine.content)!!
        showShop(state)
        compose.onNodeWithText(line).assertIsDisplayed()
        compose.onNodeWithTag("want_answered_${hero.id.value}").assertDoesNotExist()
        compose.onNodeWithTag("forge_want_${hero.id.value}").performClick()
        assertEquals(family, forgedFamily)
    }

    @Test
    fun theShopPlateSaysTheBesiegerAndARequestSaysWhy() {
        val buyer = morning.aliveHeroes().first()
        val asked = Commission(CommissionId("why1"), buyer.id, engine.content.families.first().id, 40, 80, morning.day, morning.day + 3, CommissionStatus.OFFERED, kind = CommissionKind.SIEGE_PREP)
        val state = morning.copy(commissions = mapOf(asked.id to asked))
        showShop(state)
        compose.onNodeWithText(engine.threatUi(state)!!.plate, substring = true).assertIsDisplayed()
        compose.onNodeWithTag("request_why_why1", useUnmergedTree = true).assertTextEquals(Lines.commissionWhy(asked, state)!!)
    }

    /** The Forge over a ViewModel on [state], opened on planning. */
    private fun showForge(state: GameState): GameViewModel {
        val repo = MemoryRepository(SaveCodec.encodeRun(state), SaveCodec.encodeLegacy(state.legacy), state.lastResolution?.let { DayCursor(it.commandId.value, DayCursor.Stage.DONE).encode() })
        lateinit var vm: GameViewModel
        compose.runOnUiThread { vm = GameViewModel(engine, GameSession(engine, repo), QuietSettings(), SavedStateHandle()) }
        compose.waitUntil(10_000) { vm.ui.value is UiState.Playing }
        compose.setContent {
            BlacksmithProjectTheme {
                val ui by vm.ui.collectAsState()
                (ui as? UiState.Playing)?.let { Box(Modifier.size(360.dp, 2400.dp)) { ForgePanel(it, vm, reducedMotion = true, tip = null) } }
            }
        }
        return vm
    }

    @Test
    fun theForgeMarksTheElementsTheBesiegerCaresAbout() {
        val threat = engine.threatUi(morning)!!
        val vm = showForge(morning)
        compose.onNodeWithTag("forge_threat", useUnmergedTree = true).assertTextContains(threat.plate, substring = true)
        // With a family and a core chosen the augment step is the open one: its chips carry the marks.
        compose.runOnUiThread { vm.updateDraft { it.copy(familyId = engine.content.families.first().id, coreId = engine.content.materials(MaterialCategory.CORE).first().id) } }
        compose.waitForIdle()
        for ((element, mark) in threat.marks) {
            compose.onNodeWithTag("forge_mark_${element.name.lowercase()}", useUnmergedTree = true).assertTextContains(mark.label, substring = true)
        }
    }

    @Test
    fun theHeroSheetSaysTheWant() {
        val hero = morning.aliveHeroes().first()
        val wanting = hero.copy(want = Want(morning.listedWeapons().first().familyId, minPower = 10, budget = 95, sinceDay = morning.day))
        val state = morning.copy(heroes = morning.heroes + (hero.id to wanting))
        val detail = engine.heroDetail(state, hero.id)!!
        compose.setContent { BlacksmithProjectTheme { HeroDetailContent(detail, onOpenHero = {}, onOpenItem = {}, onDismiss = {}) } }
        compose.onNodeWithTag("hero_want", useUnmergedTree = true).assertTextEquals(Lines.want(wanting, engine.content)!!)
    }
}
