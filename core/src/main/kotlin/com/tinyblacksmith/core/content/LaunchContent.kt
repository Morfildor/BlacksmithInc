package com.tinyblacksmith.core.content

import com.tinyblacksmith.core.model.*

/**
 * Launch content at the LOCKED counts (GDD 4.1, 4.2, 6, 8, 9, 10): 6 families, 16 materials, 5 classes, 3 factions,
 * 12 beneficial affixes, 6 flaws, 8 blessings, 11 upgrade tracks. Names are PROPOSED (GDD 4.2); IDs are stable.
 * Every ID, value and affinity that also exists in [SliceContent] is kept identical so saves, legacy profiles and
 * the persistent Experiment Journal stay valid. Numbers without GDD guidance are marked PROPOSED inline.
 * The engine default since balance v2; [SliceContent] remains for slice-specific tests.
 */
object LaunchContent {
    val SWORD = WeaponFamilyId("sword")
    val AXE = WeaponFamilyId("axe")
    val SPEAR = WeaponFamilyId("spear")
    val BOW = WeaponFamilyId("bow")
    val DAGGER = WeaponFamilyId("dagger")
    val STAFF = WeaponFamilyId("staff")

    val IRON = MaterialId("iron")
    val BRONZE = MaterialId("bronze")
    val SILVER = MaterialId("silver")
    val OBSIDIAN = MaterialId("obsidian")
    val STARSTEEL = MaterialId("starsteel")
    val MOONSTEEL = MaterialId("moonsteel")
    val EMBER_RESIN = MaterialId("ember_resin")
    val FROST_BLOOM = MaterialId("frost_bloom")
    val STORMGLASS = MaterialId("stormglass")
    val GRAVE_DUST = MaterialId("grave_dust")
    val VERDANT_SAP = MaterialId("verdant_sap")
    val SUN_ASH = MaterialId("sun_ash")
    val BINDING_SALT = MaterialId("binding_salt")
    val RUNESTONE_SHARD = MaterialId("runestone_shard")
    val DRAGON_OIL = MaterialId("dragon_oil")
    val VOID_INK = MaterialId("void_ink")

    val GUARDIAN = HeroClassId("guardian")
    val RANGER = HeroClassId("ranger")
    val DUELIST = HeroClassId("duelist")
    val BATTLEMAGE = HeroClassId("battlemage")
    val WARDEN = HeroClassId("warden")

    val BRAVE = TraitId("brave")
    val CAUTIOUS = TraitId("cautious")
    val GREEDY = TraitId("greedy")
    val AMBITIOUS = TraitId("ambitious")
    val LOYAL = TraitId("loyal")
    val CURIOUS = TraitId("curious")
    val PATIENT = TraitId("patient")
    val VAIN = TraitId("vain")
    val RESTLESS = TraitId("restless")

    val ASHCLAW = FactionId("ashclaw_raiders")
    val HOLLOWBOUND = FactionId("hollowbound")
    val EMBERMAW = FactionId("embermaw_brood")

    val KEEN = AffixId("keen")
    val REINFORCED = AffixId("reinforced")
    val FLAMING = AffixId("flaming")
    val FROSTBOUND = AffixId("frostbound")
    val STORMCHARGED = AffixId("stormcharged")
    val VAMPIRIC = AffixId("vampiric")
    val SWIFT = AffixId("swift")
    val GIANT_SLAYER = AffixId("giant_slayer")
    val UNDEAD_BANE = AffixId("undead_bane")
    val GUARDIANS = AffixId("guardians")
    val LUCKY = AffixId("lucky")
    val RESONANT = AffixId("resonant")
    val BRITTLE = AffixId("brittle")
    val HEAVY = AffixId("heavy")
    val UNSTABLE = AffixId("unstable")
    val CURSED = AffixId("cursed")
    val BLOODBOUND = AffixId("bloodbound")
    val SENTIENT = AffixId("sentient")

    val FORGEFIRE = BlessingId("forgefire")
    val TIRELESS_HANDS = BlessingId("tireless_hands")
    val MERCHANTS_FAVOR = BlessingId("merchants_favor")
    val RUNIC_INSIGHT = BlessingId("runic_insight")
    val STALWART_TOWN = BlessingId("stalwart_town")
    val HUNTERS_EDGE = BlessingId("hunters_edge")
    val LUCKY_ALLOY = BlessingId("lucky_alloy")
    val GUILD_PATRONAGE = BlessingId("guild_patronage")

    val UPG_ENERGY = UpgradeId("starting_energy")
    val UPG_GOLD = UpgradeId("starting_gold")
    val UPG_MASTERY = UpgradeId("forge_mastery")
    val UPG_WALLS = UpgradeId("stalwart_walls")
    val UPG_EFFICIENCY = UpgradeId("material_efficiency")
    val UPG_STOCK = UpgradeId("starting_stock")
    val UPG_LUCK = UpgradeId("lucky_hammer")
    val UPG_REPUTATION = UpgradeId("shop_reputation")
    val UPG_CATALOG = UpgradeId("catalog_access")
    val UPG_RECIPES = UpgradeId("recipe_odds")
    val UPG_ARTIFACTS = UpgradeId("legacy_artifacts")

    /** GDD 9 PROPOSED early upgrade tiers. */
    private val TIER_COSTS = listOf(8, 20, 45)

    // GDD 4.1 synergy table. PROPOSED: listed class 1.2, any other class 0.85 (just above BalanceConfig.offFamilyFit).
    private val ALL_CLASSES = listOf(GUARDIAN, RANGER, DUELIST, BATTLEMAGE, WARDEN)
    private fun fit(synergy: List<HeroClassId>): Map<HeroClassId, Double> =
        ALL_CLASSES.associateWith { if (it in synergy) 1.2 else 0.85 }

    val catalog: ContentCatalog = ContentCatalog(
        version = 3,
        families = listOf(
            // basePower / defensiveWeight are PROPOSED; Sword, Axe and Bow keep their slice values.
            WeaponFamilyDef(SWORD, "Sword", basePower = 10, classFit = fit(listOf(GUARDIAN, DUELIST, BATTLEMAGE)), defensiveWeight = 1.1),
            WeaponFamilyDef(AXE, "Axe", basePower = 12, classFit = fit(listOf(GUARDIAN, WARDEN)), defensiveWeight = 1.0),
            WeaponFamilyDef(SPEAR, "Spear", basePower = 10, classFit = fit(listOf(GUARDIAN, WARDEN)), defensiveWeight = 1.15),
            WeaponFamilyDef(BOW, "Bow", basePower = 9, classFit = fit(listOf(RANGER)), defensiveWeight = 0.9),
            WeaponFamilyDef(DAGGER, "Dagger", basePower = 8, classFit = fit(listOf(DUELIST, RANGER)), defensiveWeight = 0.8),
            WeaponFamilyDef(STAFF, "Staff", basePower = 9, classFit = fit(listOf(BATTLEMAGE, WARDEN)), defensiveWeight = 1.0),
        ),
        materials = listOf(
            // Cores, tiers 1-6. Prices and rare-stock limits above Silver are PROPOSED extrapolations of the slice curve.
            MaterialDef(IRON, "Iron", MaterialCategory.CORE, tier = 1, price = 10, flavor = "Honest grey metal."),
            MaterialDef(BRONZE, "Bronze", MaterialCategory.CORE, tier = 2, price = 22, flavor = "Warm alloy that holds an edge."),
            MaterialDef(SILVER, "Silver", MaterialCategory.CORE, tier = 3, price = 45, dailySupplierStock = 1, flavor = "Bright, rare, hungry for augments."),
            MaterialDef(OBSIDIAN, "Obsidian", MaterialCategory.CORE, tier = 4, price = 75, dailySupplierStock = 1, flavor = "Volcanic glass; keen, but it remembers the fire."),
            MaterialDef(STARSTEEL, "Starsteel", MaterialCategory.CORE, tier = 5, price = 120, dailySupplierStock = 1, flavor = "Fallen from the sky, still faintly warm."),
            MaterialDef(MOONSTEEL, "Moonsteel", MaterialCategory.CORE, tier = 6, price = 180, dailySupplierStock = 1, flavor = "Pale metal that is cold even at the forge."),
            // Augments. Tiers for Grave Dust, Verdant Sap and Sun Ash are PROPOSED (Verdant common, Sun Ash the rarest).
            MaterialDef(EMBER_RESIN, "Ember Resin", MaterialCategory.AUGMENT, tier = 1, price = 8, element = Element.FIRE, flavor = "Smoulders when struck."),
            MaterialDef(FROST_BLOOM, "Frost Bloom", MaterialCategory.AUGMENT, tier = 2, price = 18, element = Element.FROST, flavor = "Petals that never thaw."),
            MaterialDef(VERDANT_SAP, "Verdant Sap", MaterialCategory.AUGMENT, tier = 2, price = 16, element = Element.VERDANT, flavor = "Thick green sap that knits wood and flesh alike."),
            MaterialDef(STORMGLASS, "Stormglass", MaterialCategory.AUGMENT, tier = 3, price = 40, element = Element.STORM, dailySupplierStock = 1, flavor = "Hums before a storm."),
            MaterialDef(GRAVE_DUST, "Grave Dust", MaterialCategory.AUGMENT, tier = 4, price = 55, element = Element.GRAVE, dailySupplierStock = 1, flavor = "Swept from old tombs; it drinks warmth."),
            MaterialDef(SUN_ASH, "Sun Ash", MaterialCategory.AUGMENT, tier = 5, price = 85, element = Element.SUN, dailySupplierStock = 1, flavor = "Ash from a fire that burned at noon; the dead shun it."),
            // Catalysts. The engine currently treats every catalyst alike (Forge.kt); identities are for Advanced Forge techniques.
            MaterialDef(BINDING_SALT, "Binding Salt", MaterialCategory.CATALYST, tier = 1, price = 15, dailySupplierStock = 2, flavor = "Steadies the forge. Some recipes ask for something to bind them."),
            MaterialDef(RUNESTONE_SHARD, "Runestone Shard", MaterialCategory.CATALYST, tier = 2, price = 30, dailySupplierStock = 1, flavor = "Steadies the forge. Some recipes ask for a word cut into them."),
            MaterialDef(DRAGON_OIL, "Dragon Oil", MaterialCategory.CATALYST, tier = 3, price = 55, dailySupplierStock = 1, flavor = "Steadies the forge. Some recipes ask for a hotter fire."),
            MaterialDef(VOID_INK, "Void Ink", MaterialCategory.CATALYST, tier = 4, price = 90, dailySupplierStock = 1, flavor = "Steadies the forge. Some recipes ask for a rule rewritten."),
        ),
        affixes = listOf(
            // Beneficial (12). Exactly one per element: Forge.kt picks the first beneficial affix matching the augment element.
            // Multipliers are PROPOSED and bounded (0.85..1.15); the engine has no faction-specific or luck effects yet, so
            // Undead Bane, Giant Slayer and Lucky are flat multipliers for now.
            AffixDef(KEEN, "Keen", AffixKind.BENEFICIAL, power = 3, description = "Sharper than it looks.", attackMultiplier = 1.06),
            AffixDef(REINFORCED, "Reinforced", AffixKind.BENEFICIAL, power = 2, description = "Hard to break; shields its wielder in a rout.", defenseMultiplier = 1.1, damageTakenMultiplier = 0.7),
            AffixDef(FLAMING, "Flaming", AffixKind.BENEFICIAL, power = 4, element = Element.FIRE, description = "Burns foes; raiders fear it.", attackMultiplier = 1.08),
            AffixDef(FROSTBOUND, "Frostbound", AffixKind.BENEFICIAL, power = 4, element = Element.FROST, description = "Slows enemies.", attackMultiplier = 1.05, defenseMultiplier = 1.05),
            AffixDef(STORMCHARGED, "Stormcharged", AffixKind.BENEFICIAL, power = 5, element = Element.STORM, description = "Crackles with lightning.", attackMultiplier = 1.1),
            AffixDef(VAMPIRIC, "Vampiric", AffixKind.BENEFICIAL, power = 4, element = Element.GRAVE, description = "Drinks the strength of what it cuts; mends its wielder after a victory.", attackMultiplier = 1.06, defenseMultiplier = 1.04, healOnWin = 12),
            AffixDef(SWIFT, "Swift", AffixKind.BENEFICIAL, power = 3, description = "Light in the hand, quick to strike and quicker to retreat.", attackMultiplier = 1.05, defenseMultiplier = 1.03, damageTakenMultiplier = 0.6),
            AffixDef(GIANT_SLAYER, "Giant Slayer", AffixKind.BENEFICIAL, power = 5, description = "Bites deepest into elites and warlords.", attackMultiplier = 1.03, eliteMultiplier = 1.5),
            AffixDef(UNDEAD_BANE, "Undead Bane", AffixKind.BENEFICIAL, power = 4, element = Element.SUN, description = "Noon-light folded into steel; the Hollowbound cannot bear it.", attackMultiplier = 1.03, baneFaction = HOLLOWBOUND, baneMultiplier = 1.2),
            AffixDef(GUARDIANS, "Guardian's", AffixKind.BENEFICIAL, power = 2, description = "Steadies the wielder in defense.", defenseMultiplier = 1.15),
            AffixDef(LUCKY, "Lucky", AffixKind.BENEFICIAL, power = 2, description = "Things go its wielder's way; spoils turn up more often, and better ones.", attackMultiplier = 1.03, defenseMultiplier = 1.03, lootChanceBonus = 0.25, scarceLoot = true),
            AffixDef(RESONANT, "Resonant", AffixKind.BENEFICIAL, power = 4, element = Element.VERDANT, description = "Hums with living sap; magic flows through it.", attackMultiplier = 1.04, defenseMultiplier = 1.06),
            // Flaws (6). GDD 4.4: predictable trade-offs, no secret behaviour. Power values are PROPOSED.
            AffixDef(BRITTLE, "Brittle", AffixKind.FLAW, power = -3, description = "Chips under stress and may shatter in a rout.", defenseMultiplier = 0.9, breakChanceOnLoss = 0.2),
            AffixDef(HEAVY, "Heavy", AffixKind.FLAW, power = -2, description = "Tires the wielder; slow to get clear of a lost fight.", attackMultiplier = 0.92, damageTakenMultiplier = 1.5),
            AffixDef(UNSTABLE, "Unstable", AffixKind.FLAW, power = -4, description = "Unreliable in a pinch.", attackMultiplier = 0.9),
            AffixDef(CURSED, "Cursed", AffixKind.FLAW, power = -2, description = "Strikes harder, guards worse, and bites the hand that wins with it.", attackMultiplier = 1.08, defenseMultiplier = 0.85, selfDamageOnWin = 15),
            AffixDef(BLOODBOUND, "Bloodbound", AffixKind.FLAW, power = -3, description = "Feeds on its wielder: fierce on the attack, weak when holding the line.", attackMultiplier = 1.05, defenseMultiplier = 0.9, selfDamageOnWin = 20),
            AffixDef(SENTIENT, "Sentient", AffixKind.FLAW, power = -1, description = "Has opinions. Fights well for its home, balks on the road.", attackMultiplier = 0.94, defenseMultiplier = 1.05),
        ),
        classes = listOf(
            // Guardian and Ranger keep slice values. Base power, gold ranges and preferred elements of the new classes are PROPOSED.
            HeroClassDef(GUARDIAN, "Guardian", basePower = 16, powerPerLevel = 2, preferredFamilies = listOf(SWORD, AXE, SPEAR), preferredElement = Element.FROST, startingGoldMin = 60, startingGoldMax = 140, appearances = listOf("portrait_hero_01", "portrait_hero_06", "portrait_hero_09", "portrait_hero_11", "portrait_hero_16")),
            HeroClassDef(RANGER, "Ranger", basePower = 14, powerPerLevel = 2, preferredFamilies = listOf(BOW, DAGGER), preferredElement = Element.STORM, startingGoldMin = 50, startingGoldMax = 120, appearances = listOf("portrait_hero_02", "portrait_hero_07", "portrait_hero_12", "portrait_hero_17")),
            HeroClassDef(DUELIST, "Duelist", basePower = 15, powerPerLevel = 2, preferredFamilies = listOf(SWORD, DAGGER), preferredElement = Element.GRAVE, startingGoldMin = 55, startingGoldMax = 130, appearances = listOf("portrait_hero_03", "portrait_hero_10", "portrait_hero_13", "portrait_hero_18")),
            HeroClassDef(BATTLEMAGE, "Battlemage", basePower = 13, powerPerLevel = 3, preferredFamilies = listOf(STAFF, SWORD), preferredElement = Element.FIRE, startingGoldMin = 70, startingGoldMax = 150, appearances = listOf("portrait_hero_04", "portrait_hero_08", "portrait_hero_14", "portrait_hero_19")),
            HeroClassDef(WARDEN, "Warden", basePower = 15, powerPerLevel = 2, preferredFamilies = listOf(SPEAR, AXE, STAFF), preferredElement = Element.VERDANT, startingGoldMin = 50, startingGoldMax = 120, appearances = listOf("portrait_hero_05", "portrait_hero_15", "portrait_hero_20")),
        ),
        traits = listOf(
            TraitDef(BRAVE, "Brave", expeditionWeight = 1.5, restWeight = -0.5, combatModifier = 1.05),
            TraitDef(CAUTIOUS, "Cautious", patrolWeight = 1.2, restWeight = 0.8, expeditionWeight = -0.6, combatModifier = 0.97),
            TraitDef(GREEDY, "Greedy", expeditionWeight = 0.6, priceSensitivity = 1.6, shopWeight = 0.3, guildWeight = -0.2),
            TraitDef(AMBITIOUS, "Ambitious", expeditionWeight = 1.0, shopWeight = 0.5),
            TraitDef(LOYAL, "Loyal", patrolWeight = 0.8, loyaltyGain = 2.0, shopWeight = 0.4, guildWeight = 0.4),
            TraitDef(CURIOUS, "Curious", shopWeight = 0.6, noveltyTaste = 0.8, guildWeight = 0.4),
            TraitDef(PATIENT, "Patient", priceSensitivity = 0.7, restWeight = 0.3, guildWeight = 0.6),
            // PROPOSED additions (GDD 6 "etc."): each shifts an observable decision weight.
            TraitDef(VAIN, "Vain", shopWeight = 0.7, priceSensitivity = 0.6, noveltyTaste = 0.5),
            TraitDef(RESTLESS, "Restless", expeditionWeight = 0.8, patrolWeight = 0.5, restWeight = -0.6, guildWeight = -0.2),
        ),
        factions = listOf(
            // Daily growth is PROPOSED and summed across three factions (4+3+2 = 9/day) against roughly the same hero
            // suppression the slice spent on one faction at 6/day; balance v2 (docs/DECISIONS.md) found 6/5/4 saturated all
            // three at 100 pressure, making every siege unwinnable.
            FactionDef(
                ASHCLAW, "Ashclaw Raiders", weakTo = Element.FROST, resists = Element.FIRE, dailyGrowth = 4,
                encounterNames = listOf("Ashclaw scouts", "an Ashclaw warband", "Ashclaw foragers", "an Ashclaw brute"),
                siegeName = "Ashclaw horde",
                eliteNames = listOf("an Ashclaw warchief", "the Ashclaw beastmaster"), warlordName = "Warlord Krag",
            ),
            // GDD 8: undead/curses with specialized counters. PROPOSED: weak to Sun (not Fire) so the counter stays specialized.
            FactionDef(
                HOLLOWBOUND, "Hollowbound", weakTo = Element.SUN, resists = Element.GRAVE, dailyGrowth = 3,
                encounterNames = listOf("Hollowbound shamblers", "a Hollowbound cortege", "a bone warden", "a gravecaller and its thralls"),
                siegeName = "Hollowbound procession",
                eliteNames = listOf("a Hollowbound lich", "the Pale Knight"), warlordName = "the Hollow King",
            ),
            // GDD 8: fire elites, frost counters. PROPOSED: the slowest growth (fewer, stronger foes). Shares Ashclaw's frost weakness by brief.
            FactionDef(
                EMBERMAW, "Embermaw Brood", weakTo = Element.FROST, resists = Element.FIRE, dailyGrowth = 2,
                encounterNames = listOf("Embermaw whelps", "an Embermaw drake", "Embermaw cinder-knights", "an Embermaw matriarch"),
                siegeName = "Embermaw Brood",
                eliteNames = listOf("an Embermaw wyrm", "the Cinder Matriarch"), warlordName = "Broodmother Vyrash",
            ),
        ),
        blessings = listOf(
            BlessingDef(FORGEFIRE, "Forgefire", BlessingEffect.QUALITY_BONUS, magnitude = 6, durationDays = 5, description = "Forged quality improves for a few days."),
            BlessingDef(TIRELESS_HANDS, "Tireless Hands", BlessingEffect.EXTRA_ENERGY, magnitude = 2, durationDays = 5, description = "Two extra energy each morning."),
            BlessingDef(MERCHANTS_FAVOR, "Merchant's Favor", BlessingEffect.SALE_GOLD_BONUS, magnitude = 20, durationDays = 5, description = "Sales earn a fifth more gold."),
            // PROPOSED magnitudes for the three new effects (engine support pending).
            BlessingDef(RUNIC_INSIGHT, "Runic Insight", BlessingEffect.DISCOVERY_BONUS, magnitude = 1, durationDays = 5, description = "Every forge teaches the journal twice as much."),
            BlessingDef(STALWART_TOWN, "Stalwart Town", BlessingEffect.INTEGRITY_RECOVERY, magnitude = 3, durationDays = 5, description = "The town repairs faster."),
            BlessingDef(HUNTERS_EDGE, "Hunter's Edge", BlessingEffect.HERO_POWER, magnitude = 10, durationDays = 5, description = "Heroes fight a tenth stronger."),
            BlessingDef(LUCKY_ALLOY, "Lucky Alloy", BlessingEffect.EXCEPTIONAL_CHANCE, magnitude = 8, durationDays = 5, description = "Exceptional forgings come more often."),
            BlessingDef(GUILD_PATRONAGE, "Guild Patronage", BlessingEffect.GUILD_PATRONAGE, magnitude = 0, durationDays = 5, description = "Guild members come by every day, and their guild pays part of one blade for each of them."),
        ),
        upgrades = listOf(
            // GDD 9 categories, three tiers each at 8/20/45. Slice tracks keep their IDs and per-level magnitudes (levels widened 2 -> 3).
            UpgradeDef(UPG_ENERGY, "Tireless Smith", UpgradeEffect.STARTING_ENERGY, magnitudePerLevel = 1, maxLevel = 3, costPerLevel = TIER_COSTS, description = "+1 daily energy per level."),
            UpgradeDef(UPG_GOLD, "Family Savings", UpgradeEffect.STARTING_GOLD, magnitudePerLevel = 100, maxLevel = 3, costPerLevel = TIER_COSTS, description = "+100 starting gold per level."),
            UpgradeDef(UPG_MASTERY, "Forge Mastery", UpgradeEffect.QUALITY_BONUS, magnitudePerLevel = 4, maxLevel = 3, costPerLevel = TIER_COSTS, description = "+4 crafted quality per level."),
            UpgradeDef(UPG_WALLS, "Stalwart Walls", UpgradeEffect.STARTING_INTEGRITY, magnitudePerLevel = 20, maxLevel = 3, costPerLevel = TIER_COSTS, description = "+20 starting forge integrity per level."),
            // PROPOSED magnitudes for the new effects (engine support pending).
            UpgradeDef(UPG_EFFICIENCY, "Thrifty Hands", UpgradeEffect.MATERIAL_EFFICIENCY, magnitudePerLevel = 10, maxLevel = 3, costPerLevel = TIER_COSTS, description = "Now and then a forge spares its augment, a little more often with each level."),
            UpgradeDef(UPG_STOCK, "Well-Stocked Cellar", UpgradeEffect.STARTING_MATERIALS, magnitudePerLevel = 2, maxLevel = 3, costPerLevel = TIER_COSTS, description = "+2 of every common material at the start of a run, per level."),
            UpgradeDef(UPG_LUCK, "Lucky Hammer", UpgradeEffect.EXCEPTIONAL_CHANCE, magnitudePerLevel = 2, maxLevel = 3, costPerLevel = TIER_COSTS, description = "Exceptional forgings come a little more often with each level."),
            UpgradeDef(UPG_REPUTATION, "Known Name", UpgradeEffect.STARTING_REPUTATION, magnitudePerLevel = 5, maxLevel = 3, costPerLevel = TIER_COSTS, description = "+5 starting shop reputation per level, and one more hero starts as a regular with coin saved for your work."),
            // v5 tracks for the remaining GDD 9 categories (catalog access, recipe odds, legacy artifacts). Names are PROPOSED;
            // each counts levels and takes its numbers from BalanceConfig.legacyTracks.
            UpgradeDef(UPG_CATALOG, "Caravan Ties", UpgradeEffect.CATALOG_ACCESS, magnitudePerLevel = 1, maxLevel = 3, costPerLevel = TIER_COSTS, description = "The supplier keeps more of every rare material in stock each day; a deeper shelf with each level."),
            UpgradeDef(UPG_RECIPES, "Anvil Lore", UpgradeEffect.RECIPE_ODDS, magnitudePerLevel = 1, maxLevel = 3, costPerLevel = TIER_COSTS, description = "A true signature recipe answers the hammer more readily with each level, though never for certain."),
            UpgradeDef(UPG_ARTIFACTS, "Homing Steel", UpgradeEffect.LEGACY_ARTIFACTS, magnitudePerLevel = 1, maxLevel = 3, costPerLevel = TIER_COSTS, description = "Blades on the Legend Board find their way home more often, and less dulled by the years, with each level."),
        ),
        // Hidden affinities (GDD 4.6), range -4..+8; >= 7 reads "excellent affinity" in the journal. Slice pairs unchanged.
        // Each augment has one excellent core: Ember/Obsidian, Frost/Moonsteel, Storm/Silver, Grave/Bronze, Verdant/Iron, Sun/Starsteel.
        coreAugmentAffinity = mapOf(
            (IRON to EMBER_RESIN) to 6, (IRON to FROST_BLOOM) to 2, (IRON to STORMGLASS) to -2, (IRON to GRAVE_DUST) to 3, (IRON to VERDANT_SAP) to 8, (IRON to SUN_ASH) to -3,
            (BRONZE to EMBER_RESIN) to 3, (BRONZE to FROST_BLOOM) to 6, (BRONZE to STORMGLASS) to 1, (BRONZE to GRAVE_DUST) to 8, (BRONZE to VERDANT_SAP) to 4, (BRONZE to SUN_ASH) to 0,
            (SILVER to EMBER_RESIN) to -2, (SILVER to FROST_BLOOM) to 3, (SILVER to STORMGLASS) to 8, (SILVER to GRAVE_DUST) to -1, (SILVER to VERDANT_SAP) to 2, (SILVER to SUN_ASH) to 5,
            (OBSIDIAN to EMBER_RESIN) to 8, (OBSIDIAN to FROST_BLOOM) to -3, (OBSIDIAN to STORMGLASS) to 4, (OBSIDIAN to GRAVE_DUST) to 5, (OBSIDIAN to VERDANT_SAP) to -2, (OBSIDIAN to SUN_ASH) to 2,
            (STARSTEEL to EMBER_RESIN) to 4, (STARSTEEL to FROST_BLOOM) to 1, (STARSTEEL to STORMGLASS) to 6, (STARSTEEL to GRAVE_DUST) to 0, (STARSTEEL to VERDANT_SAP) to 3, (STARSTEEL to SUN_ASH) to 8,
            (MOONSTEEL to EMBER_RESIN) to -1, (MOONSTEEL to FROST_BLOOM) to 8, (MOONSTEEL to STORMGLASS) to 3, (MOONSTEEL to GRAVE_DUST) to 6, (MOONSTEEL to VERDANT_SAP) to 5, (MOONSTEEL to SUN_ASH) to -4,
        ),
        augmentFamilyAffinity = mapOf(
            (EMBER_RESIN to SWORD) to 2, (EMBER_RESIN to AXE) to 4, (EMBER_RESIN to SPEAR) to 1, (EMBER_RESIN to BOW) to 0, (EMBER_RESIN to DAGGER) to 1, (EMBER_RESIN to STAFF) to 3,
            (FROST_BLOOM to SWORD) to 4, (FROST_BLOOM to AXE) to 1, (FROST_BLOOM to SPEAR) to 3, (FROST_BLOOM to BOW) to 2, (FROST_BLOOM to DAGGER) to 0, (FROST_BLOOM to STAFF) to 2,
            (STORMGLASS to SWORD) to 2, (STORMGLASS to AXE) to -1, (STORMGLASS to SPEAR) to 1, (STORMGLASS to BOW) to 5, (STORMGLASS to DAGGER) to 3, (STORMGLASS to STAFF) to 4,
            (GRAVE_DUST to SWORD) to 1, (GRAVE_DUST to AXE) to 0, (GRAVE_DUST to SPEAR) to -1, (GRAVE_DUST to BOW) to 2, (GRAVE_DUST to DAGGER) to 5, (GRAVE_DUST to STAFF) to 3,
            (VERDANT_SAP to SWORD) to 1, (VERDANT_SAP to AXE) to 3, (VERDANT_SAP to SPEAR) to 5, (VERDANT_SAP to BOW) to 1, (VERDANT_SAP to DAGGER) to 0, (VERDANT_SAP to STAFF) to 4,
            (SUN_ASH to SWORD) to 3, (SUN_ASH to AXE) to 2, (SUN_ASH to SPEAR) to 2, (SUN_ASH to BOW) to 1, (SUN_ASH to DAGGER) to -1, (SUN_ASH to STAFF) to 6,
        ),
        // Hero names (plan 4.4): 120 first names and 96 surnames in one shared pool for every folk of Emberfall. The rules they obey
        // are checked by ContentCatalog.validate(). Ashwood, Brackenridge, Holloway, Mossgrave, Rooksbane and Nessa left the pool
        // with content 3; heroes and lineages that carry them keep them, because a stored name needs no pool entry.
        firstNames = listOf(
            "Mira", "Aldric", "Tessa", "Bram", "Ione", "Corvin", "Sable", "Edric", "Wren", "Halvard", "Orin", "Liora",
            "Garrick", "Thane", "Ysolde", "Piet", "Maren", "Dagny", "Rook", "Elspeth", "Torvald", "Cassia", "Faolan", "Greta",
            "Lucan", "Odile", "Sten", "Verity", "Hesper", "Anwen", "Alba", "Brisa", "Bertram", "Cael", "Ceridwen", "Dorrin",
            "Davin", "Evander", "Elowen", "Fenna", "Fintan", "Gideon", "Hedda", "Idris", "Joss", "Keir", "Leof", "Maud",
            "Niall", "Ottilie", "Petra", "Rowan", "Runa", "Saskia", "Tobin", "Ulla", "Varek", "Willa", "Yorick", "Azrek",
            "Arvo", "Averil", "Bodil", "Blix", "Benno", "Brokk", "Crispin", "Cyra", "Durgan", "Dezra", "Eska", "Emrys",
            "Frode", "Ferelith", "Gorrim", "Gundra", "Hakon", "Hilde", "Hobb", "Ilka", "Ivo", "Ixen", "Jorund", "Jessamy",
            "Jovan", "Kazimir", "Katla", "Kolgrim", "Kivi", "Lenka", "Lysander", "Linnet", "Merle", "Mox", "Nerys", "Norrik",
            "Nixie", "Othmar", "Onora", "Pascoe", "Prisca", "Quenna", "Radek", "Rosalind", "Sorrel", "Sigrun", "Selka", "Tamsin",
            "Tansy", "Urien", "Urzog", "Vesna", "Vidar", "Wystan", "Wendel", "Xanthe", "Yarrow", "Zinnia", "Zorka", "Zephyr",
        ),
        // Four kinds, 24 of each.
        surnames = listOf(
            // Nature compounds
            "Stonebrook", "Nightingale", "Kestrel", "Fairweather", "Foxglove", "Gorse", "Juniper", "Larkspur",
            "Orchard", "Birchall", "Yewdale", "Dovecote", "Bracken", "Heronshaw", "Thistledown", "Hazelrigg",
            "Otterbourne", "Meadowsweet", "Hawkridge", "Nettlefold", "Burdock", "Woodruff", "Briarwood", "Hemlock",
            // Places
            "Thornefell", "Coldwater", "Dunmore", "Hartwell", "Oakhurst", "Underhill", "Aldermoor", "Applegarth",
            "Barrowby", "Crowhurst", "Eastmere", "Fenwick", "Harrowgate", "Kettleby", "Norwood", "Ravensworth",
            "Sedgewick", "Stroud", "Umberlow", "Redcliffe", "Longbarrow", "Holmfirth", "Deepdale", "Caldbeck",
            // Trades
            "Quill", "Tallow", "Dray", "Millrace", "Tanner", "Wainwright", "Fletcher", "Cooper",
            "Chandler", "Mercer", "Wheeler", "Weaver", "Collier", "Draper", "Farrier", "Pargeter",
            "Ostler", "Arkwright", "Lorimer", "Falconer", "Hayward", "Harper", "Carver", "Naylor",
            // Old family names
            "Greymantle", "Vance", "Ferris", "Marrow", "Ellery", "Lindqvist", "Pellam", "Cobbett",
            "Ingram", "Penhallow", "Varley", "Whitlock", "Tremaine", "Selwyn", "Wyndham", "Devlin",
            "Isherwood", "Jarvis", "Lovell", "Merrick", "Osgood", "Bellamy", "Everard", "Gresham",
        ),
        // In-run workshop tools (PROPOSED): the gold sink of a run. Costs are per level.
        tools = listOf(
            ToolDef("bellows", "Great Bellows", ToolEffect.EXTRA_ENERGY, magnitudePerLevel = 1, costPerLevel = listOf(300, 700), description = "+1 daily energy per level."),
            ToolDef("whetstone", "Master Whetstone", ToolEffect.QUALITY_BONUS, magnitudePerLevel = 3, costPerLevel = listOf(120, 300), description = "+3 forged quality per level."),
            ToolDef("signboard", "Painted Signboard", ToolEffect.EXTRA_CUSTOMERS, magnitudePerLevel = 1, costPerLevel = listOf(150, 400), description = "+1 customer a day per level."),
            ToolDef("display_case", "Display Case", ToolEffect.SHELF_SLOTS, magnitudePerLevel = 2, costPerLevel = listOf(200), description = "+2 shelf slots."),
        ),
    )
}
