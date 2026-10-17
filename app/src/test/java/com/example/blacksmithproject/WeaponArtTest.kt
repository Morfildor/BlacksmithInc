package com.example.blacksmithproject

import com.example.blacksmithproject.ui.Sprites
import com.example.blacksmithproject.ui.WeaponArt
import com.tinyblacksmith.core.content.Element
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.MaterialCategory
import com.tinyblacksmith.core.model.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The generated weapon layers (tools/pixelart/weapon_layers.py) against the content: every metal, augment and catalyst shows. */
class WeaponArtTest {
    private val content = LaunchContent.catalog
    private fun drawable(name: String) = File("src/main/res/drawable-nodpi/$name.png")

    @Test
    fun everyMaterialHasItsLayer() {
        assertEquals(content.materials(MaterialCategory.CORE).map { it.id.value }, WeaponArt.metals)
        assertEquals(Element.entries.map { it.name.lowercase() }, WeaponArt.elements)
        assertEquals(content.materials(MaterialCategory.CATALYST).map { it.id.value }, WeaponArt.catalysts)
        assertEquals(content.materials(MaterialCategory.AUGMENT).mapNotNull { it.element }.toSet(), Element.entries.toSet())
        for (family in content.families.map { it.id.value }) for (level in 1..WeaponArt.LEVELS) {
            WeaponArt.metals.forEach { assertTrue("$family $it $level", drawable("weapon_${family}_${it}_$level").isFile) }
            WeaponArt.elements.forEach { assertTrue("$family $it $level", drawable("weapon_fx_${family}_${it}_$level").isFile) }
            WeaponArt.catalysts.forEach { assertTrue("$family $it $level", drawable("weapon_charm_${family}_${it}_$level").isFile) }
        }
    }

    @Test
    fun aWeaponStacksItsMetalAugmentAndCatalyst() {
        val catalysts = content.materials(MaterialCategory.CATALYST)
        for (family in content.families) for (rarity in Rarity.entries) {
            val bases = content.materials(MaterialCategory.CORE).map { Sprites.weapon(family.id, it.id, null, rarity).base }
            assertEquals("each metal its own base: ${family.id.value} $rarity", bases.size, bases.toSet().size)
            for (core in content.materials(MaterialCategory.CORE)) {
                val plain = Sprites.weapon(family.id, core.id, null, rarity)
                assertNull(plain.effect)
                assertNull(plain.charm)
                assertEquals(listOf(plain.base), plain.layers)
                val effects = Element.entries.map { Sprites.weapon(family.id, core.id, it, rarity).effect }
                effects.forEach(::assertNotNull)
                assertEquals(effects.size, effects.toSet().size)
                for (catalyst in catalysts) {
                    val full = Sprites.weapon(family.id, core.id, Element.FIRE, rarity, catalystId = catalyst.id)
                    assertEquals(plain.base, full.base)
                    assertEquals(3, full.layers.size)
                }
                assertEquals(catalysts.size, catalysts.map { Sprites.weapon(family.id, core.id, null, rarity, catalystId = it.id).charm }.toSet().size)
            }
        }
        // The level column still rises with the metal and the rarity: an iron common blade and a moonsteel legendary differ in more than colour.
        val sword = content.families.first().id
        assertNotEquals(Sprites.weapon(sword, content.materials(MaterialCategory.CORE).first().id, null).base,
            Sprites.weapon(sword, content.materials(MaterialCategory.CORE).first().id, null, Rarity.LEGENDARY).base)
    }
}
