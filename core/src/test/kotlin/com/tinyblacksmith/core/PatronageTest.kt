package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.shopday.Lines
import com.tinyblacksmith.core.shopday.ShopDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** G03: Guild Patronage sends the guild's members to the shop and pays a recorded stipend toward one purchase each. */
class PatronageTest {
    private val config = engine.config
    private val content = engine.content
    private val stipend = config.customers.patronageStipend
    private val guild = "g1"

    /** A day-1 town with a guild whose members are [members], and a shelf of [blades] plain swords at [price]. */
    private fun town(seed: Long = 7, blades: Int = 2, price: Int = 60, members: (Int, Hero) -> Boolean = { i, _ -> i < 4 }, edit: (Hero) -> Hero = { it }): GameState {
        val out = engine.newRun(LegacyProfile(), seed).withMaterials().forgeAccepted(quickSword(Risk.SAFE))
        val sword = out.state.weapon(out.forgedWeaponId!!).copy(affixes = emptyList(), flaws = emptyList(), element = null, power = 30)
        val heroes = out.state.aliveHeroes()
        return out.state.copy(
            heroes = heroes.withIndex().associate { (i, h) -> h.id to edit(h).let { if (members(i, h)) it.copy(guildId = guild) else it } },
            weapons = (1..blades).associate { i -> WeaponId("w$i") to sword.copy(id = WeaponId("w$i"), location = WeaponLocation.Shelf(price)) },
            town = out.state.town.copy(guilds = listOf(Guild(guild, "the Test Company", heroes.first().id, 1))), commissions = emptyMap(),
        )
    }

    private fun GameState.blessed(until: Int = day + 4) = copy(blessings = listOf(ActiveBlessing(LaunchContent.GUILD_PATRONAGE, until)))

    @Test
    fun guildMembersAreWillingAtTheCeiling() {
        val plain = town()
        val blessed = plain.blessed()
        val before = ResolutionContext(plain, content, config)
        val during = ResolutionContext(blessed, content, config)
        for (festival in listOf(false, true)) for (h in plain.aliveHeroes()) {
            val usual = Market.willingness(before, h, festival)
            assertTrue(usual < config.customers.visitCeiling, "${h.fullName} is not at the ceiling on an ordinary day")
            assertEquals(if (h.guildId != null) config.customers.visitCeiling else usual, Market.willingness(during, h, festival), 1e-12, h.fullName)
        }
        // The day after it lapses, a member is as willing as before.
        val lapsed = ResolutionContext(blessed.copy(day = blessed.day + 5), content, config)
        for (h in plain.aliveHeroes()) assertEquals(Market.willingness(before, h, false), Market.willingness(lapsed, h, false), 1e-12)
    }

    @Test
    fun oneStipendPerMemberPerBlessing() {
        val s = town(blades = 3, price = 60) { it.copy(gold = 500) }.blessed()
        val (member, outsider) = s.aliveHeroes().let { it.first { h -> h.guildId != null } to it.first { h -> h.guildId == null } }
        val ctx = ResolutionContext(s, content, config)
        assertEquals(stipend, Market.stipend(ctx, member))
        assertEquals(0, Market.stipend(ctx, outsider), "no guild, no stipend")
        assertEquals(0, Market.stipend(ResolutionContext(s.copy(blessings = emptyList()), content, config), member), "no blessing, no stipend")

        val first = Market.purchase(ctx, ctx.hero(member.id), ctx.weapon(WeaponId("w1")), 60)
        assertEquals(stipend to 60 - stipend, first.stipend to first.cashPaid)
        assertEquals(500 - (60 - stipend), ctx.hero(member.id).gold, "the member paid the rest")
        assertEquals(0, Market.stipend(ctx, ctx.hero(member.id)), "spent for this blessing")
        val second = Market.purchase(ctx, ctx.hero(member.id), ctx.weapon(WeaponId("w2")).copy(power = 60), 60)
        assertEquals(0, second.stipend, "one per member per blessing")
        assertEquals(0, Market.purchase(ctx, ctx.hero(outsider.id), ctx.weapon(WeaponId("w3")), 60).stipend)

        // The next blessing brings the next stipend; it never pays more than is owed after the trade-in.
        val later = ResolutionContext(ctx.toState().copy(day = s.day + 5).blessed(until = s.day + 9), content, config)
        assertEquals(stipend, Market.stipend(later, later.hero(member.id)))
        val cheap = later.weapon(WeaponId("w3")).copy(id = WeaponId("w9"), power = 90, location = WeaponLocation.Shelf(10))
        later.updateWeapon(cheap)
        val third = Market.purchase(later, later.hero(member.id), cheap, 10)
        assertEquals(0, third.cashPaid)
        assertEquals(10 - third.tradeInCredit, third.stipend)
        assertTrue(third.stipend in 0..stipend)
    }

    /** Twelve unarmed guild members who are ten gold short of every blade: only the guild's coin makes a sale. */
    @Test
    fun theStipendIsOnTheReceiptAndInTheLedger() {
        var sales = 0
        for (seed in 1L..6L) {
            val poor = town(seed, blades = 8, price = 60, members = { _, _ -> true }) { it.copy(gold = 50) }
            val unblessed = poor.endDayAccepted().resolution!!
            assertTrue(unblessed.browsers.none { it.purchasedWeaponId != null } && unblessed.ledger!!.income[IncomeKind.STIPEND] == null, "seed $seed: without the blessing nobody can pay")

            val pre = poor.blessed()
            val done = pre.endDayAccepted()
            val r = done.resolution!!
            val script = ShopDay.script(r, done.state, content, config)
            val paid = r.browsers.filter { it.purchasedWeaponId != null }
            sales += paid.size
            for (v in paid) {
                val sale = v.sale!!
                assertEquals(listOf(60, stipend, 60 - stipend), listOf(sale.listedPrice, sale.stipend, sale.cashPaid), "seed $seed ${v.heroName}")
                assertEquals(stipend.toString(), r.events.first { it.id in v.eventIds && it.type == EventType.WEAPON_SOLD }.data["stipend"])
                assertTrue("Their guild covered $stipend gold. They paid ${60 - stipend} gold themselves." in Lines.decision(v, script, content), Lines.decision(v, script, content))
                assertEquals(pre.day + 4, done.state.hero(v.heroId!!).stipendSpentFor)
            }
            val ledger = r.ledger!!
            assertEquals(paid.sumOf { it.sale!!.stipend }.takeIf { it > 0 }, ledger.income[IncomeKind.STIPEND], "seed $seed: the guilds' coin is its own line")
            assertEquals(paid.sumOf { it.sale!!.cashPaid }.takeIf { it > 0 }, ledger.income[IncomeKind.SHELF_SALE])
            assertEquals(ledger.goldAtClose, ledger.goldAtOpen + ledger.income.values.sum(), "seed $seed: the till balances")
            // Whoever left without a blade was not refused for the price: the stipend counted toward what they could afford.
            assertTrue(r.browsers.filter { it.purchasedWeaponId == null }.none { it.reason == VisitReason.TOO_EXPENSIVE && it.considered.any { k -> k.price == 60 } }, "seed $seed")
        }
        assertTrue(sales >= 6, "stipend sales over six towns: $sales")
    }

    @Test
    fun notOfferedWithoutAGuild() {
        val won = config.copy(siegeModifier = 0.0)  // no raid: the town holds and is offered a blessing
        fun offers(guilds: Boolean): List<List<BlessingId>> = (1L..200L).map { seed ->
            val s = engine.newRun(LegacyProfile(), seed)
            val ctx = ResolutionContext(s.copy(town = s.town.copy(nextSiegeDay = s.day, guilds = if (guilds) listOf(Guild(guild, "the Test Company", s.aliveHeroes().first().id, 1)) else emptyList())), content, won)
            Battle.resolveSiegeIfDue(ctx)
            ctx.pendingBlessingOffer
        }
        val without = offers(guilds = false)
        val with = offers(guilds = true)
        assertTrue(without.all { it.size == config.blessingOfferSize && it.toSet().size == it.size } && with.all { it.size == config.blessingOfferSize })
        assertTrue(without.none { LaunchContent.GUILD_PATRONAGE in it }, "never among the choices while no guild stands")
        assertTrue(with.count { LaunchContent.GUILD_PATRONAGE in it } in 40..110, "offered like any other once a guild stands: ${with.count { LaunchContent.GUILD_PATRONAGE in it }} of 200")
    }
}
