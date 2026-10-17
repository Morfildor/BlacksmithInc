package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
 * group (under it when the text is large); then the siege strip, which opens where the siege is prepared.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkshopHeader(ui: SiegeHeaderUi, gold: Int, energy: Int, onSettings: () -> Unit, onOpenSiege: () -> Unit, openLabel: String) {
    val large = LocalDensity.current.fontScale >= 1.3f
    val stats: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stat(R.drawable.icon_gold, "Gold", "$gold", Gold)
            Spacer(Modifier.width(Space.md))
            Stat(R.drawable.icon_energy, "Energy", "$energy")
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp).testTag("nav_settings")) { Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = Gold) }
        }
    }
    val day: @Composable () -> Unit = {
        Text(
            "Day ${ui.day}", style = MaterialTheme.typography.titleLarge, color = Cream, maxLines = 1,
            modifier = Modifier.semantics { heading(); contentDescription = "Day ${ui.day}" }.testTag("header_day"),
        )
    }
    // V2: one band. The day and the countdown stack on the left as one tap to where the siege is prepared, the purse
    // and energy sit on the right, and the cycle is a hairline track along the foot. With large text the purse takes a
    // row of its own beside the day, as before.
    Column(Modifier.fillMaxWidth().background(ForgePanel).testTag("workshop_header")) {
        if (large) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = Space.md, end = Space.xs),
                horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.Center,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) { day(); stats() }
            // A short screen with large text keeps only the countdown here: who comes and how many stand are one tap away.
            val tight = LocalConfiguration.current.screenHeightDp < 700
            SiegeStrip(ui, onOpenSiege, openLabel, Modifier.padding(start = Space.md, end = Space.sm, bottom = Space.xs), brief = tight)
        } else Row(Modifier.fillMaxWidth().padding(start = Space.md, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpenSiege).heightIn(min = 48.dp).padding(vertical = 6.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                day()
                SiegeLine(ui, Modifier.testTag("siege_strip"))
                // Under the countdown it counts towards, not under the purse: it reads as the days to the siege.
                if (ui.marks.isNotEmpty()) CycleTrack(ui, Modifier.padding(top = 6.dp))
            }
            stats()
        }
        if (large && ui.marks.isNotEmpty()) CycleTrack(ui, Modifier.padding(start = Space.md, end = Space.md, bottom = 6.dp))
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

/**
 * The countdown and who comes, then the cycle as a slim track ending at the wall: plain text on the header's ground, no
 * plate or tiles. The tone is said by the countdown's colour alone (cream, gold the day before, ember on the day).
 */
@Composable
private fun SiegeStrip(ui: SiegeHeaderUi, onOpen: () -> Unit, openLabel: String, modifier: Modifier = Modifier, brief: Boolean = false) {
    Row(
        modifier.fillMaxWidth().clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen).heightIn(min = 48.dp).testTag("siege_strip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiegeLine(ui, Modifier.weight(1f), brief)
        Chevron(ChevronWay.RIGHT, Modifier.padding(start = Space.sm, end = Space.xs), tint = CreamMuted)
    }
}

/**
 * The countdown in its tone, the besieger muted after it, and on the eve and the day how many places the forecast fills,
 * as one paragraph that wraps as words do (a separator never starts a line).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SiegeLine(ui: SiegeHeaderUi, modifier: Modifier = Modifier, brief: Boolean = false) {
    val ink = when (ui.tone) { SiegeTone.CALM -> CreamMuted; SiegeTone.TOMORROW -> Gold; SiegeTone.TONIGHT -> Ember }
    val small = MaterialTheme.typography.bodySmall
    if (brief) {
        // Said whole to a screen reader; on screen, the countdown alone.
        Text(
            ui.countdown, style = MaterialTheme.typography.labelLarge, color = ink,
            modifier = modifier.testTag("siege_countdown").semantics { contentDescription = listOfNotNull(ui.countdown, ui.besieger, ui.defenders).joinToString(", ") },
        )
        return
    }
    FlowRow(modifier.semantics(mergeDescendants = true) {}, itemVerticalAlignment = Alignment.CenterVertically) {
        // The separator is bound to the word before it (a no-break space), so a wrapped line never starts with it.
        Text(
            buildAnnotatedString {
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
