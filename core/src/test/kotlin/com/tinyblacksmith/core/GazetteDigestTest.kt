package com.tinyblacksmith.core

import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.gazette.GazetteDigest
import com.tinyblacksmith.core.gazette.GazetteDigest.Kind
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.DayResolution
import com.tinyblacksmith.core.model.EventRecord
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.IncomeKind
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.MarketVisit
import com.tinyblacksmith.core.model.ShopLedger
import com.tinyblacksmith.core.model.VisitKind
import com.tinyblacksmith.core.model.VisitReason
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Gazette's daily briefing: grouping, what may never be hidden, what is never invented, and one story for fresh and archived days. */
class GazetteDigestTest {
    private val content = LaunchContent.catalog
    private val names = mapOf("h1" to "Mira Vance", "h2" to "Bram Holt", "h3" to "Ione Dray", "h4" to "Thane Kestrel", "h5" to "Sable Stonebrook")
    private val weapons = mapOf("w1" to "Winterwake", "w2" to "Stormsong", "w3" to "Iron Axe")
    private var serial = 0

    private fun ev(type: EventType, priority: Int, text: String, subjects: List<String> = emptyList(), data: Map<String, String> = emptyMap()) =
        EventRecord("e${serial++}", 1, 8, type, priority, text, subjects, data)

    private fun digest(
        events: List<EventRecord>, warnedBefore: Set<String> = emptySet(), resolution: DayResolution? = null, claimHint: Boolean = false,
    ) = GazetteDigest.of(GazetteDigest.Facts(8, events, warnedBefore, names, weapons, content, resolution, emptyList(), claimHint))

    private fun resolution(events: List<EventRecord>, visits: List<MarketVisit>, gold: Int?) = DayResolution(
        CommandId("c1"), 8, events, emptyList(), visits, emptyList(), defeated = false,
        ledger = gold?.let { ShopLedger(100, 100 + it, mapOf(IncomeKind.SHELF_SALE to it), 0, 0) },
    )

    private fun visit(n: Int, reason: VisitReason, kind: VisitKind = VisitKind.BROWSE) =
        MarketVisit(HeroId("h$n"), "Hero $n", null, reason, seq = n, kind = kind)

    @Test
    fun aSiegeWinIsOneBlockWithItsDamageAndTribute() {
        val events = listOf(
            ev(EventType.FORGE_DAMAGED, 7, "The forge took 12 damage in the siege.", data = mapOf("damage" to "12")),
            ev(
                EventType.SIEGE_WON, 9, "Emberfall held against the Ashclaw Raiders. Mira Vance, Bram Holt, Ione Dray defended the walls.",
                listOf("h1", "h2", "h3"), mapOf("attacker" to "the Ashclaw Raiders"),
            ),
            ev(EventType.MILESTONE, 4, "A champion defended the town with a weapon from this forge: Winterwake.", data = mapOf("milestone" to "CHAMPION_ARMED")),
            ev(EventType.MILESTONE, 6, "Krag was defeated at the walls. The town paid you 100 gold.", data = mapOf("tribute" to "100")),
            ev(EventType.MILESTONE, 4, "Emberfall survived its first siege.", data = mapOf("milestone" to "SIEGE_SURVIVED")),
            ev(EventType.HERO_RESTED, 0, "Sable Stonebrook rested and regained health.", listOf("h5")),
        )
        val d = digest(events)
        val story = d.stories.single()
        assertEquals(Kind.SIEGE_WON, story.kind)
        assertEquals("Emberfall held", story.heading)
        assertEquals("Mira Vance, Bram Holt and Ione Dray defended the walls against the Ashclaw Raiders. The forge took 12 damage and the town paid you 100 gold.", story.body)
        assertEquals(setOf("e0", "e1", "e2", "e3", "e4"), story.eventIds.toSet(), "the siege folds its damage, its tribute and its milestones")
        assertTrue(d.more.isEmpty())
        assertTrue(d.details.sections.any { section -> section.lines.any { "Sable Stonebrook" in it } }, "the routine record is still in All details")
    }

    @Test
    fun aDeathAndTheFateOfItsWeaponAreOneBlock() {
        val events = listOf(
            ev(EventType.HERO_DIED, 8, "Thane Kestrel fell to an Ashclaw warchief.", listOf("h4")),
            ev(EventType.WEAPON_RECOVERED, 5, "Iron Axe returned to the forge after Thane Kestrel died.", listOf("w3", "h4"), mapOf("fate" to "RECOVERED")),
        )
        val d = digest(events)
        val story = d.stories.single()
        assertEquals(Kind.DEATH, story.kind)
        assertEquals("Thane Kestrel died", story.heading)
        assertEquals("Thane Kestrel fell to an Ashclaw warchief. Iron Axe returned to the forge after Thane Kestrel died.", story.body)
    }

    @Test
    fun noDeathOrEndOfEraIsEverHiddenByTheLimit() {
        val commissions = (1..5).map { ev(EventType.COMMISSION_COMPLETED, 5, "Hero $it collected Iron Sword and paid 90 gold for the commission.", listOf("h$it", "w1", "c$it")) }
        val core = listOf(
            ev(EventType.SIEGE_WARNING, 6, "Hollowbound will attack on day 13.", data = mapOf("day" to "13")),
            ev(EventType.SIEGE_LOST, 9, "The Ashclaw Raiders overran the defenders (Mira Vance, Bram Holt, Ione Dray).", listOf("h1", "h2", "h3"), mapOf("attacker" to "the Ashclaw Raiders")),
            ev(EventType.HERO_DIED, 8, "Mira Vance died defending the walls.", listOf("h1")),
            ev(EventType.HERO_DIED, 8, "Bram Holt died defending the walls.", listOf("h2")),
            ev(EventType.HERO_DIED, 8, "Ione Dray died defending the walls.", listOf("h3")),
            ev(EventType.FORGE_DESTROYED, 10, "The forge has fallen. This era is over."),
        )
        val d = digest(commissions + core, claimHint = true)
        val kinds = d.stories.map { it.kind }
        assertTrue(Kind.ERA_ENDED in kinds && Kind.SIEGE_LOST in kinds && Kind.SIEGE_WARNING in kinds, "$kinds")
        val deaths = d.stories.single { it.kind == Kind.DEATHS }
        assertEquals("3 heroes died", deaths.heading)
        assertEquals("Mira Vance, Bram Holt and Ione Dray died.", deaths.body)
        assertEquals(3, deaths.details.size, "every death's own record stays in the block's details")
        assertEquals("This era ended on day 8. Claim your legacy points to begin the next.", d.stories.first { it.kind == Kind.ERA_ENDED }.body)
        // Nothing is dropped: what did not fit is one disclosure away.
        assertEquals(4 + 5, d.stories.size + d.more.size)
        assertEquals(GazetteDigest.MAX_STORIES, d.stories.size)
        assertTrue(d.more.all { it.kind == Kind.COMMISSION_DONE })
    }

    @Test
    fun fewDeathsStayAsTheirOwnBlocks() {
        val d = digest(listOf(ev(EventType.HERO_DIED, 8, "Mira Vance fell to a raider.", listOf("h1")), ev(EventType.HERO_DIED, 8, "Bram Holt fell to a raider.", listOf("h2"))))
        assertEquals(listOf("Mira Vance died", "Bram Holt died"), d.stories.map { it.heading })
    }

    @Test
    fun routineActivityIsNotInTheDefaultViewButIsInAllDetails() {
        val events = listOf(
            ev(EventType.HERO_RESTED, 0, "Mira Vance rested and regained health.", listOf("h1")),
            ev(EventType.HERO_PATROLLED, 1, "Bram Holt patrolled the town walls.", listOf("h2")),
            ev(EventType.GUILD_TRAINED, 0, "Ione Dray trained with the Ellery Company.", listOf("h3")),
            ev(EventType.HERO_LEVELED, 2, "Thane Kestrel reached level 2.", listOf("h4")),
            ev(EventType.WEAPON_LISTED, 0, "Iron Axe was placed on the shelf for 60 gold.", listOf("w3")),
            ev(EventType.WEAPON_HONED, 2, "Honed Iron Axe to quality 40.", listOf("w3")),
            ev(EventType.WEAPON_FORGED, 1, "Forged Iron Axe. Common. Quality 38.", listOf("w3")),
            ev(EventType.MATERIAL_BOUGHT, 0, "Bought 3 Iron for 30 gold."),
            ev(EventType.EXPEDITION_WON, 3, "Sable Stonebrook routed an Ashclaw scout bare-handed.", listOf("h5")),
            ev(EventType.WEAPON_EQUIPPED, 2, "Thane Kestrel now wields Iron Axe.", listOf("h4", "w3")),
            ev(EventType.DISCOVERY, 1, "First notes on Iron on Axes. Seems promising.", data = mapOf("key" to "af:iron|axe")),
        )
        val d = digest(events)
        assertTrue(d.isQuiet, "no story: ${d.stories}")
        assertEquals(GazetteDigest.QUIET, d.headline)
        assertTrue(d.details.sections.flatMap { it.lines }.isNotEmpty())
    }

    @Test
    fun aHintAboutASignatureRecipeIsAClueNotAPairing() {
        val d = digest(listOf(ev(EventType.DISCOVERY, 3, "Notes on Silver + Stormglass Bow. A signature recipe is hidden here.", data = mapOf("key" to "sig:stormsong"))))
        val story = d.stories.single()
        assertEquals(Kind.CLUE, story.kind)
        assertTrue(story.opensNotebook)
    }

    @Test
    fun aSiegeWarningIsNewsOnceAndThenAReminder() {
        val trait = content.siegeTraits.first()
        val warning = ev(
            EventType.SIEGE_WARNING, 6, "Ashclaw Raiders will attack on day 9. Current pressure is rising threat. Frost weapons are especially effective against them.",
            data = mapOf("day" to "9", "faction" to "Ashclaw Raiders", "weak" to "frost", "trait" to trait.id),
        )
        val fresh = digest(listOf(warning))
        val story = fresh.stories.single()
        assertEquals("Siege on day 9", story.heading)
        assertEquals("Ashclaw Raiders, weak to frost. ${trait.description.substringBefore(". ")}.", story.body)
        assertTrue(digest(listOf(warning), warnedBefore = setOf("9")).isQuiet, "the same siege again is a reminder")
        assertEquals(1, digest(listOf(warning), warnedBefore = setOf("5")).stories.size, "a warning for another siege is news")
    }

    @Test
    fun theShopLineCountsTheLedgerAndNamesOneGroupedProblem() {
        val sales = (1..3).map { ev(EventType.WEAPON_SOLD, 4, "Hero $it bought Iron Sword for 50 gold.", listOf("h$it", "w$it"), mapOf("price" to "50")) }
        val four = (1..4).map { visit(it, VisitReason.TOO_EXPENSIVE) }
        val d = digest(sales, resolution = resolution(sales, four, gold = 146))
        assertEquals(listOf("Shop income 146 gold. 3 weapons sold.", "Four visitors couldn't afford anything on the shelf."), d.shop)
        assertFalse(d.shop.any { "too high" in it }, "a purse that could not pay is not a price complaint")

        val mixed = four + listOf(visit(5, VisitReason.OVERPRICED), visit(6, VisitReason.OVERPRICED))
        assertEquals("Two visitors thought the price was too high.", digest(sales, resolution = resolution(sales, mixed, 146)).shop[1])
        val empty = mixed + visit(7, VisitReason.EMPTY_SHELVES)
        assertEquals("The shelf was empty when one visitor arrived.", digest(sales, resolution = resolution(sales, empty, 146)).shop[1])
        val notSuited = (1..3).map { MarketVisit(HeroId("h$it"), "Hero $it", null, VisitReason.NOT_SUITED, seq = it, customer = com.tinyblacksmith.core.model.CustomerSnapshot(HeroId("h$it"), "Hero $it", content.classes.first().id, 1, "a", gold = 10)) }
        assertEquals("Three ${content.classes.first().name}s found no suitable weapon.", digest(sales, resolution = resolution(sales, notSuited, 146)).shop[1])
    }

    @Test
    fun unknownIncomeIsOmittedAndAKnownEmptyDayIsSaid() {
        // An archived day with no ledger, no stored shop record and no sale records: its takings are unknown, not zero.
        assertEquals(emptyList(), digest(listOf(ev(EventType.HERO_RESTED, 0, "Mira Vance rested and regained health.", listOf("h1")))).shop)
        // The same day with the stored shop record says what happened.
        val shopDay = ev(EventType.SHOP_DAY, 0, "No visitors came by. 0 bought something.", data = mapOf("visitors" to "0", "bought" to "0"))
        assertEquals(listOf("No sales today."), digest(listOf(shopDay)).shop)
    }

    @Test
    fun aCommissionIsOneSaleNotTwo() {
        val events = listOf(
            ev(EventType.COMMISSION_COMPLETED, 5, "Bram Holt collected Iron Sword and paid 140 gold for the commission.", listOf("h2", "w1", "c1"), mapOf("reward" to "140")),
            ev(EventType.WEAPON_SOLD, 4, "Mira Vance bought Iron Axe for 60 gold.", listOf("h1", "w3"), mapOf("price" to "60")),
        )
        val v = listOf(visit(1, VisitReason.GREAT_FIT), visit(2, VisitReason.COMMISSION_DELIVERED, VisitKind.COMMISSION))
        assertEquals("Shop income 200 gold. 2 weapons sold.", digest(events, resolution = resolution(events, v, 200)).shop.first())
    }

    // ---- simulated runs: fresh and archived days, forecasts and sentence shape ----

    private val engine = GameEngine()

    private fun play(seed: Long, days: Int): GameState = SimulationDriver(maxDays = days).playRun(LegacyProfile(), seed, Policy.BALANCED_ACTIVE).second

    @Test
    fun theFreshReportAndTheArchiveTellTheSameDay() {
        var compared = 0
        for (seed in 1L..4L) {
            val state = play(seed, 14)
            val last = state.lastResolution?.day ?: continue
            val fresh = GazetteDigest.of(state, last, engine.content, engine.config)
            val archive = GazetteDigest.of(state.copy(lastResolution = null), last, engine.content, engine.config)
            assertEquals(fresh.stories, archive.stories, "seed $seed: the same stories")
            assertEquals(fresh.more, archive.more, "seed $seed: the same overflow")
            val income = { d: GazetteDigest.Digest -> d.shop.firstOrNull { it.startsWith("Shop income") || it.startsWith("No sales") } }
            assertEquals(income(fresh), income(archive), "seed $seed: the same takings")
            compared++
        }
        assertTrue(compared > 0)
    }

    @Test
    fun everyLineOfASimulatedPaperIsWellFormedAndInventsNoCounterfactual() {
        var days = 0
        var stories = 0
        for (seed in 1L..6L) {
            val state = play(seed, 16)
            for (day in 1..(state.lastResolution?.day ?: 0)) {
                val d = GazetteDigest.of(state, day, engine.content, engine.config)
                days++
                val lines = d.stories.flatMap { listOf(it.heading, it.body) + it.details } + d.more.flatMap { listOf(it.heading, it.body) + it.details } + d.shop + d.before +
                    d.details.lede + d.details.sections.flatMap { it.lines } + d.details.tally
                stories += d.stories.size
                for (line in lines.filter { it.isNotEmpty() }) {
                    assertFalse(line.contains('—') || line.contains(';') || line.contains(".."), "seed $seed day $day: $line")
                    assertFalse(line.contains("would have lost"), "seed $seed day $day: a counterfactual only the shop-day card may state: $line")
                }
                for (s in d.stories + d.more) {
                    assertTrue(s.heading.first().isUpperCase() || s.heading.first().isDigit(), "heading: ${s.heading}")
                    assertTrue(s.body.isEmpty() || (s.body.first().isUpperCase() && s.body.endsWith(".")), "body: ${s.body}")
                    assertFalse(s.body.contains(" ."), s.body)
                }
                assertTrue(d.before.size <= GazetteDigest.MAX_NOTICES)
                val mandatory = (d.stories + d.more).count { it.mandatory }
                assertTrue(d.stories.size <= maxOf(GazetteDigest.MAX_STORIES, mandatory), "day $day: ${d.stories.size} stories")
            }
        }
        assertTrue(days > 50 && stories > 0, "days=$days stories=$stories")
    }

    @Test
    fun anOlderPaperNeverCarriesTodaysAdvice() {
        val state = play(2L, 12)
        val last = state.lastResolution!!.day
        val siegeTomorrow = state.copy(town = state.town.copy(nextSiegeDay = state.day))
        val latest = GazetteDigest.of(siegeTomorrow, last, engine.content, engine.config)
        assertTrue("The siege starts after tomorrow's trading." in latest.before, "${latest.before}")
        val older = GazetteDigest.of(siegeTomorrow, last - 1, engine.content, engine.config)
        assertTrue(older.before.none { "tomorrow's trading" in it }, "${older.before}")
        assertNull(older.before.firstOrNull { it.startsWith("The town has offered") }, "no pending-choice notice in an older paper")
    }
}
