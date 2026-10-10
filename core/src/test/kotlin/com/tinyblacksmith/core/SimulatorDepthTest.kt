package com.tinyblacksmith.core

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.sim.BotCounter
import com.tinyblacksmith.core.sim.DepthProbes
import com.tinyblacksmith.core.sim.DepthSummary
import com.tinyblacksmith.core.sim.EncounterPref
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.PolicySummary
import com.tinyblacksmith.core.sim.RelicPref
import com.tinyblacksmith.core.sim.SimulationDriver
import com.tinyblacksmith.core.sim.Simulator
import com.tinyblacksmith.core.sim.applySet
import com.tinyblacksmith.core.sim.defaultEncounters
import com.tinyblacksmith.core.sim.defaultRelic
import com.tinyblacksmith.core.sim.withoutDepth
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The simulator's play of morning visitors, relics and siege traits: preferences, wiring, metrics, the matched baseline and the exploit probes. */
class SimulatorDepthTest {
    private val content = LaunchContent.catalog
    private val engine = GameEngine(content)

    private fun driver(days: Int = 45, encounters: EncounterPref? = null, relic: RelicPref? = null) = SimulationDriver(engine, maxDays = days, encounters = encounters, relic = relic)

    private fun depthOf(policy: Policy, seeds: LongRange, days: Int = 45, encounters: EncounterPref? = null, relic: RelicPref? = null) =
        seeds.map { assertNotNull(driver(days, encounters, relic).playRun(LegacyProfile(), it, policy).first.depth) }

    @Test
    fun everyPreferenceAnswersWithAnOpenOptionAndDeclineChangesNothing() {
        val mornings = ArrayList<GameState>()
        for (seed in 1L..3L) SimulationDriver(engine, maxDays = 24, onDayResolved = { s, _ -> if (!s.isEnded) mornings += s }).playRun(LegacyProfile(), seed, Policy.BALANCED_FAIR)
        var answers = 0
        var blockedSeen = 0
        val optionsSeen = HashSet<String>()
        for (morning in mornings) for (def in content.encounters.filter { !it.followUp }) {
            // As it stands, with an empty purse, and with the day's strength spent: the last two close the paid answers.
            for (variant in listOf(morning, morning.copy(gold = 0), morning.copy(energy = 0, overworkToday = engine.config.maxOverworkPerDay))) {
                val offered = Encounters.force(variant, content, engine.config, def.id) ?: continue
                for (pref in EncounterPref.entries) {
                    var s = offered
                    // Twice: the merchant's inspection leaves the card open for a second answer.
                    for (step in 0..1) {
                        val view = engine.encounterView(s)?.takeIf { it.instance.isOpen } ?: break
                        view.options.filter { it.id != Encounters.PASS }.forEach { optionsSeen += "${def.id}/${it.id}"; if (it.blocked != null) blockedSeen++ }
                        val pick = pref.choose(view, s, engine)
                        val option = assertNotNull(view.options.firstOrNull { it.id == pick }, "$pref picked '$pick', which ${def.id} does not offer")
                        assertNull(option.blocked, "$pref picked the blocked '$pick' of ${def.id}")
                        val out = engine.handle(s, Command.ResolveEncounter(view.instance.id, pick, CommandId("test:$step:$pick")))
                        assertTrue(out is CommandOutcome.Accepted, "$pref: '$pick' of ${def.id} was rejected: $out")
                        if (pref == EncounterPref.DECLINE) {
                            assertEquals(Encounters.PASS, pick)
                            assertEquals(s.gold, out.state.gold)
                            assertEquals(s.materials, out.state.materials)
                            assertEquals(s.energy, out.state.energy)
                            assertEquals(s.weapons, out.state.weapons)
                            assertEquals(s.commissions, out.state.commissions)
                        }
                        s = out.state
                        answers++
                    }
                }
            }
        }
        assertTrue(answers > 1000 && blockedSeen > 100, "too few cases: answers=$answers blocked options=$blockedSeen")
        val universe = DepthSummary.OPTIONS.flatMap { (enc, opts) -> opts.map { "$enc/$it" } }.toSet()
        assertTrue(universe.containsAll(optionsSeen), "the dead-option universe misses ${optionsSeen - universe}")
    }

    @Test
    fun everyPolicyPlaysTheDepthSystemsWithLegalCommands() {
        for (policy in Policy.entries) for (seed in 1L..2L) {
            val (stats, state) = driver().playRun(LegacyProfile(), seed, policy)
            val depth = assertNotNull(stats.depth)
            assertEquals(0, depth.rejected, "$policy seed $seed had a rejected depth command")
            stats.bot?.let { assertEquals(0, it[BotCounter.REJECTED], "$policy seed $seed had a rejected command") }
            assertEquals(policy.defaultEncounters().name.lowercase(), depth.encounters)
            assertEquals(policy.defaultRelic().label, depth.relic)
            assertTrue(state.weapons.values.none { it.promisedTo != null && it.isListed }, "$policy listed a promised blade")
        }
    }

    @Test
    fun everyPreferenceAndRelicChoicePlaysLegallyAndIsDeterministic() {
        val relics = listOf(RelicPref.First, RelicPref.None, RelicPref.Adaptive) + content.relics.map { RelicPref.Fixed(it.id) }
        for (policy in listOf(Policy.EXPERT, Policy.BALANCED_ACTIVE, Policy.OVERWORK)) {
            for (pref in EncounterPref.entries) check(policy, pref, null)
            for (relic in relics) check(policy, null, relic)
        }
    }

    private fun check(policy: Policy, pref: EncounterPref?, relic: RelicPref?) {
        val (stats, state) = driver(40, pref, relic).playRun(LegacyProfile(), 3, policy)
        val depth = assertNotNull(stats.depth)
        assertEquals(0, depth.rejected, "$policy $pref $relic had a rejected depth command")
        assertEquals(stats, driver(40, pref, relic).playRun(LegacyProfile(), 3, policy).first, "$policy $pref $relic is not deterministic")
        if (pref == EncounterPref.DECLINE) assertTrue(depth.chosen.keys.all { it.endsWith("/${Encounters.PASS}") } && depth.visitorGoldIn == 0 && depth.visitorGoldOut == 0)
        if (relic == RelicPref.None) assertTrue(state.relics.isEmpty())
        if (relic is RelicPref.Fixed) assertTrue(state.relics.all { it.id == relic.id }, "$policy took another relic than ${relic.id}")
    }

    @Test
    fun defaultsFollowThePolicy() {
        assertEquals(EncounterPref.DECLINE, Policy.PASSIVE.defaultEncounters())
        assertEquals(RelicPref.None, Policy.PASSIVE.defaultRelic())
        assertTrue(Policy.CLASSIC.filter { it != Policy.PASSIVE }.all { it.defaultEncounters() == EncounterPref.FIRST && it.defaultRelic() == RelicPref.First })
        assertEquals(EncounterPref.DEFENSE, Policy.SIEGE_PREP.defaultEncounters())
        assertEquals(EncounterPref.CASH, Policy.SPENDTHRIFT.defaultEncounters())
        for (p in listOf(Policy.EXPERT, Policy.EXPERT_ACTIVE)) { assertEquals(EncounterPref.ADAPTIVE, p.defaultEncounters()); assertEquals(RelicPref.Adaptive, p.defaultRelic()) }
        val passive = depthOf(Policy.PASSIVE, 1L..3L)
        assertTrue(passive.all { it.relicsHeld.isEmpty() && it.relicsDeclined == it.relicOffers && it.visitorMornings > 0 })
    }

    @Test
    fun botsUseTheRelicsTheyHoldAndKeepWhatTheyPromise() {
        val seeds = 1L..8L
        assertTrue(depthOf(Policy.OVERWORK, seeds, relic = RelicPref.Fixed(Depth.ASHEN_BELLOWS)).sumOf { it.bellowsForges } > 0, "the bellows were never blown")
        assertTrue(depthOf(Policy.BALANCED_ACTIVE, seeds, relic = RelicPref.Fixed(Depth.SALVAGERS_CRUCIBLE)).sumOf { it.crucibleReturns } > 0, "the crucible never returned an augment")
        assertTrue(depthOf(Policy.BALANCED_FAIR, seeds, relic = RelicPref.Fixed(Depth.TEMPERING_LEDGER)).sumOf { it.ledgerQuality } > 0, "the ledger never improved a forge")
        assertTrue(depthOf(Policy.BALANCED_FAIR, seeds, relic = RelicPref.Fixed(Depth.COLLECTORS_SEAL)).sumOf { it.sealsEarned } > 0, "no seal was earned")
        // A policy that neither overworks nor adapts holds the bellows without blowing them.
        assertEquals(0, depthOf(Policy.BALANCED_FAIR, seeds, encounters = EncounterPref.FIRST, relic = RelicPref.First).sumOf { it.bellowsForges })
        // The first open answer takes the wager and the pledge; the bot then forges what each asked for.
        val first = depthOf(Policy.BALANCED_FAIR, seeds)
        assertTrue(first.sumOf { it.chosen["${Depth.SMITHS_WAGER}/wager"] ?: 0 } > 0 && first.sumOf { it.wagersWon } > 0, "no wager was taken and won")
        assertTrue(first.sumOf { it.chosen["${Depth.BLADE_FOR_THE_WALL}/pledge"] ?: 0 } > 0 && first.sumOf { it.pledgesDelivered } > 0, "no pledge was taken and delivered")
        // The defense answers arm the watch for the council and are paid for it.
        assertTrue(depthOf(Policy.SIEGE_PREP, seeds).sumOf { it.bountyGold } > 0, "no bounty was ever paid")
    }

    @Test
    fun countsOnAFixedSeedEqualTheRunsOwnRecords() {
        val seed = 5L
        val mornings = ArrayList<GameState>()
        val stats = SimulationDriver(engine, maxDays = 30, onDayResolved = { s, _ -> mornings += s }).playRun(LegacyProfile(), seed, Policy.BALANCED_ACTIVE).first
        val depth = assertNotNull(stats.depth)
        // The run's last state is a morning nobody played when the run stopped at its day cap.
        val played = if (stats.ended) mornings.filter { !it.isEnded } else mornings.dropLast(1)
        val visitors = played.mapNotNull { it.encounter }
        assertTrue(visitors.size > 5, "seed $seed should have visitors")
        assertEquals(visitors.size, depth.visitorMornings)
        assertEquals(visitors.groupingBy { it.defId }.eachCount().toSortedMap(), depth.offers)
        // Every visitor got exactly one closing answer (the inspection is the one answer that does not close).
        assertEquals(depth.visitorMornings, depth.chosen.filterKeys { !it.endsWith("/inspect") }.values.sum() + depth.expired.values.sum())
        val events = mornings.mapNotNull { it.lastResolution }.flatMap { it.events }
        assertEquals(events.count { it.type == EventType.RELIC_CHOSEN }, depth.relicsChosen.values.sum())
        assertEquals(events.count { it.type == EventType.SIEGE_WON }, depth.siegesWon.values.sum())
        assertEquals(events.count { it.type == EventType.SIEGE_LOST }, depth.siegesLost.values.sum())
        assertEquals(stats.siegesSurvived to stats.siegesLost, depth.siegesWon.values.sum() to depth.siegesLost.values.sum())
        assertEquals(events.count { it.type == EventType.WEAPON_SALVAGED && "augment" in it.data }, depth.crucibleReturns)
    }

    @Test
    fun withoutDepthThereIsNoVisitorRelicOrTraitAndTheOldEventsFireAgain() {
        val (plainContent, plainConfig) = withoutDepth(LaunchContent.catalog, BalanceConfig.DEFAULT)
        val replaced = content.encounters.mapNotNull { it.replacesEvent }.toSet()
        assertEquals(setOf("collector", "wandering_master", "merchant_festival"), replaced)
        fun worldEvents(engine: GameEngine, expectDepth: Boolean): Set<String> {
            val fired = HashSet<String>()
            val depthTypes = setOf(EventType.ENCOUNTER_OFFERED, EventType.ENCOUNTER_RESOLVED, EventType.ENCOUNTER_EXPIRED, EventType.RELIC_OFFERED, EventType.RELIC_CHOSEN, EventType.RELIC_TRIGGERED, EventType.SIEGE_TRAIT, EventType.PLEDGE_RESOLVED)
            for (seed in 1L..12L) {
                var depthEvents = 0
                var traits = 0
                val (stats, state) = SimulationDriver(engine, maxDays = 60, onDayResolved = { s, _ ->
                    val events = s.lastResolution?.events ?: emptyList()
                    fired += events.filter { it.type == EventType.WORLD_EVENT }.mapNotNull { it.data["event"] }
                    depthEvents += events.count { it.type in depthTypes }
                    traits += events.count { "trait" in it.data } + (if (s.siege?.traitId != null) 1 else 0)
                    if (!expectDepth) assertTrue(s.encounter == null && s.pendingRelicOffer.isEmpty() && s.relics.isEmpty() && s.consequences.isEmpty())
                }).playRun(LegacyProfile(), seed, Policy.BALANCED_ACTIVE)
                if (expectDepth) assertTrue(stats.depth != null && depthEvents > 0)
                else assertTrue(stats.depth == null && depthEvents == 0 && traits == 0 && state.encounterLog.isEmpty(), "seed $seed: depth left a trace in the baseline")
            }
            return fired
        }
        val plain = worldEvents(GameEngine(plainContent, plainConfig), expectDepth = false)
        assertTrue(plain.containsAll(replaced), "the automatic events did not all fire in the baseline: $plain")
        val deep = worldEvents(engine, expectDepth = true)
        assertTrue(deep.none { it in replaced }, "an event a visitor replaced still fired by itself: ${deep.filter { it in replaced }}")
        assertNull(Simulator.run(3, 1, listOf(Policy.BALANCED_FAIR), content = plainContent, config = plainConfig).single().summary().depth)
    }

    @Test
    fun summaryCarriesTheDepthSectionAndSerialises() {
        val report = Simulator.run(12, 1, listOf(Policy.EXPERT)).single()
        val summary = report.summary()
        val depth = assertNotNull(summary.depth)
        assertEquals("adaptive", depth.encounters)
        assertEquals(0, depth.rejected)
        assertTrue(depth.visitorMorningsPerRun > 0 && depth.visitorMorningShare in 0.0..1.0)
        for ((enc, shares) in depth.optionShare) assertEquals(1.0, shares.filterKeys { it != "inspect" }.values.sum(), 1e-9, "the answers to $enc do not add up to its offers")
        assertEquals(12, depth.combinations.sumOf { it.runs })
        assertEquals(report.runs.sumOf { it.siegesSurvived + it.siegesLost }, depth.sieges.sumOf { it.fought })
        assertTrue("many_breaches" in depth.sieges.map { it.trait } || "long_assault" in depth.sieges.map { it.trait })
        assertTrue("depth (visitors=adaptive" in report.render(depth = true) && "depth (visitors" !in report.render())
        val json = Json.encodeToString(PolicySummary.serializer(), summary)
        assertTrue("\"depth\"" in json && "\"optionShare\"" in json)
    }

    /**
     * Every probe runs. The merchant ones are an open finding (2026-10-10: a dear core can be worth more at the supplier
     * than the merchant asks for the whole blade), so they are run and reported but not required to hold; the rest must.
     */
    @Test
    fun theProbesHoldOnASmallSample() {
        val results = DepthProbes.run(engine, seeds = 12)
        assertEquals(9, results.size)
        val open = results.filter { !it.ok }
        assertTrue(open.isEmpty(), open.joinToString("\n") { it.render() })
    }

    @Test
    fun flagsParseAndRejectUnknownValues() {
        assertEquals(EncounterPref.entries, listOf("decline", "first", "cash", "defense", "adaptive").map { EncounterPref.parse(it) })
        assertEquals(EncounterPref.ADAPTIVE, EncounterPref.parse("Adaptive"))
        assertFailsWith<IllegalArgumentException> { EncounterPref.parse("greedy") }
        assertEquals(listOf(RelicPref.First, RelicPref.Adaptive, RelicPref.None, RelicPref.Fixed(Depth.ASHEN_BELLOWS)), listOf("first", "adaptive", "none", "ashen_bellows").map { RelicPref.parse(it, content) })
        assertFailsWith<IllegalArgumentException> { RelicPref.parse("golden_anvil", content) }
        val c = applySet(BalanceConfig.DEFAULT, "encounterChance=0.5,traitChance=0.3,bellowsDebt=3,ledgerQualityPerStep=1,ledgerMaxBonus=4,sealMinCash=100,sealsPerReward=4,wagerStake=60,pledgeRewardFactor=0.75,watchBounty=20,commitBesieger=false")
        assertEquals(BalanceConfig.DEFAULT.copy(depth = BalanceConfig.DEFAULT.depth.copy(encounterChance = 0.5, traitChance = 0.3, bellowsDebt = 3, ledgerQualityPerStep = 1, ledgerMaxBonus = 4,
            sealMinCash = 100, sealsPerReward = 4, wagerStake = 60, pledgeRewardFactor = 0.75, watchBounty = 20, commitBesieger = false)), c)
        assertFailsWith<IllegalArgumentException> { applySet(BalanceConfig.DEFAULT, "commitBesieger=maybe") }
    }

    /** The CLI itself: an unknown `--encounters` or `--relic` stops with exit code 2, as an unknown `--blessing` does. */
    @Test
    fun theCommandLineStopsWithExitCodeTwoOnAnUnknownValue() {
        fun exitCode(vararg args: String): Int {
            val java = File(System.getProperty("java.home"), "bin/java").path
            val process = ProcessBuilder(listOf(java, "-cp", System.getProperty("java.class.path"), "com.tinyblacksmith.core.sim.SimulatorKt") + args).redirectErrorStream(true).start()
            process.inputStream.readBytes()
            return process.waitFor()
        }
        assertEquals(2, exitCode("--blessing", "greedy"))
        assertEquals(2, exitCode("--encounters", "greedy"))
        assertEquals(2, exitCode("--relic", "golden_anvil"))
        assertEquals(0, exitCode("--runs", "2", "--policy", "EXPERT", "--noImpact", "--depth", "--encounters", "cash", "--relic", "tempering_ledger", "--days", "12"))
        assertEquals(0, exitCode("--runs", "2", "--policy", "EXPERT", "--noImpact", "--noDepth", "--days", "12"))
    }
}
