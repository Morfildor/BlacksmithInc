package com.example.blacksmithproject

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.TitleScreen
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
    fun titleOffersNewRunAndContinueWhenSaved() {
        var started = false
        compose.setContent { TitleScreen(UiState.Title(LegacyProfile(), hasSavedRun = true), onNewRun = { started = true }, onContinue = {}) }
        compose.onNodeWithText("Tiny Blacksmith").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsDisplayed()
        compose.onNodeWithText("Light the forge").performClick()
        assertTrue(started)
    }
}
