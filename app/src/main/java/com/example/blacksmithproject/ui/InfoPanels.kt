package com.example.blacksmithproject.ui

import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontFamily
import com.example.blacksmithproject.R
import com.example.blacksmithproject.ui.theme.PaperInk
import com.example.blacksmithproject.ui.theme.PaperInkMuted
import com.example.blacksmithproject.ui.theme.PaperRule
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.RecordsPage
import com.example.blacksmithproject.Sheet
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Ember
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.gazette.GazetteDigest
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroFate

/**
 * Town: the threat, the champions and every adventurer, as one lazy list with a keyed row per hero. Rows open the
 * hero's sheet, which holds the rest (traits, purse, ambition); a row is two or three lines so sixteen can be scanned.
 * The fallen and the retired wait under a header that opens them.
 */
@Composable
fun TownPanel(s: UiState.Playing, vm: GameViewModel, modifier: Modifier = Modifier) {
    // A guild run: the same place is the guild's (the wall instead of the classic siege card, then the party, the board and the roster).
    s.guild?.let { return GuildPanel(s, it, vm, modifier) }
    val (living, gone) = remember(s.state.heroes) { s.state.heroes.values.sortedByDescending { it.fame }.partition { it.isAlive } }
    var showGone by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().testTag("town_list"), contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.sm, bottom = Space.lg)) {
        item(key = "threat") { Column { TownThreat(s, vm) } }
        item(key = "heroes_head") { SectionTitle("Heroes · ${s.state.aliveHeroes().size} in town") }
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
}

/**
 * The besieger, the other factions and the three champions (V3). The enemy leads: its face, who and when, how hard it
 * presses and one chip for how the siege reads, its card behind the ringed "i". What bites it and the warlord stay on
 * the surface, then the two forecast numbers as a bar, the forge's health, and the town's own numbers as facts. A trait
 * the scouts report is named with what it says, its effects and what helps on its card. The champions are rows: face,
 * name, weapon (a worn one marked) and the defense the forecast counts for them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TownThreat(s: UiState.Playing, vm: GameViewModel) {
    val config = vm.engine.config
    val st = s.state
    // The engine's own choice of besieger (it breaks a pressure tie by ID), so Town agrees with the Shop and the siege.
    val siege = remember(st, s.forecast) { vm.engine.townSiege(st, s.forecast) }
    val threat = s.shop.threat
    val forecast = s.forecast
    val face = siege.factionId?.let { id -> Sprites.faction(id, elite = st.factions[id]?.let { it.pressure >= 60 } == true) }

    // Who and when: the besieger's face beside the day count, its name, how hard it presses and how the siege reads.
    Row(Modifier.fillMaxWidth().padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        face?.let { SpriteSlot { PixelImage(it, 56.dp, description = null) } }
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(threat?.let { if (it.warned && !it.today) "Siege approaching" else it.siege } ?: "No threat", style = MaterialTheme.typography.titleLarge, color = if (threat?.warned == true) Ember else Cream, modifier = Modifier.testTag("town_siege"))
            threat?.takeIf { it.warned && !it.today }?.let { Text(it.siege.removePrefix("Siege ").replaceFirstChar { c -> c.uppercase() } + " · day ${st.town.nextSiegeDay}", style = MaterialTheme.typography.titleSmall) }
            threat?.takeIf { it.today }?.let { Text("After today's trading", style = MaterialTheme.typography.titleSmall) }
            siege.foe?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            Secondary("Pressure: ${siege.pressure.lowercase()}")
        }
        besiegerTip(siege, threat?.outlook, forecast?.odds, face)?.let { tip -> InfoButton(tip.name, { tip }, tag = "town_enemy_info") }
    }
    // One readiness chip, then what works against them: a sign, the words and a colour each, as on a weapon's own row.
    FlowRow(Modifier.padding(top = Space.sm).testTag("town_matchup"), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        MatchMark(when (oddsTone(forecast?.odds)) { FactTone.GOOD -> EffectKind.BUFF; FactTone.WARN -> EffectKind.FLAW; FactTone.PLAIN -> EffectKind.NEUTRAL }, threat?.outlook ?: "Outlook unknown", Modifier.testTag("town_outlook"))
        siege.weakTo?.let { MatchMark(EffectKind.BUFF, "Weak to $it") }
        siege.resists?.let { MatchMark(EffectKind.FLAW, "Resists $it") }
    }
    siege.warlord?.let { Text("⚑ $it", style = MaterialTheme.typography.bodyMedium, color = Ember, modifier = Modifier.padding(top = Space.sm)) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (siege.defense != null && siege.raid != null) Versus(siege.defense, siege.raid)
        // The classic siege is this comparison and nothing else (`Battle.resolveSiegeIfDue`), and a narrow hold still costs forge health.
        if (st.guild == null && siege.defense != null && siege.raid != null) Secondary(
            "The town holds if its defense is at least the raid. There is no roll. A narrow hold still costs up to ${config.forgeDamageBase.roundToInt()} forge health, " +
                "and a lost siege costs ${config.forgeDamageBase.roundToInt()} to ${config.maxForgeDamagePerSiege}. Only a defense far above the raid costs none.",
            Modifier.testTag("town_siege_rule"),
        )
        StatBar("Forge health", siege.forgeHealth, siege.forgeHealthMax)
        // What the forecast adds for this siege: how the defense number is made up (a trait may raise the watch and the militia).
        forecast?.let { o ->
            FactLine(listOf(Fact("${o.championPowers.sum().roundToInt()}", "Defenders"), Fact("${o.militia.roundToInt()}", "Militia"), Fact("${o.armory.roundToInt()}", "Armory")), Modifier.testTag("town_defense_parts"))
        }
        // The town's own numbers and the season, as recorded.
        FactLine((siege.standing + ("World" to siege.world)).map { (label, value) -> Fact(value, label) }, Modifier.testTag("town_standing"))
        if (forecast != null) Secondary(
            if (st.siege?.takeIf { it.siegeDay == st.town.nextSiegeDay }?.factionId != null) "The warning confirms which faction will attack."
            else "The attacking faction may change until the first warning.",
            Modifier.testTag("town_besieger"),
        )
    }
    // The trait the scouts report: its name and what it says stay here, its effects and what helps are on its card.
    forecast?.trait?.let { t ->
        Column(Modifier.fillMaxWidth().padding(top = Space.md).drawBehind { drawLine(BronzeDeep, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }.padding(top = Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                traitIcon(t.id)?.let { PixelIcon(it, 40.dp) }
                Column(Modifier.weight(1f)) {
                    Text("Siege modifier", style = MaterialTheme.typography.labelMedium, color = CreamMuted)
                    Text(t.name, style = MaterialTheme.typography.titleSmall, color = Ember, modifier = Modifier.testTag("town_trait"))
                }
                InfoButton(t.name, { siegeTraitTip(t, config, st.town.nextSiegeDay) }, tag = "town_trait_info")
            }
            Text(t.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.xs))
        }
    }

    SectionTitle("Defenders")
    // The engine's rule (`Battle.selectChampions`): alive, not wounded, the three strongest against the besieger. Shown is
    // the engine's own pick as things stand today; the stored list is only filled by the first End Day.
    val fit = config.heroWoundedThreshold
    Secondary("The 3 strongest heroes with at least $fit% health, ranked against these attackers. The list can change before the siege.")
    val champions = forecast?.champions?.map { it.first } ?: st.town.championIds.mapNotNull { st.heroes[it] }
    val empty = "No other hero has enough health. This place needs at least $fit% health."
    (0 until 3).forEach { i ->
        val h = champions.getOrNull(i)
        if (i > 0) HorizontalDivider(color = BronzeDeep)
        Row(
            Modifier.fillMaxWidth().then(if (h == null) Modifier else Modifier.clickable(onClickLabel = "Open details") { vm.openSheet(Sheet.Hero(h.id)) })
                .heightIn(min = 64.dp).padding(vertical = Space.sm).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The place at the walls, in one column so the faces line up whether or not a place is filled.
            Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = CreamMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 20.dp))
            if (h == null) Secondary(empty, Modifier.weight(1f).testTag("town_champion_empty_$i"))
            else {
                val w = st.equippedWeapon(h.id)
                SpriteSlot { PixelImage(Sprites.portrait(h), 48.dp, description = null) }
                Column(Modifier.weight(1f)) {
                    Text(h.fullName, style = MaterialTheme.typography.titleSmall)
                    FactLine(listOfNotNull(
                        Fact(w?.name ?: "Unarmed", tone = if (w == null) FactTone.WARN else FactTone.PLAIN),
                        w?.let { Labels.condition(it) }?.let { Fact(it, tone = FactTone.WARN) },
                    ))
                }
                // What the forecast counts for this champion against this besieger: their readiness in its own number.
                forecast?.championPowers?.getOrNull(i)?.let { p ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${p.roundToInt()}", style = MaterialTheme.typography.titleMedium, color = Cream)
                        Text("defense", style = MaterialTheme.typography.labelSmall, color = CreamMuted)
                    }
                }
            }
        }
    }
    // Every faction presses on the town (GDD 8); the others are listed so the leader's rise can be read coming.
    if (siege.others.isNotEmpty()) SectionHeader("Other threats")
    FactionRows(siege.others)
}

/** Factions that press on the town beside the besieger: sprite, name, pressure in a word, and the element that bites. */
@Composable
internal fun FactionRows(rows: List<FactionRowUi>) {
    rows.forEachIndexed { i, f ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 6.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SpriteSlot { Sprites.faction(f.id, elite = f.pressure >= 60)?.let { PixelImage(it, 44.dp, description = null) } ?: Spacer(Modifier.size(44.dp)) }
            Column(Modifier.weight(1f)) {
                Text(f.name, style = MaterialTheme.typography.titleSmall)
                Secondary("Pressure: ${f.pressureWord.lowercase()}")
            }
            f.weakTo?.let { MatchMark(EffectKind.BUFF, "Weak to $it") }
        }
    }
}

/** The dark slot a face or a faction's sprite sits in, so every row of Town starts with the same shape. */
@Composable
internal fun SpriteSlot(content: @Composable () -> Unit) {
    Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep)) { content() }
}

/** One mark of a matchup: "+" and green for what bites, "−" and red for what glances off, always with its words. */
@Composable
internal fun MatchMark(kind: EffectKind, text: String, modifier: Modifier = Modifier) {
    Text(
        "${kind.sign} $text", style = MaterialTheme.typography.labelMedium, color = kind.color,
        modifier = modifier.background(kind.color.copy(alpha = 0.14f), MaterialTheme.shapes.extraSmall).border(1.dp, kind.color.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall).padding(horizontal = Space.sm, vertical = Space.xs),
    )
}

/** Town defense against the raid: the two forecast numbers under their labels, and one bar split in their proportion. */
@Composable
private fun Versus(defense: Int, raid: Int) {
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Town defense $defense against a raid of $raid" }) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            Column(Modifier.weight(1f)) {
                Text("Town defense", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                Text("$defense", style = MaterialTheme.typography.titleLarge, color = Gold)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Raid", style = MaterialTheme.typography.bodySmall, color = CreamMuted)
                Text("$raid", style = MaterialTheme.typography.titleLarge, color = Ember)
            }
        }
        Canvas(Modifier.fillMaxWidth().padding(top = Space.xs).height(10.dp)) {
            val gap = 3.dp.toPx()
            val left = (size.width - gap) * defense.coerceAtLeast(0) / maxOf(1, defense.coerceAtLeast(0) + raid.coerceAtLeast(0))
            drawRect(ForgeSlot)
            drawRect(Gold, Offset.Zero, Size(left, size.height))
            drawRect(Ember, Offset(left + gap, 0f), Size(size.width - left - gap, size.height))
        }
    }
}

@Composable
internal fun HeroRow(h: Hero, s: UiState.Playing, vm: GameViewModel) {
    val st = s.state
    val w = st.equippedWeapon(h.id)
    val fateLabel = when (h.fate) {
        HeroFate.ALIVE -> null
        HeroFate.DEAD -> "Fallen" + (h.diedOnDay?.let { " on day $it" } ?: "")
        HeroFate.RETIRED -> "Retired"
        HeroFate.DEPARTED -> "Left the guild"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open details") { vm.openSheet(Sheet.Hero(h.id)) }.testTag("town_hero_${h.id.value}")
            .heightIn(min = 48.dp).padding(vertical = 6.dp).alpha(if (h.isAlive) 1f else 0.6f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep)) {
            PixelImage(Sprites.portrait(h, small = true), 44.dp, description = null)
            Sprites.marker(h.fate)?.let { PixelIcon(it, 16.dp, Modifier.align(Alignment.BottomEnd)) }
        }
        Column(Modifier.weight(1f)) {
            Text(h.fullName + (h.descendantOf?.let { " · descended from $it" } ?: ""), style = MaterialTheme.typography.titleSmall)
            Secondary("${vm.engine.raceClass(h)} ${h.level} · " + (fateLabel ?: Labels.health(h)) + " · " + (w?.let { it.name + (Labels.condition(it)?.let { c -> " ($c)" } ?: "") } ?: "no weapon"))
            townTies(h, st, vm.engine.config)?.let { Secondary(it) }
        }
    }
}

/** Where a hero stands with the town and the shop: guild, mentor and whether they are a regular (a Known Name regular is one from day 1). */
internal fun townTies(h: Hero, st: GameState, config: BalanceConfig): String? = listOfNotNull(
    h.guildId?.let { id -> st.town.guilds.firstOrNull { it.id == id }?.name },
    h.mentorName?.let { "mentor $it" },
    "a shop regular".takeIf { h.isAlive && Market.isRegular(h, config) },
).joinToString(" · ").ifEmpty { null }

/**
 * Under a signature's journal row: the four rungs of its clue ladder, each earned one in the journal's words and each
 * unearned one said to be unknown (a sign and words, never colour alone); once found, "Use this recipe" fills the forge.
 */
@Composable
internal fun SignatureLadder(sig: SignatureUi, onUse: (Command.Forge) -> Unit, modifier: Modifier = Modifier) {
    // Indented to the text of the row above (the 32 dp mark and its 12 dp gap).
    Column(modifier.fillMaxWidth().padding(start = 44.dp, bottom = Space.sm).testTag("ladder_${sig.id}")) {
        sig.rungs.forEach { r ->
            Text(
                if (r.clue != null) "✓ ${r.label}: ${r.clue}" else "○ ${r.label}: not yet known",
                style = MaterialTheme.typography.bodySmall, color = if (r.clue != null) Cream else CreamMuted,
            )
        }
        sig.recipe?.let { recipe -> SecondaryActionButton("Use this recipe", { onUse(recipe) }, Modifier.padding(top = Space.xs).testTag("use_recipe_${sig.id}")) }
    }
}

/** What an archive row says before its headline when the day held a siege or a death: read from the day's record types, never from their text. */
internal fun gazetteMarks(types: Collection<EventType>): List<String> = listOfNotNull(
    "⚔ Siege".takeIf { EventType.SIEGE_WON in types || EventType.SIEGE_LOST in types },
    "† Death".takeIf { EventType.HERO_DIED in types },
)

/** The archive: one edition per day, newest first; the newest is open, older days are a row (day, headline) until tapped. */
@Composable
fun GazettePanel(s: UiState.Playing, vm: GameViewModel) {
    val st = s.state
    val days = st.events.map { it.day }.distinct().sortedDescending()
    var open by remember(days.firstOrNull()) { mutableStateOf(days.firstOrNull()) }
    if (days.isEmpty()) Secondary("No Gazette reports yet.", Modifier.padding(top = Space.md))
    days.forEachIndexed { i, day ->
        // One pipeline for the report and the archive: the same digest, read from the day's own records.
        val digest = remember(st, day) { GazetteDigest.of(st, day, vm.engine.content, vm.engine.config) }
        val expanded = open == day
        val toggle = Modifier.fillMaxWidth().clickable { open = if (expanded) null else day }.semantics { contentDescription = "${Gazette.masthead(day)}, ${if (expanded) "open" else "closed"}. Tap to ${if (expanded) "close" else "open"}." }
        if (expanded) {
            // The open edition is a sheet of the paper itself, as the day's own report is: fixed ink on parchment.
            val head = remember(day) { gazetteHead(day) }
            Column(Modifier.padding(top = Space.sm).fillMaxWidth().paperBackground().pixelFrame(R.drawable.panel_gazette).padding(horizontal = Space.xs, vertical = frameInset())) {
                CompositionLocalProvider(LocalContentColor provides PaperInk) {
                    // Masthead and dateline strip are one target: the paper's name, a double rule, then the day and "Hide" on one line.
                    Column(toggle.padding(horizontal = Space.md).padding(top = 12.dp)) {
                        Text(head.paper, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
                        HorizontalDivider(Modifier.padding(top = Space.sm), thickness = 2.dp, color = PaperInk)
                        HorizontalDivider(Modifier.padding(top = 2.dp), color = PaperRule)
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(head.dateline, style = MaterialTheme.typography.labelLarge, color = PaperInkMuted, modifier = Modifier.weight(1f))
                            Text("Hide  ▴", style = MaterialTheme.typography.labelLarge)
                        }
                        HorizontalDivider(color = PaperRule)
                    }
                    var detailsOpen by remember(day) { mutableStateOf(false) }
                    Column(Modifier.padding(horizontal = Space.md).padding(top = 12.dp, bottom = Space.md)) {
                        DigestBody(digest, onOpenNotebook = { vm.selectRecords(RecordsPage.JOURNAL) })
                        PaperDisclosure("All details", detailsOpen, { detailsOpen = !detailsOpen }, "archive_details_$day") { EditionBody(digest.details) }
                    }
                }
            }
        } else {
            val marks = remember(st.events, day) { gazetteMarks(st.eventsForDay(day).map { it.type }) }
            val first = digest.headline
            // V3: an archive is a list, not a stack of plates: one row a day, hairlines between them.
            if (i > 0 && open != days[i - 1]) HorizontalDivider(color = BronzeDeep)
            Row(
                Modifier.then(toggle).heightIn(min = 56.dp).padding(vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The day as a date block at the head of the row, so a column of editions reads as an archive.
                Column(Modifier.widthIn(min = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DAY", style = MaterialTheme.typography.labelSmall, color = CreamMuted)
                    Text("$day", style = MaterialTheme.typography.titleLarge, color = Cream)
                }
                Column(Modifier.weight(1f)) {
                    // A sign and a word, so the colour is never the only cue.
                    if (marks.isNotEmpty()) Text(marks.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Ember)
                    Text(first, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Serif)
                }
                Chevron(ChevronWay.DOWN, tint = CreamMuted)
            }
        }
    }
}

/** Lazy rows of the legacy page: the upgrade tracks, blessings, the Legend Board and lineages, each keyed. */
fun LazyListScope.legacyItems(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    val legacy = s.state.legacy
    item(key = "legacy_head") {
        Column {
            // The account at a glance: the era on one side, the banked points as the one large number on the other.
            Row(Modifier.fillMaxWidth().padding(top = Space.md).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                Column(Modifier.weight(1f)) {
                    Text("Era ${s.state.era}", style = MaterialTheme.typography.titleLarge, color = Cream, modifier = Modifier.semantics { heading() })
                    Secondary("Claim legacy points when an era ends. Keep them for future eras.")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${legacy.points}", style = MaterialTheme.typography.headlineMedium, color = Gold)
                    Text("legacy points banked", style = MaterialTheme.typography.labelSmall, color = CreamMuted, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 96.dp))
                }
            }
            if (s.state.pendingBlessingOffer.isNotEmpty()) {
                PrimaryActionButton("Choose a blessing", vm::reopenBlessingOffer, Modifier.fillMaxWidth().padding(top = Space.sm))
            }
            SectionHeader("Legacy perks")
        }
    }
    itemsIndexed(content.upgrades, key = { _, u -> "upgrade_${u.id.value}" }) { i, u ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // The name and its level share a line, so every track starts at the same edge whatever its number of levels. What
        // the next level gives and costs is on the upgrade's card.
        val level = legacy.upgradeLevel(u.id)
        Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(u.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    LevelDots(level, u.maxLevel)
                }
                Secondary(u.description)
            }
            InfoButton(u.name, {
                upgradeTip(u, level, com.tinyblacksmith.core.legacy.Legacy.preview(u.id, level + 1, content, vm.engine.config)?.text, u.costPerLevel.getOrNull(level), tag = "legacy_upgrade_tip")
            }, tag = "legacy_upgrade_${u.id.value}_info")
        }
    }
    if (s.state.blessings.isNotEmpty()) {
        item(key = "blessings_head") { SectionHeader("Active blessings") }
        itemsIndexed(s.state.blessings, key = { i, b -> "blessing_${i}_${b.id.value}" }) { _, b ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {}) {
                Sprites.blessing(b.id)?.let { PixelImage(it, 32.dp, description = null) }
                Column {
                    Text(content.blessing(b.id).name, style = MaterialTheme.typography.titleSmall)
                    Secondary("Until day ${b.expiresDay}")
                }
            }
        }
    }
    item(key = "legends_head") {
        Column {
            SectionHeader("Hall of Legends")
            if (legacy.legendBoard.isEmpty()) Secondary("No famous weapons recorded yet.", Modifier.padding(vertical = Space.xs))
        }
    }
    itemsIndexed(legacy.legendBoard, key = { i, _ -> "legend_$i" }) { i, entry ->
        val legend = remember(entry, s.state.era) { legendUi(entry, content, s.state.era) }
        if (i > 0) HorizontalDivider(color = BronzeDeep)
        // V3: the weapon and what it was on the row, who carried it and its story on its card.
        Row(Modifier.fillMaxWidth().padding(vertical = Space.sm).testTag("legend_$i"), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(legend.head, style = MaterialTheme.typography.titleSmall, color = Cream)
                legend.lines.firstOrNull()?.let { line ->
                    // An entry older than the record of its make: said as a note in its own voice, not as a property.
                    if (legend.lostToTime) Text("◆ $line", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = CreamMuted, modifier = Modifier.testTag("legend_lost_$i"))
                    else Secondary(line)
                }
            }
            if (legend.lines.size > 1) InfoButton(legend.head, { legendTip(legend, tag = "legend_tip") }, tag = "legend_${i}_info")
        }
    }
    item(key = "lineages_head") {
        Column {
            SectionHeader("Lineages")
            if (legacy.lineages.isEmpty()) Secondary("No family lines recorded yet.", Modifier.padding(vertical = Space.xs))
        }
    }
    itemsIndexed(legacy.lineages, key = { i, _ -> "lineage_$i" }) { i, it ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.fillMaxWidth().padding(vertical = Space.sm).semantics(mergeDescendants = true) {}) {
            Text(it.heroName, style = MaterialTheme.typography.titleSmall)
            Secondary("Era ${it.era} · ${it.deed}")
        }
    }
}

/** Upgrade level as filled/empty dots, with a spoken "level x of y" so the glyphs are never the only cue. */
@Composable
fun LevelDots(level: Int, max: Int) {
    Text(
        "●".repeat(level) + "○".repeat((max - level).coerceAtLeast(0)),
        style = MaterialTheme.typography.bodyMedium,
        color = Cream,
        modifier = Modifier.semantics { contentDescription = "level $level of $max" },
    )
}

/**
 * A guild run's townsfolk on Records: the living who are not of the guild, by fame, then the fallen and retired behind
 * one heading. The guild's own members are on the Guild's Company part; the wall still takes townsfolk as defenders.
 */
internal fun LazyListScope.townsfolkItems(s: UiState.Playing, vm: GameViewModel, showGone: Boolean, onToggleGone: () -> Unit) {
    val living = s.state.residents().sortedByDescending { it.fame }
    val gone = s.state.heroes.values.filter { !it.isAlive }.sortedByDescending { it.fame }
    item(key = "townsfolk_head") { SectionTitle("Townsfolk · ${living.size} in town", Modifier.testTag("records_townsfolk")) }
    itemsIndexed(living, key = { _, h -> "hero_${h.id.value}" }) { i, h ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        HeroRow(h, s, vm)
    }
    if (gone.isNotEmpty()) {
        item(key = "gone_head") {
            Row(
                Modifier.fillMaxWidth().padding(top = Space.md).clickable(onClickLabel = if (showGone) "Hide" else "Show", role = Role.Button, onClick = onToggleGone).heightIn(min = 48.dp).testTag("town_fallen"),
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
