package com.tinyblacksmith.core.combat

import com.tinyblacksmith.core.text.LegacyProse
import com.tinyblacksmith.core.text.Terms

/**
 * Who did what in a recorded fight, counted once (the siege report). Every point of damage the enemy took, every point
 * of the party's Guard that took a blow, and every point of effective healing a member received lands in exactly one
 * place: a member's share for one rule, or [FightAccount.shared] when the record cannot say whose it was.
 *
 * What is counted, and only from the record:
 *  - Damage: a DAMAGE event on an enemy. A strike or a rule's blow belongs to whoever dealt it and the rule that did
 *    (none: the member's own moves). A tick of Burn or Bleed belongs to whoever put those stacks on, while every stack
 *    on that enemy came from one member (to the rule too, when one rule put them all); otherwise it is shared.
 *  - Prevented: a GUARD_ABSORBED event on a member, credited by the same rule to whoever gave that Guard. Chill and
 *    other softening are not counted: the record has no number for what a blow would have been.
 *  - Healed: a HEALED event (effective healing only; healing past full is not counted) on a member, by its source, or
 *    by whoever gave the Regeneration that ticked.
 *  A helper called onto the field counts for the rule that called it.
 */
data class Share(
    /** The rule's ID; null for the member's own moves; "#thorns", "#ticks:BURN", "#guard", "#regen" for the mixed kinds. */
    val ruleId: String?,
    val name: String,
    val weapon: Boolean,
    val damage: Int,
    val prevented: Int,
    val healed: Int,
    /** Stacks of a state this rule put on enemies (Burn, Chill, Mark...). Not damage: the ticks count where they land. */
    val applied: Int,
    /** How many times the rule fired. 0 for the member's own moves and the mixed kinds. */
    val times: Int,
) {
    val total: Int get() = damage + prevented + healed
}

data class Contribution(
    val key: String, val name: String, val weapon: String?, val weaponId: String?,
    val damage: Int, val prevented: Int, val healed: Int,
    /** The member's shares, largest first; they add up to the three totals above exactly. */
    val shares: List<Share>,
    val downed: Boolean,
)

data class FightAccount(
    val members: List<Contribution>,
    /** Damage, prevention and healing the record cannot give to one member. */
    val shared: Share,
    /** Everything the enemy lost to damage. The members' damage and the shared damage add up to it. */
    val enemyDamage: Int,
    /** At most two things the record shows, each a fact of it: never a claim about what would have happened. */
    val observations: List<String>,
)

/** Which tier of [FightAccounting.standout] a [Standout] came from. */
enum class StandoutKind { WEAPON_RULE, WEAPON_STATES, HERO_PART }

/**
 * One defender's one share, picked by [FightAccounting.standout], with the names as the fight recorded them (the
 * weapon is the one in the defender's hands in that fight, not whatever they carry now) and its sentence.
 */
data class Standout(val kind: StandoutKind, val memberKey: String, val memberName: String, val weapon: String?, val weaponId: String?, val share: Share, val text: String) {
    constructor(kind: StandoutKind, m: Contribution, s: Share, text: String) : this(kind, m.key, m.name, m.weapon.takeIf { s.weapon }, m.weaponId.takeIf { s.weapon }, s, text)
}

object FightAccounting {
    const val OWN = "Own abilities"

    fun of(result: FightResult): FightAccount? {
        if (!FightReplay.supports(result)) return null
        val actors = result.actors.associateBy { it.key }
        val party = { key: String? -> key != null && actors[key]?.side == Side.PARTY }
        val byId = result.events.associateBy { it.id }

        // A helper's work belongs to whoever called it, and to the rule that did.
        val calledBy = HashMap<String, Pair<String, String?>>()
        fun credit(key: String, rule: String?): Pair<String, String?> = calledBy[key] ?: (key to rule)

        data class Bucket(var damage: Int = 0, var prevented: Int = 0, var healed: Int = 0, var applied: Int = 0, val fired: MutableSet<Int> = HashSet())
        val buckets = LinkedHashMap<Pair<String, String?>, Bucket>()
        val shared = Bucket()
        fun bucket(who: Pair<String, String?>?): Bucket = if (who == null) shared else buckets.getOrPut(who) { Bucket() }

        // Who put the stacks an actor holds, while it holds any; emptied, it forgets.
        val stacks = HashMap<Pair<String, Stat>, Int>()
        val givers = HashMap<Pair<String, Stat>, MutableSet<Pair<String, String?>>>()
        result.actors.forEach { a -> a.startStats.forEach { (s, n) -> if (n > 0) stacks[a.key to s] = n } }
        fun gain(target: String, stat: Stat, n: Int, by: Pair<String, String?>?) {
            stacks[target to stat] = (stacks[target to stat] ?: 0) + n
            // A stack nobody of the party gave (an enemy's, the night's own) makes the pile nobody's.
            givers.getOrPut(target to stat) { HashSet() }.add(by ?: ("" to null))
        }
        fun lose(target: String, stat: Stat, n: Int) {
            val left = ((stacks[target to stat] ?: 0) - n).coerceAtLeast(0)
            stacks[target to stat] = left
            if (left == 0) givers.remove(target to stat)
        }
        /** Whose pile [stat] on [target] is: one member and one rule, one member (named [mixed]), or nobody's (null). */
        fun owner(target: String, stat: Stat, mixed: String): Pair<String, String?>? {
            val from = givers[target to stat] ?: return null
            if (from.any { it.first.isEmpty() }) return null
            val hands = from.map { it.first }.toSet()
            return when {
                hands.size != 1 -> null
                from.size == 1 -> from.first()
                else -> hands.first() to mixed
            }
        }
        // Stacks held from the start (a preparation, a trait) were given by nobody the record names, until they run out.
        result.actors.forEach { a -> a.startStats.forEach { (s, n) -> if (n > 0) givers.getOrPut(a.key to s) { HashSet() }.add("" to null) } }

        var enemyDamage = 0
        var tickOwner: Pair<String, String?>? = null
        for (e in result.events) {
            val t = e.target
            val by = e.source?.takeIf(party)?.let { credit(it, e.effectId) }
            // A rule fired once per event it answered: its products share a parent that is not its own.
            if (by != null && e.effectId != null && by.second == e.effectId && byId[e.parentId]?.effectId != e.effectId) bucket(by).fired += e.parentId ?: e.id
            when (e.kind) {
                EventKind.ACTION -> if (e.via == Via.TICK && t != null && e.tick != null) {
                    tickOwner = owner(t, e.tick, if (e.tick == Stat.REGEN) "#regen" else "#ticks:${e.tick.name}")
                    lose(t, e.tick, 1)
                }
                EventKind.SUMMONED -> if (t != null && e.source != null && party(e.source)) calledBy[t] = credit(e.source, e.effectId)
                EventKind.DAMAGE -> if (t != null && actors[t]?.side == Side.ENEMY) {
                    enemyDamage += e.amount
                    val thorns = e.via == Via.EFFECT && e.effectId == null && byId[e.parentId]?.let { it.kind == EventKind.STAT_SPENT && it.stat == Stat.THORNS } == true
                    val who = when {
                        e.tick != null -> tickOwner
                        thorns -> by?.let { it.first to "#thorns" }
                        else -> by
                    }
                    bucket(who).damage += e.amount
                }
                EventKind.GUARD_ABSORBED -> if (t != null) {
                    if (party(t)) bucket(owner(t, Stat.GUARD, "#guard")).prevented += e.amount
                    lose(t, Stat.GUARD, e.amount)
                }
                EventKind.GUARD_BROKEN -> if (t != null) { stacks[t to Stat.GUARD] = 0; givers.remove(t to Stat.GUARD) }
                EventKind.GUARD_GAINED -> if (t != null) gain(t, Stat.GUARD, e.amount, by)
                EventKind.STAT_GAINED -> if (t != null && e.stat != null) {
                    gain(t, e.stat, e.amount, by)
                    if (by != null && actors[t]?.side == Side.ENEMY) bucket(by).applied += e.amount
                }
                EventKind.STAT_SPENT -> if (t != null && e.stat != null) lose(t, e.stat, e.amount)
                // A returned actor comes back with no states: what it held is nobody's any more.
                EventKind.RETURNED -> if (t != null) { stacks.keys.filter { it.first == t }.forEach { stacks.remove(it) }; givers.keys.filter { it.first == t }.forEach { givers.remove(it) } }
                EventKind.HEALED -> if (t != null && party(t)) {
                    bucket(if (e.tick != null) tickOwner else by).healed += e.amount
                }
                else -> Unit
            }
        }

        fun name(rule: String?): String = when {
            rule == null -> OWN
            rule == "#thorns" -> "Thorns"
            rule == "#guard" -> "Armor"
            rule == "#regen" -> "Regeneration"
            rule.startsWith("#ticks:") -> runCatching { Terms.stat(Stat.valueOf(rule.removePrefix("#ticks:"))) + " damage" }.getOrDefault(rule)
            else -> result.rules[rule]?.let { LegacyProse.ruleName(rule, it) } ?: rule
        }
        val members = result.actors.filter { it.side == Side.PARTY && !it.summoned }.map { a ->
            val shares = buckets.filterKeys { it.first == a.key }.map { (k, b) ->
                val rule = k.second
                Share(rule, name(rule), rule != null && rule in a.weaponRules, b.damage, b.prevented, b.healed, b.applied, if (rule == null || rule.startsWith("#")) 0 else b.fired.size)
            }.filter { it.total > 0 || it.applied > 0 }.sortedWith(compareByDescending<Share> { it.total }.thenByDescending { it.applied }.thenBy { it.name })
            Contribution(a.key, a.name, a.weapon, a.weaponId, shares.sumOf { it.damage }, shares.sumOf { it.prevented }, shares.sumOf { it.healed }, shares, a.downed)
        }
        val sharedShare = Share("#shared", "Shared", false, shared.damage, shared.prevented, shared.healed, 0, 0)
        return FightAccount(members, sharedShare, enemyDamage, observations(result, members))
    }

    /**
     * The one part of the fight the record shows most plainly, for a card that has room for a single line (the siege
     * outcome). Read from the account alone, so it is the same for the same record every time it is opened.
     *
     * The ordering, first tier that has a candidate wins; inside a tier the larger amount, then the defender listed first:
     *  1. a blade's rule by its recorded total (damage, Guard it took for the party and effective healing, each counted
     *     once). Support competes with damage by what the record counted, and the line names the part that is largest,
     *     against the whole of that kind in the fight ("16 of the 193 damage the enemy took").
     *  2. a blade's rule that only left states on the enemy (stacks put on, nothing counted yet).
     *  3. a defender's own part (their moves or a rule that is not the blade's), named for the defender and not the blade.
     * Shared damage, Guard or healing the record cannot give to one hand is never a candidate. A contribution is
     * what was counted in the fight: it is not a claim that it decided the fight.
     */
    fun standout(account: FightAccount): Standout? {
        val members = account.members
        weaponLead(members)?.let { (m, s) -> return Standout(StandoutKind.WEAPON_RULE, m, s, "${m.name}'s ${m.weapon ?: "weapon"} ${partOf(s, account)} with its ${s.name} effect.${firedAfter(s)}") }
        members.flatMap { m -> m.shares.filter { it.weapon && it.applied > 0 }.map { m to it } }
            .maxWithOrNull(compareBy<Pair<Contribution, Share>> { it.second.applied }.thenBy { -members.indexOf(it.first) })?.let { (m, s) ->
                val put = members.sumOf { c -> c.shares.sumOf { it.applied } }
                return Standout(StandoutKind.WEAPON_STATES, m, s, "${m.name}'s ${m.weapon ?: "weapon"} put ${s.applied} of the party's $put ${if (put == 1) "status" else "statuses"} on enemies with its ${s.name} effect.${firedAfter(s)}")
            }
        members.flatMap { m -> m.shares.filter { !it.weapon && it.total > 0 }.map { m to it } }
            .maxWithOrNull(compareBy<Pair<Contribution, Share>> { it.second.total }.thenBy { -members.indexOf(it.first) })?.let { (m, s) ->
                val whose = if (s.ruleId == null) "${m.name}'s own abilities" else "${m.name}'s ${s.name}"
                return Standout(StandoutKind.HERO_PART, m, s, "$whose ${partOf(s, account)}.${firedAfter(s)}")
            }
        return null
    }

    /** The share's largest part against the whole of that kind in the fight, so a small part is never read as a large one. */
    private fun partOf(s: Share, account: FightAccount): String {
        val taken = account.members.sumOf { it.prevented } + account.shared.prevented
        val mended = account.members.sumOf { it.healed } + account.shared.healed
        return listOf(
            s.damage to "dealt ${s.damage} of the ${account.enemyDamage} damage the enemies took",
            s.prevented to "blocked ${s.prevented} of the $taken damage the party's Armor took",
            s.healed to "healed ${s.healed} of the $mended health the party healed",
        ).maxBy { it.first }.second
    }

    /** " The effect triggered 4 times." after a sentence, or nothing for a share that is not one effect's. */
    private fun firedAfter(s: Share): String = if (s.times > 0) " The effect triggered ${if (s.times == 1) "once" else "${s.times} times"}." else ""

    /** The blade rule that did the most, the one first listed when equal. */
    private fun weaponLead(members: List<Contribution>): Pair<Contribution, Share>? =
        members.flatMap { m -> m.shares.filter { it.weapon && it.total > 0 }.map { m to it } }
            .maxWithOrNull(compareBy<Pair<Contribution, Share>> { it.second.total }.thenBy { -members.indexOf(it.first) })

    private fun whatItDid(s: Share): String =
        listOf(s.damage to "dealt ${s.damage} damage", s.prevented to "blocked ${s.prevented} damage", s.healed to "healed ${s.healed}").maxBy { it.first }.second

    /** The report's "Worth knowing" line. */
    private fun weaponLine(m: Contribution, s: Share): String = "${m.name}'s ${m.weapon ?: "weapon"} ${whatItDid(s)} with its ${s.name} effect.${firedAfter(s)}"

    /** At most two facts worth knowing about the blades, in the order a smith can act on them. */
    private fun observations(result: FightResult, members: List<Contribution>): List<String> {
        val out = ArrayList<String>()
        // The blade rule that did the most.
        weaponLead(members)?.let { (m, s) -> out += weaponLine(m, s) }
        val party = result.actors.filter { it.side == Side.PARTY }.associateBy { it.key }
        val hits = result.events.filter { it.tick == null && (it.kind == EventKind.DAMAGE || it.kind == EventKind.GUARD_ABSORBED) && it.source in party.keys && result.actor(it.target ?: "")?.side == Side.ENEMY }
        // A blade whose element the enemy resists is worth knowing before the next siege.
        hits.filter { it.affinity == Affinity.RESISTED }.groupBy { it.source!! }.maxByOrNull { it.value.size }?.takeIf { it.value.size >= 2 }?.let { (k, list) ->
            out += "${list.size} of ${party.getValue(k).name}'s hits were resisted."
        }
        hits.filter { it.affinity == Affinity.WEAK }.groupBy { it.source!! }.maxByOrNull { it.value.size }?.takeIf { it.value.size >= 2 }?.let { (k, list) ->
            out += "${list.size} of ${party.getValue(k).name}'s hits met a weakness."
        }
        // The strongest foe, and whose hand brought it down.
        val lead = result.actors.filter { it.side == Side.ENEMY && !it.summoned }.maxByOrNull { it.maxHealth }
        result.events.firstOrNull { it.kind == EventKind.DOWNED && it.target == lead?.key && it.source in party.keys }?.let { e ->
            out += "${party.getValue(e.source!!).name} defeated ${lead!!.name} in round ${e.round}."
        }
        return out.take(2)
    }
}
