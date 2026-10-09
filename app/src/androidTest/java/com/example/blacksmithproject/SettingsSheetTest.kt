package com.example.blacksmithproject

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blacksmithproject.data.SettingsStore
import com.example.blacksmithproject.ui.Moment
import com.example.blacksmithproject.ui.SettingsSheet
import com.example.blacksmithproject.ui.rememberHaptics
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
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
        compose.setContent { BlacksmithProjectTheme { SettingsSheet(reduced, onReducedMotion = { reduced = it }, haptics = true, onHaptics = {}, onDismiss = {}) } }
        compose.onNodeWithText("Reduced motion").assertIsDisplayed()
        compose.onNodeWithTag("settings_reduced_motion").performClick()
        compose.waitForIdle()
        assertTrue(reduced)
        compose.onNodeWithTag("settings_version").assertIsDisplayed()
    }

    @Test
    fun theHapticsSwitchIsLabelledTogglesAndPersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SettingsStore(context)
        runBlocking { store.setHaptics(true) }
        try {
            compose.setContent {
                BlacksmithProjectTheme {
                    val scope = rememberCoroutineScope()
                    val on by store.haptics.collectAsState(initial = true)
                    SettingsSheet(false, onReducedMotion = {}, haptics = on, onHaptics = { scope.launch { store.setHaptics(it) } }, onDismiss = {})
                }
            }
            compose.onNodeWithText("Haptics").assertIsDisplayed()
            compose.onNodeWithTag("settings_haptics").assertIsOn()  // on by default
            compose.onNodeWithTag("settings_haptics").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("settings_haptics").assertIsOff()
            assertFalse(runBlocking { SettingsStore(context).haptics.first() })  // read back through a second store
            compose.onNodeWithTag("settings_haptics").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("settings_haptics").assertIsOn()
            assertTrue(runBlocking { store.haptics.first() })
        } finally {
            runBlocking { store.setHaptics(true) }
        }
    }

    @Test
    fun everyMomentMapsToAPlatformEffectWithoutError() {
        compose.setContent { val haptics = rememberHaptics(enabled = true); Moment.entries.forEach { haptics.play(it) } }
        compose.waitForIdle()
    }
}
