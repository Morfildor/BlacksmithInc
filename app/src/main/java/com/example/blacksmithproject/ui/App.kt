package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.GameSession
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.R
import com.example.blacksmithproject.ui.shopday.ShopDayHost
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.ForgeNight
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.LegacyProfile

@Composable
fun TinyBlacksmithApp(vm: GameViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val hapticsOn by vm.settings.haptics.collectAsStateWithLifecycle(initialValue = false)
    val haptics = rememberHaptics(hapticsOn)
    // The toll sounds when a run ends while playing, not each time the game is reopened onto an ended run.
    var wasPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(ui is UiState.RunEnded, ui is UiState.Playing, ui is UiState.ShopDay) {
        if (ui is UiState.RunEnded && wasPlaying) haptics.play(Moment.RUN_END)
        wasPlaying = ui is UiState.Playing || ui is UiState.ShopDay
    }
    // The main menu opens the game on a cold start; a restored process returns to where the player was.
    var menuOpen by rememberSaveable { mutableStateOf(true) }
    var menuSettings by remember { mutableStateOf(false) }
    // "New game" opens the run setup (the charter) before the era begins; Back there returns to the title.
    var setup by rememberSaveable { mutableStateOf(false) }
    val reducedMotion by vm.settings.reducedMotion.collectAsStateWithLifecycle(initialValue = false)
    val leaveMenu = { menuOpen = false }
    // Debug builds only: the release source set's ScenarioMenu is empty.
    val scenarios: @Composable () -> Unit = { ScenarioMenu(vm, hasRun = ui !is UiState.Title, enabled = ui.op !is GameSession.Status.Working, onLoaded = { menuSettings = false; leaveMenu() }) }
    // What the workshop and the shop day remember on screen (scroll positions, the open forge step, an open sheet)
    // outlives a visit to the menu. It is kept per day: a new day opens each destination at its top.
    val screens = rememberSaveableStateHolder()
    var kept by rememberSaveable { mutableStateOf<String?>(null) }
    val play = when (val s = ui) {
        is UiState.Playing -> "plan:${s.state.runId.value}:${s.state.day}"
        is UiState.ShopDay -> "day:${s.state.runId.value}:${s.state.day}"
        else -> null
    }
    LaunchedEffect(play) {
        if (play == null || play == kept) return@LaunchedEffect
        kept?.let(screens::removeState)
        kept = play
    }
    // Test tags double as Android resource IDs for uiautomator scripts (tools/emulator).
    CompositionLocalProvider(LocalHaptics provides haptics) { ProvideInspector {
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
            when (val s = ui) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.LoadFailed -> LoadFailedScreen(s.failure, s.working, onRetry = vm::retry, onStartOver = vm::startOver)
                is UiState.Title -> if (setup) RunSetup(vm, s.legacy.nextEra, s.op !is GameSession.Status.Working, onBack = { setup = false }, onBegin = { setup = false; leaveMenu(); vm.newRun() })
                    else MainMenu(
                        "Era ${s.legacy.nextEra}", s.legacy, "New game", "title_new_run", s.op !is GameSession.Status.Working,
                        // Content without charters has nothing to set up: the era begins at once.
                        onPrimary = { if (vm.engine.content.guild?.charters.isNullOrEmpty()) { leaveMenu(); vm.newRun() } else setup = true },
                        onSettings = { menuSettings = true }, extra = { IntroMenuLine(vm, Modifier.padding(top = Space.md)) },
                    )
                is UiState.Playing -> if (menuOpen) MainMenu("Era ${s.state.era} · Day ${s.state.day}", null, "Continue", "menu_continue", !s.busy, leaveMenu, { menuSettings = true }, onAbandon = vm::abandonRun) else screens.SaveableStateProvider(play!!) { WorkshopScreen(s, vm, onMainMenu = { menuOpen = true }) }
                is UiState.ShopDay -> if (menuOpen) MainMenu("Era ${s.state.era} · Day ${s.state.day}", null, "Continue", "menu_continue", true, leaveMenu, { menuSettings = true }) else screens.SaveableStateProvider(play!!) { ShopDayHost(s, vm, onMainMenu = { menuOpen = true }) }
                is UiState.RunEnded -> if (menuOpen) MainMenu("Era ${s.run.era} has ended", s.legacy, "See how the era ended", "menu_continue", true, leaveMenu, { menuSettings = true }) else RunEndScreen(s, vm)
            }
            val introRow by vm.introRow.collectAsStateWithLifecycle()
            if (menuSettings) SettingsSheet(reducedMotion, vm::setReducedMotion, hapticsOn, vm::setHaptics, onDismiss = { menuSettings = false }, intro = introRow, onIntro = { vm.introAction() }, debug = scenarios)
            // A save that failed leaves the last saved state on screen under this dialog; nothing is lost by dismissing it.
            (ui.op as? GameSession.Status.Failed)?.let { SaveFailureDialog(it.op, it.unconfirmed, onRetry = vm::retry, onKeepWorking = vm::dismissSaveFailure) }
        }
    } }
}

/** One count of the legacy on the title's plate: the number over what it counts, read as one. */
@Composable
private fun LegacyCount(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = Gold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * Main menu: the game's first screen. The owner's painting of the smithy at night fills it; in its dark lower third the
 * game's name, then the era and, between runs, the legacy as plain lines (nothing on a fresh account), and the actions at
 * the foot within reach of a thumb: one primary action (a new game, or the saved run) and "End era" while a run is
 * being planned ([onAbandon]); abandoning discards the run after a confirmation. Settings is in the corner, the debug
 * tools inside it. [extra] sits under the buttons: the guided first day's one line. Large text scrolls the upper part.
 */
@Composable
fun MainMenu(status: String, legacy: LegacyProfile?, primary: String, primaryTag: String, enabled: Boolean, onPrimary: () -> Unit, onSettings: () -> Unit, onAbandon: (() -> Unit)? = null, extra: @Composable () -> Unit = {}) {
    var confirmAbandon by remember { mutableStateOf(false) }
    // A fresh account has no era behind it and no points: saying "Era 1" over nothing only takes room.
    val fresh = legacy != null && legacy.points == 0 && legacy.eras.isEmpty()
    Box(Modifier.fillMaxSize().testTag("main_menu")) {
        // The owner's painting of the smithy fills the screen; the gradient below fades its lower third to night, where the name and
        // the actions stand. A painting, not a sprite: drawn smooth, cropped from the top so the roof and the sky stay.
        Image(painterResource(R.drawable.bg_title_workshop_night), contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to MaterialTheme.colorScheme.background.copy(alpha = 0.5f), 0.12f to Color.Transparent, 0.5f to Color.Transparent, 0.75f to MaterialTheme.colorScheme.background.copy(alpha = 0.6f), 1f to MaterialTheme.colorScheme.background)))
        BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding()) {
            val scroll = rememberScrollState()
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).heightIn(min = maxHeight).padding(horizontal = Space.lg).padding(top = Space.lg, bottom = Space.md),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    "Tiny Blacksmith", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, lineHeight = 46.sp, shadow = Shadow(Color.Black, Offset(0f, 2f), 10f)), color = Gold, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
                Text("Forge for the heroes. Hold the town.", style = MaterialTheme.typography.bodyMedium, color = CreamMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.xs))
                if (!fresh) Column(Modifier.padding(top = Space.lg).widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(status, style = MaterialTheme.typography.titleLarge, color = Cream, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                    legacy?.takeIf { it.points > 0 || it.eras.isNotEmpty() }?.let {
                        Row(Modifier.fillMaxWidth().padding(top = Space.sm)) {
                            LegacyCount(it.points, "Legacy points", Modifier.weight(1f))
                            LegacyCount(it.eras.size, "Eras played", Modifier.weight(1f))
                        }
                    }
                }
                Column(Modifier.padding(top = Space.lg).widthIn(max = 420.dp).fillMaxWidth()) {
                    PrimaryActionButton(primary, onPrimary, Modifier.fillMaxWidth().testTag(primaryTag), enabled)
                    if (onAbandon != null) SecondaryActionButton("End era", { confirmAbandon = true }, Modifier.fillMaxWidth().padding(top = Space.sm).heightIn(min = 48.dp).testTag("menu_abandon"), enabled = enabled)
                    extra()
                }
            }
        }
        // The pixel gear of the interface set, on a dark disc so it holds against the bright sky of the painting.
        IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(Space.sm).size(48.dp).testTag("menu_settings")) {
            Box(Modifier.size(40.dp).background(ForgeNight.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) { PixelIcon(R.drawable.icon_settings, 28.dp, description = "Settings") }
        }
    }
    // A dialog is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    if (confirmAbandon && onAbandon != null) ForgeDialog(
        "End this era?", onDismiss = { confirmAbandon = false },
        actions = {
            InlineActionButton("Keep playing", { confirmAbandon = false })
            // The one choice here that cannot be taken back, in the destructive style; staying is the plain way out.
            DestructiveActionButton("End era", { confirmAbandon = false; onAbandon() }, Modifier.testTag("menu_abandon_confirm"))
        },
    ) {
        Text("Ends this era permanently. You earn no legacy points, and its weapons and heroes won't be recorded. Your recipe notes stay.")
    }
}
