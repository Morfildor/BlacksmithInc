package com.tinyblacksmith.core.config

import com.tinyblacksmith.core.combat.FightRules

/**
 * v11: every number of the guild evolution (docs/GUILD_EVOLUTION_PLAN.md). All PROPOSED. One nested group, one slot of
 * [BalanceConfig]. A run without a guild reads none of it.
 */
data class GuildConfig(
    val fight: FightRules = FightRules(),

    // Roster (spec 4.1, 4.2)
    val maxMembers: Int = 6,
    val partyMax: Int = 3,
    /** Two residents would sign on a refresh morning; the list is redrawn every this many days. */
    val candidatesOffered: Int = 2,
    val candidateRefreshDays: Int = 3,
    val signingFeeBase: Int = 35,
    val signingFeePerLevel: Int = 15,
    /** The third member of the opening sequence signs for this share of the usual fee. */
    val openingFeePercent: Int = 50,
    /** Share of a won contract's gold each member of the party keeps. */
    val memberSharePercent: Int = 10,

    // The board (spec 5.1)
    val offersOnBoard: Int = 3,
    /** Enemy numbers on a contract: this percent of the unit as written, plus the day and the faction's pressure. */
    val enemyPercentBase: Int = 165,
    val enemyPercentPerDay: Double = 4.0,
    val enemyPercentPerPressure: Double = 0.4,
    /** Contract gold grows with the day by this percent a day. */
    val rewardPercentPerDay: Double = 2.0,
    /** A sabotage contract is posted while the siege is at most this many days away. */
    val sabotageWindowDays: Int = 4,

    // Aftermath (spec 10.4, 10.5)
    /** Stored health of a member who went down and was brought home. */
    val downedHealth: Int = 20,
    val injuredDays: Int = 2,
    val exhaustedDays: Int = 1,
    /** A member who comes home under this stored health without having gone down is exhausted. */
    val exhaustedBelowHealth: Int = 35,
    val scarredDays: Int = 5,
    /** Share of their health a scarred member enters a fight with at most. */
    val scarredHealthPercent: Int = 75,
    val captiveDays: Int = 5,
    val missionWear: Int = 6,
    /** Condition a cracked blade loses on top of the wear: the repair bill. */
    val fractureWear: Int = 25,
    val missionXpWin: Int = 30,
    val missionXpLoss: Int = 12,
    val missionFame: Int = 2,
    /** A fit member at home trains for this much XP a day; a hurt one rests and heals like any resident. */
    val homeTrainingXp: Int = 6,
    /** Share of a stage's gold a party with the Coward's Medal still brings home when it pulls back. */
    val medalRetreatGoldPercent: Int = 50,

    // Siege through the interaction engine (spec 10.3)
    val defenders: Int = 3,
    val siegePercentBase: Int = 150,
    val siegePercentPerDay: Double = 5.0,
    val siegePercentPerPressure: Double = 0.5,
    /** Grunts beside the leader or elite: this many, one more every [siegeGruntEvery] sieges, up to the field's size. */
    val siegeGrunts: Int = 2,
    val siegeGruntEvery: Int = 3,
    /** Enemy health one point of militia or armory removes on the outer approach, before the champions' fight. */
    val outerHealthPerPoint: Double = 0.5,
    /** The outer approach never takes more than this share of an enemy's health. */
    val outerMaxPercent: Int = 60,
    /** Forge damage by how the siege ended, as a share (percent) of `maxForgeDamagePerSiege` at full enemy strength left. */
    val damageHeldPerDowned: Int = 3,
    val damageCostPercent: Int = 45,
    val damageBreachPercent: Int = 100,
    val damageBreachMin: Int = 12,
    /** With nobody on the wall, the raid meets only the watch: this percent of the breach. */
    val damageUndefendedPercent: Int = 120,
    /** A lost siege kills the first defender to fall when at least this share of the enemy's health is still standing. */
    val routRemainingPercent: Int = 60,
    val siegePressureDropPerSabotage: Int = 0,

    // Charter (spec 10.7)
    val charterDay: Int = 20,
    val charterWarlordPercent: Int = 115,
    val charterLegacyPoints: Int = 6,
    val charterTribute: Int = 150,

    // Visitors of the guild (spec 11)
    val captiveTimeGold: Int = 40,
    val captiveTimeDays: Int = 2,
    /** A questioned survivor, a dealer's lead: the guards are this percent of what they were. */
    val questionedPercent: Int = 85,
    val silenceEnergy: Int = 2,
    val oathMaxLevel: Int = 3,
    val buyBackPercent: Int = 150,
    val insuranceFeePercent: Int = 15,
    val insurancePayoutPercent: Int = 60,
    val insuranceDays: Int = 10,
    val tournamentGold: Int = 40,
    val tournamentReputation: Int = 1,
    val tournamentFame: Int = 3,

    // Stories (spec 12)
    val rivalFirstDay: Int = 6,
    val rivalMoveEveryDays: Int = 4,
    val nemesisPercent: Int = 110,
    val bondMissions: Int = 3,
    val lawFirstDay: Int = 8,
    val lawChance: Double = 0.25,
)
