package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState

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

@Composable
fun TitleScreen(s: UiState.Title, onNewRun: () -> Unit, onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ForgeScene(heat = 1f, reducedMotion = true, modifier = Modifier.padding(bottom = 16.dp))
        Text("Tiny Blacksmith", style = MaterialTheme.typography.headlineLarge)
        Text("Era ${s.legacy.nextEra} awaits", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text("Legacy points: ${s.legacy.points} · eras survived: ${s.legacy.eras.size}", modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
        if (s.hasSavedRun) OutlinedButton(onClick = onContinue, modifier = Modifier.padding(bottom = 8.dp)) { Text("Continue") }
        Button(onClick = onNewRun) { Text(if (s.legacy.eras.isEmpty()) "Light the forge" else "Begin a new era") }
    }
}
