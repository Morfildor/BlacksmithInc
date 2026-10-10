package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.GameSession
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.R
import com.example.blacksmithproject.ui.shopday.ShopDayHost
import com.example.blacksmithproject.UiState
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
    val reducedMotion by vm.settings.reducedMotion.collectAsStateWithLifecycle(initialValue = false)
    val leaveMenu = { menuOpen = false }
    // Test tags double as Android resource IDs for uiautomator scripts (tools/emulator).
    CompositionLocalProvider(LocalHaptics provides haptics) {
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
            when (val s = ui) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.LoadFailed -> LoadFailedScreen(s.failure, s.working, onRetry = vm::retry, onStartOver = vm::startOver)
                is UiState.Title -> MainMenu("Era ${s.legacy.nextEra} awaits", s.legacy, "New game", "title_new_run", s.op !is GameSession.Status.Working, onPrimary = { leaveMenu(); vm.newRun() }, onSettings = { menuSettings = true })
                is UiState.Playing -> if (menuOpen) MainMenu("Era ${s.state.era} · Day ${s.state.day}", null, "Continue run", "menu_continue", !s.busy, leaveMenu, { menuSettings = true }, onAbandon = vm::abandonRun) else WorkshopScreen(s, vm, onMainMenu = { menuOpen = true })
                is UiState.ShopDay -> if (menuOpen) MainMenu("Era ${s.state.era} · Day ${s.state.day}", null, "Continue run", "menu_continue", true, leaveMenu, { menuSettings = true }) else ShopDayHost(s, vm)
                is UiState.RunEnded -> if (menuOpen) MainMenu("Era ${s.run.era} has ended", s.legacy, "Continue run", "menu_continue", true, leaveMenu, { menuSettings = true }) else RunEndScreen(s, vm)
            }
            if (menuSettings) SettingsSheet(reducedMotion, vm::setReducedMotion, hapticsOn, vm::setHaptics, onDismiss = { menuSettings = false })
            // A save that failed leaves the last saved state on screen under this dialog; nothing is lost by dismissing it.
            (ui.op as? GameSession.Status.Failed)?.let { SaveFailureDialog(it.op, it.unconfirmed, onRetry = vm::retry, onKeepWorking = vm::dismissSaveFailure) }
        }
    }
}

/**
 * Main menu: the game's first screen. One primary action (a new game, or the saved run), Settings in the corner, and
 * "Abandon run" while a run is being planned ([onAbandon]); abandoning discards the run after a confirmation.
 */
@Composable
fun MainMenu(status: String, legacy: LegacyProfile?, primary: String, primaryTag: String, enabled: Boolean, onPrimary: () -> Unit, onSettings: () -> Unit, onAbandon: (() -> Unit)? = null) {
    var confirmAbandon by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().safeDrawingPadding().testTag("main_menu")) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.lg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Tiny Blacksmith", style = MaterialTheme.typography.headlineLarge, color = Gold, textAlign = TextAlign.Center)
            FramedPanel(modifier = Modifier.padding(top = Space.md).widthIn(max = 420.dp)) {
                ForgeScene(heat = 1f, reducedMotion = true)
                Text(status, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = Space.md))
                legacy?.let { Secondary("Legacy points: ${it.points} · eras survived: ${it.eras.size}", Modifier.align(Alignment.CenterHorizontally).padding(top = Space.xs)) }
                PrimaryActionButton(primary, onPrimary, Modifier.fillMaxWidth().padding(top = Space.md).testTag(primaryTag), enabled)
                if (onAbandon != null) OutlinedButton(onClick = { confirmAbandon = true }, enabled = enabled, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(top = Space.sm).heightIn(min = 48.dp).testTag("menu_abandon")) { Text("Abandon run") }
            }
        }
        IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.TopEnd).padding(Space.sm).size(48.dp).testTag("menu_settings")) { Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings") }
    }
    // A dialog is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    if (confirmAbandon && onAbandon != null) AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = { confirmAbandon = false },
        title = { Text("Abandon this run?") },
        text = { Text("The run is discarded and cannot be recovered. It earns no legacy points, and its legends and heroes are not recorded. Recipes already written in the journal stay.") },
        confirmButton = { TextButton(onClick = { confirmAbandon = false; onAbandon() }, modifier = Modifier.testTag("menu_abandon_confirm")) { Text("Abandon run") } },
        dismissButton = { TextButton(onClick = { confirmAbandon = false }) { Text("Keep playing") } },
    )
}
