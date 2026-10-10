@file:OptIn(ExperimentalSerializationApi::class)

package com.tinyblacksmith.core.model

import com.tinyblacksmith.core.content.Element
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

// The visit record is stored once per day for up to ten visitors, so a field of it that holds its default (nothing, none,
// zero, no) is left out of the save: `@EncodeDefault(NEVER)`. Reading is unaffected, and such a default must never change.

enum class VisitKind { BROWSE, COMMISSION, COLLECTOR }

/** The first nine keep the v1 spellings. M4 appended the last five; constants are only ever appended. */
enum class VisitReason {
    EMPTY_SHELVES, TOO_EXPENSIVE, NOT_BETTER, OVERPRICED, NOT_SUITED, UNDECIDED,     // left
    WORN_OUT, GREAT_FIT, GOOD_ENOUGH,                                                // bought
    COMMISSION_DELIVERED, COLLECTOR_PURCHASE,
    TASTE_MATCH, PRIZED, STORIED,                    // bought a near-equal blade for something their own lacked (once each per hero)
    COUNTERS_THREAT,                                 // bought, in the siege warning, a blade of the element the besieger fears
    RESISTED,                                        // left: the blade they would have taken is of the element the besieger shrugs off
}

/** Facts the counter may state. No utility, weight or chance ever leaves Market (GDD: descriptive only). */
enum class VisitFactor {
    SUITS_CLASS, OFF_CLASS, ELEMENT_TASTE, LIKES_NOVELTY, STRONGER_THAN_OWN, NOT_STRONGER_THAN_OWN, OWN_BLADE_WORN,
    UNARMED, STORIED_BLADE, COLLECTOR_PRIZE, CAN_AFFORD, CANNOT_AFFORD, ABOVE_THEIR_CEILING, REGULAR,
    COUNTERS_THREAT, THREAT_RESISTS,
}

/** A blade as it was at the counter: a copy, so the evening's wear, fame or loss does not rewrite the morning. */
@Serializable data class WeaponSnapshot(
    val weaponId: WeaponId, val name: String, val familyId: WeaponFamilyId, val coreId: MaterialId, val augmentId: MaterialId,
    @EncodeDefault(NEVER) val element: Element? = null, val rarity: Rarity, val quality: Int, val power: Int, val condition: Int,
    @EncodeDefault(NEVER) val fame: Int = 0, @EncodeDefault(NEVER) val title: String? = null, @EncodeDefault(NEVER) val affixes: List<AffixId> = emptyList(),
    @EncodeDefault(NEVER) val flaws: List<AffixId> = emptyList(), @EncodeDefault(NEVER) val signatureId: String? = null,
    @EncodeDefault(NEVER) val dormantAffixes: List<AffixId> = emptyList(),   // a returned legend's sleeping affixes, for the dormant marker
) {
    companion object {
        fun of(w: Weapon) = WeaponSnapshot(w.id, w.name, w.familyId, w.coreId, w.augmentId, w.element, w.rarity, w.quality, w.power, w.condition, w.fame, w.title, w.affixes, w.flaws, w.signatureId, w.dormantAffixes)
    }
}
@Serializable data class CustomerSnapshot(
    val heroId: HeroId, val name: String, val classId: HeroClassId, val level: Int,
    val appearance: String,                           // resolved appearance key (6.6), so a dead hero still has a face
    @EncodeDefault(NEVER) val traits: List<TraitId> = emptyList(), @EncodeDefault(NEVER) val elementTaste: Element? = null, @EncodeDefault(NEVER) val ambition: Ambition? = null,
    val gold: Int, @EncodeDefault(NEVER) val loyalty: Int = 0, @EncodeDefault(NEVER) val regular: Boolean = false, val visitNumber: Int = 1,
    @EncodeDefault(NEVER) val guildId: String? = null, @EncodeDefault(NEVER) val mentorName: String? = null,
    @EncodeDefault(NEVER) val equipped: WeaponSnapshot? = null,             // what they carried when they walked in
)
/** Refers to DayResolution.shopWeapons by ID, so a blade considered by five visitors is stored once. */
@Serializable data class Considered(
    val weaponId: WeaponId, val price: Int, @EncodeDefault(NEVER) val factors: List<VisitFactor> = emptyList(),
    @EncodeDefault(NEVER) val shortBy: Int? = null,                         // gold missing after trade-in credit, when CANNOT_AFFORD
)

/** One transaction at the counter: what was asked, what came back in part payment and what reached the till. */
@Serializable data class Sale(
    @EncodeDefault(NEVER) val listedPrice: Int? = null,                     // null for a commission blade handed over from storage
    @EncodeDefault(NEVER) val tradeInCredit: Int = 0, @EncodeDefault(NEVER) val tradeInWeaponId: WeaponId? = null,
    val cashPaid: Int,                                // coin the customer paid; for a commission, the reward
    @EncodeDefault(NEVER) val saleBonus: Int = 0, @EncodeDefault(NEVER) val stipend: Int = 0, @EncodeDefault(NEVER) val commissionId: CommissionId? = null,
)   // the shop's gold rises by cashPaid + saleBonus + stipend

enum class RecognitionCue { FIRST_VISIT, FIRST_BLADE, BECAME_REGULAR, REGULAR_RETURNS, STILL_CARRIES, BLADE_WORN, HELD_THE_WALL,
                           SLEW_AN_ELITE, KEPT_THE_VOW, MENTORS_BLADE, OF_THE_LINE, WAITED_YESTERDAY, WANT_ANSWERED }
/** Typed, like everything else on the record: the cue and only the fields its template needs. */
@Serializable data class Recognition(val cue: RecognitionCue, @EncodeDefault(NEVER) val weaponId: WeaponId? = null, @EncodeDefault(NEVER) val otherHeroId: HeroId? = null,
                                     @EncodeDefault(NEVER) val day: Int? = null, @EncodeDefault(NEVER) val count: Int? = null)

/**
 * One appearance at the counter, with what was true when it happened. A day decoded from a save older than the
 * record ([DayResolution.recordVersion] 0) has only the first four fields.
 */
@Serializable
data class MarketVisit(
    val heroId: HeroId?,                              // null only for a COLLECTOR who is not a hero
    val heroName: String,
    val purchasedWeaponId: WeaponId?,                 // the blade that left with them, whatever the kind of visit
    val reason: VisitReason,
    @EncodeDefault(NEVER) val seq: Int = 0,
    @EncodeDefault(NEVER) val kind: VisitKind = VisitKind.BROWSE,
    @EncodeDefault(NEVER) val customer: CustomerSnapshot? = null,           // null for the collector and for a day decoded from a 0.5.x save
    @EncodeDefault(NEVER) val considered: List<Considered> = emptyList(),   // the chosen blade first, then the best alternatives, at most 3
    @EncodeDefault(NEVER) val sale: Sale? = null,
    @EncodeDefault(NEVER) val recognition: Recognition? = null,             // filled from T3.5
    @EncodeDefault(NEVER) val eventIds: List<String> = emptyList(),
)

enum class IncomeKind { SHELF_SALE, SALE_BONUS, STIPEND, COMMISSION, COLLECTOR, TRIBUTE, CONTRACT, INSURANCE }   // every way End Day adds gold today; a new source adds a constant
@Serializable data class ShopLedger(
    val goldAtOpen: Int, val goldAtClose: Int, val income: Map<IncomeKind, Int>,
    val tradeInCredit: Int, val spentPreparing: Int,
)   // invariant: goldAtOpen + income.values.sum() == goldAtClose

enum class FieldOutcome { WON, DRIVEN_BACK, DIED, PATROLLED, RESTED, GUILD_TRAINED, GUILD_LESSON, GUILD_TAUGHT, AMBITION_DAY, HELD_THE_WALL, FELL_AT_THE_WALL }
/** One hero's day beyond the door. Replaces string-matching on events for tallies and the aftermath. */
@Serializable data class FieldResult(
    val heroId: HeroId, val heroName: String, val outcome: FieldOutcome,
    val foe: String? = null, val elite: Boolean = false, val factionId: FactionId? = null,
    val weapon: WeaponSnapshot? = null,
    val lostBareHanded: Boolean = false,              // won, and the same recorded roll loses under the same formula with no weapon
    val lostWithOldBlade: Boolean? = null,            // for a blade bought today: the same roll loses with the blade it replaced
    val matchupHelped: Boolean = false,               // the blade's element or bane counted against this foe
    val gold: Int = 0, val materialId: MaterialId? = null,
    val withHeroId: HeroId? = null,                   // the guildmate who taught or was taught (0.6.0 hall lessons)
    val eventIds: List<String> = emptyList(),
)
