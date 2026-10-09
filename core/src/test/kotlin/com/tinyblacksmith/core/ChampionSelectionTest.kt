package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.battle.Power
import com.tinyblacksmith.core.content.BlessingEffect
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** F08: the three champions are ranked against the foe they will face, the same context their powers are valued with. */
class ChampionSelectionTest {
    private val config = engine.config
    private val content = engine.content

    private fun GameState.pressure(factionId: FactionId, value: Int) =
        copy(factions = factions.mapValues { (id, f) -> f.copy(pressure = if (id == factionId) value else 0) })

    /** Every hero carries a copy of one plain sword; every second one has Giant Slayer cut into it. */
    private fun armed(seed: Long): GameState {
        var s = engine.newRun(LegacyProfile(), seed).withMaterials()
        s.aliveHeroes().forEachIndexed { i, h ->
            val out = s.forgeAccepted(quickSword(Risk.SAFE))
            val w = out.state.weapon(out.forgedWeaponId!!).copy(
                affixes = if (i % 2 == 0) listOf(LaunchContent.GIANT_SLAYER) else emptyList(), flaws = emptyList(), element = null,
                location = WeaponLocation.Owned(h.id, equipped = true),
            )
            s = out.state.copy(energy = 10, overworkToday = 0, weapons = out.state.weapons + (w.id to w))
        }
        return s
    }

    /** Four heroes alike in everything but the blade: three plain swords of power 30, one Giant Slayer sword of power 20. */
    private fun fourAlike(): Pair<GameState, HeroId> {
        val s = armed(7)
        val model = s.aliveHeroes().first()
        val blade = s.equippedWeapon(model.id)!!.copy(affixes = emptyList(), condition = 100, fame = 0)
        val heroes = (1..4).map { model.copy(id = HeroId("t$it"), health = 100) }
        val weapons = heroes.mapIndexed { i, h ->
            blade.copy(id = WeaponId("tw$i"), power = if (i == 3) 20 else 30, affixes = if (i == 3) listOf(LaunchContent.GIANT_SLAYER) else emptyList(), location = WeaponLocation.Owned(h.id, equipped = true))
        }
        return s.copy(heroes = heroes.associateBy { it.id }, weapons = weapons.associateBy { it.id }) to heroes[3].id
    }

    @Test
    fun aGiantSlayerBearerEntersTheTopThreeOnlyForAWarlordSiege() {
        val (s, bearer) = fourAlike()
        val raid = engine.siegeForecast(s.pressure(LaunchContent.ASHCLAW, config.warlordPressure - 1))!!
        val led = engine.siegeForecast(s.pressure(LaunchContent.ASHCLAW, config.warlordPressure))!!
        assertTrue(!raid.warlord && led.warlord)
        assertEquals(listOf("t1", "t2", "t3"), raid.champions.map { it.first.id.value }, "against a plain raid the weaker Giant Slayer blade stays home")
        assertEquals(bearer, led.champions.first().first.id, "against a warlord it is the strongest blade on the wall")
        assertEquals(listOf("t4", "t1", "t2"), led.champions.map { it.first.id.value })
        assertTrue(led.townDefense > raid.townDefense)
    }

    @Test
    fun rankingAndContributionUseTheSameFoeContext() {
        var warlordSieges = 0
        var reordered = 0
        for (seed in 1L..20L) for (pressure in listOf(config.warlordPressure - 1, config.warlordPressure + 10)) {
            val s = armed(seed).pressure(LaunchContent.HOLLOWBOUND, pressure)
            val o = engine.siegeForecast(s)!!
            val ctx = ResolutionContext(s, content, config)
            fun power(h: Hero, elite: Boolean) = Power.defensePower(h, s.equippedWeapon(h.id), o.faction, content, config, ctx.blessingMagnitude(BlessingEffect.HERO_POWER), elite)
            val fit = s.aliveHeroes().filter { it.health >= config.heroWoundedThreshold }
            val ranked = fit.sortedWith(compareByDescending<Hero> { power(it, o.warlord) }.thenBy(IdOrder.numeric) { it.id.value }).take(config.championCount)
            assertEquals(ranked.map { it.id }, o.champions.map { it.first.id }, "seed $seed pressure $pressure")
            assertEquals(ranked.map { power(it, o.warlord) }, o.championPowers, "each champion is valued in the context it was ranked in")
            assertEquals(o.championPowers.sortedDescending(), o.championPowers)
            assertEquals(o.champions.map { it.first.id }, Battle.selectChampions(ctx, o.faction, o.warlord).map { it.first.id })
            if (o.warlord) {
                warlordSieges++
                if (ranked.map { it.id } != fit.sortedWith(compareByDescending<Hero> { power(it, false) }.thenBy(IdOrder.numeric) { it.id.value }).take(config.championCount).map { it.id }) reordered++
            }
        }
        assertEquals(20, warlordSieges)
        assertTrue(reordered > 0, "no seed where the warlord context changes who stands on the wall: the test would not see the rule")
    }

    @Test
    fun townChampionIdsAgreeWithTheForecast() {
        var mornings = 0
        var warlordMornings = 0
        for (seed in 1L..12L) {
            // A town of twelve armed heroes holds the pressure down, so every second town starts with a warlord already gathering.
            var s = armed(seed).let { if (seed % 2 == 0L) it.pressure(LaunchContent.HOLLOWBOUND, config.warlordPressure + 30) else it }
            repeat(20) {
                if (s.isEnded) return@repeat
                s = s.endDay()
                if (s.isEnded) return@repeat
                val o = engine.siegeForecast(s)!!
                assertEquals(o.champions.map { it.first.id }, s.town.championIds, "seed $seed day ${s.day}")
                mornings++
                if (o.warlord) warlordMornings++
            }
        }
        assertTrue(mornings > 100 && warlordMornings > 0,"$mornings mornings, $warlordMornings under a warlord")
    }
}
