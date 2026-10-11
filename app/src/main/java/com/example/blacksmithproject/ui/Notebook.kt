package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.BronzeContainer
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.BuffGreen
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.ForgePanel
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.GoldBright
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.model.KnowledgeState

/** The kinds of thing the notebook keeps apart. [FIGHTS] is a page only where fights read a weapon's rules (`fightNotes`). */
enum class BookTab(val label: String, val empty: String) {
    METAL("Metal pairings", "Your first forge will add a metal pairing here."),
    WEAPON("Weapon pairings", "Your first forge will add a weapon pairing here."),
    CLUES("Recipe clues", "No recipe clues yet. Experiments and visitors can reveal them."),
    FIGHTS("Fight notes", "No fight notes yet. Forge a weapon to learn the rules it follows in a fight."),
}

private val Tab = RoundedCornerShape(7.dp)

/** The colour of a stage: gold while untried, cream once observed, green once understood. Always beside the stage's word. */
fun KnowledgeState.color() = when (this) { KnowledgeState.UNKNOWN -> Gold; KnowledgeState.OBSERVED -> Cream; else -> BuffGreen }

/** The colour of a kind of fight note: cream for a rule forged, gold for a path only suggested, green for one a fight has shown. Always beside the kind's word and sign. */
fun FightNoteKind.color() = when (this) { FightNoteKind.LEARNED -> Cream; FightNoteKind.SUGGESTED -> Gold; FightNoteKind.WITNESSED -> BuffGreen }

/**
 * The research notebook as rows of the Records list: what has been tried, by kind, each pairing with its ingredients'
 * art, its stage and what the journal may say of it, and "Use this pairing" to put it on the workbench (nothing is
 * forged). The pairing on the workbench now is marked. Recipe clues show only the rungs earned.
 */
fun LazyListScope.notebookItems(s: UiState.Playing, vm: GameViewModel, chosen: BookTab, onTab: (BookTab) -> Unit) {
    val engine = vm.engine
    val journal = s.state.legacy.journal
    val book = engine.notebook(journal)
    // Fight notes are a page of a guild run, and of a journal that already holds some. A tab remembered from there falls back to the first.
    val fights = engine.fightNotes(s.state)
    val tabs = BookTab.entries.filter { it != BookTab.FIGHTS || fights != null }
    val tab = chosen.takeIf { it in tabs } ?: BookTab.METAL
    val bench = with(s.draft) {
        setOfNotNull(
            if (coreId != null && augmentId != null) com.tinyblacksmith.core.model.Journal.coreAugmentKey(coreId, augmentId) else null,
            if (augmentId != null && familyId != null) com.tinyblacksmith.core.model.Journal.augmentFamilyKey(augmentId, familyId) else null,
        )
    }
    item(key = "book_head") {
        Column {
            // The page's head on a plate, as the Legacy page has its own.
            FramedPanel(modifier = Modifier.fillMaxWidth().padding(top = Space.sm)) {
                Text("Notebook", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { heading() })
                Secondary("${book.counts}. Your notes carry over to every era.", Modifier.testTag("book_counts"))
            }
            // Tabs of one width across the page; a label may take two lines, so nothing is cut at a larger text size.
            // Four of them stand two to a line once the text is enlarged: a quarter of a phone cannot hold "pairings" then.
            val perRow = if (tabs.size > 3 && LocalDensity.current.fontScale > 1f) 2 else tabs.size
            tabs.chunked(perRow).forEachIndexed { i, row ->
                Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 12.dp else Space.sm).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(if (row.size > 3) Space.xs else Space.sm)) {
                    row.forEach { t ->
                        val on = t == tab
                        Box(
                            Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).clip(Tab).background(if (on) BronzeContainer else ForgePanel).border(1.dp, if (on) Gold else BronzeDeep, Tab).clickable(role = Role.Tab) { onTab(t) }
                                .padding(horizontal = if (row.size > 3) Space.xs else Space.sm, vertical = 6.dp).testTag("book_tab_${t.name.lowercase()}").semantics { selected = on },
                            contentAlignment = Alignment.Center,
                        ) { Text(t.label, style = MaterialTheme.typography.labelMedium, color = if (on) GoldBright else CreamMuted, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
    }
    if (tab == BookTab.FIGHTS && fights != null) {
        fightNoteItems(fights)
        return
    }
    val pairings = when (tab) { BookTab.METAL -> book.metal; BookTab.WEAPON -> book.weapon; BookTab.CLUES, BookTab.FIGHTS -> emptyList() }
    if ((tab == BookTab.CLUES && book.clues.isEmpty()) || (tab != BookTab.CLUES && pairings.isEmpty())) {
        // An empty kind is a blank page of the same book, not a stray line.
        item(key = "book_empty") {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp).forgeRow().padding(horizontal = Space.md, vertical = Space.lg), contentAlignment = Alignment.Center) {
                Text(tab.empty, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, textAlign = TextAlign.Center, modifier = Modifier.testTag("book_empty"))
            }
        }
    }
    items(pairings, key = { "journal_${it.key}" }) { p -> PairingRow(p, onBench = p.key in bench, art = pairingArt(p, vm)) { vm.usePairing(p.key) } }
    if (tab == BookTab.CLUES) items(book.clues, key = { "journal_$it" }) { key ->
        Column(Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().padding(horizontal = Space.sm)) {
            AffinityHint(journal, engine.content, key)
            signatureUi(journal, key, engine.content, engine.config)?.let { SignatureLadder(it, onUse = vm::useRecipe) }
        }
    }
    item(key = "book_try") {
        SecondaryActionButton("Try a new pairing", vm::tryUntried, Modifier.fillMaxWidth().padding(top = Space.md).testTag("book_try"), detail = "Selects ingredients without spending anything.")
    }
}

/**
 * The fight notes page, in three kinds that are never mixed: the rules the smith has forged, then each path those rules
 * open, marked as only suggested by the rules or as seen in a real fight, then how many paths are still closed. Each
 * kind has its own word, sign and colour. The bench writes nothing here.
 */
private fun LazyListScope.fightNoteItems(notes: FightNotesUi) {
    if (notes.learned.isEmpty() && notes.paths.isEmpty()) item(key = "book_empty") {
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).forgeRow().padding(horizontal = Space.md, vertical = Space.lg), contentAlignment = Alignment.Center) {
            Text(BookTab.FIGHTS.empty, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, textAlign = TextAlign.Center, modifier = Modifier.testTag("book_empty"))
        }
    }
    if (notes.learned.isNotEmpty()) item(key = "book_learned") {
        Column(Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().padding(horizontal = 12.dp, vertical = Space.sm).testTag("book_learned")) {
            Text("Rules you have forged", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.semantics { heading() })
            notes.learned.forEach { r ->
                Column(Modifier.padding(top = Space.sm).semantics(mergeDescendants = true) {}) {
                    Text(r.name, style = MaterialTheme.typography.titleSmall, color = Cream)
                    FightNoteKindLine(FightNoteKind.LEARNED, r.description)
                }
            }
        }
    }
    items(notes.paths, key = { "interaction_${it.id}" }) { p ->
        Column(Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().padding(horizontal = 12.dp, vertical = Space.sm).testTag("book_interaction_${p.id}").semantics(mergeDescendants = true) {}) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, color = Cream)
            FightNoteKindLine(p.kind, p.description)
            Text("Trade-off: ${p.tradeoff}", style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(top = 2.dp))
            if (p.known.isNotEmpty()) Text("You have forged: ${p.known.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(top = 2.dp))
        }
    }
    notes.hiddenLine?.let { line -> item(key = "book_hidden") { Secondary(line, Modifier.padding(top = Space.md).testTag("book_hidden")) } }
}

/** The kind of a note in its own colour and weight with its sign, then the catalog's words in the quiet one. */
@Composable
private fun FightNoteKindLine(kind: FightNoteKind, text: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = kind.color(), fontWeight = FontWeight.SemiBold)) { append("${kind.sign} ${kind.word}") }
            append(" · $text")
        },
        style = MaterialTheme.typography.bodySmall, color = CreamMuted,
    )
}

/** The two things a pairing joins, as sprites: metal and augment, or augment and the weapon type in plain metal. */
private fun pairingArt(p: PairingUi, vm: GameViewModel): List<Int> = listOfNotNull(
    p.metalId?.let(Sprites::material), p.augmentId?.let(Sprites::material),
    p.familyId?.let { Sprites.weapon(it, vm.engine.content.materials(MaterialCategory.CORE).first().id, null) },
)

@Composable
private fun PairingRow(p: PairingUi, onBench: Boolean, art: List<Int>, onUse: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().then(if (onBench) Modifier.border(1.dp, Gold, RoundedCornerShape(4.dp)) else Modifier)
            .padding(start = Space.sm).testTag("book_row_${p.key}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Both ingredients in one dark slot, as the workbench shows them.
        Row(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep).padding(2.dp)) { art.forEach { PixelImage(it, 32.dp, description = null) } }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = Space.xs).padding(vertical = Space.sm).semantics(mergeDescendants = true) { contentDescription = "${p.subject}, ${p.stage}: ${p.hint}${if (onBench) ". On the workbench now" else ""}" }) {
            Text(p.subject, style = MaterialTheme.typography.titleSmall, color = Cream)
            // The stage in its own colour and weight, then the journal's words in the quiet one.
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = p.state.color(), fontWeight = FontWeight.SemiBold)) { append(p.stage) }
                    append(" · ${p.hint}")
                },
                style = MaterialTheme.typography.bodySmall, color = CreamMuted,
            )
            if (onBench) Text("◆ On the workbench", style = MaterialTheme.typography.labelSmall, color = Gold)
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
