package com.example.blacksmithproject.ui.shopday

import androidx.compose.runtime.Immutable
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.model.BlessingId
import com.tinyblacksmith.core.model.CombatReplay
import com.tinyblacksmith.core.model.CustomerSnapshot
import com.tinyblacksmith.core.model.FactionId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.IncomeKind
import com.tinyblacksmith.core.model.MarketVisit
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.VisitKind
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponSnapshot
import com.tinyblacksmith.core.shopday.AftermathKind
import com.tinyblacksmith.core.shopday.Ending
import com.tinyblacksmith.core.shopday.Lines
import com.tinyblacksmith.core.shopday.QuietKind
import com.tinyblacksmith.core.shopday.ShopDayScript

/** Beat lengths at 1x in milliseconds (plan 5.3). PROPOSED playtest values; 2x halves them, Tap has none. */
object BeatLength {
    const val OPEN = 1200
    const val ARRIVE = 1000
    const val BROWSE = 900
    const val DECIDE = 1500
    const val TRANSACT = 1600
    const val TALLY = 2000
    const val CLOSE = 2000
    const val AFTERMATH = 2000
}

/** A face and the records behind it: the counter snapshot when the hero came in today, the hero as saved otherwise. */
@Immutable
data class FaceUi(val heroId: HeroId?, val name: String, val snapshot: CustomerSnapshot? = null, val hero: Hero? = null) {
    val regular: Boolean get() = snapshot?.regular == true
}

@Immutable data class BladeUi(val blade: WeaponSnapshot, val price: Int)

/** One line of a receipt or of the till: a label and a value, never joined into a sentence. */
@Immutable data class ReceiptRow(val label: String, val value: String, val total: Boolean = false)

/** A blade the visitor weighed: its name and price, then the recorded factors as sentences. */
@Immutable data class LookedUi(val blade: WeaponSnapshot?, val title: String, val factors: String)

@Immutable
data class VisitUi(
    val seq: Int,
    val kind: VisitKind,
    val face: FaceUi,
    val detail: String,                 // class, regular, what they carry: the rest of Lines.customer after the name
    val sold: Boolean,
    val banner: String,                 // the outcome in a word or two: Sold, Request paid, Sold to a collector, No sale
    val coin: Int,                      // what the Sale record added to the till; 0 without a sale
    val earnedBefore: Int,              // the coin of the sales shown before this card
    val earnedAfter: Int,
    val recognition: String?,           // the stored cue as a sentence: why this face is known
    val outcome: String,                // the typed reason as a heading, led by its recorded number where it has one
    val decision: String?,              // the reason with its numbers; null when it would only repeat the heading
    val receipt: List<ReceiptRow>,
    val looked: List<LookedUi>,
    val lookedIds: Set<WeaponId>,
    val purchased: WeaponSnapshot?,
)

/** [seqs] are the visits the line counts, by their order in the day. */
@Immutable data class TallyGroupUi(val line: String, val faces: List<FaceUi>, val seqs: List<Int>)

@Immutable
data class AftermathUi(
    val kind: AftermathKind,
    val title: String,
    val note: String?,                  // what the card is not, where it could be mistaken: a merchant's resale is no shop sale
    val text: String,
    val hero: FaceUi?,
    val other: FaceUi?,                 // the mentor of a lesson; the fallen owner of an inherited blade
    val champions: List<FaceUi>,
    val blade: WeaponSnapshot?,
    val factionId: FactionId?,
    val materialId: MaterialId?,
    val replay: CombatReplay?,
    /** Set on the siege's card only. */
    val siege: SiegeOutcomeUi? = null,
)

/**
 * A resolved siege as the day recorded it. [forgeDamage] is the day's own record (null when none was written);
 * [forgeHealthNow] is the forge as saved, after the night's recovery: the two are shown apart and never subtracted.
 * A lost siege is not a fallen forge: that is the day's last card.
 */
@Immutable
data class SiegeOutcomeUi(val day: Int, val held: Boolean, val foe: String?, val forgeDamage: Int?, val forgeHealthNow: Int) {
    val outcome: String get() = if (held) "The town held" else "The defenses broke"
    /** One line for the evening card, so a skipped day still says what the siege did. */
    val recap: String get() = listOfNotNull(outcome, forgeDamage?.let { if (it > 0) "forge damage $it" else "no forge damage" }).joinToString(" · ")
}

@Immutable data class BlessingUi(val id: BlessingId, val name: String, val description: String)

/**
 * One step of the day. A step is whole on its first frame; [millis] is how long it stays at 1x before the next one
 * when the player has chosen a speed, and 0 for a step that waits for a choice. [gone] are the blades that had left the
 * shelf before this step.
 */
sealed interface Beat {
    val millis: Int
    val progress: String
    val gone: Set<WeaponId>

    /** [visitors] is everyone who came; [shown] are those with a card of their own, the rest are in the tally. */
    @Immutable data class Open(val visitors: Int, val shown: Int, override val progress: String = "The shop opens") : Beat {
        override val millis get() = BeatLength.OPEN
        override val gone: Set<WeaponId> get() = emptySet()
    }
    @Immutable data class Visit(val visit: VisitUi, override val progress: String, override val gone: Set<WeaponId>) : Beat {
        override val millis get() = BeatLength.ARRIVE + BeatLength.BROWSE + BeatLength.DECIDE + if (visit.sold) BeatLength.TRANSACT else 0
    }
    @Immutable data class Tally(val count: Int, val featured: Int, val groups: List<TallyGroupUi>, val earnedBefore: Int, val earnedAfter: Int, override val gone: Set<WeaponId>, override val progress: String = "The rest of the day") : Beat {
        override val millis get() = BeatLength.TALLY
    }
    @Immutable data class Close(val rows: List<ReceiptRow>, val counts: String, val purse: Int, override val gone: Set<WeaponId>, override val progress: String = "The shop closes") : Beat {
        override val millis get() = BeatLength.CLOSE
    }
    @Immutable data class Quiet(val kind: QuietKind, val text: String, val faces: List<FaceUi>, val seqs: List<Int>, override val progress: String = "A quiet day") : Beat {
        override val millis get() = BeatLength.CLOSE
        override val gone: Set<WeaponId> get() = emptySet()
    }
    @Immutable data class Aftermath(val card: AftermathUi, val index: Int, val of: Int, val moreInGazette: Int, override val progress: String) : Beat {
        // A siege decides the run: its card waits for the player at any speed.
        override val millis get() = if (card.siege != null) 0 else BeatLength.AFTERMATH
        override val gone: Set<WeaponId> get() = emptySet()
    }
    @Immutable data class Blessing(val choices: List<BlessingUi>, val siege: String? = null, override val progress: String = "Evening") : Beat {
        override val millis get() = 0
        override val gone: Set<WeaponId> get() = emptySet()
    }
    @Immutable data class Tomorrow(val day: Int, val gold: Int, val shelf: Int, val storage: Int, val action: String, val reason: String?, val siege: String, val recap: String? = null, override val progress: String = "Evening") : Beat {
        override val millis get() = 0
        override val gone: Set<WeaponId> get() = emptySet()
    }
    @Immutable data class Fallen(val cause: String?, val days: Int, override val progress: String = "The last day") : Beat {
        override val millis get() = 0
        override val gone: Set<WeaponId> get() = emptySet()
    }
}

/** True for the steps that end the sequence and wait for a choice: Blessing, Tomorrow, Forge fallen. */
val Beat.isEnding: Boolean get() = this is Beat.Blessing || this is Beat.Tomorrow || this is Beat.Fallen

/**
 * The whole day as the screen plays it. [beats] are indexed by the session's position; [endingIndex] is where Skip day
 * lands (Forge fallen, Blessing or Tomorrow). [took] is the day's income for the Resume prompt, null on an old record.
 */
@Immutable
data class ShopDayUiModel(val day: Int, val shelf: List<BladeUi>, val beats: List<Beat>, val endingIndex: Int, val took: Int?) {
    fun beat(position: Int): Beat = beats[position.coerceIn(0, beats.lastIndex)]
}

private fun incomeLabel(kind: IncomeKind): String = when (kind) {
    IncomeKind.SHELF_SALE -> "Shelf sales"
    IncomeKind.SALE_BONUS -> "Town's blessing"
    IncomeKind.STIPEND -> "Guild stipends"
    IncomeKind.COMMISSION -> "Requests"
    IncomeKind.COLLECTOR -> "Collector"
    IncomeKind.TRIBUTE -> "Tribute from the town"
}

private fun aftermathLabel(kind: AftermathKind): String = when (kind) {
    AftermathKind.SIEGE_HELD, AftermathKind.SIEGE_LOST -> "The siege"
    AftermathKind.DEATH -> "A death"
    AftermathKind.ELITE_SLAIN -> "An elite slain"
    AftermathKind.AMBITION_FULFILLED -> "An ambition fulfilled"
    AftermathKind.TITLE_EARNED -> "A blade earns a name"
    AftermathKind.INHERITED -> "A blade passed on"
    AftermathKind.RESOLD -> "Sold on by a merchant"
    AftermathKind.WIN_NEW_BLADE -> "First fight with a new blade"
    AftermathKind.WIN -> "A fight won"
    AftermathKind.GUILD_LESSON -> "At the guild hall"
    AftermathKind.SCARCE_LOOT -> "Brought back"
    AftermathKind.BLADE_GONE -> "A blade lost"
    AftermathKind.LOSS -> "Driven back"
    AftermathKind.FIELD_SUMMARY -> "Out in the field"
}

private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/**
 * The script as immutable UI models: every sentence is a `Lines` template over the day's records or a recorded number
 * under a fixed label. Pure: reads [state] (the state End Day returned), draws nothing, decides nothing.
 */
fun ShopDayScript.toUi(state: GameState, content: ContentCatalog, config: BalanceConfig): ShopDayUiModel {
    val script = this
    val all = visits
    val snapshots = all.mapNotNull { v -> v.customer?.let { it.heroId to it } }.toMap()
    fun face(id: HeroId, name: String? = null): FaceUi {
        val hero = state.heroes[id]
        val snapshot = snapshots[id]
        return FaceUi(id, name ?: hero?.fullName ?: snapshot?.name ?: "", snapshot, hero)
    }
    fun face(v: MarketVisit) = FaceUi(v.heroId, v.heroName, v.customer, v.heroId?.let { state.heroes[it] })
    val goneAll = all.mapNotNull { it.purchasedWeaponId }.toSet()

    fun receipt(v: MarketVisit): List<ReceiptRow> {
        val sale = v.sale ?: return emptyList()
        return buildList {
            // A request pays its agreed reward and a collector more than the tag: each gets its own row, so the rows add up to the total.
            if (v.kind == VisitKind.COMMISSION) add(ReceiptRow("Request payment", "${sale.cashPaid} gold"))
            else sale.listedPrice?.let { listed ->
                add(ReceiptRow("Listed price", "$listed gold"))
                if (v.kind == VisitKind.COLLECTOR && sale.cashPaid > listed) add(ReceiptRow("Collector's premium", "+${sale.cashPaid - listed} gold"))
                if (v.kind == VisitKind.COLLECTOR && sale.cashPaid < listed) add(ReceiptRow("Collector's offer, under the tag", "−${listed - sale.cashPaid} gold"))
            }
            if (sale.tradeInWeaponId != null || sale.tradeInCredit != 0) {
                val old = v.customer?.equipped?.takeIf { it.weaponId == sale.tradeInWeaponId }?.name
                add(ReceiptRow("Trade-in" + (old?.let { ": $it" } ?: ""), "−${sale.tradeInCredit} gold"))
            }
            if (sale.saleBonus != 0) add(ReceiptRow("Town's blessing", "+${sale.saleBonus} gold"))
            // Part of the price, not on top of it: the guild's share reaches the till with the customer's.
            if (sale.stipend != 0) add(ReceiptRow("Of that, paid by their guild", "${sale.stipend} gold"))
            // What the shop's gold rose by, as the Sale record defines it.
            add(ReceiptRow("Coin to the till", "${sale.cashPaid + sale.saleBonus + sale.stipend} gold", total = true))
        }
    }

    // What a visit added to the till, as its Sale record defines it. The cards only add these up; no gold moves here.
    fun coin(v: MarketVisit) = v.sale?.let { it.cashPaid + it.saleBonus + it.stipend } ?: 0

    fun visitUi(v: MarketVisit, earnedBefore: Int): VisitUi {
        val outcome = Lines.headline(v, script).replaceFirstChar { it.uppercase() }
        val sold = v.purchasedWeaponId != null
        val name = v.customer?.name ?: v.heroName
        return VisitUi(
            seq = v.seq, kind = v.kind, face = face(v),
            detail = Lines.customer(v, content).removePrefix(name).removePrefix(", ").removePrefix(". "),
            sold = sold,
            banner = when { !sold -> "No sale"; v.kind == VisitKind.COMMISSION -> "Request paid"; v.kind == VisitKind.COLLECTOR -> "Sold to a collector"; else -> "Sold" },
            coin = coin(v), earnedBefore = earnedBefore, earnedAfter = earnedBefore + coin(v),
            recognition = Lines.recognition(v, script, state), outcome = outcome,
            decision = Lines.decision(v, script, content).takeIf { it != "$outcome." },
            receipt = receipt(v),
            looked = v.considered.takeIf { v.kind == VisitKind.BROWSE }.orEmpty().map { k ->
                LookedUi(script.blade(k.weaponId), listOfNotNull(script.blade(k.weaponId)?.name, "${k.price} gold").joinToString(", "), Lines.weighed(k))
            },
            lookedIds = v.considered.map { it.weaponId }.toSet(), purchased = script.blade(v.purchasedWeaponId),
        )
    }

    val beats = mutableListOf<Beat>()
    val quiet = script.quiet
    if (quiet != null) {
        beats += Beat.Quiet(quiet.kind, Lines.quietCount(quiet),quiet.visitors.distinctBy { it.heroId ?: it.heroName }.map { face(it) }, quiet.visitors.map { it.seq })
    } else {
        beats += Beat.Open(all.size, featured.size)
        // "Earned today" grows in the order the cards are shown: each featured sale, then everyone in the tally at once.
        var earned = 0
        featured.forEachIndexed { i, v ->
            val visit = visitUi(v, earned)
            earned = visit.earnedAfter
            beats += Beat.Visit(visit, "Counter · ${i + 1} of ${featured.size}", all.filter { it.seq < v.seq }.mapNotNull { it.purchasedWeaponId }.toSet())
        }
        if (tally.isNotEmpty()) {
            val rest = tally.sumOf { g -> g.visits.sumOf { coin(it) } }
            beats += Beat.Tally(
                tally.sumOf { it.visits.size }, featured.size,
                tally.map { g -> TallyGroupUi(Lines.tally(g, script).replaceFirstChar { it.uppercase() }, g.visits.map { face(it) }, g.visits.map { it.seq }) },
                earnedBefore = earned, earnedAfter = earned + rest, gone = goneAll,
            )
        }
        ledger?.let { l ->
            val sales = all.count { it.kind == VisitKind.BROWSE && it.purchasedWeaponId != null }
            val commissions = all.count { it.kind == VisitKind.COMMISSION }
            val collectors = all.count { it.kind == VisitKind.COLLECTOR }
            val left = all.count { it.purchasedWeaponId == null }
            beats += Beat.Close(
                rows = IncomeKind.entries.mapNotNull { k -> l.income[k]?.takeIf { it != 0 }?.let { ReceiptRow(incomeLabel(k), "$it gold") } } +
                    ReceiptRow("Earned today","${l.goldAtClose - l.goldAtOpen} gold", total = true),
                counts = listOfNotNull(
                    plural(sales, "sale", "sales"), plural(commissions, "request paid", "requests paid").takeIf { commissions > 0 },
                    plural(collectors, "collector", "collectors").takeIf { collectors > 0 }, "$left left without buying",
                    // Willing heroes with no seat left (`DayResolution.turnedAway`): the reason to buy the Signboard.
                    state.lastResolution?.takeIf { it.day == day }?.turnedAway?.size?.takeIf { it > 0 }?.let { "$it found the shop full" },
                    "${l.tradeInCredit} gold given in trade-in credit".takeIf { l.tradeInCredit > 0 },
                ).joinToString(" · "),
                purse = l.goldAtClose, gone = goneAll,
            )
        }
    }
    val siege = aftermath.firstOrNull { it.kind == AftermathKind.SIEGE_HELD || it.kind == AftermathKind.SIEGE_LOST }
        ?.let { SiegeOutcomeUi(day, it.kind == AftermathKind.SIEGE_HELD, it.foe, it.forgeDamage, state.town.integrity) }
    aftermath.forEachIndexed { i, card ->
        beats += Beat.Aftermath(
            AftermathUi(
                card.kind, aftermathLabel(card.kind), "Not a shop sale".takeIf { card.kind == AftermathKind.RESOLD }, Lines.aftermath(card, content),
                hero = card.heroId?.let { face(it, card.heroName) }, other = card.otherHeroId?.let { face(it) },
                champions = card.championIds.map { face(it) }, blade = card.weapon, factionId = card.factionId,
                materialId = card.materialId.takeIf { card.kind == AftermathKind.SCARCE_LOOT || card.kind == AftermathKind.ELITE_SLAIN }, replay = card.replay,
                siege = siege.takeIf { card.kind == AftermathKind.SIEGE_HELD || card.kind == AftermathKind.SIEGE_LOST },
            ),
            index = i + 1, of = aftermath.size, moreInGazette = if (i == aftermath.lastIndex) moreInGazette else 0,
            // The banner under the strip says "Beyond the door"; the strip says when, and counts only when there is more than one.
            progress = "After closing" + if (aftermath.size > 1) " · ${i + 1} of ${aftermath.size}" else "",
        )
    }
    val endingIndex = beats.size
    if (ending == Ending.FALLEN) {
        beats += Beat.Fallen(state.endCause, day)
    } else {
        if (ending == Ending.BLESSING) beats += Beat.Blessing(state.pendingBlessingOffer.map { id -> content.blessing(id).let { BlessingUi(id, it.name, it.description) } }, siege?.recap)
        val line = lead?.let { Lines.lead(it, state, content, config, evening = true) }
        val toSiege = state.town.nextSiegeDay - state.day
        beats += Beat.Tomorrow(
            day = state.day, gold = state.gold, shelf = state.listedWeapons().size, storage = state.storedWeapons().size,
            action = line?.action ?: "", reason = line?.reason,
            siege = "Next siege: day ${state.town.nextSiegeDay}" + when { toSiege <= 0 -> ", today"; toSiege == 1 -> ", tomorrow"; else -> ", in $toSiege days" },
            recap = siege?.recap,
        )
    }
    return ShopDayUiModel(
        day = day, shelf = shelf.mapNotNull { b -> prices[b.weaponId]?.let { BladeUi(b, it) } }, beats = beats, endingIndex = endingIndex,
        took = ledger?.let { it.goldAtClose - it.goldAtOpen },
    )
}
