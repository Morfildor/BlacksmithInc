package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.example.blacksmithproject.ForgeDraft
import com.tinyblacksmith.core.content.MaterialCategory
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

/** The places of a recipe on the workbench. The last two exist only in an Advanced forge. */
enum class RecipeSlot(val label: String, val empty: String, val choose: String, val hint: String) {
    WEAPON("Weapon", "Choose", "Choose weapon", "Choose the shape of your next weapon."),
    METAL("Metal", "Choose", "Choose metal", "The metal is the body of the blade."),
    AUGMENT("Augment", "Choose", "Choose augment", "An augment adds an element."),
    CATALYST("Catalyst", "None", "Choose catalyst", "Optional. A catalyst is used up by the forge."),
    TECHNIQUE("Technique", "Plain", "Choose technique", "Optional. How the blade is finished."),
}

/** One place of the recipe: [value] is the chosen thing's name, null while nothing is chosen. */
@Immutable data class SlotUi(val slot: RecipeSlot, val value: String?, val stock: Int? = null)

/**
 * One choice in a slot's tray. [id] is the family, material or technique it stands for (null: "None" or "Plain");
 * [stock] is what the smith owns of a material (null for what is not counted). An empty material is listed last.
 */
@Immutable data class OptionUi(val id: String?, val name: String, val stock: Int?, val selected: Boolean, val mark: MarkUi? = null, val detail: String? = null) {
    val usable: Boolean get() = stock != 0
}

/** One relationship of the recipe as the journal knows it today. An untried one says nothing of how it will go. */
@Immutable data class NoteUi(
    val label: String, val subject: String, val state: KnowledgeState, val stage: String, val hint: String,
    /** Which way the pairing leans, for the colour of [hint]: only ever what [hint] already says in words, and nothing for an untried one. */
    val tone: EffectKind = EffectKind.NEUTRAL,
)

/** One requirement of a commission; [matches] is null where the draft cannot answer it (quality is known only after forging). */
@Immutable data class AskUi(val text: String, val matches: Boolean?)

/** The commission this draft was started from. Nothing is reserved for it: [accepted] is the request's own status. */
@Immutable data class BriefUi(val id: CommissionId, val title: String, val asks: List<AskUi>, val accepted: Boolean)

/**
 * The one action of the Forge. [note] is the single thing in its way, or the price of overwork; [restock] the material
 * that ran out; [endDay] when no forge is possible today and resting is the way forward.
 */
@Immutable data class ForgeActionUi(val label: String, val enabled: Boolean, val note: String?, val restock: MaterialId? = null, val endDay: Boolean = false)

/**
 * The Forge destination for one save and one draft. [next] is the first place still empty; [command] is exactly what
 * the action sends (null until the three ingredients are chosen). Pure: reads the save, changes nothing.
 */
@Immutable
data class ForgeWorkbenchUi(
    val title: String, val slots: List<SlotUi>, val next: RecipeSlot?, val notes: List<NoteUi>, val brief: BriefUi?, val action: ForgeActionUi, val command: Command.Forge?,
)

fun KnowledgeState.stage(): String = when (this) {
    KnowledgeState.UNKNOWN -> "Untried"
    KnowledgeState.OBSERVED -> "Observed"
    KnowledgeState.UNDERSTOOD -> "Understood"
    KnowledgeState.SIGNATURE_DISCOVERED -> "Signature discovered"
}

/** A pairing as the journal allows it to be told (`Journal.hint`); an untried one has no assessment at all. */
fun GameEngine.note(journal: JournalModel, label: String, key: String): NoteUi {
    val state = journal.state(key)
    if (state == KnowledgeState.UNKNOWN) return NoteUi(label, Journal.subjectName(content, key), state, state.stage(), "Forge to learn")
    val affinity = Journal.affinityFor(content, key)
    return NoteUi(
        label, Journal.subjectName(content, key), state, state.stage(), Journal.hint(journal, content, key),
        tone = when { affinity > 0 -> EffectKind.BUFF; affinity < 0 -> EffectKind.FLAW; else -> EffectKind.NEUTRAL },
    )
}

fun GameEngine.forgeWorkbench(state: GameState, draft: ForgeDraft, requests: List<RequestUi>): ForgeWorkbenchUi {
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
    val command = if (family != null && core != null && augment != null) Command.Forge(draft.mode, family.id, core.id, augment.id, catalyst?.id, draft.risk, technique) else null

    val cost = if (advanced) config.advancedForgeEnergy else config.quickForgeEnergy
    val overwork = (cost - state.energy).coerceAtLeast(0)
    val canAfford = overwork <= config.maxOverworkPerDay - state.overworkToday
    val missing = listOfNotNull(core, augment, catalyst).firstOrNull { (state.materials[it.id] ?: 0) == 0 }
    val label = "Forge · $cost energy"
    val action = when {
        next == RecipeSlot.WEAPON -> ForgeActionUi(label, false, "Choose a weapon type")
        next == RecipeSlot.METAL -> ForgeActionUi(label, false, "Choose a metal")
        next == RecipeSlot.AUGMENT -> ForgeActionUi(label, false, "Choose an augment")
        missing != null -> ForgeActionUi(label, false, "No ${missing.name} left", restock = missing.id)
        !canAfford -> ForgeActionUi(label, false, "Not enough energy left today", endDay = true)
        overwork > 0 -> ForgeActionUi(label, true, "Overwork: $overwork less energy tomorrow")
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
    )
}

/** What a slot's tray offers, in catalog order, with the materials that have run out after those that can be used. */
fun GameEngine.forgeOptions(state: GameState, draft: ForgeDraft, slot: RecipeSlot, threat: ThreatUi?): List<OptionUi> {
    fun materials(category: MaterialCategory, chosen: MaterialId?) = content.materials(category).map { m ->
        OptionUi(m.id.value, m.name, state.materials[m.id] ?: 0, chosen == m.id, m.element?.let { threat?.marks?.get(it) }, m.element?.let { "Adds ${it.word()}" })
    }.sortedBy { !it.usable }
    return when (slot) {
        RecipeSlot.WEAPON -> content.families.map { OptionUi(it.id.value, it.name, null, draft.familyId == it.id) }
        RecipeSlot.METAL -> materials(MaterialCategory.CORE, draft.coreId)
        RecipeSlot.AUGMENT -> materials(MaterialCategory.AUGMENT, draft.augmentId)
        RecipeSlot.CATALYST -> listOf(OptionUi(null, "None", null, draft.catalystId == null)) + materials(MaterialCategory.CATALYST, draft.catalystId)
        RecipeSlot.TECHNIQUE -> listOf(OptionUi(null, "Plain", null, draft.technique == null)) +
            Technique.entries.map { OptionUi(it.name, Labels.technique(it), null, draft.technique == it, detail = Labels.techniqueExplanation(it)) }
    }
}

/** The draft with [option] placed in [slot]; an id this slot does not know leaves the draft as it was. */
fun GameEngine.place(draft: ForgeDraft, slot: RecipeSlot, option: OptionUi): ForgeDraft = when (slot) {
    RecipeSlot.WEAPON -> content.families.firstOrNull { it.id.value == option.id }?.let { draft.copy(familyId = it.id) } ?: draft
    RecipeSlot.METAL -> option.id?.let { draft.copy(coreId = MaterialId(it)) } ?: draft
    RecipeSlot.AUGMENT -> option.id?.let { draft.copy(augmentId = MaterialId(it)) } ?: draft
    RecipeSlot.CATALYST -> draft.copy(catalystId = option.id?.let(::MaterialId))
    RecipeSlot.TECHNIQUE -> draft.copy(technique = Technique.entries.firstOrNull { it.name == option.id })
}

internal fun com.tinyblacksmith.core.content.Element.word() = name.lowercase().replaceFirstChar { it.uppercase() }
