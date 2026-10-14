package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.example.blacksmithproject.GuildTab
import com.example.blacksmithproject.GuildTabMemory
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.MissionArchetype
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.GuildMember
import com.tinyblacksmith.core.model.GuildRunState
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.MemberStatus
import com.tinyblacksmith.core.model.MissionOffer

/*
 * What the Guild destination puts first and what it says is still to come, as facts read from the save and the content
 * catalog: today's actionable rows, the contracts the board can still post (`Missions.refreshBoard`'s own conditions),
 * who can be kept for the wall (`GuildOps.reserve`'s own checks), and what the charter opens (`Stories`).
 */

/** Where a row of Today takes the smith: a part of the Guild (and the row in it), a member's sheet, or the wall's assign sheet. */
sealed interface TodayTarget {
    data class Tab(val tab: GuildTab, val anchor: String) : TodayTarget
    data class Hero(val heroId: HeroId) : TodayTarget
    data object AssignWall : TodayTarget
}

/** One thing that needs the smith today: what it is, and the button that goes to where it is done. */
@Immutable data class TodayUi(val key: String, val text: String, val action: String, val target: TodayTarget) {
    val tab: GuildTab? get() = when (target) { is TodayTarget.Tab -> target.tab; TodayTarget.AssignWall -> GuildTab.WALL; is TodayTarget.Hero -> GuildTab.COMPANY }
}

/** At most three rows: more is a list, not a summary. */
const val TODAY_ROWS = 3

/** A contract the board does not show today, and the rule that brings it. */
@Immutable data class UpcomingUi(val name: String, val condition: String)

/** What a row of the assign sheet can do: keep the member for the wall, let them go, keep today's party at home, or nothing (the reason says why). */
enum class AssignAction { RESERVE, RELEASE, KEEP_HOME, NONE }

/** A member on the assign sheet: [reason] is why they cannot simply be kept today, null when they can. */
@Immutable data class AssignUi(val heroId: HeroId, val portrait: Int, val name: String, val sub: String, val reason: String?, val action: AssignAction)

enum class StepState { DONE, OPEN, LOCKED }

/** One thing the charter opens: its state, the rule that opens it, and what it changes. */
@Immutable data class StepUi(val key: String, val name: String, val state: StepState, val condition: String, val effect: String)

/** The tone of a member's status chip: fit, held back, or out of reach. */
enum class ChipTone { OK, KEPT, HURT, OUT }

/**
 * The Guild part shown on a visit: the one chosen today, else Wall when the siege is today or tomorrow, else Contracts.
 * The day changes only through End Day and the shop day, so a new default never replaces the part under the smith's thumb.
 */
fun guildTabFor(memory: GuildTabMemory?, day: Int, daysToSiege: Int): GuildTab =
    memory?.takeIf { it.day == day }?.tab ?: if (daysToSiege <= 1) GuildTab.WALL else GuildTab.CONTRACTS

/** The member's state as one short chip, the wall's keeping first (it is the smith's own choice). */
internal fun memberChip(state: GameState, g: GuildRunState, m: GuildMember): Pair<String, ChipTone> {
    val wound = m.wound?.takeIf { it.untilDay > state.day }
    return when {
        m.status == MemberStatus.CAPTURED -> (g.captives.firstOrNull { it.heroId == m.heroId }?.let { "Held · ${it.deadlineDay - state.day}d left" } ?: "Held") to ChipTone.OUT
        m.status == MemberStatus.AWAY -> (g.mission?.let { "Away · back day ${it.returnDay}" } ?: "Away") to ChipTone.OUT
        wound != null -> "${wound.kind.name.lowercase().replaceFirstChar { it.uppercase() }} · day ${wound.untilDay}" to ChipTone.HURT
        m.heroId in g.reserved -> "Kept for the wall" to ChipTone.KEPT
        else -> "Fit" to ChipTone.OK
    }
}

/** What a contract brings besides gold and stock, as short chips: the board's reason to look twice. */
internal fun GameEngine.specialRewards(state: GameState, offer: MissionOffer): List<String> = offer.stages.flatMap { s ->
    val r = s.reward
    listOfNotNull(
        "A relic".takeIf { r.relicOffer },
        "Supplier's thanks".takeIf { r.supplier },
        "Spoils their preparation".takeIf { r.sabotage },
        "Heartfire".takeIf { r.heartfire > 0 },
        r.captiveId?.let { state.heroes[it]?.name }?.let { "Brings $it home" },
        r.weaponId?.let { state.weapons[it]?.name }?.let { "Brings back $it" },
    )
}.distinct()

/**
 * Today's rows, most pressing first, at most [TODAY_ROWS]: a checkpoint without word, an empty place on the wall on the
 * eve or the day, a member held with a rescue on the board, no contract planned, a branch to choose, a charter just
 * secured. The siege's own countdown is never a row (the header says it).
 */
internal fun GameEngine.guildToday(state: GameState, g: GuildRunState, wall: WallUi): List<TodayUi> {
    val m = g.mission
    val rows = listOfNotNull(
        m?.takeIf { it.outcome == null && it.stage == 1 && it.offer.optionalPush && it.choice == null }?.let {
            TodayUi("checkpoint", "The party waits at the deeper door", "Decide", TodayTarget.Tab(GuildTab.CONTRACTS, "party"))
        },
        TodayUi(
            "wall", if (wall.daysLeft <= 0) "A place on the wall is empty tonight" else "A place on the wall is empty tomorrow", "Assign defender", TodayTarget.AssignWall,
        ).takeIf { wall.daysLeft <= 1 && wall.defenders.size < wall.places },
    ) + g.captives.mapNotNull { c ->
        val offer = g.offers.firstOrNull { it.subjectHeroId == c.heroId } ?: return@mapNotNull null
        val name = state.heroes[c.heroId]?.name ?: return@mapNotNull null
        val left = c.deadlineDay - state.day
        TodayUi("rescue_${c.heroId.value}", "$name is held · ${if (left == 1) "1 day" else "$left days"} left", "See rescue", TodayTarget.Tab(GuildTab.CONTRACTS, "offer_${offer.id}"))
    } + listOfNotNull(
        TodayUi("plan", "No contract planned today", "Choose contract", TodayTarget.Tab(GuildTab.CONTRACTS, "board")).takeIf { m == null && g.planned == null && g.offers.isNotEmpty() },
    ) + g.members.firstOrNull { openBranches(state, it.heroId).isNotEmpty() }?.let { b ->
        listOf(TodayUi("branch", "${state.heroes[b.heroId]?.name ?: "A member"} can choose a branch", "Choose branch", TodayTarget.Hero(b.heroId)))
    }.orEmpty() + listOfNotNull(
        g.milestone(GuildContent.CHARTER_SECURED)?.takeIf { it.day >= state.day - 1 && !g.retired }?.let {
            TodayUi("charter", "Charter secured", "Open charter", TodayTarget.Tab(GuildTab.CHARTER, "opens"))
        },
    )
    return rows.take(TODAY_ROWS)
}

/**
 * The contracts the board can post but does not show today, each with the rule that brings it (`Missions.refreshBoard`):
 * a drawn contract before its first day (none when the charter opens them early), the siege's two jobs, a rescue, a
 * recovery. What is on the board already is not listed.
 */
internal fun GameEngine.upcomingContracts(state: GameState, g: GuildRunState): List<UpcomingUi> {
    val cat = content.guild ?: return emptyList()
    val early = cat.charter(g.charterId)?.earlyDanger == true
    val onBoard = g.offers.map { it.defId }.toSet()
    val drawn = cat.missions
        .filter { it.weight > 0.0 && it.archetype != MissionArchetype.SUPPLY && it.archetype != MissionArchetype.SABOTAGE && !early && state.day < it.minDay && it.id !in onBoard }
        .sortedBy { it.minDay }
        .map { UpcomingUi(it.name, "May be posted from day ${it.minDay}") }
    val sabotage = cat.missions.firstOrNull { it.archetype == MissionArchetype.SABOTAGE }
        ?.takeIf { def -> g.offers.none { cat.mission(it.defId).archetype == MissionArchetype.SABOTAGE } && def.id !in onBoard }
        ?.let { UpcomingUi("${it.name} and an opportunity", "Posted together once a siege's besieger is known, while a party can still be home for it") }
    val rescue = cat.missionById[GuildContent.RESCUE]?.takeIf { GuildContent.RESCUE !in onBoard }?.let { UpcomingUi(it.name, "Posted while a member is held by the enemy") }
    val recovery = cat.missionById[GuildContent.RECOVERY]?.takeIf { GuildContent.RECOVERY !in onBoard }?.let { UpcomingUi(it.name, "Posted while an enemy carries a weapon of this forge") }
    return drawn + listOfNotNull(sabotage, rescue, recovery)
}

/**
 * Every member as the assign sheet has them, with the checks of `GuildOps.reserve` and of the wall itself: a kept member
 * can be released; one in today's planned party can be kept home by cancelling the plan; one away, held or laid up has
 * the reason and no button (keeping a laid-up member would not put them on the wall); a fit one can be kept while a
 * place is free.
 */
internal fun GameEngine.assignRows(state: GameState, g: GuildRunState, places: Int): List<AssignUi> = g.members.mapNotNull { m ->
    val h = state.heroes[m.heroId] ?: return@mapNotNull null
    val away = memberUnavailable(state, h.id)
    val (reason, action) = when {
        h.id in g.reserved -> "Kept for the wall" to AssignAction.RELEASE
        // The roster's own words where they say when (home on day N, wounded until day N, days left to a rescue).
        away != null -> (memberStatus(state, g, m) ?: away.removePrefix("is ").replaceFirstChar { it.uppercase() }) to AssignAction.NONE
        g.planned?.heroIds?.contains(h.id) == true -> "In today's planned party" to AssignAction.KEEP_HOME
        g.reserved.size >= places -> "All $places places are already kept" to AssignAction.NONE
        else -> null to AssignAction.RESERVE
    }
    AssignUi(h.id, Sprites.portrait(h, small = true), h.fullName, "${content.heroClass(h.classId).name} level ${h.level}", reason, action)
}

/**
 * What the charter opens, as the rules have it (`Stories.charterSiege`, `Stories.setRank`): securing it (the charter
 * warlord broken at the wall) opens retiring and the ranks; any higher rank may then be taken, and none is ever given back.
 */
internal fun GameEngine.charterSteps(state: GameState, g: GuildRunState): List<StepUi> {
    val cat = content.guild ?: return emptyList()
    val secured = g.milestone(GuildContent.CHARTER_SECURED)
    return listOf(
        StepUi(
            "charter", "Secure the charter", if (secured != null) StepState.DONE else StepState.LOCKED,
            secured?.let { "Secured on day ${it.day}." }
                ?: "Break the charter warlord at the wall. He leads the first siege on or after day ${config.guild.charterDay}. If he is held off, he leads the next one too.",
            "Opens retiring the chapter as a success, and the ranks.",
        ),
    ) + cat.ranks.filter { it.rank > 0 }.sortedBy { it.rank }.map { r ->
        StepUi(
            "rank_${r.rank}", r.name,
            when { r.rank <= g.rank -> StepState.DONE; secured != null -> StepState.OPEN; else -> StepState.LOCKED },
            when { r.rank <= g.rank -> "Taken."; secured != null -> "May be taken now. A rank once taken is kept."; else -> "After the charter is secured." },
            r.description,
        )
    }
}
