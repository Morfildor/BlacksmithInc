package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.combat.Action
import com.tinyblacksmith.core.combat.Aim
import com.tinyblacksmith.core.combat.Combatant
import com.tinyblacksmith.core.combat.Condition
import com.tinyblacksmith.core.combat.Cost
import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.combat.FightRules
import com.tinyblacksmith.core.combat.FightSetup
import com.tinyblacksmith.core.combat.ObjectiveKind
import com.tinyblacksmith.core.combat.Posture
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.combat.Stat
import com.tinyblacksmith.core.combat.Who
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.MissionArchetype
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CheckpointChoice
import com.tinyblacksmith.core.model.Danger
import com.tinyblacksmith.core.model.EnemySpec
import com.tinyblacksmith.core.model.FactionId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.GuildMember
import com.tinyblacksmith.core.model.GuildRunState
import com.tinyblacksmith.core.model.Hero
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.MemberStatus
import com.tinyblacksmith.core.model.MissionInstance
import com.tinyblacksmith.core.model.MissionOffer
import com.tinyblacksmith.core.model.MissionOutcome
import com.tinyblacksmith.core.model.MissionStage
import com.tinyblacksmith.core.model.Reward
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WoundKind

/*
 * The Guild destination as facts and finished sentences (a guild run only: `GameState.guild != null`). Everything here
 * reads the save and the engine's own reads (`wallForecast`, `memberUnavailable`, `fighterView`, `missionSetup`); nothing
 * is simulated and nothing is decided. Numbers of rules come from the definitions' own descriptions.
 */

/** A unit on a field: how many of it, and what is known of what it does (its moves' telegraph lines). */
@Immutable data class EnemyUi(val name: String, val count: Int, val rules: List<String>) {
    val label: String get() = if (count > 1) "$count × $name" else name
}

/** One of the three places on the wall. [member]: one of the guild (a resident of the town can stand there too). */
@Immutable data class DefenderUi(val heroId: HeroId, val portrait: Int, val name: String, val sub: String, val blade: String, val member: Boolean, val reserved: Boolean)

/** A present member who is not kept for the wall yet, or is: the Reserve / Release toggle. */
@Immutable data class ReserveUi(val heroId: HeroId, val name: String, val reserved: Boolean, val blocked: String?)

@Immutable
data class WallUi(
    val siegeDay: Int, val daysLeft: Int, val whenLine: String,
    val factionId: FactionId?, val besieger: String, val committed: Boolean, val leaning: String?,
    val weakTo: String?, val resists: String?,
    val field: List<EnemyUi>, val notes: List<String>,
    val watch: Int, val forgeHealth: Int, val forgeHealthMax: Int,
    /** In the order they would stand; fewer than [places] when the town has nobody else. */
    val defenders: List<DefenderUi>, val places: Int,
    val reserve: List<ReserveUi>, val missing: List<String>,
)

/** The choice at the door of a deeper stage. [chosen] is what the smith has already sent word of today. */
@Immutable data class CheckpointUi(val instanceId: String, val secured: String, val deeper: String, val danger: String, val enemies: List<EnemyUi>, val chosen: CheckpointChoice?, val note: String)

@Immutable data class PartyUi(val title: String, val who: String, val returns: String, val status: String?, val checkpoint: CheckpointUi?)

@Immutable data class ContractReportUi(val title: String, val outcome: String, val highlight: String?, val lines: List<String>)

@Immutable data class StageUi(val label: String, val objective: String, val danger: String, val enemies: List<EnemyUi>, val reward: String)

@Immutable
data class OfferUi(
    val id: String, val title: String, val why: String?, val description: String,
    val time: String, val missesSiege: String?, val fee: String,
    val stages: List<StageUi>, val prep: String, val failure: String, val expiry: String,
    val planned: Boolean,
)

@Immutable
data class MemberUi(
    val heroId: HeroId, val portrait: Int, val name: String, val sub: String, val role: String, val trait: String,
    /** Wound, absence or captivity in one line; null for a member who is simply at home and well. */
    val status: String?, val blade: String, val health: String, val reserved: Boolean, val branchOpen: Boolean,
)

/** [blocked]: why Recruit cannot be tapped today, in a sentence; null when it can. */
@Immutable
data class CandidateUi(val heroId: HeroId, val portrait: Int, val name: String, val sub: String, val role: String, val trait: String, val terms: String, val blocked: String?)

@Immutable data class RankUi(val rank: Int, val name: String, val description: String, val taken: Boolean, val canTake: Boolean)

@Immutable
data class RegionUi(
    val charter: String, val advantage: String, val constraint: String,
    val progress: String, val secured: Boolean, val retireBlocked: String?,
    val ranks: List<RankUi>,
    val law: List<String>, val rival: List<String>, val nemesis: List<String>, val combos: List<String>,
)

@Immutable
data class GuildUi(
    val wall: WallUi, val party: PartyUi?, val yesterday: ContractReportUi?,
    /** "Planned for today: ..." when a party is planned. */
    val plan: String?, val boardNote: String?, val offers: List<OfferUi>,
    val rosterHead: String, val roster: List<MemberUi>,
    val candidates: List<CandidateUi>, val candidatesNote: String,
    val region: RegionUi, val factions: List<FactionRowUi>,
    /** What End Day sets in motion for the guild, at most three lines. */
    val endDay: List<String>,
)

// ---- words shared by the board, the checkpoint and the contract sheet ----

internal fun dangerWords(d: Danger): String = when (d) {
    Danger.SAFE -> "Safe: nobody dies or is taken."
    Danger.DANGEROUS -> "Dangerous: if the whole party goes down, one is taken alive."
    Danger.LETHAL -> "Lethal: if the whole party goes down, one dies and one is taken."
}

internal fun postureName(p: Posture): String = p.name.lowercase().replaceFirstChar { it.uppercase() }

/** When a party of this posture turns for home, with the numbers of the rules themselves. */
internal fun postureWords(p: Posture, rules: FightRules): String = when (p) {
    Posture.CAUTIOUS -> "Leaves when a member is down or the party is under ${rules.cautiousHealthPercent}% of the health it came with."
    Posture.BALANCED -> "Leaves when ${rules.balancedDowned} are down, or when the last one standing is under ${rules.lastStandPercent}% health."
    Posture.RECKLESS -> "Never leaves."
}

internal fun returnMorning(day: Int): String = "home on the morning of day $day"

/** A party that leaves on [day] for [days] is away on the nights of [day] .. [day] + [days] - 1. */
internal fun missesSiege(day: Int, days: Int, siegeDay: Int): Boolean = siegeDay in day until day + days

private fun statWord(s: Stat) = s.name.lowercase().replaceFirstChar { it.uppercase() }
private fun names(list: List<String>) = list.joinToString(", ")

private fun GameEngine.classLine(h: Hero) = "${content.heroClass(h.classId).name} level ${h.level}"

private fun GameEngine.enemies(specs: List<EnemySpec>): List<EnemyUi> {
    val combat = content.combat ?: return emptyList()
    return specs.groupBy { it.unitId }.map { (id, all) -> combat.unit(id).let { u -> EnemyUi(u.name, all.size, u.kit.moves.mapNotNull { it.telegraph }.distinct()) } }
}

private fun GameEngine.rewardWords(state: GameState, r: Reward, factionId: FactionId?): String = listOfNotNull(
    "${r.gold} contract gold".takeIf { r.gold > 0 },
    r.materials.entries.joinToString(", ") { "${it.value} ${content.material(it.key).name}" }.ifEmpty { null },
    factionId?.takeIf { r.suppression > 0 }?.let { "sets ${content.faction(it).name} back" },
    "mends the wall".takeIf { r.integrity > 0 },
    "strengthens the militia".takeIf { r.militia > 0 },
    "spoils their preparations for the siege".takeIf { r.sabotage },
    "a relic to choose".takeIf { r.relicOffer },
    r.weaponId?.let { state.weapons[it]?.name }?.let { "$it back at the forge" },
    r.captiveId?.let { state.heroes[it]?.fullName }?.let { "$it brought home" },
).joinToString(" · ").ifEmpty { "Nothing of its own" }

private fun GameEngine.objectiveWords(stage: MissionStage): String = when {
    stage.enemies.isEmpty() -> "No fight"
    stage.objective.kind == ObjectiveKind.DEFEAT -> "Defeat them all"
    else -> "Last ${stage.objective.rounds} rounds" + (stage.protectUnitId?.let { id -> content.combat?.unitById?.get(id)?.name }?.let { " and keep the ${it.lowercase()} standing" } ?: "")
}

/** "Hunt on the Ashroad": the contract's name and the road it lies on; work at home has no road. */
internal fun GameEngine.contractTitle(offer: MissionOffer): String {
    val cat = content.guild!!
    val def = cat.mission(offer.defId)
    return if (def.archetype == MissionArchetype.EMERGENCY) def.name else "${def.name} ${cat.route(offer.factionId)?.where ?: "on the road"}"
}

private fun GameEngine.stageUi(state: GameState, offer: MissionOffer, index: Int): StageUi {
    val stage = offer.stages[index]
    val label = when {
        index == 0 -> "Secured when won"
        offer.optionalPush -> "Deeper (optional), only if won"
        else -> "Day ${index + 1}, only if won"
    }
    return StageUi(label, objectiveWords(stage), dangerWords(stage.danger), enemies(stage.enemies), rewardWords(state, stage.reward, offer.factionId))
}

private fun GameEngine.offerUi(state: GameState, g: GuildRunState, offer: MissionOffer): OfferUi {
    val def = content.guild!!.mission(offer.defId)
    val siegeDay = state.town.nextSiegeDay
    return OfferUi(
        id = offer.id, title = contractTitle(offer), why = offer.reason, description = def.description,
        time = (if (offer.days == 1) "1 day" else "${offer.days} days") + ": leaves at End Day, ${returnMorning(state.day + offer.days)}",
        missesSiege = "Misses the siege night of day $siegeDay.".takeIf { missesSiege(state.day, offer.days, siegeDay) },
        fee = if (offer.fee > 0) "Fee ${offer.fee} gold, paid when planned" else "No fee",
        stages = offer.stages.indices.map { stageUi(state, offer, it) }, prep = def.prep, failure = def.failure,
        expiry = if (offer.expiresDay == Int.MAX_VALUE) "Always on the board" else "On the board until day ${offer.expiresDay}",
        planned = g.planned?.offerId == offer.id,
    )
}

private fun bladeLine(state: GameState, heroId: HeroId): String =
    state.loanOf(heroId)?.let { "On loan: ${it.name}" } ?: state.equippedWeapon(heroId)?.let { "Own weapon: ${it.name}" } ?: "Unarmed"

private fun memberStatus(state: GameState, g: GuildRunState, m: GuildMember): String? = when {
    m.status == MemberStatus.CAPTURED -> g.captives.firstOrNull { it.heroId == m.heroId }?.let { c ->
        (c.deadlineDay - state.day).let { left -> "Held by the enemy, " + if (left == 1) "1 day left" else "$left days left" }
    } ?: "Held by the enemy"
    m.status == MemberStatus.AWAY -> g.mission?.let { "Away until the morning of day ${it.returnDay}" } ?: "Away with the party"
    m.wound != null && m.wound!!.untilDay > state.day -> m.wound!!.let { w ->
        statWord(w.kind) + " until day ${w.untilDay}" + if (w.kind == WoundKind.SCARRED) ": serves with less health" else ""
    }
    else -> null
}

private fun statWord(k: WoundKind) = k.name.lowercase().replaceFirstChar { it.uppercase() }

private fun GameEngine.traitLine(traitId: String): String = content.guild!!.trait(traitId)?.let { "${it.name}: ${it.description}" } ?: "No trait of note"

private fun GameEngine.wallUi(state: GameState, g: GuildRunState): WallUi {
    val f = wallForecast(state)!!
    val town = state.town
    val left = town.nextSiegeDay - state.day
    val plannedAway = g.planned?.let { p -> g.offers.firstOrNull { it.id == p.offerId } }?.takeIf { missesSiege(state.day, it.days, town.nextSiegeDay) }
    val present = g.members.filter { memberUnavailable(state, it.heroId) == null }
    return WallUi(
        siegeDay = town.nextSiegeDay, daysLeft = left,
        whenLine = when { left <= 0 -> "Siege tonight, after today's trading"; left == 1 -> "Siege tomorrow night · day ${town.nextSiegeDay}"; else -> "Siege in $left days · day ${town.nextSiegeDay}" },
        factionId = f.faction?.id?.takeIf { f.committed },
        besieger = if (f.committed) f.faction?.name ?: "Not yet known" else "Besieger not yet known",
        committed = f.committed,
        leaning = f.faction?.takeIf { !f.committed }?.let { "${it.name} press hardest today." },
        weakTo = f.faction?.takeIf { f.committed }?.weakTo?.word(), resists = f.faction?.takeIf { f.committed }?.resists?.word(),
        field = enemies(f.plan),
        notes = listOfNotNull(
            "The charter warlord leads this one.".takeIf { f.charter },
            "Their preparations are spoiled.".takeIf { f.sabotaged },
            plannedAway?.let { if (left <= 0) "The party you planned leaves today and is not on the wall tonight." else "The party you planned is away on the siege night of day ${town.nextSiegeDay}." },
        ),
        watch = f.watch, forgeHealth = town.integrity,
        forgeHealthMax = maxOf(town.integrity, config.startingForgeIntegrity + upgradeTotal(state.legacy, UpgradeEffect.STARTING_INTEGRITY)),
        defenders = f.defenders.map { (h, w) -> DefenderUi(h.id, Sprites.portrait(h, small = true), h.fullName, classLine(h), w?.name ?: "Unarmed", g.isMember(h.id), h.id in g.reserved) },
        places = config.guild.defenders,
        reserve = present.map { m ->
            val name = state.heroes[m.heroId]?.fullName ?: m.heroId.value
            val reserved = m.heroId in g.reserved
            ReserveUi(m.heroId, name, reserved, when {
                reserved -> null
                g.planned?.heroIds?.contains(m.heroId) == true -> "$name is in the party you planned for today."
                g.reserved.size >= config.guild.defenders -> "Only ${config.guild.defenders} places on the wall."
                else -> null
            })
        },
        missing = f.missing.map { (h, why) -> "${h.fullName}: $why" },
    )
}

private fun GameEngine.partyUi(state: GameState, m: MissionInstance): PartyUi {
    val who = m.party.mapNotNull { state.heroes[it]?.fullName }
    val at = m.outcome == null && m.stage == 1 && m.offer.optionalPush
    val deeper = m.offer.stages.getOrNull(1)
    return PartyUi(
        title = contractTitle(m.offer), who = names(who), returns = returnMorning(m.returnDay).replaceFirstChar { it.uppercase() } + ".",
        status = when (m.outcome) {
            MissionOutcome.WON -> "The work is done. They are on the way home."
            MissionOutcome.RETREATED -> "They pulled back and are on the way home."
            MissionOutcome.LOST -> "It went badly. Those who can are on the way home."
            null -> if (at) "They wait at the door of the deeper stage for your word." else "Out since day ${m.departedDay}, ${postureName(m.posture).lowercase()}."
        },
        checkpoint = deeper?.takeIf { at }?.let { stage ->
            CheckpointUi(
                m.id, secured = "Secured: " + rewardWords(state, m.secured, m.offer.factionId), deeper = "Deeper adds, only if won: " + rewardWords(state, stage.reward, m.offer.factionId),
                danger = dangerWords(stage.danger), enemies = enemies(stage.enemies), chosen = m.choice,
                note = when (m.choice) {
                    CheckpointChoice.PUSH -> "Your word is sent: push deeper. You can still change it today."
                    CheckpointChoice.RETURN -> "Your word is sent: return. You can still change it today."
                    null -> "If you choose nothing, End Day returns."
                },
            )
        },
    )
}

/** Why [c] cannot be signed today; the engine checks the roster before the purse, and so does this. */
private fun GameEngine.recruitBlocked(state: GameState, g: GuildRunState, fee: Int): String? = when {
    g.members.size >= config.guild.maxMembers -> "The roster is full (${config.guild.maxMembers} members)."
    state.gold < fee -> "Needs $fee gold. You have ${state.gold}."
    else -> null
}

private fun GameEngine.regionUi(state: GameState, g: GuildRunState): RegionUi {
    val cat = content.guild!!
    val charter = cat.charter(g.charterId)
    val secured = g.milestone(GuildContent.CHARTER_SECURED)
    return RegionUi(
        charter = charter?.name ?: "No charter", advantage = charter?.advantage.orEmpty(), constraint = charter?.constraint.orEmpty(),
        progress = secured?.let { "Charter secured on day ${it.day}." } ?: "The charter warlord leads the first siege on or after day ${config.guild.charterDay}.",
        secured = secured != null,
        retireBlocked = "The party must be home before the chapter can be closed.".takeIf { g.mission != null },
        ranks = if (secured == null) emptyList() else cat.ranks.filter { it.rank > 0 }.sortedBy { it.rank }.map { RankUi(it.rank, it.name, it.description, taken = it.rank <= g.rank, canTake = it.rank > g.rank) },
        law = g.law?.let { l -> cat.law(l.id)?.let { def ->
            // The law holds on the days from..until-1 (`Missions.setup`); it is announced a morning ahead.
            listOf(def.name, def.description, if (state.day < l.fromDay) "From day ${l.fromDay} through day ${l.untilDay - 1}." else "In force through day ${l.untilDay - 1}.")
        } }.orEmpty(),
        rival = g.rival?.let { r ->
            listOf("${r.name}, under ${r.leaderName}", "They want ${r.wants.lowercase()} contracts.", "They take a contract off the board on day ${r.nextMoveDay}.") +
                if (r.taken.isEmpty()) listOf("They have taken nothing yet.") else listOf("Taken so far:") + r.taken
        }.orEmpty(),
        nemesis = g.nemesis?.let { n ->
            listOfNotNull("${n.name} of ${content.faction(n.factionId).name}", state.weapons[n.weaponId]?.let { "Carries ${it.name}, a weapon of this forge, since day ${n.sinceDay}." })
        }.orEmpty(),
        combos = g.comboNotes.map { it.substringAfter('|') },
    )
}

/** What End Day sets in motion for the guild: the party that leaves, the siege tonight, a checkpoint left without word. */
private fun GameEngine.guildEndDay(state: GameState, g: GuildRunState, wall: WallUi): List<String> = listOfNotNull(
    g.planned?.let { p ->
        g.offers.firstOrNull { it.id == p.offerId }?.let { o ->
            val who = p.heroIds.mapNotNull { state.heroes[it]?.name }
            "${names(who)} ${if (who.size == 1) "leaves" else "leave"} for ${content.guild!!.mission(o.defId).name}, ${returnMorning(state.day + o.days)}"
        }
    },
    if (wall.daysLeft > 0) null
    else (if (wall.committed) "Siege tonight: ${wall.besieger}. " else "Siege tonight. ") +
        if (wall.defenders.isEmpty()) "Nobody stands on the wall tonight" else "On the wall: ${names(wall.defenders.map { it.name })}",
    g.mission?.takeIf { it.outcome == null && it.stage == 1 && it.offer.optionalPush && it.choice == null }?.let { m ->
        "The party at ${content.guild!!.route(m.offer.factionId)?.name ?: "the door"} returns unless you send word"
    },
)

/** The Guild destination for [state]; null on a classic run. Pure: reads only. */
fun GameEngine.guildUi(state: GameState): GuildUi? {
    val g = state.guild ?: return null
    val cat = content.guild ?: return null
    val combat = content.combat
    val wall = wallUi(state, g)
    val wallWork = { o: MissionOffer -> o.defId == GuildContent.WALL_WORK }
    val refresh = g.candidatesDay + config.guild.candidateRefreshDays
    return GuildUi(
        wall = wall,
        party = g.mission?.let { partyUi(state, it) },
        yesterday = g.lastMission?.let { r ->
            ContractReportUi(
                r.title.replaceFirstChar { it.uppercase() },
                when (r.outcome) { MissionOutcome.WON -> if (r.continues) "Won. The party is still out" else "Won"; MissionOutcome.RETREATED -> "Pulled back"; MissionOutcome.LOST -> "Lost" },
                r.fight?.highlights?.firstOrNull()?.text, r.lines,
            )
        },
        plan = g.planned?.let { p ->
            g.offers.firstOrNull { it.id == p.offerId }?.let { o ->
                "Planned for today: ${names(p.heroIds.mapNotNull { state.heroes[it]?.name })} on ${cat.mission(o.defId).name}, ${postureName(p.posture).lowercase()}. Leaves at End Day."
            }
        },
        boardNote = "The party is out: the next contract can be planned once it is home.".takeIf { g.mission != null },
        offers = g.offers.sortedBy { wallWork(it) }.map { offerUi(state, g, it) },
        rosterHead = "Roster · ${g.members.size} of ${config.guild.maxMembers}",
        roster = g.members.mapNotNull { m ->
            val h = state.heroes[m.heroId] ?: return@mapNotNull null
            val fighter = fighterView(state, h.id)
            MemberUi(
                h.id, Sprites.portrait(h, small = true), h.fullName, classLine(h), combat?.kitByClass?.get(h.classId)?.role.orEmpty(), traitLine(m.traitId),
                memberStatus(state, g, m), bladeLine(state, h.id), fighter?.let { "Health ${it.health}/${it.maxHealth}" } ?: Labels.health(h).replaceFirstChar { it.uppercase() },
                reserved = h.id in g.reserved, branchOpen = openBranches(state, h.id).isNotEmpty(),
            )
        },
        candidates = g.candidates.mapNotNull { c ->
            val h = state.heroes[c.heroId] ?: return@mapNotNull null
            CandidateUi(
                h.id, Sprites.portrait(h, small = true), h.fullName, classLine(h), combat?.kitByClass?.get(h.classId)?.role.orEmpty(), traitLine(c.traitId),
                "Fee ${c.fee} gold · keeps ${c.sharePercent}% of contract gold", recruitBlocked(state, g, c.fee),
            )
        },
        candidatesNote = if (g.candidates.isEmpty()) "Nobody would sign today. The list is redrawn on the morning of day $refresh." else "The list is redrawn on the morning of day $refresh.",
        region = regionUi(state, g),
        // Every faction but the one already at the gates: its rise is how the next besieger is read coming.
        factions = state.factions.values.filter { it.id != wall.factionId }.sortedByDescending { it.pressure }.map { f ->
            content.faction(f.id).let { d -> FactionRowUi(f.id, d.name, f.pressure, Battle.describePressure(f.pressure).replaceFirstChar { it.uppercase() }, d.weakTo?.word()) }
        },
        endDay = guildEndDay(state, g, wall),
    )
}

/**
 * The Shop's and the Forge's siege strip in a guild run: the besieger once it is known and the day, never the classic
 * defense-against-raid outlook (a guild siege is fought on the wall, not weighed).
 */
internal fun GuildUi.threat(classic: ThreatUi?): ThreatUi? = classic?.copy(
    matchup = if (wall.committed) listOfNotNull(wall.besieger, wall.weakTo?.let { "Weak to $it" }, wall.resists?.let { "Resists $it" }).joinToString(" · ") else wall.besieger,
    outlook = "Day ${wall.siegeDay} · see the wall in Guild",
)

// ---- the contract sheet ----

@Immutable data class PickUi(val heroId: HeroId, val portrait: Int, val name: String, val sub: String, val blade: String, /** 1-based place in the order the party acts; null when not picked. */ val order: Int?, val blocked: String?)

@Immutable data class PostureUi(val posture: Posture, val name: String, val line: String, val selected: Boolean)

@Immutable
data class ContractUi(
    val offer: OfferUi, val picks: List<PickUi>, val partyMax: Int, val postures: List<PostureUi>,
    val together: List<String>, val watchOut: List<String>, val atHome: String, val returns: String,
    /** Why Send cannot be tapped; null when it can. */
    val sendBlocked: String?,
    /** This contract is today's plan (Cancel plan is offered). */
    val planned: Boolean,
)

/** The members who may still be picked for [offerId]: present, fit and not kept for the wall. */
internal fun GameEngine.pickable(state: GameState, heroId: HeroId): Boolean =
    state.guild?.let { g -> g.isMember(heroId) && heroId !in g.reserved && memberUnavailable(state, heroId) == null } == true

/**
 * The sheet of one contract with [picked] (in the order they would act) and [posture]. The statements under the pickers are
 * built from the setup `missionSetup` returns (who would stand there with which rules), never from a fight.
 */
fun GameEngine.contractUi(state: GameState, offerId: String, picked: List<HeroId>, posture: Posture): ContractUi? {
    val g = state.guild ?: return null
    val offer = g.offers.firstOrNull { it.id == offerId } ?: return null
    val party = picked.filter { pickable(state, it) }
    val setup = if (party.isEmpty()) null else missionSetup(state, offerId, party, posture)
    val siegeDay = state.town.nextSiegeDay
    val home = g.members.count { it.heroId !in party && memberUnavailable(state, it.heroId) == null }
    // A plan made earlier today gives its fee back before this one's is taken (`Missions.plan`).
    val purse = state.gold + (g.planned?.let { p -> g.offers.firstOrNull { it.id == p.offerId }?.fee } ?: 0)
    return ContractUi(
        offer = offerUi(state, g, offer),
        picks = g.members.mapNotNull { m ->
            val h = state.heroes[m.heroId] ?: return@mapNotNull null
            val why = memberUnavailable(state, h.id) ?: "is kept for the wall".takeIf { h.id in g.reserved }
            PickUi(h.id, Sprites.portrait(h, small = true), h.fullName, classLine(h), bladeLine(state, h.id), party.indexOf(h.id).takeIf { it >= 0 }?.plus(1), why?.let { "${h.fullName} $it." })
        },
        partyMax = config.guild.partyMax,
        postures = Posture.entries.map { PostureUi(it, postureName(it), postureWords(it, config.guild.fight), it == posture) },
        together = setup?.let(::worksTogether).orEmpty(),
        watchOut = setup?.let(::watchOut).orEmpty(),
        atHome = (when (home) { 0 -> "Nobody fit stays at home"; 1 -> "1 fit member stays at home"; else -> "$home fit members stay at home" }) +
            if (missesSiege(state.day, offer.days, siegeDay)) " for the siege night of day $siegeDay." else ". The siege of day $siegeDay does not fall while they are away.",
        returns = returnMorning(state.day + offer.days).replaceFirstChar { it.uppercase() } + ".",
        sendBlocked = when {
            g.mission != null -> "The party is still out."
            party.isEmpty() -> "Pick one to ${config.guild.partyMax} members."
            purse < offer.fee -> "Needs ${offer.fee} gold. You have $purse."
            else -> null
        },
        planned = g.planned?.offerId == offer.id,
    )
}

private fun fighters(setup: FightSetup): List<Combatant> = setup.party.filter { it.side == Side.PARTY && it.key != "protected" }
private fun Combatant.actions(): List<Action> = kit.moves.flatMap { it.actions } + kit.passives.flatMap { it.actions } + effects.flatMap { it.actions }
private val foeAims = setOf(Aim.EventTarget, Aim.Foe, Aim.MarkedFoe, Aim.AllFoes, Aim.WeakestFoe, Aim.BackFoe, Aim.PriorityFoe)

/**
 * Pairs of rules in the party where one member produces what another's rule answers. Only what the definitions say:
 * healing that a blade answers, a blade that cracks where something answers a crack, a state a Battlemage echoes, and
 * Charge spent where something answers its spending.
 */
internal fun worksTogether(setup: FightSetup): List<String> {
    val party = fighters(setup)
    val out = ArrayList<String>()
    for (x in party) {
        val all = x.effects + x.kit.passives
        // Somebody else's healing, answered by a rule of this member's blade.
        val healer = party.firstOrNull { it.key != x.key && it.kit.moves.any { m -> m.actions.any { a -> a is Action.Heal } } }
        if (healer != null) all.filter { it.trigger.kind == EventKind.HEALED && it.trigger.who == Who.SELF_TARGET }.forEach { e ->
            out += if (e.actions.any { it is Action.Give && it.stat == Stat.CHARGE }) "${healer.name}'s healing charges ${x.name}'s weapon (${e.name})." else "${healer.name}'s healing sets off ${e.name} for ${x.name}."
        }
        // A blade that can crack, and a rule that answers a crack.
        if (all.any { e -> e.actions.any { it is Action.Fracture } }) {
            val blade = x.weaponName ?: "${x.name}'s weapon"
            setup.partyEffects.filter { it.trigger.kind == EventKind.FRACTURED }.forEach { e -> out += if (e.id == "relic_${CombatContent.SALVAGE_BELL}") "$blade cracking rings the ${e.name}." else "$blade cracking sets off ${e.name}." }
            party.filter { it.key != x.key }.forEach { y -> y.effects.filter { it.trigger.kind == EventKind.FRACTURED }.forEach { e -> out += "$blade cracking sets off ${y.name}'s ${e.name}." } }
        }
        // A Battlemage's echo, and a state another member's rule puts on a foe.
        all.filter { e -> e.actions.any { it is Action.Echo } }.forEach { echo ->
            val stats = echo.conditions.filterIsInstance<Condition.StatIn>().flatMap { it.stats }.toSet()
            for (y in party) {
                val stat = (y.effects + y.kit.passives).flatMap { it.actions }.filterIsInstance<Action.Give>().firstOrNull { it.stat in stats && it.target in foeAims }?.stat ?: continue
                out += if (y.key == x.key) "${x.name} echoes the ${statWord(stat)} of their own weapon." else "${x.name} echoes ${y.name}'s ${statWord(stat)}."
            }
        }
        // Charge that is spent, and a rule that answers its spending.
        if (all.any { (it.cost as? Cost.Spend)?.stat == Stat.CHARGE }) {
            fun answers(e: com.tinyblacksmith.core.combat.EffectDef) = e.trigger.kind == EventKind.STAT_SPENT && e.trigger.stat == Stat.CHARGE && e.trigger.who in setOf(Who.ALLY_SOURCE, Who.ANY)
            setup.partyEffects.filter(::answers).forEach { e -> out += "When ${x.name} spends Charge, the ${e.name} answers." }
            party.forEach { y -> y.effects.filter(::answers).forEach { e -> out += "When ${x.name} spends Charge, ${y.name}'s ${e.name} answers." } }
        }
    }
    return out.distinct()
}

/** What the party lacks against this field, read from kits, blades and the enemy's known moves. */
internal fun watchOut(setup: FightSetup): List<String> {
    val party = fighters(setup)
    val known = setup.enemies.flatMap { e -> e.kit.moves.mapNotNull { it.telegraph } }.distinct()
    val guards = party.any { CombatContent.TAG_INTERCEPT in it.kit.tags || it.actions().any { a -> a is Action.Guard } }
    return listOfNotNull(
        "Nobody in this party heals.".takeIf { party.none { it.actions().any { a -> a is Action.Heal } } },
        known.firstOrNull()?.takeIf { !guards }?.let { "Nobody here raises Guard. Known of them: $it" },
    ) + party.filter { it.weaponName == null }.map { "${it.name} goes unarmed." }
}

// ---- a member on the hero sheet ----

@Immutable data class BranchUi(val id: String, val name: String, val description: String)
@Immutable data class LoanableUi(val weaponId: WeaponId, val name: String, val summary: String)

/**
 * What the hero sheet adds for a member of the guild. [numbers] are the fighter's own (`fighterView`), [rules] every rule
 * they bring with the blade in hand. [dismissBlocked] / [loanBlocked]: why that action is not offered today.
 */
@Immutable
data class MemberDetailUi(
    val heroId: HeroId, val name: String,
    val trait: String, val speciality: String?, val bonds: List<String>, val share: String,
    val numbers: List<Pair<String, String>>, val rules: List<Pair<String, String>>,
    val branches: List<BranchUi>,
    val loan: LoanableUi?, val ownBlade: String?, val loanable: List<LoanableUi>, val loanBlocked: String?,
    val dismissBlocked: String?,
)

fun GameEngine.memberDetail(state: GameState, heroId: HeroId): MemberDetailUi? {
    val g = state.guild ?: return null
    val m = g.member(heroId) ?: return null
    val h = state.heroes[heroId] ?: return null
    val cat = content.guild ?: return null
    val fighter = fighterView(state, heroId)
    val away = when (m.status) { MemberStatus.AWAY -> "${h.fullName} is away with the party."; MemberStatus.CAPTURED -> "${h.fullName} is held by the enemy."; MemberStatus.HOME -> null }
    fun loanable(w: Weapon) = LoanableUi(w.id, w.name, Labels.weaponSummary(w, content))
    return MemberDetailUi(
        heroId = heroId, name = h.fullName,
        trait = traitLine(m.traitId),
        speciality = cat.speciality(m.specialityId)?.let { "${it.name}: ${it.description}" },
        bonds = m.bonds.map { b -> "${b.kind.replaceFirstChar { it.uppercase() }} with ${state.heroes[b.withHeroId]?.fullName ?: "one who is gone"} since day ${b.sinceDay}. ${b.reason}" },
        share = "Keeps ${m.sharePercent}% of a won contract's gold. " + if (m.fee > 0) "Signed on day ${m.joinedDay} for ${m.fee} gold." else "With the guild since day ${m.joinedDay}.",
        numbers = fighter?.let { f ->
            listOf("Health" to "${f.health}/${f.maxHealth}", "Strike" to if (f.strikeMax > f.strikeMin) "${f.strikeMin} to ${f.strikeMax}" else "${f.strikeMin}", "Support" to "${f.support}")
        }.orEmpty(),
        rules = fighter?.let { f -> (f.kit.passives + f.effects).distinctBy { it.id }.map { it.name to it.description } }.orEmpty(),
        branches = openBranches(state, heroId).map { BranchUi(it.id, it.name, it.description) },
        loan = state.loanOf(heroId)?.let(::loanable),
        ownBlade = state.equippedWeapon(heroId)?.name,
        loanable = if (away != null) emptyList() else state.storedWeapons().filter { it.promisedTo == null }.map(::loanable),
        loanBlocked = away,
        dismissBlocked = away,
    )
}
