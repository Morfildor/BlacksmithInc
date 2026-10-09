package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.shopday.Advice
import com.tinyblacksmith.core.shopday.Demand
import com.tinyblacksmith.core.shopday.LeadKind
import com.tinyblacksmith.core.shopday.Lines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** E1 (G02, B02): a served hero who buys nothing leaves a standing want, and comes back for a blade that answers it. */
class WantsTest {
    private val config = engine.config
    private val content = engine.content
    private val cfg = config.customers

    /** A day-1 town with [blades] plain swords of [power] on the shelf at [price]; nobody carries anything. */
    private fun town(seed: Long = 7, blades: Int = 2, price: Int = 60, power: Int = 30, edit: (Hero) -> Hero = { it }): GameState {
        val out = engine.newRun(LegacyProfile(), seed).withMaterials().forgeAccepted(quickSword(Risk.SAFE))
        val sword = out.state.weapon(out.forgedWeaponId!!).copy(affixes = emptyList(), flaws = emptyList(), element = null, power = power)
        return out.state.copy(
            heroes = out.state.heroes.mapValues { edit(it.value) },
            weapons = (1..blades).associate { i -> WeaponId("w$i") to sword.copy(id = WeaponId("w$i"), location = WeaponLocation.Shelf(price)) },
            commissions = emptyMap(),
        )
    }

    private fun suitsSword(h: Hero) = (content.family(LaunchContent.SWORD).classFit[h.classId] ?: config.offFamilyFit) >= 1.0

    @Test
    fun aRefusalRecordsWhatWouldHaveSold() {
        var refusals = 0
        for (seed in 1L..10L) {
            val s = town(seed, price = 9999)
            val out = s.endDayAccepted()
            val browsers = out.resolution!!.browsers
            assertTrue(browsers.all { it.purchasedWeaponId == null }, "nobody can pay 9999")
            for (v in browsers) {
                val before = s.hero(v.heroId!!)
                val want = assertNotNull(out.state.hero(before.id).want, "${before.fullName} left with nothing")
                assertEquals(if (suitsSword(before)) LaunchContent.SWORD else content.heroClass(before.classId).preferredFamilies.first(), want.familyId, "the family they would have taken")
                assertEquals(v.customer!!.gold, want.budget, "an unarmed hero's budget is the purse they walked in with")
                assertEquals(s.day, want.sinceDay)
                assertTrue(want.minPower > config.unarmedPower, "a blade must beat bare hands by enough to be worth buying")
                refusals++
            }
            val served = browsers.mapNotNull { it.heroId }.toSet()
            for (h in out.state.heroes.values) if (h.id !in served) assertNull(h.want, "${h.fullName} was not at the counter")
        }
        assertTrue(refusals >= 20, "the fixture serves customers ($refusals)")
        // A hero who would have bought the blade had it been affordable is answered by it once it is.
        val s = town(price = 9999) { it.copy(gold = 500) }
        val after = s.endDayAccepted()
        val asker = after.resolution!!.browsers.map { after.state.hero(it.heroId!!) }.first { it.isAlive && it.want!!.familyId == LaunchContent.SWORD }
        val cheap = after.state.copy(weapons = after.state.weapons.mapValues { it.value.copy(location = WeaponLocation.Shelf(60)) })
        assertTrue(asker.want!!.minPower <= 30, "the plain sword on the shelf clears what was recorded (${asker.want})")
        assertTrue(Market.answersWant(ResolutionContext(cheap, content, config), cheap.hero(asker.id), cheap.weapon(WeaponId("w1"))))
    }

    @Test
    fun aWantLapsesAfterThreeDaysOrAPurchase() {
        val start = town(blades = 0)
        val hero = start.aliveHeroes().first()
        val want = Want(LaunchContent.SWORD, 10, 80, start.day)
        var s = start.copy(heroes = start.heroes + (hero.id to hero.copy(want = want, health = 1)))   // too hurt to go out: they rest and live
        repeat(cfg.wantLapseDays) {
            s = s.endDay()
            assertEquals(want, s.hero(hero.id).want, "still asking on the morning of day ${s.day}")
        }
        s = s.endDay()
        assertNull(s.hero(hero.id).want, "lapsed on the morning of day ${s.day}")

        // Any purchase ends it at once.
        val shop = town { it.copy(gold = 500, want = want) }
        val ctx = ResolutionContext(shop, content, config)
        val buyer = ctx.aliveHeroes().first()
        Market.purchase(ctx, buyer, ctx.weapon(WeaponId("w1")), 60)
        assertNull(ctx.hero(buyer.id).want)
        // And a refusal for the same family keeps the day the want was first voiced.
        val dear = town(price = 9999) { it.copy(want = Want(if (suitsSword(it)) LaunchContent.SWORD else content.heroClass(it.classId).preferredFamilies.first(), 1, 1, 0)) }.copy(day = 2)
        val out = dear.endDayAccepted()
        for (v in out.resolution!!.browsers) assertEquals(0, out.state.heroes.getValue(v.heroId!!).let { h -> h.want?.sinceDay ?: 0 }, "the clock is not reset by looking in again")
    }

    @Test
    fun anAnsweredWantRaisesWillingness() {
        val base = town { it.copy(gold = 500) }
        val hero = base.aliveHeroes().first { suitsSword(it) }
        fun with(want: Want?, edit: (GameState) -> GameState = { it }) = edit(base.copy(heroes = base.heroes + (hero.id to hero.copy(want = want)))).let { s -> ResolutionContext(s, content, config) to s.hero(hero.id) }
        val (plainCtx, plain) = with(null)
        val usual = Market.willingness(plainCtx, plain, false)
        val weight = Market.seatWeight(plainCtx, plain)
        assertTrue(usual + cfg.needWantMet < cfg.visitCeiling, "the fixture is not at the ceiling")

        val sword = Want(LaunchContent.SWORD, 10, 500, base.day)
        val (ctx, asking) = with(sword)
        assertEquals(usual + cfg.needWantMet, Market.willingness(ctx, asking, false), 1e-12, "the shelf holds the sword they asked for")
        assertEquals(weight + cfg.seatWantWeight, Market.seatWeight(ctx, asking), 1e-12)

        // Not for another family, not at a price they cannot pay, not for a blade no better than their own, not off the shelf.
        val (bowCtx, bow) = with(sword.copy(familyId = LaunchContent.BOW))
        assertEquals(usual, Market.willingness(bowCtx, bow, false), 1e-12)
        val (dearCtx, dear) = with(sword) { s -> s.copy(weapons = s.weapons.mapValues { it.value.copy(location = WeaponLocation.Shelf(9999)) }) }
        assertEquals(usual, Market.willingness(dearCtx, dear, false), 1e-12)
        val (armedCtx, armed) = with(sword) { s -> s.copy(weapons = s.weapons + (WeaponId("w9") to s.weapon(WeaponId("w1")).copy(id = WeaponId("w9"), power = 60, location = WeaponLocation.Owned(hero.id, true)))) }
        assertEquals(usual, Market.willingness(armedCtx, armed, false), 1e-12)
        val (storedCtx, stored) = with(sword) { s -> s.copy(weapons = s.weapons.mapValues { it.value.copy(location = WeaponLocation.Storage) }) }
        assertEquals(usual, Market.willingness(storedCtx, stored, false), 1e-12)

        // The fallback switch: with the need term at 0 a want moves neither number.
        val off = config.copy(customers = cfg.copy(needWantMet = 0.0))
        val offCtx = ResolutionContext(ctx.base, content, off)
        assertEquals(usual, Market.willingness(offCtx, asking, false), 1e-12)
        assertEquals(weight, Market.seatWeight(offCtx, asking), 1e-12)
    }

    @Test
    fun wantsLeaveEveryRngStreamUnchanged() {
        // With the need term off, a town full of wants and the same town without them resolve the same day draw for draw.
        val inert = GameEngine(config = config.copy(customers = cfg.copy(needWantMet = 0.0)))
        fun GameState.close(e: GameEngine) = e.handle(this, Command.EndDay(TestSupport.endDayId(this))).stateOrThrow()
        fun GameState.forgotten() = copy(heroes = heroes.mapValues { it.value.copy(want = null) })
        var wants = 0
        for (seed in 1L..5L) {
            var s = town(seed, blades = 3, price = 70)
            repeat(8) {
                if (s.isEnded) return@repeat
                wants += s.heroes.values.count { it.want != null }
                val kept = s.close(inert)
                val stripped = s.forgotten().close(inert)
                assertEquals(stripped.rng, kept.rng, "seed $seed day ${s.day}: recording a want draws nothing")
                assertEquals(stripped.forgotten().copy(lastResolution = null), kept.forgotten().copy(lastResolution = null), "seed $seed day ${s.day}: and decides nothing")
                s = kept
            }
        }
        assertTrue(wants > 20, "the runs carried wants ($wants)")
    }

    @Test
    fun theShopNamesAnUnansweredWant() {
        val base = town(blades = 1, price = 9999)
        val hero = base.aliveHeroes().first { suitsSword(it) }
        val want = Want(LaunchContent.SWORD, 12, 93, base.day)
        val s = base.copy(heroes = base.heroes + (hero.id to hero.copy(want = want)))
        val demand = Demand.summary(s, content, config)
        assertEquals(listOf(hero.id), demand.wants)
        assertEquals(emptyList(), demand.wantsAnswered, "9999 gold answers nobody")
        val lead = Advice.lead(s, content, config)
        assertEquals(LeadKind.ANSWER_WANT, lead.kind)
        assertEquals(listOf(hero.id, LaunchContent.SWORD, 93, 12), listOf(lead.heroId, lead.familyId, lead.gold, lead.power))
        assertEquals("${hero.fullName} wants a sword; can spend about 90 gold.", Lines.want(s.hero(hero.id), content))
        val line = Lines.lead(lead, s, content, config)
        assertEquals("Forge a sword for ${hero.fullName}", line.action)
        assertTrue("about 90 gold" in line.reason!! && "%" !in line.reason!!, line.reason)
        // Once a listed blade answers it, the want is no longer the day's lead.
        val rich = s.copy(heroes = s.heroes + (hero.id to hero.copy(want = want, gold = 500)), weapons = s.weapons.mapValues { it.value.copy(location = WeaponLocation.Shelf(60)) })
        assertEquals(listOf(hero.id), Demand.summary(rich, content, config).wantsAnswered)
        assertTrue(Advice.lead(rich, content, config).kind != LeadKind.ANSWER_WANT)
        assertNull(Lines.want(hero, content), "no want, no line")
    }
}
