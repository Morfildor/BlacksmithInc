package com.example.blacksmithproject

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.BulkTerms
import com.example.blacksmithproject.ui.ShopPanel
import com.example.blacksmithproject.ui.detail.toCommand
import com.example.blacksmithproject.ui.ShopUi
import com.example.blacksmithproject.ui.StorageList
import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.shopday.Beat
import com.example.blacksmithproject.ui.shopday.toUi
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val engine get() = ShopDayFixtures.engine

/** Every day the fixtures play on a few seeds: real engine states the morning after. */
private fun mornings(): Sequence<ShopDayFixtures.Day> = (42L..47L).asSequence().flatMap { ShopDayFixtures.run(it, 30) }

private val stockRow = SemanticsMatcher("a stock row") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("stock_") == true }

/** The Shop destination over states the real engine produced: its order, its lead and its demand block. */
@RunWith(AndroidJUnit4::class)
class ShopPanelTest {
    @get:Rule
    val compose = createComposeRule()

    private var shop by mutableStateOf<ShopUi?>(null)

    /** The whole list in a box taller than any screen, so every row is composed and has a place. */
    private fun show(state: GameState) {
        shop = engine.shopUi(state)
        compose.setContent {
            BlacksmithProjectTheme {
                Box(Modifier.width(360.dp).requiredHeight(6000.dp)) {
                    shop?.let { ShopPanel(it, busy = false, reducedMotion = true, onLead = {}, onOpenBlade = {}, onOpenBoard = {}, onOpenStorage = {}, onOpenNews = {}) }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    /** A new game (A01): the Shop opens on "Forge your first blade", its reason and the button to the forge, above everything else to do. */
    @Test
    fun dayOneLeadsWithForgeYourFirstBlade() {
        val fresh = engine.newRun(LegacyProfile(), 42L)
        show(fresh)
        compose.onNodeWithTag("shop_lead", useUnmergedTree = true).assertTextEquals("Forge your first blade")
        compose.onNodeWithTag("shop_lead_reason", useUnmergedTree = true).assertTextEquals("${fresh.aliveHeroes().size} heroes in Emberfall and nothing on the shelf.")
        compose.onNodeWithTag("shop_lead_action", useUnmergedTree = true).assertTextEquals("Go to the forge  ›")
        compose.onNodeWithTag("shop_siege", useUnmergedTree = true).assertDoesNotExist()
        assertTrue("the lead is the first thing under the counter", top("shop_counter") < top("shop_lead") && top("shop_lead") < top("shop_demand"))
        compose.onNodeWithTag("shop_board_detail", useUnmergedTree = true).assertTextEquals("No commissions · no customer wants")
        compose.onNodeWithTag("shop_yesterday", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theShelfSitsAboveTheDoorsAndTheReportsAndYesterdayIsFolded() {
        val day = mornings().firstOrNull { d ->
            d.state.commissions.values.any { it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED } && d.state.listedWeapons().isNotEmpty() && d.resolution.visits.isNotEmpty()
        }
        assertNotNull("no morning with a request, a stocked shelf and yesterday's customers", day)
        show(day!!.state)
        val blades = compose.onAllNodes(stockRow, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.top }
        val order = listOf("shop_counter", "shop_lead", "shop_shelf", "shop_board", "shop_storage", "shop_demand", "shop_yesterday").map { top(it).value }
        assertEquals("counter, lead, shelf, the board, storage, who is buying, yesterday", order.sorted(), order)
        assertTrue("the shelf rows sit between their heading and the board", with(compose.density) { top("shop_shelf").toPx() < blades.min() && blades.max() < top("shop_board").toPx() })
        // A shelf row still names the blade and its price for TalkBack, though it shows less.
        val blade = day.state.listedWeapons().first()
        val spoken = compose.onNodeWithTag("stock_${blade.id.value}").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
        assertTrue(spoken, blade.name in spoken && "${blade.listedPrice} gold" in spoken)
        // Yesterday is one row until asked: the Gazette button is behind "Show".
        compose.onNodeWithTag("shop_news").assertDoesNotExist()
        compose.onNodeWithTag("shop_yesterday_toggle").performClick()
        // It opens under the row, which may be below the screen's edge: that it is there is the point.
        compose.onNodeWithTag("shop_news").assertExists()
        compose.onNodeWithTag("shop_yesterday_toggle").performClick()
        compose.onNodeWithTag("shop_news").assertDoesNotExist()
    }

    /** The day of a siege: one row says so above the lead, with the line every destination shows. */
    @Test
    fun theSiegeDayIsSaidAboveTheLead() {
        val fresh = engine.newRun(LegacyProfile(), 42L)
        val ui = engine.shopUi(fresh)
        assertNotNull("a new game has a besieger", ui.threat)
        show(fresh)
        compose.runOnUiThread { shop = ui.copy(threat = ui.threat!!.copy(today = true)) }
        compose.waitForIdle()
        compose.onNodeWithTag("shop_siege").assertTextContains("Siege today, after today's trading")
        assertTrue("the siege row is above the lead", top("shop_counter") < top("shop_siege") && top("shop_siege") < top("shop_lead"))
    }

    @Test
    fun theLeadIsTheSameAsTheTomorrowCard() {
        // One morning per kind of lead the fixtures reach: the Tomorrow card of the evening before, against the Shop.
        val byKind = mornings().filter { it.script.lead != null }.distinctBy { it.script.lead!!.kind }.toList()
        assertTrue("the fixtures reach several kinds of lead: ${byKind.map { it.script.lead!!.kind }}", byKind.size >= 3)
        show(byKind.first().state)
        for (day in byKind) {
            val tomorrow = day.script.toUi(day.state, engine.content, engine.config).beats.last() as Beat.Tomorrow
            compose.runOnUiThread { shop = engine.shopUi(day.state) }
            compose.waitForIdle()
            compose.onNodeWithTag("shop_lead", useUnmergedTree = true).assertTextEquals(tomorrow.action)
            // The same sentence a morning later: the day the card called "today" is the Shop's "yesterday" (`Lines.lead`).
            if (tomorrow.reason != null) compose.onNodeWithTag("shop_lead_reason", useUnmergedTree = true).assertTextEquals(tomorrow.reason!!.replace("the price today", "the price yesterday"))
            else compose.onNodeWithTag("shop_lead_reason", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun demandBlockCountsUnarmedWornAndPurseBands() {
        // A morning with an unarmed hero, a worn blade in someone's hand and two prices on the shelf.
        val day = mornings().firstOrNull { d ->
            val s = d.state
            val carried = s.aliveHeroes().map { s.equippedWeapon(it.id) }
            carried.any { it == null } && carried.any { it != null && it.condition < engine.config.wornConditionThreshold } && s.listedWeapons().mapNotNull { it.listedPrice }.distinct().size >= 2
        }
        assertNotNull("no morning with an unarmed hero, a worn blade and two prices", day)
        val s = day!!.state
        show(s)
        // Counted here from the save, not through the summary the panel was built from.
        val heroes = s.aliveHeroes()
        val prices = s.listedWeapons().mapNotNull { it.listedPrice }.sorted()
        fun afford(price: Int) = heroes.count { it.gold + Market.tradeInCredit(s.equippedWeapon(it.id), engine.config) >= price }
        val cheapest = prices.first()
        val middle = prices[(prices.size - 1) / 2]
        fun row(label: String, value: String) =
            compose.onNode(hasText(label) and hasText(value)).assertExists("\"$label\" should read $value")
        row("Heroes in town", "${heroes.size}")
        row("Carry no blade", "${heroes.count { s.equippedWeapon(it.id) == null }}")
        row("Carry a worn blade", "${heroes.count { h -> s.equippedWeapon(h.id)?.let { it.condition < engine.config.wornConditionThreshold } == true }}")
        row("Can afford the cheapest blade ($cheapest gold)", "${afford(cheapest)} of ${heroes.size}")
        if (middle != cheapest) row("Can afford the middle blade ($middle gold)", "${afford(middle)} of ${heroes.size}")
    }
}

/** Storage is a lazy, keyed list: a storeroom of hundreds composes only the rows in view. */
@RunWith(AndroidJUnit4::class)
class StorageSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun twoHundredFiftyWeaponsComposeOnlyVisibleRows() {
        val forged = ShopDayFixtures.run(42, 3).last().state
        val blade = forged.weapons.values.first()
        val crowded = forged.copy(weapons = (1..250).associate { i -> WeaponId("t$i").let { it to blade.copy(id = it, name = "Blade $i", location = WeaponLocation.Storage) } })
        val storage = engine.shopUi(crowded).storage
        assertEquals(250, storage.size)
        compose.setContent {
            BlacksmithProjectTheme { Box(Modifier.size(360.dp, 560.dp)) { StorageList(storage, shelfFree = 8, busy = false, onOpenBlade = {}, onList = { _, _ -> }) } }
        }
        compose.waitForIdle()
        val composed = compose.onAllNodes(stockRow, useUnmergedTree = true).fetchSemanticsNodes().size
        assertTrue("$composed of 250 rows composed", composed in 1..20)
        compose.onNodeWithTag("stock_t1", useUnmergedTree = true).assertIsDisplayed()
        // The last blade is reachable, and reaching it does not compose the list.
        compose.onNodeWithTag("storage_list").performScrollToIndex(250)
        compose.onNodeWithTag("stock_t250", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(compose.onAllNodes(stockRow, useUnmergedTree = true).fetchSemanticsNodes().size <= 20)
    }

    /** Select three blades, Salvage: one question, and only its answer hands over the three, as one Salvage command each. */
    @Test
    fun bulkSalvageAsksOnceAndIssuesOneCommandPerBlade() {
        val forged = ShopDayFixtures.run(42, 3).last().state
        val blade = forged.weapons.values.first()
        val stored = forged.copy(weapons = (1..5).associate { i -> WeaponId("t$i").let { it to blade.copy(id = it, name = "Blade $i", location = WeaponLocation.Storage) } })
        val storage = engine.shopUi(stored).storage
        val issued = mutableListOf<List<Command>>()
        val opened = mutableListOf<WeaponId>()
        compose.setContent {
            BlacksmithProjectTheme {
                Box(Modifier.size(360.dp, 640.dp)) {
                    StorageList(
                        storage, shelfFree = 8, busy = false, onOpenBlade = { opened += it }, onList = { _, _ -> },
                        terms = BulkTerms(salvageEnergy = 1, energy = 10, overworkLeft = 4, armoryRoom = 30),
                        onBulk = { action, ids -> issued += ids.map { action.toCommand(it) } },
                    )
                }
            }
        }
        compose.onNodeWithTag("storage_bulk_salvage").assertDoesNotExist()
        compose.onNodeWithTag("storage_select").performClick()
        compose.onNodeWithTag("storage_bulk_salvage").assertIsNotEnabled()
        for (id in listOf("t1", "t2", "t3")) compose.onNodeWithTag("stock_$id").performClick()
        assertTrue("choosing a blade does not open it", opened.isEmpty())
        compose.onNodeWithTag("stock_t2").assertIsOn()
        compose.onNodeWithTag("stock_t4").assertIsOff()
        compose.onNodeWithTag("storage_bulk_salvage").assertTextEquals("Salvage 3").performClick()
        assertTrue("nothing is issued before the answer", issued.isEmpty())
        compose.onNodeWithText("Salvage 3 blades?").assertIsDisplayed()
        compose.onNodeWithTag("storage_bulk_confirm").performClick()
        assertEquals(listOf(listOf("t1", "t2", "t3").map { Command.Salvage(WeaponId(it)) }), issued)
        compose.onNodeWithText("Salvage 3 blades?").assertDoesNotExist()
        // Asked once: nothing is left chosen, so the bar cannot fire again by itself.
        compose.onNodeWithTag("storage_bulk_salvage").assertIsNotEnabled()

        // "Select all shown" then the watch: the same single question.
        compose.onNodeWithTag("storage_select_all").performClick()
        compose.onNodeWithTag("storage_bulk_donate").assertTextEquals("Arm the watch 5").performClick()
        compose.onNodeWithText("Give 5 blades to the town watch?").assertIsDisplayed()
        compose.onNodeWithTag("storage_bulk_confirm").performClick()
        assertEquals((1..5).map { Command.DonateWeapon(WeaponId("t$it")) }, issued.last())
        assertEquals(2, issued.size)
    }
}
