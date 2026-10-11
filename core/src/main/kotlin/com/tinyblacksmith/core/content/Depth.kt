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

    const val SALVAGERS_CRUCIBLE = "salvagers_crucible"
    const val TEMPERING_LEDGER = "tempering_ledger"
    const val COLLECTORS_SEAL = "collectors_seal"
    const val ASHEN_BELLOWS = "ashen_bellows"

    const val LONG_ASSAULT = "long_assault"
    const val MANY_BREACHES = "many_breaches"

    // Weights, limits and cooldowns are PROPOSED.
    val encounters = listOf(
        EncounterDef(LAST_CRATE, "The Last Crate", 3.0, maxPerRun = 6, cooldownDays = 3, description = "One crate left before the siege closes the roads."),
        EncounterDef(BLADE_FOR_THE_WALL, "A Weapon for the Wall", 2.5, maxPerRun = 4, cooldownDays = 4, description = "A defender needs a weapon on credit. A wealthier customer is next in line."),
        EncounterDef(MASTERS_AFTERNOON, "The Master's Afternoon", 2.0, maxPerRun = 2, cooldownDays = 5, description = "A visiting master smith offers a lesson for time or gold.", replacesEvent = "wandering_master"),
        EncounterDef(COLLECTORS_OFFER, "The Collector's Offer", 2.0, maxPerRun = 2, cooldownDays = 5, description = "A collector wants one of your weapons and has made an offer.", replacesEvent = "collector"),
        EncounterDef(CRACKED_FAMILY_BLADE, "A Cracked Heirloom", 4.0, maxPerRun = 3, cooldownDays = 3, description = "Someone close to a fallen hero wants their weapon restored."),
        EncounterDef(CROOKED_MERCHANT, "The Crooked Merchant", 1.5, maxPerRun = 3, cooldownDays = 5, description = "A merchant offers a cheap weapon. There's a catch.", minDay = 4),
        EncounterDef(SMITHS_WAGER, "The Smith's Wager", 1.5, maxPerRun = 3, cooldownDays = 6, description = "A visiting smith bets you can't meet his challenge in time.", minDay = 3),
        EncounterDef(FESTIVAL_CONTRACT, "The Festival Contract", 2.0, maxPerRun = 4, cooldownDays = 4, description = "The council wants your help with the festival or the town watch.", replacesEvent = "merchant_festival"),
        EncounterDef(DEBT_REPAID, "The Debt Repaid", 0.0, maxPerRun = Int.MAX_VALUE, cooldownDays = 0, description = "The defender you armed on credit has returned.", followUp = true),
    )

    val relics = listOf(
        RelicDef(SALVAGERS_CRUCIBLE, "Salvager's Crucible", RelicEffect.SALVAGE_AUGMENT, "Once daily, salvage a fine weapon to recover its augment as well as its metal."),
        RelicDef(TEMPERING_LEDGER, "Tempering Ledger", RelicEffect.FAMILY_STREAK, "Forge different weapon types in a row to gain quality. Repeating a type resets the streak."),
        RelicDef(COLLECTORS_SEAL, "Collector's Seal", RelicEffect.PREMIUM_SEAL, "Your first qualifying shelf sale each day earns a seal. Collect three for a rare material."),
        RelicDef(ASHEN_BELLOWS, "Ashen Bellows", RelicEffect.BELLOWS, "Once daily, borrow energy from tomorrow to add a property to one forge."),
    ) + CombatContent.relicEffects.keys.map { id ->
        // A guild relic is a rule of the party in a fight; its text is the rule's own, so the card and the resolver cannot disagree.
        val rules = CombatContent.relicEffects.getValue(id)
        RelicDef(id, rules.firstOrNull()?.name ?: "Coward's Medal", RelicEffect.COMBAT,
            rules.joinToString(" ") { it.description }.ifEmpty { "A party that pulls back from a fight still brings home half of the bonus it was promised. A retreat is never counted as a victory." })
    } + listOf<RelicDef>(
    )

    // Catalog numbers, PROPOSED.
    val siegeTraits = listOf(
        SiegeTraitDef(LONG_ASSAULT, "Long Assault", "A long fight favors fresh weapons. Worn weapons contribute less, and defenders' weapons wear twice as fast.",
            "Stock fresh weapons for the defenders or arm the watch. Watch weapons don't wear out.", conditionFloorFactor = 0.6, wearMultiplier = 2.0),
        SiegeTraitDef(MANY_BREACHES, "Many Breaches", "Multiple gates are under attack. The watch and militia contribute much more, but the raid is stronger too.",
            "Donate weapons to the watch or improve the champions' gear. Plain weapons help too.", watchMultiplier = 1.75, raidMultiplier = 1.05),
    )
}
