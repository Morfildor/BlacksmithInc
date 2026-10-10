package com.tinyblacksmith.core

import com.tinyblacksmith.core.text.LegacyProse
import com.tinyblacksmith.core.text.asSentence
import com.tinyblacksmith.core.text.joinSentences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Old stored prose is shown in the new voice without being changed, and new prose passes through untouched. */
class LegacyProseTest {
    private val dash = "—"

    @Test
    fun knownOldJournalLinesBecomeTheNewOnes() {
        assertEquals("Pairing learned. Iron + Ember Resin. Excellent match.", LegacyProse.display("Journal: Iron + Ember Resin is now understood $dash excellent match."))
        assertEquals("First notes on Ember Resin on Spears. Faint harmony.", LegacyProse.display("Journal: Ember Resin on Spears observed $dash faint harmony."))
        assertEquals(
            "Notes on Silver + Stormglass Sword. Hides something more. It wants a hotter fire.",
            LegacyProse.display("Journal: Silver + Stormglass Sword $dash Hides something more; it wants a hotter fire."),
        )
    }

    @Test
    fun aRewrittenLineAlwaysEndsWithAPeriod() {
        assertEquals("First notes on Iron on Axes. Hides something more.", LegacyProse.display("Journal: Iron on Axes observed $dash hides something more"))
    }

    @Test
    fun anyOtherTextKeepsItsFactsAndLosesOnlyItsSeparators() {
        assertEquals(
            "Mira Vance routed Ashclaw scouts using Winterwake. Brought Starsteel back to the forge. Grew stronger (level 2).",
            LegacyProse.display("Mira Vance routed Ashclaw scouts using Winterwake; brought Starsteel back to the forge; grew stronger (level 2)."),
        )
        assertEquals("The raid was thrown back. The town paid 100 gold.", LegacyProse.display("The raid was thrown back $dash the town paid 100 gold"))
    }

    @Test
    fun namesTitlesAndAbbreviationsAreLeftAlone() {
        for (text in listOf(
            "Mr. Hale's Axe, 'Bane of the Raiders', is back in the forge.",
            "Mira Vance, a Ranger, arrived in Emberfall.",
            "Forged Iron Axe. Common. Quality 38.",
            "Their guild covered 30 gold. They paid 30 gold themselves.",
        )) assertEquals(text, LegacyProse.display(text))
    }

    @Test
    fun displayIsIdempotent() {
        for (text in listOf(
            "Journal: A is now understood $dash fine.", "One; two; three", "Plain sentence.", "A $dash B $dash C", "Ends without a period; and a clause",
        )) {
            val once = LegacyProse.display(text)
            assertEquals(once, LegacyProse.display(once), text)
        }
    }

    @Test
    fun theBesiegerIsReadFromTheSiegeRecordsOwnSentence() {
        assertEquals("the Ashclaw horde", LegacyProse.siegeAttacker("Emberfall held against the Ashclaw horde. Mira Vance, Bram Holt defended the walls."))
        assertEquals("the Ashclaw horde", LegacyProse.siegeAttacker("Emberfall repelled the Ashclaw horde! Champions: Mira Vance, Bram Holt."))
        assertEquals("the Hollowbound host", LegacyProse.siegeAttacker("The Hollowbound host overran the defenders (Mira Vance)."))
        assertNull(LegacyProse.siegeAttacker("Something else happened."))
    }

    @Test
    fun clausesJoinAsSentencesWithOneCapitalAndOnePeriod() {
        assertEquals("Iron Axe.", "iron Axe..".asSentence())
        assertEquals("", "  ".asSentence())
        assertEquals("Suits their class. They need a weapon.", listOf("suits their class", "They need a weapon.", "").joinSentences())
        assertEquals("", emptyList<String>().joinSentences())
    }
}
