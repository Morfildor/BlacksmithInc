package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.ui.detail.Stat
import com.example.blacksmithproject.ui.detail.salvageLabel
import com.example.blacksmithproject.ui.detail.weaponStats
import com.example.blacksmithproject.ui.shopday.Beat
import com.example.blacksmithproject.ui.shopday.FaceUi
import com.example.blacksmithproject.ui.shopday.toUi
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.ToolEffect
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.legacy.Legacy
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.DayResolution
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.IdOrder
import com.tinyblacksmith.core.model.LivingRevision
import com.tinyblacksmith.core.model.MarketVisit
import com.tinyblacksmith.core.model.VisitKind
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponFamilyId
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponSnapshot
import com.tinyblacksmith.core.shopday.Advice
import com.tinyblacksmith.core.shopday.Demand
import com.tinyblacksmith.core.shopday.LeadKind
import com.tinyblacksmith.core.shopday.Lines
import com.tinyblacksmith.core.shopday.ShopDay
import com.tinyblacksmith.core.shopday.TallyGroup
import com.tinyblacksmith.core.shopday.TallyOutcome
import com.tinyblacksmith.core.shopday.ThreatMark
import com.tinyblacksmith.core.shopday.Threats

/**
 * The one thing worth doing first, in the words of `Lines.lead`; [weaponId] is the blade it points at and [familyId] the
 * family a want asks for, if any. [commissionId] and [heroId] are the request and the hero it is about (`Advice.Lead`),
 * so "Go to the forge" and "Forge this" keep who the work is for.
 */
@Immutable data class LeadUi(
    val kind: LeadKind, val action: String, val reason: String?, val weaponId: WeaponId? = null, val familyId: WeaponFamilyId? = null,
    val commissionId: CommissionId? = null, val heroId: HeroId? = null,
)

/** A hero's standing want in the words of `Lines.want`. [answered]: a blade on the shelf today is what they left without (`DemandSummary.wantsAnswered`). */
@Immutable
data class WantUi(
    val heroId: HeroId, val line: String, val answered: Boolean, val familyId: WeaponFamilyId,
    /** The weapon type's name and the want's own numbers (`Hero.want`), for the board's groups. */
    val family: String = "", val minPower: Int = 0, val budget: Int = 0,
)

/**
 * An open request: who asks, for what, on what terms, and (once accepted) which blade End Day will hand over or what is
 * missing. [why] is the reason it was made (`Lines.commissionWhy`); null for an ordinary one.
 */
@Immutable
data class RequestUi(
    val id: CommissionId, val buyer: FaceUi, val asks: String, val terms: String, val offered: Boolean, val readiness: String?, val fits: List<String>, val why: String? = null,
    /** The same terms as facts, for a compact row and the Forge's brief: never read back out of [asks] or [terms]. */
    val reward: Int = 0, val daysLeft: Int = 0, val family: String = "", val element: Element? = null, val minQuality: Int = 0,
    /** Accepted, and a blade in the shop is the one End Day would hand over today (`Commissions.pick`). Nothing is set aside. */
    val ready: Boolean = false,
) {
    /** Where it stands, in one of four words. "Ready" is as the shop stands now: a sale or a stock change can undo it. */
    val status: String get() = when { offered -> "Offer"; ready -> "Ready for End Day"; daysLeft <= 0 -> "Due today"; else -> "Accepted" }
    /** "Due in 3 days"; the day itself stays in [terms]. */
    val due: String get() = when { daysLeft <= 0 -> "Due today"; daysLeft == 1 -> "Due tomorrow"; else -> "Due in $daysLeft days" }
}

/** How an element stands against the besieger, in the words of `Lines.threatMark`. [counters]: it bites (`ThreatMark.COUNTERS`); otherwise the besieger resists it. */
@Immutable data class MarkUi(val label: String, val counters: Boolean)

/**
 * The besieger as `Threats.of` has it: when the siege comes, `Lines.threat` in words (null when the faction cares about
 * no element), whether today's customers weigh it, and the mark of each element it is weak to or resists.
 */
@Immutable
data class ThreatUi(
    val siege: String, val line: String?, val warned: Boolean, val marks: Map<Element, MarkUi>,
    /** The besieger and its matchup as plain facts: "Ashclaw Raiders · Weak to Frost · Resists Fire". */
    val matchup: String? = null,
    /** How the siege looks as things stand (`Labels.outlook`); null without a forecast. */
    val outlook: String? = null,
    val today: Boolean = false,
    /** Inside the warning window (`combat.siegeWarningDays`): the siege is days away no more. */
    val near: Boolean = false,
    /** The first morning of that window, the one day the Shop says what the warning changes. */
    val warningFresh: Boolean = false,
    /** The besieger's name alone, for the workshop header (said there only once [warned]). */
    val besieger: String? = null,
    /** A living era: the authored army committed for this siege (`SiegeScenario.armyId`), for the Shop's siege-day block; null before it is committed and in an older era. */
    val army: String? = null,
) {
    /** When, who and what tells against them, and how the town stands: the one siege line every destination shows. */
    val summary: String get() = listOfNotNull(if (today) "$siege, after today's trading" else siege, matchup, outlook).joinToString(" · ")
    /** Siege and matchup on one line, for a plate. */
    val plate: String get() = listOfNotNull(siege, line).joinToString(" · ")
    /** Said on the Shop on the first warned day when the besieger cares about an element; the matchup line says the rest after it. */
    val note: String? get() = "The siege warning is out. Buyers now consider the attackers' strengths and weaknesses.".takeIf { warned && warningFresh && marks.isNotEmpty() }
    /** How loudly the strip speaks of the siege: one quiet line while it is days away, ember once it is near, a row of its own on the day. */
    val urgency: SiegeUrgency get() = when { today -> SiegeUrgency.TODAY; near -> SiegeUrgency.NEAR; else -> SiegeUrgency.FAR }
}

/** The siege's weight on the Shop and the Forge, in proportion to how close it is. */
enum class SiegeUrgency { FAR, NEAR, TODAY }

/** The sign and colour of a mark: what bites the besieger helps ("+"), what it resists hurts ("−"). */
val MarkUi.kind: EffectKind get() = if (counters) EffectKind.BUFF else EffectKind.FLAW

/**
 * A blade on the shelf ([price] set) or in storage ([price] null, [suggested] is what the quick "List" asks). [stats] are
 * the item card's own numbers (`weaponStats`), less a renown of nothing; [buffs] and [flaws] are the affix names.
 */
@Immutable
data class StockUi(
    val weapon: Weapon, val summary: String, val price: Int?, val suggested: Int,
    val stats: List<Stat> = emptyList(), val buffs: List<String> = emptyList(), val flaws: List<String> = emptyList(),
    /** How the blade's element stands against the besieger; null for a plain blade or an element the besieger does not care about. */
    val threat: MarkUi? = null,
    /** A returned legend's sleeping affixes (`Lines.dormant`); never among [buffs]. */
    val dormant: String? = null,
    /** The family's name, for Storage's filter. */
    val family: String = "",
    /** No hero has carried it this era and it is not a returned legend: forged here and never sold, ordered or handed back. */
    val unsold: Boolean = false,
    /** "Kept for X's order" for a blade the engine holds back for an open order (it cannot be listed, salvaged, scrapped or given away); null otherwise. */
    val promised: String? = null,
    /** The weapon's inspect card (`stockTip`), without actions: the row adds "Full details". Null where none was built. */
    val tip: TipUi? = null,
)

/**
 * A blade that came back to the smith at the last End Day or since (traded in, recovered from a fallen hero or by the
 * party, handed back from a loan, a legend come home) and is in storage. [how] is its own history line, [day] that
 * line's day (what a "Keep" on the Shop is remembered by), [salvage] the blade sheet's salvage label.
 */
@Immutable data class ReturnedUi(val stock: StockUi, val how: String, val day: Int, val salvage: String)

/** The history kinds a blade comes back to the forge by. */
private val RETURN_KINDS = setOf("TRADED_IN", "RECOVERED", "LOAN_RETURNED", "RETURNED")

/** The line a blade came back by, from the last End Day on: read from the newest end of its history only. */
private fun returnedBy(w: Weapon, state: GameState) =
    w.history.asReversed().asSequence().takeWhile { it.era == state.era && it.day >= state.day - 1 }.firstOrNull { it.kind in RETURN_KINDS }

/** "Kept for Wren Kestrel's order": a blade bound to an open order (`Weapon.promisedTo`); null for any other blade. */
fun promisedLine(state: GameState, w: Weapon): String? = w.promisedTo?.let { "Reserved for ${GameViewModel.promisedBuyer(state, it) ?: "a customer"}'s commission" }

/** One line of "Who is buying": a count from `Demand.summary` under a fixed label, with the names when they are few. */
@Immutable data class DemandRow(val label: String, val value: String, val detail: String? = null)

/** Yesterday's counter in a few lines: the till, the counts, then one line per outcome with the names it covers. */
@Immutable data class YesterdayUi(val day: Int, val took: String?, val counts: String?, val lines: List<String>)

/**
 * Everything the Shop destination shows, in the order it shows it. Built from the saved state alone; every sentence is
 * a `core/shopday` line or a recorded number under a fixed label.
 */
@Immutable
data class ShopUi(
    val seats: Int, val slots: Int,
    val lead: LeadUi,
    val requests: List<RequestUi>,
    val demand: List<DemandRow>,
    val yesterday: YesterdayUi?,
    val shelf: List<StockUi>,
    val storage: List<StockUi>,
    val wants: List<WantUi> = emptyList(),
    val threat: ThreatUi? = null,
    /** How many requests may be open at once (`customers.maxOpenCommissions`). */
    val requestSlots: Int = 1,
    /** What each living hero can pay at the counter today (`Demand.funds`): the count under a price being chosen. */
    val funds: List<Int> = emptyList(),
    /** The stored blades that came back since yesterday, in storage's order: the Shop offers to list, salvage or keep each. */
    val returned: List<ReturnedUi> = emptyList(),
    /** A competing siege action stays beside accepted work due today. */
    val siegeLead: LeadUi? = null,
) {
    /** The counter's plate: the shelf first, then how many customers the shop serves in a day. */
    val plate: String get() = "Shelf ${shelf.size} of $slots · $seats customers a day"
}

/**
 * The request a fresh blade was forged for, as the forge result shows it. [fit] is about that blade alone
 * (`Commissions.fit`); [handover] is what End Day will do for the request as the shop stands (`Labels.readiness`), which
 * may name another blade: the engine hands over the least sufficient blade that fits and sets none aside.
 */
@Immutable data class ForgedForUi(
    val id: CommissionId,
    val title: String, val terms: String, val fits: Boolean, val fit: String, val accepted: Boolean, val handover: String,
    /** Whether this blade is reserved, and for whose commission (`Weapon.promisedTo`); the engine reserves none for an ordinary one. */
    val reserved: String = NOT_RESERVED,
    /** Accepted, and this is the blade End Day would hand over as the shop stands (`Commissions.pick`). */
    val handsOver: Boolean = false,
)

internal const val NOT_RESERVED = "Not reserved. At End Day the buyer takes a matching weapon from storage first, then the shelf."

/** Null without a request, for one that is no longer open, or for a blade that has left the save. Pure: reads the save. */
fun GameEngine.forgedFor(state: GameState, weaponId: WeaponId, commissionId: CommissionId?): ForgedForUi? {
    val c = state.commissions[commissionId ?: return null]?.takeIf { it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED } ?: return null
    val w = state.weapons[weaponId] ?: return null
    val fits = Commissions.fit(w, c) == Commissions.Fit.OK
    val accepted = c.status == CommissionStatus.ACCEPTED
    return ForgedForUi(
        id = c.id,
        title = "For ${state.heroes[c.buyerId]?.fullName ?: "Someone"}: ${Labels.request(c, content, config)}",
        terms = "${c.reward} gold · ${dueWords(c.deadlineDay - state.day, c.deadlineDay)}",
        fits = fits,
        fit = if (fits) "This weapon meets the commission" else "This weapon doesn't meet the commission. ${Labels.fit(w, c, content).replaceFirstChar { it.uppercase() }}.",
        accepted = accepted,
        handover = if (accepted) Labels.readiness(c, state.weapons.values, content, config) else "Accept this commission in the Shop before it can be collected.",
        reserved = promisedLine(state, w)?.let { "$it." } ?: NOT_RESERVED,
        handsOver = accepted && Commissions.pick(state.weapons.values, c, config)?.id == w.id,
    )
}

/** Names are listed while they fit on a line or two; a longer list is only its count. */
private const val NAMES_SHOWN = 4

private fun dueWords(left: Int, deadline: Int) = when { left <= 0 -> "due today"; left == 1 -> "due tomorrow"; else -> "due day $deadline, in $left days" }

/** The hero classes that favour this family, plural, in catalog order. */
internal fun GameEngine.fans(familyId: WeaponFamilyId): List<String> = content.classes.filter { familyId in it.preferredFamilies }.map { it.name + "s" }

private fun GameEngine.stock(state: GameState, w: Weapon, threat: ThreatUi?, promised: String? = null) = StockUi(
    w, Labels.weaponSummary(w, content), w.listedPrice, suggestedPrice(w),
    // A number with a ceiling is always said; one without (power, renown) only when there is any.
    stats = weaponStats(WeaponSnapshot.of(w)).filter { it.max != null || it.value > 0 },
    buffs = w.affixes.map { content.affix(it).name }, flaws = w.flaws.map { content.affix(it).name },
    threat = w.element?.let { threat?.marks?.get(it) },
    dormant = Lines.dormant(w.dormantAffixes, content),
    family = content.family(w.familyId).name,
    unsold = w.legendKey == null && Legacy.holders(w, state.era).isEmpty(),
    promised = promised,
).let { it.copy(tip = stockTip(state, it)) }

/** The besieger for the Shop's plate, the Forge's plate, the augment chips and the stock rows: `Threats` and `Lines`, plus the day count in words. */
fun GameEngine.threatUi(state: GameState): ThreatUi? = Threats.of(state, content, config)?.let { t ->
    ThreatUi(
        siege = when { t.daysToSiege <= 0 -> "Siege today"; t.daysToSiege == 1 -> "Siege tomorrow"; else -> "Siege in ${t.daysToSiege} days" },
        line = Lines.threat(t, content),
        warned = t.warned, today = t.daysToSiege <= 0,
        near = t.daysToSiege in 0 until config.combat.siegeWarningDays,
        warningFresh = t.daysToSiege == config.combat.siegeWarningDays - 1,
        marks = Element.entries.mapNotNull { e -> Threats.mark(e, t)?.let { e to MarkUi(Lines.threatMark(it, t, content), it == ThreatMark.COUNTERS) } }.toMap(),
        matchup = content.factionById[t.factionId]?.name?.let { name -> listOfNotNull(name, t.weakTo?.let { "Weak to ${it.word()}" }, t.resists?.let { "Resists ${it.word()}" }).joinToString(" · ") },
        besieger = content.factionById[t.factionId]?.name,
        army = state.siege?.takeIf { state.livingRevision >= LivingRevision.CURRENT && it.siegeDay == state.town.nextSiegeDay }?.armyId?.let { content.guild?.army(it)?.name },
    )
}

/** Yesterday's visits regrouped by outcome and reason, each sale on its own, and worded by `Lines.tally`. */
private fun GameEngine.yesterday(state: GameState, day: DayResolution): YesterdayUi {
    val script = ShopDay.script(day, state, content, config)
    val close = script.toUi(state, content, config).beats.firstNotNullOfOrNull { it as? Beat.Close }
    fun outcome(v: MarketVisit) = when {
        v.kind == VisitKind.COMMISSION -> TallyOutcome.COMMISSION
        v.kind == VisitKind.COLLECTOR -> TallyOutcome.COLLECTOR
        v.purchasedWeaponId != null -> TallyOutcome.BOUGHT
        else -> TallyOutcome.LEFT
    }
    val groups = script.visits.groupBy { v -> outcome(v).let { o -> Triple(o, v.reason.takeIf { o == TallyOutcome.LEFT }, v.seq.takeIf { o == TallyOutcome.BOUGHT }) } }
    val lines = script.quiet?.let { listOf(Lines.quiet(it)) } ?: groups.map { (key, visits) ->
        val names = visits.map { it.heroName }.distinct()
        Lines.tally(TallyGroup(key.first, key.second, visits), script).replaceFirstChar { it.uppercase() } + if (names.size <= NAMES_SHOWN) ": ${names.joinToString()}" else ""
    }
    return YesterdayUi(day.day, day.ledger?.let { "Earned ${it.goldAtClose - it.goldAtOpen} gold" }, close?.counts, lines)
}

/** The Shop destination's content for [state]. Pure: reads the save, draws nothing, changes nothing. [forecast] is the siege forecast when it is already at hand. */
fun GameEngine.shopUi(state: GameState, forecast: Battle.SiegeOutlook? = null): ShopUi {
    val lead = Advice.lead(state, content, config)
    val line = Lines.lead(lead, state, content, config)
    val festival = state.worldFlags[WorldEvents.FLAG_FESTIVAL] == state.day
    val inShop = state.storedWeapons() + state.listedWeapons()
    val threat = threatUi(state)?.let { t -> t.copy(outlook = forecast?.let { Labels.outlook(it.odds) }) }

    val requests = state.commissions.values.filter { it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED }
        .sortedWith(compareBy<Commission> { it.deadlineDay }.thenBy(IdOrder.numeric) { it.id.value })
        .map { c ->
            val buyer = state.heroes[c.buyerId]
            RequestUi(
                id = c.id, buyer = FaceUi(buyer?.id, buyer?.fullName ?: "Someone", hero = buyer),
                asks = Labels.request(c, content, config),
                terms = "${c.reward} gold · ${dueWords(c.deadlineDay - state.day, c.deadlineDay)}",
                offered = c.status == CommissionStatus.OFFERED,
                readiness = Labels.readiness(c, state.weapons.values, content, config).takeIf { c.status == CommissionStatus.ACCEPTED },
                // Each blade of the family in the shop, by the engine's own rule: "fits" or the one thing it lacks.
                fits = inShop.filter { it.familyId == c.familyId }.map { w -> "${w.name}: ${Labels.fit(w, c, content)}" },
                why = Lines.commissionWhy(c, state),
                reward = c.reward, daysLeft = c.deadlineDay - state.day, family = content.family(c.familyId).name, element = c.element, minQuality = c.minQuality,
                ready = c.status == CommissionStatus.ACCEPTED && Commissions.pick(state.weapons.values, c, config) != null,
            )
        }

    val d = Demand.summary(state, content, config)
    fun names(ids: List<HeroId>) = ids.takeIf { it.size in 1..NAMES_SHOWN }?.mapNotNull { state.heroes[it]?.fullName }?.joinToString()
    val demand = listOfNotNull(
        DemandRow("Heroes in town", "${d.living}"),
        DemandRow("Need a weapon", "${d.unarmed.size}", names(d.unarmed)),
        DemandRow("Need to replace a worn weapon", "${d.worn.size}", names(d.worn)),
        d.cheapestPrice?.let { DemandRow("Can afford the cheapest weapon ($it gold)", "${d.canAffordCheapest} of ${d.living}") },
        d.medianPrice?.takeIf { it != d.cheapestPrice }?.let { DemandRow("Can afford the middle-priced weapon ($it gold)", "${d.canAffordMedian} of ${d.living}") },
        // Only against a stocked shelf: with nothing listed every class is unserved, and the lead already says so.
        d.unservedClasses.takeIf { it.isNotEmpty() && d.cheapestPrice != null }?.let { ids -> DemandRow("No suitable weapon for", ids.joinToString { content.heroClass(it).name + "s" }) },
    )

    val storage = state.storedWeapons().map { stock(state, it, threat, promisedLine(state, it)) }

    return ShopUi(
        seats =config.customers.shopCapacity + toolTotal(state, ToolEffect.EXTRA_CUSTOMERS) + if (festival) config.customers.festivalExtraSeats else 0,
        slots = shelfSlots(state),
        lead = LeadUi(lead.kind, line.action, line.reason, lead.weaponId, lead.familyId, lead.commissionId, lead.heroId),
        siegeLead = Advice.siegeReadiness(state, content, config)?.takeIf {
            lead.kind == LeadKind.FORGE_FOR_REQUEST && lead.days == 0
        }?.let { gap -> Lines.lead(gap, state, content, config).let { LeadUi(gap.kind, it.action, it.reason) } },
        requests = requests,
        demand = demand,
        // A day that cannot be laid out is left out here; the Gazette still has it.
        yesterday = state.lastResolution?.takeIf { it.day == state.day - 1 }?.let { runCatching { yesterday(state, it) }.getOrNull() },
        shelf = state.listedWeapons().map { stock(state, it, threat) },
        storage = storage,
        returned = storage.mapNotNull { s -> returnedBy(s.weapon, state)?.let { e -> ReturnedUi(s, e.text, e.day, salvageLabel(state, s.weapon)) } },
        threat = threat,
        requestSlots = config.customers.maxOpenCommissions,
        funds = Demand.funds(state, content, config),
        wants = d.wants.mapNotNull { id -> state.heroes[id]?.let { h -> Lines.want(h, content)?.let { WantUi(id, it, id in d.wantsAnswered, h.want!!.familyId, content.family(h.want!!.familyId).name, h.want!!.minPower, h.want!!.budget) } } },
    )
}
