package com.tinyblacksmith.core.persistence

import com.tinyblacksmith.core.legacy.Legacy
import com.tinyblacksmith.core.model.HeroId
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation

/**
 * Drops weapons nothing can read again (GDD 13.3 "preserve important ... events, not every" record; 15.3 "without
 * unbounded memory"). Runs inside End Day right after [WeaponHistoryCompaction], so it is deterministic and covered by
 * the per-command idempotence. A weapon goes only when all of these hold:
 *  - it is gone for good: Destroyed (salvaged or shattered) or Lost for one of the [terminalReasons]. A blade lost
 *    with a hero or seized can still come home (`WorldEvents` Heroic Inheritance reads those), so it stays, and so
 *    does a blade a travelling merchant still holds (`Market.resolveMerchant` may sell it) and any reason added later;
 *  - that happened at least `retentionDays` ago, the same window the event log keeps in full;
 *  - it is neither a Legend Board candidate (`Legacy.closeRun` reads fame at or above the threshold) nor a signature weapon;
 *  - nobody who held it this era is alive and carrying nothing: `Market.commissionSituations` asks whether such a hero
 *    "carried a blade this era" before they may ask for a replacement, and a blade that shattered in their hands is the
 *    only one that says so. It goes once they carry another or are gone.
 * No rule reads such a weapon and IDs come from a serial counter, so a seed replays identically with or without it.
 */
object WeaponPruning {
    /** [WeaponLocation.Lost] reasons no rule brings a blade back from: `GameEngine` Arm the watch, `WorldEvents` The Collector Arrives, `Market.resolveMerchant` unsold. */
    val terminalReasons: Set<String> = setOf("given to the town watch", "sold to a collector", "carried off by a travelling merchant")

    fun prunable(w: Weapon, today: Int, retentionDays: Int, legendFame: Int): Boolean {
        if (retentionDays <= 0 || w.fame >= legendFame || w.signatureId != null) return false
        val goneOn = when (val loc = w.location) {
            is WeaponLocation.Destroyed -> loc.day
            is WeaponLocation.Lost -> if (loc.reason in terminalReasons) loc.day else return false
            else -> return false
        }
        return goneOn <= today - retentionDays
    }

    /** Prunes in place, order-preserving. [bereft] are the living heroes who carry nothing; a blade one of them held in [era] stays. */
    fun prune(weapons: MutableMap<WeaponId, Weapon>, today: Int, retentionDays: Int, legendFame: Int, era: Int = 0, bereft: Set<HeroId> = emptySet()) {
        if (retentionDays <= 0) return
        weapons.values.removeAll { prunable(it, today, retentionDays, legendFame) && Legacy.holders(it, era).none { h -> h in bereft } }
    }
}
