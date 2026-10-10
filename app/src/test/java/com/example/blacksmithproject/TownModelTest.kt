package com.example.blacksmithproject

import com.example.blacksmithproject.ui.gazetteHead
import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.toolLine
import com.example.blacksmithproject.ui.townSiege
import com.tinyblacksmith.core.battle.Battle
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.content.UpgradeEffect
import com.tinyblacksmith.core.gazette.Gazette
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.shopday.Threats
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Town's siege card as facts: the foe, what tells against it, defense against the raid, the forge's health and its ceiling. */
class TownModelTest : ShopDayTestBase() {
    private fun mornings() = (42L..47L).asSequence().flatMap { ShopDayFixtures.run(it, 40) }.map { it.state }.filter { !it.isEnded }

    @Test
    fun theSiegeCardCarriesTheEnginesOwnFacts() {
        var matched = 0
        for (state in mornings().take(120)) {
            val forecast = engine.siegeForecast(state)!!
            val t = Threats.of(state, engine.content, engine.config)!!
            val ui = engine.townSiege(state, forecast)
            val def = engine.content.faction(t.factionId)
            assertEquals(forecast.factionState.id, ui.factionId)
            assertEquals(def.name, ui.foe)
            // The same three facts the Shop's one line joins: nothing Town says of the matchup differs from it.
            assertEquals(engine.shopUi(state, forecast).threat!!.matchup, listOfNotNull(ui.foe, ui.weakTo?.let { "Weak to $it" }, ui.resists?.let { "Resists $it" }).joinToString(" · "))
            assertEquals(forecast.townDefense.roundToInt(), ui.defense)
            assertEquals(forecast.raidPower.roundToInt(), ui.raid)
            assertEquals(state.town.integrity, ui.forgeHealth)
            assertEquals(engine.config.startingForgeIntegrity, ui.forgeHealthMax)
            assertTrue(ui.forgeHealth in 0..ui.forgeHealthMax)
            assertEquals(Battle.describePressure(forecast.factionState.pressure), ui.pressure.lowercase())
            assertEquals(forecast.warlord, ui.warlord != null)
            assertEquals(state.town.armory > 0, ui.standing.any { it.first == "Armory" })
            assertEquals("${state.town.militia}", ui.standing.first { it.first == "Militia" }.second)
            assertEquals("${state.town.siegesSurvived}", ui.standing.first { it.first == "Sieges held" }.second)
            assertEquals(state.world.name, ui.world)
            // Every other faction, the most pressing first; never the besieger twice.
            assertEquals(state.factions.keys - ui.factionId!!, ui.others.map { it.id }.toSet())
            assertEquals(ui.others.map { it.pressure }.sortedDescending(), ui.others.map { it.pressure })
            if (ui.weakTo != null || ui.resists != null) matched++
        }
        assertTrue("the fixtures reach a besieger with a matchup", matched > 0)
    }

    @Test
    fun withoutAForecastTheNumbersAreLeftOut() {
        val ui = engine.townSiege(fresh, null)
        assertNull(ui.defense)
        assertNull(ui.raid)
        assertEquals(fresh.town.integrity, ui.forgeHealth)
        assertEquals(engine.shopUi(fresh).threat!!.matchup!!.substringBefore(" · "), ui.foe)
    }

    @Test
    fun stalwartWallsRaiseTheCeilingOfForgeHealth() {
        val legacy = LegacyProfile(upgrades = mapOf(LaunchContent.UPG_WALLS to 2))
        val run = engine.newRun(legacy, 42L)
        val ui = engine.townSiege(run, null)
        assertEquals(engine.config.startingForgeIntegrity + engine.upgradeTotal(legacy, UpgradeEffect.STARTING_INTEGRITY), ui.forgeHealthMax)
        assertEquals(ui.forgeHealthMax, ui.forgeHealth)
        assertTrue(ui.forgeHealthMax > engine.config.startingForgeIntegrity)
    }

    @Test
    fun theMastheadSplitsIntoThePapersNameAndADateline() {
        val head = gazetteHead(7)
        assertEquals("EMBERFALL GAZETTE", head.paper)
        assertEquals("Day 7", head.dateline)
        assertEquals(Gazette.masthead(7), "${head.paper} — ${head.dateline.uppercase()}")
    }

    @Test
    fun aToolsLineSaysItsLevelAndTheNextPriceOrThatItIsBuilt() {
        val tool = engine.content.tools.first()
        assertEquals("Level 0 of ${tool.maxLevel} · ${engine.toolCost(fresh, tool.id)} gold", toolLine(0, tool.maxLevel, engine.toolCost(fresh, tool.id)))
        val built = fresh.copy(tools = mapOf(tool.id to tool.maxLevel))
        assertNull(engine.toolCost(built, tool.id))
        assertEquals("Level ${tool.maxLevel} of ${tool.maxLevel} · fully built", toolLine(tool.maxLevel, tool.maxLevel, engine.toolCost(built, tool.id)))
    }
}
