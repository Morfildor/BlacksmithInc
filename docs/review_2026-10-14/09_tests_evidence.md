# 09 — Test and evidence audit (2026-10-14, main at a2e0392)

Own device: Pixel_10_Pro AVD, API 37, port 5558, read-only, 1080x1920 at 420 dpi, font 1.0. Everything below was run on this tree today.

## Summary (10 lines)
1. Instrumented suite: 75 run, **8 fail, 67 pass** (8 m 10 s). PROGRESS says 9: `NavigationFlowTest.theShopKeepsItsPlaceAcrossTheForgeAndTheMenu` passed here (see the flakiness note in section 1).
2. All 8 failures are **stale tests**, every one traced to the copy remaster commit ec0486b (2026-10-11), which reworded screens and updated only part of each test. **No real defect, none release-blocking.** One dropped fact ("None is set aside") is an owner decision, not a bug.
3. Device scripts: see section 2 (smoke.sh, intro.sh, scenarios.sh) for the exact counts and the failing checks with reasons.
4. The brief's premise for task 5 is wrong: `.github/workflows/ci.yml` exists and is green on origin/main at 749061b (`gh run list`). The gap is that local `main` is 12 commits ahead and unpushed, and CI runs no instrumented test, which is how 8 stale device tests survived three days.
5. Counts reconcile: 625 core `@Test` minus 3 excluded in `core/build.gradle.kts:27-28` = 622; app 246; androidTest 75. No `@Ignore`, `assumeTrue`, `assumeFalse` or `@Disabled` anywhere (grep over all three suites, zero hits).
6. "Does not throw" tests: one honest one in the default suites (`SettingsSheetTest.everyMomentMapsToAPlatformEffectWithoutError`). 7 scanned hits in all; the others assert inside a helper or are excluded tasks.
7. Prose fragility is the structural weakness: 1,239 assertion lines compare against literals of five words or more (946 tests). One copy commit broke 8 device tests; the JVM suites survived only because the same commit updated them.
8. Determinism: the golden projection (`GoldenStateTest`) covers **classic runs only** and excludes the GUILD stream by design. Guild / rules-6 determinism rests on self-equality (`GuildRunTest.kt:372`, one seed, one policy; `CombatEngineTest.kt:41`): a change that silently alters guild outcomes passes both.
9. Migration fixtures exist for schema 1, 2, 3, 5 and 6; **no schema-4 fixture** although 0.7.0 shipped schema 4 (CHANGELOG line 330). The 4→5 step runs only transitively from v1–v3 saves that lack the schema-4 fields.
10. Claims: 8 lines in PROGRESS / GDD_CHECKLIST could not be confirmed or are stale (section 4); the simulator policies are covered by JVM tests (`SimulatorPolicyTest`, `SimulatorDepthTest`, `GuildRunTest` bots).

## Ranked findings
| # | Sev | Finding | Action | Evidence |
|---|-----|---------|--------|----------|
| 1 | high | 8 instrumented tests fail on main, all stale since ec0486b; nobody can tell a new device regression from the noise | Update the 8 assertions (table in section 1), then make the device suite a gate | `scratch/connected_full_suite.xml`, section 1 |
| 2 | high | CI never runs the instrumented suite; local main is 12 commits ahead of origin and unvalidated | Push; add the emulator job in section 5 | `.github/workflows/ci.yml:27`, `git status -sb` → `ahead 12` |
| 3 | medium | Guild / rules-6 outcomes have no pinned projection; the golden excludes GUILD | Record a guild golden (one charter, one seed, 10 days) next to `state_rules6.txt` | `core/src/test/kotlin/com/tinyblacksmith/core/GoldenStateTest.kt:48-50`, `GuildRunTest.kt:372` |
| 4 | medium | No schema-4 save fixture; 0.7.0 users upgrade through an untested step on real data | Add a 0.7.0-written save under `core/src/test/resources/saves/v4_*.json` and a `target = 5` test | `core/src/test/resources/saves/` listing; `SaveCodec.kt:80` |
| 5 | medium | 1,239 prose-literal assertions; UI tests assert full sentences instead of the fact | For device tests prefer tags + numbers; keep sentence equality only where the sentence is the feature | scan output, section 3 |
| 6 | low | GDD_CHECKLIST carries five stale evidence lines (test counts, enforced versions, 56 of 56) | Re-date the header and fix the five lines named in section 4 | section 4 |
| 7 | low | `EndDayPerfTest` logs p50 29.8 ms, p95 80.7 ms, max 575 ms (120 days, 287 weapons) on this emulator; checklist says 21 ms | Update the number; the 200 ms budget holds | logcat `TinyBlacksmithPerf` |

## 1. Instrumented suite: triage of the failures
Command: `ANDROID_SERIAL=emulator-5558 ./gradlew :app:connectedDebugAndroidTest` → `BUILD FAILED in 8m 10s`, XML: 75 tests, 8 failures, 0 errors, 436 s.

| Test | Failing line | Screen says | Test expects | Verdict | Blocking |
|---|---|---|---|---|---|
| `ConsistencyPassTest.townExplainsItsEmptyChampionPlaces` | `ConsistencyPassTest.kt:77` | "Each needs at least 50 health." and "No other hero has enough health. This slot needs at least 50." (`InfoPanels.kt:191-193`) | substring "health 50 or more" and "Not named yet: champions are chosen at the first End Day." | stale (ec0486b) | no |
| `ForgeHintTest.understoodPairingShowsFilledDotAndDescription` | `ForgeHintTest.kt:37` | "learned" (`ForgePanel.kt:567`) | "understood" | stale (ec0486b) | no |
| `ForgeResultCardTest.thePriceIsChosenOnTheCardAndTheCountFollowsIt` | `ForgeResultCardTest.kt:129` | "You're asking 20 gold less." (`PriceEditor.kt:58`) | "Your price is 20 below it." | stale (ec0486b) | no |
| `ForgeResultCardTest.aBladeForgedForARequestSaysWhatEndDayWillDo` | `ForgeResultCardTest.kt:173` | the request card no longer says what is reserved (`ShopUi.kt:160-170`; the sentence survives only as KDoc at `ShopUi.kt:60`) | "None is set aside." | stale (ec0486b); the fact was dropped, see owner decisions | no |
| `ShopDayScreenTest.fightsWithNoCardAreSummedUpBeyondTheDoor` | `ShopDayScreenTest.kt:179` | "6 heroes fought without weapons. All had to retreat." | regex `^\d+ hero(es)? went out` | stale (ec0486b); the card itself renders and equals `card.text` (line 178 passed) | no |
| `ShopPanelTest.theLeadIsTheSameAsTheTomorrowCard` | `ShopPanelTest.kt:146` | "…on price yesterday." | the test rewrites "the price today" → "the price yesterday"; the sentence now reads "on price today", so the rewrite misses | stale (ec0486b); the lead and the card do agree | no |
| `ShopPanelTest.demandBlockCountsUnarmedWornAndPurseBands` | `ShopPanelTest.kt:173` | "Can afford the middle-priced weapon (72 gold)" (`ShopUi.kt:257`) | "Can afford the middle blade (72 gold)" | stale (ec0486b); the counts (8 of 11) are right | no |
| `StorageSheetTest.bulkSalvageAsksOnceAndIssuesOneCommandPerBlade` | `ShopPanelTest.kt:231` | `stock_t4` is not composed: the `LazyColumn` (`StorageSheet.kt:160`) under the 48 dp filter chips no longer fits five rows in the test's 360x640 dp box | five rows on screen | stale (layout); the commands issued for t1..t3 were right up to that line | no |

`NavigationFlowTest.theShopKeepsItsPlaceAcrossTheForgeAndTheMenu` (listed as failing in PROGRESS) **passed** in the full run here. Re-runs alone: see the note below the table (filled after three isolated runs).

Owner decisions: the forge result card for a commissioned blade no longer says whether a blade is reserved ("None is set aside." dropped in ec0486b). Keep it dropped and delete the assertion, or restore one short line.

## 2. Device scripts
(filled below)

## 3. Coverage audit of the suites
(filled below)

## 4. Claims audit (PROGRESS top three sections, GDD_CHECKLIST)
(filled below)

## 5. CI
(filled below)
