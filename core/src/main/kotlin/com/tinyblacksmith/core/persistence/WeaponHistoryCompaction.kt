package com.tinyblacksmith.core.persistence

import com.tinyblacksmith.core.legacy.Legacy
import com.tinyblacksmith.core.model.HistoryEntry
import com.tinyblacksmith.core.model.Weapon
import com.tinyblacksmith.core.model.WeaponId

/**
 * Bounds [Weapon.history]. Runs inside End Day right after [EventCompaction], so it is deterministic and covered by
 * the per-command idempotence. Only the combat kinds in [compactable] are ever dropped (oldest first, keeping the
 * newest [com.tinyblacksmith.core.config.BalanceConfig.weaponHistoryCap]); their counts already live in
 * `Weapon.kills/victories/siegesDefended/fame`, and no rule reads the entries themselves (the fame effect reads the
 * counter, which compaction never touches). Every other kind (FORGED, SIGNATURE, SOLD,
 * EQUIPPED, COMMISSION, INHERITED, LOST, SEIZED, RECOVERED, RETURNED, COLLECTED and anything added later) is kept
 * verbatim: `Legacy.closeRun` reads SOLD/COMMISSION owners and `WorldEvents` reads the last LOST/SEIZED subject.
 * One combat line is read: `Recognitions` tells a carrier that they held the wall with this blade from their last SIEGE
 * line on it, so the newest SIEGE line of each living hero stays beyond the cap.
 * Keeping the newest N commutes with daily application, so a seed replays identically with or without it.
 */
object WeaponHistoryCompaction {
    /** Written once per fight (VICTORY) or per siege (SIEGE); the only unbounded kinds per weapon. */
    val compactable: Set<String> = setOf("VICTORY", "SIEGE")

    /**
     * Returns [history] itself when nothing is dropped; otherwise the same list minus the oldest combat entries beyond
     * [cap], except the newest SIEGE line of each hero in [living].
     */
    fun compact(history: List<HistoryEntry>, cap: Int, living: Set<String> = emptySet()): List<HistoryEntry> {
        if (cap <= 0) return history
        var toDrop = history.count { it.kind in compactable } - cap
        if (toDrop <= 0) return history
        val walls = living.mapNotNull { id -> history.lastOrNull { it.kind == "SIEGE" && id in it.subjectIds } }
        val kept = history.filter { e -> if (toDrop > 0 && e.kind in compactable) { toDrop--; walls.any { it === e } } else true }
        return if (kept.size == history.size) history else kept
    }

    /** Compacts every weapon in place, order-preserving; weapons with nothing to drop are left as the same instance. */
    fun compact(weapons: MutableMap<WeaponId, Weapon>, cap: Int, living: Set<String> = emptySet()) {
        if (cap <= 0) return
        weapons.replaceAll { _, w -> val h = compact(w.history, cap, living); if (h === w.history) w else w.copy(history = h) }
    }

    /**
     * The lines a blade gathers as it changes hands and is looked after; the only unbounded kinds besides combat. What a
     * blade is (FORGED, SIGNATURE, TITLED, RETURNED, AWAKENED) and how it left a hero (LOST, SEIZED, SCAVENGED, RECOVERED,
     * BROKEN and the like, read by `WorldEvents` and `Market.resolveMerchant` as "the last of its kind") are never dropped.
     */
    val everyday: Set<String> = setOf("SOLD", "EQUIPPED", "TRADED_IN", "HONED", "COMMISSION", "INHERITED", "RESOLD")

    /**
     * Keeps the newest [cap] everyday lines of [weapon] (EQUIPPED lines are not counted: each follows a hand-over and
     * goes with it) and drops the older ones, except the blade's first owner and any line that names a hero in [living].
     * Before a line goes, everyone who held the blade in [era] is written to [Weapon.ownerIds], so `Legacy.holders`
     * answers as it did. With that, every reader gets what it got from the full history:
     *  - `Market.commissionSituations` and `Legacy.closeRun` ask who held the blade: `Legacy.holders`;
     *  - `Recognitions` asks for a line of the blade's current carrier, who is living;
     *  - the Legend Board's copy (`Legacy.closeRun`) is the lines that say what the blade is plus the newest everyday
     *    ones up to `Legacy.STORY_MAX`, so [cap] is never taken below that.
     * Returns [weapon] itself when nothing is dropped.
     */
    fun compactEveryday(weapon: Weapon, cap: Int, era: Int, living: Set<String>): Weapon {
        if (cap <= 0) return weapon
        val keep = maxOf(cap, Legacy.STORY_MAX)
        val counted = weapon.history.count { it.kind in everyday && it.kind != "EQUIPPED" }
        if (counted <= keep) return weapon
        // Everything from the oldest of the newest `keep` counted lines on stays as it is.
        var seen = 0
        val cut = weapon.history.indexOfLast { e -> e.kind in everyday && e.kind != "EQUIPPED" && ++seen == keep }
        val firstOwner = weapon.history.firstOrNull { it.kind in Legacy.OWNERSHIP }
        val kept = weapon.history.filterIndexed { i, e -> i >= cut || e.kind !in everyday || e === firstOwner || e.subjectIds.any { it in living } }
        return if (kept.size == weapon.history.size) weapon else weapon.copy(history = kept, ownerIds = Legacy.holders(weapon, era))
    }

    /** [compactEveryday] for every weapon in place, order-preserving. */
    fun compactEveryday(weapons: MutableMap<WeaponId, Weapon>, cap: Int, era: Int, living: Set<String>) {
        if (cap <= 0) return
        weapons.replaceAll { _, w -> compactEveryday(w, cap, era, living) }
    }
}
