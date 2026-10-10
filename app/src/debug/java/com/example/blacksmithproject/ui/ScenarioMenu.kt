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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One row of `assets/scenarios/index.json`, written by `./gradlew :core:scenarios` (core test sources, `ScenarioSaves.kt`). */
private class ScenarioEntry(val id: String, val title: String, val description: String, val built: String, val ownLegacy: Boolean)

/**
 * Debug builds only (the release source set has an empty function of this name, and no scenario assets): the main
 * menu's "Scenarios" entry, with the "Combat sandbox" entry under it. It lists the constructed saves bundled under `assets/scenarios/` and, after a
 * confirmation, hands the chosen one to the session, which stores it in place of the current run.
 */
@Composable
fun ScenarioMenu(vm: GameViewModel, hasRun: Boolean, enabled: Boolean, onLoaded: () -> Unit) {
    val assets = LocalContext.current.assets
    var open by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<ScenarioEntry?>(null) }
    OutlinedButton(onClick = { open = true }, enabled = enabled, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(top = Space.sm).heightIn(min = 48.dp).testTag("menu_scenarios")) {
        Text("Scenarios (debug)")
    }
    // Beside it, and as far from a run: the fixture fights of the interaction engine (`CombatSandboxScreen`).
    var sandbox by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { sandbox = true }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(top = Space.sm).heightIn(min = 48.dp).testTag("sandbox_open")) {
        Text("Combat sandbox (debug)")
    }
    if (sandbox) CombatSandboxScreen(onClose = { sandbox = false })
    if (open) {
        val entries = remember {
            Json.parseToJsonElement(assets.open("scenarios/index.json").bufferedReader().use { it.readText() }).jsonArray.map { it.jsonObject }.map { row ->
                fun text(key: String) = row.getValue(key).jsonPrimitive.content
                ScenarioEntry(text("id"), text("title"), text("description"), text("built"), row.getValue("ownLegacy").jsonPrimitive.boolean)
            }
        }
        // A dialog is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
        Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Space.md).testTag("scenario_list")) {
                    Text("Scenarios", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { heading() })
                    Secondary("Constructed saves for testing, not natural play: each run was picked or built so that a rare event is one End Day away or already there. Loading one replaces the current run.")
                    for (e in entries) {
                        Column(
                            Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().clickable(role = Role.Button) { chosen = e }.padding(Space.md).testTag("scenario_${e.id}"),
                        ) {
                            Text(e.title, style = MaterialTheme.typography.titleMedium)
                            Secondary(e.description)
                        }
                    }
                    OutlinedButton(onClick = { open = false }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(top = Space.md).heightIn(min = 48.dp).testTag("scenario_close")) { Text("Close") }
                }
            }
        }
    }
    chosen?.let { e ->
        AlertDialog(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            onDismissRequest = { chosen = null },
            title = { Text("Load this scenario?") },
            text = {
                Text(
                    "${e.title}. A constructed save. ${e.built}\n\n" +
                        (if (hasRun) "It replaces the current run, which cannot be recovered. " else "") +
                        if (e.ownLegacy) "It also replaces your legacy (points, upgrades, journal, Legend Board and lineages) with the account this scenario was played on."
                        else "Your legacy is kept: the run continues on it.",
                    modifier = Modifier.testTag("scenario_confirm_text"),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val run = assets.open("scenarios/${e.id}.json").bufferedReader().use { it.readText() }
                        chosen = null
                        vm.loadScenario(run, e.ownLegacy) { open = false; onLoaded() }
                    },
                    modifier = Modifier.testTag("scenario_confirm"),
                ) { Text("Load scenario") }
            },
            dismissButton = { TextButton(onClick = { chosen = null }) { Text("Cancel") } },
        )
    }
}
