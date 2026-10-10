package com.example.blacksmithproject

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.MainMenu
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.model.LegacyProfile
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
        compose.setContent { BlacksmithProjectTheme { MainMenu("Era 1 awaits", LegacyProfile(), "New game", "title_new_run", enabled = true, onPrimary = { started = true }, onSettings = {}) } }
        compose.onNodeWithText("Tiny Blacksmith").assertIsDisplayed()
        compose.onNodeWithTag("title_new_run").performClick()
        assertTrue(started)
    }
}
