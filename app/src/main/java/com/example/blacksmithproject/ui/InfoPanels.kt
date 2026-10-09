package com.example.blacksmithproject.ui

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroFate
import kotlin.math.roundToInt

@Composable
fun TownPanel(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    val st = s.state
    val faction = st.factions.values.maxByOrNull { it.pressure }
    val daysLeft = st.town.nextSiegeDay - st.day

    // Header: the faction with the most pressure (the one the engine sends at the siege), numbers second.
    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(top = Space.sm)) {
        Row(Modifier.padding(Space.md).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            faction?.let { f -> Sprites.faction(f.id, elite = f.pressure >= 60)?.let { PixelImage(it, 56.dp, description = null) } }
            Column(Modifier.weight(1f)) {
                Text(faction?.let { content.faction(it.id).name } ?: "No threat", style = MaterialTheme.typography.titleMedium)
                Text(
                    faction?.let { Battle.describePressure(it.pressure).replaceFirstChar { c -> c.uppercase() } + " · " } .orEmpty() +
                        when { daysLeft <= 0 -> "siege today"; daysLeft == 1 -> "siege tomorrow"; else -> "siege on day ${st.town.nextSiegeDay}, in $daysLeft days" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                faction?.let { weakness(content.faction(it.id).weakTo) }?.let { Secondary(it, Modifier.padding(top = Space.xs)) }
                vm.engine.siegeForecast(st)?.let { o ->
                    val outlook = when (o.odds) {
                        Battle.SiegeOdds.STRONG -> "the town should hold"
                        Battle.SiegeOdds.EVEN -> "evenly matched"
                        Battle.SiegeOdds.OUTMATCHED -> "the walls are outmatched"
                        Battle.SiegeOdds.DIRE -> "grave danger"
                    }
                    Secondary("Outlook: $outlook · defense ${o.townDefense.roundToInt()} vs raid ${o.raidPower.roundToInt()}", Modifier.padding(top = Space.xs))
                    if (o.warlord) Secondary("${o.faction.warlordName} leads them")
                }
                if (st.town.armory > 0) Secondary("Town watch armory: ${st.town.armory}/${vm.engine.config.armoryMax}")
                Secondary("Forge ${st.town.integrity} · militia ${st.town.militia} · sieges held ${st.town.siegesSurvived}", Modifier.padding(top = Space.xs))
                Secondary("World: ${st.world.name}")
            }
        }
    }
    // Every faction presses on the town (GDD 8); the others are listed so the leader's rise can be read coming.
    st.factions.values.filter { it.id != faction?.id }.sortedByDescending { it.pressure }.forEach { f ->
        val def = content.faction(f.id)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.xs).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Sprites.faction(f.id, elite = f.pressure >= 60)?.let { PixelImage(it, 40.dp, description = null) }
            Column(Modifier.weight(1f)) {
                Text(def.name, style = MaterialTheme.typography.titleSmall)
                Secondary(listOfNotNull(Battle.describePressure(f.pressure).replaceFirstChar { c -> c.uppercase() }, weakness(def.weakTo)).joinToString(" · "))
            }
        }
    }

    SectionTitle("Champions")
    Secondary("The three strongest heroes fit to stand at the walls.")
    val champions = st.town.championIds.mapNotNull { st.heroes[it] }
    (0 until 3).forEach { i ->
        val h = champions.getOrNull(i)
        Surface(
            tonalElevation = if (h != null) 1.dp else 0.dp,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
        ) {
            Row(Modifier.padding(12.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (h == null) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Secondary("No hero stands here yet.", Modifier.weight(1f))
                } else {
                    val w = st.equippedWeapon(h.id)
                    PixelImage(Sprites.portrait(h), 56.dp, description = null)
                    Column(Modifier.weight(1f)) {
                        Text("${i + 1}. ${h.fullName}", style = MaterialTheme.typography.titleSmall)
                        Secondary("${content.heroClass(h.classId).name} level ${h.level} · ${Labels.health(h)}")
                        Secondary(w?.let { "Wields ${it.name}" + (Labels.condition(it)?.let { c -> " ($c)" } ?: "") } ?: "Unarmed")
                    }
                    w?.let { WeaponSprite(it, size = 44.dp) }
                }
            }
        }
    }

    SectionTitle("Adventurers (${st.aliveHeroes().size} alive)")
    st.heroes.values.sortedWith(compareBy<Hero> { !it.isAlive }.thenByDescending { it.fame }).forEachIndexed { i, h ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        HeroRow(h, s, vm)
    }
}

/** The faction's weakness in words; decorative, the engine applies the matchup itself. */
private fun weakness(e: Element?): String? = e?.let { "Weak to ${it.name.lowercase()}" }

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
        Modifier.fillMaxWidth().padding(vertical = 10.dp).alpha(if (h.isAlive) 1f else 0.6f).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            PixelImage(Sprites.portrait(h), 44.dp, description = null)
            Sprites.marker(h.fate)?.let { PixelImage(it, 16.dp, description = null, modifier = Modifier.align(Alignment.BottomEnd)) }
        }
        Column(Modifier.weight(1f)) {
            Text(h.fullName + (h.descendantOf?.let { " · of $it's line" } ?: ""), style = MaterialTheme.typography.titleSmall)
            Secondary("${content.heroClass(h.classId).name} ${h.level} · " + (fateLabel ?: Labels.health(h)) + " · " + (w?.let { it.name + (Labels.condition(it)?.let { c -> " ($c)" } ?: "") } ?: "unarmed"))
            Secondary(
                if (h.isAlive) "${Heroes.describeTraits(h, content)} · ${h.gold} gold · fame ${h.fame}"
                else "${Heroes.describeTraits(h, content)} · fame ${h.fame} · ${h.kills} kills",
            )
            Heroes.describeAmbition(h, w, vm.engine.config)?.let { Secondary(it) }
        }
    }
}

@Composable
fun JournalPanel(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    val journal = s.state.legacy.journal
    SectionTitle("Experiment Journal", Modifier.padding(top = Space.sm))
    Secondary("Knowledge survives the forge's fall. Repeat a pairing to understand it.")
    val entries = journal.interactions.entries.sortedBy { it.key }
    if (entries.isEmpty()) Text("No experiments recorded yet. Forge something.", modifier = Modifier.padding(top = Space.md))
    Column(Modifier.padding(top = Space.sm)) {
        entries.forEach { (key, _) -> AffinityHint(journal, content, key) }
    }
}

/** The archive: one edition per day, newest first; the newest is open, older days show their lede until tapped. */
@Composable
fun GazettePanel(s: UiState.Playing) {
    val st = s.state
    val days = st.events.map { it.day }.distinct().sortedDescending()
    val heroNames = remember(st.heroes) { st.heroes.values.associate { it.id.value to it.fullName } }
    var open by remember(days.firstOrNull()) { mutableStateOf(days.firstOrNull()) }
    if (days.isEmpty()) Text("The presses are quiet.", modifier = Modifier.padding(top = Space.md))
    days.forEach { day ->
        val edition = remember(st.events, day) {
            val res = st.lastResolution?.takeIf { it.day == day }
            Gazette.edition(Gazette.dayRecords(st, day), heroNames, res?.visits ?: emptyList(), res?.ledger, res?.field ?: emptyList())
        }
        val expanded = open == day
        Row(
            Modifier.fillMaxWidth().clickable { open = if (expanded) null else day }.semantics { contentDescription = "${Gazette.masthead(day)}, ${if (expanded) "open" else "closed"}. Tap to ${if (expanded) "close" else "open"}." },
            verticalAlignment = Alignment.Bottom,
        ) {
            SectionTitle(Gazette.masthead(day), Modifier.weight(1f))
            Secondary(if (expanded) "Close" else "Open", Modifier.padding(bottom = Space.sm))
        }
        if (expanded) EditionBody(edition)
        else {
            val first = edition.lede.firstOrNull() ?: edition.sections.firstOrNull()?.lines?.firstOrNull() ?: "A quiet day in Emberfall."
            Text(first, fontFamily = FontFamily.Serif, style = MaterialTheme.typography.bodyMedium)
            if (edition.tally.isNotEmpty()) Secondary(edition.tally.joinToString(" · "), Modifier.padding(top = Space.xs))
        }
    }
}

@Composable
fun LegacyPanel(s: UiState.Playing, vm: GameViewModel, reducedMotion: Boolean) {
    val content = vm.engine.content
    val legacy = s.state.legacy
    SectionTitle("Era ${s.state.era}", Modifier.padding(top = Space.sm))
    if (s.state.pendingBlessingOffer.isNotEmpty()) {
        Button(onClick = vm::reopenBlessingOffer, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(bottom = Space.sm)) { Text("Choose a blessing") }
    }
    Text("${legacy.points} legacy points banked", style = MaterialTheme.typography.titleMedium)
    Secondary("Rewards are claimed when the forge falls; they survive every era.")

    SectionTitle("Permanent upgrades")
    content.upgrades.forEach { u ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
            LevelDots(legacy.upgradeLevel(u.id), u.maxLevel)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(u.name, style = MaterialTheme.typography.titleSmall)
                Secondary(u.description)
            }
        }
    }
    if (s.state.blessings.isNotEmpty()) {
        SectionTitle("Active blessings")
        s.state.blessings.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                Sprites.blessing(b.id)?.let { PixelImage(it, 32.dp, description = null) }
                Column {
                    Text(content.blessing(b.id).name, style = MaterialTheme.typography.titleSmall)
                    Secondary("Until day ${b.expiresDay}")
                }
            }
        }
    }
    SectionTitle("Legend Board")
    if (legacy.legendBoard.isEmpty()) Secondary("No blade has earned a legend yet.")
    legacy.legendBoard.forEach {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(it.title, style = MaterialTheme.typography.titleSmall)
            Secondary("Era ${it.era} · ${it.kills} kills · carried by ${it.owners.joinToString().ifEmpty { "no one" }}")
        }
    }
    SectionTitle("Lineages")
    if (legacy.lineages.isEmpty()) Secondary("No lineage has been founded yet.")
    legacy.lineages.forEach {
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(it.heroName, style = MaterialTheme.typography.titleSmall)
            Secondary("Era ${it.era} · ${it.deed}")
        }
    }
    SectionTitle("Settings")
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = reducedMotion, role = Role.Switch, onValueChange = { vm.setReducedMotion(it) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Reduced motion", style = MaterialTheme.typography.titleSmall)
            Secondary("Stops the ember animation, the reveal fade and the stepped battle replay.")
        }
        Switch(checked = reducedMotion, onCheckedChange = null)
    }
}

/** Upgrade level as filled/empty dots, with a spoken "level x of y" so the glyphs are never the only cue. */
@Composable
fun LevelDots(level: Int, max: Int) {
    Text(
        "●".repeat(level) + "○".repeat((max - level).coerceAtLeast(0)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { contentDescription = "level $level of $max" },
    )
}
