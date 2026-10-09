package com.example.blacksmithproject

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.DestinationBar
import com.example.blacksmithproject.ui.destName
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The bar shows four destinations whose labels share one fixed style: larger text grows them, nothing shrinks to fit or clips. */
@RunWith(AndroidJUnit4::class)
class NavigationBarTest {
    @get:Rule
    val compose = createComposeRule()

    private var fontScale by mutableStateOf(1f)
    private var selected by mutableStateOf(Dest.SHOP)

    private fun show(width: Int = 360) = compose.setContent {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
            BlacksmithProjectTheme { Box(Modifier.width(width.dp)) { DestinationBar(selected, onSelect = { selected = it }) } }
        }
    }

    /** Height in px of each label, in bar order, and whether the whole label sits inside its own bar item (hasVisualOverflow is unreliable with softWrap off). */
    private fun labels(): List<Pair<Float, Boolean>> = Dest.entries.map { d ->
        val label = compose.onNodeWithTag("nav_label_${d.name.lowercase()}", useUnmergedTree = true)
        label.assertIsDisplayed()
        val box = label.fetchSemanticsNode().boundsInRoot
        val item = compose.onNodeWithTag("nav_${d.name.lowercase()}").fetchSemanticsNode().boundsInRoot
        box.height to (box.left >= item.left && box.right <= item.right && box.top >= item.top && box.bottom <= item.bottom)
    }

    @Test
    fun fourDestinationsKeepTheirLabelSize() {
        show()
        assertEquals(listOf("Shop", "Forge", "Town", "Records"), Dest.entries.map(::destName))

        val normal = labels()
        assertEquals("all four labels are one size", 1, normal.map { it.first }.distinct().size)
        assertTrue("every label sits inside its item $normal", normal.all { it.second })

        // Selecting a destination must not restyle its label.
        Dest.entries.forEach { d ->
            compose.onNodeWithTag("nav_${d.name.lowercase()}").performClick()
            compose.waitForIdle()
            assertEquals(d, selected)
            assertEquals("selecting ${d.name} keeps every label's size", normal.map { it.first }, labels().map { it.first })
        }

        // A larger font scale makes every label taller by the same factor (it is not auto-shrunk) and still on one line.
        for (scale in listOf(1.3f, 1.5f)) {
            fontScale = scale
            compose.waitForIdle()
            val big = labels()
            assertEquals("all four labels are one size at $scale", 1, big.map { it.first }.distinct().size)
            assertTrue("every label sits inside its item at $scale $big", big.all { it.second })
            val ratio = big.first().first / normal.first().first
            assertTrue("label height grows with the scale (x$ratio at $scale)", ratio > scale - 0.15f && ratio < scale + 0.15f)
        }
    }
}
