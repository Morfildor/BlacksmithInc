package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.battle.Power
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Balance v3: affix effects, elite foes and warlords, hero ambitions, element commissions, shop actions and tools. */
class GameplayDepthTest {
    private val config = engine.config
    private val content = engine.content

    private fun fresh(seed: Long = 7) = engine.newRun(LegacyProfile(), seed).withMaterials()

    private fun forged(seed: Long = 7, cmd: Command.Forge = quickSword(Risk.SAFE)): Pair<GameState, WeaponId> =
        fresh(seed).forgeAccepted(cmd).let { it.state to it.forgedWeaponId!! }

    private fun GameState.rejected(cmd: Command): GameError = assertIs<CommandOutcome.Rejected>(engine.handle(this, cmd)).error

    private fun GameState.pressure(factionId: FactionId, value: Int) =
        copy(factions = factions.mapValues { (id, f) -> f.copy(pressure = if (id == factionId) value else 0) })

    /** Same rules with raids a tenth as strong, so the siege due today is certainly won. */
    private fun GameState.endDayWithWeakRaids(): GameState =
        GameEngine(config = config.copy(siegeModifier = config.siegeModifier / 10)).handle(this, Command.EndDay(TestSupport.endDayId(this))).stateOrThrow()

    // --- Affixes ---

    @Test
    fun undeadBaneOnlyBitesTheHollowboundAndGiantSlayerOnlyElites() {
        val (s, id) = forged()
        val hero = s.aliveHeroes().first()
        val plain = s.weapon(id).copy(affixes = emptyList(), flaws = emptyList(), element = null)
        val bane = plain.copy(affixes = listOf(LaunchContent.UNDEAD_BANE))
        val slayer = plain.copy(affixes = listOf(LaunchContent.GIANT_SLAYER))
        val hollow = content.faction(LaunchContent.HOLLOWBOUND)
        val ashclaw = content.faction(LaunchContent.ASHCLAW)
        fun attack(w: Weapon, f: com.tinyblacksmith.core.content.FactionDef, elite: Boolean = false) = Power.attackPower(hero, w, f, content, config, elite = elite)

        val baneDef = content.affix(LaunchContent.UNDEAD_BANE)
        assertEquals(attack(plain, ashclaw) * baneDef.attackMultiplier, attack(bane, ashclaw), 1e-9)
        assertEquals(attack(plain, hollow) * baneDef.attackMultiplier * baneDef.baneMultiplier, attack(bane, hollow), 1e-9)
        assertTrue(baneDef.baneMultiplier > 1.1)

        val slayerDef = content.affix(LaunchContent.GIANT_SLAYER)
        assertEquals(attack(plain, ashclaw) * slayerDef.attackMultiplier, attack(slayer, ashclaw), 1e-9)
        assertEquals(attack(plain, ashclaw, elite = true) * slayerDef.attackMultiplier * slayerDef.eliteMultiplier, attack(slayer, ashclaw, elite = true), 1e-9)
        assertTrue(Power.defensePower(hero, slayer, ashclaw, content, config, elite = true) > Power.defensePower(hero, slayer, ashclaw, content, config))
    }

    /** Every hero gets the same weapon with [affixes]/[flaws]; returns states after [days] across [seeds]. */
    private fun armedWorlds(affixes: List<AffixId>, flaws: List<AffixId>, seeds: LongRange, days: Int): List<GameState> = seeds.map { seed ->
        var s = fresh(seed)
        for (h in s.aliveHeroes()) {
            val out = s.forgeAccepted(quickSword(Risk.SAFE)).let { o -> o.state.copy(energy = 10, overworkToday = 0) to o.forgedWeaponId!! }
            s = out.first
            val w = s.weapon(out.second).copy(affixes = affixes, flaws = flaws, location = WeaponLocation.Owned(h.id, equipped = true))
            s = s.copy(weapons = s.weapons + (w.id to w))
        }
        repeat(days) { if (!s.isEnded) s = s.endDay() }
        s
    }

    @Test
    fun vampiricMendsAndBrittleShatters() {
        val plain = armedWorlds(emptyList(), emptyList(), 1L..20L, 4)
        val vampiric = armedWorlds(listOf(LaunchContent.VAMPIRIC), emptyList(), 1L..20L, 4)
        val brittle = armedWorlds(emptyList(), listOf(LaunchContent.BRITTLE), 1L..20L, 4)
        fun broken(worlds: List<GameState>) = worlds.sumOf { s -> s.events.count { it.type == EventType.WEAPON_BROKEN } }
        assertEquals(0, broken(plain))
        assertEquals(0, broken(vampiric))
        assertTrue(broken(brittle) >= 3, "brittle weapons shattered ${broken(brittle)} times")
        brittle.forEach { s -> s.weapons.values.filter { it.location is WeaponLocation.Destroyed }.forEach { w -> assertTrue(w.history.any { it.kind == "BROKEN" }) } }
        fun rests(worlds: List<GameState>) = worlds.sumOf { s -> s.events.count { it.type == EventType.HERO_RESTED } }
        assertTrue(rests(vampiric) < rests(plain), "vampiric heroes rested ${rests(vampiric)} times, plain ${rests(plain)}")
    }

    // --- Elites and warlords ---

    @Test
    fun elitesAppearPayBetterAndCountTowardsLegacy() {
        var elites = 0
        for (s in armedWorlds(listOf(LaunchContent.KEEN), emptyList(), 1L..30L, 4)) {
            val slain = s.events.filter { it.type == EventType.ELITE_SLAIN }
            elites += slain.size
            if (slain.isNotEmpty()) {
                assertTrue("ELITE_SLAIN" in s.milestones)
                assertTrue(s.heroes.values.any { it.elitesSlain > 0 })
                val weaponIds = slain.flatMap { it.subjectIds }.filter { it.startsWith("w") }
                assertTrue(weaponIds.all { s.weapons.getValue(WeaponId(it)).title != null }, "the weapon that slays an elite earns a title")
            }
        }
        assertTrue(elites >= 5, "elites slain across 30 seeds: $elites")
    }

    @Test
    fun aWarlordLeadsTheSiegeOnlyAtHighPressure() {
        val s = fresh()
        val below = engine.siegeForecast(s.pressure(LaunchContent.ASHCLAW, config.warlordPressure - 1))!!
        val at = engine.siegeForecast(s.pressure(LaunchContent.ASHCLAW, config.warlordPressure))!!
        assertTrue(!below.warlord && at.warlord)
        val perPressure = config.raidPerPressure * config.siegeModifier * s.world.raidMultiplier
        assertEquals((below.raidPower + perPressure) * config.warlordRaidMultiplier, at.raidPower, 1e-6)
    }

    @Test
    fun beatingAWarlordPaysTributeAndBreaksTheFaction() {
        var s = fresh().pressure(LaunchContent.ASHCLAW, 90)
        s = s.copy(day = s.town.nextSiegeDay, town = s.town.copy(armory = config.armoryMax))
        val after = s.endDayWithWeakRaids()
        assertEquals(1, after.town.siegesSurvived)
        assertTrue("WARLORD_DEFEATED" in after.milestones)
        assertTrue(after.gold >= s.gold + config.warlordTribute)
        assertTrue(after.factions.getValue(LaunchContent.ASHCLAW).pressure <= 90 + 4 - config.siegeWinPressureDrop - config.warlordPressureDrop)
        assertEquals((config.armoryMax * (1.0 - config.armorySiegeWear)).toInt(), after.town.armory, "a siege wears the armory down")
    }

    // --- Ambitions ---

    @Test
    fun everyHeroHasAnAmbitionAndItTiltsTheirDays() {
        val byAmbition = mutableMapOf<Ambition, IntArray>()  // [expeditions, patrols]
        for (seed in 1L..60L) {
            val s0 = fresh(seed)
            assertTrue(s0.heroes.values.all { it.ambition != null })
            val s1 = s0.endDay()
            for (h in s0.aliveHeroes()) {
                val counts = byAmbition.getOrPut(h.ambition!!) { IntArray(2) }
                if (s1.events.any { h.id.value in it.subjectIds && it.type in setOf(EventType.EXPEDITION_WON, EventType.EXPEDITION_LOST, EventType.ELITE_SLAIN, EventType.HERO_DIED) }) counts[0]++
                if (s1.events.any { h.id.value in it.subjectIds && it.type == EventType.HERO_PATROLLED }) counts[1]++
            }
        }
        fun expeditionShare(a: Ambition) = byAmbition.getValue(a).let { it[0].toDouble() / (it[0] + it[1]) }
        assertTrue(expeditionShare(Ambition.SLAYER) > expeditionShare(Ambition.DEFENDER) + 0.1,
            "slayers ${expeditionShare(Ambition.SLAYER)}, defenders ${expeditionShare(Ambition.DEFENDER)}")
    }

    @Test
    fun aFulfilledAmbitionMakesNewsOnceAndRewardsTheShop() {
        val s0 = fresh()
        val hero = s0.aliveHeroes().first()
        val s = s0.copy(heroes = s0.heroes + (hero.id to hero.copy(ambition = Ambition.FORTUNE, gold = config.ambitionFortuneGold)))
        assertTrue(Heroes.describeAmbition(s.hero(hero.id), null, config)!!.contains("${config.ambitionFortuneGold}/${config.ambitionFortuneGold}"))
        val after = s.endDay()
        val news = after.events.filter { it.type == EventType.AMBITION_FULFILLED && hero.id.value in it.subjectIds }
        assertEquals(1, news.size)
        assertTrue(after.hero(hero.id).ambitionDone)
        assertTrue(after.hero(hero.id).fame >= hero.fame + config.ambitionFame)
        assertTrue(after.reputation >= s.reputation + config.ambitionReputation)
        assertTrue("AMBITION_FULFILLED" in after.milestones)
        val later = after.endDay()
        assertEquals(1, later.events.count { it.type == EventType.AMBITION_FULFILLED && hero.id.value in it.subjectIds })
    }

    @Test
    fun aSwornDefenderIsFulfilledByAWonSiege() {
        var s = fresh()
        s = s.copy(day = s.town.nextSiegeDay, heroes = s.heroes.mapValues { it.value.copy(ambition = Ambition.DEFENDER) })
        val after = s.endDayWithWeakRaids()
        val champions = after.events.first { it.type == EventType.SIEGE_WON }.subjectIds
        assertTrue(champions.isNotEmpty())
        assertTrue(champions.all { after.hero(HeroId(it)).ambitionDone })
        assertTrue(after.heroes.values.filter { it.id.value !in champions }.none { it.ambitionDone })
    }

    // --- Commissions ---

    @Test
    fun anElementCommissionOnlyClosesWithThatElement() {
        val (s0, fireSword) = forged(cmd = quickSword(Risk.SAFE, LaunchContent.IRON, LaunchContent.EMBER_RESIN))
        val buyer = s0.aliveHeroes().first()
        val commission = Commission(CommissionId("c900"), buyer.id, LaunchContent.SWORD, 1, 200, s0.day, s0.day + 3, CommissionStatus.ACCEPTED, element = Element.FROST)
        val s = s0.copy(commissions = mapOf(commission.id to commission))
        assertEquals(Element.FIRE, s.weapon(fireSword).element)
        val waiting = s.endDay()
        assertEquals(CommissionStatus.ACCEPTED, waiting.commissions.getValue(commission.id).status)

        val frost = waiting.copy(energy = 10).forgeAccepted(quickSword(Risk.SAFE, LaunchContent.IRON, LaunchContent.FROST_BLOOM))
        val done = frost.state.endDay()
        val closed = done.commissions.getValue(commission.id)
        assertEquals(CommissionStatus.COMPLETED, closed.status)
        assertEquals(frost.forgedWeaponId, closed.deliveredWeaponId)
    }

    @Test
    fun someOfferedCommissionsAskForAnElementAndPayMoreForIt() {
        val offers = (1L..40L).flatMap { seed ->
            var s = fresh(seed)
            repeat(6) { if (!s.isEnded) s = s.endDay() }
            s.commissions.values
        }
        val elemental = offers.filter { it.element != null }
        assertTrue(elemental.isNotEmpty() && elemental.size < offers.size, "${elemental.size} of ${offers.size} offers name an element")
        elemental.forEach {
            val base = config.commissionRewardBase + it.minQuality * config.commissionRewardPerQuality
            assertEquals((base * config.commissionElementRewardMultiplier).toInt(), it.reward)
        }
    }

    // --- Shop actions ---

    @Test
    fun salvageReturnsTheCoreAndDestroysTheWeapon() {
        val (s, id) = forged()
        val after = s.run(Command.Salvage(id))
        assertEquals(s.materials.getValue(LaunchContent.IRON) + 1, after.materials.getValue(LaunchContent.IRON))
        assertEquals(s.energy - config.salvageEnergy, after.energy)
        assertIs<WeaponLocation.Destroyed>(after.weapon(id).location)
        assertIs<GameError.WeaponNotAvailable>(after.rejected(Command.Salvage(id)))
        assertIs<GameError.NotEnoughEnergy>(s.copy(energy = 0, overworkToday = config.maxOverworkPerDay).rejected(Command.Salvage(id)))
    }

    @Test
    fun honeRaisesQualityOnceForEnergyAndCore() {
        val (s, id) = forged()
        val before = s.weapon(id)
        val after = s.run(Command.Hone(id))
        val honed = after.weapon(id)
        assertEquals(minOf(100, before.quality + config.honeQualityBonus), honed.quality)
        assertTrue(honed.power >= before.power + 1)
        assertEquals(s.energy - config.honeEnergy, after.energy)
        assertEquals(s.materials.getValue(LaunchContent.IRON) - 1, after.materials.getValue(LaunchContent.IRON))
        assertIs<GameError.AlreadyHoned>(after.rejected(Command.Hone(id)))
        assertIs<GameError.MissingMaterial>(s.copy(materials = s.materials + (LaunchContent.IRON to 0)).rejected(Command.Hone(id)))
        // A listed weapon stays listed at its price.
        val listed = s.run(Command.ToggleShelf(id, true, 77)).run(Command.Hone(id))
        assertEquals(77, listed.weapon(id).listedPrice)
    }

    @Test
    fun donatedWeaponsJoinTheTownDefenseUpToTheCap() {
        val (s, id) = forged()
        val gain = engine.armoryValue(s.weapon(id))
        val before = engine.siegeForecast(s)!!
        val after = s.run(Command.DonateWeapon(id))
        assertEquals(gain, after.town.armory)
        assertEquals(before.townDefense + gain, engine.siegeForecast(after)!!.townDefense, 1e-9)
        assertEquals(s.reputation + 1, after.reputation)
        assertTrue(!after.weapon(id).isInStorage && after.weapon(id).ownerId == null)
        assertIs<GameError.WeaponNotAvailable>(after.rejected(Command.DonateWeapon(id)))

        val nearlyFull = s.copy(town = s.town.copy(armory = config.armoryMax - 1)).run(Command.DonateWeapon(id))
        assertEquals(config.armoryMax, nearlyFull.town.armory)
        assertIs<GameError.ArmoryFull>(s.copy(town = s.town.copy(armory = config.armoryMax)).rejected(Command.DonateWeapon(id)))
    }

    @Test
    fun toolsCostGoldAndChangeTheShop() {
        val s = fresh().copy(gold = 5_000)
        val bellows = content.tool("bellows")!!
        val withBellows = s.run(Command.BuyTool("bellows"))
        assertEquals(s.gold - bellows.costPerLevel[0], withBellows.gold)
        assertEquals(s.energy + 1, withBellows.energy)
        assertEquals(s.energy + 1, withBellows.endDay().energy, "the bellows keep working every morning")
        assertEquals(bellows.costPerLevel[1], engine.toolCost(withBellows, "bellows"))
        val maxed = withBellows.run(Command.BuyTool("bellows"))
        assertIs<GameError.ToolMaxed>(maxed.rejected(Command.BuyTool("bellows")))
        assertIs<GameError.NotEnoughGold>(s.copy(gold = 10).rejected(Command.BuyTool("bellows")))
        assertIs<GameError.UnknownContent>(s.rejected(Command.BuyTool("nope")))

        assertEquals(config.shelfSlots + 2, engine.shelfSlots(s.run(Command.BuyTool("display_case"))))

        fun meanQuality(state: GameState) = (1..30).map { state.copy(rng = state.rng).forgeAccepted(quickSword(Risk.SAFE)).let { o -> o.state.weapon(o.forgedWeaponId!!).quality } }.average()
        val whetstone = content.tool("whetstone")!!
        assertEquals(meanQuality(s) + whetstone.magnitudePerLevel, meanQuality(s.run(Command.BuyTool("whetstone"))), 1e-9)
    }

    @Test
    fun theDisplayCaseLetsTheShelfHoldMore() {
        var s = fresh().copy(gold = 5_000, energy = 100)
        val ids = (1..config.shelfSlots + 1).map { s.forgeAccepted(quickSword(Risk.SAFE)).also { o -> s = o.state.copy(energy = 100) }.forgedWeaponId!! }
        for (id in ids.dropLast(1)) s = s.run(Command.ToggleShelf(id, true, 10))
        assertIs<GameError.ShelfFull>(s.rejected(Command.ToggleShelf(ids.last(), true, 10)))
        s = s.run(Command.BuyTool("display_case")).run(Command.ToggleShelf(ids.last(), true, 10))
        assertEquals(config.shelfSlots + 1, s.listedWeapons().size)
    }

    // --- Demand ---

    @Test
    fun aHeroTradesInTheOldWeaponAsPartPayment() {
        val (s1, weakId) = forged()
        val out = s1.copy(energy = 10).forgeAccepted(quickSword(Risk.SAFE))
        val strongId = out.forgedWeaponId!!
        val hero0 = out.state.aliveHeroes().first()
        val weak = out.state.weapon(weakId).copy(location = WeaponLocation.Owned(hero0.id, equipped = true))
        val strong0 = out.state.weapon(strongId).let { it.copy(power = weak.power + 20) }
        val price = engine.suggestedPrice(strong0)
        val credit = Market.tradeInCredit(weak, config)
        assertTrue(credit in 1 until price)
        val hero = hero0.copy(gold = price - credit)
        val s = out.state.copy(
            weapons = out.state.weapons + (weak.id to weak) + (strong0.id to strong0.copy(location = WeaponLocation.Shelf(price))),
            heroes = out.state.heroes + (hero.id to hero),
        )
        val ctx = ResolutionContext(s, content, config)
        assertTrue(Market.evaluate(ctx, hero, weak, ctx.weapon(strongId), 0.5).affordable)
        assertTrue(!Market.evaluate(ctx, hero.copy(gold = hero.gold - 1), weak, ctx.weapon(strongId), 0.5).affordable)

        Market.purchase(ctx, hero, ctx.weapon(strongId), price)
        val after = ctx.toState()
        assertEquals(s.gold + price - credit, after.gold)
        assertEquals(0, after.hero(hero.id).gold)
        assertTrue(after.weapon(weakId).isInStorage, "the old weapon comes back to the shop")
        assertTrue(after.weapon(weakId).history.any { it.kind == "TRADED_IN" })
        assertEquals(strongId, after.equippedWeapon(hero.id)?.id)
        assertEquals(emptyList(), Invariants.check(after, config))
        val sale = after.events.last { it.type == EventType.WEAPON_SOLD }
        assertEquals(credit.toString(), sale.data["tradeIn"])
    }

    @Test
    fun theTownPaysForPatrols() {
        var paid = 0
        for (seed in 1L..10L) {
            val s = engine.newRun(LegacyProfile(), seed)
            val after = s.endDay()
            for (e in after.events.filter { it.type == EventType.HERO_PATROLLED }) {
                val id = HeroId(e.subjectIds.single())
                assertEquals(s.hero(id).gold + config.patrolGold, after.hero(id).gold)
                paid++
            }
        }
        assertTrue(paid > 0)
    }

    // --- Legacy floor ---

    @Test
    fun theShortestFirstRunStillAffordsTheCheapestUpgrade() {
        var s = engine.newRun(LegacyProfile(), 3)
        while (!s.isEnded) s = s.endDay()
        val end = engine.closeRun(s)
        val cheapest = content.upgrades.minOf { it.costPerLevel.first() }
        assertNotNull(end)
        assertTrue(end.totalPoints >= cheapest, "a passive run of ${end.daysSurvived} days banks ${end.totalPoints}, cheapest upgrade $cheapest")
    }
}
