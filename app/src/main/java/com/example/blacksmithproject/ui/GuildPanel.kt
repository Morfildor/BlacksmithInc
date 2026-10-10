package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.Sheet
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Ember
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.model.CheckpointChoice
import com.tinyblacksmith.core.model.HeroId

/** A choice that cannot be taken back, asked once more before it is sent. */
private data class Confirm(val title: String, val body: String, val action: String, val command: Command)

/**
 * The Guild destination of a guild run, in the place Town has in a classic run: the party that is out, the board, the
 * roster, the wall (first when the siege is today or tomorrow), who would sign, the region, then the town's own people. One lazy list; every row that changes
 * something sends a command and waits for the save.
 */
@Composable
fun GuildPanel(s: UiState.Playing, ui: GuildUi, vm: GameViewModel, modifier: Modifier = Modifier) {
    val st = s.state
    val (living, gone) = remember(st.heroes, st.guild?.members) {
        st.residents().sortedByDescending { it.fame } to st.heroes.values.filter { !it.isAlive }.sortedByDescending { it.fame }
    }
    var showGone by rememberSaveable { mutableStateOf(false) }
    // The contract whose sheet is open, by its ID on the board.
    var contract by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val openHero = { id: HeroId -> vm.openSheet(Sheet.Hero(id)) }
    val enabled = !s.busy
    LazyColumn(modifier.fillMaxSize().testTag("guild_list"), contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.sm, bottom = Space.lg)) {
        // What asks for a decision comes first: a party waiting for word always; the wall on the siege day and the day before it.
        // On any other morning the wall stands after the board and the roster, where it is one scroll away, not in the way.
        val wallFirst = st.town.nextSiegeDay - st.day <= 1
        val wall = { item(key = "wall") { WallCard(ui.wall, enabled, openHero) { id, keep -> vm.dispatch(Command.ReserveDefender(id, keep)) } } }
        ui.party?.let { p -> item(key = "party") { PartyCard(p, enabled) { id, choice -> vm.dispatch(Command.ChooseMissionCheckpoint(id, choice)) } } }
        if (wallFirst) wall()
        ui.yesterday?.let { y -> item(key = "yesterday") { ReportCard(y) } }
        item(key = "board_head") {
            Column {
                SectionTitle("The board")
                ui.plan?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Gold, modifier = Modifier.testTag("guild_plan")) }
                ui.boardNote?.let { Secondary(it, Modifier.testTag("guild_board_note")) }
            }
        }
        items(ui.offers, key = { "offer_${it.id}" }) { o -> OfferCard(o) { contract = o.id } }
        item(key = "roster_head") { SectionTitle(ui.rosterHead) }
        itemsIndexed(ui.roster, key = { _, m -> "member_${m.heroId.value}" }) { i, m ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MemberRow(m) { openHero(m.heroId) }
        }
        if (!wallFirst) wall()
        item(key = "candidates_head") {
            Column {
                SectionTitle("Would sign today")
                Secondary(ui.candidatesNote, Modifier.testTag("guild_candidates_note"))
            }
        }
        itemsIndexed(ui.candidates, key = { _, c -> "candidate_${c.heroId.value}" }) { i, c ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CandidateRow(c, enabled, onOpen = { openHero(c.heroId) }) { vm.dispatch(Command.RecruitHero(c.heroId)) }
        }
        item(key = "region") {
            RegionCard(
                ui.region, enabled,
                onRetire = {
                    confirm = Confirm(
                        "Retire the chapter?", "The run ends here as a success: the forge stands, the charter is secured, and its legacy can be claimed. This cannot be taken back.",
                        "Retire the chapter", Command.RetireAfterMilestone(GuildContent.CHARTER_SECURED),
                    )
                },
                onRank = { r -> confirm = Confirm("Take the rank of ${r.name}?", "${r.description} A rank is kept once taken.", "Take rank", Command.SetGuildRank(r.rank)) },
            )
        }
        if (ui.factions.isNotEmpty()) item(key = "factions") {
            Column {
                SectionHeader("Pressing on the town")
                FactionRows(ui.factions)
            }
        }
        item(key = "heroes_head") { SectionTitle("Townsfolk · ${living.size} in town") }
        itemsIndexed(living, key = { _, h -> "hero_${h.id.value}" }) { i, h ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HeroRow(h, s, vm)
        }
        if (gone.isNotEmpty()) {
            item(key = "gone_head") {
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.md).clickable(onClickLabel = if (showGone) "Hide" else "Show", role = Role.Button) { showGone = !showGone }.heightIn(min = 48.dp).testTag("town_fallen"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Fallen and retired (${gone.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(if (showGone) "Hide  ▴" else "Show  ▾", style = MaterialTheme.typography.labelLarge, color = Gold)
                }
            }
            if (showGone) itemsIndexed(gone, key = { _, h -> "hero_${h.id.value}" }) { i, h ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                HeroRow(h, s, vm)
            }
        }
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
    confirm?.let { c ->
        AlertDialog(
            modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("guild_confirm"),
            onDismissRequest = { confirm = null },
            title = { Text(c.title) },
            text = { Text(c.body) },
            confirmButton = { InlineActionButton(c.action, { confirm = null; vm.dispatch(c.command) }, Modifier.testTag("guild_confirm_yes")) },
            dismissButton = { InlineActionButton("Not now", { confirm = null }, Modifier.testTag("guild_confirm_no")) },
        )
    }
}

/** A face in its dark slot, as every row of Town has it. */
@Composable
private fun Face(portrait: Int, size: Int = 44) {
    Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep)) { PixelImage(portrait, size.dp, description = null) }
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

/** The coming siege as a guild run meets it: who comes, who stands in the three places, who is missing and why. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WallCard(w: WallUi, enabled: Boolean, onOpenHero: (HeroId) -> Unit, onReserve: (HeroId, Boolean) -> Unit) {
    FramedPanel(modifier = Modifier.fillMaxWidth().padding(top = Space.sm).testTag("guild_wall")) {
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            w.factionId?.let { id -> Sprites.faction(id)?.let { Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep)) { PixelImage(it, 56.dp, description = null) } } }
            Column(Modifier.weight(1f)) {
                Text("The wall", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { heading() })
                Text(w.whenLine, style = MaterialTheme.typography.titleSmall, color = if (w.daysLeft <= 1) Ember else Cream, modifier = Modifier.testTag("guild_wall_when"))
                Text(w.besieger, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("guild_wall_besieger"))
                w.leaning?.let { Secondary(it) }
            }
        }
        if (w.weakTo != null || w.resists != null) FlowRow(Modifier.padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            w.weakTo?.let { MatchMark(EffectKind.BUFF, "Weak to $it") }
            w.resists?.let { MatchMark(EffectKind.FLAW, "Resists $it") }
        }
        w.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember, modifier = Modifier.padding(top = Space.xs)) }
        if (w.field.isNotEmpty()) {
            SectionHeader("Their field")
            EnemyLines(w.field, Modifier.testTag("guild_wall_field"))
        }
        HorizontalDivider(Modifier.padding(top = 12.dp, bottom = Space.sm), color = MaterialTheme.colorScheme.outlineVariant)
        StatRow("Watch", "${w.watch}")
        Secondary("Militia and the watch armory thin the enemy before the wall.")
        StatBar("Forge health", w.forgeHealth, w.forgeHealthMax)

        SectionHeader("Who stands")
        (0 until w.places).forEach { i ->
            val d = w.defenders.getOrNull(i)
            Row(
                Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().then(if (d == null) Modifier else Modifier.clickable(onClickLabel = "Open details") { onOpenHero(d.heroId) })
                    .heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = Space.sm).testTag("guild_place_$i"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.titleLarge, color = if (d == null) CreamMuted else Gold, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 20.dp))
                if (d == null) Secondary("Empty: nobody else in town is fit to stand.", Modifier.weight(1f))
                else {
                    Face(d.portrait, 48)
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleSmall)
                        Text(d.blade, style = MaterialTheme.typography.bodySmall, color = Cream)
                        Secondary(d.sub + when { d.reserved -> " · kept for the wall"; d.member -> " · of the guild"; else -> " · of the town" })
                    }
                }
            }
        }
        // Keeping a member for the wall puts them in a place first and out of any party; it can be taken back any morning.
        if (w.reserve.isNotEmpty()) {
            Secondary("Keep a member for the wall: they stand first, and no party takes them.", Modifier.padding(top = Space.sm))
            w.reserve.forEach { r ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name + if (r.reserved) " · kept" else "", style = MaterialTheme.typography.bodyMedium, color = if (r.reserved) Gold else Cream)
                        r.blocked?.let { Secondary(it) }
                    }
                    InlineActionButton(
                        if (r.reserved) "Release" else "Reserve", { onReserve(r.heroId, !r.reserved) },
                        Modifier.testTag("guild_reserve_${r.heroId.value}"), enabled = enabled && r.blocked == null,
                    )
                }
            }
        }
        if (w.missing.isNotEmpty()) {
            SectionHeader("Missing")
            Column(Modifier.testTag("guild_wall_missing")) { w.missing.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ember) } }
        }
    }
}

/** The party that is out: where, who, when they are home; at a checkpoint, the choice. */
@Composable
private fun PartyCard(p: PartyUi, enabled: Boolean, onChoose: (String, CheckpointChoice) -> Unit) {
    FramedPanel(modifier = Modifier.fillMaxWidth().padding(top = Space.md).testTag("guild_party")) {
        Text("The party · ${p.title}", style = MaterialTheme.typography.titleMedium, color = Gold, modifier = Modifier.semantics { heading() })
        Text(p.who, style = MaterialTheme.typography.bodyMedium)
        Text(p.returns, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("guild_party_returns"))
        p.status?.let { Secondary(it) }
        p.checkpoint?.let { c ->
            HorizontalDivider(Modifier.padding(vertical = Space.sm), color = MaterialTheme.colorScheme.outlineVariant)
            Text(c.secured, style = MaterialTheme.typography.bodyMedium, color = EffectKind.BUFF.color)
            Text(c.deeper, style = MaterialTheme.typography.bodyMedium)
            Text(c.danger, style = MaterialTheme.typography.bodyMedium, color = Ember)
            EnemyLines(c.enemies, Modifier.padding(top = Space.xs))
            Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                SecondaryActionButton("Push deeper", { onChoose(c.instanceId, CheckpointChoice.PUSH) }, Modifier.weight(1f).testTag("guild_push"), enabled = enabled && c.chosen != CheckpointChoice.PUSH)
                SecondaryActionButton("Return", { onChoose(c.instanceId, CheckpointChoice.RETURN) }, Modifier.weight(1f).testTag("guild_return"), enabled = enabled && c.chosen != CheckpointChoice.RETURN)
            }
            Secondary(c.note, Modifier.padding(top = Space.xs).testTag("guild_checkpoint_note"))
        }
    }
}

@Composable
private fun ReportCard(r: ContractReportUi) {
    Column(Modifier.fillMaxWidth().padding(top = Space.md).forgeRow().padding(horizontal = 12.dp, vertical = Space.sm).testTag("guild_yesterday")) {
        Text("Yesterday's contract · ${r.title}", style = MaterialTheme.typography.titleSmall, color = Gold)
        Text(r.outcome, style = MaterialTheme.typography.bodyMedium)
        r.highlight?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Cream) }
        r.lines.forEach { Secondary(it) }
    }
}

/** One contract on the board, whole: everything that decides whether to take it is on the card; the tap opens the party picker. */
@Composable
private fun OfferCard(o: OfferUi, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().clickable(onClickLabel = "Choose a party", role = Role.Button, onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = Space.sm).testTag("guild_offer_${o.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(o.title, style = MaterialTheme.typography.titleMedium, color = Gold, modifier = Modifier.weight(1f))
            if (o.planned) Text("Planned", style = MaterialTheme.typography.labelMedium, color = EffectKind.BUFF.color)
        }
        o.why?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ember) }
        Text(o.description, style = MaterialTheme.typography.bodyMedium)
        Text(o.time, style = MaterialTheme.typography.bodySmall, color = Cream, modifier = Modifier.padding(top = Space.xs))
        o.missesSiege?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember) }
        Secondary(o.fee)
        o.stages.forEach { st ->
            Text(st.label, style = MaterialTheme.typography.labelLarge, color = Gold, modifier = Modifier.padding(top = Space.sm))
            Text(st.reward, style = MaterialTheme.typography.bodyMedium)
            Secondary("${st.objective} · ${st.danger}")
            EnemyLines(st.enemies)
        }
        Secondary("Bring: ${o.prep}", Modifier.padding(top = Space.sm))
        Secondary("If it fails: ${o.failure}")
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Secondary(o.expiry, Modifier.weight(1f))
            Text(if (o.planned) "Change the party  ›" else "Choose a party  ›", style = MaterialTheme.typography.labelLarge, color = Gold)
        }
    }
}

@Composable
private fun MemberRow(m: MemberUi, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open details", onClick = onOpen).testTag("guild_member_${m.heroId.value}").heightIn(min = 48.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Face(m.portrait)
        Column(Modifier.weight(1f)) {
            Text(m.name, style = MaterialTheme.typography.titleSmall)
            Secondary("${m.sub} · ${m.health}" + if (m.reserved) " · kept for the wall" else "")
            m.status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember) }
            Text(m.blade, style = MaterialTheme.typography.bodySmall, color = Cream)
            Secondary(m.role)
            Secondary(m.trait)
            if (m.branchOpen) Text("A branch is open: choose it on their sheet  ›", style = MaterialTheme.typography.bodySmall, color = Gold)
        }
    }
}

@Composable
private fun CandidateRow(c: CandidateUi, enabled: Boolean, onOpen: () -> Unit, onRecruit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("guild_candidate_${c.heroId.value}")) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Open details", onClick = onOpen).heightIn(min = 48.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Face(c.portrait)
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall)
                Secondary(c.sub)
                Secondary(c.role)
                Secondary(c.trait)
                Text(c.terms, style = MaterialTheme.typography.bodySmall, color = Cream)
            }
        }
        c.blocked?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember, modifier = Modifier.testTag("guild_recruit_blocked_${c.heroId.value}")) }
        SecondaryActionButton("Recruit", onRecruit, Modifier.fillMaxWidth().padding(top = Space.xs).testTag("guild_recruit_${c.heroId.value}"), enabled = enabled && c.blocked == null)
    }
}

/** The charter and what it asks, the law of the season, the other guild, the one who carries a blade of the forge, the chains seen so far. */
@Composable
private fun RegionCard(r: RegionUi, enabled: Boolean, onRetire: () -> Unit, onRank: (RankUi) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("guild_region")) {
        SectionTitle("The region")
        Text(r.charter, style = MaterialTheme.typography.titleMedium, color = Gold)
        if (r.advantage.isNotEmpty()) Text("${EffectKind.BUFF.sign} ${r.advantage}", style = MaterialTheme.typography.bodySmall, color = EffectKind.BUFF.color)
        if (r.constraint.isNotEmpty()) Text("${EffectKind.FLAW.sign} ${r.constraint}", style = MaterialTheme.typography.bodySmall, color = EffectKind.FLAW.color)
        Text(r.progress, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs).testTag("guild_charter"))
        if (r.secured) {
            r.retireBlocked?.let { Secondary(it) }
            SecondaryActionButton("Retire the chapter", onRetire, Modifier.fillMaxWidth().padding(top = Space.xs).testTag("guild_retire"), enabled = enabled && r.retireBlocked == null)
            if (r.ranks.isNotEmpty()) SectionHeader("Ranks")
            r.ranks.forEach { rank ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Column(Modifier.weight(1f)) {
                        Text(rank.name + if (rank.taken) " · taken" else "", style = MaterialTheme.typography.titleSmall, color = if (rank.taken) Gold else Cream)
                        Secondary(rank.description)
                    }
                    if (rank.canTake) InlineActionButton("Take rank", { onRank(rank) }, Modifier.testTag("guild_rank_${rank.rank}"), enabled = enabled)
                }
            }
        }
        Block("Law of the season", r.law)
        Block("The other guild", r.rival)
        Block("Nemesis", r.nemesis)
        Block("Chains you have seen", r.combos)
    }
}

/** A headed block of lines, the first of them the name; nothing when there is nothing to say. */
@Composable
private fun Block(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    SectionHeader(title)
    lines.forEachIndexed { i, line -> if (i == 0) Text(line, style = MaterialTheme.typography.bodyMedium) else Secondary(line) }
}
