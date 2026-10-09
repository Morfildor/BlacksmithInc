package com.tinyblacksmith.core.shopday

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.model.*

/** A lead as the Shop and the Tomorrow card print it: what to do, and the recorded reason (absent when its facts are). */
data class LeadLine(val action: String, val reason: String?)

/**
 * The one vocabulary of the shop day: authored templates over typed reasons, typed factors and recorded numbers.
 * A clause whose field is missing is dropped; a line with nothing left is the bare reason label. Nothing here states a
 * chance, a weight or anything the record does not hold.
 */
object Lines {

    /** The bare label of a visit's outcome; follows a name or a count ("Mira ...", "3 ..."). */
    fun reason(reason: VisitReason): String = when (reason) {
        VisitReason.EMPTY_SHELVES -> "found the shelves bare"
        VisitReason.TOO_EXPENSIVE -> "could afford nothing on the shelf"
        VisitReason.NOT_BETTER -> "found nothing better than the blade in hand"
        VisitReason.OVERPRICED -> "balked at the price"
        VisitReason.NOT_SUITED -> "found nothing to suit"
        VisitReason.UNDECIDED -> "left undecided"
        VisitReason.WORN_OUT -> "replaced a worn blade"
        VisitReason.GREAT_FIT -> "found a blade that suits"
        VisitReason.GOOD_ENOUGH -> "bought a better blade"
        VisitReason.COMMISSION_DELIVERED -> "collected a commission"
        VisitReason.COLLECTOR_PURCHASE -> "bought for a collection"
        VisitReason.TASTE_MATCH -> "took a blade of their favoured element"
        VisitReason.PRIZED -> "took a prize for their collection"
        VisitReason.STORIED -> "took a blade with a name"
        VisitReason.COUNTERS_THREAT -> "armed against the besieger"
        VisitReason.RESISTED -> "passed over a blade the besieger shrugs off"
    }

    fun factor(factor: VisitFactor): String = when (factor) {
        VisitFactor.SUITS_CLASS -> "suits their class"
        VisitFactor.OFF_CLASS -> "not their kind of weapon"
        VisitFactor.ELEMENT_TASTE -> "their favoured element"
        VisitFactor.LIKES_NOVELTY -> "elemental, which they like"
        VisitFactor.STRONGER_THAN_OWN -> "stronger than their own"
        VisitFactor.NOT_STRONGER_THAN_OWN -> "no stronger than their own"
        VisitFactor.OWN_BLADE_WORN -> "their own blade is worn"
        VisitFactor.UNARMED -> "they carry nothing"
        VisitFactor.STORIED_BLADE -> "a blade with a name"
        VisitFactor.COLLECTOR_PRIZE -> "a prize for a collector"
        VisitFactor.CAN_AFFORD -> "within their purse"
        VisitFactor.CANNOT_AFFORD -> "beyond their purse"
        VisitFactor.ABOVE_THEIR_CEILING -> "priced above what they hold fair"
        VisitFactor.REGULAR -> "trusts the shop"
        VisitFactor.COUNTERS_THREAT -> "bites the besieger"
        VisitFactor.THREAT_RESISTS -> "the besieger shrugs it off"
    }

    /** "Mira Ashwood, Ranger, a regular. Carries a worn Iron Bow." */
    fun customer(visit: MarketVisit, content: ContentCatalog): String {
        val c = visit.customer ?: return visit.heroName
        val who = listOfNotNull(c.name, content.classById[c.classId]?.name, "a regular".takeIf { c.regular }).joinToString(", ")
        val worn = visit.considered.any { VisitFactor.OWN_BLADE_WORN in it.factors }
        return "$who. " + (c.equipped?.let { "Carries ${if (worn) "a worn " else ""}${it.name}." } ?: "Carries no weapon.")
    }

    /** One blade a visitor weighed: "Iron Sword, 60 gold: suits their class, beyond their purse; short by 12 gold". */
    fun considered(item: Considered, day: ShopDayScript): String =
        listOfNotNull(day.blade(item.weaponId)?.name, "${item.price} gold").joinToString(", ") +
            (if (item.factors.isEmpty()) "" else ": " + item.factors.joinToString(", ") { factor(it) }) +
            (item.shortBy?.let { "; short by $it gold" } ?: "")

    /** Why they bought or left, with the numbers the visit recorded. */
    fun decision(visit: MarketVisit, day: ShopDayScript, content: ContentCatalog): String {
        val c = visit.customer
        val own = c?.equipped?.name
        val className = c?.let { content.classById[it.classId]?.name }
        val sale = visit.sale
        val blade = day.blade(visit.purchasedWeaponId)?.name
        fun with(f: VisitFactor) = visit.considered.firstOrNull { f in it.factors }
        val clauses: List<String?> = when (visit.reason) {
            VisitReason.TOO_EXPENSIVE -> {
                val funds = visit.considered.firstNotNullOfOrNull { k -> k.shortBy?.let { k.price - it } }
                // The shelf as this visitor found it: the opening prices less what earlier visitors took.
                val gone = day.visits.filter { it.seq < visit.seq }.mapNotNull { it.purchasedWeaponId }
                val cheapest = day.prices.filterKeys { it !in gone }.values.minOrNull()
                listOf(funds?.let { "Could pay up to $it gold" + (cheapest?.let { p -> "; the cheapest blade is $p gold" } ?: "") + "." })
            }
            VisitReason.OVERPRICED -> listOf(with(VisitFactor.ABOVE_THEIR_CEILING)?.let { k -> day.blade(k.weaponId)?.let { "${it.name} at ${k.price} gold is more than they hold fair." } })
            VisitReason.NOT_SUITED -> listOf(with(VisitFactor.OFF_CLASS)?.let { k -> day.blade(k.weaponId)?.let { b -> className?.let { "${b.name} is not a weapon for a $it." } } })
            VisitReason.NOT_BETTER -> listOf(own?.let { "Nothing on the shelf beats their $it." })
            VisitReason.EMPTY_SHELVES, VisitReason.UNDECIDED -> emptyList()
            VisitReason.WORN_OUT -> listOf(own?.let { "Their $it was worn." }, bought(blade, sale))
            VisitReason.GREAT_FIT -> listOf(blade?.let { b -> className?.let { "$b suits a $it." } }, bought(blade, sale))
            VisitReason.GOOD_ENOUGH -> listOf(
                if (own != null) blade?.let { "$it is stronger than their $own." } else "Came in unarmed.".takeIf { with(VisitFactor.UNARMED) != null },
                bought(blade, sale),
            )
            VisitReason.COMMISSION_DELIVERED -> listOf(sale?.let { "Collected the commissioned ${blade ?: "blade"} and paid ${it.cashPaid} gold." })
            VisitReason.COLLECTOR_PURCHASE -> listOf(sale?.let { "Paid ${it.cashPaid} gold for ${blade ?: "a blade"} and carried it off." })
            VisitReason.TASTE_MATCH -> listOf(sidegrade(blade, own, "it is of the element they favour"), bought(blade, sale))
            VisitReason.PRIZED -> listOf(sidegrade(blade, own, "fine work is what they collect"), bought(blade, sale))
            VisitReason.STORIED -> listOf(sidegrade(blade, own, "it has a name"), bought(blade, sale))
            VisitReason.COUNTERS_THREAT -> listOf(blade?.let { "With the siege near, $it is of the element the besieger fears." }, bought(blade, sale))
            VisitReason.RESISTED -> listOf(with(VisitFactor.THREAT_RESISTS)?.let { k -> day.blade(k.weaponId)?.let { "With the siege near, ${it.name} is of the element the besieger shrugs off; it stayed on the shelf." } })
        }
        val trade = sale?.takeIf { it.tradeInWeaponId != null && visit.kind == VisitKind.BROWSE }?.let { "${own ?: "Their old blade"} came back in part payment: ${it.tradeInCredit} gold off, ${it.cashPaid} gold in coin." }
        val bonus = sale?.takeIf { it.saleBonus > 0 }?.let { "The town's blessing added ${it.saleBonus} gold." }
        val stipend = sale?.takeIf { it.stipend > 0 }?.let { "Their guild paid ${it.stipend} gold of the price; ${it.cashPaid} gold came from their own purse." }
        return (clauses + trade + stipend + bonus).filterNotNull().joinToString(" ").ifEmpty { reason(visit.reason).replaceFirstChar { it.uppercase() } + "." }
    }

    private fun sidegrade(blade: String?, own: String?, why: String): String? = blade?.let { b -> own?.let { "$b is no stronger than their $it, but $why." } }

    private fun bought(blade: String?, sale: Sale?): String? = sale?.listedPrice?.let { "Bought ${blade ?: "a blade"} for $it gold." }

    /**
     * What the counter knows this customer by: the visit's stored cue ([Recognitions]) in words, or null when the visit
     * has none or a field its sentence needs is gone. Narration, never speech. [state] is read for the lineage only.
     */
    fun recognition(visit: MarketVisit, day: ShopDayScript, state: GameState): String? {
        val r = visit.recognition ?: return null
        val hero = visit.heroName
        val carried = visit.customer?.equipped?.takeIf { it.weaponId == r.weaponId }
        val blade = (day.blade(r.weaponId) ?: carried)?.name
        return when (r.cue) {
            RecognitionCue.FIRST_VISIT -> "$hero's first time at your counter."
            RecognitionCue.FIRST_BLADE -> blade?.let { "$hero leaves with $it: a first blade from your forge." }
            RecognitionCue.BECAME_REGULAR -> listOfNotNull(r.count?.takeIf { it >= 2 }?.let { "That makes $it from your forge." }, "$hero is a regular now.").joinToString(" ")
            RecognitionCue.REGULAR_RETURNS -> r.day?.let { "$hero, a regular, last in on day $it." }
            RecognitionCue.STILL_CARRIES -> blade?.let { b -> r.count?.let { "$hero still carries $b: ${if (it == 1) "1 victory" else "$it victories"} with it." } }
            RecognitionCue.BLADE_WORN -> blade?.let { "$hero lays $it on the counter. The edge is worn." }
            RecognitionCue.HELD_THE_WALL -> blade?.let { b -> r.day?.let { "$hero held the wall on day $it with $b." } }
            RecognitionCue.SLEW_AN_ELITE -> blade?.let { b -> carried?.title?.let { "$hero brought down an elite foe with $b. They call it '$it' now." } }
            RecognitionCue.KEPT_THE_VOW -> r.count?.let { "$hero kept the vow, $it foes routed, and came back for more steel." }
            RecognitionCue.MENTORS_BLADE -> blade?.let { b -> visit.customer?.mentorName?.let { "$hero trained under $it and carries $it's old $b." } }
            RecognitionCue.OF_THE_LINE -> visit.heroId?.let { state.heroes[it]?.lineageId }?.let { id -> state.legacy.lineages.firstOrNull { it.id == id } }?.let { "$hero, of the line of ${it.heroName}, who ${it.deed}." }
            RecognitionCue.WAITED_YESTERDAY -> "$hero could not get in yesterday and is first through the door."
            RecognitionCue.WANT_ANSWERED -> blade?.let { b -> r.day?.let { "$hero left without such a blade on day $it; today $b was on the shelf." } }
        }
    }

    /** One group of the tally: "3 could afford nothing on the shelf", "1 bought (Iron Axe, 60 gold)". */
    fun tally(group: TallyGroup, day: ShopDayScript): String {
        val n = group.visits.size
        return when (group.outcome) {
            TallyOutcome.COMMISSION -> "$n collected a commission"
            TallyOutcome.COLLECTOR -> "$n sold to a collector"
            TallyOutcome.LEFT -> "$n ${reason(group.reason ?: VisitReason.UNDECIDED)}"
            TallyOutcome.BOUGHT -> "$n bought" + (group.visits.singleOrNull()?.let { v -> day.blade(v.purchasedWeaponId)?.let { b -> v.sale?.let { " (${b.name}, ${it.cashPaid} gold)" } } } ?: "")
        }
    }

    fun quiet(quiet: QuietDay): String = when (quiet.kind) {
        QuietKind.NO_VISITORS -> "Nobody came to the shop today."
        QuietKind.EMPTY_SHELF -> "${names(quiet.visitors.map { it.heroName }.distinct())} looked in and found the shelves bare."
    }

    /**
     * The record's own sentence, then only what the stored result says: the counterfactual in those words, a new title,
     * what was brought back. No card says a blade decided anything.
     */
    fun aftermath(card: AftermathCard, content: ContentCatalog): String = listOfNotNull(
        card.text,
        "The blade left the shop this morning.".takeIf { card.kind == AftermathKind.WIN_NEW_BLADE },
        when (card.counterfactual) {
            Counterfactual.OLD_BLADE -> "With the old ${card.oldWeapon?.name ?: "blade"} the same fight was lost."
            Counterfactual.BARE_HANDED -> "Bare-handed the same fight was lost."
            null -> null
        },
        card.title?.let { "The blade has earned a name: $it." },
        card.factionId?.takeIf { card.matchupHelped }?.let { content.factionById[it] }?.let { "Its make tells against the ${it.name}." },
        card.materialId?.let { content.materialById[it] }?.let { "Brought back ${it.name}." }?.takeIf { card.kind == AftermathKind.SCARCE_LOOT || card.kind == AftermathKind.ELITE_SLAIN },
    ).joinToString(" ")

    /** The lead in words. Names come from [state]; a name that is gone drops its clause. */
    fun lead(lead: Lead, state: GameState, content: ContentCatalog, config: BalanceConfig): LeadLine {
        val hero = lead.heroId?.let { state.heroes[it]?.fullName }
        val asked = lead.commissionId?.let { state.commissions[it] }?.let { Commissions.describe(it, content, config) }
        val faction = lead.factionId?.let { content.factionById[it]?.name }
        val days = lead.days?.let { if (it <= 0) "today" else if (it == 1) "tomorrow" else "in $it days" }
        return when (lead.kind) {
            LeadKind.FIRST_BLADE -> LeadLine("Forge your first blade", lead.count?.let { "${count(it, "hero")} in Emberfall and nothing on the shelf." })
            LeadKind.CHOOSE_BLESSING -> LeadLine("Choose a blessing", "The town's thanks are waiting.")
            LeadKind.ANSWER_REQUEST -> LeadLine(
                "Answer ${hero?.let { "$it's" } ?: "a"} request",
                listOfNotNull(asked?.let { "A $it" }, lead.gold?.let { "$it gold" }, days?.let { "the offer lapses $it" }).joinToString("; ").ifEmpty { null }?.plus("."),
            )
            LeadKind.FORGE_FOR_REQUEST -> LeadLine(
                "Forge for ${hero?.let { "$it's" } ?: "a"} request",
                listOfNotNull(asked?.let { "Nothing in the shop is a $it" }, days?.let { "due $it" }).joinToString("; ").ifEmpty { null }?.plus("."),
            )
            LeadKind.LIST_STOCK -> LeadLine("Put a blade on the shelf", lead.count?.let { "The shelf is empty; ${count(it, "blade")} in storage." })
            LeadKind.FORGE_STOCK -> LeadLine("Forge something to sell", "The shelf and the storeroom are empty.")
            LeadKind.PRICES_TOO_HIGH -> LeadLine(
                "Lower a price",
                listOfNotNull(lead.count?.let { "${count(it, "customer")} left over the price yesterday" }, lead.gold?.let { "the cheapest blade is $it gold" }).joinToString("; ").ifEmpty { null }?.plus("."),
            )
            LeadKind.ARM_DEFENDERS -> LeadLine(
                "Arm the defenders",
                listOfNotNull(listOfNotNull(faction, days).joinToString(" ").ifEmpty { null }, lead.element?.let { "weak to ${it.name.lowercase()}" }).joinToString(", ").ifEmpty { null }?.plus("."),
            )
            LeadKind.ANSWER_WANT -> LeadLine(
                "Forge ${lead.familyId?.let { content.familyById[it] }?.let { withArticle(it.name.lowercase()) } ?: "a blade"}${hero?.let { " for $it" }.orEmpty()}",
                listOfNotNull(
                    lead.power?.let { "Nothing on the shelf was what they came for: it wants power $it or better" },
                    lead.gold?.let { "they can spend about ${about(it)} gold" },
                    lead.days?.let { if (it <= 0) "they stop asking tomorrow" else "they ask for ${count(it + 1, "more day")}" },
                ).joinToString("; ").ifEmpty { null }?.plus("."),
            )
            LeadKind.FORGE_FOR_BUYERS -> LeadLine(
                "Forge for today's buyers",
                when {
                    hero != null -> "$hero's ${lead.weaponId?.let { state.weapons[it]?.name } ?: "blade"} is worn."
                    lead.gold != null && lead.count != null -> "${count(lead.count, "hero")} can afford the cheapest blade (${lead.gold} gold)."
                    lead.count != null -> "${count(lead.count, "hero")} ${if (lead.count == 1) "carries" else "carry"} no blade."
                    else -> null
                },
            )
        }
    }

    /**
     * The "story" section of the item sheet (E5): one line per entry of `Legacy.story(weapon)`, oldest first. Every line
     * is a stored history entry with its day in front; an entry from an earlier era says which.
     */
    fun story(weapon: Weapon, currentEra: Int): List<String> = com.tinyblacksmith.core.legacy.Legacy.story(weapon).map { storyLine(it, currentEra) }

    fun storyLine(entry: HistoryEntry, currentEra: Int): String = (if (entry.era == currentEra) "Day ${entry.day}" else "Era ${entry.era}, day ${entry.day}") + ": " + entry.text

    /** The dormant marker of a returned legend: what sleeps in it and what wakes it; null when nothing does. Works for a blade or its counter snapshot. */
    fun dormant(dormantAffixes: List<AffixId>, content: ContentCatalog): String? {
        val names = dormantAffixes.mapNotNull { content.affixById[it]?.name }
        return if (names.isEmpty()) null else "Dormant: ${names.joinToString(", ")}. Hone it once to wake ${if (names.size == 1) "it" else "them"}."
    }

    /**
     * A Legend Board entry in lines: the blade and its fame, what it was, who carried it, then its story. An entry
     * written before its make was recorded says so instead of inventing it.
     */
    fun legend(entry: LegendEntry, content: ContentCatalog, currentEra: Int): List<String> {
        val what = if (entry.lostToTime) "Its properties are lost to time." else listOfNotNull(
            entry.signatureId?.let { com.tinyblacksmith.core.crafting.SignatureCatalog.byId[it] }?.let { "A signature blade: ${it.name}" },
            entry.affixes.mapNotNull { content.affixById[it]?.name }.takeIf { it.isNotEmpty() }?.joinToString(", "),
            entry.flaws.mapNotNull { content.affixById[it]?.name }.takeIf { it.isNotEmpty() }?.let { "flawed: ${it.joinToString(", ")}" },
            entry.catalystId?.let { content.materialById[it] }?.let { "forged with ${it.name}" },
        ).joinToString("; ").ifEmpty { "A plain blade, with nothing worked into it" }.replaceFirstChar { it.uppercase() }.let { if (it.endsWith(".")) it else "$it." }
        return listOf(
            "${entry.weaponName}, ${entry.title}. Era ${entry.era}; ${entry.kills} ${if (entry.kills == 1) "victory" else "victories"}; fame ${entry.fame}.",
            what,
            if (entry.owners.isEmpty()) "Nobody is remembered as having carried it." else "Carried by ${names(entry.owners)}.",
        ) + entry.ownerLine.map { storyLine(it, currentEra) }
    }

    /**
     * The besieger and what tells against it, for the Forge and the shelf: "Frost bites the Ashclaw Raiders; fire glances
     * off them." A clause whose element the faction lacks is dropped; null when nothing is left to say.
     */
    fun threat(threat: Threat, content: ContentCatalog): String? {
        val name = content.factionById[threat.factionId]?.name ?: return null
        fun word(e: com.tinyblacksmith.core.content.Element) = e.name.lowercase()
        return listOfNotNull(
            threat.weakTo?.let { "${word(it).replaceFirstChar { c -> c.uppercase() }} bites the $name" },
            threat.resists?.let { if (threat.weakTo == null) "${word(it).replaceFirstChar { c -> c.uppercase() }} glances off the $name" else "${word(it)} glances off them" },
        ).joinToString("; ").ifEmpty { null }?.plus(".")
    }

    /** The label beside one blade or one material of that element. */
    fun threatMark(mark: ThreatMark, threat: Threat, content: ContentCatalog): String {
        val name = content.factionById[threat.factionId]?.name ?: "besiegers"
        return if (mark == ThreatMark.COUNTERS) "Bites the $name" else "The $name resist it"
    }

    /** Why a request is made, for its card: `Commissions.why` with the names from [state]. Null for an ordinary request or a patron who is gone. */
    fun commissionWhy(commission: Commission, state: GameState): String? {
        val buyer = state.heroes[commission.buyerId]?.fullName ?: return null
        return Commissions.why(commission, buyer, commission.recipientId?.let { state.heroes[it]?.fullName })
    }

    /** A hero's standing want in a line: "Wren Kestrel wants a bow; can spend about 90 gold." Null without one. The numbers are `Hero.want`'s own. */
    fun want(hero: Hero, content: ContentCatalog): String? = hero.want?.let { w ->
        "${hero.fullName} wants ${content.familyById[w.familyId]?.let { withArticle(it.name.lowercase()) } ?: "a blade"}; can spend about ${about(w.budget)} gold."
    }

    /** A purse as the counter would say it: to the ten below, exact under twenty. */
    private fun about(gold: Int) = if (gold < 20) gold else gold / 10 * 10

    private fun withArticle(noun: String) = (if (noun.firstOrNull()?.lowercaseChar() in setOf('a', 'e', 'i', 'o', 'u')) "an " else "a ") + noun

    private fun count(n: Int, noun: String) = "$n $noun" + if (n == 1) "" else if (noun == "hero") "es" else "s"

    private fun names(list: List<String>): String = when (list.size) {
        0 -> ""
        1 -> list[0]
        else -> list.dropLast(1).joinToString(", ") + " and " + list.last()
    }
}
