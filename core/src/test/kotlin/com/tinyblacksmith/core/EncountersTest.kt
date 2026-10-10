package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Morning visitors: stored offers, exactly-once answers, expiry, subjects that are gone, and every catalogue entry from a fixture. */
class EncountersTest {
    private val config = engine.config
    private val content = engine.content

    private fun GameState.rejected(cmd: Command): GameError = assertIs<CommandOutcome.Rejected>(engine.handle(this, cmd)).error
    private fun GameState.answer(option: String, id: String = "a1") = Command.ResolveEncounter(encounter!!.id, option, CommandId(id))
    private fun GameState.forced(defId: String): GameState = assertNotNull(Encounters.force(this, content, config, defId), "$defId is not eligible in this fixture")

    /** A rich run on day 1 with a famous blade in storage: the collector has something to ask after. */
    private fun withFamousBlade(seed: Long = 7): Pair<GameState, WeaponId> {
        val made = engine.newRun(LegacyProfile(), seed).withMaterials().copy(gold = 1000).forgeAccepted(quickSword(Risk.SAFE))
        val id = made.forgedWeaponId!!
        return made.state.copy(weapons = made.state.weapons + (id to made.state.weapon(id).copy(fame = 3))) to id
    }

    /** Every fixture a catalogue entry can be offered from, by encounter ID. */
    private fun fixture(defId: String): GameState {
        val base = withFamousBlade().first
        val heroes = base.aliveHeroes()
        return when (defId) {
            Depth.LAST_CRATE -> base.copy(town = base.town.copy(nextSiegeDay = base.day + 2), siege = SiegeScenario(base.day + 2))
            Depth.BLADE_FOR_THE_WALL -> base.copy(heroes = base.heroes + (heroes[0].id to heroes[0].copy(gold = 5)) + (heroes[1].id to heroes[1].copy(gold = 900)))
            Depth.MASTERS_AFTERNOON -> base.run(quickSword())
            Depth.CRACKED_FAMILY_BLADE -> {
                val fallen = heroes[0].copy(fate = HeroFate.DEAD, health = 0, diedOnDay = 1, guildId = "g1")
                val blade = base.storedWeapons().first()
                base.copy(
                    heroes = base.heroes + (fallen.id to fallen) + (heroes[1].id to heroes[1].copy(guildId = "g1")),
                    town = base.town.copy(championIds = base.town.championIds - fallen.id),
                    weapons = base.weapons + (blade.id to blade.copy(condition = 40, history = blade.history + HistoryEntry(base.era, 1, "RECOVERED", "Recovered after ${fallen.fullName}'s death.", listOf(fallen.id.value)))),
                )
            }
            else -> base
        }.forced(defId)
    }

    private val drawn = content.encounters.filter { !it.followUp }.map { it.id }

    @Test
    fun theCatalogueHoldsEightVisitorsAndTheChainsSecondStage() {
        assertEquals(8, drawn.size)
        assertEquals(listOf(Depth.DEBT_REPAID), content.encounters.filter { it.followUp }.map { it.id })
        assertTrue(content.validate().isEmpty())
    }

    @Test
    fun everyVisitorIsReachableAndHasTwoRealAnswersAndAFreeOne() {
        for (id in drawn) {
            val s = fixture(id)
            val view = assertNotNull(engine.encounterView(s), id)
            val real = view.options.filter { it.id != Encounters.PASS }
            assertTrue(view.options.last().id == Encounters.PASS && view.options.last().cost == "Free" && view.options.last().blocked == null, "$id has no free answer")
            // The collector asks one thing: sell the blade or keep it. Keeping is its free answer, and a real one.
            assertTrue(real.size >= (if (id == Depth.COLLECTORS_OFFER) 1 else 2), "$id offers ${real.size} answers")
            assertTrue(real.all { it.blocked == null }, "$id: ${real.filter { it.blocked != null }.map { it.id to it.blocked }}")
            assertTrue(view.text.isNotBlank() && view.options.all { it.label.isNotBlank() && it.effect.isNotBlank() })
        }
    }

    @Test
    fun everyAnswerOfEveryVisitorIsAcceptedAndLeavesASoundRun() {
        for (id in drawn + Depth.DEBT_REPAID) {
            val s = if (id == Depth.DEBT_REPAID) debtFixture() else fixture(id)
            for (o in engine.encounterView(s)!!.options) {
                val after = assertIs<CommandOutcome.Accepted>(engine.handle(s, s.answer(o.id)), "$id/${o.id}").state
                assertEquals(SaveCodec.decodeRun(SaveCodec.encodeRun(after)), after, "$id/${o.id} does not survive a save")
                assertTrue(after.events.any { it.type == EventType.ENCOUNTER_RESOLVED && it.data["option"] == o.id }, "$id/${o.id} left no record")
            }
        }
    }

    /** The pledge hero came through the siege with the blade in hand: the chain's second stage, built as `Consequences.dueFollowUp` builds it. */
    private fun debtFixture(): GameState {
        val (base, bladeId) = withFamousBlade()
        val hero = base.aliveHeroes().first()
        return base.copy(
            weapons = base.weapons + (bladeId to base.weapon(bladeId).copy(location = WeaponLocation.Owned(hero.id, true))),
            encounter = EncounterInstance("n1", Depth.DEBT_REPAID, base.day, heroId = hero.id, weaponId = bladeId, amounts = mapOf("owed" to 30, "stood" to 1, "held" to 1)),
        )
    }

    @Test
    fun theSameSeedIsOfferedTheSameVisitorsAndReadingThemDrawsNothing() {
        fun play(): List<EncounterInstance?> {
            var s = engine.newRun(LegacyProfile(), 11)
            return (1..8).map { s = s.endDay(); engine.encounterView(s); engine.encounterView(s); s.encounter }
        }
        val first = play()
        assertEquals(first, play())
        assertTrue(first.count { it != null } >= 3, "eight mornings brought ${first.count { it != null }} visitors")
        // Day 1 has no visitor, and nothing but the ENCOUNTERS stream pays for the ones that come.
        assertNull(engine.newRun(LegacyProfile(), 11).encounter)
    }

    @Test
    fun aVisitorSurvivesTheSaveAndIsNotRedrawn() {
        var s = engine.newRun(LegacyProfile(), 11)
        while (s.encounter == null) s = s.endDay().also { check(!it.isEnded) { "the run ended before a visitor came" } }
        val loaded = SaveCodec.decodeRun(SaveCodec.encodeRun(s))
        assertEquals(s.encounter, loaded.encounter)
        assertEquals(engine.encounterView(s), engine.encounterView(loaded))
        assertEquals(s.rng, loaded.rng)
    }

    @Test
    fun anAnswerIsAppliedOnceAndARetryChangesNothing() {
        val (base, blade) = withFamousBlade()
        val s = base.forced(Depth.COLLECTORS_OFFER)
        val price = s.encounter!!.amounts.getValue("price")
        val sold = s.run(s.answer("sell", "tap"))
        assertEquals(s.gold + price, sold.gold)
        assertIs<WeaponLocation.Lost>(sold.weapon(blade).location)
        assertEquals(EncounterStatus.RESOLVED, sold.encounter!!.status)
        // The same command again (a double tap, a retry after a process death): accepted, nothing moves.
        val retry = assertIs<CommandOutcome.Accepted>(engine.handle(sold, s.answer("sell", "tap")))
        assertTrue(retry.state === sold && retry.events.isEmpty())
        // Another command for the answered visitor is refused, whatever it asks.
        assertIs<GameError.EncounterNotOpen>(sold.rejected(s.answer("sell", "other")))
        assertIs<GameError.EncounterNotOpen>(sold.rejected(s.answer(Encounters.PASS, "other")))
        assertIs<GameError.EncounterNotOpen>(s.rejected(Command.ResolveEncounter("n999", "sell", CommandId("x"))))
        assertEquals(1, sold.encounterLog.size)
    }

    @Test
    fun anUnansweredVisitorLeavesAtEndDayWithTheFreeAnswer() {
        val (base, blade) = withFamousBlade()
        val s = base.forced(Depth.COLLECTORS_OFFER)
        val day = s.endDayAccepted()
        assertTrue(day.state.weapon(blade).isInStorage, "the unanswered collector took nothing")
        val expired = day.resolution!!.events.single { it.type == EventType.ENCOUNTER_EXPIRED }
        assertTrue(engine.encounterView(s)!!.defaultLabel in expired.text)
        assertEquals(EncounterRecord(s.encounter!!.id, Depth.COLLECTORS_OFFER, s.day, Encounters.PASS, expired = true), day.state.encounterLog.single())
        // Yesterday's command cannot reach today's visitor or the one that left.
        assertIs<CommandOutcome.Rejected>(engine.handle(day.state, s.answer("sell")))
    }

    @Test
    fun anAnswerWhoseBladeIsGoneIsClosedWithAReasonAndTheFreeOneStillWorks() {
        val (base, blade) = withFamousBlade()
        val s = base.forced(Depth.COLLECTORS_OFFER).run(Command.Salvage(blade))
        val view = engine.encounterView(s)!!
        assertNotNull(view.options.first { it.id == "sell" }.blocked)
        assertIs<GameError.EncounterOptionBlocked>(s.rejected(s.answer("sell")))
        assertEquals(s.gold, s.run(s.answer(Encounters.PASS)).gold)
    }

    @Test
    fun anAnswerTheForgeCannotPayForIsRefusedByTheEngineToo() {
        val s = fixture(Depth.LAST_CRATE).copy(gold = 0)
        assertNotNull(engine.encounterView(s)!!.options.first { it.id == "crate" }.blocked)
        assertIs<GameError.EncounterOptionBlocked>(s.rejected(s.answer("crate")))
        val broke = fixture(Depth.MASTERS_AFTERNOON).copy(energy = 0, overworkToday = config.maxOverworkPerDay)
        assertIs<GameError.EncounterOptionBlocked>(broke.rejected(broke.answer("study")))
    }

    @Test
    fun aVisitorWhoTookOverAWorldEventIsTheOnlyWayItHappens() {
        val (s, _) = withFamousBlade()
        val listed = s.run(Command.ToggleShelf(s.storedWeapons().first().id, listed = true))
        val collector = WorldEvents.byId("collector")
        assertTrue(!WorldEvents.canFire(ResolutionContext(listed, content, config), collector))
        // The same run under a catalogue without visitors plays the automatic event as rules 3 did.
        assertTrue(WorldEvents.canFire(ResolutionContext(listed, content.copy(encounters = emptyList()), config), collector))
        // Both count against the event's own limit.
        val offered = Encounters.force(listed, content, config, Depth.COLLECTORS_OFFER)!!.copy(day = 2)   // visitors come from day 2
        assertTrue(Encounters.canOffer(ResolutionContext(offered, content, config), content.encounter(Depth.COLLECTORS_OFFER)!!))
        val spent = offered.copy(eventCounters = mapOf("collector" to content.encounter(Depth.COLLECTORS_OFFER)!!.maxPerRun))
        assertTrue(!Encounters.canOffer(ResolutionContext(spent, content, config), content.encounter(Depth.COLLECTORS_OFFER)!!))
    }

    @Test
    fun theMerchantsBladeIsFixedWhenOfferedAndMintedOnlyWhenBought() {
        val s = fixture(Depth.CROOKED_MERCHANT)
        val offered = s.encounter!!.blade!!
        assertTrue(offered.flaws.size == 1 && s.weapons.values.none { it.name == offered.name && it.id == offered.id })
        val looked = s.run(s.answer("inspect", "look"))
        assertEquals(s.gold - s.encounter!!.amounts.getValue("fee"), looked.gold)
        assertTrue(looked.encounter!!.isOpen && looked.encounter!!.inspected && looked.encounter!!.blade == offered, "an inspection shows the blade, it does not reroll it")
        assertTrue(engine.encounterView(looked)!!.options.none { it.id == "inspect" })
        assertTrue(looked.run(s.answer("inspect", "look")) === looked)
        val bought = looked.run(looked.answer("buy", "buy"))
        val mine = bought.weapons.values.single { it.id !in s.weapons }
        assertEquals(offered.copy(id = mine.id, history = mine.history), mine)
        assertTrue(mine.isInStorage && bought.nextWeaponSerial == s.nextWeaponSerial + 1)
        assertEquals(s.weapons.size, looked.run(looked.answer(Encounters.PASS, "no")).weapons.size)
    }
}
