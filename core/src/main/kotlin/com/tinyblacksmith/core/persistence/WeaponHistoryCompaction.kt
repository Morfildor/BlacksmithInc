package com.tinyblacksmith.core.persistence

import com.tinyblacksmith.core.model.HistoryEntry
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponId

/**
 * Bounds [Weapon.history]. Runs inside End Day right after [EventCompaction], so it is deterministic and covered by
 * the per-command idempotence. Only the combat kinds in [compactable] are ever dropped (oldest first, keeping the
 * newest [com.tinyblacksmith.core.config.BalanceConfig.weaponHistoryCap]); their counts already live in
 * `Weapon.kills/victories/siegesDefended/fame`, and no rule reads them. Every other kind (FORGED, SIGNATURE, SOLD,
 * EQUIPPED, COMMISSION, INHERITED, LOST, SEIZED, RECOVERED, RETURNED, COLLECTED and anything added later) is kept
 * verbatim: `Legacy.closeRun` reads SOLD/COMMISSION owners and `WorldEvents` reads the last LOST/SEIZED subject.
 * Keeping the newest N commutes with daily application, so a seed replays identically with or without it.
 */
object WeaponHistoryCompaction {
    /** Written once per fight (VICTORY) or per siege (SIEGE); the only unbounded kinds per weapon. */
    val compactable: Set<String> = setOf("VICTORY", "SIEGE")

    /** Returns [history] itself when nothing is dropped; otherwise the same list minus the oldest combat entries beyond [cap]. */
    fun compact(history: List<HistoryEntry>, cap: Int): List<HistoryEntry> {
        if (cap <= 0) return history
        var toDrop = history.count { it.kind in compactable } - cap
        if (toDrop <= 0) return history
        return history.filter { e -> if (toDrop > 0 && e.kind in compactable) { toDrop--; false } else true }
    }

    /** Compacts every weapon in place, order-preserving; weapons under the cap are left as the same instance. */
    fun compact(weapons: MutableMap<WeaponId, Weapon>, cap: Int) {
        if (cap <= 0) return
        weapons.replaceAll { _, w -> val h = compact(w.history, cap); if (h === w.history) w else w.copy(history = h) }
    }
}
