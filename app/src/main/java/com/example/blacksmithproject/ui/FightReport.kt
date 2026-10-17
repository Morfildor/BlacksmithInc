package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.combat.FightOutcome
import com.tinyblacksmith.core.combat.FightResult
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.text.LegacyProse

/** One line of a fight's timeline: a round header, or an event indented under the event that caused it. */
@Immutable data class FightLine(val text: String, val depth: Int, val header: Boolean = false)

/** How the fight ended, as its own record says it and no more. */
fun fightOutcomeLine(result: FightResult): String {
    val rounds = "${result.rounds} ${if (result.rounds == 1) "round" else "rounds"}"
    return when (result.outcome) {
        FightOutcome.WON -> "Won in $rounds"
        FightOutcome.RETREATED -> "Retreated after $rounds"
        FightOutcome.LOST -> "Lost after $rounds"
        FightOutcome.TIMED_OUT -> "No winner after $rounds"
    }
}

/** Everybody of [side] as the fight left them: "Brann 21/40 health", "Mira down", with "weapon cracked" where one did. */
fun fightSide(result: FightResult, side: Side): List<String> = result.actors.filter { it.side == side }.map { a ->
    listOfNotNull("${a.name} ${if (a.downed) "down" else "${a.health}/${a.maxHealth} health"}", "weapon cracked".takeIf { a.fractured }, "joined during the fight".takeIf { a.summoned }).joinToString(", ")
}

/**
 * The fight's key numbers, said under its outcome: how many of each side are still standing at the end, and, when it
 * happened, how many of the party's weapons cracked. Read from the record only.
 */
fun fightFacts(result: FightResult): List<Fact> {
    fun standing(side: Side) = result.actors.filter { it.side == side }.let { all -> "${all.count { !it.downed }} of ${all.size} standing".takeIf { all.isNotEmpty() } }
    val cracked = result.actors.count { it.side == Side.PARTY && it.fractured }
    return listOfNotNull(
        standing(Side.PARTY)?.let { Fact(it, "Party") },
        standing(Side.ENEMY)?.let { Fact(it, "Enemies") },
        cracked.takeIf { it > 0 }?.let { Fact(if (it == 1) "1 weapon cracked" else "$it weapons cracked", tone = FactTone.WARN) },
    )
}

/**
 * The exact timeline: every stored event in order, a header at each round's start, and each other line indented by
 * the length of the chain that led to it ([com.tinyblacksmith.core.combat.FightEvent.parentId]). A line a save stored in
 * older words is shown in today's (`LegacyProse.fightLine`), never changed in the record.
 */
fun fightTimeline(result: FightResult): List<FightLine> {
    val byId = result.events.associateBy { it.id }
    return result.events.filter { it.text.isNotEmpty() }.map { e ->
        if (e.kind == EventKind.ROUND_START) FightLine(LegacyProse.fightLine(e.text), 0, header = true)
        // A chain is finite by the fight's own rules; the bound is for a record that is not.
        // Silent links a save keeps for the replay (a blow Guard took in part, a tick's start) are not lines, so they add no indent.
        else FightLine(LegacyProse.fightLine(e.text), generateSequence(e.parentId?.let(byId::get)) { it.parentId?.let(byId::get) }.filter { it.kind != EventKind.ROUND_START && it.text.isNotEmpty() }.take(MAX_DEPTH).count())
    }
}

private const val MAX_DEPTH = 8

/**
 * A resolved fight as text, in two levels. The lead ([FightLead]): how it ended, its key numbers ([fightFacts]) and the
 * highlights. Then one "Full report" row (`fight_toggle`) that opens the technical part in place ([FightFullBody]):
 * both sides with the health they ended on, then the exact timeline. It renders the record it is given and resolves nothing.
 *
 * The full part is shown while [expanded]. With [onToggle] the report has its own "Full report" row and the caller keeps
 * the state; without it there is no row and the caller decides by [expanded] alone. [highlights] caps how many are
 * listed under "Key moments" (the evening cards say them above the report and pass 0). Test tags: `fight_outcome`,
 * `fight_facts`, `fight_sides`, `fight_party`, `fight_enemies`, `fight_highlights`, `fight_toggle`, `fight_timeline`.
 */
@Composable
fun FightReport(result: FightResult, modifier: Modifier = Modifier, expanded: Boolean = false, onToggle: (() -> Unit)? = null, highlights: Int = 3) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        FightLead(result, highlights = highlights)
        onToggle?.let { FullReportHead(expanded, it) }
        if (expanded) FightFullBody(result)
    }
}

/** The one row that opens a fight's technical part: both sides and every event. Gold, as every way to more is. */
@Composable
fun FullReportHead(expanded: Boolean, onToggle: () -> Unit, summary: String = "Both sides and every event, round by round", tag: String = "fight_toggle") =
    DisclosureHead("Full report", expanded, onToggle, summary = summary, tag = tag)

/** How the fight ended, its key numbers, and up to [highlights] of its key moments: the part of a report read first. */
@Composable
fun FightLead(result: FightResult, modifier: Modifier = Modifier, highlights: Int = 3) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(fightOutcomeLine(result), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("fight_outcome"))
        FactLine(fightFacts(result), Modifier.testTag("fight_facts"))
        val told = result.highlights.take(highlights)
        if (told.isNotEmpty()) {
            Column(Modifier.testTag("fight_highlights"), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                FightLabel("Key moments")
                told.forEach { Text(LegacyProse.fightHighlight(it.text), style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/** The technical part of a fight: both sides as the fight left them, then the exact timeline, round by round. */
@Composable
fun FightFullBody(result: FightResult, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Column(Modifier.testTag("fight_sides"), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            FightSide("The party", fightSide(result, Side.PARTY), "fight_party")
            FightSide("The enemies", fightSide(result, Side.ENEMY), "fight_enemies")
        }
        Column(Modifier.testTag("fight_timeline")) {
            fightTimeline(result).forEach { line ->
                if (line.header) Text(line.text, style = MaterialTheme.typography.labelLarge, color = CreamMuted, modifier = Modifier.padding(top = Space.sm).semantics { heading() })
                else Text(line.text, style = MaterialTheme.typography.bodySmall, color = if (line.depth == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = INDENT * line.depth, top = 2.dp))
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
