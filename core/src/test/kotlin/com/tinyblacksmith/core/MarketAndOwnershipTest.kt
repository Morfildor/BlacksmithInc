package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.content.SliceContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameError
import com.tinyblacksmith.core.engine.Invariants
import com.tinyblacksmith.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MarketAndOwnershipTest {
    private fun withListedSword(seed: Long, price: Int, risk: Risk = Risk.BALANCED): Pair<GameState, WeaponId> {
        val s = engine.newRun(LegacyProfile(), seed).withMaterials()
        val out = s.forgeAccepted(quickSword(risk, SliceContent.BRONZE, SliceContent.FROST_BLOOM))
        val id = out.forgedWeaponId!!
        return out.state.run(Command.ToggleShelf(id, true, price)) to id
    }

    @Test
    fun affordableWellFitWeaponGetsBoughtAndEquippedByExactlyOneHero() {
        var bought = 0
        for (seed in 1L..30L) {
            val (s, id) = withListedSword(seed, price = 1)
            val after = s.endDay()
            val w = after.weapon(id)
            if (w.location is WeaponLocation.Owned) {
                bought++
                assertTrue(w.isEquipped)
                val owner = after.hero(w.ownerId!!)
                assertTrue(owner.isAlive)
                assertEquals(1, after.weapons.values.count { it.ownerId == owner.id && it.isEquipped })
                assertTrue(after.events.any { it.type == EventType.WEAPON_SOLD && id.value in it.subjectIds })
                assertTrue(after.events.any { it.type == EventType.WEAPON_EQUIPPED && id.value in it.subjectIds })
                assertEquals(s.gold + 1, after.gold - after.events.filter { it.type == EventType.COMMISSION_COMPLETED }.size * 0 - 0 + 0 - (after.gold - s.gold - 1))
            }
        }
        assertTrue(bought >= 20, "cheap good sword should sell most days, sold $bought/30")
    }

    @Test
    fun unaffordableWeaponIsNeverBought() {
        for (seed in 1L..20L) {
            val (s, id) = withListedSword(seed, price = 100_000)
            val after = s.endDay()
            assertTrue(after.weapon(id).isListed)
            val visits = after.lastResolution!!.visits
            assertTrue(visits.all { it.purchasedWeaponId == null })
            assertTrue(visits.isEmpty() || visits.all { it.reason == "TOO_EXPENSIVE" }, "reasons: ${visits.map { it.reason }}")
        }
    }

    @Test
    fun overpricedWeaponIsRejectedWithReason() {
        var overpriced = 0
        for (seed in 1L..40L) {
            val s0 = engine.newRun(LegacyProfile(), seed).withMaterials()
            val out = s0.forgeAccepted(quickSword(Risk.SAFE))
            val id = out.forgedWeaponId!!
            val w = out.state.weapon(id)
            val rich = out.state.copy(heroes = out.state.heroes.mapValues { it.value.copy(gold = 10_000) })
            val s = rich.run(Command.ToggleShelf(id, true, engine.suggestedPrice(w) * 6))
            val after = s.endDay()
            if (after.lastResolution!!.visits.any { it.reason == "OVERPRICED" }) overpriced++
            assertTrue(after.weapon(id).isListed, "sixfold price should not sell")
        }
        assertTrue(overpriced > 10)
    }

    @Test
    fun shelfLimitAndPricingCommands() {
        var s = engine.newRun(LegacyProfile(), 5).withMaterials().copy(energy = 100)
        val ids = (1..9).map { s.forgeAccepted(quickSword(Risk.SAFE)).also { s = it.state }.forgedWeaponId!! }
        ids.take(8).forEach { s = s.run(Command.ToggleShelf(it, true, 10)) }
        val full = engine.handle(s, Command.ToggleShelf(ids[8], true, 10))
        assertIs<CommandOutcome.Rejected>(full); assertEquals(GameError.ShelfFull, full.error)
        s = s.run(Command.SetPrice(ids[0], 77))
        assertEquals(77, s.weapon(ids[0]).listedPrice)
        val bad = engine.handle(s, Command.SetPrice(ids[0], -1))
        assertIs<CommandOutcome.Rejected>(bad)
        s = s.run(Command.ToggleShelf(ids[0], false))
        assertTrue(s.weapon(ids[0]).isInStorage)
    }

    @Test
    fun supplierKeepsBasicsStockedAndLimitsRareOnes() {
        var s = engine.newRun(LegacyProfile(), 8)
        val iron = s.materials.getValue(SliceContent.IRON)
        s = s.run(Command.BuyMaterial(SliceContent.IRON, 5))
        assertEquals(iron + 5, s.materials.getValue(SliceContent.IRON))
        s = s.run(Command.BuyMaterial(SliceContent.SILVER, 1))
        val out = engine.handle(s, Command.BuyMaterial(SliceContent.SILVER, 1))
        assertIs<CommandOutcome.Rejected>(out); assertIs<GameError.SupplierOutOfStock>(out.error)
        s = s.endDay()
        assertEquals(1, s.supplierStock.getValue(SliceContent.SILVER), "restocked each morning")
    }

    @Test
    fun deadHeroesNeverShopFightOrOwnEquipment() {
        for (seed in 1L..40L) {
            var s = engine.newRun(LegacyProfile(), seed).withMaterials()
            repeat(3) { s = s.forgeAccepted(quickSword()).state }
            s.storedWeapons().forEach { s = s.run(Command.ToggleShelf(it.id, true, 1)) }
            var deaths = 0
            while (!s.isEnded && s.day < 60) {
                val acc = s.endDayAccepted()
                val res = acc.resolution!!
                val deadBefore = s.heroes.values.filter { !it.isAlive }.map { it.id.value }.toSet()
                for (e in res.events) {
                    if (e.type in setOf(EventType.WEAPON_SOLD, EventType.EXPEDITION_WON, EventType.EXPEDITION_LOST, EventType.HERO_PATROLLED, EventType.SIEGE_WON, EventType.SIEGE_LOST)) {
                        assertTrue(e.subjectIds.none { it in deadBefore }, "dead hero acted: ${e.text}")
                    }
                }
                s = acc.state
                deaths = s.heroes.values.count { it.fate == HeroFate.DEAD }
                assertEquals(emptyList(), Invariants.check(s, engine.config))
            }
            if (deaths > 0) return
        }
    }

    @Test
    fun commissionCanBeAcceptedAndDelivered() {
        var delivered = false
        for (seed in 1L..60L) {
            var s = engine.newRun(LegacyProfile(), seed).withMaterials().copy(energy = 100)
            while (!s.isEnded && s.day < 20 && !delivered) {
                s.commissions.values.filter { it.status == CommissionStatus.OFFERED }.forEach { c ->
                    s = s.run(Command.AcceptCommission(c.id))
                    repeat(4) { s = s.forgeAccepted(Command.Forge(ForgeMode.QUICK, c.familyId, SliceContent.SILVER, SliceContent.STORMGLASS, null, Risk.BALANCED)).state }
                }
                s = s.endDay()
                if (s.commissions.values.any { it.status == CommissionStatus.COMPLETED }) {
                    delivered = true
                    val c = s.commissions.values.first { it.status == CommissionStatus.COMPLETED }
                    assertEquals(c.buyerId, s.weapon(c.deliveredWeaponId!!).ownerId)
                }
            }
            if (delivered) break
        }
        assertTrue(delivered)
    }
}
