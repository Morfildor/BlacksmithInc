package com.tinyblacksmith.core.shopday

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.model.*

enum class QuietKind { NO_VISITORS, EMPTY_SHELF }
/** A day with nobody to serve: one card. [visitors] are the ones who looked in at a bare shelf, in visit order. */
data class QuietDay(val kind: QuietKind, val visitors: List<MarketVisit> = emptyList())

enum class TallyOutcome { COMMISSION, COLLECTOR, BOUGHT, LEFT }
/** Visits that are not featured, by what came of them; [reason] is set for LEFT only. Members are in visit order. */
data class TallyGroup(val outcome: TallyOutcome, val reason: VisitReason?, val visits: List<MarketVisit>)

enum class Ending { FALLEN, BLESSING, TOMORROW }

/** In ranking order: when more than fit, the earlier kind is shown. */
enum class AftermathKind {
    SIEGE_HELD, SIEGE_LOST, DEATH, ELITE_SLAIN, AMBITION_FULFILLED, TITLE_EARNED, INHERITED, RESOLD,
    WIN_NEW_BLADE, WIN, GUILD_LESSON, SCARCE_LOOT, BLADE_GONE, LOSS,
}

/** What the stored roll says the blade was worth; never more than that. */
enum class Counterfactual { OLD_BLADE, BARE_HANDED }

/**
 * One consequence beyond the door. [text] is the sentence of the day's own record(s); every other field is copied from
 * a field result, a record of the day or a snapshot, and is absent when that source is.
 */
data class AftermathCard(
    val kind: AftermathKind,
    val text: String,
    val heroId: HeroId? = null, val heroName: String? = null,
    val otherHeroId: HeroId? = null,                  // the mentor of a lesson; the fallen owner of an inherited blade
    val weapon: WeaponSnapshot? = null,
    val oldWeapon: WeaponSnapshot? = null,            // the blade traded in this morning, for OLD_BLADE
    val foe: String? = null, val factionId: FactionId? = null,
    val counterfactual: Counterfactual? = null,
    val matchupHelped: Boolean = false,
    val title: String? = null,                        // a title the blade did not have this morning
    val materialId: MaterialId? = null,
    val fate: WeaponFate? = null,                     // what became of a fallen hero's blade
    val championIds: List<HeroId> = emptyList(),
    val forgeDamage: Int? = null,
    val replay: CombatReplay? = null,                 // "Watch the fight"
    val eventIds: List<String> = emptyList(),
)

data class ShopDayScript(
    val day: Int,
    val shelf: List<WeaponSnapshot>, val prices: Map<WeaponId, Int>,
    val quiet: QuietDay?,                 // NO_VISITORS, EMPTY_SHELF(names), or null
    val featured: List<MarketVisit>,      // in visit order
    val tally: List<TallyGroup>,          // every other visit, grouped by outcome and reason, with hero IDs and names
    val ledger: ShopLedger?,
    val aftermath: List<AftermathCard>,   // at most 3; siege first
    val moreInGazette: Int,
    val ending: Ending,                   // FALLEN, BLESSING, TOMORROW
    val lead: Lead?,                      // null when FALLEN
) {
    /** Every recorded visit of the day, each once, in the order they happened. */
    val visits: List<MarketVisit> get() = (featured + tally.flatMap { it.visits }).sortedBy { it.seq }

    fun blade(id: WeaponId?): WeaponSnapshot? = shelf.firstOrNull { it.weaponId == id }
}

/**
 * What the counter screen plays back: derived from the stored day and never saved. Reads records and state only; draws
 * no RNG and changes nothing, so watching, skipping or re-reading a day cannot move an outcome.
 */
object ShopDay {
    const val FEATURED_MAX = 3            // a presentation constant, not a balance number
    const val AFTERMATH_MAX = 3

    /** [after] is the state End Day returned with [resolution]. */
    fun script(resolution: DayResolution, after: GameState, content: ContentCatalog, config: BalanceConfig): ShopDayScript {
        val visits = resolution.visits
        // A day stored before the visit record has names and reasons only: a tally, then straight to the ending.
        val recorded = resolution.recordVersion >= 1
        val featured = if (recorded) featured(resolution) else emptyList()
        val rest = visits.filter { v -> featured.none { it === v } }
        val cards = if (recorded) aftermath(resolution, after, content) else emptyList()
        val ending = when {
            resolution.defeated -> Ending.FALLEN
            after.pendingBlessingOffer.isNotEmpty() -> Ending.BLESSING
            else -> Ending.TOMORROW
        }
        return ShopDayScript(
            day = resolution.day, shelf = resolution.shopWeapons, prices = resolution.shelfPrices,
            quiet = when {
                visits.isEmpty() -> QuietDay(QuietKind.NO_VISITORS)
                visits.all { it.reason == VisitReason.EMPTY_SHELVES } -> QuietDay(QuietKind.EMPTY_SHELF, visits)
                else -> null
            },
            featured = featured.sortedBy { it.seq }, tally = tally(rest), ledger = resolution.ledger,
            aftermath = cards.take(AFTERMATH_MAX), moreInGazette = maxOf(0, cards.size - AFTERMATH_MAX),
            ending = ending, lead = if (ending == Ending.FALLEN) null else Advice.lead(after, content, config),
        )
    }

    /** Each maps to a different action tomorrow; EMPTY_SHELVES and UNDECIDED are never featured. */
    private val refusalOrder = listOf(VisitReason.OVERPRICED, VisitReason.TOO_EXPENSIVE, VisitReason.NOT_SUITED, VisitReason.NOT_BETTER)

    /**
     * Up to [FEATURED_MAX] visits. A slot is kept for the best purchase and one for the first refusal when the day has
     * them; a commission, then a collector, take what is left before further purchases and refusals. Nobody is featured
     * twice (a patron who also browsed is featured as the patron).
     */
    private fun featured(r: DayResolution): List<MarketVisit> {
        val events = r.events.associateBy { it.id }
        fun MarketVisit.record(key: String) = eventIds.any { events[it]?.data?.containsKey(key) == true }
        val browsers = r.visits.filter { it.kind == VisitKind.BROWSE }
        val commissions = r.visits.filter { it.kind == VisitKind.COMMISSION }
        val collectors = r.visits.filter { it.kind == VisitKind.COLLECTOR }
        val specials = commissions.take(1) + collectors.take(1) + commissions.drop(1) + collectors.drop(1)
        val purchases = browsers.filter { it.purchasedWeaponId != null }.sortedWith(
            compareByDescending<MarketVisit> { v -> v.eventIds.any { events[it]?.data?.get("milestone") == "FIRST_SALE" } }
                .thenByDescending { it.customer?.regular == true }
                .thenByDescending { it.sale?.tradeInWeaponId != null }
                .thenByDescending { (it.sale?.saleBonus ?: 0) > 0 || it.record("premium") }
                .thenByDescending { it.sale?.cashPaid ?: 0 }
                .thenBy { it.seq },
        )
        val refusals = refusalOrder.map { reason -> browsers.filter { it.purchasedWeaponId == null && it.reason == reason } }

        val picked = mutableListOf<MarketVisit>()
        fun free(v: MarketVisit) = picked.none { it === v || (v.heroId != null && it.heroId == v.heroId) }
        fun take(from: List<MarketVisit>, atMost: Int) {
            var left = atMost
            for (v in from) {
                if (left == 0 || picked.size == FEATURED_MAX) return
                if (free(v)) { picked += v; left-- }
            }
        }
        val kept = (if (purchases.isNotEmpty()) 1 else 0) + (if (refusals.any { it.isNotEmpty() }) 1 else 0)
        take(specials, FEATURED_MAX - kept)
        take(purchases, 1)
        take(refusals.flatten(), 1)
        take(purchases, FEATURED_MAX)
        for (sameReason in refusals) if (sameReason.none { v -> picked.any { it === v } }) take(sameReason, 1)
        take(specials, FEATURED_MAX)
        return picked
    }

    private fun tally(rest: List<MarketVisit>): List<TallyGroup> {
        fun outcome(v: MarketVisit) = when {
            v.kind == VisitKind.COMMISSION -> TallyOutcome.COMMISSION
            v.kind == VisitKind.COLLECTOR -> TallyOutcome.COLLECTOR
            v.purchasedWeaponId != null -> TallyOutcome.BOUGHT
            else -> TallyOutcome.LEFT
        }
        return rest.groupBy { v -> outcome(v).let { it to v.reason.takeIf { _ -> it == TallyOutcome.LEFT } } }
            .map { (key, visits) -> TallyGroup(key.first, key.second, visits.sortedBy { it.seq }) }
            .sortedWith(compareBy<TallyGroup> { it.outcome }.thenBy { it.reason })
    }

    /**
     * Every card the day could show, best first: the siege, then what happened to a blade or to a hero seen at the
     * counter today. A plain win with an older blade is the Gazette's to tell, unless the stored roll says the blade
     * was needed.
     */
    private fun aftermath(r: DayResolution, after: GameState, content: ContentCatalog): List<AftermathCard> {
        val events = r.events.associateBy { it.id }
        val order = r.events.withIndex().associate { (i, e) -> e.id to i }
        val seen = r.visits.mapNotNull { it.heroId?.value }.toSet()
        val mornings = (r.shopWeapons + r.visits.mapNotNull { it.customer?.equipped }).associateBy { it.weaponId }
        fun snapshot(id: String?): WeaponSnapshot? = id?.let { WeaponId(it) }?.let { w -> after.weapons[w]?.let { WeaponSnapshot.of(it) } ?: mornings[w] }
        fun bladeOf(e: EventRecord?): WeaponSnapshot? = e?.subjectIds?.firstNotNullOfOrNull { snapshot(it) }
        fun replay(ids: List<String>) = r.replays.firstOrNull { it.kind == ReplayKind.EXPEDITION && it.eventId in ids }
        fun customer(id: HeroId) = id.value in seen || after.weapons.values.any { it.ownerId == id }

        val cards = mutableListOf<AftermathCard>()
        val used = mutableSetOf<String>()

        r.events.firstOrNull { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }?.let { siege ->
            val damage = r.events.firstOrNull { it.type == EventType.FORGE_DAMAGED }
            val wall = r.field.firstOrNull { it.outcome == FieldOutcome.HELD_THE_WALL || it.outcome == FieldOutcome.FELL_AT_THE_WALL }
            cards += AftermathCard(
                if (siege.type == EventType.SIEGE_WON) AftermathKind.SIEGE_HELD else AftermathKind.SIEGE_LOST,
                listOfNotNull(siege.text, damage?.text).joinToString(" "), foe = wall?.foe, factionId = wall?.factionId,
                championIds = siege.subjectIds.map { HeroId(it) }, forgeDamage = damage?.data?.get("damage")?.toIntOrNull(),
                replay = r.replays.firstOrNull { it.kind == ReplayKind.SIEGE }, eventIds = listOfNotNull(siege.id, damage?.id),
            )
        }

        for (f in r.field) {
            val told = f.eventIds.firstNotNullOfOrNull { events[it] } ?: continue  // nothing recorded to tell it with
            val blade = f.weapon ?: bladeOf(told)
            val relevant = blade != null || f.heroId.value in seen
            when (f.outcome) {
                FieldOutcome.DIED, FieldOutcome.FELL_AT_THE_WALL -> {
                    val died = f.eventIds.mapNotNull { events[it] }.firstOrNull { it.type == EventType.HERO_DIED } ?: told
                    // The blade they carried and what became of it; an inheritance is a card of its own.
                    val fate = r.events.firstOrNull { e -> e.type != EventType.WEAPON_INHERITED && WeaponFate.KEY in e.data && f.heroId.value in e.subjectIds && order.getValue(e.id) > order.getValue(died.id) }
                    val carried = bladeOf(fate) ?: blade
                    if (carried == null && f.heroId.value !in seen) continue
                    used += listOfNotNull(died.id, fate?.id)
                    cards += AftermathCard(
                        AftermathKind.DEATH, listOfNotNull(died.text, fate?.text).joinToString(" "), f.heroId, f.heroName, weapon = carried, foe = f.foe, factionId = f.factionId,
                        fate = fate?.data?.get(WeaponFate.KEY)?.let { name -> WeaponFate.entries.firstOrNull { it.name == name } },
                        replay = replay(f.eventIds), eventIds = listOfNotNull(died.id, fate?.id),
                    )
                }
                FieldOutcome.WON -> {
                    if (!relevant) continue
                    val bought = r.visits.lastOrNull { it.heroId == f.heroId && it.purchasedWeaponId != null && it.purchasedWeaponId == blade?.weaponId }
                    val old = bought?.customer?.equipped?.takeIf { bought.sale?.tradeInWeaponId == it.weaponId }
                    val title = blade?.let { b -> after.weapons[b.weaponId]?.title?.takeIf { mornings[b.weaponId]?.let { m -> m.title == null } == true } }
                    val scarce = f.materialId?.takeIf { id -> content.materialById[id]?.let { it.category == MaterialCategory.CATALYST || it.tier >= 3 } == true }
                    val counterfactual = when {
                        old != null -> Counterfactual.OLD_BLADE.takeIf { f.lostWithOldBlade == true }
                        f.lostBareHanded -> Counterfactual.BARE_HANDED
                        else -> null
                    }
                    val kind = when {
                        f.elite -> AftermathKind.ELITE_SLAIN
                        title != null -> AftermathKind.TITLE_EARNED
                        bought != null -> AftermathKind.WIN_NEW_BLADE
                        counterfactual != null -> AftermathKind.WIN
                        scarce != null -> AftermathKind.SCARCE_LOOT
                        else -> continue
                    }
                    used += told.id
                    cards += AftermathCard(
                        kind, told.text, f.heroId, f.heroName, weapon = blade, oldWeapon = old.takeIf { counterfactual == Counterfactual.OLD_BLADE }, foe = f.foe, factionId = f.factionId,
                        counterfactual = counterfactual, matchupHelped = f.matchupHelped, title = title, materialId = f.materialId,
                        replay = replay(f.eventIds), eventIds = f.eventIds,
                    )
                }
                FieldOutcome.DRIVEN_BACK -> {
                    if (blade == null) continue
                    val broken = r.events.firstOrNull { it.type == EventType.WEAPON_BROKEN && blade.weaponId.value in it.subjectIds }
                    used += listOfNotNull(told.id, broken?.id)
                    cards += AftermathCard(
                        if (broken != null) AftermathKind.BLADE_GONE else AftermathKind.LOSS, listOfNotNull(told.text, broken?.text).joinToString(" "), f.heroId, f.heroName,
                        weapon = blade, foe = f.foe, factionId = f.factionId, replay = replay(f.eventIds), eventIds = listOfNotNull(told.id, broken?.id),
                    )
                }
                FieldOutcome.GUILD_LESSON -> {
                    val mentor = f.withHeroId ?: continue
                    if (!customer(f.heroId) || !customer(mentor)) continue
                    used += told.id
                    cards += AftermathCard(AftermathKind.GUILD_LESSON, told.text, f.heroId, f.heroName, otherHeroId = mentor, eventIds = listOf(told.id))
                }
                else -> {}
            }
        }

        for (e in r.events) {
            if (e.id in used) continue
            val kind = when (e.type) {
                EventType.AMBITION_FULFILLED -> AftermathKind.AMBITION_FULFILLED
                EventType.WEAPON_INHERITED -> AftermathKind.INHERITED
                EventType.WEAPON_RESOLD -> AftermathKind.RESOLD
                EventType.WEAPON_BROKEN, EventType.WEAPON_LOST, EventType.WEAPON_STOLEN -> AftermathKind.BLADE_GONE
                else -> continue
            }
            val heroes = e.subjectIds.filter { HeroId(it) in after.heroes }
            val blade = bladeOf(e)
            if (blade == null && heroes.none { it in seen }) continue
            // The first hero a record names is the one it is about: the heir (then the fallen), the buyer, the wielder.
            val hero = heroes.firstOrNull()?.let { after.heroes[HeroId(it)] }
            cards += AftermathCard(
                kind, e.text, hero?.id, hero?.fullName, otherHeroId = heroes.getOrNull(1)?.let { HeroId(it) }, weapon = blade,
                fate = e.data[WeaponFate.KEY]?.let { name -> WeaponFate.entries.firstOrNull { it.name == name } }, eventIds = listOf(e.id),
            )
        }
        return cards.withIndex().sortedWith(compareBy<IndexedValue<AftermathCard>> { it.value.kind }.thenBy { it.index }).map { it.value }
    }
}
