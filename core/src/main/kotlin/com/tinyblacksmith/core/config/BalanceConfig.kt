package com.tinyblacksmith.core.config

import com.tinyblacksmith.core.model.Risk

/**
 * Single versioned home for every PROPOSED gameplay number (GDD Appendix A, §17).
 * LOCKED facts that are numbers (10 daily energy) also live here so tests can assert them,
 * but DECISIONS.md records which ones must not change without a design decision.
 */
data class RiskProfile(val exceptionalChance: Double, val defectChance: Double)

data class BalanceConfig(
    /** v2 (2026-10-08): launch-content retune of the quality formula and siege damage; see docs/DECISIONS.md. */
    val version: Int = 2,
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
    val raidPerDay: Double = 5.0,
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
    val legacyBasePoints: Int = 5,
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
    val nobleCommissionMinQuality: Int = 60,
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
    // --- Weapon-history compaction: newest combat entries (VICTORY per fight, SIEGE per siege) kept per weapon; ownership-grade entries are kept forever. 0 = never compact. ---
    val weaponHistoryCap: Int = 10,
) {
    companion object {
        val DEFAULT = BalanceConfig()
    }
}
