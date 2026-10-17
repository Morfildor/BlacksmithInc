package com.example.blacksmithproject.ui.replay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blacksmithproject.ui.EffectKind
import com.example.blacksmithproject.ui.Fact
import com.example.blacksmithproject.ui.FactLine
import com.example.blacksmithproject.ui.FactTone
import com.example.blacksmithproject.ui.FightFullBody
import com.example.blacksmithproject.ui.FightReport
import com.example.blacksmithproject.ui.FullReportHead
import com.example.blacksmithproject.ui.HeroFace
import com.example.blacksmithproject.ui.InlineActionButton
import com.example.blacksmithproject.ui.PrimaryActionButton
import com.example.blacksmithproject.ui.SecondaryActionButton
import com.example.blacksmithproject.ui.fightOutcomeLine
import com.example.blacksmithproject.ui.shopday.SiegeOutcomeUi
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.combat.Contribution
import com.tinyblacksmith.core.combat.FightAccount
import com.tinyblacksmith.core.combat.FightResult
import com.tinyblacksmith.core.combat.Share
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.SiegeVerdict
import com.tinyblacksmith.core.model.WeaponId

/**
 * The siege's report, after the replay or instead of it, in two levels. The lead: whose siege it was, how it ended and
 * what it cost, the one contribution the record counted most plainly and the siege's own notes, each defender as a row
 * (face, name, weapon, whether they went down, and their three totals in the record's own numbers), what the night
 * showed for the first time (written into the Notebook), and at most two observations of the record.
 * Then one "Full report" row that opens the technical part in place: each defender's totals split by the rule that did
 * them, every point counted once (`FightAccounting`), what the record cannot give to one defender, both sides and the
 * whole fight as text. Without a replay ([replayable] false) it says so and is the summary the save holds.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SiegeReport(
    fight: FightResult, replayable: Boolean, account: FightAccount?, siege: SiegeOutcomeUi, state: GameState, faces: Map<String, ReplayFace>,
    onWatch: (() -> Unit)?, onOpenHero: (HeroId) -> Unit, onOpenBlade: (WeaponId) -> Unit, onOpenGazette: () -> Unit, onClose: () -> Unit,
) {
    var log by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Space.md).testTag("replay_report"), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        val lead = reportLead(siege)
        Text(lead.eyebrow, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = CreamMuted, modifier = Modifier.testTag("replay_report_eyebrow"))
        val kind = when { siege.verdict == SiegeVerdict.HELD_AT_A_COST -> EffectKind.NEUTRAL; siege.held -> EffectKind.BUFF; else -> EffectKind.FLAW }
        Text(siege.outcome, style = MaterialTheme.typography.headlineMedium, color = kind.color, modifier = Modifier.semantics { heading() }.testTag("replay_report_outcome"))
        Text("On the wall: ${fightOutcomeLine(fight).lowercase()}", style = MaterialTheme.typography.bodyMedium)

        // The costs the card the report was opened from shows, from the same fields.
        Column(Modifier.fillMaxWidth().testTag("replay_report_cost")) {
            siege.consequences.forEach { ReportRow(it.label, it.value) }
        }
        // What the record counted most plainly, then what the siege's own record says decided it. Never a verdict on a person.
        lead.contribution?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.testTag("replay_report_contribution")) }
        if (lead.notes.isNotEmpty()) Column(Modifier.testTag("replay_report_notes")) {
            lead.notes.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = CreamMuted) }
        }

        if (!replayable) {
            // An older record: no replay, and no per-rule account. The fight as the save keeps it.
            Text("This siege was recorded before replays, so it can be read but not watched.", style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.testTag("replay_report_fallback"))
            FightReport(fight, expanded = log, onToggle = { log = !log })
        } else {
            account?.let { a ->
                ReportLabel("The defenders")
                Column {
                    a.members.forEachIndexed { i, m ->
                        if (i > 0) HorizontalDivider(color = BronzeDeep)
                        DefenderRow(m, faces[m.key])
                    }
                }
            }
            // What the night gave the smith to keep: paths of the rules seen for the first time.
            if (siege.discoveries.isNotEmpty()) Column(Modifier.testTag("replay_report_discoveries")) {
                ReportLabel("Seen in this fight")
                siege.discoveries.forEach { Text("${EffectKind.BUFF.sign} $it", style = MaterialTheme.typography.bodyMedium, color = EffectKind.BUFF.color) }
                Text("Written into the Notebook.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }
            account?.let { a ->
                if (a.observations.isNotEmpty()) {
                    ReportLabel("Worth knowing")
                    Column(Modifier.testTag("replay_report_observations")) { a.observations.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) } }
                }
            }
            // The technical part, one tap away and opened in place: never mixed into the lead above.
            FullReportHead(log, { log = !log }, summary = "Each defender's effects, both sides and every event", tag = "replay_report_full")
            if (log) Column(Modifier.fillMaxWidth().testTag("replay_report_full_body"), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                account?.let { a ->
                    Text("Damage dealt to enemies, damage blocked by Armor each defender gave, and health each defender healed. Each point is counted once.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                    a.members.forEach { m -> DefenderAccount(m, state, onOpenHero, onOpenBlade) }
                    if (a.shared.total > 0) {
                        Text(
                            "Shared: ${parts(a.shared).joinToString(", ")}. It came from statuses or Armor that more than one defender gave, so it is not credited to one defender.",
                            style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.testTag("replay_report_shared"),
                        )
                    }
                }
                FightFullBody(fight)
            }
        }
        FlowRow(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            onWatch?.let { SecondaryActionButton("Watch again", it, Modifier.testTag("replay_watch_again")) }
            InlineActionButton("Read the Gazette", onOpenGazette, Modifier.testTag("replay_gazette"))
            PrimaryActionButton("Close", onClose, Modifier.testTag("replay_report_close"))
        }
    }
}

/** The report's lead, from the outcome card's own fields: whose siege it was, the one recorded contribution, and the siege's notes. */
internal data class ReportLead(val eyebrow: String, val contribution: String?, val notes: List<String>)

internal fun reportLead(siege: SiegeOutcomeUi): ReportLead =
    ReportLead(listOfNotNull("Siege of Emberfall", "day ${siege.day}", siege.foe).joinToString(" · ").uppercase(), siege.contribution, siege.notes)

@Composable
private fun ReportLabel(text: String) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = CreamMuted, modifier = Modifier.padding(top = Space.sm).semantics { heading() })

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
 * One defender in the lead: face, name and weapon, "went down" as a cost with its sign, and the three totals as one line
 * of facts. What each rule of theirs did is in the full report.
 */
@Composable
private fun DefenderRow(m: Contribution, face: ReplayFace?) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.sm).semantics(mergeDescendants = true) {}.testTag("replay_contribution_${m.key}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        face?.res?.let { HeroFace(it, 40.dp) }
        Column(Modifier.weight(1f)) {
            Text(m.name, style = MaterialTheme.typography.titleSmall, color = Cream)
            Text(m.weapon ?: "No weapon", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            FactLine(
                listOfNotNull(
                    Fact("went down", tone = FactTone.WARN).takeIf { m.downed },
                    Fact("${m.damage}", "Damage"), Fact("${m.prevented}", "Blocked"), Fact("${m.healed}", "Healed"),
                ),
                Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * One defender in the full report: their totals split by rule (a weapon's rules marked as the weapon's), how often each
 * fired and what it left on the enemy, as hairline rows; then the ways to the weapon's record and the hero.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefenderAccount(m: Contribution, state: GameState, onOpenHero: (HeroId) -> Unit, onOpenBlade: (WeaponId) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = Space.xs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(m.name, style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.semantics { heading() })
        if (m.shares.isEmpty()) Text("Nothing the record can give to ${m.name}.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
        m.shares.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = BronzeDeep)
            Column(Modifier.fillMaxWidth().padding(vertical = Space.xs).semantics(mergeDescendants = true) {}.testTag("replay_share_${m.key}_${s.ruleId ?: "own"}")) {
                Text((if (s.weapon) "Weapon · " else "") + (s.ruleId?.let { CombatContent.label(it, s.name) } ?: s.name), style = MaterialTheme.typography.labelLarge, color = Cream)
                val line = parts(s) + listOfNotNull(
                    "${s.applied} ${if (s.applied == 1) "status" else "statuses"} on enemies".takeIf { s.applied > 0 },
                    "triggered ${if (s.times == 1) "once" else "${s.times} times"}".takeIf { s.times > 0 },
                )
                Text(line.joinToString(" · ").ifEmpty { "Triggered, with nothing to count" }, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            val blade = m.weaponId?.let { WeaponId(it) }?.takeIf { state.weapons.containsKey(it) }
            blade?.let { InlineActionButton("Weapon record", { onOpenBlade(it) }, Modifier.testTag("replay_report_blade_${m.key}")) }
            if (state.heroes.containsKey(HeroId(m.key))) InlineActionButton("Hero", { onOpenHero(HeroId(m.key)) }, Modifier.testTag("replay_report_hero_${m.key}"))
        }
    }
}
