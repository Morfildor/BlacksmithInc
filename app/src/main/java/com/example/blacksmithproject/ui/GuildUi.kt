package com.example.blacksmithproject.ui

import kotlin.math.roundToInt
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

/** What the besieger prepares for this siege: what it does, each answer the smith has besides spoiling it, and the job that spoils it. */
@Immutable data class PrepUi(val name: String, val description: String, val answers: List<String>, val sabotage: String)

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
    /** Rules 6: the preparation still standing, the one a party spoiled, and the bearer of a weapon of this forge who marches with them. */
    val prep: PrepUi? = null, val spoiled: String? = null, val nemesis: String? = null,
    /** What the militia and the armory are worth on the night, and what militia costs to keep: the rule's own numbers. */
    val watchLine: String = "",
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
    /** Rules 6: "Opportunity" for a chance that comes with a siege, what is different about this posting, and what is lost if nobody takes it. */
    val tag: String? = null, val modifier: String? = null, val stake: String? = null,
    /** The board's one line: the worst danger of its days, first, and what the first day brings. Details are on the sheet. */
    val danger: Danger = Danger.SAFE, val reward: String = "",
    /** The modifier's name alone, for the board; [modifier] has it whole. */
    val modifierName: String? = null,
    /** How many days it takes and what it costs, as numbers for the board's chips; and what it brings besides gold and stock. */
    val days: Int = 1, val feeGold: Int = 0, val specials: List<String> = emptyList(),
) {
    /** "2 days · Fee 20 gold": how long it takes and what it costs, for the board. */
    val terms: String get() = listOf(time.substringBefore(':'), fee).joinToString(" · ")
}

@Immutable
data class MemberUi(
    val heroId: HeroId, val portrait: Int, val name: String, val sub: String, val role: String, val trait: String,
    /** Wound, absence or captivity in one line; null for a member who is simply at home and well. */
    val status: String?, val blade: String, val health: String, val reserved: Boolean, val branchOpen: Boolean,
    /** The roster's one chip: fit, kept for the wall, laid up, away or held. */
    val chip: String = "Fit", val chipTone: ChipTone = ChipTone.OK,
) {
    /** The roster's one line under the name: class, health and the wall; role and trait are on the hero's sheet. */
    val line: String get() = "$sub · $health" + if (reserved) " · kept for the wall" else ""
}

/** [blocked]: why Recruit cannot be tapped today, in a sentence; null when it can. */
@Immutable
data class CandidateUi(val heroId: HeroId, val portrait: Int, val name: String, val sub: String, val role: String, val trait: String, val terms: String, val blocked: String?) {
    /** Class and the trait's name in one line; what the trait does is on the hero's sheet. */
    val line: String get() = "$sub · ${trait.substringBefore(':')}"
}

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
    /** Owner decision J: what members at home do without a command, in one line. */
    val rosterNote: String = HOME_LINE,
    val candidates: List<CandidateUi>, val candidatesNote: String,
    val region: RegionUi, val factions: List<FactionRowUi>,
    /** What End Day sets in motion for the guild, at most three lines. */
    val endDay: List<String>,
    /** What needs the smith today, at most [TODAY_ROWS]; the contracts still to come; the assign sheet; what the charter opens. */
    val today: List<TodayUi> = emptyList(), val upcoming: List<UpcomingUi> = emptyList(),
    val assign: List<AssignUi> = emptyList(), val steps: List<StepUi> = emptyList(),
    /** The Company part has something to choose (a branch open), even when Today has no room for it. */
    val companyNeeds: Boolean = false,
)

// ---- words shared by the board, the checkpoint and the contract sheet ----

/** What members at home do each day on their own (`GuildOps.homeDay`): no daily command, owner decision J. */
internal const val HOME_LINE = "At home, the hurt rest and the fit train on their own. You choose who goes out and who holds the wall."

/** The danger alone, for the board's line: the sheet says what it means. */
internal fun dangerWord(d: Danger): String = when (d) { Danger.SAFE -> "Safe"; Danger.DANGEROUS -> "Dangerous"; Danger.LETHAL -> "Lethal" }

internal fun dangerWords(d: Danger): String = when (d) {
    Danger.SAFE -> "Safe: nobody dies or is captured."
    Danger.DANGEROUS -> "Dangerous: if the whole party goes down, 1 member is captured."
    Danger.LETHAL -> "Lethal: if the whole party goes down, 1 member dies and another is captured."
}

internal fun postureName(p: Posture): String = p.name.lowercase().replaceFirstChar { it.uppercase() }

/** When a party with this retreat rule (code: posture) retreats, with the numbers of the rules themselves (`Fight.retreat`). */
internal fun postureWords(p: Posture, rules: FightRules): String = when (p) {
    Posture.CAUTIOUS -> "Retreats when any member is down, or when the party's total health falls below ${rules.cautiousHealthPercent}% of what it started the fight with."
    Posture.BALANCED -> "Retreats when ${rules.balancedDowned} members are down, or when only 1 member of a larger party is still standing and is below ${rules.lastStandPercent}% of their maximum health."
    Posture.RECKLESS -> "Never retreats. The party fights until it wins or every member is down."
}

/** What every retreat rule shares: when it is checked, what a retreat costs, and the round limit (`Fight.play`, `Missions.advance`). */
internal fun GameEngine.retreatNote(rules: FightRules): String {
    // The two exceptions by their catalog names (`GuildTraitDef.retreatGoldPercent`, `CombatContent.COWARDS_MEDAL`).
    val keepers = listOfNotNull(
        content.guild?.traits?.firstOrNull { it.retreatGoldPercent > 0 }?.let { "a ${it.name} member" },
        content.relic(CombatContent.COWARDS_MEDAL)?.let { "the ${it.name}" },
    )
    return "The rule is checked at the end of each round. A retreat keeps the members alive, but that stage wins no reward." +
        (if (keepers.isEmpty()) "" else " Only ${keepers.joinToString(" or ")} still brings home part of its gold.") +
        " If neither side has won after ${rules.maxRounds} rounds, the party breaks off, and that counts as a retreat."
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
    "${r.gold} gold".takeIf { r.gold > 0 },
    r.materials.entries.joinToString(", ") { "${it.value} ${content.material(it.key).name}" }.ifEmpty { null },
    factionId?.takeIf { r.suppression > 0 }?.let { "lowers the pressure of ${content.faction(it).name}" },
    "repairs up to ${r.integrity} forge health".takeIf { r.integrity > 0 },
    "+${r.militia} militia".takeIf { r.militia > 0 },
    "spoils their preparations for the siege".takeIf { r.sabotage },
    "a relic you may take for the workshop".takeIf { r.relicOffer },
    "${r.heartfire} Heartfire for the forge".takeIf { r.heartfire > 0 },
    "the supplier's thanks: ${config.guild.supplierGratefulStock} more of every limited material each morning for ${config.guild.supplierDays} days".takeIf { r.supplier },
    "the deep road opens: ${com.tinyblacksmith.core.engine.DeepRoad.names(content, config, 1)} on sale".takeIf { r.road },
    r.weaponId?.let { state.weapons[it]?.name }?.let { "$it returned to the forge" },
    r.captiveId?.let { state.heroes[it]?.fullName }?.let { "$it rescued and brought home" },
).joinToString(" · ").ifEmpty { "Nothing of its own" }

/** Who has to be standing at the end besides the party, in a sentence: " and keep the supply cart standing". */
private fun GameEngine.protectedWords(stage: MissionStage): String =
    stage.protectUnitId?.let { id -> content.combat?.unitById?.get(id)?.name }?.let { " and keep the ${it.lowercase().removePrefix("the ")} standing" } ?: ""

private fun GameEngine.objectiveWords(stage: MissionStage): String = when {
    stage.enemies.isEmpty() -> "No fight"
    stage.objective.kind == ObjectiveKind.DEFEAT -> "Defeat every enemy" + protectedWords(stage)
    else -> "Survive ${stage.objective.rounds} rounds" + protectedWords(stage)
}

/** "Hunt on the Ashroad": the quest's (code: contract's) name and the road it lies on; work at home has no road. */
internal fun GameEngine.contractTitle(offer: MissionOffer): String {
    val cat = content.guild!!
    val def = cat.mission(offer.defId)
    return if (def.archetype == MissionArchetype.EMERGENCY) def.name else "${def.name} ${cat.route(offer.factionId)?.where ?: "on the road"}"
}

private fun GameEngine.stageUi(state: GameState, offer: MissionOffer, index: Int): StageUi {
    val stage = offer.stages[index]
    // Stage i is fought at End Day of the i-th day away, the first on the day the party leaves (`Missions.commit`, `advance`).
    val day = state.day + index
    val label = when {
        offer.stages.size == 1 -> "Day $day: the reward is yours as soon as the party wins"
        index == 0 -> "Stage 1, day $day: the reward is yours as soon as this stage is won"
        offer.optionalPush -> "Stage ${index + 1}, day $day, optional: after stage 1 is won, you choose whether the party goes deeper. Its reward is added only if this stage is won too"
        else -> "Stage ${index + 1}, day $day: fought only if stage $index is won. Its reward is added when this stage is won"
    }
    return StageUi(label, objectiveWords(stage), dangerWords(stage.danger), enemies(stage.enemies), rewardWords(state, stage.reward, offer.factionId))
}

private fun GameEngine.offerUi(state: GameState, g: GuildRunState, offer: MissionOffer): OfferUi {
    val def = content.guild!!.mission(offer.defId)
    val siegeDay = state.town.nextSiegeDay
    return OfferUi(
        id = offer.id, title = contractTitle(offer), why = offer.reason, description = def.description,
        time = (if (offer.days == 1) "1 day away" else "${offer.days} days away") + ": leaves at End Day today, ${returnMorning(state.day + offer.days)}.",
        missesSiege = "Misses the siege on day $siegeDay: the party is away that night.".takeIf { missesSiege(state.day, offer.days, siegeDay) },
        fee = if (offer.fee > 0) "Fee: ${offer.fee} gold, paid when you send the party. It comes back if you cancel the plan before End Day." else "No fee.",
        stages = offer.stages.indices.map { stageUi(state, offer, it) }, prep = def.prep, failure = def.failure,
        expiry = when (offer.expiresDay) { Int.MAX_VALUE -> "Always on the board"; state.day -> "Last day to send a party: today"; else -> "Last day to send a party: day ${offer.expiresDay}" },
        planned = g.planned?.offerId == offer.id,
        tag = "Opportunity".takeIf { def.archetype == MissionArchetype.OPPORTUNITY },
        modifier = content.guild!!.modifier(offer.modifierId)?.let { "${it.name}: ${it.description}" },
        danger = offer.stages.maxOf { it.danger },
        reward = stageUi(state, offer, 0).reward + if (offer.stages.size > 1) " · more if the first day is won" else "",
        modifierName = content.guild!!.modifier(offer.modifierId)?.name,
        days = offer.days, feeGold = offer.fee, specials = specialRewards(state, offer),
        stake = "If the cart is lost, the supplier brings ${config.guild.supplierShakenStock} fewer of every limited material each morning for ${config.guild.supplierDays} days. Leaving the job on the board costs nothing.".takeIf { offer.lapse == GuildContent.LAPSE_SUPPLIER },
    )
}

private fun bladeLine(state: GameState, heroId: HeroId): String =
    state.loanOf(heroId)?.let { "On loan: ${it.name}" } ?: state.equippedWeapon(heroId)?.let { "Own weapon: ${it.name}" } ?: "Unarmed"

internal fun GameEngine.memberStatus(state: GameState, g: GuildRunState, m: GuildMember): String? = when {
    m.status == MemberStatus.CAPTURED -> g.captives.firstOrNull { it.heroId == m.heroId }?.let { c ->
        (c.deadlineDay - state.day).let { left -> "Captured by the enemy: " + (if (left == 1) "1 day" else "$left days") + " left to rescue them" }
    } ?: "Captured by the enemy"
    m.status == MemberStatus.AWAY -> g.mission?.let { "Away until the morning of day ${it.returnDay}" } ?: "Away with the party"
    m.wound != null && m.wound!!.untilDay > state.day -> m.wound!!.let { w ->
        statWord(w.kind) + " until day ${w.untilDay}" + if (w.kind == WoundKind.SCARRED) ": enters fights with at most ${config.guild.scarredHealthPercent}% of their health" else ""
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
        whenLine = when { left <= 0 -> "Siege tonight, after today's trading"; left == 1 -> "Siege tomorrow night, day ${town.nextSiegeDay}"; else -> "Siege in $left days, on day ${town.nextSiegeDay}" },
        factionId = f.faction?.id?.takeIf { f.committed },
        besieger = if (f.committed) f.faction?.name ?: "Not yet known" else "Besieger not yet known",
        committed = f.committed,
        leaning = f.faction?.takeIf { !f.committed }?.let { "Most pressure today: ${it.name}." },
        weakTo = f.faction?.takeIf { f.committed }?.weakTo?.word(), resists = f.faction?.takeIf { f.committed }?.resists?.word(),
        field = enemies(f.plan),
        notes = listOfNotNull(
            "The charter warlord leads this siege.".takeIf { f.charter },
            "Their preparations are spoiled.".takeIf { f.sabotaged && f.spoiled == null },
            plannedAway?.let { if (left <= 0) "The party you planned leaves today and is not on the wall tonight." else "The party you planned is away on the siege night of day ${town.nextSiegeDay}." },
        ),
        watch = f.watch, forgeHealth = town.integrity,
        watchLine = watchLine(town.militia, town.armory, f.watch),
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
        prep = f.prep?.let { PrepUi(it.name, it.description, it.counters, it.sabotageBrief) },
        spoiled = f.spoiled?.let { "Your party spoiled their preparation: ${it.name}. It will not come." },
        nemesis = f.nemesis?.let { n -> "${n.name} marches with them" + (state.weapons[n.weaponId]?.let { ", carrying ${it.name}, a weapon of this forge." } ?: ".") },
    )
}

/**
 * Militia and the armory on the wall, as `SiegeFight.field` uses them: [watch] (both, times a trait) takes up to this much
 * enemy health on the way in, before the defenders fight. Militia is earned by contracts and falls by a point a day.
 */
internal fun GameEngine.watchLine(militia: Int, armory: Int, watch: Int): String =
    "Militia $militia and the armory $armory take up to ${(watch * config.guild.outerHealthPerPoint).roundToInt()} health from the enemy before the wall. " +
        "Militia falls by ${config.militiaDecayPerDay} a day. A contract that pays in militia adds to it."

private fun GameEngine.partyUi(state: GameState, m: MissionInstance): PartyUi {
    val who = m.party.mapNotNull { state.heroes[it]?.fullName }
    val at = m.outcome == null && m.stage == 1 && m.offer.optionalPush
    val deeper = m.offer.stages.getOrNull(1)
    return PartyUi(
        title = contractTitle(m.offer), who = names(who), returns = returnMorning(m.returnDay).replaceFirstChar { it.uppercase() } + ".",
        status = when (m.outcome) {
            MissionOutcome.WON -> "The quest is won. They are on the way home."
            MissionOutcome.RETREATED -> "They retreated and are on the way home."
            MissionOutcome.LOST -> "The party was beaten. Those who can are on the way home."
            null -> if (at) "Stage 1 is won. The party waits for your choice: go deeper or return home." else "Out since day ${m.departedDay}. Retreat rule: ${postureName(m.posture)}."
        },
        checkpoint = deeper?.takeIf { at }?.let { stage ->
            CheckpointUi(
                m.id, secured = "Already won and kept: " + rewardWords(state, m.secured, m.offer.factionId) + ".",
                // Either way the party is home tomorrow morning: the deeper stage is fought at End Day today (`Missions.advance`).
                deeper = "Go deeper: one more fight at End Day today. If it is won, the party also brings home: " + rewardWords(state, stage.reward, m.offer.factionId) + ". Either way, the party is home tomorrow morning.",
                danger = dangerWords(stage.danger), enemies = enemies(stage.enemies), chosen = m.choice,
                note = when (m.choice) {
                    CheckpointChoice.PUSH -> "You chose: go deeper. You can change this until End Day."
                    CheckpointChoice.RETURN -> "You chose: return home. You can change this until End Day."
                    null -> "If you choose nothing, the party returns home at End Day."
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
        // The warlord leads every siege from the charter day until beaten on one (`SiegeFight`, `Stories.charterSiege`).
        progress = secured?.let { "Charter secured on day ${it.day}." } ?: "To secure the charter, defeat the charter warlord. The warlord leads the first siege on or after day ${config.guild.charterDay}, and every siege after it until defeated.",
        secured = secured != null,
        retireBlocked = "The party must be home before you can retire the chapter.".takeIf { g.mission != null },
        ranks = if (secured == null) emptyList() else cat.ranks.filter { it.rank > 0 }.sortedBy { it.rank }.map { RankUi(it.rank, it.name, it.description, taken = it.rank <= g.rank, canTake = it.rank > g.rank) },
        law = g.law?.let { l -> cat.law(l.id)?.let { def ->
            // The law holds on the days from..until-1 (`Missions.setup`); it is announced a morning ahead.
            listOf(def.name, def.description, if (state.day < l.fromDay) "From day ${l.fromDay} through day ${l.untilDay - 1}." else "In force through day ${l.untilDay - 1}.")
        } }.orEmpty(),
        rival = g.rival?.let { r ->
            listOf("${r.name}, led by ${r.leaderName}", "They prefer ${r.wants.lowercase()} quests.", "On day ${r.nextMoveDay} they take a quest off the board.") +
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
        "The party at ${content.guild!!.route(m.offer.factionId)?.name ?: "the checkpoint"} returns home unless you choose to go deeper"
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
                when (r.outcome) { MissionOutcome.WON -> if (r.continues) "Won. The party is still out" else "Won"; MissionOutcome.RETREATED -> "Retreated"; MissionOutcome.LOST -> "Lost" },
                r.fight?.highlights?.firstOrNull()?.text, r.lines,
            )
        },
        plan = g.planned?.let { p ->
            g.offers.firstOrNull { it.id == p.offerId }?.let { o ->
                "Planned for today: ${names(p.heroIds.mapNotNull { state.heroes[it]?.name })} on ${cat.mission(o.defId).name}, retreat rule ${postureName(p.posture)}. The party leaves at End Day."
            }
        },
        boardNote = "The party is out. You can plan the next quest when it is home.".takeIf { g.mission != null },
        offers = g.offers.sortedBy { wallWork(it) }.map { offerUi(state, g, it) },
        rosterHead = "Roster · ${g.members.size} of ${config.guild.maxMembers}",
        roster = g.members.mapNotNull { m ->
            val h = state.heroes[m.heroId] ?: return@mapNotNull null
            val fighter = fighterView(state, h.id)
            MemberUi(
                h.id, Sprites.portrait(h, small = true), h.fullName, classLine(h), combat?.kitByClass?.get(h.classId)?.role.orEmpty(), traitLine(m.traitId),
                memberStatus(state, g, m), bladeLine(state, h.id), fighter?.let { "Health ${it.health}/${it.maxHealth}" } ?: Labels.health(h).replaceFirstChar { it.uppercase() },
                reserved = h.id in g.reserved, branchOpen = openBranches(state, h.id).isNotEmpty(),
            ).let { ui -> memberChip(state, g, m).let { (chip, tone) -> ui.copy(chip = chip, chipTone = tone) } }
        },
        candidates = g.candidates.mapNotNull { c ->
            val h = state.heroes[c.heroId] ?: return@mapNotNull null
            CandidateUi(
                h.id, Sprites.portrait(h, small = true), h.fullName, classLine(h), combat?.kitByClass?.get(h.classId)?.role.orEmpty(), traitLine(c.traitId),
                "Signing fee: ${c.fee} gold. Keeps ${c.sharePercent}% of the gold from quests they go on.", recruitBlocked(state, g, c.fee),
            )
        },
        candidatesNote = if (g.candidates.isEmpty()) "Nobody would sign today. The list is redrawn on the morning of day $refresh." else "The list is redrawn on the morning of day $refresh.",
        region = regionUi(state, g),
        // Every faction but the one already at the gates: its rise is how the next besieger is read coming.
        factions = state.factions.values.filter { it.id != wall.factionId }.sortedByDescending { it.pressure }.map { f ->
            content.faction(f.id).let { d -> FactionRowUi(f.id, d.name, f.pressure, Battle.describePressure(f.pressure).replaceFirstChar { it.uppercase() }, d.weakTo?.word()) }
        },
        endDay = guildEndDay(state, g, wall),
        today = guildToday(state, g, wall),
        upcoming = upcomingContracts(state, g),
        assign = assignRows(state, g, config.guild.defenders),
        steps = charterSteps(state, g),
        companyNeeds = g.members.any { openBranches(state, it.heroId).isNotEmpty() },
    )
}

/**
 * The Shop's and the Forge's matchup line in a guild run: the besieger once it is known, never the classic
 * defense-against-raid outlook (a guild siege is fought on the wall, not weighed).
 */
internal fun GuildUi.threat(classic: ThreatUi?): ThreatUi? = classic?.copy(
    // Who comes only once committed; the day and the way to the wall are the workshop header's.
    matchup = if (wall.committed) listOfNotNull(wall.besieger, wall.weakTo?.let { "Weak to $it" }, wall.resists?.let { "Resists $it" }).joinToString(" · ") else null,
    outlook = null,
    besieger = wall.besieger.takeIf { wall.committed },
)

// ---- the quest sheet (code: contract) ----

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
    /** What every retreat rule shares (`retreatNote`), under the choices. */
    val retreatNote: String = "",
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
            val why = memberUnavailable(state, h.id) ?: "is reserved for the wall".takeIf { h.id in g.reserved }
            PickUi(h.id, Sprites.portrait(h, small = true), h.fullName, classLine(h), bladeLine(state, h.id), party.indexOf(h.id).takeIf { it >= 0 }?.plus(1), why?.let { "${h.fullName} $it." })
        },
        partyMax = partyMax(state, offer.id),
        postures = Posture.entries.map { PostureUi(it, postureName(it), postureWords(it, config.guild.fight), it == posture) },
        retreatNote = retreatNote(config.guild.fight),
        together = setup?.let(::worksTogether).orEmpty(),
        watchOut = setup?.let(::watchOut).orEmpty(),
        atHome = (when (home) { 0 -> "Nobody fit stays at home"; 1 -> "1 fit member stays at home"; else -> "$home fit members stay at home" }) +
            if (missesSiege(state.day, offer.days, siegeDay)) " for the siege night of day $siegeDay." else ". The party is home before the siege on day $siegeDay.",
        returns = returnMorning(state.day + offer.days).replaceFirstChar { it.uppercase() } + ".",
        sendBlocked = when {
            g.mission != null -> "The party is still out."
            party.isEmpty() -> partyMax(state, offer.id).let { if (it == 1) "Pick one member." else "Pick one to $it members." }
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
            out += if (e.actions.any { it is Action.Give && it.stat == Stat.CHARGE }) "${healer.name}'s healing charges ${x.name}'s weapon (${e.name})." else "When ${healer.name} heals ${x.name}, ${x.name}'s ${e.name} triggers."
        }
        // A blade that can crack, and a rule that answers a crack.
        if (all.any { e -> e.actions.any { it is Action.Fracture } }) {
            val blade = x.weaponName ?: "${x.name}'s weapon"
            setup.partyEffects.filter { it.trigger.kind == EventKind.FRACTURED }.forEach { e -> out += if (e.id == "relic_${CombatContent.SALVAGE_BELL}") "When $blade cracks, the ${e.name} triggers." else "When $blade cracks, ${e.name} triggers." }
            party.filter { it.key != x.key }.forEach { y -> y.effects.filter { it.trigger.kind == EventKind.FRACTURED }.forEach { e -> out += "When $blade cracks, ${y.name}'s ${e.name} triggers." } }
        }
        // A Battlemage's echo, and a state another member's rule puts on a foe.
        all.filter { e -> e.actions.any { it is Action.Echo } }.forEach { echo ->
            val stats = echo.conditions.filterIsInstance<Condition.StatIn>().flatMap { it.stats }.toSet()
            for (y in party) {
                val stat = (y.effects + y.kit.passives).flatMap { it.actions }.filterIsInstance<Action.Give>().firstOrNull { it.stat in stats && it.target in foeAims }?.stat ?: continue
                val extra = echo.actions.filterIsInstance<Action.Echo>().first().extra
                out += if (y.key == x.key) "${x.name}'s ${echo.name} adds $extra more to the ${statWord(stat)} that ${x.name}'s own weapon puts on enemies."
                else "${x.name}'s ${echo.name} adds $extra more to the ${statWord(stat)} that ${y.name} puts on enemies."
            }
        }
        // Charge that is spent, and a rule that answers its spending.
        if (all.any { (it.cost as? Cost.Spend)?.stat == Stat.CHARGE }) {
            fun answers(e: com.tinyblacksmith.core.combat.EffectDef) = e.trigger.kind == EventKind.STAT_SPENT && e.trigger.stat == Stat.CHARGE && e.trigger.who in setOf(Who.ALLY_SOURCE, Who.ANY)
            setup.partyEffects.filter(::answers).forEach { e -> out += "When ${x.name} spends Charge, the ${e.name} triggers." }
            party.forEach { y -> y.effects.filter(::answers).forEach { e -> out += "When ${x.name} spends Charge, ${y.name}'s ${e.name} triggers." } }
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
        known.firstOrNull()?.takeIf { !guards }?.let { "Nobody in this party gives Guard. A known enemy move: $it" },
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
        share = "Keeps ${m.sharePercent}% of the gold from quests they go on. " + if (m.fee > 0) "Signed on day ${m.joinedDay} for ${m.fee} gold." else "With the guild since day ${m.joinedDay}.",
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
