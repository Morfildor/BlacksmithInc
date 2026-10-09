package com.example.blacksmithproject.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.ui.Labels
import com.example.blacksmithproject.ui.PixelImage
import com.example.blacksmithproject.ui.Secondary
import com.example.blacksmithproject.ui.Sprites
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.market.Market
import com.tinyblacksmith.core.model.Ambition
import com.tinyblacksmith.core.model.CustomerSnapshot
import com.tinyblacksmith.core.model.EventType
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroFate
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.VisitKind
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponSnapshot

/**
 * A hero as the sheet shows them. When a visit snapshot was supplied and the hero has changed since (or is gone),
 * [counter] holds the hero as they stood at the counter and [now] only what is different today; otherwise [counter]
 * is empty and [now] is the whole hero.
 */
@Immutable
data class HeroDetail(
    val heroId: HeroId,
    val name: String,
    /** Drawable of the face, and of the fallen or retired marker over it. */
    val portrait: Int,
    val marker: Int?,
    val lineage: String?,
    /** Fame and deeds in one line; null for a hero who is no longer in the save. */
    val deeds: String?,
    val counter: List<Fact>,
    val now: List<Fact>,
    /** What they bought, ordered and refused at this shop, newest first. */
    val shop: List<String>,
    /** Everything else the town's records say about them, newest first. */
    val events: List<String>,
)

private val shopTypes = setOf(EventType.WEAPON_SOLD, EventType.COMMISSION_OFFERED, EventType.COMMISSION_COMPLETED, EventType.COMMISSION_EXPIRED)
private const val RECENT_EVENTS = 12

/**
 * Builds the sheet for [heroId] from the save, and from [snapshot] (a visit record) when one is supplied. Null only
 * when there is neither: a hero who has left the save still opens from the snapshot. "With your shop" reads the event
 * log, which holds the last thirty days, plus the refusals of the last resolved day.
 */
fun GameEngine.heroDetail(state: GameState, heroId: HeroId, snapshot: CustomerSnapshot? = null): HeroDetail? {
    val hero = state.heroes[heroId]
    val first = snapshot ?: hero?.let { present(state, it) } ?: return null
    val counter = snapshot?.let { facts(state, it) }.orEmpty()
    // A dead hero has no purse and carries nothing (GDD 7), so those lines are not said of them.
    val today = hero?.let { h -> facts(state, present(state, h)).filter { h.isAlive || it.label !in setOf(PURSE, CARRIES, STANDING) } }
    val differs = snapshot != null && (hero == null || !hero.isAlive || today != counter)
    val health = hero?.let { Fact("Health", fateWord(it) ?: Labels.health(it).replaceFirstChar { c -> c.uppercase() }) }
    val now = when {
        hero == null -> listOf(Fact("Whereabouts", "No longer in the town's records"))
        differs -> listOfNotNull(health) + changed(counter, today!!)
        // The whole hero: the ambition is told with its progress, which a snapshot does not carry.
        else -> listOfNotNull(health) + today!!.mapNotNull { f ->
            if (f.label != AMBITION) f else Heroes.describeAmbition(hero, state.equippedWeapon(hero.id), config)?.let { f.copy(value = it) }
        }
    }
    val records = recordsOf(state, heroId.value)
    val refusals = state.lastResolution?.let { res ->
        res.visits.filter { it.heroId == heroId && it.kind == VisitKind.BROWSE && it.purchasedWeaponId == null }.map { v ->
            val dear = v.considered.filter { it.shortBy != null }.mapNotNull { c -> res.shopWeapons.firstOrNull { it.weaponId == c.weaponId }?.let { "${it.name} was ${c.shortBy} gold out of reach" } }
            res.day to dated(state, state.era, res.day, (listOf("Left without buying: ${Gazette.visitReason(v.reason)}") + dear).joinToString("; ") + ".")
        }
    }.orEmpty()
    val dealings = records.filter { it.type in shopTypes }.asReversed().map { it.day to dated(state, it.era, it.day, it.text) }
    return HeroDetail(
        heroId = heroId,
        name = hero?.fullName ?: first.name,
        portrait = hero?.let { Sprites.portrait(it) } ?: Sprites.portrait(first.appearance, first.classId),
        marker = hero?.let { Sprites.marker(it.fate) },
        lineage = hero?.descendantOf?.let { "Of $it's line" },
        deeds = hero?.let { "Fame ${it.fame} · ${it.victories} ${if (it.victories == 1) "victory" else "victories"} · ${it.kills} ${if (it.kills == 1) "kill" else "kills"}" },
        counter = if (differs) counter else emptyList(),
        now = now,
        shop = (refusals + dealings).sortedByDescending { it.first }.map { it.second },
        events = records.filter { it.type !in shopTypes }.asReversed().take(RECENT_EVENTS).map { dated(state, it.era, it.day, it.text) },
    )
}

private const val PURSE = "Purse"
private const val CARRIES = "Carries"
private const val STANDING = "Standing"
private const val AMBITION = "Ambition"

/** The hero of today in the shape of a visit snapshot, so the counter and the present are told by one function. */
private fun GameEngine.present(state: GameState, h: Hero) = CustomerSnapshot(
    heroId = h.id, name = h.fullName, classId = h.classId, level = h.level, appearance = "", traits = h.traits, elementTaste = h.elementTaste,
    ambition = h.ambition, gold = h.gold, loyalty = h.loyalty, regular = Market.isRegular(h, config), guildId = h.guildId, mentorName = h.mentorName,
    equipped = state.equippedWeapon(h.id)?.let { WeaponSnapshot.of(it) },
)

private fun GameEngine.facts(state: GameState, c: CustomerSnapshot): List<Fact> = listOfNotNull(
    Fact("Class", "${content.classById[c.classId]?.name ?: c.classId.value}, level ${c.level}"),
    Fact("Element taste", c.elementTaste?.let { "Favours ${it.name.lowercase()} blades" } ?: "No favourite element"),
    Fact("Traits", c.traits.joinToString { content.traitById[it]?.name ?: it.value }.ifEmpty { "None of note" }),
    Fact(PURSE, "${c.gold} gold"),
    Fact(STANDING, if (c.regular) "A regular of your shop" else "Not a regular yet"),
    c.ambition?.let { Fact(AMBITION, ambitionWord(it)) },
    c.guildId?.let { id -> Fact("Guild", state.town.guilds.firstOrNull { it.id == id }?.name ?: "A guild no longer standing") },
    c.mentorName?.let { m -> Fact("Mentor", m, heroId = state.heroes.values.firstOrNull { it.fullName == m }?.id) },
    Fact(CARRIES, c.equipped?.name ?: "Unarmed", weaponId = c.equipped?.weaponId),
)

private fun ambitionWord(a: Ambition) = when (a) {
    Ambition.SLAYER -> "A slayer's vow"
    Ambition.DEFENDER -> "Sworn to defend the walls"
    Ambition.COLLECTOR -> "Wants a prized weapon"
    Ambition.FORTUNE -> "Saving a fortune"
}

private fun fateWord(h: Hero): String? = when (h.fate) {
    HeroFate.ALIVE -> null
    HeroFate.DEAD -> "Fallen" + (h.diedOnDay?.let { " on day $it" } ?: "")
    HeroFate.RETIRED -> "Retired" + (h.retiredOnDay?.let { " on day $it" } ?: "")
}

/** The hero sheet as a modal bottom sheet. Dismissal (scrim, swipe, Back, Close) is reported, never acted on here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroDetailSheet(detail: HeroDetail, onOpenHero: (HeroId) -> Unit, onOpenItem: (WeaponId) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // A sheet is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },   // the handle is a target too: 48 dp, not the default 32
        modifier = modifier.semantics { testTagsAsResourceId = true }.testTag("hero_sheet"),
    ) { HeroDetailContent(detail, onOpenHero, onOpenItem, onDismiss) }
}

/** The body of the hero sheet: one scrolling column, usable outside a sheet (the shop day's overlay, tests). */
@Composable
fun HeroDetailContent(detail: HeroDetail, onOpenHero: (HeroId) -> Unit, onOpenItem: (WeaponId) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Space.md).padding(bottom = Space.lg)) {
        Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Box {
                PixelImage(detail.portrait, 72.dp, description = null)
                detail.marker?.let { PixelImage(it, 24.dp, description = null, modifier = Modifier.align(Alignment.BottomEnd)) }
            }
            Column(Modifier.weight(1f)) {
                Text(detail.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                detail.lineage?.let { Secondary(it) }
                detail.deeds?.let { Secondary(it) }
            }
        }
        FactBlock(AT_THE_COUNTER.takeIf { detail.counter.isNotEmpty() }, detail.counter, "sheet_counter", onOpenHero, onOpenItem)
        FactBlock(NOW.takeIf { detail.counter.isNotEmpty() }, detail.now, "sheet_now", onOpenHero, onOpenItem)
        SheetSection("With your shop")
        Lines(detail.shop, "Nothing between you yet.")
        SheetSection("Recent events")
        Lines(detail.events, "The town's records say nothing of late.")
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = Space.md).heightIn(min = 48.dp).testTag("sheet_close")) { Text("Close") }
    }
}
