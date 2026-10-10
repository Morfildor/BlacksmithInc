package com.example.blacksmithproject.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.content.CharterDef

/** The picker over the ViewModel's own choice: what "New game" and "Begin era" will start under. Nothing at all for content without a guild. */
@Composable
fun CharterPicker(vm: GameViewModel, modifier: Modifier = Modifier) {
    val charters = vm.engine.content.guild?.charters?.takeIf { it.isNotEmpty() } ?: return
    val selected by vm.charter.collectAsStateWithLifecycle()
    CharterPicker(charters, selected, vm::selectCharter, modifier)
}

/** The choices in the order the arrows step through them: every charter, then null for the classic shop without a guild. */
fun charterChoices(charters: List<CharterDef>): List<String?> = charters.map { it.id } + null

/** The choice [by] steps from [selected], wrapping at both ends; an id that is not a choice counts as the first. */
fun stepCharter(charters: List<CharterDef>, selected: String?, by: Int): String? {
    val ids = charterChoices(charters)
    return ids[(ids.indexOf(selected).coerceAtLeast(0) + by).mod(ids.size)]
}

/**
 * The charter the next era begins under, chosen in place: the arrows step through [charters] and, last, "No charter"
 * (the classic shop, a null id). Collapsed it is the name and how it plays; "About this charter" opens what it gives
 * and what it costs, in the charter's own words. A selector only: the button under it starts the era with what is shown.
 */
@Composable
fun CharterPicker(charters: List<CharterDef>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    val ids = charterChoices(charters)
    val at = ids.indexOf(selected).coerceAtLeast(0)
    val charter = charters.firstOrNull { it.id == ids[at] }
    var about by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().forgeRow().padding(horizontal = Space.xs).testTag("charter_picker")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onSelect(stepCharter(charters, selected, -1)) }, modifier = Modifier.size(48.dp).testTag("charter_prev")) {
                Text("‹", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { contentDescription = "Previous charter" })
            }
            Column(Modifier.weight(1f).padding(vertical = Space.xs).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Charter ${at + 1} of ${ids.size}", style = MaterialTheme.typography.labelSmall, color = CreamMuted)
                Text(charter?.name ?: "No charter", style = MaterialTheme.typography.titleMedium, color = Cream, textAlign = TextAlign.Center, modifier = Modifier.testTag("charter_name"))
                Text(charter?.let { "Plays as: ${it.play}" } ?: "The shop alone, as before.", style = MaterialTheme.typography.bodySmall, color = CreamMuted, textAlign = TextAlign.Center)
            }
            TextButton(onClick = { onSelect(stepCharter(charters, selected, 1)) }, modifier = Modifier.size(48.dp).testTag("charter_next")) {
                Text("›", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { contentDescription = "Next charter" })
            }
        }
        if (charter != null) {
            Text(
                if (about) "About this charter ▴" else "About this charter ▾", style = MaterialTheme.typography.labelLarge, color = Gold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { about = !about }.heightIn(min = 48.dp).wrapContentHeight().testTag("charter_about"),
            )
            if (about) Column(Modifier.padding(horizontal = Space.sm).padding(bottom = Space.sm).testTag("charter_details")) {
                Text(charter.pitch, style = MaterialTheme.typography.bodySmall, color = Cream)
                Text("You get: ${charter.advantage}", style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(top = Space.xs))
                Text("You give up: ${charter.constraint}", style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(top = Space.xs))
            }
        }
    }
}
