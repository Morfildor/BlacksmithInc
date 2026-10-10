package com.tinyblacksmith.core.gazette

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.DayResolution
import com.tinyblacksmith.core.model.EventRecord
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.IncomeKind
import com.tinyblacksmith.core.model.VisitKind
import com.tinyblacksmith.core.model.VisitReason
import com.tinyblacksmith.core.shopday.Advice
import com.tinyblacksmith.core.shopday.LeadKind
import com.tinyblacksmith.core.shopday.Lines
import com.tinyblacksmith.core.text.LegacyProse
import com.tinyblacksmith.core.text.asSentence
import com.tinyblacksmith.core.text.joinSentences

/**
 * The Gazette as a daily briefing: what changed, the shop in one or two lines, what to do before tomorrow. Every
 * line comes from a typed record (event type, subject IDs, recorded data, the ledger), never from matching words in
 * prose, and a fact the records do not hold is left out rather than guessed. The full record stays one disclosure away
 * in [Digest.details]. Pure: the same records always give the same digest, and fresh and archived days read the same.
 */
object GazetteDigest {
    const val MAX_STORIES = 5
    const val MAX_NOTICES = 2
    const val QUIET = "A quiet day in Emberfall"

    enum class Kind {
        ERA_ENDED, SIEGE_WON, SIEGE_LOST, DEATH, DEATHS, SIEGE_WARNING, SIGNATURE, CLUE, PAIRING, WEAPON_BROKEN, LEGEND_RETURNED, INHERITED,
        COMMISSION_DONE, COMMISSION_EXPIRED, CONSEQUENCE, AMBITION, RETIRED, GUILD, ELITE, SCARCE, FIRST_SALE, ARRIVAL,
    }

    /** One block of the report: a short heading and at most two short sentences. [details] is only set where blocks were merged. */
    data class Story(
        val kind: Kind, val heading: String, val body: String = "",
        val subjectIds: List<String> = emptyList(), val eventIds: List<String> = emptyList(), val details: List<String> = emptyList(),
        val opensNotebook: Boolean = false, val mandatory: Boolean = false,
    )

    /**
     * [stories] is the default view, never more than [MAX_STORIES] unless safety-critical news needs more; [more] is
     * what did not fit and stays one tap away; [shop] and [before] are the shop line and the next-day notices;
     * [details] is the full record of the day.
     */
    data class Digest(
        val day: Int, val stories: List<Story>, val more: List<Story>, val shop: List<String>, val before: List<String>,
        val details: Gazette.Edition,
    ) {
        val isQuiet: Boolean get() = stories.isEmpty() && more.isEmpty()
        /** One line for an archive row. */
        val headline: String get() = stories.firstOrNull()?.heading ?: shop.firstOrNull() ?: QUIET
    }

    /** What the digest may read. Built by [of] from a save, or by hand in a test. */
    class Facts(
        val day: Int,
        val events: List<EventRecord>,
        /** Siege days the day before's paper already warned of: a warning for one of them is a reminder, not news. */
        val warnedBefore: Set<String>,
        val heroNames: Map<String, String>,
        val weaponNames: Map<String, String>,
        val content: ContentCatalog,
        val resolution: DayResolution? = null,
        /** The notices for the most recent resolved day only; an older paper never carries today's advice. */
        val notices: List<String> = emptyList(),
        val claimHint: Boolean = false,
    )

    fun of(state: GameState, day: Int, content: ContentCatalog, config: BalanceConfig): Digest {
        val events = Gazette.dayRecords(state, day)
        val resolution = state.lastResolution?.takeIf { it.day == day }
        val latest = resolution != null && day == state.day - 1
        val facts = Facts(
            day = day, events = events,
            warnedBefore = state.eventsForDay(day - 1).filter { it.type == EventType.SIEGE_WARNING }.mapNotNull { it.data["day"] }.toSet(),
            heroNames = state.heroes.values.associate { it.id.value to it.fullName },
            weaponNames = state.weapons.values.associate { it.id.value to it.name },
            content = content, resolution = resolution,
            notices = if (latest) forward(state, content, config) else recordedNotices(events),
            claimHint = state.isEnded && state.runId.value !in state.legacy.claimedRunIds,
        )
        return of(facts)
    }

    fun of(f: Facts): Digest {
        val (main, more) = select(candidates(f))
        return Digest(
            f.day, main, more, shopLines(f), f.notices.take(MAX_NOTICES),
            Gazette.edition(f.events, f.heroNames, f.resolution?.visits.orEmpty(), f.resolution?.ledger, f.resolution?.field.orEmpty()),
        )
    }

    // ---- stories ----

    private class Candidate(val tier: Int, val serial: Int, val story: Story)

    private val fateTypes = setOf(EventType.WEAPON_INHERITED, EventType.WEAPON_RECOVERED, EventType.WEAPON_STOLEN, EventType.WEAPON_LOST)
    private val siegeMilestones = setOf("SIEGE_SURVIVED", "CHAMPION_ARMED", "WARLORD_DEFEATED")

    private fun candidates(f: Facts): List<Candidate> {
        val ev = f.events.sortedBy { it.serial }
        val out = mutableListOf<Candidate>()
        val used = mutableSetOf<String>()
        fun hero(id: String?) = id?.let { f.heroNames[it] }
        fun weapon(id: String?) = id?.let { f.weaponNames[it] }
        fun add(tier: Int, first: EventRecord, story: Story, vararg folded: EventRecord?) {
            used += first.id
            folded.forEach { it?.let { r -> used += r.id } }
            out += Candidate(tier, first.serial, story.copy(eventIds = listOf(first.id) + folded.filterNotNull().map { it.id }))
        }

        // Tier 0: the era ends.
        ev.lastOrNull { it.type == EventType.FORGE_DESTROYED }?.let { e ->
            val body = listOfNotNull("This era ended on day ${e.day}.", "Claim your legacy points to begin the next.".takeIf { f.claimHint }).joinToString(" ")
            add(0, e, Story(Kind.ERA_ENDED, "The forge has fallen", body, mandatory = true))
        }

        // Tier 1: the siege, with its damage and tribute folded in.
        ev.lastOrNull { it.type == EventType.SIEGE_WON || it.type == EventType.SIEGE_LOST }?.let { s ->
            val won = s.type == EventType.SIEGE_WON
            val attacker = s.data["attacker"] ?: LegacyProse.siegeAttacker(s.text)
            val champions = s.subjectIds.mapNotNull { hero(it) }
            val damageRecord = ev.firstOrNull { it.type == EventType.FORGE_DAMAGED }
            val damage = damageRecord?.data?.get("damage")?.toIntOrNull()
            val tributeRecord = ev.firstOrNull { it.type == EventType.MILESTONE && it.data["tribute"] != null }
            val tribute = tributeRecord?.data?.get("tribute")?.toIntOrNull()
            val milestones = ev.filter { it.type == EventType.MILESTONE && it.data["milestone"] in siegeMilestones }
            val consequences = listOfNotNull(damage?.let { "the forge took $it damage" }, tribute?.takeIf { won }?.let { "the town paid you $it gold" }).joinToString(" and ")
            val sentences = if (won) listOfNotNull(
                when {
                    champions.isNotEmpty() && attacker != null -> "${names(champions)} defended the walls against $attacker."
                    champions.isNotEmpty() -> "${names(champions)} defended the walls."
                    attacker != null -> "The walls held against $attacker."
                    else -> null
                },
                consequences.takeIf { it.isNotEmpty() }?.asSentence(),
            ) else listOfNotNull(
                attacker?.let { "${it.replaceFirstChar { c -> c.uppercase() }} ${if (s.data["rout"] == "true") "routed" else "overran"} the defenders." },
                damage?.let { "The forge took $it damage." },
            )
            val body = sentences.joinToString(" ").ifEmpty { LegacyProse.display(s.text) }
            add(
                1, s, Story(if (won) Kind.SIEGE_WON else Kind.SIEGE_LOST, if (won) "Emberfall held" else "The defenses fell", body, subjectIds = s.subjectIds, mandatory = true),
                damageRecord, tributeRecord.takeIf { won }, *milestones.toTypedArray(),
            )
        }

        // Tier 1: each death with the recorded fate of the weapon it carried.
        for (d in ev.filter { it.type == EventType.HERO_DIED }.distinctBy { it.subjectIds.firstOrNull() ?: it.id }) {
            val id = d.subjectIds.firstOrNull()
            val name = hero(id)
            val fate = ev.firstOrNull { it.type in fateTypes && id != null && id in it.subjectIds && it.id !in used }
            // The record's own cause, as "They fell to ...", so the heading's name is not said twice.
            val cause = if (name != null && d.text.startsWith("$name ")) "They ${d.text.removePrefix("$name ")}" else d.text
            val body = listOfNotNull(LegacyProse.display(cause), fate?.let { LegacyProse.display(it.text) }).joinToString(" ")
            add(1, d, Story(Kind.DEATH, if (name != null) "$name died" else "A hero died", body, subjectIds = listOfNotNull(id), mandatory = true), fate)
        }

        // Tier 2: a siege warning that is new today, with the besieger's weakness and the siege's trait.
        ev.filter { it.type == EventType.SIEGE_WARNING && it.data["day"] != null && it.data["day"] !in f.warnedBefore }.lastOrNull()?.let { w ->
            val faction = w.data["faction"]
            val weak = w.data["weak"]
            val trait = f.content.siegeTrait(w.data["trait"])
            val first = when {
                faction != null && weak != null -> "$faction, weak to $weak."
                faction != null -> "$faction."
                else -> null
            }
            val effect = trait?.description?.substringBefore(". ")?.asSentence()
            val body = listOfNotNull(first, effect).joinToString(" ").ifEmpty { LegacyProse.display(w.text) }
            add(2, w, Story(Kind.SIEGE_WARNING, "Siege on day ${w.data["day"]}", body, mandatory = true))
        }

        // Tier 3: new knowledge and what a weapon came to.
        for (e in ev) when (e.type) {
            EventType.SIGNATURE_DISCOVERED -> if (e.data["first"] != "false") {
                val sig = SignatureCatalog.byId[e.data["key"].orEmpty().removePrefix("sig:")]
                val body = if (sig != null) "${sig.name}. ${sig.flavor}".let { b -> if (b.endsWith(".")) b else "$b." } else LegacyProse.display(e.text)
                add(3, e, Story(Kind.SIGNATURE, "New recipe discovered", body, subjectIds = e.subjectIds, opensNotebook = true))
            }
            EventType.DISCOVERY -> if (e.data["rung"] != null || e.data["key"]?.startsWith("sig:") == true) {
                add(3, e, Story(Kind.CLUE, "A recipe clue", LegacyProse.display(e.text), opensNotebook = true))
            } else if (e.priority >= 3) {
                add(3, e, Story(Kind.PAIRING, "A pairing learned", LegacyProse.display(e.text).removePrefix("Pairing learned. "), opensNotebook = true))
            }
            EventType.WEAPON_BROKEN -> {
                val heroId = e.subjectIds.getOrNull(1)
                val died = ev.any { it.type == EventType.HERO_DIED && heroId != null && heroId in it.subjectIds }
                val w = weapon(e.subjectIds.firstOrNull())
                val h = hero(heroId)
                val body = if (!died && h != null) "$h returned without it." else LegacyProse.display(e.text)
                add(3, e, Story(Kind.WEAPON_BROKEN, if (w != null) "$w broke" else "A weapon broke", body, subjectIds = e.subjectIds))
            }
            EventType.ARTIFACT_RETURNED -> {
                val w = weapon(e.subjectIds.firstOrNull())
                val era = e.data["era"]
                val body = listOfNotNull(era?.let { "Last recorded in era $it." }, "Hone it to restore its dormant properties.".takeIf { era != null }).joinToString(" ")
                    .ifEmpty { LegacyProse.display(e.text) }
                add(3, e, Story(Kind.LEGEND_RETURNED, if (w != null) "$w returned" else "A legend returned", body, subjectIds = e.subjectIds))
            }
            EventType.WEAPON_INHERITED -> if (e.id !in used) {
                val (w, heir, former) = Triple(weapon(e.subjectIds.getOrNull(0)), hero(e.subjectIds.getOrNull(1)), hero(e.subjectIds.getOrNull(2)))
                val story = if (w != null && heir != null) Story(
                    Kind.INHERITED, "$heir inherited $w", former?.let { "It belonged to $it." }.orEmpty().ifEmpty { LegacyProse.display(e.text) }, subjectIds = e.subjectIds,
                ) else Story(Kind.INHERITED, "A weapon was passed on", LegacyProse.display(e.text), subjectIds = e.subjectIds)
                add(3, e, story)
            }
            else -> Unit
        }

        // Tier 4: consequences for the shop and for heroes.
        for (e in ev) when (e.type) {
            EventType.COMMISSION_COMPLETED -> add(4, e, Story(Kind.COMMISSION_DONE, "Commission collected", LegacyProse.display(e.text), subjectIds = e.subjectIds))
            EventType.COMMISSION_EXPIRED -> {
                val buyer = hero(e.subjectIds.firstOrNull())
                add(4, e, Story(Kind.COMMISSION_EXPIRED, if (buyer != null) "$buyer's order expired" else "An order expired", LegacyProse.display(e.text), subjectIds = e.subjectIds))
            }
            EventType.PLEDGE_RESOLVED -> add(4, e, Story(Kind.CONSEQUENCE, "A debt was settled", LegacyProse.display(e.text), subjectIds = e.subjectIds))
            EventType.ENCOUNTER_RESOLVED -> if (e.priority >= 4) add(4, e, Story(Kind.CONSEQUENCE, "A visitor's offer was answered", LegacyProse.display(e.text), subjectIds = e.subjectIds))
            EventType.AMBITION_FULFILLED -> add(4, e, Story(Kind.AMBITION, hero(e.subjectIds.firstOrNull())?.let { "$it fulfilled an ambition" } ?: "An ambition was fulfilled", LegacyProse.display(e.text), subjectIds = e.subjectIds))
            EventType.HERO_RETIRED -> add(4, e, Story(Kind.RETIRED, hero(e.subjectIds.firstOrNull())?.let { "$it retired" } ?: "A hero retired", LegacyProse.display(e.text), subjectIds = e.subjectIds))
            EventType.GUILD_FOUNDED -> add(4, e, Story(Kind.GUILD, "A guild was founded", LegacyProse.display(e.text), subjectIds = e.subjectIds))
            else -> Unit
        }

        // Tier 5: an elite felled (its milestone and any title folded in), and scarce material brought home.
        for (e in ev.filter { it.type == EventType.ELITE_SLAIN }) {
            val id = e.subjectIds.firstOrNull()
            val h = hero(id)
            val w = weapon(e.subjectIds.getOrNull(1))
            val foe = e.data["foe"]
            val gold = e.data["gold"]?.toIntOrNull()
            val milestone = ev.firstOrNull { it.type == EventType.MILESTONE && it.data["milestone"] == "ELITE_SLAIN" && it.id !in used }
            val title = ev.firstOrNull { it.type == EventType.MILESTONE && it.data["milestone"] == "WEAPON_FIVE_KILLS" && it.id !in used }
            val loot = ev.firstOrNull { it.type == EventType.EXPEDITION_WON && it.data["material"] != null && id != null && id in it.subjectIds && it.id !in used }
            val lootName = loot?.data?.get("material")?.let { m -> f.content.materials.firstOrNull { it.id.value == m } }?.takeIf { it.dailySupplierStock != null }?.name
            val story = if (h != null && foe != null && gold != null) {
                val used1 = if (w != null) "Used $w and brought back $gold gold." else "Fought without a weapon and brought back $gold gold."
                val extra = listOfNotNull(lootName?.let { "Also brought back $it." }, title?.let { "The weapon earned a title." })
                Story(Kind.ELITE, "$h defeated $foe", (listOf(used1) + extra).take(2).joinSentences(), subjectIds = e.subjectIds)
            } else Story(Kind.ELITE, "An elite foe fell", LegacyProse.display(e.text), subjectIds = e.subjectIds)
            add(5, e, story, milestone, title, loot.takeIf { lootName != null })
        }
        for (e in ev.filter { it.type == EventType.EXPEDITION_WON && it.data["material"] != null && it.id !in used }) {
            val m = f.content.materials.firstOrNull { it.id.value == e.data["material"] }?.takeIf { it.dailySupplierStock != null } ?: continue
            val h = hero(e.subjectIds.firstOrNull())
            add(5, e, Story(Kind.SCARCE, if (h != null) "$h brought back ${m.name}" else "${m.name} came home", "${m.name} is in limited supply at the supplier.", subjectIds = e.subjectIds))
        }

        // Tier 6, when there is room: a first sale or a genuinely new arrival. At most one.
        val quiet = ev.firstOrNull { it.type == EventType.MILESTONE && it.data["milestone"] == "FIRST_SALE" && it.id !in used }?.let { e ->
            Candidate(6, e.serial, Story(Kind.FIRST_SALE, "First sale", LegacyProse.display(e.text), eventIds = listOf(e.id)))
        } ?: ev.firstOrNull { it.type == EventType.HERO_ARRIVED && it.priority >= 3 && it.id !in used }?.let { e ->
            Candidate(6, e.serial, Story(Kind.ARRIVAL, hero(e.subjectIds.firstOrNull())?.let { "$it arrived" } ?: "A new arrival", LegacyProse.display(e.text), subjectIds = e.subjectIds, eventIds = listOf(e.id)))
        }
        return out + listOfNotNull(quiet)
    }

    /**
     * The default view holds every mandatory block (the era's end, the siege, deaths, a new siege warning) and then the
     * most important of the rest up to [MAX_STORIES]. Several deaths become one block when they would not fit; nothing
     * that is left over is dropped, it goes to [Digest.more].
     */
    private fun select(all: List<Candidate>): Pair<List<Story>, List<Story>> {
        val ordered = all.sortedWith(compareBy({ it.tier }, { it.serial }))
        var mandatory = ordered.filter { it.story.mandatory }
        val deaths = mandatory.filter { it.story.kind == Kind.DEATH }
        val rest = ordered.filter { !it.story.mandatory }
        var merged: Story? = null
        if (deaths.size > 1 && mandatory.size > MAX_STORIES) {
            val parts = deaths.map { it.story }
            val heroNames = parts.map { it.heading.removeSuffix(" died") }
            merged = Story(
                Kind.DEATHS, "${parts.size} heroes died", "${names(heroNames)} died.",
                subjectIds = parts.flatMap { it.subjectIds }, eventIds = parts.flatMap { it.eventIds }, details = parts.map { it.body.ifEmpty { it.heading } },
                mandatory = true,
            )
            mandatory = mandatory.filter { it.story.kind != Kind.DEATH } + Candidate(1, deaths.first().serial, merged)
            mandatory = mandatory.sortedWith(compareBy({ it.tier }, { it.serial }))
        }
        val room = (MAX_STORIES - mandatory.size).coerceAtLeast(0)
        val main = mandatory + rest.take(room)
        return main.map { it.story } to rest.drop(room).map { it.story }
    }

    // ---- the shop ----

    private fun shopLines(f: Facts): List<String> {
        val ev = f.events
        val ledger = f.resolution?.ledger
        val shopDay = ev.firstOrNull { it.type == EventType.SHOP_DAY }?.data
        val commissions = ev.count { it.type == EventType.COMMISSION_COMPLETED }
        val collectors = ev.count { it.type == EventType.WORLD_EVENT && it.data["event"] == "collector" }
        val shelfSales = ev.count { it.type == EventType.WEAPON_SOLD }
        val sold = shelfSales + commissions + collectors
        val income: Map<IncomeKind, Int>? = ledger?.income
            ?: shopDay?.let { d -> IncomeKind.entries.associateWith { d[it.name]?.toIntOrNull() ?: 0 } }
            ?: recorded(ev).takeIf { sold > 0 }
        // The town's thanks is not shop takings.
        val gold = income?.let { it.values.sum() - (it[IncomeKind.TRIBUTE] ?: 0) }
        val soldKnown = income != null
        val lines = mutableListOf<String>()
        when {
            gold == null -> Unit
            gold == 0 && sold == 0 && soldKnown -> lines += "No sales today."
            sold > 0 -> lines += "Shop income $gold gold. ${count(sold, "weapon")} sold."
            else -> lines += "Shop income $gold gold."
        }
        problem(f)?.let { lines += it }
        return lines
    }

    /** The day's takings as its sale records tell it, for a day with neither a ledger nor a stored shop-day record. */
    private fun recorded(ev: List<EventRecord>): Map<IncomeKind, Int> {
        fun sum(type: EventType, amount: (EventRecord) -> Int?) = ev.filter { it.type == type }.sumOf { amount(it) ?: 0 }
        return mapOf(
            IncomeKind.SHELF_SALE to sum(EventType.WEAPON_SOLD) { e -> (e.data["price"]?.toIntOrNull() ?: 0) - (e.data["tradeIn"]?.toIntOrNull() ?: 0) },
            IncomeKind.SALE_BONUS to sum(EventType.WEAPON_SOLD) { it.data["bonus"]?.toIntOrNull() },
            IncomeKind.COMMISSION to sum(EventType.COMMISSION_COMPLETED) { it.data["reward"]?.toIntOrNull() },
            IncomeKind.COLLECTOR to sum(EventType.WORLD_EVENT) { e -> e.data["price"]?.toIntOrNull()?.takeIf { e.data["event"] == "collector" } },
        )
    }

    /**
     * The one grouped problem worth acting on, from the day's typed visits (so only a fresh day has one): an empty
     * shelf first, then a weapon class nothing suited, then a recorded price objection, then a purse that could not
     * meet the shelf. A visitor who could not pay is not said to think the price unfair.
     */
    private fun problem(f: Facts): String? {
        val left = f.resolution?.visits.orEmpty().filter { it.kind == VisitKind.BROWSE && it.purchasedWeaponId == null }
        fun who(reason: VisitReason) = left.filter { it.reason == reason }.map { it.heroId?.value ?: it.heroName }.distinct().size
        who(VisitReason.EMPTY_SHELVES).takeIf { it > 0 }?.let { return "The shelf was empty when ${count(it, "visitor", words = true).lowercase()} arrived." }
        val byClass = left.filter { it.reason == VisitReason.NOT_SUITED }.mapNotNull { v -> v.customer?.classId?.let { c -> f.content.classById[c]?.name?.let { n -> n to (v.heroId?.value ?: v.heroName) } } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.distinct().size }
        byClass.entries.filter { it.value >= 2 }.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.let { (cls, n) ->
            return "${number(n).replaceFirstChar { c -> c.uppercase() }} ${cls}s found no suitable weapon."
        }
        who(VisitReason.OVERPRICED).takeIf { it >= 2 }?.let { return "${count(it, "visitor", words = true)} thought the price was too high." }
        who(VisitReason.TOO_EXPENSIVE).takeIf { it >= 2 }?.let { return "${count(it, "visitor", words = true)} couldn't afford anything on the shelf." }
        return null
    }

    // ---- before tomorrow ----

    /**
     * Notices for the most recent resolved day, from the state after resolution: "tomorrow" is the next playable day.
     * Siege first, then an order due, then a pending choice, then a supply disruption, then the shop's own lead.
     */
    private fun forward(state: GameState, content: ContentCatalog, config: BalanceConfig): List<String> {
        val out = mutableListOf<String>()
        if (state.isEnded) return out
        val siegeIn = state.town.nextSiegeDay - state.day
        if (siegeIn == 0) out += "The siege starts after tomorrow's trading."
        state.commissions.values.filter { it.status == CommissionStatus.ACCEPTED || it.status == CommissionStatus.OFFERED }
            .filter { it.deadlineDay == state.day && state.heroes[it.buyerId]?.isAlive == true }
            .sortedBy { it.id.value }.firstOrNull()?.let { c ->
                val buyer = state.heroes.getValue(c.buyerId).fullName
                val what = content.familyById[c.familyId]?.name?.lowercase() ?: "weapon"
                val due = "$buyer's $what order is due on day ${c.deadlineDay}."
                out += when {
                    c.status == CommissionStatus.OFFERED -> "$due You haven't accepted it yet."
                    Commissions.pick(state.weapons.values, c, config) == null -> "$due No matching weapon is ready."
                    else -> due
                }
            }
        if (state.pendingBlessingOffer.isNotEmpty()) out += "The town has offered a blessing for the forge."
        if (state.pendingRelicOffer.isNotEmpty()) out += "A workshop relic is waiting for your choice."
        if (state.encounter?.isOpen == true) out += "A visitor is waiting at the forge."
        if (state.worldFlags[WorldEvents.FLAG_CARAVAN_DELAYED] == state.day) out += "Caravan delayed. No rare-material delivery tomorrow."
        if (state.worldFlags[WorldEvents.FLAG_FESTIVAL] == state.day) out += "Merchant festival tomorrow. Expect more shoppers."
        val lead = Advice.lead(state, content, config)
        if (lead.kind != LeadKind.FORGE_FOR_BUYERS && lead.kind != LeadKind.CHOOSE_BLESSING && lead.kind != LeadKind.FIRST_BLADE) {
            val line = Lines.lead(lead, state, content, config)
            out += listOfNotNull(line.action, line.reason).joinSentences()
        }
        val trait = content.siegeTrait(state.siege?.takeIf { it.siegeDay == state.town.nextSiegeDay }?.traitId)
        if (trait != null && siegeIn in 0..2) out += trait.counsel
        return out.distinct()
    }

    /** What an older paper may say about the days ahead: only what its own records announced, by absolute day. */
    private fun recordedNotices(ev: List<EventRecord>): List<String> = buildList {
        for (e in ev.filter { it.type == EventType.WORLD_EVENT }) when (e.data["event"]) {
            "caravan_delayed" -> add("Caravan delayed. No rare-material delivery on day ${e.day + 1}.")
            "merchant_festival" -> add("Merchant festival on day ${e.day + 1}. Expect more shoppers.")
        }
    }.distinct()

    // ---- words ----

    private val EventRecord.serial: Int get() = id.drop(1).toIntOrNull() ?: 0

    private fun names(list: List<String>): String = when (list.size) {
        0 -> ""
        1 -> list[0]
        else -> list.dropLast(1).joinToString(", ") + " and " + list.last()
    }

    private val smallNumbers = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
    private fun number(n: Int) = smallNumbers.getOrNull(n) ?: n.toString()
    private fun count(n: Int, noun: String, words: Boolean = false) = (if (words) number(n).replaceFirstChar { it.uppercase() } else n.toString()) + " $noun" + if (n == 1) "" else "s"
}
