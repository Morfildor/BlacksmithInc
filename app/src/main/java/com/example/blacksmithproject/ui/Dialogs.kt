package com.example.blacksmithproject.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.PaperInk
import com.example.blacksmithproject.ui.theme.PaperInkMuted
import com.example.blacksmithproject.ui.theme.PaperRule
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.model.CombatReplay
import com.tinyblacksmith.core.model.DayResolution
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.ReplayKind
import com.tinyblacksmith.core.model.WeaponId
import kotlinx.coroutines.delay

/** Item card after an atomic forge. The reveal is decorative; the weapon already exists in the saved state. */
@Composable
fun ForgeResultDialog(s: UiState.Playing, weaponId: WeaponId, vm: GameViewModel, reducedMotion: Boolean) {
    val w = s.state.weapons[weaponId] ?: run { vm.dismissReveal(); return }
    val content = vm.engine.content
    val suggested = vm.engine.suggestedPrice(w)
    val reveal by animateFloatAsState(targetValue = 1f, animationSpec = tween(if (reducedMotion) 0 else 600), label = "reveal")
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = vm::dismissReveal,
        title = {
            Column(Modifier.fillMaxWidth().graphicsLayer { alpha = reveal }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp)) {
                    WeaponSprite(w, size = 96.dp)
                    if (w.signatureId != null || w.rarity >= Rarity.EPIC) MilestoneBurst(96.dp, reducedMotion)
                }
                Text(w.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.sm))
                Text("${Labels.rarity(w.rarity)} · ${Labels.quality(w.quality)}", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        },
        text = {
            Column(Modifier.graphicsLayer { alpha = reveal }.verticalScroll(rememberScrollState())) {
                Secondary("${content.family(w.familyId).name} of ${content.material(w.coreId).name} and ${content.material(w.augmentId).name}" + (w.title?.let { " · \"$it\"" } ?: ""))
                if (w.affixes.isNotEmpty() || w.flaws.isNotEmpty()) Spacer(Modifier.heightIn(min = Space.sm))
                w.affixes.forEach { Text("+ ${content.affix(it).name}: ${content.affix(it).description}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
                w.flaws.forEach { Text("− ${content.affix(it).name}: ${content.affix(it).description}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
                Secondary("Suggested price $suggested gold. Set your own in the Market.", Modifier.padding(top = Space.md))
            }
        },
        confirmButton = {
            Button(onClick = { vm.dispatch(Command.ToggleShelf(w.id, true, suggested)); vm.dismissReveal() }, enabled = w.isInStorage, modifier = Modifier.heightIn(min = 48.dp).testTag("reveal_list")) { Text("List at $suggested") }
        },
        dismissButton = { OutlinedButton(onClick = vm::dismissReveal, modifier = Modifier.heightIn(min = 48.dp).testTag("reveal_store")) { Text("Store") } },
    )
}

/**
 * The Gazette as a newspaper: masthead, siege diorama, the day's edition (lede, tally, Shop / Heroes / Town / Forge),
 * then the field report with the rounds of each siege and each notable fight folded behind its outcome. Every line derives from real event
 * records; stepping is purely presentational and skippable (GDD 11).
 */
@Composable
fun DayReportDialog(s: UiState.Playing, r: DayResolution, vm: GameViewModel, reducedMotion: Boolean) {
    val totalSteps = r.replays.sumOf { it.rounds.size + 1 }
    var shown by remember(r.commandId) { mutableIntStateOf(if (reducedMotion) totalSteps else 0) }
    LaunchedEffect(r.commandId, reducedMotion) {
        if (reducedMotion) { shown = totalSteps; return@LaunchedEffect }
        while (shown < totalSteps) { delay(700); shown += 1 }
    }
    // Back and a tap outside never mean "Begin day": only the button below closes the report.
    Dialog(onDismissRequest = { vm.back() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Color.Transparent,
            contentColor = PaperInk,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.92f).dp).padding(horizontal = Space.md).paperBackground().semantics { testTagsAsResourceId = true },
        ) {
            Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
                // "EMBERFALL GAZETTE — DAY 3": the paper's name large, the date as a dateline under it.
                val masthead = Gazette.masthead(r.day)
                Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(masthead.substringBefore(" — "), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 1)
                    if (" — " in masthead) Text(masthead.substringAfter(" — "), style = MaterialTheme.typography.labelMedium, color = PaperInkMuted)
                }
                PaperRuleLine(top = Space.sm, bottom = 2.dp)
                PaperRuleLine(top = 0.dp, bottom = Space.sm)
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    r.replays.firstOrNull()?.takeIf { it.kind == ReplayKind.SIEGE }?.let { replay -> ReplayStage(replay, shown, s, vm, reducedMotion) }  // the siege comes first; fights are text only
                    val edition = remember(r.commandId) { Gazette.edition(Gazette.dayRecords(s.state, r.day), s.state.heroes.values.associate { it.id.value to it.fullName }, r.visits, r.ledger, r.field) }
                    EditionBody(edition)
                    if (r.replays.isNotEmpty()) {
                        PaperRuleLine(top = Space.md, bottom = Space.sm)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("FROM THE FIELD", style = MaterialTheme.typography.labelMedium, color = PaperInkMuted, modifier = Modifier.semantics { heading() })
                            if (shown < totalSteps) TextButton(onClick = { shown = totalSteps }, modifier = Modifier.testTag("report_skip"), colors = ButtonDefaults.textButtonColors(contentColor = PaperInk)) { Text("Skip") }
                        }
                        var step = 0
                        r.replays.forEach { replay ->
                            var open by remember(r.commandId, replay.title) { mutableStateOf(false) }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(replay.title, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(top = Space.sm))
                                TextButton(onClick = { open = !open }, colors = ButtonDefaults.textButtonColors(contentColor = PaperInkMuted)) {
                                    Text(if (open) "Hide rounds" else "${replay.rounds.size} rounds")
                                }
                            }
                            replay.rounds.forEachIndexed { i, round ->
                                if (open && step < shown) Text("${i + 1}. ${round.attacker} ${round.note} (${round.damage})", style = MaterialTheme.typography.bodySmall, color = PaperInkMuted, modifier = Modifier.padding(top = 2.dp))
                                step += 1
                            }
                            if (step < shown) Text(replay.outcome, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Space.xs))
                            step += 1
                        }
                    }
                    if (r.defeated) {
                        PaperRuleLine(top = Space.md, bottom = Space.sm)
                        Text("THE FORGE HAS FALLEN", fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                Button(onClick = vm::dismissReport, modifier = Modifier.fillMaxWidth().padding(top = Space.md).heightIn(min = 52.dp).testTag("report_close")) {
                    Text(if (r.defeated) "See the legacy" else "Begin day ${s.state.day}", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** One day's edition below its masthead: the lede, the tally line, then each section as a bulleted list. */
@Composable
fun EditionBody(edition: Gazette.Edition, modifier: Modifier = Modifier) {
    val muted = LocalContentColor.current.copy(alpha = 0.7f)
    Column(modifier) {
        if (edition.lede.isEmpty() && edition.sections.isEmpty()) Text("A quiet day in Emberfall.", fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodyMedium)
        edition.lede.forEachIndexed { i, h ->
            Text(h, fontFamily = FontFamily.Serif, style = if (i == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Space.sm))
        }
        if (edition.tally.isNotEmpty()) Text(edition.tally.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = muted)
        edition.sections.forEach { section ->
            Text(section.title.uppercase(), style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.padding(top = Space.md, bottom = 2.dp).semantics { heading() })
            section.lines.forEach { line ->
                Row(Modifier.padding(top = Space.xs)) {
                    Text("•", fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(14.dp))
                    Text(line, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PaperRuleLine(top: Dp, bottom: Dp) {
    HorizontalDivider(color = PaperRule, thickness = 1.dp, modifier = Modifier.padding(top = top, bottom = bottom))
}

@Composable
fun BlessingDialog(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = {},
        title = { Text("The town offers a blessing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                s.state.pendingBlessingOffer.forEach { id ->
                    val b = content.blessing(id)
                    OutlinedButton(onClick = { vm.dispatch(Command.ChooseBlessing(id)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("blessing_${id.value}")) {
                        Sprites.blessing(id)?.let { PixelImage(it, 32.dp, description = null); Spacer(Modifier.width(12.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = MaterialTheme.typography.titleSmall)
                            Text(b.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::dismissBlessingOffer, modifier = Modifier.testTag("blessing_later")) { Text("Decide later") } },
    )
}

@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = onDismiss,
        title = { Text("The forge says") },
        text = { Text(message) },
        confirmButton = { Button(onClick = onDismiss, modifier = Modifier.testTag("error_ok")) { Text("Alright") } },
    )
}

/** Poses for the siege diorama derive from the replay step being shown; the stage never changes outcomes. */
@Composable
private fun ReplayStage(replay: CombatReplay, shown: Int, s: UiState.Playing, vm: GameViewModel, reducedMotion: Boolean) {
    val current = (shown - 1).coerceIn(-1, replay.rounds.size)  // -1 = nothing yet, size = outcome shown
    val currentRound = replay.rounds.getOrNull(current)
    val faction = vm.engine.content.factions.firstOrNull { f -> replay.rounds.any { it.attacker == f.siegeName || it.defender == f.siegeName } }
    val heroes = replay.rounds.mapNotNull { round -> s.state.heroes.values.firstOrNull { it.fullName == round.attacker } }.distinctBy { it.id }.take(3)
    val raidersAttack = currentRound != null && faction != null && currentRound.attacker == faction.siegeName
    val lost = current >= replay.rounds.size && replay.outcome != "Town held"
    val defenders = heroes.map { h ->
        StageActor({ pose, tick -> Sprites.heroFrame(h.classId, pose, tick) }, if (currentRound?.attacker == h.fullName) Sprites.Pose.ATTACK else Sprites.Pose.IDLE, flipped = false)
    }
    val raiders = faction?.let { f ->
        listOf(false, true).mapIndexed { i, elite ->
            val pose = when {
                raidersAttack -> Sprites.Pose.ATTACK
                currentRound != null && heroes.any { it.fullName == currentRound.attacker } && i == 0 -> Sprites.Pose.HIT
                else -> Sprites.Pose.IDLE
            }
            StageActor({ p, tick -> Sprites.monsterFrame(f.id, elite, p, tick) }, pose, flipped = false)
        }
    } ?: emptyList()
    SiegeStage(defenders, raiders, damaged = lost, reducedMotion = reducedMotion, modifier = Modifier.padding(bottom = Space.md))
}
