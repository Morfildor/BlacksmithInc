package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.R
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.BronzeLine
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Ember
import com.example.blacksmithproject.ui.theme.EmberContainer
import com.example.blacksmithproject.ui.theme.ForgePanel
import com.example.blacksmithproject.ui.theme.ForgeSlot
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
)

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
        countdown = when { left <= 0 -> "SIEGE TONIGHT · Day $siegeDay"; left == 1 -> "Siege tomorrow · Day $siegeDay"; else -> "Siege in $left days · Day $siegeDay" },
        tone = when { left <= 0 -> SiegeTone.TONIGHT; left == 1 -> SiegeTone.TOMORROW; else -> SiegeTone.CALM },
        besieger = besieger,
        marks = (start..siegeDay).takeIf { siegeDay >= day && siegeDay - start < MAX_MARKS }?.map { DayMarkUi(it, it < day, it == day, it == siegeDay) }.orEmpty(),
        defenders = if (left <= 1 && filled != null && places != null) "$filled of $places defender slots filled" else null,
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
 * group (under it when the text is large); then the siege strip, which opens where the siege is prepared.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkshopHeader(ui: SiegeHeaderUi, gold: Int, energy: Int, onSettings: () -> Unit, onOpenSiege: () -> Unit, openLabel: String) {
    val large = LocalDensity.current.fontScale >= 1.3f
    // The markers are the first thing to go: on a short screen or with large text the countdown says it alone.
    val markers = ui.marks.isNotEmpty() && !large && LocalConfiguration.current.screenHeightDp >= 700
    Column(Modifier.fillMaxWidth().background(ForgePanel).testTag("workshop_header")) {
        FlowRow(
            Modifier.fillMaxWidth().padding(start = Space.md, end = Space.xs),
            horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "DAY ${ui.day}", style = MaterialTheme.typography.headlineMedium, color = Cream, maxLines = 1,
                modifier = Modifier.semantics { heading(); contentDescription = "Day ${ui.day}" }.testTag("header_day"),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Stat(R.drawable.icon_gold, "Gold", "$gold", Gold)
                Spacer(Modifier.width(Space.md))
                Stat(R.drawable.icon_energy, "Energy", "$energy")
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp).testTag("nav_settings")) { Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = Gold) }
            }
        }
        SiegeStrip(ui, markers, onOpenSiege, openLabel)
    }
}

@Composable
private fun Stat(icon: Int, label: String, value: String, color: Color = Cream) {
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "$label $value" }, verticalAlignment = Alignment.CenterVertically) {
        PixelImage(icon, wholePixelDp(24, 22.dp), description = null)
        Spacer(Modifier.width(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1)
    }
}

/** The countdown and who comes, the cycle's days, and on the eve and the day the wall's places: one tappable plate. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SiegeStrip(ui: SiegeHeaderUi, markers: Boolean, onOpen: () -> Unit, openLabel: String) {
    val (ground, edge, ink) = when (ui.tone) {
        SiegeTone.CALM -> Triple(ForgePanel, BronzeDeep, Cream)
        SiegeTone.TOMORROW -> Triple(ForgePanel, Gold, Gold)
        SiegeTone.TONIGHT -> Triple(EmberContainer, Ember, Cream)
    }
    Column(
        Modifier.fillMaxWidth().padding(start = Space.sm, end = Space.sm, bottom = Space.sm)
            .background(ground, MaterialTheme.shapes.small).border(if (ui.tone == SiegeTone.TONIGHT) 2.dp else 1.dp, edge, MaterialTheme.shapes.small)
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen).heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp).semantics(mergeDescendants = true) {}.testTag("siege_strip"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // The countdown and who comes on one line, the cycle's days and the wall's places on the next; each wraps before it is cut.
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(ui.countdown, style = MaterialTheme.typography.titleSmall, color = ink, modifier = Modifier.testTag("siege_countdown"))
                ui.besieger?.let { Text("· $it", style = MaterialTheme.typography.bodySmall, color = if (ui.tone == SiegeTone.TONIGHT) Cream else CreamMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("siege_besieger")) }
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = if (ui.tone == SiegeTone.CALM) CreamMuted else ink)
        }
        if (markers || ui.defenders != null) FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (markers) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("siege_marks")) { ui.marks.forEach { DayMark(it, ui.tone) } }
            ui.defenders?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = ink, modifier = Modifier.padding(start = 4.dp).testTag("siege_defenders")) }
        }
    }
}

/** A day of the cycle: its number; today outlined, past days muted, the siege day with the wall beside its number. */
@Composable
private fun DayMark(m: DayMarkUi, tone: SiegeTone) {
    val edge = when {
        m.today -> if (tone == SiegeTone.TONIGHT) Ember else Gold
        m.siege -> BronzeLine
        else -> BronzeDeep
    }
    Row(
        Modifier.background(if (m.siege) ForgeSlot else Color.Transparent, MaterialTheme.shapes.extraSmall)
            .border(if (m.today) 2.dp else 1.dp, edge, MaterialTheme.shapes.extraSmall).widthIn(min = 32.dp).padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
    ) {
        Text(
            "${m.day}", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
            color = when { m.past -> CreamMuted.copy(alpha = 0.55f); m.today -> Cream; else -> CreamMuted },
        )
        if (m.siege) Box { PixelImage(Sprites.siegeWall(damaged = false), 18.dp, description = null) }
    }
}

/**
 * Who comes and what tells against them, once the siege is warned: one slim line at the head of the Shop and the Forge,
 * where it decides what is bought and what is forged. Never the countdown (the header's). [note]: the Shop's one line on
 * the first warned morning, what buyers now weigh.
 */
@Composable
fun SiegeMatchup(threat: ThreatUi, tag: String, note: String? = null) {
    // The besieger's name is the header's; what tells against them (and a classic run's outlook) is this line's own.
    val facts = listOfNotNull(threat.matchup?.let { m -> threat.besieger?.let { m.removePrefix(it).removePrefix(" · ") } ?: m }?.takeIf { it.isNotBlank() }, threat.outlook)
    if (facts.isEmpty() && note == null) return
    Column(
        Modifier.fillMaxWidth().background(ForgePanel).padding(horizontal = Space.md, vertical = 6.dp).semantics(mergeDescendants = true) {}.testTag(tag),
    ) {
        if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = if (threat.today) Ember else Cream)
        note?.let { Secondary(it) }
    }
}
