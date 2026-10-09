package com.tinyblacksmith.core.config

import com.tinyblacksmith.core.model.Risk

/**
 * Single versioned home for every PROPOSED gameplay number (GDD Appendix A, §17).
 * LOCKED facts that are numbers (10 daily energy) also live here so tests can assert them,
 * but DECISIONS.md records which ones must not change without a design decision.
 */
data class RiskProfile(val exceptionalChance: Double, val defectChance: Double)

data class BalanceConfig(
    /**
     * v2 (2026-10-08): launch-content retune of the quality formula and siege damage.
     * v3 (2026-10-09): elite encounters, warlord sieges, hero ambitions, affix effects, shop actions, tools, trade-ins, raid growth 6. See docs/DECISIONS.md.
     * v4 (2026-10-09): weapon wear, weapon fame, whetstone 120/300, warlord pressure 50 with no raid bonus. See docs/DECISIONS.md.
     * v5 (2026-10-09): signboard adds a customer a day per level, stronger affix magnitudes (catalog numbers), guild hall and ambition days ([HeroLifeConfig]), fight replays and weapon fates ([WeaponFatesConfig]), three legacy tracks and Known Name regulars ([LegacyTracksConfig]). See docs/DECISIONS.md.
     * v6 (2026-10-09): commissions ask for a quality band floor ([CommissionConfig]; a noble one for the superb floor, `nobleCommissionMinQuality` removed), COLLECTOR ambition at the fine floor 50 (was 60). See docs/DECISIONS.md.
     */
    val version: Int = 6,
    // Energy (GDD 4.3). LOCKED: 10 base. PROPOSED: 4 overwork, 1:1 debt.
    val baseDailyEnergy: Int = 10,
    val maxOverworkPerDay: Int = 4,
    val quickForgeEnergy: Int = 2,
    val advancedForgeEnergy: Int = 4,
    // Risk (GDD 4.4 PROPOSED table).
    val risk: Map<Risk, RiskProfile> = mapOf(
        Risk.SAFE to RiskProfile(0.06, 0.02),
        Risk.BALANCED to RiskProfile(0.15, 0.08),
        Risk.RECKLESS to RiskProfile(0.30, 0.20),
    ),
    val maxRollChance: Double = 0.75,
    val catalystExceptionalBonus: Double = 0.05,
    val catalystQualityBonus: Int = 4,
    // Quality formula (GDD 4.4 shape): base + core*tier + aug*tier + affinity + mastery + roll[-13,13] + 20 exc - 12 defect.
    // GDD proposed 35 + 4*core; v2 uses 25 + 6*core so six core tiers spread common -> epic (docs/DECISIONS.md rarity tables).
    val qualityBase: Int = 25,
    val qualityPerCoreTier: Int = 6,
    val qualityPerAugmentTier: Int = 3,
    val qualityRollSpread: Int = 13,
    val qualityExceptionalBonus: Int = 20,
    val qualityDefectPenalty: Int = 12,
    val powerPerCoreTier: Int = 3,
    val powerPerQualityDivisor: Int = 5,
    // Rarity ranges (PROPOSED). Upper bounds inclusive.
    val uncommonMin: Int = 35,
    val rareMin: Int = 50,
    val epicMin: Int = 70,
    val legendaryMin: Int = 85,
    // Journal: experiments needed to move OBSERVED -> UNDERSTOOD.
    val experimentsToUnderstand: Int = 3,
    // Economy (GDD 5 PROPOSED).
    val startingGold: Int = 250,
    val shelfSlots: Int = 8,
    val startingForgeIntegrity: Int = 100,
    val startingMaterials: Map<String, Int> = mapOf(
        "iron" to 6, "bronze" to 3, "silver" to 1,
        "ember_resin" to 4, "frost_bloom" to 3, "stormglass" to 1,
        "binding_salt" to 1,
    ),
    val maxCustomersPerDay: Int = 4,
    /** Gold per point of weapon power that heroes consider a fair price. */
    val fairGoldPerPower: Int = 4,
    val baseVisitChance: Double = 0.35,
    val purchaseUtilityThreshold: Double = 0.5,
    val utilityImprovementWeight: Double = 0.12,
    val utilityClassFitWeight: Double = 1.5,
    val utilityElementTasteWeight: Double = 1.0,
    val utilityLoyaltyWeight: Double = 0.3,
    val utilityPricePenaltyWeight: Double = 3.0,
    val utilityNoise: Double = 0.4,
    val commissionChancePerDay: Double = 0.3,
    val commissionDeadlineDays: Int = 4,
    val commissionRewardPerQuality: Int = 2,
    val commissionRewardBase: Int = 40,
    // Heroes (GDD 6 PROPOSED).
    val startingHeroCount: Int = 8,
    val minHeroPopulation: Int = 5,
    val heroWoundedThreshold: Int = 50,
    val heroRestHeal: Int = 35,
    val heroLevelXp: Int = 120,
    val heroMaxLevel: Int = 20,
    val heroDeathHealthFloor: Int = 0,
    val offFamilyFit: Double = 0.8,
    val unarmedPower: Int = 4,
    // Combat (GDD 6/8 PROPOSED).
    val encounterBasePower: Int = 18,
    val encounterPowerPerDay: Double = 1.4,
    val encounterPowerPerPressure: Double = 0.25,
    val encounterVariance: Double = 0.2,
    val winProbabilityScale: Double = 40.0,
    val winProbabilityFloor: Double = 0.05,
    val winProbabilityCeiling: Double = 0.95,
    val matchupWeakBonus: Double = 1.25,
    val matchupResistPenalty: Double = 0.8,
    val expeditionXp: Int = 40,
    val patrolXp: Int = 15,
    val expeditionGoldMin: Int = 20,
    val expeditionGoldMax: Int = 60,
    val expeditionLootChance: Double = 0.35,
    val expeditionDamageMin: Int = 20,
    val expeditionDamageMax: Int = 55,
    val expeditionSuppression: Int = 3,
    val patrolSuppression: Int = 1,
    val patrolMilitiaGain: Int = 3,
    val militiaDecayPerDay: Int = 1,
    val militiaMax: Int = 30,
    val weaponRecoveryChance: Double = 0.5,
    // Factions and sieges (GDD 8 PROPOSED).
    val siegeInterval: Int = 5,
    val startingPressureMin: Int = 20,
    val startingPressureMax: Int = 45,
    val raidBase: Double = 32.0,
    /** v3: 6.0 (was 5.0) after trade-ins and patrol pay armed heroes better (docs/DECISIONS.md). */
    val raidPerDay: Double = 6.0,
    val raidPerPressure: Double = 0.65,
    /** v1 slice sweep adopted 2.75; v2 (launch content, three factions) lowered it to 2.0 so early sieges are winnable and moved run length to forge damage (docs/DECISIONS.md). */
    val siegeModifier: Double = 2.0,
    /** GDD scaffold 12 + 25x(ratio-1); v2 uses 24 + 50x(ratio-1) so a lost siege costs the forge about a third of its integrity. */
    val forgeDamageBase: Double = 24.0,
    val forgeDamageSlope: Double = 50.0,
    val maxForgeDamagePerSiege: Int = 60,
    val championCount: Int = 3,
    val championSiegeDamageOnLoss: Int = 40,
    val championSiegeDamageOnWin: Int = 10,
    val siegeWinPressureDrop: Int = 25,
    val siegeXp: Int = 60,
    val maxIntegrityRecoveryPerDay: Int = 2,
    val recoveryPerPatrol: Int = 1,
    val recoveryPerExpeditionWin: Int = 1,
    // Legacy (GDD 9 PROPOSED): 5 + floor(days/5) + discovery + milestones
    /** v3: 6 (GDD scaffold 5) so the shortest possible first run still affords the cheapest upgrade. */
    val legacyBasePoints: Int = 6,
    val legacyDaysPerPoint: Int = 5,
    val legacyDiscoveryPointCap: Int = 5,
    val blessingOfferSize: Int = 3,
    val legendFameThreshold: Int = 3,
    // Supplier
    val supplierPriceMultiplier: Double = 1.0,
    // Simulation guard
    val maxSimulatedDays: Int = 400,
    // Signature recipes (GDD 4.5 PROPOSED): base 15 % eligible transformation chance, + per mastery point, capped at 50 %.
    val signatureBaseChance: Double = 0.15,
    val signatureChancePerMastery: Double = 0.01,
    val signatureMaxChance: Double = 0.50,
    // Advanced Forge techniques (GDD 4.3 PROPOSED): each is a trade, never strictly better.
    val temperDefectReduction: Double = 0.06,
    val temperExceptionalReduction: Double = 0.06,
    val quenchQualityPenalty: Int = 3,
    val etchExtraAffixSlots: Int = 1,
    val etchDefectIncrease: Double = 0.06,
    // --- World events (GDD 10), generations (GDD 6) and artifact cycle (GDD 7); all PROPOSED. Owned by WorldEvents/Heroes/Battle. ---
    /** Chance that one scripted world event fires on a given day (at most one per day). */
    val worldEventChancePerDay: Double = 0.3,
    val encampmentPressure: Int = 10,
    val successfulPatrolPressureDrop: Int = 8,
    val successfulPatrolMilitia: Int = 3,
    val festivalExtraCustomers: Int = 2,
    val festivalVisitBonus: Double = 0.2,
    val abandonedMineMaterials: Int = 3,
    val nobleCommissionRewardMultiplier: Int = 3,
    val newAdventurerCount: Int = 2,
    val maxHeroPopulation: Int = 12,
    val veteranLevel: Int = 5,
    val veteranGold: Int = 150,
    val ambushDamage: Int = 35,
    val balladReputation: Int = 3,
    val returnedLegendQualityFactor: Double = 0.7,
    val collectorPriceMultiplier: Double = 1.5,
    // Retirement: level threshold, or enough victories plus a seeded daily check.
    val retirementLevel: Int = 8,
    val retirementVictories: Int = 10,
    val retirementChance: Double = 0.15,
    /** Fame needed for a retiring hero to found a guild. */
    val guildFameThreshold: Int = 3,
    /** When a dead hero's weapon is not recovered, chance that monsters seize it (else plain loss). */
    val weaponSeizureChance: Double = 0.5,
    // --- Event-log compaction (GDD 13.3): full records for the last N days; rare history-grade types are kept forever. 0 = never compact. ---
    val eventRetentionDays: Int = 30,
    // --- Reputation and loyalty depth (GDD 5 "Reputation", PROPOSED; docs/DECISIONS.md session 4). ---
    /** Each point of shop reputation raises every hero's price ceiling (the price they treat as fair) by this fraction, up to [reputationPriceCap]. */
    val reputationPricePerPoint: Double = 0.01,
    val reputationPriceCap: Double = 0.25,
    /** Each point of a hero's loyalty raises that hero's own price ceiling by this fraction, up to [loyaltyPriceCap]. */
    val loyaltyPricePerPoint: Double = 0.03,
    val loyaltyPriceCap: Double = 0.25,
    /** Commission patrons are drawn with weight 1 + min(loyalty, cap) * weight, so regulars return with requests. */
    val commissionLoyaltyWeight: Double = 0.5,
    val commissionLoyaltyCap: Int = 10,
    /** Loyalty at which the Gazette calls a hero a regular of the shop. */
    val regularLoyaltyThreshold: Int = 3,
    // --- Weapon-history compaction: newest combat entries (VICTORY per fight, SIEGE per siege) kept per weapon; ownership-grade entries are kept forever. 0 = never compact. ---
    val weaponHistoryCap: Int = 10,
    // --- Weapon pruning: a salvaged, shattered, donated or collected blade leaves the save this many days later (Legend Board candidates and signatures stay). 0 = never prune. ---
    val weaponRetentionDays: Int = 30,
    // --- Elite encounters and warlord sieges (GDD 8 elite/boss variants, PROPOSED). ---
    /** Chance that an expedition meets an elite: base + pressure x perPressure. */
    val eliteBaseChance: Double = 0.06,
    val eliteChancePerPressure: Double = 0.002,
    val elitePowerMultiplier: Double = 1.35,
    val eliteGoldMultiplier: Double = 2.5,
    val eliteDamageMultiplier: Double = 1.25,
    val eliteFame: Int = 2,
    val eliteSuppression: Int = 4,
    /** At or above this pressure the faction's warlord leads the siege. */
    val warlordPressure: Int = 50,
    val warlordRaidMultiplier: Double = 1.0,
    val warlordPressureDrop: Int = 15,
    /** Gold the town pays the smith when a warlord is beaten at the walls. */
    val warlordTribute: Int = 120,
    // --- Hero ambitions (GDD 6, PROPOSED). ---
    val ambitionSlayerWins: Int = 4,
    /** v6: the "fine" floor ([rareMin]), so "wants a fine blade" is what the rule checks (was 60, mid-band). */
    val ambitionCollectorQuality: Int = 50,
    val ambitionFortuneGold: Int = 300,
    /** Weight of the AMBITION activity while the ambition is unfulfilled (v3 added it to the expedition or patrol weight instead). */
    val ambitionActivityWeight: Double = 0.6,
    /** Purchase utility a COLLECTOR adds to a weapon of [ambitionCollectorQuality] or better. */
    val collectorUtilityBonus: Double = 0.6,
    /** A hero saving for a FORTUNE feels prices this much more. */
    val fortunePriceSensitivity: Double = 1.3,
    val ambitionFame: Int = 3,
    val ambitionLoyalty: Int = 2,
    val ambitionReputation: Int = 2,
    // --- Commissions with a desired element (GDD 5 "desirable effect", PROPOSED). ---
    val commissionElementChance: Double = 0.5,
    val commissionElementRewardMultiplier: Double = 1.5,
    // --- Shop actions on finished weapons (PROPOSED). ---
    val salvageEnergy: Int = 1,
    val honeEnergy: Int = 2,
    val honeQualityBonus: Int = 6,
    /** Share of a donated weapon's power that joins the town's defense, up to [armoryMax]; a siege wears [armorySiegeWear] of it away. */
    val armoryPowerShare: Double = 0.2,
    val armoryMax: Int = 30,
    val armorySiegeWear: Double = 0.5,
    // --- Demand (PROPOSED): half of all shop visits ended TOO_EXPENSIVE in the v3 sweep. ---
    /** A hero replacing a weapon hands the old one back to the shop for this share of its fair price, as credit against the new one. */
    val tradeInShare: Double = 0.4,
    /** The town pays a hero this much for a day's patrol. */
    val patrolGold: Int = 12,
    // --- Weapon wear (PROPOSED): most fair-price visits ended NOT_BETTER in the v3 sweep; blades now wear out and are replaced. ---
    // v4: weapon wear
    /** Condition the equipped weapon loses per expedition (a rout is harder on the blade) and per siege stood as champion; floor 0. */
    val wearPerExpeditionWin: Int = 6,
    val wearPerExpeditionLoss: Int = 10,
    val wearPerSiege: Int = 15,
    /** Weapon-power multiplier at condition 0; 1.0 at 100, linear between (Power.conditionFactor). */
    val conditionFloorFactor: Double = 0.75,
    /** Below this condition the shop calls a blade worn and its owner is keen to replace it. */
    val wornConditionThreshold: Int = 50,
    /** Purchase utility a hero adds to every listing while their own weapon is below [wornConditionThreshold]. */
    val wornReplacementUtility: Double = 0.6,
    // v4: weapon fame (GDD 7 PROPOSED "a limited mechanical effect, with caps against runaway snowballing").
    // Fame keeps growing as a record (+1 per won expedition, +2 per siege held, +2 for an elite); only the first
    // [weaponFameCap] points count for any effect, so fame earned through the bonus can never feed itself past the cap.
    val weaponFameCap: Int = 10,
    /** Attack and defense power per counted fame point (+5 % at the cap; 0.01 overshoots the v3 survival band, docs/DECISIONS.md). */
    val weaponFamePowerPerPoint: Double = 0.005,
    /** Purchase utility per counted fame point (+0.5 at the cap, one purchase threshold); a COLLECTOR counts it [collectorFameMultiplier] times (+1.0 at most). */
    val weaponFameUtilityPerPoint: Double = 0.05,
    val collectorFameMultiplier: Double = 2.0,
    /** Suggested-price premium per counted fame point (+5 % at the cap). Heroes do not raise their price ceiling for fame, so the premium must stay well under the utility bonus (3 x premium x the thriftiest sensitivity 1.6 x 1.3). */
    val weaponFamePricePerPoint: Double = 0.005,
    // v5: hero daily life
    /** GUILD and AMBITION as scored daily activities, and the money and prior-history inputs (GDD 6 utility model); the numbers are in [HeroLifeConfig]. */
    val heroLife: HeroLifeConfig = HeroLifeConfig(),
    // v5: replays and weapon fates
    /**
     * One nested object, not flat fields: this constructor is at the JVM limit of 255 parameter slots (a Double or Long
     * takes two). 176 fields used 250 of them before v5; past 255 the class still compiles and then fails at load
     * ("Too many arguments in method signature"). New groups of numbers must be nested like this one (one slot).
     */
    val weaponFates: WeaponFatesConfig = WeaponFatesConfig(),
    // v5: legacy tracks
    /** One field (one argument slot) for every number of the v5 legacy tracks; see [LegacyTracksConfig] for why. */
    val legacyTracks: LegacyTracksConfig = LegacyTracksConfig(),
    // v6: commission bands
    val commissions: CommissionConfig = CommissionConfig(),
) {
    companion object {
        val DEFAULT = BalanceConfig()
    }
}

// v5: hero daily life
/**
 * All PROPOSED. A group of its own because [BalanceConfig] is at the JVM limit of 255 parameter slots for one method
 * (a Double takes two): with eleven more flat numbers the class compiles but fails to load ("Too many arguments in
 * method signature"). One reference costs one slot.
 */
data class HeroLifeConfig(
    /** Weight of a day at the guild hall before trait weights (TraitDef.guildWeight); floor 0.02. */
    val guildBaseWeight: Double = 0.35,
    /** A day at the hall: XP and health, no gold, no suppression, no militia. With [mentorXp] it stays under one won expedition (BalanceConfig.expeditionXp). */
    val guildXp: Int = 20,
    val guildHeal: Int = 10,
    /** Extra XP for a hero taught at the hall by a higher-level guildmate who is there the same day; at most once a day per hero. */
    val mentorXp: Int = 15,
    /** AMBITION, SLAYER: added to the elite chance of the hunt (an expedition otherwise by the usual rules). */
    val slayerHuntEliteChance: Double = 0.3,
    /** AMBITION, DEFENDER: militia a day of drilling the watch adds (a patrol adds BalanceConfig.patrolMilitiaGain and suppresses; a drill does not suppress or pay). */
    val defenderDrillMilitia: Int = 5,
    /** AMBITION, COLLECTOR and FORTUNE: gold for a day of paid work (a patrol pays BalanceConfig.patrolGold); no XP, suppression or militia. */
    val ambitionWorkGold: Int = 40,
    /** Money: an unarmed hero holding less gold than this leans toward the town's patrol pay by [poorPatrolWeight]. */
    val poorHeroGold: Int = 60,
    val poorPatrolWeight: Double = 0.6,
    /** Prior history: a hero driven back from an expedition yesterday leans toward rest and toward the hall. */
    val setbackRestWeight: Double = 0.5,
    val setbackGuildWeight: Double = 0.4,
)

/**
 * v6: commission bands (PROPOSED). A commission's quality is always a band floor (market.QualityBand): a standard one
 * asks for "fine" ([BalanceConfig.rareMin]) with this chance and for "decent" ([BalanceConfig.uncommonMin]) otherwise,
 * the split the old 35..60 roll had; a noble one asks for "superb" ([BalanceConfig.epicMin]).
 */
data class CommissionConfig(
    val fineShare: Double = 0.4,
)

/** v5: replays and weapon fates. What is told of a fight, and what becomes of a fallen hero's blade (GDD 7, 11; all PROPOSED). */
data class WeaponFatesConfig(
    /** Fight replays kept in a day's report (elite fights and expeditions a hero died on, most significant first); the siege replay is always kept. */
    val maxExpeditionReplaysPerDay: Int = 3,
    // Weapon fates on a hero's death (GDD 7 "context-driven seeded recovery"). A fall on the road keeps
    // BalanceConfig.weaponRecoveryChance / weaponSeizureChance; comrades are close on the walls, and an elite keeps its trophy.
    val wallsRecoveryChance: Double = 0.7,
    val wallsSeizureChance: Double = 0.3,
    val eliteRecoveryChance: Double = 0.4,
    val eliteSeizureChance: Double = 0.7,
    /** Chance that a living guildmate inherits a fallen member's blade the enemy did not seize (it then never reaches the forge). */
    val guildInheritanceChance: Double = 0.6,
    /** A blade that would be lost surfaces with a travelling merchant at base + counted fame x perFame (fame up to BalanceConfig.weaponFameCap), never above the maximum. */
    val merchantBaseChance: Double = 0.3,
    val merchantChancePerFame: Double = 0.05,
    val merchantMaxChance: Double = 0.7,
    /** The merchant reaches Emberfall this many days after the hero fell and offers the blade on that End Day and the following ones, [merchantStayDays] in all, then moves on. */
    val merchantDelayDays: Int = 2,
    val merchantStayDays: Int = 3,
)

/**
 * v5: legacy tracks. Every number of the three GDD 9 tracks added in v5 (catalog access, recipe odds, legacy
 * artifacts) and of the Known Name regulars; all PROPOSED, evidence in docs/DECISIONS.md "Balance v5".
 *
 * Grouped in one object because [BalanceConfig] sits at the JVM limit of 255 argument slots per method (a Double takes
 * two; its generated `copy$default` needed 250 before v5). More flat fields still compile, but the class then fails to
 * load ("Too many arguments in method signature") and every test with it.
 */
data class LegacyTracksConfig(
    /** Caravan Ties: extra units per level of every limited-stock material the supplier carries each day. */
    val catalogStockPerLevel: Int = 1,
    /** Anvil Lore: added per level to the signature transformation chance, inside [BalanceConfig.signatureMaxChance]. */
    val recipeOddsPerLevel: Double = 0.05,
    /** Homing Steel: added per level to the weight of the "A Famous Blade Returns" event (1.0 without it; still at most once a run). */
    val legendReturnWeightPerLevel: Double = 1.0,
    /** Homing Steel: added per level to [BalanceConfig.returnedLegendQualityFactor], up to [returnedLegendQualityFactorMax]; a returned blade is never whole (GDD 7). */
    val legendQualityFactorPerLevel: Double = 0.05,
    val returnedLegendQualityFactorMax: Double = 0.85,
    /**
     * Known Name: one starting hero per level is already a regular (loyalty [BalanceConfig.regularLoyaltyThreshold])
     * and has this much extra coin saved for a blade. Starting reputation alone measured -0.3 days (DECISIONS.md).
     */
    val knownNameRegularGold: Int = 30,
)
