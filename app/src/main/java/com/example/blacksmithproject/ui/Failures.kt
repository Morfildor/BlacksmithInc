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
private const val NO_WAY_BACK = "The game cannot automatically restore this backup"

private fun startOver(does: String, confirm: String) = StartOverText("Start over (keeps a backup)", does, "Start over?", confirm, "Start over")

internal fun loadFailureText(f: SaveFailure): LoadFailureText = when (f) {
    is SaveFailure.Corrupt -> if (f.key == "legacy") legacyRowText(newer = false, runSound = f.runSound) else runRowText(
        "The saved run cannot be read", "Your current run's save is damaged and couldn't be opened.", "the unreadable run", newer = false,
    )
    is SaveFailure.Newer -> (if (f.key == "legacy") legacyRowText(newer = true, runSound = f.runSound) else newerRunText())
        .let { LoadFailureText(it.title, it.happened, it.safe, it.startOver, listOf("Save format ${f.found}. This version supports up to ${f.supported}.")) }
    is SaveFailure.Incompatible -> {
        val details = f.problems.take(3) + listOfNotNull("and ${f.problems.size - 3} more".takeIf { f.problems.size > 3 })
        // A run from newer rules or content is not a damaged run: the way on is to update the game.
        val text = if (f.problems.any { it.startsWith("Run uses rules") || it.startsWith("Run uses content") }) newerRunText() else runRowText(
            "This version cannot continue the saved run", "This run contains game data that this version doesn't support.", "the run this version cannot play", newer = false,
        )
        LoadFailureText(text.title, text.happened, text.safe, text.startOver, details)
    }
    is SaveFailure.FileDamaged -> LoadFailureText(
        "The save file is damaged",
        "The save for your run and legacy is damaged. It couldn't be opened.",
        UNTOUCHED,
        startOver(
            "Keeps the damaged save as a backup on this device. Starts era 1 with no run, points, upgrades or legends.",
            "Keeps the damaged save as a backup. Your run, points, upgrades and legends will be removed from the active game. Starts era 1. $NO_WAY_BACK.",
        ),
    )
    is SaveFailure.Io -> LoadFailureText(
        "The save could not be opened",
        "Couldn't access the device's storage. Try again. Check for free space if this continues.",
        "Nothing has been deleted.",
        startOver = null,
    )
}

private fun newerRunText() = runRowText(
    "This save is from a newer version",
    "A newer version of Tiny Blacksmith saved this run. Update the game to continue.",
    "the run from the newer version", newer = true,
)

/** Only the run row is the problem: the legacy row was read, so it is safe and stays. */
private fun runRowText(title: String, happened: String, theRun: String, newer: Boolean) = LoadFailureText(
    title, happened, "$UNTOUCHED Your points, upgrades and legends are stored separately and are safe.",
    startOver(
        "Keeps $theRun as a backup on this device and returns to the title screen. Keeps your legacy.",
        "Keeps ${theRun} as a backup. You won't be able to continue that run. $NO_WAY_BACK" + (if (newer) ", even after updating." else "."),
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
        (if (newer) "was saved by a newer version of Tiny Blacksmith. Update the game to continue." else "is damaged and couldn't be opened.")
    if (runSound) return LoadFailureText(
        title, happened, "$UNTOUCHED Your current run can still be opened. It contains a copy of your legacy.",
        StartOverText(
            "Rebuild the legacy from the run",
            "Keeps $theRecord as a backup. Restores the legacy copy from your current run, then continues the run." +
                (if (newer) " Any legacy data added by the newer version stays only in the backup." else ""),
            "Rebuild the legacy?",
            "Keeps $theRecord as a backup and replaces it with the legacy copy from your current run. " +
                "Continues with the points, upgrades and legends in that copy. If the run has ended, claim its legacy again, " +
                "then buy any later upgrades again using the restored points." +
                (if (newer) " Data added by the newer version remains only in the backup, even after updating. $NO_WAY_BACK." else ""),
            "Rebuild",
        ),
    )
    return LoadFailureText(
        title, happened, UNTOUCHED,
        startOver(
            "Keeps $theRecord and any current run as backups. Starts era 1 with no points, upgrades or legends.",
            "Keeps $theRecord and any current run as backups. $NO_WAY_BACK. " +
                "Your run, points, upgrades and legends will be removed from the active game" + (if (newer) ", even after updating." else ".") + " You begin again from era 1.",
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
        Secondary("Tries to open the save again without changing it.", Modifier.padding(top = Space.xs))

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
                    Text("Couldn't confirm whether " + (if (endDay) "the day" else "that last action") + "was saved. You're seeing the state from before that action.")
                    Secondary("Try again safely retries the save without repeating the action. Keep working continues from the state shown. Check storage space if this continues.")
                } else {
                    Text("The game is still in the state from before " + (if (endDay) "you ended the day." else "that last action."))
                    Secondary("Try again retries the action. Keep working returns without it. Check storage space if this continues.")
                }
            }
        },
        confirmButton = { PrimaryActionButton("Try again", onRetry, Modifier.heightIn(min = 48.dp).testTag("save_retry")) },
        dismissButton = { InlineActionButton("Keep working", onKeepWorking, Modifier.heightIn(min = 48.dp).testTag("save_keep_working")) },
    )
}
