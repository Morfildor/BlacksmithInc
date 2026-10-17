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
    /** Class and level under the name, as on the hero's card: "Guardian · level 3". */
    val kind: String? = null,
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
    return HeroDetail(
        heroId = heroId,
        name = hero?.fullName ?: first.name,
        portrait = hero?.let { Sprites.portrait(it) } ?: Sprites.portrait(first.appearance, first.classId),
        marker = hero?.let { Sprites.marker(it.fate) },
        lineage = hero?.descendantOf?.let { "Of $it's line" },
        deeds = hero?.let { "Fame ${it.fame} · ${it.victories} ${if (it.victories == 1) "victory" else "victories"} · ${it.kills} ${if (it.kills == 1) "kill" else "kills"}" },
        want = hero?.takeIf { it.isAlive }?.let { ShopLines.want(it, content) },
        counter = if (differs) counter else emptyList(),
        now = now,
        shop = (refusals + dealings).sortedByDescending { it.first }.map { it.second },
        events = records.filter { it.type !in shopTypes }.asReversed().take(RECENT_EVENTS).map { dated(state, it.era, it.day, it.text) },
        member = memberDetail(state, heroId),
        kind = "${content.classById[hero?.classId ?: first.classId]?.name ?: first.classId.value} · level ${hero?.level ?: first.level}",
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
}

private fun fateWord(h: Hero): String? = when (h.fate) {
    HeroFate.ALIVE -> null
    HeroFate.DEAD -> "Fallen" + (h.diedOnDay?.let { " on day $it" } ?: "")
    HeroFate.RETIRED -> "Retired" + (h.retiredOnDay?.let { " on day $it" } ?: "")
}

/** The hero sheet as a modal bottom sheet. Dismissal (scrim, swipe, Back, Close) is reported, never acted on here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroDetailSheet(detail: HeroDetail, onOpenHero: (HeroId) -> Unit, onOpenItem: (WeaponId) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, member: MemberActions? = null, closeLabel: String = "Close") {
    // A sheet is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 48.dp) },   // the handle is a target too: 48 dp, not the default 32
        modifier = modifier.semantics { testTagsAsResourceId = true }.testTag("hero_sheet"),
    ) { HeroDetailContent(detail, onOpenHero, onOpenItem, onDismiss, member = member, closeLabel = closeLabel) }
}

/**
 * The body of the hero sheet: one scrolling column, usable outside a sheet (the shop day's overlay, tests). Its top reads
 * like the hero's card (V3): the face, the name, class and level; for a member of the guild the fight's numbers as stat
 * rows and the trait, speciality and share as + / − lines. The facts, the guild's part, the shop and the records follow.
 */
@Composable
fun HeroDetailContent(detail: HeroDetail, onOpenHero: (HeroId) -> Unit, onOpenItem: (WeaponId) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, closeLabel: String = "Close", member: MemberActions? = null) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Space.md).padding(bottom = Space.lg)) {
        Row(Modifier.fillMaxWidth().padding(top = Space.sm).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Box(Modifier.background(ForgeSlot).border(1.dp, BronzeDeep).padding(4.dp)) {
                PixelImage(detail.portrait, 72.dp, description = null)
                detail.marker?.let { PixelImage(it, 24.dp, description = null, modifier = Modifier.align(Alignment.BottomEnd)) }
            }
            Column(Modifier.weight(1f)) {
                Text(detail.name, style = MaterialTheme.typography.titleLarge, color = Cream, modifier = Modifier.semantics { heading() })
                detail.kind?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CreamMuted) }
                detail.lineage?.let { Secondary(it) }
                detail.deeds?.let { Secondary(it) }
            }
        }
        detail.member?.let { MemberHead(it) }
        detail.want?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Space.sm).testTag("hero_want")) }
        FactBlock(AT_THE_COUNTER.takeIf { detail.counter.isNotEmpty() }, detail.counter, "sheet_counter", onOpenHero, onOpenItem)
        // Class and level are under the name, and a member's health is a stat row: the whole hero's facts do not say them
        // twice. Against a visit snapshot every line stays, since a changed level is news.
        val shown = if (detail.counter.isNotEmpty()) detail.now else detail.now.filterNot { f ->
            (f.label == CLASS && detail.kind != null) || (f.label == HEALTH && detail.member?.numbers?.isNotEmpty() == true)
        }
        FactBlock(NOW.takeIf { detail.counter.isNotEmpty() }, shown, "sheet_now", onOpenHero, onOpenItem)
        detail.member?.let { MemberSection(it, member, onOpenItem) }
        SheetSection("With your shop")
        Lines(detail.shop, "No visits or purchases recorded yet.")
        SheetSection("Recent events")
        Lines(detail.events, "No recent activity recorded.")
        SecondaryActionButton(closeLabel, onDismiss, Modifier.fillMaxWidth().padding(top = Space.md).heightIn(min = 48.dp).testTag("sheet_close"))
    }
}

private const val CLASS = "Class"
private const val HEALTH = "Health"

/** A member's card at the top of their sheet: the numbers they would enter a fight with, then what they bring and cost. */
@Composable
private fun MemberHead(m: MemberDetailUi) {
    Column(Modifier.fillMaxWidth().padding(top = Space.sm).testTag("sheet_member_head"), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        m.status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ember, modifier = Modifier.testTag("sheet_member_status")) }
        if (m.numbers.isNotEmpty()) Column { m.numbers.forEach { (label, value) -> StatRow(label, value) } }
        TipSectionBlock(TipSection(null, m.gains + m.costs))
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

        SheetSection("In a fight")
        // What the class does and every rule they bring, always open: when it fires, what it does and how often.
        if (m.abilities.isEmpty()) Secondary("No fight effects of their own with this weapon.")
        Column(Modifier.fillMaxWidth().testTag("sheet_member_abilities"), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            TipSectionBlock(TipSection(null, m.abilities))
            TipKeywords(m.keywords)
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
