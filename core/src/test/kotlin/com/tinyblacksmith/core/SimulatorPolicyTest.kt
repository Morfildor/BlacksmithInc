package com.tinyblacksmith.core

import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.sim.BlessingPref
import com.tinyblacksmith.core.sim.BotCounter
import com.tinyblacksmith.core.sim.BuyRule
import com.tinyblacksmith.core.sim.EraPlay
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The BALANCED_INVEST purchasing rule (best affordable tier within `gold - reserve`), the BALANCED_REPUTED pricing rule and the T0.7 bots. */
class SimulatorPolicyTest {
    private val content = LaunchContent.catalog

    private fun firstForgeTiers(reserve: Int): Pair<Int, Int> {
        val (_, state) = SimulationDriver(maxDays = 1, reserve = reserve).playRun(LegacyProfile(), 1, Policy.BALANCED_INVEST)
        val first = state.weapons.values.minBy { it.id.value }
        return content.material(first.coreId).tier to content.material(first.augmentId).tier
    }

    @Test
    fun investBuysTheBestTierWithinGoldMinusReserve() {
        // 250 starting gold: moonsteel (180) then grave dust (55) fit in 250; sun ash (85) does not.
        assertEquals(6 to 4, firstForgeTiers(reserve = 0))
        // Budget 130: starsteel (120) fits; the 10 gold left cannot beat the owned stormglass (tier 3, cost 0).
        assertEquals(5 to 3, firstForgeTiers(reserve = 120))
        // Budget 10: nothing premium fits, so the best owned pair from the starting kit (silver + stormglass) is forged.
        assertEquals(3 to 3, firstForgeTiers(reserve = 240))
    }

    @Test
    fun investWithAnUnreachableReserveMatchesBalancedFair() {
        val fair = SimulationDriver().playRun(LegacyProfile(), 1234, Policy.BALANCED_FAIR).first
        val invest = SimulationDriver(reserve = 1_000_000).playRun(LegacyProfile(), 1234, Policy.BALANCED_INVEST).first
        assertEquals(fair, invest)
    }

    @Test
    fun reputedListsAtTheReputationCeiling() {
        // Known Name L3 starts the run with reputation 15, so the ceiling is +15 % over the fair price.
        val engine = GameEngine()
        val knownName = content.upgrades.first { it.effect == UpgradeEffect.STARTING_REPUTATION }
        val legacy = LegacyProfile(upgrades = mapOf(knownName.id to knownName.maxLevel))
        val startingReputation = engine.newRun(legacy, 7).reputation
        assertTrue(startingReputation > 0)
        val factor = 1.0 + (startingReputation * engine.config.reputationPricePerPoint).coerceIn(0.0, engine.config.reputationPriceCap)
        val (_, state) = SimulationDriver(engine, maxDays = 1).playRun(legacy, 7, Policy.BALANCED_REPUTED)
        val listed = state.listedWeapons()
        assertTrue(listed.isNotEmpty())
        for (w in listed) assertEquals((engine.suggestedPrice(w) * factor).toInt(), w.listedPrice)
    }

    @Test
    fun investIsDeterministicPerSeed() {
        val driver = SimulationDriver()
        val a = driver.playRun(LegacyProfile(), 4321, Policy.BALANCED_INVEST).first
        val b = driver.playRun(LegacyProfile(), 4321, Policy.BALANCED_INVEST).first
        assertEquals(a, b)
    }

    @Test
    fun activeUsesEveryShopActionAndIsDeterministic() {
        val driver = SimulationDriver(maxDays = 12)
        val (stats, state) = driver.playRun(LegacyProfile(), 99, Policy.BALANCED_ACTIVE)
        assertEquals(stats, driver.playRun(LegacyProfile(), 99, Policy.BALANCED_ACTIVE).first)
        assertTrue(state.tools.isNotEmpty(), "bought no tool")
        assertTrue(state.weapons.values.any { it.honed }, "honed nothing")
        assertTrue(state.weapons.values.any { w -> w.history.any { it.kind == "DONATED" } }, "armed the watch with nothing")
        assertTrue(state.weapons.values.any { w -> w.history.any { it.kind == "SALVAGED" } }, "salvaged nothing")
    }

    // ---- T0.7 bots ----

    /** Each counter summed over [seeds] runs of [policy], [days] days at most. */
    private fun counts(policy: Policy, seeds: IntRange, days: Int, legacy: LegacyProfile = LegacyProfile()): Map<BotCounter, Int> {
        val driver = SimulationDriver(maxDays = days)
        val runs = seeds.map { driver.playRun(legacy, it.toLong(), policy).first.bot!! }
        return BotCounter.entries.associateWith { c -> runs.sumOf { it[c] } }
    }

    @Test
    fun allStillMeansTheClassicFourteenAndTheBotsAreSeparate() {
        assertEquals(14, Policy.CLASSIC.size)
        assertTrue(Policy.CLASSIC.all { it.rules == null })
        assertEquals(Policy.entries.toSet(), (Policy.CLASSIC + Policy.BOTS).toSet())
        assertTrue(Policy.BOTS.containsAll(listOf(Policy.ADVANCED_SMITH, Policy.EXPERT, Policy.FREE_LISTINGS, Policy.BROKE_START)))
        assertNull(SimulationDriver(maxDays = 5).playRun(LegacyProfile(), 1, Policy.BALANCED_FAIR).first.bot)
    }

    @Test
    fun everyBotIssuesOnlyLegalCommandsAndIsDeterministic() {
        val driver = SimulationDriver(maxDays = 40)
        for (policy in Policy.BOTS) for (seed in 1L..4L) {
            val (stats, _) = driver.playRun(LegacyProfile(), seed, policy)
            assertEquals(0, stats.bot!![BotCounter.REJECTED], "$policy seed $seed had a rejected command")
            assertEquals(stats, driver.playRun(LegacyProfile(), seed, policy).first, "$policy seed $seed is not deterministic")
        }
    }

    @Test
    fun advancedSmithForgesInAdvancedModeWithCatalysts() {
        val c = counts(Policy.ADVANCED_SMITH, 1..4, 15)
        assertTrue(c.getValue(BotCounter.ADVANCED_FORGES) > 0, "no Advanced forge")
        assertEquals(c.getValue(BotCounter.ADVANCED_FORGES), c.getValue(BotCounter.CATALYST_FORGES), "every Advanced forge carries a catalyst")
        assertTrue(c.getValue(BotCounter.QUICK_FORGES) > 0, "spare energy goes to Quick forges")
    }

    @Test
    fun eachTechniqueBotUsesItsOwnTechniqueOnly() {
        val named = mapOf(Policy.TECHNIQUE_TEMPER to BotCounter.TEMPER_FORGES, Policy.TECHNIQUE_QUENCH to BotCounter.QUENCH_FORGES, Policy.TECHNIQUE_ETCH to BotCounter.ETCH_FORGES)
        for ((policy, own) in named) {
            val c = counts(policy, 1..4, 15)
            assertTrue(c.getValue(own) > 0, "$policy never used $own")
            assertEquals(c.getValue(BotCounter.ADVANCED_FORGES), c.getValue(own), "$policy used another technique")
            assertEquals(0, c.getValue(BotCounter.CATALYST_FORGES), "$policy used a catalyst")
        }
    }

    @Test
    fun requestDrivenForgesWhatCommissionsAskForAndDelivers() {
        val c = counts(Policy.REQUEST_DRIVEN, 1..8, 25)
        assertTrue(c.getValue(BotCounter.REQUEST_FORGES) > 0, "never forged for a request")
        assertTrue(c.getValue(BotCounter.COMMISSIONS_COMPLETED) > 0, "never delivered a commission")
    }

    @Test
    fun noviceIgnoresCommissionsAndForgesAtMostTwiceADay() {
        val (stats, state) = SimulationDriver(maxDays = 25).playRun(LegacyProfile(), 3, Policy.NOVICE)
        assertTrue(state.commissions.values.none { it.status == CommissionStatus.ACCEPTED || it.status == CommissionStatus.COMPLETED })
        assertTrue(stats.bot!![BotCounter.QUICK_FORGES] in 1..(2 * 25))
    }

    @Test
    fun siegePrepForgesTheWeakElementAndArmsTheWatchInTheWarningWindow() {
        val c = counts(Policy.SIEGE_PREP, 1..4, 30)
        assertTrue(c.getValue(BotCounter.SIEGE_FORGES) > 0, "no counter-element forge")
        assertTrue(c.getValue(BotCounter.SIEGE_DONATIONS) > 0, "never armed the watch")
    }

    @Test
    fun signaturePursuitForgesSignatureRecipesAndTransformsSome() {
        val c = counts(Policy.SIGNATURE_PURSUIT, 1..6, 30)
        assertTrue(c.getValue(BotCounter.SIGNATURE_FORGES) > 0)
        assertTrue(c.getValue(BotCounter.SIGNATURE_TRIES) > 0, "no forge qualified for a signature")
        assertTrue(c.getValue(BotCounter.SIGNATURE_HITS) > 0, "no signature was ever born")
        assertTrue(c.getValue(BotCounter.CATALYST_FORGES) > 0, "recipes with a catalyst need Advanced Forge")
    }

    @Test
    fun scarceRecipeStocksUpAndCaravanTiesBuyMore() {
        val plain = counts(Policy.SCARCE_RECIPE, 1..6, 20)
        val ties = counts(Policy.SCARCE_RECIPE, 1..6, 20, LegacyProfile(upgrades = mapOf(LaunchContent.UPG_CATALOG to 3)))
        assertTrue(plain.getValue(BotCounter.SCARCE_FORGES) > 0 && plain.getValue(BotCounter.SCARCE_UNITS_BOUGHT) > 0)
        assertTrue(ties.getValue(BotCounter.SCARCE_UNITS_BOUGHT) > plain.getValue(BotCounter.SCARCE_UNITS_BOUGHT), "Caravan Ties did not deepen the stockpile")
        assertTrue(SimulationDriver(maxDays = 20).playRun(LegacyProfile(), 1, Policy.SCARCE_RECIPE).first.rareMaterialsBought > 0)
    }

    @Test
    fun expertCombinesRequestsSiegePrepAndPatronage() {
        val c = counts(Policy.EXPERT, 1..4, 30)
        assertTrue(c.getValue(BotCounter.SIEGE_FORGES) > 0 && c.getValue(BotCounter.SIEGE_DONATIONS) > 0)
        assertTrue(c.getValue(BotCounter.COMMISSIONS_COMPLETED) > 0)
        assertEquals(BlessingPref.PATRONAGE, Policy.EXPERT.rules!!.blessing)
        assertTrue(counts(Policy.EXPERT_ACTIVE, 1..2, 12).getValue(BotCounter.QUICK_FORGES) > 0)
    }

    @Test
    fun spendthriftBuysToolsAndBrokeStartBeginsWithNoGold() {
        assertTrue(SimulationDriver(maxDays = 3).playRun(LegacyProfile(), 1, Policy.SPENDTHRIFT).second.tools.isNotEmpty())
        var goldAfterDayOne = Int.MAX_VALUE
        SimulationDriver(maxDays = 1) { s, _ -> goldAfterDayOne = s.gold }.playRun(LegacyProfile(), 1, Policy.BROKE_START)
        val fair = SimulationDriver(maxDays = 1).playRun(LegacyProfile(), 1, Policy.BALANCED_FAIR).second.gold
        assertTrue(goldAfterDayOne < fair, "BROKE_START ($goldAfterDayOne) did not end day 1 poorer than BALANCED_FAIR ($fair)")
    }

    @Test
    fun freeListingsPriceEverythingAtZero() {
        val (_, state) = SimulationDriver(maxDays = 3).playRun(LegacyProfile(), 5, Policy.FREE_LISTINGS)
        val listings = state.events.filter { it.type == EventType.WEAPON_LISTED }
        assertTrue(listings.isNotEmpty())
        assertTrue(listings.all { "for 0 gold" in it.text })
    }

    @Test
    fun blessingPreferencePicksTheOfferedEffectElseTheFirst() {
        val offer = listOf(LaunchContent.FORGEFIRE, LaunchContent.GUILD_PATRONAGE, LaunchContent.HUNTERS_EDGE)
        assertEquals(LaunchContent.FORGEFIRE, BlessingPref.FIRST.choose(offer, content))
        assertEquals(LaunchContent.FORGEFIRE, BlessingPref.QUALITY.choose(offer, content))
        assertEquals(LaunchContent.GUILD_PATRONAGE, BlessingPref.PATRONAGE.choose(offer, content))
        assertEquals(LaunchContent.HUNTERS_EDGE, BlessingPref.DEFENSE.choose(offer, content))
        assertEquals(LaunchContent.FORGEFIRE, BlessingPref.ENERGY.choose(offer, content), "not offered: the first one")
        // In a real run the choice follows the offer, for every seed.
        var checked = 0
        for (seed in 1L..6L) {
            val events = SimulationDriver(maxDays = 30, blessing = BlessingPref.ENERGY).playRun(LegacyProfile(), seed, Policy.SYNERGY).second.events
            val offers = events.filter { it.type == EventType.BLESSING_OFFERED }.map { it.data.getValue("offer").split(',') }
            val chosen = events.filter { it.type == EventType.BLESSING_CHOSEN }.map { it.data.getValue("blessing") }
            assertTrue(chosen.size in offers.size - 1..offers.size, "an offer is still open only when the run ended on it")
            offers.zip(chosen).forEach { (o, c) ->
                assertEquals(if (LaunchContent.TIRELESS_HANDS.value in o) LaunchContent.TIRELESS_HANDS.value else o.first(), c)
                checked++
            }
        }
        assertTrue(checked > 0, "no blessing was offered")
    }

    @Test
    fun buyRulesSpendByTheFixedOrder() {
        val engine = GameEngine()
        assertEquals(3, BuyRule.parse("walls", content).spend(engine, LegacyProfile(points = 200)).upgradeLevel(LaunchContent.UPG_WALLS))
        val track = BuyRule.parse("track=Anvil Lore", content).spend(engine, LegacyProfile(points = 28))
        assertEquals(2, track.upgradeLevel(LaunchContent.UPG_RECIPES), "8 + 20 buys two levels of the named track and nothing else")
        assertEquals(0, track.points)
        val cheap = BuyRule.parse("cheapest", content).spend(engine, LegacyProfile(points = 16))
        assertEquals(2, cheap.upgrades.values.sum(), "two level-1 upgrades at 8 each")
    }

    @Test
    fun multiEraReturnsComeFromARealEarlierEra() {
        val engine = GameEngine()
        val driver = SimulationDriver(engine, maxDays = 150)
        val rule = BuyRule.parse("track=legacy_artifacts", engine.content)
        var returns = 0
        var genuine = 0
        for (seed in 1L..80L) {
            val account = EraPlay.account(driver, Policy.BALANCED_ACTIVE, seed, 3, rule)
            assertEquals(0, account.first().boardAtStart)
            assertEquals(0, account.first().returns, "era 1 has an empty Legend Board, so nothing can return")
            assertTrue(account.zipWithNext().all { (a, b) -> b.era == a.era + 1 && b.boardAtStart >= a.boardAtStart })
            for (era in account) { returns += era.returns; genuine += era.genuineReturns }
        }
        assertTrue(returns > 0, "no artifact returned in 80 three-era accounts")
        assertEquals(returns, genuine, "every return names a blade on the board the run started with, from an earlier era")
    }
}
