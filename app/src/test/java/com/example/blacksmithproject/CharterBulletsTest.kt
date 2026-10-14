package com.example.blacksmithproject

import com.example.blacksmithproject.ui.charterCosts
import com.example.blacksmithproject.ui.charterGains
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.engine.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/** The run setup's gains and costs are short bullets read from each charter's own numbers. */
class CharterBulletsTest {
    private val content = GameEngine().content
    private val charters = content.guild!!.charters.associateBy { it.id }

    @Test
    fun theTownsLastHopeSaysWhatItGivesAndCostsInItsOwnNumbers() {
        val c = charters.getValue(GuildContent.TOWNS_LAST_HOPE)
        assertEquals(listOf("Guardian and Warden sign on day 1", "Battlemage ready to hire on day 1", "3 extra militia on the wall", "1 Binding Salt"), charterGains(c, content))
        assertEquals(listOf("Start with 40 less gold"), charterCosts(c))
    }

    @Test
    fun everyCharterHasAtLeastOneGainAndOneCost() {
        charters.values.forEach { c ->
            assert(charterGains(c, content).isNotEmpty()) { c.id }
            assert(charterCosts(c).isNotEmpty()) { c.id }
        }
        assertEquals(listOf("The forge keeps 80% of contract gold"), charterCosts(charters.getValue(GuildContent.HONEST_BUSINESS)))
    }
}
