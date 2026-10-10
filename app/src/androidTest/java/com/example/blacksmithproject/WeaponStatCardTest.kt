package com.example.blacksmithproject

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.detail.WeaponStatCard
import com.example.blacksmithproject.ui.detail.itemDetail
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.content.AffixKind
import com.tinyblacksmith.core.content.LaunchContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.engine.stateOrThrow
import com.tinyblacksmith.core.model.ForgeMode
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.Risk
import com.tinyblacksmith.core.model.WeaponSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The item card says a blade in words and stored numbers: buffs and flaws under their headings, and a "was" only from a snapshot. */
@RunWith(AndroidJUnit4::class)
class WeaponStatCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val engine = GameEngine()
    private val good = engine.content.affixes.first { it.kind == AffixKind.BENEFICIAL }
    private val flaw = engine.content.affixes.first { it.kind == AffixKind.FLAW }
    private val forged = engine.handle(engine.newRun(LegacyProfile(), 42L), Command.Forge(ForgeMode.QUICK, LaunchContent.SWORD, LaunchContent.IRON, LaunchContent.EMBER_RESIN, null, Risk.BALANCED)).stateOrThrow()
    private val made = forged.weapons.values.single()
    private val keen = made.copy(affixes = listOf(good.id), flaws = listOf(flaw.id), condition = 100)
    private val worn = keen.copy(condition = 40)

    @Test
    fun buffsAndFlawsAreNamedUnderTheirHeadingsWithTheStoredNumbers() {
        val detail = engine.itemDetail(forged.copy(weapons = mapOf(keen.id to keen)), keen.id)!!
        assertEquals(listOf(keen.power, keen.quality, keen.condition, keen.fame), detail.stats.map { it.value })
        assertEquals("no snapshot, so nothing is told as changed", emptyList<Int>(), detail.stats.mapNotNull { it.was })
        compose.setContent { BlacksmithProjectTheme { WeaponStatCard(detail) } }
        compose.onNodeWithText(keen.name).assertIsDisplayed()
        compose.onNodeWithText("Buffs").assertIsDisplayed()
        compose.onNodeWithText(good.name, substring = true).assertIsDisplayed()
        compose.onNodeWithText("Flaws").assertIsDisplayed()
        compose.onNodeWithText(flaw.name, substring = true).assertIsDisplayed()
        compose.onNode(hasText("+") and hasText(good.name)).assertIsDisplayed()   // the signs are text, not colour
        compose.onNode(hasText("−") and hasText(flaw.name)).assertIsDisplayed()
        compose.onNode(hasText("Power") and hasText("${keen.power}")).assertIsDisplayed()
        compose.onNode(hasContentDescription("Quality ${keen.quality} of 100", substring = true)).assertIsDisplayed()
        compose.onNode(hasContentDescription("Condition 100 of 100, Sound")).assertIsDisplayed()
    }

    @Test
    fun aBladeWornSinceTheCounterShowsBothRecordedNumbers() {
        val detail = engine.itemDetail(forged.copy(weapons = mapOf(worn.id to worn)), worn.id, WeaponSnapshot.of(keen))!!
        assertEquals(100, detail.stats.first { it.label == "Condition" }.was)
        compose.setContent { BlacksmithProjectTheme { WeaponStatCard(detail) } }
        compose.onNode(hasContentDescription("Condition 40 of 100, Worn, was 100")).assertIsDisplayed()
    }
}
