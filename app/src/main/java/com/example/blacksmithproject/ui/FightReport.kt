package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.combat.FightOutcome
import com.tinyblacksmith.core.combat.FightResult
import com.tinyblacksmith.core.combat.Side

/** One line of a fight's timeline: a round header, or an event indented under the event that caused it. */
@Immutable data class FightLine(val text: String, val depth: Int, val header: Boolean = false)

/** How the fight ended, as its own record says it and no more. */
fun fightOutcomeLine(result: FightResult): String {
    val rounds = "${result.rounds} ${if (result.rounds == 1) "round" else "rounds"}"
    return when (result.outcome) {
        FightOutcome.WON -> "Won in $rounds"
        FightOutcome.RETREATED -> "Pulled back after $rounds"
        FightOutcome.LOST -> "Lost after $rounds"
        FightOutcome.TIMED_OUT -> "Undecided after $rounds"
    }
}

/** Everybody of [side] as the fight left them: "Brann 21/40", "Mira down", with "blade cracked" where one did. */
fun fightSide(result: FightResult, side: Side): List<String> = result.actors.filter { it.side == side }.map { a ->
    listOfNotNull("${a.name} ${if (a.downed) "down" else "${a.health}/${a.maxHealth}"}", "weapon cracked".takeIf { a.fractured }, "joined during the fight".takeIf { a.summoned }).joinToString(", ")
}

/**
 * The exact timeline: every stored event in order, a header at each round's start, and each other line indented by
 * the length of the chain that led to it ([com.tinyblacksmith.core.combat.FightEvent.parentId]).
 */
fun fightTimeline(result: FightResult): List<FightLine> {
    val byId = result.events.associateBy { it.id }
    return result.events.filter { it.text.isNotEmpty() }.map { e ->
        if (e.kind == EventKind.ROUND_START) FightLine(e.text, 0, header = true)
        // A chain is finite by the fight's own rules; the bound is for a record that is not.
        else FightLine(e.text, generateSequence(e.parentId?.let(byId::get)) { it.parentId?.let(byId::get) }.take(MAX_DEPTH).count { it.kind != EventKind.ROUND_START })
    }
}

private const val MAX_DEPTH = 8

/**
 * A resolved fight as text: how it ended, both sides with the health they ended on, the highlights, and the exact
 * timeline. It renders the record it is given and resolves nothing.
 *
 * The timeline is shown while [expanded]. With [onToggle] the report has its own "The whole fight" button and the
 * caller keeps the state; without it there is no button and the caller decides by [expanded] alone. [highlights] caps
 * how many are listed (the evening cards say them above the report and pass 0). Test tags: `fight_outcome`,
 * `fight_party`, `fight_enemies`, `fight_highlights`, `fight_toggle`, `fight_timeline`.
 */
@Composable
fun FightReport(result: FightResult, modifier: Modifier = Modifier, expanded: Boolean = false, onToggle: (() -> Unit)? = null, highlights: Int = 3) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(fightOutcomeLine(result), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("fight_outcome"))
        FightSide("The party", fightSide(result, Side.PARTY), "fight_party")
        FightSide("Against them", fightSide(result, Side.ENEMY), "fight_enemies")
        val told = result.highlights.take(highlights)
        if (told.isNotEmpty()) {
            Column(Modifier.testTag("fight_highlights"), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                FightLabel("What decided it")
                told.forEach { Text(it.text, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        onToggle?.let { SecondaryActionButton(if (expanded) "Hide the whole fight" else "The whole fight", it, Modifier.heightIn(min = 48.dp).testTag("fight_toggle")) }
        if (expanded) {
            Column(Modifier.testTag("fight_timeline")) {
                fightTimeline(result).forEach { line ->
                    if (line.header) Text(line.text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = Space.sm).semantics { heading() })
                    else Text(line.text, style = MaterialTheme.typography.bodySmall, color = if (line.depth == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = INDENT * line.depth, top = 2.dp))
                }
            }
        }
    }
}

private val INDENT = 12.dp

@Composable
private fun FightLabel(text: String) = Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun FightSide(label: String, actors: List<String>, tag: String) {
    if (actors.isEmpty()) return
    Column(Modifier.testTag(tag)) {
        FightLabel(label)
        // One per line: names and numbers wrap at any font scale and are never cut by a neighbour.
        actors.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
