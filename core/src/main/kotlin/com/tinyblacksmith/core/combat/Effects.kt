package com.tinyblacksmith.core.combat

import com.tinyblacksmith.core.content.Element

/**
 * The effect grammar (spec 6.3): trigger, conditions, cost, actions, limit. Everything is typed; an effect is data and
 * [Fight] is its only interpreter. "Holder" is the actor the effect belongs to. For a party-wide effect
 * ([EffectDef.party]) the holder is the party member the triggering event is about.
 */

/** Whom the triggering event must be about, seen from the holder. An ally includes the holder. */
enum class Who { SELF_TARGET, SELF_SOURCE, ALLY_TARGET, ALLY_SOURCE, FOE_TARGET, FOE_SOURCE, ANY }

/** [stat] narrows a STAT_GAINED or STAT_SPENT trigger to one state. */
data class Trigger(val kind: EventKind, val who: Who, val stat: Stat? = null)

sealed interface Condition {
    /** The event was caused by the other side (enemy damage, never a cost the holder paid itself). */
    data object SourceIsFoe : Condition
    /** The event came straight from an action, not from another effect or a tick. */
    data object FromAction : Condition
    /** The event was a direct hit: an action or an effect's strike, not a tick of Burn or Bleed. */
    data object NotTick : Condition
    data class HolderHas(val stat: Stat, val atLeast: Int = 1) : Condition
    data class HolderLacks(val stat: Stat) : Condition
    data class TargetHas(val stat: Stat, val atLeast: Int = 1) : Condition
    data class StatIn(val stats: Set<Stat>) : Condition
    data class HolderHealthAtMost(val percent: Int) : Condition
    data class TargetHealthAtMost(val percent: Int) : Condition
    data class AmountAtLeast(val amount: Int) : Condition
    /** An event of this kind about the holder's side has already happened in this fight. */
    data class Happened(val kind: EventKind) : Condition
    data class HolderTagged(val tag: String) : Condition
    data class TargetTagged(val tag: String) : Condition
}

sealed interface Cost {
    /** The holder spends [amount] of [stat]; with [all], everything it has (at least [amount]). Paid before the action. */
    data class Spend(val stat: Stat, val amount: Int, val all: Boolean = false) : Cost
    /** The holder pays up to [amount] health but never below [floor]. A HEALTH_PAID event, not damage. */
    data class PayHealth(val amount: Int, val floor: Int) : Cost
}

sealed interface Amount {
    data class Fixed(val value: Int) : Amount
    /** A share of the holder's strike. */
    data class Strike(val percent: Int = 100) : Amount
    /** A share of the holder's support number. */
    data class Support(val percent: Int = 100) : Amount
    /** A share of the triggering event's amount, at most [cap]. */
    data class OfEvent(val percent: Int = 100, val cap: Int = Int.MAX_VALUE) : Amount
    /** [perPoint] for every point the cost took. */
    data class OfSpent(val perPoint: Int = 1) : Amount
    data class OfHolderStat(val stat: Stat, val perStack: Int = 1) : Amount
}

sealed interface Aim {
    data object Self : Aim
    data object EventSource : Aim
    data object EventTarget : Aim
    /** The living ally with the least health for its size, the holder included. */
    data object WeakestAlly : Aim
    data object AllAllies : Aim
    data object OtherAllies : Aim
    /** The first living ally with [tag]; nobody when none has it. */
    data class AllyTagged(val tag: String) : Aim
    /**
     * The foe this actor would strike. For the party: a marked enemy first, else the first standing. For an enemy:
     * a party member who intercepts (tag `intercept`, Guard up) first, else the first standing.
     */
    data object Foe : Aim
    /** A marked foe only; nobody when none is marked. */
    data object MarkedFoe : Aim
    data object AllFoes : Aim
    data object WeakestFoe : Aim
    /** The last standing foe in the line: past the front, no interception. */
    data object BackFoe : Aim
    /** The foe that matters most: tagged `anchor`, then `leader`, then the one with the most health. */
    data object PriorityFoe : Aim
}

sealed interface Action {
    /** [pierce] passes Guard. A direct hit spends a Mark on its target for the rules' bonus. */
    data class Damage(val target: Aim, val amount: Amount, val element: Element? = null, val pierce: Boolean = false) : Action
    data class Heal(val target: Aim, val amount: Amount) : Action
    data class Guard(val target: Aim, val amount: Amount) : Action
    data class Give(val stat: Stat, val target: Aim, val amount: Amount = Amount.Fixed(1)) : Action
    /** Removes stacks without it being damage or a cost: a cleanse, a consumed setup. */
    data class Take(val stat: Stat, val target: Aim, val amount: Amount = Amount.Fixed(1)) : Action
    /** Gives the triggering event's own state once more to its target (a Battlemage's echo). */
    data class Echo(val extra: Int = 1) : Action
    /** The holder's weapon cracks for this fight: [tokens] Scrap now, a repair afterwards. Not destruction. */
    data class Fracture(val tokens: Int) : Action
    /** Calls [unitId] to the holder's side while there is room. */
    data class Summon(val unitId: String) : Action
}

/** At least one of the two is positive for every effect; 0 means "not limited at that scope". */
data class Limit(val perRound: Int = 0, val perFight: Int = 0)

data class EffectDef(
    val id: String,
    val name: String,
    val trigger: Trigger,
    val actions: List<Action>,
    val limit: Limit,
    /** The rule as the player reads it, limits included. The card and the resolver come from this one definition. */
    val description: String,
    val conditions: List<Condition> = emptyList(),
    val cost: Cost? = null,
    /** A rule of the party as a whole: evaluated once per event, for the member the event is about. */
    val party: Boolean = false,
    /** For a party rule: the limit counts per member instead of once for the party. */
    val perMember: Boolean = false,
    /** An echo never answers an event another echo produced. */
    val echo: Boolean = false,
)

sealed interface Cadence {
    data object Always : Cadence
    /** The first round only. */
    data object Opener : Cadence
    /** Rounds [first], [first] + [every], ... */
    data class Every(val every: Int, val first: Int = every) : Cadence
    /** While the actor holds at least [atLeast] of [stat]. */
    data class WhileHolding(val stat: Stat, val atLeast: Int = 1) : Cadence
    data class HealthAtMost(val percent: Int) : Cadence
}

/** [telegraph] is what the mission card says of this move before anybody leaves. */
data class Move(val id: String, val name: String, val cadence: Cadence, val actions: List<Action>, val telegraph: String? = null)

/** What an actor does with its one action a round: the first move whose cadence fits. [passives] are the kit's own effects. */
data class KitDef(val id: String, val name: String, val moves: List<Move>, val passives: List<EffectDef> = emptyList(), val tags: Set<String> = emptySet())

object EffectRules {
    /** What a machine can check of a definition (spec 6.4, 17.4): every effect is limited, costs are sane, echoes are marked. */
    fun problems(effects: Collection<EffectDef>, kits: Collection<KitDef> = emptyList(), units: Map<String, UnitDef> = emptyMap()): List<String> {
        val problems = mutableListOf<String>()
        val all = effects + kits.flatMap { it.passives } + units.values.flatMap { it.effects + it.kit.passives }
        all.groupBy { it.id }.filterValues { defs -> defs.distinct().size > 1 }.keys.forEach { problems += "Effect id $it names two different effects" }
        for (e in all.distinctBy { it.id }) {
            if (e.limit.perRound <= 0 && e.limit.perFight <= 0) problems += "Effect ${e.id} has no limit"
            if (e.actions.isEmpty()) problems += "Effect ${e.id} does nothing"
            if (e.description.isBlank()) problems += "Effect ${e.id} has no description"
            if (e.actions.any { it is Action.Echo } != e.echo) problems += "Effect ${e.id}: echo flag and Echo action disagree"
            if (e.echo && e.trigger.kind != EventKind.STAT_GAINED) problems += "Effect ${e.id}: an echo answers a state being given"
            when (val c = e.cost) {
                is Cost.Spend -> if (c.amount <= 0) problems += "Effect ${e.id} spends nothing"
                is Cost.PayHealth -> if (c.amount <= 0 || c.floor < 1) problems += "Effect ${e.id} health payment needs an amount and a floor of at least 1"
                null -> {}
            }
            if (e.actions.any { a -> a is Action.Damage && a.amount is Amount.OfSpent || a is Action.Guard && a.amount is Amount.OfSpent } && e.cost == null) problems += "Effect ${e.id} scales with a cost it does not have"
            // An effect that gives the very state it is triggered by, to the same actor, with no cost, is a loop held only by its limit.
            if (e.cost == null && e.trigger.kind == EventKind.STAT_GAINED && e.trigger.stat != null &&
                e.actions.any { it is Action.Give && it.stat == e.trigger.stat && it.target == Aim.Self } && e.trigger.who in setOf(Who.SELF_TARGET, Who.ALLY_TARGET)) problems += "Effect ${e.id} feeds itself"
            e.actions.filterIsInstance<Action.Summon>().forEach { if (it.unitId !in units) problems += "Effect ${e.id} summons unknown unit ${it.unitId}" }
            if (e.actions.any { it is Action.Summon } && e.limit.perFight <= 0) problems += "Effect ${e.id} summons without a per-fight limit"
        }
        for (k in kits + units.values.map { it.kit }) {
            if (k.moves.none { it.cadence == Cadence.Always }) problems += "Kit ${k.id} has no move for an ordinary round"
            k.moves.flatMap { it.actions }.filterIsInstance<Action.Summon>().forEach { if (it.unitId !in units) problems += "Kit ${k.id} summons unknown unit ${it.unitId}" }
        }
        // A summoned unit may not summon: one helper, no recursion.
        units.values.forEach { u -> if ((u.effects + u.kit.passives).any { e -> e.actions.any { it is Action.Summon } } || u.kit.moves.any { m -> m.actions.any { it is Action.Summon } }) {
            if ("summoned" in u.tags) problems += "Summoned unit ${u.id} summons"
        } }
        return problems
    }
}
