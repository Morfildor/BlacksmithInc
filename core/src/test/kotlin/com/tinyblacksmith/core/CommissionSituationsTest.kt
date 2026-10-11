package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.market.QualityBand
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.shopday.Lines
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** E4: a commission has a reason read from the town, two may be open at once, and a blade ordered for someone reaches them. */
class CommissionSituationsTest {
    private val engine = TestSupport.engine
    private val content = engine.content
    private val base = engine.config

    /** The daily roll always offers, and only [kind] has any weight (the ordinary kind too when [ordinary]). */
    private fun only(kind: CommissionKind?, ordinary: Boolean = false): BalanceConfig = base.copy(
        commissionChancePerDay = 1.0,
        commissions = base.commissions.copy(
            ordinaryWeight = if (ordinary) 1.0 else 0.0, replacementWeight = if (kind == CommissionKind.REPLACEMENT) 1.0 else 0.0,
            siegePrepWeight = if (kind == CommissionKind.SIEGE_PREP) 1.0 else 0.0, ambitionWeight = if (kind == CommissionKind.AMBITION) 1.0 else 0.0,
            firstBladeWeight = if (kind == CommissionKind.FIRST_BLADE) 1.0 else 0.0,
        ),
    )

    private val forged = engine.newRun(LegacyProfile(), 7).withMaterials().forgeAccepted(quickSword(Risk.SAFE))
    private val blade = forged.state.weapon(forged.forgedWeaponId!!).copy(affixes = emptyList(), flaws = emptyList(), element = null, power = 30, quality = 40)
    /** A quiet town: nobody armed, no ambition, no guild, no request, the siege a week off. */
    private val town = forged.state.copy(
        heroes = forged.state.heroes.mapValues { it.value.copy(ambition = null, guildId = null) }, weapons = emptyMap(), commissions = emptyMap(),
        town = forged.state.town.copy(championIds = emptyList(), nextSiegeDay = forged.state.day + 7, guilds = emptyList()),
    )
    private val heroes = town.aliveHeroes()

    private fun offered(state: GameState, config: BalanceConfig): Pair<ResolutionContext, Commission?> {
        val ctx = ResolutionContext(state, content, config)
        Market.maybeOfferCommission(ctx)
        return ctx to ctx.commissions.values.firstOrNull { it.id !in state.commissions }
    }

    private fun GameState.armed(hero: Hero, w: Weapon, id: String) = copy(weapons = weapons + (WeaponId(id) to w.copy(id = WeaponId(id), location = WeaponLocation.Owned(hero.id, true))))

    @Test
    fun aBrokenBladeProducesAReplacementRequest() {
        val (worn, bereft, keen) = heroes
        // One carries a worn blade, one carried a blade this era that has since shattered, one carries a keen blade.
        val broken = blade.copy(id = WeaponId("w92"), location = WeaponLocation.Destroyed(1), history = blade.history + HistoryEntry(town.era, 1, "SOLD", "Sold to ${bereft.fullName}.", listOf(bereft.id.value)))
        val s = town.armed(worn, blade.copy(condition = base.wornConditionThreshold - 1), "w91").armed(keen, blade, "w93").let { it.copy(weapons = it.weapons + (broken.id to broken)) }
        val ctx0 = ResolutionContext(s, content, base)
        assertEquals(setOf(worn.id, bereft.id), Market.commissionSituations(ctx0, heroes)[CommissionKind.REPLACEMENT]?.map { it.id }?.toSet(), "the worn and the bereft; not the keen, not those who never carried one")

        val seen = mutableSetOf<HeroId>()
        for (seed in 1L..30L) {
            val (_, c) = offered(s.copy(rng = engine.newRun(LegacyProfile(), seed).rng), only(CommissionKind.REPLACEMENT))
            assertNotNull(c)
            assertEquals(CommissionKind.REPLACEMENT, c.kind)
            assertTrue(c.buyerId in setOf(worn.id, bereft.id) && c.recipientId == null)
            assertTrue(c.familyId in content.heroClass(s.hero(c.buyerId).classId).preferredFamilies)
            assertEquals("${s.hero(c.buyerId).fullName} needs a replacement weapon.", Lines.commissionWhy(c, s))
            seen += c.buyerId
        }
        assertEquals(setOf(worn.id, bereft.id), seen)
        // Nobody with that reason: no request of that kind, whatever the roll; an ordinary request needs none and says no why.
        assertNull(offered(town, only(CommissionKind.REPLACEMENT)).second)
        val plain = assertNotNull(offered(town, only(null, ordinary = true)).second)
        assertEquals(CommissionKind.ORDINARY, plain.kind)
        assertNull(Lines.commissionWhy(plain, town))
        // The paper's line carries the reason, and the record its kind.
        val (ctx, c) = offered(s, only(CommissionKind.REPLACEMENT))
        val told = ctx.newEvents.single { it.type == EventType.COMMISSION_OFFERED }
        assertTrue(told.text.endsWith("needs a replacement weapon.") && told.data["kind"] == "REPLACEMENT" && c!!.id.value in told.subjectIds, told.text)
    }

    /** The blade that broke leaves the save a retention window later (`WeaponPruning`); its carrier's reason to ask does not go with it. */
    @Test
    fun aBladeBrokenLongAgoStillProducesAReplacementRequest() {
        val bereft = heroes[1]
        val broken = blade.copy(id = WeaponId("w92"), location = WeaponLocation.Destroyed(1), history = blade.history + HistoryEntry(town.era, 1, "SOLD", "Sold to ${bereft.fullName}.", listOf(bereft.id.value)))
        val late = town.copy(day = base.weaponRetentionDays + 5, weapons = mapOf(broken.id to broken), town = town.town.copy(nextSiegeDay = base.weaponRetentionDays + 12))
        fun dayAfter(config: BalanceConfig): GameState = (GameEngine(content, config).handle(late, Command.EndDay(TestSupport.endDayId(late))) as CommandOutcome.Accepted).state
        val pruned = dayAfter(base)
        val kept = dayAfter(base.copy(weaponRetentionDays = 0))
        assertTrue(pruned.hero(bereft.id).isAlive && pruned.equippedWeapon(bereft.id) == null, "still about and still carrying nothing")
        assertEquals(kept.copy(weapons = emptyMap()), pruned.copy(weapons = emptyMap()), "the same day either way")
        for ((name, s) in listOf("never pruned" to kept.copy(commissions = emptyMap()), "pruned" to pruned.copy(commissions = emptyMap()))) {
            val asking = Market.commissionSituations(ResolutionContext(s, content, base), s.aliveHeroes())[CommissionKind.REPLACEMENT]?.map { it.id }
            assertEquals(listOf(bereft.id), asking, "$name: the hero whose blade broke on day 1")
            assertEquals(bereft.id, offered(s, only(CommissionKind.REPLACEMENT)).second?.buyerId, name)
        }
        // The broken blade is kept for that question only: it goes the evening its carrier holds another.
        assertTrue(broken.id in pruned.weapons)
        val rearmed = pruned.armed(bereft, blade, "w93")
        assertTrue(broken.id !in (engine.handle(rearmed, Command.EndDay(TestSupport.endDayId(rearmed))) as CommandOutcome.Accepted).state.weapons)
    }

    @Test
    fun aChampionAsksBeforeASiege() {
        val champion = heroes.first()
        val feared = content.faction(Battle.leadingFaction(ResolutionContext(town, content, base))!!.id).weakTo!!
        fun at(days: Int, edit: (GameState) -> GameState = { it }) = edit(town.copy(town = town.town.copy(championIds = listOf(champion.id), nextSiegeDay = town.day + days)))
        for (days in 1..base.commissions.siegePrepDays) {
            val s = at(days)
            val c = assertNotNull(offered(s, only(CommissionKind.SIEGE_PREP)).second, "$days days out")
            assertEquals(listOf(CommissionKind.SIEGE_PREP, champion.id, feared, s.town.nextSiegeDay), listOf(c.kind, c.buyerId, c.element, c.deadlineDay))
            assertEquals("${champion.fullName} is preparing to defend the walls on day ${s.town.nextSiegeDay}.", Lines.commissionWhy(c, s))
            assertTrue(c.reward > base.commissionRewardBase + c.minQuality * base.commissionRewardPerQuality, "an element asked for pays more")
        }
        assertNull(offered(at(base.commissions.siegePrepDays + 1), only(CommissionKind.SIEGE_PREP)).second, "too far off")
        assertNull(offered(at(0), only(CommissionKind.SIEGE_PREP)).second, "the siege is tonight: too late to ask")
        assertNull(offered(at(2) { it.armed(champion, blade.copy(element = feared), "w91") }, only(CommissionKind.SIEGE_PREP)).second, "already carries the element the besieger fears")
        assertNull(offered(at(2) { it.copy(town = it.town.copy(championIds = emptyList())) }, only(CommissionKind.SIEGE_PREP)).second, "nobody is a champion")

        // Delivered on the siege day and taken up for that fight: weaker on a calm day, worth more against this besieger.
        val c = offered(at(1), only(CommissionKind.SIEGE_PREP)).second!!
        val s = at(1) { it.armed(champion, blade.copy(familyId = c.familyId), "w91") }   // the champion's own: the same family, four points stronger, no element
        val answer = blade.copy(id = WeaponId("w95"), familyId = c.familyId, element = feared, power = 26, quality = c.minQuality, location = WeaponLocation.Storage)
        assertEquals(Commissions.Fit.OK, Commissions.fit(answer, c))
        val ready = s.copy(day = s.town.nextSiegeDay, weapons = s.weapons + (answer.id to answer), commissions = mapOf(c.id to c.copy(status = CommissionStatus.ACCEPTED)), nextCommissionSerial = s.nextCommissionSerial + 1)
        val out = engine.handle(ready, Command.EndDay(TestSupport.endDayId(ready))) as CommandOutcome.Accepted
        assertEquals(CommissionStatus.COMPLETED, out.state.commissions.getValue(c.id).status)
        assertTrue(out.resolution!!.events.any { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }, "the siege was fought that evening")
        assertTrue(out.resolution!!.events.any { it.type == EventType.WEAPON_EQUIPPED && answer.id.value in it.subjectIds }, "and the champion took the new blade up for it")
        // The same blade on an ordinary request would have stayed a spare.
        val spare = ResolutionContext(ready.copy(commissions = mapOf(c.id to c.copy(status = CommissionStatus.ACCEPTED, kind = CommissionKind.ORDINARY))), content, base)
        Market.resolveCommissions(spare)
        assertEquals(WeaponLocation.Owned(champion.id, false), spare.weapon(answer.id).location)
    }

    @Test
    fun noKindExceedsFortyPercentOfOffers() {
        val all = mutableMapOf<String, Int>()
        for (policy in listOf(Policy.BALANCED_FAIR, Policy.BALANCED_ACTIVE, Policy.REQUEST_DRIVEN, Policy.EXPERT, Policy.NOVICE)) {
            val offers = mutableMapOf<String, Int>()
            for (seed in 1L..60L) SimulationDriver(customerMetrics = true).playRun(LegacyProfile(), seed, policy).first.customers!!.commissionOffers.forEach { (k, n) -> offers.merge(k, n, Int::plus) }
            val total = offers.values.sum()
            assertTrue(total > 200, "$policy saw offers ($total)")
            for ((kind, n) in offers) assertTrue(n <= 0.40 * total, "$policy: $kind is $n of $total")
            offers.forEach { (k, n) -> all.merge(k, n, Int::plus) }
        }
        // WALL_PLEDGE and HEIRLOOM are only ever made by a morning visitor's answer (EncountersTest), never by the daily offer.
        assertEquals((CommissionKind.entries - CommissionKind.WALL_PLEDGE - CommissionKind.HEIRLOOM).map { it.name }.toSet(), all.keys, "every kind is offered in ordinary play: $all")
    }

    @Test
    fun twoOpenAtOnceNeverThree() {
        // A smith who accepts every request and never forges: requests pile up to the cap and no further.
        fun play(config: BalanceConfig): List<Int> {
            val e = GameEngine(content, config.copy(commissionChancePerDay = 1.0))
            var s = e.newRun(LegacyProfile(), 11)
            val open = mutableListOf<Int>()
            repeat(25) {
                if (s.isEnded) return@repeat
                for (c in s.commissions.values.filter { it.status == CommissionStatus.OFFERED }) s = (e.handle(s, Command.AcceptCommission(c.id)) as CommandOutcome.Accepted).state
                val now = s.commissions.values.filter { it.status == CommissionStatus.ACCEPTED }
                open += now.size
                val named = now.flatMap { listOfNotNull(it.buyerId, it.recipientId) }
                assertEquals(named.toSet().size, named.size, "day ${s.day}: nobody is named on two requests")
                s = (e.handle(s, Command.EndDay(TestSupport.endDayId(s))) as CommandOutcome.Accepted).state
            }
            return open
        }
        val two = play(base)
        assertEquals(base.customers.maxOpenCommissions, two.max(), "$two")
        assertEquals(2, base.customers.maxOpenCommissions)
        assertTrue(two.count { it == 2 } >= 5, "two at once is ordinary once requests are left open: $two")
        assertEquals(1, play(base.copy(customers = base.customers.copy(maxOpenCommissions = 1))).max(), "the fallback: one at a time, as before")
    }

    @Test
    fun aFirstBladeGoesToTheHeroItWasOrderedFor() {
        val (patron, other) = heroes
        val guilded = town.copy(heroes = town.heroes + (patron.id to patron.copy(guildId = "g1")), town = town.town.copy(guilds = listOf(Guild("g1", "the Test Company", patron.id, 1))))
            .armed(patron, blade, "w91")
        val c = assertNotNull(offered(guilded, only(CommissionKind.FIRST_BLADE)).second)
        val newcomer = guilded.hero(c.recipientId!!)
        assertEquals(CommissionKind.FIRST_BLADE to patron.id, c.kind to c.buyerId)
        assertTrue(newcomer.id != patron.id && newcomer.shopPurchases == 0 && guilded.equippedWeapon(newcomer.id) == null)
        assertEquals(other.id, newcomer.id, "nobody else is of the patron's guild, so the earliest arrival")
        assertTrue(c.familyId in content.heroClass(newcomer.classId).preferredFamilies, "a blade for the newcomer's class, not the patron's")
        assertEquals("A first weapon for ${newcomer.fullName}. ${patron.fullName} pays for the order.", Lines.commissionWhy(c, guilded))
        assertNull(offered(town, only(CommissionKind.FIRST_BLADE)).second, "no guild member, no such request")

        val answer = blade.copy(id = WeaponId("w95"), familyId = c.familyId, quality = QualityBand.FINE.floor(base), location = WeaponLocation.Storage)
        val ready = guilded.copy(weapons = guilded.weapons + (answer.id to answer), commissions = mapOf(c.id to c.copy(status = CommissionStatus.ACCEPTED)))
        val ctx = ResolutionContext(ready, content, base)
        Market.resolveCommissions(ctx)
        assertEquals(WeaponLocation.Owned(newcomer.id, true), ctx.weapon(answer.id).location, "the newcomer carries it")
        assertEquals(ready.gold + c.reward, ctx.gold)
        assertEquals(patron.loyalty + 2, ctx.hero(patron.id).loyalty, "the patron paid and is the one who thinks better of the shop")
        assertEquals(patron.id, ctx.visits.single().heroId)
        assertTrue(ctx.newEvents.single { it.type == EventType.COMMISSION_COMPLETED }.let { "for ${newcomer.fullName}" in it.text && it.data["kind"] == "FIRST_BLADE" })
        assertEquals(listOf(newcomer.id.value), ctx.weapon(answer.id).history.last { it.kind == "COMMISSION" }.subjectIds)
        // Should the newcomer be gone by then, the patron keeps the blade.
        val gone = ResolutionContext(ready.copy(heroes = ready.heroes + (newcomer.id to newcomer.copy(fate = HeroFate.DEAD, diedOnDay = 1))), content, base)
        Market.resolveCommissions(gone)
        assertEquals(patron.id, gone.weapon(answer.id).ownerId)
        // A collector's request asks for the floor the ambition needs.
        val collector = town.copy(heroes = town.heroes + (patron.id to patron.copy(ambition = Ambition.COLLECTOR)))
        for (seed in 1L..10L) assertEquals(CommissionKind.AMBITION to QualityBand.FINE.floor(base), offered(collector.copy(rng = engine.newRun(LegacyProfile(), seed).rng), only(CommissionKind.AMBITION)).second!!.let { it.kind to it.minQuality })
    }
}
