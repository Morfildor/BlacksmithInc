package com.example.blacksmithproject.ui.replay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.EffectKind
import com.example.blacksmithproject.ui.FightReport
import com.example.blacksmithproject.ui.InlineActionButton
import com.example.blacksmithproject.ui.PixelImage
import com.example.blacksmithproject.ui.PrimaryActionButton
import com.example.blacksmithproject.ui.SecondaryActionButton
import com.example.blacksmithproject.ui.fightOutcomeLine
import com.example.blacksmithproject.ui.shopday.SiegeOutcomeUi
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.BronzeLine
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.ForgePanel
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.combat.Contribution
import com.tinyblacksmith.core.combat.FightAccount
import com.tinyblacksmith.core.combat.FightResult
import com.tinyblacksmith.core.combat.Share
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.SiegeVerdict
import com.tinyblacksmith.core.model.WeaponId

/**
 * The siege's report, after the replay or instead of it: how it ended and what it cost, then each defender's part in
 * the record's own numbers (damage dealt, damage their Guard took, effective healing), each part split by the rule
 * that did it with every point counted once (`FightAccounting`), at most two observations of the record, the
 * discoveries the night made, and the whole fight as text. Without a replay ([replayable] false) it says so and is
 * the summary the save holds.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SiegeReport(
    fight: FightResult, replayable: Boolean, account: FightAccount?, siege: SiegeOutcomeUi, state: GameState, faces: Map<String, ReplayFace>,
    onWatch: (() -> Unit)?, onOpenHero: (HeroId) -> Unit, onOpenBlade: (WeaponId) -> Unit, onOpenGazette: () -> Unit, onClose: () -> Unit,
) {
    var log by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.md).testTag("replay_report"), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text("SIEGE OF EMBERFALL · DAY ${siege.day}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = CreamMuted)
        val kind = when { siege.verdict == SiegeVerdict.HELD_AT_A_COST -> EffectKind.NEUTRAL; siege.held -> EffectKind.BUFF; else -> EffectKind.FLAW }
        Text(siege.outcome, style = MaterialTheme.typography.headlineMedium, color = kind.color, modifier = Modifier.semantics { heading() }.testTag("replay_report_outcome"))
        Text("On the wall: ${fightOutcomeLine(fight).lowercase()}", style = MaterialTheme.typography.bodyMedium)

        // What it cost, as recorded: the forge, and the defenders who went down or fell.
        val downed = fight.actors.filter { it.side == Side.PARTY && !it.summoned && it.downed }
        val fell = downed.filter { HeroId(it.key) in siege.fell }
        val hurt = downed.filter { HeroId(it.key) !in siege.fell }
        Column(Modifier.fillMaxWidth().testTag("replay_report_cost")) {
            ReportRow("Forge damage", siege.forgeDamage?.let { if (it > 0) "${EffectKind.FLAW.sign}$it" else "None" } ?: "None recorded")
            ReportRow("Forge health now", "${siege.forgeHealthNow}")
            ReportRow("Fell", fell.joinToString(", ") { it.name }.ifEmpty { "Nobody" })
            ReportRow("Went down, injured", hurt.joinToString(", ") { it.name }.ifEmpty { "Nobody" })
        }

        if (!replayable) {
            // An older record: no replay, and no per-rule account. The fight as the save keeps it.
            Text("This siege was recorded before replays, so it can be read but not watched.", style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.testTag("replay_report_fallback"))
            FightReport(fight, expanded = log, onToggle = { log = !log })
        } else {
            account?.let { a ->
                Text("THE DEFENDERS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = CreamMuted, modifier = Modifier.padding(top = Space.sm))
                Text("Damage dealt, damage their Guard took for the wall, and health mended. Each point counts once.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                a.members.forEach { m -> ContributionCard(m, faces[m.key], state, onOpenHero, onOpenBlade) }
                if (a.shared.total > 0) {
                    Text(
                        "Shared: ${parts(a.shared).joinToString(", ")}. From states or Guard more than one hand put on, so the record cannot give it to one defender.",
                        style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.testTag("replay_report_shared"),
                    )
                }
                if (a.observations.isNotEmpty()) {
                    Text("WORTH KNOWING", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = CreamMuted, modifier = Modifier.padding(top = Space.sm))
                    Column(Modifier.testTag("replay_report_observations")) { a.observations.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) } }
                }
            }
            if (siege.discoveries.isNotEmpty()) {
                Text("SEEN IN THIS FIGHT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = CreamMuted, modifier = Modifier.padding(top = Space.sm))
                siege.discoveries.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
                Text("Written into the Notebook.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }
            // The whole log is there, under its button: secondary to the replay and the numbers above.
            FightReport(fight, Modifier.padding(top = Space.sm), expanded = log, onToggle = { log = !log }, highlights = 0)
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            onWatch?.let { SecondaryActionButton("Watch again", it, Modifier.testTag("replay_watch_again")) }
            InlineActionButton("Read the Gazette", onOpenGazette, Modifier.testTag("replay_gazette"))
            PrimaryActionButton("Close", onClose, Modifier.testTag("replay_report_close"))
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

/** "24 damage", "8 blocked", "6 healed": only the parts that are not zero. */
private fun parts(s: Share): List<String> = listOfNotNull(
    "${s.damage} damage".takeIf { s.damage > 0 }, "${s.prevented} blocked".takeIf { s.prevented > 0 }, "${s.healed} healed".takeIf { s.healed > 0 },
)

/**
 * One defender: face, name and blade, the three totals in words and numbers, and on a tap the same totals split by
 * rule, a blade's rules marked as the blade's, with how often each fired and what it left on the enemy.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContributionCard(m: Contribution, face: ReplayFace?, state: GameState, onOpenHero: (HeroId) -> Unit, onOpenBlade: (WeaponId) -> Unit) {
    var open by rememberSaveable(m.key) { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    Column(
        Modifier.fillMaxWidth().background(ForgePanel, shape).border(1.dp, if (open) Gold else BronzeLine, shape)
            .clickable(onClickLabel = if (open) "Hide the rules" else "Show each rule", role = Role.Button) { open = !open }
            .semantics { stateDescription = if (open) "Rules shown" else "Rules hidden" }
            .padding(Space.sm).testTag("replay_contribution_${m.key}"),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            face?.res?.let { PixelImage(it, 40.dp, description = null) }
            Column(Modifier.weight(1f)) {
                Text(m.name + if (m.downed) " · went down" else "", style = MaterialTheme.typography.titleSmall, color = Gold)
                Text(m.weapon ?: "No weapon", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Total("Damage", m.damage); Total("Blocked", m.prevented); Total("Healed", m.healed)
        }
        if (open) {
            Column(Modifier.fillMaxWidth().padding(top = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (m.shares.isEmpty()) Text("Nothing the record can give to ${m.name}.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                m.shares.forEach { s ->
                    Column(Modifier.fillMaxWidth().border(1.dp, BronzeDeep, shape).padding(horizontal = Space.sm, vertical = Space.xs).testTag("replay_share_${m.key}_${s.ruleId ?: "own"}")) {
                        Text((if (s.weapon) "Weapon · " else "") + s.name, style = MaterialTheme.typography.labelLarge, color = if (s.weapon) Gold else Cream)
                        val line = parts(s) + listOfNotNull(
                            "${s.applied} ${if (s.applied == 1) "stack" else "stacks"} on foes".takeIf { s.applied > 0 },
                            "fired ${s.times} ${if (s.times == 1) "time" else "times"}".takeIf { s.times > 0 },
                        )
                        Text(line.joinToString(" · ").ifEmpty { "Fired, with nothing to count" }, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                val blade = m.weaponId?.let { WeaponId(it) }?.takeIf { state.weapons.containsKey(it) }
                blade?.let { SecondaryActionButton("Weapon record", { onOpenBlade(it) }, Modifier.testTag("replay_report_blade_${m.key}")) }
                if (state.heroes.containsKey(HeroId(m.key))) SecondaryActionButton("Hero", { onOpenHero(HeroId(m.key)) }, Modifier.testTag("replay_report_hero_${m.key}"))
            }
        }
    }
}

@Composable
private fun Total(label: String, value: Int) {
    Column {
        Text("$value", style = MaterialTheme.typography.titleMedium, color = if (value > 0) Cream else CreamMuted)
        Text(label, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
    }
}
