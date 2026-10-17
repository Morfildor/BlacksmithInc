package com.example.blacksmithproject

import com.example.blacksmithproject.ui.nearestWholeScale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The whole device-pixel scale pixel art is drawn at, for the same dp hint at 420, 440 and 480 dpi (density 2.625,
 * 2.75, 3.0): the same logical width can land on a different factor, and this table pins it.
 */
class SpriteScaleTest {

    private fun k(artPx: Int, hintDp: Float, density: Float, maxDp: Float = Float.POSITIVE_INFINITY) =
        nearestWholeScale(artPx, hintDp * density, maxDp * density)

    @Test fun face64At44() = assertEquals(listOf(2, 2, 2), listOf(k(64, 44f, 2.625f), k(64, 44f, 2.75f), k(64, 44f, 3f)))
    @Test fun face64At54() = assertEquals(listOf(2, 2, 3), listOf(k(64, 54f, 2.625f), k(64, 54f, 2.75f), k(64, 54f, 3f)))
    @Test fun blade56At48() = assertEquals(listOf(2, 2, 3), listOf(k(56, 48f, 2.625f), k(56, 48f, 2.75f), k(56, 48f, 3f)))
    @Test fun blade56At72() = assertEquals(listOf(3, 4, 4), listOf(k(56, 72f, 2.625f), k(56, 72f, 2.75f), k(56, 72f, 3f)))
    @Test fun icon32At30() = assertEquals(listOf(2, 3, 3), listOf(k(32, 30f, 2.625f), k(32, 30f, 2.75f), k(32, 30f, 3f)))
    @Test fun icon32At24() = assertEquals(listOf(2, 2, 2), listOf(k(32, 24f, 2.625f), k(32, 24f, 2.75f), k(32, 24f, 3f)))

    @Test
    fun theScaleStepsDownToStayInsideItsBox() {
        assertEquals(2, nearestWholeScale(56, 44f * 3f, maxPx = 44f * 3f))
        // 160 px rounds to 3 (168 px), over the 132 px box: one step down.
        assertEquals(2, nearestWholeScale(56, 160f, maxPx = 132f))
    }

    @Test fun neverBelowOne() = assertEquals(1, nearestWholeScale(8, 1f))
}
