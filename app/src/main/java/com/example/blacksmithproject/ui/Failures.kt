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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

/** The way out that sets something aside: its button, what it does (under the button), and what the confirmation says is lost. */
internal class StartOverText(val button: String, val does: String, val confirmTitle: String, val confirm: String, val confirmButton: String)

/**
 * What a failed load says: what happened, then what is safe, then what starting over would cost. Plain words; the
 * technical detail comes last and small. [startOver] is null when nothing can be set aside (a storage error).
 */
internal class LoadFailureText(val title: String, val happened: String, val safe: String, val startOver: StartOverText?, val details: List<String> = emptyList())

private const val UNTOUCHED = "Nothing has been deleted or changed."
private const val NO_WAY_BACK = "The game cannot bring a backup back by itself"

private fun startOver(does: String, confirm: String) = StartOverText("Start over (keeps a backup)", does, "Start over?", confirm, "Start over")

internal fun loadFailureText(f: SaveFailure): LoadFailureText = when (f) {
    is SaveFailure.Corrupt -> if (f.key == "legacy") legacyRowText(newer = false, runSound = f.runSound) else runRowText(
        "The saved run cannot be read", "The save that holds your current run is damaged, so the game cannot open it.", "the unreadable run", newer = false,
    )
    is SaveFailure.Newer -> (if (f.key == "legacy") legacyRowText(newer = true, runSound = f.runSound) else newerRunText())
        .let { LoadFailureText(it.title, it.happened, it.safe, it.startOver, listOf("Save format ${f.found}; this version reads up to ${f.supported}.")) }
    is SaveFailure.Incompatible -> {
        val details = f.problems.take(3) + listOfNotNull("and ${f.problems.size - 3} more".takeIf { f.problems.size > 3 })
        // A run from newer rules or content is not a damaged run: the way on is to update the game.
        val text = if (f.problems.any { it.startsWith("Run uses rules") || it.startsWith("Run uses content") }) newerRunText() else runRowText(
            "This version cannot continue the saved run", "The run holds something this version of the game does not know how to play.", "the run this version cannot play", newer = false,
        )
        LoadFailureText(text.title, text.happened, text.safe, text.startOver, details)
    }
    is SaveFailure.FileDamaged -> LoadFailureText(
        "The save file is damaged",
        "The file that holds your current run and your legacy (points, upgrades and legends) is damaged, so the game cannot open it.",
        UNTOUCHED,
        startOver(
            "Sets the damaged file aside on this device, not deleted, and begins again from era 1 with no run, points, upgrades or legends.",
            "The damaged save file is set aside, not deleted. $NO_WAY_BACK, so your run and your points, upgrades and legends will be gone from the game. You begin again from era 1.",
        ),
    )
    is SaveFailure.Io -> LoadFailureText(
        "The save could not be opened",
        "The device's storage did not answer. This is often temporary, for example when storage is full.",
        "Nothing has been deleted.",
        startOver = null,
    )
}

private fun newerRunText() = runRowText(
    "This save is from a newer version",
    "The save that holds your current run was written by a newer version of Tiny Blacksmith. Update the game to go on with it.",
    "the run from the newer version", newer = true,
)

/** Only the run row is the problem: the legacy row was read, so it is safe and stays. */
private fun runRowText(title: String, happened: String, theRun: String, newer: Boolean) = LoadFailureText(
    title, happened, "$UNTOUCHED Your legacy (points, upgrades and legends) is stored separately and is safe.",
    startOver(
        "Sets $theRun aside as a backup on this device and returns to the title. Your legacy is kept.",
        "${theRun.replaceFirstChar { it.uppercase() }} is set aside, not deleted. $NO_WAY_BACK, so you will not be able to continue that run" + (if (newer) ", even after updating." else "."),
    ),
)

/**
 * The legacy row is the problem. A run that loads carries its own copy of the legacy, so nothing the player owns is
 * lost with the row; without such a run the points, upgrades and legends go with it, and the words say so.
 */
private fun legacyRowText(newer: Boolean, runSound: Boolean): LoadFailureText {
    val theRecord = if (newer) "the legacy record from the newer version" else "the unreadable legacy record"
    val title = if (newer) "This save is from a newer version" else "The legacy record cannot be read"
    val happened = "The save that holds your legacy record (points, upgrades and legends) " +
        (if (newer) "was written by a newer version of Tiny Blacksmith. Update the game to go on with it." else "is damaged, so the game cannot open it.")
    if (runSound) return LoadFailureText(
        title, happened, "$UNTOUCHED Your current run can still be read, and it carries its own copy of your legacy.",
        StartOverText(
            "Rebuild the legacy from the run",
            "Sets $theRecord aside as a backup on this device and rebuilds it from the copy inside your current run. The run goes on." +
                (if (newer) " Whatever the newer version added to the legacy stays in the backup." else ""),
            "Rebuild the legacy?",
            "${theRecord.replaceFirstChar { it.uppercase() }} is set aside, not deleted, and replaced by the copy your current run carries. " +
                "Your run goes on with the points, upgrades and legends in that copy. If that run has already ended, you claim its legacy again, " +
                "and upgrades bought after it ended are bought again with the points you get back." +
                (if (newer) " $NO_WAY_BACK, so whatever the newer version added is not there, even after updating." else ""),
            "Rebuild",
        ),
    )
    return LoadFailureText(
        title, happened, UNTOUCHED,
        startOver(
            "Sets $theRecord aside as a backup on this device, with the current run if there is one, then begins again from era 1 with no points, upgrades or legends.",
            "${theRecord.replaceFirstChar { it.uppercase() }} is set aside, not deleted, and so is any run in progress. $NO_WAY_BACK, " +
                "so your points, upgrades and legends and that run will be gone from the game" + (if (newer) ", even after updating." else ".") + " You begin again from era 1.",
        ),
    )
}

/**
 * Shown instead of a crash when the save cannot be loaded (plan 6.5 "Load failed"). Retry reads the store again;
 * starting over sets only the unreadable rows (or the damaged file) aside as a backup and is never offered for a storage error.
 */
@Composable
fun LoadFailedScreen(failure: SaveFailure, working: Boolean, onRetry: () -> Unit, onStartOver: () -> Unit) {
    val text = loadFailureText(failure)
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Space.lg).testTag("load_failed"),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(text.happened, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.md))
        Text(text.safe, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Space.sm))

        PrimaryActionButton(if (working) "Reading the save..." else "Try again", onRetry, Modifier.fillMaxWidth().padding(top = Space.lg).testTag("load_retry"), enabled = !working)
        Secondary("Reads the save again. Changes nothing.", Modifier.padding(top = Space.xs))

        text.startOver?.let { over ->
            SecondaryActionButton(over.button, { confirming = true }, Modifier.fillMaxWidth().padding(top = Space.lg).testTag("load_start_over"), enabled = !working)
            Secondary(over.does, Modifier.padding(top = Space.xs))
        }
        if (text.details.isNotEmpty()) {
            Secondary("Details", Modifier.padding(top = Space.lg))
            text.details.forEach { Secondary(it) }
        }
    }
    val over = text.startOver
    if (confirming && over != null) {
        AlertDialog(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            onDismissRequest = { confirming = false },
            title = { Text(over.confirmTitle) },
            text = { Text(over.confirm) },
            confirmButton = { PrimaryActionButton(over.confirmButton, { confirming = false; onStartOver() }, Modifier.heightIn(min = 48.dp).testTag("load_start_over_confirm")) },
            dismissButton = { InlineActionButton("Not now", { confirming = false }, Modifier.heightIn(min = 48.dp)) },
        )
    }
}

/**
 * A save that failed after the game was loaded (plan 6.1 rule 5): the last saved state stays on screen. Back means
 * Keep working. [unconfirmed]: the game could not read the save back either, so it does not claim that nothing changed.
 */
@Composable
fun SaveFailureDialog(op: GameSession.Op, unconfirmed: Boolean, onRetry: () -> Unit, onKeepWorking: () -> Unit) {
    val endDay = (op as? GameSession.Op.Dispatch)?.command is Command.EndDay
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = onKeepWorking,
        title = { Text(if (endDay) "Could not save the day" else "Could not save") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (unconfirmed) {
                    Text("The game could not confirm whether " + (if (endDay) "the day" else "that last action") + " was saved. What you see is the game as it was before it.")
                    Secondary("Try again saves it for certain; nothing is done twice. Keep working goes on without it. If this keeps happening, the device may be out of storage space.")
                } else {
                    Text("Nothing has changed. The game is exactly as it was before " + (if (endDay) "you ended the day." else "that last action."))
                    Secondary("Try again repeats it. Keep working goes back without it. If this keeps happening, the device may be out of storage space.")
                }
            }
        },
        confirmButton = { PrimaryActionButton("Try again", onRetry, Modifier.heightIn(min = 48.dp).testTag("save_retry")) },
        dismissButton = { InlineActionButton("Keep working", onKeepWorking, Modifier.heightIn(min = 48.dp).testTag("save_keep_working")) },
    )
}
