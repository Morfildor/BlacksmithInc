package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameSession
import com.example.blacksmithproject.data.SaveFailure
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.engine.Command

/** What a failed load says: what happened, then what is safe. Plain words; the technical detail comes last and small. */
private class LoadFailureText(val title: String, val happened: String, val safe: String, val details: List<String> = emptyList())

private fun loadFailureText(f: SaveFailure): LoadFailureText {
    val legacyRow = (f as? SaveFailure.Corrupt)?.key == "legacy" || (f as? SaveFailure.Newer)?.key == "legacy"
    val what = if (legacyRow) "your legacy record (points, upgrades and legends)" else "your current run"
    val untouched = "Nothing has been deleted or changed."
    val legacySafe = if (legacyRow) untouched else "$untouched Your legacy (points, upgrades and legends) is stored separately and is safe."
    return when (f) {
        is SaveFailure.Corrupt -> LoadFailureText(
            if (legacyRow) "The legacy record cannot be read" else "The saved run cannot be read",
            "The save that holds $what is damaged, so the game cannot open it.",
            legacySafe,
        )
        is SaveFailure.Newer -> LoadFailureText(
            "This save is from a newer version",
            "The save that holds $what was written by a newer version of Tiny Blacksmith. Update the game to go on with it.",
            legacySafe,
            listOf("Save format ${f.found}; this version reads up to ${f.supported}."),
        )
        is SaveFailure.Incompatible -> LoadFailureText(
            "This version cannot continue the saved run",
            "The run holds something this version of the game does not know how to play.",
            legacySafe,
            f.problems.take(3) + listOfNotNull("and ${f.problems.size - 3} more".takeIf { f.problems.size > 3 }),
        )
        is SaveFailure.Io -> LoadFailureText(
            "The save could not be opened",
            "The device's storage did not answer. This is often temporary, for example when storage is full.",
            untouched,
        )
    }
}

/**
 * Shown instead of a crash when the save cannot be loaded (plan 6.5 "Load failed"). Retry reads the store again;
 * starting over sets the unreadable rows aside under a backup key and is never offered for a storage error.
 */
@Composable
fun LoadFailedScreen(failure: SaveFailure, working: Boolean, onRetry: () -> Unit, onStartOver: () -> Unit) {
    val text = loadFailureText(failure)
    val legacyLost = (failure as? SaveFailure.Corrupt)?.key == "legacy" || (failure as? SaveFailure.Newer)?.key == "legacy"
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Space.lg).testTag("load_failed"),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(text.happened, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.md))
        Text(text.safe, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.sm))

        Button(onClick = onRetry, enabled = !working, modifier = Modifier.fillMaxWidth().padding(top = Space.lg).heightIn(min = 52.dp).testTag("load_retry")) {
            Text(if (working) "Reading the save..." else "Try again", style = MaterialTheme.typography.titleMedium)
        }
        Secondary("Reads the save again. Changes nothing.", Modifier.padding(top = Space.xs))

        if (failure !is SaveFailure.Io) {
            OutlinedButton(onClick = { confirming = true }, enabled = !working, modifier = Modifier.fillMaxWidth().padding(top = Space.lg).heightIn(min = 48.dp).testTag("load_start_over")) {
                Text("Start over (keeps a backup)")
            }
            Secondary(
                if (legacyLost) "Sets the unreadable legacy record and the current run aside as backups on this device, then begins again from era 1."
                else "Sets the unreadable run aside as a backup on this device and returns to the title. Your legacy is kept.",
                Modifier.padding(top = Space.xs),
            )
        }
        if (text.details.isNotEmpty()) {
            Secondary("Details", Modifier.padding(top = Space.lg))
            text.details.forEach { Secondary(it) }
        }
    }
    if (confirming) {
        AlertDialog(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            onDismissRequest = { confirming = false },
            title = { Text("Start over?") },
            text = {
                Text(
                    (if (legacyLost) "The legacy record and the current run are set aside, not deleted." else "The run that cannot be read is set aside, not deleted.") +
                        " This version of the game cannot bring a backup back by itself, so you will not be able to continue from it here.",
                )
            },
            confirmButton = { Button(onClick = { confirming = false; onStartOver() }, modifier = Modifier.heightIn(min = 48.dp).testTag("load_start_over_confirm")) { Text("Start over") } },
            dismissButton = { TextButton(onClick = { confirming = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Not now") } },
        )
    }
}

/** A save that failed after the game was loaded (plan 6.1 rule 5): the last saved state stays on screen. Back means Keep working. */
@Composable
fun SaveFailureDialog(op: GameSession.Op, onRetry: () -> Unit, onKeepWorking: () -> Unit) {
    val endDay = (op as? GameSession.Op.Dispatch)?.command is Command.EndDay
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = onKeepWorking,
        title = { Text(if (endDay) "Could not save the day" else "Could not save") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("Nothing has changed. The game is exactly as it was before " + (if (endDay) "you ended the day." else "that last action."))
                Secondary("Try again repeats it. Keep working goes back without it. If this keeps happening, the device may be out of storage space.")
            }
        },
        confirmButton = { Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp).testTag("save_retry")) { Text("Try again") } },
        dismissButton = { TextButton(onClick = onKeepWorking, modifier = Modifier.heightIn(min = 48.dp).testTag("save_keep_working")) { Text("Keep working") } },
    )
}
