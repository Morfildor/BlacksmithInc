package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.combat.Stat
import com.tinyblacksmith.core.content.CombatCatalog
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.crafting.ClueRung
import com.tinyblacksmith.core.crafting.Interactions
import com.tinyblacksmith.core.crafting.Journal
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.model.Journal as JournalModel
import com.tinyblacksmith.core.model.KnowledgeState
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegendEntry
import com.tinyblacksmith.core.model.MaterialId
import com.tinyblacksmith.core.model.WeaponFamilyId
import com.tinyblacksmith.core.shopday.Lines

/**
 * A Legend Board entry as `Lines.legend` tells it: [head] is the blade, its era, victories and fame; [lines] what it
 * was, who carried it, then its story. [lostToTime]: the entry is older than the record of its make, and its first
 * line says so instead of inventing one.
 */
@Immutable data class LegendUi(val head: String, val lines: List<String>, val lostToTime: Boolean)

fun legendUi(entry: LegendEntry, content: ContentCatalog, currentEra: Int): LegendUi =
    Lines.legend(entry, content, currentEra).let { LegendUi(it.first(), it.drop(1), entry.lostToTime) }

/** One rung of a signature's clue ladder: what it is about, and what the journal has learned of it (null while unearned). */
@Immutable data class RungUi(val label: String, val clue: String?)

/**
 * A signature's row of the journal: the four rungs of its ladder (`Journal.rungs`, each in the words of `Journal.clue`)
 * and, once it is found, the forge it asks for (`SignatureCatalog.recipe`) for "Use this recipe".
 */
@Immutable data class SignatureUi(val id: String, val rungs: List<RungUi>, val recipe: Command.Forge?)

private fun rungLabel(rung: ClueRung) = when (rung) {
    ClueRung.RECIPE -> "Recipe"
    ClueRung.CATALYST -> "Catalyst"
    ClueRung.TEMPER -> "Risk"
    ClueRung.QUALITY -> "Quality"
}

/** Null for a journal row that is not a signature's ("sig:" keys only). */
fun signatureUi(journal: JournalModel, key: String, content: ContentCatalog, config: BalanceConfig): SignatureUi? {
    if (!key.startsWith("sig:")) return null
    val def = SignatureCatalog.byId[key.substring(4)] ?: return null
    val have = Journal.rungs(journal, def)
    val found = journal.state(key) == KnowledgeState.SIGNATURE_DISCOVERED
    return SignatureUi(
        def.id,
        ClueRung.entries.map { rung ->
            RungUi(rungLabel(rung), when {
                rung !in have -> null
                // Found, the first rung no longer "hides something more": it is the recipe itself.
                rung == ClueRung.RECIPE && found -> Journal.subjectName(content, key)
                else -> Journal.clue(def, rung, config)
            })
        },
        SignatureCatalog.recipe(def).takeIf { found },
    )
}

/** A pairing the smith has tried, as a row of the notebook: [metalId] and [augmentId] for a metal pairing, [augmentId] and [familyId] for a weapon pairing. */
@Immutable
data class PairingUi(
    val key: String, val subject: String, val state: KnowledgeState, val stage: String, val hint: String,
    val metalId: MaterialId? = null, val augmentId: MaterialId? = null, val familyId: WeaponFamilyId? = null,
)

/**
 * The research notebook: only what the journal holds, in three kinds. A pairing never tried is not listed, and a
 * recipe is here only once the journal has a row for it. [counts] are the smith's own records, not a share of anything.
 */
@Immutable
data class NotebookUi(val metal: List<PairingUi>, val weapon: List<PairingUi>, val clues: List<String>) {
    val counts: String get() = (metal + weapon).let { all -> "${all.count { it.state == KnowledgeState.OBSERVED }} being studied · ${all.count { it.state == KnowledgeState.UNDERSTOOD }} learned" }
}

/**
 * The three kinds of fight note, which the page never mixes: a rule the smith has forged, a path the rules say could
 * happen, and a path a real fight has shown. Each has its own [word] and [sign], and its own colour beside them.
 */
enum class FightNoteKind(val word: String, val sign: String) { LEARNED("Learned", "●"), SUGGESTED("Possible, not seen yet", "?"), WITNESSED("Seen in a real fight", "✓") }

/** A fight rule the smith has forged on a weapon, in the catalog's own words. */
@Immutable data class LearnedRuleUi(val id: String, val name: String, val description: String, val stats: List<Stat> = emptyList())

/** A relationship between rules: [kind] is SUGGESTED until a real contract or siege has shown it. [known] are its rules the smith has forged, by name. */
@Immutable data class InteractionUi(val id: String, val name: String, val description: String, val tradeoff: String, val kind: FightNoteKind, val known: List<String>)

/** The notebook's fight notes (`Interactions.page`). [hidden] is how many paths no forged rule has opened yet, [hiddenLine] the sentence for them. */
@Immutable
data class FightNotesUi(val learned: List<LearnedRuleUi>, val paths: List<InteractionUi>, val hidden: Int) {
    val hiddenLine: String? get() = when {
        hidden <= 0 -> null
        hidden == 1 -> "1 more to find. It appears when you forge a weapon with one of its effects."
        else -> "$hidden more to find. Each appears when you forge a weapon with one of its effects."
    }
}

fun fightNotes(journal: JournalModel, combat: CombatCatalog): FightNotesUi {
    val page = Interactions.page(journal, combat)
    return FightNotesUi(
        page.learned.map { LearnedRuleUi(it.id, it.name, it.description, it.statuses()) },
        page.entries.map { e ->
            InteractionUi(e.def.id, e.def.name, e.def.description, e.def.constraint, if (e.evidence == Interactions.Evidence.WITNESSED) FightNoteKind.WITNESSED else FightNoteKind.SUGGESTED, e.known.map { it.name })
        },
        page.hidden,
    )
}

/** Null where the notebook has no fight notes to keep: a classic run whose journal holds no forged rule from an earlier guild run. */
fun GameEngine.fightNotes(state: GameState): FightNotesUi? {
    val journal = state.legacy.journal
    return content.combat?.takeIf { state.guild != null || journal.learnedRules.isNotEmpty() }?.let { fightNotes(journal, it) }
}

fun GameEngine.notebook(journal: JournalModel): NotebookUi {
    fun pairing(key: String): PairingUi {
        val n = note(journal, "", key)
        val ids = key.substring(3).split("|")
        return if (key.startsWith("ca:")) PairingUi(key, n.subject, n.state, n.stage, n.hint, metalId = MaterialId(ids[0]), augmentId = MaterialId(ids[1]))
        else PairingUi(key, n.subject, n.state, n.stage, n.hint, augmentId = MaterialId(ids[0]), familyId = WeaponFamilyId(ids[1]))
    }
    val keys = journal.interactions.keys.sorted()
    return NotebookUi(
        metal = keys.filter { it.startsWith("ca:") }.map(::pairing),
        weapon = keys.filter { it.startsWith("af:") }.map(::pairing),
        clues = keys.filter { it.startsWith("sig:") },
    )
}
