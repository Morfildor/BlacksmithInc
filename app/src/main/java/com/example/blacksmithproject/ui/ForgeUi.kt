package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.example.blacksmithproject.ForgeDraft
import com.tinyblacksmith.core.combat.Action
import com.tinyblacksmith.core.combat.Amount
import com.tinyblacksmith.core.combat.EffectDef
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ConstraintKind
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.crafting.Bench
import com.tinyblacksmith.core.crafting.Capabilities
import com.tinyblacksmith.core.crafting.ClueRung
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.crafting.SignatureDef
import com.tinyblacksmith.core.crafting.Forge
import com.tinyblacksmith.core.crafting.ForgeOutlook
import com.tinyblacksmith.core.crafting.Journal
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.market.Commissions
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.HeroFate
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.WeaponFamilyId
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.shopday.Lines
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Journal as JournalModel
import com.tinyblacksmith.core.model.KnowledgeState
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.text.Terms
import com.tinyblacksmith.core.text.joinSentences

/** The places of a recipe on the workbench. The last two exist only in an Advanced forge. */
enum class RecipeSlot(val label: String, val empty: String, val choose: String, val hint: String) {
    WEAPON("Weapon", "Choose", "Choose weapon", "Each weapon type suits some hero classes. Choose one to see which."),
    METAL("Metal", "Choose", "Choose metal", "Choose the metal you'll forge with."),
    AUGMENT("Infusion", "Choose", "Choose infusion", "Gives the weapon its element, such as Fire or Frost."),
    CATALYST("Catalyst", "None", "Choose catalyst", "Optional. +${BalanceConfig.DEFAULT.catalystQualityBonus} quality, more exceptional results and fewer flaws. Used up when you forge."),
    TECHNIQUE("Technique", "Plain", "Choose technique", "Optional. Changes how you finish the weapon."),
}

/** One place of the recipe: [value] is the chosen thing's name, null while nothing is chosen. */
@Immutable data class SlotUi(val slot: RecipeSlot, val value: String?, val stock: Int? = null)

/**
 * One choice in a slot's tray. [id] is the family, material or technique it stands for (null: "None" or "Plain");
 * [stock] is what the smith owns of a material (null for what is not counted). An empty material is listed last.
 * [fight] (a guild run) is what a metal, an augment or a catalyst gives the blade in a fight, one "Name: rule" line each.
 * [blocked] is why a material that has run out cannot be placed now, and where more comes from; null while it can be.
 */
@Immutable data class OptionUi(
    val id: String?, val name: String, val stock: Int?, val selected: Boolean, val mark: MarkUi? = null, val detail: String? = null, val fight: List<String> = emptyList(),
    val blocked: String? = null,
) {
    val usable: Boolean get() = stock != 0
}

/**
 * One way the draft differs from what it was when its tray was opened. [good]: true helps, false costs, null is only
 * different. [rule]: a line about the notebook or a fight rule, told one tap away; the others are the decision facts.
 */
@Immutable data class ChangeUi(val text: String, val good: Boolean? = null, val rule: Boolean = false)

/** The draft against the one the tray was opened on, for the place being changed: [from] the choice then, [changes] what differs now. */
@Immutable data class TrayChangeUi(val from: String, val changes: List<ChangeUi>)

/** One relationship of the recipe as the journal knows it today. An untried one says nothing of how it will go. */
@Immutable data class NoteUi(val label: String, val subject: String, val state: KnowledgeState, val stage: String, val hint: String)

/** One requirement of a commission; [matches] is null where the draft cannot answer it (quality is known only after forging). */
@Immutable data class AskUi(val text: String, val matches: Boolean?)

/** A blade already in the shop read against the draft's purpose: [fits] by the engine's own rule, [line] where it is and what it lacks. Nothing is reserved by it. */
@Immutable data class StockFitUi(val weaponId: WeaponId, val name: String, val line: String, val fits: Boolean, val handsOver: Boolean = false)

/**
 * Who the draft is for, read back from the save each time: an open commission ([id]) or a hero's standing want
 * ([heroId], never an order). [status] is the request's own standing ("Accepted", "Not accepted yet") or "Not an order".
 * [stock] is what the shop already holds of the type asked for, the fitting blades first, and [stockNote] says when none
 * fits. [gone] says why a purpose the draft still names is no longer there; then it asks nothing and lists no stock.
 */
@Immutable data class BriefUi(
    val title: String, val asks: List<AskUi>, val accepted: Boolean, val status: String,
    val stock: List<StockFitUi> = emptyList(), val stockNote: String? = null, val gone: String? = null,
    val id: CommissionId? = null, val heroId: HeroId? = null,
)

/**
 * The one action of the Forge. [note] is the single thing in its way, or the price of overwork; [restock] the material
 * that ran out; [endDay] when no forge is possible today and resting is the way forward.
 */
@Immutable data class ForgeActionUi(val label: String, val enabled: Boolean, val note: String?, val restock: MaterialId? = null, val endDay: Boolean = false)

/** One chosen ingredient of the recipe: what it is for ([role]) and what choosing it costs ([constraint]). */
@Immutable data class OutlookPartUi(val name: String, val role: String, val constraint: String?, val constraintKind: ConstraintKind = ConstraintKind.DRAWBACK)

/** A rule every forge of the recipe carries that still waits for something: the [rule] by name, what it [needs] in words. */
@Immutable data class OutlookNeedUi(val rule: String, val needs: List<String>)

/** Something the roll may add ([name]) and when ([text]). Never a rule the weapon is said to have. */
@Immutable data class OutlookChanceUi(val name: String, val text: String)

/**
 * What can honestly be said of the recipe before it is forged (`ForgeOutlook`), for a guild run. [certain] holds only the
 * rules every forge of this recipe carries, [needs] what those still wait for from a party, and [possible] what a roll
 * may add: the two are never mixed. [demonstration] is the recipe as a weapon for the bench, at the middle of the
 * quality the forge can give, with nothing rolled on it.
 */
@Immutable
data class ForgeOutlookUi(
    val role: String, val suits: List<String>, val parts: List<OutlookPartUi>, val needs: List<OutlookNeedUi>, val tradeoff: String?,
    val possible: List<OutlookChanceUi>, val certain: List<BladeRuleUi>, val notes: List<String>, val demonstration: Weapon,
    /** The quality the forge gives this recipe before any exceptional result or flaw (`ForgeOutlook.qualityLow`/`qualityHigh`). */
    val qualityLow: Int = 0, val qualityHigh: Int = 0,
    /** By rule ID: what a metal's or an element's effect does, in a few words ([effectPhrase]). */
    val phrases: Map<String, String> = emptyMap(),
    /** Whether [tradeoff] is a drawback, a condition or a comparison only (`ForgeOutlook.Outlook.tradeoffKind`). */
    val tradeoffKind: ConstraintKind? = null,
)

/** A certain rule as the Forge names it: its source with what it does ("Iron · Starts each fight with 3 Armor"), else its name. */
fun ForgeOutlookUi.label(r: BladeRuleUi): String = phrases[r.id]?.let { "${r.name} · $it" } ?: r.name

/**
 * What a metal's or an element's effect does, in a few words, for the places that name it beside its source
 * (docs/WRITING_GUIDE.md 1: the source is never shown alone). Its number is read from the effect. Null for any other
 * effect, whose name already says what it does.
 */
internal fun effectPhrase(effect: EffectDef): String? {
    val n = effect.actions.firstNotNullOfOrNull { a ->
        when (a) { is Action.Damage -> a.amount; is Action.Guard -> a.amount; is Action.Give -> a.amount; else -> null } as? Amount.Fixed
    }?.value
    return when (effect.id) {
        "core_iron" -> n?.let { "Starts each fight with $it Armor" }
        "core_bronze" -> n?.let { "Hits back for $it when Armor breaks" }
        "core_silver" -> n?.let { "Starts each fight with $it Charge" }
        "core_obsidian" -> "Cracks the first time Armor breaks"
        "core_starsteel" -> n?.let { "+$it damage when its attack uses up a Mark" }
        "core_moonsteel" -> n?.let { "+$it damage to an enemy with Chill" }
        "element_fire" -> "Burns on hit"
        "element_frost" -> "Chills on hit"
        "element_storm" -> n?.let { "$it storm damage to the back enemy on hit" }
        "element_grave" -> "Inflicts Bleed on hit"
        "element_verdant" -> "Regenerates on hit"
        "element_sun" -> "Marks on hit"
        else -> null
    }
}

/**
 * The outlook's decision facts, said before its details: the likely quality and who it suits in a few words. The
 * energy it costs is on the Forge action, so it is not said again here.
 */
fun ForgeOutlookUi.facts(): List<Fact> = listOfNotNull(
    Fact(if (qualityLow == qualityHigh) "$qualityLow" else "$qualityLow to $qualityHigh", "Likely quality").takeIf { qualityHigh > 0 },
    suits.takeIf { it.isNotEmpty() }?.let { s -> Fact(if (s.size <= 2) s.joinToString(" and ") else "${s.take(2).joinToString(", ")} and ${s.size - 2} more", "Suits") },
)

/** A commission's or a want's asks as facts, a met one ticked, then its standing ("Accepted", "Not an order"). */
fun BriefUi.facts(): List<Fact> = asks.map { Fact(if (it.matches == true) "✓ ${it.text}" else it.text) } + Fact(status)

/**
 * The Forge destination for one save and one draft. [next] is the first place still empty; [command] is exactly what
 * the action sends (null until the three ingredients are chosen). Pure: reads the save, changes nothing.
 * [outlook] (a guild run, once the three ingredients are chosen) is what the recipe will do in a fight.
 */
@Immutable
data class ForgeWorkbenchUi(
    val title: String, val slots: List<SlotUi>, val next: RecipeSlot?, val notes: List<NoteUi>, val brief: BriefUi?, val action: ForgeActionUi, val command: Command.Forge?,
    val outlook: ForgeOutlookUi? = null,
    val heartfire: HeartfireUi? = null,
    /** The quality the forge gives this recipe before any exceptional result or flaw (`ForgeOutlook`), in either kind of run; null until the recipe is whole. */
    val quality: Pair<Int, Int>? = null,
)

/**
 * Heartfire on the workbench. [held] is always said. [blocked] is why the switch is not offered (Quick mode, none held),
 * null when it is. With the switch [on], [outlook] is what this forging spends and what it can give, from the engine's
 * own outlook, and [warning] says when a plain result cannot break through. [rule] and [source] are always shown: how a
 * weapon becomes Legendary, and where Heartfire comes from.
 */
@Immutable
data class HeartfireUi(
    val held: String, val on: Boolean, val blocked: String?, val outlook: List<String>, val warning: String?, val rule: String, val source: String,
    /** What the confirmation says before a Heartfire forging is sent: the cost, what is left, the chance in words and what a miss gives. Empty while there is nothing to confirm. */
    val confirm: List<String> = emptyList(),
)

/**
 * A hidden random chance in words (owner decision A: hidden odds are banded, never counted). The bands are the screen's
 * wording, not rules of the game; the chance itself is the engine's.
 */
internal fun chanceWord(chance: Double): String = when {
    chance <= 0.0 -> "none"
    chance < 0.10 -> "slim"
    chance < 0.30 -> "low"
    chance < 0.60 -> "fair"
    chance < 0.85 -> "good"
    else -> "high"
}

/** How often a hidden chance comes up, in words, for "Flaws are …". */
internal fun frequencyWord(chance: Double): String = when {
    chance <= 0.0 -> "never seen"
    chance < 0.10 -> "rare"
    chance < 0.25 -> "occasional"
    chance < 0.50 -> "common"
    else -> "the usual result"
}

/** What the temper does, with the numbers of the rules themselves. */
fun riskExplanation(r: Risk, config: BalanceConfig): String {
    val profile = config.risk.getValue(r)
    val spread = profile.rollSpread ?: config.qualityRollSpread
    val legend = config.depth.legend
    fun breakthrough(bonus: Double) = when { bonus < 0 -> " A Heartfire breakthrough is less likely."; bonus > 0 -> " A Heartfire breakthrough is more likely."; else -> "" }
    return when (r) {
        Risk.SAFE -> "Quality stays within $spread of what the recipe gives. Few flaws and few exceptional results." + breakthrough(legend.safeBonus)
        Risk.BALANCED -> "Quality moves up to $spread either way. A result can be exceptional (+${config.qualityExceptionalBonus} quality, never above the metal's quality cap, and 1 more property) or flawed (−${config.qualityDefectPenalty} quality and 1 flaw)."
        Risk.RECKLESS -> "Adds ${if (profile.extraSlots == 1) "1 more property" else "${profile.extraSlots} more properties"}. Flaws are ${frequencyWord(profile.defectChance)}, and a flawed weapon is still usable." + breakthrough(legend.recklessBonus)
    }
}

/** The Heartfire panel for [command], the draft's forge with Heartfire asked for ([asked]); null command: the recipe is not whole yet. */
fun GameEngine.heartfireUi(state: GameState, advanced: Boolean, asked: Boolean, command: Command.Forge?): HeartfireUi {
    val legend = config.depth.legend
    val blocked = when {
        state.heartfire < 1 -> "None held."
        !advanced -> "Forging with Heartfire needs the Advanced forge."
        else -> null
    }
    val on = asked && blocked == null
    val o = if (on && command != null) heartfireOutlook(state, command) else null
    val chance = o?.let {
        when {
            !it.fineMetal -> "Chance of Legendary: none in this metal."
            it.chanceHigh <= 0.0 -> "Chance of Legendary: none at this quality."
            it.chanceLow <= 0.0 -> "Chance of Legendary: none under quality ${it.floor}, ${chanceWord(it.chanceHigh)} at quality ${it.qualityHigh}."
            chanceWord(it.chanceLow) == chanceWord(it.chanceHigh) -> "Chance of Legendary: ${chanceWord(it.chanceLow)}."
            else -> "Chance of Legendary: ${chanceWord(it.chanceLow)} at quality ${it.qualityLow}, ${chanceWord(it.chanceHigh)} at quality ${it.qualityHigh}."
        }
    }
    val miss = o?.let { "If it does not become Legendary, the weapon still gets ${if (it.missSlots == 1) "1 more property" else "${it.missSlots} more properties"}." }
    return HeartfireUi(
        held = "Heartfire ${state.heartfire} of ${legend.heartfireMax}",
        on = on, blocked = blocked,
        outlook = when {
            !on -> emptyList()
            o == null -> listOf("Choose a weapon, a metal and an infusion to see what it would do.")
            else -> listOfNotNull(
                "Costs 1 Heartfire and ${legend.heartfireEnergy} extra energy, ${o.energy} energy in all. Both are spent even if the weapon does not become Legendary.",
                "Quality will be between ${o.qualityLow} and ${o.qualityHigh}, before any exceptional result or flaw. Only a weapon of quality ${o.floor} or more can become Legendary.",
                chance,
                miss,
            )
        },
        confirm = if (o == null) emptyList() else listOfNotNull(
            "Spends 1 Heartfire and ${o.energy} energy, even if the weapon does not become Legendary. ${state.heartfire - 1} of ${legend.heartfireMax} Heartfire left after.",
            chance,
            miss,
        ),
        warning = o?.takeIf { !it.fineMetal }?.let { "Heartfire cannot make a weapon of this metal Legendary. Rarity can reach Epic only in ${fineMetals()}." }
            ?: o?.takeIf { it.qualityHigh < it.floor }?.let { "Without an exceptional result, this recipe stays under quality ${it.floor}, so it cannot become Legendary." },
        rule = "Without Heartfire, the highest rarity is Epic, and only ${fineMetals()} weapons can be Epic. Weapons of other metals stop at Rare. Only Heartfire or ${legend.renownForLegend} deeds make a weapon Legendary.",
        source = "Heartfire comes from dangerous work: a deep hall cleared, a taken weapon won back, a starfall site, a warlord defeated at the town wall.",
    )
}


/** Null in a classic run, where no fight reads a weapon's rules. [forge] is the command as it will be sent, bellows included. */
fun GameEngine.forgeOutlook(state: GameState, forge: Command.Forge): ForgeOutlookUi? {
    if (state.guild == null) return null
    val o = ForgeOutlook.of(state, content, config, forge) ?: return null
    return ForgeOutlookUi(
        role = o.role, suits = o.wielders,
        parts = o.parts.filter { it.role.isNotEmpty() }.map { OutlookPartUi(it.name, it.role, it.constraint, it.constraintKind) },
        needs = o.missing.map { (id, needs) -> OutlookNeedUi(o.certain.rule(id)?.effect?.name ?: id, needs.map(Capabilities::text)) },
        tradeoff = o.tradeoff, tradeoffKind = o.tradeoffKind,
        possible = o.possible.map { OutlookChanceUi(it.name, it.text) },
        certain = o.certain.rules.map { it.ui() },
        notes = o.notes.map { Capabilities.text(it, o.certain) },
        demonstration = Bench.demonstration(forge.familyId, forge.coreId, forge.augmentId, forge.catalystId, content, config, (o.qualityLow + o.qualityHigh) / 2),
        qualityLow = o.qualityLow, qualityHigh = o.qualityHigh,
        phrases = o.certain.rules.mapNotNull { r -> effectPhrase(r.effect)?.let { r.id to it } }.toMap(),
    )
}

fun KnowledgeState.stage(): String = when (this) {
    KnowledgeState.UNKNOWN -> "Untried"
    KnowledgeState.OBSERVED -> "Studying"
    KnowledgeState.UNDERSTOOD -> "Learned"
    KnowledgeState.SIGNATURE_DISCOVERED -> "Signature discovered"
}

/** A pairing as the journal allows it to be told (`Journal.hint`); an untried one has no assessment at all. */
fun GameEngine.note(journal: JournalModel, label: String, key: String): NoteUi {
    val state = journal.state(key)
    if (state == KnowledgeState.UNKNOWN) return NoteUi(label, Journal.subjectName(content, key), state, state.stage(), "Forge to learn")
    return NoteUi(label, Journal.subjectName(content, key), state, state.stage(), Journal.hint(journal, content, key))
}

/**
 * What the journal's clues say of the signature hidden in [forge]'s weapon, metal and augment, beside the recipe; null
 * unless the journal already holds that one is hidden there (nothing is told of a recipe the smith has no clue to).
 * Only the clues held are read against the draft: a clue it misses is named, one it meets is not counted twice, and what
 * the clues do not cover is said to be unknown. Quality is always the roll's, and a signature is never promised.
 */
fun GameEngine.signatureNote(journal: JournalModel, forge: Command.Forge): NoteUi? {
    val def = SignatureCatalog.forRecipe(forge) ?: return null
    val have = Journal.rungs(journal, def)
    if (ClueRung.RECIPE !in have) return null
    val subject = Journal.subjectName(content, def.journalKey)
    if (journal.state(def.journalKey) == KnowledgeState.SIGNATURE_DISCOVERED) {
        val misses = def.misses(forge, def.minQuality).map { Journal.clue(def, it.rung, config) }
        return NoteUi("Signature", subject, KnowledgeState.SIGNATURE_DISCOVERED, def.name,
            if (misses.isEmpty()) "Your draft is the recipe. Quality is decided by the roll." else (listOf("Your draft is not the recipe yet") + misses).joinSentences())
    }
    val missed = def.misses(forge, def.minQuality).filter { it.rung in have }.map { Journal.clue(def, it.rung, config) }
    val complete = have.size == ClueRung.entries.size
    return NoteUi(
        "Signature", subject, if (complete) KnowledgeState.UNDERSTOOD else KnowledgeState.OBSERVED, if (complete) "All clues found" else "Incomplete clues",
        when {
            missed.isNotEmpty() -> (listOf("Your draft misses a clue") + missed).joinSentences()
            complete -> "Your draft matches every clue. Quality is decided by the roll, and a signature is never certain."
            else -> "Your draft matches the clues you have. The rest are still unknown."
        },
    )
}

/** [bellows]: the forge will be sent with the Ashen Bellows (asked for and still possible), which the outlook has to count. */
fun GameEngine.forgeWorkbench(state: GameState, draft: ForgeDraft, requests: List<RequestUi>, bellows: Boolean = false): ForgeWorkbenchUi {
    val advanced = draft.mode == ForgeMode.ADVANCED
    val family = draft.familyId?.let(content::family)
    val core = draft.coreId?.let(content::material)
    val augment = draft.augmentId?.let(content::material)
    // Quick never carries a catalyst or a technique, whatever the draft still holds.
    val catalyst = draft.catalystId?.takeIf { advanced }?.let(content::material)
    val technique = draft.technique?.takeIf { advanced }

    val slots = buildList {
        add(SlotUi(RecipeSlot.WEAPON, family?.name))
        add(SlotUi(RecipeSlot.METAL, core?.name, core?.let { state.materials[it.id] ?: 0 }))
        add(SlotUi(RecipeSlot.AUGMENT, augment?.name, augment?.let { state.materials[it.id] ?: 0 }))
        if (advanced) {
            add(SlotUi(RecipeSlot.CATALYST, catalyst?.name, catalyst?.let { state.materials[it.id] ?: 0 }))
            add(SlotUi(RecipeSlot.TECHNIQUE, technique?.let(Labels::technique)))
        }
    }
    val next = when { family == null -> RecipeSlot.WEAPON; core == null -> RecipeSlot.METAL; augment == null -> RecipeSlot.AUGMENT; else -> null }
    val plain = if (family != null && core != null && augment != null) Command.Forge(draft.mode, family.id, core.id, augment.id, catalyst?.id, draft.risk, technique) else null
    // Asked for and still possible: a draft that has lost its Heartfire, or gone back to Quick, forges without it.
    val heartfire = heartfireUi(state, advanced, draft.heartfire, plain)
    val command = plain?.copy(heartfire = heartfire.on)

    val cost = (if (advanced) config.advancedForgeEnergy else config.quickForgeEnergy) + if (heartfire.on) config.depth.legend.heartfireEnergy else 0
    val overwork = (cost - state.energy).coerceAtLeast(0)
    val canAfford = overwork <= config.maxOverworkPerDay - state.overworkToday
    val missing = listOfNotNull(core, augment, catalyst).firstOrNull { (state.materials[it.id] ?: 0) == 0 }
    val label = if (heartfire.on) "Forge with Heartfire · $cost energy" else "Forge · $cost energy"
    val action = when {
        next == RecipeSlot.WEAPON -> ForgeActionUi(label, false, "Choose a weapon type")
        next == RecipeSlot.METAL -> ForgeActionUi(label, false, "Choose a metal")
        next == RecipeSlot.AUGMENT -> ForgeActionUi(label, false, "Choose an infusion")
        missing != null -> ForgeActionUi(label, false, "No ${missing.name} left", restock = missing.id)
        !canAfford -> ForgeActionUi(label, false, "Not enough energy left today", endDay = true)
        overwork > 0 -> ForgeActionUi(label, true, "You'll have $overwork less energy tomorrow")
        else -> ForgeActionUi(label, true, null)
    }

    val journal = state.legacy.journal
    val notes = if (core == null || augment == null) emptyList() else listOfNotNull(
        note(journal, "Metal + infusion", JournalModel.coreAugmentKey(core.id, augment.id)),
        family?.let { note(journal, "Infusion + weapon", JournalModel.augmentFamilyKey(augment.id, it.id)) },
        plain?.let { signatureNote(journal, it) },
    )

    val brief = draft.commissionId?.let { commissionBrief(state, it, requests, family?.id, augment?.element) }
        ?: draft.wantHeroId?.let { wantBrief(state, it, family?.id) }

    val outlook = command?.let { forgeOutlook(state, it.copy(bellows = bellows)) }
    return ForgeWorkbenchUi(
        title = when { family != null && core != null -> "${core.name} ${family.name.lowercase()}"; family != null -> family.name; else -> "Empty anvil" },
        slots = slots, next = next, notes = notes, brief = brief, action = action, command = command,
        outlook = outlook,
        heartfire = heartfire,
        quality = outlook?.let { it.qualityLow to it.qualityHigh } ?: command?.let { ForgeOutlook.of(state, content, config, it.copy(bellows = bellows)) }?.let { it.qualityLow to it.qualityHigh },
    )
}

/** Where a blade of the shop is, for a stock line. */
private fun where(w: Weapon) = w.listedPrice?.let { "On the shelf at $it gold" } ?: "In storage"

/** How many stock lines a brief lists: the fitting ones first. */
private const val BRIEF_STOCK = 3

/**
 * The commission [id] beside the draft: its asks against the draft, and the blades of its type the shop holds, read by
 * `Commissions.fit` and `Commissions.pick` (one reserved for another commission never fits). One that has closed says how.
 */
private fun GameEngine.commissionBrief(state: GameState, id: CommissionId, requests: List<RequestUi>, family: WeaponFamilyId?, element: Element?): BriefUi {
    val c = state.commissions[id]
    val r = requests.firstOrNull { it.id == id }
    if (c == null || r == null || (c.status != CommissionStatus.OFFERED && c.status != CommissionStatus.ACCEPTED)) {
        val buyer = c?.let { state.heroes[it.buyerId]?.name }
        return BriefUi(
            if (buyer != null) "$buyer's commission" else "Commission", emptyList(), accepted = false, status = "Closed",
            gone = when (c?.status) {
                CommissionStatus.COMPLETED -> "It has been completed."
                CommissionStatus.EXPIRED -> "It expired before a weapon was handed over."
                CommissionStatus.DECLINED -> "It was declined."
                else -> "It is no longer open."
            } + " You can keep forging freely.",
            id = id,
        )
    }
    val accepted = c.status == CommissionStatus.ACCEPTED
    val picked = if (accepted) Commissions.pick(state.weapons.values, c, config) else null
    val stock = (state.storedWeapons() + state.listedWeapons()).filter { it.familyId == c.familyId }.map { w ->
        val elsewhere = w.promisedTo != null && w.promisedTo != c.id
        val fits = !elsewhere && Commissions.fit(w, c) == Commissions.Fit.OK
        StockFitUi(
            w.id, w.name,
            listOfNotNull(where(w), if (elsewhere) "reserved for another commission" else Labels.fit(w, c, content), "End Day would hand this over".takeIf { picked?.id == w.id }).joinToString(" · "),
            fits, handsOver = picked?.id == w.id,
        )
    }.sortedBy { !it.fits }
    return BriefUi(
        "${r.buyer.hero?.name ?: r.buyer.name}'s commission · ${r.reward} gold · ${r.due}",
        listOfNotNull(
            AskUi(r.family, family == c.familyId),
            r.element?.let { e -> AskUi(e.word(), element == e) },
            AskUi("Quality ${r.minQuality}+", null),
        ),
        accepted = accepted, status = if (accepted) "Accepted" else "Not accepted yet",
        stock = stock.take(BRIEF_STOCK),
        stockNote = when { stock.isEmpty() -> "No ${r.family.lowercase()} in stock."; stock.none { it.fits } -> "Nothing in stock fits yet."; else -> null },
        id = id,
    )
}

/**
 * The standing want of [heroId] beside the draft (`Hero.want`, in `Lines.want`'s words): never an order, nothing is
 * accepted or set aside. A listed blade is read by the counter's own rule (`Market.answersWant`, no draw); a stored one
 * cannot be, since only the shelf is browsed, so it is said as facts. A want that has gone says why.
 */
private fun GameEngine.wantBrief(state: GameState, heroId: HeroId, family: WeaponFamilyId?): BriefUi {
    val hero = state.heroes[heroId]
    val want = hero?.takeIf { it.isAlive }?.want
    if (hero == null || want == null) return BriefUi(
        "${hero?.fullName ?: "A customer"}'s want", emptyList(), accepted = false, status = "Gone",
        gone = when {
            hero == null -> "They are no longer in town."
            hero.fate == HeroFate.DEAD -> "${hero.fullName} has died."
            !hero.isAlive -> "${hero.fullName} has left town."
            else -> "${hero.fullName} is no longer waiting for a weapon."
        } + " You can keep forging freely.",
        heroId = heroId,
    )
    val name = content.family(want.familyId).name
    val ctx = ResolutionContext(state, content, config)   // read only: the counter's own rule, no draw
    val stock = (state.listedWeapons() + state.storedWeapons()).filter { it.familyId == want.familyId }.map { w ->
        val suits = w.isListed && Market.answersWant(ctx, hero, w)
        StockFitUi(
            w.id, w.name,
            if (w.isListed) "${where(w)} · ${if (suits) "suits their want" else "not what they would take today"}" else "In storage, not for sale · power ${w.power}",
            suits,
        )
    }.sortedBy { !it.fits }
    return BriefUi(
        Lines.want(hero, content) ?: "${hero.fullName}'s want",
        listOf(AskUi(name, family == want.familyId), AskUi("Power ${want.minPower}+", null)),
        accepted = false, status = "Not an order",
        stock = stock.take(BRIEF_STOCK),
        stockNote = if (stock.isEmpty()) "No ${name.lowercase()} in stock." else null,
        heroId = heroId,
    )
}

/** What a slot's tray offers, in catalog order, with the materials that have run out after those that can be used. */
fun GameEngine.forgeOptions(state: GameState, draft: ForgeDraft, slot: RecipeSlot, threat: ThreatUi?): List<OptionUi> {
    // In a guild run a metal, an augment and a catalyst are also a rule in a fight: said where they are chosen, in the catalog's words.
    val guild = state.guild != null
    fun materials(category: MaterialCategory, chosen: MaterialId?) = content.materials(category).map { m ->
        val stock = state.materials[m.id] ?: 0
        OptionUi(
            m.id.value, m.name, stock, chosen == m.id, m.element?.let { threat?.marks?.get(it) }, m.element?.let { "${it.word()} element" },
            fight = if (guild) materialRules(content, m.id).map { "${it.name}: ${it.description}" } else emptyList(),
            blocked = if (stock > 0) null else "None left. " + (roadLine(state, m.id) ?: "Buy it in Supplies."),
        )
    }.sortedBy { !it.usable }
    return when (slot) {
        // Who will want it, said where the type is chosen: the same classes the Shop names on a stock row.
        RecipeSlot.WEAPON -> content.families.map { f ->
            val fans = fans(f.id)
            OptionUi(f.id.value, f.name, null, draft.familyId == f.id, detail = when (fans.size) { 0 -> null; 1 -> "suits ${fans[0]}"; else -> "suits ${fans.dropLast(1).joinToString(", ")} and ${fans.last()}" })
        }
        RecipeSlot.METAL -> materials(MaterialCategory.CORE, draft.coreId)
        RecipeSlot.AUGMENT -> materials(MaterialCategory.AUGMENT, draft.augmentId)
        RecipeSlot.CATALYST -> listOf(OptionUi(null, "None", null, draft.catalystId == null)) + materials(MaterialCategory.CATALYST, draft.catalystId)
        RecipeSlot.TECHNIQUE -> listOf(OptionUi(null, "Plain", null, draft.technique == null)) +
            Technique.entries.map { OptionUi(it.name, Labels.technique(it), null, draft.technique == it, detail = Labels.techniqueExplanation(it)) }
    }
}

/** The out-of-stock tile the player tapped, as the tray stands now: null once it has been restocked, so "No X left" never outlives the purchase. */
fun staleFree(inspectedId: String?, options: List<OptionUi>): OptionUi? = options.firstOrNull { it.id != null && it.id == inspectedId && !it.usable }

/** The draft with [option] placed in [slot]; an id this slot does not know leaves the draft as it was. */
fun GameEngine.place(draft: ForgeDraft, slot: RecipeSlot, option: OptionUi): ForgeDraft = when (slot) {
    RecipeSlot.WEAPON -> content.families.firstOrNull { it.id.value == option.id }?.let { draft.copy(familyId = it.id) } ?: draft
    RecipeSlot.METAL -> option.id?.let { draft.copy(coreId = MaterialId(it)) } ?: draft
    RecipeSlot.AUGMENT -> option.id?.let { draft.copy(augmentId = MaterialId(it)) } ?: draft
    RecipeSlot.CATALYST -> draft.copy(catalystId = option.id?.let(::MaterialId))
    RecipeSlot.TECHNIQUE -> draft.copy(technique = Technique.entries.firstOrNull { it.name == option.id })
}

/** The draft forged in [mode]: Quick drops the catalyst, the technique and the Heartfire, which only an Advanced forge takes. */
fun ForgeDraft.inMode(mode: ForgeMode): ForgeDraft = copy(
    mode = mode, catalystId = catalystId?.takeIf { mode == ForgeMode.ADVANCED }, technique = technique?.takeIf { mode == ForgeMode.ADVANCED }, heartfire = heartfire && mode == ForgeMode.ADVANCED,
)

/** The id the draft holds in [slot] (a family, a material or a technique), null for nothing chosen, "None" or "Plain". */
fun ForgeDraft.idIn(slot: RecipeSlot): String? = when (slot) {
    RecipeSlot.WEAPON -> familyId?.value
    RecipeSlot.METAL -> coreId?.value
    RecipeSlot.AUGMENT -> augmentId?.value
    RecipeSlot.CATALYST -> catalystId?.value
    RecipeSlot.TECHNIQUE -> technique?.name
}

/** The draft with [slot] set back to [id], an empty place included: the tray's baseline and "Put back". Nothing else moves. */
fun ForgeDraft.withSlot(slot: RecipeSlot, id: String?): ForgeDraft = when (slot) {
    RecipeSlot.WEAPON -> copy(familyId = id?.let(::WeaponFamilyId))
    RecipeSlot.METAL -> copy(coreId = id?.let(::MaterialId))
    RecipeSlot.AUGMENT -> copy(augmentId = id?.let(::MaterialId))
    RecipeSlot.CATALYST -> copy(catalystId = id?.let(::MaterialId))
    RecipeSlot.TECHNIQUE -> copy(technique = Technique.entries.firstOrNull { it.name == id })
}

/** What one place holds, by name, for the comparison's "from": the tray's own word for nothing chosen. */
private fun GameEngine.nameIn(draft: ForgeDraft, slot: RecipeSlot): String = when (slot) {
    RecipeSlot.WEAPON -> draft.familyId?.let { content.family(it).name }
    RecipeSlot.METAL -> draft.coreId?.let { content.material(it).name }
    RecipeSlot.AUGMENT -> draft.augmentId?.let { content.material(it).name }
    RecipeSlot.CATALYST -> draft.catalystId?.let { content.material(it).name }
    RecipeSlot.TECHNIQUE -> draft.technique?.let(Labels::technique)
} ?: slot.empty

/**
 * How [draft] differs from [baseline], the draft its tray was opened on, in the place [slot] being changed: each line a
 * fact the save, the catalog and the journal already give (stock and price, the element and how the besieger takes it,
 * the most a metal can reach, the purpose's asks, what the journal may say of the pairings and, in a guild run, the
 * rules every forge of the recipe carries). Never a score, a forecast or a hidden recipe. Null when the place was empty
 * when the tray opened (nothing to compare with). An empty list only when the draft is back where it started. Pure.
 */
fun GameEngine.trayChange(state: GameState, baseline: ForgeDraft, draft: ForgeDraft, slot: RecipeSlot, requests: List<RequestUi>, threat: ThreatUi?): TrayChangeUi? {
    if (slot.ordinal <= RecipeSlot.AUGMENT.ordinal && baseline.idIn(slot) == null) return null
    val from = nameIn(baseline, slot)
    if (baseline == draft) return TrayChangeUi(from, emptyList())
    val changes = mutableListOf<ChangeUi>()
    fun material(d: ForgeDraft) = when (slot) {
        RecipeSlot.METAL -> d.coreId; RecipeSlot.AUGMENT -> d.augmentId; RecipeSlot.CATALYST -> d.catalystId; else -> null
    }?.let(content::material)
    val was = material(baseline)
    val now = material(draft)
    if (slot == RecipeSlot.METAL || slot == RecipeSlot.AUGMENT || slot == RecipeSlot.CATALYST) {
        if (now != null) changes += ChangeUi("Costs ${now.price} gold each" + (was?.let { " (${it.name} costs ${it.price})" } ?: "") + ". This forge uses 1.", was?.let { now.price <= it.price })
        else if (was != null) changes += ChangeUi("No catalyst. Your ${was.name} is kept.")
    }
    if (slot == RecipeSlot.METAL && was != null && now != null) {
        val epicWas = Forge.holdsEpic(was.tier, config)
        val epicNow = Forge.holdsEpic(now.tier, config)
        if (epicWas != epicNow) changes += ChangeUi(if (epicNow) "Rarity can reach Epic" else "Rarity stops at Rare", epicNow)
        val capWas = Forge.qualityCap(was.tier, config)
        val capNow = Forge.qualityCap(now.tier, config)
        if (capWas != capNow) changes += ChangeUi("Quality cap $capNow instead of $capWas", capNow > capWas)
    }
    if (slot == RecipeSlot.AUGMENT && was?.element != now?.element) {
        now?.element?.let { e -> changes += ChangeUi("${e.word()} element instead of ${was?.element?.word() ?: "none"}") }
        val markWas = was?.element?.let { threat?.marks?.get(it) }
        val markNow = now?.element?.let { threat?.marks?.get(it) }
        if (markNow != markWas) changes += markNow?.let { ChangeUi("Against the siege: ${it.label}", it.counters) } ?: ChangeUi("Against the siege, no longer: ${markWas!!.label}", markWas!!.counters.not())
    }
    if (slot == RecipeSlot.WEAPON) {
        draft.familyId?.let { f -> fans(f).takeIf { it.isNotEmpty() }?.let { changes += ChangeUi("Suits ${it.joinToString(", ")}") } }
    }
    if (slot == RecipeSlot.TECHNIQUE) draft.technique?.let { changes += ChangeUi(Labels.techniqueExplanation(it)) }
    // The purpose's asks that this change answers or stops answering.
    val before = forgeWorkbench(state, baseline, requests)
    val after = forgeWorkbench(state, draft, requests)
    before.brief?.asks.orEmpty().zip(after.brief?.asks.orEmpty()).forEach { (a, b) ->
        if (a.text == b.text && a.matches != b.matches && b.matches != null) changes += ChangeUi(if (b.matches) "Now matches: ${b.text}" else "No longer matches: ${b.text}", b.matches)
    }
    // What the journal may say of each pairing, where it changed: its own words for the stage reached, an untried one nothing.
    after.notes.forEach { n ->
        val old = before.notes.firstOrNull { it.label == n.label }
        if (old == null || old.subject != n.subject) changes += ChangeUi("${n.label}: ${if (n.state == KnowledgeState.UNKNOWN) "untried" else if (n.label == "Signature") n.stage.lowercase() else n.hint}", rule = true)
    }
    // A guild run: the rules every forge of the recipe carries, gained and lost; what they now wait for.
    val rulesWas = before.outlook?.certain.orEmpty().map { it.name }.toSet()
    val rulesNow = after.outlook?.certain.orEmpty().map { it.name }.toSet()
    if (before.outlook != null && after.outlook != null) {
        fun ForgeOutlookUi.named(name: String) = certain.firstOrNull { it.name == name }?.let { label(it) } ?: name
        (rulesNow - rulesWas).forEach { changes += ChangeUi("Gains the effect ${after.outlook.named(it)}", true, rule = true) }
        (rulesWas - rulesNow).forEach { changes += ChangeUi("Loses the effect ${before.outlook.named(it)}", false, rule = true) }
        after.outlook.needs.filter { n -> n !in before.outlook.needs }.forEach { n -> changes += ChangeUi("${n.rule} needs ${n.needs.joinToString(" or ")}", false, rule = true) }
        before.outlook.needs.filter { n -> n.rule in rulesNow && after.outlook.needs.none { it.rule == n.rule } }.forEach { n -> changes += ChangeUi("${n.rule} now has what it needs", true, rule = true) }
        // Only a real drawback is called one and counted against the change; a condition or a comparison is said plainly.
        if (after.outlook.tradeoff != null && after.outlook.tradeoff != before.outlook.tradeoff) {
            val drawback = after.outlook.tradeoffKind == ConstraintKind.DRAWBACK
            changes += ChangeUi((if (drawback) "Drawback: " else "") + after.outlook.tradeoff, if (drawback) false else null, rule = true)
        }
    }
    // A change no fact above speaks to (Plain for a technique, say) is still a change: never "Unchanged".
    if (changes.isEmpty()) changes += ChangeUi("Now ${nameIn(draft, slot)}")
    return TrayChangeUi(from, changes)
}

internal fun com.tinyblacksmith.core.content.Element.word() = Terms.element(this)

/** One thing a forge added to the journal: what kind of gain, and the pairing or recipe in the journal's own words for the stage reached. */
@Immutable data class LearnedUi(val title: String, val line: String)

/**
 * What the forge just done taught. [note] stands in when nothing moved: a repeat still short of understanding, or a
 * recipe already known. [attempt] is what the forge was as an attempt at a signature the journal knows of, read from the
 * command and the weapon: every condition met and it did not take, or a condition the clues name that it missed.
 */
@Immutable data class ForgeLearningUi(val changes: List<LearnedUi>, val note: String?, val attempt: String? = null)

/**
 * The journal [before] and [after] one accepted forge, as the result card says it. Every sentence is `Journal.hint` on
 * the journal as it now stands, so an observed pairing stays tentative; nothing is read from the day's event text.
 */
fun GameEngine.forgeLearning(before: JournalModel, after: JournalModel, forged: Command.Forge, weapon: Weapon? = null): ForgeLearningUi {
    val pairings = listOf(JournalModel.coreAugmentKey(forged.coreId, forged.augmentId), JournalModel.augmentFamilyKey(forged.augmentId, forged.familyId))
    val learned = pairings.filter { after.state(it).ordinal > before.state(it).ordinal }.map { key ->
        LearnedUi(if (after.state(key) == KnowledgeState.OBSERVED) "First notes" else "Combination learned", "${Journal.subjectName(content, key)}: ${Journal.hint(after, content, key)}")
    }
    val recipes = after.interactions.keys.filter { it.startsWith("sig:") }.sorted().mapNotNull { key ->
        when {
            after.state(key) == KnowledgeState.SIGNATURE_DISCOVERED && before.state(key) != KnowledgeState.SIGNATURE_DISCOVERED -> LearnedUi("Signature discovered", Journal.hint(after, content, key))
            after.state(key) != KnowledgeState.SIGNATURE_DISCOVERED && (after.state(key) != before.state(key) || after.signatureClues[key] != before.signatureClues[key]) -> LearnedUi("Recipe clue found", "${Journal.subjectName(content, key)}: ${Journal.hint(after, content, key)}")
            else -> null
        }
    }
    val changes = learned + recipes
    val studying = pairings.any { after.state(it) == KnowledgeState.OBSERVED }
    val attempt = weapon?.let { signatureAttempt(before, after, forged, it) }
    return ForgeLearningUi(
        changes,
        when {
            changes.isNotEmpty() || attempt != null -> null
            studying -> "Experiment recorded. Try this combination again to learn more."
            else -> "No new findings. You've already learned these combinations."
        },
        attempt,
    )
}

/**
 * The forge as an attempt at the signature hidden in its recipe, from the accepted command and the weapon it made (its
 * quality, its signature), said only as far as the journal's clues reach after it: "every condition met" needs every
 * clue, and a missed condition is named only when a clue held before the forge names it. A first discovery is already among the changes and a
 * clue just earned speaks for itself, so both are null here. No odds, no roll: what happened, in the clues' words.
 */
private fun GameEngine.signatureAttempt(before: JournalModel, after: JournalModel, forged: Command.Forge, weapon: Weapon): String? {
    val def = SignatureCatalog.forRecipe(forged) ?: return null
    if (weapon.signatureId == def.id) return if (before.state(def.journalKey) == KnowledgeState.SIGNATURE_DISCOVERED) "The known signature took again: ${def.name}." else null
    val have = Journal.rungs(after, def)
    if (ClueRung.RECIPE !in have) return null
    val misses = def.misses(forged, weapon.quality)
    if (misses.isEmpty()) return if (have.size == ClueRung.entries.size) "This forge met every condition of the recipe. The signature did not take this time. It is never certain." else null
    // A condition is named only when a clue held before this forge names it: a clue it just earned has its own line.
    val held = Journal.rungs(before, def)
    val named = misses.filter { it.rung in held }.map {
        when (it) {
            SignatureDef.Miss.QUALITY -> "its quality fell short of what the recipe needs"
            else -> Journal.clue(def, it.rung, config)
        }
    }
    return if (named.isEmpty()) null else (listOf("No signature could form") + named).joinSentences()
}

/**
 * The first metal and augment, in catalog order, that the smith has in stock and has never forged together. A draft
 * suggestion only: it reads no affinity, no hidden recipe and no RNG, and spends nothing.
 */
fun GameEngine.untriedPairing(state: GameState): Pair<MaterialId, MaterialId>? {
    fun stocked(category: MaterialCategory) = content.materials(category).filter { (state.materials[it.id] ?: 0) > 0 }
    val journal = state.legacy.journal
    for (core in stocked(MaterialCategory.CORE)) for (augment in stocked(MaterialCategory.AUGMENT)) {
        if (journal.state(JournalModel.coreAugmentKey(core.id, augment.id)) == KnowledgeState.UNKNOWN) return core.id to augment.id
    }
    return null
}

/** The core metals that hold Epic work (`LegendConfig.fineCoreTier`), named for a sentence: "Starsteel and Moonsteel". */
fun GameEngine.fineMetals(): String = content.materials(MaterialCategory.CORE).filter { it.tier >= config.depth.legend.fineCoreTier }.sortedBy { it.tier }.joinToString(" and ") { it.name }
