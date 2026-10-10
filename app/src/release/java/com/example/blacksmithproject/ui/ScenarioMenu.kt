package com.example.blacksmithproject.ui

import androidx.compose.runtime.Composable
import com.example.blacksmithproject.GameViewModel

/** Release builds have no Scenarios entry and no scenario assets; the debug source set holds the real menu under this name. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ScenarioMenu(vm: GameViewModel, hasRun: Boolean, enabled: Boolean, onLoaded: () -> Unit) {}
