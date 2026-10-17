package com.example.blacksmithproject

import com.example.blacksmithproject.ui.EffectKind
import com.example.blacksmithproject.ui.Fact
import com.example.blacksmithproject.ui.FactTone
import com.example.blacksmithproject.ui.endDayHasMore
import com.example.blacksmithproject.ui.endDayHeadline
import com.example.blacksmithproject.ui.factText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2 shell rules (docs/UI_V2_LEDGER.md): End Day carries one line and offers the rest; facts read as one wrapping line. */
class V2ShellTest {

    @Test
    fun endDayCarriesTheMostPressingLineAndOffersTheRest() {
        assertNull("no line, nothing to say", endDayHeadline(emptyList()))
        // A short line is said whole, even with two sentences: it fits the button's second line.
        assertEquals("Trading first. The siege follows tonight.", endDayHeadline(listOf("Trading first. The siege follows tonight.")))
        assertFalse(endDayHasMore(listOf("Trading first. The siege follows tonight.")))
        // A long line gives its first sentence, and "What happens" holds the rest.
        val guild = "Siege tonight: Hollowbound. Reserved for the wall: Ione Dray. Townsfolk filling in for now: none."
        assertEquals("Siege tonight: Hollowbound.", endDayHeadline(listOf(guild)))
        assertTrue(endDayHasMore(listOf(guild)))
        // Two lines always offer the rest; one short line does not.
        assertTrue(endDayHasMore(listOf("10 energy unused", "Ione Dray leaves for the Cinder Quarry, home on the morning of day 7")))
        assertFalse(endDayHasMore(listOf("10 energy unused")))
    }

    @Test
    fun factsAreOneLineWithBoundSeparatorsAndSignedTones() {
        val text = factText(listOf(Fact("20 gold", "Fee"), Fact("day 7", "Back"), Fact("Lethal", tone = FactTone.WARN), Fact("Shields up", tone = FactTone.GOOD))).text
        assertEquals("Fee 20 gold · Back day 7 · ${EffectKind.FLAW.sign} Lethal · ${EffectKind.BUFF.sign} Shields up", text)
        // A separator never starts a wrapped line: it is bound to the fact before it by a no-break space.
        assertFalse(text.contains(" ·"))
        // A label is bound to its value the same way, so "Back" never ends a line with "day 7" on the next.
        assertTrue(text.contains("Back day 7"))
        assertFalse(text.contains("Fee ") || text.contains("Back "))
    }
}
