package com.example.blacksmithproject.ui

import kotlin.math.roundToInt

/*
 * Layout decisions from measured width, not from fontScale or screenWidthDp guesses. Pure: callers measure inside
 * BoxWithConstraints (and a TextMeasurer) and pass pixels, so the rules are tested on the JVM.
 */

/**
 * How many of [count] tabs share a row: the largest of [count], its divisors, 2 and 1 at which every tab
 * (`(available − gap·(n−1)) / n`, less [padPx] on each side) still holds the widest single word of any label. Never below 1.
 */
fun tabsPerRow(availablePx: Float, widestWordPx: Float, count: Int, gapPx: Float, padPx: Float): Int {
    if (count < 1) return 1
    val candidates = ((1..count).filter { count % it == 0 } + listOf(2, 1)).filter { it <= count }.distinct().sortedDescending()
    return candidates.firstOrNull { n -> (availablePx - gapPx * (n - 1)) / n - 2 * padPx >= widestWordPx } ?: 1
}

/** Whether a row's text keeps at least [minTextPx] beside its fixed parts and its trade (price, buttons) on one line. */
fun roomBeside(rowWidthPx: Float, fixedPx: Float, tradePx: Float, minTextPx: Float): Boolean = rowWidthPx - fixedPx - tradePx >= minTextPx

/**
 * The whole number of device pixels per art pixel nearest [hintPx] for art [artPx] wide, stepped down while the art would
 * be wider than [maxPx]; never below 1. Pixel art is only ever enlarged by such a factor ([wholePixelDp], `wholeScale`).
 */
fun nearestWholeScale(artPx: Int, hintPx: Float, maxPx: Float = Float.POSITIVE_INFINITY): Int {
    var k = (hintPx / artPx).roundToInt()
    while (k > 1 && artPx * k > maxPx) k--
    return maxOf(1, k)
}
