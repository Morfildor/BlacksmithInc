package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDay
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.forgeAccepted
import com.tinyblacksmith.core.TestSupport.quickSword
import com.tinyblacksmith.core.TestSupport.run
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.shopday.Advice
import com.tinyblacksmith.core.shopday.Demand
import com.tinyblacksmith.core.shopday.Lead
import com.tinyblacksmith.core.shopday.LeadKind
import com.tinyblacksmith.core.shopday.LeadLine
import com.tinyblacksmith.core.shopday.Lines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** U02: one lead and its reason, the first match of a fixed order, from the state alone. */
class AdviceTest {
    private val config = engine.config
    private val content = engine.content

    private fun lead(s: GameState) = Advice.lead(s, content, config)
    private fun line(s: GameState) = Lines.lead(lead(s), s, content, config)

    private val fresh: GameState = engine.newRun(LegacyProfile(), 7)
    /** One sword in storage. */
    private val forged: GameState = fresh.withMaterials().forgeAccepted(quickSword(Risk.SAFE)).state
    private val sword: Weapon = forged.weapons.values.single()
    /** The sword on the shelf at 60 gold, the first siege four days off, nothing asked: none of the urgent leads holds. */
    private val stocked: GameState = forged.run(Command.ToggleShelf(sword.id, listed = true, price = 60))
    private val buyer: Hero = fresh.aliveHeroes().first()

    private fun GameState.asked(status: CommissionStatus, family: WeaponFamilyId, dueIn: Int): GameState =
        copy(commissions = mapOf(CommissionId("c900") to Commission(CommissionId("c900"), buyer.id, family, config.uncommonMin, 140, day, day + dueIn, status)))

    private fun GameState.refusedYesterday(vararg reasons: VisitReason): GameState = copy(
        day = 2,
        lastResolution = DayResolution(CommandId("x:day1"), 1, emptyList(), emptyList(), reasons.mapIndexed { i, r -> MarketVisit(HeroId("h${i + 1}"), "Hero $i", null, r, seq = i) }, emptyList(), false),
    )

    private fun GameState.besiegedIn(days: Int, pressure: Int = 100, militia: Int = 0): GameState =
        copy(town = town.copy(nextSiegeDay = day + days, militia = militia), factions = factions.mapValues { it.value.copy(pressure = pressure) })

    @Test
    fun firstBlade() {
        assertEquals(Lead(LeadKind.FIRST_BLADE, count = fresh.aliveHeroes().size), lead(fresh))
        assertEquals(LeadLine("Forge your first blade", "${fresh.aliveHeroes().size} heroes in Emberfall and nothing on the shelf."), line(fresh))
        assertNotEquals(LeadKind.FIRST_BLADE, lead(fresh.copy(day = 2)).kind, "day 1 only")
        assertNotEquals(LeadKind.FIRST_BLADE, lead(forged).kind, "and only with nothing forged")
    }

    @Test
    fun chooseBlessing() {
        val offer = content.blessings.take(2).map { it.id }
        val s = stocked.copy(pendingBlessingOffer = offer)
        assertEquals(Lead(LeadKind.CHOOSE_BLESSING, count = 2), lead(s))
        assertEquals("Choose a blessing", line(s).action)
    }

    @Test
    fun answerRequest() {
        val s = stocked.asked(CommissionStatus.OFFERED, LaunchContent.SWORD, dueIn = 1)
        assertEquals(Lead(LeadKind.ANSWER_REQUEST, heroId = buyer.id, commissionId = CommissionId("c900"), gold = 140, days = 1), lead(s))
        assertEquals(LeadLine("Answer ${buyer.fullName}'s request", "A decent Sword (quality ${config.uncommonMin}+); 140 gold; the offer lapses tomorrow."), line(s))
        assertEquals("the offer lapses today.", line(stocked.asked(CommissionStatus.OFFERED, LaunchContent.SWORD, dueIn = 0)).reason!!.substringAfterLast("; "))
        assertNotEquals(LeadKind.ANSWER_REQUEST, lead(stocked.asked(CommissionStatus.OFFERED, LaunchContent.SWORD, dueIn = 2)).kind, "not yet urgent")
        assertNotEquals(LeadKind.ANSWER_REQUEST, lead(stocked.asked(CommissionStatus.DECLINED, LaunchContent.SWORD, dueIn = 1)).kind)
        val gone = s.copy(heroes = s.heroes + (buyer.id to buyer.copy(fate = HeroFate.DEAD)))
        assertNotEquals(LeadKind.ANSWER_REQUEST, lead(gone).kind, "a request whose patron is gone is no lead")
    }

    @Test
    fun forgeForRequest() {
        val s = stocked.asked(CommissionStatus.ACCEPTED, LaunchContent.BOW, dueIn = 2)
        assertEquals(Lead(LeadKind.FORGE_FOR_REQUEST, heroId = buyer.id, commissionId = CommissionId("c900"), gold = 140, days = 2), lead(s))
        assertEquals(LeadLine("Forge for ${buyer.fullName}'s request", "Nothing in the shop is a decent Bow (quality ${config.uncommonMin}+); due in 2 days."), line(s))
        assertNotEquals(LeadKind.FORGE_FOR_REQUEST, lead(stocked.asked(CommissionStatus.ACCEPTED, LaunchContent.BOW, dueIn = 3)).kind, "three days left: not yet")
        // The same rule End Day uses: a blade that fits means nothing is missing.
        val fits = stocked.copy(weapons = stocked.weapons.mapValues { it.value.copy(quality = 90) }).asked(CommissionStatus.ACCEPTED, LaunchContent.SWORD, dueIn = 1)
        assertNotEquals(LeadKind.FORGE_FOR_REQUEST, lead(fits).kind)
        val lacks = stocked.copy(weapons = stocked.weapons.mapValues { it.value.copy(quality = 1) }).asked(CommissionStatus.ACCEPTED, LaunchContent.SWORD, dueIn = 1)
        assertEquals(LeadKind.FORGE_FOR_REQUEST, lead(lacks).kind)
    }

    @Test
    fun listStock() {
        assertEquals(Lead(LeadKind.LIST_STOCK, count = 1), lead(forged))
        assertEquals(LeadLine("Put a blade on the shelf", "The shelf is empty; 1 blade in storage."), line(forged))
    }

    @Test
    fun forgeStock() {
        val s = fresh.copy(day = 2)
        assertEquals(Lead(LeadKind.FORGE_STOCK), lead(s))
        assertEquals(LeadLine("Forge something to sell", "The shelf and the storeroom are empty."), line(s))
    }

    @Test
    fun pricesTooHigh() {
        val s = stocked.refusedYesterday(VisitReason.TOO_EXPENSIVE, VisitReason.NOT_BETTER, VisitReason.OVERPRICED)
        assertEquals(Lead(LeadKind.PRICES_TOO_HIGH, weaponId = sword.id, gold = 60, count = 2), lead(s))
        assertEquals(LeadLine("Lower a price", "2 customers left over the price yesterday; the cheapest blade is 60 gold."), line(s))
        assertNotEquals(LeadKind.PRICES_TOO_HIGH, lead(stocked.refusedYesterday(VisitReason.TOO_EXPENSIVE, VisitReason.NOT_BETTER)).kind, "one is not a pattern")
        assertNotEquals(LeadKind.PRICES_TOO_HIGH, lead(s.copy(day = 3)).kind, "yesterday only")
    }

    @Test
    fun armDefenders() {
        val s = stocked.besiegedIn(2)
        val forecast = engine.siegeForecast(s)!!
        assertTrue(forecast.odds == Battle.SiegeOdds.OUTMATCHED || forecast.odds == Battle.SiegeOdds.DIRE, "the scenario: ${forecast.odds}")
        assertEquals(Lead(LeadKind.ARM_DEFENDERS, factionId = forecast.faction.id, days = 2, element = forecast.faction.weakTo), lead(s))
        val weak = forecast.faction.weakTo?.let { ", weak to ${it.name.lowercase()}" }.orEmpty()
        assertEquals(LeadLine("Arm the defenders", "${forecast.faction.name} in 2 days$weak."), line(s))
        assertNotEquals(LeadKind.ARM_DEFENDERS, lead(stocked.besiegedIn(3)).kind, "three days off: not yet")
        val safe = stocked.besiegedIn(2, pressure = 0, militia = 100_000)
        assertEquals(Battle.SiegeOdds.STRONG, engine.siegeForecast(safe)!!.odds)
        assertNotEquals(LeadKind.ARM_DEFENDERS, lead(safe).kind, "the odds are with the town")
    }

    /** Every lead a run meets has its words; a want is led only for a living hero who has one that nothing listed answers. */
    @Test
    fun everyLeadOfARunHasItsWordsAndAWantIsLedOnlyWhenOneStands() {
        var leads = 0
        val kinds = mutableSetOf<LeadKind>()
        for (seed in 1L..10L) {
            var s = engine.newRun(LegacyProfile(), seed)
            repeat(20) {
                if (s.isEnded) return@repeat
                // The morning as found, then with a sword forged, then with everything listed.
                val forged = s.withMaterials().forgeAccepted(quickSword()).state
                val listed = forged.storedWeapons().fold(forged) { acc, w -> (engine.handle(acc, Command.ToggleShelf(w.id, listed = true)) as? CommandOutcome.Accepted)?.state ?: acc }
                for (state in listOf(s, forged, listed)) {
                    val l = lead(state)
                    leads++; kinds += l.kind
                    if (l.kind == LeadKind.ANSWER_WANT) {
                        val demand = Demand.summary(state, content, config)
                        assertTrue(l.heroId in demand.wants && l.heroId !in demand.wantsAnswered && state.hero(l.heroId!!).want?.familyId == l.familyId, "$l")
                    }
                    val text = Lines.lead(l, state, content, config)
                    assertTrue(text.action.isNotBlank() && text.reason?.isBlank() != true, "$l")
                    assertFalse("%" in text.action + text.reason, "$text")
                    assertEquals(l, lead(state), "the same state gives the same lead")
                }
                s = listed.endDay()
            }
        }
        assertTrue(leads > 300 && kinds.size >= 6, "leads=$leads kinds=$kinds")
        assertTrue(LeadKind.ANSWER_WANT in kinds, "wants are voiced in ordinary play: $kinds")
        assertEquals(LeadLine("Forge a blade", null), Lines.lead(Lead(LeadKind.ANSWER_WANT), fresh, content, config))
    }

    @Test
    fun forgeForBuyers() {
        // Nobody carries a blade on day 1: that is the demand fact.
        val demand = Demand.summary(stocked, content, config)
        assertEquals(Lead(LeadKind.FORGE_FOR_BUYERS, count = demand.unarmed.size), lead(stocked))
        assertEquals(LeadLine("Forge for today's buyers", "${demand.unarmed.size} heroes carry no blade."), line(stocked))

        // Everyone armed, one blade worn: the worn blade is named.
        fun armed(condition: (Hero) -> Int) = stocked.copy(weapons = stocked.weapons + stocked.aliveHeroes().associate { h ->
            WeaponId("own-${h.id.value}") to sword.copy(id = WeaponId("own-${h.id.value}"), name = "Old Sword", location = WeaponLocation.Owned(h.id, equipped = true), condition = condition(h))
        })
        val worn = armed { if (it.id == buyer.id) config.wornConditionThreshold - 1 else 100 }
        assertEquals(Lead(LeadKind.FORGE_FOR_BUYERS, heroId = buyer.id, weaponId = WeaponId("own-${buyer.id.value}")), lead(worn))
        assertEquals(LeadLine("Forge for today's buyers", "${buyer.fullName}'s Old Sword is worn."), line(worn))

        // Everyone armed and keen: how many can afford the cheapest blade.
        val keen = armed { 100 }
        val d = Demand.summary(keen, content, config)
        assertEquals(Lead(LeadKind.FORGE_FOR_BUYERS, gold = 60, count = d.canAffordCheapest), lead(keen))
        assertEquals("${d.canAffordCheapest} hero${if (d.canAffordCheapest == 1) "" else "es"} can afford the cheapest blade (60 gold).", line(keen).reason)
    }

    @Test
    fun firstMatchWins() {
        // Every condition at once, then one removed at a time: the lead walks down the fixed order.
        val offer = content.blessings.take(1).map { it.id }
        val offered = Commission(CommissionId("c1"), buyer.id, LaunchContent.SWORD, config.uncommonMin, 100, 1, 2, CommissionStatus.OFFERED)
        val accepted = Commission(CommissionId("c2"), buyer.id, LaunchContent.BOW, config.uncommonMin, 100, 1, 2, CommissionStatus.ACCEPTED)
        val all = fresh.besiegedIn(1).copy(pendingBlessingOffer = offer, commissions = mapOf(offered.id to offered, accepted.id to accepted))
        assertEquals(LeadKind.FIRST_BLADE, lead(all).kind)

        val later = all.copy(day = 2, town = all.town.copy(nextSiegeDay = 3), commissions = all.commissions.mapValues { it.value.copy(deadlineDay = 3) })
            .refusedYesterday(VisitReason.TOO_EXPENSIVE, VisitReason.TOO_EXPENSIVE)
        val expected = listOf(
            LeadKind.CHOOSE_BLESSING to later,
            LeadKind.ANSWER_REQUEST to later.copy(pendingBlessingOffer = emptyList()),
            LeadKind.FORGE_FOR_REQUEST to later.copy(pendingBlessingOffer = emptyList(), commissions = mapOf(accepted.id to accepted.copy(deadlineDay = 3))),
            LeadKind.FORGE_STOCK to later.copy(pendingBlessingOffer = emptyList(), commissions = emptyMap()),
        )
        for ((kind, state) in expected) assertEquals(kind, lead(state).kind)

        val withStock = later.copy(pendingBlessingOffer = emptyList(), commissions = emptyMap(), weapons = forged.weapons)
        assertEquals(LeadKind.LIST_STOCK, lead(withStock).kind)
        val listed = withStock.copy(weapons = stocked.weapons)
        assertEquals(LeadKind.PRICES_TOO_HIGH, lead(listed).kind)
        val noRefusals = listed.copy(lastResolution = null)
        assertEquals(LeadKind.ARM_DEFENDERS, lead(noRefusals).kind)
        assertEquals(LeadKind.FORGE_FOR_BUYERS, lead(noRefusals.copy(town = noRefusals.town.copy(nextSiegeDay = 9))).kind)
    }
}
