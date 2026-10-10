package com.example.blacksmithproject

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertContentDescriptionContains
import com.example.blacksmithproject.ui.CommissionBoard
import com.example.blacksmithproject.ui.board
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.ForgePanel
import com.example.blacksmithproject.ui.RecordsPanel
import com.example.blacksmithproject.ui.ShopPanel
import com.example.blacksmithproject.ui.threatUi
import com.example.blacksmithproject.ui.detail.HeroDetailContent
import com.example.blacksmithproject.ui.detail.ItemDetailContent
import com.example.blacksmithproject.ui.detail.heroDetail
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.crafting.ClueRung
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.CommissionKind
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Journal
import com.tinyblacksmith.core.model.KnowledgeState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.LegendEntry
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

    /** The first morning on seed 42 with blades still on the shelf (a three-day run can sell out) and heroes in town. */
    private val morning: GameState by lazy { ShopDayFixtures.run(42, 30).map { it.state }.first { !it.isEnded && it.listedWeapons().isNotEmpty() } }

    private var forgedFamily: WeaponFamilyId? = null

    /** What is on screen: the Shop first, then the board in its place (content can be set only once). */
    private val shown = mutableStateOf<@Composable () -> Unit>({})

    /** The Shop at a phone's size: what is asserted as displayed is on screen as a player has it, after scrolling the list to it. */
    private fun showShop(state: GameState) {
        val shop = engine.shopUi(state)
        compose.setContent { BlacksmithProjectTheme { shown.value() } }
        shown.value = {
                Box(Modifier.size(360.dp, 640.dp)) {
                    ShopPanel(shop, busy = false, reducedMotion = true, onLead = {}, onOpenBlade = {}, onOpenBoard = {}, onOpenStorage = {}, onOpenNews = {})
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
        // The Shop says only how many; the board has the want under its weapon type, one tap away.
        showShop(state)
        compose.onNodeWithTag("shop_list").performScrollToNode(hasTestTag("shop_board"))
        compose.onNodeWithTag("shop_board_detail", useUnmergedTree = true).assertTextContains("customer want", substring = true)
        compose.onNodeWithText(line).assertDoesNotExist()
        showBoard(state)
        compose.onNodeWithText(line).assertDoesNotExist()
        compose.onNodeWithTag("board_group_${family.value}").performClick()
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
        // The plate is the top of the Shop: on screen without scrolling.
        compose.onNodeWithText(engine.threatUi(state)!!.summary, substring = true).assertIsDisplayed()
        showBoard(state)
        compose.onNodeWithTag("board_commission_why1").performClick()
        compose.onNodeWithTag("request_why_why1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("request_why_why1", useUnmergedTree = true).assertTextEquals(Lines.commissionWhy(asked, state)!!)
    }

    /** The commission board for [state] at a phone's size; "Forge this" on a want is recorded. */
    private fun showBoard(state: GameState) {
        val shop = engine.shopUi(state)
        shown.value = {
            Box(Modifier.size(360.dp, 640.dp)) {
                CommissionBoard(shop.board(), shop.requestSlots, busy = false, chosen = null, onOpenHero = {}, onAnswer = { _, _ -> }, onForgeThis = {}, onForgeWant = { forgedFamily = it })
            }
        }
        compose.waitForIdle()
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
        compose.onNodeWithText(threat.matchup!!, substring = true).assertIsDisplayed()
        // With a weapon and a metal chosen the augment tray is the open one: its tiles carry the marks.
        compose.runOnUiThread { vm.updateDraft { it.copy(familyId = engine.content.families.first().id, coreId = engine.content.materials(MaterialCategory.CORE).first().id) } }
        compose.waitForIdle()
        for ((element, mark) in threat.marks) {
            val augment = engine.content.materials(MaterialCategory.AUGMENT).first { it.element == element }
            compose.onNodeWithTag("forge_option_${augment.id.value}").assertContentDescriptionContains(mark.label, substring = true)
        }
    }

    @Test
    fun theBladeSheetHasItsStoryAndADormantMarker() {
        val blade = morning.listedWeapons().first()
        val buffs = engine.content.affixes.filter { it.kind == AffixKind.BENEFICIAL }
        val legend = blade.copy(affixes = listOf(buffs[0].id), dormantAffixes = listOf(buffs[1].id))
        val state = morning.copy(weapons = morning.weapons + (legend.id to legend))
        val detail = engine.itemDetail(state, legend.id)!!
        compose.setContent { BlacksmithProjectTheme { ItemDetailContent(detail, planning = true, onOpenHero = {}, onStock = {}, onDismiss = {}) } }
        compose.onNodeWithText("Story").assertExists()
        // The same record is also a line of History, so the story's own copy is found under its tag.
        compose.onNode(hasText(Lines.story(legend, state.era).first()) and hasAnyAncestor(hasTestTag("sheet_story")), useUnmergedTree = true).assertExists()
        compose.onNode(hasTestTag("card_dormant") and hasText(Lines.dormant(legend.dormantAffixes, engine.content)!!)).assertExists()
        compose.onNode(hasText("+") and hasText(buffs[0].name)).assertExists()
        compose.onNode(hasText("+") and hasText(buffs[1].name)).assertDoesNotExist()   // asleep: not a buff
    }

    @Test
    fun recordsShowTheLadderTheRecipeAndTheLegendBoard() {
        val def = SignatureCatalog.all.first { it.catalystId != null && it.risk != null }
        val old = LegendEntry(1, "Bronze Axe", "Old Faithful", kills = 4, fame = 3, owners = emptyList())
        val legacy = LegacyProfile(journal = Journal(interactions = mapOf(def.journalKey to KnowledgeState.SIGNATURE_DISCOVERED)), legendBoard = listOf(old))
        val start = engine.newRun(legacy, 42L)
        val repo = MemoryRepository(SaveCodec.encodeRun(start), SaveCodec.encodeLegacy(legacy))
        lateinit var vm: GameViewModel
        compose.runOnUiThread { vm = GameViewModel(engine, GameSession(engine, repo), QuietSettings(), SavedStateHandle()) }
        compose.setContent {
            BlacksmithProjectTheme {
                val ui by vm.ui.collectAsState()
                (ui as? UiState.Playing)?.let { Box(Modifier.size(360.dp, 560.dp)) { RecordsPanel(it, vm) } }
            }
        }
        compose.waitUntil(10_000) { vm.ui.value is UiState.Playing }

        // Legacy: the old entry is told in core's lines and says its make is lost.
        compose.runOnUiThread { vm.selectRecords(RecordsPage.LEGACY) }
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("legend_lost_0"))
        compose.onNodeWithTag("legend_lost_0", useUnmergedTree = true).assertTextContains("Its properties are lost to time.", substring = true)
        compose.onNodeWithText(Lines.legend(old, engine.content, start.era).first()).assertIsDisplayed()

        // Journal: the found signature shows its whole ladder, and its recipe fills the forge.
        compose.runOnUiThread { vm.selectRecords(RecordsPage.JOURNAL) }
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("use_recipe_${def.id}"))
        compose.onNodeWithText("✓ Catalyst: ${com.tinyblacksmith.core.crafting.Journal.clue(def, ClueRung.CATALYST, engine.config)}").assertIsDisplayed()
        compose.onNodeWithTag("use_recipe_${def.id}").performClick()
        compose.waitForIdle()
        val p = vm.ui.value as UiState.Playing
        assertEquals(Dest.FORGE, p.dest)
        assertEquals(SignatureCatalog.recipe(def), Command.Forge(p.draft.mode, p.draft.familyId!!, p.draft.coreId!!, p.draft.augmentId!!, p.draft.catalystId, p.draft.risk, p.draft.technique))
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
