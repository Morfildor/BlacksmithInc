package com.example.blacksmithproject.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.tinyblacksmith.core.model.CombatReplay
import com.tinyblacksmith.core.model.Rarity
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.PaperInk
import com.example.blacksmithproject.ui.theme.PaperInkMuted
import com.example.blacksmithproject.ui.theme.PaperRule
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.model.DayResolution
import com.tinyblacksmith.core.model.WeaponId
import kotlinx.coroutines.delay

/** Item card after an atomic forge. The reveal is decorative; the weapon already exists in the saved state. */
@Composable
fun ForgeResultDialog(s: UiState.Playing, weaponId: WeaponId, vm: GameViewModel, reducedMotion: Boolean) {
    val w = s.state.weapons[weaponId] ?: run { vm.dismissReveal(); return }
    val content = vm.engine.content
    val reveal by animateFloatAsState(targetValue = 1f, animationSpec = tween(if (reducedMotion) 0 else 600), label = "reveal")
    AlertDialog(
        onDismissRequest = vm::dismissReveal,
        title = { Text(w.name, modifier = Modifier.graphicsLayer { alpha = reveal }) },
        text = {
            Column(Modifier.graphicsLayer { alpha = reveal }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp)) {
                        WeaponSprite(w, size = 72.dp)
                        if (w.signatureId != null || w.rarity >= Rarity.EPIC) MilestoneBurst(72.dp, reducedMotion)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(Labels.weaponSummary(w, content))
                }
                Text("${content.family(w.familyId).name} of ${content.material(w.coreId).name} and ${content.material(w.augmentId).name}", style = MaterialTheme.typography.bodySmall)
                w.affixes.forEach { Text("+ ${content.affix(it).name}: ${content.affix(it).description}", style = MaterialTheme.typography.bodySmall) }
                w.flaws.forEach { Text("− ${content.affix(it).name}: ${content.affix(it).description}", style = MaterialTheme.typography.bodySmall) }
                Text("Suggested price ${vm.engine.suggestedPrice(w)} gold; set your own in the Market.", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = { vm.dispatch(Command.ToggleShelf(w.id, true, vm.engine.suggestedPrice(w))); vm.dismissReveal() }, enabled = w.isInStorage) { Text("List at ${vm.engine.suggestedPrice(w)}") }
        },
        dismissButton = { OutlinedButton(onClick = vm::dismissReveal) { Text("Store") } },
    )
}

/**
 * The Gazette as a newspaper: masthead, rules, headlines and a step-by-step text replay. Every line derives from
 * real event records; stepping is purely presentational and skippable (GDD 11).
 */
@Composable
fun DayReportDialog(s: UiState.Playing, r: DayResolution, vm: GameViewModel, reducedMotion: Boolean) {
    val totalSteps = r.replays.sumOf { it.rounds.size + 1 }
    var shown by remember(r.commandId) { mutableIntStateOf(if (reducedMotion) totalSteps else 0) }
    LaunchedEffect(r.commandId, reducedMotion) {
        if (reducedMotion) { shown = totalSteps; return@LaunchedEffect }
        while (shown < totalSteps) { delay(700); shown += 1 }
    }
    AlertDialog(
        onDismissRequest = vm::dismissReport,
        containerColor = Color.Transparent,
        titleContentColor = PaperInk,
        textContentColor = PaperInk,
        modifier = Modifier.clip(AlertDialogDefaults.shape).paperBackground(),
        title = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    Gazette.masthead(r.day),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                PaperRuleLine(top = 6.dp, bottom = 2.dp)
                PaperRuleLine(top = 0.dp, bottom = 0.dp)
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                r.replays.firstOrNull()?.let { replay -> ReplayStage(replay, shown, s, vm, reducedMotion) }
                if (r.headlines.isEmpty()) Text("A quiet day in Emberfall.", fontFamily = FontFamily.Serif)
                r.headlines.forEachIndexed { i, h ->
                    Text(
                        h,
                        fontFamily = FontFamily.Serif,
                        fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                        style = if (i == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 3.dp),
                    )
                }
                if (r.replays.isNotEmpty()) {
                    PaperRuleLine(top = 8.dp, bottom = 4.dp)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("FROM THE FIELD", style = MaterialTheme.typography.labelMedium, color = PaperInkMuted, modifier = Modifier.semantics { heading() })
                        if (shown < totalSteps) TextButton(onClick = { shown = totalSteps }, colors = ButtonDefaults.textButtonColors(contentColor = PaperInk)) { Text("Skip") }
                    }
                    var step = 0
                    r.replays.forEach { replay ->
                        Text(replay.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                        replay.rounds.forEachIndexed { i, round ->
                            if (step < shown) Text("${i + 1}. ${round.attacker} ${round.note} (${round.damage})", style = MaterialTheme.typography.bodySmall, color = PaperInkMuted)
                            step += 1
                        }
                        if (step < shown) Text(replay.outcome, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        step += 1
                    }
                }
                if (r.defeated) {
                    PaperRuleLine(top = 8.dp, bottom = 4.dp)
                    Text("THE FORGE HAS FALLEN", fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { Button(onClick = vm::dismissReport) { Text(if (r.defeated) "See the legacy" else "Begin day ${s.state.day}") } },
    )
}

@Composable
private fun PaperRuleLine(top: Dp, bottom: Dp) {
    HorizontalDivider(color = PaperRule, thickness = 1.dp, modifier = Modifier.padding(top = top, bottom = bottom))
}

@Composable
fun BlessingDialog(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    AlertDialog(
        onDismissRequest = {},
        title = { Text("The town offers a blessing") },
        text = {
            Column {
                s.state.pendingBlessingOffer.forEach { id ->
                    val b = content.blessing(id)
                    OutlinedButton(onClick = { vm.dispatch(Command.ChooseBlessing(id)) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Sprites.blessing(id)?.let { PixelImage(it, 32.dp, description = null); Spacer(Modifier.width(10.dp)) }
                        Text("${b.name} — ${b.description}", modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::dismissBlessingOffer) { Text("Decide later") } },
    )
}

@Composable
fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("The forge says") }, text = { Text(message) }, confirmButton = { Button(onClick = onDismiss) { Text("Alright") } })
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
    SiegeStage(defenders, raiders, damaged = lost, reducedMotion = reducedMotion, modifier = Modifier.padding(bottom = 8.dp))
}
