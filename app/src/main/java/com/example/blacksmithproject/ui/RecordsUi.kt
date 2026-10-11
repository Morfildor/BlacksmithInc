package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.crafting.ClueRung
import com.tinyblacksmith.core.crafting.Journal
import com.tinyblacksmith.core.crafting.SignatureCatalog
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.model.Journal as JournalModel
import com.tinyblacksmith.core.model.KnowledgeState
import com.tinyblacksmith.core.engine.GameEngine
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
    ClueRung.TEMPER -> "Temper"
    ClueRung.QUALITY -> "Finish"
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
