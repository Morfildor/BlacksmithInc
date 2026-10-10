package com.tinyblacksmith.core.combat

/**
 * The three chains of a fight worth telling (spec 6.5). A highlight is a path through the timeline from an action to
 * one of its consequences, and its sentence is the recorded texts of that path: it can say nothing the fight did not do.
 */
object Highlights {
    private const val MAX = 3
    private const val MAX_SENTENCES = 5

    fun of(events: List<FightEvent>, sides: Map<String, Side>): List<Highlight> {
        val byId = events.associateBy { it.id }
        fun path(e: FightEvent): List<FightEvent> = generateSequence(e) { it.parentId?.let(byId::get) }.toList().asReversed()
        // A chain is worth telling for how many hands it passed through and what it cost, then for how it ended.
        fun weight(e: FightEvent, p: List<FightEvent>): Int {
            val enemy = sides[e.target] == Side.ENEMY
            val ending = when (e.kind) {
                EventKind.DOWNED -> if (enemy) 6 else 9
                EventKind.FRACTURED -> 5
                EventKind.SUMMONED -> 6
                EventKind.DAMAGE -> if (enemy && e.via == Via.EFFECT) 2 + e.amount / 3 else 0
                EventKind.GUARD_GAINED, EventKind.HEALED -> if (e.via == Via.EFFECT) 2 else 0
                else -> 0
            }
            if (ending == 0) return 0
            val hands = p.mapNotNull { it.source }.filter { sides[it] == Side.PARTY }.distinct().size
            val rules = p.mapNotNull { it.effectId }.distinct().size
            val paid = p.count { it.kind == EventKind.STAT_SPENT && it.effectId != null || it.kind == EventKind.HEALTH_PAID }
            return ending + 3 * maxOf(0, hands - 1) + 2 * rules + 4 * paid
        }
        val parents = events.mapNotNull { it.parentId }.toSet()
        val candidates = events.filter { it.id !in parents || it.kind == EventKind.DOWNED }.mapNotNull { e ->
            val p = path(e)
            val w = weight(e, p)
            if (w <= 0) null else Triple(e, p, w)
        }.sortedWith(compareByDescending<Triple<FightEvent, List<FightEvent>, Int>> { it.third }.thenBy { it.first.id })
        val chosen = ArrayList<Highlight>()
        val usedRoots = HashSet<Int>()
        val usedKinds = HashMap<String, Int>()
        for ((e, p, w) in candidates) {
            if (chosen.size == MAX) break
            if (!usedRoots.add(e.rootId)) continue
            // The same effect ending the same way is one story, however often it happened.
            val shape = "${e.kind}|${e.effectId}"
            if ((usedKinds[shape] ?: 0) >= 1) continue
            usedKinds[shape] = 1
            val told = p.filter { it.text.isNotEmpty() && it.kind != EventKind.ROUND_START }   // the round is already the highlight's first words.let { if (it.size <= MAX_SENTENCES) it else listOf(it.first()) + it.takeLast(MAX_SENTENCES - 1) }
            if (told.isEmpty()) continue
            chosen += Highlight(told.map { it.id }, "Round ${e.round}: " + told.joinToString(" ") { it.text }, w)
        }
        return chosen.sortedBy { it.eventIds.first() }
    }
}
