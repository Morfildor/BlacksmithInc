package com.tinyblacksmith.core.gazette

import com.tinyblacksmith.core.model.EventRecord
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.FieldOutcome
import com.tinyblacksmith.core.model.FieldResult
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.IncomeKind
import com.tinyblacksmith.core.model.MarketVisit
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.ShopLedger
import com.tinyblacksmith.core.model.VisitKind
import com.tinyblacksmith.core.model.VisitReason
import com.tinyblacksmith.core.text.LegacyProse
import com.tinyblacksmith.core.text.joinSentences

/** The Emberfall Gazette (GDD 11): headlines derive only from real event records, ordered by priority. */
object Gazette {
    const val MAX_HEADLINES = 5

    fun headlines(dayEvents: List<EventRecord>): List<String> =
        dayEvents.filter { it.priority >= 2 }
            .sortedWith(compareByDescending<EventRecord> { it.priority }.thenBy { it.serial })
            .take(MAX_HEADLINES)
            .map { it.text }

    /**
     * The records one day's paper is set from: the stored report of the latest day, the archive for any other. A day
     * resolved by an older build (`recordVersion` 0) may have stored only its End Day records (F03), so the archive is
     * used for it instead.
     */
    fun dayRecords(state: GameState, day: Int): List<EventRecord> =
        state.lastResolution?.takeIf { it.day == day && it.recordVersion >= 1 }?.events ?: state.eventsForDay(day)

    /** The paper's name and the day as a dateline, kept apart so no screen has to split a string to show them. */
    const val PAPER = "EMBERFALL GAZETTE"
    fun dateline(day: Int): String = "DAY $day"

    fun masthead(day: Int): String = "$PAPER | ${dateline(day)}"

    /** One day's paper: the lede (the day's biggest news, at most two lines), a short tally and the record by section. */
    data class Edition(val lede: List<String>, val tally: List<String>, val sections: List<Section>)
    data class Section(val title: String, val lines: List<String>)

    const val SHOP = "Shop"
    const val HEROES = "Heroes"
    const val TOWN = "Town"
    const val FORGE = "Forge"

    /** Milestones whose fact another record of the same day already tells; the milestone line is folded away. */
    private val impliedMilestones = mapOf(
        "ELITE_SLAIN" to EventType.ELITE_SLAIN, "AMBITION_FULFILLED" to EventType.AMBITION_FULFILLED,
        "SIEGE_SURVIVED" to EventType.SIEGE_WON, "CHAMPION_ARMED" to EventType.SIEGE_WON,
        "LEGENDARY_FORGED" to EventType.WEAPON_FORGED, "EPIC_FORGED" to EventType.WEAPON_FORGED,
        "FIRST_SALE" to EventType.WEAPON_SOLD, "HERO_LEVEL_5" to EventType.HERO_LEVELED,
    )

    private val heroTypes = setOf(
        EventType.ELITE_SLAIN, EventType.EXPEDITION_WON, EventType.EXPEDITION_LOST, EventType.HERO_WOUNDED, EventType.HERO_LEVELED,
        EventType.HERO_DIED, EventType.WEAPON_BROKEN, EventType.WEAPON_RECOVERED, EventType.WEAPON_STOLEN, EventType.WEAPON_LOST,
        EventType.AMBITION_FULFILLED, EventType.HERO_RETIRED, EventType.GUILD_FOUNDED, EventType.HERO_MENTORED, EventType.WEAPON_INHERITED,
        EventType.HERO_ARRIVED, EventType.GUILD_JOINED, EventType.GUILD_MENTORED, EventType.AMBITION_PURSUED, EventType.WEAPON_SURFACED, EventType.WEAPON_RESOLD,
    )
    private val quietTypes = setOf(EventType.HERO_PATROLLED, EventType.HERO_RESTED, EventType.GUILD_TRAINED)
    private val shopTypes = setOf(EventType.WEAPON_SOLD, EventType.COMMISSION_OFFERED, EventType.COMMISSION_COMPLETED, EventType.COMMISSION_EXPIRED)
    private val forgeTypes = setOf(
        EventType.WEAPON_FORGED, EventType.WEAPON_LISTED, EventType.WEAPON_HONED, EventType.WEAPON_DONATED, EventType.WEAPON_SALVAGED,
        EventType.TOOL_BOUGHT, EventType.DISCOVERY, EventType.SIGNATURE_DISCOVERED, EventType.MATERIAL_BOUGHT,
    )

    /** Why a visitor left without buying (`MarketVisit.reason`), as the paper puts it. */
    fun visitReason(reason: VisitReason): String = when (reason) {
        VisitReason.TOO_EXPENSIVE -> "couldn't afford anything"
        VisitReason.NOT_BETTER -> "found no upgrade for their weapon"
        VisitReason.EMPTY_SHELVES -> "found an empty shelf"
        VisitReason.OVERPRICED -> "thought the prices were too high"
        VisitReason.NOT_SUITED -> "found no suitable weapon"
        VisitReason.RESISTED -> "passed on a weapon the attackers resist"
        else -> "left undecided"
    }

    private const val VISITORS = "visitors"
    private const val BOUGHT = "bought"
    private const val WON = "won"
    private const val LOST = "lost"

    /** The `SHOP_DAY` record (written once per End Day): the counts and the coin by kind that [tally] prints, so a past day needs no snapshot to be told the same way. */
    fun shopDayData(visits: List<MarketVisit>, ledger: ShopLedger, field: List<FieldResult>): Map<String, String> {
        val browsers = visits.filter { it.kind == VisitKind.BROWSE }
        return mapOf(
            VISITORS to browsers.size, BOUGHT to browsers.count { it.purchasedWeaponId != null },
            WON to field.count { it.outcome == FieldOutcome.WON }, LOST to field.count { it.outcome == FieldOutcome.DRIVEN_BACK || it.outcome == FieldOutcome.DIED },
        ).mapValues { it.value.toString() } + ledger.income.entries.sortedBy { it.key }.associate { it.key.name to it.value.toString() }
    }

    fun shopDayText(visits: List<MarketVisit>): String {
        val browsers = visits.filter { it.kind == VisitKind.BROWSE }
        return "${count(browsers.size, "visitor")} came by. ${browsers.count { it.purchasedWeaponId != null }} bought something."
    }

    /**
     * Lays one day's records out as a paper: Shop (sales, commissions, who left and why), Heroes (one sentence per
     * hero, the quiet ones on one line), Town (siege, forge, blessings, world events, the standing siege warning last)
     * and Forge (the smith's own work in one line, then what the journal learned). [heroNames] maps hero id to full
     * name so a hero's several records fold into one sentence; [visits], [ledger] and [field] are only known for the
     * fresh resolution, and the tally is counted from the ledger and the field results when the day has them.
     * Pure: the same records always give the same edition, and every line comes from a real record (GDD 11).
     */
    fun edition(
        dayEvents: List<EventRecord>, heroNames: Map<String, String>, visits: List<MarketVisit> = emptyList(),
        ledger: ShopLedger? = null, field: List<FieldResult> = emptyList(),
    ): Edition {
        val events = dayEvents.sortedBy { it.serial }
        val types = events.map { it.type }.toSet()
        val arrivedByEvent = events.filter { it.type == EventType.WORLD_EVENT }.flatMap { it.subjectIds }.toSet()
        val pool = events.filter { e ->
            when (e.type) {
                EventType.WEAPON_EQUIPPED -> false  // always follows a sale or an inheritance line
                EventType.SHOP_DAY -> false  // the tally's own numbers, not news
                EventType.MILESTONE -> impliedMilestones[e.data["milestone"]]?.let { it !in types } ?: true
                EventType.HERO_ARRIVED -> e.subjectIds.none { it in arrivedByEvent }  // the world event tells the arrival
                else -> true
            }
        }
        val ledeEvents = pool.filter { it.priority >= 6 && it.type != EventType.SIEGE_WARNING }
            .sortedWith(compareByDescending<EventRecord> { it.priority }.thenBy { it.serial })
            .take(2).filterIndexed { i, e -> i == 0 || e.priority >= 7 }
        val rest = pool - ledeEvents.toSet()

        val sections = listOfNotNull(
            section(SHOP, shopLines(rest, visits)),
            section(HEROES, heroLines(rest, heroNames)),
            section(TOWN, townLines(rest)),
            section(FORGE, forgeLines(rest)),
        )
        return Edition(ledeEvents.map { it.text }, tally(events, visits, ledger, field), sections)
    }

    private fun section(title: String, lines: List<String>) = if (lines.isEmpty()) null else Section(title, lines)

    /** The till by kind as the day's records tell it: for a day without a ledger (resolved by an older build, or read from the archive). */
    private fun recordedIncome(events: List<EventRecord>): Map<IncomeKind, Int> {
        fun sum(kind: EventType, amount: (EventRecord) -> Int?) = events.filter { it.type == kind }.sumOf { amount(it) ?: 0 }
        return mapOf(
            IncomeKind.SHELF_SALE to sum(EventType.WEAPON_SOLD) { e -> (e.data["price"]?.toIntOrNull() ?: 0) - (e.data["tradeIn"]?.toIntOrNull() ?: 0) },
            IncomeKind.SALE_BONUS to sum(EventType.WEAPON_SOLD) { it.data["bonus"]?.toIntOrNull() },
            IncomeKind.COMMISSION to sum(EventType.COMMISSION_COMPLETED) { it.data["reward"]?.toIntOrNull() },
            IncomeKind.COLLECTOR to sum(EventType.WORLD_EVENT) { e -> e.data["price"]?.toIntOrNull()?.takeIf { e.data["event"] == "collector" } },
            IncomeKind.TRIBUTE to sum(EventType.MILESTONE) { it.data["tribute"]?.toIntOrNull() },
        )
    }

    private fun tally(events: List<EventRecord>, visits: List<MarketVisit>, ledger: ShopLedger?, field: List<FieldResult>): List<String> {
        val commissions = events.count { it.type == EventType.COMMISSION_COMPLETED }
        val sold = events.count { it.type == EventType.WEAPON_SOLD } + commissions
        val shopDay = events.firstOrNull { it.type == EventType.SHOP_DAY }?.data  // a day resolved since the visit record; null on an older one
        val income = ledger?.income ?: shopDay?.let { d -> IncomeKind.entries.associateWith { d[it.name]?.toIntOrNull() ?: 0 } } ?: recordedIncome(events)
        val tribute = income[IncomeKind.TRIBUTE] ?: 0  // the town's thanks is not shop takings
        val gold = income.values.sum() - tribute
        // A fatal expedition is a loss; only the field results tell it from a death at the walls, so an older day counts as it did.
        val won = if (ledger != null) field.count { it.outcome == FieldOutcome.WON }
            else shopDay?.get(WON)?.toIntOrNull() ?: events.count { it.type == EventType.ELITE_SLAIN || (it.type == EventType.EXPEDITION_WON && "material" !in it.data) }
        val lost = if (ledger != null) field.count { it.outcome == FieldOutcome.DRIVEN_BACK || it.outcome == FieldOutcome.DIED }
            else shopDay?.get(LOST)?.toIntOrNull() ?: events.count { it.type == EventType.EXPEDITION_LOST }
        val fallen = events.count { it.type == EventType.HERO_DIED }
        // Browsers only: a commission patron and the collector are counted by their own lines.
        val browsers = visits.filter { it.kind == VisitKind.BROWSE }
        val visitors = if (visits.isNotEmpty()) browsers.size else shopDay?.get(VISITORS)?.toIntOrNull() ?: 0
        val bought = if (visits.isNotEmpty()) browsers.count { it.purchasedWeaponId != null } else shopDay?.get(BOUGHT)?.toIntOrNull() ?: 0
        return buildList {
            if (gold > 0 || sold > 0 || visitors > 0) add("Shop income $gold gold")
            if (tribute > 0) add("Town tribute: $tribute gold")
            if (visitors > 0) {
                add("$bought of ${count(visitors, "visitor")} bought")
                if (commissions > 0) add("${count(commissions, "request")} delivered")
            } else if (sold > 0) add("$sold sold")
            if (won + lost > 0) add("Expeditions: $won won, $lost lost")
            if (fallen > 0) add(if (fallen == 1) "1 hero fell" else "$fallen heroes fell")
        }
    }

    private fun shopLines(events: List<EventRecord>, visits: List<MarketVisit>): List<String> {
        val lines = events.filter { it.type in shopTypes }.map { LegacyProse.display(it.text) }.toMutableList()
        val left = visits.filter { it.purchasedWeaponId == null }
        for (reason in left.map { it.reason }.distinct()) {
            lines += "${names(left.filter { it.reason == reason }.map { it.heroName })} ${visitReason(reason)}."
        }
        return lines
    }

    private fun heroLines(events: List<EventRecord>, heroNames: Map<String, String>): List<String> {
        val byHero = events.filter { it.type in heroTypes }.groupBy { e -> e.subjectIds.firstOrNull { it.startsWith("h") } ?: e.id }
        val groups = byHero.entries.sortedWith(
            compareByDescending<Map.Entry<String, List<EventRecord>>> { g -> g.value.maxOf { it.priority } }.thenBy { it.value.first().serial },
        )
        val lines = groups.map { (_, es) -> es.map { LegacyProse.display(it.text) }.joinSentences() }.toMutableList()
        val patrolled = events.filter { it.type == EventType.HERO_PATROLLED }.mapNotNull { heroNames[it.subjectIds.firstOrNull()] }
        val rested = events.filter { it.type == EventType.HERO_RESTED }.mapNotNull { heroNames[it.subjectIds.firstOrNull()] }
        val trained = events.filter { it.type == EventType.GUILD_TRAINED }.mapNotNull { heroNames[it.subjectIds.firstOrNull()] }
        val quiet = listOfNotNull(
            patrolled.takeIf { it.isNotEmpty() }?.let { "On the walls: ${it.joinToString(", ")}." },
            trained.takeIf { it.isNotEmpty() }?.let { "At the guild hall: ${it.joinToString(", ")}." },
            rested.takeIf { it.isNotEmpty() }?.let { "Resting: ${it.joinToString(", ")}." },
        )
        if (quiet.isNotEmpty()) lines += quiet.joinToString(" ")
        return lines
    }

    private fun townLines(events: List<EventRecord>): List<String> {
        val town = events.filter { it.type !in heroTypes && it.type !in quietTypes && it.type !in shopTypes && it.type !in forgeTypes }
        return town.filter { it.type != EventType.SIEGE_WARNING }.map { LegacyProse.display(it.text) } + town.filter { it.type == EventType.SIEGE_WARNING }.map { LegacyProse.display(it.text) }.takeLast(1)
    }

    private fun forgeLines(events: List<EventRecord>): List<String> {
        val forged = events.filter { it.type == EventType.WEAPON_FORGED }
        val fine = forged.mapNotNull { e -> e.data["rarity"]?.let { r -> Rarity.entries.firstOrNull { it.name == r } } }.filter { it >= Rarity.EPIC }
        val parts = buildList {
            if (forged.isNotEmpty()) {
                val detail = Rarity.entries.reversed().mapNotNull { r -> fine.count { it == r }.takeIf { it > 0 }?.let { "$it ${r.name.lowercase()}" } }
                add("forged ${count(forged.size, "weapon")}" + if (detail.isEmpty()) "" else " (${detail.joinToString(", ")})")
            }
            events.count { it.type == EventType.WEAPON_LISTED }.takeIf { it > 0 }?.let { add("listed $it") }
            events.count { it.type == EventType.WEAPON_HONED }.takeIf { it > 0 }?.let { add("honed $it") }
            events.count { it.type == EventType.WEAPON_DONATED }.takeIf { it > 0 }?.let { add("donated ${count(it, "weapon")} to the watch") }
            events.filter { it.type == EventType.WEAPON_SALVAGED }.sumOf { it.data["count"]?.toIntOrNull() ?: 1 }.takeIf { it > 0 }?.let { add("salvaged $it") }
            events.filter { it.type == EventType.TOOL_BOUGHT }.forEach { add("bought ${it.data["name"] ?: it.text.substringAfter(": ").trimEnd('.')}") }
            events.filter { it.type == EventType.MATERIAL_BOUGHT }.sumOf { it.data["cost"]?.toIntOrNull() ?: 0 }.takeIf { it > 0 }?.let { add("spent $it gold on materials") }
        }
        val work = if (parts.isEmpty()) emptyList() else listOf(parts.joinSentences())
        return work + events.filter { it.type == EventType.DISCOVERY || it.type == EventType.SIGNATURE_DISCOVERED }.map { LegacyProse.display(it.text) }
    }

    private fun count(n: Int, noun: String) = "$n $noun" + if (n == 1) "" else "s"

    private fun names(list: List<String>): String = when (list.size) {
        0 -> ""
        1 -> list[0]
        else -> list.dropLast(1).joinToString(", ") + " and " + list.last()
    }

    private val EventRecord.serial: Int get() = id.drop(1).toIntOrNull() ?: 0
}
