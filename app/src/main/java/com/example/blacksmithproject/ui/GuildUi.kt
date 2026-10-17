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
import com.tinyblacksmith.core.shopday.Advice
import com.tinyblacksmith.core.shopday.Lead
import com.tinyblacksmith.core.shopday.Lines
import com.tinyblacksmith.core.model.WoundKind
import com.tinyblacksmith.core.text.Terms
import com.tinyblacksmith.core.content.Depth
import com.tinyblacksmith.core.guild.Stories

/*
 * The Guild destination as facts and finished sentences (a guild run only: `GameState.guild != null`). Everything here
 * reads the save and the engine's own reads (`wallForecast`, `memberUnavailable`, `fighterView`, `missionSetup`); nothing
 * is simulated and nothing is decided. Numbers of rules come from the definitions' own descriptions.
 */

/**
 * A unit on a field: how many of it, its [health] as the field scales it ("12", or "12 to 14" when the units differ),
 * and its inspect card ([tip], `enemyTip`), which holds what is known of what it does (its moves, its rules).
 */
@Immutable data class EnemyUi(val name: String, val count: Int, val unitId: String = "", val health: String = "", val tip: TipUi? = null) {
    val label: String get() = if (count > 1) "$count × $name" else name
}

/**
 * One of the three places on the wall. [member]: one of the guild (a resident of the town can stand there too).
 * [weaponArt] is the weapon in hand (decorative, [blade] says it), [chip] its readiness by the wall's own checks
 * (`Advice.siegeReadiness`: unarmed, a worn weapon, or armed), never a promise.
 */
@Immutable data class DefenderUi(
    val heroId: HeroId, val portrait: Int, val name: String, val sub: String, val blade: String, val member: Boolean, val reserved: Boolean,
    val weaponArt: Int? = null, val chip: String = "", val chipTone: ChipTone = ChipTone.OK,
)

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
    val readiness: Lead? = null, val readinessReason: String? = null,
    /** How the wall's fight is run: the Balanced retreat rule, what a retreat there means, the round limit (`SiegeFight.resolve`). */
    val rulesLine: String = "",
)

/** The choice at the door of a deeper stage. [chosen] is what the smith has already sent word of today. */
@Immutable data class CheckpointUi(val instanceId: String, val secured: String, val deeper: String, val danger: String, val enemies: List<EnemyUi>, val chosen: CheckpointChoice?, val note: String)

@Immutable data class PartyUi(val title: String, val who: String, val returns: String, val status: String?, val checkpoint: CheckpointUi?, val returnDay: Int = 0)

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
    /** Worst stage danger and the stored rewards of each stage, including optional stages. */
    val danger: Danger = Danger.SAFE, val reward: String = "",
    /** The modifier's name alone, for the board; [modifier] has it whole. */
    val modifierName: String? = null,
    /** How many days it takes and what it costs, as numbers for the board's chips; and what it brings besides gold and stock. */
    val days: Int = 1, val feeGold: Int = 0, val specials: List<String> = emptyList(),
    val returnDay: Int = 0,
    val timingNote: String = "",
    val purpose: String = "",
    val optionalPush: Boolean = false,
    /** Every stage's reward in a few words for the board: the gold of all stages ("up to" when there are several) and how many other rewards come with it. */
    val rewardSummary: String = "",
    /** The last day a party can be sent, in a word ("today", "day 9"); null for work that is always on the board. */
    val lastDay: String? = null,
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
    /** The weapon in hand as a small sprite (decorative: [blade] says it); null when unarmed. */
    val weaponArt: Int? = null,
    /** [name] without a byname of residence, and that byname for a quieter second line (`nameParts`). */
    val shortName: String = name, val byname: String? = null,
) {
    /** The roster's one line under the name: class, health and the wall; role and trait are on the hero's sheet. */
    val line: String get() = "$sub · $health" + if (reserved) " · reserved for the wall" else ""
}

/** [blocked]: why Recruit cannot be tapped today, in a sentence; null when it can. */
@Immutable
data class CandidateUi(
    val heroId: HeroId, val portrait: Int, val name: String, val sub: String, val role: String, val trait: String, val terms: String, val blocked: String?,
    /** The signing fee and the share of quest gold they keep, as numbers for the row's facts; [terms] says both in a sentence. */
    val feeGold: Int = 0, val sharePercent: Int = 0,
    /** Their Hook in one sentence, shown before the fee is paid (an oath above all); null without one. */
    val complication: String? = null,
    /** [name] without a byname of residence, and that byname for a quieter second line (`nameParts`). */
    val shortName: String = name, val byname: String? = null,
) {
    /** Class and the trait's name in one line; what the trait does is on the hero's sheet. */
    val line: String get() = "$sub · ${trait.substringBefore(':')}"
}

/** A challenge tier (code: rank): its modifiers in [description], and the legacy points it adds if held when the era ends. */
@Immutable data class RankUi(val rank: Int, val name: String, val description: String, val taken: Boolean, val canTake: Boolean, val legacyPoints: Int = 0)

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
    val rosterNote: String = homeLine(com.tinyblacksmith.core.config.BalanceConfig.DEFAULT.heroRestHeal),
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

/** What members at home do each day on their own (`GuildOps.homeDay`): no daily command, owner decision J. [heal] is `BalanceConfig.heroRestHeal`. */
internal fun homeLine(heal: Int): String = "At home, hurt members rest and heal $heal health, and fit members train."

/**
 * The whole of what a member at home does without a command (`GuildOps.homeDay`, and in a living era `Agency.counter`), for
 * the member's sheet. The roster says the short [homeLine].
 */
internal fun homeRules(heal: Int, living: Boolean): String =
    "Each day at home, a hurt member rests and heals $heal health and a fit member trains. A member who stood on the wall on a siege night does neither that day." +
        if (living) " One member a day who needs a weapon may come to your counter and buy one with their own gold." else ""

/** The danger alone, for the board's line: the sheet says what it means. */
internal fun dangerWord(d: Danger): String = Terms.danger(d)

internal fun dangerWords(d: Danger): String = when (d) {
    Danger.SAFE -> "Safe: nobody dies or is captured."
    Danger.DANGEROUS -> "Dangerous: if the whole party goes down, 1 member is captured."
    Danger.LETHAL -> "Lethal: if the whole party goes down, 1 member dies and another is captured."
}

internal fun postureName(p: Posture): String = Terms.posture(p)

/** When a party with this retreat rule (code: posture) retreats, with the numbers of the rules themselves (`Fight.retreat`). */
internal fun postureWords(p: Posture, rules: FightRules): String = when (p) {
    Posture.CAUTIOUS -> "Retreats when any member is down, or when the party's total health falls below ${rules.cautiousHealthPercent}% of what it started the fight with."
    Posture.BALANCED -> "Retreats when ${rules.balancedDowned} members are down, or when only 1 member of a larger party is still standing and is below ${rules.lastStandPercent}% of their maximum health."
    Posture.RECKLESS -> "Never retreats. The party fights until it wins or every member is down."
}

/** What every retreat rule shares: when it is checked, what a retreat costs, and the round limit (`Fight.play`, `Missions.advance`). */
internal fun GameEngine.retreatNote(rules: FightRules): String {
    // The two exceptions by their catalog names and numbers (`GuildTraitDef.retreatGoldPercent`, `CombatContent.COWARDS_MEDAL`).
    val keepers = listOfNotNull(
        content.guild?.traits?.firstOrNull { it.retreatGoldPercent > 0 }?.let { "With a ${it.name} member the party keeps ${it.retreatGoldPercent}% of the stage's gold" },
        content.relic(CombatContent.COWARDS_MEDAL)?.let { "with the ${it.name} ${config.guild.medalRetreatGoldPercent}%" },
    )
    return "The rule is checked at the end of each round. After a retreat nobody dies or is captured, but the stage pays no reward, and members who went down are still injured." +
        (if (keepers.isEmpty()) "" else " ${keepers.joinToString(", and ").replaceFirstChar { it.uppercase() }}.") +
        " If neither side has won after ${rules.maxRounds} rounds, the party retreats."
}

internal fun returnMorning(day: Int): String = "home on the morning of day $day"

/** A party that leaves on [day] for [days] is away on the nights of [day] .. [day] + [days] - 1. */
internal fun missesSiege(day: Int, days: Int, siegeDay: Int): Boolean = siegeDay in day until day + days

private fun names(list: List<String>) = list.joinToString(", ")

/** "Elf Duelist level 3": people, class and level, as every guild row says them. */
internal fun GameEngine.classLine(h: Hero) = "${raceClass(h)} ${levelWords(h.level)}"

/** "level 3", never broken across two lines. */
internal fun levelWords(level: Int) = "level $level"

/** Each kind of unit on a field once, with its count, its health as the field scales it and its card. [note]: what changes the numbers before the fight. */
private fun GameEngine.enemies(specs: List<EnemySpec>, note: String? = null): List<EnemyUi> {
    val combat = content.combat ?: return emptyList()
    return specs.groupBy { it.unitId }.map { (id, all) ->
        combat.unit(id).let { u ->
            val percents = all.map { it.percent }
            EnemyUi(u.name, all.size, id, unitHealth(u, percents), enemyTip(u, all.size, percents, config, note, tag = "guild_enemy_tip"))
        }
    }
}

private fun GameEngine.rewardWords(state: GameState, r: Reward, factionId: FactionId?, compact: Boolean = false): String = listOfNotNull(
    "${r.gold} gold".takeIf { r.gold > 0 },
    r.materials.entries.joinToString(", ") { "${it.value} ${content.material(it.key).name}" }.ifEmpty { null },
    factionId?.takeIf { r.suppression > 0 }?.let { if (compact) "-${r.suppression} ${content.faction(it).name} pressure" else "lowers the pressure of ${content.faction(it).name}" },
    (if (compact) "up to +${r.integrity} forge health" else "restores up to ${r.integrity} forge health").takeIf { r.integrity > 0 },
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
        fee = if (offer.fee > 0) "Fee: ${offer.fee} gold, paid when you plan departure. It comes back if you cancel the plan before End Day." else "No fee.",
        stages = offer.stages.indices.map { stageUi(state, offer, it) }, prep = def.prep, failure = def.failure,
        expiry = when (offer.expiresDay) { Int.MAX_VALUE -> "Always on the board"; state.day -> "Last day to send a party: today"; else -> "Last day to send a party: day ${offer.expiresDay}" },
        lastDay = when (offer.expiresDay) { Int.MAX_VALUE -> null; state.day -> "today"; else -> "day ${offer.expiresDay}" },
        planned = g.planned?.offerId == offer.id,
        tag = "Opportunity".takeIf { def.archetype == MissionArchetype.OPPORTUNITY },
        modifier = content.guild!!.modifier(offer.modifierId)?.let { "${it.name}: ${it.description}" },
        danger = offer.stages.maxOf { it.danger },
        reward = offer.stages.indices.joinToString("\n") { i ->
            (if (offer.stages.size == 1) "" else if (i > 0 && offer.optionalPush) "Optional stage ${i + 1}: " else "Stage ${i + 1}: ") + rewardWords(state, offer.stages[i].reward, offer.factionId, compact = true)
        },
        modifierName = content.guild!!.modifier(offer.modifierId)?.name,
        days = offer.days, feeGold = offer.fee, specials = specialRewards(state, offer),
        returnDay = state.day + offer.days,
        purpose = objectiveWords(offer.stages.first()),
        optionalPush = offer.optionalPush,
        rewardSummary = rewardSummary(state, offer),
        timingNote = (if (offer.optionalPush) "The return day includes the optional deeper stage. Returning at the checkpoint skips that fight, but the party still comes home on the same morning. " else
            if (offer.stages.size > 1) "The return day includes all stages. A defeat or a retreat brings the party home earlier. " else "") +
            "A member who comes back injured cannot defend the wall for ${config.guild.injuredDays} days, an exhausted one for ${config.guild.exhaustedDays} ${if (config.guild.exhaustedDays == 1) "day" else "days"}.",
        stake = "If the cart is lost, the supplier brings ${config.guild.supplierShakenStock} fewer of every limited material each morning for ${config.guild.supplierDays} days. Leaving this quest on the board costs nothing.".takeIf { offer.lapse == GuildContent.LAPSE_SUPPLIER },
    )
}

/**
 * The board's reward in a few words: the gold of every stage together ("up to" when a later stage may not be fought),
 * else the first notable reward, and how many other rewards come with it. The stages' own lines say each in full.
 */
private fun GameEngine.rewardSummary(state: GameState, offer: MissionOffer): String {
    val gold = offer.stages.sumOf { it.reward.gold }
    val others = offer.stages.flatMap { st ->
        val r = st.reward
        r.materials.entries.map { "${it.value} ${content.material(it.key).name}" } + listOfNotNull(
            offer.factionId?.takeIf { r.suppression > 0 }?.let { "-${r.suppression} ${content.faction(it).name} pressure" },
            "up to +${r.integrity} forge health".takeIf { r.integrity > 0 },
            "+${r.militia} militia".takeIf { r.militia > 0 },
        )
    }.distinct()
    val notable = specialRewards(state, offer) + others
    val lead = if (gold > 0) "$gold gold" else notable.firstOrNull() ?: return "Nothing of its own"
    val more = notable.size - if (gold > 0) 0 else 1
    return (if (gold > 0 && offer.stages.size > 1) "up to " else "") + lead + if (more > 0) " and $more more" else ""
}

/**
 * The board row's one line of facts, the summary first: the reward, the danger (a warning unless safe), how long the party
 * is away and the morning it is back, the fee, and the last day to send a party (a warning when that is today). What is
 * different about the posting, each stage and what to bring are on the quest's card and sheet.
 */
internal fun offerFacts(o: OfferUi): List<Fact> = listOfNotNull(
    Fact(o.rewardSummary, "Reward"),
    Fact(dangerWord(o.danger), tone = if (o.danger == Danger.SAFE) FactTone.PLAIN else FactTone.WARN),
    Fact((if (o.days == 1) "1 day" else "${o.days} days") + ", back day ${o.returnDay}", "Away"),
    if (o.feeGold > 0) Fact("${o.feeGold} gold", "Fee") else Fact("No fee"),
    o.lastDay?.let { Fact(it, "Send by", if (it == "today") FactTone.WARN else FactTone.PLAIN) },
)

private fun bladeLine(state: GameState, heroId: HeroId): String =
    state.loanOf(heroId)?.let { "On loan: ${it.name}" } ?: state.equippedWeapon(heroId)?.let { "Own weapon: ${it.name}" } ?: "Unarmed"

internal fun GameEngine.memberStatus(state: GameState, g: GuildRunState, m: GuildMember): String? = when {
    m.status == MemberStatus.CAPTURED -> g.captives.firstOrNull { it.heroId == m.heroId }?.let { c ->
        (c.deadlineDay - state.day).let { left -> "Captured by the enemy: " + (if (left == 1) "1 day" else "$left days") + " left to rescue them" }
    } ?: "Captured by the enemy"
    m.status == MemberStatus.AWAY -> g.mission?.let { "Away until the morning of day ${it.returnDay}" } ?: "Away with the party"
    m.wound != null && m.wound!!.untilDay > state.day -> m.wound!!.let { w ->
        Terms.wound(w.kind) + " until day ${w.untilDay}" + if (w.kind == WoundKind.SCARRED) ": enters fights with at most ${config.guild.scarredHealthPercent}% of their health" else ""
    }
    else -> null
}


private fun GameEngine.traitLine(traitId: String): String = content.guild!!.trait(traitId)?.let { "${it.name}: ${it.description}" } ?: "No trait of note"

private fun GameEngine.wallUi(state: GameState, g: GuildRunState): WallUi {
    val f = wallForecast(state)!!
    val readiness = Advice.siegeReadiness(state, content, config)
    val town = state.town
    val left = town.nextSiegeDay - state.day
    val plannedAway = g.planned?.let { p -> g.offers.firstOrNull { it.id == p.offerId } }?.takeIf { missesSiege(state.day, it.days, town.nextSiegeDay) }
    val present = g.members.filter { memberUnavailable(state, it.heroId) == null }
    return WallUi(
        siegeDay = town.nextSiegeDay, daysLeft = left,
        whenLine = when { left <= 0 -> "Siege tonight, after today's trading"; left == 1 -> "Siege tomorrow night, day ${town.nextSiegeDay}"; else -> "Siege in $left days, on day ${town.nextSiegeDay}" },
        factionId = f.faction?.id?.takeIf { f.committed },
        besieger = if (f.committed) f.faction?.name ?: "Not yet known" else "Attackers not yet known",
        committed = f.committed,
        leaning = f.faction?.takeIf { !f.committed }?.let { "Most pressure today: ${it.name}." },
        weakTo = f.faction?.takeIf { f.committed }?.weakTo?.word(), resists = f.faction?.takeIf { f.committed }?.resists?.word(),
        field = if (f.committed) enemies(f.plan, "Before the wall, the militia and the armory take some of this health.") else emptyList(),
        notes = listOfNotNull(
            "The charter warlord leads this siege.".takeIf { f.charter },
            "Their preparations are spoiled.".takeIf { f.sabotaged && f.spoiled == null },
            plannedAway?.let { if (left <= 0) "The party you planned leaves today and is not on the wall tonight." else "The party you planned is away on the siege night of day ${town.nextSiegeDay}." },
        ),
        watch = f.watch, forgeHealth = town.integrity,
        readiness = readiness, readinessReason = readiness?.let { Lines.lead(it, state, content, config).reason },
        watchLine = watchLine(town.militia, town.armory, f.watch),
        forgeHealthMax = maxOf(town.integrity, config.startingForgeIntegrity + upgradeTotal(state.legacy, UpgradeEffect.STARTING_INTEGRITY)),
        defenders = f.defenders.map { (h, w) ->
            // The wall's own readiness checks, per place (`Advice.siegeReadiness`): no weapon, a worn one, or armed.
            val (chip, tone) = when {
                w == null -> "Unarmed" to ChipTone.OUT
                w.condition < config.wornConditionThreshold -> "Worn weapon" to ChipTone.HURT
                else -> "Armed" to ChipTone.OK
            }
            DefenderUi(h.id, Sprites.portrait(h, small = true), h.fullName, classLine(h), w?.name ?: "Unarmed", g.isMember(h.id), h.id in g.reserved, w?.let(Sprites::weapon), chip, tone)
        },
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
        prep = f.prep?.takeIf { f.committed }?.let { PrepUi(it.name, it.description, it.counters, it.sabotageBrief) },
        spoiled = f.spoiled?.let { "Your party spoiled their preparation: ${it.name}. It will not be used." },
        rulesLine = wallRules(state),
        nemesis = f.nemesis?.let { n -> "${n.name} marches with them" + (state.weapons[n.weaponId]?.let { ", carrying ${it.name}, a weapon of this forge." } ?: ".") },
    )
}

/**
 * Militia and the armory on the wall, as `SiegeFight.field` uses them: [watch] (both, times a trait) takes up to this much
 * enemy health on the way in, before the defenders fight. Militia is earned by contracts and falls by a point a day.
 */
internal fun GameEngine.watchLine(militia: Int, armory: Int, watch: Int): String =
    "Militia $militia and the armory $armory take up to ${(watch * config.guild.outerHealthPerPoint).roundToInt()} health from the enemy before the wall. " +
        "Militia falls by ${config.militiaDecayPerDay} a day. A quest that pays in militia adds to it."

/**
 * How the wall's fight is run (`SiegeFight.resolve`): always the Balanced retreat rule, a retreat there lets the attackers
 * through, and the round limit, longer in a Long Assault siege. The numbers are the fight rules' own.
 */
internal fun GameEngine.wallRules(state: GameState): String {
    val rules = config.guild.fight
    val long = state.siege?.takeIf { it.siegeDay == state.town.nextSiegeDay }?.traitId == Depth.LONG_ASSAULT
    val rounds = if (long) rules.maxRounds + rules.maxRounds / 2 else rules.maxRounds
    return "On the wall the defenders always fight under the Balanced retreat rule. They retreat when ${rules.balancedDowned} defenders are down, or when only 1 of 2 or more defenders is still standing and is below ${rules.lastStandPercent}% of their maximum health. " +
        "A retreat on the wall lets the attackers through. If neither side has won after $rounds rounds" + (if (long) " (this siege is a Long Assault)" else "") + ", the town holds at a cost."
}

private fun GameEngine.partyUi(state: GameState, m: MissionInstance): PartyUi {
    val who = m.party.mapNotNull { state.heroes[it]?.fullName }
    val at = m.outcome == null && m.stage == 1 && m.offer.optionalPush
    val deeper = m.offer.stages.getOrNull(1)
    return PartyUi(
        title = contractTitle(m.offer), who = names(who), returns = returnMorning(m.returnDay).replaceFirstChar { it.uppercase() } + ".", returnDay = m.returnDay,
        status = when (m.outcome) {
            MissionOutcome.WON -> "The quest is won. They are on the way home."
            MissionOutcome.RETREATED -> "They retreated and are on the way home."
            MissionOutcome.LOST -> "The party was defeated. Those who can are on the way home."
            null -> if (at) "Stage 1 is won. The party is waiting at the checkpoint for your choice: go deeper or return." else "Out since day ${m.departedDay}. Retreat rule: ${postureName(m.posture)}."
        },
        checkpoint = deeper?.takeIf { at }?.let { stage ->
            CheckpointUi(
                m.id, secured = "Already won and kept: " + rewardWords(state, m.secured, m.offer.factionId) + ".",
                // Either way the party is home tomorrow morning: the deeper stage is fought at End Day today (`Missions.advance`).
                deeper = "Go deeper: one more fight at End Day today. If it is won, the party also brings home: " + rewardWords(state, stage.reward, m.offer.factionId) + ". Either way, the party is home tomorrow morning.",
                danger = dangerWords(stage.danger), enemies = enemies(stage.enemies), chosen = m.choice,
                note = when (m.choice) {
                    CheckpointChoice.PUSH -> "You chose: go deeper. You can change this until End Day."
                    CheckpointChoice.RETURN -> "You chose: return. You can change this until End Day."
                    null -> "If you choose nothing, the party returns at End Day with the rewards already won."
                },
            )
        },
    )
}

/** Why [c] cannot be recruited today; the engine checks the roster before the purse, and so does this. */
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
        progress = secured?.let { "Charter secured on day ${it.day}. It adds ${config.guild.charterLegacyPoints} legacy points when the era ends." } ?: "To secure the charter, defeat the charter warlord. The warlord leads the first siege on or after day ${config.guild.charterDay}, and every siege after it until defeated. A secured charter adds ${config.guild.charterLegacyPoints} legacy points when the era ends.",
        secured = secured != null,
        retireBlocked = "The party must be home before you can retire the guild.".takeIf { g.mission != null },
        ranks = if (secured == null) emptyList() else cat.ranks.filter { it.rank > 0 }.sortedBy { it.rank }.map { RankUi(it.rank, it.name, it.description, taken = it.rank <= g.rank, canTake = it.rank > g.rank, legacyPoints = it.legacyPoints) },
        law = g.law?.let { l -> cat.law(l.id)?.let { def ->
            // The law holds on the days from..until-1 (`Missions.setup`); it is announced a morning ahead.
            listOf(def.name, def.description, if (state.day < l.fromDay) "From day ${l.fromDay} through day ${l.untilDay - 1}." else "In force through day ${l.untilDay - 1}.")
        } }.orEmpty(),
        rival = g.rival?.let { r ->
            listOf("${r.name}, led by ${r.leaderName}", "They prefer ${Terms.archetype(r.wants)} quests.", "On day ${r.nextMoveDay} they take a quest off the board.") +
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
    // One sentence a row in "What End Day does": the members kept for the wall, then who fills the other places for now.
    else (if (wall.committed) "Siege tonight: ${wall.besieger}. " else "Siege tonight. ") +
        "Reserved for the wall: ${names(wall.defenders.filter { it.reserved }.map { it.name }).ifEmpty { "none" }}. " +
        "Guild members filling in for now: ${names(wall.defenders.filter { it.member && !it.reserved }.map { it.name }).ifEmpty { "none" }}. " +
        "Townsfolk filling in for now: ${names(wall.defenders.filter { !it.member }.map { it.name }).ifEmpty { "none" }}. " +
        "Who fills in can still change during End Day.",
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
                when (r.outcome) { MissionOutcome.WON -> if (r.continues) "Won. The party is still out" else "Won"; MissionOutcome.RETREATED -> "Retreated"; MissionOutcome.LOST -> "Defeated" },
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
        rosterNote = homeLine(config.heroRestHeal),
        roster = g.members.mapNotNull { m ->
            val h = state.heroes[m.heroId] ?: return@mapNotNull null
            val fighter = fighterView(state, h.id)
            MemberUi(
                h.id, Sprites.portrait(h, small = true), h.fullName, classLine(h), combat?.kitByClass?.get(h.classId)?.role.orEmpty(), traitLine(m.traitId),
                memberStatus(state, g, m), bladeLine(state, h.id), fighter?.let { "Health ${it.health}/${it.maxHealth}" } ?: Labels.health(h).replaceFirstChar { it.uppercase() },
                reserved = h.id in g.reserved, branchOpen = openBranches(state, h.id).isNotEmpty(),
                weaponArt = (state.loanOf(h.id) ?: state.equippedWeapon(h.id))?.let(Sprites::weapon),
                shortName = nameParts(h).first, byname = nameParts(h).second,
            ).let { ui ->
                // One chip: a speciality to choose stands in for "Fit", never for a wound, an absence or the wall.
                memberChip(state, g, m).let { (chip, tone) -> if (ui.branchOpen && tone == ChipTone.OK) ui.copy(chip = "Speciality to choose", chipTone = ChipTone.KEPT) else ui.copy(chip = chip, chipTone = tone) }
            }
        },
        candidates = g.candidates.mapNotNull { c ->
            val h = state.heroes[c.heroId] ?: return@mapNotNull null
            CandidateUi(
                h.id, Sprites.portrait(h, small = true), h.fullName, classLine(h), combat?.kitByClass?.get(h.classId)?.role.orEmpty(), traitLine(c.traitId),
                "Recruitment fee: ${c.fee} gold. Keeps ${c.sharePercent}% of the gold from quests they go on.", recruitBlocked(state, g, c.fee),
                feeGold = c.fee, sharePercent = c.sharePercent, complication = hookSentence(state, h),
                shortName = nameParts(h).first, byname = nameParts(h).second,
            )
        },
        candidatesNote = if (g.candidates.isEmpty()) "Nobody is for hire today. The list is redrawn on the morning of day $refresh." else "The list is redrawn on the morning of day $refresh.",
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

@Immutable data class PickUi(val heroId: HeroId, val portrait: Int, val name: String, val sub: String, val blade: String, /** 1-based place in the order the party acts; null when not picked. */ val order: Int?, val blocked: String?, val health: String = "")

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

/** The party sheet's one line under "Selected party": the limit and the order, or, with nobody picked, that staying home is a plan too (said only here). */
internal fun partyIntro(partyMax: Int, empty: Boolean): String =
    (if (partyMax == 1) "One member." else "Up to $partyMax members.") + if (empty) " Nobody selected: keeping members home is a valid plan." else " They act in this order."

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
    val party = picked
    val valid = party.all { pickable(state, it) } && party.distinct().size == party.size && party.size <= partyMax(state, offerId)
    val setup = if (party.isEmpty() || !valid) null else missionSetup(state, offerId, party, posture)
    val siegeDay = state.town.nextSiegeDay
    val home = g.members.count { it.heroId !in party && memberUnavailable(state, it.heroId) == null }
    // A plan made earlier today gives its fee back before this one's is taken (`Missions.plan`).
    val purse = state.gold + (g.planned?.let { p -> g.offers.firstOrNull { it.id == p.offerId }?.fee } ?: 0)
    return ContractUi(
        offer = offerUi(state, g, offer),
        picks = (g.members.map { it.heroId } + picked).distinct().map { id ->
            val h = state.heroes[id]
            val name = h?.fullName ?: "Missing member (${id.value})"
            val why = if (!g.isMember(id)) "is no longer a guild member" else memberUnavailable(state, id) ?: "is reserved for the wall".takeIf { id in g.reserved }
            PickUi(
                id, h?.let { Sprites.portrait(it, small = true) } ?: 0, name, h?.let { classLine(it) }.orEmpty(), bladeLine(state, id), party.indexOf(id).takeIf { it >= 0 }?.plus(1), why?.let { "$name $it." },
                health = h?.let { hero -> fighterView(state, id)?.let { "Health ${it.health}/${it.maxHealth}" } ?: Labels.health(hero).replaceFirstChar { it.uppercase() } }.orEmpty(),
            )
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
            offer.expiresDay < state.day -> "This quest has left the board. Close this draft and choose another."
            !valid -> "Some selected members cannot go, or the party exceeds its limit. Review the selected party."
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
                out += if (y.key == x.key) "${x.name}'s ${echo.name} adds $extra more to the ${Terms.stat(stat)} that ${x.name}'s own weapon puts on enemies."
                else "${x.name}'s ${echo.name} adds $extra more to the ${Terms.stat(stat)} that ${y.name} puts on enemies."
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
        known.firstOrNull()?.takeIf { !guards }?.let { "Nobody in this party gives Armor. A known enemy ability: $it" },
    ) + party.filter { it.weaponName == null }.map { "${it.name} goes unarmed." }
}

// ---- a member on the hero sheet ----

/** A speciality the member may choose; [opened] says which of their deeds opened it. */
@Immutable data class BranchUi(val id: String, val name: String, val description: String, val opened: String = "")
@Immutable data class LoanableUi(val weaponId: WeaponId, val name: String, val summary: String)

/**
 * What the hero sheet adds for a member of the guild. [numbers] are the fighter's own (`fighterView`), [rules] every rule
 * they bring with the blade in hand. [dismissBlocked] / [loanBlocked]: why that action is not offered today.
 * V3, read like the member's card: [gains] (trait, speciality) and [costs] (their share) as + / − lines, [abilities] what
 * the class does and every rule of the class, the weapon and anything else they carry (each with where it comes from),
 * and [keywords] the statuses those name.
 */
@Immutable
data class MemberDetailUi(
    val heroId: HeroId, val name: String,
    val trait: String, val speciality: String?, val bonds: List<String>, val share: String,
    val numbers: List<Pair<String, String>>, val rules: List<Pair<String, String>>,
    val branches: List<BranchUi>,
    val loan: LoanableUi?, val ownBlade: String?, val loanable: List<LoanableUi>, val loanBlocked: String?,
    val dismissBlocked: String?,
    val gains: List<TipLine> = emptyList(), val costs: List<TipLine> = emptyList(),
    val abilities: List<TipLine> = emptyList(), val keywords: List<TipKeyword> = emptyList(),
    /** When and for what they signed, without the share (that is a cost line). */
    val since: String = "",
    /** Laid up, away or captured, with until when (`memberStatus`); null for a member at home and well. */
    val status: String? = null,
    /** How a speciality opens, with the member's progress; null once one is chosen (`Stories.openBranches`). */
    val unlock: String? = null,
    /** What a member at home does each day without a command, in full ([homeRules]); the roster says it short. */
    val home: String = "",
)

fun GameEngine.memberDetail(state: GameState, heroId: HeroId): MemberDetailUi? {
    val g = state.guild ?: return null
    val m = g.member(heroId) ?: return null
    val h = state.heroes[heroId] ?: return null
    val cat = content.guild ?: return null
    val fighter = fighterView(state, heroId)
    val away = when (m.status) { MemberStatus.AWAY -> "${h.fullName} is away with the party."; MemberStatus.CAPTURED -> "${h.fullName} has been captured."; MemberStatus.HOME -> null }
    fun loanable(w: Weapon) = LoanableUi(w.id, w.name, Labels.weaponSummary(w, content))
    val traitDef = cat.trait(m.traitId)
    val branch = cat.speciality(m.specialityId)
    val kit = content.combat?.kitByClass?.get(h.classId)
    // The trait's and the speciality's own rules are their + lines: the fight list does not say them twice.
    val own = (traitDef?.effects.orEmpty() + branch?.effects.orEmpty()).map { it.id }.toSet()
    val carried = fighter?.effects.orEmpty().filter { it.id !in own }
    return MemberDetailUi(
        heroId = heroId, name = h.fullName,
        trait = traitLine(m.traitId),
        speciality = cat.speciality(m.specialityId)?.let { "${it.name}: ${it.description}" },
        bonds = m.bonds.map { b -> "${bondWord(b.kind)} with ${state.heroes[b.withHeroId]?.fullName ?: "one who is gone"} since day ${b.sinceDay}. ${b.reason}" },
        share = "Keeps ${m.sharePercent}% of the gold from quests they go on. " + if (m.fee > 0) "Recruited on day ${m.joinedDay} for ${m.fee} gold." else "With the guild since day ${m.joinedDay}.",
        numbers = fighter?.let { f ->
            listOfNotNull("Health" to "${f.health}/${f.maxHealth} (${h.health}%)", "Attack" to if (f.strikeMax > f.strikeMin) "${f.strikeMin} to ${f.strikeMax}" else "${f.strikeMin}", kit?.let { k -> supportValue(k.kit, f.support)?.let { "Support" to it } })
        }.orEmpty(),
        rules = fighter?.let { f -> (f.kit.passives + f.effects).distinctBy { it.id }.map { it.name to it.description } }.orEmpty(),
        branches = openBranches(state, heroId).map { BranchUi(it.id, it.name, it.description, deedWord(it.deed)) },
        loan = state.loanOf(heroId)?.let(::loanable),
        ownBlade = state.equippedWeapon(heroId)?.name,
        loanable = if (away != null) emptyList() else state.storedWeapons().filter { it.promisedTo == null }.map(::loanable),
        loanBlocked = away,
        dismissBlocked = away,
        gains = listOfNotNull(
            traitDef?.let { traitLine(it) },
            branch?.let { abilityLine(it.name, it.description, "Speciality", TipTone.BONUS) },
        ),
        costs = listOf(TipLine(shareWords(m.sharePercent, traitDef, config), TipTone.DRAWBACK)),
        abilities = if (fighter == null || kit == null) emptyList() else listOf(kitLine(kit)) + fighter.kit.passives.map { it.ability("Passive") } +
            carried.map { e -> e.ability(if (e.id in fighter.weaponRules) "Weapon" else null) },
        keywords = if (fighter == null || kit == null) emptyList() else keywords(
            kit.kit.moves.flatMap { it.statuses() } + (fighter.kit.passives + fighter.effects).flatMap { it.statuses() }, config,
        ),
        since = if (m.fee > 0) "Recruited on day ${m.joinedDay} for ${m.fee} gold." else "With the guild since day ${m.joinedDay}.",
        status = memberStatus(state, g, m),
        home = homeRules(config.heroRestHeal, state.livingRevision >= com.tinyblacksmith.core.model.LivingRevision.CURRENT),
        unlock = if (m.specialityId != null) null else
            "A speciality opens after ${Stories.VETERAN_WINS} quests won (${m.missionWins} so far) or after going down ${Stories.SURVIVOR_DOWNS} times (${m.timesDowned} so far). " +
                "A hero sworn to defend the walls also opens one by fulfilling that ambition.",
    )
}

/** A bond's stored kind (`Bond.kind`, a save key) as the player reads it. */
internal fun bondWord(kind: String): String = when (kind) { Stories.BOND_COMRADES -> "Comrades"; Stories.BOND_PROTECTOR -> "Protector"; else -> kind.replaceFirstChar { it.uppercase() } }

/** What opened a speciality pair (`SpecialityDef.deed`, a content key), with the rule's own numbers (`Stories.openBranches`). */
internal fun deedWord(deed: String): String = when (deed) {
    "veteran" -> "Opened by winning ${Stories.VETERAN_WINS} quests."
    "survivor" -> "Opened by going down ${Stories.SURVIVOR_DOWNS} times."
    "defender" -> "Opened by fulfilling their ambition to defend the walls."
    else -> ""
}
