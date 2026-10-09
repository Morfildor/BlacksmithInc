package com.tinyblacksmith.core.model

import kotlinx.serialization.Serializable

/** One transaction at the counter: what was asked, what came back in part payment and what reached the till. */
@Serializable data class Sale(
    val listedPrice: Int? = null,                     // null for a commission blade handed over from storage
    val tradeInCredit: Int = 0, val tradeInWeaponId: WeaponId? = null,
    val cashPaid: Int,                                // coin the customer paid; for a commission, the reward
    val saleBonus: Int = 0, val stipend: Int = 0, val commissionId: CommissionId? = null,
)   // the shop's gold rises by cashPaid + saleBonus + stipend

enum class IncomeKind { SHELF_SALE, SALE_BONUS, STIPEND, COMMISSION, COLLECTOR, TRIBUTE }   // every way End Day adds gold today; a new source adds a constant
@Serializable data class ShopLedger(
    val goldAtOpen: Int, val goldAtClose: Int, val income: Map<IncomeKind, Int>,
    val tradeInCredit: Int, val spentPreparing: Int,
)   // invariant: goldAtOpen + income.values.sum() == goldAtClose

enum class FieldOutcome { WON, DRIVEN_BACK, DIED, PATROLLED, RESTED, GUILD_TRAINED, GUILD_LESSON, GUILD_TAUGHT, AMBITION_DAY, HELD_THE_WALL, FELL_AT_THE_WALL }
/** One hero's day beyond the door. Replaces string-matching on events for tallies and the aftermath. */
@Serializable data class FieldResult(
    val heroId: HeroId, val heroName: String, val outcome: FieldOutcome,
    val foe: String? = null, val elite: Boolean = false, val factionId: FactionId? = null,
    val lostBareHanded: Boolean = false,              // won, and the same recorded roll loses under the same formula with no weapon
    val lostWithOldBlade: Boolean? = null,            // for a blade bought today: the same roll loses with the blade it replaced
    val matchupHelped: Boolean = false,               // the blade's element or bane counted against this foe
    val gold: Int = 0, val materialId: MaterialId? = null,
    val withHeroId: HeroId? = null,                   // the guildmate who taught or was taught (0.6.0 hall lessons)
    val eventIds: List<String> = emptyList(),
)
