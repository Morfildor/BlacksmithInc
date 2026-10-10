package com.example.blacksmithproject

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.ForgeResultCard
import com.example.blacksmithproject.ui.ForgedForUi
import com.example.blacksmithproject.ui.detail.ItemDetail
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.forgedFor
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.shopday.Demand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The forge result as a player meets it: a price chosen on the card, who could pay it, a full shelf, and a blade forged for a request. */
@RunWith(AndroidJUnit4::class)
class ForgeResultCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val engine = GameEngine()
    private val fresh: GameState = engine.newRun(LegacyProfile(), 42L)
    /** Purses set by hand, 20 gold apart, so every step of the price moves the count. */
    private val forged: GameState = engine.handle(fresh, Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.BALANCED)).stateOrThrow()
        .let { s -> s.copy(heroes = s.aliveHeroes().withIndex().associate { (i, h) -> h.id to h.copy(gold = 20 * i) }) }
    private val blade: Weapon = forged.storedWeapons().single()
    private val suggested = engine.suggestedPrice(blade)
    private val funds = Demand.funds(forged, engine.content, engine.config)

    private val listed = mutableListOf<Int>()
    private var stored = 0
    private var size by mutableStateOf(DpSize(411.dp, 731.dp))
    private var font by mutableStateOf(1f)

    private fun afford(price: Int) = "${funds.count { it >= price }} of ${funds.size} heroes in town can afford this price."

    private fun show(detail: ItemDetail, request: ForgedForUi? = null) = compose.setContent {
        BlacksmithProjectTheme {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size) then DeviceConfigurationOverride.FontScale(font)) {
                Box(Modifier.fillMaxSize()) {
                    ForgeResultCard(detail, "Sword of Iron and Ember Resin.", request, enabled = true, onStore = { stored++ }, onList = { listed += it }, modifier = Modifier.heightIn(max = size.height * 0.92f))
                }
            }
        }
    }

    @Test
    fun thePriceIsChosenOnTheCardAndTheCountFollowsIt() {
        show(engine.itemDetail(forged, blade.id)!!)
        // It opens on the suggested price, named apart from the player's own.
        compose.onNodeWithTag("reveal_price").assertTextContains("$suggested")
        compose.onNodeWithTag("reveal_suggested").assertTextEquals("Suggested price $suggested gold. Your price matches it.")
        compose.onNodeWithTag("reveal_afford").assertTextEquals(afford(suggested))
        compose.onNodeWithText("List at $suggested").assertIsDisplayed()

        // The minus button, twice: the field, the button and the count all move.
        repeat(2) { compose.onNodeWithContentDescription("Lower price by 10").performClick() }
        val lower = suggested - 20
        compose.onNodeWithTag("reveal_price").assertTextContains("$lower")
        compose.onNodeWithTag("reveal_suggested").assertTextEquals("Suggested price $suggested gold. Your price is 20 below it.")
        compose.onNodeWithTag("reveal_afford").assertTextEquals(afford(lower))
        assertTrue("the scenario: the count moves between the two prices", afford(lower) != afford(suggested))
        compose.onNodeWithContentDescription("Raise price by 10").performClick()
        compose.onNodeWithText("List at ${lower + 10}").assertIsDisplayed()

        // A typed price nobody can pay says so before anything is listed.
        compose.onNodeWithTag("reveal_price").performTextReplacement("5000")
        compose.onNodeWithTag("reveal_afford").assertTextEquals("0 of ${funds.size} heroes in town can afford this price.")
        compose.onNodeWithText("Able to pay is not a sale", substring = true).assertIsDisplayed()

        // An empty field lists nothing; a typed price is the one that is listed.
        compose.onNodeWithTag("reveal_price").performTextReplacement("")
        compose.onNodeWithTag("reveal_list").assertIsNotEnabled()
        compose.onNodeWithTag("reveal_afford").assertDoesNotExist()
        compose.onNodeWithTag("reveal_price").performTextReplacement("45")
        compose.onNodeWithTag("reveal_afford").assertTextEquals(afford(45))
        compose.onNodeWithTag("reveal_list").assertIsEnabled().performClick()
        compose.onNodeWithTag("reveal_store").performClick()
        compose.runOnIdle { assertEquals(listOf(45) to 1, listed.toList() to stored) }
    }

    @Test
    fun aFullShelfSaysWhyListingIsUnavailableAndLeavesStore() {
        val slots = engine.shelfSlots(forged)
        val full = forged.copy(weapons = forged.weapons + (1..slots).associate { WeaponId("full$it") to blade.copy(id = WeaponId("full$it"), location = WeaponLocation.Shelf(60)) })
        show(engine.itemDetail(full, blade.id)!!)
        compose.onNodeWithTag("reveal_shelf_full").assertIsDisplayed().assertTextContains("The shelf is full ($slots of $slots).", substring = true)
        compose.onNodeWithTag("reveal_list").assertIsNotEnabled().assertContentDescriptionContains("unavailable: The shelf is full", substring = true)
        compose.onNodeWithTag("reveal_list").performClick()
        compose.onNodeWithTag("reveal_store").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(emptyList<Int>() to 1, listed.toList() to stored) }
    }

    @Test
    fun aBladeForgedForARequestSaysWhatEndDayWillDo() {
        val accepted = Commission(CommissionId("c1"), forged.aliveHeroes().first().id, blade.familyId, 1, 80, forged.day, forged.day + 3, CommissionStatus.ACCEPTED)
        val state = forged.copy(commissions = mapOf(accepted.id to accepted))
        show(engine.itemDetail(state, blade.id)!!, engine.forgedFor(state, blade.id, accepted.id))
        fun inRequest(text: String) = compose.onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag("reveal_request"))).assertIsDisplayed()
        inRequest("For ${forged.aliveHeroes().first().fullName}")
        inRequest("This blade fits the request")
        inRequest("Ready: ${blade.name} will be handed over at End Day.")
        inRequest("None is set aside.")
        compose.onNodeWithText("reserved", substring = true, ignoreCase = true).assertDoesNotExist()
        // The request is told before the blade, and both ways on are still there.
        assertTrue(compose.onNodeWithTag("reveal_request").getUnclippedBoundsInRoot().top < compose.onNodeWithText(blade.name).getUnclippedBoundsInRoot().top)
        compose.onNodeWithTag("reveal_store").assertIsDisplayed()
        compose.onNodeWithTag("reveal_list").assertIsDisplayed()
    }

    /**
     * The layout floor of the card: on a normal phone, at 360 x 640 dp and at 320 dp wide, with text at 1.0, 1.3 and 2.0,
     * nothing reaches outside the screen, and the price, the count and both buttons are on screen without a
     * scroll up to 1.3 (at twice the size they are reached by scrolling the footer).
     */
    @Test
    fun thePriceAndBothButtonsAreOnScreenAtEverySize() {
        show(engine.itemDetail(forged, blade.id)!!)
        for (s in listOf(DpSize(411.dp, 731.dp), DpSize(360.dp, 640.dp), DpSize(320.dp, 569.dp))) for (f in listOf(1f, 1.3f, 2f)) {
            compose.runOnUiThread { size = s; font = f }
            compose.waitForIdle()
            val where = "${s.width} x ${s.height} at font $f"
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            for (tag in listOf("reveal_price", "reveal_afford", "reveal_store", "reveal_list")) {
                val node = compose.onNodeWithTag(tag)
                if (f > 1.3f) node.performScrollTo()
                node.assertIsDisplayed()
                val b = node.fetchSemanticsNode().boundsInRoot
                assertTrue("$where: $tag spans ${b.left}..${b.right} of ${root.width}, ${b.top}..${b.bottom} of ${root.height}", b.left >= -0.5f && b.right <= root.width + 0.5f && b.bottom <= root.height + 0.5f)
            }
            // The blade itself is still told above the footer.
            runCatching { compose.onNode(hasText(blade.name)).performScrollTo().assertIsDisplayed() }.onFailure { throw AssertionError("$where: the blade's name is not on screen", it) }
        }
    }
}
