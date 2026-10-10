package com.example.blacksmithproject.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.Secondary
import com.example.blacksmithproject.ui.SectionHeader
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.DayResolution
import com.tinyblacksmith.core.model.EventRecord
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.WeaponId

/** One labelled line of a detail sheet. A line with [heroId] or [weaponId] opens that hero's or blade's sheet. */
@Immutable
data class Fact(val label: String, val value: String, val heroId: HeroId? = null, val weaponId: WeaponId? = null)

internal const val AT_THE_COUNTER = "At the counter"
internal const val NOW = "Now"

/** The lines of [now] that [counter] does not already say, in the order of [now]. */
internal fun changed(counter: List<Fact>, now: List<Fact>): List<Fact> = now.filter { n -> counter.none { it.label == n.label && it.value == n.value } }

/** "Day 12: text", with the era when it is not the one being played. Records are stored oldest first; sheets show newest first. */
internal fun dated(state: GameState, era: Int, day: Int, text: String): String = (if (era != state.era) "Era $era, day $day: " else "Day $day: ") + text

internal fun recordsOf(state: GameState, id: String): List<EventRecord> = state.events.filter { id in it.subjectIds }

/** The visit snapshot of [id] in a stored day, for a caller that has only the ID: the first time they stood at the counter. */
fun DayResolution.customerSnapshot(id: HeroId) = visits.firstNotNullOfOrNull { v -> v.customer?.takeIf { it.heroId == id } }

/** The snapshot of the blade [id] in a stored day: on the shelf, in a customer's hand or carried into the field. */
fun DayResolution.weaponSnapshot(id: WeaponId) = shopWeapons.firstOrNull { it.weaponId == id }
    ?: visits.firstNotNullOfOrNull { v -> v.customer?.equipped?.takeIf { it.weaponId == id } }
    ?: field.firstNotNullOfOrNull { f -> f.weapon?.takeIf { it.weaponId == id } }

@Composable
internal fun SheetSection(title: String, modifier: Modifier = Modifier) = SectionHeader(title, modifier)

/**
 * Label and value side by side; stacked once the text is large enough that two columns would wrap every line.
 * A fact that links to another sheet is one 48 dp target that says where it goes.
 */
@Composable
internal fun FactRow(fact: Fact, onOpenHero: (HeroId) -> Unit, onOpenItem: (WeaponId) -> Unit, modifier: Modifier = Modifier) {
    val open: (() -> Unit)? = fact.heroId?.let { { onOpenHero(it) } } ?: fact.weaponId?.let { { onOpenItem(it) } }
    val row = if (open == null) modifier.fillMaxWidth().padding(vertical = 2.dp).semantics(mergeDescendants = true) {}
    else modifier.fillMaxWidth().clickable(onClickLabel = "Open ${fact.value}", role = Role.Button, onClick = open).heightIn(min = 48.dp).padding(vertical = 2.dp)
        .testTag(if (fact.heroId != null) "sheet_open_hero" else "sheet_open_item")
    val valueColor = if (open == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(row.padding(vertical = 2.dp), verticalArrangement = Arrangement.Center) {
            Secondary(fact.label)
            Text(fact.value + if (open != null) "  ›" else "", style = MaterialTheme.typography.bodyMedium, color = valueColor)
        }
    } else {
        // Both on the first baseline: a value with a taller glyph (the rarity mark) must not sit lower than its label.
        // The pair is centred in a 48 dp target by the box; the baseline is shared inside it.
        Box(row, contentAlignment = Alignment.CenterStart) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(fact.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.34f).alignByBaseline())
                Text(fact.value + if (open != null) "  ›" else "", style = MaterialTheme.typography.bodyMedium, color = valueColor, modifier = Modifier.weight(0.66f).alignByBaseline())
            }
        }
    }
}

/** A block of facts under its heading; nothing at all when there are none. */
@Composable
internal fun FactBlock(title: String?, facts: List<Fact>, tag: String, onOpenHero: (HeroId) -> Unit, onOpenItem: (WeaponId) -> Unit) {
    if (facts.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = Space.sm).testTag(tag)) {
        title?.let { SheetSection(it) }
        facts.forEach { FactRow(it, onOpenHero, onOpenItem) }
    }
}

/** Dated lines, newest first as given. */
@Composable
internal fun Lines(lines: List<String>, empty: String) {
    if (lines.isEmpty()) Secondary(empty)
    lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
}
