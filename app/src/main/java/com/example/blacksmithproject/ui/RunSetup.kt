package com.example.blacksmithproject.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Space

/**
 * The step between "New game" and the first morning: which charter the era begins under. Its own screen so the four
 * choices have the room to be compared; the title keeps only the picture and the way in. The list scrolls above one
 * pinned action, which names what it starts. Back returns to the title without starting anything.
 */
@Composable
fun RunSetup(vm: GameViewModel, era: Int, enabled: Boolean, onBack: () -> Unit, onBegin: () -> Unit) {
    BackHandler(onBack = onBack)
    val charters = vm.engine.content.guild?.charters.orEmpty()
    val selected by vm.charter.collectAsStateWithLifecycle()
    val chosen = charters.firstOrNull { it.id == selected }
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("run_setup"), horizontalAlignment = Alignment.CenterHorizontally) {
        val scroll = rememberScrollState()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).bottomFade(scroll.canScrollForward)
                .padding(horizontal = Space.md).padding(top = Space.xs, bottom = Space.lg),
        ) {
            InlineActionButton("Back", onBack, Modifier.testTag("setup_back"))
            Text("How era $era begins", style = MaterialTheme.typography.headlineMedium, color = Cream, modifier = Modifier.padding(top = Space.xs).semantics { heading() })
            Text(
                "A charter adds a guild: heroes you hire, arm with loaned weapons and send on quests. Without a charter, you run only the shop.",
                style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.padding(top = Space.xs, bottom = Space.md),
            )
            CharterPicker(charters, selected, vm::selectCharter, vm.engine.content, title = null)
        }
        PrimaryActionButton(
            chosen?.let { "Begin under ${it.name}" } ?: "Begin a classic run", onBegin,
            Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = Space.md, vertical = Space.sm).testTag("setup_begin"), enabled = enabled,
        )
    }
}
