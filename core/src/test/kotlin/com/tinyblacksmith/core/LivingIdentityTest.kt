package com.tinyblacksmith.core

import com.tinyblacksmith.core.TestSupport.engine
import com.tinyblacksmith.core.TestSupport.legacyRun
import com.tinyblacksmith.core.content.CultureDef
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Compatibility
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.ResolutionContext
import com.tinyblacksmith.core.heroes.Appearance
import com.tinyblacksmith.core.heroes.Heroes
import com.tinyblacksmith.core.heroes.Identity
import com.tinyblacksmith.core.model.*
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.persistence.SaveEnvelope
import com.tinyblacksmith.core.rng.RngStream
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Living heroes, final design 6 and 7: peoples, names, kin, Hooks and the revision boundary (fixtures 13, 14, 20 of section 18.2). */
class LivingIdentityTest {
    private val content = LaunchContent.catalog

    @Test fun `the peoples are the bible's twelve, with every pool whole and the catalog valid`() {
        assertEquals(12, content.races.size)
        assertEquals(12, content.cultures.size)
        assertEquals(20, content.backgrounds.size)
        assertEquals(1200, content.cultures.sumOf { it.given.size })
        assertEquals(600, content.cultures.sumOf { it.family.size })
        assertEquals(100, content.races.sumOf { it.weight })
        assertTrue(content.validate().isEmpty(), content.validate().toString())
        // The bible's validation examples are in their pools (content bible 7).
        val examples = listOf("human" to "Maren Voss", "elf" to "Ilyra Rainbloom", "dwarf" to "Dagna Ironrivet", "halfling" to "Tansy Copperkettle", "gnome" to "Fizzi Lanternwick",
            "orc" to "Ashka Riverfang", "goblin" to "Nizz Lanternbit", "dragonkin" to "Keshra Bronzehorn", "hornborn" to "Savris Lanternhush", "stonekin" to "Karok Skyledge",
            "catfolk" to "Nauri Reedstep", "lizardfolk" to "Esra Stillreed")
        for ((c, n) in examples) content.cultureById.getValue(c).let { assertTrue(n.substringBefore(' ') in it.given && n.substringAfter(' ') in it.family, n) }
    }

    @Test fun `a new era names everyone from a culture, gives one Hook, and never two living heroes one full name`() {
        for (seed in 1L..40L) for (charter in listOf(null, GuildContent.TOWNS_LAST_HOPE)) {
            val s = engine.newRun(LegacyProfile(), seed, charterId = charter)
            assertEquals(LivingRevision.CURRENT, s.livingRevision)
            val alive = s.aliveHeroes()
            assertEquals(alive.size, alive.map { it.fullName }.toSet().size, "seed $seed")
            for (h in alive) {
                val culture = content.cultureById.getValue(h.cultureId!!)
                assertTrue(h.name in culture.given, "${h.fullName} given name from ${culture.id}")
                assertTrue(h.surname in culture.family || h.surname in content.bynames, h.fullName)
                assertNotNull(h.backgroundId); assertNotNull(h.hook)
                if (charter == null) assertTrue(h.hook!!.kind == HookKind.GRUDGE || h.hook!!.kind == HookKind.KIN, "a classic era has only the Hooks it can honour")
                // Unrelated heroes never share a surname while the pools allow; kin always do, with the family ID.
                alive.filter { it.id != h.id && it.surname == h.surname }.forEach { o -> assertTrue(o.kinId != null && o.kinId == h.kinId, "${h.fullName} and ${o.fullName}") }
            }
        }
    }

    @Test fun `kin are two living heroes of one culture, family ID and surname`() {
        val pairs = (1L..60L).flatMap { seed -> engine.newRun(LegacyProfile(), seed).aliveHeroes().filter { it.hook?.kind == HookKind.KIN }.map { seed to it } }
        assertTrue(pairs.isNotEmpty())
        for ((seed, h) in pairs) {
            val s = engine.newRun(LegacyProfile(), seed)
            val k = s.hero(h.hook!!.kinHeroId!!)
            assertEquals(h.surname, k.surname); assertEquals(h.cultureId, k.cultureId); assertEquals(h.kinId, k.kinId); assertTrue(h.name != k.name)
        }
    }

    @Test fun `a lost family weapon is a real record in Lost, never forged here and never the smith's`() {
        val hooks = (1L..40L).flatMap { seed -> engine.newRun(LegacyProfile(), seed, charterId = GuildContent.TOWNS_LAST_HOPE).let { s -> s.heroes.values.filter { it.hook?.kind == HookKind.LOST_WEAPON }.map { s to it } } }
        assertTrue(hooks.isNotEmpty())
        for ((s, h) in hooks) {
            val w = s.weapon(h.hook!!.weaponId!!)
            assertTrue(w.location is WeaponLocation.Lost && (w.location as WeaponLocation.Lost).reason.startsWith(Identity.LOST_FAMILY_PREFIX))
            assertEquals(0, w.forgedDay); assertEquals(h.hook!!.background, true)
        }
    }

    @Test fun `a hero of a people without matching art wears the neutral silhouette, and no identity is rerolled for it`() {
        for (seed in 1L..30L) for (h in engine.newRun(LegacyProfile(), seed).aliveHeroes()) {
            val key = Appearance.keyOf(h)
            if (Appearance.isSilhouette(key)) assertEquals(Appearance.silhouette(h.classId), key)
            else assertEquals(h.raceId, content.portraitRaces[key], "${h.fullName} wears a face of their people")
        }
    }

    @Test fun `a spent culture retires reservations, then falls back to a byname, and never hangs or duplicates a living name`() {
        val tiny = content.copy(cultures = content.cultures.map { CultureDef(it.id, it.name, it.given.take(2), it.family.take(2)) })
        // The catalog validator refuses such pools, so the context is built around it: this is the defect path, not content.
        val ctx = ResolutionContext(engine.newRun(LegacyProfile(), 9), tiny, engine.config)
        repeat(60) { ctx.updateHero(Heroes.generate(ctx, ctx.rng(RngStream.HEROES))) }
        val alive = ctx.aliveHeroes()
        assertEquals(alive.size, alive.map { it.fullName }.toSet().size)
        assertTrue(alive.any { it.surname in tiny.bynames }, "the byname fallback was reached")
    }

    @Test fun `the same given name in two cultures is allowed, the same living full name never`() {
        val shared = content.cultures.flatMap { c -> c.given.map { it to c.id } }.groupBy({ it.first }, { it.second }).filterValues { it.size > 1 }
        assertTrue(shared.isNotEmpty(), "the bible repeats some given names across cultures on purpose")
    }

    @Test fun `a save of schema 7 decodes as the legacy revision and keeps its old behaviour`() {
        val old = engine.legacyRun(LegacyProfile(), 31, GuildContent.TOWNS_LAST_HOPE)
        val payload = SaveCodec.json.encodeToString(GameState.serializer(), old).let { SaveCodec.json.parseToJsonElement(it).jsonObject }
            .let { o -> JsonObject(o.filterKeys { it != "livingRevision" }) }.toString()
        val text = SaveCodec.json.encodeToString(SaveEnvelope.serializer(), SaveEnvelope(7, payload))
        val decoded = SaveCodec.decodeRun(text)
        assertEquals(LivingRevision.LEGACY, decoded.livingRevision)
        assertTrue(decoded.heroes.values.all { it.cultureId == null && it.hook == null })
        val admitted = (Compatibility.admit(decoded.copy(rulesVersion = 7, contentVersion = 6), engine.content, engine.config) as Compatibility.Result.Admitted).state
        assertEquals(LivingRevision.LEGACY, admitted.livingRevision)
        assertEquals(GameEngine.RULES_VERSION, admitted.rulesVersion)
    }

    @Test fun `a legacy era names from the old pools and draws no Hook`() {
        val s = engine.legacyRun(LegacyProfile(), 4)
        for (h in s.aliveHeroes()) { assertNull(h.cultureId); assertNull(h.hook); assertTrue(h.name in content.firstNames) }
    }
}
