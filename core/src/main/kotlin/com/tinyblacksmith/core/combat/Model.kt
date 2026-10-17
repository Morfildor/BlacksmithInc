package com.tinyblacksmith.core.combat

import com.tinyblacksmith.core.content.Element
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.EncodeDefault.Mode.NEVER
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

enum class Side { PARTY, ENEMY }

/**
 * Integer stacks an actor carries in a fight. GUARD is consumed before health. MARK, CHILL, BURN, REGEN, BLEED, WET,
 * THORNS and POISON have a rule of their own in [Fight]; CHARGE, SCRAP and STEAM are plain resources that only effects
 * read. Nothing here outlives the fight. New states are appended: the constant names are save keys.
 */
enum class Stat { GUARD, CHARGE, MARK, BURN, REGEN, CHILL, WET, BLEED, SCRAP, THORNS, STEAM, POISON }

/**
 * What can be recorded in a fight. A DAMAGE or HEALED event exists only for a positive effective amount: a hit that
 * Guard took whole is a GUARD_ABSORBED, healing at full health is an OVERHEAL. HEALTH_PAID is a cost, never damage.
 */
enum class EventKind {
    ROUND_START, ACTION, DAMAGE, GUARD_ABSORBED, GUARD_BROKEN, GUARD_GAINED, HEALED, OVERHEAL, STAT_GAINED, STAT_SPENT,
    HEALTH_PAID, FRACTURED, DOWNED, SUMMONED, RETREATED, OBJECTIVE, ROUND_END,
    /** An actor has finished its action: what answers "after the wielder acts" answers this. ACTION is recorded before the move is carried out. */
    ACTED,
    // Living heroes (final design 13). Appended: the constant names are save keys.
    /** An actor spent its action starting a channel ([FightEvent.ability] names it); [FightEvent.target] is its fixed target, if it has one. */
    CHANNEL_STARTED,
    /** The channel was finished on the actor's next action, uninterrupted: its payload follows under this event. */
    CHANNEL_COMPLETED,
    /** The channel's source went down with the channel pending: it never completes. */
    CHANNEL_CANCELLED,
    /** The source interrupted the pending channel of the target. Recorded only when a real channel was pending. */
    INTERRUPTED,
    /** A completed attack on the town's defences: the amount is forge health, summed in [FightResult.structural]. */
    STRUCTURAL_HIT,
    /** An engine spent its last use: it stops acting and no longer keeps the fight going. Nobody is credited with it. */
    NEUTRALIZED,
    /** A downed actor stands again with the amount as its health, once ever (the source returned it). */
    RETURNED,
    /** A hit that would have downed the actor left it at 1 health, once per fight. */
    SURVIVED,
    /** The actor's healing before its action did not happen, because of what hit it since its last action. */
    RECOVERY_STOPPED,
    /** The source chose the target as its side's prey until the round ends: that side's pack attacks it. */
    PREY_MARKED,
}

/** How an event came about: an actor's own action, a triggered effect, or a tick of a state at its scheduled moment. */
enum class Via { ACTION, EFFECT, TICK }

/** A hit the target's element changed: it struck a weakness, or the target resisted it (an element, or part of a tick). */
enum class Affinity { WEAK, RESISTED }

/**
 * One line of the timeline. [parentId] is the event that caused this one, [rootId] the action (or tick) the whole chain
 * began with. [effectId] names the effect that produced it, null for what an action or a rule did directly.
 * [tick] is the state whose tick this event is part of (Burn, Bleed, Regeneration); [affinity] says a hit met a weakness
 * or a resistance. Both are records only: no rule reads them, and a fight recorded before them has neither.
 */
@OptIn(ExperimentalSerializationApi::class)
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
    @EncodeDefault(NEVER) val tick: Stat? = null,
    @EncodeDefault(NEVER) val affinity: Affinity? = null,
    /** The name of the prepared ability a channel event is about (CHANNEL_STARTED, CHANNEL_COMPLETED, CHANNEL_CANCELLED, INTERRUPTED). Records only. */
    @EncodeDefault(NEVER) val ability: String? = null,
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
        Stat.WET to 2, Stat.BLEED to 5, Stat.SCRAP to 4, Stat.THORNS to 8, Stat.STEAM to 4, Stat.POISON to 3,
    ),
    /** Healing a poisoned fighter receives is this much smaller (percent) per Poison, at most [poisonHealMax] in all. */
    val poisonHealPercent: Int = 20,
    val poisonHealMax: Int = 60,
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
    /** Records only, for the replay and the report: the blade's ID, which of [effects] it brought, and the catalog unit an enemy is. */
    val weaponId: String? = null,
    val weaponRules: List<String> = emptyList(),
    val unitId: String? = null,
    /** States this actor never receives: `Fight` skips giving them. Empty for every unit written before Poison. */
    val immune: Set<Stat> = emptySet(),
    /** The first direct hit that would down it leaves it at 1 health instead, unless the hit is Fire or Sun. Once per fight, kept across a return. */
    val surviveOnce: Boolean = false,
    /** Extra direct damage it takes from a source with a tag ([SourceBonus]): the strongest one applies, never two. */
    val bonusFrom: List<SourceBonus> = emptyList(),
    /** Healing before each of its actions that some hits stop ([Recovery]). */
    val recover: Recovery? = null,
    /** Once per round, this much more direct damage (percent) on a target another allied [Tags.BEAST] already damaged with its own action this round. */
    val packBonusPercent: Int = 0,
)

/**
 * A source a unit is weak to beyond its element: direct damage from an actor with [tag] deals [percent] more. Tags of a
 * party member come from its weapon (`Loadout`: `family:<id>`, `metal:<id>`). [Tags.AREA] means a hit of an action that
 * strikes several enemies at once ([Aim.AllFoes], [Aim.FrontFoes] of 2 or more).
 */
data class SourceBonus(val tag: String, val percent: Int)

/**
 * Before each of its own actions the unit heals [amount], unless since its previous action it took health damage from a
 * Fire hit, from a Burn tick, or from a source carrying one of [suppressedBy] (such as `metal:silver`).
 */
data class Recovery(val amount: Int, val suppressedBy: Set<String> = emptySet())

/** Tags the fight itself reads. A unit may carry any others for content to name. */
object Tags {
    /** Never returned by [Action.Return]: captains, engines, spirits and major monsters. */
    const val CAPTAIN = "captain"
    const val ENGINE = "engine"
    const val SPIRIT = "spirit"
    const val BOSS = "boss"
    /** An enemy that attacks its side's prey ([Action.MarkPrey]) with [Aim.Foe]. */
    const val PACK = "pack"
    /** What [Combatant.packBonusPercent] counts as another pack member's hit. */
    const val BEAST = "beast"
    /** A [SourceBonus] tag: a hit of an action that strikes several enemies. */
    const val AREA = "area"
    val NEVER_RETURNED = setOf(CAPTAIN, ENGINE, SPIRIT, BOSS)
}

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
    /** As on [Combatant]: states it never receives, survival once, source weaknesses, recovery and the pack bonus. */
    val immune: Set<Stat> = emptySet(),
    val surviveOnce: Boolean = false,
    val bonusFrom: List<SourceBonus> = emptyList(),
    val recover: Recovery? = null,
    val packBonusPercent: Int = 0,
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

/**
 * An actor as the fight left it. From [FightResult.RECORD] on it also says how it came in ([start], [startStats]) and
 * what it carried, so a replay can be drawn from the record alone; a fight recorded before has the defaults.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ActorResult(
    val key: String, val name: String, val side: Side, val health: Int, val maxHealth: Int, val downed: Boolean, val fractured: Boolean = false, val summoned: Boolean = false,
    @EncodeDefault(NEVER) val start: Int? = null,
    @EncodeDefault(NEVER) val startStats: Map<Stat, Int> = emptyMap(),
    @EncodeDefault(NEVER) val kit: String? = null,
    @EncodeDefault(NEVER) val weapon: String? = null,
    @EncodeDefault(NEVER) val weaponId: String? = null,
    @EncodeDefault(NEVER) val element: Element? = null,
    /** The rule IDs the actor's blade brought to this fight, in the order the fight asked them. */
    @EncodeDefault(NEVER) val weaponRules: List<String> = emptyList(),
    @EncodeDefault(NEVER) val unitId: String? = null,
    /** An engine that spent its last use: standing, but out of the fight. */
    @EncodeDefault(NEVER) val neutralized: Boolean = false,
    /** Downed once and returned to the fight ([EventKind.RETURNED]). */
    @EncodeDefault(NEVER) val returned: Boolean = false,
)

/** A chain worth telling: the events it is made of, root first, and the sentence built from their own texts. */
@Serializable
data class Highlight(val eventIds: List<Int>, val text: String, val weight: Int)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FightResult(
    val title: String,
    val outcome: FightOutcome,
    val rounds: Int,
    val actors: List<ActorResult>,
    val events: List<FightEvent>,
    val highlights: List<Highlight> = emptyList(),
    /** 0 for a fight recorded before replays; [RECORD] when the actors, ticks and affinities above are filled in. */
    @EncodeDefault(NEVER) val record: Int = 0,
    /** The name of every rule an event of this fight names, by ID: the record says what fired without the catalog. */
    @EncodeDefault(NEVER) val rules: Map<String, String> = emptyMap(),
    /**
     * Forge health the completed structural hits of this fight took ([EventKind.STRUCTURAL_HIT]), summed and uncapped.
     * The siege applies it once, under its own cap. 0 for every fight before them.
     */
    @EncodeDefault(NEVER) val structural: Int = 0,
) {
    fun actor(key: String): ActorResult? = actors.firstOrNull { it.key == key }
    val won: Boolean get() = outcome == FightOutcome.WON

    companion object {
        /** The first record a replay can be drawn from. */
        const val RECORD = 1
    }
}

/** The defect guard of spec 6.4: valid content never reaches it. The seed and round are what a report needs to reproduce it. */
class FightLoopException(val seed: Long, val round: Int, val title: String) : IllegalStateException("Fight '$title' (seed $seed) exceeded its event budget in round $round")
