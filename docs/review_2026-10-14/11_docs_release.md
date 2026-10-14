# 11. Documentation health, release readiness, the shape of the next patch

Tree: `main` at a2e0392 (12 commits ahead of `origin/main`, never pushed since 749061b). Lens: docs and release only; nothing
here changes a rule. Skills consulted for their checklists: `documentation-writer` (Diátaxis: what kind of document each file is),
`agent-md-refactor` (root file minimal, no duplicates, no contradictions). Nothing was refactored.

## Summary
1. No release gate is met beyond the JVM suites and the debug APK: the runbook's rename, signing, AAB and store steps are untouched (`docs/RELEASE_RUNBOOK.md:3`), `applicationId` is still the template (`app/build.gradle.kts:14`), and `main` is 12 commits ahead of GitHub so CI has not run on this tree.
2. `:app:assembleRelease :app:lintDebug` was run for this review on a2e0392: see section 1.3 for the result (the runbook §2.6 and CI both require it; nothing recorded it since 0.7.0).
3. The version should be **0.9.0** (three feature sets under `[Unreleased]`: run variety, deep forging, the guided first day), but the owner's own ruling R3 (`docs/RUN_VARIETY_LEDGER.md:88`) ties 0.9.0 to every run-variety gate passing and four are not (G3, G12, G13, G14). That is an owner decision, not a blocker of code.
4. v0.7.0 and v0.8.0 were never tagged (`git tag -l` ends at v0.6.0); `versionCode` 8 must rise with the name.
5. The `[Unreleased]` section is 46 lines / 41 bullets, shorter than 0.8.0's 106 bullets; its defect is three feature sets interleaved with six "Guild runs:" prefixes, not length.
6. Doc drift is wide: the runbook quotes versionCode 6, schema 2 and a `.gitignore` without keystore lines; IMPLEMENTATION_PLAN says gameplay depth is "not committed" and the shop day is "in progress, M0"; the checklist's header is 0.6.0 and has zero Heartfire lines; PROGRESS says `main` is still 0.6.0 in three places; CLAUDE.md lists two commands twice and omits two scripts and two ledgers.
7. Open owner decisions are scattered over seven files; 17 in DECISIONS are balance-8 numbers that no longer describe the game (classic now plays rules 6 / balance 14 and its golden state changed, `docs/RUN_VARIETY_LEDGER.md:142`). Section 2 consolidates them to 31 lines in six themes.
8. Repository weight: `docs/` is 135 MB tracked (609 PNGs; `run_variety_evidence` 57 MB, `deep_forging_evidence` 41 MB, `ui_review_2026-10-10` 26 MB), pack 170 MiB. An archive move inside git does not shrink it; only a decision to stop tracking screenshots does.
9. Stray files: `GDD.md` at the root has been 0 bytes since the first commit (047e058) and nothing references it; `scratchpad/` and `build/` are ignored (`.gitignore:17,13`) but three docs point into `scratchpad/`; 64 git worktrees are registered, 11 named branches among them, `deep-forging-base` still present as PROGRESS:104 asks.
10. Proposal (section 4): archive 9 files, cut PROGRESS from 1,181 lines to about 60, cut the CLAUDE.md command block from 29 lines to 14 and move the flag lists into the scripts' own `--help`.

## Ranked findings
| # | Sev | Defect | Action | Evidence |
|---|---|---|---|---|
| 1 | blocker (store) | Release identity, signing, AAB, listing untouched; package is the template | Owner supplies the app ID; run runbook §2 then §4 | `docs/RELEASE_RUNBOOK.md:3`, `app/build.gradle.kts:8,14` |
| 2 | high | `main` 12 ahead of origin; CI (`:assembleRelease :lintDebug`) never ran on this tree; 0.7.0 / 0.8.0 untagged | Push when the owner says; tag `v0.8.0` on 84dc427 and `v0.7.0` on 3589639 retroactively | `git status -sb` "[ahead 12]"; `git tag -l`; `.github/workflows/ci.yml:29` |
| 3 | high | Version policy contradicts itself: CLAUDE.md says bump on release; R3 says 0.9.0 only after all gates; four gates unmet | Owner: accept the four as known (ship 0.9.0) or hold | `docs/RUN_VARIETY_LEDGER.md:88,131-148`; `CLAUDE.md:107` |
| 4 | high | Runbook facts stale: versionCode 6 / 0.6.0, schema 2, keys list, "`.gitignore` ignores none" | Re-verify §2.4 and §4 against the tree before the rename | `docs/RELEASE_RUNBOOK.md:130,131,304,314` vs `app/build.gradle.kts:17-18`, `SaveCodec.kt:26`, `.gitignore:21-23`, `SettingsStore.kt:63-107` |
| 5 | high | Open owner decisions in seven files, many on numbers the game no longer plays | Replace with one list (section 2) in DECISIONS; mark the rest superseded | `docs/DECISIONS.md:3228-3295`, `GDD_CHECKLIST.md:194-195`, `GUILD_EVOLUTION_PLAN.md:197`, `RUN_VARIETY_LEDGER.md:150`, `DEEP_FORGING_LEDGER.md:173`, `PROGRESS.md:678`, `UI_UX_REVIEW_2026-10-10.md` §7 |
| 6 | medium | IMPLEMENTATION_PLAN: P7c "not committed", P7b "in progress, M0", T5.5 "open" | Tick: 1056157 committed depth; 0.7.0 shipped; T5.5 done | `docs/IMPLEMENTATION_PLAN.md:67,72,79`; `git log --diff-filter=A -- core/.../Depth.kt` = 1056157; `MAJOR_UPDATE_LEDGER.md:135` |
| 7 | medium | GDD_CHECKLIST header says 0.6.0 / 186 tests; no section for run variety or the guided day; 0 "Heartfire" | Add two sections, restate the header | `docs/GDD_CHECKLIST.md:8,12`; `grep -c -i heartfire` = 0 |
| 8 | medium | PROGRESS is 1,181 lines, 10 "current" sections dated 10-10/10-11; "Current phase" (line 369) describes 0.7.0 on a branch and "main is still 0.6.0" | Rewrite to 60 lines (section 4.2); archive the rest | `docs/PROGRESS.md:369,446,702` |
| 9 | medium | CLAUDE.md: `:core:scenarios` and `scenarios.sh` listed twice; `guild.sh`, `runvariety.sh`, `DEEP_FORGING_LEDGER.md`, `RELEASE_RUNBOOK.md`, `PLAYTEST_FIRST_DAY.md` absent; "four destinations (Shop, Forge, Town, Records)" but Town is Guild in a guild run | Apply the diff sketch in 4.3 | `CLAUDE.md:70,83,87,88,44`; `WorkshopScreen.kt:463`; `grep -c guild.sh CLAUDE.md` = 0 |
| 10 | medium | Dangling references: `BlacksmithInc_Gameplay_Review_2026-10-10.md`, `FINAL-REVIEW.md` exist nowhere in the tree | Say where they live or drop the pointers | `docs/GAMEPLAY_DEPTH_PLAN.md:3`, `DECISIONS.md:3195`, `MAJOR_UPDATE_LEDGER.md:70,212` |
| 11 | medium | ART_MANIFEST says the hero folder is "not in Git"; 42 files are tracked | Regenerate the manifest header text | `docs/ART_MANIFEST.md:14`; `git ls-files Assets/Implemented/heroes` = 42 |
| 12 | medium | `docs/` 135 MB tracked, pack 170 MiB | Owner: keep screenshots in git, or move evidence PNGs out (LFS or a release zip) | `git count-objects -vH`; `du` over `git ls-files docs` |
| 13 | low | `GDD.md` empty since 047e058, unreferenced | Delete | `git show --stat 047e058 -- GDD.md` = 0 lines; `grep -rn "[^_]GDD\.md" docs CLAUDE.md` = none |
| 14 | low | Docs point into ignored `scratchpad/` (UI review A/B/C, capture script) | Copy what is cited into `docs/ui_review_2026-10-10/` (A and B already are) or drop the pointers | `.gitignore:17`; `grep -rln scratchpad/ docs/*.md` = DECISIONS, PROGRESS, UI_UX_REVIEW |
| 15 | low | 64 worktrees registered (53 `agent-*`), `deep-forging-base` still on disk | `git worktree prune` and delete the folder by hand (not done here) | `git worktree list | wc -l` = 64; `PROGRESS.md:104` |
| 16 | low | `[Unreleased]` mixes three feature sets; no "Fixed"; 0.8.0 save compatibility stated mid-list | Regroup under 0.9.0 as in section 5 | `CHANGELOG.md:8-53` |

## 1. Release readiness

### 1.1 What the runbook requires (`docs/RELEASE_RUNBOOK.md`)
| Step | Requirement | On a2e0392 |
|---|---|---|
| §1 | Owner inputs: application ID, store wording, keystore location | none supplied (runbook:3) |
| §2 | Rename to `<APP_ID>` on a clean tree, then §2.6 verification | not started; `app/build.gradle.kts:8,14` still `com.example.blacksmithproject`; all 8 scripts have `PKG=com.example.blacksmithproject` |
| §2.4 | On-disk contract unchanged | holds, but the table quotes `SCHEMA_VERSION = 2` (:131, now 7 at `SaveCodec.kt:26`) and three preference keys (:130; `SettingsStore.kt:63-107` has nine, including `intro_*` and `charter_choice`), so the table must be re-verified before the rename |
| §3 | R8 on for release, keep rules, trial switch | present: `app/build.gradle.kts:27-36`, `app/src/main/keepRules/rules.keep`. The runbook says to delete the trial block when signing lands (:3.1) |
| §4 | `.gitignore` keystore lines, keystore, signingConfig, version bump, `bundleRelease`, Play Console | `.gitignore:21-23` done (runbook:304 says "ignores none": stale). Rest not started. Runbook:314 says "currently 6 / 0.6.0": now 8 / 0.8.0 |
| §5 | Store wording for AI-assisted art | draft only (runbook §5) |
| §6 | Launcher icon | template icon; not done (runbook §6) |

### 1.2 Gates met on this tree (brief figures)
| Gate | Result | Source |
|---|---|---|
| `:core:test` | 622 / 0 failures | brief |
| `:app:testDebugUnitTest` | 246 / 0 failures | brief |
| `:app:assembleDebug` | built, 21.4 MB | brief |
| `:app:assembleRelease`, `:app:lintDebug` | see 1.3 | run for this review |
| Instrumented suite | 66 / 75; 9 fail, same 9 on 749061b (wording and layout the tests were not updated for) | `docs/PROGRESS.md:46-50` |
| Device scripts | intro 29 ok, smoke 9, runend 6, guild 5 (two known missing ids), scenarios 59 ok / 3 fail (`wall_death` x2, `returned_legend`, script faults) | `docs/PROGRESS.md:42-45` |
| CI on GitHub | not run: `main` is 12 commits ahead of `origin/main` | `git status -sb` |
| Physical phone, TalkBack, playtest (T7.2) | none | `docs/PROGRESS.md:35-38` |
| Run-variety gates | 8 of 12 met; G3 one cell (Synergy 66 % vs 65), G12 drawbacks cost nothing measurable, G13 classic golden state changed by decision, G14 emulator only | `docs/RUN_VARIETY_LEDGER.md:131-148` |
| Classic 10,000-seed bands | measured at balance 8; classic now plays balance 14 with D14/D15 applied, so the band figures are stale, not failing | `docs/DECISIONS.md:3296`; `RUN_VARIETY_LEDGER.md:142` |

### 1.3 Release build and lint on a2e0392
Run for this review: `./gradlew :app:assembleRelease :app:lintDebug`. Result recorded below when the background job ends
(see the coordinator message); if this line still reads "pending", the figure was not captured before the appendix closed.
Result: pending.

### 1.4 What the version should be
- The content is a minor: a new forge ladder (Legendary by Heartfire or deeds, Epic only in fine metal), openings, preparations, contract modifiers, the metals' fight rules, the bench, the guided first day. SemVer `0.y.z` with "minor = new feature set" (`CHANGELOG.md:4-6`) gives **0.9.0**, `versionCode` 9.
- Blocking it is not code but the owner's ruling R3 ("Version 0.9.0 is set only if every one of them is met", `RUN_VARIETY_LEDGER.md:88`). Four gates are not fully met and none can be met by a doc change: G3 needs a retune or a widened band, G12 a design change to drawbacks, G13 is a decision already taken, G14 needs a phone.
- Recommendation: the owner either restates R3 (accept G3 by one point, G12 as "the card is the cost", G13 as decided, G14 as "emulator for 0.9.0, phone before the store") and 0.9.0 ships as a debug-tested development build like 0.7.0 and 0.8.0 were, or holds the name until a phone pass. Nothing in the runbook depends on the name; the store release is P8 either way.
- Also raise: tag v0.7.0 and v0.8.0 retroactively (`git tag -l` has none after v0.6.0; the 0.8.0 commit is 84dc427, the 0.7.0 bump 3589639).

## 2. Open owner decisions, consolidated
One line each, deduplicated, with where it is recorded. "Stale" means the number quoted was measured on a balance the game no longer plays.

**A. Version and release**
1. Ship 0.9.0 with G3 / G12 / G13 / G14 as known, or hold for a phone pass (R3). `RUN_VARIETY_LEDGER.md:88,150`
2. Application ID (permanent), keystore location, store wording for AI-assisted art, launcher icon candidate (`blessing_forgefire`). `RELEASE_RUNBOOK.md` §1, §5, §6; `DECISIONS.md:3292`
3. Commit policy for evidence screenshots (135 MB of `docs/` tracked). New here.
4. Name taste: Isherwood, Wyndham, Jarvis, Merrick, Lysander, Idris, Rosalind. `MAJOR_UPDATE_LEDGER.md:188`

**B. Balance, guild runs (balance 14, current)**
5. Synergy under Town's Last Hope is at the top edge of the charter band (63-70 % by seed block, band 40-65). Accept, widen to 67, or another round. `RUN_VARIETY_LEDGER.md:151`; `DEEP_FORGING_LEDGER.md:175`
6. Opening drawbacks cost nothing a bot can measure; Careful Books, Break It on Purpose and the Stormwell plans do not clearly pay. Make them bite or accept the card as the cost. `RUN_VARIETY_LEDGER.md:152-155`
7. Militia is near worthless at a guild wall while Town's Last Hope advertises three more. `RUN_VARIETY_LEDGER.md:156`
8. A classic expert ends day 40 with a median of two Legendary weapons, almost all by Heartfire. Enough? `RUN_VARIETY_LEDGER.md:158`
9. A lost rescue costs only two days, a scar and the deadline (won 96-98 %). Enough risk? `RUN_VARIETY_LEDGER.md:160`
10. A visiting merchant can sell a fine-metal blade before the supplier carries the metal. Close it? `RUN_VARIETY_LEDGER.md:162`
11. Two factions are weak to frost and resist fire; nothing is weak to fire, storm, grave or sap, so Frost Bloom is the widest augment and Ember Resin / Grave Dust have no niche. Changing it touches classic runs and golden states. `DEEP_FORGING_LEDGER.md:176`; `DECISIONS.md` "Deep forging", last paragraph
12. Residents' own expeditions still use the scalar resolver; moving them to the fight engine changes every classic number. `GUILD_EVOLUTION_PLAN.md:206`
13. Superseded, mark closed: guild plan #3 (charter 76-81 %, retuned at balance 12/13), #4 (day 5 bites now), #5 (rescue eased, R2). `GUILD_EVOLUTION_PLAN.md:201-205`

**C. Balance, classic runs (stale: measured at balance 8 or 10, before rules 6)**
14. One decision replaces DECISIONS items 1-6 and checklist 194-195: re-measure the classic bands on balance 14 (`:core:simulate --policy all`, 10,000 seeds, three base seeds), then decide the first-era band top (22.5 vs 22.52-22.60 at balance 8; median 25 / mean 25.2 at balance 10), Guild Patronage's stipend, the REQUEST_DRIVEN bound, the sub-1 % purchase reasons, the sidegrade floor, SIEGE_PREP, the wager's 94-98 % win rate and the options no bot prefers. `DECISIONS.md:3231-3258`; `GDD_CHECKLIST.md:162,194-195`; `IMPLEMENTATION_PLAN.md:84`
15. Wear margin (active smith leads the plain one by 5 days, was 10): condition floor 0.8 or less siege wear. `GDD_CHECKLIST.md:181` (0.6.0-era; re-measure with 14)
16. Scrap return of one material per four blades, never measured (no bot scraps). `PROGRESS.md:385-388`

**D. Rules and saves**
17. Abandon run keeps journal discoveries made in that run; should it erase them? `DECISIONS.md:3261`
18. Inheritance records kept for the whole run (half the kept records of a long save) vs the 30-day window. `DECISIONS.md:3268`
19. Save envelope as object instead of escaped text (13 % of the file; a migration). `DECISIONS.md:3271`
20. End Day remembers the newest 30 command IDs; raise or keep all. `DECISIONS.md:3273`
21. The save never levels off (unsold stock 60-65 %); "never listed" filter needs a stored field. `DECISIONS.md:3276`
22. Four distinct mechanical jobs for the catalysts (T4.4 withdrawn; partly overtaken by the guild's catalyst identities, `DECISIONS.md` "Guild evolution: how it was built"). `PROGRESS.md:385`
23. Weapon sheet words: "Renown" for fame and "Deeds: 2 of 3". `RUN_VARIETY_LEDGER.md:159`
24. A Heartfire forging has no confirm step. `RUN_VARIETY_LEDGER.md:160`
25. Schema numbering: fields added without a bump before testers get a build (item 13; moot once schema 7 ships). `DECISIONS.md:3283`

**E. Presentation**
26. Numbers on blade cards and rows (decision 8) with the UI review's recommendation: words on rows, numbers on the sheet. `DECISIONS.md:3264`; `UI_UX_REVIEW_2026-10-10.md` §7.1
27. Gold button hierarchy (decision 14) with the review's "one gold plate per screen". `DECISIONS.md:3285`; UI review §7.2
28. Shop-day card timings (decision 15) and the review's Auto toggle. `DECISIONS.md:3288`; UI review §7.3
29. UI review's six new ones: Back on the Shop opens the menu; duplicate stock rows on the Shop; the Resume prompt's till spoiler; gate "Scenarios (debug)" behind the debuggable flag; main-menu backdrop family; four sounds. UI review §7.5-7.10
30. Vocabulary: batch 4's "Pressure" / "Manual" vs the redesign's "Dire odds" / "Notebook"; "Forge integrity" / "request" / "Gazette". `PROGRESS.md:321,362-365`
31. The Guild screen is one scroll of seven sections: tabs or a shorter wall card. `GUILD_EVOLUTION_PLAN.md:207`

Settled and should be struck: DECISIONS item 3 (maxed SYNERGY lead holds), item 16 onboarding wording (overtaken by the guided first day), item 15 "commit the evidence folder" (tracked now), RUN_VARIETY first-pass 1, 2, 3, 6 (answered).

## 3. Doc drift, verified
| File:line | Says | Tree or git says | Fix |
|---|---|---|---|
| `CLAUDE.md:70,87` | `:core:scenarios` (two lines, two wordings) | one Gradle task (`core/build.gradle.kts:64`) | keep line 70 |
| `CLAUDE.md:83,88` | `scenarios.sh` twice | one script | keep line 83 |
| `CLAUDE.md` (none) | no `guild.sh`, `runvariety.sh` | both exist in `tools/emulator/`, both used in PROGRESS:30,69 | add one line each |
| `CLAUDE.md:93-103` | ledgers: MAJOR_UPDATE, GAMEPLAY_DEPTH, GUILD, RUN_VARIETY | `DEEP_FORGING_LEDGER.md` (182 lines), `RELEASE_RUNBOOK.md`, `PLAYTEST_FIRST_DAY.md` exist and are not named | add; drop "While the major update is open" (it closed as 0.7.0) |
| `CLAUDE.md:44` | "four destinations (Shop, Forge, Town, Records)" | `WorkshopScreen.kt:463`: Town reads "Guild" in a guild run; `GuildPanel.kt` exists | "Town (Guild in a guild run)" |
| `CLAUDE.md:44-47` | screens list | `ui/Intro.kt`, `IntroStrip.kt`, `OpeningChooser.kt`, `BenchSheet.kt`, `CharterPicker.kt` unnamed | one line for the guided day and the guild sheets |
| `CLAUDE.md:107` | bump the version on release | R3 in `RUN_VARIETY_LEDGER.md:88` adds a gate condition | reconcile (section 1.4) |
| `RELEASE_RUNBOOK.md:314` | versionCode 6, 0.6.0 | `app/build.gradle.kts:17-18`: 8, 0.8.0 | restate |
| `RELEASE_RUNBOOK.md:304` | `.gitignore` ignores no keystore | `.gitignore:21-23` | tick |
| `RELEASE_RUNBOOK.md:131` | `SCHEMA_VERSION = 2` | `SaveCodec.kt:26` = 7 | restate |
| `RELEASE_RUNBOOK.md:130` | keys reduced_motion, seen_tips, dismissed_report | `SettingsStore.kt:63-107`: also haptics, shop_day_speed, intro_status/run/reached/serial/completed_once, charter_choice | restate |
| `RELEASE_RUNBOOK.md:122` | `SaveStore.kt:90` | `SaveStore.kt:115` | re-verify §2.4 line refs |
| `IMPLEMENTATION_PLAN.md:79` | P7c "built 2026-10-10, not committed" | commit 1056157 on `main` | tick |
| `IMPLEMENTATION_PLAN.md:72-77` | P7b "in progress, M0" | 0.7.0 released (`CHANGELOG.md:176`), ledger closed | mark done |
| `IMPLEMENTATION_PLAN.md:67` | T5.5 open | `MAJOR_UPDATE_LEDGER.md:135` done | tick |
| `IMPLEMENTATION_PLAN.md` | no entry for run variety, deep forging, guided first day beyond :70 | three feature sets on `main` | add a P7d/P7e block or point at the ledgers |
| `GDD_CHECKLIST.md:8,12` | "Last reviewed 0.6.0, 186 tests"; "main is still 0.6.0" | 0.8.0 + Unreleased; 622 / 246 tests | restate |
| `GDD_CHECKLIST.md` | no run-variety / guided-day section; 0 "Heartfire" | rules 6 forge on `main` | add two sections |
| `GDD_CHECKLIST.md:172` | rename awaiting the ID | true; keep | none |
| `PROGRESS.md:3` | guided day "not pushed" | true (`[ahead 12]`) | keep until pushed |
| `PROGRESS.md:369-466` | "Current phase": 0.7.0 on `shop-day/m0`, "main is still 0.6.0" | 0.8.0 on main | archive as history |
| `PROGRESS.md:702` | evidence folder and external review "still untracked" | `git ls-files docs/major_update_evidence` = 22 | strike |
| `PROGRESS.md:104` | delete `.claude/worktrees/deep-forging-base` by hand | still present (`ls .claude/worktrees`) | do it |
| `PROGRESS.md:1172` | "RULES_VERSION stays 1" | `GameEngine.kt:39` = 6, enforced at `Compatibility.kt:23` | history; archive |
| `ART_MANIFEST.md:14` | hero folder "not in Git" | 42 tracked files under `Assets/Implemented/heroes` | regenerate |
| `GAMEPLAY_DEPTH_PLAN.md:3` | `BlacksmithInc_Gameplay_Review_2026-10-10.md` | not in the tree | name its location or drop |
| `DECISIONS.md:3195`, `MAJOR_UPDATE_LEDGER.md:70,212` | `FINAL-REVIEW.md` | not in the tree | same |
| `UI_UX_REVIEW_2026-10-10.md` §8 | `scratchpad/uiux_capture.sh` | `scratchpad/` ignored (`.gitignore:17`) | commit it under `tools/emulator/` or drop |
| `GUILD_EVOLUTION_PLAN.md:197-208` | open #3, #4, #5 | retuned by run variety (balance 12-13, R2) | mark superseded |
| `DECISIONS.md:3228-3295` | 17 open decisions, balance 8 | classic plays balance 14 and D14/D15 | annotate "stale, see section 2.C" |

Not drift, confirmed: `GameEngine.RULES_VERSION = 6`, `SaveCodec.SCHEMA_VERSION = 7`, `BalanceConfig.version = 14`, `Compatibility.kt:23-29` enforces rules and content; toolchain versions in CLAUDE.md match `gradle/libs.versions.toml` and `gradle-wrapper.properties` (Gradle 9.5.0, AGP 9.3.3, Kotlin 2.2.21, KSP 2.3.12, BOM 2026.02.01, Room 2.8.5, DataStore 1.2.1); `Policy.CLASSIC` is `entries.filter { it.rules == null }` (`Simulator.kt:105`); `GuildPolicy` has 14 entries (`GuildSim.kt:23-39`); `content/SliceContent.kt` exists; `Assets/Implemented/heroes` is committed as CLAUDE.md:56 says.

### Prior findings (brief rule 6)
- Thorough review F09 (rules version never enforced): fixed by `Compatibility.kt:23-29` and `RULES_VERSION = 6`.
- F10 (save growth): open by decision (`GDD_CHECKLIST.md:185`).
- Wording corrections C01-C09, C12: applied (`GDD_CHECKLIST.md:9-10`), but the header they sit under is now itself stale.
- UI/UX review section 7: the four recommendations for decisions 6/7/8/14 and the six new ones are still undecided (none appears in DECISIONS after line 3626).
- Guild plan open questions 1-2 (is the fight fun, is the opening fun): still open, nobody has played (`DEEP_FORGING_LEDGER.md:174`).

## 4. Lighter docs for the next patch (proposal, not executed)

### 4.1 Archive under `docs/archive/`
| File | Why | Keep at top level |
|---|---|---|
| `MAJOR_UPDATE_PLAN.md` (2,549 lines), `MAJOR_UPDATE_LEDGER.md` (221) | the 0.7.0 update closed; CLAUDE.md:97 says the ledger is live only "while the major update is open" | a 5-line pointer in DECISIONS |
| `GAMEPLAY_DEPTH_PLAN.md` | shipped in 0.7.0-post; its tables are content now in `content/Depth.kt` | the encounter / relic / trait tables could move to a `docs/reference/depth.md` if anyone reads them |
| `GUILD_EVOLUTION_SPEC.md` (1,235), `GUILD_EVOLUTION_PLAN.md` | 0.8.0 shipped; open questions move to section 2 | `GDD_REVISION_2026-10-10.md` stays (it is the rule addendum) |
| `Tiny_Blacksmith_Thorough_Review_2026-10-09.md`, `UI_UX_REVIEW_2026-10-10.md`, `ui_review_2026-10-10/` | superseded by this review folder | their undecided items in section 2 |
| `PROGRESS.md` lines 106-1181 | history of 0.4.0 to 0.8.0 | as `archive/PROGRESS_2026-10-08_to_10-11.md` |
| `DECISIONS.md` lines 1-3101 | balance v1 to v8 and the M-gates | as `archive/DECISIONS_balance_1-8.md`; the live file starts at "Major update: rulings" and gets the consolidated list |
| `major_update_evidence/`, `gameplay_depth_evidence/`, `guild_evolution_evidence/` | evidence of shipped versions | move or leave; moving inside git does not shrink the pack |
| `RUN_VARIETY_LEDGER.md`, `DEEP_FORGING_LEDGER.md` | stay live until 0.9.0 ships, then archive | |
| `GDD.md` (0 bytes) | delete, not archive | |

### 4.2 A 60-line PROGRESS
```
# Progress (date)
State: main @ <sha>, app 0.9.0 (versionCode 9), rules 6, schema 7, content 6, balance 14. Pushed: yes/no. Tag: v0.9.0.
Checks on this tree: :core:test N/0, :app:testDebugUnitTest N/0, assembleDebug, assembleRelease, lintDebug,
  instrumented P/F (the F named), intro/smoke/runend/guild/scenarios script counts, simulate and guildsim seeds used.
Known failing (3-6 lines): the 9 instrumented tests, the 3 scenarios.sh checks, the 2 guild.sh ids.
What shipped since the last version (5-8 lines, one per feature set, pointing at CHANGELOG and the ledger).
Not verified (5 lines): phone, TalkBack, playtest, font 2.0 on the Shop lead card, large saves on a device.
Open owner decisions: "see DECISIONS.md, Open decisions (31 lines)"; one line naming the three that block the next step.
Next actions (5-8 numbered lines).
Housekeeping (3 lines): worktrees to prune, folders to delete, docs to archive.
```
Everything else that PROGRESS holds today is either a ledger's job (per-feature evidence) or history.

### 4.3 CLAUDE.md diff sketch (108 lines to about 70)
- Commands block (29 lines to 14): one line per entry point, no flag lists. Move the `simulate`, `guildsim` and `combat` flag catalogues into the tools' own `--help` output (they already parse `argMap`; `Simulator.kt:1045`) or `docs/reference/simulator.md`. Delete lines 87-88 (duplicates). Add `guild.sh` and `runvariety.sh`. Drop the `<sdk>/platform-tools/adb` prefix repetition: say once "every `tools/emulator/*.sh` takes `ADB=<adb> [ANDROID_SERIAL=...] bash script <dir>`".
- Architecture (lines 24-62): keep the boundaries; move the per-feature prose (the rules-6 forge paragraph, the deep-forging file list, the art-source paragraph of lines 52-60) into `docs/reference/architecture.md` and `docs/ART_BRIEF.md`, which already hold them. Line 44: "Town (Guild in a guild run)". Add `ui/Intro.kt` + `IntroStrip.kt` to the screens line.
- Working rules (lines 93-108): replace the four ledger sentences with one: "Feature ledgers live in `docs/` and are named in `docs/PROGRESS.md`; the live ones at any time are those PROGRESS names." Add `RELEASE_RUNBOOK.md` and `PLAYTEST_FIRST_DAY.md` to the doc list. Resolve the version rule against R3 (section 1.4).
- Contradiction to resolve (agent-md-refactor phase 1): CLAUDE.md:107 "on a version bump, rename that section" vs R3's gate condition; CLAUDE.md:30 "classic run plays as rules 4 did but for the rules-6 forge" vs the GUILD plan's constraint "plays exactly as rules 4 did" (`GUILD_EVOLUTION_PLAN.md:24`), already overridden in DECISIONS ("This overrides the rules-5 note").
- Flag for deletion: "Toolchain" line 90-91 (duplicated by `libs.versions.toml`); the parenthetical `(played and constructed; run after a rules, content or balance change)` belongs in the task's own comment, which already has it (`core/build.gradle.kts:62`).

### 4.4 Housekeeping with the patch
`git worktree prune` (64 registered, most `agent-*` gone from disk), delete `.claude/worktrees/deep-forging-base` and the ten finished named worktrees after confirming their branches are merged, tag v0.7.0 and v0.8.0, decide item A3 on evidence weight.

## 5. CHANGELOG draft for 0.9.0

```
## [0.9.0] - 2026-10-XX
Rules 6, save format 7, content 6, balance 14. A save from 0.8.0 loads and plays on; the opening choice needs a new run.

### Highlights
- A guided first day: a new installation's first game is led through one real day, step by step, skippable, replayable from Settings.
- Legendary is earned: quality stops at Epic; a weapon becomes Legendary by a Heartfire forging that breaks through or by three deeds.
- Heartfire: a scarce ingredient from dangerous work; the workbench shows what a Heartfire forging needs and risks before anything is spent.
- Epic needs fine metal: only Starsteel and Moonsteel hold Epic work; the supplier carries them from day 6 and 11.
- Openings: a guild run starts with a choice of three packages (eight in all, seven build directions), each with a drawback.
- Preparations: every guild siege is announced four days ahead with what the besieger has prepared and the sabotage that spoils it.
- Contracts differ: modifiers on the card, four of which change the decision (hold the ford, bring the guard out, a narrow way, paid in stock).
- A taken weapon marches back with its faction's siege and comes home if the wall holds.
- Every metal gives a new weapon a fight rule; the Forge shows what the recipe will do, who it suits and what it still needs.
- The bench: rehearse a recipe or an owned weapon against a chosen company and enemy line, the town's wall among them; nothing is paid or recorded.
- Fight notes in the notebook: rules forged, combinations suggested, and the ones seen in a real fight, marked apart.
- Tempers differ (Safe narrows, Reckless adds a slot and risks a flaw); sieges start stronger and climb more slowly.

### Added / ### Changed / ### Fixed
(the 41 current bullets, regrouped: "Guided first day" (3), "Forge and rarity" (8), "Guild runs: openings, sieges, contracts" (12),
"Deep forging" (8), "Balance" (8), "Tools" (2); drop the "Guild runs:" prefix inside a "Guild runs" heading)
```
`versionCode` 9, `versionName` "0.9.0" in `app/build.gradle.kts:17-18`; tag `v0.9.0`; keep `mapping.txt` per the runbook §3.2.

## Owner decisions (brief rule 4)
None of the above moves a LOCKED rule. The decisions this appendix asks for are A1-A4 of section 2 (version, release identity, evidence weight, names) and the choice in 4.1 of what to archive.
