package com.tinyblacksmith.core.model

import kotlinx.serialization.Serializable

/**
 * Living heroes (docs/Tiny_Blacksmith_Living_Heroes_Final_Design.md): the era feature revision and the small records a
 * hero and a member carry. Every field elsewhere that holds one of these is defaulted, so a save from before reads as
 * revision 0 with none of them.
 */
object LivingRevision {
    /** An era begun before living heroes: every old path, a pending manual Speciality included. The default on decode. */
    const val LEGACY = 0
    /** Cultures, Hooks, member shopping, announced loan returns, autonomous Specialities, sponsored offers, army templates. */
    const val CURRENT = 1
}

enum class HookKind { GRUDGE, DEBT, LOST_WEAPON, OATH, KIN }
enum class HookStatus { OPEN, RESOLVED, CHANGED }
/** An equipment promise shown before recruitment (final design 7.3). */
enum class OathKind { PLAIN_WORK, NO_GRAVE, FAVOURED_FAMILY }

/**
 * One personal situation (final design 7.3). It names real things only: a faction, a creditor who is a resident or a
 * plainly described outsider, a weapon record in `Lost`, a weapon family, a living relative. [background] marks history
 * from before the hero came to town, which never claims a recorded fight.
 */
@Serializable
data class Hook(
    val kind: HookKind,
    val sinceDay: Int,
    val status: HookStatus = HookStatus.OPEN,
    val factionId: FactionId? = null,
    val creditor: String? = null,
    val creditorHeroId: HeroId? = null,
    val amount: Int = 0,
    val dueDay: Int? = null,
    val weaponId: WeaponId? = null,
    val oath: OathKind? = null,
    val familyId: WeaponFamilyId? = null,
    val kinHeroId: HeroId? = null,
    val background: Boolean = false,
    /** The last morning a sponsored offer for this hook was posted; ten days without one end a grudge (final design 7.3). */
    val lastOfferDay: Int? = null,
    /** A failed heirloom has become a grudge, or a grudge a new target: each happens at most once. */
    val transitioned: Boolean = false,
    val note: String? = null,
) {
    val isOpen: Boolean get() = status == HookStatus.OPEN
}

/** A significant moment of a hero's life, as a short sentence from a real record (newest ten kept, final design 11). */
@Serializable
data class ReasonRecord(val day: Int, val kind: String, val text: String, val refs: List<String> = emptyList())

/**
 * A Speciality pair a member's deeds opened (final design 9.2). Announced on [noticeDay]'s morning, chosen by the member at
 * the End Day of [resolveDay] or later, once they are home. [options] are speciality IDs, [leaningTo] the current lean.
 */
@Serializable
data class SpecialityLean(val deed: String, val options: List<String>, val leaningTo: String, val reason: String, val noticeDay: Int, val resolveDay: Int)

enum class ReturnReason { OATH, THREAT, CLASS_FIT, STRONGER, WORN_LOAN }

/** A member who would rather fight with [ownedId] than with the smith's loan [loanId] (final design 8.3). */
@Serializable
data class LoanReturnIntent(val loanId: WeaponId, val ownedId: WeaponId, val reason: ReturnReason, val noticeDay: Int, val resolveDay: Int)

enum class ConcernKind { DEBT, GRUDGE, HEIRLOOM }

/** The date a member leaves unless [Concern.remedy] is met. Never before the first siege, at least three planning mornings ahead. */
@Serializable
data class Departure(val noticeDay: Int, val day: Int, val remedy: String, val originOfferIds: List<String>, val protectedCommitment: String? = null)

/**
 * An actionable concern (final design 10.3). [missed] are the offer IDs that met every condition of an ignored offer, each
 * counted once. Three distinct misses may raise a [departure].
 */
@Serializable
data class Concern(val kind: ConcernKind, val sinceDay: Int, val remedy: String, val missed: List<String> = emptyList(), val departure: Departure? = null)
