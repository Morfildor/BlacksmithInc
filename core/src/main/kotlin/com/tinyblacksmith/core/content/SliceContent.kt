package com.tinyblacksmith.core.content

import com.tinyblacksmith.core.model.*

/**
 * Vertical-slice content (GDD 16.1): 3 families, 6 materials (+1 catalyst for Advanced Forge scaffolding),
 * 2 classes, 1 faction. Names are PROPOSED (GDD 4.2) and live only here; everything references stable IDs.
 */
object SliceContent {
    val SWORD = WeaponFamilyId("sword")
    val AXE = WeaponFamilyId("axe")
    val BOW = WeaponFamilyId("bow")

    val IRON = MaterialId("iron")
    val BRONZE = MaterialId("bronze")
    val SILVER = MaterialId("silver")
    val EMBER_RESIN = MaterialId("ember_resin")
    val FROST_BLOOM = MaterialId("frost_bloom")
    val STORMGLASS = MaterialId("stormglass")
    val BINDING_SALT = MaterialId("binding_salt")

    val GUARDIAN = HeroClassId("guardian")
    val RANGER = HeroClassId("ranger")

    val BRAVE = TraitId("brave")
    val CAUTIOUS = TraitId("cautious")
    val GREEDY = TraitId("greedy")
    val AMBITIOUS = TraitId("ambitious")
    val LOYAL = TraitId("loyal")
    val CURIOUS = TraitId("curious")
    val PATIENT = TraitId("patient")

    val ASHCLAW = FactionId("ashclaw_raiders")

    val FLAMING = AffixId("flaming")
    val FROSTBOUND = AffixId("frostbound")
    val STORMCHARGED = AffixId("stormcharged")
    val KEEN = AffixId("keen")
    val REINFORCED = AffixId("reinforced")
    val GUARDIANS = AffixId("guardians")
    val BRITTLE = AffixId("brittle")
    val HEAVY = AffixId("heavy")
    val UNSTABLE = AffixId("unstable")

    val FORGEFIRE = BlessingId("forgefire")
    val TIRELESS_HANDS = BlessingId("tireless_hands")
    val MERCHANTS_FAVOR = BlessingId("merchants_favor")
    val STALWART_TOWN = BlessingId("stalwart_town")
    val HUNTERS_EDGE = BlessingId("hunters_edge")

    val UPG_ENERGY = UpgradeId("starting_energy")
    val UPG_GOLD = UpgradeId("starting_gold")
    val UPG_MASTERY = UpgradeId("forge_mastery")
    val UPG_WALLS = UpgradeId("stalwart_walls")

    val catalog: ContentCatalog = ContentCatalog(
        version = 1,
        families = listOf(
            WeaponFamilyDef(SWORD, "Sword", basePower = 10, classFit = mapOf(GUARDIAN to 1.2, RANGER to 0.9), defensiveWeight = 1.1),
            WeaponFamilyDef(AXE, "Axe", basePower = 12, classFit = mapOf(GUARDIAN to 1.15, RANGER to 0.75), defensiveWeight = 1.0),
            WeaponFamilyDef(BOW, "Bow", basePower = 9, classFit = mapOf(GUARDIAN to 0.7, RANGER to 1.25), defensiveWeight = 0.9),
        ),
        materials = listOf(
            MaterialDef(IRON, "Iron", MaterialCategory.CORE, tier = 1, price = 10, flavor = "Honest grey metal."),
            MaterialDef(BRONZE, "Bronze", MaterialCategory.CORE, tier = 2, price = 22, flavor = "Warm alloy that holds an edge."),
            MaterialDef(SILVER, "Silver", MaterialCategory.CORE, tier = 3, price = 45, dailySupplierStock = 1, flavor = "Bright, rare, hungry for augments."),
            MaterialDef(EMBER_RESIN, "Ember Resin", MaterialCategory.AUGMENT, tier = 1, price = 8, element = Element.FIRE, flavor = "Smoulders when struck."),
            MaterialDef(FROST_BLOOM, "Frost Bloom", MaterialCategory.AUGMENT, tier = 2, price = 18, element = Element.FROST, flavor = "Petals that never thaw."),
            MaterialDef(STORMGLASS, "Stormglass", MaterialCategory.AUGMENT, tier = 3, price = 40, element = Element.STORM, dailySupplierStock = 1, flavor = "Hums before a storm."),
            MaterialDef(BINDING_SALT, "Binding Salt", MaterialCategory.CATALYST, tier = 1, price = 15, dailySupplierStock = 2, flavor = "Steadies volatile forgings."),
        ),
        affixes = listOf(
            AffixDef(FLAMING, "Flaming", AffixKind.BENEFICIAL, power = 4, element = Element.FIRE, description = "Improves attacks with fire. Some enemies resist it.", attackMultiplier = 1.08),
            AffixDef(FROSTBOUND, "Frostbound", AffixKind.BENEFICIAL, power = 4, element = Element.FROST, description = "Frost improves attack and defense.", attackMultiplier = 1.05, defenseMultiplier = 1.05),
            AffixDef(STORMCHARGED, "Stormcharged", AffixKind.BENEFICIAL, power = 5, element = Element.STORM, description = "Crackles with lightning.", attackMultiplier = 1.1),
            AffixDef(KEEN, "Keen", AffixKind.BENEFICIAL, power = 3, description = "Sharper than it looks.", attackMultiplier = 1.06),
            AffixDef(REINFORCED, "Reinforced", AffixKind.BENEFICIAL, power = 2, description = "Hard to break.", defenseMultiplier = 1.1),
            AffixDef(GUARDIANS, "Guardian's", AffixKind.BENEFICIAL, power = 2, description = "Steadies the wielder in defense.", defenseMultiplier = 1.15),
            AffixDef(BRITTLE, "Brittle", AffixKind.FLAW, power = -3, description = "Chips under stress.", defenseMultiplier = 0.9),
            AffixDef(HEAVY, "Heavy", AffixKind.FLAW, power = -2, description = "Tires the wielder.", attackMultiplier = 0.92),
            AffixDef(UNSTABLE, "Unstable", AffixKind.FLAW, power = -4, description = "Unreliable in a pinch.", attackMultiplier = 0.9),
        ),
        classes = listOf(
            HeroClassDef(GUARDIAN, "Guardian", basePower = 16, powerPerLevel = 2, preferredFamilies = listOf(SWORD, AXE), preferredElement = Element.FROST, startingGoldMin = 60, startingGoldMax = 140, appearances = listOf("portrait_hero_01", "portrait_hero_06", "portrait_hero_09", "portrait_hero_11", "portrait_hero_16")),
            HeroClassDef(RANGER, "Ranger", basePower = 14, powerPerLevel = 2, preferredFamilies = listOf(BOW), preferredElement = Element.STORM, startingGoldMin = 50, startingGoldMax = 120, appearances = listOf("portrait_hero_02", "portrait_hero_07", "portrait_hero_12", "portrait_hero_17")),
        ),
        traits = listOf(
            TraitDef(BRAVE, "Brave", expeditionWeight = 1.5, restWeight = -0.5, combatModifier = 1.05),
            TraitDef(CAUTIOUS, "Cautious", patrolWeight = 1.2, restWeight = 0.8, expeditionWeight = -0.6, combatModifier = 0.97),
            TraitDef(GREEDY, "Greedy", expeditionWeight = 0.6, priceSensitivity = 1.6, shopWeight = 0.3, guildWeight = -0.2),
            TraitDef(AMBITIOUS, "Ambitious", expeditionWeight = 1.0, shopWeight = 0.5),
            TraitDef(LOYAL, "Loyal", patrolWeight = 0.8, loyaltyGain = 2.0, shopWeight = 0.4, guildWeight = 0.4),
            TraitDef(CURIOUS, "Curious", shopWeight = 0.6, noveltyTaste = 0.8, guildWeight = 0.4),
            TraitDef(PATIENT, "Patient", priceSensitivity = 0.7, restWeight = 0.3, guildWeight = 0.6),
        ),
        factions = listOf(
            FactionDef(
                ASHCLAW, "Ashclaw Raiders", weakTo = Element.FROST, resists = Element.FIRE, dailyGrowth = 6,
                encounterNames = listOf("Ashclaw scouts", "an Ashclaw warband", "Ashclaw foragers", "an Ashclaw brute"),
                siegeName = "Ashclaw horde",
            ),
        ),
        blessings = listOf(
            BlessingDef(FORGEFIRE, "Forgefire", BlessingEffect.QUALITY_BONUS, magnitude = 6, durationDays = 5, description = "Forged quality improves for a few days."),
            BlessingDef(TIRELESS_HANDS, "Tireless Hands", BlessingEffect.EXTRA_ENERGY, magnitude = 2, durationDays = 5, description = "Two extra energy each morning."),
            BlessingDef(MERCHANTS_FAVOR, "Merchant's Favor", BlessingEffect.SALE_GOLD_BONUS, magnitude = 20, durationDays = 5, description = "Sales earn a fifth more gold."),
            BlessingDef(STALWART_TOWN, "Stalwart Town", BlessingEffect.INTEGRITY_RECOVERY, magnitude = 3, durationDays = 5, description = "The town repairs faster."),
            BlessingDef(HUNTERS_EDGE, "Hunter's Edge", BlessingEffect.HERO_POWER, magnitude = 10, durationDays = 5, description = "Heroes fight a tenth stronger."),
        ),
        upgrades = listOf(
            UpgradeDef(UPG_ENERGY, "Tireless Smith", UpgradeEffect.STARTING_ENERGY, magnitudePerLevel = 1, maxLevel = 2, costPerLevel = listOf(8, 20), description = "+1 daily energy per level."),
            UpgradeDef(UPG_GOLD, "Family Savings", UpgradeEffect.STARTING_GOLD, magnitudePerLevel = 100, maxLevel = 2, costPerLevel = listOf(8, 20), description = "+100 starting gold per level."),
            UpgradeDef(UPG_MASTERY, "Forge Mastery", UpgradeEffect.QUALITY_BONUS, magnitudePerLevel = 4, maxLevel = 3, costPerLevel = listOf(8, 20, 45), description = "+4 crafted quality per level."),
            UpgradeDef(UPG_WALLS, "Stalwart Walls", UpgradeEffect.STARTING_INTEGRITY, magnitudePerLevel = 20, maxLevel = 2, costPerLevel = listOf(20, 45), description = "+20 starting forge integrity per level."),
        ),
        coreAugmentAffinity = mapOf(
            (IRON to EMBER_RESIN) to 6, (IRON to FROST_BLOOM) to 2, (IRON to STORMGLASS) to -2,
            (BRONZE to EMBER_RESIN) to 3, (BRONZE to FROST_BLOOM) to 6, (BRONZE to STORMGLASS) to 1,
            (SILVER to EMBER_RESIN) to -2, (SILVER to FROST_BLOOM) to 3, (SILVER to STORMGLASS) to 8,
        ),
        augmentFamilyAffinity = mapOf(
            (EMBER_RESIN to AXE) to 4, (EMBER_RESIN to SWORD) to 2, (EMBER_RESIN to BOW) to 0,
            (FROST_BLOOM to SWORD) to 4, (FROST_BLOOM to AXE) to 1, (FROST_BLOOM to BOW) to 2,
            (STORMGLASS to BOW) to 5, (STORMGLASS to SWORD) to 2, (STORMGLASS to AXE) to -1,
        ),
        firstNames = listOf("Mira", "Aldric", "Tessa", "Bram", "Ione", "Corvin", "Sable", "Edric", "Wren", "Halvard", "Nerys", "Orin", "Liora", "Garrick", "Dagny"),
        surnames = listOf("Oakhurst", "Thornefell", "Greymantle", "Vance", "Coldwater", "Stonebrook", "Ferris", "Nightingale", "Marrow", "Kestrel"),
    )
}
