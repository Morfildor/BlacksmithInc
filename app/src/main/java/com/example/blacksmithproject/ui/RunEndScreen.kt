package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.BuffGreen
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.legacy.Legacy

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
    // A guild run the smith closed after securing the charter: an ending chosen, not a fall.
    val retired = s.run.guild?.retired == true
    val haptics = LocalHaptics.current
    LaunchedEffect(s.lastError) { if (s.lastError != null) haptics.play(Moment.REJECTED) }
    // The next step stays pinned under the scrolling summary, so it never hides below the upgrade list.
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.md, vertical = Space.lg)) {
            Text(if (retired) "The guild has retired" else "The forge has fallen", style = MaterialTheme.typography.headlineMedium, color = Gold, modifier = Modifier.testTag("run_end_title"))
            Text(end.cause, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs))
            Secondary("Era ${end.era} lasted ${end.daysSurvived} days.", Modifier.padding(top = Space.xs))

            // V3: the reward is one large number, no plate; how it adds up is on its card. The claim is the pinned action below.
            Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}.testTag("run_points")) {
                    Text(if (s.claimed) "Legacy claimed ✓" else "Legacy reward", style = MaterialTheme.typography.labelLarge, color = if (s.claimed) BuffGreen else CreamMuted)
                    Text("${end.totalPoints} points", style = MaterialTheme.typography.headlineMedium, color = Gold)
                    Secondary(if (s.claimed) "Reward claimed. These points are yours to keep." else "Claim these points to keep them for future eras.")
                }
                InfoButton("how the points add up", { runPointsTip(end, ::milestoneLabel) }, tag = "run_points_info")
            }
            s.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs)) }

            if (end.legends.isNotEmpty()) {
                SectionTitle("Hall of Legends")
                end.legends.forEach { Text("${it.title} · ${it.kills} kills", style = MaterialTheme.typography.bodyMedium) }
            }
            end.lineage?.let { SectionTitle("Lineage"); Text("${it.heroName} ${it.deed}. Their descendants may return.", style = MaterialTheme.typography.bodyMedium) }

            SectionTitle("Legacy perks · ${s.legacy.points} points")
            if (!s.claimed) Secondary("Claim your legacy first to spend points. Then begin era $nextEra.")
            content.upgrades.forEachIndexed { i, u ->
                val level = s.legacy.upgradeLevel(u.id)
                val cost = u.costPerLevel.getOrNull(level)
                // One row an upgrade, divided by hairlines: a list of things to buy, not a stack of plates. What the next level
                // gives, in the engine's own numbers, is on the upgrade's card.
                if (i > 0) HorizontalDivider(color = BronzeDeep)
                Column(Modifier.fillMaxWidth().padding(vertical = Space.sm)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                            Text(u.name, style = MaterialTheme.typography.titleSmall, color = Cream)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LevelDots(level, u.maxLevel)
                                Spacer(Modifier.width(6.dp))
                                Secondary("level $level of ${u.maxLevel}")
                            }
                            Secondary(u.description)
                        }
                        InfoButton(u.name, { upgradeTip(u, level, Legacy.preview(u.id, level + 1, content, vm.engine.config)?.text, cost, tag = "run_upgrade_tip") }, tag = "run_upgrade_${u.id.value}_info")
                        // A greyed button says why: nothing is bought before the claim, or the points are short.
                        val blocked = when { cost == null -> null; !s.claimed -> "claim the legacy first"; s.legacy.points < cost -> "it costs $cost points and you have ${s.legacy.points}"; else -> null }
                        SecondaryActionButton(
                            cost?.let { "$it points" } ?: "Max", { vm.buyUpgrade(u.id) },
                            Modifier.padding(start = Space.sm).semantics {
                                contentDescription = when { cost == null -> "${u.name} at maximum level"; blocked != null -> "Buy ${u.name} level ${level + 1}, unavailable: $blocked"; else -> "Buy ${u.name} level ${level + 1} for $cost points" }
                            },
                            enabled = cost != null && blocked == null && !s.busy,
                        )
                    }
                }
            }
            // What the next era begins under: the last thing in the scroll, so the pinned button below keeps the list its height.
            // The button starts the era with what is chosen here (the first charter unless changed).
            CharterPicker(vm, Modifier.padding(top = Space.md))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm)) {
            if (!s.claimed) PrimaryActionButton("Claim ${end.totalPoints} legacy points", vm::claimLegacy, Modifier.fillMaxWidth().testTag("run_claim"), enabled = !s.busy)
            else if (s.claimed) {
                PrimaryActionButton("Begin era $nextEra", vm::beginNextEra, Modifier.fillMaxWidth().testTag("run_begin_era"), enabled = !s.busy)
            }
        }
    }
}

private fun milestoneLabel(key: String) = when (key) {
    "FIRST_SALE" -> "First sale"
    "SIEGE_SURVIVED" -> "Survived a siege"
    "CHAMPION_ARMED" -> "A defender held the wall with your weapon"
    "EPIC_FORGED" -> "Forged an Epic weapon"
    "LEGENDARY_FORGED" -> "Forged a Legendary weapon"
    "HERO_LEVEL_5" -> "A hero reached level 5"
    "WEAPON_FIVE_KILLS" -> "A weapon earned a title"
    "CHARTER_SECURED" -> "Charter secured"
    "LEGEND_EARNED" -> "A weapon became Legendary by its deeds"
    else -> key
}
