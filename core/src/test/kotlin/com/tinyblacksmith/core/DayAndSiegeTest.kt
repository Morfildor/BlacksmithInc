package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.endDayId
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DayAndSiegeTest {
    @Test
    fun endDayIsIdempotentPerCommandId() {
        val s0 = engine.newRun(LegacyProfile(), 21)
        val first = s0.endDayAccepted()
        val retry = engine.handle(first.state, Command.EndDay(endDayId(s0))) as CommandOutcome.Accepted
        assertEquals(first.state, retry.state, "retry must not simulate again")
        assertEquals(first.resolution, retry.resolution)
        assertEquals(2, retry.state.day)
        val next = retry.state.endDay()
        assertEquals(3, next.day)
    }

    @Test
    fun siegesHappenExactlyOnScheduledDays() {
        for (seed in 1L..10L) {
            var s = engine.newRun(LegacyProfile(), seed)
            assertEquals(5, s.town.nextSiegeDay)
            while (!s.isEnded && s.day <= 30) {
                val acc = s.endDayAccepted()
                val siege = acc.resolution!!.events.any { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }
                assertEquals(acc.resolution.day % 5 == 0, siege, "seed $seed day ${acc.resolution.day}")
                if (acc.resolution.day % 5 == 4) assertTrue(acc.resolution.events.any { it.type == EventType.SIEGE_WARNING })
                s = acc.state
            }
        }
    }

    @Test
    fun siegeWithFewerThanThreeEligibleHeroesStillResolves() {
        var s = engine.newRun(LegacyProfile(), 4)
        val heroes = s.heroes.values.sortedBy { it.id.value }
        val keep = heroes.first().id
        s = s.copy(heroes = s.heroes.mapValues { (id, h) -> if (id == keep) h else h.copy(fate = HeroFate.DEAD, health = 0, diedOnDay = 1) }, town = s.town.copy(championIds = emptyList()))
        while (s.day < 5) s = s.endDay()
        val acc = s.endDayAccepted()
        val siege = acc.resolution!!.events.first { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }
        assertTrue(siege.subjectIds.size <= 3)
        assertTrue(acc.state.town.championIds.size <= 3)
        assertTrue(acc.resolution.replays.isNotEmpty())
        // Even zero champions is handled: the militia stands alone.
        var none = s.copy(heroes = s.heroes.mapValues { (_, h) -> h.copy(fate = HeroFate.DEAD, health = 0, diedOnDay = 1) }, town = s.town.copy(championIds = emptyList()))
        val acc2 = none.endDayAccepted()
        assertNotNull(acc2.resolution!!.events.firstOrNull { it.type == EventType.SIEGE_LOST || it.type == EventType.SIEGE_WON })
    }

    @Test
    fun runEndsWhenIntegrityHitsZeroAndBlocksFurtherActions() {
        val driver = SimulationDriver()
        val (stats, state) = driver.playRun(LegacyProfile(), 77, Policy.PASSIVE)
        assertTrue(stats.ended, "passive shop should eventually fall")
        assertEquals(0, state.town.integrity)
        assertEquals(Phase.ENDED, state.phase)
        assertTrue(state.events.any { it.type == EventType.FORGE_DESTROYED })
        val out = engine.handle(state.copy(materials = engine.content.materials.associate { it.id to 5 }), quickSword())
        assertIs<CommandOutcome.Rejected>(out); assertEquals(GameError.RunEnded, out.error)
        val end = engine.handle(state, Command.EndDay(CommandId("new")))
        assertIs<CommandOutcome.Rejected>(end)
    }

    @Test
    fun gazetteHeadlinesOnlyDescribeRealEvents() {
        var s = engine.newRun(LegacyProfile(), 13)
        repeat(6) {
            val acc = s.endDayAccepted()
            val res = acc.resolution!!
            assertTrue(res.headlines.size <= 5)
            res.headlines.forEach { h -> assertTrue(res.events.any { it.text == h }, "headline without event: $h") }
            s = acc.state
        }
    }

    @Test
    fun seedReplayIsExactAndDifferentSeedsDiffer() {
        val driver = SimulationDriver()
        val (a, sa) = driver.playRun(LegacyProfile(), 1234, Policy.BALANCED_FAIR)
        val (b, sb) = driver.playRun(LegacyProfile(), 1234, Policy.BALANCED_FAIR)
        assertEquals(a, b)
        assertEquals(SaveCodec.encodeRun(sa), SaveCodec.encodeRun(sb))
        assertEquals(sa.events, sb.events)
        val (c, _) = driver.playRun(LegacyProfile(), 1235, Policy.BALANCED_FAIR)
        assertTrue(a != c)
    }

    @Test
    fun reloadMidRunProducesIdenticalOutcome() {
        var live = engine.newRun(LegacyProfile(), 55).copy(materials = engine.content.materials.associate { it.id to 20 })
        repeat(3) { live = (engine.handle(live, quickSword()) as CommandOutcome.Accepted).state }
        live.storedWeapons().forEach { live = (engine.handle(live, Command.ToggleShelf(it.id, true, 30)) as CommandOutcome.Accepted).state }
        repeat(4) { live = live.endDay() }
        val reloaded = SaveCodec.decodeRun(SaveCodec.encodeRun(live))
        assertEquals(live, reloaded)
        var a = live
        var b = reloaded
        repeat(8) { a = a.endDay(); b = b.endDay() }
        assertEquals(a, b)
        assertEquals(SaveCodec.encodeRun(a), SaveCodec.encodeRun(b))
    }

    @Test
    fun thousandHeadlessRunsReachDefeatWithoutInvariantFailures() {
        val driver = SimulationDriver()
        var ended = 0
        for (seed in 1L..1000L) {
            val (stats, _) = driver.playRun(LegacyProfile(), seed, Policy.BALANCED_FAIR)
            if (stats.ended) ended++
            assertTrue(stats.daysSurvived >= 5)
        }
        assertTrue(ended >= 950, "most unupgraded runs should end within the cap; ended=$ended")
    }
}
