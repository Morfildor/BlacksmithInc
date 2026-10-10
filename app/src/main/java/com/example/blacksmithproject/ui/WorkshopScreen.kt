package com.example.blacksmithproject.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.Dest
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.RecordsPage
import com.example.blacksmithproject.R
import com.example.blacksmithproject.Sheet
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.detail.HeroDetailSheet
import com.example.blacksmithproject.ui.detail.ItemDetailSheet
import com.example.blacksmithproject.ui.detail.customerSnapshot
import com.example.blacksmithproject.ui.detail.heroDetail
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.detail.toCommand
import com.example.blacksmithproject.ui.detail.weaponSnapshot
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.ui.theme.BronzeContainer
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.ForgePanel
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.shopday.LeadKind

/**
 * One portrait workshop with four destinations (plan 1.2) and a settings sheet behind a gear. Chrome is deliberately
 * thin: a three-stat top bar, the destination, one End Day action and the bar. Forge integrity and the siege live on
 * the Forge and Town destinations.
 */
@Composable
fun WorkshopScreen(s: UiState.Playing, vm: GameViewModel, onMainMenu: () -> Unit = {}) {
    val state = s.state
    val reducedMotion by vm.settings.reducedMotion.collectAsStateWithLifecycle(initialValue = false)
    val seenTips by vm.settings.seenTips.collectAsStateWithLifecycle(initialValue = Tips.ALL)
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var storageOpen by rememberSaveable { mutableStateOf(false) }
    var suppliesOpen by rememberSaveable { mutableStateOf(false) }
    // Back returns to Shop from any other destination; on Shop it is not handled here, so it leaves the app.
    BackHandler(enabled = s.dest != Dest.SHOP) { vm.back() }
    Scaffold(
        bottomBar = {
            Column {
                EndDayButton(s, vm)
                DestinationBar(s.dest, vm::selectDest)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TopBar(s, onSettings = { settingsOpen = true })
            HorizontalDivider(color = Bronze)
            // Each tip belongs to one destination and shows one at a time; dismissal lives in settings.
            val tip = Tips.forDest(s.dest).firstOrNull { it.id !in seenTips }
            when (s.dest) {
                Dest.SHOP -> ShopPanel(
                    s.shop, s.busy, reducedMotion,
                    onLead = { lead ->
                        when (lead.kind) {
                            LeadKind.CHOOSE_BLESSING -> vm.reopenBlessingOffer()
                            LeadKind.LIST_STOCK -> storageOpen = true
                            LeadKind.PRICES_TOO_HIGH -> lead.weaponId?.let { vm.openSheet(Sheet.Item(it)) }
                            LeadKind.ANSWER_WANT -> lead.familyId?.let(vm::forgeFamily) ?: vm.selectDest(Dest.FORGE)
                            else -> vm.selectDest(Dest.FORGE)
                        }
                    },
                    onOpenBlade = { vm.openSheet(Sheet.Item(it)) },
                    onOpenHero = { vm.openSheet(Sheet.Hero(it)) },
                    onAnswer = { id, accept -> vm.dispatch(if (accept) Command.AcceptCommission(id) else Command.DeclineCommission(id)) },
                    onOpenStorage = { storageOpen = true },
                    onOpenNews = { vm.selectRecords(RecordsPage.GAZETTE) },
                    tip = tip?.let { { TipBanner(it, vm) } },
                    onForgeThis = vm::forgeFor,
                    onForgeWant = vm::forgeFamily,
                    onOpenSupplies = { suppliesOpen = true },
                )
                Dest.FORGE -> ForgePanel(s, vm, reducedMotion, tip, onOpenSupplies = { suppliesOpen = true })
                Dest.RECORDS -> RecordsPanel(s, vm)
                Dest.TOWN -> TownPanel(s, vm)
            }
        }
    }
    val haptics by vm.settings.haptics.collectAsStateWithLifecycle(initialValue = false)
    if (settingsOpen) SettingsSheet(reducedMotion, vm::setReducedMotion, haptics, vm::setHaptics, onDismiss = { settingsOpen = false }, onMainMenu = { settingsOpen = false; onMainMenu() })
    if (storageOpen) {
        StorageSheet(
            s.shop.storage, shelfFree = s.shop.slots - s.shop.shelf.size, busy = s.busy,
            onOpenBlade = { vm.openSheet(Sheet.Item(it)) },
            onList = { id, price -> vm.dispatch(Command.ToggleShelf(id, true, price)) },
            onDismiss = { storageOpen = false },
        )
    }
    if (suppliesOpen) SuppliesSheet(s, vm, onDismiss = { suppliesOpen = false })
    s.sheet?.let { DetailSheet(s, it, vm) }
    s.revealWeaponId?.let { ForgeResultDialog(s, it, vm, reducedMotion) }
    if (s.pendingBlessingOffer()) BlessingDialog(s, vm)
    s.lastError?.let { ErrorDialog(it, vm::dismissError) }
}

/**
 * The open hero or blade sheet, rebuilt from the save on every change. One that has left the save opens from the last
 * day's record; with neither, the sheet closes. Stock can be changed here while no day report is on screen.
 */
@Composable
private fun DetailSheet(s: UiState.Playing, sheet: Sheet, vm: GameViewModel) {
    val st = s.state
    val openHero = { id: HeroId -> vm.openSheet(Sheet.Hero(id)) }
    when (sheet) {
        is Sheet.Hero -> {
            val detail = remember(st, sheet) { vm.engine.heroDetail(st, sheet.id, st.lastResolution?.takeIf { sheet.id !in st.heroes }?.customerSnapshot(sheet.id)) }
            if (detail == null) LaunchedEffect(sheet) { vm.closeSheet() }
            else HeroDetailSheet(detail, openHero, onOpenItem = { vm.openSheet(Sheet.Item(it)) }, onDismiss = vm::closeSheet)
        }
        is Sheet.Item -> {
            val detail = remember(st, sheet) { vm.engine.itemDetail(st, sheet.id, st.lastResolution?.takeIf { sheet.id !in st.weapons }?.weaponSnapshot(sheet.id)) }
            if (detail == null) LaunchedEffect(sheet) { vm.closeSheet() }
            else ItemDetailSheet(
                detail, planning = true, openHero,
                onStock = { vm.dispatch(it.toCommand(sheet.id)) },
                onDismiss = vm::closeSheet, enabled = !s.busy,
            )
        }
    }
}

/**
 * The four destinations. Every label has the same fixed style and never shrinks to fit: a larger font scale makes the
 * label taller, not smaller, and the four names are short enough to stay on one line.
 */
@Composable
fun DestinationBar(selected: Dest, onSelect: (Dest) -> Unit, modifier: Modifier = Modifier) {
    // 64dp instead of the 80dp default: the workshop needs the vertical space more than the bar does.
    // A panel under a bronze rule; the chosen destination is a lit plate with a gold edge, not Material's pill.
    NavigationBar(
        containerColor = ForgePanel, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.navigationBarsPadding().height(64.dp).drawWithContent { drawContent(); drawLine(Bronze, Offset.Zero, Offset(size.width, 0f), strokeWidth = 2.dp.toPx()) },
    ) {
        Dest.entries.forEach { d ->
            NavigationBarItem(
                selected = selected == d,
                onClick = { onSelect(d) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, selectedTextColor = Gold, unselectedTextColor = CreamMuted),
                modifier = Modifier.testTag("nav_${d.name.lowercase()}").drawBehind {
                    if (selected == d) {
                        drawRect(BronzeContainer)
                        drawRect(Gold, size = Size(size.width, 2.dp.toPx()))
                    }
                },
                icon = { PixelImage(destIcon(d), 24.dp, description = null) },
                label = { Text(destName(d), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, modifier = Modifier.testTag("nav_label_${d.name.lowercase()}")) },
            )
        }
    }
}

/** The three segments of Records (News, Journal, Legacy) as one row of exclusive buttons. */
@Composable
fun SegmentRow(selected: RecordsPage, onSelect: (RecordsPage) -> Unit, modifier: Modifier = Modifier) {
    val pages = RecordsPage.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().padding(bottom = Space.sm)) {
        pages.forEachIndexed { i, p ->
            SegmentedButton(
                selected = selected == p,
                onClick = { onSelect(p) },
                shape = SegmentedButtonDefaults.itemShape(i, pages.size, MaterialTheme.shapes.small),
                modifier = Modifier.heightIn(min = 48.dp).testTag("page_${p.name.lowercase()}"),
            ) { Text(when (p) { RecordsPage.GAZETTE -> "News"; RecordsPage.JOURNAL -> "Journal"; RecordsPage.LEGACY -> "Legacy" }, maxLines = 1) }
        }
    }
}

private fun UiState.Playing.pendingBlessingOffer() =
    state.pendingBlessingOffer.isNotEmpty() && revealWeaponId == null && blessingOfferDismissedDay != state.day

/** First-run tips (GDD 3.3 onboarding): dismissed IDs live in settings, never in the save. */
object Tips {
    data class Tip(val id: String, val body: String)
    val FORGE = Tip("forge", "Pick a family, a core and an augment, then forge. Every valid forge yields a usable weapon.")
    val END_DAY = Tip("end_day", "At End Day heroes shop, then fight or rest, and factions press on the town. Read it all in the Gazette.")
    val MARKET = Tip("market", "Heroes buy what suits them and their purse. List weapons here at a price you like.")
    val ORDER = listOf(FORGE, END_DAY, MARKET)
    val ALL = ORDER.map { it.id }.toSet()
    fun forDest(d: Dest): List<Tip> = when (d) { Dest.FORGE -> listOf(FORGE, END_DAY); Dest.SHOP -> listOf(MARKET); else -> emptyList() }
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
    val haptics = LocalHaptics.current
    val note = when {
        state.pendingBlessingOffer.isNotEmpty() -> "A blessing awaits your choice"
        state.commissions.values.any { it.status == CommissionStatus.OFFERED } -> "A commission is waiting"
        state.overworkToday > 0 -> "Tomorrow starts ${state.overworkToday} energy short"
        state.energy > 0 -> "${state.energy} energy unused"
        else -> "Rest until dawn"
    }
    PrimaryActionButton(
        "End Day", { haptics.play(Moment.END_DAY); vm.endDay() },
        Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xs).testTag("end_day"),
        enabled = !s.busy, detail = note,
    )
}

@Composable
private fun TopBar(s: UiState.Playing, onSettings: () -> Unit) {
    val st = s.state
    Row(
        Modifier.fillMaxWidth().background(ForgePanel).padding(start = Space.md, end = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Stat(R.drawable.icon_day, "Day", "Day ${st.day}")
        Stat(R.drawable.icon_gold, "Gold", "${st.gold}", Gold)
        Stat(R.drawable.icon_energy, "Energy", "${st.energy}")
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp).testTag("nav_settings")) { Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = Gold) }
    }
}

@Composable
private fun Stat(icon: Int, label: String, value: String, color: Color = Cream) {
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "$label $value" }, verticalAlignment = Alignment.CenterVertically) {
        PixelImage(icon, 22.dp, description = null)
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

fun destName(d: Dest) = when (d) { Dest.SHOP -> "Shop"; Dest.FORGE -> "Forge"; Dest.TOWN -> "Town"; Dest.RECORDS -> "Records" }

private fun destIcon(d: Dest) = when (d) {
    Dest.SHOP -> R.drawable.icon_nav_market; Dest.FORGE -> R.drawable.icon_nav_forge; Dest.TOWN -> R.drawable.icon_nav_town; Dest.RECORDS -> R.drawable.icon_nav_journal
}

/** Section heading: a gold serif title over a bronze rule, with a generous top gap so sections read as separate blocks. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleLarge, color = Gold,
        modifier = modifier.padding(top = Space.lg, bottom = Space.sm).semantics { heading() }.drawBehind {
            drawLine(BronzeDeep, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        }.padding(bottom = 2.dp),
    )
}

/** One line of secondary text in the muted colour; the only way secondary text is styled. */
@Composable
fun Secondary(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
