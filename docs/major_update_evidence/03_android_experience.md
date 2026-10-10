# 03 — Android experience: review verification and the "shop day" design

Role: Android experience reviewer, planning only. Nothing in the project was created, edited or deleted. No Gradle,
no install, no emulator input. Every status below is a source read plus historical screenshots; nothing was executed.

## 0. Basis

**Repository state.** I read the app at `4ecbac0`. While I worked `main` advanced to `77919fe` (balance v5 part 1,
hero daily life) and a merge of worktree `agent-a9c81a03eedde0748` started (conflicts in core/docs, `Dialogs.kt`
staged). App sources are byte-identical between `4ecbac0` and `77919fe`; the pending merge changes only
`ui/Dialogs.kt` (one import, so every `Dialogs.kt` line below shifts by +1 from line 59 on once it lands).
**App line numbers are `4ecbac0`; core line numbers are `77919fe`** unless marked.

**Skills.** Loaded and applied: `android-review:compose-ui`, `compose-state-and-effects`, `compose-performance`,
`kotlin-concurrency-and-flow`, `compose-animations`, `ui-craft`. I applied the SKILL.md rules; their reference
sub-files were not opened (no UI is being generated here).

**Screenshots (historical evidence, all 1080x1920, about 411 dp wide).** The project `scratchpad/` holds only
`ui_v4` and `runend`; the newer sets are in an earlier session's temp folder
`C:\Users\tuncb\AppData\Local\Temp\claude\c--Users-tuncb-Desktop-Blacksmith-Project\68c55c63-3593-47a0-b739-ed746b3c1ccb\scratchpad\`.

| Set | Captured | Build it shows | What it proves |
|---|---|---|---|
| `scratchpad/ui_v4/01..08` | 9 Oct 06:10 | 0.3.0 candidate, **six** nav items, no Home, flat-headline Gazette | forge scene strip, reveal, market, town |
| `scratchpad/runend/00..04, 10..14` | 9 Oct 06:37 | just after 0.3.0 | final Gazette with diorama, run end unclaimed/claimed, forge and Town at font 1.3 and 1.5 |
| `…/ui_v5/01..17` | 9 Oct 08:10 | 0.4.0 | price editor, Salvage/Hone/Arm, supplier, tools, ambitions |
| `…/home_v1/*`, `…/gazette_v1/*` | 9 Oct 09:15–09:56 | 0.5.0 working tree, **seven** nav items | Home dashboard, edition layout, archive, day 10 defeat |
| `…/smoke_v5/01..08` | 9 Oct 10:07 | 0.5.0 | Home day 1, market, day 1 report, resume |
| `…/recovery_v1/01..03` | 9 Oct 10:49 | `7fe7348` (current app sources) | report reopened after kill, then closed |
| `…/ui_v3/base, fs13, fs15` | 8 Oct 22:44 | 0.2.0 | forge, market, town, report at 1.0 / 1.3 / 1.5 |
| `…/extra/09..32` | 8 Oct 20:13 | before the declutter `dcc428f` | five-stat top strip, Legacy with Settings, dark mode |

**Evidence gap:** no capture exists of the seven-item navigation, Home or run end at font scale 1.3 or above, nor of
any screen at 720x1280 or a 320 dp width. Large-font evidence stops at 0.2.0 / 0.3.0 (six items).

---

## 1. Verification against current source

Status key: CONFIRMED / FIXED / PARTIAL / DISPUTED / UNVERIFIED.

### 1.1 Review section 3 (why the game is hard to follow)

| Row | Status | Evidence | Note |
|---|---|---|---|
| End Day calculates everything, then opens the paper | CONFIRMED | one command `GameEngine.kt:268-309` (visits 279, commissions 280, activities 283, siege 290); VM sets `showReport` `GameViewModel.kt:119`; `WorkshopScreen.kt:102` opens `DayReportDialog` (`Dialogs.kt:103-164`) | Only the siege has a scene (`ReplayStage`, `Dialogs.kt:127`); a sale is one bullet or lede line |
| Home is equally styled text blocks | CONFIRMED | `HomePanel.kt:43-54` emits up to eight blocks through one `HomeBlock` style (`:59-69`) | Screenshots `home_v1/home_day5*.png`: two screens of identical cards. Day 1 leads with "Outlook: grave danger · defense 58 vs raid 179" (`smoke_v5/02_home.png`) because DIRE odds count as urgent (`:40`) |
| Market puts stock before customers and requests | CONFIRMED | order in `MarketPanel.kt`: Shelves `:52`, Storage `:60`, Yesterday's customers `:64`, Commissions `:75`, Supplier `:94`, Tools `:123` | Storage is unbounded; everything after it moves down with it |
| Town omits taste and history | CONFIRMED | `HeroRow` `InfoPanels.kt:134-162` shows class, level, health, weapon, traits, gold, fame, ambition; never reads `elementTaste` (`Model.kt:84`), `loyalty` (`:85`), `guildId`/`mentorName` (`:94-95`); no row is clickable | `Heroes.describeTraits` returns names only (`Heroes.kt`, `describeTraits`) |
| Forge does not show demand | CONFIRMED (one nuance) | `ForgePanel.kt:190-217` shows integrity, days to siege and the leading faction's name; no weakness, commission or customer. The only demand hint is in a Market row (`MarketPanel.kt:143-146,176`) | Out-of-stock text sends the player to another tab (`ForgePanel.kt:342`) |
| Affix descriptions not reopenable | CONFIRMED | descriptions only in the reveal (`Dialogs.kt:85-86`); rows use names (`Labels.kt:72-73`); the row expander has none (`MarketPanel.kt:181-217`); nothing in `app/` reads `Weapon.history` | The reveal is VM-only state and is lost on process death (1.3) |
| Newspaper is a reading task | CONFIRMED | dialog renders the whole `EditionBody` (`Dialogs.kt:128-129,168-186`); `heroLines` writes a sentence per active hero (`Gazette.kt:126`) | `smoke_v5/06_gazette.png`: day 1 is seven near-identical "driven back … bare-handed" bullets |

### 1.2 F01 / F02 from the UI side

**UiState variants** (`GameViewModel.kt:36-51`): `Loading`, `Title(legacy, hasSavedRun)`,
`Playing(state, panel, draft, revealWeaponId, showReport, lastError, busy, blessingOfferDismissedDay)`,
`RunEnded(runEnd, legacy, claimed, lastError)`. There is no load-failure, save-failure or presentation state, and
only `Playing` has `busy`.

**Where the engine runs.** `viewModelScope.launch` without a dispatcher, so `engine.handle` (`:114`),
`engine.newRun` (`:82`), `claimLegacy` (`:137`), `purchaseUpgrade` (`:153`) and `closeRun` (`:73,89,103`) run on
the main thread. Only Room and the JSON encode/decode are inside `withContext(Dispatchers.IO)`
(`:67,68,80,83,88,116,136,139,143,155`; encode at `SaveStore.kt:58-59`). Composition also calls the engine on the
main thread: `siegeForecast` builds a `ResolutionContext` on every recomposition of Home and Town
(`HomePanel.kt:38`, `InfoPanels.kt:63`, `GameEngine.kt:169`). Review section 12 note: CONFIRMED. Measured cost is
small (p95 8.45 ms on the AVD, `PROGRESS.md`), so this is hygiene, not an emergency.

**F01 — CONFIRMED (source).**

| Control | Guard while a save runs | Evidence |
|---|---|---|
| End Day, Forge, List/Set price/Unlist, Salvage/Hone/Arm, Buy material/tool | disabled by `busy` | `WorkshopScreen.kt:150`, `ForgePanel.kt:269`, `MarketPanel.kt:115,134,179,199-213` |
| Accept / Decline commission, blessing choices, "List at N" in the reveal | stay enabled; the tap is silently dropped by `dispatch` (`GameViewModel.kt:111`) | `MarketPanel.kt:86-87`, `Dialogs.kt:203`, `Dialogs.kt:91` (which then closes the reveal anyway) |
| Title "Light the forge" / "Continue" | **no guard** | `App.kt:56-59`, `GameViewModel.kt:79-90` |
| Run end: Claim, each upgrade, Begin era | **no guard**; gated only on `claimed`, cost and points | `RunEndScreen.kt:61,92,102`, `GameViewModel.kt:133-167` |

- Two upgrades: `buyUpgrade` computes from the `current.legacy` captured at tap time (`:151-153`) and writes the whole
  profile (`:155`). The second writer erases the first purchase (points come back with it; it is a lost update, not
  lost currency).
- Upgrade then Begin era: `newRun` loads the legacy from Room (`:80`), may miss the in-flight upgrade, saves
  `(state, state.legacy)` (`:83`); the upgrade then lands with `saveAtomically(null, …)` (`:155`), which **deletes the
  new run** (`SaveStore.kt:40`) and sets the UI back to `RunEnded` (`:156`).
- Double claim is safe for rewards (the pure claim rejects) but can surface a spurious "already claimed" line (`:142-145`).

**F02 — CONFIRMED, and worse than the review states.** `app/` contains no `try`/`catch` and no
`CoroutineExceptionHandler` (grep). An exception in a `viewModelScope` coroutine is an uncaught crash:

- Decode failure or a newer schema (`SaveCodec.kt:44`) in `init` (`GameViewModel.kt:67-68`) crashes **on every
  launch** until app data is cleared. "Busy is not restored" is moot.
- A save failure in `dispatch` (`:116`) crashes before `_ui.update`; on relaunch the last committed state loads,
  so no unsaved result is ever shown, but there is no retry path.
- An engine invariant failure (`GameEngine.kt:367`, `check(...)`) crashes the same way.
- `android:allowBackup="true"` with untouched template rules (`AndroidManifest.xml:6-8`, `res/xml/*.xml`) lets
  Auto Backup restore Room and DataStore onto a different app version. Combined with the crash loop this is a
  release blocker until a load-failure state exists.

### 1.3 Report dismissal and process death

- **Dismissal is asynchronous — CONFIRMED.** `dismissReport` launches `settings.setDismissedReport(...)` and does not
  await it (`GameViewModel.kt:101`); the UI moves on immediately (`:102-106`). A kill in that window reopens a report
  the player already closed. It is a presentation repeat only.
- **What reopens.** `init` compares `run.lastResolution.commandId` with the stored marker (`:70`,
  `SettingsStore.kt:37`) and passes `showReport = unread` (`:74`). An ended run with an unread report opens as
  `Playing` with the dialog over a live workshop (`:73-74`).
- **Not restored:** `panel` (back to HOME), `draft` (empty), `revealWeaponId` (the reveal and its affix descriptions
  are gone), `blessingOfferDismissedDay`, `lastError`. They are plain fields of `Playing`; the VM is
  `AndroidViewModel(app)` with no `SavedStateHandle` (`:57`). `rememberSaveable` exists only for the forge step
  (`ForgePanel.kt:91`) and the row expander/price text (`MarketPanel.kt:156-157`); scroll positions and the open
  Gazette day use `remember` (`WorkshopScreen.kt:83`, `InfoPanels.kt:183`).
- **Back and outside-tap acknowledge the day.** `Dialog(onDismissRequest = vm::dismissReport)` (`Dialogs.kt:110`):
  system back or a tap beside the paper is "Begin day". `BlessingDialog` swallows back (`:197`). There is no
  `BackHandler` in the app, so back on any panel leaves the app. The review does not mention this.
- **New finding — run end is not recoverable after the claim.** `claimLegacy` saves `(null, legacy)` (`:139,143`),
  deleting the run row. A kill between Claim and Begin era relaunches to `Title` (`:72`), and upgrades can only be
  bought on `RunEndScreen` (`RunEndScreen.kt:91-95`; `LegacyPanel` is read-only, `InfoPanels.kt:218-227`). The
  player must start the next era with unspent points.
- **New finding — dead path.** `init` only produces `Title(hasSavedRun = false)` (`:72`), so `continueRun`
  (`:87-90`) and the Continue button (`App.kt:55-57`) are unreachable; `TitleScreenTest` exercises a state the app
  cannot produce.

### 1.4 Review section 8 (UI and accessibility)

| Claim | Status | Evidence |
|---|---|---|
| Forge scene is 52 dp high | CONFIRMED | `ForgePanel.kt:85`. The 96x48 scene gets integer scale 2 at that height (`Sprites.kt:242`), so the furnace and anvil are specks on tiled wall (`ui_v4/02_workshop.png`). GDD 12 locks a "detailed close-up forge" |
| Seven destinations, labels shrink to 8 sp | CONFIRMED, refined | `Panel` has seven entries (`GameViewModel.kt:24`); bar is 64 dp, labels auto-size 8–12 sp (`WorkshopScreen.kt:60-67`). At 411 dp and scale 1.0 the labels still render at 12 sp with "Legacy" touching the edge (`smoke_v5/02_home.png`). The floor is in **sp**: large-font users get no enlargement at all, and true 8 sp appears on narrow screens at default scale. At 320 dp each item is 45.7 dp wide, under the 48 dp target |
| Fixed widths | CONFIRMED | price field `width(112.dp)` (`MarketPanel.kt:193`); Forge button `width(116.dp)`, two-line cap (`ForgePanel.kt:275-276`). Already wrapping at 1.3 (`runend/10_forge_fs13.png`) |
| Non-lazy scrolling Columns | CONFIRMED | host `Column.verticalScroll` (`WorkshopScreen.kt:84`); shelves and storage `forEach` (`MarketPanel.kt:54,62`); every hero including the dead (`InfoPanels.kt:124`); every retained Gazette day, each computing `Gazette.edition` during composition (`InfoPanels.kt:185-188`); journal (`:173`). `storedWeapons()` filters and sorts the whole map three times per Market composition (`MarketPanel.kt:60-62`, `Model.kt:291`) |
| Settings live in Legacy | CONFIRMED | `InfoPanels.kt:256-266` (`extra/16_legacy.png`) |
| No audio or haptics | CONFIRMED | no `SoundPool`, `MediaPlayer`, `HapticFeedback` or `Vibrat*` in `app/` (grep); manifest has no permission |
| Percentages in upgrade text | CONFIRMED | `LaunchContent.kt:221` "10% chance per level…", `:223` "+2% exceptional forging chance…"; shown at `InfoPanels.kt:224` and `RunEndScreen.kt:89`. No other `%` in the catalog |
| Top strip contents | CONFIRMED | Day, Gold, Energy only (`WorkshopScreen.kt:169-171`). Checklist line 80 still claims debt, integrity and next siege |
| No first-run Home tip | CONFIRMED | `Tips.forPanel` returns nothing for HOME (`WorkshopScreen.kt:118`); a new run opens on HOME (`GameViewModel.kt:41`) |

### 1.5 Test inventory and missing seams

| Test | What it actually covers |
|---|---|
| `SaveStoreTest.savesAndRestoresRunAndLegacyTogether` | in-memory Room round trip of run + legacy, continuation equality from a restored state, clearing the run keeps the legacy |
| `TitleScreenTest.titleOffersNewRunAndContinueWhenSaved` | stateless `TitleScreen` with `hasSavedRun = true` (unreachable in the app, 1.3) |
| `ForgeHintTest` (2) | `AffinityHint` text, glyph and content description for unknown and understood pairings |
| `EndDayPerfTest.endDayStaysUnderTheBudgetOnDevice` | pure simulator timing, p95 under 200 ms; no persistence, no UI |
| `ExampleInstrumentedTest`, `ExampleUnitTest` | template package-name and 2+2 tests |

Six instrumented methods in five files, one local template test: review count CONFIRMED. `PROGRESS.md:80` still says
"Room … x2". Nothing covers the ViewModel, dispatch, End Day, the report, run end, process death or any panel.

**Seams missing (the repository is not injectable):**

- `GameViewModel(app: Application)` builds `GameEngine()`, `SaveStore.create(app)` and `SettingsStore(app)` itself
  (`GameViewModel.kt:57-60`); `MainActivity` uses the default factory (`MainActivity.kt:12`).
- Core already has `SaveRepository` and `InMemorySaveRepository` (`SaveCodec.kt:58,65`) but they are blocking and
  the app's `SaveStore` does not implement them (`SaveStore.kt:50`).
- Dispatchers are hard-coded (`Dispatchers.IO` ten times); the seed is `System.nanoTime()` (`:81`).
- `SettingsStore` binds a process-wide DataStore (`SettingsStore.kt:15`).
- Every panel takes `vm: GameViewModel` (`WorkshopScreen(s, vm)`, `HomePanel(s, vm)`, …), so only `TitleScreen`,
  `AffinityHint`, `EditionBody`, `ErrorDialog` and `LevelDots` can be rendered in a test without Room.
- `kotlinx-coroutines-test` is an `androidTestImplementation` only (`app/build.gradle.kts:62`); there is no
  screenshot library.

### 1.6 Build and release facts

| Fact | Value | Evidence |
|---|---|---|
| namespace / applicationId | `com.example.blacksmithproject` (both) | `app/build.gradle.kts:8,14` |
| SDKs | minSdk 24, targetSdk 37, compileSdk 37 | `:9-16` |
| Version | versionCode 5, versionName 0.5.0 | `:17-18` |
| R8 / minify | off: `optimization { enable = false }` | `:23-29` |
| Signing | no `signingConfigs`; debug only | whole file |
| Launcher icons | Android Studio template (green `#3DDC84` grid, robot foreground) | `res/drawable/ic_launcher_*.xml`, `res/mipmap-*` |
| Permissions | none declared | `AndroidManifest.xml:1-28` |
| Backup | `allowBackup="true"`, template rules | `:6-8` |
| Orientation | `screenOrientation="portrait"` | `:17` |
| App name | "Tiny Blacksmith" | `res/values/strings.xml` |
| Dependencies | Compose, Room, DataStore, coroutines, serialization; no network, ads, billing or analytics | `:39-64` |

UNVERIFIED (platform behaviour, check on a tablet AVD): with targetSdk 36 or higher the portrait lock is ignored on
displays of 600 dp and wider, and predictive back is on by default. The layout is a single column, so it should
stretch rather than break, but it has never been seen that way.

### 1.7 F03 / F04 from the UI side, and checked-entry corrections 3, 6, 9, 10

- **F03 — CONFIRMED.** Three surfaces read three sources for the same day: the dialog uses `r.events`
  (`Dialogs.kt:128`), the archive uses `st.eventsForDay(day)` with visits only when the day matches
  (`InfoPanels.kt:186-188`), Home's "Yesterday" uses `eventsForDay(day - 1)` (`HomePanel.kt:154-157`).
  `DayResolution.events` is `ctx.newEvents` (`GameEngine.kt:298`), so the dialog lacks the day's forge and listing
  events.
- **F04 — CONFIRMED.** Commission cards print `Labels.quality(c.minQuality)` (`MarketPanel.kt:82`,
  `HomePanel.kt:130`; bands at `Labels.kt:12-18`) while delivery tests `it.quality >= c.minQuality`
  (`Market.kt:162`). No candidate or eligibility is shown anywhere.
- **Related display defects the shop day must not inherit:** two different refusal vocabularies
  (`MarketPanel.kt:231-241` and `Gazette.kt:51-58`); the purchased weapon's name is read from the *final* state
  (`MarketPanel.kt:67`, `HomePanel.kt:114`); the siege stage finds heroes by full name in the final state
  (`Dialogs.kt:228`).
- **Correction 3 (resume after process death) — CONFIRMED.** World and unread report are restored; panel, draft and
  reveal are not; the marker is asynchronous (1.3).
- **Correction 6 (48 dp, font 1.5, screen reader) — CONFIRMED as too broad.** Semantics and minimum heights exist in
  code; recorded large-font evidence predates Home and the seventh tab; there is no TalkBack pass on record.
- **Correction 9 (every system has a UI surface) — CONFIRMED.** Taste, loyalty, guild, mentor, weapon history and
  affix descriptions after the reveal have no surface (1.1).
- **Correction 10 (top strip) — CONFIRMED, with history.** The claim was true until the declutter commit `dcc428f`
  (8 Oct 22:28): `extra/16_legacy.png` shows Day, Gold, Energy, Forge and Siege in the strip. The checklist entry is
  stale, not invented.

### 1.8 Worktree `agent-a9c81a03eedde0748`: what changes in the report dialog

`git diff -- app` there (now staged on `main`) touches only `ui/Dialogs.kt`:

1. imports `com.tinyblacksmith.core.model.ReplayKind`;
2. KDoc now says the field report holds "each siege and each notable fight";
3. the diorama is drawn only for a siege:
   `r.replays.firstOrNull()?.takeIf { it.kind == ReplayKind.SIEGE }?.let { ReplayStage(...) }`.

Core side of the same diff: `CombatReplay` gains `kind` (default SIEGE) and `eventId`; `DayResolution.replays`
comes from `Battle.dayReplays(ctx)` and can hold up to three expedition fights after the siege.

Consequences for this plan:

- `replays` is now mixed-kind; the aftermath must filter by `kind` and can link a fight to its event through `eventId`.
- **Risk in the merged dialog:** outcome lines are gated by one shared step counter (`Dialogs.kt:104-108,136-150`).
  With up to four replays at 700 ms a step, the last fight's outcome appears well over ten seconds late unless the
  player taps Skip, while the rounds themselves stay folded. The new aftermath must never gate outcome text on a timer.

### 1.9 Where the review is wrong or understated

1. **F02 is a crash and a launch crash loop**, not a stuck busy flag (1.2).
2. **Back and outside-tap dismiss the report as "Begin day"; back leaves the app everywhere else** (1.3). Not in the review.
3. **Run end cannot be resumed after the claim** and upgrades become unreachable (1.3). Not in the review.
4. **"Shrinks to 8 sp"** is the configured floor, not what the recorded screens show; the real failures are no
   enlargement for large-font users and sub-48 dp items on narrow screens (1.4).
5. **Top-strip checklist entry is stale since `dcc428f`**, which explains how it got ticked (1.7).
6. **Visitor cap changed after the review.** At `77919fe` the Signboard adds a customer per level
   (`Market.kt:22`, `LaunchContent.kt:256`): 4 base + 2 signboard + 2 festival (`BalanceConfig.kt:60,157`) = up to 8
   visitors a day. The "6–10 visitors" case in section A is nearly reachable today.
7. The emulator scripts are welded to visible text (`smoke.sh:14-24`; `HomePanel.kt:31-33` forbids lines that start
   with a tab name). Any navigation or copy change breaks the device loop; the review does not list this cost.

Findings in the `android-review:compose-ui` format (the ones that shape the plan):

```
[COMPOSE-STATE-01] CRITICAL  No failure state; exceptions in viewModelScope crash, decode failure loops at launch.
  Location: GameViewModel.kt:65-77,109-125   Fix: typed LoadFailed / Op.Failed states, one guarded command boundary.
[COMPOSE-STATE-02] HIGH      Authoritative operations are not serialized outside dispatch().
  Location: GameViewModel.kt:79-90,133-167   Fix: single Mutex-guarded operation queue, Op state on every UiState.
[COMPOSE-STATE-03] HIGH      UI state that must survive process death lives in a plain StateFlow.
  Location: GameViewModel.kt:39-49,57        Fix: SavedStateHandle for destination, draft, reveal, open sheet.
[COMPOSE-NAV-01]   HIGH      No BackHandler; dialog dismissal doubles as day acknowledgement.
  Location: Dialogs.kt:110, WorkshopScreen.kt (none)  Fix: explicit back per state (section A).
[COMPOSE-API-01]   HIGH      Screens take the ViewModel instead of state + callbacks; nothing is testable.
  Location: WorkshopScreen.kt:51, HomePanel.kt:36, MarketPanel.kt:46, InfoPanels.kt:45  Fix: stateless screens.
[COMPOSE-PERF-01]  MEDIUM    Unbounded lists in a scrolling Column; engine reads and sorts during composition.
  Location: WorkshopScreen.kt:84, MarketPanel.kt:54-62, InfoPanels.kt:124,185-188, HomePanel.kt:38  Fix: LazyColumn, derive UI models off the main thread.
[COMPOSE-A11Y-01]  MEDIUM    Nav labels cannot grow; seven items fall under 48 dp at 320 dp width.
  Location: WorkshopScreen.kt:60-67          Fix: four destinations, fixed label style.
[COMPOSE-A11Y-02]  LOW       Rarity glyphs are spoken as symbols where no description is set.
  Location: Labels.kt:20-26, Dialogs.kt:78   Fix: content description with the rarity word only.
```

---

## 2. Design: the shop day

Design frame (`ui-craft`): **Scene** — one player, phone in one hand, a couple of minutes at a time, has just made
and priced three blades and wants to see who takes them. **Register** — Product (recurring task; restrained colour,
motion only for state). **Dials** — visual variance 4, motion 4, density 5. **Surface job** — "show me who came to
my counter, what they did and why, then what it led to and what to do next".

Three rules hold everywhere:

1. **Compute once, then replay.** End Day is computed and saved by the core before the first frame of the sequence.
   The sequence renders a record. It dispatches no command (one exception: the blessing choice, which is a next-day
   planning command and draws no RNG, `GameEngine.kt:253-261`), writes nothing to Room and holds no `Random`.
2. **The scene is a function of `(script, position)`.** Advance, back, skip, resume and replay only change `position`.
3. **Text carries the facts; art illustrates them.** Every fact on a card is real `Text`; the scene is decorative.

### A. Screen and state machine

#### A.1 What is authoritative and what is presentation

| Data | Owner | Durability | Written when |
|---|---|---|---|
| `GameState` with `lastResolution` holding the new per-visit record | core, Room (`saves` table) | atomic with the legacy | once, at commit |
| `ShopDayScript` (ordered beats, featured choice, consequence choice, tomorrow brief) | pure core function of the saved record and the saved next-morning state | derived, never saved | recomputed on demand; same input gives the same script |
| Presentation cursor `(commandId, stage, index, acknowledged, scriptVersion)` | app, `PresentationStore` (DataStore) | non-authoritative | write-behind per visit; **awaited** for the final acknowledgement |
| Speed choice, reduced motion | `SettingsStore` | settings | on change |
| Open detail sheet, Gazette open | VM (`SavedStateHandle`) | task restore only | on change |

Cursor policy (decided): the final acknowledgement is awaited before Home is shown, which closes the review's
"asynchronous marker" gap. Writes inside the sequence are fire-and-forget; the documented worst case after a kill is
that one visit is shown again. A cursor whose `commandId` or `scriptVersion` does not match is ignored (start at the
first beat). A failed cursor write is logged and ignored; it can never block play.

#### A.2 States

| ID | State | On screen | Controls | Detail that opens | Back button / gesture | Exit | Reduced motion (also when TalkBack is on) |
|---|---|---|---|---|---|---|---|
| PL | Planning | four-destination workshop, End Day with sublabel | all planning commands | hero, item sheets | to Shop tab; on Shop: leave app | End Day | unchanged |
| CM | Committing | same screen, input locked, End Day shows "Closing the shop…" (progress only after 150 ms) | none | none | consumed | save done or failed | unchanged |
| CF | Commit failed | dialog: "Could not save the day. Nothing has changed." | **Try again**, **Keep working** | none | = Keep working | CM or PL | unchanged |
| RS | Resume prompt (cold start with an unacknowledged day) | "Day N is done and saved. Shop took X gold." | **Resume the day**, **Skip to tomorrow** | none | leave app | stored stage, or skip target | unchanged |
| SO | Shop opens | counter scene with the shelf as it stood at lock; "Day N — the shop opens · k blades on the shelf" | tap or Next; Skip day; speed chip | item (shelf snapshot) | leave app (first beat) | first visit, or QD | no timer; static |
| CA(i) | Customer arrives | portrait, name, class and level, "regular" if recorded, the weapon in hand, one need line | Next, Back, Skip day, speed | hero, equipped item | previous beat | CB or CD | CA–CT merge into one static visit card |
| CB(i) | Browse | the considered blade lifts on the shelf; "Looks at Stormglass Bow · 108 g" (+ "and 2 others") | same | considered items | previous beat | CD | merged |
| CD(i) | Decision | typed reason as an authored line with the recorded numbers; outcome "buys" / "leaves" | same | hero, item | previous beat | CT (bought) or next visit / TL / CL | merged |
| CT(i) | Transaction | receipt rows: listed price, trade-in credit (old blade returns to storage), bonus, **cash to the till**; blade leaves the shelf; till total updates | same | sold item, traded-in item | previous beat | next visit, TL or CL | merged |
| TL | Tally of the rest | "5 more came by: 1 bought (Iron Axe, 60 g) · 3 could afford nothing · 1 found nothing better", names listed | Next, Back, Skip | each named hero | previous beat | CL | static |
| CL | Shop closes | till: "Took 199 gold · 2 sales, 1 commission · 4 left" | **Continue**, Back | — | previous beat | AF, or TM if nothing to show | static |
| QD | Quiet day (replaces SO–CL) | one card, see A.6 | **Continue** | named heroes | leave app | AF or TM | identical |
| AF(k) | Aftermath | up to three consequence cards (siege first), each naming hero and blade; "N more in the Gazette" | Next, Back, **Read the Gazette**, **Watch the fight** when a replay exists | hero, item | previous card, then CL | BL, TM or FL | cards static; replay shows all rounds at once |
| AR | Fight replay overlay | existing siege diorama or text rounds; outcome line visible from the first frame | step, Skip, Close | — | closes overlay | back to AF | no stepping, no idle frames |
| FL | Forge fallen (defeat day, replaces BL/TM) | "THE FORGE HAS FALLEN", cause, days survived | **See the legacy**, Read the Gazette | — | previous card | AK then RE | static |
| BL | Blessing choice (siege held, offer pending) | the offered blessings with descriptions | one button per blessing, **Decide later** | — | = Decide later | TM (after save if chosen) | unchanged |
| TM | Tomorrow | "Day N+1": shelf k of n and storage count, **one opportunity + one reason**, next threat line | **Begin day N+1**, **Read the Gazette**, Back | hero or item named in the lead | previous state | AK | unchanged |
| GZ | Gazette overlay | full edition on paper (`EditionBody`), the complete record | scroll, Close | chips for the people and blades in the edition | closes overlay | back to caller | unchanged |
| DT | Hero or item sheet | A.2 sheets, snapshot first ("At the counter"), "Now" below when it differs | Close | the other entity | closes sheet | back to caller | unchanged |
| AK | Acknowledging | TM or FL with the button pressed | none | — | consumed | PL (Shop, day N+1) or RE | unchanged |
| RE | Run ended | existing run-end screen, now recoverable after the claim | Claim, upgrades, Begin era (all serialized, busy shown) | item for legends | leave app | new run | unchanged |

Notes:

- **Tap anywhere on the scene** advances, as does Next. The first tap during a running motion completes the motion;
  it does not also advance.
- **Auto-advance** runs only at speed 1x or 2x, only while the activity is resumed, and pauses whenever a sheet,
  the Gazette or the replay is open. Speed "Tap" disables timers. Timers never run under reduced motion or TalkBack.
- **Skip day** is always visible in the sequence and never asks for confirmation (nothing is lost; A.5).
- Planning commands do not exist in these states: the workshop is not composed, and the VM accepts planning commands
  only in `Playing`.

#### A.3 Transitions

| From | Event | Guard | To | Durable write |
|---|---|---|---|---|
| PL | End Day | `op == Idle` | CM | none yet |
| CM | engine accepted, save committed | — | SO or QD | Room: run + legacy (one transaction) |
| CM | engine rejected | — | PL with message | none |
| CM | exception (save, encode, invariant) | — | CF | none; in-memory state stays the pre-command state |
| CF | Try again | — | CM (same command ID, same input, same result) | — |
| CF | Keep working | — | PL | — |
| SO, CA…CL | Next / tap / timer | — | next beat | cursor (write-behind, at visit boundaries) |
| any beat | Back | not first beat | previous beat | none |
| SO…AF | Skip day | — | FL if defeated, else BL if an offer is pending, else TM | cursor = target stage |
| CL / QD | Continue | consequences or replays exist | AF(0) | cursor |
| CL / QD | Continue | none | BL / TM / FL as above | cursor |
| AF(last) | Next | defeated | FL | cursor |
| AF(last) | Next | offer pending | BL | cursor |
| AF(last) | Next | otherwise | TM | cursor |
| BL | choose | `op == Idle` | BL saving, then TM | Room: `ChooseBlessing` through the same boundary; `NoBlessingOffer` is treated as already chosen |
| BL | Decide later / back | — | TM | cursor |
| BL saving | exception | — | CF variant ("Could not save your choice") | none |
| TM | Begin day | — | AK | cursor.acknowledged = true (**awaited**) |
| FL | See the legacy | — | AK | same |
| AK | write done or failed | not defeated | PL, Shop tab, day N+1 | — |
| AK | write done or failed | defeated | RE | — |
| Loading | load ok, day unacknowledged | — | RS | — |
| RS | Resume | — | stored stage and index (clamped to the script) | — |
| RS | Skip to tomorrow | — | same target as Skip day | cursor |
| Loading | decode error / newer schema / I/O | — | LoadFailed (Retry; details; never overwrites the stored bytes) | none |

#### A.4 Process death at each point

| Killed during | What is durable | Relaunch shows | Gameplay effect |
|---|---|---|---|
| PL | last accepted command | PL, same day; destination, forge draft and an open reveal come back when the system restores the task (not after force-stop) | none |
| CM before the transaction commits | day N state | PL, day N. End Day again uses `runId:dayN` (`GameViewModel.kt:130`) on the same state and RNG, so the result is the one that would have been shown | none |
| CM after commit, before the first frame | day N+1 state with `lastResolution` | RS, then SO | none |
| SO…CL | cursor at the last finished visit | RS: "Resume at customer k of n" or skip | none; at most one visit repeats |
| AF / AR | cursor AFTERMATH | RS, then AF | none |
| BL, nothing chosen | offer is in `GameState` | RS, then BL | none |
| BL while the choice is saving | either committed or not | TM if committed (no offer left), else BL | choice applied exactly once |
| TM before Begin day | cursor TOMORROW | TM directly (no prompt needed) | none |
| AK | acknowledged or not | PL day N+1, or TM again | none |
| defeat day, any stage | `phase = ENDED`, cursor | RS, sequence, FL; after acknowledgement RE | none |
| RE, unclaimed / claim in flight | ended run; claim is idempotent | RE (claimed derived from `claimedRunIds`, as `GameViewModel.kt:73` already does) | reward once |
| RE, claimed, before Begin era | **ended run kept** (change: stop saving `null` at `:139,143,155`) | RE with upgrades available | fixes the gap in 1.3 |
| Begin era in flight | new run saved or not | PL day 1, or RE | none |

#### A.5 Watch = skip = resume = restart

- The sequence performs no Room write and no engine call, so the save bytes after the commit equal the save bytes at
  acknowledgement on every path that does not choose a blessing. This is the testable invariant (section E). The one
  path that does write, choosing a blessing in the sequence, is covered by its own equivalence test
  (`blessingChosenInTheSequenceEqualsChosenNextMorning`).
- The blessing step only surfaces a pending offer. Skipping past it leaves the offer in `GameState`; the Shop tab and
  the End Day sublabel already show it (`HomePanel.kt:45-47`, `WorkshopScreen.kt:142`). Choosing it in the sequence
  and choosing it first thing next morning produce the same state (`GameEngine.kt:253-261` uses the new day).
- Optional, low cost because of rule 2: **"Replay the day"** from the Shop tab's "Yesterday" block opens the same
  screen in review mode (no acknowledgement, no blessing step) while `lastResolution` is still that day.

#### A.6 Quiet days: brief and explicit

| Case (from the record) | One card | Time |
|---|---|---|
| No visitors | "No customers today. 3 blades stayed on the shelf." | one tap |
| Visitors, empty shelf | "3 customers found the shelves bare: Wren Kestrel, Hesper Pellam, Sable Brackenridge." Names open the hero sheet | one tap |
| Visitors, no purchase | feature at most one refusal per distinct reason (A.7), tally the rest, then "No sales today. 2 could afford nothing (cheapest blade 108 g) · 1 found nothing better" | 8–12 s, or one tap on Skip |

Nothing is invented: every number and name is a field of the record; a missing fact drops its clause.

#### A.7 Time budget, and scaling to 6–10 visitors

Beat lengths at 1x (PROPOSED playtest values; 2x halves them): open 1.5 s; arrive 1.2; browse 1.2; decide 1.6;
transact 1.6 (a purchase is 5.6 s, a refusal 4.0 s); tally 2.5; close 2.0; consequence card 2.0; Tomorrow waits.

| Day | Featured | Rest | 1x total | 2x |
|---|---|---|---|---|
| 3 visitors, same refusal (today's typical day 1, `smoke_v5/06_gazette.png`) | 1 | tally of 2 | 10 s | 5 s |
| 4 visitors, 1 sale, 2 distinct refusals, 2 consequences | 3 | tally of 1 | 23.6 s | 11.8 s |
| 8 visitors (signboard 2 + festival), 2 sales, 1 refusal, 2 consequences | 3 | tally of 5 | 25.2 s | 12.6 s |
| 10 visitors | 3 | tally of 7 | 25.2 s | 12.6 s |

The featured count is fixed at **3** (a presentation constant, not a balance number), so the length is flat from
six visitors up. Skip day is one tap on any day.

**Choosing the featured encounters** (pure core function, no RNG, covered by core tests; the UI only renders it):

1. Always featured: commission collections and collector purchases (distinct transaction kinds).
2. Purchases, ranked by: first sale of the run, buyer recorded as a regular, a trade-in, a bonus or premium, cash
   paid; ties by visit order.
3. Refusals, **one per distinct reason**, in this order: OVERPRICED, TOO_EXPENSIVE, NOT_SUITED, NOT_BETTER (each
   maps to a different action tomorrow). EMPTY_SHELVES and UNDECIDED are never featured.
4. Fill three slots with at least one purchase when one exists and at least one refusal when one exists, then by
   rank. When a single slot is left, a purchase beats a refusal. If the always-featured transactions alone exceed
   three, show them all (not reachable today: a commission is only offered while none is open, `Market.kt:184-186`,
   so at most one completes a day, and the collector is one event). Show the featured visits in **visit order**,
   not rank order.
5. Every other visit goes to the tally, grouped by outcome and reason, with names. Invariant: featured plus tally
   equals the recorded visits, each exactly once.

**Choosing the consequences** (same kind of function): the siege result first when there was one; then events of the
day whose subjects include a blade the smith sold or a hero who was at the counter today, ranked hero death, elite
slain, ambition fulfilled, title or legend earned, expedition won with a blade bought today, blade broken / lost /
stolen, expedition lost. At most three; the rest are counted as "N more in the Gazette". Because visits resolve
before activities in the same End Day (`GameEngine.kt:279-283`), "bought this morning, used this afternoon" is real.

**Decision lines** are authored templates over the typed reason and recorded numbers, for example TOO_EXPENSIVE:
"Could pay up to {purse + trade-in} g; the cheapest blade is {min price} g." A template whose fields are missing
falls back to the bare reason label. One vocabulary replaces the two in the app today.

### B. Navigation and hierarchy

GDD 12 locks "one portrait workshop … menu-driven with panels/drawers/overlays"; the panel list is PROPOSED, so this
restructure stays inside the GDD.

**Seven destinations become four, plus sheets.**

| Today | Becomes | Why |
|---|---|---|
| Home + Market (shelves, storage, customers, commissions) | **Shop** (start destination) | the shop is the game's home; a dashboard about the shop and the shop itself were two tabs |
| Forge | **Forge** | unchanged role |
| Market: Supplier, Workshop tools | **Supplies sheet**, opened from Forge ("Supplies", and from an out-of-stock chip) and linked from Shop | materials are forge inputs; today the Forge tells the player to change tab (`ForgePanel.kt:342`) |
| Town | **Town** | unchanged role; rows open the hero sheet |
| Journal + Gazette + Legacy | **Records**, three segments: News · Journal · Legacy. The Journal also opens as a sheet from Forge's "Journal says" | three read-mostly archives, each rarely visited |
| Settings inside Legacy | **gear in the top strip**, opening a Settings sheet (reduced motion, shop-day speed, version). Also on Title and inside the shop day | settings are not a legacy record |

- Four items are 80 dp wide at 320 dp and 103 dp at 411 dp. Labels use `labelMedium` with **no auto-size**.
  "Records" is a working label (content names are PROPOSED); the final word should be six characters or fewer.
  The bar may cap its own label scale at 1.5x when the system scale is 2.0, and grows to 80 dp tall.
- End Day stays pinned above the bar on all four destinations; its sublabel ladder (`WorkshopScreen.kt:141-147`)
  gains "Shelf is empty — n blades in storage". Bar and End Day are hidden during the shop day.
- Back: any destination returns to Shop; back on Shop leaves the app.
- Top strip keeps Day, Gold, Energy and gains the gear. Integrity and the siege stay out of the strip; they appear
  in the Shop lead area as one threat line. Fix the checklist text rather than the strip.

**What Shop leads with: one opportunity and one reason.** A single `LeadCard` with one button, produced by a pure
read-only core function (so Shop and the Tomorrow card always agree, and it is unit-tested). First match wins;
order is PROPOSED:

| # | Condition (all are reads of `GameState`) | Opportunity | Reason |
|---|---|---|---|
| 1 | day 1, nothing forged | Forge your first blade | "Heroes browse the shelf when you end the day." |
| 2 | blessing offer pending | Choose a blessing | "The town's thanks for holding the wall." |
| 3 | offered commission, due today or tomorrow | Answer {hero}'s request | "{reward} gold, due {when}" |
| 4 | accepted commission, nothing eligible, due within two days | Forge a {quality} {family} for {hero} | "{reward} gold; nothing in stock qualifies" (uses the core's own eligibility rule, F04) |
| 5 | empty shelf, stock in storage | List your blades | "{n} in storage; yesterday {k} customers found the shelves bare" |
| 6 | empty shelf, no stock | Forge something to sell | same, or "Heroes browse at dawn." |
| 7 | two or more refusals yesterday for price | Lower a price or forge a cheaper blade | "{k} could afford nothing; the cheapest blade is {p} g" |
| 8 | siege within two days and the odds are against the town | Arm the defenders | "{faction} in {d} days, weak to {element}" |
| 9 | otherwise | Forge for today's buyers | a demand fact when the core exposes one, else "{e} energy unused" |

Under the lead sits one muted threat line ("Hollowbound in 4 days · weak to sun"). The day-1 "grave danger" block
no longer opens the game.

**Order of the Shop destination** (commissions and business feedback sit above storage, which is behind a row):

1. Counter scene header: the shelf with listed blades as sprites and price tags; tap a blade for the item sheet.
2. Lead card and threat line.
3. Requests: offered (Accept / Decline) and accepted, each with a readiness line from the core
   ("Ready: Fine Sword in storage" or the exact unmet condition) and a "Forge this" shortcut that pre-fills the draft.
4. Yesterday at the counter: up to three rows (bought / left, with the reason); links "Replay the day", "Gazette".
5. Shelf rows (n of slots).
6. "Storage · 23 blades" row, opening the Storage sheet (lazy list, family filter).

**Shared detail surfaces** (modal bottom sheets, one implementation each):

| Sheet | Opened from | Content |
|---|---|---|
| Hero | counter portrait or name, tally names, aftermath cards, Yesterday rows, Town rows and champion cards, Gazette edition chips | portrait (recorded appearance key), name, line, class and level, health word, **element taste**, favoured families, traits, purse, regular or not, ambition and progress, guild and mentor, weapon in hand (opens the item sheet), "With your shop" (purchases, trade-ins, last visit and its reason), last few events. In the counter the visit-time snapshot is on top and "Now" below when it differs |
| Item | counter shelf and receipt, shelf and storage rows, the forge reveal, champion cards, hero sheet, aftermath cards, legend rows | large sprite, name and title, rarity and quality words, condition, element, **affixes and flaws with their descriptions**, recipe (core, augment, catalyst, mode, risk), forged day, suggested price, who favours it, commission eligibility, **history** newest first, current holder (opens the hero sheet). In Planning and only for stock: price editor, List / Unlist, Hone, Salvage, Arm the watch (the two destructive actions confirm). Read-only in the shop day |

Gazette lines are plain strings today. Rather than change the edition text, each edition gets a chip row built from
the subject IDs of that day's events.

**Scope split.**

| Required now | Later (the full visual redesign) |
|---|---|
| four destinations, fixed readable labels, gear for settings | bespoke frames and chrome (the V3 UI pack, the atlas's UI panels) |
| lead card; requests and feedback above storage; storage behind a sheet | painted, layered shop and town backgrounds |
| hero and item sheets | walking customers, full-body sprites (today's hero frames are 16x16; the counter uses the 64x64 portraits) |
| counter scene from existing art: wall and floor tiles, `shelf.png`, 56 px weapon sprites, 64 px portraits, a drawn counter | custom fonts, audio and haptics with their toggles |
| forge scene gets real height on the Forge destination only (at least 120 dp), no 52 dp strip | tablet and landscape layouts |
| no fixed widths; lazy lists; 48 dp targets; back handling | golden-image screenshot suite |

Material 3, the current palette and type scale, `Surface` cards and the paper Gazette all stay.

### C. Component plan

#### C.1 Core contract this plan depends on (owned by the core work, listed so the UI is buildable)

The UI needs these and must not reconstruct them from final state. Names are placeholders.

- `ShopDayRecord` on `DayResolution` (versioned): gold at open, shelf at lock (weapon snapshots with prices),
  ordered `visits`.
- Per visit: sequence; kind (BROWSE, COMMISSION, COLLECTOR); hero snapshot (id, name, **appearance key**, class,
  level, taste, traits, purse, regular flag); equipped weapon snapshot; considered and selected weapon snapshots;
  listed price; trade-in credit and the traded weapon; cash paid; bonus; **typed** reason and outcome; related
  event IDs.
- Pure functions: `ShopDay.script(resolution, stateAfter, content): ShopDayScript` (beats, featured choice,
  consequence choice, tomorrow brief), `Advice.lead(state)`, hero and item dossiers, commission eligibility.
- Today `MarketVisit` has four fields (`Model.kt:179`; 176 at the review snapshot) and a string reason.

#### C.2 State holder and data

| File | Change | Signature / content |
|---|---|---|
| `GameViewModel.kt` | changed | `class GameViewModel(repo: GameRepository, presentation: PresentationStore, settings: SettingsStore, savedState: SavedStateHandle, engine: GameEngine = GameEngine(), io: CoroutineDispatcher = Dispatchers.IO, compute: CoroutineDispatcher = Dispatchers.Default, newSeed: () -> Long = System::nanoTime) : ViewModel()` with `companion object { val Factory: ViewModelProvider.Factory }`. One `Mutex`-guarded `authoritative { }` helper used by **every** load, command, claim, upgrade, new run and acknowledgement; it reads the latest state inside the lock, runs the engine on `compute`, saves on `io`, catches everything except cancellation into `Op.Failed`. UI events: `onShopDay(event: ShopDayEvent)`, `onShop(event: ShopEvent)` |
| `GameViewModel.kt` | changed | `UiState` gains `LoadFailed(reason, detail)`, `ShopDay(state, script, pos, speed, sheet, gazetteOpen, resumed, op)`; `Title`, `Playing`, `RunEnded` gain `op: Op` (`Idle`, `Saving`, `Failed(message, retry)`); `RunEnded` keeps the ended `GameState`; `Playing` loses `showReport` and `busy`; `Panel` becomes `Dest { SHOP, FORGE, TOWN, RECORDS }` |
| `MainActivity.kt` | changed | `by viewModels { GameViewModel.Factory }` |
| `data/GameRepository.kt` | new | `interface GameRepository { suspend fun load(): LoadResult; suspend fun commit(run: GameState?, legacy: LegacyProfile) }`; `LoadResult = Ok(run, legacy) | Corrupt | Unsupported(found, supported) | Io` |
| `data/SaveStore.kt` | changed | `class SaveStore(dao) : GameRepository`; load maps decode exceptions to `LoadResult`; stored bytes are never replaced on a failed load |
| `data/PresentationStore.kt` | new | `interface PresentationStore { suspend fun read(): PresentationCursor?; suspend fun write(c: PresentationCursor); suspend fun acknowledge(commandId: String) }`; DataStore implementation in its own file "presentation"; honours the old `dismissed_report` key once |
| `data/SettingsStore.kt` | changed | adds `shopDaySpeed`; loses the report marker |

#### C.3 Composables (all stateless: data in, events out, `modifier` last)

| File | Composable | New / reuse |
|---|---|---|
| `ui/shopday/ShopDayScreen.kt` | `ShopDayScreen(state: ShopDayUi, onEvent: (ShopDayEvent) -> Unit, modifier: Modifier = Modifier)` | new; hosts scene, card, controls, sheets, Gazette overlay; owns the auto-advance `LaunchedEffect(pos, speed, paused)` gated on the resumed lifecycle; `BackHandler` |
| `ui/shopday/CounterScene.kt` | `CounterScene(shelf: List<ShelfSlotUi>, customer: CustomerUi?, focus: WeaponId?, phase: CounterPhase, reducedMotion: Boolean, modifier: Modifier = Modifier)` | new; `Canvas` in the style of `ForgeScene` (`Sprites.kt:214-274`); one `rememberTransition(phase)`; values read in the draw phase; `clearAndSetSemantics { }` |
| `ui/shopday/VisitCard.kt` | `VisitCard(visit: VisitUi, beat: VisitBeat, onHero: () -> Unit, onItem: (WeaponId) -> Unit, modifier: Modifier = Modifier)`, `ReceiptRows(receipt: ReceiptUi, modifier: Modifier = Modifier)` | new; receipt rows are separate labelled `Text` pairs |
| `ui/shopday/DayCards.kt` | `ShopOpenCard`, `TallyCard(groups, onHero)`, `ShopCloseCard(till)`, `QuietDayCard(kind, names, onHero)`, `AftermathCard(item, onHero, onItem, onWatch)`, `FallenCard(cause, days)`, `TomorrowCard(brief, onBegin, onGazette)`, `ResumePrompt(day, summary, onResume, onSkip)` | new |
| `ui/shopday/ShopDayControls.kt` | `ShopDayControls(canGoBack: Boolean, speed: Speed, showSpeed: Boolean, onNext: () -> Unit, onBack: () -> Unit, onSkip: () -> Unit, onSpeed: (Speed) -> Unit, modifier: Modifier = Modifier)` | new; Next is full width, 56 dp |
| `ui/shopday/ShopDayUi.kt` | `@Immutable` UI models and `fun ShopDayScript.toUi(pos, content): ShopDayUi` (formatting and sprite lookup only) | new |
| `ui/detail/HeroDetailSheet.kt` | `HeroDetailSheet(hero: HeroDetailUi, onItem: (WeaponId) -> Unit, onDismiss: () -> Unit)` | new (`ModalBottomSheet`) |
| `ui/detail/ItemDetailSheet.kt` | `ItemDetailSheet(item: ItemDetailUi, actions: ItemActions?, onHolder: (HeroId) -> Unit, onDismiss: () -> Unit)` | new; absorbs the row expander (`MarketPanel.kt:181-217`) with a weighted price field instead of `width(112.dp)` |
| `ui/ShopPanel.kt` | `ShopPanel(ui: ShopUi, onEvent: (ShopEvent) -> Unit, modifier: Modifier = Modifier)` | new; replaces `HomePanel.kt` and the top of `MarketPanel.kt`; one `LazyColumn` |
| `ui/LeadCard.kt` | `LeadCard(lead: LeadUi, onAct: () -> Unit, modifier: Modifier = Modifier)` | new |
| `ui/StorageSheet.kt` | `StorageSheet(weapons: List<WeaponRowUi>, onItem: (WeaponId) -> Unit, onList: (WeaponId) -> Unit, onDismiss: () -> Unit)` | new; `LazyColumn`, `key = id`, `contentType` |
| `ui/SuppliesSheet.kt` | `SuppliesSheet(ui: SuppliesUi, onBuyMaterial: (MaterialId) -> Unit, onBuyTool: (String) -> Unit, onDismiss: () -> Unit)` | moved from `MarketPanel.kt:94-139` |
| `ui/SettingsSheet.kt` | `SettingsSheet(reducedMotion: Boolean, speed: Speed, onReducedMotion: (Boolean) -> Unit, onSpeed: (Speed) -> Unit, onDismiss: () -> Unit)` | moved from `InfoPanels.kt:256-266` |
| `ui/RecordsPanel.kt` | `RecordsPanel(ui: RecordsUi, onEvent: (RecordsEvent) -> Unit, modifier: Modifier = Modifier)` | new shell; bodies of `GazettePanel`, `JournalPanel`, `LegacyPanel` become `LazyListScope` builders |
| `ui/Failures.kt` | `SaveFailureDialog(message: String, onRetry: () -> Unit, onKeepWorking: () -> Unit)`, `LoadFailedScreen(reason: LoadFailure, onRetry: () -> Unit)` | new |
| `ui/WorkshopScreen.kt` | `WorkshopScreen(ui: WorkshopUi, onEvent: (WorkshopEvent) -> Unit)` | changed: four items, no `autoSize`, gear, `BackHandler`, no report dialog |
| `ui/ForgePanel.kt` | `ForgePanel(ui: ForgeUi, onEvent: (ForgeEvent) -> Unit, …)` | changed: scene height, `weight` instead of `width(116.dp)`, Supplies and Journal entry points, request context line when the draft came from "Forge this" |
| `ui/InfoPanels.kt` | `TownPanel(ui: TownUi, onHero: (HeroId) -> Unit, onItem: (WeaponId) -> Unit, …)` | changed: lazy, rows clickable |
| `ui/RunEndScreen.kt` | `RunEndScreen(ui: RunEndUi, onEvent: (RunEndEvent) -> Unit)` | changed: `op` disables Claim, upgrades and Begin era and shows failure |
| `ui/Sprites.kt` | `Sprites.portrait(appearanceKey: String, classId: HeroClassId): Int` | changed: consumes the recorded key; the id-hash version (`Sprites.kt:73-76`) remains the fallback for old saves |

Reused unchanged: `PixelImage`, `WeaponSprite`, `EditionBody`, `paperBackground`, `SiegeStage`, `ReplayStage`,
`MilestoneBurst`, `SectionTitle`, `Secondary`, `TipBanner`, `AffinityHint`, `LevelDots`, `ForgeScene`, `Labels`,
the theme. `DayReportDialog` is retired; its paper layout becomes the Gazette overlay. `BlessingDialog`'s body
becomes `BlessingChoices(offers, onChoose)` used by the BL state and by Planning.

#### C.4 Where `LazyColumn` replaces `Column`

| Today | Change |
|---|---|
| host `Column.verticalScroll` for all panels (`WorkshopScreen.kt:84`) | each destination owns a `LazyColumn` with a saveable `LazyListState` |
| shelves and storage `forEach` (`MarketPanel.kt:54,62`) | `items(rows, key = { it.id })` in Shop; storage only in the sheet |
| all heroes `forEachIndexed` (`InfoPanels.kt:124`) | `items(heroes, key)`; the fallen under a collapsed header |
| every retained edition, computed in composition (`InfoPanels.kt:185-188`) | `items(days, key = { it })`; only the open day's edition is built, in the VM mapping |
| journal entries (`InfoPanels.kt:173`) | `items(entries, key)` |
| paper edition inside a scrolling `Column` (`Dialogs.kt:126`) | lazy items per section in the Gazette overlay |

Stay as `Column`: the forge steps, the run-end upgrades (eight rows), the settings sheet, the visit card.
UI models are built once per `GameState` in the VM on `compute` (sorting, prices, forecast), not in composition.

#### C.5 How the cursor is consumed

1. `init`: `repo.load()`; on `Ok`, `presentation.read()`.
2. If `run.lastResolution` exists and the cursor does not say "this `commandId`, acknowledged": build the script with
   the pure core function on `compute` and emit `ShopDay(script, pos, resumed = true)`, where `pos` is the stored
   position when `commandId` and `scriptVersion` match, else the first beat.
3. The screen renders `script.beats[pos]`. `Next`, `Back`, `Skip` and the timer send events; the VM changes `pos`,
   emits, and writes the cursor behind.
4. `Begin day` runs `presentation.acknowledge(commandId)` inside the serialized boundary, then emits
   `Playing(dest = SHOP)` or `RunEnded`.
5. The UI never calls `engine.handle`, never reads an RNG stream and never uses `kotlin.random`. Timers and
   `rememberInfiniteTransition` are its only sources of change. A grep gate in the test task ("no `Random` under
   `ui/`") keeps it that way.

#### C.6 Saved state

| State | Mechanism | Note |
|---|---|---|
| selected destination | `SavedStateHandle` | restored on task restore; cold start opens Shop |
| forge draft | `SavedStateHandle` (one JSON string; serialization is already a dependency) | validated against current materials on restore |
| reveal weapon id | `SavedStateHandle` | dropped if the weapon is no longer in storage (`Dialogs.kt:65` already guards); the item sheet makes the content reopenable anyway |
| open hero / item sheet, Gazette open | `SavedStateHandle` | ids only |
| forge step, price text, Records segment, list positions, storage filter, open archive day | `rememberSaveable` | the last one is `remember` today (`InfoPanels.kt:183`) |
| shop-day position | `PresentationStore` | survives force-stop, which `SavedStateHandle` does not |
| `op`, errors, timers | not saved | recomputed |

### D. Accessibility and performance checklist

| Area | Acceptance (all on the new and changed screens) |
|---|---|
| Font scale 1.0 / 1.3 / 1.5 / 2.0 | no text node reports visual overflow or truncation; no label auto-shrinks; at 1.0–1.3 Next, Skip and the whole visit card fit without scrolling at 411 dp; at 1.5–2.0 the card scrolls while Next and Skip stay pinned and the scene shrinks (min 120 dp; 96 dp at 2.0 on 320 dp). Nav labels may cap at 1.5x |
| 720x1280 and small width | pass at 360x640 dp (`wm size 720x1280`, `wm density 320`) and 320x569 dp (`wm density 360`): four nav items at least 48 dp wide, receipt rows wrap instead of clipping, no fixed-width control, End Day and Next fully visible |
| TalkBack order, shop day | header as heading ("Day 3, customer 2 of 3") → card: who, wields, looked at, decision, receipt rows as "label, value" → Next → Back → Skip day → speed. The card region is a polite live region so each beat is announced; focus is not moved programmatically. No auto-advance while touch exploration is on |
| Content descriptions | scene hidden from the tree; portrait and blade actions are real buttons in the card ("Mira Ashwood, details"); weapon chips read name, rarity word, element; rarity glyphs never spoken; till "Till 199 gold"; receipt total "Cash to the till, 91 gold" |
| Reduced motion | effective when the app toggle is on **or** the system animator scale is 0; no timers, no tweens, no idle frames; one static card per visit; the result of every beat is visible on its first frame |
| Targets | every clickable node at least 48x48 dp (asserted in tests); Skip and Next are never adjacent without 8 dp spacing |
| Contrast | text on paper and on scene overlays at least 4.5:1 measured on rendered pixels (muted paper text is about 4.8:1 by calculation, `Dialogs.kt:169`); no text drawn inside sprites |
| Audio and haptics | **Deferred, with their toggles.** Nothing in the app emits sound or vibration, haptics cannot be verified on the emulator, and a toggle with no effect is dead UI. The Settings sheet is built now so they have a home. Checklist line 85 splits into "Settings surface" (this update) and "audio, haptics and their toggles" (visual/audio redesign milestone) |
| Lists, 200+ stored weapons | Storage sheet with 250 weapons composes fewer than 30 rows; scripted fling shows at least 90 % of frames under 16.7 ms in `dumpsys gfxinfo` on the AVD (recorded as emulator evidence); no sort or forecast runs during composition |
| Bitmap memory | all 493 current drawables decode to 5.6 MB in total (4.2 MB is the 336 weapon sprites at 56x56); the counter scene from existing sprites adds nothing. Any new background goes through `import_assets.py` at 512x512 px or less (1 MB); a concept sheet is never loaded at run time (the untracked atlas is 1536x1024, about 6.3 MB as one bitmap). Graphics memory delta between Shop and the sequence under 8 MB in `dumpsys meminfo` |
| Timing | no time-pressure anywhere; Tomorrow and every decision wait indefinitely |

### E. Test plan

**Seams first** (C.2): injectable repository, presentation store, settings, dispatchers and seed; stateless screens;
`testImplementation` of `kotlinx-coroutines-test`. With them the ViewModel tests are plain JVM tests.

JVM, ViewModel with fakes (`app/src/test`):

| Test | Asserts |
|---|---|
| `EndDayCommitTest.nothingIsShownUntilTheSaveCompletes` | with the save held, state is `Playing(op = Saving)`; released, it is `ShopDay` |
| `EndDayCommitTest.doubleTapCommitsOnce` | two End Day events, one engine call, one commit |
| `SaveFailureTest.failedCommitKeepsCommittedStateAndOffersRetry` | repo throws: state is `Playing(op = Failed)` on day N, repo bytes unchanged |
| `SaveFailureTest.retryGivesTheSameResolutionAsAFirstSuccess` | retried result equals a control run byte for byte |
| `SaveFailureTest.keepWorkingReturnsToPlanningUntouched` | state and RNG equal the pre-command state |
| `SerializedOpsTest.twoUpgradesWithReorderedSavesBothSurvive` | F01, both release orders |
| `SerializedOpsTest.upgradeThenBeginEraKeepsRunAndUpgrade` | F01, both release orders |
| `ShopDayEquivalenceTest.watchSkipAndRestartLeaveIdenticalSaves` | three VMs from one seeded save: advance every beat / Skip day / advance k beats, rebuild the VM from the stores, finish. `SaveCodec.encodeRun` equal across all three and equal to the bytes captured at commit; one commit for the day; engine not called after the commit |
| `ShopDayEquivalenceTest.blessingChosenInTheSequenceEqualsChosenNextMorning` | equal saves |
| `ProcessDeathTest.killBeforeCommitThenEndDayAgainGivesTheSameDay` | equal to control |
| `ProcessDeathTest.killAfterCommitResumesAtTheFirstBeat` | `ShopDay(resumed)`, position 0, engine call count 0 |
| `ProcessDeathTest.killMidCounterResumesAtTheStoredVisit` | position equals the last written cursor, engine call count 0 |
| `ProcessDeathTest.lostAcknowledgementShowsTomorrowNotTheCounter` | cursor at TOMORROW gives the Tomorrow card |
| `ProcessDeathTest.acknowledgedDayOpensPlanningOnTheNextDay` | `Playing`, Shop, day N+1 |
| `ProcessDeathTest.defeatDayResumesThenEndsTheRun` | sequence, Fallen, `RunEnded(unclaimed)` |
| `ProcessDeathTest.claimedRunIsStillRunEndedWithUpgrades` | 1.3 gap closed |
| `LoadFailureTest.corruptRunShowsLoadFailedAndKeepsTheBytes` / `newerSchemaIsUnsupported` / `ioErrorCanRetry` | F02 |
| `SavedStateTest.destinationDraftAndRevealSurviveRecreation` | new VM from the same `SavedStateHandle` |
| `CursorTest.cursorOfAnotherDayIsIgnored` / `oldDismissedReportKeyCountsAsAcknowledged` | — |

Compose UI tests on stateless screens with fixture scripts (`app/src/androidTest`, `mainClock.autoAdvance = false`):

| Test | Asserts |
|---|---|
| `ShopDayScreenTest.purchaseShowsBuyerReasonAndSeparateReceiptRows` | name, reason line, and price, trade-in, bonus, cash as four distinct nodes |
| `ShopDayScreenTest.refusalShowsTheTypedReasonWithItsNumbers` | — |
| `ShopDayScreenTest.noVisitorsIsOneCardAndOneTap` / `emptyShelfListsEachNameOnce` | quiet days |
| `ShopDayScreenTest.tenVisitorsFeatureThreeAndTheTallyCoversTheRest` | 3 visit cards, 7 names in the tally |
| `ShopDayScreenTest.skipLandsOnTomorrowBlessingOrFallen` | three fixtures |
| `ShopDayScreenTest.backStepsABeatAndNeverAcknowledges` | no `Acknowledge` event from back |
| `ShopDayScreenTest.reducedMotionNeverAutoAdvances` | `advanceTimeBy(60_000)` leaves the beat unchanged |
| `ShopDayScreenTest.autoAdvancePausesWhileASheetIsOpen` | — |
| `ShopDayScreenTest.outcomeIsVisibleOnTheFirstFrameOfABeat` | guards against the timer-gated text of 1.8 |
| `ShopDayA11yTest.traversalOrderIsHeaderCardNextBackSkip` | semantics order |
| `ShopDayA11yTest.everyClickableIsAtLeast48dp` | — |
| `LayoutMatrixTest.noOverflow` (parameterised: 1.0 / 1.3 / 1.5 / 2.0 x 411 / 360 / 320 dp, via `DeviceConfigurationOverride`) | no visual overflow; Next and Skip displayed; applies to ShopDay, Shop, item sheet, run end |
| `NavigationBarTest.fourDestinationsKeepTheirLabelSize` | — |
| `StorageSheetTest.twoHundredFiftyWeaponsComposeOnlyVisibleRows` | — |
| `FailureUiTest.saveFailureOffersRetryAndKeepWorking` / `loadFailedOffersRetry` | — |

Instrumented with real stores: `ShopDayPersistenceTest.realRoomAndDataStoreSurviveRecreation` (`ActivityScenario`
recreate plus `StateRestorationTester`). A true kill is covered by the scripts.

Structural assertions replace golden images for now: the visuals will be replaced in the redesign and goldens would
churn. Human-review screenshots come from the scripts.

**adb scripts to extend** (both must change; they anchor on text the update removes: `"Today"` `smoke.sh:34`,
`tap "Market"` `:42`, `"Shelves (1/8)"` `:43`, `"EMBERFALL GAZETTE"` `:45`, `"Begin day"` `:47` and `runend.sh:34`):

- Move anchors to resource IDs: `testTag` on nav items, Next, Skip day, Begin day, End Day, with
  `testTagsAsResourceId` at the root, and a `tap_id` helper. "Shop" as a nav label would otherwise collide with the
  tally line "Shop took …".
- `smoke.sh`: after End Day wait for the counter, screenshot open / first visit / receipt / close / aftermath /
  tomorrow, advance with Next, check "Day 2" and the gold on the Shop tab equals the gold on the Tomorrow card.
  Second day: Skip day, same check. Third day: `am force-stop` on the second beat, relaunch, expect the resume
  prompt, resume, finish. Fourth: background with HOME, `am kill`, relaunch, expect the same beat.
- `runend.sh`: the loop gains branches for Skip day, Decide later and the Fallen card; after Claim add
  `force-stop` + relaunch and expect the run-end screen with upgrades.
- New `tools/emulator/layout.sh`: for scale in 1.0 1.3 1.5 2.0 and size in 1080x1920, 720x1280 (density 320 and
  360): set `font_scale`, `wm size`, `wm density`, screenshot Shop, Forge, an item sheet, a purchase beat, a tally
  beat, Tomorrow and run end; restore the settings in a trap.

### F. Fresh-player protocol (15 minutes)

Participant: has not seen the game. Fresh install, default settings, font scale as they keep it. The observer does
not explain anything and notes taps, hesitations and words. No hints before minute 10.

| Minute | Step | Observe |
|---|---|---|
| 0–1 | "Play as you would at home." Start a run | first tap after the title; do they follow the lead card |
| 1–4 | Day 1: forge, list, End Day, watch the shop day | do they watch or skip; do they open a hero or a blade |
| 4–5 | **Q1 "Who bought your blade, and why?"** (if nothing sold: "Who came, and what happened?") | answered from memory, without scrolling |
| 5–8 | Days 2–3, free play | whether they act on the Tomorrow card |
| 8–9 | **Q2 "Why did that customer leave without buying?"** then **Q3 "What happened because of a blade you sold?"** | — |
| 9–10 | **Q4 "What will you do tomorrow, and why?"** before they tap anything on the new day | — |
| 10–14 | Play on. If the run is still alive at minute 12, load the prepared save one day before a fatal siege and let them finish, claim and reach upgrades | claim and upgrade taps |
| 14–15 | **Q5 "What does that upgrade do for your next era?"** | — |

Pass criteria per participant:

1. Q1: names the buyer (name, class or face) **and** gives the recorded reason.
2. Q2: gives the recorded reason for one refusal **and** an action that addresses it.
3. Q3: links one real consequence to a hero and a blade seen at the counter. A day with no such event passes with
   "nothing yet".
4. Q4: states one action that matches the lead card or a request, without opening more than one other destination.
5. Q5: describes the upgrade's effect without a percentage and without the observer's help.
6. Behavioural: finishes three days within ten minutes; uses Skip day or Next without being told; never leaves the
   app by accident with back.

Milestone gate: five participants; **at least four pass Q1, Q2 and Q4, at least three pass Q3 and Q5**, and nobody
fails the behavioural line. Any question failed by two or more people is a defect in the screen that should have
answered it, not in the player.

---

## 3. Risks, order of work, decisions needed

**Top risks**

1. **The record does not exist yet.** Without visit-time snapshots the counter would be drawn from final state and
   could show the wrong blade or a dead hero's later gear. The UI work is blocked on C.1, not the other way round.
2. **Trustworthy state must land first.** The serialized boundary, failure states and injectable repository touch
   every authoritative path. Adding the sequence, the cursor and an in-sequence blessing save on top of today's
   unguarded paths multiplies F01.
3. **Launch crash loop plus Auto Backup** (1.2) is a store blocker independent of this update.
4. **Device scripts and docs break on day one** of the navigation change; budget the move to resource IDs.
5. **Length creep.** The cap of three featured visits and the tally keep 8–10 visitors at about 25 s; a fourth
   featured slot or auto-played replays would break the target.
6. **Art.** There is no counter prop and the full-body hero frames are 16x16. The plan uses portraits behind a drawn
   counter; the atlas is an unsliced concept sheet with baked-in text and should not gate this milestone.
7. **Large screens** (UNVERIFIED): portrait lock is likely ignored on tablets at this targetSdk.

**Order inside the Android work**

1. Seams, serialized boundary, `LoadFailed` / `Op.Failed`, run end kept after the claim, `SavedStateHandle`, back
   handling. Tests: `SerializedOpsTest`, `SaveFailureTest`, `LoadFailureTest`, `SavedStateTest`.
2. Stateless screens and four destinations, hero and item sheets, lazy lists, scripts on resource IDs.
3. Shop day on the core record: screen, cursor, resume, equivalence and process-death tests, layout matrix.
4. Lead card and Tomorrow card on the core advice function; fresh-player sessions.

**Decisions for the owner**

- Final label for the fourth destination (six characters or fewer).
- Whether "Replay the day" ships in this update (cheap, optional).
- Whether exact commission quality is shown as a number (F04) or the bands are aligned; the item sheet shows
  whatever the core exposes.
- Whether to turn `allowBackup` off until a load-failure state and a version policy exist.
