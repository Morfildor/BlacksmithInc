# Progress — 2026-10-08 (session 4)

## Current phase
P7 in progress. Launch content is the engine default and balance v2 is tuned against it (this session); the
remaining P7 items are signatures for the three new families, the Strange Weapon Fragment event and save compaction.

## What exists
- `core/` pure Kotlin engine: RNG, slice + launch content catalogs (launch is the default), balance config v2, model,
  commands, End Day resolver, crafting with techniques and signature recipes, market, hero AI with retirement/guilds/
  mentoring, battles/sieges with weapon seizure, world-event pool (23 events), legacy with famous-blade returns,
  Gazette, JSON save codec with a migration scaffold, headless simulator (GDD policy set, `--content`, `--rarityTable`,
  `--impactPolicy`, forge-damage overrides, JSON report, perf probe). 72 JVM tests.
- `app/` Compose portrait workshop with the forge palette theme, onboarding tips, six panels, technique chips,
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
| Core tests | `./gradlew :core:test` | 72/72 pass (68 previous + 4 `RarityShapeTest`); the soak now runs on launch content |
| App compiles | `./gradlew :app:compileDebugKotlin -q` | BUILD SUCCESSFUL (UI untouched; all launch IDs already had sprites) |
| Rarity tables | `./gradlew :core:simulate --args="--rarityTable 1000 --seed 1 --content launch"` | before/after tables in DECISIONS.md |
| Policy sweep | `./gradlew :core:simulate --args="--runs 1000 --seed 1 --content launch"` | BALANCED_FAIR 25 (15/35), mean 23.8, 1.6 sieges survived/run, 0 hard-locks; full table in DECISIONS.md |
| Confirmation | `--runs 10000 --policy BALANCED_FAIR` / `SAFE_FAIR` | 25 (15/35) mean 23.7 survived 1.5 / 25 (15/30) mean 23.2 survived 1.5 |
| Slice regression | `--content slice --policy SAFE_FAIR` | 30 (20/35), rarity 18/51/28/2/0 (was 1/43/52/4/0; accepted, see DECISIONS) |

Previous sessions: debug APK, instrumented tests (5/5), device smoke loop and art import were verified on the Pixel 10
Pro AVD before this session; they were not re-run here (no `app/` changes).

## Obstacles hit and resolved
- Launch content with the v1 siege numbers gave 0 survived sieges: heroes always fought the first faction by ID and
  three factions' growth (15/day) saturated pressure. Fixed by targeting the most pressing faction and growth 4/3/2;
  run length then retuned through forge damage (24 + 50x(ratio-1)) with siege modifier 2.0.
- The quality formula could not separate six core tiers (20-point core span vs 27-point roll); base 25 + 6/tier
  spreads them. Tier 5 remains a rare/epic split; the excellent top-tier pairs are 33-41 % legendary by design.
- Two tests encoded slice facts (12 signatures = 4 per slice family; `restless_graves` ineligible without
  Hollowbound): the signature test now checks the slice catalog explicitly; the world-event test expects the launch
  factions and the most pressing faction for Successful Patrol.
- The RANDOM policy reported 53 "hard-lock" days on launch content: it drew unaffordable moonsteel. It now draws only
  obtainable materials; 0 hard-locks in all runs.

## Known limitations
- Starting energy/gold upgrades measure ~0 days under BALANCED_FAIR because the bot never spends its ~500 gold on
  better cores; gold registers under `--impactPolicy SYNERGY` (+5 median). A harness purchasing rule is the next lever.
- 12 of 24 signature recipes (launch families spear/dagger/staff have none); event "Strange Weapon Fragment" missing.
- Save events are never compacted; a 400-day run stores ~4,500 event records (P7 soak item).
- `panel_gazette`/`panel_journal` frames and the pack's signature weapon variants are not used.
- Package name is still `com.example.blacksmithproject`; no release signing.
- `GameEngine.RULES_VERSION` stays 1 although v2 changed hero targeting and RNG draw order; bump with the first release.
- Git: `main` tracks https://github.com/Morfildor/BlacksmithInc. Commit/push only on request.

## Next executable actions (P7)
1. Add the 12 spear/dagger/staff signatures and the Strange Weapon Fragment event; extend `catalogIsConsistentWithSliceContent` to the launch catalog once they exist.
2. Event-log compaction policy in `SaveCodec` that keeps histories intact; Room migration test for schema v2.
3. Re-run the device smoke loop and instrumented tests on the launch default (three factions, five classes in the UI).
4. Balance review after the signatures land: harness purchasing rule so starting gold/energy upgrades register; tier-5 epic centring if a new lever appears.
