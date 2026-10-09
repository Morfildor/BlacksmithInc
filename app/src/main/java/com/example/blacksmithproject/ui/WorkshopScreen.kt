package com.example.blacksmithproject.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.Panel
import com.example.blacksmithproject.R
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.CommissionStatus

/**
 * One portrait workshop with seven panels (GDD 12). Chrome is deliberately thin: a three-stat top bar, the panel,
 * one End Day action and the nav bar. Forge integrity and the siege live on the Forge and Town panels.
 */
@Composable
fun WorkshopScreen(s: UiState.Playing, vm: GameViewModel) {
    val state = s.state
    val reducedMotion by vm.settings.reducedMotion.collectAsStateWithLifecycle(initialValue = false)
    val seenTips by vm.settings.seenTips.collectAsStateWithLifecycle(initialValue = Tips.ALL)
    Scaffold(
        bottomBar = {
            Column {
                EndDayButton(s, vm)
                // 64dp instead of the 80dp default: the workshop needs the vertical space more than the nav bar does.
                NavigationBar(tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0), modifier = Modifier.navigationBarsPadding().height(64.dp)) {
                    Panel.entries.forEach { p ->
                        NavigationBarItem(
                            selected = s.panel == p,
                            onClick = { vm.selectPanel(p) },
                            icon = { PixelImage(panelIcon(p), 24.dp, description = null) },
                            // Shrinks rather than clips at large font scales (GDD 12: scalable text).
                            label = { Text(panelName(p), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp, stepSize = 0.5.sp)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TopBar(s)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Each tip belongs to one panel and shows one at a time; dismissal lives in settings.
            val tip = Tips.forPanel(s.panel).firstOrNull { it.id !in seenTips }
            when (s.panel) {
                Panel.FORGE -> ForgePanel(s, vm, reducedMotion, tip)
                else -> {
                    // Each panel keeps its own scroll position; switching panels must not land mid-list.
                    val scroll = remember(s.panel) { ScrollState(0) }
                    Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = Space.md, vertical = Space.sm)) {
                        tip?.let { TipBanner(it, vm) }
                        when (s.panel) {
                            Panel.HOME -> HomePanel(s, vm)
                            Panel.MARKET -> MarketPanel(s, vm)
                            Panel.TOWN -> TownPanel(s, vm)
                            Panel.JOURNAL -> JournalPanel(s, vm)
                            Panel.GAZETTE -> GazettePanel(s)
                            Panel.LEGACY -> LegacyPanel(s, vm, reducedMotion)
                            Panel.FORGE -> Unit
                        }
                        Spacer(Modifier.heightIn(min = Space.lg))
                    }
                }
            }
        }
    }
    s.revealWeaponId?.let { ForgeResultDialog(s, it, vm, reducedMotion) }
    s.showReport?.let { DayReportDialog(s, it, vm, reducedMotion) }
    if (s.pendingBlessingOffer()) BlessingDialog(s, vm)
    s.lastError?.let { ErrorDialog(it, vm::dismissError) }
}

private fun UiState.Playing.pendingBlessingOffer() =
    state.pendingBlessingOffer.isNotEmpty() && showReport == null && revealWeaponId == null && blessingOfferDismissedDay != state.day

/** First-run tips (GDD 3.3 onboarding): dismissed IDs live in settings, never in the save. */
object Tips {
    data class Tip(val id: String, val body: String)
    val FORGE = Tip("forge", "Pick a family, a core and an augment, then forge. Every valid forge yields a usable weapon.")
    val END_DAY = Tip("end_day", "At End Day heroes shop, then fight or rest, and factions press on the town. Read it all in the Gazette.")
    val MARKET = Tip("market", "Heroes buy what suits them and their purse. List weapons here at a price you like.")
    val ORDER = listOf(FORGE, END_DAY, MARKET)
    val ALL = ORDER.map { it.id }.toSet()
    fun forPanel(p: Panel): List<Tip> = when (p) { Panel.FORGE -> listOf(FORGE, END_DAY); Panel.MARKET -> listOf(MARKET); else -> emptyList() }
}

/** One slim line of guidance with a dismiss action; never a card that stays on every panel. */
@Composable
fun TipBanner(tip: Tips.Tip, vm: GameViewModel, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth().padding(bottom = Space.sm),
    ) {
        Row(Modifier.padding(start = Space.md, end = Space.sm, top = Space.sm, bottom = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(tip.body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { vm.dismissTip(tip.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Got it") }
        }
    }
}

/** The single rest action with its contextual sublabel (GDD 12 "End Day with contextual warning"). */
@Composable
private fun EndDayButton(s: UiState.Playing, vm: GameViewModel) {
    val state = s.state
    val note = when {
        state.pendingBlessingOffer.isNotEmpty() -> "A blessing awaits your choice"
        state.commissions.values.any { it.status == CommissionStatus.OFFERED } -> "A commission is waiting"
        state.overworkToday > 0 -> "Tomorrow starts ${state.overworkToday} energy short"
        state.energy > 0 -> "${state.energy} energy unused"
        else -> "Rest until dawn"
    }
    FilledTonalButton(
        onClick = vm::endDay,
        enabled = !s.busy,
        contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.xs),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xs).heightIn(min = 48.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("End Day", style = MaterialTheme.typography.titleMedium)
            Text(note, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun TopBar(s: UiState.Playing) {
    val st = s.state
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(R.drawable.icon_day, "Day", "Day ${st.day}")
        Stat(R.drawable.icon_gold, "Gold", "${st.gold}")
        Stat(R.drawable.icon_energy, "Energy", "${st.energy}")
    }
}

@Composable
private fun Stat(icon: Int, label: String, value: String) {
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "$label $value" }, verticalAlignment = Alignment.CenterVertically) {
        PixelImage(icon, 22.dp, description = null)
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

fun panelName(p: Panel) = when (p) {
    Panel.HOME -> "Home"; Panel.FORGE -> "Forge"; Panel.MARKET -> "Market"; Panel.TOWN -> "Town"; Panel.JOURNAL -> "Journal"; Panel.GAZETTE -> "Gazette"; Panel.LEGACY -> "Legacy"
}

private fun panelIcon(p: Panel) = when (p) {
    Panel.HOME -> R.drawable.icon_day; Panel.FORGE -> R.drawable.icon_nav_forge; Panel.MARKET -> R.drawable.icon_nav_market; Panel.TOWN -> R.drawable.icon_nav_town
    Panel.JOURNAL -> R.drawable.icon_nav_journal; Panel.GAZETTE -> R.drawable.icon_nav_gazette; Panel.LEGACY -> R.drawable.icon_nav_legacy
}

/** Section heading: serif title with a generous top gap so sections read as separate blocks. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = Space.lg, bottom = Space.sm).semantics { heading() })
}

/** One line of secondary text in the muted colour; the only way secondary text is styled. */
@Composable
fun Secondary(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
