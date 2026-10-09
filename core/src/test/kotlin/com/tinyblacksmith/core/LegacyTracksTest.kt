package com.tinyblacksmith.core

import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.SliceContent
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import com.tinyblacksmith.core.sim.Simulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The v5 legacy tracks (GDD 9: catalog access, recipe odds, legacy artifacts) and the Known Name regulars. */
class LegacyTracksTest {
    private val engine = GameEngine()
    private val content = engine.content
    private val config = engine.config
    private val tracks = config.legacyTracks
    private val newTracks = listOf(LaunchContent.UPG_CATALOG, LaunchContent.UPG_RECIPES, LaunchContent.UPG_ARTIFACTS)

    private val legend = LegendEntry(
        era = 1, weaponName = "Old Ember", title = "Bane of the Ashclaw Raiders", kills = 12, fame = 9, owners = listOf("Mira Vance"),
        familyId = LaunchContent.SWORD, coreId = LaunchContent.SILVER, augmentId = LaunchContent.EMBER_RESIN, quality = 80, power = 40,
    )

    private fun legacy(vararg levels: Pair<UpgradeId, Int>, board: List<LegendEntry> = emptyList()) = LegacyProfile(upgrades = mapOf(*levels), legendBoard = board)

    private fun ctx(legacy: LegacyProfile, e: GameEngine = engine) = ResolutionContext(e.newRun(legacy, 1), e.content, e.config)

    @Test
    fun elevenTracksEachThreeLevelsAtTheTierCosts() {
        assertEquals(emptyList(), content.validate())
        assertEquals(11, content.upgrades.size)
        assertEquals(UpgradeEffect.entries.toSet(), content.upgrades.map { it.effect }.toSet(), "one track per effect")
        for (u in content.upgrades) {
            assertEquals(3, u.maxLevel, u.name)
            assertEquals(listOf(8, 20, 45), u.costPerLevel, u.name)
        }
        for (id in newTracks) {
            val u = content.upgrade(id)
            // GDD round 9, descriptive indicators: the player reads words, not formulas.
            assertTrue(u.description.none { it.isDigit() || it == '%' }, "${u.name}: ${u.description}")
            var profile = LegacyProfile(points = 73)
            repeat(3) { profile = (engine.purchaseUpgrade(profile, id) as LegacyOutcome.Updated).legacy }
            assertEquals(0, profile.points, "8 + 20 + 45")
            assertEquals(3, profile.upgradeLevel(id))
            assertEquals(GameError.UpgradeMaxed(id), (engine.purchaseUpgrade(profile.copy(points = 99), id) as LegacyOutcome.Rejected).error)
        }
    }

    @Test
    fun tracksAtLevelZeroAndOnTheSliceCatalogChangeNothing() {
        val zero = legacy(LaunchContent.UPG_CATALOG to 0, LaunchContent.UPG_RECIPES to 0, LaunchContent.UPG_ARTIFACTS to 0, LaunchContent.UPG_REPUTATION to 0, board = listOf(legend))
        val none = LegacyProfile(legendBoard = listOf(legend))
        assertEquals(engine.newRun(none, 9).copy(legacy = zero), engine.newRun(zero, 9))
        val driver = SimulationDriver(maxDays = 12)
        for (policy in listOf(Policy.BALANCED_ACTIVE, Policy.BALANCED_INVEST, Policy.RECKLESS_FAIR)) {
            assertEquals(driver.playRun(none, 9, policy).first, driver.playRun(zero, 9, policy).first, policy.name)
        }
        // The slice catalog carries none of the four tracks: a profile that owns them plays as a new account there.
        val slice = GameEngine(content = SliceContent.catalog)
        val owned = LegacyProfile(upgrades = (newTracks + LaunchContent.UPG_REPUTATION).associateWith { 3 })
        assertEquals(slice.newRun(LegacyProfile(), 3).copy(legacy = owned), slice.newRun(owned, 3))
    }

    @Test
    fun caravanTiesDeepensEveryLimitedMaterialByOnePerLevel() {
        val limited = content.materials.filter { it.dailySupplierStock != null }
        assertTrue(limited.size >= 8, "launch content limits its rare cores, augments and catalysts")
        for (level in 0..3) {
            val start = engine.newRun(legacy(LaunchContent.UPG_CATALOG to level), 5)
            // The always-stocked basics stay unlimited (absent from the map); every limited material gains one unit a level.
            val expected = limited.associate { it.id to it.dailySupplierStock!! + level * tracks.catalogStockPerLevel }
            assertEquals(expected, start.supplierStock, "day 1, level $level")
            val bought = engine.handle(start.copy(gold = 10_000), Command.BuyMaterial(LaunchContent.SILVER, 1 + level)).stateOrThrow()
            assertEquals(0, bought.supplierStock[LaunchContent.SILVER])
            assertEquals(GameError.SupplierOutOfStock(LaunchContent.SILVER), assertIs<CommandOutcome.Rejected>(engine.handle(bought, Command.BuyMaterial(LaunchContent.SILVER, 1))).error, "bounded at level $level")
            // The deeper shelf is refilled each morning; a delayed caravan still leaves it bare.
            val nextDay = engine.handle(bought, Command.EndDay(CommandId("t:1"))).stateOrThrow()
            val delayed = nextDay.worldFlags[WorldEvents.FLAG_CARAVAN_DELAYED] == nextDay.day
            assertEquals(if (delayed) expected.mapValues { 0 } else expected, nextDay.supplierStock, "day 2, level $level")
            val late = engine.handle(start.copy(worldFlags = mapOf(WorldEvents.FLAG_CARAVAN_DELAYED to 2)), Command.EndDay(CommandId("t:1"))).stateOrThrow()
            assertTrue(late.supplierStock.values.all { it == 0 }, "caravan delay, level $level")
        }
    }

    @Test
    fun caravanTiesLetsASmithWithGoldRepeatARareRecipe() {
        // Sparkfang's recipe (silver + stormglass, reckless Quick Forge): with gold to spare, the daily rare stock is what limits the attempts.
        val recipe = Command.Forge(ForgeMode.QUICK, LaunchContent.DAGGER, LaunchContent.SILVER, LaunchContent.STORMGLASS, null, Risk.RECKLESS)
        fun attemptsInFiveDays(level: Int): Int {
            var s = engine.newRun(legacy(LaunchContent.UPG_CATALOG to level), 11).copy(gold = 100_000)
            var attempts = 0
            repeat(5) {
                while (s.energy >= config.quickForgeEnergy) {
                    for (m in listOf(recipe.coreId, recipe.augmentId)) {
                        if ((s.materials[m] ?: 0) == 0) (engine.handle(s, Command.BuyMaterial(m, 1)) as? CommandOutcome.Accepted)?.let { s = it.state }
                    }
                    s = (engine.handle(s, recipe) as? CommandOutcome.Accepted)?.state ?: break
                    attempts++
                }
                s = engine.handle(s, Command.EndDay(CommandId("t:${s.day}"))).stateOrThrow()
            }
            return attempts
        }
        val attempts = (0..3).map { attemptsInFiveDays(it) }
        assertTrue(attempts.zipWithNext().all { (a, b) -> b > a }, "attempts by level: $attempts")
        assertTrue(attempts[0] <= 6, "one unit a day plus the starting kit: ${attempts[0]}")
        assertTrue(attempts[3] <= 5 * (config.baseDailyEnergy / config.quickForgeEnergy), "energy still bounds the deepest shelf: ${attempts[3]}")
    }

    private val stormsong = SignatureCatalog.byId.getValue("stormsong")
    private val exact = Command.Forge(ForgeMode.ADVANCED, LaunchContent.BOW, LaunchContent.SILVER, LaunchContent.STORMGLASS, LaunchContent.BINDING_SALT, Risk.SAFE)

    /** Forges the exact Stormsong recipe once per seed; returns the forged weapons by seed. */
    private fun stormsongForges(e: GameEngine, legacy: LegacyProfile, seeds: Int, prepare: (GameState) -> GameState = { it }): Map<Int, Weapon> =
        (1..seeds).associateWith { seed ->
            val s = prepare(e.newRun(legacy, seed.toLong()).copy(materials = content.materials.associate { it.id to 5 }, energy = 100))
            val out = e.handle(s, exact) as CommandOutcome.Accepted
            out.state.weapon(out.forgedWeaponId!!)
        }

    private fun Map<Int, Weapon>.transformed(): Set<Int> = filterValues { it.signatureId == stormsong.id }.keys

    @Test
    fun anvilLoreRaisesTheSignatureChancePerLevelInsideTheCap() {
        // Quality pinned at the ceiling, so every forge is eligible and only the transformation roll decides.
        val sure = GameEngine(config = config.copy(qualityBase = 90))
        val byLevel = (0..3).map { stormsongForges(sure, legacy(LaunchContent.UPG_RECIPES to it), 1000).transformed() }
        assertEquals(stormsongForges(sure, LegacyProfile(), 1000).transformed(), byLevel[0], "level 0 is the plain chance")
        for (level in 0..3) {
            assertEquals(config.signatureBaseChance + level * tracks.recipeOddsPerLevel, byLevel[level].size / 1000.0, 0.04, "level $level")
            // Same seed, same rolls: a level only ever adds transformations.
            if (level > 0) assertTrue(byLevel[level].containsAll(byLevel[level - 1]) && byLevel[level].size > byLevel[level - 1].size, "level $level")
        }
        // Three levels would add 0.15 to a base of cap - 0.05: the existing cap holds.
        val nearCap = GameEngine(config = config.copy(qualityBase = 90, signatureBaseChance = config.signatureMaxChance - 0.05))
        assertEquals(config.signatureMaxChance, stormsongForges(nearCap, legacy(LaunchContent.UPG_RECIPES to 3), 1000).transformed().size / 1000.0, 0.04)
    }

    @Test
    fun aSignatureIsNeverCertainWithEveryBonusStacked() {
        // 0.15 base + 0.24 mastery (Forge Mastery, two whetstone levels, Forgefire) + 0.15 Anvil Lore = 0.54, held at the 0.50 cap.
        val stacked = legacy(LaunchContent.UPG_RECIPES to 3, LaunchContent.UPG_MASTERY to 3)
        val forged = stormsongForges(engine, stacked, 600) { it.copy(tools = mapOf("whetstone" to 2), blessings = listOf(ActiveBlessing(LaunchContent.FORGEFIRE, expiresDay = 99))) }
        val eligible = forged.filterValues { it.quality >= stormsong.minQuality }
        assertEquals(600, eligible.size, "with this much mastery every forge clears the quality floor")
        assertEquals(config.signatureMaxChance, eligible.transformed().size / 600.0, 0.05)
    }

    @Test
    fun homingSteelRaisesReturnWeightAndWholenessPerLevelBounded() {
        val blade = WorldEvents.byId("famous_blade")
        var lastQuality = 0
        for (level in 0..3) {
            val c = ctx(legacy(LaunchContent.UPG_ARTIFACTS to level, board = listOf(legend)))
            assertEquals(blade.weight + level * tracks.legendReturnWeightPerLevel, WorldEvents.weight(c, blade), 1e-9, "level $level")
            for (other in WorldEvents.all) if (other.id != blade.id) assertEquals(other.weight, WorldEvents.weight(c, other), 0.0, other.id)
            val factor = WorldEvents.returnedLegendFactor(c)
            assertEquals(config.returnedLegendQualityFactor + level * tracks.legendQualityFactorPerLevel, factor, 1e-9, "level $level")
            WorldEvents.fire(c, blade)
            val s = c.toState()
            assertEquals(emptyList(), Invariants.check(s, config))
            val w = s.weapons.values.single()
            assertEquals((legend.quality * factor).toInt(), w.quality)
            assertEquals((legend.power * factor).toInt(), w.power)
            assertTrue(w.quality > lastQuality && w.quality < legend.quality && w.power < legend.power, "level $level: less dormant, never whole")
            lastQuality = w.quality
            assertFalse(WorldEvents.canFire(ResolutionContext(s, content, config), blade), "still at most once a run")
        }
        // However large the per-level step, the factor stops at the cap, below a whole blade.
        val generous = GameEngine(config = config.copy(legacyTracks = tracks.copy(legendQualityFactorPerLevel = 0.5)))
        val capped = WorldEvents.returnedLegendFactor(ctx(legacy(LaunchContent.UPG_ARTIFACTS to 3, board = listOf(legend)), generous))
        assertEquals(tracks.returnedLegendQualityFactorMax, capped, 1e-9)
        assertTrue(capped < 1.0)
    }

    @Test
    fun homingSteelBringsLegendsHomeMoreOftenAndNeverTwiceARun() {
        val driver = SimulationDriver(maxDays = 20)
        fun returns(level: Int) = (1L..150L).sumOf { seed ->
            val stats = driver.playRun(legacy(LaunchContent.UPG_ARTIFACTS to level, board = listOf(legend)), seed, Policy.BALANCED_FAIR).first
            assertTrue(stats.legendsReturned <= 1, "seed $seed, level $level")
            stats.legendsReturned
        }
        val plain = returns(0)
        val upgraded = returns(3)
        assertTrue(plain > 0, "a legend returns in some of 150 runs without the upgrade")
        assertTrue(upgraded >= plain + 20 && upgraded < 150, "returned in $plain of 150 runs without Homing Steel, $upgraded with it: likelier, not guaranteed")
    }

    @Test
    fun homingSteelDoesNothingWithAnEmptyLegendBoard() {
        val upgraded = legacy(LaunchContent.UPG_ARTIFACTS to 3)
        assertFalse(WorldEvents.canFire(ctx(upgraded), WorldEvents.byId("famous_blade")))
        val driver = SimulationDriver()
        for (seed in 1L..8L) {
            val (stats, state) = driver.playRun(upgraded, seed, Policy.BALANCED_ACTIVE)
            assertTrue(state.isEnded, "seed $seed plays to its end")
            assertEquals(0, stats.legendsReturned)
            assertEquals(driver.playRun(LegacyProfile(), seed, Policy.BALANCED_ACTIVE).first, stats, "seed $seed plays exactly as without the upgrade")
        }
    }

    @Test
    fun knownNameStartsOneRegularPerLevelWithSavingsAndRollsNothing() {
        val plain = engine.newRun(LegacyProfile(), 21)
        assertTrue(plain.heroes.values.none { Market.isRegular(it, config) })
        assertTrue(plain.events.none { it.type == EventType.RUN_STARTED && it.subjectIds.isNotEmpty() })
        for (level in 1..3) {
            val s = engine.newRun(legacy(LaunchContent.UPG_REPUTATION to level), 21)
            assertEquals(content.upgrade(LaunchContent.UPG_REPUTATION).magnitudePerLevel * level, s.reputation)
            assertEquals(plain.rng, s.rng, "the regulars are chosen without a roll")
            val regulars = s.aliveHeroes().take(level)
            for (h in s.aliveHeroes()) {
                val before = plain.hero(h.id)
                if (h in regulars) {
                    assertEquals(before.copy(loyalty = config.regularLoyaltyThreshold, gold = before.gold + tracks.knownNameRegularGold), h)
                    assertTrue(Market.isRegular(h, config))
                } else assertEquals(before, h)
            }
            // The Gazette can tell the player who they are.
            val record = s.events.single { it.type == EventType.RUN_STARTED && it.subjectIds.isNotEmpty() }
            assertEquals(regulars.map { it.id.value }, record.subjectIds)
            regulars.forEach { assertTrue(it.fullName in record.text, record.text) }
        }
    }

    @Test
    fun knownNameRegularsArmTheTownForTheFirstSiege() {
        // Runs stop at the first siege (day 5): what the regulars bought by then is what defends the town.
        val driver = SimulationDriver(maxDays = config.siegeInterval)
        fun firstSiege(l: LegacyProfile) = (1L..600L).map { driver.playRun(l, it, Policy.BALANCED_ACTIVE).first }
        val plain = firstSiege(LegacyProfile())
        val known = firstSiege(legacy(LaunchContent.UPG_REPUTATION to 3))
        assertTrue(known.sumOf { it.soldByFirstSiege } > plain.sumOf { it.soldByFirstSiege }, "sales by the first siege")
        assertTrue(known.sumOf { it.firstSiegeDefense } > plain.sumOf { it.firstSiegeDefense }, "town defense at the first siege")
        assertTrue(known.count { it.firstSiegeHeld } > plain.count { it.firstSiegeHeld }, "first sieges held: ${plain.count { it.firstSiegeHeld }} -> ${known.count { it.firstSiegeHeld }} of 600")
    }

    @Test
    fun impactTableCoversEveryTrackWithSecondYardsticks() {
        assertEquals(content.upgrades.associate { it.id to it.maxLevel }, Simulator.maxedLegacy(engine).upgrades)
        val board = Simulator.veteranLegendBoard(engine, Policy.BALANCED_ACTIVE, baseSeed = 1, runs = 4)
        assertTrue(board.isNotEmpty() && board.size <= 20, "board of ${board.size}")
        assertEquals(board, Simulator.veteranLegendBoard(engine, Policy.BALANCED_ACTIVE, baseSeed = 1, runs = 4))
        val impact = Simulator.upgradeImpact(runs = 8, baseSeed = 1, config = config, baselineMedianDays = 0, baselineMeanDays = 0.0, policy = Policy.BALANCED_INVEST, legendBoard = board)
        assertEquals(content.upgrades.map { it.id.value }, impact.map { it.upgradeId })
        for (row in impact) {
            val y = assertNotNull(row.yardsticks, row.name)
            assertTrue(y.firstSiegeDefense > 0 && y.firstSiegeHeld in 0.0..1.0 && y.forgedByFirstSiege <= y.forged && y.soldByFirstSiege <= y.sold, row.name)
            assertTrue(y.premiumSaleShare in 0.0..1.0 && y.rareMaterialsBought > 0, "the invest rule buys limited stock (${row.name})")
        }
    }

    @Test
    fun elevenMaxedTracksAreDeterministicAndDoNotMakeTheForgeImmortal() {
        val veteran = Simulator.maxedLegacy(engine).copy(legendBoard = listOf(legend))
        val driver = SimulationDriver()
        for (seed in 1L..40L) {
            val (stats, state) = driver.playRun(veteran, seed, Policy.BALANCED_ACTIVE)
            assertTrue(state.isEnded && stats.daysSurvived < 100, "seed $seed: ${stats.daysSurvived} days")
        }
        fun play() = SaveCodec.encodeRun(SimulationDriver(maxDays = 12).playRun(veteran, 77, Policy.BALANCED_INVEST).second)
        assertEquals(play(), play())
    }
}
