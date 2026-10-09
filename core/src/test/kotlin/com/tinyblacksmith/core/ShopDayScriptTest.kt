package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.admitted
import com.tinyblacksmith.core.TestSupport.endDayId
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.shopday.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** S02, S03, S07, U07: the script of a day is its records, rearranged; it adds nothing and decides nothing. */
class ShopDayScriptTest {
    private val config = engine.config
    private val content = engine.content
    private val base: GameState = engine.newRun(LegacyProfile(), 7)

    private fun script(r: DayResolution, after: GameState = base) = ShopDay.script(r, after, content, config)
    private fun fixture(name: String): GameState = SaveCodec.decodeRun(ShopDayScriptTest::class.java.getResource("/saves/$name")!!.readText())

    // ---- real days ----

    private fun GameState.tryRun(e: GameEngine, command: Command): GameState = (e.handle(this, command) as? CommandOutcome.Accepted)?.state ?: this

    /** The golden script's morning: restock, forge up to three quick swords, list every stored blade at the going rate. */
    private fun GameState.morning(e: GameEngine = engine): GameState {
        var s = this
        repeat(3) {
            if ((s.materials[LaunchContent.IRON] ?: 0) == 0) s = s.tryRun(e, Command.BuyMaterial(LaunchContent.IRON))
            if ((s.materials[LaunchContent.EMBER_RESIN] ?: 0) == 0) s = s.tryRun(e, Command.BuyMaterial(LaunchContent.EMBER_RESIN))
            s = s.tryRun(e, quickSword())
        }
        for (w in s.storedWeapons()) s = s.tryRun(e, Command.ToggleShelf(w.id, listed = true))
        return s
    }

    /** Every resolved day of [seeds] x [days], with the state End Day returned. */
    private fun realDays(seeds: LongRange, days: Int, e: GameEngine = engine, prepare: GameState.() -> GameState = { morning(e) }): List<Pair<DayResolution, GameState>> = buildList {
        for (seed in seeds) {
            var s = e.newRun(LegacyProfile(), seed)
            repeat(days) {
                if (s.isEnded) return@repeat
                val out = e.handle(s.prepare(), Command.EndDay(endDayId(s))) as CommandOutcome.Accepted
                add(out.resolution!! to out.state)
                s = out.state
            }
        }
    }

    private val busy = GameEngine(config = config.copy(customers = config.customers.copy(shopCapacity = 10, baseVisitChance = 0.9, startingHeroes = 12)))

    // ---- hand-built days ----

    private fun blade(id: String, name: String = "Blade $id") =
        WeaponSnapshot(WeaponId(id), name, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, rarity = Rarity.COMMON, quality = 40, power = 20, condition = 100)

    private fun customer(hero: String, regular: Boolean = false, equipped: WeaponSnapshot? = null) =
        CustomerSnapshot(HeroId(hero), "Hero $hero", LaunchContent.GUARDIAN, 2, "portrait", gold = 50, regular = regular, equipped = equipped)

    private fun left(seq: Int, hero: String, reason: VisitReason, considered: List<Considered> = emptyList()) =
        MarketVisit(HeroId(hero), "Hero $hero", null, reason, seq = seq, customer = customer(hero), considered = considered)

    private fun bought(seq: Int, hero: String, weapon: String, cash: Int = 40, regular: Boolean = false, old: WeaponSnapshot? = null, events: List<String> = emptyList()) = MarketVisit(
        HeroId(hero), "Hero $hero", WeaponId(weapon), VisitReason.GOOD_ENOUGH, seq = seq, customer = customer(hero, regular, old),
        considered = listOf(Considered(WeaponId(weapon), cash, listOf(VisitFactor.SUITS_CLASS, VisitFactor.CAN_AFFORD))),
        sale = Sale(listedPrice = cash + (if (old != null) 5 else 0), tradeInCredit = if (old != null) 5 else 0, tradeInWeaponId = old?.weaponId, cashPaid = cash), eventIds = events,
    )

    private fun patron(seq: Int, hero: String, weapon: String) = MarketVisit(
        HeroId(hero), "Hero $hero", WeaponId(weapon), VisitReason.COMMISSION_DELIVERED, seq = seq, kind = VisitKind.COMMISSION, customer = customer(hero),
        considered = listOf(Considered(WeaponId(weapon), 0)), sale = Sale(cashPaid = 140, commissionId = CommissionId("c1")),
    )

    private fun collector(seq: Int, weapon: String) = MarketVisit(
        null, "A collector", WeaponId(weapon), VisitReason.COLLECTOR_PURCHASE, seq = seq, kind = VisitKind.COLLECTOR,
        considered = listOf(Considered(WeaponId(weapon), 60, listOf(VisitFactor.COLLECTOR_PRIZE))), sale = Sale(listedPrice = 60, cashPaid = 90),
    )

    private fun event(id: String, type: EventType, text: String, subjects: List<String> = emptyList(), data: Map<String, String> = emptyMap(), priority: Int = 5) =
        EventRecord(id, 1, 5, type, priority, text, subjects, data)

    private fun day(
        visits: List<MarketVisit> = emptyList(), events: List<EventRecord> = emptyList(), field: List<FieldResult> = emptyList(),
        shelf: List<WeaponSnapshot> = visits.flatMap { v -> v.considered.map { blade(it.weaponId.value) } }.distinct(), replays: List<CombatReplay> = emptyList(), recordVersion: Int = 1,
    ) = DayResolution(
        CommandId("test:day5"), 5, events, emptyList(), visits, replays, defeated = false, field = field,
        shopWeapons = shelf, shelfPrices = shelf.associate { it.weaponId to 60 }, recordVersion = recordVersion,
    )

    private fun ShopDayScript.seqs() = featured.map { it.seq }
    private fun ShopDayScript.tallied() = tally.associate { (it.outcome to it.reason) to it.visits.map { v -> v.seq } }

    /** Every sentence the script of a day can put on screen. */
    private fun allLines(s: ShopDayScript, after: GameState): List<String> = buildList {
        for (v in s.visits) {
            add(Lines.customer(v, content)); add(Lines.decision(v, s, content)); add(Lines.reason(v.reason))
            v.considered.forEach { add(Lines.considered(it, s)) }
        }
        s.tally.forEach { add(Lines.tally(it, s)) }
        s.quiet?.let { add(Lines.quiet(it)) }
        s.aftermath.forEach { add(Lines.aftermath(it, content)) }
        s.lead?.let { l -> Lines.lead(l, after, content, config).let { add(it.action); it.reason?.let { r -> add(r) } } }
    }

    // ---- featured visits and the tally ----

    @Test
    fun featuredPlusTallyIsEveryVisitExactlyOnce() {
        var featured = 0; var tallied = 0
        for ((r, after) in realDays(1L..12L, 15) + realDays(1L..6L, 12, busy)) {
            val s = script(r, after)
            assertEquals(r.visits.map { it.seq }, s.visits.map { it.seq }, "day ${r.day}: every visit once")
            assertEquals(r.visits, s.visits, "day ${r.day}: and unchanged")
            assertTrue(s.featured.size <= ShopDay.FEATURED_MAX)
            assertEquals(s.featured.sortedBy { it.seq }, s.featured, "featured in visit order")
            assertEquals(s.featured.mapNotNull { it.heroId }.distinct(), s.featured.mapNotNull { it.heroId }, "nobody is featured twice")
            assertTrue(s.featured.none { it.reason == VisitReason.EMPTY_SHELVES || it.reason == VisitReason.UNDECIDED })
            assertEquals(s.tally.map { it.outcome to it.reason }.distinct(), s.tally.map { it.outcome to it.reason }, "one group per outcome and reason")
            assertEquals(r.shopWeapons to r.shelfPrices, s.shelf to s.prices)
            assertEquals(r.ledger, s.ledger)
            featured += s.featured.size; tallied += s.tally.sumOf { it.visits.size }
        }
        assertTrue(featured > 100 && tallied > 100, "featured=$featured tallied=$tallied")
    }

    @Test
    fun aCommissionAndACollectorNeverSqueezeOutThePurchaseAndTheRefusal() {
        val visits = listOf(
            patron(0, "h1", "a"), left(1, "h2", VisitReason.NOT_BETTER), bought(2, "h3", "b"), left(3, "h4", VisitReason.TOO_EXPENSIVE), collector(4, "c"),
        )
        val s = script(day(visits))
        assertEquals(listOf(0, 2, 3), s.seqs(), "the patron, the purchase and the first refusal by rank; the collector waits")
        assertEquals(mapOf((TallyOutcome.COLLECTOR to null) to listOf(4), (TallyOutcome.LEFT to VisitReason.NOT_BETTER) to listOf(1)), s.tallied())

        // Two commissions and a collector (possible from T4.6) beside a purchase and a refusal: one commission only.
        val crowded = script(day(listOf(patron(0, "h1", "a"), patron(1, "h5", "d")) + visits.drop(1).mapIndexed { i, v -> v.copy(seq = i + 2) }))
        assertEquals(listOf(0, 3, 4), crowded.seqs())
        assertEquals(listOf(1), crowded.tallied()[TallyOutcome.COMMISSION to null])
        assertEquals(listOf(5), crowded.tallied()[TallyOutcome.COLLECTOR to null])

        // With nothing to squeeze out they are always featured.
        assertEquals(listOf(0, 1, 2), script(day(listOf(patron(0, "h1", "a"), patron(1, "h5", "d"), collector(2, "c"), left(3, "h6", VisitReason.UNDECIDED)))).seqs())
        assertEquals(listOf(0, 1, 2), script(day(listOf(patron(0, "h1", "a"), bought(1, "h3", "b"), collector(2, "c")))).seqs())
    }

    @Test
    fun purchasesAreRankedByWhatTheRecordSaysOfThem() {
        val firstSale = event("e1", EventType.MILESTONE, "The shop made its first sale.", data = mapOf("milestone" to "FIRST_SALE"))
        val premium = event("e2", EventType.WEAPON_SOLD, "Sold above the going rate.", data = mapOf("price" to "50", "premium" to "8"))
        val visits = listOf(
            bought(0, "h1", "a", cash = 90), bought(1, "h2", "b", cash = 20, events = listOf("e2")), bought(2, "h3", "c", cash = 30, old = blade("old")),
            bought(3, "h4", "d", cash = 10, regular = true), bought(4, "h5", "e", cash = 5, events = listOf("e1")),
        )
        assertEquals(listOf(2, 3, 4), script(day(visits, listOf(firstSale, premium))).seqs(), "the first sale of the run, then the regular, then the trade-in")
        assertEquals(listOf(0, 1, 2), script(day(visits.take(3), listOf(premium))).seqs())
        assertEquals(listOf(0, 1), script(day(visits.take(2) + left(2, "h9", VisitReason.UNDECIDED), listOf(premium))).seqs())
        // Coin paid, then visit order.
        val plain = listOf(bought(0, "h1", "a", cash = 10), bought(1, "h2", "b", cash = 30), bought(2, "h3", "c", cash = 30), bought(3, "h4", "d", cash = 30), left(4, "h5", VisitReason.NOT_BETTER))
        assertEquals(listOf(1, 2, 4), script(day(plain)).seqs(), "two purchases by coin then order, and the refusal keeps its slot")
    }

    @Test
    fun oneRefusalPerDistinctReason() {
        val visits = listOf(
            left(0, "h1", VisitReason.UNDECIDED), left(1, "h2", VisitReason.TOO_EXPENSIVE), left(2, "h3", VisitReason.NOT_BETTER), left(3, "h4", VisitReason.TOO_EXPENSIVE),
            left(4, "h5", VisitReason.OVERPRICED), left(5, "h6", VisitReason.NOT_BETTER), left(6, "h7", VisitReason.EMPTY_SHELVES),
        )
        val s = script(day(visits))
        assertEquals(listOf(1, 2, 4), s.seqs(), "the first of each reason, shown in visit order")
        assertEquals(s.featured.map { it.reason }.distinct(), s.featured.map { it.reason })
        assertEquals(listOf(3), s.tallied()[TallyOutcome.LEFT to VisitReason.TOO_EXPENSIVE])
        assertEquals(listOf(0), s.tallied()[TallyOutcome.LEFT to VisitReason.UNDECIDED])
        // Four reasons, three slots: the order is OVERPRICED, TOO_EXPENSIVE, NOT_SUITED, NOT_BETTER.
        val four = script(day(listOf(left(0, "h1", VisitReason.NOT_BETTER), left(1, "h2", VisitReason.NOT_SUITED), left(2, "h3", VisitReason.TOO_EXPENSIVE), left(3, "h4", VisitReason.OVERPRICED))))
        assertEquals(listOf(1, 2, 3), four.seqs())
        // A second refusal of a reason is never featured, even with slots to spare; the undecided and the bare shelf never are.
        assertEquals(listOf(0), script(day(listOf(left(0, "h1", VisitReason.NOT_BETTER), left(1, "h2", VisitReason.NOT_BETTER), left(2, "h3", VisitReason.UNDECIDED)))).seqs())
        assertNull(script(day(listOf(left(0, "h1", VisitReason.UNDECIDED), left(1, "h2", VisitReason.EMPTY_SHELVES)))).quiet, "somebody weighed a blade: not a quiet day")
    }

    @Test
    fun tenVisitorsFeatureThree() {
        var tenVisitorDays = 0; var full = 0
        for ((r, after) in realDays(1L..10L, 15, busy)) {
            if (r.browsers.size != 10) continue
            tenVisitorDays++
            val s = script(r, after)
            val purchases = r.browsers.count { it.purchasedWeaponId != null }
            val reasons = r.browsers.filter { it.purchasedWeaponId == null && it.reason != VisitReason.UNDECIDED && it.reason != VisitReason.EMPTY_SHELVES }.map { it.reason }.distinct().size
            val specials = r.visits.size - r.browsers.size
            assertEquals(minOf(ShopDay.FEATURED_MAX, purchases + reasons + specials), s.featured.size, "day ${r.day}: three when the day has three to show")
            if (purchases > 0) assertTrue(s.featured.any { it.kind == VisitKind.BROWSE && it.purchasedWeaponId != null }, "a purchase is among them")
            if (reasons > 0) assertTrue(s.featured.any { it.purchasedWeaponId == null }, "and a refusal")
            assertEquals(r.visits.size - s.featured.size, s.tally.sumOf { it.visits.size }, "the tally covers the rest")
            if (s.featured.size == ShopDay.FEATURED_MAX) full++
        }
        assertTrue(tenVisitorDays >= 50 && full >= 40, "ten-visitor days: $tenVisitorDays, with three featured: $full")
    }

    /** Known tolerance until seating is fair (T3.1): a patron may also browse the same day. */
    @Test
    fun aPatronWhoAlsoBrowsesIsFeaturedOnce() {
        val twice = listOf(patron(0, "h1", "a"), bought(1, "h1", "b", cash = 99, regular = true), bought(2, "h2", "c"), left(3, "h1", VisitReason.NOT_BETTER), left(4, "h3", VisitReason.NOT_BETTER))
        val s = script(day(twice))
        assertEquals(listOf(0, 2, 4), s.seqs(), "the patron once; the purchase and the refusal of others take the kept slots")
        assertEquals(listOf(0, 1, 2, 3, 4), s.visits.map { it.seq }, "the second and third appearances are in the tally, not lost")
        assertEquals(listOf(1), s.tallied()[TallyOutcome.BOUGHT to null])
        allLines(s, base)
        // Nobody else to show: the day is still told, with the patron featured once.
        val alone = script(day(twice.take(2)))
        assertEquals(listOf(0), alone.seqs())
        assertEquals(listOf(1), alone.tallied()[TallyOutcome.BOUGHT to null])
    }

    // ---- quiet days and old days ----

    @Test
    fun aDayWithNoLivingHeroesIsOneQuietCard() {
        val start = engine.newRun(LegacyProfile(), 3).morning()
        val empty = start.copy(heroes = start.heroes.mapValues { (_, h) -> h.copy(fate = HeroFate.DEAD, health = 0, diedOnDay = 1) }, town = start.town.copy(championIds = emptyList()))
        val out = engine.handle(empty, Command.EndDay(endDayId(empty))) as CommandOutcome.Accepted
        val s = script(out.resolution!!, out.state)
        assertEquals(QuietDay(QuietKind.NO_VISITORS), s.quiet)
        assertTrue(s.featured.isEmpty() && s.tally.isEmpty() && s.aftermath.isEmpty())
        assertEquals(Ending.TOMORROW, s.ending)
        assertEquals("Nobody came to the shop today.", Lines.quiet(s.quiet!!))
        assertEquals(start.listedWeapons().map { it.id }, s.shelf.map { it.weaponId }, "the stock is still shown")
    }

    @Test
    fun anEmptyShelfNamesWhoLookedIn() {
        var days = 0
        for ((r, after) in realDays(1L..8L, 4, prepare = { this })) {  // nothing forged, nothing listed
            val s = script(r, after)
            if (r.visits.isEmpty()) { assertEquals(QuietKind.NO_VISITORS, s.quiet?.kind); continue }
            days++
            val quiet = assertNotNull(s.quiet)
            assertEquals(QuietKind.EMPTY_SHELF, quiet.kind)
            assertEquals(r.visits, quiet.visitors, "everyone who looked in, once, in order")
            assertTrue(s.featured.isEmpty(), "nobody is featured at a bare shelf")
            assertEquals(listOf(TallyGroup(TallyOutcome.LEFT, VisitReason.EMPTY_SHELVES, r.visits)), s.tally)
            val line = Lines.quiet(quiet)
            for (v in r.visits) assertEquals(1, Regex(Regex.escape(v.heroName)).findAll(line).count(), line)
            assertTrue(s.shelf.isEmpty())
        }
        assertTrue(days >= 10, "days with visitors at a bare shelf: $days")
    }

    @Test
    fun aDayRecordedBeforeSnapshotsIsATallyNotACrash() {
        for (name in listOf("v1_forced_seed4242_day61.json", "v1_release060_active_seed4242_day61.json", "v2_balance6_expert_seed4242_day33.json", "v2_forced_seed4242_day61.json")) {
            val state = fixture(name).admitted()
            val r = assertNotNull(state.lastResolution, name)
            assertEquals(0, r.recordVersion, name)
            assertTrue(r.visits.isNotEmpty() && r.visits.all { it.customer == null && it.considered.isEmpty() }, "$name: names and reasons only")
            val s = script(r, state)
            assertTrue(s.featured.isEmpty() && s.aftermath.isEmpty() && s.moreInGazette == 0, "$name: a tally, then the ending")
            assertEquals(r.visits.size, s.tally.sumOf { it.visits.size }, name)
            assertEquals(r.visits.map { it.heroName }.sorted(), s.tally.flatMap { it.visits }.map { it.heroName }.sorted(), name)
            assertTrue(s.shelf.isEmpty() && s.prices.isEmpty())
            assertTrue(s.ending != Ending.FALLEN, name)
            assertNotNull(s.lead)
            for (v in s.visits) {
                assertEquals(v.heroName, Lines.customer(v, content))
                assertEquals(Lines.reason(v.reason).replaceFirstChar { it.uppercase() } + ".", Lines.decision(v, s, content), "$name: the bare label")
            }
            assertTrue(allLines(s, state).none { it.isBlank() })
        }
    }

    // ---- aftermath ----

    private val held = event("e10", EventType.SIEGE_WON, "Emberfall repelled the Hollow Host! Champions: Hero h1.", listOf("h1"), priority = 9)
    private fun won(hero: String, eventId: String, elite: Boolean = false, bare: Boolean = false, old: Boolean? = null) =
        FieldResult(HeroId(hero), "Hero $hero", FieldOutcome.WON, "bone scouts", elite, LaunchContent.HOLLOWBOUND, lostBareHanded = bare, lostWithOldBlade = old, eventIds = listOf(eventId))

    @Test
    fun aftermathLeadsWithTheSiege() {
        val shelf = listOf(blade("a"), blade("b"), blade("c"), blade("d"))
        val visits = listOf(bought(0, "h1", "a"), bought(1, "h2", "b"), bought(2, "h3", "c"), bought(3, "h4", "d"))
        val events = listOf(
            event("e1", EventType.EXPEDITION_WON, "Hero h1 routed bone scouts using Blade a.", listOf("h1", "a")),
            event("e2", EventType.ELITE_SLAIN, "Hero h2 slew the Bone Knight using Blade b.", listOf("h2", "b"), priority = 7),
            event("e3", EventType.HERO_DIED, "Hero h3 fell to bone scouts and will not return.", listOf("h3"), priority = 8),
            event("e4", EventType.WEAPON_LOST, "Blade c was lost with Hero h3.", listOf("c", "h3"), mapOf(WeaponFate.KEY to WeaponFate.LOST.name)),
            event("e5", EventType.EXPEDITION_LOST, "Hero h4 was driven back by bone scouts using Blade d.", listOf("h4", "d")),
            event("e9", EventType.FORGE_DAMAGED, "The forge took 6 damage in the siege.", data = mapOf("damage" to "6"), priority = 7),
            held,
        )
        val field = listOf(
            won("h1", "e1"), won("h2", "e2", elite = true),
            FieldResult(HeroId("h3"), "Hero h3", FieldOutcome.DIED, "bone scouts", eventIds = listOf("e3")),
            FieldResult(HeroId("h4"), "Hero h4", FieldOutcome.DRIVEN_BACK, "bone scouts", eventIds = listOf("e5")),
            FieldResult(HeroId("h1"), "Hero h1", FieldOutcome.HELD_THE_WALL, "Hollow Host", factionId = LaunchContent.HOLLOWBOUND, eventIds = listOf("e10")),
        )
        val siege = CombatReplay("Siege of Emberfall, day 5", 5, emptyList(), "Town held")
        val fight = CombatReplay("Hero h2 vs the Bone Knight", 5, emptyList(), "Hero h2 slew the Bone Knight", ReplayKind.EXPEDITION, "e2")
        val s = script(day(visits, events, field, shelf, listOf(siege, fight)))
        assertEquals(listOf(AftermathKind.SIEGE_HELD, AftermathKind.DEATH, AftermathKind.ELITE_SLAIN), s.aftermath.map { it.kind }, "the siege, then by rank; at most three")
        assertEquals(2, s.moreInGazette, "the win with this morning's blade and the loss")
        val wall = s.aftermath[0]
        assertEquals(listOf(listOf(HeroId("h1")), 6, siege, "Hollow Host"), listOf(wall.championIds, wall.forgeDamage, wall.replay, wall.foe))
        assertEquals("Emberfall repelled the Hollow Host! Champions: Hero h1. The forge took 6 damage in the siege.", Lines.aftermath(wall, content))
        val death = s.aftermath[1]
        assertEquals(listOf(HeroId("h3"), WeaponId("c"), WeaponFate.LOST, listOf("e3", "e4")), listOf(death.heroId, death.weapon?.weaponId, death.fate, death.eventIds))
        assertEquals(fight, s.aftermath[2].replay, "a recorded fight can be watched")
        assertNull(death.replay)

        // No siege: the same cards without it, the same order; a lost siege leads as well.
        assertEquals(listOf(AftermathKind.DEATH, AftermathKind.ELITE_SLAIN, AftermathKind.WIN_NEW_BLADE), script(day(visits, events - held, field.dropLast(1), shelf)).aftermath.map { it.kind })
        val overrun = event("e10", EventType.SIEGE_LOST, "The Hollow Host overran the defenders (Hero h1).", listOf("h1"), priority = 9)
        assertEquals(AftermathKind.SIEGE_LOST, script(day(visits, events - held + overrun, field.dropLast(1), shelf)).aftermath.first().kind)

        // A real siege day, from the engine.
        var sieges = 0
        for ((r, after) in realDays(1L..8L, config.siegeInterval)) {
            val record = r.events.firstOrNull { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST } ?: continue
            sieges++
            val real = script(r, after)
            assertEquals(record.id, real.aftermath.first().eventIds.first())
            assertTrue(real.aftermath.first().kind == AftermathKind.SIEGE_HELD || real.aftermath.first().kind == AftermathKind.SIEGE_LOST)
            assertEquals(if (r.defeated) Ending.FALLEN else if (after.pendingBlessingOffer.isNotEmpty()) Ending.BLESSING else Ending.TOMORROW, real.ending)
            assertEquals(r.defeated, real.lead == null, "no lead only when the forge has fallen")
        }
        assertTrue(sieges >= 6, "siege days: $sieges")
    }

    @Test
    fun aftermathIsAboutTheSmithsBladesAndCustomers() {
        val events = listOf(
            event("e1", EventType.EXPEDITION_WON, "Hero h1 routed bone scouts bare-handed.", listOf("h1")),
            event("e2", EventType.HERO_DIED, "Hero h2 fell to bone scouts and will not return.", listOf("h2"), priority = 8),
            event("e3", EventType.WEAPON_INHERITED, "Blade a passed from the fallen Hero h2 to guildmate Hero h3.", listOf("a", base.aliveHeroes()[0].id.value, base.aliveHeroes()[1].id.value), mapOf(WeaponFate.KEY to WeaponFate.INHERITED.name)),
            event("e4", EventType.WEAPON_RESOLD, "Hero h4 bought Blade b from a travelling merchant for 40 gold.", listOf(base.aliveHeroes()[2].id.value, "b"), mapOf("price" to "40")),
            event("e5", EventType.GUILD_MENTORED, "Hero h5 was taught by Hero h6 at the guild hall.", listOf("h5", "h6")),
        )
        val field = listOf(
            won("h1", "e1"), FieldResult(HeroId("h2"), "Hero h2", FieldOutcome.DIED, "bone scouts", eventIds = listOf("e2")),
            FieldResult(HeroId("h5"), "Hero h5", FieldOutcome.GUILD_LESSON, withHeroId = HeroId("h6"), eventIds = listOf("e5")),
        )
        // Nobody of them came to the counter and only two records name a blade: those two are the cards.
        val s = script(day(events = events, field = field, shelf = listOf(blade("a"), blade("b"))))
        assertEquals(listOf(AftermathKind.INHERITED, AftermathKind.RESOLD), s.aftermath.map { it.kind })
        assertEquals(0, s.moreInGazette)
        assertEquals(listOf(base.aliveHeroes()[0].id, base.aliveHeroes()[1].id), s.aftermath[0].let { listOf(it.heroId, it.otherHeroId) }, "the heir and the fallen")
        assertEquals(events[3].text, Lines.aftermath(s.aftermath[1], content), "a merchant's sale is told as the record tells it, never as a shop sale")
        // The pupil and the mentor both looked in today: the lesson is a card. The dead hero looked in: so is the death.
        val seen = script(day(listOf(left(0, "h5", VisitReason.NOT_BETTER), left(1, "h6", VisitReason.NOT_BETTER), left(2, "h2", VisitReason.NOT_BETTER)), events, field, listOf(blade("a"), blade("b"))))
        assertEquals(listOf(AftermathKind.DEATH, AftermathKind.INHERITED, AftermathKind.RESOLD), seen.aftermath.map { it.kind })
        assertEquals(1, seen.moreInGazette)
    }

    @Test
    fun theCounterfactualLineAppearsOnlyWhenRecorded() {
        val old = blade("old", "Iron Bow")
        val told = event("e1", EventType.EXPEDITION_WON, "Hero h1 routed bone scouts using Blade a.", listOf("h1", "a"))
        fun card(result: FieldResult, traded: WeaponSnapshot? = old, boughtToday: Boolean = true): AftermathCard? =
            script(day(if (boughtToday) listOf(bought(0, "h1", "a", old = traded)) else emptyList(), listOf(told), listOf(result), listOf(blade("a")))).aftermath.singleOrNull()
        val oldLine = "With the old Iron Bow the same fight was lost."
        val bareLine = "Bare-handed the same fight was lost."

        // A blade bought this morning in exchange for another: only the old blade may be named, and only when recorded.
        val recorded = card(won("h1", "e1", old = true))!!
        assertEquals(listOf(AftermathKind.WIN_NEW_BLADE, Counterfactual.OLD_BLADE, old), listOf(recorded.kind, recorded.counterfactual, recorded.oldWeapon))
        assertEquals("${told.text} The blade left the shop this morning. $oldLine", Lines.aftermath(recorded, content))
        for (unrecorded in listOf(won("h1", "e1", old = false), won("h1", "e1", old = null), won("h1", "e1", old = false, bare = true))) {
            val c = card(unrecorded)!!
            assertEquals(listOf(AftermathKind.WIN_NEW_BLADE, null, null), listOf(c.kind, c.counterfactual, c.oldWeapon))
            assertEquals("${told.text} The blade left the shop this morning.", Lines.aftermath(c, content), "she carried it, and the card stops there")
        }
        // Bought unarmed: bare-handed is what it replaced.
        assertEquals("${told.text} The blade left the shop this morning. $bareLine", Lines.aftermath(card(won("h1", "e1", bare = true), traded = null)!!, content))
        assertEquals("${told.text} The blade left the shop this morning.", Lines.aftermath(card(won("h1", "e1"), traded = null)!!, content))
        // Any older blade: a card only when the roll says the blade was needed, and in those words.
        val older = card(won("h1", "e1", bare = true), boughtToday = false)!!
        assertEquals(listOf(AftermathKind.WIN, Counterfactual.BARE_HANDED), listOf(older.kind, older.counterfactual))
        assertEquals("${told.text} $bareLine", Lines.aftermath(older, content))
        assertNull(card(won("h1", "e1"), boughtToday = false), "a plain win is the Gazette's to tell")

        // Real days: the line is on a card exactly when the field result holds the flag, and no card says a blade decided anything.
        var cards = 0; var claims = 0
        for ((r, after) in realDays(1L..15L, 15)) {
            for (c in script(r, after).let { it.aftermath }) {
                cards++
                val line = Lines.aftermath(c, content)
                assertFalse(Regex("decid|thanks to|because of", RegexOption.IGNORE_CASE).containsMatchIn(line), line)
                val result = r.field.firstOrNull { f -> f.outcome == FieldOutcome.WON && f.eventIds == c.eventIds }
                assertEquals(c.counterfactual == Counterfactual.OLD_BLADE, "With the old" in line, line)
                assertEquals(c.counterfactual == Counterfactual.BARE_HANDED, bareLine in line, line)
                when (c.counterfactual) {
                    Counterfactual.OLD_BLADE -> { claims++; assertEquals(true, result!!.lostWithOldBlade) }
                    Counterfactual.BARE_HANDED -> { claims++; assertTrue(result!!.lostBareHanded) }
                    null -> {}
                }
            }
        }
        assertTrue(cards > 50 && claims > 0, "cards=$cards claims=$claims")
    }

    // ---- lines ----

    @Test
    fun everyLineMapsToARecordedField() {
        var lines = 0; var withNumbers = 0
        for ((r, after) in realDays(1L..12L, 15) + realDays(1L..4L, 10, busy)) {
            val s = script(r, after)
            val bladeNames = r.shopWeapons.map { it.name }
            for (v in s.visits) {
                val c = v.customer
                // Every number a visit's lines may print is one the visit recorded.
                val recorded = (v.considered.flatMap { k -> listOfNotNull(k.price, k.shortBy, k.shortBy?.let { k.price - it }) } + r.shelfPrices.values +
                    listOfNotNull(v.sale?.listedPrice, v.sale?.cashPaid, v.sale?.tradeInCredit, v.sale?.saleBonus)).toSet()
                val text = listOf(Lines.customer(v, content), Lines.decision(v, s, content)) + v.considered.map { Lines.considered(it, s) }
                for (line in text) {
                    lines++
                    val stripped = (bladeNames + listOfNotNull(c?.equipped?.name, c?.name)).fold(line) { acc, name -> acc.replace(name, "") }
                    val numbers = Regex("\\d+").findAll(stripped).map { it.value.toInt() }.toList()
                    if (numbers.isNotEmpty()) withNumbers++
                    assertTrue(recorded.containsAll(numbers), "day ${r.day} ${v.heroName}: $line has a number the visit does not hold ($recorded)")
                    assertFalse("%" in line || "chance" in line.lowercase() || "odds" in line.lowercase(), line)
                }
                // A refusal over the purse states what they could pay; a purchase the listed price; a trade-in the blade that came back.
                val decision = Lines.decision(v, s, content)
                if (v.reason == VisitReason.TOO_EXPENSIVE) assertTrue("Could pay up to ${v.considered.first().let { it.price - it.shortBy!! }} gold; the cheapest blade is" in decision, decision)
                if (v.kind == VisitKind.BROWSE && v.sale != null) assertTrue("for ${v.sale!!.listedPrice} gold" in decision && s.blade(v.purchasedWeaponId)!!.name in decision, decision)
                if (v.sale?.tradeInWeaponId != null) assertTrue(c!!.equipped!!.name in decision && "${v.sale!!.tradeInCredit} gold off" in decision, decision)
                if (c != null) assertTrue(c.name in Lines.customer(v, content) && (c.equipped?.name ?: "no weapon") in Lines.customer(v, content))

                // The same visit with its fields gone says less, never something else: the bare label.
                val bare = v.copy(customer = null, considered = emptyList(), sale = null)
                assertEquals(Lines.reason(v.reason).replaceFirstChar { it.uppercase() } + ".", Lines.decision(bare, s, content))
                assertEquals(v.heroName, Lines.customer(bare, content))
            }
            for (g in s.tally) assertTrue(Lines.tally(g, s).startsWith("${g.visits.size} "))
            for (card in s.aftermath) {
                assertTrue(card.eventIds.isNotEmpty() && card.eventIds.all { id -> r.events.any { it.id == id } }, "a card points at records of the day")
                assertTrue(Lines.aftermath(card, content).startsWith(r.events.first { it.id == card.eventIds.first() }.text), "and opens with the record's own sentence")
                card.weapon?.let { w -> assertTrue(card.eventIds.any { id -> r.events.first { it.id == id }.subjectIds.contains(w.weaponId.value) }, "its blade is one the record names") }
            }
            assertTrue(allLines(s, after).none { "%" in it || it.isBlank() })
        }
        assertTrue(lines > 1500 && withNumbers > 500, "lines=$lines withNumbers=$withNumbers")

        // Templates with a field missing drop the clause that needed it.
        val shelf = listOf(blade("a", "Iron Sword"), blade("b", "Bronze Axe"))
        val bow = blade("old", "Iron Bow")
        val day = script(day(shelf = shelf))
        fun line(v: MarketVisit) = Lines.decision(v, day, content)
        val dear = left(0, "h1", VisitReason.TOO_EXPENSIVE, listOf(Considered(WeaponId("a"), 60, listOf(VisitFactor.CANNOT_AFFORD), shortBy = 12)))
        assertEquals("Could pay up to 48 gold; the cheapest blade is 60 gold.", line(dear))
        assertEquals("Could pay up to 48 gold.", Lines.decision(dear, day.copy(prices = emptyMap()), content))
        assertEquals("Could afford nothing on the shelf.", line(dear.copy(considered = listOf(Considered(WeaponId("a"), 60)))))
        val sale = bought(1, "h2", "a", cash = 40, old = bow)
        assertEquals("Iron Sword is stronger than their Iron Bow. Bought Iron Sword for 45 gold. Iron Bow came back in part payment: 5 gold off, 40 gold in coin.", line(sale))
        assertEquals("Bought a blade for 45 gold. Their old blade came back in part payment: 5 gold off, 40 gold in coin.", line(sale.copy(customer = null, purchasedWeaponId = WeaponId("gone"))))
        assertEquals("Hero h2, Guardian. Carries Iron Bow.", Lines.customer(sale, content))
        assertEquals("Hero h3, Guardian, a regular. Carries no weapon.", Lines.customer(bought(2, "h3", "a", regular = true), content))
        assertEquals("Iron Sword, 60 gold: beyond their purse; short by 12 gold", Lines.considered(dear.considered.single(), day))
        assertEquals("Nothing on the shelf beats their Iron Bow.", line(left(3, "h4", VisitReason.NOT_BETTER).let { it.copy(customer = it.customer!!.copy(equipped = bow)) }))
        assertEquals("Found nothing better than the blade in hand.", line(left(3, "h4", VisitReason.NOT_BETTER)))
        assertEquals("Bronze Axe is not a weapon for a Guardian.", line(left(4, "h5", VisitReason.NOT_SUITED, listOf(Considered(WeaponId("b"), 30, listOf(VisitFactor.OFF_CLASS))))))
        assertEquals("Bronze Axe at 300 gold is more than they hold fair.", line(left(5, "h6", VisitReason.OVERPRICED, listOf(Considered(WeaponId("b"), 300, listOf(VisitFactor.ABOVE_THEIR_CEILING))))))
        assertEquals("Balked at the price.", line(left(5, "h6", VisitReason.OVERPRICED)))
        assertEquals("Collected the commissioned Iron Sword and paid 140 gold.", line(patron(6, "h7", "a")))
        assertEquals("Paid 90 gold for Bronze Axe and carried it off.", line(collector(7, "b")))
        VisitReason.entries.forEach { assertTrue(Lines.reason(it).isNotBlank()) }
        VisitFactor.entries.forEach { assertTrue(Lines.factor(it).isNotBlank()) }
    }

    // ---- purity ----

    @Test
    fun scriptIsAPureFunctionOfItsInputs() {
        for ((r, after) in realDays(1L..6L, 12)) {
            val text = SaveCodec.encodeRun(after)
            val first = script(r, after)
            Demand.summary(after, content, config); Advice.lead(after, content, config); allLines(first, after)
            assertEquals(first, script(r, after), "day ${r.day}: the same inputs give the same script")
            assertEquals(first, script(SaveCodec.decodeRun(text).lastResolution!!, SaveCodec.decodeRun(text)), "and so does the day read back from the save")
            assertEquals(text, SaveCodec.encodeRun(after), "day ${r.day}: nothing was written, no RNG stream moved")
            assertEquals(SaveCodec.decodeRun(text).rng, after.rng)
            // Reading the script is no input to the next day.
            if (!after.isEnded) assertEquals(engine.handle(SaveCodec.decodeRun(text), Command.EndDay(endDayId(after))), engine.handle(after, Command.EndDay(endDayId(after))))
        }
        // No source of chance is reachable from the package at all.
        val sources = File("src/main/kotlin/com/tinyblacksmith/core/shopday").listFiles()!!.filter { it.extension == "kt" }
        assertTrue(sources.size >= 4, "the package was found: $sources")
        for (f in sources) assertFalse(Regex("\\.rng\\b|\\brng\\(|Random|RngStream|nanoTime|currentTimeMillis").containsMatchIn(f.readText()), "${f.name} reaches for chance or the clock")
    }

    /** The stored day of the schema-3 fixture (day 60 of seed 4242): the same on every run and every JVM. */
    @Test
    fun theScriptOfTheFixturesDayIsPinned() {
        val state = fixture("v3_forced_seed4242_day61.json")
        val s = script(state.lastResolution!!, state)
        val told = listOf(
            "day ${s.day}, quiet ${s.quiet?.kind}, ending ${s.ending}",
            "featured " + s.featured.joinToString { "${it.seq}:${it.heroName}:${it.reason}" },
            "tally " + s.tally.joinToString { "${it.outcome}/${it.reason}=${it.visits.map { v -> v.seq }}" },
            "aftermath " + s.aftermath.joinToString { "${it.kind}:${it.heroName}:${it.weapon?.name}:${it.counterfactual}" } + " +${s.moreInGazette}",
        )
        assertEquals(PINNED_FIXTURE_DAY, told.joinToString("\n"))
        println("SHOP DAY SCRIPT of the v3 fixture:\n" + (told + allLines(s, state)).joinToString("\n"))
    }

    companion object {
        val PINNED_FIXTURE_DAY = """
            day 60, quiet null, ending BLESSING
            featured 0:Piet Underhill:NOT_BETTER, 2:Elspeth Coldwater:TOO_EXPENSIVE
            tally LEFT/NOT_BETTER=[1, 3]
            aftermath SIEGE_HELD:null:null:null, GUILD_LESSON:Sten Quill:null:null, LOSS:Greta Holloway:Iron Dagger:null +1
        """.trimIndent()
    }
}
