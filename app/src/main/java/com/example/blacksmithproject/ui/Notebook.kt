package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.BuffGreen
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.ForgePanel
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.model.KnowledgeState

/** The three kinds of thing the notebook keeps apart. */
enum class BookTab(val label: String, val empty: String) {
    METAL("Metal pairings", "No metal pairings recorded yet. Forge something to begin."),
    WEAPON("Weapon pairings", "No weapon pairings recorded yet. Forge something to begin."),
    CLUES("Recipe clues", "No recipe clues recorded yet. Earned clues will appear here."),
}

private val Tab = RoundedCornerShape(7.dp)

/** The colour of a stage: gold while untried, cream once observed, green once understood. Always beside the stage's word. */
fun KnowledgeState.color() = when (this) { KnowledgeState.UNKNOWN -> Gold; KnowledgeState.OBSERVED -> Cream; else -> BuffGreen }

/**
 * The research notebook as rows of the Records list: what has been tried, by kind, each pairing with its ingredients'
 * art, its stage and what the journal may say of it, and "Use this pairing" to put it on the workbench (nothing is
 * forged). The pairing on the workbench now is marked. Recipe clues show only the rungs earned.
 */
fun LazyListScope.notebookItems(s: UiState.Playing, vm: GameViewModel, tab: BookTab, onTab: (BookTab) -> Unit) {
    val engine = vm.engine
    val journal = s.state.legacy.journal
    val book = engine.notebook(journal)
    val bench = with(s.draft) {
        setOfNotNull(
            if (coreId != null && augmentId != null) com.tinyblacksmith.core.model.Journal.coreAugmentKey(coreId, augmentId) else null,
            if (augmentId != null && familyId != null) com.tinyblacksmith.core.model.Journal.augmentFamilyKey(augmentId, familyId) else null,
        )
    }
    item(key = "book_head") {
        Column {
            SectionTitle("Notebook", Modifier.padding(top = Space.sm))
            Secondary("${book.counts}. Knowledge survives the forge's fall.", Modifier.testTag("book_counts"))
            FlowRow(Modifier.padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                BookTab.entries.forEach { t ->
                    val on = t == tab
                    Box(
                        Modifier.heightIn(min = 48.dp).clip(Tab).background(ForgePanel).border(1.dp, if (on) Gold else BronzeDeep, Tab).clickable(role = Role.Tab) { onTab(t) }
                            .padding(horizontal = 12.dp).testTag("book_tab_${t.name.lowercase()}").semantics { selected = on },
                        contentAlignment = Alignment.Center,
                    ) { Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (on) Gold else CreamMuted) }
                }
            }
        }
    }
    val pairings = when (tab) { BookTab.METAL -> book.metal; BookTab.WEAPON -> book.weapon; BookTab.CLUES -> emptyList() }
    if ((tab == BookTab.CLUES && book.clues.isEmpty()) || (tab != BookTab.CLUES && pairings.isEmpty())) {
        item(key = "book_empty") { Text(tab.empty, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.padding(top = Space.md).testTag("book_empty")) }
    }
    items(pairings, key = { "journal_${it.key}" }) { p -> PairingRow(p, onBench = p.key in bench, art = pairingArt(p, vm)) { vm.usePairing(p.key) } }
    if (tab == BookTab.CLUES) items(book.clues, key = { "journal_$it" }) { key ->
        Column {
            AffinityHint(journal, engine.content, key)
            signatureUi(journal, key, engine.content, engine.config)?.let { SignatureLadder(it, onUse = vm::useRecipe) }
        }
    }
    item(key = "book_try") {
        SecondaryActionButton("Try an untried pairing", vm::tryUntried, Modifier.fillMaxWidth().padding(top = Space.md).testTag("book_try"), detail = "Chooses ingredients only. Nothing is spent.")
    }
}

/** The two things a pairing joins, as sprites: metal and augment, or augment and the weapon type in plain metal. */
private fun pairingArt(p: PairingUi, vm: GameViewModel): List<Int> = listOfNotNull(
    p.metalId?.let(Sprites::material), p.augmentId?.let(Sprites::material),
    p.familyId?.let { Sprites.weapon(it, vm.engine.content.materials(MaterialCategory.CORE).first().id, null) },
)

@Composable
private fun PairingRow(p: PairingUi, onBench: Boolean, art: List<Int>, onUse: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.xs).forgeRow().then(if (onBench) Modifier.border(1.dp, Gold, RoundedCornerShape(4.dp)) else Modifier)
            .padding(start = Space.sm).testTag("book_row_${p.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        art.forEach { PixelImage(it, 36.dp, description = null) }
        Column(Modifier.weight(1f).padding(horizontal = Space.sm, vertical = Space.sm).semantics(mergeDescendants = true) { contentDescription = "${p.subject}, ${p.stage}: ${p.hint}${if (onBench) ". On the workbench now" else ""}" }) {
            Text(p.subject, style = MaterialTheme.typography.titleSmall, color = Cream)
            Text("${p.stage} · ${p.hint}", style = MaterialTheme.typography.bodySmall, color = p.state.color())
            if (onBench) Text("On the workbench", style = MaterialTheme.typography.labelSmall, color = Gold)
        }
        TextButton(onClick = onUse, modifier = Modifier.heightIn(min = 48.dp).testTag("book_use_${p.key}")) { Text("Use ›", color = Gold) }
    }
}

/** What the forge just done taught, on its result: each gain by name with the journal's words, or one line when nothing moved. */
@Composable
fun LearningCard(learning: ForgeLearningUi, modifier: Modifier = Modifier) {
    val card = RoundedCornerShape(9.dp)
    if (learning.changes.isEmpty()) {
        learning.note?.let { Secondary(it, modifier.testTag("reveal_learning_note")) }
        return
    }
    Column(
        modifier.fillMaxWidth().clip(card).background(BuffGreen.copy(alpha = 0.10f)).border(1.dp, BuffGreen.copy(alpha = 0.45f), card).padding(horizontal = 12.dp, vertical = Space.sm).testTag("reveal_learning"),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        learning.changes.forEach { c ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(c.title, style = MaterialTheme.typography.titleSmall, color = BuffGreen)
                Text(c.line, style = MaterialTheme.typography.bodySmall, color = Cream)
            }
        }
    }
}
