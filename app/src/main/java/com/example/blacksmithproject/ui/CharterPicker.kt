package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
 * The charter the next era begins under, every choice in view: one [ChoiceRow] per charter and, last, "No charter" (the
 * classic shop, a null id). A row says whether it is a guild run and how it plays. The chosen row also says what the
 * charter gives and what it costs, so the choice is read before "New game" or "Begin era" under it starts the era.
 */
@Composable
fun CharterPicker(charters: List<CharterDef>, selected: String?, onSelect: (String?) -> Unit, modifier: Modifier = Modifier) {
    val ids = charterChoices(charters)
    val at = ids[ids.indexOf(selected).coerceAtLeast(0)]
    Column(modifier.fillMaxWidth().testTag("charter_picker"), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Caps("How the era begins")
        ids.forEach { id ->
            val charter = charters.firstOrNull { it.id == id }
            val chosen = id == at
            ChoiceRow(chosen, { onSelect(id) }, Modifier.testTag("charter_row_${id ?: "none"}")) {
                Text(charter?.name ?: "No charter", style = MaterialTheme.typography.titleSmall, color = if (chosen) Gold else Cream, modifier = if (chosen) Modifier.testTag("charter_name") else Modifier)
                Text(charter?.let { "Guild run · ${it.play}" } ?: "Classic run · the shop alone, no guild.", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                if (chosen && charter != null) Column(Modifier.padding(top = Space.xs).testTag("charter_details")) {
                    Text("${EffectKind.BUFF.sign} ${charter.advantage}", style = MaterialTheme.typography.bodySmall, color = EffectKind.BUFF.color)
                    Text("${EffectKind.FLAW.sign} ${charter.constraint}", style = MaterialTheme.typography.bodySmall, color = EffectKind.FLAW.color, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}
