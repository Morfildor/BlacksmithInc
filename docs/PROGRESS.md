# Progress — 2026-10-09 (session 8)

## Current phase
P7 in progress. Launch content is the engine default and balance v2 is tuned against it; all 24 signature recipes,
the Strange Weapon Fragment event and End Day event-log compaction landed the same day (parallel agents, merged).

## Session 6 focus: gameplay depth (balance v3, app 0.4.0)
The owner asked for function and features over UI ("not fun yet, not much in it"; the UI will be redesigned later).
Added in `:core`, each with one plain UI surface: affix effects, elite foes and warlords, hero ambitions, element
commissions, the Salvage / Hone / Arm the watch shop actions, workshop tools, a siege forecast. Design, numbers and
simulator evidence are in DECISIONS.md ("Balance v3: gameplay depth").

## Session 7 focus: readable Gazette, Home dashboard, demand (balance v4, app 0.5.0)
The owner found the between-days journal "a text block" and asked for a home screen "like a dashboard", then for all
agents on gameplay. `Gazette.edition` (core) lays a day out as a paper: lede (priority >= 6, at most two lines, never
the standing siege warning), a tally line (gold taken net of trade-ins, visitors who bought, expeditions won/lost,
heroes fallen), then Shop / Heroes / Town / Forge; a hero's several records fold into one sentence, quiet heroes share
a line, visitors who left are named with the reason, milestones already told by the news fold away, the smith's own
work is one line. The report dialog and the archive render it (`EditionBody`); siege rounds fold behind the outcome.
Four agents then worked in worktrees and were merged: the Home panel (first tab, landing panel, one block per
concern), weapon wear (`Weapon.condition`, worn-power demand, Hone restores), weapon fame as a bounded effect, and
the v3 review at 10,000 seeds with per-tool / per-affix sweeps (two of its recommendations applied: whetstone
120/300, warlord pressure 50 without a raid bonus). Evidence in DECISIONS.md ("Balance v4 ...").

## Session 8 focus: major features from the checklist (app 0.6.0 and balance v5 in progress)
`docs/GDD_CHECKLIST.md` now lists every GDD feature as built or open; the owner asked for the major open features,
kept in `:core` with plain text surfaces because the UI will be redesigned. On `main` and pushed: weapons-map pruning
(`WeaponPruning`, End Day tail), the day report reopening after process death (`SettingsStore.dismissedReport`), and
an instrumented End Day budget test (`EndDayPerfTest`). Merged from an agent worktree: hero daily life (the guild
hall and the ambition as scored daily activities, money and yesterday as inputs; `Heroes.activityWeights`,
`HeroLifeConfig`), then fight replays for elite fights and deaths plus the fates of a fallen hero's blade (guild
inheritance, merchant resale, odds by where the hero fell; `Battle.fightReplay`, `Market.resolveMerchant`,
`WeaponFatesConfig`), then three legacy tracks (Caravan Ties, Anvil Lore, Homing Steel), Known Name regulars and a
second yardstick table for upgrades (`LegacyTracksConfig`, `Simulator` `Yardsticks`). All three branches are merged;
nothing is in flight.
Also on `main`: the signboard now adds a customer a day per level and six affix magnitudes are stronger; that is
balance config v5 (the branches join v5 when they merge). Evidence in DECISIONS.md ("Weapon pruning and day-report
recovery", "Balance v5, part 1").
Two documents arrived in `docs/` during the session and are not part of this work (untracked, left as found):
`Tiny_Blacksmith_Thorough_Review_2026-10-09.md` (an external review pinned to b39ad76, findings F01-F11) and
`MAJOR_UPDATE_PLAN.md` (a draft plan for a shop-day update, written by another session). None of the review findings
is fixed by session 8 except the signboard (its section 6.2); the checklist corrections it lists are still to apply.

## What exists
- `core/` pure Kotlin engine: RNG, slice + launch content catalogs (launch is the default), balance config v2, model,
  commands, End Day resolver, crafting with techniques and all 24 signature recipes, market, hero AI with retirement/
  guilds/mentoring, battles/sieges with weapon seizure, world-event pool (23 events + 2 deterministic rules = all 25 GDD
  events), legacy with famous-blade returns, Gazette, JSON save codec with a migration scaffold, End Day event-log
  compaction (30-day window, history-grade types kept), headless simulator (GDD policy set, `--content`,
  `--rarityTable`, `--impactPolicy`, `BALANCED_INVEST` purchasing rule with `--reserve`, `BALANCED_REPUTED`
  pricing, forge-damage overrides, JSON report, perf probe), reputation/loyalty depth (bounded price ceiling,
  loyalty-weighted commission patrons, premium/regular Gazette records), weapon-history cap, a checked-in v1 save
  fixture. Balance v3 (session 6): affix effects (bane, elite, heal, loot, wound, shatter, self-harm), elite
  encounters, warlord sieges, hero ambitions, element commissions, `Salvage` / `Hone` / `DonateWeapon` / `BuyTool`
  commands, workshop tools, `siegeForecast`, `BALANCED_ACTIVE` simulator policy, trade-ins and patrol pay. Balance v4 (session 7): weapon wear and condition, weapon fame effects (capped), whetstone 120/300, warlords from pressure 50; `Gazette.edition`; simulator catalog sweeps. Session 8: `WeaponPruning` (blades gone for good leave the save after 30 days); balance v5 part 1 (signboard +1 customer a day per level, stronger affix magnitudes) and part 2 (`HeroActivity.GUILD` / `AMBITION`, mentoring at the hall, guilds founded in life, simulator activity shares) and part 3 (`CombatReplay.kind`, expedition replays, `WeaponFate`, merchant resale, simulator fate counters and artifact recovery, `--noFates`) part 4 (Lucky brings back scarce materials) and part 5 (11 upgrade tracks, Known Name regulars, upgrade yardsticks, `--upgrades` / `--yardsticks` / `--legends`). 186 JVM tests.
- `app/` Compose portrait workshop (session 4 layout: three-stat top bar, pinned forge summary over collapsible
  auto-advancing steps, row-based market, per-panel tip banners, single End Day action, full-width paper day report;
  principles in DECISIONS.md; session 5: title/run-end on the spacing tokens with one primary action, Town lists
  every faction with pressure and weakness, screen-reader descriptions on disabled actions), seven panels (Home dashboard first, session 7), technique chips,
  newspaper day report with stepped replay, blessing choice (dismissable for the day), run-end/legacy screen; Room
  atomic save store; DataStore settings (reduced motion, seen tips, the last dismissed day report). Hand-made pixel art on every screen.
- `tools/pixelart/import_assets.py` (slices concept sheets, copies selected pack sprites, prunes stale imports),
  `generate_assets.py` (placeholders), `tools/emulator/smoke.sh` (device loop). `docs/ART_BRIEF.md` is the brief.
- Animated siege diorama in the day report (artist pack frames), dead/retired markers, milestone burst on the result
  card for signature or epic+ weapons.
- Weapon master sheet sliced into 336 `weapon_<family>_<row>_<level>` sprites with a generated `ui/WeaponArt.kt`
  lookup; 488 hand-made sprites in total (94 sheet slices + 336 weapons + 58 pack sprites).

## Checks run this session (balance v2, launch default)
| Check | Command | Result |
|---|---|---|
| Core tests | `./gradlew :core:test` | 85/85 pass (14 classes incl. RarityShape, EventCompaction, SignatureAndTechnique, SimulatorPolicy on the launch catalog); the soak runs on launch content |
| App compiles | `./gradlew :app:compileDebugKotlin -q` | BUILD SUCCESSFUL |
| Rarity tables | `./gradlew :core:simulate --args="--rarityTable 1000 --seed 1 --content launch"` | before/after tables in DECISIONS.md |
| Policy sweep | `./gradlew :core:simulate --args="--runs 1000 --seed 1 --content launch"` | BALANCED_FAIR 25 (15/35), mean 23.8, 1.6 sieges survived/run, 0 hard-locks; full table in DECISIONS.md |
| Confirmation | `--runs 10000 --policy BALANCED_FAIR` / `SAFE_FAIR` | 25 (15/35) mean 23.7 survived 1.5 / 25 (15/30) mean 23.2 survived 1.5 |
| 10,000-seed review | `./gradlew :core:simulate --args="--runs 10000 --seed 1 --policy all --impactPolicy BALANCED_INVEST --reserve 0 --perf --json <out>"` | every policy at 10,000 seeds, maxed legacy for BALANCED_FAIR and BALANCED_INVEST, upgrade impact under BALANCED_INVEST; full table in DECISIONS.md ("Balance review at 10,000 seeds") |
| Slice regression | `--content slice --policy SAFE_FAIR` | 30 (20/35), rarity 18/51/28/2/0 (was 1/43/52/4/0; accepted, see DECISIONS) |
| Event compaction | `EventCompactionTest` + `:core:simulate --runs 200 --seed 1 --perf` | 400-day forced-survival pair: gameplay identical with/without compaction; perf run ends at events=1,834 (was 13,275) |
| Weapon-history cap + v1 fixture | `./gradlew :core:test` (`WeaponHistoryCompactionTest`, `SaveFixtureTest`) + `:core:simulate --runs 200 --seed 1 --perf` | 92/92 pass; 400-day pair: gameplay identical with/without the cap, entries 1,812 -> 1,721, max combat entries 21 -> 10, save 973,919 -> 961,841 bytes; `saves/v1_forced_seed4242_day61.json` decodes, accepts an End Day and compacts; perf p50 0.34-0.40 ms vs 0.26 before, within machine noise (cap-disabled control 0.39-0.71) |
| Debug APK + install | `./gradlew :app:installDebug` | BUILD SUCCESSFUL |
| Instrumented | `./gradlew :app:connectedDebugAndroidTest` | 5/5 pass (Room atomic save/restore x2, title screen, forge hint x2) |
| Device loop | `tools/emulator/smoke.sh` | Re-run on the merged 0.2.0 build (launch content, new layout): shelf/town/resume checks ok, SMOKE_DONE; screenshots sent to the user |
| Instrumented on merged main | `./gradlew :app:connectedDebugAndroidTest` | 5/5 pass on the 0.2.0 build |
| Art import | `python tools/pixelart/import_assets.py` | 5 sheets + weapon master + 1 pack -> 488 sprites; contact sheet reviewed; weapon shelf verified on device |
| Reputation/loyalty depth (session 4) | `./gradlew :core:test`, `./gradlew :core:simulate --args="--runs 1000 --seed 1"` before/after | 89/89 pass (82 + 7 in `ReputationAndLoyaltyTest`: caps, evaluate() stranger vs regular, reputation ceiling, premium sales 40/40 vs 29/40 at 190 %, commission bias 22/58 offers to the regular, event text, determinism); BALANCED_FAIR 25 (15/30) mean 23.7 (was 25 (15/35) mean 24.0), sell rate 14 % unchanged; tables in DECISIONS.md |
| Simulator wave 2 + UI wave 2 (session 5) | `./gradlew :core:test`; `:core:simulate --runs 10000 --seed 1 --policy BALANCED_REPUTED`; `:app:assembleDebug`, `:app:installDebug`, `tools/emulator/smoke.sh scratchpad/ui_v4`, `:app:connectedDebugAndroidTest` | 103/103 pass (SimulatorPolicyTest added); BALANCED_REPUTED 25 (15/30) mean 23.6, within 0.3 days of FAIR (DECISIONS); build ok, SMOKE_DONE with shelf/town/resume checks ok, instrumented 5/5; title/forge/town screenshots sent |
| Run-end route + large fonts (session 5) | `tools/emulator/runend.sh scratchpad/runend`; Town at font scale 1.3 and 1.5 | passive run falls on day 10; final Gazette -> claim -> pinned Begin era -> era 2 day 1, all checks ok, RUNEND_DONE; Town wraps without clipping at both scales |
| Gameplay depth (session 6) | `./gradlew :core:test`; `:core:simulate --runs 1000 --seed 1 --policy all`; `--policy BALANCED_ACTIVE --impactPolicy BALANCED_ACTIVE` | 122/122 pass (18 new in `GameplayDepthTest`, 1 in `SimulatorPolicyTest`); BALANCED_FAIR 20 (15/30) mean 21.7 (was 25 / 23.7), BALANCED_ACTIVE 30 (20/35) mean 27.5, sales 17.7 / 23.4 a run (was 16.2), 0 hard-locks; armory and raid sweeps and the full table in DECISIONS.md |
| Weapon wear (session 7, merged into v4) | `./gradlew :core:test`; `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE` before/after plus a 4-variant wear sweep; `:app:compileDebugKotlin -q` | 130/130 pass (8 new in `WeaponWearTest`: bounds, worn power, expedition and siege wear, worn replacement, re-hone, save defaults, determinism); BALANCED_FAIR 20 (15/25) mean 20.0, sold 19.3, NOT_BETTER 36.3 (was 20 (15/30) 21.7 / 17.7 / 45.8); BALANCED_ACTIVE 25 (20/35) mean 25.4, sold 25.9, NOT_BETTER 43.5 (was 30 (20/35) 27.5 / 23.4 / 58.3); deaths 0.9, 0 hard-locks; app compiles; sweep table in DECISIONS.md ("Balance v4 (pending merge)") |
| 0.4.0 on device (session 6) | `:app:installDebug`, `tools/emulator/smoke.sh`, `:app:connectedDebugAndroidTest`, manual adb pass | SMOKE_DONE (shelf/town/resume ok), instrumented 5/5; Hone, Arm the watch, Salvage and a tool purchase each changed state on device; not exercised on device: commission element text, warlord line, Maxed tool state, trade-in sale text, run-end route |
| Weapon fame (session 7, merged into v4) | `./gradlew :core:test`; `:core:simulate --runs 1000 --seed 1 --policy all` with the fame numbers at 0 / desire+price only / half power / adopted; `:app:compileDebugKotlin -q` | 127/127 pass (5 new in `WeaponFameTest`: capped factor, famous beats plain in `evaluate`, capped price premium, returned legend at the cap, determinism); BALANCED_FAIR 20 (15/30) mean 22.5, sold 18.0, deaths 0.9 (was 20 (15/30) 21.7 / 17.7 / 0.8), BALANCED_ACTIVE 30 (20/35) mean 28.4, sold 24.0, deaths 1.0 (was 30 (20/35) 27.5 / 23.4 / 0.9), 0 hard-locks; desire and price alone change nothing, the +5 % power cap carries the shift (the brief's +10 % example overshoots the band: FAIR median 25, ACTIVE p90 40); app compiles; table in DECISIONS.md "Balance v4 (pending merge)" |
| Balance v3 review at 10k + per-tool / per-affix sweeps (session 6) | `:core:simulate --runs 10000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE --perf --json`; 1,000-seed `--noTool` / `--toolCost` / `--noAffixEffect` sweeps under BALANCED_ACTIVE (noise floor at seeds 10001 / 20001, SYNERGY cross-check); `./gradlew :core:test` | BALANCED_ACTIVE 30 (20/35) mean 27.7 at 10k (27.5 at 1k), BALANCED_FAIR 20 (15/30) 21.8, every 1k mean holds within 0.5 days, 0 hard-locks in 160,000 runs; all four tools removed +0.3 days (tools buy no run length), signboard capped out by `maxCustomersPerDay` (removed: +0.7 days, +1.2 sales), whetstone pays only at 100/250 (+0.8); single affix effects inside the 0.3-day noise floor, all ten off -0.3 (ACTIVE) / -0.9 (SYNERGY, Undead Bane alone -0.7); warlord-led sieges 0.02-0.03/run, never won; perf p50 0.51 / p95 0.99 ms warm; 122/122 pass; tables, commands and recommendations in DECISIONS.md |
| Gazette edition (session 7) | `./gradlew :core:test`; `:app:installDebug`; scripted days on the emulator (`scratchpad/drive_gazette.sh`, `drive_home*.sh`) | 127/127 at the time (5 new in `GazetteEditionTest`: hero folding, shop reasons, lede and implied milestones, forge line, a simulated run fully accounted for and order-independent); day 1-4 and day 10 (siege, run end) reports and the archive (latest open, older days as a lede, one opened) screenshotted and sent; the rounds toggle renders collapsed but was not tapped |
| Home panel on device (session 7) | `:app:installDebug` over a saved day-5 run; `tools/emulator/smoke.sh`; `:app:connectedDebugAndroidTest` | resumes on Home (siege-today block first, commission to answer, shelf with yesterday's visitors, champions, yesterday's lede); End Day returns to Home; smoke.sh SMOKE_DONE with shelf/town/resume checks ok on the 0.5.0 build; instrumented 5/5 |
| Balance v4 as merged (session 7) | `./gradlew :core:test`; `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE` before/after the whetstone and warlord changes; warlord sweep `--policy all --noImpact` x3 | 140/140 pass; BALANCED_FAIR 20 (15/25) mean 20.5, sold 19.6; BALANCED_ACTIVE 25 (20/35) mean 26.4, sold 26.9; 0 hard-locks; warlord sieges 0.5-0.6/run (was 0.0), p10 kept by dropping the 1.15 raid bonus; full table in DECISIONS.md |
| Weapon pruning (session 8) | `./gradlew :core:test` (`WeaponPruningTest`); `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE` before/after | 144/144 pass (4 new: prunable rule, 400-day pair identical but for the weapons map, every Lost reason classified, save round-trip); 400-day active pair: weapons 1,727 -> 857, save 1,745,453 -> 1,012,913 bytes; simulator output identical to 0.5.0 except the `affix weapons/run` line, which counts the end-of-run map |
| Day report recovery (session 8) | `:app:installDebug`; `scratchpad/drive_report_recovery.sh` on the emulator | End Day -> kill with the report open -> relaunch shows the report -> Begin day 2 -> Home -> kill -> relaunch stays on Home day 2; all checks ok, RECOVERY_DONE |
| End Day budget on the Android runtime (session 8) | `./gradlew :app:connectedDebugAndroidTest` (`EndDayPerfTest`) | 1/1 pass on the Pixel_10_Pro AVD (API 37, x86_64): 120 forced-survival days as the active smith, p50 4.38 ms, p95 8.45 ms, max 35.5 ms against the 200 ms budget; real mid-range hardware not measured |
| Balance v5 part 1: signboard and affixes (session 8) | `./gradlew :core:test`; `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE`; `--policy BALANCED_ACTIVE --noTool signboard --noImpact`; per-affix `--noAffixEffect <id>` under BALANCED_ACTIVE and SYNERGY before and after (32 runs, JSON); `:app:compileDebugKotlin -q` | 145/145 pass (1 new: the signboard lets 0-2 more customers in on the same seed); BALANCED_ACTIVE 25 (20/35) mean 27.2, sold 31.6 (was 26.4 / 26.9); signboard alone +0.4 days and +3.5 sales over no signboard (the old one was -0.1 / -0.9); Giant Slayer worth 0.42 elite kills a run (was 0.26), Reinforced -0.09 deaths (was -0.04), Heavy +0.07 (was +0.03), Swift, Cursed and Bloodbound unchanged in the aggregate; every other policy within 0.2 mean days; 0 hard-locks; app compiles; tables in DECISIONS.md |
| Hero daily life, merged (session 8) | `./gradlew :core:test`; `:app:compileDebugKotlin -q`; `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE` on the merged tree | 158/158 pass (13 new: 11 in `HeroDailyLifeTest`, 2 in `GazetteEditionTest`); app compiles (no UI change); BALANCED_FAIR 20 (15/25) mean 20.5, BALANCED_ACTIVE 30 (20/35) mean 27.5 (was 25 (20/35) 27.2), SYNERGY 35 (25/40) 34.7, maxed ACTIVE 40 (35/45) 40.6; deaths -0.1 a run; 8.1 % of hero-days at the hall, 6.5 % on ambitions, a guild in 97 % of runs; 0 hard-locks; tables in DECISIONS.md ("Balance v5, part 2") |
| Replays and weapon fates, merged (session 8) | `./gradlew :core:test`; `:app:compileDebugKotlin -q`; `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE` on the merged tree | 172/172 pass (14 new in `ReplaysAndWeaponFatesTest`; two more asserts in `WeaponPruningTest`; one seed range widened in `HeroDailyLifeTest`); app compiles (three lines in the day report); BALANCED_FAIR 20 (15/25) mean 20.5, BALANCED_ACTIVE 30 (20/35) mean 27.4, SYNERGY 35 (25/40) 34.7, no mean moved more than 0.1 day; artifact recovery 51-53 %, guild inheritance 0.04-0.12 a run (0 on the branch alone), merchant resale 0.01-0.03 a run; 0 hard-locks; the agent showed the replay feature alone leaves simulator output byte-identical; not run on a device yet; tables in DECISIONS.md ("Balance v5, part 3") |
| Lucky loot and forge-time affix count (session 8) | `./gradlew :core:test`; `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE`; `--policy BALANCED_ACTIVE` and `SYNERGY` with `--noAffixEffect lucky` | 173/173 pass (1 new: Lucky loot is always scarce and more frequent); Lucky is inside the noise floor for both bots (+0.06 / +0.07 mean days), which do not spend scarce stock; BALANCED_ACTIVE 25 (20/35) mean 27.4, BALANCED_FAIR 20 (15/25) mean 20.5, 0 hard-locks; table in DECISIONS.md ("Balance v5, part 4") |
| Legacy tracks, merged (session 8) | `./gradlew :core:test`; `:app:compileDebugKotlin -q`; `:core:simulate --runs 1000 --seed 1 --policy all --impactPolicy BALANCED_ACTIVE` on the merged tree | 186/186 pass (13 new in `LegacyTracksTest`); app compiles (no UI change: both upgrade lists iterate the catalog); new accounts identical to the part 4 baseline; maxed BALANCED_ACTIVE 45 (35/50) mean 42.2, longest 55 (was 40 (30/45) 40.4), maxed BALANCED_FAIR 35 (30/40) 36.5; Known Name +5 / +1.6 days (was +0 / -0.1), the three new tracks 0 days by construction (their own yardsticks in DECISIONS); 0 hard-locks; tables in DECISIONS.md ("Balance v5, part 5") |
| UI declutter (session 4) | `:app:assembleDebug`, `:app:installDebug`, scripted screenshots of every panel at font scale 1.0 and 1.3 (`scratchpad/ui_v2/`), `tools/emulator/smoke.sh`, `:app:connectedDebugAndroidTest` | build ok; SMOKE_DONE with shelf/town/resume checks ok; instrumented 5 tests, 0 failures (ForgeHint x2, SaveStore, TitleScreen, Example) |

## Obstacles hit and resolved
- Launch content with the v1 siege numbers gave 0 survived sieges: heroes always fought the first faction by ID and
  three factions' growth (15/day) saturated pressure. Fixed by targeting the most pressing faction and growth 4/3/2;
  run length then retuned through forge damage (24 + 50x(ratio-1)) with siege modifier 2.0.
- The quality formula could not separate six core tiers (20-point core span vs 27-point roll); base 25 + 6/tier
  spreads them. Tier 5 remains a rare/epic split; the excellent top-tier pairs are 33-41 % legendary by design.
- Two tests encoded slice facts (12 signatures = 4 per slice family; `restless_graves` ineligible without
  Hollowbound): the signature test now checks the launch catalog (24 recipes); the world-event test expects the launch
  factions and the most pressing faction for Successful Patrol.
- The RANDOM policy reported 53 "hard-lock" days on launch content: it drew unaffordable moonsteel. It now draws only
  obtainable materials; 0 hard-locks in all runs.

## Known limitations
- Starting energy and gold upgrades buy little run length (+0.1 to +0.3 mean days under the active smith); what they
  buy shows on the second yardsticks (about a sixth more weapons forged, one more tool level by the first siege).
  The three v5 tracks read 0 days for every bot: none hunts signatures, repeats a rare recipe or owns a Legend Board.
- The `weapons` map still grows with unsold stock: salvaged, shattered, donated and collected blades are pruned 30
  days later (session 8), but blades in storage, blades lost with a hero or seized (an event can bring them home) and
  kept-forever events are not. A plain smith who never salvages keeps about 3 weapons a day (3,140 after 1,000 days).
- `panel_gazette`/`panel_journal` frames and the pack's signature weapon variants are not used (the pack's 16 px
  signature sprites would clash with the 64 px concept weapons; signatures show their name and burst instead).
- Of the v3 review recommendations (DECISIONS.md), whetstone 120/300 and warlord pressure 50 are applied in v4, the
  signboard effect, the affix magnitudes and the Lucky loot in v5; Known Name is not. Guild Patronage still
  raises the visit chance, which the customer cap mostly swallows (the old signboard problem). v4 and v5 are checked
  at 1,000 seeds only.
- No champion can die on the walls (champions need health 50, a lost siege costs 40), so the walls odds for a fallen
  blade are reached only in unit tests; merchant resale is rare (0.01-0.03 a run) because few blades are left over.
- Cursed and Bloodbound self-harm (15 / 20 health a win) does not show in deaths or run length; the simulator has no
  per-wielder or expedition-count metric to show the rest days it costs.
- The simulator's other policies ignore the new shop actions, so their rows measure a smith who never uses them.
- Weapon wear costs every policy 1-2 mean days and had cut the lead of the active smith over the plain one from 10
  median days to 5. In v5 the mean lead is 6.9 days (27.4 against 20.5, was 5.9); the active median sits on the siege
  boundary between 25 and 30 and reads either on a 0.1-day change, so it is no evidence either way. The wear levers
  (condition floor ~0.8, less siege wear) stay unused; the owner has not decided whether more is wanted.
- `BalanceConfig` is at the JVM limit of 255 parameter slots (1 free on the generated `copy$default`; a Double takes
  two). New numbers go into a nested group (`HeroLifeConfig`, `WeaponFatesConfig`, `LegacyTracksConfig`), or the class
  compiles and every test fails at class load.
- A maxed account under the active smith is at median 45 days (35/50, longest 55): the signboard and the Known Name
  regulars stack. Inside the band asked for (about 50) but at its top; to be confirmed at 10,000 seeds.
- Noticed, not fixed: the Traveling Ore Merchant adds +2 supplier stock at End Day and the next morning restock
  overwrites it, so only its free unit is ever seen.
- Not exercised on device: the rounds toggle in the field report (renders collapsed; a plain state toggle), Re-hone,
  the worn / storied labels and the WORN_OUT visit reason (need a played run). Home repeats the Shelf line in its
  Yesterday block when yesterday had no lede, and has no first-run tip.
- Package name is still `com.example.blacksmithproject`; no release signing.
- `GameEngine.RULES_VERSION` stays 1 although v2 changed hero targeting and RNG draw order and session-4 commission
  patron weighting changes which hero asks on a given seed; bump with the first release.
- Git: `main` tracks https://github.com/Morfildor/BlacksmithInc; commit and push per verified milestone. App version 0.5.0 (versionCode 5); session 8 work sits under `[Unreleased]` in CHANGELOG.md until 0.6.0.

## Next executable actions (P7)
0. Session 8, in order: merge the three agent
   branches (replays and fates, hero daily life, legacy tracks), classify any new Lost reason in `WeaponPruning`,
   bump the balance config to 5; a 10,000-seed v5 review; device run; release 0.6.0 and tick GDD_CHECKLIST.
   Left for the UI redesign: Home first-run tip and the Yesterday / Shelf repeat.
1. Measure starting gold/energy upgrades by first-siege champion power or first tier-4+ sale (DECISIONS proposal a)
   instead of run length; hero first-week purchasing power is the lever if run length must move.
2. 720x1280 pass over every panel; nav labels are tight at font scale 1.5.
3. Register the first migration step against `saves/v1_forced_seed4242_day61.json` when the envelope schema changes.
4. Package rename from `com.example.blacksmithproject`, release signing.
