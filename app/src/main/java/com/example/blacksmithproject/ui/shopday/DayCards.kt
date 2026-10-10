@file:OptIn(ExperimentalLayoutApi::class)

package com.example.blacksmithproject.ui.shopday

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.blacksmithproject.ui.FramedPanel
import com.example.blacksmithproject.ui.PrimaryActionButton
import com.example.blacksmithproject.ui.Sprites
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.BlessingId
import com.tinyblacksmith.core.model.CombatReplay
import com.tinyblacksmith.core.model.WeaponSnapshot
import com.tinyblacksmith.core.shopday.AftermathKind

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) = Text(text, style = MaterialTheme.typography.bodyLarge, modifier = modifier)

@Composable
private fun Quiet(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

private fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

/** The shop as it stood when the door opened: how many came today and what was on the shelf. */
@Composable
fun ShopOpenCard(open: Beat.Open, day: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Overline("Day $day")
        CardTitle("The shop is open")
        Body("${count(open.blades, "blade", "blades")} on the shelf. ${count(open.visitors, "customer", "customers")} came to the counter today.")
        Quiet("Tap anywhere to go on. Skip day jumps to tomorrow.", Modifier.padding(top = Space.sm))
    }
}

/** Everyone who was not shown one by one: a line per outcome, with the faces and names it counts. */
@Composable
fun TallyCard(tally: Beat.Tally, onOpenHero: (FaceUi) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        CardTitle(if (tally.featured == 0) "${tally.count} came by" else "${tally.count} more came by", Modifier.testTag("shopday_tally_title"))
        tally.groups.forEach { group ->
            Body(group.line, Modifier.padding(top = Space.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) { group.faces.forEach { PersonChip(it, onOpenHero) } }
        }
    }
}

/** The till by kind, then the purse it left. */
@Composable
fun ShopCloseCard(close: Beat.Close, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Overline("The till")
        ReceiptRows(close.rows)
        Body(close.counts, Modifier.padding(top = Space.xs))
        Quiet("Purse: ${close.purse} gold")
    }
}

/** A day with nobody to serve: one card. An empty shelf names each person who looked in, once. */
@Composable
fun QuietDayCard(quiet: Beat.Quiet, onOpenHero: (FaceUi) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Overline("No sale today")
        Body(quiet.text, Modifier.testTag("shopday_quiet"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) { quiet.faces.forEach { PersonChip(it, onOpenHero) } }
    }
}

/**
 * One consequence beyond the door: the people and the blade it names, then the record's own sentence. A merchant
 * resale is labelled as not a shop sale and has no receipt. The fight can be watched; its outcome is already in the text.
 */
@Composable
fun AftermathCard(
    beat: Beat.Aftermath,
    onOpenHero: (FaceUi) -> Unit,
    onOpenBlade: (WeaponSnapshot) -> Unit,
    onWatchFight: (CombatReplay) -> Unit,
    onOpenGazette: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = beat.card
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        card.note?.let { Overline(it, Modifier.testTag("shopday_aftermath_note"), strong = true) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(card.title, Modifier.weight(1f).testTag("shopday_aftermath_kind"))
            val art = card.materialId?.let { Sprites.material(it) }?.let { it to 48 } ?: card.factionId?.let { Sprites.faction(it) }?.let { it to 80 }
            art?.let { (res, px) -> PixelSprite(res, wholeScale(px, 56.dp), Modifier.clearAndSetSemantics {}) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            card.hero?.let { PersonChip(it, onOpenHero) }
            card.other?.let {
                PersonChip(it, onOpenHero, note = when (card.kind) { AftermathKind.INHERITED -> "carried it before"; AftermathKind.GUILD_LESSON -> "gave the lesson"; else -> null })
            }
            card.champions.forEach { PersonChip(it, onOpenHero) }
            card.blade?.let { BladeChip(it, onOpenBlade) }
        }
        Body(card.text, Modifier.testTag("shopday_aftermath_text"))
        card.replay?.let { replay ->
            OutlinedButton(onClick = { onWatchFight(replay) }, shape = MaterialTheme.shapes.small, modifier = Modifier.heightIn(min = 48.dp).testTag("shopday_watch")) { Text("Watch the fight") }
        }
        if (beat.moreInGazette > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Quiet("${beat.moreInGazette} more in the Gazette", Modifier.weight(1f))
                TextButton(onClick = onOpenGazette, modifier = Modifier.heightIn(min = 48.dp).testTag("shopday_gazette")) { Text("Read the Gazette") }
            }
        }
    }
}

/** The fight as recorded: the outcome first and at once, then every round. Nothing here waits on a timer. */
@Composable
fun ReplayOverlay(replay: CombatReplay, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Dialog(onDismissRequest = onClose) {
        FramedPanel(modifier = modifier.semantics { testTagsAsResourceId = true }) {
            CardTitle(replay.title)
            Text(replay.outcome, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = Space.xs).testTag("shopday_replay_outcome"))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                replay.rounds.forEachIndexed { i, round ->
                    Quiet("${i + 1}. ${round.attacker} ${round.note} (${round.damage})", Modifier.padding(top = 2.dp))
                }
            }
            PrimaryActionButton("Close", onClose, Modifier.fillMaxWidth().padding(top = Space.sm).testTag("shopday_replay_close"))
        }
    }
}

/** The run is over: the cause as recorded and the day it happened. "See the legacy" is the screen's wide button. */
@Composable
fun FallenCard(fallen: Beat.Fallen, onOpenGazette: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Overline("The run ended on day ${fallen.days}", strong = true)
        fallen.cause?.let { CardTitle(it, Modifier.testTag("shopday_fallen_cause")) }
        TextButton(onClick = onOpenGazette, modifier = Modifier.heightIn(min = 48.dp).testTag("shopday_gazette")) { Text("Read the Gazette") }
    }
}

/** The next morning in one glance: one lead and its reason, what is in the shop, the next siege. "Begin day N" is the screen's wide button. */
@Composable
fun TomorrowCard(tomorrow: Beat.Tomorrow, onOpenGazette: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (tomorrow.action.isNotEmpty()) {
            Overline("Worth doing first", strong = true)
            CardTitle(tomorrow.action, Modifier.testTag("shopday_lead"))
            tomorrow.reason?.let { Body(it, Modifier.testTag("shopday_lead_reason")) }
        }
        ReceiptRows(
            listOf(
                ReceiptRow("Purse", "${tomorrow.gold} gold"),
                ReceiptRow("On the shelf", count(tomorrow.shelf, "blade", "blades")),
                ReceiptRow("In storage", count(tomorrow.storage, "blade", "blades")),
            ),
            Modifier.padding(top = Space.sm),
        )
        Quiet(tomorrow.siege)
        TextButton(onClick = onOpenGazette, modifier = Modifier.heightIn(min = 48.dp).testTag("shopday_gazette")) { Text("Read the Gazette") }
    }
}

/** The town's thanks after a held siege: one button per blessing. "Decide later" is the screen's wide button. */
@Composable
fun BlessingChoices(choices: List<BlessingUi>, onChoose: (BlessingId) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        CardTitle("The town offers a blessing")
        choices.forEach { b ->
            OutlinedButton(onClick = { onChoose(b.id) }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("blessing_${b.id.value}")) {
                Sprites.blessing(b.id)?.let { PixelSprite(it, wholeScale(48, 40.dp), Modifier.clearAndSetSemantics {}); Spacer(Modifier.width(12.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(b.name, style = MaterialTheme.typography.titleSmall)
                    Text(b.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Shown on a cold start with an unwatched day: the day is already saved, so both choices are safe. */
@Composable
fun ResumePrompt(day: Int, took: Int?, onResume: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    FramedPanel(modifier = modifier.fillMaxWidth().padding(Space.md)) {
        CardTitle("Day $day is done and saved.")
        took?.let { Body("The shop took $it gold.", Modifier.padding(top = Space.sm)) }
        PrimaryActionButton("Resume the day", onResume, Modifier.fillMaxWidth().padding(top = Space.md).testTag("shopday_resume"))
        OutlinedButton(onClick = onSkip, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(top = Space.sm).heightIn(min = 48.dp).testTag("shopday_resume_skip")) { Text("Skip to tomorrow") }
    }
}
