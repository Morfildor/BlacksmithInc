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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import com.example.blacksmithproject.ui.detail.HeroDetailContent
import com.example.blacksmithproject.ui.detail.HeroDetailSheet
import com.example.blacksmithproject.ui.detail.ItemDetailContent
import com.example.blacksmithproject.ui.detail.ItemDetailSheet
import com.example.blacksmithproject.ui.detail.StockAction
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
import com.tinyblacksmith.core.engine.Encounters
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommissionStatus
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.MaterialId
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
    // The material a "Restock" was tapped for: Supplies opens on its row. Any other way in opens at the top.
    var suppliesFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var boardOpen by rememberSaveable { mutableStateOf(false) }
    val workshopHaptics = LocalHaptics.current
    // Back returns to Shop from any other destination, and from Shop it opens the main menu. Sheets and dialogs are
    // their own windows and take Back first.
    BackHandler { if (!vm.back()) onMainMenu() }
    // Each destination keeps its own scroll position and what is open in it while the player is somewhere else.
    val positions = rememberSaveableStateHolder()
    // What just happened to a blade (listed, stored, repriced, melted down): said once, over whichever destination is
    // open. Under a sheet it could not be seen, so there the sheet says it and this waits.
    val covered = storageOpen || s.sheet != null
    val notices = remember { SnackbarHostState() }
    LaunchedEffect(s.notice, covered) {
        val notice = s.notice?.takeIf { !covered } ?: return@LaunchedEffect
        try { notices.showSnackbar(notice, duration = SnackbarDuration.Long) } finally { vm.dismissNotice(notice) }
    }
    Scaffold(
        snackbarHost = {
            SnackbarHost(notices) { Snackbar(it, Modifier.testTag("notice"), shape = MaterialTheme.shapes.small, containerColor = BronzeContainer, contentColor = Cream) }
        },
        bottomBar = {
            Column {
                // End Day is the Shop's way forward. The Forge keeps it under "More", and beside its action when no forge is left today.
                if (s.dest == Dest.SHOP) EndDayButton(s, vm, primary = true)
                DestinationBar(s.dest, vm::selectDest)
            }
        },
    ) { padding -> positions.SaveableStateProvider(s.dest) {
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
                            LeadKind.ANSWER_REQUEST -> boardOpen = true
                            LeadKind.PRICES_TOO_HIGH -> lead.weaponId?.let { vm.openSheet(Sheet.Item(it)) }
                            LeadKind.ANSWER_WANT -> lead.familyId?.let(vm::forgeFamily) ?: vm.selectDest(Dest.FORGE)
                            else -> vm.selectDest(Dest.FORGE)
                        }
                    },
                    onOpenBlade = { vm.openSheet(Sheet.Item(it)) },
                    onOpenBoard = { boardOpen = true },
                    onOpenStorage = { storageOpen = true },
                    onOpenNews = { vm.selectRecords(RecordsPage.GAZETTE) },
                    tip = tip?.let { { TipBanner(it, vm) } },
                    // On the day itself the siege has its own row under the counter, so the strip does not say it twice.
                    strip = if (s.shop.threat?.today == true) null else ({ ThreatStrip(s, "shop_threat", s.shop.threat?.note) { vm.selectDest(Dest.TOWN) } }),
                    onOpenSupplies = { suppliesOpen = true },
                    visitor = s.encounter, onOpenVisitor = { vm.openSheet(Sheet.Visitor) },
                    relics = s.relics, relicSlots = if (vm.engine.content.relics.isEmpty()) 0 else vm.engine.config.depth.relicSlots,
                    relicOffer = state.pendingRelicOffer.isNotEmpty(), onOpenRelicOffer = vm::reopenRelicOffer,
                )
                Dest.FORGE -> ForgePanel(s, vm, reducedMotion, tip, onOpenSupplies = { suppliesFocus = it?.value; suppliesOpen = true }, onOpenBoard = { boardOpen = true }, onEndDay = { workshopHaptics.play(Moment.END_DAY); vm.endDay() })
                Dest.RECORDS -> RecordsPanel(s, vm)
                Dest.TOWN -> TownPanel(s, vm)
            }
        }
    } }
    val haptics by vm.settings.haptics.collectAsStateWithLifecycle(initialValue = false)
    if (settingsOpen) SettingsSheet(reducedMotion, vm::setReducedMotion, haptics, vm::setHaptics, onDismiss = { settingsOpen = false }, onMainMenu = { settingsOpen = false; onMainMenu() })
    if (storageOpen) {
        StorageSheet(
            s.shop.storage, shelfFree = s.shop.slots - s.shop.shelf.size, busy = s.busy,
            onOpenBlade = { vm.openSheet(Sheet.Item(it)) },
            onList = { id, price -> vm.stock(id, StockAction.ListAt(price)) },
            onDismiss = { storageOpen = false; vm.closeSheet() },
            terms = with(vm.engine.config) { BulkTerms(salvageEnergy, state.energy, maxOverworkPerDay - state.overworkToday, armoryMax - state.town.armory) },
            onBulk = { action, ids -> vm.dispatchAll(ids.map { action.toCommand(it) }) },
            scrapBack = { ids -> scrapBackText(vm.engine, state, ids) },
            onScrap = { ids -> vm.dispatch(Command.Scrap(ids)) },
            notice = s.notice,
            // A blade opened from the list is shown in this same sheet, not in a second one over it.
            detail = s.sheet?.let { sheet -> { DetailSheet(s, sheet, vm, inStorage = true) } }, onBack = vm::closeSheet,
        )
    }
    if (suppliesOpen) SuppliesSheet(s, vm, onDismiss = { suppliesOpen = false; suppliesFocus = null }, focus = suppliesFocus?.let(::MaterialId))
    // Under a hero's sheet opened from it the board waits, as Storage does; "Forge this" closes it onto the Forge.
    if (boardOpen && s.sheet == null) CommissionBoardSheet(
        remember(s.shop.requests, s.shop.wants) { s.shop.board() }, s.shop.requestSlots, s.busy, s.draft.commissionId,
        onOpenHero = { vm.openSheet(Sheet.Hero(it)) },
        onAnswer = { id, accept -> vm.dispatch(if (accept) Command.AcceptCommission(id) else Command.DeclineCommission(id)) },
        onForgeThis = { boardOpen = false; vm.forgeFor(it) }, onForgeWant = { boardOpen = false; vm.forgeFamily(it) },
        onDismiss = { boardOpen = false },
    )
    if (!storageOpen) s.sheet?.let { DetailSheet(s, it, vm) }
    s.revealWeaponId?.let { ForgeResultDialog(s, it, vm, reducedMotion) }
    if (s.pendingBlessingOffer()) BlessingDialog(s, vm)
    // One offer at a time: the blessing first, then the relic.
    else if (s.pendingRelicOffer()) RelicDialog(
        state.pendingRelicOffer.mapNotNull { vm.engine.content.relic(it) }, s.relics, vm.engine.config.depth.relicSlots, s.busy,
        onChoose = vm::chooseRelic, onDecline = vm::declineRelics, onLater = vm::dismissRelicOffer,
    )
    s.lastError?.let { ErrorDialog(it, vm::dismissError) }
}

/**
 * The open hero or blade sheet, rebuilt from the save on every change. One that has left the save opens from the last
 * day's record; with neither, the sheet closes. Stock can be changed here while no day report is on screen.
 * [inStorage]: only the body, for the Storage sheet that is already open ("Back to storage" then returns to its list).
 */
@Composable
private fun DetailSheet(s: UiState.Playing, sheet: Sheet, vm: GameViewModel, inStorage: Boolean = false) {
    val st = s.state
    val openHero = { id: HeroId -> vm.openSheet(Sheet.Hero(id)) }
    when (sheet) {
        is Sheet.Hero -> {
            val detail = remember(st, sheet) { vm.engine.heroDetail(st, sheet.id, st.lastResolution?.takeIf { sheet.id !in st.heroes }?.customerSnapshot(sheet.id)) }
            if (detail == null) LaunchedEffect(sheet) { vm.closeSheet() }
            else if (inStorage) HeroDetailContent(detail, openHero, onOpenItem = { vm.openSheet(Sheet.Item(it)) }, onDismiss = vm::closeSheet, closeLabel = "Back to storage")
            else HeroDetailSheet(detail, openHero, onOpenItem = { vm.openSheet(Sheet.Item(it)) }, onDismiss = vm::closeSheet)
        }
        is Sheet.Item -> {
            val detail = remember(st, sheet) { vm.engine.itemDetail(st, sheet.id, st.lastResolution?.takeIf { sheet.id !in st.weapons }?.weaponSnapshot(sheet.id)) }
            if (detail == null) LaunchedEffect(sheet) { vm.closeSheet() }
            else if (inStorage) ItemDetailContent(detail, planning = true, openHero, onStock = { vm.stock(sheet.id, it) }, onDismiss = vm::closeSheet, enabled = !s.busy, closeLabel = "Back to storage")
            else ItemDetailSheet(
                detail, planning = true, openHero,
                onStock = { vm.stock(sheet.id, it) },
                onDismiss = vm::closeSheet, enabled = !s.busy, notice = s.notice,
            )
        }
        Sheet.Visitor -> {
            // Open only while the visitor waits: an answer that sends them away closes it, the inspection does not.
            val view = s.encounter?.takeIf { it.instance.isOpen }
            if (view == null) LaunchedEffect(sheet) { vm.closeSheet() }
            else EncounterSheet(view, st, s.busy, onCommit = vm::answerVisitor, onDismiss = vm::closeSheet)
        }
    }
}

/**
 * The four destinations. Every label has the same fixed style and never shrinks to fit: a larger font scale makes the
 * label taller, not smaller, and the four names are short enough to stay on one line.
 */
@Composable
fun DestinationBar(selected: Dest, onSelect: (Dest) -> Unit, modifier: Modifier = Modifier) {
    // 72dp instead of the 80dp default: the workshop needs the vertical space more than the bar does.
    // A panel under a bronze rule; the chosen destination is a lit plate with a gold edge, not Material's pill.
    NavigationBar(
        containerColor = ForgePanel, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.navigationBarsPadding().height(72.dp).drawWithContent { drawContent(); drawLine(Bronze, Offset.Zero, Offset(size.width, 0f), strokeWidth = 2.dp.toPx()) },
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
                icon = { PixelImage(destIcon(d), wholePixelDp(40, 36.dp), description = null) },
                label = { Text(destName(d), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, modifier = Modifier.testTag("nav_label_${d.name.lowercase()}")) },
            )
        }
    }
}

/** The three segments of Records (News, Notebook, Legacy) as one row of exclusive buttons. */
@Composable
fun SegmentRow(selected: RecordsPage, onSelect: (RecordsPage) -> Unit, modifier: Modifier = Modifier) {
    val pages = RecordsPage.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().padding(bottom = Space.sm)) {
        pages.forEachIndexed { i, p ->
            // These are pages, not a setting: no check mark. The chosen page is the lit bronze plate with gold lettering,
            // and the row says which is selected to a screen reader.
            SegmentedButton(
                selected = selected == p,
                onClick = { onSelect(p) },
                shape = SegmentedButtonDefaults.itemShape(i, pages.size, MaterialTheme.shapes.small),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = BronzeContainer, activeContentColor = Gold, activeBorderColor = Bronze, inactiveContentColor = CreamMuted, inactiveBorderColor = BronzeDeep),
                icon = {},
                modifier = Modifier.heightIn(min = 48.dp).testTag("page_${p.name.lowercase()}"),
            ) { Text(recordsPageName(p), maxLines = 1) }
        }
    }
}

private fun UiState.Playing.pendingBlessingOffer() =
    state.pendingBlessingOffer.isNotEmpty() && revealWeaponId == null && blessingOfferDismissedDay != state.day

/** The relic offer; it is shown only when the blessing dialog is not (the blessing comes first, and once that is put off the relic may be chosen). */
internal fun UiState.Playing.pendingRelicOffer() =
    state.pendingRelicOffer.isNotEmpty() && revealWeaponId == null && sheet == null && relicOfferDismissedDay != state.day
/** The one name of the day's skip, on the strip, on the restart prompt and in the hint: it lands on the evening card, not on tomorrow. */
const val SKIP_TO_EVENING = "Skip to evening"

/** First-run tips (GDD 3.3 onboarding): dismissed IDs live in settings, never in the save. */
object Tips {
    data class Tip(val id: String, val body: String)
    val FORGE = Tip("forge", "Pick a family, a core and an augment, then forge. Every valid forge yields a usable weapon.")
    val END_DAY = Tip("end_day", "At End Day heroes shop, then fight or rest, and factions press on the town. Read it all in the Gazette.")
    val MARKET = Tip("market", "Heroes buy what suits them and their purse. List weapons here at a price you like.")
    /** Not a banner: one line under the first customer of the first shop day the player watches (`ShopDayHost`). */
    val COUNTER = Tip("counter", "Tap anywhere for the next card · $SKIP_TO_EVENING jumps to the day's last card")
    val ORDER = listOf(FORGE, END_DAY, MARKET, COUNTER)
    val ALL = ORDER.map { it.id }.toSet()
    fun forDest(d: Dest): List<Tip> = when (d) { Dest.FORGE -> listOf(END_DAY); Dest.SHOP -> listOf(MARKET); else -> emptyList() }
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
            InlineActionButton("Got it", { vm.dismissTip(tip.id) }, Modifier.heightIn(min = 48.dp))
        }
    }
}

/**
 * The single rest action with its contextual sublabel (GDD 12 "End Day with contextual warning"). [primary]: the gold
 * plate, on the Shop; on the Forge, where "Forge weapon" is the way forward, the same action as an outline.
 */
@Composable
private fun EndDayButton(s: UiState.Playing, vm: GameViewModel, primary: Boolean) {
    val haptics = LocalHaptics.current
    val note = endDayNote(s)
    // Clear of the destination bar under it: this is the one tap of the day that cannot be taken back.
    val place = Modifier.fillMaxWidth().padding(start = Space.md, end = Space.md, top = Space.xs, bottom = Space.md).testTag("end_day")
    val end = { haptics.play(Moment.END_DAY); vm.endDay() }
    if (primary) PrimaryActionButton("End Day", end, place, enabled = !s.busy, detail = note)
    else SecondaryActionButton("End Day", end, place, enabled = !s.busy, detail = note)
}

/** What End Day would leave behind or bring on, the most pressing first: said wherever the day can be ended (the Shop's button, the Forge's menu and its foot). */
internal fun endDayNote(s: UiState.Playing): String {
    val state = s.state
    val offers = state.commissions.values.count { it.status == CommissionStatus.OFFERED }
    return when {
        state.pendingBlessingOffer.isNotEmpty() -> "A blessing awaits your choice"
        // The run can end tonight: said before anything else that waits.
        s.shop.threat?.today == true -> "A siege follows today's trading"
        // A visitor who waits, then a relic on offer: the words of the rule below.
        s.encounter?.instance?.isOpen == true || state.pendingRelicOffer.isNotEmpty() -> endDayNote(state, s.encounter)
        offers > 0 -> if (offers == 1) "1 unaccepted commission" else "$offers unaccepted commissions"
        state.overworkToday > 0 -> "Overwork: ${state.overworkToday} less energy tomorrow"
        state.energy > 0 -> "${state.energy} energy unused"
        else -> "Rest until dawn"
    }
}

/** What End Day will leave behind, most pressing first. An unanswered visitor is told the free answer; an offer waits for another day. */
internal fun endDayNote(state: GameState, visitor: Encounters.View?): String = when {
    state.pendingBlessingOffer.isNotEmpty() -> "A blessing awaits your choice"
    visitor?.instance?.isOpen == true -> "The visitor leaves tonight: ${visitor.defaultLabel}"
    state.pendingRelicOffer.isNotEmpty() -> "A relic awaits your choice"
    state.commissions.values.any { it.status == CommissionStatus.OFFERED } -> "A commission is waiting"
    state.overworkToday > 0 -> "Tomorrow starts ${state.overworkToday} energy short"
    state.energy > 0 -> "${state.energy} energy unused"
    else -> "Rest until dawn"
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
        PixelImage(icon, wholePixelDp(24, 30.dp), description = null)
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

/** The paper has one name everywhere: every link to this page says "Read the Gazette". */
fun recordsPageName(p: RecordsPage) = when (p) { RecordsPage.GAZETTE -> "Gazette"; RecordsPage.JOURNAL -> "Notebook"; RecordsPage.LEGACY -> "Legacy" }

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

/** What the engine says scrapping these blades gives back, in a sentence for the confirmation. */
internal fun scrapBackText(engine: GameEngine, state: GameState, ids: List<WeaponId>): String {
    val back = engine.scrapYield(ids.mapNotNull { state.weapons[it] })
    val per = engine.config.saveGrowth.scrapBladesPerMaterial
    return if (back.isEmpty()) "Nothing comes back: it takes $per blades of one metal to recover a unit of it."
    else "You get back " + back.entries.joinToString(", ") { "${it.value} ${engine.content.material(it.key).name}" } + " (one unit for every $per blades of a metal)."
}
