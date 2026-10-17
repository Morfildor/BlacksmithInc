package com.tinyblacksmith.core.combat

import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.text.LegacyProse
import com.tinyblacksmith.core.text.Terms

/**
 * A recorded fight as something to watch: the siege replay's model. It is built from a [FightResult] alone, reruns
 * nothing and draws nothing, and every number it shows is an event's own amount. Watching, skipping or replaying it
 * changes nothing anywhere: it is a value.
 *
 * A step is one action (or tick) and everything it set off, in the order the fight recorded it; consecutive events of
 * the same root are one step. A pop is what one event did to one actor, in words a fight log would use ("−18", "Burn
 * applied", "Burn −3", "Heal +4", "Blocked 5"); a silent pop moves a number the screen shows without saying anything
 * (a tick using up a stack, Wet drying). Applying every pop in order to [Replay.start] gives each actor's health as the
 * fight's own result says it ended: [FightReplay.of] refuses a record where it would not.
 */
enum class PopKind { DAMAGE, HEAL, BLOCK, GUARD, GUARD_BROKEN, STATUS, SPENT, PAID, DOWN, JOINED, CRACKED }

/** What an actor shows at one moment: its health, its states (Guard among them), and whether it is down or not yet on the field. */
data class ActorState(val health: Int, val stats: Map<Stat, Int>, val downed: Boolean, val present: Boolean)

/**
 * One event's effect on [target]. [text] is the big line, [note] the small one under it (what kind of hit, which rule).
 * [rule] is the rule's name when a rule did it, [weapon] whether that rule is the blade of [source]. [changes] are the
 * states this pop leaves, for every actor it moved.
 */
data class Pop(
    val eventId: Int,
    val target: String,
    val kind: PopKind,
    val text: String,
    val note: String?,
    val source: String?,
    val rule: String?,
    val weapon: Boolean,
    val tick: Stat?,
    val shown: Boolean,
    val changes: Map<String, ActorState>,
)

/** A rule that fired in a step: whose it was, and whether their blade brought it. */
data class RuleUse(val holder: String, val ruleId: String, val name: String, val weapon: Boolean)

enum class StepKind { ROUND, MOVE, TICK, RULE, END }

/** A reason a step is a turning point, in the order [FightReplay] prefers them for the short version. */
enum class Turn { TOLD, DOWN, CALLED, CRACKED, FIRST_RULE, BIG_HIT }

data class Step(
    val index: Int,
    val round: Int,
    val kind: StepKind,
    /** Whose step it is: the one who moves, the one whose state ticks. Null for a round's start and the end. */
    val actor: String?,
    /** "Brann · Cleave", "Mira · Burn", "Round 3", "Victory". */
    val title: String,
    val move: String?,
    val uses: List<RuleUse>,
    val pops: List<Pop>,
    /** The record's own sentences of this step, in order: the recent-action strip and the log read these. */
    val lines: List<String>,
    val turns: Set<Turn>,
    val before: Map<String, ActorState>,
) {
    val shownPops: List<Pop> get() = pops.filter { it.shown }

    /** Everybody after the first [count] pops of this step (silent ones included in the count). */
    fun stateAfter(count: Int): Map<String, ActorState> {
        val now = before.toMutableMap()
        pops.take(count).forEach { now.putAll(it.changes) }
        return now
    }

    val after: Map<String, ActorState> get() = stateAfter(pops.size)
}

/** An actor as the replay draws it: the record's own description, nothing looked up. */
data class ReplayActor(
    val key: String, val name: String, val side: Side, val maxHealth: Int, val summoned: Boolean, val kit: String?,
    val weapon: String?, val weaponId: String?, val element: Element?, val weaponRules: List<String>, val unitId: String?,
)

data class Replay(
    val title: String,
    val outcome: FightOutcome,
    val rounds: Int,
    val actors: List<ReplayActor>,
    val start: Map<String, ActorState>,
    val steps: List<Step>,
    /** The short version: indices of the steps that turned the fight, then the end. Never padded. */
    val highlights: List<Int>,
    /** Rule names by ID, as the record holds them. */
    val rules: Map<String, String>,
) {
    fun actor(key: String?): ReplayActor? = key?.let { k -> actors.firstOrNull { it.key == k } }
    val final: Map<String, ActorState> get() = steps.lastOrNull()?.after ?: start
}

object FightReplay {
    /** At most this many turning points in the short version, before the end. */
    const val MAX_HIGHLIGHTS = 10
    /** A single blow this large a share (percent) of its target's health is a turning point of its own. */
    const val BIG_HIT_PERCENT = 30

    /** States a hostile rule puts on somebody: "applied" the first time. The rest are an actor's own stores. */
    private val AILMENTS = setOf(Stat.BURN, Stat.CHILL, Stat.WET, Stat.BLEED, Stat.MARK, Stat.POISON)

    private fun word(stat: Stat): String = Terms.stat(stat)

    fun supports(result: FightResult): Boolean = result.record >= FightResult.RECORD

    /**
     * The replay of [result], or null when the record cannot carry one: a fight stored before replays, or a record whose
     * events do not add up to the health it says the fight ended on. The caller shows its summary then. [rules] gives the
     * numbers a pop names ("Chill −35%", "Mark +3"): the fight's own (`BalanceConfig.guild.fight`).
     * Stored names and sentences are shown in today's words (`LegacyProse`), never changed in the record.
     */
    fun of(result: FightResult, rules: FightRules = FightRules()): Replay? {
        if (!supports(result)) return null
        val actors = result.actors.map { a ->
            ReplayActor(a.key, a.name, a.side, a.maxHealth, a.summoned, a.kit, a.weapon, a.weaponId, a.element, a.weaponRules, a.unitId)
        }
        val byKey = actors.associateBy { it.key }
        val start = result.actors.associate { a ->
            val health = a.start ?: return null
            a.key to ActorState(health, a.startStats.filterValues { it > 0 }, downed = health <= 0, present = !a.summoned)
        }
        val now = start.toMutableMap()
        val byId = result.events.associateBy { it.id }
        val boiled = result.events.filter { it.kind == EventKind.STAT_GAINED && it.stat == Stat.STEAM }.mapNotNull { it.parentId }.toSet()
        val told = result.highlights.flatMap { it.eventIds }.toSet()
        val usedRules = HashSet<Pair<String, String>>()

        val names = LegacyProse.ruleNames(result.rules)
        fun ruleName(id: String?): String? = id?.let { names[it] ?: it }
        fun isWeapon(holder: String?, rule: String?): Boolean = holder != null && rule != null && byKey[holder]?.weaponRules?.contains(rule) == true
        fun change(key: String, f: (ActorState) -> ActorState): Map<String, ActorState> {
            val s = now[key] ?: return emptyMap()
            val n = f(s)
            now[key] = n
            return mapOf(key to n)
        }
        fun ActorState.add(stat: Stat, n: Int): ActorState {
            val v = ((stats[stat] ?: 0) + n).coerceAtLeast(0)
            return copy(stats = if (v == 0) stats - stat else stats + (stat to v))
        }

        // Consecutive events of one root are one step.
        val groups = ArrayList<MutableList<FightEvent>>()
        for (e in result.events) {
            if (e.kind == EventKind.ROUND_END || e.kind == EventKind.ACTED) continue
            val last = groups.lastOrNull()
            if (last != null && last.first().rootId == e.rootId && e.kind != EventKind.OBJECTIVE && e.kind != EventKind.RETREATED) last += e else groups += mutableListOf(e)
        }

        val steps = ArrayList<Step>()
        for (group in groups) {
            val first = group.first()
            val root = byId[first.rootId]?.takeIf { it === first }
            val before = now.toMap()
            val pops = ArrayList<Pop>()
            fun pop(e: FightEvent, target: String, kind: PopKind, text: String, note: String?, changes: Map<String, ActorState>, shown: Boolean = true) {
                pops += Pop(e.id, target, kind, text, note, e.source, ruleName(e.effectId), isWeapon(e.source, e.effectId), e.tick, shown, changes)
            }
            // A new round: Wet dried at the end of the last one, for everybody still standing.
            if (first.kind == EventKind.ROUND_START && first.round > 1) {
                val dried = now.filter { (_, s) -> s.present && !s.downed && (s.stats[Stat.WET] ?: 0) > 0 }.keys.toList()
                dried.forEach { k -> pop(first, k, PopKind.SPENT, "Wet dries", null, change(k) { it.add(Stat.WET, -1) }, shown = false) }
            }
            // A tick uses its stack up after its own blow or mend has landed.
            val lastTicked = group.indexOfLast { it.tick != null && it.kind != EventKind.ACTION }
            var pendingTick: Pair<String, Stat>? = null
            group.forEachIndexed { i, e ->
                val t = e.target
                val note = ruleName(e.effectId)
                when (e.kind) {
                    EventKind.ACTION -> if (e.via == Via.TICK && t != null && e.tick != null) pendingTick = t to e.tick
                    EventKind.DAMAGE -> if (t != null) {
                        val thorns = e.via == Via.EFFECT && e.effectId == null && byId[e.parentId]?.let { it.kind == EventKind.STAT_SPENT && it.stat == Stat.THORNS } == true
                        val kindNote = when {
                            e.tick != null -> word(e.tick) + if (e.affinity == Affinity.RESISTED) " · resisted" else ""
                            e.affinity == Affinity.WEAK -> listOfNotNull("Weak", note).joinToString(" · ")
                            e.affinity == Affinity.RESISTED -> listOfNotNull("Resisted", note).joinToString(" · ")
                            thorns -> "Thorns"
                            else -> note
                        }
                        pop(e, t, PopKind.DAMAGE, "−${e.amount}", kindNote, change(t) { it.copy(health = (it.health - e.amount).coerceAtLeast(0)) })
                    }
                    EventKind.DOWNED -> if (t != null) pop(e, t, PopKind.DOWN, "Down", null, change(t) { it.copy(health = 0, downed = true) })
                    EventKind.GUARD_ABSORBED -> if (t != null) {
                        val kindNote = when { e.tick != null -> word(e.tick); e.affinity == Affinity.RESISTED -> "Resisted"; e.affinity == Affinity.WEAK -> "Weak"; else -> null }
                        pop(e, t, PopKind.BLOCK, "Blocked ${e.amount}", kindNote, change(t) { it.add(Stat.GUARD, -e.amount) })
                    }
                    EventKind.GUARD_BROKEN -> if (t != null) pop(e, t, PopKind.GUARD_BROKEN, "Armor broken", null, change(t) { it.copy(stats = it.stats - Stat.GUARD) })
                    EventKind.GUARD_GAINED -> if (t != null) pop(e, t, PopKind.GUARD, "Armor +${e.amount}", note, change(t) { it.add(Stat.GUARD, e.amount) })
                    EventKind.HEALED -> if (t != null) {
                        val m = byKey[t]?.maxHealth ?: Int.MAX_VALUE
                        pop(e, t, PopKind.HEAL, "+${e.amount}", if (e.tick != null) word(e.tick) else note ?: "Heal", change(t) { it.copy(health = minOf(m, it.health + e.amount)) })
                    }
                    // Healing past full moves nothing; it is said only when nothing at all was healed.
                    EventKind.OVERHEAL -> if (t != null) pop(e, t, PopKind.HEAL, "Full health", note, emptyMap(), shown = e.text.isNotEmpty())
                    EventKind.STAT_GAINED -> if (t != null && e.stat != null) {
                        val had = now[t]?.stats?.get(e.stat) ?: 0
                        val text = if (e.stat in AILMENTS && had == 0) "${word(e.stat)} applied" else "${word(e.stat)} +${e.amount}"
                        pop(e, t, PopKind.STATUS, text, note, change(t) { it.add(e.stat, e.amount) })
                    }
                    EventKind.STAT_SPENT -> if (t != null && e.stat != null) {
                        val text = when {
                            e.id in boiled -> "Steam (${word(e.stat)} −${e.amount})"
                            e.stat == Stat.MARK -> "Mark +${rules.markBonus}"
                            e.stat == Stat.WET && e.source != t -> "Wet +${rules.wetStormBonus}"
                            e.stat == Stat.CHILL && e.effectId == null && e.source == t -> "Chill −${rules.chillPercent}%"
                            e.stat == Stat.THORNS -> "Thorns"
                            else -> "${word(e.stat)} −${e.amount}"
                        }
                        pop(e, t, PopKind.SPENT, text, note, change(t) { it.add(e.stat, -e.amount) })
                    }
                    EventKind.HEALTH_PAID -> if (t != null) pop(e, t, PopKind.PAID, "−${e.amount}", listOfNotNull("Paid", note).joinToString(" · "), change(t) { it.copy(health = (it.health - e.amount).coerceAtLeast(0)) })
                    EventKind.FRACTURED -> if (t != null) pop(e, t, PopKind.CRACKED, "Weapon cracked", note, emptyMap())
                    EventKind.SUMMONED -> if (t != null) pop(e, t, PopKind.JOINED, "Joins", note, change(t) { it.copy(present = true) })
                    // Living heroes: said on the actor they are about, with the pops a screen already draws.
                    EventKind.CHANNEL_STARTED -> e.source?.let { pop(e, it, PopKind.STATUS, "Preparing ${e.ability ?: "an ability"}", null, emptyMap()) }
                    EventKind.CHANNEL_COMPLETED -> Unit
                    EventKind.CHANNEL_CANCELLED -> if (t != null) pop(e, t, PopKind.SPENT, "${e.ability ?: "Ability"} cancelled", null, emptyMap(), shown = false)
                    EventKind.INTERRUPTED -> if (t != null) pop(e, t, PopKind.SPENT, "Interrupted", listOfNotNull(e.ability, note).joinToString(" · ").ifEmpty { null }, emptyMap())
                    EventKind.STRUCTURAL_HIT -> e.source?.let { pop(e, it, PopKind.STATUS, "Wall −${e.amount}", null, emptyMap()) }
                    EventKind.NEUTRALIZED -> if (t != null) pop(e, t, PopKind.SPENT, "Neutralized", null, emptyMap())
                    EventKind.RETURNED -> if (t != null) pop(e, t, PopKind.JOINED, "Returned", note, change(t) { it.copy(health = e.amount, downed = false, present = true, stats = emptyMap()) })
                    EventKind.SURVIVED -> if (t != null) pop(e, t, PopKind.STATUS, "Survived at 1 Health", null, emptyMap())
                    EventKind.RECOVERY_STOPPED -> if (t != null) pop(e, t, PopKind.STATUS, "No healing", null, emptyMap())
                    EventKind.PREY_MARKED -> if (t != null) pop(e, t, PopKind.STATUS, "Prey", note, emptyMap())
                    EventKind.ROUND_START, EventKind.OBJECTIVE, EventKind.RETREATED, EventKind.ROUND_END, EventKind.ACTED -> Unit
                }
                if (i == lastTicked) pendingTick?.let { (k, s) ->
                    pop(e, k, PopKind.SPENT, "${word(s)} −1", null, change(k) { it.add(s, -1) }, shown = false)
                    pendingTick = null
                }
            }
            // A tick that left no blow and no mend (nothing recorded under it) still used its stack.
            pendingTick?.let { (k, s) -> pop(group.first(), k, PopKind.SPENT, "${word(s)} −1", null, change(k) { it.add(s, -1) }, shown = false) }

            val kind: StepKind
            val actor: String?
            val move: String?
            val title: String
            when {
                first.kind == EventKind.ROUND_START -> { kind = StepKind.ROUND; actor = null; move = null; title = "Round ${first.round}" }
                first.kind == EventKind.OBJECTIVE || first.kind == EventKind.RETREATED -> { kind = StepKind.END; actor = null; move = null; title = outcomeWord(result.outcome) }
                root != null && root.kind == EventKind.ACTION && root.via == Via.TICK -> {
                    kind = StepKind.TICK; actor = root.target; move = null
                    title = listOfNotNull(byKey[actor]?.name, root.tick?.let(::word)).joinToString(" · ")
                }
                root != null && root.kind == EventKind.ACTION -> {
                    kind = StepKind.MOVE; actor = root.source
                    val name = byKey[actor]?.name.orEmpty()
                    // The engine writes "Name: Move." for an action; the move is what it names.
                    val said = LegacyProse.fightLine(root.text)
                    move = said.removePrefix("$name: ").removeSuffix(".").takeIf { it.isNotBlank() && it != said }
                    title = listOfNotNull(name.ifEmpty { null }, move).joinToString(" · ")
                }
                else -> {
                    kind = StepKind.RULE; actor = group.firstNotNullOfOrNull { it.source }
                    move = null
                    title = listOfNotNull(byKey[actor]?.name, group.firstNotNullOfOrNull { ruleName(it.effectId) }).joinToString(" · ").ifEmpty { "Round's end" }
                }
            }
            val uses = group.filter { it.effectId != null && it.source != null }.map { it.source!! to it.effectId!! }.distinct()
                .map { (h, r) -> RuleUse(h, r, ruleName(r)!!, isWeapon(h, r)) }
            val turns = HashSet<Turn>()
            if (group.any { it.id in told }) turns += Turn.TOLD
            if (group.any { it.kind == EventKind.DOWNED || it.kind == EventKind.NEUTRALIZED }) turns += Turn.DOWN
            if (group.any { it.kind == EventKind.SUMMONED || it.kind == EventKind.RETURNED }) turns += Turn.CALLED
            if (group.any { it.kind == EventKind.FRACTURED }) turns += Turn.CRACKED
            if (uses.any { it.weapon && usedRules.add(it.holder to it.ruleId) }) turns += Turn.FIRST_RULE
            if (group.any { e -> e.kind == EventKind.DAMAGE && e.target != null && e.amount * 100 >= BIG_HIT_PERCENT * (byKey[e.target]?.maxHealth ?: Int.MAX_VALUE) }) turns += Turn.BIG_HIT
            if (kind == StepKind.ROUND || kind == StepKind.END) turns.clear()
            steps += Step(steps.size, first.round, kind, actor, title, move, uses, pops, group.filter { it.text.isNotEmpty() && it.kind != EventKind.ROUND_START }.map { LegacyProse.fightLine(it.text) }, turns, before)
        }

        // The record must add up: the health every pop leaves is the health the fight says each actor ended on.
        val end = steps.lastOrNull()?.after ?: start
        if (result.actors.any { a -> end[a.key]?.health != a.health || end[a.key]?.downed != a.downed }) return null

        return Replay(result.title, result.outcome, result.rounds, actors, start, steps, highlights(steps), names)
    }

    /** The steps that turned the fight, at most [MAX_HIGHLIGHTS], in the order they happened, and the end. Nothing is added to fill. */
    fun highlights(steps: List<Step>): List<Int> {
        val ranked = steps.filter { it.turns.isNotEmpty() }.sortedWith(compareBy<Step> { s -> s.turns.minOf { it.ordinal } }.thenBy { it.index })
        val chosen = ranked.take(MAX_HIGHLIGHTS).map { it.index }.sorted()
        return chosen + listOfNotNull(steps.lastOrNull()?.takeIf { it.kind == StepKind.END }?.index)
    }

    fun outcomeWord(outcome: FightOutcome): String = when (outcome) {
        FightOutcome.WON -> "Victory"
        FightOutcome.LOST -> "Defeat"
        FightOutcome.RETREATED -> "Retreated"
        FightOutcome.TIMED_OUT -> "Undecided"
    }
}
