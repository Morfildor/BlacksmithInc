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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Space

@Composable
fun TinyBlacksmithApp(vm: GameViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = ui) {
            UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Title -> TitleScreen(s, onNewRun = vm::newRun, onContinue = vm::continueRun)
            is UiState.Playing -> WorkshopScreen(s, vm)
            is UiState.RunEnded -> RunEndScreen(s, vm)
        }
    }
}

/** Title: one primary action. Continue leads when a run is saved, since a new run writes over it. */
@Composable
fun TitleScreen(s: UiState.Title, onNewRun: () -> Unit, onContinue: () -> Unit) {
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
        if (s.hasSavedRun) {
            Button(onClick = onContinue, modifier = Modifier.heightIn(min = 52.dp)) { Text("Continue", style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(onClick = onNewRun, modifier = Modifier.padding(top = Space.sm).heightIn(min = 48.dp)) { Text(newRunLabel) }
        } else {
            Button(onClick = onNewRun, modifier = Modifier.heightIn(min = 52.dp)) { Text(newRunLabel, style = MaterialTheme.typography.titleMedium) }
        }
    }
}
