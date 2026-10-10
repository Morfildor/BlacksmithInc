package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.example.blacksmithproject.ui.detail.Stat
import com.example.blacksmithproject.ui.detail.weaponStats
import com.example.blacksmithproject.ui.shopday.Beat
import com.example.blacksmithproject.ui.shopday.FaceUi
import com.example.blacksmithproject.ui.shopday.toUi
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

/** The one thing worth doing first, in the words of `Lines.lead`; [weaponId] is the blade it points at and [familyId] the family a want asks for, if any. */
@Immutable data class LeadUi(val kind: LeadKind, val action: String, val reason: String?, val weaponId: WeaponId? = null, val familyId: WeaponFamilyId? = null)

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
    /** "Due in 3 days"; the day itself stays in [terms]. */
    /** Where it stands, in one of four words. "Ready" is as the shop stands now: a sale or a stock change can undo it. */
    val status: String get() = when { offered -> "Offer"; ready -> "Ready for End Day"; daysLeft <= 0 -> "Due today"; else -> "Accepted" }
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
) {
    /** Siege and matchup on one line, for a plate. */
    val plate: String get() = listOfNotNull(siege, line).joinToString(" · ")
    /** Said on the Shop on the days `Threat.warned` holds and the besieger cares about an element. */
    val note: String? get() = "The siege warning is out: buyers weigh this today.".takeIf { warned && marks.isNotEmpty() }
}

/** The sign and colour of a mark: what bites the besieger helps ("+"), what it resists hurts ("−"). */
val MarkUi.kind: EffectKind get() = if (counters) EffectKind.BUFF else EffectKind.FLAW

/**
 * A blade on the shelf ([price] set) or in storage ([price] null, [suggested] is what the quick "List" asks). [stats] are
 * the item card's own numbers (`weaponStats`), less a renown of nothing; [buffs] and [flaws] are the affix names.
 */
@Immutable
data class StockUi(
    val weapon: Weapon, val summary: String, val favoured: String?, val price: Int?, val suggested: Int,
    val stats: List<Stat> = emptyList(), val buffs: List<String> = emptyList(), val flaws: List<String> = emptyList(),
    /** How the blade's element stands against the besieger; null for a plain blade or an element the besieger does not care about. */
    val threat: MarkUi? = null,
    /** A returned legend's sleeping affixes (`Lines.dormant`); never among [buffs]. */
    val dormant: String? = null,
    /** The family's name, for Storage's filter. */
    val family: String = "",
    /** No hero has carried it this era and it is not a returned legend: forged here and never sold, ordered or handed back. */
    val unsold: Boolean = false,
)

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
)

/**
 * The request a fresh blade was forged for, as the forge result shows it. [fit] is about that blade alone
 * (`Commissions.fit`); [handover] is what End Day will do for the request as the shop stands (`Labels.readiness`), which
 * may name another blade: the engine hands over the least sufficient blade that fits and sets none aside.
 */
@Immutable data class ForgedForUi(val title: String, val terms: String, val fits: Boolean, val fit: String, val accepted: Boolean, val handover: String)

/** Null without a request, for one that is no longer open, or for a blade that has left the save. Pure: reads the save. */
fun GameEngine.forgedFor(state: GameState, weaponId: WeaponId, commissionId: CommissionId?): ForgedForUi? {
    val c = state.commissions[commissionId ?: return null]?.takeIf { it.status == CommissionStatus.OFFERED || it.status == CommissionStatus.ACCEPTED } ?: return null
    val w = state.weapons[weaponId] ?: return null
    val fits = Commissions.fit(w, c) == Commissions.Fit.OK
    val accepted = c.status == CommissionStatus.ACCEPTED
    return ForgedForUi(
        title = "For ${state.heroes[c.buyerId]?.fullName ?: "Someone"}: ${Labels.request(c, content, config)}",
        terms = "${c.reward} gold · ${dueWords(c.deadlineDay - state.day, c.deadlineDay)}",
        fits = fits,
        fit = if (fits) "This blade fits the request" else "This blade does not fit: ${Labels.fit(w, c, content)}",
        accepted = accepted,
        handover = if (accepted) Labels.readiness(c, state.weapons.values, content, config) else "Not accepted yet. Accept the request in the Shop, or nothing is handed over.",
    )
}

/** Names are listed while they fit on a line or two; a longer list is only its count. */
private const val NAMES_SHOWN = 4

private fun dueWords(left: Int, deadline: Int) = when { left <= 0 -> "due today"; left == 1 -> "due tomorrow"; else -> "due day $deadline, in $left days" }

/** Which hero classes favour this family: a content read, never a sale prediction. */
private fun GameEngine.favoured(familyId: WeaponFamilyId): String? {
    val fans = content.classes.filter { familyId in it.preferredFamilies }.map { it.name + "s" }
    return if (fans.isEmpty()) null else "${fans.joinToString(" and ")} favour the ${content.family(familyId).name.lowercase()}"
}

private fun GameEngine.stock(w: Weapon, threat: ThreatUi?, era: Int) = StockUi(
    w, Labels.weaponSummary(w, content), favoured(w.familyId), w.listedPrice, suggestedPrice(w),
    // A number with a ceiling is always said; one without (power, renown) only when there is any.
    stats = weaponStats(WeaponSnapshot.of(w)).filter { it.max != null || it.value > 0 },
    buffs = w.affixes.map { content.affix(it).name }, flaws = w.flaws.map { content.affix(it).name },
    threat = w.element?.let { threat?.marks?.get(it) },
    dormant = Lines.dormant(w.dormantAffixes, content),
    family = content.family(w.familyId).name,
    unsold = w.legendKey == null && Legacy.holders(w, era).isEmpty(),
)

/** The besieger for the Shop's plate, the Forge's plate, the augment chips and the stock rows: `Threats` and `Lines`, plus the day count in words. */
fun GameEngine.threatUi(state: GameState): ThreatUi? = Threats.of(state, content, config)?.let { t ->
    ThreatUi(
        siege = when { t.daysToSiege <= 0 -> "Siege today"; t.daysToSiege == 1 -> "Siege tomorrow"; else -> "Siege in ${t.daysToSiege} days" },
        line = Lines.threat(t, content),
        warned = t.warned,
        marks = Element.entries.mapNotNull { e -> Threats.mark(e, t)?.let { e to MarkUi(Lines.threatMark(it, t, content), it == ThreatMark.COUNTERS) } }.toMap(),
        matchup = content.factionById[t.factionId]?.name?.let { name -> listOfNotNull(name, t.weakTo?.let { "Weak to ${it.word()}" }, t.resists?.let { "Resists ${it.word()}" }).joinToString(" · ") },
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
    return YesterdayUi(day.day, day.ledger?.let { "Took ${it.goldAtClose - it.goldAtOpen} gold" }, close?.counts, lines)
}

/** The Shop destination's content for [state]. Pure: reads the save, draws nothing, changes nothing. */
fun GameEngine.shopUi(state: GameState): ShopUi {
    val lead = Advice.lead(state, content, config)
    val line = Lines.lead(lead, state, content, config)
    val festival = state.worldFlags[WorldEvents.FLAG_FESTIVAL] == state.day
    val inShop = state.storedWeapons() + state.listedWeapons()
    val threat = threatUi(state)

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
        DemandRow("Carry no blade", "${d.unarmed.size}", names(d.unarmed)),
        DemandRow("Carry a worn blade", "${d.worn.size}", names(d.worn)),
        d.cheapestPrice?.let { DemandRow("Can afford the cheapest blade ($it gold)", "${d.canAffordCheapest} of ${d.living}") },
        d.medianPrice?.takeIf { it != d.cheapestPrice }?.let { DemandRow("Can afford the middle blade ($it gold)", "${d.canAffordMedian} of ${d.living}") },
        // Only against a stocked shelf: with nothing listed every class is unserved, and the lead already says so.
        d.unservedClasses.takeIf { it.isNotEmpty() && d.cheapestPrice != null }?.let { ids -> DemandRow("No listed blade suits", ids.joinToString { content.heroClass(it).name + "s" }) },
    )

    return ShopUi(
        seats = config.customers.shopCapacity + toolTotal(state, ToolEffect.EXTRA_CUSTOMERS) + if (festival) config.customers.festivalExtraSeats else 0,
        slots = shelfSlots(state),
        lead = LeadUi(lead.kind, line.action, line.reason, lead.weaponId, lead.familyId),
        requests = requests,
        demand = demand,
        // A day that cannot be laid out is left out here; the Gazette still has it.
        yesterday = state.lastResolution?.takeIf { it.day == state.day - 1 }?.let { runCatching { yesterday(state, it) }.getOrNull() },
        shelf = state.listedWeapons().map { stock(it, threat, state.era) },
        storage = state.storedWeapons().map { stock(it, threat, state.era) },
        threat = threat,
        requestSlots = config.customers.maxOpenCommissions,
        funds = Demand.funds(state, content, config),
        wants = d.wants.mapNotNull { id -> state.heroes[id]?.let { h -> Lines.want(h, content)?.let { WantUi(id, it, id in d.wantsAnswered, h.want!!.familyId, content.family(h.want!!.familyId).name, h.want!!.minPower, h.want!!.budget) } } },
    )
}
