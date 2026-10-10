package com.tinyblacksmith.core

import com.tinyblacksmith.core.combat.*
import com.tinyblacksmith.core.content.CombatContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.guild.Loadout
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.sim.CombatSandbox
import com.tinyblacksmith.core.sim.CombatSandbox.blade
import com.tinyblacksmith.core.sim.CombatSandbox.enemies
import com.tinyblacksmith.core.sim.CombatSandbox.fighter
import com.tinyblacksmith.core.sim.CombatSandbox.hero
import com.tinyblacksmith.core.sim.CombatSandbox.steady
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The rules of the interaction engine by themselves (plan A03 to A06, spec 6.4 and 17.4). No run, no save. */
class CombatEngineTest {
    private val combat = LaunchContent.catalog.combat!!
    private val rules = FightRules()

    private fun stand(id: String = "idle") = KitDef(id, id, listOf(Move("wait", "Waits", Cadence.Always, emptyList())))
    private fun striker(id: String = "striker") = KitDef(id, id, listOf(Move("hit", "Hits", Cadence.Always, listOf(Action.Damage(Aim.Foe, Amount.Strike())))))
    private fun member(key: String, kit: KitDef = striker(), health: Int = 30, strike: Int = 5, effects: List<EffectDef> = emptyList(), stats: Map<Stat, Int> = emptyMap(), support: Int = 0, tags: Set<String> = emptySet()) =
        Combatant(key, key, Side.PARTY, kit, health, health, strike, strike, support, effects = effects, stats = stats, tags = tags, weaponName = "blade")
    private fun foe(key: String, kit: KitDef = striker(), health: Int = 30, strike: Int = 5, effects: List<EffectDef> = emptyList(), stats: Map<Stat, Int> = emptyMap(), tags: Set<String> = emptySet()) =
        Combatant(key, key, Side.ENEMY, kit, health, health, strike, strike, effects = effects, stats = stats, tags = tags)
    private fun fight(party: List<Combatant>, foes: List<Combatant>, relics: List<EffectDef> = emptyList(), posture: Posture = Posture.RECKLESS, objective: Objective = Objective(), rules: FightRules = this.rules, seed: Long = 1) =
        Fight.resolve(FightSetup("test", party, foes, relics, objective, posture, rules, seed, combat.unitById))

    private fun FightResult.of(kind: EventKind, effect: String? = null) = events.filter { it.kind == kind && (effect == null || it.effectId == effect) }
    private fun FightResult.chain(e: FightEvent): List<FightEvent> = generateSequence(e) { x -> x.parentId?.let { id -> events.first { it.id == id } } }.toList().asReversed()

    // ---- determinism, termination, shape of the timeline ----

    @Test fun `the same setup gives the same timeline`() {
        for (f in CombatSandbox.fixtures) assertEquals(Fight.resolve(f.setup), Fight.resolve(f.setup), f.id)
    }

    @Test fun `a seed only moves strikes that have a range`() {
        val party = listOf(fighter(CombatSandbox.brann, CombatSandbox.capacitor), fighter(CombatSandbox.mira, CombatSandbox.healingStaff))
        val ranged = enemies("ashclaw_warchief", "ashclaw_brute", percent = 150)
        val outcomes = (1L..40L).map { Fight.resolve(FightSetup("seeded", party, ranged, rules = rules, seed = it, units = combat.unitById)) }
        assertTrue(outcomes.map { it.events }.toSet().size > 1, "seeds should differ when strikes have a range")
        val pinned = steady(ranged)
        assertEquals(1, (1L..10L).map { Fight.resolve(FightSetup("pinned", party, pinned, rules = rules, seed = it, units = combat.unitById)) }.toSet().size)
    }

    @Test fun `a fight nobody can finish stops at the last round`() {
        val r = fight(listOf(member("a", stand())), listOf(foe("x", stand())))
        assertEquals(FightOutcome.TIMED_OUT, r.outcome)
        assertEquals(rules.maxRounds, r.rounds)
    }

    @Test fun `every event points at an earlier parent and at the root of its chain`() {
        for (f in CombatSandbox.fixtures) {
            val r = Fight.resolve(f.setup)
            val shown = r.events.associateBy { it.id }
            for (e in r.events) {
                e.parentId?.let { assertTrue(it < e.id, "${f.id}: event ${e.id} has a later parent") }
                assertTrue(e.rootId <= e.id)
                if (e.kind == EventKind.DAMAGE || e.kind == EventKind.HEALED) assertTrue(e.amount > 0, "${f.id}: ${e.kind} of ${e.amount}")
            }
            for (h in r.highlights) for (id in h.eventIds) assertTrue(shown.getValue(id).text in h.text, "${f.id}: a highlight says something the timeline does not")
            assertTrue(r.highlights.size <= 3)
            for (a in r.actors) assertTrue(a.health in 0..a.maxHealth && (a.health == 0) == a.downed, "${f.id}: ${a.name}")
        }
    }

    @Test fun `a fight result survives the save codec`() {
        val r = CombatSandbox.run("scrap-choir")
        assertEquals(r, SaveCodec.json.decodeFromString(FightResult.serializer(), SaveCodec.json.encodeToString(FightResult.serializer(), r)))
    }

    // ---- built-in states ----

    @Test fun `guard is spent before health and a blow it takes whole is no damage`() {
        val r = fight(listOf(member("a", stand(), stats = mapOf(Stat.GUARD to 5))), listOf(foe("x", strike = 5)), rules = rules.copy(maxRounds = 1))
        assertEquals(30, r.actor("a")!!.health)
        assertTrue(r.of(EventKind.DAMAGE).isEmpty())
        assertEquals(1, r.of(EventKind.GUARD_BROKEN).size)
    }

    @Test fun `a mark adds its bonus to one direct hit and is gone`() {
        val r = fight(listOf(member("a", strike = 5), member("b", strike = 5)), listOf(foe("x", stand(), health = 100, stats = mapOf(Stat.MARK to 1))), rules = rules.copy(maxRounds = 1))
        assertEquals(listOf(5 + rules.markBonus, 5), r.of(EventKind.DAMAGE).map { it.amount })
        assertEquals(1, r.of(EventKind.STAT_SPENT).count { it.stat == Stat.MARK })
    }

    @Test fun `chill weakens one strike and is spent by it`() {
        val r = fight(listOf(member("a", strike = 10, stats = mapOf(Stat.CHILL to 1))), listOf(foe("x", stand(), health = 100)), rules = rules.copy(maxRounds = 2))
        assertEquals(listOf(Fight.pct(10, 100 - rules.chillPercent), 10), r.of(EventKind.DAMAGE).map { it.amount })
    }

    @Test fun `burn ticks at round end, one less each round, and a resistant foe takes a share`() {
        val r = fight(listOf(member("a", stand())), listOf(foe("x", stand(), health = 100, stats = mapOf(Stat.BURN to 3))), rules = rules.copy(maxRounds = 4))
        assertEquals(listOf(3, 2, 1), r.of(EventKind.DAMAGE).map { it.amount })
        val half = Fight.resolve(FightSetup("resist", listOf(member("a", stand())), listOf(foe("x", stand(), health = 100, stats = mapOf(Stat.BURN to 4)).copy(tickResist = mapOf(Stat.BURN to 50))), rules = rules.copy(maxRounds = 1)))
        assertEquals(listOf(2), half.of(EventKind.DAMAGE).map { it.amount })
    }

    @Test fun `thorns answer one blow and are not answered themselves`() {
        val r = fight(listOf(member("a", strike = 5, stats = mapOf(Stat.THORNS to 4))), listOf(foe("x", strike = 5, stats = mapOf(Stat.THORNS to 4))), rules = rules.copy(maxRounds = 2))
        // Each side's thorns fire once, on the first blow, and the thorn damage draws no thorns back.
        assertEquals(2, r.events.count { it.kind == EventKind.DAMAGE && it.via == Via.EFFECT && it.amount == 4 })
    }

    @Test fun `a downed fighter does not act, is not healed and stays down`() {
        val healer = KitDef("healer", "healer", listOf(Move("heal", "Heals", Cadence.Always, listOf(Action.Heal(Aim.WeakestAlly, Amount.Fixed(10))))))
        val r = fight(listOf(member("a", stand(), health = 3), member("b", healer, health = 30)), listOf(foe("x", strike = 5, health = 500)), rules = rules.copy(maxRounds = 3))
        assertTrue(r.actor("a")!!.downed)
        assertTrue(r.of(EventKind.HEALED).none { it.target == "a" })
        assertTrue(r.events.none { it.kind == EventKind.ACTION && it.source == "a" && it.round > 1 && it.via == Via.ACTION })
    }

    // ---- posture and objectives ----

    @Test fun `a cautious party leaves when one is down, a reckless one stays`() {
        val party = listOf(member("a", health = 4), member("b", health = 60))
        val foes = listOf(foe("x", strike = 6, health = 500))
        assertEquals(FightOutcome.RETREATED, fight(party, foes, posture = Posture.CAUTIOUS).outcome)
        assertEquals(1, fight(party, foes, posture = Posture.CAUTIOUS).rounds)
        assertEquals(FightOutcome.LOST, fight(party, foes, posture = Posture.RECKLESS, rules = rules.copy(maxRounds = 30)).outcome)
    }

    @Test fun `a balanced party leaves when two are down`() {
        val party = listOf(member("a", health = 4), member("b", health = 10), member("c", health = 200))
        val r = fight(party, listOf(foe("x", strike = 6, health = 500)), posture = Posture.BALANCED)
        assertEquals(FightOutcome.RETREATED, r.outcome)
        assertEquals(2, r.actors.count { it.side == Side.PARTY && it.downed })
    }

    @Test fun `holding wins at the round the objective names and loses with what it protects`() {
        val cart = Loadout.enemy(combat.unit("escort_cart"), "cart").copy(side = Side.PARTY)
        val held = fight(listOf(member("a", stand(), health = 500), cart.copy(maxHealth = 500, health = 500)), listOf(foe("x", strike = 3, health = 500)), objective = Objective(ObjectiveKind.HOLD, 4, "cart"))
        assertEquals(FightOutcome.WON, held.outcome)
        assertEquals(4, held.rounds)
        // The thief strikes the last in the line: the cart.
        val lost = fight(listOf(member("a", stand(), health = 500), cart.copy(maxHealth = 5, health = 5)), enemies("ashclaw_thief"), objective = Objective(ObjectiveKind.HOLD, 6, "cart"))
        assertEquals(FightOutcome.LOST, lost.outcome)
    }

    // ---- Stormwell (spec 8.1) ----

    @Test fun `stormwell - the opener and two effective heals make exactly one burst`() {
        val r = CombatSandbox.run("stormwell")
        val gains = r.of(EventKind.STAT_GAINED).filter { it.stat == Stat.CHARGE && it.target == "h1" }
        assertEquals(null, gains[0].effectId, "the first Charge is the Battlemage's opener")
        assertEquals(listOf(CombatContent.CAPACITOR.id, CombatContent.CAPACITOR.id), gains.drop(1).take(2).map { it.effectId })
        val firstBurst = r.of(EventKind.DAMAGE, CombatContent.CHARGE_BURST.id).first()
        assertTrue(firstBurst.id > gains[2].id)
        assertEquals(8, firstBurst.amount - if (r.chain(firstBurst).any { it.kind == EventKind.STAT_SPENT && it.stat == Stat.MARK }) rules.markBonus else 0)
        // Heal -> Charge -> spend -> burst, in one chain that starts with the Warden's action.
        val chain = r.chain(firstBurst)
        assertEquals(listOf(EventKind.ACTION, EventKind.HEALED, EventKind.STAT_GAINED, EventKind.STAT_SPENT), chain.take(4).map { it.kind })
        assertEquals("h2", chain[0].source)
        // Charge is spent before the burst is recorded, and never more often than it was stored.
        val spends = r.of(EventKind.STAT_SPENT, CombatContent.CHARGE_BURST.id)
        assertEquals(spends.size, r.of(EventKind.DAMAGE, CombatContent.CHARGE_BURST.id).size)
        assertTrue(spends.sumOf { it.amount } <= gains.sumOf { it.amount })
        assertTrue(spends.all { it.amount >= 3 })
    }

    @Test fun `stormwell - the capacitor stores one charge a round however often its wielder is healed`() {
        val twoWardens = listOf(fighter(CombatSandbox.brann.copy(health = 40), CombatSandbox.capacitor), fighter(CombatSandbox.mira, CombatSandbox.healingStaff), fighter(hero("h9", "Sela", LaunchContent.WARDEN), CombatSandbox.healingStaff))
        val r = fight(twoWardens, listOf(foe("x", stand(), health = 500)), rules = rules.copy(maxRounds = 1))
        assertEquals(2, r.of(EventKind.HEALED).count { it.target == "h1" && it.via == Via.ACTION })
        assertEquals(1, r.of(EventKind.STAT_GAINED, CombatContent.CAPACITOR.id).size)
    }

    @Test fun `stormwell - healing at full health gives guard and no charge`() {
        val r = CombatSandbox.run("overheal")
        assertTrue(r.of(EventKind.OVERHEAL).isNotEmpty())
        assertTrue(r.of(EventKind.STAT_GAINED, CombatContent.CAPACITOR.id).isEmpty())
        val basin = r.of(EventKind.GUARD_GAINED, "relic_overflow_basin")
        assertTrue(basin.isNotEmpty() && basin.all { it.amount <= 4 })
        // The Guard the Basin gave is not healing: no HEALED event descends from it.
        assertTrue(r.of(EventKind.HEALED).none { h -> r.chain(h).any { it.effectId == "relic_overflow_basin" } })
    }

    @Test fun `stormwell - the basin gives each member at most four guard a round`() {
        val healer = KitDef("big", "big", listOf(Move("heal", "Heals", Cadence.Always, listOf(Action.Heal(Aim.AllAllies, Amount.Fixed(20)), Action.Heal(Aim.AllAllies, Amount.Fixed(20))))))
        val r = fight(listOf(member("a", healer), member("b", stand())), listOf(foe("x", stand(), health = 500)), CombatSandbox.relic(CombatContent.OVERFLOW_BASIN), rules = rules.copy(maxRounds = 1))
        assertEquals(mapOf("a" to 4, "b" to 4), r.of(EventKind.GUARD_GAINED).groupBy { it.target!! }.mapValues { it.value.sumOf { e -> e.amount } })
    }

    @Test fun `stormwell - taking a piece away changes what happens`() {
        val full = CombatSandbox.run("stormwell")
        val noCapacitor = CombatSandbox.run("stormwell-no-capacitor")
        val noHealer = CombatSandbox.run("stormwell-no-healer")
        assertTrue(full.of(EventKind.DAMAGE, CombatContent.CHARGE_BURST.id).isNotEmpty())
        assertTrue(noCapacitor.events.none { it.stat == Stat.CHARGE || it.effectId == CombatContent.CHARGE_BURST.id })
        assertTrue(noHealer.of(EventKind.DAMAGE, CombatContent.CHARGE_BURST.id).isEmpty(), "one opening Charge never reaches three without healing")
        assertTrue(full.rounds < noCapacitor.rounds, "the burst should end the fight sooner (${full.rounds} against ${noCapacitor.rounds})")
        assertTrue(full.actor("h1")!!.health > noHealer.actor("h1")!!.health)
    }

    // ---- Scrap Choir (spec 8.2) ----

    @Test fun `scrap choir - one crack, its scrap spent at once, guard for everyone`() {
        val r = CombatSandbox.run("scrap-choir")
        val crack = r.of(EventKind.FRACTURED).single()
        assertEquals("h1", crack.source)
        assertTrue(r.actor("h1")!!.fractured)
        val broken = r.events.first { it.id == crack.parentId }
        assertEquals(EventKind.GUARD_BROKEN, broken.kind)
        assertTrue(broken.source!!.startsWith("e"), "an enemy's blow cracked it")
        assertEquals(2, r.of(EventKind.STAT_SPENT, "relic_salvage_bell").single().amount)
        val bell = r.of(EventKind.GUARD_GAINED, "relic_salvage_bell")
        assertEquals(setOf("h1", "h2", "h3"), bell.map { it.target }.toSet())
        assertTrue(bell.all { it.amount == 3 })
    }

    @Test fun `scrap choir - the rune answers later breaks, not the one that cracked the blade, and at most twice`() {
        val r = CombatSandbox.run("scrap-choir")
        val crack = r.of(EventKind.FRACTURED).single()
        val answers = r.of(EventKind.DAMAGE, CombatContent.RUNED.id)
        assertTrue(answers.isNotEmpty() && answers.size <= 2)
        assertTrue(answers.all { it.id > crack.id && it.parentId != crack.parentId }, "the breaking blow itself is not answered")
        assertTrue(answers.groupBy { it.round }.all { it.value.size == 1 })
    }

    @Test fun `scrap choir - guard the wielder gives up itself never cracks the blade`() {
        val drop = EffectDef("drop_guard", "Drop", Trigger(EventKind.ROUND_START, Who.ANY), listOf(Action.Take(Stat.GUARD, Aim.Self, Amount.Fixed(99))), Limit(perRound = 1), "Drops its own Guard.")
        val brittle = combat.affixEffects.getValue(LaunchContent.BRITTLE)
        val r = fight(listOf(member("a", stand(), effects = brittle + drop, stats = mapOf(Stat.GUARD to 6))), listOf(foe("x", stand())), rules = rules.copy(maxRounds = 2))
        assertTrue(r.of(EventKind.STAT_SPENT, "drop_guard").isNotEmpty())
        assertTrue(r.of(EventKind.FRACTURED).isEmpty())
        assertFalse(r.actor("a")!!.fractured)
    }

    @Test fun `scrap choir - a blade cracks once a fight however often its guard breaks`() {
        val r = CombatSandbox.run("scrap-choir")
        assertTrue(r.of(EventKind.GUARD_BROKEN).count { it.target == "h1" } > 1)
        assertEquals(1, r.of(EventKind.FRACTURED).size)
    }

    @Test fun `scrap choir - taking a piece away changes what happens`() {
        val sound = CombatSandbox.run("scrap-choir-sound-blade")
        val noBell = CombatSandbox.run("scrap-choir-no-bell")
        assertTrue(sound.of(EventKind.FRACTURED).isEmpty() && sound.of(EventKind.DAMAGE, CombatContent.RUNED.id).isEmpty() && sound.of(EventKind.GUARD_GAINED, "relic_salvage_bell").isEmpty())
        assertEquals(1, noBell.of(EventKind.FRACTURED).size)
        assertTrue(noBell.of(EventKind.GUARD_GAINED, "relic_salvage_bell").isEmpty())
        assertTrue(noBell.of(EventKind.STAT_SPENT).none { it.stat == Stat.SCRAP }, "without the Bell the Scrap is never spent")
    }

    // ---- other families of rules ----

    @Test fun `blood bank - a payment is not damage, never goes under its floor and is not paid twice an action`() {
        val bloodbound = combat.affixEffects.getValue(LaunchContent.BLOODBOUND)
        val r = fight(listOf(member("a", health = 15, effects = bloodbound, stats = mapOf(Stat.THORNS to 5))), listOf(foe("x", stand(), health = 500, stats = mapOf(Stat.THORNS to 5))), rules = rules.copy(maxRounds = 3))
        val paid = r.of(EventKind.HEALTH_PAID)
        assertEquals(listOf(3), paid.map { it.amount }, "15 health, floor 12: one payment of 3, then nothing to pay")
        assertEquals(6, r.of(EventKind.DAMAGE, "affix_bloodbound").single().amount)
        assertTrue(paid.single().id < r.of(EventKind.DAMAGE).first { it.source == "a" && it.effectId == null }.id, "paid before the strike of the action")
        assertTrue(r.of(EventKind.DAMAGE).none { it.target == "a" && r.chain(it).any { c -> c.kind == EventKind.HEALTH_PAID } }, "paying draws no damage back")
    }

    @Test fun `icebreaker - the heavy blade shatters chill once a round and the chill is gone`() {
        val r = CombatSandbox.run("icebreaker")
        val shatters = r.of(EventKind.DAMAGE, "affix_heavy_shatter")
        assertTrue(shatters.isNotEmpty() && shatters.groupBy { it.round }.all { it.value.size == 1 })
        assertTrue(r.of(EventKind.STAT_SPENT, "affix_heavy_shatter").all { it.stat == Stat.CHILL && it.amount in 1..3 })
    }

    @Test fun `an echo is never echoed`() {
        val mages = listOf(fighter(hero("m1", "Ana", LaunchContent.BATTLEMAGE), CombatSandbox.sigilStaff), fighter(hero("m2", "Bel", LaunchContent.BATTLEMAGE), CombatSandbox.sigilStaff))
        val r = fight(mages, listOf(foe("x", stand(), health = 500)), rules = rules.copy(maxRounds = 1))
        val burns = r.of(EventKind.STAT_GAINED).filter { it.stat == Stat.BURN }
        // Each mage's hit puts one Burn; each mage echoes once a round; an echo answers a hit, never the other echo.
        assertEquals(2, burns.count { it.effectId == "element_fire" })
        assertEquals(2, burns.count { it.effectId == "battlemage_echo" })
        assertTrue(burns.filter { it.effectId == "battlemage_echo" }.all { e -> r.events.first { it.id == e.parentId }.effectId == "element_fire" })
    }

    @Test fun `one helper a fight, and it is not a member`() {
        val r = fight(listOf(member("a", strike = 50)), listOf(foe("x", health = 5), foe("y", health = 5), foe("z", stand(), health = 5000)), CombatSandbox.relic(CombatContent.BONE_MUSIC_BOX), rules = rules.copy(maxRounds = 3))
        assertEquals(1, r.of(EventKind.SUMMONED).size)
        assertTrue(r.actors.single { it.summoned }.side == Side.PARTY)
        // The helper alone is not a party: when the member falls the fight is lost.
        val alone = fight(listOf(member("a", strike = 50, health = 6)), listOf(foe("x", health = 5), foe("z", strike = 6, health = 5000)), CombatSandbox.relic(CombatContent.BONE_MUSIC_BOX))
        assertEquals(FightOutcome.LOST, alone.outcome)
    }

    // ---- loops (spec 6.4, 17.4) ----

    @Test fun `an effect never answers its own product`() {
        val feed = EffectDef("feed", "Feed", Trigger(EventKind.STAT_GAINED, Who.SELF_TARGET, Stat.CHARGE), listOf(Action.Give(Stat.CHARGE, Aim.Self)), Limit(perRound = 100), "Bad content: Charge gives Charge.")
        val r = fight(listOf(member("a", stand(), effects = listOf(feed), stats = emptyMap()), member("b", KitDef("g", "g", listOf(Move("g", "Gives", Cadence.Always, listOf(Action.Give(Stat.CHARGE, Aim.AllyTagged("t"))))))).copy()),
            listOf(foe("x", stand())), rules = rules.copy(maxRounds = 1)).let { it }
        assertTrue(r.of(EventKind.STAT_GAINED, "feed").size <= 1)
        assertTrue("Effect feed feeds itself" in EffectRules.problems(listOf(feed)))
    }

    @Test fun `two rules that feed each other without a real limit trip the guard, and say where`() {
        val a = EffectDef("ping", "Ping", Trigger(EventKind.GUARD_GAINED, Who.SELF_TARGET), listOf(Action.Give(Stat.STEAM, Aim.Self), Action.Take(Stat.STEAM, Aim.Self)), Limit(perRound = 100000), "Bad content.")
        val b = EffectDef("pong", "Pong", Trigger(EventKind.STAT_SPENT, Who.SELF_TARGET, Stat.STEAM), listOf(Action.Guard(Aim.Self, Amount.Fixed(1)), Action.Take(Stat.GUARD, Aim.Self)), Limit(perRound = 100000), "Bad content.")
        val kick = KitDef("kick", "kick", listOf(Move("k", "Kicks", Cadence.Always, listOf(Action.Guard(Aim.Self, Amount.Fixed(1))))))
        val thrown = assertFailsWith<FightLoopException> { fight(listOf(member("a", kick, effects = listOf(a, b))), listOf(foe("x", stand())), seed = 99) }
        assertEquals(99, thrown.seed)
        assertEquals(1, thrown.round)
    }

    @Test fun `with their limits the same two rules stop`() {
        val a = EffectDef("ping", "Ping", Trigger(EventKind.GUARD_GAINED, Who.SELF_TARGET), listOf(Action.Give(Stat.STEAM, Aim.Self), Action.Take(Stat.STEAM, Aim.Self)), Limit(perRound = 2), "Limited.")
        val b = EffectDef("pong", "Pong", Trigger(EventKind.STAT_SPENT, Who.SELF_TARGET, Stat.STEAM), listOf(Action.Guard(Aim.Self, Amount.Fixed(1)), Action.Take(Stat.GUARD, Aim.Self)), Limit(perRound = 2), "Limited.")
        val kick = KitDef("kick", "kick", listOf(Move("k", "Kicks", Cadence.Always, listOf(Action.Guard(Aim.Self, Amount.Fixed(1))))))
        val r = fight(listOf(member("a", kick, effects = listOf(a, b))), listOf(foe("x", stand())), rules = rules.copy(maxRounds = 2))
        assertEquals(4, r.of(EventKind.STAT_GAINED, "ping").size)
    }

    @Test fun `a definition without a limit, a description or a sane cost is refused`() {
        val bad = EffectDef("bad", "Bad", Trigger(EventKind.DAMAGE, Who.SELF_TARGET), listOf(Action.Guard(Aim.Self, Amount.OfSpent())), Limit(), "")
        val problems = EffectRules.problems(listOf(bad))
        assertTrue(problems.any { "no limit" in it } && problems.any { "no description" in it } && problems.any { "scales with a cost" in it })
        assertTrue(EffectRules.problems(listOf(EffectDef("s", "S", Trigger(EventKind.DOWNED, Who.FOE_TARGET), listOf(Action.Summon("nobody")), Limit(perRound = 1), "x"))).size == 2)
    }

    @Test fun `the launch catalog's combat content is valid and complete`() {
        assertEquals(emptyList(), combat.problems())
        assertEquals(emptyList(), LaunchContent.catalog.validate())
        assertEquals(LaunchContent.catalog.classes.map { it.id }.toSet(), combat.kitByClass.keys)
        // Every element an augment can give has a rule, so no forged blade is blank in a fight.
        assertEquals(LaunchContent.catalog.materials.mapNotNull { it.element }.toSet(), combat.elementEffects.keys)
        assertEquals(LaunchContent.catalog.materials.filter { it.category == com.tinyblacksmith.core.content.MaterialCategory.CATALYST }.map { it.id }.toSet(), combat.catalystEffects.keys)
    }

    // ---- breadth: every class with every blade the catalog can describe, against every kind of foe ----

    @Test fun `every class, family, element, affix, catalyst and signature fights to an end inside the rules`() {
        val content = LaunchContent.catalog
        val foes = listOf(listOf("ashclaw_warchief", "ashclaw_thief", "ashclaw_scout"), listOf("gravecaller", "bone_warden", "pale_knight"), listOf("ember_drake", "cinder_knight", "ember_whelp"), listOf("hollow_king"), listOf("broodmother", "ember_whelp"), listOf("warlord_krag", "ashclaw_brute"))
        val relicSets = listOf(emptyList<EffectDef>()) + combat.relicEffects.values.toList() + listOf(combat.relicEffects.values.flatten())
        var n = 0
        val fired = HashSet<String>()
        for ((ci, cls) in content.classes.withIndex()) for ((fi, family) in content.families.withIndex()) {
            val augments = content.materials.filter { it.element != null }
            val catalysts = listOf(null) + combat.catalystEffects.keys
            val affixSets = content.affixes.map { listOf(it) } + listOf(emptyList())
            for ((ai, set) in affixSets.withIndex()) {
                val augment = augments[(ai + fi) % augments.size]
                val catalyst = catalysts[(ai + ci) % catalysts.size]
                val signature = combat.signatureEffects.keys.toList().let { it[(ai + fi + ci) % it.size] }.takeIf { ai % 3 == 0 }
                val w = blade("w$n", family.id, LaunchContent.BRONZE, augment.id, catalyst, quality = 20 + (ai * 7) % 70,
                    affixes = set.filter { it.kind == com.tinyblacksmith.core.content.AffixKind.BENEFICIAL }.map { it.id }, flaws = set.filter { it.kind == com.tinyblacksmith.core.content.AffixKind.FLAW }.map { it.id }, signatureId = signature)
                val lead = fighter(hero("h1", "Lead", cls.id, level = 1 + ai % 5, health = 40 + (ai * 13) % 61), w, if ((ai + ci + fi) % 2 == 0) Loadout.Field.ROAD else Loadout.Field.HOME)
                val party = listOf(lead, fighter(CombatSandbox.mira, CombatSandbox.healingStaff), fighter(CombatSandbox.brann.copy(id = com.tinyblacksmith.core.model.HeroId("h7")), CombatSandbox.bellblade))
                val setup = FightSetup("sweep", party, enemies(*foes[n % foes.size].toTypedArray(), percent = 80 + (n * 17) % 200), relicSets[n % relicSets.size], Objective(), Posture.entries[n % 3], rules, n.toLong(), combat.unitById)
                val r = Fight.resolve(setup)
                assertTrue(r.rounds in 1..rules.maxRounds)
                assertTrue(r.actors.count { it.side == Side.PARTY } <= rules.maxActorsPerSide && r.actors.count { it.side == Side.ENEMY && !it.downed } <= rules.maxActorsPerSide)
                assertTrue(r.of(EventKind.FRACTURED).groupBy { it.source }.all { it.value.size == 1 })
                assertTrue(r.of(EventKind.SUMMONED).count { e -> r.actors.first { it.key == e.target }.side == Side.PARTY } <= 2)
                fired += r.events.mapNotNull { it.effectId }
                n += 1
            }
        }
        assertTrue(n > 500)
        // Every rule a blade or a relic can carry did something somewhere in the sweep: none is dead text.
        val silent = combat.allEffects.map { it.id }.toSet() - fired
        assertEquals(emptySet(), silent, "rules that never fired")
    }
}
