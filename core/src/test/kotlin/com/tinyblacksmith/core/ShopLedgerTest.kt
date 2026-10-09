package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.endDayId
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F07, X04, X05: every coin End Day adds is entered under its kind, and every hero's day has a typed result. */
class ShopLedgerTest {
    private val config = engine.config
    private val content = engine.content
    private val quiet = GameEngine(config = config.copy(worldEventChancePerDay = 0.0))

    private fun fresh(seed: Long = 7) = engine.newRun(LegacyProfile(), seed).withMaterials()
    private fun names(s: GameState) = s.heroes.values.associate { it.id.value to it.fullName }
    private fun GameState.tryRun(command: Command): GameState = (engine.handle(this, command) as? CommandOutcome.Accepted)?.state ?: this
    private fun edition(s: GameState, r: DayResolution) = Gazette.edition(r.events, names(s), r.visits, r.ledger, r.field)

    private fun balances(l: ShopLedger) = assertEquals(l.goldAtClose, l.goldAtOpen + l.income.values.sum(), "the ledger balances: $l")

    @Test
    fun saleWithBonusAndTradeInSplitsCoinCreditAndBonus() {
        val first = fresh().forgeAccepted(quickSword(Risk.SAFE))
        val second = first.state.copy(energy = 10).forgeAccepted(quickSword(Risk.SAFE))
        val hero0 = second.state.aliveHeroes().first()
        val old = second.state.weapon(first.forgedWeaponId!!).copy(location = WeaponLocation.Owned(hero0.id, equipped = true))
        val new0 = second.state.weapon(second.forgedWeaponId!!).let { it.copy(power = old.power + 20) }
        val price = engine.suggestedPrice(new0)
        val credit = Market.tradeInCredit(old, config)
        assertTrue(credit in 1 until price)
        val s = second.state.copy(
            weapons = second.state.weapons + (old.id to old) + (new0.id to new0.copy(location = WeaponLocation.Shelf(price))),
            heroes = second.state.heroes + (hero0.id to hero0.copy(gold = price)),
            blessings = listOf(ActiveBlessing(LaunchContent.MERCHANTS_FAVOR, second.state.day + 3)),
        )
        val bonus = price * 20 / 100
        val ctx = ResolutionContext(s, content, config)
        val sale = Market.purchase(ctx, ctx.hero(hero0.id), ctx.weapon(new0.id), price)
        assertEquals(Sale(listedPrice = price, tradeInCredit = credit, tradeInWeaponId = old.id, cashPaid = price - credit, saleBonus = bonus), sale)
        assertEquals(mapOf(IncomeKind.SHELF_SALE to price - credit, IncomeKind.SALE_BONUS to bonus), ctx.income.toMap())
        assertEquals(credit, ctx.tradeInCreditToday)
        assertEquals(s.gold + sale.cashPaid + sale.saleBonus + sale.stipend, ctx.gold, "the shop's gold rises by coin, bonus and stipend")
        assertEquals(price - (price - credit), ctx.hero(hero0.id).gold, "the customer paid the coin and no more")
        assertEquals(bonus.toString(), ctx.newEvents.single { it.type == EventType.WEAPON_SOLD }.data["bonus"], "the bonus is on the record")
    }

    @Test
    fun commissionOnlyDayCountsNoBrowserAsBuyer() {
        var checked = false
        for (seed in 1L..30L) {
            val out = fresh(seed).forgeAccepted(quickSword(Risk.SAFE))
            val patron = out.state.aliveHeroes().last()
            val c = Commission(CommissionId("c1"), patron.id, LaunchContent.SWORD, 1, 140, out.state.day, out.state.day + 3, CommissionStatus.ACCEPTED)
            val s = out.state.copy(commissions = mapOf(c.id to c), nextCommissionSerial = 2)
            val gone = quiet.handle(s, Command.EndDay(endDayId(s))) as CommandOutcome.Accepted
            val r = gone.resolution!!
            if (r.browsers.isEmpty()) continue
            checked = true
            assertEquals(CommissionStatus.COMPLETED, gone.state.commissions.getValue(c.id).status)
            assertTrue(r.browsers.all { it.purchasedWeaponId == null }, "the shelf was bare: nobody browsing bought")
            val ledger = assertNotNull(r.ledger)
            assertEquals(mapOf(IncomeKind.COMMISSION to 140), ledger.income)
            balances(ledger)
            val tally = edition(gone.state, r).tally
            assertEquals(listOf("Shop took 140 gold", "0 of ${r.browsers.size} visitor${if (r.browsers.size == 1) "" else "s"} bought", "1 commission delivered"), tally.take(3))
            // The same day read from its records alone (the archive, an older build's report) says the same.
            assertEquals(tally.take(3), Gazette.edition(r.events, names(gone.state), r.visits).tally.take(3))
        }
        assertTrue(checked, "no seed sent a browser to the bare shelf")
    }

    private fun famousOnShelf(price: Int): Pair<GameState, WeaponId> {
        val out = engine.newRun(LegacyProfile(), 4).withMaterials().forgeAccepted(quickSword())
        val id = out.forgedWeaponId!!
        val s = out.state.run(Command.ToggleShelf(id, true, price))
        return s.copy(weapons = s.weapons + (id to s.weapon(id).copy(fame = 4))) to id
    }

    @Test
    fun collectorPurchaseIsItsOwnKind() {
        val (s, id) = famousOnShelf(engine.suggestedPrice(engine.newRun(LegacyProfile(), 4).withMaterials().forgeAccepted(quickSword()).let { it.state.weapon(it.forgedWeaponId!!) }))
        val ctx = ResolutionContext(s, content, config)
        val record = WorldEvents.fire(ctx, WorldEvents.byId("collector"))
        val paid = record.data.getValue("price").toInt()
        assertTrue(paid > 0)
        assertEquals(mapOf(IncomeKind.COLLECTOR to paid), ctx.income.toMap())
        assertEquals(s.gold + paid, ctx.gold)
        assertTrue(ctx.weapon(id).location is WeaponLocation.Lost)
        assertEquals(listOf("Shop took $paid gold"), Gazette.edition(listOf(record), emptyMap()).tally, "the paper counts the collector's coin from the record too")
    }

    @Test
    fun theCollectorNeverPaysAboveTheGoingRateTimesItsMultiplier() {
        fun paid(listed: Int): Pair<Int, Int> {
            val (s, id) = famousOnShelf(listed)
            val ctx = ResolutionContext(s, content, config)
            val asking = Market.askingPrice(ctx.weapon(id), config)
            WorldEvents.fire(ctx, WorldEvents.byId("collector"))
            assertEquals(ctx.gold - s.gold, ctx.income[IncomeKind.COLLECTOR])
            return ctx.gold - s.gold to asking
        }
        val (absurd, asking) = paid(999_999)
        assertTrue(asking > 0)
        assertEquals((asking * config.collectorPriceMultiplier).toInt(), absurd, "an absurd price mints no gold")
        assertEquals(absurd, paid(asking + 1).first, "one coin over the going rate is already capped")
        assertEquals(absurd, paid(asking).first, "at the going rate the cap changes nothing")
        assertEquals(((asking - 5) * config.collectorPriceMultiplier).toInt(), paid(asking - 5).first, "below the going rate the collector pays over the shelf price, as before")
        assertEquals(0, paid(0).first, "a free blade stays free")
    }

    @Test
    fun aFreeBladeIsASaleOfZeroCoinAndTheLedgerBalances() {
        var sales = 0
        for (seed in 1L..20L) {
            var s = fresh(seed)
            repeat(3) { s = s.forgeAccepted(quickSword()).state }
            for (w in s.storedWeapons()) s = s.run(Command.ToggleShelf(w.id, true, 0))
            val out = s.endDayAccepted()
            val r = out.resolution!!
            val ledger = assertNotNull(r.ledger)
            balances(ledger)
            val bought = r.browsers.count { it.purchasedWeaponId != null }
            sales += bought
            if (bought > 0) {
                assertEquals(0, ledger.income[IncomeKind.SHELF_SALE], "a free blade is a sale of zero coin, not a missing one")
                assertEquals(0, ledger.tradeInCredit, "no credit is owed against a price of nothing")
                assertEquals(s.gold, out.state.gold - ledger.income.filterKeys { it != IncomeKind.SHELF_SALE }.values.sum())
            }
        }
        assertTrue(sales > 0, "free blades found no taker")
        // The other absurd end: nobody can pay, nothing overflows, the ledger still balances.
        var s = fresh(3)
        repeat(3) { s = s.forgeAccepted(quickSword()).state }
        for (w in s.storedWeapons()) s = s.run(Command.ToggleShelf(w.id, true, 999_999))
        val r = s.endDayAccepted().resolution!!
        assertTrue(r.browsers.none { it.purchasedWeaponId != null })
        balances(r.ledger!!)
        assertNull(r.ledger!!.income[IncomeKind.SHELF_SALE])
    }

    @Test
    fun fatalExpeditionCountsAsALoss() {
        val fatal = config.copy(winProbabilityFloor = 0.0, winProbabilityCeiling = 0.0, expeditionDamageMin = 500, expeditionDamageMax = 500)
        val s = fresh()
        val victim = s.aliveHeroes().first()
        val ctx = ResolutionContext(s, content, fatal)
        val f = s.factions.values.sortedBy { it.id.value }.maxByOrNull { it.pressure }!!
        Battle.resolveExpedition(ctx, victim, null, f.id, content.faction(f.id))
        val result = ctx.field.single()
        assertEquals(FieldOutcome.DIED, result.outcome)
        assertEquals(victim.id, result.heroId)
        assertEquals(f.id, result.factionId)
        assertNotNull(result.foe)
        assertEquals(listOf(ctx.newEvents.single { it.type == EventType.HERO_DIED }.id), result.eventIds)
        assertTrue(ctx.newEvents.none { it.type == EventType.EXPEDITION_LOST }, "the records alone still show no lost expedition")
        val ledger = ShopLedger(s.gold, s.gold, emptyMap(), 0, 0)
        assertEquals(listOf("Expeditions: 0 won, 1 lost", "1 hero fell"), Gazette.edition(ctx.newEvents, names(s), ledger = ledger, field = ctx.field).tally)
    }

    @Test
    fun tributeIsNotShopTakings() {
        val weakRaids = GameEngine(config = config.copy(siegeModifier = config.siegeModifier / 10, worldEventChancePerDay = 0.0))
        var s = fresh().let { it.copy(factions = it.factions.mapValues { (id, f) -> f.copy(pressure = if (id == LaunchContent.ASHCLAW) 90 else 0) }) }
        s = s.copy(day = s.town.nextSiegeDay, town = s.town.copy(armory = config.armoryMax))
        val out = weakRaids.handle(s, Command.EndDay(endDayId(s))) as CommandOutcome.Accepted
        val r = out.resolution!!
        val ledger = assertNotNull(r.ledger)
        assertEquals(config.warlordTribute, ledger.income[IncomeKind.TRIBUTE])
        balances(ledger)
        val takings = ledger.income.filterKeys { it != IncomeKind.TRIBUTE }.values.sum()
        for (tally in listOf(edition(out.state, r).tally, Gazette.edition(r.events, names(out.state), r.visits).tally)) {
            assertTrue("Town tribute: ${config.warlordTribute} gold" in tally, "tribute has its own line: $tally")
            assertTrue(tally.none { it.startsWith("Shop took") && it != "Shop took $takings gold" }, "tribute is not in the takings: $tally")
        }
        val held = r.field.filter { it.outcome == FieldOutcome.HELD_THE_WALL }
        assertEquals(r.events.single { it.type == EventType.SIEGE_WON }.subjectIds, held.map { it.heroId.value })
    }

    @Test
    fun ledgerBalancesOnEveryDayOfTwoHundredRuns() {
        var days = 0
        val kinds = mutableSetOf<IncomeKind>()
        val outcomes = mutableSetOf<FieldOutcome>()
        for (seed in 1L..200L) {
            var s = engine.newRun(LegacyProfile(), seed)
            while (!s.isEnded && s.day <= 40) {
                val morning = s.gold
                s.commissions.values.filter { it.status == CommissionStatus.OFFERED }.forEach { s = s.tryRun(Command.AcceptCommission(it.id)) }
                s.pendingBlessingOffer.firstOrNull()?.let { s = s.tryRun(Command.ChooseBlessing(it)) }
                if (seed % 2 == 0L) content.tools.firstOrNull { engine.toolCost(s, it.id)?.let { cost -> cost <= s.gold - 60 } == true }?.let { s = s.tryRun(Command.BuyTool(it.id)) }
                repeat(3) {
                    if ((s.materials[LaunchContent.IRON] ?: 0) == 0) s = s.tryRun(Command.BuyMaterial(LaunchContent.IRON))
                    if ((s.materials[LaunchContent.EMBER_RESIN] ?: 0) == 0) s = s.tryRun(Command.BuyMaterial(LaunchContent.EMBER_RESIN))
                    s = s.tryRun(quickSword())
                }
                for (w in s.storedWeapons()) s = s.tryRun(Command.ToggleShelf(w.id, listed = true))
                val out = s.endDayAccepted()
                val r = out.resolution!!
                val at = "seed $seed day ${r.day}"
                val ledger = assertNotNull(r.ledger, at)
                assertEquals(s.gold, ledger.goldAtOpen, at)
                assertEquals(out.state.gold, ledger.goldAtClose, at)
                assertEquals(ledger.goldAtClose, ledger.goldAtOpen + ledger.income.values.sum(), "$at: $ledger")
                assertTrue(ledger.income.values.all { it >= 0 }, at)
                assertEquals(morning - s.gold, ledger.spentPreparing, "$at: preparation spending is on the record")
                val sold = r.events.filter { it.type == EventType.WEAPON_SOLD }
                assertEquals(sold.sumOf { it.data.getValue("price").toInt() - (it.data["tradeIn"]?.toInt() ?: 0) }, ledger.income[IncomeKind.SHELF_SALE] ?: 0, at)
                assertEquals(sold.sumOf { it.data["tradeIn"]?.toInt() ?: 0 }, ledger.tradeInCredit, at)
                assertTrue(r.browsers.count { it.purchasedWeaponId != null } <= r.browsers.size, at)
                assertEquals(sold.size, r.browsers.count { it.purchasedWeaponId != null }, "$at: every buyer is a browser and every browser who bought is a sale")
                // The paper's till is the same whether it is counted from the ledger or from the day's records.
                val typed = edition(out.state, r).tally
                val recorded = Gazette.edition(r.events, names(out.state), r.visits).tally
                assertEquals(recorded.filter { "gold" in it || "bought" in it || "delivered" in it }, typed.filter { "gold" in it || "bought" in it || "delivered" in it }, at)
                // Every hero who acted has a result; the dead are counted where they fell.
                assertEquals(r.events.count { it.type == EventType.ELITE_SLAIN || (it.type == EventType.EXPEDITION_WON && "material" !in it.data) }, r.field.count { it.outcome == FieldOutcome.WON }, at)
                assertEquals(r.events.count { it.type == EventType.EXPEDITION_LOST }, r.field.count { it.outcome == FieldOutcome.DRIVEN_BACK }, at)
                assertEquals(r.events.count { it.type == EventType.HERO_DIED }, r.field.count { it.outcome == FieldOutcome.DIED || it.outcome == FieldOutcome.FELL_AT_THE_WALL }, at)
                assertEquals(r.events.count { it.type == EventType.HERO_PATROLLED }, r.field.count { it.outcome == FieldOutcome.PATROLLED }, at)
                assertEquals(r.events.count { it.type == EventType.HERO_RESTED }, r.field.count { it.outcome == FieldOutcome.RESTED }, at)
                for (f in r.field) {
                    assertTrue(f.eventIds.isNotEmpty() && f.eventIds.all { id -> r.events.any { it.id == id } }, "$at: a result points at the day's records: $f")
                    if (f.lostBareHanded || f.lostWithOldBlade != null || f.matchupHelped) assertEquals(FieldOutcome.WON, f.outcome, at)
                }
                kinds += ledger.income.keys
                outcomes += r.field.map { it.outcome }
                days++
                s = out.state
            }
        }
        assertTrue(days > 2000, "days played: $days")
        assertTrue(kinds.containsAll(listOf(IncomeKind.SHELF_SALE, IncomeKind.COMMISSION)), "income kinds met: $kinds")
        assertTrue(outcomes.containsAll(FieldOutcome.entries - FieldOutcome.FELL_AT_THE_WALL), "field outcomes met: $outcomes")
    }
}
