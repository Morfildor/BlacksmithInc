package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.GuildTab
import com.example.blacksmithproject.Sheet
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.BronzeLine
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Ember
import com.example.blacksmithproject.ui.theme.ForgePanel
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.model.HeroId

/** A choice that cannot be taken back, asked once more before it is sent. */
internal data class Confirm(val title: String, val body: String, val action: String, val command: Command)

/** One row of a Guild part's list: its key (what Today scrolls to) and what it shows. */
internal class GuildRow(val key: String, val content: @Composable () -> Unit)

/** What a part needs from the frame: open a hero's sheet or a contract, ask before a step that cannot be undone, open the assign sheet. */
internal class GuildActions(
    val enabled: Boolean,
    val openHero: (HeroId) -> Unit,
    val openContract: (String) -> Unit,
    val confirm: (Confirm) -> Unit,
    val assign: () -> Unit,
    val send: (Command) -> Unit,
)

internal fun guildTabName(t: GuildTab) = when (t) { GuildTab.CONTRACTS -> "Contracts"; GuildTab.COMPANY -> "Company"; GuildTab.WALL -> "Wall"; GuildTab.CHARTER -> "Charter" }

/**
 * The Guild destination of a guild run, in the place Town has in a classic run. Today (at most three
 * rows, each with a direct action) and the four parts, Contracts, Company,
 * Wall and Charter, each a short list with Today in its scrolling content. The part shown is the one chosen today, else the Wall on the eve and
 * the day of a siege, else Contracts (`guildTabFor`). Every row that changes something sends a command and waits for the save.
 */
@Composable
fun GuildPanel(s: UiState.Playing, ui: GuildUi, vm: GameViewModel, modifier: Modifier = Modifier) {
    val st = s.state
    val tab = guildTabFor(s.guildTab, st.day, ui.wall.daysLeft)
    // The contract whose sheet is open, by its ID on the board; the assign sheet; a confirmation; the row Today asked for.
    var assigning by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var anchor by remember { mutableStateOf<Pair<GuildTab, String>?>(null) }
    val actions = GuildActions(
        enabled = !s.busy, openHero = { vm.openSheet(Sheet.Hero(it)) }, openContract = vm::openContract,
        confirm = { confirm = it }, assign = { assigning = true }, send = vm::dispatch,
    )
    val dots = ui.today.filterNot { it.optional }.mapNotNull { it.tab }.toSet() + listOfNotNull(GuildTab.COMPANY.takeIf { ui.companyNeeds })
    val reducedMotion by vm.settings.reducedMotion.collectAsStateWithLifecycle(initialValue = false)
    Column(modifier.fillMaxSize()) {
        val onToday: (TodayUi) -> Unit = { row ->
            when (val t = row.target) {
                is TodayTarget.Tab -> { vm.selectGuildTab(t.tab); anchor = t.tab to t.anchor }
                is TodayTarget.Hero -> vm.openSheet(Sheet.Hero(t.heroId))
                TodayTarget.AssignWall -> { vm.selectGuildTab(GuildTab.WALL); assigning = true }
            }
        }
        GuildTabs(tab, dots, onSelect = { vm.selectGuildTab(it); anchor = null })
        HorizontalDivider(color = BronzeDeep)
        val rows = when (tab) {
            GuildTab.CONTRACTS -> contractRows(ui, actions)
            GuildTab.COMPANY -> companyRows(ui, actions)
            GuildTab.WALL -> wallRows(ui, actions)
            GuildTab.CHARTER -> charterRows(ui, actions)
        }
        GuildList(tab, rows, anchor, reducedMotion, ui.today, onToday) { anchor = null }
    }
    // The ViewModel owns the draft while the existing hero/weapon sheets replace this modal.
    s.partyDraft?.let { draft ->
        if (s.sheet == null) ContractSheet(
            st, vm.engine, draft, s.busy,
            onToggle = vm::togglePartyMember, onMove = vm::movePartyMember, onPosture = vm::setPartyPosture,
            onOpenHero = { vm.openSheet(Sheet.Hero(it)) }, onReviewEquipment = vm::reviewPartyEquipment,
            onSend = vm::confirmParty, onCancelPlan = vm::cancelPartyPlan, onDismiss = vm::closeContract,
        )
    }
    if (assigning && s.sheet == null && confirm == null) AssignSheet(ui, actions, onDismiss = { assigning = false })
    confirm?.let { c ->
        ForgeDialog(
            c.title, onDismiss = { confirm = null }, tag = "guild_confirm",
            actions = {
                InlineActionButton("Not now", { confirm = null }, Modifier.testTag("guild_confirm_no"))
                InlineActionButton(c.action, { confirm = null; vm.dispatch(c.command) }, Modifier.testTag("guild_confirm_yes"))
            },
        ) {
            Text(c.body)
        }
    }
}

/** Today scrolls with the part, so full reminders and actions fit even at large font scales. */
@Composable
internal fun TodayRows(rows: List<TodayUi>, onAct: (TodayUi) -> Unit) {
    Column(Modifier.fillMaxWidth().background(ForgePanel).testTag("guild_today")) {
        if (rows.none { !it.optional }) Secondary("No urgent guild decisions. You can keep today's plan.", Modifier.padding(vertical = Space.sm))
        rows.forEach { r ->
            Column(Modifier.fillMaxWidth().padding(vertical = Space.xs).testTag("guild_today_${r.key}")) {
                if (r.optional) Secondary("Optional", Modifier.testTag("guild_today_optional_${r.key}"))
                Text(r.text, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.testTag("guild_today_text_${r.key}"))
                InlineActionButton("${r.action}  ›", { onAct(r) }, Modifier.heightIn(min = 48.dp).testTag("guild_today_go_${r.key}"))
            }
        }
    }
}

/**
 * The four parts as one row of tabs of equal width: the chosen one underlined in gold, a dot where something waits. A narrow screen
 * takes the smaller label; with large text the row scrolls sideways rather than cut a name.
 */
@Composable
private fun GuildTabs(selected: GuildTab, dots: Set<GuildTab>, onSelect: (GuildTab) -> Unit) {
    val scroll = LocalDensity.current.fontScale >= 1.3f
    val label = if (LocalConfiguration.current.screenWidthDp < 400) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    Row(
        Modifier.fillMaxWidth().background(ForgePanel).then(if (scroll) Modifier.horizontalScroll(rememberScrollState()) else Modifier).padding(horizontal = Space.sm, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        GuildTab.entries.forEach { t ->
            val on = t == selected
            Row(
                (if (scroll) Modifier.widthIn(min = 80.dp) else Modifier.weight(1f)).heightIn(min = 44.dp)
                    // The chosen part underlined in gold, the others plain: tabs, not a row of buttons.
                    .drawBehind { if (on) drawRect(Gold, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx())) }
                    .clickable(role = Role.Tab, onClick = { onSelect(t) })
                    .semantics { this.selected = on; if (t in dots) stateDescription = "Needs you" }
                    .padding(horizontal = 6.dp).testTag("guild_tab_${t.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Text(guildTabName(t), style = label, color = if (on) Gold else CreamMuted, maxLines = 1, softWrap = false)
                if (t in dots) Box(Modifier.padding(start = 6.dp).size(7.dp).background(if (on) Gold else Ember, CircleShape))
            }
        }
    }
}

/** One part's rows as one lazy list; a row Today pointed at is scrolled to once. Each part keeps its own scroll. */
@Composable
private fun GuildList(tab: GuildTab, rows: List<GuildRow>, anchor: Pair<GuildTab, String>?, reducedMotion: Boolean, today: List<TodayUi>, onToday: (TodayUi) -> Unit, onAnchored: () -> Unit) {
    val list = key(tab) { rememberLazyListState() }
    // Once the part Today pointed at is the one shown: the save that switches it may land a frame later.
    LaunchedEffect(anchor, tab) {
        val (t, k) = anchor ?: return@LaunchedEffect
        if (t != tab) return@LaunchedEffect
        rows.indexOfFirst { it.key == k }.takeIf { it >= 0 }?.let { if (reducedMotion) list.scrollToItem(it + 1) else list.animateScrollToItem(it + 1) }
        onAnchored()
    }
    LazyColumn(
        Modifier.fillMaxSize().bottomFade(list.canScrollForward).testTag("guild_list"), state = list,
        contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.sm, bottom = Space.lg),
    ) {
        item(key = "today") { TodayRows(today, onToday) }
        items(rows, key = { it.key }) { it.content() }
    }
}

/** A face in its dark slot, as every row of Town has it. */
@Composable
internal fun Face(portrait: Int, size: Int = 44) {
    Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep)) { PixelImage(portrait, size.dp, description = null) }
}

/** A short fact on a plate: the word is always there beside the colour. */
@Composable
internal fun Chip(text: String, color: Color = CreamMuted, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1,
        modifier = modifier.border(1.dp, color.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** A section that opens and closes under its heading: the count and one line say what is inside while it is closed. */
@Composable
internal fun Collapsible(key: String, title: String, summary: String?, openAtFirst: Boolean, tag: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(key) { mutableStateOf(openAtFirst) }
    Column(Modifier.fillMaxWidth().padding(top = Space.sm)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = if (open) "Close" else "Open", role = Role.Button) { open = !open }
                .semantics { heading(); stateDescription = if (open) "Open" else "Closed" }.testTag("${tag}_head"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Gold)
                if (!open) summary?.let { Secondary(it) }
            }
            Text(if (open) "▴" else "▾", style = MaterialTheme.typography.titleMedium, color = Gold)
        }
        HorizontalDivider(color = BronzeDeep)
        if (open) Column(Modifier.fillMaxWidth().padding(top = Space.xs).testTag(tag)) { content() }
    }
}

/** What is known of the units on a field: each kind once, with how many of it and its known moves under it. */
@Composable
internal fun EnemyLines(enemies: List<EnemyUi>, modifier: Modifier = Modifier) {
    Column(modifier) {
        enemies.forEach { e ->
            Text(e.label, style = MaterialTheme.typography.bodyMedium, color = Cream)
            e.rules.forEach { Secondary("· $it", Modifier.padding(start = Space.sm)) }
        }
    }
}

/**
 * Who can be kept for the wall: every member with the rules' own answer. Keep or Release where the rule allows; Keep home
 * for one in today's planned party (it cancels the plan, asked first); a reason without a button for one away, held or
 * laid up. A place empty on the eve is filled this way or not at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignSheet(ui: GuildUi, a: GuildActions, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },
        modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("guild_assign_sheet"),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Space.md).padding(bottom = Space.lg)) {
            Text("Keep for the wall", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { heading() })
            Secondary("A kept member stands first and no party takes them. The other places go to the strongest fit, members or townsfolk.")
            Text("${ui.wall.defenders.size} of ${ui.wall.places} places filled", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.padding(top = Space.sm))
            ui.assign.forEach { r ->
                Column(Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().padding(horizontal = 12.dp, vertical = Space.sm).testTag("guild_assign_row_${r.heroId.value}")) {
                    Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Open member equipment") { a.openHero(r.heroId) }.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Face(r.portrait, 40)
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.titleSmall)
                            Secondary(r.sub)
                            r.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (r.action == AssignAction.NONE) Ember else Cream) }
                        }
                    }
                    when (r.action) {
                        AssignAction.RESERVE -> SecondaryActionButton("Keep", { a.send(Command.ReserveDefender(r.heroId, true)) }, Modifier.heightIn(min = 48.dp).testTag("guild_reserve_${r.heroId.value}"), enabled = a.enabled)
                        AssignAction.RELEASE -> InlineActionButton("Release", { a.send(Command.ReserveDefender(r.heroId, false)) }, Modifier.heightIn(min = 48.dp).testTag("guild_reserve_${r.heroId.value}"), enabled = a.enabled)
                        AssignAction.KEEP_HOME -> InlineActionButton("Keep home", {
                            a.confirm(Confirm("Keep the party home?", "Today's planned contract is cancelled and its fee comes back. You can plan another this morning.", "Keep home", Command.CancelPlannedDeployment))
                        }, Modifier.heightIn(min = 48.dp).testTag("guild_keep_home_${r.heroId.value}"), enabled = a.enabled)
                        AssignAction.NONE -> Unit
                    }
                }
            }
            InlineActionButton("Done", onDismiss, Modifier.align(Alignment.End).testTag("guild_assign_done"))
        }
    }
}

/** A row's plate: the panel ground in a bronze line, as every tappable thing in the guild has it. */
internal fun Modifier.guildCard(): Modifier = fillMaxWidth().padding(top = Space.sm).forgeRow().padding(horizontal = 12.dp, vertical = Space.sm)

/** The hairline between rows of a list inside a part. */
@Composable
internal fun RowRule() = HorizontalDivider(color = BronzeLine.copy(alpha = 0.5f))
