# Tiny Blacksmith — project guide for Claude Code

Premium (€1.99, offline, no ads/IAP/accounts) portrait Android game: a menu-only pixel-art blacksmith shop whose
weapons are bought by autonomous heroes who fight, defend the town and die. Source of truth for design:
`Tiny_Blacksmith_GDD_v1.0.md` (read it; do not copy it here). LOCKED = requirement, PROPOSED = tunable, DEFERRED = do not build.

## Non-negotiables (LOCKED in the GDD)
- Player never moves a character, commands combat or equips heroes. Only shop actions: forge, price, list, commissions, End Day.
- 10 daily energy; overwork causes next-day exhaustion. Every valid forge yields a usable (possibly flawed) weapon.
- Predictable major siege timing; living faction pressure decides strength. Forge integrity 0 = run over.
- Three dynamic champions = strongest *available* heroes (gracefully 0–2). Dead heroes never act or own equipment.
- Strong permanent legacy (knowledge, upgrades, legends, lineages); run-only state resets. Claim once per run.
- Gazette and replays derive from real event records; animations never consume gameplay RNG.
- No backend, telemetry, ads, billing SDK, runtime AI, timers or idle production.

## Architecture boundaries
- `:core` — pure Kotlin JVM, zero Android deps. Deterministic SplitMix64 streams per subsystem (`rng/`), content
  by stable IDs (`content/`), every number in `config/BalanceConfig.kt` (versioned), immutable `GameState`
  (`model/`), typed commands and errors (`engine/Commands.kt`), the only mutator `engine/GameEngine.kt`
  (End Day = fixed GDD 3.2 order, idempotent per command ID), invariants asserted after each command.
- `:app` — Compose UI is an observer: `GameViewModel` dispatches commands, saves accepted results atomically
  (Room, `data/SaveStore.kt`), then renders. DataStore holds only settings. Never compute outcomes in UI.
- Save format: versioned JSON envelope from `core/persistence/SaveCodec.kt`; migrations go there.
- Content names are PROPOSED; counts are LOCKED. Vertical slice content lives in `content/SliceContent.kt`.
- Pixel art has three sources, never hand-edited PNGs, all imported by `tools/pixelart/import_assets.py` into
  `tools/pixelart/overrides.json`: concept sheets in `Pixel art assets/` (sliced by cell layout, rich 64 px icons
  and the forge scene), loose `<id>.png` files, and the artist's 1x production pack (a subfolder with
  `drawable-nodpi/` + `manifest.json`), from which only battle frames, siege wall, milestone burst and hero markers
  are taken by default (`--pack-all` takes everything). `generate_assets.py` draws placeholders only for IDs without
  hand-made art. The brief for new art
  is `docs/ART_BRIEF.md`. `ui/Sprites.kt` maps content IDs to sprites (nearest-neighbour when enlarging, bilinear
  when shrinking hand-made art); sprites stay decorative (no essential text, no gameplay reads).

## Commands
```
./gradlew :core:test                                   # JVM tests (determinism, bounds, idempotence, e2e)
./gradlew :core:simulate --args="--runs 1000 --seed 1"  # headless balance harness (add --policy X, --siegeModifier, --recoveryCap)
./gradlew :app:assembleDebug                           # APK (needs Android SDK at local.properties sdk.dir)
./gradlew :app:installDebug                            # install on connected device/emulator
./gradlew :app:connectedDebugAndroidTest               # instrumented tests (emulator required)
python tools/pixelart/import_assets.py                 # slice hand-made sheets from 'Pixel art assets/' into drawables (Pillow, numpy)
python tools/pixelart/generate_assets.py               # placeholders for IDs without hand-made art + manifest
ADB=<sdk>/platform-tools/adb bash tools/emulator/smoke.sh <dir>  # scripted device loop + screenshots (after installDebug)
```
Toolchain: Gradle 9.5, AGP 9.3.3 (built-in Kotlin), Kotlin plugins 2.2.21 (compose/jvm/serialization), KSP 2.3.12,
Compose BOM 2026.02.01, Room 2.8.5, DataStore 1.2.1, JDK 21 launcher / JDK 25 daemon toolchain.

## Working rules
- Surgical edits; keep docs in `docs/` current: IMPLEMENTATION_PLAN (phase gates), DECISIONS (locked vs proposed,
  tuning evidence), PROGRESS (state, checks run, next actions). Update PROGRESS before ending a session.
- Balance changes: run the simulator, record numbers in DECISIONS.md, bump `BalanceConfig.version` on semantic change.
- New gameplay numbers go in `BalanceConfig`, never inline. New content goes through `ContentCatalog.validate()`.
- Tests must pass before claiming a phase done; do not commit/push without being asked.
