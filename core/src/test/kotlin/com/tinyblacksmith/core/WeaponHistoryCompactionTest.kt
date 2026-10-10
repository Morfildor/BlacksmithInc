package com.tinyblacksmith.core

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HistoryEntry
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.persistence.WeaponHistoryCompaction
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import com.tinyblacksmith.core.sim.Simulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * `Weapon.history` is bounded at End Day: the newest N combat entries (VICTORY, SIEGE) survive, everything else is
 * kept verbatim. The policy runs inside End Day, so the same seed must give identical gameplay with and without it;
 * only weapon histories may differ, and only by dropped combat entries. One 400-day forced-survival pair backs it.
 */
class WeaponHistoryCompactionTest {
    private companion object {
        const val DAYS = 400
        const val SEED = 77L
        val config = Simulator.forcedSurvival()
        val cap = config.weaponHistoryCap
        val combat = WeaponHistoryCompaction.compactable

        /** Same seed and policy; A compacts (default config), B never does (cap 0). Both still compact the event log. */
        val bounded: GameState by lazy { play(config) }
        val unbounded: GameState by lazy { play(config.copy(weaponHistoryCap = 0)) }

        fun play(cfg: BalanceConfig): GameState =
            SimulationDriver(GameEngine(config = cfg), maxDays = DAYS).playRun(LegacyProfile(), SEED, Policy.BALANCED_FAIR).second

        fun Weapon.combatEntries() = history.filter { it.kind in combat }
        fun stripHistories(weapons: Map<WeaponId, Weapon>) = weapons.mapValues { (_, w) -> w.copy(history = emptyList()) }
    }

    private fun e(kind: String, day: Int, vararg subjects: String) = HistoryEntry(1, day, kind, "$kind on day $day", subjects.toList())

    @Test
    fun keepsNewestCombatEntriesAndEveryOtherKindInOrder() {
        val history = listOf(
            e("FORGED", 1), e("SOLD", 2, "h1"), e("VICTORY", 3, "h1"), e("SIEGE", 5, "h1"), e("EQUIPPED", 6, "h2"),
            e("VICTORY", 7, "h2"), e("VICTORY", 8, "h2"), e("SIEGE", 10, "h2"), e("LOST", 11, "h2"),
        )
        assertSame(history, WeaponHistoryCompaction.compact(history, 0), "cap 0 disables compaction")
        assertSame(history, WeaponHistoryCompaction.compact(history, 5), "exactly at the cap: untouched")
        assertSame(history, WeaponHistoryCompaction.compact(history, 50))
        val three = WeaponHistoryCompaction.compact(history, 3)
        assertEquals(listOf("FORGED", "SOLD", "EQUIPPED", "VICTORY", "VICTORY", "SIEGE", "LOST"), three.map { it.kind })
        assertEquals(listOf(7, 8, 10), three.filter { it.kind in combat }.map { it.day }, "the oldest combat entries go first")
        val one = WeaponHistoryCompaction.compact(history, 1)
        assertEquals(listOf(1, 2, 6, 10, 11), one.map { it.day })
        // A living hero's newest siege line stays beyond the cap (the counter reads it); a fallen hero's does not.
        assertEquals(listOf(1, 2, 5, 6, 10, 11), WeaponHistoryCompaction.compact(history, 1, setOf("h1", "h2")).map { it.day })
        assertEquals(one, WeaponHistoryCompaction.compact(history, 1, setOf("h2", "h9")))
        val twice = history + e("SIEGE", 12, "h1")
        assertEquals(listOf(1, 2, 6, 11, 12), WeaponHistoryCompaction.compact(twice, 1, setOf("h1")).map { it.day }, "only the newest of them")
        val wallThenFight = listOf(e("FORGED", 1), e("SIEGE", 5, "h1"), e("VICTORY", 7, "h1"))
        assertSame(wallThenFight, WeaponHistoryCompaction.compact(wallThenFight, 1, setOf("h1")), "nothing to drop: the same list")
    }

    @Test
    fun mapCompactionReplacesOnlyWeaponsOverTheCap() {
        val s = unbounded
        val over = s.weapons.values.filter { it.combatEntries().size > cap }
        assertTrue(over.isNotEmpty(), "the 400-day run must have a weapon over the cap")
        val map = s.weapons.toMutableMap()
        WeaponHistoryCompaction.compact(map, cap)
        assertEquals(s.weapons.keys.toList(), map.keys.toList(), "order preserved")
        for ((id, w) in s.weapons) {
            if (w.combatEntries().size > cap) assertEquals(cap, map.getValue(id).combatEntries().size)
            else assertSame(w, map.getValue(id), "weapons under the cap keep their instance")
        }
    }

    @Test
    fun compactionDoesNotChangeGameplayOverFourHundredDays() {
        val a = bounded
        val b = unbounded
        assertEquals(DAYS + 1, a.day)
        assertEquals(b.copy(weapons = stripHistories(b.weapons)), a.copy(weapons = stripHistories(a.weapons)), "everything but weapon histories must match")
        assertEquals(b.events, a.events)
        assertEquals(b.weapons.keys.toList(), a.weapons.keys.toList())
        for ((id, w) in b.weapons) {
            val bounded = a.weapons.getValue(id)
            assertEquals(w.copy(history = emptyList()), bounded.copy(history = emptyList()), "kills/fame/location of ${id.value}")
            // Daily compaction equals applying the policy once at the end: newest combat entries, every other kind verbatim.
            assertEquals(WeaponHistoryCompaction.compact(w.history, cap), bounded.history, id.value)
            assertEquals(w.history.filter { it.kind !in combat }, bounded.history.filter { it.kind !in combat }, "ownership entries of ${id.value}")
            assertEquals(w.combatEntries().takeLast(cap), bounded.combatEntries(), "combat entries of ${id.value}")
        }
    }

    @Test
    fun readersSeeTheSameOwnersWithOrWithoutCompaction() {
        val a = bounded
        val b = unbounded
        for ((id, w) in b.weapons) {
            val bounded = a.weapons.getValue(id)
            // Legacy.closeRun: owners of a legend; WorldEvents.fallenOwnerName: who carried a lost blade.
            fun owners(x: Weapon) = x.history.filter { it.kind == "SOLD" || it.kind == "COMMISSION" }.flatMap { it.subjectIds }.distinct()
            fun fallen(x: Weapon) = x.history.lastOrNull { it.kind == "LOST" || it.kind == "SEIZED" }?.subjectIds?.firstOrNull()
            assertEquals(owners(w), owners(bounded), id.value)
            assertEquals(fallen(w), fallen(bounded), id.value)
        }
        assertTrue(b.weapons.values.any { w -> w.history.any { it.kind == "SOLD" || it.kind == "COMMISSION" } && w.combatEntries().size > cap }, "an owned weapon over the cap proves the reader check")
    }

    @Test
    fun historiesStayBoundedAndTheSaveShrinks() {
        val a = bounded
        val b = unbounded
        val maxBefore = b.weapons.values.maxOf { it.combatEntries().size }
        assertTrue(maxBefore > cap, "unbounded max $maxBefore must exceed the cap $cap")
        assertTrue(a.weapons.values.all { it.combatEntries().size <= cap })
        val entriesBefore = b.weapons.values.sumOf { it.history.size }
        val entriesAfter = a.weapons.values.sumOf { it.history.size }
        assertTrue(entriesAfter < entriesBefore)
        val bytesBefore = SaveCodec.encodeRun(b).length
        val bytesAfter = SaveCodec.encodeRun(a).length
        assertTrue(bytesAfter < bytesBefore)
        assertEquals(a, SaveCodec.decodeRun(SaveCodec.encodeRun(a)), "bounded state round-trips")
        val overCap = b.weapons.values.count { it.combatEntries().size > cap }
        println("WEAPON_HISTORY $DAYS days cap=$cap: entries unbounded=$entriesBefore bounded=$entriesAfter weapons=${a.weapons.size} overCap=$overCap maxCombat unbounded=$maxBefore bounded=${a.weapons.values.maxOf { it.combatEntries().size }} saveBytes unbounded=$bytesBefore bounded=$bytesAfter")
    }
}
