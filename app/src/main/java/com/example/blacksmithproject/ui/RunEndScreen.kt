package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Space

/**
 * Defeat flow (GDD 3.2): summary -> claim-once legacy -> spend on permanent upgrades -> next era.
 * One primary action at a time: Claim until it is claimed, then Begin era. Each is one serialized operation, so all
 * three kinds of button are disabled while one is being saved; a failed save is shown by the app-wide dialog.
 */
@Composable
fun RunEndScreen(s: UiState.RunEnded, vm: GameViewModel) {
    val content = vm.engine.content
    val end = s.runEnd
    val nextEra = maxOf(s.legacy.nextEra, end.era + 1)
    val haptics = LocalHaptics.current
    LaunchedEffect(s.lastError) { if (s.lastError != null) haptics.play(Moment.REJECTED) }
    // The next step stays pinned under the scrolling summary, so it never hides below the upgrade list.
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.md, vertical = Space.lg)) {
            Text("The forge has fallen", style = MaterialTheme.typography.titleLarge)
            Text(end.cause, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs))
            Secondary("Era ${end.era} lasted ${end.daysSurvived} days.", Modifier.padding(top = Space.xs))

            Card(
                Modifier.fillMaxWidth().padding(top = Space.md),
                colors = CardDefaults.cardColors(containerColor = if (s.claimed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(Space.md)) {
                    Text(if (s.claimed) "Legacy claimed ✓" else "Legacy reward", style = MaterialTheme.typography.titleMedium)
                    Text("${end.totalPoints} points", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.xs))
                    Secondary("Baseline ${end.basePoints} · survival ${end.survivalPoints} · discoveries ${end.discoveryPoints} · milestones ${end.milestonePoints}")
                    end.milestones.forEach { Secondary("• ${milestoneLabel(it)}") }
                    Secondary(
                        if (s.claimed) "This era's reward is banked. Each era can be claimed only once." else "Claim once to bank these points for good. They survive every future era.",
                        Modifier.padding(top = Space.sm),
                    )
                    if (!s.claimed) {
                        Button(onClick = vm::claimLegacy, enabled = !s.busy, modifier = Modifier.fillMaxWidth().padding(top = Space.sm).heightIn(min = 52.dp).testTag("run_claim")) {
                            Text("Claim ${end.totalPoints} legacy points", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    s.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs)) }
                }
            }

            if (end.legends.isNotEmpty()) {
                SectionTitle("Blades remembered")
                end.legends.forEach { Text("${it.title} · ${it.kills} kills", style = MaterialTheme.typography.bodyMedium) }
            }
            end.lineage?.let { SectionTitle("Lineage"); Text("${it.heroName} ${it.deed}; their line may return.", style = MaterialTheme.typography.bodyMedium) }

            SectionTitle("Permanent upgrades · ${s.legacy.points} points")
            if (!s.claimed) Secondary("Claim your legacy first to spend points.")
            content.upgrades.forEach { u ->
                val level = s.legacy.upgradeLevel(u.id)
                val cost = u.costPerLevel.getOrNull(level)
                Card(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(u.name, style = MaterialTheme.typography.titleSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LevelDots(level, u.maxLevel)
                                Spacer(Modifier.width(6.dp))
                                Secondary("level $level of ${u.maxLevel}")
                            }
                            Secondary(u.description)
                        }
                        OutlinedButton(
                            enabled = s.claimed && cost != null && s.legacy.points >= cost && !s.busy,
                            onClick = { vm.buyUpgrade(u.id) },
                            modifier = Modifier.padding(start = Space.sm).heightIn(min = 48.dp).semantics { contentDescription = cost?.let { "Buy ${u.name} level ${level + 1} for $it points" } ?: "${u.name} at maximum level" },
                        ) { Text(cost?.let { "$it pts" } ?: "Max") }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm)) {
            if (s.claimed) {
                Button(onClick = vm::beginNextEra, enabled = !s.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("run_begin_era")) {
                    Text("Begin era $nextEra", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                FilledTonalButton(onClick = vm::beginNextEra, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("Begin era $nextEra", style = MaterialTheme.typography.titleMedium)
                }
                Secondary("Claim the legacy above to begin the next era.", Modifier.padding(top = Space.xs))
            }
        }
    }
}

private fun milestoneLabel(key: String) = when (key) {
    "FIRST_SALE" -> "First sale"
    "SIEGE_SURVIVED" -> "Survived a siege"
    "CHAMPION_ARMED" -> "A champion defended the town with your weapon"
    "EPIC_FORGED" -> "Forged an epic weapon"
    "LEGENDARY_FORGED" -> "Forged a legendary weapon"
    "HERO_LEVEL_5" -> "A hero reached level 5"
    "WEAPON_FIVE_KILLS" -> "A weapon earned a title"
    else -> key
}
