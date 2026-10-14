package com.tinyblacksmith.core

import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MissionArchetype
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.guild.GuildOps
import com.tinyblacksmith.core.guild.Missions
import com.tinyblacksmith.core.guild.Stories
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.sim.CombatSandbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regression tests for the confirmed `:core` defects of the 2026-10-14 review (docs/REVIEW_2026-10-14.md rows 22, 31 to 34, 36). */
class ReviewFixesTest {
    private val engine = TestSupport.engineWithoutOpenings
    private val content = engine.content
    private val cat = content.guild!!

    private fun run(seed: Long = 7, e: GameEngine = engine): GameState = e.newRun(LegacyProfile(), seed, charterId = GuildContent.TOWNS_LAST_HOPE).let { e.handle(it, Command.DeclineRelicOffer).stateOrThrow() }
    private fun GameState.send(cmd: Command, e: GameEngine = engine): GameState = e.handle(this, cmd).stateOrThrow()
    private fun GameState.rejected(cmd: Command): GameError = (engine.handle(this, cmd) as CommandOutcome.Rejected).error
    private fun GameState.sound(e: GameEngine = engine): GameState = also { assertEquals(emptyList(), Invariants.check(it, e.config, e.shelfSlots(it), content)) }
    private fun GameState.members() = guild!!.members.map { it.heroId }
    private fun GameState.edit(e: GameEngine = engine, f: (ResolutionContext) -> Unit): GameState = ResolutionContext(this, content, e.config).also(f).toState()
    /** Puts a contract of [defId] on the board, generated the way the board generates it. */
    private fun GameState.post(defId: String): Pair<GameState, MissionOffer> {
        val id = "t${day}x${guild!!.nextMissionSerial}x${guild!!.offers.size}"
        val s = edit { c -> c.guild = c.guild!!.copy(offers = c.guild!!.offers + Missions.generate(c, cat.mission(defId), c.factions.keys.minBy { it.value }, c.rng(com.tinyblacksmith.core.rng.RngStream.GUILD), id)) }
        return s to s.guild!!.offers.single { it.id == id }
    }
    private fun GameState.farSiege(): GameState = copy(town = town.copy(nextSiegeDay = day + 30), siege = SiegeScenario(day + 30))

    // ---- row 31: rank 3's shorter offer life ----

    @Test fun `at rank 3 an ordinary contract leaves the board a day sooner`() {
        val s0 = run().farSiege()
        val life = { rank: Int ->
            val s = s0.copy(guild = s0.guild!!.copy(rank = rank, offers = emptyList())).edit { Missions.refreshBoard(it) }
            val o = s.guild!!.offers.first { cat.mission(it.defId).let { d -> d.weight > 0.0 && d.archetype != MissionArchetype.SABOTAGE } }
            o.expiresDay - s.day + 1 to cat.mission(o.defId).lifeDays
        }
        val (plain, lifeDays) = life(0)
        assertEquals(lifeDays, plain)
        val delta = cat.rank(3)!!.offerLifeDelta
        assertEquals(-1, delta)
        val (ranked, rankedDays) = life(3)
        assertEquals(maxOf(1, rankedDays + delta), ranked, "the rank's card says contracts stay a day less")
    }

    // ---- row 33: dismissing a member of today's planned party ----

    @Test fun `a member of today's planned party cannot be dismissed, and the plan and its fee stand`() {
        val (s0, paid) = run().farSiege().copy(gold = 500).post(GuildContent.HUNT)
        assertTrue(paid.fee > 0)
        val who = s0.members().first()
        val planned = s0.send(Command.PlanDeployment(paid.id, listOf(who), Posture.BALANCED)).sound()
        assertEquals(500 - paid.fee, planned.gold)
        val error = planned.rejected(Command.DismissHero(who))
        assertIs<GameError.MemberUnavailable>(error)
        assertEquals("is in the party you planned for today", error.reason)
        // Cancelling first is the way: the fee comes back, and then the member can go.
        val cancelled = planned.send(Command.CancelPlannedDeployment).sound()
        assertEquals(500, cancelled.gold)
        assertTrue(who !in cancelled.send(Command.DismissHero(who)).sound().members())
    }

    // ---- row 32: a planned Recovery contract and the dealer's "Buy it back" ----

    @Test fun `buying the weapon back drops a plan for its recovery contract and gives the fee back, with a line that says so`() {
        val s0 = run().farSiege()
        val carrier = s0.members().first()
        val blade = CombatSandbox.blade("w900", LaunchContent.SWORD, LaunchContent.MOONSTEEL, LaunchContent.FROST_BLOOM, quality = 95).copy(location = WeaponLocation.Loaned(carrier))
        val taken = s0.copy(weapons = s0.weapons + (blade.id to blade)).edit { c ->
            c.updateWeapon(c.weapon(blade.id).copy(location = WeaponLocation.Lost(c.day, GuildOps.LOST_TAKEN)))
            Stories.taken(c, blade.id, LaunchContent.ASHCLAW)
            Missions.refreshBoard(c)
        }.sound().copy(gold = 5000)
        val recovery = taken.guild!!.offers.single { it.subjectWeaponId == blade.id }
        assertTrue(recovery.fee > 0)
        val party = taken.members().filter { taken.guild!!.member(it)!!.status == MemberStatus.HOME }.take(1)
        val planned = taken.send(Command.PlanDeployment(recovery.id, party, Posture.BALANCED)).sound()
        assertEquals(5000 - recovery.fee, planned.gold)
        val s = Encounters.force(planned, content, engine.config, GuildContent.ACROSS_THE_COUNTER) ?: error("the dealer is not eligible")
        val price = s.encounter!!.amounts.getValue("price")
        val outcome = engine.handle(s, Command.ResolveEncounter(s.encounter!!.id, "buy", CommandId("answer:buy")))
        val bought = assertIs<CommandOutcome.Accepted>(outcome).state.sound()
        assertTrue(bought.weapon(blade.id).isInStorage)
        assertNull(bought.guild!!.planned)
        assertEquals(5000 - price, bought.gold, "the recovery contract's fee came back")
        val line = outcome.events.single { it.text.contains("plan") }.text
        assertTrue(line.contains("${recovery.fee} gold"), line)
        assertTrue(!line.contains(";") && !line.contains(" - ") && !line.contains("blade"), line)
    }

    // ---- row 36b: a law's repair percent waits for the day it is in force ----

    @Test fun `a law announced for tomorrow does not raise tonight's repair bill`() {
        val s = run().farSiege()
        val law = cat.laws.first { it.repairPercent != 100 }
        val announced = s.copy(guild = s.guild!!.copy(law = WorldLaw(law.id, s.day + 1, s.day + 1 + law.days)))
        val plain = engine.config.guild.fractureWear
        assertEquals(plain, Missions.fractureWear(ResolutionContext(announced, content, engine.config)), "announced, not yet in force")
        val inForce = announced.copy(day = s.day + 1)
        assertEquals(com.tinyblacksmith.core.combat.Fight.pct(plain, law.repairPercent), Missions.fractureWear(ResolutionContext(inForce, content, engine.config)))
        val over = announced.copy(day = s.day + 1 + law.days)
        assertEquals(plain, Missions.fractureWear(ResolutionContext(over, content, engine.config)), "lapsed")
    }
}
