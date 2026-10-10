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
     * v7 (2026-10-09): fair customer selection ([CustomerConfig]: one intent draw per living hero, weighted seats, a waiting bound; the loyalty and reputation terms of the visit chance are capped); a larger town (12 residents with all five classes, refill toward 12, floor 9, event cap 16; 6 seats, festival +3) with expedition suppression 2 and raid growth 6.5. Prices, purses and wear are unchanged. Guild Patronage is no longer +15 points of visit chance: guild members come at the ceiling and their guild pays [CustomerConfig.patronageStipend] toward one purchase each. See docs/DECISIONS.md.
     * v8 (2026-10-09): standing wants ([CustomerConfig.needWantMet], [CustomerConfig.seatWantWeight], [CustomerConfig.wantLapseDays]): a hero who left with nothing comes back more readily while the shelf holds what they asked for. The sidegrade gate ([CustomerConfig.sidegradeTolerance]: the gain is no longer rounded, affixes and fame count on both sides, a near-equal blade may be bought once for taste, a prize or a name) and siege demand ([CustomerConfig.threatUtility], [CustomerConfig.championSiegeWillingness]: in the warning window the besieger's weakness is wanted and its resistance refused). Rumours ([CustomerConfig.maxRumoursPerRun]): real events earn rungs of a signature's clue ladder, and every miss at a base recipe now earns one. Commission situations ([CommissionConfig] weights, [CustomerConfig.maxOpenCommissions] 2): a request has a reason read from the town, and two may be open at once. See docs/DECISIONS.md.
     */
    val version: Int = 8,
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
    /** Gold per point of weapon power that heroes consider a fair price. */
    val fairGoldPerPower: Int = 4,
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
    /** v7: 2 (was 3). Suppression is the one channel that grows with head count; a town of twelve wins half again as many expeditions (docs/DECISIONS.md). */
    val expeditionSuppression: Int = 2,
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
    /** v3: 6.0 (was 5.0) after trade-ins and patrol pay armed heroes better; v7: 6.5 for a town of twelve (docs/DECISIONS.md). */
    val raidPerDay: Double = 6.5,
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
    val abandonedMineMaterials: Int = 3,
    val nobleCommissionRewardMultiplier: Int = 3,
    val newAdventurerCount: Int = 2,
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
    // v7: customers and population
    val customers: CustomerConfig = CustomerConfig(),
    // T5.4: numbers that were inline in the resolvers (values unchanged, so no version step)
    val combat: CombatConfig = CombatConfig(),
    val worldEvents: WorldEventConfig = WorldEventConfig(),
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
    // T5.4: were inline in heroes.Heroes; values unchanged.
    // Generation: a hero has [traitsMin]..[traitsMax] traits, takes the class's element taste with [classTasteChance], else another element with [otherTasteChance], else none.
    val traitsMin: Int = 2,
    val traitsMax: Int = 3,
    val classTasteChance: Double = 0.6,
    val otherTasteChance: Double = 0.5,
    /** A lineage's descendant arrives at this level; a retiree's mentee gains [menteeLevelBonus] on top of a newcomer's. */
    val descendantLevel: Int = 2,
    val menteeLevelBonus: Int = 1,
    // Daily activity weights before trait weights (GDD 6 utility model). EXPEDITION: base + pressure / 100 x [expeditionPressureWeight] + armed or unarmed.
    val expeditionBaseWeight: Double = 1.0,
    val expeditionPressureWeight: Double = 0.5,
    val armedExpeditionWeight: Double = 0.5,
    val unarmedExpeditionWeight: Double = -0.3,
    /** PATROL: base, plus [patrolLowIntegrityWeight] while the forge stands below [patrolLowIntegrity]. */
    val patrolBaseWeight: Double = 0.8,
    val patrolLowIntegrity: Int = 60,
    val patrolLowIntegrityWeight: Double = 0.4,
    /** REST: base plus the share of health missing. */
    val restBaseWeight: Double = 0.2,
    /** Floors: expedition and patrol never fall below the first, rest and the hall never below the second. */
    val fieldWeightFloor: Double = 0.05,
    val quietWeightFloor: Double = 0.02,
    /** The level that earns the HERO_LEVEL_5 milestone. */
    val milestoneLevel: Int = 5,
)

/**
 * v6: commission bands (PROPOSED). A commission's quality is always a band floor (market.QualityBand): a standard one
 * asks for "fine" ([BalanceConfig.rareMin]) with this chance and for "decent" ([BalanceConfig.uncommonMin]) otherwise,
 * the split the old 35..60 roll had; a noble one asks for "superb" ([BalanceConfig.epicMin]).
 */
data class CommissionConfig(
    val fineShare: Double = 0.4,
    // v8: situations. The daily roll first picks a kind among those somebody in town has a reason for, by these weights
    // (0 removes a kind), then the patron among the heroes with that reason. An ordinary request needs no reason.
    /** Below the others so that no kind, the ordinary one included, is more than two offers in five under any measured policy (docs/DECISIONS.md). */
    val ordinaryWeight: Double = 0.75,
    /** The patron carries a worn blade, or carried one this era and now has none. */
    val replacementWeight: Double = 1.0,
    /** A current champion, with the siege at most [siegePrepDays] days off: asks for the element the besieger fears, due on the siege day. */
    val siegePrepWeight: Double = 1.0,
    val siegePrepDays: Int = 4,
    /** An unfulfilled COLLECTOR: asks for the fine floor their ambition needs. */
    val ambitionWeight: Double = 1.0,
    /** A guild member orders for an unarmed hero who has never bought here; the blade goes to that hero. */
    val firstBladeWeight: Double = 1.0,
)

/**
 * v7: who lives in town and who gets a turn at the counter (all PROPOSED; plan 4.1, 4.2). The first seven numbers were
 * flat fields of [BalanceConfig] and keep their values; the visit terms were inline constants of the market.
 */
data class CustomerConfig(
    // Population (was startingHeroCount, minHeroPopulation, maxHeroPopulation). The first heroes of a run take one class each.
    val startingHeroes: Int = 12,
    /** While fewer heroes than this are alive, one newcomer may arrive each day: chance [arrivalChancePerMissing] per missing resident, at most [arrivalChanceMax]. */
    val populationTarget: Int = 12,
    val arrivalChancePerMissing: Double = 0.15,
    val arrivalChanceMax: Double = 0.6,
    /** Below this many living heroes the day's newcomer is certain. */
    val minHeroPopulation: Int = 9,
    /** Event arrivals never raise the living population above this. */
    val maxHeroPopulation: Int = 16,
    // Seats (was maxCustomersPerDay, festivalExtraCustomers). The Signboard adds one per level on top.
    val shopCapacity: Int = 6,
    val festivalExtraSeats: Int = 3,
    // Willingness: the chance that a living hero wants to visit today, one draw each.
    val baseVisitChance: Double = 0.35,
    val festivalVisitBonus: Double = 0.2,
    val visitTraitScale: Double = 0.1,
    /** Per point of loyalty, counted up to [visitLoyaltyCap]. */
    val visitPerLoyalty: Double = 0.01,
    val visitLoyaltyCap: Int = 10,
    /** Per point of shop reputation, counted up to [visitReputationCap]. */
    val visitPerReputation: Double = 0.005,
    val visitReputationCap: Int = 50,
    val visitFloor: Double = 0.05,
    val visitCeiling: Double = 0.9,
    // Seats among the willing: one weighted draw per seat (weight 1 for a stranger with nothing else to say for them).
    /** A willing hero turned away this many days running is seated before anyone else. */
    val maxTurnedAwayDays: Int = 2,
    /** The first ordinary seats go to heroes of classes not yet seated, while the willing allow. */
    val classSeats: Int = 3,
    /** Added at loyalty [seatLoyaltyCap] or more, in proportion below it: a regular is at most 1.5x a stranger. */
    val seatLoyaltyWeight: Double = 0.5,
    val seatLoyaltyCap: Int = 10,
    /** Added for a hero who has never been served at a stocked shelf. */
    val seatNewcomerWeight: Double = 1.0,
    /** Added per day of the current turned-away streak. */
    val seatWaitWeight: Double = 0.75,
    /** Added for a hero who is unarmed or whose own blade is worn. */
    val seatNeedWeight: Double = 0.5,
    /** Multiplies the weight of a hero who was served yesterday and bought nothing. */
    val seatBrowsedYesterday: Double = 0.5,
    // Guild Patronage (blessing): while it lasts every guild member is willing at [visitCeiling].
    /** Gold a member's guild pays toward one purchase per member per blessing; it counts toward what they can afford and reaches the till as its own income. */
    val patronageStipend: Int = 30,
    // v8: standing wants (plan 4.6 E1). A served hero who buys nothing leaves a want; it lapses after [wantLapseDays] days or on a purchase.
    /** Added to the visit chance while a listed blade answers the hero's want (`Market.answersWant`). 0 switches the loop off: wants are still recorded and move nothing. */
    val needWantMet: Double = 0.3,
    /** Added to the seat weight in the same case. */
    val seatWantWeight: Double = 0.5,
    val wantLapseDays: Int = 3,
    // v8: the sidegrade gate (plan 4.7, G01). 0 switches it off: only a gain is bought.
    /** A blade worth no less than this share below the one in hand may still be bought for a side reason its owner's blade lacks (taste, a collector's prize, a name), once per reason per hero. */
    val sidegradeTolerance: Double = 0.05,
    // v8: the town arms for the siege (plan 4.6 E2). In the warning window the faction matchup counts in what a blade is worth to a buyer.
    /** Purchase utility added, in the window, to a blade of the element the besieger is weak to. 0 switches it off. */
    val threatUtility: Double = 0.8,
    /** Added to a current champion's visit chance in the window. */
    val championSiegeWillingness: Double = 0.15,
    // v8: rumours (plan 4.6 E3). A hero who slays an elite with a blade, or a patron who collects a commission, tells of one
    // signature not yet found: one rung of its clue ladder. Kept here with the other things customers bring to the counter.
    /** At most this many rumours a run (0 switches them off), and none within [rumourCooldownDays] days of the last. */
    val maxRumoursPerRun: Int = 4,
    val rumourCooldownDays: Int = 3,
    // v8: commission situations (plan 4.6 E4).
    /** Commissions that may be offered or accepted at one time (1 before v8); a hero has at most one of them. */
    val maxOpenCommissions: Int = 2,
    // T5.4: were inline in market.Market; values unchanged.
    /** Loyalty counts in purchase utility as loyalty x this x [BalanceConfig.utilityLoyaltyWeight]. */
    val utilityLoyaltyScale: Double = 0.01,
    /** A refusal is recorded as OVERPRICED when the best blade's price penalty is above this. */
    val overpricedPenalty: Double = 0.5,
    /** Shop reputation per shelf sale and per commission delivered, and lost when an accepted commission expires. */
    val saleReputation: Int = 1,
    val commissionReputation: Int = 2,
    val commissionExpiredReputation: Int = 1,
    /** Loyalty the patron of a delivered commission gains. */
    val commissionLoyalty: Int = 2,
)

/** v5: replays and weapon fates. What is told of a fight, and what becomes of a fallen hero's blade (GDD 7, 11; all PROPOSED). */
data class WeaponFatesConfig(
    /** Fight replays kept in a day's report (elite fights and expeditions a hero died on, most significant first); the siege replay is always kept. */
    val maxExpeditionReplaysPerDay: Int = 3,
    // Weapon fates on a hero's death (GDD 7 "context-driven seeded recovery"). A fall on the road keeps
    // BalanceConfig.weaponRecoveryChance / weaponSeizureChance; comrades are close on the walls, and an elite keeps its trophy.
    val wallsRecoveryChance: Double = 0.7,
    val wallsSeizureChance: Double = 0.3,
    /**
     * A rout: a siege lost with the raid at [wallsRoutRatio] times the town's defense or more. Each champion then takes
     * [wallsRoutDamage] instead of BalanceConfig.championSiegeDamageOnLoss, which can kill one who went up barely fit
     * (champions stand at BalanceConfig.heroWoundedThreshold health or more). A narrower loss wounds and never kills.
     */
    val wallsRoutRatio: Double = 1.5,
    val wallsRoutDamage: Int = 55,
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
    // T5.4: were inline in legacy.Legacy; values unchanged.
    /** PROPOSED milestone bonus table (round 9: baseline rewards plus achievements). */
    val milestonePoints: Map<String, Int> = mapOf(
        "FIRST_SALE" to 1,
        "SIEGE_SURVIVED" to 2,
        "CHAMPION_ARMED" to 2,
        "EPIC_FORGED" to 1,
        "LEGENDARY_FORGED" to 3,
        "HERO_LEVEL_5" to 1,
        "WEAPON_FIVE_KILLS" to 2,
        "ELITE_SLAIN" to 1,
        "WARLORD_DEFEATED" to 3,
        "AMBITION_FULFILLED" to 1,
    ),
    /** The most famous blades of a run that go onto the Legend Board; the board keeps the newest [legendBoardSize], the account the newest [lineagesKept] lineages. */
    val legendsPerRun: Int = 3,
    val legendBoardSize: Int = 20,
    val lineagesKept: Int = 10,
)

/** T5.4: numbers that were inline in battle.Battle and battle.Power (all PROPOSED; values unchanged). */
data class CombatConfig(
    /** An expedition is won with this chance at equal powers; the difference moves it by 1 / [BalanceConfig.winProbabilityScale] per point. */
    val winProbabilityBase: Double = 0.5,
    /** Fame a hero and the blade in hand gain per won expedition and per siege held ([BalanceConfig.eliteFame] comes on top for an elite). */
    val expeditionFame: Int = 1,
    val siegeFame: Int = 2,
    /** Victories at which a blade earns its "Bane of" title and the WEAPON_FIVE_KILLS milestone. */
    val weaponTitleKills: Int = 5,
    /** A warning goes out on each of this many evenings before a siege; heroes shop under it on the days that follow. */
    val siegeWarningDays: Int = 2,
    /** Power.condition: a hero fights at [healthPowerFloor] + [healthPowerRange] x the share of health left. */
    val healthPowerFloor: Double = 0.6,
    val healthPowerRange: Double = 0.4,
    /** Bounds of the product of a hero's trait combat modifiers. */
    val traitModifierMin: Double = 0.8,
    val traitModifierMax: Double = 1.2,
    /** The defensive weight of bare hands (a weapon family has its own). */
    val unarmedDefensiveWeight: Double = 0.9,
    /** An elite, or a blade with scarce loot, brings back catalysts and materials of this tier or higher. */
    val scarceLootTier: Int = 3,
)

/** T5.4: effect sizes that were inline in engine.WorldEvents (all PROPOSED; values unchanged). Weights, limits and cooldowns stay in the event table. */
data class WorldEventConfig(
    /** Traveling Ore Merchant: units left at the forge, and extra units on sale at the supplier the next morning. */
    val oreMerchantGift: Int = 1,
    val oreMerchantStock: Int = 2,
    /** Noble Commission: days added to [BalanceConfig.commissionDeadlineDays]. */
    val nobleExtraDays: Int = 2,
    val veteranFame: Int = 2,
    /** Mysterious Alloy and Strange Weapon Fragment: units of each of the two materials. */
    val alloyMaterials: Int = 1,
    val fragmentMaterials: Int = 1,
    val shrineCatalysts: Int = 2,
    /** A Famous Blade Returns: the quality a legend recorded without one is taken to have had. */
    val legendDefaultQuality: Int = 60,
    val bannerReputation: Int = 2,
    val bannerMilitia: Int = 3,
    val collectorReputation: Int = 1,
)
