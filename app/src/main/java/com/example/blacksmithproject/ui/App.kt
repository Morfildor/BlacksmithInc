package com.example.blacksmithproject.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.GameSession
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.R
import com.example.blacksmithproject.ui.shopday.ShopDayHost
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.LegacyProfile
import kotlin.math.ceil

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
    CompositionLocalProvider(LocalHaptics provides haptics) {
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
            when (val s = ui) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.LoadFailed -> LoadFailedScreen(s.failure, s.working, onRetry = vm::retry, onStartOver = vm::startOver)
                is UiState.Title -> MainMenu("Era ${s.legacy.nextEra} awaits", s.legacy, "New game", "title_new_run", s.op !is GameSession.Status.Working, onPrimary = { leaveMenu(); vm.newRun() }, onSettings = { menuSettings = true })
                is UiState.Playing -> if (menuOpen) MainMenu("Era ${s.state.era} · Day ${s.state.day}", null, "Continue run", "menu_continue", !s.busy, leaveMenu, { menuSettings = true }, onAbandon = vm::abandonRun) else screens.SaveableStateProvider(play!!) { WorkshopScreen(s, vm, onMainMenu = { menuOpen = true }) }
                is UiState.ShopDay -> if (menuOpen) MainMenu("Era ${s.state.era} · Day ${s.state.day}", null, "Continue run", "menu_continue", true, leaveMenu, { menuSettings = true }) else screens.SaveableStateProvider(play!!) { ShopDayHost(s, vm, onMainMenu = { menuOpen = true }) }
                is UiState.RunEnded -> if (menuOpen) MainMenu("Era ${s.run.era} has ended", s.legacy, "Continue run", "menu_continue", true, leaveMenu, { menuSettings = true }) else RunEndScreen(s, vm)
            }
            if (menuSettings) SettingsSheet(reducedMotion, vm::setReducedMotion, hapticsOn, vm::setHaptics, onDismiss = { menuSettings = false })
            // A save that failed leaves the last saved state on screen under this dialog; nothing is lost by dismissing it.
            (ui.op as? GameSession.Status.Failed)?.let { SaveFailureDialog(it.op, it.unconfirmed, onRetry = vm::retry, onKeepWorking = vm::dismissSaveFailure) }
        }
    }
}

/**
 * The title's picture: the workshop at night at [scale] device pixels per art pixel, centred and cropped to the box. A
 * scrim under the status bar keeps the gear readable, and the foot fades into the ground so the name can stand on it.
 */
@Composable
private fun TitleArt(bitmap: ImageBitmap, scale: Int, modifier: Modifier = Modifier) {
    val ground = MaterialTheme.colorScheme.background
    Canvas(modifier.clipToBounds().clearAndSetSemantics {}) {
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        drawImage(bitmap, dstOffset = IntOffset(((size.width - w) / 2f).toInt(), ((size.height - h) / 2f).toInt()), dstSize = IntSize(w, h), filterQuality = FilterQuality.None)
        drawRect(Brush.verticalGradient(0f to ground.copy(alpha = 0.6f), 0.22f to Color.Transparent, 0.5f to Color.Transparent, 0.88f to ground.copy(alpha = 0.9f), 1f to ground))
    }
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
 * Main menu: the game's first screen. The workshop at night with the game's name on it, the era (and the legacy, between
 * runs) on a plate, and the actions at the foot within reach of a thumb: one primary action (a new game, or the saved
 * run) and "Abandon run" while a run is being planned ([onAbandon]); abandoning discards the run after a confirmation.
 * Settings is in the corner. The gaps are weights, so a taller screen spreads the three parts and large text scrolls.
 */
@Composable
fun MainMenu(status: String, legacy: LegacyProfile?, primary: String, primaryTag: String, enabled: Boolean, onPrimary: () -> Unit, onSettings: () -> Unit, onAbandon: (() -> Unit)? = null) {
    var confirmAbandon by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("main_menu")) {
        // One whole step past the scale that covers the width, more on a tall screen: the picture carries over a third of the height and loses little at the sides.
        val art = ImageBitmap.imageResource(R.drawable.bg_title_workshop_night)
        val scale = maxOf(ceil(constraints.maxWidth / art.width.toFloat()).toInt() + 1, ceil(constraints.maxHeight * 0.36f / art.height).toInt())
        val artHeight = with(LocalDensity.current) { minOf(art.height * scale, (constraints.maxHeight * 0.46f).toInt()).toDp() }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(artHeight)) {
                TitleArt(art, scale, Modifier.matchParentSize())
                Text(
                    "Tiny Blacksmith", style = MaterialTheme.typography.headlineLarge.copy(shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)), color = Gold, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Space.md).semantics { heading() }.drawBehind {
                        // A bronze rule under the name, fading out at both ends.
                        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Bronze, Color.Transparent), size.width * 0.15f, size.width * 0.85f), Offset(size.width * 0.15f, size.height - 1.dp.toPx()), Size(size.width * 0.7f, 1.dp.toPx()))
                    }.padding(bottom = Space.sm),
                )
            }
            Spacer(Modifier.weight(2f).heightIn(min = Space.md))
            Column(Modifier.padding(horizontal = Space.lg).widthIn(max = 420.dp).fillMaxWidth().forgeFrame().padding(Space.md), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(status, style = MaterialTheme.typography.titleLarge, color = Cream, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                legacy?.let {
                    HorizontalDivider(Modifier.padding(vertical = Space.sm), color = BronzeDeep)
                    Row(Modifier.fillMaxWidth()) {
                        LegacyCount(it.points, "Legacy points", Modifier.weight(1f))
                        LegacyCount(it.eras.size, "Eras survived", Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.weight(3f).heightIn(min = Space.md))
            Column(Modifier.padding(horizontal = Space.lg).padding(bottom = Space.lg).widthIn(max = 420.dp).fillMaxWidth()) {
                PrimaryActionButton(primary, onPrimary, Modifier.fillMaxWidth().testTag(primaryTag), enabled)
                if (onAbandon != null) OutlinedButton(onClick = { confirmAbandon = true }, enabled = enabled, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(top = Space.sm).heightIn(min = 48.dp).testTag("menu_abandon")) { Text("Abandon run") }
            }
        }
        IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(Space.sm).size(48.dp).testTag("menu_settings")) { Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings") }
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
