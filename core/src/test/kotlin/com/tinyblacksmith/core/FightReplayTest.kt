package com.tinyblacksmith.core

import com.tinyblacksmith.core.combat.*
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.guild.Missions
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.sim.CombatSandbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The siege replay and its report (`combat/Replay.kt`, `combat/Account.kt`): drawn from the record alone, consistent
 * with it to the point, and the same however often it is built. Each supported case has a fight of its own.
 */
class FightReplayTest {
    private val combat = LaunchContent.catalog.combat!!
    private val rules = FightRules()

    private fun striker(id: String, vararg extra: Action) = KitDef(id, id, listOf(Move("hit", "Hit", Cadence.Always, listOf<Action>(Action.Damage(Aim.Foe, Amount.Strike())) + extra)))
    private val idle = KitDef("idle", "idle", listOf(Move("wait", "Waits", Cadence.Always, emptyList())))
    private fun member(key: String, kit: KitDef, health: Int = 30, strike: Int = 5, effects: List<EffectDef> = emptyList(), element: Element? = null, support: Int = 0) =
        Combatant(key, key.replaceFirstChar { it.uppercase() }, Side.PARTY, kit, health, health, strike, strike, support, element = element, effects = effects, weaponName = "$key's blade", weaponId = "w-$key", weaponRules = effects.map { it.id })
    private fun foe(key: String, kit: KitDef = striker("foe"), health: Int = 30, strike: Int = 3, resists: Element? = null, weakTo: Element? = null, tickResist: Map<Stat, Int> = emptyMap()) =
        Combatant(key, "Foe $key", Side.ENEMY, kit, health, health, strike, strike, resists = resists, weakTo = weakTo, tickResist = tickResist, unitId = "unit_$key")
    private fun fight(party: List<Combatant>, foes: List<Combatant>, posture: Posture = Posture.RECKLESS) =
        Fight.resolve(FightSetup("test", party, foes, posture = posture, rules = rules, seed = 3, units = combat.unitById))

    private val searing = EffectDef("t_searing", "Searing Edge", Trigger(EventKind.DAMAGE, Who.SELF_SOURCE), listOf(Action.Give(Stat.BURN, Aim.EventTarget, Amount.Fixed(2))), Limit(perRound = 1), "On a hit: 2 Burn.", listOf(Condition.FromAction))
    private val leech = EffectDef("t_leech", "Life steal", Trigger(EventKind.DAMAGE, Who.SELF_SOURCE), listOf(Action.Heal(Aim.Self, Amount.OfEvent(50))), Limit(perRound = 1), "Heals half of a hit.", listOf(Condition.FromAction))
    private val cleave = EffectDef("t_cleave", "Cleave", Trigger(EventKind.ACTED, Who.SELF_SOURCE), listOf(Action.Damage(Aim.AllFoes, Amount.Fixed(2))), Limit(perRound = 1), "After acting: 2 to every foe.")

    private fun pops(r: Replay) = r.steps.flatMap { it.shownPops }

    /** What the record and the replay must agree on, for any fight. */
    private fun assertConsistent(r: FightResult, label: String) {
        val replay = assertNotNull(FightReplay.of(r), "$label: a current record replays")
        for (a in r.actors) {
            assertEquals(a.health, replay.final.getValue(a.key).health, "$label: ${a.name} ends on the record's health")
            assertEquals(a.downed, replay.final.getValue(a.key).downed, "$label: ${a.name} down as recorded")
        }
        // Every amount on screen is an event's own: damage pops add up to the damage recorded, heals to the healing.
        val damage = r.events.filter { it.kind == EventKind.DAMAGE }.sumOf { it.amount }
        assertEquals(damage, pops(replay).filter { it.kind == PopKind.DAMAGE }.sumOf { it.text.removePrefix("−").toInt() }, "$label: damage pops")
        val healed = r.events.filter { it.kind == EventKind.HEALED }.sumOf { it.amount }
        assertEquals(healed, pops(replay).filter { it.kind == PopKind.HEAL && it.text.startsWith("+") }.sumOf { it.text.removePrefix("+").toInt() }, "$label: heal pops")
        // A state is never shown below zero, and health stays inside the bar.
        replay.steps.forEach { s -> (0..s.pops.size).forEach { k -> s.stateAfter(k).forEach { (key, st) ->
            assertTrue(st.health in 0..replay.actor(key)!!.maxHealth && st.stats.values.all { it > 0 }, "$label: step ${s.index} keeps $key in bounds")
        } } }
        // The account counts every point once.
        val account = assertNotNull(FightAccounting.of(r))
        val enemyDamage = r.events.filter { it.kind == EventKind.DAMAGE && r.actor(it.target!!)?.side == Side.ENEMY }.sumOf { it.amount }
        assertEquals(enemyDamage, account.enemyDamage, label)
        assertEquals(enemyDamage, account.members.sumOf { it.damage } + account.shared.damage, "$label: damage is counted once")
        val blocked = r.events.filter { it.kind == EventKind.GUARD_ABSORBED && r.actor(it.target!!)?.side == Side.PARTY }.sumOf { it.amount }
        assertEquals(blocked, account.members.sumOf { it.prevented } + account.shared.prevented, "$label: prevented is counted once")
        val mended = r.events.filter { it.kind == EventKind.HEALED && r.actor(it.target!!)?.side == Side.PARTY }.sumOf { it.amount }
        assertEquals(mended, account.members.sumOf { it.healed } + account.shared.healed, "$label: healing is counted once")
        account.members.forEach { m ->
            assertEquals(m.damage, m.shares.sumOf { it.damage }, "$label: ${m.name}'s shares add up")
            assertEquals(m.shares.size, m.shares.map { it.ruleId }.toSet().size, "$label: ${m.name} has each rule once")
        }
        assertTrue(account.observations.size <= 2, label)
    }

    @Test fun `every fixture fight replays and adds up, as fought and as the save keeps it`() {
        for (f in CombatSandbox.fixtures) {
            val r = Fight.resolve(f.setup)
            assertConsistent(r, f.id)
            val kept = Missions.compact(r)
            assertConsistent(kept, "${f.id} (compacted)")
            // The save's copy shows the same thing as the fight itself.
            val a = FightReplay.of(r)!!; val b = FightReplay.of(kept)!!
            assertEquals(pops(a).map { Triple(it.eventId, it.text, it.note) }, pops(b).map { Triple(it.eventId, it.text, it.note) }, f.id)
            assertEquals(FightAccounting.of(r), FightAccounting.of(kept), f.id)
        }
    }

    @Test fun `a direct hit is the target's own number, and the actor is named on the step`() {
        val r = fight(listOf(member("ash", striker("duelist"), strike = 7)), listOf(foe("x", idle, health = 20)))
        val replay = FightReplay.of(r)!!
        val step = replay.steps.first { it.kind == StepKind.MOVE && it.actor == "ash" }
        assertEquals("Ash · Hit", step.title)
        val hit = step.shownPops.first()
        assertEquals(PopKind.DAMAGE, hit.kind); assertEquals("−7", hit.text); assertEquals("x", hit.target)
        assertEquals(13, step.stateAfter(step.pops.indexOf(hit) + 1).getValue("x").health, "the bar moves with the pop that says so")
    }

    @Test fun `a status is applied once and its later ticks are told apart`() {
        val r = fight(listOf(member("ash", striker("duelist"), strike = 4, effects = listOf(searing))), listOf(foe("x", idle, health = 60)))
        val replay = FightReplay.of(r)!!
        val applied = pops(replay).first { it.kind == PopKind.STATUS }
        assertEquals("Burn applied", applied.text); assertEquals("Searing Edge", applied.note); assertTrue(applied.weapon, "the blade's rule is marked as the blade's")
        val tick = pops(replay).first { it.tick == Stat.BURN && it.kind == PopKind.DAMAGE }
        assertEquals("Burn", tick.note)
        val tickStep = replay.steps.first { s -> tick in s.pops }
        assertEquals(StepKind.TICK, tickStep.kind)
        // The tick used a stack: the state after the step holds one less than before it, as the fight's rule says.
        assertEquals((tickStep.before.getValue("x").stats[Stat.BURN] ?: 0) - 1, tickStep.after.getValue("x").stats[Stat.BURN] ?: 0)
        // Later hits add stacks to a burning foe: "+", not "applied" again.
        assertTrue(pops(replay).filter { it.kind == PopKind.STATUS }.drop(1).all { it.text.startsWith("Burn +") || it.text == "Burn applied" })
        val account = FightAccounting.of(r)!!.members.single()
        val share = account.shares.first { it.ruleId == "t_searing" }
        assertTrue(share.damage > 0 && share.applied > 0, "the Burn's ticks count for the rule that put it on: $share")
        assertEquals(r.events.count { it.kind == EventKind.STAT_GAINED && it.effectId == "t_searing" }, share.times)
    }

    @Test fun `healing shows on the one healed, and only what it mended counts`() {
        val r = fight(listOf(member("ash", striker("duelist"), health = 30, strike = 6, effects = listOf(leech))), listOf(foe("x", health = 40, strike = 5)))
        val replay = FightReplay.of(r)!!
        // The first hit lands at full health: the rule fires and mends nothing, and the screen says so instead of "+0".
        assertEquals("Full health", pops(replay).first { it.kind == PopKind.HEAL }.text)
        val heal = pops(replay).first { it.kind == PopKind.HEAL && it.text.startsWith("+") }
        assertEquals("ash", heal.target); assertEquals("Life steal", heal.note)
        val account = FightAccounting.of(r)!!
        assertEquals(r.events.filter { it.kind == EventKind.HEALED }.sumOf { it.amount }, account.members.single().healed)
        assertTrue(r.events.filter { it.kind == EventKind.OVERHEAL }.sumOf { it.amount } >= 0)
    }

    @Test fun `a rule striking every foe pops on each in the order recorded`() {
        val r = fight(listOf(member("ash", striker("duelist"), strike = 3, effects = listOf(cleave))), listOf(foe("x", idle, health = 30), foe("y", idle, health = 30), foe("z", idle, health = 30)))
        val replay = FightReplay.of(r)!!
        val step = replay.steps.first { it.kind == StepKind.MOVE && it.actor == "ash" }
        val cleaves = step.shownPops.filter { it.rule == "Cleave" && it.kind == PopKind.DAMAGE }
        assertEquals(listOf("x", "y", "z"), cleaves.map { it.target })
        assertTrue(step.shownPops.indexOfFirst { it.rule == null } < step.shownPops.indexOfFirst { it.rule == "Cleave" }, "the strike lands before what it set off")
        assertEquals(listOf(RuleUse("ash", "t_cleave", "Cleave", true)), step.uses)
        val share = FightAccounting.of(r)!!.members.single().shares.first { it.ruleId == "t_cleave" }
        assertEquals(r.rounds, share.times, "fired once a round")
        assertEquals(r.events.filter { it.effectId == "t_cleave" && it.kind == EventKind.DAMAGE }.sumOf { it.amount }, share.damage)
    }

    @Test fun `a resisted element and a weakness are said where they happened`() {
        val r = fight(listOf(member("ash", striker("duelist"), strike = 10, element = Element.FIRE), member("bo", striker("ranger"), strike = 10, element = Element.FROST)),
            listOf(foe("x", idle, health = 200, resists = Element.FIRE, weakTo = Element.FROST)))
        val replay = FightReplay.of(r)!!
        assertTrue(pops(replay).filter { it.source == "ash" && it.kind == PopKind.DAMAGE }.all { it.note == "Resisted" && it.text == "−7" })
        assertTrue(pops(replay).filter { it.source == "bo" && it.kind == PopKind.DAMAGE }.all { it.note == "Weak" && it.text == "−13" })
        val notes = FightAccounting.of(r)!!.observations
        assertTrue(notes.any { it.endsWith("of Ash's hits were resisted.") }, "$notes")
    }

    @Test fun `a lost fight ends on the defeat with everybody down as recorded`() {
        val r = fight(listOf(member("ash", striker("duelist"), health = 8, strike = 1)), listOf(foe("x", health = 50, strike = 6)))
        assertEquals(FightOutcome.LOST, r.outcome)
        val replay = FightReplay.of(r)!!
        assertEquals(StepKind.END, replay.steps.last().kind); assertEquals("Defeat", replay.steps.last().title)
        assertTrue(pops(replay).any { it.kind == PopKind.DOWN && it.target == "ash" })
        assertTrue(replay.final.getValue("ash").downed)
        assertEquals(replay.steps.last().index, replay.highlights.last(), "the short version ends where the fight did")
        assertTrue(replay.steps.first { s -> s.shownPops.any { it.kind == PopKind.DOWN } }.index in replay.highlights)
    }

    @Test fun `the short version holds only turning points and never pads a quiet fight`() {
        val quiet = fight(listOf(member("ash", striker("duelist"), strike = 1)), listOf(foe("x", idle, health = 500)), posture = Posture.RECKLESS)
        val replay = FightReplay.of(quiet)!!
        // Undecided after the last round, with nothing turning: only the end is in it.
        assertEquals(listOf(replay.steps.last().index), replay.highlights)
        for (f in CombatSandbox.fixtures) {
            val r = FightReplay.of(Fight.resolve(f.setup))!!
            assertTrue(r.highlights.size <= FightReplay.MAX_HIGHLIGHTS + 1 && r.highlights == r.highlights.sorted(), f.id)
            assertTrue(r.highlights.dropLast(1).all { r.steps[it].turns.isNotEmpty() }, f.id)
        }
    }

    @Test fun `a fight recorded before replays, or one that does not add up, gets the summary instead`() {
        val r = fight(listOf(member("ash", striker("duelist"))), listOf(foe("x")))
        assertNull(FightReplay.of(r.copy(record = 0)), "an older record has no replay")
        assertNull(FightAccounting.of(r.copy(record = 0)))
        assertNull(FightReplay.of(r.copy(actors = r.actors.map { if (it.key == "x") it.copy(health = it.health + 1) else it })), "a record that does not add up is not replayed")
        assertNull(FightReplay.of(r.copy(actors = r.actors.map { it.copy(start = null) })))
    }

    @Test fun `building the replay changes nothing and gives the same thing every time`() {
        val r = CombatSandbox.run("stormwell")
        val copy = r.copy()
        val a = FightReplay.of(r); val b = FightReplay.of(r)
        assertEquals(a, b); assertEquals(FightAccounting.of(r), FightAccounting.of(r))
        assertEquals(copy, r)
    }

    @Test fun `the record survives the save and an older save still reads`() {
        val r = Missions.compact(CombatSandbox.run("blood-bank"))
        val json = SaveCodec.json.encodeToString(FightResult.serializer(), r)
        val back = SaveCodec.json.decodeFromString(FightResult.serializer(), json)
        assertEquals(r, back)
        assertEquals(FightReplay.of(r), FightReplay.of(back))
        // An older record: none of the new keys, and it still reads, as a fight without a replay.
        val old = SaveCodec.json.encodeToString(FightResult.serializer(), r.copy(record = 0, rules = emptyMap(), actors = r.actors.map { ActorResult(it.key, it.name, it.side, it.health, it.maxHealth, it.downed, it.fractured, it.summoned) },
            events = r.events.map { it.copy(tick = null, affinity = null) }))
        listOf("\"record\"", "\"rules\"", "\"start\"", "\"tick\"", "\"affinity\"", "\"weaponRules\"").forEach { assertTrue(it !in old, "$it is not written when it has nothing to say") }
        assertNull(FightReplay.of(SaveCodec.json.decodeFromString(FightResult.serializer(), old)))
    }

    @Test fun `the paid health of a blood rule is a cost, not damage`() {
        val r = CombatSandbox.run("blood-bank")
        val replay = FightReplay.of(r)!!
        val paid = pops(replay).filter { it.kind == PopKind.PAID }
        assertTrue(paid.isNotEmpty() && paid.all { it.note!!.startsWith("Paid") })
        val account = FightAccounting.of(r)!!
        assertEquals(r.events.filter { it.kind == EventKind.DAMAGE && r.actor(it.target!!)?.side == Side.ENEMY }.sumOf { it.amount }, account.enemyDamage)
    }

    @Test fun `a helper's work counts for the rule that called it`() {
        val call = EffectDef("t_call", "Call the helper", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Action.Summon(com.tinyblacksmith.core.content.CombatContent.SPECTRAL_HELPER)), Limit(perFight = 1), "Calls a helper once.")
        val r = fight(listOf(member("ash", striker("duelist"), strike = 2, effects = listOf(call))), listOf(foe("x", idle, health = 60)))
        val replay = FightReplay.of(r)!!
        assertTrue(pops(replay).any { it.kind == PopKind.JOINED })
        val helper = r.actors.single { it.summoned }
        assertEquals(false, replay.start.getValue(helper.key).present, "a helper is not on the field before it is called")
        val share = FightAccounting.of(r)!!.members.single().shares.first { it.ruleId == "t_call" }
        val helperDamage = r.events.filter { it.kind == EventKind.DAMAGE && it.source == helper.key }.sumOf { it.amount }
        assertTrue(helperDamage > 0)
        assertEquals(helperDamage, share.damage)
    }

    @Test fun `playback shows every pop once, in the recorded order, and ends on the record's state`() {
        for (f in CombatSandbox.fixtures) {
            val replay = FightReplay.of(Missions.compact(Fight.resolve(f.setup)))!!
            val full = ReplayTimeline.frames(replay, short = false)
            assertEquals(pops(replay).map { it.eventId to it.target }, full.mapNotNull { it.pop }.map { it.eventId to it.target }, f.id)
            assertEquals(replay.final, ReplayTimeline.state(replay, full.last()), f.id)
            assertEquals(FrameKind.END, full.last().kind)
            // The bars of a frame are never ahead of its label: what a pop changed is on screen only from its own frame.
            full.zipWithNext().forEach { (a, b) -> if (a.step == b.step) assertTrue(b.applied >= a.applied, f.id) }
            val short = ReplayTimeline.frames(replay, short = true)
            assertTrue(short.map { it.step }.toSet() == replay.highlights.toSet(), f.id)
            assertTrue(short.size <= full.size)
            short.forEach { fr -> assertEquals(ReplayTimeline.labels(replay, fr).size, ReplayTimeline.labels(replay, fr).keys.toSet().size) }
            // Faster playback keeps a floor: a pop is never on screen for less than half a second.
            assertTrue(FrameKind.entries.all { ReplayTimeline.millis(it, fast = true) >= 320 } && ReplayTimeline.millis(FrameKind.POP, fast = true) >= 500)
        }
    }

    @Test fun `the recent-action strip says who did what to whom in the record's numbers`() {
        val r = fight(listOf(member("ash", striker("duelist"), strike = 3, effects = listOf(cleave))), listOf(foe("x", idle, health = 30), foe("y", idle, health = 30), foe("z", idle, health = 30), foe("w", idle, health = 30)))
        val replay = FightReplay.of(r)!!
        val step = replay.steps.first { it.kind == StepKind.MOVE }
        assertEquals("Ash · Hit: Foe x −3, Foe x −2 (Cleave), Foe y −2 (Cleave), 2 more", ReplayTimeline.summary(replay, step))
    }
}
