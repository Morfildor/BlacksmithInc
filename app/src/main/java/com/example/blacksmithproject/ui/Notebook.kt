package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import com.tinyblacksmith.core.config.BalanceConfig
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
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
    METAL("Metal combinations", "Your first forge will add a metal combination here."),
    WEAPON("Weapon combinations", "Your first forge will add a weapon combination here."),
    CLUES("Recipe clues", "No recipe clues yet. Experiments and visitors can reveal them."),
    FIGHTS("Fight notes", "No fight notes yet. Forge a weapon to discover its effects in a fight."),
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
            // The page's head, as the Legacy page has its own: a title and the smith's own count, no plate.
            Column(Modifier.fillMaxWidth().padding(top = Space.md)) {
                Text("Notebook", style = MaterialTheme.typography.titleLarge, color = Cream, modifier = Modifier.semantics { heading() })
                Secondary("${book.counts}. Your notes carry over to every era.", Modifier.testTag("book_counts"))
            }
            // Tabs of one width across the page; a label may take two lines between its words, so nothing is cut at a larger
            // text size. As many stand on a line as leave each tab room for the longest word of any label ("combinations"),
            // measured on this page's own width: a word is never broken inside.
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val style = MaterialTheme.typography.labelMedium
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val widest = tabs.flatMap { it.label.split(' ') }.maxOf { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width }.toFloat()
                // Each tab's gap and inner padding (and its 1 dp border), as the rows below draw them for that many to a line.
                fun fit(space: Dp) = with(density) { tabsPerRow(constraints.maxWidth.toFloat(), widest, tabs.size, space.toPx(), (space + 1.dp).toPx()) }
                val perRow = fit(Space.xs).takeIf { it > 3 } ?: fit(Space.sm)
                Column {
                    tabs.chunked(perRow).forEachIndexed { i, row ->
                        Row(Modifier.fillMaxWidth().padding(top = if (i == 0) 12.dp else Space.sm).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(if (perRow > 3) Space.xs else Space.sm)) {
                            row.forEach { t ->
                                val on = t == tab
                                Box(
                                    Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).clip(Tab).background(if (on) BronzeContainer else ForgePanel).border(1.dp, if (on) Gold else BronzeDeep, Tab).clickable(role = Role.Tab) { onTab(t) }
                                        .padding(horizontal = if (perRow > 3) Space.xs else Space.sm, vertical = 6.dp).testTag("book_tab_${t.name.lowercase()}").semantics { selected = on },
                                    contentAlignment = Alignment.Center,
                                ) { Text(t.label, style = style, color = if (on) GoldBright else CreamMuted, textAlign = TextAlign.Center) }
                            }
                            // An odd last row keeps the tabs' width: an empty share, not one tab across the page.
                            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
    if (tab == BookTab.FIGHTS && fights != null) {
        fightNoteItems(fights, engine.config)
        return
    }
    val pairings = when (tab) { BookTab.METAL -> book.metal; BookTab.WEAPON -> book.weapon; BookTab.CLUES, BookTab.FIGHTS -> emptyList() }
    if ((tab == BookTab.CLUES && book.clues.isEmpty()) || (tab != BookTab.CLUES && pairings.isEmpty())) {
        // An empty kind is a blank page of the same book, not a stray line.
        item(key = "book_empty") {
            Box(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.lg), contentAlignment = Alignment.Center) {
                Text(tab.empty, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, textAlign = TextAlign.Center, modifier = Modifier.testTag("book_empty"))
            }
        }
    }
    // V3: rows with hairlines between them, not a stack of plates.
    itemsIndexed(pairings, key = { _, p -> "journal_${p.key}" }) { i, p ->
        if (i > 0) HorizontalDivider(color = BronzeDeep)
        PairingRow(p, onBench = p.key in bench, art = pairingArt(p, vm)) { vm.usePairing(p.key) }
    }
    if (tab == BookTab.CLUES) itemsIndexed(book.clues, key = { _, k -> "journal_$k" }) { i, key ->
        if (i > 0) HorizontalDivider(color = BronzeDeep)
        Column(Modifier.fillMaxWidth().padding(top = Space.xs)) {
            AffinityHint(journal, engine.content, key)
            signatureUi(journal, key, engine.content, engine.config)?.let { SignatureLadder(it, onUse = vm::useRecipe) }
        }
    }
    item(key = "book_try") {
        SecondaryActionButton("Try a new combination", vm::tryUntried, Modifier.fillMaxWidth().padding(top = Space.md).testTag("book_try"), detail = "Puts a metal and an infusion from your stock that you have never forged together on the workbench. Nothing is spent.")
    }
}

/**
 * The fight notes page, in three kinds that are never mixed: the rules the smith has forged, then each path those rules
 * open, marked as only suggested by the rules or as seen in a real fight, then how many paths are still closed. Each
 * kind has its own word, sign and colour. The bench writes nothing here.
 */
private fun LazyListScope.fightNoteItems(notes: FightNotesUi, config: BalanceConfig) {
    if (notes.learned.isEmpty() && notes.paths.isEmpty()) item(key = "book_empty") {
        Box(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.lg), contentAlignment = Alignment.Center) {
            Text(BookTab.FIGHTS.empty, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, textAlign = TextAlign.Center, modifier = Modifier.testTag("book_empty"))
        }
    }
    // The effects forged so far as ability rows (when, what, how often), with the statuses they name explained once.
    if (notes.learned.isNotEmpty()) item(key = "book_learned") {
        Column(Modifier.fillMaxWidth().padding(top = Space.sm).testTag("book_learned"), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            TipSectionBlock(TipSection("Discovered weapon effects", notes.learned.map { abilityLine(it.name, it.description, source = "${FightNoteKind.LEARNED.sign} ${FightNoteKind.LEARNED.word}") }))
            TipKeywords(keywords(notes.learned.flatMap { it.stats }, config))
        }
    }
    if (notes.paths.isNotEmpty()) item(key = "book_paths_head") { SectionHeader("How effects work together") }
    itemsIndexed(notes.paths, key = { _, p -> "interaction_${p.id}" }) { i, p ->
        if (i > 0) HorizontalDivider(color = BronzeDeep)
        Column(Modifier.fillMaxWidth().padding(vertical = Space.sm).testTag("book_interaction_${p.id}").semantics(mergeDescendants = true) {}) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, color = Cream)
            FightNoteKindLine(p.kind, p.description)
            Text("${EffectKind.FLAW.sign} Drawback: ${p.tradeoff}", style = MaterialTheme.typography.bodySmall, color = EffectKind.FLAW.color, modifier = Modifier.padding(top = 2.dp))
            if (p.known.isNotEmpty()) Text("Effects you have discovered: ${p.known.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(top = 2.dp))
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
            append(": $text")
        },
        style = MaterialTheme.typography.bodySmall, color = CreamMuted,
    )
}

/** The two things a pairing joins, as sprites: metal and augment, or augment and the weapon type in plain metal. */
private fun pairingArt(p: PairingUi, vm: GameViewModel): List<Int> = listOfNotNull(
    p.metalId?.let(Sprites::material), p.augmentId?.let(Sprites::material),
    p.familyId?.let { Sprites.weapon(it, vm.engine.content.materials(MaterialCategory.CORE).first().id, null).base },
)

/**
 * A pairing: the art, its words, and "Use ›". When the words would keep less than their longest word (or 120 dp at this
 * text size; a 360 dp phone keeps about 142 dp at 1.0, so the row stays as it was there) beside the art and the action,
 * the action goes under the words; the art goes above them only when their longest word would not fit beside it.
 */
@Composable
private fun PairingRow(p: PairingUi, onBench: Boolean, art: List<Int>, onUse: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val subjectStyle = MaterialTheme.typography.titleSmall
    val hintStyle = MaterialTheme.typography.bodySmall
    val useStyle = MaterialTheme.typography.labelLarge
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = Space.xs).testTag("book_row_${p.key}")) {
        val (actionBeside, artBeside) = with(density) {
            fun widest(text: String, style: androidx.compose.ui.text.TextStyle) =
                text.split(' ').filter { it.isNotEmpty() }.maxOfOrNull { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } ?: 0
            val word = maxOf(widest(p.subject, subjectStyle), widest("${p.stage}: ${p.hint}", hintStyle.copy(fontWeight = FontWeight.SemiBold)))
            val minText = maxOf(word.toFloat(), 120.dp.toPx() * fontScale)
            // Fixed: the slot (32 dp a picture and its 2 dp padding) and the words' own start and end padding.
            val fixed = (art.size * 32 + 4).dp.toPx() + (12.dp + Space.xs).toPx()
            val use = measurer.measure("Use", useStyle, softWrap = false, maxLines = 1).size.width + (18.dp + Space.sm * 2).toPx()
            val w = constraints.maxWidth.toFloat()
            // The floor decides the action; the art moves above only when the longest word itself would not fit beside it.
            roomBeside(w, fixed, use, minText) to roomBeside(w, fixed, 0f, word.toFloat())
        }
        // Both ingredients in one dark slot, as the workbench shows them.
        val slot = @Composable { Row(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep).padding(2.dp)) { art.forEach { PixelImage(it, 32.dp, description = null) } } }
        val words = @Composable { m: Modifier ->
            Column(m.padding(vertical = Space.sm).semantics(mergeDescendants = true) { contentDescription = "${p.subject}, ${p.stage}: ${p.hint}${if (onBench) ". On the workbench now" else ""}" }) {
                Text(p.subject, style = subjectStyle, color = Cream)
                // The stage in its own colour and weight, then the journal's words in the quiet one.
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = p.state.color(), fontWeight = FontWeight.SemiBold)) { append(p.stage) }
                        append(": ${p.hint}")
                    },
                    style = hintStyle, color = CreamMuted,
                )
                if (onBench) Text("◆ On the workbench", style = MaterialTheme.typography.labelSmall, color = Gold)
            }
        }
        // A way to the workbench, said in cream with the one chevron, as every door is.
        val action = @Composable {
            Row(
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "Put on the workbench", onClick = onUse).padding(horizontal = Space.sm).testTag("book_use_${p.key}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Use", style = useStyle, color = Cream)
                Chevron(ChevronWay.RIGHT, tint = CreamMuted, size = 18.dp)
            }
        }
        when {
            actionBeside -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                slot()
                words(Modifier.weight(1f).padding(start = 12.dp, end = Space.xs))
                action()
            }
            artBeside -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                slot()
                Column(Modifier.weight(1f).padding(start = 12.dp, end = Space.xs)) {
                    words(Modifier.fillMaxWidth())
                    action()
                }
            }
            else -> Column(Modifier.fillMaxWidth()) {
                slot()
                words(Modifier.fillMaxWidth())
                action()
            }
        }
    }
}

/**
 * What the forge just done taught, on its result, in one line ([learningLine]): the kind of each gain with a green "+",
 * or the note that nothing moved. Each gain in the journal's words, and the attempt at a signature the clues know of,
 * are on its card behind the ringed "i" ([learningTip]).
 */
@Composable
fun LearningCard(learning: ForgeLearningUi, modifier: Modifier = Modifier) {
    val line = learningLine(learning) ?: return
    val gained = learning.changes.isNotEmpty()
    val tag = when { gained -> "reveal_learning"; learning.note != null -> "reveal_learning_note"; else -> "reveal_attempt" }
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}.testTag(tag)) {
            Text("Notebook", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
            Text(
                if (gained) "${EffectKind.BUFF.sign} $line" else line, style = MaterialTheme.typography.bodyMedium,
                color = if (gained) BuffGreen else Cream,
            )
        }
        InfoButton("what the notebook learned", { learningTip(learning) }, tag = "reveal_learning_info")
    }
}
