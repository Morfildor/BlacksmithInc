package com.tinyblacksmith.core.combat

import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.rng.Rng

/**
 * The automatic fight (spec 6). Pure: a [FightSetup] in, a [FightResult] out; the same setup gives the same timeline.
 *
 * Order. Rounds 1..max. In a round the party acts in slot order, then the enemies in theirs; each actor takes the first
 * move of its kit whose cadence fits. After every action the trigger queue is drained in the order events were recorded;
 * for one event, holders are asked in slot order (party, then enemies) and each holder's effects in their listed order,
 * then the party's own rules. At round end Burn ticks, Regeneration ticks and Wet dries, in slot order.
 *
 * Finite chains. An effect never answers an event it produced itself. An echo never answers an echo. A cost is paid
 * before the action it buys is recorded. Every effect has a limit per round or per fight. The event budget is a guard
 * against a defect in content, not a rule: reaching it throws [FightLoopException].
 *
 * Arithmetic is integer; a percentage is taken as `(value * percent + 50) / 100`, here and nowhere else.
 *
 * Draws. Only an actor's strike inside its own [min, max] draws from `Rng(seed)`; an actor with min = max draws nothing.
 */
object Fight {
    fun pct(value: Int, percent: Int): Int = (value * percent + 50) / 100

    fun resolve(setup: FightSetup): FightResult = Run(setup).play()

    private class Actor(val def: Combatant, val slot: Int, val summoned: Boolean = false) {
        val key = def.key
        val name = def.name
        val side = def.side
        var health = def.health.coerceIn(0, def.maxHealth)
        val stats = HashMap<Stat, Int>().apply { def.stats.forEach { (s, n) -> if (n > 0) put(s, n) } }
        var downed = health <= 0
        var fractured = false
        var strike = def.strikeMin
        val effects: List<EffectDef> = def.kit.passives + def.effects
        val tags: Set<String> = def.kit.tags + def.tags
        fun stat(s: Stat) = stats[s] ?: 0
        val alive get() = !downed
    }

    private class Run(val setup: FightSetup) {
        val rules = setup.rules
        val rng = Rng(setup.seed)
        val actors = ArrayList<Actor>()
        val events = ArrayList<FightEvent>()
        val queue = ArrayDeque<FightEvent>()
        val perRound = HashMap<String, Int>()
        val perFight = HashMap<String, Int>()
        val happened = HashSet<Pair<Side, EventKind>>()
        var round = 0
        var roundEvents = 0
        var nextSummon = 1
        val enteringHealth = setup.party.sumOf { it.health.coerceAtLeast(0) }

        init {
            require(setup.party.isNotEmpty()) { "A fight needs a party" }
            require((setup.party + setup.enemies).map { it.key }.toSet().size == setup.party.size + setup.enemies.size) { "Actor keys must be unique" }
            setup.party.forEachIndexed { i, c -> require(c.side == Side.PARTY); actors += Actor(c, i) }
            setup.enemies.forEachIndexed { i, c -> require(c.side == Side.ENEMY); actors += Actor(c, i) }
        }

        fun side(side: Side) = actors.filter { it.side == side }
        fun living(side: Side) = actors.filter { it.side == side && it.alive }
        fun actor(key: String?) = key?.let { k -> actors.firstOrNull { it.key == k } }
        fun other(side: Side) = if (side == Side.PARTY) Side.ENEMY else Side.PARTY

        fun emit(kind: EventKind, source: Actor?, target: Actor?, amount: Int = 0, stat: Stat? = null, via: Via = Via.ACTION, effect: EffectDef? = null, parent: FightEvent? = null, text: String): FightEvent {
            if (++roundEvents > rules.eventBudgetPerRound) throw FightLoopException(setup.seed, round, setup.title)
            val id = events.size + 1
            val e = FightEvent(id, round, kind, source?.key, target?.key, amount, stat, via, effect?.id, parent?.id, parent?.rootId ?: id, text)
            events += e
            queue += e
            return e
        }

        fun play(): FightResult {
            var outcome: FightOutcome? = null
            while (outcome == null && round < rules.maxRounds) {
                round += 1
                roundEvents = 0
                perRound.clear()
                emit(EventKind.ROUND_START, null, null, text = "Round $round.")
                drain()
                for (a in actors.toList()) {
                    if (finished() != null) break
                    if (a.alive) { act(a); drain() }
                }
                if (finished() == null) endOfRound()
                emit(EventKind.ROUND_END, null, null, text = "")
                drain()
                outcome = finished() ?: holdWon() ?: retreat()
            }
            val result = outcome ?: FightOutcome.TIMED_OUT
            val closing = when (result) {
                FightOutcome.WON -> "The party has won."
                FightOutcome.LOST -> "The party is beaten."
                FightOutcome.RETREATED -> "The party pulls back."
                FightOutcome.TIMED_OUT -> "Neither side can finish it; the party breaks off."
            }
            emit(if (result == FightOutcome.RETREATED) EventKind.RETREATED else EventKind.OBJECTIVE, null, null, text = closing)
            queue.clear()
            // The whole record: a line without text (a tick's root, a partly absorbed blow) is still a link of somebody's chain. A screen shows the lines that have text.
            return FightResult(setup.title, result, round, actors.map { ActorResult(it.key, it.name, it.side, it.health, it.def.maxHealth, it.downed, it.fractured, it.summoned) }, events.toList(),
                Highlights.of(events, actors.associate { it.key to it.side }))
        }

        /** Decided the moment it is true, whoever's turn it is. */
        fun finished(): FightOutcome? {
            val protect = setup.objective.protect
            if (protect != null && actor(protect)?.downed == true) return FightOutcome.LOST
            if (living(Side.PARTY).none { !it.summoned && it.key != protect }) return FightOutcome.LOST
            if (living(Side.ENEMY).isEmpty()) return FightOutcome.WON
            return null
        }

        fun holdWon(): FightOutcome? = if (setup.objective.kind == ObjectiveKind.HOLD && round >= setup.objective.rounds) FightOutcome.WON else null

        fun retreat(): FightOutcome? {
            val members = side(Side.PARTY).filter { !it.summoned && it.key != setup.objective.protect }
            val down = members.count { it.downed }
            val standing = members.filter { it.alive }
            val leaves = when (setup.posture) {
                Posture.RECKLESS -> false
                Posture.CAUTIOUS -> down > 0 || standing.sumOf { it.health } * 100 < enteringHealth * rules.cautiousHealthPercent
                Posture.BALANCED -> down >= rules.balancedDowned || (members.size > 1 && standing.size == 1 && standing[0].health * 100 < standing[0].def.maxHealth * rules.lastStandPercent)
            }
            return if (leaves) FightOutcome.RETREATED else null
        }

        fun act(a: Actor) {
            val bleed = a.stat(Stat.BLEED)
            if (bleed > 0) {
                val tick = emit(EventKind.ACTION, a, a, via = Via.TICK, text = "")
                damage(null, a, tickAmount(a, Stat.BLEED, bleed), null, pierce = true, via = Via.TICK, effect = null, parent = tick, what = "bleeds")
                a.stats[Stat.BLEED] = bleed - 1
                drain()
                if (a.downed || finished() != null) return
            }
            val move = a.def.kit.moves.firstOrNull { fits(it.cadence, a) } ?: return
            a.strike = if (a.def.strikeMax > a.def.strikeMin) rng.nextInt(a.def.strikeMin, a.def.strikeMax) else a.def.strikeMin
            val action = emit(EventKind.ACTION, a, null, text = "${a.name}: ${move.name}.")
            drain()   // what is paid or readied "before the wielder acts" comes before the move itself
            if (a.downed || finished() != null) return
            var chilled = false
            for (step in move.actions) {
                if (a.downed) break
                // Chill bites the first strike of the action and is spent by it.
                val cold = step is Action.Damage && !chilled && a.stat(Stat.CHILL) > 0
                if (cold) { chilled = true; spend(a, Stat.CHILL, 1, null, action, "${a.name} is slowed by the cold.") }
                perform(step, a, action, null, 0, if (cold) 100 - rules.chillPercent else 100)
            }
            if (a.alive) emit(EventKind.ACTED, a, null, parent = action, text = "")
        }

        fun fits(c: Cadence, a: Actor): Boolean = when (c) {
            Cadence.Always -> true
            Cadence.Opener -> round == 1
            is Cadence.Every -> round >= c.first && (round - c.first) % c.every == 0
            is Cadence.WhileHolding -> a.stat(c.stat) >= c.atLeast
            is Cadence.HealthAtMost -> a.health * 100 <= a.def.maxHealth * c.percent
        }

        fun endOfRound() {
            for (a in actors.toList()) {
                if (a.downed) continue
                val burn = a.stat(Stat.BURN)
                if (burn > 0) {
                    val tick = emit(EventKind.ACTION, a, a, via = Via.TICK, text = "")
                    damage(null, a, tickAmount(a, Stat.BURN, burn), null, pierce = false, via = Via.TICK, effect = null, parent = tick, what = "burns")
                    a.stats[Stat.BURN] = burn - 1
                }
                val regen = a.stat(Stat.REGEN)
                if (regen > 0 && a.alive) {
                    val tick = emit(EventKind.ACTION, a, a, via = Via.TICK, text = "")
                    heal(a, a, rules.regenHeal, Via.TICK, null, tick, "mends")
                    a.stats[Stat.REGEN] = regen - 1
                }
                val wet = a.stat(Stat.WET)
                if (wet > 0) a.stats[Stat.WET] = wet - 1
                drain()
                if (finished() != null) return
            }
        }

        fun tickAmount(a: Actor, stat: Stat, stacks: Int): Int = maxOf(1, pct(stacks, 100 - (a.def.tickResist[stat] ?: 0).coerceIn(0, 90)))

        // ---- triggers ----

        fun drain() {
            while (queue.isNotEmpty()) {
                val e = queue.removeFirst()
                // Known as "happened" from the moment its own answers are asked for: an effect that waits for a crack does not answer the blow that caused it.
                actor(e.target ?: e.source)?.let { happened += it.side to e.kind }
                if (finished() != null) continue   // the fight is decided: nothing more is spent or struck
                for (holder in actors.toList()) {
                    for (effect in holder.effects) {
                        if (holder.downed) break
                        if (matches(effect.trigger, holder, e)) fire(effect, holder, e, holder.key)
                    }
                }
                for (effect in setup.partyEffects) {
                    val holder = partyHolder(effect.trigger.who, e) ?: continue
                    if (matches(effect.trigger, holder, e)) fire(effect, holder, e, if (effect.perMember) holder.key else "party")
                }
            }
        }

        /** The member a party rule speaks about for this event: its subject if that is a member, else the first one standing. */
        fun partyHolder(who: Who, e: FightEvent): Actor? {
            val subject = when (who) {
                Who.SELF_TARGET, Who.ALLY_TARGET -> actor(e.target)
                Who.SELF_SOURCE, Who.ALLY_SOURCE -> actor(e.source)
                Who.FOE_TARGET, Who.FOE_SOURCE, Who.ANY -> null
            }
            return (subject?.takeIf { it.side == Side.PARTY && it.alive }) ?: if (who in setOf(Who.FOE_TARGET, Who.FOE_SOURCE, Who.ANY)) living(Side.PARTY).firstOrNull() else null
        }

        fun matches(t: Trigger, holder: Actor, e: FightEvent): Boolean {
            if (t.kind != e.kind) return false
            if (t.stat != null && t.stat != e.stat) return false
            val source = actor(e.source)
            val target = actor(e.target)
            return when (t.who) {
                Who.SELF_TARGET -> target === holder
                Who.SELF_SOURCE -> source === holder
                Who.ALLY_TARGET -> target?.side == holder.side
                Who.ALLY_SOURCE -> source?.side == holder.side
                Who.FOE_TARGET -> target != null && target.side != holder.side
                Who.FOE_SOURCE -> source != null && source.side != holder.side
                Who.ANY -> true
            }
        }

        fun fire(effect: EffectDef, holder: Actor, e: FightEvent, scope: String) {
            if (e.effectId == effect.id) return   // never its own product
            if (effect.echo && e.effectId != null && isEcho(e.effectId)) return
            val counter = "${effect.id}|$scope"
            if (effect.limit.perRound > 0 && (perRound[counter] ?: 0) >= effect.limit.perRound) return
            if (effect.limit.perFight > 0 && (perFight[counter] ?: 0) >= effect.limit.perFight) return
            if (!effect.conditions.all { holds(it, holder, e) }) return
            // The cost is recorded first and is the parent of what it buys: heal -> Charge -> spend -> burst reads as one chain.
            var paid: FightEvent? = null
            val spent = when (val c = effect.cost) {
                null -> 0
                is Cost.Spend -> {
                    val has = holder.stat(c.stat)
                    if (has < c.amount) return
                    val n = if (c.all) has else c.amount
                    paid = spend(holder, c.stat, n, effect, e, "${owner(holder, effect)} spends $n ${word(c.stat)}.")
                    n
                }
                is Cost.PayHealth -> {
                    val n = minOf(c.amount, holder.health - c.floor)
                    if (n <= 0) return
                    holder.health -= n
                    paid = emit(EventKind.HEALTH_PAID, holder, holder, n, via = Via.EFFECT, effect = effect, parent = e, text = "${holder.name} pays $n health to ${effect.name}.")
                    n
                }
            }
            perRound[counter] = (perRound[counter] ?: 0) + 1
            perFight[counter] = (perFight[counter] ?: 0) + 1
            for (step in effect.actions) perform(step, holder, e, effect, spent, 100, paid ?: e)
        }

        private val echoIds: Set<String> by lazy { (actors.flatMap { it.effects } + setup.partyEffects + setup.units.values.flatMap { it.effects + it.kit.passives }).filter { it.echo }.map { it.id }.toSet() }
        fun isEcho(effectId: String) = effectId in echoIds

        fun holds(c: Condition, holder: Actor, e: FightEvent): Boolean = when (c) {
            Condition.SourceIsFoe -> actor(e.source)?.let { it.side != holder.side } == true
            Condition.FromAction -> e.via == Via.ACTION
            Condition.NotTick -> e.via != Via.TICK
            is Condition.HolderHas -> holder.stat(c.stat) >= c.atLeast
            is Condition.HolderLacks -> holder.stat(c.stat) == 0
            is Condition.TargetHas -> (actor(e.target)?.stat(c.stat) ?: 0) >= c.atLeast
            is Condition.StatIn -> e.stat in c.stats
            is Condition.HolderHealthAtMost -> holder.health * 100 <= holder.def.maxHealth * c.percent
            is Condition.TargetHealthAtMost -> actor(e.target)?.let { it.alive && it.health * 100 <= it.def.maxHealth * c.percent } == true
            is Condition.AmountAtLeast -> e.amount >= c.amount
            is Condition.Happened -> (holder.side to c.kind) in happened
            is Condition.HolderTagged -> c.tag in holder.tags
            is Condition.TargetTagged -> actor(e.target)?.let { c.tag in it.tags } == true
        }

        // ---- actions ----

        fun amount(a: Amount, holder: Actor, e: FightEvent, spent: Int): Int = when (a) {
            is Amount.Fixed -> a.value
            is Amount.Strike -> pct(holder.strike, a.percent)
            is Amount.Support -> pct(holder.def.support, a.percent)
            is Amount.OfEvent -> minOf(a.cap, pct(e.amount, a.percent))
            is Amount.OfSpent -> spent * a.perPoint
            is Amount.OfHolderStat -> holder.stat(a.stat) * a.perStack
        }

        fun targets(t: Aim, holder: Actor, e: FightEvent): List<Actor> {
            val allies = living(holder.side)
            val foes = living(other(holder.side))
            return when (t) {
                Aim.Self -> listOf(holder)
                Aim.EventSource -> listOfNotNull(actor(e.source))
                Aim.EventTarget -> listOfNotNull(actor(e.target))
                Aim.WeakestAlly -> listOfNotNull(allies.minWithOrNull(compareBy<Actor> { it.health * 1000 / it.def.maxHealth }.thenBy { it.slot }))
                Aim.AllAllies -> allies
                Aim.OtherAllies -> allies.filter { it !== holder }
                is Aim.AllyTagged -> listOfNotNull(allies.firstOrNull { t.tag in it.tags })
                Aim.Foe -> listOfNotNull(
                    if (holder.side == Side.PARTY) foes.firstOrNull { it.stat(Stat.MARK) > 0 } ?: foes.firstOrNull()
                    else foes.firstOrNull { "intercept" in it.tags && it.stat(Stat.GUARD) > 0 } ?: foes.firstOrNull())
                Aim.MarkedFoe -> listOfNotNull(foes.firstOrNull { it.stat(Stat.MARK) > 0 })
                Aim.AllFoes -> foes
                Aim.WeakestFoe -> listOfNotNull(foes.minWithOrNull(compareBy<Actor> { it.health }.thenBy { it.slot }))
                Aim.BackFoe -> listOfNotNull(foes.lastOrNull())
                Aim.PriorityFoe -> listOfNotNull(foes.firstOrNull { "anchor" in it.tags } ?: foes.firstOrNull { "leader" in it.tags } ?: foes.maxWithOrNull(compareBy<Actor> { it.health }.thenByDescending { it.slot }))
            }.filter { it.alive }
        }

        /** [e] is the event the rule answers (what "the event target" and "its amount" mean); [parent] is what the result is recorded under. */
        fun perform(step: Action, holder: Actor, e: FightEvent, effect: EffectDef?, spent: Int, scale: Int, parent: FightEvent = e) {
            val via = if (effect == null) Via.ACTION else Via.EFFECT
            when (step) {
                is Action.Damage -> for (t in targets(step.target, holder, e)) {
                    val n = pct(amount(step.amount, holder, e, spent), scale)
                    // A strike carries the blade's element; a fixed amount only the element its rule names.
                    damage(holder, t, n, step.element ?: holder.def.element.takeIf { step.amount is Amount.Strike }, step.pierce, via, effect, parent, null)
                }
                is Action.Heal -> for (t in targets(step.target, holder, e)) heal(holder, t, amount(step.amount, holder, e, spent), via, effect, parent, null)
                is Action.Guard -> for (t in targets(step.target, holder, e)) give(holder, t, Stat.GUARD, amount(step.amount, holder, e, spent), via, effect, parent)
                is Action.Give -> for (t in targets(step.target, holder, e)) give(holder, t, step.stat, amount(step.amount, holder, e, spent), via, effect, parent)
                is Action.Take -> for (t in targets(step.target, holder, e)) {
                    val n = minOf(t.stat(step.stat), amount(step.amount, holder, e, spent))
                    if (n > 0) spend(t, step.stat, n, effect, parent, "${owner(holder, effect)} takes $n ${word(step.stat)} from ${t.name}.", holder)
                }
                is Action.Echo -> {
                    val t = actor(e.target)
                    val s = e.stat
                    if (t != null && t.alive && s != null) give(holder, t, s, step.extra, via, effect, parent)
                }
                is Action.Fracture -> if (!holder.fractured) {
                    holder.fractured = true
                    val f = emit(EventKind.FRACTURED, holder, holder, step.tokens, via = via, effect = effect, parent = parent, text = "${holder.name}'s ${holder.def.weaponName ?: "weapon"} cracks.")
                    give(holder, holder, Stat.SCRAP, step.tokens, via, effect, f)
                }
                is Action.Summon -> {
                    val unit = setup.units[step.unitId] ?: return
                    if (side(holder.side).count { it.alive } >= rules.maxActorsPerSide) return
                    val key = "s${nextSummon++}"
                    val c = Combatant(key, unit.name, holder.side, unit.kit, unit.health, unit.health, unit.strikeMin, unit.strikeMax, unit.support, weakTo = unit.weakTo, resists = unit.resists,
                        effects = unit.effects, tags = unit.tags + "summoned", tickResist = unit.tickResist)
                    val s = Actor(c, side(holder.side).size, summoned = true)
                    actors.add(actors.indexOfLast { it.side == holder.side } + 1, s)
                    emit(EventKind.SUMMONED, holder, s, via = via, effect = effect, parent = parent, text = "${unit.name} joins ${if (holder.side == Side.PARTY) "the party" else "the enemy"}${effect?.let { " (${it.name})" } ?: ""}.")
                }
            }
        }

        fun owner(holder: Actor, effect: EffectDef?): String = if (effect == null) holder.name else if (effect.party) effect.name else "${holder.name}'s ${effect.name}"

        fun word(stat: Stat): String = when (stat) {
            Stat.GUARD -> "Guard"; Stat.CHARGE -> "Charge"; Stat.MARK -> "Mark"; Stat.BURN -> "Burn"; Stat.REGEN -> "Regeneration"; Stat.CHILL -> "Chill"
            Stat.WET -> "Wet"; Stat.BLEED -> "Bleed"; Stat.SCRAP -> "Scrap"; Stat.THORNS -> "Thorns"; Stat.STEAM -> "Steam"
        }

        fun spend(a: Actor, stat: Stat, n: Int, effect: EffectDef?, parent: FightEvent, text: String, by: Actor? = null): FightEvent {
            a.stats[stat] = a.stat(stat) - n
            return emit(EventKind.STAT_SPENT, by ?: a, a, n, stat, if (effect == null) parent.via else Via.EFFECT, effect, parent, text)
        }

        fun give(source: Actor, t: Actor, stat: Stat, n: Int, via: Via, effect: EffectDef?, parent: FightEvent) {
            if (n <= 0 || t.downed) return
            // Heat on cold or on wet, cold on heat: the two cancel one for one and whoever brought the second gets Steam for it.
            val meets = when (stat) { Stat.BURN -> listOf(Stat.CHILL, Stat.WET); Stat.CHILL -> listOf(Stat.BURN); else -> emptyList() }.firstOrNull { t.stat(it) > 0 }
            if (meets != null && source.side != t.side) {
                val boiled = minOf(n, t.stat(meets))
                val spent = spend(t, meets, boiled, effect, parent, "${owner(source, effect)} boils off ${word(meets)} on ${t.name}.", source)
                give(source, source, Stat.STEAM, boiled, via, effect, spent)
                if (n - boiled > 0) give(source, t, stat, n - boiled, via, effect, parent)
                return
            }
            val gained = minOf(n, rules.cap(stat) - t.stat(stat))
            if (gained <= 0) return
            t.stats[stat] = t.stat(stat) + gained
            val who = owner(source, effect)
            if (stat == Stat.GUARD) emit(EventKind.GUARD_GAINED, source, t, gained, stat, via, effect, parent, if (source === t && effect == null) "${t.name} raises $gained Guard." else "$who gives ${t.name} $gained Guard.")
            else emit(EventKind.STAT_GAINED, source, t, gained, stat, via, effect, parent,
                if (source === t) "$who stores ${word(stat)} (${t.stat(stat)})." else "$who puts ${word(stat)} on ${t.name}${if (t.stat(stat) > 1) " (${t.stat(stat)})" else ""}.")
        }

        fun heal(source: Actor, t: Actor, n: Int, via: Via, effect: EffectDef?, parent: FightEvent, tick: String?) {
            if (n <= 0 || t.downed) return
            val effective = minOf(n, t.def.maxHealth - t.health)
            val who = owner(source, effect)
            if (effective > 0) {
                t.health += effective
                emit(EventKind.HEALED, source, t, effective, via = via, effect = effect, parent = parent, text = if (tick != null) "${t.name} $tick for $effective." else if (source === t) "$who heals for $effective." else "$who heals ${t.name} for $effective.")
            }
            val over = n - effective
            if (over > 0) emit(EventKind.OVERHEAL, source, t, over, via = via, effect = effect, parent = parent, text = if (effective > 0) "" else if (tick != null) "" else "$who has nothing to heal on ${t.name} ($over over).")
        }

        /** [source] is null for a tick. A direct hit (not a tick) spends a Mark for the bonus and meets Thorns. */
        fun damage(source: Actor?, t: Actor, base: Int, element: Element?, pierce: Boolean, via: Via, effect: EffectDef?, parent: FightEvent, what: String?) {
            if (base <= 0 || t.downed) return
            var n = base
            val who = if (source == null) t.name else owner(source, effect)
            if (via != Via.TICK && source != null && source.side != t.side) {
                if (element != null) n = when (element) { t.def.weakTo -> pct(n, rules.weakPercent); t.def.resists -> pct(n, rules.resistPercent); else -> n }
                if (element == Element.STORM && t.stat(Stat.WET) > 0) {
                    spend(t, Stat.WET, 1, effect, parent, "The storm runs through the wet on ${t.name}.", source)
                    n += rules.wetStormBonus
                }
                if (t.stat(Stat.MARK) > 0) {
                    spend(t, Stat.MARK, 1, effect, parent, "$who strikes the Mark on ${t.name}.", source)
                    n += rules.markBonus
                }
            }
            if (!pierce) {
                val guard = t.stat(Stat.GUARD)
                val absorbed = minOf(guard, n)
                if (absorbed > 0) {
                    t.stats[Stat.GUARD] = guard - absorbed
                    n -= absorbed
                    emit(EventKind.GUARD_ABSORBED, source, t, absorbed, Stat.GUARD, via, effect, parent, if (n == 0) "${t.name}'s Guard takes $who's blow ($absorbed)." else "")
                    if (guard - absorbed == 0) emit(EventKind.GUARD_BROKEN, source, t, absorbed, Stat.GUARD, via, effect, parent, "${t.name}'s Guard breaks.")
                }
            }
            if (n > 0) {
                t.health = maxOf(0, t.health - n)
                val hit = emit(EventKind.DAMAGE, source, t, n, via = via, effect = effect, parent = parent, text = if (what != null) "${t.name} $what for $n." else "$who hits ${t.name} for $n.")
                if (t.health == 0) {
                    t.downed = true
                    emit(EventKind.DOWNED, source, t, via = via, effect = effect, parent = hit, text = "${t.name} is down.")
                }
            }
            // Thorns answer a blow from an action, once, and are spent; what they deal is an effect's damage and meets no Thorns itself.
            if (via == Via.ACTION && source != null && source.alive && source.side != t.side && t.stat(Stat.THORNS) > 0) {
                val thorns = t.stat(Stat.THORNS)
                t.stats[Stat.THORNS] = 0
                val spent = emit(EventKind.STAT_SPENT, t, t, thorns, Stat.THORNS, Via.EFFECT, null, parent, "")
                damage(t, source, thorns, null, pierce = false, via = Via.EFFECT, effect = null, parent = spent, what = "takes the thorns")
            }
        }
    }
}
