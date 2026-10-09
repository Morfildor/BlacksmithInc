package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.endDayId
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.Compatibility
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.rng.RngStream
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Section 6.7 of the major update plan: a run is admitted and stamped forward, or refused with reasons; never a crash. */
class CompatibilityTest {
    private fun admit(state: GameState) = Compatibility.admit(state, engine.content, engine.config)

    private fun problems(state: GameState): List<String> = assertIs<Compatibility.Result.Unsupported>(admit(state)).problems

    /** A day-1 run with one forged blade, so every kind of content reference has somewhere to live. */
    private fun runWithABlade(): Pair<GameState, Weapon> {
        val forged = engine.newRun(LegacyProfile(), 7).forgeAccepted(quickSword())
        return forged.state to forged.state.weapon(forged.forgedWeaponId!!)
    }

    @Test
    fun theV1FixtureIsAdmittedWithoutTouchingHistory() {
        val text = javaClass.getResource("/saves/v1_forced_seed4242_day61.json")?.readText() ?: error("missing fixture")
        val saved = SaveCodec.decodeRun(text)
        assertEquals(5, saved.balanceVersion, "written before the balance version was tracked; the schema 1 -> 2 step stamps 5")
        val admitted = assertIs<Compatibility.Result.Admitted>(admit(saved)).state
        assertEquals(GameEngine.RULES_VERSION, admitted.rulesVersion)
        assertEquals(engine.content.version, admitted.contentVersion)
        assertEquals(engine.config.version, admitted.balanceVersion)
        assertEquals(saved, admitted.copy(rulesVersion = saved.rulesVersion, contentVersion = saved.contentVersion, balanceVersion = saved.balanceVersion), "only the three version stamps differ")
        assertEquals(saved.events, admitted.events)
        assertEquals(saved.weapons.mapValues { it.value.history }, admitted.weapons.mapValues { it.value.history })
        assertEquals(saved.rng, admitted.rng)
        assertEquals(
            listOf(saved.nextWeaponSerial, saved.nextHeroSerial, saved.nextCommissionSerial, saved.nextEventSerial),
            listOf(admitted.nextWeaponSerial, admitted.nextHeroSerial, admitted.nextCommissionSerial, admitted.nextEventSerial),
        )
    }

    private fun v1Fixture(): GameState = SaveCodec.decodeRun(javaClass.getResource("/saves/v1_forced_seed4242_day61.json")?.readText() ?: error("missing fixture"))

    /** Rules and content are the engine's to enforce: an older run is brought forward by `admit`, never silently by `handle`. */
    @Test
    fun handleRejectsAnUnadmittedOlderRun() {
        val saved = v1Fixture()
        assertEquals(1, saved.rulesVersion)
        val rejected = engine.handle(saved, Command.EndDay(endDayId(saved)))
        assertEquals(GameError.IncompatibleRun(saved.rulesVersion, saved.contentVersion), assertIs<CommandOutcome.Rejected>(rejected).error)

        val fresh = engine.newRun(LegacyProfile(), 7)
        fun rejects(run: GameState) = assertIs<GameError.IncompatibleRun>(assertIs<CommandOutcome.Rejected>(engine.handle(run, quickSword())).error)
        rejects(fresh.copy(rulesVersion = GameEngine.RULES_VERSION - 1))
        rejects(fresh.copy(rulesVersion = GameEngine.RULES_VERSION + 1))
        rejects(fresh.copy(contentVersion = engine.content.version - 1))
        rejects(fresh.copy(contentVersion = engine.content.version + 1))
        // A rejected ended run does not slip through the "run ended" gate either.
        assertIs<GameError.IncompatibleRun>(assertIs<CommandOutcome.Rejected>(engine.handle(saved.copy(phase = Phase.ENDED), Command.EndDay(endDayId(saved)))).error)
        assertIs<CommandOutcome.Accepted>(engine.handle(fresh, quickSword()))
    }

    @Test
    fun admittedFixtureAcceptsAnEndDay() {
        val saved = v1Fixture()
        val admitted = assertIs<Compatibility.Result.Admitted>(admit(saved)).state
        val accepted = assertIs<CommandOutcome.Accepted>(engine.handle(admitted, Command.EndDay(endDayId(admitted))))
        val next = accepted.state
        assertEquals(61, accepted.resolution?.day)
        assertEquals(listOf(GameEngine.RULES_VERSION, engine.content.version, engine.config.version), listOf(next.rulesVersion, next.contentVersion, next.balanceVersion), "the stamps survive a command")
        assertTrue(endDayId(admitted).value in next.processedEndDayIds, "the End Day is recorded")
        assertTrue(next.isEnded || next.day == 62)
    }

    @Test
    fun aRunFromNewerRulesIsUnsupported() {
        val run = engine.newRun(LegacyProfile(), 7)
        assertTrue(problems(run.copy(rulesVersion = GameEngine.RULES_VERSION + 1)).single().startsWith("Run uses rules"))
        assertTrue(problems(run.copy(contentVersion = engine.content.version + 1)).single().startsWith("Run uses content"))
        // Older versions are carried forward, not refused.
        val older = assertIs<Compatibility.Result.Admitted>(admit(run.copy(rulesVersion = 0, contentVersion = 1))).state
        assertEquals(GameEngine.RULES_VERSION to engine.content.version, older.rulesVersion to older.contentVersion)
    }

    @Test
    fun anUnknownContentIdIsUnsupportedNotACrash() {
        val (run, blade) = runWithABlade()
        val hero = run.aliveHeroes().first()
        fun withBlade(w: Weapon) = run.copy(weapons = run.weapons + (w.id to w))
        fun withHero(h: Hero) = run.copy(heroes = run.heroes + (h.id to h))
        val commission = Commission(CommissionId("c1"), hero.id, WeaponFamilyId("glaive"), 40, 100, 1, 5, CommissionStatus.OFFERED)
        val cases = listOf(
            "Unknown family glaive" to withBlade(blade.copy(familyId = WeaponFamilyId("glaive"))),
            "Unknown family glaive" to run.copy(commissions = mapOf(commission.id to commission)),
            "Unknown material star_iron" to withBlade(blade.copy(coreId = MaterialId("star_iron"))),
            "Unknown material star_iron" to withBlade(blade.copy(catalystId = MaterialId("star_iron"))),
            "Unknown material star_iron" to run.copy(materials = run.materials + (MaterialId("star_iron") to 1)),
            "Unknown affix singing" to withBlade(blade.copy(affixes = listOf(AffixId("singing")))),
            "Unknown affix cracked_haft" to withBlade(blade.copy(flaws = listOf(AffixId("cracked_haft")))),
            "Unknown class bard" to withHero(hero.copy(classId = HeroClassId("bard"))),
            "Unknown trait moody" to withHero(hero.copy(traits = hero.traits + TraitId("moody"))),
            "Unknown blessing of_tides" to run.copy(blessings = listOf(ActiveBlessing(BlessingId("of_tides"), 9))),
            "Unknown blessing of_tides" to run.copy(pendingBlessingOffer = listOf(BlessingId("of_tides"))),
            "Unknown tool steam_hammer" to run.copy(tools = mapOf("steam_hammer" to 1)),
            "Unknown upgrade deep_cellar" to run.copy(legacy = run.legacy.copy(upgrades = mapOf(UpgradeId("deep_cellar") to 1))),
            "Unknown faction sea_raiders" to run.copy(factions = run.factions + (FactionId("sea_raiders") to FactionState(FactionId("sea_raiders"), 30))),
        )
        for ((expected, state) in cases) {
            val found = problems(state)
            assertTrue(found.any { it.startsWith(expected) }, "$expected not in $found")
        }
        assertIs<Compatibility.Result.Admitted>(admit(run))
    }

    @Test
    fun aMissingRngStreamIsReported() {
        val run = engine.newRun(LegacyProfile(), 7)
        val found = problems(run.copy(rng = run.rng.copy(streams = run.rng.streams - RngStream.EVENTS)))
        assertEquals(listOf("Missing RNG stream EVENTS"), found)
    }

    /** The extended invariants hold for a run played to its end: a fallen forge, hurt champions, hundreds of weapons. */
    @Test
    fun aRunPlayedToItsEndIsAdmitted() {
        val ended = SimulationDriver().playRun(LegacyProfile(), 4242, Policy.BALANCED_FAIR).second
        assertTrue(ended.isEnded)
        assertEquals(engine.config.version, assertIs<Compatibility.Result.Admitted>(admit(ended)).state.balanceVersion)
    }
}
