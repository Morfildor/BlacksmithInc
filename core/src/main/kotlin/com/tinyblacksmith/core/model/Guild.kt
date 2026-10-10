package com.tinyblacksmith.core.model

import com.tinyblacksmith.core.combat.FightResult
import com.tinyblacksmith.core.combat.Objective
import com.tinyblacksmith.core.combat.Posture
import kotlinx.serialization.Serializable

/**
 * The player's own guild (docs/GUILD_EVOLUTION_PLAN.md, D2): everything a guild run adds to a run, in one aggregate.
 * Not the NPC [Guild] a famous hero founds in town. A run whose [GameState.guild] is null is a classic run.
 */

enum class MemberStatus { HOME, AWAY, CAPTURED }

/** EXHAUSTED and INJURED keep a member from a party and the wall until [Wound.untilDay]; SCARRED lets them serve with less health. */
enum class WoundKind { EXHAUSTED, INJURED, SCARRED }

/** [untilDay] is the first morning the wound is gone. [cause] is the line the roster shows. */
@Serializable
data class Wound(val kind: WoundKind, val untilDay: Int, val cause: String)

/** A tie between two members, made by something that really happened to both ([sinceDay], [reason]). */
@Serializable
data class Bond(val withHeroId: HeroId, val kind: String, val sinceDay: Int, val reason: String)

@Serializable
data class GuildMember(
    val heroId: HeroId,
    val joinedDay: Int,
    val fee: Int,
    /** Share of a won contract's gold that goes to this member's own purse. */
    val sharePercent: Int,
    /** The member's defining trait (`GuildTraitDef.id`), disclosed before hiring. */
    val traitId: String,
    val status: MemberStatus = MemberStatus.HOME,
    val wound: Wound? = null,
    val missions: Int = 0,
    val missionWins: Int = 0,
    val timesDowned: Int = 0,
    val bonds: List<Bond> = emptyList(),
    /** A deed-driven branch the smith chose for this member (`GuildContent.specialities`), or null. */
    val specialityId: String? = null,
    /** The deeds this member has been offered a branch for: each once. */
    val deedsOffered: Set<String> = emptySet(),
)

/** A resident who would sign today. Everything about the offer is stored; looking at it draws nothing. */
@Serializable
data class RecruitCandidate(val heroId: HeroId, val fee: Int, val sharePercent: Int, val traitId: String, val complication: String? = null)

enum class Danger { SAFE, DANGEROUS, LETHAL }

@Serializable
data class EnemySpec(val unitId: String, val percent: Int = 100)

/**
 * What a won stage brings. [suppression] lowers the faction's pressure; [sabotage] is what it does to the coming siege;
 * [weaponId] is a blade that comes back with the party; [captiveId] a member who does.
 */
@Serializable
data class Reward(
    val gold: Int = 0,
    val materials: Map<MaterialId, Int> = emptyMap(),
    val suppression: Int = 0,
    val integrity: Int = 0,
    val militia: Int = 0,
    val sabotage: Boolean = false,
    val relicOffer: Boolean = false,
    val weaponId: WeaponId? = null,
    val captiveId: HeroId? = null,
) {
    operator fun plus(o: Reward) = Reward(gold + o.gold, (materials.keys + o.materials.keys).associateWith { (materials[it] ?: 0) + (o.materials[it] ?: 0) }, suppression + o.suppression, integrity + o.integrity,
        militia + o.militia, sabotage || o.sabotage, relicOffer || o.relicOffer, weaponId ?: o.weaponId, captiveId ?: o.captiveId)
    val isEmpty: Boolean get() = this == Reward()
}

/** One fight of a mission as it was generated: who stands there, what winning means, what it brings. */
@Serializable
data class MissionStage(val enemies: List<EnemySpec>, val objective: Objective, val danger: Danger, val seed: Long, val reward: Reward,
    /** An extra party-side actor the objective protects (a cart, a captive): a unit ID, or null. */
    val protectUnitId: String? = null)

/**
 * A contract on the board. Generated once (GUILD stream) and stored. [stages] has one fight, or two: with [optionalPush]
 * the second is the smith's choice at the checkpoint, otherwise it is part of the job.
 */
@Serializable
data class MissionOffer(
    val id: String,
    val defId: String,
    val factionId: FactionId,
    val postedDay: Int,
    /** The last morning it can be taken. */
    val expiresDay: Int,
    val days: Int,
    val fee: Int,
    val stages: List<MissionStage>,
    val optionalPush: Boolean = false,
    /** Why this one matters to somebody: a real name, a real blade. */
    val reason: String? = null,
    val subjectHeroId: HeroId? = null,
    val subjectWeaponId: WeaponId? = null,
)

/** The party the smith has put together this morning; End Day sends it. Replaced whole by each `PlanDeployment`. */
@Serializable
data class PlannedDeployment(val offerId: String, val heroIds: List<HeroId>, val posture: Posture)

enum class CheckpointChoice { RETURN, PUSH }

enum class MissionOutcome { WON, RETREATED, LOST }

/**
 * A party on the road. Everything it left with is stored here ([gear], [relics]), so nothing the smith does at home
 * afterwards changes a fight that is already committed. [stage] is the next stage to resolve.
 */
@Serializable
data class MissionInstance(
    val id: String,
    val offer: MissionOffer,
    val party: List<HeroId>,
    val gear: Map<HeroId, WeaponId>,
    val posture: Posture,
    val relics: List<String>,
    val departedDay: Int,
    val returnDay: Int,
    val stage: Int = 0,
    val secured: Reward = Reward(),
    val choice: CheckpointChoice? = null,
    val outcome: MissionOutcome? = null,
)

/** What a party's day came to, for the evening's report and the next morning. Built from the fight's own record. */
@Serializable
data class MissionReport(
    val missionId: String,
    val defId: String,
    val title: String,
    val day: Int,
    val stage: Int,
    val party: List<HeroId>,
    val outcome: MissionOutcome,
    /** True when the party is still out after this stage (a checkpoint). */
    val continues: Boolean,
    /** Null for work without a fight. */
    val fight: FightResult? = null,
    val gained: Reward = Reward(),
    val lines: List<String> = emptyList(),
)

/** A member in enemy hands. Past [deadlineDay] nobody comes back. [weaponId] is the loan that was taken with them. */
@Serializable
data class Captive(val heroId: HeroId, val factionId: FactionId, val capturedDay: Int, val deadlineDay: Int, val weaponId: WeaponId? = null, val missionDefId: String = "")

/** A blade of the forge in an enemy's hands, and whose. One at a time. */
@Serializable
data class Nemesis(val name: String, val factionId: FactionId, val weaponId: WeaponId, val sinceDay: Int, val unitId: String, val seen: Int = 0)

/** The other guild in the region: what it wants and what it last took. */
@Serializable
data class RivalGuild(val name: String, val leaderName: String, val wants: String, val relation: Int = 0, val taken: List<String> = emptyList(), val nextMoveDay: Int = 0)

/** A visible rule on everybody for a few days. */
@Serializable
data class WorldLaw(val id: String, val fromDay: Int, val untilDay: Int)

/** Something the run has earned for good ([id]), when, and whether its reward has been claimed. Claimed once. */
@Serializable
data class RunMilestoneClaim(val id: String, val day: Int, val claimed: Boolean = false, val points: Int = 0)

/** The siege of one evening in a guild run, for the report. */
@Serializable
data class SiegeReport(val day: Int, val defenders: List<HeroId>, val fight: FightResult?, val verdict: SiegeVerdict, val forgeDamage: Int, val outerLine: String = "")

enum class SiegeVerdict { HELD, HELD_AT_A_COST, BREACHED }

@Serializable
data class GuildRunState(
    val charterId: String,
    val members: List<GuildMember> = emptyList(),
    val candidates: List<RecruitCandidate> = emptyList(),
    /** The day the candidates were last drawn. */
    val candidatesDay: Int = 0,
    val offers: List<MissionOffer> = emptyList(),
    val planned: PlannedDeployment? = null,
    val mission: MissionInstance? = null,
    /** Members the smith keeps for the wall, in the order they stand. */
    val reserved: List<HeroId> = emptyList(),
    val captives: List<Captive> = emptyList(),
    /** Today's party report (kept until the next End Day writes another) and today's siege. */
    val lastMission: MissionReport? = null,
    val lastSiege: SiegeReport? = null,
    val nextMissionSerial: Int = 1,
    val milestones: List<RunMilestoneClaim> = emptyList(),
    val nemesis: Nemesis? = null,
    val rival: RivalGuild? = null,
    val law: WorldLaw? = null,
    /** Optional difficulty the smith accepted after a charter was secured: 0 = none. */
    val rank: Int = 0,
    /** Chains the party has shown in a real fight, by effect pair, with who and what did it (spec 7.5 combo notes). */
    val comboNotes: List<String> = emptyList(),
    /** Contracts completed by archetype, for what the board offers next. */
    val completed: Map<String, Int> = emptyMap(),
    /** The run ended by the smith's own choice after a milestone, not by the forge falling. */
    val retired: Boolean = false,
) {
    fun member(id: HeroId): GuildMember? = members.firstOrNull { it.heroId == id }
    fun isMember(id: HeroId): Boolean = members.any { it.heroId == id }
    fun milestone(id: String): RunMilestoneClaim? = milestones.firstOrNull { it.id == id }
}
