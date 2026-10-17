package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.blacksmithproject.ui.theme.BronzeContainer
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.ForgeNight
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.combat.FightResult
import com.tinyblacksmith.core.combat.Stat
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.crafting.Bench
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroClassId
import com.tinyblacksmith.core.model.Weapon

/** A rule that came into play on the bench: how often, and the first chain of events that led to it, cause first. */
@Immutable data class BenchFiredUi(val id: String, val name: String, val times: Int, val chain: List<String>)

/** A rule that did not, and the plain reason the bench read off the fight. */
@Immutable data class BenchSilentUi(val id: String, val name: String, val why: String)

/**
 * One rehearsal as the sheet shows it. [synthetic] names everything made up and is always shown. Every rule the weapon
 * has on the road is in [fired] or in [silent]. [notTested] are the rules it has only on the town's wall, by name.
 */
@Immutable
data class BenchUi(
    val title: String, val synthetic: List<String>, val fired: List<BenchFiredUi>, val silent: List<BenchSilentUi>, val notTested: List<String>, val result: FightResult,
    /** Heading and one line for [notTested]: which place this rehearsal stands in and where the other rules can be tried. */
    val elsewhere: String = "", val elsewhereNote: String = "",
    /** The statuses the tested rules read or give, from their definitions: the keywords the sheet explains once. */
    val stats: List<Stat> = emptyList(),
)

fun benchUi(report: Bench.Report): BenchUi = BenchUi(
    report.title, report.synthetic,
    report.fired.map { BenchFiredUi(it.rule.id, it.rule.effect.name, it.times, it.chain) },
    report.silent.map { BenchSilentUi(it.rule.id, it.rule.effect.name, it.why) },
    report.notTested.map { it.effect.name }, report.result,
    elsewhere = if (report.home) "Quests only" else "Siege only",
    elsewhereNote = if (report.home) "This practice fight is on the town's wall. To try these effects, choose another enemy line under Against." else "This practice fight is on a quest. To try these effects, choose The town's wall under Against.",
    stats = (report.fired.map { it.rule.effect } + report.silent.map { it.rule.effect }).flatMap { it.statuses() }.distinct(),
)

/** The party relics the workshop holds, by ID: what a party leaving today would take along. The bench names them in its report. */
fun benchRelics(state: GameState, content: ContentCatalog): List<String> = content.combat?.let { combat -> state.relics.map { it.id }.filter { it in combat.relicEffects } }.orEmpty()

private val Chip = RoundedCornerShape(6.dp)

/**
 * The bench: [weapon] in the hands of a made-up fighter against a made-up line, resolved by the same fight a quest
 * uses (`Bench.run`). A full-screen dialog like the debug build's fixture fights. It reads no save and changes none:
 * nothing is paid, learned or recorded. [demonstration]: a recipe from the Forge, not a weapon that exists.
 *
 * V3, outcome first: "Practice fight" and what it is (the bench's own disclaimer, always on screen, everything it made
 * up on its card), how the fight ended, then its effects as ability rows, always open: those that triggered (how often,
 * and the chain that led to them) and those that did not (and why), with the statuses they name explained once. Then
 * the choices that run it again (who carries it, who stands with them, what they face), the effects only tried
 * elsewhere, and the fight's technical part behind "Full report". A new choice brings the new outcome into view.
 */
@Composable
fun BenchSheet(weapon: Weapon, demonstration: Boolean, content: ContentCatalog, config: BalanceConfig, relics: List<String>, onClose: () -> Unit) {
    val combat = content.combat ?: return
    // Saved as plain IDs, so the choices come back after the process is killed.
    var wielder by rememberSaveable { mutableStateOf(Bench.defaultWielder(weapon.familyId, content).value) }
    var company by rememberSaveable { mutableStateOf(Bench.companions.first().id) }
    var target by rememberSaveable { mutableStateOf(Bench.targets.first().id) }
    var whole by rememberSaveable { mutableStateOf(false) }
    val ui = remember(weapon, demonstration, wielder, company, target, relics) { benchUi(Bench.run(weapon, demonstration, HeroClassId(wielder), company, target, content, config, relics)) }
    val run = "${wielder}_${company}_$target"
    val scroll = rememberScrollState()
    // A choice below the outcome runs the fight again: the new outcome is brought into view. Not on the first frame.
    var shownRun by remember { mutableStateOf(run) }
    LaunchedEffect(run) { if (run != shownRun) { shownRun = run; scroll.animateScrollTo(0) } }
    // A dialog is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(scroll).padding(Space.md).testTag("bench_sheet")) {
                Text("Practice fight", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { heading() })
                Text(ui.title, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.testTag("bench_title"))

                // What this is, said before anything it produced and never folded away: everything made up is on its card.
                Row(Modifier.fillMaxWidth().padding(top = Space.sm).testTag("bench_synthetic"), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        Text("This is a practice fight", style = MaterialTheme.typography.titleSmall, color = Cream)
                        ui.synthetic.lastOrNull()?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CreamMuted) }
                    }
                    InfoButton("what is made up", { benchSyntheticTip(ui) }, tag = "bench_synthetic_info")
                }

                FightLead(ui.result, Modifier.padding(top = Space.sm).testTag("bench_fight"))

                SectionHeader("Effects that triggered")
                Column(Modifier.fillMaxWidth().testTag("bench_fired")) {
                    if (ui.fired.isEmpty()) Secondary("None of its effects triggered.")
                    TipSectionBlock(TipSection(null, benchFiredLines(ui)))
                }

                SectionHeader("Effects that did not trigger")
                Column(Modifier.fillMaxWidth().testTag("bench_silent")) {
                    if (ui.silent.isEmpty()) Secondary("Every effect it has here triggered.")
                    TipSectionBlock(TipSection(null, benchSilentLines(ui)))
                }
                // The statuses those effects read or give, each explained once.
                Column(Modifier.padding(top = Space.sm)) { TipKeywords(keywords(ui.stats, config)) }

                SectionHeader("Try it differently")
                BenchChoice("Carried by", "bench_wielder", content.classes.filter { it.id in combat.kitByClass }.map { Triple(it.id.value, it.name, null) }, wielder) { wielder = it }
                BenchChoice("Company", "bench_company", Bench.companions.map { Triple(it.id, it.name, it.about) }, company) { company = it }
                BenchChoice("Against", "bench_target", Bench.targets.map { Triple(it.id, it.name, it.about) }, target) { target = it }

                if (ui.notTested.isNotEmpty()) {
                    SectionHeader(ui.elsewhere)
                    Column(Modifier.fillMaxWidth().testTag("bench_not_tested")) {
                        Secondary(ui.elsewhereNote)
                        ui.notTested.forEach { Text(it, style = MaterialTheme.typography.titleSmall, color = CreamMuted, modifier = Modifier.padding(top = Space.xs)) }
                    }
                }

                // Both sides and every event: the fight's technical part, opened in place on request.
                FullReportHead(whole, { whole = !whole }, tag = "fight_toggle")
                if (whole) FightFullBody(ui.result)

                SecondaryActionButton("Close", onClose, Modifier.fillMaxWidth().padding(top = Space.md).testTag("bench_close"))
            }
        }
    }
}

/** One choice of the bench as a row of chips that wraps: [choices] are ID, name and what the choice means. The chosen one says so with a tick, and its meaning stands under the row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BenchChoice(label: String, tag: String, choices: List<Triple<String, String, String?>>, chosen: String, onPick: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = CreamMuted, modifier = Modifier.padding(top = Space.md, bottom = Space.xs))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        choices.forEach { (id, name, _) ->
            val on = id == chosen
            Box(
                Modifier.heightIn(min = 48.dp).clip(Chip).background(if (on) BronzeContainer else ForgeNight).border(1.dp, if (on) Gold else BronzeDeep, Chip)
                    .clickable(role = Role.RadioButton) { onPick(id) }.padding(horizontal = 12.dp, vertical = 6.dp).testTag("${tag}_$id").semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) { Text(if (on) "✓ $name" else name, style = MaterialTheme.typography.labelLarge, color = if (on) Gold else CreamMuted) }
        }
    }
    choices.firstOrNull { it.first == chosen }?.third?.let { Secondary(it, Modifier.padding(top = Space.xs).testTag("${tag}_about")) }
}
