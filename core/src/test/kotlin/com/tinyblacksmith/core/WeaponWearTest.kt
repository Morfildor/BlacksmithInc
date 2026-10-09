package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.battle.Power
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Balance v4 (pending): weapons wear in use, worn power counts in battle and at the shelf, Hone restores the edge. */
class WeaponWearTest {
    private val config = engine.config
    private val content = engine.content

    private fun fresh(seed: Long = 7) = engine.newRun(LegacyProfile(), seed).withMaterials()

    private fun forged(seed: Long = 7, cmd: Command.Forge = quickSword(Risk.SAFE)): Pair<GameState, WeaponId> =
        fresh(seed).forgeAccepted(cmd).let { it.state to it.forgedWeaponId!! }

    private fun GameState.rejected(cmd: Command): GameError = assertIs<CommandOutcome.Rejected>(engine.handle(this, cmd)).error

    /** Same rules with raids a tenth as strong, so the siege due today is certainly won. */
    private fun GameState.endDayWithWeakRaids(): GameState =
        GameEngine(config = config.copy(siegeModifier = config.siegeModifier / 10)).handle(this, Command.EndDay(TestSupport.endDayId(this))).stateOrThrow()

    /** Every hero wields a fresh safe iron sword at [condition]; nothing is on the shelf. */
    private fun armed(seed: Long = 7, condition: Int = 100): GameState {
        var s = fresh(seed)
        for (h in s.aliveHeroes()) {
            val out = s.forgeAccepted(quickSword(Risk.SAFE))
            s = out.state.copy(energy = 10, overworkToday = 0)
            val w = s.weapon(out.forgedWeaponId!!).copy(condition = condition, location = WeaponLocation.Owned(h.id, equipped = true))
            s = s.copy(weapons = s.weapons + (w.id to w))
        }
        return s
    }

    @Test
    fun conditionFactorIsBoundedAndLinear() {
        val (s, id) = forged()
        val w = s.weapon(id)
        assertEquals(100, w.condition, "a forged weapon is keen")
        assertEquals(1.0, Power.conditionFactor(w, config))
        assertEquals(config.conditionFloorFactor, Power.conditionFactor(w.copy(condition = 0), config), 1e-9)
        assertEquals((1.0 + config.conditionFloorFactor) / 2, Power.conditionFactor(w.copy(condition = 50), config), 1e-9)
        assertEquals(1.0, Power.conditionFactor(null, config), "bare hands do not wear")
        assertEquals(config.conditionFloorFactor, Power.conditionFactor(w.copy(condition = -40), config), 1e-9)
        assertEquals(1.0, Power.conditionFactor(w.copy(condition = 140), config))
        var last = 0.0
        for (c in 0..100) { val f = Power.conditionFactor(w.copy(condition = c), config); assertTrue(f >= last); last = f }
        assertTrue(Invariants.check(s.copy(weapons = s.weapons + (id to w.copy(condition = 101))), config).any { "condition" in it })
        assertTrue(Invariants.check(s.copy(weapons = s.weapons + (id to w.copy(condition = -1))), config).any { "condition" in it })
    }

    @Test
    fun powerFallsWithConditionAndOnlyTheWeaponShareWears() {
        val (s, id) = forged()
        val hero = s.aliveHeroes().first()
        val faction = content.faction(LaunchContent.ASHCLAW)
        val keen = s.weapon(id)
        val full = Power.attackPower(hero, keen, faction, content, config)
        val worn = Power.attackPower(hero, keen.copy(condition = 50), faction, content, config)
        val dead = Power.attackPower(hero, keen.copy(condition = 0), faction, content, config)
        assertTrue(full > worn && worn > dead)
        val base = Power.heroBase(hero, content).toDouble()
        val rest = full / (base + keen.power)
        assertEquals((base + keen.power * config.conditionFloorFactor) * rest, dead, 1e-9)
        val fullDef = Power.defensePower(hero, keen, faction, content, config)
        val deadDef = Power.defensePower(hero, keen.copy(condition = 0), faction, content, config)
        assertTrue(deadDef < fullDef && deadDef >= fullDef * config.conditionFloorFactor)
    }

    @Test
    fun anExpeditionWearsTheBladeMoreOnARoutAndNeverBelowZero() {
        var wins = 0
        var losses = 0
        for (seed in 1L..30L) {
            val s = armed(seed)
            val hero = s.aliveHeroes().first()
            val ctx = ResolutionContext(s, content, config)
            val weapon = ctx.equippedWeapon(hero.id)!!
            Battle.resolveExpedition(ctx, hero, weapon, LaunchContent.ASHCLAW, content.faction(LaunchContent.ASHCLAW))
            val won = ctx.newEvents.any { it.type == EventType.EXPEDITION_WON || it.type == EventType.ELITE_SLAIN }
            if (won) wins++ else losses++
            assertEquals(100 - (if (won) config.wearPerExpeditionWin else config.wearPerExpeditionLoss), ctx.weapon(weapon.id).condition)
        }
        assertTrue(wins > 0 && losses > 0, "wins $wins losses $losses")
        val s = armed()
        val hero = s.aliveHeroes().first()
        val nearlyGone = s.equippedWeapon(hero.id)!!.copy(condition = 3)
        val ctx = ResolutionContext(s.copy(weapons = s.weapons + (nearlyGone.id to nearlyGone)), content, config)
        Battle.resolveExpedition(ctx, hero, nearlyGone, LaunchContent.ASHCLAW, content.faction(LaunchContent.ASHCLAW))
        assertEquals(0, ctx.weapon(nearlyGone.id).condition)
        assertTrue(Invariants.check(ctx.toState(), config).isEmpty())
    }

    @Test
    fun aSiegeWearsTheChampionsBlades() {
        val s = armed().let { it.copy(day = it.town.nextSiegeDay) }
        val after = s.endDayWithWeakRaids()
        val champions = after.events.first { it.type == EventType.SIEGE_WON }.subjectIds.map { HeroId(it) }
        assertTrue(champions.isNotEmpty())
        val afterSiege = setOf(100 - config.wearPerSiege, 100 - config.wearPerSiege - config.wearPerExpeditionWin, 100 - config.wearPerSiege - config.wearPerExpeditionLoss)
        for (id in champions) {
            val w = after.equippedWeapon(id)!!
            assertTrue(w.condition in afterSiege, "${w.name} condition ${w.condition} (a champion may also have fought that day)")
        }
        for (h in after.aliveHeroes().filter { it.id !in champions }) {
            val w = after.equippedWeapon(h.id) ?: continue
            assertTrue(w.condition >= 100 - config.wearPerExpeditionLoss, "${w.name} did not stand at the walls")
        }
    }

    @Test
    fun aHeroReplacesAWornBladeTheyWouldOtherwiseKeep() {
        val (s1, ownedId) = forged()
        val out = s1.copy(energy = 10).forgeAccepted(quickSword(Risk.SAFE))
        val listedId = out.forgedWeaponId!!
        val listed0 = out.state.weapon(listedId)
        val hero0 = out.state.aliveHeroes().maxBy { Power.classFit(it, listed0, content, config) }
        // Same power, same family: on its own the listing is no upgrade.
        val owned = out.state.weapon(ownedId).copy(power = listed0.power, location = WeaponLocation.Owned(hero0.id, equipped = true))
        val price = engine.suggestedPrice(listed0)
        val hero = hero0.copy(gold = price, loyalty = 0, ambition = null, elementTaste = null)
        val s = out.state.copy(
            weapons = out.state.weapons + (owned.id to owned) + (listedId to listed0.copy(location = WeaponLocation.Shelf(price))),
            heroes = out.state.heroes + (hero.id to hero),
        )
        val ctx = ResolutionContext(s, content, config)
        val keen = Market.evaluate(ctx, hero, owned, ctx.weapon(listedId), 0.5)
        assertTrue(keen.gain <= 0 && !keen.worn, "a keen blade of equal power is kept")

        val worn = owned.copy(condition = config.wornConditionThreshold - 1)
        val eval = Market.evaluate(ctx, hero, worn, ctx.weapon(listedId), 0.5)
        assertTrue(eval.worn && eval.gain > 0, "improvement ${eval.gain}")
        assertTrue(eval.utility >= config.purchaseUtilityThreshold, "utility ${eval.utility}")
        assertTrue(Market.tradeInCredit(worn, config) < Market.tradeInCredit(owned, config), "a worn trade-in is worth less")
        assertTrue(engine.suggestedPrice(worn) < engine.suggestedPrice(owned), "a worn listing is priced for its wear")

        // Through a real End Day the purchase is reported as a replacement, and the worn blade comes back to the shop.
        val wornCtx = ResolutionContext(s.copy(weapons = s.weapons + (worn.id to worn)), content, config)
        Market.purchase(wornCtx, hero, wornCtx.weapon(listedId), price)
        val after = wornCtx.toState()
        assertEquals(listedId, after.equippedWeapon(hero.id)?.id)
        assertTrue(after.weapon(ownedId).isInStorage && after.weapon(ownedId).condition == worn.condition)
        assertTrue(after.weapon(ownedId).canBeHoned)
        assertTrue(Invariants.check(after, config).isEmpty())
        var replacements = 0
        for (seed in 1L..12L) {
            var world = armed(seed, condition = config.wornConditionThreshold - 10)
            val forgedOut = world.forgeAccepted(quickSword(Risk.SAFE))
            world = forgedOut.state.run(Command.ToggleShelf(forgedOut.forgedWeaponId!!, true))
            val day = world.endDay()
            replacements += day.lastResolution!!.visits.count { it.reason == VisitReason.WORN_OUT }
            assertTrue(armed(seed).forgeAccepted(quickSword(Risk.SAFE)).let { o -> o.state.run(Command.ToggleShelf(o.forgedWeaponId!!, true)) }.endDay()
                .lastResolution!!.visits.none { it.reason == VisitReason.WORN_OUT })
        }
        assertTrue(replacements > 0, "worn heroes replaced their blades $replacements times over 12 seeds")
    }

    @Test
    fun honeRestoresConditionAndAWornBladeCanBeHonedAgain() {
        val (s0, id) = forged()
        val s = s0.copy(weapons = s0.weapons + (id to s0.weapon(id).copy(condition = 40)))
        val before = s.weapon(id)
        val first = s.run(Command.Hone(id))
        val honed = first.weapon(id)
        assertEquals(100, honed.condition)
        assertEquals(minOf(100, before.quality + config.honeQualityBonus), honed.quality)
        assertTrue(honed.honed && !honed.canBeHoned)
        assertIs<GameError.AlreadyHoned>(first.rejected(Command.Hone(id)))

        val wornAgain = first.copy(weapons = first.weapons + (id to honed.copy(condition = 25)))
        assertTrue(wornAgain.weapon(id).canBeHoned)
        val second = wornAgain.run(Command.Hone(id))
        val again = second.weapon(id)
        assertEquals(100, again.condition)
        assertEquals(honed.quality, again.quality, "the quality bonus is spent the first time")
        assertEquals(honed.power, again.power)
        assertEquals(first.energy - config.honeEnergy, second.energy)
        assertEquals(first.materials.getValue(LaunchContent.IRON) - 1, second.materials.getValue(LaunchContent.IRON))
        assertTrue(second.events.last { it.type == EventType.WEAPON_HONED }.text.contains("keen edge"))
        assertEquals(2, again.history.count { it.kind == "HONED" })
        assertIs<GameError.AlreadyHoned>(second.rejected(Command.Hone(id)))
    }

    @Test
    fun conditionSurvivesASaveAndOldSavesDecodeAsKeen() {
        val (s, id) = forged()
        val worn = s.copy(weapons = s.weapons + (id to s.weapon(id).copy(condition = 37)))
        assertEquals(37, SaveCodec.decodeRun(SaveCodec.encodeRun(worn)).weapon(id).condition)
        val json = SaveCodec.json.encodeToString(Weapon.serializer(), s.weapon(id))
        assertTrue(json.contains("\"condition\":100"))
        val old = SaveCodec.json.decodeFromString(Weapon.serializer(), json.replace(",\"condition\":100", ""))
        assertEquals(100, old.condition)
        val fixture = WeaponWearTest::class.java.getResource("/saves/v1_forced_seed4242_day61.json")!!.readText()
        assertTrue(SaveCodec.decodeRun(fixture).weapons.values.all { it.condition == 100 }, "a v1 save decodes with every blade keen")
    }

    @Test
    fun wearIsDeterministic() {
        fun play(): GameState { var s = armed(11); repeat(12) { if (!s.isEnded) s = s.endDay() }; return s }
        val a = play()
        val b = play()
        assertEquals(a, b)
        assertTrue(a.weapons.values.any { it.condition < 100 }, "something wore")
        assertTrue(Invariants.check(a, config).isEmpty())
    }
}
