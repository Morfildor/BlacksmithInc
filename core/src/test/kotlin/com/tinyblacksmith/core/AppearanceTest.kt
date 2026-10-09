package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.endDayAccepted
import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.content.ContentCatalog
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.heroes.Appearance
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.legacy.LegacyOutcome
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.rng.Rng
import com.tinyblacksmith.core.sim.Policy
import com.tinyblacksmith.core.sim.SimulationDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Plan 6.6 (N04): every hero stores a face of their class, spread evenly, drawn without gameplay RNG. */
class AppearanceTest {
    private val content = engine.content
    private fun keys(cls: HeroClassId) = content.heroClass(cls).appearances
    private fun ctx(state: GameState, catalog: ContentCatalog = content) = ResolutionContext(state, catalog, engine.config)
    private fun anchor(cls: HeroClassId, era: Int = 1, appearance: String? = null) =
        LineageAnchor(era, "Mira Vance", "Vance", cls, 5, "died on day 9", id = "era$era-h3", appearance = appearance)

    private fun days(seed: Long, maxDays: Int = 60, e: GameEngine = engine): List<GameState> {
        val out = arrayListOf(e.newRun(LegacyProfile(), seed))
        SimulationDriver(e, maxDays = maxDays, onDayResolved = { s, _ -> out += s }).playRun(LegacyProfile(), seed, Policy.BALANCED_FAIR)
        return out
    }

    /** The table of the faces heroes showed before the field existed, written out: changing it changes every old save. */
    private val legacyFaces = mapOf(
        "guardian" to listOf("01", "06", "09", "11", "16"), "ranger" to listOf("02", "07", "12", "17", "02"),
        "duelist" to listOf("03", "10", "13", "18", "03"), "battlemage" to listOf("04", "08", "14", "19", "04"),
        "warden" to listOf("05", "15", "20", "05", "15"),
    )

    @Test
    fun legacyKeysArePinned() {
        val slots = "3401234011234012340234012340134012340124"  // floorMod("h<N>".hashCode(), 5) for h1..h40
        assertEquals(legacyFaces.keys, content.classes.map { it.id.value }.toSet())
        for (cls in content.classes) {
            for (n in 1..40) {
                val slot = slots[n - 1].digitToInt()
                assertEquals(slot, Appearance.legacySlot("h$n"))
                val face = "portrait_hero_" + legacyFaces.getValue(cls.id.value)[slot]
                assertEquals(face, Appearance.legacyKey(cls.id, "h$n"), "${cls.id.value} h$n")
                assertEquals(face, Appearance.legacyFace(cls.id, slot))
            }
            // Every face a hero could show before the field existed is still a face of the class.
            assertTrue(keys(cls.id).containsAll(legacyFaces.getValue(cls.id.value).map { "portrait_hero_$it" }), cls.id.value)
            assertNull(Appearance.legacyFace(cls.id, 5))
        }
        val hero = engine.newRun(LegacyProfile(), 1).heroes.values.first()
        assertEquals(hero.appearance, Appearance.keyOf(hero))
        assertEquals(Appearance.legacyKey(hero.classId, hero.id.value), Appearance.keyOf(hero.copy(appearance = null)))
    }

    @Test
    fun contentListsTheFacesOfEachClass() {
        assertEquals(mapOf("guardian" to 5, "ranger" to 4, "duelist" to 4, "battlemage" to 4, "warden" to 3), content.classes.associate { it.id.value to it.appearances.size })
        val first = content.classes.first()
        assertTrue(content.copy(classes = listOf(first.copy(appearances = emptyList())) + content.classes.drop(1)).validate().any { "no appearance" in it })
        assertTrue(content.copy(classes = listOf(first.copy(appearances = content.classes[1].appearances)) + content.classes.drop(1)).validate().any { "Duplicate appearance" in it })
        // A sixth face is a content-only change.
        assertEquals(emptyList(), content.copy(classes = content.classes.map { it.copy(appearances = it.appearances + "portrait_new_${it.id.value}") }).validate())
    }

    @Test
    fun newHeroesNeverShareAFaceWhileAClassHasNoMoreLivingThanFaces() {
        // A new run: nobody has died, so every class is spread as evenly as its faces allow.
        for (seed in 1L..1000L) {
            val s = engine.newRun(LegacyProfile(), seed)
            for ((cls, heroes) in s.aliveHeroes().groupBy { it.classId }) {
                val worn = heroes.groupingBy { it.appearance!! }.eachCount()
                assertTrue(worn.keys.all { it in keys(cls) }, "seed $seed")
                if (heroes.size <= keys(cls).size) assertEquals(heroes.size, worn.size, "seed $seed ${cls.value}: ${heroes.map { it.appearance }}")
                else assertTrue(worn.size == keys(cls).size && worn.values.max() - worn.values.min() <= 1, "seed $seed ${cls.value}: $worn")
            }
        }
        // Arrivals and deaths in any order: a newcomer always takes a face that the fewest living of the class wear, so one
        // nobody wears whenever there is one. (Deaths can leave two alike in a class that is no longer full; nobody is repainted.)
        for (seed in 1L..200L) {
            val c = ctx(engine.newRun(LegacyProfile(), seed))
            val rng = Rng(seed)
            repeat(80) { step ->
                if (rng.chance(0.4)) c.aliveHeroes().takeIf { it.isNotEmpty() }?.let { c.updateHero(rng.pick(it).copy(fate = HeroFate.DEAD)) }
                val before = c.aliveHeroes()
                val h = Heroes.generate(c, rng).also { c.updateHero(it) }
                val worn = before.filter { it.classId == h.classId }.groupingBy { it.appearance!! }.eachCount()
                assertEquals(keys(h.classId).minOf { worn[it] ?: 0 }, worn[h.appearance!!] ?: 0, "seed $seed step $step: ${h.appearance} among $worn")
            }
        }
        // Whole runs: a face is of the hero's class and never changes, in life or after.
        for (seed in 1L..100L) {
            val seen = HashMap<HeroId, String>()
            for (s in days(seed)) for (h in s.heroes.values) {
                assertTrue(h.appearance in keys(h.classId), "seed $seed day ${s.day} ${h.id.value}")
                assertEquals(seen.getOrPut(h.id) { h.appearance!! }, h.appearance, "seed $seed day ${s.day} ${h.id.value}")
            }
        }
    }

    @Test
    fun aPreUpdateHeroBlocksTheFaceThePlayerSeesOnThem() {
        for (seed in 1L..50L) {
            // A save from before the field: nobody has a stored face, each shows the one their ID always gave them.
            val old = engine.newRun(LegacyProfile(), seed).let { s -> s.copy(heroes = s.heroes.mapValues { it.value.copy(appearance = null) }) }
            for (cls in content.classes) {
                val c = ctx(old)
                val shown = old.aliveHeroes().filter { it.classId == cls.id }.map { Appearance.legacyKey(it) }.toMutableSet()
                while (shown.size < cls.appearances.size) {
                    val h = Heroes.generate(c, Rng(seed), anchor(cls.id)).also { c.updateHero(it) }
                    assertEquals(cls.id, h.classId)
                    assertTrue(shown.add(h.appearance!!), "seed $seed ${cls.id.value}: ${h.appearance} is already on somebody in $shown")
                }
            }
        }
    }

    @Test
    fun assignmentLeavesEveryRngStreamUnchanged() {
        // Other faces, the same heroes: the stream stands where it stood, and only the face differs.
        val other = content.copy(classes = content.classes.map { it.copy(appearances = it.appearances.reversed() + "portrait_new_${it.id.value}") })
        val state = engine.newRun(LegacyProfile(), 3)
        for (ancestor in listOf(null, anchor(content.classes.first().id, appearance = "portrait_hero_09"))) {
            val rngs = listOf(content, other).map { catalog -> Rng(99).also { Heroes.generate(ctx(state, catalog), it, ancestor) } }
            val heroes = listOf(content, other).map { catalog -> Heroes.generate(ctx(state, catalog), Rng(99), ancestor) }
            assertEquals(rngs[0].state, rngs[1].state)
            assertEquals(heroes[0].copy(appearance = null), heroes[1].copy(appearance = null))
        }
        val e = GameEngine(content = other)
        for (seed in 1L..20L) {
            val x = days(seed, maxDays = 25).last()
            val y = days(seed, maxDays = 25, e = e).last()
            assertEquals(x.rng, y.rng, "seed $seed")
            assertEquals(x.heroes.mapValues { it.value.copy(appearance = null) }, y.heroes.mapValues { it.value.copy(appearance = null) }, "seed $seed")
            assertEquals(x.events, y.events, "seed $seed")
            assertEquals(x.weapons, y.weapons, "seed $seed")
            assertEquals(x.gold to x.town, y.gold to y.town, "seed $seed")
        }
        // The rule itself is a function of its arguments.
        val guardian = keys(content.classes.first().id)
        assertEquals(Appearance.assign(7, HeroId("h9"), guardian, listOf(guardian[0])), Appearance.assign(7, HeroId("h9"), guardian, listOf(guardian[0])))
        assertEquals(guardian[3], Appearance.assign(7, HeroId("h9"), guardian, guardian - guardian[3]))
        assertEquals(guardian[1], Appearance.assign(7, HeroId("h9"), guardian, guardian + (guardian - guardian[1])), "the least worn of a full class")
        assertEquals(guardian.toSet(), (1..200).map { Appearance.assign(7, HeroId("h$it"), guardian, emptyList()) }.toSet(), "ties are spread by the hero ID")
    }

    @Test
    fun aDescendantTakesTheAncestorsFaceWhenNobodyLivingWearsIt() {
        var s = engine.newRun(LegacyProfile(), 600)
        s = s.copy(town = s.town.copy(integrity = 1), heroes = s.heroes.mapValues { (_, h) -> h.copy(fame = 3) })
        while (!s.isEnded) s = s.endDayAccepted().state
        val end = engine.closeRun(s)
        val founder = s.heroes.getValue(HeroId(end.lineage!!.id.substringAfter('-')))
        assertEquals(founder.appearance, assertNotNull(end.lineage.appearance))
        val legacy = (engine.claimLegacy(LegacyProfile(), end) as LegacyOutcome.Updated).legacy
        for (seed in 601L..620L) assertEquals(founder.appearance, engine.newRun(legacy, seed).heroes.values.first { it.lineageId == end.lineage.id }.appearance)
        // Somebody living already wears it: the descendant takes a free face of the class.
        val cls = content.classes.first().id
        val c = ctx(engine.newRun(LegacyProfile(), 5))
        val worn = c.aliveHeroes().filter { it.classId == cls }.map { it.appearance!! }
        assertTrue(worn.isNotEmpty() && worn.size < keys(cls).size)
        val h = Heroes.generate(c, Rng(1), anchor(cls, appearance = worn.first()))
        assertTrue(h.appearance in keys(cls) && h.appearance !in worn)
    }

    @Test
    fun aMissingAssetFallsBackToTheClass() {
        val cls = content.classes.last()
        val state = engine.newRun(LegacyProfile(), 5)
        // A face that has left the content: the hero keeps the key (the app draws a face of the class for it) and blocks nothing.
        val gone = state.copy(heroes = state.heroes.mapValues { (_, h) -> if (h.classId == cls.id) h.copy(appearance = "portrait_gone_9") else h })
        assertEquals("portrait_gone_9", Appearance.keyOf(gone.aliveHeroes().first { it.classId == cls.id }))
        val c = ctx(gone)
        val taken = cls.appearances.map { Heroes.generate(c, Rng(1), anchor(cls.id, appearance = "portrait_gone_9")).also { h -> c.updateHero(h) }.appearance }
        assertEquals(cls.appearances.toSet(), taken.toSet())
        // A class the old table never knew keeps the old slot key, which the app also maps to a face of the class.
        assertEquals("portrait_bard_${Appearance.legacySlot("h7")}", Appearance.legacyKey(HeroClassId("bard"), "h7"))
    }
}
