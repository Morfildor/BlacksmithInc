package com.example.blacksmithproject

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blacksmithproject.ui.*
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.model.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForgePurposeDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val engine = GameEngine()
    private val forged = engine.handle(engine.newRun(LegacyProfile(), 42), com.tinyblacksmith.core.engine.Command.Forge(ForgeMode.QUICK, com.tinyblacksmith.core.content.LaunchContent.SWORD, com.tinyblacksmith.core.content.LaunchContent.IRON, com.tinyblacksmith.core.content.LaunchContent.EMBER_RESIN, null, Risk.BALANCED)).stateOrThrow()
    private val blade = forged.storedWeapons().single()
    private val hero = forged.aliveHeroes().first()
    private var width by mutableIntStateOf(360)
    private var scale by mutableFloatStateOf(1f)
    private var case by mutableIntStateOf(0)
    private val evidence = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "batch-b").apply { mkdirs() }
    private fun shot(name: String) = File(evidence, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }

    @Test fun purposeIsBeforeStatisticsAtEveryScaleWithTruthfulDestinations() {
        val c = Commission(CommissionId("fit"), hero.id, blade.familyId, 1, 80, forged.day, forged.day + 3, CommissionStatus.ACCEPTED)
        val want = Want(blade.familyId, 1, 9999, forged.day)
        val states = listOf(forged.copy(commissions = mapOf(c.id to c)), forged.copy(commissions = mapOf(c.id to c.copy(minQuality = blade.quality + 5))), forged.copy(heroes = forged.heroes + (hero.id to hero.copy(gold = 9999, want = want))))
        val purposes = listOf(ForgePurpose(c.id, c), ForgePurpose(c.id, c.copy(minQuality = blade.quality + 5)), ForgePurpose(heroId = hero.id, want = want))
        var stored = 0
        compose.setContent {
            BlacksmithProjectTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, 800.dp)) then DeviceConfigurationOverride.FontScale(scale)) {
                    key(width, scale, case) {
                        val s = states[case]
                        ForgeResultCard(engine.itemDetail(s, blade.id)!!, "Sword of Iron and Ember Resin.", engine.forgedFor(s, blade.id, purposes[case].commissionId), true, { stored++ }, {}, Modifier.heightIn(max = 760.dp), assessment = engine.assessPurpose(s, blade.id, purposes[case]), explainRarity = false)
                    }
                }
            }
        }
        for (w in listOf(320, 360)) for (f in listOf(1f, 1.5f, 2f)) for (i in states.indices) {
            compose.runOnUiThread { width = w; scale = f; case = i }
            compose.waitForIdle()
            compose.onNodeWithTag("reveal_assessment").assertIsDisplayed()
            val assessment = compose.onNodeWithTag("reveal_assessment").getUnclippedBoundsInRoot()
            assertTrue("Assessment precedes stats", assessment.top < compose.onNodeWithText("Power").getUnclippedBoundsInRoot().top)
            compose.onNodeWithTag("reveal_purpose_expanded").assertDoesNotExist()
            shot("${listOf("commission-fit", "commission-quality-miss", "want-possible-match")[i]}-$w-$f")
            compose.onNodeWithTag("reveal_store").performScrollTo().assertIsDisplayed().performClick()
            if (i == 2) compose.onNodeWithTag("reveal_accept").assertDoesNotExist()
        }
        assertEquals(18, stored)
    }

    @Test fun longIngredientNamesAndRulesWrapAndStayDisclosedUntilRequested() {
        val a = engine.content.materials.first()
        val b = engine.content.materials.first { it.id != a.id && it.category == a.category }
        val longName = "Ancient silver from the very distant northern mountain forge"
        val longRule = "Conditional effect: waits for an ally to mark a target before dealing additional damage. ".repeat(8)
        var selected by mutableStateOf(a.id.value)
        compose.setContent {
            BlacksmithProjectTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, 800.dp)) then DeviceConfigurationOverride.FontScale(scale)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Tray(RecipeSlot.METAL, listOf(OptionUi(a.id.value, longName, 4, selected == a.id.value, detail = longRule), OptionUi(b.id.value, b.name, 0, false, blocked = "None left. Buy more in Supplies.")), engine.content, TrayChangeUi(longName, listOf(ChangeUi("Restock: 5 to 10 gold, uses 1"))), {}, {}, {}, { selected = it.id!! })
                    }
                }
            }
        }
        for (w in listOf(320, 360)) for (f in listOf(1f, 1.5f, 2f)) {
            compose.runOnUiThread { width = w; scale = f }
            compose.waitForIdle()
            compose.onNodeWithTag("forge_choices").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("forge_tray_change").assertIsDisplayed()
            shot("long-material-$w-$f")
            compose.onNodeWithTag("forge_tray_rules").performScrollTo().performClick()
            compose.onNodeWithText(longRule).performScrollTo().assertIsDisplayed()
            val bounds = compose.onNodeWithText(longRule).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.right <= compose.onRoot().fetchSemanticsNode().boundsInRoot.right)
            compose.onNodeWithTag("forge_tray_rules").performScrollTo().performClick()
            compose.onNodeWithTag("forge_choices").performScrollTo()
            compose.onNodeWithTag("forge_option_${b.id.value}").performScrollTo().performClick()
            compose.onNodeWithTag("forge_tray_blocked").performScrollTo().assertIsDisplayed()
            assertEquals(a.id.value, selected)
            // Return focus for the next configuration.
            compose.onNodeWithTag("forge_choices").performScrollTo()
            compose.onNodeWithTag("forge_option_${a.id.value}").performScrollTo().performClick()
        }
    }
}
