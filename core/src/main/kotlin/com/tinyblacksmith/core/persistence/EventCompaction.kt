package com.tinyblacksmith.core.persistence

import com.tinyblacksmith.core.model.EventRecord
import com.tinyblacksmith.core.model.EventType

/**
 * Event-log compaction (GDD 13.3: "compact ordinary events and retain rare milestones"). Runs inside End Day after
 * the Gazette is built, so it is deterministic and covered by the per-command idempotence. No gameplay rule reads
 * past events (world-event cooldowns, milestones, siege tallies and weapon owners live on their own counters and
 * histories), so compaction never changes outcomes; it only bounds what the save and the Gazette archive carry.
 */
object EventCompaction {
    /** History-grade types kept for the whole run: siege results, deaths, lineage and guild events, famous-weapon developments, scripted events. */
    val keptForever: Set<EventType> = setOf(
        EventType.RUN_STARTED, EventType.HERO_ARRIVED, EventType.HERO_DIED, EventType.HERO_RETIRED, EventType.GUILD_FOUNDED, EventType.HERO_MENTORED,
        EventType.SIEGE_WON, EventType.SIEGE_LOST, EventType.FORGE_DESTROYED, EventType.SIGNATURE_DISCOVERED, EventType.MILESTONE,
        EventType.LEGEND_RECORDED, EventType.WORLD_EVENT, EventType.ARTIFACT_RETURNED, EventType.WEAPON_STOLEN, EventType.WEAPON_INHERITED,
        EventType.WEAPON_RECOVERED, EventType.WEAPON_LOST, EventType.WEAPON_RESOLD,
    )

    fun keeps(event: EventRecord, today: Int, retentionDays: Int): Boolean =
        retentionDays <= 0 || event.type in keptForever || event.day > today - retentionDays

    /** Drops, in place and order-preserving, every ordinary event older than [retentionDays] days before [today]. */
    fun compact(events: MutableList<EventRecord>, today: Int, retentionDays: Int) {
        if (retentionDays <= 0) return
        events.retainAll { keeps(it, today, retentionDays) }
    }
}
