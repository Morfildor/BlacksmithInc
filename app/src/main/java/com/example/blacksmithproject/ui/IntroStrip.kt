package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.data.IntroStatus
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.ForgePanel
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space

/**
 * The guided first day's step, between the top bar and the destination. It is a row of the layout, so it never covers a
 * control or takes a tap meant for one. The objective and its sentence are read out when the step changes; focus is
 * left where the player put it. "Skip guidance" is on every step; the day's outcome closes with "Done" instead.
 */
@Composable
fun IntroStrip(ui: IntroUi, onSkip: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var why by rememberSaveable(ui.step) { mutableStateOf(false) }
    Column(
        // Never more than a part of the screen, however large the text or however much "Why?" has opened: past that the
        // words scroll inside the strip, and its actions and the game's controls under it keep their places.
        modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * if (LocalDensity.current.fontScale > 1.3f) 0.3f else 0.42f).dp).background(ForgePanel)
            .drawBehind { drawRect(Gold, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = Space.md, end = Space.xs, bottom = Space.sm).testTag("intro_strip"),
    ) {
        val actions: @Composable () -> Unit = {
            if (ui.line.why != null) InlineActionButton(if (why) "Hide" else "Why?", { why = !why }, Modifier.testTag("intro_why"))
            if (ui.last) InlineActionButton("Done", onDone, Modifier.testTag("intro_done"))
            else InlineActionButton("Skip guidance", onSkip, Modifier.testTag("intro_skip"))
        }
        val overline: @Composable (Modifier) -> Unit = { Text(ui.overline, style = MaterialTheme.typography.labelSmall, color = Gold, modifier = it) }
        // Large text: the actions get a row of their own under the words, as the stat rows stack, and the sentence waits
        // behind "Why?" with the rest, so the step, its way out and the controls under the strip all stay within reach.
        // The day's outcome keeps its sentence: there it is the news itself.
        val large = LocalDensity.current.fontScale > 1.3f
        val folded = large && !ui.last
        if (large) overline(Modifier.padding(top = Space.xs))
        else Row(verticalAlignment = Alignment.CenterVertically) { overline(Modifier.weight(1f)); actions() }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(end = Space.sm).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text(ui.line.objective, style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.semantics { heading() }.testTag("intro_objective"))
            if (!folded || why) Text(ui.line.sentence, style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.testTag("intro_sentence"))
            if (why) ui.line.why?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Cream, modifier = Modifier.padding(top = Space.xs).testTag("intro_why_text")) }
        }
        if (large) Row { actions() }
    }
}

/** The step as the forge result says it, over the price: the same words and the same way out as the strip the card covers. */
@Composable
fun IntroCardLine(line: IntroLine, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    var why by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(vertical = Space.sm).drawBehind { drawRect(Gold, size = Size(3.dp.toPx(), size.height)) }.padding(start = Space.md).testTag("intro_card")) {
        Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
            Text(line.objective, style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.testTag("intro_objective"))
            Text(line.sentence, style = MaterialTheme.typography.bodySmall, color = CreamMuted)
            if (why) line.why?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Cream, modifier = Modifier.padding(top = Space.xs).testTag("intro_why_text")) }
        }
        Row {
            if (line.why != null) InlineActionButton(if (why) "Hide" else "Why?", { why = !why }, Modifier.testTag("intro_why"))
            InlineActionButton("Skip guidance", onSkip, Modifier.testTag("intro_skip"))
        }
    }
}

/**
 * The guidance on the title, only while it is waiting to be decided: queued for the game about to start (with its way
 * out), or stopped with its run and asking once whether to go on. Nothing otherwise. It stands under the menu's primary
 * action, so "New game" is where it always was at every text size.
 */
@Composable
fun IntroMenuLine(vm: GameViewModel, modifier: Modifier = Modifier) {
    val record by vm.intro.collectAsStateWithLifecycle()
    val status = record?.status
    if (status != IntroStatus.QUEUED && status != IntroStatus.OFFERED) return
    val words: @Composable (Modifier) -> Unit = {
        Text(
            if (status == IntroStatus.QUEUED) "This game starts with a short guided first day." else "The guided first day stopped before its end.",
            style = MaterialTheme.typography.bodySmall, color = Cream, modifier = it.padding(vertical = Space.sm),
        )
    }
    val actions: @Composable () -> Unit = {
        if (status == IntroStatus.QUEUED) InlineActionButton("Skip guidance", { vm.skipIntro() }, Modifier.testTag("intro_menu_skip"))
        else {
            InlineActionButton("Resume", { vm.introAction() }, Modifier.testTag("intro_menu_resume"))
            InlineActionButton("Dismiss", { vm.skipIntro() }, Modifier.testTag("intro_menu_dismiss"))
        }
    }
    val place = modifier.fillMaxWidth().forgeRow().padding(start = Space.md, end = Space.xs).testTag("intro_menu")
    // Large text: the words get the whole width and the actions a row under them.
    if (LocalDensity.current.fontScale > 1.3f) Column(place) { words(Modifier); Row { actions() } }
    else Row(place, verticalAlignment = Alignment.CenterVertically) { words(Modifier.weight(1f)); actions() }
}

/**
 * Where the guided step is done: a still gold outline with a small pointer at its corner, so it is a shape as well as a
 * colour, and it is said to a screen reader. Drawn on the control itself; nothing is laid over the screen.
 */
fun Modifier.introMark(on: Boolean): Modifier = if (!on) this else this
    .semantics { stateDescription = "Suggested next step" }
    .drawWithContent {
        drawContent()
        val w = 2.dp.toPx()
        drawRect(Gold, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
        val p = 12.dp.toPx()
        drawPath(Path().apply { moveTo(0f, 0f); lineTo(p, 0f); lineTo(0f, p); close() }, Gold)
    }

/**
 * A first game begun under a charter: what a new player needs before the one choice that cannot be taken back, and the
 * guidance's way out, which is otherwise not on this screen. Shown only while the guided first day is still ahead.
 */
@Composable
fun OpeningIntroNote(onSkip: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().drawBehind { drawRect(Gold, size = Size(3.dp.toPx(), size.height)) }.padding(start = Space.md, end = Space.xs).testTag("opening_intro")) {
        Text(
            "First game? Each of these is a full start. The guided first day begins once you choose. The shop without a guild is \"No charter\" on the title.",
            style = MaterialTheme.typography.bodySmall, color = Cream, modifier = Modifier.padding(top = Space.sm),
        )
        InlineActionButton("Skip guidance", onSkip, Modifier.testTag("intro_skip"))
    }
}
