package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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
import com.tinyblacksmith.core.model.Danger

/*
 * The four parts of the Guild destination as rows of one lazy list each (`GuildPanel`). Every part leads with what is
 * decided there and keeps the reading behind a heading that opens.
 */

// ---- Contracts: the party that is out, yesterday's contract, the board, what is still to come ----

internal fun contractRows(ui: GuildUi, a: GuildActions): List<GuildRow> = buildList {
    ui.party?.let { p -> add(GuildRow("party") { PartyCard(p, a.enabled) { id, choice -> a.send(Command.ChooseMissionCheckpoint(id, choice)) } }) }
    ui.yesterday?.let { y ->
        add(GuildRow("yesterday") {
            Collapsible("yesterday", "Yesterday · ${y.title}", y.outcome, openAtFirst = false, tag = "guild_yesterday") {
                Text(y.outcome, style = MaterialTheme.typography.bodyMedium)
                y.highlight?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Cream) }
                y.lines.forEach { Secondary(it) }
            }
        })
    }
    add(GuildRow("board") {
        Column {
            SectionTitle("The board")
            ui.plan?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Gold, modifier = Modifier.testTag("guild_plan")) }
            ui.boardNote?.let { Secondary(it, Modifier.testTag("guild_board_note")) }
        }
    })
    ui.offers.forEach { o -> add(GuildRow("offer_${o.id}") { OfferCard(o) { a.openContract(o.id) } }) }
    if (ui.upcoming.isNotEmpty()) add(GuildRow("upcoming") {
        Collapsible(
            "upcoming", "What comes next", if (ui.upcoming.size == 1) "1 contract not on the board yet" else "${ui.upcoming.size} contracts not on the board yet",
            openAtFirst = false, tag = "guild_upcoming",
        ) {
            ui.upcoming.forEachIndexed { i, u ->
                if (i > 0) RowRule()
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Chip("Not yet")
                    Column(Modifier.weight(1f)) {
                        Text(u.name, style = MaterialTheme.typography.titleSmall, color = CreamMuted)
                        Secondary(u.condition)
                    }
                }
            }
        }
    })
}

/** The party that is out: where, who, when they are home; at a checkpoint, the choice. */
@Composable
private fun PartyCard(p: PartyUi, enabled: Boolean, onChoose: (String, CheckpointChoice) -> Unit) {
    FramedPanel(modifier = Modifier.fillMaxWidth().padding(top = Space.sm).testTag("guild_party")) {
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

/** Safe in the body colour, a taken member in ember, a death in the flaw red: the word is always there beside the colour. */
private fun dangerColor(d: Danger) = when (d) { Danger.SAFE -> Cream; Danger.DANGEROUS -> Ember; Danger.LETHAL -> EffectKind.FLAW.color }

/**
 * One contract on the board: its name, the facts as chips (danger in word and colour, days, fee, what it brings besides
 * gold and stock), the first day's reward, and the way to choose its party. The rest is on the sheet the tap opens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OfferCard(o: OfferUi, onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().clickable(onClickLabel = "Choose a party", role = Role.Button, onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = Space.sm).testTag("guild_offer_${o.id}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(o.title, style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.weight(1f))
            if (o.planned) Chip("Planned", EffectKind.BUFF.color)
        }
        // The facts as one quiet line, not a row of plates: the danger in its word and colour, what it brings besides gold in gold.
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = dangerColor(o.danger))) { append(dangerWord(o.danger)) }
                append(" · " + (if (o.days == 1) "1 day" else "${o.days} days") + " · " + (if (o.feeGold > 0) "Fee ${o.feeGold}g" else "Free"))
                (listOfNotNull(o.tag) + o.specials).forEach { append(" · "); withStyle(SpanStyle(color = Gold)) { append(it) } }
            },
            style = MaterialTheme.typography.bodySmall, color = CreamMuted, modifier = Modifier.padding(top = 2.dp),
        )
        Text(o.reward, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.padding(top = Space.xs).testTag("guild_offer_line_${o.id}"))
        o.missesSiege?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember) }
        o.why?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember) }
        o.modifierName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Cream, modifier = Modifier.testTag("guild_offer_modifier_${o.id}")) }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Secondary(o.expiry, Modifier.weight(1f))
            Text(if (o.planned) "Change the party  ›" else "Choose a party  ›", style = MaterialTheme.typography.labelLarge, color = Gold)
        }
    }
}

// ---- Company: the roster, then who would sign ----

internal fun companyRows(ui: GuildUi, a: GuildActions): List<GuildRow> = buildList {
    add(GuildRow("roster_head") { SectionTitle(ui.rosterHead) })
    ui.roster.forEachIndexed { i, m ->
        add(GuildRow("member_${m.heroId.value}") {
            Column {
                if (i > 0) RowRule()
                MemberRow(m) { a.openHero(m.heroId) }
            }
        })
    }
    add(GuildRow("roster_note") { Secondary(ui.rosterNote, Modifier.padding(top = Space.sm).testTag("guild_roster_note")) })
    add(GuildRow("recruits") {
        Collapsible(
            "recruits", "Recruits", if (ui.candidates.isEmpty()) "Nobody would sign today" else "${ui.candidates.size} would sign today",
            openAtFirst = false, tag = "guild_candidates",
        ) {
            Secondary(ui.candidatesNote, Modifier.testTag("guild_candidates_note"))
            ui.candidates.forEachIndexed { i, c ->
                if (i > 0) RowRule()
                CandidateRow(c, a.enabled, onOpen = { a.openHero(c.heroId) }) { a.send(Command.RecruitHero(c.heroId)) }
            }
        }
    })
}

private fun chipColor(t: ChipTone) = when (t) { ChipTone.OK -> EffectKind.BUFF.color; ChipTone.KEPT -> Gold; ChipTone.HURT -> Ember; ChipTone.OUT -> EffectKind.FLAW.color }

/** A member: the face, the name with their state as a chip, class and health, what they carry; a gold line when a branch waits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberRow(m: MemberUi, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open details", onClick = onOpen).testTag("guild_member_${m.heroId.value}").heightIn(min = 56.dp).padding(vertical = Space.sm),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Face(m.portrait)
        Column(Modifier.weight(1f)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(m.name, style = MaterialTheme.typography.titleSmall)
                Chip(m.chip, chipColor(m.chipTone))
            }
            Secondary("${m.sub} · ${m.health}")
            Text(m.blade, style = MaterialTheme.typography.bodySmall, color = Cream)
            if (m.branchOpen) Text("Branch to choose  ›", style = MaterialTheme.typography.labelLarge, color = Gold)
        }
    }
}

@Composable
private fun CandidateRow(c: CandidateUi, enabled: Boolean, onOpen: () -> Unit, onRecruit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.sm).testTag("guild_candidate_${c.heroId.value}")) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Open details", onClick = onOpen).heightIn(min = 48.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Face(c.portrait)
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall)
                Secondary(c.line)
                Text(c.terms, style = MaterialTheme.typography.bodySmall, color = Cream)
            }
        }
        c.blocked?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember, modifier = Modifier.testTag("guild_recruit_blocked_${c.heroId.value}")) }
        SecondaryActionButton("Recruit", onRecruit, Modifier.fillMaxWidth().padding(top = Space.xs).testTag("guild_recruit_${c.heroId.value}"), enabled = enabled && c.blocked == null)
    }
}

// ---- Wall: who comes, the three places, then what is known of the night ----

internal fun wallRows(ui: GuildUi, a: GuildActions): List<GuildRow> = buildList {
    val w = ui.wall
    val eve = w.daysLeft <= 1
    add(GuildRow("wall") { WallHead(w) })
    add(GuildRow("places") { Places(ui, a) })
    w.prep?.let { p ->
        add(GuildRow("prep") {
            // What they prepare is this siege's own rule, with every answer the smith has: open, since it is decided before the night.
            Collapsible("prep", "Siege preparations", p.name, openAtFirst = true, tag = "guild_wall_prep") {
                Text(p.name, style = MaterialTheme.typography.titleSmall, color = Ember)
                Text(p.description, style = MaterialTheme.typography.bodyMedium)
                Secondary("Your answers", Modifier.padding(top = Space.xs))
                p.answers.forEach { Secondary("· $it", Modifier.padding(start = Space.sm)) }
                Secondary("· Or send a party to spoil it. ${p.sabotage}", Modifier.padding(start = Space.sm))
            }
        })
    }
    if (w.field.isNotEmpty()) add(GuildRow("field") {
        Collapsible("field", "Battlefield", w.field.joinToString(", ") { it.label }, openAtFirst = eve, tag = "guild_wall_field") { EnemyLines(w.field) }
    })
    if (w.missing.isNotEmpty()) add(GuildRow("missing") {
        Collapsible("missing", "Unavailable defenders", if (w.missing.size == 1) "1 member cannot stand" else "${w.missing.size} members cannot stand", openAtFirst = eve, tag = "guild_wall_missing") {
            w.missing.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ember) }
        }
    })
}

/** Who comes and when, what tells against them, the forge's health and the watch: the head of the Wall. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WallHead(w: WallUi) {
    FramedPanel(modifier = Modifier.fillMaxWidth().padding(top = Space.sm).testTag("guild_wall")) {
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            w.factionId?.let { id -> Sprites.faction(id)?.let { Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep)) { PixelImage(it, 56.dp, description = null) } } }
            Column(Modifier.weight(1f)) {
                Text(w.besieger, style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { heading() }.testTag("guild_wall_besieger"))
                Text(w.whenLine, style = MaterialTheme.typography.titleSmall, color = if (w.daysLeft <= 1) Ember else Cream, modifier = Modifier.testTag("guild_wall_when"))
                w.leaning?.let { Secondary(it) }
            }
        }
        if (w.weakTo != null || w.resists != null) FlowRow(Modifier.padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            w.weakTo?.let { MatchMark(EffectKind.BUFF, "Weak to $it") }
            w.resists?.let { MatchMark(EffectKind.FLAW, "Resists $it") }
        }
        w.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember, modifier = Modifier.padding(top = Space.xs)) }
        w.nemesis?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember, modifier = Modifier.padding(top = Space.xs).testTag("guild_wall_nemesis")) }
        w.spoiled?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EffectKind.BUFF.color, modifier = Modifier.padding(top = Space.xs).testTag("guild_wall_spoiled")) }
        HorizontalDivider(Modifier.padding(top = 12.dp, bottom = Space.sm), color = MaterialTheme.colorScheme.outlineVariant)
        StatBar("Forge health", w.forgeHealth, w.forgeHealthMax)
        StatRow("Watch", "${w.watch}", Modifier.padding(top = Space.xs))
        // The rule's first sentence (what the watch takes before the wall); how militia rises and falls is the Gazette's.
        Secondary(w.watchLine.substringBefore(". ").let { if (it.endsWith(".")) it else "$it." }, Modifier.testTag("guild_wall_watch_line"))
    }
}

/**
 * The three places: who stands in each (a member kept for the wall can be released here), and on an empty one the way to
 * fill it. When all are filled and a member could still be kept first, one link to the same sheet.
 */
@Composable
private fun Places(ui: GuildUi, a: GuildActions) {
    val w = ui.wall
    Column(Modifier.fillMaxWidth()) {
        SectionHeader("Who stands · ${w.defenders.size} of ${w.places}")
        (0 until w.places).forEach { i ->
            val d = w.defenders.getOrNull(i)
            Row(
                Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().then(if (d == null) Modifier else Modifier.clickable(onClickLabel = "Open details") { a.openHero(d.heroId) })
                    .heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = Space.sm).testTag("guild_place_$i"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.titleLarge, color = if (d == null) CreamMuted else Gold, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 20.dp))
                if (d == null) {
                    Column(Modifier.weight(1f)) {
                        Text("Empty", style = MaterialTheme.typography.titleSmall, color = Ember)
                        Secondary("Nobody else fit is free to stand.")
                    }
                    SecondaryActionButton("Assign", a.assign, Modifier.testTag("guild_assign_$i"), enabled = a.enabled)
                } else {
                    Face(d.portrait, 48)
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleSmall)
                        Text(d.blade, style = MaterialTheme.typography.bodySmall, color = Cream)
                        Secondary(d.sub + when { d.reserved -> " · kept for the wall"; d.member -> " · of the guild"; else -> " · of the town" })
                    }
                    if (d.reserved) InlineActionButton("Release", { a.send(Command.ReserveDefender(d.heroId, false)) }, Modifier.testTag("guild_reserve_${d.heroId.value}"), enabled = a.enabled)
                }
            }
        }
        if (w.defenders.size >= w.places && ui.assign.any { it.action == AssignAction.RESERVE }) {
            InlineActionButton("Keep a member for the wall  ›", a.assign, Modifier.testTag("guild_keep_open"))
        }
    }
}

// ---- Charter: what it is, what opens, then the region ----

internal fun charterRows(ui: GuildUi, a: GuildActions): List<GuildRow> = buildList {
    val r = ui.region
    add(GuildRow("charter") {
        // How the charter is secured is the first step under it; here only what the charter is.
        Column(Modifier.fillMaxWidth().testTag("guild_region")) {
            SectionTitle(r.charter, Modifier.testTag("guild_charter"))
            if (r.advantage.isNotEmpty()) Text("${EffectKind.BUFF.sign} ${r.advantage}", style = MaterialTheme.typography.bodySmall, color = EffectKind.BUFF.color)
            if (r.constraint.isNotEmpty()) Text("${EffectKind.FLAW.sign} ${r.constraint}", style = MaterialTheme.typography.bodySmall, color = EffectKind.FLAW.color)
        }
    })
    add(GuildRow("opens") {
        Column(Modifier.fillMaxWidth().testTag("guild_steps")) {
            SectionHeader("What opens")
            ui.steps.forEach { s -> Step(s, r, a) }
        }
    })
    fun block(key: String, title: String, lines: List<String>) {
        if (lines.isNotEmpty()) add(GuildRow(key) {
            Collapsible(key, title, lines.first(), openAtFirst = false, tag = "guild_$key") {
                lines.forEachIndexed { i, line -> if (i == 0) Text(line, style = MaterialTheme.typography.bodyMedium) else Secondary(line) }
            }
        })
    }
    block("law", "Law of the season", r.law)
    block("rival", "The other guild", r.rival)
    block("nemesis", "Nemesis", r.nemesis)
    block("chains", "Chains you have seen", r.combos)
    if (ui.factions.isNotEmpty()) add(GuildRow("factions") {
        Collapsible("factions", "Pressing on the town", ui.factions.first().name, openAtFirst = false, tag = "guild_factions") { FactionRows(ui.factions) }
    })
}

/** One thing the charter opens: its state in a word, the rule, what it changes; the action where it is open. */
@Composable
private fun Step(s: StepUi, r: RegionUi, a: GuildActions) {
    val (word, color) = when (s.state) { StepState.DONE -> "Done" to EffectKind.BUFF.color; StepState.OPEN -> "Open" to Gold; StepState.LOCKED -> "Not yet" to CreamMuted }
    Column(Modifier.guildCard().testTag("guild_step_${s.key}")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text(s.name, style = MaterialTheme.typography.titleSmall, color = if (s.state == StepState.LOCKED) CreamMuted else Cream, modifier = Modifier.weight(1f))
            Chip(word, color)
        }
        Secondary(s.condition)
        Text(s.effect, style = MaterialTheme.typography.bodySmall, color = Cream)
        if (s.key == "charter" && s.state == StepState.DONE) {
            r.retireBlocked?.let { Secondary(it) }
            SecondaryActionButton("Retire the chapter", {
                a.confirm(Confirm(
                    "Retire the chapter?", "The run ends here as a success: the forge stands, the charter is secured, and its legacy can be claimed. This cannot be taken back.",
                    "Retire the chapter", Command.RetireAfterMilestone(GuildContent.CHARTER_SECURED),
                ))
            }, Modifier.fillMaxWidth().padding(top = Space.xs).testTag("guild_retire"), enabled = a.enabled && r.retireBlocked == null)
        }
        r.ranks.firstOrNull { "rank_${it.rank}" == s.key && it.canTake }?.let { rank ->
            InlineActionButton("Take rank", {
                a.confirm(Confirm("Take the rank of ${rank.name}?", "${rank.description} A rank is kept once taken.", "Take rank", Command.SetGuildRank(rank.rank)))
            }, Modifier.align(Alignment.End).testTag("guild_rank_${rank.rank}"), enabled = a.enabled)
        }
    }
}
