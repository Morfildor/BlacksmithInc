package com.example.blacksmithproject

import com.example.blacksmithproject.ui.SiegeTone
import com.example.blacksmithproject.ui.endDayNote
import com.example.blacksmithproject.ui.shopUi
import com.example.blacksmithproject.ui.siegeHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The workshop header: the day, the countdown from `town.nextSiegeDay` (never the day modulo the interval), the cycle's markers and the wall's places. */
class SiegeHeaderTest : ShopDayTestBase() {

    @Test
    fun theCountdownIsSaidInThreeVoices() {
        siegeHeader(day = 1, siegeDay = 5, interval = 5, besieger = null, filled = 2, places = 3).run {
            assertEquals("Siege in 4 days · Day 5", countdown)
            assertEquals(SiegeTone.CALM, tone)
            assertNull("the wall's places are said only on the eve and the day", defenders)
        }
        siegeHeader(day = 4, siegeDay = 5, interval = 5, besieger = "Ashclaw Raiders", filled = 2, places = 3).run {
            assertEquals("Siege tomorrow · Day 5", countdown)
            assertEquals(SiegeTone.TOMORROW, tone)
            assertEquals("Ashclaw Raiders", besieger)
            assertEquals("2 of 3 defender slots filled", defenders)
        }
        siegeHeader(day = 5, siegeDay = 5, interval = 5, besieger = null, filled = 3, places = 3).run {
            assertEquals("SIEGE TONIGHT · Day 5", countdown)
            assertEquals(SiegeTone.TONIGHT, tone)
            assertEquals("3 of 3 defender slots filled", defenders)
        }
    }

    @Test
    fun theMarkersAreTheDaysOfThisCycle() {
        val first = siegeHeader(day = 3, siegeDay = 5, interval = 5, besieger = null, filled = null, places = null)
        assertEquals(listOf(1, 2, 3, 4, 5), first.marks.map { it.day })
        assertEquals(listOf(1, 2), first.marks.filter { it.past }.map { it.day })
        assertEquals(listOf(3), first.marks.filter { it.today }.map { it.day })
        assertEquals(listOf(5), first.marks.filter { it.siege }.map { it.day })
        // After a siege the next cycle starts the day after it.
        assertEquals(listOf(6, 7, 8, 9, 10), siegeHeader(6, 10, 5, null, null, null).marks.map { it.day })
        // Read from the schedule, not the day modulo five: a siege the save puts on day 9 is day 9.
        siegeHeader(day = 7, siegeDay = 9, interval = 5, besieger = null, filled = null, places = null).run {
            assertEquals("Siege in 2 days · Day 9", countdown)
            assertEquals(listOf(5, 6, 7, 8, 9), marks.map { it.day })
        }
        // Never a marker before day 1, and never a cycle that starts after today.
        assertEquals(listOf(1, 2, 3), siegeHeader(1, 3, 5, null, null, null).marks.map { it.day })
        assertEquals(listOf(4, 5, 6, 7, 8), siegeHeader(4, 8, 2, null, null, null).marks.map { it.day })
    }

    @Test
    fun everyMorningOfAClassicRunReadsTheSave() {
        var eves = 0
        for (state in (42L..44L).asSequence().flatMap { ShopDayFixtures.run(it, 30) }.map { it.state }.filter { !it.isEnded }.take(60)) {
            val forecast = engine.siegeForecast(state)
            val s = UiState.Playing(state, engine.shopUi(state, forecast), forecast)
            val h = s.siegeHeader(engine.config.siegeInterval, engine.config.championCount)
            val left = state.town.nextSiegeDay - state.day
            assertEquals(state.day, h.day)
            assertTrue(h.countdown, h.countdown.endsWith("Day ${state.town.nextSiegeDay}"))
            assertTrue(h.marks.any { it.today && it.day == state.day })
            assertTrue(h.marks.last().siege && h.marks.last().day == state.town.nextSiegeDay)
            // Who comes is said only once the warning is out.
            assertEquals(s.shop.threat?.takeIf { it.warned }?.besieger, h.besieger)
            if (left <= 1 && forecast != null) {
                eves++
                assertEquals("${forecast.champions.size} of ${engine.config.championCount} defender slots filled", h.defenders)
            } else if (left > 1) assertNull(h.defenders)
            // End Day on the siege's day says trading comes first, and no other day says it.
            val note = endDayNote(s)
            assertEquals(note, left <= 0, note.contains("Trading first. The siege follows tonight."))
            assertFalse(note.contains("The siege starts after today's trading"))
        }
        assertTrue("the fixtures reach a siege eve or day", eves > 0)
    }
}
