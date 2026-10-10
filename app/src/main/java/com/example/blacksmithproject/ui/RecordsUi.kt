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
import com.tinyblacksmith.core.model.LegendEntry
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
