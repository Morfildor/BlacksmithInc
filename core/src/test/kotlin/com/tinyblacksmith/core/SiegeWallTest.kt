package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** X19: a champion can die on the walls, but only in a rout and only one who went up barely fit. */
class SiegeWallTest {
    private val base = BalanceConfig.DEFAULT
    private val fates = base.weaponFates
    private val content = engine.content

    private fun blade(id: WeaponId, owner: HeroId) = Weapon(
        id = id, name = "Test Blade ${id.value}", familyId = LaunchContent.SWORD, coreId = LaunchContent.IRON, augmentId = LaunchContent.EMBER_RESIN,
        mode = ForgeMode.QUICK, risk = Risk.BALANCED, quality = 50, rarity = Rarity.RARE, power = 30, element = null,
        affixes = emptyList(), flaws = emptyList(), location = WeaponLocation.Owned(owner, true), forgedEra = 1, forgedDay = 1,
    )

    /** The siege is today, every hero is armed and stands at [health]; the raid is [ratio] times the town's defense. */
    private fun siege(seed: Long, health: Int, ratio: Double): ResolutionContext {
        val s0 = engine.newRun(LegacyProfile(), seed)
        val s = s0.copy(
            heroes = s0.heroes.mapValues { (_, h) -> h.copy(health = health) },
            weapons = s0.aliveHeroes().mapIndexed { i, h -> WeaponId("w9$i").let { it to blade(it, h.id) } }.toMap(),
            town = s0.town.copy(nextSiegeDay = s0.day),
        )
        val o = Battle.outlook(ResolutionContext(s, content, base), s.day)!!
        val ctx = ResolutionContext(s, content, base.copy(siegeModifier = base.siegeModifier * ratio * o.townDefense / o.raidPower))
        Battle.resolveSiegeIfDue(ctx)
        assertEquals(1, ctx.newEvents.count { it.type == EventType.SIEGE_LOST }, "seed $seed: the siege is lost at $ratio")
        assertEquals(emptyList(), Invariants.check(ctx.toState(), ctx.config), "seed $seed")
        return ctx
    }

    private fun ResolutionContext.champions() = newEvents.single { it.type == EventType.SIEGE_LOST }.subjectIds.map { HeroId(it) }
    private fun ResolutionContext.fallen() = field.filter { it.outcome == FieldOutcome.FELL_AT_THE_WALL }

    @Test
    fun aNarrowLossNeverKillsAHealthyChampion() {
        val weakest = base.heroWoundedThreshold  // nobody weaker is chosen to stand on the wall
        for (seed in 1L..40L) for (ratio in listOf(1.01, 1.2, fates.wallsRoutRatio - 0.001)) {
            val ctx = siege(seed, weakest, ratio)
            val at = "seed $seed ratio $ratio"
            assertEquals(base.championCount, ctx.champions().size, at)
            assertEquals(0, ctx.newEvents.count { it.type == EventType.HERO_DIED }, at)
            assertTrue(ctx.fallen().isEmpty() && "rout" !in ctx.newEvents.single { it.type == EventType.SIEGE_LOST }.data, at)
            for (id in ctx.champions()) assertEquals(weakest - base.championSiegeDamageOnLoss, ctx.hero(id).health, "$at: wounded, alive")
        }
        // A rout does not kill a champion who went up fit either.
        for (seed in 1L..40L) {
            val ctx = siege(seed, fates.wallsRoutDamage + 1, 3.0)
            assertEquals(0, ctx.newEvents.count { it.type == EventType.HERO_DIED }, "seed $seed")
            for (id in ctx.champions()) assertEquals(1, ctx.hero(id).health, "seed $seed")
        }
        assertTrue(fates.wallsRoutDamage < 100 && base.championSiegeDamageOnLoss < base.heroWoundedThreshold, "a full-health champion survives any siege; a narrow loss kills nobody")
    }

    @Test
    fun aRoutCanKillAChampionAtTheThreshold() {
        for (seed in 1L..40L) for (ratio in listOf(fates.wallsRoutRatio + 0.001, 2.5)) for (health in listOf(base.heroWoundedThreshold, fates.wallsRoutDamage)) {
            val ctx = siege(seed, health, ratio)
            val at = "seed $seed ratio $ratio health $health"
            val lost = ctx.newEvents.single { it.type == EventType.SIEGE_LOST }
            assertEquals("true", lost.data["rout"], at)
            assertTrue("routed the defenders" in lost.text, lost.text)
            assertEquals(base.championCount, ctx.fallen().size, at)
            for (f in ctx.fallen()) {
                val hero = ctx.hero(f.heroId)
                assertEquals(HeroFate.DEAD to ctx.day, hero.fate to hero.diedOnDay, at)
                val died = ctx.newEvents.single { it.type == EventType.HERO_DIED && f.heroId.value in it.subjectIds }
                assertTrue("died defending the walls" in died.text && f.eventIds == listOf(lost.id, died.id), at)
            }
            assertTrue(ctx.town.championIds.isEmpty(), "$at: the dead hold no post")
        }
    }

    @Test
    fun theFallenChampionsBladeTakesAWallsFate() {
        val tally = mutableMapOf<WeaponFate, Int>()
        for (seed in 1L..200L) {
            val ctx = siege(seed, base.heroWoundedThreshold, 2.0)
            for (f in ctx.fallen()) {
                val told = ctx.newEvents.filter { WeaponFate.KEY in it.data && f.heroId.value in it.subjectIds }
                assertEquals(1, told.size, "seed $seed ${f.heroName}: one fate for the blade they carried")
                val fate = WeaponFate.valueOf(told.single().data.getValue(WeaponFate.KEY))
                val blade = ctx.weapon(WeaponId(told.single().subjectIds.first()))
                when (fate) {
                    WeaponFate.RECOVERED -> assertTrue(blade.isInStorage, "seed $seed: back at the forge")
                    WeaponFate.MERCHANT -> assertTrue(blade.isWithMerchant, "seed $seed")
                    WeaponFate.SEIZED, WeaponFate.LOST -> assertTrue(blade.location is WeaponLocation.Lost && !blade.isWithMerchant, "seed $seed")
                    else -> error("seed $seed: no guild stands on day 1, yet $fate")
                }
                assertTrue(blade.ownerId == null, "seed $seed: the dead own nothing")
                tally.merge(fate, 1, Int::plus)
            }
        }
        val total = tally.values.sum()
        assertEquals(200 * base.championCount, total)
        // Comrades are close on the walls: the walls' own odds, not the road's.
        assertTrue(tally.getValue(WeaponFate.RECOVERED).toDouble() / total in fates.wallsRecoveryChance - 0.06..fates.wallsRecoveryChance + 0.06, "$tally")
        assertTrue((tally[WeaponFate.SEIZED] ?: 0) > 0 && (tally[WeaponFate.MERCHANT] ?: 0) + (tally[WeaponFate.LOST] ?: 0) > 0, "$tally")
    }
}
