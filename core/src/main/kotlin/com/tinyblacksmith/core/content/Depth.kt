package com.tinyblacksmith.core.content

/**
 * A morning visitor (`engine.Encounters` holds its eligibility and effects by [id]). [replacesEvent] names the automatic
 * world event this encounter took the place of: that event no longer fires by itself in a catalog that carries the
 * encounter, and both count against the event's limits under the event's ID.
 */
data class EncounterDef(
    val id: String,
    val name: String,
    val weight: Double,
    val maxPerRun: Int,
    val cooldownDays: Int,
    val description: String,
    val minDay: Int = 2,
    val replacesEvent: String? = null,
    /** Only ever scheduled by an earlier choice, never drawn for a morning. */
    val followUp: Boolean = false,
)

/** COMBAT: a party rule of the interaction engine (`CombatCatalog.relicEffects`); offered only in a guild run. */
enum class RelicEffect { SALVAGE_AUGMENT, FAMILY_STREAK, PREMIUM_SEAL, BELLOWS, COMBAT }

/** A run-long workshop relic; its numbers are in `BalanceConfig.depth`. */
data class RelicDef(val id: String, val name: String, val effect: RelicEffect, val description: String)

/**
 * One announced property of a siege, applied in `Battle.outlook` (so the forecast and the resolver agree).
 * [conditionFloorFactor] replaces `BalanceConfig.conditionFloorFactor` for the champions' defense when set.
 */
data class SiegeTraitDef(
    val id: String,
    val name: String,
    val description: String,
    /** What the smith can do about it, shown with the forecast. */
    val counsel: String,
    val conditionFloorFactor: Double? = null,
    val wearMultiplier: Double = 1.0,
    val watchMultiplier: Double = 1.0,
    val raidMultiplier: Double = 1.0,
    /** Drawn in a guild run too. A trait whose numbers only the classic, scalar siege reads (`Battle.outlook`) is false. */
    val guild: Boolean = true,
)

object Depth {
    const val LAST_CRATE = "last_crate"
    const val BLADE_FOR_THE_WALL = "blade_for_the_wall"
    const val MASTERS_AFTERNOON = "masters_afternoon"
    const val COLLECTORS_OFFER = "collectors_offer"
    const val CRACKED_FAMILY_BLADE = "cracked_family_blade"
    const val CROOKED_MERCHANT = "crooked_merchant"
    const val SMITHS_WAGER = "smiths_wager"
    const val FESTIVAL_CONTRACT = "festival_contract"
    const val DEBT_REPAID = "debt_repaid"
    /** Rules 7: the caravan master after the first siege (`engine.DeepRoad`). */
    const val DEEP_ROAD = "deep_road"

    const val SALVAGERS_CRUCIBLE = "salvagers_crucible"
    const val TEMPERING_LEDGER = "tempering_ledger"
    const val COLLECTORS_SEAL = "collectors_seal"
    const val ASHEN_BELLOWS = "ashen_bellows"

    const val LONG_ASSAULT = "long_assault"
    const val MANY_BREACHES = "many_breaches"
    const val HUNGRY_HORDE = "hungry_horde"
    const val NIGHT_ASSAULT = "night_assault"

    // Weights, limits and cooldowns are PROPOSED.
    val encounters = listOf(
        EncounterDef(LAST_CRATE, "The Last Crate", 3.0, maxPerRun = 6, cooldownDays = 3, description = "A carter has one last crate before the siege closes the roads."),
        EncounterDef(BLADE_FOR_THE_WALL, "A Weapon for the Wall", 2.5, maxPerRun = 4, cooldownDays = 4, description = "A defender asks for a weapon on credit. A richer customer wants an order too."),
        EncounterDef(MASTERS_AFTERNOON, "The Master's Afternoon", 2.0, maxPerRun = 2, cooldownDays = 5, description = "A visiting master smith offers a lesson for energy or a recipe clue for gold.", replacesEvent = "wandering_master"),
        EncounterDef(COLLECTORS_OFFER, "The Collector's Offer", 2.0, maxPerRun = 2, cooldownDays = 5, description = "A collector wants one of your weapons and has made an offer.", replacesEvent = "collector"),
        EncounterDef(CRACKED_FAMILY_BLADE, "A Cracked Heirloom", 4.0, maxPerRun = 3, cooldownDays = 3, description = "Someone close to a fallen hero wants the hero's weapon restored."),
        EncounterDef(CROOKED_MERCHANT, "The Crooked Merchant", 1.5, maxPerRun = 3, cooldownDays = 5, description = "A merchant offers a cheap weapon with one hidden flaw.", minDay = 4),
        EncounterDef(SMITHS_WAGER, "The Smith's Wager", 1.5, maxPerRun = 3, cooldownDays = 6, description = "A visiting smith bets you can't forge a superb weapon of a given type in time.", minDay = 3),
        EncounterDef(FESTIVAL_CONTRACT, "The Festival Contract", 2.0, maxPerRun = 4, cooldownDays = 4, description = "The council wants your help with the festival or the town watch.", replacesEvent = "merchant_festival"),
        EncounterDef(DEBT_REPAID, "The Debt Repaid", 0.0, maxPerRun = Int.MAX_VALUE, cooldownDays = 0, description = "The defender you armed on credit has come back to settle the debt.", followUp = true),
        EncounterDef(DEEP_ROAD, "The Deep Road", 0.0, maxPerRun = 1, cooldownDays = 0, description = "After the siege, the caravan master can bring rare stock down the deep road.", followUp = true),
    )

    val relics = listOf(
        RelicDef(SALVAGERS_CRUCIBLE, "Salvager's Crucible", RelicEffect.SALVAGE_AUGMENT, "Once per day, salvaging a weapon of fine quality or better also gives back its augment, not only its metal."),
        RelicDef(TEMPERING_LEDGER, "Tempering Ledger", RelicEffect.FAMILY_STREAK, "Forge a different weapon type each time to build a streak. The longer the streak, the more quality the next new type gains. Forging a type already in the streak starts a new streak with it."),
        RelicDef(COLLECTORS_SEAL, "Collector's Seal", RelicEffect.PREMIUM_SEAL, "Once per day, your first shelf sale at the going rate or higher earns a seal, if the buyer pays enough in coin (trade-ins don't count). Every three seals give the forge one rare material."),
        RelicDef(ASHEN_BELLOWS, "Ashen Bellows", RelicEffect.BELLOWS, "Once per day, one forge can borrow energy from tomorrow to give the weapon one more property. You start tomorrow with that much less energy."),
    ) + CombatContent.relicEffects.keys.map { id ->
        // A guild relic is a rule of the party in a fight; its text is the rule's own, so the card and the resolver cannot disagree.
        val rules = CombatContent.relicEffects.getValue(id)
        RelicDef(id, rules.firstOrNull()?.name ?: "Coward's Medal", RelicEffect.COMBAT,
            rules.joinToString(" ") { it.description }.ifEmpty { "When your party retreats from a quest fight, it still brings home half of that stage's gold. A retreat never counts as a win." })
    } + listOf<RelicDef>(
    )

    // Catalog numbers, PROPOSED. A description states its trait's numbers: change both together.
    val siegeTraits = listOf(
        SiegeTraitDef(LONG_ASSAULT, "Long Assault", "In a long fight, worn weapons count for less than usual, and the defenders' weapons lose twice as much condition.",
            "Get weapons in good condition into the likely defenders' hands, or give weapons to the town watch. The watch's strength does not depend on condition.", conditionFloorFactor = 0.6, wearMultiplier = 2.0),
        SiegeTraitDef(MANY_BREACHES, "Many Breaches", "With several gates under attack, the militia and the armory count 75% more, but the attackers are 5% stronger.",
            "Give weapons to the town watch, or get better weapons into the likely defenders' hands. Even plain weapons help the watch.", watchMultiplier = 1.75, raidMultiplier = 1.05),
        // Review 2026-10-14 (proposals P9, P10): two traits that change what counts instead of only making the siege harder.
        SiegeTraitDef(HUNGRY_HORDE, "Hungry Horde", "A ragged horde, hungrier than it is strong. The attackers are weaker, but worn weapons count for little against them.",
            "Repair or replace the likely defenders' worn weapons. A weapon in good condition counts for more than a strong worn one.", conditionFloorFactor = 0.4, raidMultiplier = 0.85, guild = false),
        SiegeTraitDef(NIGHT_ASSAULT, "Night Assault", "They come over the wall at night. The town watch and the militia are not at their posts, so only the defenders fight. The attackers are fewer.",
            "Get your best weapons into the likely defenders' hands. Weapons given to the town watch won't help this time.", watchMultiplier = 0.0, raidMultiplier = 0.9, guild = false),
    )
}
