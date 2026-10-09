package com.example.blacksmithproject

import com.example.blacksmithproject.ui.PortraitArt
import com.example.blacksmithproject.ui.Sprites
import com.tinyblacksmith.core.model.HeroClassId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The generated portrait table (tools/pixelart/import_assets.py) against the drawables that are really in the module. */
class PortraitArtTest {
    private val classes = listOf("guardian", "ranger", "duelist", "battlemage", "warden")
    private fun drawable(name: String) = File("src/main/res/drawable-nodpi/$name.png")

    @Test
    fun everyLegacyKeyResolves() {
        val seen = HashSet<Int>()
        for (cls in classes) for (i in 0..4) {
            val key = "portrait_${cls}_$i"
            val entry = PortraitArt[key]
            assertNotNull("no entry for $key", entry)
            assertEquals("a legacy key names its own drawable", key, entry!!.resName)
            assertTrue("missing drawable ${entry.resName}.png", drawable(entry.resName).isFile)
            assertTrue("$key shares a drawable with another key", seen.add(entry.drawable))
            assertEquals(entry.drawable, Sprites.portrait(key, HeroClassId(cls)))
            assertTrue("content box of $key leaves the 64 px box or the outer ring", entry.left >= 1 && entry.top >= 1 && entry.right <= 63 && entry.bottom <= 63 && entry.left < entry.right && entry.top < entry.bottom)
        }
        assertEquals(25, seen.size)
    }

    @Test
    fun unknownKeyFallsBackToTheClass() {
        for (cls in classes) assertEquals(PortraitArt["portrait_${cls}_0"]!!.drawable, Sprites.portrait("portrait_gone_9", HeroClassId(cls)))
    }

    @Test
    fun secondSetStaysOutOfThePoolUntilSwitchedOn() {
        assertFalse(PortraitArt.SECOND_SET_ENABLED)
        assertTrue(PortraitArt.secondSet.isNotEmpty())
        for ((key, entry) in PortraitArt.secondSet) {
            assertTrue("missing drawable ${entry.resName}.png", drawable(entry.resName).isFile)
            assertEquals("a second-set key must not resolve while the set is off", null, PortraitArt[key])
            val cls = key.removePrefix("portrait_v2_").substringBefore('_')
            assertEquals(PortraitArt["portrait_${cls}_0"]!!.drawable, Sprites.portrait(key, HeroClassId(cls)))
            assertEquals(entry.drawable, Sprites.portrait(key, HeroClassId(cls), secondSet = true))
        }
    }
}
