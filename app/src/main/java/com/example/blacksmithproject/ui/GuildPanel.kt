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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.GuildTab
import com.example.blacksmithproject.Sheet
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.BronzeContainer
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
 * The Guild destination of a guild run, in the place Town has in a classic run. Pinned at its head: Today (at most three
 * rows, each one thing that needs the smith with the button that goes to it) and the four parts, Contracts, Company,
 * Wall and Charter, each a short list of its own. The part shown is the one chosen today, else the Wall on the eve and
 * the day of a siege, else Contracts (`guildTabFor`). Every row that changes something sends a command and waits for the save.
 */
@Composable
fun GuildPanel(s: UiState.Playing, ui: GuildUi, vm: GameViewModel, modifier: Modifier = Modifier) {
    val st = s.state
    val tab = guildTabFor(s.guildTab, st.day, ui.wall.daysLeft)
    // The contract whose sheet is open, by its ID on the board; the assign sheet; a confirmation; the row Today asked for.
    var contract by rememberSaveable { mutableStateOf<String?>(null) }
    var assigning by rememberSaveable { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var anchor by remember { mutableStateOf<Pair<GuildTab, String>?>(null) }
    val actions = GuildActions(
        enabled = !s.busy, openHero = { vm.openSheet(Sheet.Hero(it)) }, openContract = { contract = it },
        confirm = { confirm = it }, assign = { assigning = true }, send = vm::dispatch,
    )
    val dots = ui.today.mapNotNull { it.tab }.toSet() + listOfNotNull(GuildTab.COMPANY.takeIf { ui.companyNeeds })
    Column(modifier.fillMaxSize()) {
        TodayRows(ui.today) { row ->
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
        GuildList(tab, rows, anchor) { anchor = null }
    }
    // Under a hero's sheet the contract waits, as the commission board does.
    contract?.let { id ->
        if (st.guild?.offers?.none { it.id == id } != false) LaunchedEffect(id) { contract = null }
        else if (s.sheet == null) ContractSheet(
            st, vm.engine, id, s.busy,
            onSend = { heroes, posture -> contract = null; vm.dispatch(Command.PlanDeployment(id, heroes, posture)) },
            onCancelPlan = { contract = null; vm.dispatch(Command.CancelPlannedDeployment) },
            onDismiss = { contract = null },
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

/**
 * What needs the smith today. On a short screen or with large text only the first row shows, with how many more wait
 * under one tap: the part's own list keeps the room. With nothing to do it is one quiet line.
 */
@Composable
private fun TodayRows(rows: List<TodayUi>, onAct: (TodayUi) -> Unit) {
    val compact = LocalDensity.current.fontScale >= 1.3f || LocalConfiguration.current.screenHeightDp < 700
    var more by rememberSaveable { mutableStateOf(false) }
    val shown = if (compact && !more) rows.take(1) else rows
    Column(Modifier.fillMaxWidth().background(ForgePanel).padding(horizontal = Space.md, vertical = Space.xs).testTag("guild_today")) {
        if (rows.isEmpty()) Secondary("Nothing in the guild needs you today.", Modifier.heightIn(min = 32.dp).padding(vertical = 6.dp))
        shown.forEach { r ->
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).testTag("guild_today_${r.key}"), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = Space.sm).size(6.dp).background(Gold, CircleShape))
                Text(r.text, style = MaterialTheme.typography.bodyMedium, color = Cream, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                InlineActionButton("${r.action}  ›", { onAct(r) }, Modifier.heightIn(min = 40.dp).testTag("guild_today_go_${r.key}"))
            }
        }
        if (compact && rows.size > 1) InlineActionButton(if (more) "Fewer" else "${rows.size - 1} more today", { more = !more }, Modifier.testTag("guild_today_more"))
    }
}

/** The four parts as one row of plates: the chosen one lit, a gold dot where something waits. Scrolls sideways before a name would shrink. */
@Composable
private fun GuildTabs(selected: GuildTab, dots: Set<GuildTab>, onSelect: (GuildTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(ForgePanel).horizontalScroll(rememberScrollState()).padding(horizontal = Space.sm, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        GuildTab.entries.forEach { t ->
            val on = t == selected
            Row(
                Modifier.widthIn(min = 80.dp).heightIn(min = 44.dp)
                    .background(if (on) BronzeContainer else Color.Transparent, MaterialTheme.shapes.small)
                    .border(1.dp, if (on) Gold else BronzeDeep, MaterialTheme.shapes.small)
                    .clickable(role = Role.Tab, onClick = { onSelect(t) })
                    .semantics { this.selected = on; if (t in dots) stateDescription = "Needs you" }
                    .padding(horizontal = 12.dp).testTag("guild_tab_${t.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Text(guildTabName(t), style = MaterialTheme.typography.labelLarge, color = if (on) Gold else CreamMuted, maxLines = 1, softWrap = false)
                if (t in dots) Box(Modifier.padding(start = 6.dp).size(7.dp).background(if (on) Gold else Ember, CircleShape))
            }
        }
    }
}

/** One part's rows as one lazy list; a row Today pointed at is scrolled to once. Each part keeps its own scroll. */
@Composable
private fun GuildList(tab: GuildTab, rows: List<GuildRow>, anchor: Pair<GuildTab, String>?, onAnchored: () -> Unit) {
    val list = key(tab) { rememberLazyListState() }
    // Once the part Today pointed at is the one shown: the save that switches it may land a frame later.
    LaunchedEffect(anchor, tab) {
        val (t, k) = anchor ?: return@LaunchedEffect
        if (t != tab) return@LaunchedEffect
        rows.indexOfFirst { it.key == k }.takeIf { it >= 0 }?.let { list.animateScrollToItem(it) }
        onAnchored()
    }
    LazyColumn(
        Modifier.fillMaxSize().bottomFade(list.canScrollForward).testTag("guild_list"), state = list,
        contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.sm, bottom = Space.lg),
    ) {
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
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = Space.sm).testTag("guild_assign_row_${r.heroId.value}"),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Face(r.portrait, 40)
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleSmall)
                        Secondary(r.sub)
                        r.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (r.action == AssignAction.NONE) Ember else Cream) }
                    }
                    when (r.action) {
                        AssignAction.RESERVE -> SecondaryActionButton("Keep", { a.send(Command.ReserveDefender(r.heroId, true)) }, Modifier.testTag("guild_reserve_${r.heroId.value}"), enabled = a.enabled)
                        AssignAction.RELEASE -> InlineActionButton("Release", { a.send(Command.ReserveDefender(r.heroId, false)) }, Modifier.testTag("guild_reserve_${r.heroId.value}"), enabled = a.enabled)
                        AssignAction.KEEP_HOME -> InlineActionButton("Keep home", {
                            a.confirm(Confirm("Keep the party home?", "Today's planned contract is cancelled and its fee comes back. You can plan another this morning.", "Keep home", Command.CancelPlannedDeployment))
                        }, Modifier.testTag("guild_keep_home_${r.heroId.value}"), enabled = a.enabled)
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
