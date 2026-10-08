# Progress — 2026-10-08 (session 4)

## Current phase
P7 in progress. Launch content is the engine default and balance v2 is tuned against it; all 24 signature recipes,
the Strange Weapon Fragment event and End Day event-log compaction landed the same day (parallel agents, merged).

## What exists
- `core/` pure Kotlin engine: RNG, slice + launch content catalogs (launch is the default), balance config v2, model,
  commands, End Day resolver, crafting with techniques and all 24 signature recipes, market, hero AI with retirement/
  guilds/mentoring, battles/sieges with weapon seizure, world-event pool (23 events + 2 deterministic rules = all 25 GDD
  events), legacy with famous-blade returns, Gazette, JSON save codec with a migration scaffold, End Day event-log
  compaction (30-day window, history-grade types kept), headless simulator (GDD policy set, `--content`,
  `--rarityTable`, `--impactPolicy`, forge-damage overrides, JSON report, perf probe), weapon-history cap, a checked-in
  v1 save fixture. 92 JVM tests.
- `app/` Compose portrait workshop (session 4 layout: three-stat top bar, pinned forge summary over collapsible
  auto-advancing steps, row-based market, per-panel tip banners, single End Day action, full-width paper day report;
  principles in DECISIONS.md), six panels, technique chips,
  newspaper day report with stepped replay, blessing choice (dismissable for the day), run-end/legacy screen; Room
  atomic save store; DataStore settings (reduced motion, seen tips). Hand-made pixel art on every screen.
- `tools/pixelart/import_assets.py` (slices concept sheets, copies selected pack sprites, prunes stale imports),
  `generate_assets.py` (placeholders), `tools/emulator/smoke.sh` (device loop). `docs/ART_BRIEF.md` is the brief.
- Animated siege diorama in the day report (artist pack frames), dead/retired markers, milestone burst on the result
  card for signature or epic+ weapons.
- Weapon master sheet sliced into 336 `weapon_<family>_<row>_<level>` sprites with a generated `ui/WeaponArt.kt`
  lookup; 488 hand-made sprites in total (94 sheet slices + 336 weapons + 58 pack sprites).

## Checks run this session (balance v2, launch default)
| Check | Command | Result |
|---|---|---|
| Core tests | `./gradlew :core:test` | 82/82 pass (13 classes incl. RarityShape, EventCompaction, SignatureAndTechnique on the launch catalog); the soak runs on launch content |
| App compiles | `./gradlew :app:compileDebugKotlin -q` | BUILD SUCCESSFUL |
| Rarity tables | `./gradlew :core:simulate --args="--rarityTable 1000 --seed 1 --content launch"` | before/after tables in DECISIONS.md |
| Policy sweep | `./gradlew :core:simulate --args="--runs 1000 --seed 1 --content launch"` | BALANCED_FAIR 25 (15/35), mean 23.8, 1.6 sieges survived/run, 0 hard-locks; full table in DECISIONS.md |
| Confirmation | `--runs 10000 --policy BALANCED_FAIR` / `SAFE_FAIR` | 25 (15/35) mean 23.7 survived 1.5 / 25 (15/30) mean 23.2 survived 1.5 |
| Slice regression | `--content slice --policy SAFE_FAIR` | 30 (20/35), rarity 18/51/28/2/0 (was 1/43/52/4/0; accepted, see DECISIONS) |
| Event compaction | `EventCompactionTest` + `:core:simulate --runs 200 --seed 1 --perf` | 400-day forced-survival pair: gameplay identical with/without compaction; perf run ends at events=1,834 (was 13,275) |
| Weapon-history cap + v1 fixture | `./gradlew :core:test` (`WeaponHistoryCompactionTest`, `SaveFixtureTest`) + `:core:simulate --runs 200 --seed 1 --perf` | 92/92 pass; 400-day pair: gameplay identical with/without the cap, entries 1,812 -> 1,721, max combat entries 21 -> 10, save 973,919 -> 961,841 bytes; `saves/v1_forced_seed4242_day61.json` decodes, accepts an End Day and compacts; perf p50 0.34-0.40 ms vs 0.26 before, within machine noise (cap-disabled control 0.39-0.71) |
| Debug APK + install | `./gradlew :app:installDebug` | BUILD SUCCESSFUL |
| Instrumented | `./gradlew :app:connectedDebugAndroidTest` | 5/5 pass (Room atomic save/restore x2, title screen, forge hint x2) |
| Device loop | `tools/emulator/smoke.sh` | Re-run on the merged 0.2.0 build (launch content, new layout): shelf/town/resume checks ok, SMOKE_DONE; screenshots sent to the user |
| Instrumented on merged main | `./gradlew :app:connectedDebugAndroidTest` | 5/5 pass on the 0.2.0 build |
| Art import | `python tools/pixelart/import_assets.py` | 5 sheets + weapon master + 1 pack -> 488 sprites; contact sheet reviewed; weapon shelf verified on device |
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
- Starting energy/gold upgrades measure ~0 days under BALANCED_FAIR because the bot never spends its ~500 gold on
  better cores; gold registers under `--impactPolicy SYNERGY` (+5 median). A harness purchasing rule is the next lever.
- `Weapon.history` combat entries are capped (10 per weapon), but the per-weapon FORGED/INHERITED entries and the
  `weapons` map itself still grow with every weapon forged (930 weapons, 1,721 entries after a forced 400-day run;
  2,330 weapons after 1,000 days); lost/destroyed weapons are never pruned. The next list to watch.
- `panel_gazette`/`panel_journal` frames and the pack's signature weapon variants are not used (the pack's 16 px
  signature sprites would clash with the 64 px concept weapons; signatures show their name and burst instead).
- Package name is still `com.example.blacksmithproject`; no release signing.
- `GameEngine.RULES_VERSION` stays 1 although v2 changed hero targeting and RNG draw order; bump with the first release.
- Git: `main` tracks https://github.com/Morfildor/BlacksmithInc; commit and push per verified milestone. App version 0.2.0 (versionCode 2).

## Next executable actions (P7)
1. Re-run the device smoke loop and instrumented tests on the launch default (three factions, five classes in the UI).
2. Harness purchasing rule (buy the best affordable core) so starting gold/energy upgrades register; tier-5 epic
   centring if a new lever appears.
3. Register the first migration step against `saves/v1_forced_seed4242_day61.json` when the envelope schema changes;
   prune or cap the `weapons` map (lost/destroyed records) if 1,000-day saves matter.
4. Package rename from `com.example.blacksmithproject`, release signing.
