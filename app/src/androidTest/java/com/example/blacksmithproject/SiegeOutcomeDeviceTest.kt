package com.example.blacksmithproject

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blacksmithproject.data.ShopDaySpeed
import com.example.blacksmithproject.ui.shopday.Beat
import com.example.blacksmithproject.ui.shopday.ShopDayScreen
import com.example.blacksmithproject.ui.shopday.ShopDayUiModel
import com.example.blacksmithproject.ui.shopday.toUi
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import com.tinyblacksmith.core.content.GuildContent
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.model.SiegeVerdict
import com.tinyblacksmith.core.shopday.AftermathKind
import com.tinyblacksmith.core.shopday.ShopDay
import com.tinyblacksmith.core.sim.GuildBot
import com.tinyblacksmith.core.sim.GuildPolicy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T3 on a device: the siege outcome card over real days (a classic hold and a classic defeat, a guild's clean hold, costly
 * hold and breach), at 320 and 360 dp and font scale 1.0, 1.5 and 2.0. Nothing spans past the screen's width, the outcome
 * and the cost receipt are on the first screenful, every action can be reached and acts, and the day's own account is
 * still behind its one button. Screenshots go to the app's external files, `t3-evidence`.
 */
@RunWith(AndroidJUnit4::class)
class SiegeOutcomeDeviceTest {
    @get:Rule
    val compose = createComposeRule()

    private var size by mutableStateOf(DpSize(360.dp, 700.dp))
    private var font by mutableStateOf(1f)
    private var model by mutableStateOf<ShopDayUiModel?>(null)
    private var position by mutableIntStateOf(0)
    /** A new value starts the card over: its scroll position and fold are its own state. */
    private var round by mutableIntStateOf(0)
    private var wall = mutableListOf<Boolean>()
    private var closes = 0
    private var nexts = 0
    private val evidence = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "t3-evidence").apply { mkdirs() }
    private val matrix = listOf(320, 360).flatMap { w -> listOf(1f, 1.5f, 2f).map { w to it } }
    private val hasTextOrClick = SemanticsMatcher("text or click") {
        it.config.getOrNull(SemanticsProperties.Text) != null || it.config.contains(SemanticsActions.OnClick)
    }

    private fun shot(name: String) = File(evidence, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }

    private fun guildSiege(want: SiegeVerdict): ShopDayFixtures.Day {
        val e = ShopDayFixtures.engine
        for (policy in listOf(GuildPolicy.GREEDY_DIVER, GuildPolicy.SYNERGY, GuildPolicy.DEFENCE_FIRST, GuildPolicy.PASSIVE)) for (seed in 1L..6L) {
            val bot = GuildBot(e, policy)
            var s = e.newRun(LegacyProfile(), seed, charterId = GuildContent.TOWNS_LAST_HOPE).let { r ->
                (e.handle(r, Command.ChooseOpening(r.guild!!.openingOffer.first().defId)) as CommandOutcome.Accepted).state
            }
            while (!s.isEnded && s.day <= 24) {
                val out = e.handle(bot.planDay(s), Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))) as CommandOutcome.Accepted
                val r = out.resolution!!
                if (r.siege?.fight != null && r.siege!!.verdict == want) return ShopDayFixtures.Day(e, seed, r, out.state, ShopDay.script(r, out.state, e.content, e.config))
                s = out.state
            }
        }
        error("no guild siege with verdict $want on seeds 1..6")
    }

    private fun classic(kind: AftermathKind): ShopDayFixtures.Day {
        val day = ShopDayFixtures.find("aftermath_${kind.name.lowercase()}")
        assertNotNull("the engine produced no ${kind.name} day", day)
        return day!!
    }

    private fun host(m: ShopDayUiModel, at: Int) {
        model = m; position = at
        compose.setContent {
            BlacksmithProjectTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size) then DeviceConfigurationOverride.FontScale(font)) {
                    val shown = model!!
                    key(round) {
                        ShopDayScreen(
                            model = shown, position = position, speed = ShopDaySpeed.TAP, reducedMotion = true, paused = false,
                            onNext = { nexts++ }, onBack = {}, onSkipDay = {}, onSpeedChange = {},
                            onOpenHero = { _, _ -> }, onOpenBlade = { _, _ -> }, onChooseBlessing = {}, onOpenGazette = {},
                            onWatchFight = {}, onClose = { closes++ }, onWatchSiege = { wall += it },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertInside(where: String) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        for (n in compose.onAllNodes(hasTextOrClick, useUnmergedTree = true).fetchSemanticsNodes()) {
            val b = n.boundsInRoot
            if (b.width <= 0f || b.height <= 0f) continue
            val label = n.config.getOrNull(SemanticsProperties.Text)?.joinToString() ?: n.config.getOrNull(SemanticsProperties.TestTag) ?: "a control"
            assertTrue("$where: \"$label\" spans ${b.left}..${b.right} of ${root.width}", b.left >= -0.5f && b.right <= root.width + 0.5f)
        }
    }

    /** One case over the whole matrix; [replayable] cases have the report and the replay, the others only the round list. */
    private fun run(name: String, day: ShopDayFixtures.Day, replayable: Boolean, contribution: Boolean) {
        val m = day.script.toUi(day.state, day.engine.content, day.engine.config)
        val at = m.beats.indexOfFirst { it is Beat.Aftermath && it.card.siege != null }
        assertTrue("$name has a siege card", at >= 0)
        val siege = (m.beats[at] as Beat.Aftermath).card.siege!!
        assertEquals("$name: contribution presence", contribution, siege.contribution != null)
        host(m, at)
        for ((w, f) in matrix) {
            val where = "$name at $w dp x $f"
            wall.clear()
            compose.runOnUiThread { size = DpSize(w.dp, 700.dp); font = f; round++ }
            compose.waitForIdle()
            // The first screenful: the verdict, and (when there is one) the part the record counted.
            try { compose.onNodeWithTag("shopday_siege_outcome", useUnmergedTree = true).assertIsDisplayed() } catch (e: AssertionError) { shot("$name-$w-$f-0-FAILED"); throw AssertionError("$where: ${e.message}", e) }
            if (contribution) compose.onNodeWithTag("shopday_siege_contribution", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("shopday_siege_cost", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            assertInside(where)
            shot("$name-$w-$f-1-cost")
            // The ways in: reachable by scrolling, and each acts without closing the day.
            if (replayable) {
                compose.onNodeWithTag("shopday_siege_report").performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("shopday_watch_siege").performScrollTo().assertIsDisplayed()
                shot("$name-$w-$f-2-actions")
                compose.onNodeWithTag("shopday_siege_report").performClick()
                assertEquals(where, listOf(true), wall)
            }
            // The day's own words stay: one button opens them, whole.
            compose.onNodeWithTag("shopday_siege_account_toggle").performScrollTo().performClick()
            compose.onNodeWithTag("shopday_aftermath_text", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            assertInside("$where, account")
            shot("$name-$w-$f-3-account")
            compose.onNodeWithTag("shopday_siege_account_toggle").performScrollTo().performClick()   // folded again for the next size
            assertEquals(where, 0, closes)
        }
    }

    @Test fun aGuildsCleanHold() = run("guild-held", guildSiege(SiegeVerdict.HELD), replayable = true, contribution = true)
    @Test fun aGuildsCostlyHold() = run("guild-costly", guildSiege(SiegeVerdict.HELD_AT_A_COST), replayable = true, contribution = true)
    @Test fun aGuildsBreach() = run("guild-breach", guildSiege(SiegeVerdict.BREACHED), replayable = true, contribution = true)
    @Test fun aClassicHold() = run("classic-held", classic(AftermathKind.SIEGE_HELD), replayable = false, contribution = true)
    @Test fun aClassicDefeat() = run("classic-lost", classic(AftermathKind.SIEGE_LOST), replayable = false, contribution = true)

    /** A siege recorded before replays: no report to open, no contribution to invent, and the fight as the save keeps it under the account. */
    @Test fun anOlderRecordKeepsItsSummaryUnderTheAccount() {
        val day = guildSiege(SiegeVerdict.HELD_AT_A_COST)
        val report = day.resolution.siege!!
        val old = day.state.copy(lastResolution = day.resolution.copy(siege = report.copy(fight = report.fight!!.copy(record = 0))))
        val m = day.script.toUi(old, day.engine.content, day.engine.config)
        val at = m.beats.indexOfFirst { it is Beat.Aftermath && it.card.siege != null }
        host(m, at)
        for ((w, f) in matrix) {
            val where = "older record at $w dp x $f"
            compose.runOnUiThread { size = DpSize(w.dp, 700.dp); font = f; round++ }
            compose.waitForIdle()
            compose.onNodeWithTag("shopday_siege_outcome", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithTag("shopday_siege_cost", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("shopday_siege_contribution", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("shopday_siege_report").assertDoesNotExist()
            compose.onNodeWithTag("shopday_watch_siege").assertDoesNotExist()
            compose.onNodeWithTag("shopday_siege_account_toggle").performScrollTo().performClick()
            compose.onNodeWithTag("fight_outcome", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
            assertInside(where)
            shot("older-record-$w-$f")
        }
    }
}
