package com.tinyblacksmith.core.shopday

import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.text.LegacyProse
import com.tinyblacksmith.core.text.joinSentences

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
        VisitReason.EMPTY_SHELVES -> "found an empty shelf"
        VisitReason.TOO_EXPENSIVE -> "couldn't afford anything"
        VisitReason.NOT_BETTER -> "found no upgrade for their weapon"
        VisitReason.OVERPRICED -> "thought the price was too high"
        VisitReason.NOT_SUITED -> "found no weapon that suited them"
        VisitReason.UNDECIDED -> "left undecided"
        VisitReason.WORN_OUT -> "replaced a worn weapon"
        VisitReason.GREAT_FIT -> "found a weapon that suited them"
        VisitReason.GOOD_ENOUGH -> "bought an upgrade"
        VisitReason.COMMISSION_DELIVERED -> "collected a commission"
        VisitReason.COLLECTOR_PURCHASE -> "bought a weapon for their collection"
        VisitReason.TASTE_MATCH -> "chose their favorite element"
        VisitReason.PRIZED -> "chose a fine weapon for their collection"
        VisitReason.STORIED -> "chose a weapon with a history"
        VisitReason.COUNTERS_THREAT -> "bought a weapon suited to the coming siege"
        VisitReason.RESISTED -> "passed on a weapon the attackers resist"
    }

    fun factor(factor: VisitFactor): String = when (factor) {
        VisitFactor.SUITS_CLASS -> "suits their class"
        VisitFactor.OFF_CLASS -> "doesn't suit their class"
        VisitFactor.ELEMENT_TASTE -> "has their favorite element"
        VisitFactor.LIKES_NOVELTY -> "has the elemental effect they like"
        VisitFactor.STRONGER_THAN_OWN -> "stronger than their current weapon"
        VisitFactor.NOT_STRONGER_THAN_OWN -> "no stronger than their current weapon"
        VisitFactor.OWN_BLADE_WORN -> "their current weapon is worn"
        VisitFactor.UNARMED -> "they need a weapon"
        VisitFactor.STORIED_BLADE -> "has a history"
        VisitFactor.COLLECTOR_PRIZE -> "fine enough for a collection"
        VisitFactor.CAN_AFFORD -> "they can afford it"
        VisitFactor.CANNOT_AFFORD -> "they can't afford it"
        VisitFactor.ABOVE_THEIR_CEILING -> "costs more than they think it's worth"
        VisitFactor.REGULAR -> "they trust your shop"
        VisitFactor.COUNTERS_THREAT -> "effective against the attackers"
        VisitFactor.THREAT_RESISTS -> "the attackers resist its element"
    }

    /** "Mira Ashwood, Ranger, a regular. Carries a worn Iron Bow." */
    fun customer(visit: MarketVisit, content: ContentCatalog): String {
        val c = visit.customer ?: return visit.heroName
        val who = listOfNotNull(c.name, content.classById[c.classId]?.name, "a regular".takeIf { c.regular }).joinToString(", ")
        val worn = visit.considered.any { VisitFactor.OWN_BLADE_WORN in it.factors }
        return "$who. " + (c.equipped?.let { "Carries ${if (worn) "a worn " else ""}${it.name}." } ?: "Carries no weapon.")
    }

    private val against = setOf(VisitFactor.OFF_CLASS, VisitFactor.NOT_STRONGER_THAN_OWN, VisitFactor.CANNOT_AFFORD, VisitFactor.ABOVE_THEIR_CEILING, VisitFactor.THREAT_RESISTS)

    /**
     * The recorded factors of one weighed blade as sentences, what spoke for it apart from what spoke against:
     * "For it: suits their class. Against it: 12 gold beyond their purse." Empty when the visit recorded none.
     */
    fun weighed(item: Considered): String {
        fun words(f: VisitFactor) = if (f == VisitFactor.CANNOT_AFFORD && item.shortBy != null) "${item.shortBy} gold over their budget" else factor(f)
        val (cons, pros) = item.factors.partition { it in against }
        return listOfNotNull(
            pros.takeIf { it.isNotEmpty() }?.let { "Reasons to buy. ${it.map { f -> words(f) }.joinSentences()}" },
            cons.takeIf { it.isNotEmpty() }?.let { "Reasons to pass. ${it.map { f -> words(f) }.joinSentences()}" },
        ).joinToString(" ")
    }

    /** What a visitor without the price could put down, and the cheapest blade as they found the shelf; either is null when the record lacks it. */
    private fun purse(visit: MarketVisit, day: ShopDayScript): Pair<Int?, Int?> {
        val funds = visit.considered.firstNotNullOfOrNull { k -> k.shortBy?.let { k.price - it } }
        // The shelf as this visitor found it: the opening prices less what earlier visitors took.
        val gone = day.visits.filter { it.seq < visit.seq }.mapNotNull { it.purchasedWeaponId }
        return funds to day.prices.filterKeys { it !in gone }.values.minOrNull()
    }

    /**
     * A visit's outcome as a heading: the reason, led by its recorded number where one says more than the label
     * ("38 gold short of the cheapest blade"). States what was missing, never what a lower price would have done.
     */
    fun headline(visit: MarketVisit, day: ShopDayScript): String {
        if (visit.reason == VisitReason.TOO_EXPENSIVE) {
            val (funds, cheapest) = purse(visit, day)
            if (funds != null && cheapest != null && cheapest > funds) return "${cheapest - funds} gold short of the cheapest weapon"
        }
        return reason(visit.reason)
    }

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
                val (funds, cheapest) = purse(visit, day)
                listOf(funds?.let { "Had $it gold to spend" + (cheapest?.let { p -> ". The cheapest weapon cost $p gold" } ?: "") + "." })
            }
            VisitReason.OVERPRICED -> listOf(with(VisitFactor.ABOVE_THEIR_CEILING)?.let { k -> day.blade(k.weaponId)?.let { "They didn't think ${it.name} was worth ${k.price} gold." } })
            VisitReason.NOT_SUITED -> listOf(with(VisitFactor.OFF_CLASS)?.let { k -> day.blade(k.weaponId)?.let { b -> className?.let { "${b.name} doesn't suit a $it." } } })
            VisitReason.NOT_BETTER -> listOf(own?.let { "Nothing on the shelf improved on their $it." })
            VisitReason.EMPTY_SHELVES, VisitReason.UNDECIDED -> emptyList()
            VisitReason.WORN_OUT -> listOf(own?.let { "Their $it needed replacing." }, bought(blade, sale))
            VisitReason.GREAT_FIT -> listOf(blade?.let { b -> className?.let { "$b suits a $it well." } }, bought(blade, sale))
            VisitReason.GOOD_ENOUGH -> listOf(
                if (own != null) blade?.let { "$it is stronger than their $own." } else "They came in without a weapon.".takeIf { with(VisitFactor.UNARMED) != null },
                bought(blade, sale),
            )
            VisitReason.COMMISSION_DELIVERED -> listOf(sale?.let { "Collected ${blade ?: "the weapon"} and paid ${it.cashPaid} gold." })
            VisitReason.COLLECTOR_PURCHASE -> listOf(sale?.let { "Paid ${it.cashPaid} gold for ${blade ?: "a weapon"} for their collection." })
            VisitReason.TASTE_MATCH -> listOf(sidegrade(blade, own, "it has their favorite element"), bought(blade, sale))
            VisitReason.PRIZED -> listOf(sidegrade(blade, own, "they collect fine weapons"), bought(blade, sale))
            VisitReason.STORIED -> listOf(sidegrade(blade, own, "they value its history"), bought(blade, sale))
            VisitReason.COUNTERS_THREAT -> listOf(blade?.let { "They chose $it for its element before the siege." }, bought(blade, sale))
            VisitReason.RESISTED -> listOf(with(VisitFactor.THREAT_RESISTS)?.let { k -> day.blade(k.weaponId)?.let { "The attackers resist ${it.name}'s element. They left it on the shelf." } })
        }
        val trade = sale?.takeIf { it.tradeInWeaponId != null && visit.kind == VisitKind.BROWSE }?.let { "Traded in ${own ?: "their old weapon"} for ${it.tradeInCredit} gold of credit. Paid ${it.cashPaid} gold in cash." }
        val bonus = sale?.takeIf { it.saleBonus > 0 }?.let { "The blessing added ${it.saleBonus} gold to the sale." }
        val stipend = sale?.takeIf { it.stipend > 0 }?.let { "Their guild covered ${it.stipend} gold. They paid ${it.cashPaid} gold themselves." }
        return (clauses + trade + stipend + bonus).filterNotNull().joinToString(" ").ifEmpty { reason(visit.reason).replaceFirstChar { it.uppercase() } + "." }
    }

    private fun sidegrade(blade: String?, own: String?, why: String): String? = blade?.let { b -> own?.let { "$b is no stronger than their $it, but $why." } }

    private fun bought(blade: String?, sale: Sale?): String? = sale?.listedPrice?.let { "Bought ${blade ?: "a weapon"} for $it gold." }

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
            RecognitionCue.FIRST_VISIT -> "$hero visited your shop for the first time."
            RecognitionCue.FIRST_BLADE -> blade?.let { "$hero bought their first weapon from you. $it." }
            RecognitionCue.BECAME_REGULAR -> listOfNotNull(r.count?.takeIf { it >= 2 }?.let { "That's $it weapons bought from you." }, "$hero is a regular now.").joinToString(" ")
            RecognitionCue.REGULAR_RETURNS -> r.day?.let { "$hero is a regular. Their last visit was on day $it." }
            RecognitionCue.STILL_CARRIES -> blade?.let { b -> r.count?.let { "$hero still uses $b. ${if (it == 1) "One win" else "$it wins"} so far." } }
            RecognitionCue.BLADE_WORN -> blade?.let { "$hero brought back $it. It's looking worn." }
            RecognitionCue.HELD_THE_WALL -> blade?.let { b -> r.day?.let { "$hero defended the walls with $b on day $it." } }
            RecognitionCue.SLEW_AN_ELITE -> blade?.let { b -> carried?.title?.let { "$hero defeated an elite with $b. It earned the name '$it'." } }
            RecognitionCue.KEPT_THE_VOW -> r.count?.let { "$hero kept their vow by defeating $it foes. Now they're back for a weapon." }
            RecognitionCue.MENTORS_BLADE -> blade?.let { b -> visit.customer?.mentorName?.let { "$hero trained with $it and now carries their old $b." } }
            RecognitionCue.OF_THE_LINE -> visit.heroId?.let { state.heroes[it]?.lineageId }?.let { id -> state.legacy.lineages.firstOrNull { it.id == id } }?.let { "$hero is descended from ${it.heroName}, who ${it.deed}." }
            RecognitionCue.WAITED_YESTERDAY -> "The shop was full when $hero came yesterday. Today they're first in."
            RecognitionCue.WANT_ANSWERED -> blade?.let { b -> r.day?.let { "$hero couldn't find what they wanted on day $it. Today $b was waiting." } }
        }
    }

    /** One group of the tally: "3 could afford nothing on the shelf", "1 bought (Iron Axe, 60 gold)". */
    fun tally(group: TallyGroup, day: ShopDayScript): String {
        val n = group.visits.size
        return when (group.outcome) {
            TallyOutcome.COMMISSION -> "$n collected a commission"
            TallyOutcome.COLLECTOR -> "$n bought for a collection"
            TallyOutcome.LEFT -> "$n ${reason(group.reason ?: VisitReason.UNDECIDED)}"
            TallyOutcome.BOUGHT -> "$n bought" + (group.visits.singleOrNull()?.let { v -> day.blade(v.purchasedWeaponId)?.let { b -> v.sale?.let { " (${b.name}, ${it.cashPaid} gold)" } } } ?: "")
        }
    }

    fun quiet(quiet: QuietDay): String = when (quiet.kind) {
        QuietKind.NO_VISITORS -> "No visitors today."
        QuietKind.EMPTY_SHELF -> "${names(quiet.visitors.map { it.heroName }.distinct())} visited. The shelf was empty."
    }

    /** The bare-shelf day for a card that shows the faces beside it: the count, not the names again. */
    fun quietCount(quiet: QuietDay): String = when (quiet.kind) {
        QuietKind.NO_VISITORS -> quiet(quiet)
        QuietKind.EMPTY_SHELF -> "${count(quiet.visitors.map { it.heroId?.value ?: it.heroName }.distinct().size, "visitor")} came by. The shelf was empty."
    }

    /**
     * The day's fights in one or two sentences of counts: "5 heroes went out unarmed and all were driven back." Says who
     * carried no blade because the fight's record names none; says nothing of what a blade would have changed.
     */
    fun field(t: FieldTally): String {
        val unarmed = t.wonUnarmed + t.drivenBackUnarmed
        fun was(n: Int) = if (n == 1) "was" else "were"
        if (t.died == 0 && t.won == 0 && unarmed == t.fought)
            return if (t.fought == 1) "One hero fought without a weapon and had to retreat." else "${t.fought} heroes fought without weapons. All had to retreat."
        val outcomes = listOfNotNull(
            "${t.won} won".takeIf { t.won > 0 }, "${t.drivenBack} ${was(t.drivenBack)} driven back".takeIf { t.drivenBack > 0 }, "${t.died} died".takeIf { t.died > 0 },
        ).joinToString(", ")
        val bare = when {
            unarmed == 0 -> null
            t.drivenBackUnarmed == 0 -> "$unarmed of them fought without weapons."
            t.drivenBackUnarmed == unarmed -> "$unarmed of them fought without weapons and ${was(unarmed)} driven back."
            else -> "$unarmed of them fought without weapons. ${t.drivenBackUnarmed} of those ${was(t.drivenBackUnarmed)} driven back."
        }
        return listOfNotNull("${count(t.fought, "hero")} fought today. $outcomes.", bare).joinToString(" ")
    }

    /**
     * The record's own sentence, then only what the stored result says: the counterfactual in those words, a new title,
     * what was brought back. No card says a blade decided anything.
     */
    fun aftermath(card: AftermathCard, content: ContentCatalog): String = listOfNotNull(
        LegacyProse.display(card.text),
        "Bought from your shop this morning.".takeIf { card.kind == AftermathKind.WIN_NEW_BLADE },
        when (card.counterfactual) {
            Counterfactual.OLD_BLADE -> "Their old ${card.oldWeapon?.name ?: "weapon"} would have lost this fight."
            Counterfactual.BARE_HANDED -> "They would have lost this fight without a weapon."
            null -> null
        },
        card.title?.let { "The weapon earned the name $it." },
        card.factionId?.takeIf { card.matchupHelped }?.let { content.factionById[it] }?.let { "Its element helped against the ${it.name}." },
        card.materialId?.let { content.materialById[it] }?.let { "Brought back ${it.name}." }?.takeIf { card.kind == AftermathKind.SCARCE_LOOT || card.kind == AftermathKind.ELITE_SLAIN },
    ).joinToString(" ")

    /**
     * The lead in words. Names come from [state]; a name that is gone drops its clause. [evening] is the card that ends
     * the day the lead was drawn from: there the day just watched is "today", on the next morning's Shop "yesterday".
     */
    fun lead(lead: Lead, state: GameState, content: ContentCatalog, config: BalanceConfig, evening: Boolean = false): LeadLine {
        val hero = lead.heroId?.let { state.heroes[it]?.fullName }
        val asked = lead.commissionId?.let { state.commissions[it] }?.let { Commissions.describe(it, content, config) }
        val faction = lead.factionId?.let { content.factionById[it]?.name }
        val days = lead.days?.let { if (it <= 0) "today" else if (it == 1) "tomorrow" else "in $it days" }
        return when (lead.kind) {
            LeadKind.FIRST_BLADE -> LeadLine("Forge your first weapon", lead.count?.let { "${count(it, "hero")} in town. Your shelf is empty." })
            LeadKind.CHOOSE_BLESSING -> LeadLine("Choose a blessing", "The town has offered a blessing for the forge.")
            LeadKind.ANSWER_REQUEST -> LeadLine(
                "Answer ${hero?.let { "$it's" } ?: "a"} request",
                listOfNotNull(asked?.let { "A $it" }, lead.gold?.let { "It pays $it gold" }, days?.let { "the offer expires $it" }).joinSentences().ifEmpty { null },
            )
            LeadKind.FORGE_FOR_REQUEST -> LeadLine(
                "Forge for ${hero?.let { "$it's" } ?: "a"} request",
                listOfNotNull(asked?.let { "You don't have a $it ready" }, days?.let { "due $it" }).joinSentences().ifEmpty { null },
            )
            LeadKind.LIST_STOCK -> LeadLine("Put a weapon on the shelf", lead.count?.let { "Your shelf is empty. ${count(it, "weapon")} in storage." })
            LeadKind.FORGE_STOCK -> LeadLine("Forge something to sell", "You have no weapons in storage or on the shelf.")
            LeadKind.PRICES_TOO_HIGH -> LeadLine(
                "Review your prices",
                listOfNotNull(lead.count?.let { "${count(it, "customer")} left without buying on price ${if (evening) "today" else "yesterday"}" }, lead.gold?.let { "the cheapest weapon costs $it gold" }).joinSentences().ifEmpty { null },
            )
            LeadKind.ARM_DEFENDERS -> LeadLine(
                "Arm the defenders",
                listOfNotNull(faction?.let { f -> "$f expected${days?.let { d -> " $d" }.orEmpty()}" }, lead.element?.let { "weak to ${it.name.lowercase()}" }).joinSentences().ifEmpty { null },
            )
            LeadKind.ANSWER_WANT -> LeadLine(
                "Forge ${lead.familyId?.let { content.familyById[it] }?.let { withArticle(it.name.lowercase()) } ?: "a weapon"}${hero?.let { " for $it" }.orEmpty()}",
                listOfNotNull(
                    lead.power?.let { "They need a weapon with power $it or more" },
                    lead.gold?.let { "their budget is about ${about(it)} gold" },
                    lead.days?.let { if (it <= 0) "their request ends tomorrow" else "their request lasts ${count(it + 1, "more day")}" },
                ).joinSentences().ifEmpty { null },
            )
            LeadKind.FORGE_FOR_BUYERS -> LeadLine(
                "Forge for today's buyers",
                when {
                    hero != null -> "$hero's ${lead.weaponId?.let { state.weapons[it]?.name } ?: "weapon"} is worn."
                    lead.gold != null && lead.count != null -> "${count(lead.count, "hero")} can afford your cheapest weapon at ${lead.gold} gold."
                    lead.count != null -> "${count(lead.count, "hero")} ${if (lead.count == 1) "needs" else "need"} a weapon."
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

    fun storyLine(entry: HistoryEntry, currentEra: Int): String = (if (entry.era == currentEra) "Day ${entry.day}" else "Era ${entry.era}, day ${entry.day}") + ": " + LegacyProse.display(entry.text)

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
        val what = if (entry.lostToTime) "No record of its properties survives." else listOfNotNull(
            entry.signatureId?.let { com.tinyblacksmith.core.crafting.SignatureCatalog.byId[it] }?.let { "Signature weapon ${it.name}" },
            entry.affixes.mapNotNull { content.affixById[it]?.name }.takeIf { it.isNotEmpty() }?.let { "Properties: ${it.joinToString(", ")}" },
            entry.flaws.mapNotNull { content.affixById[it]?.name }.takeIf { it.isNotEmpty() }?.let { "flaws include ${it.joinToString(", ")}" },
            entry.catalystId?.let { content.materialById[it] }?.let { "forged with ${it.name}" },
        ).joinSentences().ifEmpty { "A plain weapon with no added properties." }
        return listOf(
            "${entry.weaponName}, ${entry.title}. Era ${entry.era}. ${entry.kills} ${if (entry.kills == 1) "victory" else "victories"}. Fame ${entry.fame}.",
            what,
            if (entry.owners.isEmpty()) "Its past owners are unknown." else "Carried by ${names(entry.owners)}.",
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
            threat.weakTo?.let { "The $name are weak to ${word(it)}" },
            threat.resists?.let { if (threat.weakTo == null) "The $name resist ${word(it)}" else "they resist ${word(it)}" },
        ).joinSentences().ifEmpty { null }
    }

    /** The label beside one blade or one material of that element. */
    fun threatMark(mark: ThreatMark, threat: Threat, content: ContentCatalog): String {
        val name = content.factionById[threat.factionId]?.name ?: "besiegers"
        return if (mark == ThreatMark.COUNTERS) "Effective against the $name" else "The $name resist it"
    }

    /** Why a request is made, for its card: `Commissions.why` with the names from [state]. Null for an ordinary request or a patron who is gone. */
    fun commissionWhy(commission: Commission, state: GameState): String? {
        val buyer = state.heroes[commission.buyerId]?.fullName ?: return null
        return Commissions.why(commission, buyer, commission.recipientId?.let { state.heroes[it]?.fullName })
    }

    /** A hero's standing want in a line: "Wren Kestrel wants a bow; can spend about 90 gold." Null without one. The numbers are `Hero.want`'s own. */
    fun want(hero: Hero, content: ContentCatalog): String? = hero.want?.let { w ->
        "${hero.fullName} wants ${content.familyById[w.familyId]?.let { withArticle(it.name.lowercase()) } ?: "a weapon"}. Budget about ${about(w.budget)} gold."
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
