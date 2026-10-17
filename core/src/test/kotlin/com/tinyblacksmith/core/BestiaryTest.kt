package com.tinyblacksmith.core

import com.tinyblacksmith.core.combat.*
import com.tinyblacksmith.core.config.BalanceConfig
import com.tinyblacksmith.core.content.ArmyKind
import com.tinyblacksmith.core.content.Bestiary
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.guild.Loadout
import com.tinyblacksmith.core.sim.CombatSandbox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The living heroes siege bestiary (content bible 10 to 13, final design Appendix A): its counts, the one conversion of
 * the bible's numbers, the faction fallback of its elements, its tags, every army fought once, the structural units,
 * the return once, and its player text.
 */
class BestiaryTest {
    private val content = LaunchContent.catalog
    private val combat = content.combat!!
    private val guild = content.guild!!
    private val config = BalanceConfig.DEFAULT
    private val units = Bestiary.units
    private val unitById = units.associateBy { it.id }

    private fun profile(id: String) = guild.profileById.getValue(id)
    private fun standard(id: String) = id.startsWith("ib_") || id.startsWith("pp_") || id.startsWith("tc_")

    @Test fun `48 enemies and 36 armies, as the bible counts them`() {
        assertEquals(48, units.size)
        assertEquals(48, guild.bestiary.size)
        assertEquals(units.map { it.id }.toSet(), guild.bestiary.map { it.unitId }.toSet())
        assertTrue(units.all { it.id in combat.unitById }, "in the combat catalog")
        assertEquals(36, units.count { standard(it.id) })
        for (host in Bestiary.Host.values()) assertEquals(12, guild.bestiary.count { it.factionId == host.factionId && standard(it.unitId) }, "standard enemies of $host")
        assertEquals(6, units.count { it.id.startsWith("cap_") })
        assertEquals(3, units.count { it.id.startsWith("eng_") })
        assertEquals(3, units.count { it.id.startsWith("boss_") })

        val armies = guild.armies
        assertEquals(36, armies.size)
        assertEquals(mapOf(ArmyKind.STANDARD to 24, ArmyKind.CAPTAIN to 6, ArmyKind.ENGINE to 3, ArmyKind.MONSTER to 3), armies.groupingBy { it.kind }.eachCount())
        for (host in Bestiary.Host.values()) {
            assertEquals(8, armies.count { it.factionId == host.factionId && it.kind == ArmyKind.STANDARD }, "standard armies of $host")
            assertEquals(2, armies.count { it.factionId == host.factionId && it.kind == ArmyKind.CAPTAIN }, "captain armies of $host")
            assertEquals(1, armies.count { it.factionId == host.factionId && it.kind == ArmyKind.MONSTER }, "great monster of $host")
        }
        // Every enemy marches in some army.
        assertEquals(units.map { it.id }.toSet(), armies.flatMap { it.members }.toSet())
        // The kind is what its headline member is.
        for (a in armies) {
            val headline = when { a.members.any { it.startsWith("cap_") } -> ArmyKind.CAPTAIN; a.members.any { it.startsWith("eng_") } -> ArmyKind.ENGINE
                a.members.any { it.startsWith("boss_") } -> ArmyKind.MONSTER; else -> ArmyKind.STANDARD }
            assertEquals(headline, a.kind, a.id)
        }
    }

    @Test fun `the launch catalog validates with the bestiary in it`() {
        assertEquals(emptyList(), content.validate())
        assertEquals(units.size, units.map { it.name }.toSet().size, "unit names are unique")
    }

    @Test fun `the validation catches an army that breaks the bestiary's rules`() {
        val bad = guild.copy(armies = guild.armies + guild.armies.first().copy(id = "army_bad", members = listOf("ib_goblin_skirmisher", "pp_zombie", "spectral_helper"), cost = 5))
        val problems = bad.problems(content, combat)
        assertTrue(problems.any { "spectral_helper, which has no bestiary entry" in it }, "$problems")
        assertTrue(problems.any { "fields pp_zombie of hollowbound" in it }, "$problems")
    }

    private fun armorOf(id: String): Int = unitById.getValue(id).kit.passives.firstOrNull { it.id == "${id}_armor" }
        ?.actions?.filterIsInstance<Action.Guard>()?.single()?.amount?.let { (it as Amount.Fixed).value } ?: 0

    @Test fun `the bible's numbers are converted once, spot-checked against its stat register`() {
        // id to the bible's Health, Attack and Armor percent (content bible 10.1, 11, 12), then the expected live numbers.
        data class Row(val hp: Int, val atk: Int, val arm: Int, val health: Int, val attack: IntRange, val armor: Int)
        val rows = mapOf(
            "ib_goblin_skirmisher" to Row(24, 7, 0, 10, 3..3, 2),   // Appendix A: 2 Armor at the start in place of the first-hit dodge
            "ib_ogre" to Row(95, 16, 5, 38, 5..7, 1),
            "pp_wight" to Row(55, 11, 15, 22, 4..4, 3),
            "pp_black_knight" to Row(73, 12, 30, 29, 5..6, 6),
            "tc_treant" to Row(96, 12, 20, 38, 5..6, 4),
            "tc_earth_elemental" to Row(102, 13, 30, 41, 5..6, 6),
            "cap_death_knight" to Row(130, 16, 30, 52, 5..7, 6),
            "eng_battering_ram" to Row(90, 0, 20, 36, 0..0, 4),
            "boss_hill_giant" to Row(180, 22, 10, 72, 8..10, 2),
        )
        for ((id, r) in rows) {
            val u = unitById.getValue(id)
            val p = profile(id)
            assertEquals(Triple(r.hp, r.atk, r.arm), Triple(p.bibleHealth, p.bibleAttack, p.bibleArmorPercent), "$id bible numbers")
            assertEquals(r.health, u.health, "$id Health")
            assertEquals(r.attack, u.strikeMin..u.strikeMax, "$id Attack")
            assertEquals(r.armor, armorOf(id), "$id Armor")
        }
        // And the rule holds for all 48: no unit was scaled twice or tuned by hand.
        for (u in units) {
            val p = profile(u.id)
            assertEquals(Bestiary.converted(p.bibleHealth), u.health, u.id)
            assertEquals(Bestiary.attackRange(p.bibleAttack), u.strikeMin..u.strikeMax, u.id)
            if (u.id != "ib_goblin_skirmisher") assertEquals(Bestiary.armorPoints(p.bibleArmorPercent), armorOf(u.id), u.id)
        }
    }

    @Test fun `elements start from the faction and only a card's own element overrides them`() {
        for (host in Bestiary.Host.values()) {
            val faction = content.faction(host.factionId)
            assertEquals(faction.weakTo to faction.resists, host.weakTo to host.resists, "$host matches its faction")
        }
        val overrides = mapOf(
            "tc_giant_spider" to (Element.FIRE to null), "tc_treant" to (Element.FIRE to null), "cap_elder_treant" to (Element.FIRE to null),
            "pp_wraith" to (Element.SUN to Element.FROST),
            "eng_battering_ram" to (Element.FIRE to null), "eng_ballista" to (Element.FIRE to null), "eng_corpse_catapult" to (Element.FIRE to Element.GRAVE),
        )
        for (u in units) {
            val host = Bestiary.Host.values().single { it.factionId == profile(u.id).factionId }
            assertEquals(overrides[u.id] ?: (host.weakTo to host.resists), u.weakTo to u.resists, u.id)
            assertTrue(u.weakTo == null || u.weakTo != u.resists, u.id)
        }
        // Weak to Fire takes no half Burn.
        assertTrue(units.filter { it.weakTo == Element.FIRE }.none { Stat.BURN in it.tickResist })
    }

    @Test fun `captains, engines, spirits and great monsters are never returned, and structural units say so`() {
        fun tagged(tag: String) = units.filter { tag in it.tags }.map { it.id }.toSet()
        assertEquals(units.filter { it.id.startsWith("cap_") }.map { it.id }.toSet(), tagged(Tags.CAPTAIN))
        assertEquals(units.filter { it.id.startsWith("eng_") }.map { it.id }.toSet(), tagged(Tags.ENGINE))
        assertEquals(units.filter { it.id.startsWith("boss_") }.map { it.id }.toSet(), tagged(Tags.BOSS))
        assertEquals(setOf("pp_banshee", "pp_wraith"), tagged(Tags.SPIRIT))
        assertTrue(units.filter { u -> u.tags.any { it in Tags.NEVER_RETURNED } }.none { Bestiary.RAISABLE in it.tags || Bestiary.STANDARD_UNDEAD in it.tags })
        assertEquals(setOf("pp_skeleton_guard", "pp_skeleton_archer", "pp_zombie"), tagged(Bestiary.RAISABLE))

        // "structural" is exactly the units whose prepared ability lands a structural hit.
        val structural = units.filter { u -> u.kit.moves.any { m -> m.channel?.payload?.any { it is Action.Structural } == true } }.map { it.id }.toSet()
        assertEquals(setOf("ib_goblin_sapper", "ib_ogre", "eng_battering_ram", "eng_corpse_catapult", "boss_hill_giant"), structural)
        assertEquals(structural, tagged(Bestiary.STRUCTURAL))
        assertTrue(combat.units.filter { it.id !in unitById }.none { Bestiary.STRUCTURAL in it.tags }, "no older unit is structural")
        // Each channel move says what it does when it lands: a card reads the telegraph, not the payload.
        for (u in units) for (m in u.kit.moves.filter { it.channel != null }) {
            val tele = assertNotNull(m.telegraph, "${u.id} ${m.id}")
            m.channel!!.payload.filterIsInstance<Action.Structural>().forEach { s -> assertTrue("${s.amount} damage to the defences" in tele, "${u.id}: $tele") }
        }
    }

    // ---- fights ----

    private val rules = config.guild.fight
    private val wait = Move("wait", "Waits", Cadence.Always, emptyList())
    private fun member(key: String, vararg moves: Move, health: Int = 1000) =
        Combatant(key, key, Side.PARTY, KitDef("kit_$key", "Kit", moves.toList() + wait), health, health, 5, 5)
    private fun foes(vararg ids: String, percent: Int = 100) = ids.mapIndexed { i, id -> Loadout.enemy(combat.unit(id), "e${i + 1}", percent) }
    private fun fight(party: List<Combatant>, enemies: List<Combatant>, seed: Long = 1) =
        Fight.resolve(FightSetup("bestiary", party, enemies, posture = Posture.RECKLESS, rules = rules, seed = seed, units = combat.unitById))
    private fun kill(cadence: Cadence, aim: Aim = Aim.FrontFoe) = Move("kill", "Kill", cadence, listOf(Action.Damage(aim, Amount.Fixed(500), pierce = true)))

    @Test fun `every army fights a fixed three-member party to an end`() {
        val s = CombatSandbox
        val party = listOf(s.fighter(s.brann, s.heavyAxe, Loadout.Field.HOME), s.fighter(s.mira, s.healingStaff, Loadout.Field.HOME), s.fighter(s.oda, s.frostBow, Loadout.Field.HOME))
        for (a in guild.armies) for (percent in listOf(100, config.guild.siegePercentBase)) for (seed in 1L..3L) {
            val r = Fight.resolve(FightSetup("${a.id} at $percent%", party, foes(*a.members.toTypedArray(), percent = percent), posture = Posture.BALANCED, rules = rules, seed = seed, units = combat.unitById))
            assertTrue(r.rounds in 1..rules.maxRounds, "${a.id}: ${r.rounds} rounds")
            assertTrue(r.events.last().kind in setOf(EventKind.OBJECTIVE, EventKind.RETREATED), a.id)
            assertNotNull(FightReplay.of(r), "${a.id} replays")
            assertNotNull(FightAccounting.of(r), "${a.id} accounts")
            assertTrue(r.actors.count { it.side == Side.ENEMY } <= rules.maxActorsPerSide, a.id)
        }
    }

    @Test fun `each structural unit lands its bible number on the defences when nobody stops it`() {
        val expected = mapOf("ib_goblin_sapper" to 4, "ib_ogre" to 5, "eng_battering_ram" to 12, "eng_corpse_catapult" to 3, "boss_hill_giant" to 6)
        for ((id, n) in expected) {
            val r = fight(listOf(member("a")), foes(id))
            assertEquals(n, r.structural, id)
        }
        // The ram's three strokes, then it stands neutralized and the field is won.
        val ram = fight(listOf(member("a")), foes("eng_battering_ram"))
        assertEquals(listOf(4, 4, 4), ram.events.filter { it.kind == EventKind.STRUCTURAL_HIT }.map { it.amount })
        assertEquals(FightOutcome.WON, ram.outcome)
        assertTrue(ram.actor("e1")!!.neutralized)
        // An interrupted sapper lands nothing, for good.
        val stopped = fight(listOf(member("a", Move("stop", "Stop", Cadence.Actions(3), listOf(Action.Interrupt(Aim.Foe))))), foes("ib_goblin_sapper"))
        assertEquals(0, stopped.structural)
        assertEquals(1, stopped.events.count { it.kind == EventKind.INTERRUPTED })
    }

    @Test fun `the necromancer returns one skeleton once and never raises a captain`() {
        // Two skeletons fall in rounds 1 and 2, the returned one again in round 5: one return in all.
        val r = fight(listOf(member("a", kill(Cadence.Actions(1, 2, 5)))), foes("pp_skeleton_guard", "pp_skeleton_guard", "pp_necromancer"))
        val back = r.events.filter { it.kind == EventKind.RETURNED }
        assertEquals(1, back.size)
        assertEquals("e1", back.single().target, "the first to fall")
        assertEquals(Fight.pct(Bestiary.converted(30), 35), back.single().amount, "35% of its Health")
        assertTrue(r.events.none { it.kind == EventKind.ACTION && it.source == "e1" && it.round == back.single().round }, "it waits for the next round")
        assertEquals(2, r.events.count { it.kind == EventKind.DOWNED && it.target == "e1" })

        // A fallen captain is never raised: the necromancer has nobody to prepare for.
        val captain = fight(listOf(member("a", kill(Cadence.Actions(1)))), foes("cap_death_knight", "pp_necromancer"))
        assertTrue(captain.events.any { it.kind == EventKind.DOWNED && it.target == "e1" })
        assertTrue(captain.events.none { it.kind == EventKind.CHANNEL_STARTED || it.kind == EventKind.RETURNED })
        // Nor does the Bellkeeper return a spirit.
        val spirit = fight(listOf(member("a", kill(Cadence.Actions(1)))), foes("pp_banshee", "cap_lich"))
        assertTrue(spirit.events.none { it.kind == EventKind.RETURNED })
        // It does return a standard undead, at 40%.
        val toll = fight(listOf(member("a", kill(Cadence.Actions(1)))), foes("pp_wight", "cap_lich"))
        assertEquals(listOf("e1"), toll.events.filter { it.kind == EventKind.RETURNED }.map { it.target })
    }

    @Test fun `the named rules the main session reads`() {
        assertEquals(Recovery(2, setOf("metal:" + LaunchContent.SILVER.value)), unitById.getValue("tc_werewolf").recover)
        assertEquals(Recovery(2), unitById.getValue("ib_troll").recover)
        assertTrue(unitById.getValue("tc_fire_elemental").kit.moves.all { m -> m.actions.filterIsInstance<Action.Damage>().all { it.element == Element.FIRE } })
        assertTrue(Tags.CAPTAIN in unitById.getValue("cap_death_knight").tags)
        for (u in units.filter { Tags.CAPTAIN in it.tags }) assertTrue(u.kit.moves.filter { it.channel != null }.all { it.telegraph != null }, u.id)
        // The Matriarch hunts with the wolves of her army.
        assertTrue(Tags.PACK in unitById.getValue("tc_dire_wolf").tags)
        val hunt = fight(listOf(member("g"), member("w", health = 100)), foes("cap_beast_matriarch", "tc_dire_wolf"))
        val prey = hunt.events.first { it.kind == EventKind.PREY_MARKED }
        assertTrue(hunt.events.filter { it.round == prey.round && it.source == "e2" && it.kind == EventKind.DAMAGE }.all { it.target == prey.target })
    }

    // ---- words ----

    private fun texts(): List<Pair<String, String>> = buildList {
        for (u in units) {
            add(u.id to u.name)
            for (m in u.kit.moves) { add(u.id to m.name); m.telegraph?.let { add(u.id to it) }; m.channel?.let { add(u.id to it.name); add(u.id to it.fallbackName) } }
            for (e in u.kit.passives + u.effects) { add(u.id to e.name); add(u.id to e.description) }
        }
        for (p in guild.bestiary) listOf(p.host, p.job, p.counter, p.forecast, p.adaptation).filter { it.isNotEmpty() }.forEach { add(p.unitId to it) }
        for (a in guild.armies) { add(a.id to a.forecast); a.counters.forEach { add(a.id to it) } }
    }

    @Test fun `every bestiary string follows the Writing Guide`() {
        val names = guild.armies.map { it.name }.toSet() + units.map { it.name }
        // Words the glossary retires, as whole words. "Slow" is the status (a sentence may still say "slowly").
        val retired = listOf(Regex("""\bblades?\b""", RegexOption.IGNORE_CASE), Regex("""\btaunts?\b""", RegexOption.IGNORE_CASE), Regex("""\bexposed?\b""", RegexOption.IGNORE_CASE),
            Regex("""\bsilence[ds]?\b""", RegexOption.IGNORE_CASE), Regex("""\bSlow(ed)?\b"""), Regex("""\bslowed\b"""), Regex("""\binitiative\b""", RegexOption.IGNORE_CASE),
            Regex("""\barmou?r(ed)? percent""", RegexOption.IGNORE_CASE), Regex("""\barmour""", RegexOption.IGNORE_CASE), Regex("""\bGuard\b"""), Regex("""\bcleanse""", RegexOption.IGNORE_CASE),
            Regex("""\bHP\b"""), Regex("""\bMortal Wound\b"""), Regex("""\bWeakened\b"""))
        val all = texts() + guild.armies.map { it.id to it.name }
        val bad = all.filter { (_, t) -> '—' in t || '–' in t || ';' in t }.map { "${it.first}: ${it.second}" } +
            texts().filter { (_, t) -> t !in names && retired.any { it.containsMatchIn(t) } }.map { "${it.first}: ${it.second}" }
        assertEquals(emptyList(), bad)
        // Every sentence of a rule ends as a sentence.
        assertTrue(texts().filter { (_, t) -> t !in names && t != "Attack" && t != "Grave attack" }.all { (_, t) -> t.isEmpty() || t.last() in ".!" || t.first().isUpperCase() && t.split(" ").size <= 4 },
            texts().filter { (_, t) -> t !in names && t.split(" ").size > 4 && t.last() !in ".!" }.toString())
    }
}
