package com.tinyblacksmith.core.combat

import com.tinyblacksmith.core.content.Element
import kotlinx.serialization.Serializable

enum class Side { PARTY, ENEMY }

/**
 * Integer stacks an actor carries in a fight. GUARD is consumed before health. MARK, CHILL, BURN, REGEN, BLEED, WET and
 * THORNS have a rule of their own in [Fight]; CHARGE, SCRAP and STEAM are plain resources that only effects read.
 * Nothing here outlives the fight.
 */
enum class Stat { GUARD, CHARGE, MARK, BURN, REGEN, CHILL, WET, BLEED, SCRAP, THORNS, STEAM }

/**
 * What can be recorded in a fight. A DAMAGE or HEALED event exists only for a positive effective amount: a hit that
 * Guard took whole is a GUARD_ABSORBED, healing at full health is an OVERHEAL. HEALTH_PAID is a cost, never damage.
 */
enum class EventKind {
    ROUND_START, ACTION, DAMAGE, GUARD_ABSORBED, GUARD_BROKEN, GUARD_GAINED, HEALED, OVERHEAL, STAT_GAINED, STAT_SPENT,
    HEALTH_PAID, FRACTURED, DOWNED, SUMMONED, RETREATED, OBJECTIVE, ROUND_END,
    /** An actor has finished its action: what answers "after the wielder acts" answers this. ACTION is recorded before the move is carried out. */
    ACTED,
}

/** How an event came about: an actor's own action, a triggered effect, or a tick of a state at its scheduled moment. */
enum class Via { ACTION, EFFECT, TICK }

/**
 * One line of the timeline. [parentId] is the event that caused this one, [rootId] the action (or tick) the whole chain
 * began with. [effectId] names the effect that produced it, null for what an action or a rule did directly.
 */
@Serializable
data class FightEvent(
    val id: Int,
    val round: Int,
    val kind: EventKind,
    val source: String? = null,
    val target: String? = null,
    val amount: Int = 0,
    val stat: Stat? = null,
    val via: Via = Via.ACTION,
    val effectId: String? = null,
    val parentId: Int? = null,
    val rootId: Int = id,
    val text: String = "",
)

enum class Posture { CAUTIOUS, BALANCED, RECKLESS }

enum class ObjectiveKind { DEFEAT, HOLD }

/**
 * DEFEAT: every enemy down. HOLD: somebody of the party still stands at the end of round [rounds] (an escort, an
 * extraction, a wall). With [protect], the actor of that key must not go down under either.
 */
@Serializable
data class Objective(val kind: ObjectiveKind = ObjectiveKind.DEFEAT, val rounds: Int = 0, val protect: String? = null)

enum class FightOutcome { WON, RETREATED, LOST, TIMED_OUT }

/** The numbers of the rules themselves (`BalanceConfig.guild.fight`). */
data class FightRules(
    val maxRounds: Int = 8,
    /** Added to a direct hit on a marked target; the Mark is spent. */
    val markBonus: Int = 3,
    /** A chilled actor's next strike loses this share; one Chill is spent. */
    val chillPercent: Int = 35,
    /** Health a Regeneration stack restores at round end; one stack is spent. */
    val regenHeal: Int = 3,
    /** Storm damage to a Wet target gains this and spends one Wet. */
    val wetStormBonus: Int = 2,
    val weakPercent: Int = 130,
    val resistPercent: Int = 70,
    val caps: Map<Stat, Int> = mapOf(
        Stat.GUARD to 14, Stat.CHARGE to 5, Stat.MARK to 1, Stat.BURN to 6, Stat.REGEN to 4, Stat.CHILL to 3,
        Stat.WET to 2, Stat.BLEED to 5, Stat.SCRAP to 4, Stat.THORNS to 8, Stat.STEAM to 4,
    ),
    /** Cautious leaves when a member is down or the party's health is under this share of what it came with. */
    val cautiousHealthPercent: Int = 40,
    /** Balanced leaves when this many members are down, or when the last one standing is under [lastStandPercent]. */
    val balancedDowned: Int = 2,
    val lastStandPercent: Int = 30,
    val maxActorsPerSide: Int = 4,
    /** A defect guard, not a balance tool: more events than this in one round throws. */
    val eventBudgetPerRound: Int = 400,
) {
    fun cap(stat: Stat): Int = caps[stat] ?: Int.MAX_VALUE
}

/** One fighter as the fight receives it. Health is on the fight's own scale; `Loadout` converts to and from a hero's. */
data class Combatant(
    val key: String,
    val name: String,
    val side: Side,
    val kit: KitDef,
    val maxHealth: Int,
    val health: Int = maxHealth,
    val strikeMin: Int,
    val strikeMax: Int = strikeMin,
    /** What the kit's supporting move is worth: a Guardian's Guard, a Warden's heal. */
    val support: Int = 0,
    val element: Element? = null,
    val weakTo: Element? = null,
    val resists: Element? = null,
    val effects: List<EffectDef> = emptyList(),
    val tags: Set<String> = emptySet(),
    val stats: Map<Stat, Int> = emptyMap(),
    /** Share (percent) of a tick of this state the actor ignores: a boss halves Burn, it is never immune. */
    val tickResist: Map<Stat, Int> = emptyMap(),
    val weaponName: String? = null,
)

/** Something an effect can call onto the field: one helper, with numbers of its own. */
data class UnitDef(
    val id: String,
    val name: String,
    val kit: KitDef,
    val health: Int,
    val strikeMin: Int,
    val strikeMax: Int = strikeMin,
    val support: Int = 0,
    val effects: List<EffectDef> = emptyList(),
    val tags: Set<String> = emptySet(),
    val weakTo: Element? = null,
    val resists: Element? = null,
    val tickResist: Map<Stat, Int> = emptyMap(),
)

data class FightSetup(
    val title: String,
    val party: List<Combatant>,
    val enemies: List<Combatant>,
    /** Rules that belong to the party as a whole (relics, a banner), not to one fighter. */
    val partyEffects: List<EffectDef> = emptyList(),
    val objective: Objective = Objective(),
    val posture: Posture = Posture.BALANCED,
    val rules: FightRules = FightRules(),
    val seed: Long = 0,
    val units: Map<String, UnitDef> = emptyMap(),
)

@Serializable
data class ActorResult(val key: String, val name: String, val side: Side, val health: Int, val maxHealth: Int, val downed: Boolean, val fractured: Boolean = false, val summoned: Boolean = false)

/** A chain worth telling: the events it is made of, root first, and the sentence built from their own texts. */
@Serializable
data class Highlight(val eventIds: List<Int>, val text: String, val weight: Int)

@Serializable
data class FightResult(
    val title: String,
    val outcome: FightOutcome,
    val rounds: Int,
    val actors: List<ActorResult>,
    val events: List<FightEvent>,
    val highlights: List<Highlight> = emptyList(),
) {
    fun actor(key: String): ActorResult? = actors.firstOrNull { it.key == key }
    val won: Boolean get() = outcome == FightOutcome.WON
}

/** The defect guard of spec 6.4: valid content never reaches it. The seed and round are what a report needs to reproduce it. */
class FightLoopException(val seed: Long, val round: Int, val title: String) : IllegalStateException("Fight '$title' (seed $seed) exceeded its event budget in round $round")
