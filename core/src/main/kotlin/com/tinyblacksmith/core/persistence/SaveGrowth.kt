package com.tinyblacksmith.core.persistence

import com.tinyblacksmith.core.model.Commission
import com.tinyblacksmith.core.model.CommissionId
import com.tinyblacksmith.core.model.CommissionStatus

/**
 * Two of the rules that keep a long save bounded (GDD 13.3, 15.3; the numbers are `BalanceConfig.saveGrowth`, 0 = off).
 * Like [EventCompaction] they run inside End Day after the day's report is built, so they are deterministic and covered
 * by the per-command idempotence, and they trim only the archive: what a screen may show, never what a rule reads
 * (docs/DECISIONS.md, "hot state and archive").
 */

/**
 * A closed commission (completed, expired or declined) leaves the save `retentionDays` after its deadline, by when the
 * event log no longer holds a record that names it. Rules read open commissions only (`Market.openCommissions`,
 * `resolveCommissions`), and IDs come from a serial counter, so a seed replays identically with or without it.
 */
object CommissionPruning {
    fun prunable(c: Commission, today: Int, retentionDays: Int): Boolean =
        retentionDays > 0 && c.status != CommissionStatus.OFFERED && c.status != CommissionStatus.ACCEPTED && c.deadlineDay <= today - retentionDays

    /** Prunes in place, order-preserving. */
    fun prune(commissions: MutableMap<CommissionId, Commission>, today: Int, retentionDays: Int) {
        if (retentionDays <= 0) return
        commissions.values.removeAll { prunable(it, today, retentionDays) }
    }
}

/**
 * The End Day command IDs a run remembers for its retry check: the newest `keep`, in the order they were processed
 * (the set keeps insertion order in memory and in the save). A retry of the latest End Day, the case the check exists
 * for, returns the stored resolution as before. An ID older than the newest `keep` is forgotten, so re-sending it
 * would resolve a new day: the caller builds an ID from the run and the day it ends, and never holds one that old.
 */
object ProcessedCommands {
    fun trim(ids: MutableSet<String>, keep: Int) {
        if (keep <= 0) return
        val drop = ids.size - keep
        if (drop > 0) ids.removeAll(ids.take(drop).toSet())
    }
}
