package com.example.blacksmithproject.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
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

/*
 * The four parts of the Guild destination as rows of one lazy list each (`GuildPanel`). Every part leads with what is
 * decided there and keeps the reading behind a heading that opens.
 */

// ---- Contracts: the party that is out, yesterday's contract, the board, what is still to come ----

internal fun contractRows(ui: GuildUi, a: GuildActions): List<GuildRow> = buildList {
    ui.party?.let { p -> add(GuildRow("party") { PartyCard(p, a.enabled) { id, choice -> a.send(Command.ChooseMissionCheckpoint(id, choice)) } }) }
    add(GuildRow("board") {
        Column {
            SectionTitle("The quest board")
            ui.plan?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Gold, modifier = Modifier.testTag("guild_plan")) }
            ui.boardNote?.let { Secondary(it, Modifier.testTag("guild_board_note")) }
        }
    })
    ui.offers.forEach { o -> add(GuildRow("offer_${o.id}") { OfferCard(o) { a.openContract(o.id) } }) }
    ui.yesterday?.let { y ->
        add(GuildRow("yesterday") {
            Collapsible("yesterday", "Yesterday · ${y.title}", y.outcome, openAtFirst = false, tag = "guild_yesterday") {
                Text(y.outcome, style = MaterialTheme.typography.bodyMedium)
                y.highlight?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Cream) }
                y.lines.forEach { Secondary(it) }
            }
        })
    }
    if (ui.upcoming.isNotEmpty()) add(GuildRow("upcoming") {
        Collapsible(
            "upcoming", "What comes next", if (ui.upcoming.size == 1) "1 quest not on the board yet" else "${ui.upcoming.size} quests not on the board yet",
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

/**
 * The party that is out, on its own plate (an accepted quest, never a board row): where, then who and the morning they
 * are back as one line of facts, how it stands; at a checkpoint, the choice with what is won, what deeper adds and its danger.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartyCard(p: PartyUi, enabled: Boolean, onChoose: (String, CheckpointChoice) -> Unit) {
    FramedPanel(modifier = Modifier.fillMaxWidth().padding(top = Space.sm).testTag("guild_party")) {
        Text("The party · ${p.title}", style = MaterialTheme.typography.titleMedium, color = Gold, modifier = Modifier.semantics { heading() })
        FactLine(listOf(Fact(p.who, "Party"), Fact("day ${p.returnDay}", "Back")), Modifier.padding(top = 2.dp).testTag("guild_party_returns"), emphasis = true)
        p.status?.let { Secondary(it) }
        p.checkpoint?.let { c ->
            HorizontalDivider(Modifier.padding(vertical = Space.sm), color = MaterialTheme.colorScheme.outlineVariant)
            Text(c.secured, style = MaterialTheme.typography.bodyMedium, color = EffectKind.BUFF.color)
            Text(c.deeper, style = MaterialTheme.typography.bodyMedium)
            Text(c.danger, style = MaterialTheme.typography.bodyMedium, color = Ember)
            EnemyLines(c.enemies, Modifier.padding(top = Space.xs))
            FlowRow(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                SecondaryActionButton("Go deeper", { onChoose(c.instanceId, CheckpointChoice.PUSH) }, Modifier.testTag("guild_push"), enabled = enabled && c.chosen != CheckpointChoice.PUSH)
                SecondaryActionButton("Return", { onChoose(c.instanceId, CheckpointChoice.RETURN) }, Modifier.testTag("guild_return"), enabled = enabled && c.chosen != CheckpointChoice.RETURN)
            }
            Secondary(c.note, Modifier.padding(top = Space.xs).testTag("guild_checkpoint_note"))
        }
    }
}

/**
 * One quest on the board, comparable at a glance: its name, then the summary as one line of facts (reward, danger, how
 * long and the morning they are back, the fee, the last day to send), the name of what is different about this posting,
 * a missed siege as its own warning, and the way to the party sheet. Each stage, the modifier's rule, what to bring and
 * what failing costs are on its card (the ringed "i") and in the party sheet. A planned quest says so in a chip.
 */
@Composable
private fun OfferCard(o: OfferUi, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Choose a party", role = Role.Button, onClick = onOpen)
                .padding(top = Space.xs, bottom = Space.xs).testTag("guild_offer_${o.id}"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(o.title, style = MaterialTheme.typography.titleMedium, color = Cream, modifier = Modifier.weight(1f))
                if (o.planned) Chip("Planned", EffectKind.BUFF.color)
                InfoButton(o.title, { offerTip(o, onOpen, tag = "guild_offer_tip") }, tag = "guild_offer_${o.id}_info")
            }
            // A personal offer: who asks for it and why, before the terms. The one-party rule follows the terms, plainly.
            o.sponsor?.let { sp ->
                Row(Modifier.padding(top = Space.xs, bottom = Space.xs).testTag("guild_offer_sponsor_${o.id}"), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Face(sp.portrait)
                    Column(Modifier.weight(1f)) {
                        Text("Asked by ${sp.name}", style = MaterialTheme.typography.titleSmall, color = Cream)
                        sp.why?.let { Secondary(it) }
                    }
                }
            }
            FactLine(offerFacts(o), Modifier.testTag("guild_offer_terms_${o.id}"), emphasis = true)
            o.sponsor?.let { Secondary(it.oneParty, Modifier.padding(top = 2.dp).testTag("guild_offer_one_party_${o.id}")) }
            o.modifierName?.let { name ->
                Text(
                    buildAnnotatedString { withStyle(SpanStyle(color = TipTone.CONDITION.color)) { append("${TipTone.CONDITION.sign} ") }; append(name) },
                    style = MaterialTheme.typography.bodySmall, color = Cream, modifier = Modifier.padding(top = 2.dp).testTag("guild_offer_modifier_${o.id}"),
                )
            }
            o.missesSiege?.let { FactLine(listOf(Fact(it, tone = FactTone.WARN)), Modifier.padding(top = 2.dp)) }
            Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (o.planned) "Change the party" else "Choose a party", style = MaterialTheme.typography.labelLarge, color = Cream)
                Chevron(ChevronWay.RIGHT, tint = CreamMuted, size = 18.dp)
            }
        }
        HorizontalDivider(color = BronzeDeep)
    }
}

// ---- Company: the roster, then who would sign ----

internal fun companyRows(ui: GuildUi, a: GuildActions): List<GuildRow> = buildList {
    add(GuildRow("roster_head") { SectionTitle(ui.rosterHead) })
    ui.roster.forEachIndexed { i, m ->
        add(GuildRow("member_${m.heroId.value}") {
            Column {
                if (i > 0) RowRule()
                MemberRow(m) { a.heroTip(m.heroId, false) }
            }
        })
    }
    add(GuildRow("roster_note") { Secondary(ui.rosterNote, Modifier.padding(top = Space.sm).testTag("guild_roster_note")) })
    add(GuildRow("recruits") {
        Collapsible(
            "recruits", "For hire", if (ui.candidates.isEmpty()) "Nobody is for hire today" else "${ui.candidates.size} for hire today",
            openAtFirst = false, tag = "guild_candidates",
        ) {
            Secondary(ui.candidatesNote, Modifier.testTag("guild_candidates_note"))
            ui.candidates.forEachIndexed { i, c ->
                if (i > 0) RowRule()
                CandidateRow(c, a.enabled, tip = { a.heroTip(c.heroId, false) }) { a.send(Command.RecruitHero(c.heroId)) }
            }
        }
    })
}

private fun chipColor(t: ChipTone) = when (t) { ChipTone.OK -> EffectKind.BUFF.color; ChipTone.KEPT -> Gold; ChipTone.HURT -> Ember; ChipTone.OUT -> EffectKind.FLAW.color }

/**
 * A hero's row that is read, not acted on: a tap opens the hero's card by the row ([tip]); the card's "Full details" is
 * the sheet with equipment, loans and branches. Long press is not needed: the tap is the way in.
 */
@Composable
private fun Modifier.heroInspect(name: String, tip: () -> TipUi?): Modifier {
    val inspector = LocalInspector.current
    val anchor = remember { InspectAnchor() }
    return inspectAnchor(anchor).clickable(onClickLabel = "Inspect $name", role = Role.Button) { tip()?.let { inspector?.show(it, anchor.rect) } }
}

/** The weapon a hero holds as a small sprite (its name is said, the art is decoration), or "Unarmed" in words. */
@Composable
private fun HeldWeapon(art: Int?, name: String) {
    if (art != null) PixelImage(art, 32.dp, description = name)
    else Text("Unarmed", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
}

/**
 * A line led by the condition sign (◇), which hangs in its own column so a wrapped sentence lines up under its first
 * word. The sign and the words carry the meaning, never the colour alone; TalkBack reads the words.
 */
@Composable
internal fun SignedLine(text: String, color: Color, signColor: Color, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 2.dp).semantics(mergeDescendants = true) { contentDescription = text }, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(TipTone.CONDITION.sign, style = MaterialTheme.typography.bodySmall, color = signColor)
        Text(text.keepNumbersTogether(), style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.weight(1f))
    }
}

/**
 * Whether a row has room for the weapon beside the identity: the identity keeps about 190 dp of text at the default size
 * once the face (44 dp), the gaps and the weapon (up to 64 dp) are taken. With less (a narrow phone at 1.3x text) the
 * weapon goes under the identity instead of squeezing the name and class into a word a line.
 */
@Composable
private fun roomBeside(maxWidth: Dp): Boolean = (maxWidth - 132.dp) / LocalDensity.current.fontScale >= 190.dp

/**
 * A member at a glance, in reading order: the face, the name with one chip (fit, a branch to choose, kept for the wall,
 * laid up, away or held), people, class and level, health, then the weapon in hand: beside the text when there is room,
 * under it when there is not. The face lines up with the name. A tap reads their card ([tip]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberRow(m: MemberUi, tip: () -> TipUi?) {
    BoxWithConstraints(Modifier.fillMaxWidth().heroInspect(m.name, tip).testTag("guild_member_${m.heroId.value}").heightIn(min = 64.dp).padding(vertical = Space.sm)) {
        val beside = roomBeside(maxWidth)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Face(m.portrait)
            Column(Modifier.weight(1f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(m.shortName, style = MaterialTheme.typography.titleSmall)
                    Chip(m.chip, chipColor(m.chipTone))
                }
                m.byname?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CreamMuted) }
                FactFlow(listOf(Fact(m.sub), Fact(m.health)), Modifier.testTag("guild_member_line_${m.heroId.value}"))
                // A living era: the member's own decision in a few words; a departure in ember. The sheet says it in full.
                m.notice?.let { SignedLine(it, if (m.urgent) Ember else Cream, if (m.urgent) Ember else TipTone.CONDITION.color, Modifier.testTag("guild_member_notice_${m.heroId.value}")) }
                if (!beside) Row(Modifier.padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    m.weaponArt?.let { PixelImage(it, 32.dp, description = null) }
                    Text(m.blade, style = MaterialTheme.typography.bodySmall, color = if (m.weaponArt == null) CreamMuted else Cream)
                }
            }
            if (beside) HeldWeapon(m.weaponArt, m.blade)
        }
    }
}

/**
 * Who would sign, in reading order: the face beside the name (a byname of residence on its own quieter line), people,
 * class, level and trait, the fee and the share in figures, then their Hook, all before Recruit. A tap reads their card.
 */
@Composable
private fun CandidateRow(c: CandidateUi, enabled: Boolean, tip: () -> TipUi?, onRecruit: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = Space.sm).testTag("guild_candidate_${c.heroId.value}")) {
        Row(Modifier.fillMaxWidth().heroInspect(c.name, tip).heightIn(min = 48.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Face(c.portrait)
            Column(Modifier.weight(1f)) {
                Text(c.shortName, style = MaterialTheme.typography.titleSmall)
                c.byname?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CreamMuted) }
                FactFlow(listOf(Fact(c.sub), Fact(c.trait.substringBefore(':'), "Trait")))
                FactFlow(listOf(Fact("${c.feeGold} gold", "Fee"), Fact("${c.sharePercent}% of quest gold", "Keeps")))
                // Their Hook before the fee is paid: what comes with them, an oath above all (final design 20.4). A sign and words,
                // not colour alone; the sign hangs in its own column so a wrapped sentence lines up under its first word.
                c.complication?.let { SignedLine(it, Cream, TipTone.CONDITION.color, Modifier.testTag("guild_candidate_hook_${c.heroId.value}")) }
            }
        }
        c.blocked?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember, modifier = Modifier.testTag("guild_recruit_blocked_${c.heroId.value}")) }
        SecondaryActionButton("Recruit", onRecruit, Modifier.fillMaxWidth().padding(top = Space.xs).testTag("guild_recruit_${c.heroId.value}"), enabled = enabled && c.blocked == null)
    }
}

// ---- Wall: who stands and the gaps, the forge and the watch, what is known of the enemy, then the details ----

internal fun wallRows(ui: GuildUi, a: GuildActions): List<GuildRow> = buildList {
    val w = ui.wall
    val eve = w.daysLeft <= 1
    add(GuildRow("readiness") { Readiness(ui, a) })
    add(GuildRow("watch") {
        // Forge health and the watch as one block: the bar, then the watch's worth in the rule's own numbers.
        Column(Modifier.fillMaxWidth().padding(top = Space.md).testTag("guild_wall_watch")) {
            StatBar("Forge health", w.forgeHealth, w.forgeHealthMax)
            FactLine(listOf(Fact("${w.watch}", "Militia and armory"), Fact(firstSentence(w.watchLine))), Modifier.padding(top = Space.xs).testTag("guild_wall_watch_line"))
        }
    })
    if (w.weakTo != null || w.resists != null || (w.prep == null && !w.committed) || w.leaning != null || w.nemesis != null || w.spoiled != null) add(GuildRow("wall") { WallHead(w) })
    if (w.missing.isNotEmpty()) add(GuildRow("missing") {
        Collapsible("missing", "Unavailable defenders", if (w.missing.size == 1) "1 member cannot stand" else "${w.missing.size} members cannot stand", openAtFirst = eve, tag = "guild_wall_missing") {
            w.missing.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ember) }
        }
    })
    w.prep?.let { p ->
        add(GuildRow("prep") {
            // The preparation by name while closed; what it does and every answer once opened (open on the eve and the day).
            Collapsible("prep", "Their preparation: ${p.name}", "What it does and your answers", openAtFirst = eve, tag = "guild_wall_prep", tagWhole = true) {
                Text(p.description, style = MaterialTheme.typography.bodyMedium)
                InlineActionButton("Review member equipment", a.assign, Modifier.heightIn(min = 48.dp).testTag("guild_prep_equipment"), enabled = a.enabled)
                p.answers.forEach { Secondary("· $it", Modifier.padding(start = Space.sm)) }
                Secondary("· Or send a party to spoil it. ${p.sabotage}", Modifier.padding(start = Space.sm))
                ui.offers.firstOrNull { offer -> offer.specials.contains("Spoils their preparation") }?.let { offer ->
                    InlineActionButton("Review sabotage quest", { a.openContract(offer.id) }, Modifier.heightIn(min = 48.dp).testTag("guild_prep_quest"), enabled = a.enabled)
                }
            }
        })
    }
    if (w.field.isNotEmpty()) add(GuildRow("field") {
        Collapsible("field", "Battlefield", w.field.joinToString(", ") { it.label }, openAtFirst = eve, tag = "guild_wall_field") { EnemyLines(w.field) }
    })
}

/**
 * Who stands and where the gaps are, first: the reason a place is weak (or, near the siege, that the weapons are sound
 * and that this is no promise), the three places, and the one way to change them.
 */
@Composable
private fun Readiness(ui: GuildUi, a: GuildActions) {
    val w = ui.wall
    Column(Modifier.fillMaxWidth().padding(top = Space.sm).testTag("guild_wall_readiness")) {
        SectionTitle("Defenders")
        val reason = w.readinessReason
        if (reason != null) Text(reason, style = MaterialTheme.typography.bodyMedium, color = Ember)
        else if (w.daysLeft in 0..2) Secondary("The available defenders have weapons in good condition. This does not guarantee victory.")
        w.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember) }
        Places(ui, a)
        if (w.rulesLine.isNotEmpty()) Secondary(w.rulesLine, Modifier.padding(top = Space.xs).testTag("guild_wall_rules"))
        SecondaryActionButton("Review defenders", a.assign, Modifier.fillMaxWidth().padding(top = Space.sm).testTag("guild_wall_assign"), enabled = a.enabled)
    }
}

/** What is known of the enemy: what tells against them, a weapon of this forge in their hands, a preparation spoiled. The header names them. */
@Composable
private fun WallHead(w: WallUi) {
    val matchup = listOfNotNull(w.weakTo?.let { Fact(it, "Weak to", FactTone.GOOD) }, w.resists?.let { Fact(it, "Resists", FactTone.WARN) })
    val unknown = w.prep == null && !w.committed
    Column(Modifier.fillMaxWidth().padding(top = Space.sm).testTag("guild_wall")) {
        SectionHeader("The enemy")
        FactLine(matchup, emphasis = true)
        if (unknown) Secondary("Enemy preparation is not yet known.")
        w.leaning?.let { Secondary(it) }
        w.nemesis?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ember, modifier = Modifier.padding(top = Space.xs).testTag("guild_wall_nemesis")) }
        w.spoiled?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EffectKind.BUFF.color, modifier = Modifier.padding(top = Space.xs).testTag("guild_wall_spoiled")) }
    }
}

/**
 * The three places as rows: the place's number, who stands there (face, name, class and level, of the guild or of the
 * town), the weapon in hand as a sprite and one chip of the wall's own readiness check (armed, a worn weapon, unarmed).
 * A tap reads the defender's card as they would stand on the wall; a member kept for the wall is released on the row.
 * An empty place says so, with the way to fill it. When all are filled and a member could still be kept first, one
 * link to the same sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Places(ui: GuildUi, a: GuildActions) {
    val w = ui.wall
    // Large text or a narrow screen: Release goes under the name rather than squeezing it.
    val stacked = LocalDensity.current.fontScale > 1.3f || LocalConfiguration.current.screenWidthDp < 340
    Column(Modifier.fillMaxWidth().padding(top = Space.xs).testTag("guild_wall_places")) {
        (0 until w.places).forEach { i ->
            val d = w.defenders.getOrNull(i)
            if (i > 0) RowRule()
            Row(
                Modifier.fillMaxWidth().then(if (d == null) Modifier else Modifier.heroInspect(d.name) { a.heroTip(d.heroId, true) })
                    .heightIn(min = 64.dp).padding(vertical = Space.sm).testTag("guild_place_$i"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = CreamMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 16.dp))
                if (d == null) {
                    Column(Modifier.weight(1f)) {
                        Text("Empty", style = MaterialTheme.typography.titleSmall, color = Ember)
                        Secondary("Nobody else fit is free to stand.")
                    }
                    SecondaryActionButton("Assign", a.assign, Modifier.testTag("guild_assign_$i"), enabled = a.enabled)
                } else {
                    Face(d.portrait)
                    Column(Modifier.weight(1f)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                            Text(d.name, style = MaterialTheme.typography.titleSmall)
                            if (d.chip.isNotEmpty()) Chip(d.chip, chipColor(d.chipTone))
                        }
                        Secondary(d.sub + when { d.reserved -> " · reserved for the wall"; d.member -> " · of the guild"; else -> " · of the town" })
                        if (d.reserved && stacked) InlineActionButton("Release", { a.send(Command.ReserveDefender(d.heroId, false)) }, Modifier.testTag("guild_reserve_${d.heroId.value}"), enabled = a.enabled)
                    }
                    HeldWeapon(d.weaponArt, d.blade)
                    if (d.reserved && !stacked) InlineActionButton("Release", { a.send(Command.ReserveDefender(d.heroId, false)) }, Modifier.testTag("guild_reserve_${d.heroId.value}"), enabled = a.enabled)
                }
            }
        }
        if (w.defenders.size >= w.places && ui.assign.any { it.action == AssignAction.RESERVE }) {
            InlineActionButton("Reserve a member for the wall  ›", a.assign, Modifier.testTag("guild_keep_open"))
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
            ui.steps.forEachIndexed { i, s -> Step(s, r, a, first = i == 0) }
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

/**
 * One thing the charter opens, as a row: its name, its state in a word and the rule in one line. A tap reads the rest of
 * the rule and what it changes. Retiring and taking a rank stay on the row: they are decisions, not reading.
 */
@Composable
private fun Step(s: StepUi, r: RegionUi, a: GuildActions, first: Boolean) {
    val (word, color) = when (s.state) { StepState.DONE -> "Done" to EffectKind.BUFF.color; StepState.OPEN -> "Open" to Gold; StepState.LOCKED -> "Not yet" to CreamMuted }
    var open by rememberSaveable("charter_step_${s.key}") { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("guild_step_${s.key}")) {
        if (!first) RowRule()
        Column(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clickable(onClickLabel = if (open) "Hide" else "Read more", role = Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
                .padding(vertical = Space.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall, color = if (s.state == StepState.LOCKED) CreamMuted else Cream, modifier = Modifier.weight(1f))
                Chip(word, color)
                Chevron(if (open) ChevronWay.UP else ChevronWay.DOWN, tint = CreamMuted, size = 18.dp)
            }
            Secondary(s.gist)
            if (open) {
                if (s.more.isNotEmpty()) Secondary(s.more)
                Text(s.effect, style = MaterialTheme.typography.bodySmall, color = Cream, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (s.key == "charter" && s.state == StepState.DONE) {
            r.retireBlocked?.let { Secondary(it) }
            SecondaryActionButton("Retire the guild", {
                a.confirm(Confirm(
                    "Retire the guild?", "The era ends here as a success: the forge stands, the charter is secured, and the era's legacy points can be claimed. This cannot be undone.",
                    "Retire the guild", Command.RetireAfterMilestone(GuildContent.CHARTER_SECURED),
                ))
            }, Modifier.fillMaxWidth().padding(bottom = Space.sm).testTag("guild_retire"), enabled = a.enabled && r.retireBlocked == null)
        }
        r.ranks.firstOrNull { "rank_${it.rank}" == s.key && it.canTake }?.let { rank ->
            InlineActionButton("Take this challenge tier", {
                a.confirm(Confirm(
                    "Take the challenge tier ${rank.name}?",
                    "${rank.description} If the guild holds this tier when the era ends, it adds ${rank.legacyPoints} legacy points. A challenge tier cannot be undone.",
                    "Take this tier", Command.SetGuildRank(rank.rank),
                ))
            }, Modifier.align(Alignment.End).testTag("guild_rank_${rank.rank}"), enabled = a.enabled)
        }
    }
}
