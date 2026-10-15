# Main Menu Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the main menu into an atmospheric title screen with one action hierarchy (summary → dominant primary → quiet Legacy · Records · Settings row), and give the account's legacy and notebook their own screens reachable from the title.

**Architecture:** The title stays `MainMenu` in `ui/App.kt`, fed by a pure summary model in a new `ui/MenuUi.kt` whose siege wording comes from the same `Threats.of` arithmetic the workshop uses. Two new full-screen destinations (`LegacyScreen`, `RecordsScreen`) reuse the in-run rows by splitting `legacyItems` and `notebookItems` into account-level functions that take a `LegacyProfile` / `Journal` instead of `UiState.Playing`. Navigation is one `rememberSaveable` `MenuPage` enum in `TinyBlacksmithApp`. No ViewModel or core rule changes; `GameSession` already accepts `Op.BuyUpgrade` with no run.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3, BOM 2026.02.01), JUnit 4 + kotlinx-coroutines-test for `:app` JVM tests, Compose UI test (`createComposeRule`) for the instrumented title test, Gradle 9.5.

**Spec:** `docs/superpowers/specs/2026-10-14-main-menu-redesign-design.md`

## Global Constraints

- UI is an observer: it reads the save and formats it; nothing on the title computes an outcome. `daysToSiege` comes from `Threats.of(state, content, config)` (`core/.../shopday/Demand.kt:33-40`), i.e. `town.nextSiegeDay − day`.
- Test tags kept with the same meaning: `main_menu`, `title_new_run`, `menu_continue`, `menu_abandon`, `menu_abandon_confirm`, `menu_settings`, `intro_menu`, `intro_menu_skip`, `intro_menu_resume`, `intro_menu_dismiss`, `run_setup`, `setup_back`, `setup_begin`, `settings_main_menu`. New: `menu_legacy`, `menu_records`, `legacy_screen`, `legacy_back`, `records_screen`, `records_back`, `menu_summary`, `menu_siege`.
- Copy on the title: title word, tagline "Forge for the heroes. Hold the town.", the summary lines, button labels, the intro line. No other sentences.
- Siege wording: `≤0` → "Siege tonight" (`Ember`), `1` → "Siege tomorrow" (`Ember`), `≥2` → "Siege in N days" (`Cream`), no factions → no line.
- Disabled purchase during a run says "Available after this run" (content description "…, unavailable: available after this run"); on an unclaimed ended era "Claim your legacy first"; never a bare "Unavailable". A claimed `RunEnded` buys as between runs.
- Abandon run only while a run is in progress: `Playing`, and `ShopDay` unless `state.isEnded`; never in `RunEnded` or `Title`. New game is never offered while a run exists.
- Gradient spans the screen's height behind the column (night reaches the foot under the navigation bar); the Legacy · Records · Settings row stacks vertically when `LocalDensity.current.fontScale > 1f`.
- Touch targets ≥ 48 dp; the primary plate ≥ 52 dp; column width cap 420 dp; heading semantics on the title word.
- Colours from `ui/theme/Color.kt` only (`Gold`, `Cream`, `CreamMuted`, `Ember`); spacing from `Space`.
- Emulator work on `emulator-5556` (never 5554, see memory `shared-emulator`); SDK at `C:\atools\sdk`.
- Every user-visible change gets a `CHANGELOG.md` `[Unreleased]` line; docs `PROGRESS.md`, `GDD_CHECKLIST.md` (item 116), `DECISIONS.md` updated in the last task.
- Commit per task on the current branch `review-2026-10-14`; do not push.

## Review Focus

1. **A run whose unwatched evening held the siege** (`ShopDay` after the siege day): the saved `state` already has `nextSiegeDay` moved on, so the title must show the *next* siege ("Siege in 10 days"), not "tonight" — pinned in Task 1's `menuSummary` test with a state played past day 20.
2. **A world without factions** (`Threats.of == null`, e.g. the slice content): the siege line must be omitted and the day line still shown — pinned in Task 1 (`siegeLine(null)`), and `menuSummary` test on a state whose `factions` is empty.
3. **Font scale 2.0 on a short screen**: the primary must stay reachable and the siege line readable; the column scrolls rather than overflowing the painting — device screenshot in Task 5 at font 2.0, plus the `heightIn(min = maxHeight)` scroll arrangement kept in Task 4.
4. **A purchase from the title with a run in progress**: must never reach the session with an enabled button; if it did, the session rejects it (`RunNotEnded`) and both run and legacy stay — pinned in Task 2's session test.
5. **Pressing Back on Legacy or Records**: returns to the title, not out of the app, and the title's state (menuOpen) is unchanged — pinned in Task 4's instrumented test via `BackHandler` + `Espresso.pressBack()`.

---

### Task 1: The summary model and siege wording (`MenuUi.kt`)

**Files:**
- Create: `app/src/main/java/com/example/blacksmithproject/ui/MenuUi.kt`
- Create: `app/src/test/java/com/example/blacksmithproject/MenuUiTest.kt`
- Create: `core/src/test/kotlin/com/tinyblacksmith/core/SiegeTimingTest.kt`
- Modify: `core/src/test/kotlin/com/tinyblacksmith/core/ScenarioSaves.kt:139-143` (add a "siege_tomorrow" scenario after `wall_death`)

**Interfaces:**
- Consumes: `Threats.of(state, content, config): Threat?` (`core/shopday/Demand.kt`), `GameEngine.content`, `GameEngine.config`, `GameState.day/era/guild/town`, `ContentCatalog.guild?.charters` (`CharterDef.id`, `.name`).
- Produces:
  ```kotlin
  @Immutable data class SiegeLine(val text: String, val urgent: Boolean)
  sealed interface MenuSummary {
      data object None : MenuSummary
      data class NextEra(val era: Int) : MenuSummary
      data class Run(val day: Int, val era: Int, val siege: SiegeLine?, val charter: String?) : MenuSummary
      data class Ended(val era: Int) : MenuSummary
  }
  fun siegeLine(daysToSiege: Int?): SiegeLine?
  fun GameEngine.menuSummary(state: GameState): MenuSummary.Run
  fun titleSummary(legacy: LegacyProfile): MenuSummary   // None on a fresh account, NextEra otherwise
  ```

- [ ] **Step 1: Write the failing JVM tests**

`app/src/test/java/com/example/blacksmithproject/MenuUiTest.kt`:

```kotlin
package com.example.blacksmithproject

import com.example.blacksmithproject.ui.MenuSummary
import com.example.blacksmithproject.ui.menuSummary
import com.example.blacksmithproject.ui.siegeLine
import com.example.blacksmithproject.ui.titleSummary
import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.EraSummary
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuUiTest {
    private val engine = GameEngine()

    private fun endDay(s: GameState) = (engine.handle(s, Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))) as CommandOutcome.Accepted).state

    /** A smith who never forges, played to the morning of [day]. */
    private fun morning(day: Int): GameState {
        var s = engine.newRun(LegacyProfile(), 42L)
        while (s.day < day && !s.isEnded) s = endDay(s)
        return s
    }

    @Test
    fun siegeWordingBands() {
        assertEquals("Siege tonight", siegeLine(0)!!.text)
        assertTrue(siegeLine(0)!!.urgent)
        assertEquals("Siege tonight", siegeLine(-1)!!.text)
        assertEquals("Siege tomorrow", siegeLine(1)!!.text)
        assertTrue(siegeLine(1)!!.urgent)
        assertEquals("Siege in 2 days", siegeLine(2)!!.text)
        assertFalse(siegeLine(2)!!.urgent)
        assertEquals("Siege in 9 days", siegeLine(9)!!.text)
        assertNull("no factions, no line", siegeLine(null))
    }

    @Test
    fun runSummaryReadsTheSaveAsTheWorkshopDoes() {
        val s = morning(1)
        val summary = engine.menuSummary(s)
        assertEquals(1, summary.day)
        assertEquals(1, summary.era)
        assertEquals("Siege in ${s.town.nextSiegeDay - s.day} days", summary.siege!!.text)
        assertNull("a classic run has no charter line", summary.charter)
    }

    @Test
    fun theSiegeDayItselfSaysTonightAndTheMorningAfterSaysTheNextOne() {
        val siegeDay = morning(1).town.nextSiegeDay
        val eve = morning(siegeDay - 1)
        assertEquals("Siege tomorrow", engine.menuSummary(eve).siege!!.text)
        val day = morning(siegeDay)
        assertEquals("Siege tonight", engine.menuSummary(day).siege!!.text)
        val after = endDay(day)
        if (!after.isEnded) {
            val next = engine.menuSummary(after).siege!!
            assertFalse("the save after the siege night already points at the next siege", next.urgent)
            assertEquals("Siege in ${after.town.nextSiegeDay - after.day} days", next.text)
        }
    }

    @Test
    fun noFactionsNoSiegeLine() {
        val s = morning(1).copy(factions = emptyMap(), siege = null)
        assertNull(engine.menuSummary(s).siege)
    }

    @Test
    fun titleSummaryHidesTheEraOnAFreshAccount() {
        assertEquals(MenuSummary.None, titleSummary(LegacyProfile()))
        assertEquals(MenuSummary.NextEra(1), titleSummary(LegacyProfile(points = 5)))
        val one = LegacyProfile(eras = listOf(EraSummary(era = 1, daysSurvived = 3, points = 0)))
        assertEquals(MenuSummary.NextEra(2), titleSummary(one))
    }
}
```

If `EraSummary`'s constructor differs, open `core/.../model/Model.kt` and use its real parameter names; the assertion is on `nextEra = eras.size + 1`.

`core/src/test/kotlin/com/tinyblacksmith/core/SiegeTimingTest.kt` (pins when End Day fights the siege, which the wording relies on):

```kotlin
package com.tinyblacksmith.core

import com.tinyblacksmith.core.engine.Command
import com.tinyblacksmith.core.engine.CommandOutcome
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.CommandId
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.shopday.Threats
import kotlin.test.Test
import kotlin.test.assertEquals

class SiegeTimingTest {
    private val engine = GameEngine()
    private fun endDay(s: GameState) = (engine.handle(s, Command.EndDay(CommandId("${s.runId.value}:day${s.day}"))) as CommandOutcome.Accepted).state

    @Test
    fun theSiegeIsFoughtAtEndDayOfTheDayWhereDaysToSiegeIsZero() {
        var s = engine.newRun(LegacyProfile(), 42L)
        val first = s.town.nextSiegeDay
        while (Threats.of(s, engine.content, engine.config)!!.daysToSiege > 1) s = endDay(s)
        assertEquals(1, Threats.of(s, engine.content, engine.config)!!.daysToSiege)
        s = endDay(s)
        assertEquals("the night before the siege fights nothing", first, s.town.nextSiegeDay)
        assertEquals(0, Threats.of(s, engine.content, engine.config)!!.daysToSiege)
        s = endDay(s)
        assertEquals("the siege night moves the next siege on by the interval", first + engine.config.siegeInterval, s.town.nextSiegeDay)
    }
}
```

Check the test framework the core module uses (`core/src/test/kotlin/.../ConstantsTest.kt` imports) and match its `Test`/`assertEquals` imports.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.blacksmithproject.MenuUiTest" :core:test --tests "com.tinyblacksmith.core.SiegeTimingTest"`
Expected: the app test fails to compile (`siegeLine` unresolved); the core test passes or fails only on its own assertions. If the core assertion fails, the spec's "tonight" wording is wrong — stop and report, do not change the wording to fit.

- [ ] **Step 3: Write `MenuUi.kt`**

```kotlin
package com.example.blacksmithproject.ui

import androidx.compose.runtime.Immutable
import com.tinyblacksmith.core.engine.GameEngine
import com.tinyblacksmith.core.model.GameState
import com.tinyblacksmith.core.model.LegacyProfile
import com.tinyblacksmith.core.shopday.Threats

/** The siege as the title says it; [urgent] when it is tonight or tomorrow (said in ember, never muted). */
@Immutable
data class SiegeLine(val text: String, val urgent: Boolean)

/** What stands between the tagline and the primary action, by state. */
sealed interface MenuSummary {
    /** A fresh account: nothing. */
    data object None : MenuSummary
    /** Between runs with history: the era the plate's detail line names. */
    data class NextEra(val era: Int) : MenuSummary
    /** A saved run: the day first, the siege, and the charter in a guild run. */
    data class Run(val day: Int, val era: Int, val siege: SiegeLine?, val charter: String?) : MenuSummary
    data class Ended(val era: Int) : MenuSummary
}

/**
 * The same count the workshop shows (`Threats.of`: `nextSiegeDay − day`), in the title's words. Zero is tonight because
 * End Day fights the siege on the day they are equal (`Battle.resolveSiege`), after the day's trading.
 */
fun siegeLine(daysToSiege: Int?): SiegeLine? = when {
    daysToSiege == null -> null
    daysToSiege <= 0 -> SiegeLine("Siege tonight", urgent = true)
    daysToSiege == 1 -> SiegeLine("Siege tomorrow", urgent = true)
    else -> SiegeLine("Siege in $daysToSiege days", urgent = false)
}

/** The saved run as the title summarises it. Reads the state; computes nothing the workshop does not. */
fun GameEngine.menuSummary(state: GameState): MenuSummary.Run = MenuSummary.Run(
    day = state.day, era = state.era,
    siege = siegeLine(Threats.of(state, content, config)?.daysToSiege),
    charter = state.guild?.charterId?.let { id -> content.guild?.charters?.firstOrNull { it.id == id }?.name },
)

/** Between runs: the era to come, or nothing on an account with no points and no eras behind it. */
fun titleSummary(legacy: LegacyProfile): MenuSummary =
    if (legacy.points == 0 && legacy.eras.isEmpty()) MenuSummary.None else MenuSummary.NextEra(legacy.nextEra)
```

- [ ] **Step 4: Add the "siege_tomorrow" scenario save**

In `core/src/test/kotlin/com/tinyblacksmith/core/ScenarioSaves.kt`, after the `wall_death` scenario (line 143), add:

```kotlin
        Scenario(
            "siege_tomorrow", "The eve of a siege",
            "Open the main menu: the summary says \"Siege tomorrow\" in ember. Press End Day: no siege tonight.",
            "Played: BALANCED_FAIR, seed 2, stopped on the morning of day 19 (the day before the siege).",
        ) { morning(2, Policy.BALANCED_FAIR, 19) },
```

Check `Scenario`'s constructor in the same file for the defaults of `endDay`/`card`/`ownLegacy` (leave them default). Then run `./gradlew :core:scenarios` to regenerate `app/src/debug/assets/scenarios`. `tools/emulator/scenarios.sh` checks an explicit list (lines 110-114); add after the `wall_death` line:

```bash
load siege_tomorrow && shot siege_tomorrow_1_planning
```

(`load` and `shot` are the script's own helpers; the title's wording is judged from Task 5's screenshots, not here.)

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.blacksmithproject.MenuUiTest" :core:test --tests "com.tinyblacksmith.core.SiegeTimingTest" :core:scenarios`
Expected: PASS; the scenarios task writes a new `siege_tomorrow.json` (check `git status app/src/debug/assets/scenarios`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/blacksmithproject/ui/MenuUi.kt app/src/test/java/com/example/blacksmithproject/MenuUiTest.kt core/src/test/kotlin/com/tinyblacksmith/core/SiegeTimingTest.kt core/src/test/kotlin/com/tinyblacksmith/core/ScenarioSaves.kt app/src/debug/assets/scenarios
git commit -m "Title summary model: the day, the siege in the workshop's count, the charter; siege timing pinned

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: Legacy between runs (`LegacyScreen`, shared account rows, purchase proof)

**Files:**
- Modify: `app/src/main/java/com/example/blacksmithproject/ui/InfoPanels.kt:395-473` (split `legacyItems`)
- Create: `app/src/main/java/com/example/blacksmithproject/ui/LegacyScreen.kt`
- Modify: `app/src/test/java/com/example/blacksmithproject/GameSessionTest.kt` (two tests appended)
- Modify: `app/src/main/java/com/example/blacksmithproject/ui/App.kt:121-128` (move `LegacyCount` out)

**Interfaces:**
- Consumes: `LegacyProfile`, `ContentCatalog.upgrades` (`id`, `name`, `description`, `maxLevel`, `costPerLevel`), `Legacy.preview(id, level, content, config)`, `legendUi(entry, content, era)`, `LevelDots`, `SectionHeader`, `Secondary`, `BackRow`, `SecondaryActionButton`, `GameViewModel.buyUpgrade(UpgradeId)`, `GameViewModel.engine`.
- Produces:
  ```kotlin
  sealed interface BuyState {
      data object Hidden : BuyState                   // the in-run Records page: levels only
      data class Open(val busy: Boolean) : BuyState    // between runs, and a claimed ended era
      data class Closed(val line: String) : BuyState   // "Available after this run" / "Claim your legacy first"
  }
  fun LazyListScope.upgradeItems(legacy: LegacyProfile, content: ContentCatalog, config: BalanceConfig, buy: BuyState, onBuy: (UpgradeId) -> Unit)
  fun LazyListScope.recordItems(legacy: LegacyProfile, era: Int, content: ContentCatalog)   // Legend Board + Lineages
  @Composable fun LegacyCount(value: Int, label: String, modifier: Modifier = Modifier)   // moved from App.kt, now public
  @Composable fun LegacyScreen(legacy: LegacyProfile, era: Int, buy: BuyState, vm: GameViewModel, onBack: () -> Unit)
  ```

- [ ] **Step 1: Write the failing session tests**

Append to `GameSessionTest.kt` inside the class (the helpers `repo`, `session`, `storedLegacy`, `storedRun`, `liveRepo`, `ended` exist above):

```kotlin
    @Test
    fun anUpgradeBoughtFromTheTitleWithNoRunIsStoredAndStartsTheNextEra() = sessionTest {
        val repo = repo(run = null, legacy = LegacyProfile(points = 40))
        val session = session(repo)
        session.load()
        assertTrue(session.run(Op.BuyUpgrade(LaunchContent.UPG_WALLS, runId = null)) is Result.Done)
        assertEquals(1, storedLegacy(repo).upgradeLevel(LaunchContent.UPG_WALLS))
        assertEquals(40 - 8, storedLegacy(repo).points)
        assertEquals("what is shown is what is stored", storedLegacy(repo), session.snapshot.value?.legacy)
        // A fresh session over the same store sees the purchase, and the era begun from it starts with the upgrade.
        val again = session(repo)
        again.load()
        assertEquals(1, again.snapshot.value?.legacy?.upgradeLevel(LaunchContent.UPG_WALLS))
        assertTrue(again.run(Op.BeginEra(7L, afterRunId = null)) is Result.Done)
        assertEquals(1, storedRun(repo).legacy.upgradeLevel(LaunchContent.UPG_WALLS))
    }

    @Test
    fun anUpgradeAttemptedDuringARunIsRejectedAndChangesNothing() = sessionTest {
        val repo = liveRepo()
        val session = session(repo)
        session.load()
        val runBefore = storedRun(repo)
        val legacyBefore = storedLegacy(repo)
        val result = session.run(Op.BuyUpgrade(LaunchContent.UPG_WALLS, runBefore.runId))
        assertTrue("$result", result is Result.Rejected && (result as Result.Rejected).error is GameError.RunNotEnded)
        assertEquals(runBefore, storedRun(repo))
        assertEquals(legacyBefore, storedLegacy(repo))
    }
```

Check the imports already in the file: `GameError` (`com.tinyblacksmith.core.engine.GameError`), `Result` (`GameSession.Result`), `LaunchContent`; add any missing. If `Op.BuyUpgrade`'s parameter is not named `runId`, use positional arguments. The first test needs `UPG_WALLS` to cost 8 at level 0 as the existing tests assume (`40 - 8`).

- [ ] **Step 2: Run the tests to verify they fail or pass as expected**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.blacksmithproject.GameSessionTest"`
Expected: both new tests PASS already (the session supports this today) — they are the pin the spec asks for. If the first one fails, the title purchase is not supported by the session and the plan stops here for a decision.

- [ ] **Step 3: Split `legacyItems` into the account rows**

In `InfoPanels.kt`, replace lines 416-473 (from `SectionHeader("Permanent upgrades")` inside the head item through the end of the lineages block) so that `legacyItems` becomes:

```kotlin
fun LazyListScope.legacyItems(s: UiState.Playing, vm: GameViewModel) {
    val content = vm.engine.content
    val legacy = s.state.legacy
    item(key = "legacy_head") {
        Column {
            // The account at a glance: the era on one side, the banked points as the one large number on the other.
            FramedPanel(modifier = Modifier.fillMaxWidth().padding(top = Space.sm)) {
                Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    Column(Modifier.weight(1f)) {
                        Text("Era ${s.state.era}", style = MaterialTheme.typography.titleLarge, color = Gold, modifier = Modifier.semantics { heading() })
                        Secondary("Claim legacy points when an era ends. Keep them for future eras.")
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${legacy.points}", style = MaterialTheme.typography.headlineMedium, color = Gold)
                        Text("legacy points banked", style = MaterialTheme.typography.labelSmall, color = CreamMuted, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 96.dp))
                    }
                }
            }
            if (s.state.pendingBlessingOffer.isNotEmpty()) {
                PrimaryActionButton("Choose a blessing", vm::reopenBlessingOffer, Modifier.fillMaxWidth().padding(top = Space.sm))
            }
        }
    }
    upgradeItems(legacy, content, vm.engine.config, BuyState.Hidden) {}
    if (s.state.blessings.isNotEmpty()) {
        item(key = "blessings_head") { SectionHeader("Active blessings") }
        itemsIndexed(s.state.blessings, key = { i, b -> "blessing_${i}_${b.id.value}" }) { _, b ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {}) {
                Sprites.blessing(b.id)?.let { PixelImage(it, 32.dp, description = null) }
                Column {
                    Text(content.blessing(b.id).name, style = MaterialTheme.typography.titleSmall)
                    Secondary("Until day ${b.expiresDay}")
                }
            }
        }
    }
    recordItems(legacy, s.state.era, content)
}

/** Who may buy on a legacy page: nobody (levels only), the smith between runs or after the claim, or nobody yet, with the line that says why. */
sealed interface BuyState {
    data object Hidden : BuyState
    data class Open(val busy: Boolean) : BuyState
    data class Closed(val line: String) : BuyState
}

/**
 * The permanent upgrades as rows of a lazy list, with a buy button when [buy] allows one. Shared by the in-run Records
 * page (levels only) and the title's Legacy screen.
 */
fun LazyListScope.upgradeItems(legacy: LegacyProfile, content: ContentCatalog, config: BalanceConfig, buy: BuyState, onBuy: (UpgradeId) -> Unit) {
    item(key = "upgrades_head") {
        Column {
            SectionHeader("Permanent upgrades")
            if (buy is BuyState.Closed) Secondary(buy.line, Modifier.padding(bottom = Space.xs).testTag("legacy_closed"))
        }
    }
    itemsIndexed(content.upgrades, key = { _, u -> "upgrade_${u.id.value}" }) { i, u ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val level = legacy.upgradeLevel(u.id)
        val cost = u.costPerLevel.getOrNull(level)
        Row(Modifier.fillMaxWidth().padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            // The name and its level share a line, so every track starts at the same edge whatever its number of levels.
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(u.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    LevelDots(level, u.maxLevel)
                }
                Secondary(u.description)
                Text(com.tinyblacksmith.core.legacy.Legacy.preview(u.id, level + 1, content, config)?.text ?: "Fully upgraded.", style = MaterialTheme.typography.bodySmall, color = Cream, modifier = Modifier.padding(top = 2.dp))
            }
            if (buy !is BuyState.Hidden) {
                // A greyed button says why: the page's own line (the run is not over, the claim is not made), or the points are short.
                val blocked = when {
                    cost == null -> null
                    buy is BuyState.Closed -> buy.line.lowercase()
                    legacy.points < cost -> "it costs $cost points and you have ${legacy.points}"
                    else -> null
                }
                SecondaryActionButton(
                    cost?.let { "$it pts" } ?: "Max", { onBuy(u.id) },
                    Modifier.padding(start = Space.sm).testTag("legacy_buy_${u.id.value}").semantics {
                        contentDescription = when { cost == null -> "${u.name} at maximum level"; blocked != null -> "Buy ${u.name} level ${level + 1}, unavailable: $blocked"; else -> "Buy ${u.name} level ${level + 1} for $cost points" }
                    },
                    enabled = cost != null && blocked == null && (buy as? BuyState.Open)?.busy != true,
                )
            }
        }
    }
}

/** The Legend Board and the Lineages as rows of a lazy list. [era]: the run's era, or the era to come between runs, for how old a legend is. */
fun LazyListScope.recordItems(legacy: LegacyProfile, era: Int, content: ContentCatalog) {
    item(key = "legends_head") {
        Column {
            SectionHeader("Legend Board")
            if (legacy.legendBoard.isEmpty()) Secondary("No legendary histories recorded yet.", Modifier.padding(vertical = Space.xs))
        }
    }
    itemsIndexed(legacy.legendBoard, key = { i, _ -> "legend_$i" }) { i, entry ->
        val legend = remember(entry, era) { legendUi(entry, content, era) }
        Column(Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().padding(horizontal = 12.dp, vertical = Space.sm).testTag("legend_$i")) {
            Text(legend.head, style = MaterialTheme.typography.titleSmall, color = Gold)
            legend.lines.forEachIndexed { j, line ->
                // An entry older than the record of its make: said as a note in its own voice, not as a property.
                if (j == 0 && legend.lostToTime) Text("◆ $line", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = CreamMuted, modifier = Modifier.testTag("legend_lost_$i"))
                else Secondary(line)
            }
        }
    }
    item(key = "lineages_head") {
        Column {
            SectionHeader("Lineages")
            if (legacy.lineages.isEmpty()) Secondary("No family lines recorded yet.", Modifier.padding(vertical = Space.xs))
        }
    }
    itemsIndexed(legacy.lineages, key = { i, _ -> "lineage_$i" }) { i, it ->
        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.fillMaxWidth().padding(vertical = Space.sm).semantics(mergeDescendants = true) {}) {
            Text(it.heroName, style = MaterialTheme.typography.titleSmall)
            Secondary("Era ${it.era} · ${it.deed}")
        }
    }
}
```

Add imports: `com.tinyblacksmith.core.config.BalanceConfig`, `com.tinyblacksmith.core.content.ContentCatalog`, `com.tinyblacksmith.core.model.UpgradeId`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.platform.testTag`. The in-run page keeps today's order: upgrades, then the blessings, then the Legend Board and the Lineages.

- [ ] **Step 4: Move `LegacyCount` and write `LegacyScreen.kt`**

Cut `LegacyCount` (App.kt lines 121-128) and paste it, now `fun` not `private fun`, into the new file:

```kotlin
package com.example.blacksmithproject.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.LegacyProfile

/** One count of the legacy: the number over what it counts, read as one. */
@Composable
fun LegacyCount(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = Gold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * The account's legacy from the title: points banked and eras survived, the permanent upgrades (bought here between
 * runs; "Available after this run" while one is saved), the Legend Board and the Lineages. Back returns to the title.
 */
@Composable
fun LegacyScreen(legacy: LegacyProfile, era: Int, buy: BuyState, vm: GameViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("legacy_screen"), horizontalAlignment = Alignment.CenterHorizontally) {
        BackRow("Title", onBack, Modifier.fillMaxWidth().testTag("legacy_back"))
        LazyColumn(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = Space.md), contentPadding = PaddingValues(bottom = Space.lg)) {
            item(key = "legacy_title") {
                Column {
                    Text("Legacy", style = MaterialTheme.typography.headlineMedium, color = Gold, modifier = Modifier.semantics { heading() })
                    Row(Modifier.fillMaxWidth().padding(top = Space.md)) {
                        LegacyCount(legacy.points, "Legacy points", Modifier.weight(1f))
                        LegacyCount(legacy.eras.size, "Eras survived", Modifier.weight(1f))
                    }
                }
            }
            upgradeItems(legacy, vm.engine.content, vm.engine.config, buy, vm::buyUpgrade)
            recordItems(legacy, era, vm.engine.content)
        }
    }
}
```

`BackRow` draws "‹  Title". Keep `App.kt` compiling by leaving its (now unused) `LegacyCount` call sites until Task 4 rewrites `MainMenu`; if the compiler complains about the removed private function, temporarily keep the import-free call working by having App.kt use the public one (same name, same package — nothing to change).

- [ ] **Step 5: Build and run the app tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (no screen test covers the new file yet; the compile is the check).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/blacksmithproject/ui/InfoPanels.kt app/src/main/java/com/example/blacksmithproject/ui/LegacyScreen.kt app/src/main/java/com/example/blacksmithproject/ui/App.kt app/src/test/java/com/example/blacksmithproject/GameSessionTest.kt
git commit -m "Legacy screen: the account's upgrades, legends and lineages from the title, bought between runs

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: Records between runs (`RecordsScreen`, the notebook on a journal)

**Files:**
- Modify: `app/src/main/java/com/example/blacksmithproject/ui/Notebook.kt:75-136, 184-212`
- Modify: `app/src/main/java/com/example/blacksmithproject/ui/InfoPanels.kt:317` (`SignatureLadder` takes a nullable `onUse`)
- Create: `app/src/main/java/com/example/blacksmithproject/ui/RecordsScreen.kt`

**Interfaces:**
- Consumes: `GameEngine.notebook(journal)`, `GameEngine.fightNotes(state)`, `BookTab`, `PairingRow`, `AffinityHint`, `signatureUi`, `SignatureLadder`, `Journal` (`com.tinyblacksmith.core.model.Journal`).
- Produces:
  ```kotlin
  fun LazyListScope.notebookItems(
      journal: Journal, engine: GameEngine, chosen: BookTab, onTab: (BookTab) -> Unit,
      fights: FightNotesUi? = null, bench: Set<String> = emptySet(),
      onUse: ((String) -> Unit)? = null, onUseRecipe: ((Command.Forge) -> Unit)? = null, onTry: (() -> Unit)? = null,
  )
  fun LazyListScope.notebookItems(s: UiState.Playing, vm: GameViewModel, chosen: BookTab, onTab: (BookTab) -> Unit)  // unchanged signature, delegates
  @Composable fun RecordsScreen(journal: Journal, vm: GameViewModel, onBack: () -> Unit)
  ```

- [ ] **Step 1: Refactor `notebookItems`**

Replace lines 75-136 of `Notebook.kt` with:

```kotlin
fun LazyListScope.notebookItems(s: UiState.Playing, vm: GameViewModel, chosen: BookTab, onTab: (BookTab) -> Unit) {
    val bench = with(s.draft) {
        setOfNotNull(
            if (coreId != null && augmentId != null) com.tinyblacksmith.core.model.Journal.coreAugmentKey(coreId, augmentId) else null,
            if (augmentId != null && familyId != null) com.tinyblacksmith.core.model.Journal.augmentFamilyKey(augmentId, familyId) else null,
        )
    }
    notebookItems(s.state.legacy.journal, vm.engine, chosen, onTab, fights = vm.engine.fightNotes(s.state), bench = bench, onUse = vm::usePairing, onUseRecipe = vm::useRecipe, onTry = vm::tryUntried)
}

/**
 * The notebook on a journal alone: what the title's Records shows between runs. [fights], [bench] and the three actions
 * belong to a workbench; without them there is no Fight notes tab, no "On the workbench" mark, no "Use" and no "Try a
 * new pairing".
 */
fun LazyListScope.notebookItems(
    journal: Journal, engine: GameEngine, chosen: BookTab, onTab: (BookTab) -> Unit,
    fights: FightNotesUi? = null, bench: Set<String> = emptySet(),
    onUse: ((String) -> Unit)? = null, onUseRecipe: ((Command.Forge) -> Unit)? = null, onTry: (() -> Unit)? = null,
) {
    val book = engine.notebook(journal)
    // Fight notes are a page of a guild run, and of a journal that already holds some. A tab remembered from there falls back to the first.
    val tabs = BookTab.entries.filter { it != BookTab.FIGHTS || fights != null }
    val tab = chosen.takeIf { it in tabs } ?: BookTab.METAL
    item(key = "book_head") {
        // COPY VERBATIM from the current Notebook.kt lines 90-111: the head panel and the tab row (they use book, tabs, tab, onTab only)
    }
    if (tab == BookTab.FIGHTS && fights != null) {
        fightNoteItems(fights)
        return
    }
    val pairings = when (tab) { BookTab.METAL -> book.metal; BookTab.WEAPON -> book.weapon; BookTab.CLUES, BookTab.FIGHTS -> emptyList() }
    if ((tab == BookTab.CLUES && book.clues.isEmpty()) || (tab != BookTab.CLUES && pairings.isEmpty())) {
        // COPY VERBATIM from the current Notebook.kt lines 119-124: the empty page (uses tab only)
    }
    items(pairings, key = { "journal_${it.key}" }) { p -> PairingRow(p, onBench = p.key in bench, art = pairingArt(p, engine), onUse = onUse?.let { use -> { use(p.key) } }) }
    if (tab == BookTab.CLUES) items(book.clues, key = { "journal_$it" }) { key ->
        Column(Modifier.fillMaxWidth().padding(top = Space.sm).forgeRow().padding(horizontal = Space.sm)) {
            AffinityHint(journal, engine.content, key)
            signatureUi(journal, key, engine.content, engine.config)?.let { SignatureLadder(it, onUse = onUseRecipe) }
        }
    }
    if (onTry != null) item(key = "book_try") {
        SecondaryActionButton("Try a new pairing", onTry, Modifier.fillMaxWidth().padding(top = Space.md).testTag("book_try"), detail = "Selects ingredients without spending anything.")
    }
}
```

Copy the elided blocks verbatim from the current file (they reference `book`, `tabs`, `tab`, `onTab` only). Then:
- `pairingArt(p: PairingUi, vm: GameViewModel)` → `pairingArt(p: PairingUi, engine: GameEngine)` using `engine.content` (line 186).
- `PairingRow(..., onUse: () -> Unit)` → `onUse: (() -> Unit)?`; the trailing `TextButton` becomes `if (onUse != null) TextButton(onClick = onUse, ...)`.
- `SignatureLadder(sig, onUse: (Command.Forge) -> Unit, …)` in `InfoPanels.kt:317` → `onUse: ((Command.Forge) -> Unit)?`; read the body (lines 326-333) and guard its "Use this recipe" button with `if (onUse != null)`.
- Imports in `Notebook.kt`: `com.tinyblacksmith.core.engine.Command`, `com.tinyblacksmith.core.engine.GameEngine`, `com.tinyblacksmith.core.model.Journal`. Check `usePairing`'s parameter type on `GameViewModel` (`String` key expected) and `useRecipe`'s (`Command.Forge`); match them.

- [ ] **Step 2: Write `RecordsScreen.kt`**

```kotlin
package com.example.blacksmithproject.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.blacksmithproject.GameViewModel
import com.example.blacksmithproject.ui.theme.Gold
import com.example.blacksmithproject.ui.theme.Space
import com.tinyblacksmith.core.model.Journal

/** The research notebook from the title: the account's journal, which every era shares. Nothing here needs a run. */
@Composable
fun RecordsScreen(journal: Journal, vm: GameViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var tab by rememberSaveable { mutableStateOf(BookTab.METAL) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("records_screen"), horizontalAlignment = Alignment.CenterHorizontally) {
        BackRow("Title", onBack, Modifier.fillMaxWidth().testTag("records_back"))
        LazyColumn(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = Space.md), contentPadding = PaddingValues(bottom = Space.lg)) {
            item(key = "records_title") { Text("Records", style = MaterialTheme.typography.headlineMedium, color = Gold, modifier = Modifier.semantics { heading() }) }
            notebookItems(journal, vm.engine, tab, onTab = { tab = it })
        }
    }
}
```

- [ ] **Step 3: Build and run the app tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS. If a test referenced `pairingArt(p, vm)` or `SignatureLadder` with a non-null lambda it still compiles (a lambda is a valid nullable argument).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/blacksmithproject/ui/Notebook.kt app/src/main/java/com/example/blacksmithproject/ui/InfoPanels.kt app/src/main/java/com/example/blacksmithproject/ui/RecordsScreen.kt
git commit -m "Records screen: the notebook on the account's journal from the title, without a workbench

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: The title itself and its navigation (`MainMenu`, `TinyBlacksmithApp`)

**Files:**
- Modify: `app/src/main/java/com/example/blacksmithproject/ui/App.kt:62-186`
- Modify: `app/src/androidTest/java/com/example/blacksmithproject/TitleScreenTest.kt`

**Interfaces:**
- Consumes: `MenuSummary`, `SiegeLine`, `menuSummary`, `titleSummary` (Task 1); `LegacyScreen`, `BuyState` (Task 2); `RecordsScreen` (Task 3); `IntroMenuLine`, `RunSetup`, `SettingsSheet`, `PrimaryActionButton`, `InlineActionButton`, `ForgeDialog`.
- Produces:
  ```kotlin
  enum class MenuPage { MENU, SETUP, LEGACY, RECORDS }
  @Composable fun MainMenu(
      summary: MenuSummary, primary: String, primaryTag: String, enabled: Boolean,
      onPrimary: () -> Unit, onLegacy: () -> Unit, onRecords: () -> Unit, onSettings: () -> Unit,
      onAbandon: (() -> Unit)? = null, extra: @Composable () -> Unit = {},
  )
  ```

- [ ] **Step 1: Rewrite the instrumented title test (it will not compile until Step 3)**

Replace `TitleScreenTest.kt` with:

```kotlin
package com.example.blacksmithproject

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.blacksmithproject.ui.MainMenu
import com.example.blacksmithproject.ui.MenuSummary
import com.example.blacksmithproject.ui.SiegeLine
import com.example.blacksmithproject.ui.theme.BlacksmithProjectTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TitleScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun titleOffersANewRunAndTheThreeQuietDestinations() {
        val pressed = mutableListOf<String>()
        compose.setContent {
            BlacksmithProjectTheme {
                MainMenu(MenuSummary.None, "New game", "title_new_run", enabled = true, onPrimary = { pressed += "new" }, onLegacy = { pressed += "legacy" }, onRecords = { pressed += "records" }, onSettings = { pressed += "settings" })
            }
        }
        compose.onNodeWithText("Tiny Blacksmith").assertIsDisplayed()
        compose.onNodeWithTag("title_new_run").performClick()
        compose.onNodeWithTag("menu_legacy").performClick()
        compose.onNodeWithTag("menu_records").performClick()
        compose.onNodeWithTag("menu_settings").performClick()
        assertEquals(listOf("new", "legacy", "records", "settings"), pressed)
        compose.onNodeWithTag("menu_abandon").assertDoesNotExist()
    }

    @Test
    fun aSavedRunShowsTheDayFirstTheSiegeAndNoNewGame() {
        var abandoned = false
        compose.setContent {
            BlacksmithProjectTheme {
                MainMenu(MenuSummary.Run(day = 12, era = 3, siege = SiegeLine("Siege tomorrow", urgent = true), charter = "Ironhand"), "Continue run", "menu_continue", enabled = true, onPrimary = {}, onLegacy = {}, onRecords = {}, onSettings = {}, onAbandon = { abandoned = true })
            }
        }
        compose.onNodeWithText("Day 12", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("menu_siege").assertIsDisplayed()
        compose.onNodeWithText("Siege tomorrow").assertIsDisplayed()
        compose.onNodeWithText("Under the Ironhand charter").assertIsDisplayed()
        compose.onNodeWithText("New game").assertDoesNotExist()
        compose.onNodeWithTag("menu_abandon").performClick()
        compose.onNodeWithText("Abandon this run?").assertIsDisplayed()
        compose.onNodeWithTag("menu_abandon_confirm").performClick()
        assertTrue(abandoned)
    }

    @Test
    fun anEndedEraHasNoAbandonAndSaysSo() {
        compose.setContent {
            BlacksmithProjectTheme {
                MainMenu(MenuSummary.Ended(3), "See how the era ended", "menu_continue", enabled = true, onPrimary = {}, onLegacy = {}, onRecords = {}, onSettings = {})
            }
        }
        compose.onNodeWithText("Era 3 has ended").assertIsDisplayed()
        compose.onNodeWithTag("menu_abandon").assertDoesNotExist()
    }

    @Test
    fun aSavedRunThatAlreadyEndedOffersNoAbandon() {
        // The ShopDay of the evening the forge fell: a run summary, but no onAbandon (App.kt passes null when state.isEnded).
        compose.setContent {
            BlacksmithProjectTheme {
                MainMenu(MenuSummary.Run(day = 21, era = 1, siege = null, charter = null), "Continue run", "menu_continue", enabled = true, onPrimary = {}, onLegacy = {}, onRecords = {}, onSettings = {})
            }
        }
        compose.onNodeWithText("Day 21", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("menu_abandon").assertDoesNotExist()
        compose.onNodeWithTag("menu_siege").assertDoesNotExist()
    }

    @Test
    fun aBusySessionDisablesThePrimary() {
        compose.setContent {
            BlacksmithProjectTheme {
                MainMenu(MenuSummary.NextEra(4), "New game", "title_new_run", enabled = false, onPrimary = {}, onLegacy = {}, onRecords = {}, onSettings = {})
            }
        }
        compose.onNodeWithTag("title_new_run").assertIsNotEnabled()
        compose.onNodeWithText("Era 4").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Rewrite `MainMenu` and the title navigation in `App.kt`**

Replace the `MainMenu` function (lines 130-186) with:

```kotlin
/**
 * Main menu: the game's first screen. The owner's painting of the smithy at night fills it and is left alone for as
 * much of the screen as the text allows; the column is anchored to the foot, and the night deepens behind it (a
 * gradient the height of the column, not of the screen, so a short screen or large text darkens more of the painting
 * rather than putting words over the sky). In order: the name, its one line, the summary ([summary]: the day first,
 * then the siege in the workshop's count, then the charter), one primary action, the intro line while the guided day is
 * waiting to be decided ([extra]), then Legacy · Records · Settings as quiet text, and "Abandon run" quieter still at
 * the foot while a run is saved ([onAbandon]; a confirmation follows). Large text scrolls.
 */
@Composable
fun MainMenu(
    summary: MenuSummary, primary: String, primaryTag: String, enabled: Boolean,
    onPrimary: () -> Unit, onLegacy: () -> Unit, onRecords: () -> Unit, onSettings: () -> Unit,
    onAbandon: (() -> Unit)? = null, extra: @Composable () -> Unit = {},
) {
    var confirmAbandon by remember { mutableStateOf(false) }
    val night = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().testTag("main_menu")) {
        // A painting, not a sprite: drawn smooth, cropped from the top so the roof and the sky stay.
        Image(painterResource(R.drawable.bg_title_workshop_night), contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
        // The scroll column spans the whole screen (the navigation bar included) so the night behind the text reaches the
        // screen's foot; only the content is inset from the bar. The gradient is sized to the column, not to the screen:
        // a short screen or large text darkens more of the painting rather than putting words over the sky.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val scroll = rememberScrollState()
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll).heightIn(min = maxHeight),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom,
            ) {
                Column(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color.Transparent, 0.22f to night.copy(alpha = 0.72f), 0.55f to night.copy(alpha = 0.94f), 1f to night))
                        .navigationBarsPadding().padding(horizontal = Space.lg).padding(top = 72.dp, bottom = Space.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Tiny Blacksmith", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, lineHeight = 46.sp, shadow = Shadow(Color.Black, Offset(0f, 2f), 10f)), color = Gold, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().semantics { heading() },
                    )
                    Text("Forge for the heroes. Hold the town.", style = MaterialTheme.typography.bodyMedium, color = CreamMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.xs))
                    MenuSummaryLines(summary, Modifier.padding(top = Space.lg).widthIn(max = 420.dp).fillMaxWidth())
                    Column(Modifier.padding(top = Space.md).widthIn(max = 420.dp).fillMaxWidth()) {
                        PrimaryActionButton(primary, onPrimary, Modifier.fillMaxWidth().testTag(primaryTag), enabled, detail = (summary as? MenuSummary.NextEra)?.let { "Era ${it.era}" })
                        extra()
                        // Three words in one row; enlarged text stacks them so no label breaks mid-word (the notebook's tabs do the same).
                        if (LocalDensity.current.fontScale > 1f) Column(Modifier.fillMaxWidth().padding(top = Space.sm)) {
                            InlineActionButton("Legacy", onLegacy, Modifier.fillMaxWidth().testTag("menu_legacy"))
                            InlineActionButton("Records", onRecords, Modifier.fillMaxWidth().testTag("menu_records"))
                            InlineActionButton("Settings", onSettings, Modifier.fillMaxWidth().testTag("menu_settings"))
                        } else Row(Modifier.fillMaxWidth().padding(top = Space.sm), horizontalArrangement = Arrangement.SpaceEvenly) {
                            InlineActionButton("Legacy", onLegacy, Modifier.testTag("menu_legacy"))
                            InlineActionButton("Records", onRecords, Modifier.testTag("menu_records"))
                            InlineActionButton("Settings", onSettings, Modifier.testTag("menu_settings"))
                        }
                        if (onAbandon != null) TextButton(
                            onClick = { confirmAbandon = true }, enabled = enabled, shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.textButtonColors(contentColor = CreamMuted),
                            modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).testTag("menu_abandon"),
                        ) { Text("Abandon run") }
                    }
                }
            }
        }
    }
    // A dialog is its own window: it does not inherit the root's resource-id exposure that the emulator scripts rely on.
    if (confirmAbandon && onAbandon != null) ForgeDialog(
        "Abandon this run?", onDismiss = { confirmAbandon = false },
        actions = {
            InlineActionButton("Keep playing", { confirmAbandon = false })
            InlineActionButton("Abandon run", { confirmAbandon = false; onAbandon() }, Modifier.testTag("menu_abandon_confirm"))
        },
    ) {
        Text("Ends this run permanently. You earn no legacy points, and its weapons and heroes won't be recorded. Your recipe notes stay.")
    }
}

/** The summary: the day as the strongest line, the siege never muted (ember when it is tonight or tomorrow), the charter under them. */
@Composable
private fun MenuSummaryLines(summary: MenuSummary, modifier: Modifier = Modifier) {
    when (summary) {
        MenuSummary.None, is MenuSummary.NextEra -> Unit
        is MenuSummary.Ended -> Text("Era ${summary.era} has ended", style = MaterialTheme.typography.titleLarge, color = Cream, textAlign = TextAlign.Center, modifier = modifier.testTag("menu_summary").semantics { heading() })
        is MenuSummary.Run -> Column(modifier.testTag("menu_summary"), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = MaterialTheme.typography.headlineMedium.fontSize, color = Cream)) { append("Day ${summary.day}") }
                    withStyle(SpanStyle(fontSize = MaterialTheme.typography.titleMedium.fontSize, color = CreamMuted)) { append("  ·  Era ${summary.era}") }
                },
                style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() },
            )
            summary.siege?.let { Text(it.text, style = MaterialTheme.typography.titleMedium, color = if (it.urgent) Ember else Cream, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.xs).testTag("menu_siege")) }
            summary.charter?.let { Text("Under the $it charter", style = MaterialTheme.typography.bodyMedium, color = CreamMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.xs)) }
        }
    }
}
```

Imports to add in `App.kt`: `androidx.compose.ui.platform.LocalDensity`, `androidx.compose.material3.TextButton`, `androidx.compose.material3.ButtonDefaults`, `androidx.compose.ui.text.SpanStyle`, `androidx.compose.ui.text.buildAnnotatedString`, `androidx.compose.ui.text.withStyle`, `com.example.blacksmithproject.ui.theme.Ember`; remove `Icon`, `IconButton`, `safeDrawingPadding`, `size`, `LegacyProfile` if no longer used. The gradient's 72 dp top padding is the band of transparent-to-night above the name; keep it so the first line never sits on an unshaded part of the painting.

Then replace lines 73-77 and 99-114 of `TinyBlacksmithApp`:

```kotlin
    // The main menu opens the game on a cold start; a restored process returns to where the player was.
    var menuOpen by rememberSaveable { mutableStateOf(true) }
    var menuSettings by remember { mutableStateOf(false) }
    // Where the menu stands: the title, the run setup (the charter) under "New game", or one of its two quiet pages.
    var page by rememberSaveable { mutableStateOf(MenuPage.MENU) }
    val reducedMotion by vm.settings.reducedMotion.collectAsStateWithLifecycle(initialValue = false)
    val leaveMenu = { page = MenuPage.MENU; menuOpen = false }
    val toTitle = { page = MenuPage.MENU }
```

and inside the `Surface`:

```kotlin
            when (val s = ui) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.LoadFailed -> LoadFailedScreen(s.failure, s.working, onRetry = vm::retry, onStartOver = vm::startOver)
                else -> {
                    // The account's legacy from whichever state holds it; the two quiet pages read it in every state.
                    val legacy = when (s) { is UiState.Title -> s.legacy; is UiState.Playing -> s.state.legacy; is UiState.ShopDay -> s.state.legacy; is UiState.RunEnded -> s.legacy; else -> null }
                    val era = when (s) { is UiState.Playing -> s.state.era; is UiState.ShopDay -> s.state.era; is UiState.RunEnded -> s.run.era; else -> legacy?.nextEra ?: 1 }
                    val busy = s.op is GameSession.Status.Working
                    val inMenu = menuOpen || s is UiState.Title
                    // Who may buy on the Legacy page: between runs and after the claim, the smith; during a run or before the claim, nobody, with the line that says why.
                    val buy = when {
                        s is UiState.Title || (s is UiState.RunEnded && s.claimed) -> BuyState.Open(busy)
                        s is UiState.RunEnded -> BuyState.Closed("Claim your legacy first")
                        else -> BuyState.Closed("Available after this run")
                    }
                    when {
                        inMenu && page == MenuPage.LEGACY && legacy != null -> LegacyScreen(legacy, era, buy, vm, onBack = toTitle)
                        inMenu && page == MenuPage.RECORDS && legacy != null -> RecordsScreen(legacy.journal, vm, onBack = toTitle)
                        s is UiState.Title -> if (page == MenuPage.SETUP) RunSetup(vm, s.legacy.nextEra, !busy, onBack = toTitle, onBegin = { leaveMenu(); vm.newRun() })
                            else MainMenu(
                                titleSummary(s.legacy), "New game", "title_new_run", !busy,
                                // Content without charters has nothing to set up: the era begins at once.
                                onPrimary = { if (vm.engine.content.guild?.charters.isNullOrEmpty()) { leaveMenu(); vm.newRun() } else page = MenuPage.SETUP },
                                onLegacy = { page = MenuPage.LEGACY }, onRecords = { page = MenuPage.RECORDS }, onSettings = { menuSettings = true },
                                extra = { IntroMenuLine(vm, Modifier.padding(top = Space.md)) },
                            )
                        s is UiState.Playing -> if (menuOpen) MainMenu(vm.engine.menuSummary(s.state), "Continue run", "menu_continue", !s.busy, leaveMenu, { page = MenuPage.LEGACY }, { page = MenuPage.RECORDS }, { menuSettings = true }, onAbandon = vm::abandonRun)
                            else screens.SaveableStateProvider(play!!) { WorkshopScreen(s, vm, onMainMenu = { menuOpen = true }) }
                        // The evening the forge fell is a ShopDay over an ended state: nothing to abandon there.
                        s is UiState.ShopDay -> if (menuOpen) MainMenu(vm.engine.menuSummary(s.state), "Continue run", "menu_continue", true, leaveMenu, { page = MenuPage.LEGACY }, { page = MenuPage.RECORDS }, { menuSettings = true }, onAbandon = if (s.state.isEnded) null else vm::abandonRun)
                            else screens.SaveableStateProvider(play!!) { ShopDayHost(s, vm, onMainMenu = { menuOpen = true }) }
                        s is UiState.RunEnded -> if (menuOpen) MainMenu(MenuSummary.Ended(s.run.era), "See how the era ended", "menu_continue", true, leaveMenu, { page = MenuPage.LEGACY }, { page = MenuPage.RECORDS }, { menuSettings = true })
                            else RunEndScreen(s, vm)
                    }
                }
            }
```

Add at file level: `enum class MenuPage { MENU, SETUP, LEGACY, RECORDS }`. Remove the old `setup` flag and its uses. Before this change only `Playing` offered Abandon; the spec adds it to a `ShopDay` whose run is still in progress (`abandonRun` reads the session's run, so it works there) and withholds it on the ended one. `menuSummary` on a `ShopDay` state reads the save exactly as the day card's summary does.

- [ ] **Step 3: Compile and run the JVM tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (existing ViewModel/screen tests do not compose `MainMenu`).

- [ ] **Step 4: Build, install and run the instrumented title test on 5556**

Run (PowerShell): `$env:ANDROID_SERIAL='emulator-5556'; ./gradlew :app:installDebug :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.blacksmithproject.TitleScreenTest`
Expected: 4 tests PASS. If the Gradle property form for one class is rejected, run the full `connectedDebugAndroidTest` (longer) and read `app/build/reports/androidTests/connected/debug/index.html`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/blacksmithproject/ui/App.kt app/src/androidTest/java/com/example/blacksmithproject/TitleScreenTest.kt
git commit -m "Title screen: the painting left alone, the day and the siege over one Continue, Legacy · Records · Settings as quiet text

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Device verification, scripts, docs and changelog

**Files:**
- Modify: `CHANGELOG.md:12` (the existing "A new title screen" line under `[Unreleased]`)
- Modify: `docs/GDD_CHECKLIST.md:116`
- Modify: `docs/PROGRESS.md:693-698` ("Main menu" section)
- Modify: `docs/DECISIONS.md` (append one ruling)
- Create: screenshots under the scratchpad (not committed) and a short note of them in PROGRESS

**Interfaces:** none produced; consumes the finished title.

- [ ] **Step 1: Full JVM suites**

Run: `./gradlew :core:test :app:testDebugUnitTest`
Expected: PASS. Paste the summary lines into PROGRESS in Step 6.

- [ ] **Step 2: Full instrumented suite on 5556**

Run (PowerShell): `$env:ANDROID_SERIAL='emulator-5556'; ./gradlew :app:connectedDebugAndroidTest`
Expected: PASS. `NavigationFlowTest`, `ShopDayPersistenceTest`, `IntroStripTest` drive `menu_continue` / `menu_settings` / `intro_menu*` and must pass unchanged. A failure that names a moved tag is a plan bug: fix the tag, not the test.

- [ ] **Step 3: Screenshots of the eight states at two font scales**

Use the debug Scenarios menu (Settings → Scenarios (debug)) to load `wall_death` (siege tonight), `siege_tomorrow` (siege tomorrow), `hall_lesson` (siege far), any `GuildScenarios` entry (charter line), and the fresh install for the fresh title. For the title with history and the ended era, run `tools/emulator/runend.sh <dir>` (passive run to defeat, claim, next era) and screenshot its menu stops. Capture with:

```bash
# Git Bash. Never redirect exec-out in PowerShell: it re-encodes the PNG bytes.
ADB=/c/atools/sdk/platform-tools/adb.exe; export ANDROID_SERIAL=emulator-5556
OUT="$TMP/menu_shots"; mkdir -p "$OUT"
shot() { "$ADB" shell screencap -p /sdcard/shot.png && "$ADB" pull /sdcard/shot.png "$OUT/$1.png" >/dev/null; }
"$ADB" shell settings put system font_scale 1.0; shot menu_<state>_1.0
"$ADB" shell settings put system font_scale 2.0; shot menu_<state>_2.0
"$ADB" shell settings put system font_scale 1.0
```

For each: open the Legacy and Records screens too. Check by eye and note in PROGRESS: the siege line readable on its ground at both scales; "Siege tonight"/"tomorrow" in ember; the primary within the lower third at 1.0 and still fully visible without scrolling at 2.0 on the Pixel-class AVD; no text over an unshaded part of the painting. Send the screenshots to the owner (memory: they want them as work progresses).

- [ ] **Step 4: The three scripts that cross the menu**

Run (Git Bash): `ADB=/c/atools/sdk/platform-tools/adb.exe ANDROID_SERIAL=emulator-5556 bash tools/emulator/smoke.sh "$TMP/menu_smoke"`, then `intro.sh`, then `runend.sh`, each to its own directory.
Expected: each ends with its own success line (read the script's tail for what it prints). A script failing on a tag it taps is a plan bug (see Task 4 Step 2's tag list).

- [ ] **Step 5: Changelog and docs**

`CHANGELOG.md`: replace the "A new title screen" paragraph with:

```
- **A new title screen.** The owner's painting of the smithy at night fills the main menu and is left alone for most of the screen. With a run saved, the day stands first, then the siege in the workshop's own count ("Siege in 4 days"; "Siege tomorrow" and "Siege tonight" in ember) and the charter in a guild run, over one "Continue run". Without a run, "New game" (with the era to come) is the one action. Legacy, Records and Settings are three quiet words under it; "Abandon run" sits quieter still at the foot while a run is saved and asks before it acts. The settings gear is gone.
- **Legacy and Records from the title.** Legacy shows the points banked and eras survived, the permanent upgrades, the Legend Board and the Lineages; legacy points can be spent there between runs (during a run the buttons say "Available after this run"). Records opens the research notebook without a run.
```

`docs/GDD_CHECKLIST.md` line 116: rewrite the item to the new menu (title with summary, New game / Continue, Legacy and Records pages, Abandon with confirmation, Settings as text) and mark it with the checks run here.

`docs/PROGRESS.md` "Main menu" section: replace with what shipped, the exact commands run with their results (test counts, the scripts' success lines, the screenshot list and what was judged), and a **Deferred checks** list naming any command that could not be run and why (if everything ran, say "none").

`docs/DECISIONS.md`: append "Legacy points may be spent from the title between runs (the session already allowed `BuyUpgrade` with no run); never during a run, where the Legacy page says 'Available after this run'. Rationale: points left unspent after an abandoned or claimed run were unreachable until the next defeat."

- [ ] **Step 6: Commit**

```bash
git add CHANGELOG.md docs/GDD_CHECKLIST.md docs/PROGRESS.md docs/DECISIONS.md
git commit -m "Main menu redesign: changelog, checklist item 116, PROGRESS with the checks run, the between-runs spending ruling

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```
