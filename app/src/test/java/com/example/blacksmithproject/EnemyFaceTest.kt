package com.example.blacksmithproject

import com.example.blacksmithproject.ui.Sprites
import com.example.blacksmithproject.ui.replay.BESTIARY_ART
import com.example.blacksmithproject.ui.replay.BESTIARY_FIGURES
import com.example.blacksmithproject.ui.replay.enemyFace
import com.example.blacksmithproject.ui.replay.enemyFaction
import com.example.blacksmithproject.ui.replay.replayFaces
import com.tinyblacksmith.core.combat.FightReplay
import com.tinyblacksmith.core.combat.Side
import com.tinyblacksmith.core.combat.Tags
import com.tinyblacksmith.core.content.Bestiary
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.LivingRevision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** R-01: every enemy of a siege has a picture that truly shows it, or a plate with its name. Never an initial. */
class EnemyFaceTest {
    private val content = GameEngine().content
    private val guild = content.guild!!

    @Test
    fun everyBestiaryUnitMarchesWithItsOwnHost() {
        assertEquals(48, Bestiary.profiles.size)
        Bestiary.profiles.forEach { p -> assertEquals(p.unitId, p.factionId, enemyFaction(p.unitId, content)) }
    }

    @Test
    fun aBestiaryUnitHasAFactionFigureOnlyWhereTheDeclaredRuleSaysItShowsThatCreature() {
        // Pinned: changing who wears a shared figure is a decision about the art, taken on purpose.
        assertEquals(
            mapOf("pp_skeleton_guard" to false, "pp_skeleton_archer" to false, "pp_wraith" to true, "cap_lich" to true),
            BESTIARY_FIGURES,
        )
        assertTrue("every declared unit is in the bestiary", BESTIARY_FIGURES.keys.all { it in guild.profileById })
        assertEquals(
            setOf("eng_battering_ram", "eng_ballista", "eng_corpse_catapult", "boss_hill_giant", "boss_bone_colossus", "boss_ash_dragon"),
            BESTIARY_ART.keys,
        )
        assertTrue("every portrait is of a bestiary unit", BESTIARY_ART.keys.all { it in guild.profileById })
        assertTrue("a unit has its own portrait or a shared figure, never both", BESTIARY_ART.keys.none { it in BESTIARY_FIGURES })
        Bestiary.profiles.forEach { p ->
            val figure = BESTIARY_FIGURES[p.unitId]
            val art = BESTIARY_ART[p.unitId]
            if (art != null) assertEquals(p.unitId, art, enemyFace(p.unitId, content))
            else if (figure == null) assertNull("${p.unitId} has a name plate", enemyFace(p.unitId, content))
            else assertEquals(p.unitId, Sprites.faction(p.factionId, elite = figure), enemyFace(p.unitId, content))
        }
        // The longest real name is a plate, and the plate carries the recorded name whole.
        assertEquals("Sir Carrow, the Unburied", content.combat!!.unitById.getValue("cap_death_knight").name)
        assertNull(enemyFace("cap_death_knight", content))
    }

    @Test
    fun anEngineHasItsOwnPortraitAndNeverASharedFigure() {
        val engines = Bestiary.units.filter { Tags.ENGINE in it.tags }
        assertEquals(3, engines.size)
        engines.forEach {
            assertFalse(it.id, it.id in BESTIARY_FIGURES)
            assertEquals(it.id, BESTIARY_ART[it.id], enemyFace(it.id, content))
        }
    }

    @Test
    fun everyUnitASiegeOrAQuestFieldsHasAPictureOrIsADeclaredNamePlate() {
        val plates = Bestiary.profiles.map { it.unitId }.toSet() - BESTIARY_FIGURES.keys - BESTIARY_ART.keys
        val fielded = buildSet {
            addAll(guild.siegeLeaders.values); addAll(guild.siegeElites.values); guild.siegeGrunts.values.forEach { addAll(it) }
            guild.preparations.forEach { p -> addAll(listOfNotNull(p.addUnit, p.gruntUnit)); p.sabotage.rosters.values.forEach { addAll(it) } }
            guild.modifiers.forEach { addAll(it.addUnit.values) }
            guild.armies.forEach { addAll(it.members) }
            guild.missions.forEach { m -> m.stages.forEach { s -> s.rosters.values.forEach { addAll(it) } } }
        }
        assertTrue(fielded.size > 40)
        fielded.forEach { u -> assertTrue("$u: a picture or a declared plate", enemyFace(u, content) != null || u in plates) }
        // The older rosters keep the pictures they had.
        fielded.filter { it !in guild.profileById }.forEach { assertNotNull(it, enemyFace(it, content)) }
    }

    @Test
    fun whatAPartyProtectsOrBringsAlongHasNoFactionPicture() {
        listOf("town_watch", "captive", "escort_cart").forEach {
            assertNull(it, enemyFaction(it, content))
            assertNull(it, enemyFace(it, content))
        }
    }

    @Test
    fun aLivingEraSiegeShowsEveryFighterAsAPictureOrByName() {
        val (state, r) = LivingAgencyFixtures.siegeDay()!!
        assertTrue("a living era", state.livingRevision >= LivingRevision.CURRENT)
        val replay = FightReplay.of(r.siege!!.fight!!)!!
        val faces = replayFaces(replay, state, LivingAgencyFixtures.engine.content)
        val enemies = replay.actors.filter { it.side == Side.ENEMY }
        assertTrue("the army is the bestiary's", enemies.any { (it.unitId ?: "") in guild.profileById })
        replay.actors.forEach { a ->
            val face = faces.getValue(a.key)
            if (face.res == null) {
                assertEquals("${a.key}: the plate says the recorded name", a.name.trim(), face.name)
                assertTrue("${a.key}: a name, not an initial", face.name.length > 1)
            }
        }
    }
}
