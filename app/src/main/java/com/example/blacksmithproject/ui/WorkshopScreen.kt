package com.example.blacksmithproject.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.R
import com.example.blacksmithproject.Panel
import com.example.blacksmithproject.UiState
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.model.CommissionStatus

/** One portrait workshop with panels (GDD 12). The top strip, forge art and End Day are always visible. */
@Composable
fun WorkshopScreen(s: UiState.Playing, vm: GameViewModel) {
    val state = s.state
    val reducedMotion by vm.settings.reducedMotion.collectAsStateWithLifecycle(initialValue = false)
    val seenTips by vm.settings.seenTips.collectAsStateWithLifecycle(initialValue = Tips.ALL)
    Scaffold(
        bottomBar = {
            Column {
                NavigationBar {
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
                val warning = when {
                    state.pendingBlessingOffer.isNotEmpty() -> "A blessing awaits your choice"
                    state.commissions.values.any { it.status == CommissionStatus.OFFERED } -> "A commission is waiting"
                    state.energy > 0 -> "${state.energy} energy unused"
                    else -> null
                }
                if (warning != null) {
                    Text(
                        "Before you rest: $warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }
                Button(
                    onClick = vm::endDay,
                    enabled = !s.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 6.dp),
                ) { Text("End Day ${state.day}", style = MaterialTheme.typography.titleMedium) }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TopStrip(s)
            ForgeScene(heat = state.energy / vm.engine.config.baseDailyEnergy.toFloat(), reducedMotion = reducedMotion)
            // Each panel keeps its own scroll position; switching panels must not land mid-list.
            val scroll = remember(s.panel) { ScrollState(0) }
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp)) {
                // One onboarding tip at a time, in order; dismissing one reveals the next.
                Tips.ORDER.firstOrNull { it.id !in seenTips }?.let { TipCard(it, vm) }
                when (s.panel) {
                    Panel.FORGE -> ForgePanel(s, vm)
                    Panel.MARKET -> MarketPanel(s, vm)
                    Panel.TOWN -> TownPanel(s, vm)
                    Panel.JOURNAL -> JournalPanel(s, vm)
                    Panel.GAZETTE -> GazettePanel(s)
                    Panel.LEGACY -> LegacyPanel(s, vm, reducedMotion)
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
    data class Tip(val id: String, val title: String, val body: String)
    val FORGE = Tip("forge", "Your first weapon", "Pick a family, a core metal and an augment, then tap Forge weapon. Every valid forge yields a usable blade.")
    val END_DAY = Tip("end_day", "What happens at End Day", "Heroes shop, then fight, patrol or rest; factions press on the town. Read it all in the Gazette at dawn.")
    val MARKET = Tip("market", "Selling to heroes", "Heroes buy what suits them and their purse. List weapons in the Market at a price you like.")
    val ORDER = listOf(FORGE, END_DAY, MARKET)
    val ALL = ORDER.map { it.id }.toSet()
}

@Composable
private fun TipCard(tip: Tips.Tip, vm: GameViewModel, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tip.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = { vm.dismissTip(tip.id) }) { Text("Got it") }
            }
            Text(tip.body, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 10.dp))
        }
    }
}

@Composable
private fun TopStrip(s: UiState.Playing) {
    val st = s.state
    val faction = st.factions.values.maxByOrNull { it.pressure }
    Surface(tonalElevation = 2.dp) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Stat(R.drawable.icon_day, "Day", "${st.day}")
            Stat(R.drawable.icon_gold, "Gold", "${st.gold}")
            Stat(R.drawable.icon_energy, "Energy", if (st.overworkToday > 0) "${st.energy} (−${st.overworkToday} tomorrow)" else "${st.energy}")
            Stat(R.drawable.icon_integrity, "Forge", "${st.town.integrity}")
            Stat(R.drawable.icon_militia, "Siege day ${st.town.nextSiegeDay}", faction?.let { Battle.describePressure(it.pressure) } ?: "quiet")
        }
    }
}

@Composable
private fun Stat(icon: Int, label: String, value: String) {
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "$label $value" }, verticalAlignment = Alignment.CenterVertically) {
        PixelImage(icon, 20.dp, description = null)
        Spacer(Modifier.width(3.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

fun panelName(p: Panel) = when (p) {
    Panel.FORGE -> "Forge"; Panel.MARKET -> "Market"; Panel.TOWN -> "Town"; Panel.JOURNAL -> "Journal"; Panel.GAZETTE -> "Gazette"; Panel.LEGACY -> "Legacy"
}

private fun panelIcon(p: Panel) = when (p) {
    Panel.FORGE -> R.drawable.icon_nav_forge; Panel.MARKET -> R.drawable.icon_nav_market; Panel.TOWN -> R.drawable.icon_nav_town
    Panel.JOURNAL -> R.drawable.icon_nav_journal; Panel.GAZETTE -> R.drawable.icon_nav_gazette; Panel.LEGACY -> R.drawable.icon_nav_legacy
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp).semantics { heading() })
}
