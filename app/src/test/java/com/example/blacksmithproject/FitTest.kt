package com.example.blacksmithproject

import com.example.blacksmithproject.ui.roomBeside
import com.example.blacksmithproject.ui.tabsPerRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Layout decisions from measured width (docs/superpowers/plans/2026-10-17-visual-quality-pass.md, Task 1), at density 1 so a px is a dp. */
class FitTest {

    private val gap = 0f
    private val pad = 12f

    @Test
    fun tabsGoTwoPerRowWhenFourDoNotFitAndOnePerRowAtDoubleText() {
        // 288 dp across, the widest label word 79 dp at font 1.0: four tabs leave 48 dp of text each, two leave 120.
        assertEquals(2, tabsPerRow(288f, 79f, 4, gap, pad))
        // The same words at font 2.0 (158 dp) only fit one to a row.
        assertEquals(1, tabsPerRow(288f, 79f * 2f, 4, gap, pad))
        // Four short words that fit stay on one row.
        assertEquals(4, tabsPerRow(288f, 48f, 4, gap, pad))
    }

    @Test
    fun tabsPerRowIsNeverBelowOne() {
        assertEquals(1, tabsPerRow(100f, 500f, 4, gap, pad))
        assertEquals(1, tabsPerRow(288f, 10f, 0, gap, pad))
    }

    @Test
    fun theTextHasRoomBesideOnlyWhenMinimumWidthIsLeft() {
        // 328 dp row, 122 dp of fixed parts, a 64 dp trade: 142 dp left, under the 150 dp minimum.
        assertFalse(roomBeside(328f, 122f, 64f, 150f))
        assertTrue(roomBeside(380f, 122f, 64f, 150f))
    }
}
