package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.shopday.Demand
import com.tinyblacksmith.core.shopday.DemandSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Who is buying" (U05): counts of the living heroes and the shelf as they stand, by the counter's own rules. */
class DemandTest {
    private val config = engine.config
    private val content = engine.content
    private val fresh: GameState = engine.newRun(LegacyProfile(), 7)
    private val template: Weapon = fresh.withMaterials().forgeAccepted(quickSword(Risk.SAFE)).let { it.state.weapon(it.forgedWeaponId!!) }

    private fun blade(id: String, family: WeaponFamilyId = LaunchContent.SWORD, location: WeaponLocation, condition: Int = 100) =
        template.copy(id = WeaponId(id), familyId = family, location = location, condition = condition)

    @Test
    fun aFreshTownIsUnarmedAndTheShelfBare() {
        val d = Demand.summary(fresh, content, config)
        val heroes = fresh.aliveHeroes()
        assertEquals(DemandSummary(heroes.size, heroes.map { it.id }, emptyList(), heroes.map { it.classId }.distinct().sortedBy { it.value }, null, null, 0, 0), d)
    }

    @Test
    fun countsUnarmedWornUnservedAndPurseBands() {
        val heroes = fresh.aliveHeroes()
        val (a, b, c) = heroes
        // a carries a worn sword, b a keen one, everyone else nothing; purses are set by hand.
        val weapons = listOf(
            blade("own-a", location = WeaponLocation.Owned(a.id, equipped = true), condition = config.wornConditionThreshold - 1),
            blade("own-b", location = WeaponLocation.Owned(b.id, equipped = true), condition = config.wornConditionThreshold),
            blade("spare-c", location = WeaponLocation.Owned(c.id, equipped = false), condition = 10),  // a spare is not what they carry
            blade("s1", location = WeaponLocation.Shelf(40)), blade("s2", location = WeaponLocation.Shelf(100)),
            blade("s3", location = WeaponLocation.Shelf(100)), blade("s4", location = WeaponLocation.Shelf(900)),
            blade("stored", LaunchContent.BOW, WeaponLocation.Storage),
        )
        val purse = mapOf(a.id to 0, b.id to 99, c.id to 100)
        val s = fresh.copy(weapons = weapons.associateBy { it.id }, heroes = fresh.heroes.mapValues { (id, h) -> h.copy(gold = purse[id] ?: 40) })
        val d = Demand.summary(s, content, config)

        assertEquals(heroes.size, d.living)
        assertEquals(heroes.map { it.id } - a.id - b.id, d.unarmed)
        assertEquals(listOf(a.id), d.worn, "below the threshold, and only the blade in hand")
        assertEquals(listOf(40, 100), listOf(d.cheapestPrice, d.medianPrice), "the lower median of 40, 100, 100, 900")
        // Purse plus trade-in credit, as the counter counts it: a has no coin but a blade to trade.
        val credit = mapOf(a.id to Market.tradeInCredit(weapons[0], config), b.id to Market.tradeInCredit(weapons[1], config))
        fun canPay(price: Int) = s.aliveHeroes().count { it.gold + (credit[it.id] ?: 0) >= price }
        assertEquals(listOf(canPay(40), canPay(100)), listOf(d.canAffordCheapest, d.canAffordMedian))
        assertTrue(credit.getValue(b.id) >= 1, "the scenario: b's 99 gold and a trade-in reach 100")
        assertEquals(heroes.size - (if (credit.getValue(a.id) >= 40) 0 else 1), d.canAffordCheapest)
        assertEquals(2, d.canAffordMedian, "b by trade-in and c by purse")

        // Only swords are listed: the classes a sword does not suit are unserved; the stored bow serves nobody until it is listed.
        fun suits(family: WeaponFamilyId, cls: HeroClassId) = (content.family(family).classFit[cls] ?: config.offFamilyFit) >= 1.0
        val classes = heroes.map { it.classId }.distinct().sortedBy { it.value }
        assertEquals(classes.filter { !suits(LaunchContent.SWORD, it) }, d.unservedClasses)
        assertTrue(d.unservedClasses.isNotEmpty() && d.unservedClasses.size < classes.size, "the scenario: ${d.unservedClasses} of $classes")
        val withBow = s.copy(weapons = s.weapons + (WeaponId("stored") to weapons.last().copy(location = WeaponLocation.Shelf(55))))
        assertEquals(classes.filter { !suits(LaunchContent.SWORD, it) && !suits(LaunchContent.BOW, it) }, Demand.summary(withBow, content, config).unservedClasses)

        // A dead hero is nobody's customer.
        val fewer = s.copy(heroes = s.heroes + (a.id to s.hero(a.id).copy(fate = HeroFate.DEAD)))
        assertEquals(listOf(heroes.size - 1, emptyList<HeroId>()), Demand.summary(fewer, content, config).let { listOf(it.living, it.worn) })
    }

    @Test
    fun summaryReadsTheStateAndNothingElse() {
        var s = fresh
        repeat(12) {
            if (s.isEnded) return@repeat
            val d = Demand.summary(s, content, config)
            assertEquals(d, Demand.summary(s, content, config))
            assertEquals(s.aliveHeroes().size, d.living)
            assertTrue(d.unarmed.none { it in d.worn } && (d.unarmed + d.worn).all { s.hero(it).isAlive })
            assertEquals(s.aliveHeroes().count { s.equippedWeapon(it.id) == null }, d.unarmed.size)
            assertTrue(d.canAffordMedian <= d.canAffordCheapest && d.canAffordCheapest <= d.living)
            s = s.endDay()
        }
    }
}
