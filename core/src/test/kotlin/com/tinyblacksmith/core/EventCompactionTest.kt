package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.model.EventRecord
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.persistence.EventCompaction
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import com.tinyblacksmith.core.sim.Simulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GDD 13.3: ordinary events are compacted, rare milestones retained. The policy runs inside End Day, so the same
 * seed must give identical gameplay with and without it; only `GameState.events` may differ, and only outside the
 * retention window. One 400-day forced-survival pair (compacting vs never compacting) backs every long-run assertion.
 */
class EventCompactionTest {
    private companion object {
        const val DAYS = 400
        const val SEED = 77L
        val config = Simulator.forcedSurvival()
        val retention = config.eventRetentionDays

        /** Same seed and policy; A compacts (default config), B never does (retention 0). No sim-side trimming. */
        val compacted: GameState by lazy { play(config) }
        val uncompacted: GameState by lazy { play(config.copy(eventRetentionDays = 0)) }

        fun play(cfg: BalanceConfig): GameState =
            SimulationDriver(GameEngine(config = cfg), maxDays = DAYS).playRun(LegacyProfile(), SEED, Policy.BALANCED_FAIR).second

        /** The last compaction ran on End Day of day `state.day - 1`. */
        fun lastEndDay(state: GameState) = state.day - 1
    }

    @Test
    fun boundaryIsExactAndZeroRetentionDisablesCompaction() {
        fun e(id: Int, day: Int, type: EventType) = EventRecord("e$id", 1, day, type, 1, "t")
        val today = 50
        val events = mutableListOf(
            e(1, 1, EventType.RUN_STARTED),             // kept forever
            e(2, today - 30, EventType.WEAPON_SOLD),    // exactly 30 days old: dropped
            e(3, today - 29, EventType.WEAPON_SOLD),    // inside the window: kept
            e(4, 2, EventType.HERO_DIED),               // kept forever
            e(5, today, EventType.HERO_RESTED),         // today: kept
        )
        val untouched = events.toList()
        EventCompaction.compact(events, today, 0)
        assertEquals(untouched, events, "retention 0 must be a no-op")
        EventCompaction.compact(events, today, 30)
        assertEquals(listOf("e1", "e3", "e4", "e5"), events.map { it.id })
    }

    @Test
    fun compactionDoesNotChangeGameplayOverFourHundredDays() {
        val a = compacted
        val b = uncompacted
        assertEquals(DAYS + 1, a.day)
        assertEquals(b.copy(events = emptyList()), a.copy(events = emptyList()), "gold, weapons, heroes, town, RNG and resolutions must match")
        assertEquals(b.gold, a.gold)
        assertEquals(b.weapons, a.weapons)
        assertEquals(b.heroes, a.heroes)
        assertEquals(b.nextEventSerial, a.nextEventSerial, "every event is still emitted and numbered")
        // Daily compaction is equivalent to applying the policy once at the final End Day.
        val expected = b.events.filter { EventCompaction.keeps(it, lastEndDay(a), retention) }
        assertEquals(expected, a.events)
    }

    @Test
    fun eventLogStaysBoundedOverFourHundredDays() {
        val a = compacted
        val b = uncompacted
        assertTrue(b.events.size == b.nextEventSerial - 1 && b.events.size > 4000, "unbounded log must be large to make the comparison meaningful: ${b.events.size}")
        assertTrue(a.events.size <= 1200, "compacted log too large: ${a.events.size}")
        assertTrue(a.events.size * 4 < b.events.size, "compacted ${a.events.size} vs unbounded ${b.events.size}")
        val cutoff = lastEndDay(a) - retention
        assertTrue(a.events.all { it.type in EventCompaction.keptForever || it.day > cutoff })
        val historyEntries = a.weapons.values.sumOf { it.history.size }
        val keptByType = a.events.filter { it.day <= cutoff }.groupingBy { it.type }.eachCount().entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" }
        println("COMPACTION $DAYS days: events compacted=${a.events.size} unbounded=${b.events.size} keptForever=${a.events.count { it.day <= cutoff }} [$keptByType] weaponHistoryEntries=$historyEntries weapons=${a.weapons.size}")
    }

    @Test
    fun gazetteForTheRetentionWindowIsUnaffected() {
        val a = compacted
        val b = uncompacted
        val window = (lastEndDay(a) - retention + 1)..lastEndDay(a)
        assertEquals(retention, window.count())
        for (day in window) {
            assertEquals(b.eventsForDay(day), a.eventsForDay(day), "day $day")
            assertEquals(Gazette.headlines(b.eventsForDay(day)), Gazette.headlines(a.eventsForDay(day)), "day $day")
        }
        assertEquals(b.lastResolution, a.lastResolution)
        assertTrue(window.sumOf { a.eventsForDay(it).size } > 100, "the window must hold real days")
    }

    @Test
    fun historyGradeEventsSurviveForTheWholeRun() {
        val a = compacted
        val b = uncompacted
        val cutoff = lastEndDay(a) - retention
        val oldKept = b.events.filter { it.day <= cutoff && it.type in EventCompaction.keptForever }
        val retainedIds = a.events.map { it.id }.toSet()
        for (e in oldKept) assertTrue(e.id in retainedIds, "${e.type} on day ${e.day} (${e.id}) was dropped")
        val oldTypes = oldKept.map { it.type }.toSet()
        for (t in listOf(EventType.RUN_STARTED, EventType.HERO_DIED, EventType.MILESTONE, EventType.WORLD_EVENT, EventType.HERO_RETIRED)) {
            assertTrue(t in oldTypes, "the 400-day run should contain an old $t to prove it survives; had $oldTypes")
        }
        assertTrue(a.events.none { it.day <= cutoff && it.type !in EventCompaction.keptForever })
    }

    @Test
    fun compactedStateRoundTripsThroughSaveCodec() {
        val a = compacted
        val text = SaveCodec.encodeRun(a)
        val decoded = SaveCodec.decodeRun(text)
        assertEquals(a, decoded)
        assertEquals(text, SaveCodec.encodeRun(decoded), "encode(decode(x)) must be byte-equal")
        val unboundedText = SaveCodec.encodeRun(uncompacted)
        assertTrue(text.length < unboundedText.length, "the compacted save must be smaller")
        println("COMPACTION save bytes: compacted=${text.length} unbounded=${unboundedText.length} (weapons and heroes dominate a 400-day save)")
    }

    /** A save written before compaction existed (same schema, long log) loads unchanged and is compacted on its next End Day. */
    @Test
    fun uncompactedSaveFromOlderBuildLoadsAndCompactsOnNextEndDay() {
        val legacy = uncompacted
        assertEquals(1, SaveCodec.SCHEMA_VERSION, "the schema did not change for compaction")
        val loaded = SaveCodec.decodeRun(SaveCodec.encodeRun(legacy))
        assertEquals(legacy, loaded)
        val next = loaded.endDay()  // TestSupport engine: default config, retention 30, sieges enabled
        val expected = loaded.events.filter { EventCompaction.keeps(it, loaded.day, TestSupport.engine.config.eventRetentionDays) }
        assertTrue(next.events.size < loaded.events.size / 4, "${next.events.size} vs ${loaded.events.size}")
        assertEquals(expected, next.events.filter { it.day < loaded.day }, "surviving old events; new events carry day ${loaded.day}")
    }
}
