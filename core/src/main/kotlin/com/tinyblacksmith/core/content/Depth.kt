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
        EncounterDef(LAST_CRATE, "The Last Crate", 3.0, maxPerRun = 6, cooldownDays = 3, description = "A carter has one crate left before the roads close for the siege."),
        EncounterDef(BLADE_FOR_THE_WALL, "A Blade for the Wall", 2.5, maxPerRun = 4, cooldownDays = 4, description = "A defender with a thin purse asks for a blade; a richer patron waits behind them."),
        EncounterDef(MASTERS_AFTERNOON, "The Master's Afternoon", 2.0, maxPerRun = 2, cooldownDays = 5, description = "A wandering master smith will teach, for an afternoon of your time or for coin.", replacesEvent = "wandering_master"),
        EncounterDef(COLLECTORS_OFFER, "The Collector's Offer", 2.0, maxPerRun = 2, cooldownDays = 5, description = "A collector has heard of one of your blades and names a price.", replacesEvent = "collector"),
        EncounterDef(CRACKED_FAMILY_BLADE, "A Cracked Family Blade", 4.0, maxPerRun = 3, cooldownDays = 3, description = "Someone close to a fallen hero asks after the blade that came back without them."),
        EncounterDef(CROOKED_MERCHANT, "The Crooked Merchant", 1.5, maxPerRun = 3, cooldownDays = 5, description = "A merchant with a quick smile sells a blade cheap and will not say why.", minDay = 4),
        EncounterDef(SMITHS_WAGER, "The Smith's Wager", 1.5, maxPerRun = 3, cooldownDays = 6, description = "A rival smith passing through wagers you cannot make what he names in three days.", minDay = 3),
        EncounterDef(FESTIVAL_CONTRACT, "The Festival Contract", 2.0, maxPerRun = 4, cooldownDays = 4, description = "The town council is planning a festival and wants the forge for it, one way or the other.", replacesEvent = "merchant_festival"),
        EncounterDef(DEBT_REPAID, "The Debt Repaid", 0.0, maxPerRun = Int.MAX_VALUE, cooldownDays = 0, description = "The defender you armed on trust is back at the forge.", followUp = true),
    )

    val relics = listOf(
        RelicDef(SALVAGERS_CRUCIBLE, "Salvager's Crucible", RelicEffect.SALVAGE_AUGMENT, "Once a day, melting down a fine blade also gives its augment back."),
        RelicDef(TEMPERING_LEDGER, "Tempering Ledger", RelicEffect.FAMILY_STREAK, "Each blade of a family not yet in the streak is forged better; repeating one starts the streak again."),
        RelicDef(COLLECTORS_SEAL, "Collector's Seal", RelicEffect.PREMIUM_SEAL, "The first costly shelf sale of a day earns a seal; three seals bring a rare material."),
        RelicDef(ASHEN_BELLOWS, "Ashen Bellows", RelicEffect.BELLOWS, "Once a day one forge may burn tomorrow's strength for an extra property."),
    ) + CombatContent.relicEffects.keys.map { id ->
        // A guild relic is a rule of the party in a fight; its text is the rule's own, so the card and the resolver cannot disagree.
        val rules = CombatContent.relicEffects.getValue(id)
        RelicDef(id, rules.firstOrNull()?.name ?: "Coward's Medal", RelicEffect.COMBAT,
            rules.joinToString(" ") { it.description }.ifEmpty { "A party that pulls back from a fight still brings home half of the bonus it was promised. A retreat is never counted as a victory." })
    } + listOf<RelicDef>(
    )

    // Catalog numbers, PROPOSED.
    val siegeTraits = listOf(
        SiegeTraitDef(LONG_ASSAULT, "Long Assault", "The fighting will last till dawn: worn blades count for much less, and every blade on the wall wears twice as fast.",
            "Put fresh blades in the defenders' hands, or arm the watch: its arms do not tire.", conditionFloorFactor = 0.6, wearMultiplier = 2.0),
        SiegeTraitDef(MANY_BREACHES, "Many Breaches", "They will come at every gate at once: the watch and the militia count for far more, and the raid is stronger.",
            "Give blades to the town watch, even plain ones, or arm the champions well enough to hold regardless.", watchMultiplier = 1.75, raidMultiplier = 1.05),
    )
}
