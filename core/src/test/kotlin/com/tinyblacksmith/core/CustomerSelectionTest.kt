package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDayId
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.config.CustomerConfig
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.RngStream
import com.tinyblacksmith.core.rng.SplitMix64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F05, plan 4.2: every living hero decides for themselves whether to come, the seats are drawn among the willing, and
 * nobody willing waits past the bound. The ID order of the heroes assigns draws and nothing else.
 */
class CustomerSelectionTest {
    private val content = engine.content
    private val default = BalanceConfig.DEFAULT

    private fun config(edit: CustomerConfig.() -> CustomerConfig) = default.copy(customers = default.customers.edit())

    /** Everyone wants to come every day as far as the ceiling allows (0.9). */
    private fun CustomerConfig.keen() = copy(baseVisitChance = 2.0)

    /**
     * A town of [n] heroes who differ only in their ID (one starting hero copied; no traits, no loyalty, no gold, unarmed)
     * unless [edit] says otherwise, and a shelf of [blades] swords nobody can pay for: every visit ends in a refusal, so
     * the days differ only in who was seated.
     */
    private fun town(n: Int, seed: Long = 7, blades: Int = 1, edit: (Int, Hero) -> Hero = { _, h -> h }): GameState {
        val out = engine.newRun(LegacyProfile(), seed).withMaterials().forgeAccepted(quickSword(Risk.SAFE))
        val sword = out.state.weapon(out.forgedWeaponId!!)
        val template = out.state.aliveHeroes().first().copy(traits = emptyList(), loyalty = 0, gold = 0)
        return out.state.copy(
            heroes = (1..n).associate { i -> HeroId("h$i") to edit(i, template.copy(id = HeroId("h$i"))) },
            weapons = (1..blades).associate { i -> WeaponId("w$i") to sword.copy(id = WeaponId("w$i"), location = WeaponLocation.Shelf(1_000_000)) },
            reputation = 0, commissions = emptyMap(),
        )
    }

    private class Day(val next: GameState, val seated: List<HeroId>, val turnedAway: List<HeroId>) {
        val willing: Set<HeroId> get() = (seated + turnedAway).toSet()
    }

    /** The browsers of one day and the next morning's state; nothing else of End Day runs, so only the counter moves. */
    private fun GameState.shopDay(config: BalanceConfig): Day {
        val ctx = ResolutionContext(this, content, config)
        Market.resolveShelfVisits(ctx)
        return Day(ctx.toState().copy(day = day + 1), ctx.visits.map { it.heroId!! }, ctx.turnedAway.toList())
    }

    /** Seats per hero over [days] days (only the days the shop was full when [cappedOnly]). */
    private fun seats(start: GameState, config: BalanceConfig, days: Int, cappedOnly: Boolean = false): Map<HeroId, Int> {
        val counts = start.heroes.keys.associateWith { 0 }.toMutableMap()
        var s = start
        var counted = 0
        while (counted < days) {
            val d = s.shopDay(config)
            if (!cappedOnly || d.turnedAway.isNotEmpty()) {
                counted++
                for (id in d.seated) counts[id] = counts.getValue(id) + 1
            }
            s = d.next
        }
        return counts
    }

    private fun draws(before: GameState, after: GameState, stream: RngStream): Long {
        var k = 0L
        var state = before.rng.stateOf(stream)
        while (state != after.rng.stateOf(stream)) { state += SplitMix64.GOLDEN; k++; check(k < 100_000) }
        return k
    }

    @Test
    fun drawsAreOnePerLivingHeroOnePerSeatAndOnePerBladePerVisitor() {
        val config = config { copy(shopCapacity = 4) }
        var s = town(12, blades = 2).let { it.copy(heroes = it.heroes + (HeroId("h5") to it.hero(HeroId("h5")).copy(fate = HeroFate.DEAD))) }
        repeat(60) { n ->
            val d = s.shopDay(config)
            assertTrue(d.seated.size <= 4 && HeroId("h5") !in d.willing, "day $n")
            assertEquals(11L + d.seated.size + d.seated.size * 2, draws(s, d.next, RngStream.PURCHASES), "day $n: 11 living, ${d.seated.size} seated, two blades each")
            for (stream in RngStream.entries) if (stream != RngStream.PURCHASES) assertEquals(0L, draws(s, d.next, stream), "day $n: $stream")
            s = d.next
        }
        // An empty shelf: the intent and seat draws are still made, nothing is weighed.
        val bare = town(12).copy(weapons = emptyMap())
        val d = bare.shopDay(config)
        assertEquals(12L + d.seated.size, draws(bare, d.next, RngStream.PURCHASES))
    }

    @Test
    fun intentDoesNotDependOnTheSeats() {
        for (seed in 1L..40L) {
            val s = town(12, seed)
            val few = s.shopDay(config { copy(shopCapacity = 2) })
            val many = s.shopDay(config { copy(shopCapacity = 9) })
            assertEquals(few.willing, many.willing, "seed $seed: the same heroes came")
            assertEquals(minOf(2, few.willing.size), few.seated.size, "seed $seed")
            assertEquals(minOf(9, many.willing.size), many.seated.size, "seed $seed")
        }
    }

    /** Twelve heroes who differ only in ID, four seats, 10,000 days on which somebody was turned away. */
    @Test
    fun heroesDifferingOnlyInIdGetEqualTurns() {
        val counts = seats(town(12), default, 10_000, cappedOnly = true)
        val mean = counts.values.sum() / 12.0
        val chiSquare = counts.values.sumOf { (it - mean) * (it - mean) / mean }
        assertTrue(chiSquare < 24.72, "chi-square over 11 degrees of freedom, p > 0.01 below 24.72: $chiSquare, $counts")

        // Permuting serials: four regulars among twelve get the same share whether they hold the lowest IDs or the highest.
        fun regularShare(regulars: IntRange): Double {
            val c = seats(town(12) { i, h -> if (i in regulars) h.copy(loyalty = 10) else h }, default, 10_000)
            return regulars.sumOf { c.getValue(HeroId("h$it")) }.toDouble() / c.values.sum()
        }
        val low = regularShare(1..4)
        val high = regularShare(9..12)
        assertTrue(kotlin.math.abs(low - high) < 0.015, "the regulars' share of the seats: $low as h1-h4, $high as h9-h12")
    }

    @Test
    fun loyaltyRaisesAShareWithinItsBound() {
        val c = seats(town(12) { i, h -> if (i % 2 == 0) h.copy(loyalty = 10) else h }, default, 10_000)
        val regulars = (2..12 step 2).sumOf { c.getValue(HeroId("h$it")) }
        val strangers = (1..11 step 2).sumOf { c.getValue(HeroId("h$it")) }
        val ratio = regulars.toDouble() / strangers
        assertTrue(ratio in 1.2..1.6, "a regular (loyalty 10) is served more often than a stranger, within bounds: $ratio")
    }

    /**
     * One newcomer among eleven veterans, four seats. Counted in days the newcomer chose to come: a hero who stays home
     * (the base chance of coming is 0.35 a day) is not waiting. By the calendar, 145 of these 200 are served within two
     * days of arriving.
     */
    @Test
    fun aNewcomerIsSeatedByTheirSecondWillingDayInFourSeedsOfFive() {
        val newcomer = HeroId("h12")
        var seatedInTime = 0
        val seeds = 200
        for (seed in 1L..seeds) {
            var s = town(12, seed) { i, h -> if (i == 12) h else h.copy(shopVisits = 5) }
            var willingDays = 0
            while (willingDays < 2) {
                val d = s.shopDay(default)
                if (newcomer in d.willing) willingDays++
                if (newcomer in d.seated) { seatedInTime++; break }
                s = d.next
            }
        }
        assertTrue(seatedInTime >= seeds * 4 / 5, "seated by the second day they came: $seatedInTime of $seeds")
    }

    /** Sixteen keen heroes for four seats: the worst queue this town can form. */
    @Test
    fun noStreakExceedsTheWaitingBound() {
        val config = config { keen().copy(shopCapacity = 4) }
        val limit = config.customers.maxTurnedAwayDays
        var s = town(16)
        var worst = 0
        repeat(5_000) { n ->
            val d = s.shopDay(config)
            val waiting = d.willing.filter { s.hero(it).turnedAwayStreak >= limit }
            val (in_, out) = waiting.partition { it in d.seated }
            if (waiting.size <= 4) assertTrue(out.isEmpty(), "day $n: everyone at the limit is seated when they fit")
            else assertEquals(4, in_.size, "day $n: the seats all go to those at the limit")
            if (out.isNotEmpty()) assertTrue(out.maxOf { s.hero(it).turnedAwayStreak } <= in_.minOf { s.hero(it).turnedAwayStreak }, "day $n: the longest waiters first")
            assertEquals(in_, d.seated.take(in_.size), "day $n: they are served before anyone else")
            val bound = limit + (waiting.size + 3) / 4
            for (h in d.next.heroes.values) assertTrue(h.turnedAwayStreak <= bound, "day $n: ${h.id.value} has waited ${h.turnedAwayStreak} days, bound $bound")
            worst = maxOf(worst, d.next.heroes.values.maxOf { it.turnedAwayStreak })
            s = d.next
        }
        assertTrue(worst > limit, "the scenario does queue: $worst")
    }

    /** Sixteen heroes, six seats: at reputation 50 and with everyone at the ceiling, equal heroes get equal turns and a regular at most 2.5 times a stranger's. */
    @Test
    fun reputationFiftyAccountKeepsEqualTurns() {
        fun spread(c: Map<HeroId, Int>) = c.values.max().toDouble() / c.values.min()
        val six = config { copy(shopCapacity = 6) }
        val saturated = config { keen().copy(shopCapacity = 6) }
        val reputed = spread(seats(town(16).copy(reputation = 50), six, 5_000))
        val keen = spread(seats(town(16), saturated, 5_000))
        val mixed = spread(seats(town(16) { i, h -> if (i % 2 == 0) h.copy(loyalty = 10) else h }, saturated, 5_000))
        assertTrue(reputed < 1.15, "equal heroes at reputation 50: $reputed")
        assertTrue(keen < 1.15, "equal heroes, everyone willing: $keen")
        assertTrue(mixed <= 2.5, "regulars against strangers, everyone willing: $mixed")
    }

    @Test
    fun theFirstSeatsGoToDistinctClasses() {
        val classes = content.classes.map { it.id }
        assertTrue(classes.size >= 4)
        // No waiting priority here, so every seat is an ordinary one.
        val config = config { keen().copy(shopCapacity = 4, maxTurnedAwayDays = Int.MAX_VALUE) }
        var s = town(12) { i, h -> h.copy(classId = classes[i % 4]) }
        var sameClassFourth = 0
        repeat(500) { n ->
            val d = s.shopDay(config)
            val willingClasses = d.willing.map { s.hero(it).classId }.toSet()
            val firstThree = d.seated.take(3).map { s.hero(it).classId }
            assertEquals(minOf(3, willingClasses.size, firstThree.size), firstThree.toSet().size, "day $n: $firstThree of $willingClasses")
            if (d.seated.size == 4 && s.hero(d.seated[3]).classId in firstThree) sameClassFourth++
            s = d.next
        }
        assertTrue(sameClassFourth > 50, "after ${config.customers.classSeats} classes the seats are open to all: $sameClassFourth")
    }

    @Test
    fun twoEmptyShelfDaysDoNotCreateAnIdOrderedQueue() {
        val config = config { keen().copy(shopCapacity = 4) }
        val firstSeats = HashMap<HeroId, Int>()
        for (seed in 1L..300L) {
            val stocked = town(12, seed)
            var s = stocked.copy(weapons = emptyMap())
            repeat(2) {
                val ctx = ResolutionContext(s, content, config)
                Market.resolveShelfVisits(ctx)
                assertTrue(ctx.visits.size == 4 && ctx.visits.all { it.reason == VisitReason.EMPTY_SHELVES } && ctx.turnedAway.isNotEmpty(), "seed $seed: four visits to a bare shelf, the rest turned away")
                s = ctx.toState().copy(day = s.day + 1)
                assertTrue(s.heroes.values.all { it.shopVisits == 0 && it.turnedAwayStreak == 0 && it.lastServedDay == null }, "seed $seed: nobody's standing changed")
            }
            for (id in s.copy(weapons = stocked.weapons).shopDay(config).seated) firstSeats.merge(id, 1, Int::plus)
        }
        // 1,200 seats over twelve heroes who are all still newcomers: about 100 each, whatever their ID.
        assertTrue(firstSeats.size == 12 && firstSeats.values.all { it in 65..135 }, "seats on the first stocked day by hero: ${firstSeats.toSortedMap(compareBy(IdOrder.numeric) { it.value })}")

        // A shelf that sells out during the day: those seated after the last blade went keep their place in the queue.
        val one = town(12).let { t -> t.copy(heroes = t.heroes.mapValues { it.value.copy(gold = 5_000, turnedAwayStreak = 1) }, weapons = t.weapons.mapValues { it.value.copy(location = WeaponLocation.Shelf(1)) }) }
        val ctx = ResolutionContext(one, content, config)
        Market.resolveShelfVisits(ctx)
        val after = ctx.toState()
        assertEquals(listOf(VisitReason.EMPTY_SHELVES, VisitReason.EMPTY_SHELVES, VisitReason.EMPTY_SHELVES), ctx.visits.drop(1).map { it.reason })
        assertTrue(ctx.visits.first().purchasedWeaponId != null)
        assertEquals(listOf(1, 1, 0, one.day), after.hero(ctx.visits.first().heroId!!).let { listOf(it.shopVisits, it.shopPurchases, it.turnedAwayStreak, it.lastPurchaseDay) })
        for (v in ctx.visits.drop(1)) assertEquals(listOf(0, 1), after.hero(v.heroId!!).let { listOf(it.shopVisits, it.turnedAwayStreak) }, "seated at the emptied shelf")
        for (id in ctx.turnedAway) assertEquals(2, after.hero(id).turnedAwayStreak, "found the shop full")
    }

    /** Through End Day: the day names who found the shop full, their streak grows, and being served ends a streak. */
    @Test
    fun turnedAwayIsStoredOnTheDayAndCounted() {
        val keen = GameEngine(config = config { keen().copy(shopCapacity = 4) })
        var seen = 0
        for (seed in 1L..10L) {
            val s = town(8, seed) { i, h -> if (i == 1) h.copy(turnedAwayStreak = 5) else h }
            val out = keen.handle(s, Command.EndDay(endDayId(s))) as CommandOutcome.Accepted
            val r = out.resolution!!
            val served = r.browsers.map { it.heroId!! }
            assertEquals(4, served.size, "seed $seed")
            assertTrue(r.turnedAway.none { it in served } && r.turnedAway == r.turnedAway.sortedWith(compareBy(IdOrder.numeric) { it.value }), "seed $seed")
            for (id in served) assertEquals(listOf(0, 1, s.day), out.state.hero(id).let { listOf(it.turnedAwayStreak, it.shopVisits, it.lastServedDay) }, "seed $seed: served")
            for (id in r.turnedAway) assertEquals(s.hero(id).turnedAwayStreak + 1, out.state.hero(id).turnedAwayStreak, "seed $seed: turned away")
            for (h in s.heroes.values) if (h.id !in served && h.id !in r.turnedAway) assertEquals(h.turnedAwayStreak, out.state.hero(h.id).turnedAwayStreak, "seed $seed: stayed home")
            if (HeroId("h1") in served + r.turnedAway) assertEquals(HeroId("h1"), served.first(), "seed $seed: the longest waiter is served first")
            seen += r.turnedAway.size
        }
        assertTrue(seen > 10, "eight keen heroes for four seats: $seen turned away over ten days")
    }

    @Test
    fun oneIdOrderEverywhere() {
        assertEquals(listOf("c2", "c10", "elf", "h1", "h2", "h10", "w3"), listOf("h10", "w3", "c10", "h2", "elf", "c2", "h1").sortedWith(IdOrder.numeric))
        val s = town(12)
        val order = (1..12).map { "h$it" }
        assertEquals(order, s.aliveHeroes().map { it.id.value })
        val ctx = ResolutionContext(s, content, default)
        assertEquals(order, ctx.aliveHeroes().map { it.id.value })
        // Twelve equal heroes: the champions are the three lowest serials, not h1, h10, h11.
        assertEquals(listOf("h1", "h2", "h3"), Battle.selectChampions(ctx, content.factions.first()).map { it.first.id.value })

        // Two equal blades, two equal requests: the lower serial of each, by number.
        val blade = s.weapons.values.first().copy(location = WeaponLocation.Storage, quality = 60)
        val stock = listOf(blade.copy(id = WeaponId("w10")), blade.copy(id = WeaponId("w2")))
        fun request(id: String, buyer: String) = Commission(CommissionId(id), HeroId(buyer), blade.familyId, 50, 100, s.day, s.day + 3, CommissionStatus.ACCEPTED)
        assertEquals("w2", Commissions.pick(stock, request("c2", "h1"), default)!!.id.value)
        val asked = s.copy(weapons = mapOf(blade.id to blade), commissions = listOf(request("c10", "h1"), request("c2", "h2")).associateBy { it.id })
        val day = ResolutionContext(asked, content, default)
        Market.resolveCommissions(day)
        assertEquals(listOf(CommissionStatus.COMPLETED, CommissionStatus.ACCEPTED), listOf(day.commissions.getValue(CommissionId("c2")).status, day.commissions.getValue(CommissionId("c10")).status))
    }
}
