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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState

/** Defeat flow (GDD 3.2): summary -> claim-once legacy -> spend on permanent upgrades -> next era. */
@Composable
fun RunEndScreen(s: UiState.RunEnded, vm: GameViewModel) {
    val content = vm.engine.content
    val end = s.runEnd
    val nextEra = maxOf(s.legacy.nextEra, end.era + 1)
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("The forge has fallen", style = MaterialTheme.typography.headlineMedium)
        Text(end.cause, modifier = Modifier.padding(top = 4.dp))
        Text("Era ${end.era} lasted ${end.daysSurvived} days.", modifier = Modifier.padding(top = 4.dp))

        Card(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = if (s.claimed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(if (s.claimed) "Legacy claimed ✓" else "Legacy reward", style = MaterialTheme.typography.titleMedium)
                Text("Baseline ${end.basePoints} + survival ${end.survivalPoints} + discoveries ${end.discoveryPoints} + milestones ${end.milestonePoints} = ${end.totalPoints} points", modifier = Modifier.padding(top = 4.dp))
                end.milestones.forEach { Text("• ${milestoneLabel(it)}", style = MaterialTheme.typography.bodySmall) }
                Text(
                    if (s.claimed) "This era's reward is banked. Each era can be claimed only once." else "Claim once to bank these points for good. They survive every future era.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(onClick = vm::claimLegacy, enabled = !s.claimed, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 10.dp)) {
                    Text(if (s.claimed) "Legacy claimed" else "Claim ${end.totalPoints} legacy points", style = MaterialTheme.typography.titleMedium)
                }
                s.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        if (end.legends.isNotEmpty()) {
            SectionTitle("Blades remembered")
            end.legends.forEach { Text("${it.title} — ${it.kills} kills", style = MaterialTheme.typography.bodySmall) }
        }
        end.lineage?.let { SectionTitle("Lineage"); Text("${it.heroName} ${it.deed}; their line may return.", style = MaterialTheme.typography.bodySmall) }

        SectionTitle("Permanent upgrades · ${s.legacy.points} points")
        if (!s.claimed) Text("Claim your legacy first to spend points.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content.upgrades.forEach { u ->
            val level = s.legacy.upgradeLevel(u.id)
            val cost = u.costPerLevel.getOrNull(level)
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(u.name, style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LevelDots(level, u.maxLevel)
                            Spacer(Modifier.width(6.dp))
                            Text("level $level of ${u.maxLevel}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(u.description, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(enabled = s.claimed && cost != null && s.legacy.points >= cost, onClick = { vm.buyUpgrade(u.id) }, modifier = Modifier.padding(start = 8.dp)) {
                        Text(cost?.let { "$it pts" } ?: "Max")
                    }
                }
            }
        }

        Button(onClick = vm::beginNextEra, enabled = s.claimed, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 20.dp)) {
            Text("Begin era $nextEra", style = MaterialTheme.typography.titleMedium)
        }
        if (!s.claimed) Text("Claim the legacy above to begin the next era.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
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
