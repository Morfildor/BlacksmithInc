package com.example.blacksmithproject

import com.example.blacksmithproject.ui.Effect
import com.example.blacksmithproject.ui.Haptics
import com.example.blacksmithproject.ui.Moment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The setting is the gate: every moment goes through [Haptics.play], and off means silent. */
class HapticsTest {
    private class Recorder(var on: Boolean) {
        val played = mutableListOf<Effect>()
        val haptics = Haptics(enabled = { on }, perform = { played += it })
    }

    @Test
    fun onFiresTheMomentsEffect() {
        val r = Recorder(on = true)
        Moment.entries.forEach { r.haptics.play(it) }
        assertEquals(Moment.entries.map { it.effect }, r.played)
    }

    @Test
    fun offFiresNothingForAnyMoment() {
        val r = Recorder(on = false)
        Moment.entries.forEach { r.haptics.play(it) }
        assertTrue(r.played.isEmpty())
    }

    @Test
    fun theSettingIsReadOnEveryCall() {
        val r = Recorder(on = true)
        r.haptics.play(Moment.SALE)
        r.on = false
        r.haptics.play(Moment.SALE)
        r.on = true
        r.haptics.play(Moment.REJECTED)
        assertEquals(listOf(Moment.SALE.effect, Moment.REJECTED.effect), r.played)
    }

    @Test
    fun everyMomentHasItsOwnEffect() {
        assertEquals(6, Moment.entries.size)
        assertEquals(Moment.entries.size, Moment.entries.map { it.effect }.toSet().size)
    }
}
