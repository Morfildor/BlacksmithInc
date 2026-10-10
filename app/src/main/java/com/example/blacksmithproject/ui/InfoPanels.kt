package com.example.blacksmithproject.ui

import kotlin.math.roundToInt
import com.tinyblacksmith.core.content.Depth
import com.example.blacksmithproject.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontFamily
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
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroFate

/**
 * Town: the threat, the champions and every adventurer, as one lazy list with a keyed row per hero. Rows open the
 * hero's sheet, which holds the rest (traits, purse, ambition); a row is two or three lines so sixteen can be scanned.
 * The fallen and the retired wait under a header that opens them.
 */
@Composable
fun TownPanel(s: UiState.Playing, vm: GameViewModel, modifier: Modifier = Modifier) {
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

/** The besieger, the other factions and the three champions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TownThreat(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    val st = s.state
    // The engine's own choice of besieger (it breaks a pressure tie by ID), so Town agrees with the Shop and the siege.
    val siege = remember(st, s.forecast) { vm.engine.townSiege(st, s.forecast) }
    val threat = s.shop.threat
    val forecast = s.forecast

    FramedPanel(modifier = Modifier.fillMaxWidth().padding(top = Space.sm)) {
        // Who and when: the besieger's face beside the day count, its name and how hard it presses.
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            siege.factionId?.let { id -> Sprites.faction(id, elite = st.factions[id]?.let { it.pressure >= 60 } == true)?.let { SpriteSlot { PixelImage(it, 56.dp, description = null) } } }
            Column(Modifier.weight(1f)) {
                Text(threat?.let { if (it.warned && !it.today) "Siege approaching" else it.siege } ?: "No threat", style = MaterialTheme.typography.titleLarge, color = if (threat?.warned == true) Ember else Gold, modifier = Modifier.testTag("town_siege"))
                threat?.takeIf { it.warned && !it.today }?.let { Text(it.siege.removePrefix("Siege ").replaceFirstChar { c -> c.uppercase() } + " · day ${st.town.nextSiegeDay}", style = MaterialTheme.typography.titleSmall) }
                threat?.takeIf { it.today }?.let { Text("After today's trading", style = MaterialTheme.typography.titleSmall) }
                siege.foe?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                Secondary(siege.pressure)
            }
        }
        // What works against them: a sign, the words and a colour each, as on a blade's own row.
        if (siege.weakTo != null || siege.resists != null) FlowRow(
            Modifier.padding(top = Space.sm).testTag("town_matchup"), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            siege.weakTo?.let { MatchMark(EffectKind.BUFF, "Weak to $it") }
            siege.resists?.let { MatchMark(EffectKind.FLAW, "Resists $it") }
        }
        siege.warlord?.let { Text("⚑ $it", style = MaterialTheme.typography.bodySmall, color = Ember, modifier = Modifier.padding(top = Space.sm)) }
        HorizontalDivider(Modifier.padding(top = 12.dp, bottom = Space.sm), color = MaterialTheme.colorScheme.outlineVariant)
        if (siege.defense != null && siege.raid != null) Versus(siege.defense, siege.raid)
        StatRow("Outlook", threat?.outlook ?: "Unknown", Modifier.padding(top = Space.xs))
        StatBar("Forge health", siege.forgeHealth, siege.forgeHealthMax)
        HorizontalDivider(Modifier.padding(top = 12.dp, bottom = Space.sm), color = MaterialTheme.colorScheme.outlineVariant)
        // The town's own numbers and the season, each a value over its label; they wrap as cells, never mid-phrase.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            (siege.standing + ("World" to siege.world)).forEach { (label, value) ->
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(value, style = MaterialTheme.typography.titleSmall)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = CreamMuted)
                }
            }
        }
    }
    // What the forecast adds for this siege: how the defense number is made up (a trait may raise the watch and the
    // militia), whether the besieger can still change, and the trait the scouts report, if any.
    s.forecast?.let { o ->
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.sm).padding(top = Space.sm)) {
            Secondary("Defense is champions ${o.championPowers.sum().roundToInt()} · militia ${o.militia.roundToInt()} · watch ${o.armory.roundToInt()}", Modifier.testTag("town_defense_parts"))
            Secondary(
                if (st.siege?.takeIf { it.siegeDay == st.town.nextSiegeDay }?.factionId != null) "The besieger is fixed: this is who comes."
                else "As things stand: the besieger can still change before the first warning.",
                Modifier.testTag("town_besieger"),
            )
            o.trait?.let { t ->
                Row(Modifier.padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    when (t.id) { Depth.LONG_ASSAULT -> R.drawable.icon_trait_long_assault; Depth.MANY_BREACHES -> R.drawable.icon_trait_many_breaches; else -> null }
                        ?.let { PixelImage(it, wholePixelDp(80, 40.dp), description = null) }
                    Text(t.name, style = MaterialTheme.typography.titleSmall, color = Gold, modifier = Modifier.testTag("town_trait"))
                }
                Text(t.description, style = MaterialTheme.typography.bodyMedium)
                Secondary(t.counsel)
            }
        }
    }
    // Every faction presses on the town (GDD 8); the others are listed so the leader's rise can be read coming.
    if (siege.others.isNotEmpty()) SectionHeader("Also pressing on the town")
    siege.others.forEachIndexed { i, f ->
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

    SectionTitle("Champions")
    // The engine's rule (`Battle.selectChampions`): alive, not wounded, the three strongest against the besieger. Shown is
    // the engine's own pick as things stand today; the stored list is only filled by the first End Day.
    val fit = vm.engine.config.heroWoundedThreshold
    Secondary("The three strongest living heroes who are not wounded (health $fit or more), as things stand today. They stand at the walls when the siege comes.")
    val champions = forecast?.champions?.map { it.first } ?: st.town.championIds.mapNotNull { st.heroes[it] }
    val empty = "Empty: no other living hero has health $fit or more."
    (0 until 3).forEach { i ->
        val h = champions.getOrNull(i)
        Row(
            Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().then(if (h == null) Modifier else Modifier.clickable(onClickLabel = "Open details") { vm.openSheet(Sheet.Hero(h.id)) })
                .heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = Space.sm).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The place at the walls, in one column so the faces line up whether or not a place is filled.
            Text("${i + 1}", style = MaterialTheme.typography.titleLarge, color = if (h == null) CreamMuted else Gold, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 20.dp))
            if (h == null) Secondary(empty, Modifier.weight(1f).testTag("town_champion_empty_$i"))
            else {
                val w = st.equippedWeapon(h.id)
                SpriteSlot { PixelImage(Sprites.portrait(h), 48.dp, description = null) }
                Column(Modifier.weight(1f)) {
                    Text(h.fullName, style = MaterialTheme.typography.titleSmall)
                    Text(w?.let { "Wields ${it.name}" + (Labels.condition(it)?.let { c -> " ($c)" } ?: "") } ?: "Unarmed", style = MaterialTheme.typography.bodySmall, color = Cream)
                    Secondary("${content.heroClass(h.classId).name} level ${h.level} · ${Labels.health(h)}")
                }
                w?.let { WeaponSprite(it, size = 40.dp) }
            }
        }
    }
}

/** The dark slot a face or a faction's sprite sits in, so every row of Town starts with the same shape. */
@Composable
private fun SpriteSlot(content: @Composable () -> Unit) {
    Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep)) { content() }
}

/** One mark of a matchup: "+" and green for what bites, "−" and red for what glances off, always with its words. */
@Composable
private fun MatchMark(kind: EffectKind, text: String) {
    Text(
        "${kind.sign} $text", style = MaterialTheme.typography.labelMedium, color = kind.color,
        modifier = Modifier.background(kind.color.copy(alpha = 0.14f), MaterialTheme.shapes.extraSmall).border(1.dp, kind.color.copy(alpha = 0.6f), MaterialTheme.shapes.extraSmall).padding(horizontal = Space.sm, vertical = Space.xs),
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
private fun HeroRow(h: Hero, s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    val st = s.state
    val w = st.equippedWeapon(h.id)
    val fateLabel = when (h.fate) {
        HeroFate.ALIVE -> null
        HeroFate.DEAD -> "Fallen" + (h.diedOnDay?.let { " on day $it" } ?: "")
        HeroFate.RETIRED -> "Retired"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Open details") { vm.openSheet(Sheet.Hero(h.id)) }.testTag("town_hero_${h.id.value}")
            .heightIn(min = 48.dp).padding(vertical = 6.dp).alpha(if (h.isAlive) 1f else 0.6f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep)) {
            PixelImage(Sprites.portrait(h, small = true), 44.dp, description = null)
            Sprites.marker(h.fate)?.let { PixelImage(it, 16.dp, description = null, modifier = Modifier.align(Alignment.BottomEnd)) }
        }
        Column(Modifier.weight(1f)) {
            Text(h.fullName + (h.descendantOf?.let { " · of $it's line" } ?: ""), style = MaterialTheme.typography.titleSmall)
            Secondary("${content.heroClass(h.classId).name} ${h.level} · " + (fateLabel ?: Labels.health(h)) + " · " + (w?.let { it.name + (Labels.condition(it)?.let { c -> " ($c)" } ?: "") } ?: "unarmed"))
            townTies(h, st, vm.engine.config)?.let { Secondary(it) }
        }
    }
}

/** Where a hero stands with the town and the shop: guild, mentor and whether they are a regular (a Known Name regular is one from day 1). */
internal fun townTies(h: Hero, st: GameState, config: BalanceConfig): String? = listOfNotNull(
    h.guildId?.let { id -> st.town.guilds.firstOrNull { it.id == id }?.name },
    h.mentorName?.let { "mentor $it" },
    "a regular of your shop".takeIf { h.isAlive && Market.isRegular(h, config) },
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
fun GazettePanel(s: UiState.Playing) {
    val st = s.state
    val days = st.events.map { it.day }.distinct().sortedDescending()
    val heroNames = remember(st.heroes) { st.heroes.values.associate { it.id.value to it.fullName } }
    var open by remember(days.firstOrNull()) { mutableStateOf(days.firstOrNull()) }
    if (days.isEmpty()) Secondary("The presses are quiet.", Modifier.padding(top = Space.md))
    days.forEach { day ->
        val edition = remember(st.events, day) {
            val res = st.lastResolution?.takeIf { it.day == day }
            Gazette.edition(Gazette.dayRecords(st, day), heroNames, res?.visits ?: emptyList(), res?.ledger, res?.field ?: emptyList())
        }
        val expanded = open == day
        val toggle = Modifier.fillMaxWidth().clickable { open = if (expanded) null else day }.semantics { contentDescription = "${Gazette.masthead(day)}, ${if (expanded) "open" else "closed"}. Tap to ${if (expanded) "close" else "open"}." }
        if (expanded) {
            // The open edition is a sheet of the paper itself, as the day's own report is: fixed ink on parchment.
            val head = remember(day) { gazetteHead(day) }
            Column(Modifier.padding(top = Space.sm).fillMaxWidth().clip(MaterialTheme.shapes.small).paperBackground()) {
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
                    EditionBody(edition, Modifier.padding(horizontal = Space.md).padding(top = 12.dp, bottom = Space.md))
                }
            }
        } else {
            val marks = remember(st.events, day) { gazetteMarks(st.eventsForDay(day).map { it.type }) }
            val first = edition.lede.firstOrNull() ?: edition.sections.firstOrNull()?.lines?.firstOrNull() ?: "A quiet day in Emberfall."
            Row(
                Modifier.padding(top = Space.sm).forgeRow().then(toggle).heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The day as a date block at the head of the row, so a column of editions reads as an archive.
                Column(Modifier.widthIn(min = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("DAY", style = MaterialTheme.typography.labelSmall, color = CreamMuted)
                    Text("$day", style = MaterialTheme.typography.titleLarge, color = Gold)
                }
                Column(Modifier.weight(1f)) {
                    // A sign and a word, so the colour is never the only cue.
                    if (marks.isNotEmpty()) Text(marks.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Ember)
                    Text(first, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Serif)
                }
                Text("▾", style = MaterialTheme.typography.titleMedium, color = Gold)
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
            FramedPanel(modifier = Modifier.fillMaxWidth().padding(top = Space.sm)) {
                Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    Column(Modifier.weight(1f)) {
                        Text("Era ${s.state.era}", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { heading() })
                        Secondary("Rewards are claimed when the forge falls; they survive every era.")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${legacy.points}", style = MaterialTheme.typography.headlineMedium, color = Gold)
                        Text("legacy points banked", style = MaterialTheme.typography.labelSmall, color = CreamMuted, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 96.dp))
                    }
                }
            }
            if (s.state.pendingBlessingOffer.isNotEmpty()) {
                PrimaryActionButton("Choose a blessing", vm::reopenBlessingOffer, Modifier.fillMaxWidth().padding(top = Space.sm))
            }
            SectionHeader("Permanent upgrades")
        }
    }
    itemsIndexed(content.upgrades, key = { _, u -> "upgrade_${u.id.value}" }) { i, u ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // The name and its level share a line, so every track starts at the same edge whatever its number of levels.
        Column(Modifier.fillMaxWidth().padding(vertical = Space.sm).semantics(mergeDescendants = true) {}) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(u.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                LevelDots(legacy.upgradeLevel(u.id), u.maxLevel)
            }
            Secondary(u.description)
            Text(com.tinyblacksmith.core.legacy.Legacy.preview(u.id, legacy.upgradeLevel(u.id) + 1, content, vm.engine.config)?.text ?: "Fully upgraded: nothing more to buy.", style = MaterialTheme.typography.bodySmall, color = Cream, modifier = Modifier.padding(top = 2.dp))
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
            SectionHeader("Legend Board")
            if (legacy.legendBoard.isEmpty()) Secondary("No blade has earned a legend yet.", Modifier.padding(vertical = Space.xs))
        }
    }
    itemsIndexed(legacy.legendBoard, key = { i, _ -> "legend_$i" }) { i, entry ->
        val legend = remember(entry, s.state.era) { legendUi(entry, content, s.state.era) }
        Column(Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().padding(horizontal = 12.dp, vertical = Space.sm).testTag("legend_$i")) {
            Text(legend.head, style = MaterialTheme.typography.titleSmall, color = Gold)
            legend.lines.forEachIndexed { j, line ->
                // An entry older than the record of its make: said as a note in its own voice, not as a property.
                if (j == 0 && legend.lostToTime) Text("◆ $line", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = CreamMuted, modifier = Modifier.testTag("legend_lost_$i"))
                else Secondary(line)
            }
        }
    }
    item(key = "lineages_head") {
        Column {
            SectionHeader("Lineages")
            if (legacy.lineages.isEmpty()) Secondary("No lineage has been founded yet.", Modifier.padding(vertical = Space.xs))
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
        color = Gold,
        modifier = Modifier.semantics { contentDescription = "level $level of $max" },
    )
}
