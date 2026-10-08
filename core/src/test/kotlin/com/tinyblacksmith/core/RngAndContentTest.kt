package com.tinyblacksmith.core

import com.tinyblacksmith.core.content.SliceContent
import com.tinyblacksmith.core.persistence.SaveCodec
import com.tinyblacksmith.core.rng.Rng
import com.tinyblacksmith.core.rng.RngState
import com.tinyblacksmith.core.rng.RngStream
import com.tinyblacksmith.core.rng.SplitMix64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RngAndContentTest {
    @Test
    fun splitMixMatchesReferenceVector() {
        // Reference SplitMix64 with seed 0: first output is 0xE220A8397B1DCDAF.
        val (_, out) = SplitMix64.step(0L)
        assertEquals(-2152535657050944081L, out) // 0xE220A8397B1DCDAF
    }

    @Test
    fun sameSeedSameSequenceAndStreamsAreIndependent() {
        val a = Rng(RngState.seeded(42, 1).stateOf(RngStream.CRAFTING))
        val b = Rng(RngState.seeded(42, 1).stateOf(RngStream.CRAFTING))
        val c = Rng(RngState.seeded(42, 1).stateOf(RngStream.COMBAT))
        val sa = List(50) { a.nextInt(1000) }
        val sb = List(50) { b.nextInt(1000) }
        val sc = List(50) { c.nextInt(1000) }
        assertEquals(sa, sb)
        assertNotEquals(sa, sc)
    }

    @Test
    fun boundsHold() {
        val r = Rng(7)
        repeat(10_000) {
            val d = r.nextDouble(); assertTrue(d >= 0.0 && d < 1.0)
            val i = r.nextInt(-13, 13); assertTrue(i in -13..13)
        }
    }

    @Test
    fun rngStateRoundTripsThroughJson() {
        val s = RngState.seeded(99, 1)
        val text = SaveCodec.json.encodeToString(RngState.serializer(), s)
        assertEquals(s, SaveCodec.json.decodeFromString(RngState.serializer(), text))
    }

    @Test
    fun sliceContentIsValid() {
        assertEquals(emptyList(), SliceContent.catalog.validate())
        assertEquals(3, SliceContent.catalog.families.size)
        assertEquals(2, SliceContent.catalog.classes.size)
        assertEquals(1, SliceContent.catalog.factions.size)
    }
}
