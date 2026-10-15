# Main menu redesign — design

Date: 2026-10-14. Branch: `review-2026-10-14`. Owner approved the design in chat with the adjustments folded in below.

## Intent

The main menu becomes an atmospheric title screen with one clear action hierarchy. The owner's night painting
(`bg_title_workshop_night`) breathes; the text and the actions stand in its dark lower part. With a run saved, one
compact summary (the day first, then the siege) leads to a dominant Continue. Without a run, New game is the primary
action and the charter choice stays inside its setup screen. Legacy, Records and Settings are visually secondary, and
the progression numbers (points, eras, upgrades, legends, lineages, the notebook) live inside those destinations, not
on the title. No stacked cards, no framed panels, no explanatory paragraphs on the title. An existing run is never
replaced by accident.

Not in scope: new art, opening straight into the workshop on launch (the menu still opens the game), the Back model
(a second Back on the menu still leaves the app, REVIEW_2026-10-14 item 6), the Loading state, the run-end screen.

## The title (`MainMenu`, `ui/App.kt`)

### Composition

- The painting fills the screen, cropped from the top (as now). A vertical gradient to `colorScheme.background`
  starts where the text begins and reaches full opacity at the foot, so every line of text sits on a ground of at
  least the contrast the theme promises for `Cream` / `CreamMuted` / `Gold` on `ForgePanel`. The gradient start is a
  composition target, not a constant: the column is bottom-aligned, so on a short screen or with enlarged text the
  text column grows upward and the gradient grows with it (it is drawn behind the column, sized to the column, not to
  a fixed fraction of the screen). The top band of the painting (roof, moon, stars) is what gets covered first.
- Target on a 2340×1080 phone at font 1.0: the painting is clear for roughly the top two thirds. At font 2.0 or on a
  short screen the column takes what it needs; the primary action is always within thumb reach because the column is
  anchored to the bottom and scrolls only when it cannot fit at all (the existing `heightIn(min = maxHeight)` +
  `verticalScroll` arrangement).
- The night reaches the screen's foot, under the navigation bar too: the gradient is drawn on a layer that spans the
  whole screen height behind the padded column, not on the column inside the navigation-bar padding.
- Width cap 420 dp on the text and action column, centred. Horizontal padding `Space.lg`.
- The Legacy · Records · Settings row is three text buttons in one row at font scale ≤ 1.0; above 1.0 (the pattern of
  `Notebook.kt:98`) the three stack one under another, each full width, so no label breaks mid-word.
- No borders, no `FramedPanel`, no dividers on the title.

### Contents, top to bottom

1. **Title word** "Tiny Blacksmith": `headlineLarge` at 40 sp, `Gold`, the existing shadow, heading semantics.
2. **Tagline** "Forge for the heroes. Hold the town." `bodyMedium`, `CreamMuted`. The only copy on the title.
3. **Summary** (varies by state, below).
4. **Primary action**: the gold plate (`PrimaryActionButton`), full column width, 52 dp+.
5. **Secondary row**: three `InlineActionButton`s in one row, evenly spaced: **Legacy · Records · Settings**
   (48 dp targets, `Gold` text). The top-right gear is removed; `menu_settings` moves to the Settings text button.
6. **Intro line** (`IntroMenuLine`): only while the guided first day is QUEUED or OFFERED, as today, directly under
   the primary action and above the secondary row. It is the one piece of onboarding guidance that stays on the
   title, because it carries an action (Skip / Resume / Dismiss). The tagline is not repeated anywhere and no other
   explanatory copy is added.
7. **Abandon run**: only while a run is in progress: `Playing`, and `ShopDay` unless its saved state is already ended
   (`state.isEnded`, the evening the forge fell); never in `RunEnded` or `Title`. Rendered as the quietest element on
   the screen: an `InlineActionButton` in `CreamMuted` (not Gold), centred, at the very foot under the secondary row.
   Tapping it opens the existing `ForgeDialog` "Abandon this run?" with "Keep playing" and "Abandon run"; text and
   test tags unchanged.

### Summary by state

| State | Lines | Primary |
|---|---|---|
| `Title`, fresh account (`points == 0 && eras.isEmpty()`) | none | "New game" |
| `Title`, with history | none on the title; the plate carries `detail = "Era N"` (`legacy.nextEra`) | "New game", detail "Era N" |
| `Playing` | **Day line** then **siege line** then optional **charter line** (below) | "Continue run" |
| `ShopDay` (an unwatched evening) | same as Playing, read from the saved `state` exactly as the day card's own summary does (`ShopDayUi.kt:399-403`): `state.day` is the morning after the unwatched evening | "Continue run" |
| `RunEnded` | "Era N has ended" (`titleLarge`, `Cream`) | "See how the era ended" |

The **day line** is the strongest element of the summary: "Day 12" in `headlineMedium`, `Cream`, with "Era 3"
after it in `titleMedium` `CreamMuted` on the same baseline ("Day 12 · Era 3", the era visibly lighter).

The **siege line** is never muted. Its wording and colour come from one place, a pure function in
`ui/MenuUi.kt` (new, app module) fed by the same arithmetic the workshop uses — `Threats.of(state, content,
config)?.daysToSiege`, which is `town.nextSiegeDay − day` — and verified against where End Day fires the siege
(`Battle.resolveSiege`: at End Day when `day == nextSiegeDay`; a guild run's `SiegeFight.resolve` on the same day):

| `daysToSiege` | Text | Style |
|---|---|---|
| `≤ 0` | "Siege tonight" | `titleMedium`, `Ember` |
| `1` | "Siege tomorrow" | `titleMedium`, `Ember` |
| `≥ 2` | "Siege in N days" | `titleMedium`, `Cream` |
| `Threats.of == null` (no factions) | line omitted | — |

"Tonight" is right because `daysToSiege == 0` means the siege resolves at this day's End Day, after trading — the same
case the workshop phrases "Siege today, after today's trading" (`ThreatUi.summary`). There is no pending-resolution
state to show: End Day resolves the siege and the save in `ShopDay` is already the morning after, with
`nextSiegeDay` moved on, so a `ShopDay` whose unwatched evening held the siege shows the *next* siege, exactly as the
day card's summary does. A JVM test pins the three bands and the day the engine actually fights on (End Day on a
state with `daysToSiege == 0` produces a siege event; on `1` it does not).

The **charter line**, only in a guild run: "Under the <charter name> charter" (`bodyMedium`, `CreamMuted`), on its
own line under the siege line. A classic run has no third line.

### Protecting a saved run

While a run exists (`Playing`, `ShopDay`, `RunEnded` before the era is begun) the title never offers "New game": the
primary is Continue (or "See how the era ended"), and the only way to a new game is through Abandon's confirmation or
the run-end screen's "Begin era". This is the existing rule; the redesign keeps it and the test for it is made
explicit (`TitleScreenTest`: with `onAbandon` set, no node "New game").

## Navigation (`TinyBlacksmithApp`)

A `rememberSaveable` enum `MenuPage { MENU, SETUP, LEGACY, RECORDS }` replaces the `setup: Boolean` flag. `menuOpen`
stays as it is. `MainMenu` gets `onLegacy` and `onRecords` callbacks beside `onSettings`. Back from SETUP, LEGACY and
RECORDS returns to MENU (`BackHandler`); the Settings sheet is unchanged (and still holds the debug `ScenarioMenu`).
The intro line keeps `vm` as today (`extra`).

The two new pages read the account's legacy profile from whichever state is current: `Title.legacy`,
`Playing.state.legacy`, `ShopDay.state.legacy`, `RunEnded.legacy`. They are available in every state, so the
secondary row never changes shape.

## Legacy (`ui/LegacyScreen.kt`, new)

Full screen, `safeDrawingPadding`, Back row at the head ("‹ Title", `BackRow`), test tag `legacy_screen`. One
`LazyColumn`:

1. Head: "Legacy" heading in `Gold`; then on one line the two counts as they were on the old title: points banked
   and eras survived (`LegacyCount`, moved from `App.kt`). If `totalPointsEarned > points`, nothing more is said.
2. "Permanent upgrades": the upgrade rows the in-run page already draws (name, `LevelDots`, description, the next
   level's preview), each with the buy button of the run-end screen (`SecondaryActionButton` "N pts" / "Max").
   - No run: enabled when `points >= cost`; otherwise disabled with content description "…, unavailable: it costs N
     points and you have M" (the run-end wording).
   - A run in progress (`Playing`, `ShopDay`): disabled, with the line "Available after this run" under the section
     header (one line, `CreamMuted`) and content description "…, unavailable: available after this run".
   - `RunEnded` before the claim: disabled, with the run-end screen's own line "Claim your legacy first" and content
     description "…, unavailable: claim your legacy first". After the claim the session accepts a purchase from the
     ended run (`GameSession.kt:279`), so the page is open exactly as between runs.
3. "Legend Board" and "Lineages": the rows the in-run page draws, unchanged; the era used for `legendUi` is
   `legacy.nextEra` when there is no run and the run's era otherwise.

To share the rows, `legacyItems(s: UiState.Playing, vm)` in `InfoPanels.kt` is split into two account-level
functions, `upgradeItems(legacy, content, config, buy: BuyState, onBuy)` and `recordItems(legacy, era, content)` (the
Legend Board and the Lineages), where `BuyState` is `Hidden` (the in-run page, as today: no buttons), `Open(busy)` or
`Closed(line)`. The in-run page keeps its head panel and calls upgrades → blessings → records in today's order;
the Legacy screen calls upgrades → records.

Buying from the title goes through `vm.buyUpgrade(id)` → `GameSession` `Op.BuyUpgrade(id, runId = null)`, which the
session already accepts when no run exists (`GameSession.kt:277-281`: `run == null` passes the check) and persists
through the same `legacyStep` as the run-end purchase. A JVM test in `:app` (session or ViewModel test, alongside the
existing ones) proves: a purchase from the Title state is saved and survives a reload; a purchase attempted while a
run is in progress is rejected (`RunNotEnded`) and changes neither the legacy nor the run; a purchase made between
runs is visible in the next run's starting state (upgrade level applied) — the engine already does this, the test
pins it. Busy state: all buy buttons disabled while `op is Working`, errors shown with the run-end screen's pattern.

## Records (`ui/RecordsScreen.kt`, new)

Full screen, Back row, test tag `records_screen`. The research notebook only: `engine.notebook(legacy.journal)` with
the METAL / AUGMENT / FAMILY tabs. No Gazette (needs a run), no FIGHTS tab (needs a run), no "Use this pairing", no
bench marking. `notebookItems(s: UiState.Playing, …)` in `Notebook.kt` is refactored so the pure part takes
`(journal, content, engine, chosen tab, onTab, bench: Set<String> = emptySet(), onUse: ((pairing) -> Unit)? = null,
fights: … = null)`; the in-run call passes what it has today, the title passes the defaults. Empty journal: the
notebook's own empty wording, no added copy.

## Test tags

Kept, same meaning: `main_menu`, `title_new_run`, `menu_continue`, `menu_abandon`, `menu_abandon_confirm`,
`menu_settings` (now the text button), `intro_menu`, `intro_menu_skip`, `intro_menu_resume`, `intro_menu_dismiss`,
`run_setup`, `setup_back`, `setup_begin`, `settings_main_menu`. New: `menu_legacy`, `menu_records`, `legacy_screen`,
`legacy_back`, `records_screen`, `records_back`, `menu_summary` (the summary column), `menu_siege` (the siege line).

Scripts that cross the menu and must still pass unchanged: `tools/emulator/intro.sh`, `smoke.sh`, `runend.sh`,
`journey.sh`, `forge.sh`, `guild.sh`, `runvariety.sh`, `scenarios.sh` (they drive the tags above by resource id).

## Verification

Tooling found on this machine: SDK at `C:\atools\sdk`, emulators `emulator-5556` and `emulator-5558` attached (5554 is
the shared one and is not used; see memory). Everything below is run, not substituted.

1. `./gradlew :core:test` — includes the new siege-band test.
2. `./gradlew :app:testDebugUnitTest` — `MenuUi` wording bands, the Legacy purchase tests, existing ViewModel tests.
3. `./gradlew :app:assembleDebug` and `installDebug` on 5556.
4. `./gradlew :app:connectedDebugAndroidTest` on 5556 — `TitleScreenTest` extended: title word shown; Legacy, Records,
   Settings reachable; New game absent with `onAbandon`; the abandon confirmation; the siege line text for 0 / 1 / N.
5. Screenshots on 5556 at font 1.0 and 2.0 for: fresh title; title with history; run in progress (far siege, siege
   tomorrow, siege tonight — via the debug scenario saves); guild run (charter line); era ended; the Legacy and
   Records screens. Judged by the owner.
6. `intro.sh` and `smoke.sh` re-run (they cross the menu); `runend.sh` (claims and begins an era through the menu).
7. Any check that cannot be run is listed in PROGRESS with the exact command and why.

## Docs and changelog

`CHANGELOG.md` `[Unreleased]`: one line for the title, one for Legacy and Records between runs, one for spending
points between runs. `docs/GDD_CHECKLIST.md` item 116 and the "Main menu" section of `docs/PROGRESS.md` rewritten;
`docs/DECISIONS.md` gets the ruling "legacy points may be spent between runs from the title; never during a run".
