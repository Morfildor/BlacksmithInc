package com.example.blacksmithproject

import com.example.blacksmithproject.ui.PortraitArt
import com.example.blacksmithproject.ui.Sprites
import com.example.blacksmithproject.ui.heroUpgraded
import com.tinyblacksmith.core.model.HeroClassId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The generated portrait table (tools/pixelart/import_assets.py) against the drawables that are really in the module. */
class PortraitArtTest {
    private val classes = listOf("guardian", "ranger", "duelist", "battlemage", "warden")
    private val faceCount = mapOf("guardian" to 5, "ranger" to 4, "duelist" to 4, "battlemage" to 4, "warden" to 3)
    private fun drawable(name: String) = File("src/main/res/drawable-nodpi/$name.png")

    @Test
    fun everyClassHasItsFaces() {
        assertEquals(classes, PortraitArt.heroFaces.keys.toList())
        for (cls in classes) assertEquals("faces of $cls", faceCount[cls], PortraitArt.heroFaces.getValue(cls).size)
        val all = PortraitArt.heroFaces.values.flatten()
        assertEquals("a hero belongs to one class", 20, all.toSet().size)
        assertEquals((1..20).map { "portrait_hero_%02d".format(it) }.toSet(), all.toSet())
        assertEquals(all.toSet(), PortraitArt.heroes.keys)
        assertEquals(all.toSet(), PortraitArt.heroesUpgraded.keys)
    }

    @Test
    fun baseAndUpgradedDrawablesExistForEveryHero() {
        val seen = HashSet<Int>()
        for (cls in classes) for (key in PortraitArt.heroFaces.getValue(cls)) {
            val base = PortraitArt.heroes.getValue(key)
            val up = PortraitArt.heroesUpgraded.getValue(key)
            assertEquals(key, base.resName)
            assertEquals("${key}_up", up.resName)
            for (entry in listOf(base, up)) {
                assertTrue("missing drawable ${entry.resName}.png", drawable(entry.resName).isFile)
                assertTrue("${entry.resName} shares a drawable with another face", seen.add(entry.drawable))
            }
            assertFalse("base and upgraded face of $key are the same file", drawable(base.resName).readBytes().contentEquals(drawable(up.resName).readBytes()))
            assertEquals(base.drawable, Sprites.portrait(key, HeroClassId(cls)))
            assertEquals(up.drawable, Sprites.portrait(key, HeroClassId(cls), upgraded = true))
        }
        assertEquals(40, seen.size)
    }

    /** Every key that existed before the hero set (25 sheet-3 keys, 25 second-set keys) and hero IDs h1..h40. */
    private fun legacyKeys(cls: String) = (0..4).map { "portrait_${cls}_$it" } + (1..5).map { "portrait_v2_${cls}_$it" } + (1..40).map { "h$it" }

    @Test
    fun everyLegacyKeyResolvesToAFaceOfTheClass() {
        assertEquals(50, PortraitArt.base.size + PortraitArt.secondSet.size)
        for (key in PortraitArt.base.keys + PortraitArt.secondSet.keys) assertTrue("$key is not covered", classes.any { key in legacyKeys(it) })
        for (cls in classes) {
            val faces = PortraitArt.heroFaces.getValue(cls)
            for (key in legacyKeys(cls)) {
                val face = Sprites.heroFaceKey(key, HeroClassId(cls))
                assertTrue("$key resolves to $face, which is not a $cls", face in faces)
                assertEquals(PortraitArt.heroes.getValue(face).drawable, Sprites.portrait(key, HeroClassId(cls)))
                assertEquals(PortraitArt.heroesUpgraded.getValue(face).drawable, Sprites.portrait(key, HeroClassId(cls), upgraded = true))
            }
        }
    }

    /** The table is written out, so a change to the mapping is a failing test, not a silent change of every saved face. */
    @Test
    fun mappingIsStable() {
        fun face(key: String, cls: String) = Sprites.heroFaceKey(key, HeroClassId(cls)).takeLast(2)
        val sheet3 = mapOf(
            "guardian" to listOf("01", "06", "09", "11", "16"), "ranger" to listOf("02", "07", "12", "17", "02"),
            "duelist" to listOf("03", "10", "13", "18", "03"), "battlemage" to listOf("04", "08", "14", "19", "04"),
            "warden" to listOf("05", "15", "20", "05", "15"),
        )
        for (cls in classes) {
            assertEquals(sheet3[cls], (0..4).map { face("portrait_${cls}_$it", cls) })
            assertEquals("the second set takes the same slots", sheet3[cls], (1..5).map { face("portrait_v2_${cls}_$it", cls) })
            // A hero ID shows the face of the sheet-3 key the same hero has always had (core: the ID hash over five).
            for (n in 1..40) assertEquals(face("portrait_${cls}_${Math.floorMod("h$n".hashCode(), 5)}", cls), face("h$n", cls))
            // Spread: every face of the class is used, and no face takes more than two of the five slots.
            val spread = sheet3.getValue(cls).groupingBy { it }.eachCount()
            assertEquals(faceCount[cls], spread.size)
            assertTrue(spread.values.all { it <= 2 })
        }
        assertEquals("asking twice gives the same face", face("h17", "ranger"), face("h17", "ranger"))
        assertEquals("a key of another class still gives a face of the class asked for", "09", face("portrait_ranger_2", "guardian"))
    }

    @Test
    fun unknownKeyFallsBackToTheClass() {
        for (cls in classes) {
            val first = PortraitArt.heroes.getValue(PortraitArt.heroFaces.getValue(cls).first()).drawable
            for (key in listOf("portrait_gone_9", "", "portrait_hero_99", "hero", "h")) assertEquals(first, Sprites.portrait(key, HeroClassId(cls)))
        }
        // A hero key of another class is not a face of this one.
        assertEquals(PortraitArt.heroes.getValue("portrait_hero_01").drawable, Sprites.portrait("portrait_hero_02", HeroClassId("guardian")))
        // An unknown class gets a guardian rather than a crash.
        assertEquals(PortraitArt.heroes.getValue("portrait_hero_01").drawable, Sprites.portrait("portrait_gone_9", HeroClassId("bard")))
    }

    @Test
    fun olderSetsStayInTheRepoAndOutOfThePool() {
        assertFalse(PortraitArt.SECOND_SET_ENABLED)
        val heroDrawables = (PortraitArt.heroes.values + PortraitArt.heroesUpgraded.values).map { it.drawable }.toSet()
        for ((key, entry) in PortraitArt.base + PortraitArt.secondSet) {
            assertTrue("missing drawable ${entry.resName}.png", drawable(entry.resName).isFile)
            val cls = classes.first { "_${it}_" in key }
            assertTrue("$key must draw a hero-set face", Sprites.portrait(key, HeroClassId(cls)) in heroDrawables)
            assertNotEquals(entry.drawable, Sprites.portrait(key, HeroClassId(cls)))
        }
        // The gallery can still show a rejected tile by asking for it.
        val tile = PortraitArt.secondSet.getValue("portrait_v2_ranger_3")
        assertEquals(tile.drawable, Sprites.portrait("portrait_v2_ranger_3", HeroClassId("ranger"), secondSet = true))
    }

    @Test
    fun smallRowTilesExistOnlyWhereTheFaceIsSmall() {
        val small = (11..15).map { "portrait_hero_$it" }.toSet()
        assertEquals(small, PortraitArt.heroesSmall.keys)
        assertEquals(small, PortraitArt.heroesUpgradedSmall.keys)
        val seen = (PortraitArt.heroes.values + PortraitArt.heroesUpgraded.values).map { it.drawable }.toHashSet()
        for (cls in classes) for (key in PortraitArt.heroFaces.getValue(cls)) {
            val base = Sprites.portrait(key, HeroClassId(cls), small = true)
            val up = Sprites.portrait(key, HeroClassId(cls), upgraded = true, small = true)
            if (key in small) {
                for ((entry, suffix, id) in listOf(Triple(PortraitArt.heroesSmall.getValue(key), "_sm", base), Triple(PortraitArt.heroesUpgradedSmall.getValue(key), "_up_sm", up))) {
                    assertEquals(key + suffix, entry.resName)
                    assertEquals(entry.drawable, id)
                    assertTrue("missing drawable ${entry.resName}.png", drawable(entry.resName).isFile)
                    assertTrue("${entry.resName} shares a drawable with another face", seen.add(entry.drawable))
                }
            } else {
                assertEquals("a hero without a small tile keeps the full one", Sprites.portrait(key, HeroClassId(cls)), base)
                assertEquals(Sprites.portrait(key, HeroClassId(cls), upgraded = true), up)
            }
        }
    }

    @Test
    fun upgradedLookNeedsATitleOrEnoughVictories() {
        assertFalse(heroUpgraded(null, 0))
        assertFalse(heroUpgraded("", 4))
        assertFalse(heroUpgraded(" ", 4))
        assertTrue(heroUpgraded("Slayer of the Brute", 0))
        assertTrue(heroUpgraded(null, 5))
    }
}
