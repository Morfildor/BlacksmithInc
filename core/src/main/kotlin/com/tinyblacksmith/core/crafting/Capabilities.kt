package com.tinyblacksmith.core.crafting

import com.tinyblacksmith.core.combat.Action
import com.tinyblacksmith.core.combat.Aim
import com.tinyblacksmith.core.combat.Amount
import com.tinyblacksmith.core.combat.Cadence
import com.tinyblacksmith.core.combat.Condition
import com.tinyblacksmith.core.combat.Cost
import com.tinyblacksmith.core.combat.EffectDef
import com.tinyblacksmith.core.combat.EventKind
import com.tinyblacksmith.core.combat.KitDef
import com.tinyblacksmith.core.combat.Limit
import com.tinyblacksmith.core.combat.Stat
import com.tinyblacksmith.core.combat.UnitDef
import com.tinyblacksmith.core.combat.Who
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.text.Terms

/**
 * What a weapon can do in a fight, as typed facts a requirement can be checked against (the interface a commission
 * will use). Facts are derived from the effect data the fight itself runs, never from a description. The few rules
 * the fight owns and no effect states (Steam from heat on cold, Regeneration healing, Thorns, the Mark every hit
 * spends) are written down here once, as [Capabilities.engine] facts.
 *
 * Bounds, on purpose: an enabler is looked for one step away (who puts Chill there), never further (who enables
 * the one who puts Chill there). Where the answer hangs on a second step it says so ([Need.Unverified]) and does not
 * guess. Nothing here predicts a fight. The bench shows one.
 */

/** Something a rule makes, takes or waits for. The twelve fight statuses by their own names (`Terms.stat`), and what is not a status. */
enum class Res(private val own: String? = null) {
    GUARD, CHARGE, MARK("a Mark"), BURN, REGEN, CHILL, WET, BLEED, SCRAP, THORNS, STEAM, POISON,
    HEALING("healing"), DAMAGE("damage"), FRACTURE("a cracked weapon"), HEALTH_PAID("health paid to a weapon"), HELPER("a helper"), INTERRUPT("an interrupted enemy ability");

    val word: String get() = own ?: Terms.stat(Stat.valueOf(name))

    companion object {
        fun of(stat: Stat): Res = valueOf(stat.name)
    }
}

enum class Verb { PROVIDES, SPENDS }

/** Whom a fact reaches, seen from the wielder. [others] is true where an ally who is not the wielder is sure to be reached. */
enum class On(val party: Boolean, val others: Boolean = false, val back: Boolean = false) {
    SELF(true), WEAKEST_ALLY(true), AN_ALLY(true, others = true), OTHER_ALLIES(true, others = true), ALLIES(true, others = true),
    FOE(false), MARKED_FOE(false), WEAKEST_FOE(false), PRIORITY_FOE(false), BACK_FOE(false, back = true), ALL_FOES(false, back = true),
}

enum class Place { WIELDER, PARTY, FOE }

/** What must be true for a fact to come about. A requirement is typed too: "answers healing" is a need, never a provision. */
sealed interface Need {
    /**
     * One of [any] has to be at [place]. [beforeHit]: it must already be there when the wielder's hit lands, so the
     * wielder's own hit cannot be what puts it there unless the wielder strikes twice in an action. [byWielder]: only
     * the wielder's own weapon or class counts. [ownAction]: only the wielder's class action counts. [atLeast]: how
     * much has to gather before it is spent. More than one needs a source that repeats: a rule that gives once a fight
     * cannot fill it.
     */
    data class Present(val any: Set<Res>, val place: Place, val beforeHit: Boolean = false, val byWielder: Boolean = false, val ownAction: Boolean = false, val atLeast: Int = 1) : Need
    /** Somebody has to spend [res]: the wielder ([byWielder]) or anyone of the party. */
    data class Spent(val res: Res, val byWielder: Boolean) : Need
    data class FoeIs(val tag: String) : Need
    data class HolderIs(val tag: String) : Need
    /** A circumstance no party member can supply, in words. */
    data class Circumstance(val text: String) : Need
    /** Something in the company answers [need], but only after a step of its own that is not followed here. Not a yes and not a no. */
    data class Unverified(val need: Need) : Need
}

/**
 * One fact about one rule. [from] is what the rule consumed to make it (a conversion). [engine] marks a fact the
 * fight's own rules add to what the effect states.
 */
data class Facet(
    val ruleId: String, val verb: Verb, val res: Res, val on: On, val needs: List<Need> = emptyList(), val from: Res? = null, val limit: Limit = Limit(),
    val where: WeaponRules.Where = WeaponRules.Where.ANYWHERE, val pierce: Boolean = false, val engine: Boolean = false,
    /** How much one firing gives, where the rule names a number. */
    val amount: Int = 1,
)

/** A question about a weapon. Every one is answered from [Facet]s. */
sealed interface Query {
    /** Can it make [res], at [place] when one is named? */
    data class Generates(val res: Res, val place: Place? = null) : Query
    data class Spends(val res: Res) : Query
    /** Does it shield, heal or mend an ally who is not the wielder? */
    data object ProtectsAnother : Query
    /** Does one of its rules wait for [res] (incoming healing, a cracked weapon, Chill on the target)? */
    data class Requires(val res: Res) : Query
    data class Converts(val from: Res, val to: Res) : Query
    data object ReachesBackline : Query
}

/**
 * An answer, named for what it promises. [Available]: the weapon has the rule and the company brings every piece it
 * waits for. Whether its moment comes in a given fight is not promised. [Needs]: the weapon has the rule and
 * [Needs.missing] is what the company still has to bring, or what could not be checked. [CannotProvide]: no rule of it does this.
 */
sealed interface Verdict {
    data class Available(val by: List<Facet>) : Verdict
    data class Needs(val by: List<Facet>, val missing: List<Need>) : Verdict
    data class CannotProvide(val reason: String) : Verdict
}

/** Who stands with the weapon: the wielder's class, the allies with what they carry, the party's relics, the enemy when known. */
data class Fighter(val kit: KitDef?, val weapon: WeaponRules.Composition? = null)
data class Company(val wielder: KitDef? = null, val allies: List<Fighter> = emptyList(), val relics: List<EffectDef> = emptyList(), val foes: List<UnitDef>? = null, val home: Boolean = false)

/** What a composition says about itself beyond its rules: a rule shared, a state two rules both take, a weapon that undoes its own work. */
sealed interface CompositionNote {
    data class Shared(val ruleId: String, val sources: List<String>) : CompositionNote
    /** Rules that take the same state at the same place, in the order the fight asks them. */
    data class Contest(val res: Res, val ruleIds: List<String>) : CompositionNote
    /** [takerId] takes what [giverId] puts there. */
    data class Feeds(val res: Res, val giverId: String, val takerId: String) : CompositionNote
    /** The weapon puts both Burn and Chill on enemies: they cancel into Steam. */
    data object BoilsItself : CompositionNote
    /** Several rules crack the weapon. It cracks once. */
    data class CracksOnce(val ruleIds: List<String>) : CompositionNote
}

object Capabilities {
    private val HARMFUL = setOf(Stat.BURN, Stat.CHILL, Stat.MARK, Stat.BLEED, Stat.WET)

    /** The Mark any direct hit spends for the rules' bonus: a fact about every fighter, stated by no effect. */
    val MARK_SPENT = Facet("rule_mark", Verb.SPENDS, Res.MARK, On.FOE, listOf(Need.Present(setOf(Res.MARK), Place.FOE)), engine = true)

    private fun on(aim: Aim, helpful: Boolean, e: EffectDef?): On = when (aim) {
        Aim.Self -> On.SELF
        Aim.WeakestAlly -> On.WEAKEST_ALLY
        Aim.AllAllies -> On.ALLIES
        Aim.OtherAllies -> On.OTHER_ALLIES
        is Aim.AllyTagged -> On.AN_ALLY
        Aim.Foe -> On.FOE
        Aim.MarkedFoe -> On.MARKED_FOE
        Aim.AllFoes -> On.ALL_FOES
        Aim.WeakestFoe -> On.WEAKEST_FOE
        Aim.BackFoe -> On.BACK_FOE
        Aim.PriorityFoe -> On.PRIORITY_FOE
        Aim.FrontFoe, Aim.PackPrey -> On.FOE
        is Aim.FrontFoes -> if (aim.count > 1) On.ALL_FOES else On.FOE
        is Aim.WeakestAllyTagged, is Aim.WeakestAllyHolding -> On.WEAKEST_ALLY
        is Aim.WeakestOtherAlly -> On.AN_ALLY
        // The event's own subject: an enemy for what harms, an ally for what helps. The wielder itself when the rule answers what happens to the wielder.
        Aim.EventTarget, Aim.EventSource -> if (!helpful) On.FOE else if (aim == Aim.EventTarget && e?.trigger?.who == Who.SELF_TARGET) On.SELF else On.AN_ALLY
    }

    /**
     * [sameHit]: the rule is asked after the weapon's element on the same hit (a buff, a flaw, a catalyst, a signature),
     * so it finds what the element has just put on the target. A family's or a metal's rule is asked before it and does not.
     */
    private fun needs(e: EffectDef, sameHit: Boolean): List<Need> = buildList {
        val t = e.trigger
        val ownAction = Condition.FromAction in e.conditions
        when (t.kind) {
            EventKind.HEALED, EventKind.OVERHEAL -> when (t.who) {
                Who.SELF_TARGET -> add(Need.Present(setOf(Res.HEALING), Place.WIELDER))
                Who.ALLY_TARGET -> add(Need.Present(setOf(Res.HEALING), Place.PARTY))
                Who.SELF_SOURCE -> add(Need.Present(setOf(Res.HEALING), Place.PARTY, byWielder = true, ownAction = ownAction))
                else -> {}
            }
            EventKind.GUARD_BROKEN -> when (t.who) {
                Who.SELF_TARGET -> add(Need.Present(setOf(Res.GUARD), Place.WIELDER))
                Who.ALLY_TARGET -> add(Need.Present(setOf(Res.GUARD), Place.PARTY))
                else -> add(Need.Present(setOf(Res.GUARD), Place.FOE))
            }
            EventKind.GUARD_ABSORBED -> if (t.who == Who.SELF_SOURCE || t.who == Who.ALLY_SOURCE) add(Need.Present(setOf(Res.GUARD), Place.FOE))
            EventKind.GUARD_GAINED -> if (t.who == Who.SELF_TARGET) add(Need.Present(setOf(Res.GUARD), Place.WIELDER, byWielder = ownAction, ownAction = ownAction))
            EventKind.STAT_GAINED -> {
                val stats = t.stat?.let { setOf(it) } ?: e.conditions.filterIsInstance<Condition.StatIn>().firstOrNull()?.stats
                if (stats != null) when (t.who) {
                    Who.SELF_TARGET -> add(Need.Present(stats.map(Res::of).toSet(), Place.WIELDER))
                    Who.SELF_SOURCE -> add(Need.Present(stats.map(Res::of).toSet(), Place.FOE, byWielder = true))
                    Who.FOE_TARGET, Who.ALLY_SOURCE -> add(Need.Present(stats.map(Res::of).toSet(), Place.FOE))
                    else -> {}
                }
            }
            EventKind.STAT_SPENT -> t.stat?.let { add(Need.Spent(Res.of(it), byWielder = t.who == Who.SELF_SOURCE)) }
            EventKind.FRACTURED -> add(Need.Present(setOf(Res.FRACTURE), Place.PARTY))
            else -> {}
        }
        for (c in e.conditions) when (c) {
            is Condition.TargetHas -> add(Need.Present(setOf(Res.of(c.stat)), Place.FOE, beforeHit = !sameHit && t.kind == EventKind.DAMAGE && t.who == Who.SELF_SOURCE))
            is Condition.HolderHas -> add(Need.Present(setOf(Res.of(c.stat)), Place.WIELDER))
            is Condition.Happened -> when (c.kind) {
                EventKind.FRACTURED -> add(Need.Present(setOf(Res.FRACTURE), Place.PARTY))
                EventKind.HEALTH_PAID -> add(Need.Present(setOf(Res.HEALTH_PAID), Place.PARTY))
                else -> {}
            }
            is Condition.TargetTagged -> add(Need.FoeIs(c.tag))
            is Condition.HolderTagged -> add(Need.HolderIs(c.tag))
            else -> {}
        }
        (e.cost as? Cost.Spend)?.let { add(Need.Present(setOf(Res.of(it.stat)), Place.WIELDER, atLeast = it.amount)) }
        for (a in e.actions) if (a is Action.Take && !on(a.target, false, e).party) add(Need.Present(setOf(Res.of(a.stat)), Place.FOE))
    }.distinct().let { all ->
        // The same thing asked twice (by a condition and by the take it guards, by a trigger and by the cost it pays) is one need: the stricter one.
        all.filterNot { n -> n is Need.Present && all.any { it is Need.Present && it != n && it.any == n.any && it.place == n.place && (it.beforeHit && !n.beforeHit || it.atLeast > n.atLeast) } }
    }

    /** Every fact of [e], a rule on a weapon, a class passive, a relic or an enemy's own rule. */
    fun facets(e: EffectDef, where: WeaponRules.Where = WeaponRules.Where.ANYWHERE, sameHit: Boolean = true): List<Facet> = facets(e.id, e.actions, e, needs(e, sameHit), e.limit, where)

    /** The facts of a class's or an enemy's moves and passives. A move's cadence is not a need: it comes round. */
    fun facets(kit: KitDef): List<Facet> = moves(kit) + kit.passives.flatMap { facets(it) }

    private fun moves(kit: KitDef): List<Facet> = kit.moves.flatMap { facets("kit:${kit.id}:${it.id}", it.actions, null, emptyList(), if (it.cadence == Cadence.Opener) Limit(perFight = 1) else Limit(perRound = 1), WeaponRules.Where.ANYWHERE) }

    private fun facets(id: String, actions: List<Action>, e: EffectDef?, needs: List<Need>, limit: Limit, where: WeaponRules.Where): List<Facet> {
        val out = ArrayList<Facet>()
        val spend = e?.cost as? Cost.Spend
        val takes = actions.filterIsInstance<Action.Take>()
        // What the rule turns into something else: its cost, what it takes, or the spending it answers.
        val from = spend?.let { Res.of(it.stat) } ?: takes.firstOrNull()?.let { Res.of(it.stat) } ?: e?.trigger?.takeIf { it.kind == EventKind.STAT_SPENT }?.stat?.let(Res::of)
        fun fact(verb: Verb, res: Res, on: On, pierce: Boolean = false, source: Res? = from, more: List<Need> = emptyList(), engine: Boolean = false, amount: Amount? = null) {
            out += Facet(id, verb, res, on, (needs + more).distinct(), if (verb == Verb.PROVIDES) source else null, limit, where, pierce, engine, (amount as? Amount.Fixed)?.value ?: 1)
        }
        spend?.let { fact(Verb.SPENDS, Res.of(it.stat), On.SELF) }
        if (e?.cost is Cost.PayHealth) fact(Verb.PROVIDES, Res.HEALTH_PAID, On.SELF)
        for (a in actions) when (a) {
            is Action.Take -> fact(Verb.SPENDS, Res.of(a.stat), on(a.target, a.stat !in HARMFUL, e))
            is Action.Damage -> fact(Verb.PROVIDES, Res.DAMAGE, on(a.target, false, e), a.pierce)
            is Action.Heal -> fact(Verb.PROVIDES, Res.HEALING, on(a.target, true, e))
            is Action.Guard -> fact(Verb.PROVIDES, Res.GUARD, on(a.target, true, e), amount = a.amount)
            is Action.Give -> {
                val at = on(a.target, a.stat !in HARMFUL, e)
                fact(Verb.PROVIDES, Res.of(a.stat), at, amount = a.amount)
                // The fight's own rules, which no effect states.
                when {
                    a.stat == Stat.REGEN -> fact(Verb.PROVIDES, Res.HEALING, at, source = Res.REGEN, more = listOf(Need.Circumstance("Regeneration heals only a fighter who has lost health.")), engine = true)
                    a.stat == Stat.THORNS -> fact(Verb.PROVIDES, Res.DAMAGE, On.FOE, source = Res.THORNS, more = listOf(Need.Circumstance("Thorns damage the next enemy whose attack hits their holder.")), engine = true)
                    a.stat == Stat.BURN && !at.party -> fact(Verb.PROVIDES, Res.STEAM, On.SELF, source = Res.BURN, more = listOf(Need.Present(setOf(Res.CHILL, Res.WET), Place.FOE)), engine = true)
                    a.stat == Stat.CHILL && !at.party -> fact(Verb.PROVIDES, Res.STEAM, On.SELF, source = Res.CHILL, more = listOf(Need.Present(setOf(Res.BURN), Place.FOE)), engine = true)
                }
            }
            is Action.Echo -> e?.conditions?.filterIsInstance<Condition.StatIn>()?.firstOrNull()?.stats?.forEach { fact(Verb.PROVIDES, Res.of(it), On.FOE) }
            is Action.Fracture -> { fact(Verb.PROVIDES, Res.FRACTURE, On.SELF); fact(Verb.PROVIDES, Res.SCRAP, On.SELF) }
            is Action.Summon -> fact(Verb.PROVIDES, Res.HELPER, On.SELF)
            is Action.Interrupt -> fact(Verb.PROVIDES, Res.INTERRUPT, on(a.target, false, e), more = listOf(Need.Circumstance("It needs an enemy that is preparing an ability.")))
            // Enemy grammar: nothing a weapon's facts are asked about.
            is Action.Structural, is Action.Return, is Action.MarkPrey -> {}
        }
        return out
    }

    fun facets(c: WeaponRules.Composition, home: Boolean = false): List<Facet> =
        c.rules(home).flatMap { facets(it.effect, it.where, sameHit = it.source != WeaponRules.Source.FAMILY && it.source != WeaponRules.Source.CORE) }

    fun facets(unit: UnitDef): List<Facet> = facets(unit.kit) + unit.effects.flatMap { facets(it) }

    // ---- the evaluator ----

    private class Pools(val weapon: List<Facet>, val kit: List<Facet>, val others: List<Facet>, val foes: List<Facet>?, val twice: Boolean, val foeTags: Set<String>?)

    // A class that strikes the foe in front twice in one action: its second hit finds what its first put there.
    private fun twice(kit: KitDef?) = kit?.moves?.any { m -> m.actions.count { it is Action.Damage && it.target == Aim.Foe } >= 2 } == true

    private fun pools(subject: WeaponRules.Composition, company: Company): Pools {
        // Of an enemy only what its moves do round after round counts (a line that raises Guard). What it does to itself once, when its leader falls, is not something to build on.
        val foes = company.foes?.flatMap { moves(it.kit) }
        val foeTags = company.foes?.flatMap { it.tags + it.kit.tags }?.toSet()
        // An ally's rule counts when the ally can bring it about where the ally stands: its own class and weapon are what "the wielder" means for it.
        val allies = company.allies.flatMap { a ->
            val kit = a.kit?.let { facets(it) + MARK_SPENT }.orEmpty()
            val weapon = a.weapon?.let { facets(it, company.home) }.orEmpty()
            val standing = Pools(weapon, kit, emptyList(), foes, twice(a.kit), foeTags)
            (kit + weapon).filter { ready(it, standing, 1) }
        }
        return Pools(
            weapon = facets(subject, company.home), kit = company.wielder?.let { facets(it) + MARK_SPENT }.orEmpty(),
            others = allies + company.relics.flatMap { facets(it) }, foes = foes, twice = twice(company.wielder), foeTags = foeTags,
        )
    }

    /** Every fact in [p] that could answer [need], whether or not its own needs are met. */
    private fun candidates(need: Need, p: Pools): List<Facet> = when (need) {
        is Need.Present -> {
            // A rule that only adds to what is already there (an echo, a fire that spreads) is not where it comes from.
            fun gives(f: Facet) = f.verb == Verb.PROVIDES && f.res in need.any && f.needs.none { it is Need.Present && it.place == need.place && it.any.any { r -> r in need.any } }
            // What must be there before the hit: the wielder's own rules count when the wielder strikes twice in an action,
            // or when two of them stack it in one round, so that one is left after the enemy's strike has spent the other.
            val stacked = (p.weapon + p.kit).filter { gives(it) && !it.on.party }.map { it.ruleId }.distinct().size >= 2
            val own = if (need.ownAction) p.kit else if (need.beforeHit && !p.twice && !stacked) emptyList() else p.weapon + p.kit
            val allies = if (need.byWielder || need.ownAction) emptyList() else p.others
            when (need.place) {
                // The wielder's own rules reach the wielder wherever they aim on its side. An ally's reach it unless they aim at themselves.
                Place.WIELDER -> own.filter { gives(it) && it.on.party } + allies.filter { gives(it) && it.on.party && it.on != On.SELF }
                Place.PARTY -> (own + allies).filter { gives(it) && it.on.party }
                Place.FOE -> (own + allies).filter { gives(it) && !it.on.party } + (if (need.byWielder) emptyList() else p.foes.orEmpty().filter { gives(it) && it.on.party })
            }
        }
        is Need.Spent -> (if (need.byWielder) p.weapon + p.kit else p.weapon + p.kit + p.others).filter { it.verb == Verb.SPENDS && it.res == need.res }
        is Need.FoeIs, is Need.HolderIs, is Need.Circumstance, is Need.Unverified -> emptyList()
    }

    /** One step and no further: what answers a need must itself have what it waits for, and what answers that is taken on trust. */
    private fun ready(f: Facet, p: Pools, depth: Int): Boolean = depth <= 0 || f.needs.all { it is Need.Circumstance || met(it, p, depth - 1) }

    private fun met(need: Need, p: Pools, depth: Int = 1): Boolean = when (need) {
        // What has to gather needs a source that keeps giving, or enough one-time gifts to add up.
        is Need.Present -> candidates(need, p).filter { ready(it, p, depth) }.let { c -> if (need.atLeast <= 1) c.isNotEmpty() else c.any { it.limit.perFight == 0 } || c.sumOf { it.amount * it.limit.perFight } >= need.atLeast }
        is Need.Spent -> candidates(need, p).any { ready(it, p, depth) }
        is Need.FoeIs -> p.foeTags?.contains(need.tag) == true
        is Need.HolderIs, is Need.Circumstance, is Need.Unverified -> false
    }

    /**
     * Whether one step is enough to know. What answers [need] may wait for something itself: that is fine when what it
     * waits for is brought by a rule that waits for nothing. A longer chain is not followed.
     */
    private fun established(need: Need, p: Pools): Boolean = need !is Need.Present && need !is Need.Spent || candidates(need, p).any { c ->
        c.needs.all { n -> n is Need.Circumstance || if (n is Need.Present || n is Need.Spent) candidates(n, p).any { it.needs.all { last -> last is Need.Circumstance } } else met(n, p, 0) }
    }

    /**
     * What is still open behind [need]: nothing when it is met and one step shows it. When the weapon has the answer and
     * the answer is what lacks something, that. When the answer hangs on a second step, the need itself as [Need.Unverified].
     */
    private fun open(need: Need, p: Pools): List<Need> {
        if (met(need, p)) return if (established(need, p)) emptyList() else listOf(Need.Unverified(need))
        val behind = candidates(need, p).firstOrNull()?.needs?.filterNot { it is Need.Circumstance || met(it, p, 0) }.orEmpty()
        return behind.ifEmpty { listOf(need) }
    }

    private fun matching(q: Query, weapon: List<Facet>): List<Facet> = when (q) {
        is Query.Generates -> weapon.filter { f -> f.verb == Verb.PROVIDES && f.res == q.res && when (q.place) { null -> true; Place.WIELDER -> f.on == On.SELF; Place.PARTY -> f.on.party; Place.FOE -> !f.on.party } }
        is Query.Spends -> weapon.filter { it.verb == Verb.SPENDS && it.res == q.res }
        Query.ProtectsAnother -> weapon.filter { it.verb == Verb.PROVIDES && it.res in setOf(Res.GUARD, Res.HEALING, Res.REGEN) && it.on.party && it.on != On.SELF }
        is Query.Requires -> weapon.filter { f -> f.needs.any { n -> n is Need.Present && q.res in n.any || n is Need.Spent && n.res == q.res } }
        is Query.Converts -> weapon.filter { it.verb == Verb.PROVIDES && it.res == q.to && it.from == q.from }
        Query.ReachesBackline -> weapon.filter { it.verb == Verb.PROVIDES && it.res == Res.DAMAGE && it.on.back }
    }

    private fun reason(q: Query): String = when (q) {
        is Query.Generates -> "Nothing on it makes ${q.res.word}${mapOf(Place.WIELDER to " for the wielder", Place.PARTY to " for the party", Place.FOE to " on an enemy")[q.place].orEmpty()}."
        is Query.Spends -> "Nothing on it consumes ${q.res.word}."
        Query.ProtectsAnother -> "Nothing on it gives Armor or healing to anybody but the wielder."
        is Query.Requires -> "Nothing on it waits for ${q.res.word}."
        is Query.Converts -> "Nothing on it turns ${q.from.word} into ${q.to.word}."
        Query.ReachesBackline -> "Nothing on it hits past the front enemy. A Mark on the back enemy would make the party attack that enemy first."
    }

    /**
     * Pure. [Verdict.Available] when a rule of [subject] answers [q] and [company] brings everything that rule waits
     * for. [Verdict.Needs] names what is missing for the rule that lacks the least. A question about what the
     * weapon needs ([Query.Requires]) is answered yes or no: it has no enablers of its own.
     */
    fun evaluate(q: Query, subject: WeaponRules.Composition, company: Company = Company()): Verdict {
        val p = pools(subject, company)
        val by = matching(q, p.weapon)
        if (by.isEmpty()) return Verdict.CannotProvide(reason(q))
        if (q is Query.Requires) return Verdict.Available(by)
        fun missing(f: Facet): List<Need> = f.needs.flatMap { open(it, p) }.distinct() +
            // Whoever is weakest may be the wielder: no company can promise otherwise.
            listOfNotNull(Need.Circumstance("It goes to whoever is weakest, which may be the wielder.").takeIf { q == Query.ProtectsAnother && f.on == On.WEAKEST_ALLY })
        val open = by.associateWith(::missing)
        val ready = open.filterValues { it.isEmpty() }.keys.toList()
        if (ready.isNotEmpty()) return Verdict.Available(ready)
        val least = open.values.minOf { it.size }
        return Verdict.Needs(by, open.values.first { it.size == least })
    }

    /** What every rule of [subject] still waits for in [company], by rule ID: the forge's "missing enablers". A rule with nothing open is absent. */
    fun missing(subject: WeaponRules.Composition, company: Company = Company()): Map<String, List<Need>> {
        val p = pools(subject, company)
        return p.weapon.filter { !it.engine }.groupBy { it.ruleId }.mapValues { (_, fs) -> fs.flatMap { it.needs }.distinct().flatMap { open(it, p) }.distinct() }.filterValues { it.isNotEmpty() }
    }

    fun notes(c: WeaponRules.Composition, home: Boolean = false): List<CompositionNote> {
        val fs = facets(c, home).filter { !it.engine }
        val out = ArrayList<CompositionNote>()
        c.rules(home).filter { it.alsoFrom.isNotEmpty() }.forEach { out += CompositionNote.Shared(it.id, listOf(it.sourceName) + it.alsoFrom) }
        fs.filter { it.verb == Verb.SPENDS }.groupBy { it.res to it.on.party }.values.map { g -> g.map { it.ruleId }.distinct() to g.first().res }.filter { it.first.size > 1 }
            .forEach { (ids, res) -> out += CompositionNote.Contest(res, ids) }
        for (take in fs.filter { it.verb == Verb.SPENDS && !it.on.party }) {
            fs.firstOrNull { it.verb == Verb.PROVIDES && it.res == take.res && !it.on.party && it.ruleId != take.ruleId }?.let { out += CompositionNote.Feeds(take.res, it.ruleId, take.ruleId) }
        }
        fun puts(res: Res) = fs.any { it.verb == Verb.PROVIDES && it.res == res && !it.on.party }
        if (puts(Res.BURN) && puts(Res.CHILL)) out += CompositionNote.BoilsItself
        fs.filter { it.verb == Verb.PROVIDES && it.res == Res.FRACTURE }.map { it.ruleId }.distinct().takeIf { it.size > 1 }?.let { out += CompositionNote.CracksOnce(it) }
        return out.distinct()
    }

    // ---- words ----

    /** Nothing a smith forges, hires or meets puts Wet on anybody: only a Storm front does. Ordinary guidance leaves it out where something else serves. */
    fun offered(any: Set<Res>): Set<Res> = if (any.size > 1) any - Res.WET else any

    private fun list(any: Set<Res>): String = offered(any).map { it.word }.let { if (it.size == 1) it[0] else it.dropLast(1).joinToString(", ") + " or " + it.last() }

    /** A need as the forge says it: what the company has to bring. */
    fun text(need: Need): String = when (need) {
        is Need.Present -> when {
            need.any == setOf(Res.WET) -> "Wet on ${if (need.place == Place.FOE) "an enemy" else "the party"}. Only a Storm front brings it."
            Res.HEALING in need.any && need.ownAction -> "A wielder whose own action heals."
            Res.HEALING in need.any && need.place == Place.WIELDER -> "Somebody or something that heals the wielder."
            Res.HEALING in need.any -> "A healer in the party."
            Res.FRACTURE in need.any -> "A weapon in the party that cracks."
            Res.HEALTH_PAID in need.any -> "A weapon in the party that takes health from its wielder."
            need.place == Place.FOE && Res.GUARD in need.any -> "An enemy that raises Armor."
            need.place == Place.FOE && need.beforeHit -> "${list(need.any).cap()} on the enemy before the hit lands. An ally can put it there, or a wielder who attacks twice per round."
            need.place == Place.FOE && need.byWielder -> "The wielder's own hit has to leave ${list(need.any)} on an enemy."
            need.place == Place.FOE -> "${list(need.any).cap()} on an enemy."
            need.ownAction -> "A wielder whose own action raises ${list(need.any)}."
            need.place == Place.WIELDER && Res.GUARD in need.any -> "Armor on the wielder for an enemy to break."
            need.place == Place.WIELDER && need.atLeast > 1 -> "More ${list(need.any)} for the wielder. It takes ${need.atLeast} to spend."
            need.place == Place.WIELDER -> "A way for the wielder to get ${list(need.any)}."
            else -> "${list(need.any).cap()} on somebody in the party."
        }
        is Need.Spent -> if (need.res == Res.MARK) "A Mark on an enemy for the wielder's hit to consume." else if (need.byWielder) "An effect on this weapon that consumes ${need.res.word}." else "Somebody in the party who consumes ${need.res.word}."
        is Need.FoeIs -> when (need.tag) { "elite" -> "An Elite enemy."; "undead" -> "One of the Hollowbound among the enemies."; else -> "An enemy of the right kind." }
        is Need.HolderIs -> "A wielder of the right kind."
        is Need.Circumstance -> need.text
        is Need.Unverified -> "${text(need.need)} The company can bring it, but only through another effect first. That is not checked here. Try it on the bench to see it."
    }

    /** A verdict as a smith reads it. "Available" never promises that the moment comes. */
    fun text(v: Verdict, c: WeaponRules.Composition): String = when (v) {
        is Verdict.Available -> "Available when its trigger happens: ${v.by.map { c.rule(it.ruleId)?.effect?.let { e -> CombatContent.label(e.id, e.name) } ?: it.ruleId }.distinct().joinToString(", ")}."
        is Verdict.Needs -> "Needs: ${v.missing.joinToString(" ") { text(it) }}"
        is Verdict.CannotProvide -> "Cannot provide. ${v.reason}"
    }

    private fun String.cap() = replaceFirstChar { it.uppercase() }

    fun text(note: CompositionNote, c: WeaponRules.Composition): String {
        fun name(id: String) = c.rule(id)?.effect?.name ?: id
        return when (note) {
            is CompositionNote.Shared -> "${name(note.ruleId)} comes with both ${note.sources.joinToString(" and ")}. It counts once."
            is CompositionNote.Contest -> "${note.ruleIds.joinToString(" and ") { name(it) }} both consume ${note.res.word}. ${name(note.ruleIds.first())} goes first and the other gets what is left."
            is CompositionNote.Feeds -> "${name(note.takerId)} consumes the ${note.res.word} that ${name(note.giverId)} puts on enemies. That ${note.res.word} is used up and no longer works as ${note.res.word}."
            CompositionNote.BoilsItself -> "This weapon applies both Burn and Chill to enemies. When they meet on one enemy, they cancel each other one for one and the attacker gains that much Steam."
            is CompositionNote.CracksOnce -> "${note.ruleIds.joinToString(" and ") { name(it) }} would both crack this weapon. It cracks at most once per fight."
        }
    }
}
