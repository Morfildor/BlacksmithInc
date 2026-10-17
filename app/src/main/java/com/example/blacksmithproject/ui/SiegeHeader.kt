package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.R
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Ember
import com.example.blacksmithproject.ui.theme.EmberContainer
import com.example.blacksmithproject.ui.theme.ForgePanel
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space

/** How loudly the header speaks of the siege: calm while it is days away, gold the day before, ember on the day. Never animated. */
enum class SiegeTone { CALM, TOMORROW, TONIGHT }

/** One day of the current siege cycle on the header's row of markers. */
@Immutable data class DayMarkUi(val day: Int, val past: Boolean, val today: Boolean, val siege: Boolean)

/**
 * The workshop's header in planning: the day, when the next siege comes, the days of the cycle up to it, and on the eve
 * and the day itself how many places on the wall the forecast fills. [besieger] only once it is known under the reveal
 * rules (a guild run: the besieger is committed; a classic run: the warning is out).
 */
@Immutable
data class SiegeHeaderUi(
    val day: Int, val siegeDay: Int, val countdown: String, val tone: SiegeTone,
    val besieger: String?, val marks: List<DayMarkUi>, val defenders: String?,
) {
    /** The strip's first part as it reads on screen: the countdown and who comes, with the separator before the defenders bound to it (no-break spaces, as drawn). */
    val lead: String get() = countdown + besieger?.let { " · $it" }.orEmpty() + if (defenders != null) " ·" else ""
}

/** The most markers the row shows; a longer wait (a schedule this game does not have today) shows the countdown alone. */
private const val MAX_MARKS = 7

/**
 * The header for [day] with the siege on [siegeDay]. The cycle starts the day after the last siege, which is
 * [siegeDay] - [interval] (the engine moves `nextSiegeDay` on by `siegeInterval` after each one), never before day 1 and
 * never after today. [filled] of [places] is the wall as the forecast stands; it is said only on the eve and the day.
 */
fun siegeHeader(day: Int, siegeDay: Int, interval: Int, besieger: String?, filled: Int?, places: Int?): SiegeHeaderUi {
    val left = siegeDay - day
    val start = maxOf(1, siegeDay - interval + 1).coerceAtMost(day)
    return SiegeHeaderUi(
        day = day, siegeDay = siegeDay,
        countdown = when { left <= 0 -> "Siege tonight"; left == 1 -> "Siege tomorrow"; else -> "Siege in $left days" },
        tone = when { left <= 0 -> SiegeTone.TONIGHT; left == 1 -> SiegeTone.TOMORROW; else -> SiegeTone.CALM },
        besieger = besieger,
        marks = (start..siegeDay).takeIf { siegeDay >= day && siegeDay - start < MAX_MARKS }?.map { DayMarkUi(it, it < day, it == day, it == siegeDay) }.orEmpty(),
        defenders = if (left <= 1 && filled != null && places != null) "$filled of $places defenders" else null,
    )
}

/**
 * The header of a planning screen, from the save as it stands: a guild run reads the wall forecast (members and fit
 * townsfolk alike), a classic run the siege forecast's champions. Never the shop day's: that screen has its own strip.
 */
fun UiState.Playing.siegeHeader(interval: Int, championCount: Int): SiegeHeaderUi {
    val town = state.town
    val wall = guild?.wall
    return if (wall != null) siegeHeader(state.day, town.nextSiegeDay, interval, wall.besieger.takeIf { wall.committed }, wall.defenders.size, wall.places)
    else siegeHeader(
        state.day, town.nextSiegeDay, interval, shop.threat?.takeIf { it.warned }?.besieger,
        forecast?.champions?.size, championCount.takeIf { forecast != null },
    )
}

/**
 * The day in the heading serif, the strongest text on the screen, with gold, energy and Settings beside it as one smaller
 * group (under it only when they cannot share a line); then the siege strip, which opens where the siege is prepared.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkshopHeader(ui: SiegeHeaderUi, gold: Int, energy: Int, onSettings: () -> Unit, onOpenSiege: () -> Unit, openLabel: String) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // A count keeps at least two digits' room (the figures are tabular), so 10 to 8 energy moves nothing beside it.
    val digit = with(density) { measurer.measure("8", MaterialTheme.typography.titleSmall, softWrap = false, maxLines = 1).size.width.toDp() }
    val lineStyle = MaterialTheme.typography.labelLarge
    // One layout at every size: the day with the purse, then the strip across the whole width, so the band keeps its
    // height from day to day. The purse wraps under the day only when the two cannot share a line.
    Column(Modifier.fillMaxWidth().background(ForgePanel).testTag("workshop_header")) {
        FlowRow(
            Modifier.fillMaxWidth().padding(start = Space.md, end = Space.xs),
            horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Day ${ui.day}", style = MaterialTheme.typography.titleLarge, color = Cream, maxLines = 1,
                modifier = Modifier.semantics { heading(); contentDescription = "Day ${ui.day}" }.testTag("header_day"),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stat(R.drawable.icon_gold, "Gold", "$gold", digit, Gold)
                Spacer(Modifier.width(Space.md))
                Stat(R.drawable.icon_energy, "Energy", "$energy", digit)
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp).testTag("nav_settings")) { PixelIcon(R.drawable.icon_settings, 28.dp, description = "Settings") }
            }
        }
        // The whole line while each of its two parts (the countdown with who comes, how many stand) fits a line beside the
        // chevron: at most two lines, which the strip's 48 dp holds at ordinary sizes. Otherwise the countdown alone (who
        // comes and how many stand are said to a screen reader and are one tap away): the strip never grows a third line.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = Space.md, end = Space.sm, bottom = Space.xs)) {
            fun width(text: String) = measurer.measure(text, lineStyle, softWrap = false, maxLines = 1).size.width
            val room = constraints.maxWidth - with(density) { 40.dp.toPx() }
            val brief = width(ui.lead) > room || (ui.defenders?.let(::width) ?: 0) > room
            // Brief: who comes stays beside the countdown while both fit one line; how many stand is always its own line.
            val who = !brief || ui.besieger == null || width(ui.countdown + " · " + ui.besieger) <= room
            SiegeStrip(ui, onOpenSiege, openLabel, brief = brief, briefWho = who)
        }
    }
}

@Composable
private fun Stat(icon: Int, label: String, value: String, digit: Dp, color: Color = Cream) {
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "$label $value" }, verticalAlignment = Alignment.CenterVertically) {
        PixelIcon(icon, 22.dp)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = digit * maxOf(2, value.length)))
    }
}

/**
 * The countdown and who comes, then the cycle as a slim track ending at the wall, all one tap: plain text on the
 * header's ground, no plate or tiles. The tone is said by the countdown's colour alone (cream, gold the day before,
 * ember on the day).
 */
@Composable
private fun SiegeStrip(ui: SiegeHeaderUi, onOpen: () -> Unit, openLabel: String, modifier: Modifier = Modifier, brief: Boolean = false, briefWho: Boolean = true) {
    Row(
        modifier.fillMaxWidth().clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen).heightIn(min = 48.dp).testTag("siege_strip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SiegeLine(ui, brief = brief, briefWho = briefWho)
            // Under the countdown it counts towards: it reads as the days to the siege.
            if (ui.marks.isNotEmpty()) CycleTrack(ui, Modifier.padding(top = 6.dp))
        }
        Chevron(ChevronWay.RIGHT, Modifier.padding(start = Space.sm, end = Space.xs), tint = CreamMuted)
    }
}

/**
 * The countdown in its tone, the besieger muted after it, and on the eve and the day how many places the forecast fills,
 * as one paragraph that wraps as words do (a separator never starts a line).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SiegeLine(ui: SiegeHeaderUi, modifier: Modifier = Modifier, brief: Boolean = false, briefWho: Boolean = true) {
    val ink = when (ui.tone) { SiegeTone.CALM -> CreamMuted; SiegeTone.TOMORROW -> Gold; SiegeTone.TONIGHT -> Ember }
    val small = MaterialTheme.typography.bodySmall
    if (brief) {
        // Said whole to a screen reader; on screen, the countdown (with who comes when it fits), then how many stand.
        Column(modifier.semantics(mergeDescendants = true) { contentDescription = listOfNotNull(ui.countdown, ui.besieger, ui.defenders).joinToString(", ") }) {
            Text(
                ui.countdown + (ui.besieger?.takeIf { briefWho }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1,
                modifier = Modifier.testTag("siege_countdown"),
            )
            ui.defenders?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = small.fontWeight, color = CreamMuted, maxLines = 1, modifier = Modifier.testTag("siege_defenders")) }
        }
        return
    }
    FlowRow(modifier.semantics(mergeDescendants = true) {}, itemVerticalAlignment = Alignment.CenterVertically) {
        // The separator is bound to the word before it (a no-break space), so a wrapped line never starts with it.
        Text(
            buildAnnotatedString {
                // The same words as [SiegeHeaderUi.lead], which the header measures.
                append(ui.countdown)
                ui.besieger?.let { withStyle(SpanStyle(color = CreamMuted, fontWeight = small.fontWeight)) { append(" · $it") } }
                if (ui.defenders != null) withStyle(SpanStyle(color = CreamMuted)) { append(" ·") }
            },
            style = MaterialTheme.typography.labelLarge, color = ink, modifier = Modifier.padding(end = Space.xs).testTag("siege_countdown"),
        )
        ui.defenders?.let { Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = small.fontWeight, color = CreamMuted, modifier = Modifier.testTag("siege_defenders")) }
    }
}

/**
 * The cycle as a hairline track under the countdown: past days bronze, today gold (ember on the siege's day), days to
 * come dark, the siege's own day a dark ember. TalkBack hears where today stands in it ("Siege cycle: day 3 of 5").
 */
@Composable
private fun CycleTrack(ui: SiegeHeaderUi, modifier: Modifier = Modifier) {
    val where = ui.marks.indexOfFirst { it.today }.takeIf { it >= 0 }?.let { "Siege cycle: day ${it + 1} of ${ui.marks.size}" }
    Row(modifier.fillMaxWidth().clearAndSetSemantics { where?.let { contentDescription = it } }.testTag("siege_marks"), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        ui.marks.forEach { m ->
            val color = when {
                m.today -> if (ui.tone == SiegeTone.TONIGHT) Ember else Gold
                m.past -> Bronze
                m.siege -> EmberContainer
                else -> BronzeDeep
            }
            Box(Modifier.weight(1f).height(3.dp).background(color, MaterialTheme.shapes.extraSmall))
        }
    }
}

/**
 * Who comes and what tells against them, once the siege is warned: one slim line at the head of the Shop and the Forge,
 * where it decides what is bought and what is forged. Never the countdown (the header's). [note]: the Shop's one line on
 * the first warned morning, what buyers now weigh.
 */
@Composable
fun SiegeMatchup(threat: ThreatUi, tag: String, note: String? = null, inset: Boolean = true) {
    // The besieger's name is the header's; what tells against them (and a classic run's outlook) is this line's own.
    val facts = listOfNotNull(threat.matchup?.let { m -> threat.besieger?.let { m.removePrefix(it).removePrefix(" · ") } ?: m }?.takeIf { it.isNotBlank() }, threat.outlook)
    if (facts.isEmpty() && note == null) return
    Column(
        Modifier.fillMaxWidth().padding(start = if (inset) Space.md else 0.dp, end = if (inset) Space.md else 0.dp, top = Space.sm).semantics(mergeDescendants = true) {}.testTag(tag),
    ) {
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = if (threat.today) Ember else CreamMuted)
        note?.let { Secondary(it) }
    }
}

/**
 * The siege's own day at the head of the Shop, in place of [SiegeMatchup]: an ember rule, "Siege tonight", who comes (the
 * army in a living era, else the besieger) once it is known, what tells against them and how many stand on the wall.
 * The header says the countdown and the defenders too; on this day they are worth saying twice. End Day keeps its label:
 * the day's trading still comes first.
 */
@Composable
fun SiegeTonight(threat: ThreatUi, defenders: String?, tag: String, note: String? = null) {
    val who = (threat.army ?: threat.besieger).takeIf { threat.warned }
    // Under an army's name the besieger stays in the line (what they are weak to is the besieger's); under the besieger's own name it is not said twice.
    val against = threat.matchup?.takeIf { threat.warned }?.let { m -> if (threat.army == null) threat.besieger?.let { m.removePrefix(it).removePrefix(" · ") } ?: m else m }?.takeIf { it.isNotBlank() }
    Column(
        Modifier.fillMaxWidth().padding(start = Space.md, end = Space.md, top = Space.md)
            .drawBehind { drawRect(Ember, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = Space.md, top = Space.xs, bottom = Space.xs)
            .semantics(mergeDescendants = true) {}.testTag(tag),
    ) {
        Text("Siege tonight", style = MaterialTheme.typography.titleMedium, color = Ember, modifier = Modifier.semantics { heading() }.testTag("shop_siege_tonight"))
        who?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.testTag("shop_siege_who")) }
        listOfNotNull(against, threat.outlook, defenders).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = CreamMuted) }
        note?.let { Secondary(it) }
    }
}
