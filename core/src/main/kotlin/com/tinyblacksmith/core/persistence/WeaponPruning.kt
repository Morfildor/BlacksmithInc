package com.tinyblacksmith.core.persistence

import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponId
import com.tinyblacksmith.core.model.WeaponLocation

/**
 * Drops weapons nothing can read again (GDD 13.3 "preserve important ... events, not every" record; 15.3 "without
 * unbounded memory"). Runs inside End Day right after [WeaponHistoryCompaction], so it is deterministic and covered by
 * the per-command idempotence. A weapon goes only when all of these hold:
 *  - it is gone for good: Destroyed (salvaged or shattered) or Lost for one of the [terminalReasons]. A blade lost
 *    with a hero or seized can still come home (`WorldEvents` Heroic Inheritance reads those), so it stays, and so
 *    does any reason added later;
 *  - that happened at least `retentionDays` ago, the same window the event log keeps in full;
 *  - it is neither a Legend Board candidate (`Legacy.closeRun` reads fame at or above the threshold) nor a signature weapon.
 * No rule reads such a weapon and IDs come from a serial counter, so a seed replays identically with or without it.
 */
object WeaponPruning {
    /** [WeaponLocation.Lost] reasons no rule brings a blade back from: `GameEngine` Arm the watch, `WorldEvents` The Collector Arrives. */
    val terminalReasons: Set<String> = setOf("given to the town watch", "sold to a collector")

    fun prunable(w: Weapon, today: Int, retentionDays: Int, legendFame: Int): Boolean {
        if (retentionDays <= 0 || w.fame >= legendFame || w.signatureId != null) return false
        val goneOn = when (val loc = w.location) {
            is WeaponLocation.Destroyed -> loc.day
            is WeaponLocation.Lost -> if (loc.reason in terminalReasons) loc.day else return false
            else -> return false
        }
        return goneOn <= today - retentionDays
    }

    /** Prunes in place, order-preserving. */
    fun prune(weapons: MutableMap<WeaponId, Weapon>, today: Int, retentionDays: Int, legendFame: Int) {
        if (retentionDays <= 0) return
        weapons.values.removeAll { prunable(it, today, retentionDays, legendFame) }
    }
}
