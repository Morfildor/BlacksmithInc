package com.tinyblacksmith.core.model

import kotlinx.serialization.Serializable

enum class EncounterStatus { OPEN, RESOLVED, EXPIRED }

/**
 * The visitor of one morning (`engine.Encounters`). Everything random about the offer is drawn when it is made and
 * stored here, so reading, reopening or reloading it draws nothing. [day] is the morning it is offered on; it expires
 * at that day's End Day. Which fields are set depends on [defId]; a subject is always a real ID of this run.
 */
@Serializable
data class EncounterInstance(
    val id: String,
    val defId: String,
    val day: Int,
    val heroId: HeroId? = null,
    val otherHeroId: HeroId? = null,
    val weaponId: WeaponId? = null,
    val materialId: MaterialId? = null,
    val otherMaterialId: MaterialId? = null,
    val familyId: WeaponFamilyId? = null,
    val otherFamilyId: WeaponFamilyId? = null,
    /** Journal keys (the master's afternoon). */
    val key: String? = null,
    val otherKey: String? = null,
    /** Prices, quantities and rewards as offered, by the names `Encounters` reads them under. */
    val amounts: Map<String, Int> = emptyMap(),
    /** The crooked merchant's blade: not in the run's weapons until it is bought. */
    val blade: Weapon? = null,
    val inspected: Boolean = false,
    val status: EncounterStatus = EncounterStatus.OPEN,
    val chosen: String? = null,
    /** The command that resolved it: the same ID again is a retry and changes nothing. */
    val commandId: String? = null,
) {
    val isOpen: Boolean get() = status == EncounterStatus.OPEN
}

/** One line of the bounded log of how visitors were answered. */
@Serializable
data class EncounterRecord(val instanceId: String, val defId: String, val day: Int, val optionId: String, val expired: Boolean = false)

/** A run-long workshop relic. [progress] counts seals; [families] is the ledger's current streak. */
@Serializable
data class ActiveRelic(val id: String, val progress: Int = 0, val families: List<WeaponFamilyId> = emptyList())

enum class ConsequenceKind { WALL_PLEDGE, WAGER, WATCH_BOUNTY }

/** Something a choice set in motion, read again on [dueDay] against the run as it then is. */
@Serializable
data class ScheduledConsequence(
    val id: String,
    val kind: ConsequenceKind,
    val dueDay: Int,
    val heroId: HeroId? = null,
    val weaponId: WeaponId? = null,
    val commissionId: CommissionId? = null,
    val familyId: WeaponFamilyId? = null,
    val amounts: Map<String, Int> = emptyMap(),
)

/** The siege that is coming: its trait from the day it is scheduled, its besieger from the first warning. */
@Serializable
data class SiegeScenario(val siegeDay: Int, val traitId: String? = null, val factionId: FactionId? = null)
