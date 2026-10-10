package com.example.blacksmithproject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.Tips
import com.example.blacksmithproject.ui.shopday.Beat
import com.example.blacksmithproject.ui.shopday.ReplayOverlay
import com.example.blacksmithproject.ui.shopday.ShopDayScreen
import com.example.blacksmithproject.data.ShopDaySpeed
import com.example.blacksmithproject.ui.shopday.ShopDayUiModel
import com.example.blacksmithproject.ui.shopday.toUi
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.model.CombatReplay
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.ReplayKind
import com.tinyblacksmith.core.model.VisitKind
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponSnapshot
import com.tinyblacksmith.core.shopday.AftermathCard
import com.tinyblacksmith.core.shopday.AftermathKind
import com.tinyblacksmith.core.shopday.Ending
import com.tinyblacksmith.core.shopday.QuietDay
import com.tinyblacksmith.core.shopday.QuietKind
import com.tinyblacksmith.core.shopday.ShopDayScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shop-day screen over days the real engine resolved (`ShopDayFixtures`); the two cases the engine all but never
 * produces in a short run (nobody at all, a merchant resale) are a real day's script with that one part replaced.
 */
@RunWith(AndroidJUnit4::class)
class ShopDayScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var model by mutableStateOf<ShopDayUiModel?>(null)
    private var resolutionReplays: List<CombatReplay> = emptyList()
    private var position by mutableIntStateOf(0)
    private var speed by mutableStateOf(ShopDaySpeed.TAP)
    private var reduced by mutableStateOf(false)
    private var paused by mutableStateOf(false)
    private var coach by mutableStateOf<String?>(null)
    private var replay by mutableStateOf<CombatReplay?>(null)
    private var nexts = 0
    private var closes = 0
    private var skips = 0
    private var watched: MutableList<String?> = mutableListOf()
    private var openedHero: HeroId? = null
    private var openedBlade: WeaponId? = null

    private fun day(case: String): ShopDayFixtures.Day = assertNotNull("the engine produced no '$case' day", ShopDayFixtures.find(case)).let { ShopDayFixtures.find(case)!! }
    private fun ShopDayFixtures.Day.ui(script: ShopDayScript = this.script): ShopDayUiModel = script.toUi(state, engine.content, engine.config)
    private fun ShopDayUiModel.first(match: (Beat) -> Boolean): Int = beats.indexOfFirst(match).also { assertTrue("no such beat in $beats", it >= 0) }

    /** Hosts the screen the way the session will: position in, events out. */
    private fun show(m: ShopDayUiModel, at: Int = 0) {
        model = m; position = at
        compose.setContent {
            BlacksmithProjectTheme {
                val shown = model!!
                ShopDayScreen(
                    model = shown, position = position, speed = speed, reducedMotion = reduced, paused = paused,
                    onNext = { nexts++; position = (position + 1).coerceAtMost(shown.beats.lastIndex) },
                    onBack = { position = (position - 1).coerceAtLeast(0) },
                    onSkipDay = { skips++; position = shown.endingIndex },
                    onSpeedChange = { speed = it },
                    onOpenHero = { id, _ -> openedHero = id },
                    onOpenBlade = { id, _ -> openedBlade = id },
                    onChooseBlessing = {},
                    onOpenGazette = {},
                    onWatchFight = { id -> watched += id; replay = resolutionReplays.firstOrNull { if (id == null) it.kind == ReplayKind.SIEGE else it.eventId == id } },
                    onClose = { closes++ },
                    coach = coach,
                )
                replay?.let { ReplayOverlay(it, onClose = { replay = null }) }
            }
        }
        compose.waitForIdle()
    }

    private fun text(value: String) = compose.onNodeWithText(value, useUnmergedTree = true)

    /** The strip holds the playback chip and the skip: a tap that misses them is not the screen's "tap anywhere". */
    @Test
    fun aTapOnTheStripBesideItsControlsDoesNotAdvance() {
        val m = day("purchase").ui()
        show(m, m.first { it is Beat.Visit })
        compose.onNodeWithTag("shopday_progress", useUnmergedTree = true).performClick()
        assertEquals("the strip is not Next", 0, nexts)
        compose.onNodeWithTag("shopday_speed").assertTextEquals("Manual")
        compose.onNodeWithTag("shopday_skip").assertTextEquals("Skip to evening")
        compose.onNodeWithTag("shopday_card").performClick()
        assertEquals("the card still is", 1, nexts)
    }

    /** The first-run line sits above the controls while the host passes it, and a tap on it is a tap anywhere: Next. */
    @Test
    fun theCoachLineShowsWhilePassedAndATapOnItIsNext() {
        val m = day("purchase").ui()
        coach = Tips.COUNTER.body
        show(m, m.first { it is Beat.Visit })
        compose.onNodeWithTag("shopday_coach").assertIsDisplayed().assertTextEquals(coach!!)
        assertTrue("above the controls", compose.onNodeWithTag("shopday_coach").getUnclippedBoundsInRoot().bottom <= compose.onNodeWithTag("shopday_next").getUnclippedBoundsInRoot().top)
        compose.onNodeWithTag("shopday_coach").performClick()
        assertEquals(1, nexts)
        coach = null
        compose.waitForIdle()
        compose.onNodeWithTag("shopday_coach").assertDoesNotExist()
        compose.onNodeWithTag("shopday_next").assertIsDisplayed()
    }

    @Test
    fun purchaseShowsBuyerReasonAndSeparateReceiptRows() {
        val day = day("tradein")
        val m = day.ui()
        val at = m.first { it is Beat.Visit && it.visit.receipt.any { r -> r.label.startsWith("Trade-in") } }
        val visit = (m.beats[at] as Beat.Visit).visit
        show(m, at)
        compose.onNodeWithTag("shopday_plate", useUnmergedTree = true).assertTextEquals(visit.face.name)   // who
        compose.onNodeWithTag("shopday_outcome_chip", useUnmergedTree = true).assertTextEquals("SOLD")
        compose.onNodeWithTag("shopday_outcome", useUnmergedTree = true).assertTextEquals(visit.outcome).assertIsDisplayed()
        compose.onNodeWithTag("shopday_decision", useUnmergedTree = true).assertTextEquals(visit.decision!!).assertIsDisplayed()   // why
        // The money is rows of label and value, each its own pair of nodes; no row is folded into a sentence.
        assertTrue("listed, trade-in and the till: ${visit.receipt}", visit.receipt.size >= 3)
        assertEquals(listOf("Listed price", "Coin to the till"), listOf(visit.receipt.first().label, visit.receipt.last().label))
        for (row in visit.receipt) {
            text(row.label).performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText(row.value, useUnmergedTree = true).fetchSemanticsNodes().let { assertTrue("${row.label} has its value ${row.value}", it.isNotEmpty()) }
        }
        val sale = day.script.featured.first { it.seq == visit.seq }.sale!!
        text("${sale.listedPrice} gold").assertExists()
        text("−${sale.tradeInCredit} gold").assertExists()
        // The sale's band carries the coin it brought, and "Earned today" shows the day's sum before and after it.
        compose.onNodeWithTag("shopday_coin", useUnmergedTree = true).assertTextEquals("+${sale.cashPaid + sale.saleBonus + sale.stipend} gold").performScrollTo().assertIsDisplayed()
        assertEquals(visit.earnedBefore + visit.coin, visit.earnedAfter)
        compose.onNodeWithTag("shopday_earned").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("${visit.earnedAfter} gold") and hasAnyAncestor(hasTestTag("shopday_earned")), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("${visit.earnedBefore} → ") and hasAnyAncestor(hasTestTag("shopday_earned")), useUnmergedTree = true).assertExists()
    }

    /** A day when heroes fought and no card tells it: one summary beyond the door, with the way to the Gazette. */
    @Test
    fun fightsWithNoCardAreSummedUpBeyondTheDoor() {
        val day = ShopDayFixtures.run(42, 1, forge = false).single()
        val m = day.ui()
        val at = m.first { it is Beat.Aftermath }
        val card = (m.beats[at] as Beat.Aftermath).card
        assertEquals(AftermathKind.FIELD_SUMMARY, card.kind)
        show(m, at)
        compose.onNodeWithTag("shopday_aftermath_kind", useUnmergedTree = true).assertTextEquals("Out in the field").assertIsDisplayed()
        compose.onNodeWithTag("shopday_aftermath_text", useUnmergedTree = true).assertTextEquals(card.text).assertIsDisplayed()
        assertTrue(card.text, Regex("^\\d+ hero(es)? went out").containsMatchIn(card.text))
        compose.onNodeWithTag("shopday_gazette").assertIsDisplayed()
        compose.onNodeWithTag("shopday_watch").assertDoesNotExist()
        // The evening card names the day just watched on the strip and tomorrow once on the banner and once on its button.
        compose.onNodeWithTag("shopday_next").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("shopday_progress").assertTextEquals("Evening")
        compose.onNodeWithTag("shopday_plate", useUnmergedTree = true).assertTextEquals("Tomorrow: day ${day.state.day}")
        text("Begin day ${day.state.day}").assertIsDisplayed()
    }

    /** The opening card says how many came and how many are shown; the strip counts the shown; nothing repeats the strip. */
    @Test
    fun theOpeningCardCountsVisitorsAndThoseShown() {
        val day = day("busy")
        val m = day.ui()
        val open = m.beats.first() as Beat.Open
        show(m)
        compose.onNodeWithTag("shopday_progress").assertTextEquals("The shop opens")
        compose.onAllNodesWithText("The shop opens", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("shopday_open_title", useUnmergedTree = true).assertTextEquals("${day.resolution.visits.size} visitors today")
        compose.onNodeWithTag("shopday_open_shown", useUnmergedTree = true).assertTextEquals("3 are shown at the counter; the other ${open.visitors - 3} are summed up after.")
        compose.onNodeWithTag("shopday_plate", useUnmergedTree = true).assertTextEquals("${m.shelf.size} blades on the shelf")
        compose.onNodeWithTag("shopday_next").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("shopday_progress").assertTextEquals("Counter · 1 of 3")
    }

    @Test
    fun refusalShowsTheTypedReasonWithItsNumbers() {
        val m = day("refusal").ui()
        val at = m.first { it is Beat.Visit && !it.visit.sold }
        val visit = (m.beats[at] as Beat.Visit).visit
        show(m, at)
        compose.onNodeWithTag("shopday_plate", useUnmergedTree = true).assertTextEquals(visit.face.name)
        compose.onNodeWithTag("shopday_outcome_chip", useUnmergedTree = true).assertTextEquals("NO SALE")
        compose.onNodeWithTag("shopday_outcome", useUnmergedTree = true).assertTextEquals(visit.outcome).assertIsDisplayed()
        assertTrue("the reason carries its recorded numbers: ${visit.decision}", Regex("\\d+ gold").containsMatchIn(visit.decision!!))
        compose.onNodeWithTag("shopday_decision", useUnmergedTree = true).assertTextEquals(visit.decision!!).assertIsDisplayed()
        compose.onNodeWithTag("shopday_receipt").assertDoesNotExist()   // no coin changed hands, so no receipt
        compose.onNodeWithTag("shopday_coin", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("shopday_earned").assertDoesNotExist()
    }

    @Test
    fun noVisitorsIsOneCardAndOneTap() {
        val day = day("first")
        val m = day.ui(day.script.copy(quiet = QuietDay(QuietKind.NO_VISITORS), featured = emptyList(), tally = emptyList(), ledger = null, aftermath = emptyList(), ending = Ending.TOMORROW))
        assertEquals(listOf("Quiet", "Tomorrow"), m.beats.map { it::class.simpleName })
        show(m)
        text("Nobody came to the shop today.").assertIsDisplayed()
        compose.onNodeWithTag("shopday_card").performTouchInput { click() }   // a tap anywhere
        compose.waitForIdle()
        assertEquals(1, nexts)
        assertEquals(1, position)
        compose.onNodeWithTag("shopday_lead", useUnmergedTree = true).assertIsDisplayed()
        // The card that ends the day does not take the stray tap: only its button begins tomorrow.
        compose.onNodeWithTag("shopday_card").performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(1, nexts)
        assertEquals(0, closes)
    }

    @Test
    fun emptyShelfListsEachNameOnce() {
        val m = day("empty").ui()
        val quiet = m.beats.first() as Beat.Quiet
        assertEquals(QuietKind.EMPTY_SHELF, quiet.kind)
        assertTrue(quiet.faces.isNotEmpty())
        assertEquals("nobody is listed twice", quiet.faces.map { it.name }.distinct(), quiet.faces.map { it.name })
        show(m)
        compose.onNodeWithTag("shopday_quiet", useUnmergedTree = true).assertTextEquals(quiet.text).assertIsDisplayed()
        for (face in quiet.faces) compose.onAllNodesWithText(face.name, useUnmergedTree = true).assertCountEquals(1)
        assertEquals("one card for the whole counter", 1, m.beats.count { it is Beat.Quiet || it is Beat.Open || it is Beat.Visit || it is Beat.Tally || it is Beat.Close })
    }

    @Test
    fun tenVisitorsFeatureThreeAndTheTallyCoversTheRest() {
        val day = day("busy")
        val m = day.ui()
        assertTrue(day.resolution.visits.size >= 10)
        assertEquals(3, m.beats.count { it is Beat.Visit })
        val at = m.first { it is Beat.Tally }
        val tally = m.beats[at] as Beat.Tally
        assertEquals(day.resolution.visits.size - 3, tally.groups.sumOf { it.faces.size })
        show(m, at)
        compose.onNodeWithTag("shopday_tally_title", useUnmergedTree = true).assertTextEquals("${day.resolution.visits.size - 3} more came by").assertIsDisplayed()
        for (group in tally.groups) {
            text(group.line).performScrollTo().assertIsDisplayed()
            for (face in group.faces) compose.onAllNodesWithText(face.name, useUnmergedTree = true).assertCountEquals(1)
        }
        // A name in the tally opens that hero.
        val someone = tally.groups.first().faces.first()
        text(someone.name).performScrollTo().performClick()
        assertEquals(someone.heroId, openedHero)
    }

    @Test
    fun backStepsABeatAndNeverAcknowledges() {
        val m = day("purchase").ui()
        show(m, 2)
        compose.onNodeWithTag("shopday_back").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(1, position)
        compose.onNodeWithTag("shopday_back").performClick()
        compose.waitForIdle()
        assertEquals(0, position)
        compose.onNodeWithTag("shopday_back").assertIsNotEnabled()   // the first beat has nowhere to go back to
        // From the last card too: Back is the previous card, never "Begin day".
        position = m.beats.lastIndex
        compose.waitForIdle()
        compose.onNodeWithTag("shopday_back").performClick()
        compose.waitForIdle()
        assertEquals(m.beats.lastIndex - 1, position)
        assertEquals(0, closes)
        assertEquals(0, nexts)
    }

    @Test
    fun nothingAdvancesByItselfAtTheDefaultSpeed() {
        val m = day("purchase").ui()
        assertEquals(ShopDaySpeed.TAP, speed)
        show(m)
        compose.mainClock.autoAdvance = false
        for (at in 0 until m.endingIndex) {
            position = at
            compose.mainClock.advanceTimeBy(7_000)
            assertEquals("beat $at stays", at, position)
        }
        assertEquals(0, nexts)
    }

    @Test
    fun reducedMotionNeverAutoAdvances() {
        val m = day("purchase").ui()
        speed = ShopDaySpeed.X2
        reduced = true
        show(m)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(7_000)
        assertEquals(0, nexts)
        assertEquals(0, position)
        // The same speed without reduced motion does move on, so the test above is not passing for lack of a timer.
        reduced = false
        compose.mainClock.advanceTimeBy(m.beats[0].millis / 2 + 500L)
        assertEquals(1, nexts)
    }

    @Test
    fun autoAdvancePausesWhileASheetIsOpen() {
        val m = day("purchase").ui()
        speed = ShopDaySpeed.X1
        paused = true
        show(m)
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(7_000)
        assertEquals(0, nexts)
        paused = false
        compose.mainClock.advanceTimeBy(m.beats[0].millis - 300L)
        assertEquals("the beat waits its whole length again after the sheet closes", 0, nexts)
        compose.mainClock.advanceTimeBy(600)
        assertEquals(1, nexts)
        assertEquals(1, position)
    }

    @Test
    fun outcomeIsVisibleOnTheFirstFrameOfABeat() {
        val m = day("purchase").ui()
        val at = m.first { it is Beat.Visit && it.visit.sold }
        val visit = (m.beats[at] as Beat.Visit).visit
        show(m)
        compose.mainClock.autoAdvance = false
        position = at
        compose.mainClock.advanceTimeByFrame()   // one frame, and no more
        compose.onNodeWithTag("shopday_plate", useUnmergedTree = true).assertTextEquals(visit.face.name)
        compose.onNodeWithTag("shopday_outcome", useUnmergedTree = true).assertTextEquals(visit.outcome).assertIsDisplayed()
        compose.onNodeWithTag("shopday_decision", useUnmergedTree = true).assertIsDisplayed()
        text(visit.receipt.last().label).assertExists()
        assertTrue(compose.onAllNodesWithText(visit.receipt.last().value, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun skipLandsOnTomorrowBlessingOrFallen() {
        val tomorrow = day("purchase").ui()
        val blessing = day("blessing").ui()
        val fallen = day("fallen").ui()
        show(tomorrow)
        for ((m, kind, lands) in listOf(Triple(tomorrow, "Tomorrow", "shopday_lead"), Triple(blessing, "Blessing", "shopday_later"), Triple(fallen, "Fallen", "shopday_fallen_cause"))) {
            model = m; position = 0; skips = 0
            compose.waitForIdle()
            compose.onNodeWithTag("shopday_skip").performClick()   // one tap, no question asked
            compose.waitForIdle()
            assertEquals(1, skips)
            assertEquals(kind, m.beats[position]::class.simpleName)
            compose.onNodeWithTag(lands, useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("shopday_skip").assertDoesNotExist()
        }
        assertEquals("skipping acknowledges nothing", 0, closes)
    }

    @Test
    fun aftermathNamesHeroAndBlade() {
        val m = day("aftermath_win_new_blade").ui()
        val at = m.first { it is Beat.Aftermath && it.card.kind == AftermathKind.WIN_NEW_BLADE }
        val card = (m.beats[at] as Beat.Aftermath).card
        show(m, at)
        text(card.hero!!.name).assertIsDisplayed()
        text(card.blade!!.name).assertIsDisplayed()
        compose.onNodeWithTag("shopday_aftermath_text", useUnmergedTree = true).assertTextEquals(card.text).assertIsDisplayed()
        text(card.blade!!.name).performClick()
        assertEquals(card.blade!!.weaponId, openedBlade)
    }

    @Test
    fun anInheritedBladeNamesBothGuildmates() {
        // The engine's own sentence for a guild inheritance (Battle), on a real day's heroes: about one run in ten has one.
        val day = day("aftermath_death")
        val (heir, fallen) = day.state.heroes.values.sortedBy { it.id.value }.let { all -> all.first { it.isAlive } to all.first { !it.isAlive } }
        val blade = WeaponSnapshot.of(day.state.weapons.values.first())
        val passed = AftermathCard(AftermathKind.INHERITED, "${blade.name} passed from the fallen ${fallen.fullName} to guildmate ${heir.fullName}.", heir.id, heir.fullName, otherHeroId = fallen.id, weapon = blade)
        val m = day.ui(day.script.copy(aftermath = listOf(passed)))
        val at = m.first { it is Beat.Aftermath && it.card.kind == AftermathKind.INHERITED }
        val card = (m.beats[at] as Beat.Aftermath).card
        show(m, at)
        assertEquals(heir.fullName to fallen.fullName, card.hero!!.name to card.other!!.name)
        assertTrue("heir and fallen are two people: ${card.hero} / ${card.other}", card.hero != null && card.other != null && card.hero!!.heroId != card.other!!.heroId)
        text(card.hero!!.name).assertIsDisplayed()
        text(card.other!!.name).assertIsDisplayed()
        text(card.blade!!.name).assertIsDisplayed()
    }

    @Test
    fun aMerchantResaleIsNotShownAsAShopSale() {
        val day = day("purchase")
        val buyer = day.state.aliveHeroes().first()
        val blade = WeaponSnapshot.of(day.state.weapons.values.first())
        val resale = AftermathCard(AftermathKind.RESOLD, "A travelling merchant sold ${blade.name} to ${buyer.fullName}.", buyer.id, buyer.fullName, weapon = blade)
        val m = day.ui(day.script.copy(aftermath = listOf(resale)))
        val at = m.first { it is Beat.Aftermath }
        show(m, at)
        compose.onNodeWithTag("shopday_aftermath_kind", useUnmergedTree = true).assertTextEquals("Sold on by a merchant").assertIsDisplayed()
        compose.onNodeWithTag("shopday_aftermath_note", useUnmergedTree = true).assertTextEquals("NOT A SHOP SALE").assertIsDisplayed()
        compose.onNodeWithTag("shopday_aftermath_text", useUnmergedTree = true).assertTextEquals(resale.text)
        compose.onNodeWithTag("shopday_receipt").assertDoesNotExist()
        compose.onNodeWithTag("shopday_outcome_chip", useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodesWithText("Coin to the till", useUnmergedTree = true).assertCountEquals(0)
        assertTrue("and it is no visit at the counter", m.beats.filterIsInstance<Beat.Visit>().none { it.visit.purchased?.weaponId == blade.weaponId && it.visit.face.heroId == buyer.id && it.visit.kind != VisitKind.BROWSE })
    }

    @Test
    fun replayOutcomeIsNeverGatedOnATimer() {
        val day = day("siege")
        resolutionReplays = day.resolution.replays
        val m = day.ui()
        val at = m.first { it is Beat.Aftermath && it.card.replay != null }
        val card = (m.beats[at] as Beat.Aftermath).card
        show(m)
        compose.mainClock.autoAdvance = false
        position = at
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("shopday_aftermath_text", useUnmergedTree = true).assertTextEquals(card.text).assertIsDisplayed()   // what happened, before any replay
        // The siege's card is taller than the screen: bringing the button into view is a scroll, which needs the clock.
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("shopday_watch").performScrollTo()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("shopday_watch").performClick()
        repeat(3) { compose.mainClock.advanceTimeByFrame() }   // frames to open the overlay; no timer is waited out
        assertEquals(listOf(card.replay!!.eventId), watched)
        compose.onNodeWithTag("shopday_replay_outcome", useUnmergedTree = true).assertTextEquals(card.replay!!.outcome).assertIsDisplayed()
        assertNull("the siege replay is asked for without an event id", if (card.replay!!.kind == ReplayKind.SIEGE) watched.single() else null)
    }

    @Test
    fun tomorrowShowsOneLeadAndItsReason() {
        val day = day("purchase")
        val m = day.ui()
        val tomorrow = m.beats.last() as Beat.Tomorrow
        show(m, m.beats.lastIndex)
        compose.onAllNodesWithTag("shopday_lead", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("shopday_lead", useUnmergedTree = true).assertTextEquals(tomorrow.action).assertIsDisplayed()
        assertNotNull("this day's lead has a recorded reason", tomorrow.reason)
        compose.onNodeWithTag("shopday_lead_reason", useUnmergedTree = true).assertTextEquals(tomorrow.reason!!).assertIsDisplayed()
        text("Begin day ${day.state.day}").assertExists()
        compose.onNodeWithTag("shopday_next").assertDoesNotExist()
    }

    @Test
    fun fallenLeadsToRunEnd() {
        val day = day("fallen")
        val m = day.ui()
        assertTrue(m.beats.last() is Beat.Fallen)
        show(m, m.beats.lastIndex)
        compose.onNodeWithTag("shopday_plate", useUnmergedTree = true).assertTextEquals("The forge has fallen").assertIsDisplayed()
        compose.onNodeWithTag("shopday_fallen_cause", useUnmergedTree = true).assertTextEquals(day.state.endCause!!)
        compose.onNodeWithTag("shopday_next").assertDoesNotExist()
        compose.onNodeWithTag("shopday_lead", useUnmergedTree = true).assertDoesNotExist()   // no tomorrow on the last day
        compose.onNodeWithTag("shopday_card").performTouchInput { click(topLeft) }   // a stray tap does not end the run
        compose.onNodeWithTag("shopday_close").performClick()
        compose.waitForIdle()
        assertEquals(1, closes)
        assertEquals(0, nexts)
    }
}
