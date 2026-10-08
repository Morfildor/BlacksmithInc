package com.example.blacksmithproject.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.UiState
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.crafting.Journal
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroFate
import com.tinyblacksmith.core.model.KnowledgeState

@Composable
fun TownPanel(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    val st = s.state
    val faction = st.factions.values.maxByOrNull { it.pressure }
    SectionTitle("Threat")
    Row(verticalAlignment = Alignment.CenterVertically) {
        faction?.let { f -> Sprites.faction(f.id, elite = f.pressure >= 60)?.let { PixelImage(it, 48.dp, description = content.faction(f.id).name); Spacer(Modifier.width(12.dp)) } }
        Column {
            faction?.let { f -> Text("${content.faction(f.id).name}: ${Battle.describePressure(f.pressure)}. Next invasion on day ${st.town.nextSiegeDay}.") }
            Text("Forge integrity ${st.town.integrity} · militia ${st.town.militia} · sieges held ${st.town.siegesSurvived}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("World: ${st.world.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    SectionTitle("Champions")
    Text("The three strongest heroes fit to stand at the walls.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val champions = st.town.championIds.mapNotNull { st.heroes[it] }
    (0 until 3).forEach { i ->
        val h = champions.getOrNull(i)
        Card(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = if (h != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (h == null) {
                    Text("${i + 1}.", style = MaterialTheme.typography.titleMedium)
                    Text("No hero stands here yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                } else {
                    val w = st.equippedWeapon(h.id)
                    PixelImage(Sprites.portrait(h), 56.dp, description = "${h.fullName}, ${content.heroClass(h.classId).name}")
                    Column(Modifier.weight(1f)) {
                        Text("${i + 1}. ${h.fullName}", style = MaterialTheme.typography.titleSmall)
                        Text("${content.heroClass(h.classId).name} level ${h.level} · ${Labels.health(h)}", style = MaterialTheme.typography.bodySmall)
                        Text(w?.let { "Wields ${it.name}" } ?: "Unarmed", style = MaterialTheme.typography.bodySmall)
                    }
                    w?.let { WeaponSprite(it, size = 44.dp) }
                }
            }
        }
    }

    SectionTitle("Adventurers (${st.aliveHeroes().size} alive)")
    st.heroes.values.sortedWith(compareBy<Hero> { !it.isAlive }.thenByDescending { it.fame }).forEach { h -> HeroRow(h, s, vm) }
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
        Modifier.fillMaxWidth().padding(vertical = 5.dp).alpha(if (h.isAlive) 1f else 0.55f).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box {
            PixelImage(Sprites.portrait(h), 40.dp, description = content.heroClass(h.classId).name)
            Sprites.marker(h.fate)?.let { PixelImage(it, 16.dp, description = null, modifier = Modifier.align(Alignment.BottomEnd)) }
        }
        Column(Modifier.weight(1f)) {
            Text(
                "${h.fullName} · ${content.heroClass(h.classId).name} ${h.level} · " + (fateLabel ?: Labels.health(h)) + (h.descendantOf?.let { " · of ${it}'s line" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (h.isAlive) "${Heroes.describeTraits(h, content)} · ${h.gold} gold · ${w?.name ?: "unarmed"} · fame ${h.fame}"
                else "${Heroes.describeTraits(h, content)} · fame ${h.fame} · ${h.kills} kills",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (fateLabel != null) Text(fateLabel.substringBefore(" "), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun JournalPanel(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    val journal = s.state.legacy.journal
    SectionTitle("Experiment Journal")
    Text("Knowledge survives the forge's fall. Repeat a pairing to understand it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val entries = journal.interactions.entries.sortedBy { it.key }
    if (entries.isEmpty()) Text("No experiments recorded yet. Forge something!", modifier = Modifier.padding(top = 8.dp))
    entries.forEach { (key, state) ->
        val label = when (state) {
            KnowledgeState.UNKNOWN -> "unknown"
            KnowledgeState.OBSERVED -> "observed"
            KnowledgeState.UNDERSTOOD -> "understood"
            KnowledgeState.SIGNATURE_DISCOVERED -> "signature"
        }
        Text("${Journal.subjectName(content, key)} — $label: ${Journal.hint(journal, content, key)}", modifier = Modifier.padding(vertical = 2.dp))
    }
}

@Composable
fun GazettePanel(s: UiState.Playing) {
    val st = s.state
    val days = st.events.map { it.day }.distinct().sortedDescending()
    if (days.isEmpty()) Text("The presses are quiet.")
    days.forEach { day ->
        SectionTitle(Gazette.masthead(day))
        Gazette.headlines(st.eventsForDay(day)).forEach { Text("• $it", modifier = Modifier.padding(vertical = 2.dp)) }
    }
}

@Composable
fun LegacyPanel(s: UiState.Playing, vm: GameViewModel, reducedMotion: Boolean) {
    val content = vm.engine.content
    val legacy = s.state.legacy
    SectionTitle("Era ${s.state.era}")
    if (s.state.pendingBlessingOffer.isNotEmpty()) {
        Button(onClick = vm::reopenBlessingOffer, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Text("Choose a blessing") }
    }
    Text("Legacy points banked: ${legacy.points}. Rewards are claimed when the forge falls.", style = MaterialTheme.typography.bodySmall)
    SectionTitle("Permanent upgrades")
    content.upgrades.forEach { u ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            LevelDots(legacy.upgradeLevel(u.id), u.maxLevel)
            Spacer(Modifier.width(8.dp))
            Text("${u.name} — ${u.description}", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (s.state.blessings.isNotEmpty()) {
        SectionTitle("Active blessings")
        s.state.blessings.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                Sprites.blessing(b.id)?.let { PixelImage(it, 28.dp, description = null) }
                Text("${content.blessing(b.id).name} until day ${b.expiresDay}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    SectionTitle("Legend Board")
    if (legacy.legendBoard.isEmpty()) Text("No blade has earned a legend yet.", style = MaterialTheme.typography.bodySmall)
    legacy.legendBoard.forEach { Text("${it.title} — era ${it.era}, ${it.kills} kills, carried by ${it.owners.joinToString().ifEmpty { "no one" }}", style = MaterialTheme.typography.bodySmall) }
    SectionTitle("Lineages")
    if (legacy.lineages.isEmpty()) Text("No lineage has been founded yet.", style = MaterialTheme.typography.bodySmall)
    legacy.lineages.forEach { Text("${it.heroName} (era ${it.era}) ${it.deed}", style = MaterialTheme.typography.bodySmall) }
    SectionTitle("Settings")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Reduced motion")
            Text("Stops the ember animation, the reveal fade and the stepped battle replay.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = reducedMotion, onCheckedChange = { vm.setReducedMotion(it) })
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
