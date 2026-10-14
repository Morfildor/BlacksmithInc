package com.tinyblacksmith.core

import com.tinyblacksmith.core.text.Odds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Decision A: hidden odds become words, never a number, and no word promises an outcome. */
class OddsTest {
    private val steps = (0..100).map { it / 100.0 }

    @Test
    fun theWordsCarryNoNumberAndNeverPromise() {
        for (p in steps) {
            for (word in listOf(Odds.often(p), Odds.chance(p))) {
                assertTrue(word.none { it.isDigit() } && '%' !in word, "$p: $word")
                for (promise in listOf("always", "certain", "sure", "guaranteed")) assertTrue(promise !in word, "$p: $word")
            }
        }
    }

    @Test
    fun theWordsRiseWithTheChanceAndZeroIsSaidPlainly() {
        val often = listOf("never", "rarely", "now and then", "often", "more often than not")
        val chance = listOf("no chance", "a slim chance", "a fair chance", "a good chance", "a strong chance")
        assertEquals("never", Odds.often(0.0))
        assertEquals("no chance", Odds.chance(0.0))
        // Each scale moves only upward as the chance rises, and reaches its top.
        assertEquals(often, steps.map(Odds::often).distinct())
        assertEquals(chance, steps.map(Odds::chance).distinct())
        // The odds the game has today: the Reckless flaw chance and the Heartfire breakthrough at its floor and its cap.
        assertEquals("often", Odds.often(0.30))
        assertEquals("a fair chance", Odds.chance(0.30))
        assertEquals("a strong chance", Odds.chance(0.75))
    }
}
