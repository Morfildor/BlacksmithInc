package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.example.blacksmithproject.ui.detail.Stat
import com.example.blacksmithproject.ui.detail.weaponStats
import com.example.blacksmithproject.ui.shopday.Beat
import com.example.blacksmithproject.ui.shopday.FaceUi
import com.example.blacksmithproject.ui.shopday.toUi
import com.tinyblacksmith.core.content.ToolEffect
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.WorldEvents
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

/** The one thing worth doing first, in the words of `Lines.lead`; [weaponId] is the blade it points at, if any. */
@Immutable data class LeadUi(val kind: LeadKind, val action: String, val reason: String?, val weaponId: WeaponId? = null)

/** An open request: who asks, for what, on what terms, and (once accepted) which blade End Day will hand over or what is missing. */
@Immutable
data class RequestUi(val id: CommissionId, val buyer: FaceUi, val asks: String, val terms: String, val offered: Boolean, val readiness: String?, val fits: List<String>)

/**
 * A blade on the shelf ([price] set) or in storage ([price] null, [suggested] is what the quick "List" asks). [stats] are
 * the item card's own numbers (`weaponStats`), less a renown of nothing; [buffs] and [flaws] are the affix names.
 */
@Immutable
data class StockUi(
    val weapon: Weapon, val summary: String, val favoured: String?, val price: Int?, val suggested: Int,
    val stats: List<Stat> = emptyList(), val buffs: List<String> = emptyList(), val flaws: List<String> = emptyList(),
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
)

/** Names are listed while they fit on a line or two; a longer list is only its count. */
private const val NAMES_SHOWN = 4

private fun dueWords(left: Int, deadline: Int) = when { left <= 0 -> "due today"; left == 1 -> "due tomorrow"; else -> "due day $deadline, in $left days" }

/** Which hero classes favour this family: a content read, never a sale prediction. */
private fun GameEngine.favoured(familyId: WeaponFamilyId): String? {
    val fans = content.classes.filter { familyId in it.preferredFamilies }.map { it.name + "s" }
    return if (fans.isEmpty()) null else "${fans.joinToString(" and ")} favour the ${content.family(familyId).name.lowercase()}"
}

private fun GameEngine.stock(w: Weapon) = StockUi(
    w, Labels.weaponSummary(w, content), favoured(w.familyId), w.listedPrice, suggestedPrice(w),
    // A number with a ceiling is always said; one without (power, renown) only when there is any.
    stats = weaponStats(WeaponSnapshot.of(w)).filter { it.max != null || it.value > 0 },
    buffs = w.affixes.map { content.affix(it).name }, flaws = w.flaws.map { content.affix(it).name },
)

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
        lead = LeadUi(lead.kind, line.action, line.reason, lead.weaponId),
        requests = requests,
        demand = demand,
        // A day that cannot be laid out is left out here; the Gazette still has it.
        yesterday = state.lastResolution?.takeIf { it.day == state.day - 1 }?.let { runCatching { yesterday(state, it) }.getOrNull() },
        shelf = state.listedWeapons().map { stock(it) },
        storage = state.storedWeapons().map { stock(it) },
    )
}
