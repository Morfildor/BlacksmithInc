package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.admitted
import com.tinyblacksmith.core.TestSupport.endDayId
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.config.SaveGrowthConfig
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.legacy.Legacy
import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.EventRecord
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.HistoryEntry
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Phase
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponFamilyId
import com.tinyblacksmith.core.persistence.CommissionPruning
import com.tinyblacksmith.core.persistence.EventCompaction
import com.tinyblacksmith.core.persistence.ProcessedCommands
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.persistence.WeaponHistoryCompaction
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import com.tinyblacksmith.core.sim.Simulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The four rules of `BalanceConfig.saveGrowth` (T6.3a, F10): closed commissions, processed End Day IDs, routine
 * kept-forever records and the everyday lines of a blade's history. Each runs inside End Day, so the same seed must
 * give the same gameplay with and without it; only the trimmed field may differ. One 400-day forced-survival run with
 * every rule on is compared with four runs that each switch one rule off, in the style of [WeaponPruningTest].
 */
class SaveGrowthTest {
    private companion object {
        const val DAYS = 400
        const val SEED = 77L
        val config = Simulator.forcedSurvival()
        val rules = config.saveGrowth

        val bounded: GameState by lazy { play(rules) }
        val keepsCommissions: GameState by lazy { play(rules.copy(commissionRetentionDays = 0)) }
        val keepsIds: GameState by lazy { play(rules.copy(processedEndDayIdsKept = 0)) }
        val keepsRoutine: GameState by lazy { play(rules.copy(routineEventRetentionDays = 0)) }
        val keepsEveryday: GameState by lazy { play(rules.copy(weaponEverydayHistoryCap = 0)) }

        fun play(growth: SaveGrowthConfig): GameState =
            SimulationDriver(GameEngine(config = config.copy(saveGrowth = growth)), maxDays = DAYS).playRun(LegacyProfile(), SEED, Policy.BALANCED_ACTIVE).second

        /** The last End Day ran on day `state.day - 1`. */
        fun lastEndDay(state: GameState) = state.day - 1

        fun Weapon.everydayLines() = history.count { it.kind in WeaponHistoryCompaction.everyday && it.kind != "EQUIPPED" }
        fun GameState.withoutHistories() = copy(weapons = weapons.mapValues { (_, w) -> w.copy(history = emptyList(), ownerIds = emptyList()) })
    }

    // ---- closed commissions ------------------------------------------------------------------------------------

    @Test
    fun onlyClosedCommissionsPastTheWindowArePrunable() {
        fun c(status: CommissionStatus, deadline: Int) = Commission(CommissionId("c1"), HeroId("h1"), WeaponFamilyId("sword"), 35, 100, 1, deadline, status)
        val today = 100
        for (closed in listOf(CommissionStatus.COMPLETED, CommissionStatus.EXPIRED, CommissionStatus.DECLINED)) {
            assertTrue(CommissionPruning.prunable(c(closed, 70), today, 30), "$closed, 30 days past its deadline")
            assertFalse(CommissionPruning.prunable(c(closed, 71), today, 30), "$closed, still inside the window")
            assertFalse(CommissionPruning.prunable(c(closed, 1), today, 0), "0 switches the rule off")
        }
        for (open in listOf(CommissionStatus.OFFERED, CommissionStatus.ACCEPTED)) assertFalse(CommissionPruning.prunable(c(open, 1), today, 30), "$open is never pruned")
    }

    @Test
    fun closedCommissionsLeaveTheSaveAndNothingElseMoves() {
        val a = bounded
        val b = keepsCommissions
        assertEquals(DAYS + 1, a.day)
        assertEquals(b.copy(commissions = emptyMap()), a.copy(commissions = emptyMap()), "everything but the commissions map must match")
        val gone = b.commissions.keys - a.commissions.keys
        assertEquals((b.commissions - gone).entries.toList(), a.commissions.entries.toList(), "surviving commissions are identical and in the same order")
        assertEquals(b.commissions.values.filter { CommissionPruning.prunable(it, lastEndDay(a), rules.commissionRetentionDays) }.map { it.id }.toSet(), gone)
        assertTrue(gone.all { b.commissions.getValue(it).status !in setOf(CommissionStatus.OFFERED, CommissionStatus.ACCEPTED) })
        assertTrue(b.commissions.size > 60, "the unbounded map must be large to make the comparison meaningful: ${b.commissions.size}")
        assertTrue(a.commissions.size <= 30, "a month of requests at most: ${a.commissions.size}")
        println("SAVE_GROWTH commissions over $DAYS days: kept=${a.commissions.size} unbounded=${b.commissions.size}")
    }

    // ---- processed End Day IDs ---------------------------------------------------------------------------------

    @Test
    fun theNewestProcessedIdsAreKeptInOrder() {
        val ids = (1..10).mapTo(LinkedHashSet()) { "run:day$it" }
        ProcessedCommands.trim(ids, 0)
        assertEquals(10, ids.size, "0 switches the rule off")
        ProcessedCommands.trim(ids, 10)
        assertEquals(10, ids.size, "exactly at the bound: untouched")
        ProcessedCommands.trim(ids, 3)
        assertEquals(listOf("run:day8", "run:day9", "run:day10"), ids.toList())
    }

    @Test
    fun processedIdsStayBoundedAndTheLatestEndDayStillRetries() {
        val a = bounded
        val b = keepsIds
        assertEquals(b.copy(processedEndDayIds = emptySet()), a.copy(processedEndDayIds = emptySet()), "everything but the processed IDs must match")
        assertEquals(DAYS, b.processedEndDayIds.size)
        assertEquals(rules.processedEndDayIdsKept, a.processedEndDayIds.size)
        assertEquals(b.processedEndDayIds.toList().takeLast(rules.processedEndDayIdsKept), a.processedEndDayIds.toList(), "the newest, in the order they were processed")
        // The order survives the save, and a retry of the End Day just processed returns its stored resolution without simulating.
        val loaded = SaveCodec.decodeRun(SaveCodec.encodeRun(a))
        assertEquals(a.processedEndDayIds.toList(), loaded.processedEndDayIds.toList())
        val retry = assertIs<CommandOutcome.Accepted>(GameEngine(config = config).handle(loaded, Command.EndDay(a.lastResolution!!.commandId)))
        assertSame(loaded, retry.state)
        assertEquals(a.lastResolution, retry.resolution)
    }

    // ---- routine kept-forever records ---------------------------------------------------------------------------

    @Test
    fun routineRecordsAreKeptForTheirWindowOnly() {
        fun e(id: Int, day: Int, type: EventType) = EventRecord("e$id", 1, day, type, 1, "t")
        val today = 100
        val events = mutableListOf(
            e(1, 1, EventType.HERO_DIED),        // history: kept for the run
            e(2, 60, EventType.WORLD_EVENT),     // routine, 40 days old: goes with a 30-day window
            e(3, 60, EventType.HERO_ARRIVED),
            e(4, 71, EventType.WORLD_EVENT),     // inside the window
            e(5, 60, EventType.WEAPON_SOLD),     // ordinary and old
        )
        val all = events.toList()
        EventCompaction.compact(events, today, 30, 0)
        assertEquals(listOf("e1", "e2", "e3", "e4"), events.map { it.id }, "0 keeps routine records for the whole run, as before")
        EventCompaction.compact(events, today, 30, 45)
        assertEquals(listOf("e1", "e2", "e3", "e4"), events.map { it.id }, "a longer window of their own")
        EventCompaction.compact(events, today, 30, 30)
        assertEquals(listOf("e1", "e4"), events.map { it.id })
        val untouched = all.toMutableList()
        EventCompaction.compact(untouched, today, 0, 30)
        assertEquals(all, untouched, "with the event log uncompacted nothing goes")
    }

    @Test
    fun routineRecordsLeaveTheLogAndNothingElseMoves() {
        val a = bounded
        val b = keepsRoutine
        assertEquals(b.copy(events = emptyList()), a.copy(events = emptyList()), "everything but the event log must match")
        assertEquals(b.nextEventSerial, a.nextEventSerial, "every event is still emitted and numbered")
        assertEquals(b.events.filter { EventCompaction.keeps(it, lastEndDay(a), config.eventRetentionDays, rules.routineEventRetentionDays) }, a.events)
        val cutoff = lastEndDay(a) - rules.routineEventRetentionDays
        val oldRoutine = b.events.count { it.type in EventCompaction.routine && it.day <= cutoff }
        assertTrue(oldRoutine > 100, "the unbounded log must hold many old arrivals and world events: $oldRoutine")
        assertTrue(a.events.none { it.type in EventCompaction.routine && it.day <= cutoff })
        // History proper is still kept for the whole run.
        for (t in listOf(EventType.RUN_STARTED, EventType.HERO_DIED, EventType.HERO_RETIRED, EventType.MILESTONE))
            assertEquals(b.events.filter { it.type == t }, a.events.filter { it.type == t }, "$t")
        println("SAVE_GROWTH events over $DAYS days: kept=${a.events.size} with routine records kept=${b.events.size} (old routine records dropped=$oldRoutine)")
    }

    // ---- everyday lines of a blade's history ----------------------------------------------------------------------

    private fun e(kind: String, day: Int, vararg subjects: String, era: Int = 1) = HistoryEntry(era, day, kind, "$kind on day $day", subjects.toList())

    private fun blade(history: List<HistoryEntry>) = bounded.weapons.values.first().copy(history = history, ownerIds = emptyList())

    @Test
    fun theNewestEverydayLinesStayAndTheHoldersAreRemembered() {
        // Sold to h1..h20 in turn, each sale followed by its EQUIPPED line and a trade-in; h3 lost it once on the way.
        val history = listOf(e("FORGED", 1)) + (1..20).flatMap { n ->
            listOf(e("SOLD", n * 2, "h$n"), e("EQUIPPED", n * 2, "h$n"), e("TRADED_IN", n * 2 + 1, "h$n")) + (if (n == 3) listOf(e("LOST", 7, "h3"), e("RECOVERED", 7, "h3")) else emptyList())
        }
        val w = blade(history)
        assertSame(w, WeaponHistoryCompaction.compactEveryday(w, 0, 1, emptySet()), "0 switches the rule off")
        assertSame(w, WeaponHistoryCompaction.compactEveryday(w, 40, 1, emptySet()), "exactly at the cap: untouched")
        val cap = 12
        val cut = WeaponHistoryCompaction.compactEveryday(w, cap, 1, setOf("h5"))
        val everyday = WeaponHistoryCompaction.everyday
        assertEquals(history.filter { it.kind !in everyday }, cut.history.filter { it.kind !in everyday }, "what the blade is and how it was lost stay")
        val counted = cut.history.filter { it.kind in everyday && it.kind != "EQUIPPED" }
        assertEquals(history.filter { it.kind in everyday && it.kind != "EQUIPPED" }.takeLast(cap), counted.takeLast(cap), "the newest everyday lines stay")
        assertEquals(listOf("SOLD:h1", "SOLD:h5", "TRADED_IN:h5"), counted.dropLast(cap).map { "${it.kind}:${it.subjectIds.first()}" }, "beside them only the first owner and a living hero's lines")
        assertEquals(listOf("h5"), cut.history.filter { it.kind == "EQUIPPED" && it.day < counted.takeLast(cap).first().day }.flatMap { it.subjectIds }, "an old EQUIPPED line goes with its hand-over")
        assertEquals(cut.history.sortedBy { history.indexOf(it) }, cut.history, "order kept")
        assertEquals((1..20).map { HeroId("h$it") }, cut.ownerIds)
        assertEquals(Legacy.holders(w, 1), Legacy.holders(cut, 1), "who held it is answered as before")
        // A second pass, later, changes nothing more; new lines are added to the remembered holders in order.
        assertSame(cut, WeaponHistoryCompaction.compactEveryday(cut, cap, 1, setOf("h5")))
        val later = cut.copy(history = cut.history + e("SOLD", 50, "h21") + e("SOLD", 52, "h2"))
        assertEquals((1..21).map { HeroId("h$it") }, Legacy.holders(later, 1))
        // The cap is never below the Legend Board's story length.
        assertEquals(WeaponHistoryCompaction.compactEveryday(w, Legacy.STORY_MAX, 1, emptySet()), WeaponHistoryCompaction.compactEveryday(w, 1, 1, emptySet()))
    }

    @Test
    fun everydayLinesStayBoundedAndNothingElseMoves() {
        val a = bounded
        val b = keepsEveryday
        assertEquals(b.withoutHistories(), a.withoutHistories(), "everything but weapon histories must match")
        assertTrue(b.weapons.values.all { it.ownerIds.isEmpty() })
        for ((id, w) in a.weapons) {
            val full = b.weapons.getValue(id)
            assertEquals(Legacy.holders(full, a.era), Legacy.holders(w, a.era), "holders of ${id.value}")
            assertEquals(full.history.filter { it.kind !in WeaponHistoryCompaction.everyday }, w.history.filter { it.kind !in WeaponHistoryCompaction.everyday }, "other lines of ${id.value}")
            assertTrue(full.history.containsAll(w.history), "nothing is invented for ${id.value}")
        }
        // What the Legend Board would take from each blade is the same list, line for line.
        val boardA = Legacy.closeRun(a.copy(phase = Phase.ENDED), GameEngine().content, config.copy(legendFameThreshold = 0, legacyTracks = config.legacyTracks.copy(legendsPerRun = Int.MAX_VALUE)))
        val boardB = Legacy.closeRun(b.copy(phase = Phase.ENDED), GameEngine().content, config.copy(legendFameThreshold = 0, legacyTracks = config.legacyTracks.copy(legendsPerRun = Int.MAX_VALUE)))
        assertEquals(boardB.legends, boardA.legends)
        assertEquals(a.weapons.size, boardA.legends.size)
        val living = a.aliveHeroes().map { it.id.value }.toSet()
        val longestFull = b.weapons.values.maxOf { it.everydayLines() }
        val longest = a.weapons.values.maxOf { w -> w.history.count { it.kind in WeaponHistoryCompaction.everyday && it.kind != "EQUIPPED" && it.subjectIds.none { s -> s in living } } }
        assertTrue(longestFull > rules.weaponEverydayHistoryCap + 1, "the run must hold a blade with more everyday lines than the cap: $longestFull")
        assertTrue(longest <= rules.weaponEverydayHistoryCap + 1, "the cap, plus the first owner: $longest")
        println("SAVE_GROWTH everyday history over $DAYS days: longest kept=$longest unbounded=$longestFull; history lines kept=${a.weapons.values.sumOf { it.history.size }} unbounded=${b.weapons.values.sumOf { it.history.size }}")
    }

    // ---- saves ----------------------------------------------------------------------------------------------------

    @Test
    fun theBoundedSaveRoundTripsAndIsSmaller() {
        val a = bounded
        val text = SaveCodec.encodeRun(a)
        assertEquals(a, SaveCodec.decodeRun(text))
        assertEquals(text, SaveCodec.encodeRun(SaveCodec.decodeRun(text)), "encode(decode(x)) must be byte-equal")
        val sizes = listOf(keepsCommissions, keepsIds, keepsRoutine, keepsEveryday).map { SaveCodec.encodeRun(it).length }
        assertTrue(sizes.take(3).all { it > text.length }, "each of the first three rules makes the save smaller: ${text.length} against $sizes")
        assertTrue(sizes[3] >= text.length)
        println("SAVE_GROWTH save bytes at day $DAYS: all rules=${text.length}; without the commission rule=${sizes[0]}, the ID rule=${sizes[1]}, the routine-record rule=${sizes[2]}, the everyday-history rule=${sizes[3]}")
    }

    /**
     * Saves written before the rules existed (schemas 1, 2 and 3, and the same runs as schema 4 wrote them before this
     * task: no `ownerIds`) load unchanged, answer "who held this blade" from their history, and are trimmed on their next
     * End Day to the same outcome as an engine with every rule off.
     */
    @Test
    fun olderSavesLoadAndAreTrimmedOnTheirNextEndDayToTheSameOutcome() {
        val off = GameEngine(config = BalanceConfig.DEFAULT.copy(saveGrowth = SaveGrowthConfig(0, 0, 0, 0)))
        for (name in listOf("v1_forced_seed4242_day61.json", "v1_release060_active_seed4242_day61.json", "v2_forced_seed4242_day61.json", "v2_balance6_expert_seed4242_day33.json", "v3_forced_seed4242_day61.json")) {
            val decoded = SaveCodec.decodeRun(javaClass.getResource("/saves/$name")?.readText() ?: error("missing $name"))
            assertTrue(decoded.weapons.values.all { it.ownerIds.isEmpty() }, name)
            assertEquals(decoded, SaveCodec.decodeRun(SaveCodec.encodeRun(decoded)), "$name: the schema-4 form, still without ownerIds")
            val loaded = decoded.admitted()
            assertEquals(decoded.day - 1, loaded.processedEndDayIds.size, "$name: loading trims nothing")
            val with = assertIs<CommandOutcome.Accepted>(TestSupport.engine.handle(loaded, Command.EndDay(endDayId(loaded)))).state
            val without = assertIs<CommandOutcome.Accepted>(off.handle(loaded, Command.EndDay(endDayId(loaded)))).state
            assertEquals(emptyList(), Invariants.check(with, TestSupport.engine.config, TestSupport.engine.shelfSlots(with), TestSupport.engine.content), name)
            fun GameState.outcome() = withoutHistories().copy(commissions = emptyMap(), processedEndDayIds = emptySet(), events = emptyList())
            assertEquals(without.outcome(), with.outcome(), name)
            assertEquals(minOf(decoded.day, rules.processedEndDayIdsKept), with.processedEndDayIds.size, name)
            assertEquals(decoded.day, without.processedEndDayIds.size, name)
            assertEquals(without.commissions.filterValues { !CommissionPruning.prunable(it, loaded.day, rules.commissionRetentionDays) }, with.commissions, name)
            for ((id, w) in with.weapons) assertEquals(Legacy.holders(without.weapons.getValue(id), with.era), Legacy.holders(w, with.era), "$name ${id.value}")
        }
    }
}
