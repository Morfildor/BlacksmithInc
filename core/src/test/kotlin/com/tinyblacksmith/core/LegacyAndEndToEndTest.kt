package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.battle.Power
import com.tinyblacksmith.core.content.SliceContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.acceptedOrThrow
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.InMemorySaveRepository
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LegacyAndEndToEndTest {

    @Test
    fun legacyClaimIsOnceOnlyAndUpgradesStrengthenNextRun() {
        val repo = InMemorySaveRepository()
        var state = engine.newRun(repo.loadLegacy(), 500)
        state = state.copy(town = state.town.copy(integrity = 1), milestones = setOf("FIRST_SALE"), discoveriesThisRun = 2)
        while (!state.isEnded) state = state.endDayAccepted().state
        val end = engine.closeRun(state)
        assertEquals(5, end.basePoints)
        assertEquals(state.day / 5, end.survivalPoints)
        assertEquals(2, end.discoveryPoints)
        assertTrue(end.milestonePoints >= 1)
        val claimed = engine.claimLegacy(repo.loadLegacy(), end)
        assertIs<LegacyOutcome.Updated>(claimed)
        repo.saveAtomically(null, claimed.legacy)
        val again = engine.claimLegacy(repo.loadLegacy(), end)
        assertIs<LegacyOutcome.Rejected>(again)
        assertIs<GameError.AlreadyClaimed>(again.error)
        assertEquals(claimed.legacy, again.legacy)

        val bought = engine.purchaseUpgrade(repo.loadLegacy(), SliceContent.UPG_ENERGY)
        assertIs<LegacyOutcome.Updated>(bought)
        repo.saveAtomically(null, bought.legacy)
        val next = engine.newRun(repo.loadLegacy(), 501)
        assertEquals(11, next.energy)
        assertEquals(2, next.era)
        assertEquals(0, next.weapons.size, "run-only inventory does not persist")
        assertEquals(engine.config.startingGold, next.gold, "gold resets")
        assertEquals(1, next.day)
    }

    @Test
    fun journalAndLineagePersistAcrossRunsButRunStateDoesNot() {
        var s = engine.newRun(LegacyProfile(), 600).copy(materials = engine.content.materials.associate { it.id to 30 }, energy = 100)
        repeat(3) { s = s.forgeAccepted(quickSword()).state }
        val key = Journal.coreAugmentKey(SliceContent.IRON, SliceContent.EMBER_RESIN)
        assertEquals(KnowledgeState.UNDERSTOOD, s.legacy.journal.state(key))
        s = s.copy(town = s.town.copy(integrity = 1), heroes = s.heroes.mapValues { (_, h) -> h.copy(fame = 3) })
        while (!s.isEnded) s = s.endDayAccepted().state
        val end = engine.closeRun(s)
        val legacy = (engine.claimLegacy(LegacyProfile(), end) as LegacyOutcome.Updated).legacy
        assertEquals(KnowledgeState.UNDERSTOOD, legacy.journal.state(key))
        assertNotNull(end.lineage)
        assertEquals(1, legacy.lineages.size)
        val next = engine.newRun(legacy, 601)
        val descendant = next.heroes.values.first { it.descendantOf != null }
        assertEquals(end.lineage!!.surname, descendant.surname)
        assertEquals(end.lineage.heroName, descendant.descendantOf)
        assertEquals(10, next.energy)
    }

    @Test
    fun blessingOfferedAfterSurvivedSiegeAndAppliesTemporarily() {
        // This test is about the blessing mechanism, not balance: use a gentle siege so the champions win.
        val gentle = GameEngine(config = engine.config.copy(siegeModifier = 0.5))
        var s = gentle.newRun(LegacyProfile(), 700)
        while (s.day < 5) s = gentle.handle(s, Command.EndDay(TestSupport.endDayId(s))).acceptedOrThrow().state
        s = s.copy(heroes = s.heroes.mapValues { (_, h) -> h.copy(level = 20, health = 100) })
        val acc = gentle.handle(s, Command.EndDay(TestSupport.endDayId(s))).acceptedOrThrow()
        assertTrue(acc.resolution!!.events.any { it.type == EventType.SIEGE_WON })
        assertEquals(3, acc.state.pendingBlessingOffer.size)
        val chosen = gentle.handle(acc.state, Command.ChooseBlessing(SliceContent.TIRELESS_HANDS)).acceptedOrThrow().state
        assertTrue(chosen.pendingBlessingOffer.isEmpty())
        val morning = gentle.handle(chosen, Command.EndDay(TestSupport.endDayId(chosen))).acceptedOrThrow().state
        assertEquals(12, morning.energy)
        var later = morning
        repeat(6) { later = gentle.handle(later, Command.EndDay(TestSupport.endDayId(later))).acceptedOrThrow().state }
        assertTrue(later.blessings.isEmpty())
        assertEquals(10, later.energy)
    }

    /**
     * GDD 16.1 ACCEPTANCE: craft -> price/list -> autonomous purchase -> equip -> weapon affects combat -> Gazette ->
     * scheduled siege -> forge falls -> baseline + milestone reward -> stronger following run. Deterministic and resumable.
     */
    @Test
    fun verticalSliceEndToEnd() {
        val repo = InMemorySaveRepository()
        val seed = 2026L
        var state = engine.newRun(repo.loadLegacy(), seed)
        repo.saveAtomically(state, state.legacy)

        // Craft a sword and price it manually.
        val forged = state.forgeAccepted(quickSword(Risk.BALANCED, SliceContent.BRONZE, SliceContent.FROST_BLOOM))
        val swordId = forged.forgedWeaponId!!
        state = forged.state.run(Command.ToggleShelf(swordId, true, price = 20))
        assertEquals(20, state.weapon(swordId).listedPrice)

        // End days until an autonomous hero buys it (bounded).
        var buyer: Hero? = null
        var dayOfSale = -1
        while (buyer == null && state.day < 6) {
            val acc = state.endDayAccepted()
            repo.saveAtomically(acc.state, acc.state.legacy)
            state = repo.loadRun()!! // resume from the save every day: outcomes must be unaffected
            val sale = acc.resolution!!.events.firstOrNull { it.type == EventType.WEAPON_SOLD && swordId.value in it.subjectIds }
            if (sale != null) { buyer = state.hero(HeroId(sale.subjectIds[0])); dayOfSale = acc.resolution.day }
        }
        assertNotNull(buyer, "a hero should buy a cheap bronze sword within a few days")
        val sword = state.weapon(swordId)
        assertEquals(WeaponLocation.Owned(buyer.id, equipped = true), sword.location)
        assertTrue(state.eventsForDay(dayOfSale).any { it.type == EventType.WEAPON_EQUIPPED && swordId.value in it.subjectIds })
        assertTrue(state.lastResolution!!.headlines.any { "bought" in it } || state.eventsForDay(dayOfSale).any { it.priority >= 2 })

        // The weapon measurably improves combat for its owner.
        val faction = engine.content.factions.first()
        val armed = Power.attackPower(buyer, sword, faction, engine.content, engine.config)
        val unarmed = Power.attackPower(buyer, null, faction, engine.content, engine.config)
        assertTrue(armed > unarmed, "armed $armed vs unarmed $unarmed")

        // Keep ending days without further crafting: the hero fights with the sword, sieges arrive on schedule, forge falls.
        var swordFought = false
        var siegeSeen = false
        var gazetteStory = false
        while (!state.isEnded) {
            val acc = state.endDayAccepted()
            val r = acc.resolution!!
            if (r.events.any { (it.type == EventType.EXPEDITION_WON || it.type == EventType.EXPEDITION_LOST || it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST) && swordId.value in it.subjectIds }) swordFought = true
            if (r.events.any { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }) { siegeSeen = true; assertEquals(0, r.day % 5) }
            if (r.headlines.any { h -> r.events.any { it.text == h && (buyer.fullName in it.text || sword.name in it.text) } }) gazetteStory = true
            repo.saveAtomically(acc.state, acc.state.legacy)
            state = repo.loadRun()!!
            assertTrue(state.day <= engine.config.maxSimulatedDays)
        }
        assertTrue(swordFought, "the sold sword should have seen combat")
        assertTrue(siegeSeen)
        assertTrue(gazetteStory, "Gazette should carry a true story about the buyer or the sword")
        assertTrue(state.events.any { it.type == EventType.FORGE_DESTROYED })

        // Baseline + milestone legacy reward, claimed once.
        val end = engine.closeRun(state)
        assertEquals(5, end.basePoints)
        assertTrue("FIRST_SALE" in end.milestones)
        assertTrue(end.milestonePoints >= 1)
        val claimed = engine.claimLegacy(repo.loadLegacy(), end) as LegacyOutcome.Updated
        repo.saveAtomically(null, claimed.legacy)
        assertIs<LegacyOutcome.Rejected>(engine.claimLegacy(repo.loadLegacy(), end))
        assertTrue(claimed.legacy.points >= 8, "enough points for a first upgrade: ${claimed.legacy.points}")

        // Spend on a permanent upgrade; the next era starts stronger.
        val upgraded = engine.purchaseUpgrade(repo.loadLegacy(), SliceContent.UPG_GOLD) as LegacyOutcome.Updated
        repo.saveAtomically(null, upgraded.legacy)
        val nextRun = engine.newRun(repo.loadLegacy(), seed + 1)
        assertEquals(engine.config.startingGold + 100, nextRun.gold)
        assertEquals(2, nextRun.era)
        assertTrue(nextRun.legacy.journal.interactions.isNotEmpty(), "journal knowledge survives death")

        // Whole sequence is deterministic: replaying from the same seed yields the same final run state.
        var replay = engine.newRun(LegacyProfile(), seed)
        val f2 = replay.forgeAccepted(quickSword(Risk.BALANCED, SliceContent.BRONZE, SliceContent.FROST_BLOOM))
        replay = f2.state.run(Command.ToggleShelf(f2.forgedWeaponId!!, true, 20))
        while (!replay.isEnded) replay = replay.endDayAccepted().state
        assertEquals(SaveCodec.encodeRun(state), SaveCodec.encodeRun(replay))
    }
}
