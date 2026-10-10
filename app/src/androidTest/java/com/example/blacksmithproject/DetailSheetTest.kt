package com.example.blacksmithproject

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.detail.HeroDetail
import com.example.blacksmithproject.ui.detail.HeroDetailSheet
import com.example.blacksmithproject.ui.detail.ItemDetail
import com.example.blacksmithproject.ui.detail.ItemDetailSheet
import com.example.blacksmithproject.ui.detail.StockAction
import com.example.blacksmithproject.ui.detail.heroDetail
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.model.CustomerSnapshot
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Guild
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.HistoryEntry
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.model.WeaponSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The hero and blade sheets as a player meets them: what they say, what they open, and what they refuse to do. */
@RunWith(AndroidJUnit4::class)
class DetailSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val engine = GameEngine()
    private val good = engine.content.affixes.first { it.kind == AffixKind.BENEFICIAL }
    private val flaw = engine.content.affixes.first { it.kind == AffixKind.FLAW }

    private val forged: GameState = engine.handle(engine.newRun(LegacyProfile(), 42L), Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.BALANCED)).stateOrThrow()
    private val mentor: Hero = forged.heroes.values.first()
    private val hero: Hero = forged.heroes.values.elementAt(1).copy(elementTaste = Element.FROST, gold = 77, guildId = "guild_1", mentorName = mentor.fullName, loyalty = engine.config.regularLoyaltyThreshold)
    private val made: Weapon = forged.weapons.values.single()
    /** The same blade sold, carried and storied. */
    private val blade: Weapon = made.copy(
        affixes = listOf(good.id), flaws = listOf(flaw.id), location = WeaponLocation.Owned(hero.id, equipped = true),
        history = made.history + HistoryEntry(1, 2, "SOLD", "Sold to ${hero.fullName} for 40 gold.") + HistoryEntry(1, 3, "VICTORY", "Routed a raider on the north road."),
    )
    private val state: GameState = forged.copy(
        heroes = forged.heroes + (hero.id to hero), weapons = mapOf(blade.id to blade),
        town = forged.town.copy(guilds = listOf(Guild("guild_1", "The Ember Hall", mentor.id, 1))),
    )
    private val atTheCounter = CustomerSnapshot(
        heroId = hero.id, name = hero.fullName, classId = hero.classId, level = hero.level, appearance = "portrait_${hero.classId.value}_0",
        traits = hero.traits, elementTaste = hero.elementTaste, ambition = hero.ambition, gold = 120, guildId = hero.guildId, mentorName = hero.mentorName,
    )

    private var openedHero: HeroId? = null
    private var openedItem: WeaponId? = null
    private val actions = mutableListOf<StockAction>()
    private var fontScale by mutableStateOf(1f)

    private fun show(detail: () -> HeroDetail) = compose.setContent {
        BlacksmithProjectTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                HeroDetailSheet(detail(), onOpenHero = { openedHero = it }, onOpenItem = { openedItem = it }, onDismiss = {})
            }
        }
    }

    private fun show(planning: Boolean, detail: () -> ItemDetail) = compose.setContent {
        BlacksmithProjectTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                ItemDetailSheet(detail(), planning, onOpenHero = { openedHero = it }, onStock = { actions += it }, onDismiss = {})
            }
        }
    }

    /** The first node that says [text] (a name is also said by the records further down) can be scrolled to and is on screen. */
    private fun seen(text: String) = compose.onAllNodesWithText(text, substring = true).onFirst().performScrollTo().assertIsDisplayed()
    private fun top(text: String) = compose.onNodeWithText(text, substring = true).getUnclippedBoundsInRoot().top
    /** Where a line of History stands; the Story above it tells some of the same records. */
    private fun historyTop(text: String) = compose.onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("sheet_history")), useUnmergedTree = true).getUnclippedBoundsInRoot().top

    @Test
    fun heroSheetShowsTasteGuildMentorAndPurse() {
        val detail = engine.heroDetail(state, hero.id)!!
        show { detail }
        seen(hero.fullName)
        seen("Favours frost blades")
        seen("The Ember Hall")
        seen("77 gold")
        seen("A regular of your shop")
        seen(engine.content.heroClass(hero.classId).name)
        compose.onNodeWithText("At the counter").assertDoesNotExist()
        // The mentor and the blade are doors to their own sheets.
        compose.onNode(hasText(mentor.fullName, substring = true) and hasClickAction()).performScrollTo().performClick()
        compose.onNode(hasText(blade.name, substring = true) and hasClickAction()).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(mentor.id to blade.id, openedHero to openedItem) }
        seen("With your shop")
        seen("Recent events")
    }

    @Test
    fun itemSheetShowsAffixDescriptionsAndHistory() {
        val detail = engine.itemDetail(state, blade.id)!!
        show(planning = true) { detail }
        seen(blade.name)
        seen(good.name); seen(good.description)
        seen("Flaws"); seen(flaw.name); seen(flaw.description)
        // Rarity is a word in a labelled line, never the pip or its colour alone.
        seen(blade.rarity.name.lowercase().replaceFirstChar { it.uppercase() })
        seen("Ember Resin")
        seen("History")
        assertTrue("history is newest first", historyTop("Routed a raider") < historyTop("Sold to ${hero.fullName}") && historyTop("Sold to ${hero.fullName}") < historyTop("Forged from Iron"))
        compose.onNode(hasText(hero.fullName, substring = true) and hasClickAction()).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(hero.id, openedHero) }
    }

    @Test
    fun aBladeNoLongerInTheSaveOpensFromItsSnapshot() {
        val pruned = state.copy(weapons = emptyMap())
        val detail = engine.itemDetail(pruned, blade.id, WeaponSnapshot.of(blade))!!
        show(planning = true) { detail }
        seen(blade.name)
        seen("At the counter")
        seen("No longer in the forge's records")
        seen(good.description)
        seen(flaw.description)
        compose.onNodeWithTag("item_price").assertDoesNotExist()

        // The same for a customer who has left the save: their face and what the counter knew.
        val goneHero = engine.heroDetail(state.copy(heroes = state.heroes - hero.id), hero.id, atTheCounter)!!
        assertEquals(hero.fullName, goneHero.name)
        assertEquals("No longer in the town's records", goneHero.now.single().value)
    }

    @Test
    fun snapshotFirstThenNowWhenTheyDiffer() {
        var detail by mutableStateOf(engine.heroDetail(state, hero.id, atTheCounter)!!)
        show { detail }
        seen("At the counter")
        assertTrue("the counter is told before the present", top("At the counter") < top("Now"))
        compose.onNode(hasText("120 gold", substring = true) and hasAnyAncestor(hasTestTag("sheet_counter"))).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("77 gold", substring = true) and hasAnyAncestor(hasTestTag("sheet_now"))).performScrollTo().assertIsDisplayed()
        // What did not change is said once, at the counter.
        compose.onNode(hasText("Favours frost blades") and hasAnyAncestor(hasTestTag("sheet_now"))).assertDoesNotExist()

        // A snapshot that says what the save says: one block, no headings.
        val unchanged = state.copy(heroes = state.heroes + (hero.id to hero.copy(gold = 120, loyalty = 0)), weapons = emptyMap())
        detail = engine.heroDetail(unchanged, hero.id, atTheCounter)!!
        compose.waitForIdle()
        compose.onNodeWithText("At the counter").assertDoesNotExist()
        compose.onNodeWithText("Now").assertDoesNotExist()
        seen("120 gold")
    }

    @Test
    fun stockActionsAreReadOnlyInTheShopDay() {
        val stored = made.copy(location = WeaponLocation.Storage)
        val inStorage = state.copy(weapons = mapOf(stored.id to stored))
        var planning by mutableStateOf(false)
        var detail by mutableStateOf(engine.itemDetail(inStorage, stored.id)!!)
        compose.setContent { BlacksmithProjectTheme { ItemDetailSheet(detail, planning, onOpenHero = {}, onStock = { actions += it }, onDismiss = {}) } }
        seen("In storage, not for sale.")
        listOf("item_price", "item_list", "item_set_price", "item_unlist", "item_salvage", "item_hone", "item_donate").forEach { compose.onNodeWithTag(it).assertDoesNotExist() }

        planning = true
        compose.waitForIdle()
        compose.onNodeWithTag("item_price").performScrollTo().performTextReplacement("55")
        compose.onNodeWithTag("item_list").performScrollTo().performClick()
        compose.onNodeWithTag("item_salvage").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(StockAction.ListAt(55), StockAction.Salvage), actions.toList()) }

        val listed = stored.copy(location = WeaponLocation.Shelf(55))
        detail = engine.itemDetail(state.copy(weapons = mapOf(listed.id to listed)), listed.id)!!
        compose.waitForIdle()
        compose.onNodeWithTag("item_list").assertDoesNotExist()
        compose.onNodeWithTag("item_unlist").performScrollTo().performClick()
        compose.onNodeWithTag("item_set_price").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(StockAction.Unlist, StockAction.SetPrice(55)), actions.drop(2)) }
    }

    /** Accessibility floor: at twice the text size both sheets still scroll to their last line, and every target is 48 dp. */
    @Test
    fun atTwiceTheTextSizeTheSheetsScrollToTheirEndAndEveryTargetIsLargeEnough() {
        fontScale = 2f
        var item by mutableStateOf(false)
        val heroDetail = engine.heroDetail(state, hero.id, atTheCounter)!!
        val stored = made.copy(location = WeaponLocation.Shelf(40), affixes = listOf(good.id), flaws = listOf(flaw.id))
        val itemDetail = engine.itemDetail(state.copy(weapons = mapOf(stored.id to stored)), stored.id)!!
        compose.setContent {
            BlacksmithProjectTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    if (item) ItemDetailSheet(itemDetail, planning = true, onOpenHero = {}, onStock = {}, onDismiss = {})
                    else HeroDetailSheet(heroDetail, onOpenHero = {}, onOpenItem = {}, onDismiss = {})
                }
            }
        }
        fun check(sheet: String) {
            compose.onNodeWithTag("sheet_close").performScrollTo().assertIsDisplayed()
            val min = with(compose.density) { 48.dp.roundToPx() }
            val targets = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(sheet))).fetchSemanticsNodes()
            assertTrue("the sheet has targets to measure", targets.size >= 2)
            targets.forEach { n ->
                val what = n.config.getOrNull(SemanticsProperties.TestTag) ?: n.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                assertTrue("$what is ${n.size} px, under 48 dp ($min px)", n.size.width >= min && n.size.height >= min)
            }
        }
        check("hero_sheet")
        item = true
        compose.waitForIdle()
        check("item_sheet")
    }
}
