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
}
