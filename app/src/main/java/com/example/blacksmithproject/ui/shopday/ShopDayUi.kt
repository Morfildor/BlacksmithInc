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
    const val OPEN = 1500
    const val ARRIVE = 1200
    const val BROWSE = 1200
    const val DECIDE = 1600
    const val TRANSACT = 1600
    const val TALLY = 2500
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

/** A blade the visitor weighed: its name and price, then the recorded factors. */
@Immutable data class LookedUi(val blade: WeaponSnapshot?, val title: String, val factors: String)

@Immutable
data class VisitUi(
    val seq: Int,
    val kind: VisitKind,
    val face: FaceUi,
    val detail: String,                 // class, regular, what they carry: the rest of Lines.customer after the name
    val sold: Boolean,
    val recognition: String?,           // the stored cue as a sentence: why this face is known
    val outcome: String,                // the typed reason as a heading
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
)

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

    @Immutable data class Open(val visitors: Int, val blades: Int, override val progress: String = "The shop opens") : Beat {
        override val millis get() = BeatLength.OPEN
        override val gone: Set<WeaponId> get() = emptySet()
    }
    @Immutable data class Visit(val visit: VisitUi, override val progress: String, override val gone: Set<WeaponId>) : Beat {
        override val millis get() = BeatLength.ARRIVE + BeatLength.BROWSE + BeatLength.DECIDE + if (visit.sold) BeatLength.TRANSACT else 0
    }
    @Immutable data class Tally(val count: Int, val featured: Int, val groups: List<TallyGroupUi>, override val gone: Set<WeaponId>, override val progress: String = "The rest of the day") : Beat {
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
        override val millis get() = BeatLength.AFTERMATH
        override val gone: Set<WeaponId> get() = emptySet()
    }
    @Immutable data class Blessing(val choices: List<BlessingUi>, override val progress: String = "The town's thanks") : Beat {
        override val millis get() = 0
        override val gone: Set<WeaponId> get() = emptySet()
    }
    @Immutable data class Tomorrow(val day: Int, val gold: Int, val shelf: Int, val storage: Int, val action: String, val reason: String?, val siege: String, override val progress: String = "The day is done") : Beat {
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
    IncomeKind.STIPEND -> "Stipend"
    IncomeKind.COMMISSION -> "Commissions"
    IncomeKind.COLLECTOR -> "Collector"
    IncomeKind.TRIBUTE -> "Tribute"
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
            sale.listedPrice?.let { add(ReceiptRow("Listed price", "$it gold")) }
            if (sale.tradeInWeaponId != null || sale.tradeInCredit != 0) {
                val old = v.customer?.equipped?.takeIf { it.weaponId == sale.tradeInWeaponId }?.name
                add(ReceiptRow("Trade-in" + (old?.let { ": $it" } ?: ""), "−${sale.tradeInCredit} gold"))
            }
            if (sale.saleBonus != 0) add(ReceiptRow("Town's blessing", "+${sale.saleBonus} gold"))
            if (sale.stipend != 0) add(ReceiptRow("Stipend", "+${sale.stipend} gold"))
            // What the shop's gold rose by, as the Sale record defines it.
            add(ReceiptRow("Coin to the till", "${sale.cashPaid + sale.saleBonus + sale.stipend} gold", total = true))
        }
    }

    fun visitUi(v: MarketVisit): VisitUi {
        val outcome = Lines.reason(v.reason).replaceFirstChar { it.uppercase() }
        val name = v.customer?.name ?: v.heroName
        return VisitUi(
            seq = v.seq, kind = v.kind, face = face(v),
            detail = Lines.customer(v, content).removePrefix(name).removePrefix(", ").removePrefix(". "),
            sold = v.purchasedWeaponId != null, recognition = Lines.recognition(v, script, state), outcome = outcome,
            decision = Lines.decision(v, script, content).takeIf { it != "$outcome." },
            receipt = receipt(v),
            looked = v.considered.takeIf { v.kind == VisitKind.BROWSE }.orEmpty().map { k ->
                val line = Lines.considered(k, script)
                LookedUi(script.blade(k.weaponId), line.substringBefore(": "), line.substringAfter(": ", ""))
            },
            lookedIds = v.considered.map { it.weaponId }.toSet(), purchased = script.blade(v.purchasedWeaponId),
        )
    }

    val beats = mutableListOf<Beat>()
    val quiet = script.quiet
    if (quiet != null) {
        beats += Beat.Quiet(quiet.kind, Lines.quiet(quiet), quiet.visitors.distinctBy { it.heroId ?: it.heroName }.map { face(it) }, quiet.visitors.map { it.seq })
    } else {
        beats += Beat.Open(all.size, prices.size)
        featured.forEachIndexed { i, v ->
            beats += Beat.Visit(visitUi(v), "Customer ${i + 1} of ${featured.size}", all.filter { it.seq < v.seq }.mapNotNull { it.purchasedWeaponId }.toSet())
        }
        if (tally.isNotEmpty()) {
            beats += Beat.Tally(tally.sumOf { it.visits.size }, featured.size, tally.map { g -> TallyGroupUi(Lines.tally(g, script).replaceFirstChar { it.uppercase() }, g.visits.map { face(it) }, g.visits.map { it.seq }) }, goneAll)
        }
        ledger?.let { l ->
            val sales = all.count { it.kind == VisitKind.BROWSE && it.purchasedWeaponId != null }
            val commissions = all.count { it.kind == VisitKind.COMMISSION }
            val collectors = all.count { it.kind == VisitKind.COLLECTOR }
            val left = all.count { it.purchasedWeaponId == null }
            beats += Beat.Close(
                rows = IncomeKind.entries.mapNotNull { k -> l.income[k]?.takeIf { it != 0 }?.let { ReceiptRow(incomeLabel(k), "$it gold") } } +
                    ReceiptRow("Taken today", "${l.goldAtClose - l.goldAtOpen} gold", total = true),
                counts = listOfNotNull(
                    plural(sales, "sale", "sales"), plural(commissions, "commission", "commissions").takeIf { commissions > 0 },
                    plural(collectors, "collector", "collectors").takeIf { collectors > 0 }, "$left left without buying",
                    // Willing heroes with no seat left (`DayResolution.turnedAway`): the reason to buy the Signboard.
                    state.lastResolution?.takeIf { it.day == day }?.turnedAway?.size?.takeIf { it > 0 }?.let { "$it found the shop full" },
                    "${l.tradeInCredit} gold given in trade-in credit".takeIf { l.tradeInCredit > 0 },
                ).joinToString(" · "),
                purse = l.goldAtClose, gone = goneAll,
            )
        }
    }
    aftermath.forEachIndexed { i, card ->
        beats += Beat.Aftermath(
            AftermathUi(
                card.kind, aftermathLabel(card.kind), "Not a shop sale".takeIf { card.kind == AftermathKind.RESOLD }, Lines.aftermath(card, content),
                hero = card.heroId?.let { face(it, card.heroName) }, other = card.otherHeroId?.let { face(it) },
                champions = card.championIds.map { face(it) }, blade = card.weapon, factionId = card.factionId,
                materialId = card.materialId.takeIf { card.kind == AftermathKind.SCARCE_LOOT || card.kind == AftermathKind.ELITE_SLAIN }, replay = card.replay,
            ),
            index = i + 1, of = aftermath.size, moreInGazette = if (i == aftermath.lastIndex) moreInGazette else 0,
            progress = "Beyond the door · ${i + 1} of ${aftermath.size}",
        )
    }
    val endingIndex = beats.size
    if (ending == Ending.FALLEN) {
        beats += Beat.Fallen(state.endCause, day)
    } else {
        if (ending == Ending.BLESSING) beats += Beat.Blessing(state.pendingBlessingOffer.map { id -> content.blessing(id).let { BlessingUi(id, it.name, it.description) } })
        val line = lead?.let { Lines.lead(it, state, content, config) }
        val toSiege = state.town.nextSiegeDay - state.day
        beats += Beat.Tomorrow(
            day = state.day, gold = state.gold, shelf = state.listedWeapons().size, storage = state.storedWeapons().size,
            action = line?.action ?: "", reason = line?.reason,
            siege = "Next siege: day ${state.town.nextSiegeDay}" + when { toSiege <= 0 -> ", today"; toSiege == 1 -> ", tomorrow"; else -> ", in $toSiege days" },
        )
    }
    return ShopDayUiModel(
        day = day, shelf = shelf.mapNotNull { b -> prices[b.weaponId]?.let { BladeUi(b, it) } }, beats = beats, endingIndex = endingIndex,
        took = ledger?.let { it.goldAtClose - it.goldAtOpen },
    )
}
