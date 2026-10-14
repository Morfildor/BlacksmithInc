package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.example.blacksmithproject.ForgeDraft
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.crafting.Bench
import com.tinyblacksmith.core.crafting.Capabilities
import com.tinyblacksmith.core.crafting.ForgeOutlook
import com.tinyblacksmith.core.crafting.Journal
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.Technique
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Journal as JournalModel
import com.tinyblacksmith.core.model.KnowledgeState
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.Weapon
import kotlin.math.roundToInt

/** The places of a recipe on the workbench. The last two exist only in an Advanced forge. */
enum class RecipeSlot(val label: String, val empty: String, val choose: String, val hint: String) {
    WEAPON("Weapon", "Choose", "Choose weapon", "Each weapon type suits some hero classes. Choose one to see which."),
    METAL("Metal", "Choose", "Choose metal", "Choose the metal you'll forge with."),
    AUGMENT("Augment", "Choose", "Choose augment", "Add fire, frost or another element."),
    CATALYST("Catalyst", "None", "Choose catalyst", "Optional. Improves the result and is used up when you forge."),
    TECHNIQUE("Technique", "Plain", "Choose technique", "Optional. Changes how you finish the weapon."),
}

/** One place of the recipe: [value] is the chosen thing's name, null while nothing is chosen. */
@Immutable data class SlotUi(val slot: RecipeSlot, val value: String?, val stock: Int? = null)

/**
 * One choice in a slot's tray. [id] is the family, material or technique it stands for (null: "None" or "Plain");
 * [stock] is what the smith owns of a material (null for what is not counted). An empty material is listed last.
 * [fight] (a guild run) is what a metal, an augment or a catalyst gives the blade in a fight, one "Name: rule" line each.
 */
@Immutable data class OptionUi(val id: String?, val name: String, val stock: Int?, val selected: Boolean, val mark: MarkUi? = null, val detail: String? = null, val fight: List<String> = emptyList()) {
    val usable: Boolean get() = stock != 0
}

/** One relationship of the recipe as the journal knows it today. An untried one says nothing of how it will go. */
@Immutable data class NoteUi(val label: String, val subject: String, val state: KnowledgeState, val stage: String, val hint: String)

/** One requirement of a commission; [matches] is null where the draft cannot answer it (quality is known only after forging). */
@Immutable data class AskUi(val text: String, val matches: Boolean?)

/** The commission this draft was started from. Nothing is reserved for it: [accepted] is the request's own status. */
@Immutable data class BriefUi(val id: CommissionId, val title: String, val asks: List<AskUi>, val accepted: Boolean)

/**
 * The one action of the Forge. [note] is the single thing in its way, or the price of overwork; [restock] the material
 * that ran out; [endDay] when no forge is possible today and resting is the way forward.
 */
@Immutable data class ForgeActionUi(val label: String, val enabled: Boolean, val note: String?, val restock: MaterialId? = null, val endDay: Boolean = false)

/** One chosen ingredient of the recipe: what it is for ([role]) and what choosing it costs ([constraint]). */
@Immutable data class OutlookPartUi(val name: String, val role: String, val constraint: String?)

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
)

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
)

/**
 * Heartfire on the workbench. [held] is always said. [blocked] is why the switch is not offered (Quick mode, none held),
 * null when it is. With the switch [on], [outlook] is what this forging spends and what it can give, from the engine's
 * own outlook, and [warning] says when a plain result cannot break through. [rule] and [source] are always shown: how a
 * weapon becomes Legendary, and where Heartfire comes from.
 */
@Immutable
data class HeartfireUi(val held: String, val on: Boolean, val blocked: String?, val outlook: List<String>, val warning: String?, val rule: String, val source: String)

private fun percent(chance: Double) = "${(chance * 100).roundToInt()}%"

/** What the temper does, with the numbers of the rules themselves. */
fun riskExplanation(r: Risk, config: BalanceConfig): String {
    val profile = config.risk.getValue(r)
    val spread = profile.rollSpread ?: config.qualityRollSpread
    val legend = config.depth.legend
    fun breakthrough(bonus: Double) = when { bonus < 0 -> " A Heartfire breakthrough is less likely."; bonus > 0 -> " A Heartfire breakthrough is more likely."; else -> "" }
    return when (r) {
        Risk.SAFE -> "Quality stays within $spread of what the recipe gives. Few flaws and few exceptional results." + breakthrough(legend.safeBonus)
        Risk.BALANCED -> "Quality moves up to $spread either way. A chance of exceptional work or flaws."
        Risk.RECKLESS -> "Adds ${if (profile.extraSlots == 1) "one more property" else "${profile.extraSlots} more properties"}. About ${percent(profile.defectChance)} come out flawed, and a flawed weapon is still usable." + breakthrough(legend.recklessBonus)
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
    return HeartfireUi(
        held = "Heartfire ${state.heartfire} of ${legend.heartfireMax}",
        on = on, blocked = blocked,
        outlook = when {
            !on -> emptyList()
            o == null -> listOf("Choose a weapon, a metal and an augment to see what it would do.")
            else -> listOf(
                "Costs 1 Heartfire and ${legend.heartfireEnergy} extra energy, ${o.energy} energy in all. Both are spent even if the weapon does not become Legendary.",
                "Quality will be between ${o.qualityLow} and ${o.qualityHigh}, before any exceptional result or flaw. Only a weapon of quality ${o.floor} or more can become Legendary.",
                when {
                    !o.fineMetal -> "Chance of Legendary: none in this metal."
                    o.chanceHigh <= 0.0 -> "Chance of Legendary: none at this quality."
                    o.chanceLow <= 0.0 -> "Chance of Legendary: none under quality ${o.floor}, up to ${percent(o.chanceHigh)} at quality ${o.qualityHigh}."
                    o.chanceLow == o.chanceHigh -> "Chance of Legendary: ${percent(o.chanceLow)}."
                    else -> "Chance of Legendary: ${percent(o.chanceLow)} at quality ${o.qualityLow}, up to ${percent(o.chanceHigh)} at quality ${o.qualityHigh}."
                },
                "If it does not become Legendary, the weapon still gets ${if (o.missSlots == 1) "one more property" else "${o.missSlots} more properties"}.",
            )
        },
        warning = o?.takeIf { !it.fineMetal }?.let { "Heartfire cannot make a weapon of this metal Legendary. Only ${fineMetals()} can reach Epic." }
            ?: o?.takeIf { it.qualityHigh < it.floor }?.let { "Without an exceptional result, this recipe stays under quality ${it.floor}, so it cannot become Legendary." },
        rule = "Without Heartfire, quality can reach Epic at most, and only in ${fineMetals()}. Weapons of other metals stop at Rare. Only Heartfire or great deeds make a weapon Legendary.",
        source = "Heartfire comes from dangerous work: a deep hall cleared, a taken weapon won back, a starfall site, a warlord defeated at the town wall.",
    )
}


/** Null in a classic run, where no fight reads a weapon's rules. [forge] is the command as it will be sent, bellows included. */
fun GameEngine.forgeOutlook(state: GameState, forge: Command.Forge): ForgeOutlookUi? {
    if (state.guild == null) return null
    val o = ForgeOutlook.of(state, content, config, forge) ?: return null
    return ForgeOutlookUi(
        role = o.role, suits = o.wielders,
        parts = o.parts.filter { it.role.isNotEmpty() }.map { OutlookPartUi(it.name, it.role, it.constraint) },
        needs = o.missing.map { (id, needs) -> OutlookNeedUi(o.certain.rule(id)?.effect?.name ?: id, needs.map(Capabilities::text)) },
        tradeoff = o.tradeoff,
        possible = o.possible.map { OutlookChanceUi(it.name, it.text) },
        certain = o.certain.rules.map { it.ui() },
        notes = o.notes.map { Capabilities.text(it, o.certain) },
        demonstration = Bench.demonstration(forge.familyId, forge.coreId, forge.augmentId, forge.catalystId, content, config, (o.qualityLow + o.qualityHigh) / 2),
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
        next == RecipeSlot.AUGMENT -> ForgeActionUi(label, false, "Choose an augment")
        missing != null -> ForgeActionUi(label, false, "No ${missing.name} left", restock = missing.id)
        !canAfford -> ForgeActionUi(label, false, "Not enough energy left today", endDay = true)
        overwork > 0 -> ForgeActionUi(label, true, "You'll have $overwork less energy tomorrow")
        else -> ForgeActionUi(label, true, null)
    }

    val journal = state.legacy.journal
    val notes = if (core == null || augment == null) emptyList() else listOfNotNull(
        note(journal, "Metal + augment", JournalModel.coreAugmentKey(core.id, augment.id)),
        family?.let { note(journal, "Augment + weapon", JournalModel.augmentFamilyKey(augment.id, it.id)) },
    )

    val brief = requests.firstOrNull { it.id == draft.commissionId }?.let { r ->
        BriefUi(
            r.id, "${(r.buyer.hero?.name ?: r.buyer.name)}'s commission · ${r.reward} gold · ${r.due}",
            listOfNotNull(
                AskUi(r.family, family?.name == r.family),
                r.element?.let { e -> AskUi(e.word(), augment?.element == e) },
                AskUi("Quality ${r.minQuality}+", null),
            ),
            accepted = !r.offered,
        )
    }

    return ForgeWorkbenchUi(
        title = when { family != null && core != null -> "${core.name} ${family.name.lowercase()}"; family != null -> family.name; else -> "Empty anvil" },
        slots = slots, next = next, notes = notes, brief = brief, action = action, command = command,
        outlook = command?.let { forgeOutlook(state, it.copy(bellows = bellows)) },
        heartfire = heartfire,
    )
}

/** What a slot's tray offers, in catalog order, with the materials that have run out after those that can be used. */
fun GameEngine.forgeOptions(state: GameState, draft: ForgeDraft, slot: RecipeSlot, threat: ThreatUi?): List<OptionUi> {
    // In a guild run a metal, an augment and a catalyst are also a rule in a fight: said where they are chosen, in the catalog's words.
    val guild = state.guild != null
    fun materials(category: MaterialCategory, chosen: MaterialId?) = content.materials(category).map { m ->
        OptionUi(
            m.id.value, m.name, state.materials[m.id] ?: 0, chosen == m.id, m.element?.let { threat?.marks?.get(it) }, m.element?.let { "Adds ${it.word()}" },
            fight = if (guild) materialRules(content, m.id).map { "${it.name}: ${it.description}" } else emptyList(),
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

internal fun com.tinyblacksmith.core.content.Element.word() = name.lowercase().replaceFirstChar { it.uppercase() }

/** One thing a forge added to the journal: what kind of gain, and the pairing or recipe in the journal's own words for the stage reached. */
@Immutable data class LearnedUi(val title: String, val line: String)

/** What the forge just done taught. [note] stands in when nothing moved: a repeat still short of understanding, or a recipe already known. */
@Immutable data class ForgeLearningUi(val changes: List<LearnedUi>, val note: String?)

/**
 * The journal [before] and [after] one accepted forge, as the result card says it. Every sentence is `Journal.hint` on
 * the journal as it now stands, so an observed pairing stays tentative; nothing is read from the day's event text.
 */
fun GameEngine.forgeLearning(before: JournalModel, after: JournalModel, forged: Command.Forge): ForgeLearningUi {
    val pairings = listOf(JournalModel.coreAugmentKey(forged.coreId, forged.augmentId), JournalModel.augmentFamilyKey(forged.augmentId, forged.familyId))
    val learned = pairings.filter { after.state(it).ordinal > before.state(it).ordinal }.map { key ->
        LearnedUi(if (after.state(key) == KnowledgeState.OBSERVED) "First notes" else "Pairing learned", "${Journal.subjectName(content, key)}: ${Journal.hint(after, content, key)}")
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
    return ForgeLearningUi(
        changes,
        when {
            changes.isNotEmpty() -> null
            studying -> "Experiment recorded. Try this pairing again to learn more."
            else -> "No new findings. You've already learned these pairings."
        },
    )
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
