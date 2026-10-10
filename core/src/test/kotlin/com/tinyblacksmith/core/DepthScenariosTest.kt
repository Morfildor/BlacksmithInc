package com.tinyblacksmith.core

import com.tinyblacksmith.core.ScenarioSaves.byId
import com.tinyblacksmith.core.ScenarioSaves.endDay
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The constructed scenario saves show what their titles say: each visitor, each relic, both traits and both ends of the chain. */
class DepthScenariosTest {
    private val visitors = mapOf(
        "visitor_collector" to Depth.COLLECTORS_OFFER, "visitor_crate_no_gold" to Depth.LAST_CRATE, "visitor_pledge" to Depth.BLADE_FOR_THE_WALL,
        "visitor_master" to Depth.MASTERS_AFTERNOON, "visitor_heirloom" to Depth.CRACKED_FAMILY_BLADE, "visitor_merchant" to Depth.CROOKED_MERCHANT,
        "visitor_wager" to Depth.SMITHS_WAGER, "visitor_festival" to Depth.FESTIVAL_CONTRACT, "visitor_unanswered" to Depth.CROOKED_MERCHANT, "chain_debt_repaid" to Depth.DEBT_REPAID,
    )

    @Test
    fun everyVisitorOfTheCatalogueHasASaveWithItWaitingAfterAReload() {
        assertEquals(engine.content.encounters.map { it.id }.toSet(), visitors.values.toSet())
        for ((id, defId) in visitors) {
            // What the app does with a scenario file: decode it. The visitor is there, open, and reads the same.
            val s = SaveCodec.decodeRun(SaveCodec.encodeRun(byId(id).state))
            val view = assertNotNull(engine.encounterView(s), id)
            assertTrue(view.instance.defId == defId && view.instance.isOpen && view.instance.day == s.day, id)
            assertEquals(engine.encounterView(byId(id).state), view, id)
        }
    }

    @Test
    fun theEmptyPurseClosesBothPurchasesAndLeavesTheFreeAnswer() {
        val view = engine.encounterView(byId("visitor_crate_no_gold").state)!!
        assertEquals(listOf("crate", "metal"), view.options.filter { it.blocked != null }.map { it.id })
        assertNull(view.options.last().blocked)
    }

    @Test
    fun everyOtherVisitorSaveOffersAnswersThatCanBeTaken() {
        for (id in visitors.keys - "visitor_crate_no_gold") {
            val open = engine.encounterView(byId(id).state)!!.options.filter { it.blocked == null && it.id != Encounters.PASS }
            assertTrue(open.isNotEmpty(), id)
            for (o in open) assertIs<CommandOutcome.Accepted>(engine.handle(byId(id).state, Command.ResolveEncounter(byId(id).state.encounter!!.id, o.id, CommandId("t"))), "$id/${o.id}")
        }
    }

    @Test
    fun theUnansweredVisitorLeavesAtEndDay() {
        val day = endDay(byId("visitor_unanswered").state)
        assertTrue(day.resolution.events.any { it.type == EventType.ENCOUNTER_EXPIRED })
        assertEquals(day.before.weapons.size, day.after.weapons.size)
        assertEquals(day.before.gold, day.resolution.ledger!!.goldAtOpen)
    }

    @Test
    fun theRelicSavesHoldWhatTheySay() {
        assertTrue(byId("relic_offer").state.let { it.day == 1 && it.pendingRelicOffer.size == 3 && it.relics.isEmpty() })
        val crucible = byId("relic_crucible").state
        assertEquals(2, crucible.storedWeapons().count { engine.salvageKeepsAugment(crucible, it) })
        val ledger = byId("relic_ledger").state
        assertEquals(listOf(0, 0, 4), listOf(ledger.relics.single().families[0], ledger.relics.single().families[1], engine.content.families.first { it.id !in ledger.relics.single().families }.id).map { engine.ledgerBonus(ledger, it) })
        val seal = byId("relic_seal").state
        assertTrue(seal.relics.single().progress == engine.config.depth.sealsPerReward - 1 && seal.listedWeapons().any { (it.listedPrice ?: 0) >= engine.config.depth.sealMinCash })
        val bellows = byId("relic_bellows").state
        assertIs<CommandOutcome.Accepted>(engine.handle(bellows, TestSupport.quickSword().copy(bellows = true)))
        val full = byId("relic_full_workshop").state
        assertTrue(full.relics.size == engine.config.depth.relicSlots && full.pendingRelicOffer == listOf(Depth.ASHEN_BELLOWS))
        assertIs<CommandOutcome.Rejected>(engine.handle(full, Command.ChooseRelic(Depth.ASHEN_BELLOWS)))
        assertIs<CommandOutcome.Accepted>(engine.handle(full, Command.ChooseRelic(Depth.ASHEN_BELLOWS, full.relics.first().id)))
    }

    @Test
    fun theSiegeSavesForecastTheirTraitTwoDaysOut() {
        for ((id, trait) in listOf("siege_long_assault" to Depth.LONG_ASSAULT, "siege_many_breaches" to Depth.MANY_BREACHES)) {
            val s = byId(id).state
            val outlook = engine.siegeForecast(s)!!
            assertEquals(trait, outlook.trait?.id, id)
            assertEquals(2, s.town.nextSiegeDay - s.day, id)
            assertTrue(outlook.champions.isNotEmpty(), "$id: somebody stands on the wall")
        }
    }

    @Test
    fun theChainSavesShowBothEnds() {
        val back = byId("chain_debt_repaid").state
        val visitor = back.encounter!!
        assertTrue(back.hero(visitor.heroId!!).isAlive && back.weapon(visitor.weaponId!!).ownerId == visitor.heroId)
        assertTrue(back.events.any { it.type == EventType.SIEGE_WON && it.day == back.day - 1 })
        val dead = endDay(byId("chain_pledge_dead").state)
        val told = dead.after.events.single { it.type == EventType.PLEDGE_RESOLVED }
        assertEquals("dead", told.data["outcome"])
        assertTrue(dead.after.encounter?.defId != Depth.DEBT_REPAID && dead.after.consequences.none { it.kind == ConsequenceKind.WALL_PLEDGE })
    }
}
