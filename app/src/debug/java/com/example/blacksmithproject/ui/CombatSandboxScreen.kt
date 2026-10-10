package com.example.blacksmithproject.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.combat.Combatant
import com.tinyblacksmith.core.combat.Fight
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.sim.CombatSandbox

/** One fighter of a fixture as the fight receives it: the numbers on one line, then every rule it brings, in the definitions' own words. */
internal fun sandboxFighter(c: Combatant): Pair<String, List<String>> =
    (c.name + (c.weaponName?.let { " with $it" } ?: "") + ": health ${c.health}/${c.maxHealth}, strike ${c.strikeMin}" + (if (c.strikeMax > c.strikeMin) " to ${c.strikeMax}" else "") +
        (if (c.support > 0) ", support ${c.support}" else "")) to
        ((c.kit.passives + c.effects).map { "${it.name}: ${it.description}" } + c.kit.moves.mapNotNull { m -> m.telegraph?.let { "Known: $it" } })

/**
 * Debug builds only: the fixture fights of `CombatSandbox` (the ones `./gradlew :core:combat` prints), to read on the
 * phone. A list of fixtures; choosing one shows who stands on each side with their rules, then the fight resolved from
 * that setup. Nothing here reads or changes a run or a save.
 */
@Composable
fun CombatSandboxScreen(onClose: () -> Unit) {
    var chosen by rememberSaveable { mutableStateOf<String?>(null) }
    val fixture = chosen?.let { id -> CombatSandbox.fixtures.firstOrNull { it.id == id } }
    // A dialog is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    Dialog(onDismissRequest = { if (fixture != null) chosen = null else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
            // Keyed on the page: each opens scrolled to its top.
            key(chosen) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Space.md).testTag(if (fixture == null) "sandbox_list" else "sandbox_fight")) {
                    if (fixture == null) {
                        Text("Combat sandbox", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { heading() })
                        Secondary("Fixture fights of the interaction engine, resolved here from a fixed setup. They are not part of any run and change no save.")
                        for (f in CombatSandbox.fixtures) {
                            Column(Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().clickable(role = Role.Button) { chosen = f.id }.padding(Space.md).testTag("sandbox_fixture_${f.id}")) {
                                Text(f.title, style = MaterialTheme.typography.titleMedium)
                                Secondary(f.about)
                            }
                        }
                        OutlinedButton(onClick = onClose, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(top = Space.md).heightIn(min = 48.dp).testTag("sandbox_close")) { Text("Close") }
                    } else {
                        val setup = fixture.setup
                        val result = remember(fixture.id) { Fight.resolve(setup) }
                        OutlinedButton(onClick = { chosen = null }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sandbox_back")) { Text("Back to the fixtures") }
                        Text(fixture.title, style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.padding(top = Space.sm).semantics { heading() })
                        Secondary(fixture.about)
                        Secondary(setup.title + ". Posture: " + when (setup.posture) { Posture.CAUTIOUS -> "cautious"; Posture.BALANCED -> "balanced"; Posture.RECKLESS -> "reckless" } + ".", Modifier.padding(top = Space.xs))
                        SandboxSide("The party", setup.party, "sandbox_party")
                        if (setup.partyEffects.isNotEmpty()) {
                            SectionHeader("Relics with the party", Modifier.padding(top = Space.md))
                            Column(Modifier.testTag("sandbox_relics")) { setup.partyEffects.forEach { Secondary("${it.name}: ${it.description}", Modifier.padding(top = 2.dp)) } }
                        }
                        SandboxSide("Against them", setup.enemies, "sandbox_enemies")
                        SectionHeader("The fight", Modifier.padding(top = Space.md))
                        FightReport(result, Modifier.padding(top = Space.xs).testTag("sandbox_report"), expanded = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun SandboxSide(title: String, fighters: List<Combatant>, tag: String) {
    SectionHeader(title, Modifier.padding(top = Space.md))
    Column(Modifier.testTag(tag)) {
        for (c in fighters) {
            val (head, rules) = sandboxFighter(c)
            Text(head, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.sm))
            rules.forEach { Secondary(it, Modifier.padding(start = Space.sm, top = 2.dp)) }
        }
    }
}
