package com.example.blacksmithproject

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.MainMenu
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.model.LegacyProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TitleScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun titleOffersANewRun() {
        var started = false
        compose.setContent { BlacksmithProjectTheme { MainMenu("Era 1", LegacyProfile(), "New game", "title_new_run", enabled = true, onPrimary = { started = true }, onSettings = {}) } }
        compose.onNodeWithText("Tiny Blacksmith").assertIsDisplayed()
        compose.onNodeWithTag("title_new_run").performClick()
        assertTrue(started)
    }

    /** "End era" asks first: keeping on closes the question and ends nothing; the red confirmation ends the era once. */
    @Test
    fun endingAnEraAsksFirst() {
        var ended = 0
        compose.setContent { BlacksmithProjectTheme { MainMenu("Era 1 · Day 3", null, "Continue", "menu_continue", enabled = true, onPrimary = {}, onSettings = {}, onAbandon = { ended++ }) } }
        compose.onNodeWithTag("menu_abandon").performClick()
        compose.onNodeWithTag("menu_abandon_confirm").assertIsDisplayed()
        compose.onNodeWithText("Keep playing").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("menu_abandon_confirm").assertCountEquals(0)
        assertFalse("keeping on ends nothing", ended > 0)
        compose.onNodeWithTag("menu_abandon").performClick()
        compose.onNodeWithTag("menu_abandon_confirm").performClick()
        compose.waitForIdle()
        assertTrue("the confirmation ends the era once ($ended)", ended == 1)
        compose.onAllNodesWithTag("menu_abandon_confirm").assertCountEquals(0)
    }
}
