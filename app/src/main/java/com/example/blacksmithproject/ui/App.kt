package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.GameSession
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Space

@Composable
fun TinyBlacksmithApp(vm: GameViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val hapticsOn by vm.settings.haptics.collectAsStateWithLifecycle(initialValue = false)
    val haptics = rememberHaptics(hapticsOn)
    // The toll sounds when a run ends while playing, not each time the game is reopened onto an ended run.
    var wasPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(ui is UiState.RunEnded, ui is UiState.Playing) {
        if (ui is UiState.RunEnded && wasPlaying) haptics.play(Moment.RUN_END)
        wasPlaying = ui is UiState.Playing
    }
    // Test tags double as Android resource IDs for uiautomator scripts (tools/emulator).
    CompositionLocalProvider(LocalHaptics provides haptics) {
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
            when (val s = ui) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.LoadFailed -> LoadFailedScreen(s.failure, s.working, onRetry = vm::retry, onStartOver = vm::startOver)
                is UiState.Title -> TitleScreen(s, onNewRun = vm::newRun)
                is UiState.Playing -> WorkshopScreen(s, vm)
                is UiState.RunEnded -> RunEndScreen(s, vm)
            }
            // A save that failed leaves the last saved state on screen under this dialog; nothing is lost by dismissing it.
            (ui.op as? GameSession.Status.Failed)?.let { SaveFailureDialog(it.op, it.unconfirmed, onRetry = vm::retry, onKeepWorking = vm::dismissSaveFailure) }
        }
    }
}

/** Title: one primary action. It shows only when no run is saved; a saved run opens straight into the workshop. */
@Composable
fun TitleScreen(s: UiState.Title, onNewRun: () -> Unit) {
    val newRunLabel = if (s.legacy.eras.isEmpty()) "Light the forge" else "Begin a new era"
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Space.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ForgeScene(heat = 1f, reducedMotion = true, modifier = Modifier.padding(bottom = Space.md))
        Text("Tiny Blacksmith", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text("Era ${s.legacy.nextEra} awaits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Space.sm))
        Secondary("Legacy points: ${s.legacy.points} · eras survived: ${s.legacy.eras.size}", Modifier.padding(top = Space.xs, bottom = Space.lg))
        Button(onClick = onNewRun, enabled = s.op !is GameSession.Status.Working, modifier = Modifier.heightIn(min = 52.dp).testTag("title_new_run")) { Text(newRunLabel, style = MaterialTheme.typography.titleMedium) }
    }
}
