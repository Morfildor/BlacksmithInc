package com.example.blacksmithproject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.SettingsSheet
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theSheetTogglesReducedMotionAndShowsTheVersion() {
        var reduced by mutableStateOf(false)
        compose.setContent { BlacksmithProjectTheme { SettingsSheet(reduced, onReducedMotion = { reduced = it }, onDismiss = {}) } }
        compose.onNodeWithText("Reduced motion").assertIsDisplayed()
        compose.onNodeWithTag("settings_reduced_motion").performClick()
        compose.waitForIdle()
        assertTrue(reduced)
        compose.onNodeWithTag("settings_version").assertIsDisplayed()
    }
}
