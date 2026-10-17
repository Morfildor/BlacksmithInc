package com.example.blacksmithproject.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.example.blacksmithproject.ui.SecondaryActionButton
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
import com.example.blacksmithproject.ui.SecondaryActionButton
import com.example.blacksmithproject.ui.ForgeDialog
import com.example.blacksmithproject.ui.InlineActionButton
import com.example.blacksmithproject.ui.MemberDetailUi
import com.example.blacksmithproject.ui.memberDetail
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import com.example.blacksmithproject.ui.Labels
import com.example.blacksmithproject.ui.PixelImage
import com.example.blacksmithproject.ui.Secondary
import com.example.blacksmithproject.ui.Sprites
import com.example.blacksmithproject.ui.Chevron
import com.example.blacksmithproject.ui.ChevronWay
import com.example.blacksmithproject.ui.StatRow
import com.example.blacksmithproject.ui.TipKeywords
import com.example.blacksmithproject.ui.TipSection
import com.example.blacksmithproject.ui.TipSectionBlock
import com.example.blacksmithproject.ui.backgroundName
import com.example.blacksmithproject.ui.hookLabel
import com.example.blacksmithproject.ui.hookSentence
import com.example.blacksmithproject.ui.kinOf
import com.example.blacksmithproject.ui.kinSentence
import com.example.blacksmithproject.ui.raceClass
import com.example.blacksmithproject.ui.levelWords
import com.example.blacksmithproject.ui.nameParts
import com.example.blacksmithproject.ui.situation
import com.example.blacksmithproject.ui.heroRules
import com.example.blacksmithproject.ui.TipLine
import com.example.blacksmithproject.ui.TipKeyword
import com.example.blacksmithproject.R
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalDensity
import com.tinyblacksmith.core.model.HookKind
import com.example.blacksmithproject.ui.theme.BronzeDeep
import com.example.blacksmithproject.ui.theme.Cream
import com.example.blacksmithproject.ui.theme.CreamMuted
import com.example.blacksmithproject.ui.theme.Ember
import com.example.blacksmithproject.ui.theme.ForgeSlot
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.engine.GameEngine
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
import com.tinyblacksmith.core.shopday.Lines as ShopLines
import com.tinyblacksmith.core.text.joinSentences
import com.tinyblacksmith.core.text.Terms
import kotlin.math.roundToInt

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
    /** The blade they left without and still ask for, in the words of `Lines.want`; null without a standing want. */
    val want: String?,
    val counter: List<Fact>,
    val now: List<Fact>,
    /** What they bought, ordered and refused at this shop, newest first. */
    val shop: List<String>,
    /** Everything else the town's records say about them, newest first. */
    val events: List<String>,
    /** What the guild knows of them, when they are one of its members (a guild run only). */
    val member: MemberDetailUi? = null,
    /** People, class and level under the name, as on the hero's card: "Elf Guardian · level 3" ("Guardian · level 3" without a people). */
    val kind: String? = null,
    /** How they are now, under the name: fallen, retired or gone, a member laid up, away or held, else a word for their health. Null for a member who is well (their numbers say it). */
    val condition: String? = null,
    /** Who they are beyond the fight: background, Hook, kin and ambition, each once. */
    val story: List<Fact> = emptyList(),
    /** Significant moments of their life, newest first (`Hero.reasons`). Empty shows nothing. */
    val moments: List<String> = emptyList(),
    /** [name] without a byname of residence, and that byname for a quieter line (`nameParts`). */
    val shortName: String = name, val byname: String? = null,
    /** What they hold, in the head: "Carries Iron Sword", "On loan: Iron Sword", "Unarmed". Null for one who carries nothing any more (fallen, gone). */
    val equipment: String? = null,
    /** A recruit's fight rules in full, as a member's sheet gives them: the quick card says them only in short. */
    val fight: List<TipLine> = emptyList(), val fightKeywords: List<TipKeyword> = emptyList(),
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
            res.day to dated(state, state.era, res.day, (listOf("Left without buying: ${ShopLines.reason(v.reason)}") + dear).joinSentences())
        }
    }.orEmpty()
    val dealings = records.filter { it.type in shopTypes }.asReversed().map { it.day to dated(state, it.era, it.day, it.text) }
    val member = memberDetail(state, heroId)
    return HeroDetail(
        heroId = heroId,
        name = hero?.fullName ?: first.name,
        portrait = hero?.let { Sprites.portrait(it) } ?: Sprites.portrait(first.appearance, first.classId),
        marker = hero?.let { Sprites.marker(it.fate) },
        lineage = hero?.descendantOf?.let { "Of $it's line" },
        // Lifetime numbers only where there is something to tell: a newcomer's three zeros say nothing.
        deeds = hero?.let { h ->
            listOfNotNull(
                "Fame ${h.fame}".takeIf { h.fame > 0 }, "${h.victories} ${if (h.victories == 1) "victory" else "victories"}".takeIf { h.victories > 0 },
                "${h.kills} ${if (h.kills == 1) "kill" else "kills"}".takeIf { h.kills > 0 },
            ).joinToString(", ").ifEmpty { null }
        },
        want = hero?.takeIf { it.isAlive }?.let { ShopLines.want(it, content) },
        counter = if (differs) counter else emptyList(),
        now = now,
        shop = (refusals + dealings).sortedByDescending { it.first }.map { it.second },
        events = records.filter { it.type !in shopTypes }.asReversed().take(RECENT_EVENTS).map { dated(state, it.era, it.day, it.text) },
        member = member,
        kind = "${raceClass(hero?.raceId, hero?.classId ?: first.classId)}, ${levelWords(hero?.level ?: first.level)}",
        condition = hero?.let { h ->
            fateWord(h) ?: member?.status ?: if (member != null) null else Labels.health(h).replaceFirstChar { it.uppercase() }
        },
        story = hero?.let { story(state, it) }.orEmpty(),
        moments = hero?.reasons.orEmpty().asReversed().map { dated(state, state.era, it.day, it.text) },
        shortName = nameParts(hero?.fullName ?: first.name).first, byname = nameParts(hero?.fullName ?: first.name).second,
        equipment = hero?.takeIf { it.isAlive }?.let { h ->
            state.loanOf(h.id)?.let { "On loan: ${it.name}" } ?: state.equippedWeapon(h.id)?.let { "Carries ${it.name}" } ?: "Unarmed"
        },
        fight = if (member == null && state.guild?.candidates?.any { it.heroId == heroId } == true) heroRules(state, heroId).first else emptyList(),
        fightKeywords = if (member == null && state.guild?.candidates?.any { it.heroId == heroId } == true) heroRules(state, heroId).second else emptyList(),
    )
}

/**
 * Who they are beyond the fight, most pressing first: a member's situation (leaving, a concern, a Speciality lean, a loan
 * they mean to return, in core's words), the Hook, kin, the ambition, then the background. A relative opens their sheet.
 */
private fun GameEngine.story(state: GameState, h: Hero): List<Fact> =
    situation(state, h).map { (label, text) -> Fact(label, text) } +
        listOfNotNull(
            h.hook?.let { hook ->
                hookSentence(state, h)?.let { Fact(hookLabel(hook.kind), it, heroId = hook.kinHeroId?.takeIf { id -> hook.kind == HookKind.KIN && id in state.heroes }) }
            },
        ) + kinOf(state, h).mapNotNull { id -> kinSentence(state, id)?.let { Fact(hookLabel(HookKind.KIN), it, heroId = id) } } +
        listOfNotNull(ambitionText(state, h)?.let { Fact(AMBITION, it) }, backgroundName(h)?.let { Fact("Background", it) })

/**
 * The ambition with its progress (`Heroes.describeAmbition`). The Captain's is said here in plainer words with the same
 * condition (`Agency.ledHome`: 3 won quests with them as the first member in the party's order) and the member's count.
 */
private fun GameEngine.ambitionText(state: GameState, h: Hero): String? {
    if (h.ambition == Ambition.CAPTAIN && !h.ambitionDone && h.isAlive) {
        val led = state.guild?.member(h.id)?.ledWins
        return "Wants to lead the party, first in its order, to 3 won quests." + (led?.let { " ${minOf(it, 3)} so far." } ?: "")
    }
    return Heroes.describeAmbition(h, state.equippedWeapon(h.id), config)
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
    Fact("Element taste", c.elementTaste?.let { "Favors ${Terms.element(it).lowercase()} weapons" } ?: "No favourite element"),
    // A customer's temperament (code: trait); Brave and Cautious also change the hero's Attack (`Power.traitModifier`).
    Fact("Personality", c.traits.joinToString { id -> content.traitById[id]?.let { it.name + attackNote(it.combatModifier) } ?: id.value }.ifEmpty { "None of note" }),
    Fact(PURSE, "${c.gold} gold"),
    Fact(STANDING, if (c.regular) "A shop regular" else "Not a regular yet"),
    c.ambition?.let { Fact(AMBITION, ambitionWord(it)) },
    c.guildId?.let { id -> Fact("Guild", state.town.guilds.firstOrNull { it.id == id }?.name ?: "A guild no longer standing") },
    c.mentorName?.let { m -> Fact("Mentor", m, heroId = state.heroes.values.firstOrNull { it.fullName == m }?.id) },
    Fact(CARRIES, c.equipped?.name ?: "Unarmed", weaponId = c.equipped?.weaponId),
)

/** What a personality's fight modifier does to Attack, from the catalog's own number: " (+5% Attack in fights)". */
private fun attackNote(modifier: Double): String {
    val percent = ((modifier - 1.0) * 100).roundToInt()
    return if (percent == 0) "" else " (${if (percent > 0) "+" else ""}$percent% Attack in fights)"
}

private fun ambitionWord(a: Ambition) = when (a) {
    Ambition.SLAYER -> "A slayer's vow"
    Ambition.DEFENDER -> "Sworn to defend the walls"
    Ambition.COLLECTOR -> "Wants a prized weapon"
    Ambition.FORTUNE -> "Saving a fortune"
    Ambition.AVENGER -> "Bent on an old grudge"
    Ambition.HEIRLOOM -> "Wants the family weapon back"
    Ambition.CAPTAIN -> "Wants to lead the party"
    Ambition.NAME -> "Wants to make a name"
}

private fun fateWord(h: Hero): String? = when (h.fate) {
    HeroFate.ALIVE -> null
    HeroFate.DEAD -> "Fallen" + (h.diedOnDay?.let { " on day $it" } ?: "")
    HeroFate.RETIRED -> "Retired" + (h.retiredOnDay?.let { " on day $it" } ?: "")
    HeroFate.DEPARTED -> "Left the guild" + (h.departedOnDay?.let { " on day $it" } ?: "")
}

/** The hero sheet as a modal bottom sheet. Dismissal (scrim, swipe, Back, Close) is reported, never acted on here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroDetailSheet(
    detail: HeroDetail, onOpenHero: (HeroId) -> Unit, onOpenItem: (WeaponId) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, member: MemberActions? = null,
    closeLabel: String = "Close", scroll: ScrollState = rememberScrollState(),
) {
    // A sheet is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },   // the handle is a target too: 48 dp, not the default 32
        modifier = modifier.semantics { testTagsAsResourceId = true }.testTag("hero_sheet"),
    ) { HeroDetailContent(detail, onOpenHero, onOpenItem, onDismiss, member = member, closeLabel = closeLabel, scroll = scroll) }
}

/**
 * The body of the hero sheet: one scrolling column, usable outside a sheet (the shop day's overlay, tests). Its top reads
 * like the hero's card (V3): the face, the name, class and level; for a member of the guild the fight's numbers as stat
 * rows and the trait, speciality and share as + / − lines. The facts, the guild's part, the shop and the records follow.
 */
@Composable
fun HeroDetailContent(
    detail: HeroDetail, onOpenHero: (HeroId) -> Unit, onOpenItem: (WeaponId) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, closeLabel: String = "Close",
    member: MemberActions? = null, scroll: ScrollState = rememberScrollState(),
) {
    Column(modifier.fillMaxWidth().verticalScroll(scroll).navigationBarsPadding().padding(horizontal = Space.md).padding(bottom = Space.lg)) {
        HeroHead(detail, onDismiss)
        // Their story first, most pressing line on top, each sentence under its label at full width so a long label or
        // sentence never breaks inside a word.
        if (detail.story.isNotEmpty()) {
            SheetSection("Their story")
            FactBlock(null, detail.story, "sheet_story", onOpenHero, onOpenItem, stacked = true)
        }
        detail.member?.let { MemberHead(it) }
        detail.want?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.sm).testTag("hero_want")) }
        FactBlock(AT_THE_COUNTER.takeIf { detail.counter.isNotEmpty() }, detail.counter, "sheet_counter", onOpenHero, onOpenItem)
        // The head says class, level, condition and what they carry, a member's health is a stat row and the ambition is
        // in their story: the facts do not say them twice. Against a visit snapshot the class stays, since a changed level is news.
        val told = detail.story.any { it.label == AMBITION }
        val shown = detail.now.filterNot { f ->
            (f.label == HEALTH && (detail.condition != null || detail.member?.numbers?.isNotEmpty() == true)) || (f.label == AMBITION && told) ||
                (f.label == CARRIES && detail.equipment != null) || (detail.counter.isEmpty() && f.label == CLASS && detail.kind != null)
        }
        if (shown.isNotEmpty() && detail.counter.isEmpty()) SheetSection("As a customer")
        FactBlock(NOW.takeIf { detail.counter.isNotEmpty() }, shown, "sheet_now", onOpenHero, onOpenItem)
        detail.member?.let { MemberSection(it, member, onOpenItem) }
        if (detail.fight.isNotEmpty()) {
            SheetSection("Abilities")
            Column(Modifier.fillMaxWidth().testTag("sheet_fight"), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                TipSectionBlock(TipSection(null, detail.fight))
                TipKeywords(detail.fightKeywords)
            }
        }
        if (detail.moments.isNotEmpty()) {
            SheetSection("Moments")
            Column(Modifier.testTag("sheet_moments")) { Lines(detail.moments, "") }
        }
        SheetSection("With your shop")
        Lines(detail.shop, "No visits or purchases recorded yet.")
        SheetSection("Recent events")
        Lines(detail.events, "No recent activity recorded.")
        SecondaryActionButton(closeLabel, onDismiss, Modifier.fillMaxWidth().padding(top = Space.md).heightIn(min = 48.dp).testTag("sheet_close"))
    }
}

private const val CLASS = "Class"
private const val HEALTH = "Health"

/**
 * The face, the full name (wrapping, never cut), people, class and level, and the condition. Beside the face as a rule,
 * under it once the text is so large that a long family name would break inside a word.
 */
@Composable
private fun HeroHead(detail: HeroDetail, onDismiss: () -> Unit) {
    val face = @Composable {
        Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep).padding(4.dp)) {
            PixelImage(detail.portrait, 72.dp, description = null)
            detail.marker?.let { PixelImage(it, 24.dp, description = null, modifier = Modifier.align(Alignment.BottomEnd)) }
        }
    }
    // Reading order: name (a byname of residence on a quieter line), condition, people, class and level, what they carry.
    val words = @Composable { m: Modifier ->
        Column(m.semantics(mergeDescendants = true) {}) {
            Text(detail.shortName, style = MaterialTheme.typography.titleLarge, color = Cream, modifier = Modifier.semantics { heading() }.testTag("sheet_name"))
            detail.byname?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.testTag("sheet_byname")) }
            // A member's condition is news (laid up, away, held); anyone else's is the plain state of their health.
            detail.condition?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = if (detail.member != null) Ember else Cream, modifier = Modifier.testTag("sheet_condition")) }
            detail.kind?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CreamMuted, modifier = Modifier.testTag("sheet_kind")) }
            detail.equipment?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Cream, modifier = Modifier.testTag("sheet_equipment")) }
            detail.lineage?.let { Secondary(it) }
            detail.deeds?.let { Secondary(it) }
        }
    }
    // The way out at the top as well as at the foot, so a long sheet never has to be scrolled to be left.
    val close = @Composable {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onDismiss).semantics { contentDescription = "Close" }.testTag("sheet_close_top"),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = CreamMuted, modifier = Modifier.size(18.dp)) }
    }
    // The face lines up with the name. With very large text it stands above the words, so a long family name never breaks inside a word.
    if (LocalDensity.current.fontScale > 1.5f) Column(Modifier.fillMaxWidth().padding(top = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { face(); Spacer(Modifier.weight(1f)); close() }
        words(Modifier.fillMaxWidth())
    }
    else Row(Modifier.fillMaxWidth().padding(top = Space.sm), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        face(); words(Modifier.weight(1f)); close()
    }
}

/** A member's card at the top of their sheet: the numbers they would enter a fight with, then what they bring and cost. Their condition is in the head. */
@Composable
private fun MemberHead(m: MemberDetailUi) {
    SheetSection("In a fight")
    Column(Modifier.fillMaxWidth().padding(top = Space.sm).testTag("sheet_member_head"), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (m.numbers.isNotEmpty()) Column { m.numbers.forEach { (label, value) -> StatRow(label, value) } }
        TipSectionBlock(TipSection(null, m.gains))
        TipSectionBlock(TipSection("Terms", m.costs))
    }
}

/** What the smith can do to a member from their sheet; every one of them is a command, sent by the caller. */
class MemberActions(
    val enabled: Boolean, val onChooseBranch: (String) -> Unit, val onLoan: (WeaponId) -> Unit, val onRecall: (WeaponId) -> Unit, val onDismissMember: () -> Unit,
)

/**
 * A member of the guild on their sheet: trait and bonds, their share, the numbers and rules they bring to a fight, a
 * branch their deeds opened, the blade on loan and what could be loaned instead, and letting them go. Without
 * [actions] (the day's report, a test) the same facts with nothing to tap.
 */
@Composable
private fun MemberSection(m: MemberDetailUi, actions: MemberActions?, onOpenItem: (WeaponId) -> Unit) {
    var confirmDismiss by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().testTag("sheet_member")) {
        SheetSection("In the guild")
        // The trait, the speciality and the share are + / − lines at the top; here the guild's history with them.
        Secondary(m.since)
        m.bonds.forEach { Secondary(it) }
        if (m.home.isNotEmpty()) Secondary(m.home, Modifier.testTag("sheet_member_home"))
        if (m.branches.isEmpty()) m.unlock?.let { Secondary(it, Modifier.testTag("sheet_member_unlock")) }

        if (m.branches.isNotEmpty()) {
            SheetSection("Choose a speciality")
            Secondary("Choose one now or leave it for later. The choice is permanent.")
            m.branches.forEach { b ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Column(Modifier.weight(1f)) {
                        Text(b.name, style = MaterialTheme.typography.titleSmall)
                        Secondary(b.description)
                        if (b.opened.isNotEmpty()) Secondary(b.opened)
                    }
                    if (actions != null) InlineActionButton("Choose", { actions.onChooseBranch(b.id) }, Modifier.testTag("sheet_branch_${b.id}"), enabled = actions.enabled)
                }
            }
        }

        SheetSection("Weapon")
        val loan = m.loan
        if (loan != null) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Column(Modifier.weight(1f).clickable(onClickLabel = "Open ${loan.name}", role = Role.Button) { onOpenItem(loan.weaponId) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("On loan: ${loan.name}", style = MaterialTheme.typography.bodyMedium, color = Cream)
                        Chevron(ChevronWay.RIGHT, tint = CreamMuted, size = 18.dp)
                    }
                    Secondary(loan.summary)
                }
                if (actions != null) InlineActionButton("Recall", { actions.onRecall(loan.weaponId) }, Modifier.testTag("sheet_recall"), enabled = actions.enabled && m.loanBlocked == null)
            }
        } else Secondary(m.ownBlade?.let { "Carries their own $it. A loaned weapon replaces it while the loan lasts." } ?: "Unarmed. Loan them a weapon from storage.")
        m.loanBlocked?.let { Secondary(it) }
        if (actions != null && m.loanBlocked == null) {
            if (m.loanable.isEmpty()) Secondary("Nothing in storage to loan.")
            else Text("Loan a weapon", style = MaterialTheme.typography.titleSmall, color = Cream, modifier = Modifier.padding(top = Space.sm))
            m.loanable.forEach { w ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Column(Modifier.weight(1f)) {
                        Text(w.name, style = MaterialTheme.typography.bodyMedium)
                        Secondary(w.summary)
                    }
                    InlineActionButton("Loan", { actions.onLoan(w.weaponId) }, Modifier.testTag("sheet_loan_${w.weaponId.value}"), enabled = actions.enabled)
                }
            }
        }

        SheetSection("Abilities")
        // What the class does and every rule they bring, always open: when it fires, what it does and how often.
        if (m.abilities.isEmpty()) Secondary("No fight effects of their own with this weapon.")
        Column(Modifier.fillMaxWidth().testTag("sheet_member_abilities"), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            TipSectionBlock(TipSection(null, m.abilities))
            TipKeywords(m.keywords)
        }

        if (actions != null) {
            SecondaryActionButton("Dismiss from the guild", { confirmDismiss = true }, Modifier.fillMaxWidth().padding(top = Space.md).testTag("sheet_dismiss_member"), enabled = actions.enabled && m.dismissBlocked == null)
        }
    }
    if (confirmDismiss && actions != null) ForgeDialog(
        "Let ${m.name} go?", onDismiss = { confirmDismiss = false },
        actions = {
            InlineActionButton("Keep them", { confirmDismiss = false }, Modifier.testTag("sheet_dismiss_no"))
            InlineActionButton("Dismiss", { confirmDismiss = false; actions.onDismissMember() }, Modifier.testTag("sheet_dismiss_yes"))
        },
    ) {
        Text("The recruitment fee is not returned. A weapon on loan returns to storage.")
    }
}
