package com.example.blacksmithproject.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.example.blacksmithproject.ui.theme.NUMBERS
import com.example.blacksmithproject.R
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp

/*
 * V2 building blocks (docs/UI_V2_LEDGER.md §2). The rule every surface follows: situation in one line, what matters now,
 * one primary action, details behind a disclosure. These keep the details one tap away without moving the decision facts.
 */

/** How a fact reads: plain, a gain ("+", green) or a warning ("−", red). Never colour alone: each has its sign. */
enum class FactTone { PLAIN, GOOD, WARN }

/** One short fact of a [FactLine]: "Fee 20 gold", "Back day 7", "Lethal". [label] is muted, [value] carries the tone. */
@Immutable
data class Fact(val value: String, val label: String? = null, val tone: FactTone = FactTone.PLAIN)

/**
 * Short facts as one line that wraps as words do: "Fee 20 gold  ·  Back day 7  ·  − Lethal". This replaces a paragraph
 * of terms and a wall of chips; a fact that changes the decision gets [FactTone.WARN] (its sign and colour).
 */
@Composable
fun FactLine(facts: List<Fact>, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    if (facts.isEmpty()) return
    val text = factText(facts)
    Text(
        text, style = if (emphasis) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall, color = Cream,
        modifier = modifier.semantics { contentDescription = facts.joinToString(". ") { f -> listOfNotNull(f.label, f.value).joinToString(" ") } },
    )
}

/**
 * The facts of [FactLine] as pieces that flow onto as many lines as they need. A separator is drawn only between two
 * facts on the same line, so a wrapped line never ends or starts with a stray " · ". A fact wider than the line wraps
 * inside itself. One merged description for TalkBack, as [FactLine] gives.
 */
@Composable
fun FactFlow(facts: List<Fact>, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    if (facts.isEmpty()) return
    val style = if (emphasis) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
    androidx.compose.ui.layout.Layout(
        content = {
            // A fact without a label is a plain description (people, class and level): quiet, so it never competes with a name.
            facts.forEach { f -> if (f.label == null && f.tone == FactTone.PLAIN) Text(f.value, style = style, color = CreamMuted) else Text(factText(listOf(f)), style = style, color = Cream) }
            repeat(facts.size - 1) { Text(" · ", style = style, color = CreamMuted) }
        },
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = facts.joinToString(". ") { f -> listOfNotNull(f.label, f.value).joinToString(" ") } },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val items = measurables.take(facts.size).map { it.measure(loose) }
        val seps = measurables.drop(facts.size).map { it.measure(loose) }
        val max = constraints.maxWidth
        // Lines of item indices, greedily: a fact joins the line when it fits after a separator.
        val lines = mutableListOf(mutableListOf(0))
        var x = items[0].width
        for (i in 1 until items.size) {
            val needed = seps[i - 1].width + items[i].width
            if (x + needed <= max) { lines.last() += i; x += needed } else { lines += mutableListOf(i); x = items[i].width }
        }
        val heights = lines.map { line -> line.maxOf { items[it].height } }
        val width = if (constraints.hasBoundedWidth) max else lines.maxOf { line -> line.sumOf { items[it].width } + (line.size - 1) * (seps.firstOrNull()?.width ?: 0) }
        layout(width.coerceAtLeast(constraints.minWidth), heights.sum().coerceAtLeast(constraints.minHeight)) {
            var y = 0
            lines.forEachIndexed { n, line ->
                var px = 0
                line.forEachIndexed { k, i ->
                    if (k > 0) { seps[i - 1].placeRelative(px, y); px += seps[i - 1].width }
                    items[i].placeRelative(px, y); px += items[i].width
                }
                y += heights[n]
            }
        }
    }
}

internal fun factText(facts: List<Fact>): AnnotatedString = buildAnnotatedString {
    facts.forEachIndexed { i, f ->
        // The separator is bound to the fact before it (no-break space), so a wrapped line never starts with it.
        if (i > 0) withStyle(SpanStyle(color = CreamMuted)) { append(" · ") }
        f.label?.let { withStyle(SpanStyle(color = CreamMuted)) { append("$it ") } }
        // The value is what the eye looks for: a step heavier than its label, with figures that line up.
        val value = SpanStyle(fontWeight = FontWeight.Medium, fontFeatureSettings = NUMBERS)
        when (f.tone) {
            FactTone.PLAIN -> withStyle(value) { append(f.value) }
            FactTone.GOOD -> withStyle(value.copy(color = EffectKind.BUFF.color)) { append("${EffectKind.BUFF.sign} ${f.value}") }
            FactTone.WARN -> withStyle(value.copy(color = EffectKind.FLAW.color)) { append("${EffectKind.FLAW.sign} ${f.value}") }
        }
    }
}

/**
 * Details on demand: a 48 dp row in gold ("How Heartfire works", "Details") with a chevron, and its content under it once
 * opened. [summary] says what is inside while it is closed. The open state survives navigation and process death under
 * [key]. TalkBack hears a button with "Expanded"/"Collapsed".
 */
@Composable
fun Disclosure(
    title: String, key: String, modifier: Modifier = Modifier, summary: String? = null, tag: String? = null,
    initiallyOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(key) { mutableStateOf(initiallyOpen) }
    Column(modifier.fillMaxWidth()) {
        DisclosureHead(title, open, { open = !open }, summary, tag)
        if (open) Column(Modifier.fillMaxWidth().padding(bottom = Space.sm).let { if (tag != null) it.testTag("${tag}_body") else it }, verticalArrangement = Arrangement.spacedBy(Space.xs), content = content)
    }
}

/** The head row of a [Disclosure], for a caller that keeps the open state itself. */
@Composable
fun DisclosureHead(title: String, open: Boolean, onToggle: () -> Unit, summary: String? = null, tag: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(onClickLabel = if (open) "Hide" else "Show", role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
            .let { if (tag != null) it.testTag(tag) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = Space.xs)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Gold)
            if (!open) summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CreamMuted) }
        }
        Chevron(if (open) ChevronWay.UP else ChevronWay.DOWN, Modifier.padding(start = Space.sm))
    }
}

/** Which way a [Chevron] points: right for a door to another place, down for a closed disclosure, up for an open one. */
enum class ChevronWay(val degrees: Float) { RIGHT(0f), DOWN(90f), UP(-90f), LEFT(180f) }

/** The one chevron of the game (V2): a drawn stroke, gold by default, decorative (the row says what it does). */
@Composable
fun Chevron(way: ChevronWay, modifier: Modifier = Modifier, tint: Color = Gold, size: Dp = 20.dp) {
    Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = tint, modifier = modifier.size(size).rotate(way.degrees))
}

/** A named rule: its name always, its text once the name is tapped. */
@Immutable
data class NamedRule(val name: String, val text: String, val kind: EffectKind = EffectKind.NEUTRAL)

/**
 * Rules as names first (a weapon's, a hero's kit): each name is a row; tapping it opens the rule's full sentence under it.
 * The player sees what a thing can do at a glance and reads how only for the rule they care about. [key] keeps which
 * rules are open.
 */
@Composable
fun RuleList(rules: List<NamedRule>, key: String, modifier: Modifier = Modifier, tag: String? = null) {
    if (rules.isEmpty()) return
    var open by rememberSaveable(key) { mutableStateOf(emptyList<Int>()) }
    Column(modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it }) {
        rules.forEachIndexed { i, r ->
            val on = i in open
            Column(
                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .clickable(onClickLabel = if (on) "Hide effect" else "Read effect", role = Role.Button) { open = if (on) open - i else open + i }
                    .semantics(mergeDescendants = true) { stateDescription = if (on) "Expanded" else "Collapsed" }
                    .padding(vertical = Space.xs),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.kind.sign, style = MaterialTheme.typography.labelMedium, color = r.kind.color, modifier = Modifier.padding(end = Space.sm))
                    Text(r.name, style = MaterialTheme.typography.titleSmall, color = if (r.kind == EffectKind.NEUTRAL) Cream else r.kind.color, modifier = Modifier.weight(1f))
                    Chevron(if (on) ChevronWay.UP else ChevronWay.DOWN, tint = CreamMuted, size = 18.dp)
                }
                if (on && r.text.isNotBlank()) Text(r.text, style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(start = Space.md + Space.xs, top = 2.dp))
            }
        }
    }
}
