package com.example.blacksmithproject.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.blacksmithproject.Dest
import com.example.blacksmithproject.GuildTab
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.RecordsPage
import com.example.blacksmithproject.R
import com.example.blacksmithproject.Sheet
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.data.IntroStatus
import com.example.blacksmithproject.ui.detail.HeroDetailContent
import com.example.blacksmithproject.ui.detail.HeroDetailSheet
import com.example.blacksmithproject.ui.detail.ItemDetailContent
import com.example.blacksmithproject.ui.detail.ItemDetailSheet
import com.example.blacksmithproject.ui.detail.MemberActions
import com.example.blacksmithproject.ui.detail.StockAction
import com.example.blacksmithproject.ui.detail.customerSnapshot
import com.example.blacksmithproject.ui.detail.heroDetail
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.detail.toCommand
import com.example.blacksmithproject.ui.detail.weaponSnapshot
import com.example.blacksmithproject.ui.theme.Bronze
import com.example.blacksmithproject.ui.theme.BronzeContainer
import com.example.blacksmithproject.ui.theme.BronzeDeep
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
import kotlinx.coroutines.delay

/** How long a notice stays over the workshop before it leaves by itself. */
private const val NOTICE_MILLIS = 4_000L

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
    // A guild run's first morning: the opening is chosen before anything else, because End Day waits for it. The offer is
    // in the save, so this comes back after any restart; nothing dismisses it but the choice (Back opens the main menu).
    val opening = remember(state.guild?.openingOffer, state.heroes) { vm.engine.openingChooser(state) }
    if (opening != null) {
        val introRecord by vm.intro.collectAsStateWithLifecycle()
        OpeningChooser(
            opening, enabled = !s.busy, onChoose = { vm.dispatch(Command.ChooseOpening(it)) },
            firstGame = if (introRecord?.status == IntroStatus.QUEUED) ({ OpeningIntroNote(onSkip = { vm.skipIntro() }) }) else null,
        )
        s.lastError?.let { ErrorDialog(it, vm::dismissError) }
        return
    }
    // Each destination keeps its own scroll position and what is open in it while the player is somewhere else.
    val positions = rememberSaveableStateHolder()
    // What just happened to a blade (listed, stored, repriced, melted down): said once, over whichever destination is
    // open. Under a sheet it could not be seen, so there the sheet says it and this waits.
    val covered = storageOpen || s.sheet != null
    val notice = s.notice?.takeIf { !covered }
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        try { delay(NOTICE_MILLIS) } finally { vm.dismissNotice(notice) }
    }
    Scaffold(
        bottomBar = {
            Column {
                // End Day is the Shop's way forward, on a footer of its own under a rule: the list ends above it, so it never
                // sits on a cut row. The Forge keeps it under "More", and beside its action when no forge is left today.
                if (s.dest == Dest.SHOP) Box(Modifier.fillMaxWidth().background(ForgePanel).drawBehind { drawLine(BronzeDeep, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }) {
                    EndDayButton(s, vm, primary = true)
                }
                DestinationBar(s.dest, vm::selectDest, guild = s.guild != null, marked = when (s.intro?.mark) { IntroMark.NAV_FORGE -> Dest.FORGE; IntroMark.NAV_SHOP -> Dest.SHOP; else -> null })
            }
        },
    ) { padding -> positions.SaveableStateProvider(s.dest) {
        Column(Modifier.padding(padding).fillMaxSize()) {
            // The day and the siege, pinned over every destination in planning: the strip opens where the siege is prepared.
            val header = remember(s.state, s.guild, s.shop.threat, s.forecast) { s.siegeHeader(vm.engine.config.siegeInterval, vm.engine.config.championCount) }
            WorkshopHeader(
                header, state.gold, state.energy, onSettings = { settingsOpen = true },
                onOpenSiege = { if (s.guild != null) vm.openGuildWall() else vm.selectDest(Dest.TOWN) },
                openLabel = if (s.guild != null) "Open the wall" else "Open Town",
            )
            HorizontalDivider(color = BronzeDeep)
            // The guided first day's one step, in the layout and never over it: nothing under it is covered or blocked.
            s.intro?.let { IntroStrip(it, onSkip = { vm.skipIntro() }, onDone = { vm.finishIntro() }) }
            // Each tip belongs to one destination and shows one at a time; dismissal lives in settings. The guided day says
            // what the tips say, so they wait while it runs and count as seen when it ends.
            val tip = Tips.forDest(s.dest).firstOrNull { it.id !in seenTips }.takeIf { s.intro == null }
            // The notice drops in over the head of the destination: the actions at its foot (Forge, End Day) stay free.
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            when (s.dest) {
                Dest.SHOP -> ShopPanel(
                    s.shop, s.busy, reducedMotion,
                    onLead = { lead ->
                        when (lead.kind) {
                            LeadKind.CHOOSE_BLESSING -> vm.reopenBlessingOffer()
                            LeadKind.LIST_STOCK -> storageOpen = true
                            LeadKind.ANSWER_REQUEST -> boardOpen = true
                            LeadKind.PRICES_TOO_HIGH -> lead.weaponId?.let { vm.openSheet(Sheet.Item(it)) }
                            // Who the work is for goes to the Forge with it: the commission, or the hero whose want it is.
                            LeadKind.FORGE_FOR_REQUEST -> lead.commissionId?.let(vm::forgeFor) ?: vm.selectDest(Dest.FORGE)
                            LeadKind.ANSWER_WANT -> lead.heroId?.let(vm::forgeWant) ?: lead.familyId?.let(vm::forgeFamily) ?: vm.selectDest(Dest.FORGE)
                            LeadKind.REVIEW_WALL -> { vm.selectGuildTab(GuildTab.WALL); vm.selectDest(Dest.TOWN) }
                            LeadKind.GUILD_RESCUE, LeadKind.GUILD_CHECKPOINT -> { vm.selectGuildTab(GuildTab.CONTRACTS); vm.selectDest(Dest.TOWN) }
                            LeadKind.DAY_SETTLED -> Unit
                            else -> vm.selectDest(Dest.FORGE)
                        }
                    },
                    onOpenBlade = { vm.openSheet(Sheet.Item(it)) },
                    onOpenBoard = { boardOpen = true },
                    onOpenStorage = { storageOpen = true },
                    onOpenNews = { vm.selectRecords(RecordsPage.GAZETTE) },
                    tip = tip?.let { { TipBanner(it, vm) } },
                    // The header owns the day and the countdown; once the siege is warned the Shop keeps who comes and what buyers now weigh.
                    strip = s.shop.threat?.takeIf { it.warned && it.matchup != null }?.let { t -> { SiegeMatchup(t, "shop_threat", t.note) } },
                    // One voice: while the guided first day runs, its strip says what to do next; the lead comes back with the day's outcome.
                    showLead = s.intro?.last ?: true,
                    onOpenSupplies = { suppliesOpen = true },
                    visitor = s.encounter, onOpenVisitor = { vm.openSheet(Sheet.Visitor) },
                    relics = s.relics, relicSlots = if (vm.engine.content.relics.isEmpty()) 0 else vm.engine.config.depth.relicSlots,
                    relicOffer = state.pendingRelicOffer.isNotEmpty(), onOpenRelicOffer = vm::reopenRelicOffer,
                    onAnswer = { id, accept -> vm.dispatch(if (accept) Command.AcceptCommission(id) else Command.DeclineCommission(id)) },
                    onList = { id, price -> vm.stock(id, StockAction.ListAt(price)) }, onSalvage = { vm.stock(it, StockAction.Salvage) },
                )
                Dest.FORGE -> ForgePanel(s, vm, reducedMotion, tip, onOpenSupplies = { suppliesFocus = it?.value; suppliesOpen = true }, onOpenBoard = { boardOpen = true }, onEndDay = { workshopHaptics.play(Moment.END_DAY); vm.endDay() })
                Dest.RECORDS -> RecordsPanel(s, vm)
                Dest.TOWN -> TownPanel(s, vm)
            }
            AnimatedContent(
                notice, Modifier.align(Alignment.TopCenter),
                transitionSpec = {
                    // No size animation: the slide and the fade are the whole of it.
                    (if (reducedMotion) fadeIn() togetherWith fadeOut()
                    else (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())) using null
                },
                label = "notice",
            ) { said -> if (said != null) NoticeBanner(said, onDismiss = { vm.dismissNotice(said) }) }
            }
        }
    } }
    val haptics by vm.settings.haptics.collectAsStateWithLifecycle(initialValue = false)
    val introRow by vm.introRow.collectAsStateWithLifecycle()
    if (settingsOpen) SettingsSheet(reducedMotion, vm::setReducedMotion, haptics, vm::setHaptics, onDismiss = { settingsOpen = false }, onMainMenu = { settingsOpen = false; onMainMenu() }, intro = introRow, onIntro = { vm.introAction() })
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
            loans = s.state.loanRows(),
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
        onForgeThis = { boardOpen = false; vm.forgeFor(it) }, onForgeWant = { boardOpen = false; vm.forgeWant(it) },
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
            else HeroDetailSheet(
                detail, openHero, onOpenItem = { vm.openSheet(Sheet.Item(it)) }, onDismiss = vm::closeSheet,
                closeLabel = if (s.partyDraft != null) "Back to party" else "Close",
                // A member of the guild: the smith's own actions on them (a branch, a loan, letting them go).
                member = MemberActions(
                    enabled = !s.busy,
                    onChooseBranch = { vm.dispatch(Command.ChooseSpeciality(sheet.id, it)) },
                    onLoan = { vm.dispatch(Command.LoanWeapon(sheet.id, it)) }, onRecall = { vm.dispatch(Command.RecallLoan(it)) },
                    onDismissMember = { vm.closeSheet(); vm.dispatch(Command.DismissHero(sheet.id)) },
                ).takeIf { detail.member != null },
            )
        }
        is Sheet.Item -> {
            val detail = remember(st, sheet) { vm.engine.itemDetail(st, sheet.id, st.lastResolution?.takeIf { sheet.id !in st.weapons }?.weaponSnapshot(sheet.id)) }
            // The bench for this weapon, over the sheet. It reads the weapon and the workshop's relics and changes nothing.
            var benching by rememberSaveable(sheet.id.value) { mutableStateOf(false) }
            val onBench = { benching = true }
            if (detail == null) LaunchedEffect(sheet) { vm.closeSheet() }
            else if (inStorage) ItemDetailContent(detail, planning = true, openHero, onStock = { vm.stock(sheet.id, it) }, onDismiss = vm::closeSheet, enabled = !s.busy, closeLabel = "Back to storage", onBench = onBench)
            else ItemDetailSheet(
                detail, planning = true, openHero,
                closeLabel = if (s.partyDraft != null) "Back to party" else "Close",
                onStock = { vm.stock(sheet.id, it) },
                onDismiss = vm::closeSheet, enabled = !s.busy, notice = s.notice, onBench = onBench,
            )
            if (benching && detail?.rules != null) st.weapons[sheet.id]?.let { weapon ->
                BenchSheet(weapon, demonstration = false, vm.engine.content, vm.engine.config, remember(st.relics) { benchRelics(st, vm.engine.content) }) { benching = false }
            }
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
 * label taller, not smaller, and the four names are short enough to stay on one line. [guild]: a guild run, where the
 * third destination is called Guild. [marked]: the destination the guided first day points at.
 */
@Composable
fun DestinationBar(selected: Dest, onSelect: (Dest) -> Unit, modifier: Modifier = Modifier, guild: Boolean = false, marked: Dest? = null) {
    // 72dp instead of the 80dp default: the workshop needs the vertical space more than the bar does.
    // A panel under a bronze rule; the chosen destination is a lit plate with a gold edge, not Material's pill.
    // Large text: the label is taller, so the bar is; it never shrinks the label to fit.
    val height = if (LocalDensity.current.fontScale > 1.3f) 96.dp else 72.dp
    NavigationBar(
        containerColor = ForgePanel, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.navigationBarsPadding().height(height).drawWithContent { drawContent(); drawLine(Bronze, Offset.Zero, Offset(size.width, 0f), strokeWidth = 2.dp.toPx()) },
    ) {
        Dest.entries.forEach { d ->
            NavigationBarItem(
                selected = selected == d,
                onClick = { onSelect(d) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, selectedTextColor = Gold, unselectedTextColor = CreamMuted),
                modifier = Modifier.testTag("nav_${d.name.lowercase()}").introMark(marked == d).drawBehind {
                    if (selected == d) {
                        drawRect(BronzeContainer)
                        drawRect(Gold, size = Size(size.width, 2.dp.toPx()))
                    }
                },
                icon = { PixelImage(destIcon(d), wholePixelDp(40, 36.dp), description = null) },
                label = { Text(destName(d, guild), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, modifier = Modifier.testTag("nav_label_${d.name.lowercase()}")) },
            )
        }
    }
}

/** The pages of Records (Gazette, Notebook, Legacy, and Town in a guild run) as one row of tabs, as the Guild's parts are. */
@Composable
fun SegmentRow(selected: RecordsPage, onSelect: (RecordsPage) -> Unit, modifier: Modifier = Modifier, pages: List<RecordsPage> = RecordsPage.entries - RecordsPage.TOWNSFOLK) {
    Row(modifier.fillMaxWidth().padding(bottom = Space.sm).drawBehind { drawLine(BronzeDeep, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx()) }) {
        pages.forEach { p ->
            // These are pages, not a setting: the chosen one is underlined in gold, the others plain, and the row says which is selected to a screen reader.
            val on = selected == p
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp)
                    .drawBehind { if (on) drawRect(Gold, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx())) }
                    .clickable(role = Role.Tab) { onSelect(p) }.semantics { this.selected = on }.testTag("page_${p.name.lowercase()}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(recordsPageName(p), style = if (pages.size > 3) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge, color = if (on) Gold else CreamMuted, maxLines = 1, softWrap = false)
            }
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
    val FORGE = Tip("forge", "Choose a weapon, metal and augment. Then forge. You'll always make something usable.")
    val END_DAY = Tip("end_day", "End Day opens the shop and sends the heroes out. Check the Gazette to see who came back.")
    val MARKET = Tip("market", "Put weapons on the shelf and set their prices. Heroes look for gear they like and can afford.")
    /** Not a banner: one line under the first customer of the first shop day the player watches (`ShopDayHost`). */
    val COUNTER = Tip("counter", "Tap to see the next card. $SKIP_TO_EVENING takes you to the day's final card.")
    /** Not a banner: what rarity means, in full on the first forge result and behind a tap after (`ForgeResultDialog`). */
    const val RARITY = "rarity"
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
    val place = Modifier.fillMaxWidth().padding(start = Space.md, end = Space.md, top = Space.sm, bottom = Space.md).testTag("end_day").introMark(s.intro?.mark == IntroMark.END_DAY)
    val end = { haptics.play(Moment.END_DAY); vm.endDay() }
    if (primary) PrimaryActionButton("End Day", end, place, enabled = !s.busy, detail = note)
    else SecondaryActionButton("End Day", end, place, enabled = !s.busy, detail = note)
}

/** What End Day would leave behind or bring on, the most pressing first: said wherever the day can be ended (the Shop's button, the Forge's menu and its foot). */
internal fun endDayNote(s: UiState.Playing): String {
    val state = s.state
    val offers = state.commissions.values.count { it.status == CommissionStatus.OFFERED }
    // A guild run: what End Day sets in motion for the guild (the party that leaves, tonight's siege and who stands, a checkpoint without word), under whatever still waits.
    val guild = s.guild?.endDay.orEmpty()
    // What must be done before End Day does anything: said before the siege line, as the engine refuses End Day for it.
    val blocker = when {
        // The engine refuses End Day until the guild's opening is chosen (`GameError.OpeningPending`).
        state.guild?.openingOffer?.isNotEmpty() == true -> "Choose how the guild begins first"
        state.pendingBlessingOffer.isNotEmpty() -> "Choose a blessing"
        else -> null
    }
    // The siege is fought after the day's trading, never instead of it: said on the day, before what else waits. A guild
    // run's own line already says the siege tonight and who stands, so the button does not say it twice.
    val siege = "Trading first. The siege follows tonight.".takeIf { state.town.nextSiegeDay <= state.day && guild.none { it.startsWith("Siege tonight") } }
    val note = when {
        blocker != null -> null
        // A visitor who waits, then a relic on offer: the words of the rule below. The guided first day keeps the relic
        // for later (it waits as long as it takes), so it is not put beside the step that says to end the day.
        s.encounter?.instance?.isOpen == true || (state.pendingRelicOffer.isNotEmpty() && s.intro == null) -> endDayNote(state, s.encounter)
        offers > 0 -> if (offers == 1) "1 unaccepted commission" else "$offers unaccepted commissions"
        state.overworkToday > 0 -> "You'll have ${state.overworkToday} less energy tomorrow"
        // What is merely left over is not said beside what the guild sets in motion.
        guild.isNotEmpty() || siege != null -> null
        state.energy > 0 -> "${state.energy} energy unused"
        else -> "Rest until dawn"
    }
    return (listOfNotNull(blocker, siege, note) + guild).joinToString("\n")
}

/** What End Day will leave behind, most pressing first. An unanswered visitor is told the free answer; an offer waits for another day. */
internal fun endDayNote(state: GameState, visitor: Encounters.View?): String = when {
    state.pendingBlessingOffer.isNotEmpty() -> "Choose a blessing"
    visitor?.instance?.isOpen == true -> "The visitor leaves tonight. No answer means ${visitor.defaultLabel.lowercase()}."
    state.pendingRelicOffer.isNotEmpty() -> "Choose a workshop relic"
    state.commissions.values.any { it.status == CommissionStatus.OFFERED } -> "A new commission needs an answer"
    state.overworkToday > 0 -> "You'll have ${state.overworkToday} less energy tomorrow"
    state.energy > 0 -> "${state.energy} energy unused"
    else -> "Rest until dawn"
}

/** The paper has one name everywhere: every link to this page says "Read the Gazette". */
fun recordsPageName(p: RecordsPage) = when (p) { RecordsPage.GAZETTE -> "Gazette"; RecordsPage.JOURNAL -> "Notebook"; RecordsPage.LEGACY -> "Legacy"; RecordsPage.TOWNSFOLK -> "Town" }

fun destName(d: Dest) = when (d) { Dest.SHOP -> "Shop"; Dest.FORGE -> "Forge"; Dest.TOWN -> "Town"; Dest.RECORDS -> "Records" }

/** In a guild run the third destination is the guild's own: the wall, the party, the board and the roster. The place itself ([Dest.TOWN]) and its tags stay. */
fun destName(d: Dest, guild: Boolean) = if (guild && d == Dest.TOWN) "Guild" else destName(d)

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
    return if (back.isEmpty()) "No metal recovered. You need $per weapons of the same metal to recover one unit."
    else "You get back " + back.entries.joinToString(", ") { "${it.value} ${engine.content.material(it.key).name}" } + " (one unit per $per weapons of the same metal)."
}
