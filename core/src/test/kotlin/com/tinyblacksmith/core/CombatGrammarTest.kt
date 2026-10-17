package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.guildRun
import com.tinyblacksmith.core.TestSupport.withMaterials
import com.tinyblacksmith.core.combat.*
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.LivingRevision
import com.tinyblacksmith.core.model.Rarity
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.sim.CombatSandbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The combat grammar of living heroes (final design 13, Appendix A): Poison, channels and Interrupt, the return once,
 * survival once, structural hits and neutralized engines, source weaknesses, recovery, the pack, and the two living-era
 * bonuses. Hand-built fighters, one cause each: not a mirror of content numbers.
 */
class CombatGrammarTest {
    private val rules = FightRules()
    private val combat = LaunchContent.catalog.combat!!

    private val wait = Move("wait", "Waits", Cadence.Always, emptyList())
    private val hit = Move("hit", "Hits", Cadence.Always, listOf(Action.Damage(Aim.Foe, Amount.Strike())))
    private fun move(id: String, cadence: Cadence, vararg actions: Action) = Move(id, id, cadence, actions.toList())
    private fun channel(id: String, cadence: Cadence, def: ChannelDef) = Move(id, def.name, cadence, emptyList(), channel = def)
    private fun dmg(aim: Aim, n: Int, element: Element? = null) = Action.Damage(aim, Amount.Fixed(n), element)
    private fun kit(moves: Array<out Move>) = KitDef("kit_" + moves.joinToString("_") { it.id }, "Kit", moves.toList())

    private fun member(key: String, vararg moves: Move, health: Int = 100, now: Int = health, strike: Int = 5, stats: Map<Stat, Int> = emptyMap(), effects: List<EffectDef> = emptyList(), tags: Set<String> = emptySet()) =
        Combatant(key, key, Side.PARTY, kit(moves), health, now, strike, strike, effects = effects, stats = stats, tags = tags, weaponName = "weapon", weaponRules = effects.map { it.id })

    private fun foe(key: String, vararg moves: Move, health: Int = 100, now: Int = health, strike: Int = 3, stats: Map<Stat, Int> = emptyMap(), effects: List<EffectDef> = emptyList(), tags: Set<String> = emptySet(),
        immune: Set<Stat> = emptySet(), surviveOnce: Boolean = false, bonusFrom: List<SourceBonus> = emptyList(), recover: Recovery? = null, pack: Int = 0) =
        Combatant(key, key, Side.ENEMY, kit(moves), health, now, strike, strike, effects = effects, stats = stats, tags = tags, immune = immune, surviveOnce = surviveOnce, bonusFrom = bonusFrom, recover = recover, packBonusPercent = pack)

    private fun fight(party: List<Combatant>, foes: List<Combatant>, units: Map<String, UnitDef> = emptyMap()) =
        Fight.resolve(FightSetup("grammar", party, foes, posture = Posture.RECKLESS, rules = rules, units = units))

    private fun FightResult.of(kind: EventKind) = events.filter { it.kind == kind }
    private fun FightResult.damageTo(key: String) = events.filter { it.kind == EventKind.DAMAGE && it.target == key }

    /** Every record here must still be a replay that adds up, and read in words. */
    private fun FightResult.replays(): FightResult = also { r ->
        assertNotNull(FightReplay.of(r), "the record adds up to the health it ends on")
        assertNotNull(FightAccounting.of(r))
        for (e in r.events) if (e.kind.ordinal > EventKind.ACTED.ordinal) assertTrue(e.text.isNotEmpty(), "${e.kind} is said in words")
    }

    // ---- Poison ----

    @Test fun `Poison is capped at 3, ticks past Armor at round end and runs down`() {
        val flinch = EffectDef("flinch", "Flinch", Trigger(EventKind.DAMAGE, Who.SELF_TARGET), listOf(Action.Guard(Aim.Self, Amount.Fixed(1))), Limit(perRound = 1), "When the enemy is hit, it gains 1 Armor. Once per round.", listOf(Condition.NotTick))
        val r = fight(
            listOf(member("a", move("dose", Cadence.Opener, Action.Give(Stat.POISON, Aim.AllFoes, Amount.Fixed(5))), wait)),
            listOf(foe("x", wait, health = 30, stats = mapOf(Stat.GUARD to 10, Stat.THORNS to 5), effects = listOf(flinch)), foe("y", wait, immune = setOf(Stat.POISON))),
        ).replays()
        assertEquals(listOf(3), r.of(EventKind.STAT_GAINED).filter { it.stat == Stat.POISON }.map { it.amount }, "capped, and the immune enemy gets none")
        val ticks = r.damageTo("x")
        assertEquals(listOf(3, 2, 1), ticks.map { it.amount })
        assertEquals(listOf(1, 2, 3), ticks.map { it.round })
        assertTrue(ticks.all { it.tick == Stat.POISON && it.via == Via.TICK && it.source == null })
        assertTrue(r.of(EventKind.GUARD_ABSORBED).isEmpty(), "Armor blocks no Poison")
        // A tick is no hit: nothing that answers a hit or a blow answers it.
        assertTrue(r.events.none { it.effectId == "flinch" } && r.events.none { it.stat == Stat.THORNS })
        assertEquals(24, r.actor("x")!!.health)
    }

    @Test fun `healing a poisoned fighter is smaller and uses up 1 Poison after it lands`() {
        val heal = { n: Int -> move("mend", Cadence.Opener, Action.Heal(Aim.AllyTagged("patient"), Amount.Fixed(n))) }
        val two = fight(listOf(member("h", heal(10), wait), member("p", wait, health = 30, now = 10, stats = mapOf(Stat.POISON to 2), tags = setOf("patient"))), listOf(foe("x", wait))).replays()
        val healed = two.of(EventKind.HEALED).single()
        assertEquals(6, healed.amount, "10 healing at 2 Poison is 40% smaller")
        val removed = two.of(EventKind.STAT_SPENT).first { it.stat == Stat.POISON }
        assertTrue(removed.id > healed.id && removed.amount == 1 && removed.target == "p" && removed.round == 1)
        assertEquals(10 + 6 - 1, two.actor("p")!!.health, "then 1 Poison ticks at the round's end")

        // 1 healing at 3 Poison rounds to nothing: nothing is healed, and 1 Poison is still used up.
        val zero = fight(listOf(member("h", heal(1), wait), member("p", wait, health = 30, now = 10, stats = mapOf(Stat.POISON to 3), tags = setOf("patient"))), listOf(foe("x", wait))).replays()
        assertTrue(zero.of(EventKind.HEALED).isEmpty() && zero.of(EventKind.OVERHEAL).isEmpty())
        assertEquals(1, zero.of(EventKind.STAT_SPENT).count { it.stat == Stat.POISON && it.round == 1 && it.source == "h" })
        assertEquals(2, zero.damageTo("p").first().amount, "2 Poison were left to tick")

        // Regeneration is healing too: Poison ticks first, then the weaker Regeneration uses one up.
        val regen = fight(listOf(member("p", wait, health = 30, now = 20, stats = mapOf(Stat.POISON to 2, Stat.REGEN to 1))), listOf(foe("x", wait))).replays()
        val round1 = regen.events.filter { it.round == 1 && it.target == "p" }
        assertEquals(listOf(EventKind.DAMAGE, EventKind.HEALED, EventKind.STAT_SPENT), round1.filter { it.kind in setOf(EventKind.DAMAGE, EventKind.HEALED, EventKind.STAT_SPENT) }.map { it.kind })
        assertEquals(2, round1.first { it.kind == EventKind.HEALED }.amount, "3 Regeneration at 1 Poison")
        assertTrue(regen.events.none { it.round > 1 && it.target == "p" && it.kind == EventKind.DAMAGE }, "no Poison was left after the heal")
    }

    @Test fun `Purifying removes 1 Poison from the weakest poisoned ally after the wielder acts`() {
        val purifying = combat.affixEffects.getValue(LaunchContent.PURIFYING)
        val r = fight(
            listOf(member("a", hit, effects = purifying), member("p", wait, health = 30, now = 15, stats = mapOf(Stat.POISON to 2)), member("q", wait, health = 30, stats = mapOf(Stat.POISON to 1)), member("c", wait, health = 30, now = 5)),
            listOf(foe("x", wait, health = 500)),
        ).replays()
        val first = r.events.filter { it.effectId == purifying.single().id && it.round == 1 }
        assertEquals(listOf("p"), first.map { it.target }, "the weakest of those who have Poison, not the weakest ally")
        assertEquals(1, first.single().amount)
        assertTrue(r.events.groupBy { it.round }.values.all { es -> es.count { it.effectId == purifying.single().id } <= 1 })
    }

    // ---- channels and Interrupt ----

    private fun charge(mode: OnInterrupt, uses: Int = 2) = ChannelDef("powder", "Powder Charge", uses, mode, payload = listOf(dmg(Aim.FrontFoe, 5)), fallback = listOf(dmg(Aim.Foe, 1)))

    @Test fun `Interrupt records nothing when nobody is preparing, and does what it says instead`() {
        val r = fight(listOf(member("a", move("silence", Cadence.Always, Action.Interrupt(Aim.Foe, otherwise = listOf(Action.Give(Stat.CHILL, Aim.Foe)))))), listOf(foe("x", wait))).replays()
        assertTrue(r.of(EventKind.INTERRUPTED).isEmpty())
        assertTrue(r.of(EventKind.STAT_GAINED).any { it.stat == Stat.CHILL && it.target == "x" })
    }

    @Test fun `a channel takes one action to start and completes on the next, never an extra action`() {
        val r = fight(listOf(member("a", wait)), listOf(foe("x", channel("prep", Cadence.Actions(1), charge(OnInterrupt.SPENT, uses = 1)), hit))).replays()
        val x = r.events.filter { it.kind == EventKind.ACTION && it.source == "x" && it.via == Via.ACTION }
        assertEquals((1..r.rounds).toList(), x.map { it.round }, "one action a round")
        assertEquals(1, r.of(EventKind.CHANNEL_STARTED).single().round)
        val done = r.of(EventKind.CHANNEL_COMPLETED).single()
        assertEquals(2, done.round)
        assertEquals("Powder Charge", done.ability)
        assertEquals(5, r.events.first { it.kind == EventKind.DAMAGE && it.parentId == done.id }.amount)
    }

    @Test fun `an interruption spends the channel or only its cycle, as the card says`() {
        val silence = move("silence", Cadence.Every(100, first = 2), Action.Interrupt(Aim.Foe))
        fun play(mode: OnInterrupt) = fight(listOf(member("a", silence, wait), member("b", silence, wait)), listOf(foe("x", channel("prep", Cadence.Actions(1, 3), charge(mode)), wait))).replays()

        val cycle = play(OnInterrupt.CYCLE)
        assertEquals(1, cycle.of(EventKind.INTERRUPTED).size, "a second interruption finds nothing left to stop")
        assertEquals(listOf(1, 3), cycle.of(EventKind.CHANNEL_STARTED).map { it.round })
        assertEquals(listOf(4), cycle.of(EventKind.CHANNEL_COMPLETED).map { it.round })
        assertEquals(1, cycle.events.single { it.kind == EventKind.DAMAGE && it.round == 2 && it.source == "x" }.amount, "the interrupted cycle falls back")

        val spent = play(OnInterrupt.SPENT)
        assertEquals(1, spent.of(EventKind.INTERRUPTED).size)
        assertTrue("cannot prepare it again" in spent.of(EventKind.INTERRUPTED).single().text)
        assertEquals(listOf(1), spent.of(EventKind.CHANNEL_STARTED).map { it.round }, "no use left to start again")
        assertTrue(spent.of(EventKind.CHANNEL_COMPLETED).isEmpty())
    }

    @Test fun `a channeler that goes down cancels what it was preparing`() {
        val r = fight(listOf(member("a", move("kill", Cadence.Every(100, first = 2), dmg(Aim.Foe, 50)), wait)), listOf(foe("x", channel("prep", Cadence.Actions(1), charge(OnInterrupt.SPENT, uses = 1)), wait, health = 5))).replays()
        val cancelled = r.of(EventKind.CHANNEL_CANCELLED).single()
        assertEquals("x", cancelled.target)
        assertEquals(r.of(EventKind.DOWNED).single().id, cancelled.parentId)
        assertTrue(r.of(EventKind.CHANNEL_COMPLETED).isEmpty())
        assertEquals(FightOutcome.WON, r.outcome)
        assertEquals(100, r.actor("a")!!.health)
    }

    @Test fun `Disrupting interrupts a channeling enemy once per fight and does nothing without one`() {
        val disrupting = combat.affixEffects.getValue(LaunchContent.DISRUPTING)
        val idle = fight(listOf(member("a", hit, effects = disrupting)), listOf(foe("x", wait, health = 60))).replays()
        assertTrue(idle.events.none { it.effectId == disrupting.single().id } && idle.of(EventKind.INTERRUPTED).isEmpty())

        val busy = fight(listOf(member("a", hit, effects = disrupting)), listOf(foe("x", channel("prep", Cadence.Actions(1, 3), charge(OnInterrupt.CYCLE)), wait, health = 500))).replays()
        val stopped = busy.of(EventKind.INTERRUPTED).single()
        assertEquals(2, stopped.round)
        assertEquals(disrupting.single().id, stopped.effectId)
        assertEquals(listOf(4), busy.of(EventKind.CHANNEL_COMPLETED).map { it.round }, "the second cycle is not interrupted: once per fight")
    }

    // ---- return once, survival once ----

    private val raisable = "raisable"
    private fun necromancer(uses: Int) = foe("necro", channel("raise", Cadence.All(listOf(Cadence.ActionsFrom(2), Cadence.AllyDownedEligible(raisable))),
        ChannelDef("raise", "Raise the dead", uses, OnInterrupt.CYCLE, payload = listOf(Action.Return(raisable, 35)))), wait, health = 500)

    @Test fun `a returned enemy keeps its identity and spent effects, waits a round, and returns only once`() {
        val once = EffectDef("sk_once", "Bone shield", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Action.Guard(Aim.Self, Amount.Fixed(1))), Limit(perFight = 1), "The skeleton starts the fight with 1 Armor.")
        val sk = foe("sk", hit, health = 10, strike = 2, stats = mapOf(Stat.CHILL to 2), effects = listOf(once), tags = setOf(raisable))
        val r = fight(listOf(member("a", move("smash", Cadence.Actions(1, 5), dmg(Aim.BackFoe, 30)), wait, health = 300)), listOf(necromancer(uses = 2), sk)).replays()
        val back = r.of(EventKind.RETURNED).single()
        assertEquals("sk", back.target)
        assertEquals(4, back.amount, "35% of 10")
        assertEquals(3, back.round)
        assertTrue(r.events.none { it.kind == EventKind.ACTION && it.source == "sk" && it.round == 3 }, "no action in the round it returns")
        assertTrue(r.events.any { it.kind == EventKind.ACTION && it.source == "sk" && it.round == 4 })
        assertTrue(r.events.none { it.kind == EventKind.STAT_SPENT && it.stat == Stat.CHILL }, "its states are gone")
        assertEquals(1, r.events.count { it.effectId == "sk_once" && it.kind == EventKind.GUARD_GAINED }, "what it spent stays spent")
        assertEquals(2, r.events.count { it.kind == EventKind.DOWNED && it.target == "sk" })
        assertEquals(1, r.of(EventKind.CHANNEL_STARTED).size, "returned once, it is no longer eligible: the second use never starts")
        assertTrue(r.actor("sk")!!.returned)
    }

    @Test fun `nobody returns while four already stand`() {
        val units = mapOf("filler" to UnitDef("filler", "Filler", KitDef("filler", "Filler", listOf(wait)), health = 50, strikeMin = 0))
        val r = fight(listOf(member("a", move("smash", Cadence.Opener, dmg(Aim.BackFoe, 30)), wait)),
            listOf(necromancer(uses = 1), foe("caller", move("call", Cadence.Every(100, first = 2), Action.Summon("filler")), wait), foe("x", wait), foe("sk", wait, health = 10, tags = setOf(raisable))), units).replays()
        assertEquals(1, r.of(EventKind.SUMMONED).size)
        assertEquals(1, r.of(EventKind.CHANNEL_COMPLETED).size)
        assertTrue(r.of(EventKind.RETURNED).isEmpty())
        assertTrue(r.actors.count { it.side == Side.ENEMY && !it.downed } <= rules.maxActorsPerSide)
    }

    @Test fun `survival once leaves 1 health, not against Fire or Sun or a tick, and not again after a return`() {
        val smash = move("smash", Cadence.Always, dmg(Aim.Foe, 50))
        val zombie = { stats: Map<Stat, Int> -> foe("z", wait, health = 10, stats = stats, surviveOnce = true, tags = setOf(raisable)) }
        val plain = fight(listOf(member("a", smash)), listOf(zombie(emptyMap()))).replays()
        assertEquals(listOf(9, 50), plain.damageTo("z").map { it.amount }, "the record keeps the whole second blow")
        assertEquals(1, plain.of(EventKind.SURVIVED).single().round)
        assertEquals(2, plain.of(EventKind.DOWNED).single().round)

        for (element in listOf(Element.FIRE, Element.SUN)) {
            val r = fight(listOf(member("a", move("burn", Cadence.Always, dmg(Aim.Foe, 50, element)))), listOf(zombie(emptyMap()))).replays()
            assertTrue(r.of(EventKind.SURVIVED).isEmpty(), "$element")
            assertEquals(1, r.of(EventKind.DOWNED).single().round)
        }
        val bled = fight(listOf(member("a", wait)), listOf(zombie(mapOf(Stat.BLEED to 5)).copy(health = 3))).replays()
        assertTrue(bled.of(EventKind.SURVIVED).isEmpty() && bled.of(EventKind.DOWNED).isNotEmpty(), "a tick is no hit")

        val twice = move("twice", Cadence.Always, dmg(Aim.BackFoe, 50), dmg(Aim.BackFoe, 50))
        val returned = fight(listOf(member("a", twice, health = 300)), listOf(necromancer(uses = 1), zombie(emptyMap()))).replays()
        assertEquals(1, returned.of(EventKind.RETURNED).size)
        assertEquals(1, returned.of(EventKind.SURVIVED).size, "the survival was spent before the return")
        assertEquals(2, returned.events.count { it.kind == EventKind.DOWNED && it.target == "z" })
    }

    // ---- structural hits and engines ----

    private val shot = ChannelDef("shot", "Corpse shot", uses = 1, onInterrupt = OnInterrupt.SPENT, payload = listOf(Action.Structural(3), dmg(Aim.AllFoes, 2)), neutralizeWhenSpent = true)
    private fun catapult() = foe("cat", channel("load", Cadence.Actions(2), shot), wait, health = 40, tags = setOf(Tags.ENGINE))

    @Test fun `an engine's completed shot is one structural record, then it is neutralized and the fight can still be won`() {
        val r = fight(listOf(member("a", move("shoot", Cadence.Always, dmg(Aim.BackFoe, 10)))), listOf(catapult(), foe("grunt", wait, health = 45))).replays()
        assertEquals(FightOutcome.WON, r.outcome)
        assertEquals(3, r.of(EventKind.STRUCTURAL_HIT).single().amount)
        assertEquals(3, r.structural)
        val out = r.of(EventKind.NEUTRALIZED).single()
        assertEquals("cat", out.target)
        assertEquals(null, out.source, "nobody is credited with it")
        val cat = r.actor("cat")!!
        assertTrue(cat.neutralized && !cat.downed && cat.health == 40)
        assertTrue(r.events.none { it.kind == EventKind.DOWNED && it.target == "cat" })
        assertTrue(r.events.none { it.source == "cat" && it.round > out.round }, "it does nothing more")
        val pops = FightReplay.of(r)!!.steps.flatMap { it.pops }.map { it.text }
        assertTrue("Defences −3" in pops && "Neutralized" in pops && "Preparing Corpse shot" in pops)
    }

    @Test fun `an engine interrupted on its last use lands no structural hit and is neutralized all the same`() {
        val r = fight(listOf(member("a", move("shoot", Cadence.Always, dmg(Aim.BackFoe, 10))), member("b", move("silence", Cadence.Every(100, first = 3), Action.Interrupt(Aim.Foe)), wait)),
            listOf(catapult(), foe("grunt", wait, health = 45))).replays()
        assertEquals(1, r.of(EventKind.INTERRUPTED).size)
        assertEquals(0, r.structural)
        assertTrue(r.events.any { it.kind == EventKind.ACTION && it.source == "cat" && it.text == "cat: Corpse shot (interrupted)." }, "no attack is named that never comes")
        assertEquals(1, r.of(EventKind.NEUTRALIZED).size)
        assertEquals(FightOutcome.WON, r.outcome)
    }

    // ---- source weaknesses, recovery, the pack, Armor bypass and drain ----

    @Test fun `a weakness to a weapon's family, metal or an area hit applies once, the strongest`() {
        val bones = listOf(SourceBonus("family:axe", 30), SourceBonus("metal:silver", 50))
        val cut = move("cut", Cadence.Opener, dmg(Aim.Foe, 10))
        val r = fight(listOf(member("both", cut, tags = setOf("family:axe", "metal:silver")), member("axe", cut, tags = setOf("family:axe")), member("none", cut)),
            listOf(foe("sk", wait, health = 300, bonusFrom = bones))).replays()
        assertEquals(listOf(15, 13, 10), r.damageTo("sk").map { it.amount })
        assertEquals(listOf(Affinity.WEAK, Affinity.WEAK, null), r.damageTo("sk").map { it.affinity })

        val rats = fight(listOf(member("wide", move("sweep", Cadence.Opener, dmg(Aim.AllFoes, 10)), wait), member("one", cut)), listOf(foe("rats", wait, health = 300, bonusFrom = listOf(SourceBonus(Tags.AREA, 30))))).replays()
        assertEquals(listOf(13, 10), rats.damageTo("rats").map { it.amount })

        // The tags come from the weapon's own parts.
        val tags = CombatSandbox.fighter(CombatSandbox.hero("h", "Hal", LaunchContent.GUARDIAN), CombatSandbox.blade("w", LaunchContent.AXE, LaunchContent.SILVER, LaunchContent.EMBER_RESIN)).tags
        assertTrue("family:axe" in tags && "metal:silver" in tags)
    }

    @Test fun `recovery heals before the unit's action unless Fire, Burn or its named source struck it since`() {
        val troll = { tags: Set<String> -> foe("t", wait, health = 30, now = 20, recover = Recovery(2, tags)) }
        val fire = fight(listOf(member("a", move("burn", Cadence.Actions(1), dmg(Aim.Foe, 3, Element.FIRE)), wait)), listOf(troll(emptySet()))).replays()
        assertEquals(1, fire.of(EventKind.RECOVERY_STOPPED).single().round)
        assertEquals(2, fire.of(EventKind.HEALED).first().round)

        val silver = move("cut", Cadence.Actions(1), dmg(Aim.Foe, 3))
        val wolf = fight(listOf(member("a", silver, wait, tags = setOf("metal:silver"))), listOf(troll(setOf("metal:silver")))).replays()
        assertEquals(1, wolf.of(EventKind.RECOVERY_STOPPED).size)
        val iron = fight(listOf(member("a", silver, wait)), listOf(troll(setOf("metal:silver")))).replays()
        assertTrue(iron.of(EventKind.RECOVERY_STOPPED).isEmpty())
        assertEquals(1, iron.of(EventKind.HEALED).first().round)
    }

    @Test fun `the pack bonus needs another beast's hit this round, once a round, and the pack hunts its prey past a Guardian`() {
        val strikes = Move("bite", "Bite", Cadence.Always, listOf(Action.Damage(Aim.Foe, Amount.Strike()), Action.Damage(Aim.Foe, Amount.Strike())))
        val r = fight(listOf(member("a", wait, health = 500)),
            listOf(foe("w1", hit, strike = 10, tags = setOf(Tags.BEAST), pack = 20), foe("w2", strikes, strike = 10, tags = setOf(Tags.BEAST), pack = 20))).replays()
        val round1 = r.damageTo("a").filter { it.round == 1 }
        assertEquals(listOf("w1" to 10, "w2" to 12, "w2" to 10), round1.map { it.source to it.amount })

        val guardian = member("g", wait, stats = mapOf(Stat.GUARD to 10), tags = setOf("intercept"))
        val matriarch = foe("m", move("hunt", Cadence.Always, Action.MarkPrey(Aim.BackFoe)))
        val hunted = fight(listOf(guardian, member("back", wait)), listOf(matriarch, foe("wolf", hit, tags = setOf(Tags.PACK)))).replays()
        assertEquals("back", hunted.of(EventKind.PREY_MARKED).first().target)
        assertTrue(hunted.events.filter { it.source == "wolf" && (it.kind == EventKind.DAMAGE || it.kind == EventKind.GUARD_ABSORBED) }.all { it.target == "back" })
        val loose = fight(listOf(guardian, member("back", wait)), listOf(foe("wolf", hit, tags = setOf(Tags.PACK)))).replays()
        assertTrue(loose.events.filter { it.source == "wolf" && it.round == 1 }.any { it.target == "g" }, "without prey the Guardian draws it")
    }

    @Test fun `ignoring Armor passes points without using them up, and a drain heals from health damage only`() {
        val r = fight(listOf(member("a", move("pierce", Cadence.Opener, Action.Damage(Aim.Foe, Amount.Fixed(10), ignoreArmor = 4, drainPercent = 40, drainCap = 3)), wait, health = 30, now = 20)),
            listOf(foe("x", wait, health = 30, stats = mapOf(Stat.GUARD to 6)))).replays()
        assertEquals(2, r.of(EventKind.GUARD_ABSORBED).single().amount)
        assertEquals(8, r.damageTo("x").single().amount)
        assertTrue(r.of(EventKind.GUARD_BROKEN).isEmpty())
        assertEquals(4, FightReplay.of(r)!!.final.getValue("x").stats[Stat.GUARD])
        assertEquals(3, r.of(EventKind.HEALED).single { it.target == "a" }.amount, "40% of 8, at most 3")

        val blocked = fight(listOf(member("a", move("drain", Cadence.Opener, Action.Damage(Aim.Foe, Amount.Fixed(5), drainPercent = 100)), wait, health = 30, now = 20)),
            listOf(foe("x", wait, stats = mapOf(Stat.GUARD to 10)))).replays()
        assertTrue(blocked.of(EventKind.HEALED).isEmpty(), "Armor-blocked damage gives nothing")
    }

    @Test fun `Treant retaliation is one path, and Thorns never answer it`() {
        val bark = EffectDef("treant_bark", "Bark", Trigger(EventKind.DAMAGE, Who.SELF_TARGET), listOf(Action.Damage(Aim.EventSource, Amount.Fixed(3))), Limit(perRound = 1),
            "The first time each round an enemy's attack damages the Treant, the Treant deals 3 damage to that enemy. Once per round.", listOf(Condition.FromAction, Condition.SourceIsFoe))
        val r = fight(listOf(member("a", hit, stats = mapOf(Stat.THORNS to 4)), member("b", hit)), listOf(foe("treant", wait, health = 500, effects = listOf(bark)))).replays()
        assertEquals(r.rounds, r.events.count { it.effectId == "treant_bark" && it.kind == EventKind.DAMAGE }, "once per round, on the first attacker")
        assertTrue(r.events.filter { it.effectId == "treant_bark" && it.kind == EventKind.DAMAGE }.all { it.target == "a" })
        assertTrue(r.events.none { it.stat == Stat.THORNS }, "an effect's damage meets no Thorns")
    }

    @Test fun `action cadences count the actor's own actions, not rounds`() {
        val imp = UnitDef("imp", "Imp", KitDef("imp", "Imp", listOf(move("first", Cadence.Actions(1), Action.Give(Stat.CHILL, Aim.Foe)), wait)), health = 10, strikeMin = 0)
        val r = fight(listOf(member("a", wait)), listOf(foe("caller", move("call", Cadence.Every(100, first = 2), Action.Summon("imp")), wait)), mapOf("imp" to imp)).replays()
        val chill = r.of(EventKind.STAT_GAINED).single { it.stat == Stat.CHILL }
        assertEquals(3, chill.round, "summoned in round 2, its first action is in round 3")
    }

    @Test fun `the grammar's content checks`() {
        val bad = KitDef("bad", "Bad", listOf(
            channel("ram", Cadence.Actions(1), ChannelDef("ram", "Ram", uses = 0, onInterrupt = OnInterrupt.CYCLE, payload = listOf(Action.Structural(20)))),
            move("raise", Cadence.Actions(0), Action.Return(raisable, 40)),
            move("dose", Cadence.Always, Action.Give(Stat.POISON, Aim.Foe, Amount.Fixed(5))),
        ))
        val problems = EffectRules.problems(emptyList(), listOf(bad))
        assertTrue(problems.any { "has no use" in it })
        assertTrue(problems.any { "structural hit of 20" in it })
        assertTrue(problems.any { "without a channel's uses" in it })
        assertTrue(problems.any { "before the first" in it })
        assertTrue(problems.any { "Poison is given" in it })
        assertEquals(emptyList(), EffectRules.problems(combat.allEffects, combat.kits.map { it.kit }, combat.unitById), "the launch catalog passes")
    }

    // ---- forging the living bonuses ----

    @Test fun `Disrupting and Purifying are forged only in a living guild era, at Rare or better`() {
        val living = setOf(LaunchContent.DISRUPTING, LaunchContent.PURIFYING)
        val recipe = TestSupport.quickSword(Risk.RECKLESS, core = LaunchContent.STARSTEEL, augment = LaunchContent.EMBER_RESIN)
        fun forged(start: (Long) -> com.tinyblacksmith.core.model.GameState) = (1L..40L).flatMap { seed ->
            var s = start(seed).withMaterials()
            (1..3).mapNotNull { _ -> (TestSupport.engine.handle(s, recipe) as? com.tinyblacksmith.core.engine.CommandOutcome.Accepted)?.let { o -> s = o.state; o.state.weapon(o.forgedWeaponId!!) } }
        }
        val guild = forged { TestSupport.engine.guildRun(LegacyProfile(), it, GuildContent.TOWNS_LAST_HOPE) }
        assertTrue(guild.any { w -> w.affixes.any { it in living } }, "a living guild era rolls them")
        assertTrue(guild.filter { w -> w.affixes.any { it in living } }.all { it.rarity >= Rarity.RARE })
        assertTrue(forged { TestSupport.engine.newRun(LegacyProfile(), it) }.none { w -> w.affixes.any { it in living } }, "a classic era never does")
        assertTrue(forged { TestSupport.engine.guildRun(LegacyProfile(), it, GuildContent.TOWNS_LAST_HOPE, livingRevision = LivingRevision.LEGACY) }.none { w -> w.affixes.any { it in living } }, "nor a guild era begun before living heroes")
    }
}
