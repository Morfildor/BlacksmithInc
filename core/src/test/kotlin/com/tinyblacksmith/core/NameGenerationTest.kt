package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.engine.WorldEvents
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Rng
import com.tinyblacksmith.core.rng.RngStream
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import com.tinyblacksmith.core.sim.Simulator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Plan 4.4 (N03) and the identity rule of 6.6 (X07): names do not repeat, and nothing is ever matched by a name. */
class NameGenerationTest {
    private val content = engine.content
    private val guardian = content.classes.first().id

    private fun ctx(state: GameState, catalog: ContentCatalog = content) = ResolutionContext(state, catalog, engine.config)

    /** The states after each End Day of one run, the new run first. */
    private fun days(seed: Long, legacy: LegacyProfile = LegacyProfile(), maxDays: Int = 60, policy: Policy = Policy.BALANCED_FAIR, e: GameEngine = engine): List<GameState> {
        val out = arrayListOf(e.newRun(legacy, seed))
        SimulationDriver(e, maxDays = maxDays, onDayResolved = { s, _ -> out += s }).playRun(legacy, seed, policy)
        return out
    }

    private fun anchor(era: Int, name: String, heroId: String) = LineageAnchor(era, name, name.substringAfter(' '), guardian, 5, "died on day 9", id = "era$era-$heroId")

    private fun assertNoSharedNames(s: GameState, where: String) {
        val alive = s.aliveHeroes()
        assertEquals(alive.size, alive.map { it.name }.toSet().size, "$where: a first name is shared among ${alive.map { it.fullName }}")
        for (group in alive.groupBy { it.surname }.values.filter { it.size > 1 })
            assertEquals(1, group.map { it.lineageId ?: it.id.value }.toSet().size, "$where: a surname is shared outside one lineage: ${group.map { it.fullName }}")
    }

    @Test
    fun noLivingPairSharesAFirstNameOrSurnameOutsideOneLineage() {
        var heroDays = 0
        for (seed in 1L..1000L) for (s in days(seed)) { assertNoSharedNames(s, "seed $seed day ${s.day}"); heroDays += s.aliveHeroes().size }
        assertTrue(heroDays > 50_000, "the runs were played: $heroDays hero-days")
        // An account with history: two lineages, one of them a family that was famous twice.
        val storied = LegacyProfile(lineages = listOf(anchor(1, "Mira Vance", "h3"), anchor(2, "Bram Ferris", "h5")), eras = listOf(EraSummary(1, 12, 8, "The forge fell."), EraSummary(2, 12, 8, "The forge fell.")))
        for (seed in 1L..100L) for (s in days(seed, storied)) assertNoSharedNames(s, "storied seed $seed day ${s.day}")
    }

    @Test
    fun noFullNameRepeatsInARun() {
        for (seed in 1L..300L) {
            val heroes = days(seed).last().heroes.values
            assertEquals(heroes.size, heroes.map { it.fullName }.toSet().size, "seed $seed")
        }
    }

    /** A run to its end, and a forced-survival run of 400 days, never reach the fallback: every first name in them is new. */
    @Test
    fun aFullRunNeverExhaustsTheNames() {
        for (policy in listOf(Policy.BALANCED_FAIR, Policy.EXPERT_ACTIVE)) for (seed in 1L..100L) {
            val heroes = SimulationDriver(engine).playRun(LegacyProfile(), seed, policy).second.heroes.values
            assertEquals(heroes.size, heroes.map { it.name }.toSet().size, "$policy seed $seed: a first name came round again")
        }
        val forced = GameEngine(config = Simulator.forcedSurvival())
        val long = days(7, maxDays = 400, e = forced)
        val heroes = long.last().heroes.values
        assertTrue(long.size > 300, "the forced run lasted: ${long.size} days")
        assertTrue(heroes.size in 20..content.firstNames.size, "heroes met in ${long.size} days: ${heroes.size}")
        assertEquals(heroes.size, heroes.map { it.name }.toSet().size)
        for (s in long) assertNoSharedNames(s, "forced day ${s.day}")
    }

    @Test
    fun namesAreTheSameForTheSameSeed() {
        fun names(seed: Long) = days(seed, maxDays = 30).last().heroes.values.map { it.id.value to it.fullName }
        assertEquals(names(11), names(11))
        assertNotEquals(names(11).map { it.second }, names(12).map { it.second })
    }

    @Test
    fun descendantsCarryTheSurnameAndADifferentFirstName() {
        val a = anchor(1, "Mira Vance", "h3")
        for (seed in 1L..200L) {
            val s = engine.newRun(LegacyProfile(lineages = listOf(a), eras = listOf(EraSummary(1, 12, 8, "The forge fell."))), seed)
            val d = s.heroes.values.single { it.lineageId == a.id }
            assertEquals("Vance", d.surname)
            assertNotEquals("Mira", d.name)
            assertEquals(a.heroName, d.descendantOf)
            assertTrue(s.heroes.values.none { it.id != d.id && it.surname == "Vance" }, "seed $seed: the lineage's surname is reserved for its own")
            assertNoSharedNames(s, "seed $seed")
        }
    }

    /** X07: two fallen heroes of one surname, even of one full name, are two lineages; each gets its descendant. */
    @Test
    fun descendantsAreMatchedById() {
        fun eligible(s: GameState) = WorldEvents.canFire(ctx(s), WorldEvents.byId("descendant"))
        fun fire(s: GameState) = ctx(s).also { WorldEvents.fire(it, WorldEvents.byId("descendant")) }.toState()
        for (second in listOf("Mira Vance", "Corvin Vance")) {
            val first = anchor(1, "Mira Vance", "h3")
            val later = anchor(2, second, "h3")
            val legacy = LegacyProfile(lineages = listOf(first, later), eras = listOf(EraSummary(1, 12, 8, "The forge fell."), EraSummary(2, 9, 6, "The forge fell.")))
            val s0 = engine.newRun(legacy, 5)
            assertEquals(later.id, s0.heroes.values.single { it.lineageId != null }.lineageId, "the run opens with a descendant of the latest lineage")
            assertTrue(s0.events.any { it.type == EventType.HERO_ARRIVED && "descendant of $second" in it.text })
            assertTrue(eligible(s0), "$second: the older lineage of the same surname is still unclaimed")
            val s1 = fire(s0)
            val kin = s1.heroes.values.filter { it.lineageId != null }
            assertEquals(setOf(first.id, later.id), kin.map { it.lineageId }.toSet())
            assertTrue(kin.all { it.surname == "Vance" && it.name != it.descendantOf!!.substringBefore(' ') } && kin.map { it.name }.toSet().size == 2)
            assertFalse(eligible(s1), "no unclaimed lineage remains")
            assertEquals(emptyList(), com.tinyblacksmith.core.engine.Invariants.check(s1, engine.config))
        }
        // The anchor a run founds carries the era and the hero's ID, and the next run finds its descendant by it.
        var s = engine.newRun(LegacyProfile(), 600)
        s = s.copy(town = s.town.copy(integrity = 1), heroes = s.heroes.mapValues { (_, h) -> h.copy(fame = 3) })
        while (!s.isEnded) s = with(TestSupport) { s.endDayAccepted().state }
        val template = s.heroes.values.first()
        val twelve = (1..12).associate { HeroId("h$it") to template.copy(id = HeroId("h$it"), name = content.firstNames[it], fame = 3) }
        val founded = engine.closeRun(s.copy(heroes = twelve)).lineage!!
        assertEquals("era1-h12", founded.id, "of the equally famous the latest arrival founds the line: h12, where text order said h9")
        assertEquals(twelve.getValue(HeroId("h12")).fullName, founded.heroName)
        val heir = engine.newRun(LegacyProfile(lineages = listOf(founded), eras = listOf(EraSummary(1, s.day, 8, "The forge fell."))), 601).heroes.values.single { it.lineageId == founded.id }
        assertEquals(listOf(founded.surname, founded.heroName), listOf(heir.surname, heir.descendantOf))
    }

    /** One draw for the class, one per name, then the rest as before: the pools change only what the draws pick from. */
    @Test
    fun sameDrawCountAsToday() {
        val state = engine.newRun(LegacyProfile(), 3)
        val old = content.copy(firstNames = listOf("Mira", "Aldric", "Tessa"), surnames = listOf("Vance", "Ferris"))  // a pool this small is soon spent
        val a = anchor(1, "Mira Vance", "h3")
        for (ancestor in listOf(null, a)) {
            val plain = Rng(99).also { r ->
                if (ancestor == null) r.pick(content.classes)
                r.nextLong()                              // first name
                if (ancestor == null) r.nextLong()        // surname; a descendant's is forced
            }
            val rngs = listOf(content, old).map { catalog -> Rng(99).also { Heroes.generate(ctx(state, catalog), it, ancestor) } }
            val heroes = listOf(content, old).map { catalog -> Heroes.generate(ctx(state, catalog), Rng(99), ancestor) }
            assertEquals(rngs[0].state, rngs[1].state, "the pool size does not change the draw count")
            assertEquals(heroes[0].copy(name = "", surname = ""), heroes[1].copy(name = "", surname = ""), "everything drawn after the names is the same hero")
            // What follows the names in today's order: trait count and so on. The stream stands where the plain draws left it.
            val after = Rng(99).also { r ->
                if (ancestor == null) r.pick(content.classes)
                com.tinyblacksmith.core.heroes.Names.first(ctx(state), r, ancestor)
                if (ancestor == null) com.tinyblacksmith.core.heroes.Names.surname(ctx(state), r, "Mira")
            }
            assertEquals(plain.state, after.state)
        }
        // And a whole day: End Day leaves the HEROES and EVENTS streams where a catalog with other names leaves them.
        val other = GameEngine(content = content.copy(firstNames = content.firstNames.reversed(), surnames = content.surnames.reversed()))
        for (seed in 1L..20L) {
            val x = days(seed, maxDays = 25).last()
            val y = days(seed, maxDays = 25, e = other).last()
            assertEquals(x.rng, y.rng, "seed $seed")
            assertEquals(x.heroes.mapValues { it.value.copy(name = "", surname = "", mentorName = null) }, y.heroes.mapValues { it.value.copy(name = "", surname = "", mentorName = null) }, "seed $seed")
        }
    }

    @Test
    fun poolExhaustionStillConsumesItsDrawAndReusesTheLongestDeadName() {
        val small = content.copy(firstNames = listOf("Mira", "Aldric", "Tessa", "Bram"), surnames = listOf("Vance", "Ferris", "Marrow", "Quill"))
        fun hero(n: Int, name: String, surname: String, died: Int?) = Hero(
            HeroId("h$n"), name, surname, guardian, 1, 0, 50, 100, emptyList(), null,
            fate = if (died == null) HeroFate.ALIVE else HeroFate.DEAD, diedOnDay = died,
        )
        val base = engine.newRun(LegacyProfile(), 1)
        // Mira lives; Aldric fell on day 3 and again on day 9; Tessa fell on day 5; Bram on day 5.
        val cast = listOf(hero(1, "Mira", "Vance", null), hero(2, "Aldric", "Ferris", 3), hero(3, "Tessa", "Marrow", 5), hero(4, "Bram", "Quill", 5), hero(5, "Aldric", "Quill", 9))
        val state = base.copy(day = 12, heroes = cast.associateBy { it.id }, nextHeroSerial = 6, town = base.town.copy(championIds = emptyList()))
        val seen = HashSet<String>()
        for (seed in 1L..200L) {
            val rng = Rng(seed)
            val h = Heroes.generate(ctx(state, small), rng)
            seen += h.name
            assertTrue(h.name in setOf("Tessa", "Bram"), "the names of the heroes dead longest, never a living hero's and not the one who fell last: ${h.name}")
            assertTrue(h.surname != "Vance", "a living hero's surname is not shared: ${h.fullName}")
            assertTrue(cast.none { it.fullName == h.fullName }, "a full name is not given twice: ${h.fullName}")
            // The same draws as an unspent pool makes.
            val fresh = Rng(seed).also { Heroes.generate(ctx(state.copy(heroes = emptyMap()), small), it) }
            assertEquals(fresh.state, rng.state, "seed $seed: the spent pool drew as often as the fresh one")
        }
        assertEquals(setOf("Tessa", "Bram"), seen, "both names of the longest dead come up")
        // When the living hold every name, one of theirs is shared and the draw is still made.
        val crowded = state.copy(heroes = listOf(hero(1, "Mira", "Vance", null), hero(2, "Aldric", "Ferris", null), hero(3, "Tessa", "Marrow", null), hero(4, "Bram", "Quill", null)).associateBy { it.id })
        val rng = Rng(4)
        val h = Heroes.generate(ctx(crowded, small), rng)
        assertTrue(h.name in small.firstNames && h.surname in small.surnames && crowded.heroes.values.none { it.fullName == h.fullName })
        assertEquals(Rng(4).also { Heroes.generate(ctx(crowded.copy(heroes = emptyMap()), small), it) }.state, rng.state)
    }

    /** A hero stored before the update keeps the name it was given, though the name has left the pool. */
    @Test
    fun namesThatLeftThePoolStayOnTheHeroesWhoCarryThem() {
        for (gone in listOf("Ashwood", "Brackenridge", "Holloway", "Mossgrave", "Rooksbane")) assertFalse(gone in content.surnames, gone)
        assertFalse("Nessa" in content.firstNames)
        val s = engine.newRun(LegacyProfile(), 2)
        val first = s.aliveHeroes().first()
        val old = s.copy(heroes = s.heroes + (first.id to first.copy(name = "Nessa", surname = "Ashwood")))
        var next = old
        repeat(5) { next = with(TestSupport) { next.endDayAccepted().state } }
        assertEquals("Nessa Ashwood", next.hero(first.id).fullName)
        assertTrue(next.heroes.values.count { it.surname == "Ashwood" } == 1 && next.heroes.values.count { it.name == "Nessa" } == 1)
    }

    @Test
    fun theStreamSetIsUnchanged() {
        assertEquals(listOf("CRAFTING", "HEROES", "PURCHASES", "COMBAT", "FACTIONS", "EVENTS", "LEGACY", "WORLD", "ENCOUNTERS"), RngStream.entries.map { it.name })
    }
}
