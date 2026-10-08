# Progress — 2026-10-08 (session 2)

## Current phase
P6 largely implemented and verified on JVM and on the Pixel 10 Pro AVD (hand-made art, launch content as data,
signatures/techniques, world events, generations, balance harness, UI polish). Next phase: P7 (see below).

## What exists
- `core/` pure Kotlin engine: RNG, slice + launch content catalogs, balance config, model, commands, End Day resolver,
  crafting with techniques and signature recipes, market, hero AI with retirement/guilds/mentoring, battles/sieges with
  weapon seizure, world-event pool (23 events + 2 deterministic rules = all 25 GDD events), 24 signature recipes,
  legacy with famous-blade returns, Gazette, JSON save codec with a migration scaffold, headless simulator (GDD
  policy set, JSON report, perf probe). 71 JVM tests.
- `app/` Compose portrait workshop with the forge palette theme, onboarding tips, six panels, technique chips,
  newspaper day report with stepped replay, blessing choice (dismissable for the day), run-end/legacy screen; Room
  atomic save store; DataStore settings (reduced motion, seen tips). Hand-made pixel art on every screen.
- `tools/pixelart/import_assets.py` (slices concept sheets, copies selected pack sprites, prunes stale imports),
  `generate_assets.py` (placeholders), `tools/emulator/smoke.sh` (device loop). `docs/ART_BRIEF.md` is the brief.
- Animated siege diorama in the day report (artist pack frames), dead/retired markers, milestone burst on the result
  card for signature or epic+ weapons.
- Weapon master sheet sliced into 336 `weapon_<family>_<row>_<level>` sprites with a generated `ui/WeaponArt.kt`
  lookup; 488 hand-made sprites in total (94 sheet slices + 336 weapons + 58 pack sprites).

## Checks run this session
| Check | Command | Result |
|---|---|---|
| Core tests | `./gradlew :core:test` | 71/71 pass (11 classes incl. LaunchContent, SignatureAndTechnique, WorldEventsAndGenerations, Migration, Soak, LaunchEffects); the 5,000-day soak adds ~100 s |
| Full Gradle tests | `./gradlew test` | 69 tests, 0 failures |
| Simulator | `./gradlew :core:simulate --args="--runs 1000 --seed 1"` | 8 policies + maxed account + upgrade impact in ~20 s; BALANCED_FAIR median 35 (p10 20 / p90 40), 0 hard-locks; table in DECISIONS.md |
| Debug APK + install | `./gradlew :app:installDebug` | BUILD SUCCESSFUL |
| Instrumented | `./gradlew :app:connectedDebugAndroidTest` | 5/5 pass (Room atomic save/restore x2, title screen, forge hint x2) |
| Device loop | `tools/emulator/smoke.sh` | New run -> forge -> list -> End Day -> Gazette with a real sale and a battle using the sword -> Town -> resume after process death on day 2 (screenshots sent to the user) |
| Art import | `python tools/pixelart/import_assets.py` | 5 sheets + 1 pack -> 194 sprites; contact sheet `docs/art_contact_handmade.png` reviewed; siege diorama verified on device at day 5 |
| Re-verification after the diorama | smoke + `connectedDebugAndroidTest` | SMOKE_DONE, 5/5 instrumented |

## Obstacles hit and resolved
- Six parallel agents on one tree: transient compile breaks and Gradle lock waits; integrated by re-running the whole
  chain afterwards. No pre-existing test was weakened (timestamps checked).
- `Element` enum grew to six values: `validate()` now checks coverage against the catalog's augments and hero taste is
  drawn from the catalog, otherwise slice heroes wanted elements no slice weapon has.
- Hand-made art is high-resolution pseudo-pixel art, not 16 px sprites: imported at 4x the scene unit / 64 px icons,
  bilinear when shrunk, nearest when enlarged; the non-seamless parchment is stretched to cover instead of tiled.
- Connected tests uninstall the APK afterwards; reinstall before manual device checks.
- Balance drift after world events (median 30 -> 35); siege-modifier re-sweep showed higher values only remove
  survived sieges, so 2.75 was kept (DECISIONS.md).

## Known limitations
- `SliceContent` is still the engine default; `LaunchContent` is data-complete but needs the quality formula retuned.
  The 12 spear/dagger/staff signatures are only forgeable once it is.
- Save events are never compacted; a 400-day run stores ~4,500 event records (fine for play, a P7 soak item).
- `panel_gazette`/`panel_journal` frames and the pack's signature weapon variants are not used (the pack's 16 px
  signature sprites would clash with the 64 px concept weapons; signatures show their name and burst instead).
- Package name is still `com.example.blacksmithproject`; no release signing.
- Git: `main` tracks https://github.com/Morfildor/BlacksmithInc (first commit 2026-10-08). Commit/push only on request.

## Next executable actions (P7)
1. Rarity-distribution sweep with `GameEngine(content = LaunchContent.catalog)`; retune the quality formula for tiers
   4-6, then flip the default catalog and re-run the full chain.
2. Tune forge damage per lost siege toward the 15-25-day early median without starving survived sieges.
3. Give starting energy/gold upgrades measurable effect (simulator shows about 0 days).
4. Event-log compaction policy in `SaveCodec` that keeps histories intact; Room migration test for schema v2.
