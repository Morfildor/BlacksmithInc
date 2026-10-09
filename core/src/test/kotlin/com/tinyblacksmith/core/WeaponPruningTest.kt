package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponLocation
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.persistence.WeaponPruning
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import com.tinyblacksmith.core.sim.Simulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Blades gone for good (salvaged, shattered, given to the watch, sold to a collector) leave the weapons map a
 * retention window later. The policy runs inside End Day, so the same seed must give identical gameplay with and
 * without it; only the weapons map may differ, and only by weapons no rule can read. One 400-day forced-survival pair
 * under the active smith (who salvages and arms the watch) backs it.
 */
class WeaponPruningTest {
    private companion object {
        const val DAYS = 400
        const val SEED = 77L
        val config = Simulator.forcedSurvival()
        val keep = config.weaponRetentionDays
        val legendFame = config.legendFameThreshold

        /** Same seed and policy; A prunes (default config), B never does (retention 0). */
        val pruned: GameState by lazy { play(config) }
        val unpruned: GameState by lazy { play(config.copy(weaponRetentionDays = 0)) }

        fun play(cfg: BalanceConfig): GameState =
            SimulationDriver(GameEngine(config = cfg), maxDays = DAYS).playRun(LegacyProfile(), SEED, Policy.BALANCED_ACTIVE).second
    }

    private fun blade(): Weapon {
        val out = TestSupport.engine.newRun(LegacyProfile(), 5L).withMaterials().forgeAccepted(quickSword())
        return out.state.weapon(out.forgedWeaponId!!).copy(fame = 0, signatureId = null)
    }

    @Test
    fun onlyBladesGoneForGoodAndForgottenArePrunable() {
        val w = blade()
        val today = 100
        val old = today - keep
        fun prunable(x: Weapon, days: Int = keep) = WeaponPruning.prunable(x, today, days, legendFame)
        assertTrue(prunable(w.copy(location = WeaponLocation.Destroyed(old))))
        assertTrue(prunable(w.copy(location = WeaponLocation.Lost(old, "given to the town watch"))))
        assertTrue(prunable(w.copy(location = WeaponLocation.Lost(old, "sold to a collector"))))
        assertFalse(prunable(w.copy(location = WeaponLocation.Destroyed(old + 1))), "still inside the window")
        assertFalse(prunable(w.copy(location = WeaponLocation.Lost(old, "seized"))), "a seized blade can come home")
        assertFalse(prunable(w.copy(location = WeaponLocation.Lost(old, "lost with Mira Ashwood"))), "a blade lost with a hero can come home")
        assertFalse(prunable(w.copy(location = WeaponLocation.Lost(old, "a reason added later"))), "unknown reasons are kept")
        assertFalse(prunable(w.copy(location = WeaponLocation.Storage)))
        assertFalse(prunable(w.copy(location = WeaponLocation.Shelf(10))))
        assertFalse(prunable(w.copy(location = WeaponLocation.Owned(HeroId("h1"), equipped = true))))
        assertFalse(prunable(w.copy(location = WeaponLocation.Destroyed(old), fame = legendFame)), "a Legend Board candidate stays")
        assertFalse(prunable(w.copy(location = WeaponLocation.Destroyed(old), signatureId = "any")), "a signature weapon stays")
        assertFalse(prunable(w.copy(location = WeaponLocation.Destroyed(old)), days = 0), "0 disables pruning")
    }

    @Test
    fun pruningDoesNotChangeGameplayOverFourHundredDays() {
        val a = pruned
        val b = unpruned
        assertEquals(DAYS + 1, a.day)
        assertEquals(b.copy(weapons = emptyMap()), a.copy(weapons = emptyMap()), "everything but the weapons map must match")
        val gone = b.weapons.keys - a.weapons.keys
        assertTrue(gone.isNotEmpty(), "a 400-day active run must prune something")
        assertEquals((b.weapons - gone).entries.toList(), a.weapons.entries.toList(), "surviving weapons are identical and in the same order")
        // Daily pruning equals applying the rule once at the end: exactly the prunable weapons are gone.
        assertEquals(b.weapons.values.filter { WeaponPruning.prunable(it, DAYS, keep, legendFame) }.map { it.id }.toSet(), gone)
        assertEquals(b.weapons.values.filter { it.fame >= legendFame }, a.weapons.values.filter { it.fame >= legendFame }, "Legend Board candidates")
        val goneLocations = gone.map { b.weapons.getValue(it).location }
        assertTrue(goneLocations.any { it is WeaponLocation.Destroyed }, "salvaged blades were pruned")
        assertTrue(goneLocations.any { it is WeaponLocation.Lost && it.reason == "given to the town watch" }, "the watch's blades were pruned")
    }

    /** Guards the reason strings: a new way to lose a blade must be classified here as terminal or returnable. */
    @Test
    fun everyLostReasonIsClassified() {
        val reasons = unpruned.weapons.values.mapNotNull { (it.location as? WeaponLocation.Lost)?.reason }.toSet()
        val unclassified = reasons.filterNot { it in WeaponPruning.terminalReasons || it == "seized" || it.startsWith("lost with ") }
        assertTrue(unclassified.isEmpty(), "unclassified reasons: $unclassified")
    }

    @Test
    fun theSaveShrinksRoundTripsAndKeepsPlaying() {
        val a = pruned
        val b = unpruned
        val bytesBefore = SaveCodec.encodeRun(b).length
        val bytesAfter = SaveCodec.encodeRun(a).length
        assertTrue(a.weapons.size < b.weapons.size)
        assertTrue(bytesAfter < bytesBefore)
        assertEquals(a, SaveCodec.decodeRun(SaveCodec.encodeRun(a)), "pruned state round-trips")
        assertIs<CommandOutcome.Accepted>(GameEngine(config = config).handle(a, Command.EndDay(CommandId("${a.runId.value}:day${a.day}"))))
        println("WEAPON_PRUNING $DAYS days keep=$keep: weapons unpruned=${b.weapons.size} pruned=${a.weapons.size} saveBytes unpruned=$bytesBefore pruned=$bytesAfter")
    }
}
